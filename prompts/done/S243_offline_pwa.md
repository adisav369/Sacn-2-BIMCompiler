# ⚠ DO NOT REMOVE — Scope: Offline PWA for BIM OOTB. Read the log after every run.

# S243 — Offline PWA: Install & Run Without Internet

## Status: SPEC

---

## §1 Product Vision

**"Load once, use forever."**

After a user visits BIM OOTB once with internet, the app works fully offline —
including any building DBs they've already viewed. On mobile, an "Install App"
prompt lets them add it to their home screen like a native app.

---

## §2 Current State (what already works)

| Component | Status | File |
|-----------|--------|------|
| Service Worker | EXISTS, v242, **not registered** | `deploy/dev/sw.js` |
| CDN cache-first | YES (Three.js, sql.js, xlsx, sql-wasm.wasm) | `sw.js` lines 14-21 |
| Network-first for JS/HTML | YES (always fresh on deploy) | `sw.js` lines 38-49, 66-68 |
| DB caching in IndexedDB | YES — `A.cachedFetch()` | `scene.js` lines 111-172 |
| DB skip in SW | YES — `.db` URLs bypass SW | `sw.js` line 58 |
| Manifest | **MISSING** | — |
| SW registration | **MISSING** | `index.html` has no `navigator.serviceWorker.register()` |
| Precache of JS modules | **MISSING** — SW only caches on fetch, not on install | `sw.js` install event is empty |
| Offline fallback page | **MISSING** | — |
| Install prompt | **MISSING** | — |

**Gap:** Three items — register SW, add manifest, precache JS on install.

---

## §3 Spec — Changes Required

### 3.1 Register Service Worker (`index.html`)

Add before `</body>`, after the GoatCounter script block:

```html
<script>
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('sw.js?v=243')
    .then(r => console.log('§SW_REG scope=' + r.scope))
    .catch(e => console.warn('§SW_REG_FAIL', e));
}
</script>
```

Also add to `landing.html`, `boq_charts.html`, `2d.html` — all entry points
share the same SW and cache.

### 3.2 PWA Manifest (`manifest.webmanifest`)

Create `deploy/dev/manifest.webmanifest`:

```json
{
  "name": "BIM OOTB — Frictionless BIM",
  "short_name": "BIM OOTB",
  "description": "Two DBs. One browser. Zero install.",
  "start_url": "index.html",
  "display": "standalone",
  "orientation": "any",
  "background_color": "#1a1a2e",
  "theme_color": "#4fc3f7",
  "icons": [
    { "src": "icons/icon-192.png", "sizes": "192x192", "type": "image/png" },
    { "src": "icons/icon-512.png", "sizes": "512x512", "type": "image/png" }
  ]
}
```

Link in `<head>` of `index.html`, `landing.html`, `boq_charts.html`, `2d.html`:

```html
<link rel="manifest" href="manifest.webmanifest">
<meta name="theme-color" content="#4fc3f7">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
<link rel="apple-touch-icon" href="icons/icon-192.png">
```

### 3.3 Icons

Generate from existing BIM OOTB logo or create minimal icon:
- `deploy/dev/icons/icon-192.png` (192×192)
- `deploy/dev/icons/icon-512.png` (512×512)

