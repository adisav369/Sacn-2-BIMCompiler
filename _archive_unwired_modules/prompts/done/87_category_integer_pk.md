# DONE
# M_Product_Category INTEGER PK — Phase B of iDempiere PK Conformance

**Priority:** Migrate `M_Product_Category_ID` from TEXT PK to INTEGER PK
AUTOINCREMENT across ERP.db, all BOM.db files, and 43 Java files. This is
the widest blast radius change in the PK conformance series.

**Prerequisite:** Phase A (m_bom INTEGER PK) is DONE and committed.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Schema migration following the iDempiere
convention established in S90-S92 and continued in Phase A (prompt 86).

## Read first

1. `CLAUDE.md` + `PROGRESS.md` — current state
2. `prompts/86_idempiere_pk_conformance.md` §Task 2 — Phase B spec
3. `docs/DISC_VALIDATION_DB_SRS.md` §11.6.5 — the 6-step migration pattern
4. `ORMSandbox/src/main/java/com/bim/ormsandbox/po/X_M_Product_Category.java`
   — current ORM accessor
5. `DAGCompiler/src/main/java/com/bim/compiler/bom/BomDropper.java` lines
   589-601 — `resolveDiscipline()` switches on TEXT category codes
6. `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java` — DDL
   that creates BOM.db tables with `m_product_category_id TEXT`

## Understanding: Current State

ERP.db `M_Product_Category` schema:
```sql
M_Product_Category_ID TEXT PRIMARY KEY    -- codes like 'RE', 'GF', 'STR'
Name                  TEXT NOT NULL
Value                 TEXT                -- added S83, has unique index
-- 121 rows
```

The TEXT codes ('RE', 'GF', 'LI', 'CO', 'STR', etc.) appear as:
- FK column in `m_bom.m_product_category_id` (BOM.db + ERP.db)
- FK column in `M_Product.m_product_category_id` (ERP.db)
- FK column in `C_OrderLine.m_product_category_id` (output.db)
- String literals in Java: `"RE"`, `"GF"`, `"LI"`, `"CO"` in switch
  statements, SQL WHERE clauses, and equality checks
- `resolveDiscipline()` in BomDropper switches on these TEXT codes

After migration:
```sql
M_Product_Category_ID INTEGER PRIMARY KEY AUTOINCREMENT  -- opaque
Value                 TEXT NOT NULL UNIQUE                -- was the TEXT PK
Name                  TEXT NOT NULL
```

## CRITICAL: Execution Order

These steps MUST execute in this exact order. Doing Java before schema
or skipping re-extraction will produce silent failures.

### Step 1: ERP.db Migration (DV027)

Create `migration/DV027_category_integer_pk.sql`. This migrates
ERP.db's `M_Product_Category` from TEXT PK to INTEGER PK:

```sql
-- Phase B: M_Product_Category INTEGER PK conformance
-- TEXT codes ('RE','GF','STR'...) → Value column
-- New INTEGER PK is opaque surrogate

-- Step 1: Create new table
CREATE TABLE M_Product_Category_new (
    M_Product_Category_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    Value                 TEXT NOT NULL UNIQUE,
    Name                  TEXT NOT NULL,
    Description           TEXT,
    IFC_Class             TEXT,
    SeqNo                 INTEGER DEFAULT 10,
    IsActive              INTEGER DEFAULT 1
);

-- Step 2: Copy data (old TEXT PK → Value)
INSERT INTO M_Product_Category_new (Value, Name, Description, IFC_Class, SeqNo, IsActive)
SELECT M_Product_Category_ID, Name, Description, IFC_Class, SeqNo, IsActive
FROM M_Product_Category;

-- Step 3: Update FK references in ERP.db tables
-- M_Product.m_product_category_id TEXT → INTEGER
-- M_BOM.M_Product_Category_ID TEXT → INTEGER
-- (use subquery: WHERE Value = old_text_value)

-- Step 4: Drop old, rename new
DROP TABLE M_Product_Category;
ALTER TABLE M_Product_Category_new RENAME TO M_Product_Category;
CREATE UNIQUE INDEX idx_m_product_category_value ON M_Product_Category(Value);
```

