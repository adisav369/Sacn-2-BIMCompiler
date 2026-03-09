-- migration_SH_M_Product_Image.sql
-- Supplements migration_LOD_pair.sql for 8 SH products whose M_Product_IDs
-- were assigned (P0.1-BOM) AFTER the original LOD pair migration ran.
--
-- Target: library/component_library.db
-- Run:    sqlite3 library/component_library.db < migration/migration_SH_M_Product_Image.sql
--
-- 8 SH products have geometry in component_geometries but are missing from
-- M_Product_Image / LOD_Object. 9 others (walls, slabs, roof, curtain wall,
-- window) have no extraction geometry — box geometry is correct for those.
--
-- Uses the same canonical selection CTE as migration_LOD_pair.sql:
--   1. Highest vertex_count (best fidelity)
--   2. Alphabetically first geometry_hash (deterministic tiebreak)
--
-- Idempotent: INSERT OR IGNORE.

-- ============================================================
-- Step 1: Canonical selection CTE for missing SH products
-- ============================================================

CREATE TEMP TABLE _sh_canonical AS
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
      AND p.building_type = 'Ifc4_SampleHouse'
      AND p.M_Product_ID NOT IN (SELECT M_Product_ID FROM M_Product_Image)
)
SELECT M_Product_ID, geometry_hash
FROM product_geom
WHERE rn = 1;

-- ============================================================
-- Step 2: Insert mesh blobs into LOD_Object (unique hashes only)
-- ============================================================

INSERT OR IGNORE INTO LOD_Object (geometry_hash, vertices, faces, normals, vertex_count, face_count)
SELECT DISTINCT
    cg.geometry_hash,
    cg.vertices,
    cg.faces,
    cg.normals,
    cg.vertex_count,
    cg.face_count
FROM _sh_canonical c
JOIN component_geometries cg ON c.geometry_hash = cg.geometry_hash;

-- ============================================================
-- Step 3: Insert product→geometry mapping into M_Product_Image
-- ============================================================

INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash, up_axis, forward_axis, attachment_face)
SELECT c.M_Product_ID, c.geometry_hash,
       COALESCE(cd.up_axis, 'Z'),
       COALESCE(cd.forward_axis, 'Y'),
       COALESCE(cd.attachment_face, 'CENTER')
FROM _sh_canonical c
LEFT JOIN component_definitions cd ON c.geometry_hash = cd.geometry_hash
GROUP BY c.M_Product_ID;

-- Clean up
DROP TABLE _sh_canonical;

-- ============================================================
-- Step 4: Rebuild lod_product_geometry view
-- ============================================================

DROP VIEW IF EXISTS lod_product_geometry;
CREATE VIEW lod_product_geometry AS
SELECT
    k.M_Product_ID,
    k.geometry_hash,
    k.up_axis,
    k.forward_axis,
    k.attachment_face,
    o.vertices,
    o.faces,
    o.normals,
    o.vertex_count,
    o.face_count
FROM M_Product_Image k
JOIN LOD_Object o ON k.geometry_hash = o.geometry_hash;

-- ============================================================
-- Step 5: Verification queries
-- ============================================================

-- Row counts (expect M_Product_Image: 87, LOD_Object: 86)
SELECT 'M_Product_Image rows' AS metric, COUNT(*) AS value FROM M_Product_Image
UNION ALL
SELECT 'LOD_Object rows',                COUNT(*)          FROM LOD_Object
UNION ALL
SELECT 'lod_product_geometry rows',      COUNT(*)          FROM lod_product_geometry;

-- SH products still missing from M_Product_Image (expect 9 — no-geometry products)
SELECT 'still_missing_SH_with_geometry' AS metric, COUNT(*) AS value
FROM (
    SELECT DISTINCT p.M_Product_ID
    FROM ad_element_placement p
    JOIN ad_geometry_map g
        ON  p.building_type = g.building_type
        AND p.ifc_class     = g.ifc_class
        AND p.storey        = g.storey
        AND p.ordinal       = g.ordinal
    JOIN component_geometries cg
        ON g.geometry_hash = cg.geometry_hash
    WHERE p.M_Product_ID IS NOT NULL
      AND p.building_type = 'Ifc4_SampleHouse'
      AND p.M_Product_ID NOT IN (SELECT M_Product_ID FROM M_Product_Image)
);

-- FK integrity: no orphan M_Product_Image rows
SELECT 'orphan_M_Product_Image' AS metric, COUNT(*) AS value
FROM M_Product_Image k
LEFT JOIN LOD_Object o ON k.geometry_hash = o.geometry_hash
WHERE o.geometry_hash IS NULL;
