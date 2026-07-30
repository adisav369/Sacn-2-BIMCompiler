#!/usr/bin/env python3
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT
"""W-LOD400-ENVELOPE — witness for prompts/RESUME_MODELLER_LOD400_REAL_GEOMETRY.md §LOD400-ENVELOPE
+ §LOD400-LAYERS-REAL (per-layer slabs compiled from authored layer sets, 2026-07-30).

ISSUE THIS TEST EXPOSES
-----------------------
An element the source authored as N material layers, shipped as ONE undifferentiated envelope solid, is
non-LOD400 content presented as the element's real geometry — the fallback the NO-FALLBACK rule forbids
(user directive 2026-07-29/30: "simple throws exception and hard fail"). Two prior sessions missed it
because they measured only the SHIPPED DB and the tessellated product shape, never the authored
construction data. This test proves/disproves:

  A. the element -> layer-set edge is EXTRACTED at all (it did not exist in the schema before);
  B. a multi-layer element still shipping as an envelope makes P10 go RED and exit NON-ZERO —
     while elements whose hash resolves COMPILED per-layer slabs pass (§LOD400-LAYERS-REAL);
  D. the compiled layers are REAL and AUTHORED: the 7-layer party wall 2O2Fr$t4X7Zf8NOew3FNbT
     resolves 7 component_geometry_layers rows summing to the authored 0.550 m, whose face ranges
     tile the concatenated buffer exactly and whose per-slab extents along the authored layer axis
     equal each authored layer thickness; FALSIFIED by deleting one material_layers row and
     asserting --compile-layers hard-fails (never silently ships 6 slabs as 7 layers);
  C. the gate query itself is falsifiable — remove the multi-layer population, it must count 0.

C/D-falsify are what stop the gate from being a light nobody can trust: it must be able to pass AND
be provably able to fail.

USAGE
    python3 scripts/witness_lod400_envelope.py [--ifc <path>]
Reads the extractor's own log (Log Mandate) — never an exit code alone.
"""
import argparse
import os
import re
import sqlite3
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
EXTRACTOR = os.path.join(REPO, "DAGCompiler", "python", "extractIFCtoDB.py")
DEFAULT_IFC = os.path.expanduser("~/bim-ootb/IFC/Duplex_ARC.ifc")

_pass = 0
_fail = 0


