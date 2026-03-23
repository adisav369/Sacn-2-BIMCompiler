-- DV014_ad_org_columns.sql
-- Adds AD_Org_ID columns alongside existing TEXT discipline columns.
-- Dual-column phase: both TEXT and FK coexist until Step 5 switches Java code.
-- APPEND-ONLY: never modify this migration after first run
--
-- Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 2 — Witness: W-DV-DB-ORG-COLS

-- ── ad_element_mep: discipline TEXT → AD_Org_ID INTEGER ─────────────
ALTER TABLE ad_element_mep ADD COLUMN AD_Org_ID INTEGER;

UPDATE ad_element_mep SET AD_Org_ID = (
    SELECT AD_Org_ID FROM AD_Org WHERE AD_Org.Value = ad_element_mep.discipline
);
-- HVAC→ACMV: ad_element_mep uses 'HVAC', AD_Org uses 'ACMV' (canonical trade code)
UPDATE ad_element_mep SET AD_Org_ID = 5 WHERE discipline = 'HVAC' AND AD_Org_ID IS NULL;

-- ── ad_ifc_class_map: discipline TEXT → AD_Org_ID INTEGER ───────────
ALTER TABLE ad_ifc_class_map ADD COLUMN AD_Org_ID INTEGER;

UPDATE ad_ifc_class_map SET AD_Org_ID = (
    SELECT AD_Org_ID FROM AD_Org WHERE AD_Org.Value = ad_ifc_class_map.discipline
);

-- Update AD_SysConfig version
INSERT OR REPLACE INTO AD_SysConfig (Name, Value, Description)
    VALUES ('AD_ORG_COLS_VERSION', 'DV014', 'AD_Org_ID columns added to ad_element_mep, ad_ifc_class_map');
