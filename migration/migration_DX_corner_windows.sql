-- migration_DX_corner_windows.sql
-- Fix Duplex Level 2 corner windows: out-of-range FRACTION → ABSOLUTE
--
-- Root cause: WALL_ROOM_Level_2_3_NORTH and WALL_ROOM_Level_2_10_SOUTH
--   do not span to the building exterior corners, so the extracted
--   fractions are -0.007 (7mm before wall start) and 1.021 (21mm past
--   wall end). The relational resolver places them outside the wall,
--   visually appearing as "jutted to one side / sunken on the other."
--
-- Fix: switch to ABSOLUTE with oracle world center coords (mm).
--   PositionRule.from() routes ABSOLUTE → DirectCoordinate regardless
--   of host_type, so no code change needed.
--
-- Oracle positions (from ad_element_placement):
--   1126/1127/1128 → cx=826.5, cy=-208.5  (NW cluster, Level 2, NORTH)
--   1131/1132/1133 → cx=7973.5, cy=-17591.5 (SE cluster, Level 2, SOUTH)

-- NW cluster: IfcWindow_1126, 1127, 1128
UPDATE ad_element_rule
SET position_rule    = 'ABSOLUTE',
    position_value   = 826.5,
    position_value_2 = -208.5,
    host_type        = 'BUILDING',
    host_ref         = 'BUILDING'
WHERE building_type = 'Ifc2x3_Duplex'
  AND element_ref IN ('IfcWindow_1126', 'IfcWindow_1127', 'IfcWindow_1128');

-- SE cluster: IfcWindow_1131, 1132, 1133
UPDATE ad_element_rule
SET position_rule    = 'ABSOLUTE',
    position_value   = 7973.5,
    position_value_2 = -17591.5,
    host_type        = 'BUILDING',
    host_ref         = 'BUILDING'
WHERE building_type = 'Ifc2x3_Duplex'
  AND element_ref IN ('IfcWindow_1131', 'IfcWindow_1132', 'IfcWindow_1133');

-- Verify: all 6 rows now have ABSOLUTE rule
SELECT element_ref, position_rule, position_value, position_value_2, host_type
FROM ad_element_rule
WHERE building_type = 'Ifc2x3_Duplex'
  AND element_ref IN (
    'IfcWindow_1126','IfcWindow_1127','IfcWindow_1128',
    'IfcWindow_1131','IfcWindow_1132','IfcWindow_1133'
  )
ORDER BY element_ref;
