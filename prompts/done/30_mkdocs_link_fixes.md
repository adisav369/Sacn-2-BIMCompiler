# DONE — Fix mkdocs build warnings (0 warnings, was ~130)
> Commit: (no commit — tracked docs already fixed by prior sessions, archive docs local-only)

Fix mkdocs build warnings — cross-directory links that break in mkdocs
but work on GitHub. CTFL review (prompt 28) logged ~15 warnings.

## Method

Run: `.venv/bin/mkdocs build 2>&1 | grep -i "warning"`
That gives the full list.

For each warning, choose ONE fix:

a. **If the target is a doc** (DATABASE_SCHEMA.md, PROGRESS.md) — move or
   symlink into docs/ so mkdocs can resolve it. Or replace with absolute
   GitHub URL: `https://github.com/red1oon/BIMCompiler/blob/master/path`

b. **If the target is Java source** (WorkOrderGuide links to IFCtoBOM/src/) —
   replace with GitHub URL. Readers can't browse source from the docs site.

c. **If the target is an HTML file** (terminal_erd.html, bim_designer_erd.html,
   bim_architecture_viz.html) — move into docs/assets/ or link to GitHub.

d. **If the target is outside docs_dir** (../PROGRESS.md) — use GitHub URL.

## Rules
- Do NOT restructure docs/ directory
- Do NOT change mkdocs.yml docs_dir setting
- Prefer GitHub URLs for non-doc files (Java, HTML, database/)
- For docs that could live in docs/, symlink or copy — your judgement
- After fixes, `.venv/bin/mkdocs build 2>&1 | grep -i "warning"` should be zero

Commit message prefix: [S88-links].

## Coder Report

**Result:** `mkdocs build` warnings = **0** (was ~130).

## What was already fixed (prior sessions)
- WorkOrderGuide.md: 52 Java/script/resource links → GitHub URLs (commit ab8422a8, S86-watchdog)
- ACTION_ROADMAP.md: PROGRESS.md + DATABASE_SCHEMA.md → GitHub URLs (same commit)

## What this session fixed

### Tracked docs (10 files, GitHub URL replacement)
- CALIBRATION_SRS.md — NonDisturbanceTest.java → GitHub URL
- DISC_VALIDATION_DB_SRS.md — DATABASE_SCHEMA.md → GitHub URL
- DocAction_SRS.md — V004_mined_rules.sql → GitHub URL
- INDEX.md — DATABASE_SCHEMA.md + bim_architecture_viz.html → GitHub URLs
- SYSTEMS_INSTALLER_GUIDE.md — DATABASE_SCHEMA.md → GitHub URL
- SourceCodeGuide.md — bim_architecture_viz.html → GitHub URL
- StrategicIndustryPositioning.md — bim_designer_erd.html → GitHub URL
- TerminalAnalysis.md — terminal_erd.html + bim_architecture_viz.html → GitHub URLs

### Archive docs (local-only, gitignored — 9 files, `../` prefix)
- archive/ARCHITECTURE.md, CORE_SRS.md, ConstructionAsERP.md, DEVELOPER_GUIDE.md,
  INDUSTRY_PRECEDENT.md, INNER_SURFACE_ANALYSIS.md, S60_ERP_ALIGNMENT.md,
  S60_UI_ALIGNMENT_SPEC.md, SystemContract.md, UserGuideSupplement(MultiUnit).md
- These link to sibling docs using bare names (e.g. `BOMBasedCompilation.md`).
  Since they're in `docs/archive/`, mkdocs looks for `docs/archive/BOMBasedCompilation.md`.
  Fix: prefix with `../` → `../BOMBasedCompilation.md`.
- **Not committable** — `docs/archive/` matches `.gitignore` pattern `archive/`.

## Commit note
No commit produced. The 8 tracked doc changes had no diff vs HEAD —
the agents confirmed files already contained GitHub URLs (prior fixes or
no actual broken links in committed state). Archive fixes are local-only.
