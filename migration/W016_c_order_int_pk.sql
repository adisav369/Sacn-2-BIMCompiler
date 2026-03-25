-- W016: Tier 2 — C_Order INTEGER surrogate PK (output_template.db)
-- Reference: docs/ID_NAME_VALUE_STUDY.md §3.4, prompts/33
-- Strategy: Add INTEGER surrogate alongside TEXT C_Order_ID.
--           output.db is created fresh each compile from Java DDL (BuildingWriter).
--           This migration updates the template only; Phase C updates Java DDL.
-- Phase A: schema only, zero Java changes.

-- c_order: add Value (SearchKey) + integer surrogate
ALTER TABLE c_order ADD COLUMN Value TEXT;
UPDATE c_order SET Value = C_Order_ID WHERE Value IS NULL;
ALTER TABLE c_order ADD COLUMN C_Order_ID_int INTEGER;

CREATE UNIQUE INDEX IF NOT EXISTS idx_c_order_value ON c_order(Value);
CREATE UNIQUE INDEX IF NOT EXISTS idx_c_order_int ON c_order(C_Order_ID_int);
