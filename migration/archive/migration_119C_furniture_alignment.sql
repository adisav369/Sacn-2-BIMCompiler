-- Phase 119C: Furniture BOM alignment for Rosetta Stone convergence
-- Fixes furniture gaps identified by spatial_checker:
--   Duplex: 12/61 (19%) -> target ~45/61 (73%)
--   SampleHouse: 1/14 (7%) -> target ~8/14 (57%)
--
-- Gap analysis:
--   DUPLEX missing: 16 base cabinets, 8 upper cabinets, 6 counter tops, 2 sink counters,
--     4 small coffee tables (610x610x610), 5 vanity cabinets, 2 small sofas (1830x660)
--   SAMPLEHOUSE missing: 1 dining table, 6 dining chairs, 1 piano, 1 desk,
--     2 lounge chairs, 1 small coffee table (1200x550)
--
-- Strategy: fix BOM recipes and room-type assignments (data-only, no Java changes)
-- All statements are idempotent (INSERT OR IGNORE / UPDATE with WHERE guards)

-- =========================================================================
-- 1. KITCHEN_CABINET_SET: fix name patterns, activate, add children
-- =========================================================================

-- Fix name patterns: Cabinet_Base% -> Base_Cabinet%, Cabinet_Upper% -> Upper_Cabinet%
-- The existing patterns don't match component_definitions names
UPDATE ad_bom_child SET child_name_pattern = 'Base_Cabinet%'
  WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET'
    AND child_name_pattern = 'Cabinet_Base%';

UPDATE ad_bom_child SET child_name_pattern = 'Upper_Cabinet%'
  WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'UPPER_CABINET'
    AND child_name_pattern = 'Cabinet_Upper%';

-- Activate the KITCHEN_CABINET_SET BOM (was is_active=0)
UPDATE ad_bom SET is_active = 1
  WHERE bom_id = 'KITCHEN_CABINET_SET' AND is_active = 0;

-- Add name_pattern params for existing children
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Base_Cabinet', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Upper_Cabinet', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'UPPER_CABINET';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Counter_Top', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'COUNTER';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Sink_Island', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'SINK';

-- Add offset params for existing children (dx/dy/dz required for hasOffsets path)
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dx', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dy', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dz', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'back_to_wall', 'true', 'BOOLEAN'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dx', '1.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'UPPER_CABINET';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dy', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'UPPER_CABINET';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dz', '1.4', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'UPPER_CABINET';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dx', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'COUNTER';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dy', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'COUNTER';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dz', '0.86', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'COUNTER';

-- Additional base cabinets (Duplex ref has 8 per kitchen, add 3 more = 4 total)
INSERT OR IGNORE INTO ad_bom_child
  (bom_id, child_ifc_class, child_name_pattern, role, qty_type, sequence)
VALUES
  ('KITCHEN_CABINET_SET', 'IfcFurniture', 'Base_Cabinet%', 'BASE_CABINET_B', 'FIXED', 5),
  ('KITCHEN_CABINET_SET', 'IfcFurniture', 'Base_Cabinet%', 'BASE_CABINET_C', 'FIXED', 6),
  ('KITCHEN_CABINET_SET', 'IfcFurniture', 'Base_Cabinet%', 'BASE_CABINET_D', 'FIXED', 7);

-- Params for additional base cabinets (offset along wall)
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Base_Cabinet', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_B';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dx', '1.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_B';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dy', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_B';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dz', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_B';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Base_Cabinet', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_C';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dx', '2.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_C';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dy', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_C';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dz', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_C';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Base_Cabinet', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_D';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dx', '3.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_D';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dy', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_D';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dz', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_D';

-- Additional upper cabinets (add 1 more = 2 total)
INSERT OR IGNORE INTO ad_bom_child
  (bom_id, child_ifc_class, child_name_pattern, role, qty_type, sequence)
