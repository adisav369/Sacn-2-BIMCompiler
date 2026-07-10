#!/usr/bin/env python3
"""
Restore generative-MEP-device geometry into component_library.db — Witness: W-GEN-GEOMAP-RESTORE
Implementing RESUME_FACING_GATE.md §NEXT-1 (gate reconciliation).

ROOT CAUSE (proven NON-INVENT 2026-06-22):
  The 2026-06-22 foundation resync (facing session) REBUILT I_Geometry_Map to hold ONLY
  the freshly re-extracted SampleHouse + Duplex rows. That rebuild
    (a) DROPPED every generative-device type-level row (Fancoil/AIRCON_POINT, fans,
        sprinkler, diffuser, ...) the discipline callout + CL_001 depend on, and
    (b) DELETED the meshes that several surviving M_Product_Image rows pointed at
        (TOILET/SINK/LIGHT/SWITCH/OUTLET/FRIDGE) — leaving them dangling.
  Result: BuildingRegistryTest hard-fails "No geometry for IfcFlowTerminal ... familyRef=...".

FIX (NON-INVENT, reproducible, idempotent):
  For each generative product CL_001 needs, ensure ONE real mesh + a type-level
  (ordinal IS NULL) I_Geometry_Map row exists, sourced verbatim from a genuine
  extraction — either the in-library master `ad_geometry_map` (the un-renamed full
  map) or, where the family is absent there, the committed source building DB that
  originally provided it (per DV046 provenance). Nothing is fabricated; every
  (element_ref -> geometry_hash) pair and every mesh blob is copied from real
  extracted geometry. Then drop any still-dangling M_Product_Image so CL_001's
  INSERT-OR-IGNORE re-seeds it from the restored meshes.

  These are device families (no building_type) — NOT SampleHouse/Duplex building
  geometry — so there is ZERO conflict with the fresh facing-carrying SH/DX meshes
  (the RESUME_FACING_GATE.md §CONSISTENCY-TRAP does not apply).

Run BEFORE CL_001 in the run_RosettaStones pre-flight.
"""
import sqlite3, os, sys

LIB = "library/component_library.db"

# product_id -> (element_ref CL_001 queries, source).
#   source 'master'           : the in-library ad_geometry_map full map (exact element_ref).
#   source ('master_like', s) : ad_geometry_map element_ref LIKE %s% (FLOOR_TRAP: real jkrAR16 trap).
#   source ('building', db, name_prefix) : import mesh blob from a committed source extraction.
# Every source was verified to contain a REAL mesh for the family (see session log).
RESTORE = {
    # already in the in-library master (Duplex/Terminal extractions) — restore type row:
    'AIRCON_POINT':   ('Fancoil Unit:ACSU-02', 'master'),
    'EXHAUST_FAN':    ('M_Exhaust FAn Ventilation:1200mm x 1100mm', 'master'),
    'CEILING_FAN':    ('E_Fan_Ceiling_1500mm_V1:Ceiling Fan', 'master'),
    'TOILET':         ('M_Water Closet - Flush Tank:Private - 6.1 Lpf:Private - 6.1 Lpf', 'master'),
    'SINK':           ('M_Sink - Island - Single:455 mmx455 mm - Private:455 mmx455 mm - Private', 'master'),
    'LIGHT':          ('M_Pendant Light - Hemisphere:150W - 120V:150W - 120V', 'master'),
    'SWITCH':         ('M_Lighting Switches:Single Pole:Single Pole', 'master'),
    'OUTLET':         ('M_Duplex Receptacle:Duplex Receptacle:Duplex Receptacle', 'master'),
    'FRIDGE':         ('M_Refrigerator:850 x 760mm:850 x 760mm', 'master'),
    # absent from in-library master — import the EXACT mesh from its source extraction:
    'SPRINKLER':      ('M_Sprinkler - Pendent - Hosted:15 mm Pendent on Drop with Guard',
                       ('building', 'deploy/buildings/HHS_Office_Federated_extracted.db',
                        'M_Sprinkler - Pendent - Hosted:15 mm Pendent on Drop with Guard', 'IfcFlowTerminal')),
    'SUPPLY_DIFFUSER':('M_Supply Diffuser - Rectangular Face Round Neck:600x600 - 250 Neck',
                       ('building', 'deploy/buildings/HHS_Office_Federated_extracted.db',
                        'M_Supply Diffuser - Rectangular Face Round Neck:600x600 - 250 Neck', 'IfcFlowTerminal')),
    'OUTLET_20A':     ('M_Duplex Receptacle:Standard',
                       ('building', 'deploy/buildings/Clinic_extracted.db',
                        'M_Duplex Receptacle:Standard', 'IfcFlowTerminal')),
    'OUTLET_GFCI':    ('M_Duplex Receptacle:GFCI',
                       ('building', 'deploy/buildings/Clinic_extracted.db',
                        'M_Duplex Receptacle:GFCI', 'IfcFlowTerminal')),
    # FLOOR_TRAP: CL_001 matches LIKE %Floor Trap% — the real jkrAR16 trap is in master:
    'FLOOR_TRAP':     ('jkrAR16_plb-fx_LSf_TDS_(LSf001)-3 Floor Trap:jkrAR16_plb-fx_LSf_TDS_(LSf001)-3 Floor Trap',
                       ('master_like', 'Floor Trap')),
}


