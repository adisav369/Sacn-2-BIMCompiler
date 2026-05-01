# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
nD Fleet Test — run all dimensions on every extracted DB + sandbox city.

Tests every building individually, then the 1M sandbox consolidated.
Writes results to scripts/nD_fleet_report.txt

Usage:
  python3 scripts/test_nD_fleet.py
  python3 scripts/test_nD_fleet.py --include-sandbox   # also run 1M sandbox (slow)
"""
import argparse
import os
import shutil
import sqlite3
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from nD_engine import TemplateLoader, run_engine, table_exists, get_element_count

DB_DIR = Path(__file__).parent.parent / 'DAGCompiler' / 'lib' / 'input'
REPORT_FILE = Path(__file__).parent / 'nD_fleet_report.txt'


def test_one_db(db_path: str, report_lines: list) -> dict:
    """Run all 5 dims on a temp copy, return summary dict."""
    name = Path(db_path).stem.replace('_extracted', '')

    # Check has elements_meta
    conn = sqlite3.connect(db_path)
    count = get_element_count(conn)
    conn.close()
    if count == 0:
        report_lines.append(f"  SKIP  {name:<30s}  no elements_meta")
        return {'name': name, 'status': 'SKIP', 'elements': 0}

    # Work on temp copy
    tmp_dir = tempfile.mkdtemp(prefix='nD_fleet_')
    tmp_db = os.path.join(tmp_dir, Path(db_path).name)
    shutil.copy2(db_path, tmp_db)

    t0 = time.time()
    try:
        results = run_engine(tmp_db, dims=['5D', '4D', '6D', '7D', '8D'])
    except Exception as e:
        elapsed = time.time() - t0
        report_lines.append(f"  ERROR {name:<30s}  {count:>10,d} elements  {elapsed:.1f}s  {e}")
        shutil.rmtree(tmp_dir)
        return {'name': name, 'status': 'ERROR', 'elements': count, 'error': str(e)}
    elapsed = time.time() - t0

    # Verify key witnesses
    conn = sqlite3.connect(tmp_db)
    issues = []

    # 5D
    if table_exists(conn, 'simple_qto'):
        uncosted = conn.execute(
            "SELECT COUNT(DISTINCT ifc_class) FROM simple_qto WHERE unit_cost_rm IS NULL"
        ).fetchone()[0]
        grand = conn.execute("SELECT SUM(total_cost_rm) FROM simple_qto").fetchone()[0] or 0
        if uncosted > 0:
            issues.append(f"5D:{uncosted} uncosted")
    else:
        issues.append("5D:no table")
        grand = 0

    # 4D
    if table_exists(conn, 'construction_schedule'):
        unknown = conn.execute(
            "SELECT COUNT(*) FROM construction_schedule WHERE phase='Unknown'"
        ).fetchone()[0]
        tasks = conn.execute("SELECT COUNT(*) FROM construction_schedule").fetchone()[0]
        if unknown > 0:
            issues.append(f"4D:{unknown} Unknown")
    else:
        issues.append("4D:no table")
        tasks = 0

    # 6D
    if table_exists(conn, 'carbon_audit'):
        carbon = conn.execute("SELECT SUM(embodied_carbon_kg) FROM carbon_audit").fetchone()[0] or 0
    else:
        issues.append("6D:no table")
        carbon = 0

    # 7D
    if table_exists(conn, 'asset_register'):
        assets = conn.execute("SELECT COUNT(*) FROM asset_register").fetchone()[0]
    else:
        issues.append("7D:no table")
        assets = 0

    # 8D
    if table_exists(conn, 'hazard_register'):
        hazards = conn.execute("SELECT COUNT(*) FROM hazard_register").fetchone()[0]
    else:
        issues.append("8D:no table")
        hazards = 0

    conn.close()
    shutil.rmtree(tmp_dir)

    status = "FAIL" if issues else "PASS"
    issue_str = "; ".join(issues) if issues else ""

    line = (f"  {status:<5s} {name:<30s}  {count:>10,d} el  "
            f"RM {grand:>14,.2f}  {carbon:>10,.0f} kgCO2  "
            f"{assets:>7,d} assets  {tasks:>5d} tasks  {hazards:>5d} haz  "
            f"{elapsed:>5.1f}s")
    if issue_str:
        line += f"  [{issue_str}]"
    report_lines.append(line)

    return {
        'name': name, 'status': status, 'elements': count,
        'grand_total': grand, 'carbon_kg': carbon,
        'assets': assets, 'tasks': tasks, 'hazards': hazards,
        'elapsed': elapsed, 'issues': issues
    }


def main():
    parser = argparse.ArgumentParser(description='nD Fleet Test')
    parser.add_argument('--include-sandbox', action='store_true',
                        help='Also run 1M sandbox (takes longer)')
    args = parser.parse_args()

    # Suppress nD_engine console output during fleet run
    import logging
    logging.getLogger('nD_engine').handlers = []
    fh = logging.FileHandler(Path(__file__).parent / 'nD_engine_log.txt', mode='w')
    fh.setLevel(logging.DEBUG)
    fh.setFormatter(logging.Formatter('%(asctime)s [%(levelname)-5s] %(message)s', datefmt='%H:%M:%S'))
    logging.getLogger('nD_engine').addHandler(fh)

    dbs = sorted(DB_DIR.glob('*_extracted.db'))

    # Separate sandbox from individual buildings
    sandbox_db = None
    building_dbs = []
    for db in dbs:
        if 'sandbox' in db.name.lower():
            sandbox_db = db
        else:
            building_dbs.append(db)

    report = []
    report.append("=" * 130)
    report.append("nD FLEET TEST — All Buildings + Sandbox City")
    report.append("=" * 130)
    report.append("")

    # --- Individual buildings ---
    report.append(f"--- Individual Buildings ({len(building_dbs)} DBs) ---")
    report.append(f"  {'STAT':<5s} {'Building':<30s}  {'Elements':>10s}  "
                  f"{'5D Grand Total':>14s}  {'6D Carbon':>10s}  "
                  f"{'7D Assets':>7s}  {'4D Tasks':>5s}  {'8D Haz':>5s}  {'Time':>5s}")
    report.append("  " + "-" * 120)

    all_results = []
    t_total = time.time()

    for db in building_dbs:
        result = test_one_db(str(db), report)
        all_results.append(result)

    # --- Sandbox ---
    if args.include_sandbox and sandbox_db:
        report.append("")
        report.append(f"--- Sandbox City (1M) ---")
        result = test_one_db(str(sandbox_db), report)
        all_results.append(result)

    elapsed_total = time.time() - t_total

    # --- Summary ---
    tested = [r for r in all_results if r['status'] != 'SKIP']
    passed = [r for r in tested if r['status'] == 'PASS']
    failed = [r for r in tested if r['status'] == 'FAIL']
    errors = [r for r in tested if r['status'] == 'ERROR']
    skipped = [r for r in all_results if r['status'] == 'SKIP']
    total_elements = sum(r.get('elements', 0) for r in tested)
    total_cost = sum(r.get('grand_total', 0) for r in tested)
    total_carbon = sum(r.get('carbon_kg', 0) for r in tested)
    total_assets = sum(r.get('assets', 0) for r in tested)

    report.append("")
    report.append("=" * 130)
    report.append("SUMMARY")
    report.append("=" * 130)
    report.append(f"  Buildings tested : {len(tested)}")
    report.append(f"  PASS             : {len(passed)}")
    report.append(f"  FAIL             : {len(failed)}")
    report.append(f"  ERROR            : {len(errors)}")
    report.append(f"  SKIP (no data)   : {len(skipped)}")
    report.append(f"  Total elements   : {total_elements:,d}")
    report.append(f"  Total 5D cost    : RM {total_cost:,.2f}")
    report.append(f"  Total 6D carbon  : {total_carbon:,.0f} kgCO2e ({total_carbon/1000:,.1f} tCO2e)")
    report.append(f"  Total 7D assets  : {total_assets:,d}")
    report.append(f"  Total time       : {elapsed_total:.1f}s")

    if failed:
        report.append("")
        report.append("FAILURES:")
        for r in failed:
            report.append(f"  {r['name']}: {r.get('issues', [])}")

    if errors:
        report.append("")
        report.append("ERRORS:")
        for r in errors:
            report.append(f"  {r['name']}: {r.get('error', '?')}")

    # Write report
    report_text = "\n".join(report)
    REPORT_FILE.write_text(report_text + "\n")

    # Also print
    print(report_text)
    print(f"\nReport: {REPORT_FILE}")

    sys.exit(1 if (failed or errors) else 0)


if __name__ == '__main__':
    main()
