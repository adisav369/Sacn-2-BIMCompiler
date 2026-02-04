#!/usr/bin/env python3
"""
Create AD MEP Schema for BIM Compiler

SIMPLIFIED: Property-value pattern inspired by iDempiere AD_Ref_List.
Three tables handle ALL MEP metadata:
  - ad_reference: Reference types (MEP_SYSTEM, HOST_TYPE, CIRCUIT_TYPE, etc.)
  - ad_ref_value: Values for each reference (ELECTRICAL, WALL, GENERAL, etc.)
  - ad_element_mep: Element characteristics (links to ref_values)

Author: BIM Compiler Project
Date: 2026-02-04
"""

import sqlite3
import os
import sys

DB_PATH = "library/component_library.db"


def create_mep_schema(conn: sqlite3.Connection) -> None:
    """Create MEP AD tables - simplified property-value pattern."""
    cursor = conn.cursor()

    # Reference type definitions (like AD_Reference in iDempiere)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_reference (
        ref_id            TEXT PRIMARY KEY,    -- 'MEP_SYSTEM', 'HOST_TYPE'
        ref_name          TEXT NOT NULL,
        description       TEXT,
        is_active         INTEGER DEFAULT 1
    )
    """)

    # Reference values (like AD_Ref_List in iDempiere)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_ref_value (
        ref_id            TEXT NOT NULL,       -- FK to ad_reference
        value_id          TEXT NOT NULL,       -- 'ELECTRICAL', 'WALL'
        value_name        TEXT NOT NULL,
        seq_no            INTEGER DEFAULT 10,  -- Sort order
        color_code        TEXT,                -- For viz '#FF0000'
        code_reference    TEXT,                -- Standard reference
        extra_json        TEXT,                -- Additional properties as JSON
        is_active         INTEGER DEFAULT 1,
        PRIMARY KEY (ref_id, value_id)
    )
    """)

    # Element MEP characteristics (simplified - uses ref_values)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_element_mep (
        element_type      TEXT PRIMARY KEY,    -- 'OUTLET', 'SPRINKLER'
        ifc_class         TEXT NOT NULL,       -- 'IfcOutlet'
        discipline        TEXT NOT NULL,       -- 'ELEC', 'FP', 'SP'
        mep_system        TEXT,                -- -> ad_ref_value(MEP_SYSTEM, *)
        host_type         TEXT,                -- -> ad_ref_value(HOST_TYPE, *)
        dist_role         TEXT,                -- -> ad_ref_value(DIST_ROLE, *)
        circuit_type      TEXT,                -- -> ad_ref_value(CIRCUIT_TYPE, *)
        mount_height      REAL,                -- meters from floor
        clearance         TEXT,                -- JSON: {"front":0.5,"side":0.3}
        ports             TEXT,                -- JSON: [{"id":"IN","size":0.015}]
        properties        TEXT,                -- JSON: extra properties
        code_ref          TEXT,
        is_active         INTEGER DEFAULT 1
    )
    """)

    # Sprinkler coverage - kept separate as it's rule-heavy
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_fp_coverage (
        hazard_class      TEXT PRIMARY KEY,    -- 'LIGHT', 'ORDINARY_1'
        max_coverage_m2   REAL NOT NULL,
        max_spacing_m     REAL NOT NULL,
        min_spacing_m     REAL NOT NULL,
        wall_distance_m   REAL,
        k_factor          REAL,
        code_ref          TEXT DEFAULT 'NFPA 13',
        is_active         INTEGER DEFAULT 1
    )
    """)

    conn.commit()
    print("[AD MEP] Created: ad_reference, ad_ref_value, ad_element_mep, ad_fp_coverage")


