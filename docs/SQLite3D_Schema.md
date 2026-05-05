# SQLite Schema — BIM OOTB Viewer Database

> **The innovation:** IFC → web-ifc WASM → SQLite BLOBs → Three.js GPU. No server. No format conversion. No proprietary viewer. Two DBs (or one), straight from the IFC standard to the browser.

**Updated:** 2026-05-02

---

## Architecture: Why SQLite Beats Autodesk/Bonsai

| Traditional (Autodesk/Revit) | BIM OOTB |
|---|---|
| Proprietary binary format (.rvt) | Open SQLite + IFC |
| Server-side rendering (Forge/ACC) | Client-side Three.js from DB BLOBs |
| Viewer requires cloud account + API key | Zero install, zero account |
| Geometry locked in application | Raw Float32Array vertices in DB — any tool can read |
| IFC export lossy | IFC is the source — round-trip guaranteed |
| 4D/5D bolt-on product (Navisworks) | Same DB, same tables, same viewer |

| Traditional (Bonsai/IfcOpenShell) | BIM OOTB |
|---|---|
| Python + C++ dependency chain | Single WASM file (web-ifc, 1.3MB) |
| Blender GPU required for viewing | Browser WebGL — any device |
| IfcOpenShell tessellation (slow, OOM on large) | web-ifc tessellation (fast, handles 48K elements in seconds) |
| Bbox recalculated from vertices | Bbox extracted from IFC IfcBoundingBox representation |
| GPL entanglement (Blender) | MIT license throughout |

---

## Schema (10 tables, one DB)

Produced by `scripts/extractIFC2DB.js` (Node.js) or browser Drop IFC (`import_worker.js` + `import_db_builder.js`). Both produce identical output.

### Core Tables

```sql
-- Project identity
CREATE TABLE project_metadata (
    key TEXT PRIMARY KEY,
    value TEXT
);
-- Keys: project_name, building_name, import_date, source_file

-- Element catalog (WHAT is in the building)
CREATE TABLE elements_meta (
    guid TEXT PRIMARY KEY,       -- IFC GlobalId
    ifc_class TEXT,              -- e.g. IfcWall, IfcBeam, IfcPipeSegment
    element_name TEXT,           -- User-visible name from IFC
    storey TEXT,                 -- Building storey assignment
    discipline TEXT,             -- ARC/STR/MEP/ELEC/PLB/ACMV/FP
    material_name TEXT,          -- IFC material name (if assigned)
    material_rgba TEXT,          -- "r,g,b,a" colour string (null = use discipline colour)
    building TEXT                -- Building name (for multi-building DBs)
);

-- Spatial placement + IFC-extracted bounding box
CREATE TABLE element_transforms (
    guid TEXT PRIMARY KEY,
    center_x REAL,              -- World-space centroid X (metres)
    center_y REAL,              -- World-space centroid Y
    center_z REAL,              -- World-space centroid Z
    rotation_x REAL,            -- Rotation (radians)
    rotation_y REAL,
    rotation_z REAL,
    bbox_x REAL,                -- IFC BoundingBox width (metres) — NOT computed from vertices
    bbox_y REAL,                -- IFC BoundingBox depth
    bbox_z REAL                 -- IFC BoundingBox height
);

-- Geometry instancing (deduplication via content hash)
CREATE TABLE element_instances (
    guid TEXT PRIMARY KEY,
    geometry_hash TEXT           -- FNV-1a hash of centred vertices + indices
);

-- Geometry BLOBs (shared across elements with same hash)
CREATE TABLE component_geometries (
    geometry_hash TEXT PRIMARY KEY,
    vertices BLOB,              -- Float32Array, centred at origin, Z-up
    faces BLOB,                 -- Int32Array (triangle indices)
    building TEXT
);
```

### 4D Scheduling Tables (empty if IFC has no schedule data)

