#!/usr/bin/env python3
"""
RosettaStoneToBOM -- Source of truth for BOM.db.

Creates BOM.db from scratch: schema + all reference data + static BOMs,
then calls RosettaStoneExtract for extracted building elements (SH + DX).
Per DATA_MODEL.md S1.6: one script, one DB, fully reproducible.

Usage:
    python scripts/RosettaStoneToBOM.py

Developer Notes:
    After regeneration, verify via ./scripts/run_RosettaStones.sh
    Test result logs at logs/run_RosettaStones_{YYYYMMDD}_{HHMMSS}.txt
    Full gate logs at logs/run_tests_{YYYYMMDD}_{HHMMSS}.txt
"""

import sqlite3
import os
import sys
import subprocess
import yaml

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
LIB_DIR = os.path.join(SCRIPT_DIR, "..", "library")
BOM_DB = os.path.join(LIB_DIR, "BOM.db")
COMP_DB = os.path.join(LIB_DIR, "component_library.db")
SCHEMA_SQL = os.path.join(LIB_DIR, "schema_snapshot_bom.sql")

# =============================================================================
# DSL Content (multi-line strings)
# =============================================================================
DSL_RE_SH = '// Ifc4_SampleHouse — Rosetta Stone for Ifc4_SampleHouse.ifc\n// Single-storey house with flat roof. 3 rooms: Living, Bedroom, Entrance hall.\n// Curtain wall (glazed) on west face. Partition walls separate rooms.\n// Reference: reference/rosetta/Ifc4_SampleHouse_extracted.db\n//\n// Phase 121: Switched to GRID layout to match reference axis orientation.\n// Reference building is long along X (~14m x 5.5m). Living west, bedroom NE, entrance SE.\n\nBUILDING "Ifc4_SampleHouse" type:SINGLE_UNIT profile:"UK_Residential" {\n\n    GRID {\n        axes: A, B, C / 1, 2, 3\n        spacing: 9.3, 4.5 / 2.0, 3.5\n    }\n\n    // Computed positions:\n    // X: A=0, B=9.3, C=13.8\n    // Y: 1=0, 2=2.0, 3=5.5\n\n    STOREY "Ground Floor" level:0 height:3.3m {\n        LIVING "living" bounds:A1-B3 {\n            exterior: west, north;\n            WINDOW north;\n            WINDOW north;\n            WINDOW north\n        }\n        CORRIDOR "entrance" bounds:B1-C2 {\n            adjacent: living;\n            exterior: south;\n            DOOR south type:D_EXT_DBL\n        }\n        BEDROOM "bedroom" bounds:B2-C3 {\n            adjacent: entrance;\n            exterior: north, east;\n            WINDOW north\n        }\n    }\n\n    ROOF pitch:0deg overhang:600mm\n}'

DSL_RE_DX = '// Ifc2x3_Duplex — Rosetta Stone MANIFEST\n// Everything resolved from metadata: ad_unit_type + ad_unit_type_room\nBUILDING "Ifc2x3_Duplex" type:MULTI_UNIT profile:"US_Residential" {\n    UNIT "A" type:DUPLEX entry:DIRECT\n    UNIT "B" type:DUPLEX entry:DIRECT\n    ROOF pitch:25deg overhang:600mm\n}'

DSL_RE_TB = 'BUILDING "TB_LKTN"\n    profile: "Malaysian_Residential"\n    protocol: "Residential_Single_Storey"\n    lod: 300\n{\n    GRID {\n        axes: A, B, C, D, E / 1, 2, 3, 4, 5\n        spacing: 1.3, 1.8, 3.7, 3.1 / 2.3, 3.1, 1.6, 1.5\n    }\n\n    STOREY "Ground Floor" level:0 height:3.0m {\n        PORCH "anjung" bounds:C1-D2 {\n            exterior: south\n            roof: ATTACHED\n        }\n\n        OPEN_PLAN "common" bounds:C2-D5 {\n            zones: LIVING, DINING, KITCHEN\n            exterior: south\n            exterior: north\n        }\n\n        BEDROOM "bilik_utama" bounds:A2-C3 {\n            exterior: south\n            exterior: west\n            opens_to: common\n        }\n\n        TOILET "tandas" bounds:A3-B4 {\n            exterior: west\n            opens_to: common\n        }\n\n        BATHROOM "bilik_mandi" bounds:A4-B5 {\n            exterior: west\n            exterior: north\n            opens_to: common\n        }\n\n        BEDROOM "bilik_2" bounds:D2-E3 {\n            exterior: south\n            exterior: east\n            opens_to: common\n        }\n\n        BEDROOM "bilik_3" bounds:D3-E5 {\n            exterior: north\n            exterior: east\n            opens_to: common\n        }\n    }\n\n    ROOF pitch:25deg overhang:700mm\n}'

DSL_CO_TE = '// SJTII Terminal — 3rd Rosetta Stone (Malaysian Institutional)\n// Sultan Johor Terminal II airport terminal.\n// Reference: reference/rosetta/SJTII_Terminal_extracted.db (1400 ARC elements)\n// Profile selects 150mm BrickPlaster walls (93% of reference walls).\n// Grid layout from column grid analysis: 8m Y-axis dominant spacing.\n// Storey height 4.0m (reference Z range ~4000mm per storey).\n\nBUILDING "SJTII_Terminal" profile:"Malaysian_Institutional" {\n\n    GRID {\n        axes: A, B, C, D, E, F, G / 1, 2, 3, 4, 5, 6, 7, 8\n        spacing: 10, 10, 12, 12, 10, 8 / 8, 8, 8, 8, 8, 8, 8\n    }\n\n    // Computed positions:\n    // X: A=0, B=10, C=20, D=32, E=44, F=54, G=62\n    // Y: 1=0, 2=8, 3=16, 4=24, 5=32, 6=40, 7=48, 8=56\n\n    STOREY "Ground Floor" level:0 height:4.0m {\n\n        // West wing — main hall\n        LOBBY "main_lobby" bounds:A1-C3 {\n            exterior: south\n            exterior: west\n            DOOR south; DOOR south; DOOR south\n        }\n\n        OPEN_PLAN "checkin" bounds:C1-E3 {\n            exterior: south\n        }\n\n        // East wing — admin\n        OFFICE "admin_1" bounds:E1-F2 { exterior: south }\n        OFFICE "admin_2" bounds:F1-G2 { exterior: south; exterior: east }\n\n        // Central corridor\n        CORRIDOR "corridor_g" bounds:A3-G4 {\n            exterior: west; exterior: east\n        }\n\n        // North wing — services\n        TOILET_BLOCK "toilet_g1" bounds:A4-B5 { exterior: west }\n        TOILET_BLOCK "toilet_g2" bounds:B4-C5 { }\n        DINING "canteen" bounds:C4-E6 {\n            WINDOW north; WINDOW north\n        }\n        STAFFROOM "staff_g" bounds:E4-F5 { }\n        OFFICE "admin_3" bounds:F4-G5 { exterior: east }\n\n        LOBBY "arrival" bounds:A5-C7 {\n            exterior: west; exterior: north\n        }\n        OFFICE "security" bounds:E5-G6 { exterior: east }\n\n        STAIR "stair_g" at:F3 width:1.5m to:"First Floor"\n    }\n\n    STOREY "First Floor" level:1 height:4.0m {\n        LANDING "landing_1" at:F3 size:3x3m from:"stair_g"\n\n        LOBBY "departure" bounds:A1-D3 {\n            exterior: south; exterior: west\n        }\n        OPEN_PLAN "gates" bounds:D1-G3 {\n            exterior: south; exterior: east\n        }\n\n        CORRIDOR "corridor_f" bounds:A3-G4 {\n            exterior: west; exterior: east\n        }\n\n        TOILET_BLOCK "toilet_f1" bounds:A4-B5 { exterior: west }\n        TOILET_BLOCK "toilet_f2" bounds:B4-C5 { }\n        DINING "food_court" bounds:C4-E6 {\n            WINDOW north\n        }\n        OFFICE "airline_1" bounds:E4-F5 { }\n        OFFICE "airline_2" bounds:F4-G5 { exterior: east }\n\n        LOBBY "lounge_f" bounds:A5-C7 {\n            exterior: west; exterior: north\n        }\n\n        STAIR "stair_f" at:F3 width:1.5m to:"Second Floor"\n    }\n\n    STOREY "Second Floor" level:2 height:4.0m {\n        LANDING "landing_2" at:F3 size:3x3m from:"stair_f"\n\n        OFFICE "ops_1" bounds:A1-B2 { exterior: south; exterior: west }\n        OFFICE "ops_2" bounds:B1-C2 { exterior: south }\n        OFFICE "ops_3" bounds:C1-D2 { exterior: south }\n        OFFICE "ops_4" bounds:D1-E2 { exterior: south }\n        OFFICE "ops_5" bounds:E1-F2 { exterior: south }\n        OFFICE "ops_6" bounds:F1-G2 { exterior: south; exterior: east }\n\n        CORRIDOR "corridor_s" bounds:A2-G3 {\n            exterior: west; exterior: east\n        }\n\n        TOILET_BLOCK "toilet_s1" bounds:A3-B4 { exterior: west }\n        STAFFROOM "staff_s" bounds:B3-C4 { }\n        LOBBY "waiting_s" bounds:C3-E4 {\n            WINDOW north\n        }\n\n        STAIR "stair_s" at:F3 width:1.5m to:"Third Floor"\n    }\n\n    STOREY "Third Floor" level:3 height:4.0m {\n        LANDING "landing_3" at:F3 size:3x3m from:"stair_s"\n\n        OFFICE "exec_1" bounds:A1-C2 { exterior: south; exterior: west }\n        OFFICE "exec_2" bounds:C1-E2 { exterior: south }\n        OFFICE "exec_3" bounds:E1-G2 { exterior: south; exterior: east }\n\n        CORRIDOR "corridor_t" bounds:A2-G3 {\n            exterior: west; exterior: east\n        }\n\n        TOILET_BLOCK "toilet_t1" bounds:A3-B4 { exterior: west }\n        LOBBY "vip_lounge" bounds:B3-D4 {\n            WINDOW north\n        }\n    }\n\n    ROOF pitch:0deg overhang:600mm\n}'

DSL_ST_SH = None

DSL_ST_DX = None

# =============================================================================
# Reference Data
# =============================================================================

# DSL content keyed by C_DocType_ID (stays in Python — too large for YAML)
_DSL_CONTENT = {
    'RE_SH': DSL_RE_SH,
    'RE_DX': DSL_RE_DX,
    'RE_TB': DSL_RE_TB,
    'CO_TE': DSL_CO_TE,
    'ST_SH': DSL_ST_SH,
    'ST_DX': DSL_ST_DX,
}

MANIFEST_PATH = os.path.join(SCRIPT_DIR, 'construction_manifest.yaml')


def _build_c_doctype():
    """Build C_DOCTYPE from construction_manifest.yaml + DSL constants."""
    with open(MANIFEST_PATH) as f:
        manifest = yaml.safe_load(f)
    rows = []
    for project_name, cfg in manifest['buildings'].items():
        doc_type_id = cfg['doc_type_id']
        has_output = cfg.get('output_path') is not None
        rows.append((
            doc_type_id,
            cfg['name'],
            cfg['doc_base_type'],
            cfg['doc_sub_type'],
            0,  # IsDefault
            1,  # IsActive
            cfg['description'],
            project_name,
            _DSL_CONTENT.get(doc_type_id),
            cfg.get('output_path'),
            cfg.get('reference_path'),
            cfg.get('expected_elements'),
            cfg['provenance'],
            cfg.get('geometry_fail_threshold', 0),
            cfg['seq_no'],
            None,  # AabbWidthMm — derived by extraction
            None,  # AabbDepthMm
            None,  # AabbHeightMm
            cfg.get('climate'),
            100 if has_output else None,  # SalesRep_ID
        ))
    return rows


C_DOCTYPE = _build_c_doctype()

C_BPARTNER = [
    ('DX', 'Duplex', 'Duplex', 'Two-unit side-by-side extraction', 1),
    ('MY', 'Malaysian', 'Malaysian Residential', 'Malaysian residential building pattern', 1),
    ('SH', 'SampleHouse', 'Sample House', 'Single-storey residential extraction', 1),
    ('ST', 'Standard', 'Standard Mode', 'Owner-agnostic template-driven compilation', 1),
    ('TB', 'TerraceBlock', 'Terrace Block', 'Malaysian single-storey terrace', 1),
    ('TE', 'Terminal', 'Terminal', 'Multi-storey institutional extraction', 1),
]

AD_SYSCONFIG = [
    ('exclude_ifc_class', 'IfcOpeningElement', 'Boolean void placeholder — not a physical element. Doors/windows already counted.', 1),
    ('closest_fit', 'true', None, 1),
]

M_BOMCATEGORY = [
    ('BD', 'Bedroom', 'Bedroom settings', 1, None, 'Bedroom', 0, 0, 0, 'RE', None),
    ('BT', 'Bathroom', 'Bathroom/toilet settings', 1, None, 'Bathroom', 0, 0, 0, 'RE', None),
    ('CW', 'Curtain Wall', 'SH curtain wall / unknown storey extraction', 1, None, 'CurtainWall', 0, 0, 0, None, None),
    ('DN', 'Dining', 'Dining settings', 1, None, 'Dining', 0, 0, 0, 'RE', None),
    ('FN', 'Foundation', 'DX foundation / T-FDN storey extraction', 1, None, 'Foundation', 0, 0, 0, None, None),
    ('FR', 'Furniture', 'Leaf furniture items (~4th BOM layer)', 1, None, 'Furniture', 0, 0, 0, None, None),
    ('GF', 'Ground Floor', 'Habitable body between roof and slab', 1, None, 'GroundFloor', 0, 0, 0, None, None),
    ('HU', 'Half-Unit', 'Single half-unit of a duplex (one complete dwelling)', 1, None, 'HalfUnit', 0, 0, 0, 'RE', None),
    ('KA', 'Kitchen Unit A', 'DX unit-specific kitchen A (KITCHEN_CABINET_SET_DX_A)', 0, None, 'KitchenA', 0, 0, 0, 'RE', None),
    ('KB', 'Kitchen Unit B', 'DX unit-specific kitchen B (KITCHEN_CABINET_SET_DX_B)', 0, None, 'KitchenB', 0, 0, 0, 'RE', None),
    ('KT', 'Kitchen', 'Kitchen settings', 1, None, 'Kitchen', 0, 0, 0, 'RE', None),
    ('L1', 'Level 1', 'Ground floor assembly', 1, None, 'Level1', 0, 0, 0, None, None),
    ('L2', 'Level 2', 'Upper floor assembly', 1, None, 'Level2', 0, 0, 0, None, None),
    ('LI', 'Living', 'Living room settings', 1, None, 'Living', 0, 0, 0, 'RE', None),
    ('LI_DX', 'Living Room (DX)', 'Duplex living room — ~3000x4000mm', 1, None, 'LivingRoom_DX', 3332, 3943, 2800, 'RE', None),
    ('LI_SH', 'Living Room (SH)', 'SampleHouse living room — 8869x4690mm', 1, None, 'LivingRoom_SH', 8869, 4690, 2700, 'RE', None),
    ('MP', 'MEP', 'Mechanical, Electrical, Plumbing trunk/service group', 1, None, 'MEP', 0, 0, 0, None, None),
    ('MS', 'Miscellaneous', 'DX miscellaneous / unknown storey extraction', 1, None, 'Misc', 0, 0, 0, None, None),
    ('PH', 'Porch', 'Porch/canopy modules', 1, None, 'Porch', 0, 0, 0, 'RE', None),
    ('PR', 'Pair', 'Duplex unit pair container (two mirrored half-units)', 1, None, 'Pair', 0, 0, 0, 'RE', None),
    ('RE', 'Residential Template', 'Standard residential decomposition template', 1, None, 'ResidentialTemplate', 0, 0, 0, 'RE', None),
    ('RF', 'Roof', 'Roof assemblies', 1, None, 'Roof', 0, 0, 0, None, None),
    ('SL', 'Slab', 'Floor slab assemblies', 1, None, 'Slab', 0, 0, 0, None, None),
    ('ST', 'Space', 'Buffer/empty space (variable AABB)', 1, None, 'Space', 0, 0, 0, None, None),
    ('ST-DX', 'Standard - AABB DX', None, 1, None, 'ST-DX', 9215, 26565, 7885, 'RE', 'ST'),
    ('ST-SH', 'Standard - AABB SH', None, 1, None, 'ST-SH', 16868, 8668, 3945, 'RE', 'ST'),
    ('UN', 'Unit', 'Deprecated — too generic (Object.class). Use template root category (RE, CO, IN) instead.', 0, None, 'Unit', 0, 0, 0, None, None),
    ('WL', 'Wall', 'External/internal wall assemblies', 1, None, 'Wall', 0, 0, 0, None, None),
]

