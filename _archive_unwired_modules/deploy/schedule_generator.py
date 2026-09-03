"""
4D Construction Schedule Generator
-----------------------------------
Generates construction schedules from federation database.

Template-driven: reads phase rules from 4D_phases.json and labor/equipment
rates from 5D_rates.json. Falls back to hardcoded defaults if templates
are not found (for backward compatibility).
"""

import sqlite3
import json
import os
from datetime import datetime, timedelta
from pathlib import Path
from typing import List, Dict, Tuple, Optional


# ---------------------------------------------------------------------------
# Template loading (shared pattern with simple_qto_extract.py)
# ---------------------------------------------------------------------------
def _find_templates_dir():
    """Locate the templates/ directory."""
    env_dir = os.environ.get('BIM_TEMPLATES_DIR')
    if env_dir and Path(env_dir).exists():
        return Path(env_dir)

    here = Path(__file__).resolve()
    for parent in here.parents:
        candidate = parent / 'templates' / 'nD_formulas.json'
        if candidate.exists():
            return parent / 'templates'

    home_candidate = Path.home() / 'bim-compiler' / 'templates'
    if (home_candidate / 'nD_formulas.json').exists():
        return home_candidate

    return None


def _load_templates():
    """Load 4D phase rules and 5D labor/equipment rates from JSON templates.
    Returns (phase_rules, calendar, labor_rates, equip_alloc) or Nones."""
    tdir = _find_templates_dir()
    if not tdir:
        return None, None, None, None

    try:
        with open(tdir / '4D_phases.json', 'r') as f:
            phases = json.load(f)
        with open(tdir / '5D_rates.json', 'r') as f:
            rates = json.load(f)
        return (
            phases.get('ifc_class_rules'),
            phases.get('calendar'),
            rates.get('labor_rates'),
            rates.get('equipment_allocation'),
        )
    except Exception:
        return None, None, None, None


# ---------------------------------------------------------------------------
# Hardcoded fallbacks — used ONLY if templates not found
# ---------------------------------------------------------------------------

# Import labor productivity data from BOQ module (fallback)
import sys
_boq_path = Path(__file__).parent.parent / "boq"
sys.path.insert(0, str(_boq_path))

try:
    from comprehensive_boq_export import LABOR_RATES as _FB_LABOR_RATES, EQUIPMENT_ALLOCATION as _FB_EQUIPMENT_ALLOCATION
except ImportError:
    try:
        from bonsai.bim.module.federation.boq.comprehensive_boq_export import LABOR_RATES as _FB_LABOR_RATES, EQUIPMENT_ALLOCATION as _FB_EQUIPMENT_ALLOCATION
    except ImportError:
        _FB_LABOR_RATES = {}
        _FB_EQUIPMENT_ALLOCATION = {}

