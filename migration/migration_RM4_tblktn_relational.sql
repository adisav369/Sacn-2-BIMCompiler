-- Phase RM-4: Populate TB_LKTN relational tables from architectural drawings.
-- GENERATIVE: no IFC source. Data extracted from WD-1/01 (5-sheet Rumah Rakyat drawing set).
-- Pattern: same 5 tables as SH/DX, same RelationalResolver, same position rules.

-- ============================================================
-- 1. GRID LINES (10 lines: 5 X-axis + 5 Y-axis)
-- ============================================================
-- X-axis: 3 structural bays = 3100 + 3700 + 3100 = 9900mm
-- Y-axis: 4 depth bays = 2300 + 3100 + 1600 + 1500 = 8500mm
-- B is a sub-grid at 1300mm (wet zone boundary within first bay)

INSERT INTO ad_building_grid (building_type, axis, grid_label, position_mm, building_id)
VALUES
    ('TB_LKTN', 'X', 'A',    0, 4),
    ('TB_LKTN', 'X', 'B', 1300, 4),
    ('TB_LKTN', 'X', 'C', 3100, 4),
    ('TB_LKTN', 'X', 'D', 6800, 4),
    ('TB_LKTN', 'X', 'E', 9900, 4),
    ('TB_LKTN', 'Y', '1',    0, 4),
    ('TB_LKTN', 'Y', '2', 2300, 4),
    ('TB_LKTN', 'Y', '3', 5400, 4),
    ('TB_LKTN', 'Y', '4', 7000, 4),
    ('TB_LKTN', 'Y', '5', 8500, 4);

-- ============================================================
-- 2. ROOM BOUNDARIES (7 rooms)
-- ============================================================
-- Design intent: 3 same-size SQUARE rooms (3100×3100) + 2 wet + open common + porch
-- All coords in mm, origin at (0,0) = grid A/1

INSERT INTO ad_room_boundary (building_type, storey, room_name, room_type,
    grid_min_x, grid_max_x, grid_min_y, grid_max_y,
    min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id)
VALUES
    ('TB_LKTN', 'Ground Floor', 'anjung',       'PORCH',    'B', 'D', '1', '2',  1300, 6800,    0, 2300, 4),
    ('TB_LKTN', 'Ground Floor', 'bilik_utama',  'BEDROOM',  'A', 'C', '2', '3',     0, 3100, 2300, 5400, 4),
    ('TB_LKTN', 'Ground Floor', 'bilik_2',      'BEDROOM',  'D', 'E', '2', '3',  6800, 9900, 2300, 5400, 4),
    ('TB_LKTN', 'Ground Floor', 'bilik_3',      'BEDROOM',  'D', 'E', '3', '5',  6800, 9900, 5400, 8500, 4),
    ('TB_LKTN', 'Ground Floor', 'tandas',       'TOILET',   'A', 'B', '3', '4',     0, 1300, 5400, 7000, 4),
    ('TB_LKTN', 'Ground Floor', 'bilik_mandi',  'BATHROOM', 'A', 'B', '4', '5',     0, 1300, 7000, 8500, 4),
    ('TB_LKTN', 'Ground Floor', 'common',       'LIVING',   'C', 'D', '2', '5',  3100, 6800, 2300, 8500, 4);

-- ============================================================
-- 3. WALL FACES (28 faces = 7 rooms × 4)
-- ============================================================
-- V1 = 100mm Teablock exterior, V2 = 4mm Teablock interior partition
-- Porch (anjung) has no enclosed walls — open covered area

-- bilik_utama (master bedroom, A-C / 2-3)
INSERT INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, adjacent_room, building_id)
VALUES
    ('TB_LKTN', 'Ground Floor', 'bilik_utama', 'SOUTH', 'EXTERIOR_V1', 1, NULL, 4),
    ('TB_LKTN', 'Ground Floor', 'bilik_utama', 'WEST',  'EXTERIOR_V1', 1, NULL, 4),
    ('TB_LKTN', 'Ground Floor', 'bilik_utama', 'NORTH', 'INTERIOR_V2', 0, 'tandas', 4),
    ('TB_LKTN', 'Ground Floor', 'bilik_utama', 'EAST',  'INTERIOR_V2', 0, 'common', 4);

-- bilik_2 (bedroom 2, D-E / 2-3)
INSERT INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, adjacent_room, building_id)
VALUES
    ('TB_LKTN', 'Ground Floor', 'bilik_2', 'SOUTH', 'EXTERIOR_V1', 1, NULL, 4),
    ('TB_LKTN', 'Ground Floor', 'bilik_2', 'EAST',  'EXTERIOR_V1', 1, NULL, 4),
    ('TB_LKTN', 'Ground Floor', 'bilik_2', 'NORTH', 'INTERIOR_V2', 0, 'bilik_3', 4),
    ('TB_LKTN', 'Ground Floor', 'bilik_2', 'WEST',  'INTERIOR_V2', 0, 'common', 4);

