-- ════════════════════════════════════════════════════════
-- CE: Clinic Electrical (Clinic_Electrical)
-- Source: DAGCompiler/lib/output/clinic_electrical.db
-- Generated: 2026-03-30 07:54
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class                storey   cnt   avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- -----------------------  -------  ----  --------  --------  --------  --------  --------
-- IfcFlowTerminal          Level 1  1162  366.0     307.0     164.0     59.0      2400.0  
-- IfcFlowTerminal          Level 2  919   348.0     310.0     198.0     59.0      2400.0  
-- IfcBuildingElementProxy  Level 1  15    454.0     416.0     1663.0    146.0     2235.0  
-- IfcBuildingElementProxy  Level 2  14    301.0     353.0     911.0     146.0     508.0   

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class                discipline  cnt 
-- -----------------------  ----------  ----
-- IfcFlowTerminal          MEP         1556
-- IfcFlowTerminal          ARC         525 
-- IfcBuildingElementProxy  ARC         29  

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcFlowTerminal_Level_1 (1162 instances, avg 366.0x307.0x164.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowTerminal_Level_1', 'IfcFlowTerminal', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowTerminal on Level 1: 1162 instances, avg W=366.0 D=307.0 H=164.0mm',
--     'Clinic_Electrical');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '366.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '307.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '164.0');

-- Rule: IfcFlowTerminal_Level_2 (919 instances, avg 348.0x310.0x198.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowTerminal_Level_2', 'IfcFlowTerminal', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowTerminal on Level 2: 919 instances, avg W=348.0 D=310.0 H=198.0mm',
--     'Clinic_Electrical');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '348.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '310.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '198.0');

-- Rule: IfcBuildingElementProxy_Level_1 (15 instances, avg 454.0x416.0x1663.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcBuildingElementProxy_Level_1', 'IfcBuildingElementProxy', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcBuildingElementProxy on Level 1: 15 instances, avg W=454.0 D=416.0 H=1663.0mm',
--     'Clinic_Electrical');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '454.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '416.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1663.0');

-- Rule: IfcBuildingElementProxy_Level_2 (14 instances, avg 301.0x353.0x911.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcBuildingElementProxy_Level_2', 'IfcBuildingElementProxy', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcBuildingElementProxy on Level 2: 14 instances, avg W=301.0 D=353.0 H=911.0mm',
--     'Clinic_Electrical');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '301.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '353.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '911.0');


