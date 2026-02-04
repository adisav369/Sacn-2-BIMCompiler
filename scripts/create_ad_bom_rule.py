#!/usr/bin/env python3
"""
Phase 60: Create ad_bom_rule table for variable BOM resolution.

BOM Types:
- MANDATORY: Always required (qty=1 unless overridden)
- OPTIONAL: User-specified in DSL
- VARIABLE: Calculated from room properties

Calculation Rules:
- PER_AREA: qty = ceil(room_area / base_value)
- PER_LUX: qty = ceil(room_area * target_lux / lumens_per_fixture)
- PER_CFM: qty = ceil(required_cfm / cfm_per_unit)
- PER_OCCUPANT: qty = ceil(occupancy / seats_per_unit)
- PER_LINEAR: qty = ceil(perimeter / spacing)
- FIXED: qty = base_value (constant)
"""

import sqlite3
import os

DB_PATH = "database/authority_data.db"

def create_table(conn):
    """Create ad_bom_rule table."""
    conn.execute("DROP TABLE IF EXISTS ad_bom_rule")
    conn.execute("""
        CREATE TABLE ad_bom_rule (
            rule_id INTEGER PRIMARY KEY,
            space_type TEXT NOT NULL,       -- CANTEEN, OFFICE, BATHROOM, CLASSROOM, etc.
            element_type TEXT NOT NULL,     -- SPRINKLER, LIGHT, DIFFUSER, FURNITURE, FIXTURE
            element_subtype TEXT,           -- Specific: pendant_sprinkler, canteen_table, etc.
            bom_type TEXT NOT NULL,         -- MANDATORY, OPTIONAL, VARIABLE
            component_name TEXT,            -- Library component name pattern
            calc_rule TEXT,                 -- PER_AREA, PER_LUX, PER_CFM, PER_OCCUPANT, PER_LINEAR, FIXED
            calc_base REAL,                 -- Divisor or multiplier for calculation
            calc_param TEXT,                -- Additional parameter (e.g., target_lux for PER_LUX)
            min_qty INTEGER DEFAULT 0,      -- Minimum quantity
            max_qty INTEGER,                -- Maximum quantity (NULL = unlimited)
            priority INTEGER DEFAULT 100,   -- Lower = higher priority for conflicts
            code_id TEXT,                   -- UBBL_2012, IBC_2021, NFPA_13, etc.
            clause TEXT,                    -- Code clause reference
            notes TEXT
        )
    """)
    print("Created ad_bom_rule table")

