"""
nD BIM Engine — Template-Driven 4D/5D/6D/7D/8D Analytics
---------------------------------------------------------
Abstract engine: reads JSON templates, queries extracted DB, produces tables + Excel.
Zero hardcoded IFC classes. Users edit templates, engine applies formulas.

Usage:
    python nD_engine.py <database_path> [--dims 4D,5D,6D] [--templates /path/to/templates]
    python nD_engine.py <database_path> --all
    python nD_engine.py <database_path> --dims 5D --start-date 2025-06-01
"""

import json
import logging
import sqlite3
import sys
import os
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List, Optional, Tuple

# ---------------------------------------------------------------------------
# Logging — debug log to file, summary to console
# ---------------------------------------------------------------------------
LOG_DIR = Path(__file__).parent
LOG_FILE = LOG_DIR / 'nD_engine_log.txt'

logger = logging.getLogger('nD_engine')
logger.setLevel(logging.DEBUG)

# File handler — full debug
fh = logging.FileHandler(LOG_FILE, mode='w', encoding='utf-8')
fh.setLevel(logging.DEBUG)
fh.setFormatter(logging.Formatter('%(asctime)s [%(levelname)-5s] %(message)s', datefmt='%H:%M:%S'))
logger.addHandler(fh)

# Console handler — INFO and above
ch = logging.StreamHandler(sys.stdout)
ch.setLevel(logging.INFO)
ch.setFormatter(logging.Formatter('[%(levelname)-5s] %(message)s'))
logger.addHandler(ch)


# ---------------------------------------------------------------------------
# Template Loader
# ---------------------------------------------------------------------------
class TemplateLoader:
    """Loads and validates JSON templates from the templates directory."""

    def __init__(self, templates_dir: str = None):
        if templates_dir:
            self.templates_dir = Path(templates_dir)
        else:
            self.templates_dir = Path(__file__).parent.parent / 'templates'

        if not self.templates_dir.exists():
            raise FileNotFoundError(f"Templates directory not found: {self.templates_dir}")

        self._cache: Dict[str, dict] = {}
        logger.info("§TEMPLATES dir=%s", self.templates_dir)

    def load(self, filename: str) -> dict:
        """Load a JSON template, with caching."""
        if filename in self._cache:
            return self._cache[filename]

        path = self.templates_dir / filename
        if not path.exists():
            raise FileNotFoundError(f"Template not found: {path}")

        with open(path, 'r', encoding='utf-8') as f:
            data = json.load(f)

        self._cache[filename] = data
        logger.info("§TEMPLATE_LOADED file=%s keys=%s", filename, list(data.keys()))
        return data

    def load_master(self) -> dict:
        """Load nD_formulas.json (the master template)."""
        return self.load('nD_formulas.json')


# ---------------------------------------------------------------------------
# DB Helpers
# ---------------------------------------------------------------------------
def table_exists(conn: sqlite3.Connection, table_name: str) -> bool:
    row = conn.execute(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (table_name,)
    ).fetchone()
    return row is not None


def get_element_count(conn: sqlite3.Connection) -> int:
    if not table_exists(conn, 'elements_meta'):
        return 0
    return conn.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]


def get_ifc_classes(conn: sqlite3.Connection) -> List[Tuple[str, int]]:
    """Return [(ifc_class, count)] sorted by count descending."""
    return conn.execute("""
        SELECT ifc_class, COUNT(*) n FROM elements_meta
        WHERE ifc_class IS NOT NULL
        GROUP BY ifc_class ORDER BY n DESC
    """).fetchall()


def resolve_measurement(ifc_class: str, rules: dict) -> str:
    """Resolve measurement type for an IFC class from nD_formulas measurement_rules."""
    for mtype in ('linear', 'area', 'volume'):
        if ifc_class in rules.get(mtype, []):
            return mtype
    return rules.get('_default', 'count')


