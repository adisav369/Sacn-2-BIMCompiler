-- DV028: iDempiere PK conformance Phase C — AD tables INTEGER PK
-- Adds surrogate _ID INTEGER PK AUTOINCREMENT to 13 AD tables in ERP.db.
-- Tier 1: TEXT PK → Value. Tier 2: composite PK → surrogate + UNIQUE. Tier 3: AD_SysConfig.
-- Safe: AD tables are read-only metadata. No FK cascade risk.

-- ============================================================
-- TIER 1 — Simple TEXT PK (8 tables)
-- Pattern: old TEXT PK column → Value TEXT NOT NULL UNIQUE
-- ============================================================

-- 1. ad_space_type (41 rows) — space_type_id → Value
CREATE TABLE ad_space_type_new (
    AD_Space_Type_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
    Value             TEXT NOT NULL UNIQUE,
    category          TEXT NOT NULL,
    omniclass_code    TEXT NOT NULL,
    wall_rule         TEXT NOT NULL,
    is_sleeping_room  INTEGER DEFAULT 0,
    is_open_plan      INTEGER DEFAULT 0,
    is_exterior       INTEGER DEFAULT 0,
    min_area          REAL DEFAULT 0,
    min_dimension     REAL DEFAULT 0,
    requires_window   INTEGER DEFAULT 0,
    requires_egress   INTEGER DEFAULT 0,
    structural_grid   INTEGER DEFAULT 0,
    beam_max_span     REAL DEFAULT 8.0,
    code_reference    TEXT,
    is_active         INTEGER DEFAULT 1
);
INSERT INTO ad_space_type_new (Value, category, omniclass_code, wall_rule,
    is_sleeping_room, is_open_plan, is_exterior, min_area, min_dimension,
    requires_window, requires_egress, structural_grid, beam_max_span,
    code_reference, is_active)
SELECT space_type_id, category, omniclass_code, wall_rule,
    is_sleeping_room, is_open_plan, is_exterior, min_area, min_dimension,
    requires_window, requires_egress, structural_grid, beam_max_span,
    code_reference, is_active
FROM ad_space_type;
DROP TABLE ad_space_type;
ALTER TABLE ad_space_type_new RENAME TO ad_space_type;
CREATE INDEX idx_ad_space_type_category ON ad_space_type(category);

-- 2. ad_element_mep (12 rows) — element_type → Value
CREATE TABLE ad_element_mep_new (
    AD_Element_MEP_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    Value             TEXT NOT NULL UNIQUE,
    ifc_class         TEXT NOT NULL,
    discipline        TEXT NOT NULL,
    mep_system        TEXT,
    host_type         TEXT,
    dist_role         TEXT,
    circuit_type      TEXT,
    mount_height      REAL,
    clearance         TEXT,
    ports             TEXT,
    properties        TEXT,
    code_ref          TEXT,
    is_active         INTEGER DEFAULT 1,
    width             REAL,
    depth             REAL,
    height            REAL,
    AD_Org_ID         INTEGER
);
INSERT INTO ad_element_mep_new (Value, ifc_class, discipline, mep_system,
    host_type, dist_role, circuit_type, mount_height, clearance, ports,
    properties, code_ref, is_active, width, depth, height, AD_Org_ID)
SELECT element_type, ifc_class, discipline, mep_system,
    host_type, dist_role, circuit_type, mount_height, clearance, ports,
    properties, code_ref, is_active, width, depth, height, AD_Org_ID
FROM ad_element_mep;
DROP TABLE ad_element_mep;
ALTER TABLE ad_element_mep_new RENAME TO ad_element_mep;