_FB_SEQUENCE_RULES = {
    'IfcFooting': {'phase': 'Substructure', 'sequence': 1, 'predecessors': [], 'resource': 'CONCRETE_GANG'},
    'IfcColumn': {'phase': 'Superstructure', 'sequence': 2, 'predecessors': ['IfcFooting'], 'resource': 'STEEL_ERECTOR'},
    'IfcBeam': {'phase': 'Superstructure', 'sequence': 3, 'predecessors': ['IfcColumn'], 'resource': 'STEEL_ERECTOR'},
    'IfcSlab': {'phase': 'Superstructure', 'sequence': 4, 'predecessors': ['IfcBeam'], 'resource': 'CONCRETE_GANG'},
    'IfcDuct': {'phase': 'MEP Rough-in', 'sequence': 5, 'predecessors': ['IfcSlab'], 'resource': 'HVAC_TECH'},
    'IfcDuctSegment': {'phase': 'MEP Rough-in', 'sequence': 5, 'predecessors': ['IfcSlab'], 'resource': 'HVAC_TECH'},
    'IfcDuctFitting': {'phase': 'MEP Rough-in', 'sequence': 5, 'predecessors': ['IfcSlab'], 'resource': 'HVAC_TECH'},
    'IfcPipe': {'phase': 'MEP Rough-in', 'sequence': 5, 'predecessors': ['IfcSlab'], 'resource': 'PLUMBER'},
    'IfcPipeSegment': {'phase': 'MEP Rough-in', 'sequence': 5, 'predecessors': ['IfcSlab'], 'resource': 'PLUMBER'},
    'IfcPipeFitting': {'phase': 'MEP Rough-in', 'sequence': 5, 'predecessors': ['IfcSlab'], 'resource': 'PLUMBER'},
    'IfcCableCarrier': {'phase': 'MEP Rough-in', 'sequence': 5, 'predecessors': ['IfcSlab'], 'resource': 'ELECTRICIAN'},
    'IfcCableCarrierSegment': {'phase': 'MEP Rough-in', 'sequence': 5, 'predecessors': ['IfcSlab'], 'resource': 'ELECTRICIAN'},
    'IfcWall': {'phase': 'Architecture', 'sequence': 6, 'predecessors': ['IfcDuct', 'IfcPipe'], 'resource': 'MASON'},
    'IfcWallStandardCase': {'phase': 'Architecture', 'sequence': 6, 'predecessors': ['IfcDuct', 'IfcPipe'], 'resource': 'MASON'},
    'IfcDoor': {'phase': 'Architecture', 'sequence': 7, 'predecessors': ['IfcWall'], 'resource': 'CARPENTER'},
    'IfcWindow': {'phase': 'Architecture', 'sequence': 7, 'predecessors': ['IfcWall'], 'resource': 'CARPENTER'},
    'IfcStairFlight': {'phase': 'Architecture', 'sequence': 7, 'predecessors': ['IfcSlab'], 'resource': 'CARPENTER'},
    'IfcRoof': {'phase': 'Architecture', 'sequence': 8, 'predecessors': ['IfcWall'], 'resource': 'ROOFER'},
    'IfcLightFixture': {'phase': 'MEP Final', 'sequence': 9, 'predecessors': ['IfcWall'], 'resource': 'ELECTRICIAN'},
    'IfcOutlet': {'phase': 'MEP Final', 'sequence': 9, 'predecessors': ['IfcWall'], 'resource': 'ELECTRICIAN'},
    'IfcElectricAppliance': {'phase': 'MEP Final', 'sequence': 9, 'predecessors': ['IfcWall'], 'resource': 'ELECTRICIAN'},
    'IfcFlowTerminal': {'phase': 'MEP Final', 'sequence': 9, 'predecessors': ['IfcDuct'], 'resource': 'HVAC_TECH'},
    'IfcCovering': {'phase': 'Finishes', 'sequence': 10, 'predecessors': ['IfcLightFixture'], 'resource': 'FINISHER'},
    'IfcFurniture': {'phase': 'Finishes', 'sequence': 11, 'predecessors': ['IfcCovering'], 'resource': 'FINISHER'},
}

_FB_PRODUCTIVITY = {
    'IfcColumn': 6.0, 'IfcBeam': 8.0, 'IfcSlab': 35.0, 'IfcWall': 12.0,
    'IfcDoor': 5.0, 'IfcWindow': 5.0, 'IfcDuct': 18.0, 'IfcPipe': 25.0,
    'IfcCableCarrier': 30.0, 'IfcLightFixture': 20.0, 'IfcOutlet': 25.0,
    'IfcFurniture': 8.0, 'IfcStairFlight': 2.0, 'IfcRoof': 25.0,
}

_FB_CALENDAR = {
    'working_days': [0, 1, 2, 3, 4, 5],
    'non_working_days': [6],
    'hours_per_day': 8,
}