# ---------------------------------------------------------------------------
# Ensure prerequisite tables exist (auto-run if missing)
# ---------------------------------------------------------------------------
def ensure_tables(conn: sqlite3.Connection, db_path: str, required: List[str],
                  loader: 'TemplateLoader', start_date: str):
    """Auto-generate prerequisite tables if missing."""
    for table in required:
        if table_exists(conn, table):
            logger.debug("§TABLE_EXISTS table=%s", table)
            continue

        logger.info("§TABLE_MISSING table=%s — auto-generating", table)
        if table == 'simple_qto':
            run_5d_qto(db_path, loader)
            conn.execute("SELECT 1")  # refresh connection state
        elif table == 'construction_schedule':
            run_4d_schedule(db_path, loader, start_date)
            conn.execute("SELECT 1")
        else:
            logger.warning("§TABLE_CANNOT_AUTO_CREATE table=%s — skipping", table)


# ---------------------------------------------------------------------------
# 5D — QTO + Cost (template-driven)
# ---------------------------------------------------------------------------
def run_5d_qto(db_path: str, loader: TemplateLoader):
    """Extract quantities and apply costs from 5D_rates.json template."""
    logger.info("=" * 70)
    logger.info("§5D_START — QTO + Cost Extraction")
    logger.info("=" * 70)

    master = loader.load_master()
    rates = loader.load('5D_rates.json')
    meas_rules = master['measurement_rules']

    conn = sqlite3.connect(db_path)
    total_elements = get_element_count(conn)
    logger.info("§5D_DB elements=%d db=%s", total_elements, Path(db_path).name)

    # Drop and recreate
    conn.execute("DROP TABLE IF EXISTS simple_qto")
    conn.execute("""
    CREATE TABLE simple_qto (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        discipline TEXT,
        ifc_class TEXT,
        storey TEXT,
        measurement_type TEXT,
        element_count INTEGER,
        total_quantity REAL,
        uom TEXT,
        avg_quantity REAL,
        unit_cost_rm REAL,
        total_cost_rm REAL
    )""")
    logger.debug("§5D_TABLE_CREATED simple_qto")

    # Build SQL per measurement type
    has_rtree = table_exists(conn, 'elements_rtree')
    formulas = master['measurement_rules']['_formulas']

    ifc_classes = get_ifc_classes(conn)
    logger.info("§5D_IFC_CLASSES total_distinct=%d", len(ifc_classes))

    # Group IFC classes by measurement type
    groups = {'linear': [], 'area': [], 'volume': [], 'count': []}
    for ifc_class, count in ifc_classes:
        mtype = resolve_measurement(ifc_class, meas_rules)
        groups[mtype].append(ifc_class)
        logger.debug("§5D_MEASURE ifc=%s type=%s count=%d", ifc_class, mtype, count)

    # Insert per group
    for mtype, classes in groups.items():
        if not classes:
            continue

        placeholders = ','.join('?' * len(classes))

        if mtype == 'count' or not has_rtree:
            sql = f"""
            INSERT INTO simple_qto (discipline, ifc_class, storey, measurement_type,
                                    element_count, total_quantity, uom, avg_quantity)
            SELECT discipline, ifc_class, storey, 'COUNT',
                   COUNT(*), COUNT(*), 'EA', 1.0
            FROM elements_meta
            WHERE ifc_class IN ({placeholders})
            GROUP BY discipline, ifc_class, storey
            """
            uom = 'EA'
        elif mtype == 'linear':
            sql = f"""
            INSERT INTO simple_qto (discipline, ifc_class, storey, measurement_type,
                                    element_count, total_quantity, uom, avg_quantity)
            SELECT e.discipline, e.ifc_class, e.storey, 'LINEAR',
                   COUNT(*),
                   ROUND(SUM(r.maxZ - r.minZ), 2),
                   'M',
                   ROUND(AVG(r.maxZ - r.minZ), 2)
            FROM elements_meta e JOIN elements_rtree r ON e.id = r.id
            WHERE e.ifc_class IN ({placeholders})
            GROUP BY e.discipline, e.ifc_class, e.storey
            """
            uom = 'M'
        elif mtype == 'area':
            sql = f"""
            INSERT INTO simple_qto (discipline, ifc_class, storey, measurement_type,
                                    element_count, total_quantity, uom, avg_quantity)
            SELECT e.discipline, e.ifc_class, e.storey, 'AREA',
                   COUNT(*),
                   ROUND(SUM((r.maxX - r.minX) * (r.maxY - r.minY)), 2),
                   'M2',
                   ROUND(AVG((r.maxX - r.minX) * (r.maxY - r.minY)), 2)
            FROM elements_meta e JOIN elements_rtree r ON e.id = r.id
            WHERE e.ifc_class IN ({placeholders})
            GROUP BY e.discipline, e.ifc_class, e.storey
            """
            uom = 'M2'
        elif mtype == 'volume':
            sql = f"""
            INSERT INTO simple_qto (discipline, ifc_class, storey, measurement_type,
                                    element_count, total_quantity, uom, avg_quantity)
            SELECT e.discipline, e.ifc_class, e.storey, 'VOLUME',
                   COUNT(*),
                   ROUND(SUM((r.maxX-r.minX)*(r.maxY-r.minY)*(r.maxZ-r.minZ)), 2),
                   'M3',
                   ROUND(AVG((r.maxX-r.minX)*(r.maxY-r.minY)*(r.maxZ-r.minZ)), 2)
            FROM elements_meta e JOIN elements_rtree r ON e.id = r.id
            WHERE e.ifc_class IN ({placeholders})
            GROUP BY e.discipline, e.ifc_class, e.storey
            """
            uom = 'M3'

        conn.execute(sql, classes)
        logger.info("§5D_INSERT mtype=%s classes=%d uom=%s", mtype, len(classes), uom)

    conn.commit()

    # Apply costs from template
    material_rates = rates['material_rates']
    default_rate = material_rates.get('_default', {})
    costed = 0
    uncosted_classes = set()

    for row in conn.execute("SELECT DISTINCT ifc_class FROM simple_qto").fetchall():
        ifc_class = row[0]
        rate_entry = material_rates.get(ifc_class, default_rate)
        unit_rate = rate_entry.get('rate', 0)

        if unit_rate > 0:
            conn.execute("""
                UPDATE simple_qto SET unit_cost_rm = ?, total_cost_rm = ROUND(total_quantity * ?, 2)
                WHERE ifc_class = ?
            """, (unit_rate, unit_rate, ifc_class))
            costed += 1
            logger.debug("§5D_RATE ifc=%s rate=%.2f src=%s",
                         ifc_class, unit_rate,
                         'template' if ifc_class in material_rates else '_default')
        else:
            uncosted_classes.add(ifc_class)

    conn.commit()

    # Summary
    grand = conn.execute("SELECT SUM(total_cost_rm) FROM simple_qto WHERE total_cost_rm IS NOT NULL").fetchone()[0] or 0
    rows = conn.execute("SELECT COUNT(*) FROM simple_qto").fetchone()[0]
    conn.close()

    logger.info("§5D_DONE rows=%d costed=%d uncosted=%d grand_total=RM %.2f",
                rows, costed, len(uncosted_classes), grand)
    if uncosted_classes:
        logger.warning("§5D_UNCOSTED classes=%s", sorted(uncosted_classes))

    return rows


