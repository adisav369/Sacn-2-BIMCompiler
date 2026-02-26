-- ============================================================
-- MIGRATION: DRAIN_PERIMETER_HALFROUND — TB-LKTN G5 Perimeter Drain
-- iDempiere analogy: M_Product (catalog) + AD_Parm (mesh params)
-- ─────────────────────────────────────────────────────────
-- Source: [EXTRACTED: TBLKTN_HOUSE.pdf page 3 + schedule G5]
-- Component: DRAIN_PERIMETER_HALFROUND
-- IFC class: IfcDistributionFlowElement / IfcPipeSegment
-- Schedule ref: G5 — "230mm ø half p.c perimeter drain to be discharged
--               to the nearest road side drain to Engr's Detail"
-- Material: Half precast concrete (half p.c)
--
-- Mesh type: DRAIN_HALFROUND_MY
-- Generator: HalfRoundDrainMesh (new sealed ParametricMesh type)
-- Topology: canonical 1m segment — hollow semicircular channel
--   Profile (XZ plane): outer semicircle (R=115mm) + inner semicircle (r=75mm)
--   Open face at Z=0 (flat top, water inlet, faces upward)
--   Curved surfaces slope downward to Z=-R (outer bottom at Z=-0.115m)
--   Extruded along Y-axis for canonical segment_length_mm
--   N=8 half-circle divisions → 36 vertices, 68 triangular faces
--
-- Spatial rule: INLET must be within 150mm of roof DISCHARGE projected to ground.
-- The 700mm overhang positions the eave drip line directly over drain centre:
--   offset_from_wall_mm = 700mm aligns with overhang_mm of both roof components.
--
-- Run lengths (EXTRACTED — follow building footprint three sides):
--   north_run_mm: 9900  (full A→E width)
--   east_run_mm:  8200  (rows 1-5 depth, approx)
--   west_run_mm:  8200  (mirror of east)
--   south: OPEN — discharges to road drain at MH1
--
-- OUTLET: MH1 manhole at front-left corner (row 1, grid A)
-- FALL: minimum 1:100 toward MH1 [RESEARCHED: JKR drain gradient standard]
-- ============================================================

-- ── Shape type registry ──────────────────────────────────────
INSERT OR IGNORE INTO ad_parametric_mesh
    (mesh_type, generator_class, description, provenance)
VALUES
    ('DRAIN_HALFROUND_MY',
     'HalfRoundDrainMesh',
     'Half-round precast concrete perimeter drain — 230mm diameter, canonical 1m segment',
     'EXTRACTED_TBLKTN');

-- ── Parameters ───────────────────────────────────────────────
INSERT OR IGNORE INTO ad_parametric_mesh_param
    (mesh_type, param_key, param_value, param_unit, provenance)
VALUES
-- ── Cross-section ─────────────────────────────────────────────
('DRAIN_HALFROUND_MY', 'diameter_mm',           '230',  'mm',      'EXTRACTED_TBLKTN'),
   -- [EXTRACTED: TBLKTN_HOUSE.pdf schedule G5 — "230mm ø"]
('DRAIN_HALFROUND_MY', 'wall_thickness_mm',      '40',   'mm',      'RESEARCHED_JKR_DRAIN_PRECAST'),
   -- [RESEARCHED: standard half-round precast concrete wall ≈ 40mm for 230mm ø]
   -- inner_diameter = 230 - 2×40 = 150mm (flow channel)
('DRAIN_HALFROUND_MY', 'profile',               'HALF_ROUND', 'string', 'EXTRACTED_TBLKTN'),
   -- [EXTRACTED: "half p.c" = half precast concrete = half-round cross-section]
-- ── Canonical segment (1m unit for mesh generation) ──────────
('DRAIN_HALFROUND_MY', 'segment_length_mm',      '1000', 'mm',      'RESEARCHED_JKR_DRAIN_PRECAST'),
   -- Canonical 1m segment; actual runs assembled from segment repetitions
