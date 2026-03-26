-- ============================================================
-- seed_dm_bom.sql — Populate DM_BOM.db for DemoHouse_2BR (GENERATIVE)
--
-- // Implementing GENERATIVE_HOUSE_SRS.md §4 — Witness: W-DM-PIPE-1
--
-- BOM hierarchy: BUILDING_DEMO_2BR → FLOOR_DEMO_GF → 5 ROOM BOMs → leaf elements
-- All products borrowed from component_library.db (SH extraction).
-- No IFC source, no extraction. Pure data authoring.
-- dx/dy/dz in METERS (pipeline convention), allocated_*_mm in MM.
--
-- Room layout (single storey, 11m × 7m footprint):
--   LIVING   (4×3.5m)  @ (0,3.5)    BEDROOM1 (5×3.5m)  @ (4,3.5)
--   KITCHEN  (4×3.5m)  @ (0,0)      BEDROOM2 (5×3.5m)  @ (4,0)
--   BATHROOM (2×1.5m)  @ (9,0)
-- ============================================================

-- ── Schema (minimal — full schema applied by schema_snapshot_bom.sql before this seed) ──

CREATE TABLE IF NOT EXISTS C_DocType (
    C_DocType_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    Value TEXT NOT NULL UNIQUE, Name TEXT NOT NULL,
    doc_sub_type TEXT,
    IsDefault INTEGER DEFAULT 0, IsActive INTEGER DEFAULT 1,
    Description TEXT, ProjectName TEXT, DSLContent TEXT,
    OutputDbPath TEXT, ReferenceDbPath TEXT,
    ExpectedElements INTEGER, Provenance TEXT DEFAULT 'EXTRACTED',
    GeometryFailThreshold INTEGER DEFAULT 0, SeqNo INTEGER DEFAULT 10,
    AabbWidthMm REAL, AabbDepthMm REAL, AabbHeightMm REAL,
    C_Campaign_ID TEXT, SalesRep_ID INTEGER
);

CREATE TABLE IF NOT EXISTS m_bom (
    M_BOM_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    bom_id TEXT NOT NULL UNIQUE, Value TEXT, Name TEXT,
    bom_name TEXT NOT NULL, bom_type TEXT NOT NULL DEFAULT 'SET',
    m_product_category_id TEXT, group_by TEXT DEFAULT 'default',
    aabb_width_mm INTEGER DEFAULT 0, aabb_depth_mm INTEGER DEFAULT 0,
    aabb_height_mm INTEGER DEFAULT 0, entity_type TEXT DEFAULT 'D',
    is_active INTEGER DEFAULT 1, description TEXT,
    target_ifc_class TEXT DEFAULT 'IfcElementAssembly',
    bom_level TEXT DEFAULT 'SET', doc_sub_type TEXT,
    seq_no INTEGER DEFAULT 10, origin_x REAL DEFAULT 0,
    origin_y REAL DEFAULT 0, origin_z REAL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS m_bom_line (
    bom_child_id INTEGER PRIMARY KEY AUTOINCREMENT,
    bom_id TEXT NOT NULL, child_product_id TEXT,
    child_element_type TEXT, child_name_pattern TEXT,
    role TEXT NOT NULL, qty_type TEXT DEFAULT 'VARIABLE',
    qty INTEGER DEFAULT 1, sequence INTEGER DEFAULT 100,
    is_active INTEGER DEFAULT 1, z_rule TEXT,
    dx REAL DEFAULT 0.0, dy REAL DEFAULT 0.0, dz REAL DEFAULT 0.0,
    rotation_rule TEXT DEFAULT '0', fit_priority INTEGER DEFAULT 20,
    min_space_mm INTEGER DEFAULT 0, locator_ref TEXT DEFAULT 'FLOAT',
    is_variance INTEGER DEFAULT 0, anchor_face TEXT DEFAULT 'BACK',
    layout_strategy TEXT DEFAULT 'LINEAR',
    allocated_width_mm INTEGER DEFAULT 0,
    allocated_depth_mm INTEGER DEFAULT 0,
    allocated_height_mm INTEGER DEFAULT 0,
    component_type TEXT DEFAULT 'MAKE', storey TEXT,
    element_ref TEXT, ordinal INTEGER DEFAULT 0,
    orientation TEXT, material_name TEXT
);

CREATE TABLE IF NOT EXISTS ad_sysconfig (
    config_key TEXT PRIMARY KEY, config_value TEXT
);

-- ── C_DocType (W018: DocBaseType dropped, doc_sub_type is FK to m_bom) ──
INSERT OR REPLACE INTO C_DocType (
    Value, Name, doc_sub_type, IsActive,
    ProjectName, Provenance, SeqNo,
    OutputDbPath, ExpectedElements, GeometryFailThreshold,
    AabbWidthMm, AabbDepthMm, AabbHeightMm
) VALUES (
    'RE_DM', 'Demo House 2BR', 'DM', 1,
    'DemoHouse_2BR', 'GENERATIVE', 10,
    'DAGCompiler/lib/output/demohouse_2br.db', 60, 25,
    11000, 7000, 2800
);

-- ── BUILDING BOM ──
INSERT OR REPLACE INTO m_bom (bom_id, bom_name, bom_type, m_product_category_id, group_by,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm, doc_sub_type, seq_no)
VALUES ('BUILDING_DEMO_2BR', 'Demo House 2BR', 'BUILDING', 'RE', 'building',
        11000, 7000, 2800, 'DM', 10);

-- ── FLOOR BOM ──
INSERT OR REPLACE INTO m_bom (bom_id, bom_name, bom_type, m_product_category_id, group_by,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm, seq_no)
VALUES ('FLOOR_DEMO_GF', 'Ground Floor', 'FLOOR', 'GF', 'storey',
        11000, 7000, 2800, 10);

-- BUILDING → FLOOR link
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence)
VALUES ('BUILDING_DEMO_2BR', 'FLOOR_DEMO_GF', 'MAKE', 'GROUND_FLOOR', 10);

