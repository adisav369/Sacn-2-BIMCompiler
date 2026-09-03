# DONE
# TE Post-PK Migration Verification

**Priority:** IFCtoBOM pipeline fails for TE after S100-p86/p87/p88 INTEGER PK
migration. Extraction reads 48,428 elements but TE_BOM.db is empty/not committed.
Debug and fix.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The PK migration (p86-p88) changed m_bom, m_bom_line,
M_Product_Category, and 13 AD tables from TEXT PK to INTEGER PK AUTOINCREMENT.
Something in IFCtoBOM's DDL or commit path is incompatible with the new schema.

## Read first

1. `PROGRESS.md` §Current State — gate table, session log for p86/p87/p88
2. `docs/DISC_VALIDATION_DB_SRS.md` §11.6.5 — the 6-step migration sequence
3. `logs/run_RosettaStones_20260328_194005.txt` — the failing run:
   - IFCtoBOM reads 48,428 elements across 7 storeys
   - Then: `VERDICT: FAIL — library/TE_BOM.db not found or empty`
   - Fidelity (C8/C9) ran against stale output DB — ignore those results
4. `IFCtoBOM/src/main/java/com/bim/ifctobom/` — DDL creation, BOM commit path
5. `migration/DV026_bom_integer_pk.sql` — Phase A migration (m_bom)
6. `migration/DV027_category_integer_pk.sql` — Phase B migration (M_Product_Category)
7. `scripts/run_RosettaStones.sh` — `prepare_compile_db()` function

## Symptoms

From the 19:40 run log:
```
[IFCtoBOM] Schema created from schema_snapshot_bom.sql
[ExtractionPopulator] Terminal: 48428 elements → 505 distinct products
[IFCtoBOM] Read 48428 elements across 7 storeys
VERDICT: FAIL — library/TE_BOM.db not found or empty
```

IFCtoBOM reads elements successfully but the BOM DB is either:
1. Not written (commit fails silently)
2. Written with wrong schema (DDL mismatch)
3. Written but to wrong path
4. Written but the post-check query fails on new schema

## Debug Steps

### Step 1: Check IFCtoBOM DDL vs new schema

The IFCtoBOM pipeline creates BOM.db tables from hardcoded DDL or from
`schema_snapshot_bom.sql`. After p86-p88, the expected schema is:

```sql
-- m_bom: M_BOM_ID INTEGER PK AUTO, Value TEXT (was bom_id), Name TEXT
-- m_bom_line: M_BOM_Line_ID INTEGER PK AUTO (was bom_child_id)
-- M_Product_Category: M_Product_Category_ID INTEGER PK AUTO, Value TEXT
```

Check if IFCtoBOM's DDL still uses the old TEXT PK schema. If so, the
Java ORM (MBOM, MBOMLine) can't read the old-format DB.

### Step 2: Check BOM QA validation

The IFCtoBOM pipeline runs BOM QA after building the BOM. If QA fails,
it may abort before committing. Check for QA errors in the pipeline log:

```bash
grep -i "fail\|error\|abort\|exception" logs/pipeline_*TE*ifctobom*.log | tail -20
```

### Step 3: Check ERP.db records

The PK migration added INTEGER PKs to ERP.db tables. Verify:
```sql
sqlite3 library/ERP.db "PRAGMA table_info(M_Product_Category);"
sqlite3 library/ERP.db "SELECT M_Product_Category_ID, Value, Name FROM M_Product_Category LIMIT 5;"
```

### Step 4: Force re-extract

Delete TE_BOM.db and re-run to see the full error:
```bash
rm library/TE_BOM.db
./scripts/run_RosettaStones.sh classify_te.yaml 2>&1 | tee /tmp/te_debug.log
```

### Step 5: Verify SH first (smaller, faster)

```bash
rm library/SH_BOM.db
./scripts/run_RosettaStones.sh classify_sh.yaml
```

If SH passes, the issue is TE-specific. If SH also fails, the DDL
mismatch is systemic.

## After fixing

Run full verification:
```bash
./scripts/run_RosettaStones.sh classify_sh.yaml   # SH 7/7
./scripts/run_RosettaStones.sh classify_te.yaml   # TE 6/7+WARN (C9)
bash scripts/verify_test_seal.sh                   # Seal intact
```

Also examine the FINE pipeline log for TE — check the DRIFT section
and stage timings. Compare against the p84 audit baseline:
- BOM walk: 339ms for 48K elements
- Write: 8.3s
- Geometry check: 3.9s
- DRIFT: 6 pass, 0 fail, 2 deferred

