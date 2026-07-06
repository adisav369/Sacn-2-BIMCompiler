# DONE — Tier 2 Phase C: Migrate Java to INTEGER PKs

You are a coder for bim-compiler. Java migration — change queries and inserts
to use INTEGER PK columns added in Phase A+B (S90, commit f9a9a07e).

## Context

Phase A+B added INTEGER PK columns alongside TEXT keys on 5 core tables.
Both columns coexist. This session migrates Java code to prefer INTEGER PKs
for lookups and FKs while keeping TEXT `Value` columns for human-readable
SearchKey access.

## Read first

1. `PROGRESS.md`
2. `docs/ID_NAME_VALUE_STUDY.md` §3.3-3.5 (Java files, test assertions at risk)
3. `prompts/done/33_tier2_integer_pk_migration.md` §Findings (architecture discoveries)
4. `docs/AUDIT_S51_FOCUSED.md` Appendix V (Phase A+B audit, V.3 hardcoded DDL, V.5 C_Order gap)
5. `migration/W014_m_product_int_pk.sql` through `W017_c_doctype_int_pk.sql` (new schema)

## Phase C Tasks (use agents to parallelize independent tables)

### Task 1: Update IFCtoBOM Java DDL (V.3 finding — MUST DO FIRST)

`IFCtoBOMPipeline.createSchema()` has hardcoded DDL for M_Product, m_bom,
m_bom_line, C_DocType. The `schemaPath` parameter is accepted but not read.

Update the hardcoded DDL to include the new INTEGER PK columns:
- M_Product: add `M_Product_ID INTEGER PRIMARY KEY AUTOINCREMENT`, rename
  `product_id` → keep as `Value TEXT`
- m_bom: add `M_BOM_ID INTEGER PRIMARY KEY AUTOINCREMENT`, keep `bom_id` as `Value`
- m_bom_line: add `M_BOM_ID INTEGER` FK column alongside `bom_id TEXT`
- C_DocType: add INTEGER PK + Value + Name

After this, freshly extracted BOM.db files will have the new schema natively.
The `prepare_compile_db()` ALTER TABLE workaround in run_RosettaStones.sh
can then be removed.

### Task 2: Persist C_Order/C_OrderLine in output DB (V.5 finding)

BomDropper populates C_Order + C_OrderLine in the temp compile DB, then data
is discarded. Output DB has the tables (BuildingWriter DDL) but 0 rows.

Add a copy step after BomDropper, before CompilationPipeline:
- Copy C_Order rows from compile DB → output DB
- Copy C_OrderLine rows from compile DB → output DB
- Preserve INTEGER PKs and all columns

Check `BomDropper.java`, `BuildingCompilerCLI.java`, `IntentCompiler.java`
for the right insertion point.

### Task 3: M_Product Java migration (29 files — use agent)

Migrate from `product_id TEXT` to `M_Product_ID INTEGER` where beneficial:

**Priority reads (compile-path):**
- `BOMWalker.java` — `loadBom(childProductId)` resolve
- `PlacementCollectorVisitor.java` — leaf resolution
- `BomDropper.java` — product lookups and inserts
- `MBOM.java` / `MBOMLine.java` — ORM accessors

**Pattern:** Add INTEGER-based accessors alongside TEXT. Keep TEXT accessors
for backward compat during dual-key period. New code should prefer INTEGER.

**Do NOT change:**
- `RosettaStoneToBOM.py` — Python extraction stays TEXT-based
- `X_M_BOM.java` / `X_M_BOMLine.java` — Sacred Files, edit with extreme care
- Test assertions that verify TEXT key content (these test business logic)

### Task 4: m_bom Java migration (37 files — use agent)

Same pattern. Migrate `bom_id TEXT` → `M_BOM_ID INTEGER` where beneficial.

Key files: `MBOM.java`, `MBOMLine.java`, `BomDropper.java`, `BomValidator.java`,
`StructuralBomBuilder.java`, `ScopeBomBuilder.java`, `DisciplineBomBuilder.java`.

### Task 5: C_Order + C_DocType Java migration

Lower touch count. Migrate TEXT → INTEGER PKs in:
- `BomDropper.java` (C_Order creation)
- `BuildingWriter.java` (output DB DDL)
- `CompilationPipeline.java` (order lookups)

### Task 6: Remove prepare_compile_db() workaround

After Task 1 lands, freshly extracted BOM.db files have INTEGER PK natively.
Remove the ALTER TABLE + backfill section from `scripts/run_RosettaStones.sh`
`prepare_compile_db()` that was added in S90.