def has_type_row(cur, element_ref):
    cur.execute("SELECT 1 FROM I_Geometry_Map WHERE element_ref=? AND ordinal IS NULL LIMIT 1", (element_ref,))
    return cur.fetchone() is not None


def mesh_present(cur, h):
    cur.execute("SELECT 1 FROM component_geometries WHERE geometry_hash=? LIMIT 1", (h,))
    return cur.fetchone() is not None


def insert_type_row(cur, element_ref, ifc_class, h, src):
    cur.execute(
        "INSERT INTO I_Geometry_Map (building_type, element_ref, ifc_class, storey, ordinal, geometry_hash, source, provenance) "
        "VALUES (NULL, ?, ?, NULL, NULL, ?, ?, 'LIBRARY')",
        (element_ref, ifc_class, h, src))


def main():
    if not os.path.exists(LIB):
        print(f"[RESTORE] FATAL: {LIB} missing"); sys.exit(1)
    con = sqlite3.connect(LIB); cur = con.cursor()
    # §SELF-HEAL (Watchdog correction 2026-07-10): after the ad_geometry_map → I_Geometry_Map rename this
    # script CRASHED unless the back-compat VIEW had been created by hand (JavaEra_FOSSIL_README.md recipe
    # step 3) — the one manual step keeping the complib repair from being a single committed command.
    # Create it here (pure alias, no data): the repair is now `python3 scripts/restore_generative_meshes.py`.
    cur.execute("SELECT 1 FROM sqlite_master WHERE name='ad_geometry_map'")
    if not cur.fetchone():
        cur.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='I_Geometry_Map'")
        if cur.fetchone():
            cur.execute("CREATE VIEW ad_geometry_map AS SELECT * FROM I_Geometry_Map")
            print("[RESTORE] §SELF-HEAL created back-compat view ad_geometry_map → I_Geometry_Map")
    else:
        # Recipe step 3's OTHER half: a pre-rename complib (ad_geometry_map still a TABLE, e.g. the
        # pristine LFS checkout) gets the rename + view, verbatim from the fossil README. NOTE a fully
        # pristine checkout ALSO lacks M_Product_Image (recipe step 2, pre_s173 restore) — that Java-era
        # path stays the README's documented manual recipe, deliberately not automated here (fossil).
        cur.execute("SELECT type FROM sqlite_master WHERE name='ad_geometry_map'")
        if cur.fetchone()[0] == 'table' and not cur.execute(
                "SELECT 1 FROM sqlite_master WHERE name='I_Geometry_Map'").fetchone():
            cur.execute("ALTER TABLE ad_geometry_map RENAME TO I_Geometry_Map")
            cur.execute("CREATE VIEW ad_geometry_map AS SELECT * FROM I_Geometry_Map")
            print("[RESTORE] §SELF-HEAL renamed ad_geometry_map → I_Geometry_Map + back-compat view")
    restored, skipped, unresolved = [], [], []

    for prod, (element_ref, source) in RESTORE.items():
        if has_type_row(cur, element_ref):
            skipped.append(prod); continue

        if source == 'master':
            cur.execute(
                "SELECT a.ifc_class, MIN(a.geometry_hash) FROM ad_geometry_map a "
                "JOIN component_geometries g ON g.geometry_hash=a.geometry_hash "
                "WHERE a.element_ref=? GROUP BY a.ifc_class LIMIT 1", (element_ref,))
            r = cur.fetchone()
            if not r or not r[1]:
                unresolved.append((prod, 'master: no real-mesh row')); continue
            insert_type_row(cur, element_ref, r[0], r[1], 'restored_from_master')
            restored.append(f"{prod} <- master {element_ref[:40]} hash={r[1]}")

        elif source[0] == 'master_like':
            patt = f"%{source[1]}%"
            cur.execute(
                "SELECT a.element_ref, a.ifc_class, MIN(a.geometry_hash) FROM ad_geometry_map a "
                "JOIN component_geometries g ON g.geometry_hash=a.geometry_hash "
                "WHERE a.element_ref LIKE ? GROUP BY a.element_ref, a.ifc_class LIMIT 1", (patt,))
            r = cur.fetchone()
            if not r or not r[2]:
                unresolved.append((prod, f'master_like {source[1]}: none')); continue
            insert_type_row(cur, r[0], r[1], r[2], 'restored_from_master')
            restored.append(f"{prod} <- master~ {r[0][:40]} hash={r[2]}")

        elif source[0] == 'building':
            _, srcdb, name_prefix, ifc_class = source
            if not os.path.exists(srcdb):
                unresolved.append((prod, f'source DB missing: {srcdb}')); continue
            scon = sqlite3.connect(srcdb); scur = scon.cursor()
            scur.execute(
                "SELECT ei.geometry_hash, cg.vertices, cg.faces "
                "FROM elements_meta em JOIN element_instances ei ON ei.guid=em.guid "
                "JOIN component_geometries cg ON cg.geometry_hash=ei.geometry_hash "
                "WHERE (em.element_name=? OR em.element_name LIKE ?) "
                "AND cg.vertices IS NOT NULL AND cg.faces IS NOT NULL LIMIT 1",
                (name_prefix, name_prefix + ':%'))
            sr = scur.fetchone(); scon.close()
            if not sr:
                unresolved.append((prod, f'{os.path.basename(srcdb)}: no mesh for {name_prefix}')); continue
            h, vblob, fblob = sr
            vcount, fcount = len(vblob) // 12, len(fblob) // 12   # 3x float32 / 3x int32
            if not mesh_present(cur, h):
                cur.execute(
                    "INSERT INTO component_geometries (geometry_hash, vertices, faces, normals, vertex_count, face_count) "
                    "VALUES (?, ?, ?, NULL, ?, ?)", (h, vblob, fblob, vcount, fcount))
            insert_type_row(cur, element_ref, ifc_class, h, f'restored_from:{os.path.basename(srcdb)}')
            restored.append(f"{prod} <- {os.path.basename(srcdb)} hash={h} v={vcount} f={fcount}")

    # Drop dangling M_Product_Image (mesh deleted by the resync) so CL_001 re-seeds them.
    cur.execute("SELECT M_Product_ID FROM M_Product_Image mi "
                "WHERE NOT EXISTS (SELECT 1 FROM component_geometries g WHERE g.geometry_hash=mi.geometry_hash)")
    dangling = [r[0] for r in cur.fetchall()]
    cur.execute("DELETE FROM M_Product_Image WHERE geometry_hash NOT IN (SELECT geometry_hash FROM component_geometries)")

    con.commit()
    # §RESTORE whitebox log
    for line in restored: print(f"  §RESTORE restored: {line}")
    if skipped:    print(f"  §RESTORE skipped (already present): {', '.join(skipped)}")
    if dangling:   print(f"  §RESTORE dropped dangling M_Product_Image: {', '.join(dangling)} (CL_001 re-seeds)")
    if unresolved:
        print("  §RESTORE UNRESOLVED (no genuine source mesh — honest gap, NOT fabricated):")
        for p, why in unresolved: print(f"    - {p}: {why}")
    print(f"  §RESTORE summary: {len(restored)} restored, {len(skipped)} already-present, {len(unresolved)} unresolved")
    con.close()


if __name__ == '__main__':
    main()
