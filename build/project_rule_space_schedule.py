#!/usr/bin/env python3
"""
project_rule_space_schedule.py — project the Java-era MEASURED per-space MEP schedule
(`ad_space_type_mep_bom`, 188 rows / 37 space types) + its placement-offset semantics
(`ad_placement_offset`) from disc_patterns.db into a per-building-class `*_rules.db`,
following the exact projection pattern of project_rule_shim.py (same doctrine, same
mechanism, new source tables — WalkerDoctrine: disc stays a COLUMN, never a per-disc file).

WHY (2026-07-10, user-directed): the JS disc_walker's fixture path scatters density-scaled
fixtures onto occupancy-grid cell centres — no room schedule, no offset semantics, no
ARC-surface Z ("geometry hell", ModellerGuide screenshots). The Java generative placer
solved this in Oct-Dec (verified again today: schedule × room-dims reproduces a real
compile EXACTLY — LIVING 15 + BEDROOM 9 + CORRIDOR 19 = 43 devices, 0 breaches, see
logs/pipeline_SH*20260710*.log in the g1count worktree). Java is deprecated as runtime;
its MEASURED data + semantics are mined here for the SQLite-WASM browser walker.

TABLES WRITTEN (DROP+CREATE each — idempotent, isolated; mined rule tables untouched):
  rule_space_schedule — one row per (space_type, device): quantities (qty_normal/min/max,
      per_area_normal), placement offsets copied VERBATIM from ad_placement_offset
      (edge_x/edge_y/z_offset metres, z_rule FLOOR/CEILING/MID, x_ref/y_ref MIN/MAX/CENTER),
      device dims (M_Product metres, else the REAL LOD400 mesh bbox), and the LOD400
      geometry_hash (M_Product_Image direct, else source_element_ref → I_Geometry_Map).
      A device with NO real mesh gets geometry_hash NULL → the walker must REFUSE it
      (UNBREAKABLE no-fallback-shape law, WalkerDoctrine §11) — never a substitute box.
  rule_space_type   — space_type value + measured default ceiling (m).
  rule_space_alias  — alias → space_type (ad_space_type_alias, for LongName mapping
      e.g. "Bathroom 1" → BATHROOM).
  rule_code_spacing — max_spacing_m per (element_type, space_type) from ad_code_requirement.

DISC MAPPING (Java Discipline → JS walker disc roster): the Java placer resolves a device's
discipline via ad_assembly_connector.connects_to (PlacementCollectorVisitor.resolveDeviceDiscipline);
Java's CW (cold water) and SP (sanitary) are both the JS walker's single PLB discipline
(duplex_rules disc roster = ELEC/PLB/ACMV + borrowed FP — established naming, not invented here).

NON-INVENT: every quantity/offset/dim/hash is copied or measured from real source rows;
devices without connector rows fall back to the SAME anchor_end mapping Java used; nothing
is fabricated, meshless devices are surfaced as a REFUSE list, never silently defaulted.

BUILDING_CLASS SEMANTICS (Watchdog follow-up 1b, 2026-07-10): the building_class column stamped
on every projected row is the PROJECTION-TARGET class DB (duplex_rules.db = 'residential'), i.e.
provenance of the projection run — it is NOT a per-space-type classification claim. The source has
no residential/non-residential axis; the only per-type class signal is ad_space_type.category
(CIRCULATION/EXTERIOR/HABITABLE/SERVICE/UTILITY/UNKNOWN), carried VERBATIM into
rule_space_type.category. Space types with non-residential vocabulary (CONCOURSE, GATE, ...) sit
in the projection because ad_space_type carries them; they stay dormant unless a real room's
LongName resolves to them via rule_space_alias.

Usage: project_rule_space_schedule.py <rules_db> <building_class>
"""
import array
import os
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DISC_PATTERNS = os.path.join(HERE, "..", "library", "disc_patterns.db")
COMPONENT_LIB = os.path.join(HERE, "..", "library", "component_library.db")