-- ── ROOF BOM ──
INSERT OR REPLACE INTO m_bom (bom_id, bom_name, bom_type, m_product_category_id, group_by,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm, seq_no)
VALUES ('ROOF_DEMO', 'Roof', 'FLOOR', 'RF', 'roof',
        11000, 7000, 500, 20);

-- BUILDING → ROOF link
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz)
VALUES ('BUILDING_DEMO_2BR', 'ROOF_DEMO', 'MAKE', 'ROOF', 20,
        0.0, 0.0, 2.8);

-- Roof leaf element
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOF_DEMO', 'Basic Roof:Roof_Flat-4Felt-150Ins-50Scr-150Conc-12Plr', 'LEAF', 'IfcRoof', 10,
        0, 0, 0, 11000, 7000, 500);

-- ══════════════════════════════════════════════════════════════
-- ROOM BOMs — 5 rooms, each with walls + door + window + slab + furniture + MEP
-- Products are REAL IDs from component_library.db (SH/TE extraction)
-- ══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────────────────────────
-- ROOM_DEMO_LI (LIVING) 4000×3500×2800mm @ (0.0, 3.5)
-- Exterior: N, W
-- ────────────────────────────────────────────────────────────
INSERT OR REPLACE INTO m_bom (bom_id, bom_name, bom_type, m_product_category_id, group_by,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm, origin_x, origin_y, origin_z)
VALUES ('ROOM_DEMO_LI', 'LIVING', 'ROOM', 'LIVING', 'ROOM',
        4000, 3500, 2800, 0.0, 3.5, 0.0);

INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('FLOOR_DEMO_GF', 'ROOM_DEMO_LI', 'MAKE', 'LIVING', 10,
        0.0, 3.5, 0.0, 4000, 3500, 2800);
-- South wall (int)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_LI', 'Basic Wall:Wall-Partn_12P-70MStd-12P', 'LEAF', 'IfcWall', 10,
        0, 0, 0, 4000, 290, 2800);
-- North wall (ext)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_LI', 'Basic Wall:Wall-Ext_102Bwk-75Ins-100LBlk-12P', 'LEAF', 'IfcWall', 20,
        0, 3.21, 0, 4000, 290, 2800);
-- West wall (ext)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_LI', 'Basic Wall:Wall-Ext_102Bwk-75Ins-100LBlk-12P', 'LEAF', 'IfcWall', 30,
        0, 0, 0, 290, 3500, 2800);
