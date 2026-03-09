-- migration_P02_deactivate_sh_dx.sql
-- Phase P0.2: Deactivate SH/DX placement rows + drop lod_element_placement view.
--
-- SH/DX placement data now lives in m_bom_line (BOM.db) — backfilled in
-- migration_P02_bom_walk_columns.sql. ad_element_placement rows deactivated,
-- not deleted (archive + audit trail). Terminal 51K rows stay active for Phase B.
--
-- Target: component_library.db
-- Run:    sqlite3 library/component_library.db < migration/migration_P02_deactivate_sh_dx.sql

-- ── 1. Deactivate SH/DX rows ──────────────────────────────────────────

UPDATE ad_element_placement SET is_active = 0
WHERE building_type IN ('Ifc4_SampleHouse', 'Ifc2x3_Duplex');

-- ── 2. Drop the view (was over ad_element_placement) ──────────────────

DROP VIEW IF EXISTS lod_element_placement;

-- ── 3. Verify ──────────────────────────────────────────────────────────

SELECT 'active_SH', COUNT(*) FROM ad_element_placement
WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 1;

SELECT 'active_DX', COUNT(*) FROM ad_element_placement
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1;

SELECT 'active_TE', COUNT(*) FROM ad_element_placement
WHERE building_type = 'SJTII_Terminal' AND is_active = 1;

SELECT 'deactivated_SH', COUNT(*) FROM ad_element_placement
WHERE building_type = 'Ifc4_SampleHouse' AND is_active = 0;

SELECT 'deactivated_DX', COUNT(*) FROM ad_element_placement
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 0;
