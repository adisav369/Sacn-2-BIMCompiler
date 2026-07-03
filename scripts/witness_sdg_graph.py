#!/usr/bin/env python3
# ⚠ DO NOT REMOVE — W-SDG-GRAPH: the integrative Spatial Dependency Graph witness (SPATIAL_DEPENDENCY_GRAPH.md
# Phase 1). Proves the SAME edge-builders materialize a coherent, zero-invented graph on BOTH a building (SH,
# IFC4, has storeys + voids) AND a non-building (Infra-Bridge, IFC4X3, NO grid, NO storeys, NO voids) — the
# construct-generality claim. Read the §-log as proof.
#
# Issue it proves: the graph is construct-agnostic. The recovered edges (contains/hosted-by) reflect the source
# faithfully (hosted-by is 0 on the bridge — no fabrication where the source has none); the derived edges
# (abuts/anchored-to) EMERGE by measure on the bridge with no authored grid/storeys. Disproves "this only works
# for buildings".
#
# Oracle: an INDEPENDENT second read of each source IFC (aggregate pairs, voids∩fills, grid/storey counts) +
# physical re-measurement of every derived edge. Never trusts the builders' own output.
#
# Regenerate the bridge db first if absent:
#   python3 DAGCompiler/python/extractIFCtoDB.py --ifc reference/infrastructure/Infra-Bridge.ifc \
#       -o DAGCompiler/lib/input/Bridge_extracted.db
# Run:  source venv/bin/activate && python3 scripts/witness_sdg_graph.py

import os
import sys
import shutil
import sqlite3
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "DAGCompiler", "python"))
import ifcopenshell
from extractIFCtoDB import extract_rel_fills_host, derive_adjacency, derive_datums_and_anchors

TOL = 0.05
AXIS = {"X": 0, "Y": 1, "Z": 2}

# (ifc, extracted_db, label, expect_hosted_by, source_has_grid_or_storey)
CASES = [
    ("reference/residential/Ifc4_SampleHouse.ifc",
     "DAGCompiler/lib/input/SampleHouse_extracted.db", "SH-building", 7, True),
    ("reference/infrastructure/Infra-Bridge.ifc",
     "DAGCompiler/lib/input/Bridge_extracted.db", "Bridge-infra", 0, False),
]

passed = 0
failed = 0


def check(name, ok, evidence):
    global passed, failed
    if ok:
        passed += 1
    else:
        failed += 1
    print(f"    {'PASS' if ok else 'FAIL':4s}  {name:34s}  {evidence}")


def oracle_face_touch(a, b):
    ov = [min(a[2 * k + 1], b[2 * k + 1]) - max(a[2 * k], b[2 * k]) for k in range(3)]
    axis = min(range(3), key=lambda k: abs(ov[k]))
    if abs(ov[axis]) > TOL:
        return False
    o = [k for k in range(3) if k != axis]
    return ov[o[0]] >= 0.02 and ov[o[1]] >= 0.02


