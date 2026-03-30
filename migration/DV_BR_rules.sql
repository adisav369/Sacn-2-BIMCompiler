-- ════════════════════════════════════════════════════════
-- BR: IFC Infra Bridge Sample (Infra_Bridge)
-- Source: DAGCompiler/lib/output/infra_bridge.db
-- Generated: 2026-03-30 23:14
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class          storey                             cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- -----------------  ---------------------------------  ---  --------  --------  --------  --------  --------
-- IfcMember          railbridge - superstructure        8    6080.0    5531.0    4084.0    6080.0    6080.0  
-- IfcColumn          rail bridge - pier                 4    3499.0    4561.0    3780.0    3499.0    3499.0  
-- IfcEarthworksFill  railbridge - superstructure        4    10410.0   8031.0    4084.0    10410.0   10410.0 
-- IfcFooting         rail bridge - pier                 4    6231.0    7293.0    1000.0    6231.0    6231.0  
-- IfcSign            railbridge - superstructure        4    1401.0    826.0     400.0     1401.0    1401.0  
-- IfcWall            railbridge - superstructure        4    17546.0   10390.0   4484.0    17546.0   17546.0 
-- IfcBeam            road rail bridge - superstructure  3    8691.0    5162.0    300.0     8691.0    8691.0  
-- IfcBeam            road river bridge - pier           3    2260.0    3614.0    400.0     2260.0    2260.0  
-- IfcColumn          road river bridge - pier           3    2276.0    3393.0    2286.0    2276.0    2276.0  
-- IfcFooting         road river bridge - pier           3    4319.0    5380.0    700.0     4319.0    4319.0  
-- IfcBeam            bridge road - abutment             2    1969.0    3215.0    557.0     1969.0    1969.0  
-- IfcRailing         road rail bridge - deck            2    8719.0    5201.0    956.0     8719.0    8719.0  
-- IfcSlab            road rail bridge - approach        2    3916.0    4339.0    441.0     3916.0    3916.0  

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class                discipline  cnt
-- -----------------------  ----------  ---
-- IfcBeam                  STR         8  
-- IfcMember                STR         8  
-- IfcColumn                STR         7  
-- IfcFooting               STR         7  
-- IfcEarthworksFill        ARC         4  
-- IfcSign                  ARC         4  
-- IfcWall                  STR         4  
-- IfcSlab                  STR         3  
-- IfcRailing               ARC         2  
-- IfcBuildingElementProxy  ARC         1  

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcMember_railbridge_-_superstructure (8 instances, avg 6080.0x5531.0x4084.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcMember_railbridge_-_superstructure', 'IfcMember', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcMember on railbridge - superstructure: 8 instances, avg W=6080.0 D=5531.0 H=4084.0mm',
--     'Infra_Bridge');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '6080.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '5531.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '4084.0');

-- Rule: IfcColumn_rail_bridge_-_pier (4 instances, avg 3499.0x4561.0x3780.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcColumn_rail_bridge_-_pier', 'IfcColumn', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcColumn on rail bridge - pier: 4 instances, avg W=3499.0 D=4561.0 H=3780.0mm',
--     'Infra_Bridge');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '3499.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '4561.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '3780.0');

-- Rule: IfcEarthworksFill_railbridge_-_superstructure (4 instances, avg 10410.0x8031.0x4084.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcEarthworksFill_railbridge_-_superstructure', 'IfcEarthworksFill', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcEarthworksFill on railbridge - superstructure: 4 instances, avg W=10410.0 D=8031.0 H=4084.0mm',
--     'Infra_Bridge');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '10410.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '8031.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '4084.0');

-- Rule: IfcFooting_rail_bridge_-_pier (4 instances, avg 6231.0x7293.0x1000.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFooting_rail_bridge_-_pier', 'IfcFooting', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFooting on rail bridge - pier: 4 instances, avg W=6231.0 D=7293.0 H=1000.0mm',
--     'Infra_Bridge');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '6231.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '7293.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '1000.0');

-- Rule: IfcSign_railbridge_-_superstructure (4 instances, avg 1401.0x826.0x400.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcSign_railbridge_-_superstructure', 'IfcSign', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcSign on railbridge - superstructure: 4 instances, avg W=1401.0 D=826.0 H=400.0mm',
--     'Infra_Bridge');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '1401.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '826.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '400.0');

-- Rule: IfcWall_railbridge_-_superstructure (4 instances, avg 17546.0x10390.0x4484.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_railbridge_-_superstructure', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on railbridge - superstructure: 4 instances, avg W=17546.0 D=10390.0 H=4484.0mm',
--     'Infra_Bridge');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '17546.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '10390.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '4484.0');

-- Rule: IfcBeam_road_rail_bridge_-_superstructure (3 instances, avg 8691.0x5162.0x300.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcBeam_road_rail_bridge_-_superstructure', 'IfcBeam', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcBeam on road rail bridge - superstructure: 3 instances, avg W=8691.0 D=5162.0 H=300.0mm',
--     'Infra_Bridge');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '8691.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '5162.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '300.0');

-- Rule: IfcBeam_road_river_bridge_-_pier (3 instances, avg 2260.0x3614.0x400.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcBeam_road_river_bridge_-_pier', 'IfcBeam', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcBeam on road river bridge - pier: 3 instances, avg W=2260.0 D=3614.0 H=400.0mm',
--     'Infra_Bridge');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2260.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3614.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '400.0');

-- Rule: IfcColumn_road_river_bridge_-_pier (3 instances, avg 2276.0x3393.0x2286.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcColumn_road_river_bridge_-_pier', 'IfcColumn', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcColumn on road river bridge - pier: 3 instances, avg W=2276.0 D=3393.0 H=2286.0mm',
--     'Infra_Bridge');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2276.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3393.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2286.0');

-- Rule: IfcFooting_road_river_bridge_-_pier (3 instances, avg 4319.0x5380.0x700.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcFooting_road_river_bridge_-_pier', 'IfcFooting', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcFooting on road river bridge - pier: 3 instances, avg W=4319.0 D=5380.0 H=700.0mm',
--     'Infra_Bridge');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '4319.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '5380.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '700.0');


