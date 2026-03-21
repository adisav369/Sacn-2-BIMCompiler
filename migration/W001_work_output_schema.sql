-- W001_work_output_schema.sql
-- Creates work_output.db schema — the BIM Designer's design workspace
-- BIM_Designer.md §17.10 (canonical) + ConstructionAsERP.md §2-3 (iDempiere parallel)
-- APPEND-ONLY: never modify this migration after first run
--
-- DESIGN RATIONALE (§17.10):
--   work_output.db is self-contained — YAML, Order, ASI embedded during init.
--   Backend tools (reports, SQL queries, admin dashboards) can read the design
--   without needing Blender.  Three actions: Save (cheap), Recall (browse),
--   Promote (governance gate → writes to {PREFIX}_BOM.db).
--
-- DATABASE RELATIONSHIPS:
--   component_library.db  → READ-ONLY (geometry catalog)
--   {PREFIX}_BOM.db       → READ-ONLY (BOM templates, product catalog)
--   work_output.db        → READ-WRITE (this schema — designer workspace)
--   validation.db         → READ-ONLY (AD_Val_Rule for compliance checks)
--   output.db             → WRITE-ONLY (compiled result, fresh each compile)
--
-- TABLE PREFIX CONVENTION (ConstructionAsERP.md §1.3):
--   c_*   = Construction order (C_Order, C_OrderLine)
--   m_*   = Master data attributes (M_AttributeSetInstance, M_AttributeInstance)
--   co_*  = Construction output spatial (CO_EmptySpaceLine)
--   pp_*  = Production operations (PP_Order_Node)
--   w_*   = Work-output-specific (W_BuildingConfig, W_Variant — no iDempiere parallel)

-- ============================================================================
-- 1. BUILDING IDENTITY — self-contained YAML + metadata
-- ============================================================================

