-- DAGCompiler Output DB Schema
-- Unified CREATE script — all columns the compiler writes.
-- Used by BuildingWriter.java to create output DBs.
--
-- Usage:
--   sqlite3 new_output.db < DAGCompiler/python/output_schema.sql

CREATE TABLE IF NOT EXISTS spatial_structure (
    guid TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    name TEXT,
    parent_guid TEXT,
    object_type TEXT,
    predefined_type TEXT
);

CREATE TABLE IF NOT EXISTS elements_meta (
    id INTEGER PRIMARY KEY,
    guid TEXT UNIQUE NOT NULL,
    discipline TEXT NOT NULL,
    ifc_class TEXT NOT NULL,
    element_name TEXT,
    element_type TEXT,
    storey TEXT,
    fire_rating_hr REAL,
    material_name TEXT,
    material_rgba TEXT,
    element_ref TEXT
);

CREATE VIRTUAL TABLE IF NOT EXISTS elements_rtree USING rtree(
    id, minX, maxX, minY, maxY, minZ, maxZ
);

CREATE TABLE IF NOT EXISTS base_geometries (
    geometry_hash TEXT PRIMARY KEY,
    vertices BLOB NOT NULL,
    faces BLOB NOT NULL,
    vertex_count INTEGER NOT NULL,
    face_count INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS element_instances (
    guid TEXT PRIMARY KEY,
    geometry_hash TEXT,
    FOREIGN KEY (geometry_hash) REFERENCES base_geometries(geometry_hash)
);

CREATE TABLE IF NOT EXISTS assembly_components (
    parent_guid TEXT,
    child_guid TEXT,
    role TEXT,
    sequence INTEGER,
    PRIMARY KEY (parent_guid, child_guid)
);

CREATE TABLE IF NOT EXISTS mep_systems (
    system_id TEXT PRIMARY KEY,
    system_type TEXT NOT NULL,
    system_name TEXT
);

CREATE TABLE IF NOT EXISTS system_nodes (
    node_id TEXT PRIMARY KEY,
    system_id TEXT,
    name TEXT,
    node_type TEXT,
    FOREIGN KEY (system_id) REFERENCES mep_systems(system_id)
);

CREATE TABLE IF NOT EXISTS system_edges (
    from_node_id TEXT,
    to_node_id TEXT,
    edge_type TEXT,
    PRIMARY KEY (from_node_id, to_node_id),
    FOREIGN KEY (from_node_id) REFERENCES system_nodes(node_id),
    FOREIGN KEY (to_node_id) REFERENCES system_nodes(node_id)
);

CREATE TABLE IF NOT EXISTS simple_qto (
    element_guid TEXT,
    quantity_name TEXT,
    quantity_value REAL,
    unit TEXT,
    PRIMARY KEY (element_guid, quantity_name)
);

-- Rich material library: one row per unique surface style (instanced, not per-element)
CREATE TABLE IF NOT EXISTS surface_styles (
    style_name TEXT PRIMARY KEY,
    surface_r REAL, surface_g REAL, surface_b REAL,
    transparency REAL DEFAULT 0.0,
    specular_r REAL, specular_g REAL, specular_b REAL,
    specular_ratio REAL,
    specular_exponent REAL,
    reflectance_method TEXT DEFAULT 'NOTDEFINED',
    side TEXT DEFAULT 'BOTH',
    source TEXT
);

-- Material layer composition (wall = Brick > Air > Insulation > Block > Plaster)
CREATE TABLE IF NOT EXISTS material_layers (
    layer_set_name TEXT NOT NULL,
    sequence INTEGER NOT NULL,
    material_name TEXT,
    thickness_m REAL,
    is_ventilated INTEGER DEFAULT 0,
    PRIMARY KEY (layer_set_name, sequence)
);
