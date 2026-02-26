-- Phase 122C: Center-placement for dining tables, BOM tolerance fix
-- Target: SampleHouse chairs 3/6 → 6/6 (+3 X-ray matches)

-- 1. DINING_SET TABLE should be CENTER-placed (not wall-anchored)
-- Dining tables need chairs on all sides, so anchor at room center.
INSERT OR REPLACE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bom_child_id, 'wall_rule', 'CENTER'
FROM ad_bom_child WHERE bom_id = 'DINING_SET' AND role = 'TABLE';
