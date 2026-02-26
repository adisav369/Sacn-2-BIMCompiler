-- Migration RM6: Fix IfcWindow material_name for transparency
--
-- Problem: IfcWindow elements have material_name = 'Window Frame' or NULL.
-- 'Window Frame' maps to surface_styles.transparency = 0.0 (opaque frame).
-- Windows should use 'Glass' which maps to transparency = 0.9 in surface_styles.
--
-- The Bonsai Federation addon joins: elements_meta.material_name → surface_styles.style_name
-- Without this fix, windows appear opaque in the viewer.
--
-- Scope: Both ad_element_placement (flat) and ad_element_rule (relational)
-- TB-LKTN ad_element_rule already has Window_W1/W2/W3_Small (correct, skipped).

-- Fix ad_element_placement: all IfcWindow rows
UPDATE ad_element_placement
SET material_name = 'Glass'
WHERE ifc_class = 'IfcWindow'
  AND (material_name IS NULL OR material_name = 'Window Frame');

-- Fix ad_element_rule: SH + DX IfcWindow rows (TB-LKTN already correct)
UPDATE ad_element_rule
SET material_name = 'Glass'
WHERE ifc_class = 'IfcWindow'
  AND (material_name IS NULL OR material_name = 'Window Frame');

-- Verify both tables
SELECT 'ad_element_placement' as tbl, building_type, COUNT(*) as cnt, material_name
FROM ad_element_placement
WHERE ifc_class = 'IfcWindow'
GROUP BY building_type, material_name;

SELECT 'ad_element_rule' as tbl, building_type, COUNT(*) as cnt, material_name
FROM ad_element_rule
WHERE ifc_class = 'IfcWindow'
GROUP BY building_type, material_name;
