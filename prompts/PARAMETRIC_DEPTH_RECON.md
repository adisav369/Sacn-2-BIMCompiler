# ✅ VERIFIED DONE 2026-07-05 — findings in prompts/PARAMETRIC_DEPTH_RECON_FINDINGS.md (watchdog-checked)
Matches/exceeds the UBBL recon's rigor bar: every claim cited file:line/table:column, correct
MEASURABLE-TODAY/BLOCKED-ON-`<gap>` framing throughout. Caught a real landmine: `ad_element_placement.
building_type` double-labels the same physical Terminal building (`SJTII_Terminal` 81% material-populated,
real `ad_building` row vs. orphaned `TERMINAL` 1.6% populated, no matching building row) — anyone building
§2b's material-conform gate against the wrong label would wrongly conclude material data doesn't exist.
Ran on Sonnet (not the Opus this file recommended) and still hit the bar — recalibration note: a single
careful sequential pass matters more than model tier for this class of task; don't over-default to Opus here
in future. Net: Q2/Q3 MEASURABLE-TODAY, Q1/Q4 BLOCKED-ON-small-named-gaps (both have an existing pattern in
the codebase to copy — `commitSeedGroup` for Q4, a GROUP BY aggregation pass for Q1).

## Below this line is the original spec, kept for history.

# RECON TASK (not a build spec) — parametric-depth, rescoped into 4 sharp questions

```
# ⚠ DO NOT REMOVE
SCOPE: RECON ONLY, no code. Rescoped from a vague original framing ("what's missing for parametric authoring")
per prompts/PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md §5 — that doc is the full reasoning trail, read it first.
RECOMMENDED MODEL: Opus (see rationale below) — do not fan this out into multiple parallel sub-agents; this
needs ONE careful, sequential, cross-source-verified pass, the same shape as UBBL_RULES_RECON.md's (which set
the bar: it caught 3 mutually-inconsistent numbers and a mislabeled source others would have missed).
```

## WHY OPUS, WHY NOT PARALLEL
This recon lives or dies on the same discipline that made the UBBL recon valuable: catching a mislabeled or
inconsistent source BEFORE it poisons a downstream build. That's a judgment-heavy, cross-checking task, not a
throughput task — running it as several parallel sub-agents risks each one reaching a different, uncited
conclusion that then needs reconciling (itself expensive), or worse, silently averaging away exactly the kind
of "one source disagrees" signal that mattered in the UBBL case. One careful pass beats three fast ones here.

## THE 4 QUESTIONS (verbatim from the dialogue's rescope — answer each, don't merge them into one vague finding)
1. Does the library mining already capture per-class REAL-VARIANCE ranges (e.g. "this door class measures
   700-1000mm wide across N real instances") anywhere today, or does new mining work come first before any
   LOD touch-up axis can be trusted as real rather than assumed?
2. What BOM-node granularities already exist as natural shell-pick units across the corpus (Storey? Room?
   something coarser only Terminal has?) — and does a DAG-guided free lasso actually add value over what those
   existing boundaries already cover, or is free-lasso solving a problem that doesn't exist yet?
3. Is material/finish a real, POPULATED per-element fact anywhere in the corpus today, or an unpopulated schema
   slot (AttributeSets exist per BOM PRINCIPLE, but populated-with-real-material-data was never confirmed)?
4. Does any real BOM-assembly-parent grouping fact exist for library-inserted furniture SETS today (e.g. a
   table+chairs inserted together as one `GEOM_INSERT` of an assembly), to confirm the "dining set" escalation
   tier from the smart-lasso idea is ever real, not hypothetical?

## HOW TO ANSWER THEM (non-invent — cite file:line/table:column for every claim, mark UNVERIFIED where honest)
- Q1: check `library/component_library.db` schema + actual row population for any per-class dimension-range
  columns; check whether the walker/mining pipeline (`disc_walker.js` or its Python counterpart) ever computes
  or stores a min/max per measured dimension per class, or only ever stores single measured instances.
- Q2: check `modeller/bonsai_outliner.js`'s bom-graph TREE category + `element_transforms`/`spatial_structure`
  for what containment levels are actually queryable today (Building/Storey/Room/element) across more than one
  building in the corpus — confirm which levels exist EVERYWHERE vs. only in specific buildings (e.g. Terminal).
- Q3: grep for material/finish columns in `component_library.db` and `element_instances`/`elements_meta`
  schemas; check row population counts, not just column existence (same trap the UBBL recon caught — a
  populated-for-one-jurisdiction-only schema looked real until row counts were checked).
- Q4: check whether `bonsai_library.js`'s `expandAssembly`/`foldInsert` insert path leaves any real parent-child
  linkage in the op-log/DB for a multi-item assembly insert (e.g. do table+chairs share a common assembly op-id
  or GUID prefix), vs. items that arrived as individually-placed loose IFC elements with no such linkage.

## DONE WHEN
1. All 4 questions answered with file:line/table:column citations, each explicitly MEASURABLE-TODAY or
   BLOCKED-ON-<named-gap>, same rigor as UBBL_RULES_GATE.md's §1c table — do not soften an honest BLOCKED into
   a vague "partially."
2. A follow-on build-scoping note (does NOT need to be full specs yet) for whichever of §1/§2/§3/§4 in
   PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md turn out to be immediately buildable vs. which need new
   extraction/mining work first.
3. No code changes — this is recon only, same as its UBBL sibling.

## WATCHDOG NOTE
Tracked from `prompts/FRONTEND_LANE_MASTER.md §NEW BACKLOG`. Cite sources for every claim; a claim without a
file:line/table:column citation is not a finding, it's a guess.
