-- DV001: Discipline Validation database schema
-- Separates discipline metadata from product LOD catalog (component_library.db)
-- References component_library.db products by name (no FK, no LOD copies)
-- APPEND-ONLY: never modify this migration after first run
--
-- Implementing DISC_VALIDATION_DB_SRS.md §4 — Witness: W-DV-DB-SCHEMA

-- ── Space types ──────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_space_type (
    space_type_id     TEXT PRIMARY KEY,
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

CREATE INDEX IF NOT EXISTS idx_ad_space_type_category
    ON ad_space_type(category);

-- ── MEP element type definitions ─────────────────────────────────────
-- Reference pointer: element_type → M_Product.name in component_library.db

CREATE TABLE IF NOT EXISTS ad_element_mep (
    element_type      TEXT PRIMARY KEY,
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
    height            REAL
);

-- ── Discipline schedule per space type ───────────────────────────────
-- Reference pointer: mep_product_id → ad_element_mep.element_type

CREATE TABLE IF NOT EXISTS ad_space_type_mep_bom (
    space_type_id     TEXT,
    mep_product_id    TEXT,
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
    PRIMARY KEY (space_type_id, mep_product_id)
);

-- ── FP coverage by hazard class ──────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_fp_coverage (
    hazard_class      TEXT PRIMARY KEY,
    max_coverage_m2   REAL NOT NULL,
    max_spacing_m     REAL NOT NULL,
    min_spacing_m     REAL NOT NULL,
    wall_distance_m   REAL,
    k_factor          REAL,
    code_ref          TEXT DEFAULT 'NFPA 13',
    is_active         INTEGER DEFAULT 1
);

-- ── Assembly connectors ──────────────────────────────────────────────
-- Reference pointer: assembly_id → M_Product.name in component_library.db

CREATE TABLE IF NOT EXISTS ad_assembly_connector (
    connector_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    assembly_id       TEXT NOT NULL,
    version           TEXT NOT NULL DEFAULT '1.0.0',
    face              TEXT NOT NULL,
    connector_type    TEXT NOT NULL,
    position_x        REAL DEFAULT 0,
    position_y        REAL DEFAULT 0,
    position_z        REAL DEFAULT 0,
    diameter_mm       REAL,
    connects_to       TEXT,
    UNIQUE(assembly_id, version, face, connector_type)
);

-- ── Assembly manifests ───────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_assembly_manifest (
    manifest_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    assembly_id       TEXT NOT NULL,
    version           TEXT NOT NULL DEFAULT '1.0.0',
    face              TEXT NOT NULL,
    interface_type    TEXT NOT NULL,
    clearance_m       REAL DEFAULT 0,
    UNIQUE(assembly_id, version, face, interface_type)
);

-- ── Wall faces ───────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_wall_face (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type     TEXT NOT NULL,
    storey            TEXT NOT NULL,
    room_name         TEXT NOT NULL,
    face              TEXT NOT NULL,
    wall_type_id      TEXT NOT NULL,
    is_exterior       INTEGER NOT NULL,
    adjacent_room     TEXT,
    is_active         INTEGER DEFAULT 1,
    building_id       INTEGER,
    UNIQUE(building_type, storey, room_name, face)
);

-- ── Placement rules ──────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS placement_rules (
    id                INTEGER PRIMARY KEY,
    component_id      INTEGER,
    host_type         TEXT,
    offset_from_host  REAL,
    grid_spacing      REAL,
    clearance_radius  REAL
);

-- ── Space adjacency ──────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_space_adjacency (
    space_type_a      TEXT NOT NULL,
    space_type_b      TEXT NOT NULL,
    relationship      TEXT NOT NULL,
    door_required     INTEGER DEFAULT 1,
    notes             TEXT,
    is_active         INTEGER DEFAULT 1,
    PRIMARY KEY (space_type_a, space_type_b)
);

-- ── FP triggers ──────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_fp_trigger (
    trigger_id        TEXT PRIMARY KEY,
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
    is_active         INTEGER DEFAULT 1
);

-- ── Code requirements ────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_code_requirement (
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
    PRIMARY KEY (code_id, clause, element_type, space_type)
);

-- ── Room slots ───────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_room_slot (
    slot_id           INTEGER PRIMARY KEY AUTOINCREMENT,
    room_type         TEXT NOT NULL,
    slot_name         TEXT NOT NULL,
    assembly_id       TEXT,
    version_range     TEXT DEFAULT '[1.0,2.0)',
    slot_face         TEXT,
    slot_priority     INTEGER DEFAULT 100,
    is_required       INTEGER DEFAULT 0,
    profile           TEXT DEFAULT NULL,
    min_area          REAL DEFAULT 0.0,
    UNIQUE(room_type, slot_name, profile)
);

-- ── Space dimensions ─────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_space_dim (
    space_type        TEXT PRIMARY KEY,
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

-- ── Space exterior rules ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_space_exterior_rule (
    space_type_id     TEXT NOT NULL PRIMARY KEY,
    requires_exterior INTEGER DEFAULT 0,
    preferred_exterior TEXT,
    min_exterior_ratio REAL DEFAULT 0,
    facade_preference  TEXT,
    notes             TEXT,
    is_active         INTEGER DEFAULT 1
);

-- ── Space type openings ──────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_space_type_opening (
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
    PRIMARY KEY (space_type_id, opening_role, family_id)
);

-- ── Space type furniture ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_space_type_furniture (
    space_type_id     TEXT NOT NULL PRIMARY KEY,
    furniture_bom_id  TEXT,
    fallback_rule     TEXT DEFAULT 'NONE',
    is_active         INTEGER DEFAULT 1
);

-- ── Space type MEP services ──────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_space_type_mep (
    space_type_id     TEXT PRIMARY KEY,
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

-- ── Calibration results (CalibrationTest output) ─────────────────────

CREATE TABLE IF NOT EXISTS W_Calibration_Result (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    discipline        TEXT NOT NULL,
    storey            TEXT NOT NULL,
    te_count          INTEGER,
    te_floor_area_m2  REAL,
    te_density        REAL,
    docevent_qty      INTEGER,
    docevent_density  REAL,
    density_ratio     REAL,
    te_nn_median_mm   REAL,
    docevent_pitch_mm REAL,
    spacing_delta_mm  REAL,
    verdict           TEXT CHECK(verdict IN ('CALIBRATED','DRIFT','UNCALIBRATED','NO_SEED_DATA')),
    run_date          TEXT NOT NULL DEFAULT (datetime('now'))
);

-- ── Schema version ───────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS AD_SysConfig (
    Name              TEXT PRIMARY KEY,
    Value             TEXT NOT NULL,
    Description       TEXT,
    updated           TEXT NOT NULL DEFAULT (datetime('now'))
);

INSERT OR IGNORE INTO AD_SysConfig (Name, Value, Description)
VALUES ('SCHEMA_VERSION', 'DV001', 'ERP.db schema version');
