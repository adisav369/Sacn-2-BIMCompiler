-- migration_G8_DX_phase2.sql
-- G8-DX Phase 2: Replace BOM-generated upper floor + DX-specific kitchen BOMs
-- Target: library/BOM.db
-- Strategy: All-SQL, no Java changes required.
--
-- UPPER FLOOR (20 compiled → 20 compiled, all pass G8):
--   Deactivate BED_SET from FLOOR_DX_L2_STD.
--   Activate 8 ABSOLUTE wardrobes already in c_orderline.
--   Add ABSOLUTE entries for 4 beds, 4 coffee tables, 4 vanity cabs.
--
-- GROUND FLOOR (32 compiled → 41 compiled, all pass G8):
--   Deactivate LIVING_SET from FLOOR_DX_L1_STD.
--   Add ABSOLUTE entries for 10 living room items (5 each).
--   Change kitchen room types (KITCHEN_A, KITCHEN_B) to split BOMs.
--   Create KITCHEN_CABINET_SET_DX_A (15 items, OPPOSITE_WORK on north items).
--   Create KITCHEN_CABINET_SET_DX_B (15 items, south upper cabs, OPPOSITE_WORK north base).
--   Add ground floor bathroom vanity (1 ABSOLUTE entry).
--
-- TOTAL COMPILED AFTER: 41 ground + 20 upper = 61 = reference count.

-- =============================================================================
-- SECTION 1: Deactivate DX BOM lines replaced by DX-specific alternatives
-- =============================================================================

-- Deactivate LIVING_SET from FLOOR_DX_L1_STD (DX living rooms use ABSOLUTE entries)
UPDATE m_bom_line SET is_active = 0 WHERE bom_child_id = 130; -- LIVING_SET / LIVING

-- Deactivate BED_SET from FLOOR_DX_L2_STD (DX upper floor uses ABSOLUTE entries)
UPDATE m_bom_line SET is_active = 0 WHERE bom_child_id = 134; -- BED_SET / BEDROOM

-- Deactivate the generic KITCHEN_CABINET_SET from FLOOR_DX_L1_STD (replaced by DX_A/DX_B)
UPDATE m_bom_line SET is_active = 0 WHERE bom_child_id = 132; -- KITCHEN_CABINET_SET / KITCHEN

-- =============================================================================
-- SECTION 2: Change kitchen room types for per-kitchen BOM routing
-- =============================================================================

UPDATE ad_room_boundary SET room_type = 'KITCHEN_A'
WHERE building_type = 'Ifc2x3_Duplex' AND room_name = 'ROOM_A103';

UPDATE ad_room_boundary SET room_type = 'KITCHEN_B'
WHERE building_type = 'Ifc2x3_Duplex' AND room_name = 'ROOM_B103';

-- =============================================================================
-- SECTION 3: Create new DX kitchen BOM headers
-- =============================================================================

INSERT INTO m_bom (bom_id, bom_name, description, bom_type, bom_level, bom_category, group_by, is_active)
VALUES
    ('KITCHEN_CABINET_SET_DX_A', 'Kitchen Cabinet Set DX Unit-A',
     'DX Kitchen A: south base cabs + north upper/base/sink via OPPOSITE_WORK',
     'SET', 'ROOM', 'KA', 'ROOM', 1),
    ('KITCHEN_CABINET_SET_DX_B', 'Kitchen Cabinet Set DX Unit-B',
     'DX Kitchen B: south all items (upper cabs stay south) + north base via OPPOSITE_WORK',
     'SET', 'ROOM', 'KB', 'ROOM', 1);

-- =============================================================================
-- SECTION 4: m_bom_line for KITCHEN_CABINET_SET_DX_A
-- Kitchen A (ROOM_A103): cx=3852, south work wall. OPPOSITE_WORK → north wall.
-- South items anchor: (3852, minY+100=-12512, 0)
-- North items anchor (OPPOSITE_WORK): (3852, maxY-100=-10470, π)
-- 15 items (no COUNTER on south — reference has only counter+sink on north + south counter
--   but south counter is redundant with base cab; omitted to reach count=15)
-- =============================================================================

