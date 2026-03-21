---
name: CP-4 IFC Class Independence
description: CP-4 complete (s46+s48). Geometric half: 23→archetype. Semantic half: 20→product_category. ~17 refs remain as documented exceptions.
type: project
---

CP-4 COMPLETE. Geometric half (s46): 23 IFC class switches → shape_archetype/scale_band. Semantic half (s48): 20 refs → product_category.

**Why:** BBC.md §2.2.1 — compiler must not branch on IFC class (assigned by BIM tool, not geometry). Original audit: 43 geometric + 37 semantic = 80 switch points.

**How to apply:** Geometric decisions use `shape_archetype`/`scale_band` from m_bom_line. Semantic decisions use `ProductCategory.java` (static map mirrors component_types.product_category). ~17 documented exceptions remain: MEPWriter output metadata (traceability, correct per BBC.md), OVERLAP_EXEMPT (category too coarse), P14 IfcSlab (floor vs wall), findPlacement matching (lookup key).
