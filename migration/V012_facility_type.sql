-- V011_facility_type.sql
-- Facility type column on AD_Val_Rule for infrastructure scoping
-- Values: BUILDING, BRIDGE, ROAD, RAIL, TUNNEL, DAM, PORT, null=any
-- APPEND-ONLY: never modify this migration after first run
-- Implementing CORE_SRS.md §5.1 — Witness: W-FACILITY-TYPE-SCHEMA

ALTER TABLE AD_Val_Rule ADD COLUMN facility_type TEXT;

UPDATE AD_Val_Rule SET facility_type = 'BRIDGE' WHERE provenance = 'Infra_Bridge';
UPDATE AD_Val_Rule SET facility_type = 'ROAD' WHERE provenance = 'Infra_Road';
UPDATE AD_Val_Rule SET facility_type = 'RAIL' WHERE provenance = 'Infra_Rail';
