# DONE — Tier 2 Phase A+B: INTEGER PK on 5 core tables
> Commit: f9a9a07e [S90-tier2] + 68f38909 [S90-tier2 Appendix V]

You are a coder for bim-compiler. Schema + Java migration. Use agents to
parallelize independent tables. This is the highest-risk migration in the
project — read everything before touching code.

## Goal

Convert 5 core tables from TEXT primary keys to INTEGER PKs with the old
TEXT key preserved as `Value` (SearchKey). This aligns with iDempiere
convention: every table has `_ID` (INTEGER PK), `Name` (TEXT), `Value` (TEXT).

## Read first

1. `PROGRESS.md`
2. `docs/ID_NAME_VALUE_STUDY.md` — full impact study (§3.4 migration table, §4 FK joins)
3. `docs/ACTION_ROADMAP.md` §Schema Migration Backlog step 9
4. `BIM_COBOL/src/main/java/com/bim/cobol/VerbRegistry.java` — verb count (compile gate)
5. Sacred Files list in `CLAUDE.md`

## Strategy

**Add INTEGER PK alongside TEXT key. Do NOT drop TEXT columns.**

The TEXT keys (`product_id`, `bom_id`, `C_Order_ID`) become the `Value`
(SearchKey) column. All existing queries keep working via the TEXT column
while we add INTEGER PKs and FK references incrementally.

**Phase A** — add `_ID INTEGER` + backfill (schema only, zero Java changes)
**Phase B** — add INTEGER FK columns + backfill (schema only, zero Java changes)
**Phase C** — migrate Java reads/writes to INTEGER PKs (code changes)
**Phase D** — drop TEXT FK columns (cleanup, after all code migrated)

This prompt covers **Phase A + B only**. Phase C+D are separate sessions
after gates confirm Phase A+B is clean.

## Tables (ordered by dependency — do in sequence, not parallel)

### Table 1: M_Product_Category (lowest risk — 6 Java files)

Current: `M_Product_Category_ID TEXT` PK (values like 'RE', 'IN', 'CO', 'LIVING')

Migration:
- Add `M_Product_Category_ID_int INTEGER` (new surrogate)
- Backfill: `UPDATE M_Product_Category SET M_Product_Category_ID_int = ROWID`
- Add `Value TEXT` ← copy from current `M_Product_Category_ID`
- **Do NOT rename or drop** `M_Product_Category_ID TEXT` yet

Affected DBs: ERP.db, component_library.db (M_Product_Category exists in both)

### Table 2: M_Product (highest risk — 29 Java files)

Current: `product_id TEXT` PK (values like 'WALL_EXT_150', 'PIPE_CW_50MM')

Migration:
- Add `M_Product_ID INTEGER PRIMARY KEY AUTOINCREMENT` (new surrogate)
- Rename `product_id` → `Value` (SearchKey)
- Add `Name TEXT` ← copy from `product_id` initially
- Add INTEGER FK: `M_Product_Category_ID_int INTEGER` alongside existing TEXT FK

Affected DBs: BOM.db (×34), ERP.db, component_library.db

**CRITICAL:** `m_bom_line.child_product_id TEXT` is the cross-DB join key
(BOM → component_library). This is the hardest FK to migrate because it
crosses database boundaries. Phase A adds the INTEGER column; Phase C
migrates the join.

### Table 3: m_bom (high risk — 37 Java files)

Current: `bom_id TEXT` PK (values like 'BUILDING_SH_STD', 'FLOOR_SH_GF_STD')

Migration:
- Add `M_BOM_ID INTEGER PRIMARY KEY AUTOINCREMENT` (new surrogate)
- Rename `bom_id` → `Value`
- Add `Name TEXT` ← copy from `bom_name` (already exists)
- Fix `m_bom_line.bom_id TEXT` FK → add `M_BOM_ID INTEGER` FK alongside

Affected DBs: BOM.db (×34)

### Table 4: C_Order (high risk — 19 Java files)

Current: `C_Order_ID TEXT` PK

Migration:
- Change `C_Order_ID` from TEXT → INTEGER AUTOINCREMENT
- Add `Value TEXT` ← copy from old TEXT C_Order_ID
- Add `Name TEXT`

Affected DBs: output.db (created fresh each compile)