VALUES
  ('KITCHEN_CABINET_SET', 'IfcFurniture', 'Upper_Cabinet%', 'UPPER_CABINET_B', 'FIXED', 8);

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Upper_Cabinet', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'UPPER_CABINET_B';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dx', '2.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'UPPER_CABINET_B';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dy', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'UPPER_CABINET_B';
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dz', '1.4', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'UPPER_CABINET_B';

-- Assign KITCHEN room type to KITCHEN_CABINET_SET
UPDATE ad_space_type_furniture SET furniture_bom_id = 'KITCHEN_CABINET_SET'
  WHERE space_type_id = 'KITCHEN' AND (furniture_bom_id IS NULL OR furniture_bom_id = '');


-- =========================================================================
-- 2. LIVING_SET: add params for SOFA_B, SIDE_TABLE_A, SIDE_TABLE_B
-- =========================================================================

-- Create distinct component name for the 610x610x610 cube coffee table
-- The existing Coffee_Table_Small name resolves to the 1200x550 variant (bigger footprint)
-- We need a distinct name for the cube variant
INSERT OR IGNORE INTO component_definitions
  (type_id, name, geometry_hash,
   local_min_x, local_max_x, local_min_y, local_max_y, local_min_z, local_max_z,
   attachment_face, up_axis, forward_axis, orientation, default_rotation,
   vertex_count, face_count, instance_count)
VALUES
  (9, 'Side_Table_Cube_610', '9879e57f079a4ead',
   -0.305, 0.305, -0.305, 0.305, -0.305, 0.305,
   'BOTTOM', 'Z', 'Y', 'UPRIGHT', 0.0,
   64, 112, 1);

-- Create distinct component name for the small sofa (1830x660x813)
-- The existing Sofa name resolves to the 2287x977 variant (bigger footprint)
INSERT OR IGNORE INTO component_definitions
  (type_id, name, geometry_hash,
   local_min_x, local_max_x, local_min_y, local_max_y, local_min_z, local_max_z,
   attachment_face, up_axis, forward_axis, orientation, default_rotation,
   vertex_count, face_count, instance_count)
VALUES
  (9, 'Sofa_Loveseat', '81d58cbed27620a5',
   -0.915, 0.915, -0.3302, 0.3302, -0.4064, 0.4064,
   'BOTTOM', 'Z', 'Y', 'UPRIGHT', 0.0,
   76, 120, 1);

-- SOFA_B: perpendicular second sofa forming L-shape
-- Offset from primary sofa anchor: perpendicular position
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Sofa_Loveseat', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'LIVING_SET' AND role = 'SOFA_B';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dx', '-1.5', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'LIVING_SET' AND role = 'SOFA_B';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dy', '0.5', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'LIVING_SET' AND role = 'SOFA_B';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dz', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'LIVING_SET' AND role = 'SOFA_B';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'rotation', '1.5708', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'LIVING_SET' AND role = 'SOFA_B';

-- SIDE_TABLE_A: cube side table next to primary sofa
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Side_Table_Cube_610', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'LIVING_SET' AND role = 'SIDE_TABLE_A';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dx', '1.2', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'LIVING_SET' AND role = 'SIDE_TABLE_A';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dy', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'LIVING_SET' AND role = 'SIDE_TABLE_A';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dz', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'LIVING_SET' AND role = 'SIDE_TABLE_A';

-- SIDE_TABLE_B: cube side table at other end of L-shape sofa
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Side_Table_Cube_610', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'LIVING_SET' AND role = 'SIDE_TABLE_B';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dx', '-1.2', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'LIVING_SET' AND role = 'SIDE_TABLE_B';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dy', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'LIVING_SET' AND role = 'SIDE_TABLE_B';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dz', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'LIVING_SET' AND role = 'SIDE_TABLE_B';


-- =========================================================================
-- 3. BED_SET: add offset params for TALL_CABINET_A/B
-- =========================================================================

