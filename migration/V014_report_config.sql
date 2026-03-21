-- V012_report_config.sql
-- Report configuration table for Schedule/Cost/Carbon/Asset/KPI/Compliance reports
-- APPEND-ONLY: never modify this migration after first run
-- Implementing CORE_SRS.md §5.1 — Witness: W-REPORT-CONFIG-SCHEMA

CREATE TABLE IF NOT EXISTS AD_Report_Config (
    report_id TEXT PRIMARY KEY,
    report_name TEXT NOT NULL,
    report_type TEXT NOT NULL,  -- SCHEDULE, COST, CARBON, ASSET, KPI, COMPLIANCE
    template_path TEXT,
    jurisdiction TEXT,
    is_active INTEGER DEFAULT 1
);
