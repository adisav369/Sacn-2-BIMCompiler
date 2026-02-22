-- ============================================================
-- G8 Calibration — DX Room Boundary Coordinates
-- Source: [EXTRACTED: Ifc2x3_Duplex_extracted.db]
--   rel_contained_in_space → elements_rtree → MIN/MAX per IfcSpace
--
-- Root cause: 44 rows were a hand-generated grid decomposition
--   (small uniform cells) that bore no relation to actual IFC space extents.
--   The real rooms (A102-B204) are large open spaces spanning many grid cells.
--
-- Strategy: DELETE 44 artificial rows + 168 wall-face rows.
--   INSERT 11 real rooms from reference IFC spaces.
--   INSERT 44 wall-face rows (4 faces × 11 rooms, INTERIOR_PARTITION_92).
--   ad_element_rule is NOT affected (all DX rules use host_ref='BUILDING').
-- ============================================================

BEGIN TRANSACTION;

-- ── Step 1: Remove old grid ───────────────────────────────────────────────

DELETE FROM ad_wall_face     WHERE building_type = 'Ifc2x3_Duplex';
DELETE FROM ad_room_boundary WHERE building_type = 'Ifc2x3_Duplex';

-- ── Step 2: Insert 11 real rooms ─────────────────────────────────────────
-- Columns: building_type, storey, room_name, room_type,
--          grid_min_x, grid_max_x, grid_min_y, grid_max_y,
--          z_offset_mm, is_active,
--          min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id

-- GROUND FLOOR (Z = 0 mm) — Unit A ────────────────────────────────────────

-- A102: LIVING  (2× Sofa, 3× Coffee table)
-- Extracted: guid 0BTBFw6f90Nfh9rP1dlXr2, 5 elements
-- minX=0.5707m, maxX=3.5159m, minY=-17.0434m, maxY=-13.7252m
INSERT INTO ad_room_boundary
  (building_type, storey, room_name, room_type,
   grid_min_x, grid_max_x, grid_min_y, grid_max_y,
   z_offset_mm, is_active,
   min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id)
VALUES
  ('Ifc2x3_Duplex', 'Ground', 'ROOM_A102', 'LIVING',
   '', '', '', '',
   0, 1,
   571.0, 3516.0, -17043.0, -13725.0, 2);

-- A103: KITCHEN (8× Base cabinet, 4× Upper cabinet, 3× Counter top)
-- Extracted: guid 0BTBFw6f90Nfh9rP1dlXr$, 15 elements
-- minX=1.466m, maxX=6.238m, minY=-12.612m, maxY=-10.37m
INSERT INTO ad_room_boundary
  (building_type, storey, room_name, room_type,
   grid_min_x, grid_max_x, grid_min_y, grid_max_y,
   z_offset_mm, is_active,
   min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id)
VALUES
  ('Ifc2x3_Duplex', 'Ground', 'ROOM_A103', 'KITCHEN',
   '', '', '', '',
   0, 1,
   1466.0, 6238.0, -12612.0, -10370.0, 2);

-- A104: BATHROOM (1× Vanity cabinet)
-- Extracted: guid 0BTBFw6f90Nfh9rP1dlXru, 1 element
-- minX=4.77m, maxX=5.2454m, minY=-9.3825m, maxY=-8.9325m
INSERT INTO ad_room_boundary
  (building_type, storey, room_name, room_type,
   grid_min_x, grid_max_x, grid_min_y, grid_max_y,
   z_offset_mm, is_active,
   min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id)
VALUES
  ('Ifc2x3_Duplex', 'Ground', 'ROOM_A104', 'BATHROOM',
   '', '', '', '',
   0, 1,
   4770.0, 5245.0, -9383.0, -8933.0, 2);

-- GROUND FLOOR (Z = 0 mm) — Unit B ────────────────────────────────────────

-- B102: LIVING  (2× Sofa, 3× Coffee table)
-- Extracted: guid 0BTBFw6f90Nfh9rP1dl_CZ, 5 elements
-- minX=5.368m, maxX=8.3132m, minY=-3.9302m, maxY=-0.612m
INSERT INTO ad_room_boundary
  (building_type, storey, room_name, room_type,
   grid_min_x, grid_max_x, grid_min_y, grid_max_y,
   z_offset_mm, is_active,
   min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id)
VALUES
  ('Ifc2x3_Duplex', 'Ground', 'ROOM_B102', 'LIVING',
   '', '', '', '',
   0, 1,
   5368.0, 8313.0, -3930.0, -612.0, 2);