-- TALL_CABINET_A: positioned beside the bed (left side)
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dx', '-1.2', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'BED_SET' AND role = 'TALL_CABINET_A';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dy', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'BED_SET' AND role = 'TALL_CABINET_A';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dz', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'BED_SET' AND role = 'TALL_CABINET_A';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Tall_Cabinet', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'BED_SET' AND role = 'TALL_CABINET_A';

-- TALL_CABINET_B: positioned beside the bed (right side)
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dx', '1.2', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'BED_SET' AND role = 'TALL_CABINET_B';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dy', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'BED_SET' AND role = 'TALL_CABINET_B';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dz', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'BED_SET' AND role = 'TALL_CABINET_B';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Tall_Cabinet', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'BED_SET' AND role = 'TALL_CABINET_B';


-- =========================================================================
-- 4. BATHROOM_FURNITURE_SET: vanity cabinet via FurniturePlacer pathway
-- =========================================================================

-- DUPLEX_BATHROOM_SET is unused in Java (no code references it).
-- Bathroom fixtures (WC, basin) go through FixturePlacer.
-- But vanity cabinets are IfcFurnishingElement (furniture), need FurniturePlacer.
-- Create a separate furniture BOM for bathroom vanity cabinets.

INSERT OR IGNORE INTO ad_bom
  (bom_id, bom_name, description, target_ifc_class, group_by, is_active)
VALUES
  ('BATHROOM_FURNITURE_SET', 'Bathroom Furniture Set',
   'Vanity cabinet for residential bathrooms',
   'IfcElementAssembly', 'ROOM', 1);

INSERT OR IGNORE INTO ad_bom_child
  (bom_id, child_ifc_class, child_name_pattern, role, qty_type, sequence)
VALUES
  ('BATHROOM_FURNITURE_SET', 'IfcFurniture', 'Vanity_Cabinet%', 'VANITY', 'FIXED', 1);

-- Add params for vanity cabinet placement
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Vanity_Cabinet', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'BATHROOM_FURNITURE_SET' AND role = 'VANITY';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dx', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'BATHROOM_FURNITURE_SET' AND role = 'VANITY';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dy', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'BATHROOM_FURNITURE_SET' AND role = 'VANITY';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dz', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'BATHROOM_FURNITURE_SET' AND role = 'VANITY';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'back_to_wall', 'true', 'BOOLEAN'
  FROM ad_bom_child WHERE bom_id = 'BATHROOM_FURNITURE_SET' AND role = 'VANITY';

-- Assign BATHROOM room type to BATHROOM_FURNITURE_SET
UPDATE ad_space_type_furniture SET furniture_bom_id = 'BATHROOM_FURNITURE_SET'
  WHERE space_type_id = 'BATHROOM' AND (furniture_bom_id IS NULL OR furniture_bom_id = '');


-- =========================================================================
-- 5. DINING_SET: add two more chairs (C, D already exist; add E, F)
-- =========================================================================

-- Reference Duplex has no explicit DINING rooms but SampleHouse has a dining table
-- with 6 chairs. Current DINING_SET has 4 chairs (A-D).
-- Add chairs E and F for 6-chair tables.
INSERT OR IGNORE INTO ad_bom_child
  (bom_id, child_ifc_class, role, qty_type, sequence)
VALUES
  ('DINING_SET', 'IfcFurniture', 'CHAIR_E', 'VARIABLE', 6),
  ('DINING_SET', 'IfcFurniture', 'CHAIR_F', 'VARIABLE', 7);

-- Add params for chairs E and F (mirrored from C and D, at table ends)
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Dining_Chair', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'DINING_SET' AND role = 'CHAIR_E';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dx', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'DINING_SET' AND role = 'CHAIR_E';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dy', '1.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'DINING_SET' AND role = 'CHAIR_E';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dz', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'DINING_SET' AND role = 'CHAIR_E';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'face_table', 'true', 'BOOLEAN'
  FROM ad_bom_child WHERE bom_id = 'DINING_SET' AND role = 'CHAIR_E';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Dining_Chair', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'DINING_SET' AND role = 'CHAIR_F';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dx', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'DINING_SET' AND role = 'CHAIR_F';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dy', '-1.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'DINING_SET' AND role = 'CHAIR_F';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dz', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'DINING_SET' AND role = 'CHAIR_F';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'face_table', 'true', 'BOOLEAN'
  FROM ad_bom_child WHERE bom_id = 'DINING_SET' AND role = 'CHAIR_F';


