# BIM OOTB — Technical Feature Paper

## What It Is

BIM OOTB is a browser-based IFC viewer that runs entirely on the client. No server. No cloud subscription. No software to install. Open a URL, drop an IFC file, and view a full BIM model with construction scheduling, cost breakdown, and clash detection — all in a single browser tab.

The viewer handles buildings from 200 to 122,000+ elements with smooth orbit, pick, and section cut. After first load, it works offline from local cache. The entire application is under 500KB of code.

## How It Works

### IFC In, Building Out

An IFC file is parsed once into a SQLite database. Geometry is content-hashed and deduplicated — identical columns, beams, or fittings share one mesh. The database is cached locally in the browser's IndexedDB. On return visits, the building loads from cache in under one second.

### Split-Database Streaming

For large buildings (15,000+ elements), the database is split into three files:

| File | Size (Terminal, 48K elements) | Loads in |
|------|------|----------|
| Positions (bbox centres) | 1.1 MB | <1 second |
| Metadata (properties, transforms) | 17 MB | 1-2 seconds |
| Geometry (mesh BLOBs) | 249 MB | Background stream |

The viewer shows the building outline instantly from positions, populates storey/discipline panels from metadata, and streams real geometry in the background sorted by camera distance — nearest elements appear first. You can orbit, pick, and filter while geometry is still loading.

### Rendering Pipeline

The viewer uses Three.js r160 with physically-based rendering (PBR):

- **Geometry instancing**: Elements sharing the same shape are rendered as a single GPU draw call via `BatchedMesh`. A 122K-element building produces roughly 15,000 draw calls instead of 122,000.
- **IFC-faithful materials**: Colours are extracted from the IFC's own `IfcSurfaceStyle` definitions. Where the IFC author assigned no colour (common in Revit exports), a class-based palette provides physically plausible defaults — concrete is warm grey, steel is reflective, glass is smooth and blue-tinted.
- **Per-class PBR properties**: Roughness and metalness vary by IFC class. Structural steel (IfcBeam, IfcColumn) is smooth and reflective. Concrete (IfcSlab, IfcWall) is rough and matte. Glass (IfcWindow, IfcCurtainWall) is near-mirror.
- **Cinematic tone mapping**: ACESFilmic with tuned exposure. No washed-out greys.

### Visibility Culling (DLOD)

For buildings above 100,000 elements, a frustum-based visibility system hides geometry that is behind the camera. This is pure visibility toggling — no geometry is simplified or replaced with proxies. What you see is exactly what is in the IFC file.

## Feature Overview

### Viewing
- Orbit, pan, zoom with mouse or touch
- Pick any element to see IFC properties (class, name, storey, material, dimensions)
- X-ray mode (transparent) and wireframe mode
- Section cut at any angle
- Night mode with adjustable ambient lighting
- Storey filter and discipline filter (Architecture, Structure, MEP, Electrical, Plumbing, ACMV)
- Colour Palette panel — 5 sliders for exposure, sun intensity, ambient, hemisphere, and tone mapping
- Walk mode with GPS blue-dot tracking, device orientation (accelerometer/gyroscope), wall X-ray on approach

### IFC Import and Export
- Drop any IFC file into the browser — parsed into SQLite, cached locally, no upload
- **IFC export**: reconstruct a valid IFC STEP file from the SQLite database, download directly from the browser. No server, no web-ifc dependency — pure STEP/ISO-10303-21 text generation
- 4D Excel export: 3-sheet workbook (schedule, project summary, discipline/phase breakdown)
- 5D BOQ Excel export: 7-sheet workbook (cover, executive summary, material/labour/equipment, BOQ, work packages, discipline breakdown, provisions)

### 2D
- Section cut views
- Elevation generation
- Grid overlay with drag-to-recompile
- Door arc generation from IFC openings
- Dimension chains
- Doc Canvas: AABBCC lettered/numbered grid derived from column cadence, with circle bubbles and dimension labels

### 4D Construction Scheduling
- Time Machine: scrub through construction phases on a timeline
- Elements appear in construction sequence with frontier highlighting
- Cinematic drone tour auto-generated from building storeys
- Gantt chart overlay with phase progress
- Gantt Phase Stepper: step through construction phases one at a time, meshes materialise per phase

### 5D Cost
- Bill of Quantities extracted from IFC element dimensions — browser-side JavaScript BOM extractor groups elements by storey, discipline, and IFC class
- Cost dashboard with phase-by-phase breakdown
- 17 country-specific rate templates
- Building envelope, storey heights, floor-to-floor deltas, column cadence and bay proportions computed automatically

### Clash Detection
- R-tree spatial index built from element bounding boxes
- Configurable clash rules (tolerances per discipline pair)
- Two-point distance measurement tool
- Snag reporting with QR codes that deep-link back to the clashing elements

