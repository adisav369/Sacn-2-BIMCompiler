-- V011_changelog.sql
-- Audit trail for design changes in work_output.db
-- APPEND-ONLY: never modify this migration after first run
-- Implementing TIER1_SRS.md §3.2 — Witness: W-AUDIT-DDL-1

CREATE TABLE IF NOT EXISTS bim_changelog (
    changelog_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    building_id     TEXT NOT NULL,
    variant_id      TEXT,
    user_id         TEXT DEFAULT 'local',
    timestamp       TEXT NOT NULL DEFAULT (datetime('now')),
    action          TEXT NOT NULL,
    entity_type     TEXT NOT NULL,
    entity_id       TEXT NOT NULL,
    field_name      TEXT,
    old_value       TEXT,
    new_value       TEXT,
    bom_id          TEXT,
    CONSTRAINT ck_action CHECK (action IN
        ('SAVE','PLACE','MOVE','RESIZE','DELETE','PROMOTE','UNDO'))
);

CREATE INDEX IF NOT EXISTS idx_changelog_building
    ON bim_changelog(building_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_changelog_entity
    ON bim_changelog(entity_type, entity_id);