```sql
CREATE TABLE schedules (
    schedule_id TEXT PRIMARY KEY,
    name TEXT,
    status TEXT,
    created_date TEXT
);

CREATE TABLE tasks (
    task_id TEXT PRIMARY KEY,
    schedule_id TEXT,
    name TEXT,
    start_date TEXT,
    finish_date TEXT,
    duration_days REAL,
    status TEXT
);

CREATE TABLE task_sequences (
    predecessor_id TEXT,
    successor_id TEXT,
    sequence_type TEXT,         -- FINISH_START, START_START, etc.
    lag_days REAL DEFAULT 0,
    PRIMARY KEY (predecessor_id, successor_id)
);

CREATE TABLE task_elements (
    task_id TEXT,
    guid TEXT,                  -- Links task → building element
    PRIMARY KEY (task_id, guid)
);
```

---

## Key Design Decisions

### 1. Geometry centred at origin + world-space centre
Each element's vertices are centred at (0,0,0). The viewer positions them via `center_x/y/z`. This enables:
- **Instancing**: identical shapes share one geometry BLOB regardless of position
- **Deduplication**: FNV-1a hash of centred vertices → same shape = same hash
- Terminal: 48,428 elements → 9,394 unique geometries (81% dedup, 45% smaller DB)

### 2. IfcBoundingBox separation
web-ifc's `GetFlatMesh` returns multiple geometry entries per element. One may be the IfcBoundingBox (8 vertices, 36 indices). We detect it, extract dimensions, skip it from body mesh. Without this, small fittings get oversized highlight boxes.

### 3. Near-white material skip
85% of elements get default white surface style from IFC authoring tools. We skip materials with R,G,B all > 0.95 — the viewer uses discipline-based colours instead (ARC=beige, STR=grey, MEP=green). Real colours (grey concrete, blue pipes) are preserved.

### 4. Auto-scale heuristic
IFC files may use mm or m. If element SPREAD > 500 in any axis, assume mm → divide by 1000. Uses spread (MAX-MIN), not absolute coords — buildings at large grid offsets (e.g. Singapore SVY21) stay in metres.

### 5. Single DB = both extracted + library
Browser Drop produces one DB with all tables. The viewer assigns the same handle to `extDb` and `libDb`. For OCI hosting, the same file is served as both `_extracted.db` and `_library.db`. No split needed.

---

## Extraction Paths

| Path | Engine | Use case |
|---|---|---|
| `scripts/extractIFC2DB.js` | web-ifc (Node.js) | Batch extraction, <100K elements |
| Browser Drop IFC | web-ifc (WASM) | Interactive import in viewer |
| `scripts/extract_merge_disciplines.py` | IfcOpenShell (Python) | Large merged IFCs >200MB (per-discipline) |

All three produce the same schema. Viewer doesn't distinguish source.

---

## Viewer Pipeline

```
SQLite DB → sql.js WASM → query elements_meta + element_transforms
  → stream geometry BLOBs from component_geometries
  → Float32Array → THREE.BufferGeometry → GPU

Pick: click → raycast → guid → SELECT bbox_x,y,z → yellow highlight box
Filter: storey/discipline → hide/show InstancedMesh instances
4D: scrub timeline → SELECT tasks by date → colour elements by phase
```

No intermediate format. No conversion. DB BLOBs ARE the GPU buffers.

---

## Authorship Map — What's Ours vs Third-Party

### Third-party libraries (loaded from CDN, not modified)

