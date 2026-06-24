#!/usr/bin/env python3
# ⚠ DO NOT REMOVE — W-SDG-PATHB witness for SPATIAL_DEPENDENCY_GRAPH.md Phase 1 (§PATHB).
# Scope: prove `rel_fills_host` recovers the authored void/fill chain on a REAL building with
# ZERO invented edges. Oracle = an INDEPENDENT second read of the source IFC (anti-tautology:
# the oracle never reads the extractor's own table). Read the §-log lines below as the proof.
#
# Issue it proves: Path B (door/window → opening → host wall) is RECOVERABLE from source IFC and
# was being DROPPED at extraction. It disproves "the host link must be proximity-guessed".
#
# Run:  source venv/bin/activate && python3 scripts/witness_pathb_rel_fills_host.py

import os
import sys
import sqlite3

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "DAGCompiler", "python"))

import ifcopenshell
from extractIFCtoDB import extract_rel_fills_host, REFERENCE_SCHEMA

# (ifc_path, label, expected_filled_host_edges, expected_filling_classes)
CASES = [
    ("reference/residential/Ifc4_SampleHouse.ifc", "SH-Ifc4", 7,
     {"IfcDoor": 3, "IfcWindow": 4}),
    ("reference/residential/Ifc2x3_Duplex_Architecture.ifc", "DX-Arch", 38, None),
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
    print(f"    {tag:4s}  {name:28s}  {evidence}")


def oracle_voidfill(ifc_file):
    """Independent re-derivation straight from the IFC — the anti-tautology oracle.

    Returns (filled_host_edge_count, filling_class_counts, all_guids set)."""
    host_of_opening = {}
    for rel in ifc_file.by_type("IfcRelVoidsElement"):
        op = rel.RelatedOpeningElement
        if op and getattr(op, "GlobalId", None):
            host_of_opening[op.GlobalId] = rel.RelatingBuildingElement
    fill_of_opening = {}
    for rel in ifc_file.by_type("IfcRelFillsElement"):
        op = rel.RelatingOpeningElement
        if op and getattr(op, "GlobalId", None):
            fill_of_opening[op.GlobalId] = rel.RelatedBuildingElement
    filled = 0
    classes = {}
    for og, host in host_of_opening.items():
        if not host or not getattr(host, "GlobalId", None):
            continue
        fl = fill_of_opening.get(og)
        if fl and getattr(fl, "GlobalId", None):
            filled += 1
            classes[fl.is_a()] = classes.get(fl.is_a(), 0) + 1
    return len(host_of_opening), filled, classes


def main():
    for rel_path, label, exp_filled, exp_classes in CASES:
        ifc_path = os.path.join(ROOT, rel_path)
        print(f"\n  §PATHB-WITNESS {label}  {rel_path}")
        if not os.path.exists(ifc_path):
            check(f"{label}:source-exists", False, f"missing {ifc_path}")
            continue

        ifc_file = ifcopenshell.open(ifc_path)
        all_guids = {e.GlobalId for e in ifc_file.by_type("IfcRoot")
                     if getattr(e, "GlobalId", None)}

        # Subject under test: the REAL extractor function into a fresh in-memory db
        conn = sqlite3.connect(":memory:")
        conn.executescript(REFERENCE_SCHEMA)
        rows, filled = extract_rel_fills_host(ifc_file, conn)

        # Independent oracle (second read, never touches the table)
        ora_hosts, ora_filled, ora_classes = oracle_voidfill(ifc_file)

        db_rows = conn.execute("SELECT * FROM rel_fills_host").fetchall()
        cols = [c[1] for c in conn.execute("PRAGMA table_info(rel_fills_host)")]
        idx = {c: i for i, c in enumerate(cols)}
        db_filled = sum(1 for r in db_rows if r[idx["filling_guid"]])
        db_filled_classes = {}
        for r in db_rows:
            fc = r[idx["filling_class"]]
            if r[idx["filling_guid"]]:
                db_filled_classes[fc] = db_filled_classes.get(fc, 0) + 1

        print(f"      §LOG rows={rows} filled={filled} | oracle hosts={ora_hosts} "
              f"filled={ora_filled} classes={ora_classes}")

        # 1) table count == function return == oracle host count
        check(f"{label}:rows==oracle-hosts", rows == ora_hosts == len(db_rows),
              f"rows={rows} return-vs-table, oracle_hosts={ora_hosts}")

        # 2) filled host edges match the oracle (independent)
        check(f"{label}:filled==oracle", db_filled == ora_filled,
              f"db_filled={db_filled} oracle_filled={ora_filled}")

        # 3) expected filled count for this known building
        check(f"{label}:filled==expected", db_filled == exp_filled,
              f"db_filled={db_filled} expected={exp_filled}")

        # 4) filling class breakdown matches oracle (and known truth for SH)
        check(f"{label}:classes==oracle", db_filled_classes == ora_classes,
              f"db={db_filled_classes} oracle={ora_classes}")
        if exp_classes is not None:
            check(f"{label}:classes==known", db_filled_classes == exp_classes,
                  f"db={db_filled_classes} known={exp_classes}")

        # 5) every row provenance == ifc:recovered (NON-INVENT stamp)
        bad_prov = [r for r in db_rows if r[idx["provenance"]] != "ifc:recovered"]
        check(f"{label}:provenance=recovered", not bad_prov,
              f"{len(bad_prov)} rows with wrong provenance")

        # 6) ZERO invented GUIDs — every opening/host/filling GUID exists in the source IFC
        invented = []
        for r in db_rows:
            for key in ("opening_guid", "host_guid", "filling_guid"):
                g = r[idx[key]]
                if g and g not in all_guids:
                    invented.append((key, g))
        check(f"{label}:zero-invented-guids", not invented,
              f"{len(invented)} GUIDs not in source IFC")

        conn.close()

    print(f"\n  §PATHB-RESULT  PASS={passed}  FAIL={failed}")
    if failed:
        print("  ✗ W-SDG-PATHB RED")
        sys.exit(1)
    total = passed
    print(f"  ✓ W-SDG-PATHB GREEN {total}/{total}")


if __name__ == "__main__":
    main()