-- Embedded building definition — the design is self-contained.
-- ConstructionModelSpawner writes this at init; never touched during design.
CREATE TABLE IF NOT EXISTS W_BuildingConfig (
    w_buildingconfig_id   INTEGER PRIMARY KEY,
    building_id           TEXT NOT NULL UNIQUE,   -- 'DemoHouse_2BR', 'MyProject_1'
    yaml_content          TEXT NOT NULL,          -- full classify_*.yaml text
    doc_base_type         TEXT NOT NULL,          -- 'RE', 'CO', 'IN'
    doc_sub_type          TEXT NOT NULL,          -- 'DM', 'SH', 'DX', etc.
    jurisdiction          TEXT NOT NULL DEFAULT 'MY',  -- rule set for validation
    aabb_width_mm         REAL NOT NULL,          -- site envelope X
    aabb_depth_mm         REAL NOT NULL,          -- site envelope Y
    aabb_height_mm        REAL NOT NULL,          -- site envelope Z
    provenance            TEXT NOT NULL DEFAULT 'GENERATIVE',  -- GENERATIVE | EXTRACTED
    created               TEXT NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================================
-- 2. C_ORDER — Construction Order header (iDempiere C_Order parallel)
-- ============================================================================

-- Master-detail model:
--   MASTER C_Order: one per work_output.db (the building project)
--     DocStatus: DR → IP → AP (approved for BOM) → CO (promoted, frozen)
--   SUB-WORK-ORDERS: each change set (Parent_Order_ID references master)
--     DocStatus: DR (spawned, in focus) → IP (editing) → CO (saved to output.db)
-- AP is EXCLUSIVE for BOM creation — strict compliance + host tack + dangle gate.
-- CO on sub-orders is cheap save. CO on master = promoted to BOM.
CREATE TABLE IF NOT EXISTS C_Order (
    C_Order_ID            TEXT PRIMARY KEY,       -- = W_BuildingConfig.building_id (master) or auto-generated (sub)
    Parent_Order_ID       TEXT REFERENCES C_Order(C_Order_ID),  -- NULL = master, non-NULL = sub-work-order
    C_DocType_ID          TEXT NOT NULL,          -- FK → {PREFIX}_BOM.db C_DocType (logical)
    Name                  TEXT NOT NULL,          -- display name
    Description           TEXT,
    DocStatus             TEXT NOT NULL DEFAULT 'DR'
                          CHECK(DocStatus IN ('DR','IP','AP','CO','VO')),
    aabb_width_mm         REAL NOT NULL,          -- design envelope X (may differ from site)
    aabb_depth_mm         REAL NOT NULL,          -- design envelope Y
    aabb_height_mm        REAL NOT NULL,          -- design envelope Z
    IsActive              INTEGER NOT NULL DEFAULT 1,
    created               TEXT NOT NULL DEFAULT (datetime('now')),
    updated               TEXT NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================================
-- 3. C_ORDERLINE — Design decisions (iDempiere C_OrderLine parallel)
-- ============================================================================

-- Each row = one design decision: "use THIS BOM/product at THIS position."
-- Enhanced vs output.db C_OrderLine: includes tack offsets (dx/dy/dz) for
-- positional editing, M_AttributeSetInstance_ID for parameter overrides,
-- and parent hierarchy for tree reconstruction.
--
-- Change tiers (§17.10.3):
--   Parameter change → update M_AttributeSetInstance (resize room)
--   Positional change → update dx/dy/dz (move room)
--   Structural change → INSERT/DELETE row (add/remove room)
CREATE TABLE IF NOT EXISTS C_OrderLine (
    C_OrderLine_ID        INTEGER PRIMARY KEY AUTOINCREMENT,
    C_Order_ID            TEXT NOT NULL REFERENCES C_Order(C_Order_ID),
    Parent_OrderLine_ID   INTEGER REFERENCES C_OrderLine(C_OrderLine_ID),  -- tree hierarchy (NULL = root)
    Line                  INTEGER NOT NULL DEFAULT 10,                     -- display sequence (iDempiere Line convention)

    -- WHAT: which BOM template or leaf product
    family_ref            TEXT NOT NULL,          -- m_bom.bom_id or M_Product.M_Product_ID
    host_type             TEXT NOT NULL,          -- BUILDING | FLOOR | DISCIPLINE | ROOM | LEAF
    bom_category          TEXT,                   -- M_Product_Category (discipline/room, denormalized from m_bom)

    -- WHERE: tack position (LBD convention, BBC.md §4)
    dx                    REAL NOT NULL DEFAULT 0,  -- child LBD X offset in parent (metres)
    dy                    REAL NOT NULL DEFAULT 0,  -- child LBD Y offset in parent (metres)
    dz                    REAL NOT NULL DEFAULT 0,  -- child LBD Z offset in parent (metres)

    -- HOW BIG: AABB of this assembly/product (mm)
    aabb_width_mm         REAL,
    aabb_depth_mm         REAL,
    aabb_height_mm        REAL,

    -- Per-instance overrides (iDempiere ASI pattern)
    M_AttributeSetInstance_ID  INTEGER REFERENCES M_AttributeSetInstance(M_AttributeSetInstance_ID),

    -- Metadata
    Qty                   INTEGER NOT NULL DEFAULT 1,
    IsActive              INTEGER NOT NULL DEFAULT 1,
    validation_status     TEXT DEFAULT 'UNCHECKED'
                          CHECK(validation_status IN ('UNCHECKED','PASS','WARN','FAIL')),
    created               TEXT NOT NULL DEFAULT (datetime('now')),
    updated               TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_orderline_order ON C_OrderLine(C_Order_ID);
CREATE INDEX IF NOT EXISTS idx_orderline_parent ON C_OrderLine(Parent_OrderLine_ID);
CREATE INDEX IF NOT EXISTS idx_orderline_family ON C_OrderLine(family_ref);

-- ============================================================================
-- 4. M_ATTRIBUTESETINSTANCE — Per-instance overrides (iDempiere ASI parallel)
-- ============================================================================

-- ASI header.  Each C_OrderLine may have one ASI that captures how THIS
-- instance differs from the BOM template default.
-- iDempiere pattern: M_AttributeSet (template, in BOM) → M_AttributeSetInstance
-- (instance, in order) → M_AttributeInstance (individual values).
--
-- Example: BOM says room width = 3100mm.  User drags to 4500mm.
--   → ASI row created, M_AttributeInstance row: name='width_mm', value='4500'
CREATE TABLE IF NOT EXISTS M_AttributeSetInstance (
    M_AttributeSetInstance_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
    M_AttributeSet_ID          TEXT,               -- FK → {PREFIX}_BOM.db M_AttributeSet (logical)
    Description                TEXT,               -- human-readable summary: "4500mm wide, cherry wood"
    created                    TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Individual attribute values within an ASI.
-- Flat name/value pairs — no schema enforcement (same as iDempiere).
CREATE TABLE IF NOT EXISTS M_AttributeInstance (
    M_AttributeInstance_ID     INTEGER PRIMARY KEY AUTOINCREMENT,
    M_AttributeSetInstance_ID  INTEGER NOT NULL
                               REFERENCES M_AttributeSetInstance(M_AttributeSetInstance_ID),
    M_Attribute_ID             TEXT,               -- FK → {PREFIX}_BOM.db m_attribute (logical, optional)
    Name                       TEXT NOT NULL,      -- 'width_mm', 'depth_mm', 'material', 'finish'
    Value                      TEXT NOT NULL,      -- '4500', 'cherry_wood', 'matte'
    ValueType                  TEXT NOT NULL DEFAULT 'NUM'
                               CHECK(ValueType IN ('NUM','TEXT','BOOL')),
    UNIQUE(M_AttributeSetInstance_ID, Name)
);

CREATE INDEX IF NOT EXISTS idx_attrinst_asi ON M_AttributeInstance(M_AttributeSetInstance_ID);

-- ============================================================================
-- 5. CO_EMPTYSPACELINE — Spatial slots (iDempiere CO_EmptySpace parallel)
-- ============================================================================

-- One header per C_Order (simplified — single row, not a separate table,
-- because work_output.db always has exactly one building).
CREATE TABLE IF NOT EXISTS CO_EmptySpace (
    co_emptyspace_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    C_Order_ID            TEXT NOT NULL REFERENCES C_Order(C_Order_ID),
    origin_x_mm           REAL NOT NULL DEFAULT 0,   -- building world LBD X
    origin_y_mm           REAL NOT NULL DEFAULT 0,   -- building world LBD Y
    origin_z_mm           REAL NOT NULL DEFAULT 0,   -- building world LBD Z
    aabb_width_mm         REAL NOT NULL,
    aabb_depth_mm         REAL NOT NULL,
    aabb_height_mm        REAL NOT NULL,
    is_available          INTEGER NOT NULL DEFAULT 1,  -- quality gate (§3.1 in ConstructionAsERP)
    doc_status            TEXT NOT NULL DEFAULT 'DR',
    created               TEXT NOT NULL DEFAULT (datetime('now')),
    updated               TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Spatial slot per BOM level.  Carries tack_from (where in parent space)
-- and capacity (how much space this slot offers).
-- Links to C_OrderLine via c_orderline_id (what was placed in this slot).
CREATE TABLE IF NOT EXISTS CO_EmptySpaceLine (
    line_id               INTEGER PRIMARY KEY AUTOINCREMENT,
    co_emptyspace_id      INTEGER NOT NULL REFERENCES CO_EmptySpace(co_emptyspace_id),
    C_OrderLine_ID        INTEGER REFERENCES C_OrderLine(C_OrderLine_ID),

    bom_line_seq          INTEGER NOT NULL,       -- sequence from M_BOM_Line
    bom_id                TEXT NOT NULL,           -- which M_BOM this slot accepts
    bom_line_role         TEXT,                    -- role (WALL_EXT, FURNITURE, BUFFER...)
    bom_level             INTEGER DEFAULT 0,       -- depth (0=building, 1=floor, 2=room...)

    -- Tack position: where in parent space this slot's LBD sits (BBC.md §4)
    tack_from_x_mm        REAL NOT NULL DEFAULT 0,
    tack_from_y_mm        REAL NOT NULL DEFAULT 0,
    tack_from_z_mm        REAL NOT NULL DEFAULT 0,

    -- Slot capacity: how much AABB space this slot offers
    capacity_width_mm     REAL,
    capacity_depth_mm     REAL,
    capacity_height_mm    REAL,

    -- Space accounting
    filled_width_mm       REAL DEFAULT 0,
    filled_depth_mm       REAL DEFAULT 0,
    filled_height_mm      REAL DEFAULT 0,

    -- Metadata
    storey                TEXT,
    room_name             TEXT,
    locator_ref           TEXT,                    -- NORTH_WALL, CENTRE, FLOAT...
    doc_status            TEXT NOT NULL DEFAULT 'DR',
    created               TEXT NOT NULL DEFAULT (datetime('now')),
    updated               TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_esline_space ON CO_EmptySpaceLine(co_emptyspace_id);
CREATE INDEX IF NOT EXISTS idx_esline_orderline ON CO_EmptySpaceLine(C_OrderLine_ID);

-- ============================================================================
-- 6. PP_ORDER_NODE — Production routing (iDempiere PP_Order_Node parallel)
-- ============================================================================

-- Verb execution record.  Each row = one operation in the construction sequence.
-- DocStatus tracks operation lifecycle: DR → IP → CO → VO.
-- The sequence defines construction order (foundation → frame → envelope → MEP → finishes).
CREATE TABLE IF NOT EXISTS PP_Order_Node (
    PP_Order_Node_ID      INTEGER PRIMARY KEY AUTOINCREMENT,
    C_Order_ID            TEXT NOT NULL REFERENCES C_Order(C_Order_ID),
    C_OrderLine_ID        INTEGER REFERENCES C_OrderLine(C_OrderLine_ID),

    Name                  TEXT NOT NULL,           -- operation name: 'TILE roof plates'
    SeqNo                 INTEGER NOT NULL,        -- execution order
    verb_ref              TEXT,                     -- BIM COBOL verb: TILE, ROUTE, CLUSTER, etc.

    -- Resource link (which slot this op works on)
    co_emptyspaceline_id  INTEGER REFERENCES CO_EmptySpaceLine(line_id),

    -- Lifecycle
    DocStatus             TEXT NOT NULL DEFAULT 'DR'
                          CHECK(DocStatus IN ('DR','IP','CO','VO')),
    Duration_minutes      INTEGER,                 -- estimated duration (future: scheduling)
    Description           TEXT,
    created               TEXT NOT NULL DEFAULT (datetime('now')),
    updated               TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Structured parameters for verb operations (name/value/type).
-- Same pattern as iDempiere PP_Order_NodeProduct.
CREATE TABLE IF NOT EXISTS PP_Order_NodeProduct (
    PP_Order_NodeProduct_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
    PP_Order_Node_ID         INTEGER NOT NULL REFERENCES PP_Order_Node(PP_Order_Node_ID),
    Name                     TEXT NOT NULL,        -- 'step_x_mm', 'count_x', 'origin_x'
    Value                    TEXT NOT NULL,         -- '495', '15', '92500'
    ValueType                TEXT DEFAULT 'NUM'
                             CHECK(ValueType IN ('NUM','TEXT','BOOL')),
    UNIQUE(PP_Order_Node_ID, Name)
);

CREATE INDEX IF NOT EXISTS idx_ppnode_order ON PP_Order_Node(C_Order_ID);

-- ============================================================================
-- 7. W_VARIANT — Lightweight pointer to sub-work-order (version metadata)
-- ============================================================================

-- DESIGN RATIONALE (master-detail refinement):
--   W_Variant is a POINTER, not a snapshot.  The sub-C_Order's C_OrderLine
--   rows ARE the version data — no duplication into snapshot_json.
--
--   Save = create sub-C_Order (DR→IP→CO) + W_Variant pointer to it.
--   Recall = set target sub-C_Order as active (new sub DR, old stays CO).
--   The iDempiere pattern: you don't snapshot an order into a blob —
--   you create a new revision as a proper document.
--
--   Sub-work-order C_OrderLine rows carry the full state (tack dx/dy/dz,
--   ASI overrides, family_ref).  W_Variant just records metadata for
--   the variant list UI (label, counts, compliance, timestamp).
--
-- Variant naming: user-configurable label (default: auto-incrementing).
-- Noted in YAML/Order profile per §17.10.
CREATE TABLE IF NOT EXISTS W_Variant (
    W_Variant_ID          INTEGER PRIMARY KEY AUTOINCREMENT,
    C_Order_ID            TEXT NOT NULL REFERENCES C_Order(C_Order_ID),  -- FK to SUB-work-order (not master)
    label                 TEXT NOT NULL,           -- user label: 'v1', 'wide-rooms', 'final'
    description           TEXT,
    is_active             INTEGER NOT NULL DEFAULT 0,  -- 1 = currently active variant (at most one per master)
    orderline_count       INTEGER NOT NULL,        -- summary: how many C_OrderLine rows in this sub-order
    esline_count          INTEGER NOT NULL DEFAULT 0,
    asi_count             INTEGER NOT NULL DEFAULT 0,
    ppnode_count          INTEGER NOT NULL DEFAULT 0,
    compliance_status     TEXT DEFAULT 'UNCHECKED'
                          CHECK(compliance_status IN ('UNCHECKED','PASSED','FAILED')),
    -- snapshot_json REMOVED: sub-C_Order's C_OrderLine rows ARE the snapshot.
    -- No data duplication.  Recall reads from the sub-order's tables directly.
    created               TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_variant_order ON W_Variant(C_Order_ID);

-- ============================================================================
-- 8. W_VALIDATION_RESULT — Per-line compliance (mirrors validation.db pattern)
-- ============================================================================

-- Validation results are stored locally in work_output.db (not in validation.db)
-- because they are per-design, not per-rule.  validation.db holds the rules;
-- work_output.db holds the results of applying those rules to this design.
CREATE TABLE IF NOT EXISTS W_Validation_Result (
    w_validation_result_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    C_OrderLine_ID          INTEGER NOT NULL REFERENCES C_OrderLine(C_OrderLine_ID),
    ad_val_rule_id          INTEGER NOT NULL,       -- FK → validation.db AD_Val_Rule (logical)
    tier                    INTEGER NOT NULL DEFAULT 1
                            CHECK(tier IN (1, 2, 3)),  -- §15.1: Tier 1/2/3
    result                  TEXT NOT NULL
                            CHECK(result IN ('PASS','WARN','BLOCK')),
    actual_value            REAL,
    required_value          REAL,
    message                 TEXT,
    created                 TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_valresult_orderline ON W_Validation_Result(C_OrderLine_ID);
CREATE INDEX IF NOT EXISTS idx_valresult_rule ON W_Validation_Result(ad_val_rule_id);

-- ============================================================================
-- 9. AD_SYSCONFIG — System configuration (iDempiere AD_SysConfig parallel)
-- ============================================================================

-- Key-value store for work_output.db metadata.
-- Self-contained: stores schema_version, spawn timestamp, source BOM path, etc.
CREATE TABLE IF NOT EXISTS AD_SysConfig (
    Name                  TEXT PRIMARY KEY,
    Value                 TEXT NOT NULL,
    Description           TEXT,
    updated               TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Seed system config
INSERT OR IGNORE INTO AD_SysConfig (Name, Value, Description)
VALUES ('SCHEMA_VERSION', 'W001', 'work_output.db schema version');
INSERT OR IGNORE INTO AD_SysConfig (Name, Value, Description)
VALUES ('CREATED_BY', 'ConstructionModelSpawner', 'Which component initialized this DB');
