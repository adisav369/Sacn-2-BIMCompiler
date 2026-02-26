#!/usr/bin/env python3
"""
Create AD tables for BOM assembly grouping rules.

Following the AD (Application Dictionary) pattern:
- Rules defined in database, not Java code
- Change grouping behavior by updating AD tables
- No code changes needed for new assembly types

Tables:
- ad_bom_assembly_type: Defines assembly type templates
- ad_bom_grouping_rule: Defines which elements belong to which assemblies
"""

import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), '../library/BOM.db')

SCHEMA = """
-- Assembly type definitions
CREATE TABLE IF NOT EXISTS ad_bom_assembly_type (
    assembly_type TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    ifc_class TEXT DEFAULT 'IfcElementAssembly',
    description TEXT,
    group_by TEXT NOT NULL,  -- STOREY, ELEMENT_NAME, PROXIMITY
    is_active INTEGER DEFAULT 1
);

-- Grouping rules: which elements belong to which assembly types
CREATE TABLE IF NOT EXISTS ad_bom_grouping_rule (
    rule_id INTEGER PRIMARY KEY AUTOINCREMENT,
    assembly_type TEXT NOT NULL,
    element_ifc_class TEXT NOT NULL,  -- IfcBeam, IfcColumn, etc.
    element_type TEXT,                 -- Optional filter (e.g., LANDING, FRAME)
    element_name_pattern TEXT,         -- Optional LIKE pattern for element_name
    role TEXT NOT NULL,                -- Role in assembly: BEAM, COLUMN, FLIGHT, etc.
    sequence INTEGER DEFAULT 100,      -- Order in assembly (lower = first)
    is_active INTEGER DEFAULT 1,
    FOREIGN KEY (assembly_type) REFERENCES ad_bom_assembly_type(assembly_type)
);
"""

# Assembly type definitions
ASSEMBLY_TYPES = [
    # (assembly_type, name, ifc_class, description, group_by)
    ('FLOOR_STRUCTURAL', 'Floor Structural Package', 'IfcElementAssembly',
     'Beams, columns, and slabs grouped by storey', 'STOREY'),

    ('STAIR_COMPLETE', 'Complete Stair Assembly', 'IfcStair',
     'Stair flight with landings and railings', 'ELEMENT_NAME'),

    ('WALL_PANEL', 'Wall Panel Assembly', 'IfcElementAssembly',
     'Wall frame, cladding, and openings', 'ELEMENT_NAME'),

    ('MEP_ZONE', 'MEP Zone Package', 'IfcSystem',
     'Lights, sprinklers, diffusers grouped by room', 'STOREY'),
]

# Grouping rules
GROUPING_RULES = [
    # (assembly_type, ifc_class, element_type, name_pattern, role, sequence)

    # Floor Structural
    ('FLOOR_STRUCTURAL', 'IfcSlab', None, None, 'SLAB', 10),
    ('FLOOR_STRUCTURAL', 'IfcBeam', None, None, 'BEAM', 20),
    ('FLOOR_STRUCTURAL', 'IfcColumn', None, None, 'COLUMN', 30),

    # Stair Complete
    ('STAIR_COMPLETE', 'IfcStairFlight', None, None, 'FLIGHT', 10),
    ('STAIR_COMPLETE', 'IfcSlab', 'LANDING', None, 'LANDING', 20),
    ('STAIR_COMPLETE', 'IfcRailing', None, None, 'RAILING', 30),

    # Wall Panel (already handled by existing code, but documented here)
    ('WALL_PANEL', 'IfcMember', 'FRAME', None, 'FRAME', 10),
    ('WALL_PANEL', 'IfcPlate', 'CLADDING', None, 'CLADDING', 20),
    ('WALL_PANEL', 'IfcDoor', None, None, 'OPENING', 30),
    ('WALL_PANEL', 'IfcWindow', None, None, 'OPENING', 31),

    # MEP Zone
    ('MEP_ZONE', 'IfcLightFixture', None, None, 'LIGHT', 10),
    ('MEP_ZONE', 'IfcFireSuppressionTerminal', None, None, 'SPRINKLER', 20),
    ('MEP_ZONE', 'IfcAirTerminal', None, None, 'DIFFUSER', 30),
    ('MEP_ZONE', 'IfcOutlet', None, None, 'OUTLET', 40),
]


def main():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # Create schema
    cursor.executescript(SCHEMA)

    # Insert assembly types
    cursor.execute("DELETE FROM ad_bom_assembly_type")
    for row in ASSEMBLY_TYPES:
        cursor.execute("""
            INSERT INTO ad_bom_assembly_type
            (assembly_type, name, ifc_class, description, group_by)
            VALUES (?, ?, ?, ?, ?)
        """, row)

    # Insert grouping rules
    cursor.execute("DELETE FROM ad_bom_grouping_rule")
    for row in GROUPING_RULES:
        cursor.execute("""
            INSERT INTO ad_bom_grouping_rule
            (assembly_type, element_ifc_class, element_type, element_name_pattern, role, sequence)
            VALUES (?, ?, ?, ?, ?, ?)
        """, row)

    conn.commit()

    # Print summary
    cursor.execute("SELECT COUNT(*) FROM ad_bom_assembly_type")
    type_count = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM ad_bom_grouping_rule")
    rule_count = cursor.fetchone()[0]

    print(f"Created AD BOM grouping tables:")
    print(f"  Assembly types: {type_count}")
    print(f"  Grouping rules: {rule_count}")

    # Show rules
    print("\nGrouping Rules:")
    cursor.execute("""
        SELECT t.assembly_type, t.group_by, r.element_ifc_class, r.role
        FROM ad_bom_assembly_type t
        JOIN ad_bom_grouping_rule r ON t.assembly_type = r.assembly_type
        ORDER BY t.assembly_type, r.sequence
    """)
    current_type = None
    for row in cursor.fetchall():
        if row[0] != current_type:
            current_type = row[0]
            print(f"\n  {current_type} (group_by: {row[1]})")
        print(f"    - {row[2]} → {row[3]}")

    conn.close()


if __name__ == '__main__':
    main()
