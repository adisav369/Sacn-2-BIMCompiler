CREATE TABLE component_types (
            id INTEGER PRIMARY KEY,
            ifc_class TEXT NOT NULL,
            category TEXT NOT NULL,
            discipline TEXT NOT NULL, product_category TEXT,
            UNIQUE(ifc_class, category)
        );
CREATE TABLE component_definitions (
            id INTEGER PRIMARY KEY,
            type_id INTEGER REFERENCES component_types(id),
            name TEXT NOT NULL,
            geometry_hash TEXT NOT NULL,

            -- Local geometry bounds (in local coordinates)
            local_min_x REAL, local_max_x REAL,
            local_min_y REAL, local_max_y REAL,
            local_min_z REAL, local_max_z REAL,

            -- Attachment convention
            attachment_face TEXT NOT NULL,  -- TOP, BOTTOM, SIDE, CENTER
            up_axis TEXT DEFAULT 'Z',       -- Which local axis points up
            forward_axis TEXT DEFAULT 'Y',  -- Which local axis is forward

            -- Orientation
            orientation TEXT,               -- PENDANT, UPRIGHT, WALL_MOUNT
            default_rotation REAL DEFAULT 0,

            -- Geometry stats
            vertex_count INTEGER,
            face_count INTEGER, instance_count INTEGER DEFAULT 1,

            UNIQUE(name, geometry_hash)
        );
CREATE TABLE component_geometries (
            geometry_hash TEXT PRIMARY KEY,
            vertices BLOB NOT NULL,
            faces BLOB NOT NULL,
            normals BLOB,
            vertex_count INTEGER NOT NULL,
            face_count INTEGER NOT NULL
        );
CREATE TABLE placement_rules (
            id INTEGER PRIMARY KEY,
            component_id INTEGER REFERENCES component_definitions(id),
            host_type TEXT,           -- CEILING, WALL, FLOOR
            offset_from_host REAL,    -- Distance from host surface
            grid_spacing REAL,        -- Standard spacing (e.g., 4.6m for sprinklers)
            clearance_radius REAL     -- Min distance from other objects
        );
CREATE TABLE ad_product_dim (
        product_id        TEXT PRIMARY KEY,    -- 'DOOR_D1', 'UNIT_2BR_A', 'FIXTURE_TOILET'
        product_type      TEXT NOT NULL,       -- DOOR, WINDOW, UNIT, FIXTURE, FURNITURE

        -- Bounding box (LEGO brick size)
        width             REAL NOT NULL,       -- X dimension (meters)
        depth             REAL NOT NULL,       -- Y dimension (meters)
        height            REAL NOT NULL,       -- Z dimension (meters)

        -- Minimum clearances (LEGO connection rules)
        clear_front       REAL DEFAULT 0,      -- Required clearance in front
        clear_back        REAL DEFAULT 0,
        clear_left        REAL DEFAULT 0,
        clear_right       REAL DEFAULT 0,
        clear_above       REAL DEFAULT 0,
        clear_below       REAL DEFAULT 0,

        -- Fitting rules
        fits_in           TEXT,                -- JSON: what spaces this fits in ["BEDROOM","BATHROOM"]
        requires_host     TEXT,                -- Host type required: WALL, CEILING, FLOOR, null
        host_min_width    REAL,                -- Minimum host dimension
        host_min_height   REAL,

        -- Quantity rules (for auto-calculation)
        qty_per_area      REAL,                -- Quantity per m² (e.g., 1 light per 10m²)
        qty_per_room      INTEGER,             -- Quantity per room (e.g., 1 toilet per bathroom)
        qty_per_person    REAL,                -- Quantity per occupant
        max_spacing       REAL,                -- Maximum spacing between (e.g., outlets every 3.6m)

        -- Connection points (LEGO studs)
        conn_points       TEXT,                -- JSON: [{"face":"BACK","type":"PLUMB"},...]

        code_ref          TEXT,
        is_active         INTEGER DEFAULT 1
    );
