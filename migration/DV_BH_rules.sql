-- ════════════════════════════════════════════════════════
-- BH: PCERT Building Hvac (Building_Hvac)
-- Source: DAGCompiler/lib/output/building_hvac.db
-- Generated: 2026-03-28 17:48
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules

-- ifc_class                storey          cnt  avg_W_mm  avg_D_mm  avg_H_mm  min_W_mm  max_W_mm
-- -----------------------  --------------  ---  --------  --------  --------  --------  --------
-- IfcBuildingElementProxy  Unknown         2    1313.0    1342.0    550.0     1000.0    1626.0  
-- IfcFlowTerminal          00 groundfloor  2    500.0     636.0     175.0     300.0     700.0   

-- §2: Material distribution


-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class                discipline  cnt
-- -----------------------  ----------  ---
-- IfcBuildingElementProxy  ARC         2  
-- IfcFlowTerminal          MEP         2  
-- IfcFlowSegment           ARC         1  

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.


