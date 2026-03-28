-- ════════════════════════════════════════════════════════
-- WB: BimWhale Basic House (BimWhale_Basic)
-- Source: DAGCompiler/lib/output/bimwhale_basic.db
-- Generated: 2026-03-28 17:54
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class                storey   cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- -----------------------  -------  ---  --------  --------  --------  --------  --------
-- IfcFurnishingElement     Floor 0  71   833.0     765.0     828.0     380.0     3640.0  
-- IfcWindow                Floor 0  19   952.0     461.0     1327.0    115.0     2180.0  
-- IfcWall                  Floor 0  13   4967.0    3089.0    2603.0    170.0     22600.0 
-- IfcDoor                  Floor 0  8    682.0     472.0     2110.0    235.0     1010.0  
-- IfcFlowTerminal          Floor 0  7    671.0     686.0     989.0     457.0     865.0   
-- IfcBuildingElementProxy  Floor 0  4    3288.0    694.0     959.0     600.0     10962.0 
-- IfcSlab                  Unknown  2    23800.0   5350.0    1373.0    23800.0   23800.0 

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class                discipline  cnt
-- -----------------------  ----------  ---
-- IfcFurnishingElement     ARC         71 
-- IfcWindow                ARC         19 
-- IfcWall                  STR         13 
-- IfcDoor                  ARC         8  
-- IfcFlowTerminal          MEP         7  
-- IfcBuildingElementProxy  ARC         4  
-- IfcSlab                  STR         3  

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcFurnishingElement_Floor_0 (71 instances, avg 833.0x765.0x828.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFurnishingElement_Floor_0', 'IfcFurnishingElement', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFurnishingElement on Floor 0: 71 instances, avg W=833.0 D=765.0 H=828.0mm',
--     'BimWhale_Basic');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '833.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '765.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '828.0');

-- Rule: IfcWindow_Floor_0 (19 instances, avg 952.0x461.0x1327.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Floor_0', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Floor 0: 19 instances, avg W=952.0 D=461.0 H=1327.0mm',
--     'BimWhale_Basic');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '952.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '461.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1327.0');

-- Rule: IfcWall_Floor_0 (13 instances, avg 4967.0x3089.0x2603.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Floor_0', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Floor 0: 13 instances, avg W=4967.0 D=3089.0 H=2603.0mm',
--     'BimWhale_Basic');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '4967.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3089.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2603.0');

-- Rule: IfcDoor_Floor_0 (8 instances, avg 682.0x472.0x2110.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Floor_0', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Floor 0: 8 instances, avg W=682.0 D=472.0 H=2110.0mm',
--     'BimWhale_Basic');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '682.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '472.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2110.0');

-- Rule: IfcFlowTerminal_Floor_0 (7 instances, avg 671.0x686.0x989.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFlowTerminal_Floor_0', 'IfcFlowTerminal', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFlowTerminal on Floor 0: 7 instances, avg W=671.0 D=686.0 H=989.0mm',
--     'BimWhale_Basic');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '671.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '686.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '989.0');

-- Rule: IfcBuildingElementProxy_Floor_0 (4 instances, avg 3288.0x694.0x959.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcBuildingElementProxy_Floor_0', 'IfcBuildingElementProxy', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcBuildingElementProxy on Floor 0: 4 instances, avg W=3288.0 D=694.0 H=959.0mm',
--     'BimWhale_Basic');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '3288.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '694.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '959.0');


