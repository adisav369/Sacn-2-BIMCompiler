# ⚠ DO NOT REMOVE — Docs-Wide Review Prompt
# Scope: Review and update ALL docs/ for accuracy, consistency, and current state.
# Read the log after every run.

## Objective

Audit every `.md` file in `docs/` and update stale numbers, feature claims, links, and language to reflect the current state of BIM OOTB as of the `bim-ootb` repo migration (May 2026).

## Ground Truth (verify these from code/git, don't trust this list blindly)

### Key Numbers
- **Viewer modules:** count JS files in `bim-ootb/viewer/*.js`
- **Playwright tests:** count specs in `bim-ootb/tests/specs/*.spec.js` + unit tests in `tests/test_*.js`
- **Buildings in gallery:** count entries in BUILDINGS object in `bim-ootb/index.html`
- **Buildings on OCI:** `oci os object list --bucket-name bim-ootb --prefix buildings/ --all` — count unique `_extracted.db`
- **Locales:** count files in `bim-ootb/viewer/locales/`
- **Rate templates:** count files in `bim-ootb/viewer/rates/` (exclude `custom_template.json`)
- **Sprints:** grep `S2[0-9]` from `git log --oneline` — count unique sprint tags
- **Commits:** `git log --oneline --after=2026-04-19 | wc -l` (OOTB era)
- **Lines of JS:** `wc -l bim-ootb/viewer/*.js` total
- **Three.js version:** check `bim-ootb/viewer/lib/three.module.min.js` header or scene.js comments

### Timeline
- October 2025: BIM Intent Compiler concept
- January 25, 2026: first code commit (Java compiler)
- April 20, 2026: S200 — BIM OOTB browser viewer born
- May 23, 2026: migrated to `bim-ootb` repo, GitHub Pages production

### Architecture (current)
- Repo: `github.com/red1oon/bim-ootb` — code only, no .db files
- Deploy: `git push` → GitHub Pages at `red1oon.github.io/bim-ootb/`
- Building DBs: OCI `bim-ootb` bucket only (not bim-ootb-live, not bim-ootb-dev)
- Structure: `index.html` (landing), `viewer/viewer.html` (3D viewer)
- No server. No build step. No framework. Vanilla ES6.

### Features Shipped (S200–S271)
- 3D viewer: Three.js r160 ESM, BatchedMesh, DLOD, frustum culling
- 2D plans: section cuts, elevations, grid overlay, dimension chains
- IFC import: web-ifc WASM, IFC2x3 + IFC4, multi-format (OBJ/STL/DAE/GLB/FBX/3DS)
- IFC export: DB → .ifc STEP text builder
- Clash detection: 12 discipline-pair rules, matrix, report
- 4D time machine: construction sequence from BOM
- 5D cost estimation: 17 country rates, forex, Excel export
- NLP search: keyword intent, voice input, no LLM
- Grid system: drag, scissors, kinematics, door arcs, contours, ceiling grids
- ERP: iDempiere AD from PostgreSQL → SQLite → browser, 23 tables, FTS5
- BOM engine: extraction + verb expansion, 100% JavaScript
- Grid kinematics: drag grid line → cascade recompile building
- PWA offline, service worker
- City mode: 786 buildings
- Share sheet, variation orders, print sheets
- Mobile optimised: DPR=1, no antialias, rAF gate

## What To Fix

### 1. Stale numbers
Search all docs for these patterns and verify against ground truth:
- `17K lines` or `17,000 lines` — recount
- `72 tests` or `53 tests` or any test count — recount
- `16 modules` or module counts — recount
- `r128` or `r156` Three.js version — now r160
- `35 buildings` or building counts — recount
- `15 locales` — now 18
- `2,475 products` or `77 verbs` — verify from Java source or note as "BIM Compiler era"
- `48,428 elements` — verify Terminal count from DB or manifest

### 2. Stale links
Search for and update:
- `objectstorage.ap-kulai-2...bim-ootb-full` → `red1oon.github.io/bim-ootb/`
- `objectstorage.ap-kulai-2...bim-ootb-dev` → `red1oon.github.io/bim-ootb/`
- `objectstorage.ap-kulai-2...bim-ootb-live` → `red1oon.github.io/bim-ootb/`
- `github.com/red1oon/BIMCompiler` repo links → `github.com/red1oon/bim-ootb` (for product links, NOT for docs links which stay at BIMCompiler)
- `deploy/dev/` or `deploy/sandbox/` path references → `bim-ootb/viewer/`
- `sandbox/index.html` → `viewer/viewer.html`

### 3. Stale architecture claims
- "Two DBs" → now single DB standard (library merged into extracted) for most buildings. Split (meta+geo) for large buildings only.
- "Blender/Bonsai viewport" → replaced by browser Three.js viewer. Blender is historical.
- "Python" extraction → replaced by Node.js extractor. Python archived.
- "Java BOM engine" → BOM Walker ported to JavaScript (S267). Java is historical.
- "iDempiere backend" → ERP AD runs in browser from SQLite, no iDempiere server needed
- References to `deploy/live/`, `deploy/sandbox/` as deploy targets → GitHub Pages now
- OCI bucket descriptions — only `bim-ootb` for building DBs. All code buckets deprecated.

### 4. Language consistency
Ensure these terms are used consistently:
- "BIM OOTB" (not "BIM Out Of The Box" in running text — spell out only on first use)
- "viewer" (not "sandbox", not "demo")
- "landing page" (not "index page" when referring to the gallery)
- "building database" or "building DB" (not "extracted DB" in user-facing text)
- "grid kinematics" (not "grid drag" when referring to the cascade engine)

### 5. Docs to pay special attention to
- `index.md` — homepage, first impression
- `ROADMAP.md` — just rewritten, verify consistency with other docs
- `FeatureComparison.md` — competitor comparison, numbers must be current
- `BIM_Designer_Browser.md` — browser viewer spec
- `NEW_FROM_REFERENCE.md` — Red Pill / grid kinematics spec
- `MOBILE_DEPLOY.md` — mobile architecture
- `DEPLOYMENT.md` — deploy instructions
- `USER_GUIDE.md` — user-facing guide
- `AboutMore.md` — public-facing about page
- `ERP.md` / `ERP_Roadmap.md` — ERP feature state

## Process

1. `grep -rn` for each stale pattern across all `docs/*.md`
2. For each hit, verify the correct current value
3. Update in-place — don't rewrite entire docs, just fix the stale parts
4. After all fixes: `mkdocs build --strict 2>&1 | grep -i error` — must be clean
5. Deploy: `mkdocs gh-deploy --force`

## Do NOT
- Rewrite docs that are architecturally sound — just fix numbers and links
- Add new docs — this is a review, not a writing task
- Change the BIM Intent Compiler narrative (BOM = source of truth, geometry = compiled output)
- Remove historical references to Java/Python/Blender — mark them as historical, don't delete
- Touch `SPATIAL_COMPILATION_PAPER.md` — it's a published paper, frozen
