-- ════════════════════════════════════════════════════════
-- IP: PCERT Infra Plumbing (Infra_Plumbing)
-- Source: DAGCompiler/lib/output/infra_plumbing.db
-- Generated: 2026-03-30 07:55
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class           storey   cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- ------------------  -------  ---  --------  --------  --------  --------  --------
-- IfcFlowSegment      Unknown  24   2555.0    1850.0    700.0     2555.0    2555.0  
-- IfcElementAssembly  Unknown  2    1077.0    1040.0    2275.0    1077.0    1077.0  

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class                discipline  cnt
-- -----------------------  ----------  ---
-- IfcFlowSegment           MEP         24 
-- IfcElementAssembly       STR         2  
-- IfcBuildingElementProxy  ARC         1  

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcFlowSegment_Unknown (24 instances, avg 2555.0x1850.0x700.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowSegment_Unknown', 'IfcFlowSegment', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowSegment on Unknown: 24 instances, avg W=2555.0 D=1850.0 H=700.0mm',
--     'Infra_Plumbing');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2555.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1850.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '700.0');


