-- DV011_building_profile.sql
-- Creates building profile table in disc_validation.db
-- Source: mined from 34 extracted DBs — element class distribution per building
-- Purpose: Layer 2 flywheel — "does this building's composition make sense?"
-- APPEND-ONLY: never modify this migration after first run
--
-- Implementing BBC.md §9.1 — Witness: W-DV-PROFILE

-- One row per (building, ifc_class) observation
CREATE TABLE IF NOT EXISTS ad_building_profile (
    ad_building_profile_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type           TEXT NOT NULL,       -- 'Ifc4_SampleHouse', 'Clinic_Plumbing'
    ifc_class               TEXT NOT NULL,       -- 'IfcWall', 'IfcFlowFitting'
    element_count           INTEGER NOT NULL,    -- raw count
    element_ratio           REAL NOT NULL,       -- percentage of total (0.0–100.0)
    total_elements          INTEGER NOT NULL,    -- total elements in this building
    class_count             INTEGER NOT NULL,    -- distinct IFC classes in this building
    created_at              TEXT DEFAULT (datetime('now')),
    UNIQUE(building_type, ifc_class)
);

CREATE INDEX IF NOT EXISTS idx_building_profile_type ON ad_building_profile(building_type);
CREATE INDEX IF NOT EXISTS idx_building_profile_class ON ad_building_profile(ifc_class);

-- Update schema version
INSERT OR REPLACE INTO AD_SysConfig (name, value, description, updated)
VALUES ('PROFILE_VERSION', 'DV011', 'Building profile table', datetime('now'));
