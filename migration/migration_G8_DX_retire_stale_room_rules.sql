-- ============================================================
-- G8 Calibration — Retire stale Ifc2x3_Duplex element rules
-- that reference rooms/walls deleted by migration_G8_DX_room_boundary_calibration.sql
--
-- Root cause: migration_G8_DX_room_boundary_calibration.sql replaced 44 grid
--   rooms (ROOM_Level_1_*, ROOM_Level_2_*) with 11 IFC-extracted rooms
--   (ROOM_A102, ROOM_B102 ... ROOM_B204). Legacy ARC/MEP/STR rules that
--   reference the old names are now dead. The walls they referenced
--   (WALL_ROOM_Level_1_*) also no longer exist.
--
-- DX is compiled via the MULTI-UNIT metadata path.  Doors, windows, MEP
-- flow elements and structural beams are placed directly from the extracted
-- reference DB, not via RelationalResolver.  These 715 rules are therefore
-- dead code and safe to retire with is_active=0.
--
-- FURN rules (BOM_DINING_SET_ROOM_Level_1_* etc.) were already set to
-- is_active=0 by migration_G8_DX_room_boundary_calibration.sql.
-- ============================================================

-- ARC rules whose host_ref is an old room name (34 rows)
-- Includes IfcFurnishingElement_*, IfcWall_* that reference ROOM_Level_1/2
UPDATE ad_element_rule
SET is_active = 0
WHERE building_type = 'Ifc2x3_Duplex'
  AND discipline   = 'ARC'
  AND host_type    = 'ROOM'
  AND host_ref LIKE 'ROOM_Level_%';

-- ARC rules whose host_ref is an old wall derived from deleted rooms (16 rows)
-- Includes IfcDoor_*, IfcWindow_* that reference WALL_ROOM_Level_1/2
UPDATE ad_element_rule
SET is_active = 0
WHERE building_type = 'Ifc2x3_Duplex'
  AND discipline   = 'ARC'
  AND host_type    = 'WALL'
  AND host_ref LIKE 'WALL_ROOM_Level_%';

-- MEP rules whose host_ref is an old room name (661 rows)
-- IfcFlowFitting_*, IfcFlowSegment_*, IfcFlowTerminal_* placed by MULTI-UNIT path
UPDATE ad_element_rule
SET is_active = 0
WHERE building_type = 'Ifc2x3_Duplex'
  AND discipline   = 'MEP'
  AND host_type    = 'ROOM'
  AND host_ref LIKE 'ROOM_Level_%';

-- STR rules whose host_ref is an old room name (4 rows)
-- IfcBeam_56, IfcBeam_57, IfcBeam_61, IfcBeam_62
UPDATE ad_element_rule
SET is_active = 0
WHERE building_type = 'Ifc2x3_Duplex'
  AND discipline   = 'STR'
  AND host_type    = 'ROOM'
  AND host_ref LIKE 'ROOM_Level_%';

-- Verify: no active DX rules should now reference ROOM_Level_* or WALL_ROOM_Level_*
SELECT discipline, host_type, COUNT(*) as remaining_active
FROM ad_element_rule
WHERE building_type = 'Ifc2x3_Duplex'
  AND is_active = 1
  AND (host_ref LIKE 'ROOM_Level_%' OR host_ref LIKE 'WALL_ROOM_Level_%')
GROUP BY discipline, host_type;
-- Expected: zero rows