# ---------------------------------------------------------------------------
# 4D — Schedule (template-driven)
# ---------------------------------------------------------------------------
def run_4d_schedule(db_path: str, loader: TemplateLoader, start_date_str: str = '2025-01-06'):
    """Generate construction schedule from 4D_phases.json template."""
    logger.info("=" * 70)
    logger.info("§4D_START — Construction Schedule Generation")
    logger.info("=" * 70)

    phases_tmpl = loader.load('4D_phases.json')
    calendar = phases_tmpl['calendar']
    rules = phases_tmpl['ifc_class_rules']
    default_rule = rules.get('_default', {
        'phase': 'Unknown', 'sequence': 99, 'predecessors': [], 'resource': 'LABORER', 'productivity': 10.0
    })

    rates_tmpl = loader.load('5D_rates.json')
    labor_rates = rates_tmpl.get('labor_rates', {})
    equip_alloc = rates_tmpl.get('equipment_allocation', {})

    conn = sqlite3.connect(db_path)
    total_elements = get_element_count(conn)
    logger.info("§4D_DB elements=%d db=%s", total_elements, Path(db_path).name)

    # Ensure schedule table
    conn.executescript("""
    CREATE TABLE IF NOT EXISTS construction_schedule (
        task_id INTEGER PRIMARY KEY AUTOINCREMENT,
        wbs_code TEXT NOT NULL,
        task_name TEXT NOT NULL,
        element_guid TEXT,
        ifc_class TEXT,
        discipline TEXT,
        storey TEXT,
        phase TEXT,
        sequence INTEGER,
        quantity REAL,
        uom TEXT,
        productivity_rate REAL,
        duration_days REAL,
        start_date TEXT,
        finish_date TEXT,
        predecessors TEXT,
        labor_resource TEXT,
        labor_crew_size INTEGER,
        equipment_resource TEXT,
        status TEXT DEFAULT 'Not Started',
        percent_complete REAL DEFAULT 0.0,
        early_start TEXT, early_finish TEXT,
        late_start TEXT, late_finish TEXT,
        total_float_days REAL,
        is_critical BOOLEAN DEFAULT 0
    );
    CREATE INDEX IF NOT EXISTS idx_schedule_phase ON construction_schedule(phase);
    CREATE INDEX IF NOT EXISTS idx_schedule_discipline ON construction_schedule(discipline);
    CREATE INDEX IF NOT EXISTS idx_schedule_storey ON construction_schedule(storey);
    """)

    conn.execute("DELETE FROM construction_schedule")

    start_date = datetime.fromisoformat(start_date_str)
    working_days = set(calendar.get('working_days', [0, 1, 2, 3, 4, 5]))

    # Get activities grouped by (storey, discipline, ifc_class)
    activities = conn.execute("""
        SELECT COALESCE(storey, 'Unknown') as storey, discipline, ifc_class, COUNT(*) as cnt
        FROM elements_meta WHERE ifc_class IS NOT NULL
        GROUP BY storey, discipline, ifc_class ORDER BY storey, ifc_class
    """).fetchall()

    logger.info("§4D_ACTIVITIES total=%d", len(activities))

    tasks_created = 0
    phase_counts = {}
    unknown_count = 0

    for storey, discipline, ifc_class, element_count in activities:
        rule = rules.get(ifc_class, default_rule)
        phase = rule['phase']
        sequence = rule['sequence']
        resource = rule.get('resource', 'LABORER')
        productivity = rule.get('productivity', 10.0)

        if ifc_class not in rules:
            unknown_count += 1
            logger.debug("§4D_DEFAULT ifc=%s -> phase=%s (using _default)", ifc_class, phase)

        quantity = element_count
        duration_days = max(1.0, quantity / productivity)

        # Crew size from labor rates
        crew_size = labor_rates.get(resource, {}).get('crew_size', 2)

        # Equipment
        equipment = None
        if ifc_class in equip_alloc:
            equipment = equip_alloc[ifc_class].get('equipment')

        # Date calculation
        task_start = start_date + timedelta(days=sequence * 5)
        current = task_start
        remaining = duration_days
        while remaining > 0:
            if current.weekday() in working_days:
                remaining -= 1
            current += timedelta(days=1)
        task_finish = current

        task_name = f"{storey} - Install {ifc_class.replace('Ifc', '')} ({discipline})"
        wbs_code = f"1.{sequence}.{tasks_created}"

        conn.execute("""
            INSERT INTO construction_schedule (
                wbs_code, task_name, ifc_class, discipline, storey,
                phase, sequence, quantity, uom, productivity_rate, duration_days,
                start_date, finish_date, predecessors,
                labor_resource, labor_crew_size, equipment_resource,
                status, percent_complete
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """, (wbs_code, task_name, ifc_class, discipline, storey,
              phase, sequence, quantity, 'EA', productivity, duration_days,
              task_start.isoformat(), task_finish.isoformat(), '[]',
              resource, crew_size, equipment, 'Not Started', 0.0))

        tasks_created += 1
        phase_counts[phase] = phase_counts.get(phase, 0) + 1

        logger.debug("§4D_TASK [%s] %s qty=%d dur=%.1f days=%s->%s",
                      wbs_code, task_name, element_count, duration_days,
                      task_start.date(), task_finish.date())

    conn.commit()
    conn.close()

    logger.info("§4D_DONE tasks=%d unknown_fallback=%d", tasks_created, unknown_count)
    for phase, count in sorted(phase_counts.items(), key=lambda x: x[1], reverse=True):
        logger.info("§4D_PHASE %-20s tasks=%d", phase, count)

    return tasks_created


