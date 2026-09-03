# S220 — Browser IFC Import (Self-Service Onboarding)

## DO NOT REMOVE
Scope: Drag-and-drop IFC file → extract to DB entirely in browser → view instantly.
Read the log after every run. No server, no Python, no install.

## Status: IN PROGRESS — WASM loading fixed, schema aligned, testing on dev

## Pitch
**"IFC to 5D in 60 seconds. On your phone. Zero install."**
No other tool does this — not Autodesk, not Trimble, not open source.

## What It Does
User picks an IFC file → browser parses it → extracts to two sql.js DBs (same schema as Python pipeline) → card appears on landing page → tap to view. Everything works: 3D, NLP, walk, sitecam, BOQ, issues.

## Landing Page Placement

```
┌─────────────────────────────────┐
│        BIM OOTB header          │
│     stats bar                   │
├─────────────────────────────────┤
│  ┌───────────────────────────┐  │
│  │  📂 Import IFC            │  │  ← one button (phone: file picker)
│  │  Any IFC. <50MB instant.  │  │     desktop: drag-drop zone + button
│  └───────────────────────────┘  │
├─────────────────────────────────┤
│  MY BUILDINGS (imported)        │  ← user's own IFCs, IndexedDB
│  [House.ifc — 2,340 elements]  │     same card style as demo buildings
├─────────────────────────────────┤
│  City Buildings (existing)      │
│  Landmark Buildings (existing)  │
└─────────────────────────────────┘
```

- Import zone sits ABOVE demo buildings — first thing users see
- "My Buildings" section only appears if user has imported buildings
- Cards identical to demo cards (name, element count, discipline bars)
- Per-card delete button (x) to remove from IndexedDB

## User Flow

### Phone
1. Tap `📂 Import IFC`
2. File picker opens (downloads, email attachments, WhatsApp files)
3. Progress bar: `Parsing IFC (30%) → Extracting elements (50%) → Tessellating (20%)`
4. Card appears in "My Buildings"
5. Tap card → viewer opens

### Desktop
1. Drag IFC onto drop zone (or click to browse)
2. Same progress bar
3. Same card → same viewer

### Repeat Visit
- Landing page loads → "My Buildings" cards appear from IndexedDB
- No re-import needed. Tap → view instantly (from cache)

## Technical Architecture

### Parser: web-ifc (WASM)
- `web-ifc` — MIT licensed, ~2MB CDN, WebAssembly IFC parser
- Supports IFC2x3 (Revit, ArchiCAD), IFC4 (Bonsai, Tekla), IFC4x3
- Runs in Web Worker (no UI freeze)
- CDN: `https://unpkg.com/web-ifc@0.0.57/web-ifc-api-browser.js` (pin version)

### Extraction Pipeline (mirrors Python `extractIFCtoDB_open.py`)
```
File → ArrayBuffer → web-ifc parse → walk spatial tree → populate sql.js DB
```

Tables to populate (same schema as Python pipeline):
1. `project_metadata` — project name, schema version, export date
2. `spatial_structure` — Site → Building → Storeys → Spaces
3. `elements_meta` — guid, ifc_class, element_name, storey, discipline, material_name
4. `element_transforms` — placement matrix per element (4x4 or position+rotation)
5. `base_geometries` — tessellated BLOBs (vertices Float32Array, faces Int32Array)
6. `surface_styles` — RGB colour per element/material
7. `material_layers` — material assignments
8. `simple_qto` — quantities (area, volume, length) from IfcElementQuantity

