# ⚠ DO NOT REMOVE — Scope guard / RESUME CARD: UI PAYLOAD performance (JS weight on mobile)
# Scope: the LOAD-WEIGHT axis of UI performance — how many KB of JS the browser fetches/parses, esp. on
#   mobile cellular + first PWA install. DISTINCT from `prompts/IDLE_RENDER_GATE.md` (render-loop FPS) and the
#   S276b/S280 WebGPU/import cards. This card is about bytes-over-the-wire + precache size, NOT frame rate.
# STATUS: BOTH WINS SHIPPED (2026-06-11). Win #1 minify LIVE (#249, GH_DEPLOY esbuild pass: gz 1171→742KB
#   −36.6%, readable source untouched, scripts/minify_pages.js). Win #2 PRECACHE-TRIM LIVE (viewer sw v640:
#   ~8.9MB IFC/Excel off the auto-install path → first-visitor ~10MB→~1.5MB; offline preserved via cache-on-use
#   + the existing download-for-offline button). FOLLOW-UP: surface that button → prompts/OFFLINE_BUTTON_SURFACE.md.
# NON-NEGOTIABLE: zero behavior change (minify/precache-trim only — no logic edits); §-log/measure first;
#   test before deploy; reuse the SW precache machinery (don't fork sw.js). ERP source-of-truth = build/erp/.
# Read first: feedback_sw_version (CACHE_VERSION bump), feedback_oci_deploy (MIME), reference_bloat_reduction.

---

## MEASURED BASELINE (2026-06-11, ~/bim-ootb shipping code — verified, not estimated)
- **Our viewer JS** (~40 files, excl. lib/): raw **2.95 MB → ~780 KB gzip**.  **Our ERP JS**: 1.25 MB → ~345 KB gzip.
- **Third-party libs**: 9.7 MB raw — web-ifc **5.9 MB**, xlsx+exceljs **1.85 MB**, three.js **1.35 MB (~150 KB gz)**, chart.js 0.2 MB.
- **What loads on the critical path is SMALL** (the good news, already industry-correct):
  - web-ifc (5.9 MB) → Web Worker, only on IFC drag-drop (`viewer/import_worker.js` importScripts). NOT on view.
  - xlsx/exceljs (1.85 MB) → only `viewer/boq_charts.html` (separate page) + on export.
  - three.js → dynamic `import()` when the scene starts (core; ~150 KB gz, unavoidable for WebGL).
  - ⇒ initial interactive ≈ three + our viewer code ≈ **<~1 MB gz**. Heavy-ish but normal for a 3D BIM app.
- **The one real mobile cost**: `viewer/sw.js` PRECACHE precaches EVERYTHING (~10 MB incl. web-ifc + Excel) on
  PWA install — one-time background download, non-blocking, but hits cellular data.

## INDUSTRY SCORECARD (what we do / don't)
- ✅ Lazy-load the giants off the critical path (IFC worker, Excel on its own page) — the move that matters.
- ✅ gzip/brotli transport (GH Pages automatic). ✅ libs ship minified.
- ❌ Our OWN source is NOT minified/bundled/tree-shaken (no webpack/vite/rollup — deliberate no-build, readable-source ethos).
- ⚠ Precache-everything-for-offline (~10 MB) instead of shell-only + lazy-cache-on-first-use.

## THE TWO WINS (do top-down; each shippable alone, zero behavior change)
1. **Minify our own JS** (HIGH value, LOW risk). ~780 KB gz viewer payload → ~**500–550 KB gz** (text-minify saves
   ~25–30% beyond gzip). KEEP the no-build ethos: a minify STEP that emits `*.min.js` deployed alongside readable
   source (source stays the edited truth; deploy serves minified), OR a one-shot deploy-time minify pass. Decide which
   (the readable-source rule must survive — don't replace the sources). Witness: `§PAYLOAD-MIN before=KB after=KB`
   per file + a behavior smoke (the existing whitebox/Playwright suite PASSES unchanged on the minified bundle).
2. **Trim the SW precache** (MED value). Precache only the shell + three.js; let web-ifc/xlsx/exceljs cache on first
   use (the fetch handler already cache-firsts them once fetched). First PWA install ~10 MB → **~1–2 MB**. Witness:
   `§PRECACHE-TRIM assets=N bytes=… (web-ifc/excel deferred)` + offline still works after first IFC import / export
   (don't break `S284c_OFFLINE_DEEP_TEST` — re-run it).

## METHOD / STOP CONDITION
Measure-first (the baseline above is the oracle — re-measure after each change, diff KB). One win at a time, behavior
suite green before deploy, CACHE_VERSION bump on the sw.js touch. DONE when (1) the deployed viewer initial JS is
measurably smaller with the full whitebox/Playwright suite unchanged, and/or (2) first PWA install precache is the
shell+three only with offline still proven. If minify breaks a source-readability rule the user holds → propose the
`*.min.js`-alongside approach and confirm before adopting (this is the one real decision). ⛔ BLOCKED: only if the
user must choose the build-step shape.
