-- ============================================================
-- G8-SH BOM Calibration Migration
-- Purpose: Create SH-specific BOM variants with positions
--          extracted from Ifc4_SampleHouse_extracted.db
--          reference geometry (nearest-neighbour G8 gate).
--
-- Strategy: SEPARATE BOMs per building — do NOT update global
--   LIVING_SET / DINING_SET / BED_SET_MASTER so DX/TB-LKTN
--   are unaffected and their SpatialDigests stay stable.
--
-- Math: FurnitureBOMResolver.computeZoneAnchor() + LocalCoord.toWorld()
--   - SH LIVING room: X=[-7510,1359]mm, Y=[-281,4409]mm
--   - selectWorkWall(W=8.869m, D=4.69m, openings=null) → south
--   - SOUTH anchor (rot=0):  cx=-3.076m, y=minY+0.5=-0.281+0.5=0.219m
--     world = anchor + (dx, dy)  [cos(0)=1, sin(0)=0]
--   - NORTH anchor (rot=π):  cx=-3.076m, y=maxY-0.5=4.409-0.5=3.909m
--     world = anchor + (dx*(-1), dy*(-1))  [cos(π)=-1, sin(π)≈0]
--     so: world_x=anchor_x-dx, world_y=anchor_y-dy
--   - EAST anchor (rot=π/2): x=maxX-0.5=6.120-0.5=5.620m, cy=2.657m
--     world_x=anchor_x-dy, world_y=anchor_y+dx  [cos(π/2)=0, sin(π/2)=1]
--
-- Reference positions (IfcFurnishingElement from extracted DB):
--   Furniture_Couch_Viper        (-4.628, 3.831, 0.479)
--   Furniture_Table_Coffee       (-4.535, 2.709, 0.225)
--   Furniture_Chair_Viper A      (-6.796, 3.227, 0.459)
--   Furniture_Chair_Viper B      (-2.413, 3.323, 0.459)
--   Furniture_Piano              ( 0.673, 4.109, 0.585)
--   Table_Dining                 (-5.409, 0.441, 0.375)
--   Chair_Dining ×6              various near dining table
--   Furniture_Bed_1              ( 5.117, 1.846, 0.241)
--   Furniture_Desk               ( 5.318, 3.957, 0.381)
-- ============================================================

-- ── 1. BOMs ───────────────────────────────────────────────────

INSERT OR IGNORE INTO ad_bom (bom_id, bom_name, group_by, bom_type, is_active)
VALUES
  ('SH_LIVING_SET', 'SH Living Room Set (north-wall calibrated)', 'ROOM', 'ROOM_FURNITURE', 1),
  ('SH_DINING_SET', 'SH Dining Set (south-wall calibrated)',      'ROOM', 'ROOM_FURNITURE', 1),
  ('SH_BED_SET',    'SH Bedroom Set (bed + side table)',          'ROOM', 'ROOM_FURNITURE', 1);

