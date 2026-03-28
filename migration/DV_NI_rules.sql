-- ════════════════════════════════════════════════════════
-- NI: AC90 Niedriha (AC90_Niedriha)
-- Source: DAGCompiler/lib/output/ac90_niedriha.db
-- Generated: 2026-03-28 14:51
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class  storey        cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- ---------  ------------  ---  --------  --------  --------  --------  --------
-- IfcWall    Erdgeschoss   16   2421.0    2296.0    2700.0    160.0     9395.0  
-- IfcWall    Obergeschoss  16   2333.0    2570.0    2478.0    160.0     9395.0  
-- IfcBeam    Erdgeschoss   13   808.0     2630.0    115.0     100.0     4700.0  
-- IfcDoor    Erdgeschoss   9    737.0     610.0     2093.0    100.0     1750.0  
-- IfcWall    Keller        9    2927.0    3077.0    2700.0    160.0     9395.0  
-- IfcWindow  Erdgeschoss   8    425.0     989.0     1360.0    70.0      1250.0  
-- IfcWindow  Obergeschoss  8    776.0     575.0     1254.0    70.0      2000.0  
-- IfcColumn  Erdgeschoss   4    100.0     100.0     2400.0    100.0     100.0   
-- IfcDoor    Obergeschoss  4    723.0     723.0     2010.0    686.0     761.0   
-- IfcWall    Dachgeschoss  4    4823.0    4165.0    1070.0    250.0     9395.0  
-- IfcDoor    Keller        3    496.0     683.0     2010.0    309.0     870.0   
-- IfcSlab    Dachgeschoss  3    10060.0   5720.0    1498.0    9395.0    10393.0 
-- IfcSlab    Obergeschoss  3    5368.0    5087.0    1203.0    2740.0    9395.0  

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class  discipline  cnt
-- ---------  ----------  ---
-- IfcWall    STR         45 
-- IfcDoor    ARC         16 
-- IfcWindow  ARC         16 
-- IfcBeam    STR         13 
-- IfcSlab    STR         8  
-- IfcColumn  STR         4  
-- IfcStair   ARC         2  

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcWall_Erdgeschoss (16 instances, avg 2421.0x2296.0x2700.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Erdgeschoss', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Erdgeschoss: 16 instances, avg W=2421.0 D=2296.0 H=2700.0mm',
--     'AC90_Niedriha');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2421.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '2296.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2700.0');

-- Rule: IfcWall_Obergeschoss (16 instances, avg 2333.0x2570.0x2478.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Obergeschoss', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Obergeschoss: 16 instances, avg W=2333.0 D=2570.0 H=2478.0mm',
--     'AC90_Niedriha');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2333.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '2570.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2478.0');

-- Rule: IfcBeam_Erdgeschoss (13 instances, avg 808.0x2630.0x115.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcBeam_Erdgeschoss', 'IfcBeam', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcBeam on Erdgeschoss: 13 instances, avg W=808.0 D=2630.0 H=115.0mm',
--     'AC90_Niedriha');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '808.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '2630.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '115.0');

-- Rule: IfcDoor_Erdgeschoss (9 instances, avg 737.0x610.0x2093.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Erdgeschoss', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Erdgeschoss: 9 instances, avg W=737.0 D=610.0 H=2093.0mm',
--     'AC90_Niedriha');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '737.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '610.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2093.0');

-- Rule: IfcWall_Keller (9 instances, avg 2927.0x3077.0x2700.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Keller', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Keller: 9 instances, avg W=2927.0 D=3077.0 H=2700.0mm',
--     'AC90_Niedriha');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2927.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3077.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2700.0');

-- Rule: IfcWindow_Erdgeschoss (8 instances, avg 425.0x989.0x1360.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Erdgeschoss', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Erdgeschoss: 8 instances, avg W=425.0 D=989.0 H=1360.0mm',
--     'AC90_Niedriha');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '425.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '989.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1360.0');

-- Rule: IfcWindow_Obergeschoss (8 instances, avg 776.0x575.0x1254.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Obergeschoss', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Obergeschoss: 8 instances, avg W=776.0 D=575.0 H=1254.0mm',
--     'AC90_Niedriha');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '776.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '575.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1254.0');

-- Rule: IfcColumn_Erdgeschoss (4 instances, avg 100.0x100.0x2400.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcColumn_Erdgeschoss', 'IfcColumn', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcColumn on Erdgeschoss: 4 instances, avg W=100.0 D=100.0 H=2400.0mm',
--     'AC90_Niedriha');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '100.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '100.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2400.0');

-- Rule: IfcDoor_Obergeschoss (4 instances, avg 723.0x723.0x2010.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Obergeschoss', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Obergeschoss: 4 instances, avg W=723.0 D=723.0 H=2010.0mm',
--     'AC90_Niedriha');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '723.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '723.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2010.0');

-- Rule: IfcWall_Dachgeschoss (4 instances, avg 4823.0x4165.0x1070.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Dachgeschoss', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Dachgeschoss: 4 instances, avg W=4823.0 D=4165.0 H=1070.0mm',
--     'AC90_Niedriha');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '4823.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '4165.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1070.0');

-- Rule: IfcDoor_Keller (3 instances, avg 496.0x683.0x2010.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Keller', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Keller: 3 instances, avg W=496.0 D=683.0 H=2010.0mm',
--     'AC90_Niedriha');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '496.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '683.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2010.0');

-- Rule: IfcSlab_Dachgeschoss (3 instances, avg 10060.0x5720.0x1498.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcSlab_Dachgeschoss', 'IfcSlab', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcSlab on Dachgeschoss: 3 instances, avg W=10060.0 D=5720.0 H=1498.0mm',
--     'AC90_Niedriha');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '10060.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '5720.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1498.0');

-- Rule: IfcSlab_Obergeschoss (3 instances, avg 5368.0x5087.0x1203.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcSlab_Obergeschoss', 'IfcSlab', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcSlab on Obergeschoss: 3 instances, avg W=5368.0 D=5087.0 H=1203.0mm',
--     'AC90_Niedriha');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '5368.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '5087.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1203.0');


