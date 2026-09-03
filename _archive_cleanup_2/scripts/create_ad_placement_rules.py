# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
Create placement rule metadata - EXTRACTED from existing Java code.

Source files:
- ElectricalPlacer.java: OUTLET_HEIGHT, SWITCH_HEIGHT, WALL_OFFSET
- FixturePlacer.java: TOILET_SIDE_CLEARANCE, SINK_FRONT_CLEARANCE
- StructuralPlacer.java: COLUMN_SIZE, MAX_BEAM_SPAN

EXTRACT, DON'T INVENT.
"""

import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), '..', 'library', 'BOM.db')

def create_tables(conn):
    """Create placement rule table"""

    conn.execute("""
        CREATE TABLE IF NOT EXISTS ad_placement_rule (
            rule_id         TEXT PRIMARY KEY,
            description     TEXT,

            -- Surface type
            surface         TEXT,               -- CEILING, WALL, FLOOR

            -- Position parameters (EXTRACTED from Java)
            height_m        REAL,               -- Mounting height above floor
            wall_offset_m   REAL,               -- Distance from wall face
            edge_offset_m   REAL,               -- Distance from room edges

            -- Spacing (for grid/distributed patterns)
            max_spacing_m   REAL,               -- Max distance between items

            -- Clearances (EXTRACTED from Java)
            clearance_front_m REAL,             -- Front clearance
            clearance_side_m  REAL,             -- Side clearance

            -- Source (provenance)
            source_file     TEXT,               -- Java file extracted from
            source_line     TEXT,               -- Line number or constant name
            building_code   TEXT                -- Code reference
        )
    """)

    conn.commit()
    print("Table created: ad_placement_rule")

def populate_rules(conn):
    """Insert placement rules EXTRACTED from existing Java code"""

    # EXTRACTED from ElectricalPlacer.java, FixturePlacer.java, StructuralPlacer.java
    rules = [
        # =====================================================================
        # From ElectricalPlacer.java lines 41-48
        # =====================================================================
        ('OUTLET_WALL', 'Outlet on wall, 300mm above floor',
         'WALL', 0.3, 0.05, 0.3, 3.6, None, None,
         'ElectricalPlacer.java', 'OUTLET_HEIGHT=0.3, WALL_OFFSET=0.05', 'NEC 210.52'),

        ('SWITCH_ENTRY', 'Switch near door, 1200mm above floor',
         'WALL', 1.2, 0.05, 0.15, None, None, None,
         'ElectricalPlacer.java', 'SWITCH_HEIGHT=1.2, WALL_OFFSET=0.05', 'NEC 404.4'),

        ('LIGHT_CEILING', 'Light on ceiling, centered or grid',
         'CEILING', None, None, 0.3, None, None, None,
         'ElectricalPlacer.java', 'avoidColumnZones, edge_offset=0.3', 'NEC 210.70'),

        ('LIGHT_CEILING_GRID', 'Multiple lights in grid pattern',
         'CEILING', None, None, 0.35, 4.6, None, None,
         'ElectricalPlacer.java', 'COLUMN_CLEARANCE=0.35', 'NFPA 13'),

        # =====================================================================
        # From FixturePlacer.java lines 33-43
        # =====================================================================
        ('TOILET_BACK_WALL', 'Toilet against back wall',
         'FLOOR', None, 0.05, None, None, 0.533, 0.38,
         'FixturePlacer.java', 'TOILET_SIDE_CLEARANCE=0.38, TOILET_FRONT_CLEARANCE=0.533', 'IPC 405.3.1'),

        ('SINK_SIDE_WALL', 'Sink against side wall',
         'WALL', 0.85, 0.05, None, None, 0.533, None,
         'FixturePlacer.java', 'SINK_FRONT_CLEARANCE=0.533, WALL_OFFSET=0.05', 'IPC 405'),

        # =====================================================================
        # From StructuralPlacer.java lines 35-56
        # =====================================================================
        ('LINTEL_ABOVE_OPENING', 'Lintel above door/window opening',
         'WALL', None, None, 0.15, None, None, None,
         'StructuralPlacer.java', 'LINTEL_BEARING=0.15, LINTEL_DEPTH=0.2', 'BS 5628'),

        ('COLUMN_CORNER', 'Column at room corner',
         'FLOOR', None, None, 0.15, None, None, None,
         'StructuralPlacer.java', 'COLUMN_SIZE=0.3 (half=0.15)', 'MS 1195'),

        ('BEAM_SPAN', 'Beam spanning between columns',
         'CEILING', None, None, None, 8.0, None, None,
         'StructuralPlacer.java', 'MAX_BEAM_SPAN_MASONRY=8.0', 'MS 1195'),

        # =====================================================================
        # From AutoFitter.java placeMEPProduct() method
        # =====================================================================
        ('CEILING_CENTER', 'Center of ceiling',
         'CEILING', None, None, 0.3, None, None, None,
         'AutoFitter.java', 'placeMEPProduct CEILING_CENTER case', None),

        ('CEILING_GRID', 'Distributed grid on ceiling',
         'CEILING', None, None, 0.6, 4.6, None, None,
         'AutoFitter.java', 'placeMEPProduct CEILING_GRID case', 'NFPA 13'),

        ('WALL_SPACED', 'Distributed along walls',
         'WALL', 0.3, 0.05, None, 3.6, None, None,
         'AutoFitter.java', 'placeMEPProduct WALL_SPACED case', 'NEC 210.52'),

        ('WALL_HIGH', 'High on wall (aircon)',
         'WALL', 2.2, 0.05, 0.3, None, None, None,
         'AutoFitter.java', 'placeMEPProduct WALL_HIGH case', 'MS IEC 60364'),

        ('WALL_SINK', 'Near sink (GFCI outlet)',
         'WALL', 1.0, 0.05, None, None, None, 0.9,
         'AutoFitter.java', 'placeMEPProduct WALL_SINK case', 'NEC 210.8'),

        ('FLOOR_LOW', 'Floor drain at low point',
         'FLOOR', None, None, 0.3, None, None, None,
         'AutoFitter.java', 'placeMEPProduct FLOOR_LOW case', 'MS 1228'),
    ]

    conn.executemany("""
        INSERT OR REPLACE INTO ad_placement_rule
        (rule_id, description, surface, height_m, wall_offset_m, edge_offset_m,
         max_spacing_m, clearance_front_m, clearance_side_m,
         source_file, source_line, building_code)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, rules)

    conn.commit()
    print(f"Inserted {len(rules)} placement rules (EXTRACTED from Java)")

