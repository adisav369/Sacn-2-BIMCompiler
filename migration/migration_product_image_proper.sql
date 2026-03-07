-- migration_product_image_proper.sql
-- Rewrites M_Product_Image population to NOT depend on I_Element_Extraction.
--
-- ROOT CAUSE: migration_LOD_pair.sql joined I_Element_Extraction to I_Geometry_Map
-- on (building_type, ifc_class, storey, ordinal). But storeys and ordinals are
-- MISALIGNED between the two tables for 9 SH structural products. 12 template
-- products were never in I_Element_Extraction at all.
--
-- PROPER FIX: Two independent population paths:
--   Path 1: element_ref matching via I_Geometry_Map (SH structural products)
--   Path 2: name matching via component_definitions (template products)
--
-- Neither path uses I_Element_Extraction.
--
-- Target: library/component_library.db
-- Prereq: m_bom_line in BOM.db must have child_product_id + element_ref populated
-- Run:    sqlite3 library/component_library.db < migration/migration_product_image_proper.sql
--
-- Idempotent: INSERT OR IGNORE.

-- ============================================================
-- Path 1: SH structural products via element_ref → I_Geometry_Map
-- Products that have element_ref in m_bom_line (backfilled P0.2).
-- Join I_Geometry_Map on element_ref (not ordinal) to find geometry.
-- ============================================================

ATTACH 'library/BOM.db' AS bom;

CREATE TEMP TABLE _path1_canonical AS
WITH product_geom AS (
    SELECT
        bl.child_product_id AS M_Product_ID,
        g.geometry_hash,
        cg.vertex_count,
        ROW_NUMBER() OVER (
            PARTITION BY bl.child_product_id
            ORDER BY cg.vertex_count DESC, g.geometry_hash ASC
        ) AS rn
    FROM bom.m_bom_line bl
    JOIN bom.m_bom b ON bl.bom_id = b.bom_id
    JOIN bom.C_DocType dt ON b.doc_sub_type = dt.DocSubType
    JOIN I_Geometry_Map g
        ON  dt.ProjectName = g.building_type
        AND bl.element_ref = g.element_ref
    JOIN component_geometries cg
        ON g.geometry_hash = cg.geometry_hash
    WHERE bl.is_active = 1
      AND b.is_active = 1
      AND bl.component_type = 'BUY'
      AND bl.element_ref IS NOT NULL
      AND bl.child_product_id NOT IN (SELECT M_Product_ID FROM M_Product_Image)
)
SELECT M_Product_ID, geometry_hash
FROM product_geom
WHERE rn = 1;

-- Insert mesh blobs into LOD_Object
INSERT OR IGNORE INTO LOD_Object (geometry_hash, vertices, faces, normals, vertex_count, face_count)
SELECT DISTINCT
    cg.geometry_hash, cg.vertices, cg.faces, cg.normals, cg.vertex_count, cg.face_count
FROM _path1_canonical c
JOIN component_geometries cg ON c.geometry_hash = cg.geometry_hash;

-- Insert product→geometry mapping
INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash, up_axis, forward_axis, attachment_face)
SELECT c.M_Product_ID, c.geometry_hash,
       COALESCE(cd.up_axis, 'Z'),
       COALESCE(cd.forward_axis, 'Y'),
       COALESCE(cd.attachment_face, 'CENTER')
FROM _path1_canonical c
LEFT JOIN component_definitions cd ON c.geometry_hash = cd.geometry_hash
GROUP BY c.M_Product_ID;

SELECT 'Path 1 (element_ref) added' AS step, COUNT(*) AS rows FROM _path1_canonical;
DROP TABLE _path1_canonical;

-- ============================================================
-- Path 2: Template products via name → component_definitions
-- Products whose product_id matches a component_definitions.name.
-- No extraction needed — direct name-to-geometry mapping.
-- ============================================================

CREATE TEMP TABLE _path2_canonical AS
WITH product_geom AS (
    SELECT
        p.product_id AS M_Product_ID,
        cd.geometry_hash,
        cg.vertex_count,
        ROW_NUMBER() OVER (
            PARTITION BY p.product_id
            ORDER BY cg.vertex_count DESC, cd.geometry_hash ASC
        ) AS rn
    FROM bom.M_Product p
    JOIN component_definitions cd ON cd.name = p.product_id
    JOIN component_geometries cg ON cd.geometry_hash = cg.geometry_hash
    WHERE p.is_active = 1
      AND p.product_id NOT IN (SELECT M_Product_ID FROM M_Product_Image)
)
SELECT M_Product_ID, geometry_hash
FROM product_geom
WHERE rn = 1;

-- Insert mesh blobs into LOD_Object
INSERT OR IGNORE INTO LOD_Object (geometry_hash, vertices, faces, normals, vertex_count, face_count)
SELECT DISTINCT
    cg.geometry_hash, cg.vertices, cg.faces, cg.normals, cg.vertex_count, cg.face_count
FROM _path2_canonical c
JOIN component_geometries cg ON c.geometry_hash = cg.geometry_hash;

-- Insert product→geometry mapping
INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash, up_axis, forward_axis, attachment_face)
SELECT c.M_Product_ID, c.geometry_hash,
       COALESCE(cd.up_axis, 'Z'),
       COALESCE(cd.forward_axis, 'Y'),
       COALESCE(cd.attachment_face, 'CENTER')
FROM _path2_canonical c
LEFT JOIN component_definitions cd ON c.geometry_hash = cd.geometry_hash
GROUP BY c.M_Product_ID;

SELECT 'Path 2 (name match) added' AS step, COUNT(*) AS rows FROM _path2_canonical;
DROP TABLE _path2_canonical;

DETACH bom;

-- ============================================================
-- Rebuild view
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
-- Verification
-- ============================================================

SELECT 'M_Product_Image rows' AS metric, COUNT(*) AS value FROM M_Product_Image
UNION ALL
SELECT 'LOD_Object rows',                COUNT(*)          FROM LOD_Object
UNION ALL
SELECT 'lod_product_geometry rows',      COUNT(*)          FROM lod_product_geometry;

-- FK integrity: no orphan M_Product_Image rows
SELECT 'orphan_M_Product_Image' AS metric, COUNT(*) AS value
FROM M_Product_Image k
LEFT JOIN LOD_Object o ON k.geometry_hash = o.geometry_hash
WHERE o.geometry_hash IS NULL;
