#!/usr/bin/env python3
"""
Create AD tables for BOM-driven opening families and space-type defaults.

Following the AD (Application Dictionary) pattern:
- Opening families define door/window types with default dimensions
- Space-type opening defaults map room types to their standard openings
- DSL explicit declarations override BOM defaults

Tables:
- ad_opening_family: Door/window family definitions
- ad_space_type_opening: Default openings per space type
"""

import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), '../library/BOM.db')

SCHEMA = """
-- Opening family definitions (door/window types)
CREATE TABLE IF NOT EXISTS ad_opening_family (
    family_id         TEXT PRIMARY KEY,
    family_name       TEXT NOT NULL,
    opening_type      TEXT NOT NULL,       -- DOOR or WINDOW
    ifc_class         TEXT NOT NULL,       -- IfcDoor or IfcWindow
    default_width_mm  INTEGER NOT NULL,
    default_height_mm INTEGER NOT NULL,
    is_fire_rated     INTEGER DEFAULT 0,
    description       TEXT,
    is_active         INTEGER DEFAULT 1
);

-- Space-type opening defaults (which openings each room type gets)
CREATE TABLE IF NOT EXISTS ad_space_type_opening (
    space_type_id      TEXT NOT NULL,
    opening_role       TEXT NOT NULL,       -- ENTRY, EGRESS, NATURAL_LIGHT, VENTILATION
    family_id          TEXT NOT NULL,
    qty_rule           TEXT DEFAULT 'FIXED', -- FIXED, PER_EXTERIOR_WALL
    qty_base           INTEGER DEFAULT 1,
    override_width_mm  INTEGER,
    override_height_mm INTEGER,
    sill_height_mm     INTEGER DEFAULT 0,
    placement_wall     TEXT,                -- NULL=auto, 'exterior', 'interior'
    placement_rule     TEXT DEFAULT 'CENTER',
    is_fire_rated      INTEGER DEFAULT 0,
    building_code      TEXT,
    code_clause        TEXT,
    priority           INTEGER DEFAULT 100,
    is_active          INTEGER DEFAULT 1,
    PRIMARY KEY (space_type_id, opening_role, family_id),
    FOREIGN KEY (family_id) REFERENCES ad_opening_family(family_id)
);
"""

# Opening family definitions
# Widths matched to library component_definitions
OPENING_FAMILIES = [
    # (family_id, family_name, opening_type, ifc_class, width_mm, height_mm, fire_rated, description)
    ('RES_ENTRY',        'Residential Entry',      'DOOR',   'IfcDoor',   900,  2100, 1, 'Residential entry (fire rated)'),
    ('RES_INTERNAL',     'Internal Room Door',     'DOOR',   'IfcDoor',   750,  2100, 0, 'Internal room door'),
    ('FIRE_DOOR',        'Fire Door',              'DOOR',   'IfcDoor',  1050,  2175, 1, 'Fire rated self-closing'),
    ('FIRE_DOOR_WIDE',   'Wide Fire Door',         'DOOR',   'IfcDoor',  1150,  2175, 1, 'Wide fire door (stair/lobby)'),
    ('DOUBLE_LEAF',      'Double Leaf Fire Door',  'DOOR',   'IfcDoor',  1500,  2100, 1, 'Double leaf fire rated'),
    ('BATHROOM_DOOR',    'Bathroom Door',          'DOOR',   'IfcDoor',   750,  2100, 0, 'Bathroom/WC door'),
    ('STD_WINDOW',       'Standard Window',        'WINDOW', 'IfcWindow',  833, 1200, 0, 'Standard window'),
    ('LARGE_WINDOW',     'Large Window',           'WINDOW', 'IfcWindow', 1250, 1200, 0, 'Large window (living)'),
    ('SMALL_WINDOW',     'Small/High Window',      'WINDOW', 'IfcWindow',  610, 1830, 0, 'Small/high window (bathroom)'),
    ('CLASSROOM_WINDOW', 'Classroom Window',       'WINDOW', 'IfcWindow', 2562, 3449, 0, 'Full-height casement'),
]