# ---------------------------------------------------------------------------
# 6D — Carbon Audit (template-driven)
# ---------------------------------------------------------------------------
def run_6d_carbon(db_path: str, loader: TemplateLoader):
    """Generate carbon audit from 6D_carbon.json template."""
    logger.info("=" * 70)
    logger.info("§6D_START — Sustainability / Carbon Audit")
    logger.info("=" * 70)

    carbon_tmpl = loader.load('6D_carbon.json')
    master = loader.load_master()
    factors = carbon_tmpl['carbon_factors']
    default_factor = factors.get('_default', {'factor': 50.0, 'per': 'EA'})
    meas_rules = master['measurement_rules']

    conn = sqlite3.connect(db_path)
    has_rtree = table_exists(conn, 'elements_rtree')

    conn.execute("DROP TABLE IF EXISTS carbon_audit")
    conn.execute("""
    CREATE TABLE carbon_audit (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        discipline TEXT,
        ifc_class TEXT,
        storey TEXT,
        element_count INTEGER,
        total_quantity REAL,
        uom TEXT,
        carbon_factor REAL,
        embodied_carbon_kg REAL,
        source_note TEXT
    )""")
    logger.debug("§6D_TABLE_CREATED carbon_audit")

    # If simple_qto exists, use it directly; otherwise compute from elements_meta
    if table_exists(conn, 'simple_qto'):
        logger.info("§6D_SOURCE using simple_qto table")
        rows = conn.execute("""
            SELECT discipline, ifc_class, storey, element_count, total_quantity, uom
            FROM simple_qto
        """).fetchall()
    else:
        logger.info("§6D_SOURCE using elements_meta (no simple_qto)")
        rows = conn.execute("""
            SELECT discipline, ifc_class, COALESCE(storey,'Unknown'), COUNT(*), COUNT(*), 'EA'
            FROM elements_meta WHERE ifc_class IS NOT NULL
            GROUP BY discipline, ifc_class, storey
        """).fetchall()

    total_carbon = 0.0
    for discipline, ifc_class, storey, elem_count, quantity, uom in rows:
        factor_entry = factors.get(ifc_class, default_factor)
        factor = factor_entry['factor']
        note = factor_entry.get('note', '')

        embodied = quantity * factor
        total_carbon += embodied

        conn.execute("""
            INSERT INTO carbon_audit (discipline, ifc_class, storey, element_count,
                                      total_quantity, uom, carbon_factor, embodied_carbon_kg, source_note)
            VALUES (?,?,?,?,?,?,?,?,?)
        """, (discipline, ifc_class, storey, elem_count, quantity, uom,
              factor, round(embodied, 2), note))

        logger.debug("§6D_ROW ifc=%s qty=%.1f factor=%.1f carbon=%.1f kg",
                      ifc_class, quantity, factor, embodied)

    conn.commit()
    row_count = conn.execute("SELECT COUNT(*) FROM carbon_audit").fetchone()[0]
    conn.close()

    logger.info("§6D_DONE rows=%d total_embodied_carbon=%.0f kgCO2e (%.1f tCO2e)",
                row_count, total_carbon, total_carbon / 1000)
    return row_count


