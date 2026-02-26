-- Phase 115B: Assembly MANIFEST tables
-- Idempotent: CREATE TABLE IF NOT EXISTS + INSERT OR IGNORE

-- ============================================================
-- 1a. Table: ad_assembly_manifest — face interfaces per assembly
-- ============================================================
CREATE TABLE IF NOT EXISTS ad_assembly_manifest (
    manifest_id   INTEGER PRIMARY KEY AUTOINCREMENT,
    assembly_id   TEXT NOT NULL,
    version       TEXT NOT NULL DEFAULT '1.0.0',
    face          TEXT NOT NULL,
    interface_type TEXT NOT NULL,
    clearance_m   REAL DEFAULT 0,
    UNIQUE(assembly_id, version, face, interface_type)
);

-- ============================================================
-- 1b. Table: ad_assembly_connector — typed MEP connection points
-- ============================================================
CREATE TABLE IF NOT EXISTS ad_assembly_connector (
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

-- ============================================================
-- 1c. Table: ad_room_slot — room type → slot → assembly mapping
-- ============================================================
CREATE TABLE IF NOT EXISTS ad_room_slot (
    slot_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    room_type     TEXT NOT NULL,
    slot_name     TEXT NOT NULL,
    assembly_id   TEXT,
    version_range TEXT DEFAULT '[1.0,2.0)',
    slot_face     TEXT,
    slot_priority INTEGER DEFAULT 100,
    is_required   INTEGER DEFAULT 0,
    UNIQUE(room_type, slot_name)
);

-- ============================================================
-- 2. MANIFEST face entries (~35 rows for 11 BOMs)
-- ============================================================

-- WORKSTATION_SET (Level -1)
INSERT OR IGNORE INTO ad_assembly_manifest (assembly_id, version, face, interface_type, clearance_m)
VALUES
    ('WORKSTATION_SET', '1.0.0', 'BACK',   'WALL_BACK',  0),
    ('WORKSTATION_SET', '1.0.0', 'FRONT',  'CLEARANCE',  1.2),
    ('WORKSTATION_SET', '1.0.0', 'LEFT',   'JOINABLE',   0.3),
    ('WORKSTATION_SET', '1.0.0', 'RIGHT',  'JOINABLE',   0.3),
    ('WORKSTATION_SET', '1.0.0', 'BOTTOM', 'ELEC_IN',    0);

-- BED_SET (Level -1)
INSERT OR IGNORE INTO ad_assembly_manifest (assembly_id, version, face, interface_type, clearance_m)
VALUES
    ('BED_SET', '1.0.0', 'BACK',  'WALL_BACK',  0),
    ('BED_SET', '1.0.0', 'FRONT', 'CLEARANCE',  0.6),
    ('BED_SET', '1.0.0', 'LEFT',  'CLEARANCE',  0.4),
    ('BED_SET', '1.0.0', 'RIGHT', 'JOINABLE',   0.3);

-- BED_SET_MASTER (Level -1)
INSERT OR IGNORE INTO ad_assembly_manifest (assembly_id, version, face, interface_type, clearance_m)
VALUES
    ('BED_SET_MASTER', '1.0.0', 'BACK',  'WALL_BACK',  0),
    ('BED_SET_MASTER', '1.0.0', 'FRONT', 'CLEARANCE',  0.6),
    ('BED_SET_MASTER', '1.0.0', 'LEFT',  'CLEARANCE',  0.6),
    ('BED_SET_MASTER', '1.0.0', 'RIGHT', 'CLEARANCE',  0.6);

-- LIVING_SET (Level -1)
INSERT OR IGNORE INTO ad_assembly_manifest (assembly_id, version, face, interface_type, clearance_m)
VALUES
    ('LIVING_SET', '1.0.0', 'BACK',  'WALL_BACK',  0),
    ('LIVING_SET', '1.0.0', 'FRONT', 'CLEARANCE',  1.5),
    ('LIVING_SET', '1.0.0', 'LEFT',  'JOINABLE',   0.3),
    ('LIVING_SET', '1.0.0', 'RIGHT', 'JOINABLE',   0.3);

-- DINING_SET (Level -1)
INSERT OR IGNORE INTO ad_assembly_manifest (assembly_id, version, face, interface_type, clearance_m)
VALUES
    ('DINING_SET', '1.0.0', 'FRONT', 'CLEARANCE', 0.9),
    ('DINING_SET', '1.0.0', 'BACK',  'CLEARANCE', 0.9),
    ('DINING_SET', '1.0.0', 'LEFT',  'CLEARANCE', 0.6),
    ('DINING_SET', '1.0.0', 'RIGHT', 'CLEARANCE', 0.6);

-- VISITOR_SET (Level -1)
INSERT OR IGNORE INTO ad_assembly_manifest (assembly_id, version, face, interface_type, clearance_m)
VALUES
    ('VISITOR_SET', '1.0.0', 'FRONT', 'CLEARANCE', 0.6),
    ('VISITOR_SET', '1.0.0', 'BACK',  'CLEARANCE', 0.6);

-- TOILET_BLOCK_FIXTURES (Level -1)
INSERT OR IGNORE INTO ad_assembly_manifest (assembly_id, version, face, interface_type, clearance_m)
VALUES
    ('TOILET_BLOCK_FIXTURES', '1.0.0', 'BACK',  'WALL_BACK', 0.05),
    ('TOILET_BLOCK_FIXTURES', '1.0.0', 'FRONT', 'CLEARANCE', 0.533),
    ('TOILET_BLOCK_FIXTURES', '1.0.0', 'LEFT',  'CLEARANCE', 0.381),
    ('TOILET_BLOCK_FIXTURES', '1.0.0', 'RIGHT', 'CLEARANCE', 0.381);

-- DUPLEX_BATHROOM_SET (Level -1)
INSERT OR IGNORE INTO ad_assembly_manifest (assembly_id, version, face, interface_type, clearance_m)
VALUES
    ('DUPLEX_BATHROOM_SET', '1.0.0', 'BACK',  'WALL_BACK', 0.05),
    ('DUPLEX_BATHROOM_SET', '1.0.0', 'FRONT', 'CLEARANCE', 0.533);

-- KITCHEN_CABINET_SET (Level -1)
INSERT OR IGNORE INTO ad_assembly_manifest (assembly_id, version, face, interface_type, clearance_m)
VALUES
    ('KITCHEN_CABINET_SET', '1.0.0', 'BACK',  'WALL_BACK', 0),
    ('KITCHEN_CABINET_SET', '1.0.0', 'FRONT', 'CLEARANCE', 0.9),
    ('KITCHEN_CABINET_SET', '1.0.0', 'LEFT',  'JOINABLE',  0),
    ('KITCHEN_CABINET_SET', '1.0.0', 'RIGHT', 'JOINABLE',  0);

-- T_CONNECTOR_ASSEMBLY (Level 0.5)
INSERT OR IGNORE INTO ad_assembly_manifest (assembly_id, version, face, interface_type, clearance_m)
VALUES
    ('T_CONNECTOR_ASSEMBLY', '1.0.0', 'TOP', 'MAIN_HOOKUP', 0);

-- SPRINKLER_PENDANT_ASSEMBLY (Level 0.5)
INSERT OR IGNORE INTO ad_assembly_manifest (assembly_id, version, face, interface_type, clearance_m)
VALUES
    ('SPRINKLER_PENDANT_ASSEMBLY', '1.0.0', 'TOP', 'MAIN_HOOKUP', 0);

-- ============================================================
-- 3. Connector entries (~12 rows for 5 BOMs)
-- ============================================================

-- TOILET_BLOCK_FIXTURES
INSERT OR IGNORE INTO ad_assembly_connector (assembly_id, version, face, connector_type, diameter_mm, connects_to)
VALUES
    ('TOILET_BLOCK_FIXTURES', '1.0.0', 'BOTTOM', 'WASTE_OUT',  100, 'PLUMBING_STACK'),
    ('TOILET_BLOCK_FIXTURES', '1.0.0', 'BOTTOM', 'SUPPLY_IN',   15, 'WATER_RISER');

-- DUPLEX_BATHROOM_SET
INSERT OR IGNORE INTO ad_assembly_connector (assembly_id, version, face, connector_type, diameter_mm, connects_to)
VALUES
    ('DUPLEX_BATHROOM_SET', '1.0.0', 'BOTTOM', 'WASTE_OUT',  100, 'PLUMBING_STACK'),
    ('DUPLEX_BATHROOM_SET', '1.0.0', 'BOTTOM', 'SUPPLY_IN',   15, 'WATER_RISER');

-- KITCHEN_CABINET_SET
INSERT OR IGNORE INTO ad_assembly_connector (assembly_id, version, face, connector_type, diameter_mm, connects_to)
VALUES
    ('KITCHEN_CABINET_SET', '1.0.0', 'BOTTOM', 'WASTE_OUT',   40, 'PLUMBING_STACK'),
    ('KITCHEN_CABINET_SET', '1.0.0', 'BOTTOM', 'SUPPLY_IN',   15, 'WATER_RISER');

-- T_CONNECTOR_ASSEMBLY
INSERT OR IGNORE INTO ad_assembly_connector (assembly_id, version, face, connector_type, diameter_mm, connects_to)
VALUES
    ('T_CONNECTOR_ASSEMBLY', '1.0.0', 'TOP',    'MAIN_HOOKUP',  65, 'FP_MAIN'),
    ('T_CONNECTOR_ASSEMBLY', '1.0.0', 'BOTTOM', 'PENDANT_HEAD', 15, NULL);

-- SPRINKLER_PENDANT_ASSEMBLY
INSERT OR IGNORE INTO ad_assembly_connector (assembly_id, version, face, connector_type, diameter_mm, connects_to)
VALUES
    ('SPRINKLER_PENDANT_ASSEMBLY', '1.0.0', 'TOP',    'MAIN_HOOKUP', 65, 'FP_MAIN'),
    ('SPRINKLER_PENDANT_ASSEMBLY', '1.0.0', 'BOTTOM', 'BRANCH_OUT',  25, NULL);

-- ============================================================
-- 4. Room slot entries (~22 rows for 8 room types)
-- ============================================================

-- BATHROOM
INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, assembly_id, version_range, slot_face, slot_priority, is_required)
VALUES
    ('BATHROOM', 'SANITARY',    'TOILET_BLOCK_FIXTURES', '[1.0,2.0)', 'BACK',    10, 1),
    ('BATHROOM', 'EXHAUST',     NULL,                    '[1.0,2.0)', 'TOP',     50, 0),
    ('BATHROOM', 'FLOOR_TRAP',  NULL,                    '[1.0,2.0)', 'BOTTOM',  60, 0);

