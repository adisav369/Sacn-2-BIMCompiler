-- Phase DE-3: Per-instance geometry mapping
-- Adds building_type, storey, ordinal columns to ad_geometry_map
-- so each element INSTANCE gets its own geometry hash (not just per-type).
--
-- Usage:
--   sqlite3 library/component_library.db < migration/migration_phase_DE3_instance_geometry.sql
--   python3 tools/geometry_extractor.py --instance
--
-- Idempotent: safe to re-run (checks column existence before migration).

-- Check if migration already applied (ordinal column exists)
-- SQLite doesn't have IF NOT EXISTS for ALTER TABLE, so we use a temp table approach.

CREATE TABLE IF NOT EXISTS ad_geometry_map_v2 (
    id INTEGER PRIMARY KEY,
    building_type TEXT,
    element_ref TEXT NOT NULL,
    ifc_class TEXT NOT NULL,
    storey TEXT,
    ordinal INTEGER,
    geometry_hash TEXT NOT NULL REFERENCES component_geometries(geometry_hash),
    source TEXT
);

-- Migrate existing type-level entries (roof etc.) — preserve them with NULL building_type/storey/ordinal
INSERT OR IGNORE INTO ad_geometry_map_v2 (id, building_type, element_ref, ifc_class, storey, ordinal, geometry_hash, source)
    SELECT id, NULL, element_ref, ifc_class, NULL, NULL, geometry_hash, source
    FROM ad_geometry_map
    WHERE EXISTS (SELECT 1 FROM ad_geometry_map LIMIT 1);

-- Drop old table and rename
DROP TABLE IF EXISTS ad_geometry_map;
ALTER TABLE ad_geometry_map_v2 RENAME TO ad_geometry_map;

-- Instance-level index: unique per (building, class, storey, ordinal) when ordinal is set
CREATE UNIQUE INDEX IF NOT EXISTS idx_geom_instance
    ON ad_geometry_map(building_type, ifc_class, storey, ordinal)
    WHERE ordinal IS NOT NULL;

-- Type-level index: unique per (element_ref, ifc_class) when ordinal is NULL (backward compat)
CREATE UNIQUE INDEX IF NOT EXISTS idx_geom_type
    ON ad_geometry_map(element_ref, ifc_class)
    WHERE ordinal IS NULL;