**Run this migration on `library/ERP.db` and verify:**
```bash
sqlite3 library/ERP.db < migration/DV027_category_integer_pk.sql
sqlite3 library/ERP.db "SELECT M_Product_Category_ID, Value, Name FROM M_Product_Category LIMIT 10"
# Expect: INTEGER ids, old text codes in Value column
```

### Step 2: IFCtoBOM DDL Update

Update `IFCtoBOMPipeline.java` DDL so fresh BOM.db files use INTEGER FK:

- `m_bom`: `m_product_category_id TEXT` → `m_product_category_id INTEGER`
- `m_bom_line`: if any category FK exists, same change
- All INSERT statements that write category values must look up the
  INTEGER ID from ERP.db by Value, not write the TEXT code directly

Also update any IFCtoBOM Java code that reads/writes category:
- `DisciplineBomBuilder.java` — writes m_product_category_id on BUILDING BOM
- `StructuralBomBuilder.java` — may write category
- `ScopeBomBuilder.java`, `FloorRoomBomBuilder.java`, `CompositionBomBuilder.java`
- `BomValidator.java` — reads category

**Pattern:** Where code currently does:
```java
ps.setString(n, "RE");  // TEXT category code
```
Change to:
```java
ps.setInt(n, lookupCategoryId(discConn, "RE"));  // INTEGER FK
```
With a helper that does `SELECT M_Product_Category_ID FROM M_Product_Category WHERE Value = ?`.

### Step 3: Java Code (43 files, ~135 refs)

**43 files across 7 modules.** Group by risk:

**High risk (compilation pipeline — test after these):**
- `BomDropper.java` — `resolveDiscipline()` switches on TEXT codes
- `CompilationPipeline.java` — 10 refs, SQL queries with category
- `BuildingRegistry.java` — 5 refs, root BOM lookup by category
- `BuildingWriter.java` — output DDL, C_OrderLine category column
- `PlacementCollectorVisitor.java` — 4 refs

**Medium risk (Designer/ORM):**
- `DesignerDAO.java`, `DesignerAPIImpl.java`, `OrderMutationService.java`
- `X_M_Product_Category.java`, `X_M_BOM.java`, `MBOM.java`
- `StubDataSeeder.java`, `CalibrationDAO.java`, `WorkOutputDAO.java`

**Lower risk (read-only reports/queries):**
- `CostDAO.java`, `ScheduleDAO.java`, `PortfolioDAO.java`, etc.
- `ColorSchemeEngine.java`, `DimensionQuery.java`, `NlpQueryParser.java`
- BIM_COBOL verbs (6 files)

### KEY TRAP: `resolveDiscipline()`

`BomDropper.resolveDiscipline(String productCategory)` at line 589
switches on TEXT codes:
```java
case "RF", "STR", "SL" -> Discipline.STR;
case "FP" -> Discipline.FP;
// ...
```

After Phase B, `m_product_category_id` is INTEGER in the database. But
`resolveDiscipline()` is called with the **category Value** (the text
code), not the integer ID. This is correct — the function maps business
meaning to discipline. The trap is: whatever reads the category from
the database and passes it to `resolveDiscipline()` must now read
`Value` (TEXT), not `M_Product_Category_ID` (INTEGER).

Check every call site of `resolveDiscipline()`:
```bash
grep -rn "resolveDiscipline\|deriveDiscipline" --include="*.java" | grep -v test | grep -v worktree
```

Each call site that previously read `m_product_category_id` as TEXT
now needs to either:
- Read `Value` from a JOIN to M_Product_Category, OR
- Read the category Value that was already stored as TEXT elsewhere

### Step 4: Delete All BOM.db Files and Re-extract

After Steps 1-3 compile cleanly:

```bash
mvn compile -q   # must pass first

# Delete all per-building BOM databases
rm library/*_BOM.db

# Re-extract all buildings (IFCtoBOM will create new-schema BOM.db)
# The run_RosettaStones.sh script handles extraction + compilation
```

The re-extraction is MANDATORY because:
- Existing BOM.db files have `m_product_category_id TEXT` columns
- The new Java code expects INTEGER FK in those columns
- IFCtoBOM reads category INTEGER ID from ERP.db and writes it to BOM.db
- Without re-extraction: TEXT value in INTEGER column → silent type mismatch