-- East wall (int)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_LI', 'Basic Wall:Wall-Partn_12P-70MStd-12P', 'LEAF', 'IfcWall', 40,
        3.71, 0, 0, 290, 3500, 2800);
-- Door
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_LI', 'Doors_IntSgl:810x2110mm', 'LEAF', 'IfcDoor', 50,
        1.595, 0, 0, 810, 200, 2110);
-- Window
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_LI', 'Windows_Sgl_Plain:1810x1210mm', 'LEAF', 'IfcWindow', 60,
        1.095, 3.3, 0.9, 1810, 200, 1210);
-- Slab
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_LI', 'Floor:Floor-Grnd-Susp_65Scr-80Ins-100Blk-75PC', 'LEAF', 'IfcSlab', 70,
        0, 0, -0.15, 4000, 3500, 150);
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_LI', 'Furniture_Couch_Viper:2290x950x340mm', 'LEAF', 'IfcFurnishingElement', 80,
        0.5, 0.5, 0, 2290, 950, 340);
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_LI', 'Furniture_Table_Coffee_1:1200x550x450mm', 'LEAF', 'IfcFurnishingElement', 90,
        1.0, 1.5, 0, 1200, 550, 450);
-- Light
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_LI', 'M_Sconce Light - Sphere:100W - 120V:100W - 120V', 'LEAF', 'IfcFlowTerminal', 100,
        2.0, 1.75, 2.75, 200, 200, 200);
-- Outlet
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_LI', 'M_Duplex Receptacle:Duplex Receptacle:Duplex Receptacle', 'LEAF', 'IfcFlowTerminal', 110,
        0.3, 0, 0.3, 100, 50, 100);
-- Switch
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_LI', 'M_Lighting Switches:Single Pole:Single Pole', 'LEAF', 'IfcFlowTerminal', 120,
        1.5, 0, 1.1, 80, 50, 120);

-- ────────────────────────────────────────────────────────────
-- ROOM_DEMO_KT (KITCHEN) 4000×3500×2800mm @ (0.0, 0.0)
-- Exterior: S, W
-- ────────────────────────────────────────────────────────────
INSERT OR REPLACE INTO m_bom (bom_id, bom_name, bom_type, m_product_category_id, group_by,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm, origin_x, origin_y, origin_z)
VALUES ('ROOM_DEMO_KT', 'KITCHEN', 'ROOM', 'KITCHEN', 'ROOM',
        4000, 3500, 2800, 0.0, 0.0, 0.0);

INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('FLOOR_DEMO_GF', 'ROOM_DEMO_KT', 'MAKE', 'KITCHEN', 20,
        0.0, 0.0, 0.0, 4000, 3500, 2800);
-- South wall (ext)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_KT', 'Basic Wall:Wall-Ext_102Bwk-75Ins-100LBlk-12P', 'LEAF', 'IfcWall', 10,
        0, 0, 0, 4000, 290, 2800);
-- North wall (int)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_KT', 'Basic Wall:Wall-Partn_12P-70MStd-12P', 'LEAF', 'IfcWall', 20,
        0, 3.21, 0, 4000, 290, 2800);
-- West wall (ext)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_KT', 'Basic Wall:Wall-Ext_102Bwk-75Ins-100LBlk-12P', 'LEAF', 'IfcWall', 30,
        0, 0, 0, 290, 3500, 2800);
-- East wall (int)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_KT', 'Basic Wall:Wall-Partn_12P-70MStd-12P', 'LEAF', 'IfcWall', 40,
        3.71, 0, 0, 290, 3500, 2800);
-- Door
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_KT', 'Doors_IntSgl:810x2110mm', 'LEAF', 'IfcDoor', 50,
        1.595, 0, 0, 810, 200, 2110);
-- Window
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_KT', 'Windows_Sgl_Plain:1810x1210mm', 'LEAF', 'IfcWindow', 60,
        1.095, 0, 0.9, 1810, 200, 1210);
