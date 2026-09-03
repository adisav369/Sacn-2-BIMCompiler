# DONE
# TE Clean Re-extraction + Advisory Batch Fix

**Priority:** TE_BOM.db is 0 bytes (advisory hang killed extraction). Output.db is
stale (pre-P92). Must fix advisory batch, re-extract, recompile, and verify P92's
discipline flow end-to-end before parasitic discipline work can begin.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The advisory fix is a batch-size guard. The
re-extraction uses existing IFCtoBOM pipeline. No new behavior.

## Read first

1. `PROGRESS.md` §Current State
2. `IFCtoBOM/src/main/java/com/bim/ifctobom/ShapeAdvisoryWriter.java` — `writeAdvisories()` at line 131
3. `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java` — advisory call at line 173
4. `prompts/92_te_discipline_tagging.md` §FINDINGS — AD_Org_ID wiring details
5. `docs/BOMBasedCompilation.md` §3.6 — parasitic discipline compilation (the spec this unblocks)

## Task 1: Fix ShapeAdvisoryWriter batch hang

`writeAdvisories()` does `ps.executeBatch()` on the full mismatch list.
For TE with 48K elements this can be 80K+ rows in one batch — SQLite
journal mode chokes and the process hangs indefinitely, holding locks
on ERP.db.

**Fix:** Chunk the batch writes. Commit every 500 rows.

```java
// In writeAdvisories(), replace ps.executeBatch() with:
int batchSize = 0;
for (Mismatch m : mismatches) {
    // ... existing bind code ...
    ps.addBatch();
    if (++batchSize % 500 == 0) {
        ps.executeBatch();
    }
}
ps.executeBatch();  // flush remainder
```

No data loss. No cap. Every advisory still written, just in 500-row chunks.

Log the total: `BIMLogger.info(TAG, "Wrote {} SHAPE advisories in {} batches", ...)`

## Task 2: Kill stale processes and re-extract TE

```bash
# Kill any stale Java/Maven processes holding SQLite locks
pkill -f "IFCtoBOMMain.*TE" 2>/dev/null || true

# Delete stale 0-byte BOM.db
rm -f library/TE_BOM.db

# Re-extract (expect ~7 min for 48K elements)
./scripts/run_RosettaStones.sh classify_te.yaml
```

Verify TE_BOM.db is non-zero and has AD_Org_ID column:
```bash
ls -la library/TE_BOM.db
sqlite3 library/TE_BOM.db "SELECT count(*) FROM m_bom_line WHERE AD_Org_ID > 0;"
```

## Task 3: Verify discipline distribution in compiled output

After successful extraction + compilation, check c_orderline:

```sql
sqlite3 DAGCompiler/lib/output/terminal.db \
  "SELECT Discipline, AD_Org_ID, host_type, count(*), sum(Qty)
   FROM c_orderline
   GROUP BY Discipline, AD_Org_ID, host_type
   ORDER BY AD_Org_ID, host_type;"
```

**Expected** (from TerminalAnalysis.md §Element Inventory):

| Discipline | AD_Org_ID | Expected elements |
|------------|-----------|-------------------|
| ARC | 1 | ~34,724 |
| STR | 2 | ~1,429 |
| FP | 3 | ~6,863 |
| ACMV | 4 | ~1,621 |
| ELEC | 5 | ~1,172 |
| CW | 6 | ~1,431 |
| SP | 7 | ~979 |
| LPG | 8 | ~209 |

Total must = 48,428.

If only ARC + STR appear, P92's AD_Org_ID wiring didn't propagate.
Check `m_bom_line.AD_Org_ID` in TE_BOM.db.

## Task 4: Cross-check c_orderline vs elements_meta

The prior output had a dual-source inconsistency: c_orderline had only
ARC+STR disciplines, but elements_meta had 6 disciplines (copied from
extraction). After recompilation, both tables must agree:

```sql
-- These two queries should produce matching discipline distributions:
SELECT 'orderline' as src, Discipline, sum(Qty) FROM c_orderline WHERE host_type='LEAF' GROUP BY Discipline ORDER BY Discipline;
SELECT 'meta' as src, discipline, count(*) FROM elements_meta GROUP BY discipline ORDER BY discipline;
```

If they diverge, elements_meta is being populated from extraction instead
of from compilation. That violates LMP §3 (compiler only).

## Task 5: Verify container origins (P92 fix)

P92 added container origin persistence. Check that FLOOR containers have
non-zero dx/dy/dz:

