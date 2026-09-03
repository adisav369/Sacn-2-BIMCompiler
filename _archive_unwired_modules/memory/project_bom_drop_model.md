---
name: BOM Drop Model — iDempiere OrderLine Pattern
description: S53-S54 architectural direction: C_OrderLine.M_Product_ID → M_Product, IsBOM explosion, no more ENBLOC/WALKTHRU or BOMCategory on OrderLine
type: project
---

S53 hardened specs, S54a wired into code.

**Core pattern (iDempiere):**
- C_OrderLine.M_Product_ID → M_Product (the product being ordered)
- If product IsBOM=Y (has matching m_bom entry) → backend MUST explode recursively
- Frontend sends 1 unexploded parent (thin pipe) OR fully exploded tree (if user modified)
- Backend just copies if already exploded; explodes if not
- No more BOMCategory / DocType / SubType on OrderLine level — all is simple OrderLine/BOM
- No more EN-BLOC / WALK-THRU dichotomy — single compilation path

**Why:** iDempiere ERP alignment. Customers choose product combinations and customize them.
Validation happens server-side (Save button → backend validates → updates status → thin bridge to GUI).

**How to apply:** Any new C_OrderLine code must reference M_Product_ID, not bom_category or host_type.
bomDrop() + explodeBomTree() in DesignerAPIImpl is the explodeBOM(). TC-1 proven: 55 elements.
W002 migration adds M_Product_ID column. BomDropCompileTest is the gate.
