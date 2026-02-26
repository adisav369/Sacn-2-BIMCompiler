-- ============================================================
-- MIGRATION: ROOF_HIP_MAIN — TB-LKTN Main Block Hip Roof
-- iDempiere analogy: M_Product variant + AD_Parm parameters
-- ─────────────────────────────────────────────────────────
-- Source: [EXTRACTED: TBLKTN_HOUSE.pdf page 3]
-- Component: ROOF_HIP_MAIN
-- IFC class: IfcRoof, PredefinedType=HIP_ROOF
-- Material: Corrugated sheet metal (hatching confirmed on drawing)
--
-- Mesh type: HIP_ROOF_MY
-- Generator: HipRoofMesh (sealed ParametricMesh, com.bim.compiler.mesh)
-- Topology: 6-vertex hip prism — 4 eave corners + 2 ridge ends
--           All four sides slope: south slope, north slope,
--           west hip, east hip (6 triangular faces)
--
-- Geometry (canonical, Z=0 at eave plane, Y=0 at ridge centre line):
--   halfX = depth_mm/2/1000  = 4.950m  (E-W half-span)
--   halfY = span_mm/2/1000   = 2.700m  (N-S half-span)
--   ridgeHalfLen = ridge_length_mm/2/1000 = 1.850m
--   ridgeZ = halfY × tan(25°) ≈ 1.259m
--
-- Valley interface: ROOF_PORCH_GABLE meets at row-3 S eave (T-junction).
--                  Each component is independently parametric — no merged verts.
-- ============================================================

-- ── Shape type registry ──────────────────────────────────────
INSERT OR IGNORE INTO ad_parametric_mesh
    (mesh_type, generator_class, description, provenance)
VALUES
    ('HIP_ROOF_MY',
     'HipRoofMesh',
     'Malaysian residential main-block hip roof — all four sides hipped, E-W ridge',
     'RESEARCHED_UBBL');

-- ── Parameters (all EXTRACTED from TBLKTN_HOUSE.pdf page 3) ─
-- span_mm   = total N-S footprint including overhangs (perpendicular to X ridge)
-- depth_mm  = total E-W footprint including overhangs (parallel to X ridge)
-- These are TOTAL dimensions (overhang_mm already embedded) so HipRoofMesh
-- does not add a second overhang offset — contrast with GableRoofMesh which
-- adds overhang separately from wall span.
INSERT OR IGNORE INTO ad_parametric_mesh_param
    (mesh_type, param_key, param_value, param_unit, provenance)
VALUES
-- ── Pitch ────────────────────────────────────────────────────
('HIP_ROOF_MY', 'pitch_deg',        '25',   'degrees', 'EXTRACTED_TBLKTN'),
-- ── Footprint (total, overhangs included) ────────────────────
('HIP_ROOF_MY', 'span_mm',          '5400', 'mm',      'EXTRACTED_TBLKTN'),
   -- N-S total: 700(S-overhang) + 4000(wall) + 700(N-overhang) = 5400
   -- [drawing rows 3-5: 700+1600+3100 = 5400, overhang_mm=700 confirmed all four sides]
('HIP_ROOF_MY', 'depth_mm',         '9900', 'mm',      'EXTRACTED_TBLKTN'),
   -- E-W total: 700(W-overhang) + 8500(wall_span) + 700(E-overhang) = 9900
('HIP_ROOF_MY', 'wall_span_mm',     '8500', 'mm',      'EXTRACTED_TBLKTN'),
   -- Informational: E-W between outer wall faces (grid A-E)
('HIP_ROOF_MY', 'wall_depth_mm',    '4000', 'mm',      'EXTRACTED_TBLKTN'),
   -- Informational: N-S between outer wall faces (rows 3-5)
-- ── Ridge ────────────────────────────────────────────────────
('HIP_ROOF_MY', 'ridge_length_mm',  '3700', 'mm',      'EXTRACTED_TBLKTN'),
   -- Centre bay B-D (explicit annotation on drawing)
('HIP_ROOF_MY', 'ridge_axis',       'X',    'axis',    'EXTRACTED_TBLKTN'),
   -- Ridge runs E-W (along X); both A and E ends are hipped
-- ── Overhang ─────────────────────────────────────────────────
('HIP_ROOF_MY', 'overhang_mm',      '700',  'mm',      'EXTRACTED_TBLKTN'),
   -- Uniform 700mm all four sides (× 6 pitch annotations on drawing)
-- ── Wall plate (datum) ────────────────────────────────────────
('HIP_ROOF_MY', 'wall_plate_aff_mm','2700', 'mm',      'EXTRACTED_TBLKTN'),
   -- BOTTOM=WALL_PLATE at 2700mm AFF (standard MY single-storey)
-- ── Hip geometry ─────────────────────────────────────────────
('HIP_ROOF_MY', 'hip_plan_angle_deg','41',  'degrees', 'EXTRACTED_TBLKTN');
   -- Computed from drawing: arctan(2700/3100) ≈ 41° (not standard 45°)
   -- hip_setback = (depth_mm - ridge_length_mm) / 2 = (9900-3700)/2 = 3100mm
   -- hip_rise    = span_mm/2 = 2700mm → angle = arctan(2700/3100) ≈ 41.0°

-- ── Regional preset ──────────────────────────────────────────
-- Preset seq_no=20 (after GABLE_ROOF_MY=10, before future FLAT_ROOF=30)
INSERT OR IGNORE INTO ad_roof_preset
    (preset_id, mesh_type, building_type, region, is_active, seq_no, provenance)
VALUES
    ('ROOF_HIP_MY_RESIDENTIAL',
     'HIP_ROOF_MY',
     'RESIDENTIAL',
     'MY',
     1,
     20,
     'EXTRACTED_TBLKTN');

-- ── ad_spatial_rule placeholder ──────────────────────────────
-- Spatial rule: ROOF_HIP_MAIN.DISCHARGE projected to ground must be
-- within 150mm of DRAIN_PERIMETER_HALFROUND.INLET centreline.
-- Rule record deferred until DRAIN_HALFROUND_MY element exists.
-- See migration_TBLKTN_drain_halfround.sql for the companion rule INSERT.
