-- Phase 109A: Furniture BOM Recipes — Parent assemblies with spatial arrangement
-- Date: 2026-02-10
-- Purpose: Define furniture combos (BED_SET, LIVING_SET, DINING_SET) as BOM parents
--          with child offsets for spatial placement in rooms.

-- ============================================================
-- PART 1: BOM parent entries
-- ============================================================

INSERT OR IGNORE INTO ad_bom (bom_id, bom_name, description, target_ifc_class, group_by, is_active)
VALUES
    ('BED_SET',         'Bedroom Furniture Set',    'Bed + side table(s)',                   'IfcElementAssembly', 'ROOM', 1),
    ('BED_SET_MASTER',  'Master Bedroom Set',       'King bed + 2 side tables + wardrobe',   'IfcElementAssembly', 'ROOM', 1),
    ('LIVING_SET',      'Living Room Set',          'Sofa + coffee table + TV',              'IfcElementAssembly', 'ROOM', 1),
    ('DINING_SET',      'Dining Set',               'Dining table + chairs',                 'IfcElementAssembly', 'ROOM', 1),
    ('KITCHEN_CABINET_SET', 'Kitchen Cabinet Set',  'Base cabinets + counter + upper cabinets', 'IfcElementAssembly', 'ROOM', 0);

-- ============================================================
-- PART 2: BOM children with spatial roles
-- Using autoincrement (no explicit IDs)
-- ============================================================

-- BED_SET children
INSERT OR IGNORE INTO ad_bom_child (bom_id, child_ifc_class, role, sequence) VALUES
    ('BED_SET', 'IfcFurniture', 'BED',        1),
    ('BED_SET', 'IfcFurniture', 'SIDE_TABLE',  2);

-- BED_SET_MASTER children
INSERT OR IGNORE INTO ad_bom_child (bom_id, child_ifc_class, role, sequence) VALUES
    ('BED_SET_MASTER', 'IfcFurniture', 'BED',          1),
    ('BED_SET_MASTER', 'IfcFurniture', 'SIDE_TABLE_L', 2),
    ('BED_SET_MASTER', 'IfcFurniture', 'SIDE_TABLE_R', 3),
    ('BED_SET_MASTER', 'IfcFurniture', 'WARDROBE',     4);

-- LIVING_SET children
INSERT OR IGNORE INTO ad_bom_child (bom_id, child_ifc_class, role, sequence) VALUES
    ('LIVING_SET', 'IfcFurniture', 'SOFA',          1),
    ('LIVING_SET', 'IfcFurniture', 'COFFEE_TABLE',  2),
    ('LIVING_SET', 'IfcFurniture', 'TV',            3),
    ('LIVING_SET', 'IfcFurniture', 'LOUNGE_CHAIR',  4);

-- DINING_SET children
INSERT OR IGNORE INTO ad_bom_child (bom_id, child_ifc_class, role, sequence) VALUES
    ('DINING_SET', 'IfcFurniture', 'TABLE',    1),
    ('DINING_SET', 'IfcFurniture', 'CHAIR_A',  2),
    ('DINING_SET', 'IfcFurniture', 'CHAIR_B',  3),
    ('DINING_SET', 'IfcFurniture', 'CHAIR_C',  4),
    ('DINING_SET', 'IfcFurniture', 'CHAIR_D',  5);

-- ============================================================
-- PART 3: Spatial parameters for each child
-- dx/dy/dz are offsets from the SET anchor point (room center or wall offset)
-- name_pattern is SQL LIKE pattern for library lookup
-- We reference bom_child_id by subquery since IDs are autoincrement
-- ============================================================

-- BED_SET: BED params
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value) VALUES
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET' AND role='BED'), 'name_pattern', 'Bed_Queen'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET' AND role='BED'), 'dx', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET' AND role='BED'), 'dy', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET' AND role='BED'), 'dz', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET' AND role='BED'), 'back_to_wall', 'true');

-- BED_SET: SIDE_TABLE params
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value) VALUES
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET' AND role='SIDE_TABLE'), 'name_pattern', 'Side_Table'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET' AND role='SIDE_TABLE'), 'dx', '0.98'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET' AND role='SIDE_TABLE'), 'dy', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET' AND role='SIDE_TABLE'), 'dz', '0.0');

-- BED_SET_MASTER: BED params
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value) VALUES
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='BED'), 'name_pattern', 'Bed_King'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='BED'), 'dx', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='BED'), 'dy', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='BED'), 'dz', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='BED'), 'back_to_wall', 'true');

