-- ════════════════════════════════════════════════════════
-- SH: Sample House (Ifc4_SampleHouse)
-- Source: DAGCompiler/lib/output/ifc4_samplehouse.db
-- Generated: 2026-03-24 09:52
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class             storey        cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- --------------------  ------------  ---  --------  --------  --------  --------  --------
-- IfcMember             Unknown       20   499.0     581.0     1226.0    30.0      1603.0  
-- IfcFurnishingElement  Ground Floor  14   1136.0    795.0     984.0     427.0     2287.0  
-- IfcPlate              Unknown       6    829.0     955.0     3027.0    25.0      1633.0  
-- IfcWall               Ground Floor  5    5588.0    2397.0    2665.0    95.0      14145.0 
-- IfcWindow             Ground Floor  4    1860.0    353.0     1210.0    1860.0    1860.0  
-- IfcCovering           Ground Floor  3    6071.0    3690.0    57.0      4453.0    9308.0  
-- IfcDoor               Ground Floor  3    739.0     653.0     2133.0    178.0     1860.0  

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class             discipline  cnt
-- --------------------  ----------  ---
-- IfcMember             STR         20 
-- IfcFurnishingElement  ARC         14 
-- IfcPlate              STR         6  
-- IfcWall               STR         5  
-- IfcWindow             ARC         4  
-- IfcCovering           ARC         3  
-- IfcDoor               ARC         3  
-- IfcSlab               STR         2  
-- IfcRoof               STR         1  

-- §5: Candidate validation rules for disc_validation.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcMember_Unknown (20 instances, avg 499.0x581.0x1226.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcMember_Unknown', 'IfcMember', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcMember on Unknown: 20 instances, avg W=499.0 D=581.0 H=1226.0mm',
--     'Ifc4_SampleHouse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '499.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '581.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1226.0');

-- Rule: IfcFurnishingElement_Ground_Floor (14 instances, avg 1136.0x795.0x984.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFurnishingElement_Ground_Floor', 'IfcFurnishingElement', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFurnishingElement on Ground Floor: 14 instances, avg W=1136.0 D=795.0 H=984.0mm',
--     'Ifc4_SampleHouse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1136.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '795.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '984.0');

-- Rule: IfcPlate_Unknown (6 instances, avg 829.0x955.0x3027.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcPlate_Unknown', 'IfcPlate', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcPlate on Unknown: 6 instances, avg W=829.0 D=955.0 H=3027.0mm',
--     'Ifc4_SampleHouse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '829.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '955.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '3027.0');

-- Rule: IfcWall_Ground_Floor (5 instances, avg 5588.0x2397.0x2665.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Ground_Floor', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Ground Floor: 5 instances, avg W=5588.0 D=2397.0 H=2665.0mm',
--     'Ifc4_SampleHouse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '5588.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '2397.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2665.0');

-- Rule: IfcWindow_Ground_Floor (4 instances, avg 1860.0x353.0x1210.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Ground_Floor', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Ground Floor: 4 instances, avg W=1860.0 D=353.0 H=1210.0mm',
--     'Ifc4_SampleHouse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1860.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '353.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1210.0');

-- Rule: IfcCovering_Ground_Floor (3 instances, avg 6071.0x3690.0x57.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcCovering_Ground_Floor', 'IfcCovering', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcCovering on Ground Floor: 3 instances, avg W=6071.0 D=3690.0 H=57.0mm',
--     'Ifc4_SampleHouse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '6071.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3690.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '57.0');

-- Rule: IfcDoor_Ground_Floor (3 instances, avg 739.0x653.0x2133.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Ground_Floor', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Ground Floor: 3 instances, avg W=739.0 D=653.0 H=2133.0mm',
--     'Ifc4_SampleHouse');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '739.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '653.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2133.0');


