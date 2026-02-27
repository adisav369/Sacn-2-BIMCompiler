-- Phase F2: DX Duplex BOM Restructure — Correct 2-in-1 Model
-- ============================================================
-- A duplex is TWO mirrored half-width homes sharing a party wall,
-- common roof, floor slabs, and central MEP trunk.
-- The π rotation belongs to Unit B (the mirrored half), not the upper floor.
--
-- This migration adds the target BOM tree (DUPLEX_SET_STD → DUPLEX_SINGLE_UNIT_STD ×2)
-- alongside the existing compiler path (LEVEL_1/LEVEL_2 floors).
--
-- COMPILER SAFETY: new BOMs use bom_level='SET' which is NOT loaded by
-- loadBomChain() (filters on bom_level IN ('UNIT','FLOOR')).
-- Old compiler path is UNCHANGED. Tests stay at baseline.
--
-- c_orderline: NO CHANGE. orientation=π stays on L2 for current compiler.

-- ────────────────────────────────────────────────────────────
-- 1a. Create 3 new m_bom records
-- ────────────────────────────────────────────────────────────

INSERT OR IGNORE INTO m_bom (bom_id, bom_name, bom_type, bom_level, group_by, bom_owner, bom_category, description)
VALUES (
    'DUPLEX_SET_STD',
    'Duplex Unit Pair',
    'SET',
    'SET',
    'BUILDING',
    'DX',
    'PR',
    'The duplex PAIR container: two mirrored half-width residential units sharing a party wall. Unit A faces original orientation; Unit B = Unit A rotated π (mirrored about party wall axis). Each half-unit is a complete DUPLEX_SINGLE_UNIT_STD with all rooms across both storeys. The compiler instantiates this pair — one rotation=0, one rotation=π — to produce the full duplex layout.'
);

INSERT OR IGNORE INTO m_bom (bom_id, bom_name, bom_type, bom_level, group_by, bom_owner, bom_category, description)
VALUES (
    'DUPLEX_SINGLE_UNIT_STD',
    'Duplex Single Half-Unit',
    'FLOOR',
    'FLOOR',
    'STOREY',
    'DX',
    'HU',
    'One complete half of a duplex: all rooms for a single residential unit spanning both storeys. Ground floor: living room, dining room, kitchen, bathroom. Upper floor: master bedroom, second bedroom, wardrobe, bathroom, kitchen. This is the atomic "home" — the smallest self-contained dwelling. Unit A uses this BOM at rotation=0; Unit B uses the same BOM at rotation=π. Shared items (KITCHEN_CABINET_SET, DUPLEX_BATHROOM_SET) appear twice — once per storey — because each floor genuinely has its own kitchen and bathroom. The duplication is physical reality, not a modelling error.'
);

INSERT OR IGNORE INTO m_bom (bom_id, bom_name, bom_type, bom_level, group_by, bom_owner, bom_category, is_active, description)
VALUES (
    'DUPLEX_MEP_TRUNK_STD',
    'Duplex MEP Trunk',
    'SET',
    'SET',
    'BUILDING',
    'DX',
    'MP',
    0,
    'Central MEP (Mechanical, Electrical, Plumbing) service trunk shared between both duplex half-units. Runs vertically through the party wall zone: drainage stack, water supply riser, electrical riser, gas line. The IFC Rosetta Stone contains 904 MEP elements (427 IfcFlowSegment, 358 IfcFlowFitting, 105 IfcFlowTerminal, 14 IfcFlowController) compiled verbatim into the output. Activate when MEP BOM decomposition populates children from Rosetta Stone reference.'
);

-- ────────────────────────────────────────────────────────────
-- 1b. Create m_bom_line records
-- ────────────────────────────────────────────────────────────

-- Under UNIT_DUPLEX_STD: 2 new lines (MEP_TRUNK + PAIR)
-- MEP_TRUNK inactive until BOM decomposition populates children
INSERT INTO m_bom_line (bom_id, role, child_bom_id, sequence, qty_type, is_active)
VALUES ('UNIT_DUPLEX_STD', 'MEP_TRUNK', 'DUPLEX_MEP_TRUNK_STD', 22, 'FIXED', 0);

INSERT INTO m_bom_line (bom_id, role, child_bom_id, sequence, qty_type)
VALUES ('UNIT_DUPLEX_STD', 'PAIR', 'DUPLEX_SET_STD', 100, 'FIXED');

-- Under DUPLEX_SET_STD: 2 lines (UNIT_A rotation=0, UNIT_B rotation=π)
INSERT INTO m_bom_line (bom_id, role, child_bom_id, rotation_rule, sequence)
VALUES ('DUPLEX_SET_STD', 'UNIT_A', 'DUPLEX_SINGLE_UNIT_STD', '0', 10);

INSERT INTO m_bom_line (bom_id, role, child_bom_id, rotation_rule, sequence)
VALUES ('DUPLEX_SET_STD', 'UNIT_B', 'DUPLEX_SINGLE_UNIT_STD', '3.14159265358979', 20);

-- Under DUPLEX_SINGLE_UNIT_STD: 9 room lines (flat — all rooms both storeys)
-- Space dimensions copied from existing FLOOR_DX_L1_STD / FLOOR_DX_L2_STD lines

-- Ground floor rooms
INSERT INTO m_bom_line (bom_id, role, child_bom_id, sequence, space_width_mm, space_depth_mm, space_height_mm)
VALUES ('DUPLEX_SINGLE_UNIT_STD', 'LIVING',      'LIVING_SET',           10, 4871, 1400, 1170);

