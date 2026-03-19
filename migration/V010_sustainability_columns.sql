-- V010_sustainability_columns.sql
-- Sustainability columns on M_Product for carbon/lifecycle tracking
-- APPEND-ONLY: never modify this migration after first run
-- Implementing CORE_SRS.md §5.1 — Witness: W-SUSTAINABILITY-SCHEMA

ALTER TABLE M_Product ADD COLUMN carbon_kg_per_unit REAL DEFAULT 0;
ALTER TABLE M_Product ADD COLUMN recyclability TEXT DEFAULT 'UNKNOWN';
ALTER TABLE M_Product ADD COLUMN eol_strategy TEXT DEFAULT 'LANDFILL';
ALTER TABLE M_Product ADD COLUMN lifespan_years INTEGER DEFAULT 50;
ALTER TABLE M_Product ADD COLUMN maintenance_interval_months INTEGER DEFAULT 12;
