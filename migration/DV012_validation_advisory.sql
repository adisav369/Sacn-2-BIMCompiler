-- DV012_validation_advisory.sql
-- Creates W_Validation_Advisory table in disc_validation.db
-- Purpose: Flywheel advisory output — surfaces dimension outliers and profile
-- anomalies to the BIM Designer panel for user review.
-- APPEND-ONLY: never modify this migration after first run
--
-- Implementing BIM_Designer_SRS.md §27.5 — Witness: W-FL-ADVISORY-4, W-FL-ADVISORY-5

CREATE TABLE IF NOT EXISTS W_Validation_Advisory (
    advisory_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type   TEXT NOT NULL,
    element_ref     TEXT,           -- element bomId (NULL for profile-level)
    layer           TEXT NOT NULL   -- 'DIMENSION', 'PROFILE', 'COMPLIANCE', 'SHAPE'
                    CHECK(layer IN ('DIMENSION','PROFILE','COMPLIANCE','SHAPE')),
    severity        TEXT NOT NULL   -- 'INFO', 'WARNING', 'SUGGESTION'
                    CHECK(severity IN ('INFO','WARNING','SUGGESTION')),
    rule_name       TEXT,           -- e.g. 'DIMENSION_RANGE_W:IfcWall'
    message         TEXT NOT NULL,  -- human-readable
    actual_value    REAL,
    expected_min    REAL,
    expected_max    REAL,
    suggestion      TEXT,           -- e.g. 'Nearest typical: 5000mm (from Esplanades)'
    created_at      TEXT DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_advisory_building ON W_Validation_Advisory(building_type);
CREATE INDEX IF NOT EXISTS idx_advisory_severity ON W_Validation_Advisory(severity);

-- Update schema version
INSERT OR REPLACE INTO AD_SysConfig (name, value, description, updated)
VALUES ('ADVISORY_VERSION', 'DV012', 'Validation advisory table', datetime('now'));
