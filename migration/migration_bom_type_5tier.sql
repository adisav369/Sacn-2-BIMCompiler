-- ============================================================
-- bom_type 5-Tier Fix
-- Date: 2026-02-23
-- VIEW_CONTRACTS.md §4.3 correction
--
-- Problem: ad_bom.bom_type CHECK was IN ('ROOM','SET','ITEM').
--   UNIT and FLOOR assemblies were forced into bom_type='ROOM' (wrong).
-- Fix: recreate ad_bom with 5-value CHECK; re-seed correct tiers.
--
-- SQLite cannot ALTER CHECK — table recreation required.
-- Preserve ALL columns including bom_level.
--
-- Expected result: FLOOR=6, SET=25, UNIT=3
-- ============================================================

BEGIN TRANSACTION;

-- Drop views that reference ad_bom (required before table recreation in SQLite)
DROP VIEW IF EXISTS v_qualified_bom;
DROP VIEW IF EXISTS v_active_bom_assembly;
DROP VIEW IF EXISTS v_component_leaf;

CREATE TABLE ad_bom_new (
    bom_id            TEXT PRIMARY KEY,
    bom_name          TEXT NOT NULL,
    description       TEXT,
    target_ifc_class  TEXT DEFAULT 'IfcElementAssembly',
    group_by          TEXT NOT NULL,
    is_active         INTEGER DEFAULT 1,
    bom_level         TEXT DEFAULT 'SET',
    bom_type          TEXT NOT NULL DEFAULT 'SET'
        CHECK(bom_type IN ('UNIT', 'FLOOR', 'ROOM', 'SET', 'ITEM'))
);

INSERT INTO ad_bom_new SELECT * FROM ad_bom;
DROP TABLE ad_bom;
ALTER TABLE ad_bom_new RENAME TO ad_bom;

-- Re-seed correct tiers
UPDATE ad_bom SET bom_type = 'UNIT'
WHERE bom_id IN ('UNIT_DUPLEX_STD', 'UNIT_SH_STD', 'UNIT_TBLKTN_STD');

UPDATE ad_bom SET bom_type = 'FLOOR'
WHERE bom_id IN (
    'FLOOR_DX_L1_STD', 'FLOOR_DX_L2_STD',
    'FLOOR_SH_GF_STD', 'FLOOR_TBLKTN_GF_STD',
    'FLOOR_STRUCTURAL', 'TYPICAL_CONDO_FLOOR'
);
-- All 25 SET assemblies remain SET. ROOM=0 and ITEM=0 correct — no such assemblies exist yet.

-- Recreate views dropped above (SQL preserved verbatim from migration_VIEW_CONTRACTS_v15_phases.sql
-- and migration_phase4_product_ref_fk.sql)
CREATE VIEW v_qualified_bom AS
SELECT
    b.bom_id,
    b.bom_name,
    b.bom_type,
    bc.bom_child_id,
    bc.role,
    bc.sequence,
    bc.fit_priority,
    bc.min_space_mm,
    bc.child_name_pattern,
    pd.width            AS width_mm,
    pd.depth            AS depth_mm,
    pd.height           AS height_mm,
    pd.extracted_from
FROM ad_bom b
JOIN ad_bom_child bc
    ON bc.bom_id = b.bom_id
    AND bc.is_active = 1
JOIN ad_product_dim pd
    ON pd.product_id = bc.product_ref
    AND pd.width > 0
    AND pd.depth > 0
    AND pd.height > 0
    AND pd.extracted_from NOT LIKE '%PENDING%'
WHERE b.is_active = 1;

CREATE VIEW v_active_bom_assembly AS
SELECT
    b.bom_id,
    b.bom_name,
    b.bom_type,
    COUNT(bc.bom_child_id)                              AS child_count,
    SUM(CASE WHEN bc.is_active = 1 THEN 1 ELSE 0 END)  AS active_children
FROM ad_bom b
JOIN ad_bom_child bc ON bc.bom_id = b.bom_id
WHERE b.is_active = 1
GROUP BY b.bom_id, b.bom_name, b.bom_type
HAVING child_count = active_children;

CREATE VIEW v_component_leaf AS
SELECT
    cd.id,
    cd.name,
    (SELECT MIN(gm.ifc_class)
     FROM ad_geometry_map gm
     WHERE gm.geometry_hash = cd.geometry_hash) AS ifc_class,
    pd.width                        AS width_mm,
    pd.depth                        AS depth_mm,
    pd.height                       AS height_mm,
    cd.geometry_hash,
    cd.extracted_from               AS geometry_source
FROM component_definitions cd
JOIN ad_product_dim pd
    ON pd.product_id = cd.name
    AND pd.width  > 0
    AND pd.depth  > 0
    AND pd.height > 0
    AND pd.extracted_from NOT LIKE '%PENDING%'
WHERE cd.vertex_count > 8
  AND cd.extracted_from NOT LIKE '%PENDING%'
  AND cd.geometry_hash IS NOT NULL
  AND cd.name NOT IN (SELECT bom_id FROM ad_bom WHERE is_active = 1);

COMMIT;

-- Verify
SELECT 'bom_type distribution (expect FLOOR=6, SET=25, UNIT=3):';
SELECT bom_type, COUNT(*) FROM ad_bom WHERE is_active=1 GROUP BY bom_type ORDER BY bom_type;

SELECT 'UNIT assemblies:';
SELECT bom_id, bom_type FROM ad_bom WHERE bom_type='UNIT' ORDER BY bom_id;

SELECT 'FLOOR assemblies:';
SELECT bom_id, bom_type FROM ad_bom WHERE bom_type='FLOOR' ORDER BY bom_id;