-- bilik_3 (bedroom 3, D-E / 3-5)
INSERT INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, adjacent_room, building_id)
VALUES
    ('TB_LKTN', 'Ground Floor', 'bilik_3', 'NORTH', 'EXTERIOR_V1', 1, NULL, 4),
    ('TB_LKTN', 'Ground Floor', 'bilik_3', 'EAST',  'EXTERIOR_V1', 1, NULL, 4),
    ('TB_LKTN', 'Ground Floor', 'bilik_3', 'SOUTH', 'INTERIOR_V2', 0, 'bilik_2', 4),
    ('TB_LKTN', 'Ground Floor', 'bilik_3', 'WEST',  'INTERIOR_V2', 0, 'common', 4);

-- tandas (toilet, A-B / 3-4)
INSERT INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, adjacent_room, building_id)
VALUES
    ('TB_LKTN', 'Ground Floor', 'tandas', 'NORTH', 'INTERIOR_V2', 0, 'bilik_mandi', 4),
    ('TB_LKTN', 'Ground Floor', 'tandas', 'WEST',  'EXTERIOR_V1', 1, NULL, 4),
    ('TB_LKTN', 'Ground Floor', 'tandas', 'SOUTH', 'INTERIOR_V2', 0, 'bilik_utama', 4),
    ('TB_LKTN', 'Ground Floor', 'tandas', 'EAST',  'INTERIOR_V2', 0, 'common', 4);

-- bilik_mandi (bathroom, A-B / 4-5)
INSERT INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, adjacent_room, building_id)
VALUES
    ('TB_LKTN', 'Ground Floor', 'bilik_mandi', 'NORTH', 'EXTERIOR_V1', 1, NULL, 4),
    ('TB_LKTN', 'Ground Floor', 'bilik_mandi', 'WEST',  'EXTERIOR_V1', 1, NULL, 4),
    ('TB_LKTN', 'Ground Floor', 'bilik_mandi', 'SOUTH', 'INTERIOR_V2', 0, 'tandas', 4),
    ('TB_LKTN', 'Ground Floor', 'bilik_mandi', 'EAST',  'INTERIOR_V2', 0, 'common', 4);

-- common (living+kitchen open plan, C-D / 2-5)
INSERT INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, adjacent_room, building_id)
VALUES
    ('TB_LKTN', 'Ground Floor', 'common', 'SOUTH', 'EXTERIOR_V1', 1, NULL, 4),
    ('TB_LKTN', 'Ground Floor', 'common', 'NORTH', 'EXTERIOR_V1', 1, NULL, 4),
    ('TB_LKTN', 'Ground Floor', 'common', 'WEST',  'INTERIOR_V2', 0, 'bilik_utama', 4),
    ('TB_LKTN', 'Ground Floor', 'common', 'EAST',  'INTERIOR_V2', 0, 'bilik_2', 4);

-- anjung (porch, open — no enclosed walls, but we record faces for completeness)
INSERT INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, adjacent_room, building_id)
VALUES
    ('TB_LKTN', 'Ground Floor', 'anjung', 'SOUTH', 'NONE', 0, NULL, 4),
    ('TB_LKTN', 'Ground Floor', 'anjung', 'NORTH', 'NONE', 0, 'common', 4),
    ('TB_LKTN', 'Ground Floor', 'anjung', 'WEST',  'NONE', 0, NULL, 4),
    ('TB_LKTN', 'Ground Floor', 'anjung', 'EAST',  'NONE', 0, NULL, 4);

-- ============================================================
-- 4. ELEMENT RULES
-- ============================================================
-- Position rules follow SH/DX patterns:
--   ENVELOPE = building-level (slab, roof)
--   BOUNDARY = wall on room edge (center computed from room extents)
--   FRACTION = fractional position on wall/room host
-- All dimensions in mm. Heights in mm from ground floor (0).
-- Coordinates computed by RelationalResolver at compile time.

-- ── 4a. SLABS (ENVELOPE) ──────────────────────────────────
-- Ground slab: ENCLOSED AREA ONLY (grid 2-5, Y: 2300-8500), 150mm thick
-- Porch has its own slab — no overlap
INSERT INTO ad_element_rule (building_type, storey, element_ref, ifc_class, discipline,
    host_type, host_ref, position_rule, position_value, position_value_2,
    height_mm, width_mm, height_extent_mm, depth_mm,
    orientation, material_name, material_rgba, building_id)
VALUES
    ('TB_LKTN', 'Ground Floor', 'IfcSlab_1', 'IfcSlab', 'ARC',
     'BUILDING', 'BUILDING', 'ABSOLUTE', 4950, 5400,
     0, 9900, 150, 6200,
     NULL, 'Concrete_Slab', '200,200,200,255', 4),
-- Porch slab: apron level (-150mm), covers porch area (B-D / 1-2)
    ('TB_LKTN', 'Ground Floor', 'IfcSlab_2', 'IfcSlab', 'ARC',
     'BUILDING', 'BUILDING', 'ABSOLUTE', 4050, 1150,
     -150, 5500, 150, 2300,
     NULL, 'Concrete_Apron', '180,180,180,255', 4);

-- ── 4b. EXTERIOR WALLS (BOUNDARY on room edges) ──────────
-- V1 = 100mm Teablock, 3000mm high, placed at room boundaries
-- Wall center is at the room edge; width=100mm (wall thickness), depth=wall length

