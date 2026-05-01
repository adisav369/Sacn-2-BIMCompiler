# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
nD Engine test — proves all 5 dimensions produce correct tables from templates.

Issue: 4D/5D hardcoded IFC class rules caused 64% Unknown phase (8,703 days).
       Template-driven engine with _default fallback must produce zero Unknown.

Witnesses:
  W-5D_COSTED:    Every IFC class gets a cost (template or _default)
  W-4D_NO_UNKNOWN: Zero tasks with phase='Unknown'
  W-6D_CARBON:    carbon_audit total > 0 and matches SUM(qty * factor)
  W-7D_ASSETS:    asset_register row count == elements_meta row count
  W-8D_SAFETY:    Every hazard row has a non-empty risk_level
  W-AUTO_PREREQ:  Requesting 6D on fresh DB auto-generates simple_qto
  W-TEMPLATE:     Engine loads all 6 template files without error

Usage:
  python3 scripts/test_nD_engine.py
  python3 scripts/test_nD_engine.py --db DAGCompiler/lib/input/Clinic_extracted.db
  python3 scripts/test_nD_engine.py --db DAGCompiler/lib/input/Hospital_extracted.db
"""
import argparse
import os
import shutil
import sqlite3
import sys
import tempfile
from pathlib import Path

# Add scripts dir to path so we can import nD_engine
sys.path.insert(0, str(Path(__file__).parent))
from nD_engine import TemplateLoader, run_engine, table_exists, get_element_count

PASS = 0
FAIL = 0


def witness(name, condition, detail=""):
    global PASS, FAIL
    tag = "PASS" if condition else "FAIL"
    if condition:
        PASS += 1
    else:
        FAIL += 1
    msg = f"  [{tag}] {name}"
    if detail:
        msg += f" — {detail}"
    print(msg)
    return condition


def test_templates_load():
    """W-TEMPLATE: All 6 template files load without error."""
    print("\n--- W-TEMPLATE: Template Loading ---")
    loader = TemplateLoader()
    files = ['nD_formulas.json', '4D_phases.json', '5D_rates.json',
             '6D_carbon.json', '7D_lifecycle.json', '8D_safety.json']
    for f in files:
        try:
            data = loader.load(f)
            witness(f"TEMPLATE_{f}", len(data) > 0, f"keys={len(data)}")
        except Exception as e:
            witness(f"TEMPLATE_{f}", False, str(e))

    # Check _default exists in key templates
    master = loader.load('nD_formulas.json')
    phases = loader.load('4D_phases.json')
    rates = loader.load('5D_rates.json')
    carbon = loader.load('6D_carbon.json')
    lifecycle = loader.load('7D_lifecycle.json')

    witness("DEFAULT_in_4D", '_default' in phases['ifc_class_rules'],
            "4D_phases.json has _default rule")
    witness("DEFAULT_in_5D", '_default' in rates['material_rates'],
            "5D_rates.json has _default rate")
    witness("DEFAULT_in_6D", '_default' in carbon['carbon_factors'],
            "6D_carbon.json has _default factor")
    witness("DEFAULT_in_7D", '_default' in lifecycle['lifespan_rules'],
            "7D_lifecycle.json has _default lifespan")


def test_full_run(db_path):
    """Run all 5 dimensions on a copy of the DB and verify tables."""
    print(f"\n--- Full nD Run: {Path(db_path).name} ---")

    # Work on a temp copy so we don't pollute the original
    tmp_dir = tempfile.mkdtemp(prefix='nD_test_')
    tmp_db = os.path.join(tmp_dir, Path(db_path).name)
    shutil.copy2(db_path, tmp_db)

    conn = sqlite3.connect(tmp_db)
    total_elements = get_element_count(conn)
    if total_elements == 0:
        witness("DB_HAS_ELEMENTS", False, "elements_meta is empty — skipping")
        conn.close()
        shutil.rmtree(tmp_dir)
        return

    # Count distinct IFC classes
    ifc_classes = conn.execute(
        "SELECT DISTINCT ifc_class FROM elements_meta WHERE ifc_class IS NOT NULL"
    ).fetchall()
    distinct_classes = len(ifc_classes)
    print(f"  DB: {total_elements} elements, {distinct_classes} IFC classes")
    conn.close()

    # Run all dimensions
    results = run_engine(tmp_db, dims=['5D', '4D', '6D', '7D', '8D'])

    conn = sqlite3.connect(tmp_db)

    # --- W-5D_COSTED ---
    print("\n--- W-5D_COSTED: Every IFC class gets a cost ---")
    if table_exists(conn, 'simple_qto'):
        qto_rows = conn.execute("SELECT COUNT(*) FROM simple_qto").fetchone()[0]
        uncosted = conn.execute(
            "SELECT COUNT(DISTINCT ifc_class) FROM simple_qto WHERE unit_cost_rm IS NULL"
        ).fetchone()[0]
        costed = conn.execute(
            "SELECT COUNT(DISTINCT ifc_class) FROM simple_qto WHERE unit_cost_rm IS NOT NULL"
        ).fetchone()[0]
        grand_total = conn.execute(
            "SELECT SUM(total_cost_rm) FROM simple_qto WHERE total_cost_rm IS NOT NULL"
        ).fetchone()[0] or 0

        witness("5D_TABLE_EXISTS", True, f"simple_qto rows={qto_rows}")
        witness("5D_ZERO_UNCOSTED", uncosted == 0,
                f"costed={costed} uncosted={uncosted}")
        witness("5D_GRAND_TOTAL", grand_total > 0,
                f"RM {grand_total:,.2f}")
    else:
        witness("5D_TABLE_EXISTS", False, "simple_qto not created")

    # --- W-4D_NO_UNKNOWN ---
    print("\n--- W-4D_NO_UNKNOWN: Zero tasks with phase='Unknown' ---")
    if table_exists(conn, 'construction_schedule'):
        total_tasks = conn.execute("SELECT COUNT(*) FROM construction_schedule").fetchone()[0]
        unknown_tasks = conn.execute(
            "SELECT COUNT(*) FROM construction_schedule WHERE phase = 'Unknown'"
        ).fetchone()[0]
        phases = conn.execute(
            "SELECT phase, COUNT(*) FROM construction_schedule GROUP BY phase ORDER BY COUNT(*) DESC"
        ).fetchall()

        witness("4D_TABLE_EXISTS", True, f"tasks={total_tasks}")
        witness("4D_ZERO_UNKNOWN", unknown_tasks == 0,
                f"unknown={unknown_tasks} total={total_tasks}")
        for phase, count in phases:
            print(f"    {phase:<20s} {count} tasks")
    else:
        witness("4D_TABLE_EXISTS", False, "construction_schedule not created")

    # --- W-6D_CARBON ---
    print("\n--- W-6D_CARBON: Carbon audit integrity ---")
    if table_exists(conn, 'carbon_audit'):
        carbon_rows = conn.execute("SELECT COUNT(*) FROM carbon_audit").fetchone()[0]
        total_carbon = conn.execute("SELECT SUM(embodied_carbon_kg) FROM carbon_audit").fetchone()[0] or 0
        # Cross-check: SUM(total_quantity * carbon_factor) should match
        cross = conn.execute(
            "SELECT SUM(total_quantity * carbon_factor) FROM carbon_audit"
        ).fetchone()[0] or 0

        witness("6D_TABLE_EXISTS", True, f"rows={carbon_rows}")
        witness("6D_CARBON_POSITIVE", total_carbon > 0,
                f"{total_carbon:,.0f} kgCO2e ({total_carbon/1000:,.1f} tCO2e)")
        witness("6D_CROSS_CHECK", abs(total_carbon - cross) < 1.0,
                f"stored={total_carbon:,.0f} computed={cross:,.0f}")
    else:
        witness("6D_TABLE_EXISTS", False, "carbon_audit not created")

    # --- W-7D_ASSETS ---
    print("\n--- W-7D_ASSETS: Asset register completeness ---")
    if table_exists(conn, 'asset_register'):
        asset_rows = conn.execute("SELECT COUNT(*) FROM asset_register").fetchone()[0]
        # Should match elements_meta count (one asset per element)
        elem_with_class = conn.execute(
            "SELECT COUNT(*) FROM elements_meta WHERE ifc_class IS NOT NULL"
        ).fetchone()[0]
        null_warranty = conn.execute(
            "SELECT COUNT(*) FROM asset_register WHERE warranty_months IS NULL"
        ).fetchone()[0]
        null_tags = conn.execute(
            "SELECT COUNT(*) FROM asset_register WHERE asset_tag IS NULL OR asset_tag = ''"
        ).fetchone()[0]

        witness("7D_TABLE_EXISTS", True, f"assets={asset_rows}")
        witness("7D_COUNT_MATCH", asset_rows == elem_with_class,
                f"assets={asset_rows} elements={elem_with_class}")
        witness("7D_ALL_WARRANTED", null_warranty == 0,
                f"null_warranty={null_warranty}")
        witness("7D_ALL_TAGGED", null_tags == 0,
                f"null_tags={null_tags}")
    else:
        witness("7D_TABLE_EXISTS", False, "asset_register not created")

    # --- W-8D_SAFETY ---
    print("\n--- W-8D_SAFETY: Hazard register completeness ---")
    if table_exists(conn, 'hazard_register'):
        safety_rows = conn.execute("SELECT COUNT(*) FROM hazard_register").fetchone()[0]
        null_risk = conn.execute(
            "SELECT COUNT(*) FROM hazard_register WHERE risk_level IS NULL OR risk_level = ''"
        ).fetchone()[0]
        high_risk = conn.execute(
            "SELECT COUNT(*) FROM hazard_register WHERE risk_level IN ('HIGH', 'VERY HIGH')"
        ).fetchone()[0]
        permits = conn.execute(
            "SELECT COUNT(*) FROM hazard_register WHERE permit_required = 1"
        ).fetchone()[0]

        witness("8D_TABLE_EXISTS", True, f"rows={safety_rows}")
        witness("8D_ALL_HAVE_RISK", null_risk == 0,
                f"null_risk={null_risk}")
        witness("8D_HIGH_RISK_EXISTS", high_risk > 0,
                f"HIGH/VERY HIGH={high_risk} permits={permits}")
    else:
        witness("8D_TABLE_EXISTS", False, "hazard_register not created")

    conn.close()
    shutil.rmtree(tmp_dir)


def test_auto_prereq(db_path):
    """W-AUTO_PREREQ: Requesting 6D on a fresh DB auto-generates simple_qto."""
    print(f"\n--- W-AUTO_PREREQ: Auto-prerequisite generation ---")

    tmp_dir = tempfile.mkdtemp(prefix='nD_prereq_')
    tmp_db = os.path.join(tmp_dir, Path(db_path).name)
    shutil.copy2(db_path, tmp_db)

    # Drop simple_qto if it exists
    conn = sqlite3.connect(tmp_db)
    conn.execute("DROP TABLE IF EXISTS simple_qto")
    conn.execute("DROP TABLE IF EXISTS carbon_audit")
    conn.commit()

    has_qto_before = table_exists(conn, 'simple_qto')
    conn.close()

    witness("PREREQ_QTO_ABSENT", not has_qto_before, "simple_qto dropped")

    # Request only 6D — should auto-create simple_qto
    run_engine(tmp_db, dims=['6D'])

    conn = sqlite3.connect(tmp_db)
    has_qto_after = table_exists(conn, 'simple_qto')
    has_carbon = table_exists(conn, 'carbon_audit')
    conn.close()

    witness("PREREQ_QTO_AUTO_CREATED", has_qto_after,
            "simple_qto auto-generated as 6D prerequisite")
    witness("PREREQ_6D_COMPLETED", has_carbon,
            "carbon_audit created after auto-prereq")

    shutil.rmtree(tmp_dir)


def main():
    parser = argparse.ArgumentParser(description='nD Engine test')
    parser.add_argument('--db', type=str,
                        default='DAGCompiler/lib/input/Clinic_extracted.db',
                        help='Extracted DB to test against')
    args = parser.parse_args()

    db_path = args.db
    if not os.path.isabs(db_path):
        db_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), db_path)

    if not Path(db_path).exists():
        print(f"ERROR: Database not found: {db_path}")
        sys.exit(1)

    print("=" * 70)
    print("nD ENGINE TEST")
    print("=" * 70)

    test_templates_load()
    test_full_run(db_path)
    test_auto_prereq(db_path)

    print("\n" + "=" * 70)
    print(f"RESULTS: {PASS} PASS, {FAIL} FAIL")
    print("=" * 70)

    sys.exit(1 if FAIL > 0 else 0)


if __name__ == '__main__':
    main()
