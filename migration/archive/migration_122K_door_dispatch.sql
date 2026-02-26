-- migration_122K_door_dispatch.sql
-- Door dispatch rules extracted from Terminal reference Analysis 2
-- Reference: 135 doors, Compiled: 89, Gap: 46
-- Idempotent: INSERT OR IGNORE

-- ============================================================
-- 1. LANDING — 3 rooms, ZERO dispatch rules. Add fire door + stair access.
-- ============================================================
INSERT OR IGNORE INTO ad_space_type_opening
  (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall, is_fire_rated, priority, profile)
  VALUES ('LANDING', 'ENTRY', 'INST_FIRE_DOOR_900', 'FIXED', 1, 'interior', 1, 100, 'Malaysian_Institutional');

INSERT OR IGNORE INTO ad_space_type_opening
  (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall, is_fire_rated, priority, profile)
  VALUES ('LANDING', 'FIRE_EXIT', 'INST_FIRE_DOOR_750', 'FIXED', 1, 'interior', 1, 100, 'Malaysian_Institutional');

-- Generic landing rule (non-profile)
INSERT OR IGNORE INTO ad_space_type_opening
  (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall, is_fire_rated, priority)
  VALUES ('LANDING', 'ENTRY', 'FIRE_DOOR', 'FIXED', 1, 'interior', 1, 100);

-- ============================================================
-- 2. LOBBY exterior entry: FIXED 1 → PER_EXTERIOR_WALL
--    Lobbies with 2-3 exterior walls get 2-3 entrance doors
--    Reference pattern: main_lobby has 3 doors on south + 1 on west
-- ============================================================
UPDATE ad_space_type_opening
  SET qty_rule = 'PER_EXTERIOR_WALL', qty_base = 1
  WHERE space_type_id = 'LOBBY'
    AND family_id = 'MY_GLASS_ENTRANCE_1850'
    AND profile = 'Malaysian_Institutional';

-- ============================================================
-- 3. LOBBY: add service/utility door (1500x2100 double leaf)
--    Reference shows 13 fire-rated double doors (FD4 1500x2100)
-- ============================================================
INSERT OR IGNORE INTO ad_space_type_opening
  (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall, is_fire_rated, priority, profile)
  VALUES ('LOBBY', 'SERVICE', 'INST_FIRE_DOOR_DOUBLE', 'FIXED', 1, 'interior', 1, 100, 'Malaysian_Institutional');

-- ============================================================
-- 4. CORRIDOR: Add entry door (currently qty_base=0)
--    Corridors in institutional buildings have fire zone access doors
-- ============================================================
INSERT OR IGNORE INTO ad_space_type_opening
  (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall, is_fire_rated, priority, profile)
  VALUES ('CORRIDOR', 'ENTRY', 'INST_FIRE_DOOR_900', 'FIXED', 1, 'interior', 1, 90, 'Malaysian_Institutional');

-- ============================================================
-- 5. OPEN_PLAN: add service double door
--    Large open plan spaces (check-in, gates) need multiple access points
-- ============================================================
INSERT OR IGNORE INTO ad_space_type_opening
  (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall, is_fire_rated, priority, profile)
  VALUES ('OPEN_PLAN', 'SERVICE', 'INST_FIRE_DOOR_DOUBLE', 'FIXED', 1, 'interior', 1, 100, 'Malaysian_Institutional');

-- ============================================================
-- 6. TOILET_BLOCK: add ventilation window for institutional
--    Reference shows toilet blocks with small windows
-- ============================================================
INSERT OR IGNORE INTO ad_space_type_opening
  (space_type_id, opening_role, family_id, qty_rule, qty_base, sill_height_mm, placement_wall, priority, profile)
  VALUES ('TOILET_BLOCK', 'VENTILATION', 'SMALL_WINDOW', 'PER_EXTERIOR_WALL', 1, 1500, 'exterior', 100, 'Malaysian_Institutional');

-- ============================================================
-- 7. DINING: add double door for large dining halls
--    Reference shows 1800x2100 double doors in dining areas
-- ============================================================
INSERT OR IGNORE INTO ad_space_type_opening
  (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall, is_fire_rated, priority, profile)
  VALUES ('DINING', 'SERVICE', 'INST_FIRE_DOOR_WIDE', 'FIXED', 1, 'interior', 1, 100, 'Malaysian_Institutional');
