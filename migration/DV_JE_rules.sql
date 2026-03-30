-- ════════════════════════════════════════════════════════
-- JE: Jesse Residential (Jesse)
-- Source: DAGCompiler/lib/output/jesse.db
-- Generated: 2026-03-30 07:45
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class       storey              cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- --------------  ------------------  ---  --------  --------  --------  --------  --------
-- IfcDoor         Unknown             209  1182.0    372.0     751.0     25.0      3775.0  
-- IfcMember       Unknown             179  1782.0    252.0     655.0     25.0      3950.0  
-- IfcWall         Level -1            68   2641.0    2473.0    2335.0    100.0     21525.0 
-- IfcWall         Level 1             35   3273.0    2547.0    4153.0    100.0     21350.0 
-- IfcDoor         Level 1             25   830.0     670.0     2243.0    150.0     1982.0  
-- IfcWall         T.O. level 0 floor  24   3295.0    3005.0    3914.0    100.0     21525.0 
-- IfcDoor         Level -1            21   1067.0    254.0     2195.0    150.0     1982.0  
-- IfcWall         level 0             17   1421.0    2748.0    3141.0    100.0     12300.0 
-- IfcRailing      Unknown             10   40.0      1702.0    2229.0    40.0      40.0    
-- IfcWall         Level 2             9    2278.0    3037.0    2600.0    100.0     8650.0  
-- IfcWall         T.O. level 1 floor  7    6939.0    5871.0    360.0     250.0     21625.0 
-- IfcDoor         level 0             6    764.0     1054.0    2244.0    150.0     2000.0  
-- IfcStairFlight  Unknown             5    1150.0    1747.0    1462.0    1150.0    1150.0  
-- IfcDoor         Level 2             3    456.0     761.0     2210.0    150.0     1067.0  
-- IfcSlab         Level 2             2    11450.0   6260.0    360.0     3400.0    19500.0 
-- IfcSlab         level 0             2    11350.0   6298.0    360.0     3400.0    19300.0 

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class       discipline  cnt
-- --------------  ----------  ---
-- IfcDoor         ARC         264
-- IfcMember       STR         179
-- IfcWall         STR         160
-- IfcRailing      ARC         10 
-- IfcSlab         STR         7  
-- IfcStairFlight  ARC         5  
-- IfcRoof         ARC         1  

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcDoor_Unknown (209 instances, avg 1182.0x372.0x751.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Unknown', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Unknown: 209 instances, avg W=1182.0 D=372.0 H=751.0mm',
--     'Jesse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1182.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '372.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '751.0');

-- Rule: IfcMember_Unknown (179 instances, avg 1782.0x252.0x655.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcMember_Unknown', 'IfcMember', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcMember on Unknown: 179 instances, avg W=1782.0 D=252.0 H=655.0mm',
--     'Jesse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1782.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '252.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '655.0');

-- Rule: IfcWall_Level_-1 (68 instances, avg 2641.0x2473.0x2335.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Level_-1', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Level -1: 68 instances, avg W=2641.0 D=2473.0 H=2335.0mm',
--     'Jesse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2641.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '2473.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2335.0');

-- Rule: IfcWall_Level_1 (35 instances, avg 3273.0x2547.0x4153.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Level_1', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Level 1: 35 instances, avg W=3273.0 D=2547.0 H=4153.0mm',
--     'Jesse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '3273.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '2547.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '4153.0');

-- Rule: IfcDoor_Level_1 (25 instances, avg 830.0x670.0x2243.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Level_1', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Level 1: 25 instances, avg W=830.0 D=670.0 H=2243.0mm',
--     'Jesse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '830.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '670.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2243.0');

-- Rule: IfcWall_T.O._level_0_floor (24 instances, avg 3295.0x3005.0x3914.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_T.O._level_0_floor', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on T.O. level 0 floor: 24 instances, avg W=3295.0 D=3005.0 H=3914.0mm',
--     'Jesse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '3295.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3005.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '3914.0');

-- Rule: IfcDoor_Level_-1 (21 instances, avg 1067.0x254.0x2195.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Level_-1', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Level -1: 21 instances, avg W=1067.0 D=254.0 H=2195.0mm',
--     'Jesse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1067.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '254.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2195.0');

-- Rule: IfcWall_level_0 (17 instances, avg 1421.0x2748.0x3141.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_level_0', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on level 0: 17 instances, avg W=1421.0 D=2748.0 H=3141.0mm',
--     'Jesse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1421.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '2748.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '3141.0');

-- Rule: IfcRailing_Unknown (10 instances, avg 40.0x1702.0x2229.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcRailing_Unknown', 'IfcRailing', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcRailing on Unknown: 10 instances, avg W=40.0 D=1702.0 H=2229.0mm',
--     'Jesse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '40.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1702.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2229.0');

-- Rule: IfcWall_Level_2 (9 instances, avg 2278.0x3037.0x2600.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Level_2', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Level 2: 9 instances, avg W=2278.0 D=3037.0 H=2600.0mm',
--     'Jesse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2278.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3037.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2600.0');

-- Rule: IfcWall_T.O._level_1_floor (7 instances, avg 6939.0x5871.0x360.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_T.O._level_1_floor', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on T.O. level 1 floor: 7 instances, avg W=6939.0 D=5871.0 H=360.0mm',
--     'Jesse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '6939.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '5871.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '360.0');

-- Rule: IfcDoor_level_0 (6 instances, avg 764.0x1054.0x2244.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_level_0', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on level 0: 6 instances, avg W=764.0 D=1054.0 H=2244.0mm',
--     'Jesse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '764.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1054.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2244.0');

-- Rule: IfcStairFlight_Unknown (5 instances, avg 1150.0x1747.0x1462.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcStairFlight_Unknown', 'IfcStairFlight', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcStairFlight on Unknown: 5 instances, avg W=1150.0 D=1747.0 H=1462.0mm',
--     'Jesse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1150.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1747.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1462.0');

-- Rule: IfcDoor_Level_2 (3 instances, avg 456.0x761.0x2210.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Level_2', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Level 2: 3 instances, avg W=456.0 D=761.0 H=2210.0mm',
--     'Jesse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '456.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '761.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2210.0');


