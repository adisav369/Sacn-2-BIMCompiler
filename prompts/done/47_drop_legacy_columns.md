# DONE — Drop DocBaseType + DocSubType from C_DocType
> Commit: f8987798 [S97-schema]

You are a coder for bim-compiler. Schema cleanup — iDempiere alignment.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Migrate references before dropping. Know what breaks before touching code.

## Read first

1. This prompt
2. `docs/DATA_MODEL.md` §C_DocType + §m_bom
3. `docs/BOMBasedCompilation.md` §1
4. `CLAUDE.md` — Sacred Files (migration append-only)

## Context

iDempiere BIM has ONE DocType: `ConstructionOrder`. The columns `DocBaseType` and
`DocSubType` on C_DocType were repurposed as routing keys, but that routing now
belongs on m_bom where the canonical columns already exist:

| Stale column (C_DocType) | Canonical column (m_bom) | Status |
|--------------------------|--------------------------|--------|
| `DocBaseType` (RE/CO/IN/ST) | `m_product_category_id` | m_bom is source of truth since S77 |
| `DocSubType` (SH/DX/TE/FK) | `doc_sub_type` | m_bom is source of truth since S84 |

`doc_base_type` on m_bom was already dropped (W012, S84). `doc_sub_type` on m_bom
stays — it IS the building identity. This session drops the **C_DocType copies only**.

## Prior art (what already happened)

- S77: Java routing changed from `doc_base_type` → `m_product_category_id` (19 source + 12 test files)
- S84: `doc_base_type` dropped from m_bom (W012 migration, 14 files)
- S86: `doc_base_type` removed from output.db DDL
- BuildingRegistry already fetches AABB from m_bom via LEFT JOIN (not from C_DocType)

## Breakage map — fix BEFORE dropping

### A. JOINs that use DocBaseType/DocSubType as join keys

| File | Line | Current JOIN | Fix |
|------|------|-------------|-----|
| `BuildingRegistry.java` | 142-143 | `b.doc_sub_type = d.DocSubType AND b.m_product_category_id = d.DocBaseType` | Reverse: drive from m_bom BUILDING, LEFT JOIN C_DocType ON `d.Value = b.m_product_category_id \|\| '_' \|\| b.doc_sub_type` (Value = 'RE_SH' etc.) |
| `DesignerDAO.java` | 61-62, 97-98 | Same JOIN pattern | Same fix |
| `DesignerAPIImpl.java` | 212-213 | Same JOIN pattern | Same fix |
| `DataIntegrityTest.java` | 285-286 | Same JOIN pattern | Same fix |
| `PrimeRuleWitnessTest.java` | 241-242 | Same JOIN pattern | Same fix |

### B. Queries that SELECT DocBaseType/DocSubType

| File | Line | Current query | Fix |
|------|------|--------------|-----|
| `BuildingRegistry.java` | 134, 157-158 | `SELECT d.DocBaseType, d.DocSubType` | Replace with `b.m_product_category_id, b.doc_sub_type` from m_bom |
| `BuildingRegistry.java` | 86-87 | `loadByDocBaseType(String)` WHERE clause | Filter via `m_bom.m_product_category_id` instead |
| `DesignerDAO.java` | 55, 75-76, 91, 110-111 | Same SELECT pattern | Same fix |
| `DesignerDAO.java` | 130 | `WHERE doc_sub_type = ?` on m_bom | Already correct (reads m_bom) |
| `DesignerAPIImpl.java` | 204, 226-227 | Same SELECT pattern | Same fix |
| `PortfolioDAO.java` | 265, 270, 273 | `SELECT DocBaseType, DocSubType` | Read from m_bom via JOIN or remove if unused |
| `HelloWorldVerb.java` | 211 | `FROM C_DocType WHERE DocSubType = ?` | Change to `WHERE Value LIKE ? \|\| '_%'` or JOIN m_bom |
| `PlaceBomVerb.java` | 246 | `FROM C_DocType WHERE DocSubType=?` | Same fix |
| `EnBlocVerb.java` | 74 | `FROM C_DocType WHERE DocSubType=?` | Same fix |
| `WalkThruVerb.java` | 82 | `FROM C_DocType WHERE DocSubType=?` | Same fix |
| `RegisterBuildingVerb.java` | 82 | `FROM C_DocType WHERE DocSubType=?` | Same fix |
| `PlacementLoader.java` | 298 | `SELECT DocSubType, ProjectName FROM C_DocType` | Need replacement: m_bom.doc_sub_type → C_DocType.ProjectName mapping |
| `CompilationPipeline.java` | 500 | `.filter(b -> docSubType.equals(b.getDocSubType()))` | Uses BuildingEntry which gets data from m_bom — verify source |
| `WebUIServer.java` | 318, 322 | `SELECT DocSubType FROM C_DocType` | Read from m_bom instead |
| `AllModelsReportGenerator.java` | 174, 179, 194, 208 | `dt.getDocSubType()` | Read from m_bom |
| `BomValidator.java` | 135 | `SELECT m_product_category_id, doc_sub_type FROM m_bom` | Already correct (reads m_bom) |

### C. DDL / INSERT that define or populate the columns

| File | Line | Action |
|------|------|--------|
| `IFCtoBOMPipeline.java` | 645-646 | Remove DocBaseType + DocSubType from CREATE TABLE |
| `IFCtoBOMPipeline.java` | 362 | Remove from INSERT |
| `RosettaStoneToBOM.py` | 758 | Remove from INSERT |
| `StubDataSeeder.java` | 46-47, 143, 153, 163 | Remove from DDL + INSERT |
| `seed_dm_bom.sql` | 22, 72 | Remove from DDL + INSERT |
| `WorkOutputDAO.java` | 1131 | Remove from output DDL |
| `W001_work_output_schema.sql` | 36-37 | DO NOT EDIT (Sacred File — append-only migration) |
| `W012_drop_doc_base_type.sql` | references | DO NOT EDIT |
| `W015_m_bom_int_pk.sql` | references | DO NOT EDIT |
| `schema_snapshot_bom.sql` | 1153-1154 | Remove from snapshot |
| YAML configs (35 files) | `doc_base_type:` line | Keep — still read by Python for C_DocType.Value construction |

