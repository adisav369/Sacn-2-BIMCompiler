# BONSAI MODELLER → MORPHEUS WIRING — New-Session Handoff Card

> **SHIPPED 2026-06-18:** All 4 tasks DONE + witnessed. LIVE URL = `https://red1oon.github.io/bim-ootb/viewer/modeller.html`
> (project-pages base `/bim-ootb/`, NOT root — the URLs below were wrong). PR #370 modeller go-live (live smoke PASS);
> PR #371 modeller Lucide icons + authoring audio feedback + Sound toggle (W-BONSAI-MODICONS); PR #372 Morpheus
> index2.html wiring: red-pill preload shower + ⋯-rail Bonsai-tree pill → modeller (W-BONSAI-MORPHEUS). Bonsai-tree
> icon = verbatim Lucide tree-deciduous. **OOTB ROADMAP (user-confirmed, see BONSAI_KERNEL_RESEARCH.md §OOTB):**
> 3 authoring modes on one signed op-log — sketch · **insert library component @ LOD** · **RouteWalker→MEP sweep** —
> + the common PillBuilder rail on the modeller + the ERP join (Outliner→Project→Order). NEXT eng plumbing:
> unify the +Wall/+Opening quick buttons through the signed op-log (today they bypass it → not in outliner/history/IFC).

```
# ⚠ DO NOT REMOVE
SCOPE: Wire the PROVEN Bonsai modeller (our BIM geometry-authoring surface) into the MORPHEUS
landing app-shell as its BIM face. The modeller IS the "BIM → Bonsai look-alike" the Morpheus
doctrine asks for — this session connects the two, then settles deploy.
DOCTRINE (unchanged): BIM → Bonsai look-alike · ERP → iDempiere · `⋯` rail = the ONLY proprietary
chrome on both. ONE signed op-log (kernel_ops) backs ERP records AND BIM geometry.
LOG MANDATE: after ANY witness run, READ the .log before conclusions. Deterministic, non-invent.
PRIME RULE: never touch the present `viewer.html`; the modeller is a SEPARATE alternative surface.
STATUS: modeller spine COMPLETE + visually confirmed (13 kernel witnesses). NOT yet deployed, NOT yet
wired into Morpheus. This card = the wiring + deploy session.
```

## ✅ TASKS — DO THIS SESSION (ordered)
0. **Sync first:** `git -C ~/bim-ootb fetch origin && git -C ~/bim-ootb merge --ff-only origin/main`; work the
   modeller in a `/tmp/wt-*` worktree off `feat/bonsai-kernel-viewer` (NEVER edit the shared checkout / never `viewer.html`).
