-- ============================================================
-- MIGRATION B1b: BT Layer — Building Type element rules
-- iDempiere analogy: C_OrderLine (per-building order lines)
-- ─────────────────────────────────────────────────────────
-- ad_element_rule = C_OrderLine: one product placed at one
-- location within one building. family_ref → M_Product
-- (ad_product_dim.product_id). width_mm is the opening size
-- resolved against the host wall at compile time.
-- ============================================================

-- Front door: double-leaf entry (IfcDoor_1, south exterior wall)
-- family_ref → DOOR_D1_DOUBLE (M_Product, 1125mm double leaf)
UPDATE ad_element_rule
   SET family_ref = 'DOOR_D1_DOUBLE',
       width_mm   = 1125.0
 WHERE building_type = 'TB_LKTN'
   AND element_ref   = 'IfcDoor_1';

-- Internal bedroom doors (IfcDoor_2, 3, 4) — DOOR_D2, 800mm
-- Malaysian standard internal door: MS 1184, 800mm clear opening
UPDATE ad_element_rule
   SET family_ref = 'DOOR_D2',
       width_mm   = 800.0
 WHERE building_type = 'TB_LKTN'
   AND element_ref IN ('IfcDoor_2','IfcDoor_3','IfcDoor_4');

-- Bathroom doors (IfcDoor_5, 6) — DOOR_D3, 700mm
-- Smaller door for wet rooms per MS 1184
UPDATE ad_element_rule
   SET family_ref = 'DOOR_D3',
       width_mm   = 700.0
 WHERE building_type = 'TB_LKTN'
   AND element_ref IN ('IfcDoor_5','IfcDoor_6');