CREATE TABLE ad_fire_compartment (
        compartment_id    TEXT PRIMARY KEY,
        occupancy_group   TEXT NOT NULL,
        space_type        TEXT,                -- NULL = any space in group
        max_area_m2       REAL NOT NULL,
        max_area_sprink_m2 REAL,               -- Max area if sprinklered (bonus)
        min_fire_rating_hr REAL NOT NULL,
        requires_sprinkler INTEGER DEFAULT 0,
        requires_detection INTEGER DEFAULT 0,
        requires_smoke_ctrl INTEGER DEFAULT 0,
        code_id           TEXT,
        clause            TEXT,
        jurisdiction      TEXT DEFAULT 'INTERNATIONAL',
        is_active         INTEGER DEFAULT 1
    );
CREATE TABLE ad_check_applicability (
        check_id          TEXT PRIMARY KEY,
        check_name        TEXT NOT NULL,
        check_class       TEXT NOT NULL,         -- Java class name (e.g., 'StairwellCheck')
        category          TEXT NOT NULL,         -- STRUCTURAL, MEP, EGRESS, ENVELOPE, GEOMETRY

        -- Applicability rules (NULL = applies to all)
        min_storeys       INTEGER,               -- Minimum storeys for check to apply
        max_storeys       INTEGER,               -- Maximum storeys (NULL = no limit)
        occupancy_groups  TEXT,                  -- Comma-separated: 'R,A,B' or NULL for all
        min_area_m2       REAL,                  -- Minimum building area for check
        requires_element  TEXT,                  -- IFC type required (e.g., 'IfcStair')

        -- Skip rules
        skip_condition    TEXT,                  -- SQL-like condition for skip
        skip_reason       TEXT,                  -- Reason shown when skipped

        -- Behavior
        severity          TEXT DEFAULT 'ERROR',  -- ERROR, WARNING, INFO
        enabled           INTEGER DEFAULT 1,
        sort_order        INTEGER DEFAULT 100,   -- Execution order

        -- Traceability
        code_id           TEXT,
        clause            TEXT,
        description       TEXT,

        is_active         INTEGER DEFAULT 1
    );
CREATE TABLE ad_check_threshold (
        threshold_id      TEXT PRIMARY KEY,
        check_id          TEXT NOT NULL,         -- FK to ad_check_applicability
        threshold_name    TEXT NOT NULL,         -- e.g., 'max_travel_distance', 'min_stair_width'

        -- Scope (which buildings this threshold applies to)
        occupancy_group   TEXT,                  -- R, A, B, E, etc. (NULL = all)
        storey_type       TEXT,                  -- SINGLE, MULTI, HIGH_RISE (NULL = all)
        building_class    TEXT,                  -- RESIDENTIAL, PUBLIC, COMMERCIAL (NULL = all)

        -- Values
        threshold_value   REAL NOT NULL,
        threshold_unit    TEXT,                  -- 'm', 'mm', 'm2', 'hr', '%'
        sprinkler_bonus   REAL DEFAULT 1.0,      -- Multiplier when sprinklered (e.g., 1.25)

        -- Traceability
        code_id           TEXT,
        clause            TEXT,
        description       TEXT,
        jurisdiction      TEXT DEFAULT 'INTERNATIONAL',

        is_active         INTEGER DEFAULT 1,

        FOREIGN KEY (check_id) REFERENCES ad_check_applicability(check_id)
    );
