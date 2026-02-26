#!/usr/bin/env python3
"""
Add dimension columns to AD tables for auto-fitting (LEGO pattern).

Each element/unit/space carries its dimensions so autochooser can:
1. Know what fits where
2. Calculate quantities automatically
3. Check clearance requirements

Author: BIM Compiler Project
Date: 2026-02-04
"""

import sqlite3
import os
import sys

DB_PATH = "library/BOM.db"


def add_dimension_columns(conn: sqlite3.Connection) -> None:
    """Add dimension columns to existing AD tables."""
    cursor = conn.cursor()

    # Add dimensions to ad_unit_type (if not exists)
    try:
        cursor.execute("ALTER TABLE ad_unit_type ADD COLUMN width REAL")
        cursor.execute("ALTER TABLE ad_unit_type ADD COLUMN depth REAL")
        cursor.execute("ALTER TABLE ad_unit_type ADD COLUMN min_width REAL")
        cursor.execute("ALTER TABLE ad_unit_type ADD COLUMN min_depth REAL")
        print("[AD Dims] Added dimension columns to ad_unit_type")
    except sqlite3.OperationalError:
        print("[AD Dims] ad_unit_type already has dimension columns")

    # Add dimensions to ad_floor_type
    try:
        cursor.execute("ALTER TABLE ad_floor_type ADD COLUMN min_area REAL")
        cursor.execute("ALTER TABLE ad_floor_type ADD COLUMN max_area REAL")
        print("[AD Dims] Added area columns to ad_floor_type")
    except sqlite3.OperationalError:
        print("[AD Dims] ad_floor_type already has area columns")

    # Add dimensions to ad_element_mep
    try:
        cursor.execute("ALTER TABLE ad_element_mep ADD COLUMN width REAL")
        cursor.execute("ALTER TABLE ad_element_mep ADD COLUMN depth REAL")
        cursor.execute("ALTER TABLE ad_element_mep ADD COLUMN height REAL")
        print("[AD Dims] Added dimension columns to ad_element_mep")
    except sqlite3.OperationalError:
        print("[AD Dims] ad_element_mep already has dimension columns")

    conn.commit()


def create_product_dimensions_table(conn: sqlite3.Connection) -> None:
    """Create unified product dimensions table."""
    cursor = conn.cursor()

    # Master product dimensions (like M_Product attributes)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_product_dim (
        product_id        TEXT PRIMARY KEY,    -- 'DOOR_D1', 'UNIT_2BR_A', 'FIXTURE_TOILET'
        product_type      TEXT NOT NULL,       -- DOOR, WINDOW, UNIT, FIXTURE, FURNITURE

        -- Bounding box (LEGO brick size)
        width             REAL NOT NULL,       -- X dimension (meters)
        depth             REAL NOT NULL,       -- Y dimension (meters)
        height            REAL NOT NULL,       -- Z dimension (meters)

        -- Minimum clearances (LEGO connection rules)
        clear_front       REAL DEFAULT 0,      -- Required clearance in front
        clear_back        REAL DEFAULT 0,
        clear_left        REAL DEFAULT 0,
        clear_right       REAL DEFAULT 0,
        clear_above       REAL DEFAULT 0,
        clear_below       REAL DEFAULT 0,

        -- Fitting rules
        fits_in           TEXT,                -- JSON: what spaces this fits in ["BEDROOM","BATHROOM"]
        requires_host     TEXT,                -- Host type required: WALL, CEILING, FLOOR, null
        host_min_width    REAL,                -- Minimum host dimension
        host_min_height   REAL,

        -- Quantity rules (for auto-calculation)
        qty_per_area      REAL,                -- Quantity per m² (e.g., 1 light per 10m²)
        qty_per_room      INTEGER,             -- Quantity per room (e.g., 1 toilet per bathroom)
        qty_per_person    REAL,                -- Quantity per occupant
        max_spacing       REAL,                -- Maximum spacing between (e.g., outlets every 3.6m)

        -- Connection points (LEGO studs)
        conn_points       TEXT,                -- JSON: [{"face":"BACK","type":"PLUMB"},...]

        code_ref          TEXT,
        is_active         INTEGER DEFAULT 1
    )
    """)

    # Room/space dimensions
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_space_dim (
        space_type        TEXT PRIMARY KEY,    -- 'BEDROOM', 'BATHROOM', 'KITCHEN'

        -- Size constraints
        min_width         REAL,
        min_depth         REAL,
        min_area          REAL,
        min_height        REAL DEFAULT 2.4,

        -- Preferred proportions
        preferred_ratio   REAL DEFAULT 1.5,    -- width:depth ratio
        max_ratio         REAL DEFAULT 2.5,

        -- What fits inside (auto-populate)
        required_products TEXT,                -- JSON: ["TOILET","SINK"]
        optional_products TEXT,                -- JSON: ["SHOWER","TUB"]

        -- Layout rules
        door_on_wall      TEXT DEFAULT 'ANY',  -- LONGEST, SHORTEST, ANY
        window_on_wall    TEXT DEFAULT 'EXTERIOR',

        code_ref          TEXT,
        is_active         INTEGER DEFAULT 1
    )
    """)

    conn.commit()
    print("[AD Dims] Created ad_product_dim, ad_space_dim tables")


