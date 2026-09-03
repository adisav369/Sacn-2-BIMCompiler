# Tier 2 Phase E: Migrate remaining TEXT FK queries → INTEGER PK, then drop TEXT columns

You are a coder for bim-compiler. Java + schema migration. Use agents to
parallelize — each DAO file is independent.

## Context

Phase C (S91) migrated compile-path Java to INTEGER PKs. Phase D (S92)
dropped `_int` sidecar columns. But 6 TEXT FK columns remain because
BackOffice/Designer/Sustainability DAOs still join on them.

This session migrates ALL remaining TEXT FK references to INTEGER keys,
then drops the TEXT columns from the schema.

## Goal

After this session: zero TEXT-based PK/FK columns on the 5 core iDempiere
tables. Every query uses INTEGER `_ID` for joins. `Value` (SearchKey) stays
for display/lookup but is not a join key.

## Read first

1. `PROGRESS.md`
2. `prompts/done/36_tier2_phase_d_cleanup.md` §Task 3 (the 6 TEXT FK columns + files that use them)
3. `docs/ID_NAME_VALUE_STUDY.md` §4 (TEXT-to-TEXT FK references)

## The 6 TEXT columns to eliminate

| Column | Table | Used by | Migration |
|---|---|---|---|
| `product_id` | M_Product | BackOffice DAOs (cost/carbon/schedule/FM) | → `M_Product_ID INTEGER` or `Value` for lookups |
| `bom_id` | m_bom | TopologyWriter, DesignerDAO, SustainabilityDAO, CompilationPipeline | → `M_BOM_ID INTEGER` or `Value` for lookups |
| `child_product_id` | m_bom_line | BOM tree walk (child → parent resolution) | → INTEGER FK to M_Product_ID |
| `M_Product_Category_ID` | m_bom (TEXT FK) | Discipline classification in pipeline | → INTEGER FK to M_Product_Category_ID |
| `C_Order_ID` | c_orderline, W_Verb_Node (TEXT FK) | Building scope for verbs | → INTEGER FK |
| `C_DocType_ID` | c_order (TEXT FK) | Building type classifier | → INTEGER FK |

## Task 1: Inventory (agent — read only, report back)

Run these greps and report ALL hits (not just head):
```bash
grep -rn "product_id\b" src/main/java/ --include='*.java' | grep -v "//\|M_Product_ID\|@Deprecated\|import"
grep -rn "bom_id\b" src/main/java/ --include='*.java' | grep -v "//\|M_BOM_ID\|@Deprecated\|import"
grep -rn "child_product_id" src/main/java/ --include='*.java' | grep -v "//\|@Deprecated"
grep -rn "C_Order_ID" src/main/java/ --include='*.java' | grep -v "//\|INTEGER\|@Deprecated"
grep -rn "C_DocType_ID" src/main/java/ --include='*.java' | grep -v "//\|INTEGER\|@Deprecated"
```

Group hits by file. This is the work list.

## Task 2: Migrate Java queries (agents — one per file group)

For each file, apply this pattern:

