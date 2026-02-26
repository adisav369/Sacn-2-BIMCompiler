-- Phase BOM-2c: UNIT/FLOOR Chain Dispatch
-- Replaces 44 ROOM anchors with 3 UNIT anchors + 4 FLOOR Orderlines.
-- Compiler dispatches: UNIT anchor → walk ad_bom_child → cascade-select via ad_room_slot.
-- ERP analogy: C_OrderLine hierarchy — UNIT = project header, FLOOR = sub-order, SET = line item.

-- ──────────────────────────────────────────────────────────────────────────
-- UNIT Orderlines (1 per building, host_type='BUILDING')
-- position_rule='FRACTION' + host_type='BUILDING' → PositionRule.BomAnchor
-- family_ref = UNIT BOM id (top of the chain)
-- ──────────────────────────────────────────────────────────────────────────
INSERT OR IGNORE INTO ad_element_rule
    (building_type, storey, element_ref, ifc_class, discipline,
     host_type, host_ref, position_rule, position_value, position_value_2,
     height_mm, family_ref, height_extent_mm, width_mm, depth_mm,
     position_value_3, is_active)
VALUES
  ('Ifc4_SampleHouse', 'Ground Floor', 'UNIT_SH',
   'IfcElementAssembly', 'FURN', 'BUILDING', 'Ifc4_SampleHouse',
   'FRACTION', 0.5, 0.5, 0.0, 'UNIT_SH_STD', 0.0, 0.0, 0.0, 0.0, 1),
  ('Ifc2x3_Duplex', 'Level 1', 'UNIT_DX',
   'IfcElementAssembly', 'FURN', 'BUILDING', 'Ifc2x3_Duplex',
   'FRACTION', 0.5, 0.5, 0.0, 'UNIT_DUPLEX_STD', 0.0, 0.0, 0.0, 0.0, 1),
  ('TB_LKTN', 'Ground Floor', 'UNIT_TBLKTN',
   'IfcElementAssembly', 'FURN', 'BUILDING', 'TB_LKTN',
   'FRACTION', 0.5, 0.5, 0.0, 'UNIT_TBLKTN_STD', 0.0, 0.0, 0.0, 0.0, 1);

-- ──────────────────────────────────────────────────────────────────────────
-- FLOOR Orderlines (storey name in .storey column, host_type='UNIT')
-- position_rule='FRACTION' + host_type='UNIT' → PositionRule.BomAnchor (metadata-only)
-- host_ref = UNIT BOM id (parent); family_ref = FLOOR BOM id (this level)
-- storey column → used by loadFloorStoreys() to map floor BOM → storey name
-- ──────────────────────────────────────────────────────────────────────────
INSERT OR IGNORE INTO ad_element_rule
    (building_type, storey, element_ref, ifc_class, discipline,
     host_type, host_ref, position_rule, position_value, position_value_2,
     height_mm, family_ref, height_extent_mm, width_mm, depth_mm,
     position_value_3, is_active)
VALUES
  ('Ifc4_SampleHouse', 'Ground Floor', 'FLOOR_SH_GF',
   'IfcElementAssembly', 'FURN', 'UNIT', 'UNIT_SH_STD',
   'FRACTION', 0.5, 0.5, 0.0, 'FLOOR_SH_GF_STD', 0.0, 0.0, 0.0, 0.0, 1),
  ('Ifc2x3_Duplex', 'Level 1', 'FLOOR_DX_L1',
   'IfcElementAssembly', 'FURN', 'UNIT', 'UNIT_DUPLEX_STD',
   'FRACTION', 0.5, 0.5, 0.0, 'FLOOR_DX_L1_STD', 0.0, 0.0, 0.0, 0.0, 1),
  ('Ifc2x3_Duplex', 'Level 2', 'FLOOR_DX_L2',
   'IfcElementAssembly', 'FURN', 'UNIT', 'UNIT_DUPLEX_STD',
   'FRACTION', 0.5, 0.5, 0.0, 'FLOOR_DX_L2_STD', 0.0, 0.0, 0.0, 0.0, 1),
  ('TB_LKTN', 'Ground Floor', 'FLOOR_TBLKTN_GF',
   'IfcElementAssembly', 'FURN', 'UNIT', 'UNIT_TBLKTN_STD',
   'FRACTION', 0.5, 0.5, 0.0, 'FLOOR_TBLKTN_GF_STD', 0.0, 0.0, 0.0, 0.0, 1);

-- ──────────────────────────────────────────────────────────────────────────
-- Deactivate Phase BOM-1 ROOM anchors (UNIT dispatch supersedes them)
-- Rollback: UPDATE ad_element_rule SET is_active=1
--           WHERE discipline='FURN' AND host_type='ROOM'
-- ──────────────────────────────────────────────────────────────────────────
UPDATE ad_element_rule SET is_active = 0
WHERE discipline = 'FURN' AND host_type = 'ROOM' AND is_active = 1
  AND building_type IN ('Ifc4_SampleHouse', 'Ifc2x3_Duplex', 'TB_LKTN');