M_PRODUCT_CATEGORY = [
    ('ASM_SET', 'Set', 'BOM assembly group', 'ASM', None, 10, 1),
    ('IFC_DOOR', 'Door', 'Hinged, sliding, folding', 'ARC', 'IfcDoor', 10, 1),
    ('IFC_FLOWSEGMENT', 'Pipe / Duct Segment', 'Straight pipe or duct run', 'MEP', 'IfcFlowSegment', 10, 1),
    ('IFC_WALL', 'Wall', 'Vertical enclosure', 'STR', 'IfcWall', 10, 1),
    ('STR', 'Structural', 'Load-bearing structure: walls, slabs, beams, columns, members, plates', None, None, 10, 1),
    ('IFC_PIPESEGMENT', 'Pipe Segment', 'Pipe run (IFC2x3 specific)', 'MEP', 'IfcPipeSegment', 11, 1),
    ('ASM_PHANTOM', 'Phantom', 'Phantom BOM (pass-through)', 'ASM', None, 20, 1),
    ('IFC_FLOWFITTING', 'Pipe / Duct Fitting', 'Elbow, tee, reducer, coupling', 'MEP', 'IfcFlowFitting', 20, 1),
    ('IFC_SLAB', 'Slab', 'Horizontal plate (floor/roof)', 'STR', 'IfcSlab', 20, 1),
    ('IFC_WINDOW', 'Window', 'Fixed, casement, sliding', 'ARC', 'IfcWindow', 20, 1),
    ('MEP', 'MEP', 'Mechanical, Electrical, Plumbing: pipes, fittings, terminals, devices', None, None, 20, 1),
    ('IFC_PIPEFITTING', 'Pipe Fitting', 'Pipe fitting (IFC2x3 specific)', 'MEP', 'IfcPipeFitting', 21, 1),
    ('ARC', 'Architectural', 'Architectural elements: doors, windows, furniture, railings, stairs', None, None, 30, 1),
    ('ASM_FLOOR', 'Floor', 'Floor-level assembly', 'ASM', None, 30, 1),
    ('IFC_BEAM', 'Beam', 'Horizontal structural member', 'STR', 'IfcBeam', 30, 1),
    ('IFC_FLOWCONTROLLER', 'Flow Controller', 'Valve, damper, detector', 'MEP', 'IfcFlowController', 30, 1),
    ('IFC_FURNISHING', 'Furnishing Element', 'Furniture, equipment (IFC4)', 'ARC', 'IfcFurnishingElement', 30, 1),
    ('IFC_FURNITURE', 'Furniture', 'Furniture (IFC2x3)', 'ARC', 'IfcFurniture', 31, 1),
    ('ASM', 'Assembly', 'Non-physical: sets, phantoms, floors, BOMs — assembly groupings', None, None, 40, 1),
    ('IFC_COLUMN', 'Column', 'Vertical structural member', 'STR', 'IfcColumn', 40, 1),
    ('IFC_FLOWTERMINAL', 'Flow Terminal', 'Fixture, outlet, drain, tap', 'MEP', 'IfcFlowTerminal', 40, 1),
    ('IFC_RAILING', 'Railing', 'Guard rail, handrail, balustrade', 'ARC', 'IfcRailing', 40, 1),
    ('IFC_DISCRETEACC', 'Discrete Accessory', 'Bracket, anchor, support', 'ARC', 'IfcDiscreteAccessory', 50, 1),
    ('IFC_LIGHTFIXTURE', 'Light Fixture', 'Luminaire', 'MEP', 'IfcLightFixture', 50, 1),
    ('IFC_MEMBER', 'Member', 'Generic structural member', 'STR', 'IfcMember', 50, 1),
    ('IFC_BLDGELEMPROXY', 'Building Element Proxy', 'Generic/unclassified element', 'ARC', 'IfcBuildingElementProxy', 60, 1),
    ('IFC_OUTLET', 'Electrical Outlet', 'Power/data outlet', 'MEP', 'IfcOutlet', 60, 1),
    ('IFC_PLATE', 'Plate', 'Thin structural plate', 'STR', 'IfcPlate', 60, 1),
    ('IFC_ROOF', 'Roof', 'Roof element', 'STR', 'IfcRoof', 70, 1),
    ('IFC_SWITCHDEVICE', 'Switching Device', 'Switch, dimmer, sensor', 'MEP', 'IfcSwitchingDevice', 70, 1),
    ('IFC_AIRTERMINAL', 'Air Terminal', 'Diffuser, grille, register', 'MEP', 'IfcAirTerminal', 80, 1),
    ('IFC_STAIR', 'Stair', 'Stair assembly', 'STR', 'IfcStair', 80, 1),
    ('IFC_STAIRFLIGHT', 'Stair Flight', 'Single run of stairs', 'STR', 'IfcStairFlight', 81, 1),
    ('IFC_FAN', 'Fan', 'Exhaust/supply fan', 'MEP', 'IfcFan', 90, 1),
    ('IFC_FIRESUPPTERM', 'Fire Suppression Terminal', 'Sprinkler head, nozzle', 'MEP', 'IfcFireSuppressionTerminal', 100, 1),
    ('IFC_SANITARYTERM', 'Sanitary Terminal', 'WC, basin, bath, shower', 'MEP', 'IfcSanitaryTerminal', 110, 1),
]