-- bilik_utama SOUTH wall (grid 2 line, X: 0-3100)
INSERT INTO ad_element_rule (building_type, storey, element_ref, ifc_class, discipline,
    host_type, host_ref, position_rule, position_value, position_value_2,
    height_mm, width_mm, height_extent_mm, depth_mm,
    orientation, material_name, material_rgba, building_id)
VALUES
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_1', 'IfcPlate', 'ARC',
     'WALL', 'WALL_bilik_utama_SOUTH', 'BOUNDARY', 1550, 2300,
     0, 3100, 3000, 100,
     'EW', 'Teablock_V1_100', '220,210,200,255', 4),
-- bilik_utama WEST wall (grid A line, Y: 2300-5400)
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_2', 'IfcPlate', 'ARC',
     'WALL', 'WALL_bilik_utama_WEST', 'BOUNDARY', 0, 3850,
     0, 100, 3000, 3100,
     'NS', 'Teablock_V1_100', '220,210,200,255', 4),
-- bilik_2 SOUTH wall (grid 2 line, X: 6800-9900)
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_3', 'IfcPlate', 'ARC',
     'WALL', 'WALL_bilik_2_SOUTH', 'BOUNDARY', 8350, 2300,
     0, 3100, 3000, 100,
     'EW', 'Teablock_V1_100', '220,210,200,255', 4),
-- bilik_2 EAST wall (grid E line, Y: 2300-5400)
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_4', 'IfcPlate', 'ARC',
     'WALL', 'WALL_bilik_2_EAST', 'BOUNDARY', 9900, 3850,
     0, 100, 3000, 3100,
     'NS', 'Teablock_V1_100', '220,210,200,255', 4),
-- bilik_3 NORTH wall (grid 5 line, X: 6800-9900)
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_5', 'IfcPlate', 'ARC',
     'WALL', 'WALL_bilik_3_NORTH', 'BOUNDARY', 8350, 8500,
     0, 3100, 3000, 100,
     'EW', 'Teablock_V1_100', '220,210,200,255', 4),
-- bilik_3 EAST wall (grid E line, Y: 5400-8500)
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_6', 'IfcPlate', 'ARC',
     'WALL', 'WALL_bilik_3_EAST', 'BOUNDARY', 9900, 6950,
     0, 100, 3000, 3100,
     'NS', 'Teablock_V1_100', '220,210,200,255', 4),
-- common SOUTH wall (grid 2 line, X: 3100-6800) — front wall
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_7', 'IfcPlate', 'ARC',
     'WALL', 'WALL_common_SOUTH', 'BOUNDARY', 4950, 2300,
     0, 3700, 3000, 100,
     'EW', 'Teablock_V1_100', '220,210,200,255', 4),
-- common NORTH wall (grid 5 line, X: 3100-6800) — rear wall
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_8', 'IfcPlate', 'ARC',
     'WALL', 'WALL_common_NORTH', 'BOUNDARY', 4950, 8500,
     0, 3700, 3000, 100,
     'EW', 'Teablock_V1_100', '220,210,200,255', 4),
-- tandas WEST wall (grid A line, Y: 5400-7000)
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_9', 'IfcPlate', 'ARC',
     'WALL', 'WALL_tandas_WEST', 'BOUNDARY', 0, 6200,
     0, 100, 3000, 1600,
     'NS', 'Teablock_V1_100', '220,210,200,255', 4),
-- bilik_mandi WEST wall (grid A line, Y: 7000-8500)
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_10', 'IfcPlate', 'ARC',
     'WALL', 'WALL_bilik_mandi_WEST', 'BOUNDARY', 0, 7750,
     0, 100, 3000, 1500,
     'NS', 'Teablock_V1_100', '220,210,200,255', 4),
-- bilik_mandi NORTH wall (grid 5 line, X: 0-1300)
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_11', 'IfcPlate', 'ARC',
     'WALL', 'WALL_bilik_mandi_NORTH', 'BOUNDARY', 650, 8500,
     0, 1300, 3000, 100,
     'EW', 'Teablock_V1_100', '220,210,200,255', 4);

-- ── 4c. INTERIOR PARTITION WALLS (V2 = 4mm Teablock) ─────
INSERT INTO ad_element_rule (building_type, storey, element_ref, ifc_class, discipline,
    host_type, host_ref, position_rule, position_value, position_value_2,
    height_mm, width_mm, height_extent_mm, depth_mm,
    orientation, material_name, material_rgba, building_id)
VALUES
-- bilik_utama EAST / common WEST partition (grid C line, Y: 2300-5400)
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_12', 'IfcPlate', 'ARC',
     'WALL', 'WALL_bilik_utama_EAST', 'BOUNDARY', 3100, 3850,
     0, 4, 3000, 3100,
     'NS', 'Teablock_V2_4', '230,220,210,255', 4),
-- bilik_2 WEST / common EAST partition (grid D line, Y: 2300-5400)
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_13', 'IfcPlate', 'ARC',
     'WALL', 'WALL_bilik_2_WEST', 'BOUNDARY', 6800, 3850,
     0, 4, 3000, 3100,
     'NS', 'Teablock_V2_4', '230,220,210,255', 4),
