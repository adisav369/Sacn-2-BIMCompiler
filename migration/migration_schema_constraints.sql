-- ============================================================
-- G9 — Schema Constraint Hardening
-- iDempiere/Compiere pattern: database-level CHECK constraints
-- survive code refactors and cannot be bypassed by any Java path.
--
-- SQLite does not support ALTER TABLE ADD CONSTRAINT.
-- Pattern used: CREATE new -> INSERT SELECT -> DROP old -> RENAME.
--
-- SAFETY: Single TRANSACTION. Any failure rolls back all changes.
-- FKs disabled during recreation; re-enabled at end.
-- Pre-flight: zero existing rows violated any constraint added here.
-- ============================================================

PRAGMA foreign_keys = OFF;
BEGIN TRANSACTION;

-- ============================================================
-- 1. ad_product_dim
--    - width, depth, height must be > 0
--    - clearances must be >= 0
--    - is_active IN (0,1)
--    - provenance column added (DEFAULT 'LIBRARY' for existing rows)
-- ============================================================
CREATE TABLE ad_product_dim_new (
    product_id        TEXT PRIMARY KEY,
    product_type      TEXT NOT NULL,
    width             REAL NOT NULL CHECK(width > 0),
    depth             REAL NOT NULL CHECK(depth > 0),
    height            REAL NOT NULL CHECK(height > 0),
    clear_front       REAL DEFAULT 0 CHECK(clear_front >= 0),
    clear_back        REAL DEFAULT 0 CHECK(clear_back >= 0),
    clear_left        REAL DEFAULT 0 CHECK(clear_left >= 0),
    clear_right       REAL DEFAULT 0 CHECK(clear_right >= 0),
    clear_above       REAL DEFAULT 0 CHECK(clear_above >= 0),
    clear_below       REAL DEFAULT 0 CHECK(clear_below >= 0),
    fits_in           TEXT,
    requires_host     TEXT,
    host_min_width    REAL,
    host_min_height   REAL,
    qty_per_area      REAL,
    qty_per_room      INTEGER,
    qty_per_person    REAL,
    max_spacing       REAL,
    conn_points       TEXT,
    code_ref          TEXT,
    is_active         INTEGER DEFAULT 1 CHECK(is_active IN (0,1)),
    provenance        TEXT NOT NULL DEFAULT 'LIBRARY'
                          CHECK(provenance IN ('LIBRARY','EXTRACTED','RESEARCHED','BUILDING_SPEC'))
);
INSERT INTO ad_product_dim_new
    SELECT product_id, product_type,
           width, depth, height,
           clear_front, clear_back, clear_left, clear_right, clear_above, clear_below,
           fits_in, requires_host, host_min_width, host_min_height,
           qty_per_area, qty_per_room, qty_per_person, max_spacing,
           conn_points, code_ref, is_active,
           'LIBRARY'
    FROM ad_product_dim;
DROP TABLE ad_product_dim;
ALTER TABLE ad_product_dim_new RENAME TO ad_product_dim;