CREATE TABLE ad_opening_family (
    family_id         TEXT PRIMARY KEY,
    family_name       TEXT NOT NULL,
    opening_type      TEXT NOT NULL,       -- DOOR or WINDOW
    ifc_class         TEXT NOT NULL,       -- IfcDoor or IfcWindow
    default_width_mm  INTEGER NOT NULL,
    default_height_mm INTEGER NOT NULL,
    is_fire_rated     INTEGER DEFAULT 0,
    description       TEXT,
    is_active         INTEGER DEFAULT 1
, depth_mm INTEGER DEFAULT NULL);
CREATE TABLE ad_covering_type (
    covering_type_id   TEXT PRIMARY KEY,
    covering_name      TEXT NOT NULL,
    covering_category  TEXT NOT NULL,       -- CEILING, FLOOR_FINISH, WALL_FINISH, INSULATION
    ifc_class          TEXT DEFAULT 'IfcCovering',
    thickness_mm       INTEGER NOT NULL,
    material           TEXT,
    profile            TEXT,
    is_active          INTEGER DEFAULT 1
);
-- ad_geometry_map renamed to I_Geometry_Map (migration_rename_geometry_map.sql)
CREATE TABLE surface_styles (style_name TEXT PRIMARY KEY, surface_r REAL, surface_g REAL, surface_b REAL, transparency REAL DEFAULT 0.0, specular_r REAL, specular_g REAL, specular_b REAL, specular_ratio REAL, specular_exponent REAL, reflectance_method TEXT DEFAULT 'NOTDEFINED', side TEXT DEFAULT 'BOTH', source TEXT);
CREATE TABLE material_layers (layer_set_name TEXT NOT NULL, sequence INTEGER NOT NULL, material_name TEXT, thickness_m REAL, is_ventilated INTEGER DEFAULT 0, PRIMARY KEY (layer_set_name, sequence));
CREATE TABLE ad_building_grid (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type TEXT NOT NULL,       -- 'SampleHouse', 'Duplex'
    axis          TEXT NOT NULL,       -- 'X' or 'Y'
    grid_label    TEXT NOT NULL,       -- 'A', 'B', '1', '2' etc.
    position_mm   REAL NOT NULL,       -- absolute position in mm from building origin
    is_active     INTEGER DEFAULT 1, building_id INTEGER REFERENCES ad_building(id),
    UNIQUE(building_type, axis, grid_label)
);
CREATE TABLE ad_room_boundary (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type TEXT NOT NULL,
    storey        TEXT NOT NULL,       -- 'Ground Floor', 'Level 1'
    room_name     TEXT NOT NULL,       -- descriptive name
    room_type     TEXT NOT NULL,       -- → ad_space_type: 'BEDROOM', 'LIVING'
    grid_min_x    TEXT NOT NULL,       -- grid label for min X
    grid_max_x    TEXT NOT NULL,       -- grid label for max X
    grid_min_y    TEXT NOT NULL,       -- grid label for min Y
    grid_max_y    TEXT NOT NULL,       -- grid label for max Y
    z_offset_mm   REAL DEFAULT 0,     -- floor level offset from storey base
    is_active     INTEGER DEFAULT 1, min_x_mm REAL, max_x_mm REAL, min_y_mm REAL, max_y_mm REAL, building_id INTEGER REFERENCES ad_building(id),
    UNIQUE(building_type, storey, room_name)
);
CREATE TABLE ad_building (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type   TEXT NOT NULL UNIQUE,  -- key used by all child tables
    name            TEXT NOT NULL,         -- display name
    description     TEXT,
    width_mm        REAL,                  -- envelope X extent
    depth_mm        REAL,                  -- envelope Y extent
    height_mm       REAL,                  -- wall/storey height
    num_storeys     INTEGER DEFAULT 1,
    num_units       INTEGER DEFAULT 1,     -- >1 for duplex/townhouse
    building_class  TEXT DEFAULT 'RESIDENTIAL',  -- RESIDENTIAL, COMMERCIAL, INSTITUTIONAL
    has_ifc_ref     INTEGER DEFAULT 0,     -- 1 = extracted from IFC (Rosetta stones), 0 = generative
    is_active       INTEGER DEFAULT 1
);
CREATE TABLE ad_building_registry (
    building_id       TEXT PRIMARY KEY,
    building_name     TEXT NOT NULL,
    building_type     TEXT NOT NULL,       -- RESIDENTIAL, INSTITUTIONAL, COMMERCIAL
    dsl_content       TEXT NOT NULL,       -- full DSL text (opaque manifest)
    output_db_path    TEXT NOT NULL,       -- relative to runtime base dir
    reference_db_path TEXT,                -- NULL for generative buildings
    is_active         INTEGER DEFAULT 1,
    seq_no            INTEGER DEFAULT 10,
    expected_elements INTEGER,
    spatial_digest    TEXT,                -- expected MD5 (NULL = don't check)
    provenance        TEXT DEFAULT 'EXTRACTED',  -- EXTRACTED | GENERATIVE
    description       TEXT
, geometry_fail_threshold INTEGER DEFAULT 0);
CREATE TABLE ad_building_assertions (
    building_id   TEXT NOT NULL REFERENCES ad_building_registry(building_id),
    assertion_id  TEXT NOT NULL,
    element_match TEXT NOT NULL,     -- 'IfcRoof', 'IfcWall:PARTY', etc.
    property      TEXT NOT NULL,     -- 'centroidX', 'maxZ', 'count', 'vertex_count'
    operator      TEXT NOT NULL,     -- 'EQUALS', 'GREATER_THAN', 'BETWEEN', 'LESS_THAN'
    expected      TEXT NOT NULL,     -- '4.5', '0|5.5' for BETWEEN
    tolerance     REAL DEFAULT 0.001,
    PRIMARY KEY (building_id, assertion_id)
);
CREATE TABLE IF NOT EXISTS "I_Geometry_Map" (
    id INTEGER PRIMARY KEY,
    building_type TEXT,
    element_ref TEXT NOT NULL,
    ifc_class TEXT NOT NULL,
    storey TEXT,
    ordinal INTEGER,
    geometry_hash TEXT NOT NULL REFERENCES component_geometries(geometry_hash),
    source TEXT,
    provenance TEXT DEFAULT 'LIBRARY' CHECK(provenance IN ('LIBRARY','EXTRACTED','PARAMETRIC'))
);
CREATE TABLE M_Product_Image (
    M_Product_ID TEXT PRIMARY KEY,
    geometry_hash TEXT NOT NULL,
    up_axis TEXT NOT NULL DEFAULT 'Z',
    forward_axis TEXT NOT NULL DEFAULT 'Y',
    attachment_face TEXT NOT NULL DEFAULT 'CENTER'
);
CREATE TABLE M_Product (
    product_id TEXT PRIMARY KEY,
    product_type TEXT NOT NULL,
    width REAL NOT NULL,
    depth REAL NOT NULL,
    height REAL NOT NULL,
    ifc_class TEXT,
    extracted_from TEXT NOT NULL DEFAULT 'IFC_EXTRACTION',
    is_active INTEGER DEFAULT 1,
    building_type TEXT
, carbon_kg_per_unit REAL DEFAULT 0, recyclability TEXT DEFAULT 'UNKNOWN', eol_strategy TEXT DEFAULT 'LANDFILL', lifespan_years INTEGER DEFAULT 50, maintenance_interval_months INTEGER DEFAULT 12, unit_cost_rm REAL DEFAULT 0, cost_uom TEXT DEFAULT 'EA', cost_spec TEXT, construction_phase TEXT DEFAULT 'Architecture', construction_sequence INTEGER DEFAULT 6, labor_resource TEXT DEFAULT 'GENERAL', labor_rate_rm_per_day REAL DEFAULT 145.0, labor_crew_size INTEGER DEFAULT 2, productivity_rate REAL DEFAULT 10.0, equipment_type TEXT, equipment_rate_rm_per_day REAL DEFAULT 0, equipment_duration_factor REAL DEFAULT 0);
CREATE TABLE ad_material_thermal (
    material_name     TEXT PRIMARY KEY,
    conductivity_w_mk REAL NOT NULL,
    description       TEXT,
    source            TEXT DEFAULT 'CIBSE'
);
CREATE INDEX idx_threshold_check
    ON ad_check_threshold(check_id, occupancy_group, storey_type)
    ;
CREATE UNIQUE INDEX idx_geom_instance
    ON I_Geometry_Map(building_type, ifc_class, storey, ordinal)
    WHERE ordinal IS NOT NULL;
CREATE UNIQUE INDEX idx_geom_type
    ON I_Geometry_Map(element_ref, ifc_class)
    WHERE ordinal IS NULL;
