-- DV010_ad_val_rule_schema.sql
-- Creates mined dimension rule tables in ERP.db
-- Source: 22 DV_*_rules.sql files (415 rules mined from 34 onboarded buildings)
-- Purpose: typical element dimensions per (ifc_class, storey, building) — reference data
-- NOT the same as validation.db AD_Val_Rule (compliance rules).
-- APPEND-ONLY: never modify this migration after first run
--
-- Implementing BBC.md §4.2.3 — Witness: W-DV-MINED-SCHEMA

-- Mined dimension rules: one row per (ifc_class, storey) observation from a building
CREATE TABLE IF NOT EXISTS ad_val_rule (
    ad_val_rule_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    rule_name       TEXT NOT NULL,                       -- 'IfcWall_Level_1'
    ifc_class       TEXT NOT NULL,                       -- 'IfcWall'
    check_method    TEXT NOT NULL DEFAULT 'DIMENSION_RANGE',  -- extensible
    severity        TEXT NOT NULL DEFAULT 'WARNING',     -- 'WARNING', 'BLOCK'
    is_active       INTEGER NOT NULL DEFAULT 1,
    description     TEXT,                                -- human: 'IfcWall on Level 1: 160 instances, avg ...'
    provenance      TEXT NOT NULL,                       -- building name: 'Esplanades', 'Clinic_Plumbing'
    created_at      TEXT DEFAULT (datetime('now'))
);

-- Rule parameters: typical_width_mm, typical_depth_mm, typical_height_mm per rule
CREATE TABLE IF NOT EXISTS ad_val_rule_param (
    ad_val_rule_param_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    ad_val_rule_id        INTEGER NOT NULL REFERENCES ad_val_rule(ad_val_rule_id),
    param_name            TEXT NOT NULL,                  -- 'typical_width_mm', 'typical_depth_mm', 'typical_height_mm'
    param_value           TEXT NOT NULL                   -- stored as text, parsed to numeric at load time
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_ad_val_rule_ifc_class ON ad_val_rule(ifc_class);
CREATE INDEX IF NOT EXISTS idx_ad_val_rule_provenance ON ad_val_rule(provenance);
CREATE INDEX IF NOT EXISTS idx_ad_val_rule_param_rule ON ad_val_rule_param(ad_val_rule_id);

-- Update schema version
INSERT OR REPLACE INTO AD_SysConfig (name, value, description, updated)
VALUES ('MINED_RULES_VERSION', 'DV010', 'Mined dimension rule tables', datetime('now'));