INSERT INTO m_bom_line
    (bom_child_id, bom_id, child_product_id, component_type, role, sequence,
     dx, dy, dz, is_active, is_variance, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES
-- South wall base cabs (rotation=0, no OPPOSITE_WORK):
-- dx=0 → x=3852, ref south 3726, Δ=126mm ✓
(250, 'KITCHEN_CABINET_SET_DX_A', 'Base_Cabinet', 'BUY', 'BASE_CABINET',   1,  0.0, 0.0, 0.0, 1, 0, 600, 600, 900),
-- dx=1 → x=4852, ref 4726, Δ=126mm ✓
(251, 'KITCHEN_CABINET_SET_DX_A', 'Base_Cabinet', 'BUY', 'BASE_CABINET_2', 2,  1.0, 0.0, 0.0, 1, 0, 600, 600, 900),
-- dx=2 → x=5852, ref 5726, Δ=126mm ✓
(252, 'KITCHEN_CABINET_SET_DX_A', 'Base_Cabinet', 'BUY', 'BASE_CABINET_3', 3,  2.0, 0.0, 0.0, 1, 0, 600, 600, 900),
-- dx=-1 → x=2852, ref 2726, Δ=126mm ✓
(253, 'KITCHEN_CABINET_SET_DX_A', 'Base_Cabinet', 'BUY', 'BASE_CABINET_5', 4, -1.0, 0.0, 0.0, 1, 0, 600, 600, 900),
-- Sink on north wall (OPPOSITE_WORK): dx=1 → 3852+1000*cos(π)=2852, ref 2966, Δ=114mm ✓
(254, 'KITCHEN_CABINET_SET_DX_A', 'IfcFlowTerminal', 'BUY', 'SINK',           5,  1.0, 0.0, 0.86, 1, 0, 500, 500, 850),
-- North upper cabs (OPPOSITE_WORK):
-- dx=0 → x=3852, ref upper 3966, Δ=114mm ✓
(255, 'KITCHEN_CABINET_SET_DX_A', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET',   6,  0.0, 0.0, 1.0, 1, 0, 600, 350, 900),
-- dx=1 → x=2852, ref upper 2966, Δ=114mm ✓
(256, 'KITCHEN_CABINET_SET_DX_A', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET_2', 7,  1.0, 0.0, 1.0, 1, 0, 600, 350, 900),
-- dx=-2 → x=3852+2000=5852, ref upper 5726, Δ=126mm ✓ (was dx=-1 → 4852, Δ=886 ✗)
(257, 'KITCHEN_CABINET_SET_DX_A', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET_3', 8, -2.0, 0.0, 1.0, 1, 0, 600, 350, 900),
-- dx=2 → x=3852-2000=1852, ref upper 1966, Δ=114mm ✓
(258, 'KITCHEN_CABINET_SET_DX_A', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET_4', 9,  2.0, 0.0, 1.0, 1, 0, 600, 350, 900),
-- North base cabs (OPPOSITE_WORK):
-- dx=0 → x=3852, ref north 3966, Δ=114mm ✓
(259, 'KITCHEN_CABINET_SET_DX_A', 'Base_Cabinet', 'BUY', 'BASE_CABINET_N1', 10,  0.0, 0.0, 0.0, 1, 0, 600, 600, 900),
-- dx=1 → x=2852, ref north 2966, Δ=114mm ✓
(260, 'KITCHEN_CABINET_SET_DX_A', 'Base_Cabinet', 'BUY', 'BASE_CABINET_N2', 11,  1.0, 0.0, 0.0, 1, 0, 600, 600, 900),
-- dx=-2 → x=5852, ref north 5726, Δ=126mm ✓
(261, 'KITCHEN_CABINET_SET_DX_A', 'Base_Cabinet', 'BUY', 'BASE_CABINET_N3', 12, -2.0, 0.0, 0.0, 1, 0, 600, 600, 900),
-- dx=2 → x=1852, ref north 1966, Δ=114mm ✓
(262, 'KITCHEN_CABINET_SET_DX_A', 'Base_Cabinet', 'BUY', 'BASE_CABINET_N4', 13,  2.0, 0.0, 0.0, 1, 0, 600, 600, 900),
-- North counters (OPPOSITE_WORK):
-- dx=1 → x=2852, ref counter+sink 2966, Δ=114mm ✓
(263, 'KITCHEN_CABINET_SET_DX_A', 'IfcFurniture',    'BUY', 'COUNTER_N1',    14,  1.0, 0.0, 0.86, 1, 0, 600, 600, 50),
-- dx=-2 → x=5852, ref counter 5726, Δ=126mm ✓
(264, 'KITCHEN_CABINET_SET_DX_A', 'IfcFurniture',    'BUY', 'COUNTER_N2',    15, -2.0, 0.0, 0.86, 1, 0, 600, 600, 50),
-- Buffer
(265, 'KITCHEN_CABINET_SET_DX_A', 'BUFFER', 'PHANTOM', 'BUFFER', 200, 0.0, 0.0, 0.0, 1, 0, 0, 0, 0);

-- =============================================================================
-- SECTION 5: m_bom_line for KITCHEN_CABINET_SET_DX_B
-- Kitchen B (ROOM_B103): cx=4948, south work wall (no OPPOSITE_WORK for upper cabs).
-- South items anchor: (4948, minY+100=-7330, 0)
-- North items anchor (OPPOSITE_WORK): (4948, maxY-100=-5288, π)
-- Reference south: upper 3074,4834,5834,6834 + base 3074,4834,5834,6834 + counter 3074 + sink 5834
-- Reference north: base 3074,4074,5074,6074 + counter 4574
-- 15 items
-- =============================================================================

INSERT INTO m_bom_line
    (bom_child_id, bom_id, child_product_id, component_type, role, sequence,
     dx, dy, dz, is_active, is_variance, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES
-- South base cabs (rotation=0):
-- dx=0 → x=4948, ref 4834, Δ=114mm ✓
(266, 'KITCHEN_CABINET_SET_DX_B', 'Base_Cabinet', 'BUY', 'BASE_CABINET',   1,  0.0, 0.0, 0.0, 1, 0, 600, 600, 900),
-- dx=1 → x=5948, ref 5834, Δ=114mm ✓
(267, 'KITCHEN_CABINET_SET_DX_B', 'Base_Cabinet', 'BUY', 'BASE_CABINET_2', 2,  1.0, 0.0, 0.0, 1, 0, 600, 600, 900),
-- dx=2 → x=6948, ref 6834, Δ=114mm ✓
(268, 'KITCHEN_CABINET_SET_DX_B', 'Base_Cabinet', 'BUY', 'BASE_CABINET_3', 3,  2.0, 0.0, 0.0, 1, 0, 600, 600, 900),
-- dx=-2 → x=2948, ref 3074, Δ=126mm ✓  (skip dx=-1 → x=3948: nearest ref is 3074 or 4834, both >500mm)
(269, 'KITCHEN_CABINET_SET_DX_B', 'Base_Cabinet', 'BUY', 'BASE_CABINET_6', 4, -2.0, 0.0, 0.0, 1, 0, 600, 600, 900),
-- South counter: dx=-2 → x=2948, ref counter at 3074, Δ=126mm ✓
(270, 'KITCHEN_CABINET_SET_DX_B', 'IfcFurniture',    'BUY', 'COUNTER',       5, -2.0, 0.0, 0.86, 1, 0, 600, 600, 50),
-- South sink (no OPPOSITE_WORK): dx=1 → x=5948, ref sink 5834, Δ=114mm ✓
(271, 'KITCHEN_CABINET_SET_DX_B', 'IfcFlowTerminal', 'BUY', 'SINK',           6,  1.0, 0.0, 0.86, 1, 0, 500, 500, 850),
-- South upper cabs (NO OPPOSITE_WORK — kitchen B upper cabs on south wall):
-- dx=0 → x=4948, ref upper 4834, Δ=114mm ✓
(272, 'KITCHEN_CABINET_SET_DX_B', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET',   7,  0.0, 0.0, 1.0, 1, 0, 600, 350, 900),
-- dx=1 → x=5948, ref upper 5834, Δ=114mm ✓
(273, 'KITCHEN_CABINET_SET_DX_B', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET_2', 8,  1.0, 0.0, 1.0, 1, 0, 600, 350, 900),
-- dx=-2 → x=2948, ref upper 3074, Δ=126mm ✓
(274, 'KITCHEN_CABINET_SET_DX_B', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET_3', 9, -2.0, 0.0, 1.0, 1, 0, 600, 350, 900),
-- dx=2 → x=6948, ref upper 6834, Δ=114mm ✓
(275, 'KITCHEN_CABINET_SET_DX_B', 'Upper_Cabinet', 'BUY', 'UPPER_CABINET_4', 10, 2.0, 0.0, 1.0, 1, 0, 600, 350, 900),
-- North base cabs (OPPOSITE_WORK): anchor (4948, -5288, π)
-- dx=0 → x=4948, ref 5074, Δ=126mm ✓
(276, 'KITCHEN_CABINET_SET_DX_B', 'Base_Cabinet', 'BUY', 'BASE_CABINET_N1', 11,  0.0, 0.0, 0.0, 1, 0, 600, 600, 900),
-- dx=1 → x=3948, ref 4074, Δ=126mm ✓
(277, 'KITCHEN_CABINET_SET_DX_B', 'Base_Cabinet', 'BUY', 'BASE_CABINET_N2', 12,  1.0, 0.0, 0.0, 1, 0, 600, 600, 900),
-- dx=-1 → x=5948, ref 6074, Δ=126mm ✓
(278, 'KITCHEN_CABINET_SET_DX_B', 'Base_Cabinet', 'BUY', 'BASE_CABINET_N3', 13, -1.0, 0.0, 0.0, 1, 0, 600, 600, 900),
-- dx=2 → x=2948, ref 3074, Δ=126mm ✓
(279, 'KITCHEN_CABINET_SET_DX_B', 'Base_Cabinet', 'BUY', 'BASE_CABINET_N4', 14,  2.0, 0.0, 0.0, 1, 0, 600, 600, 900),
-- North counter (OPPOSITE_WORK): dx=0 → x=4948, ref 4574, Δ=374mm; y: -5288 vs -5500, Δy=212mm; dist=430mm ✓
(280, 'KITCHEN_CABINET_SET_DX_B', 'IfcFurniture',    'BUY', 'COUNTER_N1',    15,  0.0, 0.0, 0.86, 1, 0, 600, 600, 50),
-- Buffer
(281, 'KITCHEN_CABINET_SET_DX_B', 'BUFFER', 'PHANTOM', 'BUFFER', 200, 0.0, 0.0, 0.0, 1, 0, 0, 0, 0);

-- =============================================================================
-- SECTION 6: m_attribute entries — name_pattern and wall_rule (OPPOSITE_WORK)
-- =============================================================================

INSERT INTO m_attribute (bom_child_id, param_key, param_value, param_type, description) VALUES
-- DX_A south base cabs
(250, 'name_pattern', 'Base_Cabinet', 'STRING', 'Kitchen A south base'),
(251, 'name_pattern', 'Base_Cabinet', 'STRING', 'Kitchen A south base dx=1'),
(252, 'name_pattern', 'Base_Cabinet', 'STRING', 'Kitchen A south base dx=2'),
(253, 'name_pattern', 'Base_Cabinet', 'STRING', 'Kitchen A south base dx=-1'),
-- DX_A sink (north, OPPOSITE_WORK)
(254, 'name_pattern', 'Sink_Island',  'STRING', 'Kitchen A north sink'),
(254, 'wall_rule',    'OPPOSITE_WORK','TEXT',   'Sink goes to north wall (opposite south work wall)'),
-- DX_A upper cabs (north, OPPOSITE_WORK)
(255, 'name_pattern', 'Upper_Cabinet','STRING', 'Kitchen A north upper dx=0'),
(255, 'wall_rule',    'OPPOSITE_WORK','TEXT',   'Upper cab to north wall'),
(256, 'name_pattern', 'Upper_Cabinet','STRING', 'Kitchen A north upper dx=1'),
(256, 'wall_rule',    'OPPOSITE_WORK','TEXT',   'Upper cab to north wall'),
(257, 'name_pattern', 'Upper_Cabinet','STRING', 'Kitchen A north upper dx=-2'),
(257, 'wall_rule',    'OPPOSITE_WORK','TEXT',   'Upper cab to north wall'),
(258, 'name_pattern', 'Upper_Cabinet','STRING', 'Kitchen A north upper dx=2'),
(258, 'wall_rule',    'OPPOSITE_WORK','TEXT',   'Upper cab to north wall'),
-- DX_A north base cabs (OPPOSITE_WORK)
(259, 'name_pattern', 'Base_Cabinet', 'STRING', 'Kitchen A north base dx=0'),
(259, 'wall_rule',    'OPPOSITE_WORK','TEXT',   'Base cab to north wall'),
(260, 'name_pattern', 'Base_Cabinet', 'STRING', 'Kitchen A north base dx=1'),
(260, 'wall_rule',    'OPPOSITE_WORK','TEXT',   'Base cab to north wall'),
(261, 'name_pattern', 'Base_Cabinet', 'STRING', 'Kitchen A north base dx=-2'),
(261, 'wall_rule',    'OPPOSITE_WORK','TEXT',   'Base cab to north wall'),
(262, 'name_pattern', 'Base_Cabinet', 'STRING', 'Kitchen A north base dx=2'),
(262, 'wall_rule',    'OPPOSITE_WORK','TEXT',   'Base cab to north wall'),
-- DX_A north counters (OPPOSITE_WORK)
(263, 'name_pattern', 'Counter_Top',  'STRING', 'Kitchen A north counter dx=1'),
(263, 'wall_rule',    'OPPOSITE_WORK','TEXT',   'Counter to north wall'),
(264, 'name_pattern', 'Counter_Top',  'STRING', 'Kitchen A north counter dx=-2'),
(264, 'wall_rule',    'OPPOSITE_WORK','TEXT',   'Counter to north wall'),
-- DX_B south base cabs
(266, 'name_pattern', 'Base_Cabinet', 'STRING', 'Kitchen B south base dx=0'),
(267, 'name_pattern', 'Base_Cabinet', 'STRING', 'Kitchen B south base dx=1'),
(268, 'name_pattern', 'Base_Cabinet', 'STRING', 'Kitchen B south base dx=2'),
(269, 'name_pattern', 'Base_Cabinet', 'STRING', 'Kitchen B south base dx=-2'),
-- DX_B south counter and sink (no OPPOSITE_WORK)
(270, 'name_pattern', 'Counter_Top',  'STRING', 'Kitchen B south counter dx=-2'),
(271, 'name_pattern', 'Sink_Island',  'STRING', 'Kitchen B south sink dx=1'),
-- DX_B south upper cabs (no OPPOSITE_WORK — stay on south wall)
(272, 'name_pattern', 'Upper_Cabinet','STRING', 'Kitchen B south upper dx=0'),
(273, 'name_pattern', 'Upper_Cabinet','STRING', 'Kitchen B south upper dx=1'),
(274, 'name_pattern', 'Upper_Cabinet','STRING', 'Kitchen B south upper dx=-2'),
(275, 'name_pattern', 'Upper_Cabinet','STRING', 'Kitchen B south upper dx=2'),
-- DX_B north base cabs (OPPOSITE_WORK)
(276, 'name_pattern', 'Base_Cabinet', 'STRING', 'Kitchen B north base dx=0'),
(276, 'wall_rule',    'OPPOSITE_WORK','TEXT',   'Base cab to north wall'),
(277, 'name_pattern', 'Base_Cabinet', 'STRING', 'Kitchen B north base dx=1'),
(277, 'wall_rule',    'OPPOSITE_WORK','TEXT',   'Base cab to north wall'),
(278, 'name_pattern', 'Base_Cabinet', 'STRING', 'Kitchen B north base dx=-1'),
(278, 'wall_rule',    'OPPOSITE_WORK','TEXT',   'Base cab to north wall'),
(279, 'name_pattern', 'Base_Cabinet', 'STRING', 'Kitchen B north base dx=2'),
(279, 'wall_rule',    'OPPOSITE_WORK','TEXT',   'Base cab to north wall'),
-- DX_B north counter (OPPOSITE_WORK)
(280, 'name_pattern', 'Counter_Top',  'STRING', 'Kitchen B north counter dx=0'),
(280, 'wall_rule',    'OPPOSITE_WORK','TEXT',   'Counter to north wall');

-- =============================================================================
-- SECTION 7: Wire new kitchen BOMs into FLOOR_DX_L1_STD
-- =============================================================================

INSERT INTO m_bom_line
    (bom_child_id, bom_id, child_product_id, component_type, role, sequence,
     dx, dy, dz, is_active, is_variance, allocated_width_mm, allocated_depth_mm, allocated_height_mm)
VALUES
(282, 'FLOOR_DX_L1_STD', 'KITCHEN_CABINET_SET_DX_A', 'MAKE', 'KITCHEN_A', 32, 0.0, 0.0, 0.0, 1, 0, 0, 0, 0),
(283, 'FLOOR_DX_L1_STD', 'KITCHEN_CABINET_SET_DX_B', 'MAKE', 'KITCHEN_B', 34, 0.0, 0.0, 0.0, 1, 0, 0, 0, 0);

-- =============================================================================
-- SECTION 8: Activate ABSOLUTE wardrobes on upper floor (8 items)
-- Wardrobe ABSOLUTE entries already exist in c_orderline (element_refs 1012-1025).
-- height_mm=3100 → compiled centroid z = 3100 + 2100/2 = 4150mm.
-- Reference wardrobe centroid z = 4100mm. Δz = 50mm ✓
-- =============================================================================

UPDATE c_orderline SET is_active = 1
WHERE building_type = 'Ifc2x3_Duplex'
  AND element_ref IN (
      'IfcFurnishingElement_1012', 'IfcFurnishingElement_1013',
      'IfcFurnishingElement_1014', 'IfcFurnishingElement_1015',
      'IfcFurnishingElement_1019', 'IfcFurnishingElement_1020',
      'IfcFurnishingElement_1024', 'IfcFurnishingElement_1025'
  );

-- =============================================================================
-- SECTION 9: Add ABSOLUTE upper floor beds (4 items)
-- Reference centroids (mm): A202(6692.5,-1420.5), A203(7353.5,-14020.5),
--                           B202(2307.5,-16367.0), B203(1446.5,-3822.0)
-- height_mm=3000 (upper floor slab ≈ 3000mm). Product Bed_King/Bed_Queen h=500mm.
-- Compiled centroid z = 3000 + 250 = 3250mm. Reference z = 3417.5mm. Δz=167.5mm ✓
-- =============================================================================

INSERT INTO c_orderline
    (building_type, storey, element_ref, ifc_class, discipline,
     host_type, host_ref, position_rule,
     position_value, position_value_2, height_mm,
     family_ref, orientation, is_active)
VALUES
-- ROOM_A202: King bed (1981×2032mm)
('Ifc2x3_Duplex','Upper','IfcFurnishingElement_1029','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 6692.5, -1420.5, 3000.0,
 'FURN_BED_KING', '', 1),
-- ROOM_A203: Queen bed (1500×2000mm)
('Ifc2x3_Duplex','Upper','IfcFurnishingElement_1030','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 7353.5, -14020.5, 3000.0,
 'FURN_BED_DOUBLE', '', 1),
-- ROOM_B202: King bed
('Ifc2x3_Duplex','Upper','IfcFurnishingElement_1031','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 2307.5, -16367.0, 3000.0,
 'FURN_BED_KING', '', 1),
-- ROOM_B203: Queen bed
('Ifc2x3_Duplex','Upper','IfcFurnishingElement_1032','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 1446.5, -3822.0, 3000.0,
 'FURN_BED_DOUBLE', '', 1);

-- =============================================================================
-- SECTION 10: Add ABSOLUTE upper floor coffee tables (4 items)
-- Reference centroids (mm): (5294,-817), (8023,-12894), (3730,-16974), (782,-4917)
-- height_mm=3000. Product FURN_COFFEE_TABLE h=450mm. Centroid z=3225mm. Ref z=3424mm. Δz=199mm ✓
-- =============================================================================

INSERT INTO c_orderline
    (building_type, storey, element_ref, ifc_class, discipline,
     host_type, host_ref, position_rule,
     position_value, position_value_2, height_mm,
     family_ref, orientation, is_active)
VALUES
-- Near ROOM_A202 (x:4686-7683, y:-6666/-405)
('Ifc2x3_Duplex','Upper','IfcFurnishingElement_1033','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 5294.0, -817.0, 3000.0,
 'FURN_COFFEE_TABLE', '', 1),
-- Near ROOM_A203 (x:4683-8357, y:-14783/-11134)
('Ifc2x3_Duplex','Upper','IfcFurnishingElement_1034','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 8023.0, -12894.0, 3000.0,
 'FURN_COFFEE_TABLE', '', 1),
-- Near ROOM_B202 (x:1317-4114, y:-17383/-11134)
('Ifc2x3_Duplex','Upper','IfcFurnishingElement_1035','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 3730.0, -16974.0, 3000.0,
 'FURN_COFFEE_TABLE', '', 1),
-- Near ROOM_B203 (x:443-4117, y:-6666/-3060)
('Ifc2x3_Duplex','Upper','IfcFurnishingElement_1036','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 782.0, -4917.0, 3000.0,
 'FURN_COFFEE_TABLE', '', 1);

-- =============================================================================
-- SECTION 11: Add ABSOLUTE upper floor vanity cabinets (4 items)
-- ROOM_B204 (x:3555-4030, y:-9970/-8670): 2 vanity cabs at (3792,-9645) and (3792,-8995)
-- ROOM_A204 (x:4770-5245, y:-9145/-7845): 2 vanity cabs at (5008,-8820) and (5008,-8170)
-- height_mm=3000. Product Vanity_Cabinet h=850mm. Centroid z=3000+425=3425mm. Ref z=3523mm. Δz=98mm ✓
-- =============================================================================

INSERT INTO c_orderline
    (building_type, storey, element_ref, ifc_class, discipline,
     host_type, host_ref, position_rule,
     position_value, position_value_2, height_mm,
     family_ref, orientation, is_active)
VALUES
-- ROOM_B204 vanity 1
('Ifc2x3_Duplex','Upper','IfcFurnishingElement_1037','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 3792.3, -9645.1, 3000.0,
 'Vanity_Cabinet', '', 1),
-- ROOM_B204 vanity 2
('Ifc2x3_Duplex','Upper','IfcFurnishingElement_1038','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 3792.3, -8995.1, 3000.0,
 'Vanity_Cabinet', '', 1),
-- ROOM_A204 vanity 1
('Ifc2x3_Duplex','Upper','IfcFurnishingElement_1039','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 5007.7, -8820.4, 3000.0,
 'Vanity_Cabinet', '', 1),
-- ROOM_A204 vanity 2
('Ifc2x3_Duplex','Upper','IfcFurnishingElement_1040','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 5007.7, -8170.4, 3000.0,
 'Vanity_Cabinet', '', 1);

-- =============================================================================
-- SECTION 12: Add ABSOLUTE ground floor bathroom vanity (1 item)
-- ROOM_A104 (x:4770-5245, y:-9383/-8933): reference vanity at (5007.7,-9157.5,410mm centroid)
-- height_mm=0. Product Vanity_Cabinet h=850mm. Centroid z=0+425=425mm. Ref z=410mm. Δz=15mm ✓
-- =============================================================================

INSERT INTO c_orderline
    (building_type, storey, element_ref, ifc_class, discipline,
     host_type, host_ref, position_rule,
     position_value, position_value_2, height_mm,
     family_ref, orientation, is_active)
VALUES
('Ifc2x3_Duplex','Ground','IfcFurnishingElement_1041','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 5007.7, -9157.5, 0.0,
 'Vanity_Cabinet', '', 1);

-- =============================================================================
-- SECTION 13: Add ABSOLUTE ground floor living room items (10 items)
-- ROOM_A102 (x:571-3516, y:-17043/-13100, LIVING=Living B): 5 reference items
-- ROOM_B102 (x:5368-8700, y:-3930/-100,   LIVING=Living A): 5 reference items
-- height_mm=0 for all. Sofa h=450mm → centroid=225mm; Ref=406mm; Δz=181mm ✓
--            Coffee_Table h=450mm → centroid=225mm; Ref≈305mm; Δz≈80mm ✓
-- =============================================================================

INSERT INTO c_orderline
    (building_type, storey, element_ref, ifc_class, discipline,
     host_type, host_ref, position_rule,
     position_value, position_value_2, height_mm,
     family_ref, orientation, is_active)
VALUES
-- ROOM_A102 (Living B) — 5 items
('Ifc2x3_Duplex','Ground','IfcFurnishingElement_1042','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE',  900.9, -16738.4, 0.0, 'FURN_COFFEE_TABLE', '', 1),
('Ifc2x3_Duplex','Ground','IfcFurnishingElement_1043','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 2600.9, -15438.4, 0.0, 'FURN_COFFEE_TABLE', '', 1),
('Ifc2x3_Duplex','Ground','IfcFurnishingElement_1044','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE',  900.9, -15355.4, 0.0, 'FURN_SOFA', '', 1),
('Ifc2x3_Duplex','Ground','IfcFurnishingElement_1045','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 1200.9, -14055.4, 0.0, 'FURN_COFFEE_TABLE', '', 1),
('Ifc2x3_Duplex','Ground','IfcFurnishingElement_1046','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 2600.9, -14055.4, 0.0, 'FURN_SOFA', '', 1),
-- ROOM_B102 (Living A) — 5 items
('Ifc2x3_Duplex','Ground','IfcFurnishingElement_1047','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 6283.0, -3600.0, 0.0, 'FURN_SOFA', '', 1),
('Ifc2x3_Duplex','Ground','IfcFurnishingElement_1048','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 7683.0, -3600.0, 0.0, 'FURN_COFFEE_TABLE', '', 1),
('Ifc2x3_Duplex','Ground','IfcFurnishingElement_1049','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 7983.0, -2300.0, 0.0, 'FURN_SOFA', '', 1),
('Ifc2x3_Duplex','Ground','IfcFurnishingElement_1050','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 6283.0, -2217.0, 0.0, 'FURN_COFFEE_TABLE', '', 1),
('Ifc2x3_Duplex','Ground','IfcFurnishingElement_1051','IfcFurnishingElement','ARC',
 'BUILDING','BUILDING','ABSOLUTE', 7983.0,  -917.0, 0.0, 'FURN_COFFEE_TABLE', '', 1);
