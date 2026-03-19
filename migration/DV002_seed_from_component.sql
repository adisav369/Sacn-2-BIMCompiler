-- DV002: Seed disc_validation.db from component_library.db
-- Run against disc_validation.db AFTER DV001 schema is created.
-- Requires: ATTACH DATABASE 'library/component_library.db' AS comp;
--
-- Implementing DISC_VALIDATION_DB_SRS.md §6 Phase 1 — Witness: W-DV-DB-SEED
--
-- Expected row counts (from component_library.db 2026-03-19):
--   ad_space_type=41, ad_element_mep=12, ad_space_type_mep_bom=186,
--   ad_fp_coverage=4, ad_assembly_connector=10, ad_assembly_manifest=37,
--   ad_wall_face=204, placement_rules=4801, ad_space_adjacency=22,
--   ad_fp_trigger=12, ad_code_requirement=23, ad_room_slot=38,
--   ad_space_dim=37, ad_space_exterior_rule=24, ad_space_type_opening=103,
--   ad_space_type_furniture=37, ad_space_type_mep=22

ATTACH DATABASE 'library/component_library.db' AS comp;

INSERT OR IGNORE INTO ad_space_type
    SELECT * FROM comp.ad_space_type;

INSERT OR IGNORE INTO ad_element_mep
    SELECT * FROM comp.ad_element_mep;

INSERT OR IGNORE INTO ad_space_type_mep_bom
    SELECT * FROM comp.ad_space_type_mep_bom;

INSERT OR IGNORE INTO ad_fp_coverage
    SELECT * FROM comp.ad_fp_coverage;

INSERT OR IGNORE INTO ad_assembly_connector
    SELECT * FROM comp.ad_assembly_connector;

INSERT OR IGNORE INTO ad_assembly_manifest
    SELECT * FROM comp.ad_assembly_manifest;

INSERT OR IGNORE INTO ad_wall_face
    SELECT * FROM comp.ad_wall_face;

INSERT OR IGNORE INTO placement_rules
    SELECT * FROM comp.placement_rules;

INSERT OR IGNORE INTO ad_space_adjacency
    SELECT * FROM comp.ad_space_adjacency;

INSERT OR IGNORE INTO ad_fp_trigger
    SELECT * FROM comp.ad_fp_trigger;

INSERT OR IGNORE INTO ad_code_requirement
    SELECT * FROM comp.ad_code_requirement;

INSERT OR IGNORE INTO ad_room_slot
    SELECT * FROM comp.ad_room_slot;

INSERT OR IGNORE INTO ad_space_dim
    SELECT * FROM comp.ad_space_dim;

INSERT OR IGNORE INTO ad_space_exterior_rule
    SELECT * FROM comp.ad_space_exterior_rule;

INSERT OR IGNORE INTO ad_space_type_opening
    SELECT * FROM comp.ad_space_type_opening;

INSERT OR IGNORE INTO ad_space_type_furniture
    SELECT * FROM comp.ad_space_type_furniture;

INSERT OR IGNORE INTO ad_space_type_mep
    SELECT * FROM comp.ad_space_type_mep;

DETACH DATABASE comp;

-- Mark seed complete
INSERT OR REPLACE INTO AD_SysConfig (Name, Value, Description)
VALUES ('SEED_VERSION', 'DV002', 'Seed data copied from component_library.db');
