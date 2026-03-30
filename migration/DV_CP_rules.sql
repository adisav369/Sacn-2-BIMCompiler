-- ════════════════════════════════════════════════════════
-- CP: Clinic Plumbing (Clinic_Plumbing)
-- Source: DAGCompiler/lib/output/clinic_plumbing.db
-- Generated: 2026-03-31 00:21
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class          storey       cnt   avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- -----------------  -----------  ----  --------  --------  --------  --------  --------
-- IfcFlowSegment     Level 1      1880  501.0     454.0     263.0     4.0       14501.0 
-- IfcFlowFitting     Level 1      1647  48.0      47.0      47.0      3.0       300.0   
-- IfcFlowFitting     TOF Footing  1361  57.0      55.0      53.0      6.0       382.0   
-- IfcFlowSegment     Level 2      1012  565.0     414.0     265.0     5.0       21851.0 
-- IfcFlowFitting     Level 2      310   60.0      61.0      80.0      4.0       300.0   
-- IfcFlowController  TOF Footing  114   137.0     161.0     141.0     80.0      293.0   
-- IfcFlowController  Level 1      111   154.0     218.0     187.0     80.0      406.0   
-- IfcFlowTerminal    Level 1      74    526.0     512.0     329.0     310.0     915.0   
-- IfcFlowTerminal    Level 2      45    533.0     511.0     409.0     310.0     915.0   
-- IfcFlowController  Level 2      29    196.0     301.0     322.0     146.0     406.0   

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class          discipline  cnt 
-- -----------------  ----------  ----
-- IfcFlowFitting     MEP         3317
-- IfcFlowSegment     ARC         1931
-- IfcFlowSegment     MEP         962 
-- IfcFlowController  MEP         254 
-- IfcFlowTerminal    MEP         119 
-- IfcFlowFitting     ARC         1   

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcFlowSegment_Level_1 (1880 instances, avg 501.0x454.0x263.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowSegment_Level_1', 'IfcFlowSegment', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowSegment on Level 1: 1880 instances, avg W=501.0 D=454.0 H=263.0mm',
--     'Clinic_Plumbing');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '501.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '454.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '263.0');

-- Rule: IfcFlowFitting_Level_1 (1647 instances, avg 48.0x47.0x47.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowFitting_Level_1', 'IfcFlowFitting', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowFitting on Level 1: 1647 instances, avg W=48.0 D=47.0 H=47.0mm',
--     'Clinic_Plumbing');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '48.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '47.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '47.0');

-- Rule: IfcFlowFitting_TOF_Footing (1361 instances, avg 57.0x55.0x53.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowFitting_TOF_Footing', 'IfcFlowFitting', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowFitting on TOF Footing: 1361 instances, avg W=57.0 D=55.0 H=53.0mm',
--     'Clinic_Plumbing');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '57.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '55.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '53.0');

-- Rule: IfcFlowSegment_Level_2 (1012 instances, avg 565.0x414.0x265.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowSegment_Level_2', 'IfcFlowSegment', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowSegment on Level 2: 1012 instances, avg W=565.0 D=414.0 H=265.0mm',
--     'Clinic_Plumbing');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '565.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '414.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '265.0');

-- Rule: IfcFlowFitting_Level_2 (310 instances, avg 60.0x61.0x80.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowFitting_Level_2', 'IfcFlowFitting', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowFitting on Level 2: 310 instances, avg W=60.0 D=61.0 H=80.0mm',
--     'Clinic_Plumbing');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '60.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '61.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '80.0');

-- Rule: IfcFlowController_TOF_Footing (114 instances, avg 137.0x161.0x141.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowController_TOF_Footing', 'IfcFlowController', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowController on TOF Footing: 114 instances, avg W=137.0 D=161.0 H=141.0mm',
--     'Clinic_Plumbing');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '137.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '161.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '141.0');

-- Rule: IfcFlowController_Level_1 (111 instances, avg 154.0x218.0x187.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowController_Level_1', 'IfcFlowController', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowController on Level 1: 111 instances, avg W=154.0 D=218.0 H=187.0mm',
--     'Clinic_Plumbing');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '154.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '218.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '187.0');

-- Rule: IfcFlowTerminal_Level_1 (74 instances, avg 526.0x512.0x329.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowTerminal_Level_1', 'IfcFlowTerminal', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowTerminal on Level 1: 74 instances, avg W=526.0 D=512.0 H=329.0mm',
--     'Clinic_Plumbing');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '526.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '512.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '329.0');

-- Rule: IfcFlowTerminal_Level_2 (45 instances, avg 533.0x511.0x409.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowTerminal_Level_2', 'IfcFlowTerminal', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowTerminal on Level 2: 45 instances, avg W=533.0 D=511.0 H=409.0mm',
--     'Clinic_Plumbing');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '533.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '511.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '409.0');

-- Rule: IfcFlowController_Level_2 (29 instances, avg 196.0x301.0x322.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowController_Level_2', 'IfcFlowController', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowController on Level 2: 29 instances, avg W=196.0 D=301.0 H=322.0mm',
--     'Clinic_Plumbing');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '196.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '301.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '322.0');


