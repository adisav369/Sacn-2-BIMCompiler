# DONE — Docs Polish Resume — S96 Continuation
> Commit: b32f7871 [S96-polish]

You are a coder for bim-compiler. Documentation session. Continue from where S96 left off.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Read the docs. Fix what's stale. Don't rewrite working prose.

## Uncommitted Changes (MUST commit first)

These files have been edited but NOT committed:

| File | What changed |
|------|-------------|
| `docs/LAST_MILE_PROBLEM.md` | Full rewrite as "The Drift" — 11 spec-cited drift points, banner, Compiere logging convention, spec fidelity updated with C_Order flow |
| `docs/index.md` | Problem Statement heading, prose restructured (problem → solution, no repeat with cards), Key Terms removed |
| `docs/BOMBasedCompilation.md` | Banner: "Everything is a BOM" (short, linked). Tagline: "draw it" not "bill it". `.html` links fixed to `.md` |
| `docs/SampleHouseAnalysis.md` | TRIM verb noted (was "no verb factorization") |
| `mkdocs.yml` | "The Drift" nav label |
| `ORMSandbox/.../MCDocType.java` | `getConnection()` → `conn` (compile fix from other session's schema work) |

### To commit

```bash
git add docs/LAST_MILE_PROBLEM.md docs/index.md docs/BOMBasedCompilation.md \
        docs/SampleHouseAnalysis.md mkdocs.yml \
        ORMSandbox/src/main/java/com/bim/ormsandbox/po/MCDocType.java
git commit -m "[S96-polish] The Drift rewrite + landing page + BBC banner + SH TRIM

LAST_MILE_PROBLEM.md rewritten as 'The Drift' — 11 spec-cited drift points,
C_Order flow in spec fidelity, Compiere logging convention, banner.
Landing page: Problem Statement, no prose/card repetition.
BBC: 'Everything is a BOM' banner, 'draw it' tagline.
SH: TRIM verb documented. MCDocType compile fix.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

Then deploy: `.venv/bin/mkdocs gh-deploy --force`

## Already Committed This Session (4 commits)

1. `a357d615` — Number consistency (64→75 verbs, 392→408 tests), MANIFESTO reading order fix, ShipYard promoted, inline glossary, pipeline I/O labels
2. `a16a2c02` — Legacy/superseded labels removed from DATA_MODEL, BBC, WorkOrderGuide
3. `647d3224` — BONSAI_EXTENSIONS→Enterprise.md rename, nav merge (Enterprise+Extensions), Last Mile to Start Here
4. `90b4afbf` — Last Mile as top-level tab, Engine merged into Specs, Disciplines into Enterprise
5. `4f645f9c` — Key Terms + Last Mile paragraph removed from landing page body

## What Was Discussed But NOT Yet Done

1. **FINE logging for verb detail** — prompt written at `prompts/48_fine_log_compile.md`. Another session should run SH + TE with FINE logging to capture which verbs fire, then update The Drift's Pipeline Debug section with real examples
2. **Standalone GLOSSARY.md** — Key Terms removed from landing page. A full glossary (12 terms) could be a standalone page under Guides. Low priority
3. **Remaining stale anchors** — mkdocs build shows INFO warnings about broken cross-doc anchor links (e.g. `TestArchitecture.md#rosetta-stone-coverage-s58c`). These are pre-existing

## User Preferences (important for this docs work)

- **"Gift to the world" stays** — MSC Vision 2020 FOSS spirit, not self-promotion
- **No clutter on landing page** — cards are the hero, prose sets up the problem only
- **Last Mile = top-level tab** — nerdy pull for experts, not buried in a dropdown
- **Banners must be short** — one bold line, not a paragraph
- **Session refs (S96, R21, etc.) are OK in internal docs** but not in public-facing ones (index, MANIFESTO, BBC, Rosetta Stone, BIMERPPaper)
- **Don't run YAML compilation** — check existing logs instead
- **Don't commit until reviewed** — proof-read together first, commit at the end

## Reading Order for Context

1. `docs/index.md` — current landing page state
2. `docs/LAST_MILE_PROBLEM.md` — The Drift (just rewritten)
3. `mkdocs.yml` — current nav structure
4. `docs/BOMBasedCompilation.md` lines 1-10 — tagline + banner
