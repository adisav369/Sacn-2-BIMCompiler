-- W002: Add M_Product_ID to C_OrderLine (iDempiere alignment)
--
-- iDempiere pattern: C_OrderLine.M_Product_ID references M_Product.
-- If M_Product.IsBOM = Y (i.e. product_id exists as m_bom.bom_id),
-- the backend MUST explode the BOM recursively before processing.
-- Frontend may send 1 unexploded parent line (thin pipe) or a
-- fully exploded tree (if user modified). Backend handles both.
--
-- M_Product_ID replaces the semantic role of family_ref. Both columns
-- coexist during transition; family_ref stays as display-friendly name.

ALTER TABLE C_OrderLine ADD COLUMN M_Product_ID TEXT;

-- Backfill: existing rows use family_ref as the product reference
UPDATE C_OrderLine SET M_Product_ID = family_ref WHERE M_Product_ID IS NULL;

-- Index for BOM explosion lookups
CREATE INDEX IF NOT EXISTS idx_orderline_product ON C_OrderLine(M_Product_ID);