# Space-type opening defaults
# (space_type, role, family_id, qty_rule, qty_base, override_w, override_h, sill_mm, wall, placement, fire, code, clause, priority)
SPACE_TYPE_OPENINGS = [
    # BEDROOM
    ('BEDROOM',        'ENTRY',         'RES_INTERNAL',  'FIXED',             1, None, None,    0, 'interior', 'CENTER', 0, None, None, 100),
    ('BEDROOM',        'NATURAL_LIGHT', 'STD_WINDOW',    'PER_EXTERIOR_WALL', 1, None, None,  900, 'exterior', 'CENTER', 0, None, None, 100),

    # BATHROOM
    ('BATHROOM',       'ENTRY',         'BATHROOM_DOOR', 'FIXED',             1, None, None,    0, 'interior', 'CENTER', 0, None, None, 100),
    ('BATHROOM',       'VENTILATION',   'SMALL_WINDOW',  'PER_EXTERIOR_WALL', 1, None, None, 1400, 'exterior', 'CENTER', 0, None, None, 100),

    # KITCHEN
    ('KITCHEN',        'ENTRY',         'RES_INTERNAL',  'FIXED',             1, None, None,    0, 'interior', 'CENTER', 0, None, None, 100),
    ('KITCHEN',        'NATURAL_LIGHT', 'STD_WINDOW',    'PER_EXTERIOR_WALL', 1, None, None,  900, 'exterior', 'CENTER', 0, None, None, 100),

    # LIVING
    ('LIVING',         'ENTRY',         'RES_INTERNAL',  'FIXED',             1, None, None,    0, 'interior', 'CENTER', 0, None, None, 100),
    ('LIVING',         'NATURAL_LIGHT', 'LARGE_WINDOW',  'PER_EXTERIOR_WALL', 1, None, None,  900, 'exterior', 'CENTER', 0, None, None, 100),

    # MASTER_BEDROOM
    ('MASTER_BEDROOM', 'ENTRY',         'RES_INTERNAL',  'FIXED',             1, None, None,    0, 'interior', 'CENTER', 0, None, None, 100),
    ('MASTER_BEDROOM', 'NATURAL_LIGHT', 'LARGE_WINDOW',  'PER_EXTERIOR_WALL', 1, None, None,  900, 'exterior', 'CENTER', 0, None, None, 100),

    # OFFICE
    ('OFFICE',         'ENTRY',         'RES_INTERNAL',  'FIXED',             1, None, None,    0, 'interior', 'CENTER', 0, None, None, 100),
    ('OFFICE',         'NATURAL_LIGHT', 'STD_WINDOW',    'PER_EXTERIOR_WALL', 1, None, None,  900, 'exterior', 'CENTER', 0, None, None, 100),

    # CLASSROOM
    ('CLASSROOM',      'ENTRY',         'FIRE_DOOR',     'FIXED',             1, None, None,    0, 'interior', 'CENTER', 1, None, None, 100),
    ('CLASSROOM',      'NATURAL_LIGHT', 'CLASSROOM_WINDOW', 'PER_EXTERIOR_WALL', 1, None, None, 900, 'exterior', 'CENTER', 0, None, None, 100),

    # STORAGE
    ('STORAGE',        'ENTRY',         'BATHROOM_DOOR', 'FIXED',             1, None, None,    0, 'interior', 'CENTER', 0, None, None, 100),

    # DINING
    ('DINING',         'ENTRY',         'RES_INTERNAL',  'FIXED',             1, None, None,    0, 'interior', 'CENTER', 0, None, None, 100),
    ('DINING',         'NATURAL_LIGHT', 'STD_WINDOW',    'PER_EXTERIOR_WALL', 1, None, None,  900, 'exterior', 'CENTER', 0, None, None, 100),

    # STUDY
    ('STUDY',          'ENTRY',         'RES_INTERNAL',  'FIXED',             1, None, None,    0, 'interior', 'CENTER', 0, None, None, 100),
    ('STUDY',          'NATURAL_LIGHT', 'STD_WINDOW',    'PER_EXTERIOR_WALL', 1, None, None,  900, 'exterior', 'CENTER', 0, None, None, 100),

    # LAUNDRY
    ('LAUNDRY',        'ENTRY',         'BATHROOM_DOOR', 'FIXED',             1, None, None,    0, 'interior', 'CENTER', 0, None, None, 100),
    ('LAUNDRY',        'VENTILATION',   'SMALL_WINDOW',  'PER_EXTERIOR_WALL', 1, None, None, 1400, 'exterior', 'CENTER', 0, None, None, 100),
]


def main():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # Create schema
    cursor.executescript(SCHEMA)

    # Insert opening families
    cursor.execute("DELETE FROM ad_opening_family")
    for row in OPENING_FAMILIES:
        cursor.execute("""
            INSERT INTO ad_opening_family
            (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, is_fire_rated, description)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, row)

    # Insert space-type opening defaults
    cursor.execute("DELETE FROM ad_space_type_opening")
    for row in SPACE_TYPE_OPENINGS:
        cursor.execute("""
            INSERT INTO ad_space_type_opening
            (space_type_id, opening_role, family_id, qty_rule, qty_base,
             override_width_mm, override_height_mm, sill_height_mm,
             placement_wall, placement_rule, is_fire_rated,
             building_code, code_clause, priority)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, row)

    conn.commit()

    # Print summary
    cursor.execute("SELECT COUNT(*) FROM ad_opening_family")
    family_count = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM ad_space_type_opening")
    opening_count = cursor.fetchone()[0]

    print(f"Created AD opening tables:")
    print(f"  Opening families: {family_count}")
    print(f"  Space-type defaults: {opening_count}")

    # Show families
    print("\nOpening Families:")
    cursor.execute("""
        SELECT family_id, opening_type, default_width_mm, default_height_mm, is_fire_rated, description
        FROM ad_opening_family ORDER BY opening_type, family_id
    """)
    for row in cursor.fetchall():
        fire = " [FIRE]" if row[4] else ""
        print(f"  {row[0]}: {row[1]} {row[2]}×{row[3]}mm{fire} — {row[5]}")

    # Show defaults
    print("\nSpace-Type Opening Defaults:")
    cursor.execute("""
        SELECT s.space_type_id, s.opening_role, s.family_id, s.qty_rule, s.qty_base, s.sill_height_mm
        FROM ad_space_type_opening s
        ORDER BY s.space_type_id, s.opening_role
    """)
    current_type = None
    for row in cursor.fetchall():
        if row[0] != current_type:
            current_type = row[0]
            print(f"\n  {current_type}:")
        print(f"    {row[1]}: {row[2]} ({row[3]} ×{row[4]}) sill={row[5]}mm")

    conn.close()


if __name__ == '__main__':
    main()
