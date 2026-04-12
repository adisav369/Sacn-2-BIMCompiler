-- V004_mined_rules.sql
-- Seed AD_Val_Rule for mined rules M1-M17 (DocValidate.md §15.5)
-- Source: Terminal (48,428 elements), Duplex (1,099 elements)
-- Mining results: TE_MINING_RESULTS.md
-- APPEND-ONLY: never modify this migration after first run

-- ============================================================
-- NOTE: V002 already has:
--   Rule 601 = MINED_FPR_NN_SPACING       (M1: sprinkler NN)
--   Rule 602 = NFPA13_LH_MAX_SPACING      (M1: code max)
--   Rule 603 = MINED_ELEC_SP_CLEARANCE     (M12: ELEC-SP)
--   Rule 701 = CLASH_MEP_STR_HARD          (M10: MEP×STR)
--   Rule 702 = CLASH_PLB_ELC_CLEARANCE     (M12: PLB×ELC)
--   Rule 703 = CLASH_FPR_ACMV_OBSTRUCTION  (M9: FP×ACMV)
--
-- This migration adds M2-M8, M11, M13-M17 (rules not yet seeded)
-- and vertical continuity rules M13-M15 (Tier 3).
-- ID range: 801-817
-- ============================================================

-- ============================================================
-- M2: FP Branch Pipe Max Length (Tier 1)
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (801, 'MINED_FP_BRANCH_MAX_LENGTH',
    'Fire sprinkler branch pipe max run length',
    'COMPLIANCE', 'FPR', 'NFPA 13 §8.15 (inferred)', 'INTL',
    'MINED:Terminal', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (8011, 801, 'max_run_length_mm', '12000', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8012, 801, 'ifc_class', 'IfcPipeSegment', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8013, 801, 'check_method', 'ROUTE_SEGMENT_SUM', 'TEXT', NULL);

INSERT INTO AD_Val_Rule_Occupancy VALUES (801, 1);  -- Light Hazard
INSERT INTO AD_Val_Rule_Occupancy VALUES (801, 6);  -- Airport Terminal

INSERT INTO AD_Val_Rule_Mining_Source VALUES (801, 'Terminal', 455, NULL, NULL, NULL, NULL, 'mm',
    'Sum of ROUTE segment lengths per branch run from TEE to last head', datetime('now'));

-- ============================================================
-- M3: FP Riser Diameter (Tier 1)
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (802, 'MINED_FP_RISER_DIAMETER',
    'Fire protection riser minimum diameter',
    'COMPLIANCE', 'FPR', 'NFPA 13 §8.15 (inferred)', 'INTL',
    'MINED:Terminal', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (8021, 802, 'min_main_diameter_mm', '50', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8022, 802, 'min_branch_diameter_mm', '25', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8023, 802, 'ifc_class', 'IfcPipeSegment', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8024, 802, 'check_method', 'M_PRODUCT_CROSS_SECTION', 'TEXT', NULL);

INSERT INTO AD_Val_Rule_Occupancy VALUES (802, 1);

INSERT INTO AD_Val_Rule_Mining_Source VALUES (802, 'Terminal', 455, 25, 343, 106, 250, 'mm',
    'MIN(width, depth) of IfcPipeSegment in FP discipline — cross-section diameter', datetime('now'));

-- ============================================================
-- M4: Light Fixture Grid Spacing (Tier 1)
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (803, 'MINED_ELEC_LIGHT_SPACING',
    'Light fixture NN spacing (mined from TE)',
    'COMPLIANCE', 'ELEC', 'IES RP-1 (observed)', 'INTL',
    'MINED:Terminal', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (8031, 803, 'max_spacing_mm', '5000', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8032, 803, 'ifc_class', 'IfcLightFixture', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8033, 803, 'verdict', 'WARN', 'TEXT', NULL);

INSERT INTO AD_Val_Rule_Occupancy VALUES (803, 1);
INSERT INTO AD_Val_Rule_Occupancy VALUES (803, 6);

INSERT INTO AD_Val_Rule_Mining_Source VALUES (803, 'Terminal', 814, 0, 5999, 3964, 5200, 'mm',
    'NN centroid distance between IfcLightFixture on same storey', datetime('now'));

-- ============================================================
-- M5: Light Fixture Ceiling Offset (Tier 1)
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (804, 'MINED_ELEC_CEILING_OFFSET',
    'Light fixture Z-offset consistency per storey (mined from TE)',
    'COMPLIANCE', 'ELEC', 'IES (observed)', 'INTL',
    'MINED:Terminal', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (8041, 804, 'max_z_deviation_mm', '200', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8042, 804, 'ifc_class', 'IfcLightFixture', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8043, 804, 'check_method', 'PER_STOREY_Z_CONSISTENCY', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8044, 804, 'verdict', 'WARN', 'TEXT', NULL);

INSERT INTO AD_Val_Rule_Mining_Source VALUES (804, 'Terminal', 814, 89, 534, 100, 200, 'mm',
    'Std dev of IfcLightFixture Z relative to storey slab soffit, per storey', datetime('now'));

-- ============================================================
-- M6: Column Grid Spacing (Tier 1 — advisory, design intent)
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (805, 'MINED_STR_COLUMN_BAY',
    'Structural column bay spacing (mined from TE — advisory)',
    'COMPLIANCE', 'STR', 'Engineering practice (observed)', 'INTL',
    'MINED:Terminal', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (8051, 805, 'typical_bay_mm', '8500', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8052, 805, 'bay_tolerance_mm', '2000', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8053, 805, 'ifc_class', 'IfcColumn', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8054, 805, 'verdict', 'WARN', 'TEXT', NULL);

INSERT INTO AD_Val_Rule_Mining_Source VALUES (805, 'Terminal', 758, 0, 14000, 8500, 13000, 'mm',
    'NN column centroid distance per storey — multi-modal: 5000/8500/12000mm', datetime('now'));

-- ============================================================
-- M7: Beam Span vs Column Spacing (Tier 1)
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (806, 'MINED_STR_BEAM_SPAN',
    'Beam span must not exceed column bay width',
    'COMPLIANCE', 'STR', 'Engineering practice', 'INTL',
    'MINED:Terminal', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (8061, 806, 'check_method', 'BEAM_LENGTH_VS_BAY', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8062, 806, 'ifc_class', 'IfcBeam', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8063, 806, 'tolerance_pct', '5', 'NUM', NULL);

INSERT INTO AD_Val_Rule_Mining_Source VALUES (806, 'Terminal', 758, NULL, NULL, NULL, NULL, 'mm',
    'Beam M_Product.width vs nearest column pair spacing', datetime('now'));

-- ============================================================
-- M8: Roof Tile Pitch (Tier 1 — already TILE-verified)
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (807, 'MINED_ARC_TILE_PITCH',
    'Roof tile pitch consistency (TILE verb: 0.0mm fidelity)',
    'COMPLIANCE', 'ARC', 'Architectural practice (verified)', 'INTL',
    'MINED:Terminal', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (8071, 807, 'tile_width_mm', '495', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8072, 807, 'tile_depth_mm', '150', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8073, 807, 'step_mm', '495', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8074, 807, 'ifc_class', 'IfcPlate', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8075, 807, 'check_method', 'TILE_VERB_FIDELITY', 'TEXT', NULL);

INSERT INTO AD_Val_Rule_Mining_Source VALUES (807, 'Terminal', 33324, 495, 495, 495, 495, 'mm',
    'IfcPlate avg width 495.5mm, avg depth 149.6mm — TILE(15x294, 495mm step)', datetime('now'));

-- ============================================================
-- M11: MEP×ARC Fire Wall Penetration (Tier 2 — clash)
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (808, 'CLASH_MEP_ARC_FIRE_PEN',
    'MEP penetration through fire-rated wall requires fire collar',
    'CLASH', NULL, 'Building code — fire compartmentation', 'INTL',
    'RESEARCHED', NULL, NULL, 1);

INSERT INTO AD_Clash_Rule VALUES (7, 'SP', 'ARC',
    NULL, 'fire_rating IS NOT NULL',
    'MATERIAL', NULL, 'WARN',
    'Add fire collar/damper at penetration point per fire compartment rules', 808);

INSERT INTO AD_Clash_Rule VALUES (8, 'ACMV', 'ARC',
    'ifc_class IN (''IfcDuctSegment'', ''IfcDuctFitting'')',
    'fire_rating IS NOT NULL',
    'MATERIAL', NULL, 'WARN',
    'Add fire damper at duct penetration through fire-rated wall', 808);

-- ============================================================
-- M13: CW Riser Vertical Continuity (Tier 3)
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (809, 'VERT_CW_RISER_CONTINUITY',
    'Cold water riser X,Y alignment across storeys',
    'CONTINUITY', 'CW', 'Engineering practice', 'INTL',
    'MINED:Terminal', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (8091, 809, 'element_classes', 'IfcPipeSegment', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8092, 809, 'max_xy_drift_mm', '50', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8093, 809, 'check_across', 'STOREY', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8094, 809, 'min_storey_span', '3', 'NUM', NULL);

INSERT INTO AD_Val_Rule_Mining_Source VALUES (809, 'Terminal', NULL, NULL, NULL, NULL, NULL, 'mm',
    'Elements at same ROUND(x,1), ROUND(y,1) across >=3 storeys — CW pipes', datetime('now'));

-- ============================================================
-- M14: STR Column Vertical Continuity (Tier 3)
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (810, 'VERT_STR_COLUMN_CONTINUITY',
    'Column grid X,Y alignment across storeys (max 25mm drift)',
    'CONTINUITY', 'STR', 'Engineering practice', 'INTL',
    'MINED:Terminal', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (8101, 810, 'element_classes', 'IfcColumn', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8102, 810, 'max_xy_drift_mm', '25', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8103, 810, 'check_across', 'STOREY', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8104, 810, 'min_storey_span', '2', 'NUM', NULL);

INSERT INTO AD_Val_Rule_Mining_Source VALUES (810, 'Terminal', 758, NULL, NULL, NULL, NULL, 'mm',
    'Column centroid X,Y grouped by ROUND(x,1), ROUND(y,1) — check drift across storeys', datetime('now'));

-- ============================================================
-- M15: FP Riser Vertical Continuity (Tier 3)
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (811, 'VERT_FP_RISER_CONTINUITY',
    'Fire protection riser X,Y alignment across storeys',
    'CONTINUITY', 'FPR', 'Engineering practice', 'INTL',
    'MINED:Terminal', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (8111, 811, 'element_classes', 'IfcPipeSegment', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8112, 811, 'max_xy_drift_mm', '50', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8113, 811, 'check_across', 'STOREY', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8114, 811, 'min_storey_span', '3', 'NUM', NULL);

INSERT INTO AD_Val_Rule_Mining_Source VALUES (811, 'Terminal', NULL, NULL, NULL, NULL, NULL, 'mm',
    'Elements at same ROUND(x,1), ROUND(y,1) across >=3 storeys — FP pipes', datetime('now'));

-- ============================================================
-- M16: ARC Opening Face-Anchor Consistency (Tier 1)
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (812, 'MINED_ARC_OPENING_FACE_ANCHOR',
    'Opening centroid depth vs host wall center — face anchor consistency',
    'COMPLIANCE', 'ARC', 'Architectural practice (observed)', 'INTL',
    'MINED:Terminal', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (8121, 812, 'max_depth_offset_mm', '10', 'NUM', NULL);
-- Widened from 5mm → 10mm per Non-Disturbance analysis (G4_SRS §6.1):
-- SH centered openings show 6-7mm offset from wall center.
INSERT INTO AD_Val_Rule_Param VALUES (8122, 812, 'ifc_classes', 'IfcDoor,IfcWindow', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8123, 812, 'check_method', 'CENTROID_DEPTH_VS_HOST_CENTER', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8124, 812, 'verdict', 'WARN', 'TEXT', NULL);
-- Openings may be intentionally flush to INT or EXT face (not centered):
-- ASI face_anchor override = 'INT' or 'EXT' → skip center check
INSERT INTO AD_Val_Rule_Param VALUES (8125, 812, 'asi_override_key', 'face_anchor', 'TEXT', NULL);
-- Skip partition walls — too thin for meaningful center check
INSERT INTO AD_Val_Rule_Param VALUES (8126, 812, 'min_host_wall_thickness_mm', '150', 'NUM', NULL);
-- Use MIN(width, depth) for opening depth axis (same ERP-maths pattern as M12)
INSERT INTO AD_Val_Rule_Param VALUES (8127, 812, 'depth_axis_method', 'MIN_WD', 'TEXT', NULL);

-- ============================================================
-- M17: ARC Opening Host Association (Tier 1)
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (813, 'MINED_ARC_OPENING_HOST',
    'Every IfcDoor/IfcWindow must have a host IfcWall',
    'COMPLIANCE', 'ARC', 'IFC schema (IfcRelVoidsElement)', 'INTL',
    'MINED:Terminal', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (8131, 813, 'ifc_classes', 'IfcDoor,IfcWindow', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8132, 813, 'host_ifc_class', 'IfcWall', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8133, 813, 'check_method', 'AABB_PROXIMITY', 'TEXT', NULL);
-- Changed from HOST_FK_NOT_NULL per Non-Disturbance analysis (G4_SRS §6.2):
-- I_Element_Extraction has no host_id column. Use AABB proximity until R20.
INSERT INTO AD_Val_Rule_Param VALUES (8134, 813, 'proximity_tolerance_mm', '200', 'NUM', NULL);

-- ============================================================
-- M9: Already seeded as rule 703 (CLASH_FPR_ACMV_OBSTRUCTION)
-- M10: Already seeded as rule 701 (CLASH_MEP_STR_HARD)
-- M12: Already seeded as rule 603 (MINED_ELEC_SP_CLEARANCE)
--       + rule 702 (CLASH_PLB_ELC_CLEARANCE)
-- ============================================================

-- ============================================================
-- MINING SOURCE: Terminal M12 under-150mm exceptions
-- 35 pairs under 150mm on lower floors — design intent
-- ============================================================
INSERT INTO AD_Val_Rule_Exception VALUES (3, 'Terminal', 603,
    'Ground Floor ELEC-SP pairs under 150mm', 21,
    'Original engineer', 'Co-routed MEP in Ground Floor ceiling plenum — tight spacing is design intent', datetime('now'));

INSERT INTO AD_Val_Rule_Exception VALUES (4, 'Terminal', 603,
    'Level 1 ELEC-SP pairs under 150mm', 7,
    'Original engineer', 'Branch connections at MEP junction boxes', datetime('now'));

INSERT INTO AD_Val_Rule_Exception VALUES (5, 'Terminal', 603,
    'Level 2 ELEC-SP pairs under 150mm', 7,
    'Original engineer', 'Branch connections at MEP junction boxes', datetime('now'));

-- ============================================================
-- M16 exceptions from Non-Disturbance analysis (G4_SRS §6.1)
-- ============================================================
INSERT INTO AD_Val_Rule_Exception VALUES (6, 'SampleHouse', 812,
    'Exterior door flush-face', 1,
    'Original engineer', 'Exterior door at 75mm offset — flush with EXT face, not centered', datetime('now'));

-- ============================================================
-- TYPICAL SPACING params for grid computation (G4_SRS §7.2)
-- Distinct from max_spacing (limit) — this is the observed dominant pitch
-- ============================================================
INSERT INTO AD_Val_Rule_Param VALUES (6013, 601, 'typical_spacing_mm', '3500', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8034, 803, 'typical_spacing_mm', '3000', 'NUM', NULL);