### Discipline Classification
Map IFC class to discipline (same logic as Python pipeline):
- ARC: IfcWall, IfcSlab, IfcDoor, IfcWindow, IfcRoof, IfcStair, IfcRailing, IfcCovering
- STR: IfcBeam, IfcColumn, IfcFooting, IfcPile, IfcMember
- MEP: IfcFlowSegment, IfcFlowTerminal, IfcFlowFitting, IfcDistributionElement
- ELEC: IfcCableSegment, IfcElectricAppliance, IfcLightFixture, IfcOutlet
- PLB: IfcPipeSegment, IfcPipeFitting, IfcSanitaryTerminal, IfcValve
- ACMV: IfcDuctSegment, IfcDuctFitting, IfcAirTerminal, IfcUnitaryEquipment
- FP: IfcFireSuppressionTerminal, IfcAlarm

### Output: Two DBs (same as Python pipeline)
- `{name}_extracted.db` — metadata, transforms, spatial structure, quantities
- `{name}_library.db` — geometry BLOBs (vertices + faces per element)
- Both saved to IndexedDB keyed by filename
- Viewer loads from IndexedDB — same path as demo buildings after first download

### Tessellation
- `web-ifc` provides `GetFlatMesh()` → returns triangulated vertices + indices
- Convert to Float32Array (vertices) + Int32Array (faces) → store as BLOBs in `base_geometries`
- Same format as Python `ifcopenshell.geom.create_shape()` output
- Coordinate system: IFC (X=east, Y=north, Z=up) — viewer handles conversion

## File Size Handling

| Size | Elements | Phone | Desktop | UX |
|------|----------|-------|---------|-----|
| <10MB | <5K | <10s | <5s | No warning |
| 10-50MB | 5-50K | 30-60s | <15s | No warning |
| 50-200MB | 50-200K | 2-5min | 30-60s | Toast: "Large file — may take a few minutes" |
| 200MB+ | 200K+ | 5-10min | 1-3min | Toast: "Very large — best on desktop" (still allows) |

## Files to Create/Change

| File | What |
|------|------|
| `deploy/dev/import.js` | New module — web-ifc parsing, extraction, IndexedDB storage |
| `deploy/dev/index.html` | Add import button, "My Buildings" section, script tag |
| `deploy/dev/main.js` | Wire `setupImport(APP)` |
| `deploy/dev/landing2.html` | Add import button + "My Buildings" to dev landing |
| `deploy/sandbox/landing.html` | Add import button + "My Buildings" to prod landing (after promote) |

## What NOT to Change
- `streaming.js` — already loads from sql.js DB, no change needed
- `config.js` — already reads DB/LIB URLs from params
- Any other existing module — import is additive, not invasive
- Demo buildings — import zone is separate section, doesn't touch existing grid

## Web Worker Structure
```
main thread                          worker thread
─────────────                        ─────────────
import.js                            import_worker.js
  ├─ file picker / drop              ├─ load web-ifc WASM
  ├─ postMessage(arrayBuffer) ──→    ├─ parse IFC
  ├─ onmessage(progress%) ←──       ├─ walk spatial tree
  ├─ update progress bar             ├─ extract elements
  ├─ onmessage(result) ←──          ├─ tessellate geometry
  ├─ save to IndexedDB               ├─ postMessage({extracted, library})
  └─ create card, open viewer        └─ done
```

## IndexedDB Schema
```
Database: bim-ootb-imports
  Store: buildings
    Key: filename (e.g. "MyHouse.ifc")
    Value: {
      name: "MyHouse",
      filename: "MyHouse.ifc",
      importDate: "2026-04-23T15:00:00Z",
      elementCount: 2340,
      disciplines: { ARC: 1200, STR: 800, MEP: 340 },
      extractedDb: ArrayBuffer,   // the full extracted DB
      libraryDb: ArrayBuffer,     // the full library DB
      fileSize: 12400000          // original IFC file size
    }
```

## Test Plan
- `deploy/dev/s220_test.js` — offline tests:
  - Syntax check import.js
  - Wiring: index.html has import button, main.js calls setupImport
  - Import button exists in landing page
  - "My Buildings" section renders when IndexedDB has entries
  - Discipline classification map covers all common IFC classes
  - File size warning thresholds correct
  - No eval(), no innerHTML from user input (security)