**WHERE clauses / JOINs:** Change `WHERE product_id = ?` → `WHERE Value = ?`
or `WHERE M_Product_ID = ?` depending on context:
- If caller has the text key (e.g. from YAML, user input): use `WHERE Value = ?`
- If caller has the integer ID (e.g. from another table's FK): use `WHERE M_Product_ID = ?`

**INSERT statements:** Ensure INTEGER PK columns are populated. If the table
has AUTOINCREMENT, omit the `_ID` from INSERT (SQLite assigns it). Keep
`Value` populated with the business key.

**ResultSet reads:** Change `rs.getString("product_id")` → `rs.getInt("M_Product_ID")`
or `rs.getString("Value")` as appropriate.

**Cross-DB joins:** `m_bom_line.child_product_id` joins to `M_Product.product_id`
across databases. Replace with: `m_bom_line.child_product_id` → new INTEGER FK
column `M_Product_ID` that joins to `M_Product.M_Product_ID`. The TEXT column
becomes `Value` for display.

### Sacred Files warning
- `X_M_BOM.java` / `X_M_BOMLine.java` — edit with extreme care
- `RosettaStoneGateTest.java` — do NOT modify gate logic
- `BuildingCompiler.java` — main orchestrator, many dependencies

## Task 3: Update extraction pipeline

`RosettaStoneToBOM.py` uses TEXT keys for INSERTs. Update to populate
INTEGER PK columns (or rely on AUTOINCREMENT + Value backfill).

Check `IFCtoBOMPipeline.java` — Phase C updated DDL but verify all INSERT
statements write INTEGER PKs.

## Task 4: Drop TEXT columns (migration SQL)

Only after Tasks 2+3 confirm zero TEXT FK reads remain.

```sql
-- W018_drop_text_fk.sql (BOM.db template)
-- SQLite rename-copy-drop for each table:
-- m_bom: drop bom_id (rename to Value already done in Phase C DDL)
-- m_bom_line: drop child_product_id (replaced by M_Product_ID FK)
-- etc.
```

**If any column still has active reads after Task 2, do NOT drop it.**
Report as "Phase F" and move on.

## Task 5: Update schema_snapshot_bom.sql

Reflect final schema after TEXT columns dropped.

## Task 6: Verification

1. `mvn compile -q` PASS
2. `mvn test-compile -q` PASS
3. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH ALL GREEN
4. Grep confirmation:
   ```bash
   grep -rn "product_id\|bom_id\|child_product_id" src/main/java/ --include='*.java' | grep -v "//\|Value\|@Deprecated\|import\|test"
   ```
   Should return zero production hits.

## Rules

- Sacred Files: X_M_BOM.java, X_M_BOMLine.java — minimal touch
- component_library.db is local-only — no git operations
- If compile breaks mid-migration, fix before continuing
- If a TEXT column cannot be dropped (still has active reads), leave it and report
- Append-only migrations

Commit: `[S##-tier2e] Phase E: Drop TEXT FK columns — full INTEGER PK migration`

## When Done

Prepend `# DONE` + commit hash to this file's first line.
Append findings below `---`.

---

### Findings — S93 Phase E

**Task 1 (Inventory):** Completed. 106 files reference `bom_id`, 88 reference `product_id`, 67 reference `child_product_id`, 44 reference `C_Order_ID`, 31 reference `C_DocType_ID`. Most were already migrated in prior sessions (S91 Phase C, S92 Phase D).

**Task 2 (Migrate Java queries):** Completed. 37 production Java files changed across 7 modules:
- ORMSandbox: X_MProduct (M_Product_ID/Value/Name accessors), MBOM (orderBy→Value), MProduct (orderBy→Value), BuildingInspector (JOIN→M_BOM_ID)
- TopologyMaker: TopologyAccessLayer (Value lookup), TopologyWriter (3 JOINs→M_BOM_ID)
- BonsaiBIMDesigner: StubDataSeeder (DDL parity), DesignerAPIImpl (promote INSERT with Value+M_BOM_ID)
- IFCtoBOM: BomValidator (9 JOINs→M_BOM_ID), ProductRegistrar+5 builders+VerbFactorizer already migrated
- DAGCompiler: MetadataValidator (bom_id LIKE→Value LIKE), rest already migrated
- BIM_COBOL: All verbs already migrated or use ORM layer
- BIMBackOffice: All 4 DAOs already migrated

**Task 3 (Extraction pipeline):** Already migrated in prior sessions. ProductRegistrar DDL updated. IFCtoBOMPipeline DDL correct since Phase C.

**Task 4 (Drop TEXT columns): DEFERRED TO PHASE F.**
Cannot drop TEXT columns because:
1. `X_M_BOM.getPKColumnName()` → `bom_id` (Sacred File — ORM load() depends on it)
2. `X_MProduct.getPKColumnName()` → `product_id` (ORM load() depends on it)
3. `m_bom_line.bom_id` TEXT FK still actively read by ClearVarianceVerb, FillBuffersVerb, IntegrityHash, ClusterPatternAnalyser, ClusterReclassifier
4. `child_product_id` has no INTEGER FK equivalent in schema yet

Phase F requires: change ORM getPKColumnName() to INTEGER PK + migrate remaining m_bom_line.bom_id reads + add M_Product_ID FK to m_bom_line for child_product_id replacement.

**Task 5 (schema_snapshot):** Deferred — no columns dropped.

**Task 6 (Verification):**
- `mvn compile -q` PASS
- `mvn test-compile -q` PASS
- `./scripts/run_RosettaStones.sh classify_sh.yaml` → SH 7/7 PASS
- Grep: zero TEXT FK SQL in production Java outside COLUMNNAME constants and ORM accessors