-- Slab
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_KT', 'Floor:Floor-Grnd-Susp_65Scr-80Ins-100Blk-75PC', 'LEAF', 'IfcSlab', 70,
        0, 0, -0.15, 4000, 3500, 150);
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_KT', 'Furniture_Table_Dining_w-Chairs_Rectangular:2000x1000x750mm_w-6_Seats', 'LEAF', 'IfcFurnishingElement', 80,
        1.0, 1.0, 0, 2000, 1000, 750);
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_KT', 'M_Refrigerator:850 x 760mm:850 x 760mm', 'LEAF', 'IfcFlowTerminal', 90,
        0.1, 0.1, 0, 850, 760, 1800);
-- Light
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_KT', 'M_Sconce Light - Sphere:100W - 120V:100W - 120V', 'LEAF', 'IfcFlowTerminal', 100,
        2.0, 1.75, 2.75, 200, 200, 200);
-- Outlet
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_KT', 'M_Duplex Receptacle:Duplex Receptacle:Duplex Receptacle', 'LEAF', 'IfcFlowTerminal', 110,
        0.3, 0, 0.3, 100, 50, 100);
-- Switch
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_KT', 'M_Lighting Switches:Single Pole:Single Pole', 'LEAF', 'IfcFlowTerminal', 120,
        1.5, 0, 1.1, 80, 50, 120);

-- ────────────────────────────────────────────────────────────
-- ROOM_DEMO_BD1 (BEDROOM) 5000×3500×2800mm @ (4.0, 3.5)
-- Exterior: E, N
-- ────────────────────────────────────────────────────────────
INSERT OR REPLACE INTO m_bom (bom_id, bom_name, bom_type, m_product_category_id, group_by,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm, origin_x, origin_y, origin_z)
VALUES ('ROOM_DEMO_BD1', 'BEDROOM', 'ROOM', 'BEDROOM', 'ROOM',
        5000, 3500, 2800, 4.0, 3.5, 0.0);

INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('FLOOR_DEMO_GF', 'ROOM_DEMO_BD1', 'MAKE', 'BEDROOM', 30,
        4.0, 3.5, 0.0, 5000, 3500, 2800);
-- South wall (int)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD1', 'Basic Wall:Wall-Partn_12P-70MStd-12P', 'LEAF', 'IfcWall', 10,
        0, 0, 0, 5000, 290, 2800);
-- North wall (ext)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD1', 'Basic Wall:Wall-Ext_102Bwk-75Ins-100LBlk-12P', 'LEAF', 'IfcWall', 20,
        0, 3.21, 0, 5000, 290, 2800);
-- West wall (int)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD1', 'Basic Wall:Wall-Partn_12P-70MStd-12P', 'LEAF', 'IfcWall', 30,
        0, 0, 0, 290, 3500, 2800);
-- East wall (ext)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD1', 'Basic Wall:Wall-Ext_102Bwk-75Ins-100LBlk-12P', 'LEAF', 'IfcWall', 40,
        4.71, 0, 0, 290, 3500, 2800);
-- Door
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD1', 'Doors_IntSgl:810x2110mm', 'LEAF', 'IfcDoor', 50,
        2.095, 0, 0, 810, 200, 2110);
-- Window
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD1', 'Windows_Sgl_Plain:1810x1210mm', 'LEAF', 'IfcWindow', 60,
        1.595, 3.3, 0.9, 1810, 200, 1210);
-- Slab
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD1', 'Floor:Floor-Grnd-Susp_65Scr-80Ins-100Blk-75PC', 'LEAF', 'IfcSlab', 70,
        0, 0, -0.15, 5000, 3500, 150);
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD1', 'Furniture_Bed_1:1525x2007x355mm-Queen', 'LEAF', 'IfcFurnishingElement', 80,
        0.5, 0.5, 0, 1525, 2007, 355);
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD1', 'Furniture_Desk:1525x762mm', 'LEAF', 'IfcFurnishingElement', 90,
        2.5, 0.5, 0, 1525, 762, 750);
-- Light
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD1', 'M_Sconce Light - Sphere:100W - 120V:100W - 120V', 'LEAF', 'IfcFlowTerminal', 100,
        2.5, 1.75, 2.75, 200, 200, 200);