-- 3. ad_fp_coverage (4 rows) — hazard_class → Value
CREATE TABLE ad_fp_coverage_new (
    AD_FP_Coverage_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    Value             TEXT NOT NULL UNIQUE,
    max_coverage_m2   REAL NOT NULL,
    max_spacing_m     REAL NOT NULL,
    min_spacing_m     REAL NOT NULL,
    wall_distance_m   REAL,
    k_factor          REAL,
    code_ref          TEXT DEFAULT 'NFPA 13',
    is_active         INTEGER DEFAULT 1,
    pack_id           TEXT DEFAULT 'NFPA-13'
);
INSERT INTO ad_fp_coverage_new (Value, max_coverage_m2, max_spacing_m,
    min_spacing_m, wall_distance_m, k_factor, code_ref, is_active, pack_id)
SELECT hazard_class, max_coverage_m2, max_spacing_m,
    min_spacing_m, wall_distance_m, k_factor, code_ref, is_active, pack_id
FROM ad_fp_coverage;
DROP TABLE ad_fp_coverage;
ALTER TABLE ad_fp_coverage_new RENAME TO ad_fp_coverage;

-- 4. ad_fp_trigger (12 rows) — trigger_id → Value
CREATE TABLE ad_fp_trigger_new (
    AD_FP_Trigger_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
    Value             TEXT NOT NULL UNIQUE,
    trigger_type      TEXT NOT NULL,
    element_type      TEXT NOT NULL,
    min_storeys       INTEGER,
    min_height_m      REAL,
    min_floor_area_m2 REAL,
    total_area_m2     REAL,
    occupancy_group   TEXT,
    min_quantity      INTEGER DEFAULT 1,
    quantity_formula  TEXT,
    code_id           TEXT,
    clause            TEXT,
    description       TEXT,
    jurisdiction      TEXT DEFAULT 'INTERNATIONAL',
    is_mandatory      INTEGER DEFAULT 1,
    is_active         INTEGER DEFAULT 1,
    pack_id           TEXT DEFAULT 'BASE'
);
INSERT INTO ad_fp_trigger_new (Value, trigger_type, element_type,
    min_storeys, min_height_m, min_floor_area_m2, total_area_m2,
    occupancy_group, min_quantity, quantity_formula, code_id, clause,
    description, jurisdiction, is_mandatory, is_active, pack_id)
SELECT trigger_id, trigger_type, element_type,
    min_storeys, min_height_m, min_floor_area_m2, total_area_m2,
    occupancy_group, min_quantity, quantity_formula, code_id, clause,
    description, jurisdiction, is_mandatory, is_active, pack_id
FROM ad_fp_trigger;
DROP TABLE ad_fp_trigger;
ALTER TABLE ad_fp_trigger_new RENAME TO ad_fp_trigger;

-- 5. ad_ifc_class_map (47 rows) — ifc_class → Value
CREATE TABLE ad_ifc_class_map_new (
    AD_IFC_Class_Map_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    Value             TEXT NOT NULL UNIQUE,
    discipline        TEXT NOT NULL,
    category          TEXT NOT NULL,
    attachment_face   TEXT NOT NULL DEFAULT 'CENTER',
    ifc_schema        TEXT NOT NULL DEFAULT 'IFC4',
    domain            TEXT NOT NULL DEFAULT 'BUILDING',
    is_active         INTEGER NOT NULL DEFAULT 1,
    description       TEXT,
    AD_Org_ID         INTEGER
);
INSERT INTO ad_ifc_class_map_new (Value, discipline, category,
    attachment_face, ifc_schema, domain, is_active, description, AD_Org_ID)
SELECT ifc_class, discipline, category,
    attachment_face, ifc_schema, domain, is_active, description, AD_Org_ID
FROM ad_ifc_class_map;
DROP TABLE ad_ifc_class_map;
ALTER TABLE ad_ifc_class_map_new RENAME TO ad_ifc_class_map;

