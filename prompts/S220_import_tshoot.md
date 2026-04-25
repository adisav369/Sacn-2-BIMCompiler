# S220 — Import Troubleshooting Log

## DO NOT REMOVE
Scope: Continue debugging browser IFC import. Read this before making changes.
Read the log after every run.

## SOP REMINDER — Tests Must Log
Do NOT ask the user to test manually and report console output. Your code must emit
`§`-tagged log lines for every significant step. After every change, read the log yourself
before drawing conclusions. If a log tag is missing, add it before deploying. The user should
never be the one telling you what the console says — the code should tell you.

## Status: WORKING — IFC2x3+IFC4 import proven, 122K elements tested

## What Works
- WASM loads from OCI (web-ifc 0.0.77, `sandbox/web-ifc.wasm` with `application/wasm` MIME)
- Import zone on dev landing page — drag-drop + file picker
- Progress bar, My Buildings cards, delete with cache clear
- web-ifc parses IFC2x3, IFC4, IFC4x3 files
- Worker lifecycle logging: `§WORKER_START` → `§WORKER_LOADED` → `§WASM_INIT` → `§WASM_LOCATE` → `§PARSE_START`

## Bug 1: IFC4X4 returns modelID=-1 (not caught)

**Console log:**
```
[S220] §PARSE_START size=0.3MB
ERROR:  Unsupported Schema:IFC4X4_B072BCCA
[S220] §PARSE_OK modelID=-1
[S220] §IMPORT_FATAL Passing a number "-1" from JS side to C/C++ side to an argument of type "unsigned int", which is outside the valid range [0, 4294967295]!
[S220] §IMPORT_STACK assertIntegerRange@...web-ifc-api-iife.js:5007:21
  toWireType@...web-ifc-api-iife.js:5862:33
  invokerFn@...web-ifc-api-iife.js:5653:48
  GetAllLines@...web-ifc-api-iife.js:73245:37
  self.onmessage@.../import_worker.js:82:26
```

**Root cause:** `ifcApi.OpenModel()` returns -1 for unsupported schemas but does NOT throw.
The current try/catch around OpenModel only catches exceptions, not -1 returns.
Code proceeds to `GetAllLines(modelID=-1)` → C/C++ unsigned int overflow → crash.

**Fix:** After `OpenModel()`, check `if (modelID < 0)` → post error message with schema name → return.
The `§PARSE_FAIL` catch block handles thrown errors but not -1 returns.

```js
modelID = ifcApi.OpenModel(data);
if (modelID < 0) {
  console.log('[S220] §PARSE_FAIL modelID=' + modelID + ' (unsupported schema?)');
  self.postMessage({ type: 'error', message: 'Failed to parse IFC. Check schema version — supported: IFC2x3, IFC4, IFC4x3.' });
  return;
}
```

## Bug 2: IFC4X3 parses but viewer fails on null minZ

**Console log (IFC4X3 earthworks file):**
```
[S203] §CACHE_HIT import://bSI_Earthworks_Bentley2_IFC4X3.ifc/extracted size=0.0MB
[S192] §DB_LOADED size=0MB
[S192] §BOOTSTRAP centres=0
[S192] §OFFSET ifc=(0, 0, 0)
[S192] §INIT_ERROR TypeError: can't access property "toFixed", minZ is null
    init .../streaming.js?v=3:296
```

**Root cause:** `centres=0` means `element_transforms` table has zero rows. The IFC was parsed
and elements were found, but NO elements had geometry (all skipped in tessellation).
This is an earthworks file — IfcGeographicElement, terrain meshes, alignment curves — none of
which are in our PRODUCT_TYPES list in `import_worker.js`.

The viewer then queries `SELECT MIN(center_z) FROM element_transforms` → null → `.toFixed()` crash.

**Two fixes needed:**
1. **import_worker.js:** Add more IFC types to PRODUCT_TYPES, especially:
   - `IFCGEOGRAPHICELEMENT` (earthworks, terrain)
   - `IFCALIGNMENT` (roads, rail)
   - `IFCSITE` (site geometry)
   - Or better: extract ALL IfcProduct subtypes instead of a hardcoded list
2. **streaming.js (or import error handling):** Guard against zero-element imports.
   If `element_transforms` is empty after import, show "No viewable elements found" instead of crashing.

## Files to Change
- `deploy/dev/import_worker.js` — check modelID < 0, expand PRODUCT_TYPES or use GetAllTypesOfType
- `deploy/landing2.html` — (already fixed: delete clears cache + progress bar)
- `deploy/dev/streaming.js` — guard against null minZ (zero elements edge case)

## Schema Reference (viewer expects this)
```
extracted DB:
  elements_meta: guid, ifc_class, element_name, storey, discipline, material_name, material_rgba, building
  element_transforms: guid, center_x, center_y, center_z, rotation_x, rotation_y, rotation_z
  element_instances: guid, geometry_hash
  project_metadata: key, value

library DB:
  component_geometries: geometry_hash, vertices (BLOB), faces (BLOB), building
```

