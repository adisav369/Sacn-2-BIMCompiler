-- Phase NORM-2: Unify m_bom_line three-way split into child_product_id → M_Product
-- Requires NORM-1 (M_Product exists with bom_id FK for MAKE stubs).
-- Gate: 207 PASS / 1 RED / 1 SKIP unchanged.

PRAGMA legacy_alter_table = OFF;
PRAGMA foreign_keys = OFF;

-- ── Step 1: Add ifc_class to M_Product ────────────────────────────────────────
-- Needed so BOMAssemblerAD can resolve IFC element type from child_product_id
-- instead of the dropped child_ifc_class column.
ALTER TABLE M_Product ADD COLUMN ifc_class TEXT;

-- ── Step 2: PHANTOM M_Product stubs (inline-expanded, no output record) ───────
-- 9 distinct roles: BUFFER, CORRIDOR, EAST_UNITS, LIFT_LOBBY, STAIR_A,
--                   STAIR_B, TOILET, WALL_BODY, WEST_UNITS
INSERT OR IGNORE INTO M_Product (product_id, product_type, width, depth, height, is_active, extracted_from)
SELECT DISTINCT role, 'PHANTOM', 0.001, 0.001, 0.001, 0, 'PHANTOM'
FROM m_bom_line WHERE component_type = 'PHANTOM';

-- ── Step 3: Structural BUY M_Product stubs (one per unique ifc_class) ─────────
-- 25 distinct IFC classes (IfcBeam, IfcDoor, …). product_id = ifc_class string.
INSERT OR IGNORE INTO M_Product (product_id, product_type, width, depth, height, is_active, extracted_from, ifc_class)
SELECT DISTINCT child_ifc_class, 'STRUCTURAL', 0.001, 0.001, 0.001, 0, 'STRUCTURAL', child_ifc_class
FROM m_bom_line
WHERE component_type = 'BUY' AND product_ref IS NULL AND child_ifc_class IS NOT NULL;

-- ── Step 4: Populate ifc_class for active BUY M_Product rows ──────────────────
-- BUY rows that have BOTH product_ref AND child_ifc_class: propagate to M_Product.
UPDATE M_Product
SET ifc_class = (
    SELECT MIN(child_ifc_class)
    FROM m_bom_line
    WHERE product_ref = M_Product.product_id AND child_ifc_class IS NOT NULL
)
WHERE is_active = 1
  AND ifc_class IS NULL
  AND product_id IN (
      SELECT DISTINCT product_ref FROM m_bom_line WHERE child_ifc_class IS NOT NULL
  );

-- ── Step 5: Add child_product_id to m_bom_line ────────────────────────────────
ALTER TABLE m_bom_line ADD COLUMN child_product_id TEXT REFERENCES M_Product(product_id);

-- ── Step 6: Populate child_product_id (4 cases) ───────────────────────────────
-- BUY rows with product_ref (catalog leaf — M_Product exists):
UPDATE m_bom_line SET child_product_id = product_ref
WHERE component_type = 'BUY' AND product_ref IS NOT NULL;

-- BUY rows with only child_ifc_class (structural leaf — stub just created above):
UPDATE m_bom_line SET child_product_id = child_ifc_class
WHERE component_type = 'BUY' AND product_ref IS NULL AND child_ifc_class IS NOT NULL;

-- MAKE rows (assembly BOMs — stubs created in NORM-1):
UPDATE m_bom_line SET child_product_id = child_bom_id
WHERE component_type = 'MAKE';

-- PHANTOM rows (buffer/spacer stubs created above):
UPDATE m_bom_line SET child_product_id = role
WHERE component_type = 'PHANTOM';

-- Sanity: verify all rows populated
-- SELECT COUNT(*) FROM m_bom_line WHERE child_product_id IS NULL;  -- must be 0

-- ── Step 7: Recreate m_bom_line ───────────────────────────────────────────────
-- Drops: child_bom_id (FK constraint prevents direct DROP COLUMN),
--        product_ref (inline ref), child_ifc_class (no FK).
-- Renames: space_*_mm → allocated_*_mm (nullable; null = M_Product dims × 1000).
-- New FK: only bom_id → m_bom (child_bom_id FK removed).

CREATE TABLE m_bom_line_new (
    bom_child_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    bom_id              TEXT NOT NULL,            -- Parent BOM

    -- Child reference: single FK replaces three-way split (NORM-2)
    child_product_id    TEXT REFERENCES M_Product(product_id),

    child_element_type  TEXT,                     -- Optional element_type filter
    child_name_pattern  TEXT,                     -- Optional LIKE pattern on element_name

    -- Role and ordering
    role                TEXT NOT NULL,
    qty_type            TEXT DEFAULT 'VARIABLE',
    sequence            INTEGER DEFAULT 100,

    is_active           INTEGER DEFAULT 1,
    z_rule              TEXT DEFAULT NULL,
    dx                  REAL DEFAULT 0.0,
    dy                  REAL DEFAULT 0.0,
    dz                  REAL DEFAULT 0.0,
    rotation_rule       TEXT DEFAULT '0',
    fit_priority        INTEGER NOT NULL DEFAULT 20,
    min_space_mm        INTEGER NOT NULL DEFAULT 0,
    locator_ref         TEXT DEFAULT 'FLOAT',
    is_variance         INTEGER DEFAULT 0,
    anchor_face         TEXT DEFAULT 'BACK',
    layout_strategy     TEXT DEFAULT 'LINEAR',

    -- SpaceSize renamed: allocated_*_mm (null = derive from M_Product dims × 1000)
    allocated_width_mm  INTEGER DEFAULT 0,
    allocated_depth_mm  INTEGER DEFAULT 0,
    allocated_height_mm INTEGER DEFAULT 0,

    component_type      TEXT NOT NULL DEFAULT 'MAKE',

    FOREIGN KEY (bom_id) REFERENCES m_bom(bom_id)
);

INSERT INTO m_bom_line_new
    (bom_child_id, bom_id, child_product_id, child_element_type, child_name_pattern,
     role, qty_type, sequence, is_active, z_rule, dx, dy, dz, rotation_rule,
     fit_priority, min_space_mm, locator_ref, is_variance, anchor_face, layout_strategy,
     allocated_width_mm, allocated_depth_mm, allocated_height_mm, component_type)
SELECT
    bom_child_id, bom_id, child_product_id, child_element_type, child_name_pattern,
    role, qty_type, sequence, is_active, z_rule, dx, dy, dz, rotation_rule,
    fit_priority, min_space_mm, locator_ref, is_variance, anchor_face, layout_strategy,
    space_width_mm, space_depth_mm, space_height_mm, component_type
FROM m_bom_line;

DROP TABLE m_bom_line;
ALTER TABLE m_bom_line_new RENAME TO m_bom_line;

PRAGMA foreign_keys = ON;
