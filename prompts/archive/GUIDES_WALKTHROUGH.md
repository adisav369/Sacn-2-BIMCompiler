# ⚠ DO NOT REMOVE — Guides Walk-Through Enhancement (new session)
Scope: make the user comfortable **passing through the front doors** of BIM/ERP OOTB with
screenshots + numbered steps. Initial walk-through only — **deeper feature docs come later**.
Read the log after every screenshot/deploy run. Honour until DONE.

## Goal (user, 2026-06-20)
Triage the guides; add **more screenshots + steps** so a new user can confidently enter each front
door. Not exhaustive feature coverage yet — just "what do I click first, and what happens."

## The front doors (interactions VERIFIED in source — re-verify before writing, non-invent)
1. **Matrix landing** `index.html` — first visit / cleared cache → red/blue pill → round selector.
   **Refresh → big round selection; return (Home/back) → compact `⋯` rail.** Trash = clear building
   IndexedDB + back to red/blue gate (keeps app caches). Pick docks the icon to the `⋯`.
   Doc launcher (lightbulb) → this User Guide. *Asset:* `docs/assets/matrix_landing.png` ✅
2. **Buildings page (BIM Viewer on-ramp)** `gallery.html` — **Drop IFC, Save As, or pick a ready-made
   building** → opens the 3D/2D viewer on THAT building. (NB: bare `viewer/viewer.html` defaults to the
   WH/Pick POS view — do NOT link users there; always enter via the buildings page.)
   *Asset:* `docs/assets/buildings_page.png` ✅
3. **Kernel-ERP bubbles** `erp/erp.html` — the bubble launcher (verified: "erp.html is the bubble
   LAUNCHER"). Click/**explode** or **long-press** a bubble → enters `idempiere.html` directly (the
   iDempiere renderer #1, via `?window=` forward). The **`⋯` 3-dots** rail → **Glass** (`glassbowl.html`)
   / **Gravity** (`glassbowl_gravity.html`) — they are pill icons in erp_pills (pills.json glassbowl/gravity).
   *Asset:* `docs/assets/erp_bubbles.png` ✅  (re-verify the exact tap-vs-long-press in the globe JS.)
4. **DAGeVu Modeller** `viewer/modeller.html` — toolbar icon index already in `docs/ModellerGuide.md`.
   ✅ DONE "Your first insert — the BOM catalog" step added + `modeller.png` (INSERT · BOM CATALOG:
   search + chips All/Structure/Openings/Furniture/Sets; assemblies by level Buildings/Floors/Rooms/
   Sets/Items; aim-on-grid → R rotate → Elev → click = one undoable signed op). Verified in modeller.html.

## Done already (this session)
- `docs/USER_GUIDE.md` — welcome + page index. Index links FIXED: BIM Viewer → `gallery.html`
  (buildings page, not WH/Pick `viewer.html`); Kernel-ERP → `erp.html` (bubbles, not `idempiere.html`);
  redundant Buildings row folded in. Screenshot of the Matrix landing embedded.
- `docs/ModellerGuide.md` — created (minimal intro + icon index).
- 3 screenshots captured & banked under `docs/assets/` (above).

## TODO (the walk-through)
- ✅ DONE **Viewer Guide** `docs/BIMUserGuide.md` — Quick Start rewritten as a step walk-through from the
  **buildings page** (gallery.html): pick a ready-made building / drop your own IFC / merge-into-existing →
  viewer. `buildings_page.png` embedded. Local-setup cmd now opens gallery.html (was landing.html).
  Verified live: red1oon.github.io/BIMCompiler/BIMUserGuide/ (200, "Three ways in" + image).
- ✅ DONE **ERP Guide** `docs/ERPUserGuide.md` — new "bubbles front door" section with `erp_bubbles.png`
  + the gesture table VERIFIED in `ad_graph.js` (tap bubble→dive into records; long-press/double-tap→real
  iDempiere via idempiere.html; `⋯` rail→Glass `glassbowl.html` / Gravity `glassbowl_gravity.html`).
  Existing login/POS/report flow follows unchanged. Verified live (200, "bubble launcher" + image).
- Deployed via `scripts/safe_gh_deploy.sh` (W-DEPLOY-GUARD PASS, 415→414 only blessed `.nojekyll`).
  Committed+pushed on feat/erp-substrate-phase012 (0 local-only). NB: script canary list has 2 stale
  entries (RetailScaleStory, glassbowl) that live on the bim-ootb site, not BIMCompiler — false 404s.
- ✅ DONE **USER_GUIDE** — per-surface "First run" 1-2-3 blurbs added (Viewer / Modeller / ERP), verified
  live (3 blurbs on gh-pages).
- ✅ DONE (housekeeping) pruned 2 stale canaries (RetailScaleStory, glassbowl — live on bim-ootb, not
  BIMCompiler) from `scripts/safe_gh_deploy.sh`; deploys now run 5/5 200 with no false 404 warning.
- Consider a tiny screenshot per door (red/blue pill, the `⋯` rail open) if it helps comfort. (deferred —
  optional comfort polish only)

## Mechanics
- Screenshots: headless puppeteer (`~/bim-compiler/node_modules/puppeteer`) against the LIVE GH-Pages
  URLs (see this session's `/tmp/shot_*.js`); write PNGs to `docs/assets/`.
- Publish docs ONLY via `scripts/safe_gh_deploy.sh` (guard PASS; bless `.nojekyll` via
  `ALLOW_SHRINK=1 paths=".nojekyll"`). Read `build/docs_deploy.log`. Verify live with curl after.
- prompts/ is gitignored (local). Docs live = red1oon.github.io/BIMCompiler/.
