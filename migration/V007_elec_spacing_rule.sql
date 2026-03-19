-- V007_elec_spacing_rule.sql
-- Fix 1: ELEC light spacing rule for CalibrationTest §4.2
-- Mined from TE: M4 avg NN ~3,964mm, ceiling grid aligned
-- APPEND-ONLY: never modify this migration after first run
-- Implementing CALIBRATION_SRS.md §7.1 — Witness: W-CAL-RULES-EXIST

INSERT INTO AD_Val_Rule VALUES (803, 'MINED_ELEC_LIGHT_SPACING',
    'Light fixture nearest-neighbor spacing (mined from TE ceiling grids)',
    'COMPLIANCE', 'ELC', 'IES LPD / AS1680 grid spacing (observed)', 'INTL',
    'MINED:SJTII_Terminal', NULL, NULL, 1, 0);

INSERT INTO AD_Val_Rule_Param VALUES (8031, 803, 'typical_spacing_mm', '3000', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8032, 803, 'max_spacing_mm', '5000', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8033, 803, 'ifc_class', 'IfcLightFixture', 'TEXT', NULL);

INSERT INTO AD_Val_Rule_Occupancy VALUES (803, 1);  -- Light Hazard
INSERT INTO AD_Val_Rule_Occupancy VALUES (803, 5);  -- Commercial

INSERT INTO AD_Val_Rule_Mining_Source VALUES (803, 'SJTII_Terminal', 814, 1295, 5655, 3964, 3108, 'mm',
    'NN centroid distance between IfcLightFixture on same storey, ceiling grid modules', datetime('now'));
