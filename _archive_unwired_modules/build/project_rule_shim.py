#!/usr/bin/env python3
"""
project_rule_shim.py — project the host-bind percepts from disc_patterns.db `_shim_attributes`
into a per-building-class `*_rules.db` as a first-class `rule_shim` table (RESUME_DISC_WALKER_
ENVELOPE_BOUND.md §NAMING DIRECTIVE §SHIM + §SHIM-SELECT). This makes the SHIM flow like
routing/placement: the walker reads its anchor/mount/offset FROM THE PROJECTION (the lean rules
DB), not from a caller.

TWO ROW KINDS:
  (1) DISC-LEVEL (fixture_ifc_class=NULL) — one per `_shim_attributes` percept, copied verbatim
      (the long-standing fallback; selected by `priority` when a disc has >1 host).
  (2) PER-FIXTURE (fixture_ifc_class=<class>) — the §SHIM-SELECT SELECTION KEY: which fixture class
      actually mounts on which host, MEASURED from the source building (`source_db`). Only stamped
      when the nearest host is DECISIVE (see MOUNT_TOL gate) — else REFUSED (no row), so the
      disc-level fallback persists. NON-INVENT: every host is the measured nearest, never guessed.

NON-INVENT: every disc-level row is copied verbatim from `_shim_attributes`; every per-fixture row
is a real measurement (point-to-bbox-surface nearest-neighbour) over the source building's REAL
geometry. The only derived columns are unit conversions (mm→m), a deterministic `priority`, and
`same_storey`. No values invented; ambiguous fixtures are refused, never forced.

IDEMPOTENT + ISOLATED: DROP+CREATE+INSERT `rule_shim` ONLY — the 5 mined rule tables are untouched
(re-run safe, no drift). Source = library/disc_patterns.db (physically library/ERP.db until the
rename slice; both names resolve via symlink). Usage:
  project_rule_shim.py <rules_db> <building_class> [source_extracted_db]
"""
import os
import sqlite3
import statistics
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
# disc_patterns.db (the pattern store) — symlink to ERP.db today; new code names disc_patterns.
DISC_PATTERNS = os.path.join(HERE, "..", "library", "disc_patterns.db")

# hosts that STACK vertically (same plan XY across storeys) → require same-storey host selection.
# A small, structural fact about the host class, not a tuned constant.
STACKING_HOSTS = {"IfcWindow", "IfcDoor"}

# §SHIM-SELECT gate: a fixture class is bound to a host only when the host is DECISIVELY nearest —
# median surface-distance within MOUNT_TOL (effectively touching/mounted) AND, when the disc offers
# >1 candidate host, at most HALF the runner-up's median. Else REFUSE (no per-fixture row).
MOUNT_TOL_M = 0.5
RUNNER_RATIO = 0.5


def _surf_dist(p, pool):
    """Min point-to-bbox-surface distance from fixture centre `p` to any host bbox in `pool`."""
    px, py, pz = p
    best = 1e18
    for cx, cy, cz, bx, by, bz in pool:
        dx = max(abs(px - cx) - bx / 2, 0.0)
        dy = max(abs(py - cy) - by / 2, 0.0)
        dz = max(abs(pz - cz) - bz / 2, 0.0)
        d = (dx * dx + dy * dy + dz * dz) ** 0.5
        if d < best:
            best = d
    return best


def _mine_fixture_hosts(source_db, disc_hosts, walkable, log):
    """MEASURE which fixture ifc_class mounts on which host (the §SHIM-SELECT selection key).

    disc_hosts : {disc: [(host_ifc_class, percept_tuple), ...]} — the disc's candidate hosts.
    walkable   : set of (disc, fixture_ifc_class) the walker actually places (∈ rule_placement).
    Returns    : {(disc, fixture_ifc_class): (host_ifc_class, percept_tuple, median_m)}.
    """
    if not source_db or not os.path.exists(source_db):
        log(f"§SHIM-SELECT SKIP — source_db absent ({source_db}); per-fixture selection key not mined")
        return {}
    s = sqlite3.connect(source_db)
    tx = {g: (cx, cy, cz, bx, by, bz) for g, cx, cy, cz, bx, by, bz in s.execute(
        "SELECT guid, center_x, center_y, center_z, bbox_x, bbox_y, bbox_z FROM element_transforms")}
    cls = {g: c for g, c in s.execute("SELECT guid, ifc_class FROM elements_meta")}
    disc_of = {g: d for g, d in s.execute("SELECT guid, discipline FROM elements_meta")}
    # Duplex resolves the sub-discipline (ELEC/PLB/ACMV) per guid via mep_subdisc; Terminal already
    # carries ELEC/FP/ACMV in elements_meta.discipline. Prefer the explicit sub-disc when present.
    has_subdisc = s.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='mep_subdisc'").fetchone()
    if has_subdisc:
        for g, sd in s.execute("SELECT guid, subdisc FROM mep_subdisc"):
            if sd:
                disc_of[g] = sd
    s.close()

    shim_discs = set(disc_hosts.keys())
    # host pools keyed by the host's ifc_class token (substring match mirrors disc_walker.hostBind's
    # `ifc_class LIKE '%host%'`, so IfcWall picks up IfcWallStandardCase).
    host_keys = sorted({h for hs in disc_hosts.values() for (h, _) in hs})
    pools = {h: [tx[g] for g, c in cls.items() if h.lower() in (c or "").lower() and g in tx]
             for h in host_keys}

    out = {}
    for disc, fixture in sorted(walkable):
        if disc not in shim_discs:
            continue
        cands = disc_hosts[disc]
        # fixture instances in the source: resolved-disc == disc AND ifc_class == fixture (exact).
        # only the centre is needed for the fixture point (distance is to the HOST bbox surface).
        pts = [tx[g][:3] for g, c in cls.items()
               if c == fixture and disc_of.get(g) == disc and g in tx]
        if not pts:
            continue
        if fixture in host_keys:  # never bind a host class to itself
            continue
        meds = []
        for host, percept in cands:
            pool = pools.get(host) or []
            if not pool:
                continue
            ds = [_surf_dist(p, pool) for p in pts]
            meds.append((statistics.median(ds), host, percept))
        if not meds:
            continue
        meds.sort(key=lambda m: m[0])
        win_med, win_host, win_percept = meds[0]
        runner = meds[1][0] if len(meds) > 1 else None
        decisive = win_med <= MOUNT_TOL_M and (runner is None or win_med <= RUNNER_RATIO * runner)
        if decisive:
            out[(disc, fixture)] = (win_host, win_percept, win_med)
            log(f"§SHIM-SELECT {disc}/{fixture} → {win_host} "
                f"(median={win_med:.3f}m{'' if runner is None else f', runner-up={runner:.3f}m'}) STAMP")
        else:
            log(f"§SHIM-SELECT {disc}/{fixture} REFUSE — not decisive "
                f"(nearest {win_host}={win_med:.3f}m"
                f"{'' if runner is None else f', runner-up={runner:.3f}m'}) → disc-level fallback")
    return out


