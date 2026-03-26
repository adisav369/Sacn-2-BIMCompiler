-- DV024: Phase D — Drop _int sidecar columns from ERP.db
-- Reference: prompts/36_tier2_phase_d_cleanup.md
-- Phase A+B added M_Product_Category_ID_int as transitional scaffold.
-- Phase C (S91) nativized INTEGER PKs in BOM.db. Sidecar is now orphaned.

-- Drop the index first
DROP INDEX IF EXISTS idx_m_product_category_int;

-- Drop the sidecar column (SQLite 3.35+ supports ALTER TABLE DROP COLUMN)
ALTER TABLE M_Product_Category DROP COLUMN M_Product_Category_ID_int;
