-- ASI_001: Add M_AttributeSetInstance support to BOM databases.
-- Implementing BBC.md §3.5.1 — Per-Instance Customization.
--
-- Pattern: same as iDempiere ERP manufacturing BOM.
-- Product = "Poly Steel Pipe" (fixed identity: diameter, material).
-- ASI = { length_mm: 3200, angle_deg: 45 } (varies per instance).
--
-- Three tables:
--   M_AttributeSet        — template: "which attributes exist for this product type"
--   M_AttributeSetInstance — header: "one customized instance"
--   M_AttributeInstance    — values: "name=value pairs for that instance"
--
-- m_bom_line gains M_AttributeSetInstance_ID FK for per-instance overrides.
-- Compiler resolution: effective = ASI_override ?? allocated_*_mm ?? M_Product.

-- ── Templates ─────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS M_AttributeSet (
    M_AttributeSet_ID   TEXT PRIMARY KEY,
    Name                TEXT NOT NULL,
    IsInstanceAttribute INTEGER NOT NULL DEFAULT 0,
    Description         TEXT
);

INSERT OR IGNORE INTO M_AttributeSet VALUES
    ('BIM_Pipe',      'Pipe',      1, 'Cross-section product, length/angle vary per instance'),
    ('BIM_Conduit',   'Conduit',   1, 'Electrical conduit, length varies per instance'),
    ('BIM_Duct',      'Duct',      1, 'HVAC duct, length/cross-section vary per instance'),
    ('BIM_Wall',      'Wall',      1, 'Wall type: thickness fixed, length/height vary'),
    ('BIM_Slab',      'Slab',      1, 'Floor slab: thickness fixed, area varies'),
    ('BIM_Beam',      'Beam',      1, 'Structural beam: section fixed, span varies'),
    ('BIM_Column',    'Column',    1, 'Structural column: section fixed, height varies'),
    ('BIM_Component', 'Component', 0, 'Discrete item: identical everywhere, no instance attributes'),
    ('BIM_Fitting',   'Fitting',   0, 'MEP fitting: identical per type, angle is type not instance');

-- ── Instance headers ──────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS M_AttributeSetInstance (
    M_AttributeSetInstance_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
    M_AttributeSet_ID          TEXT REFERENCES M_AttributeSet(M_AttributeSet_ID),
    Description                TEXT     -- human summary: "3200mm, 45°"
);

-- ── Instance values (name/value pairs) ────────────────────────────

CREATE TABLE IF NOT EXISTS M_AttributeInstance (
    M_AttributeInstance_ID     INTEGER PRIMARY KEY AUTOINCREMENT,
    M_AttributeSetInstance_ID  INTEGER NOT NULL
        REFERENCES M_AttributeSetInstance(M_AttributeSetInstance_ID),
    Name                       TEXT NOT NULL,    -- 'width_mm', 'length_mm', 'angle_deg'
    Value                      TEXT NOT NULL,    -- '3200', '45'
    ValueType                  TEXT NOT NULL DEFAULT 'NUM'
        CHECK(ValueType IN ('NUM','TEXT','BOOL')),
    UNIQUE(M_AttributeSetInstance_ID, Name)
);

-- ── FK on m_bom_line ──────────────────────────────────────────────

-- SQLite ALTER TABLE ADD COLUMN — nullable FK, backward compatible.
-- Existing rows get NULL (no ASI override = use allocated_*_mm as before).
ALTER TABLE m_bom_line ADD COLUMN M_AttributeSetInstance_ID INTEGER
    REFERENCES M_AttributeSetInstance(M_AttributeSetInstance_ID);
