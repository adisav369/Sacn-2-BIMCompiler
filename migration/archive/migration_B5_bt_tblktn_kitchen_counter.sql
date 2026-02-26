-- ============================================================
-- MIGRATION B5: BT Layer — Building Type element rules
-- iDempiere analogy: C_OrderLine (per-building order line)
-- ─────────────────────────────────────────────────────────
-- Adds kitchen counter to TB-LKTN 'common' open-plan zone.
-- TB-LKTN 'common' room has type=OPEN_PLAN (from DSL keyword).
-- No ad_room_slot dispatch for OPEN_PLAN → explicit C_OrderLine
-- required, same pattern as IfcFurnishingElement_1-13.
-- family_ref → FURN_KITCHEN_COUNTER (M_Product, 3.0m × 0.625m).
-- ============================================================

-- Kitchen counter: along north wall of open-plan common zone
-- common bounds: C2-D5 (3.7m wide × 6.2m deep)
-- NS orientation: fractionY=0.88 → back against north wall, faces south (into kitchen zone)
-- fractionX=0.5 → center of room width (1.85m from west)
-- FURN_KITCHEN_COUNTER: 3.0m wide × 0.625m deep × 0.86m high
INSERT INTO ad_element_rule
  (building_type, storey,         element_ref,             ifc_class,
   discipline,    host_type,      host_ref,
   position_rule, position_value, position_value_2, height_mm,
   family_ref,           width_mm, height_extent_mm, depth_mm,
   orientation,   is_active)
VALUES
  ('TB_LKTN',    'Ground Floor', 'IfcFurnishingElement_15', 'IfcFurnishingElement',
   'ARC',        'ROOM',         'common',
   'FRACTION',    0.5,            0.88,             0.0,
   'FURN_KITCHEN_COUNTER', 3000.0, 860.0,           625.0,
   'NS',          1);

-- Also add M_BOM_Line note: ad_room_slot COMMON type for future buildings
-- (does not affect TB_LKTN which uses OPEN_PLAN room type)
INSERT OR IGNORE INTO ad_room_slot
  (room_type, slot_name, assembly_id, slot_face, slot_priority, is_required)
VALUES
  ('COMMON', 'COUNTER', 'KITCHEN_CABINET_SET', 'BACK', 15, 0);

-- Update expected_elements: 101 → 102 (one new kitchen counter)
UPDATE ad_building_registry
   SET expected_elements = 102
 WHERE building_id = 'TB_LKTN';