-- B103: KITCHEN (8× Base cabinet, 4× Upper cabinet, 3× Counter top)
-- Extracted: guid 0BTBFw6f90Nfh9rP1dl_3S, 15 elements
-- minX=2.562m, maxX=7.334m, minY=-7.4304m, maxY=-5.188m
INSERT INTO ad_room_boundary
  (building_type, storey, room_name, room_type,
   grid_min_x, grid_max_x, grid_min_y, grid_max_y,
   z_offset_mm, is_active,
   min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id)
VALUES
  ('Ifc2x3_Duplex', 'Ground', 'ROOM_B103', 'KITCHEN',
   '', '', '', '',
   0, 1,
   2562.0, 7334.0, -7430.0, -5188.0, 2);

-- UPPER FLOOR (Z = 3100 mm) — Unit A ──────────────────────────────────────

-- A202: BEDROOM (King bed + 2× Wardrobe)
-- Extracted: guid 0BTBFw6f90Nfh9rP1dlXrc, 4 elements
-- minX=4.686m, maxX=7.683m, minY=-6.666m, maxY=-0.4045m
INSERT INTO ad_room_boundary
  (building_type, storey, room_name, room_type,
   grid_min_x, grid_max_x, grid_min_y, grid_max_y,
   z_offset_mm, is_active,
   min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id)
VALUES
  ('Ifc2x3_Duplex', 'Upper', 'ROOM_A202', 'BEDROOM',
   '', '', '', '',
   3100, 1,
   4686.0, 7683.0, -6666.0, -405.0, 2);

-- A203: BEDROOM (Queen bed + 2× Wardrobe)
-- Extracted: guid 0BTBFw6f90Nfh9rP1dlXrb, 4 elements
-- minX=4.683m, maxX=8.357m, minY=-14.783m, maxY=-11.134m
INSERT INTO ad_room_boundary
  (building_type, storey, room_name, room_type,
   grid_min_x, grid_max_x, grid_min_y, grid_max_y,
   z_offset_mm, is_active,
   min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id)
VALUES
  ('Ifc2x3_Duplex', 'Upper', 'ROOM_A203', 'BEDROOM',
   '', '', '', '',
   3100, 1,
   4683.0, 8357.0, -14783.0, -11134.0, 2);

-- A204: BATHROOM (1× Vanity cabinet upper floor)
-- Extracted: guid 0BTBFw6f90Nfh9rP1dlXre, 2 elements
-- minX=4.77m, maxX=5.2454m, minY=-9.1454m, maxY=-7.8454m
INSERT INTO ad_room_boundary
  (building_type, storey, room_name, room_type,
   grid_min_x, grid_max_x, grid_min_y, grid_max_y,
   z_offset_mm, is_active,
   min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id)
VALUES
  ('Ifc2x3_Duplex', 'Upper', 'ROOM_A204', 'BATHROOM',
   '', '', '', '',
   3100, 1,
   4770.0, 5245.0, -9145.0, -7845.0, 2);

-- UPPER FLOOR (Z = 3100 mm) — Unit B ──────────────────────────────────────

-- B202: BEDROOM (King bed + 2× Wardrobe)
-- Extracted: guid 0BTBFw6f90Nfh9rP1dl_3A, 4 elements
-- minX=1.317m, maxX=4.114m, minY=-17.383m, maxY=-11.134m
INSERT INTO ad_room_boundary
  (building_type, storey, room_name, room_type,
   grid_min_x, grid_max_x, grid_min_y, grid_max_y,
   z_offset_mm, is_active,
   min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id)
VALUES
  ('Ifc2x3_Duplex', 'Upper', 'ROOM_B202', 'BEDROOM',
   '', '', '', '',
   3100, 1,
   1317.0, 4114.0, -17383.0, -11134.0, 2);

-- B203: BEDROOM (Queen bed + 2× Wardrobe)
-- Extracted: guid 0BTBFw6f90Nfh9rP1dl_39, 4 elements
-- minX=0.443m, maxX=4.117m, minY=-6.666m, maxY=-3.0595m
INSERT INTO ad_room_boundary
  (building_type, storey, room_name, room_type,
   grid_min_x, grid_max_x, grid_min_y, grid_max_y,
   z_offset_mm, is_active,
   min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id)