-- =========================================================================
-- 6. STUDY_SET: new BOM for bedroom desks (SampleHouse reference)
-- =========================================================================

-- SampleHouse bedroom has a desk (1563x819x762) — create STUDY_SET BOM
INSERT OR IGNORE INTO ad_bom
  (bom_id, bom_name, description, target_ifc_class, group_by, is_active)
VALUES
  ('STUDY_SET', 'Study Nook Set', 'Desk + chair for bedroom study area',
   'IfcElementAssembly', 'ROOM', 1);

INSERT OR IGNORE INTO ad_bom_child
  (bom_id, child_ifc_class, role, qty_type, sequence)
VALUES
  ('STUDY_SET', 'IfcFurniture', 'DESK', 'FIXED', 1);

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'name_pattern', 'Desk', 'STRING'
  FROM ad_bom_child WHERE bom_id = 'STUDY_SET' AND role = 'DESK';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dx', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'STUDY_SET' AND role = 'DESK';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dy', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'STUDY_SET' AND role = 'DESK';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'dz', '0.0', 'DOUBLE'
  FROM ad_bom_child WHERE bom_id = 'STUDY_SET' AND role = 'DESK';

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type)
  SELECT bom_child_id, 'opposite_wall', 'true', 'BOOLEAN'
  FROM ad_bom_child WHERE bom_id = 'STUDY_SET' AND role = 'DESK';

-- Assign STUDY_NOOK to STUDY_SET (overrides fallback_rule=WORKSTATION)
UPDATE ad_space_type_furniture SET furniture_bom_id = 'STUDY_SET'
  WHERE space_type_id = 'STUDY_NOOK' AND furniture_bom_id IS NULL;


-- =========================================================================
-- 7. Coffee_Table_Small renaming: the 1200x550x450 variant
-- =========================================================================

-- Create distinct name for the rectangular coffee table (1200x550x450)
-- so it can be specifically referenced (currently both variants are Coffee_Table_Small)
INSERT OR IGNORE INTO component_definitions
  (type_id, name, geometry_hash,
   local_min_x, local_max_x, local_min_y, local_max_y, local_min_z, local_max_z,
   attachment_face, up_axis, forward_axis, orientation, default_rotation,
   vertex_count, face_count, instance_count)
SELECT
  type_id, 'Coffee_Table_Rect_1200', geometry_hash,
  local_min_x, local_max_x, local_min_y, local_max_y, local_min_z, local_max_z,
  attachment_face, up_axis, forward_axis, orientation, default_rotation,
  vertex_count, face_count, instance_count
FROM component_definitions
WHERE name = 'Coffee_Table_Small' AND geometry_hash = '738682c11dd4d4f1'
LIMIT 1;


-- =========================================================================
-- 8. SampleHouse LIVING_SET: COFFEE_TABLE should use the 1200x550 variant
-- =========================================================================

-- The SampleHouse ref has Coffee_Table_1 (1200x550x450), not Coffee_Table_Large (1830x915x457)
-- However, the LIVING_SET COFFEE_TABLE name_pattern is set to "Coffee_Table" which
-- picks Coffee_Table_Large (biggest footprint). This is correct for Duplex but wrong for SH.
-- Without profile-aware BOM, we can't fix this for SH without breaking Duplex.
-- Noted as a known gap requiring profile-based BOM resolution.


-- =========================================================================
-- 9. WET_KITCHEN furniture assignment (for condo buildings)
-- =========================================================================

