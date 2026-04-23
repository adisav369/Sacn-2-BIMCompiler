-- ════════════════════════════════════════════════════════
-- IP: PCERT Infra Plumbing (Infra_Plumbing)
-- Source: DAGCompiler/lib/output/infra_plumbing.db
-- Generated: 2026-04-17 09:01
-- ════════════════════════════════════════════════════════

-- §1: Structural dimensions per (ifc_class, storey)
-- Use: identify typical element sizes for validation rules


-- §2: Material distribution

-- ifc_class                material_name  cnt
-- -----------------------  -------------  ---
-- IfcBuildingElementProxy  virtual_black  1  

-- §3: Spacing patterns (adjacent element gaps)
-- Elements of the same ifc_class on the same storey, sorted by X


-- §4: IFC class inventory

-- ifc_class                discipline  cnt
-- -----------------------  ----------  ---
-- IfcBuildingElementProxy  ARC         1  

-- §5: Candidate validation rules for ERP.db
-- Review and adjust before applying. Rule IDs are placeholders.


