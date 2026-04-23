-- ════════════════════════════════════════════════════════
-- BR: IFC Infra Bridge Sample (Infra_Bridge)
-- Source: DAGCompiler/lib/output/infra_bridge.db
-- Generated: 2026-04-17 09:01
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class   storey   cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- ----------  -------  ---  --------  --------  --------  --------  --------
-- IfcBeam     Unknown  8    4599.0    4095.0    402.0     1969.0    8691.0  
-- IfcMember   Unknown  8    6080.0    5531.0    4084.0    6080.0    6080.0  
-- IfcColumn   Unknown  7    2975.0    4060.0    3140.0    2276.0    3499.0  
-- IfcWall     Unknown  4    17546.0   10390.0   4484.0    17546.0   17546.0 
-- IfcSlab     Unknown  3    6032.0    5517.0    313.0     3916.0    10265.0 
-- IfcRailing  Unknown  2    8719.0    5201.0    956.0     8719.0    8719.0  

-- §2: Material distribution

-- ifc_class                material_name                cnt
-- -----------------------  ---------------------------  ---
-- IfcMember                stone_granite_masonry        8  
-- IfcColumn                stone_granite_masonry        7  
-- IfcBeam                  wood-generic                 6  
-- IfcWall                  stone_granite_masonry        4  
-- IfcBeam                  concrete_reinforced_in-situ  2  
-- IfcRailing               wood-generic                 2  
-- IfcSlab                  concrete_reinforced_prefab   2  
-- IfcBuildingElementProxy  virtual_black                1  
-- IfcSlab                  wood-generic                 1  

-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class                discipline  cnt
-- -----------------------  ----------  ---
-- IfcBeam                  STR         8  
-- IfcMember                STR         8  
-- IfcColumn                STR         7  
-- IfcWall                  STR         4  
-- IfcSlab                  STR         3  
-- IfcRailing               ARC         2  
-- IfcBuildingElementProxy  ARC         1  

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcBeam_Unknown (8 instances, avg 4599.0x4095.0x402.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcBeam_Unknown', 'IfcBeam', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcBeam on Unknown: 8 instances, avg W=4599.0 D=4095.0 H=402.0mm',
--     'Infra_Bridge');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '4599.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '4095.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '402.0');

-- Rule: IfcMember_Unknown (8 instances, avg 6080.0x5531.0x4084.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcMember_Unknown', 'IfcMember', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcMember on Unknown: 8 instances, avg W=6080.0 D=5531.0 H=4084.0mm',
--     'Infra_Bridge');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '6080.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '5531.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '4084.0');

-- Rule: IfcColumn_Unknown (7 instances, avg 2975.0x4060.0x3140.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcColumn_Unknown', 'IfcColumn', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcColumn on Unknown: 7 instances, avg W=2975.0 D=4060.0 H=3140.0mm',
--     'Infra_Bridge');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '2975.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '4060.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '3140.0');

-- Rule: IfcWall_Unknown (4 instances, avg 17546.0x10390.0x4484.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_Unknown', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on Unknown: 4 instances, avg W=17546.0 D=10390.0 H=4484.0mm',
--     'Infra_Bridge');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '17546.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '10390.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '4484.0');

-- Rule: IfcSlab_Unknown (3 instances, avg 6032.0x5517.0x313.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcSlab_Unknown', 'IfcSlab', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcSlab on Unknown: 3 instances, avg W=6032.0 D=5517.0 H=313.0mm',
--     'Infra_Bridge');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '6032.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '5517.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '313.0');