-- Outlet
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD1', 'M_Duplex Receptacle:Duplex Receptacle:Duplex Receptacle', 'LEAF', 'IfcFlowTerminal', 110,
        0.3, 0, 0.3, 100, 50, 100);
-- Switch
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD1', 'M_Lighting Switches:Single Pole:Single Pole', 'LEAF', 'IfcFlowTerminal', 120,
        2.0, 0, 1.1, 80, 50, 120);

-- ────────────────────────────────────────────────────────────
-- ROOM_DEMO_BD2 (BEDROOM) 5000×3500×2800mm @ (4.0, 0.0)
-- Exterior: E, S
-- ────────────────────────────────────────────────────────────
INSERT OR REPLACE INTO m_bom (bom_id, bom_name, bom_type, m_product_category_id, group_by,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm, origin_x, origin_y, origin_z)
VALUES ('ROOM_DEMO_BD2', 'BEDROOM', 'ROOM', 'BEDROOM', 'ROOM',
        5000, 3500, 2800, 4.0, 0.0, 0.0);

INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('FLOOR_DEMO_GF', 'ROOM_DEMO_BD2', 'MAKE', 'BEDROOM', 40,
        4.0, 0.0, 0.0, 5000, 3500, 2800);
-- South wall (ext)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD2', 'Basic Wall:Wall-Ext_102Bwk-75Ins-100LBlk-12P', 'LEAF', 'IfcWall', 10,
        0, 0, 0, 5000, 290, 2800);
-- North wall (int)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD2', 'Basic Wall:Wall-Partn_12P-70MStd-12P', 'LEAF', 'IfcWall', 20,
        0, 3.21, 0, 5000, 290, 2800);
-- West wall (int)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD2', 'Basic Wall:Wall-Partn_12P-70MStd-12P', 'LEAF', 'IfcWall', 30,
        0, 0, 0, 290, 3500, 2800);
-- East wall (ext)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD2', 'Basic Wall:Wall-Ext_102Bwk-75Ins-100LBlk-12P', 'LEAF', 'IfcWall', 40,
        4.71, 0, 0, 290, 3500, 2800);
-- Door
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD2', 'Doors_IntSgl:810x2110mm', 'LEAF', 'IfcDoor', 50,
        2.095, 0, 0, 810, 200, 2110);
-- Window
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD2', 'Windows_Sgl_Plain:1810x1210mm', 'LEAF', 'IfcWindow', 60,
        1.595, 0, 0.9, 1810, 200, 1210);
-- Slab
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD2', 'Floor:Floor-Grnd-Susp_65Scr-80Ins-100Blk-75PC', 'LEAF', 'IfcSlab', 70,
        0, 0, -0.15, 5000, 3500, 150);
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD2', 'Furniture_Bed_1:1525x2007x355mm-Queen', 'LEAF', 'IfcFurnishingElement', 80,
        0.5, 0.5, 0, 1525, 2007, 355);
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD2', 'Furniture_Desk:1525x762mm', 'LEAF', 'IfcFurnishingElement', 90,
        2.5, 0.5, 0, 1525, 762, 750);
-- Light
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD2', 'M_Sconce Light - Sphere:100W - 120V:100W - 120V', 'LEAF', 'IfcFlowTerminal', 100,
        2.5, 1.75, 2.75, 200, 200, 200);
-- Outlet
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD2', 'M_Duplex Receptacle:Duplex Receptacle:Duplex Receptacle', 'LEAF', 'IfcFlowTerminal', 110,
        0.3, 0, 0.3, 100, 50, 100);
-- Switch
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BD2', 'M_Lighting Switches:Single Pole:Single Pole', 'LEAF', 'IfcFlowTerminal', 120,
        2.0, 0, 1.1, 80, 50, 120);

-- ────────────────────────────────────────────────────────────
-- ROOM_DEMO_BT (BATHROOM) 2000×1500×2800mm @ (9.0, 0.0)
-- Exterior: E, S
-- ────────────────────────────────────────────────────────────
INSERT OR REPLACE INTO m_bom (bom_id, bom_name, bom_type, m_product_category_id, group_by,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm, origin_x, origin_y, origin_z)
VALUES ('ROOM_DEMO_BT', 'BATHROOM', 'ROOM', 'BATHROOM', 'ROOM',
        2000, 1500, 2800, 9.0, 0.0, 0.0);

INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('FLOOR_DEMO_GF', 'ROOM_DEMO_BT', 'MAKE', 'BATHROOM', 50,
        9.0, 0.0, 0.0, 2000, 1500, 2800);
-- South wall (ext)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BT', 'Basic Wall:Wall-Ext_102Bwk-75Ins-100LBlk-12P', 'LEAF', 'IfcWall', 10,
        0, 0, 0, 2000, 290, 2800);
-- North wall (int)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BT', 'Basic Wall:Wall-Partn_12P-70MStd-12P', 'LEAF', 'IfcWall', 20,
        0, 1.21, 0, 2000, 290, 2800);
-- West wall (int)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BT', 'Basic Wall:Wall-Partn_12P-70MStd-12P', 'LEAF', 'IfcWall', 30,
        0, 0, 0, 290, 1500, 2800);
-- East wall (ext)
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BT', 'Basic Wall:Wall-Ext_102Bwk-75Ins-100LBlk-12P', 'LEAF', 'IfcWall', 40,
        1.71, 0, 0, 290, 1500, 2800);
-- Door
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BT', 'Doors_IntSgl:810x2110mm', 'LEAF', 'IfcDoor', 50,
        0.595, 0, 0, 810, 200, 2110);
-- Window
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BT', 'Windows_Sgl_Plain:1810x1210mm', 'LEAF', 'IfcWindow', 60,
        0.095, 0, 0.9, 1810, 200, 1210);
-- Slab
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BT', 'Floor:Floor-Grnd-Susp_65Scr-80Ins-100Blk-75PC', 'LEAF', 'IfcSlab', 70,
        0, 0, -0.15, 2000, 1500, 150);
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BT', 'M_Bath Tub:1525 mmx760 mm - Private:1525 mmx760 mm - Private', 'LEAF', 'IfcFlowTerminal', 80,
        0.2, 0.2, 0, 1525, 760, 500);
-- Light
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BT', 'M_Sconce Light - Sphere:100W - 120V:100W - 120V', 'LEAF', 'IfcFlowTerminal', 90,
        1.0, 0.75, 2.75, 200, 200, 200);
-- Outlet
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BT', 'M_Duplex Receptacle:Duplex Receptacle:Duplex Receptacle', 'LEAF', 'IfcFlowTerminal', 100,
        0.3, 0, 0.3, 100, 50, 100);
-- Switch
INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence,
    dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES ('ROOM_DEMO_BT', 'M_Lighting Switches:Single Pole:Single Pole', 'LEAF', 'IfcFlowTerminal', 110,
        0.5, 0, 1.1, 80, 50, 120);

-- ── Expected element count ──
INSERT OR REPLACE INTO ad_sysconfig (config_key, config_value) VALUES ('EXPECTED_ELEMENTS', '60');

-- ══════════════════════════════════════════════════════════════
-- METADATA — ad_building_grid, ad_room_boundary, ad_wall_face, ad_space_type, ad_wall_type
-- Required by MetadataValidator during compilation.
-- Tables created by schema_snapshot_bom.sql (applied before this seed).
-- ══════════════════════════════════════════════════════════════

INSERT OR REPLACE INTO ad_building_grid (building_type, axis, grid_label, position_mm) VALUES
    ('DemoHouse_2BR', 'X', 'A', 0),
    ('DemoHouse_2BR', 'X', 'B', 4000),
    ('DemoHouse_2BR', 'X', 'C', 9000),
    ('DemoHouse_2BR', 'X', 'D', 11000),
    ('DemoHouse_2BR', 'Y', '1', 0),
    ('DemoHouse_2BR', 'Y', '2', 3500),
    ('DemoHouse_2BR', 'Y', '3', 7000);

