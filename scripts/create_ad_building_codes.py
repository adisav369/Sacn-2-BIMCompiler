#!/usr/bin/env python3
"""
Create Building Code metadata tables.

Building codes as queryable data - no Java changes needed to add/update codes.

Tables:
- ad_building_code: Code definitions (NEC, NFPA, IPC, etc.)
- ad_code_requirement: Actual requirements per code clause
"""

import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), '..', 'library', 'component_library.db')

def create_tables(conn):
    """Create building code tables"""

    # Building code master
    conn.execute("""
        CREATE TABLE IF NOT EXISTS ad_building_code (
            code_id         TEXT PRIMARY KEY,
            code_name       TEXT NOT NULL,
            category        TEXT,              -- ELECTRICAL, PLUMBING, FIRE, MECHANICAL
            jurisdiction    TEXT,              -- USA, MALAYSIA, INTERNATIONAL
            year            INTEGER,
            is_active       INTEGER DEFAULT 1,
            supersedes      TEXT,              -- Previous version code_id
            url             TEXT               -- Reference URL
        )
    """)

    # Code requirements - the actual rules
    conn.execute("""
        CREATE TABLE IF NOT EXISTS ad_code_requirement (
            code_id         TEXT REFERENCES ad_building_code(code_id),
            clause          TEXT,              -- 210.52(A), 8.5.5
            requirement_id  TEXT,              -- Unique key for this requirement

            -- What it applies to
            element_type    TEXT,              -- OUTLET, SPRINKLER, LIGHT
            space_type      TEXT DEFAULT 'ANY',-- BEDROOM, BATHROOM, or ANY

            -- Quantity rules (mutually exclusive)
            qty_fixed       INTEGER,           -- Fixed count (1 exhaust fan)
            qty_per_area    REAL,              -- Per m² (0.1 = 1 per 10m²)
            qty_per_wall    INTEGER,           -- Per wall (1 outlet per wall)

            -- Spacing rules
            max_spacing_m   REAL,              -- Max distance between (3.6m for outlets)
            min_clearance_m REAL,              -- Min clearance from obstruction

            -- Coverage rules (sprinklers)
            coverage_area   REAL,              -- m² per device
            coverage_radius REAL,              -- meters radius

            -- Placement constraints
            max_height_m    REAL,              -- Max mounting height
            min_height_m    REAL,              -- Min mounting height
            placement_rule  TEXT,              -- WALL, CEILING, FLOOR

            -- Description
            description     TEXT,
            is_mandatory    INTEGER DEFAULT 1, -- 1=required, 0=recommended

            PRIMARY KEY (code_id, clause, element_type, space_type)
        )
    """)

    # Jurisdiction mapping - which codes apply where
    conn.execute("""
        CREATE TABLE IF NOT EXISTS ad_jurisdiction_codes (
            jurisdiction    TEXT,              -- MALAYSIA, USA, SINGAPORE
            code_id         TEXT REFERENCES ad_building_code(code_id),
            is_primary      INTEGER DEFAULT 0, -- Primary code for this jurisdiction
            adoption_date   TEXT,
            notes           TEXT,
            PRIMARY KEY (jurisdiction, code_id)
        )
    """)

    conn.commit()
    print("Tables created: ad_building_code, ad_code_requirement, ad_jurisdiction_codes")

