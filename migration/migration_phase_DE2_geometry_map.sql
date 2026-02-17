-- Phase DE-2: Reference Geometry Extraction
-- Creates ad_geometry_map table for mapping element references to extracted meshes.
-- Data is populated by: python3 tools/geometry_extractor.py
--
-- Usage:
--   sqlite3 library/component_library.db < migration/migration_phase_DE2_geometry_map.sql
--   python3 tools/geometry_extractor.py --ifc-class IfcRoof

-- Schema: ad_geometry_map
CREATE TABLE IF NOT EXISTS ad_geometry_map (
    id INTEGER PRIMARY KEY,
    element_ref TEXT NOT NULL,
    ifc_class TEXT NOT NULL,
    geometry_hash TEXT NOT NULL REFERENCES component_geometries(geometry_hash),
    source TEXT,
    UNIQUE(element_ref, ifc_class)
);

-- Ensure IfcRoof component type exists
INSERT OR IGNORE INTO component_types (ifc_class, category, discipline)
VALUES ('IfcRoof', 'ROOF', 'ARC');
