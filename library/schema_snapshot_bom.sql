CREATE TABLE IF NOT EXISTS "m_attribute" (
    param_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    bom_child_id    INTEGER NOT NULL,
    param_key       TEXT NOT NULL,
    param_value     TEXT NOT NULL,
    param_type      TEXT DEFAULT 'DOUBLE',
    unit            TEXT,
    description     TEXT,
    source_code     TEXT,
    is_active       INTEGER DEFAULT 1,
    FOREIGN KEY (bom_child_id) REFERENCES "m_bom_line"(bom_child_id),
    UNIQUE(bom_child_id, param_key)
);
CREATE TABLE sqlite_sequence(name,seq);
CREATE TABLE M_Product_Category (
    M_Product_Category_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
    Value             TEXT NOT NULL UNIQUE,  -- old TEXT PK (e.g., 'RE', 'LIVING')
    Name              TEXT NOT NULL,
    Description       TEXT,
    IsActive          INTEGER DEFAULT 1,
    C_BPartner_ID     TEXT REFERENCES C_BPartner(C_BPartner_ID),
    aabb_width_mm     INTEGER DEFAULT 0,
    aabb_depth_mm     INTEGER DEFAULT 0,
    aabb_height_mm    INTEGER DEFAULT 0,
    doc_type          TEXT DEFAULT NULL
        CHECK(doc_type IS NULL OR doc_type IN ('Residential','Commercial','Industrial','RE','CO','IN')),
    doc_sub_type      TEXT DEFAULT NULL);
CREATE TABLE ad_building_storey (
    building_type  TEXT NOT NULL,     -- 'Ifc2x3_Duplex', 'Ifc4_SampleHouse', 'SJTII_Terminal'
    storey_name    TEXT NOT NULL,
    storey_level   INTEGER NOT NULL,
    height_m       REAL NOT NULL,
    unit_type_id   TEXT,              -- FK to ad_unit_type (NULL for single-unit/grid buildings)
    grid_def       TEXT,              -- JSON grid definition (axes, spacing) for grid-based storeys
    is_active      INTEGER DEFAULT 1,
    PRIMARY KEY (building_type, storey_name)
);
CREATE TABLE ad_building_template (
        template_id       TEXT PRIMARY KEY,    -- 'CONDO_30F', 'OFFICE_TOWER'
        template_name     TEXT NOT NULL,
        building_type     TEXT NOT NULL,       -- RESIDENTIAL, COMMERCIAL, MIXED
        description       TEXT,
        default_floors    INTEGER,             -- Total floors if not specified
        min_floors        INTEGER,
        max_floors        INTEGER,
        typical_floor_start INTEGER,           -- Where typical floors begin (1-indexed)
        typical_floor_end   INTEGER,           -- Where typical floors end (negative = from top)
        structural_system TEXT,                -- FRAMED, SHEAR_WALL, CORE_OUTRIGGER
        code_reference    TEXT,
        is_active         INTEGER DEFAULT 1
    );
CREATE TABLE ad_building_assertions (
    building_id   TEXT NOT NULL REFERENCES "c_order"(C_Order_ID),
    assertion_id  TEXT NOT NULL,
    element_match TEXT NOT NULL,     -- 'IfcRoof', 'IfcWall:PARTY', etc.
    property      TEXT NOT NULL,     -- 'centroidX', 'maxZ', 'count', 'vertex_count'
    operator      TEXT NOT NULL,     -- 'EQUALS', 'GREATER_THAN', 'BETWEEN', 'LESS_THAN'
    expected      TEXT NOT NULL,     -- '4.5', '0|5.5' for BETWEEN
    tolerance     REAL DEFAULT 0.001,
    PRIMARY KEY (building_id, assertion_id)
);
CREATE TABLE ad_building_bom (
        bom_id            INTEGER PRIMARY KEY AUTOINCREMENT,
        template_id       TEXT NOT NULL REFERENCES ad_building_template(template_id),
        floor_type_id     TEXT NOT NULL REFERENCES ad_floor_type(floor_type_id),
        position          TEXT NOT NULL,       -- HEAD, STANDARD, TAIL
        seq_no            INTEGER NOT NULL,    -- Order within position (1, 2, 3...)
        qty_fixed         INTEGER,             -- Fixed quantity (for HEAD/TAIL)
        qty_min           INTEGER,             -- Min repeats (for STANDARD)
        qty_max           INTEGER,             -- Max repeats (for STANDARD)
        qty_default       INTEGER,             -- Default repeats
        level_name_pattern TEXT,               -- 'B%d', 'Level_%d', 'P%d'
        notes             TEXT,
        is_active         INTEGER DEFAULT 1,
        UNIQUE(template_id, floor_type_id, seq_no)
    );
CREATE TABLE ad_building_code (
            code_id         TEXT PRIMARY KEY,
            code_name       TEXT NOT NULL,
            category        TEXT,              -- ELECTRICAL, PLUMBING, FIRE, MECHANICAL
            jurisdiction    TEXT,              -- USA, MALAYSIA, INTERNATIONAL
            year            INTEGER,
            is_active       INTEGER DEFAULT 1,
            supersedes      TEXT,              -- Previous version code_id
            url             TEXT               -- Reference URL
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
    is_active     INTEGER DEFAULT 1, min_x_mm REAL, max_x_mm REAL, min_y_mm REAL, max_y_mm REAL, building_id INTEGER REFERENCES ad_building(id), extracted_from TEXT NOT NULL DEFAULT 'GRID_DERIVED', coordinate_frame TEXT NOT NULL DEFAULT 'LOCAL_MM',
    UNIQUE(building_type, storey, room_name)
);
CREATE TABLE ad_building_grid (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type TEXT NOT NULL,       -- 'Ifc4_SampleHouse', 'Ifc2x3_Duplex'
    axis          TEXT NOT NULL,       -- 'X' or 'Y'
    grid_label    TEXT NOT NULL,       -- 'A', 'B', '1', '2' etc.
    position_mm   REAL NOT NULL,       -- absolute position in mm from building origin
    is_active     INTEGER DEFAULT 1, building_id INTEGER REFERENCES ad_building(id),
    UNIQUE(building_type, axis, grid_label)
);
CREATE TABLE ad_space_dim (
        space_type        TEXT PRIMARY KEY,    -- 'BEDROOM', 'BATHROOM', 'KITCHEN'

        -- Size constraints
        min_width         REAL,
        min_depth         REAL,
        min_area          REAL,
        min_height        REAL DEFAULT 2.4,

        -- Preferred proportions
        preferred_ratio   REAL DEFAULT 1.5,    -- width:depth ratio
        max_ratio         REAL DEFAULT 2.5,

        -- What fits inside (auto-populate)
        required_products TEXT,                -- JSON: ["TOILET","SINK"]
        optional_products TEXT,                -- JSON: ["SHOWER","TUB"]

        -- Layout rules
        door_on_wall      TEXT DEFAULT 'ANY',  -- LONGEST, SHORTEST, ANY
        window_on_wall    TEXT DEFAULT 'EXTERIOR',

        code_ref          TEXT,
        is_active         INTEGER DEFAULT 1
    , facade_type TEXT DEFAULT 'OPAQUE', ventilation_opening TEXT);
