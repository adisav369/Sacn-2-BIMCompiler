# DONE — Docs readability pass 2 — BBC + DATA_MODEL rewrite + commit S79 code
> Commit: 241f2d63 [S81]

You are a watchdog + docs session for bim-compiler. No new code unless committing.

Read first:
1. PROGRESS.md
2. docs/SpecsAnalysis.txt (all 4 parts — especially §19 stale terminology, §20-23 new findings)
3. docs/MANIFESTO.md (just reviewed and tightened — reference tone)
4. docs/BOMBasedCompilation.md (header rewritten S80, content needs full readability pass)
5. docs/DATA_MODEL.md (deprecated markers cleaned S80, needs rewrite for audience)
6. CLAUDE.md §BOM PRINCIPLE (added S80 — the anti-drift anchor)

## Task 1: Commit S79 code changes

13 modified Java files + W010 migration + PROGRESS.md sitting uncommitted from S79 (bulk discipline migration). `mvn compile -q` + `mvn test-compile -q` both PASS (verified S80).

Commit as: `[S79] Bulk discipline migration: TEXT → Discipline enum + AD_Org_ID`

Also commit all S80 doc changes (20+ files) as separate commit:
`[S80] Docs readability: stale stats, overhype, SystemContract archived, nav restructured, 3 new specs`

## Task 2: BBC.md full readability rewrite

Current state: 1361 lines, 13 sections. Header professionalised (S80) but content still dev-WIP tone.

Goals:
- Audience-facing, not dev log
- Remove "Gospel Principle" naming (just explain the principle)
- Remove stale `bom_category` references (use AD_Org_ID / Discipline enum)
- Remove `work_output.db` references
- Remove "Onboarding Gotchas" appendix (move to WorkOrderGuide if needed)
- Clean up §11 Dynamic Building Registration (done items, remove task framing)
- Ensure BOM cascade definition appears early (already in header from S80)
- Link to MANIFESTO for Three Concerns, to BLUEPRINT for extended features
- Tone: confident, qualified, humble — match MANIFESTO style from S80

## Task 3: DATA_MODEL.md readability rewrite

Current state: 588 lines. Legacy markers cleaned (S80) but structure still dev-oriented.

Goals:
- Lead with the 4-DB architecture (the reader's first question: where does data live?)
- Entity registry content from archived SystemContract §2 should be merged here
- Remove migration task framing (§7.6 already converted to status table S80)
- Schema tables should show current state, not migration history
- Link to BBC for compilation model, MANIFESTO for ERP world view

## Task 4: Remaining stale terminology (from SpecsAnalysis §19)

Three specs still use old model terminology:
- DISC_VALIDATE_SRS.md — 12 `bom_category` refs → rewrite to AD_Org_ID
- DocAction_SRS.md — 6 `bom_category` refs + 15 `work_output.db` refs (§1.7-1.9 vs §1.10 contradiction)
- GENERATIVE_HOUSE_SRS.md — 3 `bom_category` refs

These are internal specs but still audience-visible on github.io.

## Task 5: SpecsAnalysis §20-23 (from user's latest findings)

User added Part 4 to SpecsAnalysis.txt (TRIM verb + roof swap gap analysis).
Review findings §20-23 and determine:
- §20 SH curved roof vs flat DSL — document limitation or fix?
- §21 TRIM not wired — add to .bimcobol or defer?
- §22 OrderLine replace end-to-end witness — write prompt?
- §23 DSL/YAML redundancy — decision needed for ACTION_ROADMAP

## Constraints

- No code changes beyond the S79 commit (already verified)
- BBC and DATA_MODEL rewrites are doc-only
- Deploy after each major change: `.venv/bin/mkdocs gh-deploy`
- Keep MANIFESTO as tone reference — confident, qualified, always linked
- CLAUDE.md line limit 45 lines — check after changes
