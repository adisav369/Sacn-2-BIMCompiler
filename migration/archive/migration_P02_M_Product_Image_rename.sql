-- migration_P02_M_Product_Image_rename.sql
-- Phase P0.2: Rename LOD_key → M_Product_Image.
--
-- LOD_key was the working name. M_Product_Image aligns with iDempiere
-- M_Product_Image (product imagery/representation). LOD_Object stays —
-- it's the blob store (the "image file" itself).
--
-- Target: component_library.db
-- Run:    sqlite3 library/component_library.db < migration/migration_P02_M_Product_Image_rename.sql

-- ── 1. Rename table ────────────────────────────────────────────────────

ALTER TABLE LOD_key RENAME TO M_Product_Image;

-- ── 2. Recreate the joined view ───────────────────────────────────────
-- lod_product_geometry was defined over LOD_key; rebuild with new name.

DROP VIEW IF EXISTS lod_product_geometry;

CREATE VIEW lod_product_geometry AS
  SELECT k.M_Product_ID, k.geometry_hash,
         k.up_axis, k.forward_axis, k.attachment_face,
         o.vertices, o.faces, o.normals, o.vertex_count, o.face_count
  FROM M_Product_Image k
  JOIN LOD_Object o ON k.geometry_hash = o.geometry_hash;

-- ── 3. Verify ──────────────────────────────────────────────────────────

SELECT 'M_Product_Image_rows', COUNT(*) FROM M_Product_Image;
SELECT 'lod_product_geometry_rows', COUNT(*) FROM lod_product_geometry;
