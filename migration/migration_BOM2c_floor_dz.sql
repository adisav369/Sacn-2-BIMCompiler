-- Phase BOM-2c: Declare storey Z elevation on Duplex Level 2 FLOOR Orderline.
-- [EXTRACTED: Ifc2x3_Duplex Rosetta Stone — storey elevation fact]
-- position_value_3 = dZ in mm: how far above Level 1 floor elevation this storey sits.
-- Duplex Level 2 = 3000mm above Level 1 (3m standard residential storey height).
UPDATE ad_element_rule
SET position_value_3 = 3000.0
WHERE family_ref = 'FLOOR_DX_L2_STD' AND is_active = 1;
