-- ════════════════════════════════════════════════════════
-- BS: PCERT Building Structural (Building_Structural)
-- Source: DAGCompiler/lib/output/building_structural.db
-- Generated: 2026-03-30 23:14
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class                storey          cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- -----------------------  --------------  ---  --------  --------  --------  --------  --------
-- IfcBeam                  Unknown         6    193.0     4117.0    210.0     100.0     212.0   
-- IfcWall                  00 groundfloor  4    3550.0    200.0     5013.0    1300.0    5200.0  
-- IfcBuildingElementProxy  Unknown         2    1313.0    1342.0    550.0     1000.0    1626.0  
-- IfcDiscreteAccessory     Unknown         2    221.0     80.0      221.0     221.0     221.0   

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class                discipline  cnt
-- -----------------------  ----------  ---
-- IfcBeam                  STR         6  
-- IfcWall                  STR         4  
-- IfcBuildingElementProxy  ARC         2  
-- IfcDiscreteAccessory     STR         2  
-- IfcChimney               ARC         1  
-- IfcFooting               STR         1  

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcBeam_Unknown (6 instances, avg 193.0x4117.0x210.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcBeam_Unknown', 'IfcBeam', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcBeam on Unknown: 6 instances, avg W=193.0 D=4117.0 H=210.0mm',
--     'Building_Structural');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '193.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '4117.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '210.0');

-- Rule: IfcWall_00_groundfloor (4 instances, avg 3550.0x200.0x5013.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_00_groundfloor', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on 00 groundfloor: 4 instances, avg W=3550.0 D=200.0 H=5013.0mm',
--     'Building_Structural');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '3550.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '200.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '5013.0');