# ---------------------------------------------------------------------------
# 7D — Asset Register / Lifecycle (template-driven)
# ---------------------------------------------------------------------------
def run_7d_lifecycle(db_path: str, loader: TemplateLoader):
    """Generate asset register from 7D_lifecycle.json template."""
    logger.info("=" * 70)
    logger.info("§7D_START — Facility Management / Asset Register")
    logger.info("=" * 70)

    lifecycle_tmpl = loader.load('7D_lifecycle.json')
    rules = lifecycle_tmpl['lifespan_rules']
    default_rule = rules.get('_default', {
        'lifespan_years': 30, 'warranty_months': 12, 'service_interval_months': 6
    })

    conn = sqlite3.connect(db_path)

    conn.execute("DROP TABLE IF EXISTS asset_register")
    conn.execute("""
    CREATE TABLE asset_register (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        guid TEXT,
        ifc_class TEXT,
        element_name TEXT,
        discipline TEXT,
        storey TEXT,
        material_name TEXT,
        asset_tag TEXT,
        lifespan_years INTEGER,
        warranty_months INTEGER,
        service_interval_months INTEGER,
        replacement_year INTEGER,
        lifecycle_note TEXT
    )""")
    logger.debug("§7D_TABLE_CREATED asset_register")

    # Per-element register (not grouped)
    elements = conn.execute("""
        SELECT guid, ifc_class, element_name, discipline, storey, material_name
        FROM elements_meta WHERE ifc_class IS NOT NULL
        ORDER BY discipline, storey, ifc_class
    """).fetchall()

    logger.info("§7D_ELEMENTS total=%d", len(elements))

    seq = {}  # per (discipline, storey) sequence counter for asset tags
    batch = []

    for guid, ifc_class, elem_name, discipline, storey, material in elements:
        rule = rules.get(ifc_class, default_rule)
        lifespan = rule['lifespan_years']
        warranty = rule['warranty_months']
        service = rule['service_interval_months']
        note = rule.get('note', '')
        replacement_yr = int(lifespan * 0.8)

        # Asset tag
        key = (discipline or 'UNK', storey or 'UNK')
        seq[key] = seq.get(key, 0) + 1
        asset_tag = f"{key[0]}_{key[1]}_{seq[key]:04d}"

        batch.append((guid, ifc_class, elem_name, discipline, storey, material,
                       asset_tag, lifespan, warranty, service, replacement_yr, note))

    conn.executemany("""
        INSERT INTO asset_register (guid, ifc_class, element_name, discipline, storey,
                                    material_name, asset_tag, lifespan_years, warranty_months,
                                    service_interval_months, replacement_year, lifecycle_note)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
    """, batch)

    conn.commit()
    row_count = conn.execute("SELECT COUNT(*) FROM asset_register").fetchone()[0]
    conn.close()

    logger.info("§7D_DONE assets=%d", row_count)
    return row_count