-- bilik_3 WEST / common EAST partition (grid D line, Y: 5400-8500)
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_14', 'IfcPlate', 'ARC',
     'WALL', 'WALL_bilik_3_WEST', 'BOUNDARY', 6800, 6950,
     0, 4, 3000, 3100,
     'NS', 'Teablock_V2_4', '230,220,210,255', 4),
-- bilik_utama NORTH / tandas SOUTH partition (grid 3 line, X: 0-3100)
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_15', 'IfcPlate', 'ARC',
     'WALL', 'WALL_bilik_utama_NORTH', 'BOUNDARY', 1550, 5400,
     0, 3100, 3000, 4,
     'EW', 'Teablock_V2_4', '230,220,210,255', 4),
-- bilik_2 NORTH / bilik_3 SOUTH partition (grid 3 line, X: 6800-9900)
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_16', 'IfcPlate', 'ARC',
     'WALL', 'WALL_bilik_2_NORTH', 'BOUNDARY', 8350, 5400,
     0, 3100, 3000, 4,
     'EW', 'Teablock_V2_4', '230,220,210,255', 4),
-- tandas NORTH / bilik_mandi SOUTH partition (grid 4 line, X: 0-1300)
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_17', 'IfcPlate', 'ARC',
     'WALL', 'WALL_tandas_NORTH', 'BOUNDARY', 650, 7000,
     0, 1300, 3000, 4,
     'EW', 'Teablock_V2_4', '230,220,210,255', 4),
-- tandas EAST / common partition (grid B line, Y: 5400-7000)
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_18', 'IfcPlate', 'ARC',
     'WALL', 'WALL_tandas_EAST', 'BOUNDARY', 1300, 6200,
     0, 4, 3000, 1600,
     'NS', 'Teablock_V2_4', '230,220,210,255', 4),
-- bilik_mandi EAST / common partition (grid B line, Y: 7000-8500)
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_19', 'IfcPlate', 'ARC',
     'WALL', 'WALL_bilik_mandi_EAST', 'BOUNDARY', 1300, 7750,
     0, 4, 3000, 1500,
     'NS', 'Teablock_V2_4', '230,220,210,255', 4);

-- ── 4d. ROOF (ENVELOPE) ──────────────────────────────────
INSERT INTO ad_element_rule (building_type, storey, element_ref, ifc_class, discipline,
    host_type, host_ref, position_rule, position_value, position_value_2,
    height_mm, width_mm, height_extent_mm, depth_mm,
    orientation, material_name, material_rgba, building_id)
VALUES
    ('TB_LKTN', 'Ground Floor', 'IfcRoof_1', 'IfcRoof', 'ARC',
     'BUILDING', 'BUILDING', 'ENVELOPE', 4950, 4250,
     3000, 11300, 500, 9900,
     NULL, 'Metal_Roofing_R1', '150,50,50,255', 4);

-- ── 4e. DOORS (FRACTION on wall host) ────────────────────
-- D1: Main entry 900×2100 on common SOUTH wall (front door, centered in porch opening)
-- D2: Internal doors 800×2100 between rooms and common
-- D3: Bathroom door 700×2100

INSERT INTO ad_element_rule (building_type, storey, element_ref, ifc_class, discipline,
    host_type, host_ref, position_rule, position_value, position_value_2,
    height_mm, width_mm, height_extent_mm, depth_mm,
    orientation, material_name, material_rgba, building_id)
VALUES
-- D1: Main entry — common SOUTH wall, centered (fraction 0.5)
    ('TB_LKTN', 'Ground Floor', 'IfcDoor_1', 'IfcDoor', 'ARC',
     'WALL', 'WALL_common_SOUTH', 'FRACTION', 0.5, 0,
     0, 900, 2100, 100,
     'EW', 'Door_D1_Main', '139,90,43,255', 4),
-- D2: bilik_utama entry — bilik_utama EAST wall (NS), fraction 0.3
-- Convention: width_mm=X extent, depth_mm=Y extent (absolute, like SH extractor)
    ('TB_LKTN', 'Ground Floor', 'IfcDoor_2', 'IfcDoor', 'ARC',
     'WALL', 'WALL_bilik_utama_EAST', 'FRACTION', 0.3, 0,
     0, 4, 2100, 800,
     'NS', 'Door_D2_Internal', '139,90,43,255', 4),
-- D2: bilik_2 entry — bilik_2 WEST wall (NS), fraction 0.3
    ('TB_LKTN', 'Ground Floor', 'IfcDoor_3', 'IfcDoor', 'ARC',
     'WALL', 'WALL_bilik_2_WEST', 'FRACTION', 0.3, 0,
     0, 4, 2100, 800,
     'NS', 'Door_D2_Internal', '139,90,43,255', 4),
-- D2: bilik_3 entry — bilik_3 WEST wall (NS), fraction 0.3
    ('TB_LKTN', 'Ground Floor', 'IfcDoor_4', 'IfcDoor', 'ARC',
     'WALL', 'WALL_bilik_3_WEST', 'FRACTION', 0.3, 0,
     0, 4, 2100, 800,
     'NS', 'Door_D2_Internal', '139,90,43,255', 4),