def populate_codes(conn):
    """Insert building code definitions"""

    codes = [
        # Electrical
        ('NEC_2020', 'National Electrical Code', 'ELECTRICAL', 'USA', 2020, 1, 'NEC_2017',
         'https://www.nfpa.org/codes-and-standards/all-codes-and-standards/list-of-codes-and-standards/detail?code=70'),
        ('MS_IEC_60364', 'Malaysian Wiring Regulations', 'ELECTRICAL', 'MALAYSIA', 2014, 1, None,
         'https://www.jsm.gov.my'),

        # Plumbing
        ('IPC_2021', 'International Plumbing Code', 'PLUMBING', 'INTERNATIONAL', 2021, 1, 'IPC_2018',
         'https://codes.iccsafe.org/content/IPC2021'),
        ('MS_1228', 'Malaysian Plumbing Code', 'PLUMBING', 'MALAYSIA', 2014, 1, None,
         'https://www.jsm.gov.my'),

        # Fire Protection
        ('NFPA_13', 'Sprinkler Systems', 'FIRE', 'INTERNATIONAL', 2022, 1, 'NFPA_13_2019',
         'https://www.nfpa.org/codes-and-standards/all-codes-and-standards/list-of-codes-and-standards/detail?code=13'),
        ('NFPA_101', 'Life Safety Code', 'FIRE', 'INTERNATIONAL', 2021, 1, 'NFPA_101_2018',
         'https://www.nfpa.org/codes-and-standards/all-codes-and-standards/list-of-codes-and-standards/detail?code=101'),
        ('UBBL_FIRE', 'UBBL Fire Requirements', 'FIRE', 'MALAYSIA', 2012, 1, None,
         'https://www.bomba.gov.my'),

        # Mechanical/HVAC
        ('IMC_2021', 'International Mechanical Code', 'MECHANICAL', 'INTERNATIONAL', 2021, 1, 'IMC_2018',
         'https://codes.iccsafe.org/content/IMC2021'),
        ('ASHRAE_62_1', 'Ventilation Standard', 'MECHANICAL', 'INTERNATIONAL', 2022, 1, None,
         'https://www.ashrae.org/technical-resources/standards-and-guidelines'),

        # Accessibility
        ('ADA_2010', 'Americans with Disabilities Act', 'ACCESSIBILITY', 'USA', 2010, 1, None,
         'https://www.ada.gov/regs2010/2010ADAStandards/2010ADAstandards.htm'),
        ('MS_1184', 'Malaysian Accessibility Code', 'ACCESSIBILITY', 'MALAYSIA', 2014, 1, None,
         'https://www.jsm.gov.my'),

        # Structural
        ('MS_1195', 'Malaysian Structural Code', 'STRUCTURAL', 'MALAYSIA', 2019, 1, None,
         'https://www.jsm.gov.my'),
        ('EC2', 'Eurocode 2 (Concrete)', 'STRUCTURAL', 'INTERNATIONAL', 2004, 1, None,
         'https://eurocodes.jrc.ec.europa.eu'),

        # Data/Telecom
        ('TIA_568', 'Telecom Cabling Standard', 'TELECOM', 'INTERNATIONAL', 2017, 1, None,
         'https://tiaonline.org'),
    ]

    conn.executemany("""
        INSERT OR REPLACE INTO ad_building_code
        (code_id, code_name, category, jurisdiction, year, is_active, supersedes, url)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """, codes)

    conn.commit()
    print(f"Inserted {len(codes)} building codes")

