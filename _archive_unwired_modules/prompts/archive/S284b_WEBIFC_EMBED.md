# ⚠ DO NOT REMOVE — Scope: S284b Embed web-ifc WASM for fully offline IFC import
# Read the log after every run.

## Background

S284b Phase 1 (done) embedded the web-ifc **JS** (6MB) as `<script type="text/plain" id="webifc-src">` in BIM-OOTB.html. The import worker reads it from the DOM element, creates a Blob Worker, and web-ifc parses IFC files. All proven by sandbox test.

### What's done
- `<script type="text/plain" id="webifc-src">` — 6MB web-ifc JS embedded as inert text
- `_createWorker()` reads DOM `.textContent`, replaces `importScripts()`, creates Blob Worker
- Function replacement in all `html.replace()` calls to avoid `$'` corruption
- `_STANDALONE` redirect guard removed — IFC import runs locally via Blob Workers
- sql-wasm.js + sql-wasm.wasm (base64) embedded for offline SQLite
- `_WORKER_SOURCES` — import/mesh/export workers as JSON strings
- 165 tests across 6 suites, including Playwright browser from `file://` + sandbox e2e IFC drop
- `handleImportFile()` no longer redirects to online — Blob Workers handle import offline

### What's still fetching from CDN
The `ifcApi.Init(locateFile)` callback in `import_worker.js` line 91-95 returns:
```
https://unpkg.com/web-ifc@0.0.77/web-ifc.wasm
```
This ~4MB WASM binary is fetched at runtime when the worker initializes web-ifc. Without it, IFC parsing fails.

### Root cause
Only the JS source was embedded. The WASM binary needs the same treatment as sql-wasm.wasm: base64-encode and embed, then decode in the worker before `ifcApi.Init()`.

## Goal

Embed `web-ifc.wasm` (~4MB) in BIM-OOTB.html so IFC import works **fully offline** from `file://` with zero CDN dependency.

## DO

- **Fetch `web-ifc.wasm` in `packageLandingPage()` as base64** — same pattern as sql-wasm.wasm:
  ```js
  var webIfcWasmBuf = await fetch('https://unpkg.com/web-ifc@0.0.77/web-ifc.wasm').then(r => r.arrayBuffer());
  var webIfcWasmB64 = btoa(String.fromCharCode(...new Uint8Array(webIfcWasmBuf)));
  ```
  Embed as `window._WEBIFC_WASM_B64 = "..."` in the standalone config block.

- **Override `locateFile` in the worker** — when `_STANDALONE`, the worker should decode the base64 WASM and pass it to `ifcApi.Init()` as a `WebAssembly.Module` or override the fetch. See web-ifc docs for `Init({ wasmModule })` or `Init({ locateFile })` returning a data URI or Blob URL.

- **Option A: Inject base64 into worker source** — `_createWorker()` already prepends web-ifc JS. Also prepend `self._WEBIFC_WASM_B64 = "..."` so the worker has the WASM binary available. Then modify the `locateFile` callback to return a Blob URL from the decoded base64:
  ```js
  await ifcApi.Init(function(path) {
    if (self._WEBIFC_WASM_B64) {
      var binary = atob(self._WEBIFC_WASM_B64);
      var bytes = new Uint8Array(binary.length);
      for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
      return URL.createObjectURL(new Blob([bytes], {type: 'application/wasm'}));
    }
    return 'https://unpkg.com/web-ifc@0.0.77/' + path;
  }, true);
  ```

- **Option B: Pass WASM binary directly** — if web-ifc supports `{ wasmBinary }` like sql.js does, pass the decoded ArrayBuffer directly instead of a URL.

- **Check web-ifc API for WASM init options** — read `web-ifc-api-iife.js` to find what `Init()` actually accepts. It may use Emscripten's `locateFile` or a custom `wasmBinary` path.

- **Cache locally** — consider caching the fetched WASM in IndexedDB on first package, so repeat packaging doesn't re-download 4MB.

- **Run sandbox test** — `test_s284b_sandbox.js` must pass with the CDN blocked (use Playwright route interception to block `unpkg.com`). This proves true offline.

