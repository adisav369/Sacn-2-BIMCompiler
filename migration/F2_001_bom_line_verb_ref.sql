-- F2_001_bom_line_verb_ref.sql — FACTORIZE-v1 Phase F-2
-- Adds verb_ref column to m_bom_line for verb pattern compression.
-- verb_ref encodes the placement formula: TILE:nx:ny:stepX:stepY, ROUTE:..., etc.
-- NULL = unfactored (per-instance dx/dy/dz), backward compatible.
-- dx/dy/dz on a verb line = pattern origin (floor-relative).
--
-- Applied to: {PREFIX}_BOM.db (SH, DX, TE) and schema_snapshot_bom.sql inline DDL
-- Safe: additive only, no data modification.
-- Rollback: ALTER TABLE m_bom_line DROP COLUMN verb_ref;  (SQLite 3.35+)

ALTER TABLE m_bom_line ADD COLUMN verb_ref TEXT DEFAULT NULL;