CREATE TABLE ad_space_type (
        space_type_id     TEXT PRIMARY KEY,    -- 'BEDROOM', 'BATHROOM'
        category          TEXT NOT NULL,        -- 'HABITABLE', 'SERVICE', etc.
        omniclass_code    TEXT NOT NULL,        -- '13-21 11 00'
        wall_rule         TEXT NOT NULL,        -- 'ENCLOSED', 'PERIMETER_ONLY', 'NONE', 'AS_REQUIRED'
        is_sleeping_room  INTEGER DEFAULT 0,
        is_open_plan      INTEGER DEFAULT 0,
        is_exterior       INTEGER DEFAULT 0,
        min_area          REAL DEFAULT 0,       -- m^2 (from IRC/UBBL)
        min_dimension     REAL DEFAULT 0,       -- m
        requires_window   INTEGER DEFAULT 0,
        requires_egress   INTEGER DEFAULT 0,
        structural_grid   INTEGER DEFAULT 0,    -- For large-span spaces
        beam_max_span     REAL DEFAULT 8.0,
        code_reference    TEXT,                 -- 'IRC R304.1', 'UBBL 1984'
        is_active         INTEGER DEFAULT 1
    );
CREATE TABLE ad_space_type_alias (
        alias             TEXT PRIMARY KEY,
        space_type_id     TEXT NOT NULL REFERENCES ad_space_type(space_type_id)
    );
CREATE TABLE ad_space_exterior_rule (
    space_type_id     TEXT NOT NULL,
    requires_exterior INTEGER DEFAULT 0,  -- 1 = must have at least one exterior wall
    preferred_exterior TEXT,               -- 'ANY', 'SOUTH', 'NORTH', 'STREET_FACING'
    min_exterior_ratio REAL DEFAULT 0,     -- min fraction of perimeter on exterior (0-1)
    facade_preference  TEXT,               -- 'GLASS_CURTAIN', 'OPAQUE', 'MIXED'
    notes             TEXT,
    is_active         INTEGER DEFAULT 1,
    PRIMARY KEY (space_type_id)
);
CREATE TABLE ad_space_adjacency (
    space_type_a    TEXT NOT NULL,
    space_type_b    TEXT NOT NULL,
    relationship    TEXT NOT NULL,
    door_required   INTEGER DEFAULT 1,
    notes           TEXT,
    is_active       INTEGER DEFAULT 1,
    PRIMARY KEY (space_type_a, space_type_b)
);
CREATE TABLE IF NOT EXISTS "M_Product" (
        M_Product_ID      INTEGER PRIMARY KEY AUTOINCREMENT,
        product_id        TEXT NOT NULL,       -- old TEXT PK preserved (Tier 2 migration)
        Value             TEXT,                -- iDempiere SearchKey (= product_id)
        Name              TEXT,                -- iDempiere Name (= product_id initially)
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
    , extracted_from TEXT NOT NULL DEFAULT 'PENDING'
    CHECK(extracted_from NOT IN ('','TODO','UNKNOWN')), material_name TEXT, material_rgba TEXT, component_id INTEGER, bom_id TEXT REFERENCES m_bom(bom_id), ifc_class TEXT, M_AttributeSet_ID TEXT, Name TEXT, Description TEXT, M_Product_Category_ID TEXT REFERENCES M_Product_Category(M_Product_Category_ID));
CREATE TABLE ad_element_dependency (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type TEXT NOT NULL,
    element_ref   TEXT NOT NULL,       -- child element
    parent_ref    TEXT NOT NULL,       -- parent (wall, room, grid line)
    relation      TEXT NOT NULL,       -- 'HOSTED_ON', 'CONTAINED_IN', 'CONNECTS_TO', 'SUPPORTS'
    cascade_rule  TEXT DEFAULT 'MOVE', -- 'MOVE', 'RECOMPUTE', 'NONE'
    is_active     INTEGER DEFAULT 1, building_id INTEGER REFERENCES ad_building(id),
    UNIQUE(building_type, element_ref, parent_ref)
);
CREATE TABLE ad_element_mep (
        element_type      TEXT PRIMARY KEY,    -- 'OUTLET', 'SPRINKLER'
        ifc_class         TEXT NOT NULL,       -- 'IfcOutlet'
        discipline        TEXT NOT NULL,       -- 'ELEC', 'FP', 'SP'
        mep_system        TEXT,                -- -> ad_ref_value(MEP_SYSTEM, *)
        host_type         TEXT,                -- -> ad_ref_value(HOST_TYPE, *)
        dist_role         TEXT,                -- -> ad_ref_value(DIST_ROLE, *)
        circuit_type      TEXT,                -- -> ad_ref_value(CIRCUIT_TYPE, *)
        mount_height      REAL,                -- meters from floor
        clearance         TEXT,                -- JSON: {"front":0.5,"side":0.3}
        ports             TEXT,                -- JSON: [{"id":"IN","size":0.015}]
        properties        TEXT,                -- JSON: extra properties
        code_ref          TEXT,
        is_active         INTEGER DEFAULT 1
    , width REAL, depth REAL, height REAL);
