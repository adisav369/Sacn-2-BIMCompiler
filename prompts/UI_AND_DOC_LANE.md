# ⚠ DO NOT REMOVE — Scope guard
# **Lane: UI Look-&-Feel + the evaluator DOC (comparison paper).** Distinct from the backend/substrate lane
#       (prompts/BACKEND_SUBSTRATE_LANE.md) — they share only a few files (see §SHARED below). Read the log
#       after every run (exit code is NOT evidence). Honour this block until every item is ✅ DONE or ⛔ BLOCKED.
# NON-NEGOTIABLE: NON-INVENT — every number in the paper traces to a real source file (keep the footnotes +
#       GAPS); whitebox §-log first, Playwright second; KISS / follow existing pill + panel patterns; use the
#       MkDocs pipeline for docs (NOT a hand-rolled viewer). Deploy only on the user saying "deploy".
# Read first: memory MEMORY.md + [[project_erp_sync_fsm]] (UI L&F HANDOFF para) + [[reference_gh_deploy]]
#       (DOCS SITE section) + [[feedback_pill_icon_consistency]] + [[feedback_kiss_best_practice]].
# §SERVE (standing): keep the LOCAL MkDocs preview live for the user to review every change —
#       `scripts/serve_docs.sh` → http://127.0.0.1:8000/MigrateComparisonPaper/ (mkdocs serve auto-reloads on
#       docs/ + mkdocs.yml edits; restart it if you change mkdocs.yml). After EACH doc change, confirm the page
#       renders on the local URL and tell the user to refresh — do NOT push to the live docs site until "deploy".

---

## State at handoff (2026-06-08)
- The evaluator comparison paper EXISTS: `docs/MigrateComparisonPaper.md` (thesis + ASCII diagram + ONE dense
  11-row vitals table + Method/honesty + GAPS + per-cell footnotes). Renders via MkDocs.
- `mkdocs.yml` (this repo, feat/revit-plus-lens working tree) has: a `Start Here → "Migrate & Compare (ERP)"`
  nav entry AND mermaid enabled (pymdownx.superfences custom_fence). **These edits are UNCOMMITTED.** NOTE:
  master's `mkdocs.yml` nav differs (no "Feature Comparison" row) — re-apply the nav entry against master's actual
  Start Here block when deploying.
- Local review server is/was running: `scripts/serve_docs.sh` → `http://127.0.0.1:8000/MigrateComparisonPaper/`.
- DOCS DEPLOY (captured, 2 months): `docs/**`+`mkdocs.yml` push to **BIMCompiler master** → `.github/workflows/
  docs.yml` (`mkdocs gh-deploy --force`, NOT `--strict`) → `https://red1oon.github.io/BIMCompiler/<Doc>/`.
- App-side (bim-ootb): PR #197 ALSO copied the paper + 5 deep docs + a hand-rolled `erp/migrate_compare.html`
  viewer into `bim-ootb/erp/`, linked from erp.html + idempiere.html. **This duplicates the MkDocs site — see §D1.**

## OUTSTANDING (work top-to-bottom to zero)

### §DOC-1 — paper readability (USER FEEDBACK: "not enough good table graphs for readability")
✅ FIRST PASS DONE (2026-06-08), rendering on the local URL — user reviewing:
- ✅ "At a glance" stat-card strip (0 round-trips · ~53× bootstrap · ≈89× fewer LOC · ≈3.5× smaller seed),
  HTML cards via md_in_html.
- ✅ ASCII diagram → **mermaid** flowchart (mermaid enabled in mkdocs.yml superfences custom_fence) comparing
  legacy round-trip vs op→local-kernel→fold→paint, citations beneath.
- ✅ single 11-row table → 3 THEMED tables: A·Speed & latency, B·Footprint & bloat, C·Migration & ownership.
  All numbers + footnotes + GAPS + not-feature-parity caveat preserved verbatim (NON-INVENT held).
- ✅ TITLE BANNER (matches docs/index.md style: centered #263238 + orange #ff9800 left-border) — "Migrate &
  Compare" large + a smaller uppercase byline. The plain `# H1` was REMOVED so the banner is the title
  (mkdocs uses the nav title for the tab); keep it that way unless the user wants the H1 back.
- ⚠ GOTCHA: mkdocs `serve` live-reload sometimes MISSES Edit-tool writes → the page looks stale. Fix = kill &
  relaunch `mkdocs serve` (a fresh build picks them up). Verified renders: 1 mermaid, 4 cards, 3 tables.
- OPEN: await the user's further readability feedback; iterate on the SAME local URL (re-serve after edits).
  Possible next: per-cell visual emphasis, a small bar/spark for the bloat ratios, or a TL;DR admonition.

### §D1 — DECIDE the app↔doc link model (needs user)
The MkDocs site is the canonical home. The in-app `erp/migrate_compare.html` + 5 copied deep `.md` in PR #197
duplicate it and risk drift. Options: (a) point erp.html/idempiere.html "How this compares" links at the LIVE
docs URL (`https://red1oon.github.io/BIMCompiler/MigrateComparisonPaper/`) and REMOVE the in-app copies — simple,
no drift, but breaks offline; (b) keep the in-app viewer for offline PWA, treat docs site as the canonical/source.
⛔ ASK the user which; until decided, do not re-touch the bim-ootb copies.

### §UI-1 — Install/Migrate pills visible for testing
Gate: `erp/idmp_pills.js` `_applyStage` (~line 53) sets `pre-client` pills (Install/Migrate) to `pill=false`
once a client is committed (§C GATE-2). Add a TESTING override so they show in-client too. ⛔ DECISION needed on
the mechanism: URL param (e.g. `?showpills=1`), an always-on dev flag, or a settings toggle — ask the user, then
implement minimally + witness `§IDMP-LIFECYCLE` shows them shown in-client under the override.

### §UI-2 — idempiere left menu collapsible
`#idmp-menu` (CSS ~line 56: `flex:0 0 262px`, holds `#idmp-tree`). A mobile drawer already exists
(`#idmp-menu-backdrop` @media). Add a DESKTOP collapse toggle (pattern: glassbowl.html `#panelToggle`/`togglePanel`
— collapse to 0 width, a small re-open handle, persist the state). Witness: toggle hides/shows the panel,
tree still works, no pageErrors.

## §SHARED — coordination with the backend lane (avoid collisions)
The substrate lane touches kernel_ops/erp_period_close/period_close_ui (SEPARATE files — no clash). The ONLY shared
files are: `erp/idempiere.html`, `erp/sw.js` (CACHE_VERSION = the conflict magnet → take the HIGHER version, keep
BOTH precache lists on conflict), and the pills config. **Sequence:** do bim-ootb UI work in a `/tmp/wt-*` worktree
off FRESH `origin/main` AFTER PR #197 lands (#197 already edits idempiere.html + bumps sw v600). Never edit the
shared `~/bim-ootb` tree directly (PreToolUse hook blocks it).

## Done = §DOC-1 improved + re-reviewed, §D1 decided, §UI-1 + §UI-2 witnessed; deploys HELD for "deploy".
## Append a # DONE ledger with a §-line per item.