# ---------------------------------------------------------------------------
# 8D — Safety / Hazard Register (template-driven)
# ---------------------------------------------------------------------------
def run_8d_safety(db_path: str, loader: TemplateLoader):
    """Generate hazard register from 8D_safety.json template."""
    logger.info("=" * 70)
    logger.info("§8D_START — Safety / Hazard Register")
    logger.info("=" * 70)

    safety_tmpl = loader.load('8D_safety.json')
    phase_hazards = safety_tmpl['phase_hazards']
    ifc_hazards = safety_tmpl['ifc_class_hazards']
    risk_matrix = safety_tmpl['risk_matrix']
    default_hazard = ifc_hazards.get('_default', {
        'hazard_class': 'GENERAL', 'additional_ppe': [], 'permit_required': False
    })

    conn = sqlite3.connect(db_path)

    conn.execute("DROP TABLE IF EXISTS hazard_register")
    conn.execute("""
    CREATE TABLE hazard_register (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        ifc_class TEXT,
        discipline TEXT,
        storey TEXT,
        element_count INTEGER,
        phase TEXT,
        hazard_class TEXT,
        risk_level TEXT,
        hazards TEXT,
        ppe_required TEXT,
        additional_ppe TEXT,
        permit_required INTEGER
    )""")
    logger.debug("§8D_TABLE_CREATED hazard_register")

    # Need phase info — get from construction_schedule if available, else from 4D template
    phases_tmpl = loader.load('4D_phases.json')
    phase_rules = phases_tmpl['ifc_class_rules']
    default_phase_rule = phase_rules.get('_default', {'phase': 'Architecture'})

    activities = conn.execute("""
        SELECT ifc_class, discipline, COALESCE(storey,'Unknown'), COUNT(*)
        FROM elements_meta WHERE ifc_class IS NOT NULL
        GROUP BY ifc_class, discipline, storey
    """).fetchall()

    logger.info("§8D_ACTIVITIES total=%d", len(activities))

    for ifc_class, discipline, storey, count in activities:
        # Phase
        phase_rule = phase_rules.get(ifc_class, default_phase_rule)
        phase = phase_rule['phase']

        # Hazard from ifc_class
        hz = ifc_hazards.get(ifc_class, default_hazard)
        hazard_class = hz['hazard_class']
        additional_ppe = hz.get('additional_ppe', [])
        permit = hz.get('permit_required', False)

        # Phase-level hazards
        ph = phase_hazards.get(phase, {'risk_level': 'LOW', 'hazards': [], 'ppe': []})
        risk_level = ph['risk_level']
        hazards = ph.get('hazards', [])
        base_ppe = ph.get('ppe', [])

        # Override risk from risk matrix if class-specific
        rm = risk_matrix.get(hazard_class, {})
        if rm:
            risk_level = rm.get('risk', risk_level)

        # Merge PPE
        all_ppe = list(dict.fromkeys(base_ppe + additional_ppe))  # deduplicate, preserve order

        conn.execute("""
            INSERT INTO hazard_register (ifc_class, discipline, storey, element_count,
                                         phase, hazard_class, risk_level,
                                         hazards, ppe_required, additional_ppe, permit_required)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
        """, (ifc_class, discipline, storey, count,
              phase, hazard_class, risk_level,
              json.dumps(hazards), json.dumps(all_ppe), json.dumps(additional_ppe),
              1 if permit else 0))

        logger.debug("§8D_ROW ifc=%s phase=%s hazard=%s risk=%s permit=%s",
                      ifc_class, phase, hazard_class, risk_level, permit)

    conn.commit()
    row_count = conn.execute("SELECT COUNT(*) FROM hazard_register").fetchone()[0]
    conn.close()

    logger.info("§8D_DONE rows=%d", row_count)
    return row_count


