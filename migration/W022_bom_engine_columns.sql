-- W022_bom_engine_columns.sql
-- S272 BOM Engine Phase 2: Add 10 columns to m_bom_line for recomposition engine.
-- Implementing BOM_ENGINE_SPEC.md §8.1 — Witness: W-BOM-ENGINE
-- Append-only. Never modify existing migrations.

ALTER TABLE m_bom_line ADD COLUMN mandatory       INTEGER DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN edge_offset_mm   REAL DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN buffer_mm         REAL DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN min_count         INTEGER DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN max_count         INTEGER DEFAULT NULL;
ALTER TABLE m_bom_line ADD COLUMN fill_axis         TEXT DEFAULT 'x';
ALTER TABLE m_bom_line ADD COLUMN creates_grid      INTEGER DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN drag_axis         TEXT DEFAULT NULL;
ALTER TABLE m_bom_line ADD COLUMN grid_shared_key   TEXT DEFAULT NULL;
ALTER TABLE m_bom_line ADD COLUMN grid_editable     INTEGER DEFAULT 1;