- Manual tests:
  - Import SampleHouse.ifc (<5MB) on phone — should complete in <15s
  - Import Hospital.ifc (~80MB) on desktop — should complete in <60s
  - Close browser, reopen landing → imported building card still there
  - Tap card → viewer opens, all features work (NLP, walk, BOQ)
  - Delete card → removed from IndexedDB, disappears from landing

## Exit Criterion
User drops any valid IFC, gets a viewable DB with working 3D + BOQ in <60s for <50MB files. On phone. No install. No account. No server.

## Implementation Learnings (this session)

### WASM Loading
- web-ifc IIFE auto-resolves `web-ifc.wasm` relative to `self.location.href` in Web Workers
- When worker is hosted on OCI, it fetches `bim-ootb-dev/sandbox/web-ifc.wasm` → 404
- Fix: host `web-ifc.wasm` on OCI bucket with `--content-type application/wasm`
- `Module.locateFile` global override does NOT work — IIFE scopes its own Module
- `Init(customLocateFileHandler)` works for the Init-time fetch but importScripts triggers an earlier fetch
- See `prompts/S220_wasm_mime_fix.md` for full diagnosis

### Schema Alignment
The viewer (streaming.js) expects a specific DB schema. Import must produce exactly this:

**extracted DB:**
- `elements_meta`: guid, ifc_class, element_name, storey, discipline, material_name, material_rgba, building
- `element_transforms`: guid, center_x, center_y, center_z, rotation_x, rotation_y, rotation_z
- `element_instances`: guid, geometry_hash (viewer joins this to get geometry)
- `project_metadata`: key, value

**library DB:**
- `component_geometries`: geometry_hash, vertices (BLOB), faces (BLOB), building

Key differences from naive schema:
- Transforms are center+rotation, NOT 4x4 matrix (center = matrix columns 12,13,14)
- Geometry is keyed by `geometry_hash`, not guid (allows instancing)
- `element_instances` is the join table between elements and geometry
- `material_rgba` column required even if null

### web-ifc Version
- v0.0.66 → v0.0.77 (latest as of 2026-04)
- Supports: IFC2x3, IFC4, IFC4X3
- Does NOT support: IFC4X4 (draft standard, no tools support it yet)
- Graceful error on unsupported schema → shows user-friendly message

### Debug Log Tags
- `§WORKER_START` / `§WORKER_LOADED` — worker lifecycle
- `§WASM_INIT` — WASM initialization
- `§WASM_LOCATE` — path resolution
- `§PARSE_START` / `§PARSE_OK` / `§PARSE_FAIL` — IFC parsing
- `§EXTRACT_START` — line count
- `§ELEMENTS_FOUND` — element + storey count
- `§GEOM_DONE` — geometry count + skipped
- `§DB_BUILD` — DB creation stats
- `§IMPORT_FATAL` / `§IMPORT_STACK` — error details

### Files Created
| File | Purpose |
|------|---------|
| `deploy/dev/import.js` | Import module for viewer (setupImport) |
| `deploy/dev/import_worker.js` | Web Worker: web-ifc parse + extract |
| `deploy/landing2.html` | Import zone + My Buildings + inline DB builder |
| `prompts/S220_wasm_mime_fix.md` | WASM MIME diagnosis |
| `web-ifc.wasm` on OCI dev bucket | WASM binary with correct MIME |

## Dependencies
- `web-ifc` WASM (CDN, MIT license, pinned version)
- `sql.js` (already loaded by viewer)
- IndexedDB (already used for building cache)
- No new server, no new bucket, no new backend

## WARNINGS
- DO NOT change existing modules (streaming, config, scene, etc.)
- DO NOT add server-side processing — everything runs in browser
- DO NOT store user's IFC files on OCI — privacy, they stay in browser only
- Pin web-ifc version — breaking changes between versions
- Test on Safari iOS (WebAssembly + Web Worker quirks)
