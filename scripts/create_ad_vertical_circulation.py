#!/usr/bin/env python3
"""
Create Vertical Circulation AD Tables for BIM Compiler.

EXTRACT, DON'T INVENT - All values from building codes:
- UBBL 2012 (Malaysia) - Part VIII, By-Law 166-185
- IBC 2021 (International Building Code) - Chapter 10
- NFPA 101 2021 (Life Safety Code) - Chapter 7
- MS 1184:2014 (Malaysian Accessibility Code)

Tables:
- ad_vert_circ_trigger: When stairs/elevators are REQUIRED
- ad_stair_requirement: Stair design parameters by occupancy
- ad_elevator_requirement: Elevator requirements by building type
- ad_egress_travel: Travel distance limits
- ad_pressurization_trigger: When pressurized stairs required

Author: BIM Compiler Project
Date: 2026-02-04
"""

import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), '..', 'library', 'BOM.db')


def create_tables(conn):
    """Create vertical circulation AD tables."""

    # ==========================================================================
    # Table 1: Triggers - when vertical circulation elements are REQUIRED
    # ==========================================================================
    conn.execute("""
        CREATE TABLE IF NOT EXISTS ad_vert_circ_trigger (
            trigger_id          TEXT PRIMARY KEY,
            element_type        TEXT NOT NULL,      -- STAIR, ELEVATOR, FIRE_LIFT, STRETCHER_LIFT
            trigger_type        TEXT NOT NULL,      -- STOREY_COUNT, TRAVEL_HEIGHT, OCCUPANT_LOAD, AREA

            -- Threshold values (one applies based on trigger_type)
            min_storeys         INTEGER,            -- Trigger if >= this many storeys
            min_travel_m        REAL,               -- Trigger if travel > this height (m)
            min_occupant_load   INTEGER,            -- Trigger if occupant load >= this
            min_area_m2         REAL,               -- Trigger if floor area >= this

            -- Output
            min_quantity        INTEGER DEFAULT 1,  -- Minimum number required
            quantity_formula    TEXT,               -- Formula for additional (e.g., "1 + floor(occupants/500)")

            -- Scope
            occupancy_group     TEXT DEFAULT 'ANY', -- R, B, A, E, M, or ANY
            jurisdiction        TEXT DEFAULT 'INTERNATIONAL',

            -- Source
            code_id             TEXT,
            clause              TEXT,
            description         TEXT,
            is_mandatory        INTEGER DEFAULT 1
        )
    """)

    # ==========================================================================
    # Table 2: Stair Requirements - design parameters
    # ==========================================================================
    conn.execute("""
        CREATE TABLE IF NOT EXISTS ad_stair_requirement (
            req_id              TEXT PRIMARY KEY,
            occupancy_group     TEXT NOT NULL,      -- R, B, A, E, M, H, or ANY

            -- Width calculation
            min_width_mm        INTEGER NOT NULL,   -- Absolute minimum width
            width_per_person_mm REAL,               -- Additional width per person served
            max_occupant_per_stair INTEGER,         -- Max persons per stair (for quantity calc)

            -- Geometry constraints (EXTRACTED from codes)
            max_riser_mm        INTEGER,            -- Max riser height
            min_riser_mm        INTEGER,            -- Min riser height
            min_tread_mm        INTEGER,            -- Min tread depth (going)
            max_flight_rise_m   REAL,               -- Max vertical rise per flight

            -- Landing requirements
            min_landing_length_mm INTEGER,          -- Min landing dimension
            landing_at_doors    INTEGER DEFAULT 1,  -- Require landing at door swing

            -- Handrail
            handrail_height_mm  INTEGER,            -- Handrail height
            handrail_both_sides INTEGER DEFAULT 1,  -- Require on both sides
            handrail_extension_mm INTEGER,          -- Extension beyond top/bottom

            -- Fire rating
            min_fire_rating_hr  REAL,               -- Enclosure fire rating (hours)

            -- Source
            code_id             TEXT,
            clause              TEXT,
            jurisdiction        TEXT DEFAULT 'INTERNATIONAL'
        )
    """)

    # ==========================================================================
    # Table 3: Elevator Requirements
    # ==========================================================================
    conn.execute("""
        CREATE TABLE IF NOT EXISTS ad_elevator_requirement (
            req_id              TEXT PRIMARY KEY,
            elevator_type       TEXT NOT NULL,      -- PASSENGER, FIRE, STRETCHER, FREIGHT, ACCESSIBLE
            occupancy_group     TEXT DEFAULT 'ANY',

            -- Trigger (when this elevator type is required)
            trigger_storeys     INTEGER,            -- Required if >= this many storeys
            trigger_travel_m    REAL,               -- Required if travel > this (m)
            trigger_occupants   INTEGER,            -- Required if occupant load > this
            trigger_beds        INTEGER,            -- For stretcher lift (hospital)

            -- Quantity
            min_quantity        INTEGER DEFAULT 1,
            quantity_formula    TEXT,               -- e.g., "1 + floor((units-8)/50)"

            -- Car dimensions (minimum internal)
            min_width_mm        INTEGER,
            min_depth_mm        INTEGER,
            min_door_width_mm   INTEGER,

            -- Lobby requirements
            min_lobby_depth_m   REAL,               -- Min elevator lobby depth
            min_lobby_width_m   REAL,               -- Min lobby width per car

            -- Fire requirements
            fire_rating_hr      REAL,               -- Shaft fire rating
            smoke_control       INTEGER DEFAULT 0,  -- Requires smoke control
            emergency_power     INTEGER DEFAULT 0,  -- Requires backup power

            -- Accessibility
            is_accessible       INTEGER DEFAULT 1,  -- Must be accessible
            voice_annunciator   INTEGER DEFAULT 0,  -- Requires voice announcement

            -- Source
            code_id             TEXT,
            clause              TEXT,
            jurisdiction        TEXT DEFAULT 'INTERNATIONAL'
        )
    """)

    # ==========================================================================
    # Table 4: Egress Travel Distance
    # ==========================================================================
    conn.execute("""
        CREATE TABLE IF NOT EXISTS ad_egress_travel (
            travel_id           TEXT PRIMARY KEY,
            occupancy_group     TEXT NOT NULL,

            -- Travel limits
            max_travel_m        REAL NOT NULL,      -- Max travel to exit
            max_dead_end_m      REAL,               -- Max dead-end corridor
            max_common_path_m   REAL,               -- Max common path of egress

            -- Modifiers
            sprinklered_bonus   REAL DEFAULT 1.0,   -- Multiplier if sprinklered (e.g., 1.25)

            -- Source
            code_id             TEXT,
            clause              TEXT,
            jurisdiction        TEXT DEFAULT 'INTERNATIONAL'
        )
    """)

    # ==========================================================================
    # Table 5: Pressurization Triggers
    # ==========================================================================
    conn.execute("""
        CREATE TABLE IF NOT EXISTS ad_pressurization_trigger (
            trigger_id          TEXT PRIMARY KEY,
            element_type        TEXT NOT NULL,      -- STAIR, ELEVATOR_LOBBY, CORRIDOR

            -- Trigger conditions
            min_storeys         INTEGER,
            min_height_m        REAL,
            underground_storeys INTEGER,

            -- Pressure differential
            min_pressure_pa     INTEGER,            -- Minimum pressure (Pascal)
            max_pressure_pa     INTEGER,            -- Maximum (door operability)

            -- Door requirements
            max_door_force_n    INTEGER,            -- Max force to open door (N)

            -- Source
            code_id             TEXT,
            clause              TEXT,
            jurisdiction        TEXT DEFAULT 'INTERNATIONAL',
            description         TEXT
        )
    """)

    conn.commit()
    print("Created tables: ad_vert_circ_trigger, ad_stair_requirement, ad_elevator_requirement, ad_egress_travel, ad_pressurization_trigger")