def populate_data(conn: sqlite3.Connection) -> None:
    """Populate reference data."""
    cursor = conn.cursor()

    # Reference types
    refs = [
        ('MEP_SYSTEM', 'MEP Distribution System', 'ELECTRICAL, PLUMBING, HVAC, FP'),
        ('HOST_TYPE', 'Host Element Type', 'WALL, CEILING, FLOOR, ROOM'),
        ('DIST_ROLE', 'Distribution Role', 'SOURCE, MAIN, BRANCH, TERMINAL'),
        ('CIRCUIT_TYPE', 'Electrical Circuit Type', 'GENERAL, LIGHTING, DEDICATED'),
        ('ATTACH_FACE', 'Attachment Face', 'INTERIOR, EXTERIOR, TOP, BOTTOM'),
    ]
    cursor.executemany("INSERT OR IGNORE INTO ad_reference VALUES (?,?,?,1)", refs)

    # Reference values - all in one table
    values = [
        # MEP Systems
        ('MEP_SYSTEM', 'ELECTRICAL', 'Electrical Power', 10, '#FFFF00', 'MS IEC 60364', None),
        ('MEP_SYSTEM', 'PLUMBING_SUPPLY', 'Water Supply', 20, '#0000FF', 'IPC', None),
        ('MEP_SYSTEM', 'PLUMBING_WASTE', 'Sanitary Waste', 21, '#8B4513', 'IPC', None),
        ('MEP_SYSTEM', 'PLUMBING_VENT', 'Vent Stack', 22, '#90EE90', 'IPC', None),
        ('MEP_SYSTEM', 'HVAC_SUPPLY', 'Supply Air', 30, '#00FFFF', 'ASHRAE', None),
        ('MEP_SYSTEM', 'HVAC_EXHAUST', 'Exhaust Air', 31, '#808080', 'ASHRAE', None),
        ('MEP_SYSTEM', 'FIRE_PROTECTION', 'Fire Sprinkler', 40, '#FF0000', 'NFPA 13', None),
        ('MEP_SYSTEM', 'FIRE_ALARM', 'Fire Alarm', 41, '#FF6600', 'NFPA 72', None),
        # Host Types
        ('HOST_TYPE', 'WALL', 'Wall', 10, None, None, '{"thickness":0.15,"openings":true}'),
        ('HOST_TYPE', 'CEILING', 'Ceiling', 20, None, None, '{"thickness":0.15}'),
        ('HOST_TYPE', 'FLOOR', 'Floor', 30, None, None, '{"thickness":0.15}'),
        ('HOST_TYPE', 'ROOM', 'Room/Space', 40, None, None, '{"openings":false}'),
        # Distribution Roles
        ('DIST_ROLE', 'SOURCE', 'Source', 10, None, None, '{"inlet":false,"outlet":true}'),
        ('DIST_ROLE', 'MAIN', 'Main Distribution', 20, None, None, '{"inlet":true,"outlet":true}'),
        ('DIST_ROLE', 'BRANCH', 'Branch', 30, None, None, '{"inlet":true,"outlet":true}'),
        ('DIST_ROLE', 'TERMINAL', 'Terminal', 50, None, None, '{"inlet":true,"outlet":false}'),
        ('DIST_ROLE', 'JUNCTION', 'Junction', 35, None, None, '{"inlet":true,"outlet":true}'),
        # Circuit Types
        ('CIRCUIT_TYPE', 'GENERAL', 'General Purpose', 10, None, 'MS IEC 60364', '{"voltage":240,"max_outlets":10,"breaker":10,"wire":"2.5mm²"}'),
        ('CIRCUIT_TYPE', 'LIGHTING', 'Lighting', 20, None, 'MS IEC 60364', '{"voltage":240,"max_outlets":12,"breaker":6,"wire":"1.5mm²"}'),
        ('CIRCUIT_TYPE', 'DEDICATED', 'Dedicated (Aircon)', 30, None, 'MS IEC 60364', '{"voltage":240,"max_outlets":1,"breaker":16,"wire":"4mm²"}'),
        ('CIRCUIT_TYPE', 'BATHROOM', 'Bathroom GFCI', 40, None, 'MS IEC 60364', '{"voltage":240,"max_outlets":4,"breaker":10,"gfci":true}'),
        # Attachment Faces
        ('ATTACH_FACE', 'INTERIOR', 'Interior', 10, None, None, None),
        ('ATTACH_FACE', 'EXTERIOR', 'Exterior', 20, None, None, None),
        ('ATTACH_FACE', 'TOP', 'Top Surface', 30, None, None, None),
        ('ATTACH_FACE', 'BOTTOM', 'Bottom Surface', 40, None, None, None),
        ('ATTACH_FACE', 'CENTER', 'Centered', 50, None, None, None),
    ]
    cursor.executemany("INSERT OR IGNORE INTO ad_ref_value VALUES (?,?,?,?,?,?,?,1)", values)

    # Element MEP - all properties as JSON
    elements = [
        # Electrical
        ('OUTLET', 'IfcOutlet', 'ELEC', 'ELECTRICAL', 'WALL', 'TERMINAL', 'GENERAL', 0.3,
         '{"front":0,"side":0}', '[{"id":"IN","size":null}]', '{"watts":180}', 'MS IEC 60364'),
        ('SWITCH', 'IfcSwitchingDevice', 'ELEC', 'ELECTRICAL', 'WALL', 'TERMINAL', 'LIGHTING', 1.2,
         '{"front":0,"side":0}', '[{"id":"IN"},{"id":"OUT"}]', '{"watts":10}', 'MS IEC 60364'),
        ('LIGHT', 'IfcLightFixture', 'ELEC', 'ELECTRICAL', 'CEILING', 'TERMINAL', 'LIGHTING', None,
         '{"front":0,"side":0}', '[{"id":"IN"}]', '{"watts":60}', 'MS IEC 60364'),
        ('PANEL', 'IfcDistributionBoard', 'ELEC', 'ELECTRICAL', 'WALL', 'SOURCE', None, 1.5,
         '{"front":0.9,"side":0.3}', None, '{"circuits":20}', 'MS IEC 60364'),
        # Plumbing
        ('TOILET', 'IfcSanitaryTerminal', 'SP', 'PLUMBING_WASTE', 'FLOOR', 'TERMINAL', None, 0,
         '{"front":0.533,"side":0.381}', '[{"id":"WASTE","size":0.1},{"id":"SUPPLY","size":0.015}]', '{"lpm":6}', 'IPC'),
        ('SINK', 'IfcSanitaryTerminal', 'SP', 'PLUMBING_WASTE', 'WALL', 'TERMINAL', None, 0.85,
         '{"front":0.5,"side":0.3}', '[{"id":"WASTE","size":0.04},{"id":"SUPPLY","size":0.015}]', '{"lpm":8}', 'IPC'),
        ('FLOOR_DRAIN', 'IfcSanitaryTerminal', 'SP', 'PLUMBING_WASTE', 'FLOOR', 'TERMINAL', None, 0,
         '{"front":0,"side":0}', '[{"id":"WASTE","size":0.075}]', None, 'IPC'),
        # Fire Protection
        ('SPRINKLER', 'IfcFireSuppressionTerminal', 'FP', 'FIRE_PROTECTION', 'CEILING', 'TERMINAL', None, None,
         '{"front":0,"side":0}', '[{"id":"IN","size":0.015}]', '{"k_factor":5.6,"lpm":50}', 'NFPA 13'),
        ('FP_RISER', 'IfcPipeSegment', 'FP', 'FIRE_PROTECTION', None, 'MAIN', None, None,
         None, '[{"id":"IN","size":0.1},{"id":"OUT","size":0.1}]', None, 'NFPA 13'),
        ('SMOKE_DETECTOR', 'IfcSensor', 'FP', 'FIRE_ALARM', 'CEILING', 'TERMINAL', None, None,
         '{"front":0,"side":0}', '[{"id":"SIGNAL"}]', '{"coverage_m2":84}', 'NFPA 72'),
        # HVAC
        ('DIFFUSER', 'IfcAirTerminal', 'HVAC', 'HVAC_SUPPLY', 'CEILING', 'TERMINAL', None, None,
         '{"front":0,"side":0}', '[{"id":"IN","size":0.2}]', '{"cfm":150}', 'ASHRAE'),
        ('EXHAUST_FAN', 'IfcFan', 'HVAC', 'HVAC_EXHAUST', 'WALL', 'TERMINAL', 'DEDICATED', None,
         '{"front":0,"side":0}', '[{"id":"OUT","size":0.15}]', '{"cfm":50,"watts":25}', 'ASHRAE'),
    ]
    cursor.executemany("""
        INSERT OR IGNORE INTO ad_element_mep
        (element_type, ifc_class, discipline, mep_system, host_type, dist_role, circuit_type,
         mount_height, clearance, ports, properties, code_ref)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
    """, elements)

    # FP Coverage (NFPA 13)
    coverage = [
        ('LIGHT', 18.6, 4.6, 1.8, 2.3, 5.6, 'NFPA 13 Table 10.2.4.2.1'),
        ('ORDINARY_1', 12.1, 4.0, 1.8, 2.1, 5.6, 'NFPA 13'),
        ('ORDINARY_2', 12.1, 4.0, 1.8, 2.1, 8.0, 'NFPA 13'),
        ('RESIDENTIAL', 37.2, 6.1, 2.4, 2.4, 4.2, 'NFPA 13R'),
    ]
    cursor.executemany("""
        INSERT OR IGNORE INTO ad_fp_coverage
        (hazard_class, max_coverage_m2, max_spacing_m, min_spacing_m, wall_distance_m, k_factor, code_ref)
        VALUES (?,?,?,?,?,?,?)
    """, coverage)

    conn.commit()

    # Summary
    cursor.execute("SELECT COUNT(*) FROM ad_reference")
    ref_count = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM ad_ref_value")
    val_count = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM ad_element_mep")
    elem_count = cursor.fetchone()[0]
    print(f"[AD MEP] Populated: {ref_count} refs, {val_count} values, {elem_count} elements")


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    db_path = os.path.join(project_root, DB_PATH)

    if not os.path.exists(db_path):
        print(f"[ERROR] Database not found: {db_path}")
        sys.exit(1)

    print(f"[AD MEP] Opening: {db_path}")
    conn = sqlite3.connect(db_path)
    create_mep_schema(conn)
    populate_data(conn)
    conn.close()
    print("[AD MEP] Done")


if __name__ == "__main__":
    main()