def print_summary(conn):
    """Print summary with provenance"""

    print("\n=== Placement Rules (EXTRACTED) ===\n")

    print("By source file:")
    for row in conn.execute("""
        SELECT source_file, COUNT(*), GROUP_CONCAT(rule_id, ', ')
        FROM ad_placement_rule
        GROUP BY source_file
    """):
        print(f"  {row[0]}: {row[1]} rules")
        print(f"    → {row[2]}")

    print("\nKey values extracted:")
    for row in conn.execute("""
        SELECT rule_id, height_m, wall_offset_m, clearance_front_m, clearance_side_m, source_line
        FROM ad_placement_rule
        WHERE height_m IS NOT NULL OR clearance_front_m IS NOT NULL
    """):
        print(f"  {row[0]}:")
        if row[1]: print(f"    height={row[1]}m")
        if row[2]: print(f"    wall_offset={row[2]}m")
        if row[3]: print(f"    clearance_front={row[3]}m")
        if row[4]: print(f"    clearance_side={row[4]}m")
        print(f"    source: {row[5]}")

def main():
    print(f"Creating placement rules (EXTRACTED) in {DB_PATH}")

    conn = sqlite3.connect(DB_PATH)

    create_tables(conn)
    populate_rules(conn)
    print_summary(conn)

    conn.close()
    print("\nDone!")

if __name__ == '__main__':
    main()