### Sharing
- Share a URL — recipient sees the exact camera angle, picked element, storey filter, x-ray state, clash pair, Time Machine cursor, and tour state
- Native OS share sheet on mobile (Web Share API)
- Share preview card on desktop with canvas snapshot
- QR code generation for on-site access
- No account required to view

### Doc Pill (Design Interface)
- Red Pill icon toggles a 9-icon document design interface
- Contains: Home, Grid, Time Machine, Phase Stepper, Open, Save, MEP Routes, UBBL Compliance Checklist, Rosetta Stone Calibration
- Automatic BOM extraction on entry
- Rosetta Stone calibration mode: drag grid to align with real building geometry for verification

### Search and Navigation
- Find panel with inline voice search (microphone in search bar)
- NLP natural-language queries ("show me all walls on level 2")
- Context-aware filter chips auto-generated from loaded building's top IFC classes
- Help Tree: 6 expandable entries (Time Machine, Find, Section, Clash, Palette, Issues) with blue/red bar toggle

## Performance

Measured on a standard laptop (Intel i7, integrated GPU, Chrome):

| Building | Elements | Geometry DB | Cache Load | Draw Calls | Orbit FPS |
|----------|----------|-------------|------------|------------|-----------|
| SampleHouse | 218 | 0.4 MB | instant | 45 | 60 |
| FZKHaus | 620 | 1.2 MB | instant | 89 | 60 |
| Clinic (federated MEP) | 16,114 | 72 MB | <1s | ~1,800 | 60 |
| Terminal | 48,428 | 249 MB | <1s | ~2,000 | 45-60 |
| Hospital | 63,182 | 318 MB | <1s | ~3,500 | 30-45 |
| LTU A-House | 122,330 | 379 MB | <1s | ~15,000 | 20-30 |

All measurements with geometry cached in IndexedDB. First load depends on network speed for the initial database download.

## Spatial ERP — Construction Project Management in the Browser

BIM OOTB includes a browser-based ERP engine built on the same zero-infrastructure principle. The system renders iDempiere's Application Dictionary (AD) — the metadata-driven UI framework behind one of the most mature open-source ERPs — entirely in a browser using SQLite WASM.

### What This Means

iDempiere is a full enterprise ERP (accounting, procurement, manufacturing, HR) with a unique architecture: the entire UI is defined as database rows, not code. Change a row in `AD_Field` and the UI changes. No recompile. This Application Dictionary (60,000+ metadata rows across 10 tables) has been exported from PostgreSQL, loaded into SQLite, and parsed by a JavaScript renderer.

The result: the same menu tree, windows, tabs, fields, validation rules, and display logic that run on a JVM + PostgreSQL + OSGi stack — running in a browser tab with no server.

### Current State

| Capability | Status |
|---|---|
| AD menu tree (826 nodes) | Working |
| Window/Tab/Field rendering | Working |
| FK resolution (Name lookup) | Working |
| DisplayLogic (conditional fields) | Working |
| CRUD (Create/Read/Update/Delete) | Working |
| Multi-panel master-detail | Working |
| Data Globe (3D record visualisation) | Working |
| Role-based access | In progress |
| DocAction state machine | In progress |
| Construction POC (Land → BOQ → Approval) | In progress |

### Roadmap: Construction ERP vs Primavera / Procore

The construction ERP roadmap targets the workflow that Primavera P6, Procore, and SAP PS serve today — project scheduling, cost control, procurement, and progress tracking — but delivered as a browser-first application that lives alongside the 3D BIM model.

| Capability | BIM OOTB (Roadmap) | Primavera P6 | Procore | SAP PS |
|---|---|---|---|---|
| 4D Schedule + 3D Model | **Same browser tab** | Separate tools | Separate | Separate |
| 5D Cost + BOQ | **Extracted from IFC** | Manual input | Manual | Manual |
| Offline | **Yes** | No | No | No |
| Server required | **No** | Oracle DB | Cloud | SAP HANA |
| Licence cost | **Free (MIT)** | $3-8K/user/yr | $375+/mo | Enterprise contract |
| IFC integration | **Native** | None | Limited (viewer) | None |
| BOM from model | **Automatic** | N/A | N/A | Manual |
| Install | **URL** | Desktop | Cloud | On-premise/cloud |
| ERP foundation | **iDempiere AD (open)** | Proprietary | Proprietary | Proprietary |

The key differentiator: in Primavera, the schedule is disconnected from the model. An architect updates a wall in Revit, exports IFC, and someone manually reconciles the schedule. In BIM OOTB, the 4D schedule is derived from the IFC elements — change the model, the schedule updates. The 3D viewer, the Gantt chart, and the cost dashboard are the same application.

### iDempiere Graduation Path

BIM OOTB is not a replacement for enterprise ERP. It is a **browser-first entry point** that graduates to iDempiere when an organisation needs multi-user, multi-tenant, accounting, and audit. The same Application Dictionary runs in both environments. Data migrates up. Users don't retrain.