def run_case(ifc_path, db_path, label, expect_hosted, has_grid_storey):
    print(f"\n  §GRAPH-WITNESS {label}")
    if not os.path.exists(db_path):
        check(f"{label}:db-exists", False, f"missing {db_path} — extract it (see header)")
        return
    f = ifcopenshell.open(ifc_path)
    ifc_guids = {e.GlobalId for e in f.by_type("IfcRoot") if getattr(e, "GlobalId", None)}
    # independent oracles from the IFC
    agg_pairs = sum(1 for rel in f.by_type("IfcRelAggregates") for _ in rel.RelatedObjects)
    n_grid = len(f.by_type("IfcGrid"))
    n_storey = len(f.by_type("IfcBuildingStorey"))
    n_voids = len(f.by_type("IfcRelVoidsElement"))

    # run the REAL builders fresh on a copy
    tmp = tempfile.mktemp(suffix=".db")
    shutil.copy(db_path, tmp)
    conn = sqlite3.connect(tmp)
    conn.executescript(
        "CREATE TABLE IF NOT EXISTS rel_fills_host (opening_guid TEXT PRIMARY KEY, host_guid TEXT, "
        "filling_guid TEXT, host_class TEXT, filling_class TEXT, provenance TEXT);"
        "CREATE TABLE IF NOT EXISTS rel_adjacency (a_guid TEXT,b_guid TEXT,touch_axis TEXT,gap_mm REAL,"
        "contact_m2 REAL,provenance TEXT,PRIMARY KEY(a_guid,b_guid,touch_axis));"
        "CREATE TABLE IF NOT EXISTS datum_plane (datum_id INTEGER PRIMARY KEY,axis TEXT,coord REAL,"
        "support_count INTEGER,provenance TEXT);"
        "CREATE TABLE IF NOT EXISTS rel_anchored (element_guid TEXT,datum_id INTEGER,axis TEXT,offset_mm REAL,"
        "provenance TEXT,PRIMARY KEY(element_guid,datum_id));")
    for t in ("rel_fills_host", "rel_adjacency", "datum_plane", "rel_anchored"):
        conn.execute(f"DELETE FROM {t}")
    hb, hb_filled = extract_rel_fills_host(f, conn)
    n_adj = derive_adjacency(conn)
    n_dat, n_anc = derive_datums_and_anchors(conn)

    aabb = {g: (x0, x1, y0, y1, z0, z1) for g, x0, x1, y0, y1, z0, z1 in conn.execute(
        "SELECT m.guid,r.minX,r.maxX,r.minY,r.maxY,r.minZ,r.maxZ "
        "FROM elements_meta m JOIN elements_rtree r ON m.id=r.id").fetchall()}
    geom_guids = set(aabb)
    agg_rows = conn.execute("SELECT parent_guid, child_guid FROM rel_aggregates").fetchall()
    datum_coord = {d: (ax, co) for d, ax, co, *_ in conn.execute(
        "SELECT datum_id,axis,coord,support_count,provenance FROM datum_plane").fetchall()}

    print(f"      §LOG {label}: contains={len(agg_rows)} hosted-by={hb_filled} abuts={n_adj} "
          f"datums={n_dat} anchored={n_anc} | source: agg_pairs={agg_pairs} voids={n_voids} "
          f"grid={n_grid} storey={n_storey}")

    # ── recovered edges reflect the source faithfully ──
    check(f"{label}:contains==IFC-aggregates", len(agg_rows) == agg_pairs,
          f"graph={len(agg_rows)} IFC_pairs={agg_pairs}")
    check(f"{label}:hosted-by==voids-filled", hb_filled == expect_hosted,
          f"hosted-by={hb_filled} expected={expect_hosted} (source voids={n_voids})")

    # ── ZERO INVENTED across every edge type ──
    inv_agg = [g for p, c in agg_rows for g in (p, c) if g not in ifc_guids]
    adj = conn.execute("SELECT a_guid,b_guid FROM rel_adjacency").fetchall()
    inv_adj = [g for a, b in adj for g in (a, b) if g not in geom_guids]
    anc = conn.execute("SELECT element_guid,datum_id,axis FROM rel_anchored").fetchall()
    inv_anc = [g for g, *_ in anc if g not in geom_guids]
    check(f"{label}:zero-invented(all-edges)", not (inv_agg or inv_adj or inv_anc),
          f"contains={len(inv_agg)} abuts={len(inv_adj)} anchored={len(inv_anc)} invented guids")

    # ── derived edges physically true ──
    bad_adj = [(a, b) for a, b in adj if a not in aabb or b not in aabb or not oracle_face_touch(aabb[a], aabb[b])]
    check(f"{label}:abuts-really-touch", not bad_adj, f"{len(bad_adj)} spurious face-touch edges")
    bad_anc = []
    for g, did, ax in anc:
        k = AXIS[ax]; co = datum_coord[did][1]
        faces = (aabb[g][2 * k], aabb[g][2 * k + 1])
        if min(abs(fc - co) for fc in faces) > TOL + 1e-9:
            bad_anc.append((g, did))
    check(f"{label}:anchored-within-tol", not bad_anc, f"{len(bad_anc)} anchors off-datum")

    # ── THE GENERALITY HEADLINE: derived edges emerge regardless of authored grid/storeys ──
    if not has_grid_storey:
        check(f"{label}:datums-emerged-WITHOUT-grid/storey", n_grid == 0 and n_storey == 0 and n_dat > 0,
              f"source grid={n_grid} storey={n_storey} → derived datums={n_dat} (measured, not recovered)")
        check(f"{label}:no-fabricated-hosted-by", n_voids == 0 and hb_filled == 0,
              f"source has 0 voids → 0 hosted-by edges (builder fabricates nothing)")
    else:
        check(f"{label}:graph-rich-on-building", n_dat > 0 and n_adj > 0 and hb_filled > 0,
              f"datums={n_dat} abuts={n_adj} hosted-by={hb_filled}")

    conn.close()
    os.remove(tmp)


def main():
    print("═══ W-SDG-GRAPH — same edge-builders, a building AND a bridge, zero invented ═══")
    for ifc, db, label, eh, gs in CASES:
        run_case(os.path.join(ROOT, ifc), os.path.join(ROOT, db), label, eh, gs)
    print(f"\n  §GRAPH-RESULT  PASS={passed}  FAIL={failed}")
    if failed:
        print("  ✗ W-SDG-GRAPH RED"); sys.exit(1)
    print(f"  ✓ W-SDG-GRAPH GREEN {passed}/{passed} — the graph is construct-agnostic "
          "(house + bridge, recovered+derived edges, zero invented)")


if __name__ == "__main__":
    main()