# ---------------------------------------------------------------------------
# Orchestrator — run selected dimensions
# ---------------------------------------------------------------------------
DIMENSION_RUNNERS = {
    '4D': lambda db, loader, sd: run_4d_schedule(db, loader, sd),
    '5D': lambda db, loader, sd: run_5d_qto(db, loader),
    '6D': lambda db, loader, sd: run_6d_carbon(db, loader),
    '7D': lambda db, loader, sd: run_7d_lifecycle(db, loader),
    '8D': lambda db, loader, sd: run_8d_safety(db, loader),
}


def run_engine(db_path: str, dims: List[str] = None, templates_dir: str = None,
               start_date: str = '2025-01-06'):
    """
    Main entry point. Runs selected dimensions against an extracted DB.

    Args:
        db_path: Path to _extracted.db
        dims: List of dimensions to run, e.g. ['4D','5D','6D']. None = all enabled.
        templates_dir: Override templates directory
        start_date: Project start date for 4D scheduling
    """
    logger.info("=" * 70)
    logger.info("§ENGINE_START nD BIM Engine v1.0")
    logger.info("§ENGINE_DB %s", db_path)
    logger.info("§ENGINE_DATE %s", datetime.now().isoformat())
    logger.info("=" * 70)

    if not Path(db_path).exists():
        logger.error("§ENGINE_ERROR Database not found: %s", db_path)
        return

    loader = TemplateLoader(templates_dir)
    master = loader.load_master()

    # Resolve which dimensions to run
    all_dims = master['dimensions']
    if dims is None:
        dims = [k for k, v in all_dims.items() if v.get('enabled', True)]

    logger.info("§ENGINE_DIMS running=%s", dims)

    # Check DB has elements_meta
    conn = sqlite3.connect(db_path)
    elem_count = get_element_count(conn)
    if elem_count == 0:
        logger.error("§ENGINE_ERROR elements_meta is empty or missing")
        conn.close()
        return

    ifc_classes = get_ifc_classes(conn)
    logger.info("§ENGINE_ELEMENTS total=%d distinct_ifc_classes=%d", elem_count, len(ifc_classes))
    for cls, cnt in ifc_classes[:10]:
        logger.debug("§ENGINE_CLASS %-30s %d", cls, cnt)
    if len(ifc_classes) > 10:
        logger.debug("§ENGINE_CLASS ... and %d more classes", len(ifc_classes) - 10)

    conn.close()

    # Run dimensions in dependency order
    # 5D must run before 6D (needs simple_qto). 4D must run before 8D (needs schedule).
    order = ['5D', '4D', '6D', '7D', '8D']
    sorted_dims = [d for d in order if d in dims]

    results = {}
    for dim in sorted_dims:
        dim_info = all_dims.get(dim, {})
        logger.info("")
        logger.info(">>> Running %s — %s", dim, dim_info.get('name', dim))

        # Ensure prerequisites
        conn = sqlite3.connect(db_path)
        required = dim_info.get('requires_tables', [])
        ensure_tables(conn, db_path, required, loader, start_date)
        conn.close()

        # Run
        runner = DIMENSION_RUNNERS.get(dim)
        if runner:
            try:
                result = runner(db_path, loader, start_date)
                results[dim] = result
                logger.info("§ENGINE_RESULT %s = %s", dim, result)
            except Exception as e:
                logger.error("§ENGINE_ERROR %s failed: %s", dim, e, exc_info=True)
                results[dim] = f"ERROR: {e}"
        else:
            logger.warning("§ENGINE_SKIP %s — no runner implemented", dim)

    # Final summary
    logger.info("")
    logger.info("=" * 70)
    logger.info("§ENGINE_DONE — Summary")
    logger.info("=" * 70)
    for dim, result in results.items():
        logger.info("  %s: %s", dim, result)
    logger.info("§ENGINE_LOG %s", LOG_FILE)

    return results


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------
def main():
    import argparse

    parser = argparse.ArgumentParser(
        description='nD BIM Engine — Template-Driven 4D/5D/6D/7D/8D Analytics',
        epilog='Example: python nD_engine.py myproject_extracted.db --all'
    )
    parser.add_argument('database', help='Path to _extracted.db')
    parser.add_argument('--dims', type=str, default=None,
                        help='Comma-separated dimensions to run: 4D,5D,6D,7D,8D')
    parser.add_argument('--all', action='store_true', help='Run all enabled dimensions')
    parser.add_argument('--templates', type=str, default=None,
                        help='Path to templates directory (default: ../templates/)')
    parser.add_argument('--start-date', type=str, default='2025-01-06',
                        help='Project start date for 4D (default: 2025-01-06)')

    args = parser.parse_args()

    dims = None
    if args.dims:
        dims = [d.strip().upper() for d in args.dims.split(',')]
    elif args.all:
        dims = None  # run_engine defaults to all enabled

    run_engine(args.database, dims=dims, templates_dir=args.templates,
               start_date=args.start_date)


if __name__ == '__main__':
    main()
