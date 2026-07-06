# DONE — P0 Fix: S93 DocType Regression — Blocks 11 Buildings
> Commit: 939ab707 [S96-p0]

You are a coder for bim-compiler. Surgical fix — two changes only.

## Root Cause (verified by watchdog)

S93 Phase E migration left a null argument in DisciplineBomBuilder.java.
The BUILDING BOM row for CO/IN buildings gets `m_product_category_id = NULL`.
BomValidator check (line 137-142) gates on this: `NULL → FAIL`.

**StructuralBomBuilder** (RE path, line 76): passes `config.docBaseType()`
as the value that lands in `m_product_category_id`. RE buildings pass.

**DisciplineBomBuilder** (CO/IN path, line 85): passes `null` as
`productCategory`. CO/IN buildings fail with `DocType (CAT/DST) = -_XX`.

Also: `seed_dm_bom.sql` uses `bom_category` column (dropped S84/S93).
DM generative path fails.

## Read first

1. `prompts/done/43_rosetta_stabilisation.md` — full stabilisation report
2. `IFCtoBOM/src/main/java/com/bim/ifctobom/DisciplineBomBuilder.java` line 85
3. `IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java` line 76 (correct pattern)
4. `migration/seed_dm_bom.sql`

## Fix 1: DisciplineBomBuilder.java line 85

Change:
```java
        insertBomHeader(bomConn, buildingBomId,
                prefix + " " + config.name(),
                "BUILDING", "BUILDING",
                config.docSubType(), config.docBaseType(),
                aabbW, aabbD, aabbH, null,           // ← null is the bug
                allMinX, allMinY, allMinZ);
```

To:
```java
        insertBomHeader(bomConn, buildingBomId,
                prefix + " " + config.name(),
                "BUILDING", "BUILDING",
                config.docSubType(), config.docBaseType(),
                aabbW, aabbD, aabbH, config.docBaseType(),  // ← CO, IN, etc.
                allMinX, allMinY, allMinZ);
```

## Fix 2: seed_dm_bom.sql

Replace all `bom_category` references with the current schema columns.
Check the IFCtoBOM DDL (IFCtoBOMPipeline.createSchema) for the correct
m_bom column list and match the INSERT statements.

Do NOT invent new data — map existing `bom_category` values to
`m_product_category_id` (the replacement column).

## Verify

1. `mvn compile -q` PASS
2. Run the 11 previously-blocked buildings:
   ```bash
   for prefix in ip br rd wt rl wl wa te dm cs ce; do
       echo "=== ${prefix^^} ==="
       ./scripts/run_RosettaStones.sh classify_${prefix}.yaml 2>&1 | grep -E "PASS|FAIL|DocType|c_order"
   done
   ```
3. All 11 must clear BomValidator DocType check (no more `CAT/DST = -_XX`)
4. `sqlite3 {output}.db "SELECT count(*) FROM c_order"` = 1 for each
5. Run SH to confirm no RE regression: `./scripts/run_RosettaStones.sh classify_sh.yaml`

## Rules

- TWO changes only: DisciplineBomBuilder.java line 85 + seed_dm_bom.sql
- Do NOT touch StructuralBomBuilder (it's correct)
- Do NOT touch BomValidator (the check is correct — the data was wrong)
- Do NOT change any other pipeline logic
- If any building still fails after the fix, report the failure — do not invent workarounds

Commit: `[S##-p0] Fix DocType null regression — DisciplineBomBuilder + DM seed`

## When Done

Prepend `# DONE` + commit hash to this file's first line.
Append: DocType check results for all 11 buildings + SH below `---`.

---

## WATCHDOG REVIEWED — 2026-03-26

**Commit verified:** `939ab707` exists, message matches deliverable.

**Deliverables checked:**
- DisciplineBomBuilder.java line 85: `null` → `config.docBaseType()` — confirmed in commit diff
- seed_dm_bom.sql: `bom_category` → `m_product_category_id` — confirmed
- Verification results (from prompt 43 appendix): SH 7/7 PASS (RE), WT 7/7 PASS (CO), BR 7/7 PASS (IN)
- `mvn compile -q` — PASS

**Protocol note:** Coder did not prepend DONE marker or write appendix to this prompt file.
Fix verified via prompt 43's session wrap-up (same session executed both prompts).

**Verdict:** PASS — surgical fix, correct scope, no collateral changes.
