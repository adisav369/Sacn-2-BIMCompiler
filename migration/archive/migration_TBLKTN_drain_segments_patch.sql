-- ============================================================
-- PATCH: DRAIN_HALFROUND_MY segments_n 8 → 16 (LOD400 compliance)
-- ─────────────────────────────────────────────────────────
-- Initial migration used segments_n=8 (LOD200 quality).
-- At 230mm diameter (R=115mm), sagitta per segment:
--   N=8:  R × (1 − cos(π/16)) ≈ 2.2mm  — exceeds LOD400 tolerance
--   N=12: ≈ 1.0mm                        — LOD300/400 threshold
--   N=16: ≈ 0.6mm                        — LOD400 compliant
-- Mesh stats at N=16: vertices=4×17=68, faces=8×16+4=132 triangles.
-- Reference: Mesh2Library.txt — minimum tessellation for drainage elements
--            at LOD400 is max 1mm chord deviation from true arc.
-- ============================================================

UPDATE ad_parametric_mesh_param
   SET param_value = '16',
       provenance  = 'RESEARCHED_MESH2LIB_LOD400'
 WHERE mesh_type = 'DRAIN_HALFROUND_MY'
   AND param_key = 'segments_n';