## Comparison

| Capability | BIM OOTB | Autodesk APS (Forge) | IFC.js / ThatOpen | Trimble Connect |
|---|---|---|---|---|
| Runs in browser | Yes | Yes | Yes | Yes |
| Server required | **No** | Yes (cloud) | No | Yes |
| Works offline | **Yes** | No | No | No |
| Install required | **None** | None | npm build | Desktop or web |
| IFC colour fidelity | **IfcSurfaceStyle extracted** | Proprietary conversion | WASM parse | Proprietary |
| IFC export from browser | **Yes (STEP format)** | No | No | No |
| Max elements (smooth) | **125K** | ~50K (server tiles) | ~20K | Server-rendered |
| Load from cache | **<1 second** | N/A (cloud) | N/A | N/A |
| 4D scheduling | **Integrated** | Separate product | No | Separate |
| 5D cost | **Integrated** | Separate product | No | Separate |
| Clash detection | **Integrated** | Separate (Navisworks) | No | Limited |
| BOM extraction | **Automatic from IFC** | Manual | No | No |
| Design interface (2D grids) | **Doc Pill + AABBCC grid** | Separate tools | No | No |
| Voice search | **Integrated (NLP + mic)** | No | No | No |
| Walk mode + GPS | **Integrated** | No | No | No |
| Cost | **Free** | Metered API | Free (library) | Licensed |
| Vendor lock-in | **None (IFC standard)** | SVF/SVF2 proprietary | IFC standard | Proprietary |
| ERP integration | **Built-in (AD engine)** | None | None | None |
| Construction scheduling | **4D in same viewer** | None | None | None |

### Key Differences

**vs Autodesk APS**: Autodesk requires a cloud pipeline — IFC uploads to their server, converts to a proprietary format (SVF2), and streams pre-processed tiles back. This takes minutes per model, costs per API call, and cannot function offline. BIM OOTB processes IFC once into SQLite and runs entirely from local cache thereafter.

**vs IFC.js / ThatOpen Company**: IFC.js parses IFC files in the browser using a WASM module at load time. This works for small models but becomes slow above 20K elements because it re-parses geometry on every page load. BIM OOTB pre-extracts geometry into a SQLite database with content-hash deduplication, so repeat visits load from cache without re-parsing.

**vs Desktop viewers (Solibri, BIMvision, Navisworks)**: Desktop applications require installation, licensing, and specific operating systems. BIM OOTB runs on any device with a browser — including tablets on a construction site with no internet after the initial cache.

## Architecture

```
IFC File
  ↓  (extract once)
SQLite Database (.db)
  ├── elements_meta      — guid, class, name, storey, discipline, material_rgba
  ├── element_transforms  — position (cx, cy, cz), rotation, bbox
  ├── element_instances   — geometry_hash (content-addressed dedup)
  └── component_geometries — vertex/index/normal BLOBs
  ↓  (browser loads from URL or cache)
sql.js (SQLite over WASM)
  ↓  (query → BufferGeometry)
Three.js r160
  ├── InstancedMesh    — elements sharing geometry (2+ instances)
  ├── BatchedMesh      — single-instance elements grouped by bucket
  └── MeshStandardMaterial — PBR with per-class roughness/metalness
  ↓
WebGL → Screen
```

No server in the loop. No WebSocket. No REST API. The browser is the entire application.

## Technology Stack

| Layer | Technology | Role |
|-------|-----------|------|
| Rendering | Three.js r160 ESM | WebGL, BatchedMesh, InstancedMesh, PBR |
| Database | sql.js (SQLite WASM) | Query engine in browser |
| Spatial index | rtree-sql.js 1.7.0 | R-tree for clash detection |
| Caching | Service Worker + IndexedDB | Offline-first, cache-first |
| BVH | three-mesh-bvh 0.7.8 | Accelerated raycasting for pick |
| Tone mapping | ACESFilmic | Cinematic colour grading |
| Export (Excel) | SheetJS | 4D schedule + 5D BOQ multi-sheet workbooks |
| Export (IFC) | Custom STEP writer | IFC STEP/ISO-10303-21 generation from SQLite |
| Voice | Web Speech API | NLP voice commands + Find search |
| Location | Geolocation + DeviceOrientation | Walk mode GPS + accelerometer |
| Share | Web Share API + Canvas | Native share sheet + preview snapshot |
| ERP | iDempiere AD + sql.js | Application Dictionary renderer in browser |

All open-source dependencies. No proprietary components.

## Catalogue

30 buildings from public IFC test files, ranging from a 65-element sample house to a 125,698-element university campus (LTU A-House, Sweden). Includes residential, commercial, hospital, airport terminal, and federated MEP models.

## Contact

Open source. MIT licence.
GitHub: [github.com/red1oon/BIMCompiler](https://github.com/red1oon/BIMCompiler)

---

*BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.*