# ---------------------------------------------------------------------------
# Schedule generation
# ---------------------------------------------------------------------------
def generate_construction_schedule(
    db_path: str,
    project_start_date: str = '2025-01-06',
    project_name: str = None
) -> int:
    """
    Generate construction schedule from federation database.

    Template-driven: if templates/4D_phases.json is found, uses it for
    phase rules, productivity, and calendar. All IFC classes get mapped
    (with _default fallback), eliminating the Unknown phase problem.
    Falls back to hardcoded rules if templates not found.
    """
    if not Path(db_path).exists():
        raise FileNotFoundError(f"Database not found: {db_path}")

    if project_name is None:
        project_name = Path(db_path).stem.replace('_extracted', '').replace('_', ' ') + ' Construction'

    # Load templates or use fallbacks
    tmpl_rules, tmpl_calendar, tmpl_labor, tmpl_equip = _load_templates()
    using_templates = tmpl_rules is not None

    if using_templates:
        sequence_rules = tmpl_rules
        default_rule = tmpl_rules.get('_default', {
            'phase': 'Architecture', 'sequence': 6, 'predecessors': [],
            'resource': 'LABORER', 'productivity': 10.0
        })
        calendar = tmpl_calendar or _FB_CALENDAR
        labor_rates = tmpl_labor or {}
        equip_alloc = tmpl_equip or {}
        print("§SCHEDULE_TEMPLATES loaded from JSON — all IFC classes mapped")
    else:
        sequence_rules = _FB_SEQUENCE_RULES
        default_rule = {'phase': 'Unknown', 'sequence': 99, 'predecessors': [], 'resource': None}
        calendar = _FB_CALENDAR
        labor_rates = {k: {'crew_size': v.get('crew_size', 2)} for k, v in _FB_LABOR_RATES.items()}
        equip_alloc = _FB_EQUIPMENT_ALLOCATION
        print("§SCHEDULE_FALLBACK no templates found — using hardcoded rules")

    working_days_set = set(calendar.get('working_days', [0, 1, 2, 3, 4, 5]))

    # Ensure schema exists
    try:
        from .database_schema import initialize_schedule_schema
    except ImportError:
        from database_schema import initialize_schedule_schema
    initialize_schedule_schema(db_path)

    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    cursor.execute("DELETE FROM construction_schedule")

    start_date = datetime.fromisoformat(project_start_date)

    # Get activities
    cursor.execute("""
        SELECT COALESCE(storey, 'Unknown') as storey, discipline, ifc_class,
               COUNT(*) as element_count
        FROM elements_meta WHERE ifc_class IS NOT NULL
        GROUP BY storey, discipline, ifc_class ORDER BY storey, ifc_class
    """)
    activities = cursor.fetchall()

    print(f"\n{'='*80}")
    print(f"4D CONSTRUCTION SCHEDULE GENERATION")
    print(f"{'='*80}")
    print(f"Project: {project_name}")
    print(f"Start Date: {project_start_date}")
    print(f"Database: {Path(db_path).name}")
    print(f"Activities found: {len(activities)}")
    print(f"{'='*80}\n")

    tasks_created = 0
    for storey, discipline, ifc_class, element_count in activities:
        # Get rules from template or fallback
        rules = sequence_rules.get(ifc_class, default_rule)
        phase = rules.get('phase', 'Unknown')
        sequence = rules.get('sequence', 99)
        resource = rules.get('resource')

        # Productivity: template rule has it directly, fallback checks multiple sources
        if using_templates:
            productivity = rules.get('productivity', 10.0)
        else:
            productivity = _get_fallback_productivity(ifc_class)

        quantity = element_count
        duration_days = max(1.0, quantity / productivity)

        # Crew size
        crew_size = 2
        if resource and resource in labor_rates:
            crew_size = labor_rates[resource].get('crew_size', 2)

        # Equipment
        equipment = None
        if ifc_class in equip_alloc:
            equipment = equip_alloc[ifc_class].get('equipment')

        # Date calculation
        task_start = start_date + timedelta(days=sequence * 5)
        current = task_start
        remaining = duration_days
        while remaining > 0:
            if current.weekday() in working_days_set:
                remaining -= 1
            current += timedelta(days=1)
        task_finish = current

        task_name = f"{storey} - Install {ifc_class.replace('Ifc', '')} ({discipline})"
        wbs_code = f"1.{sequence}.{tasks_created}"

        cursor.execute("""
            INSERT INTO construction_schedule (
                wbs_code, task_name, ifc_class, discipline, storey,
                phase, sequence, quantity, uom, productivity_rate, duration_days,
                start_date, finish_date, predecessors,
                labor_resource, labor_crew_size, equipment_resource,
                status, percent_complete
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """, (wbs_code, task_name, ifc_class, discipline, storey,
              phase, sequence, quantity, 'EA', productivity, duration_days,
              task_start.isoformat(), task_finish.isoformat(), json.dumps([]),
              resource, crew_size, equipment, 'Not Started', 0.0))

        tasks_created += 1

        print(f"  [{wbs_code}] {task_name}")
        print(f"    Quantity: {element_count} EA | Duration: {duration_days:.1f} days | {task_start.date()} -> {task_finish.date()}")
        print(f"    Resource: {resource} (crew: {crew_size}) | Equipment: {equipment or 'None'}")
        print()

    conn.commit()
    conn.close()

    print(f"\n{'='*80}")
    print(f"Schedule generated successfully!")
    print(f"  Total tasks created: {tasks_created}")
    print(f"  Database: {db_path}")
    print(f"{'='*80}\n")

    return tasks_created


def _get_fallback_productivity(ifc_class: str) -> float:
    """Get productivity from BOQ labor rates or hardcoded defaults (fallback path)."""
    for resource_data in _FB_LABOR_RATES.values():
        productivity = resource_data.get('productivity', {})
        if ifc_class in productivity:
            return productivity[ifc_class]
    return _FB_PRODUCTIVITY.get(ifc_class, 10.0)


if __name__ == "__main__":
    import sys

    if len(sys.argv) < 2:
        print("Usage: python schedule_generator.py <database_path> [start_date] [project_name]")
        sys.exit(1)

    db_path = sys.argv[1]
    start_date = sys.argv[2] if len(sys.argv) > 2 else '2025-01-06'
    default_name = Path(db_path).stem.replace('_extracted', '').replace('_', ' ') + ' Construction'
    project_name = sys.argv[3] if len(sys.argv) > 3 else default_name

    generate_construction_schedule(db_path, start_date, project_name)
