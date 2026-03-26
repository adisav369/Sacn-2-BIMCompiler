-- ASI_002: iDempiere Attribute detail tables — M_Attribute, M_AttributeUse, M_AttributeValue.
-- Implementing BBC.md §3.5.2 — Product → Verb Routing via ASI.
--
-- Completes the iDempiere M_AttributeSet chain:
--   M_AttributeSet        (ASI_001) — template: "which attributes exist for this product type"
--   M_Attribute            (this)   — individual attribute definitions
--   M_AttributeUse         (this)   — join: which attributes belong to which set
--   M_AttributeValue       (this)   — valid list-of-values for LIST-type attributes
--
-- Also adds missing FK columns:
--   M_Product.M_AttributeSet_ID      → product → its attribute set
--   c_orderline.M_AttributeSetInstance_ID → order line → its per-instance ASI
--
-- Seed data from BIM_Designer_SRS.md §28.7 field resolution matrix.

-- ── Attribute definitions ────────────────────────────────────────

CREATE TABLE IF NOT EXISTS M_Attribute (
    M_Attribute_ID       INTEGER PRIMARY KEY AUTOINCREMENT,
    Name                 TEXT NOT NULL UNIQUE,   -- 'length_mm', 'trim_action', etc.
    Description          TEXT,
    ValueType            TEXT NOT NULL DEFAULT 'NUM'
                         CHECK(ValueType IN ('NUM','TEXT','LIST')),
    IsInstanceAttribute  INTEGER NOT NULL DEFAULT 1,  -- 1 = varies per instance, 0 = fixed
    DefaultValue         TEXT                          -- default if no ASI override
);

-- ── Join table: attribute → attribute set ────────────────────────

CREATE TABLE IF NOT EXISTS M_AttributeUse (
    M_AttributeUse_ID    INTEGER PRIMARY KEY AUTOINCREMENT,
    M_AttributeSet_ID    TEXT NOT NULL REFERENCES M_AttributeSet(M_AttributeSet_ID),
    M_Attribute_ID       INTEGER NOT NULL REFERENCES M_Attribute(M_Attribute_ID),
    SeqNo                INTEGER NOT NULL DEFAULT 10,
    UNIQUE(M_AttributeSet_ID, M_Attribute_ID)
);

-- ── Valid values for LIST-type attributes ────────────────────────

CREATE TABLE IF NOT EXISTS M_AttributeValue (
    M_AttributeValue_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
    M_Attribute_ID       INTEGER NOT NULL REFERENCES M_Attribute(M_Attribute_ID),
    Value                TEXT NOT NULL,
    Name                 TEXT,           -- display name (NULL = same as Value)
    SeqNo                INTEGER NOT NULL DEFAULT 10,
    UNIQUE(M_Attribute_ID, Value)
);

-- ── FK on M_Product ──────────────────────────────────────────────

ALTER TABLE M_Product ADD COLUMN M_AttributeSet_ID TEXT
    REFERENCES M_AttributeSet(M_AttributeSet_ID);

-- ── Indexes ──────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_m_attribute_use_set
    ON M_AttributeUse(M_AttributeSet_ID);

CREATE INDEX IF NOT EXISTS idx_m_attribute_value_attr
    ON M_AttributeValue(M_Attribute_ID);

-- ══════════════════════════════════════════════════════════════════
-- SEED DATA — from BIM_Designer_SRS.md §28.7 field resolution matrix
-- ══════════════════════════════════════════════════════════════════

-- ── Geometric attributes (per-instance, from §28.7) ─────────────

INSERT OR IGNORE INTO M_Attribute (Name, Description, ValueType, IsInstanceAttribute, DefaultValue) VALUES
    ('length_mm',       'Element length in millimetres',          'NUM',  1, NULL),
    ('height_mm',       'Element height in millimetres',          'NUM',  1, NULL),
    ('span_mm',         'Beam span in millimetres',               'NUM',  1, NULL),
    ('area_m2',         'Slab area in square metres',             'NUM',  1, NULL),
    ('thickness_mm',    'Slab thickness in millimetres',          'NUM',  1, NULL),
    ('angle_deg',       'Pipe bend angle in degrees',             'NUM',  1, NULL),
    ('elevation',       'Pipe elevation above datum',             'NUM',  1, NULL),
    ('cross_section',   'Duct cross-section descriptor',          'TEXT', 1, NULL),
    ('sill_height_mm',  'Window sill height in millimetres',      'NUM',  1, NULL),
    ('material',        'Material override',                      'TEXT', 1, NULL),
    ('finish',          'Surface finish override',                'TEXT', 1, NULL),
    ('swing_side',      'Door swing side (LEFT/RIGHT)',           'TEXT', 1, NULL),
    ('face_anchor',     'Face anchor (INT/EXT)',                  'TEXT', 1, NULL);