def populate_dimensions(conn: sqlite3.Connection) -> None:
    """Populate dimension data."""
    cursor = conn.cursor()

    # Product dimensions (LEGO bricks)
    products = [
        # Doors
        ('DOOR_D1', 'DOOR', 0.9, 0.045, 2.1, 0.9, 0, 0, 0, 0, 0, '["BEDROOM","BATHROOM","KITCHEN"]', 'WALL', 0.9, 2.1, None, None, None, None, None, 'MS 1184'),
        ('DOOR_D2', 'DOOR', 0.8, 0.045, 2.1, 0.8, 0, 0, 0, 0, 0, '["BATHROOM","STORE"]', 'WALL', 0.8, 2.1, None, None, None, None, None, 'MS 1184'),
        ('DOOR_D3', 'DOOR', 0.7, 0.045, 2.0, 0.7, 0, 0, 0, 0, 0, '["BATHROOM"]', 'WALL', 0.7, 2.0, None, None, None, None, None, 'MS 1184'),

        # Windows
        ('WINDOW_W1', 'WINDOW', 1.2, 0.1, 1.2, 0, 0, 0, 0, 0, 0, '["BEDROOM","LIVING","KITCHEN"]', 'WALL', 1.2, 1.2, None, None, None, None, None, None),
        ('WINDOW_W2', 'WINDOW', 0.6, 0.1, 0.6, 0, 0, 0, 0, 0, 0, '["BATHROOM","KITCHEN"]', 'WALL', 0.6, 0.6, None, None, None, None, None, None),

        # Fixtures (plumbing)
        ('FIXTURE_TOILET', 'FIXTURE', 0.4, 0.7, 0.4, 0.533, 0, 0.381, 0.381, 0, 0, '["BATHROOM"]', 'FLOOR', None, None, None, 1, None, None, '[{"face":"BACK","type":"WASTE"},{"face":"LEFT","type":"SUPPLY"}]', 'IPC'),
        ('FIXTURE_SINK', 'FIXTURE', 0.5, 0.45, 0.2, 0.5, 0, 0.3, 0.3, 0, 0, '["BATHROOM","KITCHEN"]', 'WALL', 0.5, None, None, 1, None, None, '[{"face":"BACK","type":"WASTE"},{"face":"BACK","type":"SUPPLY"}]', 'IPC'),
        ('FIXTURE_SHOWER', 'FIXTURE', 0.9, 0.9, 2.1, 0.6, 0.6, 0, 0, 0, 0, '["BATHROOM"]', 'FLOOR', None, None, None, 1, None, None, '[{"face":"WALL","type":"SUPPLY"},{"face":"FLOOR","type":"WASTE"}]', 'IPC'),

        # Electrical
        ('ELEC_OUTLET', 'ELECTRICAL', 0.085, 0.04, 0.085, 0, 0, 0, 0, 0, 0, '["BEDROOM","LIVING","KITCHEN","BATHROOM"]', 'WALL', None, None, None, None, None, 3.6, '[{"face":"BACK","type":"ELEC"}]', 'MS IEC 60364'),
        ('ELEC_SWITCH', 'ELECTRICAL', 0.085, 0.04, 0.085, 0, 0, 0, 0, 0, 0, '["BEDROOM","LIVING","KITCHEN","BATHROOM"]', 'WALL', None, None, None, 1, None, None, '[{"face":"BACK","type":"ELEC"}]', 'MS IEC 60364'),
        ('ELEC_LIGHT', 'ELECTRICAL', 0.3, 0.3, 0.1, 0, 0, 0, 0, 0, 0, '["BEDROOM","LIVING","KITCHEN","BATHROOM"]', 'CEILING', None, None, 0.1, None, None, None, '[{"face":"TOP","type":"ELEC"}]', 'MS IEC 60364'),

        # Fire protection
        ('FP_SPRINKLER', 'FP', 0.1, 0.1, 0.15, 0, 0, 0, 0, 0.3, 0, '["BEDROOM","LIVING","KITCHEN","BATHROOM","CORRIDOR"]', 'CEILING', None, None, 0.054, None, None, 4.6, '[{"face":"TOP","type":"FP"}]', 'NFPA 13'),
        ('FP_SMOKE', 'FP', 0.12, 0.12, 0.05, 0, 0, 0, 0, 0, 0, '["BEDROOM","LIVING","CORRIDOR"]', 'CEILING', None, None, 0.012, None, None, 9.1, '[{"face":"TOP","type":"ELEC"}]', 'NFPA 72'),

        # Furniture (for reference)
        ('FURN_BED_SINGLE', 'FURNITURE', 0.9, 1.9, 0.5, 0.6, 0, 0.6, 0.6, 0, 0, '["BEDROOM"]', None, None, None, None, 1, None, None, None, None),
        ('FURN_BED_DOUBLE', 'FURNITURE', 1.5, 2.0, 0.5, 0.6, 0, 0.6, 0.6, 0, 0, '["BEDROOM"]', None, None, None, None, 1, None, None, None, None),
        ('FURN_WARDROBE', 'FURNITURE', 1.2, 0.6, 2.1, 0.6, 0, 0, 0, 0, 0, '["BEDROOM"]', None, None, None, None, 1, None, None, None, None),
    ]
    cursor.executemany("""
        INSERT OR REPLACE INTO ad_product_dim
        (product_id, product_type, width, depth, height, clear_front, clear_back, clear_left, clear_right, clear_above, clear_below,
         fits_in, requires_host, host_min_width, host_min_height, qty_per_area, qty_per_room, qty_per_person, max_spacing, conn_points, code_ref)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
    """, products)

    # Space dimensions (LEGO baseplates)
    spaces = [
        ('BEDROOM', 2.7, 2.7, 9.0, 2.7, 1.3, 2.0, '["DOOR_D1","WINDOW_W1","ELEC_OUTLET","ELEC_SWITCH","ELEC_LIGHT"]', '["FURN_BED_SINGLE","FURN_WARDROBE"]', 'SHORTEST', 'EXTERIOR', 'UBBL'),
        ('MASTER_BEDROOM', 3.0, 3.5, 12.0, 2.7, 1.2, 2.0, '["DOOR_D1","WINDOW_W1","ELEC_OUTLET","ELEC_SWITCH","ELEC_LIGHT"]', '["FURN_BED_DOUBLE","FURN_WARDROBE"]', 'SHORTEST', 'EXTERIOR', 'UBBL'),
        ('BATHROOM', 1.5, 1.8, 3.5, 2.4, 1.2, 2.0, '["DOOR_D2","FIXTURE_TOILET","FIXTURE_SINK","ELEC_LIGHT"]', '["FIXTURE_SHOWER","WINDOW_W2"]', 'SHORTEST', 'ANY', 'IPC'),
        ('KITCHEN', 2.4, 2.4, 5.5, 2.7, 1.0, 2.5, '["DOOR_D1","FIXTURE_SINK","ELEC_OUTLET","ELEC_LIGHT"]', '["WINDOW_W1"]', 'ANY', 'EXTERIOR', 'UBBL'),
        ('LIVING', 3.5, 4.0, 15.0, 2.7, 1.2, 2.0, '["DOOR_D1","WINDOW_W1","ELEC_OUTLET","ELEC_SWITCH","ELEC_LIGHT"]', '[]', 'ANY', 'EXTERIOR', 'UBBL'),
        ('CORRIDOR', 1.2, 3.0, 4.0, 2.4, 4.0, 10.0, '["ELEC_LIGHT","FP_SPRINKLER"]', '[]', 'SHORT_END', 'NONE', 'UBBL'),
    ]
    cursor.executemany("""
        INSERT OR REPLACE INTO ad_space_dim
        (space_type, min_width, min_depth, min_area, min_height, preferred_ratio, max_ratio,
         required_products, optional_products, door_on_wall, window_on_wall, code_ref)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
    """, spaces)

    conn.commit()
    print(f"[AD Dims] Populated: {len(products)} products, {len(spaces)} spaces")


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    db_path = os.path.join(project_root, DB_PATH)

    if not os.path.exists(db_path):
        print(f"[ERROR] Database not found: {db_path}")
        sys.exit(1)

    print(f"[AD Dims] Opening: {db_path}")
    conn = sqlite3.connect(db_path)
    add_dimension_columns(conn)
    create_product_dimensions_table(conn)
    populate_dimensions(conn)
    conn.close()
    print("[AD Dims] Done")


if __name__ == "__main__":
    main()
