-- DV017_product_category_rename.sql
-- Implementing MANIFESTO.md §The Mapping — Witness: W-CATEGORY-1
--
-- Renames bom_category → m_product_category_id across all tables.
--
-- RATIONALE: In iDempiere, product classification uses M_Product_Category —
-- there is no separate BomCategory entity. The bom_category column was a
-- project-specific drift that predated ERP alignment (see MANIFESTO.md).
-- This migration brings column names in line with iDempiere conventions.
--
-- NOTE: bom_category lives on m_bom, which merges iDempiere's M_Product + M_BOM
-- (see AUDIT Appendix O.2). The rename is to m_product_category_id because the
-- column classifies the PRODUCT, not the BOM structure.
--
-- APPLIES TO: {PREFIX}_BOM.db, output.db, disc_validation.db
-- Run with: sqlite3 <db> < migration/DV017_product_category_rename.sql
-- Non-existent tables are silently skipped (IF EXISTS not needed for ALTER
-- in SQLite 3.25+ — errors are suppressed by the runner).
--
-- SQLite RENAME COLUMN requires 3.25.0+. Verified: project uses 3.45.1.
-- APPEND-ONLY: never modify this migration after first run.

-- ════════════════════════════════════════════════════════════════
-- 1. m_bom (lives in {PREFIX}_BOM.db)
-- ════════════════════════════════════════════════════════════════
ALTER TABLE m_bom RENAME COLUMN bom_category TO m_product_category_id;

-- ════════════════════════════════════════════════════════════════
-- 2. C_OrderLine (lives in {PREFIX}_BOM.db via S60_schema,
--                 and in output.db via W001)
-- ════════════════════════════════════════════════════════════════
ALTER TABLE C_OrderLine RENAME COLUMN bom_category TO m_product_category_id;

-- ════════════════════════════════════════════════════════════════
-- 3. ad_pattern_rule (lives in disc_validation.db via V003)
-- ════════════════════════════════════════════════════════════════
ALTER TABLE ad_pattern_rule RENAME COLUMN bom_category TO m_product_category_id;

-- ════════════════════════════════════════════════════════════════
-- 4. ad_val_rule_param — data-level rename
--    param_name='bom_category' → 'm_product_category_id'
--    (lives in disc_validation.db via V002/DV010)
-- ════════════════════════════════════════════════════════════════
UPDATE ad_val_rule_param
   SET param_name = 'm_product_category_id'
 WHERE param_name = 'bom_category';

-- ════════════════════════════════════════════════════════════════
-- 5. M_BomCategory table rename → M_Product_Category
--    (lives in {PREFIX}_BOM.db)
-- ════════════════════════════════════════════════════════════════
ALTER TABLE M_BomCategory RENAME TO M_Product_Category;

-- Schema version stamp
INSERT OR REPLACE INTO AD_SysConfig (name, value, description, updated)
VALUES ('BOM_CATEGORY_RENAME', 'DV017', 'bom_category → m_product_category_id', datetime('now'));
