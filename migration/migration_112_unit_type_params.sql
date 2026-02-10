-- Phase 112: Add unit_type params to WEST_UNITS and EAST_UNITS BOM children
-- This activates UnitInteriorResolver for condo typical floors
-- Idempotent: INSERT OR IGNORE

-- Child 52 (WEST_UNITS): 2 units, both 2BR_A
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
VALUES (52, 'unit_type_1', '2BR_A'),
       (52, 'unit_type_2', '2BR_A');

-- Child 53 (EAST_UNITS): 2 units, both 2BR_A
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
VALUES (53, 'unit_type_1', '2BR_A'),
       (53, 'unit_type_2', '2BR_A');
