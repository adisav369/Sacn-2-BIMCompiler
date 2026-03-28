-- ════════════════════════════════════════════════════════
-- CH: Clinic HVAC (Clinic_HVAC)
-- Source: DAGCompiler/lib/output/clinic_hvac.db
-- Generated: 2026-03-28 14:35
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class          storey        cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- -----------------  ------------  ---  --------  --------  --------  --------  --------
-- IfcFlowFitting     First Floor   903  271.0     265.0     238.0     25.0      1200.0  
-- IfcFlowSegment     First Floor   867  1009.0    876.0     441.0     9.0       23366.0 
-- IfcFlowFitting     Second Floor  683  274.0     286.0     240.0     6.0       1000.0  
-- IfcFlowSegment     Second Floor  680  905.0     777.0     418.0     13.0      26659.0 
-- IfcFlowTerminal    First Floor   266  607.0     594.0     104.0     600.0     1241.0  
-- IfcFlowTerminal    Second Floor  174  600.0     600.0     102.0     600.0     600.0   
-- IfcFlowController  Second Floor  61   437.0     444.0     249.0     428.0     528.0   
-- IfcFlowController  First Floor   54   445.0     446.0     260.0     428.0     528.0   
-- IfcFlowFitting     TOF Footing   3    156.0     121.0     160.0     156.0     156.0   

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class          discipline  cnt 
-- -----------------  ----------  ----
-- IfcFlowFitting     MEP         1541
-- IfcFlowSegment     MEP         1060
-- IfcFlowSegment     ARC         488 
-- IfcFlowTerminal    MEP         437 
-- IfcFlowController  MEP         115 
-- IfcFlowFitting     ARC         49  
-- IfcFlowTerminal    ARC         3   

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcFlowFitting_First_Floor (903 instances, avg 271.0x265.0x238.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowFitting_First_Floor', 'IfcFlowFitting', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowFitting on First Floor: 903 instances, avg W=271.0 D=265.0 H=238.0mm',
--     'Clinic_HVAC');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '271.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '265.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '238.0');

-- Rule: IfcFlowSegment_First_Floor (867 instances, avg 1009.0x876.0x441.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowSegment_First_Floor', 'IfcFlowSegment', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowSegment on First Floor: 867 instances, avg W=1009.0 D=876.0 H=441.0mm',
--     'Clinic_HVAC');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1009.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '876.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '441.0');

-- Rule: IfcFlowFitting_Second_Floor (683 instances, avg 274.0x286.0x240.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowFitting_Second_Floor', 'IfcFlowFitting', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowFitting on Second Floor: 683 instances, avg W=274.0 D=286.0 H=240.0mm',
--     'Clinic_HVAC');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '274.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '286.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '240.0');

-- Rule: IfcFlowSegment_Second_Floor (680 instances, avg 905.0x777.0x418.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowSegment_Second_Floor', 'IfcFlowSegment', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowSegment on Second Floor: 680 instances, avg W=905.0 D=777.0 H=418.0mm',
--     'Clinic_HVAC');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '905.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '777.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '418.0');

-- Rule: IfcFlowTerminal_First_Floor (266 instances, avg 607.0x594.0x104.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowTerminal_First_Floor', 'IfcFlowTerminal', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowTerminal on First Floor: 266 instances, avg W=607.0 D=594.0 H=104.0mm',
--     'Clinic_HVAC');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '607.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '594.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '104.0');

-- Rule: IfcFlowTerminal_Second_Floor (174 instances, avg 600.0x600.0x102.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowTerminal_Second_Floor', 'IfcFlowTerminal', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowTerminal on Second Floor: 174 instances, avg W=600.0 D=600.0 H=102.0mm',
--     'Clinic_HVAC');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '600.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '600.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '102.0');

-- Rule: IfcFlowController_Second_Floor (61 instances, avg 437.0x444.0x249.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowController_Second_Floor', 'IfcFlowController', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowController on Second Floor: 61 instances, avg W=437.0 D=444.0 H=249.0mm',
--     'Clinic_HVAC');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '437.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '444.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '249.0');

-- Rule: IfcFlowController_First_Floor (54 instances, avg 445.0x446.0x260.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowController_First_Floor', 'IfcFlowController', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowController on First Floor: 54 instances, avg W=445.0 D=446.0 H=260.0mm',
--     'Clinic_HVAC');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '445.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '446.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '260.0');

-- Rule: IfcFlowFitting_TOF_Footing (3 instances, avg 156.0x121.0x160.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowFitting_TOF_Footing', 'IfcFlowFitting', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowFitting on TOF Footing: 3 instances, avg W=156.0 D=121.0 H=160.0mm',
--     'Clinic_HVAC');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '156.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '121.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '160.0');