-- 6. ad_space_dim (37 rows) — space_type → Value
CREATE TABLE ad_space_dim_new (
    AD_Space_Dim_ID   INTEGER PRIMARY KEY AUTOINCREMENT,
    Value             TEXT NOT NULL UNIQUE,
    min_width         REAL,
    min_depth         REAL,
    min_area          REAL,
    min_height        REAL DEFAULT 2.4,
    preferred_ratio   REAL DEFAULT 1.5,
    max_ratio         REAL DEFAULT 2.5,
    required_products TEXT,
    optional_products TEXT,
    door_on_wall      TEXT DEFAULT 'ANY',
    window_on_wall    TEXT DEFAULT 'EXTERIOR',
    code_ref          TEXT,
    is_active         INTEGER DEFAULT 1,
    facade_type       TEXT DEFAULT 'OPAQUE',
    ventilation_opening TEXT
);
INSERT INTO ad_space_dim_new (Value, min_width, min_depth, min_area,
    min_height, preferred_ratio, max_ratio, required_products, optional_products,
    door_on_wall, window_on_wall, code_ref, is_active, facade_type, ventilation_opening)
SELECT space_type, min_width, min_depth, min_area,
    min_height, preferred_ratio, max_ratio, required_products, optional_products,
    door_on_wall, window_on_wall, code_ref, is_active, facade_type, ventilation_opening
FROM ad_space_dim;
DROP TABLE ad_space_dim;
ALTER TABLE ad_space_dim_new RENAME TO ad_space_dim;

-- 7. ad_space_exterior_rule (24 rows) — space_type_id → Value
CREATE TABLE ad_space_exterior_rule_new (
    AD_Space_Exterior_Rule_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    Value             TEXT NOT NULL UNIQUE,
    requires_exterior INTEGER DEFAULT 0,
    preferred_exterior TEXT,
    min_exterior_ratio REAL DEFAULT 0,
    facade_preference  TEXT,
    notes             TEXT,
    is_active         INTEGER DEFAULT 1
);
INSERT INTO ad_space_exterior_rule_new (Value, requires_exterior,
    preferred_exterior, min_exterior_ratio, facade_preference, notes, is_active)
SELECT space_type_id, requires_exterior,
    preferred_exterior, min_exterior_ratio, facade_preference, notes, is_active
FROM ad_space_exterior_rule;
DROP TABLE ad_space_exterior_rule;
ALTER TABLE ad_space_exterior_rule_new RENAME TO ad_space_exterior_rule;

-- 8. ad_space_type_mep (22 rows) — space_type_id → Value
CREATE TABLE ad_space_type_mep_new (
    AD_Space_Type_MEP_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    Value             TEXT NOT NULL UNIQUE,
    plumbing_fixtures TEXT,
    requires_stack    INTEGER DEFAULT 0,
    requires_vent     INTEGER DEFAULT 0,
    water_supply      TEXT DEFAULT 'none',
    light_points      INTEGER DEFAULT 0,
    power_points      INTEGER DEFAULT 0,
    switch_points     INTEGER DEFAULT 0,
    requires_dedicated INTEGER DEFAULT 0,
    circuit_type      TEXT DEFAULT 'general',
    requires_exhaust  INTEGER DEFAULT 0,
    exhaust_cfm       INTEGER DEFAULT 0,
    allows_aircon     INTEGER DEFAULT 0,
    ventilation_type  TEXT DEFAULT 'natural'
);
INSERT INTO ad_space_type_mep_new (Value, plumbing_fixtures,
    requires_stack, requires_vent, water_supply, light_points, power_points,
    switch_points, requires_dedicated, circuit_type, requires_exhaust,
    exhaust_cfm, allows_aircon, ventilation_type)
SELECT space_type_id, plumbing_fixtures,
    requires_stack, requires_vent, water_supply, light_points, power_points,
    switch_points, requires_dedicated, circuit_type, requires_exhaust,
    exhaust_cfm, allows_aircon, ventilation_type
FROM ad_space_type_mep;
DROP TABLE ad_space_type_mep;
ALTER TABLE ad_space_type_mep_new RENAME TO ad_space_type_mep;

