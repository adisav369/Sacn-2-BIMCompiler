-- DV031: Phase D Groups 1+2 — TEXT PK → INTEGER + column renames
-- Group 1: bad_rule family (4 tables, 0 Java refs, FK chain)
-- Group 2: 6 tables with wrong PK column name (id/slot_id/etc → TableName_ID)
-- Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 — Witness: W-DV-PHASE-D2

-- ============================================================
-- GROUP 1: bad_rule family (TEXT PK → INTEGER PK + Value)
-- Order: category → rule → param (FK chain)
-- ============================================================

-- --- bad_rule_category (6 rows) ---
CREATE TABLE bad_rule_category_new (
    Bad_Rule_Category_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    Value           TEXT NOT NULL UNIQUE,
    Name            TEXT NOT NULL,
    Description     TEXT NOT NULL,
    SeqNo           INTEGER NOT NULL,
    IsActive        INTEGER DEFAULT 1
);
INSERT INTO bad_rule_category_new (Value, Name, Description, SeqNo, IsActive)
SELECT category_id, category_name, description, seq_no, is_active
FROM bad_rule_category;
DROP TABLE bad_rule_category;
ALTER TABLE bad_rule_category_new RENAME TO bad_rule_category;

-- --- bad_rule (53 rows, FK to category, self-ref override) ---
CREATE TABLE bad_rule_new (
    Bad_Rule_ID     INTEGER PRIMARY KEY AUTOINCREMENT,
    Value           TEXT NOT NULL UNIQUE,
    Name            TEXT NOT NULL,
    Bad_Rule_Category_ID INTEGER NOT NULL REFERENCES bad_rule_category(Bad_Rule_Category_ID),
    rule_type       TEXT NOT NULL,
    applies_to_domain TEXT NOT NULL,
    applies_to_type TEXT NOT NULL,
    property        TEXT NOT NULL,
    operator        TEXT NOT NULL,
    rule_value      TEXT NOT NULL,
    value_unit      TEXT,
    condition_expr  TEXT,
    authority_id    TEXT,
    clause_ref      TEXT,
    authority_name  TEXT,
    severity        TEXT DEFAULT 'ERROR',
    IsActive        INTEGER DEFAULT 1,
    SeqNo           INTEGER DEFAULT 10,
    Override_Bad_Rule_ID INTEGER REFERENCES bad_rule_new(Bad_Rule_ID),
    provenance      TEXT NOT NULL
);
-- Step 1: insert without self-reference
INSERT INTO bad_rule_new (
    Value, Name, Bad_Rule_Category_ID, rule_type,
    applies_to_domain, applies_to_type, property, operator,
    rule_value, value_unit, condition_expr, authority_id,
    clause_ref, authority_name, severity, IsActive, SeqNo,
    provenance
)
SELECT
    r.rule_id, r.rule_name,
    (SELECT Bad_Rule_Category_ID FROM bad_rule_category WHERE Value = r.category_id),
    r.rule_type, r.applies_to_domain, r.applies_to_type,
    r.property, r.operator, r.value, r.value_unit,
    r.condition_expr, r.authority_id, r.clause_ref, r.authority_name,
    r.severity, r.is_active, r.seq_no, r.provenance
FROM bad_rule r;
-- Step 2: fix self-references (5 rows)
UPDATE bad_rule_new SET Override_Bad_Rule_ID = (
    SELECT n2.Bad_Rule_ID FROM bad_rule_new n2
    WHERE n2.Value = (
        SELECT old.override_rule_id FROM bad_rule old
        WHERE old.rule_id = bad_rule_new.Value
    )
)
WHERE Value IN (SELECT rule_id FROM bad_rule WHERE override_rule_id IS NOT NULL);
DROP TABLE bad_rule;
ALTER TABLE bad_rule_new RENAME TO bad_rule;

