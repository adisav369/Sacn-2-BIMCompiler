# DONE — CTFL spec review: 3 stale fixes, validation.db finding
> Commit: 70afbb1a [S87-ctfl-review]

CTFL-level review of all live specs on github.io. Goal: every spec a
reader lands on should be correct, internally consistent, and free of
stale claims. This is quality assurance, not new feature work.

## Scope

All docs in the mkdocs nav (docs/*.md minus archive/). Review each for:

### 1. Factual correctness
- Counts: 64 verbs, 34 buildings, 196 witnesses, 19 ALL GREEN, 4-DB architecture
- Table/column names: PP_Order_Node is now W_Verb_Node everywhere
- DB names: disc_validation.db renamed to ERP.db (S76), work_output.db removed (S61)
- Doc references: no links to CORE_SRS.md (archived), no links to removed docs
- iDempiere alignment: table names match iDempiere conventions (C_, M_, AD_, W_)

### 2. Internal consistency
- Cross-references between docs resolve (no broken §X.Y citations)
- Foundation lines at top of each doc list only live docs
- Witness IDs cited in specs exist in TestArchitecture.md §Traceability Matrix
- Status markers (DONE/IMPLEMENTED/SPEC ONLY) match reality — check Java code exists

### 3. Stale claims
- "5 databases" → should be 4 (work_output.db removed S61)
- "PP_Order" → should be W_Verb_Node
- "disc_validation.db" → should be ERP.db
- "63 verbs" → should be 64
- "22 ALL GREEN" or "19 ALL GREEN" — verify against current gate results
- Any reference to validation.db (old name for ERP.db)
- "component_library.db 81 tables" → now 21 (discipline metadata moved to ERP.db S41)

### 4. Audience clarity
- Each spec should be readable by someone who hasn't seen the codebase
- Aspirational/future sections clearly labelled (not mixed with implemented)
- iDempiere concepts explained on first use (not assumed)
- No orphaned TODO/FIXME/HACK markers visible to readers

## Method

For each doc in mkdocs.yml nav order:
1. Read the full doc
2. Grep for known stale terms: PP_Order, disc_validation, work_output, 5.database, 63.verb, validation.db, CORE_SRS
3. Check Foundation line links resolve
4. Check status markers against Java codebase (spot-check, not exhaustive)
5. Fix in place — small corrections only, no restructuring
6. Log every fix in the commit message

## Priority order (audience-facing first)

1. index.md (landing page)
2. MANIFESTO.md
3. BOMBasedCompilation.md (BBC)
4. TheRosettaStoneStrategy.md
5. BIM_COBOL.md
6. TestArchitecture.md
7. DATA_MODEL.md
8. SourceCodeGuide.md
9. All SRS docs (BIM_Designer_SRS, DocValidate, DISC_VALIDATE_SRS, etc.)
10. Analysis docs (SH, DX, TE, FK, AC, IN, DM)
11. Guides (WorkOrderGuide, USER_GUIDE, BIM_Designer_UserGuide, etc.)
12. Strategy/paper docs (BIMERPPaper, StrategicIndustryPositioning)

## Verification

After all fixes:
```bash
# Stale term scan — should return zero hits outside archive/
grep -rn "PP_Order\|disc_validation\.db\|work_output\.db\|CORE_SRS" docs/*.md

# Broken internal links — mkdocs build warnings
venv/bin/mkdocs build 2>&1 | grep -i "warning\|error"

# Compile gate
mvn compile -q
```

## Rules
- Small corrections only — fix the fact, don't rewrite the paragraph
- If a section needs major rewrite, note it in prompts/independent.txt, don't do it here
- Don't touch archive/ docs
- Don't change code — this is doc review only
- Commit per-doc or per-batch (not one giant commit)

Commit message prefix: [S87-ctfl-review]

---

## Coder Report — Fixes applied (3 files, 6 edits)

1. **TheRosettaStoneStrategy.md:13** — "Three real IFC buildings" → "35 real buildings (34 extracted + 1 generative)". Stale count from when only SH/FK/DX existed.

2. **BIM_Designer_SRS.md:1781** — "all 5 databases" → "all 4 databases". Stage 3 embedding table still referenced pre-S61 count.

3. **DocValidate.md:9,484,488,491,1847** — 5 references to `DISC_BOM_DESIGN.md` → `DISC_VALIDATION_DB_SRS.md`. DISC_BOM_DESIGN.md was deleted in S30 (commit b56dd5a3). Discipline BOM structure is under AD_Org → belongs in ERP database spec. Broken link caused mkdocs build warning.

## Verified clean (no fixes needed)

- **PP_Order** (without _Node): 0 hits in docs/*.md. Already cleaned.
- **disc_validation.db**: 4 hits, all in historical context (DATA_MODEL.md §6 rename history, AUDIT_S51 documenting S76 rename). Correct as-is.
- **work_output.db**: All hits in DocAction_SRS §1.10 (documenting removal) or DemoHouseAnalysis (strikethrough + update). Correct as-is.
- **CORE_SRS**: 2 hits — AUDIT (link count) and BIMERPPaper (listed as archived). Correct as-is.
- **validation.db**: ~60 hits. This is a LIVE separate database from ERP.db. Java code (BonsaiBIMDesigner) actively uses `library/validation.db` for AD_Val_Rule compliance rules. The prompt assumed validation.db = old name for ERP.db — that is WRONG. `disc_validation.db` was renamed to `ERP.db` (S76); `validation.db` is a separate file. All doc references are correct.
- **Counts** (64 verbs, 196 witnesses, 19 ALL GREEN, 35 buildings, 4-DB): consistent across all 55 nav docs. Verified against PROGRESS.md gate table.
- **Foundation lines**: all checked, all link to existing files.
- **TODO/FIXME/HACK**: present in audit docs, roadmaps, and spec future-work sections. Appropriate in context — not orphaned markers.

## mkdocs build warnings (structural, not fixable here)

These are cross-directory links that work on GitHub but not in mkdocs (docs_dir boundary):
- 10+ links from WorkOrderGuide.md to Java source files (`../IFCtoBOM/src/...`)
- 4 links to `../database/DATABASE_SCHEMA.md` and `bim_architecture_viz.html`
- `terminal_erd.html`, `bim_designer_erd.html` — HTML files not in docs/
- `../PROGRESS.md` — outside docs_dir

These need either (a) moving files into docs/, (b) absolute GitHub URLs, or (c) mkdocs `use_directory_urls` workaround. Not a small correction — noted for a future session.

## Pre-existing uncommitted changes (not mine)

- `docs/ACTION_ROADMAP.md` — SpecsAnalysis.txt refs replaced with proper doc links
- `docs/SpecsAnalysis.txt` — blanked (analysis processed into specs)
- `library/component_library.db` — binary, local-only

These are from a previous session (S86). Do not include in S87 commit.

## Finding: validation.db → ERP.db merge

validation.db holds AD_Val_Rule (compliance rules) — an ERP concern. From the iDempiere ERP lens, AD_Val_Rule belongs alongside AD_Org in ERP.db, not in a separate database. Current 4-DB split treats validation as separate, but both are AD-prefix tables serving the same ERP validation engine. Merging validation.db into ERP.db would reduce the database count and unify the AD concern.

**Scope:** Schema + migration + Java connection changes. Not a doc fix — deferred to a future session. Write findings to DISC_VALIDATION_DB_SRS.md or LAST_MILE_PROBLEM.md before code.

## COMMIT NOW (two separate commits, in order)

```bash
git add docs/ACTION_ROADMAP.md docs/SpecsAnalysis.txt && git commit -m "[S86-watchdog] SpecsAnalysis blanked (processed), ACTION_ROADMAP refs repointed"
git add docs/BIM_Designer_SRS.md docs/DocValidate.md docs/TheRosettaStoneStrategy.md prompts/28_ctfl_spec_review.md && git commit -m "[S87-ctfl-review] 3 stale fixes: RosettaStone count, 5→4 DB, DISC_BOM_DESIGN→DISC_VALIDATION_DB_SRS"
```

## Watchdog Review (2026-03-26)
- 3 fixes all correct. validation.db finding is a real architectural insight — track for future session.
- mkdocs cross-directory warnings noted but out of scope.
- Clean CTFL pass.
