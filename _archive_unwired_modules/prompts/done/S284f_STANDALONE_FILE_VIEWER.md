# ⚠ DO NOT REMOVE — Scope: S284f — make the single-file `file://` standalone open the 3D VIEWER
# Read the output log after EVERY run. §-tagged runtime logs are the only proof. No guessing.
# SAFETY FENCE (do not cross): touch ONLY packageLandingPage() output, the `location.protocol==="file:"`
# branch in the injected `_openViewerBlob`, and a GATED window.name fallback in config.js. NEVER touch
# the online/live runtime: import_worker.js import logic, openViewer()/openProject() native window.open,
# or the live viewer boot. Prove they are byte-for-byte unchanged (git diff vs main = empty for runtime).

## Goal
The downloaded `BIM-OOTB.html`, opened from `file://`, must: (1) drop an IFC → render the 3D viewer
in-place; (2) open a previously-saved project → render. In BOTH Chrome and Firefox, network blocked.
Online (building list) and PWA opening must remain exactly as they are today.

## Already shipped (do NOT redo) — S284e, PR #58 on red1oon/bim-ootb
- ✅ `file://` IFC IMPORT works in both browsers. Null-origin worker wasm via `data:` URL
  (import_worker.js locateFile: `if (self.location starts "blob:null") return "data:application/wasm;base64,"+B64`).
- This prompt is ONLY about the VIEWER hand-off, which is still routed to the PWA.

## Proven dead-ends (do NOT repeat blindly — see docs `prompts/S284b_WEBIFC_EMBED.md` §S284e)
Tested in both browsers from `file://`, with iframe state/§-logs as proof:
1. `window.open(blob:null)` — BLOCKED by file:// (Chrome "Not allowed to load local resource",
   FF "from script denied"). blob: is the only origin we can mint, and file:// forbids navigating to it.
2. top-frame `document.open();document.write(viewerHtml);document.close()` — classic scripts run
   (`§STANDALONE_INPLACE` fires) but the ESM Three.js chain never executes → no canvas. Also wiping
   `document.body` crashes the PARENT landing (`renderTabs` → "Cannot read properties of null (reading 'style')").
3. `iframe.srcdoc = viewerHtml` — the 4.3MB doc exceeds practical srcdoc-attribute parsing → near-empty
   document (`bodyKids:0, scripts:1`). Do NOT wipe body to mount.
4. `iframe.contentDocument.write(viewerHtml)` (overlay, no body wipe — this part is GOOD, no parent crash) —
   the 4.3MB IS written (`document.documentElement.outerHTML.length≈4458679`) BUT the parser collapses the
   ENTIRE doc into the FIRST `<script>` element (`headKids:1, firstScriptLen≈4458612, bodyKids:0`). The first
   injected `<script>window._STANDALONE=true; _SQL_WASM_B64="…"; … _openViewerBlob=…` never sees its closing
   `</script>`, so everything after becomes its text content.

## Step 1 (CHEAP — try first): is dead-end #4 just a `</script>` boundary bug?
The collapse in #4 means the first injected standalone `<script>` block's closing `</script>` is not being
recognized when re-parsed in-place — even though the SAME `vHtml` parses correctly via Blob navigation.
- The injected standaloneBlock (index.html ~1925-1975) contains `_openViewerBlob`'s own source, which includes
  the LITERAL string `"</script>"` (and `el.textContent.split("</script>")`, a NO-OP today). A literal
  `</script>` inside an inline `<script>` terminates the script early in the HTML tokenizer — but here the
  symptom is the OPPOSITE (never terminates). Investigate the EXACT byte where the first `<script>` should
  close in `vHtml` vs what the in-place parser sees. Hypotheses to confirm/refute with logs:
  (a) the standaloneBlock is injected into the VIEWER html with a different/escaped `</script>` than the Blob
      path tolerates; (b) `_d.write()` of 4.3MB in one call streams differently than navigation; (c) the
      `split("</script>").join("</"+"script>")` no-op should actually be splitting on an ESCAPED closer.
- If it IS a boundary/escaping bug, fixing the assembly so the first script closes correctly may make
  `iframe.contentDocument.write` render — turning S284f into a SMALL fix. Try this before the refactor.
