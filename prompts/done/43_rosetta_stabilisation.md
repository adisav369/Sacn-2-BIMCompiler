# DONE — Rosetta Stone Stabilisation — 4-Tier Gate Verification + C_Order Contract
> Commit: c32e31b1 [S96-docs] (findings committed with docs tightening)

You are a coder for bim-compiler. Testing and verification only. Do NOT
invent new logic, do NOT add features. Extract, compile, verify, report.
The approach is deterministic — every result must be reproducible.

## Goal

Stabilise the Rosetta Stone pipeline so all fundamentals work reliably.
Organise testing into 4 tiers by complexity. Add a C_Order contract check
to every tier. Report results to the appendix — we decide what to fix.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Run the existing pipeline. Query results.
Report findings. Do NOT invent workarounds, do NOT modify pipeline logic,
do NOT add features. If something fails, document the failure precisely
(building, gate, error message, log line) and move on.

## Read first

1. `PROGRESS.md` — gate table, 19/34 ALL GREEN
2. `docs/TestArchitecture.md` §Rosetta Stone Coverage (lines 820-858)
3. `docs/WorkOrderGuide.md` — pipeline config, how to run
4. `scripts/run_RosettaStones.sh` — the test runner

## Task 0: Pre-Check — Regression + Test Inventory (before ANY tier run)

Seven sessions (S89-S95) landed back-to-back: Sacred File edits, PK migration,
PO class deletions, output schema rewiring, ESLine removal. Each session
checked SH only. Nobody ran a full regression.

### 0a. Compile + test-compile gate

```bash
mvn compile -q          # must PASS
mvn test-compile -q     # must PASS
```

If either fails, STOP and report the error. Do not proceed to tier runs.

### 0b. Full test suite

```bash
mvn test -q 2>&1 | tail -30
```

Report: total tests, passed, failed, errors, skipped. List every failure
with class name + test method + error message. Do NOT fix failures — report.

### 0c. Test inventory — what exists, what's broken, what's orphaned

Use agents to scan all test classes:

```bash
find . -name "*Test.java" -path "*/test/*" -not -path "./venv/*" | sort
```