- **Test the actual output HTML in browser** — the WASM decode path must be proven by Playwright, not just Node.js.

## DON'T

- **DON'T commit web-ifc.wasm (4MB) to the repo** — fetch from CDN at package time.
- **DON'T break online mode** — all changes gated by `_STANDALONE`. Online import uses CDN as before.
- **DON'T inject the 4MB base64 into every worker** — only import_worker.js needs it (ifc_export_worker.js doesn't call `ifcApi.Init()`).
- **DON'T use `<script type="text/plain">` for the WASM base64** — it's a clean ASCII string, safe as a JS variable in the config block (same as `_SQL_WASM_B64`).

## Size budget

| Component | Size | Encoding |
|-----------|------|----------|
| index.html source | ~100KB | raw |
| import_db_builder.js + locale_loader.js | ~25KB | inlined `<script>` |
| import_worker.js + mesh + export | ~55KB | JSON in `_WORKER_SOURCES` |
| sql-wasm.js | ~50KB | JSON in `_SQL_WASM_JS` |
| sql-wasm.wasm (base64) | ~841KB | string in `_SQL_WASM_B64` |
| web-ifc-api-iife.js | ~6MB | `<script type="text/plain">` |
| **web-ifc.wasm (base64)** | **~5.3MB** | **string in `_WEBIFC_WASM_B64`** |
| Sysnova.png (base64) | ~5KB | data URI |
| manifest.json snapshot | ~10KB | JSON in `_MANIFEST_SNAPSHOT` |
| **Total** | **~12.4MB** | gzip ~4MB transfer |

## Files to modify

| File | Change |
|------|--------|
| `index.html` | packageLandingPage(): fetch + base64-encode web-ifc.wasm, add `_WEBIFC_WASM_B64` to standaloneBlock |
| `index.html` | _createWorker(): prepend `self._WEBIFC_WASM_B64` to import worker source |
| `viewer/import_worker.js` | `ifcApi.Init()`: check `self._WEBIFC_WASM_B64`, decode to Blob URL or ArrayBuffer |
| `tests/test_s284b_sandbox.js` | Block CDN with Playwright route, prove true offline IFC parse |
| `tests/test_s284b_full_package.js` | Verify `_WEBIFC_WASM_B64` in output, size ~5.3MB |

## Test

1. `§PACK_DOWNLOAD size=N` — expect ~12.4MB
2. Open from `file://` **with network blocked** — zero console errors
3. Drop IFC — import completes, `§PARSE_OK`, `§IMPORT_SAVED`
4. No CDN requests at all (`§WASM_LOCATE` should show Blob URL, not unpkg.com)
5. `test_s284b_sandbox.js` — 18+ tests, 0 failures, CDN blocked
6. `test_s284b_full_package.js` — 30+ tests, `_WEBIFC_WASM_B64` present
7. Online landing page — unchanged behavior (no `_STANDALONE`, CDN loads)

## Witness claims

| ID | Claim | Proof |
|----|-------|-------|
| W-284b-6 | Saved HTML opens from file:// with zero CDN dependency | Playwright route blocks unpkg.com, still works |
| W-284b-7 | web-ifc.wasm loaded from embedded base64 | §WASM_LOCATE shows Blob URL not CDN |
| W-284b-8 | IFC drop works fully offline (zero network) | §IMPORT_SAVED + CDN blocked in test |
| W-284b-9 | Output size within 13MB budget | §PACK_DOWNLOAD size=N |
| W-284b-10 | Online landing page unchanged | No _STANDALONE flag, CDN loads normally |

---

## S284d — PWA offline wasm robustness (spec, 2026-05-29)

### Issue this proves/disproves
**Symptom (user, Firefox, live v538):** offline IFC import aborts with
`wasm streaming compile failed: NetworkError` → `both async and sync fetching of
the wasm failed` → `§IMPORT_FATAL`, even though `§WORKER_SRC local` and
`§WASM_INIT starting` succeed.

