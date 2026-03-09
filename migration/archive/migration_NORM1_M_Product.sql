-- Phase NORM-1: ad_product_dim → M_Product
-- Add component_id + bom_id FKs, insert assembly stubs, rename table.
-- iDempiere pattern: M_Product is the universal product master (leaves + assemblies).
-- Gate: 207 PASS / 1 RED / 1 SKIP unchanged.

PRAGMA legacy_alter_table = OFF;   -- ensure FK refs auto-update on rename

-- ── Step 1: add component_id (logical cross-DB FK → component_library.component_definitions) ──
ALTER TABLE ad_product_dim ADD COLUMN component_id INTEGER;

-- ── Step 2: add bom_id (FK → m_bom.bom_id) ──
ALTER TABLE ad_product_dim ADD COLUMN bom_id TEXT REFERENCES m_bom(bom_id);

-- ── Step 3: populate component_id via name-match from component_library ──
-- Run AFTER ATTACH 'library/component_library.db' AS lib in the same session.
-- UPDATE ad_product_dim
--   SET component_id = (
--       SELECT id FROM lib.component_definitions
--       WHERE name = ad_product_dim.product_id LIMIT 1);
-- This is applied via the shell script below (requires ATTACH).

-- ── Step 4: insert M_Product stub rows for MAKE-referenced BOMs ──
-- is_active=0: not active catalog products for placement.
-- bom_id set: FK link back to m_bom.
-- dims=0.001: non-zero sentinel (avoids dims ≤ 0 constraint violations if filter removed).
-- product_type = bom_type from m_bom.
INSERT OR IGNORE INTO ad_product_dim
    (product_id, product_type, width, depth, height, is_active, extracted_from, bom_id)
SELECT bom_id, bom_type, 0.001, 0.001, 0.001, 0, 'BOM_ASSEMBLY', bom_id
FROM m_bom
WHERE bom_id IN (
    SELECT DISTINCT child_bom_id FROM m_bom_line WHERE component_type = 'MAKE'
);

-- ── Step 5: rename table (SQLite 3.26+ auto-updates FK refs in all dependent tables) ──
ALTER TABLE ad_product_dim RENAME TO M_Product;