def project_shims(rules_db, building_class, patterns_db=DISC_PATTERNS, source_db=None, log=print):
    if not os.path.exists(patterns_db):
        log(f"§SHIM-PROJ SKIP — {patterns_db} missing (pattern store absent)")
        return 0
    src = sqlite3.connect(patterns_db)
    percepts = src.execute(
        "SELECT product_value, host_ifc_class, mount, offset_mm, height_mm FROM _shim_attributes"
    ).fetchall()
    src.close()

    # disc → candidate hosts (with the full percept tuple, so a per-fixture row reuses the exact
    # mount/offset/height of the matching disc+host percept).
    disc_hosts = {}
    for percept in percepts:
        product_value, host, mount, off_mm, ht_mm = percept
        disc = str(product_value).split("_")[0]
        disc_hosts.setdefault(disc, []).append((host, percept))

    con = sqlite3.connect(rules_db)
    cur = con.cursor()
    # walkable (disc, ifc_class) the walker actually places — the per-fixture rows only matter for these.
    try:
        walkable = set(cur.execute("SELECT DISTINCT disc, ifc_class FROM rule_placement").fetchall())
    except sqlite3.OperationalError:
        walkable = set()

    cur.executescript(
        """
        DROP TABLE IF EXISTS rule_shim;
        CREATE TABLE rule_shim(
            disc TEXT, fixture_ifc_class TEXT, host_ifc_class TEXT, mount TEXT,
            offset_m REAL, height_m REAL, same_storey INTEGER, priority INTEGER,
            building_class TEXT, provenance TEXT, product_value TEXT);
        """
    )

    def _row(disc, fixture, percept, provenance):
        product_value, host, mount, off_mm, ht_mm = percept
        offset_m = (off_mm or 0) / 1000.0
        height_m = (ht_mm / 1000.0) if ht_mm is not None else None
        same_storey = 1 if host in STACKING_HOSTS else 0
        priority = 0 if str(mount).upper() == "SIDE" else 1  # wall-SIDE anti-float wins for disc-level pick
        cur.execute(
            "INSERT INTO rule_shim VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            (disc, fixture, host, mount, offset_m, height_m, same_storey, priority,
             building_class, provenance, product_value),
        )

    # (1) DISC-LEVEL rows (fallback) — verbatim copy, one per percept.
    n_disc = 0
    for percept in percepts:
        disc = str(percept[0]).split("_")[0]
        _row(disc, None, percept, "projected:disc_patterns._shim_attributes")
        n_disc += 1

    # (2) PER-FIXTURE rows (the §SHIM-SELECT selection key) — measured from the source building.
    mined = _mine_fixture_hosts(source_db, disc_hosts, walkable, log)
    n_fix = 0
    for (disc, fixture), (host, percept, med) in sorted(mined.items()):
        _row(disc, fixture, percept,
             f"measured:fixture-host-nn:{os.path.basename(source_db or '?')}@{med:.3f}m")
        n_fix += 1

    con.commit()
    con.close()
    log(f"§SHIM-PROJ {os.path.basename(rules_db)} ← disc_patterns._shim_attributes: "
        f"{n_disc} disc-level + {n_fix} per-fixture (selection-key) = {n_disc + n_fix} rule_shim rows "
        f"(building_class={building_class})")
    return n_disc + n_fix


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4):
        print("usage: project_rule_shim.py <rules_db> <building_class> [source_extracted_db]",
              file=sys.stderr)
        sys.exit(2)
    src_db = sys.argv[3] if len(sys.argv) == 4 else None
    project_shims(sys.argv[1], sys.argv[2], source_db=src_db)
