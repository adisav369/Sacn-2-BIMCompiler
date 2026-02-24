-- =============================================================================
-- Phase 4c Step 2 — SOFA_AREA sub-BOM: Coffee_Table + Side_Tables
--
-- Problem (G8-SH @Disabled):
--   Coffee_Table and Side_Table dx/dy were calibrated relative to the FLOAT
--   zone anchor (computeZoneAnchor north ≈ -3.076, 3.909).
--   After Phase 4c Step 1, Sofa moved to GPD placement (NORTH_WALL) at a
--   completely different absolute position (-5.139, 4.008 vs IFC -4.628, 3.831).
--   Coffee_Table stayed at the old zone anchor → anchor drift of ~0.5m.
--
-- Fix — SOFA_AREA sub-BOM:
--   Sofa.child_bom_id → 'SOFA_AREA'.
--   resolveWithGPD() expands SOFA_AREA at Sofa's placed centroid.
--   Coffee_Table + Side_Tables become relative to Sofa regardless of where
--   GPD lands Sofa. This is the recursive-descent (BOMCascadeResolver) pattern.
--
-- W-SOFA-AREA witness (IFC calibrated, rotation=π):
--   Zone anchor = (-3.076, 3.909)
--   Sofa IFC  = (-4.628, 3.831)   old FLOAT dx=1.552, dy=0.078
--   Coffee_Table IFC = (-4.535, 2.709)
--   Side_Table_A IFC = (-4.276, 3.909)   old FLOAT dx=1.2, dy=0.0
--   Side_Table_B IFC = (-2.413, 3.323)   old FLOAT dx=-0.663, dy=0.586
--
-- SOFA_AREA local offsets (dx_local = sofa.x − item.x, dy_local = sofa.y − item.y):
--   Coffee_Table : dx = -0.093, dy = +1.122
--   Side_Table_A : dx = -0.352, dy = -0.078
--   Side_Table_B : dx = -2.215, dy = +0.508
-- =============================================================================

-- ── 1. SOFA_AREA BOM record ────────────────────────────────────────────────

INSERT OR IGNORE INTO ad_bom
    (bom_id, bom_name, description, target_ifc_class, group_by,
     is_active, bom_level, bom_type, bom_category)
VALUES
    ('SOFA_AREA', 'Sofa Area Sub-BOM',
     'Coffee table and side tables relative to sofa centroid (SOFA anchor child)',
     'IfcElementAssembly', 'PROXIMITY',
     1, 'SET', 'SET', 'SH');

-- ── 2. SOFA_AREA children (IFC-calibrated, rotation=π applied) ───────────

INSERT OR IGNORE INTO ad_bom_child
    (bom_id, child_ifc_class, child_name_pattern, role,
     sequence, dx, dy, dz, rotation_rule,
     locator_ref, layout_strategy, is_active, fit_priority, min_space_mm)
VALUES
    ('SOFA_AREA', 'IfcFurniture', 'Coffee_Table', 'COFFEE_TABLE',
     10, -0.093, 1.122, 0.0, '0',
     'FLOAT', 'LINEAR', 1, 20, 0),

    ('SOFA_AREA', 'IfcFurniture', 'Side_Table_Cube_610', 'SIDE_TABLE_A',
     20, -0.352, -0.078, 0.0, '0',
     'FLOAT', 'LINEAR', 1, 20, 0),

    ('SOFA_AREA', 'IfcFurniture', 'Side_Table_Cube_610', 'SIDE_TABLE_B',
     30, -2.215, 0.508, 0.0, '0',
     'FLOAT', 'LINEAR', 1, 20, 0);

-- ── 3. Wire Sofa → SOFA_AREA sub-BOM ──────────────────────────────────────

UPDATE ad_bom_child
SET child_bom_id = 'SOFA_AREA'
WHERE bom_id = 'SH_LIVING_SET'
  AND child_name_pattern = 'Sofa'
  AND role = 'SOFA';

-- ── 4. Deactivate old SH_LIVING_SET FLOAT entries (replaced by SOFA_AREA) ──

UPDATE ad_bom_child
SET is_active = 0
WHERE bom_id = 'SH_LIVING_SET'
  AND child_name_pattern IN ('Coffee_Table', 'Side_Table_Cube_610')
  AND locator_ref = 'FLOAT';

-- ── 5. Verify ──────────────────────────────────────────────────────────────

SELECT '=== SH_LIVING_SET (active) ===' AS label;
SELECT sequence, child_name_pattern, locator_ref, child_bom_id, is_active
FROM ad_bom_child
WHERE bom_id = 'SH_LIVING_SET'
ORDER BY sequence;

SELECT '=== SOFA_AREA children ===' AS label;
SELECT sequence, child_name_pattern, role, dx, dy, locator_ref
FROM ad_bom_child
WHERE bom_id = 'SOFA_AREA'
ORDER BY sequence;