-- --- bad_rule_param (1 row, FK to bad_rule) ---
CREATE TABLE bad_rule_param_new (
    Bad_Rule_Param_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    Bad_Rule_ID     INTEGER NOT NULL REFERENCES bad_rule(Bad_Rule_ID),
    Value           TEXT NOT NULL,
    Name            TEXT NOT NULL,
    param_value     TEXT NOT NULL,
    param_unit      TEXT,
    provenance      TEXT NOT NULL,
    UNIQUE(Bad_Rule_ID, Value)
);
INSERT INTO bad_rule_param_new (Bad_Rule_ID, Value, Name, param_value, param_unit, provenance)
SELECT
    (SELECT Bad_Rule_ID FROM bad_rule WHERE Value = p.rule_id),
    p.param_key, p.param_key, p.param_value, p.param_unit, p.provenance
FROM bad_rule_param p;
DROP TABLE bad_rule_param;
ALTER TABLE bad_rule_param_new RENAME TO bad_rule_param;

-- --- bad_discipline_priority (7 rows, no FKs) ---
CREATE TABLE bad_discipline_priority_new (
    Bad_Discipline_Priority_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    Value           TEXT NOT NULL UNIQUE,
    Name            TEXT NOT NULL,
    higher_discipline TEXT NOT NULL,
    lower_discipline  TEXT NOT NULL,
    resolution      TEXT NOT NULL,
    notes           TEXT,
    provenance      TEXT NOT NULL,
    UNIQUE(higher_discipline, lower_discipline)
);
INSERT INTO bad_discipline_priority_new (
    Value, Name, higher_discipline, lower_discipline,
    resolution, notes, provenance
)
SELECT
    higher_discipline || '>' || lower_discipline,
    higher_discipline || ' overrides ' || lower_discipline,
    higher_discipline, lower_discipline,
    resolution, notes, provenance
FROM bad_discipline_priority;
DROP TABLE bad_discipline_priority;
ALTER TABLE bad_discipline_priority_new RENAME TO bad_discipline_priority;

-- ============================================================
-- GROUP 2: PK column renames (INTEGER PK, wrong name)
-- ============================================================

-- --- ad_wall_face (204 rows): id → AD_Wall_Face_ID ---
CREATE TABLE ad_wall_face_new (
    AD_Wall_Face_ID   INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type     TEXT NOT NULL,
    storey            TEXT NOT NULL,
    room_name         TEXT NOT NULL,
    face              TEXT NOT NULL,
    wall_type_id      TEXT NOT NULL,
    is_exterior       INTEGER NOT NULL,
    adjacent_room     TEXT,
    IsActive          INTEGER DEFAULT 1,
    building_id       INTEGER,
    Name              TEXT,
    Value             TEXT,
    UNIQUE(building_type, storey, room_name, face)
);
INSERT INTO ad_wall_face_new (
    AD_Wall_Face_ID, building_type, storey, room_name, face,
    wall_type_id, is_exterior, adjacent_room, IsActive,
    building_id, Name, Value
)
SELECT id, building_type, storey, room_name, face,
    wall_type_id, is_exterior, adjacent_room, is_active,
    building_id, Name, Value
FROM ad_wall_face;
DROP TABLE ad_wall_face;
ALTER TABLE ad_wall_face_new RENAME TO ad_wall_face;

-- --- placement_rules (4801 rows): id → AD_Placement_Rule_ID ---
CREATE TABLE placement_rules_new (
    AD_Placement_Rule_ID INTEGER PRIMARY KEY,
    component_id      INTEGER,
    host_type         TEXT,
    offset_from_host  REAL,
    grid_spacing      REAL,
    clearance_radius  REAL,
    Name              TEXT,
    Value             TEXT
);
INSERT INTO placement_rules_new (
    AD_Placement_Rule_ID, component_id, host_type,
    offset_from_host, grid_spacing, clearance_radius, Name, Value
)
SELECT id, component_id, host_type,
    offset_from_host, grid_spacing, clearance_radius, Name, Value
FROM placement_rules;
DROP TABLE placement_rules;
ALTER TABLE placement_rules_new RENAME TO placement_rules;

