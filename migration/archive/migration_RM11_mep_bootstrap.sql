-- Phase RM-11: MEP bootstrap for GENERATIVE buildings
-- Two DB changes needed when TB-LKTN MEP pipeline was wired up.
--
-- Change 1: FP_PIPE_ASSEMBLY/MAIN requires a 'spacing' param for pipe layout.
--   bom_child_id=28 is FP_PIPE_ASSEMBLY / MAIN child entry.
--   BOMRuleAD.loadPlacementParams throws if spacing is absent.
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, is_active)
VALUES (28, 'spacing', '5.0', 1);

-- Change 2: TB_LKTN element count updated from 70 to 100 after MEP elements added.
--   30 new elements: ceiling fans (5) + lights (4) + sprinklers + pipes etc.
--   HVAC supply/return diffusers (IfcAirTerminal) excluded for RESIDENTIAL buildings
--   (split-unit AC model, not ducted). Controlled by building_type in ad_building_registry.
UPDATE ad_building_registry
SET expected_elements = 100
WHERE building_id = 'TB_LKTN';