VALUES
  ('Ifc2x3_Duplex', 'Upper', 'ROOM_B203', 'BEDROOM',
   '', '', '', '',
   3100, 1,
   443.0, 4117.0, -6666.0, -3060.0, 2);

-- B204: BATHROOM (2× Vanity cabinet upper floor)
-- Extracted: guid 0BTBFw6f90Nfh9rP1dl_3C, 2 elements
-- minX=3.5546m, maxX=4.03m, minY=-9.9701m, maxY=-8.6701m
INSERT INTO ad_room_boundary
  (building_type, storey, room_name, room_type,
   grid_min_x, grid_max_x, grid_min_y, grid_max_y,
   z_offset_mm, is_active,
   min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id)
VALUES
  ('Ifc2x3_Duplex', 'Upper', 'ROOM_B204', 'BATHROOM',
   '', '', '', '',
   3100, 1,
   3555.0, 4030.0, -9970.0, -8670.0, 2);

-- ── Step 3: Insert 44 wall-face rows (4 × 11 rooms) ──────────────────────
-- wall_type_id: INTERIOR_PARTITION_92 — standard interior partition for DX
-- is_exterior: 0 — room walls are interior faces of the room space

INSERT INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES
-- ROOM_A102
('Ifc2x3_Duplex','Ground','ROOM_A102','NORTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Ground','ROOM_A102','SOUTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Ground','ROOM_A102','EAST', 'INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Ground','ROOM_A102','WEST', 'INTERIOR_PARTITION_92',0,1,2),
-- ROOM_A103
('Ifc2x3_Duplex','Ground','ROOM_A103','NORTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Ground','ROOM_A103','SOUTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Ground','ROOM_A103','EAST', 'INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Ground','ROOM_A103','WEST', 'INTERIOR_PARTITION_92',0,1,2),
-- ROOM_A104
('Ifc2x3_Duplex','Ground','ROOM_A104','NORTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Ground','ROOM_A104','SOUTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Ground','ROOM_A104','EAST', 'INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Ground','ROOM_A104','WEST', 'INTERIOR_PARTITION_92',0,1,2),
-- ROOM_B102
('Ifc2x3_Duplex','Ground','ROOM_B102','NORTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Ground','ROOM_B102','SOUTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Ground','ROOM_B102','EAST', 'INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Ground','ROOM_B102','WEST', 'INTERIOR_PARTITION_92',0,1,2),
-- ROOM_B103
('Ifc2x3_Duplex','Ground','ROOM_B103','NORTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Ground','ROOM_B103','SOUTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Ground','ROOM_B103','EAST', 'INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Ground','ROOM_B103','WEST', 'INTERIOR_PARTITION_92',0,1,2),
-- ROOM_A202
('Ifc2x3_Duplex','Upper','ROOM_A202','NORTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_A202','SOUTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_A202','EAST', 'INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_A202','WEST', 'INTERIOR_PARTITION_92',0,1,2),
-- ROOM_A203
('Ifc2x3_Duplex','Upper','ROOM_A203','NORTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_A203','SOUTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_A203','EAST', 'INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_A203','WEST', 'INTERIOR_PARTITION_92',0,1,2),
-- ROOM_A204
('Ifc2x3_Duplex','Upper','ROOM_A204','NORTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_A204','SOUTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_A204','EAST', 'INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_A204','WEST', 'INTERIOR_PARTITION_92',0,1,2),
-- ROOM_B202
('Ifc2x3_Duplex','Upper','ROOM_B202','NORTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_B202','SOUTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_B202','EAST', 'INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_B202','WEST', 'INTERIOR_PARTITION_92',0,1,2),
-- ROOM_B203
('Ifc2x3_Duplex','Upper','ROOM_B203','NORTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_B203','SOUTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_B203','EAST', 'INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_B203','WEST', 'INTERIOR_PARTITION_92',0,1,2),
-- ROOM_B204
('Ifc2x3_Duplex','Upper','ROOM_B204','NORTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_B204','SOUTH','INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_B204','EAST', 'INTERIOR_PARTITION_92',0,1,2),
('Ifc2x3_Duplex','Upper','ROOM_B204','WEST', 'INTERIOR_PARTITION_92',0,1,2);

COMMIT;

-- Verify
SELECT storey, room_name, room_type, min_x_mm, max_x_mm, min_y_mm, max_y_mm
FROM ad_room_boundary
WHERE building_type = 'Ifc2x3_Duplex'
ORDER BY storey DESC, room_name;
