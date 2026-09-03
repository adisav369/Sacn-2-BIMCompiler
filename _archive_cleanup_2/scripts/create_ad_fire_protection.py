# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
Create AD Fire Protection Schema for BIM Compiler

Phase 57: Fire Protection triggers and riser requirements.
Tables:
  - ad_fp_trigger: When fire protection is required
  - ad_fire_riser_requirement: Riser sizing by building parameters

Sources:
  - UBBL 2012 (Malaysia Uniform Building By-Laws)
  - NFPA 13 (Sprinkler System Design)
  - NFPA 14 (Standpipe Systems)
  - MS 1910 (Malaysian Fire Protection)

Author: BIM Compiler Project
Date: 2026-02-04
"""

import sqlite3
import os
import sys

DB_PATH = "library/BOM.db"


def create_fp_schema(conn: sqlite3.Connection) -> None:
    """Create Fire Protection AD tables."""
    cursor = conn.cursor()

    # Fire Protection Trigger - when is FP required?
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_fp_trigger (
        trigger_id        TEXT PRIMARY KEY,
        trigger_type      TEXT NOT NULL,       -- STOREY_COUNT, BUILDING_HEIGHT, FLOOR_AREA, OCCUPANCY
        element_type      TEXT NOT NULL,       -- SPRINKLER, STANDPIPE, FIRE_ALARM, SMOKE_DETECTION
        min_storeys       INTEGER,
        min_height_m      REAL,
        min_floor_area_m2 REAL,
        total_area_m2     REAL,
        occupancy_group   TEXT,                -- R, A, B, E, F, H, I, M, S (IBC groups)
        min_quantity      INTEGER DEFAULT 1,
        quantity_formula  TEXT,
        code_id           TEXT,
        clause            TEXT,
        description       TEXT,
        jurisdiction      TEXT DEFAULT 'INTERNATIONAL',
        is_mandatory      INTEGER DEFAULT 1,
        is_active         INTEGER DEFAULT 1
    )
    """)

    # Fire Riser Requirement - sizing based on building parameters
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_fire_riser_requirement (
        req_id            TEXT PRIMARY KEY,
        riser_type        TEXT NOT NULL,       -- WET_RISER, DRY_RISER, COMBINED, STANDPIPE
        hazard_class      TEXT DEFAULT 'LIGHT',-- LIGHT, ORDINARY_1, ORDINARY_2, EXTRA
        min_storeys       INTEGER,
        max_storeys       INTEGER,
        min_height_m      REAL,
        max_height_m      REAL,
        max_floor_area_m2 REAL,                -- Per floor area served
        pipe_diameter_mm  INTEGER NOT NULL,
        branch_diameter_mm INTEGER,
        min_flow_lpm      INTEGER,             -- Minimum flow rate
        min_pressure_bar  REAL,                -- Minimum residual pressure
        pump_required     INTEGER DEFAULT 0,
        tank_capacity_l   INTEGER,
        valve_type        TEXT,                -- GATE, CHECK, ALARM
        code_id           TEXT,
        clause            TEXT,
        description       TEXT,
        jurisdiction      TEXT DEFAULT 'INTERNATIONAL',
        is_active         INTEGER DEFAULT 1
    )
    """)

    # Fire Compartment Rules - compartment sizing limits
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_fire_compartment (
        compartment_id    TEXT PRIMARY KEY,
        occupancy_group   TEXT NOT NULL,
        space_type        TEXT,                -- NULL = any space in group
        max_area_m2       REAL NOT NULL,
        max_area_sprink_m2 REAL,               -- Max area if sprinklered (bonus)
        min_fire_rating_hr REAL NOT NULL,
        requires_sprinkler INTEGER DEFAULT 0,
        requires_detection INTEGER DEFAULT 0,
        requires_smoke_ctrl INTEGER DEFAULT 0,
        code_id           TEXT,
        clause            TEXT,
        jurisdiction      TEXT DEFAULT 'INTERNATIONAL',
        is_active         INTEGER DEFAULT 1
    )
    """)

    conn.commit()
    print("[AD FP] Created: ad_fp_trigger, ad_fire_riser_requirement, ad_fire_compartment")


