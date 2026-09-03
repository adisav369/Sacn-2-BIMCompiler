# S284 — Single HTML Offline Installer

# !! DO NOT REMOVE — read the log after every run !!
# Scope: Self-packaging viewer as one downloadable HTML file, About box install, built-in updater
# Spec-first: this IS the spec. Implement section by section.

## Thesis

The viewer packages itself into a single `.html` file at runtime. The user
clicks "Install" in the About box on the landing page. The browser downloads
`BIM-OOTB.html` (~5MB). User double-clicks it. The full BIM viewer opens —
drop an IFC file, get a 3D model. No server, no terminal, no zip, no PWA,
no Chrome vs Firefox. Every OS, every browser.

The installed HTML includes a built-in updater that checks GitHub Pages for
a newer version and replaces itself.

## User Story

```
User visits landing page (GitHub Pages)
  → clicks About → clicks "Install"
  → browser downloads BIM-OOTB.html (~5MB) to Downloads
  → user double-clicks BIM-OOTB.html
  → viewer opens in browser (file:// or localhost)
  → user drops IFC file → full 3D viewer
  → user clicks "Check Update" → compares version → downloads new HTML if available
```

## What Already Exists

| Component | File | Status |
|---|---|---|
| Viewer (80+ JS files) | `viewer/*.js` | Done |
| Three.js r184 ESM | `viewer/lib/three.module.min.js` | Done — 357KB |
| sql-wasm.js + .wasm | `viewer/lib/sql-wasm.*` | Done — 50KB + 631KB |
| OrbitControls ESM | `viewer/lib/OrbitControls.module.js` | Done — 40KB |
| Post-processing (EffectComposer etc) | `viewer/lib/*.js` | Done |
| IFC import worker | `viewer/import_worker.js` | Done — 637 lines |
| IFC parser (CDN) | `web-ifc-api-iife.js` | External — unpkg CDN |
| BVH (CDN) | `three-mesh-bvh@0.8.0` | External — jsdelivr CDN |
| Landing page About box | `index.html` | Done — has install scripts |
| CI pipeline | `.github/workflows/ci.yml` | Done — 2-stage gate |
| CACHE_VERSION in sw.js | `viewer/sw.js` | Done — version tracking |

## Architecture

### Self-Packaging (runtime, in browser)

The viewer already has all JS loaded in memory. The "Install" button:

1. Reads all `<script>` tags from the current document
2. Fetches each script's source text (from Cache API or network)
3. Fetches `sql-wasm.wasm` as ArrayBuffer → base64
4. Builds a single HTML string with everything inlined
5. Triggers download as `BIM-OOTB.html`

No build step. No CI. The running viewer packages itself.

### Three Technical Challenges

#### 1. WASM Binary (sql-wasm.wasm) — SOLVABLE

**Current:** `initSqlJs({ locateFile: f => 'lib/' + f })` → fetches .wasm via XHR

**Solution:** sql-wasm.js accepts `wasmBinary` config:
```javascript
// Embed WASM as base64 string in the HTML
var WASM_B64 = '...base64...';  // ~841KB string
var wasmBuf = Uint8Array.from(atob(WASM_B64), c => c.charCodeAt(0)).buffer;
var SQL = await initSqlJs({ wasmBinary: wasmBuf });
```

Or use Blob URL (more memory-efficient):
```javascript
var wasmBlob = new Blob([wasmBuf], {type: 'application/wasm'});
var wasmUrl = URL.createObjectURL(wasmBlob);
var SQL = await initSqlJs({ locateFile: f => f === 'sql-wasm.wasm' ? wasmUrl : f });
```

**Size impact:** +841KB (base64 overhead on 631KB binary)
**Complexity:** Medium — patch one `locateFile` call

#### 2. ESM Imports on file:// — SOLVABLE

**Current:** `await import('./lib/three.module.min.js')` — dynamic ESM imports

**Problem:** `file://` protocol has CORS restrictions on some browsers for
dynamic `import()`. Chrome works, Firefox may block.

**Solution:** Convert all modules to inline IIFE/UMD scripts:
```html
<!-- Instead of: import('./lib/three.module.min.js') -->
<script id="three-module">
(function() {
  // ... three.module.min.js content ...
  // Expose to window.THREE
})();
</script>
```

Loader.js must be patched: instead of `await import(url)`, read from
`window.THREE` (already loaded by inline script above).

**Files to inline (ordered by dependency):**
- `three.module.min.js` (357KB) → `window.THREE`
- `OrbitControls.module.js` (40KB) → `window.THREE.OrbitControls`
- `Sky.js` → `window.THREE.Sky`
- `EffectComposer.js`, `RenderPass.js`, `SSAOPass.js`, `OutlinePass.js`, `OutputPass.js`

