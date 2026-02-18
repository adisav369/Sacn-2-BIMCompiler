-- =============================================================================
-- Phase RM-5: Closest-Fit Geometry Migration
-- =============================================================================
-- Fixes TB-LKTN door/window/fixture geometry assignments so that MeshBinder
-- can produce real LOD meshes instead of parametric boxes.
-- Run against: library/component_library.db
-- =============================================================================

-- ─── 1. Config flag ─────────────────────────────────────────────────────────
INSERT INTO ad_compiler_config (config_key, config_value, is_active)
VALUES ('closest_fit', 'true', 1);

-- ─── 2. Fix TB-LKTN doors: SH hash → Duplex 914mm door ─────────────────────
-- Current: c5357415e37e7593 (SH 810mm door, 1336V, wrong proportions)
-- Target:  0980b5c41bb2643f (DX 914mm door, 40V, 914×174×2108mm EW-aligned)
-- Scale factors: 900/914=0.985, 800/914=0.875, 700/914=0.766 — all within [0.3,3.0]
UPDATE ad_geometry_map
SET geometry_hash = '0980b5c41bb2643f',
    source = 'TB_LKTN:DX_914mm_door'
WHERE building_type = 'TB_LKTN' AND ifc_class = 'IfcDoor';

-- ─── 3. Fix TB-LKTN W3_Small windows: SH hash → Duplex 819mm window ────────
-- Current: 8aabd3e04dfb3bb2 (SH 1810mm window, 108V — way too wide for 600mm)
-- Target:  02788fc8c663321e (DX 819mm window, 56V — closer match, scale=0.73)
-- W1/W2 (1200-1800mm) keep SH hash — scale factors are fine
UPDATE ad_geometry_map
SET geometry_hash = '02788fc8c663321e',
    source = 'TB_LKTN:DX_819mm_window'
WHERE building_type = 'TB_LKTN' AND ifc_class = 'IfcWindow'
  AND element_ref = 'Window_W3_Small';

-- ─── 4. Add missing fixture geometry_map entries (ordinals 1-5) ─────────────
-- These 5 wet-room fixtures have element_rules but no geometry_map → parametric box
-- Ordinals 1-5 match RelationalResolver processing order for IfcFurnishingElement
INSERT INTO ad_geometry_map (building_type, element_ref, ifc_class, storey, ordinal, geometry_hash, source)
VALUES
  -- IfcFurnishingElement_1: kitchen sink (1200×600mm)
  -- Library: 005_915x535_single_end_bowl_sink (984V, 533×850×483mm)
  ('TB_LKTN', 'IfcFurnishingElement_1', 'IfcFurnishingElement', 'Ground Floor', 1,
   '07513b0bca891776', 'TB_LKTN:sink_bowl'),

  -- IfcFurnishingElement_2: basin (500×400mm)
  -- Library: same sink, scaled down (scaleX=0.94, scaleY=0.47)
  ('TB_LKTN', 'IfcFurnishingElement_2', 'IfcFurnishingElement', 'Ground Floor', 2,
   '07513b0bca891776', 'TB_LKTN:basin_sink'),

  -- IfcFurnishingElement_3: WC toilet (400×700mm)
  -- Library: Toilet (1829V, 507×800×787mm)
  ('TB_LKTN', 'IfcFurnishingElement_3', 'IfcFurnishingElement', 'Ground Floor', 3,
   '0cbf88c02987a900', 'TB_LKTN:toilet'),

  -- IfcFurnishingElement_4: shower tray (900×900mm)
  -- Library: Shower_Rectangular_865x814 (100V, 865×815×1829mm)
  ('TB_LKTN', 'IfcFurnishingElement_4', 'IfcFurnishingElement', 'Ground Floor', 4,
   '456eea772369752b', 'TB_LKTN:shower_rect'),

  -- IfcFurnishingElement_5: WC toilet (400×700mm)
  -- Library: Toilet (1829V, 507×800×787mm)
  ('TB_LKTN', 'IfcFurnishingElement_5', 'IfcFurnishingElement', 'Ground Floor', 5,
   '0cbf88c02987a900', 'TB_LKTN:toilet');

-- ─── 5. Fix zero-height fixtures in ad_element_rule ─────────────────────────
-- WC toilets: 787mm (from library mesh bounding box height)
UPDATE ad_element_rule SET height_mm = 787
WHERE building_type = 'TB_LKTN'
  AND element_ref IN ('IfcFurnishingElement_3', 'IfcFurnishingElement_5');

-- Shower: 1829mm (from library mesh bounding box height)
UPDATE ad_element_rule SET height_mm = 1829
WHERE building_type = 'TB_LKTN'
  AND element_ref = 'IfcFurnishingElement_4';

-- ─── Verification queries ───────────────────────────────────────────────────
-- After running, verify with:
-- SELECT ordinal, element_ref, geometry_hash, source FROM ad_geometry_map
-- WHERE building_type = 'TB_LKTN' AND ifc_class = 'IfcFurnishingElement' ORDER BY ordinal;
--
-- SELECT element_ref, height_mm FROM ad_element_rule
-- WHERE building_type = 'TB_LKTN' AND element_ref LIKE 'IfcFurnishingElement_%' ORDER BY element_ref;