### Step 5: Verify

**Canary (run first):**
```bash
./scripts/run_RosettaStones.sh classify_sh.yaml   # SH 7/7
```

**Core buildings:**
```bash
./scripts/run_RosettaStones.sh classify_fk.yaml   # FK 7/7
./scripts/run_RosettaStones.sh classify_dx.yaml   # DX 6/7+WARN
./scripts/run_RosettaStones.sh classify_te.yaml   # TE 6/7+WARN
```

**Full fleet (Phase B demands this — widest blast radius):**
```bash
for yaml in classify_ba.yaml classify_bh.yaml classify_bs.yaml \
  classify_ca.yaml classify_ce.yaml classify_ch.yaml classify_cl.yaml \
  classify_cp.yaml classify_cs.yaml classify_es.yaml classify_gh.yaml \
  classify_hi.yaml classify_je.yaml classify_js.yaml classify_mo.yaml \
  classify_ni.yaml classify_ra.yaml classify_rm.yaml classify_rs.yaml \
  classify_sc.yaml classify_wa.yaml classify_wb.yaml classify_wi.yaml \
  classify_br.yaml classify_ip.yaml classify_rd.yaml classify_rl.yaml \
  classify_wl.yaml classify_wt.yaml; do
    echo "=== $yaml ===" && ./scripts/run_RosettaStones.sh "$yaml" 2>&1 | tail -3
done
```

**Check FINE logs for INTEGER category flow:**
```bash
grep "BOMDROP\|category" logs/pipeline_*_extracted_*.log | head -20
# Expect: category=INTEGER (not 'RE', 'GF' text)
```

**Check discipline resolution still works:**
```bash
grep "discipline" logs/pipeline_SampleHouse_extracted_*.log
# SH must show ARC+STR+CW (not all-ARC — that means resolveDiscipline broke)
```

**Tamper seal:**
```bash
bash scripts/verify_test_seal.sh
```

### P85 Baseline — Regression Detection

| Signal | Diagnosis |
|--------|-----------|
| SH 7/7 → 0 elements | Category FK JOIN broken |
| SH ARC+STR+CW → all-ARC | `resolveDiscipline()` getting INTEGER not Value |
| Any 7/7 building → 0 elements | Stale BOM.db not re-extracted |
| H6 WARNs count changed dramatically | C_OrderLine category column type mismatch |
| DM still GENERATIVE FAIL | Expected (pre-existing, not Phase B) |
| RD/RL still 0 elements | Expected (infra walker gap, not Phase B) |

## What NOT to do

- Do NOT modify existing migration files (sacred — append only)
- Do NOT hardcode INTEGER _ID values — always look up by `WHERE Value = ?`
- Do NOT change `resolveDiscipline()` logic — only change what feeds it
- Do NOT skip the full fleet run — Phase B is the widest blast radius
- Do NOT change AD_Org (already INTEGER PK, already compliant)
- Do NOT proceed to Phase C (AD tables) in this session
- Do NOT do Java changes before ERP.db migration — the FK targets must
  exist before code that references them
- Do NOT skip re-extraction — existing BOM.db files have TEXT category FKs

## When Done

Prepend `# DONE` + commit hash to this file's first line.

Append findings:
- DV027 migration result (row count, sample data)
- How many Java files changed, across which modules
- IFCtoBOM DDL changes
- `resolveDiscipline()` — how call sites were updated
- Gate results: SH (must be 7/7 with ARC+STR+CW), FK, DX, TE
- Full fleet summary vs P85 baseline (24 ALL GREEN, 7 C9 WARN, etc.)
- Any TEXT category references remaining (`grep "= 'RE'\|= 'GF'" --include="*.java"`)
- Re-extraction confirmation (all 34+TE BOM.db files rebuilt)

## Findings

### DV027 Migration
- 121 rows migrated (TEXT PK → Value, INTEGER PK AUTOINCREMENT)
- FK updated in M_Product (2104 rows), M_BOM (1 row), M_BOM_Line (3 rows)
- Zero orphans after migration

