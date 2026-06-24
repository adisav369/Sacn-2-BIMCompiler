#!/usr/bin/env python3
# ⚠ DO NOT REMOVE — W-SDG-ANCHORED witness for SPATIAL_DEPENDENCY_GRAPH.md (§ANCHORED, the `anchored-to` edge).
# Scope: prove datum planes + the `anchored-to` edge are DERIVED BY MEASURE (face cadence) with ZERO invented
# data and ZERO recovered grid (datums emerge from geometry, not IfcGrid/storeys). Read the §-log as proof.
#
# Issue it proves: organizing datums (gridlines / storey planes / pier stations) can be MEASURED from element
# face cadence — so the edge works where no IfcGrid or IfcBuildingStorey exists (e.g. a bridge). Disproves
# "the grid must be authored/recovered".
#
# Falsifiers (non-tautological, independent of the builder internals):
#   - PHYSICAL: every anchored element genuinely has a face within tol of its datum coord.
#   - SUPPORT: every datum is backed by ≥min_support DISTINCT elements actually within tol.
#   - CRISPNESS: a datum's supporting faces span ≤ tol (no chained pseudo-plane).
#   - ZERO INVENTED + provenance + deterministic recompute.
#
# Run:  source venv/bin/activate && python3 scripts/witness_sdg_anchored.py

import os
import sys
import shutil
import sqlite3
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "DAGCompiler", "python"))
from extractIFCtoDB import derive_datums_and_anchors   # subject under test

TOL = 0.05
MIN_SUPPORT = 3
SCHEMA = (
    "CREATE TABLE IF NOT EXISTS datum_plane (datum_id INTEGER PRIMARY KEY, axis TEXT, coord REAL, "
    "support_count INTEGER, provenance TEXT DEFAULT 'derived:cadence');"
    "CREATE TABLE IF NOT EXISTS rel_anchored (element_guid TEXT, datum_id INTEGER, axis TEXT, offset_mm REAL, "
    "provenance TEXT DEFAULT 'derived:cadence-snap', PRIMARY KEY (element_guid, datum_id));"
)

CASES = [
    ("DAGCompiler/lib/input/SampleHouse_extracted.db", "SH"),
    ("DAGCompiler/lib/input/Duplex_extracted.db", "DX"),
]

passed = 0
failed = 0
AXIS = {"X": 0, "Y": 1, "Z": 2}


def check(name, ok, evidence):
    global passed, failed
    tag = "PASS" if ok else "FAIL"
    passed_local = ok
    if ok:
        passed += 1
    else:
        failed += 1
    print(f"    {tag:4s}  {name:32s}  {evidence}")


