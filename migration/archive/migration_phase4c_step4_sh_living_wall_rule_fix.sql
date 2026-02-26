-- =============================================================================
-- Phase 4c Step 4 — SH_LIVING_SET: Fix Piano wallRule + remove Sofa OPPOSITE_WORK
--
-- Problem (G8-SH 2 failures after Step 3 FLOAT revert):
--   Piano/Loveseat placed at south-wall positions (~y=0.019/0.901).
--   IFC truth: all three items are on the north wall.
--
-- Root cause:
--   In resolveFloatChildren hasOffsets=true path, the PRIMARY child (Piano, seq=10)
--   determines the zone anchor via primary.wallRule().
--   Piano.wallRule = null → resolveWall(null, workWall) = workWall.
--   selectWorkWall() returns 'south' for SH living room (north wall has openings/windows).
--   → anchor = south → Piano at wrong y≈0.019 instead of 4.109.
--   Sofa.wall_rule='OPPOSITE_WORK' → expandBOMNode re-anchors to north (opposite of
--   south anchor) → ACCIDENTALLY correct. Loveseat.wallRule=null → south → WRONG.
--
-- Fix:
--   a. Piano: add wall_rule='NORTH_WALL' param → anchor always = north wall
--      (resolveWall 'NORTH_WALL' case returns 'north' regardless of workWall).
--   b. Sofa: remove wall_rule='OPPOSITE_WORK' — now that primary anchor IS north,
--      OPPOSITE_WORK on Sofa would double-flip back to south (wrong).
--
-- Expected result (anchor = north wall center = (-3.076, 3.909, π)):
--   Piano    dx=-3.749, dy=-0.2 → world(0.673,  4.109) ≈ IFC(0.673, 4.109) ✓
--   Sofa     dx=1.552,  dy=0.078 → world(-4.628, 3.831) ≈ IFC(-4.628, 3.831) ✓
--   Loveseat dx=3.72,   dy=0.682 → world(-6.796, 3.227) ≈ IFC(-6.796, 3.227) ✓
-- =============================================================================

-- ── 1. Piano: add explicit wall_rule='NORTH_WALL' ─────────────────────────────
-- Bom child id=150 (Piano, SH_LIVING_SET, seq=10).
-- INSERT OR REPLACE updates existing or inserts new row.

INSERT OR REPLACE INTO ad_bom_child_param
    (bom_child_id, param_key, param_value, is_active)
SELECT bc.bom_child_id, 'wall_rule', 'NORTH_WALL', 1
FROM ad_bom_child bc
WHERE bc.bom_id = 'SH_LIVING_SET' AND bc.role = 'PIANO';

-- ── 2. Sofa: deactivate OPPOSITE_WORK — primary anchor is now explicitly north ─
-- Loveseat has no wall_rule — no action needed.

UPDATE ad_bom_child_param
SET is_active = 0
WHERE bom_child_id IN (
    SELECT bom_child_id FROM ad_bom_child
    WHERE bom_id = 'SH_LIVING_SET' AND role = 'SOFA'
)
AND param_key = 'wall_rule';

-- ── 3. Verify ──────────────────────────────────────────────────────────────────

SELECT '=== SH_LIVING_SET params after Step 4 ===' AS label;
SELECT bc.bom_child_id, bc.role, bc.sequence, bc.locator_ref, bc.dx, bc.dy,
       p.param_key, p.param_value, p.is_active
FROM ad_bom_child bc
LEFT JOIN ad_bom_child_param p ON bc.bom_child_id = p.bom_child_id
WHERE bc.bom_id = 'SH_LIVING_SET' AND bc.is_active = 1
ORDER BY bc.sequence, p.param_key;