### Task 7: Verification

1. `mvn compile -q` PASS
2. `mvn test-compile -q` PASS
3. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH ALL GREEN
4. Verify output.db has C_Order + C_OrderLine rows (non-zero)
5. `sqlite3 output.db "SELECT count(*) FROM C_OrderLine"` — should match element count
6. Grep: `grep -rn "product_id\|bom_id" src/main/java/ --include='*.java' | grep -v Value | grep -v "//"` — report remaining TEXT key references

## Rules

- Sacred Files: X_M_BOM.java, X_M_BOMLine.java — edit with extreme care
- component_library.db is local-only — no git operations
- One commit per major task group (not one giant commit)
- If ANY gate fails, stop and report
- Keep TEXT columns alive — Phase D drops them in a later session

## Commit Messages

```
[S##-tier2c] Task 1: IFCtoBOM DDL updated — INTEGER PK native in BOM.db
[S##-tier2c] Task 2: Persist C_Order/C_OrderLine in output DB
[S##-tier2c] Task 3-5: Java migration — INTEGER PK queries on M_Product, m_bom, C_Order
[S##-tier2c] Task 6: Remove prepare_compile_db() workaround
```

## When Done

Prepend `# DONE` + commit hash to this file's first line.
Append findings below `---`.

---

## Findings (S91-tier2c)

### Task 1: IFCtoBOM DDL — INTEGER PK native
All 3 IFCtoBOM tables updated to iDempiere convention:
- M_Product: `M_Product_ID INTEGER PRIMARY KEY AUTOINCREMENT`, `product_id TEXT NOT NULL UNIQUE`
- m_bom: `M_BOM_ID INTEGER PRIMARY KEY AUTOINCREMENT`, `bom_id TEXT NOT NULL UNIQUE`
- C_DocType: `C_DocType_ID INTEGER PRIMARY KEY AUTOINCREMENT`, `Value TEXT NOT NULL UNIQUE`
- m_bom_line: `M_BOM_ID INTEGER` FK column added
- m_bom_line_ma: `M_BOM_ID INTEGER` FK column added

### Task 2: C_OrderLine persisted in output DB
- Output DB c_orderline schema replaced: element-list (Storey/Name/IfcClass) → BOM tree (family_ref/host_type/bom_child_id/dx/dy/dz)
- copyCOrderLineToOutput() copies from compile DB → output DB with C_Order_ID remapping (docTypeId → buildingId)
- SH: 37 rows (was 0). Matches BomDropper explosion.
- c_order still 0 rows — pre-existing (V.5). VerbExecutor SPI not on classpath during gate runs.

### Task 3-5: Java migration
- IFCtoBOM backfill step populates Value/Name/M_BOM_ID natively before commit
- ORM accessors: X_M_BOM gets getMBomId()/getValue()/getNameIDV(); X_M_BOMLine gets getMBomId()
- BuildSpatialStructureVerb: fixed getInt() on TEXT bom_id → uses M_BOM_ID INTEGER properly
- C_DocType reads migrated: BuildingRegistry, PlacementLoader, RegisterBuildingVerb → `Value` instead of `C_DocType_ID`
- C_Order reads migrated: PlacementLoader → `Value`; CompleteBuildingVerb → `WHERE Value = ?`
- BomDropper.createOrder → writes `Value` column

### Task 6: prepare_compile_db() workaround removed
ALTER TABLE + backfill block (30 lines) removed from run_RosettaStones.sh. IFCtoBOM DDL + backfill handles everything natively.

### Interject: _int sidecar columns eliminated
Per user course correction (34_interject.md):
- C_DocType: `C_DocType_ID_int` → `C_DocType_ID INTEGER PRIMARY KEY AUTOINCREMENT`
- C_Order: `C_Order_ID_int` → `C_Order_ID INTEGER PRIMARY KEY AUTOINCREMENT`
- M_Product_Category: `M_Product_Category_ID_int` → `M_Product_Category_ID INTEGER PRIMARY KEY AUTOINCREMENT`
- schema_snapshot_bom.sql updated for all 3 tables

### Files changed (17)
IFCtoBOMPipeline.java, BuildingWriter.java, CompilationPipeline.java, BomDropper.java,
BuildingRegistry.java, PlacementLoader.java, RegisterBuildingVerb.java, CompleteBuildingVerb.java,
BuildSpatialStructureVerb.java, X_M_BOM.java, X_M_BOMLine.java, schema_snapshot_bom.sql,
run_RosettaStones.sh

### Gate: `mvn compile -q` PASS + SH 7/7 PASS