INSERT INTO m_bom_line (bom_id, role, child_bom_id, sequence, space_width_mm, space_depth_mm, space_height_mm)
VALUES ('DUPLEX_SINGLE_UNIT_STD', 'DINING',      'DINING_SET',           20, 1950, 2450, 750);

INSERT INTO m_bom_line (bom_id, role, child_bom_id, sequence, space_width_mm, space_depth_mm, space_height_mm)
VALUES ('DUPLEX_SINGLE_UNIT_STD', 'KITCHEN_GF',  'KITCHEN_CABINET_SET',  30, 1500, 600, 1900);

INSERT INTO m_bom_line (bom_id, role, child_bom_id, sequence)
VALUES ('DUPLEX_SINGLE_UNIT_STD', 'BATHROOM_GF', 'DUPLEX_BATHROOM_SET',  40);

-- Upper floor rooms
INSERT INTO m_bom_line (bom_id, role, child_bom_id, sequence, space_width_mm, space_depth_mm, space_height_mm)
VALUES ('DUPLEX_SINGLE_UNIT_STD', 'BEDROOM',     'BED_SET',              50, 3000, 600, 2000);

INSERT INTO m_bom_line (bom_id, role, child_bom_id, sequence, space_width_mm, space_depth_mm, space_height_mm)
VALUES ('DUPLEX_SINGLE_UNIT_STD', 'MASTER',      'BED_SET_MASTER',       60, 1820, 600, 2000);

INSERT INTO m_bom_line (bom_id, role, child_bom_id, sequence, space_width_mm, space_depth_mm, space_height_mm)
VALUES ('DUPLEX_SINGLE_UNIT_STD', 'WARDROBE',    'WARDROBE_SET',         70, 2400, 600, 2100);

INSERT INTO m_bom_line (bom_id, role, child_bom_id, sequence)
VALUES ('DUPLEX_SINGLE_UNIT_STD', 'BATHROOM_UF', 'DUPLEX_BATHROOM_SET',  80);

INSERT INTO m_bom_line (bom_id, role, child_bom_id, sequence, space_width_mm, space_depth_mm, space_height_mm)
VALUES ('DUPLEX_SINGLE_UNIT_STD', 'KITCHEN_UF',  'KITCHEN_CABINET_SET',  90, 1500, 600, 1900);

-- ────────────────────────────────────────────────────────────
-- 1c. Update descriptions on existing DX BOMs
-- ────────────────────────────────────────────────────────────

UPDATE m_bom SET description =
    'Duplex building: two mirrored half-width homes sharing a party wall, common roof, floor slabs, and central MEP trunk. First-level children are shared structural elements (GROUND_SLAB, UPPER_SLAB, ROOF, MEP_TRUNK) plus the DUPLEX_SET_STD pair container. Legacy LEVEL_1/LEVEL_2 floor entries retained for current compiler path — Phase F3 will switch compiler to read the PAIR→SINGLE_UNIT tree instead.'
WHERE bom_id = 'UNIT_DUPLEX_STD';

UPDATE m_bom SET description =
    '[Legacy compiler path] Ground floor rooms for one duplex half-unit: living, dining, kitchen, bathroom. Used by current compiler for storey=Ground room matching. Target model: replaced by DUPLEX_SINGLE_UNIT_STD flat room list.'
WHERE bom_id = 'FLOOR_DX_L1_STD';

UPDATE m_bom SET description =
    '[Legacy compiler path] Upper floor rooms for one duplex half-unit: master bedroom, second bedroom, wardrobe, bathroom, kitchen. Used by current compiler for storey=Upper room matching. Target model: replaced by DUPLEX_SINGLE_UNIT_STD flat room list.'
WHERE bom_id = 'FLOOR_DX_L2_STD';

UPDATE m_bom SET description =
    'Duplex bathroom fixtures: WC, lavatory, bath/shower fittings. Shared SET used by both ground-floor and upper-floor bathrooms within a single half-unit. Referenced twice in DUPLEX_SINGLE_UNIT_STD (BATHROOM_GF and BATHROOM_UF) because each storey genuinely has its own bathroom — the duplication is physical reality.'
WHERE bom_id = 'DUPLEX_BATHROOM_SET';

-- ────────────────────────────────────────────────────────────
-- 1d. M_BomCategory lookup entries for new codes
-- ────────────────────────────────────────────────────────────

INSERT OR IGNORE INTO M_BomCategory (M_BomCategory_ID, Name, Description)
VALUES ('PR', 'Pair',      'Duplex unit pair container (two mirrored half-units)');

INSERT OR IGNORE INTO M_BomCategory (M_BomCategory_ID, Name, Description)
VALUES ('HU', 'Half-Unit', 'Single half-unit of a duplex (one complete dwelling)');

INSERT OR IGNORE INTO M_BomCategory (M_BomCategory_ID, Name, Description)
VALUES ('MP', 'MEP',       'Mechanical, Electrical, Plumbing trunk/service group');

-- ────────────────────────────────────────────────────────────
-- 1e. c_orderline — NO CHANGE
-- ────────────────────────────────────────────────────────────
-- orientation=π stays on L2 c_orderline row for current compiler.
-- Phase F3 compiler refactor will clear it and read rotation
-- from BOM UNIT_B line instead.
