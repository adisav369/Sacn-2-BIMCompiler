-- DV019: Move bad_* tables from component_library.db to disc_validation.db
-- Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 4
-- No Java readers exist for these tables (confirmed S69)

-- ============================================================
-- bad_rule_category (6 rows) — must exist before bad_rule FK
-- ============================================================
CREATE TABLE IF NOT EXISTS bad_rule_category (
    category_id     TEXT PRIMARY KEY,
    category_name   TEXT NOT NULL,
    description     TEXT NOT NULL,
    seq_no          INTEGER NOT NULL,
    is_active       INTEGER DEFAULT 1
);

-- ============================================================
-- bad_rule (53 rows)
-- ============================================================
CREATE TABLE IF NOT EXISTS bad_rule (
    rule_id         TEXT PRIMARY KEY,
    category_id     TEXT NOT NULL REFERENCES bad_rule_category,
    rule_name       TEXT NOT NULL,
    rule_type       TEXT NOT NULL,
    applies_to_domain TEXT NOT NULL,
    applies_to_type   TEXT NOT NULL,
    property        TEXT NOT NULL,
    operator        TEXT NOT NULL,
    value           TEXT NOT NULL,
    value_unit      TEXT,
    condition_expr  TEXT,
    authority_id    TEXT,
    clause_ref      TEXT,
    authority_name  TEXT,
    severity        TEXT DEFAULT 'ERROR',
    is_active       INTEGER DEFAULT 1,
    seq_no          INTEGER DEFAULT 10,
    override_rule_id TEXT REFERENCES bad_rule,
    provenance      TEXT NOT NULL
);

-- ============================================================
-- bad_rule_param (1 row)
-- ============================================================
CREATE TABLE IF NOT EXISTS bad_rule_param (
    rule_id         TEXT NOT NULL REFERENCES bad_rule,
    param_key       TEXT NOT NULL,
    param_value     TEXT NOT NULL,
    param_unit      TEXT,
    provenance      TEXT NOT NULL,
    PRIMARY KEY (rule_id, param_key)
);

-- ============================================================
-- bad_discipline_priority (7 rows)
-- ============================================================
CREATE TABLE IF NOT EXISTS bad_discipline_priority (
    higher_discipline TEXT NOT NULL,
    lower_discipline  TEXT NOT NULL,
    resolution       TEXT NOT NULL,
    notes           TEXT,
    provenance      TEXT NOT NULL,
    PRIMARY KEY (higher_discipline, lower_discipline)
);

-- ============================================================
-- Copy data from component_library.db via ATTACH
-- ============================================================
ATTACH DATABASE 'library/component_library.db' AS complib;

INSERT OR IGNORE INTO bad_rule_category
  SELECT * FROM complib.bad_rule_category;

INSERT OR IGNORE INTO bad_rule
  SELECT * FROM complib.bad_rule;

INSERT OR IGNORE INTO bad_rule_param
  SELECT * FROM complib.bad_rule_param;

INSERT OR IGNORE INTO bad_discipline_priority
  SELECT * FROM complib.bad_discipline_priority;

DETACH DATABASE complib;
