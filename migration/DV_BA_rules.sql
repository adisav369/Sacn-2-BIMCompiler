-- ════════════════════════════════════════════════════════
-- BA: PCERT Building Architecture (Building_Architecture)
-- Source: DAGCompiler/lib/output/building_architecture.db
-- Generated: 2026-03-26 20:16
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class                storey          cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- -----------------------  --------------  ---  --------  --------  --------  --------  --------
-- IfcWall                  00 groundfloor  4    156.0     3950.0    2926.0    24.0      200.0   
-- IfcBuildingElementProxy  Unknown         2    1313.0    1342.0    550.0     1000.0    1626.0  
-- IfcSlab                  Unknown         2    3100.0    6600.0    3524.0    2400.0    3800.0  

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class                discipline  cnt
-- -----------------------  ----------  ---
-- IfcWall                  STR         4  
-- IfcSlab                  STR         3  
-- IfcBuildingElementProxy  ARC         2  
-- IfcEarthworksFill        ARC         1  
-- IfcFurnishingElement     ARC         1  

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.

-- Rule: IfcWall_00_groundfloor (4 instances, avg 156.0x3950.0x2926.0 mm)
-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,
--     description, provenance)
-- VALUES ('IfcWall_00_groundfloor', 'IfcWall', 'DIMENSION_RANGE', 'WARNING', 1,
--     'IfcWall on 00 groundfloor: 4 instances, avg W=156.0 D=3950.0 H=2926.0mm',
--     'Building_Architecture');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_width_mm', '156.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_depth_mm', '3950.0');
-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)
-- VALUES (last_insert_rowid(), 'typical_height_mm', '2926.0');


