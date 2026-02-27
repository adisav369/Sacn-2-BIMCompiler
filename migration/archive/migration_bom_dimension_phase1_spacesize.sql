-- =============================================================================
-- BOM Dimension Phase 1 — SpaceSize Seeding
-- Seeds space_width_mm / space_depth_mm / space_height_mm on m_bom_line
--
-- Strategy:
--   1. Leaf children with product_ref → direct from ad_product_dim (metres→mm)
--   2. Leaf children with exact child_name_pattern = product_id → same
--   3. Leaf children with LIKE child_name_pattern → LIKE match
--   4. Sub-BOM children → bottom-up AABB from contained children
--   5. Second cascade pass for deeper nesting
--
-- Prerequisite: migration_bom_dimension_model.sql Parts 0-7
--               migration_bom_dimension_phase1_records.sql
-- Reference: docs/ConstructionAsERP.md Appendix C §C.4 Phase 1e
-- =============================================================================

-- ─── Step 1: product_ref → ad_product_dim ──────────────────────────────────
UPDATE m_bom_line
SET space_width_mm  = CAST(pd.width  * 1000 AS INTEGER),
    space_depth_mm  = CAST(pd.depth  * 1000 AS INTEGER),
    space_height_mm = CAST(pd.height * 1000 AS INTEGER)
FROM ad_product_dim pd
WHERE m_bom_line.product_ref = pd.product_id
  AND m_bom_line.product_ref IS NOT NULL;

-- ─── Step 2: exact child_name_pattern = product_id ─────────────────────────
UPDATE m_bom_line
SET space_width_mm  = CAST(pd.width  * 1000 AS INTEGER),
    space_depth_mm  = CAST(pd.depth  * 1000 AS INTEGER),
    space_height_mm = CAST(pd.height * 1000 AS INTEGER)
FROM ad_product_dim pd
WHERE m_bom_line.child_name_pattern = pd.product_id
  AND m_bom_line.product_ref IS NULL
  AND m_bom_line.space_width_mm = 0
  AND m_bom_line.role != 'BUFFER';

-- ─── Step 3: LIKE child_name_pattern → ad_product_dim ──────────────────────
UPDATE m_bom_line
SET space_width_mm  = CAST(pd.width  * 1000 AS INTEGER),
    space_depth_mm  = CAST(pd.depth  * 1000 AS INTEGER),
    space_height_mm = CAST(pd.height * 1000 AS INTEGER)
FROM ad_product_dim pd
WHERE pd.product_id LIKE m_bom_line.child_name_pattern
  AND m_bom_line.space_width_mm = 0
  AND m_bom_line.role != 'BUFFER'
  AND m_bom_line.is_active = 1;

-- ─── Step 4: Bottom-up AABB for sub-BOMs (first pass) ─────────────────────
WITH bom_aabb AS (
    SELECT
        bom_id,
        CAST(MAX(dx * 1000 + space_width_mm) - MIN(dx * 1000) AS INTEGER) AS aabb_w,
        CAST(MAX(dy * 1000 + space_depth_mm) - MIN(dy * 1000) AS INTEGER) AS aabb_d,
        CAST(MAX(dz * 1000 + space_height_mm) - MIN(dz * 1000) AS INTEGER) AS aabb_h
    FROM m_bom_line
    WHERE is_active = 1 AND role != 'BUFFER' AND space_width_mm > 0
    GROUP BY bom_id
)
UPDATE m_bom_line
SET space_width_mm  = ba.aabb_w,
    space_depth_mm  = ba.aabb_d,
    space_height_mm = ba.aabb_h
FROM bom_aabb ba
WHERE m_bom_line.child_bom_id = ba.bom_id
  AND m_bom_line.space_width_mm = 0;

-- ─── Step 5: Second cascade pass ──────────────────────────────────────────
WITH bom_aabb AS (
    SELECT
        bom_id,
        CAST(MAX(dx * 1000 + space_width_mm) - MIN(dx * 1000) AS INTEGER) AS aabb_w,
        CAST(MAX(dy * 1000 + space_depth_mm) - MIN(dy * 1000) AS INTEGER) AS aabb_d,
        CAST(MAX(dz * 1000 + space_height_mm) - MIN(dz * 1000) AS INTEGER) AS aabb_h
    FROM m_bom_line
    WHERE is_active = 1 AND role != 'BUFFER' AND space_width_mm > 0
    GROUP BY bom_id
)
UPDATE m_bom_line
SET space_width_mm  = ba.aabb_w,
    space_depth_mm  = ba.aabb_d,
    space_height_mm = ba.aabb_h
FROM bom_aabb ba
WHERE m_bom_line.child_bom_id = ba.bom_id
  AND m_bom_line.space_width_mm = 0;

-- ─── Verify ────────────────────────────────────────────────────────────────
SELECT 'SpaceSize seeded:' AS msg, COUNT(*) AS n FROM m_bom_line WHERE space_width_mm > 0;
SELECT 'SpaceSize zero (active, non-buffer):' AS msg, COUNT(*) AS n
FROM m_bom_line WHERE space_width_mm = 0 AND is_active=1 AND role != 'BUFFER';
SELECT 'Buffers (SpaceSize TBD):' AS msg, COUNT(*) AS n FROM m_bom_line WHERE role = 'BUFFER';
