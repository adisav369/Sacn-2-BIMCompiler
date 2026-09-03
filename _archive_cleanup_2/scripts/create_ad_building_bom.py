# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
Building BOM (Bill of Materials) Schema - iDempiere M_BOM Pattern

Buildings as hierarchical BOMs like trains:
  HEAD (basement, ground) → STANDARD (typical floors) → TAIL (penthouse, roof)

Tables:
  ad_building_template: Building type templates (condo, office, hotel)
  ad_floor_type: Floor type definitions (basement, typical, penthouse)
  ad_building_bom: BOM linking template → floor types with sequence

This is the 80/20 pattern - most buildings are:
  - 1-2 basement levels
  - 1 ground floor (retail/lobby)
  - N typical floors (the repeating middle)
  - 1 penthouse/roof level

Author: BIM Compiler Project
Date: 2026-02-04
"""

import sqlite3
import os
import sys

DB_PATH = "library/BOM.db"


def create_bom_schema(conn: sqlite3.Connection) -> None:
    """Create Building BOM tables."""
    cursor = conn.cursor()

    # Building templates (like M_Product with BOM)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_building_template (
        template_id       TEXT PRIMARY KEY,    -- 'CONDO_30F', 'OFFICE_TOWER'
        template_name     TEXT NOT NULL,
        building_type     TEXT NOT NULL,       -- RESIDENTIAL, COMMERCIAL, MIXED
        description       TEXT,
        default_floors    INTEGER,             -- Total floors if not specified
        min_floors        INTEGER,
        max_floors        INTEGER,
        typical_floor_start INTEGER,           -- Where typical floors begin (1-indexed)
        typical_floor_end   INTEGER,           -- Where typical floors end (negative = from top)
        structural_system TEXT,                -- FRAMED, SHEAR_WALL, CORE_OUTRIGGER
        code_reference    TEXT,
        is_active         INTEGER DEFAULT 1
    )
    """)

    # Floor types (reusable floor definitions)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_floor_type (
        floor_type_id     TEXT PRIMARY KEY,    -- 'BASEMENT_1', 'TYPICAL_CONDO'
        floor_name        TEXT NOT NULL,
        floor_category    TEXT NOT NULL,       -- BASEMENT, GROUND, TYPICAL, TRANSFER, ROOF, PENTHOUSE
        floor_height      REAL NOT NULL,       -- Floor-to-floor height in meters
        slab_thickness    REAL DEFAULT 0.15,
        is_below_grade    INTEGER DEFAULT 0,
        is_mechanical     INTEGER DEFAULT 0,   -- MEP floor (transfer, refuge)
        is_amenity        INTEGER DEFAULT 0,   -- Pool, gym, sky garden
        unit_types        TEXT,                -- JSON: allowed unit types on this floor
        structural_notes  TEXT,
        mep_notes         TEXT,
        is_active         INTEGER DEFAULT 1
    )
    """)

    # Building BOM - links template to floor types (like M_BOM_Product)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_building_bom (
        bom_id            INTEGER PRIMARY KEY AUTOINCREMENT,
        template_id       TEXT NOT NULL REFERENCES ad_building_template(template_id),
        floor_type_id     TEXT NOT NULL REFERENCES ad_floor_type(floor_type_id),
        position          TEXT NOT NULL,       -- HEAD, STANDARD, TAIL
        seq_no            INTEGER NOT NULL,    -- Order within position (1, 2, 3...)
        qty_fixed         INTEGER,             -- Fixed quantity (for HEAD/TAIL)
        qty_min           INTEGER,             -- Min repeats (for STANDARD)
        qty_max           INTEGER,             -- Max repeats (for STANDARD)
        qty_default       INTEGER,             -- Default repeats
        level_name_pattern TEXT,               -- 'B%d', 'Level_%d', 'P%d'
        notes             TEXT,
        is_active         INTEGER DEFAULT 1,
        UNIQUE(template_id, floor_type_id, seq_no)
    )
    """)

    # Unit types (what goes on each floor)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_unit_type (
        unit_type_id      TEXT PRIMARY KEY,    -- '1BR_A', '2BR_B', 'STUDIO'
        unit_name         TEXT NOT NULL,
        unit_category     TEXT NOT NULL,       -- STUDIO, 1BR, 2BR, 3BR, PENTHOUSE, RETAIL
        typical_area      REAL,                -- m²
        bedroom_count     INTEGER DEFAULT 0,
        bathroom_count    INTEGER DEFAULT 0,
        has_balcony       INTEGER DEFAULT 0,
        has_utility       INTEGER DEFAULT 0,
        room_layout       TEXT,                -- JSON: room types and rough areas
        mep_load          TEXT,                -- JSON: electrical, plumbing loads
        is_active         INTEGER DEFAULT 1
    )
    """)

    conn.commit()
    print("[AD BOM] Created: ad_building_template, ad_floor_type, ad_building_bom, ad_unit_type")


def populate_data(conn: sqlite3.Connection) -> None:
    """Populate reference data."""
    cursor = conn.cursor()

    # Building templates
    templates = [
        ('CONDO_LOW', 'Low-Rise Condo', 'RESIDENTIAL', '4-8 storey residential', 6, 4, 8, 2, -1, 'FRAMED', 'UBBL'),
        ('CONDO_MID', 'Mid-Rise Condo', 'RESIDENTIAL', '10-20 storey residential', 15, 10, 20, 3, -2, 'SHEAR_WALL', 'UBBL'),
        ('CONDO_HIGH', 'High-Rise Condo', 'RESIDENTIAL', '25-50 storey residential', 35, 25, 50, 4, -3, 'CORE_OUTRIGGER', 'UBBL'),
        ('OFFICE_MID', 'Mid-Rise Office', 'COMMERCIAL', '10-20 storey office', 15, 10, 20, 2, -1, 'FRAMED', 'UBBL'),
        ('OFFICE_HIGH', 'High-Rise Office', 'COMMERCIAL', '30+ storey office', 40, 30, 60, 3, -2, 'CORE_OUTRIGGER', 'UBBL'),
        ('MIXED_USE', 'Mixed Use Tower', 'MIXED', 'Retail + Office + Residential', 30, 20, 45, 5, -3, 'CORE_OUTRIGGER', 'UBBL'),
        ('HOTEL_MID', 'Mid-Rise Hotel', 'HOSPITALITY', '10-20 storey hotel', 15, 10, 20, 2, -1, 'SHEAR_WALL', 'UBBL'),
        ('LANDED_2S', 'Two-Storey House', 'RESIDENTIAL', 'Landed residential', 2, 1, 3, 1, 2, 'FRAMED', 'UBBL'),
    ]
    cursor.executemany("""
        INSERT OR IGNORE INTO ad_building_template
        (template_id, template_name, building_type, description, default_floors,
         min_floors, max_floors, typical_floor_start, typical_floor_end, structural_system, code_reference)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, templates)

    # Floor types
    floors = [
        ('BASEMENT_PARKING', 'Basement Parking', 'BASEMENT', 3.0, 0.20, 1, 0, 0, None, 'Transfer beams for columns above', 'Sump pumps, mechanical vent'),
        ('BASEMENT_MECH', 'Basement Mechanical', 'BASEMENT', 4.0, 0.25, 1, 1, 0, None, 'Heavy loads for equipment', 'Main switchroom, pump room, water tanks'),
        ('GROUND_LOBBY', 'Ground Floor Lobby', 'GROUND', 4.5, 0.20, 0, 0, 0, '["LOBBY","RETAIL"]', 'Double height possible', 'Main electrical intake, water/gas meters'),
        ('GROUND_RETAIL', 'Ground Floor Retail', 'GROUND', 4.5, 0.20, 0, 0, 0, '["RETAIL","F&B"]', 'High floor load for retail', 'Grease traps, exhaust risers'),
        ('TYPICAL_CONDO', 'Typical Condo Floor', 'TYPICAL', 3.0, 0.15, 0, 0, 0, '["STUDIO","1BR","2BR","3BR"]', None, 'Wet areas stacked'),
        ('TYPICAL_OFFICE', 'Typical Office Floor', 'TYPICAL', 3.6, 0.15, 0, 0, 0, '["OFFICE"]', 'Raised floor zone 150mm', 'Floor boxes, VAV zones'),
        ('TYPICAL_HOTEL', 'Typical Hotel Floor', 'TYPICAL', 3.0, 0.15, 0, 0, 0, '["HOTEL_ROOM"]', 'Sound isolation STC 55', 'FCU each room'),
        ('TRANSFER_MECH', 'Transfer/Mechanical', 'TRANSFER', 4.0, 0.30, 0, 1, 0, None, 'Transfer structure', 'AHU plant, cooling towers'),
        ('AMENITY', 'Amenity Floor', 'AMENITY', 4.0, 0.20, 0, 0, 1, '["GYM","POOL","FUNCTION"]', 'Pool waterproofing', 'Pool plant, gym ventilation'),
        ('REFUGE', 'Refuge Floor', 'REFUGE', 4.0, 0.20, 0, 1, 0, None, 'Fire rated construction', 'Pressurization, emergency power'),
        ('PENTHOUSE', 'Penthouse Floor', 'PENTHOUSE', 3.6, 0.20, 0, 0, 0, '["PENTHOUSE"]', 'Premium finishes', 'Dedicated services'),
        ('ROOF_MECH', 'Roof Mechanical', 'ROOF', 3.0, 0.20, 0, 1, 0, None, 'Equipment pads', 'Cooling towers, lift motor room'),
    ]
    cursor.executemany("""
        INSERT OR IGNORE INTO ad_floor_type
        (floor_type_id, floor_name, floor_category, floor_height, slab_thickness,
         is_below_grade, is_mechanical, is_amenity, unit_types, structural_notes, mep_notes)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, floors)

    # Building BOM for mid-rise condo (train pattern)
    bom_condo_mid = [
        # HEAD (basement + ground)
        ('CONDO_MID', 'BASEMENT_PARKING', 'HEAD', 1, 2, None, None, 'B%d'),    # 2 basement levels
        ('CONDO_MID', 'GROUND_LOBBY', 'HEAD', 2, 1, None, None, 'Ground'),     # 1 ground floor
        # STANDARD (typical floors - the repeating middle)
        ('CONDO_MID', 'TYPICAL_CONDO', 'STANDARD', 1, None, 8, 16, 'Level_%d'), # 8-16 typical floors
        # TAIL (top floors)
        ('CONDO_MID', 'AMENITY', 'TAIL', 1, 1, None, None, 'Amenity'),         # 1 amenity
        ('CONDO_MID', 'PENTHOUSE', 'TAIL', 2, 1, None, None, 'Penthouse'),     # 1 penthouse
        ('CONDO_MID', 'ROOF_MECH', 'TAIL', 3, 1, None, None, 'Roof'),          # 1 roof
    ]
    cursor.executemany("""
        INSERT OR IGNORE INTO ad_building_bom
        (template_id, floor_type_id, position, seq_no, qty_fixed, qty_min, qty_max, level_name_pattern)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """, bom_condo_mid)

    # Building BOM for 2-storey house
    bom_landed = [
        ('LANDED_2S', 'GROUND_LOBBY', 'HEAD', 1, 1, None, None, 'Ground'),
        ('LANDED_2S', 'TYPICAL_CONDO', 'TAIL', 1, 1, None, None, 'Upper'),
    ]
    cursor.executemany("""
        INSERT OR IGNORE INTO ad_building_bom
        (template_id, floor_type_id, position, seq_no, qty_fixed, qty_min, qty_max, level_name_pattern)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """, bom_landed)

    # Unit types
    units = [
        ('STUDIO', 'Studio', 'STUDIO', 35, 0, 1, 0, 0, '{"living":25,"bathroom":5,"kitchen":5}', '{"elec_kw":3,"plumb_lpm":15}'),
        ('1BR_A', '1-Bedroom Type A', '1BR', 55, 1, 1, 1, 0, '{"living":25,"bedroom":12,"bathroom":6,"kitchen":8}', '{"elec_kw":5,"plumb_lpm":20}'),
        ('1BR_B', '1-Bedroom Type B', '1BR', 60, 1, 1, 1, 1, '{"living":25,"bedroom":14,"bathroom":6,"kitchen":8,"balcony":5}', '{"elec_kw":5,"plumb_lpm":20}'),
        ('2BR_A', '2-Bedroom Type A', '2BR', 85, 2, 2, 1, 1, '{"living":30,"br1":14,"br2":12,"bath1":6,"bath2":5,"kitchen":10}', '{"elec_kw":7,"plumb_lpm":30}'),
        ('2BR_B', '2-Bedroom Type B (Corner)', '2BR', 95, 2, 2, 1, 1, '{"living":35,"br1":16,"br2":14,"bath1":6,"bath2":5,"kitchen":12,"balcony":8}', '{"elec_kw":8,"plumb_lpm":30}'),
        ('3BR_A', '3-Bedroom Type A', '3BR', 120, 3, 2, 1, 1, '{"living":35,"br1":16,"br2":12,"br3":12,"bath1":6,"bath2":5,"kitchen":12}', '{"elec_kw":10,"plumb_lpm":40}'),
        ('PENTHOUSE_A', 'Penthouse Type A', 'PENTHOUSE', 200, 3, 3, 1, 1, '{"living":60,"br1":25,"br2":20,"br3":18,"bath1":12,"bath2":8,"bath3":6,"kitchen":20}', '{"elec_kw":20,"plumb_lpm":60}'),
    ]
    cursor.executemany("""
        INSERT OR IGNORE INTO ad_unit_type
        (unit_type_id, unit_name, unit_category, typical_area, bedroom_count,
         bathroom_count, has_balcony, has_utility, room_layout, mep_load)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, units)

    conn.commit()
    print(f"[AD BOM] Populated: {len(templates)} templates, {len(floors)} floor types, {len(bom_condo_mid)+len(bom_landed)} BOM entries, {len(units)} unit types")


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    db_path = os.path.join(project_root, DB_PATH)

    if not os.path.exists(db_path):
        print(f"[ERROR] Database not found: {db_path}")
        sys.exit(1)

    print(f"[AD BOM] Opening: {db_path}")
    conn = sqlite3.connect(db_path)
    create_bom_schema(conn)
    populate_data(conn)
    conn.close()
    print("[AD BOM] Done")


if __name__ == "__main__":
    main()
