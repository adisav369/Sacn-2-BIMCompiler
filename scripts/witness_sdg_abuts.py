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
# ⚠ DO NOT REMOVE — W-SDG-ABUTS witness for SPATIAL_DEPENDENCY_GRAPH.md (§ABUTS, the first DERIVED edge).
# Scope: prove `rel_adjacency` (the `abuts` face-touch edge) is MEASURED from the pristine substrate with
# ZERO invented edges, on a REAL building. Read the §-log lines below as the proof.
#
# Issue it proves: a spatial dependency edge can be DERIVED from a measured property (shared-face contact)
# without a proximity radius and without any IFC class name. It disproves "adjacency must be guessed".
#
# Anti-tautology: the oracle is an INDEPENDENT brute-force O(n²) recompute of face-touch from the raw
# extraction AABBs (a different implementation from the builder's rtree-pruned pass), PLUS a physical
# re-measurement of every emitted edge (a spurious edge cannot survive re-measurement). The oracle never
# trusts the builder's own table.
#
# Run:  source venv/bin/activate && python3 scripts/witness_sdg_abuts.py

import os
import sys
import shutil
import sqlite3
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "DAGCompiler", "python"))
from extractIFCtoDB import derive_adjacency   # subject under test

TOL = 0.03           # must match derive_adjacency defaults
MIN_OVERLAP = 0.02

CASES = [
    ("DAGCompiler/lib/input/SampleHouse_extracted.db", "SH"),
    ("DAGCompiler/lib/input/Duplex_extracted.db", "DX"),
]

passed = 0
failed = 0


def check(name, ok, evidence):
    global passed, failed
    tag = "PASS" if ok else "FAIL"
    if ok:
        passed += 1
    else:
        failed += 1
    print(f"    {tag:4s}  {name:30s}  {evidence}")


def oracle_face_touch(a, b):
    """INDEPENDENT re-derivation of the face-touch predicate (separate implementation from the builder).
    a,b = (minX,maxX,minY,maxY,minZ,maxZ). Returns touch_axis char or None."""
    ovs = []
    for k in range(3):
        ovs.append(min(a[2 * k + 1], b[2 * k + 1]) - max(a[2 * k], b[2 * k]))
    # touch axis = smallest |overlap|
    axis = min(range(3), key=lambda k: abs(ovs[k]))
    if abs(ovs[axis]) > TOL:
        return None
    o = [k for k in range(3) if k != axis]
    if ovs[o[0]] < MIN_OVERLAP or ovs[o[1]] < MIN_OVERLAP:
        return None
    return "XYZ"[axis]


