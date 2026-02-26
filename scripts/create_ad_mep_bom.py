#!/usr/bin/env python3
"""
Create MEP BOM tables in BOM.db

Tables:
- ad_mep_profile: BUDGET/STANDARD/PREMIUM profiles
- ad_space_type_mep_bom: MEP product quantities per space type with min/normal/max

Pattern: Like iDempiere M_Product_PO MOQ concept
"""

import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), '..', 'library', 'BOM.db')

def create_tables(conn):
    """Create MEP BOM tables"""

    # Profile table - maps DSL option to column selection
    conn.execute("""
        CREATE TABLE IF NOT EXISTS ad_mep_profile (
            profile_id      TEXT PRIMARY KEY,
            description     TEXT,
            qty_column      TEXT,
            per_area_column TEXT,
            conduit_column  TEXT
        )
    """)

    # MEP BOM table - quantities per space type with min/normal/max bounds
    conn.execute("""
        CREATE TABLE IF NOT EXISTS ad_space_type_mep_bom (
            space_type_id   TEXT,
            mep_product_id  TEXT,

            -- Fixed quantity bounds
            qty_min         INTEGER DEFAULT 0,
            qty_normal      INTEGER DEFAULT 1,
            qty_max         INTEGER DEFAULT 99,

            -- Per-area bounds (when > 0, overrides fixed qty)
            per_area_min    REAL DEFAULT 0,
            per_area_normal REAL DEFAULT 0,
            per_area_max    REAL DEFAULT 0,

            -- Placement
            placement_rule  TEXT DEFAULT 'AUTO',
            host_surface    TEXT DEFAULT 'WALL',

            -- Provenance
            building_code   TEXT,
            code_clause     TEXT,

            -- Conduit/pipe budget (meters)
            conduit_min     REAL DEFAULT 0,
            conduit_normal  REAL DEFAULT 0,
            conduit_max     REAL DEFAULT 0,

            PRIMARY KEY (space_type_id, mep_product_id)
        )
    """)

    conn.commit()
    print("Tables created: ad_mep_profile, ad_space_type_mep_bom")

def populate_profiles(conn):
    """Insert MEP profile definitions"""

    profiles = [
        ('BUDGET',   'Code minimum - meets requirements only',
         'qty_min', 'per_area_min', 'conduit_min'),
        ('STANDARD', 'Typical installation - balanced',
         'qty_normal', 'per_area_normal', 'conduit_normal'),
        ('PREMIUM',  'Full specification - maximum comfort',
         'qty_max', 'per_area_max', 'conduit_max'),
    ]

    conn.executemany("""
        INSERT OR REPLACE INTO ad_mep_profile
        (profile_id, description, qty_column, per_area_column, conduit_column)
        VALUES (?, ?, ?, ?, ?)
    """, profiles)

    conn.commit()
    print(f"Inserted {len(profiles)} MEP profiles")

