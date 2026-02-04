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

            -- Calculation formula (all parameters stored, no hardcoding in Java)
            calc_rule TEXT,                 -- PER_AREA, PER_LUX, PER_CFM, PER_OCCUPANT, PER_LINEAR, FIXED
            calc_base REAL,                 -- Divisor: area/base, cfm/base, occupancy/base
            calc_param TEXT,                -- Extra param: target_lux for PER_LUX
            calc_occupancy_density REAL,    -- m²/person when occupancy not provided (PER_OCCUPANT)
            calc_cfm_density REAL,          -- CFM/m² when CFM not provided (PER_CFM)
            calc_formula TEXT,              -- Human-readable formula for audit

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
    """Insert BOM rules extracted from codes and standards.

    Tuple format:
    (space_type, element_type, element_subtype, bom_type, component_name,
     calc_rule, calc_base, calc_param, calc_occupancy_density, calc_cfm_density, calc_formula,
     min_qty, max_qty, priority, code_id, clause, notes)
    """

    rules = [
        # =====================================================================
        # SPRINKLERS - NFPA 13 / UBBL 2012
        # Formula: qty = ceil(area / coverage_m2)
        # =====================================================================
        ("CLASSROOM", "SPRINKLER", "pendant", "VARIABLE", "pendent",
         "PER_AREA", 18.6, None, None, None, "ceil(area_m2 / 18.6)",
         1, None, 10, "NFPA_13", "8.5.2.1", "Light hazard occupancy"),

        ("OFFICE", "SPRINKLER", "pendant", "VARIABLE", "pendent",
         "PER_AREA", 18.6, None, None, None, "ceil(area_m2 / 18.6)",
         1, None, 10, "NFPA_13", "8.5.2.1", "Light hazard occupancy"),

        ("CORRIDOR", "SPRINKLER", "pendant", "VARIABLE", "pendent",
         "PER_AREA", 18.6, None, None, None, "ceil(area_m2 / 18.6)",
         1, None, 10, "NFPA_13", "8.5.2.1", "Light hazard - corridors"),

        ("CANTEEN", "SPRINKLER", "pendant", "VARIABLE", "pendent",
         "PER_AREA", 12.1, None, None, None, "ceil(area_m2 / 12.1)",
         1, None, 10, "NFPA_13", "8.6.2.1", "Ordinary hazard Group 1"),

        ("KITCHEN", "SPRINKLER", "pendant", "VARIABLE", "pendent",
         "PER_AREA", 12.1, None, None, None, "ceil(area_m2 / 12.1)",
         1, None, 10, "NFPA_13", "8.6.2.1", "Ordinary hazard Group 1"),

        # =====================================================================
        # LIGHTS - MS 1525 / IES Standards
        # Formula: qty = ceil(area × target_lux / lumens_per_fixture)
        # =====================================================================
        ("CLASSROOM", "LIGHT", "ceiling_light", "VARIABLE", "Downlight",
         "PER_LUX", 3000, "300", None, None, "ceil(area_m2 × 300 lux / 3000 lm)",
         2, None, 20, "MS_1525", "Table 4.1", "Educational - classroom 300 lux"),

        ("OFFICE", "LIGHT", "ceiling_light", "VARIABLE", "Downlight",
         "PER_LUX", 3000, "400", None, None, "ceil(area_m2 × 400 lux / 3000 lm)",
         2, None, 20, "MS_1525", "Table 4.1", "Office - general 400 lux"),

        ("CORRIDOR", "LIGHT", "ceiling_light", "VARIABLE", "Downlight",
         "PER_LUX", 3000, "100", None, None, "ceil(area_m2 × 100 lux / 3000 lm)",
         1, None, 30, "MS_1525", "Table 4.1", "Circulation - corridor 100 lux"),

        ("CANTEEN", "LIGHT", "ceiling_light", "VARIABLE", "Downlight",
         "PER_LUX", 3000, "200", None, None, "ceil(area_m2 × 200 lux / 3000 lm)",
         2, None, 20, "MS_1525", "Table 4.1", "Canteen/cafeteria 200 lux"),

        ("BATHROOM", "LIGHT", "ceiling_light", "VARIABLE", "Downlight",
         "PER_LUX", 2000, "150", None, None, "ceil(area_m2 × 150 lux / 2000 lm)",
         1, 4, 25, "MS_1525", "Table 4.1", "Sanitary - bathroom 150 lux"),

        # =====================================================================
        # DIFFUSERS - ASHRAE 62.1
        # Formula: qty = ceil(cfm / cfm_per_diffuser)
        # cfm_density = CFM per m² when CFM not provided
        # =====================================================================
        ("CLASSROOM", "DIFFUSER", "supply", "VARIABLE", "Supply Diffuser",
         "PER_CFM", 600, None, None, 1.3, "ceil((area_m2 × 1.3 CFM/m²) / 600)",
         1, None, 15, "ASHRAE_62_1", "Table 6.2.2.1", "Educational ventilation"),

        ("OFFICE", "DIFFUSER", "supply", "VARIABLE", "Supply Diffuser",
         "PER_CFM", 600, None, None, 0.65, "ceil((area_m2 × 0.65 CFM/m²) / 600)",
         1, None, 15, "ASHRAE_62_1", "Table 6.2.2.1", "Office ventilation"),

        ("CANTEEN", "DIFFUSER", "supply", "VARIABLE", "Supply Diffuser",
         "PER_CFM", 600, None, None, 1.9, "ceil((area_m2 × 1.9 CFM/m²) / 600)",
         2, None, 15, "ASHRAE_62_1", "Table 6.2.2.1", "Food service ventilation"),

        ("CLASSROOM", "DIFFUSER", "return", "VARIABLE", "Exhaust Diffuser",
         "PER_CFM", 750, None, None, 1.0, "ceil((area_m2 × 1.0 CFM/m²) / 750)",
         1, None, 16, "ASHRAE_62_1", "6.2.3", "Return air - 80% of supply"),

        # =====================================================================
        # FURNITURE - Variable by occupancy/area
        # occupancy_density = m²/person when occupancy not provided
        # =====================================================================
        ("CANTEEN", "FURNITURE", "canteen_table", "VARIABLE", "Canteen Table",
         "PER_OCCUPANT", 4, None, 2.5, None, "ceil((area_m2 / 2.5 m²/person) / 4 seats)",
         2, 25, 50, "IBC", "1004.5", "Cafeteria seating 2.5m²/person, 4 per table"),

        ("LOBBY", "FURNITURE", "lobby_seating", "VARIABLE", "Waiting_Room_Seat",
         "PER_AREA", 12.0, None, None, None, "ceil(area_m2 / 12.0)",
         1, 8, 50, None, None, "One 4-seat bench per 12m² waiting area"),

        ("WAITING", "FURNITURE", "lobby_seating", "VARIABLE", "Waiting_Room_Seat",
         "PER_AREA", 12.0, None, None, None, "ceil(area_m2 / 12.0)",
         1, 8, 50, None, None, "One 4-seat bench per 12m² waiting area"),

        ("OFFICE", "FURNITURE", "workstation", "VARIABLE", "Desk_with_return",
         "PER_AREA", 18.0, None, None, None, "ceil(area_m2 / 18.0)",
         1, 10, 50, None, None, "L-desk (2.66×1.97m) + clearance = 18m²/workstation"),

        ("STAFFROOM", "FURNITURE", "workstation", "VARIABLE", "Desk_with_return",
         "PER_AREA", 18.0, None, None, None, "ceil(area_m2 / 18.0)",
         1, 10, 50, None, None, "Shared office 18m²/workstation"),

        ("CLASSROOM", "FURNITURE", "student_desk", "VARIABLE", "Desk",
         "PER_OCCUPANT", 1, None, 1.85, None, "ceil((area_m2 / 1.85 m²/student) / 1)",
         10, 40, 50, "UBBL", "Table 3", "Educational 1.85m²/student, 1 desk each"),

        # =====================================================================
        # FIXTURES - Mandatory by room type
        # =====================================================================
        ("BATHROOM", "FIXTURE", "toilet", "MANDATORY", "Toilet",
         "FIXED", 1, None, None, None, "1",
         1, 2, 5, "IPC", "403.1", "Minimum 1 toilet per bathroom"),

        ("BATHROOM", "FIXTURE", "sink", "MANDATORY", "sink",
         "FIXED", 1, None, None, None, "1",
         1, 2, 5, "IPC", "403.1", "Minimum 1 lavatory per bathroom"),

        # Bathroom: exhaust fan mandatory
        ("BATHROOM", "FIXTURE", "exhaust_fan", "MANDATORY", "Exhaust",
         "FIXED", 1, None, None, None, "1",
         1, 1, 5, "IMC", "401.2", "Mechanical exhaust for windowless bathrooms"),

        # Kitchen: sink mandatory
        ("KITCHEN", "FIXTURE", "sink", "MANDATORY", "sink",
         "FIXED", 1, None, None, None, "1",
         1, 2, 5, "IPC", "403.1", "Minimum 1 sink per kitchen"),
    ]

    conn.executemany("""
        INSERT INTO ad_bom_rule (
            space_type, element_type, element_subtype, bom_type, component_name,
            calc_rule, calc_base, calc_param, calc_occupancy_density, calc_cfm_density, calc_formula,
            min_qty, max_qty, priority, code_id, clause, notes
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
