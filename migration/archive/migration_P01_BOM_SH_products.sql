-- ============================================================================
-- P0.1-BOM: SH Product Catalog — 11 new M_Product entries for SampleHouse
-- ============================================================================
-- Applies to: library/BOM.db
-- Prerequisite: P0.1-DEDUP complete (187 M_Product rows)
--
-- 11 new SH-specific products. 8 existing products reused (Dining_Chair,
-- Bed_Queen, FURN_DESK, FURN_PIANO, FURN_SOFA, FURN_COFFEE_TABLE,
-- FURN_DINING_TABLE, DOOR_D1_DOUBLE).
--
-- Dimensions: representative AABB from ad_element_placement averages.
-- Not critical — BOM lines store actual instance AABB in allocated_*_mm.
-- ============================================================================

INSERT OR IGNORE INTO M_Product (product_id, product_type, width, depth, height,
    M_AttributeSet_ID, Name, Description, extracted_from, ifc_class, is_active,
    M_Product_Category_ID)
VALUES
    -- IfcWall (2 types, 5 instances)
    ('WALL_SH_EXT_102BWK',
        'WALL', 0.289, 7.797, 2.884, 'BIM_Wall',
        'Exterior Wall 102Bwk',
        'Basic Wall:Wall-Ext_102Bwk-75Ins-100LBlk-12P',
        'Ifc4_SampleHouse', 'IfcWall', 1, 'IFC_WALL'),

    ('WALL_SH_PARTN_70M',
        'WALL', 0.094, 2.274, 2.335, 'BIM_Wall',
        'Partition Wall 70MStd',
        'Basic Wall:Wall-Partn_12P-70MStd-12P',
        'Ifc4_SampleHouse', 'IfcWall', 1, 'IFC_WALL'),

    -- IfcSlab (2 types, 2 instances)
    ('SLAB_SH_GROUND_SUSP',
        'SLAB', 0.470, 16.868, 8.668, 'BIM_Slab',
        'Ground Floor Suspended Slab',
        'Floor:Floor-Grnd-Susp_65Scr-80Ins-100Blk-75PC',
        'Ifc4_SampleHouse', 'IfcSlab', 1, 'IFC_SLAB'),

    ('SLAB_SH_SIMPLE',
        'SLAB', 0.165, 13.968, 5.768, 'BIM_Slab',
        'Simple Floor Slab',
        'Floor:Simple floor',
        'Ifc4_SampleHouse', 'IfcSlab', 1, 'IFC_SLAB'),

    -- IfcRoof (1 type, 1 instance)
    ('ROOF_SH_FLAT_4FELT',
        'STRUCTURAL', 1.734, 14.841, 7.285, 'BIM_Component',
        'Flat Roof 4Felt',
        'Basic Roof:Roof_Flat-4Felt-150Ins-50Scr-150Conc-12Plr',
        'Ifc4_SampleHouse', 'IfcRoof', 1, 'IFC_ROOF'),

    -- IfcMember (1 type, 20 instances)
    ('MEMBER_SH_CURTAIN_WALL',
        'STRUCTURAL', 0.499, 0.581, 1.226, 'BIM_Component',
        'Curtain Wall Member',
        'Curtain Wall:Curtain_Wall-Exterior_Glazing',
        'Ifc4_SampleHouse', 'IfcMember', 1, 'IFC_MEMBER'),

    -- IfcPlate (1 type, 6 instances)
    ('PLATE_SH_GLAZED',
        'STRUCTURAL', 0.829, 0.955, 3.027, 'BIM_Component',
        'Glazed System Panel',
        'System Panel:Glazed',
        'Ifc4_SampleHouse', 'IfcPlate', 1, 'IFC_PLATE'),

    -- IfcWindow (1 type, 4 instances)
    ('WINDOW_SH_1810x1210',
        'WINDOW', 0.353, 1.860, 1.210, 'BIM_Component',
        'Single Plain Window 1810x1210',
        'Windows_Sgl_Plain:1810x1210mm',
        'Ifc4_SampleHouse', 'IfcWindow', 1, 'IFC_WINDOW'),

    -- IfcDoor (2 types, 3 instances)
    ('DOOR_SH_EXT_DBL',
        'DOOR', 0.199, 1.860, 2.110, 'BIM_Component',
        'External Double Flush Door',
        'Doors_ExtDbl_Flush:1810x2110mm',
        'Ifc4_SampleHouse', 'IfcDoor', 1, 'IFC_DOOR'),

    ('DOOR_SH_INT_SGL',
        'DOOR', 0.178, 0.880, 2.145, 'BIM_Component',
        'Internal Single Door 810x2110',
        'Doors_IntSgl:810x2110mm',
        'Ifc4_SampleHouse', 'IfcDoor', 1, 'IFC_DOOR'),

    -- IfcFurnishingElement (1 type, 2 instances — Viper armchair)
    ('FURN_ARMCHAIR_VIPER',
        'FURNITURE', 1.426, 1.395, 0.918, 'BIM_Component',
        'Armchair Viper',
        'Furniture_Chair_Viper:1120x940x350mm',
        'Ifc4_SampleHouse', 'IfcFurnishingElement', 1, 'IFC_FURNISHING');

-- ============================================================================
-- END: 11 new SH products. Final M_Product count: 187 + 11 = 198
-- ============================================================================