def populate_requirements(conn):
    """Insert code requirements - the actual rules"""

    requirements = [
        # =====================================================================
        # NEC 2020 - Electrical Requirements
        # =====================================================================
        # Outlets per wall (receptacles)
        ('NEC_2020', '210.52(A)', 'NEC_OUTLET_WALL', 'OUTLET', 'BEDROOM',
         None, None, 1, 3.6, None, None, None, 0.15, 1.2, 'WALL',
         'No point along floor line >1.8m from outlet', 1),
        ('NEC_2020', '210.52(A)', 'NEC_OUTLET_WALL', 'OUTLET', 'LIVING',
         None, None, 1, 3.6, None, None, None, 0.15, 1.2, 'WALL',
         'No point along floor line >1.8m from outlet', 1),

        # Kitchen counter outlets
        ('NEC_2020', '210.52(B)', 'NEC_OUTLET_COUNTER', 'OUTLET_20A', 'KITCHEN',
         None, None, None, 1.2, None, None, None, 0.15, 0.5, 'WALL',
         'Counter outlets max 1.2m apart, within 0.6m of counter end', 1),

        # GFCI near water
        ('NEC_2020', '210.8(A)', 'NEC_GFCI_WET', 'OUTLET_GFCI', 'BATHROOM',
         1, None, None, None, 0.9, None, None, 0.15, 1.2, 'WALL',
         'GFCI required within 0.9m of sink', 1),
        ('NEC_2020', '210.8(A)', 'NEC_GFCI_WET', 'OUTLET_GFCI', 'KITCHEN',
         1, None, None, None, 0.9, None, None, 0.15, 0.5, 'WALL',
         'GFCI required within 0.9m of sink', 1),

        # Lighting - switched light required
        ('NEC_2020', '210.70(A)', 'NEC_LIGHT_ROOM', 'LIGHT', 'ANY',
         1, None, None, None, None, None, None, 2.1, 3.0, 'CEILING',
         'At least one wall-switched light per habitable room', 1),

        # Switch placement
        ('NEC_2020', '404.4', 'NEC_SWITCH', 'SWITCH', 'ANY',
         1, None, None, None, 0.15, None, None, 0.9, 1.2, 'WALL',
         'Switch at entry, 0.9-1.2m height', 1),

        # =====================================================================
        # NFPA 13 - Sprinkler Requirements
        # =====================================================================
        # Light hazard (residential, office)
        ('NFPA_13', '8.5.5', 'NFPA_SPRINK_LIGHT', 'SPRINKLER', 'BEDROOM',
         None, 0.07, None, 4.6, None, 18.6, 2.4, 2.4, 3.0, 'CEILING',
         'Light hazard: max 18.6m² per head, 4.6m max spacing', 0),
        ('NFPA_13', '8.5.5', 'NFPA_SPRINK_LIGHT', 'SPRINKLER', 'LIVING',
         None, 0.07, None, 4.6, None, 18.6, 2.4, 2.4, 3.0, 'CEILING',
         'Light hazard: max 18.6m² per head, 4.6m max spacing', 0),
        ('NFPA_13', '8.5.5', 'NFPA_SPRINK_LIGHT', 'SPRINKLER', 'OFFICE',
         None, 0.07, None, 4.6, None, 18.6, 2.4, 2.4, 3.0, 'CEILING',
         'Light hazard: max 18.6m² per head, 4.6m max spacing', 0),

        # Ordinary hazard (kitchen)
        ('NFPA_13', '8.6.2', 'NFPA_SPRINK_ORDINARY', 'SPRINKLER', 'KITCHEN',
         None, 0.1, None, 4.0, None, 12.1, 2.0, 2.4, 3.0, 'CEILING',
         'Ordinary hazard: max 12.1m² per head, 4.0m max spacing', 1),

        # Corridor sprinklers
        ('NFPA_13', '8.5.5.3', 'NFPA_SPRINK_CORRIDOR', 'SPRINKLER', 'CORRIDOR',
         None, 0.05, None, 4.6, None, 18.6, 2.4, 2.4, 3.0, 'CEILING',
         'Corridor: same as light hazard', 0),

        # =====================================================================
        # IPC 2021 - Plumbing Requirements
        # =====================================================================
        # Toilet clearances
        ('IPC_2021', '405.3.1', 'IPC_TOILET_CLEAR', 'TOILET', 'BATHROOM',
         1, None, None, None, 0.38, None, None, None, None, 'FLOOR',
         'Min 380mm side clearance, 533mm front clearance', 1),

        # Sink required
        ('IPC_2021', '405.3.1', 'IPC_SINK', 'SINK', 'BATHROOM',
         1, None, None, None, None, None, None, 0.7, 0.9, 'WALL',
         'Lavatory required in each bathroom', 1),

        # Floor drain in wet areas
        ('MS_1228', 'Clause 5', 'MS_FLOOR_TRAP', 'FLOOR_TRAP', 'BATHROOM',
         1, None, None, None, None, None, None, None, None, 'FLOOR',
         'Floor trap required in wet areas', 1),
        ('MS_1228', 'Clause 5', 'MS_FLOOR_TRAP', 'FLOOR_TRAP', 'KITCHEN',
         1, None, None, None, None, None, None, None, None, 'FLOOR',
         'Floor trap required in wet areas', 1),

        # =====================================================================
        # IMC 2021 - Mechanical Requirements
        # =====================================================================
        # Bathroom exhaust
        ('IMC_2021', '403.3', 'IMC_EXHAUST_BATH', 'EXHAUST_FAN', 'BATHROOM',
         1, None, None, None, None, None, None, 2.1, 2.7, 'CEILING',
         'Mechanical exhaust required if no window', 1),
        ('IMC_2021', '403.3', 'IMC_EXHAUST_BATH', 'EXHAUST_FAN', 'TOILET',
         1, None, None, None, None, None, None, 2.1, 2.7, 'CEILING',
         'Mechanical exhaust required if no window', 1),

        # Kitchen exhaust
        ('IMC_2021', '505.1', 'IMC_EXHAUST_KITCHEN', 'EXHAUST_FAN', 'KITCHEN',
         1, None, None, None, None, None, None, 1.8, 2.1, 'WALL',
         'Range hood or exhaust required', 1),

        # =====================================================================
        # NFPA 101 - Life Safety
        # =====================================================================
        # Emergency lighting
        ('NFPA_101', '7.9', 'NFPA_EMERG_LIGHT', 'EMERGENCY_LIGHT', 'CORRIDOR',
         None, 0.05, None, 15.0, None, None, None, 2.0, 2.4, 'WALL',
         'Emergency lighting on egress path', 1),
        ('NFPA_101', '7.9', 'NFPA_EMERG_LIGHT', 'EMERGENCY_LIGHT', 'ASSEMBLY_HALL',
         2, None, None, 30.0, None, None, None, 2.0, 2.4, 'WALL',
         'Emergency lighting at exits', 1),

        # =====================================================================
        # MS 1184 - Accessibility
        # =====================================================================
        ('MS_1184', '4.3', 'MS_DOOR_WIDTH', 'DOOR', 'ANY',
         None, None, None, None, None, None, None, None, None, None,
         'Min 900mm clear door width for accessible route', 1),
        ('MS_1184', '5.1', 'MS_TOILET_ACCESS', 'TOILET', 'BATHROOM',
         None, None, None, None, 0.9, None, None, None, None, 'FLOOR',
         'Accessible toilet: 900mm side transfer space', 0),
    ]

    conn.executemany("""
        INSERT OR REPLACE INTO ad_code_requirement
        (code_id, clause, requirement_id, element_type, space_type,
         qty_fixed, qty_per_area, qty_per_wall, max_spacing_m, min_clearance_m,
         coverage_area, coverage_radius, min_height_m, max_height_m, placement_rule,
         description, is_mandatory)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, requirements)

    conn.commit()
    print(f"Inserted {len(requirements)} code requirements")

def populate_jurisdictions(conn):
    """Map codes to jurisdictions"""

    mappings = [
        # Malaysia
        ('MALAYSIA', 'MS_IEC_60364', 1, '2014-01-01', 'Primary electrical code'),
        ('MALAYSIA', 'MS_1228', 1, '2014-01-01', 'Primary plumbing code'),
        ('MALAYSIA', 'UBBL_FIRE', 1, '2012-01-01', 'Primary fire code'),
        ('MALAYSIA', 'MS_1184', 1, '2014-01-01', 'Primary accessibility code'),
        ('MALAYSIA', 'MS_1195', 1, '2019-01-01', 'Primary structural code'),
        ('MALAYSIA', 'NFPA_13', 0, None, 'Referenced for sprinklers'),
        ('MALAYSIA', 'IPC_2021', 0, None, 'Referenced for advanced plumbing'),

        # USA
        ('USA', 'NEC_2020', 1, '2020-01-01', 'Primary electrical code'),
        ('USA', 'IPC_2021', 1, '2021-01-01', 'Primary plumbing code'),
        ('USA', 'NFPA_13', 1, '2022-01-01', 'Primary sprinkler code'),
        ('USA', 'NFPA_101', 1, '2021-01-01', 'Primary life safety code'),
        ('USA', 'IMC_2021', 1, '2021-01-01', 'Primary mechanical code'),
        ('USA', 'ADA_2010', 1, '2010-03-15', 'Primary accessibility code'),

        # International (fallback)
        ('INTERNATIONAL', 'IPC_2021', 1, None, 'Default plumbing'),
        ('INTERNATIONAL', 'NFPA_13', 1, None, 'Default fire protection'),
        ('INTERNATIONAL', 'IMC_2021', 1, None, 'Default mechanical'),
        ('INTERNATIONAL', 'EC2', 1, None, 'Default structural'),
    ]

    conn.executemany("""
        INSERT OR REPLACE INTO ad_jurisdiction_codes
        (jurisdiction, code_id, is_primary, adoption_date, notes)
        VALUES (?, ?, ?, ?, ?)
    """, mappings)

    conn.commit()
    print(f"Inserted {len(mappings)} jurisdiction mappings")

def print_summary(conn):
    """Print summary"""

    print("\n=== Building Codes Summary ===\n")

    print("Codes by category:")
    for row in conn.execute("""
        SELECT category, COUNT(*) FROM ad_building_code
        GROUP BY category ORDER BY COUNT(*) DESC
    """):
        print(f"  {row[0]}: {row[1]}")

    print("\nRequirements by code:")
    for row in conn.execute("""
        SELECT code_id, COUNT(*) FROM ad_code_requirement
        GROUP BY code_id ORDER BY COUNT(*) DESC LIMIT 10
    """):
        print(f"  {row[0]}: {row[1]} requirements")

    print("\nMalaysia primary codes:")
    for row in conn.execute("""
        SELECT c.code_id, c.code_name, c.category
        FROM ad_building_code c
        JOIN ad_jurisdiction_codes j ON c.code_id = j.code_id
        WHERE j.jurisdiction = 'MALAYSIA' AND j.is_primary = 1
    """):
        print(f"  {row[0]}: {row[1]} ({row[2]})")

    print("\nSample requirements (NEC outlets):")
    for row in conn.execute("""
        SELECT clause, element_type, space_type, qty_fixed, qty_per_wall, max_spacing_m
        FROM ad_code_requirement
        WHERE code_id = 'NEC_2020' AND element_type LIKE 'OUTLET%'
    """):
        print(f"  {row[0]}: {row[1]} in {row[2]} - fixed={row[3]} per_wall={row[4]} spacing={row[5]}m")

def main():
    print(f"Creating building code tables in {DB_PATH}")

    conn = sqlite3.connect(DB_PATH)

    create_tables(conn)
    populate_codes(conn)
    populate_requirements(conn)
    populate_jurisdictions(conn)
    print_summary(conn)

    conn.close()
    print("\nDone!")

if __name__ == '__main__':
    main()
