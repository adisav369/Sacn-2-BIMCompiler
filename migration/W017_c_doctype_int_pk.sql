-- W017: Tier 2 — C_DocType INTEGER surrogate PK (BOM.db template)
-- Reference: docs/ID_NAME_VALUE_STUDY.md §3.4, prompts/33
-- Strategy: Add INTEGER surrogate alongside TEXT C_DocType_ID.
--           C_DocType_ID TEXT kept — Java queries unchanged.
-- Phase A: schema only, zero Java changes.
-- Apply to SH_BOM.db as proof. Remaining 33 rebuilt via re-extraction.

-- Add Value (SearchKey) — copy of TEXT PK for iDempiere convention
ALTER TABLE C_DocType ADD COLUMN Value TEXT;
UPDATE C_DocType SET Value = C_DocType_ID WHERE Value IS NULL;

-- Add integer surrogate (not actual PK — TEXT PK stays)
ALTER TABLE C_DocType ADD COLUMN C_DocType_ID_int INTEGER;
UPDATE C_DocType SET C_DocType_ID_int = ROWID WHERE C_DocType_ID_int IS NULL;

-- Indexes for dual-key lookup
CREATE UNIQUE INDEX IF NOT EXISTS idx_c_doctype_value ON C_DocType(Value);
CREATE UNIQUE INDEX IF NOT EXISTS idx_c_doctype_int ON C_DocType(C_DocType_ID_int);