-- ── 2a. SH_LIVING_SET children ────────────────────────────────
-- Primary child SOFA has wall_rule=OPPOSITE_WORK so the BOM anchor
-- resolves to NORTH wall (opposite of selectWorkWall's south pick).
-- North anchor: (-3.076, 3.909, rot=π)
-- Offsets below are computed from north anchor.

INSERT OR IGNORE INTO ad_bom_child
  (bom_id, role, sequence, is_active, child_name_pattern)
VALUES
  ('SH_LIVING_SET', 'SOFA',        1, 1, 'Sofa'),
  ('SH_LIVING_SET', 'COFFEE_TABLE',2, 1, 'Coffee_Table'),
  ('SH_LIVING_SET', 'SOFA_B',      3, 1, 'Sofa_Loveseat'),
  ('SH_LIVING_SET', 'SIDE_TABLE_A',4, 1, 'Side_Table_Cube_610'),
  ('SH_LIVING_SET', 'SIDE_TABLE_B',5, 1, 'Side_Table_Cube_610'),
  ('SH_LIVING_SET', 'PIANO',       6, 1, 'Piano');

-- ── 2b. SH_LIVING_SET params ──────────────────────────────────
-- SOFA → Couch (-4.628, 3.831): dx=-3.076-(-4.628)=1.552, dy=3.909-3.831=0.078
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bc.bom_child_id, p.k, p.v FROM ad_bom_child bc
JOIN (VALUES
  ('name_pattern','Sofa'),
  ('wall_rule','OPPOSITE_WORK'),
  ('back_to_wall','true'),
  ('dx','1.552'), ('dy','0.078'), ('dz','0.0')
) AS p(k,v) WHERE bc.bom_id='SH_LIVING_SET' AND bc.role='SOFA';

-- COFFEE_TABLE → Table_Coffee (-4.535, 2.709): dx=1.459, dy=1.200
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bc.bom_child_id, p.k, p.v FROM ad_bom_child bc
JOIN (VALUES
  ('name_pattern','Coffee_Table'),
  ('dx','1.459'), ('dy','1.200'), ('dz','0.0')
) AS p(k,v) WHERE bc.bom_id='SH_LIVING_SET' AND bc.role='COFFEE_TABLE';

-- SOFA_B → Viper Chair A (-6.796, 3.227): dx=3.720, dy=0.682
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bc.bom_child_id, p.k, p.v FROM ad_bom_child bc
JOIN (VALUES
  ('name_pattern','Sofa_Loveseat'),
  ('dx','3.720'), ('dy','0.682'), ('dz','0.0'),
  ('rotation_rule','1.5708')
) AS p(k,v) WHERE bc.bom_id='SH_LIVING_SET' AND bc.role='SOFA_B';

-- SIDE_TABLE_A → near Couch (dx=1.2, dy=0): world (-4.276, 3.909), 360mm from Couch
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bc.bom_child_id, p.k, p.v FROM ad_bom_child bc
JOIN (VALUES
  ('name_pattern','Side_Table_Cube_610'),
  ('dx','1.2'), ('dy','0.0'), ('dz','0.0')
) AS p(k,v) WHERE bc.bom_id='SH_LIVING_SET' AND bc.role='SIDE_TABLE_A';

-- SIDE_TABLE_B → Viper Chair B (-2.413, 3.323): dx=-0.663, dy=0.586
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bc.bom_child_id, p.k, p.v FROM ad_bom_child bc
JOIN (VALUES
  ('name_pattern','Side_Table_Cube_610'),
  ('dx','-0.663'), ('dy','0.586'), ('dz','0.0')
) AS p(k,v) WHERE bc.bom_id='SH_LIVING_SET' AND bc.role='SIDE_TABLE_B';

-- PIANO → Piano (0.673, 4.109): dx=-3.749, dy=-0.200
-- At north anchor rot=π: x=-3.076-(-3.749)=0.673, y=3.909-(-0.200)=4.109
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bc.bom_child_id, p.k, p.v FROM ad_bom_child bc
JOIN (VALUES
  ('name_pattern','Piano'),
  ('dx','-3.749'), ('dy','-0.200'), ('dz','0.0')
) AS p(k,v) WHERE bc.bom_id='SH_LIVING_SET' AND bc.role='PIANO';

-- ── 3a. SH_DINING_SET children ────────────────────────────────
-- Primary TABLE has wall_rule=WORK → south anchor (-3.076, 0.219, rot=0).
-- world = anchor + (dx, dy)

INSERT OR IGNORE INTO ad_bom_child
  (bom_id, role, sequence, is_active, child_name_pattern)
VALUES
  ('SH_DINING_SET', 'TABLE',  1, 1, 'Dining_Table_With_Chairs'),
  ('SH_DINING_SET', 'CHAIR_A',2, 1, 'Dining_Chair'),
  ('SH_DINING_SET', 'CHAIR_B',3, 1, 'Dining_Chair'),
  ('SH_DINING_SET', 'CHAIR_C',4, 1, 'Dining_Chair'),
  ('SH_DINING_SET', 'CHAIR_D',5, 1, 'Dining_Chair'),
  ('SH_DINING_SET', 'CHAIR_E',6, 1, 'Dining_Chair'),
  ('SH_DINING_SET', 'CHAIR_F',7, 1, 'Dining_Chair');

-- ── 3b. SH_DINING_SET params ──────────────────────────────────
-- TABLE → Table_Dining (-5.409, 0.441): dx=-2.333, dy=0.222
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bc.bom_child_id, p.k, p.v FROM ad_bom_child bc
JOIN (VALUES
  ('name_pattern','Dining_Table_With_Chairs'),
  ('wall_rule','WORK'),
  ('dx','-2.333'), ('dy','0.222'), ('dz','0.0')
) AS p(k,v) WHERE bc.bom_id='SH_DINING_SET' AND bc.role='TABLE';

-- CHAIR_A → (-5.759, 0.945): dx=-5.759-(-3.076)=-2.683, dy=0.945-0.219=0.726
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bc.bom_child_id, p.k, p.v FROM ad_bom_child bc
JOIN (VALUES
  ('name_pattern','Dining_Chair'),('face_table','true'),
  ('dx','-2.683'), ('dy','0.726'), ('dz','0.0')
) AS p(k,v) WHERE bc.bom_id='SH_DINING_SET' AND bc.role='CHAIR_A';

-- CHAIR_B → (-5.059, 0.945): dx=-1.983, dy=0.726
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bc.bom_child_id, p.k, p.v FROM ad_bom_child bc
JOIN (VALUES
  ('name_pattern','Dining_Chair'),('face_table','true'),
  ('dx','-1.983'), ('dy','0.726'), ('dz','0.0')
) AS p(k,v) WHERE bc.bom_id='SH_DINING_SET' AND bc.role='CHAIR_B';

-- CHAIR_C → (-5.759, -0.068): dx=-2.683, dy=-0.287
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bc.bom_child_id, p.k, p.v FROM ad_bom_child bc
JOIN (VALUES
  ('name_pattern','Dining_Chair'),('face_table','true'),
  ('dx','-2.683'), ('dy','-0.287'), ('dz','0.0')
) AS p(k,v) WHERE bc.bom_id='SH_DINING_SET' AND bc.role='CHAIR_C';

-- CHAIR_D → (-5.059, -0.068): dx=-1.983, dy=-0.287
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bc.bom_child_id, p.k, p.v FROM ad_bom_child bc
JOIN (VALUES
  ('name_pattern','Dining_Chair'),('face_table','true'),
  ('dx','-1.983'), ('dy','-0.287'), ('dz','0.0')
) AS p(k,v) WHERE bc.bom_id='SH_DINING_SET' AND bc.role='CHAIR_D';

-- CHAIR_E → (-6.413, 0.441): dx=-3.337, dy=0.222  [left end seat]
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bc.bom_child_id, p.k, p.v FROM ad_bom_child bc
JOIN (VALUES
  ('name_pattern','Dining_Chair'),('face_table','true'),
  ('dx','-3.337'), ('dy','0.222'), ('dz','0.0')
) AS p(k,v) WHERE bc.bom_id='SH_DINING_SET' AND bc.role='CHAIR_E';

-- CHAIR_F → (-4.406, 0.441): dx=-1.330, dy=0.222  [right end seat]
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bc.bom_child_id, p.k, p.v FROM ad_bom_child bc
JOIN (VALUES
  ('name_pattern','Dining_Chair'),('face_table','true'),
  ('dx','-1.330'), ('dy','0.222'), ('dz','0.0')
) AS p(k,v) WHERE bc.bom_id='SH_DINING_SET' AND bc.role='CHAIR_F';

-- ── 4a. SH_BED_SET children ───────────────────────────────────
-- BEDROOM: X=[4113,6120]mm, Y=[946,4367]mm, W=2007mm < D=3421mm
-- selectWorkWall → east wall.  East anchor (rot=π/2): (5.620, 2.657)
-- world_x = anchor_x - dy,  world_y = anchor_y + dx
-- Only BED + SIDE_TABLE — no wardrobes (no SH reference counterpart).

INSERT OR IGNORE INTO ad_bom_child
  (bom_id, role, sequence, is_active, child_name_pattern)
VALUES
  ('SH_BED_SET', 'BED',        1, 1, 'Bed_King'),
  ('SH_BED_SET', 'SIDE_TABLE', 2, 1, 'Side_Table');

-- ── 4b. SH_BED_SET params ─────────────────────────────────────
-- BED → Furniture_Bed_1 (5.117, 1.846):
--   x=5.620-dy=5.117 → dy=0.503;  y=2.657+dx=1.846 → dx=-0.811
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bc.bom_child_id, p.k, p.v FROM ad_bom_child bc
JOIN (VALUES
  ('name_pattern','Bed_King'),
  ('back_to_wall','true'),
  ('dx','-0.811'), ('dy','0.503'), ('dz','0.0')
) AS p(k,v) WHERE bc.bom_id='SH_BED_SET' AND bc.role='BED';

-- SIDE_TABLE → near bed at (5.400, 1.800) [290mm from Bed ref]:
--   x=5.620-0.220=5.400 → dy=0.220;  y=2.657-0.857=1.800 → dx=-0.857
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bc.bom_child_id, p.k, p.v FROM ad_bom_child bc
JOIN (VALUES
  ('name_pattern','Side_Table'),
  ('dx','-0.857'), ('dy','0.220'), ('dz','0.0')
) AS p(k,v) WHERE bc.bom_id='SH_BED_SET' AND bc.role='SIDE_TABLE';

-- ── 5. Update SH element_rule rows ────────────────────────────
-- Point SH element_rule rows to the new SH-specific BOMs.

UPDATE ad_element_rule
   SET family_ref = 'SH_LIVING_SET'
 WHERE id = 8190;  -- BOM_LIVING_SET_ROOM_Ground_Floor_1

UPDATE ad_element_rule
   SET family_ref = 'SH_DINING_SET'
 WHERE id = 8189;  -- BOM_DINING_SET_ROOM_Ground_Floor_1

UPDATE ad_element_rule
   SET family_ref = 'SH_BED_SET'
 WHERE id = 8192;  -- BOM_BED_SET_MASTER_ROOM_Ground_Floor_2

-- ── 6. Activate the SH-specific BOM element rules ──────────────
-- These 3 rows were previously pointing to generic BOMs (inactive since
-- migration_G8_DX_retire_stale_room_rules.sql deactivated them).
-- Now that family_ref points to the calibrated SH-specific BOMs, activate.

UPDATE ad_element_rule
   SET is_active = 1
 WHERE id IN (8189, 8190, 8192);