-- D3: tandas entry — tandas EAST wall (NS), fraction 0.5
    ('TB_LKTN', 'Ground Floor', 'IfcDoor_5', 'IfcDoor', 'ARC',
     'WALL', 'WALL_tandas_EAST', 'FRACTION', 0.5, 0,
     0, 4, 2100, 700,
     'NS', 'Door_D3_Bathroom', '139,90,43,255', 4),
-- D3: bilik_mandi entry — bilik_mandi EAST wall (NS), fraction 0.5
    ('TB_LKTN', 'Ground Floor', 'IfcDoor_6', 'IfcDoor', 'ARC',
     'WALL', 'WALL_bilik_mandi_EAST', 'FRACTION', 0.5, 0,
     0, 4, 2100, 700,
     'NS', 'Door_D3_Bathroom', '139,90,43,255', 4);

-- ── 4f. WINDOWS (FRACTION on exterior wall host) ─────────
-- From front/rear elevations: paired windows on exterior walls
-- W1: 1200×1200, sill at 900mm
-- W2: 1800×1200, sill at 900mm (large, for common area)

INSERT INTO ad_element_rule (building_type, storey, element_ref, ifc_class, discipline,
    host_type, host_ref, position_rule, position_value, position_value_2,
    height_mm, width_mm, height_extent_mm, depth_mm,
    orientation, material_name, material_rgba, building_id)
VALUES
-- Front elevation (SOUTH walls): 3 window sets visible
-- W1 on bilik_utama SOUTH, centered
    ('TB_LKTN', 'Ground Floor', 'IfcWindow_1', 'IfcWindow', 'ARC',
     'WALL', 'WALL_bilik_utama_SOUTH', 'FRACTION', 0.5, 0,
     900, 1200, 1200, 100,
     'EW', 'Window_W1', '180,220,255,160', 4),
-- W2 on common SOUTH (large window), fraction 0.25 (left of door)
    ('TB_LKTN', 'Ground Floor', 'IfcWindow_2', 'IfcWindow', 'ARC',
     'WALL', 'WALL_common_SOUTH', 'FRACTION', 0.25, 0,
     900, 1800, 1200, 100,
     'EW', 'Window_W2', '180,220,255,160', 4),
-- W2 on common SOUTH (large window), fraction 0.75 (right of door)
    ('TB_LKTN', 'Ground Floor', 'IfcWindow_3', 'IfcWindow', 'ARC',
     'WALL', 'WALL_common_SOUTH', 'FRACTION', 0.75, 0,
     900, 1800, 1200, 100,
     'EW', 'Window_W2', '180,220,255,160', 4),
-- W1 on bilik_2 SOUTH, centered
    ('TB_LKTN', 'Ground Floor', 'IfcWindow_4', 'IfcWindow', 'ARC',
     'WALL', 'WALL_bilik_2_SOUTH', 'FRACTION', 0.5, 0,
     900, 1200, 1200, 100,
     'EW', 'Window_W1', '180,220,255,160', 4),
-- Rear elevation (NORTH walls): windows on bedrooms + common
-- W1 on bilik_3 NORTH, centered
    ('TB_LKTN', 'Ground Floor', 'IfcWindow_5', 'IfcWindow', 'ARC',
     'WALL', 'WALL_bilik_3_NORTH', 'FRACTION', 0.5, 0,
     900, 1200, 1200, 100,
     'EW', 'Window_W1', '180,220,255,160', 4),
-- W1 on common NORTH, centered
    ('TB_LKTN', 'Ground Floor', 'IfcWindow_6', 'IfcWindow', 'ARC',
     'WALL', 'WALL_common_NORTH', 'FRACTION', 0.5, 0,
     900, 1200, 1200, 100,
     'EW', 'Window_W1', '180,220,255,160', 4),
-- W1 on bilik_mandi NORTH (small louvre W4 actually, but using W3)
    ('TB_LKTN', 'Ground Floor', 'IfcWindow_7', 'IfcWindow', 'ARC',
     'WALL', 'WALL_bilik_mandi_NORTH', 'FRACTION', 0.5, 0,
     900, 600, 600, 100,
     'EW', 'Window_W3_Small', '180,220,255,160', 4),
-- Left elevation (WEST walls): bilik_utama WEST + tandas WEST
-- W1 on bilik_utama WEST (NS) — width_mm=X(thin), depth_mm=Y(wide)
    ('TB_LKTN', 'Ground Floor', 'IfcWindow_8', 'IfcWindow', 'ARC',
     'WALL', 'WALL_bilik_utama_WEST', 'FRACTION', 0.5, 0,
     900, 100, 1200, 1200,
     'NS', 'Window_W1', '180,220,255,160', 4),
-- W3 on tandas WEST (NS) (small toilet window)
    ('TB_LKTN', 'Ground Floor', 'IfcWindow_9', 'IfcWindow', 'ARC',
     'WALL', 'WALL_tandas_WEST', 'FRACTION', 0.5, 0,
     900, 100, 600, 600,
     'NS', 'Window_W3_Small', '180,220,255,160', 4),
