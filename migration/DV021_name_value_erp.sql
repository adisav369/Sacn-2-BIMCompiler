-- DV021: Tier 1 — Add Name/Value (SearchKey) columns to ERP.db INTEGER PK tables
-- Reference: docs/ID_NAME_VALUE_STUDY.md §3
-- Convention: iDempiere _ID/Name/Value triad. No PK changes, no FK changes.
-- Existing semantic columns kept for backward compat.

-- W_Calibration_Result
ALTER TABLE W_Calibration_Result ADD COLUMN Name TEXT;
ALTER TABLE W_Calibration_Result ADD COLUMN Value TEXT;
UPDATE W_Calibration_Result SET Name = discipline || '_' || storey WHERE Name IS NULL;

-- W_Validation_Advisory
ALTER TABLE W_Validation_Advisory ADD COLUMN Name TEXT;
ALTER TABLE W_Validation_Advisory ADD COLUMN Value TEXT;
UPDATE W_Validation_Advisory SET Name = rule_name WHERE Name IS NULL;

-- ad_assembly_connector
ALTER TABLE ad_assembly_connector ADD COLUMN Name TEXT;
ALTER TABLE ad_assembly_connector ADD COLUMN Value TEXT;
UPDATE ad_assembly_connector SET Name = assembly_id || '_' || face WHERE Name IS NULL;
UPDATE ad_assembly_connector SET Value = assembly_id || ':' || connector_type || ':' || face WHERE Value IS NULL;

-- ad_assembly_manifest
ALTER TABLE ad_assembly_manifest ADD COLUMN Name TEXT;
ALTER TABLE ad_assembly_manifest ADD COLUMN Value TEXT;
UPDATE ad_assembly_manifest SET Name = assembly_id || '_' || face WHERE Name IS NULL;
UPDATE ad_assembly_manifest SET Value = assembly_id || ':' || interface_type || ':' || face WHERE Value IS NULL;

-- ad_building_profile
ALTER TABLE ad_building_profile ADD COLUMN Name TEXT;
ALTER TABLE ad_building_profile ADD COLUMN Value TEXT;
UPDATE ad_building_profile SET Name = building_type || '_' || ifc_class WHERE Name IS NULL;
UPDATE ad_building_profile SET Value = building_type WHERE Value IS NULL;

-- ad_element_mep_alias
ALTER TABLE ad_element_mep_alias ADD COLUMN Name TEXT;
ALTER TABLE ad_element_mep_alias ADD COLUMN Value TEXT;
UPDATE ad_element_mep_alias SET Name = match_value WHERE Name IS NULL;
UPDATE ad_element_mep_alias SET Value = canonical_type || ':' || match_field || ':' || match_value WHERE Value IS NULL;

-- ad_room_slot
ALTER TABLE ad_room_slot ADD COLUMN Name TEXT;
ALTER TABLE ad_room_slot ADD COLUMN Value TEXT;
UPDATE ad_room_slot SET Name = slot_name WHERE Name IS NULL;
UPDATE ad_room_slot SET Value = room_type || ':' || slot_name WHERE Value IS NULL;

-- ad_val_rule (ERP copy — has rule_name, not Name)
ALTER TABLE ad_val_rule ADD COLUMN Name TEXT;
ALTER TABLE ad_val_rule ADD COLUMN Value TEXT;
UPDATE ad_val_rule SET Name = rule_name WHERE Name IS NULL;

-- ad_wall_face
ALTER TABLE ad_wall_face ADD COLUMN Name TEXT;
ALTER TABLE ad_wall_face ADD COLUMN Value TEXT;
UPDATE ad_wall_face SET Name = room_name || '_' || face WHERE Name IS NULL;
UPDATE ad_wall_face SET Value = building_type || ':' || storey || ':' || room_name || ':' || face WHERE Value IS NULL;

-- placement_rules
ALTER TABLE placement_rules ADD COLUMN Name TEXT;
ALTER TABLE placement_rules ADD COLUMN Value TEXT;
UPDATE placement_rules SET Name = host_type WHERE Name IS NULL;