def populate_triggers(conn: sqlite3.Connection) -> None:
    """Populate FP trigger data - when is fire protection required."""
    cursor = conn.cursor()

    # EXTRACTED from UBBL 2012, NFPA 13, IBC
    triggers = [
        # SPRINKLER triggers - Malaysia (UBBL)
        ('SPRINK_MY_HIGHRISE', 'BUILDING_HEIGHT', 'SPRINKLER', None, 18.0, None, None, None,
         1, None, 'UBBL', 'By-Law 225', 'Buildings >18m require sprinklers', 'MALAYSIA', 1),
        ('SPRINK_MY_AREA', 'FLOOR_AREA', 'SPRINKLER', None, None, 1000.0, None, None,
         1, None, 'UBBL', 'By-Law 225', 'Floor area >1000m² requires sprinklers', 'MALAYSIA', 1),
        ('SPRINK_MY_BASEMENT', 'STOREY_COUNT', 'SPRINKLER', -1, None, None, None, None,
         1, None, 'UBBL', 'By-Law 225', 'Basement levels require sprinklers', 'MALAYSIA', 1),

        # SPRINKLER triggers - International (IBC/NFPA)
        ('SPRINK_INT_HIGHRISE', 'BUILDING_HEIGHT', 'SPRINKLER', None, 23.0, None, None, None,
         1, None, 'IBC', '403.3', 'High-rise buildings (>75ft/23m)', 'INTERNATIONAL', 1),
        ('SPRINK_INT_ASSEMBLY', 'FLOOR_AREA', 'SPRINKLER', None, None, 1115.0, None, 'A',
         1, None, 'IBC', '903.2.1.1', 'Assembly >12,000sf requires NFPA 13', 'INTERNATIONAL', 1),
        ('SPRINK_INT_STORAGE', 'FLOOR_AREA', 'SPRINKLER', None, None, 232.0, None, 'S',
         1, None, 'IBC', '903.2.9', 'Storage >2500sf requires sprinklers', 'INTERNATIONAL', 1),

        # STANDPIPE triggers - Malaysia
        ('STANDPIPE_MY_HIGHRISE', 'BUILDING_HEIGHT', 'STANDPIPE', None, 18.0, None, None, None,
         1, None, 'UBBL', 'By-Law 226', 'Buildings >18m require wet riser', 'MALAYSIA', 1),
        ('STANDPIPE_MY_4STOREY', 'STOREY_COUNT', 'STANDPIPE', 4, None, None, None, None,
         1, None, 'UBBL', 'By-Law 226', 'Buildings >4 storeys require standpipe', 'MALAYSIA', 1),

        # STANDPIPE triggers - International
        ('STANDPIPE_INT_4STOREY', 'STOREY_COUNT', 'STANDPIPE', 4, None, None, None, None,
         1, None, 'IBC', '905.3.1', 'Class III standpipe >4 storeys', 'INTERNATIONAL', 1),

        # FIRE ALARM triggers
        ('ALARM_MY_HIGHRISE', 'BUILDING_HEIGHT', 'FIRE_ALARM', None, 18.0, None, None, None,
         1, None, 'UBBL', 'By-Law 229', 'Fire alarm for high-rise', 'MALAYSIA', 1),
        ('ALARM_INT_ASSEMBLY', 'FLOOR_AREA', 'FIRE_ALARM', None, None, 279.0, None, 'A',
         1, None, 'IBC', '907.2.1', 'Fire alarm for assembly >300 occupants', 'INTERNATIONAL', 1),

        # SMOKE DETECTION
        ('SMOKE_MY_SLEEPING', 'OCCUPANCY', 'SMOKE_DETECTION', None, None, None, None, 'R',
         1, 'per_sleeping_room', 'UBBL', 'By-Law 230', 'Smoke detectors in sleeping rooms', 'MALAYSIA', 1),
    ]

    cursor.executemany("""
        INSERT OR IGNORE INTO ad_fp_trigger
        (trigger_id, trigger_type, element_type, min_storeys, min_height_m,
         min_floor_area_m2, total_area_m2, occupancy_group, min_quantity,
         quantity_formula, code_id, clause, description, jurisdiction, is_mandatory)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
    """, triggers)

    conn.commit()
    cursor.execute("SELECT COUNT(*) FROM ad_fp_trigger")
    print(f"[AD FP] Triggers: {cursor.fetchone()[0]} entries")


