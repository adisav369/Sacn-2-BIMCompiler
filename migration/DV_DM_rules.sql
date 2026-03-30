-- ════════════════════════════════════════════════════════
-- DM: Demo House 2BR (DemoHouse_2BR)
-- Source: DAGCompiler/lib/output/demohouse_2br.db
-- Generated: 2026-03-30 23:14
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class             storey        cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- --------------------  ------------  ---  --------  --------  --------  --------  --------
-- IfcWall               Ground Floor  20   2145.0    1695.0    2800.0    290.0     5000.0  
-- IfcFlowTerminal       Ground Floor  17   251.0     178.0     259.0     80.0      1525.0  
-- IfcFurnishingElement  Ground Floor  7    1656.0    1148.0    536.0     1200.0    2290.0  
-- IfcDoor               Ground Floor  5    810.0     200.0     2110.0    810.0     810.0   
-- IfcSlab               Ground Floor  5    4000.0    3100.0    150.0     2000.0    5000.0  
-- IfcWindow             Ground Floor  5    1810.0    200.0     1210.0    1810.0    1810.0  

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class             discipline  cnt
-- --------------------  ----------  ---
-- IfcWall               STR         20 
-- IfcFlowTerminal       MEP         17 
-- IfcFurnishingElement  ARC         7  
-- IfcDoor               ARC         5  
-- IfcSlab               STR         5  
-- IfcWindow             ARC         5  
-- IfcRoof               ARC         1  

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcWall_Ground_Floor (20 instances, avg 2145.0x1695.0x2800.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Ground_Floor', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Ground Floor: 20 instances, avg W=2145.0 D=1695.0 H=2800.0mm',
--     'DemoHouse_2BR');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2145.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1695.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2800.0');

-- Rule: IfcFlowTerminal_Ground_Floor (17 instances, avg 251.0x178.0x259.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowTerminal_Ground_Floor', 'IfcFlowTerminal', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowTerminal on Ground Floor: 17 instances, avg W=251.0 D=178.0 H=259.0mm',
--     'DemoHouse_2BR');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '251.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '178.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '259.0');

-- Rule: IfcFurnishingElement_Ground_Floor (7 instances, avg 1656.0x1148.0x536.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFurnishingElement_Ground_Floor', 'IfcFurnishingElement', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFurnishingElement on Ground Floor: 7 instances, avg W=1656.0 D=1148.0 H=536.0mm',
--     'DemoHouse_2BR');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1656.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1148.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '536.0');

-- Rule: IfcDoor_Ground_Floor (5 instances, avg 810.0x200.0x2110.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Ground_Floor', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Ground Floor: 5 instances, avg W=810.0 D=200.0 H=2110.0mm',
--     'DemoHouse_2BR');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '810.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '200.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2110.0');

-- Rule: IfcSlab_Ground_Floor (5 instances, avg 4000.0x3100.0x150.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcSlab_Ground_Floor', 'IfcSlab', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcSlab on Ground Floor: 5 instances, avg W=4000.0 D=3100.0 H=150.0mm',
--     'DemoHouse_2BR');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '4000.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3100.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '150.0');

-- Rule: IfcWindow_Ground_Floor (5 instances, avg 1810.0x200.0x1210.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Ground_Floor', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Ground Floor: 5 instances, avg W=1810.0 D=200.0 H=1210.0mm',
--     'DemoHouse_2BR');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1810.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '200.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1210.0');