-- ============================================================
-- TIER 2 — Composite PK (4 tables)
-- Pattern: add surrogate _ID, keep composite columns + UNIQUE constraint
-- ============================================================

-- 9. ad_code_requirement (23 rows) — composite (code_id, clause, element_type, space_type)
CREATE TABLE ad_code_requirement_new (
    AD_Code_Requirement_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    code_id           TEXT,
    clause            TEXT,
    requirement_id    TEXT,
    element_type      TEXT,
    space_type        TEXT DEFAULT 'ANY',
    qty_fixed         INTEGER,
    qty_per_area      REAL,
    qty_per_wall      INTEGER,
    max_spacing_m     REAL,
    min_clearance_m   REAL,
    coverage_area     REAL,
    coverage_radius   REAL,
    max_height_m      REAL,
    min_height_m      REAL,
    placement_rule    TEXT,
    description       TEXT,
    is_mandatory      INTEGER DEFAULT 1,
    pack_id           TEXT DEFAULT 'BASE',
    UNIQUE (code_id, clause, element_type, space_type)
);
INSERT INTO ad_code_requirement_new (code_id, clause, requirement_id,
    element_type, space_type, qty_fixed, qty_per_area, qty_per_wall,
    max_spacing_m, min_clearance_m, coverage_area, coverage_radius,
    max_height_m, min_height_m, placement_rule, description, is_mandatory, pack_id)
SELECT code_id, clause, requirement_id,
    element_type, space_type, qty_fixed, qty_per_area, qty_per_wall,
    max_spacing_m, min_clearance_m, coverage_area, coverage_radius,
    max_height_m, min_height_m, placement_rule, description, is_mandatory, pack_id
FROM ad_code_requirement;
DROP TABLE ad_code_requirement;
ALTER TABLE ad_code_requirement_new RENAME TO ad_code_requirement;

-- 10. ad_space_adjacency (22 rows) — composite (space_type_a, space_type_b)
CREATE TABLE ad_space_adjacency_new (
    AD_Space_Adjacency_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    space_type_a      TEXT NOT NULL,
    space_type_b      TEXT NOT NULL,
    relationship      TEXT NOT NULL,
    door_required     INTEGER DEFAULT 1,
    notes             TEXT,
    is_active         INTEGER DEFAULT 1,
    UNIQUE (space_type_a, space_type_b)
);
INSERT INTO ad_space_adjacency_new (space_type_a, space_type_b,
    relationship, door_required, notes, is_active)
SELECT space_type_a, space_type_b,
    relationship, door_required, notes, is_active
FROM ad_space_adjacency;
DROP TABLE ad_space_adjacency;
ALTER TABLE ad_space_adjacency_new RENAME TO ad_space_adjacency;

-- 11. ad_space_type_mep_bom (186 rows) — composite (space_type_id, mep_product_id)
CREATE TABLE ad_space_type_mep_bom_new (
    AD_Space_Type_MEP_BOM_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    space_type_id     TEXT NOT NULL,
    mep_product_id    TEXT NOT NULL,
    qty_min           INTEGER DEFAULT 0,
    qty_normal        INTEGER DEFAULT 1,
    qty_max           INTEGER DEFAULT 99,
    per_area_min      REAL DEFAULT 0,
    per_area_normal   REAL DEFAULT 0,
    per_area_max      REAL DEFAULT 0,
    placement_rule    TEXT DEFAULT 'AUTO',
    host_surface      TEXT DEFAULT 'WALL',
    building_code     TEXT,
    code_clause       TEXT,
    conduit_min       REAL DEFAULT 0,
    conduit_normal    REAL DEFAULT 0,
    conduit_max       REAL DEFAULT 0,
    pack_id           TEXT DEFAULT 'BASE',
    UNIQUE (space_type_id, mep_product_id)
);
INSERT INTO ad_space_type_mep_bom_new (space_type_id, mep_product_id,
    qty_min, qty_normal, qty_max, per_area_min, per_area_normal, per_area_max,
    placement_rule, host_surface, building_code, code_clause,
    conduit_min, conduit_normal, conduit_max, pack_id)
