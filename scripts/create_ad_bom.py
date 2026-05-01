# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
Create AD BOM tables following iDempiere M_BOM / M_BOM_Product pattern.

Structure:
- m_bom: BOM header definitions (like M_BOM)
- m_bom_line: BOM children/components (like M_BOM_Product)

A child can point to:
- An IFC class (leaf element) via child_ifc_class
- Another BOM (nested assembly) via child_bom_id

This allows:
  FLOOR_STRUCTURAL
  ├── IfcSlab (leaf)
  ├── IfcBeam (leaf)
  └── STAIR_COMPLETE (nested BOM)
        ├── IfcStairFlight (leaf)
        ├── IfcRailing (leaf)
        └── LANDING_ASSEMBLY (nested BOM)
              └── IfcSlab[LANDING] (leaf)
"""

import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), '../library/BOM.db')

SCHEMA = """
-- Drop old tables if they exist
DROP TABLE IF EXISTS m_bom_grouping_rule;
DROP TABLE IF EXISTS m_bom_assembly_type;

-- BOM header definitions (like iDempiere M_BOM)
CREATE TABLE IF NOT EXISTS m_bom (
    bom_id TEXT PRIMARY KEY,
    bom_name TEXT NOT NULL,
    description TEXT,
    target_ifc_class TEXT DEFAULT 'IfcElementAssembly',
    group_by TEXT NOT NULL,          -- STOREY, ELEMENT_NAME, PROXIMITY, ROOM
    is_active INTEGER DEFAULT 1
);

-- BOM children/components (like iDempiere M_BOM_Product)
CREATE TABLE IF NOT EXISTS m_bom_line (
    bom_child_id INTEGER PRIMARY KEY AUTOINCREMENT,
    bom_id TEXT NOT NULL,            -- Parent BOM

    -- Child reference (one of these is set)
    child_ifc_class TEXT,            -- Leaf element IFC class (e.g., IfcBeam)
    child_element_type TEXT,         -- Optional filter on element_type
    child_name_pattern TEXT,         -- Optional LIKE pattern on element_name
    child_bom_id TEXT,               -- Nested BOM reference (NULL for leaf)

    -- Role and ordering
    role TEXT NOT NULL,              -- BEAM, COLUMN, FLIGHT, RAILING, etc.
    qty_type TEXT DEFAULT 'VARIABLE', -- FIXED, VARIABLE, PER_AREA
    sequence INTEGER DEFAULT 100,

    is_active INTEGER DEFAULT 1,
    FOREIGN KEY (bom_id) REFERENCES m_bom(bom_id),
    FOREIGN KEY (child_bom_id) REFERENCES m_bom(bom_id)
);

