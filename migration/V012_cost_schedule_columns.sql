-- V012_cost_schedule_columns.sql
-- 4D scheduling + 5D costing columns on M_Product
-- APPEND-ONLY: never modify this migration after first run
-- Implementing TIER1_SRS.md §1-§2 (4D/5D extension)

-- ── 5D Cost columns ────────────────────────────────────
ALTER TABLE M_Product ADD COLUMN unit_cost_rm REAL DEFAULT 0;
ALTER TABLE M_Product ADD COLUMN cost_uom TEXT DEFAULT 'EA';
ALTER TABLE M_Product ADD COLUMN cost_spec TEXT;

-- ── 4D Schedule columns ────────────────────────────────
ALTER TABLE M_Product ADD COLUMN construction_phase TEXT DEFAULT 'Architecture';
ALTER TABLE M_Product ADD COLUMN construction_sequence INTEGER DEFAULT 6;
ALTER TABLE M_Product ADD COLUMN labor_resource TEXT DEFAULT 'GENERAL';
ALTER TABLE M_Product ADD COLUMN labor_rate_rm_per_day REAL DEFAULT 145.0;
ALTER TABLE M_Product ADD COLUMN labor_crew_size INTEGER DEFAULT 2;
ALTER TABLE M_Product ADD COLUMN productivity_rate REAL DEFAULT 10.0;
ALTER TABLE M_Product ADD COLUMN equipment_type TEXT;
ALTER TABLE M_Product ADD COLUMN equipment_rate_rm_per_day REAL DEFAULT 0;
ALTER TABLE M_Product ADD COLUMN equipment_duration_factor REAL DEFAULT 0;
