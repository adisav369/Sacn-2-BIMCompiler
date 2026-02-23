-- ============================================================
-- MIGRATION: TBLKTN_MESH_WIRING
-- Phase: Mesh2Library TODO #3 + #4 (Watchdog §2)
--
-- 1. Wire IfcRoof_1   → family_ref = 'HIP_ROOF_MY'
--    Wire IfcRoofCanopy_1 → family_ref = 'GABLE_PORCH_MY'
--    Both rules retain orientation='GABLE_25' until the compiler is
--    refactored to be metadata-agnostic (compiler reads family_ref →
--    dispatches to ParametricMesh, not to hardcoded writeGableGeometry()).
--    The family_ref wiring records the INTENT in metadata now.
--    Compiler agnostic flag: ADD orientation='HIP_25' when Java routing lands.
--
-- 2. Add ad_product_dim catalog entries for fabricated mesh BOM leaves.
--    Three-Table Authority: fabricated mesh leaf nodes must have a product_dim
--    entry (bounding box in meters) so ad_bom_child_param has an FK target.
--    Dims = bbox of the parametric mesh output at canonical params.
--
--    HIP_ROOF_MY:
--      width  = depth_mm/1000  = 9.9m  (E-W total footprint)
--      depth  = span_mm/1000   = 5.4m  (N-S total footprint — hip portion rows 3-5)
--      height = ridgeZ = (span_mm/2000) × tan(25°) = 2.7 × 0.46631 ≈ 1.259m
--
--    GABLE_PORCH_MY (ridge_axis=Y, GableRoofMesh):
--      span_mm=3700 (E-W wall), depth_mm=2900 (N-S wall), overhang_mm=700
--      eave_W  = span_mm/2 + overhang = 1850 + 700 = 2550mm → total E-W = 5100mm
--      eave_D  = depth_mm  + overhang = 2900 + 700 = 3600mm → total N-S = 3600mm
--      ridgeZ  = (span_mm/2/1000) × tan(25°) = 1.85 × 0.46631 ≈ 0.863m
--
--    DRAIN_HALFROUND_MY (1m canonical segment):
--      width  = 0.23m  (outer diameter 230mm)
--      depth  = 1.0m   (canonical segment length)
--      height = 0.115m (outer radius = half-diameter)
--
-- 3. Add ad_roof_preset for canopy (porch) type.
--    CANOPY_MY_PORCH routes DSL 'roof: ATTACHED' in PORCH rooms to GABLE_PORCH_MY.
--
-- Compiler agnostic note (Watchdog §2 TODO #5 elaboration):
--   BuildingWriter.resolveRoofGeometry() currently checks orientation.startsWith("GABLE_")
--   and calls writeGableGeometry(). This hardcoded path must be replaced by:
--     1. Read family_ref from placement
--     2. Load ad_parametric_mesh row for family_ref → get generator_class
--     3. Load ad_parametric_mesh_param rows → build MeshParameters
--     4. Inject runtime dims: span_mm = (p.maxY()-p.minY())*1000, depth_mm = (p.maxX()-p.minX())*1000
--        (for mesh types without static span/depth params in DB — e.g. GABLE_ROOF_MY)
--        (for mesh types WITH static params — HIP_ROOF_MY, GABLE_PORCH_MY — DB values take precedence)
--     5. Dispatch: new GableRoofMesh() or new HipRoofMesh() per generator_class
--     6. MeshResult → writeTransformedGeometry()
--   span_mm/depth_mm are inferred from the 2D layout: the ENVELOPE placement rule
--   already computes the building footprint bbox from room bounds (grid axes × spacing).
--   No new data source needed — bbox IS the 2D layout projection.
-- ============================================================

PRAGMA foreign_keys = ON;

-- ── 1. Wire family_refs ──────────────────────────────────────

-- Main body roof: HIP_ROOF_MY (hip, all four sides slope)
-- orientation retained as GABLE_25 until compiler dispatch is refactored.
-- TODO: change orientation to 'HIP_25' when BuildingWriter reads family_ref for dispatch.
UPDATE ad_element_rule
   SET family_ref = 'HIP_ROOF_MY'
 WHERE building_type = 'TB_LKTN'
   AND element_ref   = 'IfcRoof_1';

-- Porch canopy: GABLE_PORCH_MY (gable, Y-ridge, south-facing bargeboard)
UPDATE ad_element_rule
   SET family_ref = 'GABLE_PORCH_MY'
 WHERE building_type = 'TB_LKTN'
   AND element_ref   = 'IfcRoofCanopy_1';

-- ── 2. Fabricated mesh product catalog entries ───────────────

-- HIP_ROOF_MY — main block hip roof bbox (canonical TB-LKTN dimensions)
INSERT OR IGNORE INTO ad_product_dim
    (product_id, product_type, width, depth, height,
     fits_in, requires_host, code_ref, is_active, extracted_from)
VALUES
    ('HIP_ROOF_MY', 'MESH_ROOF',
     9.9,    -- E-W total footprint (depth_mm=9900mm, includes 700mm overhang E+W)
     5.4,    -- N-S total footprint (span_mm=5400mm, includes 700mm overhang N+S)
     1.259,  -- ridge height = (2700mm) × tan(25°) ≈ 1259mm
     '["BUILDING"]',
     'ROOF_PLATE',
     'JKR_ROOF_STANDARD',
     1,
     'EXTRACTED_TBLKTN');

-- GABLE_PORCH_MY — front porch gable bbox (Y-ridge, south bargeboard)
-- eave_total_EW = span_mm + 2×overhang = 3700+1400 = 5100mm
-- eave_total_NS = depth_mm + overhang (south only; north=valley, no overhang) = 2900+700 = 3600mm
-- ridgeZ = (span_mm/2/1000) × tan(25°) = 1.85 × 0.46631 ≈ 0.863m
INSERT OR IGNORE INTO ad_product_dim
    (product_id, product_type, width, depth, height,
     fits_in, requires_host, code_ref, is_active, extracted_from)
VALUES
    ('GABLE_PORCH_MY', 'MESH_ROOF',
     5.1,    -- E-W total: span 3700 + 700 overhang each side = 5100mm
     3.6,    -- N-S total: depth 2900 + 700 south overhang = 3600mm (valley end no overhang)
     0.863,  -- ridge height = 1850mm × tan(25°)
     '["PORCH","CAR_PORCH"]',
     'ROOF_PLATE',
     'JKR_ROOF_STANDARD',
     1,
     'EXTRACTED_TBLKTN');

-- DRAIN_HALFROUND_MY — canonical 1m segment bbox
-- Already inserted by migration_TBLKTN_drain_halfround.sql; guard with OR IGNORE.
INSERT OR IGNORE INTO ad_product_dim
    (product_id, product_type, width, depth, height,
     fits_in, requires_host, code_ref, is_active, extracted_from)
VALUES
    ('DRAIN_HALFROUND_MY', 'DRAINAGE',
     0.23,   -- outer diameter 230mm
     1.0,    -- canonical segment length 1000mm
     0.115,  -- outer radius = half-diameter 115mm
     '["PERIMETER"]',
     'GROUND_SLAB',
     'JKR_DRAIN_SCHEDULE_G5',
     1,
     'EXTRACTED_TBLKTN');

-- ── 3. Add canopy roof preset ────────────────────────────────
-- PORCH rooms with 'roof: ATTACHED' in DSL route here via ad_roof_preset.
-- seq_no=30: fires after ROOF_MY_RESIDENTIAL (seq_no=10) — porch-level, not building-level.
INSERT OR IGNORE INTO ad_roof_preset
    (preset_id, mesh_type, building_type, region, is_active, seq_no, provenance)
VALUES
    ('CANOPY_MY_PORCH',
     'GABLE_PORCH_MY',
     'RESIDENTIAL',
     'MY',
     1,
     30,
     'EXTRACTED_TBLKTN');

-- ── Verify ───────────────────────────────────────────────────
SELECT 'Roof family_refs: ' ||
       GROUP_CONCAT(element_ref || '=' || COALESCE(family_ref,'NULL'), ', ')
FROM ad_element_rule
WHERE building_type = 'TB_LKTN' AND ifc_class = 'IfcRoof';

SELECT 'Product dims registered: ' ||
       GROUP_CONCAT(product_id, ', ')
FROM ad_product_dim
WHERE product_id IN ('HIP_ROOF_MY','GABLE_PORCH_MY','DRAIN_HALFROUND_MY');

SELECT 'Roof presets: ' ||
       GROUP_CONCAT(preset_id || '→' || mesh_type, ', ')
FROM ad_roof_preset
ORDER BY seq_no;