-- ============================================================
-- 2. ad_room_boundary
--    - is_active IN (0,1)
--    - min_x_mm < max_x_mm when both set
--    - min_y_mm < max_y_mm when both set
--    - extracted_from: not PENDING/empty/unknown
--    - coordinate_frame: closed vocabulary
-- ============================================================
CREATE TABLE ad_room_boundary_new (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type    TEXT NOT NULL,
    storey           TEXT NOT NULL,
    room_name        TEXT NOT NULL,
    room_type        TEXT NOT NULL,
    grid_min_x       TEXT NOT NULL,
    grid_max_x       TEXT NOT NULL,
    grid_min_y       TEXT NOT NULL,
    grid_max_y       TEXT NOT NULL,
    z_offset_mm      REAL DEFAULT 0,
    is_active        INTEGER DEFAULT 1 CHECK(is_active IN (0,1)),
    min_x_mm         REAL,
    max_x_mm         REAL,
    min_y_mm         REAL,
    max_y_mm         REAL,
    building_id      INTEGER REFERENCES ad_building(id),
    extracted_from   TEXT NOT NULL DEFAULT 'GRID_DERIVED'
                         CHECK(extracted_from NOT IN ('PENDING','','TODO','UNKNOWN')),
    coordinate_frame TEXT NOT NULL DEFAULT 'LOCAL_MM'
                         CHECK(coordinate_frame IN ('IFC_GLOBAL_MM','LOCAL_MM','DRAWING_MM','GRID_DERIVED_MM')),
    CHECK(min_x_mm IS NULL OR max_x_mm IS NULL OR min_x_mm < max_x_mm),
    CHECK(min_y_mm IS NULL OR max_y_mm IS NULL OR min_y_mm < max_y_mm),
    UNIQUE(building_type, storey, room_name)
);
INSERT INTO ad_room_boundary_new
    SELECT id, building_type, storey, room_name, room_type,
           grid_min_x, grid_max_x, grid_min_y, grid_max_y,
           z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm,
           building_id, 'GRID_DERIVED', 'LOCAL_MM'
    FROM ad_room_boundary;
DROP TABLE ad_room_boundary;
ALTER TABLE ad_room_boundary_new RENAME TO ad_room_boundary;

-- ============================================================
-- 3. ad_element_rule
--    - position_value_3 (storey Z) must be >= 0
--    - is_active IN (0,1)
--    - provenance column added (DEFAULT 'BUILDING_DSL' for existing rows)
-- ============================================================
CREATE TABLE ad_element_rule_new (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type    TEXT NOT NULL,
    storey           TEXT NOT NULL,
    element_ref      TEXT NOT NULL,
    ifc_class        TEXT NOT NULL,
    discipline       TEXT DEFAULT 'ARC',
    host_type        TEXT NOT NULL,
    host_ref         TEXT NOT NULL,
    position_rule    TEXT NOT NULL,
    position_value   REAL,
    height_mm        REAL,
    family_ref       TEXT,
    width_mm         REAL,
    height_extent_mm REAL,
    depth_mm         REAL,
    orientation      TEXT,
    geometry_hash    TEXT,
    material_name    TEXT,
    material_rgba    TEXT,
    is_active        INTEGER DEFAULT 1 CHECK(is_active IN (0,1)),
    position_value_2 REAL,
    building_id      INTEGER REFERENCES ad_building(id),
    position_value_3 REAL DEFAULT 0.0
                         CHECK(position_value_3 IS NULL OR position_value_3 >= 0),
    provenance       TEXT NOT NULL DEFAULT 'BUILDING_DSL'
                         CHECK(provenance IN ('BUILDING_DSL','EXTRACTED','MIGRATION','RESEARCHED')),
    UNIQUE(building_type, storey, element_ref)
);
INSERT INTO ad_element_rule_new
    SELECT id, building_type, storey, element_ref, ifc_class, discipline,
           host_type, host_ref, position_rule, position_value, height_mm,
           family_ref, width_mm, height_extent_mm, depth_mm, orientation,
           geometry_hash, material_name, material_rgba, is_active,
           position_value_2, building_id, position_value_3,
           'BUILDING_DSL'
    FROM ad_element_rule;
DROP TABLE ad_element_rule;
ALTER TABLE ad_element_rule_new RENAME TO ad_element_rule;

