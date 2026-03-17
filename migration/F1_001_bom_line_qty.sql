-- F1_001_bom_line_qty.sql — FACTORIZE-v1 Phase F-1
-- Adds qty column to m_bom_line for BOM factorization.
-- One type line with qty=N replaces N unfactored rows for the same product.
-- DEFAULT 1 preserves all existing data (trivial factorization).
--
-- Applied to: {PREFIX}_BOM.db (SH, DX, TE) and schema_snapshot_bom.sql
-- Safe: additive only, no data modification.
-- Rollback: ALTER TABLE m_bom_line DROP COLUMN qty;  (SQLite 3.35+)

ALTER TABLE m_bom_line ADD COLUMN qty INTEGER NOT NULL DEFAULT 1;