('DRAIN_HALFROUND_MY', 'segments_n',             '8',    'count',   'RESEARCHED_MESH2LIB'),
   -- Half-circle divisions for cross-section tessellation
   -- N=8 → 36 vertices, 68 triangles per 1m segment (adequate for clash detection)
-- ── Run lengths (building-specific, for spatial rule wiring) ─
('DRAIN_HALFROUND_MY', 'north_run_mm',            '9900', 'mm',      'EXTRACTED_TBLKTN'),
   -- Full A→E width (matches ROOF_HIP_MAIN depth_mm)
('DRAIN_HALFROUND_MY', 'east_run_mm',             '8200', 'mm',      'EXTRACTED_TBLKTN'),
   -- Rows 1→5 approximate depth (south road level to north wall + overhang)
('DRAIN_HALFROUND_MY', 'west_run_mm',             '8200', 'mm',      'EXTRACTED_TBLKTN'),
   -- Mirror of east run
('DRAIN_HALFROUND_MY', 'south_run_mm',            '0',    'mm',      'EXTRACTED_TBLKTN'),
   -- OPEN: south face discharges to road drain at MH1; no drain segment here
-- ── Position ──────────────────────────────────────────────────
('DRAIN_HALFROUND_MY', 'offset_from_wall_mm',     '700',  'mm',      'EXTRACTED_TBLKTN'),
   -- Drain centreline = 700mm from outer wall face
   -- Aligns with eave drip line: overhang_mm=700 on both roof components
   -- Spatial rule: INLET within 150mm of DISCHARGE projected to ground
('DRAIN_HALFROUND_MY', 'z_offset_aff_mm',         '0',    'mm',      'EXTRACTED_TBLKTN'),
   -- Drain sits at finished ground level (Z=0 relative to site datum)
-- ── Hydraulics (researched) ───────────────────────────────────
('DRAIN_HALFROUND_MY', 'fall_ratio',              '0.01', 'ratio',   'RESEARCHED_JKR_DRAIN_GRADIENT'),
   -- [RESEARCHED: JKR minimum fall 1:100 toward outlet MH1]
('DRAIN_HALFROUND_MY', 'outlet_ref',              'MH1',  'element_ref', 'EXTRACTED_TBLKTN');
   -- MH1 manhole at front-left corner (row 1, grid A)

-- ── Product catalog entry ─────────────────────────────────────
-- Intrinsic catalog dimensions for a canonical 1m segment (in METERS).
-- ad_product_dim stores cross-section envelope; run length varies at placement.
INSERT OR IGNORE INTO ad_product_dim
    (product_id, product_type, width, depth, height, code_ref, is_active)
VALUES
    ('DRAIN_HALFROUND_MY',
     'DRAINAGE',
     0.23,    -- width  = outer diameter (230mm)
     1.0,     -- depth  = canonical segment length (1m)
     0.115,   -- height = outer radius = half-diameter (115mm)
     'JKR_DRAIN_SCHEDULE_G5',
     1);

-- ── ad_spatial_rule: DISCHARGE → DRAIN alignment ─────────────
-- Spatial rule linking roof discharge points to drain inlet.
-- Topology type: DRAIN_MUST_CATCH_DISCHARGE (custom topology rule)
-- Subject: DRAIN_HALFROUND_MY element
-- Object:  ROOF_HIP_MAIN / ROOF_PORCH_GABLE DISCHARGE points
-- Tolerance: 150mm lateral offset allowed
--
-- INSERT deferred until DRAIN_HALFROUND_MY element refs are resolved in
-- ad_element_rule. Uncomment and populate element_ref once TB-LKTN drain
-- rows are inserted:
--
-- INSERT OR IGNORE INTO ad_spatial_rule
--     (rule_id, rule_type, subject_ref, object_ref, tolerance_mm, provenance)
-- VALUES
--     ('SR_TBLKTN_DRAIN_DISCHARGE',
--      'TOPOLOGY',
--      'DRAIN_HALFROUND_MY',
--      'ROOF_HIP_MAIN.DISCHARGE',
--      150,
--      'EXTRACTED_TBLKTN');
