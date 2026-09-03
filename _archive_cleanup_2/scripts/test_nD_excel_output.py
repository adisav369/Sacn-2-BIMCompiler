# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
nD Excel Output Test — runs Federation pipeline on any DB, saves Excel to output folders.

Produces:
  boq_reports/BOQ_{building}_{timestamp}.xlsx    — 5D Comprehensive BOQ
  schedules/{building}_Schedule_{timestamp}.xlsx  — 4D Schedule + Dashboard

Usage:
  python3 scripts/test_nD_excel_output.py
  python3 scripts/test_nD_excel_output.py --db DAGCompiler/lib/input/sandbox_1M_extracted.db
  python3 scripts/test_nD_excel_output.py --db DAGCompiler/lib/input/Hospital_extracted.db
"""
import argparse
import os
import shutil
import sqlite3
import sys
import time
from datetime import datetime
from pathlib import Path

# Federation module paths — dataintelligence FIRST (has template-driven versions)
# boq/ has older copies of same filenames; dataintelligence must win on import
FED_BASE = Path('/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation')
sys.path.insert(0, str(FED_BASE / 'schedule'))
sys.path.insert(0, str(FED_BASE / 'boq'))
sys.path.insert(0, str(FED_BASE / 'dataintelligence'))  # must be last = highest priority

PROJECT_ROOT = Path(__file__).parent.parent
BOQ_DIR = PROJECT_ROOT / 'boq_reports'
SCHEDULE_DIR = PROJECT_ROOT / 'schedules'


def run_boq_excel(db_path: str, building_name: str) -> str:
    """Run Federation 5D pipeline: QTO extraction → Comprehensive BOQ Excel."""
    from simple_qto_extract import extract_simple_qto
    from comprehensive_boq_export import ComprehensiveBOQExporter

    # Step 1: QTO extraction (template-driven)
    print(f"\n{'='*70}")
    print(f"5D BOQ — {building_name}")
    print(f"{'='*70}")

    t0 = time.time()
    extract_simple_qto(db_path)
    t_qto = time.time() - t0

    # Step 2: Excel export
    BOQ_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_path = BOQ_DIR / f"BOQ_{building_name}_{timestamp}.xlsx"

    exporter = ComprehensiveBOQExporter(str(output_path))
    t0 = time.time()
    result_path = exporter.generate_comprehensive_boq(db_path, f"{building_name} Project")
    t_excel = time.time() - t0

    size_kb = os.path.getsize(result_path) / 1024
    print(f"\n§BOQ_EXCEL written: {result_path}")
    print(f"§BOQ_TIMING qto={t_qto:.1f}s excel={t_excel:.1f}s total={t_qto+t_excel:.1f}s")
    print(f"§BOQ_SIZE {size_kb:.0f} KB")

    return result_path


def run_schedule_excel(db_path: str, building_name: str) -> str:
    """Run Federation 4D pipeline: Schedule generation → Excel export."""
    from schedule_generator import generate_construction_schedule
    from excel_export import export_schedule_to_excel

    print(f"\n{'='*70}")
    print(f"4D SCHEDULE — {building_name}")
    print(f"{'='*70}")

    # Step 1: Generate schedule (template-driven)
    t0 = time.time()
    tasks = generate_construction_schedule(db_path, project_name=f"{building_name} Construction")
    t_gen = time.time() - t0

    # Step 2: Excel export
    SCHEDULE_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_path = SCHEDULE_DIR / f"{building_name}_Schedule_{timestamp}.xlsx"

    t0 = time.time()
    result_path = export_schedule_to_excel(db_path, str(output_path), f"{building_name} Construction")
    t_excel = time.time() - t0

    size_kb = os.path.getsize(result_path) / 1024
    print(f"\n§SCHEDULE_EXCEL written: {result_path}")
    print(f"§SCHEDULE_TIMING gen={t_gen:.1f}s excel={t_excel:.1f}s total={t_gen+t_excel:.1f}s")
    print(f"§SCHEDULE_SIZE {size_kb:.0f} KB")

    return result_path


def main():
    parser = argparse.ArgumentParser(description='nD Excel Output Test')
    parser.add_argument('--db', type=str,
                        default='DAGCompiler/lib/input/Clinic_extracted.db',
                        help='Extracted DB to generate reports from')
    args = parser.parse_args()

    db_path = args.db
    if not os.path.isabs(db_path):
        db_path = str(PROJECT_ROOT / db_path)

    if not Path(db_path).exists():
        print(f"ERROR: Database not found: {db_path}")
        sys.exit(1)

    building_name = Path(db_path).stem.replace('_extracted', '')

    # Check element count
    conn = sqlite3.connect(db_path)
    count = conn.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]
    classes = conn.execute("SELECT COUNT(DISTINCT ifc_class) FROM elements_meta WHERE ifc_class IS NOT NULL").fetchone()[0]
    conn.close()

    print(f"{'='*70}")
    print(f"nD EXCEL OUTPUT TEST")
    print(f"{'='*70}")
    print(f"Database : {Path(db_path).name}")
    print(f"Building : {building_name}")
    print(f"Elements : {count:,}")
    print(f"IFC classes: {classes}")

    # Work on a temp copy so we don't pollute the source DB
    import tempfile
    tmp_dir = tempfile.mkdtemp(prefix='nD_excel_')
    tmp_db = os.path.join(tmp_dir, Path(db_path).name)
    shutil.copy2(db_path, tmp_db)

    t_total = time.time()

    # Run BOQ
    boq_path = run_boq_excel(tmp_db, building_name)

    # Run Schedule
    schedule_path = run_schedule_excel(tmp_db, building_name)

    elapsed = time.time() - t_total

    # Cleanup temp
    shutil.rmtree(tmp_dir)

    # Summary
    print(f"\n{'='*70}")
    print(f"DONE — {building_name} ({count:,} elements)")
    print(f"{'='*70}")
    print(f"  BOQ Excel     : {boq_path}")
    print(f"  Schedule Excel : {schedule_path}")
    print(f"  Total time     : {elapsed:.1f}s")
    print(f"{'='*70}")


if __name__ == '__main__':
    main()