1. **GO-LIVE (outward-facing — confirm with user):** squash-merge `feat/bonsai-kernel-viewer` → `main`
   (`gh pr create` then `gh pr merge --squash`). `deploy-pages.yml` auto-deploys. SMOKE-TEST LIVE: fetch
   `red1oon.github.io/viewer/modeller.html`, author a wall, confirm `§BONSAI author … inScene=true` (Log Mandate —
   verify the `minify_pages.js` step didn't break the module worker / `lib/kernel`). Branch is deploy-ready (22MB wasm committed).
2. **WIRE into Morpheus** (`index2.html` in `/tmp/wt-landing`, spec §12): BIM face = our modeller. ENTRY PATH =
   `red pill → ⋯ rail → Bonsai tree icon → modeller.html`. Witness the boot (Morpheus BIM face authors a wall).
3. **RED-PILL PRELOAD + STATUS SHOWER:** on red-pill pick call `Bonsai.preload({assets:[occt-wasm 22MB warm,
   erp/ad_seed.db 26MB]}, onProgress)` and render the loader-style shower during the pulse (engine primitive DONE).
4. **ICON REDO** (`[[feedback_pill_icon_consistency]]`): design a Lucide line **Bonsai-tree** icon for the rail launcher
   + replace the modeller's placeholder glyphs (`▦ ✎ ✂ ⤓`, `▼/▸`) with Lucide line icons. No unicode on the shipped surface.
5. **DEFERRED (only if time / explicit go):** PillBuilder `⋯` rail extras + Find-retrain (Room/Phase → ERP Project→Order
   via `Bonsai.outliner.addCategory`) — as a SHARED module with the viewer, NOT a fork. See DEFERRED section.

## ▶ READ FIRST (next session)
1. `prompts/BONSAI_KERNEL_RESEARCH.md` — the full modeller lane (research → 13 witnesses → §RESUME).
2. `prompts/LANDING_APP_SHELL_SPEC.md §12` — the Morpheus landing app-shell spec.
3. Memory: `[[project_landing_appshell]]` (Morpheus front door) + `[[project_bonsai_kernel]]` (modeller).
4. Screenshot of the running modeller: `~/Pictures/Screenshots/bonsai_modeller_outliner.png`.

## WHAT EXISTS (the thing to wire)
- **Branch `feat/bonsai-kernel-viewer` on bim-ootb** (worktree was `/tmp/wt-bonsai`), PUSHED, 0 local-only.
- **`viewer/modeller.html`** = the alternative authoring viewer (Blender/Bonsai-faithful). Reuses the prod
  three.js stack (overlay standard `three.module.min.js` for WebGLRenderer; copy frozen ESM namespace mutable).
- Host modules (all `viewer/bonsai_*.js`, lazy): `bonsai_kernel(_worker).js` (occt in a module Worker),
  `bonsai_sketch.js` (planegcs), `bonsai_oplog.js` (SIGNED kernel_ops chain), `bonsai_kernel.js`
  (foldChainToScene), `bonsai_grid.js` (architectural grid + snap), `bonsai_ifc.js` (web-ifc export),
  `bonsai_outliner.js` (Blender Outliner = the richer Find, CATEGORY-DRIVEN via `addCategory`).
- Vendored: `viewer/lib/kernel/` (occt dist; **`occt-wasm.wasm` 22MB GITIGNORED** = slim-build/deploy follow-up),
  `viewer/lib/planegcs/` (500K wasm committed). web-ifc already in the viewer.
- **13 kernel witnesses GREEN:** 6 headless (`build/kernel/poc_kernel_*.js` in bim-compiler) +
  7 in-viewer (`viewer/tests/bonsai_*_live.js`: kernel/sketch/recipe/signed/ifc/grid/outliner). Re-run any:
  `node viewer/tests/bonsai_<name>_live.js` (puppeteer/SwiftShader; Node18 can't host occt → Chromium).
- Whole pipeline proven: **sketch → grid-snap → planegcs solve → signed commit → fold → occt → render → IFC export**,
  with the Outliner reflecting the signed feature tree (grid refs `Wall A-1·B-1`, signed tip in footer).

## THE WIRING TASK (this session's core)
The Morpheus front door (`index2.html`, lives in `/tmp/wt-landing/` — **NOT** `index.html`) presents BIM and ERP
faces. Its BIM face should now be **our modeller** (it already looks like Bonsai). Wire it:
- **ENTRY PATH (user-specified 2026-06-18):** `red pill` → `⋯` three-dots rail → **Bonsai tree icon** → launch the
  modeller. (Mirror the ERP face's route to iDempiere.) The Bonsai tree icon is the modeller's launcher in the `⋯` rail.
- Make that Bonsai-tree pill launch/embed `modeller.html`.
- Keep the `⋯` rail as the only proprietary chrome on both faces (doctrine). The modeller currently has a
  PLACEHOLDER top button-bar — moving its EXTRAS (history/IFC/grid/signed) into the real **PillBuilder `⋯` rail**
  is the chrome convergence with Morpheus (see DEFERRED below — do it as the shared-module program, not a fork).
- EXTRACT real `pill_builder.js` / `ICONS` / `whole_history` — never mock (landing doctrine).
- Witness the wiring (the Morpheus BIM face actually boots the modeller + authors a wall) — in-viewer §-log.

## RED-PILL BACKGROUND PRELOAD + STATUS SHOWER (user 2026-06-18) — engine half DONE, UI half = this session
On `red pill` pick, hide the heavy load behind the pulsing transition: background-fetch BOTH faces' big assets
with a Matrix-style "what's loaded" status shower (reuse the `loader.js` named-progress pattern), so whichever
icon the user picks (Bonsai tree / iDempiere) the surface is already hot.
- **ENGINE PRIMITIVE DONE + WITNESSED (`W-BONSAI-PRELOAD`, `bonsai_kernel.js`):** `Bonsai.preload({assets:[{key,url,warm}]},
  onProgress)` streams each asset (per-asset `{asset,loaded,total,pct,done}` callback = the shower data) and WARMS the
  kernel worker from the fetched bytes (single download). Default asset = occt-wasm (warm:true).
- **WIRE (this session):** the red-pill handler calls `Bonsai.preload` with BOTH heavy assets and renders the shower:
  - `{ key:'occt-wasm', url:'viewer/lib/kernel/occt-wasm.wasm', warm:true }`  (22MB — warms the BIM worker)
  - `{ key:'idempiere-seed', url:'erp/ad_seed.db' }`  (**26MB basic iDempiere seed** — cache-warms the ERP face)
  ~48MB total, hidden behind the pulse. `pct` needs `Content-Length` (GH Pages sends it; the local test server doesn't).
- The shower lists named assets with progress bars (occt-wasm, iDempiere seed, …) — the "status shower of what code's loaded".

## ⚠ ICON DEBT — REDO (user flagged 2026-06-18, per `[[feedback_pill_icon_consistency]]`)
OUR surface = clean **Lucide line icons only** (`icons.js` / pill-registry) — NO unicode/emoji glyphs.
- **Design a proper Bonsai-tree LINE icon** for the `⋯`-rail launcher (the entry above). Lucide-style, single
  stroke, matches the rest of the rail. This is "the icon you said before" that must be redone — do NOT ship the
  unicode placeholder.
- **Replace the modeller's placeholder glyph buttons** — `▦ Grid`, `✎ Sketch`, `✂ Cut`, `⤓ IFC`, `▼/▸` Outliner
  disclosure — with Lucide line icons. They were scaffolding; the icon-consistency rule applies to the shipped surface.
- Reuse the existing pill-registry + settings-editor patterns; follow common HMI, don't overthink.

## DEPLOY — RESOLVED: GitHub Pages (user 2026-06-18). Branch is deploy-ready.
- **Hosting = GH Pages, same-origin.** The 22MB occt `.wasm` IS NOW COMMITTED (gitignore removed, commit
  `409f0a6`). `deploy-pages.yml` triggers on push to `main` and uploads the WHOLE repo (`path: '.'`) → the
  modeller serves at `red1oon.github.io/viewer/modeller.html` and the wasm at `…/viewer/lib/kernel/occt-wasm.wasm`.
  No CORS / no OCI / no COOP-COEP (occt single-thread); GH serves `.wasm` as `application/wasm`. 22MB << GH's
  100MB file limit. `OcctKernel.init()` auto-locates the co-located wasm — current code works unchanged.
- **GO-LIVE = merge `feat/bonsai-kernel-viewer` → `main`** (squash-merge carries only the final tree). Pages
  workflow auto-deploys. This is the OUTWARD-FACING publish — get explicit user go first.
- **CAVEAT to smoke-test:** `deploy-pages.yml` runs `scripts/minify_pages.js .` (whitespace-only, no identifier
  mangling — cross-file `window.*` globals safe). Verify the module worker `bonsai_kernel_worker.js` + `lib/kernel/`
  occt dist survive minify (vendored libs likely skipped). VERIFY LIVE: fetch the deployed `modeller.html`, author
  a wall, confirm `§BONSAI author … inScene=true`. Don't trust the workflow exit alone (Log Mandate).
- Later optimisation (NOT a blocker): slim custom occt build (~22MB → ~5MB brotli) to cut the served size.

## DEFERRED — the deliberate SHARED-MODULE chrome program (LATER, after deploy + real usage)
User weighed cost + agreed: do NOT fork the viewer's Find/PillBuilder/grid (= double-maintenance + drift).
When done, do it as ONE shared module used by both viewer and modeller:
- Full Bonsai chrome: PillBuilder `⋯` rail on the modeller (history/IFC/grid/signed pills).
- **Retrain the powerful cross-Find categories** (Room, Phase, …) onto the modeller Outliner via the
  `Bonsai.outliner.addCategory(...)` seam (already built for this) → and bridge to **ERP Project → Order create**
  (one signed op-log already spans both — the natural join). Source the real Revit+/Find lens, don't reinvent.
- This may eventually DEPRECATE the present viewer (the modeller cannibalises grid/Find/pills) — user's call,
  decide on real-usage evidence, "see later". `viewer.html` stays untouched until then.

## WITNESS DISCIPLINE (every leg)
Witness CLAIM before code; §-tagged `console.log`; puppeteer harness pattern in `viewer/tests/bonsai_*_live.js`
(swiftshader flags + correct `.wasm`/`.js` MIME). Read the `.log`. Push before session end (0 local-only).
```
```
