# DONE fe69576 — Phase 3: Remove CO_EmptySpaceLine — rewrite pipeline to use M_BOM_Line

You are a coder for bim-compiler. Code only — no docs (already aligned in S73).

Read first:
1. docs/MANIFESTO.md — Three Concerns: WHERE = M_BOM_Line dx/dy/dz
2. prompts/done/07_remove_co_emptyspaceline.md — Phase 1+2 results, Phase 3 plan
3. PROGRESS.md

## Context

S73 deprecated CO_EmptySpaceLine across docs (15 files) and code (4 PO classes
@Deprecated, 10 files javadoc updated). The tables and PO classes still exist
and are used by 119 production references. This session removes them.

## Phase 3 plan (from S73 findings)

1. **Rewrite `CompilationPipeline.populateCoEmptySpace()`** (~200 LOC)
   - This method creates co_empty_space and co_empty_space_line rows from BOM data
   - The BOM already has all the spatial data (dx/dy/dz, AABB) on M_BOM_Line
   - Either remove entirely (if nothing downstream reads these tables) or
     replace with a simple view/query on M_BOM_Line

2. **Rewrite `SpatialStructureBuilder.emitIfcSpaceFromL2()`**
   - Currently reads co_empty_space_line to emit IfcSpace geometry
   - Should query M_BOM_Line with bom_type/bom_level instead

3. **Rewrite `VerifyPlacementVerb`** (already @Deprecated)
   - Verifies containment using co_empty_space_line AABB
   - Should verify using M_BOM_Line AABB (parent contains children)

4. **Update `HelloWorldVerb`, `SummarizeBuildingVerb`, `CompleteBuildingVerb`**
   - HelloWorldVerb: output label already changed, remove table query
   - SummarizeBuildingVerb: counts from co_empty_space_line → count from M_BOM_Line
   - CompleteBuildingVerb: EmptySpaceChecksum column on C_Order

5. **Migration W008:**
   - `ALTER TABLE C_Order DROP COLUMN EmptySpaceChecksum` (or leave as NULL — SQLite can't drop columns before 3.35)
   - `DROP TABLE IF EXISTS co_empty_space_line`
   - `DROP TABLE IF EXISTS co_empty_space`

6. **Delete PO classes:**
   - `X_CO_EmptySpace.java`
   - `X_CO_EmptySpaceLine.java`
   - `M_CO_EmptySpace.java`
   - `M_CO_EmptySpaceLine.java`

7. **Update `BuildingWriter`** — remove co_empty_space DDL from table creation

8. **Update `OutputTemplateGenerator`** — remove co_empty_space from schema guide

## How to work

- Code through everything. Do NOT run tests — just code.
- `mvn compile -q` at the very end only.
- If compile fails, fix until it passes.
- Commit everything in one commit.

## Constraints

- Append-only migrations (W008)
- Do NOT change docs (already done in S73)
- Do NOT change MANIFESTO.md
- Pre-flight: `// Implementing MANIFESTO.md Three Concerns — WHERE = M_BOM_Line dx/dy/dz`

## When Done

Prepend `# DONE` + commit hash to this file's first line before committing.
Commit with `[S74] Phase 3: remove CO_EmptySpaceLine — pipeline migrated to M_BOM_Line`.
