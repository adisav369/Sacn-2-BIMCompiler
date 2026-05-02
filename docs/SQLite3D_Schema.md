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

## Proven Scale

| Building | Elements | Unique Hashes | DB Size |
|---|---|---|---|
| Terminal | 48,428 | 9,394 | 268MB |
| LTU AHouse | 125,698 | — | 56MB (old path) |
| Hospital | 63,917 | — | 90MB (old path) |
| Ifc4_Revit | 11,412 | 3,724 | 41MB |
| HHS Office | 6,871 | 3,265 | merged 6 IFCs |
| WBDG Office | 7,000 | 4,141 | merged 3 IFCs |
