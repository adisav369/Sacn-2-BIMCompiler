-- CL_002: Fix LOD double-prefix in M_Product_Image for OUTLET_20A and OUTLET_GFCI
-- Implementing S157 Priority 1 — Witness: W-LOD-FIX
--
-- Root cause: CL_001 seeded M_Product_Image from I_Geometry_Map, which stores
-- EXTRACTED hashes with LOD_ prefix. MeshBinder adds another LOD_ → LOD_LOD_.
--
-- OUTLET_20A and OUTLET_GFCI: raw hashes exist in component_geometries.
-- Update M_Product_Image to use raw hashes so MeshBinder can correctly
-- apply scale and produce a single-prefix LOD hash.
--
-- SPRINKLER and SUPPLY_DIFFUSER: handled by MeshBinder fix (strip LOD_ prefix
-- from refGeoHash when computing geoHash — no raw geometry needed).
--
-- Verify after fix: SELECT geometry_hash FROM M_Product_Image
--   WHERE M_Product_ID IN ('OUTLET_20A','OUTLET_GFCI') → must NOT start with LOD_

UPDATE M_Product_Image
SET geometry_hash = '4730c92b5a6a38ae'
WHERE M_Product_ID = 'OUTLET_20A'
  AND geometry_hash LIKE 'LOD_%';

UPDATE M_Product_Image
SET geometry_hash = '23c614fecbd992b8'
WHERE M_Product_ID = 'OUTLET_GFCI'
  AND geometry_hash LIKE 'LOD_%';