-- Index for performance
CREATE INDEX IF NOT EXISTS idx_bom_child_parent ON m_bom_line(bom_id);
CREATE INDEX IF NOT EXISTS idx_bom_child_nested ON m_bom_line(child_bom_id);
"""

# BOM definitions
BOMS = [
    # (bom_id, bom_name, description, target_ifc_class, group_by)
    ('FLOOR_STRUCTURAL', 'Floor Structural Package',
     'Structural elements grouped by storey', 'IfcElementAssembly', 'STOREY'),

    ('STAIR_COMPLETE', 'Complete Stair Assembly',
     'Stair with flight, landings, and railings', 'IfcStair', 'ELEMENT_NAME'),

    ('WALL_PANEL', 'Wall Panel Assembly',
     'Wall with frame, cladding, and openings', 'IfcElementAssembly', 'ELEMENT_NAME'),

    ('MEP_ROOM', 'Room MEP Package',
     'MEP elements grouped by room', 'IfcSystem', 'ROOM'),

    ('DOOR_ASSEMBLY', 'Door Assembly',
     'Door with frame and hardware', 'IfcDoor', 'ELEMENT_NAME'),
]

# BOM children (bom_id, child_ifc_class, child_element_type, child_name_pattern, child_bom_id, role, sequence)
BOM_CHILDREN = [
    # FLOOR_STRUCTURAL children (leafs)
    ('FLOOR_STRUCTURAL', 'IfcSlab', None, None, None, 'SLAB', 10),
    ('FLOOR_STRUCTURAL', 'IfcBeam', None, None, None, 'BEAM', 20),
    ('FLOOR_STRUCTURAL', 'IfcColumn', None, None, None, 'COLUMN', 30),

    # STAIR_COMPLETE children
    ('STAIR_COMPLETE', 'IfcStairFlight', None, None, None, 'FLIGHT', 10),
    ('STAIR_COMPLETE', 'IfcSlab', 'LANDING', None, None, 'LANDING', 20),
    ('STAIR_COMPLETE', 'IfcRailing', None, None, None, 'RAILING', 30),

    # WALL_PANEL children
    ('WALL_PANEL', 'IfcMember', 'FRAME', None, None, 'FRAME', 10),
    ('WALL_PANEL', 'IfcPlate', 'CLADDING', None, None, 'CLADDING', 20),
    # Nested: door assembly within wall
    ('WALL_PANEL', None, None, None, 'DOOR_ASSEMBLY', 'OPENING', 30),
    ('WALL_PANEL', 'IfcWindow', None, None, None, 'OPENING', 31),

    # DOOR_ASSEMBLY children (nested BOM example)
    ('DOOR_ASSEMBLY', 'IfcDoor', None, None, None, 'LEAF', 10),
    ('DOOR_ASSEMBLY', 'IfcMember', 'FRAME', '%DOOR_FRAME%', None, 'FRAME', 20),
    ('DOOR_ASSEMBLY', 'IfcDiscreteAccessory', 'HARDWARE', None, None, 'HARDWARE', 30),

    # MEP_ROOM children
    ('MEP_ROOM', 'IfcLightFixture', None, None, None, 'LIGHT', 10),
    ('MEP_ROOM', 'IfcFireSuppressionTerminal', None, None, None, 'SPRINKLER', 20),
    ('MEP_ROOM', 'IfcAirTerminal', None, None, None, 'DIFFUSER', 30),
    ('MEP_ROOM', 'IfcOutlet', None, None, None, 'OUTLET', 40),
    ('MEP_ROOM', 'IfcSwitchingDevice', None, None, None, 'SWITCH', 50),
]


def main():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # Create schema
    cursor.executescript(SCHEMA)

    # Insert BOMs
    cursor.execute("DELETE FROM m_bom")
    for row in BOMS:
        cursor.execute("""
            INSERT INTO m_bom (bom_id, bom_name, description, target_ifc_class, group_by)
            VALUES (?, ?, ?, ?, ?)
        """, row)

    # Insert BOM children
    cursor.execute("DELETE FROM m_bom_line")
    for row in BOM_CHILDREN:
        cursor.execute("""
            INSERT INTO m_bom_line
            (bom_id, child_ifc_class, child_element_type, child_name_pattern, child_bom_id, role, sequence)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, row)

    conn.commit()

    # Print summary
    cursor.execute("SELECT COUNT(*) FROM m_bom")
    bom_count = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM m_bom_line")
    child_count = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM m_bom_line WHERE child_bom_id IS NOT NULL")
    nested_count = cursor.fetchone()[0]

    print(f"Created AD BOM tables (iDempiere M_BOM pattern):")
    print(f"  BOMs defined:     {bom_count}")
    print(f"  BOM children:     {child_count}")
    print(f"  Nested BOMs:      {nested_count}")

    # Show BOM structure
    print("\nBOM Structure:")
    cursor.execute("SELECT bom_id, bom_name, group_by FROM m_bom ORDER BY bom_id")
    for bom in cursor.fetchall():
        print(f"\n  {bom[0]} ({bom[2]})")
        cursor.execute("""
            SELECT child_ifc_class, child_element_type, child_bom_id, role
            FROM m_bom_line
            WHERE bom_id = ?
            ORDER BY sequence
        """, (bom[0],))
        for child in cursor.fetchall():
            if child[2]:  # Nested BOM
                print(f"    └─[BOM] {child[2]} → {child[3]}")
            else:  # Leaf element
                type_filter = f"[{child[1]}]" if child[1] else ""
                print(f"    └─ {child[0]}{type_filter} → {child[3]}")

    conn.close()


if __name__ == '__main__':
    main()