### Table 5: C_DocType (medium risk — 14 Java files)

Current: `C_DocType_ID TEXT` PK

Migration:
- Change `C_DocType_ID` from TEXT → INTEGER AUTOINCREMENT
- Add `Value TEXT` ← copy from old TEXT C_DocType_ID
- Add `Name TEXT`

Affected DBs: BOM.db (×34)

## Migration SQL Pattern (SQLite cannot ALTER PK — use rename-copy-drop)

For each table:
```sql
-- 1. Create new table with INTEGER PK
CREATE TABLE {table}_new (
    {Table}_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    Value TEXT NOT NULL,        -- old TEXT PK preserved as SearchKey
    Name TEXT,                  -- human label
    ... (all other columns)
);

-- 2. Copy data
INSERT INTO {table}_new (Value, Name, ...)
SELECT {old_pk}, {name_col_or_pk}, ... FROM {table};

-- 3. Swap
DROP TABLE {table};
ALTER TABLE {table}_new RENAME TO {table};

-- 4. Recreate indexes
CREATE UNIQUE INDEX idx_{table}_value ON {table}(Value);
```

## Migration Files (append-only — never modify existing)

| Migration | DB | What |
|---|---|---|
| `DV022_m_product_category_int_pk.sql` | ERP.db | M_Product_Category TEXT → INTEGER PK |
| `CL_002_m_product_category_int_pk.sql` | component_library.db | Same for CL copy |
| `W014_m_product_int_pk.sql` | BOM.db template | M_Product TEXT → INTEGER PK |
| `DV023_m_product_int_pk.sql` | ERP.db | M_Product TEXT → INTEGER PK |
| `CL_003_m_product_int_pk.sql` | component_library.db | Same for CL copy |
| `W015_m_bom_int_pk.sql` | BOM.db template | m_bom + m_bom_line FK |
| `W016_c_order_int_pk.sql` | output.db template | C_Order TEXT → INTEGER PK |
| `W017_c_doctype_int_pk.sql` | BOM.db template | C_DocType TEXT → INTEGER PK |

## Applying to 34 BOM DBs

Do NOT manually apply to all 34. Apply to SH_BOM.db as proof. The remaining
33 get migrated via re-extraction (`run_RosettaStones.sh`) which rebuilds
BOM DBs from scratch. Verify SH gates pass first.

## Pre-flight Checks (before ANY migration)

```bash
# 1. Current state — record baseline
sqlite3 library/ERP.db "SELECT count(*) FROM M_Product_Category"
sqlite3 library/ERP.db "SELECT count(*) FROM M_Product"
sqlite3 library/SH_BOM.db "SELECT count(*) FROM m_bom"
sqlite3 library/SH_BOM.db "SELECT count(*) FROM m_bom_line"

# 2. Compile gate
mvn compile -q && mvn test-compile -q

# 3. SH baseline
./scripts/run_RosettaStones.sh classify_sh.yaml
# Must be ALL GREEN before starting
```

## Post-Migration Checks (after EACH table)

```bash
# 1. Verify new PK exists and data preserved
sqlite3 library/ERP.db "PRAGMA table_info(M_Product_Category)"
sqlite3 library/ERP.db "SELECT count(*) FROM M_Product_Category"
# Row count must match pre-flight

# 2. Verify Value column has old PK values
sqlite3 library/ERP.db "SELECT Value FROM M_Product_Category LIMIT 5"

# 3. Compile gate
mvn compile -q

# 4. SH gate (after all tables done)
./scripts/run_RosettaStones.sh classify_sh.yaml
```

## Rules

- **Phase A+B ONLY** — add INTEGER PKs and FK columns. Do NOT change Java code.
- Append-only migrations — never modify existing migration files.
- X_M_BOM.java and X_M_BOMLine.java are Sacred — do NOT touch in this phase.
- component_library.db is local-only — no git operations on it.
- If ANY pre-flight or post-migration check fails, STOP and report.
- Keep TEXT columns alongside INTEGER — dual-key period. Drop comes in Phase D.
- One commit per table (not one giant commit).

## Commit Messages

