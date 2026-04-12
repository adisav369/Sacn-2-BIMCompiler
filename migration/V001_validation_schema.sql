-- V001_validation_schema.sql
-- Creates validation.db schema: AD_Val_Rule + supporting tables
-- DocValidate.md §3.1 (canonical) + provenance from BIM_Designer enabling framework
-- APPEND-ONLY: never modify this migration after first run

-- Compliance rules (sprinkler spacing, room minimums, clearance, etc.)
CREATE TABLE IF NOT EXISTS AD_Val_Rule (
    ad_val_rule_id    INTEGER PRIMARY KEY,
    name              TEXT NOT NULL,           -- 'NFPA13_LH_SPACING'
    description       TEXT,
    rule_type         TEXT NOT NULL,           -- 'COMPLIANCE', 'CLASH', 'CLEARANCE'
    discipline        TEXT,                    -- 'FPR', 'ELC', 'PLB', NULL=any
    standard_ref      TEXT,                    -- 'NFPA 13 §8.6.2.2.1'
    jurisdiction      TEXT,                    -- 'US', 'SG', 'MY', 'INTL'
    provenance        TEXT,                    -- 'MINED:Terminal', 'RESEARCHED', 'UBBL'
    valid_from        TEXT,                    -- code edition date
    valid_to          TEXT,                    -- NULL = current
    is_active         INTEGER DEFAULT 1
);

-- Rule parameters (like C_Tax has rate, threshold, etc.)
CREATE TABLE IF NOT EXISTS AD_Val_Rule_Param (
    ad_val_rule_param_id  INTEGER PRIMARY KEY,
    ad_val_rule_id        INTEGER NOT NULL REFERENCES AD_Val_Rule,
    name                  TEXT NOT NULL,       -- 'max_spacing_mm', 'min_clearance_mm'
    value                 TEXT NOT NULL,       -- '4600', '150'
    value_type            TEXT DEFAULT 'NUM',  -- 'NUM', 'TEXT', 'BOOL'
    condition_expr        TEXT                 -- 'occupancy_class = LH' (optional)
);

-- Clash rules (which discipline pairs interact)
CREATE TABLE IF NOT EXISTS AD_Clash_Rule (
    ad_clash_rule_id  INTEGER PRIMARY KEY,
    discipline_a      TEXT NOT NULL,           -- 'ELC'
    discipline_b      TEXT NOT NULL,           -- 'PLB'
    element_filter_a  TEXT,                    -- 'ifc_class = IfcFlowSegment'
    element_filter_b  TEXT,                    -- 'fire_rating IS NOT NULL'
    clash_type        TEXT NOT NULL,           -- 'HARD', 'SOFT', 'MATERIAL', 'CLEARANCE'
    min_distance_mm   REAL,                    -- NULL for hard clash (=intersection)
    verdict           TEXT NOT NULL,           -- 'BLOCK', 'WARN', 'ALLOW_IF'
    resolution_note   TEXT,                    -- 'Add fire stop at penetration'
    ad_val_rule_id    INTEGER REFERENCES AD_Val_Rule  -- parent rule reference
);

-- Occupancy classification (drives which rules apply)
CREATE TABLE IF NOT EXISTS AD_Occupancy_Class (
    ad_occupancy_class_id  INTEGER PRIMARY KEY,
    code                   TEXT NOT NULL,       -- 'LH', 'OH1', 'OH2'
    name                   TEXT NOT NULL,       -- 'Light Hazard Occupancy'
    standard_ref           TEXT,                -- 'NFPA 13 §5.2'
    description            TEXT
);

-- Links rules to occupancy classes (many-to-many)
CREATE TABLE IF NOT EXISTS AD_Val_Rule_Occupancy (
    ad_val_rule_id         INTEGER NOT NULL REFERENCES AD_Val_Rule,
    ad_occupancy_class_id  INTEGER NOT NULL REFERENCES AD_Occupancy_Class,
    PRIMARY KEY (ad_val_rule_id, ad_occupancy_class_id)
);

-- Validation results (per instance, per rule — the audit trail)
CREATE TABLE IF NOT EXISTS AD_Validation_Result (
    ad_validation_result_id INTEGER PRIMARY KEY,
    c_orderline_id          INTEGER NOT NULL,
    ad_val_rule_id          INTEGER NOT NULL,
    rule_valid_from         TEXT,
    result                  TEXT NOT NULL,         -- 'PASS', 'WARN', 'BLOCK'
    actual_value            REAL,
    required_value          REAL,
    message                 TEXT,
    resolved_by_product_id  TEXT,
    created_at              TEXT DEFAULT (datetime('now'))
);

-- Documented deviations (engineer-approved exceptions)
CREATE TABLE IF NOT EXISTS AD_Val_Rule_Exception (
    ad_val_rule_exception_id INTEGER PRIMARY KEY,
    building_id              TEXT NOT NULL,
    ad_val_rule_id           INTEGER NOT NULL,
    element_ref              TEXT,
    count                    INTEGER,
    approved_by              TEXT,
    reason                   TEXT,
    created_at               TEXT DEFAULT (datetime('now'))
);

-- Mining provenance: records which buildings contributed to each rule
-- Separation layer: mining queries record their source here, not in the rule itself
CREATE TABLE IF NOT EXISTS AD_Val_Rule_Mining_Source (
    ad_val_rule_id     INTEGER NOT NULL REFERENCES AD_Val_Rule,
    building_type      TEXT NOT NULL,         -- 'Terminal', 'Duplex'
    element_count      INTEGER,               -- how many elements were sampled
    measured_min       REAL,                   -- observed minimum
    measured_max       REAL,                   -- observed maximum
    measured_p50       REAL,                   -- median
    measured_p95       REAL,                   -- 95th percentile
    measured_unit      TEXT DEFAULT 'mm',      -- unit of measurement
    query_desc         TEXT,                   -- human-readable mining query description
    mined_at           TEXT DEFAULT (datetime('now')),
    PRIMARY KEY (ad_val_rule_id, building_type)
);