def insert_rules(conn):
    """Insert BOM rules extracted from codes and standards."""

    rules = [
        # =====================================================================
        # SPRINKLERS - NFPA 13 / UBBL 2012
        # =====================================================================
        # Light hazard: 18.6m² per sprinkler head
        ("CLASSROOM", "SPRINKLER", "pendant", "VARIABLE", "pendent",
         "PER_AREA", 18.6, None, 1, None, 10, "NFPA_13", "8.5.2.1",
         "Light hazard occupancy coverage"),

        ("OFFICE", "SPRINKLER", "pendant", "VARIABLE", "pendent",
         "PER_AREA", 18.6, None, 1, None, 10, "NFPA_13", "8.5.2.1",
         "Light hazard occupancy coverage"),

        ("CORRIDOR", "SPRINKLER", "pendant", "VARIABLE", "pendent",
         "PER_AREA", 18.6, None, 1, None, 10, "NFPA_13", "8.5.2.1",
         "Light hazard - corridors"),

        # Ordinary hazard (canteen, kitchen): 12.1m² per head
        ("CANTEEN", "SPRINKLER", "pendant", "VARIABLE", "pendent",
         "PER_AREA", 12.1, None, 1, None, 10, "NFPA_13", "8.6.2.1",
         "Ordinary hazard Group 1"),

        ("KITCHEN", "SPRINKLER", "pendant", "VARIABLE", "pendent",
         "PER_AREA", 12.1, None, 1, None, 10, "NFPA_13", "8.6.2.1",
         "Ordinary hazard Group 1"),

        # =====================================================================
        # LIGHTS - MS 1525 / IES Standards
        # =====================================================================
        # Classroom: 300 lux, assume 3000 lumens per fixture
        ("CLASSROOM", "LIGHT", "ceiling_light", "VARIABLE", "Downlight",
         "PER_LUX", 3000, "300", 2, None, 20, "MS_1525", "Table 4.1",
         "Educational - classroom 300 lux"),

        # Office: 300-500 lux
        ("OFFICE", "LIGHT", "ceiling_light", "VARIABLE", "Downlight",
         "PER_LUX", 3000, "400", 2, None, 20, "MS_1525", "Table 4.1",
         "Office - general 400 lux"),

        # Corridor: 100 lux
        ("CORRIDOR", "LIGHT", "ceiling_light", "VARIABLE", "Downlight",
         "PER_LUX", 3000, "100", 1, None, 30, "MS_1525", "Table 4.1",
         "Circulation - corridor 100 lux"),

        # Canteen: 200 lux
        ("CANTEEN", "LIGHT", "ceiling_light", "VARIABLE", "Downlight",
         "PER_LUX", 3000, "200", 2, None, 20, "MS_1525", "Table 4.1",
         "Canteen/cafeteria 200 lux"),

        # Bathroom: 150 lux
        ("BATHROOM", "LIGHT", "ceiling_light", "VARIABLE", "Downlight",
         "PER_LUX", 2000, "150", 1, 4, 25, "MS_1525", "Table 4.1",
         "Sanitary - bathroom 150 lux"),

        # =====================================================================
        # DIFFUSERS - ASHRAE 62.1
        # =====================================================================
        # Supply diffuser: 600 CFM per 600x600 diffuser
        ("CLASSROOM", "DIFFUSER", "supply", "VARIABLE", "Supply Diffuser",
         "PER_CFM", 600, None, 1, None, 15, "ASHRAE_62_1", "Table 6.2.2.1",
         "Educational - 0.12 CFM/ft² + 10 CFM/person"),

        ("OFFICE", "DIFFUSER", "supply", "VARIABLE", "Supply Diffuser",
         "PER_CFM", 600, None, 1, None, 15, "ASHRAE_62_1", "Table 6.2.2.1",
         "Office - 0.06 CFM/ft² + 5 CFM/person"),

        ("CANTEEN", "DIFFUSER", "supply", "VARIABLE", "Supply Diffuser",
         "PER_CFM", 600, None, 2, None, 15, "ASHRAE_62_1", "Table 6.2.2.1",
         "Food service - 0.18 CFM/ft² + 7.5 CFM/person"),

        # Return diffuser: typically 80% of supply
        ("CLASSROOM", "DIFFUSER", "return", "VARIABLE", "Exhaust Diffuser",
         "PER_CFM", 750, None, 1, None, 16, "ASHRAE_62_1", "6.2.3",
         "Return air - 80% of supply"),

        # =====================================================================
        # FURNITURE - Variable by occupancy/area
        # =====================================================================
        # Canteen table: 4 seats per table, 1.5m² per seat
        ("CANTEEN", "FURNITURE", "canteen_table", "VARIABLE", "Canteen Table",
         "PER_OCCUPANT", 4, None, 2, 25, 50, None, None,
         "4 seats per table, occupancy from area/1.5m²"),

        # Lobby seating: 4-seat bench per 12m² waiting area
        ("LOBBY", "FURNITURE", "lobby_seating", "VARIABLE", "Waiting_Room_Seat",
         "PER_AREA", 12.0, None, 1, 8, 50, None, None,
         "One 4-seat bench per 12m² waiting area"),

        ("WAITING", "FURNITURE", "lobby_seating", "VARIABLE", "Waiting_Room_Seat",
         "PER_AREA", 12.0, None, 1, 8, 50, None, None,
         "One 4-seat bench per 12m² waiting area"),

        # Office desk: 1 per 6m² (open plan) or 1 per room (private)
        ("OFFICE", "FURNITURE", "workstation", "VARIABLE", "Desk_with_return",
         "PER_AREA", 9.0, None, 1, 20, 50, None, None,
         "One workstation per 9m² (open plan density)"),

        # Classroom desk: calculated differently (student desks)
        ("CLASSROOM", "FURNITURE", "student_desk", "VARIABLE", "Desk",
         "PER_OCCUPANT", 1, None, 10, 40, 50, None, None,
         "One desk per student, occupancy from class size"),

        # =====================================================================
        # FIXTURES - Mandatory by room type
        # =====================================================================
        # Bathroom: toilet mandatory
        ("BATHROOM", "FIXTURE", "toilet", "MANDATORY", "Toilet",
         "FIXED", 1, None, 1, 2, 5, "IPC", "403.1",
         "Minimum 1 toilet per bathroom"),

        # Bathroom: sink mandatory
        ("BATHROOM", "FIXTURE", "sink", "MANDATORY", "sink",
         "FIXED", 1, None, 1, 2, 5, "IPC", "403.1",
         "Minimum 1 lavatory per bathroom"),

        # Bathroom: exhaust fan mandatory
        ("BATHROOM", "FIXTURE", "exhaust_fan", "MANDATORY", "Exhaust",
         "FIXED", 1, None, 1, 1, 5, "IMC", "401.2",
         "Mechanical exhaust required for windowless bathrooms"),

        # Kitchen: sink mandatory
        ("KITCHEN", "FIXTURE", "sink", "MANDATORY", "sink",
         "FIXED", 1, None, 1, 2, 5, "IPC", "403.1",
         "Minimum 1 sink per kitchen"),
    ]

    conn.executemany("""
        INSERT INTO ad_bom_rule (
            space_type, element_type, element_subtype, bom_type, component_name,
            calc_rule, calc_base, calc_param, min_qty, max_qty, priority,
            code_id, clause, notes
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, rules)

    print(f"Inserted {len(rules)} BOM rules")

def print_summary(conn):
    """Print summary of inserted rules."""
    print("\n=== BOM Rules Summary ===")

    cursor = conn.execute("""
        SELECT bom_type, element_type, COUNT(*) as cnt
        FROM ad_bom_rule
        GROUP BY bom_type, element_type
        ORDER BY bom_type, element_type
    """)

    for row in cursor:
        print(f"  {row[0]:10} {row[1]:12} {row[2]:3} rules")

    print("\n=== Sample Rules ===")
    cursor = conn.execute("""
        SELECT space_type, element_type, bom_type, calc_rule, calc_base, code_id
        FROM ad_bom_rule
        ORDER BY element_type, space_type
        LIMIT 10
    """)

    print(f"  {'Space':<12} {'Element':<12} {'Type':<10} {'Rule':<12} {'Base':>8} {'Code':<10}")
    print("  " + "-" * 70)
    for row in cursor:
        base = f"{row[4]:.1f}" if row[4] else "-"
        code = row[5] or "-"
        print(f"  {row[0]:<12} {row[1]:<12} {row[2]:<10} {row[3]:<12} {base:>8} {code:<10}")

def main():
    os.makedirs("database", exist_ok=True)

    conn = sqlite3.connect(DB_PATH)
    try:
        create_table(conn)
        insert_rules(conn)
        conn.commit()
        print_summary(conn)
        print(f"\nDatabase: {DB_PATH}")
    finally:
        conn.close()

if __name__ == "__main__":
    main()
