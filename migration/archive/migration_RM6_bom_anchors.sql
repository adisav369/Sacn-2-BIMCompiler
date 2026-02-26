-- Phase RM-6: BOM Anchor Migration
-- MRP BOM Drop → Schedule → Compile flow
-- See: docs/RELATIONAL_PLACEMENT_SPEC.md
--
-- ERP analogy:
--   ad_room_slot   = M_BOM_Line  (room template slots, generic)
--   ad_room_boundary = demand context (rooms in this building)
--   BOM Drop       = INSERT anchor rows (one per assembly per room)
--   ad_element_rule anchors = C_OrderLine (editable schedule)
--   compile time   = MRP Execution (FurnitureBOMResolver → N children)

-- ─────────────────────────────────────────────────────────────────────────────
-- Step 1a: Fix SH room type so LIVING slot rules match
-- ROOM_Ground_Floor_1 was extracted as generic 'ROOM' from Revit;
-- it is the living/dining area of the sample house.
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE ad_room_boundary
   SET room_type = 'LIVING'
 WHERE building_type = 'Ifc4_SampleHouse'
   AND room_name = 'ROOM_Ground_Floor_1';

-- ─────────────────────────────────────────────────────────────────────────────
-- Step 1a-extra: Fix TB-LKTN 'common' room type to COMMON
-- 'common' is an open-plan area combining living + dining + kitchen.
-- COMMON slots dispatch KITCHEN_CABINET_SET(≥0) + DINING_SET(≥8m²) + LIVING_SET(≥15m²).
-- common = 3700×6200mm = 22.94m² → all three fire.
-- (B5 session added COMMON COUNTER slot explicitly for this purpose.)
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE ad_room_boundary
   SET room_type = 'COMMON'
 WHERE building_type = 'TB_LKTN'
   AND room_name = 'common';

-- ─────────────────────────────────────────────────────────────────────────────
-- Step 1b: Deactivate individual furniture leaf rows for SH/DX/TB-LKTN
-- Filter: ifc_class='IfcFurnishingElement' AND family_ref LIKE 'FURN_%'
-- This keeps FIXTURE_* rows (FIXTURE_SINK, FIXTURE_SHOWER, FIXTURE_TOILET,
-- FIXTURE_WATER_HEATER) which are plumbing fixtures, not furniture assemblies.
-- Deactivated counts: SH≈14, DX≈56, TB-LKTN≈10
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE ad_element_rule
   SET is_active = 0
 WHERE building_type IN ('Ifc4_SampleHouse', 'Ifc2x3_Duplex', 'TB_LKTN')
   AND ifc_class = 'IfcFurnishingElement'
   AND family_ref LIKE 'FURN_%';

-- ─────────────────────────────────────────────────────────────────────────────
-- Step 1c: BOM Drop — INSERT anchor rows from slot rules × room boundaries
--
-- element_ref = 'BOM_<assembly_id>_<room_name>' — unique within a building.
-- family_ref  = assembly_id (bom_id) — detected by RelationalResolver as BOM anchor.
-- position_rule='FRACTION', host_type='ROOM' → RoomFraction(roomRef, 0.5, 0.5)
-- fractionX/Y = 0.5 — anchor is room-centred; FurnitureBOMResolver uses room
--               bounds to compute actual child positions, not anchor pos.
-- width/depth/height_extent = 0 — BOM children carry their own dims.
-- storey from room boundary.
-- ifc_class='IfcFurnishingElement' matches the anchor element type.
-- discipline='FURN' marks these as BOM anchors (new discipline value).
-- ─────────────────────────────────────────────────────────────────────────────
INSERT OR IGNORE INTO ad_element_rule
    (element_ref, building_type, storey, ifc_class, discipline,
     host_type, host_ref, position_rule, position_value, position_value_2,
     family_ref, height_mm, height_extent_mm, width_mm, depth_mm,
     orientation, material_name, material_rgba, is_active)
SELECT DISTINCT
    'BOM_' || rs.assembly_id || '_' || rb.room_name,
    rb.building_type,
    rb.storey,
    'IfcFurnishingElement',
    'FURN',
    'ROOM',
    rb.room_name,
    'FRACTION',
    0.5,
    0.5,
    rs.assembly_id,
    0.0,
    0.0,
    0.0,
    0.0,
    NULL,
    NULL,
    NULL,
    1
FROM ad_room_boundary rb
JOIN ad_room_slot rs
  ON rs.room_type = rb.room_type
 AND ((rb.max_x_mm - rb.min_x_mm) * (rb.max_y_mm - rb.min_y_mm) / 1000000.0)
     >= COALESCE(rs.min_area, 0)
 AND rs.assembly_id IS NOT NULL
WHERE rb.building_type IN ('Ifc4_SampleHouse', 'Ifc2x3_Duplex', 'TB_LKTN');

-- ─────────────────────────────────────────────────────────────────────────────
-- Verification queries (run manually to confirm):
--
-- BOM anchor count per building:
--   SELECT building_type, COUNT(*) FROM ad_element_rule
--    WHERE discipline='FURN' AND is_active=1
--    GROUP BY building_type;
--
-- TB-LKTN anchors:
--   SELECT element_ref, family_ref FROM ad_element_rule
--    WHERE building_type='TB_LKTN' AND discipline='FURN' AND is_active=1;
--
-- Deactivated leaf rows:
--   SELECT building_type, COUNT(*) FROM ad_element_rule
--    WHERE ifc_class='IfcFurnishingElement' AND family_ref LIKE 'FURN_%'
--      AND is_active=0
--    GROUP BY building_type;
-- ─────────────────────────────────────────────────────────────────────────────
