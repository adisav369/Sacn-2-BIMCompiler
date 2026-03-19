-- DV004_light_per_area.sql
-- Fix 2: Set per_area_normal=0.05 for LIGHT where currently 0
-- Terminal observed: ~814 lights across ~16,000 m² = 0.05 lights/m²
-- APPEND-ONLY: never modify this migration after first run
-- Implementing CALIBRATION_SRS.md §7.2 — Witness: W-CAL-ELEC-DENSITY

UPDATE ad_space_type_mep_bom
   SET per_area_normal = 0.05
 WHERE mep_product_id = 'LIGHT'
   AND per_area_normal = 0;

-- Version bump
UPDATE AD_SysConfig SET value = 'DV004' WHERE name = 'SCHEMA_VERSION';
