-- ============================================================
-- MIGRATION: ROOF_PORCH_GABLE — TB-LKTN Front Porch Gable Roof
-- iDempiere analogy: M_Product variant + AD_Parm parameters
-- ─────────────────────────────────────────────────────────
-- Source: [EXTRACTED: TBLKTN_HOUSE.pdf page 3]
-- Component: ROOF_PORCH_GABLE
-- IFC class: IfcRoof, PredefinedType=GABLE_ROOF
-- Material: Corrugated sheet (matching main roof)
--
-- Mesh type: GABLE_PORCH_MY
-- Generator: GableRoofMesh (reuses existing sealed type — same generator class,
--            different parameter set; ridge_axis=Y = N-S ridge, gable faces SOUTH)
--
-- Valley interface: Connects to ROOF_HIP_MAIN at row-3 T-junction.
--   BACK=VALLEY_JUNCTION (hooks to main roof at row 3)
--   FRONT=GABLE_END (exposed bargeboard — BC marker confirmed on drawing)
--   Each component is independently parametric; valley rafter is a third element.
--
-- GableRoofMesh parameter convention (established by GABLE_ROOF_MY):
--   span_mm   = wall span PERPENDICULAR to ridge (overhang added separately)
--   depth_mm  = wall span PARALLEL to ridge (overhang added separately)
--   overhang_mm = eave overhang beyond wall face
--
-- For GABLE_PORCH_MY (ridge_axis=Y, ridge runs N-S):
--   span_mm = E-W wall width = 5100 - 2×700 = 3700mm (B→D grid, explicit on drawing)
--   depth_mm = N-S wall depth = 600 + 2300 = 2900mm
--     600mm = valley-junction offset (back, row-3 to first internal line)
--     2300mm = main porch bay depth
--   ridgeZ = (span_mm/2/1000) × tan(25°) = 1.85 × 0.46631 ≈ 0.863m above wall plate
-- ============================================================

-- ── Shape type registry ──────────────────────────────────────
-- NOTE: generator_class = GableRoofMesh (same sealed type as GABLE_ROOF_MY).
-- The sealed ParametricMesh interface already permits this class.
-- No new Java implementation required for this migration.
INSERT OR IGNORE INTO ad_parametric_mesh
    (mesh_type, generator_class, description, provenance)
VALUES
    ('GABLE_PORCH_MY',
     'GableRoofMesh',
     'TB-LKTN front porch gable — south-facing, N-S ridge, T-junction valley at row 3',
     'EXTRACTED_TBLKTN');

-- ── Parameters ───────────────────────────────────────────────
INSERT OR IGNORE INTO ad_parametric_mesh_param
    (mesh_type, param_key, param_value, param_unit, provenance)
VALUES
-- ── Pitch ────────────────────────────────────────────────────
('GABLE_PORCH_MY', 'pitch_deg',          '25',   'degrees', 'EXTRACTED_TBLKTN'),
   -- Matches main roof pitch (× 6 annotations on drawing)
-- ── Wall spans (GableRoofMesh convention: wall only, overhang added separately)
('GABLE_PORCH_MY', 'span_mm',            '3700', 'mm',      'EXTRACTED_TBLKTN'),
   -- E-W wall width: B→D grid = 3700mm (also = ridge_length_mm of ROOF_HIP_MAIN — T-junction spans full ridge bay)
('GABLE_PORCH_MY', 'depth_mm',           '2900', 'mm',      'EXTRACTED_TBLKTN'),
   -- N-S wall depth: 600 (valley offset) + 2300 (porch bay) = 2900mm
   -- [EXTRACTED: porch_depth_mm=3600 total; 3600 - 700(front overhang) = 2900 wall depth]
-- ── Overhang ─────────────────────────────────────────────────
('GABLE_PORCH_MY', 'overhang_mm',        '700',  'mm',      'EXTRACTED_TBLKTN'),
   -- Front eave (SOUTH/road side) and side eaves: 700mm
('GABLE_PORCH_MY', 'back_overhang_mm',   '600',  'mm',      'EXTRACTED_TBLKTN'),
   -- Back overhang (NORTH, into valley junction): 600mm
   -- Informational — GableRoofMesh uses uniform overhang_mm; back_overhang
   -- is stored here for the valley rafter element to reference.
-- ── Ridge orientation ─────────────────────────────────────────
('GABLE_PORCH_MY', 'ridge_axis',         'Y',    'axis',    'EXTRACTED_TBLKTN'),
   -- Ridge runs N-S (perpendicular to ROOF_HIP_MAIN E-W ridge)
   -- GableRoofMesh ridge_axis=Y: gable ends face E and W;
   -- SOUTH face is the exposed bargeboard (gable_face annotation below)
('GABLE_PORCH_MY', 'gable_face',         'SOUTH','cardinal', 'EXTRACTED_TBLKTN'),
   -- Informational: exposed bargeboard faces road (row 1 side)
   -- BC marker on drawing confirms bargeboard at south end
-- ── Valley junction ───────────────────────────────────────────
('GABLE_PORCH_MY', 'valley_type',        'T_JUNCTION', 'string', 'EXTRACTED_TBLKTN'),
   -- Porch ridge perpendicular to main ridge → T-junction at row 3
('GABLE_PORCH_MY', 'connects_to',        'HIP_ROOF_MY', 'mesh_ref', 'EXTRACTED_TBLKTN'),
   -- Companion component reference (for ad_spatial_rule wiring)
-- ── Wall plate (datum) ────────────────────────────────────────
('GABLE_PORCH_MY', 'wall_plate_aff_mm',  '2700', 'mm',      'EXTRACTED_TBLKTN');
   -- BOTTOM=WALL_PLATE at 2700mm AFF (matching main roof datum)

-- ── Regional preset ──────────────────────────────────────────
-- Preset seq_no=30 (after HIP_ROOF_MY=20)
INSERT OR IGNORE INTO ad_roof_preset
    (preset_id, mesh_type, building_type, region, is_active, seq_no, provenance)
VALUES
    ('ROOF_PORCH_GABLE_MY',
     'GABLE_PORCH_MY',
     'RESIDENTIAL',
     'MY',
     1,
     30,
     'EXTRACTED_TBLKTN');

-- ── Implementation note ───────────────────────────────────────
-- GableRoofMesh ridge_axis=Y path: ridgeX = spanM/2.0
-- This positions the ridge at X = spanM/2 instead of X = 0 (centre).
-- This is a known defect in the Y-axis branch — to be corrected in the
-- GableRoofMesh implementation when the PARAMETRIC wiring is activated.
-- The defect does not affect the DB migration (parameters are correct).
-- Workaround: until corrected, use ridge_axis=X with span and depth swapped
-- if immediate geometry generation is required.
