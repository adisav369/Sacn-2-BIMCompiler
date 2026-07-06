# S203 — Viewer UX Fixes + OCI Polish

## DONE (2026-04-20)

### 1. Button scoping — FIXED
All functions hoisted to `window.` from `initViewer()` scope via explicit assignments at end of function.
`window.togglePanel`, `window.clearStreamed`, `window.toggleXray`, `window.screenshot`, etc.

### 2. Camera positioning — FIXED
Camera uses actual building envelope from DB (`SELECT MIN/MAX center_x/y/z FROM element_transforms`).
Distance = `max(80, envelope * 1.5)`. Fly orbit = envelope × 1.2, height × 0.6.
No more guessing from `sqrt(count)`.

### 3. httpvfs — RETIRED
**Root cause:** 130ms per HTTP Range request × hundreds of B-tree pages = minutes of stalling.
**Fix:** Split sandbox into 30 per-building DB pairs. Full download via sql.js (1-2s per building).
**Extraction script:** `scripts/extract_per_building.py`
**httpvfs verdict:** Only viable for single-row lookups on large DBs. Useless for BIM viewers that need all data.

### 4. IndexedDB cache
`bim_ootb_cache` store — every downloaded DB cached in browser. Second visit = instant (no network).
`cachedFetch(url)` wrapper: try IndexedDB → fall back to network → store result.
Clear: F12 → Application → IndexedDB → Delete, or "Clear all cached data" link on landing page.

### 5. Per-building extraction — ALL 30 ARCHETYPES
16 new buildings extracted + uploaded to OCI `bim-ootb-full/buildings/`:
LTU_AHouse (173MB), Hospital (86MB), Hospital_3 (87MB), Terminal (57MB), Clinic (30MB),
Ifc4_Revit (41MB), WBDG_Office (13MB), HHS_Office_Federated (9MB), HITOS (5MB),
Esplanades (1MB), HospitalGarage (2MB), HospitalGarage_2 (2MB),
Schependomlaan (4MB), SampleCastle (4MB), Molio (3MB), BimWhale_Advanced (6MB).
14 existing small buildings unchanged.

### 6. City mode
`city_index.db` (324KB) — pre-computed building bboxes + archetype mapping for all 786 buildings.
Viewer param: `?city=buildings/city_index.db&bldbase=buildings/`
Shows all bboxes. Click any → downloads per-building DB on demand (from cache if already explored).
Position offset applied: archetype DB has one copy, city copies placed at correct coordinates.

### 7. "Complete the City" progress
Landing page tracks exploration (localStorage `bim_ootb_viewed`). 30 archetypes.
Green checkmarks on viewed cards. Progress bar fills. "LAUNCH CITY 1M" button appears on completion.

### 8. Streaming performance
Batch=50 per frame (~3ms of 16ms budget). Smooth 60fps during streaming.
Time-budget (8ms) explored but reverted to simple batch=50 — same result, easier to reason about.
Auto-fly starts 2 seconds after building loads.

### 9. Landing page
All 30 buildings use direct-download viewer. "Landmark Buildings" (>5K elements) + "City Buildings".
Footer: storage explanation, "Clear all cached data" link, "experimental project" footnote → BIM Designer Browser docs.

## Architecture
```
User clicks building → cachedFetch(extracted.db) + cachedFetch(library.db)
  → IndexedDB hit? instant : download from OCI (10MB/s) then cache
  → sql.js Database(ArrayBuffer) — in-memory SQLite
  → streamTick() 50 meshes/frame → Three.js scene
  → 60fps smooth, building appears piece by piece
```

## Files changed
- `deploy/rtree_browser_demo.html` — button fix, camera, cache, city mode, batch=50
- `deploy/landing.html` — all 30 buildings, progress bar, city launch, cache info
- `deploy/buildings/city_index.db` — 324KB city index (786 buildings, 30 archetypes)
- `deploy/buildings/*_extracted.db` + `*_library.db` — 16 new per-building DB pairs
- `scripts/extract_per_building.py` — extraction script

## OCI
Region: ap-kulai-2 (Malaysia). Bucket: bim-ootb-full. Public read. Free Tier.
URL: `https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/index.html`