# Java resolveDeviceDiscipline connects_to → Discipline, folded to the JS disc roster
# (CW/SP → PLB). Fallback anchor_end mapping mirrors the same Java method.
CONNECTS_TO_DISC = {
    "ELEC_CONDUIT": "ELEC",
    "WATER_RISER": "PLB",      # Java: CW
    "PLUMBING_STACK": "PLB",   # Java: SP
    "FP_MAIN": "FP",
    "ACMV_DUCT": "ACMV",
}
ANCHOR_END_DISC = {"RISER": "PLB", "STACK": "PLB", "PANEL": "ELEC"}

# device→family-ref mapping owned by the mesh-restore tool (single source of truth).
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
try:
    from restore_generative_meshes import RESTORE as _RESTORE_MAP
except Exception:
    _RESTORE_MAP = {}


def _mesh_bbox_m(lib, geometry_hash):
    """Real LOD400 mesh extent (m) from component_geometries float32 vertex triplets."""
    row = lib.execute("SELECT vertices FROM component_geometries WHERE geometry_hash=?",
                      (geometry_hash,)).fetchone()
    if not row or not row[0]:
        return None
    a = array.array("f")
    a.frombytes(row[0])
    if len(a) < 3:
        return None
    xs, ys, zs = a[0::3], a[1::3], a[2::3]
    return (max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs))


def _resolve_hash(lib, erp, device):
    """LOD400 hash cascade: M_Product_Image direct → source_element_ref → I_Geometry_Map.
    Returns (hash, src) — hash None when no REAL mesh exists (walker must REFUSE)."""
    r = lib.execute(
        "SELECT i.geometry_hash FROM M_Product_Image i "
        "JOIN component_geometries g ON g.geometry_hash = i.geometry_hash "
        "WHERE i.M_Product_ID = ? LIMIT 1", (device,)).fetchone()
    if r:
        return r[0], "M_Product_Image"
    refs = []
    r = erp.execute("SELECT source_element_ref FROM M_Product "
                    "WHERE product_id=? AND source_element_ref IS NOT NULL "
                    "AND source_element_ref<>'' LIMIT 1", (device,)).fetchone()
    if r:
        refs.append(r[0])
    # Cascade 3: the canonical device→family mapping maintained by the mesh-restore
    # tool (scripts/restore_generative_meshes.py RESTORE dict) — imported, not copied,
    # so there is exactly one source of truth for the mapping.
    if device in _RESTORE_MAP:
        ref = _RESTORE_MAP[device][0]
        if ref not in refs:
            refs.append(ref)
    for ref in refs:
        m = lib.execute(
            "SELECT i.geometry_hash FROM I_Geometry_Map i "
            "JOIN component_geometries g ON g.geometry_hash = i.geometry_hash "
            "WHERE i.element_ref = ? AND i.ordinal IS NULL LIMIT 1", (ref,)).fetchone()
        if m:
            return m[0], "I_Geometry_Map:" + ref[:40]
    return None, None