UPDATE ad_space_type_furniture SET furniture_bom_id = 'KITCHEN_CABINET_SET'
  WHERE space_type_id = 'WET_KITCHEN' AND (furniture_bom_id IS NULL OR furniture_bom_id = '');


-- =========================================================================
-- 10. Bed side tables: use cube side table instead of 420x420 side table
-- =========================================================================

-- The Duplex reference has Coffee_Table_Small 610x610x610 as bedroom side tables
-- Current BED_SET SIDE_TABLE uses Side_Table (420x420x410) which doesn't match
-- Update SIDE_TABLE name_pattern to use the cube variant (610x610x610)
UPDATE ad_bom_child_param SET param_value = 'Side_Table_Cube_610'
  WHERE param_key = 'name_pattern'
    AND bom_child_id = (SELECT bom_child_id FROM ad_bom_child
                         WHERE bom_id = 'BED_SET' AND role = 'SIDE_TABLE')
    AND param_value = 'Side_Table';


-- =========================================================================
-- VERIFICATION QUERIES (run manually to confirm)
-- =========================================================================
-- SELECT bom_id, role, child_name_pattern FROM ad_bom_child WHERE bom_id='KITCHEN_CABINET_SET' ORDER BY sequence;
-- SELECT bom_id, is_active FROM ad_bom WHERE bom_id IN ('KITCHEN_CABINET_SET','BATHROOM_FURNITURE_SET','STUDY_SET');
-- SELECT * FROM ad_space_type_furniture WHERE space_type_id IN ('KITCHEN','WET_KITCHEN','BATHROOM','STUDY_NOOK');
-- SELECT bc.bom_id, bc.role, p.param_key, p.param_value FROM ad_bom_child bc JOIN ad_bom_child_param p ON bc.bom_child_id=p.bom_child_id WHERE bc.bom_id='LIVING_SET' ORDER BY bc.sequence, p.param_key;
-- SELECT bc.role, p.param_key, p.param_value FROM ad_bom_child bc JOIN ad_bom_child_param p ON bc.bom_child_id=p.bom_child_id WHERE bc.bom_id='BATHROOM_FURNITURE_SET' ORDER BY bc.sequence;
-- SELECT bc.role, p.param_key, p.param_value FROM ad_bom_child bc JOIN ad_bom_child_param p ON bc.bom_child_id=p.bom_child_id WHERE bc.bom_id='BED_SET' ORDER BY bc.sequence, p.param_key;
-- SELECT name FROM component_definitions WHERE name IN ('Side_Table_Cube_610','Sofa_Loveseat','Coffee_Table_Rect_1200');
-- SELECT bc.role, p.param_key, p.param_value FROM ad_bom_child bc JOIN ad_bom_child_param p ON bc.bom_child_id=p.bom_child_id WHERE bc.bom_id='DINING_SET' ORDER BY bc.sequence, p.param_key;
--
-- EXPECTED ITEM COUNTS AFTER MIGRATION (per building):
-- Duplex (2 kitchens, 2 living, 4 bedrooms, 4 bathrooms):
--   Kitchen:  8 items/kitchen x 2 = 16 (was 0) -- 4 base_cab + 1 upper + 1 upper_b + 1 counter + 1 sink
--   Living:   7 items/living x 2 = 14 (was ~6) -- sofa + coffee + TV + lounge + sofa_b + side_a + side_b
--   Bedroom:  4 items/bedroom x 4 = 16 (was 16) -- bed + side_table + tall_a + tall_b
--   Bathroom: 1 item/bathroom x 4 = 4 (was 0) -- vanity cabinet
--   Total:    ~50 furniture items (was 20, ref=61)
--
-- SampleHouse (1 living, 1 bedroom):
--   Living:   7 items (was 4) -- sofa + coffee + TV + lounge + sofa_b + side_a + side_b
--   Bedroom:  4 items (was 4) -- bed + side_table + tall_a + tall_b
--   Total:    ~11 furniture items (was 6, ref=14)
