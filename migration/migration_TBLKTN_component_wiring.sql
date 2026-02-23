-- ============================================================
-- MIGRATION: TBLKTN_COMPONENT_WIRING
-- Phase: Mesh2Library component library coverage (Watchdog §3)
--
-- All TB-LKTN compiled items must reference a component library entry.
-- This migration:
--   1. Adds missing WINDOW_W3 product (1800mm casement — main living/common)
--   2. Wires all 11 IfcWindow rules to their catalog product
--   3. Documents drain slab TODO (Java + position_rule change required)
--
-- Window mapping from element_rule dimensions:
--   1200×1200mm → WINDOW_W1 (6 windows: bilik_utama S, bilik_2 S, bilik_3 N, common N, bilik_2 E, bilik_3 E)
--   1800×1200mm → WINDOW_W3 (2 windows: common SOUTH × 2) ← NEW PRODUCT
--   600×600mm   → WINDOW_W2 (3 windows: bilik_mandi N, tandas W, bilik_utama W [small])
-- ============================================================

PRAGMA foreign_keys = ON;

-- ── 1. Add WINDOW_W3 (1800mm wide casement, Malaysian residential) ─
-- Source: TB-LKTN common room south facade — two 1800mm openings confirmed
-- on elevation drawing. Standard Malaysian casement: 1800×1200mm opening.
INSERT OR IGNORE INTO ad_product_dim
    (product_id, product_type, width, depth, height,
     fits_in, requires_host, code_ref, is_active, extracted_from)
VALUES
    ('WINDOW_W3', 'WINDOW',
     1.8,    -- width: 1800mm casement
     0.1,    -- depth: 100mm frame (same as W1, W2)
     1.2,    -- height: 1200mm (matches W1 height)
     '["LIVING","OPEN_PLAN","COMMON"]',
     'WALL',
     'MS_1064_WINDOW',
     1,
     'EXTRACTED_TBLKTN');

-- ── 2. Wire window family_refs ────────────────────────────────

-- 1200×1200mm → WINDOW_W1
UPDATE ad_element_rule SET family_ref = 'WINDOW_W1'
WHERE building_type = 'TB_LKTN'
  AND ifc_class = 'IfcWindow'
  AND width_mm = 1200.0 AND height_extent_mm = 1200.0;

-- 1800×1200mm → WINDOW_W3 (common south, living-area casements)
UPDATE ad_element_rule SET family_ref = 'WINDOW_W3'
WHERE building_type = 'TB_LKTN'
  AND ifc_class = 'IfcWindow'
  AND width_mm = 1800.0 AND height_extent_mm = 1200.0;

-- 600×600mm EW → WINDOW_W2 (bilik_mandi north)
UPDATE ad_element_rule SET family_ref = 'WINDOW_W2'
WHERE building_type = 'TB_LKTN'
  AND ifc_class = 'IfcWindow'
  AND width_mm = 600.0 AND height_extent_mm = 600.0
  AND orientation = 'EW';

-- 600×600mm NS → WINDOW_W2 (tandas west — width/depth swapped for NS orientation)
UPDATE ad_element_rule SET family_ref = 'WINDOW_W2'
WHERE building_type = 'TB_LKTN'
  AND ifc_class = 'IfcWindow'
  AND depth_mm = 600.0 AND height_extent_mm = 600.0
  AND orientation = 'NS';

-- ── 3. Drain slab TODO note ───────────────────────────────────
-- IfcSlab_drain_1 through _8:
--   Current:  position_rule=ABSOLUTE, ifc_class=IfcSlab, family_ref=NULL, 8v/12f GEN-BOX
--   Target:   position_rule=BOUNDARY/PERIMETER, ifc_class=IfcDistributionFlowElement,
--             family_ref='DRAIN_HALFROUND_MY', 68v/132f (LOD400 N=16)
--
-- DRAIN_HALFROUND_MY is now registered in ad_parametric_mesh (TODO #1 done).
-- Rewiring requires:
--   a) Java: BuildingWriter to handle PERIMETER position_rule + HalfRoundDrainMesh dispatch
--      (same metadata-agnostic refactor needed for roof dispatch — do together)
--   b) Data: UPDATE ifc_class + position_rule + family_ref for drain rules
--   c) Data: Remove absolute position_values — use perimeter offset_from_wall_mm=700mm
--
-- Blocked on Java compiler-agnostic refactor. Drain rules stay ABSOLUTE/GEN-BOX until then.
-- Preflight Check D will flag: non-FURN elements with no geometry_map (drain = GEN-BOX expected).

-- ── Verify ───────────────────────────────────────────────────
SELECT 'Windows wired: ' || COUNT(*) || ' of 11'
FROM ad_element_rule
WHERE building_type = 'TB_LKTN'
  AND ifc_class = 'IfcWindow'
  AND family_ref IS NOT NULL;

SELECT element_ref, family_ref, width_mm, depth_mm, orientation
FROM ad_element_rule
WHERE building_type = 'TB_LKTN' AND ifc_class = 'IfcWindow'
ORDER BY element_ref;