**Size impact:** ~500KB (already counted in Three.js total)
**Complexity:** High — loader.js rewrite to support both ESM (online) and
global (offline HTML). Use a flag: `if (window._STANDALONE)` → read globals,
else → dynamic import.

#### 3. Web Workers on file:// — SOLVABLE

**Current:** `new Worker('import_worker.js?v=8')`

**Solution:** Inline worker code as string, create Blob URL:
```javascript
var workerCode = '...import_worker.js content...';
var blob = new Blob([workerCode], {type: 'application/javascript'});
var worker = new Worker(URL.createObjectURL(blob));
```

Worker's `importScripts('https://unpkg.com/web-ifc@...')` still works from
Blob URL — `importScripts()` is exempt from CORS.

**Size impact:** +20KB (worker code as string)
**Complexity:** Low — one change in import.js

### External CDN Dependencies (stay external)

These are fetched from CDN at runtime. They work on `file://` because:
- `importScripts()` in workers bypasses CORS
- Dynamic script injection (`<script src="cdn">`) works on `file://`

| Library | CDN | Size | When loaded |
|---|---|---|---|
| web-ifc | unpkg.com | ~200KB | On IFC drop |
| three-mesh-bvh | jsdelivr | ~50KB | On viewer init |

If offline (no internet), these fail gracefully — IFC import won't work
without web-ifc, but previously loaded buildings still render.

### Built-In Updater

The single HTML embeds a version number (matching CACHE_VERSION from sw.js).
Update check flow:

```javascript
// Embedded in BIM-OOTB.html
var _STANDALONE_VERSION = 515;  // matches sw.js CACHE_VERSION at build time

function checkUpdate() {
  // 1. Fetch version from GitHub Pages
  fetch('https://red1oon.github.io/bim-ootb/viewer/sw.js', { cache: 'no-store' })
    .then(r => r.text())
    .then(text => {
      var match = text.match(/CACHE_VERSION\s*=\s*'v(\d+)'/);
      var remote = parseInt(match[1]);
      if (remote <= _STANDALONE_VERSION) { /* up to date */ return; }

      // 2. Check CI green (same as S283)
      return fetch('https://api.github.com/repos/red1oon/bim-ootb/actions/runs?branch=main&status=success&per_page=1')
        .then(r => r.json())
        .then(data => {
          if (!data.workflow_runs || !data.workflow_runs.length) {
            // "Not Ready, Try Later"
            return;
          }
          // 3. Fetch changelog, show OK/Cancel (reuse S283 pattern)
          // 4. If OK: download new BIM-OOTB.html from GitHub Pages
          //    The online viewer has a /download endpoint that self-packages
          //    Or: redirect to landing page About → Install
        });
    });
}
```

**Update delivery options:**
- **A. Self-replace:** Download new HTML, write over current file via
  `showSaveFilePicker()` (Chrome/Edge only — not Firefox)
- **B. Download alongside:** Download new `BIM-OOTB-v518.html` to Downloads,
  user deletes old one manually
- **C. Redirect to online:** "Update available — click to get latest" →
  opens GitHub Pages landing page → user clicks Install again

Option C is simplest and works everywhere. Option B is second best.

## What To Build

### Phase 1: Self-Packaging Function

Add to scene.js or a new `packager.js`:

```javascript
async function packageSelfAsHTML() {
  // 1. Collect all inline-able scripts
  // 2. Fetch sql-wasm.wasm → base64
  // 3. Fetch all viewer JS source text
  // 4. Build HTML with:
  //    - window._STANDALONE = true
  //    - All JS as <script> tags (IIFE, not ESM)
  //    - WASM as base64 string
  //    - Worker code as inline string
  //    - Version number from sw.js
  //    - Update checker function
  // 5. Trigger download
  var blob = new Blob([html], {type: 'text/html'});
  var a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'BIM-OOTB.html';
  a.click();
}
```

### Phase 2: Loader Dual-Mode

Patch `loader.js` to support both modes:
- **Online (normal):** `await import('./lib/three.module.min.js')` — ESM
- **Standalone (`window._STANDALONE`):** Read from `window.THREE` — globals

```javascript
if (window._STANDALONE) {
  // Three.js already loaded by inline <script>
  for (var k of Object.keys(window._THREE_MODULE)) THREE[k] = window._THREE_MODULE[k];
} else {
  var _std = await import('./lib/three.module.min.js');
  for (var k of Object.keys(_std)) THREE[k] = _std[k];
}
```

### Phase 3: About Box Install Button

Replace the existing shell script installer in the About box with:

```html
<button onclick="packageSelfAsHTML()">Install (Offline Viewer)</button>
```

This only works from the online viewer (GitHub Pages) — the About box
on the landing page links to the viewer first, then the viewer packages
itself.

Or: the landing page opens the viewer in a hidden iframe, calls
`contentWindow.packageSelfAsHTML()`, triggers the download.

