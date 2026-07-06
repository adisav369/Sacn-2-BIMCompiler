# DONE 4fad6200
# Fix CompileBridgeTest — Align with iDempiere _ID Convention

**Priority:** Pre-existing test breakage since S90-S92 INTEGER PK migration,
compounded by Phase A (m_bom _ID migration, S100-p86). Multiple schema
mismatches in `prepareCompileDb()`.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Read the real schema from SH_BOM.db. Match it.

## Read first

1. `BonsaiBIMDesigner/src/test/java/com/bim/designer/CompileBridgeTest.java`
   — `prepareCompileDb()` creates C_DocType and reads m_bom.
2. Run: `sqlite3 library/SH_BOM.db ".schema"` — see ALL real table schemas.
3. `docs/DATA_MODEL.md` — iDempiere PK convention (if updated), else
   `prompts/86_idempiere_pk_conformance.md` §Understanding.
4. `orm-core/src/main/java/com/bim/orm/BasePO.java` — `loadByValue()` method.

## iDempiere PK Convention (must follow)

Every master table: `TableName_ID INTEGER PK AUTOINCREMENT` (opaque),
`Value TEXT NOT NULL UNIQUE` (search key), `Name TEXT NOT NULL` (display).
FKs reference `_ID`. Business lookups use `WHERE Value = ?`.

## Task: Align CompileBridgeTest with current schema

### Fix 1: C_DocType DDL (line 150-166)

`C_DocType_ID TEXT PRIMARY KEY` → `C_DocType_ID INTEGER PRIMARY KEY AUTOINCREMENT`.
Add `Value TEXT NOT NULL UNIQUE`. INSERT uses INTEGER for ID, "RE_SH" in `Value`.

### Fix 2: m_bom query (line 140)

```java
// BEFORE (stale):
"SELECT ... FROM m_bom WHERE bom_id='BUILDING_SH_STD'"
// AFTER (iDempiere convention):
"SELECT ... FROM m_bom WHERE Value='BUILDING_SH_STD'"
```

`bom_id` column still exists as legacy alias but `Value` is the canonical
search key after Phase A. Use `Value` for all business-key lookups.

### Fix 3: Check all other schema assumptions

Grep the test for any remaining TEXT PK patterns or stale column names.
After Phase B (prompt 87), `m_product_category_id` will also be INTEGER —
but that hasn't landed yet. Only fix what's currently broken.

### What NOT to do

- Do NOT change the compilation pipeline or DesignerAPIImpl
- Do NOT change real schemas or migration files
- Do NOT skip the test — fix it properly
- Do NOT anticipate Phase B changes — only align with current schema

## Verify

1. `mvn compile -q` — PASS
2. `mvn test -pl BonsaiBIMDesigner -Dtest=CompileBridgeTest -Dpipeline.tests.skip=false` — ALL PASS
3. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS

## Bonus

Once CompileBridgeTest passes, add W-VERB-DISPATCH-1 as a new @Order(5)
test: bomDrop → compile → executeVerb(CHECK BOM BUILDING_SH_STD) → dispatched.
Original intent of prompt 52, blocked by this breakage.

## When Done

Prepend `# DONE` + commit hash to this file's first line.
