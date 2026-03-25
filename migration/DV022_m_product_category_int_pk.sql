-- DV022: Tier 2 — M_Product_Category INTEGER surrogate PK (ERP.db)
-- Reference: docs/ID_NAME_VALUE_STUDY.md §3.4, prompts/33
-- Strategy: Add INTEGER surrogate alongside TEXT PK. Do NOT drop TEXT PK.
-- Phase A: schema only, zero Java changes.

-- Add Value (SearchKey) — copy of TEXT PK for iDempiere convention
ALTER TABLE M_Product_Category ADD COLUMN Value TEXT;
UPDATE M_Product_Category SET Value = M_Product_Category_ID WHERE Value IS NULL;

-- Add integer surrogate PK (not actual PK — TEXT PK stays)
ALTER TABLE M_Product_Category ADD COLUMN M_Product_Category_ID_int INTEGER;
UPDATE M_Product_Category SET M_Product_Category_ID_int = ROWID WHERE M_Product_Category_ID_int IS NULL;

-- Indexes for dual-key lookup
CREATE UNIQUE INDEX IF NOT EXISTS idx_m_product_category_value ON M_Product_Category(Value);
CREATE UNIQUE INDEX IF NOT EXISTS idx_m_product_category_int ON M_Product_Category(M_Product_Category_ID_int);
