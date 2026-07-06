# DONE 94d0e4f — Session F: DiffVerb + Callout (ProjectOrderBlueprint §9)

You are a coder for bim-compiler. One bounded task.

Read first:
1. docs/MANIFESTO.md (ERP world view — Column Callout + ModelValidator)
2. docs/ProjectOrderBlueprint.md §9 (DiffVerb + Callout spec)
3. docs/ProjectOrderBlueprint.md §14.1 row for §9 (gap assessment)
4. docs/BOMBasedCompilation.md §3 (verb system)
5. PROGRESS.md

## Prerequisites

- Sessions A–E DONE (§1 exception-based ordering complete)
- All 4 mutations implemented (Replace, Add, Remove, Compress)

## Context

§9 describes: "drag a wall in the viewport → cascading consequences fire."
This is iDempiere's Column Callout pattern applied to spatial operations.

Currently PP_Order_Node records verb execution (audit trail) but there is no
DIFF verb_type and no AD_Rule callout chain. When an element moves, nothing
cascades.

## Task

1. **Investigate first:** Read the §9 spec carefully. Determine:
   - What DIFF verb_type should record (old position, new position, delta)
   - What callout chain should fire (room AABB recalc, furniture revalidation, MEP reroute)
   - What AD_Rule rows need to exist for callout registration
   - Is this implementable now, or are there blocking dependencies?

2. **Write findings** to docs/AUDIT_S51_FOCUSED.md as Appendix T.
   Include: feasibility assessment, proposed schema additions, dependency check.

3. **If feasible:** Implement the minimal DIFF verb:
   - Add `DIFF` to verb_type enum/constants
   - PP_Order_Node records old/new position on MOVE/RESIZE
   - One callout: room AABB recalculation when a wall moves
   - Test: DiffVerbTest with witness W-DIFF-1

4. **If blocked:** Document what blocks it and what needs to happen first.

## Constraints

- Append-only migrations
- Gate: existing tests must pass unchanged
- Pre-flight citation: `// Implementing ProjectOrderBlueprint.md §9 — Witness: W-DIFF-1`

## When Done

Prepend `# DONE` + commit hash to this file's first line before committing.
Commit with `[S72] Session F: DiffVerb + Callout — cascading spatial consequences`.
