-- ════════════════════════════════════════════════════════
-- RS: Revit Structural (Revit_STR)
-- Source: DAGCompiler/lib/output/revit_str_enbloc.db
-- Generated: 2026-03-21 09:27
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class          storey   cnt   avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- -----------------  -------  ----  --------  --------  --------  --------  --------
-- IfcReinforcingBar  Unknown  3680  1210.0    767.0     899.0     8.0       24360.0 
-- IfcBeam            Unknown  370   2339.0    1059.0    340.0     50.0      13487.0 
-- IfcSlab            Unknown  37    2076.0    1686.0    3041.0    300.0     24410.0 
-- IfcColumn          Unknown  30    116.0     155.0     3484.0    64.0      450.0   
-- IfcWall            Unknown  9     10269.0   3133.0    2756.0    280.0     24410.0 
-- IfcFooting         Unknown  4     11001.0   5514.0    300.0     900.0     20727.0 
-- IfcMember          Unknown  2     50.0      900.0     772.0     50.0      50.0    

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class          discipline  cnt 
-- -----------------  ----------  ----
-- IfcReinforcingBar  STR         3680
-- IfcBeam            STR         370 
-- IfcSlab            STR         37  
-- IfcColumn          ARC         30  
-- IfcWall            ARC         9   
-- IfcFooting         ARC         4   
-- IfcMember          STR         2   
-- IfcStairFlight     STR         1   

-- §5: Candidate validation rules for disc_validation.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcReinforcingBar_Unknown (3680 instances, avg 1210.0x767.0x899.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcReinforcingBar_Unknown', 'IfcReinforcingBar', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcReinforcingBar on Unknown: 3680 instances, avg W=1210.0 D=767.0 H=899.0mm',
--     'Revit_STR');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1210.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '767.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '899.0');

-- Rule: IfcBeam_Unknown (370 instances, avg 2339.0x1059.0x340.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcBeam_Unknown', 'IfcBeam', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcBeam on Unknown: 370 instances, avg W=2339.0 D=1059.0 H=340.0mm',
--     'Revit_STR');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2339.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1059.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '340.0');

-- Rule: IfcSlab_Unknown (37 instances, avg 2076.0x1686.0x3041.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcSlab_Unknown', 'IfcSlab', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcSlab on Unknown: 37 instances, avg W=2076.0 D=1686.0 H=3041.0mm',
--     'Revit_STR');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2076.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1686.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '3041.0');

-- Rule: IfcColumn_Unknown (30 instances, avg 116.0x155.0x3484.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcColumn_Unknown', 'IfcColumn', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcColumn on Unknown: 30 instances, avg W=116.0 D=155.0 H=3484.0mm',
--     'Revit_STR');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '116.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '155.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '3484.0');

-- Rule: IfcWall_Unknown (9 instances, avg 10269.0x3133.0x2756.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Unknown', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Unknown: 9 instances, avg W=10269.0 D=3133.0 H=2756.0mm',
--     'Revit_STR');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '10269.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3133.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2756.0');

-- Rule: IfcFooting_Unknown (4 instances, avg 11001.0x5514.0x300.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFooting_Unknown', 'IfcFooting', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFooting on Unknown: 4 instances, avg W=11001.0 D=5514.0 H=300.0mm',
--     'Revit_STR');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '11001.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '5514.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '300.0');


