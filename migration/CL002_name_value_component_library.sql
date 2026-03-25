-- CL002: Tier 1 — Add Name/Value (SearchKey) columns to component_library.db
-- Reference: docs/ID_NAME_VALUE_STUDY.md §3
-- Convention: iDempiere _ID/Name/Value triad. No PK changes, no FK changes.
-- Existing semantic columns kept for backward compat.

-- I_Geometry_Map
ALTER TABLE I_Geometry_Map ADD COLUMN Name TEXT;
ALTER TABLE I_Geometry_Map ADD COLUMN Value TEXT;
UPDATE I_Geometry_Map SET Name = element_ref WHERE Name IS NULL;
UPDATE I_Geometry_Map SET Value = element_ref || ':' || geometry_hash WHERE Value IS NULL;

-- ad_beam_type_rule
ALTER TABLE ad_beam_type_rule ADD COLUMN Name TEXT;
ALTER TABLE ad_beam_type_rule ADD COLUMN Value TEXT;
UPDATE ad_beam_type_rule SET Name = context || '_' || beam_type_id WHERE Name IS NULL;

-- ad_building — already has name, add Value
ALTER TABLE ad_building ADD COLUMN Value TEXT;
UPDATE ad_building SET Value = building_type WHERE Value IS NULL;

-- ad_building_bom
ALTER TABLE ad_building_bom ADD COLUMN Name TEXT;
ALTER TABLE ad_building_bom ADD COLUMN Value TEXT;
UPDATE ad_building_bom SET Name = template_id || '_' || floor_type_id || '_' || position WHERE Name IS NULL;
UPDATE ad_building_bom SET Value = template_id || ':' || floor_type_id || ':' || seq_no WHERE Value IS NULL;

-- ad_building_grid
ALTER TABLE ad_building_grid ADD COLUMN Name TEXT;
ALTER TABLE ad_building_grid ADD COLUMN Value TEXT;
UPDATE ad_building_grid SET Name = grid_label WHERE Name IS NULL;
UPDATE ad_building_grid SET Value = building_type || ':' || axis || ':' || grid_label WHERE Value IS NULL;

-- ad_column_type_rule
ALTER TABLE ad_column_type_rule ADD COLUMN Name TEXT;
ALTER TABLE ad_column_type_rule ADD COLUMN Value TEXT;
UPDATE ad_column_type_rule SET Name = context || '_' || column_type_id WHERE Name IS NULL;

-- ad_compiler_config
ALTER TABLE ad_compiler_config ADD COLUMN Name TEXT;
ALTER TABLE ad_compiler_config ADD COLUMN Value TEXT;
UPDATE ad_compiler_config SET Name = config_key WHERE Name IS NULL;
UPDATE ad_compiler_config SET Value = config_value WHERE Value IS NULL;

-- ad_element_dependency
ALTER TABLE ad_element_dependency ADD COLUMN Name TEXT;
ALTER TABLE ad_element_dependency ADD COLUMN Value TEXT;
UPDATE ad_element_dependency SET Name = element_ref WHERE Name IS NULL;
UPDATE ad_element_dependency SET Value = element_ref || ':' || parent_ref WHERE Value IS NULL;

-- ad_element_placement
ALTER TABLE ad_element_placement ADD COLUMN Name TEXT;
ALTER TABLE ad_element_placement ADD COLUMN Value TEXT;
UPDATE ad_element_placement SET Name = element_ref WHERE Name IS NULL;
UPDATE ad_element_placement SET Value = building_type || ':' || storey || ':' || element_ref WHERE Value IS NULL;

-- ad_element_rule
ALTER TABLE ad_element_rule ADD COLUMN Name TEXT;
ALTER TABLE ad_element_rule ADD COLUMN Value TEXT;
UPDATE ad_element_rule SET Name = element_ref WHERE Name IS NULL;
UPDATE ad_element_rule SET Value = building_type || ':' || storey || ':' || element_ref WHERE Value IS NULL;

-- ad_floor_type_rule
ALTER TABLE ad_floor_type_rule ADD COLUMN Name TEXT;
ALTER TABLE ad_floor_type_rule ADD COLUMN Value TEXT;
UPDATE ad_floor_type_rule SET Name = room_type || '_' || floor_type_id WHERE Name IS NULL;

-- ad_room_boundary
ALTER TABLE ad_room_boundary ADD COLUMN Name TEXT;
ALTER TABLE ad_room_boundary ADD COLUMN Value TEXT;
UPDATE ad_room_boundary SET Name = room_name WHERE Name IS NULL;
UPDATE ad_room_boundary SET Value = building_type || ':' || storey || ':' || room_name WHERE Value IS NULL;

-- ad_room_slot
ALTER TABLE ad_room_slot ADD COLUMN Name TEXT;
ALTER TABLE ad_room_slot ADD COLUMN Value TEXT;
UPDATE ad_room_slot SET Name = slot_name WHERE Name IS NULL;
UPDATE ad_room_slot SET Value = room_type || ':' || slot_name WHERE Value IS NULL;

-- ad_wall_face
ALTER TABLE ad_wall_face ADD COLUMN Name TEXT;
ALTER TABLE ad_wall_face ADD COLUMN Value TEXT;
UPDATE ad_wall_face SET Name = room_name || '_' || face WHERE Name IS NULL;
UPDATE ad_wall_face SET Value = building_type || ':' || storey || ':' || room_name || ':' || face WHERE Value IS NULL;

-- ad_wall_type_rule
ALTER TABLE ad_wall_type_rule ADD COLUMN Name TEXT;
ALTER TABLE ad_wall_type_rule ADD COLUMN Value TEXT;
UPDATE ad_wall_type_rule SET Name = context || '_' || wall_type_id WHERE Name IS NULL;

-- component_definitions — already has name, add Value
ALTER TABLE component_definitions ADD COLUMN Value TEXT;
UPDATE component_definitions SET Value = name || ':' || geometry_hash WHERE Value IS NULL;

-- component_types
ALTER TABLE component_types ADD COLUMN Name TEXT;
ALTER TABLE component_types ADD COLUMN Value TEXT;
UPDATE component_types SET Name = ifc_class || '_' || category WHERE Name IS NULL;
UPDATE component_types SET Value = ifc_class || ':' || discipline || ':' || category WHERE Value IS NULL;

-- placement_rules
ALTER TABLE placement_rules ADD COLUMN Name TEXT;
ALTER TABLE placement_rules ADD COLUMN Value TEXT;
UPDATE placement_rules SET Name = host_type WHERE Name IS NULL;

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
