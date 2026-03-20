-- ════════════════════════════════════════════════════════
-- CS: Clinic Structural (Clinic_Structural)
-- Source: DAGCompiler/lib/output/clinic_structural_enbloc.db
-- Generated: 2026-03-21 05:44
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class   storey        cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- ----------  ------------  ---  --------  --------  --------  --------  --------
-- IfcBeam     First Floor   415  1051.0    6414.0    390.0     74.0      7371.0  
-- IfcBeam     Second Floor  315  1445.0    5894.0    394.0     74.0      8692.0  
-- IfcColumn   TOF Footing   111  291.0     274.0     3534.0    152.0     400.0   
-- IfcColumn   Second Floor  68   221.0     212.0     3913.0    152.0     257.0   
-- IfcFooting  TOF Footing   58   3579.0    3832.0    300.0     900.0     24440.0 
-- IfcFooting  First Floor   38   1974.0    1974.0    300.0     1000.0    2000.0  
-- IfcWall     TOF Footing   26   5420.0    6031.0    1462.0    264.0     26440.0 
-- IfcColumn   Roof - Main   10   152.0     152.0     2988.0    152.0     152.0   
-- IfcBeam     Roof - Main   8    6570.0    153.0     455.0     4614.0    7772.0  
-- IfcSlab     Unknown       7    19488.0   16157.0   120.0     8927.0    47226.0 
-- IfcColumn   First Floor   6    152.0     152.0     2380.0    152.0     152.0   
-- IfcRoof     Roof - Main   5    10344.0   7541.0    1396.0    5230.0    21381.0 
-- IfcSlab     First Floor   4    18092.0   20970.0   150.0     2235.0    52132.0 
-- IfcRailing  Unknown       3    2862.0    1507.0    1803.0    40.0      8505.0  

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class       discipline  cnt
-- --------------  ----------  ---
-- IfcBeam         STR         738
-- IfcColumn       ARC         195
-- IfcFooting      ARC         96 
-- IfcWall         ARC         26 
-- IfcSlab         STR         13 
-- IfcRoof         ARC         5  
-- IfcRailing      STR         3  
-- IfcRampFlight   ARC         1  
-- IfcStairFlight  STR         1  

-- §5: Candidate validation rules for disc_validation.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcBeam_First_Floor (415 instances, avg 1051.0x6414.0x390.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcBeam_First_Floor', 'IfcBeam', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcBeam on First Floor: 415 instances, avg W=1051.0 D=6414.0 H=390.0mm',
--     'Clinic_Structural');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1051.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '6414.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '390.0');

-- Rule: IfcBeam_Second_Floor (315 instances, avg 1445.0x5894.0x394.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcBeam_Second_Floor', 'IfcBeam', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcBeam on Second Floor: 315 instances, avg W=1445.0 D=5894.0 H=394.0mm',
--     'Clinic_Structural');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1445.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '5894.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '394.0');

-- Rule: IfcColumn_TOF_Footing (111 instances, avg 291.0x274.0x3534.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcColumn_TOF_Footing', 'IfcColumn', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcColumn on TOF Footing: 111 instances, avg W=291.0 D=274.0 H=3534.0mm',
--     'Clinic_Structural');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '291.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '274.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '3534.0');

-- Rule: IfcColumn_Second_Floor (68 instances, avg 221.0x212.0x3913.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcColumn_Second_Floor', 'IfcColumn', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcColumn on Second Floor: 68 instances, avg W=221.0 D=212.0 H=3913.0mm',
--     'Clinic_Structural');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '221.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '212.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '3913.0');

-- Rule: IfcFooting_TOF_Footing (58 instances, avg 3579.0x3832.0x300.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFooting_TOF_Footing', 'IfcFooting', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFooting on TOF Footing: 58 instances, avg W=3579.0 D=3832.0 H=300.0mm',
--     'Clinic_Structural');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '3579.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3832.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '300.0');

-- Rule: IfcFooting_First_Floor (38 instances, avg 1974.0x1974.0x300.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFooting_First_Floor', 'IfcFooting', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFooting on First Floor: 38 instances, avg W=1974.0 D=1974.0 H=300.0mm',
--     'Clinic_Structural');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1974.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1974.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '300.0');

-- Rule: IfcWall_TOF_Footing (26 instances, avg 5420.0x6031.0x1462.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_TOF_Footing', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on TOF Footing: 26 instances, avg W=5420.0 D=6031.0 H=1462.0mm',
--     'Clinic_Structural');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '5420.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '6031.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1462.0');

-- Rule: IfcColumn_Roof_-_Main (10 instances, avg 152.0x152.0x2988.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcColumn_Roof_-_Main', 'IfcColumn', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcColumn on Roof - Main: 10 instances, avg W=152.0 D=152.0 H=2988.0mm',
--     'Clinic_Structural');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '152.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '152.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2988.0');

-- Rule: IfcBeam_Roof_-_Main (8 instances, avg 6570.0x153.0x455.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcBeam_Roof_-_Main', 'IfcBeam', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcBeam on Roof - Main: 8 instances, avg W=6570.0 D=153.0 H=455.0mm',
--     'Clinic_Structural');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '6570.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '153.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '455.0');

-- Rule: IfcSlab_Unknown (7 instances, avg 19488.0x16157.0x120.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcSlab_Unknown', 'IfcSlab', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcSlab on Unknown: 7 instances, avg W=19488.0 D=16157.0 H=120.0mm',
--     'Clinic_Structural');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '19488.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '16157.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '120.0');

-- Rule: IfcColumn_First_Floor (6 instances, avg 152.0x152.0x2380.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcColumn_First_Floor', 'IfcColumn', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcColumn on First Floor: 6 instances, avg W=152.0 D=152.0 H=2380.0mm',
--     'Clinic_Structural');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '152.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '152.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2380.0');

-- Rule: IfcRoof_Roof_-_Main (5 instances, avg 10344.0x7541.0x1396.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcRoof_Roof_-_Main', 'IfcRoof', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcRoof on Roof - Main: 5 instances, avg W=10344.0 D=7541.0 H=1396.0mm',
--     'Clinic_Structural');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '10344.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '7541.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1396.0');

-- Rule: IfcSlab_First_Floor (4 instances, avg 18092.0x20970.0x150.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcSlab_First_Floor', 'IfcSlab', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcSlab on First Floor: 4 instances, avg W=18092.0 D=20970.0 H=150.0mm',
--     'Clinic_Structural');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '18092.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '20970.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '150.0');

-- Rule: IfcRailing_Unknown (3 instances, avg 2862.0x1507.0x1803.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcRailing_Unknown', 'IfcRailing', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcRailing on Unknown: 3 instances, avg W=2862.0 D=1507.0 H=1803.0mm',
--     'Clinic_Structural');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2862.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1507.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1803.0');