-- ── Verb-parameter attributes ────────────────────────────────────

INSERT OR IGNORE INTO M_Attribute (Name, Description, ValueType, IsInstanceAttribute, DefaultValue) VALUES
    ('trim_action',       'Trim verb action override',                    'LIST', 1, 'DEFAULT'),
    ('trim_tolerance_mm', 'Trim tolerance in millimetres',                'NUM',  1, '50'),
    ('joint_type',        'Joint type for structural connections',        'LIST', 1, 'BUTT'),
    ('connection_type',   'MEP connection type',                          'LIST', 1, 'SOCKET'),
    ('fire_rating_min',   'Fire rating in minutes',                       'LIST', 1, '0');

-- ── M_AttributeUse: map attributes to sets (from §28.7) ─────────

-- BIM_Wall: length_mm, height_mm, material, finish + verb params
INSERT OR IGNORE INTO M_AttributeUse (M_AttributeSet_ID, M_Attribute_ID, SeqNo)
SELECT 'BIM_Wall', M_Attribute_ID, 10 FROM M_Attribute WHERE Name = 'length_mm'
UNION ALL SELECT 'BIM_Wall', M_Attribute_ID, 20 FROM M_Attribute WHERE Name = 'height_mm'
UNION ALL SELECT 'BIM_Wall', M_Attribute_ID, 30 FROM M_Attribute WHERE Name = 'material'
UNION ALL SELECT 'BIM_Wall', M_Attribute_ID, 40 FROM M_Attribute WHERE Name = 'finish'
UNION ALL SELECT 'BIM_Wall', M_Attribute_ID, 50 FROM M_Attribute WHERE Name = 'trim_action'
UNION ALL SELECT 'BIM_Wall', M_Attribute_ID, 60 FROM M_Attribute WHERE Name = 'trim_tolerance_mm'
UNION ALL SELECT 'BIM_Wall', M_Attribute_ID, 70 FROM M_Attribute WHERE Name = 'joint_type'
UNION ALL SELECT 'BIM_Wall', M_Attribute_ID, 80 FROM M_Attribute WHERE Name = 'fire_rating_min';

-- BIM_Pipe: length_mm, angle_deg, elevation + connection_type
INSERT OR IGNORE INTO M_AttributeUse (M_AttributeSet_ID, M_Attribute_ID, SeqNo)
SELECT 'BIM_Pipe', M_Attribute_ID, 10 FROM M_Attribute WHERE Name = 'length_mm'
UNION ALL SELECT 'BIM_Pipe', M_Attribute_ID, 20 FROM M_Attribute WHERE Name = 'angle_deg'
UNION ALL SELECT 'BIM_Pipe', M_Attribute_ID, 30 FROM M_Attribute WHERE Name = 'elevation'
UNION ALL SELECT 'BIM_Pipe', M_Attribute_ID, 40 FROM M_Attribute WHERE Name = 'connection_type';

-- BIM_Conduit: length_mm + connection_type
INSERT OR IGNORE INTO M_AttributeUse (M_AttributeSet_ID, M_Attribute_ID, SeqNo)
SELECT 'BIM_Conduit', M_Attribute_ID, 10 FROM M_Attribute WHERE Name = 'length_mm'
UNION ALL SELECT 'BIM_Conduit', M_Attribute_ID, 20 FROM M_Attribute WHERE Name = 'connection_type';

-- BIM_Duct: length_mm, cross_section + connection_type
INSERT OR IGNORE INTO M_AttributeUse (M_AttributeSet_ID, M_Attribute_ID, SeqNo)
SELECT 'BIM_Duct', M_Attribute_ID, 10 FROM M_Attribute WHERE Name = 'length_mm'
UNION ALL SELECT 'BIM_Duct', M_Attribute_ID, 20 FROM M_Attribute WHERE Name = 'cross_section'
UNION ALL SELECT 'BIM_Duct', M_Attribute_ID, 30 FROM M_Attribute WHERE Name = 'connection_type';

-- BIM_Door: swing_side, face_anchor + fire_rating_min
INSERT OR IGNORE INTO M_AttributeUse (M_AttributeSet_ID, M_Attribute_ID, SeqNo)
SELECT 'BIM_Door', M_Attribute_ID, 10 FROM M_Attribute WHERE Name = 'swing_side'
UNION ALL SELECT 'BIM_Door', M_Attribute_ID, 20 FROM M_Attribute WHERE Name = 'face_anchor'
UNION ALL SELECT 'BIM_Door', M_Attribute_ID, 30 FROM M_Attribute WHERE Name = 'fire_rating_min';

