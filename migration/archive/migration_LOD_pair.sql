-- migration_LOD_pair.sql
-- Creates LOD_key + LOD_Object pair tables in component_library.db
-- LOD_key:    M_Product_ID -> geometry_hash  (one row per product, ~71 DX rows)
-- LOD_Object: geometry_hash -> mesh blob     (one row per unique canonical mesh, ~70 rows)
--
-- Linkage chain:
--   ad_element_placement(M_Product_ID, building_type, ifc_class, storey, ordinal)
--     -> ad_geometry_map(building_type, ifc_class, storey, ordinal, geometry_hash)
--       -> component_geometries(geometry_hash, vertices, faces, normals, vertex_count, face_count)
--
-- Canonical selection per M_Product_ID:
--   1. Highest vertex_count (best fidelity)
--   2. Alphabetically first geometry_hash (deterministic tiebreak)
--
-- Idempotent: DROP IF EXISTS before CREATE.

-- ============================================================
-- Step 1: DDL
-- ============================================================

-- LOD_Object must exist before LOD_key (FK constraint)
DROP VIEW  IF EXISTS lod_product_geometry;
DROP TABLE IF EXISTS LOD_key;
DROP TABLE IF EXISTS LOD_Object;

CREATE TABLE LOD_Object (
    geometry_hash TEXT PRIMARY KEY,
    vertices      BLOB    NOT NULL,
    faces         BLOB    NOT NULL,
    normals       BLOB,
    vertex_count  INTEGER NOT NULL,
    face_count    INTEGER NOT NULL
);

CREATE TABLE LOD_key (
    M_Product_ID    TEXT PRIMARY KEY,
    geometry_hash   TEXT NOT NULL,
    up_axis         TEXT NOT NULL DEFAULT 'Z',
    forward_axis    TEXT NOT NULL DEFAULT 'Y',
    attachment_face TEXT NOT NULL DEFAULT 'CENTER',
    FOREIGN KEY (geometry_hash) REFERENCES LOD_Object(geometry_hash)
);

-- ============================================================
-- Step 2: Populate LOD_key via canonical selection CTE
-- ============================================================

-- Temporary table to hold the canonical mapping (needed because we insert
-- into LOD_Object first, then LOD_key, from the same CTE result).
CREATE TEMP TABLE _canonical AS
WITH product_geom AS (
    SELECT
        p.M_Product_ID,
        g.geometry_hash,
        cg.vertex_count,
        ROW_NUMBER() OVER (
            PARTITION BY p.M_Product_ID
            ORDER BY cg.vertex_count DESC, g.geometry_hash ASC
        ) AS rn
    FROM ad_element_placement p
    JOIN ad_geometry_map g
        ON  p.building_type = g.building_type
        AND p.ifc_class     = g.ifc_class
        AND p.storey        = g.storey
        AND p.ordinal       = g.ordinal
    JOIN component_geometries cg
        ON g.geometry_hash = cg.geometry_hash
    WHERE p.M_Product_ID IS NOT NULL
)
SELECT M_Product_ID, geometry_hash
FROM product_geom
WHERE rn = 1;

-- ============================================================
-- Step 3: Populate LOD_Object (unique meshes only)
-- ============================================================

INSERT INTO LOD_Object (geometry_hash, vertices, faces, normals, vertex_count, face_count)
SELECT DISTINCT
    cg.geometry_hash,
    cg.vertices,
    cg.faces,
    cg.normals,
    cg.vertex_count,
    cg.face_count
FROM _canonical c
JOIN component_geometries cg ON c.geometry_hash = cg.geometry_hash;

-- ============================================================
-- Step 4: Populate LOD_key
-- ============================================================

INSERT INTO LOD_key (M_Product_ID, geometry_hash, up_axis, forward_axis, attachment_face)
SELECT c.M_Product_ID, c.geometry_hash,
       COALESCE(cd.up_axis, 'Z'),
       COALESCE(cd.forward_axis, 'Y'),
       COALESCE(cd.attachment_face, 'CENTER')
FROM _canonical c
LEFT JOIN component_definitions cd ON c.geometry_hash = cd.geometry_hash
GROUP BY c.M_Product_ID;

-- Clean up temp table
DROP TABLE _canonical;

-- ============================================================
-- Step 5: Read-only view (project convention: lod_ prefix)
-- ============================================================

CREATE VIEW lod_product_geometry AS
SELECT
    k.M_Product_ID,
    k.up_axis,
    k.forward_axis,
    k.attachment_face,
    o.geometry_hash,
    o.vertices,
    o.faces,
    o.normals,
    o.vertex_count,
    o.face_count
FROM LOD_key k
JOIN LOD_Object o ON k.geometry_hash = o.geometry_hash;

-- ============================================================
-- Step 6: Verification queries (SELECT only, no side effects)
-- ============================================================

-- Row counts
SELECT 'LOD_key rows'       AS metric, COUNT(*) AS value FROM LOD_key
UNION ALL
SELECT 'LOD_Object rows',            COUNT(*)          FROM LOD_Object
UNION ALL
SELECT 'lod_product_geometry rows',  COUNT(*)          FROM lod_product_geometry;

-- FK integrity: every LOD_key.geometry_hash exists in LOD_Object
SELECT 'orphan LOD_key rows' AS metric, COUNT(*) AS value
FROM LOD_key k
LEFT JOIN LOD_Object o ON k.geometry_hash = o.geometry_hash
WHERE o.geometry_hash IS NULL;

-- Every LOD_key.M_Product_ID exists in ad_element_placement
SELECT 'orphan M_Product_ID rows' AS metric, COUNT(*) AS value
FROM LOD_key k
WHERE k.M_Product_ID NOT IN (
    SELECT DISTINCT M_Product_ID FROM ad_element_placement WHERE M_Product_ID IS NOT NULL
);

-- Products with geometry that were NOT mapped (should be the 8 missing ones)
SELECT 'unmapped M_Product_IDs' AS metric, COUNT(DISTINCT M_Product_ID) AS value
FROM ad_element_placement
WHERE M_Product_ID IS NOT NULL
  AND M_Product_ID NOT IN (SELECT M_Product_ID FROM LOD_key);