-- BEDROOM
INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, assembly_id, version_range, slot_face, slot_priority, is_required)
VALUES
    ('BEDROOM', 'FURNITURE', 'BED_SET',   '[1.0,2.0)', 'BACK',  10, 1),
    ('BEDROOM', 'CEILING_MEP', NULL,      '[1.0,2.0)', 'TOP',   50, 0);

-- KITCHEN
INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, assembly_id, version_range, slot_face, slot_priority, is_required)
VALUES
    ('KITCHEN', 'COUNTER',     'KITCHEN_CABINET_SET', '[1.0,2.0)', 'BACK',   10, 1),
    ('KITCHEN', 'CEILING_MEP', NULL,                  '[1.0,2.0)', 'TOP',    50, 0);

-- OFFICE
INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, assembly_id, version_range, slot_face, slot_priority, is_required)
VALUES
    ('OFFICE', 'FURNITURE',  'WORKSTATION_SET', '[1.0,2.0)', 'BACK',  10, 1),
    ('OFFICE', 'VISITOR',    'VISITOR_SET',     '[1.0,2.0)', 'FRONT', 20, 0),
    ('OFFICE', 'CEILING_MEP', NULL,             '[1.0,2.0)', 'TOP',   50, 0);

-- LIVING
INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, assembly_id, version_range, slot_face, slot_priority, is_required)
VALUES
    ('LIVING', 'FURNITURE',  'LIVING_SET',  '[1.0,2.0)', 'BACK',  10, 1),
    ('LIVING', 'DINING',     'DINING_SET',  '[1.0,2.0)', 'FRONT', 20, 0),
    ('LIVING', 'CEILING_MEP', NULL,         '[1.0,2.0)', 'TOP',   50, 0);