M_PRODUCT = [
    ('BACKFLOW_PREVENTER_25MM', 'VALVE', 0.095, 0.125, 0.36, 'IfcFlowController', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWCONTROLLER', 1),
    ('BALL_VALVE_50MM', 'VALVE', 0.08, 0.139, 0.216, 'IfcFlowController', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWCONTROLLER', 1),
    ('BATH_TUB_1525', 'FIXTURE', 0.578, 0.76, 1.525, 'IfcFlowTerminal', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWTERMINAL', 1),
    ('BEAM_W310X60', 'STRUCTURAL', 0.203, 0.303, 7.421, 'IfcBeam', 'Ifc2x3_Duplex', None, None, 'IFC_BEAM', 1),
    ('BEAM_W410X60', 'STRUCTURAL', 0.407, 1.492, 3.487, 'IfcBeam', 'Ifc2x3_Duplex', None, None, 'IFC_BEAM', 1),
    ('BED_SET', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('BED_SET_MASTER', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('BUFFER', 'PHANTOM', 0.001, 0.001, 0.001, None, 'PHANTOM', None, None, 'ASM_PHANTOM', 0),
    ('Base_Cabinet', 'FURNITURE', 0.6, 0.6, 0.9, 'IfcFurniture', 'EXTRACTED', 'Wood - Birch', '0.871,0.722,0.529,1.000', 'IFC_FURNITURE', 1),
    ('Bed_King', 'FURNITURE', 2.032, 1.981, 0.5, None, 'Ifc2x3_Duplex', 'Textile - White', '0.961,0.961,0.961,1.000', 'IFC_FURNITURE', 1),
    ('Bed_Queen', 'FURNITURE', 1.5, 2.0, 0.5, None, 'Ifc2x3_Duplex', 'Textile - White', '0.961,0.961,0.961,1.000', 'IFC_FURNITURE', 1),
    ('COFFEE_TABLE_RECT', 'FURNITURE', 0.457, 0.915, 1.83, 'IfcFurnishingElement', 'Ifc2x3_Duplex', None, None, 'IFC_FURNISHING', 1),
    ('CONDUIT_ELBOW_STEEL', 'CONDUIT', 0.116, 0.136, 0.154, 'IfcFlowSegment', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWSEGMENT', 1),
    ('CONDUIT_EMT_30MM', 'CONDUIT', 0.03, 0.03, 0.03, 'IfcFlowSegment', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWSEGMENT', 1),
    ('CORE_ASSEMBLY', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('CORRIDOR', 'PHANTOM', 0.001, 0.001, 0.001, None, 'PHANTOM', None, None, 'ASM_PHANTOM', 0),
    ('COUNTER_TOP_600', 'FURNITURE', 0.142, 0.625, 2.512, 'IfcFurnishingElement', 'Ifc2x3_Duplex', None, None, 'IFC_FURNISHING', 1),
    ('COUNTER_TOP_SINK_600', 'FURNITURE', 0.142, 0.625, 3.0, 'IfcFurnishingElement', 'Ifc2x3_Duplex', None, None, 'IFC_FURNISHING', 1),
    ('Coffee_Table', 'FURNITURE', 1.0, 0.6, 0.45, 'IfcFurniture', 'EXTRACTED', 'Wood - Mahogany', '0.600,0.400,0.200,1.000', 'IFC_FURNITURE', 1),
    ('Counter_Top', 'FURNITURE', 0.6, 0.6, 0.05, None, 'EXTRACTED', 'Laminate, Ivory, Matte', '1.000,1.000,0.941,1.000', 'IFC_FURNITURE', 1),
    ('DINING_SET', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('DOOR_ASSEMBLY', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('DOOR_D1', 'DOOR', 0.9, 0.045, 2.1, None, 'EXTRACTED', None, None, 'IFC_DOOR', 1),
    ('DOOR_D1_DOUBLE', 'DOOR', 1.125, 0.045, 2.1, None, 'EXTRACTED', None, None, 'IFC_DOOR', 1),
    ('DOOR_D2', 'DOOR', 0.8, 0.045, 2.1, None, 'EXTRACTED', None, None, 'IFC_DOOR', 1),
    ('DOOR_D3', 'DOOR', 0.7, 0.045, 2.0, None, 'EXTRACTED', None, None, 'IFC_DOOR', 1),
    ('DOOR_DUPLEX_ENTRY', 'DOOR', 1.25, 0.467, 2.01, None, 'Ifc2x3_Duplex', None, None, 'IFC_DOOR', 1),
    ('DOOR_DUPLEX_GLASS', 'DOOR', 0.813, 0.467, 2.42, None, 'Ifc2x3_Duplex', None, None, 'IFC_DOOR', 1),
    ('DOOR_FLUSH_762', 'DOOR', 0.544, 0.544, 2.108, 'IfcDoor', 'Ifc2x3_Duplex', None, None, 'IFC_DOOR', 1),
    ('DOOR_FLUSH_864', 'DOOR', 0.455, 0.735, 2.108, 'IfcDoor', 'Ifc2x3_Duplex', None, None, 'IFC_DOOR', 1),
    ('DOOR_SH_EXT_DBL', 'DOOR', 0.199, 1.86, 2.11, 'IfcDoor', 'Ifc4_SampleHouse', None, None, 'IFC_DOOR', 1),
    ('DOOR_SH_INT_SGL', 'DOOR', 0.178, 0.88, 2.145, 'IfcDoor', 'Ifc4_SampleHouse', None, None, 'IFC_DOOR', 1),
    ('DRAIN_HALFROUND_MY', 'DRAINAGE', 0.23, 1.0, 0.115, None, 'PENDING', None, None, 'IFC_FLOWTERMINAL', 1),
    ('DUCT_ROUND_250MM', 'DUCT', 0.25, 0.25, 0.25, 'IfcFlowSegment', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWSEGMENT', 1),
    ('DUPLEX_BATHROOM_SET', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('DUPLEX_MEP_TRUNK_STD', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('DUPLEX_SET_STD', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('DUPLEX_SINGLE_UNIT_STD', 'FLOOR', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_FLOOR', 0),
    ('Desk', 'FURNITURE', 1.2, 0.6, 0.75, None, 'EXTRACTED', 'Wood - Birch', '0.871,0.722,0.529,1.000', 'IFC_FURNITURE', 1),
    ('Dining_Chair', 'FURNITURE', 0.45, 0.45, 0.45, 'IfcFurniture', 'EXTRACTED', 'Wood - Birch', '0.871,0.722,0.529,1.000', 'IFC_FURNITURE', 1),
    ('Dining_Table_With_Chairs', 'FURNITURE', 1.5, 0.9, 0.75, 'IfcFurniture', 'EXTRACTED', 'Wood - Birch', '0.871,0.722,0.529,1.000', 'IFC_FURNITURE', 1),
    ('EAST_UNITS', 'PHANTOM', 0.001, 0.001, 0.001, None, 'PHANTOM', None, None, 'ASM_PHANTOM', 0),
    ('ELEC_LIGHT', 'ELECTRICAL', 0.3, 0.3, 0.1, None, 'EXTRACTED', None, None, 'IFC_LIGHTFIXTURE', 1),
    ('ELEC_OUTLET', 'ELECTRICAL', 0.085, 0.04, 0.085, None, 'Ifc2x3_Duplex', None, None, 'IFC_LIGHTFIXTURE', 1),
    ('ELEC_SWITCH', 'ELECTRICAL', 0.085, 0.04, 0.085, None, 'Ifc2x3_Duplex', None, None, 'IFC_LIGHTFIXTURE', 1),
    ('FIRE_ALARM_PANEL', 'FP', 0.138, 0.4, 0.475, 'IfcFlowTerminal', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWTERMINAL', 1),
    ('FITTING_BEND_PVC_DWV', 'FITTING', 0.066, 0.072, 0.077, 'IfcFlowFitting', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWFITTING', 1),
    ('FITTING_ELBOW_GENERIC', 'FITTING', 0.033, 0.033, 0.034, 'IfcFlowFitting', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWFITTING', 1),
    ('FITTING_RECT_ROUND_45', 'FITTING', 0.063, 0.25, 0.25, 'IfcFlowFitting', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWFITTING', 1),
    ('FITTING_ROOF_DRAIN_380', 'FITTING', 0.3, 0.3, 0.585, 'IfcFlowFitting', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWFITTING', 1),
    ('FITTING_TEE_GENERIC', 'FITTING', 0.045, 0.048, 0.059, 'IfcFlowFitting', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWFITTING', 1),
    ('FITTING_TRANSITION_GENERIC', 'FITTING', 0.025, 0.03, 0.033, 'IfcFlowFitting', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWFITTING', 1),
    ('FIXTURE_SHOWER', 'FIXTURE', 0.9, 0.9, 2.1, 'IfcFlowTerminal', 'Ifc2x3_Duplex', 'Ceramic', '0.950,0.950,0.950,1.000', 'IFC_FLOWTERMINAL', 1),
    ('FIXTURE_SINK', 'FIXTURE', 0.5, 0.45, 0.2, 'IfcFlowTerminal', 'EXTRACTED', 'Ceramic', '0.950,0.950,0.950,1.000', 'IFC_FLOWTERMINAL', 1),
    ('FIXTURE_TOILET', 'FIXTURE', 0.4, 0.7, 0.4, 'IfcFlowTerminal', 'Ifc2x3_Duplex', 'Ceramic', '0.950,0.950,0.950,1.000', 'IFC_FLOWTERMINAL', 1),
    ('FIXTURE_WATER_HEATER', 'FIXTURE', 0.55, 0.25, 0.55, None, 'EXTRACTED', 'Metal - Steel', '0.753,0.753,0.753,1.000', 'IFC_SANITARYTERM', 1),
    ('FLOOR_DX_L1_STD', 'FLOOR', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_FLOOR', 0),
    ('FLOOR_DX_L2_STD', 'FLOOR', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_FLOOR', 0),
    ('FLOOR_SH_GF_STD', 'FLOOR', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_FLOOR', 0),
    ('FLOOR_SLAB_GF', 'FLOOR', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_FLOOR', 0),
    ('FLOOR_SLAB_L2', 'FLOOR', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_FLOOR', 0),
    ('FLOOR_TBLKTN_GF_STD', 'FLOOR', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_FLOOR', 0),
    ('FP_SMOKE', 'FP', 0.12, 0.12, 0.05, None, 'Ifc2x3_Duplex', None, None, 'IFC_FIRESUPPTERM', 1),
    ('FP_SPRINKLER', 'FP', 0.1, 0.1, 0.15, None, 'EXTRACTED', None, None, 'IFC_FIRESUPPTERM', 1),
    ('FURN_ARMCHAIR_VIPER', 'FURNITURE', 1.426, 1.395, 0.918, 'IfcFurnishingElement', 'Ifc4_SampleHouse', None, None, 'IFC_FURNISHING', 1),
    ('FURN_BED_DOUBLE', 'FURNITURE', 1.5, 2.0, 0.5, None, 'EXTRACTED', 'Textile - White', '0.961,0.961,0.961,1.000', 'IFC_FURNITURE', 1),
    ('FURN_BED_KING', 'FURNITURE', 2.032, 1.981, 0.5, None, 'EXTRACTED', 'Textile - White', '0.961,0.961,0.961,1.000', 'IFC_FURNITURE', 1),
    ('FURN_BED_SINGLE', 'FURNITURE', 0.9, 1.9, 0.5, None, 'EXTRACTED', 'Textile - White', '0.961,0.961,0.961,1.000', 'IFC_FURNITURE', 1),
    ('FURN_COFFEE_TABLE', 'FURNITURE', 1.0, 0.6, 0.45, None, 'EXTRACTED', 'Wood - Mahogany', '0.600,0.400,0.200,1.000', 'IFC_FURNITURE', 1),
    ('FURN_DESK', 'FURNITURE', 1.525, 0.762, 0.75, None, 'EXTRACTED', 'Wood - Birch', '0.871,0.722,0.529,1.000', 'IFC_FURNITURE', 1),
    ('FURN_DINING_CHAIR', 'FURNITURE', 0.45, 0.45, 0.45, None, 'EXTRACTED', 'Wood - Birch', '0.871,0.722,0.529,1.000', 'IFC_FURNITURE', 1),
    ('FURN_DINING_TABLE', 'FURNITURE', 1.5, 0.9, 0.75, None, 'EXTRACTED', 'Wood - Birch', '0.871,0.722,0.529,1.000', 'IFC_FURNITURE', 1),
    ('FURN_KITCHEN_BASE', 'FURNITURE', 1.0, 0.625, 0.86, None, 'Ifc2x3_Duplex', 'Laminate, Ivory, Matte', '1.000,1.000,0.941,1.000', 'IFC_FURNITURE', 1),
    ('FURN_KITCHEN_COUNTER', 'FURNITURE', 3.0, 0.625, 0.86, None, 'EXTRACTED', 'Laminate, Ivory, Matte', '1.000,1.000,0.941,1.000', 'IFC_FURNITURE', 1),
    ('FURN_KITCHEN_UPPER', 'FURNITURE', 1.0, 0.344, 1.4, None, 'EXTRACTED', 'Laminate, Ivory, Matte', '1.000,1.000,0.941,1.000', 'IFC_FURNITURE', 1),
    ('FURN_PIANO', 'FURNITURE', 1.371, 0.6, 1.17, None, 'EXTRACTED', 'Wood - Mahogany', '0.600,0.400,0.200,1.000', 'IFC_FURNITURE', 1),
    ('FURN_SOFA', 'FURNITURE', 2.0, 0.8, 0.45, None, 'EXTRACTED', 'Textile - White', '0.961,0.961,0.961,1.000', 'IFC_FURNITURE', 1),
    ('FURN_TV_UNIT', 'FURNITURE', 1.2, 0.6, 0.45, None, 'EXTRACTED', 'Wood - Birch', '0.871,0.722,0.529,1.000', 'IFC_FURNITURE', 1),
    ('FURN_WARDROBE', 'FURNITURE', 1.2, 0.6, 2.1, None, 'EXTRACTED', 'Wood - Birch', '0.871,0.722,0.529,1.000', 'IFC_FURNITURE', 1),
    ('GABLE_PORCH_MY', 'MESH_ROOF', 5.1, 3.6, 0.863, None, 'EXTRACTED_TBLKTN', None, None, 'IFC_ROOF', 1),
    ('HIP_ROOF_MY', 'MESH_ROOF', 9.9, 5.4, 1.259, None, 'EXTRACTED_TBLKTN', None, None, 'IFC_ROOF', 1),
    ('IfcAirTerminal', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcAirTerminal', 'STRUCTURAL', None, None, 'IFC_AIRTERMINAL', 0),
    ('IfcBeam', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcBeam', 'STRUCTURAL', None, None, 'IFC_BEAM', 0),
    ('IfcBuildingElementProxy', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcBuildingElementProxy', 'STRUCTURAL', None, None, 'IFC_BLDGELEMPROXY', 0),
    ('IfcColumn', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcColumn', 'STRUCTURAL', None, None, 'IFC_COLUMN', 0),
    ('IfcDiscreteAccessory', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcDiscreteAccessory', 'STRUCTURAL', None, None, 'IFC_DISCRETEACC', 0),
    ('IfcDoor', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcDoor', 'STRUCTURAL', None, None, 'IFC_DOOR', 0),
    ('IfcFan', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcFan', 'STRUCTURAL', None, None, 'IFC_FAN', 0),
    ('IfcFireSuppressionTerminal', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcFireSuppressionTerminal', 'STRUCTURAL', None, None, 'IFC_FIRESUPPTERM', 0),
    ('IfcFlowTerminal', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcFlowTerminal', 'STRUCTURAL', None, None, 'IFC_FLOWTERMINAL', 0),
    ('IfcFurniture', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcFurniture', 'STRUCTURAL', None, None, 'IFC_FURNITURE', 0),
    ('IfcLightFixture', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcLightFixture', 'STRUCTURAL', None, None, 'IFC_LIGHTFIXTURE', 0),
    ('IfcMember', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcMember', 'STRUCTURAL', None, None, 'IFC_MEMBER', 0),
    ('IfcOutlet', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcOutlet', 'STRUCTURAL', None, None, 'IFC_OUTLET', 0),
    ('IfcPipeFitting', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcPipeFitting', 'STRUCTURAL', None, None, 'IFC_PIPEFITTING', 0),
    ('IfcPipeSegment', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcPipeSegment', 'STRUCTURAL', None, None, 'IFC_PIPESEGMENT', 0),
    ('IfcPlate', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcPlate', 'STRUCTURAL', None, None, 'IFC_PLATE', 0),
    ('IfcRailing', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcRailing', 'STRUCTURAL', None, None, 'IFC_RAILING', 0),
    ('IfcRoof', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcRoof', 'STRUCTURAL', None, None, 'IFC_ROOF', 0),
    ('IfcSanitaryTerminal', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcSanitaryTerminal', 'STRUCTURAL', None, None, 'IFC_SANITARYTERM', 0),
    ('IfcSlab', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcSlab', 'STRUCTURAL', None, None, 'IFC_SLAB', 0),
    ('IfcStair', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcStair', 'STRUCTURAL', None, None, 'IFC_STAIR', 0),
    ('IfcStairFlight', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcStairFlight', 'STRUCTURAL', None, None, 'IFC_STAIRFLIGHT', 0),
    ('IfcSwitchingDevice', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcSwitchingDevice', 'STRUCTURAL', None, None, 'IFC_SWITCHDEVICE', 0),
    ('IfcWall', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcWall', 'STRUCTURAL', None, None, 'IFC_WALL', 0),
    ('IfcWindow', 'STRUCTURAL', 0.001, 0.001, 0.001, 'IfcWindow', 'STRUCTURAL', None, None, 'IFC_WINDOW', 0),
    ('KITCHEN_CABINET_SET', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('KITCHEN_CABINET_SET_DX_A', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('KITCHEN_CABINET_SET_DX_B', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('LAVATORY_OVAL_535', 'FIXTURE', 0.228, 0.485, 0.535, 'IfcFlowTerminal', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWTERMINAL', 1),
    ('LAVATORY_OVAL_650', 'FIXTURE', 0.228, 0.485, 0.65, 'IfcFlowTerminal', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWTERMINAL', 1),
    ('LIFT_LOBBY', 'PHANTOM', 0.001, 0.001, 0.001, None, 'PHANTOM', None, None, 'ASM_PHANTOM', 0),
    ('LIGHT_PENDANT_150W', 'ELECTRICAL', 0.506, 0.507, 0.675, 'IfcFlowTerminal', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWTERMINAL', 1),
    ('LIGHT_SCONCE_100W', 'ELECTRICAL', 0.2, 0.397, 0.4, 'IfcFlowTerminal', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWTERMINAL', 1),
    ('LIVING_SET', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('MEMBER_SH_CURTAIN_WALL', 'STRUCTURAL', 0.499, 0.581, 1.226, 'IfcMember', 'Ifc4_SampleHouse', None, None, 'IFC_MEMBER', 1),
    ('MICROWAVE', 'APPLIANCE', 0.399, 0.45, 0.76, 'IfcFlowTerminal', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWTERMINAL', 1),
    ('PANELBOARD_208V_400A', 'ELECTRICAL', 0.146, 0.508, 1.27, 'IfcFlowTerminal', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWTERMINAL', 1),
    ('PIPE_COLD_WATER_13MM', 'PIPE', 0.013, 0.013, 0.013, 'IfcFlowSegment', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWSEGMENT', 1),
    ('PIPE_COLD_WATER_25MM', 'PIPE', 0.025, 0.025, 0.025, 'IfcFlowSegment', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWSEGMENT', 1),
    ('PIPE_HOT_WATER_13MM', 'PIPE', 0.013, 0.013, 0.013, 'IfcFlowSegment', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWSEGMENT', 1),
    ('PIPE_HOT_WATER_25MM', 'PIPE', 0.025, 0.025, 0.025, 'IfcFlowSegment', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWSEGMENT', 1),
    ('PIPE_MECHANICAL_33MM', 'PIPE', 0.033, 0.033, 0.033, 'IfcFlowSegment', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWSEGMENT', 1),
    ('PIPE_PVC_33MM', 'PIPE', 0.033, 0.033, 0.033, 'IfcFlowSegment', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWSEGMENT', 1),
    ('PIPE_WASTE_33MM', 'PIPE', 0.033, 0.033, 0.033, 'IfcFlowSegment', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWSEGMENT', 1),
    ('PIPE_WASTE_48MM', 'PIPE', 0.048, 0.048, 0.048, 'IfcFlowSegment', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWSEGMENT', 1),
    ('PLATE_SH_GLAZED', 'STRUCTURAL', 0.829, 0.955, 3.027, 'IfcPlate', 'Ifc4_SampleHouse', None, None, 'IFC_PLATE', 1),
    ('Piano', 'FURNITURE', 1.371, 0.6, 1.17, 'IfcFurniture', 'EXTRACTED', 'Wood - Mahogany', '0.600,0.400,0.200,1.000', 'IFC_FURNITURE', 1),
    ('RAILING_GUARD_1100', 'STRUCTURAL', 0.09, 3.805, 3.952, 'IfcRailing', 'Ifc2x3_Duplex', None, None, 'IFC_RAILING', 1),
    ('RAILING_HANDRAIL_900', 'STRUCTURAL', 0.04, 2.957, 3.75, 'IfcRailing', 'Ifc2x3_Duplex', None, None, 'IFC_RAILING', 1),
    ('RANGE', 'APPLIANCE', 0.663, 0.76, 1.041, 'IfcFlowTerminal', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWTERMINAL', 1),
    ('REFRIGERATOR', 'APPLIANCE', 0.804, 0.85, 1.83, 'IfcFlowTerminal', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWTERMINAL', 1),
    ('ROOF_ASSEMBLY', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('ROOF_COVERING', 'ITEM', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'IFC_DISCRETEACC', 0),
    ('ROOF_SH_FLAT_4FELT', 'STRUCTURAL', 1.734, 14.841, 7.285, 'IfcRoof', 'Ifc4_SampleHouse', None, None, 'IFC_ROOF', 1),
    ('ROOF_STRUCTURE', 'ITEM', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'IFC_DISCRETEACC', 0),
    ('SH_BED_SET', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('SH_CW_STR', 'FLOOR', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_FLOOR', 0),
    ('SH_DINING_SET', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('SH_GF_STR', 'FLOOR', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_FLOOR', 0),
    ('SH_LIVING_SET', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('SH_ROOF_STR', 'FLOOR', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_FLOOR', 0),
    ('SINK_ISLAND', 'FIXTURE', 0.455, 0.457, 0.486, 'IfcFlowTerminal', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWTERMINAL', 1),
    ('SKYLIGHT_1180', 'WINDOW', 0.178, 1.173, 1.225, 'IfcWindow', 'Ifc2x3_Duplex', None, None, 'IFC_WINDOW', 1),
    ('SLAB_FINISH_CERAMIC', 'SLAB', 0.013, 1.0, 1.0, 'IfcSlab', 'Ifc2x3_Duplex', None, None, 'IFC_SLAB', 1),
    ('SLAB_FINISH_WOOD', 'SLAB', 0.019, 1.0, 1.0, 'IfcSlab', 'Ifc2x3_Duplex', None, None, 'IFC_SLAB', 1),
    ('SLAB_GRADE_127', 'SLAB', 0.127, 1.0, 1.0, 'IfcSlab', 'Ifc2x3_Duplex', None, None, 'IFC_SLAB', 1),
    ('SLAB_GRADE_150', 'SLAB', 0.15, 1.0, 1.0, 'IfcSlab', 'Ifc2x3_Duplex', None, None, 'IFC_SLAB', 1),
    ('SLAB_ROOF_LIVE', 'SLAB', 0.457, 1.0, 1.0, 'IfcSlab', 'Ifc2x3_Duplex', None, None, 'IFC_SLAB', 1),
    ('SLAB_SH_GROUND_SUSP', 'SLAB', 0.47, 16.868, 8.668, 'IfcSlab', 'Ifc4_SampleHouse', None, None, 'IFC_SLAB', 1),
    ('SLAB_SH_SIMPLE', 'SLAB', 0.165, 13.968, 5.768, 'IfcSlab', 'Ifc4_SampleHouse', None, None, 'IFC_SLAB', 1),
    ('SLAB_WOOD_JOIST', 'SLAB', 0.305, 1.0, 1.0, 'IfcSlab', 'Ifc2x3_Duplex', None, None, 'IFC_SLAB', 1),
    ('SOFA_1830', 'FURNITURE', 0.813, 1.245, 1.245, 'IfcFurnishingElement', 'Ifc2x3_Duplex', None, None, 'IFC_FURNISHING', 1),
    ('SOFA_AREA', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('SPACER_VAR', 'FURN', 0.0, 0.0, 0.0, None, 'MANUAL', None, None, 'IFC_FURNITURE', 0),
    ('STAIR_A', 'PHANTOM', 0.001, 0.001, 0.001, None, 'PHANTOM', None, None, 'ASM_PHANTOM', 0),
    ('STAIR_B', 'PHANTOM', 0.001, 0.001, 0.001, None, 'PHANTOM', None, None, 'ASM_PHANTOM', 0),
    ('STAIR_FLIGHT', 'STRUCTURAL', 0.914, 3.772, 3.05, 'IfcStairFlight', 'Ifc2x3_Duplex', None, None, 'IFC_STAIRFLIGHT', 1),
    ('STAIR_STRINGER', 'STRUCTURAL', 0.05, 3.1, 3.75, 'IfcMember', 'Ifc2x3_Duplex', None, None, 'IFC_MEMBER', 1),
    ('Side_Table', 'FURNITURE', 0.5, 0.5, 0.6, None, 'EXTRACTED', 'Wood - Birch', '0.871,0.722,0.529,1.000', 'IFC_FURNITURE', 1),
    ('Side_Table_Cube_610', 'FURNITURE', 0.61, 0.61, 0.61, 'IfcFurniture', 'Ifc2x3_Duplex', 'Wood - Birch', '0.871,0.722,0.529,1.000', 'IFC_FURNITURE', 1),
    ('Sink_Island', 'FURNITURE', 0.5, 0.5, 0.85, None, 'EXTRACTED', 'Laminate, Ivory, Matte', '1.000,1.000,0.941,1.000', 'IFC_FURNITURE', 1),
    ('Sofa', 'FURNITURE', 2.0, 0.8, 0.45, 'IfcFurniture', 'EXTRACTED', 'Textile - White', '0.961,0.961,0.961,1.000', 'IFC_FURNITURE', 1),
    ('Sofa_Loveseat', 'FURNITURE', 1.6, 0.8, 0.45, 'IfcFurniture', 'EXTRACTED', 'Textile - White', '0.961,0.961,0.961,1.000', 'IFC_FURNITURE', 1),
    ('TALL_CABINET_800', 'FURNITURE', 0.545, 0.8, 2.0, 'IfcFurnishingElement', 'Ifc2x3_Duplex', None, None, 'IFC_FURNISHING', 1),
    ('TELEPHONE_BOARD', 'ELECTRICAL', 0.044, 0.3, 0.3, 'IfcFlowTerminal', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWTERMINAL', 1),
    ('TELEPHONE_OUTLET', 'ELECTRICAL', 0.064, 0.064, 0.114, 'IfcFlowTerminal', 'Ifc2x3_Duplex', None, None, 'IFC_FLOWTERMINAL', 1),
    ('TOILET', 'PHANTOM', 0.001, 0.001, 0.001, None, 'PHANTOM', None, None, 'ASM_PHANTOM', 0),
    ('TOILET_BLOCK_FIXTURES', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('T_CONNECTOR_ASSEMBLY', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('Tall_Cabinet', 'FURNITURE', 0.6, 0.6, 2.0, 'IfcFurniture', 'EXTRACTED', 'Wood - Birch', '0.871,0.722,0.529,1.000', 'IFC_FURNITURE', 1),
    ('UPPER_CABINET_1000', 'FURNITURE', 0.345, 0.6, 1.0, 'IfcFurnishingElement', 'Ifc2x3_Duplex', None, None, 'IFC_FURNISHING', 1),
    ('Upper_Cabinet', 'FURNITURE', 0.6, 0.35, 0.9, 'IfcFurniture', 'EXTRACTED', 'Wood - Birch', '0.871,0.722,0.529,1.000', 'IFC_FURNITURE', 1),
    ('VANITY_CABINET_450', 'FURNITURE', 0.45, 0.475, 0.82, 'IfcFurnishingElement', 'Ifc2x3_Duplex', None, None, 'IFC_FURNISHING', 1),
    ('VANITY_CABINET_650', 'FURNITURE', 0.475, 0.65, 0.82, 'IfcFurnishingElement', 'Ifc2x3_Duplex', None, None, 'IFC_FURNISHING', 1),
    ('VISITOR_SET', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('Vanity_Cabinet', 'FURNITURE', 0.8, 0.5, 0.85, 'IfcFurniture', 'EXTRACTED', 'Wood - Birch', '0.871,0.722,0.529,1.000', 'IFC_FURNITURE', 1),
    ('WALL_BODY', 'PHANTOM', 0.001, 0.001, 0.001, None, 'PHANTOM', None, None, 'ASM_PHANTOM', 0),
    ('WALL_EXT_BRICK_BLOCK', 'WALL', 0.417, 1.0, 1.0, 'IfcWall', 'Ifc2x3_Duplex', None, None, 'IFC_WALL', 1),
    ('WALL_EXT_MY_150_SOLID', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('WALL_EXT_MY_150_WIN_STD', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('WALL_FOUNDATION_417', 'WALL', 0.417, 1.0, 1.0, 'IfcWall', 'Ifc2x3_Duplex', None, None, 'IFC_WALL', 1),
    ('WALL_FOUNDATION_435', 'WALL', 0.435, 1.0, 1.0, 'IfcWall', 'Ifc2x3_Duplex', None, None, 'IFC_WALL', 1),
    ('WALL_INT_FURRING_152', 'WALL', 0.152, 1.0, 1.0, 'IfcWall', 'Ifc2x3_Duplex', None, None, 'IFC_WALL', 1),
    ('WALL_INT_FURRING_38', 'WALL', 0.054, 1.0, 1.0, 'IfcWall', 'Ifc2x3_Duplex', None, None, 'IFC_WALL', 1),
    ('WALL_INT_PARTITION_92', 'WALL', 0.124, 1.0, 1.0, 'IfcWall', 'Ifc2x3_Duplex', None, None, 'IFC_WALL', 1),
    ('WALL_INT_PLUMBING_152', 'WALL', 0.184, 1.0, 1.0, 'IfcWall', 'Ifc2x3_Duplex', None, None, 'IFC_WALL', 1),
    ('WALL_PARTY_CMU', 'WALL', 0.493, 1.0, 1.0, 'IfcWall', 'Ifc2x3_Duplex', None, None, 'IFC_WALL', 1),
    ('WALL_SH_EXT_102BWK', 'WALL', 0.289, 7.797, 2.884, 'IfcWall', 'Ifc4_SampleHouse', None, None, 'IFC_WALL', 1),
    ('WALL_SH_PARTN_70M', 'WALL', 0.094, 2.274, 2.335, 'IfcWall', 'Ifc4_SampleHouse', None, None, 'IFC_WALL', 1),
    ('WARDROBE_SET', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('WEST_UNITS', 'PHANTOM', 0.001, 0.001, 0.001, None, 'PHANTOM', None, None, 'ASM_PHANTOM', 0),
    ('WINDOW_CASEMENT_819', 'WINDOW', 0.618, 0.618, 0.759, 'IfcWindow', 'Ifc2x3_Duplex', None, None, 'IFC_WINDOW', 1),
    ('WINDOW_DUPLEX_BEDROOM', 'WINDOW', 2.8, 0.417, 2.41, None, 'Ifc2x3_Duplex', None, None, 'IFC_WINDOW', 1),
    ('WINDOW_DUPLEX_LIVING', 'WINDOW', 4.835, 0.417, 2.42, None, 'Ifc2x3_Duplex', None, None, 'IFC_WINDOW', 1),
    ('WINDOW_DUPLEX_SMALL', 'WINDOW', 0.819, 0.417, 0.759, None, 'Ifc2x3_Duplex', None, None, 'IFC_WINDOW', 1),
    ('WINDOW_FIXED_750x2200', 'WINDOW', 0.417, 0.75, 2.2, 'IfcWindow', 'Ifc2x3_Duplex', None, None, 'IFC_WINDOW', 1),
    ('WINDOW_SH_1810x1210', 'WINDOW', 0.353, 1.86, 1.21, 'IfcWindow', 'Ifc4_SampleHouse', None, None, 'IFC_WINDOW', 1),
    ('WINDOW_W1', 'WINDOW', 1.2, 0.1, 1.2, None, 'EXTRACTED', None, None, 'IFC_WINDOW', 1),
    ('WINDOW_W2', 'WINDOW', 0.6, 0.1, 0.6, None, 'EXTRACTED', None, None, 'IFC_WINDOW', 1),
    ('WINDOW_W3', 'WINDOW', 1.8, 0.1, 1.2, None, 'EXTRACTED_TBLKTN', None, None, 'IFC_WINDOW', 1),
    ('WORKSTATION_SET', 'SET', 0.001, 0.001, 0.001, None, 'BOM_ASSEMBLY', None, None, 'ASM_SET', 0),
    ('Wardrobe', 'FURNITURE', 1.2, 0.6, 2.1, None, 'EXTRACTED', 'Wood - Birch', '0.871,0.722,0.529,1.000', 'IFC_FURNITURE', 1),
]

M_BOM = [
    ('BUILDING_DX_STD', 'Duplex Unit Standard', 'Duplex building: two mirrored half-width homes sharing a party wall, common roof, floor slabs, and central MEP trunk. First-level children are shared structural elements (GROUND_SLAB, UPPER_SLAB, ROOF, MEP_TRUNK) plus the DUPLEX_SET_STD pair container. Legacy LEVEL_1/LEVEL_2 floor entries retained for current compiler path — Phase F3 will switch compiler to read the PAIR→SINGLE_UNIT tree instead.', 'BUILDING', 'BUILDING', 'RE', 'DX', 'D', 9215, 26565, 7885, 0.0, 0.0, 0.0, 'RE', 1),
    ('BUILDING_SH_STD', 'Sample House Unit Standard', None, 'BUILDING', 'BUILDING', 'RE', 'SH', 'D', 16868, 8668, 3945, 0.0, 0.0, 0.0, 'RE', 1),
    ('BUILDING_TBLKTN_STD', 'TB-LKTN Terrace Unit Standard', None, 'BUILDING', 'BUILDING', 'RE', 'TB', 'D', 9900, 8500, 4300, 0.0, 0.0, 0.0, 'RE', 1),
    ('DUPLEX_SINGLE_UNIT_STD', 'Duplex Single Half-Unit', 'One complete half of a duplex: all rooms for a single residential unit spanning both storeys. Ground floor: living room, dining room, kitchen, bathroom. Upper floor: master bedroom, second bedroom, wardrobe, bathroom, kitchen. This is the atomic "home" — the smallest self-contained dwelling. Unit A uses this BOM at rotation=0; Unit B uses the same BOM at rotation=π. Shared items (KITCHEN_CABINET_SET, DUPLEX_BATHROOM_SET) appear twice — once per storey — because each floor genuinely has its own kitchen and bathroom. The duplication is physical reality, not a modelling error.', 'FLOOR', 'STOREY', 'HU', 'DX', 'D', 17041, 2450, 2100, 0.0, 0.0, 0.0, None, 1),
    ('FLOOR_DX_L1_STD', 'Duplex Level 1 Standard', '[Legacy compiler path] Ground floor rooms for one duplex half-unit: living, dining, kitchen, bathroom. Used by current compiler for storey=Ground room matching. Target model: replaced by DUPLEX_SINGLE_UNIT_STD flat room list.', 'FLOOR', 'STOREY', 'L1', 'DX', 'D', 1950, 2450, 750, 0.0, 0.0, 0.0, None, 1),
    ('FLOOR_DX_L2_STD', 'Duplex Level 2 Standard', '[Legacy compiler path] Upper floor rooms for one duplex half-unit: master bedroom, second bedroom, wardrobe, bathroom, kitchen. Used by current compiler for storey=Upper room matching. Target model: replaced by DUPLEX_SINGLE_UNIT_STD flat room list.', 'FLOOR', 'STOREY', 'L2', 'DX', 'D', 3900, 600, 2100, 0.0, 0.0, 0.0, None, 1),
    ('FLOOR_SH_GF_STD', 'SH Ground Floor Standard', None, 'FLOOR', 'STOREY', 'GF', 'SH', 'D', 13651, 2264, 1170, 0.0, 0.0, 0.0, None, 1),
    ('FLOOR_SLAB_GF', 'Ground Floor Slab', 'Structural ground floor slab (IfcSlab)', 'FLOOR', 'STOREY', 'SL', None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('FLOOR_SLAB_L2', 'Upper Floor Slab', 'Structural upper floor slab (IfcSlab)', 'FLOOR', 'STOREY', 'SL', None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('FLOOR_STRUCTURAL', 'Floor Structural Package', 'Structural elements grouped by storey', 'FLOOR', 'STOREY', None, None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('FLOOR_TBLKTN_GF_STD', 'TB-LKTN Ground Floor Standard', None, 'FLOOR', 'STOREY', 'L1', 'TB', 'D', 9900, 8500, 3000, 0.0, 0.0, 0.0, None, 1),
    ('TYPICAL_CONDO_FLOOR', 'Typical Condo Floor Plate', 'Spatial BOM for condo typical floor zone layout', 'FLOOR', 'STOREY', None, None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('BATHROOM_PREFAB_MY', 'Bathroom — MY Terrace', 'Sanitary block + vanity + ceiling lamp. UBBL-compliant 2.5m².', 'ROOM', 'ROOM', 'BT', 'MY', 'D', 300, 300, 100, 0.0, 0.0, -0.3, None, 1),
    ('BEDROOM_PREFAB_MY_3100', 'Bedroom 3.1×3.1m — MY Terrace', 'Exterior window wall (N face) + bedroom door (W face) + king bed set + ceiling lamp. UBBL-compliant 9.61m².', 'ROOM', 'ROOM', 'BD', 'MY', 'D', 4120, 600, 2100, 0.0, 0.0, -0.3, None, 1),
    ('KITCHEN_PREFAB_MY', 'Kitchen — MY Terrace', 'Exterior window wall + cabinet set + ceiling light. 3.6m wide cabinet run, 3.3m deep work triangle, 2.7m ceiling.', 'ROOM', 'ROOM', 'KT', 'MY', 'D', 3600, 3300, 2700, 0.0, 0.0, -0.3, None, 1),
    ('LIVING_PREFAB_MY', 'Living/Common — MY Terrace', 'Window wall + living set + 2 ceiling lamps. Common zone for terrace units.', 'ROOM', 'ROOM', 'LI', 'MY', 'D', 6671, 1400, 1200, 0.0, 0.0, -0.3, None, 1),
    ('PORCH_MODULE_MY', 'Porch Module — MY Terrace', 'Solid front wall + exterior ceiling lamp.', 'ROOM', 'ROOM', 'PH', 'MY', 'D', 300, 300, 100, 0.0, 0.0, -0.3, None, 1),
    ('BATHROOM_FURNITURE_SET', 'Bathroom Furniture Set', 'Vanity cabinet for residential bathrooms', 'SET', 'ROOM', 'BT', None, 'D', 800, 500, 850, 0.0, 0.0, 0.0, None, 1),
    ('BATHROOM_VANITY_SET', 'Bathroom Vanity Set', None, 'SET', 'ROOM', 'BT', None, 'D', 1600, 500, 850, 0.0, 0.0, 0.0, None, 1),
    ('BED_SET', 'Bedroom Furniture Set', 'Bed + side table(s)', 'SET', 'ROOM', 'BD', None, 'D', 1200, 600, 2000, -1.5, 0.0, 0.0, None, 1),
    ('BED_SET_MASTER', 'Master Bedroom Set', 'King bed + 2 side tables + wardrobe', 'SET', 'ROOM', 'BD', None, 'D', 1200, 600, 2000, -1.22, 0.0, 0.0, None, 1),
    ('CANTEEN_SET', 'Canteen Set', 'Canteen table + chairs for institutional dining', 'SET', 'ROOM', None, None, 'D', 0, 0, 0, -0.45, -0.8, 0.0, None, 1),
    ('CORE_ASSEMBLY', 'Core Assembly', 'Vertical circulation core: stairs + lift lobby', 'SET', 'STOREY', None, None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('DINING_SET', 'Dining Set', 'Dining table + chairs', 'SET', 'ROOM', 'DN', None, 'D', 4200, 900, 750, -0.45, -1.0, 0.0, None, 1),
    ('DOOR_ASSEMBLY', 'Door Assembly', 'Door with frame and hardware', 'SET', 'ELEMENT_NAME', None, None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('DUPLEX_BATHROOM_SET', 'Duplex Bathroom Set', 'Duplex bathroom fixtures: WC, lavatory, bath/shower fittings. Shared SET used by both ground-floor and upper-floor bathrooms within a single half-unit. Referenced twice in DUPLEX_SINGLE_UNIT_STD (BATHROOM_GF and BATHROOM_UF) because each storey genuinely has its own bathroom — the duplication is physical reality.', 'SET', 'ROOM', 'BT', 'DX', 'D', 2700, 900, 2100, 0.0, 0.0, 0.0, None, 1),
    ('DUPLEX_MEP_TRUNK_STD', 'Duplex MEP Trunk', 'Central MEP (Mechanical, Electrical, Plumbing) service trunk shared between both duplex half-units. Runs vertically through the party wall zone: drainage stack, water supply riser, electrical riser, gas line. The IFC Rosetta Stone contains 904 MEP elements (427 IfcFlowSegment, 358 IfcFlowFitting, 105 IfcFlowTerminal, 14 IfcFlowController) compiled verbatim into the output. Activate when MEP BOM decomposition populates children from Rosetta Stone reference.', 'SET', 'BUILDING', 'MP', 'DX', 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 0),
    ('DUPLEX_SET_STD', 'Duplex Unit Pair', 'The duplex PAIR container: two mirrored half-width residential units sharing a party wall. Unit A faces original orientation; Unit B = Unit A rotated π (mirrored about party wall axis). Each half-unit is a complete DUPLEX_SINGLE_UNIT_STD with all rooms across both storeys. The compiler instantiates this pair — one rotation=0, one rotation=π — to produce the full duplex layout.', 'SET', 'BUILDING', 'PR', 'DX', 'D', 17041, 2450, 2100, 0.0, 0.0, 0.0, None, 1),
    ('FP_PIPE_ASSEMBLY', 'Fire Protection Pipe Assembly', 'FP pipes grouped by storey (riser/main/branch)', 'SET', 'STOREY', None, None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('KITCHEN_CABINET_SET', 'Kitchen Cabinet Set', 'Base cabinets + counter + upper cabinets', 'SET', 'ROOM', 'KT', None, 'D', 7100, 600, 900, -2.0, 0.0, 0.0, None, 1),
    ('KITCHEN_CABINET_SET_DX_A', 'Kitchen Cabinet Set DX Unit-A', 'DX Kitchen A: south base cabs + north upper/base/sink via OPPOSITE_WORK', 'SET', 'ROOM', 'KA', None, 'D', 9000, 600, 900, -2.0, 0.0, 0.0, None, 1),
    ('KITCHEN_CABINET_SET_DX_B', 'Kitchen Cabinet Set DX Unit-B', 'DX Kitchen B: south all items (upper cabs stay south) + north base via OPPOSITE_WORK', 'SET', 'ROOM', 'KB', None, 'D', 9000, 600, 900, -2.0, 0.0, 0.0, None, 1),
    ('LIVING_SET', 'Living Room Set', 'Sofa + coffee table + TV', 'SET', 'ROOM', 'LI', None, 'D', 7191, 800, 1170, -1.5, 0.0, 0.0, None, 1),
    ('LOBBY_SEAT_SET', 'Lobby Seat Set', 'Waiting room seats for lobby areas', 'SET', 'ROOM', None, None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('MEP_ROOM', 'Room MEP Package', 'MEP elements grouped by room', 'SET', 'ROOM', None, None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('ROOF_ASSEMBLY', 'Roof Assembly', 'Roof elements grouped per building', 'SET', 'STOREY', 'RF', None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('ROOM_FURNITURE', 'Room Furniture', 'Workstation + visitor + guest', 'SET', 'ROOM', None, None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('SH_BED_SET', 'SH Bedroom Set (bed + side table)', None, 'SET', 'ROOM', 'BD', 'SH', 'D', 3570, 1800, 762, -0.857, 0.0, 0.0, None, 1),
    ('SH_DINING_SET', 'SH Dining Set (south-wall calibrated)', None, 'SET', 'ROOM', 'DN', 'SH', 'D', 4658, 1000, 1227, -3.337, -0.287, 0.0, None, 1),
    ('SH_LIVING_SET', 'SH Living Room Set (north-wall calibrated)', None, 'SET', 'ROOM', 'LI', 'SH', 'D', 5087, 1400, 1170, -3.749, -0.2, 0.0, None, 1),
    ('SOFA_AREA', 'Sofa Area Sub-BOM', 'Coffee table and side tables relative to sofa centroid (SOFA anchor child)', 'SET', 'PROXIMITY', 'FR', 'SH', 'D', 2420, 610, 610, -2.215, -0.078, 0.0, None, 1),
    ('SPRINKLER_PENDANT_ASSEMBLY', 'Pendant Sprinkler Assembly', 'Sprinkler head + T-connector assembly (tee + transition + drop pipe). 177mm total height.', 'SET', 'ROOM', None, None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('STAIR_COMPLETE', 'Complete Stair Assembly', 'Stair with flight, landings, and railings', 'SET', 'ELEMENT_NAME', None, None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('STUDY_SET', 'Study Nook Set', 'Desk + chair for bedroom study area', 'SET', 'ROOM', 'FR', None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('TOILET_BLOCK_FIXTURES', 'Toilet Block Fixtures', 'BOM-driven toilet fixture layout — toilets, bidets, sinks, floor trap, exhaust fan', 'SET', 'ROOM', 'BT', None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('T_CONNECTOR_ASSEMBLY', 'T-Connector Assembly', 'Tee fitting + transition adaptor + drop pipe. Connects MAIN pipe to sprinkler head.', 'SET', 'PROXIMITY', None, None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('VISITOR_SET', 'Visitor Seating Set', 'Table + 2 facing chairs', 'SET', 'ROOM', None, None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 0),
    ('WALL_EXT_MY_150_SOLID', 'Ext Wall 150mm Solid (MY)', 'No openings — party/utility faces.', 'SET', 'BUILDING', 'WL', 'MY', 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('WALL_EXT_MY_150_WIN_STD', 'Ext Wall 150mm + Std Window (MY)', 'WINDOW_W1 centred, 900mm sill height.', 'SET', 'BUILDING', 'WL', 'MY', 'D', 1200, 100, 1200, 0.0, 0.0, 0.0, None, 1),
    ('WALL_EXT_MY_150_WIN_WIDE', 'Ext Wall 150mm + Wide Window (MY)', 'WINDOW_W2 centred, 900mm sill height.', 'SET', 'BUILDING', 'WL', 'MY', 'D', 600, 100, 600, 0.0, 0.0, 0.0, None, 1),
    ('WALL_PANEL', 'Wall Panel Assembly', 'Wall with frame, cladding, and openings', 'SET', 'ELEMENT_NAME', None, None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('WARDROBE_SET', 'Wardrobe Set', 'Tall cabinet/wardrobe for bedrooms (Thesaurus)', 'SET', 'ROOM', 'FR', None, 'D', 2400, 600, 2100, 0.0, 0.0, 0.0, None, 1),
    ('WATER_TANK_ASSEMBLY', 'Water Tank Assembly', 'FRP water tank with plumbing connections', 'SET', 'ELEMENT_NAME', None, None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('WORKSTATION_SET', 'Workstation Set', 'Desk + user chair + monitor', 'SET', 'ROOM', None, None, 'D', 0, 0, 0, -0.59, 0.0, 0.0, None, 1),
    ('ROOF_COVERING', 'Roof Covering', 'Tiles, membrane, flashing', 'ITEM', 'ELEMENT_NAME', 'FR', None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
    ('ROOF_STRUCTURE', 'Roof Structure', 'Trusses, rafters, ridge beam', 'ITEM', 'ELEMENT_NAME', 'FR', None, 'D', 0, 0, 0, 0.0, 0.0, 0.0, None, 1),
]

M_BOM_LINE = [
    ('BATHROOM_FURNITURE_SET', 'Vanity_Cabinet', 'BUY', 'VANITY', 1, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 800, 500, 850),
    ('BATHROOM_FURNITURE_SET', 'BUFFER', 'PHANTOM', 'BUFFER', 200, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('BATHROOM_PREFAB_MY', 'TOILET_BLOCK_FIXTURES', 'BUY', 'SANITARY', 10, 'FACE_AWAY_FROM_WALL', 10, 1200, 0.0, 0.0, 0.3, 1, 'D', 0, 0, 0),
    ('BATHROOM_PREFAB_MY', 'ELEC_LIGHT', 'BUY', 'LIGHT', 20, '0', 30, 0, 0.75, 0.0, 0.0, 1, 'D', 300, 300, 100),
    ('BATHROOM_VANITY_SET', 'Vanity_Cabinet', 'BUY', 'VANITY_A', 1, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 800, 500, 850),
    ('BATHROOM_VANITY_SET', 'Vanity_Cabinet', 'BUY', 'VANITY_B', 2, '0', 20, 0, 0.8, 0.0, 0.0, 1, 'D', 800, 500, 850),
    ('BATHROOM_VANITY_SET', 'BUFFER', 'PHANTOM', 'BUFFER', 200, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('BEDROOM_PREFAB_MY_3100', 'WALL_EXT_MY_150_WIN_STD', 'BUY', 'WALL_EXT', 10, 'FACE_OUTSIDE', 10, 0, 0.0, 0.0, 0.3, 1, 'D', 1200, 100, 1200),
    ('BEDROOM_PREFAB_MY_3100', 'DOOR_D2', 'BUY', 'DOOR_ENTRY', 20, 'FACE_INTO_ROOM', 10, 0, 0.4, 0.0, 0.3, 1, 'D', 800, 45, 2100),
    ('BEDROOM_PREFAB_MY_3100', 'BED_SET_MASTER', 'BUY', 'FURNITURE', 30, 'FACE_AWAY_FROM_WALL', 20, 2000, 0.0, 0.0, 0.3, 1, 'D', 1820, 600, 2000),
    ('BEDROOM_PREFAB_MY_3100', 'ELEC_LIGHT', 'BUY', 'LIGHT', 40, '0', 30, 0, 1.55, 1.55, 0.0, 1, 'D', 300, 300, 100),
    ('BED_SET', 'IfcFurniture', 'BUY', 'BED', 1, '0', 20, 0, 1.5, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('BED_SET', 'IfcFurniture', 'BUY', 'SIDE_TABLE', 2, '0', 20, 0, 2.48, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('BED_SET', 'Tall_Cabinet', 'BUY', 'TALL_CABINET_A', 3, '0', 20, 0, 0.30000000000000004, 0.0, 0.0, 1, 'D', 600, 600, 2000),
    ('BED_SET', 'Tall_Cabinet', 'BUY', 'TALL_CABINET_B', 4, '0', 20, 0, 2.7, 0.0, 0.0, 1, 'D', 600, 600, 2000),
    ('BED_SET', 'IfcFurniture', 'BUY', 'DESK', 5, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('BED_SET', 'BUFFER', 'PHANTOM', 'BUFFER', 200, '0', 20, 0, 1.5, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('BED_SET_MASTER', 'IfcFurniture', 'BUY', 'BED', 1, '0', 20, 0, 1.22, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('BED_SET_MASTER', 'IfcFurniture', 'BUY', 'SIDE_TABLE', 2, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('BED_SET_MASTER', 'Tall_Cabinet', 'BUY', 'TALL_CABINET_B', 3, '0', 20, 0, 2.44, 0.0, 0.0, 1, 'D', 600, 600, 2000),
    ('BED_SET_MASTER', 'Tall_Cabinet', 'BUY', 'TALL_CABINET_A', 4, '0', 20, 0, 1.22, 0.0, 0.0, 1, 'D', 600, 600, 2000),
    ('BED_SET_MASTER', 'BUFFER', 'PHANTOM', 'BUFFER', 200, '0', 20, 0, 1.22, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('BUILDING_DX_STD', 'FLOOR_SLAB_GF', 'BUY', 'GROUND_SLAB', 5, '0', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 0, 0, 0),
    ('BUILDING_DX_STD', 'FLOOR_DX_L1_STD', 'BUY', 'LEVEL_1', 10, '0', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 4871, 2450, 1900),
    ('BUILDING_DX_STD', 'FLOOR_SLAB_L2', 'BUY', 'UPPER_SLAB', 15, '0', 20, 0, 0.0, 0.0, 3.0, 0, 'D', 0, 0, 0),
    ('BUILDING_DX_STD', 'FLOOR_DX_L2_STD', 'BUY', 'LEVEL_2', 20, '0', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 3000, 600, 2100),
    ('BUILDING_DX_STD', 'DUPLEX_MEP_TRUNK_STD', 'BUY', 'MEP_TRUNK', 22, '0', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 0, 0, 0),
    ('BUILDING_DX_STD', 'ROOF_ASSEMBLY', 'BUY', 'ROOF', 25, '0', 20, 0, 0.0, 0.0, 6.0, 0, 'D', 0, 0, 0),
    ('BUILDING_DX_STD', 'DUPLEX_SET_STD', 'BUY', 'PAIR', 100, '0', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 0, 0, 0),
    ('BUILDING_SH_STD', 'FLOOR_SLAB_GF', 'BUY', 'GROUND_SLAB', 5, '0', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 0, 0, 0),
    ('BUILDING_SH_STD', 'FLOOR_SH_GF_STD', 'BUY', 'GROUND_FLOOR', 10, '0', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 9069, 2264, 1170),
    ('BUILDING_SH_STD', 'ROOF_ASSEMBLY', 'BUY', 'ROOF', 15, '0', 20, 0, 0.0, 0.0, 3.0, 0, 'D', 0, 0, 0),
    ('BUILDING_TBLKTN_STD', 'FLOOR_SLAB_GF', 'BUY', 'GROUND_SLAB', 5, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('BUILDING_TBLKTN_STD', 'FLOOR_TBLKTN_GF_STD', 'BUY', 'GROUND_FLOOR', 10, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 9900, 8500, 3000),
    ('BUILDING_TBLKTN_STD', 'ROOF_ASSEMBLY', 'BUY', 'ROOF', 15, '0', 20, 0, 0.0, 0.0, 3.0, 1, 'D', 0, 0, 0),
    ('CANTEEN_SET', 'IfcFurniture', 'BUY', 'TABLE', 1, '0', 20, 0, 0.45, 0.8, 0.0, 1, 'D', 0, 0, 0),
    ('CANTEEN_SET', 'IfcFurniture', 'BUY', 'CHAIR_A', 2, '0', 20, 0, 0.0, 1.6, 0.0, 1, 'D', 0, 0, 0),
    ('CANTEEN_SET', 'IfcFurniture', 'BUY', 'CHAIR_B', 3, '0', 20, 0, 0.9, 1.6, 0.0, 1, 'D', 0, 0, 0),
    ('CANTEEN_SET', 'IfcFurniture', 'BUY', 'CHAIR_C', 4, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('CANTEEN_SET', 'IfcFurniture', 'BUY', 'CHAIR_D', 5, '0', 20, 0, 0.9, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('CORE_ASSEMBLY', 'STAIR_A', 'PHANTOM', 'STAIR_A', 1, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('CORE_ASSEMBLY', 'STAIR_B', 'PHANTOM', 'STAIR_B', 2, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('CORE_ASSEMBLY', 'LIFT_LOBBY', 'PHANTOM', 'LIFT_LOBBY', 3, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('DINING_SET', 'Dining_Table_With_Chairs', 'BUY', 'TABLE', 1, '0', 20, 0, 0.45, 1.0, 0.0, 1, 'D', 1500, 900, 750),
    ('DINING_SET', 'Dining_Chair', 'BUY', 'CHAIR_A', 2, '0', 20, 0, 0.0, 1.65, 0.0, 1, 'D', 450, 450, 450),
    ('DINING_SET', 'Dining_Chair', 'BUY', 'CHAIR_B', 3, '0', 20, 0, 0.9, 1.65, 0.0, 1, 'D', 450, 450, 450),
    ('DINING_SET', 'Dining_Chair', 'BUY', 'CHAIR_C', 4, '0', 20, 0, 0.0, 0.35, 0.0, 1, 'D', 450, 450, 450),
    ('DINING_SET', 'Dining_Chair', 'BUY', 'CHAIR_D', 5, '0', 20, 0, 0.9, 0.35, 0.0, 1, 'D', 450, 450, 450),
    ('DINING_SET', 'Dining_Chair', 'BUY', 'CHAIR_E', 6, '0', 20, 0, 0.45, 2.0, 0.0, 1, 'D', 450, 450, 450),
    ('DINING_SET', 'Dining_Chair', 'BUY', 'CHAIR_F', 7, '0', 20, 0, 0.45, 0.0, 0.0, 1, 'D', 450, 450, 450),
    ('DINING_SET', 'BUFFER', 'PHANTOM', 'BUFFER', 200, '0', 20, 0, 0.45, 1.0, 0.0, 1, 'D', 0, 0, 0),
    ('DOOR_ASSEMBLY', 'IfcDoor', 'BUY', 'LEAF', 10, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('DOOR_ASSEMBLY', 'IfcMember', 'BUY', 'FRAME', 20, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('DOOR_ASSEMBLY', 'IfcDiscreteAccessory', 'BUY', 'HARDWARE', 30, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('DUPLEX_BATHROOM_SET', 'FIXTURE_TOILET', 'BUY', 'TOILET', 1, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 400, 700, 400),
    ('DUPLEX_BATHROOM_SET', 'FIXTURE_SINK', 'BUY', 'BASIN', 2, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 500, 450, 200),
    ('DUPLEX_BATHROOM_SET', 'FIXTURE_SHOWER', 'BUY', 'BATHING', 3, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 900, 900, 2100),
    ('DUPLEX_BATHROOM_SET', 'FIXTURE_SHOWER', 'BUY', 'BATHING', 4, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 900, 900, 2100),
    ('DUPLEX_BATHROOM_SET', 'BUFFER', 'PHANTOM', 'BUFFER', 200, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('DUPLEX_SET_STD', 'DUPLEX_SINGLE_UNIT_STD', 'BUY', 'UNIT_A', 10, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('DUPLEX_SET_STD', 'DUPLEX_SINGLE_UNIT_STD', 'BUY', 'UNIT_B', 20, '3.14159265358979', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('DUPLEX_SINGLE_UNIT_STD', 'LIVING_SET', 'BUY', 'LIVING', 10, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 4871, 1400, 1170),
    ('DUPLEX_SINGLE_UNIT_STD', 'DINING_SET', 'BUY', 'DINING', 20, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 1950, 2450, 750),
    ('DUPLEX_SINGLE_UNIT_STD', 'KITCHEN_CABINET_SET', 'BUY', 'KITCHEN_GF', 30, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 1500, 600, 1900),
    ('DUPLEX_SINGLE_UNIT_STD', 'DUPLEX_BATHROOM_SET', 'BUY', 'BATHROOM_GF', 40, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('DUPLEX_SINGLE_UNIT_STD', 'BED_SET', 'BUY', 'BEDROOM', 50, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 3000, 600, 2000),
    ('DUPLEX_SINGLE_UNIT_STD', 'BED_SET_MASTER', 'BUY', 'MASTER', 60, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 1820, 600, 2000),
    ('DUPLEX_SINGLE_UNIT_STD', 'WARDROBE_SET', 'BUY', 'WARDROBE', 70, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 2400, 600, 2100),
    ('DUPLEX_SINGLE_UNIT_STD', 'DUPLEX_BATHROOM_SET', 'BUY', 'BATHROOM_UF', 80, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('DUPLEX_SINGLE_UNIT_STD', 'KITCHEN_CABINET_SET', 'BUY', 'KITCHEN_UF', 90, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 1500, 600, 1900),
    ('FLOOR_DX_L1_STD', 'LIVING_SET', 'BUY', 'LIVING', 10, '0', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 4871, 1400, 1170),
    ('FLOOR_DX_L1_STD', 'DINING_SET', 'BUY', 'DINING', 20, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 1950, 2450, 750),
    ('FLOOR_DX_L1_STD', 'KITCHEN_CABINET_SET', 'BUY', 'KITCHEN', 30, '0', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 1500, 600, 1900),
    ('FLOOR_DX_L1_STD', 'KITCHEN_CABINET_SET_DX_A', 'BUY', 'KITCHEN_A', 32, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('FLOOR_DX_L1_STD', 'KITCHEN_CABINET_SET_DX_B', 'BUY', 'KITCHEN_B', 34, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('FLOOR_DX_L1_STD', 'TOILET_BLOCK_FIXTURES', 'BUY', 'BATHROOM', 40, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('FLOOR_DX_L2_STD', 'BED_SET', 'BUY', 'BEDROOM', 10, '0', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 3000, 600, 2000),
    ('FLOOR_DX_L2_STD', 'BED_SET_MASTER', 'BUY', 'MASTER', 20, '0', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 1820, 600, 2000),
    ('FLOOR_DX_L2_STD', 'WARDROBE_SET', 'BUY', 'WARDROBE', 30, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 2400, 600, 2100),
    ('FLOOR_DX_L2_STD', 'TOILET_BLOCK_FIXTURES', 'BUY', 'BATHROOM', 40, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('FLOOR_DX_L2_STD', 'KITCHEN_CABINET_SET', 'BUY', 'KITCHEN', 50, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 1500, 600, 1900),
    ('FLOOR_SH_GF_STD', 'SH_LIVING_SET', 'BUY', 'LIVING', 10, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 9069, 1682, 1170),
    ('FLOOR_SH_GF_STD', 'SH_DINING_SET', 'BUY', 'DINING', 20, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 2504, 1463, 750),
    ('FLOOR_SH_GF_STD', 'SH_BED_SET', 'BUY', 'MASTER', 30, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 2078, 2264, 600),
    ('FLOOR_SH_GF_STD', 'TOILET_BLOCK_FIXTURES', 'BUY', 'BATHROOM', 40, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('FLOOR_STRUCTURAL', 'IfcSlab', 'BUY', 'SLAB', 10, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('FLOOR_STRUCTURAL', 'IfcBeam', 'BUY', 'BEAM', 20, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('FLOOR_STRUCTURAL', 'IfcColumn', 'BUY', 'COLUMN', 30, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('FLOOR_TBLKTN_GF_STD', 'LIVING_SET', 'BUY', 'LIVING', 10, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 4871, 1400, 1170),
    ('FLOOR_TBLKTN_GF_STD', 'DINING_SET', 'BUY', 'DINING', 20, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 1950, 2450, 750),
    ('FLOOR_TBLKTN_GF_STD', 'KITCHEN_CABINET_SET', 'BUY', 'KITCHEN', 30, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 1500, 600, 1900),
    ('FLOOR_TBLKTN_GF_STD', 'BED_SET', 'BUY', 'BEDROOM', 40, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 3000, 600, 2000),
    ('FLOOR_TBLKTN_GF_STD', 'BED_SET_MASTER', 'BUY', 'MASTER', 50, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 1820, 600, 2000),
    ('FLOOR_TBLKTN_GF_STD', 'TOILET_BLOCK_FIXTURES', 'BUY', 'BATHROOM', 60, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('FP_PIPE_ASSEMBLY', 'IfcPipeSegment', 'BUY', 'PIPE', 10, '0', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 0, 0, 0),
    ('FP_PIPE_ASSEMBLY', 'IfcFireSuppressionTerminal', 'BUY', 'HEAD', 10, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('FP_PIPE_ASSEMBLY', 'IfcPipeSegment', 'BUY', 'MAIN', 20, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('FP_PIPE_ASSEMBLY', 'IfcPipeSegment', 'BUY', 'BRANCH', 30, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('FP_PIPE_ASSEMBLY', 'IfcPipeSegment', 'BUY', 'RISER', 40, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('FP_PIPE_ASSEMBLY', 'IfcPipeFitting', 'BUY', 'FITTING', 60, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('FP_PIPE_ASSEMBLY', 'IfcPipeSegment', 'BUY', 'DROP', 70, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('KITCHEN_CABINET_SET', 'Base_Cabinet', 'BUY', 'BASE_CABINET', 1, '0', 20, 0, 2.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET', 2, '0', 20, 0, 2.0, 0.0, 1.0, 1, 'D', 600, 350, 900),
    ('KITCHEN_CABINET_SET', 'Counter_Top', 'BUY', 'COUNTER', 3, '0', 20, 0, 2.0, 0.0, 0.86, 1, 'D', 600, 600, 50),
    ('KITCHEN_CABINET_SET', 'Tall_Cabinet', 'BUY', 'TALL_CABINET', 4, '0', 20, 0, 3.0, 0.0, 0.86, 1, 'D', 500, 500, 850),
    ('KITCHEN_CABINET_SET', 'Base_Cabinet', 'BUY', 'BASE_CABINET_2', 5, '0', 20, 0, 3.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET', 'Base_Cabinet', 'BUY', 'BASE_CABINET_3', 6, '0', 20, 0, 4.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET', 'Base_Cabinet', 'BUY', 'BASE_CABINET_4', 7, '0', 20, 0, 5.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET', 'Base_Cabinet', 'BUY', 'BASE_CABINET_5', 8, '0', 20, 0, 1.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET', 'Base_Cabinet', 'BUY', 'BASE_CABINET_6', 9, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET', 'Base_Cabinet', 'BUY', 'BASE_CABINET_7', 10, '0', 20, 0, -3.0, 0.0, 0.0, 0, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET_2', 11, '0', 20, 0, 3.0, 0.0, 1.0, 1, 'D', 600, 350, 900),
    ('KITCHEN_CABINET_SET', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET_3', 12, '0', 20, 0, 1.0, 0.0, 1.0, 1, 'D', 600, 350, 900),
    ('KITCHEN_CABINET_SET', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET_4', 13, '0', 20, 0, 4.0, 0.0, 1.0, 1, 'D', 600, 350, 900),
    ('KITCHEN_CABINET_SET', 'BUFFER', 'PHANTOM', 'BUFFER', 200, '0', 20, 0, 2.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('KITCHEN_CABINET_SET_DX_A', 'Base_Cabinet', 'BUY', 'BASE_CABINET', 1, '0', 20, 0, 2.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET_DX_A', 'Base_Cabinet', 'BUY', 'BASE_CABINET_2', 2, '0', 20, 0, 3.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET_DX_A', 'Base_Cabinet', 'BUY', 'BASE_CABINET_3', 3, '0', 20, 0, 4.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET_DX_A', 'Base_Cabinet', 'BUY', 'BASE_CABINET_5', 4, '0', 20, 0, 1.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET_DX_A', 'IfcFlowTerminal', 'BUY', 'SINK', 5, '0', 20, 0, 1.0, 0.0, 0.86, 0, 'D', 500, 500, 850),
    ('KITCHEN_CABINET_SET_DX_A', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET', 6, '0', 20, 0, 2.0, 0.0, 1.0, 1, 'D', 600, 350, 900),
    ('KITCHEN_CABINET_SET_DX_A', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET_2', 7, '0', 20, 0, 3.0, 0.0, 1.0, 1, 'D', 600, 350, 900),
    ('KITCHEN_CABINET_SET_DX_A', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET_3', 8, '0', 20, 0, 0.0, 0.0, 1.0, 1, 'D', 600, 350, 900),
    ('KITCHEN_CABINET_SET_DX_A', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET_4', 9, '0', 20, 0, 4.0, 0.0, 1.0, 1, 'D', 600, 350, 900),
    ('KITCHEN_CABINET_SET_DX_A', 'Base_Cabinet', 'BUY', 'BASE_CABINET_N1', 10, '0', 20, 0, 2.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET_DX_A', 'Base_Cabinet', 'BUY', 'BASE_CABINET_N2', 11, '0', 20, 0, 3.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET_DX_A', 'Base_Cabinet', 'BUY', 'BASE_CABINET_N3', 12, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET_DX_A', 'Base_Cabinet', 'BUY', 'BASE_CABINET_N4', 13, '0', 20, 0, 4.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET_DX_A', 'Counter_Top', 'BUY', 'COUNTER_N1', 14, '0', 20, 0, 3.0, 0.0, 0.86, 1, 'D', 600, 600, 50),
    ('KITCHEN_CABINET_SET_DX_A', 'Counter_Top', 'BUY', 'COUNTER_N2', 15, '0', 20, 0, 0.0, 0.0, 0.86, 1, 'D', 600, 600, 50),
    ('KITCHEN_CABINET_SET_DX_A', 'Counter_Top', 'BUY', 'COUNTER_SINK', 16, '0', 20, 0, 2.0, 0.0, 0.86, 1, 'D', 600, 600, 50),
    ('KITCHEN_CABINET_SET_DX_A', 'BUFFER', 'PHANTOM', 'BUFFER', 200, '0', 20, 0, 2.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('KITCHEN_CABINET_SET_DX_B', 'Base_Cabinet', 'BUY', 'BASE_CABINET', 1, '0', 20, 0, 2.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET_DX_B', 'Base_Cabinet', 'BUY', 'BASE_CABINET_2', 2, '0', 20, 0, 3.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET_DX_B', 'Base_Cabinet', 'BUY', 'BASE_CABINET_3', 3, '0', 20, 0, 4.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET_DX_B', 'Base_Cabinet', 'BUY', 'BASE_CABINET_6', 4, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET_DX_B', 'Counter_Top', 'BUY', 'COUNTER', 5, '0', 20, 0, 0.0, 0.0, 0.86, 1, 'D', 600, 600, 50),
    ('KITCHEN_CABINET_SET_DX_B', 'IfcFlowTerminal', 'BUY', 'SINK', 6, '0', 20, 0, 1.0, 0.0, 0.86, 0, 'D', 500, 500, 850),
    ('KITCHEN_CABINET_SET_DX_B', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET', 7, '0', 20, 0, 2.0, 0.0, 1.0, 1, 'D', 600, 350, 900),
    ('KITCHEN_CABINET_SET_DX_B', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET_2', 8, '0', 20, 0, 3.0, 0.0, 1.0, 1, 'D', 600, 350, 900),
    ('KITCHEN_CABINET_SET_DX_B', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET_3', 9, '0', 20, 0, 0.0, 0.0, 1.0, 1, 'D', 600, 350, 900),
    ('KITCHEN_CABINET_SET_DX_B', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET_4', 10, '0', 20, 0, 4.0, 0.0, 1.0, 1, 'D', 600, 350, 900),
    ('KITCHEN_CABINET_SET_DX_B', 'Base_Cabinet', 'BUY', 'BASE_CABINET_N1', 11, '0', 20, 0, 2.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET_DX_B', 'Base_Cabinet', 'BUY', 'BASE_CABINET_N2', 12, '0', 20, 0, 3.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET_DX_B', 'Base_Cabinet', 'BUY', 'BASE_CABINET_N3', 13, '0', 20, 0, 1.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET_DX_B', 'Base_Cabinet', 'BUY', 'BASE_CABINET_N4', 14, '0', 20, 0, 4.0, 0.0, 0.0, 1, 'D', 600, 600, 900),
    ('KITCHEN_CABINET_SET_DX_B', 'Counter_Top', 'BUY', 'COUNTER_N1', 15, '0', 20, 0, 2.0, 0.0, 0.86, 1, 'D', 600, 600, 50),
    ('KITCHEN_CABINET_SET_DX_B', 'Counter_Top', 'BUY', 'COUNTER_SINK', 16, '0', 20, 0, 3.0, 0.0, 0.86, 1, 'D', 600, 600, 50),
    ('KITCHEN_CABINET_SET_DX_B', 'BUFFER', 'PHANTOM', 'BUFFER', 200, '0', 20, 0, 2.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('KITCHEN_PREFAB_MY', 'WALL_EXT_MY_150_WIN_STD', 'BUY', 'WALL_EXT', 10, 'FACE_OUTSIDE', 10, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('KITCHEN_PREFAB_MY', 'KITCHEN_CABINET_SET', 'BUY', 'FURNITURE', 20, 'FACE_AWAY_FROM_WALL', 20, 600, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('KITCHEN_PREFAB_MY', 'ELEC_LIGHT', 'BUY', 'LIGHT', 30, '0', 30, 0, 1.8, 1.65, -0.3, 1, 'D', 0, 0, 0),
    ('LIVING_PREFAB_MY', 'WALL_EXT_MY_150_WIN_STD', 'BUY', 'WALL_EXT', 10, 'FACE_OUTSIDE', 10, 0, 0.0, 0.0, 0.3, 1, 'D', 1200, 100, 1200),
    ('LIVING_PREFAB_MY', 'LIVING_SET', 'BUY', 'FURNITURE', 20, 'FACE_AWAY_FROM_WALL', 20, 3000, 0.0, 0.0, 0.3, 1, 'D', 4871, 1400, 1170),
    ('LIVING_PREFAB_MY', 'ELEC_LIGHT', 'BUY', 'LIGHT_1', 30, '0', 30, 0, 1.2, 1.2, 0.0, 1, 'D', 300, 300, 100),
    ('LIVING_PREFAB_MY', 'ELEC_LIGHT', 'BUY', 'LIGHT_2', 40, '0', 30, 0, 2.5, 1.2, 0.0, 1, 'D', 300, 300, 100),
    ('LIVING_SET', 'Sofa', 'BUY', 'SOFA', 1, '0', 20, 0, 1.5, 0.0, 0.0, 1, 'D', 2000, 800, 450),
    ('LIVING_SET', 'Coffee_Table', 'BUY', 'COFFEE_TABLE', 2, '0', 20, 0, 1.5, 0.8, 0.0, 1, 'D', 1000, 600, 450),
    ('LIVING_SET', 'IfcFurniture', 'BUY', 'TV', 3, '0', 20, 0, 0.0, 0.0, 1.2, 0, 'D', 0, 0, 0),
    ('LIVING_SET', 'IfcFurniture', 'BUY', 'LOUNGE_CHAIR', 4, '0', 20, 0, 1.5, 0.0, 0.0, 0, 'D', 0, 0, 0),
    ('LIVING_SET', 'Sofa_Loveseat', 'BUY', 'SOFA_B', 5, '1.5708', 20, 0, 0.0, 0.5, 0.0, 1, 'D', 1600, 800, 450),
    ('LIVING_SET', 'Side_Table_Cube_610', 'BUY', 'SIDE_TABLE_A', 6, '0', 20, 0, 2.7, 0.0, 0.0, 1, 'D', 610, 610, 610),
    ('LIVING_SET', 'Side_Table_Cube_610', 'BUY', 'SIDE_TABLE_B', 7, '0', 20, 0, 0.30000000000000004, 0.0, 0.0, 1, 'D', 610, 610, 610),
    ('LIVING_SET', 'Piano', 'BUY', 'PIANO', 8, '0', 20, 0, 3.5, 0.0, 0.0, 1, 'D', 1371, 600, 1170),
    ('LIVING_SET', 'BUFFER', 'PHANTOM', 'BUFFER', 200, '0', 20, 0, 1.5, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('LOBBY_SEAT_SET', 'IfcFurniture', 'BUY', 'GUEST_SEAT', 1, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('MEP_ROOM', 'IfcLightFixture', 'BUY', 'LIGHT', 10, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('MEP_ROOM', 'IfcFireSuppressionTerminal', 'BUY', 'SPRINKLER', 20, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('MEP_ROOM', 'IfcAirTerminal', 'BUY', 'DIFFUSER', 30, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('MEP_ROOM', 'IfcFan', 'BUY', 'FAN', 35, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('MEP_ROOM', 'IfcOutlet', 'BUY', 'OUTLET', 40, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('MEP_ROOM', 'IfcSwitchingDevice', 'BUY', 'SWITCH', 50, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('MEP_ROOM', 'IfcFurniture', 'BUY', 'FURNITURE', 60, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('MEP_ROOM', 'IfcSanitaryTerminal', 'BUY', 'FIXTURE', 110, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('PORCH_MODULE_MY', 'WALL_EXT_MY_150_SOLID', 'BUY', 'WALL_SOUTH', 10, 'FACE_OUTSIDE', 10, 0, 0.0, 0.0, 0.3, 1, 'D', 0, 0, 0),
    ('PORCH_MODULE_MY', 'ELEC_LIGHT', 'BUY', 'LIGHT', 20, '0', 20, 0, 2.75, 1.15, 0.0, 1, 'D', 300, 300, 100),
    ('ROOF_ASSEMBLY', 'IfcRoof', 'BUY', 'ROOF', 10, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('ROOF_ASSEMBLY', 'ROOF_STRUCTURE', 'BUY', 'STRUCTURE', 10, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('ROOF_ASSEMBLY', 'ROOF_COVERING', 'BUY', 'COVERING', 20, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('ROOM_FURNITURE', 'WORKSTATION_SET', 'BUY', 'WORK_ZONE', 1, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('ROOM_FURNITURE', 'VISITOR_SET', 'BUY', 'VISITOR_ZONE', 2, '0', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 0, 0, 0),
    ('ROOM_FURNITURE', 'IfcFurniture', 'BUY', 'GUEST_SEAT', 3, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('SH_BED_SET', 'IfcFurniture', 'BUY', 'BED', 1, '0', 20, 0, 0.04599999999999993, 0.503, 0.0, 1, 'D', 2007, 1800, 483),
    ('SH_BED_SET', 'IfcFurniture', 'BUY', 'DESK', 2, '0', 20, 0, 0.0, 0.22, 0.0, 1, 'D', 1563, 819, 762),
    ('SH_BED_SET', 'BUFFER', 'PHANTOM', 'BUFFER', 200, '0', 20, 0, 0.857, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('SH_DINING_SET', 'IfcFurniture', 'BUY', 'TABLE', 1, '0', 20, 0, 1.004, 0.509, 0.0, 1, 'D', 2000, 1000, 750),
    ('SH_DINING_SET', 'IfcFurniture', 'BUY', 'CHAIR_A', 2, '0', 20, 0, 0.6540000000000004, 1.013, 0.0, 1, 'D', 443, 443, 1227),
    ('SH_DINING_SET', 'IfcFurniture', 'BUY', 'CHAIR_B', 3, '0', 20, 0, 1.354, 1.013, 0.0, 1, 'D', 443, 443, 1227),
    ('SH_DINING_SET', 'IfcFurniture', 'BUY', 'CHAIR_C', 4, '0', 20, 0, 0.6540000000000004, 0.0, 0.0, 1, 'D', 443, 443, 1227),
    ('SH_DINING_SET', 'IfcFurniture', 'BUY', 'CHAIR_D', 5, '0', 20, 0, 1.354, 0.0, 0.0, 1, 'D', 443, 443, 1227),
    ('SH_DINING_SET', 'IfcFurniture', 'BUY', 'CHAIR_E', 6, '0', 20, 0, 0.0, 0.509, 0.0, 1, 'D', 443, 443, 1227),
    ('SH_DINING_SET', 'IfcFurniture', 'BUY', 'CHAIR_F', 7, '0', 20, 0, 2.007, 0.509, 0.0, 1, 'D', 443, 443, 1227),
    ('SH_DINING_SET', 'BUFFER', 'PHANTOM', 'BUFFER', 200, '0', 20, 0, 3.337, 0.287, 0.0, 1, 'D', 0, 0, 0),
    ('SH_DINING_SET', 'BUFFER', 'PHANTOM', 'BUFFER', 210, '0', 20, 0, 3.337, 0.287, 0.0, 1, 'D', 0, 0, 0),
    ('SH_LIVING_SET', 'IfcFurniture', 'BUY', 'COFFEE_TABLE', 2, '0', 20, 0, 1.459, 1.2, 0.0, 0, 'D', 1200, 550, 450),
    ('SH_LIVING_SET', 'IfcFurniture', 'BUY', 'SIDE_TABLE_A', 4, '0', 20, 0, 1.2, 0.0, 0.0, 0, 'D', 610, 610, 610),
    ('SH_LIVING_SET', 'IfcFurniture', 'BUY', 'SIDE_TABLE_B', 5, '0', 20, 0, -0.663, 0.586, 0.0, 0, 'D', 610, 610, 610),
    ('SH_LIVING_SET', 'IfcFurniture', 'BUY', 'PIANO', 10, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 1372, 600, 1170),
    ('SH_LIVING_SET', 'SPACER_VAR', 'BUY', 'SPACER', 20, '0', 99, 0, 0.0, 0.0, 0.0, 0, 'D', 0, 0, 0),
    ('SH_LIVING_SET', 'SOFA_AREA', 'BUY', 'SOFA', 30, '0', 20, 0, 5.301, 0.278, 0.0, 1, 'D', 2287, 977, 958),
    ('SH_LIVING_SET', 'IfcFurniture', 'BUY', 'SOFA_B', 50, '0', 20, 0, 7.469, 0.8820000000000001, 0.0, 1, 'D', 1428, 1400, 918),
    ('SH_LIVING_SET', 'BUFFER', 'PHANTOM', 'BUFFER', 200, '0', 20, 0, 3.749, 0.2, 0.0, 1, 'D', 0, 0, 0),
    ('SH_LIVING_SET', 'BUFFER', 'PHANTOM', 'BUFFER', 210, '0', 20, 0, 3.749, 0.2, 0.0, 1, 'D', 0, 0, 0),
    ('SOFA_AREA', 'IfcFurniture', 'BUY', 'COFFEE_TABLE', 10, '0', 20, 0, 2.122, 1.2000000000000002, 0.0, 1, 'D', 1200, 550, 450),
    ('SOFA_AREA', 'IfcFurniture', 'BUY', 'SIDE_TABLE_A', 20, '0', 20, 0, 1.863, 0.0, 0.0, 1, 'D', 610, 610, 610),
    ('SOFA_AREA', 'IfcFurniture', 'BUY', 'SIDE_TABLE_B', 30, '0', 20, 0, 0.0, 0.586, 0.0, 1, 'D', 610, 610, 610),
    ('SPRINKLER_PENDANT_ASSEMBLY', 'IfcFireSuppressionTerminal', 'BUY', 'SPRINKLER_HEAD', 1, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('SPRINKLER_PENDANT_ASSEMBLY', 'T_CONNECTOR_ASSEMBLY', 'BUY', 'T_ASSEMBLY', 2, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('STAIR_COMPLETE', 'IfcStair', 'BUY', 'PARENT', 5, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('STAIR_COMPLETE', 'IfcStairFlight', 'BUY', 'FLIGHT', 10, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('STAIR_COMPLETE', 'IfcSlab', 'BUY', 'LANDING', 20, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('STAIR_COMPLETE', 'IfcRailing', 'BUY', 'RAILING', 30, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('STUDY_SET', 'IfcFurniture', 'BUY', 'DESK', 1, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('STUDY_SET', 'BUFFER', 'PHANTOM', 'BUFFER', 200, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('TOILET_BLOCK_FIXTURES', 'IfcSanitaryTerminal', 'BUY', 'TOILET', 1, 'FACE_AWAY_FROM_WALL', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('TOILET_BLOCK_FIXTURES', 'IfcSanitaryTerminal', 'BUY', 'HAND_BIDET', 2, 'FACE_AWAY_FROM_WALL', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('TOILET_BLOCK_FIXTURES', 'IfcSanitaryTerminal', 'BUY', 'SINK', 3, 'FACE_INTO_ROOM', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 0, 0, 0),
    ('TOILET_BLOCK_FIXTURES', 'IfcSanitaryTerminal', 'BUY', 'FLOOR_TRAP', 4, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('TOILET_BLOCK_FIXTURES', 'IfcFlowTerminal', 'BUY', 'EXHAUST_FAN', 5, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('TOILET_BLOCK_FIXTURES', 'IfcSanitaryTerminal', 'BUY', 'SINK', 35, 'FACE_INTO_ROOM', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('TOILET_BLOCK_FIXTURES', 'IfcSanitaryTerminal', 'BUY', 'SINK', 36, 'FACE_INTO_ROOM', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('TOILET_BLOCK_FIXTURES', 'BUFFER', 'PHANTOM', 'BUFFER', 200, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('TYPICAL_CONDO_FLOOR', 'CORE_ASSEMBLY', 'BUY', 'CORE', 1, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('TYPICAL_CONDO_FLOOR', 'CORRIDOR', 'PHANTOM', 'CORRIDOR', 2, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('TYPICAL_CONDO_FLOOR', 'WEST_UNITS', 'PHANTOM', 'WEST_UNITS', 3, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('TYPICAL_CONDO_FLOOR', 'EAST_UNITS', 'PHANTOM', 'EAST_UNITS', 4, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('TYPICAL_CONDO_FLOOR', 'TOILET', 'PHANTOM', 'TOILET', 5, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('T_CONNECTOR_ASSEMBLY', 'IfcPipeSegment', 'BUY', 'DROP_PIPE', 1, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('T_CONNECTOR_ASSEMBLY', 'IfcPipeFitting', 'BUY', 'TRANSITION', 2, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('T_CONNECTOR_ASSEMBLY', 'IfcPipeFitting', 'BUY', 'TEE', 3, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('VISITOR_SET', 'IfcFurniture', 'BUY', 'TABLE', 1, '0', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 0, 0, 0),
    ('VISITOR_SET', 'IfcFurniture', 'BUY', 'CHAIR_A', 2, '0', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 0, 0, 0),
    ('VISITOR_SET', 'IfcFurniture', 'BUY', 'CHAIR_B', 3, '3.14159265', 20, 0, 0.0, 0.0, 0.0, 0, 'D', 0, 0, 0),
    ('WALL_EXT_MY_150_SOLID', 'WALL_BODY', 'PHANTOM', 'WALL_BODY', 10, 'PARALLEL_TO_WALL', 10, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('WALL_EXT_MY_150_WIN_STD', 'WALL_BODY', 'PHANTOM', 'WALL_BODY', 10, 'PARALLEL_TO_WALL', 10, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('WALL_EXT_MY_150_WIN_STD', 'WINDOW_W1', 'BUY', 'WIN_CENTRE', 20, 'FACE_OUTSIDE', 20, 0, 0.0, 0.0, 0.9, 1, 'D', 1200, 100, 1200),
    ('WALL_EXT_MY_150_WIN_WIDE', 'WALL_BODY', 'PHANTOM', 'WALL_BODY', 10, 'PARALLEL_TO_WALL', 10, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('WALL_EXT_MY_150_WIN_WIDE', 'WINDOW_W2', 'BUY', 'WIN_CENTRE', 20, 'FACE_OUTSIDE', 20, 0, 0.0, 0.0, 0.9, 1, 'D', 600, 100, 600),
    ('WALL_PANEL', 'IfcMember', 'BUY', 'FRAME', 10, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('WALL_PANEL', 'IfcWall', 'BUY', 'WALL', 10, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('WALL_PANEL', 'IfcBeam', 'BUY', 'LINTEL', 15, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('WALL_PANEL', 'IfcPlate', 'BUY', 'CLADDING', 20, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('WALL_PANEL', 'DOOR_ASSEMBLY', 'BUY', 'OPENING', 30, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('WALL_PANEL', 'IfcWindow', 'BUY', 'OPENING', 31, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('WALL_PANEL', 'IfcDoor', 'BUY', 'OPENING', 32, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('WARDROBE_SET', 'FURN_WARDROBE', 'BUY', 'WARDROBE_A', 10, 'FACE_INTO_ROOM', 10, 0, 0.0, 0.0, 0.0, 1, 'D', 1200, 600, 2100),
    ('WARDROBE_SET', 'FURN_WARDROBE', 'BUY', 'WARDROBE_B', 20, 'FACE_INTO_ROOM', 20, 0, 1.2, 0.0, 0.0, 1, 'D', 1200, 600, 2100),
    ('WARDROBE_SET', 'BUFFER', 'PHANTOM', 'BUFFER', 200, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('WATER_TANK_ASSEMBLY', 'IfcBuildingElementProxy', 'BUY', 'VESSEL', 10, '0', 20, 0, 0.0, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('WORKSTATION_SET', 'IfcFurniture', 'BUY', 'DESK', 1, '0', 20, 0, 0.59, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('WORKSTATION_SET', 'IfcFurniture', 'BUY', 'USER_CHAIR', 2, '0', 20, 0, 0.59, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('WORKSTATION_SET', 'IfcFurniture', 'BUY', 'MONITOR', 3, '0', 20, 0, 0.59, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('WORKSTATION_SET', 'IfcFurniture', 'BUY', 'VISITOR_CHAIR_A', 4, '3.14159265', 20, 0, 0.59, 0.0, 0.0, 1, 'D', 0, 0, 0),
    ('WORKSTATION_SET', 'IfcFurniture', 'BUY', 'VISITOR_CHAIR_B', 5, '3.14159265', 20, 0, 0.59, 0.0, 0.0, 1, 'D', 0, 0, 0),
]

# --- m_attribute params keyed by (bom_id, role) — abstract, ID-independent ---
# Resolved to bom_child_id at insert time via populate_attributes()
M_ATTRIBUTE = [
    # BOM composition rules — portable to any building using these BOM templates
    ('SH_LIVING_SET', 'PIANO', 'wall_rule', 'NORTH_WALL', 'STRING', None, 'Piano placed against north wall'),
    ('BED_SET_MASTER', 'TALL_CABINET_A', 'opposite_wall', 'true', 'STRING', None, 'Place on wall opposite to primary furniture'),
    ('TOILET_BLOCK_FIXTURES', 'TOILET', 'spacing', '1.3', 'DOUBLE', 'm', 'Stall spacing center-to-center'),
    ('TOILET_BLOCK_FIXTURES', 'TOILET', 'stall_divider_depth', '1.2', 'DOUBLE', 'm', 'Depth of partition wall from back wall'),
    ('TOILET_BLOCK_FIXTURES', 'TOILET', 'stall_divider_height', '1.8', 'DOUBLE', 'm', 'Height of partition wall'),
]

AD_SCRIPTS = [
    "create_ad_building_bom.py",
    "create_ad_building_codes.py",
    "create_ad_fire_protection.py",
    "create_ad_mep_bom.py",
    "create_ad_mep_schema.py",
    "create_ad_placement_rules.py",
    "create_ad_sanity_check.py",
    "create_ad_space_type_opening.py",
    "create_ad_space_type_schema.py",
    "create_ad_vertical_circulation.py",
]


# ==============================================================================
# Functions
# ==============================================================================

def create_schema():
    if os.path.exists(BOM_DB):
        os.remove(BOM_DB)
    if not os.path.exists(SCHEMA_SQL):
        print(f"[ERROR] Schema not found: {SCHEMA_SQL}")
        sys.exit(1)
    with open(SCHEMA_SQL) as f:
        schema = f.read()
    # Filter out sqlite_sequence (auto-created by AUTOINCREMENT)
    lines = [l for l in schema.split('\n')
             if 'sqlite_sequence' not in l.lower()]
    schema = '\n'.join(lines)
    # Fix legacy CHECK constraint (schema says full words, data uses codes)
    schema = schema.replace(
        "IN ('Residential','Commercial','Industrial')",
        "IN ('Residential','Commercial','Industrial','RE','CO','IN')")
    conn = sqlite3.connect(BOM_DB)
    conn.executescript(schema)
    conn.close()
    print("[1/8] Schema created")


def populate_reference(conn):
    c = conn.cursor()
    for row in C_DOCTYPE:
        c.execute("""INSERT INTO C_DocType
            (C_DocType_ID, Name, DocBaseType, DocSubType, IsDefault, IsActive,
             Description, ProjectName, DSLContent, OutputDbPath, ReferenceDbPath,
             ExpectedElements, Provenance, GeometryFailThreshold, SeqNo,
             AabbWidthMm, AabbDepthMm, AabbHeightMm, C_Campaign_ID, SalesRep_ID)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", row)
    for row in M_BOMCATEGORY:
        c.execute("""INSERT INTO M_BomCategory
            (M_BomCategory_ID, Name, Description, IsActive, C_BPartner_ID, Value,
             aabb_width_mm, aabb_depth_mm, aabb_height_mm, doc_type, doc_sub_type)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)""", row)
    for row in M_PRODUCT_CATEGORY:
        c.execute("""INSERT INTO M_Product_Category
            (M_Product_Category_ID, Name, Description, Parent_Category_ID,
             IFC_Class, SeqNo, IsActive)
            VALUES (?,?,?,?,?,?,?)""", row)
    for row in C_BPARTNER:
        c.execute("""INSERT INTO C_BPartner
            (C_BPartner_ID, Value, Name, Description, IsActive)
            VALUES (?,?,?,?,?)""", row)
    for row in AD_SYSCONFIG:
        c.execute("""INSERT INTO ad_sysconfig
            (config_key, config_value, description, is_active)
            VALUES (?,?,?,?)""", row)
    conn.commit()
    print(f"[2/8] Reference: {len(C_DOCTYPE)} DocTypes, {len(M_BOMCATEGORY)} BomCat, "
          f"{len(M_PRODUCT_CATEGORY)} ProdCat, {len(C_BPARTNER)} BPartners")


def populate_products(conn):
    c = conn.cursor()
    for row in M_PRODUCT:
        c.execute("""INSERT INTO M_Product
            (product_id, product_type, width, depth, height, ifc_class,
             extracted_from, material_name, material_rgba,
             M_Product_Category_ID, is_active)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)""", row)
    conn.commit()
    print(f"[3/8] Products: {len(M_PRODUCT)} rows")


def populate_boms(conn):
    c = conn.cursor()
    for row in M_BOM:
        c.execute("""INSERT INTO m_bom
            (bom_id, bom_name, description, bom_type, group_by, bom_category,
             doc_sub_type, entity_type, aabb_width_mm, aabb_depth_mm, aabb_height_mm,
             origin_x, origin_y, origin_z, doc_base_type, is_active)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", row)
    for row in M_BOM_LINE:
        c.execute("""INSERT INTO m_bom_line
            (bom_id, child_product_id, component_type, role, sequence,
             rotation_rule, fit_priority, min_space_mm,
             dx, dy, dz, is_active, entity_type,
             allocated_width_mm, allocated_depth_mm, allocated_height_mm)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", row)
    # m_attribute: resolve bom_child_id from (bom_id, role) — abstract, not hardcoded IDs
    attr_count = 0
    for bom_id, role, key, val, ptype, unit, desc in M_ATTRIBUTE:
        c.execute("SELECT bom_child_id FROM m_bom_line WHERE bom_id=? AND role=?", (bom_id, role))
        row = c.fetchone()
        if row is None:
            print(f"  [WARN] m_attribute skip: no child {bom_id}/{role}")
            continue
        c.execute("""INSERT INTO m_attribute
            (bom_child_id, param_key, param_value, param_type, unit, description, is_active)
            VALUES (?,?,?,?,?,?,1)""", (row[0], key, val, ptype, unit, desc))
        attr_count += 1
    conn.commit()
    print(f"[4/8] Static BOMs: {len(M_BOM)} headers, {len(M_BOM_LINE)} lines, {attr_count} attrs")


def run_extraction(conn):
    if not os.path.exists(COMP_DB):
        print("  [WARN] component_library.db not found, skipping extraction")
        return
    sys.path.insert(0, SCRIPT_DIR)
    import RosettaStoneExtract
    comp_conn = sqlite3.connect(COMP_DB)
    print("  Extracting all Rosetta Stone buildings (SH + DX)...")
    total = RosettaStoneExtract.extract_all(conn, comp_conn)
    h = RosettaStoneExtract.compute_integrity_hash(conn)
    conn.commit()
    comp_conn.close()
    print(f"[5/8] Extraction: {total} element lines, hash: {h[:16]}...")


def run_ad_scripts():
    ok = 0
    for script_name in AD_SCRIPTS:
        script_path = os.path.join(SCRIPT_DIR, script_name)
        if not os.path.exists(script_path):
            print(f"  [WARN] Missing: {script_name}")
            continue
        result = subprocess.run(
            [sys.executable, script_path],
            capture_output=True, text=True,
            cwd=os.path.join(SCRIPT_DIR, ".."))
        if result.returncode != 0:
            print(f"  [WARN] {script_name} failed: {result.stderr[:200]}")
        else:
            ok += 1
    print(f"[6/8] AD scripts: {ok}/{len(AD_SCRIPTS)} OK")


def validate(conn):
    c = conn.cursor()
    counts = {}
    for table in ['C_DocType', 'M_BomCategory', 'M_Product_Category', 'C_BPartner',
                   'M_Product', 'm_bom', 'm_bom_line', 'ad_sysconfig']:
        counts[table] = c.execute(f"SELECT COUNT(*) FROM [{table}]").fetchone()[0]
    print(f"[7/8] Counts: M_Product={counts['M_Product']}, "
          f"m_bom={counts['m_bom']}, m_bom_line={counts['m_bom_line']}")
    bad = c.execute("""
        SELECT COUNT(*) FROM m_bom_line l
        JOIN m_bom b ON l.bom_id = b.bom_id
        WHERE b.bom_type = 'FLOOR' AND l.component_type = 'BUY'
          AND l.storey IS NOT NULL
          AND (l.dx < -0.001 OR l.dy < -0.001 OR l.dz < -0.001)
    """).fetchone()[0]
    if bad > 0:
        print(f"  [WARN] {bad} extracted lines with negative dx/dy/dz")
    return counts


def print_summary(counts):
    print(f"[8/8] BOM.db ready at {BOM_DB}")
    for k, v in sorted(counts.items()):
        print(f"  {k:25s} {v:>6}")


def main():
    print("=" * 60)
    print("RosettaStoneToBOM -- Creating pristine BOM.db")
    print("=" * 60)
    create_schema()
    conn = sqlite3.connect(BOM_DB)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    populate_reference(conn)
    populate_products(conn)
    populate_boms(conn)
    run_extraction(conn)
    run_ad_scripts()
    counts = validate(conn)
    print_summary(counts)
    conn.close()


if __name__ == '__main__':
    main()