## Architecture
```
Landing page (landing2.html)
  ├─ Import zone (drop/pick IFC)
  ├─ handleImportFile() → Web Worker
  │   └─ import_worker.js
  │       ├─ importScripts(web-ifc CDN)
  │       ├─ web-ifc.wasm (hosted on OCI, application/wasm MIME)
  │       ├─ Parse IFC → extract elements → tessellate
  │       └─ postMessage({elements, geometries, transforms})
  ├─ Build sql.js DBs (main thread, viewer-compatible schema)
  ├─ Save to IndexedDB (bim_ootb_imports store)
  ├─ Card appears in "My Buildings"
  └─ Open card → viewer loads via import:// URL from cachedFetch

Viewer (sandbox/index.html)
  ├─ config.js reads ?db=import://... from URL
  ├─ streaming.js → cachedFetch → IndexedDB hit
  └─ Streams normally (same as OCI-hosted DBs)
```

## Debug Log Tags (all present in code)
| Tag | Where | What |
|-----|-------|------|
| `§WORKER_START` | import_worker.js | Worker loading |
| `§WORKER_LOADED` | import_worker.js | IIFE loaded |
| `§WASM_INIT` | import_worker.js | Init starting/done |
| `§WASM_LOCATE` | import_worker.js | WASM path resolution |
| `§PARSE_START` | import_worker.js | File size |
| `§PARSE_OK` / `§PARSE_FAIL` | import_worker.js | Parse result + modelID |
| `§EXTRACT_START` | import_worker.js | Total IFC lines |
| `§ELEMENTS_FOUND` | import_worker.js | Element + storey count |
| `§GEOM_DONE` | import_worker.js | Geometry count + skipped |
| `§DB_BUILD` | landing2.html | DB creation stats |
| `§IMPORT_DELETE` | landing2.html | Card deleted |
| `§IMPORT_FATAL` | import_worker.js | Unhandled error + stack |

## Test Files
- IFC4X4: `bSI_Earthworks_Fill_IFC4X3.ifc` (actually IFC4X4 despite name) → should show "Unsupported"
- IFC4X3: `bSI_Earthworks_Bentley2_IFC4X3.ifc` → parses but 0 viewable elements (earthworks)
- Need test with: standard IFC2x3 building (Duplex, SampleHouse) to prove full pipeline

## Refactor: Extract shared code
The DB builder (elements_meta + element_transforms + element_instances + component_geometries)
is duplicated between `landing2.html` (inline) and `deploy/dev/import.js`.

Extract to: `deploy/dev/import_db_builder.js`
- Single function: `buildImportDBs(SQL, workerResult) → { extractedDb, libraryDb }`
- Both landing2.html and import.js call this
- One place to update schema if viewer changes

Current files and responsibilities:
```
import_worker.js     — web-ifc parse + tessellate (Web Worker, no sql.js)
import_db_builder.js — NEW: build sql.js DBs from worker output (main thread)
import.js            — viewer-side: setupImport, IndexedDB ops, drop zone wiring
landing2.html        — landing-side: inline import handler, card rendering
```

## Fixes Applied This Session

### Bug 1 FIXED: modelID < 0 guard (import_worker.js:79-83)
After `OpenModel()`, if `modelID < 0`, post error message with schema hint and return.
No more unsigned int overflow crash on IFC4X4 files.

### Bug 2 FIXED: null minZ guard (streaming.js:291-300)
Two guards added:
1. `minZ == null` check before `.toFixed()` — logs `§GROUND_SKIP` instead of crashing
2. `bboxQ[0].values[0][0] != null` check on bbox envelope query — prevents NaN dimensions

### PRODUCT_TYPES expanded (import_worker.js:137-162)
Added 25+ missing types: IfcSpace, IfcPile, IfcReinforcingBar/Mesh, IfcTendon,
IfcFlowStorageDevice, IfcFlowTreatmentDevice, IfcEnergyConversionDevice,
IfcValve, IfcWasteTerminal, IfcStackTerminal, IfcAirTerminal/Box, IfcCoil, IfcFan,
IfcCompressor, IfcChiller, IfcFireSuppressionTerminal, IfcAlarm, IfcJunctionBox,
IfcSwitchingDevice, IfcElectricDistributionBoard, IfcCableCarrierFitting,
IfcDistributionElement, IfcGeographicElement (IFC4x3), IfcSite.
Now matches DISC_MAP coverage.