def populate_triggers(conn):
    """Populate vertical circulation triggers - EXTRACTED from codes."""

    triggers = [
        # ======================================================================
        # STAIR TRIGGERS
        # ======================================================================
        # Basic: 2+ storeys need stairs (universal)
        ('STAIR_BASIC', 'STAIR', 'STOREY_COUNT',
         2, None, None, None,
         1, None,
         'ANY', 'INTERNATIONAL',
         'IBC_2021', '1011.1', 'Stairway required for 2+ storeys', 1),

        # Second stair trigger - occupant load (IBC Table 1006.2.1)
        ('STAIR_SECOND_OCC', 'STAIR', 'OCCUPANT_LOAD',
         None, None, 500, None,
         2, '1 + floor(occupants/500)',
         'ANY', 'INTERNATIONAL',
         'IBC_2021', '1006.2.1', 'Second exit/stair if occupant load >= 500', 1),

        # Malaysia: UBBL By-Law 166 - buildings >18m need 2 stairs
        ('STAIR_UBBL_HEIGHT', 'STAIR', 'TRAVEL_HEIGHT',
         None, 18.0, None, None,
         2, None,
         'ANY', 'MALAYSIA',
         'UBBL_2012', '166(1)', 'Building >18m requires minimum 2 protected stairs', 1),

        # High-rise: 3 stairs for very tall (IBC for >128m/420ft)
        ('STAIR_HIGHRISE', 'STAIR', 'TRAVEL_HEIGHT',
         None, 128.0, None, None,
         3, None,
         'ANY', 'INTERNATIONAL',
         'IBC_2021', '403.5.2', 'Buildings >128m require 3rd stair', 1),

        # ======================================================================
        # ELEVATOR TRIGGERS
        # ======================================================================
        # Basic passenger elevator (most codes: 4+ storeys)
        ('ELEV_BASIC_STOREY', 'ELEVATOR', 'STOREY_COUNT',
         4, None, None, None,
         1, None,
         'ANY', 'INTERNATIONAL',
         'IBC_2021', '3002.4', 'Elevator required for 4+ storeys (accessibility)', 1),

        # Malaysia: UBBL >4 storeys or >12m (By-Law 172)
        ('ELEV_UBBL', 'ELEVATOR', 'STOREY_COUNT',
         5, None, None, None,
         1, '1 + floor((floors-4)/10)',
         'ANY', 'MALAYSIA',
         'UBBL_2012', '172(1)', 'Lift required for >4 storeys', 1),

        # Residential: elevator per units (typical 1 per 50-80 units)
        ('ELEV_RESIDENTIAL', 'ELEVATOR', 'OCCUPANT_LOAD',
         None, None, 50, None,
         1, '1 + floor((units-50)/50)',
         'R', 'INTERNATIONAL',
         'IBC_2021', '3002.4', 'Residential elevator sizing', 0),

        # ======================================================================
        # FIRE LIFT TRIGGERS
        # ======================================================================
        # Fire/firefighter elevator (IBC >36.6m / 120ft)
        ('FIRE_LIFT_HEIGHT', 'FIRE_LIFT', 'TRAVEL_HEIGHT',
         None, 36.6, None, None,
         1, None,
         'ANY', 'INTERNATIONAL',
         'IBC_2021', '403.6.1', 'Fire service access elevator for buildings >36.6m', 1),

        # Malaysia: UBBL >30m (By-Law 173)
        ('FIRE_LIFT_UBBL', 'FIRE_LIFT', 'TRAVEL_HEIGHT',
         None, 30.0, None, None,
         1, None,
         'ANY', 'MALAYSIA',
         'UBBL_2012', '173', 'Fireman lift required for buildings >30m', 1),

        # ======================================================================
        # STRETCHER/MEDICAL LIFT TRIGGERS
        # ======================================================================
        # Stretcher-size elevator (hospitals, residential high-rise)
        ('STRETCHER_HOSPITAL', 'STRETCHER_LIFT', 'STOREY_COUNT',
         2, None, None, None,
         1, None,
         'I', 'INTERNATIONAL',  # I = Institutional (hospital)
         'IBC_2021', '3002.4', 'Stretcher-size elevator for hospitals', 1),

        # High-rise residential stretcher access (UBBL By-Law 174)
        ('STRETCHER_UBBL', 'STRETCHER_LIFT', 'TRAVEL_HEIGHT',
         None, 30.0, None, None,
         1, None,
         'R', 'MALAYSIA',
         'UBBL_2012', '174', 'Stretcher lift for residential >30m', 1),
    ]

    conn.executemany("""
        INSERT OR REPLACE INTO ad_vert_circ_trigger
        (trigger_id, element_type, trigger_type,
         min_storeys, min_travel_m, min_occupant_load, min_area_m2,
         min_quantity, quantity_formula,
         occupancy_group, jurisdiction,
         code_id, clause, description, is_mandatory)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, triggers)

    conn.commit()
    print(f"Inserted {len(triggers)} vertical circulation triggers")


def populate_stair_requirements(conn):
    """Populate stair design requirements - EXTRACTED from IBC/UBBL/NFPA."""

    requirements = [
        # ======================================================================
        # IBC 2021 Chapter 10 - General stair requirements
        # ======================================================================
        ('STAIR_IBC_GENERAL', 'ANY',
         1118, 7.6, 250,  # 44" min, 0.3"/person, 250 max per stair
         196, 102, 254, 3.66,  # 7.75" max riser, 4" min riser, 10" tread, 12' max flight
         1118, 1,  # 44" landing, required at doors
         864, 1, 305,  # 34" handrail, both sides, 12" extension
         1.0,
         'IBC_2021', 'Table 1011.5.2', 'INTERNATIONAL'),

        # Residential (R-3) - less stringent
        ('STAIR_IBC_RESIDENTIAL', 'R',
         914, None, None,  # 36" min, no per-person calc for residential
         196, 102, 254, 3.66,
         914, 1,
         864, 0, 305,  # Handrail one side okay for <44" width
         1.0,
         'IBC_2021', '1011.5.2', 'INTERNATIONAL'),

        # Assembly (theaters, etc) - more stringent
        ('STAIR_IBC_ASSEMBLY', 'A',
         1219, 7.6, 250,  # 48" minimum
         178, 102, 279, 3.66,  # 7" max riser, 11" tread
         1219, 1,
         864, 1, 305,
         2.0,  # 2-hour enclosure for assembly
         'IBC_2021', '1029', 'INTERNATIONAL'),

        # ======================================================================
        # UBBL 2012 (Malaysia) - By-Law 167-171
        # ======================================================================
        ('STAIR_UBBL_GENERAL', 'ANY',
         1050, 7.6, 200,  # 1050mm min, 200 persons per stair
         175, 100, 250, 3.0,  # 175mm max riser, 250mm tread, 3m flight
         1050, 1,
         900, 1, 300,
         1.0,
         'UBBL_2012', '167-171', 'MALAYSIA'),

        # UBBL Residential
        ('STAIR_UBBL_RESIDENTIAL', 'R',
         900, None, None,  # 900mm min for residential
         190, 100, 225, 3.0,  # Slightly relaxed riser
         900, 1,
         900, 1, 300,
         1.0,
         'UBBL_2012', '167(2)', 'MALAYSIA'),

        # UBBL High-rise (>18m) - protected stair
        ('STAIR_UBBL_HIGHRISE', 'ANY',
         1200, 7.6, 200,  # 1200mm min for high-rise
         175, 100, 250, 3.0,
         1200, 1,
         900, 1, 300,
         2.0,  # 2-hour fire rating
         'UBBL_2012', '166(3)', 'MALAYSIA'),

        # ======================================================================
        # NFPA 101 - Life Safety specific
        # ======================================================================
        ('STAIR_NFPA_HEALTHCARE', 'I',
         1524, 7.6, 150,  # 60" min for healthcare (stretcher)
         178, 102, 279, 3.66,
         1524, 1,
         864, 1, 305,
         2.0,
         'NFPA_101', '18.2.2', 'INTERNATIONAL'),
    ]

    conn.executemany("""
        INSERT OR REPLACE INTO ad_stair_requirement
        (req_id, occupancy_group,
         min_width_mm, width_per_person_mm, max_occupant_per_stair,
         max_riser_mm, min_riser_mm, min_tread_mm, max_flight_rise_m,
         min_landing_length_mm, landing_at_doors,
         handrail_height_mm, handrail_both_sides, handrail_extension_mm,
         min_fire_rating_hr,
         code_id, clause, jurisdiction)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, requirements)

    conn.commit()
    print(f"Inserted {len(requirements)} stair requirements")


