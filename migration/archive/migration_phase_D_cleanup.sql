-- =============================================================================
-- Phase D Cleanup — Zero-risk table renames + deprecated table drop
-- Date: 2026-02-26
-- Gate: 197 PASS / 1 RED / 3 SKIP — unchanged
--
-- 1. DROP ad_space_type_furniture (deprecated — FurnitureTypeResolver deleted in G-1)
-- 2. RENAME ad_ref_value → ad_ref_list (iDempiere AD_Ref_List alignment)
-- 3. RENAME ad_compiler_config → ad_sysconfig (iDempiere AD_SysConfig alignment)
-- 4. DROP broken views referencing m_bom tables (moved to BOM.db in Phase 4)
--
-- Also cleaned up: v_qualified_bom, v_active_bom_assembly, v_component_leaf
-- (broken since BOM.db extraction — referenced m_bom/m_bom_line in component_library.db)
-- =============================================================================

-- Prerequisite: drop broken views that block ALTER TABLE RENAME
DROP VIEW IF EXISTS v_qualified_bom;
DROP VIEW IF EXISTS v_active_bom_assembly;
DROP VIEW IF EXISTS v_component_leaf;

-- 1. Drop deprecated table (37 rows, zero consumers in active code)
DROP TABLE IF EXISTS ad_space_type_furniture;

-- 2. Rename to match iDempiere AD_Ref_List (26 rows)
ALTER TABLE ad_ref_value RENAME TO ad_ref_list;

-- 3. Rename to match iDempiere AD_SysConfig (2 rows)
ALTER TABLE ad_compiler_config RENAME TO ad_sysconfig;

-- =============================================================================
-- Java code changes (same commit):
--   MEPAD.java:          ad_ref_value → ad_ref_list (SQL + javadoc)
--   CompilerConfig.java: ad_compiler_config → ad_sysconfig (SQL + javadoc)
--   MetadataIntegrityTest.java: removed ad_space_type_furniture from M13 satellites
--   create_ad_mep_schema.py: ad_ref_value → ad_ref_list (CREATE + INSERT + COUNT)
-- =============================================================================

-- Verification
SELECT 'ad_ref_list' AS expected, COUNT(*) AS rows FROM ad_ref_list;
SELECT 'ad_sysconfig' AS expected, COUNT(*) AS rows FROM ad_sysconfig;
SELECT 'ad_space_type_furniture should not exist' AS check,
       COUNT(*) AS found
  FROM sqlite_master
 WHERE type = 'table' AND name = 'ad_space_type_furniture';
