-- ════════════════════════════════════════════════════════
-- WT: BimWhale Tall Building (BimWhale_Tall)
-- Source: DAGCompiler/lib/output/bimwhale_tall.db
-- Generated: 2026-03-28 22:04
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class  storey   cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- ---------  -------  ---  --------  --------  --------  --------  --------
-- IfcWindow  Level 2  7    609.0     506.0     1830.0    200.0     915.0   
-- IfcWall    Level 1  6    3433.0    4033.0    3900.0    200.0     8200.0  
-- IfcWall    Level 2  6    3433.0    4033.0    4000.0    200.0     8200.0  
-- IfcWall    Level 4  6    3433.0    4033.0    4000.0    200.0     8200.0  
-- IfcWall    Level 5  6    3433.0    4033.0    4000.0    200.0     8200.0  
-- IfcWindow  Level 1  6    677.0     438.0     1830.0    200.0     915.0   
-- IfcDoor    Level 1  5    255.0     1182.0    2182.0    250.0     275.0   
-- IfcWindow  Level 4  4    915.0     200.0     1830.0    915.0     915.0   
-- IfcWindow  Level 5  4    915.0     200.0     1830.0    915.0     915.0   

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class  discipline  cnt
-- ---------  ----------  ---
-- IfcWall    STR         24 
-- IfcWindow  ARC         21 
-- IfcDoor    ARC         5  
-- IfcSlab    STR         5  

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcWindow_Level_2 (7 instances, avg 609.0x506.0x1830.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Level_2', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Level 2: 7 instances, avg W=609.0 D=506.0 H=1830.0mm',
--     'BimWhale_Tall');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '609.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '506.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1830.0');

-- Rule: IfcWall_Level_1 (6 instances, avg 3433.0x4033.0x3900.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Level_1', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Level 1: 6 instances, avg W=3433.0 D=4033.0 H=3900.0mm',
--     'BimWhale_Tall');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '3433.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '4033.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '3900.0');

-- Rule: IfcWall_Level_2 (6 instances, avg 3433.0x4033.0x4000.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Level_2', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Level 2: 6 instances, avg W=3433.0 D=4033.0 H=4000.0mm',
--     'BimWhale_Tall');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '3433.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '4033.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '4000.0');

-- Rule: IfcWall_Level_4 (6 instances, avg 3433.0x4033.0x4000.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Level_4', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Level 4: 6 instances, avg W=3433.0 D=4033.0 H=4000.0mm',
--     'BimWhale_Tall');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '3433.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '4033.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '4000.0');

-- Rule: IfcWall_Level_5 (6 instances, avg 3433.0x4033.0x4000.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Level_5', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Level 5: 6 instances, avg W=3433.0 D=4033.0 H=4000.0mm',
--     'BimWhale_Tall');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '3433.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '4033.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '4000.0');

-- Rule: IfcWindow_Level_1 (6 instances, avg 677.0x438.0x1830.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Level_1', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Level 1: 6 instances, avg W=677.0 D=438.0 H=1830.0mm',
--     'BimWhale_Tall');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '677.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '438.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1830.0');

-- Rule: IfcDoor_Level_1 (5 instances, avg 255.0x1182.0x2182.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Level_1', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Level 1: 5 instances, avg W=255.0 D=1182.0 H=2182.0mm',
--     'BimWhale_Tall');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '255.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1182.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2182.0');

-- Rule: IfcWindow_Level_4 (4 instances, avg 915.0x200.0x1830.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Level_4', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Level 4: 4 instances, avg W=915.0 D=200.0 H=1830.0mm',
--     'BimWhale_Tall');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '915.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '200.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1830.0');

-- Rule: IfcWindow_Level_5 (4 instances, avg 915.0x200.0x1830.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Level_5', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Level 5: 4 instances, avg W=915.0 D=200.0 H=1830.0mm',
--     'BimWhale_Tall');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '915.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '200.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1830.0');