def populate_bom(conn):
    """Insert MEP BOM data with Malaysian + international codes"""

    # Format: (space_type, product, qty_min, qty_normal, qty_max,
    #          per_area_min, per_area_normal, per_area_max,
    #          placement_rule, host_surface, building_code, code_clause,
    #          conduit_min, conduit_normal, conduit_max)

    bom_data = [
        # =====================================================================
        # BATHROOM - wet area, requires GFCI, exhaust mandatory
        # =====================================================================
        ('BATHROOM', 'TOILET', 1, 1, 1, 0, 0, 0,
         'WALL_BACK', 'FLOOR', 'IPC 2021', '405.3.1', 0, 0, 0),
        ('BATHROOM', 'SINK', 1, 1, 2, 0, 0, 0,
         'WALL_SIDE', 'WALL', 'IPC 2021', '405.3.1', 0, 0, 0),
        ('BATHROOM', 'FLOOR_TRAP', 1, 1, 1, 0, 0, 0,
         'FLOOR_LOW', 'FLOOR', 'MS 1228', 'Clause 5', 0, 0, 0),
        ('BATHROOM', 'OUTLET_GFCI', 1, 1, 2, 0, 0, 0,
         'WALL_SINK', 'WALL', 'NEC 2020', '210.8(A)', 3, 5, 8),
        ('BATHROOM', 'LIGHT', 1, 1, 2, 0, 0, 0,
         'CEILING_CENTER', 'CEILING', 'NEC 2020', '210.70(A)', 3, 4, 6),
        ('BATHROOM', 'SWITCH', 1, 1, 1, 0, 0, 0,
         'WALL_ENTRY', 'WALL', 'NEC 2020', '404.4', 2, 3, 4),
        ('BATHROOM', 'EXHAUST_FAN', 1, 1, 1, 0, 0, 0,
         'CEILING_CENTER', 'CEILING', 'IMC 2021', '403.3', 2, 3, 5),
        ('BATHROOM', 'SPRINKLER', 0, 1, 1, 0, 0, 0,
         'CEILING_CENTER', 'CEILING', 'NFPA 13', '8.5.5', 2, 3, 4),

        # =====================================================================
        # BEDROOM - general area, outlets per wall rule
        # =====================================================================
        ('BEDROOM', 'OUTLET', 2, 3, 4, 0, 0, 0,
         'WALL_SPACED', 'WALL', 'NEC 2020', '210.52(A)', 6, 10, 15),
        ('BEDROOM', 'LIGHT', 1, 1, 2, 0, 0, 0,
         'CEILING_CENTER', 'CEILING', 'NEC 2020', '210.70(A)', 3, 4, 6),
        ('BEDROOM', 'SWITCH', 1, 1, 2, 0, 0, 0,
         'WALL_ENTRY', 'WALL', 'NEC 2020', '404.4', 2, 3, 4),
        ('BEDROOM', 'AIRCON_POINT', 0, 1, 1, 0, 0, 0,
         'WALL_HIGH', 'WALL', 'MS IEC 60364', '5.5', 4, 6, 8),
        ('BEDROOM', 'SPRINKLER', 0, 0, 1, 0.05, 0.07, 0.1,
         'CEILING_GRID', 'CEILING', 'NFPA 13', '8.5.5', 2, 3, 4),

        # =====================================================================
        # MASTER_BEDROOM - like bedroom but more outlets
        # =====================================================================
        ('MASTER_BEDROOM', 'OUTLET', 3, 4, 6, 0, 0, 0,
         'WALL_SPACED', 'WALL', 'NEC 2020', '210.52(A)', 8, 12, 18),
        ('MASTER_BEDROOM', 'LIGHT', 1, 2, 3, 0, 0, 0,
         'CEILING_CENTER', 'CEILING', 'NEC 2020', '210.70(A)', 4, 6, 10),
        ('MASTER_BEDROOM', 'SWITCH', 1, 2, 3, 0, 0, 0,
         'WALL_ENTRY', 'WALL', 'NEC 2020', '404.4', 3, 5, 8),
        ('MASTER_BEDROOM', 'AIRCON_POINT', 1, 1, 2, 0, 0, 0,
         'WALL_HIGH', 'WALL', 'MS IEC 60364', '5.5', 4, 6, 8),
        ('MASTER_BEDROOM', 'SPRINKLER', 0, 0, 1, 0.05, 0.07, 0.1,
         'CEILING_GRID', 'CEILING', 'NFPA 13', '8.5.5', 2, 3, 4),

        # =====================================================================
        # LIVING - larger area, per-area outlets
        # =====================================================================
        ('LIVING', 'OUTLET', 3, 4, 6, 0, 0, 0,
         'WALL_SPACED', 'WALL', 'NEC 2020', '210.52(A)', 8, 12, 20),
        ('LIVING', 'LIGHT', 1, 2, 4, 0, 0, 0,
         'CEILING_CENTER', 'CEILING', 'NEC 2020', '210.70(A)', 4, 8, 12),
        ('LIVING', 'SWITCH', 1, 2, 3, 0, 0, 0,
         'WALL_ENTRY', 'WALL', 'NEC 2020', '404.4', 3, 5, 8),
        ('LIVING', 'AIRCON_POINT', 0, 1, 2, 0, 0, 0,
         'WALL_HIGH', 'WALL', 'MS IEC 60364', '5.5', 5, 8, 12),
        ('LIVING', 'SPRINKLER', 0, 0, 2, 0.05, 0.07, 0.1,
         'CEILING_GRID', 'CEILING', 'NFPA 13', '8.5.5', 3, 5, 8),

        # =====================================================================
        # KITCHEN - high load area, dedicated circuits
        # =====================================================================
        ('KITCHEN', 'SINK', 1, 1, 2, 0, 0, 0,
         'COUNTER_BACK', 'WALL', 'IPC 2021', '405.3.1', 0, 0, 0),
        ('KITCHEN', 'FLOOR_TRAP', 1, 1, 1, 0, 0, 0,
         'FLOOR_LOW', 'FLOOR', 'MS 1228', 'Clause 5', 0, 0, 0),
        ('KITCHEN', 'OUTLET_20A', 2, 3, 4, 0, 0, 0,
         'COUNTER_BACK', 'WALL', 'NEC 2020', '210.52(B)', 6, 10, 15),
        ('KITCHEN', 'OUTLET_GFCI', 1, 2, 2, 0, 0, 0,
         'COUNTER_SINK', 'WALL', 'NEC 2020', '210.8(A)', 4, 6, 8),
        ('KITCHEN', 'LIGHT', 1, 2, 3, 0, 0, 0,
         'CEILING_CENTER', 'CEILING', 'NEC 2020', '210.70(A)', 4, 6, 10),
        ('KITCHEN', 'SWITCH', 1, 2, 2, 0, 0, 0,
         'WALL_ENTRY', 'WALL', 'NEC 2020', '404.4', 3, 4, 6),
        ('KITCHEN', 'EXHAUST_FAN', 1, 1, 1, 0, 0, 0,
         'WALL_COOKER', 'WALL', 'IMC 2021', '505.1', 3, 5, 8),
        ('KITCHEN', 'SPRINKLER', 1, 1, 2, 0, 0, 0,
         'CEILING_GRID', 'CEILING', 'NFPA 13', '8.5.5', 3, 4, 6),

        # =====================================================================
        # WET_KITCHEN - Malaysian style, more drainage
        # =====================================================================
        ('WET_KITCHEN', 'SINK', 1, 1, 2, 0, 0, 0,
         'COUNTER_BACK', 'WALL', 'IPC 2021', '405.3.1', 0, 0, 0),
        ('WET_KITCHEN', 'FLOOR_TRAP', 1, 2, 2, 0, 0, 0,
         'FLOOR_LOW', 'FLOOR', 'MS 1228', 'Clause 5', 0, 0, 0),
        ('WET_KITCHEN', 'WASHING_TAP', 0, 1, 1, 0, 0, 0,
         'WALL_LOW', 'WALL', 'IPC 2021', '405', 0, 0, 0),
        ('WET_KITCHEN', 'OUTLET_GFCI', 1, 2, 3, 0, 0, 0,
         'COUNTER_BACK', 'WALL', 'NEC 2020', '210.8(A)', 5, 8, 12),
        ('WET_KITCHEN', 'LIGHT', 1, 2, 2, 0, 0, 0,
         'CEILING_CENTER', 'CEILING', 'NEC 2020', '210.70(A)', 4, 6, 8),
        ('WET_KITCHEN', 'EXHAUST_FAN', 1, 1, 2, 0, 0, 0,
         'WALL_COOKER', 'WALL', 'IMC 2021', '505.1', 3, 5, 8),

        # =====================================================================
        # CORRIDOR - minimal, emergency lighting if long
        # =====================================================================
        ('CORRIDOR', 'LIGHT', 1, 1, 2, 0, 0, 0,
         'CEILING_CENTER', 'CEILING', 'NEC 2020', '210.70(A)', 3, 4, 6),
        ('CORRIDOR', 'SWITCH', 1, 1, 2, 0, 0, 0,
         'WALL_ENTRY', 'WALL', 'NEC 2020', '404.4', 2, 3, 5),
        ('CORRIDOR', 'SPRINKLER', 0, 0, 1, 0.03, 0.05, 0.07,
         'CEILING_GRID', 'CEILING', 'NFPA 13', '8.5.5', 2, 3, 5),

        # =====================================================================
        # STORE / UTILITY - minimal MEP
        # =====================================================================
        ('STORE', 'OUTLET', 1, 1, 2, 0, 0, 0,
         'WALL_ANY', 'WALL', 'NEC 2020', '210.52', 3, 4, 6),
        ('STORE', 'LIGHT', 1, 1, 1, 0, 0, 0,
         'CEILING_CENTER', 'CEILING', 'NEC 2020', '210.70(A)', 2, 3, 4),
        ('STORE', 'SWITCH', 1, 1, 1, 0, 0, 0,
         'WALL_ENTRY', 'WALL', 'NEC 2020', '404.4', 2, 2, 3),

        # =====================================================================
        # TOILET (standalone WC) - minimal plumbing
        # =====================================================================
        ('TOILET', 'TOILET', 1, 1, 1, 0, 0, 0,
         'WALL_BACK', 'FLOOR', 'IPC 2021', '405.3.1', 0, 0, 0),
        ('TOILET', 'SINK', 0, 1, 1, 0, 0, 0,
         'WALL_SIDE', 'WALL', 'IPC 2021', '405.3.1', 0, 0, 0),
        ('TOILET', 'EXHAUST_FAN', 1, 1, 1, 0, 0, 0,
         'CEILING_CENTER', 'CEILING', 'IMC 2021', '403.3', 2, 3, 4),
        ('TOILET', 'LIGHT', 1, 1, 1, 0, 0, 0,
         'CEILING_CENTER', 'CEILING', 'NEC 2020', '210.70(A)', 2, 3, 4),

        # =====================================================================
        # OFFICE - dense outlets
        # =====================================================================
        ('OFFICE', 'OUTLET', 0, 0, 0, 0.15, 0.2, 0.3,
         'WALL_SPACED', 'WALL', 'NEC 2020', '210.52', 8, 12, 20),
        ('OFFICE', 'LIGHT', 0, 0, 0, 0.08, 0.1, 0.15,
         'CEILING_GRID', 'CEILING', 'NEC 2020', '210.70(A)', 6, 10, 15),
        ('OFFICE', 'SWITCH', 1, 2, 3, 0, 0, 0,
         'WALL_ENTRY', 'WALL', 'NEC 2020', '404.4', 3, 5, 8),
        ('OFFICE', 'DATA_POINT', 0, 0, 0, 0.1, 0.15, 0.25,
         'WALL_SPACED', 'WALL', 'TIA-568', 'Clause 6', 5, 8, 15),
        ('OFFICE', 'AIRCON_POINT', 0, 1, 2, 0, 0, 0,
         'WALL_HIGH', 'WALL', 'MS IEC 60364', '5.5', 5, 8, 12),
        ('OFFICE', 'SPRINKLER', 0, 0, 0, 0.05, 0.07, 0.1,
         'CEILING_GRID', 'CEILING', 'NFPA 13', '8.5.5', 3, 5, 8),

        # =====================================================================
        # CLASSROOM - institutional, emergency lighting
        # =====================================================================
        ('CLASSROOM', 'OUTLET', 4, 6, 8, 0, 0, 0,
         'WALL_SPACED', 'WALL', 'NEC 2020', '210.52', 10, 15, 25),
        ('CLASSROOM', 'LIGHT', 0, 0, 0, 0.1, 0.12, 0.15,
         'CEILING_GRID', 'CEILING', 'NEC 2020', '210.70(A)', 8, 12, 18),
        ('CLASSROOM', 'SWITCH', 2, 2, 4, 0, 0, 0,
         'WALL_ENTRY', 'WALL', 'NEC 2020', '404.4', 4, 6, 10),
        ('CLASSROOM', 'AIRCON_POINT', 1, 2, 3, 0, 0, 0,
         'WALL_HIGH', 'WALL', 'MS IEC 60364', '5.5', 6, 10, 15),
        ('CLASSROOM', 'SPRINKLER', 0, 0, 0, 0.05, 0.07, 0.1,
         'CEILING_GRID', 'CEILING', 'NFPA 13', '8.5.5', 4, 6, 10),

        # =====================================================================
        # ASSEMBLY_HALL - large area, per-area everything
        # =====================================================================
        ('ASSEMBLY_HALL', 'OUTLET', 4, 6, 10, 0, 0, 0,
         'WALL_SPACED', 'WALL', 'NEC 2020', '210.52', 15, 25, 40),
        ('ASSEMBLY_HALL', 'LIGHT', 0, 0, 0, 0.08, 0.1, 0.12,
         'CEILING_GRID', 'CEILING', 'NEC 2020', '210.70(A)', 15, 25, 40),
        ('ASSEMBLY_HALL', 'SWITCH', 2, 4, 6, 0, 0, 0,
         'WALL_ENTRY', 'WALL', 'NEC 2020', '404.4', 6, 10, 15),
        ('ASSEMBLY_HALL', 'EMERGENCY_LIGHT', 2, 4, 6, 0, 0, 0,
         'WALL_EXIT', 'WALL', 'NFPA 101', '7.9', 8, 12, 20),
        ('ASSEMBLY_HALL', 'SPRINKLER', 0, 0, 0, 0.05, 0.07, 0.1,
         'CEILING_GRID', 'CEILING', 'NFPA 13', '8.5.5', 10, 15, 25),
    ]

    conn.executemany("""
        INSERT OR REPLACE INTO ad_space_type_mep_bom
        (space_type_id, mep_product_id, qty_min, qty_normal, qty_max,
         per_area_min, per_area_normal, per_area_max,
         placement_rule, host_surface, building_code, code_clause,
         conduit_min, conduit_normal, conduit_max)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, bom_data)

    conn.commit()
    print(f"Inserted {len(bom_data)} MEP BOM entries")

def print_summary(conn):
    """Print summary of what was created"""

    print("\n=== MEP BOM Summary ===\n")

    # Profiles
    print("Profiles:")
    for row in conn.execute("SELECT * FROM ad_mep_profile"):
        print(f"  {row[0]}: {row[1]}")

    # Space types with BOM
    print("\nSpace Types with MEP BOM:")
    for row in conn.execute("""
        SELECT space_type_id, COUNT(*) as products,
               SUM(conduit_normal) as conduit_budget
        FROM ad_space_type_mep_bom
        GROUP BY space_type_id
        ORDER BY products DESC
    """):
        print(f"  {row[0]}: {row[1]} products, {row[2]:.0f}m conduit budget")

    # Sample BOM
    print("\nSample BOM (BATHROOM):")
    for row in conn.execute("""
        SELECT mep_product_id, qty_min, qty_normal, qty_max, building_code
        FROM ad_space_type_mep_bom
        WHERE space_type_id = 'BATHROOM'
    """):
        print(f"  {row[0]}: min={row[1]} normal={row[2]} max={row[3]} ({row[4]})")

def main():
    print(f"Creating MEP BOM tables in {DB_PATH}")

    conn = sqlite3.connect(DB_PATH)

    create_tables(conn)
    populate_profiles(conn)
    populate_bom(conn)
    print_summary(conn)

    conn.close()
    print("\nDone!")

if __name__ == '__main__':
    main()