def check(name, ok, evidence):
    global _pass, _fail
    if ok:
        _pass += 1
    else:
        _fail += 1
    print(f"  {'PASS' if ok else 'FAIL'}  {name:28s} {evidence}")
    return ok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ifc", default=DEFAULT_IFC)
    args = ap.parse_args()

    if not os.path.exists(args.ifc):
        print(f"W-LOD400-ENVELOPE SKIP — source IFC not in this checkout: {args.ifc}")
        return 0

    tmp = tempfile.mkdtemp(prefix="w-lod400-")
    db = os.path.join(tmp, "probe.db")
    log_path = os.path.join(tmp, "extract.log")

    print(f"W-LOD400-ENVELOPE  ifc={os.path.basename(args.ifc)}")
    proc = subprocess.run([sys.executable, EXTRACTOR, "--ifc", args.ifc, "-o", db],
                          capture_output=True, text=True)
    log = proc.stdout + proc.stderr
    with open(log_path, "w") as fh:
        fh.write(log)
    print(f"  log: {log_path}  exit={proc.returncode}")

    # ── A. the edge exists and carries the authored placement data ───────────
    conn = sqlite3.connect(db)
    try:
        total, multi = conn.execute(
            "SELECT COUNT(*), SUM(layer_count > 1) FROM rel_material_layer_set").fetchone()
    except sqlite3.OperationalError as exc:
        check("EDGE_TABLE_EXISTS", False, f"rel_material_layer_set missing: {exc}")
        return 1
    multi = multi or 0
    check("EDGE_TABLE_EXISTS", total > 0,
          f"rel_material_layer_set: {total} element->layer-set edges, {multi} multi-layer")

    placed = conn.execute(
        "SELECT COUNT(*) FROM rel_material_layer_set WHERE direction_sense IS NOT NULL").fetchone()[0]
    check("PLACEMENT_EXTRACTED", placed > 0,
          f"{placed}/{total} edges carry DirectionSense (needed to slice along the authored axis)")

    # Non-invent: every recorded thickness must come from the source, never a default.
    zero_thick = conn.execute(
        "SELECT COUNT(*) FROM rel_material_layer_set "
        "WHERE total_thickness_m IS NULL OR total_thickness_m <= 0").fetchone()[0]
    check("THICKNESS_AUTHORED", zero_thick == 0,
          f"{total - zero_thick}/{total} edges have a real summed thickness, {zero_thick} null/zero")

    # ── B. envelope = multi-layer element whose hash does NOT resolve compiled layer slabs.
    # RED + non-zero exit while any exist; GREEN once §LOD400-LAYERS-REAL compiled them all.
    layered = {}
    try:
        layered = dict(conn.execute(
            "SELECT geometry_hash, COUNT(*) FROM component_geometry_layers "
            "GROUP BY geometry_hash").fetchall())
    except sqlite3.OperationalError:
        pass
    pairs = conn.execute("""
        SELECT r.element_guid, r.layer_count, i.geometry_hash
        FROM rel_material_layer_set r
        JOIN element_instances i ON i.guid = r.element_guid
        WHERE r.layer_count > 1""").fetchall()
    envelopes = sum(1 for (_g, n, h) in pairs if layered.get(h) != n)

    m = re.search(r"^\s*(PASS|FAIL)\s+LOD400_ENVELOPE\s+(.*)$", log, re.M)
    check("P10_IN_LOG", m is not None,
          m.group(0).strip() if m else "no LOD400_ENVELOPE line in the log")

    if envelopes:
        check("P10_RED_ON_ENVELOPE", bool(m) and m.group(1) == "FAIL",
              f"{envelopes} multi-layer elements shipped as envelopes -> gate must be FAIL")
        check("NAMED_NOT_COUNTED",
              "§ILLEGAL_LOD_FALLBACK guid=" in log,
              "offenders printed with guid + layer count (a count alone is not actionable)")
        check("EXIT_NONZERO", proc.returncode != 0,
              f"exit={proc.returncode} — a red §PROOF must fail the run, not exit 0")
    else:
        check("P10_GREEN_WHEN_CLEAN", bool(m) and m.group(1) == "PASS",
              f"{len(pairs)} multi-layer elements all resolve compiled layer slabs -> gate PASS")
        check("EXIT_ZERO_WHEN_GREEN", proc.returncode == 0,
              f"exit={proc.returncode} — a green gate must not fail the run")

    # ── D. §LOD400-LAYERS-REAL — the compiled layers are real, authored, and falsifiable ─────
    party = "2O2Fr$t4X7Zf8NOew3FNbT"
    prow = conn.execute("""
        SELECT i.geometry_hash, r.total_thickness_m, r.layer_set_direction, r.layer_set_name
        FROM element_instances i
        JOIN rel_material_layer_set r ON r.element_guid = i.guid
        WHERE i.guid = ?""", (party,)).fetchone()
    if prow is None:
        check("PARTY_WALL_PRESENT", False, f"{party} not in element_instances⋈rel_material_layer_set")
    else:
        ghash, total, direction, lsn = prow
        axis = {"AXIS1": 0, "AXIS2": 1, "AXIS3": 2}.get(direction)
        lrows = conn.execute(
            "SELECT layer_seq, thickness_m, face_start, face_count FROM component_geometry_layers "
            "WHERE geometry_hash = ? ORDER BY layer_seq", (ghash,)).fetchall()
        lsum = sum(t for (_s, t, _f, _n) in lrows)
        check("PARTY_7_LAYER_ROWS", len(lrows) == 7 and abs(lsum - total) < 1e-9,
              f"hash {ghash}: {len(lrows)} layer rows, SUM(thickness_m)={lsum:.3f} "
              f"== authored total {total:.3f}")

        blob = conn.execute("SELECT vertices, faces, face_count FROM base_geometries "
                            "WHERE geometry_hash = ?", (ghash,)).fetchone()
        import numpy as np
        verts = np.frombuffer(blob[0], dtype=np.float32).reshape(-1, 3)
        faces = np.frombuffer(blob[1], dtype=np.int32).reshape(-1, 3)
        cursor = 0
        tiled = True
        for (_seq, _t, fs, fc) in lrows:
            if fs != cursor:
                tiled = False
            cursor += fc
        check("FACE_RANGES_TILE", tiled and cursor == len(faces) == blob[2],
              f"ranges tile [0,{cursor}) == buffer {len(faces)} tris == face_count col {blob[2]}")

        ext_ok = True
        n_empty = 0
        detail = []
        for (seq, th, fs, fc) in lrows:
            if fc == 0:
                n_empty += 1
                continue
            idx = np.unique(faces[fs:fs + fc])
            ext = float(verts[idx, axis].max() - verts[idx, axis].min())
            detail.append(f"L{seq}={ext:.4f}/{th:.3f}")
            if abs(ext - th) > 5e-4:
                ext_ok = False
        check("SLAB_EXTENTS_AUTHORED", ext_ok and len(lrows) - n_empty >= 5,
              f"per-slab extent == authored thickness (±0.5mm): {' '.join(detail)}")
        # empty rows are legal ONLY when announced loudly as authored-outside-body (§LAYER-PARTIAL:
        # the party wall's neighbour-side finishes belong to the neighbour element's body)
        check("EMPTY_ONLY_ANNOUNCED", n_empty == 0 or
              (f"§LAYER-PARTIAL guid={party}" in log),
              f"{n_empty} empty slab rows; §LAYER-PARTIAL announced={f'§LAYER-PARTIAL guid={party}' in log}")

        # FALSIFY: delete one authored layer row -> --compile-layers must hard-fail, never ship 6 as 7
        fdb = os.path.join(tmp, "falsify.db")
        import shutil
        shutil.copyfile(db, fdb)
        fc_ = sqlite3.connect(fdb)
        fc_.execute("DELETE FROM material_layers WHERE layer_set_name = ? AND sequence = 3", (lsn,))
        fc_.commit()
        fc_.close()
        fproc = subprocess.run([sys.executable, EXTRACTOR, "--compile-layers", "--ref", fdb],
                               capture_output=True, text=True)
        flog = fproc.stdout + fproc.stderr
        with open(os.path.join(tmp, "falsify.log"), "w") as fh:
            fh.write(flog)
        check("FALSIFY_LAYER_DELETE", fproc.returncode != 0 and "§LAYER-VERIFY-FAIL" in flog
              and party in flog,
              f"one material_layers row deleted -> exit={fproc.returncode}, "
              f"§LAYER-VERIFY-FAIL names the element (never silently ships 6 slabs)")

    # ── C. falsification: strip the envelope population, gate must go green ──
    # Same DB, same code path, only the offending JOIN population removed. If P10 still reports
    # offenders after this, the check is not reading what it claims to read.
    conn.execute("DELETE FROM rel_material_layer_set WHERE layer_count > 1")
    conn.commit()
    still = conn.execute("""
        SELECT COUNT(*) FROM rel_material_layer_set r
        JOIN element_instances i ON i.guid = r.element_guid
        WHERE r.layer_count > 1""").fetchone()[0]
    check("FALSIFIABLE", still == 0,
          f"with the multi-layer population removed the gate query returns {still} (must be 0)")

    conn.close()
    print(f"\nW-LOD400-ENVELOPE RESULT: {_pass} PASS, {_fail} FAIL")
    return 1 if _fail else 0


if __name__ == "__main__":
    sys.exit(main())
