-- migration_lod_to_ad_views.sql
-- Backward-compatibility views for lod_* → ad_* table renames in component_library.db
-- The tables were renamed (lod_geometry_map → ad_geometry_map, etc.) but 20+ Java files
-- still reference the old names. Creating views avoids a mass rename.
-- Target: component_library.db

CREATE VIEW IF NOT EXISTS lod_geometry_map AS SELECT * FROM ad_geometry_map;
CREATE VIEW IF NOT EXISTS lod_element_placement AS SELECT * FROM ad_element_placement;
CREATE VIEW IF NOT EXISTS lod_parametric_mesh AS SELECT * FROM ad_parametric_mesh;
CREATE VIEW IF NOT EXISTS lod_parametric_mesh_param AS SELECT * FROM ad_parametric_mesh_param;
