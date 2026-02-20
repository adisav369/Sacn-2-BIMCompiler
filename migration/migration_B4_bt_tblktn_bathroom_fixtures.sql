-- ============================================================
-- MIGRATION B4: BT Layer — Building Type element rules
-- iDempiere analogy: C_OrderLine (per-building order lines)
-- ─────────────────────────────────────────────────────────
-- Adds water heater to TB-LKTN bilik_mandi (bathroom).
-- Explicit C_OrderLine row — same pattern as IfcFurnishingElement_2
-- (sink) and IfcFurnishingElement_4 (shower) already in DB.
-- family_ref → FIXTURE_WATER_HEATER (M_Product).
-- ============================================================

-- Water heater: wall-mounted on north wall of bilik_mandi
-- bilik_mandi bounds: A4-B5 (1.3m wide × 1.5m deep)
-- NS orientation: fractionY=0.85 → back against north wall, faces south
-- fractionX=0.7 → east side of bathroom (above sink zone)
INSERT INTO ad_element_rule
  (building_type, storey,         element_ref,             ifc_class,
   discipline,    host_type,      host_ref,
   position_rule, position_value, position_value_2, height_mm,
   family_ref,    width_mm,       height_extent_mm, depth_mm,
   orientation,   is_active)
VALUES
  ('TB_LKTN',    'Ground Floor', 'IfcFurnishingElement_14', 'IfcFurnishingElement',
   'PLB',        'ROOM',         'bilik_mandi',
   'FRACTION',    0.7,            0.85,             1500.0,
   'FIXTURE_WATER_HEATER', 550.0, 550.0,            250.0,
   'NS',          1);

-- ────────────────────────────────────────────────────────────
-- TOILET ROTATION FIX (same migration boundary)
-- ─────────────────────────────────────────────────────────
-- IfcFurnishingElement_5 (FIXTURE_TOILET in tandas):
-- Previous: orientation=NS → back against north wall → faces south (WRONG)
-- tandas opens_to common on the EAST side → toilet should face EAST.
-- Fix: orientation=EW, fractionX=0.27 → back against west wall, faces east.
-- fractionX=0.27: 0.27 × 1.3m room = 0.35m from west = half of toilet depth (0.7/2).
-- fractionY=0.5: centered front-to-back in the room.
-- Per three-table authority: orientation belongs in ad_element_rule. ✓
-- Per conn_points: FIXTURE_TOILET has face=BACK → resolveOrientation applies. ✓
UPDATE ad_element_rule
   SET orientation     = 'EW',
       position_value  = 0.27,
       position_value_2 = 0.5
 WHERE building_type = 'TB_LKTN'
   AND element_ref   = 'IfcFurnishingElement_5';

-- Update expected_elements: 100 → 101 (one new water heater element)
UPDATE ad_building_registry
   SET expected_elements = 101
 WHERE building_id = 'TB_LKTN';