-- TOILET_BLOCK
INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, assembly_id, version_range, slot_face, slot_priority, is_required)
VALUES
    ('TOILET_BLOCK', 'SANITARY',   'TOILET_BLOCK_FIXTURES', '[1.0,2.0)', 'BACK',   10, 1),
    ('TOILET_BLOCK', 'BASIN',      NULL,                    '[1.0,2.0)', 'LEFT',   20, 0),
    ('TOILET_BLOCK', 'EXHAUST',    NULL,                    '[1.0,2.0)', 'TOP',    50, 0),
    ('TOILET_BLOCK', 'FLOOR_TRAP', NULL,                    '[1.0,2.0)', 'BOTTOM', 60, 0);

-- MASTER_BEDROOM
INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, assembly_id, version_range, slot_face, slot_priority, is_required)
VALUES
    ('MASTER_BEDROOM', 'FURNITURE',  'BED_SET_MASTER', '[1.0,2.0)', 'BACK', 10, 1),
    ('MASTER_BEDROOM', 'CEILING_MEP', NULL,            '[1.0,2.0)', 'TOP',  50, 0);

-- DINING
INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, assembly_id, version_range, slot_face, slot_priority, is_required)
VALUES
    ('DINING', 'FURNITURE',  'DINING_SET', '[1.0,2.0)', 'BACK',  10, 1),
    ('DINING', 'CEILING_MEP', NULL,        '[1.0,2.0)', 'TOP',   50, 0);
