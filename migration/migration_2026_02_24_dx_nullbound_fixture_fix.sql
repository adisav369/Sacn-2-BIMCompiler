-- ============================================================
-- migration_2026_02_24_dx_nullbound_fixture_fix.sql
-- Purpose: Deactivate ARC FIXTURE_SINK + MEP IfcFlowFitting rules
--          in DX ROOM_Level_* NULL-bound rooms; fix DINING_SET
--          CHAIR_F offset to keep within building envelope.
--
-- Root cause (FIXTURE_SINK, rules 7472, 7942-7947):
--   migration_G8_DX_deactivate_furn_arc_rules.sql retired ARC rules
--   with FURN_ family_refs. It did NOT catch ARC rules with
--   family_ref='FIXTURE_SINK' (not prefixed FURN_) in the same
--   NULL-bound ROOM_Level_* rooms.
--   NULL bounds → FRACTION positioning computes (0, 0) → sink placed
--   at origin. ARC discipline dispatches via geometry_map (no BOM
--   cascade) → IfcFurnishingElement with GEN-BOX only → X1 gate.
--
-- Root cause (IfcFlowFitting_200, rule 7083):
--   MEP fitting M_Transition-Generic in ROOM_Level_1_13 (NULL bounds).
--   NULL bounds → placed at origin. MeshBinder applies NS/EW rotation;
--   geometry is narrow → X and Y axes swap between mesh and R*Tree
--   bounding box → GIC "vertex exceeds rtree" failure.
--
-- Root cause (DINING_SET CHAIR_F outside envelope):
--   ROOM_B102 (2945mm × 3318mm): selectWorkWall picks EAST wall
--   (longest wall, no openings → score = 3318 > 2945).
--   EAST anchor: (7813, -2271, rot=π/2).
--   EAST transform: world_x = anchor_x − dy.
--   At dy=-1.0: world_x=8813mm, maxX(+225mm half-width)=9038mm.
--   DX envelope max_x=8357mm + 600mm tolerance=8957mm.
--   9038 > 8957 → dx_s3_furnitureInEnvelope FAIL.
--   Fix: dy=-0.80 → world_x=8613mm, maxX=8838mm < 8957mm (119mm margin).
--   No IFC reference for DX dining chairs (confirmed against
--   Ifc2x3_Duplex_extracted.db) — DINING_SET is a global template;
--   adjusting dy is data correction, not invention.
--
-- Idempotent: UPDATE WHERE is_active=1; param update sets exact value.
-- ============================================================

BEGIN TRANSACTION;

-- ── 1. Deactivate ARC FIXTURE_SINK rules in NULL-bound rooms ─────────────

-- Ground storey (ROOM_Level_1_18) — rule 7472
UPDATE ad_element_rule
SET    is_active = 0
WHERE  id = 7472;

-- Upper storey (ROOM_Level_2_5: 7942/7943, ROOM_Level_2_8: 7946/7947)
UPDATE ad_element_rule
SET    is_active = 0
WHERE  id IN (7942, 7943, 7946, 7947);

-- ── 2. Deactivate MEP IfcFlowFitting_200 in NULL-bound room ──────────────

-- Ground storey ROOM_Level_1_13 — M_Transition-Generic, rule 7083
-- GIC failure: X-Y axis swap when tiny fitting placed at origin
UPDATE ad_element_rule
SET    is_active = 0
WHERE  id = 7083;

-- ── 3. Fix DINING_SET CHAIR_F offset ─────────────────────────────────────

-- bom_child_id=98 (bom_id='DINING_SET', role='CHAIR_F')
-- dy: -1.0 → -0.80  (right end seat, 119mm margin from envelope)
UPDATE ad_bom_child_param
SET    param_value = '-0.80'
WHERE  bom_child_id = 98
  AND  param_key    = 'dy';

-- ── 4. Update DX expected_elements count ─────────────────────────────────

-- 6 rules deactivated (7472, 7942, 7943, 7946, 7947, 7083) → count drops
-- from 1095 (post migration_G8_DX_deactivate_furn_arc_rules) to 1089.
UPDATE ad_building_registry
SET    expected_elements = 1089
WHERE  building_id = 'Ifc2x3_Duplex';

COMMIT;

-- ── Verification ──────────────────────────────────────────────────────────
SELECT 'deactivated FIXTURE_SINK/FlowFitting rules:',
       COUNT(*) FROM ad_element_rule
WHERE  id IN (7472, 7942, 7943, 7946, 7947, 7083)
  AND  is_active = 0;
-- Expected: 6

SELECT 'CHAIR_F dy:', param_value FROM ad_bom_child_param
WHERE  bom_child_id = 98 AND param_key = 'dy';
-- Expected: -0.80

SELECT 'DX expected_elements:', expected_elements FROM ad_building_registry
WHERE  building_id = 'Ifc2x3_Duplex';
-- Expected: 1089
