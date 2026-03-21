-- ════════════════════════════════════════════════════════
-- WL: BimWhale Large Building (BimWhale_Large)
-- Source: DAGCompiler/lib/output/bimwhale_large_enbloc.db
-- Generated: 2026-03-21 09:04
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class                storey   cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- -----------------------  -------  ---  --------  --------  --------  --------  --------
-- IfcWindow                Level 1  28   558.0     558.0     1830.0    200.0     915.0   
-- IfcDoor                  Level 1  18   692.0     692.0     2195.0    250.0     1643.0  
-- IfcFurnishingElement     Level 1  14   1563.0    819.0     762.0     1563.0    1563.0  
-- IfcWall                  Level 1  14   5779.0    5779.0    3893.0    200.0     20000.0 
-- IfcWindow                Level 2  14   302.0     813.0     1830.0    200.0     915.0   
-- IfcBuildingElementProxy  Level 1  13   7869.0    7076.0    5185.0    5924.0    8221.0  
-- IfcCovering              Level 1  4    5800.0    6825.0    57.0      3400.0    7800.0  
-- IfcWall                  Level 2  4    4100.0    10100.0   4000.0    200.0     8200.0  
-- IfcSlab                  Level 1  2    13800.0   13800.0   150.0     7800.0    19800.0 

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class                discipline  cnt
-- -----------------------  ----------  ---
-- IfcWindow                ARC         42 
-- IfcDoor                  ARC         18 
-- IfcWall                  ARC         18 
-- IfcFurnishingElement     ARC         14 
-- IfcBuildingElementProxy  ARC         13 
-- IfcCovering              ARC         5  
-- IfcSlab                  ARC         4  

-- §5: Candidate validation rules for disc_validation.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcWindow_Level_1 (28 instances, avg 558.0x558.0x1830.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Level_1', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Level 1: 28 instances, avg W=558.0 D=558.0 H=1830.0mm',
--     'BimWhale_Large');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '558.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '558.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1830.0');

-- Rule: IfcDoor_Level_1 (18 instances, avg 692.0x692.0x2195.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Level_1', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Level 1: 18 instances, avg W=692.0 D=692.0 H=2195.0mm',
--     'BimWhale_Large');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '692.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '692.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2195.0');

-- Rule: IfcFurnishingElement_Level_1 (14 instances, avg 1563.0x819.0x762.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFurnishingElement_Level_1', 'IfcFurnishingElement', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFurnishingElement on Level 1: 14 instances, avg W=1563.0 D=819.0 H=762.0mm',
--     'BimWhale_Large');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1563.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '819.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '762.0');

-- Rule: IfcWall_Level_1 (14 instances, avg 5779.0x5779.0x3893.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Level_1', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Level 1: 14 instances, avg W=5779.0 D=5779.0 H=3893.0mm',
--     'BimWhale_Large');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '5779.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '5779.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '3893.0');

-- Rule: IfcWindow_Level_2 (14 instances, avg 302.0x813.0x1830.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Level_2', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Level 2: 14 instances, avg W=302.0 D=813.0 H=1830.0mm',
--     'BimWhale_Large');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '302.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '813.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1830.0');

-- Rule: IfcBuildingElementProxy_Level_1 (13 instances, avg 7869.0x7076.0x5185.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcBuildingElementProxy_Level_1', 'IfcBuildingElementProxy', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcBuildingElementProxy on Level 1: 13 instances, avg W=7869.0 D=7076.0 H=5185.0mm',
--     'BimWhale_Large');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '7869.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '7076.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '5185.0');

-- Rule: IfcCovering_Level_1 (4 instances, avg 5800.0x6825.0x57.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcCovering_Level_1', 'IfcCovering', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcCovering on Level 1: 4 instances, avg W=5800.0 D=6825.0 H=57.0mm',
--     'BimWhale_Large');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '5800.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '6825.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '57.0');

-- Rule: IfcWall_Level_2 (4 instances, avg 4100.0x10100.0x4000.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Level_2', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Level 2: 4 instances, avg W=4100.0 D=10100.0 H=4000.0mm',
--     'BimWhale_Large');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '4100.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '10100.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '4000.0');


