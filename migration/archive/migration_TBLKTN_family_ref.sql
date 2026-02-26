-- Phase RM-11 Step 2: Populate family_ref for TB_LKTN furniture/fixtures
-- Root Cause 2 fix: all 12 IfcFurnishingElement rows had family_ref = NULL
-- Mapping based on room context (host_ref) and dimensions in ad_element_rule

-- ─── New products not yet in ad_product_dim ─────────────────────────────────
-- These are standard Malaysian residential furniture items from RUMAH RAKYAT
-- Dimensions sourced from ad_element_rule width_mm/depth_mm for TB_LKTN.

INSERT OR IGNORE INTO ad_product_dim
    (product_id, product_type, width, depth, height, clear_front, is_active)
VALUES
    -- Living room sofa (element_9: 2000×800×150mm)
    ('FURN_SOFA',          'FURNITURE', 2.0,  0.8,  0.45, 0.6,  1),
    -- TV unit / entertainment cabinet (element_10: 1200×600×150mm floor unit)
    ('FURN_TV_UNIT',       'FURNITURE', 1.2,  0.6,  0.45, 0.0,  1),
    -- Dining table 4-person (element_11: 1500×600×750mm)
    ('FURN_DINING_TABLE',  'FURNITURE', 1.5,  0.9,  0.75, 0.6,  1),
    -- Dining chair (elements_12, 13)
    ('FURN_DINING_CHAIR',  'FURNITURE', 0.45, 0.45, 0.45, 0.0,  1);

-- ─── Populate family_ref for all 12 TB_LKTN IfcFurnishingElement rows ────────
-- Element_1: common room, 1200×600×850mm near north wall → wardrobe/cabinet footprint
UPDATE ad_element_rule
SET family_ref = 'FURN_WARDROBE'
WHERE building_type = 'TB_LKTN' AND element_ref = 'IfcFurnishingElement_1';

-- Element_2: bilik_mandi (bathroom), 500×400×800mm → washbasin/sink
UPDATE ad_element_rule
SET family_ref = 'FIXTURE_SINK'
WHERE building_type = 'TB_LKTN' AND element_ref = 'IfcFurnishingElement_2';

-- Element_4: bilik_mandi (bathroom), 900×900×1829mm → shower stall
UPDATE ad_element_rule
SET family_ref = 'FIXTURE_SHOWER'
WHERE building_type = 'TB_LKTN' AND element_ref = 'IfcFurnishingElement_4';

-- Element_5: tandas (toilet room), 400×700×787mm → WC bowl
UPDATE ad_element_rule
SET family_ref = 'FIXTURE_TOILET'
WHERE building_type = 'TB_LKTN' AND element_ref = 'IfcFurnishingElement_5';

-- Element_6: bilik_utama (master bedroom), 1500×1900×150mm → double bed
UPDATE ad_element_rule
SET family_ref = 'FURN_BED_DOUBLE'
WHERE building_type = 'TB_LKTN' AND element_ref = 'IfcFurnishingElement_6';

-- Element_7: bilik_2 (bedroom 2), 900×1900×150mm → single bed
UPDATE ad_element_rule
SET family_ref = 'FURN_BED_SINGLE'
WHERE building_type = 'TB_LKTN' AND element_ref = 'IfcFurnishingElement_7';

-- Element_8: bilik_3 (bedroom 3), 900×1900×150mm → single bed
UPDATE ad_element_rule
SET family_ref = 'FURN_BED_SINGLE'
WHERE building_type = 'TB_LKTN' AND element_ref = 'IfcFurnishingElement_8';

-- Element_9: common (living area), 2000×800×150mm → sofa
UPDATE ad_element_rule
SET family_ref = 'FURN_SOFA'
WHERE building_type = 'TB_LKTN' AND element_ref = 'IfcFurnishingElement_9';

-- Element_10: common (living area), 1200×600×150mm near south wall → TV unit
UPDATE ad_element_rule
SET family_ref = 'FURN_TV_UNIT'
WHERE building_type = 'TB_LKTN' AND element_ref = 'IfcFurnishingElement_10';

-- Element_11: common (living/dining area), 1500×600×0mm → dining table
UPDATE ad_element_rule
SET family_ref = 'FURN_DINING_TABLE'
WHERE building_type = 'TB_LKTN' AND element_ref = 'IfcFurnishingElement_11';

-- Element_12: common (dining area), 600×600×0mm → dining chair
UPDATE ad_element_rule
SET family_ref = 'FURN_DINING_CHAIR'
WHERE building_type = 'TB_LKTN' AND element_ref = 'IfcFurnishingElement_12';

-- Element_13: common (dining area), 700×700×0mm → dining chair
UPDATE ad_element_rule
SET family_ref = 'FURN_DINING_CHAIR'
WHERE building_type = 'TB_LKTN' AND element_ref = 'IfcFurnishingElement_13';

-- ─── Verification query ───────────────────────────────────────────────────────
-- Expected: 0 rows (zero NULL family_ref for TB_LKTN furniture)
-- SELECT COUNT(*) FROM ad_element_rule
-- WHERE building_type='TB_LKTN'
--   AND ifc_class IN ('IfcFurnishingElement','IfcSanitaryTerminal','IfcFurniture')
--   AND family_ref IS NULL;