def main():
    for rel_path, label in CASES:
        db = os.path.join(ROOT, rel_path)
        print(f"\n  §ABUTS-WITNESS {label}  {rel_path}")
        if not os.path.exists(db):
            check(f"{label}:db-exists", False, f"missing {db}")
            continue

        # subject under test: run the REAL builder on a throwaway COPY of the reference db
        tmp = tempfile.mktemp(suffix=".db")
        shutil.copy(db, tmp)
        conn = sqlite3.connect(tmp)
        conn.executescript(
            "CREATE TABLE IF NOT EXISTS rel_adjacency (a_guid TEXT, b_guid TEXT, touch_axis TEXT, "
            "gap_mm REAL, contact_m2 REAL, provenance TEXT DEFAULT 'derived:face-touch', "
            "PRIMARY KEY (a_guid, b_guid, touch_axis));")
        conn.execute("DELETE FROM rel_adjacency")
        n = derive_adjacency(conn)

        # pristine substrate for the independent oracle
        elems = conn.execute("""
            SELECT m.guid, m.ifc_class, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta m JOIN elements_rtree r ON m.id = r.id
        """).fetchall()
        aabb = {g: (x0, x1, y0, y1, z0, z1) for g, _, x0, x1, y0, y1, z0, z1 in elems}
        cls = {g: c for g, c, *_ in elems}
        all_guids = set(aabb)

        # independent brute-force O(n²) recompute (no rtree, no builder table)
        oracle = {}
        glist = list(aabb)
        for i in range(len(glist)):
            for j in range(i + 1, len(glist)):
                ga, gb = glist[i], glist[j]
                ax = oracle_face_touch(aabb[ga], aabb[gb])
                if ax:
                    lo, hi = (ga, gb) if ga < gb else (gb, ga)
                    oracle[(lo, hi)] = ax

        table = conn.execute("SELECT a_guid, b_guid, touch_axis, provenance FROM rel_adjacency").fetchall()
        tset = {(a, b): ax for a, b, ax, _ in table}

        print(f"      §LOG builder={n} rows | oracle(brute-force)={len(oracle)} pairs")

        # 1) builder row count == distinct rows == oracle (completeness: no missed, no extra)
        check(f"{label}:count==oracle", n == len(tset) == len(oracle),
              f"builder={n} table={len(tset)} oracle={len(oracle)}")

        # 2) EXACT set match builder vs independent oracle (rtree-pruned == brute-force)
        missed = set(oracle) - set(tset)
        extra = set(tset) - set(oracle)
        check(f"{label}:set==oracle", not missed and not extra,
              f"missed={len(missed)} extra={len(extra)}")

        # 3) touch axis agrees on every shared pair
        axis_mismatch = [p for p in (set(oracle) & set(tset)) if oracle[p] != tset[p]]
        check(f"{label}:axis==oracle", not axis_mismatch, f"{len(axis_mismatch)} axis disagreements")

        # 4) PHYSICAL falsifiability — every emitted edge genuinely face-touches when re-measured
        bad_phys = []
        for (a, b), ax in tset.items():
            if a not in aabb or b not in aabb or oracle_face_touch(aabb[a], aabb[b]) is None:
                bad_phys.append((a, b))
        check(f"{label}:every-edge-really-touches", not bad_phys,
              f"{len(bad_phys)} emitted edges fail physical re-measurement (spurious)")

        # 5) symmetric/dedup — no self-pairs, each unordered pair once, a<b
        self_or_unordered = [(a, b) for a, b, _, _ in table if a == b or a >= b]
        check(f"{label}:no-self-canonical-order", not self_or_unordered,
              f"{len(self_or_unordered)} self/unordered rows")

        # 6) ZERO invented — every guid exists in the substrate
        invented = [g for a, b, _, _ in table for g in (a, b) if g not in all_guids]
        check(f"{label}:zero-invented-guids", not invented, f"{len(invented)} guids not in elements_meta")

        # 7) provenance stamp
        bad_prov = [r for r in table if r[3] != "derived:face-touch"]
        check(f"{label}:provenance=face-touch", not bad_prov, f"{len(bad_prov)} wrong provenance")

        # 8) DOMAIN SANITY (witness may name classes; the BUILDER may not) — furniture/walls SIT ON a slab:
        #    there must be ≥1 face-touch between a slab and a furniture/wall element, on the vertical (Z) axis.
        on_slab_z = 0
        for a, b, ax, _ in table:
            if ax != "Z":
                continue
            ca, cb = cls.get(a, ""), cls.get(b, "")
            pair = ca + "|" + cb
            if "Slab" in pair and ("Furnitur" in pair or "Furnishing" in pair or "Wall" in pair):
                on_slab_z += 1
        check(f"{label}:things-rest-on-slabs(Z)", on_slab_z > 0,
              f"{on_slab_z} slab↔(furniture|wall) vertical contacts found")

        conn.close()
        os.remove(tmp)

    print(f"\n  §ABUTS-RESULT  PASS={passed}  FAIL={failed}")
    if failed:
        print("  ✗ W-SDG-ABUTS RED")
        sys.exit(1)
    print(f"  ✓ W-SDG-ABUTS GREEN {passed}/{passed}")


if __name__ == "__main__":
    main()
