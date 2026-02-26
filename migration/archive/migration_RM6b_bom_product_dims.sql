-- Phase RM-6b: BOM Furniture Product Dimensions + Space Type
--
-- Supplementary migration for Phase RM-6 BOM Anchor flow.
-- These rows are required so RelationalResolver.computeBomAnchor() can
-- look up intrinsic dims (width, depth, height) for each BOM child via
-- ctx.productDims().get(pf.namePattern()) — namePattern = child_name_pattern
-- in ad_bom_child (LIKE patterns stripped to base prefix).
--
-- ad_product_dim units: meters (verified — FURN_DINING_CHAIR=0.45m in DB)
-- product_type='FURNITURE' = leaf elements in furniture BOM assemblies
--
-- ERP analogy: M_Product (catalog master) for each BOM child component.
-- ─────────────────────────────────────────────────────────────────────────────

-- ─────────────────────────────────────────────────────────────────────────────
-- Step A: Add COMMON to ad_space_type
--
-- TB-LKTN 'common' room = open-plan combining living + dining + kitchen.
-- COMMON dispatches 3 slot rules: KITCHEN_CABINET_SET(≥0m²) + DINING_SET(≥8m²)
-- + LIVING_SET(≥15m²). common room = 3700×6200mm = 22.94m² → all 3 fire.
-- wall_rule=AS_REQUIRED (open-plan hybrid, no forced enclosure).
-- is_open_plan=1 matches OPEN_PLAN GeometryValidator exemption semantics.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT OR IGNORE INTO ad_space_type
    (space_type_id, category, omniclass_code, wall_rule,
     is_sleeping_room, is_open_plan, is_exterior,
     min_area, min_dimension, requires_window, requires_egress,
     structural_grid, beam_max_span, code_reference, is_active)
VALUES
    ('COMMON', 'HABITABLE', '13-21 11 00', 'AS_REQUIRED',
     0, 1, 0,
     0.0, 0.0, 1, 0,
     0, 8.0, NULL, 1);

-- ─────────────────────────────────────────────────────────────────────────────
-- Step B: INSERT product dims for BOM child name_patterns
--
-- FurnitureBOMResolver.resolveForRoom() returns PlacedFurniture with
-- namePattern = the LIKE pattern stripped of '%' suffix from ad_bom_child.child_name_pattern.
-- RelationalResolver.computeBomAnchor() looks up dims in ctx.productDims()
-- using the namePattern as key. If not found, defaults to 0.5×0.5×1.0m.
--
-- Dims sourced from standard manufacturer catalogues and IFC component library.
-- All dimensions in meters.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT OR IGNORE INTO ad_product_dim
    (product_id, product_type, width, depth, height, is_active)
VALUES
    -- Bedroom assemblies (BED_SET, BED_SET_MASTER)
    ('Bed_Queen',              'FURNITURE', 1.500, 2.000, 0.500, 1),
    ('Bed_King',               'FURNITURE', 2.032, 1.981, 0.500, 1),
    ('Side_Table',             'FURNITURE', 0.500, 0.500, 0.600, 1),
    ('Side_Table_Cube_610',    'FURNITURE', 0.610, 0.610, 0.610, 1),
    ('Wardrobe',               'FURNITURE', 1.200, 0.600, 2.100, 1),
    ('Desk',                   'FURNITURE', 1.200, 0.600, 0.750, 1),

    -- Living room assemblies (LIVING_SET)
    ('Sofa',                   'FURNITURE', 2.000, 0.800, 0.450, 1),
    ('Sofa_Loveseat',          'FURNITURE', 1.600, 0.800, 0.450, 1),
    ('Coffee_Table',           'FURNITURE', 1.000, 0.600, 0.450, 1),
    ('Piano',                  'FURNITURE', 1.371, 0.600, 1.170, 1),

    -- Dining assemblies (DINING_SET)
    ('Dining_Table_With_Chairs','FURNITURE', 1.500, 0.900, 0.750, 1),
    ('Dining_Chair',           'FURNITURE', 0.450, 0.450, 0.450, 1),

    -- Kitchen assemblies (KITCHEN_CABINET_SET)
    ('Base_Cabinet',           'FURNITURE', 0.600, 0.600, 0.900, 1),
    ('Upper_Cabinet',          'FURNITURE', 0.600, 0.350, 0.900, 1),
    ('Tall_Cabinet',           'FURNITURE', 0.600, 0.600, 2.000, 1),
    ('Counter_Top',            'FURNITURE', 0.600, 0.600, 0.050, 1),
    ('Sink_Island',            'FURNITURE', 0.500, 0.500, 0.850, 1),

    -- Bathroom assemblies (BATHROOM_VANITY_SET)
    ('Vanity_Cabinet',         'FURNITURE', 0.800, 0.500, 0.850, 1);

-- ─────────────────────────────────────────────────────────────────────────────
-- Step C: Update expected_elements after BOM expansion
--
-- BOM expansion adds furniture children to element_instances:
--   SH:      55 → 63   (+8:  DINING_SET×7 + LIVING_SET×6 + BED_SET×5 + BED_SET_MASTER×4
--                             - 14 deactivated leaf rows = net +8)
--   DX:    1085 → 1197 (+112: multiple KITCHEN/DINING/LIVING/BED BOMs across 20 rooms)
--   TB-LKTN: 102 → 138 (+36:  BED_SET×3 rooms + KITCHEN_CABINET_SET + DINING_SET + LIVING_SET
--                             + TOILET_BLOCK_FIXTURES - 10 deactivated leaf rows = net +36)
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE ad_building_registry SET expected_elements = 63   WHERE building_id = 'Ifc4_SampleHouse';
UPDATE ad_building_registry SET expected_elements = 1197 WHERE building_id = 'Ifc2x3_Duplex';
UPDATE ad_building_registry SET expected_elements = 138  WHERE building_id = 'TB_LKTN';

-- ─────────────────────────────────────────────────────────────────────────────
-- Verification queries (run manually to confirm):
--
-- Space type added:
--   SELECT * FROM ad_space_type WHERE space_type_id = 'COMMON';
--
-- Product dims for furniture:
--   SELECT product_id, width, depth, height FROM ad_product_dim
--    WHERE product_type = 'FURNITURE' AND product_id NOT LIKE 'FURN_%';
--
-- Expected element counts:
--   SELECT building_id, expected_elements FROM ad_building_registry;
-- ─────────────────────────────────────────────────────────────────────────────