**Root cause (proven by repro):** `import_worker.js` `locateFile` returns the
RELATIVE path `lib/web-ifc.wasm` and lets emscripten fetch it itself
(`web-ifc-api-iife.js:4650-4661`). That fetch is served only if the 1.3MB wasm is
already in a cache layer. `web-ifc.wasm` was added to the SW `LOCAL_LIBS` precache
only on 2026-05-29 (commit `ca62a51`, an S285 commit), so users whose SW had not
yet background-fetched the wasm — or who went offline before it finished — have it
in NO cache, and the import aborts cryptically. Proof: live offline import PASSES
in Chromium AND Firefox **when the wasm is cached** (§IMPORT_SAVED elements=130);
the user's exact error reproduces **only** when the wasm is absent from every cache
layer offline. The 6MB JS loads fine (importScripts, long-precached); the separate
wasm fetch is the single fragile dependency.

### Fix (both robustness + honest gate) — index.html + import_worker.js ONLY (no sw.js)
- **`_getWebIfcWasm()` (index.html):** return the wasm bytes via the SAME
  offline-safe path `_loadSqlJs` already uses — `caches.match('viewer/lib/web-ifc.wasm')`
  first (works offline; main thread CAN read Cache Storage even though the root page
  is uncontrolled), then `fetch`. Standalone → decode `_WEBIFC_WASM_B64`. On success,
  store bytes in a page-owned cache (`bim-ifc-engine`) so they survive SW
  purge/version skew. On total failure → throw a CLEAR error, never the cryptic abort.
- **Transfer bytes to worker:** IFC import call sites post `{ arrayBuffer, filename,
  wasmBytes }` (wasmBytes transferable). Worker stashes `self._WEBIFC_WASM_BYTES`.
- **`locateFile` (import_worker.js):** if `_WEBIFC_WASM_BYTES` → mint Blob URL from
  bytes IN-worker (same mechanism proven for `_WEBIFC_WASM_B64`); keep `_WEBIFC_WASM_B64`
  and `lib/` branches as fallbacks. Emscripten never does its own fetch on the happy path.
- **Warm on first online load:** `_warmIfcEngine()` prefetches the wasm into the
  page cache when online so a later offline session is ready.

### Witness claims
| ID | Claim | Proof |
|----|-------|-------|
| W-284d-1 | Worker loads wasm from main-thread bytes, no emscripten fetch | §WASM_LOCATE shows "blob (from main-thread bytes)" |
| W-284d-2 | Offline import works when wasm was cached once (Cache Storage path) | §IMPORT_SAVED offline, wasm read via §IFC_WASM_FROM_CACHE |
| W-284d-3 | Offline + wasm NEVER downloaded → clear error, not cryptic abort | §IFC_ENGINE_UNAVAILABLE + friendly status, no "both async and sync" |
| W-284d-4 | Online import unchanged (golden path) | §IMPORT_SAVED online, element count matches |
| W-284d-5 | Standalone file:// still works via embedded base64 | §WASM_LOCATE blob (decoded in-worker) + §IMPORT_SAVED |

---

## S284e — standalone `file://` hardening: null-origin wasm + viewer hand-off (spec, 2026-05-30)

**Context:** S284d fixed PWA/https offline. Testing the *actual downloaded* `BIM-OOTB.html`
opened from `file://` exposed TWO further bugs that S284d's chromium-only test missed
(it asserted `§IMPORT_SAVED`, which fires BEFORE the viewer hand-off). Both stem from one
hard browser rule: **a `file://` document is a unique opaque origin, and `blob:null`
resources cannot be fetched or opened from it.**

### Bug 1 — wasm blob:null fetch fails in Firefox `file://` worker  ✅ FIXED + PROVEN
- In standalone, the import worker is a **blob worker** → null origin (`blob:null/<uuid>`).
- S284d's `locateFile` returned a `blob:` URL; emscripten `fetch()`-es it. Chrome allows
  blob fetch inside a worker; **Firefox refuses blob fetch in a null-origin worker** →
  `wasm streaming compile failed: NetworkError` → `both async and sync … failed` → abort.
  (The OLD `_WEBIFC_WASM_B64` branch had the same latent bug — never caught: sandbox test
  is chromium-only.)
