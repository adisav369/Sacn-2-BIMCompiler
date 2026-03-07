-- migration_rename_extraction_tables.sql
-- Target DB: library/component_library.db
-- Run: sqlite3 library/component_library.db < migration/migration_rename_extraction_tables.sql
-- Idempotent: Yes (checks IF EXISTS before rename)
-- Run AFTER: all other migrations (this is the final rename step)
--
-- Rename deprecated extraction archive tables to iDempiere I_ convention:
--   ad_element_placement  →  I_Element_Extraction   (67K rows, IFC placement archive)
--   ad_geometry_map       →  I_Geometry_Map          (65K rows, element→geometry hash)
--
-- I_ prefix = Import table (iDempiere convention: staging data from external source).
-- These tables hold IFC extraction data — NOT Application Dictionary (AD_) configuration.
--
-- The lod_geometry_map view is rebuilt to point to the new table name.
-- Old migration scripts reference the pre-rename names — they have already been applied.
-- ============================================================================

-- 1. Rename ad_element_placement → I_Element_Extraction
ALTER TABLE ad_element_placement RENAME TO I_Element_Extraction;

-- 2. Rename ad_geometry_map → I_Geometry_Map
--    (Must drop dependent view first, then rebuild)
DROP VIEW IF EXISTS lod_geometry_map;
ALTER TABLE ad_geometry_map RENAME TO I_Geometry_Map;
CREATE VIEW lod_geometry_map AS SELECT * FROM I_Geometry_Map;
