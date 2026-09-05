#!/usr/bin/env python3
import sys
# --- utf8-console guard (2026-09-05) ---------------------------------------------
# This script prints non-ASCII (box-drawing, arrows, section marks). On a console whose
# encoding is not UTF-8 -- Windows cp1252 is the common case -- print() raises
# UnicodeEncodeError and kills the script mid-run. That is not hypothetical: it aborted
# scripts/restore_generative_meshes.py immediately after it created its back-compat view
# but BEFORE it restored any mesh, which is why the component_library repair silently
# needed two passes to converge. errors="replace" is deliberate: a mangled glyph in a log
# line is always better than a dead pipeline stage.
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError, OSError):
        pass  # already-wrapped, detached, or replaced by a non-TextIOWrapper (e.g. in tests)
# ---------------------------------------------------------------------------------
# ⚠ DO NOT REMOVE — W-SDG-SPANS witness for SPATIAL_DEPENDENCY_GRAPH.md (§SPANS, the last Phase-1 edge).
# Scope: prove the `spans` edge is DERIVED correctly — an element's bbox genuinely reaches from one datum to
# a DIFFERENT datum on its axis — with zero invented data, on a building AND a bridge. Read the §-log as proof.
#
# Issue it proves: "element stretches between two datums" is a MEASURED fact (bbox endpoints land on two
# distinct datum planes), reusing the cadence-derived datums. The fold rule (stretch between datums, sizes
# held) therefore has a real, checkable substrate. Disproves a hand-authored span/template.
#
# Falsifiers (independent of the builder): every span edge re-measured — min face within tol of datum_lo,
# max face within tol of datum_hi, datum_lo<datum_hi on the stated axis, span_m == measured extent, the two
# datums distinct; plus zero-invented + provenance + deterministic recompute.
#
# Run:  source venv/bin/activate && python3 scripts/witness_sdg_spans.py

import os
import sys
import shutil
import sqlite3
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "DAGCompiler", "python"))
from extractIFCtoDB import derive_datums_and_anchors, derive_spans

TOL = 0.05
AXIS = {"X": 0, "Y": 1, "Z": 2}
SCHEMA = (
    "CREATE TABLE IF NOT EXISTS datum_plane (datum_id INTEGER PRIMARY KEY,axis TEXT,coord REAL,"
    "support_count INTEGER,provenance TEXT);"
    "CREATE TABLE IF NOT EXISTS rel_anchored (element_guid TEXT,datum_id INTEGER,axis TEXT,offset_mm REAL,"
    "provenance TEXT,PRIMARY KEY(element_guid,datum_id));"
    "CREATE TABLE IF NOT EXISTS rel_spans (element_guid TEXT,axis TEXT,datum_lo_id INTEGER,datum_hi_id INTEGER,"
    "span_m REAL,provenance TEXT,PRIMARY KEY(element_guid,axis));"
)
CASES = [
    ("DAGCompiler/lib/input/SampleHouse_extracted.db", "SH"),
    ("DAGCompiler/lib/input/Duplex_extracted.db", "DX"),
    ("DAGCompiler/lib/input/Bridge_extracted.db", "Bridge"),
]

passed = 0
failed = 0


def check(name, ok, evidence):
    global passed, failed
    if ok:
        passed += 1
    else:
        failed += 1
    print(f"    {'PASS' if ok else 'FAIL':4s}  {name:32s}  {evidence}")


def run_case(db, label):
    print(f"\n  §SPANS-WITNESS {label}  {db}")
    if not os.path.exists(db):
        check(f"{label}:db-exists", False, f"missing {db}")
        return
    tmp = tempfile.mktemp(suffix=".db")
    shutil.copy(db, tmp)
    conn = sqlite3.connect(tmp)
    conn.executescript(SCHEMA)
    for t in ("datum_plane", "rel_anchored", "rel_spans"):
        conn.execute(f"DELETE FROM {t}")
    derive_datums_and_anchors(conn)
    n = derive_spans(conn)

    aabb = {g: (x0, x1, y0, y1, z0, z1) for g, x0, x1, y0, y1, z0, z1 in conn.execute(
        "SELECT m.guid,r.minX,r.maxX,r.minY,r.maxY,r.minZ,r.maxZ "
        "FROM elements_meta m JOIN elements_rtree r ON m.id=r.id").fetchall()}
    all_guids = set(aabb)
    dat = {d: (ax, co) for d, ax, co in conn.execute("SELECT datum_id,axis,coord FROM datum_plane")}
    spans = conn.execute(
        "SELECT element_guid,axis,datum_lo_id,datum_hi_id,span_m,provenance FROM rel_spans").fetchall()

    print(f"      §LOG span edges={n}")

    # 1) spans emerged
    check(f"{label}:spans-emerged", n > 0 and len(spans) == n, f"rows={n}")

    # 2) PHYSICAL — every span edge genuinely reaches two distinct datums, sizes consistent
    bad = []
    for g, ax, dlo, dhi, span_m, pv in spans:
        if g not in aabb or dlo not in dat or dhi not in dat:
            bad.append((g, "missing")); continue
        k = AXIS[ax]
        lo_face, hi_face = aabb[g][2 * k], aabb[g][2 * k + 1]
        clo, chi = dat[dlo][1], dat[dhi][1]
        ok = (dat[dlo][0] == ax and dat[dhi][0] == ax            # both datums on the stated axis
              and dlo != dhi                                     # two DISTINCT datums
              and clo <= chi                                     # ordered lo<=hi
              and abs(lo_face - clo) <= TOL + 1e-9               # min face on datum_lo
              and abs(hi_face - chi) <= TOL + 1e-9               # max face on datum_hi
              and abs(span_m - (hi_face - lo_face)) <= 1e-6)     # span_m is the held extent
        if not ok:
            bad.append((g, ax))
    check(f"{label}:every-span-reaches-2-datums", not bad, f"{len(bad)} spans fail physical re-measurement")

    # 3) span_m ≈ datum separation (within 2·tol, since each face is within tol of its datum)
    bad_sep = []
    for g, ax, dlo, dhi, span_m, pv in spans:
        if dlo in dat and dhi in dat:
            sep = abs(dat[dhi][1] - dat[dlo][1])
            if abs(span_m - sep) > 2 * TOL + 1e-9:
                bad_sep.append(g)
    check(f"{label}:span==datum-separation", not bad_sep, f"{len(bad_sep)} spans ≠ datum gap (±2·tol)")

    # 4) zero invented
    invented = [g for g, *_ in spans if g not in all_guids]
    check(f"{label}:zero-invented-guids", not invented, f"{len(invented)} guids not in substrate")

    # 5) provenance
    bad_pv = [1 for *_, pv in spans if pv != "derived:bbox-spans-datums"]
    check(f"{label}:provenance-stamped", not bad_pv, f"{len(bad_pv)} wrong provenance")

    # 6) deterministic
    conn.execute("DELETE FROM rel_spans")
    n2 = derive_spans(conn)
    check(f"{label}:deterministic", n == n2, f"first={n} second={n2}")

    conn.close()
    os.remove(tmp)


def main():
    print("═══ W-SDG-SPANS — element bbox spans two datums, building + bridge, zero invented ═══")
    for db, label in CASES:
        run_case(os.path.join(ROOT, db), label)
    print(f"\n  §SPANS-RESULT  PASS={passed}  FAIL={failed}")
    if failed:
        print("  ✗ W-SDG-SPANS RED"); sys.exit(1)
    print(f"  ✓ W-SDG-SPANS GREEN {passed}/{passed}")


if __name__ == "__main__":
    main()
