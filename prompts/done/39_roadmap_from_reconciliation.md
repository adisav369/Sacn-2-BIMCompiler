# DONE 4001e781
# Update ACTION_ROADMAP from Reconciliation Findings

You are a docs-only session for bim-compiler. No code changes.

## Context

The S93 docs reconciliation (prompts/38_docs_conciseness_pass.md appendix)
cross-checked 6 major specs against code and found gaps not tracked in
ACTION_ROADMAP. This session absorbs those findings into the roadmap.

## Read first

1. `docs/ACTION_ROADMAP.md` — current roadmap
2. `prompts/38_docs_conciseness_pass.md` — scroll to the `---` appendix (reconciliation findings)
3. `PROGRESS.md`

## Task 1: Add untracked items to Open Gaps

These were found in reconciliation but have NO roadmap entry:

| Source | Gap | Priority |
|---|---|---|
| BIM_Designer_SRS §2 | UX-F-25: Set vs individual placement | MED |
| BIM_Designer_SRS §2 | UX-F-26/27: Multi-view sync (BBox↔ORDER↔3D) | MED |
| BIM_Designer_SRS §4 | UX-E-01..03: Server error handling (auto-launch, retry, crash recovery) | LOW |
| BIM_Designer_SRS §23 | DesignerServer standalone launcher (no main() method) | LOW |
| BIM_Designer_SRS §3 | UX-N-11..15: Capacity contracts (max rooms=100, storeys=10) | LOW |
| DocAction_SRS §1.3 | processIt() orchestrator — spec'd but doesn't exist in code | HIGH (ENT-1 related) |
| DocAction_SRS §2.1 | ClashDetector Phase 2 — AD_Clash_Rule engine | MED |
| DocAction_SRS §2.1 | VerticalContinuityChecker | LOW |
| DocAction_SRS §5 | Dual ad_val_rule ecosystems — spec doesn't acknowledge 5-DB split | MED |
| BIM_COBOL §20 | Spatial Predicate verbs (DISTANCE_BETWEEN etc.) — SPEC ONLY | MED |
| BIM_COBOL §2.4 | 19 verbs registered but unlisted in scoreboard | LOW (doc debt) |
| TestArchitecture | Ghost seal entry (WalkThruCompilationTest.java) | LOW (cleanup) |
| TestArchitecture | Seal version numbering diverged (v6 script vs v40 doc) | LOW (cleanup) |
| TestArchitecture §C13 | No Parametric Mesh — 28 call sites still emit parametric | MED |
| ProjectOrderBlueprint §4 | BOM Mining via DocAction=Approve — not tracked | MED |
| BOMBasedCompilation §4 | Typed coordinate hierarchy (LocalCoord/StoreyCoord/WorldCoord) undocumented in BBC | LOW (doc debt) |

Add each to the appropriate section:
- Code gaps → Open Gaps table
- Doc debt → new "Doc Debt" section (or append to existing)
- Items already covered by existing gaps (e.g. processIt → ENT-1) → add cross-reference, don't duplicate

## Task 2: Update existing gap statuses

From reconciliation findings:
- GAP-SC-1 (ASI mutation): add note — "S89 BBC audit confirmed: extraction writes ASI, compilation does NOT consume ASI for generative path"
- IDV-1 (Tier 2): update — Phase A-D DONE (S90-S92), Phase E pending (TEXT FK drop)
- Stale "Future" labels in ProjectOrderBlueprint: note that §5/§6/§9/§10/§11/§13 are IMPLEMENTED

## Task 3: Fix stale claims in ACTION_ROADMAP itself

- Verb count: should say 75 (verify it does)
- Witness count: should say 202 (or current)
- Building count: 35 (34+1)
- DB count: 5-DB (4+1)
- Session reference: should say S92 (or current)

## Task 4: Reconcile Phase F

Phase F currently has TRIM-1, TRIM-2, CP-2. Based on reconciliation:
- TRIM-1: DONE (S89-trim1, adeaf75b)
- Add: Spatial Predicate verbs (BIM_COBOL §20) — natural Phase F item
- Add: 19 unlisted verbs → scoreboard update (doc task, not code)

## Rules

- Do NOT restructure ACTION_ROADMAP — add to existing sections
- Keep entries concise (one line per gap, link to source spec)
- If an item is already tracked under a different name, add cross-reference
- Do NOT add items that are purely cosmetic doc formatting

Commit: `[S##-roadmap] Absorb reconciliation findings into ACTION_ROADMAP`

## When Done

Prepend `# DONE` + commit hash to this file's first line.

---

