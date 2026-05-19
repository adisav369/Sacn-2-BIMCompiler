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

### 2D
- Section cut views
- Elevation generation
- Grid overlay with drag-to-recompile
- Door arc generation from IFC openings
- Dimension chains

### 4D Construction Scheduling
- Time Machine: scrub through construction phases on a timeline
- Elements appear in construction sequence with frontier highlighting
- Cinematic drone tour auto-generated from building storeys
- Gantt chart overlay with phase progress

### 5D Cost
- Bill of Quantities extracted from IFC element dimensions
- Cost dashboard with phase-by-phase breakdown
- 17 country-specific rate templates

### Clash Detection
- R-tree spatial index built from element bounding boxes
- Configurable clash rules (tolerances per discipline pair)
- Snag reporting with QR codes that deep-link back to the clashing elements

### Sharing
- Share a URL — recipient sees the exact camera angle and building state
- QR code generation for on-site access
- No account required to view

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

## Comparison

| Capability | BIM OOTB | Autodesk APS (Forge) | IFC.js / ThatOpen | Trimble Connect |
|---|---|---|---|---|
| Runs in browser | Yes | Yes | Yes | Yes |
| Server required | **No** | Yes (cloud) | No | Yes |
| Works offline | **Yes** | No | No | No |
| Install required | **None** | None | npm build | Desktop or web |
| IFC colour fidelity | **IfcSurfaceStyle extracted** | Proprietary conversion | WASM parse | Proprietary |
| Max elements (smooth) | **122K** | ~50K (server tiles) | ~20K | Server-rendered |
| Load from cache | **<1 second** | N/A (cloud) | N/A | N/A |
| 4D scheduling | **Integrated** | Separate product | No | Separate |
| 5D cost | **Integrated** | Separate product | No | Separate |
| Clash detection | **Integrated** | Separate (Navisworks) | No | Limited |
| Cost | **Free** | Metered API | Free (library) | Licensed |
| Vendor lock-in | **None (IFC standard)** | SVF/SVF2 proprietary | IFC standard | Proprietary |

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
| Rendering | Three.js r160 ESM | WebGL, BatchedMesh, InstancedMesh |
| Database | sql.js (SQLite WASM) | Query engine in browser |
| Spatial index | rtree-sql.js 1.7.0 | R-tree for clash detection |
| Caching | Service Worker + IndexedDB | Offline-first, cache-first |
| BVH | three-mesh-bvh 0.7.8 | Accelerated raycasting for pick |
| Tone mapping | ACESFilmic | Cinematic colour grading |
| Export | SheetJS | Excel BOQ export |

All open-source dependencies. No proprietary components.

## Catalogue

20 buildings from public IFC test files, ranging from a 218-element sample house to a 122,330-element university campus (LTU A-House, Sweden). Includes residential, commercial, hospital, airport terminal, and federated MEP models.

## Contact

Open source. MIT licence.
GitHub: [github.com/nicholaslimck/bim-compiler](https://github.com/nicholaslimck/bim-compiler)

---

*BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.*
