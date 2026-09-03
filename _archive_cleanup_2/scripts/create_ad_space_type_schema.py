# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
Create AD Space Type Schema for BIM Compiler

Phase 1 of Metadata-Driven Architecture:
Adds ad_space_type* tables to existing BOM.db
following the iDempiere Application Dictionary (AD) pattern.

Source: Building codes (IRC, UBBL, MS 1184) captured in spacetypes.yaml
Target: library/BOM.db (extends existing AD-like structure)

Usage:
    python scripts/create_ad_space_type_schema.py

Author: BIM Compiler Project
"""

import sqlite3
import os
import sys

# Database path relative to project root
DB_PATH = "library/BOM.db"


def create_schema(conn: sqlite3.Connection) -> None:
    """Create AD space type tables if they don't exist."""
    cursor = conn.cursor()

    # Main space type definition
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_space_type (
        space_type_id     TEXT PRIMARY KEY,    -- 'BEDROOM', 'BATHROOM'
        category          TEXT NOT NULL,        -- 'HABITABLE', 'SERVICE', etc.
        omniclass_code    TEXT NOT NULL,        -- '13-21 11 00'
        wall_rule         TEXT NOT NULL,        -- 'ENCLOSED', 'PERIMETER_ONLY', 'NONE', 'AS_REQUIRED'
        is_sleeping_room  INTEGER DEFAULT 0,
        is_open_plan      INTEGER DEFAULT 0,
        is_exterior       INTEGER DEFAULT 0,
        min_area          REAL DEFAULT 0,       -- m^2 (from IRC/UBBL)
        min_dimension     REAL DEFAULT 0,       -- m
        requires_window   INTEGER DEFAULT 0,
        requires_egress   INTEGER DEFAULT 0,
        structural_grid   INTEGER DEFAULT 0,    -- For large-span spaces
        beam_max_span     REAL DEFAULT 8.0,
        code_reference    TEXT,                 -- 'IRC R304.1', 'UBBL 1984'
        is_active         INTEGER DEFAULT 1
    )
    """)

    # Alias lookup (BT -> BEDROOM, MR -> MASTER_BEDROOM)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_space_type_alias (
        alias             TEXT PRIMARY KEY,
        space_type_id     TEXT NOT NULL REFERENCES ad_space_type(space_type_id)
    )
    """)

    # MEP requirements per space type
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_space_type_mep (
        space_type_id     TEXT PRIMARY KEY REFERENCES ad_space_type(space_type_id),
        plumbing_fixtures TEXT,                 -- JSON: '["toilet","sink"]'
        requires_stack    INTEGER DEFAULT 0,
        requires_vent     INTEGER DEFAULT 0,
        water_supply      TEXT DEFAULT 'none',
        light_points      INTEGER DEFAULT 0,
        power_points      INTEGER DEFAULT 0,
        switch_points     INTEGER DEFAULT 0,
        requires_dedicated INTEGER DEFAULT 0,
        circuit_type      TEXT DEFAULT 'general',
        requires_exhaust  INTEGER DEFAULT 0,
        exhaust_cfm       INTEGER DEFAULT 0,
        allows_aircon     INTEGER DEFAULT 0,
        ventilation_type  TEXT DEFAULT 'natural'
    )
    """)

    # Create indexes for efficient lookup
    cursor.execute("""
    CREATE INDEX IF NOT EXISTS idx_ad_space_type_category
    ON ad_space_type(category)
    """)

    cursor.execute("""
    CREATE INDEX IF NOT EXISTS idx_ad_space_type_alias_type
    ON ad_space_type_alias(space_type_id)
    """)

    conn.commit()
    print("[AD Schema] Created tables: ad_space_type, ad_space_type_alias, ad_space_type_mep")


def verify_schema(conn: sqlite3.Connection) -> bool:
    """Verify all expected tables exist."""
    cursor = conn.cursor()

    expected_tables = ['ad_space_type', 'ad_space_type_alias', 'ad_space_type_mep']

    for table in expected_tables:
        cursor.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            (table,)
        )
        if not cursor.fetchone():
            print(f"[ERROR] Table {table} not found")
            return False

    print(f"[AD Schema] Verified {len(expected_tables)} tables exist")
    return True


def main():
    """Main entry point."""
    # Find project root (contains library/ directory)
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    db_path = os.path.join(project_root, DB_PATH)

    if not os.path.exists(db_path):
        print(f"[ERROR] Database not found: {db_path}")
        print("[ERROR] Run from project root or ensure BOM.db exists")
        sys.exit(1)

    print(f"[AD Schema] Opening database: {db_path}")

    try:
        conn = sqlite3.connect(db_path)
        create_schema(conn)

        if verify_schema(conn):
            print("[AD Schema] Schema creation complete")
        else:
            print("[ERROR] Schema verification failed")
            sys.exit(1)

    except sqlite3.Error as e:
        print(f"[ERROR] SQLite error: {e}")
        sys.exit(1)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
