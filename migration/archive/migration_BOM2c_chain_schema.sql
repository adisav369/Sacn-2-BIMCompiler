-- =============================================================================
-- Phase BOM-2c: AD Schema Refactor — IBOMChildLine Mandatory Fields
-- =============================================================================
-- Promotes dx, dy, dz, rotation_rule from EAV (ad_bom_child_param) to typed
-- columns in ad_bom_child, as required by the IBOMChildLine contract.
--
-- Before: dx/dy/dz/rotation_rule are optional EAV rows — missing = silent 0.
--         A BOM child with no dx row placed all its children at the origin.
--         This is the root cause of TB-LKTN furniture piling / wrong orientation.
--
-- After:  dx/dy/dz/rotation_rule are first-class columns in ad_bom_child.
--         DEFAULT '0' makes the value explicit; back-filled from existing EAV.
--         IBOMCatalogEnforcer and FurnitureBOMResolver read from typed columns.
--
-- Also adds:
--   ad_bom.bom_level    — explicit UNIT/FLOOR/ROOM/SET/LEAF hierarchy level
--   ad_element_rule.position_value_3 — Z offset for FLOOR-level Orderlines
--
-- ERP mapping:
--   ad_bom_child (M_BOM_Line) — dx/dy/dz = positional attributes on the line
--   ad_bom       (M_BOM)     — bom_level = which layer of the assembly hierarchy
--   ad_element_rule (C_OrderLine) — position_value_3 = storey Z for FLOOR lines
-- =============================================================================

-- ── Step 1: Promote positional fields to ad_bom_child typed columns ──────────

ALTER TABLE ad_bom_child ADD COLUMN dx            REAL    DEFAULT 0.0;
ALTER TABLE ad_bom_child ADD COLUMN dy            REAL    DEFAULT 0.0;
ALTER TABLE ad_bom_child ADD COLUMN dz            REAL    DEFAULT 0.0;
ALTER TABLE ad_bom_child ADD COLUMN rotation_rule TEXT    DEFAULT '0';

-- Back-fill dx from EAV (take first DOUBLE row per child)
UPDATE ad_bom_child
   SET dx = CAST(
       (SELECT param_value FROM ad_bom_child_param p
         WHERE p.bom_child_id = ad_bom_child.bom_child_id
           AND p.param_key    = 'dx'
           AND p.param_type   = 'DOUBLE'
         LIMIT 1) AS REAL)
 WHERE EXISTS (
       SELECT 1 FROM ad_bom_child_param p
        WHERE p.bom_child_id = ad_bom_child.bom_child_id
          AND p.param_key    = 'dx');

-- Back-fill dy
UPDATE ad_bom_child
   SET dy = CAST(
       (SELECT param_value FROM ad_bom_child_param p
         WHERE p.bom_child_id = ad_bom_child.bom_child_id
           AND p.param_key    = 'dy'
           AND p.param_type   = 'DOUBLE'
         LIMIT 1) AS REAL)
 WHERE EXISTS (
       SELECT 1 FROM ad_bom_child_param p
        WHERE p.bom_child_id = ad_bom_child.bom_child_id
          AND p.param_key    = 'dy');

-- Back-fill dz
UPDATE ad_bom_child
   SET dz = CAST(
       (SELECT param_value FROM ad_bom_child_param p
         WHERE p.bom_child_id = ad_bom_child.bom_child_id
           AND p.param_key    = 'dz'
           AND p.param_type   = 'DOUBLE'
         LIMIT 1) AS REAL)
 WHERE EXISTS (
       SELECT 1 FROM ad_bom_child_param p
        WHERE p.bom_child_id = ad_bom_child.bom_child_id
          AND p.param_key    = 'dz');

-- Back-fill rotation_rule (TEXT takes priority over DOUBLE for semantic rules)
UPDATE ad_bom_child
   SET rotation_rule = (
       SELECT param_value FROM ad_bom_child_param p
        WHERE p.bom_child_id = ad_bom_child.bom_child_id
          AND p.param_key    = 'rotation_rule'
          AND p.param_type   = 'TEXT'
        LIMIT 1)
 WHERE EXISTS (
       SELECT 1 FROM ad_bom_child_param p
        WHERE p.bom_child_id = ad_bom_child.bom_child_id
          AND p.param_key    = 'rotation_rule'
          AND p.param_type   = 'TEXT');

-- Numeric rotation_rule (DOUBLE) for children that have no TEXT semantic rule
UPDATE ad_bom_child
   SET rotation_rule = (
       SELECT param_value FROM ad_bom_child_param p
        WHERE p.bom_child_id = ad_bom_child.bom_child_id
          AND p.param_key    = 'rotation_rule'
          AND p.param_type   = 'DOUBLE'
        LIMIT 1)
 WHERE rotation_rule = '0'
   AND EXISTS (
       SELECT 1 FROM ad_bom_child_param p
        WHERE p.bom_child_id = ad_bom_child.bom_child_id
          AND p.param_key    = 'rotation_rule'
          AND p.param_type   = 'DOUBLE');

-- ── Step 2: Add bom_level to ad_bom ─────────────────────────────────────────
-- Explicit hierarchy level — no longer inferred from loose group_by text.
-- Values: UNIT | FLOOR | ROOM | SET | LEAF

ALTER TABLE ad_bom ADD COLUMN bom_level TEXT DEFAULT 'SET';

UPDATE ad_bom SET bom_level = 'UNIT'  WHERE group_by = 'BUILDING';
UPDATE ad_bom SET bom_level = 'FLOOR' WHERE group_by = 'STOREY';
UPDATE ad_bom SET bom_level = 'ROOM'  WHERE group_by = 'ROOM';
-- group_by = NULL or other → stays 'SET' (furniture sets, MEP assemblies, fixture blocks)

-- ── Step 3: Add position_value_3 to ad_element_rule ─────────────────────────
-- Third positional value for FLOOR-level Orderlines: storey Z offset in mm.
-- UNIT Orderlines: position_value_3 = 0 (building sits at ground)
-- FLOOR Orderlines: position_value_3 = storey Z above unit origin (mm)
--   e.g. FLOOR_DX_L2_STD: position_value_3 = 3000.0 (3m above Level 1)

ALTER TABLE ad_element_rule ADD COLUMN position_value_3 REAL DEFAULT 0.0;

-- =============================================================================
-- Verification queries (run manually):
--
-- Confirm typed columns back-filled:
--   SELECT bom_child_id, dx, dy, dz, rotation_rule FROM ad_bom_child
--    WHERE dx != 0 OR dy != 0 OR dz != 0 OR rotation_rule != '0'
--    LIMIT 20;
--
-- Confirm bom_level set:
--   SELECT bom_level, COUNT(*) FROM ad_bom GROUP BY bom_level;
--
-- Expected: UNIT=3, FLOOR=4, SET=N (furniture/MEP assemblies)
-- =============================================================================
