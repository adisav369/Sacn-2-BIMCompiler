-- ════════════════════════════════════════════════════════
-- GH: AC9 Haus G-H (AC9_HausGH)
-- Source: DAGCompiler/lib/output/ac9_hausgh.db
-- Generated: 2026-03-28 17:50
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class   storey        cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- ----------  ------------  ---  --------  --------  --------  --------  --------
-- IfcWall     Keller        21   2221.0    2341.0    2625.0    115.0     12115.0 
-- IfcWall     Obergeschoss  20   2654.0    1909.0    2240.0    115.0     12130.0 
-- IfcWindow   Erdgeschoss   17   457.0     579.0     2165.0    70.0      1010.0  
-- IfcBeam     Dachgeschoss  16   1857.0    100.0     200.0     1857.0    1857.0  
-- IfcWall     Erdgeschoss   15   2718.0    3015.0    2625.0    115.0     12130.0 
-- IfcSlab     Obergeschoss  13   5140.0    5349.0    1277.0    3080.0    12130.0 
-- IfcSlab     Keller        11   3813.0    4250.0    125.0     1565.0    12115.0 
-- IfcDoor     Keller        10   430.0     738.0     2135.0    170.0     1010.0  
-- IfcWindow   Keller        10   637.0     446.0     1589.0    70.0      1500.0  
-- IfcSlab     Erdgeschoss   9    4656.0    5515.0    127.0     1735.0    13865.0 
-- IfcDoor     Erdgeschoss   8    444.0     826.0     2150.0    170.0     885.0   
-- IfcWindow   Obergeschoss  8    540.0     853.0     1692.0    70.0      1010.0  
-- IfcDoor     Obergeschoss  7    621.0     481.0     2146.0    185.0     1010.0  
-- IfcRailing  Erdgeschoss   7    553.0     1330.0    1000.0    80.0      1735.0  
-- IfcRailing  Dachgeschoss  5    324.0     1296.0    1000.0    80.0      1300.0  
-- IfcRailing  Obergeschoss  4    1033.0    1015.0    1000.0    80.0      1985.0  
-- IfcWall     Dachgeschoss  4    3762.0    4848.0    2035.0    115.0     7410.0  
-- IfcSlab     Dachgeschoss  2    8590.0    9695.0    150.0     5780.0    11400.0 
-- IfcStair    Keller        2    2155.0    2681.0    3902.0    1710.0    2600.0  

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class                discipline  cnt
-- -----------------------  ----------  ---
-- IfcWall                  STR         60 
-- IfcSlab                  STR         35 
-- IfcWindow                ARC         35 
-- IfcDoor                  ARC         25 
-- IfcBeam                  STR         16 
-- IfcRailing               ARC         16 
-- IfcStair                 ARC         4  
-- IfcBuildingElementProxy  ARC         1  
-- IfcColumn                STR         1  

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcWall_Keller (21 instances, avg 2221.0x2341.0x2625.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Keller', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Keller: 21 instances, avg W=2221.0 D=2341.0 H=2625.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2221.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '2341.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2625.0');

-- Rule: IfcWall_Obergeschoss (20 instances, avg 2654.0x1909.0x2240.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Obergeschoss', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Obergeschoss: 20 instances, avg W=2654.0 D=1909.0 H=2240.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2654.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1909.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2240.0');

-- Rule: IfcWindow_Erdgeschoss (17 instances, avg 457.0x579.0x2165.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Erdgeschoss', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Erdgeschoss: 17 instances, avg W=457.0 D=579.0 H=2165.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '457.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '579.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2165.0');

-- Rule: IfcBeam_Dachgeschoss (16 instances, avg 1857.0x100.0x200.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcBeam_Dachgeschoss', 'IfcBeam', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcBeam on Dachgeschoss: 16 instances, avg W=1857.0 D=100.0 H=200.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1857.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '100.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '200.0');

-- Rule: IfcWall_Erdgeschoss (15 instances, avg 2718.0x3015.0x2625.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Erdgeschoss', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Erdgeschoss: 15 instances, avg W=2718.0 D=3015.0 H=2625.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2718.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3015.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2625.0');

-- Rule: IfcSlab_Obergeschoss (13 instances, avg 5140.0x5349.0x1277.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcSlab_Obergeschoss', 'IfcSlab', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcSlab on Obergeschoss: 13 instances, avg W=5140.0 D=5349.0 H=1277.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '5140.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '5349.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1277.0');

-- Rule: IfcSlab_Keller (11 instances, avg 3813.0x4250.0x125.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcSlab_Keller', 'IfcSlab', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcSlab on Keller: 11 instances, avg W=3813.0 D=4250.0 H=125.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '3813.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '4250.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '125.0');

-- Rule: IfcDoor_Keller (10 instances, avg 430.0x738.0x2135.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Keller', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Keller: 10 instances, avg W=430.0 D=738.0 H=2135.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '430.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '738.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2135.0');

-- Rule: IfcWindow_Keller (10 instances, avg 637.0x446.0x1589.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Keller', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Keller: 10 instances, avg W=637.0 D=446.0 H=1589.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '637.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '446.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1589.0');

-- Rule: IfcSlab_Erdgeschoss (9 instances, avg 4656.0x5515.0x127.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcSlab_Erdgeschoss', 'IfcSlab', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcSlab on Erdgeschoss: 9 instances, avg W=4656.0 D=5515.0 H=127.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '4656.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '5515.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '127.0');

-- Rule: IfcDoor_Erdgeschoss (8 instances, avg 444.0x826.0x2150.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Erdgeschoss', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Erdgeschoss: 8 instances, avg W=444.0 D=826.0 H=2150.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '444.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '826.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2150.0');

-- Rule: IfcWindow_Obergeschoss (8 instances, avg 540.0x853.0x1692.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWindow_Obergeschoss', 'IfcWindow', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWindow on Obergeschoss: 8 instances, avg W=540.0 D=853.0 H=1692.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '540.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '853.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1692.0');

-- Rule: IfcDoor_Obergeschoss (7 instances, avg 621.0x481.0x2146.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcDoor_Obergeschoss', 'IfcDoor', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcDoor on Obergeschoss: 7 instances, avg W=621.0 D=481.0 H=2146.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '621.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '481.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2146.0');

-- Rule: IfcRailing_Erdgeschoss (7 instances, avg 553.0x1330.0x1000.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcRailing_Erdgeschoss', 'IfcRailing', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcRailing on Erdgeschoss: 7 instances, avg W=553.0 D=1330.0 H=1000.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '553.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1330.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1000.0');

-- Rule: IfcRailing_Dachgeschoss (5 instances, avg 324.0x1296.0x1000.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcRailing_Dachgeschoss', 'IfcRailing', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcRailing on Dachgeschoss: 5 instances, avg W=324.0 D=1296.0 H=1000.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '324.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1296.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1000.0');

-- Rule: IfcRailing_Obergeschoss (4 instances, avg 1033.0x1015.0x1000.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcRailing_Obergeschoss', 'IfcRailing', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcRailing on Obergeschoss: 4 instances, avg W=1033.0 D=1015.0 H=1000.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1033.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '1015.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1000.0');

-- Rule: IfcWall_Dachgeschoss (4 instances, avg 3762.0x4848.0x2035.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Dachgeschoss', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Dachgeschoss: 4 instances, avg W=3762.0 D=4848.0 H=2035.0mm',
--     'AC9_HausGH');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '3762.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '4848.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2035.0');


