#!/usr/bin/env python3
"""
stamp_terminal_src_area.py — stamp the MEASURED source-storey footprint area onto
terminal_rules.db rule_placement, so the live disc_walker area-scales fixture counts
(count = density × target_area) instead of bbox-tiling the footprint at the local
cluster pitch. Closes audit finding F-WALK-2 (build/erp/AUDIT_WALK_GROUNDTRUTH.md):
the roof/IfcPlate rule (0.495×0.15 plate pitch = 13.5/m²) was bbox-tiling to the
50 000/storey backstop cap (SampleCastle roof = 233 374 placed) — a cap ARTIFACT,
not a measured-bound count. With src_storey_area_m2 present the engine takes its
area-density path (disc_walker.js repRules/place), bounding the count by the MEASURED
areal density and the real occupancy envelope. Brings terminal_rules to the SAME
uniform model duplex_rules already uses (PROGRESS.md "re-bake terminal_rules with src_area").

NON-INVENT: src_storey_area_m2 is the XY-bbox footprint of EVERY Terminal element whose
center_z lies in the rule's OWN measured z-band [z_band_lo, z_band_hi] — measured the
exact way disc_walker.substrate() / bake_duplex_rules.storey_areas() measure a storey
footprint (center ± bbox/2). Both sides of the density ratio (n_measured / area) are
measured from Terminal; nothing is fabricated. Rows whose z-band yields a degenerate
(zero) footprint keep area 0.0 → the engine safely falls back to the legacy tile path.

Idempotent: re-running re-measures and overwrites; ADD COLUMN is guarded.

Run:  python3 build/stamp_terminal_src_area.py
"""
import os
import sqlite3

HERE = os.path.dirname(os.path.abspath(__file__))
RULES_DB = os.path.join(HERE, "terminal_rules.db")
META_CANDIDATES = [
    os.path.join(HERE, "Terminal_meta.db"),
    os.path.expanduser("~/bim-ootb/modeller/Terminal_meta.db"),
    os.path.join(HERE, "..", "deploy", "buildings", "Terminal_meta.db"),
]


def log(m):
    print(m, flush=True)


def find_meta():
    for p in META_CANDIDATES:
        if os.path.exists(p):
            return p
    raise SystemExit("FATAL: Terminal_meta.db not found in " + str(META_CANDIDATES))


def band_area(rows, lo, hi):
    """XY-bbox footprint (m²) of all elements whose center_z ∈ [lo,hi]; same method as
    disc_walker.substrate(): min/max of center ± bbox/2. Returns (area_m2, n_elems)."""
    if lo is None or hi is None:
        return 0.0, 0
    minx = miny = float("inf")
    maxx = maxy = float("-inf")
    n = 0
    for cz, cx, cy, bx, by in rows:
        if cz is None or not (lo <= cz <= hi):
            continue
        minx = min(minx, cx - bx / 2.0); maxx = max(maxx, cx + bx / 2.0)
        miny = min(miny, cy - by / 2.0); maxy = max(maxy, cy + by / 2.0)
        n += 1
    if n == 0:
        return 0.0, 0
    return round(max(0.0, maxx - minx) * max(0.0, maxy - miny), 2), n


def main():
    if not os.path.exists(RULES_DB):
        raise SystemExit("FATAL: " + RULES_DB + " not found")
    meta_path = find_meta()
    log("§STAMP-AREA rules=%s meta=%s" % (os.path.basename(RULES_DB), meta_path))

    meta = sqlite3.connect(meta_path)
    rows = meta.execute(
        "SELECT t.center_z, COALESCE(t.center_x,0), COALESCE(t.center_y,0), "
        "COALESCE(t.bbox_x,0), COALESCE(t.bbox_y,0) FROM element_transforms t"
    ).fetchall()
    meta.close()
    log("§STAMP-AREA terminal indexed elements=%d" % len(rows))

    db = sqlite3.connect(RULES_DB)
    cols = [r[1] for r in db.execute("PRAGMA table_info(rule_placement)")]
    if "src_storey_area_m2" not in cols:
        db.execute("ALTER TABLE rule_placement ADD COLUMN src_storey_area_m2 REAL")
        log("§STAMP-AREA added column src_storey_area_m2")
    else:
        log("§STAMP-AREA column src_storey_area_m2 present — re-measuring")

    placements = db.execute(
        "SELECT rowid, disc, ifc_class, z_band_lo, z_band_hi, n_measured, "
        "spacing_x_m, spacing_y_m FROM rule_placement"
    ).fetchall()
    stamped = 0
    zero = 0
    for rid, disc, cls, lo, hi, nm, sx, sy in placements:
        area, n = band_area(rows, lo, hi)
        db.execute("UPDATE rule_placement SET src_storey_area_m2=? WHERE rowid=?", (area, rid))
        stamped += 1
        dens = (nm / area) if area > 0 and nm else 0.0
        tag = ""
        if sx and sy and sx > 0 and sy > 0 and (1.0 / (sx * sy)) > 5:
            tag = "  <== was tile-dense (cap risk) now area-bound"
        if area == 0.0:
            zero += 1
            tag = "  (degenerate z-band → 0 → engine tile-path fallback)"
        log("  §STAMP %-5s %-26s zband=%-16s n=%-6s area=%9.1f dens=%6.3f/m²%s" %
            (disc, cls, "%s..%s" % (lo, hi), nm, area, dens, tag))
    db.commit()
    db.close()
    log("§STAMP-AREA done: %d rows stamped (%d degenerate fallback)" % (stamped, zero))


if __name__ == "__main__":
    main()