| Library | License | What it does | How loaded |
|---|---|---|---|
| [web-ifc](https://github.com/ThatOpenCompany/engine_web-ifc) @0.0.77 | MPL-2.0 | C++/WASM IFC parser + tessellator | CDN `unpkg.com` in `import_worker.js` |
| [sql.js](https://github.com/sql-js/sql.js) @1.10.3 | MIT | SQLite compiled to WASM — runs SQL in browser | CDN `cdnjs.cloudflare.com` in `streaming.js` |
| [Three.js](https://github.com/mrdoob/three.js) r128 | MIT | WebGL 3D renderer | CDN `cdnjs.cloudflare.com` in HTML |
| [dxf-parser](https://github.com/gdsestimating/dxf-parser) | MIT | DXF file parsing for 2D plans | Vendored minified `dxf-parser.js` |
| [ExcelJS](https://github.com/exceljs/exceljs) | MIT | Excel export for BOQ charts | CDN `cdnjs.cloudflare.com` |

None of these libraries are modified. They are called via their public APIs.

### Original BIM OOTB scripts (all by Redhuan D. Oon, MIT)

| Script | What it does | Novelty |
|---|---|---|
| `import_worker.js` | Calls web-ifc API → extracts entities → 4×4 transform, Y→Z-up, centroid re-centre, discipline classify, storey map, material extract, geometry dedup (FNV-1a hash), auto-scale mm→m | The extraction pipeline — turns raw web-ifc output into structured DB records |
| `import_db_builder.js` | Takes extracted data → creates 10-table SQLite schema via sql.js | The schema design — BOM-based, instanced, 4D-ready |
| `streaming.js` | Queries DB → streams BLOBs → Float32Array → Three.js BufferGeometry → GPU | **The core innovation** — DB BLOBs are GPU buffers, no intermediate format |
| `picking.js` | Raycast → GUID → SQL query → highlight box from bbox | Click-to-identify from DB, not scene graph |
| `navigate.js` | Storey/discipline filter, search, tree panel | SQL-driven navigation, not IFC hierarchy |
| `section_cut.js` | Clipping plane computed from DB geometry | Section cut from DB, not mesh boolean |
| `elevation.js` | 2D elevation projected from DB geometry | Elevation from DB BLOB vertices |
| `scene_to_db.js` | Three.js scene → write back to SQLite DB | Reverse pipeline — browser edits persist to DB |
| `ifc_export_worker.js` | DB → IFC STEP/ISO-10303-21 text file | Pure text generation — **no web-ifc dependency** |
| `mesh_import_worker.js` | DAE/OBJ/GLB → DB (uses Three.js loaders from CDN) | Multi-format import to same DB schema |
| `diff.js` | Compare two DBs (base vs variation) | Variation order / design diff from SQL |
| `walk.js` | First-person walk mode | Camera + collision from DB spatial data |
| `sitecam.js` | GPS/compass/AR mobile camera overlay | Real-world BIM overlay |
| `scene.js`, `panels.js`, `helpers.js`, `city.js`, `wizard.js`, `nlp.js`, `locale_loader.js`, `grid_dims.js`, `rates.js`, `title_block.js`, `dxf_export.js`, `semantic_enrichment.js`, `variation_order.js` | UI, i18n, enrichment, export | All original |

### The innovation boundary

web-ifc, sql.js, and Three.js each solve one problem. **No existing project combines them into a serverless BIM pipeline.** The original contribution is:

1. **Schema design** — 10 tables that hold an entire building as queryable data
2. **Extraction pipeline** — IFC entities → classified, instanced, centroid-recentred DB records
3. **DB-to-GPU streaming** — SQLite BLOB → Float32Array → BufferGeometry with zero conversion
4. **Round-trip** — browser edits → DB → IFC export, closing the loop without a server
5. **R-tree clash detection** — `rtree-sql.js` WASM (2025) enables O(n log N) spatial clash queries entirely in the browser. The critical enabler for S245-S246 clash detection, proximity LOD, and deep-link sharing. See [VibeProgramming.md §Technology Convergence](VibeProgramming.md#the-technology-convergence--why-this-was-impossible-before-2025) for the full timeline.

---

## Proven Scale

| Building | Elements | Unique Hashes | DB Size |
|---|---|---|---|
| Terminal | 48,428 | 9,394 | 268MB |
| LTU AHouse | 125,698 | — | 56MB (old path) |
| Hospital | 63,917 | — | 90MB (old path) |
| Ifc4_Revit | 11,412 | 3,724 | 41MB |
| HHS Office | 6,871 | 3,265 | merged 6 IFCs |
| WBDG Office | 7,000 | 4,141 | merged 3 IFCs |
