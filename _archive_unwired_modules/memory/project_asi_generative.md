---
name: ASI as generative lever
description: AttributeSetInstance (iDempiere pattern) customizes BOM instances — length, angle, material, color — without new products or code. Key to generative scaling.
type: project
---

ASI pattern is the generative customization layer. Same BOM recipe, different per-instance attributes.

**Why:** A building has thousands of instances of the same product type (walls, pipes, doors) but each varies in length, angle, material, finish. Like ERP T-shirts: one SKU, many sizes/colors. The BOM defines WHAT; the ASI defines HOW for each instance.

**How to apply:**
- Selection Cascade (BBC.md §3.5) picks the BOM recipe by category + AABB fit
- C_OrderLine carries the order (WHAT + WHERE via tack dx/dy/dz)
- M_AttributeSetInstance on the OrderLine carries per-instance overrides (length_mm, angle_deg, material, color, finish)
- Resolution: `effective = ASI_override ?? catalog_default`
- Compiler reads effective dimensions, never invents geometry

**Current state (S52):**
- Schema exists: M_AttributeSetInstance + M_AttributeInstance tables (output.db)
- FK wired: C_OrderLine.M_AttributeSetInstance_ID
- ChangeSet.ASI enum value triggers recompile
- BIM_Designer.md §8 documents field resolution matrix
- NOT YET WIRED: WorkOutputDAO doesn't read/write ASI, Java POs don't expose ASI fields
- Spec'd but unimplemented: G4_SRS STEP 3d-iii (create default ASIs for LEAFs)

**Needs focused session to:**
- Derive attribute sets from extracted buildings (what attributes actually vary per instance)
- Seed M_AttributeSet templates for major product types (wall, pipe, door, window, slab)
- Wire WorkOutputDAO to read/write ASI tables
- Wire compiler to resolve effective_dimension = ASI ?? catalog
