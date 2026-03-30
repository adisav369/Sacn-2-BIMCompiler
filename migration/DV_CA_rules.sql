-- ════════════════════════════════════════════════════════
-- CA: Clinic Architecture (Clinic_Architecture)
-- Source: DAGCompiler/lib/output/clinic_architecture.db
-- Generated: 2026-03-31 00:20
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class             storey        cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- --------------------  ------------  ---  --------  --------  --------  --------  --------
-- IfcWall               First Floor   656  1363.0    1296.0    4082.0    25.0      32333.0 
-- IfcMember             Unknown       543  457.0     502.0     713.0     20.0      4840.0  
-- IfcWall               Second Floor  404  1622.0    1469.0    4340.0    25.0      47493.0 
-- IfcPlate              Unknown       172  587.0     646.0     1366.0    8.0       1649.0  
-- IfcCovering           First Floor   153  3941.0    3714.0    53.0      902.0     30095.0 
-- IfcDoor               First Floor   151  621.0     653.0     2198.0    25.0      1982.0  
-- IfcCovering           Second Floor  96   4053.0    4031.0    52.0      876.0     22790.0 
-- IfcDoor               Second Floor  96   706.0     538.0     2173.0    25.0      1067.0  
-- IfcFlowTerminal       First Floor   65   410.0     415.0     765.0     19.0      2235.0  
-- IfcFurnishingElement  First Floor   64   674.0     1168.0    510.0     344.0     1525.0  
-- IfcFurnishingElement  Second Floor  54   742.0     1274.0    524.0     344.0     3872.0  
-- IfcFlowTerminal       Second Floor  37   401.0     388.0     607.0     19.0      1146.0  
-- IfcWindow             Second Floor  36   715.0     552.0     1735.0    267.0     1000.0  
-- IfcWindow             First Floor   22   434.0     833.0     1735.0    267.0     1000.0  
-- IfcWall               Roof - Main   20   3151.0    1355.0    2920.0    124.0     18536.0 
-- IfcRailing            Unknown       6    2786.0    3572.0    5679.0    1265.0    5140.0  
-- IfcDoor               Unknown       5    1151.0    739.0     2180.0    114.0     2702.0  
-- IfcDoor               TOF Footing   2    2702.0    46.0      1962.0    2702.0    2702.0  
-- IfcRailing            Second Floor  2    1109.0    6206.0    1100.0    1108.0    1110.0  

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class             discipline  cnt 
-- --------------------  ----------  ----
-- IfcWall               STR         1080
-- IfcMember             STR         543 
-- IfcDoor               ARC         254 
-- IfcCovering           ARC         250 
-- IfcPlate              STR         172 
-- IfcFurnishingElement  ARC         118 
-- IfcFlowTerminal       ARC         88  
-- IfcWindow             ARC         58  
-- IfcFlowTerminal       MEP         14  
-- IfcRailing            ARC         9   

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcWall_First_Floor (656 instances, avg 1363.0x1296.0x4082.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_First_Floor', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on First Floor: 656 instances, avg W=1363.0 D=1296.0 H=4082.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1363.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1296.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '4082.0');

-- Rule: IfcMember_Unknown (543 instances, avg 457.0x502.0x713.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcMember_Unknown', 'IfcMember', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcMember on Unknown: 543 instances, avg W=457.0 D=502.0 H=713.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '457.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '502.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '713.0');

-- Rule: IfcWall_Second_Floor (404 instances, avg 1622.0x1469.0x4340.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Second_Floor', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Second Floor: 404 instances, avg W=1622.0 D=1469.0 H=4340.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1622.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1469.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '4340.0');

-- Rule: IfcPlate_Unknown (172 instances, avg 587.0x646.0x1366.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcPlate_Unknown', 'IfcPlate', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcPlate on Unknown: 172 instances, avg W=587.0 D=646.0 H=1366.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '587.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '646.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1366.0');

-- Rule: IfcCovering_First_Floor (153 instances, avg 3941.0x3714.0x53.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcCovering_First_Floor', 'IfcCovering', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcCovering on First Floor: 153 instances, avg W=3941.0 D=3714.0 H=53.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '3941.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3714.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '53.0');

-- Rule: IfcDoor_First_Floor (151 instances, avg 621.0x653.0x2198.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_First_Floor', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on First Floor: 151 instances, avg W=621.0 D=653.0 H=2198.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '621.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '653.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2198.0');

-- Rule: IfcCovering_Second_Floor (96 instances, avg 4053.0x4031.0x52.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcCovering_Second_Floor', 'IfcCovering', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcCovering on Second Floor: 96 instances, avg W=4053.0 D=4031.0 H=52.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '4053.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '4031.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '52.0');

-- Rule: IfcDoor_Second_Floor (96 instances, avg 706.0x538.0x2173.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Second_Floor', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Second Floor: 96 instances, avg W=706.0 D=538.0 H=2173.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '706.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '538.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2173.0');

-- Rule: IfcFlowTerminal_First_Floor (65 instances, avg 410.0x415.0x765.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowTerminal_First_Floor', 'IfcFlowTerminal', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowTerminal on First Floor: 65 instances, avg W=410.0 D=415.0 H=765.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '410.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '415.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '765.0');

-- Rule: IfcFurnishingElement_First_Floor (64 instances, avg 674.0x1168.0x510.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFurnishingElement_First_Floor', 'IfcFurnishingElement', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFurnishingElement on First Floor: 64 instances, avg W=674.0 D=1168.0 H=510.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '674.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1168.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '510.0');

-- Rule: IfcFurnishingElement_Second_Floor (54 instances, avg 742.0x1274.0x524.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFurnishingElement_Second_Floor', 'IfcFurnishingElement', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFurnishingElement on Second Floor: 54 instances, avg W=742.0 D=1274.0 H=524.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '742.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1274.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '524.0');

-- Rule: IfcFlowTerminal_Second_Floor (37 instances, avg 401.0x388.0x607.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowTerminal_Second_Floor', 'IfcFlowTerminal', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowTerminal on Second Floor: 37 instances, avg W=401.0 D=388.0 H=607.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '401.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '388.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '607.0');

-- Rule: IfcWindow_Second_Floor (36 instances, avg 715.0x552.0x1735.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Second_Floor', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Second Floor: 36 instances, avg W=715.0 D=552.0 H=1735.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '715.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '552.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1735.0');

-- Rule: IfcWindow_First_Floor (22 instances, avg 434.0x833.0x1735.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_First_Floor', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on First Floor: 22 instances, avg W=434.0 D=833.0 H=1735.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '434.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '833.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1735.0');

-- Rule: IfcWall_Roof_-_Main (20 instances, avg 3151.0x1355.0x2920.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Roof_-_Main', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Roof - Main: 20 instances, avg W=3151.0 D=1355.0 H=2920.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '3151.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1355.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2920.0');

-- Rule: IfcRailing_Unknown (6 instances, avg 2786.0x3572.0x5679.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcRailing_Unknown', 'IfcRailing', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcRailing on Unknown: 6 instances, avg W=2786.0 D=3572.0 H=5679.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2786.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3572.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '5679.0');

-- Rule: IfcDoor_Unknown (5 instances, avg 1151.0x739.0x2180.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Unknown', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Unknown: 5 instances, avg W=1151.0 D=739.0 H=2180.0mm',
--     'Clinic_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1151.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '739.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2180.0');


