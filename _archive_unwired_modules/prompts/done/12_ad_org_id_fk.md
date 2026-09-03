# DONE 80b28a8
# Switch TEXT discipline → AD_Org_ID integer FK

You are a coder for bim-compiler. Schema + Java migration.

Read first:
1. docs/MANIFESTO.md — AD_Org = discipline (WHO is responsible)
2. docs/DISC_VALIDATION_DB_SRS.md §11.6.5 Step 5
3. docs/DATA_MODEL.md §7
4. PROGRESS.md

## Prerequisites

- Prompt 11 DONE — Java routing migrated to M_Product_Category

## Context

Discipline is TEXT throughout the codebase: `"ARC"`, `"STR"`, `"FP"`.
ERP.db (was ERP.db) has `AD_Org` table with integer `AD_Org_ID`.
DV014 already added AD_Org_ID to ERP.db tables (ad_element_mep,
ad_ifc_class_map). This step completes the migration for work_output tables.

## Task

1. **Investigate:** Grep for `Discipline` across all Java files. List every
   class that uses discipline as TEXT.

2. **Migration:** C_OrderLine lives in work_output (W-series).
   Write `migration/W009_orderline_ad_org_id.sql`:
   - Adds `AD_Org_ID INTEGER` to C_OrderLine
   - Backfills from AD_Org lookup:
     `UPDATE C_OrderLine SET AD_Org_ID = (SELECT AD_Org_ID FROM AD_Org WHERE Value = Discipline)`
   - Keep TEXT Discipline column (human readability)

3. **Java:** Update `Discipline.java` enum to include AD_Org_ID field.
   Update PO classes and DAOs to use AD_Org_ID where appropriate.
   Keep backward-compatible TEXT accessors.

## Constraints

- Backward compatibility — existing TEXT discipline code must still work
- Do NOT run tests — code through, `mvn compile -q` at the end
- Append-only migrations
- Pre-flight: `// Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 5`

## When Done

Prepend `# DONE` + commit hash to this file's first line before committing.
Commit with `[S78] AD_Org_ID FK on discipline columns`.

---

# DONE — Coder Appendix (S78)

## Commit: 80b28a8

## Files changed (9)

| File | Change |
|---|---|
| `migration/W009_orderline_ad_org_id.sql` | NEW — `AD_Org_ID INTEGER` on C_OrderLine, backfill via ATTACH erp |
| `Discipline.java` | `adOrgId` field (IDs 1–9 per DV013), `getAD_Org_ID()`, `fromAD_Org_ID()` |
| `X_C_OrderLine.java` | `COLUMNNAME_AD_Org_ID`, getter/setter |
| `BomDropper.java` | Writes `AD_Org_ID` on INSERT, `deriveAD_Org_ID()` helper |
| `OrderMutationService.java` | Writes `AD_Org_ID` on proposed line INSERT, `resolveAD_Org_ID()` |
| `OrderLineWalker.java` | SELECTs `AD_Org_ID`, record field added |
| `BuildingWriter.java` | `AD_Org_ID INTEGER` in output c_orderline DDL |
| `WorkOutputDAO.java` | W009 migration step in `initSchema()` |
| `PROGRESS.md` | Session log + Step 5 DONE |

## Finding: FPR→FP legacy mapping

W003 backfill mapped `bom_category='FP'` → Discipline `'FPR'`. But AD_Org has `Value='FP'` (ID=3). `Discipline.fromString("FPR")` returns null (no such enum). Handled with explicit `"FPR"→"FP"` mapping in:
- `BomDropper.deriveAD_Org_ID()`
- `OrderMutationService.resolveAD_Org_ID()`
- `W009 SQL backfill` (CASE expression)

Step 6 scope: consider normalizing W003 Discipline values from `'FPR'`→`'FP'`.

## Not touched (deferred to Step 6)

- WorkOutputDAO INSERT sites at lines 217, 406, 703 — don't specify Discipline, AD_Org_ID will be NULL
- `elements_meta.discipline` TEXT column in BuildingWriter output DDL — not a C_OrderLine concern
- Test files (AddDisciplineTest, OrderLineMutationTest, OrderInheritanceTest) — TEXT assertions still valid
- 75 files reference Discipline as TEXT — bulk migration is Step 6/7 scope
