-- migration_BUILDING_bom_type_rename.sql
-- Rename bom_type 'UNIT' → 'BUILDING' (broader than housing: bridge, landscape, etc.)
-- Also rename bom_id/bom_level UNIT_ → BUILDING_ for consistency.
--
-- SQLite cannot ALTER CHECK constraints, so we recreate m_bom with the new constraint.
-- Safe: BOM.db is rebuilt from create_ad_*.py + migrations; this is append-only.

PRAGMA foreign_keys=OFF;

-- Step 1: Recreate m_bom with updated CHECK constraint
CREATE TABLE m_bom_new (
    bom_id            TEXT PRIMARY KEY,
    bom_name          TEXT NOT NULL,
    description       TEXT,
    target_ifc_class  TEXT DEFAULT 'IfcElementAssembly',
    group_by          TEXT NOT NULL,
    is_active         INTEGER DEFAULT 1,
    bom_level         TEXT DEFAULT 'SET',
    bom_type          TEXT NOT NULL DEFAULT 'SET'
        CHECK(bom_type IN ('BUILDING', 'FLOOR', 'ROOM', 'SET', 'ITEM')),
    bom_category      TEXT DEFAULT NULL,
    doc_sub_type      TEXT DEFAULT NULL,
    seq_no            INTEGER DEFAULT 10,
    origin_x          REAL DEFAULT 0.0,
    origin_y          REAL DEFAULT 0.0,
    origin_z          REAL DEFAULT 0.0,
    entity_type       TEXT DEFAULT 'D',
    aabb_width_mm     INTEGER DEFAULT 0,
    aabb_depth_mm     INTEGER DEFAULT 0,
    aabb_height_mm    INTEGER DEFAULT 0
);

-- Step 2: Copy data, transforming UNIT → BUILDING in bom_type, bom_level, and bom_id
INSERT INTO m_bom_new
SELECT
    CASE WHEN bom_id LIKE 'UNIT_%' THEN 'BUILDING_' || SUBSTR(bom_id, 6) ELSE bom_id END,
    bom_name,
    description,
    target_ifc_class,
    group_by,
    is_active,
    CASE WHEN bom_level = 'UNIT' THEN 'BUILDING' ELSE bom_level END,
    CASE WHEN bom_type  = 'UNIT' THEN 'BUILDING' ELSE bom_type  END,
    bom_category,
    doc_sub_type,
    seq_no,
    origin_x,
    origin_y,
    origin_z,
    entity_type,
    aabb_width_mm,
    aabb_depth_mm,
    aabb_height_mm
FROM m_bom;

-- Step 3: Drop old, rename new
DROP TABLE m_bom;
ALTER TABLE m_bom_new RENAME TO m_bom;

-- Step 4: Update m_bom_line references (parent FK)
UPDATE m_bom_line SET bom_id = 'BUILDING_SH_STD'     WHERE bom_id = 'UNIT_SH_STD';
UPDATE m_bom_line SET bom_id = 'BUILDING_DX_STD'     WHERE bom_id = 'UNIT_DX_STD';
UPDATE m_bom_line SET bom_id = 'BUILDING_TBLKTN_STD' WHERE bom_id = 'UNIT_TBLKTN_STD';

-- Step 5: Update m_bom_line child references (if any point to UNIT_ BOMs)
UPDATE m_bom_line SET child_product_id = 'BUILDING_SH_STD'     WHERE child_product_id = 'UNIT_SH_STD';
UPDATE m_bom_line SET child_product_id = 'BUILDING_DX_STD'     WHERE child_product_id = 'UNIT_DX_STD';
UPDATE m_bom_line SET child_product_id = 'BUILDING_TBLKTN_STD' WHERE child_product_id = 'UNIT_TBLKTN_STD';

PRAGMA foreign_keys=ON;

-- Verify
SELECT 'BUILDING BOMs: ' || COUNT(*) FROM m_bom WHERE bom_type = 'BUILDING';
SELECT 'Remaining UNIT refs: ' || COUNT(*) FROM m_bom WHERE bom_type = 'UNIT';
SELECT 'UNIT_ bom_ids: ' || COUNT(*) FROM m_bom WHERE bom_id LIKE 'UNIT_%';