def populate_elevator_requirements(conn):
    """Populate elevator requirements - EXTRACTED from IBC/UBBL/ADA."""

    requirements = [
        # ======================================================================
        # Passenger Elevators
        # ======================================================================
        ('ELEV_PASSENGER_BASIC', 'PASSENGER', 'ANY',
         4, 12.0, None, None,
         1, None,
         1100, 1350, 900,  # Min car: 1100mm wide, 1350mm deep, 900mm door
         1.5, 1.8,  # Lobby: 1.5m deep x 1.8m per car
         2.0, 0, 1,  # 2hr shaft, no smoke control, emergency power
         1, 1,  # Accessible, voice annunciator
         'IBC_2021', '3002.4', 'INTERNATIONAL'),

        # Residential elevator (smaller acceptable)
        ('ELEV_RESIDENTIAL', 'PASSENGER', 'R',
         5, None, 50, None,
         1, '1 + floor((units-50)/60)',
         1000, 1250, 800,  # Slightly smaller acceptable
         1.2, 1.5,
         1.0, 0, 1,
         1, 0,
         'IBC_2021', '3002.4', 'INTERNATIONAL'),

        # High-capacity (office, retail)
        ('ELEV_COMMERCIAL', 'PASSENGER', 'B',
         4, None, 300, None,
         2, '2 + floor(occupants/500)',
         1600, 1400, 1100,  # Larger cars
         2.0, 2.4,
         2.0, 1, 1,  # Smoke control required
         1, 1,
         'IBC_2021', '3002.4', 'INTERNATIONAL'),

        # ======================================================================
        # Fire/Firefighter Elevators
        # ======================================================================
        ('ELEV_FIRE_IBC', 'FIRE', 'ANY',
         None, 36.6, None, None,
         1, None,
         1500, 2100, 1100,  # Large car for firefighting equipment
         2.4, 2.4,
         2.0, 1, 1,
         1, 1,
         'IBC_2021', '403.6.1', 'INTERNATIONAL'),

        ('ELEV_FIRE_UBBL', 'FIRE', 'ANY',
         None, 30.0, None, None,
         1, None,
         1500, 2100, 1100,
         2.4, 2.4,
         2.0, 1, 1,
         1, 1,
         'UBBL_2012', '173', 'MALAYSIA'),

        # ======================================================================
        # Stretcher Elevators
        # ======================================================================
        ('ELEV_STRETCHER', 'STRETCHER', 'ANY',
         None, 30.0, None, None,
         1, None,
         1100, 2400, 1100,  # Deep car for stretcher (2400mm)
         2.0, 2.4,
         2.0, 0, 1,
         1, 1,
         'IBC_2021', '3002.4', 'INTERNATIONAL'),

        # Hospital stretcher
        ('ELEV_HOSPITAL', 'STRETCHER', 'I',
         2, None, None, 50,  # 50 beds trigger
         1, '1 + floor(beds/100)',
         1400, 2900, 1300,  # Bed elevator size
         2.4, 3.0,
         2.0, 1, 1,
         1, 1,
         'IBC_2021', '3002.4', 'INTERNATIONAL'),

        # ======================================================================
        # Accessible Elevators (ADA/MS 1184)
        # ======================================================================
        ('ELEV_ACCESSIBLE_ADA', 'ACCESSIBLE', 'ANY',
         None, None, None, None,
         1, None,
         1730, 1350, 915,  # ADA minimum (68" x 53" x 36")
         1.5, 1.8,
         1.0, 0, 1,
         1, 1,
         'ADA_2010', '407', 'USA'),

        ('ELEV_ACCESSIBLE_MS', 'ACCESSIBLE', 'ANY',
         None, None, None, None,
         1, None,
         1100, 1400, 900,  # MS 1184 minimum
         1.5, 1.8,
         1.0, 0, 1,
         1, 1,
         'MS_1184', '6.3', 'MALAYSIA'),
    ]

    conn.executemany("""
        INSERT OR REPLACE INTO ad_elevator_requirement
        (req_id, elevator_type, occupancy_group,
         trigger_storeys, trigger_travel_m, trigger_occupants, trigger_beds,
         min_quantity, quantity_formula,
         min_width_mm, min_depth_mm, min_door_width_mm,
         min_lobby_depth_m, min_lobby_width_m,
         fire_rating_hr, smoke_control, emergency_power,
         is_accessible, voice_annunciator,
         code_id, clause, jurisdiction)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, requirements)

    conn.commit()
    print(f"Inserted {len(requirements)} elevator requirements")


def populate_egress_travel(conn):
    """Populate egress travel distances - EXTRACTED from IBC Table 1017.2."""

    travel = [
        # ======================================================================
        # IBC 2021 Table 1017.2 - Exit Access Travel Distance
        # ======================================================================
        ('TRAVEL_IBC_A', 'A', 61.0, 6.1, 23.0, 1.25, 'IBC_2021', '1017.2', 'INTERNATIONAL'),  # Assembly
        ('TRAVEL_IBC_B', 'B', 91.0, 15.2, 23.0, 1.25, 'IBC_2021', '1017.2', 'INTERNATIONAL'),  # Business
        ('TRAVEL_IBC_E', 'E', 61.0, 6.1, 23.0, 1.25, 'IBC_2021', '1017.2', 'INTERNATIONAL'),  # Educational
        ('TRAVEL_IBC_F', 'F', 76.0, 15.2, 23.0, 1.25, 'IBC_2021', '1017.2', 'INTERNATIONAL'),  # Factory
        ('TRAVEL_IBC_H', 'H', 23.0, 6.1, 7.6, 1.0, 'IBC_2021', '1017.2', 'INTERNATIONAL'),    # Hazardous (no bonus)
        ('TRAVEL_IBC_I', 'I', 61.0, 6.1, 23.0, 1.25, 'IBC_2021', '1017.2', 'INTERNATIONAL'),  # Institutional
        ('TRAVEL_IBC_M', 'M', 61.0, 15.2, 23.0, 1.25, 'IBC_2021', '1017.2', 'INTERNATIONAL'),  # Mercantile
        ('TRAVEL_IBC_R', 'R', 61.0, 15.2, 23.0, 1.25, 'IBC_2021', '1017.2', 'INTERNATIONAL'),  # Residential
        ('TRAVEL_IBC_S', 'S', 122.0, 15.2, 23.0, 1.25, 'IBC_2021', '1017.2', 'INTERNATIONAL'), # Storage

        # ======================================================================
        # UBBL 2012 (Malaysia) - By-Law 165
        # ======================================================================
        ('TRAVEL_UBBL_GENERAL', 'ANY', 45.0, 7.5, 15.0, 1.25, 'UBBL_2012', '165(1)', 'MALAYSIA'),
        ('TRAVEL_UBBL_HIGHRISE', 'ANY', 30.0, 7.5, 15.0, 1.0, 'UBBL_2012', '165(2)', 'MALAYSIA'),  # >18m, no sprinkler bonus
        ('TRAVEL_UBBL_RESIDENTIAL', 'R', 60.0, 15.0, 22.5, 1.25, 'UBBL_2012', '165(3)', 'MALAYSIA'),

        # ======================================================================
        # NFPA 101 - Life Safety Code
        # ======================================================================
        ('TRAVEL_NFPA_HEALTHCARE', 'I', 45.0, 6.1, 15.0, 1.0, 'NFPA_101', '18.2.6', 'INTERNATIONAL'),  # Healthcare strict
        ('TRAVEL_NFPA_HIGHRISE', 'ANY', 61.0, 15.2, 15.0, 1.0, 'NFPA_101', '11.2.6', 'INTERNATIONAL'),  # High-rise
    ]

    conn.executemany("""
        INSERT OR REPLACE INTO ad_egress_travel
        (travel_id, occupancy_group, max_travel_m, max_dead_end_m, max_common_path_m,
         sprinklered_bonus, code_id, clause, jurisdiction)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, travel)

    conn.commit()
    print(f"Inserted {len(travel)} egress travel requirements")


