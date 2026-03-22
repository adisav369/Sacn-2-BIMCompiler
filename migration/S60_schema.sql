-- S60_schema.sql — ERP Model Alignment schema additions
-- APPEND-ONLY: never modify this migration after first run
--
-- Adds C_Order + C_OrderLine to compile DB for OrderLine-driven compilation.
-- Adds U2 (Discipline), U3 (Jurisdiction), U4 (OccupancyClass), U6 (AD_Val_Rule_Exception).

-- ── C_Order: Construction Order header ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS C_Order (
    C_Order_ID        TEXT PRIMARY KEY,
    C_DocType_ID      TEXT NOT NULL,
    Name              TEXT NOT NULL,
    DocStatus         TEXT NOT NULL DEFAULT 'DR'
                      CHECK(DocStatus IN ('DR','IP','AP','CO','VO')),
    aabb_width_mm     REAL NOT NULL DEFAULT 0,
    aabb_depth_mm     REAL NOT NULL DEFAULT 0,
    aabb_height_mm    REAL NOT NULL DEFAULT 0,
    Jurisdiction      TEXT,          -- U3: MY/US/UK — building code jurisdiction
    OccupancyClass    TEXT,          -- U4: LH/OH1 — NFPA 13 occupancy class
    IsActive          INTEGER NOT NULL DEFAULT 1,
    created           TEXT NOT NULL DEFAULT (datetime('now')),
    updated           TEXT NOT NULL DEFAULT (datetime('now'))
);

-- ── C_OrderLine: Design decisions (BOM Drop tree) ──────────────────────────
CREATE TABLE IF NOT EXISTS C_OrderLine (
    C_OrderLine_ID    INTEGER PRIMARY KEY AUTOINCREMENT,
    C_Order_ID        TEXT NOT NULL REFERENCES C_Order(C_Order_ID),
    Parent_OrderLine_ID INTEGER REFERENCES C_OrderLine(C_OrderLine_ID),
    Line              INTEGER NOT NULL DEFAULT 10,
    family_ref        TEXT NOT NULL,          -- m_bom.bom_id or M_Product.M_Product_ID
    host_type         TEXT NOT NULL,          -- BUILDING | FLOOR | ROOM | LEAF
    bom_category      TEXT,                   -- M_Product_Category
    bom_child_id      INTEGER,               -- FK → m_bom_line.bom_child_id (join-back for leaf details)
    dx                REAL NOT NULL DEFAULT 0,
    dy                REAL NOT NULL DEFAULT 0,
    dz                REAL NOT NULL DEFAULT 0,
    aabb_width_mm     REAL,
    aabb_depth_mm     REAL,
    aabb_height_mm    REAL,
    M_Product_ID      TEXT,
    Discipline        TEXT DEFAULT 'ARC',     -- U2: ARC/STR/FP/MEP/ELEC
    Qty               INTEGER NOT NULL DEFAULT 1,
    IsActive          INTEGER NOT NULL DEFAULT 1,
    created           TEXT NOT NULL DEFAULT (datetime('now')),
    updated           TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_orderline_order ON C_OrderLine(C_Order_ID);
CREATE INDEX IF NOT EXISTS idx_orderline_parent ON C_OrderLine(Parent_OrderLine_ID);
CREATE INDEX IF NOT EXISTS idx_orderline_family ON C_OrderLine(family_ref);

-- ── U6: AD_Val_Rule_Exception — user override of WARN validation ───────────
CREATE TABLE IF NOT EXISTS AD_Val_Rule_Exception (
    AD_Val_Rule_Exception_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
    C_OrderLine_ID            INTEGER NOT NULL,
    ad_val_rule_id            INTEGER NOT NULL,
    reason                    TEXT NOT NULL,        -- user justification
    approved_by               TEXT,                 -- who approved
    created                   TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(C_OrderLine_ID, ad_val_rule_id)
);
