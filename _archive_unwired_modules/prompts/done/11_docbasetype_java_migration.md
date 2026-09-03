# DONE — Migrate Java routing: DocBaseType → M_Product_Category
> Commit: 94422caa [S77]

You are a coder for bim-compiler. Java migration.

Read first:
1. docs/MANIFESTO.md — C_DocType = ONE, classification = M_Product_Category
2. docs/DATA_MODEL.md §7.3 — list of 19 Java source files + 12 test files
3. docs/DATA_MODEL.md §7.6 — migration plan
4. PROGRESS.md

## Prerequisites

- Prompt 09 DONE — M_Product_Category hierarchy populated, BUILDING BOMs backfilled
- Prompt 10 DONE — ERP.db renamed to ERP.db

## Context

19 Java files route on `doc_base_type`/`doc_sub_type` (DATA_MODEL.md §7.3).
After prompt 09, every BUILDING BOM has `m_product_category_id` populated.
The query `WHERE doc_base_type = ? AND doc_sub_type = ?` is now equivalent to
`WHERE m_product_category_id = ? AND bom_id = ?`.

## Task

### Phase 1: Core routing path (BomDropper → BuildingRegistry → CompilationPipeline)

These 3 files are the critical path — everything else depends on them:

- `BomDropper.findBuildingBom()` — change WHERE clause
- `BuildingRegistry.load()` — change JOIN condition
- `CompilationPipeline` — three-key match

### Phase 2: IFCtoBOM module (extraction pipeline)

- `IFCtoBOMPipeline` — dispatch and C_DocType creation
- `StructuralBomBuilder`, `DisciplineBomBuilder` — INSERT with doc_base_type
- `ClassificationYaml` — record field
- `BomValidator` — validation

### Phase 3: Remaining modules

- `ORMSandbox` PO classes — column accessors (keep deprecated aliases)
- `BonsaiBIMDesigner` DAOs — JOIN conditions
- `BIMBackOffice` — PortfolioDAO SELECT
- `BIM_COBOL` — ComposeBuildingVerb

### Phase 4: Test files (12)

Update 12 test files to use m_product_category_id in assertions and setup.

### Phase 5: Deprecate doc_base_type columns

- Mark doc_base_type/doc_sub_type columns as deprecated in schema snapshots
- Do NOT drop them — backward compat for external tools
- Update schema comments

## Constraints

- Keep doc_base_type columns in schema (deprecated, not removed)
- Do NOT run tests — code through all phases, `mvn compile -q` at the end
- Append-only migrations
- Pre-flight: `// Implementing DATA_MODEL.md §7 — DocBaseType → M_Product_Category alignment`

## When Done

Prepend `# DONE` + commit hash to this file's first line before committing.
Commit with `[S77] Java routing: DocBaseType → M_Product_Category (19 source + 12 test files)`.