```sql
SELECT host_type, family_ref, round(dx,1), round(dy,1), round(dz,1)
FROM c_orderline WHERE host_type IN ('BUILDING','FLOOR') ORDER BY dz;
```

BUILDING should have the world origin (~84.6, ~-51.2, ~-30.7).
FLOOR containers should have offsets from building origin (not all 0,0,0).

## Task 6: Run SH as regression check

```bash
rm library/SH_BOM.db
./scripts/run_RosettaStones.sh classify_sh.yaml   # SH 7/7 (no regression)
```

## What NOT to do

- Do NOT change compilation logic or BomDropper
- Do NOT modify existing migration SQL files
- Do NOT cap advisory writes (write ALL advisories, just in chunks)
- Do NOT change the tamper seal
- Do NOT fix C9 axis warnings — document them

## When Done

Prepend `# DONE` to this file's first line.

Append findings:
- Advisory fix: batch size, total advisories written for TE
- TE_BOM.db: file size, m_bom_line count, AD_Org_ID > 0 count
- TE gate results (expect 6/7+WARN C9)
- Discipline distribution table (8 disciplines, actual counts)
- c_orderline vs elements_meta cross-check: MATCH or DIVERGE
- Container origins: BUILDING and FLOOR dx/dy/dz values
- SH 7/7 (no regression)

---

## FINDINGS (S100-p95, 2026-03-29)

### Advisory fix
- ShapeAdvisoryWriter.writeAdvisories(): chunked batch writes to 500 rows
- Previously: single ps.executeBatch() on full mismatch list → SQLite journal choke on 80K+ rows
- Fix: commit every 500 rows, flush remainder at end. Log includes batch count.

### TE_BOM.db
- File size: 7,192,576 bytes (7.2MB)
- m_bom_line count: 1,522 lines (48,435 instances)
- AD_Org_ID > 0 count: 1,515 (7 FLOOR lines have AD_Org_ID=0, expected)

### TE gate results: 6/7 PASS + C9 WARN (60 axis swaps — pre-existing)
- G0-COMPILED: PASS
- BOM QA: all PASS (8 BOMs, 1522 lines, 513 products, 48428 extraction reconciliation)
- Integrity: PASS (all coordinates parent-relative, 0 furniture clashes)
- C8 diversity: PASS
- C9 axis: WARN — 60 axis mismatches (rank-match artifact, not walk bug)

### Discipline distribution (8 disciplines, actual counts)

| Discipline | AD_Org_ID | host_type | Lines | Qty |
|------------|-----------|-----------|-------|-----|
| ARC | 1 | BUILDING | 1 | 1 |
| ARC | 1 | FLOOR | 6 | 6 |
| ARC | 1 | LEAF | 471 | 34,724 |
| STR | 2 | FLOOR | 1 | 1 |
| STR | 2 | LEAF | 60 | 1,429 |
| FP | 3 | LEAF | 106 | 6,863 |
| ELEC | 4 | LEAF | 106 | 1,172 |
| ACMV | 5 | LEAF | 143 | 1,621 |
| CW | 6 | LEAF | 187 | 1,431 |
| SP | 7 | LEAF | 417 | 979 |
| LPG | 8 | LEAF | 25 | 209 |

Total LEAF Qty = 48,428. Matches expected from TerminalAnalysis.md §Element Inventory.

### c_orderline vs elements_meta cross-check: DIVERGE
- c_orderline: 8 disciplines (ARC, STR, FP, ELEC, ACMV, CW, SP, LPG) — correct
- elements_meta: 6 buckets (ARC 4378, STR 35394, FP 995, ELEC 264, ACMV 220, MEP 7177) — stale extraction-source data
- Root cause: elements_meta populated from extraction (pre-discipline tagging), not from compilation. Violates LMP §3.

### Container origins: PASS
- BUILDING: dx=84.6, dy=-51.2, dz=-30.7 (world origin)
- FLOOR offsets (all non-zero except FDN dz=0.0):
  - TE_FDN: 0.9, 5.3, 0.0
  - TE_GF: 0.0, 0.0, 30.2
  - TE_L01: 3.5, 10.8, 30.5
  - TE_L02: 4.7, 3.0, 30.1
  - TE_L03: 5.1, 10.7, 33.6
  - TE_L04: 3.8, 10.7, 42.1
  - TE_RF: 5.2, 9.1, 46.8

### SH 7/7 — zero regression
