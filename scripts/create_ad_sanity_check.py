#!/usr/bin/env python3
"""
Create AD Sanity Check Schema for BIM Compiler

Phase 78: Metadata-driven sanity check applicability.
Tables:
  - ad_check_applicability: When each check should run
  - ad_check_threshold: Configurable thresholds per check

Following the AD pattern from:
  - FireProtectionAD.java
  - SpaceTypeAD.java

Sources:
  - UBBL 2012 (Malaysia Uniform Building By-Laws)
  - IBC 2021 (International Building Code)
  - MS 1184 (Malaysian Standard for Accessibility)
  - NFPA 101 (Life Safety Code)

Author: BIM Compiler Project
Date: 2026-02-05
"""

import sqlite3
import os
import sys

DB_PATH = "library/component_library.db"


def create_sanity_check_schema(conn: sqlite3.Connection) -> None:
    """Create Sanity Check AD tables."""
    cursor = conn.cursor()

    # Check Applicability - when does each check run?
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_check_applicability (
        check_id          TEXT PRIMARY KEY,
        check_name        TEXT NOT NULL,
        check_class       TEXT NOT NULL,         -- Java class name (e.g., 'StairwellCheck')
        category          TEXT NOT NULL,         -- STRUCTURAL, MEP, EGRESS, ENVELOPE, GEOMETRY

        -- Applicability rules (NULL = applies to all)
        min_storeys       INTEGER,               -- Minimum storeys for check to apply
        max_storeys       INTEGER,               -- Maximum storeys (NULL = no limit)
        occupancy_groups  TEXT,                  -- Comma-separated: 'R,A,B' or NULL for all
        min_area_m2       REAL,                  -- Minimum building area for check
        requires_element  TEXT,                  -- IFC type required (e.g., 'IfcStair')

        -- Skip rules
        skip_condition    TEXT,                  -- SQL-like condition for skip
        skip_reason       TEXT,                  -- Reason shown when skipped

        -- Behavior
        severity          TEXT DEFAULT 'ERROR',  -- ERROR, WARNING, INFO
        enabled           INTEGER DEFAULT 1,
        sort_order        INTEGER DEFAULT 100,   -- Execution order

        -- Traceability
        code_id           TEXT,
        clause            TEXT,
        description       TEXT,

        is_active         INTEGER DEFAULT 1
    )
    """)

    # Check Thresholds - configurable limits per check
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ad_check_threshold (
        threshold_id      TEXT PRIMARY KEY,
        check_id          TEXT NOT NULL,         -- FK to ad_check_applicability
        threshold_name    TEXT NOT NULL,         -- e.g., 'max_travel_distance', 'min_stair_width'

        -- Scope (which buildings this threshold applies to)
        occupancy_group   TEXT,                  -- R, A, B, E, etc. (NULL = all)
        storey_type       TEXT,                  -- SINGLE, MULTI, HIGH_RISE (NULL = all)
        building_class    TEXT,                  -- RESIDENTIAL, PUBLIC, COMMERCIAL (NULL = all)

        -- Values
        threshold_value   REAL NOT NULL,
        threshold_unit    TEXT,                  -- 'm', 'mm', 'm2', 'hr', '%'
        sprinkler_bonus   REAL DEFAULT 1.0,      -- Multiplier when sprinklered (e.g., 1.25)

        -- Traceability
        code_id           TEXT,
        clause            TEXT,
        description       TEXT,
        jurisdiction      TEXT DEFAULT 'INTERNATIONAL',

        is_active         INTEGER DEFAULT 1,

        FOREIGN KEY (check_id) REFERENCES ad_check_applicability(check_id)
    )
    """)

    # Index for efficient queries
    cursor.execute("""
    CREATE INDEX IF NOT EXISTS idx_threshold_check
    ON ad_check_threshold(check_id, occupancy_group, storey_type)
    """)

    conn.commit()
    print("[AD SanityCheck] Created: ad_check_applicability, ad_check_threshold")