SELECT space_type_id, mep_product_id,
    qty_min, qty_normal, qty_max, per_area_min, per_area_normal, per_area_max,
    placement_rule, host_surface, building_code, code_clause,
    conduit_min, conduit_normal, conduit_max, pack_id
FROM ad_space_type_mep_bom;
DROP TABLE ad_space_type_mep_bom;
ALTER TABLE ad_space_type_mep_bom_new RENAME TO ad_space_type_mep_bom;

-- 12. ad_space_type_opening (103 rows) — composite (space_type_id, opening_role, family_id)
CREATE TABLE ad_space_type_opening_new (
    AD_Space_Type_Opening_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    space_type_id      TEXT NOT NULL,
    opening_role       TEXT NOT NULL,
    family_id          TEXT NOT NULL,
    qty_rule           TEXT DEFAULT 'FIXED',
    qty_base           INTEGER DEFAULT 1,
    override_width_mm  INTEGER,
    override_height_mm INTEGER,
    sill_height_mm     INTEGER DEFAULT 0,
    placement_wall     TEXT,
    placement_rule     TEXT DEFAULT 'CENTER',
    is_fire_rated      INTEGER DEFAULT 0,
    building_code      TEXT,
    code_clause        TEXT,
    priority           INTEGER DEFAULT 100,
    is_active          INTEGER DEFAULT 1,
    profile            TEXT DEFAULT NULL,
    UNIQUE (space_type_id, opening_role, family_id)
);
INSERT INTO ad_space_type_opening_new (space_type_id, opening_role, family_id,
    qty_rule, qty_base, override_width_mm, override_height_mm, sill_height_mm,
    placement_wall, placement_rule, is_fire_rated, building_code, code_clause,
    priority, is_active, profile)
SELECT space_type_id, opening_role, family_id,
    qty_rule, qty_base, override_width_mm, override_height_mm, sill_height_mm,
    placement_wall, placement_rule, is_fire_rated, building_code, code_clause,
    priority, is_active, profile
FROM ad_space_type_opening;
DROP TABLE ad_space_type_opening;
ALTER TABLE ad_space_type_opening_new RENAME TO ad_space_type_opening;

-- ============================================================
-- TIER 3 — AD_SysConfig (ERP.db only)
-- Pattern: Name → Value, add surrogate _ID
-- ============================================================

-- 13. AD_SysConfig (8 rows) — Name TEXT PK → AD_SysConfig_ID INTEGER PK
CREATE TABLE AD_SysConfig_new (
    AD_SysConfig_ID   INTEGER PRIMARY KEY AUTOINCREMENT,
    Value             TEXT NOT NULL UNIQUE,
    Name              TEXT NOT NULL,
    Description       TEXT,
    updated           TEXT NOT NULL DEFAULT (datetime('now'))
);
INSERT INTO AD_SysConfig_new (Value, Name, Description, updated)
SELECT Name, Name, Description, updated
FROM AD_SysConfig;
DROP TABLE AD_SysConfig;
ALTER TABLE AD_SysConfig_new RENAME TO AD_SysConfig;

-- ============================================================
-- Verification: row counts must match pre-migration
-- ad_space_type=41, ad_element_mep=12, ad_fp_coverage=4,
-- ad_fp_trigger=12, ad_ifc_class_map=47, ad_space_dim=37,
-- ad_space_exterior_rule=24, ad_space_type_mep=22,
-- ad_code_requirement=23, ad_space_adjacency=22,
-- ad_space_type_mep_bom=186, ad_space_type_opening=103,
-- AD_SysConfig=8
-- ============================================================