For each test class, report:
- **Module** (DAGCompiler, BIM_COBOL, BonsaiBIMDesigner, etc.)
- **Purpose** (1-line: what does this test verify?)
- **Status**: PASS / FAIL / BROKEN (won't compile) / ORPHANED (tests removed feature)
- **Tier relevance**: which Rosetta tier does it guard? (1/2/3/4/none)

Flag specifically:
- Tests for dropped tables (co_empty_space, wm_empty_storage)
- Tests with `@Disabled` or `assumeTrue(false)`
- Tests that reference `product_id` TEXT (pre-Tier 2 migration)
- Tests with no witnesses (W-xxx) — untraceable

### 0d. Broken test triage

From 0b+0c, classify each broken test:

| Category | Action |
|---|---|
| Tests removed feature (ESLine, co_empty_space) | Candidate for deletion |
| Tests stale schema (TEXT PK, doc_base_type) | Candidate for update |
| Tests real regression from S89-S95 | Must fix before tier runs |
| Tests pre-existing broken (before S89) | Document, defer |

**Report the triage table. Do NOT delete or fix anything — we decide.**

## The 4 Tiers

### Tier 1: Easy Stones (19 buildings, ALL GREEN — regression check)

These already pass. Verify they still pass after S89-S95 changes and that
c_order is populated in each.

```
BH(5) BA(11) BS(16) IP(27) BR(48) RD(53) WT(55) SH(55) JS(61)
RL(73) FK(82) NI(104) WL(114) WB(125) WI(1) MO(3114) RS(4133)
WA(1749) DM(generative)
```

**Run:** `./scripts/run_RosettaStones.sh classify_{prefix}.yaml` for each.

**Verify per building:**
1. All gates PASS (G1-G6, C8, C9 where applicable)
2. `sqlite3 {output}.db "SELECT count(*) FROM c_order"` = 1
3. `sqlite3 {output}.db "SELECT count(*) FROM c_orderline"` > 0
4. `sqlite3 {output}.db "SELECT Value, DocStatus, expected_elements FROM c_order"` — record values

**Report:** Table with building, element count, gate results, c_order row, c_orderline count.

### Tier 2: Medium Stones (10 buildings, known failures — diagnose)

These have specific known issues. Run each and document the EXACT failure.

```
GH(193)  — G3 FAIL only
CE(2110) — G3 FAIL only
CH(3693) — G3 FAIL only
CP(6584) — G3 FAIL only
CA(2586) — G3 FAIL + 2 geom
CS(1078) — G3 + G5 (GEO_) FAIL
RA(442)  — G5 FAIL (1 GEO_)
RM(6787) — C9 FAIL (2 door depths)
IN(699)  — should be ALL GREEN (verify)
DX(1099) — should be ALL GREEN (verify)
```

**For each G3 FAIL:** Report the digest mismatch — is it a rebaseline
candidate (deterministic output that changed due to code changes S89-S95)
or a real bug?

**For each G5 FAIL:** Report which elements have GEO_ hash prefix.
These are parametric fallbacks that should be library LODs.

**Verify c_order populated for each.**

### Tier 3: Infrastructure Stones (3 buildings — domain verification)

```
BR(48)  — Bridge
RD(53)  — Road
RL(73)  — Rail
```

These use IFC4X3 infrastructure entities. Verify:
1. Gates pass (G1-G3 only — no G5/C8/C9 for infra)
2. c_order populated with correct M_Product_Category (IN)
3. BOM tree makes structural sense (FACILITY → SEGMENT → element)

### Tier 4: Terminal (1 building — stress test)

```
TE(48428) — Terminal complex
```

The largest compilation. Run separately because it takes significant time.

**Verify:**
1. All gates PASS (G1-G6, C8, C9)
2. c_order populated
3. c_orderline count matches expected (should be close to 48428)
4. Element count: `sqlite3 output.db "SELECT count(*) FROM elements_meta"` = 48428
5. Verb expansion: recipe lines vs placement instances ratio

## BOM Model Quality Check (applies to ALL tiers)

For every building, query the BOM.db and verify the extraction produced
a clean, abstract BOM tree — not just raw geometry dumps.

```sql
-- 1. BOM tree depth (should be 2-5 levels, not flat)
WITH RECURSIVE walk AS (
  SELECT bom_id, 0 AS depth FROM m_bom WHERE bom_type = 'BUILDING'
  UNION ALL
  SELECT l.child_product_id, w.depth + 1
  FROM m_bom_line l JOIN walk w ON l.bom_id = w.bom_id
  JOIN m_bom b ON b.bom_id = l.child_product_id
) SELECT max(depth) AS max_depth, count(*) AS bom_count FROM walk;

-- 2. Product dedup ratio (more products than BOM lines = no dedup)
SELECT count(*) AS products FROM M_Product;
SELECT count(*) AS bom_lines FROM m_bom_line;
-- Ratio: bom_lines / products — higher = better factorisation

-- 3. Verb coverage (factorised vs flat placement)
SELECT verb_ref, count(*) AS lines, sum(qty) AS instances
FROM m_bom_line
WHERE verb_ref IS NOT NULL
GROUP BY verb_ref;
-- Report: how much is verb-compressed vs flat qty=1

-- 4. PHANTOM/BUFFER lines (gap-fill health)
SELECT count(*) FROM m_bom_line WHERE is_phantom = 1;
-- Non-zero = BUFFER fill working. Zero for small buildings is OK.

-- 5. M_Product_Category distribution (is classification abstract?)
SELECT m_product_category_id, count(*) FROM M_Product
GROUP BY m_product_category_id ORDER BY count(*) DESC;
-- Should show meaningful categories (ARC, STR, FP), not just one blob

-- 6. Discipline coverage (AD_Org_ID populated?)
SELECT AD_Org_ID, count(*) FROM m_bom
WHERE AD_Org_ID IS NOT NULL GROUP BY AD_Org_ID;
```

**Report per building:** max BOM depth, product count, BOM line count,
dedup ratio, verb coverage %, PHANTOM count, category distribution,
discipline count. Flag any building where:
- BOM depth = 1 (flat, no hierarchy)
- Dedup ratio < 1.5 (no factorisation)
- Zero verb_ref lines on buildings > 100 elements
- All products in one category (no classification)
- AD_Org_ID all NULL (no discipline routing)

These are signs the IFCtoBOM extraction needs additional construction rules.
**Do NOT fix them — report for our decision.**

## C_Order Contract (applies to ALL tiers)

For every building that compiles, verify:

```sql
-- 1. C_Order exists
SELECT count(*) FROM c_order;              -- must be 1

-- 2. C_Order has correct metadata
SELECT Value, Name, DocStatus,
       expected_elements, C_DocType_ID
FROM c_order;                              -- Value = buildingId, DocStatus = 'IP'

-- 3. C_OrderLine has BOM tree
SELECT count(*) FROM c_orderline;          -- must be > 0

-- 4. C_OrderLine references valid products
SELECT count(*) FROM c_orderline
WHERE family_ref IS NOT NULL;              -- should be > 0 (product refs)
```

If any building fails the contract, report it but do NOT fix it in this session.

## Logging

The pipeline logging should support levels. Check if `BIMLogger` (docs/BIMLogger.md)
is configured. For this session:

- **ERROR**: gate failures, missing tables, SQL errors
- **WARNING**: c_order contract violations, unexpected zero counts
- **INFO**: gate PASS results, building summary (elements, gates, c_order status)
- **FINE**: individual element checks, verb expansion details

If BIMLogger is not wired, use standard Java logging and report the current
logging state. Do NOT rewire the logging system — just document what exists.

## Test Documentation

For each test/verification script you run, ensure the output includes:

```
=== TIER {N}: {BUILDING} ({elements} elements) ===
[INFO]  G1-COUNT:      PASS (expected={n}, actual={n})
[INFO]  G3-DIGEST:     PASS (hash={sha256})
[INFO]  G5-PROVENANCE: PASS (0 GEO_ hashes)
[INFO]  C_ORDER:       PASS (1 row, DocStatus=IP, expected={n})
[INFO]  C_ORDERLINE:   PASS ({n} rows)
--- or ---
[ERROR] G3-DIGEST:     FAIL (expected={old}, actual={new})
[WARN]  C_ORDER:       FAIL (0 rows — pipeline did not populate)
```

## Rules

- **EXTRACT OR COMPILE ONLY.** Do not invent. Do not fix. Report.
- Run the existing `run_RosettaStones.sh` — do not write a new test runner
- If a building fails to compile (not just gate fail — actual crash), report
  the stack trace and skip to the next building
- Do NOT modify gate logic in RosettaStoneGateTest.java (Sacred File)
- Do NOT modify BOM.db files, component_library.db, or ERP.db
- Do NOT rebaseline G3 digests — just report if they changed
- Tier 1 is the priority — if time runs short, complete Tier 1 fully
  before starting Tier 2

## Output

Append all findings below `---` as:

```
# Appendix: Rosetta Stone Stabilisation Report

## Tier 1: Easy Stones (19 buildings)
| Building | Elements | G1 | G2 | G3 | G5 | C8 | C9 | c_order | c_orderline | Notes |
|----------|----------|----|----|----|----|----|----|---------|-------------|-------|
| SH       | 55       | PASS | ... | ... | ... | ... | ... | 1 | 37 | reference |
...

## Tier 2: Medium Stones (10 buildings)
(same table + failure diagnostics)

## Tier 3: Infrastructure (3 buildings)
(same table)

## Tier 4: Terminal (1 building)
(detailed report)

## C_Order Contract Summary
| Tier | Buildings | c_order OK | c_order FAIL | c_orderline OK | Notes |
|------|-----------|------------|--------------|----------------|-------|

## G3 Rebaseline Candidates
(list buildings where G3 FAIL is deterministic output change, not real bug)

## Logging Assessment
(current BIMLogger state, what works, what's missing)
```

Commit: `[S##-stabilise] Rosetta Stone stabilisation report — 4-tier verification`

## When Done

Prepend `# DONE` + commit hash to this file's first line.

---

# Appendix: Rosetta Stone Stabilisation Report — 2026-03-26

## Task 0: Pre-Check

### 0a. Compile Gate
| Step | Result |
|------|--------|
| `mvn compile -q` | **PASS** |
| `mvn test-compile -q` | **PASS** |

### 0b. Test Suite Summary
**Tests run: 36, Failures: 5, Errors: 17, Skipped: 0** (14 passed)

| Class | Run | F | E | Root Cause |
|-------|-----|---|---|------------|
| W_Verb_NodeTest | 5 | 0 | 0 | — |
| EmptySpaceTest | 3 | 0 | 0 | — |
| OrderLineInterfaceContractTest | 6 | 1 | 0 | Setter count 8 vs expected 7 (S94 added setter) |
| BuildingInspectorTest | 22 | 4 | 17 | Missing ERP tables in test DB (C_DocType, m_bom, m_bom_line, M_Product, ad_room_boundary, ad_room_slot, M_BomCategoryLine, M_BomCategory) |

### 0c. Test Inventory (113 classes, 7 modules)

| Module | Classes | Notes |
|--------|---------|-------|
| BIM_COBOL | 29 | Verb tests, 3 ref dropped tables (co_empty_space, wm_empty_storage_line) |
| BonsaiBIMDesigner | 39 | 6 use product_id TEXT, 1 @Disabled |
| DAGCompiler | 30 | Sacred RosettaStoneGateTest, 4 use product_id TEXT, refs dropped tables |
| IFCtoBOM | 7 | Pipeline tests |
| ORMSandbox | 4 | BuildingInspectorTest (broken), EmptySpaceTest (PASS) |
| TopologyMaker | 2 | BasePO, TopologyBatch |
| tools/sanity-checker | 1 | HouseSanityCheckerTest |

**Flags:**
- **@Disabled (1):** FurnitureGeometryTest — ticketed DX coordinate frame alignment
- **Dropped tables (5 tests):** StTemplatePipelineTest, BuildSpatialStructureVerbTest, VerifyPlacementVerbTest, VoidEmptySpaceVerbTest, RosettaStoneGateTest
- **product_id TEXT (10 tests):** ASIAuthoringTest, DemoHouseTest, SelectionCascadeTest, Schedule5DCostTest, OrderConfiguratorTest, Tier1Test, OrderInheritanceTest, BomDropperOrderIdTest, RemoveCompressTest, (+ partial in others)
- **No W-xxx witnesses (25 tests):** DriftGuardTest, ArchitectureTest, BomChainIntegrityTest, BOMChainMathTest, BuildingRegistryTest, EdgeVertexTest, IntraBOMRelativeTest, MetadataIntegrityTest, RosettaPlacementTest, SpotCheckContractTest, StackedDuplexWitnessTest, TranslationChainTest, LocalCoordTest, AnchorComputationTest, ClassificationYamlTest, DXPipelineTest, IFCtoBOMGateTest, BasePOTest, TopologyBatchProcessTest, HouseSanityCheckerTest, DataIntegrityTest, ExtractedGeometryTruthTest, StructuralCrossCheckTest, JoiningVerbTest, RosettaStoneGateTest (meta)

### 0d. Broken Test Triage

| Category | Tests | Action |
|----------|-------|--------|
| Removed feature (co_empty_space, wm_empty_storage) | 5 tests | Candidate for deletion/update |
| Stale schema (TEXT PK in test DDL) | 10 tests | Candidate for update |
| Real regression S89-S95 (BuildingInspectorTest) | 21 failures | Must fix — test DB missing tables after PK migration |
| Real regression S94 (OrderLineInterfaceContractTest) | 1 failure | Must fix — setter count guard needs update |
| Pre-existing (@Disabled) | 1 method | Document, defer |

---

## CRITICAL FINDING: S93 DocType Regression

**Root cause:** `DisciplineBomBuilder.java` line 85 passes `null` as `productCategory` to `insertBomHeader()` for the BUILDING BOM row. The S93 migration renamed `doc_base_type` → `m_product_category_id` in the SQL column but did not fix the null argument at the BUILDING callsite.

**Impact:** All non-RE DocBaseType buildings fail IFCtoBOM QA with `DocType (CAT/DST) = -_XX` (NULL category). This blocks the pipeline before BOM.db is written, producing 0-byte BOM DBs, which cascade into compile failure (`C_DocType must have active building types`).

**Affected buildings (11):** IP, BR, RD, WT, RL, WL, WA (DocBaseType IN/CO), TE (CO), DM (seed SQL stale)

**Previously passing:** All were ALL GREEN before S93. This is a hard regression.

---

## Tier 1: Easy Stones (19 buildings — regression check)

| Building | Elements | DocBase | Gates | c_order | c_orderline | Notes |
|----------|----------|---------|-------|---------|-------------|-------|
| BH | 5 | RE | 7/7 PASS | 1 (IP) | 8 | ✓ |
| BA | 11 | RE | 7/7 PASS | 1 (IP) | 14 | ✓ |
| BS | 16 | RE | 7/7 PASS | 1 (IP) | 14 | ✓ |
| IP | 27 | IN | **FAIL** | 0 | 0 | DocType regression (CAT/DST = -_IP) |
| BR | 48 | IN | **FAIL** | 0 | 0 | DocType regression (CAT/DST = -_BR) |
| RD | 53 | IN | **FAIL** | 0 | 0 | DocType regression (CAT/DST = -_RD) |
| WT | 55 | CO | **FAIL** | 0 | 0 | DocType regression (CAT/DST = -_WT) |
| SH | 58 | RE | 7/7 PASS | 1 (IP) | 37 | ✓ reference |
| JS | 61 | RE | 7/7 PASS | 1 (IP) | 57 | ✓ |
| RL | 73 | IN | **FAIL** | 0 | 0 | DocType regression (CAT/DST = -_RL) |
| FK | 82 | RE | 7/7 PASS | 1 (IP) | 94 | ✓ |
| NI | 104 | RE | 7/7 PASS | 1 (IP) | 99 | ✓ |
| WL | 114 | CO | **FAIL** | 0 | 0 | DocType regression (CAT/DST = -_WL) |
| WB | 125 | RE | 7/7 PASS | 1 (IP) | 57 | ✓ |
| WI | 1 | RE | 7/7 PASS | 1 (IP) | 3 | ✓ |
| MO | 3114 | RE | 7/7 PASS | 1 (IP) | 257 | ✓ |
| RS | 4133 | RE | 7/7 PASS | 1 (IP) | 36 | ✓ |
| WA | 1749 | CO | **FAIL** | 0 | 0 | DocType regression (CAT/DST = -_WA) |
| DM | — | RE | **FAIL** | 0 | 0 | Generative seed SQL stale (bom_category col dropped S93) |

**Score: 11/19 PASS, 8/19 FAIL**
- All 11 RE buildings pass with c_order contract fulfilled
- All 7 IN/CO buildings fail (DocType regression)
- DM fails (stale seed SQL)

---

## Tier 2: Medium Stones (10 buildings)

| Building | Elements | G1 | G2 | G3 | G5 | C8 | C9 | c_order | c_orderline | Notes |
|----------|----------|----|----|----|----|----|----|---------|-------------|-------|
| GH | 193 | PASS | PASS | **FAIL** | PASS | PASS | PASS | 1 (IP) | 167 | Expected G3 FAIL |
| CE | 2110 | PASS | PASS | **FAIL** | PASS | PASS | PASS | 0 | 0 | Compile fails (no active CE buildings); output DB stale Mar 22 |
| CH | 3693 | PASS | PASS | **FAIL** | PASS | PASS | PASS | 1 (IP) | 81 | Expected G3 FAIL |
| CP | 6584 | PASS | PASS | **FAIL** | PASS | PASS | PASS | 1 (IP) | 76 | Expected G3 FAIL |
| CA | 2586 | PASS | PASS | **FAIL** | PASS | PASS | PASS | 1 (IP) | 122 | Expected G3 + 2 geom |
| CS | 1078 | PASS | PASS | **FAIL** | **FAIL** | PASS | PASS | 1 (IP) | 43 | Expected G3 + G5 |
| RA | 442 | PASS | PASS | PASS | **FAIL** | PASS | PASS | 1 (IP) | 136 | Expected G5 only |
| RM | 6787 | PASS | PASS | **FAIL** | PASS | PASS | **FAIL** | 1 (IP) | 280 | **REGRESSION** — C9 now 9 IfcSlab mismatches (was 2 door depths) |
| IN | 699 | PASS | PASS | **FAIL** | PASS | PASS | PASS | 1 (IP) | 379 | **REGRESSION** — was ALL GREEN; 44 IfcWindow shifted ±11695mm Y |
| DX | 1099 | PASS | **FAIL** | **FAIL** | PASS | PASS | **FAIL** | 1 (IP) | 1035 | **REGRESSION (SEVERE)** — G2+G3+C9(89)+Rotation+Totality all fail |

### G3 Failure Diagnostics

| Building | SpatialDiff | Verdict |
|----------|-------------|---------|
| GH | 180 exact, 13 drift, 0 missing | **Rebaseline candidate** — small drift from verb changes |
| CE | 2110 exact, 0 drift, 0 missing | **Rebaseline candidate** — pure metadata change |
| CH | 3691 exact, 2 shift (conjugate duct pair) | **Rebaseline candidate** — 2 flow segments shifted |
| CP | 6584 exact, 0 drift, 0 missing | **Rebaseline candidate** — pure metadata change |
| CA | 2193 exact, 384 shift, 9 missing, 9 extra | **Real bug** — IfcStairFlight/IfcSlab reclassification |
| CS | 1078 exact, 0 drift, 0 missing | **Rebaseline candidate** — pure metadata change |
| RM | 6769 exact, 10 shift, 8 missing, 8 extra | **Real bug** — 8 IfcStairFlight compiled as IfcSlab |
| IN | 654 exact, 1 drift, 44 shift | **Real bug** — systematic window Y-offset ±11695mm |
| DX | 71 exact, 60 drift, 968 shift | **Real bug (SEVERE)** — mass coordinate system failure |

### G5 Failure Details

| Building | GEO_ count | Elements |
|----------|------------|----------|
| CS | 5/1078 | IfcRoof (Standing Seam Metal Roof) — no LOD mesh in library |
| RA | 1/442 | IfcRoof (SG Metal Panels roof) — no LOD mesh in library |

### Regressions (3 buildings, previously ALL GREEN)

| Building | Failures | Root Cause |
|----------|----------|------------|
| IN (699) | G3 FAIL: 44 IfcWindow shifted ±11695mm in Y | Systematic window placement coordinate bug post-S89 |
| DX (1099) | G2+G3+C9+Rotation+Totality FAIL: 968 shifted, 60 drifted, volume -0.72%, 89 axis swaps | Full coordinate system failure for Duplex |
| RM (6787) | C9 worse: 9 IfcSlab/stair axis swaps (was 2 door depths) | IfcStairFlight→IfcSlab miscompilation |

---

## Tier 3: Infrastructure (3 buildings)

| Building | Elements | IFCtoBOM | Compile | c_order | c_orderline | Notes |
|----------|----------|----------|---------|---------|-------------|-------|
| BR | 48 | **FAIL** (DocType CAT/DST = -_BR) | FAIL | 0 | 0 | Same S93 regression as Tier 1 |
| RD | 53 | **FAIL** (SQLITE_BUSY + DocType) | FAIL | 0 | 0 | Parallel lock + S93 regression |
| RL | 73 | **FAIL** (SQLITE_BUSY + DocType) | FAIL | 0 | 0 | Parallel lock + S93 regression |

### Last Successful Run Data (Mar 22, from pipeline logs)

| Building | BOM Depth | Products | BOM Lines | Instances | Factorization | Categories |
|----------|-----------|----------|-----------|-----------|---------------|------------|
| BR | 3 (BLDG→FLOOR→SET) | 20 | 45 | 67 | 3.2x reuse | ABT,APR,ARC,DCK,GEO,MS,PIR,SIGN,STR,SUP |
| RD | 3 | 16 | 49 | 68 | 3.8x reuse | ARC,CW,GEO,MS,PKG,ROAD |
| RL | 3 | 6 | 10 | 78 | 18.3x reuse | ARC,MS,RAIL,ROAD,TRK |

BOM tree: FACILITY → SEGMENT → element. Infrastructure domain confirmed (no RE/CO categories).

**Issues:** RD has 8 duplicate BOM positions (FAIL). RL has AABB H envelope failure (building 7775mm, floor sum 500mm).

---

## Tier 4: Terminal — TE (48428 elements)

### Pipeline Result: FAIL

| Stage | Result | Detail |
|-------|--------|--------|
| IFCtoBOM extraction | PASS | 48428 elements extracted |
| IFCtoBOM QA | **FAIL** | DocType (CAT/DST) = `-_TE` — same S93 regression |
| BOM.db write | ABORTED | TE_BOM.db = 0 bytes |
| Compile | **FAIL** | C_DocType empty (no active CO buildings) |
| c_order | 0 | Never reached |
| c_orderline | 0 | Never reached |

### BOM Quality (from pipeline log, pre-abort)

| Metric | Value |
|--------|-------|
| BOM depth | 3 (BUILDING → 7 FLOOR → 50 SET) |
| Products | 58 (0 catalog, 58 assembly stubs) |
| BOM lines | 1572 (48485 instances) |
| Factorization | 3.0x lines, **95.9x reuse** |
| Extraction reconciliation | 48428 vs 48428 (delta=0) |
| Verb patterns | 352 (covering 47265 instances) |
| Expansion ratio | 32.0x (instances / LEAF lines) |
| Categories | ACMV, ARC, CW, ELEC, FN, FP, GF, L1-L4, LPG, RF, SP, STR (15 categories) |
| W-TACK-1 | WARN: 471/1515 lines overshoot parent AABB |
| W-BUFFER-1 | WARN: 14/50 SET BOMs balanced (28%) |

### Stale Output DB (Mar 24 vintage)
- `elements_meta`: 48428 rows ✓
- `c_order`: 0 rows (was never populated even on Mar 24)
- `c_orderline`: 0 rows

---

## C_Order Contract Summary

| Tier | Buildings | c_order OK | c_order FAIL | c_orderline OK | Notes |
|------|-----------|------------|--------------|----------------|-------|
| 1 | 19 | 11 | 8 | 11 (all OK have >0) | 8 FAIL = DocType regression (7) + DM seed (1) |
| 2 | 10 | 8 | 2 | 8 | CE stale (compile fail), RM has c_order but C9 regression |
| 3 | 3 | 0 | 3 | 0 | All blocked by DocType regression |
| 4 | 1 | 0 | 1 | 0 | Blocked by DocType regression |
| **Total** | **33** | **19** | **14** | **19** | |

---

## G3 Rebaseline Candidates

These buildings have G3 FAIL but SpatialDiff shows 0 missing/extra elements — pure metadata or deterministic output change from S89-S95:

| Building | Elements | SpatialDiff | Confidence |
|----------|----------|-------------|------------|
| CE | 2110 | 2110 exact | HIGH — pure metadata |
| CP | 6584 | 6584 exact | HIGH — pure metadata |
| CS | 1078 | 1078 exact | HIGH — pure metadata |
| GH | 193 | 180 exact + 13 drift | MEDIUM — small drift |
| CH | 3693 | 3691 exact + 2 shift | MEDIUM — conjugate duct pair |

**NOT rebaseline candidates** (real bugs):
- CA: 9 missing + 9 extra (IfcStairFlight→IfcSlab reclassification)
- RM: 8 missing + 8 extra (same pattern)
- IN: 44 IfcWindow shifted ±11695mm
- DX: 968 shifted + 60 drifted (severe coordinate failure)

---

## Logging Assessment

- **BIMLogger:** Not wired. The codebase uses standard Java `java.util.logging.Logger` throughout.
- **Pipeline logging:** `run_RosettaStones.sh` captures stdout/stderr to `logs/run_RosettaStones_*.txt`. IFCtoBOM QA prints structured `[PASS]`/`[FAIL]`/`[WARN]`/`[????]` lines per check.
- **Gate test logging:** JUnit output via Maven Surefire. Gate results embedded in test assertions.
- **What works:** IFCtoBOM QA structured output is comprehensive and parseable. Pipeline log files capture full runs.
- **What's missing:** No unified log level control (ERROR/WARN/INFO/FINE). No BIMLogger configuration. Gate results not emitted in the structured `[INFO] G1-COUNT: PASS` format from the spec.

---

## Action Items (for decision — NOT implemented)

### P0 — Blocks 11 buildings
1. **Fix DisciplineBomBuilder.java** — pass `config.docBaseType()` (not null) as productCategory for BUILDING BOM row
2. **Update seed_dm_bom.sql** — replace `bom_category` with current column name, fix INTEGER PK types

### P1 — Regressions (3 previously ALL GREEN buildings)
3. **DX coordinate system failure** — 968 elements shifted, volume loss, 89 axis swaps. SEVERE.
4. **IN window placement** — 44 IfcWindow shifted ±11695mm in Y. Systematic.
5. **RM stair miscompilation** — 8 IfcStairFlight→IfcSlab. C9 now 9 (was 2).

### P2 — Test suite
6. **BuildingInspectorTest** — test DB missing 8 ERP tables after S93 PK migration
7. **OrderLineInterfaceContractTest** — update setter count from 7→8 (S94 added field)

### P3 — Rebaseline
8. **G3 rebaseline** — CE, CP, CS (high confidence), GH, CH (medium confidence)

### P4 — Cleanup
9. **Dropped-table tests** — 5 tests reference co_empty_space / wm_empty_storage_line
10. **TEXT PK tests** — 10 tests use product_id TEXT in inline schemas
11. **CE compile path** — BuildingRegistry can't find active CE buildings

---

## Session Wrap-Up — S96 Audit Trail

### What was done (this session)

**Task 43 — Rosetta Stone 4-Tier Verification (report only, no fixes)**
- Task 0a: `mvn compile -q` PASS, `mvn test-compile -q` PASS
- Task 0b: 36 tests run, 14 passed, 5 failures, 17 errors (2 root causes)
- Task 0c: 113 test classes inventoried across 7 modules
- Task 0d: Broken test triage table (5 categories)
- Tier 1: 11/19 PASS, 8/19 FAIL (S93 DocType regression discovered)
- Tier 2: 3 new regressions (DX severe, IN windows, RM stairs)
- Tier 3: 3/3 FAIL (same DocType regression)
- Tier 4: TE FAIL (same DocType regression, extraction OK: 48428 elements)
- Full appendix report written above

**Task 44 — P0 DocType Regression Fix**
- `DisciplineBomBuilder.java:85` — `null` → `config.docBaseType()` for BUILDING BOM m_product_category_id
- `migration/seed_dm_bom.sql` — aligned DDL to current schema (INTEGER PK, m_product_category_id, C_DocType Value column), 8 INSERT statements updated
- Verified: SH 7/7 PASS (RE), WT 7/7 PASS (CO), BR 7/7 PASS (IN)

**Task 45a — ASI Attribute Detail Tables + Column.Callout Spec**
- `migration/ASI_002_attribute_detail.sql` — 3 new tables (M_Attribute 18 rows, M_AttributeUse 29 rows, M_AttributeValue 15 rows) + M_Product.M_AttributeSet_ID FK
- `docs/DocValidate.md` §1.5 — Column.Callout BIM wiring (dispatch chain, AD_Rule + ASI composition, user workspace explanation)
- `docs/BOMBasedCompilation.md` §3.5.2 — Product → Verb Routing via ASI (PP_Order replacement)
- `docs/BIM_Designer_SRS.md` §31 — ASI Attribute Detail Chain (schema, seed data, user interaction)
- Spec updates: all three specs amended with "Where the User Works" sections clarifying that the product declares vocabulary, the order line is the user's workspace, ASI is the override channel

**Task 45b — TRIM Verb Rewrite Prompt (issued, executed in parallel session)**
- `prompts/45b_trim_verb_rewrite.md` — rewrite TRIM WALLS TO ROOF: measure roof surface don't guess shape, AbstractTrimVerb OOP pattern, sub-verb family (CUT/FILL/CUT FILL), AbstractSpatialVerb broader pattern, callout trigger path, ASI override channel

### Design decisions made (with user)

1. **S93 regression root cause:** `DisciplineBomBuilder` passes null productCategory for BUILDING BOM. Fix: pass `config.docBaseType()`. Unblocks 11 buildings.
2. **TRIM verb insight:** SH has barrel vault roof (not flat). Current tent model is wrong. Verb should measure roof surface above each wall, not guess shape.
3. **OOP verb pattern:** AbstractTrimVerb with detect() + act() hook. Sub-verbs (CUT, FILL, CUT FILL) inherit detection. Registry longest-prefix dispatch handles hierarchy.
4. **Column.Callout as verb trigger:** Generic CalloutSpatial fires on any C_OrderLine change. AD_Rule (data) determines WHEN. ASI on order line (data) determines HOW. Replaces heavy PP_Order routing.
5. **ASI as verb parameter channel:** Product declares vocabulary (M_AttributeSet). Order line carries per-instance overrides (M_AttributeSetInstance). User edits order line, never catalog. Having an attribute doesn't force the verb — it makes it available.
6. **iDempiere attribute chain:** M_Attribute + M_AttributeUse + M_AttributeValue detail tables complete the M_AttributeSet chain. 18 attributes (13 geometric from §28.7 + 5 verb params).

### Files changed

| File | Change |
|------|--------|
| `IFCtoBOM/.../DisciplineBomBuilder.java` | Line 85: null → config.docBaseType() |
| `migration/seed_dm_bom.sql` | DDL + INSERTs aligned to current schema |
| `migration/ASI_002_attribute_detail.sql` | New: 3 tables + FK + seed data |
| `docs/DocValidate.md` | §1.5 Column.Callout BIM wiring |
| `docs/BOMBasedCompilation.md` | §3.5.2 Product → Verb Routing via ASI |
| `docs/BIM_Designer_SRS.md` | §31 ASI Attribute Detail Chain |
| `prompts/43_rosetta_stabilisation.md` | Appendix: full 4-tier report |
| `prompts/45a_asi_callout_wiring.md` | New prompt (executed this session) |
| `prompts/45b_trim_verb_rewrite.md` | New prompt (executed in parallel session) |

### What's next

- P1 regressions: DX (severe), IN (windows), RM (stairs) — need investigation
- P2 test suite: BuildingInspectorTest (21 failures), OrderLineInterfaceContractTest (1)
- P3 rebaseline: 5 G3 digest candidates (CE, CP, CS, GH, CH)
- P4 cleanup: 5 dropped-table tests, 10 TEXT PK tests
- Future: Java wiring for CalloutEngine + ASI resolution at verb dispatch time

## WATCHDOG REVIEWED — 2026-03-26

**Commit verified:** `c32e31b1` [S96-docs] — findings committed with docs tightening.

**Deliverables checked:**
- Task 0 (pre-check): compile PASS, test inventory (113 classes, 7 modules) complete
- Tier 1: 11/19 PASS, 8 DocType regression correctly identified → spawned prompt 44
- Tier 2: 3 regressions (DX severe, IN windows, RM stairs) correctly diagnosed
- Tier 3: 3/3 blocked by DocType — confirmed
- Tier 4: TE blocked by DocType — extraction OK (48428 elements) confirmed
- C_Order contract table complete
- G3 rebaseline candidates identified with confidence levels
- P0 fix (prompt 44) was delivered same session — 939ab707

**Protocol note:** DONE marker present but missing commit hash (added by watchdog).
Session was findings + fix — single session executed prompts 43, 44, 45a, 45b.

**Verdict:** PASS — comprehensive verification, correct root cause analysis,
P0 fix delivered, action items clearly prioritised (P0-P4).

