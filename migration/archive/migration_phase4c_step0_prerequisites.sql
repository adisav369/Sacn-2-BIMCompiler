-- =============================================================================
-- Phase 4c Step 0 — PhantomLayout prerequisites
--
-- Step 0a: Populate height_extent_mm for all FLOOR Orderlines.
--   Without this, ceiling Z = unknown. placeChild() Z anchor cannot be
--   computed for ceiling-referenced elements (sprinklers, pendant lights).
--   Standard clear heights: SH 2700mm, DX 3000mm, TB-LKTN 3000mm.
--
-- Step 0b: layout_strategy DEFAULT conflict.
--   SQLite does not support ALTER COLUMN SET DEFAULT.
--   All existing BOM children are locator_ref='FLOAT' — dispatch rule
--   in FurnitureBOMResolver handles this: if locator_ref='FLOAT', use
--   explicit dx/dy path and IGNORE layout_strategy.
--   No DEFAULT change needed. Document dispatch rule in code.
-- =============================================================================

-- ── Step 0a: height_extent_mm per building ────────────────────────────────────

UPDATE ad_element_rule
SET height_extent_mm = 2700
WHERE element_ref LIKE 'FLOOR_SH%'
  AND host_type = 'UNIT'
  AND discipline = 'FURN';

UPDATE ad_element_rule
SET height_extent_mm = 3000
WHERE element_ref LIKE 'FLOOR_DX%'
  AND host_type = 'UNIT'
  AND discipline = 'FURN';

UPDATE ad_element_rule
SET height_extent_mm = 3000
WHERE element_ref LIKE 'FLOOR_TBLKTN%'
  AND host_type = 'UNIT'
  AND discipline = 'FURN';

-- Verify
SELECT element_ref, building_type, position_value_3 AS floor_z_mm, height_extent_mm
FROM ad_element_rule
WHERE host_type='UNIT' AND discipline='FURN' AND is_active=1
  AND element_ref LIKE 'FLOOR_%'
ORDER BY building_type, element_ref;