def run_case(db, label):
    print(f"\n  §ANCHORED-WITNESS {label}  {db}")
    if not os.path.exists(db):
        check(f"{label}:db-exists", False, f"missing {db}")
        return
    tmp = tempfile.mktemp(suffix=".db")
    shutil.copy(db, tmp)
    conn = sqlite3.connect(tmp)
    conn.executescript(SCHEMA)
    conn.execute("DELETE FROM datum_plane")
    conn.execute("DELETE FROM rel_anchored")
    nd, na = derive_datums_and_anchors(conn)

    # pristine substrate
    aabb = {g: (x0, x1, y0, y1, z0, z1) for g, x0, x1, y0, y1, z0, z1 in conn.execute(
        "SELECT m.guid, r.minX,r.maxX,r.minY,r.maxY,r.minZ,r.maxZ "
        "FROM elements_meta m JOIN elements_rtree r ON m.id=r.id").fetchall()}
    all_guids = set(aabb)
    datums = {d: (ax, co, sc, pv) for d, ax, co, sc, pv in conn.execute(
        "SELECT datum_id, axis, coord, support_count, provenance FROM datum_plane").fetchall()}
    anchors = conn.execute(
        "SELECT element_guid, datum_id, axis, offset_mm, provenance FROM rel_anchored").fetchall()

    print(f"      §LOG datums={nd} anchors={na}")

    # 1) datums emerged at all (cadence is real)
    check(f"{label}:datums-emerged", nd > 0 and na > 0, f"datums={nd} anchors={na}")

    # 2) PHYSICAL — every anchor's element has a face within tol of its datum coord
    bad_phys = []
    for g, did, ax, off, pv in anchors:
        if g not in aabb or did not in datums:
            bad_phys.append((g, did)); continue
        k = AXIS[ax]
        faces = (aabb[g][2 * k], aabb[g][2 * k + 1])
        coord = datums[did][1]
        if min(abs(f - coord) for f in faces) > TOL + 1e-9:
            bad_phys.append((g, did))
    check(f"{label}:every-anchor-within-tol", not bad_phys,
          f"{len(bad_phys)} anchors whose face is NOT within {TOL*1000:.0f}mm of the datum")

    # 3) SUPPORT — every datum has ≥MIN_SUPPORT distinct anchored elements, and stored count matches
    anchored_by_datum = {}
    for g, did, ax, off, pv in anchors:
        anchored_by_datum.setdefault(did, set()).add(g)
    bad_support = [d for d in datums if len(anchored_by_datum.get(d, set())) < MIN_SUPPORT]
    count_mismatch = [d for d in datums if datums[d][2] != len(anchored_by_datum.get(d, set()))]
    check(f"{label}:datum-support>=min", not bad_support, f"{len(bad_support)} datums under {MIN_SUPPORT} elems")
    check(f"{label}:support-count-honest", not count_mismatch,
          f"{len(count_mismatch)} datums whose support_count != distinct anchored elems")

    # 4) CRISPNESS — each datum's anchored faces span ≤ tol (a real plane, not a smear)
    bad_crisp = []
    for did, (ax, coord, sc, pv) in datums.items():
        k = AXIS[ax]
        offs = []
        for g in anchored_by_datum.get(did, ()):
            faces = (aabb[g][2 * k], aabb[g][2 * k + 1])
            offs.append(min(faces, key=lambda f: abs(f - coord)))
        if offs and (max(offs) - min(offs)) > TOL + 1e-9:
            bad_crisp.append(did)
    check(f"{label}:datum-crisp(spread<=tol)", not bad_crisp, f"{len(bad_crisp)} smeared datums")

    # 5) ZERO invented
    invented = [g for g, *_ in anchors if g not in all_guids]
    check(f"{label}:zero-invented-guids", not invented, f"{len(invented)} guids not in substrate")

    # 6) provenance stamps
    bad_pv = [1 for *_, pv in anchors if pv != "derived:cadence-snap"] + \
             [1 for d in datums if datums[d][3] != "derived:cadence"]
    check(f"{label}:provenance-stamped", not bad_pv, f"{len(bad_pv)} wrong provenance")

    # 7) deterministic recompute (same inputs → same datum/anchor counts)
    conn.execute("DELETE FROM datum_plane"); conn.execute("DELETE FROM rel_anchored")
    nd2, na2 = derive_datums_and_anchors(conn)
    check(f"{label}:deterministic", (nd, na) == (nd2, na2), f"first=({nd},{na}) second=({nd2},{na2})")

    # 8) CADENCE SANITY (witness may name axes; builder may not) — a dominant horizontal datum (a level that
    #    many elements rest a face on) emerged on Z.
    z_support = max((datums[d][2] for d in datums if datums[d][0] == "Z"), default=0)
    check(f"{label}:level-plane-emerged(Z)", z_support >= MIN_SUPPORT,
          f"strongest Z datum backed by {z_support} elements")

    conn.close()
    os.remove(tmp)


def main():
    for db, label in CASES:
        run_case(os.path.join(ROOT, db), label)
    print(f"\n  §ANCHORED-RESULT  PASS={passed}  FAIL={failed}")
    if failed:
        print("  ✗ W-SDG-ANCHORED RED")
        sys.exit(1)
    print(f"  ✓ W-SDG-ANCHORED GREEN {passed}/{passed}")


if __name__ == "__main__":
    main()
