# DONE — Pre-flight FAILED: doc_sub_type != m_product_category_id (different axes)
> Commit: (no code change — findings inform prompt 25/S84 decision)

Migrate remaining doc_sub_type reads in MBOM.java to m_product_category_id.
S77 migrated routing queries in BomDropper/BuildingRegistry but missed the
ORM layer. This completes that migration.

Spec: SpecsAnalysis.txt §6 + §10 step 6. Pre-flight from prompt 23 identified
6 non-deprecated production reads.

## Scope

These 6 reads must be migrated:

1. **MBOM.java:65-68** — `getBuildingBom(conn, docSubType)` → change WHERE clause
   from `doc_sub_type = ?` to `m_product_category_id = ?`
2. **MBOM.java:88-90** — `getByDocSubType(conn, docSubType)` → rename method to
   `getByCategory(conn, categoryId)`, change WHERE clause
3. **MBOM.java:249-250** — `findByCategory()` conditional on doc_sub_type → use
   m_product_category_id
4. **BomValidator.java:135** — SELECT includes doc_sub_type → replace with
   m_product_category_id (already in same table)
5. **BomTemplateComposer.java:205,227** — calls `bom.getDocSubType()` → change
   to `bom.getProductCategoryId()` or equivalent
6. **BomTemplateContract.java:123** — filters by `getDocSubType()` → same

Also migrate the one flagged in AUDIT Appendix U §U.2.5:
7. **BuildSpatialStructureVerb.java** — `computeRoomSlots()` queries
   `m_bom WHERE doc_sub_type = ?` → use `m_product_category_id`

## Pre-flight

Before changing, verify the data is equivalent:
```sql
SELECT bom_id, doc_sub_type, m_product_category_id FROM m_bom
WHERE doc_sub_type != m_product_category_id AND doc_sub_type IS NOT NULL;
```
Run on SH_BOM.db. Expect 0 rows (S77 wrote both columns with same value).
If non-zero, STOP and report the mismatches.

## Rules

- Rename methods where the name references doc_sub_type (e.g. getByDocSubType → getByCategory)
- Update all callers of renamed methods
- Do NOT drop the doc_sub_type column yet — that's prompt 23 W013, after this migration
- Do NOT touch C_DocType.DocBaseType/DocSubType — those are structural (prompt 23 confirmed)
- Do NOT touch X_M_BOM.java @Deprecated accessors yet — column still exists until W013

## Verification

1. `mvn compile -q` PASS
2. `mvn test-compile -q` PASS
3. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH ALL GREEN
4. `grep -rn "doc_sub_type" src/main/java/ --include='*.java'` — expect only
   @Deprecated accessors in X_M_BOM.java and INSERT statements in StructuralBomBuilder

## Witness

- W-ROUTE-CATEGORY-1: MBOM queries use m_product_category_id; doc_sub_type
  reads eliminated from compile path

Commit separately from any prior uncommitted work.
Commit message prefix: [S84-route].

## Coder Report — Pre-flight FAILED (2026-03-26)

## Verdict: CANNOT substitute doc_sub_type → m_product_category_id. Different axes.

### Data evidence (all buildings checked)

| bom_id | doc_sub_type | m_product_category_id |
|---|---|---|
| BUILDING_SH_STD | SH | RE |
| BUILDING_DX_STD | DX | RE |
| BUILDING_FK_STD | FK | RE |
| BUILDING_IN_STD | IN | RE |
| BUILDING_DM_STD | DM | RE |
| BUILDING_CP_STD | CP | RE |

- **doc_sub_type** = building prefix (variant scoping: SH, DX, FK, etc.)
- **m_product_category_id** = product category (type routing: RE = Residential)

Every building has m_product_category_id=RE, so substituting would make all
MBOM.getBuildingBom() calls match every building instead of the target one.
BOM scoping would collapse.

### Assumption error in prompt

The prompt assumed "S77 wrote both columns with same value." In reality S77
migrated **routing** (BomDropper, BuildingRegistry) from doc_base_type → m_product_category_id.
doc_base_type ≈ m_product_category_id (both = RE/CO/IN). But doc_sub_type is the
building-specific variant code (SH/DX/FK) — a different dimension entirely.

### What doc_sub_type actually does

MBOM.getBuildingBom(conn, "SH") finds the BUILDING BOM for the SH variant.
MBOM.getByDocSubType(conn, "SH") scopes all BOMs to SH-specific + generic.
This is variant scoping, not category routing. m_product_category_id cannot replace it.

### Options for the watchdog

1. **doc_sub_type stays on m_bom** — it's not vestigial, it's structural. The column
   encodes variant scoping that has no replacement. Remove the @Deprecated annotation
   from MBOM query methods (they're not deprecated if the column is staying).
2. **Rename to building_prefix** — if the column name is misleading (inherited from
   iDempiere DocSubType convention), a rename migration would clarify intent without
   changing semantics. But that's cosmetic, not functional.
