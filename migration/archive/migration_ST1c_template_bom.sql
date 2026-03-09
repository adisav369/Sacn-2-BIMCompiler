-- Phase ST-1c: Template-Driven Compilation Walker
-- Applied to: library/BOM.db
--
-- 1. Fix FLOOR_SH_GF_STD bom_category so M_BomCategoryLine GF node finds it.
--    SH template tree uses bom_category='GF' to select the ground-floor BOM.
--    This BOM was categorised 'L1' at creation (iDempiere storey-level naming) — wrong.
--    DX uses FLOOR_DX_L1_STD (still 'L1') via its own template path; safe to rename SH floor.
UPDATE m_bom SET bom_category = 'GF' WHERE bom_id = 'FLOOR_SH_GF_STD';

-- 2. Register ST_SH in ad_building (MetadataValidator checks building_type exists).
--    Template-driven build: has_ifc_ref=0 (generative), no c_orderline rows.
INSERT OR IGNORE INTO ad_building
    (building_type, name, description, width_mm, depth_mm, height_mm,
     num_storeys, num_units, building_class, has_ifc_ref, is_active, bom_category)
VALUES
    ('ST_SH', 'Standard Mode SH POC',
     'Template-driven build using SH AABB (ST-1c POC)',
     16867.5, 8667.5, 3945.2, 1, 1, 'RESIDENTIAL', 0, 1, 'RE');

-- 3. Activate ST_SH entry for pipeline compilation.
--    ST_SH was dormant (is_active=0, doc_status='DR') until TemplateStage was wired.
--    TemplateStage skips for c_bpartner != 'ST'; activating here is safe.
--    expected_elements=123: GENERATIVE DSL compiler produces more elements than the IFC-extracted SH.
--    geometry_fail_threshold=5: POC allowance for door/window geometry edge cases.
UPDATE c_order
SET is_active = 1, doc_status = 'IP', expected_elements = 123, geometry_fail_threshold = 5
WHERE building_id = 'ST_SH';