### import.js schema FIXED (import.js:86-128)
Was: 4x4 matrix columns (m00-m33) + `t.matrix` ref (field doesn't exist in worker output)
Now: `center_x, center_y, center_z, rotation_x, rotation_y, rotation_z` + `element_instances` table + `material_rgba` column
Matches landing2.html inline schema and viewer's streaming.js expectations.

### landing2.html worker path — CORRECT as-is
`new Worker('sandbox/import_worker.js')` is correct for OCI deployment (dev files upload to `sandbox/` prefix).
Need to upload import_worker.js to dev bucket: `oci os object put --bucket-name bim-ootb-dev --file deploy/dev/import_worker.js --name sandbox/import_worker.js --content-type application/javascript --force`

## Session Results (2026-04-24)
- IFC2x3: ✓ (Duplex, Smiley West, HospitalGarage — all working)
- IFC4: ✓ (SampleHouse 60 elements, glass+materials, FZKHaus — working)
- IFC4 large: ✓ (Revit Federated 11K, LTU AHouse 122K — working)
- IFC4X4: ✓ (unsupported schema error, no crash)
- Unit scaling: ✓ (autoScale heuristic — mm files auto-detected)

## Known Limitations
- **Wall openings**: USE_FAST_BOOLS enabled but web-ifc 0.0.77 may not fully subtract
  IfcOpeningElement from walls. Glass panels behind solid walls may be hidden.
- **Materials**: web-ifc geo.color sometimes returns all-white (1,1,1,1). The Java
  pipeline (IfcOpenShell) extracts richer materials from IfcStyledItem.
- **IfcSpace/IfcSite**: excluded from extraction (render as solid blocks, obscure model)

## S222 Session (2026-04-24) — DB Refactor + Incremental Diff + VO Cost Engine

### Agent A DONE: DB Builder Refactor + Auto-Save
- **Created** `deploy/dev/import_db_builder.js` — shared `buildImportDBs(SQL, data)` function
- **Edited** `deploy/dev/import.js` — removed inline `buildDatabases()`, calls shared builder
- **Edited** `deploy/landing2.html` — `<script src="sandbox/import_db_builder.js">`, inline builder replaced with 3-line call, auto-download after import, Save button removed from cards
- **Edited** `deploy/dev/index.html` — `<script src="import_db_builder.js?v=1">` before import.js

### Agent B DONE: Incremental Diff + Variation Order Excel
- **Created** `deploy/dev/diff.js` — `setupDiff(A)` with GUID set diff, colour overlay (green/red/yellow), cost preview in summary panel
- **Created** `deploy/dev/variation_order.js` — full cost engine, 3-sheet Excel:
  - Sheet 1: VO Configuration (cost factors, overhead, markup, disruption — editable)
  - Sheet 2: Variation Order Detail (every element with unit rate, cost factor, total impact, schedule days, 4D phase)
  - Sheet 3: Executive Summary (scope, 5D cost, 4D schedule, EVM formulas reference)
- **Cost model:** FIDIC Clause 12 + AACE change order costing + PMI EVM
  - ADD=1.0× rate, REMOVE=0.3× (demo+disposal), CHANGE=1.3× (remove+reinstall+disruption)
  - Total Impact = Direct × (1+10%OH+15%Markup) × (1+5%Disruption)
  - Schedule = count / productivity (from 4D_phases.json)
  - Rates: CIDB 2024 (from 5D_rates.json), USD conversion baked in
- **Variation detection** in landing2.html: same building name re-import → `§VARIATION_DETECTED`, card badge `v2`, `v3`
- **Edited** `deploy/dev/index.html` — diff.js, variation_order.js script tags
- **Edited** `deploy/dev/main.js` — `setupDiff(APP)` wired into init

### Agent C DONE: Test Harness
- **Created** `deploy/dev/s220_test.js` — Node.js test (better-sqlite3), 8 test categories
- **Result:** 58 PASS / 0 FAIL (SampleHouse 65el + FZKHaus 98el)
- Tests: SCHEMA (17 cols), LIB_SCHEMA (3 cols), DATA, TRANSFORMS (no NULL), BLOBS (no 0-byte), ENVELOPE (1-2000m), INTEGRITY (cross-table joins × 2)

### Debug Log Tags Added
| Tag | File | What |
|-----|------|------|
| `§DB_BUILD` | import_db_builder.js | Shared builder stats (was inline) |
| `§DIFF` | diff.js | GUID diff counts (added/removed/changed) |
| `§DIFF_OVERLAY` | diff.js | Overlay applied count |
| `§VO_EXPORT` | variation_order.js | Excel export row count + total impact |
| `§VARIATION_DETECTED` | landing2.html | Re-import same building name |
| `§S220_DB_TEST` | s220_test.js | Test harness header |
| `§RESULT` | s220_test.js | PASS/FAIL summary |

### Rate Templates (editable — set once per project)
| Template | Path | Used By | What to Edit |
|----------|------|---------|--------------|
| 5D Rates | `templates/5D_rates.json` | nD engine | Material rates per IFC class, labour, equipment |
| 4D Phases | `templates/4D_phases.json` | nD engine | Phase sequence, productivity (elements/day), predecessors |
| nD Formulas | `templates/nD_formulas.json` | nD engine | Measurement rules, cost/schedule/carbon formulas |
| **Shared Rates** | `deploy/dev/rates.js` | boq_charts, VO, NLP | **Single source** for all browser-side rates, labor, equipment, sequences |
| VO Config | `variation_order.js` §VO_CONFIG | VO Excel | Addition/removal/change factors, overhead%, markup%, disruption% |
| **_TRL Locale** | `deploy/dev/locales/{code}.js` | All browser pages | **Full package:** labels + currency + rates + labor + equipment per country |

**Localization via _TRL (iDempiere AD_Window_Trl pattern):**
Each locale file is a full package — not just translated labels but also country-specific rates.
15 locales shipped: en_MY (base), en_US, en_GB, en_AU, ms_MY, de_DE, fr_FR, es_ES, zh_CN, th_TH, ja_JP, ko_KR, ar_SA, pt_BR, id_ID.
User copies any locale → `MyProject_TRL.js` → edits what differs. ISO country code (`iso` field) drives flag emoji.

**Override priority (highest wins):**
1. URL params — `?cur=USD&rate=4.45&h_labour=Labor`
2. Project locale — `MyProject_TRL.js`
3. Country locale — `locales/{code}.js`
4. `_TRL_DEFAULTS` (en_MY base in `rates.js`)

**S225 refactor:** Rates extracted from 3 files into single `rates.js`. No more VO_RATES/COST_RATES duplication.

### Deployed to OCI Dev (bim-ootb-dev)
All 8 files uploaded, HTTP 200 verified:
- `index.html` (landing), `sandbox/index.html` (viewer)
- `sandbox/import_db_builder.js`, `sandbox/import.js`
- `sandbox/diff.js`, `sandbox/variation_order.js`

### Single DB Merge (late session)
- `import_db_builder.js` now creates ONE DB with all 4 tables (metadata + geometry)
- Returns `{ extractedDb: buf, libraryDb: buf }` — same buffer for both (backward compat)
- Viewer handles it via `A.libDb = A.db` fallback in streaming.js
- Enterprise separate library.db → future, consult creator (noted in docs)

### Hero Landing Panel (promoted to prod)
- Tagline: "In **60** seconds, the way you **BIM** will never be the same."
- Step flow: IMPORT — VIEW — COMPARE — COSTED — DONE
- Drop IFC zone inside panel, FIDIC/AACE/CIDB footer
- SOP followed: snapshot → deploy → smoke → commit
- `landing2.html` keeps DEV banner — sed-strip before prod upload (SOP updated in OCI_UPLOAD.md)

### Smoke Test Results (2026-04-24)
- **Ifc4_Revit_ARC.ifc** (14MB): imported OK, card appears, discipline bars correct
- **Ifc4_Revit_MEP.ifc** (28MB): imported OK, card appears, 6814 elements
- **City cards (prod):** 4D/5D boq_charts opens and shows content — WORKING
- **Tests:** s220_test.js 58 PASS, s211_test.js 58 PASS

### Open Issues for Next Session (DO NOT FIX — just spec)

**I-S222-1: Progress bar doesn't clear after import**
After successful import, green progress bar stays at 100% with "Imported N elements".
Should reset to "See your building below ↓" or clear after 3 seconds.
File: `deploy/landing2.html` handleImportFile() completion handler.

**I-S222-2: Variation detection by filename, not project name**
ARC and MEP discipline IFCs have different `meta.name` from web-ifc (project name in IFC header).
Variation detection matches on `meta.name` — so Ifc4_Revit_ARC and Ifc4_Revit_MEP don't trigger v2.
Fix: match on filename prefix (strip `_ARC`, `_MEP`, `_STR` suffix) or let user name the project.
File: `deploy/landing2.html` handleImportFile() variation detection block.

**I-S222-3: 4D/5D boq_charts fails for imported buildings**
`export4D5D()` in `tools.js:113` builds URL from `?db=` param using OCI `/o/` regex.
For imported buildings `?db=import://filename/extracted` — no `/o/`, regex falls back to `../`.
boq_charts tab opens but no data. Works fine for city/OCI-hosted buildings.
File: `deploy/sandbox/tools.js` export4D5D().

**I-S222-4: Hospital geometry anomaly**
Large Hospital IFC shows geometry artefacts at the back of the building.
Likely web-ifc tessellation issue with complex boolean openings or curved surfaces.
Known web-ifc 0.0.77 limitation (USE_FAST_BOOLS).

**I-S222-5: Large IFC "Open" click — no immediate feedback**
Clicking Open on a large imported building card → several seconds of nothing while viewer tab loads.
Should show "Opening viewer..." or spinner on button immediately.
File: `deploy/dev/import.js` openImported() or `deploy/landing2.html` openImported().

### Learnings — Dos and Don'ts

**DO:**
- Single DB for browser import — simpler mental model, no "two mystery files"
- Hero panel with CTA — headline sells, steps guide, drop zone acts
- sed-strip DEV markers for prod — never upload landing2.html as-is to prod
- Snapshot before every deploy — SOP Step 2, no exceptions
- Guard all new setup functions with `typeof === 'function'` — safe if script not loaded

**DON'T:**
- Don't auto-download DBs on import — confuses users, clutters Downloads folder
- Don't match variations by IFC project name alone — different disciplines have different names
- Don't assume OCI URL patterns for imported buildings — `import://` URLs break OCI regex
- Don't add features to prod viewer without testing on imported buildings — import:// path differs
- Don't touch landing2.html DEV markers — they exist for a reason, strip only at deploy time

### What's Next (S223)
1. Fix I-S222-1 through I-S222-5 (all in DEV, test before prod)
2. Wire diff viewer: load v2 DB alongside v1 as `A.diffDb`, trigger `computeDiff()` + overlay
3. Test VO Excel export with real diff data (ARC vs MEP)
4. Variation detection by filename prefix (not IFC project name)
5. Promote viewer changes to prod when diff + VO proven

## S223 Session (2026-04-24) — Fix 5 Issues + Wire Diff Viewer + VO Test

### Fixes DONE (all in DEV)

**I-S222-1: Progress bar auto-clear** — `landing2.html`
Status text updated to "Imported N elements — see your building below ↓", then auto-clears after 3s (status text, progress bar width, bar hidden).

**I-S222-2: Variation detection by filename prefix** — `landing2.html`
Was matching on `meta.name` (IFC project name in header). Now strips `_ARC`, `_MEP`, `_STR`, `_ELEC`, `_FP`, `_ACMV`, `_PLB` suffixes from filename to get project prefix. Both variation detection and version badge use the same prefix logic. Log tag: `§VARIATION_DETECTED prefix=...`.

**I-S222-3: 4D/5D boq_charts for imported buildings** — `tools.js` + `boq_charts.html`
- `tools.js`: Detects `import://` URL → uses `../boq_charts.html` relative path (not OCI `/o/` regex)
- `boq_charts.html`: Added `fetchDbBuffer()` — for `import://` URLs, reads from `bim_ootb_cache` IndexedDB (same cache the viewer uses). Log tag: `§BOQ_IMPORT_DB`.

**I-S222-4: Hospital geometry anomaly** — SPEC ONLY
Known web-ifc 0.0.77 limitation with `USE_FAST_BOOLS`. No code fix possible — wait for web-ifc update.

**I-S222-5: Open button spinner feedback** — `landing2.html`
`openImported(key, btnEl)` now receives button element. Sets "Opening..." + disabled immediately, restores after viewer tab opens. Handles error paths too.

### Diff Viewer WIRED

**Landing page** (`landing2.html`):
- `openImported()` now detects variations by filename prefix
- If base version exists, caches base DB into `bim_ootb_cache` IndexedDB
- Passes `?diffdb=import://base/extracted` URL param to viewer

**Viewer** (`main.js`):
- After `APP.init()`, checks for `?diffdb=` URL param
- Loads diff DB via `cachedFetch` (handles `import://` via IndexedDB)
- Creates `APP.diffDb` sql.js instance
- Calls `computeDiff()` immediately
- Polls every 2s for ≥10 meshes (up to 30s), then applies overlay + summary panel
- Log tags: `§DIFF_DB_LOADED`, `§DIFF_OVERLAY_READY`

### Test Harness Extended (s220_test.js)

Added S223 diff + VO cost engine tests:
- Creates in-memory variation of SampleHouse: 3 CHANGED (name modified), 2 REMOVED, 1 ADDED (fake IfcBeam)
- Validates diff detection: added=1, removed=2, changed=3
- Validates VO cost engine: addCost=680 (IfcBeam×1.0), remCost=300, chgCost=1950
- Validates O&P: totalImpact=3846 > totalDirect=2930
- Validates USD conversion: $808

**Result:** 65 PASS / 0 FAIL (58 S220 + 7 S223)

### Debug Log Tags Added
| Tag | File | What |
|-----|------|------|
| `§BOQ_IMPORT_DB` | boq_charts.html | DB loaded from IndexedDB for import:// URL |
| `§DIFF_WIRED` | landing2.html | Base version detected, diffdb param set |
| `§DIFF_DB_LOADED` | main.js | Diff DB loaded in viewer |
| `§DIFF_OVERLAY_READY` | main.js | Overlay applied after mesh streaming |

### Files Modified
| File | Change |
|------|--------|
| `deploy/landing2.html` | I-S222-1 auto-clear, I-S222-2 prefix match, I-S222-5 spinner, diff wiring |
| `deploy/dev/tools.js` | I-S222-3 import:// URL handling |
| `deploy/dev/boq_charts.html` | I-S222-3 fetchDbBuffer() IndexedDB lookup |
| `deploy/dev/main.js` | Diff DB loading, overlay timing |
| `deploy/dev/s220_test.js` | S223 diff + VO tests (7 new) |

### S223-S224 Implementation Done (2026-04-24)
All items below implemented and deployed to OCI DEV. Committed as `fe1b0029`.
See §S224 Feature Review for full spec. See §S225 for next session.

---

## S224 Feature Review — Merge/New UX + Version Cards

### Design Principle
IndexedDB IS the project. The card IS the history. No files on disk unless user explicitly exports.

### Import Flow (second import onward)

```
User drops IFC
  └─ Any cards already exist?
     ├─ NO  → save to IndexedDB → new card appears. Done.
     └─ YES → prompt:
              ┌─────────────────────────────────────────┐
              │  Merge with [Card Name] or New building? │
              │                                          │
              │     [ Merge ]        [ New ]             │
              └─────────────────────────────────────────┘
              (If multiple cards: dropdown to pick which one)

        Merge → stored as version (v2, v3...) under same card
        New   → separate card, separate building
```

### Card States

**Single version (first import):**
```
┌──────────────────────────┐
│ ProjectName              │
│ 1,234 elements · ARC     │
│ ▓▓▓▓▓░░ discipline bars  │
│ [ Open ] [ Export DB ] [x]│
└──────────────────────────┘
```

**Multiple versions (after merge):**
```
┌──────────────────────────┐
│ ProjectName          v3  │
│ 6,814 elements · ARC MEP │
│ ▓▓▓▓▓▓▓▓ discipline bars │
│ [ Open ] [ Compare ]     │
│ [ Export DB ]        [x] │
└──────────────────────────┘
```

- **Open** → opens latest version in viewer
- **Compare** → picker: "Compare v1 vs v2" / "v1 vs v3" / "v2 vs v3"
  → opens viewer with both DBs loaded → diff overlay + cost preview
  → "Export VO Excel" button in diff summary panel
- **Export DB** → downloads latest version as single `.db` file
- **x** → "Delete all N versions?" confirm → removes everything for this project
- **v3 badge** → shows version count, not clickable (history is implicit)

### IndexedDB Storage Model

```
bim_ootb_imports (object store: 'buildings')
  key: "ProjectName"
  value: {
    meta: { name, elementCount, disciplines, ... },
    versions: [
      { key: "Project_ARC.ifc",  importDate: "2026-04-24T...", db: ArrayBuffer },
      { key: "Project_MEP.ifc",  importDate: "2026-04-24T...", db: ArrayBuffer },
      { key: "Project_STR.ifc",  importDate: "2026-04-24T...", db: ArrayBuffer },
    ],
    latestVersion: 2  // index into versions[]
  }
```

- Each version is a self-contained single DB (metadata + geometry)
- `meta` reflects the latest import (element count, disciplines aggregated)
- Viewer loads `versions[latestVersion].db` for Open
- Compare loads any two `versions[].db` as `A.db` and `A.diffDb`

### Compare Flow (in viewer)

1. User clicks Compare on card → picks two versions (e.g. v1 vs v3)
   - 2 versions: auto-compares (no picker needed)
   - 3+ versions: picker modal with dropdowns
2. Viewer opens with `?db=import://Project/v0&diffdb=import://Project/v2`
3. `streaming.js` loads v1 (base), streams normally — **viewer is unchanged**
4. `main.js` loads v3 as `A.diffDb`, calls `computeDiff()`
5. After streaming completes → `applyDiffOverlay()`:
   - Green (added): elements in v3 not in v1
   - Red ghost (removed): elements in v1 not in v3
   - Yellow (changed): same GUID, different properties
6. Diff summary panel shows: counts + cost preview. No separate VO export button.
7. **Variance Order in 4D/5D** — the existing 📊 button passes `&diffdb=` to `boq_charts.html`.
   boq_charts detects variance, adds two sheets to the 5D Excel:
   - **Variation Order** — per-element: status, GUID, class, rate, factor, direct cost, total impact, days
   - **Variance Summary** — scope counts, FIDIC cost breakdown, schedule impact
   No separate VO export path. One button, one Excel, all sheets.

### Design Principle: Viewer Integrity
- **No diff, no change** — without `?diffdb=`, viewer behaves exactly as before
- **With diff** — overlay + summary appear, 4D/5D includes variance sheets
- No new buttons, no new panels, no new flows — existing UX absorbs the variance

### Merge Prompt — Implementation Notes

- Prompt appears as a modal overlay (not `window.confirm` — too ugly)
- Dark theme, matches landing page aesthetic
- If only one existing card → shows its name directly
- If multiple cards → dropdown: "Merge with: [▼ ProjectA | ProjectB | ProjectC]"
- Keyboard: Enter = Merge, Escape = New
- Mobile: full-width buttons, touch-friendly

### What This Replaces
- Old: variation detection by `meta.name` matching (I-S222-2) — unreliable
- Old: filename prefix stripping (`_ARC`, `_MEP`) — fragile
- New: user decides. No guessing. No naming convention.

### Test Plan
1. Import `Ifc4_Revit_ARC.ifc` → card "Ifc4_Revit_ARC" appears (not "Project")
2. Import `Ifc4_Revit_MEP.ifc` → prompt "Merge with Ifc4_Revit_ARC or New?"
3. Click Merge → card shows v2, discipline bars update (ARC + MEP)
4. Import `Ifc4_Revit_STR.ifc` → prompt → Merge → card shows v3
5. Click Compare → auto-compare v1 vs v2 → viewer: green MEP elements (all added), HUD shows diff
6. Click Compare on v3 card → picker → viewer: diff overlay + summary panel
7. In diff viewer → click 📊 4D/5D → boq_charts opens → info bar shows VARIANCE counts
8. In boq_charts → Save 5D → Excel has extra sheets: "Variation Order" + "Variance Summary"
9. Click New instead → separate card, no merge
10. Delete card → confirm → all versions gone
11. Save button → downloads latest version as `.db` file

### Debug Log Tags (planned)
| Tag | Where | What |
|-----|-------|------|
| `§MERGE_PROMPT` | landing2.html | Prompt shown, existing card name |
| `§MERGE_ACCEPT` | landing2.html | User chose Merge, version number |
| `§MERGE_REJECT` | landing2.html | User chose New |
| `§COMPARE_OPEN` | landing2.html | Which two versions selected |
| `§VERSION_DELETE` | landing2.html | All versions deleted for project |
| `§EXPORT_DB` | landing2.html | Single DB exported, size |
| `§OPEN_DIFF` | landing2.html | Open auto-includes diff for multi-version |
| `§BOQ_DIFF` | boq_charts.html | Variance detected, counts logged |
| `§VO_IN_5D` | boq_charts.html | VO sheets added to 5D Excel |

---

## S225 — Next Session

### ⚠ DO NOT REMOVE
Scope: Fix variance detection end-to-end, test with real diff data.
Read the log after every run.

### Context
S222-S224 built the full import→merge→compare→4D/5D pipeline. Deployed to DEV.
Charts are fixed (perfect circles, visible axes). Merge/New modal works. Cards versioned.
But **variance is not flowing end-to-end** — the 5D Excel does not include VO sheets.

### Architecture (read this first)
- **Single DB per import** — no separate library.db. Each IFC import produces ONE `.db` with metadata + geometry. `library.db` is enterprise-only (server-side component library) — does NOT exist in browser DIY flow.
- **Versioned IndexedDB** — `bim_ootb_imports` v2. Each project key stores `{meta, versions[], latestVersion}`. Each version is a self-contained single DB (ArrayBuffer).
- **Merge = version append, NOT data merge** — merging adds a new version entry. The two DBs stay separate. This is correct for variance detection (need two distinct DBs to diff).
- **Diff chain**: landing `openProject()` → caches latest + previous version in `bim_ootb_cache` → viewer opens with `?db=...&diffdb=...` → `main.js` loads diffDb → `computeDiff()` → `applyDiffOverlay()` → user clicks 📊 → `tools.js` passes `?diffdb=` to `boq_charts.html` → boq_charts loads both DBs → adds VO sheets to 5D Excel.
- **Viewer integrity** — without `?diffdb=`, viewer is 100% unchanged. With it, overlay + summary appear. No new buttons, no new panels.

### Open Issue: Variance Not Reaching 5D Excel
The F12 log from user's test shows `§BOQ_IMPORT_DB` but NO `§BOQ_DIFF` — meaning boq_charts opened without `?diffdb=` in the URL. Trace the full chain:

1. **Does `openProject()` set `diffParam`?** Check that multi-version detection works — `record.versions.length > 1` and `prevIdx !== record.latestVersion`. Add `console.log` if missing.
2. **Does the viewer URL contain `?diffdb=`?** Check browser address bar after Open on a v2 card. If missing, `openProject()` isn't passing it.
3. **Does `tools.js export4D5D()` forward `?diffdb=`?** Check `new URLSearchParams(location.search).get('diffdb')` — if viewer URL doesn't have it, this returns null.
4. **Does boq_charts receive `?diffdb=`?** Check its URL in browser tab. If missing, chain broke upstream.

### Open Issue: "Open" Shows Only Latest Version
Current behavior: Open on a merged card loads `versions[latestVersion]` only. If user merged WallElementedCase (10 el) into SampleHouse (65 el), Open shows just the wall — because latestVersion points to WallElementedCase.

This may be correct (Open = view latest revision) or confusing (user expects combined building). Clarify with user before changing.

### Open Issue: No library.db in DIY Flow
The `import_db_builder.js` comment says "Enterprise setup: For centralised library...". The browser import ALWAYS produces a single DB. The viewer's `A.libDb = A.db` fallback handles this. BUT:
- `openProject()` sets `libUrl = dbUrl` (correct — same single DB)
- `openImported()` in `import.js` still writes separate `import://key/extracted` and `import://key/library` cache keys — both point to the same buffer, but the naming is legacy. Not a bug, just confusing.
- **Rule**: Never reference `library.db` as a separate file in the DIY/browser flow. It does not exist. Single DB only.

### DO
- Read F12 console logs — every `§` tag tells you where the chain is
- Test with SampleHouse (65 el, IFC4, proven) — `reference/residential/Ifc4_SampleHouse.ifc`
- Trace the full `?diffdb=` chain from landing → viewer → boq_charts before changing code
- Check `boq_charts.html` URL bar — if `diffdb` param is missing, fix upstream

### DON'T
- Don't invent test IFCs or fabricate data — use real files from `reference/`
- Don't add a separate VO export button — variance goes through 4D/5D only
- Don't create library.db in the browser flow — single DB only
- Don't change viewer behavior for non-diff cases — viewer integrity is sacred
- Don't touch deploy/sandbox/ (production) — all work in deploy/dev/ only

### Files
| File | What |
|------|------|
| `deploy/landing2.html` | Merge/New modal, versioned cards, openProject(), Compare |
| `deploy/dev/import_db_builder.js` | Single DB builder (no library.db), building name from filename |
| `deploy/dev/import.js` | Viewer-side import (IDB v2, versioned openImported) |
| `deploy/dev/main.js` | Diff DB loading from ?diffdb= param, overlay timing |
| `deploy/dev/diff.js` | computeDiff(), applyDiffOverlay(), showDiffSummary() |
| `deploy/dev/tools.js` | export4D5D() forwards ?diffdb= to boq_charts |
| `deploy/dev/boq_charts.html` | fetchDbBuffer for import://, diff loading, VO sheets in save5D |
| `deploy/dev/variation_order.js` | Cost engine (still loaded but VO button removed from diff panel) |
| `deploy/dev/s220_test.js` | 65 tests (schema + diff + VO cost math) |

### Log Tag Trace (expected full chain)
```
Landing:  §IMPORT_SAVED → §MERGE_PROMPT → §MERGE_ACCEPT → §OPEN_DIFF
Viewer:   §DIFF_DB_LOADED → §DIFF → §DIFF_OVERLAY_READY → §DIFF_SUMMARY
4D/5D:    §BOQ_IMPORT_DB → §BOQ_DIFF → §VO_IN_5D (on Save 5D click)
```
If any tag is missing, the chain broke at that point. Fix there.

## S226 — Next Session

### ⚠ DO NOT REMOVE
Scope: Refactor Excel chart capture in boq_charts.html. Excel output only — DO NOT TOUCH HTML page rendering.
Read the log after every run.

### What's done (S225)
- Variance detection end-to-end working (diff direction, added element rendering, clickable variance panel)
- 5D Excel: Charts sheet removed, charts in Executive Summary only
- `captureChartImage()` composites onto white background (offscreen canvas) — pie=400×400, bar=500×318
- `prepareChartsForExcel()` / `restoreChartsAfterExcel()` — shared prepare/restore, replaces duplicated code in save5D/save4D
- Pre-capture: black labels (#000) on bar/line axes ONLY (`!isPie` guard), restore after save
- Fixed: save4D was applying legend font to pie charts (missing `!isPie` guard) — caused squished pie
- `§CHART_CAPTURE` log line with type, canvas size, image size for each captured chart
- Deployed to dev OCI bucket 2026-04-24

### S226: Localisation (_TRL)
Full iDempiere-style `_TRL` locale system in `boq_charts.html`. Zero hardcoded strings.

**What _TRL controls:**
- Currency: `cur`, `cur2`, `cur_rate`, `cur_name`, `cur2_name`
- Rate attribution: `rate_source`, `rate_mat_source`, `rate_lab_source`, `rate_eq_source` (+ ref, basis, etc.)
- Column headers: `h_material`, `h_labour`, `h_equipment`, `h_discipline`, `h_storey`, etc. (22 keys)
- 4D labels: `h_wbs`, `h_task_name`, `h_start_date`, etc. (14 keys)
- Chart titles: `t_cost_by_disc`, `t_gantt`, `t_milestone`, etc. (10 keys)
- Excel sheet names: `s_cover`, `s_exec_summary`, `s_material`, etc. (10 keys)
- Section titles: `t_comp_boq`, `t_mat_summary`, etc. (11 keys)
- Misc: `not_started`, `source_app`

**Override mechanisms (in priority order):**
1. URL params: `?cur=USD&rate=4.45&h_labour=Labor` (any _TRL key)
2. Locale file: `locales/{lang}.js` (partial override object)
3. `_TRL_DEFAULTS` (en_GB base, always present)

**_TRL is a project locale** — not just language. Same language, different rate books:
- `en_MY` = English + CIDB rates + RM
- `en_AU` = English + Rawlinsons + AUD
- `en_US` = English + RS Means + USD

**Self-verifying (auto-downloaded .log):**
- `§TRL_VERIFY` — TRL_COMPLETE, TRL_CUR_MATCH, TRL_SHEET_NAMES, TRL_CHART_TITLES, TRL_HTML_CUR, TRL_RATE_SOURCE, TRL_NO_HARDCODE
- `§MATHS_VERIFY` — MATHS_MAT_SUM, MATHS_LAB_SUM, MATHS_EQ_SUM, MATHS_GRAND, MATHS_CUR_CONV, MATHS_QTY_SUM, MATHS_RATE_CHECK, MATHS_PIE_SUM

**Full spec:** `prompts/S226_localisation.md`

### S226 Phase 0 DONE: Rate extraction + 10 locale files
**Completed by follow-up session:**
- `deploy/dev/rates.js` — single source of truth for RATES, LABOR_RATES, EQUIPMENT_RATES, EQUIPMENT_ALLOCATION, SEQUENCE_RULES, DISC_COLORS, PHASE_COLORS, WORK_PACKAGES, calcLabor(), calcEquipment(), getRate(), getPhase(), getProductivity()
- `boq_charts.html` — removed ~150 lines of duplicated constants, loads `rates.js`
- `variation_order.js` — removed VO_RATES/VO_PHASES/VO_PRODUCTIVITY (65 lines), uses shared rates.js
- `nlp.js` — removed COST_RATES, uses shared getRate()
- `index.html` — loads `rates.js` before nlp.js/diff.js/variation_order.js
- 10 locale files in `deploy/dev/locales/`: en_MY (base), en_US, en_GB, en_AU, ms_MY, de_DE, fr_FR, es_ES, zh_CN, th_TH
- Each locale = FULL package: labels + currency + rates + labor + equipment (iDempiere AD_Window_Trl pattern)
- en_US has RS Means USD rates (not just label swap — different cost book entirely)

**Backward compatibility review needed:**
- VO Impact chart (boq_charts.html lines 725-768) uses RATES from rates.js — verified intact
- variation_order.js now shares rates.js instead of its own VO_RATES — must verify Excel VO output unchanged
- nlp.js uses getRate() from rates.js instead of COST_RATES — must verify voice cost queries work
- **Review session:** Load `?diffdb=` with two DBs, click 5D, verify VO chart + VO Excel sheet + cost values match prior output
- Full review spec: `prompts/S226_localisation.md` §Phase 0

**Still open (chart quality):**
- PIE_ROUND: canvas 1022×533 not resizing to 800×800 — `ch.resize()` alone doesn't work when responsive
- LABELS_DARK: 0.5% threshold too tight — bar[1] borderline FAIL
- RATIO_MATCH: follows from PIE_ROUND fix
- See `prompts/S226_localisation.md` §Prior Session Issues

**Next:** locale_loader.js, flag selector on landing page, Phase 2-5 per S226 spec
