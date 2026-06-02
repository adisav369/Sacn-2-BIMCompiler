# ⚠ DO NOT REMOVE — Scope guard
# Scope: SPEC for giving the ERP app its OWN folder home inside bim-ootb, separating it
#        from the BIM viewer it currently shares `viewer/` with. Read the log after every
#        run; non-invent (every file/path traces to a real grep, never assumed).
# OUT OF SCOPE: any feature work, the migrate ShowMe overlay, BIM changes. This is a
#        STRUCTURAL move only — same code, new home, nothing breaks.
# DISCIPLINE: bim-ootb deploys via PR to protected `main` (GH_DEPLOY.md). Branch off
#        origin/main. Coordinate with the renderer session (paused, branch
#        feat/idempiere-login) — confirm it is at a clean checkpoint before moving its
#        files. EXPLICIT GO before any deploy. The renderer #1 (idempiere.html) is LIVE
#        (sw v561) — a wrong path = a broken production app.

---

# ERP folder home — separating the ERP app from the BIM viewer (spec)

## Why this exists
`bim-ootb/viewer/` is named for the BIM 3D viewer but has grown to host a second, peer
product — the ERP app (`erp.html`, `idempiere.html`, the `ad_*`/`erp_*` modules, the AD
seed DB). The mixed folder is the smell ("isn't viewer for BIM?"). The ERP app deserves
its own home: clean separation of concerns, ERP-only deploy/precache cadence, and an
honest directory name.

## The decisive finding — coupling is near-zero (extracted 2026-06-02)
Measured, not assumed. The ERP HTMLs load their JS by classic `<script src="x.js?v=22">`
globals. Their full script footprint and overlap with BIM:
- **ERP HTMLs (`erp.html` + `idempiere.html`) load 8 JS files:** `ad_data.js`,
  `ad_graph.js`, `ad_parser.js`, `ad_ui.js`, `erp_pills.js`, `icons.js`,
  `idmp_session.js`, `pill_builder.js`.
- **Shared with the BIM `viewer.html` (76 JS): exactly ONE** — `pill_builder.js`.

So the split is cheap: there is essentially nothing entangled. One shared file is the
only real decision; everything else is cleanly ERP-owned or cleanly BIM-owned.

## The fork — what "own home" means (DECIDE THIS FIRST)
- **Option A — subfolder in the same repo/site (`bim-ootb/erp/`).** Reorganize within
  bim-ootb. Shares the one GitHub Pages site, the `lib/` (Three.js / sql-wasm), and the
  single PWA service worker. URLs change `…/viewer/erp.html` → `…/erp/erp.html`. Cheap,
  reversible, immediate clarity. **RECOMMENDED first step.**
- **Option B — separate repo + own GitHub Pages site.** Full product independence: own
  `sw.js`/`manifest`/CI/URL. Heavier (duplicate the PWA shell + `lib/`, new deploy
  pipeline). The eventual destination IF the ERP becomes a standalone shipped product.

Recommendation: do **A now** (it delivers the separation today with minimal risk), keep
**B** as the later graduation. The rest of this spec assumes **A**.

## File inventory (verify each loader before moving — see "Unknowns")
ERP-owned → move to `bim-ootb/erp/`:
- **HTML:** `erp.html`, `idempiere.html`
- **JS (erp_):** `erp_panel.js`, `erp_persist.js`, `erp_pills.js`, `erp_replay.js`,
  `erp_search.js`, `erp_signer.js`
- **JS (ad_):** `ad_charts.js`, `ad_data.js`, `ad_graph.js`, `ad_parser.js`,
  `ad_table_map.js`, `ad_ui.js`
- **JS (other):** `idmp_session.js`, `menu_seed.js`, `role_band.js`
- **Data/assets:** `ad_seed.db`, `initbubble.json`, `pills.json`, `aplus.png`,
  `redpill.png`

Confirmed loaders (grep): `erp.html` → `erp_persist/replay/search/signer`, `ad_charts`;
`ad_ui.js` → `ad_charts.js`; `erp_panel.js` → `role_band.js`.

**Unknowns — resolve before moving (no silent move):** `erp_panel.js`, `ad_table_map.js`,
`menu_seed.js` have **no static loader** in any `*.html`/`*.js`. They are loaded
dynamically, by an inline script, or are dead. For each: find the real loader (grep inline
`<script>` blocks + any dynamic `createElement('script')`), or prove it is dead and log
that decision. Never move (or delete) one on assumption.

Shared infra — **the one real decision:** `pill_builder.js` is loaded by BOTH `erp.html`
and `viewer.html`. Choose: (a) leave it in a `bim-ootb/common/` both apps reference (no
drift), or (b) duplicate into `erp/` and `viewer/` (independent, risks divergence).
Recommend **(a) `common/`**. Classify the other generic helpers the same way by full grep
before deciding (`icons.js`, `tour.js`, `helpers.js`, `config.js`, `qrcode.min.js`,
`share.js`, `settings_editor.js`, `kernel_ops.js`, `error_reporter.js`, `locale_loader.js`,
`lib/`): each is ERP-only, BIM-only, or `common/` strictly by who loads it.

