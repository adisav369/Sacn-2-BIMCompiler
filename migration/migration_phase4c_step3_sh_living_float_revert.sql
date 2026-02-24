-- =============================================================================
-- Phase 4c Step 3 — Revert SH_LIVING_SET Piano/Sofa/Loveseat to FLOAT
--
-- Problem (G8-SH failing):
--   Phase 4c Step 1 moved Piano/Sofa/Loveseat from FLOAT to NORTH_WALL GPD.
--   GPD places items starting from the NW corner (roomMinX), but the IFC
--   reference has them starting from the NE corner (roomMaxX) with a large
--   gap. GPD positions diverge from IFC reference by 3-7m.
--
-- Root cause:
--   loadBOMTree() reads dx/dy from ad_bom_child_param (default=0), NOT from
--   ad_bom_child.dx/dy columns where the IFC-calibrated offsets live.
--   NORTH_WALL GPD path ignores dx/dy — it uses product dims + cursor advance.
--
-- Fix:
--   a. Revert locator_ref='FLOAT' for Piano/Sofa/Loveseat (use FLOAT dx/dy path)
--   b. Deactivate SPACER_VAR (GPD-only concept, not needed for FLOAT path)
--   c. Java: loadBOMTree() falls back to raw.getDx()/getDy()/getDz() ORM columns
--      instead of defaulting to 0
--   d. Java: expandBOMNode() adds parent item AND expands sub-BOM (not either-or)
--
-- IFC-calibrated FLOAT offsets (in ad_bom_child.dx/dy, zone anchor = north wall
-- center = (-3.076, 3.909, π)):
--   Piano    dx=-3.749, dy=-0.2  → world(0.674,  4.109) ≈ IFC(0.673, 4.109) ✓
--   Sofa     dx=1.552,  dy=0.078 → world(-4.628, 3.831) ≈ IFC(-4.628,3.831) ✓
--   Loveseat dx=3.72,   dy=0.682 → world(-6.796, 3.227) ≈ IFC(-6.796,3.227) ✓
-- =============================================================================

-- ── 1. Revert Piano/Sofa/Loveseat to FLOAT ────────────────────────────────

UPDATE ad_bom_child
SET locator_ref = 'FLOAT',
    layout_strategy = 'FLOAT'
WHERE bom_id = 'SH_LIVING_SET'
  AND role IN ('PIANO', 'SOFA', 'SOFA_B');

-- ── 2. Deactivate SPACER_VAR (GPD-only, irrelevant for FLOAT path) ─────────

UPDATE ad_bom_child
SET is_active = 0
WHERE bom_id = 'SH_LIVING_SET'
  AND role = 'SPACER';

-- ── 3. Verify ──────────────────────────────────────────────────────────────

SELECT '=== SH_LIVING_SET after Step 3 ===' AS label;
SELECT sequence, child_name_pattern, role, locator_ref, layout_strategy,
       dx, dy, child_bom_id, is_active
FROM ad_bom_child
WHERE bom_id = 'SH_LIVING_SET'
ORDER BY sequence;
