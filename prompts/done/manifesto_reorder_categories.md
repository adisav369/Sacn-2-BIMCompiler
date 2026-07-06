# DONE — Fix MANIFESTO.md — Reorder + Product Category Hierarchy
> Commit: 4ad0ec5 [S68e]

You are editing docs/MANIFESTO.md in bim-compiler. No code changes.

Read first:
1. docs/MANIFESTO.md (current state)
2. docs/BOMBasedCompilation.md §1 (entity mapping)
3. docs/ProjectOrderBlueprint.md §3 (abstract category tree)

## TASK 1: Reorder sections

Move "The Three Concerns" section BEFORE "The Application Dictionary
Heritage" section. Simple concepts first, then complex AD patterns.

## TASK 2: Expand the WHAT concern with M_Product_Category hierarchy

The current WHAT row just says "Which products, how many". Expand with
the product category cascade. IMPORTANT: There is only ONE C_DocType =
"Construction Order". DocBaseType/DocSubType are classification metadata
on that single doc type — provenance tags (WHO extracted), not routing.

The actual product classification lives on M_Product_Category:

```
M_Product_Category (discipline)  → ARC, STR, FP, ELEC, ACMV, SP, CW, LPG
M_Product_Category (room)        → LIVING, KITCHEN, BEDROOM, BATHROOM, CORRIDOR, OFFICE
M_Product_Category (infra)       → ROAD, RAIL, TRK, GEO, SUP, DCK, ABT
M_Product (leaf)                 → the actual element with geometry
```

Use the Patio Furniture Set style — concrete, not abstract.

## TASK 3: Category population triage table

Query the BOM databases to check coverage. NOTE: Most BOM databases still
use the old column name `bom_category` (DV017 migration only applied to
SH so far). Query both column names.

For vertical (BOM levels): BUILDING → FLOOR → ROOM → SET → ITEM
Which buildings have which levels populated?

For horizontal (room categories per building):
- SH: LIVING, DINING, MASTER, BATHROOM, GF, RF, CW
- DM: GF, RF, LIVING, KITCHEN, BEDROOM, BATHROOM
- DX: LIVING, DINING, KITCHEN, BEDROOM, MASTER, HU, PR, L1, L2, RF, FN, MS
- FK: CORRIDOR, OFFICE, BATHROOM, BEDROOM, LIVING, KITCHEN, GALLERY, EG, DG
- IN: MEETING, MECHANICAL, SEMINAR, WC, LABORATORY, CORRIDOR, OFFICE

Show gaps: which DocBaseTypes have no room categories? Infrastructure
buildings (BR, RD, RL) use segment categories instead (SUP, GEO, STR, TRK, ROAD).

Commit when done. Deploy with: /home/red1/bim-compiler/.venv/bin/mkdocs gh-deploy

## When Done

Commit with message linking to this prompt. Leave this file for watchdog review.
Watchdog will review and move to prompts/done/.

## WATCHDOG REVIEWED
**S69 Watchdog** — 2026-03-24

All three tasks verified:
1. **Reorder:** Three Concerns (line 88) before AD Heritage (line 155) — PASS
2. **M_Product_Category hierarchy:** Expanded in §WHAT with discipline/room/infra cascade + Patio Set style — PASS
3. **Category population triage:** DocBaseType table with gaps identified (22 RE buildings floor-only, TE needs extraction) — PASS
4. **No M_BomCategory terminology:** grep confirms zero occurrences — PASS
5. **iDempiere wiki backlinks:** 5 wiki links present in AD Heritage section — PASS