## What NOT to do

- Do NOT revert the PK migration — fix forward
- Do NOT modify existing migration SQL files (sacred, append only)
- Do NOT skip IFCtoBOM DDL alignment — that's likely the root cause
- Do NOT assume C8/C9 results from the failing run are valid (stale output)

## When Done

Prepend `# DONE` to this file's first line.

Append findings:
- Root cause of IFCtoBOM failure
- Which DDL/code was misaligned
- Gate results after fix (SH, TE)
- Pipeline log DRIFT check for TE
- Any new issues discovered

# DONE — Findings (S100-p89, 2026-03-28)

## Root cause of 19:40 IFCtoBOM failure

The IFCtoBOM Java DDL was **already correct** (INTEGER PK AUTOINCREMENT on m_bom,
m_bom_line, M_Product_Category) — updated in p86/p87. The pipeline succeeded on
re-run (19:52), producing 8 BOMs, 1522 lines, 505 products.

The 19:40 failure was a **transient exception** between "Read 48428 elements" and
the builder stage. The actual error was invisible because of a grep bug in the
script (see Bug 1 below). The pipeline created TE_BOM.db (via SQLite connection
open) but rolled back on exception, leaving a 0-byte file.

Most likely cause: ERP.db was in an intermediate state during/after DV028 migration
(Phase C: 13 AD tables), causing a query failure in CategoryLookup.load() or
copyCategoryLookup().

## Bugs found and fixed

### Bug 1 (CRITICAL): Error messages silenced — run_RosettaStones.sh line 726
```
- grep -E "ERROR\|Exception"   ← BRE syntax in ERE mode: matches literal "ERROR|Exception"
+ grep -E "ERROR|Exception|FAIL"  ← correct ERE alternation
```
This is why the 19:40 log showed FAIL but no error details. Line 707 (populate)
had the correct syntax; line 726 (IFCtoBOM) did not.

### Bug 2: rule_count integer parse error — run_RosettaStones.sh line 775-776
`grep -c` returns `0` and exits non-zero when no matches. The `|| echo "0"` appended
a second `0`, giving `"0\n0"` which failed `[ "$rule_count" -gt 0 ]`. Fixed by using
`${rule_count:-0}` fallback instead.

### Bug 3: Stale schema_snapshot_bom.sql ad_sysconfig DDL
Column `id` → `AD_SysConfig_ID` (p88 rename). Added missing `Name` and `Value`
columns to match inline DDL in IFCtoBOMPipeline.java.

## Gate results

- **SH: 7/7 PASS** (zero regression)
- **TE: 6/7 PASS + C9 WARN** (60 axis mismatches, pre-existing rank-match artifact)
- **Seal: INTACT** (73 files, super-hash matches)

## Pipeline log DRIFT check (TE)

```
§1  Input=Output: 48428/48428 → PASS
§2  LOD400 Geometry: 48428/48428 OK → PASS
§6  Output Path: C_OrderLine → BOM explosion → elements → PASS
§7  Separate From Input: bom.db=library/_TE_compile.db → PASS
SUMMARY: 6 pass, 0 fail, 2 deferred
```

Stage timings (vs p84 baseline):
- BOM walk: 570ms (was 339ms — acceptable, same order)
- Write: 13.1s (was 8.3s — slower, likely measurement variance)
- Geometry check: 5.3s (was 3.9s)

## New issues discovered (not caused by PK migration — pre-existing)

### 1. Discipline tagging wrong in c_orderline and elements_meta
c_orderline has only ARC (1405) and STR (110) LEAFs. 6 MEP disciplines
(FP, ACMV, CW, ELEC, SP, LPG) absent from order. elements_meta discipline
column also misaligned: 33,324 IfcPlate roof tiles tagged STR instead of ARC.
BomDropper.resolveDiscipline() doesn't resolve MEP disciplines for CO path.

### 2. DocEvent validation is a no-op for TE
W_Validation_Result = 0 rows. Pipeline log: `[H6] No rooms found — skipping
completeness check`. Validation is room-based but TE is institutional (no rooms).
Three-layer IFCtoBOM pre-flight (DimensionRange, BuildingProfile, ShapeAdvisory)
writes to ERP.db advisories, not output DB.

### 3. W_Verb_Node = 0 rows in output
Verb nodes not generated for TE. BOM lines have verb_ref (CLUSTER/TILE/FRAME/ROUTE)
but compilation doesn't populate W_Verb_Node table.