- **Fix (done, in `import_worker.js`):** when the worker origin is null
  (`self.location` starts `blob:null`), return a **`data:application/wasm;base64,…` URL**
  instead of a blob URL. data: is self-contained and fetchable in any origin. PWA/https keeps
  the blob path. **Proven:** FF `file://`, network blocked → `§WASM_LOCATE → data: (null-origin
  worker)` → `§WASM_INIT done` → `§PARSE_OK` → `§IMPORT_SAVED elements=130`.

### Bug 2 — viewer hand-off via `blob:null` window.open is blocked in BOTH browsers  ❌ OPEN
- After import (and on opening any previously-saved project), `_openViewerBlob` (index.html
  ~1936) builds a Blob of the embedded `viewer-html-src` and does `window.open('blob:null/…?db=
  import://…')`. From `file://`: Chrome → *"Not allowed to load local resource"*; FF →
  *"Access to 'blob:null/…' from script denied"*. The viewer never opens. This also breaks
  `openProject` (open a previously-OK project) since it routes through the same `_openViewerBlob`.
- NOTE: the `[WEB-IFC][error][TriangulateBounds()] No basis found for brep!` wall is HARMLESS
  web-ifc triangulation noise (degenerate BReps), not related — import completes fine
  (`§IMPORT_SAVED elements=40086`, `§DB_EXPORT_DONE size=218.3MB`).

### Bug 2 status — DEFERRED to a dedicated spike; viewer routed to PWA
Two in-place mechanisms were tried (2026-05-30) and BOTH failed, proven by e2e in both browsers
from `file://` (`tests/test_s284e_standalone_viewer.js`, `diag2.js`):
- **`document.write(viewerHtml)`** — classic scripts run (`§STANDALONE_INPLACE` fires) but the
  **ESM Three.js chain never executes** → no canvas. (`document.write` does not run module scripts.)
- **`srcdoc` iframe** — `iframe.name` carries `?db=` correctly, but the multi-MB viewer assigned
  via `srcdoc` produced a **near-empty document** (`hasTHREE:undefined, canvas:false, bodyLen:0,
  scripts:1`) → no render. Also, wiping `document.body` to mount it crashed the PARENT landing
  (`renderTabs` → `Cannot read properties of null (reading 'style')`).
- `blob:` navigation is the only thing that carries the 7MB doc reliably, but `blob:null` is
  exactly what `file://` forbids opening. So all three in-place options are blocked.

**Decision:** revert the in-place attempt (it regressed standalone: parent crash + blank screen).
The standalone single-file is for **import + extract/share the DB**; the **3D viewer hand-off is
routed to the installed PWA** (real origin, no `blob:null`/`srcdoc`/ESM-from-`file://` traps,
handles 200MB models). A true single-file `file://` 3D viewer is a SEPARATE scoped spike
(likely `iframe.contentDocument.write` of the FULL doc + ESM-on-`file://` resolution), not a patch.

### Shipped (S284e, branch off main)
- ✅ Bug 1 wasm `data:` fix in `import_worker.js` (8 lines). FF `file://` IMPORT works.
- ✅ index.html + config.js reverted to main exactly → **live/PWA opening routine byte-for-byte
  unchanged** (`_openViewerBlob` is standalone-only; live listings use native `window.open`).

### Witness claims
| ID | Claim | Proof |
|----|-------|-------|
| W-284e-1 | FF `file://` imports IFC (null-origin wasm via data:) | §WASM_LOCATE data: + §PARSE_OK + §IMPORT_SAVED |
| W-284e-2 | Live/PWA open routine unchanged by S284e | index.html+config.js diff vs main = empty; openViewer/openProject use native window.open |
| W-284e-3 | In-place viewer from file:// is NOT shippable (deferred) | document.write=no-ESM; srcdoc=empty-doc; both proven failing |
| W-284e-4 (future B) | Single-file file:// 3D viewer renders both browsers | dedicated spike: canvas + scene §log, drop→view & open-saved |