-- Right elevation (EAST walls): bilik_2 EAST + bilik_3 EAST
-- W1 on bilik_2 EAST (NS)
    ('TB_LKTN', 'Ground Floor', 'IfcWindow_10', 'IfcWindow', 'ARC',
     'WALL', 'WALL_bilik_2_EAST', 'FRACTION', 0.5, 0,
     900, 100, 1200, 1200,
     'NS', 'Window_W1', '180,220,255,160', 4),
-- W1 on bilik_3 EAST (NS)
    ('TB_LKTN', 'Ground Floor', 'IfcWindow_11', 'IfcWindow', 'ARC',
     'WALL', 'WALL_bilik_3_EAST', 'FRACTION', 0.5, 0,
     900, 100, 1200, 1200,
     'NS', 'Window_W1', '180,220,255,160', 4);

-- ── 4g. FIXTURES (FRACTION on ROOM host) ─────────────────
-- Kitchen sink in common area (rear, near grid 5)
-- Basin + WC + shower in bilik_mandi
-- WC in tandas

INSERT INTO ad_element_rule (building_type, storey, element_ref, ifc_class, discipline,
    host_type, host_ref, position_rule, position_value, position_value_2,
    height_mm, width_mm, height_extent_mm, depth_mm,
    orientation, material_name, material_rgba, building_id)
VALUES
-- Kitchen sink — common area, rear wall (fraction 0.3 X, 0.95 Y = near rear)
    ('TB_LKTN', 'Ground Floor', 'IfcFurnishingElement_1', 'IfcFurnishingElement', 'ARC',
     'ROOM', 'common', 'FRACTION', 0.3, 0.95,
     850, 1200, 200, 600,
     'EW', 'Kitchen_Sink_Aluminium', '200,200,200,255', 4),
-- Basin — bilik_mandi, near wall (fraction 0.5 X, 0.8 Y)
    ('TB_LKTN', 'Ground Floor', 'IfcFurnishingElement_2', 'IfcFurnishingElement', 'ARC',
     'ROOM', 'bilik_mandi', 'FRACTION', 0.5, 0.8,
     800, 500, 200, 400,
     'EW', 'Basin', '255,255,255,255', 4),
-- WC — bilik_mandi, centered (fraction 0.5 X, 0.3 Y)
    ('TB_LKTN', 'Ground Floor', 'IfcFurnishingElement_3', 'IfcFurnishingElement', 'ARC',
     'ROOM', 'bilik_mandi', 'FRACTION', 0.5, 0.3,
     0, 400, 400, 700,
     'NS', 'WC_Seating', '255,255,255,255', 4),
-- Shower — bilik_mandi, corner (fraction 0.2 X, 0.5 Y)
    ('TB_LKTN', 'Ground Floor', 'IfcFurnishingElement_4', 'IfcFurnishingElement', 'ARC',
     'ROOM', 'bilik_mandi', 'FRACTION', 0.2, 0.5,
     0, 900, 2000, 900,
     NULL, 'Shower', '200,230,255,255', 4),
-- WC — tandas (fraction 0.5 X, 0.5 Y)
    ('TB_LKTN', 'Ground Floor', 'IfcFurnishingElement_5', 'IfcFurnishingElement', 'ARC',
     'ROOM', 'tandas', 'FRACTION', 0.5, 0.5,
     0, 400, 400, 700,
     'NS', 'WC_Seating', '255,255,255,255', 4);

-- ── 4h. FURNITURE (LOD400 keyword-matched to component library) ──
-- element_ref contains keywords that resolveComponentHash() maps to library components:
--   "bed" + "queen/single" → Bed_Queen / Bed_Single (real LOD400 mesh)
--   "sofa" → Sofa (real mesh)
--   "coffee" + "table" → Coffee_Table_Rect_1200 (real mesh)
INSERT INTO ad_element_rule (building_type, storey, element_ref, ifc_class, discipline,
    host_type, host_ref, position_rule, position_value, position_value_2,
    height_mm, width_mm, height_extent_mm, depth_mm,
    orientation, material_name, material_rgba, building_id)
VALUES
-- Bed_Queen — bilik_utama (master bedroom), centered, rear half
    ('TB_LKTN', 'Ground Floor', 'Furniture_Bed_Queen_bilik_utama', 'IfcFurnishingElement', 'ARC',
     'ROOM', 'bilik_utama', 'FRACTION', 0.5, 0.65,
     150, 1525, 635, 2007,
     'NS', 'Bed_Wood', '160,120,80,255', 4),
-- Bed_Single — bilik_2, centered, rear half
    ('TB_LKTN', 'Ground Floor', 'Furniture_Bed_Single_bilik_2', 'IfcFurnishingElement', 'ARC',
     'ROOM', 'bilik_2', 'FRACTION', 0.5, 0.65,
     150, 900, 600, 1900,
     'NS', 'Bed_Wood', '160,120,80,255', 4),
-- Bed_Single — bilik_3, centered, rear half
    ('TB_LKTN', 'Ground Floor', 'Furniture_Bed_Single_bilik_3', 'IfcFurnishingElement', 'ARC',
     'ROOM', 'bilik_3', 'FRACTION', 0.5, 0.65,
     150, 900, 600, 1900,
     'NS', 'Bed_Wood', '160,120,80,255', 4),