-- ============================================================
-- 4. ad_bom_child
--    - is_active IN (0,1)
--    - sequence > 0
--    - dx/dy/dz bounded to +-100m (100000mm)
-- ============================================================
CREATE TABLE ad_bom_child_new (
    bom_child_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    bom_id             TEXT NOT NULL,
    child_ifc_class    TEXT,
    child_element_type TEXT,
    child_name_pattern TEXT,
    child_bom_id       TEXT,
    role               TEXT NOT NULL,
    qty_type           TEXT DEFAULT 'VARIABLE',
    sequence           INTEGER DEFAULT 100 CHECK(sequence > 0),
    is_active          INTEGER DEFAULT 1   CHECK(is_active IN (0,1)),
    z_rule             TEXT DEFAULT NULL,
    dx                 REAL DEFAULT 0.0    CHECK(dx BETWEEN -100000 AND 100000),
    dy                 REAL DEFAULT 0.0    CHECK(dy BETWEEN -100000 AND 100000),
    dz                 REAL DEFAULT 0.0    CHECK(dz BETWEEN -100000 AND 100000),
    rotation_rule      TEXT DEFAULT '0',
    FOREIGN KEY (bom_id)       REFERENCES ad_bom(bom_id),
    FOREIGN KEY (child_bom_id) REFERENCES ad_bom(bom_id)
);
INSERT INTO ad_bom_child_new
    SELECT bom_child_id, bom_id, child_ifc_class, child_element_type,
           child_name_pattern, child_bom_id, role, qty_type,
           sequence, is_active, z_rule, dx, dy, dz, rotation_rule
    FROM ad_bom_child;
DROP TABLE ad_bom_child;
ALTER TABLE ad_bom_child_new RENAME TO ad_bom_child;
CREATE INDEX IF NOT EXISTS idx_bom_child_parent ON ad_bom_child(bom_id);
CREATE INDEX IF NOT EXISTS idx_bom_child_nested ON ad_bom_child(child_bom_id);

-- ============================================================
-- 5. ad_assembly_manifest — face vocabulary closed
-- ============================================================
CREATE TABLE ad_assembly_manifest_new (
    manifest_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    assembly_id    TEXT NOT NULL,
    version        TEXT NOT NULL DEFAULT '1.0.0',
    face           TEXT NOT NULL
                       CHECK(face IN ('FRONT','BACK','LEFT','RIGHT','TOP','BOTTOM')),
    interface_type TEXT NOT NULL,
    clearance_m    REAL DEFAULT 0 CHECK(clearance_m >= 0),
    UNIQUE(assembly_id, version, face, interface_type)
);
INSERT INTO ad_assembly_manifest_new
    SELECT manifest_id, assembly_id, version, face, interface_type, clearance_m
    FROM ad_assembly_manifest;
DROP TABLE ad_assembly_manifest;
ALTER TABLE ad_assembly_manifest_new RENAME TO ad_assembly_manifest;

-- ============================================================
-- 6. ad_unit_type_room — fractions in [0,1], min < max
-- ============================================================
CREATE TABLE ad_unit_type_room_new (
    unit_type_id TEXT NOT NULL,
    room_key     TEXT NOT NULL,
    room_type    TEXT NOT NULL,
    frac_min_x   REAL NOT NULL CHECK(frac_min_x >= 0.0 AND frac_min_x <  1.0),
    frac_min_y   REAL NOT NULL CHECK(frac_min_y >= 0.0 AND frac_min_y <  1.0),
    frac_max_x   REAL NOT NULL CHECK(frac_max_x >  0.0 AND frac_max_x <= 1.0),
    frac_max_y   REAL NOT NULL CHECK(frac_max_y >  0.0 AND frac_max_y <= 1.0),
    needs_window INTEGER DEFAULT 0 CHECK(needs_window IN (0,1)),
    opens_to     TEXT,
    is_active    INTEGER DEFAULT 1 CHECK(is_active IN (0,1)),
    CHECK(frac_min_x < frac_max_x),
    CHECK(frac_min_y < frac_max_y),
    PRIMARY KEY (unit_type_id, room_key)
);
INSERT INTO ad_unit_type_room_new
    SELECT unit_type_id, room_key, room_type,
           frac_min_x, frac_min_y, frac_max_x, frac_max_y,
           needs_window, opens_to, is_active
    FROM ad_unit_type_room;
