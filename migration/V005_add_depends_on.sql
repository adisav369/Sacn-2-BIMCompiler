-- V005: Add depends_on column to AD_Val_Rule for inference engine dependency chain.
-- Implementing BIM_Designer_SRS.md §19 — Witness: W-INF-DEP-1
-- Append-only migration — never modify existing columns.

ALTER TABLE AD_Val_Rule ADD COLUMN depends_on INTEGER DEFAULT 0;