def populate_check_applicability(conn: sqlite3.Connection) -> None:
    """Populate check applicability data."""
    cursor = conn.cursor()

    # EXTRACTED from existing Java check classes
    checks = [
        # check_id, check_name, check_class, category, min_storeys, max_storeys,
        # occupancy_groups, min_area_m2, requires_element, skip_condition, skip_reason,
        # severity, enabled, sort_order, code_id, clause, description

        # === ENVELOPE CHECKS (run for all buildings) ===
        ('foundation', 'Foundation Ground Level', 'FoundationCheck', 'ENVELOPE',
         None, None, None, None, 'IfcSlab', None, None,
         'ERROR', 1, 1, 'IBC', '1809', 'Verify foundation at ground level'),

        ('entry_door', 'Entry Door', 'EntryDoorCheck', 'ENVELOPE',
         None, None, None, None, 'IfcDoor', None, None,
         'ERROR', 1, 2, 'UBBL', 'By-Law 37', 'Verify main entry exists on perimeter'),

        ('window_placement', 'Window Placement', 'WindowPlacementCheck', 'ENVELOPE',
         None, None, None, None, 'IfcWindow', None, None,
         'ERROR', 1, 3, 'UBBL', 'By-Law 39', 'Verify windows on exterior walls'),

        ('roof_coverage', 'Roof Coverage', 'RoofCoverageCheck', 'ENVELOPE',
         None, None, None, None, 'IfcRoof', None, None,
         'ERROR', 1, 6, 'IBC', '1503', 'Verify roof covers building footprint'),

        ('envelope_containment', 'Envelope Containment', 'EnvelopeContainmentCheck', 'ENVELOPE',
         None, None, None, None, None, None, None,
         'ERROR', 1, 7, None, None, 'Verify all rooms inside building envelope'),

        # === GEOMETRY CHECKS ===
        ('room_connectivity', 'Room Connectivity', 'RoomConnectivityCheck', 'GEOMETRY',
         None, None, None, None, 'IfcSpace', None, None,
         'ERROR', 1, 4, None, None, 'Verify all rooms reachable from entry'),

        ('room_proportions', 'Room Proportions', 'RoomProportionCheck', 'GEOMETRY',
         None, None, None, None, 'IfcSpace', None, None,
         'WARNING', 1, 5, 'UBBL', 'Schedule 4', 'Check room aspect ratios'),

        ('ceiling_height', 'Ceiling Height', 'CeilingHeightCheck', 'GEOMETRY',
         None, None, None, None, 'IfcSpace', None, None,
         'ERROR', 1, 21, 'UBBL', 'By-Law 44', 'Verify minimum ceiling heights'),

        ('floor_area', 'Floor Area Requirements', 'FloorAreaCheck', 'GEOMETRY',
         None, None, None, None, 'IfcSpace', None, None,
         'ERROR', 1, 22, 'UBBL', 'Schedule 4', 'Verify minimum room floor areas'),

        ('window_area', 'Window-to-Floor Area Ratio', 'WindowAreaCheck', 'GEOMETRY',
         None, None, None, None, 'IfcWindow', None, None,
         'ERROR', 1, 20, 'UBBL', 'By-Law 39', 'Verify 10% window ratio for habitable rooms'),

        # === MEP CHECKS ===
        ('storey_count', 'Storey Count', 'StoreyCountCheck', 'MEP',
         None, None, None, None, None, None, None,
         'INFO', 1, 8, None, None, 'Report building storey count'),

        ('electrical_elements', 'Electrical Elements', 'ElectricalElementsCheck', 'MEP',
         None, None, None, None, None, None, None,
         'WARNING', 1, 9, 'MS', '1525', 'Verify electrical element counts'),

        ('plumbing_elements', 'Plumbing Elements', 'PlumbingElementsCheck', 'MEP',
         None, None, None, None, None, 'no_wet_rooms', 'No wet rooms detected',
         'WARNING', 1, 10, 'IPC', '403', 'Verify plumbing element counts'),

        ('fire_protection', 'Fire Protection Requirements', 'FireProtectionCheck', 'MEP',
         None, None, None, None, None, None, None,
         'ERROR', 1, 13, 'UBBL', 'By-Law 225', 'Verify sprinkler requirements'),

        ('fire_compartment', 'Fire Compartment Limits', 'CompartmentCheck', 'MEP',
         None, None, None, None, None, None, None,
         'ERROR', 1, 14, 'UBBL', 'By-Law 220', 'Verify compartment area limits'),

        # === EGRESS CHECKS ===
        ('escape_route', 'Escape Route Travel Distance', 'EscapeRouteCheck', 'EGRESS',
         None, None, None, None, 'IfcSpace', None, None,
         'ERROR', 1, 16, 'UBBL', 'By-Law 165', 'Verify travel distance limits'),

        ('dead_end_corridor', 'Dead-End Corridor Length', 'DeadEndCorridorCheck', 'EGRESS',
         None, None, None, None, 'IfcSpace:CORRIDOR', 'no_corridors', 'No corridors found',
         'ERROR', 1, 17, 'UBBL', 'By-Law 167', 'Verify dead-end corridor limits'),

        ('stairwell', 'Stairwell Dimensions', 'StairwellCheck', 'EGRESS',
         2, None, None, None, 'IfcStair', 'single_storey', 'Single-storey building - no stairs required',
         'ERROR', 1, 18, 'UBBL', 'By-Law 171', 'Verify stair width and dimensions'),

        ('door_clearance', 'Door Clearance Dimensions', 'DoorClearanceCheck', 'EGRESS',
         None, None, None, None, 'IfcDoor', None, None,
         'ERROR', 1, 19, 'MS', '1184', 'Verify door width for accessibility'),

        # === STRUCTURAL CHECKS ===
        ('wall_continuity', 'Wall Continuity & Perimeter', 'WallContinuityCheck', 'STRUCTURAL',
         None, None, None, None, 'IfcWall', None, None,
         'ERROR', 1, 15, None, None, 'Verify wall connectivity and perimeter closure'),

        ('structural_grid', 'Structural Grid Alignment', 'StructuralGridCheck', 'STRUCTURAL',
         None, None, None, None, 'IfcColumn', 'no_grid', 'No structural grid (masonry bearing)',
         'WARNING', 1, 23, 'IBC', '2304', 'Verify column alignment and beam spans'),

        # === HIGH-RISE CHECKS (Phase 81B) ===
        # Protected stairs requirement (UBBL 166)
        ('protected_stairs', 'Protected Stairs Count', 'ProtectedStairsCheck', 'EGRESS',
         None, None, None, None, 'IfcStair', 'not_highrise', 'Building height ≤18m (not high-rise)',
         'ERROR', 1, 24, 'UBBL', 'By-Law 166', 'Verify minimum 2 protected stairs for high-rise'),

        # Fire lift requirement (UBBL 179)
        ('fire_lift', 'Fire Lift Emergency Power', 'FireLiftCheck', 'EGRESS',
         None, None, None, None, 'IfcTransportElement', 'height_under_30m', 'Building height ≤30m (fire lift not required)',
         'ERROR', 1, 25, 'UBBL', 'By-Law 179', 'Verify fire lift has emergency power for buildings >30m'),

        # Stairwell pressurization (UBBL 178)
        ('stairwell_pressurization', 'Stairwell Pressurization', 'StairwellPressurizationCheck', 'MEP',
         None, None, None, None, 'IfcStair', 'not_highrise', 'Building height ≤18m (pressurization not required)',
         'ERROR', 1, 26, 'UBBL', 'By-Law 178', 'Verify stairwell pressurization 50-100 Pa for high-rise'),

        # === COMPLIANCE CHECKS ===
        ('witness_verification', 'Witness Verification', 'WitnessVerificationCheck', 'COMPLIANCE',
         None, None, None, None, None, None, None,
         'ERROR', 1, 11, None, None, 'Verify witness file claims'),

        ('pattern_b', 'Pattern B Compliance', 'PatternBCheck', 'COMPLIANCE',
         None, None, None, None, None, None, None,
         'ERROR', 1, 12, None, None, 'Verify all elements have zero transforms'),
    ]

    cursor.executemany("""
        INSERT OR REPLACE INTO ad_check_applicability
        (check_id, check_name, check_class, category, min_storeys, max_storeys,
         occupancy_groups, min_area_m2, requires_element, skip_condition, skip_reason,
         severity, enabled, sort_order, code_id, clause, description)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
    """, checks)

    conn.commit()
    print(f"[AD SanityCheck] Populated {len(checks)} check applicability rules")