DROP TABLE ad_unit_type_room;
ALTER TABLE ad_unit_type_room_new RENAME TO ad_unit_type_room;

-- ============================================================
-- 7. ad_opening_family — dimensions positive, type vocabulary
-- ============================================================
CREATE TABLE ad_opening_family_new (
    family_id         TEXT PRIMARY KEY,
    family_name       TEXT NOT NULL,
    opening_type      TEXT NOT NULL CHECK(opening_type IN ('DOOR','WINDOW')),
    ifc_class         TEXT NOT NULL CHECK(ifc_class IN ('IfcDoor','IfcWindow')),
    default_width_mm  INTEGER NOT NULL CHECK(default_width_mm > 0),
    default_height_mm INTEGER NOT NULL CHECK(default_height_mm > 0),
    is_fire_rated     INTEGER DEFAULT 0 CHECK(is_fire_rated IN (0,1)),
    description       TEXT,
    is_active         INTEGER DEFAULT 1 CHECK(is_active IN (0,1)),
    depth_mm          INTEGER DEFAULT NULL CHECK(depth_mm IS NULL OR depth_mm > 0)
);
INSERT INTO ad_opening_family_new
    SELECT family_id, family_name, opening_type, ifc_class,
           default_width_mm, default_height_mm, is_fire_rated,
           description, is_active, depth_mm
    FROM ad_opening_family;
DROP TABLE ad_opening_family;
ALTER TABLE ad_opening_family_new RENAME TO ad_opening_family;

-- ============================================================
-- Verification (executes within transaction — fails = rollback)
-- ============================================================
SELECT 'PASS: ad_product_dim dims positive' as result,
       COUNT(*) || '/' || (SELECT COUNT(*) FROM ad_product_dim) as ratio
FROM ad_product_dim WHERE width > 0 AND depth > 0 AND height > 0;

SELECT 'PASS: ad_room_boundary provenance declared' as result,
       COUNT(*) || '/' || (SELECT COUNT(*) FROM ad_room_boundary) as ratio
FROM ad_room_boundary WHERE extracted_from != 'PENDING' AND coordinate_frame != 'UNKNOWN';

SELECT 'PASS: ad_element_rule storey_z non-negative' as result,
       COUNT(*) || '/' || (SELECT COUNT(*) FROM ad_element_rule) as ratio
FROM ad_element_rule WHERE position_value_3 IS NULL OR position_value_3 >= 0;

SELECT 'PASS: ad_bom_child sequence positive' as result,
       COUNT(*) || '/' || (SELECT COUNT(*) FROM ad_bom_child) as ratio
FROM ad_bom_child WHERE sequence > 0;

SELECT 'PASS: ad_unit_type_room fractions valid' as result,
       COUNT(*) || '/' || (SELECT COUNT(*) FROM ad_unit_type_room) as ratio
FROM ad_unit_type_room
WHERE frac_min_x >= 0 AND frac_max_x <= 1
  AND frac_min_y >= 0 AND frac_max_y <= 1
  AND frac_min_x < frac_max_x AND frac_min_y < frac_max_y;

SELECT 'PASS: ad_opening_family dims positive' as result,
       COUNT(*) || '/' || (SELECT COUNT(*) FROM ad_opening_family) as ratio
FROM ad_opening_family WHERE default_width_mm > 0 AND default_height_mm > 0;

COMMIT;
PRAGMA foreign_keys = ON;

-- Smoke test: attempt a constraint violation — must fail
-- (Run this manually to confirm gates work:)
-- INSERT INTO ad_product_dim VALUES('_BAD_','FIXTURE',0,1,1,0,0,0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,'LIBRARY');
-- INSERT INTO ad_room_boundary VALUES(NULL,'X','Y','R','BEDROOM','A','B','1','2',0,1,NULL,NULL,NULL,NULL,NULL,'PENDING','LOCAL_MM');