-- Sofa — common area, living zone front
    ('TB_LKTN', 'Ground Floor', 'Furniture_Sofa_common', 'IfcFurnishingElement', 'ARC',
     'ROOM', 'common', 'FRACTION', 0.5, 0.2,
     150, 2287, 958, 977,
     'EW', 'Sofa_Fabric', '120,80,60,255', 4),
-- Coffee_Table — common area, in front of sofa
    ('TB_LKTN', 'Ground Floor', 'Furniture_Coffee_Table_common', 'IfcFurnishingElement', 'ARC',
     'ROOM', 'common', 'FRACTION', 0.5, 0.12,
     150, 1200, 450, 550,
     'EW', 'Coffee_Table_Wood', '140,100,60,255', 4);

-- ── 4i. PERIMETER V-DRAIN (IfcSlab with drain profile) ───
-- 230mm wide concrete V-drain at roof drip line (700mm from walls)
-- 150mm deep, sits below ground level (z: -150 to 0)
-- 8 segments following box+porch roof outline, clockwise from rear
-- Entry/exit discharge at porch-to-building junctions: (600,1600) and (7500,1600)
--
-- Drain perimeter (looking from top):
--          600    7500
--           |      |
--      -700 +------+ -700     ← porch front drip line
--           |      |
--           | PORCH|
--           |      |
-- -700 +----+      +----+ 1600  ← main building front drip line
--      |                 |
--      |   MAIN BLDG     |
--      |                 |
-- -700 +-----------------+ 9200  ← rear drip line
--                      10600

INSERT INTO ad_element_rule (building_type, storey, element_ref, ifc_class, discipline,
    host_type, host_ref, position_rule, position_value, position_value_2,
    height_mm, width_mm, height_extent_mm, depth_mm,
    orientation, material_name, material_rgba, building_id)
VALUES
-- Seg 1: REAR (X: -700→10600, at Y=9200)
    ('TB_LKTN', 'Ground Floor', 'IfcSlab_drain_1', 'IfcSlab', 'ARC',
     'BUILDING', 'BUILDING', 'ABSOLUTE', 4950, 9200,
     -150, 11300, 150, 230,
     'EW', 'Concrete_VDrain_230', '160,160,160,255', 4),
-- Seg 2: RIGHT (X=10600, Y: 1370→9430) — extended 230mm each end for corner overlap
    ('TB_LKTN', 'Ground Floor', 'IfcSlab_drain_2', 'IfcSlab', 'ARC',
     'BUILDING', 'BUILDING', 'ABSOLUTE', 10600, 5400,
     -150, 230, 150, 8060,
     'NS', 'Concrete_VDrain_230', '160,160,160,255', 4),
-- Seg 3: FRONT-RIGHT (X: 7500→10600, at Y=1600)
    ('TB_LKTN', 'Ground Floor', 'IfcSlab_drain_3', 'IfcSlab', 'ARC',
     'BUILDING', 'BUILDING', 'ABSOLUTE', 9050, 1600,
     -150, 3100, 150, 230,
     'EW', 'Concrete_VDrain_230', '160,160,160,255', 4),
-- Seg 4: PORCH-RIGHT (X=7500, Y: -930→1830) — extended 230mm each end for corner overlap
    ('TB_LKTN', 'Ground Floor', 'IfcSlab_drain_4', 'IfcSlab', 'ARC',
     'BUILDING', 'BUILDING', 'ABSOLUTE', 7500, 450,
     -150, 230, 150, 2760,
     'NS', 'Concrete_VDrain_230', '160,160,160,255', 4),
-- Seg 5: PORCH-FRONT (X: 600→7500, at Y=-700)
    ('TB_LKTN', 'Ground Floor', 'IfcSlab_drain_5', 'IfcSlab', 'ARC',
     'BUILDING', 'BUILDING', 'ABSOLUTE', 4050, -700,
     -150, 6900, 150, 230,
     'EW', 'Concrete_VDrain_230', '160,160,160,255', 4),
-- Seg 6: PORCH-LEFT (X=600, Y: -930→1830) — extended 230mm each end for corner overlap
    ('TB_LKTN', 'Ground Floor', 'IfcSlab_drain_6', 'IfcSlab', 'ARC',
     'BUILDING', 'BUILDING', 'ABSOLUTE', 600, 450,
     -150, 230, 150, 2760,
     'NS', 'Concrete_VDrain_230', '160,160,160,255', 4),
-- Seg 7: FRONT-LEFT (X: -700→600, at Y=1600)
    ('TB_LKTN', 'Ground Floor', 'IfcSlab_drain_7', 'IfcSlab', 'ARC',
     'BUILDING', 'BUILDING', 'ABSOLUTE', -50, 1600,
     -150, 1300, 150, 230,
     'EW', 'Concrete_VDrain_230', '160,160,160,255', 4),
-- Seg 8: LEFT (X=-700, Y: 1370→9430) — extended 230mm each end for corner overlap
    ('TB_LKTN', 'Ground Floor', 'IfcSlab_drain_8', 'IfcSlab', 'ARC',
     'BUILDING', 'BUILDING', 'ABSOLUTE', -700, 5400,
     -150, 230, 150, 8060,
     'NS', 'Concrete_VDrain_230', '160,160,160,255', 4);