### D. ORM / PO classes

| File | Action |
|------|--------|
| `X_C_DocType.java` | Remove DocBaseType/DocSubType column constants + accessors |
| `MCDocType.java` | Remove getByDocBaseType(), getByDocSubType(), toDocType() — replace with m_bom queries |
| `BuildingEntry` record | Remove docBaseType/docSubType fields — replace with mProductCategoryId/docSubType from m_bom |

### E. Test files

| File | Action |
|------|--------|
| `PrimeRuleWitnessTest.java` | Rewrite W-PRIME-4 (C_DocType coverage) — check m_bom has m_product_category_id + doc_sub_type |
| `HelloWorldVerbTest.java` | `docSubTypeMatch()` — verify it reads from m_bom not C_DocType |
| `BuildingRegistryTest.java` | `loadByDocBaseType` → `loadByProductCategory` |
| `DataIntegrityTest.java` | Rewrite JOIN |
| `CompileBridgeTest.java` | DDL + INSERT in test setup |
| `ASIAuthoringTest.java` | DDL + INSERT in test setup |
| `BomDropCompileTest.java` | DDL + INSERT in test setup |
| `BomDropConfigureTest.java` | DDL + INSERT in test setup |
| `RemoveCompressTest.java` | DDL in test setup |
| `OrderInheritanceTest.java` | DDL in test setup |
| `BomDropperOrderIdTest.java` | DDL in test setup |
| `DemoHouseTest.java` | DDL + query |
| `BuildingInspectorTest.java` | W-OWNER-2 checks DocSubType NOT NULL — rewrite |
| `StTemplatePipelineTest.java` | References doc_sub_type (m_bom) — likely OK |

### F. Python client

| File | Action |
|------|--------|
| `client.py` | 91, 95: `list_categories(doc_sub_type)` + `"docSubType"` param — rename to match new API |

## The C_DocType → m_bom link after dropping

C_DocType.Value = `{m_product_category_id}_{doc_sub_type}` (e.g. 'RE_SH', 'CO_TE').
After dropping DocBaseType/DocSubType, the link from C_DocType to m_bom BUILDING is:

```sql
-- Old: JOIN m_bom b ON b.doc_sub_type = d.DocSubType AND b.m_product_category_id = d.DocBaseType
-- New: JOIN m_bom b ON d.Value = b.m_product_category_id || '_' || b.doc_sub_type
--      AND b.bom_type = 'BUILDING' AND b.is_active = 1
```

Or better: add `doc_sub_type TEXT` to C_DocType as the single FK (replacing both
DocBaseType and DocSubType). This avoids string concatenation in JOINs. The
`m_product_category_id` is already on m_bom — no need to duplicate it on C_DocType.

**Decision needed:** Value-based JOIN vs doc_sub_type FK. Recommend doc_sub_type FK
since it's a clean 1:1 relationship (one C_DocType per building prefix).

## Execution order

1. **Add `doc_sub_type` column to C_DocType** (migration, backfill from DocSubType)
2. **Migrate all Java/Python reads** — DocBaseType → m_bom.m_product_category_id, DocSubType → C_DocType.doc_sub_type or m_bom.doc_sub_type
3. **Migrate all JOINs** — use new doc_sub_type FK
4. **Remove DocBaseType + DocSubType** from DDL, INSERT, ORM, tests
5. **Drop columns** via migration (append-only)
6. **Run full test suite** — `./scripts/run_tests.sh` + `./scripts/run_RosettaStones.sh classify_sh.yaml`
7. **Update docs** — DATA_MODEL.md, BOMBasedCompilation.md

## Rules

- Migration files are APPEND-ONLY (Sacred File rule)
- Fix code BEFORE dropping columns — no "let it break" approach
- If a reference turns out to be deeper than mapped here, STOP and document — don't guess
- `doc_sub_type` on m_bom stays — it is STRUCTURAL (building identity)
- YAML `doc_base_type` stays — still needed for Value construction and Python C_DocType seeding

## Commit

```
[S97-schema] Drop DocBaseType/DocSubType from C_DocType

Migration W0XX. C_DocType.doc_sub_type FK replaces both columns.
Java/Python reads migrated to m_bom.m_product_category_id + doc_sub_type.
Fixes: [list files changed].

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
```

## WATCHDOG REVIEWED — 2026-03-27

**Commit verified:** `f8987798` exists, message matches deliverable.

**Deliverables checked:**
- W018 migration created (`migration/W018_c_doctype_drop_docbasetype.sql`)
- 32 files changed across 7 modules — matches breakage map scope
- DocBaseType dropped, DocSubType renamed to doc_sub_type (snake_case FK)
- New JOIN pattern: `LEFT JOIN m_bom b ON b.doc_sub_type = d.doc_sub_type AND b.bom_type='BUILDING'`
- BuildingEntry.docBaseType → mProductCategoryId
- loadByDocBaseType() → loadByProductCategory()
- SH 7/7 PASS per commit message
- `mvn compile -q` — PASS

**Protocol note:** Coder did not prepend DONE marker. Added by watchdog.

**Verdict:** PASS — comprehensive schema cleanup, all breakage map items addressed,
Sacred Files untouched, migration append-only.
