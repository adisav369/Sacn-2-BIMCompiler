-- AD Events, Callouts, Validators & Spatial Pattern Rules — Schema
-- Implements docs/AD_Events_Spatial_Rules.docx v1.1
-- Reactive layer: rules that fire on data change, validators that gate compilation,
-- callouts that cascade spatial consequences, pattern DNA library.

-- ============================================================================
-- ad_spatial_rule — topology-level cross-assembly constraints (§2.3)
-- Complement to ad_placement_rule (element-within-host).
-- ad_spatial_rule handles: "does this assembly align correctly with other
-- assemblies across the building?" — phantom, alignment, clearance, topology.
-- ============================================================================
CREATE TABLE IF NOT EXISTS ad_spatial_rule (
    rule_id          TEXT PRIMARY KEY,
    rule_type        TEXT NOT NULL CHECK (rule_type IN ('PHANTOM','ALIGNMENT','CLEARANCE','TOPOLOGY')),
    trigger_class    TEXT NOT NULL,   -- IFC class that activates this rule
    applies_to       TEXT NOT NULL CHECK (applies_to IN ('RESIDENTIAL','INSTITUTIONAL','ALL')),
    extracted_from   TEXT,            -- 'TBLKTN','DUPLEX','TERMINAL','MULTI'
    constraint_expr  TEXT NOT NULL,   -- vocabulary term from §2.4 — no freeform SQL
    violation_action TEXT NOT NULL DEFAULT 'FAIL'
                         CHECK (violation_action IN ('FAIL','WARN','ADVISORY')),
    bom_level        INTEGER CHECK (bom_level BETWEEN -1 AND 4),
    provenance       TEXT NOT NULL    -- research citation or code reference
);

-- ============================================================================
-- ad_callout_rule — post-change cascades (§3.2)
-- Formalises implicit callouts currently in FurnitureBOMResolver,
-- StoreyCompiler, RelationalResolver as explicit, testable, traceable rows.
-- WATCHDOG: cascade_action triggers re-expansion, NEVER direct world coord write.
-- ============================================================================
CREATE TABLE IF NOT EXISTS ad_callout_rule (
    callout_id       TEXT PRIMARY KEY,
    trigger_table    TEXT NOT NULL,   -- 'ad_element_rule','ad_room_boundary'
    trigger_column   TEXT NOT NULL,   -- column whose change fires this callout
    cascade_action   TEXT NOT NULL,   -- vocabulary: REFIT, INVALIDATE, REEXPAND, PROPAGATE
    cascade_target   TEXT NOT NULL,   -- 'ad_element_rule' or specific family_ref
    manifest_face    TEXT,            -- WALL_BACK,ENTRY,WASTE_OUT — MANIFEST face trigger
    condition_expr   TEXT,            -- NULL=always; 'delta > 100mm','discipline=FURN'
    bom_level        INTEGER CHECK (bom_level BETWEEN -1 AND 4),
    sequence         INTEGER NOT NULL DEFAULT 10,
    provenance       TEXT NOT NULL
);

-- ============================================================================
-- ad_spatial_pattern — topology DNA pattern library (§7.2)
-- Named groups of ad_spatial_rule + ad_callout_rule rows that co-activate.
-- New typologies declare which patterns they inherit — pure SQL, no Java.
-- ============================================================================
CREATE TABLE IF NOT EXISTS ad_spatial_pattern (
    pattern_id       TEXT PRIMARY KEY,   -- 'WET_CORE_CLUSTER','ROOM_MESH','SHARED_WALL'
    pattern_type     TEXT NOT NULL CHECK (pattern_type IN ('TOPOLOGY','MANIFEST','MEP','CIRCULATION')),
    extracted_from   TEXT NOT NULL,       -- Rosetta Stone source(s)
    applies_to       TEXT NOT NULL CHECK (applies_to IN ('RESIDENTIAL','INSTITUTIONAL','ALL')),
    rule_ids         TEXT,                -- CSV of ad_spatial_rule.rule_id
    callout_ids      TEXT,                -- CSV of ad_callout_rule.callout_id
    manifest_faces   TEXT,                -- CSV of MANIFEST face types this pattern anchors to
    provenance       TEXT NOT NULL
);

-- ============================================================================
-- ad_typology_pattern — DNA recombination join table (§7.2)
-- Links a building typology to the patterns it inherits.
-- compiler loads all rule_ids and callout_ids from inherited patterns automatically.
-- ============================================================================
CREATE TABLE IF NOT EXISTS ad_typology_pattern (
    typology_id  TEXT    NOT NULL,
    pattern_id   TEXT    NOT NULL REFERENCES ad_spatial_pattern(pattern_id),
    sequence     INTEGER NOT NULL DEFAULT 10,
    PRIMARY KEY (typology_id, pattern_id)
);