### Phase 4: Disable S283 PWA Install Badge (surgical, don't rip out)

**Do NOT delete functions.** Many are reused by S284 (overlay, update
checker, share). Only hide the badge entry point.

**Disable (hide, don't delete):**
- Blue/green triangle badge — set `_showBadge = false` or comment out the
  `badgeHtml` assignment. Keep the code for reference.
- `beforeinstallprompt` listener in viewer.html — comment out, don't delete.
  Chrome still benefits from PWA manifest for "Add to Home Screen" via
  browser menu.

**Keep (reused by S284):**
- `_createProgressOverlay()` — reused by updater
- `_checkUpdate()` — reused by standalone updater
- `_fetchChangelog()` — reused by standalone updater
- `_applyUpdate()` — reused (adapted for standalone)
- `_shareProject()` — still useful
- `manifest.webmanifest` — users who manually "Add to Home Screen"
- `sw.js` + message handlers — online caching still works
- Home pill standalone detection
- `_verifyCacheWrite()` — reusable for standalone verification

**Why surgical:** Other sessions touch scene.js, panels.js, viewer.html.
Ripping out S283 code risks merge conflicts and regressions. Disabling
the badge entry point is one line. Dead code cleanup can happen later
in a dedicated session.

### Phase 5: Built-In Updater

In the standalone HTML, the Help palette (or a dedicated button) offers
"Check Update". Flow:
1. Fetch remote sw.js → parse CACHE_VERSION
2. Compare with `_STANDALONE_VERSION`
3. If newer + CI green → show changelog + OK/Cancel
4. OK → open landing page Install link (Option C) or download new HTML (Option B)

## Size Budget

| Component | Size |
|---|---|
| 80+ viewer JS (minified) | ~2MB |
| Three.js r184 | ~400KB |
| OrbitControls + effects | ~100KB |
| sql-wasm.js | ~50KB |
| sql-wasm.wasm (base64) | ~841KB |
| Workers (as string) | ~20KB |
| HTML shell + CSS | ~50KB |
| Update checker | ~5KB |
| **Total** | **~3.5MB** |

Gzip by browser on download: ~1.2MB transfer.

## What Users Get

| Capability | Online (GitHub Pages) | Standalone (HTML file) |
|---|---|---|
| View buildings | Yes (URL) | Yes (drop .db) |
| Import IFC | Yes | Yes (needs internet for web-ifc CDN) |
| All features | Yes | Yes |
| Update | Automatic (sw.js) | Manual (Check Update) |
| Share URL | Yes | No (file:// can't be shared) |
| Home screen icon | Via browser | Via OS shortcut to .html file |
| Works offline | Yes (SW cache) | Yes (self-contained) |
| Firefox | Yes | Yes |
| Chrome | Yes | Yes |
| iOS | Yes | Yes (open .html in Safari) |

## Witness Claims

| ID | Claim | Proof |
|---|---|---|
| W-284-1 | About box Install downloads BIM-OOTB.html | §PACK_DOWNLOAD size=N |
| W-284-2 | HTML opens from file:// with 3D viewer | §STANDALONE_INIT version=N |
| W-284-3 | IFC drop works in standalone | §STANDALONE_IFC elements=N |
| W-284-4 | sql-wasm loads from embedded base64 | §STANDALONE_WASM ok=true |
| W-284-5 | Three.js loads from inline globals | §STANDALONE_THREE r=184 |
| W-284-6 | Worker runs from Blob URL | §STANDALONE_WORKER ready |
| W-284-7 | Update check finds newer version | §STANDALONE_UPDATE remote=N local=N |
| W-284-8 | Update blocked when CI not green | §STANDALONE_UPDATE ci=fail |

## Implementation Order

1. **Proof of concept:** Manually create a minimal single HTML with Three.js
   + sql-wasm + one viewer feature. Verify it works from `file://`.
2. **Loader dual-mode:** Patch loader.js for `_STANDALONE` flag
3. **Self-packaging function:** `packageSelfAsHTML()` in new `packager.js`
4. **About box button:** Wire Install in landing page
5. **Updater:** Version check + CI gate + changelog
6. **Remove S283 badge:** Clean up PWA install code
7. **Tests:** Whitebox for package contents, Playwright for download trigger

## Risk

| Risk | Severity | Mitigation |
|---|---|---|
| file:// CORS blocks ESM | High | IIFE/UMD conversion, `_STANDALONE` flag |
| 5MB HTML feels large | Low | Gzip ~1.2MB, comparable to any app installer |
| web-ifc CDN unavailable offline | Medium | Graceful fail, show "IFC import needs internet" |
| Browser blocks download | Low | Standard `<a download>` pattern, widely supported |
| Self-replace update (showSaveFilePicker) | Medium | Firefox unsupported — use Option B/C fallback |
