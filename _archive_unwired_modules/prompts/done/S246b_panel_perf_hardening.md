# ⚠ DO NOT REMOVE — S246b Panel/Perf Hardening
# Scope: Mobile panel UX reliability, clash performance, SW caching, desktop share
# Read the log after every run.

## Context from previous session (2026-05-06)

### What was fixed and deployed to ootb-dev (SW v254):
- WASM URLs unified (was split between sql.js.org and jsdelivr) → all now `lib/` local
- Vendor libs (Three.js, OrbitControls, sql-wasm, SheetJS) localised to `deploy/dev/lib/` — single origin, CDN fallback via `loadLib()` in loader.js
- SW v254: cache key strips `?v=N` (was serving offline.html as JS → initViewer undefined). `.js` fallback returns 503 not offline.html
- R-tree + B-tree indexes built eagerly 1s after DB loads (was lazy on first clash open — caused matrix stall)
- `_countClashesRtree` rewritten as single SQL R-tree join (was N individual queries per discA element)
- Clash snag capture: direct `drawImage` from WebGL canvas with `preserveDrawingBuffer:true` (was toBlob→Image roundtrip ~500ms → now ~5ms)
- Long-press: `_longPressTimer` + `_longPressFired` cleaned on measure toggle off. Info card auto-dismisses instead of blocking `handleMeasureRightClick`
- Panel `pointerdown.stopPropagation` on all static panels (hud, search-box, storey, disc, info, issues, status)
- Swipe-hide now exits measure mode (toolbox hides with other panels consistently)
- Swipe-show clears `collapsed` class on panel bodies (prevents invisible-but-present state)
- Clash snag routing: Share/Save buttons check `_pendingClashSnag` → route to clash-specific save with both GUIDs, overlap, deep-link
- Issues list filtered to `APP.activeBuilding`
- 11 code quality fixes: setup*() guards, onerror on script tags, APP._SQL cache, IDB add() error handler, etc.
- ootb-live has fixes (locateFile, measure, panels, sitecam) but NOT local-first libs — stays CDN for A/B comparison

### Still open — troubleshoot these:

1. **Desktop clash sharing** — after snag capture, Share button shows "Deep-link copied to clipboard" but never offers a share medium (WhatsApp, etc.). The Web Share API (`navigator.share`) may not be available on desktop browsers, and the fallback goes straight to clipboard without showing any chooser. Need to check if desktop should offer a different UX (e.g. copy + WhatsApp link button).

2. **Storey column query error** — log shows:
   `§HELPERS_QUERY_ERR no such column: storey SELECT storey FROM element_transforms WHERE guid = ?`
   This is in `_snagClash` at `measure.js:793` which queries `element_transforms.storey` but that column is on `elements_meta`, not `element_transforms`. Fix: change to `SELECT storey FROM elements_meta WHERE guid = ?`.

3. **WASM preload warning** — browser says: "preloaded with link preload was not used within a few seconds". The preload in `index.html` points to `lib/sql-wasm.wasm` but loader.js fetches it separately via `_wasmBinaryPromise`. The browser can't match them because loader.js uses `fetch()` (not the preload consumer). Fix: either remove the `<link rel="preload">` (redundant now that loader.js pre-fetches) or make loader.js consume the preload via `fetch()` with `{cache: 'force-cache'}`.

4. **Panel touch-through on dynamically-created panels** — static panels have `stopPropagation` but clash matrix div, clash list div, and info card div (created in measure.js) don't. They have `pointer-events:auto` in CSS but don't stop event propagation. On mobile, taps on these panels may still reach the canvas.

5. **R-tree initial delay on very large buildings** — eager build starts 1s after DB load but 50k+ elements still takes a few seconds of batched INSERT. If user opens clash before R-tree is ready, falls back to cross-join which is slow. Consider: show a "Building spatial index..." status, or block matrix until ready.

6. **Long-press sometimes doesn't work after toggle cycles** — was partially fixed (timer/flag cleanup) but may still have edge cases with rapid toggle or when swipe-hide triggers toggleMeasure during an active long-press.

7. **Desktop toolbox disappears after clash flow** — the Tools panel (`#search-box`) vanishes after interacting with clash matrix/list then dismissing. Root cause: `clearMeasures()` could throw mid-cleanup (stale mesh refs in `_clashBackups`, removed DOM nodes in `measureLabels`), leaving the toolbox hidden. Fixed by wrapping cleanup in try/catch and always restoring `search-box.style.display = ''` at the end. Also `issues.js:248` sets `search-box display:none` when issues panel opens — if issues panel opens during clash and close path isn't hit, toolbox stays hidden. `clearMeasures` now force-restores it. **Deployed to ootb-dev — verify this is actually fixed.**

8. **Desktop measure dot placement inaccurate** — clicking to place a blue measurement dot sometimes lands off from where the pointer actually clicked. The raycaster in `_doMeasureClick` (measure.js) uses `e.clientX/clientY` mapped to NDC via `window.innerWidth/innerHeight`, but the canvas may not fill the full window (e.g. if DevTools is open, or browser chrome changes viewport). Also the 250ms debounce in `handleMeasureClick` copies `clientX/clientY` into a plain object — if the mouse moves during that 250ms, the stored coords are stale. Check if `canvas.getBoundingClientRect()` should be used instead of `window.innerWidth/Height`, and whether the debounce delay should be shorter or eliminated on desktop.

9. **Desktop clash sharing shows no share medium** — after snag capture, Share button goes straight to clipboard ("Deep-link copied") without offering WhatsApp or other share targets. `navigator.share` (Web Share API) is not available on most desktop browsers. The fallback in `_shareClashSnag` jumps to `navigator.clipboard.writeText`. Consider: on desktop, show a mini share panel with Copy + WhatsApp link + Email buttons instead of relying on Web Share API.

### Key files:
- `deploy/dev/measure.js` — clash matrix, R-tree, snag capture, long-press, info card
- `deploy/dev/panels.js` — swipe handler, panel visibility, touch-through prevention
- `deploy/dev/picking.js` — pointerdown/up/move, long-press timer, measure click routing
- `deploy/dev/sitecam.js` — share/download flow, markup listeners, preview lifecycle
- `deploy/dev/sw.js` — service worker v254, cache strategy
- `deploy/dev/loader.js` — local-first lib loading, WASM pre-fetch
- `deploy/dev/scene.js` — WebGL renderer (preserveDrawingBuffer)
- `deploy/dev/streaming.js` — DB init, A._SQL cache
- `deploy/dev/issues.js` — IndexedDB issue log, building filter

### Commits this session:
- `bf6d304b` — local-first vendor libs, WASM init fix, 11 code quality fixes
- `44af82af` — R-tree eager build, SW cache key fix, snag speed, long-press reliability
- `27a95394` — PROGRESS.md update

### Log evidence (from user's desktop Firefox, ootb-dev, Terminal building 122k elements):
- `§FLUSH instanced=49216 single=73114 drawCalls=86546` — streaming works
- `§CLASH_RTREE ready` confirmed (rtree=true in matrix log)
- `§CLASH_QUERY_RTREE STR vs ACMV ... hits=200 sql=4783 time=111ms` — R-tree query works but 4783 SQL calls (per-element loop in _queryClashesPairRtree still needs optimisation for the LIST path, not just COUNT)
- `§CLASH_SNAG ... snap=6ms` — direct drawImage confirmed fast
- `§HELPERS_QUERY_ERR no such column: storey` — bug in _snagClash storey query
- `§CLASH_SNAG_CLIPBOARD OK` — share fell through to clipboard (no Web Share API on desktop)
- WASM preload warning — `<link rel="preload">` not consumed