INSERT OR REPLACE INTO ad_room_boundary (building_type, storey, room_name, room_type,
    grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm,
    min_x_mm, max_x_mm, min_y_mm, max_y_mm) VALUES
    ('DemoHouse_2BR', 'Ground Floor', 'LIVING',   'LIVING',   'A','B','1','2', 0.0,    0, 4000, 3500, 7000),
    ('DemoHouse_2BR', 'Ground Floor', 'KITCHEN',  'KITCHEN',  'A','B','1','2', 0.0,    0, 4000,    0, 3500),
    ('DemoHouse_2BR', 'Ground Floor', 'BEDROOM1', 'BEDROOM',  'A','B','1','2', 0.0, 4000, 9000, 3500, 7000),
    ('DemoHouse_2BR', 'Ground Floor', 'BEDROOM2', 'BEDROOM',  'A','B','1','2', 0.0, 4000, 9000,    0, 3500),
    ('DemoHouse_2BR', 'Ground Floor', 'BATHROOM', 'BATHROOM', 'A','B','1','2', 0.0, 9000,11000,    0, 1500);

INSERT OR REPLACE INTO ad_space_type (space_type_id, category, omniclass_code, wall_rule) VALUES
    ('LIVING',   'HABITABLE', '13-21 11 00', 'ENCLOSED'),
    ('KITCHEN',  'SERVICE',   '13-31 11 00', 'ENCLOSED'),
    ('BATHROOM', 'SERVICE',   '13-31 21 00', 'ENCLOSED');
INSERT OR REPLACE INTO ad_space_type (space_type_id, category, omniclass_code, wall_rule, is_sleeping_room) VALUES
    ('BEDROOM',  'HABITABLE', '13-21 21 00', 'ENCLOSED', 1);

INSERT OR REPLACE INTO ad_wall_type (wall_type_id, category, construction, total_mm, is_exterior) VALUES
    ('EXT', 'EXTERIOR', 'BRICK_BLOCK', 200, 1),
    ('INT', 'INTERIOR', 'STUD_FRAME',  100, 0);

INSERT OR REPLACE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior) VALUES
    ('DemoHouse_2BR', 'Ground Floor', 'LIVING',   'NORTH', 'EXT', 1),
    ('DemoHouse_2BR', 'Ground Floor', 'LIVING',   'SOUTH', 'INT', 0),
    ('DemoHouse_2BR', 'Ground Floor', 'LIVING',   'WEST',  'EXT', 1),
    ('DemoHouse_2BR', 'Ground Floor', 'LIVING',   'EAST',  'INT', 0),
    ('DemoHouse_2BR', 'Ground Floor', 'KITCHEN',  'NORTH', 'INT', 0),
    ('DemoHouse_2BR', 'Ground Floor', 'KITCHEN',  'SOUTH', 'EXT', 1),
    ('DemoHouse_2BR', 'Ground Floor', 'KITCHEN',  'WEST',  'EXT', 1),
    ('DemoHouse_2BR', 'Ground Floor', 'KITCHEN',  'EAST',  'INT', 0),
    ('DemoHouse_2BR', 'Ground Floor', 'BEDROOM1', 'NORTH', 'EXT', 1),
    ('DemoHouse_2BR', 'Ground Floor', 'BEDROOM1', 'SOUTH', 'INT', 0),
    ('DemoHouse_2BR', 'Ground Floor', 'BEDROOM1', 'WEST',  'INT', 0),
    ('DemoHouse_2BR', 'Ground Floor', 'BEDROOM1', 'EAST',  'EXT', 1),
    ('DemoHouse_2BR', 'Ground Floor', 'BEDROOM2', 'NORTH', 'INT', 0),
    ('DemoHouse_2BR', 'Ground Floor', 'BEDROOM2', 'SOUTH', 'EXT', 1),
    ('DemoHouse_2BR', 'Ground Floor', 'BEDROOM2', 'WEST',  'INT', 0),
    ('DemoHouse_2BR', 'Ground Floor', 'BEDROOM2', 'EAST',  'EXT', 1),
    ('DemoHouse_2BR', 'Ground Floor', 'BATHROOM', 'NORTH', 'INT', 0),
    ('DemoHouse_2BR', 'Ground Floor', 'BATHROOM', 'SOUTH', 'EXT', 1),
    ('DemoHouse_2BR', 'Ground Floor', 'BATHROOM', 'WEST',  'INT', 0),
    ('DemoHouse_2BR', 'Ground Floor', 'BATHROOM', 'EAST',  'EXT', 1);
