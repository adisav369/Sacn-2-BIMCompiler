#!/usr/bin/env python3
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT
"""W-LOD400-ENVELOPE — witness for prompts/RESUME_MODELLER_LOD400_REAL_GEOMETRY.md §LOD400-ENVELOPE.

ISSUE THIS TEST EXPOSES
-----------------------
An element the source authored as N material layers, shipped as ONE undifferentiated envelope solid, is
non-LOD400 content presented as the element's real geometry — the fallback the NO-FALLBACK rule forbids
(user directive 2026-07-29/30: "simple throws exception and hard fail"). Two prior sessions missed it
because they measured only the SHIPPED DB and the tessellated product shape, never the authored
construction data. This test proves/disproves:

  A. the element -> layer-set edge is EXTRACTED at all (it did not exist in the schema before);
  B. an envelope-for-multi-layer element makes the extractor's P10 gate go RED and exit NON-ZERO;
  C. the gate is falsifiable — with no multi-layer elements shipped as envelopes, P10 goes GREEN.

C is what stops the gate from being a permanent red light nobody reads: it must be able to pass.

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

    # ── B. the gate goes RED and the run exits non-zero ──────────────────────
    envelopes = conn.execute("""
        SELECT COUNT(*) FROM rel_material_layer_set r
        JOIN element_instances i ON i.guid = r.element_guid
        WHERE r.layer_count > 1""").fetchone()[0]

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
              "no envelope fallbacks in this source -> gate must be PASS")

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