-- BED_SET_MASTER: SIDE_TABLE_L params
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value) VALUES
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='SIDE_TABLE_L'), 'name_pattern', 'Side_Table'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='SIDE_TABLE_L'), 'dx', '-1.22'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='SIDE_TABLE_L'), 'dy', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='SIDE_TABLE_L'), 'dz', '0.0');

-- BED_SET_MASTER: SIDE_TABLE_R params
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value) VALUES
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='SIDE_TABLE_R'), 'name_pattern', 'Side_Table'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='SIDE_TABLE_R'), 'dx', '1.22'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='SIDE_TABLE_R'), 'dy', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='SIDE_TABLE_R'), 'dz', '0.0');

-- BED_SET_MASTER: WARDROBE params
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value) VALUES
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='WARDROBE'), 'name_pattern', 'Wardrobe'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='WARDROBE'), 'dx', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='WARDROBE'), 'dy', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='WARDROBE'), 'dz', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='BED_SET_MASTER' AND role='WARDROBE'), 'opposite_wall', 'true');

-- LIVING_SET: SOFA params
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value) VALUES
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='SOFA'), 'name_pattern', 'Sofa'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='SOFA'), 'dx', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='SOFA'), 'dy', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='SOFA'), 'dz', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='SOFA'), 'back_to_wall', 'true');

-- LIVING_SET: COFFEE_TABLE params
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value) VALUES
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='COFFEE_TABLE'), 'name_pattern', 'Coffee_Table'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='COFFEE_TABLE'), 'dx', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='COFFEE_TABLE'), 'dy', '0.80'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='COFFEE_TABLE'), 'dz', '0.0');

-- LIVING_SET: TV params
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value) VALUES
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='TV'), 'name_pattern', 'TV_Flat_Screen'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='TV'), 'dx', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='TV'), 'dy', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='TV'), 'dz', '1.20'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='TV'), 'opposite_wall', 'true'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='TV'), 'wall_mount', 'true');

-- LIVING_SET: LOUNGE_CHAIR params
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value) VALUES
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='LOUNGE_CHAIR'), 'name_pattern', 'Lounge_Chair'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='LOUNGE_CHAIR'), 'dx', '1.50'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='LOUNGE_CHAIR'), 'dy', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='LIVING_SET' AND role='LOUNGE_CHAIR'), 'dz', '0.0');

-- DINING_SET: TABLE params
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value) VALUES
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='TABLE'), 'name_pattern', 'Dining_Table'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='TABLE'), 'dx', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='TABLE'), 'dy', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='TABLE'), 'dz', '0.0');

-- DINING_SET: CHAIR_A params (north-left)
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value) VALUES
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_A'), 'name_pattern', 'Dining_Chair'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_A'), 'dx', '-0.45'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_A'), 'dy', '0.65'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_A'), 'dz', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_A'), 'face_table', 'true');

-- DINING_SET: CHAIR_B params (north-right)
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value) VALUES
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_B'), 'name_pattern', 'Dining_Chair'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_B'), 'dx', '0.45'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_B'), 'dy', '0.65'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_B'), 'dz', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_B'), 'face_table', 'true');

-- DINING_SET: CHAIR_C params (south-left)
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value) VALUES
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_C'), 'name_pattern', 'Dining_Chair'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_C'), 'dx', '-0.45'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_C'), 'dy', '-0.65'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_C'), 'dz', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_C'), 'face_table', 'true');

-- DINING_SET: CHAIR_D params (south-right)
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value) VALUES
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_D'), 'name_pattern', 'Dining_Chair'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_D'), 'dx', '0.45'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_D'), 'dy', '-0.65'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_D'), 'dz', '0.0'),
    ((SELECT bom_child_id FROM ad_bom_child WHERE bom_id='DINING_SET' AND role='CHAIR_D'), 'face_table', 'true');

-- ============================================================
-- PART 4: Activate furniture routing for residential rooms
-- ============================================================

UPDATE ad_space_type_furniture SET furniture_bom_id = 'BED_SET',        fallback_rule = 'NONE' WHERE space_type_id = 'BEDROOM';
UPDATE ad_space_type_furniture SET furniture_bom_id = 'BED_SET_MASTER', fallback_rule = 'NONE' WHERE space_type_id = 'MASTER_BEDROOM';
UPDATE ad_space_type_furniture SET furniture_bom_id = 'LIVING_SET',     fallback_rule = 'NONE' WHERE space_type_id = 'LIVING';
UPDATE ad_space_type_furniture SET furniture_bom_id = 'DINING_SET',     fallback_rule = 'NONE' WHERE space_type_id = 'DINING';
-- CANTEEN keeps existing CANTEEN fallback (different layout logic)