Can be a simple dark-blue square (#1a1a2e) with "BIM" in cyan (#4fc3f7) text.
Placeholder is fine — replace with proper logo later.

### 3.4 Precache JS Modules on Install (`sw.js`)

Replace the empty `install` event with precaching of all viewer JS modules.
These are the files loaded by `index.html` — once cached, the viewer works
fully offline (DB files are already in IndexedDB via `cachedFetch`).

```js
const PRECACHE_ASSETS = [
  // Entry points
  'index.html',
  'landing.html',
  'boq_charts.html',
  '2d.html',
  // Core viewer modules (order matches index.html script tags)
  'config.js',
  'helpers.js',
  'loader.js',
  'scene.js',
  'streaming.js',
  'panels.js',
  'tools.js',
  'picking.js',
  'tour.js',
  'measure.js',
  'sitecam.js',
  'issues.js',
  'excel.js',
  'walk.js',
  'city.js',
  'rates.js',
  'locale_loader.js',
  'nlp.js',
  'semantic_enrichment.js',
  'scene_to_db.js',
  'import_db_builder.js',
  'diff.js',
  'variation_order.js',
  'import.js',
  'main.js',
  // Workers (fetched on demand)
  'import_worker.js',
  'ifc_export_worker.js',
  'mesh_import_worker.js',
  // Lazy-loaded modules
  'navigate.js',
  'wizard.js',
  'section_cut.js',
  'dxf-parser.js',
  'dxf_export.js',
  'elevation.js',
  'grid_dims.js',
  'title_block.js',
  // PWA
  'manifest.webmanifest',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache =>
      cache.addAll([...PRECACHE_ASSETS, ...CDN_ASSETS])
    )
  );
  self.skipWaiting();
});
```

**Note:** `.db` files are NOT precached — they stay in IndexedDB via
`A.cachedFetch()`. This is correct: DB files are large (10-50MB each) and
building-specific. The user caches them on first view.

### 3.5 Cache-First for Precached JS/HTML (`sw.js`)

**Problem:** `isNetworkFirst()` returns `true` for all `.js` and `.html` files,
so every page refresh hits the network before falling back to cache. This wastes
bandwidth and breaks the "load once, use forever" vision — the user sees fetch
spinners on every reload even though all 40+ modules are precached on install.

**Why network-first is redundant:** The `CACHE_VERSION` mechanism already
guarantees freshness. On deploy, `CACHE_VERSION` bumps → `activate` purges old
cache → `install` precaches all new files. There is no stale-cache risk.

**Fix:** In `isNetworkFirst()`, return `false` for any URL that matches a
`PRECACHE_ASSETS` entry. These files are already in the cache from install —
serve them instantly. Only truly unknown URLs (not in precache list) fall
through to network-first.

```js
// Build a Set of precache basenames for O(1) lookup
const _PRECACHE_SET = new Set(PRECACHE_ASSETS);

function isNetworkFirst(url) {
  var base = url.split('?')[0];
  // lib/ and CDN assets are immutable — always cache-first
  if (base.includes('/lib/')) return false;
  for (const cdn of CDN_ASSETS) {
    if (url === cdn || base === cdn) return false;
  }
  // Precached local files — cache-first (freshness via CACHE_VERSION bump)
  var filename = base.split('/').pop();
  if (_PRECACHE_SET.has(filename)) return false;
  // Unknown JS/HTML not in precache — network-first (safe default)
  if (base.endsWith('.html') || base.endsWith('.js')) return true;
  return false;
}
```

**Result:** First visit precaches everything. Every subsequent refresh loads
from cache in <50ms. Deploy bumps version → old cache purged → fresh precache.
Offline works identically to online.

- W-CACHE-FIRST: DevTools Network tab shows `(ServiceWorker)` for all JS on reload, zero network requests

### 3.6 Local CDN Libs for Sibling Pages (`lib/`)

**Problem:** `boq_charts.html` and `mep_report.html` load Chart.js, ExcelJS, and
FileSaver from CDN. These are not in sw.js `LOCAL_LIBS` or `CDN_ASSETS`, so they
fail offline. The viewer works because its deps (Three.js, sql-wasm, xlsx) are
already local in `lib/`.

**Fix:** Download local copies + point sibling HTML to `lib/` + add to sw.js.

New files in `deploy/dev/lib/`:
- `chart.umd.min.js` (Chart.js 4.4.1)
- `exceljs.min.js` (ExcelJS 4.4.0)
- `FileSaver.min.js` (FileSaver 2.0.5)

Update `boq_charts.html`:
```html
<script src="lib/sql-wasm.js"></script>
<script src="lib/xlsx.full.min.js"></script>
<script src="lib/exceljs.min.js"></script>
<script src="lib/FileSaver.min.js"></script>
<script src="lib/chart.umd.min.js"></script>
```

Update `mep_report.html`:
```html
<script src="lib/sql-wasm.js"></script>
<script src="lib/chart.umd.min.js"></script>
```

Also update `locateFile` in both pages to point to `lib/` for `sql-wasm.wasm`.

Add all three to sw.js `LOCAL_LIBS` array so they're precached on install.

- W-5D-OFFLINE: boq_charts.html renders with network disabled (no CDN dependency)
- W-MEP-OFFLINE: mep_report.html renders with network disabled

### 3.7 Persist kernel_ops to IndexedDB (`kernel_ops.js`)

**Problem:** `kernel_ops.js` writes ops to the in-memory sql.js database. On
refresh, `A.cachedFetch()` reloads the *original* DB from IndexedDB — all
kernel_ops edits (grid drags, section cuts, placements) are lost.

**Fix:** After each `commitOp()`, export the DB and write it back to IndexedDB
under the same cache key (`A.DB_URL`). This way a refresh — even a hard reset
or a desktop PWA relaunch — replays the latest state.

```js
function commitOp(db, opType, params, inputGuids, outputGuid) {
  ensureTable(db);
  db.run(
    'INSERT INTO kernel_ops ...',
    [Date.now(), opType, JSON.stringify(params), ...]
  );
  ...
  // Persist modified DB back to IndexedDB
  _persistToIdb(db);
  return opId;
}

// Debounced IDB write — avoids hammering IDB on rapid ops (e.g. drag)
var _persistTimer = null;
function _persistToIdb(db) {
  clearTimeout(_persistTimer);
  _persistTimer = setTimeout(function() {
    try {
      var dbUrl = window.APP && APP.DB_URL;
      if (!dbUrl) return;
      var buf = db.export().buffer;
      var req = indexedDB.open('bim_ootb_cache', 1);
      req.onupgradeneeded = function() { req.result.createObjectStore('dbs'); };
      req.onsuccess = function() {
        var tx = req.result.transaction('dbs', 'readwrite');
        tx.objectStore('dbs').put(buf, dbUrl);
        console.log('§KRN_PERSIST url=' + dbUrl + ' size=' + (buf.byteLength/1024).toFixed(0) + 'KB');
      };
    } catch(e) { console.warn('§KRN_PERSIST_ERR', e); }
  }, 2000);
}
```

Debounce at 2 seconds — during rapid grid drags this fires once after the drag
settles, not per pixel. `db.export()` is ~10-50ms for typical buildings.

- W-KRN-PERSIST: `§KRN_PERSIST` in console after grid drag, then refresh loads the moved grid

### 3.8 IndexedDB cache for 2d.html

**Problem:** `2d.html` uses bare `fetch()` for both building DB and library DB
(lines ~47744-47757). Offline, these fail.

**Fix:** Add the same `fetchDbBuffer()` IDB-cache function used by boq_charts.html
and mep_report.html. Replace bare `fetch()` calls with `fetchDbBuffer()`.

```js
async function fetchDbBuffer(url) {
  var cacheDb = await new Promise(function(resolve) {
    var req = indexedDB.open('bim_ootb_cache', 1);
    req.onupgradeneeded = function() { req.result.createObjectStore('dbs'); };
    req.onsuccess = function() { resolve(req.result); };
    req.onerror = function() { resolve(null); };
  });
  if (cacheDb) {
    try {
      var cached = await new Promise(function(ok, fail) {
        var tx = cacheDb.transaction('dbs', 'readonly');
        var req = tx.objectStore('dbs').get(url);
        req.onsuccess = function() { ok(req.result); };
        req.onerror = function() { fail(); };
      });
      if (cached) { console.log('§2D_CACHE_HIT ' + url); return cached; }
    } catch(e) {}
  }
  var resp = await fetch(url);
  if (!resp.ok) throw new Error('DB fetch ' + resp.status);
  var buf = await resp.arrayBuffer();
  if (cacheDb) {
    try {
      var tx = cacheDb.transaction('dbs', 'readwrite');
      tx.objectStore('dbs').put(buf, url);
    } catch(e) {}
  }
  return buf;
}
```

- W-2D-OFFLINE: 2d.html loads a previously-viewed building with network disabled

### 3.9 Offline Fallback (optional, low priority)

If a fetch fails and nothing is cached, sw.js `networkFirst()` already returns
`undefined` which shows a browser error. For polish, add a cache match for
`offline.html` in the catch block:

```js
.catch(() => caches.match(request).then(r => r || caches.match('offline.html')))
```

Create a minimal `deploy/dev/offline.html`:
```html
<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>BIM OOTB — Offline</title>
<style>body{background:#1a1a2e;color:#4fc3f7;font-family:sans-serif;
display:flex;align-items:center;justify-content:center;height:100vh;
text-align:center}</style></head>
<body><div><h1>You're Offline</h1>
<p>Open a building you've viewed before — it's cached locally.</p>
<p style="color:#888;font-size:12px">BIM OOTB — Frictionless BIM</p>
</div></body></html>
```

### 3.6 Install Prompt (optional, low priority)

Capture `beforeinstallprompt` event to show a custom "Install" button:

```js
let deferredPrompt;
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  deferredPrompt = e;
  // Show install button in toolbar or HUD
  document.getElementById('install-btn').style.display = 'inline-block';
});
document.getElementById('install-btn')?.addEventListener('click', () => {
  if (deferredPrompt) { deferredPrompt.prompt(); deferredPrompt = null; }
});
```

Button placement: in the "About" dropdown or as a small icon in the toolbar.

---

## §4 Implementation Plan

**Phase 1 — Core offline (5 minutes)**
1. Add SW registration to `index.html` (§3.1)
2. Add SW registration to `landing.html`, `boq_charts.html`, `2d.html`
3. Create `manifest.webmanifest` (§3.2)
4. Add `<link rel="manifest">` + meta tags to all 4 HTML entry points
5. Create placeholder icons (§3.3)
6. Update `sw.js` install event with precache list (§3.4)
7. Bump `CACHE_VERSION` to `'v243'`

**Phase 2 — Verify**
8. Deploy to dev bucket
9. Open in Chrome, check DevTools → Application → Service Workers: registered
10. Check Application → Manifest: valid, installable
11. Check Application → Cache Storage: all JS files present
12. Go offline (DevTools → Network → Offline checkbox)
13. Reload — viewer must load from cache
14. Open a previously-viewed building — DB loads from IndexedDB
15. Verify all toolbar buttons, panels, NLP work offline

**Phase 3 — True Offline Desktop**
16. Download Chart.js, ExcelJS, FileSaver to `lib/` (§3.6)
17. Update boq_charts.html + mep_report.html to use `lib/` paths (§3.6)
18. Add local CDN libs to sw.js `LOCAL_LIBS` (§3.6)
19. Add `_persistToIdb()` to kernel_ops.js `commitOp()` (§3.7)
20. Add `fetchDbBuffer()` IDB cache to 2d.html (§3.8)

**Phase 4 — Polish (separate session)**
21. Add `offline.html` fallback (§3.9)
22. Add install prompt button (§3.10)
23. Proper logo icons

---

## §5 What NOT To Change

- `A.cachedFetch()` in `scene.js` — already perfect, don't touch
- DB loading flow — IndexedDB caching already works
- SW fetch strategies — cache-first for precached JS/HTML (§3.5), network-first for unknown files
- `.db` bypass in SW — correct, IndexedDB handles these

---

## §6 Witnesses

- W-SW-REG: `§SW_REG scope=` appears in console on page load
- W-PRECACHE: DevTools Cache Storage shows all JS files after first visit
- W-MANIFEST: DevTools Application → Manifest shows valid PWA
- W-OFFLINE: Viewer loads and renders a cached building with network disabled
- W-INSTALL: Chrome shows "Install app" option (desktop) or "Add to Home Screen" (mobile)
- W-CACHE-FIRST: DevTools Network tab shows `(ServiceWorker)` for all JS on reload, zero network fetches
- W-5D-OFFLINE: boq_charts.html renders charts with network disabled
- W-MEP-OFFLINE: mep_report.html renders with network disabled
- W-KRN-PERSIST: `§KRN_PERSIST` in console after grid drag; refresh preserves moved grids
- W-2D-OFFLINE: 2d.html loads a previously-viewed building with network disabled

---

## §7 Files to Edit

| File | Change |
|------|--------|
| `deploy/dev/sw.js` | Add PRECACHE_ASSETS, cache-first for precached, local CDN libs, bump version |
| `deploy/dev/index.html` | Add SW register script, manifest link, meta tags |
| `deploy/dev/landing.html` | Add SW register script, manifest link, meta tags |
| `deploy/dev/boq_charts.html` | Add SW register script, manifest link, meta tags |
| `deploy/dev/2d.html` | Add SW register script, manifest link, meta tags |
| `deploy/dev/manifest.webmanifest` | NEW — PWA manifest |
| `deploy/dev/icons/icon-192.png` | NEW — app icon |
| `deploy/dev/icons/icon-512.png` | NEW — app icon |
| `deploy/dev/kernel_ops.js` | Add `_persistToIdb()` after commitOp — survive refresh |
| `deploy/dev/2d.html` | Add `fetchDbBuffer()` IDB cache for DB loading |
| `deploy/dev/lib/chart.umd.min.js` | NEW — local Chart.js 4.4.1 |
| `deploy/dev/lib/exceljs.min.js` | NEW — local ExcelJS 4.4.0 |
| `deploy/dev/lib/FileSaver.min.js` | NEW — local FileSaver 2.0.5 |
| `deploy/dev/offline.html` | NEW — offline fallback (Phase 4) |

---

## §8 Deploy Checklist

- [ ] `sw.js` CACHE_VERSION bumped
- [ ] All 4 HTML files have SW registration + manifest link
- [ ] `manifest.webmanifest` uploaded alongside HTML
- [ ] `icons/` directory uploaded
- [ ] Smoke test: load page → go offline → reload → works
- [ ] Mobile: "Add to Home Screen" prompt appears
