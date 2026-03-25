-- V015: Tier 1 — Add Name/Value (SearchKey) columns to validation.db
-- Reference: docs/ID_NAME_VALUE_STUDY.md §3
-- Convention: iDempiere _ID/Name/Value triad. No PK changes, no FK changes.
-- Skips: AD_Val_Rule_Param (already conformant)

-- AD_Clash_Rule
ALTER TABLE AD_Clash_Rule ADD COLUMN Name TEXT;
ALTER TABLE AD_Clash_Rule ADD COLUMN Value TEXT;
UPDATE AD_Clash_Rule SET Name = discipline_a || '_vs_' || discipline_b || '_' || clash_type WHERE Name IS NULL;

-- AD_Occupancy_Class — already has name, add Value
ALTER TABLE AD_Occupancy_Class ADD COLUMN Value TEXT;
UPDATE AD_Occupancy_Class SET Value = code WHERE Value IS NULL;

-- AD_Val_Rule — already has name, add Value
ALTER TABLE AD_Val_Rule ADD COLUMN Value TEXT;
UPDATE AD_Val_Rule SET Value = name WHERE Value IS NULL;

-- AD_Val_Rule_Exception
ALTER TABLE AD_Val_Rule_Exception ADD COLUMN Name TEXT;
ALTER TABLE AD_Val_Rule_Exception ADD COLUMN Value TEXT;
UPDATE AD_Val_Rule_Exception SET Name = building_id || '_rule_' || ad_val_rule_id WHERE Name IS NULL;

-- AD_Validation_Result
ALTER TABLE AD_Validation_Result ADD COLUMN Name TEXT;
ALTER TABLE AD_Validation_Result ADD COLUMN Value TEXT;
UPDATE AD_Validation_Result SET Name = result WHERE Name IS NULL;

-- ad_pattern_rule — has rule_name
ALTER TABLE ad_pattern_rule ADD COLUMN Name TEXT;
ALTER TABLE ad_pattern_rule ADD COLUMN Value TEXT;
UPDATE ad_pattern_rule SET Name = rule_name WHERE Name IS NULL;
