# DONE a75962f4
# Implement placeSet() — Batch Placement from Category

**Priority:** GAP-DS-1. placeItem() places one item; placeSet() places N
items from a category in one call. Thin bridge — UI calls this, backend
does the work.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** placeItem() exists. placeSet() loops over it
with category-filtered products. Don't reinvent.

## Read first

1. `BonsaiBIMDesigner/src/main/java/com/bim/designer/api/DesignerAPI.java`
   — find `placeItem()` signature and response type.
   — find if `placeSet()` is already declared (it may be in the interface
   but not implemented).
2. `BonsaiBIMDesigner/src/main/java/com/bim/designer/api/DesignerAPIImpl.java`
   — find `placeItem()` implementation. Understand what it does.
   — find the Selection Cascade / category query methods.
3. `docs/BIM_Designer_SRS.md` §2 — UX-F-25 placeSet spec.

## Task: Implement placeSet()

If `placeSet()` is declared in the interface but stubbed in the impl:
- Read the category from the request
- Query matching products (same pattern as Selection Cascade)
- Loop: call placeItem() for each product
- Return aggregate response (count placed, any errors)

If `placeSet()` is NOT in the interface:
- Add it to DesignerAPI with a simple record for request/response
- Implement in DesignerAPIImpl
- Wire in DesignerServer action dispatch if server exists

### Design: UI is thin bridge

The UI (Bonsai panel) calls `placeSet(categoryValue, targetFloor)` where
`categoryValue` is the TEXT search key (e.g. "LI", "GF") from
`M_Product_Category.Value` — NOT the INTEGER `M_Product_Category_ID`.
Lookups use `WHERE Value = ?` per iDempiere convention (see DATA_MODEL.md). All logic
lives in Java. The Python side is a one-liner HTTP call. Do NOT put
filtering, looping, or BOM logic in Python.

### What NOT to do

- Do NOT modify placeItem() — reuse it
- Do NOT add new tables
- Do NOT change the compilation pipeline

## Test

Add `PlaceSetTest.java` or extend an existing placement test.
Witness: W-PLACE-SET-1 — place 3+ items from a category in one call.

## Verify

1. `mvn compile -q` — PASS
2. New test passes
3. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS

## When Done

Prepend `# DONE` + commit hash to this file's first line.
