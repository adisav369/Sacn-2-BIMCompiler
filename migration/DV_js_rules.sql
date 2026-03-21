-- ════════════════════════════════════════════════════════
-- JS: AC90 Jasmin Sun (AC90_Jasmin)
-- Source: DAGCompiler/lib/output/ac90_jasmin_enbloc.db
-- Generated: 2026-03-21 09:01
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class             storey        cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- --------------------  ------------  ---  --------  --------  --------  --------  --------
-- IfcFurnishingElement  Erdgeschoss   10   1018.0    805.0     776.0     407.0     1992.0  
-- IfcWall               Erdgeschoss   7    3776.0    2750.0    2750.0    115.0     8010.0  
-- IfcWall               Dachgeschoss  6    4024.0    3189.0    3270.0    115.0     8010.0  
-- IfcWall               Keller        6    4045.0    3210.0    2750.0    240.0     8010.0  
-- IfcWindow             Erdgeschoss   6    804.0     504.0     1584.0    75.0      1800.0  
-- IfcDoor               Erdgeschoss   4    678.0     493.0     2118.0    100.0     1510.0  
-- IfcSlab               Dachgeschoss  3    5673.0    8677.0    2614.0    4505.0    8010.0  
-- IfcWindow             Dachgeschoss  3    1133.0    75.0      1400.0    900.0     1600.0  
-- IfcWindow             Keller        3    75.0      900.0     500.0     75.0      75.0    
-- IfcDoor               Dachgeschoss  2    550.0     550.0     2100.0    100.0     1000.0  
-- IfcDoor               Keller        2    550.0     550.0     2100.0    100.0     1000.0  
-- IfcFlowTerminal       Erdgeschoss   2    500.0     325.0     271.0     400.0     600.0   

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class             discipline  cnt
-- --------------------  ----------  ---
-- IfcWall               ARC         19 
-- IfcWindow             ARC         12 
-- IfcFurnishingElement  ARC         11 
-- IfcDoor               ARC         8  
-- IfcSlab               STR         5  
-- IfcFlowTerminal       MEP         3  
-- IfcStair              ARC         2  
-- IfcRailing            STR         1  

-- §5: Candidate validation rules for disc_validation.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcFurnishingElement_Erdgeschoss (10 instances, avg 1018.0x805.0x776.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFurnishingElement_Erdgeschoss', 'IfcFurnishingElement', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFurnishingElement on Erdgeschoss: 10 instances, avg W=1018.0 D=805.0 H=776.0mm',
--     'AC90_Jasmin');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1018.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '805.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '776.0');

-- Rule: IfcWall_Erdgeschoss (7 instances, avg 3776.0x2750.0x2750.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Erdgeschoss', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Erdgeschoss: 7 instances, avg W=3776.0 D=2750.0 H=2750.0mm',
--     'AC90_Jasmin');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '3776.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '2750.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2750.0');

-- Rule: IfcWall_Dachgeschoss (6 instances, avg 4024.0x3189.0x3270.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Dachgeschoss', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Dachgeschoss: 6 instances, avg W=4024.0 D=3189.0 H=3270.0mm',
--     'AC90_Jasmin');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '4024.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3189.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '3270.0');

-- Rule: IfcWall_Keller (6 instances, avg 4045.0x3210.0x2750.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Keller', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Keller: 6 instances, avg W=4045.0 D=3210.0 H=2750.0mm',
--     'AC90_Jasmin');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '4045.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3210.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2750.0');

-- Rule: IfcWindow_Erdgeschoss (6 instances, avg 804.0x504.0x1584.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Erdgeschoss', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Erdgeschoss: 6 instances, avg W=804.0 D=504.0 H=1584.0mm',
--     'AC90_Jasmin');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '804.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '504.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1584.0');

-- Rule: IfcDoor_Erdgeschoss (4 instances, avg 678.0x493.0x2118.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Erdgeschoss', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Erdgeschoss: 4 instances, avg W=678.0 D=493.0 H=2118.0mm',
--     'AC90_Jasmin');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '678.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '493.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2118.0');

-- Rule: IfcSlab_Dachgeschoss (3 instances, avg 5673.0x8677.0x2614.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcSlab_Dachgeschoss', 'IfcSlab', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcSlab on Dachgeschoss: 3 instances, avg W=5673.0 D=8677.0 H=2614.0mm',
--     'AC90_Jasmin');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '5673.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '8677.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2614.0');

-- Rule: IfcWindow_Dachgeschoss (3 instances, avg 1133.0x75.0x1400.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Dachgeschoss', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Dachgeschoss: 3 instances, avg W=1133.0 D=75.0 H=1400.0mm',
--     'AC90_Jasmin');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1133.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '75.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1400.0');

-- Rule: IfcWindow_Keller (3 instances, avg 75.0x900.0x500.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Keller', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Keller: 3 instances, avg W=75.0 D=900.0 H=500.0mm',
--     'AC90_Jasmin');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '75.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '900.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '500.0');


