-- Phase BOM-2c: Map DX FIXTURE_SINK element_refs to real sink geometry.
--
-- Root cause: FIXTURE_SINK elements (IfcFurnishingElement_994/1017/1018/1021/1022)
-- are relational metadata entries in ad_element_rule. They have no corresponding row
-- in ad_geometry_map, so MeshBinder.resolveGeometryByRef() returns null → bindParametric()
-- fallback writes a GEO_ 8-vertex bounding box (fails X1 vertex_count > 8 gate).
--
-- Fix: add type-level rows in ad_geometry_map for each element_ref.
-- geometry_hash = 13ffa0740585876b = "M_Sink - Island - Single:455 mmx455 mm - Private"
-- → 947 vertices in component_geometries, matches FIXTURE_SINK dims (0.5×0.45×0.2m).
-- ordinal = NULL so resolveGeometryByRef() (WHERE ordinal IS NULL) picks them up.
--
-- [EXTRACTED: MeshBinder.bind() → ComponentLibrary.resolveGeometryByRef() → ad_geometry_map]
-- [EXTRACTED: ad_product_dim FIXTURE_SINK = 0.5m×0.45m×0.2m]

INSERT OR IGNORE INTO ad_geometry_map
    (building_type, element_ref, ifc_class, storey, ordinal, geometry_hash, source, provenance)
VALUES
    ('Ifc2x3_Duplex','IfcFurnishingElement_994', 'IfcFurnishingElement','Ground',NULL,'13ffa0740585876b','ad_element_rule','LIBRARY'),
    ('Ifc2x3_Duplex','IfcFurnishingElement_1017','IfcFurnishingElement','Upper', NULL,'13ffa0740585876b','ad_element_rule','LIBRARY'),
    ('Ifc2x3_Duplex','IfcFurnishingElement_1018','IfcFurnishingElement','Upper', NULL,'13ffa0740585876b','ad_element_rule','LIBRARY'),
    ('Ifc2x3_Duplex','IfcFurnishingElement_1021','IfcFurnishingElement','Upper', NULL,'13ffa0740585876b','ad_element_rule','LIBRARY'),
    ('Ifc2x3_Duplex','IfcFurnishingElement_1022','IfcFurnishingElement','Upper', NULL,'13ffa0740585876b','ad_element_rule','LIBRARY');

-- Verification:
-- sqlite3 library/component_library.db \
--   "SELECT element_ref, geometry_hash FROM ad_geometry_map
--    WHERE building_type='Ifc2x3_Duplex' AND ifc_class='IfcFurnishingElement'
--    AND element_ref LIKE 'IfcFurnishingElement_%' AND ordinal IS NULL"
-- Expected: 5 rows, all geometry_hash='13ffa0740585876b'