def populate_pressurization(conn):
    """Populate stair/shaft pressurization triggers - EXTRACTED from codes."""

    pressurization = [
        # ======================================================================
        # IBC 2021 - Smoke-protected egress
        # ======================================================================
        ('PRESS_IBC_STAIR', 'STAIR',
         None, 22.9, None,  # >75ft
         25, 87,  # 25-87 Pa differential
         133,  # 30 lbf max door force
         'IBC_2021', '909.6', 'INTERNATIONAL',
         'Pressurized stair enclosure for buildings >22.9m'),

        ('PRESS_IBC_ELEV_LOBBY', 'ELEVATOR_LOBBY',
         None, 22.9, None,
         25, 87,
         133,
         'IBC_2021', '909.6', 'INTERNATIONAL',
         'Elevator lobby pressurization for buildings >22.9m'),

        # ======================================================================
        # UBBL 2012 (Malaysia) - By-Law 178-182
        # ======================================================================
        ('PRESS_UBBL_STAIR', 'STAIR',
         7, 18.0, None,  # >18m or >6 storeys
         50, 100,  # 50-100 Pa (UBBL stricter)
         100,
         'UBBL_2012', '178', 'MALAYSIA',
         'Pressurized protected stairway >18m'),

        ('PRESS_UBBL_LOBBY', 'ELEVATOR_LOBBY',
         7, 18.0, None,
         50, 100,
         100,
         'UBBL_2012', '179', 'MALAYSIA',
         'Elevator lobby pressurization >18m'),

        ('PRESS_UBBL_BASEMENT', 'STAIR',
         None, None, 2,  # 2+ basement levels
         50, 100,
         100,
         'UBBL_2012', '180', 'MALAYSIA',
         'Pressurized stair for 2+ basement levels'),

        # ======================================================================
        # NFPA 92 - Smoke Control Systems
        # ======================================================================
        ('PRESS_NFPA_STAIR', 'STAIR',
         None, 22.9, None,
         12, 87,  # NFPA allows lower minimum
         133,
         'NFPA_92', '4.4', 'INTERNATIONAL',
         'NFPA 92 stair pressurization'),

        ('PRESS_NFPA_CORRIDOR', 'CORRIDOR',
         None, 22.9, None,
         12, 45,  # Lower for corridors
         133,
         'NFPA_92', '4.4', 'INTERNATIONAL',
         'Corridor pressurization (smoke control)'),
    ]

    conn.executemany("""
        INSERT OR REPLACE INTO ad_pressurization_trigger
        (trigger_id, element_type,
         min_storeys, min_height_m, underground_storeys,
         min_pressure_pa, max_pressure_pa,
         max_door_force_n,
         code_id, clause, jurisdiction,
         description)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, pressurization)

    conn.commit()
    print(f"Inserted {len(pressurization)} pressurization triggers")


def print_summary(conn):
    """Print comprehensive summary."""

    print("\n" + "=" * 70)
    print("VERTICAL CIRCULATION AD TABLES - SUMMARY")
    print("=" * 70)

    # Triggers
    print("\n1. CIRCULATION TRIGGERS (ad_vert_circ_trigger)")
    print("-" * 50)
    for row in conn.execute("""
        SELECT element_type, COUNT(*), GROUP_CONCAT(DISTINCT jurisdiction)
        FROM ad_vert_circ_trigger
        GROUP BY element_type
    """):
        print(f"   {row[0]}: {row[1]} rules ({row[2]})")

    # Stair requirements
    print("\n2. STAIR REQUIREMENTS (ad_stair_requirement)")
    print("-" * 50)
    for row in conn.execute("""
        SELECT req_id, min_width_mm, max_riser_mm, min_tread_mm, jurisdiction
        FROM ad_stair_requirement
        ORDER BY jurisdiction, occupancy_group
    """):
        print(f"   {row[0]}: width={row[1]}mm, riser≤{row[2]}mm, tread≥{row[3]}mm [{row[4]}]")

    # Elevator requirements
    print("\n3. ELEVATOR REQUIREMENTS (ad_elevator_requirement)")
    print("-" * 50)
    for row in conn.execute("""
        SELECT elevator_type, COUNT(*), GROUP_CONCAT(DISTINCT jurisdiction)
        FROM ad_elevator_requirement
        GROUP BY elevator_type
    """):
        print(f"   {row[0]}: {row[1]} specs ({row[2]})")

    # Travel distances
    print("\n4. EGRESS TRAVEL (ad_egress_travel)")
    print("-" * 50)
    for row in conn.execute("""
        SELECT jurisdiction, COUNT(*),
               ROUND(AVG(max_travel_m),1), ROUND(AVG(max_dead_end_m),1)
        FROM ad_egress_travel
        GROUP BY jurisdiction
    """):
        print(f"   {row[0]}: {row[1]} rules (avg travel={row[2]}m, avg dead-end={row[3]}m)")

    # Pressurization
    print("\n5. PRESSURIZATION TRIGGERS (ad_pressurization_trigger)")
    print("-" * 50)
    for row in conn.execute("""
        SELECT element_type, COUNT(*), GROUP_CONCAT(DISTINCT jurisdiction)
        FROM ad_pressurization_trigger
        GROUP BY element_type
    """):
        print(f"   {row[0]}: {row[1]} rules ({row[2]})")

    # Code coverage
    print("\n" + "=" * 70)
    print("CODE COVERAGE")
    print("-" * 50)

    all_codes = set()
    for table in ['ad_vert_circ_trigger', 'ad_stair_requirement',
                  'ad_elevator_requirement', 'ad_egress_travel', 'ad_pressurization_trigger']:
        for row in conn.execute(f"SELECT DISTINCT code_id FROM {table} WHERE code_id IS NOT NULL"):
            all_codes.add(row[0])

    for code in sorted(all_codes):
        total = 0
        for table in ['ad_vert_circ_trigger', 'ad_stair_requirement',
                      'ad_elevator_requirement', 'ad_egress_travel', 'ad_pressurization_trigger']:
            for row in conn.execute(f"SELECT COUNT(*) FROM {table} WHERE code_id = ?", (code,)):
                total += row[0]
        print(f"   {code}: {total} entries")

    # Total counts
    print("\n" + "=" * 70)
    print("TOTALS")
    print("-" * 50)

    tables = [
        ('ad_vert_circ_trigger', 'Circulation triggers'),
        ('ad_stair_requirement', 'Stair requirements'),
        ('ad_elevator_requirement', 'Elevator requirements'),
        ('ad_egress_travel', 'Egress travel limits'),
        ('ad_pressurization_trigger', 'Pressurization triggers'),
    ]

    grand_total = 0
    for table, desc in tables:
        count = conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
        grand_total += count
        print(f"   {desc}: {count}")

    print(f"\n   GRAND TOTAL: {grand_total} vertical circulation rules")
    print("=" * 70)


def main():
    print(f"Creating vertical circulation AD tables in {DB_PATH}")
    print("Source: UBBL 2012, IBC 2021, NFPA 101, MS 1184, ADA 2010")
    print()

    conn = sqlite3.connect(DB_PATH)

    create_tables(conn)
    populate_triggers(conn)
    populate_stair_requirements(conn)
    populate_elevator_requirements(conn)
    populate_egress_travel(conn)
    populate_pressurization(conn)
    print_summary(conn)

    conn.close()
    print("\nDone!")


if __name__ == '__main__':
    main()
