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
| 5D Rates | `templates/5D_rates.json` | nD engine + VO Excel | Material rates per IFC class, labour, equipment |
| 4D Phases | `templates/4D_phases.json` | nD engine + VO Excel | Phase sequence, productivity (elements/day), predecessors |
| nD Formulas | `templates/nD_formulas.json` | nD engine | Measurement rules, cost/schedule/carbon formulas |
| VO Config | `variation_order.js` §VO_CONFIG | VO Excel | Addition/removal/change factors, overhead%, markup%, disruption% |
| Inline Rates | `variation_order.js` §VO_RATES | VO Excel (browser) | Mirrors 5D_rates.json for browser use (no server) |
| NLP Rates | `nlp.js` §COST_RATES | Voice/text cost queries | Same rates, used by "total cost" / "cost of beams" voice commands |

**For localization:** Edit `5D_rates.json` currency/exchange_multiplier. VO_CONFIG.currency and usdRate in `variation_order.js`. Rates stay in base currency, output multiplied.

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
