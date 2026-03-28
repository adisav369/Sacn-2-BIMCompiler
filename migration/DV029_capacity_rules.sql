-- DV029: Capacity AD_Val_Rule rows — order-level limits
-- Implementing ProjectOrderBlueprint.md §13, UX-N-11..15 — Witness: W-CAP-RULES
--
-- Three GATING rules that cap order complexity:
--   CAP-ROOMS-100:    Max 100 rooms per building order
--   CAP-STOREYS-10:   Max 10 storeys per building order
--   CAP-VARIANTS-50:  Max 50 variants per building
--
-- Uses ad_val_rule + ad_val_rule_param (schema from DV010).
-- check_method = 'CAPACITY_LIMIT' distinguishes these from DIMENSION_RANGE rules.
-- severity = 'BLOCK' (GATING — hard stop, not advisory).
-- APPEND-ONLY: never modify this migration after first run.

-- Rule 1: Max 100 rooms per building order
INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
    description, provenance)
VALUES ('CAP-ROOMS-100', 'IfcSpace', 'CAPACITY_LIMIT', 'BLOCK', 1,
    'Max 100 rooms per building order. GATING — order rejected if exceeded.',
    'ProjectOrderBlueprint');
INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
VALUES ((SELECT ad_val_rule_id FROM ad_val_rule WHERE rule_name = 'CAP-ROOMS-100'), 'max_count', '100');
INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
VALUES ((SELECT ad_val_rule_id FROM ad_val_rule WHERE rule_name = 'CAP-ROOMS-100'), 'scope', 'ORDER');

-- Rule 2: Max 10 storeys per building order
INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
    description, provenance)
VALUES ('CAP-STOREYS-10', 'IfcBuildingStorey', 'CAPACITY_LIMIT', 'BLOCK', 1,
    'Max 10 storeys per building order. GATING — order rejected if exceeded.',
    'ProjectOrderBlueprint');
INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
VALUES ((SELECT ad_val_rule_id FROM ad_val_rule WHERE rule_name = 'CAP-STOREYS-10'), 'max_count', '10');
INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
VALUES ((SELECT ad_val_rule_id FROM ad_val_rule WHERE rule_name = 'CAP-STOREYS-10'), 'scope', 'ORDER');

-- Rule 3: Max 50 variants per building
INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
    description, provenance)
VALUES ('CAP-VARIANTS-50', 'IfcBuilding', 'CAPACITY_LIMIT', 'BLOCK', 1,
    'Max 50 variants per building. GATING — order rejected if exceeded.',
    'ProjectOrderBlueprint');
INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
VALUES ((SELECT ad_val_rule_id FROM ad_val_rule WHERE rule_name = 'CAP-VARIANTS-50'), 'max_count', '50');
INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
VALUES ((SELECT ad_val_rule_id FROM ad_val_rule WHERE rule_name = 'CAP-VARIANTS-50'), 'scope', 'BUILDING');

-- Update schema version
INSERT OR REPLACE INTO AD_SysConfig (Value, Name, Description, updated)
VALUES ('CAPACITY_RULES_VERSION', 'DV029', 'Capacity limit rules for order complexity', datetime('now'));