-- ============================================================
-- 4i. SURFACE STYLES (color palette for rendering)
-- ============================================================
-- Malaysian Rumah Rakyat palette — balanced like SampleHouse
-- Format: style_name, surface_r/g/b, transparency, specular_r/g/b, spec_ratio, spec_exp, refl_method, side, source

INSERT OR IGNORE INTO surface_styles VALUES ('Teablock_V1_100', 0.871, 0.839, 0.784, 0.0, 0.871, 0.839, 0.784, 0.3, 64.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Teablock_V2_4', 0.925, 0.910, 0.878, 0.0, 0.925, 0.910, 0.878, 0.3, 64.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Concrete_Slab', 0.753, 0.753, 0.753, 0.0, 0.753, 0.753, 0.753, 0.5, 128.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Concrete_Apron', 0.678, 0.678, 0.678, 0.0, 0.678, 0.678, 0.678, 0.5, 128.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Metal_Roofing_R1', 0.545, 0.184, 0.129, 0.0, 0.545, 0.184, 0.129, 0.6, 200.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Door_D1_Main', 0.541, 0.337, 0.176, 0.0, 0.541, 0.337, 0.176, 0.5, 128.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Door_D2_Internal', 0.835, 0.643, 0.404, 0.0, 0.835, 0.643, 0.404, 0.5, 128.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Door_D3_Bathroom', 0.835, 0.643, 0.404, 0.0, 0.835, 0.643, 0.404, 0.5, 128.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Window_W1', 0.686, 0.816, 0.902, 0.6, 0.686, 0.816, 0.902, 0.7, 200.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Window_W2', 0.686, 0.816, 0.902, 0.6, 0.686, 0.816, 0.902, 0.7, 200.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Window_W3_Small', 0.600, 0.749, 0.851, 0.6, 0.600, 0.749, 0.851, 0.7, 200.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Kitchen_Sink_Aluminium', 0.847, 0.847, 0.847, 0.0, 0.847, 0.847, 0.847, 0.8, 200.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Basin', 0.965, 0.965, 0.953, 0.0, 0.965, 0.965, 0.953, 0.5, 128.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('WC_Seating', 0.965, 0.965, 0.953, 0.0, 0.965, 0.965, 0.953, 0.5, 128.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Shower', 0.847, 0.878, 0.910, 0.3, 0.847, 0.878, 0.910, 0.6, 128.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Concrete_VDrain_230', 0.580, 0.580, 0.580, 0.0, 0.580, 0.580, 0.580, 0.3, 64.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Bed_Wood', 0.627, 0.471, 0.314, 0.0, 0.627, 0.471, 0.314, 0.4, 64.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Sofa_Fabric', 0.471, 0.314, 0.235, 0.0, 0.471, 0.314, 0.235, 0.2, 32.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');
INSERT OR IGNORE INTO surface_styles VALUES ('Coffee_Table_Wood', 0.549, 0.392, 0.235, 0.0, 0.549, 0.392, 0.235, 0.5, 100.0, 'NOTDEFINED', 'BOTH', 'GENERATED:TB_LKTN');

-- ============================================================
-- 5. ELEMENT DEPENDENCIES
-- ============================================================
-- Windows/doors hosted on walls, fixtures contained in rooms

INSERT INTO ad_element_dependency (building_type, element_ref, parent_ref, relation, cascade_rule, building_id)
VALUES
    ('TB_LKTN', 'IfcDoor_1',   'IfcPlate_7',  'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcDoor_2',   'IfcPlate_12', 'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcDoor_3',   'IfcPlate_13', 'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcDoor_4',   'IfcPlate_14', 'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcDoor_5',   'IfcPlate_18', 'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcDoor_6',   'IfcPlate_19', 'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcWindow_1', 'IfcPlate_1',  'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcWindow_2', 'IfcPlate_7',  'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcWindow_3', 'IfcPlate_7',  'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcWindow_4', 'IfcPlate_3',  'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcWindow_5', 'IfcPlate_5',  'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcWindow_6', 'IfcPlate_8',  'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcWindow_7', 'IfcPlate_11', 'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcWindow_8', 'IfcPlate_2',  'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcWindow_9', 'IfcPlate_9',  'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcWindow_10','IfcPlate_4',  'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcWindow_11','IfcPlate_6',  'HOSTED_ON', 'RECOMPUTE', 4),
    ('TB_LKTN', 'IfcFurnishingElement_1', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 4),
    ('TB_LKTN', 'IfcFurnishingElement_2', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 4),
    ('TB_LKTN', 'IfcFurnishingElement_3', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 4),
    ('TB_LKTN', 'IfcFurnishingElement_4', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 4),
    ('TB_LKTN', 'IfcFurnishingElement_5', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 4),
    ('TB_LKTN', 'IfcFurnishingElement_6', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 4),
    ('TB_LKTN', 'IfcFurnishingElement_7', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 4),
    ('TB_LKTN', 'IfcFurnishingElement_8', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 4),
    ('TB_LKTN', 'IfcFurnishingElement_9', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 4),
    ('TB_LKTN', 'IfcFurnishingElement_10', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 4);