def populate_riser_requirements(conn: sqlite3.Connection) -> None:
    """Populate fire riser sizing requirements."""
    cursor = conn.cursor()

    # EXTRACTED from UBBL, NFPA 13/14, BS 9251
    risers = [
        # WET RISER - Malaysia
        ('WET_RISER_MY_LOW', 'WET_RISER', 'LIGHT', 4, 8, 12.0, 24.0, 1000.0,
         65, 25, 500, 2.0, 0, None, 'GATE', 'UBBL', 'By-Law 226', 'Low-rise wet riser (4-8 storeys)', 'MALAYSIA'),
        ('WET_RISER_MY_MID', 'WET_RISER', 'LIGHT', 9, 18, 24.0, 54.0, 1500.0,
         100, 32, 750, 3.5, 1, 45000, 'GATE', 'UBBL', 'By-Law 226', 'Mid-rise wet riser (9-18 storeys)', 'MALAYSIA'),
        ('WET_RISER_MY_HIGH', 'WET_RISER', 'LIGHT', 19, 40, 54.0, 120.0, 2000.0,
         150, 50, 1000, 5.0, 1, 90000, 'GATE', 'UBBL', 'By-Law 226', 'High-rise wet riser (>18 storeys)', 'MALAYSIA'),

        # DRY RISER - Malaysia
        ('DRY_RISER_MY', 'DRY_RISER', 'LIGHT', 4, 18, 12.0, 54.0, 2500.0,
         100, None, None, None, 0, None, 'CHECK', 'UBBL', 'By-Law 227', 'Dry riser for fire brigade access', 'MALAYSIA'),

        # STANDPIPE - International (NFPA 14)
        ('STANDPIPE_INT_CLASS1', 'STANDPIPE', 'LIGHT', None, None, None, 23.0, None,
         100, None, 1893, 6.9, 0, None, 'GATE', 'NFPA 14', '7.2.1', 'Class I standpipe (fire dept use)', 'INTERNATIONAL'),
        ('STANDPIPE_INT_CLASS3', 'STANDPIPE', 'LIGHT', None, None, None, 23.0, None,
         65, 40, 1893, 4.5, 0, None, 'GATE', 'NFPA 14', '7.3.2', 'Class III standpipe (combined)', 'INTERNATIONAL'),

        # SPRINKLER MAIN - by hazard class (NFPA 13)
        ('SPRINK_MAIN_LIGHT', 'COMBINED', 'LIGHT', None, None, None, None, 4000.0,
         80, 25, 500, 0.7, 0, None, 'ALARM', 'NFPA 13', '22.4', 'Light hazard sprinkler main', 'INTERNATIONAL'),
        ('SPRINK_MAIN_ORD1', 'COMBINED', 'ORDINARY_1', None, None, None, None, 3000.0,
         100, 32, 850, 0.7, 0, None, 'ALARM', 'NFPA 13', '22.4', 'Ordinary Group 1 sprinkler main', 'INTERNATIONAL'),
        ('SPRINK_MAIN_ORD2', 'COMBINED', 'ORDINARY_2', None, None, None, None, 2500.0,
         100, 32, 1000, 0.7, 1, None, 'ALARM', 'NFPA 13', '22.4', 'Ordinary Group 2 sprinkler main', 'INTERNATIONAL'),
    ]

    cursor.executemany("""
        INSERT OR IGNORE INTO ad_fire_riser_requirement
        (req_id, riser_type, hazard_class, min_storeys, max_storeys, min_height_m, max_height_m,
         max_floor_area_m2, pipe_diameter_mm, branch_diameter_mm, min_flow_lpm, min_pressure_bar,
         pump_required, tank_capacity_l, valve_type, code_id, clause, description, jurisdiction)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
    """, risers)

    conn.commit()
    cursor.execute("SELECT COUNT(*) FROM ad_fire_riser_requirement")
    print(f"[AD FP] Riser requirements: {cursor.fetchone()[0]} entries")


def populate_compartments(conn: sqlite3.Connection) -> None:
    """Populate fire compartment rules."""
    cursor = conn.cursor()

    # EXTRACTED from UBBL, IBC
    compartments = [
        # Residential (R)
        ('COMP_R_ANY', 'R', None, 500.0, 1000.0, 1.0, 0, 1, 0, 'UBBL', 'By-Law 222', 'MALAYSIA'),
        ('COMP_R_CORRIDOR', 'R', 'CORRIDOR', None, None, 0.5, 0, 0, 0, 'UBBL', 'By-Law 222', 'MALAYSIA'),

        # Assembly (A)
        ('COMP_A_ANY', 'A', None, 300.0, 600.0, 2.0, 1, 1, 0, 'UBBL', 'By-Law 223', 'MALAYSIA'),

        # Business (B)
        ('COMP_B_ANY', 'B', None, 1000.0, 2000.0, 1.0, 0, 1, 0, 'IBC', '707', 'INTERNATIONAL'),

        # Educational (E)
        ('COMP_E_ANY', 'E', None, 600.0, 1200.0, 1.0, 0, 1, 0, 'UBBL', 'By-Law 223', 'MALAYSIA'),

        # Storage (S)
        ('COMP_S_ANY', 'S', None, 500.0, 750.0, 2.0, 1, 1, 0, 'IBC', '903.2.9', 'INTERNATIONAL'),

        # High Hazard (H)
        ('COMP_H_ANY', 'H', None, 200.0, 300.0, 2.0, 1, 1, 1, 'IBC', '415', 'INTERNATIONAL'),
    ]

    cursor.executemany("""
        INSERT OR IGNORE INTO ad_fire_compartment
        (compartment_id, occupancy_group, space_type, max_area_m2, max_area_sprink_m2,
         min_fire_rating_hr, requires_sprinkler, requires_detection, requires_smoke_ctrl,
         code_id, clause, jurisdiction)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
    """, compartments)

    conn.commit()
    cursor.execute("SELECT COUNT(*) FROM ad_fire_compartment")
    print(f"[AD FP] Compartment rules: {cursor.fetchone()[0]} entries")


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    db_path = os.path.join(project_root, DB_PATH)

    if not os.path.exists(db_path):
        print(f"[ERROR] Database not found: {db_path}")
        print("Run: sqlite3 library/BOM.db < schema.sql")
        sys.exit(1)

    print(f"[AD FP] Opening: {db_path}")
    conn = sqlite3.connect(db_path)

    create_fp_schema(conn)
    populate_triggers(conn)
    populate_riser_requirements(conn)
    populate_compartments(conn)

    conn.close()
    print("[AD FP] Phase 57 schema complete")


if __name__ == "__main__":
    main()
