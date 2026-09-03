# DONE — Tier 2 Phase D: Drop _int sidecar columns + TEXT FK cleanup
> Commit: bebfb13c [S92-tier2d]

You are a coder for bim-compiler. Schema cleanup — remove transitional
columns from Phase A+B that Phase C made obsolete. Use agents to parallelize.

## Context

Phase A+B (S90) added `_int` sidecar INTEGER columns alongside TEXT PKs as
a transition scaffold. Phase C (S91) updated IFCtoBOM DDL to use proper
INTEGER PKs natively. The `_int` columns in ERP.db and component_library.db
are now orphaned — no code reads them.

Also: c_order in output DB has 0 rows (VerbExecutor SPI not on classpath
during gate runs). Fix if straightforward.

## Read first

1. `PROGRESS.md`
2. `prompts/done/33_tier2_integer_pk_migration.md` §Findings (Phase A+B sidecar columns)
3. `prompts/done/34_tier2_phase_c_java_migration.md` §Findings (what was migrated)

## Tasks (use agents — these are independent)

### Task 1: Drop _int sidecar columns from ERP.db

These Phase A+B transitional columns must go:
- `M_Product_Category.M_Product_Category_ID_int` → DROP (PK is now INTEGER natively in BOM.db)
- Any other `_int` suffix columns in ERP.db

SQLite rename-copy-drop pattern. New migration: `DV024_drop_int_sidecar.sql`
Apply to `library/ERP.db`.

Verify: `sqlite3 library/ERP.db "PRAGMA table_info(M_Product_Category)"` — no `_int` columns.

### Task 2: Drop _int sidecar columns from component_library.db

Same pattern for component_library.db.
New migration: `CL005_drop_int_sidecar.sql`

Verify: no `_int` columns remain in any table.

### Task 3: Audit remaining TEXT FK columns

Run:
```bash
grep -rn "_int\|product_id\|bom_id" src/main/java/ --include='*.java' | grep -v "//\|@Deprecated\|Value\|test"
```

Report which TEXT FK references remain in production Java. These are Phase D
drop candidates — but only if zero code reads them. If any production code
still joins on TEXT keys, report as "deferred to Phase E" and do NOT drop.

### Task 4: Fix c_order 0 rows in output DB (if straightforward)

Phase C added `copyCOrderLineToOutput()` (37 rows for SH). But c_order
itself has 0 rows. Check why — is VerbExecutor SPI classpath the issue,
or does BomDropper.createOrder() not persist to output DB?

If the fix is a simple copy like C_OrderLine, do it. If it requires SPI
wiring or classpath changes, defer and document.

### Task 5: Update ACTION_ROADMAP Schema Migration Backlog

Mark step 9 (Tier 2) status based on what's done:
- Phase A+B: DONE (S90)
- Phase C: DONE (S91)
- Phase D: DONE (this session) or partial

### Task 6: Verification

1. `mvn compile -q` PASS
2. `mvn test-compile -q` PASS
3. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH ALL GREEN
4. `sqlite3 library/ERP.db "PRAGMA table_info(M_Product_Category)"` — no _int
5. `sqlite3 library/ERP.db "SELECT count(*) FROM M_Product"` — count preserved

## Rules

- component_library.db is local-only — no git operations on it
- Append-only migrations — never modify existing files
- If ANY gate fails, stop and report
- One commit for the cleanup

Commit: `[S##-tier2d] Phase D: Drop _int sidecar columns from ERP.db + CL`

## When Done

Prepend `# DONE` + commit hash to this file's first line.
Append findings below `---`.

---

## Findings (S92)

### Task 1+2: _int sidecar columns DROPPED
- `DV024_drop_int_sidecar.sql` → ERP.db: dropped `M_Product_Category_ID_int` + index. 117 rows preserved.
- `CL005_drop_int_sidecar.sql` → component_library.db: same. 46 rows preserved.
- Only `M_Product_Category` had `_int` columns in either DB. SQLite 3.45 native `DROP COLUMN`.

### Task 3: TEXT FK audit — ALL DEFERRED TO PHASE E
Zero TEXT FK columns can be dropped. All actively used in production:
- `product_id` (M_Product TEXT PK): BackOffice cost/carbon/schedule/FM DAOs
- `bom_id` (m_bom TEXT PK): core BOM pivot key everywhere — TopologyWriter, DesignerDAO, SustainabilityDAO, CompilationPipeline
- `child_product_id` (m_bom_line TEXT FK): fundamental BOM tree walk (child → parent)
- `M_Product_Category_ID` (m_bom TEXT FK): discipline classification in pipeline
- `C_Order_ID` (c_orderline/W_Verb_Node TEXT FK): building scope for verbs
- `C_DocType_ID` (c_order TEXT FK): building type classifier

Phase E requires: migrate all DAO queries to INTEGER keys first, then drop TEXT columns.

### Task 4: c_order 0 rows — DEFERRED
Root cause: `copyCOrderToOutput()` uses `ServiceLoader.load(VerbExecutor.class)` which returns null because BIM_COBOL is not a DAGCompiler dependency (by design — SPI pattern). T16 tamper rule bans raw SQL on `c_order` outside verb layer, blocking a direct-SQL fix. Options:
1. Add BIM_COBOL as DAGCompiler dependency (classpath architecture change)
2. Move the INSERT to ORMSandbox (which DAGCompiler already depends on)
Documented in `copyCOrderToOutput()` javadoc.

### Task 5: ACTION_ROADMAP updated
- IDV-1 gap: Phase A–D DONE, Phase E pending (drop TEXT FK columns)
- Schema Migration Backlog step 9: Phase A+B (S90), C (S91), D (S92) all DONE

### Verification
- `mvn compile -q` PASS
- `mvn test-compile -q` PASS
- `run_RosettaStones.sh classify_sh.yaml` → 7/7 PASS
- ERP.db: no `_int` columns, 117 categories, 2477 products preserved