- Reuse the diagnostics from this session: `/tmp/diag_viewer.js` (packages + frames/errors/canvas),
  `/tmp/diag3.js` (iframe THREE/script state), `/tmp/diag4.js` (where the 4.3MB sits). Branch `s284e-b-inplace-viewer`
  has the contentDocument.write WIP (unmerged dead-end) to build on.

## Step 1.5 (ALTERNATIVE worth a cheap experiment): ship a zip/folder, not a single file
Package as a folder (index.html + viewer.html + small shared assets) the user unzips. Then the file://
landing opens the viewer via a REAL navigation: `window.open('viewer/viewer.html?db=import://<key>')` or
`location.href=...` — a real file:// URL, NOT blob:null. This DIRECTLY removes the #1 blocker (no blob,
no document.write/srcdoc parsing of 4.3MB). BUT two file:// hazards return — verify BOTH before betting:
- **GO/NO-GO 1 — cross-file IndexedDB sharing on file://:** does `viewer.html` read the IndexedDB that
  `index.html` wrote during import? Modern Chrome "strict file origin" often ISOLATES each file:// doc →
  viewer sees no DB. TEST: import in index.html (file://), navigate to viewer.html (file://), assert the
  import:// DB loads. If isolated, this path is dead (or needs a non-IndexedDB transfer, which is hard for 200MB).
- **GO/NO-GO 2 — file:// module/fetch:** Chrome blocks ESM `import`/`fetch()` of sibling files from file://.
  viewer.html MUST stay self-contained (inline THREE+wasm as data:, no `fetch('lib/…')`). Confirm zero
  file:// resource loads (only data:/IndexedDB).
- COST: loses the single-file "double-click" delight (unzip step, multi-file fragility, AV nags). If both
  GO/NO-GO checks pass in BOTH browsers, this is simpler than Step 2; if either fails, prefer Step 2 or PWA.

## Step 2 (if Step 1 proves fundamental): merged single-document refactor
Build the standalone so the viewer is REAL DOM from page load — no runtime HTML injection at all:
- packageLandingPage() assembles ONE document: landing UI + viewer container (the viewer's body/scripts),
  viewer hidden behind landing. ESM/THREE (already inlined as data: URI import) boots normally at load.
- On open (file:// branch only): hide landing UI, reveal viewer container, call viewer init with the project key.
- HARD PART (this is the real risk, all INTERNAL to the standalone file — cannot reach live site):
  landing and viewer both define globals (`APP`/`A`), element IDs, CSS. Must namespace/scope to avoid
  collisions and double-init. This is why it is multi-step, not a patch.

## DON'T
- DON'T modify import_worker.js import logic, the online window.open path, or the live viewer. Prove unchanged.
- DON'T bump CACHE_VERSION or touch sw.js (S285 concurrency).
- DON'T claim success on `§IMPORT_SAVED` — that fires BEFORE the hand-off. Assert the VIEWER RENDERS.

## Witness claims (each NAMES the issue it proves)
| ID | Claim | Proof |
|----|-------|-------|
| W-284f-1 | file:// drop→view renders, Chrome | canvas>0 px + §S277b_RENDERER/§DLOD_FLUSH in viewer frame, no blob:null/parse error |
| W-284f-2 | file:// drop→view renders, Firefox | same, FF |
| W-284f-3 | file:// open previously-saved project renders, both | §STANDALONE_INPLACE + canvas, no "from script denied" |
| W-284f-4 | Online IFC import UNCHANGED | golden-path §IMPORT_SAVED + element count, git diff import_worker.js runtime vs main = empty |
| W-284f-5 | Online/PWA building-list open UNCHANGED | openViewer/openProject use native window.open; index.html online path diff = empty |
| W-284f-6 | file:// IFC import still works (S284e not regressed) | §WASM_LOCATE data: + §IMPORT_SAVED, FF file:// |

## Step 1 RESULT (2026-05-30) — dead-end #4 IS a `</script>` escaping NO-OP. CHEAP FIX confirmed.
**Diagnosis (empirical, not theory):** Ran the EXACT runtime transform from `_openViewerBlob`
against the real packaged `/tmp/BIM-OOTB-ff.html` (`viewer-html-src` textContent, 4,458,960 bytes):
- Packager (index.html line 2069/2080) stores closers ESCAPED as literal `<\/script>` (with a
  backslash) — 82 occurrences in the viewer block, **0 real `</script>`** (so the text/plain block
  itself doesn't close early).
- `_openViewerBlob` line 1939 runtime: `el.textContent.split("<\/script>").join("</"+"script>")`.
  The JS value of `"<\/script>"` is `</script>` (the `\/` collapses to `/`). It splits on
  `</script>` but the stored closers are `<\/script>` (backslash) → **NO MATCH → NO-OP**
  (verified `vHtml === textContent`, still 82 escaped / 0 real closers).
- So `vHtml` is fed to BOTH the Blob path AND the iframe path with backslash closers and ZERO real
  `</script>`. The HTML tokenizer's first `<script>` (the sqlWrap head block) never sees a closer →
  swallows the ENTIRE 4.4MB as its text content (`firstScriptLen≈4458612, bodyKids:0`). Nothing runs.
  **This is dead-end #4's collapse, and the Blob path was never actually verified to RENDER either
  (S284c/e only asserted §IMPORT_SAVED — the documented trap).**

**Root cause:** one backslash level short in the SOURCE. To make the EMITTED standalone runtime
arg be `<\/script>` (backslash, matching stored closers), the index.html source string literal must
be `"<\\\\/script>"` (emits `"<\\/script>"`, value `<\/script>`), not `"<\\/script>"` (emits
`"<\/script>"`, value `</script>`). Verified end-to-end in node: with the fix, `X<\/script>Y` →
`X</script>Y` (real closer); current code is a no-op.

**Chosen path = CHEAP FIX (Step 1), NOT the Step 2 refactor.** Fix the split escaping so closers
un-escape to real `</script>`. Then the EXISTING `location.protocol==="file:"` branch
(iframe.contentDocument.write, no body-wipe — already WIP, safe for the parent) parses correctly:
classic scripts separate and run, and `loader.js`'s dynamic `import()` (rewritten to `data:` URIs,
base-URL-independent, importmap irrelevant) boots THREE → canvas. Keep the GATED `window.name`
fallback in config.js (WIP) for `?db=&lib=` since the iframe has no URL query.

**Scope of the fix (inside safety fence):** ONLY `packageLandingPage()` output (the injected
`_openViewerBlob` split line) + the already-present file:// branch + config.js window.name fallback.
Live runtime untouched (`_openViewerBlob` is injected only into the downloaded standalone; live
index.html never defines it; live viewer uses native window.open). Prove diff vs main = empty for
import_worker.js runtime and the online window.open path.

**Proof obligations:** rebuild standalone, assert in BOTH browsers (network blocked, file://):
canvas px > 0 + `§S277b_RENDERER` + `§DLOD_FLUSH` in the viewer iframe; `§STANDALONE_INPLACE`
(window.name params) fires; no `blob:null`/"from script denied"/parse-collapse. Plus W-284f-4/5/6.

# DONE (2026-05-30) — CHEAP FIX SHIPPED-READY, both browsers RENDER from file://, network blocked
**Result:** `node tests/test_s284f_standalone_viewer.js` → **chromium PASS, firefox PASS** (exit 0).
Drop IFC → import → auto-open(=openProject) → viewer renders IN-PLACE in a same-origin iframe.
Proof per browser (`/tmp/s284f_run7.log`):
- `§S284e_B VIEWER_INPLACE iframe.write` (file:// branch taken, NOT blob:null)
- `§STANDALONE_INPLACE params from window.name=?db=import://…` (W-284f-3: config.js fallback)
- `§S277b_RENDERER WebGLRenderer r184` (renderer created — W-284f-1/2)
- `§DB_LOADED size=1MB` (DB read from the SHARED file:// IndexedDB the import wrote — §CACHE_HIT)
- `§CONTRACT_CHECK … streamed=130 orphans=0` (all 130 elements reached the scene)
- `#canvas = 921600 px` (1280×720 render surface), `blocked=false` (no blob:null / "from script denied")
- NOTE: §DLOD_FLUSH does NOT fire for this fixture — `§DLOD_SKIP count=130 < 5000`. DLOD flush is a
  >5000-element path; small-model render proof = §S277b_RENDERER + §CONTRACT_CHECK streamed>0. The
  witness table's "§S277b_RENDERER/§DLOD_FLUSH" is an OR; §S277b_RENDERER + streamed is the proof here.

**What it took (THREE nested file:// blockers, each found by §-log, fixed in packageLandingPage output):**
1. **`</script>` escaping NO-OP** (the dead-end #4 collapse). Source emitted `split("<\/script>")`
   (value `</script>`) but stored closers are `<\/script>` (backslash) → no-op → first script ate the
   4.4MB doc. Fix: source `split("<\\\\/script>")` → emits `split("<\\/script>")` → value `<\/script>`.
2. **THREE r184 split build** — `three.webgpu/module.min.js` import RELATIVE `./three.core.min.js` and
   OrbitControls imports BARE `'three'`; neither resolves from a `data:`/`blob:` base. Fix: inline
   `three.core` as a data: URI, rewrite the relative ref inside BOTH builds, rewrite OC's `'three'`.
   scene.js needs the STANDARD build for `THREE.WebGLRenderer` (WebGPU build has none) → separate
   `threeStdUri`, and scene.js's `import('./lib/three.module.min.js')` rewritten too.
3. **sql.js dead-on-file://** — `loadLibAt` fetched `lib/sql-wasm.js` (404 on file://) then CDN
   (network-blocked) and threw OUT of loadAllLibs BEFORE initViewer (line ~226 not try/caught). Fix:
   rewrite inlined loader's `LIBS[0].url` → data: URI of the already-embedded sql-wasm.js source.
   (Optional Sky/EffectComposer/BVH/SheetJS imports all fail GRACEFULLY — direct render, no DLOD BVH.)

**Files changed (S284f — all within the safety fence):**
- `index.html` — 3 hunks, ALL inside `packageLandingPage()` (1822–2133): the injected `_openViewerBlob`
  split fix + the file:// iframe branch (pre-existing WIP, kept) + THREE/core/sql data:-URI inlining.
- `viewer/config.js` — GATED `window.name` fallback (pre-existing WIP). Online/PWA ALWAYS has
  `location.search` → branch skipped → behavior unchanged.
- `tests/test_s284f_standalone_viewer.js` — NEW e2e (Chrome+FF, network-blocked, asserts RENDER not §IMPORT_SAVED).

**Safety-fence proof (no live/online regression):**
- W-284f-4 (online import unchanged): `test_s284b_full_package.js` → **33 PASS / 0 FAIL**; 9.3 "ALL
  executable <script> blocks parse as valid JS"; `import_worker.js` NOT touched by S284f
  (`git diff --stat HEAD` shows only index.html + config.js; its diff vs main is the prior shipped S284e fix).
- W-284f-5 (online/PWA open unchanged): all index.html hunks inside packageLandingPage(); live
  `window.open`/card-open/city-open (lines 420-505/848/896) untouched. The window.open→_openViewerBlob
  override exists ONLY in packaged standalone output.
- W-284f-6 (file:// import not regressed): `test_s284e_ff_file_import.js` → PASS (§WASM_LOCATE data: + §IMPORT_SAVED).
- sw.js / CACHE_VERSION NOT touched (S285 concurrency respected).

**NOT committed.** Working tree also carries UNRELATED stray ERP edits (`viewer/ad_data.js`,
`ad_parser.js`, `ad_ui.js`, `erp.html`) that predate this session — do NOT bundle them. Commit ONLY
`index.html`, `viewer/config.js`, `tests/test_s284f_standalone_viewer.js`. Awaiting go before push
(push = GitHub-Pages deploy + CI auto-merge).

## First actions
1. Read `prompts/S284b_WEBIFC_EMBED.md` §S284e (full dead-end analysis) + this file.
2. Confirm green baseline: `node tests/test_s284e_ff_file_import.js` (FF file:// import passes).
3. Step 1 diagnosis: repackage from a fresh branch, run `/tmp/diag4.js`-style inspection, find the exact
   `</script>` boundary where the first script fails to close. Decide: cheap fix vs Step 2 refactor.
4. Spec the chosen path in this file BEFORE coding. Prove with the both-browser, network-blocked,
   canvas-render test matrix above. No deploy without that proof.