-- BIM_Window: sill_height_mm, face_anchor
INSERT OR IGNORE INTO M_AttributeUse (M_AttributeSet_ID, M_Attribute_ID, SeqNo)
SELECT 'BIM_Window', M_Attribute_ID, 10 FROM M_Attribute WHERE Name = 'sill_height_mm'
UNION ALL SELECT 'BIM_Window', M_Attribute_ID, 20 FROM M_Attribute WHERE Name = 'face_anchor';

-- BIM_Slab: area_m2, thickness_mm + fire_rating_min
INSERT OR IGNORE INTO M_AttributeUse (M_AttributeSet_ID, M_Attribute_ID, SeqNo)
SELECT 'BIM_Slab', M_Attribute_ID, 10 FROM M_Attribute WHERE Name = 'area_m2'
UNION ALL SELECT 'BIM_Slab', M_Attribute_ID, 20 FROM M_Attribute WHERE Name = 'thickness_mm'
UNION ALL SELECT 'BIM_Slab', M_Attribute_ID, 30 FROM M_Attribute WHERE Name = 'fire_rating_min';

-- BIM_Beam: span_mm + joint_type
INSERT OR IGNORE INTO M_AttributeUse (M_AttributeSet_ID, M_Attribute_ID, SeqNo)
SELECT 'BIM_Beam', M_Attribute_ID, 10 FROM M_Attribute WHERE Name = 'span_mm'
UNION ALL SELECT 'BIM_Beam', M_Attribute_ID, 20 FROM M_Attribute WHERE Name = 'joint_type';

-- BIM_Column: height_mm + joint_type
INSERT OR IGNORE INTO M_AttributeUse (M_AttributeSet_ID, M_Attribute_ID, SeqNo)
SELECT 'BIM_Column', M_Attribute_ID, 10 FROM M_Attribute WHERE Name = 'height_mm'
UNION ALL SELECT 'BIM_Column', M_Attribute_ID, 20 FROM M_Attribute WHERE Name = 'joint_type';

-- BIM_Component + BIM_Fitting: no attributes (IsInstanceAttribute=0)

-- ── M_AttributeValue: valid values for LIST-type attributes ──────

-- trim_action values
INSERT OR IGNORE INTO M_AttributeValue (M_Attribute_ID, Value, Name, SeqNo)
SELECT M_Attribute_ID, 'DEFAULT',  'Default (verb decides)',  10 FROM M_Attribute WHERE Name = 'trim_action'
UNION ALL SELECT M_Attribute_ID, 'SKIP',      'Skip trim',              20 FROM M_Attribute WHERE Name = 'trim_action'
UNION ALL SELECT M_Attribute_ID, 'CUT_ONLY',  'Cut only (no fill)',     30 FROM M_Attribute WHERE Name = 'trim_action'
UNION ALL SELECT M_Attribute_ID, 'CUT_FILL',  'Cut and fill',           40 FROM M_Attribute WHERE Name = 'trim_action';

-- joint_type values
INSERT OR IGNORE INTO M_AttributeValue (M_Attribute_ID, Value, Name, SeqNo)
SELECT M_Attribute_ID, 'BUTT',  'Butt joint',   10 FROM M_Attribute WHERE Name = 'joint_type'
UNION ALL SELECT M_Attribute_ID, 'MITRE', 'Mitre joint',  20 FROM M_Attribute WHERE Name = 'joint_type'
UNION ALL SELECT M_Attribute_ID, 'LAP',   'Lap joint',    30 FROM M_Attribute WHERE Name = 'joint_type';

-- connection_type values
INSERT OR IGNORE INTO M_AttributeValue (M_Attribute_ID, Value, Name, SeqNo)
SELECT M_Attribute_ID, 'SOCKET', 'Socket connection', 10 FROM M_Attribute WHERE Name = 'connection_type'
UNION ALL SELECT M_Attribute_ID, 'FLANGE', 'Flange connection', 20 FROM M_Attribute WHERE Name = 'connection_type'
UNION ALL SELECT M_Attribute_ID, 'WELD',   'Weld connection',   30 FROM M_Attribute WHERE Name = 'connection_type';

-- fire_rating_min values
INSERT OR IGNORE INTO M_AttributeValue (M_Attribute_ID, Value, Name, SeqNo)
SELECT M_Attribute_ID, '0',   'No rating',   10 FROM M_Attribute WHERE Name = 'fire_rating_min'
UNION ALL SELECT M_Attribute_ID, '30',  '30 min',      20 FROM M_Attribute WHERE Name = 'fire_rating_min'
UNION ALL SELECT M_Attribute_ID, '60',  '60 min',      30 FROM M_Attribute WHERE Name = 'fire_rating_min'
UNION ALL SELECT M_Attribute_ID, '90',  '90 min',      40 FROM M_Attribute WHERE Name = 'fire_rating_min'
UNION ALL SELECT M_Attribute_ID, '120', '120 min',     50 FROM M_Attribute WHERE Name = 'fire_rating_min';
