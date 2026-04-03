-- ════════════════════════════════════════════════════════
-- W019: MEP Anchor + Pattern tables
-- Formalises 00q-A DDL (previously misplaced in DV_RM_rules.sql)
-- Implementing DISC_VALIDATION_DB_SRS.md §6.12.3
-- ════════════════════════════════════════════════════════

-- A1: Discipline column on joint piece types
-- (Applied at extraction time in IFCtoERP.createStagingTable() — recorded here for audit)
-- ALTER TABLE _import_joint_piece_types ADD COLUMN discipline TEXT;

-- A2: Anchor table — source/terminal XYZ points extracted by IFCtoERP
CREATE TABLE IF NOT EXISTS ad_mep_anchor (
    anchor_id       TEXT PRIMARY KEY,
    source_building TEXT NOT NULL,
    anchor_type     TEXT NOT NULL CHECK(anchor_type IN ('METER','FIXTURE','VALVE','GENERIC')),
    x_m             REAL NOT NULL,
    y_m             REAL NOT NULL,
    z_m             REAL NOT NULL,
    storey          TEXT,
    ifc_guid        TEXT
);

-- A3: Pattern table — reusable topology sequences mined per (building_type, discipline)
-- Seed data lives in IFCtoERP.seedMepPatterns() via INSERT OR IGNORE
CREATE TABLE IF NOT EXISTS ad_mep_pattern (
    pattern_id      TEXT NOT NULL,
    discipline      TEXT NOT NULL,
    building_type   TEXT NOT NULL,
    sequence        INTEGER NOT NULL,
    from_node_type  TEXT NOT NULL,
    to_node_type    TEXT NOT NULL,
    direction_axis  TEXT NOT NULL,
    piece_type      TEXT NOT NULL,
    offset_rule     TEXT,
    gradient        REAL,
    notes           TEXT,
    source_building TEXT,
    PRIMARY KEY (pattern_id, sequence)
);

-- Index for RouteWalker pattern lookup
CREATE INDEX IF NOT EXISTS idx_mep_pattern_lookup
    ON ad_mep_pattern (building_type, discipline);

-- Index for anchor lookup by building + type
CREATE INDEX IF NOT EXISTS idx_mep_anchor_lookup
    ON ad_mep_anchor (source_building, anchor_type);
