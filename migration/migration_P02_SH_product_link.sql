-- ============================================================================
-- P0.2: SH Product Link — Backfill M_Product_ID on 55 SH extraction rows
-- ============================================================================
-- Applies to: library/component_library.db → I_Element_Extraction table
-- Port of: migration/archive/migration_P01_BOM_SH_placement_link.sql
--          (which targeted the obsolete ad_element_placement table)
--
-- 18 UPDATE statements mapping element_ref → M_Product_ID for all 55
-- active SH rows. 11 new SH products + 7 existing reused products.
--
-- WHY THIS EXISTS: The IFCtoBOM Java pipeline reads M_Product_ID from
-- I_Element_Extraction to set child_product_id on BOM leaf lines. If
-- M_Product_ID is NULL, BOMWalker skips the line → 0 placements →
-- compilation silently produces nothing. This migration closes that gap.
-- ============================================================================

-- New SH products (11 products, 42 instances)
UPDATE I_Element_Extraction SET M_Product_ID = 'WALL_SH_EXT_102BWK'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Basic Wall:Wall-Ext_102Bwk-75Ins-100LBlk-12P';     -- 3 rows

UPDATE I_Element_Extraction SET M_Product_ID = 'WALL_SH_PARTN_70M'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Basic Wall:Wall-Partn_12P-70MStd-12P';              -- 2 rows

UPDATE I_Element_Extraction SET M_Product_ID = 'SLAB_SH_GROUND_SUSP'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Floor:Floor-Grnd-Susp_65Scr-80Ins-100Blk-75PC';    -- 1 row

UPDATE I_Element_Extraction SET M_Product_ID = 'SLAB_SH_SIMPLE'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Floor:Simple floor';                                 -- 1 row

UPDATE I_Element_Extraction SET M_Product_ID = 'ROOF_SH_FLAT_4FELT'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Basic Roof:Roof_Flat-4Felt-150Ins-50Scr-150Conc-12Plr'; -- 1 row

UPDATE I_Element_Extraction SET M_Product_ID = 'MEMBER_SH_CURTAIN_WALL'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Curtain Wall:Curtain_Wall-Exterior_Glazing';         -- 20 rows

UPDATE I_Element_Extraction SET M_Product_ID = 'PLATE_SH_GLAZED'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'System Panel:Glazed';                                -- 6 rows

UPDATE I_Element_Extraction SET M_Product_ID = 'WINDOW_SH_1810x1210'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Windows_Sgl_Plain:1810x1210mm';                      -- 4 rows

UPDATE I_Element_Extraction SET M_Product_ID = 'DOOR_SH_EXT_DBL'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Doors_ExtDbl_Flush:1810x2110mm';                     -- 1 row

UPDATE I_Element_Extraction SET M_Product_ID = 'DOOR_SH_INT_SGL'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Doors_IntSgl:810x2110mm';                            -- 2 rows

UPDATE I_Element_Extraction SET M_Product_ID = 'FURN_ARMCHAIR_VIPER'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Furniture_Chair_Viper:1120x940x350mm';               -- 2 rows

-- Existing reused products (7 products, 13 instances)
UPDATE I_Element_Extraction SET M_Product_ID = 'Dining_Chair'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Chair - Dining:Chair - Dining';                      -- 6 rows

UPDATE I_Element_Extraction SET M_Product_ID = 'Bed_Queen'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Furniture_Bed_1:1525x2007x355mm-Queen';              -- 1 row

UPDATE I_Element_Extraction SET M_Product_ID = 'FURN_DESK'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Furniture_Desk:1525x762mm';                          -- 1 row

UPDATE I_Element_Extraction SET M_Product_ID = 'FURN_PIANO'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Furniture_Piano:1370x600x1170mm';                    -- 1 row

UPDATE I_Element_Extraction SET M_Product_ID = 'FURN_SOFA'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Furniture_Couch_Viper:2290x950x340mm';               -- 1 row

UPDATE I_Element_Extraction SET M_Product_ID = 'FURN_COFFEE_TABLE'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Furniture_Table_Coffee_1:1200x550x450mm';            -- 1 row

UPDATE I_Element_Extraction SET M_Product_ID = 'FURN_DINING_TABLE'
    WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
    AND element_ref = 'Furniture_Table_Dining_w-Chairs_Rectangular:2000x1000x750mm_w-6_Seats'; -- 1 row

-- ============================================================================
-- VERIFY: No unmapped SH rows remain
-- ============================================================================
-- SELECT COUNT(*) FROM I_Element_Extraction
--   WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1
--   AND M_Product_ID IS NULL;
-- Expected: 0
-- ============================================================================
