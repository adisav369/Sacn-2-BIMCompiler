-- migration_P02_bom_walk_columns.sql
-- Phase P0.2: Add instance-specific columns to m_bom_line + backfill from ad_element_placement.
--
-- Why per-instance on m_bom_line (not derived from M_Product):
--   - M_Product has material for only 12/96 EXTRACTED products (furniture only)
--   - Storey is spatial partition, not derivable from BOM hierarchy (flat BOM = all BUY)
--   - orientation/element_ref/ordinal are instance-specific
--
-- Target: BOM.db
-- Prereq: library/component_library.db must exist alongside BOM.db.
-- Run:    sqlite3 library/BOM.db < migration/migration_P02_bom_walk_columns.sql

-- ── 1. Add 6 new columns ──────────────────────────────────────────────

ALTER TABLE m_bom_line ADD COLUMN storey         TEXT;
ALTER TABLE m_bom_line ADD COLUMN element_ref    TEXT;
ALTER TABLE m_bom_line ADD COLUMN ordinal        INTEGER DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN orientation    TEXT;
ALTER TABLE m_bom_line ADD COLUMN material_name  TEXT;
ALTER TABLE m_bom_line ADD COLUMN material_rgba  TEXT;

-- ── 2. Backfill from component_library.db ──────────────────────────────
-- Match: centroid at 1mm precision (same match proven by W-VERIFY digests).
-- Mapping: EXT_SH → Ifc4_SampleHouse, EXT_DX → Ifc2x3_Duplex.

ATTACH 'library/component_library.db' AS clib;

UPDATE m_bom_line
SET storey        = ep.storey,
    element_ref   = ep.element_ref,
    ordinal       = ep.ordinal,
    orientation   = ep.orientation,
    material_name = ep.material_name,
    material_rgba = ep.material_rgba
FROM clib.ad_element_placement ep
WHERE m_bom_line.bom_id IN ('EXT_SH', 'EXT_DX')
  AND m_bom_line.is_active = 1
  AND ep.is_active = 1
  AND ep.building_type = CASE m_bom_line.bom_id
        WHEN 'EXT_SH' THEN 'Ifc4_SampleHouse'
        WHEN 'EXT_DX' THEN 'Ifc2x3_Duplex'
      END
  AND ep.ifc_class = m_bom_line.role
  AND ROUND((ep.min_x + ep.max_x) / 2.0 * 1000) = ROUND(m_bom_line.dx * 1000)
  AND ROUND((ep.min_y + ep.max_y) / 2.0 * 1000) = ROUND(m_bom_line.dy * 1000)
  AND ROUND((ep.min_z + ep.max_z) / 2.0 * 1000) = ROUND(m_bom_line.dz * 1000);

DETACH clib;

-- ── 3. Verify: 0 NULL storey/element_ref for EXT_SH + EXT_DX ──────────
-- These should print 0 rows. If not, the backfill had gaps.

SELECT 'NULL_STOREY_SH', COUNT(*) FROM m_bom_line
WHERE bom_id = 'EXT_SH' AND is_active = 1 AND storey IS NULL;

SELECT 'NULL_STOREY_DX', COUNT(*) FROM m_bom_line
WHERE bom_id = 'EXT_DX' AND is_active = 1 AND storey IS NULL;

SELECT 'NULL_ELEMENT_REF_SH', COUNT(*) FROM m_bom_line
WHERE bom_id = 'EXT_SH' AND is_active = 1 AND element_ref IS NULL;

SELECT 'NULL_ELEMENT_REF_DX', COUNT(*) FROM m_bom_line
WHERE bom_id = 'EXT_DX' AND is_active = 1 AND element_ref IS NULL;