def project(rules_db, building_class, patterns_db=DISC_PATTERNS, comp_db=COMPONENT_LIB,
            log=print):
    # §SCOPE BOUNDARY (Watchdog catch, 2026-07-10): ad_space_type_mep_bom is RESIDENTIAL-
    # provenance data (the residential space program: BEDROOM, WET_KITCHEN, VERANDAH...).
    # It must NOT sit in a non-residential class DB waiting to misapply residential
    # quantities the day that class gains real spaces (Terminal today has none, so a
    # leaked table would be dormant — a landmine, not a bug yet; we remove the landmine).
    # There is no per-class column in the source to filter by — any subset list would be
    # INVENTED — so non-residential classes get NO schedule tables at all, and the
    # walker's placeSchedule() no-table guard REFUSEs honestly. A Terminal-class schedule
    # becomes projectable only when mined from real Terminal-class data.
    if building_class != "residential":
        con = sqlite3.connect(rules_db)
        cur = con.cursor()
        dropped = []
        for t in ("rule_space_schedule", "rule_space_type", "rule_space_alias",
                  "rule_code_spacing"):
            if cur.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                           (t,)).fetchone():
                cur.execute(f"DROP TABLE {t}")
                dropped.append(t)
        con.commit()
        con.close()
        log(f"§SCHED-PROJ {os.path.basename(rules_db)}: class '{building_class}' is not "
            f"residential — schedule NOT projected (residential-provenance data)"
            + (f"; healed leak, dropped: {dropped}" if dropped else ""))
        return 0
    for p in (patterns_db, comp_db):
        if not os.path.exists(p):
            log(f"§SCHED-PROJ SKIP — {p} missing")
            return 0
    erp = sqlite3.connect(patterns_db)
    lib = sqlite3.connect(comp_db)

    connects = dict(erp.execute(
        "SELECT assembly_id, connects_to FROM ad_assembly_connector "
        "WHERE connects_to IS NOT NULL AND connects_to<>'' GROUP BY assembly_id"))
    offsets = {r[0]: r[1:] for r in erp.execute(
        "SELECT placement_rule, from_edge_x, from_edge_y, z_offset, z_rule, x_ref, y_ref, "
        "standard FROM ad_placement_offset")}
    # §W5-RATCHET (item 4): per-room Z overrides mined from the real Duplex MEP transforms
    # (ad_placement_offset_space, seeded by scripts/seed_placement_offset_space.py). Z only —
    # x/y stay generic (wall-snap owns them). Probe first: an older disc_patterns.db without
    # the table must project unchanged.
    space_z = {}
    if erp.execute("SELECT 1 FROM sqlite_master WHERE name='ad_placement_offset_space'").fetchone():
        space_z = {(r[0], r[1]): (r[2], r[3], r[4]) for r in erp.execute(
            "SELECT space_type_id, device_id, z_rule, z_offset, n_measured "
            "FROM ad_placement_offset_space")}
    dims = {r[0]: (r[1], r[2], r[3]) for r in erp.execute(
        "SELECT product_id, width, depth, height FROM M_Product "
        "WHERE width>0 AND depth>0 AND height>0")}
    name_ref = dict(erp.execute(
        "SELECT product_id, source_element_ref FROM M_Product "
        "WHERE source_element_ref IS NOT NULL AND source_element_ref<>''"))

    schedule = erp.execute(
        "SELECT space_type_id, mep_product_id, placement_rule, host_surface, anchor_end, "
        "qty_normal, qty_min, qty_max, per_area_normal FROM ad_space_type_mep_bom "
        "ORDER BY space_type_id, mep_product_id").fetchall()

    con = sqlite3.connect(rules_db)
    cur = con.cursor()
    cur.executescript("""
        DROP TABLE IF EXISTS rule_space_schedule;
        CREATE TABLE rule_space_schedule(
            disc TEXT, space_type_id TEXT, device_id TEXT,
            placement_rule TEXT, host_surface TEXT, anchor_end TEXT,
            qty_normal INTEGER, qty_min INTEGER, qty_max INTEGER, per_area_normal REAL,
            edge_x_m REAL, edge_y_m REAL, z_offset_m REAL,
            z_rule TEXT, x_ref TEXT, y_ref TEXT, standard TEXT,
            dim_x_m REAL, dim_y_m REAL, dim_z_m REAL, dim_src TEXT,
            geometry_hash TEXT, mesh_src TEXT, element_name TEXT,
            building_class TEXT, provenance TEXT);
        DROP TABLE IF EXISTS rule_space_type;
        CREATE TABLE rule_space_type(value TEXT PRIMARY KEY, ceiling_m REAL,
            category TEXT, building_class TEXT, provenance TEXT);
        DROP TABLE IF EXISTS rule_space_alias;
        CREATE TABLE rule_space_alias(alias TEXT, space_type_id TEXT,
            building_class TEXT, provenance TEXT);
        DROP TABLE IF EXISTS rule_code_spacing;
        CREATE TABLE rule_code_spacing(element_type TEXT, space_type TEXT,
            max_spacing_m REAL, building_class TEXT, provenance TEXT);
    """)

    hash_cache, refuse = {}, set()
    n = 0
    for (st, dev, rule, host, anchor, qn, qmin, qmax, per_area) in schedule:
        if dev not in hash_cache:
            hash_cache[dev] = _resolve_hash(lib, erp, dev)
        ghash, mesh_src = hash_cache[dev]
        if ghash is None:
            refuse.add(dev)
        d = dims.get(dev)
        dim_src = "M_Product"
        if not d and ghash:
            d = _mesh_bbox_m(lib, ghash)
            dim_src = "mesh_bbox"
        if not d:
            d, dim_src = (None, None, None), None
        off = offsets.get(rule) or (0.0, 0.0, 0.0, "MID", "CENTER", "CENTER", None)
        conn_to = connects.get(dev)
        disc = (CONNECTS_TO_DISC.get(conn_to)
                or ANCHOR_END_DISC.get(anchor or "") or "ELEC")
        z_off, z_rule = off[2], off[3]
        prov = "projected:disc_patterns.ad_space_type_mep_bom+ad_placement_offset"
        sz = space_z.get((st, dev))
        if sz:
            z_rule, z_off = sz[0], sz[1]
            prov += "+space-z:DX n=%d" % sz[2]
        cur.execute(
            "INSERT INTO rule_space_schedule VALUES "
            "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (disc, st, dev, rule, host, anchor, qn, qmin, qmax, per_area,
             off[0], off[1], z_off, z_rule, off[4], off[5], off[6],
             d[0], d[1], d[2], dim_src, ghash, mesh_src,
             name_ref.get(dev, dev), building_class, prov))
        n += 1

    n_types = 0
    for value, mm, category in erp.execute(
            "SELECT Value, COALESCE(default_ceiling_height_mm, 0), category FROM ad_space_type "
            "WHERE is_active=1"):
        # category = ad_space_type.category VERBATIM (incl. NULL) — the source's only per-type
        # class signal; building_class = projection target only (see module docstring).
        cur.execute("INSERT INTO rule_space_type VALUES (?,?,?,?,?)",
                    (value, (mm / 1000.0) if mm and mm > 0 else 2.7, category, building_class,
                     "projected:disc_patterns.ad_space_type"))
        n_types += 1

    n_alias = 0
    for alias, st in lib.execute("SELECT alias, space_type_id FROM ad_space_type_alias"):
        cur.execute("INSERT INTO rule_space_alias VALUES (?,?,?,?)",
                    (alias, st, building_class,
                     "projected:component_library.ad_space_type_alias"))
        n_alias += 1

    n_sp = 0
    for et, st, sp in erp.execute(
            "SELECT element_type, space_type, max_spacing_m FROM ad_code_requirement "
            "WHERE max_spacing_m IS NOT NULL AND max_spacing_m>0"):
        cur.execute("INSERT INTO rule_code_spacing VALUES (?,?,?,?,?)",
                    (et, st, sp, building_class, "projected:disc_patterns.ad_code_requirement"))
        n_sp += 1

    con.commit()
    con.close()
    erp.close()
    lib.close()
    log(f"§SCHED-PROJ {os.path.basename(rules_db)} ← ad_space_type_mep_bom: "
        f"{n} schedule rows, {n_types} space types, {n_alias} aliases, {n_sp} spacing rules "
        f"(building_class={building_class})")
    if refuse:
        log(f"§SCHED-PROJ LOD400-REFUSE list (no real mesh, hash=NULL — walker MUST refuse, "
            f"never fallback): {sorted(refuse)}")
    return n


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("usage: project_rule_space_schedule.py <rules_db> <building_class>",
              file=sys.stderr)
        sys.exit(2)
    project(sys.argv[1], sys.argv[2])