## The move (Option A) — mechanical, faithful
1. **Branch:** off `origin/main` (clean), e.g. `feat/erp-folder-home`. Do NOT build on the
   renderer's `feat/idempiere-login`.
2. **Confirm loaders:** resolve the three Unknowns; finalize the ERP / common / BIM
   partition by grep (every file classified by its actual loader).
3. **Create `bim-ootb/erp/`** and `git mv` the ERP-owned files (preserve history).
4. **Rewrite references:** every `<script src="…">` and asset path in `erp.html` /
   `idempiere.html` to the new locations; `common/` files referenced from both apps.
5. **Service worker:** `viewer/sw.js` precache currently lists ERP files (`erp_search.js`,
   `ad_charts.js`, and the HTMLs/icons). Repoint those entries to `erp/…`. Bump
   `CACHE_VERSION` (currently `v561` → `v562`). Decide sw scope: one root sw covering
   `/viewer/` + `/erp/` (Option A) keeps one PWA.
6. **Cache-bust:** the script tags use `?v=22`; bump on the moved tags so clients refetch.
7. **Landing / links:** update any link into `erp.html`/`idempiere.html` (landing
   `index.html`, pills, `sandbox/index.html` redirects) to `/erp/`.
8. **Reroute stubs at the OLD URLs (REQUIRED, user 2026-06-02).** Leave a redirect at the
   old paths so existing bookmarks/links keep working — do NOT just delete the old file:
   - `viewer/erp.html` → `/erp/erp.html` (explicitly requested).
   - `viewer/idempiere.html` → `/erp/idempiere.html` — **mandatory**: renderer #1 is LIVE
     at the old URL; without the stub, every deployed link breaks. Mirror the existing
     `sandbox/index.html` redirect pattern (meta-refresh + JS `location.replace`,
     preserving any query string). Keep the old paths in the sw precache pointing at the
     stubs (or let them 200 as tiny redirects) so offline clients also reroute.

## Witnesses (§-log first; read the log, not the exit code)
- `§ERP-HOME partition erp=N common=M bim=K unknowns-resolved=Y` — every file classified
  by a real loader; the three Unknowns resolved (loader named or proven dead).
- `§ERP-HOME refs erp.html=ok idempiere.html=ok broken=0` — every `<script src>`/asset path
  in the moved HTMLs resolves to a real file (no 404).
- `§ERP-HOME sw precache=ok moved-entries=E version=v562` — sw precache lists the new
  paths, no stale `viewer/…` ERP entries, version bumped.
- `§ERP-HOME smoke erp=loads idempiere=loads bim=loads` — all three apps boot clean
  (pageerror=0) from the new layout; renderer #1 (`idempiere.html`) unbroken.
- `§ERP-HOME reroute old-erp→/erp/erp.html=ok old-idempiere→/erp/idempiere.html=ok
  query-preserved=Y` — both old URLs redirect to the new home (old references in the wild
  keep working); query string preserved.
- `§ERP-HOME bim-regression=none` — BIM viewer untouched in behavior (its 76 JS still
  resolve; only `pill_builder.js`'s home may have changed via `common/`).

## Acceptance
DONE when: the ERP app lives under `bim-ootb/erp/`, all three Unknowns are resolved, every
moved reference resolves (no 404), `sw.js` precache is repartitioned + version-bumped,
**reroute stubs at the old `viewer/erp.html` AND `viewer/idempiere.html` redirect to the
new `/erp/` URLs (old references in the wild keep working)**, and `erp.html` /
`idempiere.html` / `viewer.html` all boot clean — witnessed. Then PR to protected `main`
(EXPLICIT GO). Option B (separate repo/site) is a later graduation, not this task.

## Honest caveats
- **URLs change** (`/viewer/erp.html` → `/erp/erp.html`). Reroute stubs at BOTH old URLs
  are REQUIRED (step 8), not optional — old references to `erp.html` and the live
  `idempiere.html` are in the wild and must keep working.
- **One shared file** (`pill_builder.js`) forces the `common/` vs duplicate call — make it
  deliberately, don't let a move decide it implicitly.
- **The renderer #1 is LIVE.** A wrong path ships a broken production ERP. Smoke all three
  apps before the PR; deploy only on EXPLICIT GO.
- This is a **structural move only** — zero behavior change. If a diff changes behavior,
  it is out of scope and a bug.

## Status
SPEC, 2026-06-02. Grounded in a live grep of `bim-ootb/viewer/` (coupling = 1 shared file).
Companion: `docs/ERP.md §0.10a` (the migrate ShowMe — its overlay currently targets
`viewer/` and will move here with the rest of the ERP code). Execute on a branch off
origin/main, coordinated with the (paused) renderer session; PR to protected main.