-- --- W_Calibration_Result (0 rows): id → W_Calibration_Result_ID ---
CREATE TABLE W_Calibration_Result_new (
    W_Calibration_Result_ID INTEGER PRIMARY KEY AUTOINCREMENT,
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
    run_date          TEXT NOT NULL DEFAULT (datetime('now')),
    Name              TEXT,
    Value             TEXT
);
DROP TABLE W_Calibration_Result;
ALTER TABLE W_Calibration_Result_new RENAME TO W_Calibration_Result;

-- --- ad_assembly_connector (10 rows): connector_id → AD_Assembly_Connector_ID ---
CREATE TABLE ad_assembly_connector_new (
    AD_Assembly_Connector_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    assembly_id       TEXT NOT NULL,
    version           TEXT NOT NULL DEFAULT '1.0.0',
    face              TEXT NOT NULL,
    connector_type    TEXT NOT NULL,
    position_x        REAL DEFAULT 0,
    position_y        REAL DEFAULT 0,
    position_z        REAL DEFAULT 0,
    diameter_mm       REAL,
    connects_to       TEXT,
    Name              TEXT,
    Value             TEXT,
    UNIQUE(assembly_id, version, face, connector_type)
);
INSERT INTO ad_assembly_connector_new (
    AD_Assembly_Connector_ID, assembly_id, version, face,
    connector_type, position_x, position_y, position_z,
    diameter_mm, connects_to, Name, Value
)
SELECT connector_id, assembly_id, version, face,
    connector_type, position_x, position_y, position_z,
    diameter_mm, connects_to, Name, Value
FROM ad_assembly_connector;
DROP TABLE ad_assembly_connector;
ALTER TABLE ad_assembly_connector_new RENAME TO ad_assembly_connector;

-- --- ad_assembly_manifest (37 rows): manifest_id → AD_Assembly_Manifest_ID ---
CREATE TABLE ad_assembly_manifest_new (
    AD_Assembly_Manifest_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    assembly_id       TEXT NOT NULL,
    version           TEXT NOT NULL DEFAULT '1.0.0',
    face              TEXT NOT NULL,
    interface_type    TEXT NOT NULL,
    clearance_m       REAL DEFAULT 0,
    Name              TEXT,
    Value             TEXT,
    UNIQUE(assembly_id, version, face, interface_type)
);
INSERT INTO ad_assembly_manifest_new (
    AD_Assembly_Manifest_ID, assembly_id, version, face,
    interface_type, clearance_m, Name, Value
)
SELECT manifest_id, assembly_id, version, face,
    interface_type, clearance_m, Name, Value
FROM ad_assembly_manifest;
DROP TABLE ad_assembly_manifest;
ALTER TABLE ad_assembly_manifest_new RENAME TO ad_assembly_manifest;

-- --- ad_room_slot (38 rows): slot_id → AD_Room_Slot_ID ---
CREATE TABLE ad_room_slot_new (
    AD_Room_Slot_ID   INTEGER PRIMARY KEY AUTOINCREMENT,
    room_type         TEXT NOT NULL,
    slot_name         TEXT NOT NULL,
    assembly_id       TEXT,
    version_range     TEXT DEFAULT '[1.0,2.0)',
    slot_face         TEXT,
    slot_priority     INTEGER DEFAULT 100,
    is_required       INTEGER DEFAULT 0,
    profile           TEXT DEFAULT NULL,
    min_area          REAL DEFAULT 0.0,
    Name              TEXT,
    Value             TEXT,
    UNIQUE(room_type, slot_name, profile)
);
INSERT INTO ad_room_slot_new (
    AD_Room_Slot_ID, room_type, slot_name, assembly_id,
    version_range, slot_face, slot_priority, is_required,
    profile, min_area, Name, Value
)
SELECT slot_id, room_type, slot_name, assembly_id,
    version_range, slot_face, slot_priority, is_required,
    profile, min_area, Name, Value
FROM ad_room_slot;
DROP TABLE ad_room_slot;
ALTER TABLE ad_room_slot_new RENAME TO ad_room_slot;