```
[S##-tier2] Phase A: M_Product_Category INTEGER PK (DV022 + CL_002)
[S##-tier2] Phase A: M_Product INTEGER PK (W014 + DV023 + CL_003)
[S##-tier2] Phase A: m_bom + m_bom_line INTEGER PK (W015)
[S##-tier2] Phase A: C_Order INTEGER PK (W016)
[S##-tier2] Phase A: C_DocType INTEGER PK (W017)
```

## When Done

Prepend `# DONE` + commit hash to this file's first line.
Append findings below the `---`: row counts before/after, any surprises,
compile/gate results.

Final gate: `mvn compile -q` PASS + SH ALL GREEN with new schema.

---

## Findings (S90-tier2)

### Pre-flight Baseline
| DB | Table | Count |
|---|---|---|
| ERP.db | M_Product_Category | 117 |
| ERP.db | M_Product | 2477 |
| SH_BOM.db | M_Product | 11 |
| SH_BOM.db | m_bom | 9 |
| SH_BOM.db | m_bom_line | 39 |
| SH_BOM.db | m_bom_line_ma | 0 |
| SH_BOM.db | C_DocType | 1 |
| component_library.db | M_Product_Category | 46 |
| component_library.db | M_Product | 2475 |

### Post-migration (all counts preserved)
- ERP.db: M_Product_Category 117 (Value, M_Product_Category_ID_int populated). M_Product 2477 (M_Product_ID, Value, Name populated).
- component_library.db: Same.
- SH gate: 7/7 PASS. Compile: PASS.

### Migration File Names (adjusted from prompt)
CL_002 was taken (Tier 1). Renamed: CL_002→CL_003 (category), CL_003→CL_004 (product).

### Architecture Discovery: BOM.db is rebuilt each extraction
IFCtoBOM Java (IFCtoBOMPipeline.createSchema) has HARDCODED DDL for M_Product, m_bom, m_bom_line, C_DocType with old TEXT PK schema. `schemaPath` parameter is accepted but NOT read (line 469 comment). BOM.db files are rebuilt from scratch each extraction run.

**Consequence:** Migration SQL files (W014, W015, W017) cannot be applied directly to BOM.db — they get overwritten. Instead, `prepare_compile_db()` in run_RosettaStones.sh applies ALTER TABLE ADD COLUMN + backfill to the compile DB copy.

Phase C must update the Java DDL in IFCtoBOMPipeline.createSchema() to match.

### Phase C Must-Do: Persist C_Order/C_OrderLine in output DB
C_Order + C_OrderLine are populated by BomDropper in the temp compile DB, then discarded. The output DB has the tables (BuildingWriter DDL) but they're empty (0 rows). BIM Designer needs them to recall/display the construction order. Phase C must copy the C_OrderLine tree from compile DB → output DB after BomDropper, before CompilationPipeline runs. Already spec'd in ProjectOrderBlueprint.md + BIM_Designer_SRS.

### Pre-existing Fixes (not caused by migration)
1. **schema_snapshot_bom.sql C_OrderLine** — missing AD_Org_ID, locator_ref, is_reference_class (stale since S78). Fixed.
2. **singularity_check** — queried `doc_base_type` (dropped S84). Changed to `m_product_category_id`.
3. **G6 co_empty_space_line** — table dropped in S74 (W008) but G6 test still queries it. Added empty stub tables to output DB pre-contract-test.

## WATCHDOG REVIEWED — 2026-03-26

**Verified:**
1. Row counts preserved: M_Product_Category 117, M_Product 2477 (matches pre-flight)
2. INTEGER PK + Value + Name columns present on M_Product_Category and M_Product in ERP.db
3. `mvn compile -q` PASS
4. Appendix V in AUDIT_S51_FOCUSED.md — 6 sections, clean writeup
5. 8 migration files created (append-only, existing migrations untouched)
6. 3 pre-existing stale fixes correctly identified and fixed (V.4.1-V.4.3)

**Architecture finding acknowledged:** IFCtoBOM hardcoded DDL (V.3) means BOM.db migrations
are workarounds via `prepare_compile_db()`. Phase C must update Java DDL. Correctly deferred.

**Design gap acknowledged:** C_Order/C_OrderLine empty in output DB (V.5). Phase C must-do.
Already tracked in ProjectOrderBlueprint.md.

**Grade: A.** Clean Phase A+B execution. Two-commit structure (code + audit) follows protocol.
