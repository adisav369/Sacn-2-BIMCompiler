-- Phase RM-1: Create 5 relational tables for computed placement
-- These tables express relationships (grid → room → wall → element)
-- that the RelationalResolver will use to compute coordinates.
-- The existing ad_element_placement remains untouched as test oracle.
--
-- Schema: this script (idempotent DDL — CREATE IF NOT EXISTS)
-- Data:   DAGCompiler/python/relational_extractor.py --building all (idempotent DML — DELETE + re-INSERT)
-- Validate: DAGCompiler/python/relational_shadow_validator.py --building all

-- 1. Building Grid — structural grid lines per building
CREATE TABLE IF NOT EXISTS ad_building_grid (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type TEXT NOT NULL,       -- 'Ifc4_SampleHouse', 'Ifc2x3_Duplex'
    axis          TEXT NOT NULL,       -- 'X' or 'Y'
    grid_label    TEXT NOT NULL,       -- 'A', 'B', '1', '2' etc.
    position_mm   REAL NOT NULL,       -- absolute position in mm from building origin
    is_active     INTEGER DEFAULT 1,
    UNIQUE(building_type, axis, grid_label)
);

-- 2. Room Boundary — rooms mapped to grid cells
CREATE TABLE IF NOT EXISTS ad_room_boundary (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type TEXT NOT NULL,
    storey        TEXT NOT NULL,       -- 'Ground Floor', 'Level 1'
    room_name     TEXT NOT NULL,       -- descriptive name
    room_type     TEXT NOT NULL,       -- → ad_space_type: 'BEDROOM', 'LIVING'
    grid_min_x    TEXT NOT NULL,       -- grid label for min X
    grid_max_x    TEXT NOT NULL,       -- grid label for max X
    grid_min_y    TEXT NOT NULL,       -- grid label for min Y
    grid_max_y    TEXT NOT NULL,       -- grid label for max Y
    min_x_mm      REAL,               -- exact min X in mm (for lossless round-trip)
    max_x_mm      REAL,               -- exact max X in mm
    min_y_mm      REAL,               -- exact min Y in mm
    max_y_mm      REAL,               -- exact max Y in mm
    z_offset_mm   REAL DEFAULT 0,     -- floor level offset from storey base
    is_active     INTEGER DEFAULT 1,
    UNIQUE(building_type, storey, room_name)
);

-- 3. Wall Face — room boundary faces with wall type + adjacency
CREATE TABLE IF NOT EXISTS ad_wall_face (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type TEXT NOT NULL,
    storey        TEXT NOT NULL,
    room_name     TEXT NOT NULL,       -- owner room
    face          TEXT NOT NULL,       -- 'NORTH', 'SOUTH', 'EAST', 'WEST'
    wall_type_id  TEXT NOT NULL,       -- → ad_wall_type: 'EXTERIOR_BRICK', 'INTERIOR_PARTITION_92'
    is_exterior   INTEGER NOT NULL,    -- 1 = exterior envelope, 0 = interior
    adjacent_room TEXT,                -- NULL if exterior, else neighbour room name
    is_active     INTEGER DEFAULT 1,
    UNIQUE(building_type, storey, room_name, face)
);

-- 4. Element Rule — placement rules (host + position + family)
CREATE TABLE IF NOT EXISTS ad_element_rule (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type   TEXT NOT NULL,
    storey          TEXT NOT NULL,
    element_ref     TEXT NOT NULL,       -- unique element name
    ifc_class       TEXT NOT NULL,       -- 'IfcDoor', 'IfcWindow', etc.
    discipline      TEXT DEFAULT 'ARC',

    -- Host reference
    host_type       TEXT NOT NULL,       -- 'WALL', 'ROOM', 'GRID', 'BUILDING', 'SLAB'
    host_ref        TEXT NOT NULL,       -- wall face key, room name, grid label, etc.

    -- Position on host
    position_rule   TEXT NOT NULL,       -- 'CENTER', 'FRACTION', 'OFFSET', 'GRID_INTERSECTION', 'SPACING', 'INSET', 'ABSOLUTE'
    position_value  REAL,                -- primary: x-fraction 0-1, offset mm, center_x_mm (ABSOLUTE)
    position_value_2 REAL,               -- secondary: y-fraction 0-1, perp offset mm, center_y_mm (ABSOLUTE)
    height_mm       REAL,                -- height above host floor (sill, outlet height)

    -- Element sizing
    family_ref      TEXT,                -- → ad_opening_family or component name
    width_mm        REAL,
    height_extent_mm REAL,
    depth_mm        REAL,

    -- Orientation
    orientation     TEXT,                -- 'ALONG_HOST', 'PERPENDICULAR', 'NS', 'EW'

    -- Geometry + material (pass-through from ad_element_placement)
    geometry_hash   TEXT,
    material_name   TEXT,
    material_rgba   TEXT,

    is_active       INTEGER DEFAULT 1,
    UNIQUE(building_type, storey, element_ref)
);

-- 5. Element Dependency — parent-child cascade chain
CREATE TABLE IF NOT EXISTS ad_element_dependency (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type TEXT NOT NULL,
    element_ref   TEXT NOT NULL,       -- child element
    parent_ref    TEXT NOT NULL,       -- parent (wall, room, grid line)
    relation      TEXT NOT NULL,       -- 'HOSTED_ON', 'CONTAINED_IN', 'CONNECTS_TO', 'SUPPORTS'
    cascade_rule  TEXT DEFAULT 'MOVE', -- 'MOVE', 'RECOMPUTE', 'NONE'
    is_active     INTEGER DEFAULT 1,
    UNIQUE(building_type, element_ref, parent_ref)
);
