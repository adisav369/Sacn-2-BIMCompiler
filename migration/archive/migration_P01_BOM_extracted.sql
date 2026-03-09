-- ============================================================================
-- P0.1-BOM: EXTRACTED BOM Hierarchy — EXT_SH (55 BUY) + EXT_DX (1099 BUY)
-- ============================================================================
-- Applies to: library/BOM.db  (run from library/ directory)
-- Prerequisite:
--   1. migration_P01_BOM_SH_products.sql applied (198 M_Product rows)
--   2. migration_P01_BOM_SH_placement_link.sql applied (55 SH rows mapped)
--   3. component_library.db accessible at ./component_library.db
--
-- Rosetta Stone principle: EXTRACTED buildings are all BUY — every element
-- already exists in the extraction archive. No MAKE (assembly) needed.
-- The BOM is a flat list of BUY lines, one per extracted instance, each
-- carrying the centroid position and AABB dimensions from the original IFC.
--
-- Idempotent: DELETE WHERE bom_id LIKE 'EXT_%' before INSERT.
-- ============================================================================

ATTACH DATABASE './component_library.db' AS comp;

-- -------------------------------------------------------------------------
-- 1. CLEAN: Remove any prior EXT_* data (idempotent re-run)
-- -------------------------------------------------------------------------
DELETE FROM m_bom_line WHERE bom_id LIKE 'EXT_%';
DELETE FROM m_bom WHERE bom_id LIKE 'EXT_%';

-- -------------------------------------------------------------------------
-- 2. CREATE m_bom roots (2 UNIT BOMs, bom_category = EXTRACTED)
-- -------------------------------------------------------------------------
INSERT INTO m_bom (bom_id, bom_name, description, target_ifc_class,
    group_by, is_active, bom_type, bom_category, doc_sub_type, seq_no)
VALUES
    ('EXT_SH', 'Extracted SampleHouse',
     'Flat BOM: 55 BUY lines from Ifc4_SampleHouse extraction',
     'IfcBuilding', 'BUILDING', 1, 'UNIT', 'EXTRACTED', 'SH', 10),

    ('EXT_DX', 'Extracted Duplex',
     'Flat BOM: 1099 BUY lines from Ifc2x3_Duplex extraction',
     'IfcBuilding', 'BUILDING', 1, 'UNIT', 'EXTRACTED', 'DX', 10);

-- -------------------------------------------------------------------------
-- 3. INSERT BUY lines for SH (55 lines from ad_element_placement)
-- -------------------------------------------------------------------------
INSERT INTO m_bom_line (bom_id, child_product_id, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm,
    rotation_rule, component_type, is_active, qty_type)
SELECT
    'EXT_SH',
    p.M_Product_ID,
    p.ifc_class,
    ROW_NUMBER() OVER (ORDER BY p.storey, p.ifc_class, p.ordinal) * 10,
    ROUND((p.min_x + p.max_x) / 2.0, 6),
    ROUND((p.min_y + p.max_y) / 2.0, 6),
    ROUND((p.min_z + p.max_z) / 2.0, 6),
    CAST(ROUND((p.max_x - p.min_x) * 1000) AS INTEGER),
    CAST(ROUND((p.max_y - p.min_y) * 1000) AS INTEGER),
    CAST(ROUND((p.max_z - p.min_z) * 1000) AS INTEGER),
    COALESCE(p.orientation, '0'),
    'BUY', 1, 'FIXED'
FROM comp.ad_element_placement p
WHERE p.building_type = 'Ifc4_SampleHouse'
  AND p.is_active = 1
  AND p.M_Product_ID IS NOT NULL;

-- -------------------------------------------------------------------------
-- 4. INSERT BUY lines for DX (1099 lines from ad_element_placement)
-- -------------------------------------------------------------------------
INSERT INTO m_bom_line (bom_id, child_product_id, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm,
    rotation_rule, component_type, is_active, qty_type)
SELECT
    'EXT_DX',
    p.M_Product_ID,
    p.ifc_class,
    ROW_NUMBER() OVER (ORDER BY p.storey, p.ifc_class, p.ordinal) * 10,
    ROUND((p.min_x + p.max_x) / 2.0, 6),
    ROUND((p.min_y + p.max_y) / 2.0, 6),
    ROUND((p.min_z + p.max_z) / 2.0, 6),
    CAST(ROUND((p.max_x - p.min_x) * 1000) AS INTEGER),
    CAST(ROUND((p.max_y - p.min_y) * 1000) AS INTEGER),
    CAST(ROUND((p.max_z - p.min_z) * 1000) AS INTEGER),
    COALESCE(p.orientation, '0'),
    'BUY', 1, 'FIXED'
FROM comp.ad_element_placement p
WHERE p.building_type = 'Ifc2x3_Duplex'
  AND p.is_active = 1
  AND p.M_Product_ID IS NOT NULL;

DETACH DATABASE comp;

-- ============================================================================
-- VERIFY COUNTS
-- ============================================================================
-- SELECT bom_id, COUNT(*) FROM m_bom_line WHERE bom_id LIKE 'EXT_%' GROUP BY bom_id;
-- Expected: EXT_SH=55, EXT_DX=1099
--
-- SELECT bom_id, component_type, COUNT(*) FROM m_bom_line
--   WHERE bom_id LIKE 'EXT_%' GROUP BY bom_id, component_type;
-- Expected: EXT_SH|BUY|55, EXT_DX|BUY|1099  (no MAKE, no PHANTOM)
-- ============================================================================
