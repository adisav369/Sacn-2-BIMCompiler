-- ════════════════════════════════════════════════════════
-- FK: FZK-Haus (Ifc4_FZKHaus)
-- Source: DAGCompiler/lib/output/ifc4_fzkhaus.db
-- Generated: 2026-03-28 16:19
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class   storey        cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- ----------  ------------  ---  --------  --------  --------  --------  --------
-- IfcMember   Dachgeschoss  42   80.0      5500.0    3360.0    80.0      80.0    
-- IfcWall     Erdgeschoss   9    4442.0    3413.0    2589.0    240.0     12000.0 
-- IfcWindow   Erdgeschoss   9    1144.0    931.0     1200.0    75.0      2000.0  
-- IfcDoor     Erdgeschoss   5    814.0     460.0     2083.0    100.0     2010.0  
-- IfcWall     Dachgeschoss  4    6150.0    5150.0    2030.0    300.0     12000.0 
-- IfcBeam     Dachgeschoss  3    13000.0   80.0      160.0     13000.0   13000.0 
-- IfcSlab     Dachgeschoss  3    12600.0   6933.0    2338.0    11800.0   13000.0 
-- IfcRailing  Dachgeschoss  2    2185.0    1040.0    1000.0    80.0      4290.0  
-- IfcWindow   Dachgeschoss  2    90.0      1000.0    1000.0    90.0      90.0    

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class   discipline  cnt
-- ----------  ----------  ---
-- IfcMember   STR         42 
-- IfcWall     STR         13 
-- IfcWindow   ARC         11 
-- IfcDoor     ARC         5  
-- IfcBeam     STR         4  
-- IfcSlab     STR         4  
-- IfcRailing  ARC         2  
-- IfcStair    ARC         1  

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcMember_Dachgeschoss (42 instances, avg 80.0x5500.0x3360.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcMember_Dachgeschoss', 'IfcMember', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcMember on Dachgeschoss: 42 instances, avg W=80.0 D=5500.0 H=3360.0mm',
--     'Ifc4_FZKHaus');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '80.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '5500.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '3360.0');

-- Rule: IfcWall_Erdgeschoss (9 instances, avg 4442.0x3413.0x2589.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Erdgeschoss', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Erdgeschoss: 9 instances, avg W=4442.0 D=3413.0 H=2589.0mm',
--     'Ifc4_FZKHaus');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '4442.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3413.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2589.0');

-- Rule: IfcWindow_Erdgeschoss (9 instances, avg 1144.0x931.0x1200.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Erdgeschoss', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Erdgeschoss: 9 instances, avg W=1144.0 D=931.0 H=1200.0mm',
--     'Ifc4_FZKHaus');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1144.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '931.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1200.0');

-- Rule: IfcDoor_Erdgeschoss (5 instances, avg 814.0x460.0x2083.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Erdgeschoss', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Erdgeschoss: 5 instances, avg W=814.0 D=460.0 H=2083.0mm',
--     'Ifc4_FZKHaus');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '814.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '460.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2083.0');

-- Rule: IfcWall_Dachgeschoss (4 instances, avg 6150.0x5150.0x2030.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Dachgeschoss', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Dachgeschoss: 4 instances, avg W=6150.0 D=5150.0 H=2030.0mm',
--     'Ifc4_FZKHaus');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '6150.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '5150.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2030.0');

-- Rule: IfcBeam_Dachgeschoss (3 instances, avg 13000.0x80.0x160.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcBeam_Dachgeschoss', 'IfcBeam', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcBeam on Dachgeschoss: 3 instances, avg W=13000.0 D=80.0 H=160.0mm',
--     'Ifc4_FZKHaus');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '13000.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '80.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '160.0');

-- Rule: IfcSlab_Dachgeschoss (3 instances, avg 12600.0x6933.0x2338.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcSlab_Dachgeschoss', 'IfcSlab', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcSlab on Dachgeschoss: 3 instances, avg W=12600.0 D=6933.0 H=2338.0mm',
--     'Ifc4_FZKHaus');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '12600.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '6933.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2338.0');