### Java Files Changed
- **IFCtoBOM** (8 files): CategoryLookup.java (NEW), IFCtoBOMPipeline.java (DDL + copyCategoryLookup), DisciplineBomBuilder, StructuralBomBuilder, FloorRoomBomBuilder, ScopeBomBuilder, CompositionBomBuilder — all builders accept CategoryLookup, write INTEGER via setInt()
- **ORMSandbox** (4 files): X_M_Product_Category (INTEGER PK + Value accessor), X_M_BOM (getProductCategory resolves INT→TEXT via SQL, setProductCategory resolves TEXT→INT), MBOM (3 category queries use subquery), MCDocType (resolveProductCategory JOINs M_Product_Category)
- **DAGCompiler** (4 files): BomDropper (findBuildingBom subquery), BuildingRegistry (JOIN mpc), CompilationPipeline (room children JOIN), BuildingWriter (DDL stays TEXT for C_OrderLine)
- **BonsaiBIMDesigner** (5 files): DesignerDAO (7 queries), CalibrationDAO, WebUIServer (2 queries), DesignerAPIImpl (SELECT + INSERT subquery)
- **BIMBackOffice** (9 files): NlpQueryParser, WorkPackageSelector, DimensionQuery, ColorSchemeEngine, PortfolioDAO, SustainabilityDAO, CostDAO, FacilityMgmtDAO, ScheduleDAO — all JOIN M_Product_Category
- **BIM_COBOL** (2 files): BuildSpatialStructureVerb (room query JOIN), VerifyPlacementVerb (room count JOIN)
- **Tests** (6 files): DXPipelineTest, IFCtoBOMGateTest, PrimeRuleWitnessTest, BuildingInspectorTest, SelectionCascadeTest, BomDropConfigureTest
- **Shell** (1 file): run_RosettaStones.sh singularity_check JOIN
- **Schema** (1 file): schema_snapshot_bom.sql (m_bom INTEGER + M_Product_Category table)
- **Total: ~35 files changed across 7 modules**

### IFCtoBOM DDL Changes
- `m_bom.m_product_category_id TEXT` → `INTEGER`
- `M_Product.M_Product_Category_ID TEXT` → `INTEGER`
- NEW: `M_Product_Category` lookup table created in BOM.db (copied from ERP.db)
- `copyCategoryLookup()` copies 121 category rows from ERP.db to each BOM.db

### resolveDiscipline() — Call Sites
- `resolveDiscipline()` itself UNCHANGED (still switches on TEXT codes)
- Call sites feed it TEXT via `bom.getProductCategory()` which now resolves INTEGER→TEXT internally via SQL lookup in X_M_BOM
- BomDropper.insertLine() line 536: `productCategory` comes from `bom.getProductCategory()` → TEXT
- PlacementCollectorVisitor line 138: `childBom.getProductCategory()` → TEXT via accessor

### Gate Results
- **SH 7/7 PASS** — ARC+STR+CW disciplines confirmed (not all-ARC)
- **FK 7/7 PASS**
- **DX 6/7+WARN** (C9 axis — pre-existing)
- **TE 6/7+WARN** (C9 axis — pre-existing)

### Full Fleet Summary (matches P85 baseline exactly)
- **24 ALL GREEN:** BA, BH, BS, CE, CH, CP, CS, ES, GH, JS, MO, RS, WI, BR, IP, WL, WT, IN, SH, FK + 4 more
- **7 C9 WARN:** HI, JE, NI, RA, RM, SC, WB
- **3 FAIL:** CA, CL, WA (pre-existing)
- **1 GENERATIVE FAIL:** DM (pre-existing)
- **2 empty:** RD, RL (infra walker gap)

### TEXT Category References Remaining
- `grep "= 'RE'\|= 'GF'" --include="*.java"` → **zero hits** in source (excluding comments)

### Re-extraction Confirmation
- All 38 BOM.db files deleted and rebuilt
- All BOM.db files now have INTEGER m_product_category_id + M_Product_Category lookup table
- Seal v8: dcb6f191771b565b386b6254291e9b423625d250bab6c99d3df86459e70c376a
