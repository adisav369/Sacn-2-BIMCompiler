# ForgePromotion — Approve Forged Piece to Component Library

**Spec:** `docs/FORGE_SUITE_SRS.md` §9 Part ④
**Depends on:** ForgeMesh (prompt 61)
**Priority:** Phase 6

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Follow existing DocAction=Approve lifecycle.
Do NOT create a new promotion path — reuse the existing one.

## Read first

1. `docs/FORGE_SUITE_SRS.md` §9 Part ④ — what gets promoted
2. `docs/DocAction_SRS.md` — Approve lifecycle (DR→IP→CO→AP)
3. `docs/ProjectOrderBlueprint.md` §4 — BOM template promotion pattern
4. `docs/GEOMETRY_FORGE_SRS.md` §7b — LOD promotion lifecycle
5. component_library.db schema — M_Product, LOD tables

## Task

### A. ForgeResult → M_Product

On Approve, write to component_library.db:
- New `M_Product` row with dimensions from GeometryRecord
- Piece type + parameters stored as provenance metadata
- `ForgeResult.promotable` must be true (all compliance PASS)

### B. User choice: singular LOD vs BOM

- **Singular LOD** (rafter, pipe bend) → one M_Product with geometry dimensions
- **BOM** (dome with 72 panels, multi-flight stair) → M_BOM + M_BOM_Line tree

### C. Reuse gate

After promotion, the next compilation that needs this piece finds it in the
library. No re-forge. Library is cache, forge is authoring.

## What NOT to do

- Do NOT modify ForgeEngine
- Do NOT modify component_library.db schema — use existing M_Product pattern
- Do NOT implement automatic forge-on-miss (pipeline should not auto-forge)

## Verify

1. Forge SLOPE_CUT → Approve → new M_Product in component_library.db
2. Verify promoted product has correct dimensions
3. component_library.db not corrupted (count check)

## Commit message

```
[S##-forge] ForgePromotion — Approve forged piece to component_library.db

DocAction=Approve writes ForgeResult to M_Product. Singular LOD for simple
pieces, BOM tree for compound. Promotable gate. Library becomes cache.
```