CREATE TABLE ad_wall_face (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type TEXT NOT NULL,
    storey        TEXT NOT NULL,
    room_name     TEXT NOT NULL,       -- owner room
    face          TEXT NOT NULL,       -- 'NORTH', 'SOUTH', 'EAST', 'WEST'
    wall_type_id  TEXT NOT NULL,       -- → ad_wall_type: 'EXTERIOR_BRICK', 'INTERIOR_PARTITION_92'
    is_exterior   INTEGER NOT NULL,    -- 1 = exterior envelope, 0 = interior
    adjacent_room TEXT,                -- NULL if exterior, else neighbour room name
    is_active     INTEGER DEFAULT 1, building_id INTEGER REFERENCES ad_building(id),
    UNIQUE(building_type, storey, room_name, face)
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
CREATE TABLE ad_space_type_opening (
    space_type_id      TEXT NOT NULL,
    opening_role       TEXT NOT NULL,       -- ENTRY, EGRESS, NATURAL_LIGHT, VENTILATION
    family_id          TEXT NOT NULL,
    qty_rule           TEXT DEFAULT 'FIXED', -- FIXED, PER_EXTERIOR_WALL
    qty_base           INTEGER DEFAULT 1,
    override_width_mm  INTEGER,
    override_height_mm INTEGER,
    sill_height_mm     INTEGER DEFAULT 0,
    placement_wall     TEXT,                -- NULL=auto, 'exterior', 'interior'
    placement_rule     TEXT DEFAULT 'CENTER',
    is_fire_rated      INTEGER DEFAULT 0,
    building_code      TEXT,
    code_clause        TEXT,
    priority           INTEGER DEFAULT 100,
    is_active          INTEGER DEFAULT 1, profile TEXT DEFAULT NULL,
    PRIMARY KEY (space_type_id, opening_role, family_id),
    FOREIGN KEY (family_id) REFERENCES ad_opening_family(family_id)
);
CREATE TABLE ad_beam_type (
    beam_type_id     TEXT PRIMARY KEY,
    category         TEXT NOT NULL,     -- FLOOR, LINTEL, TIE, SPANDREL
    construction     TEXT NOT NULL,     -- REINFORCED_CONCRETE, STEEL, TIMBER
    depth_mm         INTEGER NOT NULL,  -- section depth (vertical)
    width_mm         INTEGER NOT NULL,  -- section width (horizontal)
    span_max_m       REAL,             -- max unsupported span (NULL=unrestricted)
    is_loadbearing   INTEGER DEFAULT 1,
    material_primary TEXT,             -- fck/grade
    ifc_class        TEXT DEFAULT 'IfcBeam',
    description      TEXT,
    is_active        INTEGER DEFAULT 1
);
CREATE TABLE ad_beam_type_rule (
    rule_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    context       TEXT NOT NULL,       -- FLOOR, LINTEL
    span_min_m    REAL DEFAULT 0,      -- minimum span for this rule
    span_max_m    REAL,                -- maximum span (NULL=any)
    beam_type_id  TEXT NOT NULL REFERENCES ad_beam_type,
    priority      INTEGER DEFAULT 100,
    profile       TEXT DEFAULT NULL,   -- building profile (NULL=generic)
    description   TEXT,
    is_active     INTEGER DEFAULT 1,
    UNIQUE(context, span_min_m, span_max_m, profile)
);
CREATE TABLE ad_column_type (
    column_type_id   TEXT PRIMARY KEY,
    construction     TEXT NOT NULL,     -- REINFORCED_CONCRETE, STEEL, TIMBER
    width_mm         INTEGER NOT NULL,
    depth_mm         INTEGER NOT NULL,
    ifc_class        TEXT DEFAULT 'IfcColumn',
    description      TEXT,
    is_active        INTEGER DEFAULT 1
);
CREATE TABLE ad_column_type_rule (
    rule_id         INTEGER PRIMARY KEY AUTOINCREMENT,
    context         TEXT NOT NULL,       -- CORNER, T_JUNCTION, INTERMEDIATE
    profile         TEXT DEFAULT NULL,   -- building profile (NULL=generic)
    column_type_id  TEXT NOT NULL REFERENCES ad_column_type,
    priority        INTEGER DEFAULT 100,
    description     TEXT,
    is_active       INTEGER DEFAULT 1,
    UNIQUE(context, profile)
);
CREATE TABLE ad_wall_type (
    wall_type_id    TEXT PRIMARY KEY,
    category        TEXT NOT NULL,       -- EXTERIOR, INTERIOR, PARTY, FOUNDATION
    construction    TEXT NOT NULL,       -- BRICK_BLOCK, STUD_FRAME, CMU, CONCRETE, FURRING
    stud_mm         INTEGER,            -- structural member thickness (NULL for solid)
    total_mm        INTEGER NOT NULL,   -- total wall thickness incl. finishes (geometry truth)
    fire_rating     TEXT,               -- e.g. FRL_60_60_60, NULL if none
    is_loadbearing  INTEGER DEFAULT 0,
    is_exterior     INTEGER DEFAULT 0,
    material_primary TEXT,              -- dominant material
    ifc_class       TEXT DEFAULT 'IfcWallStandardCase',
    description     TEXT,
    is_active       INTEGER DEFAULT 1
, span_mode TEXT DEFAULT 'PER_STOREY');
CREATE TABLE ad_wall_type_rule (
    rule_id         INTEGER PRIMARY KEY AUTOINCREMENT,
    context         TEXT NOT NULL,       -- EXTERIOR, INTERIOR, PARTY, FOUNDATION, PLUMBING, ENCLOSURE
    space_type_a    TEXT,               -- room type on side A (NULL = any)
    space_type_b    TEXT,               -- room type on side B (NULL = any)
    wall_type_id    TEXT NOT NULL,      -- → ad_wall_type
    priority        INTEGER DEFAULT 100, -- lower = higher priority (checked first)
    description     TEXT,
    is_active       INTEGER DEFAULT 1, profile TEXT DEFAULT NULL,
    UNIQUE(context, space_type_a, space_type_b)
);
CREATE TABLE ad_railing_type (
    railing_type_id    TEXT PRIMARY KEY,
    railing_name       TEXT NOT NULL,
    railing_category   TEXT NOT NULL,       -- STAIR_GUARD, BALCONY, WALL_GUARD, HANDRAIL
    ifc_class          TEXT DEFAULT 'IfcRailing',
    height_mm          INTEGER NOT NULL,    -- railing height (typically 1000-1100mm)
    width_mm           INTEGER DEFAULT 50,  -- railing thickness/depth
    host_type          TEXT,                -- STAIR, SLAB_EDGE, WALL_TOP
    material           TEXT,
    profile            TEXT,
    is_active          INTEGER DEFAULT 1
);
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
CREATE TABLE ad_floor_type (
        floor_type_id     TEXT PRIMARY KEY,    -- 'BASEMENT_1', 'TYPICAL_CONDO'
        floor_name        TEXT NOT NULL,
        floor_category    TEXT NOT NULL,       -- BASEMENT, GROUND, TYPICAL, TRANSFER, ROOF, PENTHOUSE
        floor_height      REAL NOT NULL,       -- Floor-to-floor height in meters
        slab_thickness    REAL DEFAULT 0.15,
        is_below_grade    INTEGER DEFAULT 0,
        is_mechanical     INTEGER DEFAULT 0,   -- MEP floor (transfer, refuge)
        is_amenity        INTEGER DEFAULT 0,   -- Pool, gym, sky garden
        unit_types        TEXT,                -- JSON: allowed unit types on this floor
        structural_notes  TEXT,
        mep_notes         TEXT,
        is_active         INTEGER DEFAULT 1
    , min_area REAL, max_area REAL, profile TEXT);
CREATE TABLE ad_floor_type_rule (
    rule_id         INTEGER PRIMARY KEY,
    room_type       TEXT NOT NULL,
    floor_type_id   TEXT NOT NULL,
    priority        INTEGER DEFAULT 50,
    profile         TEXT,               -- NULL = generic fallback
    is_active       INTEGER DEFAULT 1
);
CREATE TABLE ad_slab_spec (
    building_type  TEXT NOT NULL,       -- 'Ifc4_SampleHouse', 'Ifc2x3_Duplex', etc.
    slab_role      TEXT NOT NULL,       -- 'FOUNDATION', 'FLOOR', 'CEILING'
    extend_x_m     REAL NOT NULL DEFAULT 0.2,   -- slab extends room_bounds ± this in X per side
    extend_y_m     REAL NOT NULL DEFAULT 0.2,   -- slab extends room_bounds ± this in Y per side
    thickness_m    REAL,                -- slab thickness (NULL = use profile default)
    slab_name      TEXT,                -- element name for output
    ceiling_z_m    REAL,                -- for CEILING: Z of slab top above storey baseZ
    is_active      INTEGER DEFAULT 1,
    PRIMARY KEY (building_type, slab_role)
);
CREATE TABLE ad_space_type_mep (
        space_type_id     TEXT PRIMARY KEY REFERENCES ad_space_type(space_type_id),
        plumbing_fixtures TEXT,                 -- JSON: '["toilet","sink"]'
        requires_stack    INTEGER DEFAULT 0,
        requires_vent     INTEGER DEFAULT 0,
        water_supply      TEXT DEFAULT 'none',
        light_points      INTEGER DEFAULT 0,
        power_points      INTEGER DEFAULT 0,
        switch_points     INTEGER DEFAULT 0,
        requires_dedicated INTEGER DEFAULT 0,
        circuit_type      TEXT DEFAULT 'general',
        requires_exhaust  INTEGER DEFAULT 0,
        exhaust_cfm       INTEGER DEFAULT 0,
        allows_aircon     INTEGER DEFAULT 0,
        ventilation_type  TEXT DEFAULT 'natural'
    );
CREATE TABLE ad_space_type_mep_bom (
            space_type_id   TEXT,
            mep_product_id  TEXT,

            -- Fixed quantity bounds
            qty_min         INTEGER DEFAULT 0,
            qty_normal      INTEGER DEFAULT 1,
            qty_max         INTEGER DEFAULT 99,

            -- Per-area bounds (when > 0, overrides fixed qty)
            per_area_min    REAL DEFAULT 0,
            per_area_normal REAL DEFAULT 0,
            per_area_max    REAL DEFAULT 0,

            -- Placement
            placement_rule  TEXT DEFAULT 'AUTO',
            host_surface    TEXT DEFAULT 'WALL',

            -- Provenance
            building_code   TEXT,
            code_clause     TEXT,

            -- Conduit/pipe budget (meters)
            conduit_min     REAL DEFAULT 0,
            conduit_normal  REAL DEFAULT 0,
            conduit_max     REAL DEFAULT 0,

            PRIMARY KEY (space_type_id, mep_product_id)
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
CREATE TABLE ad_code_requirement (
            code_id         TEXT REFERENCES ad_building_code(code_id),
            clause          TEXT,              -- 210.52(A), 8.5.5
            requirement_id  TEXT,              -- Unique key for this requirement

            -- What it applies to
            element_type    TEXT,              -- OUTLET, SPRINKLER, LIGHT
            space_type      TEXT DEFAULT 'ANY',-- BEDROOM, BATHROOM, or ANY

            -- Quantity rules (mutually exclusive)
            qty_fixed       INTEGER,           -- Fixed count (1 exhaust fan)
            qty_per_area    REAL,              -- Per m² (0.1 = 1 per 10m²)
            qty_per_wall    INTEGER,           -- Per wall (1 outlet per wall)

            -- Spacing rules
            max_spacing_m   REAL,              -- Max distance between (3.6m for outlets)
            min_clearance_m REAL,              -- Min clearance from obstruction

            -- Coverage rules (sprinklers)
            coverage_area   REAL,              -- m² per device
            coverage_radius REAL,              -- meters radius

            -- Placement constraints
            max_height_m    REAL,              -- Max mounting height
            min_height_m    REAL,              -- Min mounting height
            placement_rule  TEXT,              -- WALL, CEILING, FLOOR

            -- Description
            description     TEXT,
            is_mandatory    INTEGER DEFAULT 1, -- 1=required, 0=recommended

            PRIMARY KEY (code_id, clause, element_type, space_type)
        );
CREATE TABLE ad_egress_travel (
            travel_id           TEXT PRIMARY KEY,
            occupancy_group     TEXT NOT NULL,

            -- Travel limits
            max_travel_m        REAL NOT NULL,      -- Max travel to exit
            max_dead_end_m      REAL,               -- Max dead-end corridor
            max_common_path_m   REAL,               -- Max common path of egress

            -- Modifiers
            sprinklered_bonus   REAL DEFAULT 1.0,   -- Multiplier if sprinklered (e.g., 1.25)

            -- Source
            code_id             TEXT,
            clause              TEXT,
            jurisdiction        TEXT DEFAULT 'INTERNATIONAL'
        );
CREATE TABLE ad_stair_requirement (
            req_id              TEXT PRIMARY KEY,
            occupancy_group     TEXT NOT NULL,      -- R, B, A, E, M, H, or ANY

            -- Width calculation
            min_width_mm        INTEGER NOT NULL,   -- Absolute minimum width
            width_per_person_mm REAL,               -- Additional width per person served
            max_occupant_per_stair INTEGER,         -- Max persons per stair (for quantity calc)

            -- Geometry constraints (EXTRACTED from codes)
            max_riser_mm        INTEGER,            -- Max riser height
            min_riser_mm        INTEGER,            -- Min riser height
            min_tread_mm        INTEGER,            -- Min tread depth (going)
            max_flight_rise_m   REAL,               -- Max vertical rise per flight

            -- Landing requirements
            min_landing_length_mm INTEGER,          -- Min landing dimension
            landing_at_doors    INTEGER DEFAULT 1,  -- Require landing at door swing

            -- Handrail
            handrail_height_mm  INTEGER,            -- Handrail height
            handrail_both_sides INTEGER DEFAULT 1,  -- Require on both sides
            handrail_extension_mm INTEGER,          -- Extension beyond top/bottom

            -- Fire rating
            min_fire_rating_hr  REAL,               -- Enclosure fire rating (hours)

            -- Source
            code_id             TEXT,
            clause              TEXT,
            jurisdiction        TEXT DEFAULT 'INTERNATIONAL'
        );
CREATE TABLE ad_elevator_requirement (
            req_id              TEXT PRIMARY KEY,
            elevator_type       TEXT NOT NULL,      -- PASSENGER, FIRE, STRETCHER, FREIGHT, ACCESSIBLE
            occupancy_group     TEXT DEFAULT 'ANY',

            -- Trigger (when this elevator type is required)
            trigger_storeys     INTEGER,            -- Required if >= this many storeys
            trigger_travel_m    REAL,               -- Required if travel > this (m)
            trigger_occupants   INTEGER,            -- Required if occupant load > this
            trigger_beds        INTEGER,            -- For stretcher lift (hospital)

            -- Quantity
            min_quantity        INTEGER DEFAULT 1,
            quantity_formula    TEXT,               -- e.g., "1 + floor((units-8)/50)"

            -- Car dimensions (minimum internal)
            min_width_mm        INTEGER,
            min_depth_mm        INTEGER,
            min_door_width_mm   INTEGER,

            -- Lobby requirements
            min_lobby_depth_m   REAL,               -- Min elevator lobby depth
            min_lobby_width_m   REAL,               -- Min lobby width per car

            -- Fire requirements
            fire_rating_hr      REAL,               -- Shaft fire rating
            smoke_control       INTEGER DEFAULT 0,  -- Requires smoke control
            emergency_power     INTEGER DEFAULT 0,  -- Requires backup power

            -- Accessibility
            is_accessible       INTEGER DEFAULT 1,  -- Must be accessible
            voice_annunciator   INTEGER DEFAULT 0,  -- Requires voice announcement

            -- Source
            code_id             TEXT,
            clause              TEXT,
            jurisdiction        TEXT DEFAULT 'INTERNATIONAL'
        , shaft_clearance_m REAL DEFAULT 0.2, min_spacing_m REAL DEFAULT 0.3);
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
CREATE TABLE ad_fire_riser_requirement (
        req_id            TEXT PRIMARY KEY,
        riser_type        TEXT NOT NULL,       -- WET_RISER, DRY_RISER, COMBINED, STANDPIPE
        hazard_class      TEXT DEFAULT 'LIGHT',-- LIGHT, ORDINARY_1, ORDINARY_2, EXTRA
        min_storeys       INTEGER,
        max_storeys       INTEGER,
        min_height_m      REAL,
        max_height_m      REAL,
        max_floor_area_m2 REAL,                -- Per floor area served
        pipe_diameter_mm  INTEGER NOT NULL,
        branch_diameter_mm INTEGER,
        min_flow_lpm      INTEGER,             -- Minimum flow rate
        min_pressure_bar  REAL,                -- Minimum residual pressure
        pump_required     INTEGER DEFAULT 0,
        tank_capacity_l   INTEGER,
        valve_type        TEXT,                -- GATE, CHECK, ALARM
        code_id           TEXT,
        clause            TEXT,
        description       TEXT,
        jurisdiction      TEXT DEFAULT 'INTERNATIONAL',
        is_active         INTEGER DEFAULT 1
    );
CREATE TABLE ad_vert_circ_trigger (
            trigger_id          TEXT PRIMARY KEY,
            element_type        TEXT NOT NULL,      -- STAIR, ELEVATOR, FIRE_LIFT, STRETCHER_LIFT
            trigger_type        TEXT NOT NULL,      -- STOREY_COUNT, TRAVEL_HEIGHT, OCCUPANT_LOAD, AREA

            -- Threshold values (one applies based on trigger_type)
            min_storeys         INTEGER,            -- Trigger if >= this many storeys
            min_travel_m        REAL,               -- Trigger if travel > this height (m)
            min_occupant_load   INTEGER,            -- Trigger if occupant load >= this
            min_area_m2         REAL,               -- Trigger if floor area >= this

            -- Output
            min_quantity        INTEGER DEFAULT 1,  -- Minimum number required
            quantity_formula    TEXT,               -- Formula for additional (e.g., "1 + floor(occupants/500)")

            -- Scope
            occupancy_group     TEXT DEFAULT 'ANY', -- R, B, A, E, M, or ANY
            jurisdiction        TEXT DEFAULT 'INTERNATIONAL',

            -- Source
            code_id             TEXT,
            clause              TEXT,
            description         TEXT,
            is_mandatory        INTEGER DEFAULT 1
        );
CREATE TABLE ad_mep_profile (
            profile_id      TEXT PRIMARY KEY,
            description     TEXT,
            qty_column      TEXT,
            per_area_column TEXT,
            conduit_column  TEXT
        );
CREATE TABLE ad_fp_coverage (
        hazard_class      TEXT PRIMARY KEY,    -- 'LIGHT', 'ORDINARY_1'
        max_coverage_m2   REAL NOT NULL,
        max_spacing_m     REAL NOT NULL,
        min_spacing_m     REAL NOT NULL,
        wall_distance_m   REAL,
        k_factor          REAL,
        code_ref          TEXT DEFAULT 'NFPA 13',
        is_active         INTEGER DEFAULT 1
    );
CREATE TABLE ad_fp_trigger (
        trigger_id        TEXT PRIMARY KEY,
        trigger_type      TEXT NOT NULL,       -- STOREY_COUNT, BUILDING_HEIGHT, FLOOR_AREA, OCCUPANCY
        element_type      TEXT NOT NULL,       -- SPRINKLER, STANDPIPE, FIRE_ALARM, SMOKE_DETECTION
        min_storeys       INTEGER,
        min_height_m      REAL,
        min_floor_area_m2 REAL,
        total_area_m2     REAL,
        occupancy_group   TEXT,                -- R, A, B, E, F, H, I, M, S (IBC groups)
        min_quantity      INTEGER DEFAULT 1,
        quantity_formula  TEXT,
        code_id           TEXT,
        clause            TEXT,
        description       TEXT,
        jurisdiction      TEXT DEFAULT 'INTERNATIONAL',
        is_mandatory      INTEGER DEFAULT 1,
        is_active         INTEGER DEFAULT 1
    );
CREATE TABLE ad_pressurization_trigger (
            trigger_id          TEXT PRIMARY KEY,
            element_type        TEXT NOT NULL,      -- STAIR, ELEVATOR_LOBBY, CORRIDOR

            -- Trigger conditions
            min_storeys         INTEGER,
            min_height_m        REAL,
            underground_storeys INTEGER,

            -- Pressure differential
            min_pressure_pa     INTEGER,            -- Minimum pressure (Pascal)
            max_pressure_pa     INTEGER,            -- Maximum (door operability)

            -- Door requirements
            max_door_force_n    INTEGER,            -- Max force to open door (N)

            -- Source
            code_id             TEXT,
            clause              TEXT,
            jurisdiction        TEXT DEFAULT 'INTERNATIONAL',
            description         TEXT
        );
CREATE TABLE IF NOT EXISTS "ad_sysconfig" (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    config_key TEXT NOT NULL,
    config_value TEXT NOT NULL,
    description TEXT, is_active INTEGER DEFAULT 1,
    UNIQUE(config_key, config_value)
);
CREATE TABLE ad_placement_rule (
            rule_id         TEXT PRIMARY KEY,
            description     TEXT,

            -- Surface type
            surface         TEXT,               -- CEILING, WALL, FLOOR

            -- Position parameters (EXTRACTED from Java)
            height_m        REAL,               -- Mounting height above floor
            wall_offset_m   REAL,               -- Distance from wall face
            edge_offset_m   REAL,               -- Distance from room edges

            -- Spacing (for grid/distributed patterns)
            max_spacing_m   REAL,               -- Max distance between items

            -- Clearances (EXTRACTED from Java)
            clearance_front_m REAL,             -- Front clearance
            clearance_side_m  REAL,             -- Side clearance

            -- Source (provenance)
            source_file     TEXT,               -- Java file extracted from
            source_line     TEXT,               -- Line number or constant name
            building_code   TEXT                -- Code reference
        );
CREATE TABLE IF NOT EXISTS "ad_ref_list" (
        ref_id            TEXT NOT NULL,       -- FK to ad_reference
        value_id          TEXT NOT NULL,       -- 'ELECTRICAL', 'WALL'
        value_name        TEXT NOT NULL,
        seq_no            INTEGER DEFAULT 10,  -- Sort order
        color_code        TEXT,                -- For viz '#FF0000'
        code_reference    TEXT,                -- Standard reference
        extra_json        TEXT,                -- Additional properties as JSON
        is_active         INTEGER DEFAULT 1,
        PRIMARY KEY (ref_id, value_id)
    );
CREATE TABLE ad_reference (
        ref_id            TEXT PRIMARY KEY,    -- 'MEP_SYSTEM', 'HOST_TYPE'
        ref_name          TEXT NOT NULL,
        description       TEXT,
        is_active         INTEGER DEFAULT 1
    );
CREATE TABLE ad_jurisdiction_codes (
            jurisdiction    TEXT,              -- MALAYSIA, USA, SINGAPORE
            code_id         TEXT REFERENCES ad_building_code(code_id),
            is_primary      INTEGER DEFAULT 0, -- Primary code for this jurisdiction
            adoption_date   TEXT,
            notes           TEXT,
            PRIMARY KEY (jurisdiction, code_id)
        );
CREATE TABLE IF NOT EXISTS "ad_assembly_connector" (
    connector_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    assembly_id   TEXT NOT NULL,
    version       TEXT NOT NULL DEFAULT '1.0.0',
    face          TEXT NOT NULL,
    connector_type TEXT NOT NULL,
    position_x    REAL DEFAULT 0,
    position_y    REAL DEFAULT 0,
    position_z    REAL DEFAULT 0,
    diameter_mm   REAL,
    connects_to   TEXT,
    UNIQUE(assembly_id, version, face, connector_type)
);
CREATE TABLE ad_assembly_manifest (
    manifest_id   INTEGER PRIMARY KEY AUTOINCREMENT,
    assembly_id   TEXT NOT NULL,
    version       TEXT NOT NULL DEFAULT '1.0.0',
    face          TEXT NOT NULL,
    interface_type TEXT NOT NULL,
    clearance_m   REAL DEFAULT 0,
    UNIQUE(assembly_id, version, face, interface_type)
);
CREATE TABLE ad_typology_template (
    typology_id   TEXT PRIMARY KEY,
    typology_name TEXT NOT NULL,
    grid_strategy TEXT NOT NULL CHECK(grid_strategy IN ('STRIP_ZONES','COURTYARD','LINEAR')),
    ubbl_class    TEXT NOT NULL,
    description   TEXT,
    base_width_mm INTEGER NOT NULL,
    base_depth_mm INTEGER NOT NULL,
    zone_json     TEXT NOT NULL,
    is_active     INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE ad_typology_pattern (
    typology_id  TEXT    NOT NULL,
    pattern_id   TEXT    NOT NULL REFERENCES ad_spatial_pattern(pattern_id),
    sequence     INTEGER NOT NULL DEFAULT 10,
    PRIMARY KEY (typology_id, pattern_id)
);
CREATE TABLE ad_unit_type (
        unit_type_id      TEXT PRIMARY KEY,    -- '1BR_A', '2BR_B', 'STUDIO'
        unit_name         TEXT NOT NULL,
        unit_category     TEXT NOT NULL,       -- STUDIO, 1BR, 2BR, 3BR, PENTHOUSE, RETAIL
        typical_area      REAL,                -- m²
        bedroom_count     INTEGER DEFAULT 0,
        bathroom_count    INTEGER DEFAULT 0,
        has_balcony       INTEGER DEFAULT 0,
        has_utility       INTEGER DEFAULT 0,
        room_layout       TEXT,                -- JSON: room types and rough areas
        mep_load          TEXT,                -- JSON: electrical, plumbing loads
        is_active         INTEGER DEFAULT 1
    , width REAL, depth REAL, min_width REAL, min_depth REAL);
CREATE TABLE ad_unit_type_room (
    unit_type_id TEXT NOT NULL,
    room_key     TEXT NOT NULL,
    room_type    TEXT NOT NULL,
    frac_min_x   REAL NOT NULL,
    frac_min_y   REAL NOT NULL,
    frac_max_x   REAL NOT NULL,
    frac_max_y   REAL NOT NULL,
    needs_window INTEGER DEFAULT 0,
    opens_to     TEXT,
    is_active    INTEGER DEFAULT 1,
    PRIMARY KEY (unit_type_id, room_key)
);
CREATE TABLE bad_rule (
    rule_id         TEXT PRIMARY KEY,
    category_id     TEXT NOT NULL REFERENCES bad_rule_category,
    rule_name       TEXT NOT NULL,

    -- Rule severity
    rule_type       TEXT NOT NULL,
    -- CONSTRAINT  : hard limit, blocks compilation if violated
    -- PREFERENCE  : soft, logged as advisory warning
    -- RELATIONSHIP: defines required adjacency or dependency
    -- COORDINATION: cross-discipline clash prevention

    -- What it applies to
    applies_to_domain TEXT NOT NULL,     -- 'sd', 'cd', 'rd', 'sf', 'bt'
    applies_to_type   TEXT NOT NULL,     -- 'BEDROOM_MY', 'TOILET_*', '*'

    -- The rule expression
    property        TEXT NOT NULL,
    operator        TEXT NOT NULL,
    value           TEXT NOT NULL,
    value_unit      TEXT,

    -- Conditional application (NULL = always applies)
    condition_expr  TEXT,

    -- Authority and citation
    authority_id    TEXT,
    clause_ref      TEXT,
    authority_name  TEXT,

    -- Severity and status
    severity        TEXT DEFAULT 'ERROR',
    is_active       INTEGER DEFAULT 1,
    seq_no          INTEGER DEFAULT 10,

    -- Override: more specific rule replaces generic
    override_rule_id TEXT REFERENCES bad_rule,

    -- Provenance (PRIME RULE: every rule must cite its source)
    provenance      TEXT NOT NULL
);
CREATE TABLE bad_rule_category (
    category_id     TEXT PRIMARY KEY,
    category_name   TEXT NOT NULL,
    description     TEXT NOT NULL,
    seq_no          INTEGER NOT NULL,
    is_active       INTEGER DEFAULT 1
);
CREATE TABLE bad_rule_param (
    rule_id         TEXT NOT NULL REFERENCES bad_rule,
    param_key       TEXT NOT NULL,
    param_value     TEXT NOT NULL,
    param_unit      TEXT,
    provenance      TEXT NOT NULL,
    PRIMARY KEY (rule_id, param_key)
);
CREATE TABLE bad_discipline_priority (
    higher_discipline TEXT NOT NULL,
    lower_discipline  TEXT NOT NULL,
    resolution       TEXT NOT NULL,
    notes           TEXT,
    provenance      TEXT NOT NULL,
    PRIMARY KEY (higher_discipline, lower_discipline)
);
CREATE TABLE ad_spatial_pattern (
    pattern_id       TEXT PRIMARY KEY,   -- 'WET_CORE_CLUSTER','ROOM_MESH','SHARED_WALL'
    pattern_type     TEXT NOT NULL CHECK (pattern_type IN ('TOPOLOGY','MANIFEST','MEP','CIRCULATION')),
    extracted_from   TEXT NOT NULL,       -- Rosetta Stone source(s)
    applies_to       TEXT NOT NULL CHECK (applies_to IN ('RESIDENTIAL','INSTITUTIONAL','ALL')),
    rule_ids         TEXT,                -- CSV of ad_spatial_rule.rule_id
    callout_ids      TEXT,                -- CSV of ad_callout_rule.callout_id
    manifest_faces   TEXT,                -- CSV of MANIFEST face types this pattern anchors to
    provenance       TEXT NOT NULL
);
CREATE TABLE ad_spatial_rule (
    rule_id          TEXT PRIMARY KEY,
    rule_type        TEXT NOT NULL CHECK (rule_type IN ('PHANTOM','ALIGNMENT','CLEARANCE','TOPOLOGY')),
    trigger_class    TEXT NOT NULL,   -- IFC class that activates this rule
    applies_to       TEXT NOT NULL CHECK (applies_to IN ('RESIDENTIAL','INSTITUTIONAL','ALL')),
    extracted_from   TEXT,            -- 'TBLKTN','DUPLEX','TERMINAL','MULTI'
    constraint_expr  TEXT NOT NULL,   -- vocabulary term from §2.4 — no freeform SQL
    violation_action TEXT NOT NULL DEFAULT 'FAIL'
                         CHECK (violation_action IN ('FAIL','WARN','ADVISORY')),
    bom_level        INTEGER CHECK (bom_level BETWEEN -1 AND 4),
    provenance       TEXT NOT NULL    -- research citation or code reference
);
CREATE TABLE IF NOT EXISTS "ad_room_slot" (
    slot_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    room_type     TEXT NOT NULL,
    slot_name     TEXT NOT NULL,
    assembly_id   TEXT,
    version_range TEXT DEFAULT '[1.0,2.0)',
    slot_face     TEXT,
    slot_priority INTEGER DEFAULT 100,
    is_required   INTEGER DEFAULT 0,
    profile       TEXT DEFAULT NULL, min_area REAL DEFAULT 0.0, building_type TEXT DEFAULT NULL,
    UNIQUE(room_type, slot_name, profile)
);
CREATE TABLE ad_ubbl_rule (
    rule_id        TEXT PRIMARY KEY,
    rule_name      TEXT NOT NULL,
    room_type      TEXT,
    constraint_key TEXT NOT NULL CHECK(constraint_key IN ('AREA','MIN_DIM','CEILING_MM','NOT_ADJACENT')),
    min_value_mm   REAL,
    ubbl_ref       TEXT,
    is_active      INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE ad_callout_rule (
    callout_id       TEXT PRIMARY KEY,
    trigger_table    TEXT NOT NULL,   -- 'ad_element_rule','ad_room_boundary'
    trigger_column   TEXT NOT NULL,   -- column whose change fires this callout
    cascade_action   TEXT NOT NULL,   -- vocabulary: REFIT, INVALIDATE, REEXPAND, PROPAGATE
    cascade_target   TEXT NOT NULL,   -- 'ad_element_rule' or specific family_ref
    manifest_face    TEXT,            -- WALL_BACK,ENTRY,WASTE_OUT — MANIFEST face trigger
    condition_expr   TEXT,            -- NULL=always; 'delta > 100mm','discipline=FURN'
    bom_level        INTEGER CHECK (bom_level BETWEEN -1 AND 4),
    sequence         INTEGER NOT NULL DEFAULT 10,
    provenance       TEXT NOT NULL
);
CREATE TABLE wm_empty_storage_line (
    line_id          INTEGER PRIMARY KEY AUTOINCREMENT,

    -- ABL address — same coordinate system as ad_building_grid (all mm)
    building_type    TEXT    NOT NULL,   -- Location  (FK → ad_building_registry)
    storey           TEXT    NOT NULL,   -- Aisle     (storey label)
    room_name        TEXT    NOT NULL,   -- Bin       (FK → ad_room_boundary)
    locator_ref      TEXT    NOT NULL,   -- M_Locator (NORTH_WALL, CENTRE, FLOAT…)

    -- Capacity accounting — all in mm (mirrors WMS_Excel 8_WM_EmptyStorageLine)
    capacity_mm      REAL    NOT NULL,   -- total Locator extent along hostAxis
    filled_mm        REAL    NOT NULL DEFAULT 0,
    remaining_mm     REAL    NOT NULL,   -- = capacity_mm - filled_mm (variance)

    -- Next available putaway coordinate — mm from building origin (ad_building_grid)
    next_anchor_x_mm REAL    NOT NULL DEFAULT 0,
    next_anchor_y_mm REAL    NOT NULL DEFAULT 0,
    next_anchor_z_mm REAL    NOT NULL DEFAULT 0,

    -- iDempiere DocStatus lifecycle: DR (Draft) → CO (Complete) → VO (Void)
    doc_status       TEXT    NOT NULL DEFAULT 'DR',

    -- Audit
    created          TEXT    NOT NULL DEFAULT (datetime('now')),
    updated          TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE VIEW v_verified_room_boundary AS
SELECT
    rb.id                           AS room_id,
    rb.building_type,
    rb.room_type,
    rb.min_x_mm,
    rb.max_x_mm,
    rb.min_y_mm,
    rb.max_y_mm,
    rb.storey                       AS storey_id,
    rb.extracted_from,
    rb.coordinate_frame,
    (rb.max_x_mm - rb.min_x_mm)    AS width_mm,
    (rb.max_y_mm - rb.min_y_mm)    AS depth_mm
FROM ad_room_boundary rb
WHERE rb.coordinate_frame IN (
        'IFC_GLOBAL_MM',
        'LOCAL_MM',
        'DRAWING_MM',
        'CONSTRAINT_SOLVED',
        'DERIVED_MM'
    )
    AND rb.extracted_from NOT IN ('PENDING','','TODO','UNKNOWN','GRID_DERIVED')
/* v_verified_room_boundary(room_id,building_type,room_type,min_x_mm,max_x_mm,min_y_mm,max_y_mm,storey_id,extracted_from,coordinate_frame,width_mm,depth_mm) */
/* v_verified_room_boundary(room_id,building_type,room_type,min_x_mm,max_x_mm,min_y_mm,max_y_mm,storey_id,extracted_from,coordinate_frame,width_mm,depth_mm) */
/* v_verified_room_boundary(room_id,building_type,room_type,min_x_mm,max_x_mm,min_y_mm,max_y_mm,storey_id,extracted_from,coordinate_frame,width_mm,depth_mm) */;
CREATE TABLE C_BPartner (
    C_BPartner_ID   TEXT PRIMARY KEY,
    Value           TEXT NOT NULL UNIQUE,
    Name            TEXT NOT NULL,
    Description     TEXT,
    IsActive        INTEGER DEFAULT 1
);
CREATE TABLE M_Product_Category_Line (
    M_Product_Category_Line_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
    M_Product_Category_ID      TEXT NOT NULL REFERENCES M_Product_Category(M_Product_Category_ID),
    Child_Category_ID  TEXT NOT NULL REFERENCES M_Product_Category(M_Product_Category_ID),
    Sequence              INTEGER NOT NULL DEFAULT 10,
    Value                 TEXT NOT NULL,
    Name                  TEXT NOT NULL,
    Z_Offset_Ratio        REAL DEFAULT 0.0,
    Z_Extent_Ratio        REAL DEFAULT 0.0,
    IsActive              INTEGER DEFAULT 1
, MinQty INTEGER NOT NULL DEFAULT 1, MaxQty INTEGER NOT NULL DEFAULT 1, num_units      INTEGER NOT NULL DEFAULT 0, storey_count   INTEGER NOT NULL DEFAULT 0, mirroring_rule TEXT    NOT NULL DEFAULT 'NONE', aabb_width_mm  INTEGER DEFAULT 0, aabb_depth_mm  INTEGER DEFAULT 0, aabb_height_mm INTEGER DEFAULT 0);
CREATE TABLE IF NOT EXISTS "m_bom_line" (
    bom_child_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    bom_id              TEXT NOT NULL,            -- Parent BOM

    -- Child reference: single FK replaces three-way split (NORM-2)
    child_product_id    TEXT REFERENCES M_Product(product_id),

    child_element_type  TEXT,                     -- Optional element_type filter
    child_name_pattern  TEXT,                     -- Optional LIKE pattern on element_name

    -- Role and ordering
    role                TEXT NOT NULL,
    qty_type            TEXT DEFAULT 'VARIABLE',
    qty                 INTEGER NOT NULL DEFAULT 1, -- FACTORIZE-v1: instances this type line expands to
    sequence            INTEGER DEFAULT 100,

    is_active           INTEGER DEFAULT 1,
    z_rule              TEXT DEFAULT NULL,
    dx                  REAL DEFAULT 0.0,
    dy                  REAL DEFAULT 0.0,
    dz                  REAL DEFAULT 0.0,
    rotation_rule       TEXT DEFAULT '0',
    fit_priority        INTEGER NOT NULL DEFAULT 20,
    min_space_mm        INTEGER NOT NULL DEFAULT 0,
    locator_ref         TEXT DEFAULT 'FLOAT',
    is_variance         INTEGER DEFAULT 0,
    anchor_face         TEXT DEFAULT 'BACK',
    layout_strategy     TEXT DEFAULT 'LINEAR',

    -- SpaceSize renamed: allocated_*_mm (null = derive from M_Product dims × 1000)
    allocated_width_mm  INTEGER DEFAULT 0,
    allocated_depth_mm  INTEGER DEFAULT 0,
    allocated_height_mm INTEGER DEFAULT 0,

    component_type      TEXT NOT NULL DEFAULT 'MAKE', storey         TEXT, element_ref    TEXT, ordinal        INTEGER DEFAULT 0, orientation    TEXT, material_name  TEXT, material_rgba  TEXT, entity_type TEXT DEFAULT 'D', M_BOM_ID INTEGER,

    FOREIGN KEY (bom_id) REFERENCES m_bom(bom_id)
);
CREATE TABLE M_AttributeSet (
    M_AttributeSet_ID   TEXT PRIMARY KEY,
    Name                TEXT NOT NULL,
    IsInstanceAttribute INTEGER NOT NULL DEFAULT 0,
    Description         TEXT
);
CREATE TABLE M_Product_Category (
    M_Product_Category_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    Value                 TEXT NOT NULL UNIQUE, -- old TEXT PK (e.g., 'RE', 'LIVING')
    Name                  TEXT NOT NULL,
    Description           TEXT,
    IFC_Class             TEXT,              -- IFC4 class name (leaf nodes only)
    SeqNo                 INTEGER DEFAULT 10,
    IsActive              INTEGER DEFAULT 1
);
CREATE TABLE _migration_log (
    migration_name TEXT PRIMARY KEY,
    applied_at     TEXT DEFAULT (datetime('now'))
);
CREATE TABLE C_Campaign (
    C_Campaign_ID   TEXT PRIMARY KEY,
    Value           TEXT NOT NULL UNIQUE,
    Name            TEXT NOT NULL,
    Description     TEXT,
    IsActive        INTEGER DEFAULT 1,
    entity_type     TEXT DEFAULT 'D'
);
CREATE TABLE AD_User (
    AD_User_ID      INTEGER PRIMARY KEY,
    Name            TEXT NOT NULL,
    Value           TEXT NOT NULL UNIQUE,
    Email           TEXT,
    IsActive        INTEGER DEFAULT 1,
    entity_type     TEXT DEFAULT 'D'
);
CREATE TABLE IF NOT EXISTS "m_bom" (
    M_BOM_ID          INTEGER PRIMARY KEY AUTOINCREMENT,
    bom_id            TEXT NOT NULL,        -- old TEXT PK preserved (Tier 2 migration)
    Value             TEXT,                 -- iDempiere SearchKey (= bom_id)
    bom_name          TEXT NOT NULL,
    description       TEXT,
    target_ifc_class  TEXT DEFAULT 'IfcElementAssembly',
    group_by          TEXT NOT NULL,
    is_active         INTEGER DEFAULT 1,
    bom_level         TEXT DEFAULT 'SET',
    bom_type          TEXT NOT NULL DEFAULT 'SET'
        CHECK(bom_type IN ('BUILDING', 'FLOOR', 'ROOM', 'SET', 'ITEM')),
    m_product_category_id TEXT DEFAULT NULL,
    doc_sub_type      TEXT DEFAULT NULL, -- STRUCTURAL: building identity (SH/DX/TE), FK to C_DocType.doc_sub_type
    seq_no            INTEGER DEFAULT 10,
    origin_x          REAL DEFAULT 0.0,
    origin_y          REAL DEFAULT 0.0,
    origin_z          REAL DEFAULT 0.0,
    entity_type       TEXT DEFAULT 'D',
    aabb_width_mm     INTEGER DEFAULT 0,
    aabb_depth_mm     INTEGER DEFAULT 0,
    aabb_height_mm    INTEGER DEFAULT 0
);
-- W018: DocBaseType + DocSubType dropped from C_DocType. doc_sub_type is the single FK.
CREATE TABLE IF NOT EXISTS "C_DocType" (
    C_DocType_ID   INTEGER PRIMARY KEY AUTOINCREMENT,
    Value          TEXT NOT NULL UNIQUE,    -- composite key (e.g., 'RE_SH')
    Name           TEXT NOT NULL,
    doc_sub_type   TEXT,                    -- FK to m_bom.doc_sub_type (was DocSubType)
    IsDefault      INTEGER DEFAULT 0,
    IsActive       INTEGER DEFAULT 1,
    Description    TEXT,
    ProjectName    TEXT,
    DSLContent     TEXT,
    OutputDbPath   TEXT,
    ReferenceDbPath TEXT,
    ExpectedElements INTEGER,
    Provenance     TEXT DEFAULT 'EXTRACTED',
    GeometryFailThreshold INTEGER DEFAULT 0,
    SeqNo          INTEGER DEFAULT 10,
    AabbWidthMm    REAL,
    AabbDepthMm    REAL,
    AabbHeightMm   REAL,
    C_Campaign_ID  TEXT REFERENCES C_Campaign(C_Campaign_ID),
    SalesRep_ID    INTEGER REFERENCES AD_User(AD_User_ID)
);
CREATE INDEX idx_threshold_check
    ON ad_check_threshold(check_id, occupancy_group, storey_type)
    ;
CREATE INDEX idx_ad_space_type_category
    ON ad_space_type(category)
    ;
CREATE INDEX idx_ad_space_type_alias_type
    ON ad_space_type_alias(space_type_id)
    ;
-- ============================================================================
-- S60: C_Order + C_OrderLine in compile DB (ERP Model Alignment)
-- Enables compiler to walk C_OrderLine tree instead of m_bom directly.
-- BomDropper populates these from m_bom before compilation.
-- ============================================================================
CREATE TABLE IF NOT EXISTS C_Order (
    C_Order_ID        INTEGER PRIMARY KEY AUTOINCREMENT,
    Value             TEXT NOT NULL UNIQUE,  -- old TEXT PK (e.g., 'RE_SH')
    C_DocType_ID      TEXT NOT NULL,         -- text FK (C_DocType.Value)
    Name              TEXT NOT NULL,
    DocStatus         TEXT NOT NULL DEFAULT 'DR'
                      CHECK(DocStatus IN ('DR','IP','AP','CO','VO')),
    aabb_width_mm     REAL NOT NULL DEFAULT 0,
    aabb_depth_mm     REAL NOT NULL DEFAULT 0,
    aabb_height_mm    REAL NOT NULL DEFAULT 0,
    Jurisdiction      TEXT,
    OccupancyClass    TEXT,
    IsActive          INTEGER NOT NULL DEFAULT 1,
    created           TEXT NOT NULL DEFAULT (datetime('now')),
    updated           TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE TABLE IF NOT EXISTS C_OrderLine (
    C_OrderLine_ID    INTEGER PRIMARY KEY AUTOINCREMENT,
    C_Order_ID        TEXT NOT NULL REFERENCES C_Order(Value),
    Parent_OrderLine_ID INTEGER REFERENCES C_OrderLine(C_OrderLine_ID),
    Line              INTEGER NOT NULL DEFAULT 10,
    family_ref        TEXT NOT NULL,
    host_type         TEXT NOT NULL,
    m_product_category_id TEXT,
    bom_child_id      INTEGER,
    dx                REAL NOT NULL DEFAULT 0,
    dy                REAL NOT NULL DEFAULT 0,
    dz                REAL NOT NULL DEFAULT 0,
    aabb_width_mm     REAL,
    aabb_depth_mm     REAL,
    aabb_height_mm    REAL,
    M_Product_ID      TEXT,
    Discipline        TEXT DEFAULT 'ARC',
    AD_Org_ID         INTEGER,
    Qty               INTEGER NOT NULL DEFAULT 1,
    locator_ref       TEXT,
    is_reference_class INTEGER DEFAULT 0,
    IsActive          INTEGER NOT NULL DEFAULT 1,
    created           TEXT NOT NULL DEFAULT (datetime('now')),
    updated           TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_orderline_order ON C_OrderLine(C_Order_ID);
CREATE INDEX IF NOT EXISTS idx_orderline_parent ON C_OrderLine(Parent_OrderLine_ID);
CREATE INDEX IF NOT EXISTS idx_orderline_family ON C_OrderLine(family_ref);
-- Tier 2: UNIQUE indexes for dual-key lookup (INTEGER PK tables)
CREATE UNIQUE INDEX IF NOT EXISTS idx_m_product_product_id ON M_Product(product_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_m_product_value ON M_Product(Value);
CREATE UNIQUE INDEX IF NOT EXISTS idx_m_bom_bom_id ON m_bom(bom_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_m_bom_value ON m_bom(Value);