def populate_thresholds(conn: sqlite3.Connection) -> None:
    """Populate check threshold data."""
    cursor = conn.cursor()

    # EXTRACTED from existing check constants and UBBL/IBC codes
    thresholds = [
        # threshold_id, check_id, threshold_name, occupancy_group, storey_type, building_class,
        # threshold_value, threshold_unit, sprinkler_bonus, code_id, clause, description, jurisdiction

        # === ESCAPE ROUTE THRESHOLDS ===
        # General travel distance (UBBL By-Law 165)
        ('TRAVEL_UBBL_GENERAL', 'escape_route', 'max_travel_distance', None, None, None,
         45.0, 'm', 1.25, 'UBBL', 'By-Law 165', 'General travel distance limit', 'MALAYSIA'),

        # High-rise travel distance (UBBL By-Law 165)
        ('TRAVEL_UBBL_HIGHRISE', 'escape_route', 'max_travel_distance', None, 'HIGH_RISE', None,
         30.0, 'm', 1.0, 'UBBL', 'By-Law 165', 'High-rise travel distance (no sprinkler bonus)', 'MALAYSIA'),

        # Assembly occupancy (IBC)
        ('TRAVEL_IBC_ASSEMBLY', 'escape_route', 'max_travel_distance', 'A', None, None,
         61.0, 'm', 1.25, 'IBC', '1017.2', 'Assembly occupancy travel limit', 'INTERNATIONAL'),

        # Business occupancy (IBC)
        ('TRAVEL_IBC_BUSINESS', 'escape_route', 'max_travel_distance', 'B', None, None,
         91.0, 'm', 1.25, 'IBC', '1017.2', 'Business occupancy travel limit', 'INTERNATIONAL'),

        # === DEAD-END CORRIDOR THRESHOLDS ===
        ('DEADEND_UBBL', 'dead_end_corridor', 'max_dead_end_length', None, None, None,
         7.5, 'm', 1.5, 'UBBL', 'By-Law 167', 'Dead-end corridor limit', 'MALAYSIA'),

        ('DEADEND_IBC', 'dead_end_corridor', 'max_dead_end_length', None, None, None,
         6.1, 'm', 1.5, 'IBC', '1020.4', 'Dead-end corridor limit (20ft)', 'INTERNATIONAL'),

        # === STAIRWELL THRESHOLDS ===
        # Public building stair width (UBBL By-Law 171)
        ('STAIR_WIDTH_UBBL_PUBLIC', 'stairwell', 'min_stair_width', None, None, 'PUBLIC',
         1100.0, 'mm', 1.0, 'UBBL', 'By-Law 171', 'Public building stair width', 'MALAYSIA'),

        # Residential stair width (MS 1184)
        ('STAIR_WIDTH_MS_RESIDENTIAL', 'stairwell', 'min_stair_width', 'R', None, 'RESIDENTIAL',
         900.0, 'mm', 1.0, 'MS', '1184', 'Residential stair width', 'MALAYSIA'),

        # Stair rise limit
        ('STAIR_RISE_UBBL', 'stairwell', 'max_stair_rise', None, None, None,
         180.0, 'mm', 1.0, 'UBBL', 'By-Law 172', 'Maximum stair rise', 'MALAYSIA'),

        # Stair going (tread) minimum
        ('STAIR_GOING_UBBL', 'stairwell', 'min_stair_going', None, None, None,
         250.0, 'mm', 1.0, 'UBBL', 'By-Law 172', 'Minimum stair going', 'MALAYSIA'),

        # === DOOR CLEARANCE THRESHOLDS ===
        # Accessible route (MS 1184)
        ('DOOR_WIDTH_ACCESSIBLE', 'door_clearance', 'min_door_width', None, None, 'PUBLIC',
         850.0, 'mm', 1.0, 'MS', '1184', 'Accessible route door width', 'MALAYSIA'),

        # Standard door width
        ('DOOR_WIDTH_STANDARD', 'door_clearance', 'min_door_width', None, None, 'RESIDENTIAL',
         750.0, 'mm', 1.0, 'UBBL', 'By-Law 37', 'Standard door width', 'MALAYSIA'),

        # Door height
        ('DOOR_HEIGHT_MIN', 'door_clearance', 'min_door_height', None, None, None,
         2000.0, 'mm', 1.0, 'UBBL', 'By-Law 37', 'Minimum door height', 'MALAYSIA'),

        # === CEILING HEIGHT THRESHOLDS ===
        # Habitable room (UBBL By-Law 44)
        ('CEILING_HABITABLE', 'ceiling_height', 'min_ceiling_height', None, None, None,
         2750.0, 'mm', 1.0, 'UBBL', 'By-Law 44', 'Habitable room ceiling height', 'MALAYSIA'),

        # Utility room
        ('CEILING_UTILITY', 'ceiling_height', 'min_ceiling_height_utility', None, None, None,
         2500.0, 'mm', 1.0, 'UBBL', 'By-Law 44', 'Utility room ceiling height', 'MALAYSIA'),

        # Storage
        ('CEILING_STORAGE', 'ceiling_height', 'min_ceiling_height_storage', None, None, None,
         2200.0, 'mm', 1.0, 'UBBL', 'By-Law 44', 'Storage room ceiling height', 'MALAYSIA'),

        # === WINDOW AREA THRESHOLDS ===
        # Natural lighting (UBBL By-Law 39)
        ('WINDOW_RATIO_LIGHTING', 'window_area', 'min_window_ratio', None, None, None,
         10.0, '%', 1.0, 'UBBL', 'By-Law 39', 'Window to floor area ratio for lighting', 'MALAYSIA'),

        # Natural ventilation (UBBL By-Law 40)
        ('WINDOW_RATIO_VENTILATION', 'window_area', 'min_openable_ratio', None, None, None,
         5.0, '%', 1.0, 'UBBL', 'By-Law 40', 'Openable window ratio for ventilation', 'MALAYSIA'),

        # === FLOOR AREA THRESHOLDS ===
        # Bedroom single (MS 1184 / UBBL Schedule 4)
        ('FLOOR_BEDROOM_SINGLE', 'floor_area', 'min_floor_area', None, None, None,
         9.3, 'm2', 1.0, 'UBBL', 'Schedule 4', 'Single bedroom minimum area', 'MALAYSIA'),

        # Bedroom master
        ('FLOOR_BEDROOM_MASTER', 'floor_area', 'min_floor_area_master', None, None, None,
         11.15, 'm2', 1.0, 'UBBL', 'Schedule 4', 'Master bedroom minimum area', 'MALAYSIA'),

        # Living room
        ('FLOOR_LIVING', 'floor_area', 'min_floor_area_living', None, None, None,
         11.0, 'm2', 1.0, 'UBBL', 'Schedule 4', 'Living room minimum area', 'MALAYSIA'),

        # Kitchen
        ('FLOOR_KITCHEN', 'floor_area', 'min_floor_area_kitchen', None, None, None,
         4.5, 'm2', 1.0, 'UBBL', 'Schedule 4', 'Kitchen minimum area', 'MALAYSIA'),

        # Bathroom
        ('FLOOR_BATHROOM', 'floor_area', 'min_floor_area_bathroom', None, None, None,
         2.5, 'm2', 1.0, 'UBBL', 'Schedule 4', 'Bathroom minimum area', 'MALAYSIA'),

        # Classroom
        ('FLOOR_CLASSROOM', 'floor_area', 'min_floor_area_classroom', 'E', None, None,
         45.0, 'm2', 1.0, 'IBC', '1004.5', 'Classroom minimum area', 'INTERNATIONAL'),

        # === STRUCTURAL THRESHOLDS ===
        # Beam span limits
        ('BEAM_SPAN_TIMBER', 'structural_grid', 'max_beam_span_timber', None, None, None,
         6.0, 'm', 1.0, 'IBC', '2304', 'Timber beam max span', 'INTERNATIONAL'),

        ('BEAM_SPAN_CONCRETE', 'structural_grid', 'max_beam_span_concrete', None, None, None,
         8.0, 'm', 1.0, 'ACI', '318', 'Concrete beam max span', 'INTERNATIONAL'),

        ('BEAM_SPAN_STEEL', 'structural_grid', 'max_beam_span_steel', None, None, None,
         12.0, 'm', 1.0, 'AISC', '360', 'Steel beam max span', 'INTERNATIONAL'),

        # === COMPARTMENT THRESHOLDS ===
        # Residential (UBBL By-Law 220)
        ('COMPARTMENT_R_NO_SPRINK', 'compartment', 'max_compartment_area', 'R', None, None,
         500.0, 'm2', 2.0, 'UBBL', 'By-Law 220', 'Residential compartment limit (no sprinkler)', 'MALAYSIA'),

        ('COMPARTMENT_GENERAL', 'compartment', 'max_compartment_area', None, None, None,
         1000.0, 'm2', 1.5, 'UBBL', 'By-Law 220', 'General compartment limit', 'MALAYSIA'),

        # === ROOM PROPORTION THRESHOLDS ===
        ('PROPORTION_GENERAL', 'room_proportions', 'min_aspect_ratio', None, None, None,
         0.10, 'ratio', 1.0, None, None, 'General room aspect ratio minimum', 'INTERNATIONAL'),

        ('PROPORTION_CORRIDOR', 'room_proportions', 'min_aspect_ratio_corridor', None, None, None,
         0.05, 'ratio', 1.0, None, None, 'Corridor aspect ratio minimum', 'INTERNATIONAL'),

        # === HIGH-RISE THRESHOLDS (Phase 81B) ===
        # High-rise height threshold (UBBL definition)
        ('HIGHRISE_HEIGHT_THRESHOLD', 'protected_stairs', 'highrise_height_threshold', None, None, None,
         18.0, 'm', 1.0, 'UBBL', 'By-Law 3', 'High-rise building height threshold', 'MALAYSIA'),

        # Protected stairs minimum count for high-rise
        ('PROTECTED_STAIRS_MIN', 'protected_stairs', 'min_protected_stairs', None, 'HIGH_RISE', None,
         2.0, 'count', 1.0, 'UBBL', 'By-Law 166', 'Minimum protected stairs for high-rise', 'MALAYSIA'),

        # Fire lift height threshold (UBBL By-Law 179)
        ('FIRELIFT_HEIGHT_THRESHOLD', 'fire_lift', 'firelift_height_threshold', None, None, None,
         30.0, 'm', 1.0, 'UBBL', 'By-Law 179', 'Fire lift required above this height', 'MALAYSIA'),

        # Stairwell pressurization range (UBBL By-Law 178)
        ('PRESSURIZATION_MIN', 'stairwell_pressurization', 'min_pressure', None, 'HIGH_RISE', None,
         50.0, 'Pa', 1.0, 'UBBL', 'By-Law 178', 'Minimum stairwell pressurization', 'MALAYSIA'),

        ('PRESSURIZATION_MAX', 'stairwell_pressurization', 'max_pressure', None, 'HIGH_RISE', None,
         100.0, 'Pa', 1.0, 'UBBL', 'By-Law 178', 'Maximum stairwell pressurization', 'MALAYSIA'),
    ]

    cursor.executemany("""
        INSERT OR REPLACE INTO ad_check_threshold
        (threshold_id, check_id, threshold_name, occupancy_group, storey_type, building_class,
         threshold_value, threshold_unit, sprinkler_bonus, code_id, clause, description, jurisdiction)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
    """, thresholds)

    conn.commit()
    print(f"[AD SanityCheck] Populated {len(thresholds)} check thresholds")


def main():
    """Main entry point."""
    if not os.path.exists(DB_PATH):
        print(f"Error: Database not found: {DB_PATH}")
        print("Run from project root directory")
        sys.exit(1)

    conn = sqlite3.connect(DB_PATH)
    try:
        create_sanity_check_schema(conn)
        populate_check_applicability(conn)
        populate_thresholds(conn)

        # Verify
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM ad_check_applicability")
        check_count = cursor.fetchone()[0]
        cursor.execute("SELECT COUNT(*) FROM ad_check_threshold")
        threshold_count = cursor.fetchone()[0]

        print(f"\n[AD SanityCheck] Complete: {check_count} checks, {threshold_count} thresholds")

    finally:
        conn.close()


if __name__ == "__main__":
    main()
