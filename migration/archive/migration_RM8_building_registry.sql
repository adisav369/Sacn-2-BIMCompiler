-- Phase RM-8: Building Registry — one engine, N buildings from metadata
-- Pattern: iDempiere AD_Process + AD_Process_Para — registry drives pipeline
-- Each row is a complete compilation unit: DSL + paths + thresholds.
-- Adding a new building = one INSERT, zero Java files.

-- ============================================================
-- 1. BUILDING REGISTRY — pipeline configuration per building
-- ============================================================
CREATE TABLE IF NOT EXISTS ad_building_registry (
    building_id       TEXT PRIMARY KEY,
    building_name     TEXT NOT NULL,
    building_type     TEXT NOT NULL,       -- RESIDENTIAL, INSTITUTIONAL, COMMERCIAL
    dsl_content       TEXT NOT NULL,       -- full DSL text (opaque manifest)
    output_db_path    TEXT NOT NULL,       -- relative to runtime base dir
    reference_db_path TEXT,                -- NULL for generative buildings
    is_active         INTEGER DEFAULT 1,
    seq_no            INTEGER DEFAULT 10,
    expected_elements INTEGER,
    spatial_digest    TEXT,                -- expected MD5 (NULL = don't check)
    provenance        TEXT DEFAULT 'EXTRACTED',  -- EXTRACTED | GENERATIVE
    description       TEXT
);

-- ============================================================
-- 2. BUILDING ASSERTIONS — per-building quality checks
-- ============================================================
CREATE TABLE IF NOT EXISTS ad_building_assertions (
    building_id   TEXT NOT NULL REFERENCES ad_building_registry(building_id),
    assertion_id  TEXT NOT NULL,
    element_match TEXT NOT NULL,     -- 'IfcRoof', 'IfcWall:PARTY', etc.
    property      TEXT NOT NULL,     -- 'centroidX', 'maxZ', 'count', 'vertex_count'
    operator      TEXT NOT NULL,     -- 'EQUALS', 'GREATER_THAN', 'BETWEEN', 'LESS_THAN'
    expected      TEXT NOT NULL,     -- '4.5', '0|5.5' for BETWEEN
    tolerance     REAL DEFAULT 0.001,
    PRIMARY KEY (building_id, assertion_id)
);

-- ============================================================
-- 3. SEED REGISTRY — 4 buildings, DSL content inline
-- ============================================================

-- Rosetta Stone 1: Ifc4_SampleHouse
INSERT INTO ad_building_registry (building_id, building_name, building_type, dsl_content, output_db_path, reference_db_path, is_active, seq_no, expected_elements, provenance, description)
VALUES ('Ifc4_SampleHouse', 'IFC4 Sample House', 'RESIDENTIAL',
'// Ifc4_SampleHouse — Rosetta Stone for Ifc4_SampleHouse.ifc
// Single-storey house with flat roof. 3 rooms: Living, Bedroom, Entrance hall.
// Curtain wall (glazed) on west face. Partition walls separate rooms.
// Reference: reference/rosetta/Ifc4_SampleHouse_extracted.db
//
// Phase 121: Switched to GRID layout to match reference axis orientation.
// Reference building is long along X (~14m x 5.5m). Living west, bedroom NE, entrance SE.

BUILDING "Ifc4_SampleHouse" type:SINGLE_UNIT profile:"UK_Residential" {

    GRID {
        axes: A, B, C / 1, 2, 3
        spacing: 9.3, 4.5 / 2.0, 3.5
    }

    // Computed positions:
    // X: A=0, B=9.3, C=13.8
    // Y: 1=0, 2=2.0, 3=5.5

    STOREY "Ground Floor" level:0 height:3.3m {
        LIVING "living" bounds:A1-B3 {
            exterior: west, north;
            WINDOW north;
            WINDOW north;
            WINDOW north
        }
        CORRIDOR "entrance" bounds:B1-C2 {
            adjacent: living;
            exterior: south;
            DOOR south type:D_EXT_DBL
        }
        BEDROOM "bedroom" bounds:B2-C3 {
            adjacent: entrance;
            exterior: north, east;
            WINDOW north
        }
    }

    ROOF pitch:0deg overhang:600mm
}',
'DAGCompiler/lib/output/ifc4_sample_house.db',
'DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db',
1, 10, 55, 'EXTRACTED',
'Rosetta Stone 1 — single-storey residential, flat roof, 3 rooms');

-- Rosetta Stone 2: Ifc2x3_Duplex
INSERT INTO ad_building_registry (building_id, building_name, building_type, dsl_content, output_db_path, reference_db_path, is_active, seq_no, expected_elements, provenance, description)
VALUES ('Ifc2x3_Duplex', 'IFC2x3 Duplex', 'RESIDENTIAL',
'// Ifc2x3_Duplex — Rosetta Stone MANIFEST
// Everything resolved from metadata: ad_unit_type + ad_unit_type_room
BUILDING "Ifc2x3_Duplex" type:MULTI_UNIT profile:"US_Residential" {
    UNIT "A" type:DUPLEX entry:DIRECT
    UNIT "B" type:DUPLEX entry:DIRECT
    ROOF pitch:25deg overhang:600mm
}',
'DAGCompiler/lib/output/ifc2x3_duplex.db',
'DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db',
1, 20, 1085, 'EXTRACTED',
'Rosetta Stone 2 — 2-unit side-by-side duplex, party walls');

-- Generative: TB-LKTN
INSERT INTO ad_building_registry (building_id, building_name, building_type, dsl_content, output_db_path, reference_db_path, is_active, seq_no, expected_elements, provenance, description)
VALUES ('TB_LKTN', 'TB-LKTN Rumah Rakyat', 'RESIDENTIAL',
'BUILDING "TB_LKTN"
    profile: "Malaysian_Residential"
    protocol: "Residential_Single_Storey"
    lod: 300
{
    GRID {
        axes: A, B, C, D, E / 1, 2, 3, 4, 5
        spacing: 1.3, 1.8, 3.7, 3.1 / 2.3, 3.1, 1.6, 1.5
    }

    STOREY "Ground Floor" level:0 height:3.0m {
        PORCH "anjung" bounds:C1-D2 {
            exterior: south
            roof: ATTACHED
        }

        OPEN_PLAN "common" bounds:C2-D5 {
            zones: LIVING, DINING, KITCHEN
            exterior: south
            exterior: north
        }

        BEDROOM "bilik_utama" bounds:A2-C3 {
            exterior: south
            exterior: west
            opens_to: common
        }

        TOILET "tandas" bounds:A3-B4 {
            exterior: west
            opens_to: common
        }

        BATHROOM "bilik_mandi" bounds:A4-B5 {
            exterior: west
            exterior: north
            opens_to: common
        }

        BEDROOM "bilik_2" bounds:D2-E3 {
            exterior: south
            exterior: east
            opens_to: common
        }

        BEDROOM "bilik_3" bounds:D3-E5 {
            exterior: north
            exterior: east
            opens_to: common
        }
    }

    ROOF pitch:25deg overhang:700mm
}',
'DAGCompiler/lib/output/tb_lktn.db',
NULL,
1, 30, 69, 'GENERATIVE',
'Generative proof — 9900x8500mm Rumah Rakyat, no IFC reference');

-- Rosetta Stone 3: SJTII Terminal
INSERT INTO ad_building_registry (building_id, building_name, building_type, dsl_content, output_db_path, reference_db_path, is_active, seq_no, expected_elements, provenance, description)
VALUES ('SJTII_Terminal', 'SJTII Airport Terminal', 'COMMERCIAL',
'// SJTII Terminal — 3rd Rosetta Stone (Malaysian Institutional)
// Sultan Johor Terminal II airport terminal.
// Reference: reference/rosetta/SJTII_Terminal_extracted.db (1400 ARC elements)
// Profile selects 150mm BrickPlaster walls (93% of reference walls).
// Grid layout from column grid analysis: 8m Y-axis dominant spacing.
// Storey height 4.0m (reference Z range ~4000mm per storey).

BUILDING "SJTII_Terminal" profile:"Malaysian_Institutional" {

    GRID {
        axes: A, B, C, D, E, F, G / 1, 2, 3, 4, 5, 6, 7, 8
        spacing: 10, 10, 12, 12, 10, 8 / 8, 8, 8, 8, 8, 8, 8
    }

    // Computed positions:
    // X: A=0, B=10, C=20, D=32, E=44, F=54, G=62
    // Y: 1=0, 2=8, 3=16, 4=24, 5=32, 6=40, 7=48, 8=56

    STOREY "Ground Floor" level:0 height:4.0m {

        // West wing — main hall
        LOBBY "main_lobby" bounds:A1-C3 {
            exterior: south
            exterior: west
            DOOR south; DOOR south; DOOR south
        }

        OPEN_PLAN "checkin" bounds:C1-E3 {
            exterior: south
        }

        // East wing — admin
        OFFICE "admin_1" bounds:E1-F2 { exterior: south }
        OFFICE "admin_2" bounds:F1-G2 { exterior: south; exterior: east }

        // Central corridor
        CORRIDOR "corridor_g" bounds:A3-G4 {
            exterior: west; exterior: east
        }

        // North wing — services
        TOILET_BLOCK "toilet_g1" bounds:A4-B5 { exterior: west }
        TOILET_BLOCK "toilet_g2" bounds:B4-C5 { }
        DINING "canteen" bounds:C4-E6 {
            WINDOW north; WINDOW north
        }
        STAFFROOM "staff_g" bounds:E4-F5 { }
        OFFICE "admin_3" bounds:F4-G5 { exterior: east }

        LOBBY "arrival" bounds:A5-C7 {
            exterior: west; exterior: north
        }
        OFFICE "security" bounds:E5-G6 { exterior: east }

        STAIR "stair_g" at:F3 width:1.5m to:"First Floor"
    }

    STOREY "First Floor" level:1 height:4.0m {
        LANDING "landing_1" at:F3 size:3x3m from:"stair_g"

        LOBBY "departure" bounds:A1-D3 {
            exterior: south; exterior: west
        }
        OPEN_PLAN "gates" bounds:D1-G3 {
            exterior: south; exterior: east
        }

        CORRIDOR "corridor_f" bounds:A3-G4 {
            exterior: west; exterior: east
        }

        TOILET_BLOCK "toilet_f1" bounds:A4-B5 { exterior: west }
        TOILET_BLOCK "toilet_f2" bounds:B4-C5 { }
        DINING "food_court" bounds:C4-E6 {
            WINDOW north
        }
        OFFICE "airline_1" bounds:E4-F5 { }
        OFFICE "airline_2" bounds:F4-G5 { exterior: east }

        LOBBY "lounge_f" bounds:A5-C7 {
            exterior: west; exterior: north
        }

        STAIR "stair_f" at:F3 width:1.5m to:"Second Floor"
    }

    STOREY "Second Floor" level:2 height:4.0m {
        LANDING "landing_2" at:F3 size:3x3m from:"stair_f"

        OFFICE "ops_1" bounds:A1-B2 { exterior: south; exterior: west }
        OFFICE "ops_2" bounds:B1-C2 { exterior: south }
        OFFICE "ops_3" bounds:C1-D2 { exterior: south }
        OFFICE "ops_4" bounds:D1-E2 { exterior: south }
        OFFICE "ops_5" bounds:E1-F2 { exterior: south }
        OFFICE "ops_6" bounds:F1-G2 { exterior: south; exterior: east }

        CORRIDOR "corridor_s" bounds:A2-G3 {
            exterior: west; exterior: east
        }

        TOILET_BLOCK "toilet_s1" bounds:A3-B4 { exterior: west }
        STAFFROOM "staff_s" bounds:B3-C4 { }
        LOBBY "waiting_s" bounds:C3-E4 {
            WINDOW north
        }

        STAIR "stair_s" at:F3 width:1.5m to:"Third Floor"
    }

    STOREY "Third Floor" level:3 height:4.0m {
        LANDING "landing_3" at:F3 size:3x3m from:"stair_s"

        OFFICE "exec_1" bounds:A1-C2 { exterior: south; exterior: west }
        OFFICE "exec_2" bounds:C1-E2 { exterior: south }
        OFFICE "exec_3" bounds:E1-G2 { exterior: south; exterior: east }

        CORRIDOR "corridor_t" bounds:A2-G3 {
            exterior: west; exterior: east
        }

        TOILET_BLOCK "toilet_t1" bounds:A3-B4 { exterior: west }
        LOBBY "vip_lounge" bounds:B3-D4 {
            WINDOW north
        }
    }

    ROOF pitch:0deg overhang:600mm
}',
'DAGCompiler/lib/output/sjtii_terminal.db',
'DAGCompiler/lib/input/Terminal_Extracted.db',
1, 40, 51088, 'EXTRACTED',
'Rosetta Stone 3 — 4-storey airport terminal');

-- ============================================================
-- 4. SEED ASSERTIONS (known building-specific quality checks)
-- ============================================================

-- TB-LKTN: roof is gable prism with appropriate height
INSERT INTO ad_building_assertions (building_id, assertion_id, element_match, property, operator, expected, tolerance)
VALUES
    ('TB_LKTN', 'A01_ROOF_MAXZ',    'IfcRoof', 'maxZ',         'BETWEEN', '4.0|5.5', 0.001),
    ('TB_LKTN', 'A02_ROOF_VERTS',   'IfcRoof', 'vertex_count', 'GREATER_THAN', '5', 0.0);

-- Duplex: must have party walls
INSERT INTO ad_building_assertions (building_id, assertion_id, element_match, property, operator, expected, tolerance)
VALUES
    ('Ifc2x3_Duplex', 'A01_PARTY_WALLS', 'IfcWall:Party', 'count', 'GREATER_THAN', '0', 0.0);
