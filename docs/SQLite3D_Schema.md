# SQLite3D Schema — A Standard for DB-Native 3D Viewers

**Version 1.0 — BIM OOTB Reference Implementation**
**Creator:** [Redhuan D. Oon](mailto:red1org@gmail.com) · First published 2026-04-29

> **Origin story:** This schema was not designed top-down. It emerged across ~60 collaborative sessions between a BIM domain expert and Claude Code, through a series of constraints that progressively eliminated Blender, .blend files, bake pipelines, and finally Bonsai itself — leaving only two SQLite files and a browser. The full account is in [VibeProgramming.md — The Browser Pivot](VibeProgramming.md#case-study-the-browser-pivot--how-bonsai-became-optional-s165s231).

---

## Why This Exists

Every mainstream 3D viewer pipeline looks like this:

```
source file → parse → in-memory scene graph → GPU
```

The scene graph is a runtime construct. It exists only while the app is running. To filter by floor, you re-traverse the graph. To check what a GUID belongs to, you search a parallel data structure. To stream a large model, you build a custom chunking layer on top.

This document specifies a different approach: **the database is the scene graph**. There is no separate in-memory representation. The renderer queries SQL and sends bytes directly to the GPU. Filtering, spatial queries, and metadata lookups are SQL queries — not custom traversal code.

This architecture was proven in production at 126K elements on mobile with no native app, no server, and no conversion pipeline beyond the initial IFC extraction.

The schema is open. If you build a compatible viewer, it will load any DB produced by this extractor without modification.

---

## The Two-DB Split

Every building is two files:

| File | Role | Cache policy |
|------|------|-------------|
| `{Building}_extracted.db` | Semantic index: GUIDs, storeys, disciplines, placement, spatial structure | Invalidate on model update |
| `{Building}_library.db` | Geometry pool: pre-tessellated mesh BLOBs, keyed by hash | Immutable — content-addressed |

The split is not cosmetic. It encodes a physical fact: **geometry changes rarely; metadata changes often**. A site inspector updating a status field should not re-download 40MB of mesh data. A geometry correction should not invalidate annotation data.

The library DB can be shared across buildings. If two buildings contain the same door family, they share one row in the library. The extracted DB holds only the hash — never the bytes.

---

## Extracted DB Schema (`_extracted.db`)

### `project_metadata`

```sql
CREATE TABLE project_metadata (
    key   TEXT PRIMARY KEY,
    value TEXT
);
```

Key-value store for file-level provenance. Expected keys:

| key | example value |
|-----|--------------|
| `source_file` | `LTU_AHouse.ifc` |
| `ifc_schema` | `IFC2X3` |
| `extractor_version` | `S231` |
| `extracted_at` | `2026-04-29T14:32:00` |
| `unit_scale` | `1.0` (metres; extractor normalises all inputs to metres) |
| `coord_system` | `IFC` (X=east, Y=north, Z=up) |

---

### `spatial_structure`

```sql
CREATE TABLE spatial_structure (
    guid          TEXT PRIMARY KEY,
    type          TEXT NOT NULL,   -- IfcSite, IfcBuilding, IfcBuildingStorey, IfcSpace
    name          TEXT,
    parent_guid   TEXT,            -- NULL for root (IfcSite)
    object_type   TEXT,
    predefined_type TEXT
);
```

Direct representation of the IFC spatial containment tree. Root node has `parent_guid = NULL`.

Query: walk storey hierarchy
```sql
-- All storeys in the building
SELECT guid, name FROM spatial_structure
WHERE type = 'IfcBuildingStorey'
ORDER BY name;
```

---

### `elements_meta`

```sql
CREATE TABLE elements_meta (
    id           INTEGER PRIMARY KEY,
    guid         TEXT UNIQUE NOT NULL,
    discipline   TEXT NOT NULL,   -- ARC, STR, MEP, ELE, PLB, ...
    ifc_class    TEXT NOT NULL,   -- IfcWall, IfcBeam, IfcDuctSegment, ...
    element_name TEXT,
    element_type TEXT,
    storey       TEXT,
    material_name TEXT,
    material_rgba TEXT,           -- "r,g,b" or "r,g,b,a" in 0–1 range
    building     TEXT             -- building name for federated (multi-building) DBs
);
```

The semantic spine. Every renderable element has one row here.

`discipline` is derived from `ifc_class` at extraction time via a deterministic mapping table — not from IFC discipline annotations, which are unreliable. The mapping is:

| IFC class prefix | discipline |
|-----------------|-----------|
| IfcWall, IfcSlab, IfcRoof, IfcStair, IfcWindow, IfcDoor, IfcColumn | ARC |
| IfcBeam, IfcMember, IfcFooting, IfcPile | STR |
| IfcDuct*, IfcAirTerminal, IfcFan | MEP |
| IfcCableCarrierSegment, IfcElectricDistribution* | ELE |
| IfcPipeFitting, IfcPipeSegment, IfcValve | PLB |

Query: filter by discipline and storey
```sql
SELECT guid, ifc_class, element_name
FROM elements_meta
WHERE discipline = 'STR' AND storey = 'Level 2';
```

---

### `elements_rtree`

```sql
CREATE VIRTUAL TABLE elements_rtree
    USING rtree(id, minX, maxX, minY, maxY, minZ, maxZ);
```

`id` is a foreign key to `elements_meta.id`. Coordinates are world-space, in metres, IFC coordinate system (X=east, Y=north, Z=up).

This is the spatial index. It makes viewport culling, section cuts, storey detection, and elevation profiling into O(log n) SQL queries.

Query: elements within an axis-aligned bounding box
```sql
-- section_cut.js §SC_QUERY
SELECT m.guid, m.discipline, m.storey
FROM elements_meta m
JOIN elements_rtree r ON m.id = r.id
WHERE r.minX >= -10 AND r.maxX <= 50
  AND r.minY >= -10 AND r.maxY <= 50
  AND r.minZ >= 0   AND r.maxZ <= 4.5;
```

Query: storey floor levels (used by section_cut.js and elevation.js)
```sql
SELECT m.storey, MIN(r.minZ) AS floor_z, COUNT(*) AS n
FROM elements_meta m
JOIN elements_rtree r ON m.id = r.id
WHERE m.storey IS NOT NULL
GROUP BY m.storey
ORDER BY floor_z;
```

> **Note on sql.js:** The WASM build of sql.js may not include R-tree support depending on compile flags. Production code falls back to `element_transforms` centre-point queries when `elements_rtree` is absent or the query throws. Always `try/catch` R-tree queries in the browser.

---

### `base_geometries`

```sql
CREATE TABLE base_geometries (
    geometry_hash TEXT PRIMARY KEY,
    vertices      BLOB,           -- Float32, little-endian, XYZ triples, local coords, metres
    faces         BLOB,           -- Int32, little-endian, triangle index triples
    vertex_count  INTEGER,
    face_count    INTEGER
);
```

See §BLOB Encoding below for the byte-level format.

In single-building deployments, geometry BLOBs live here. In the two-DB deployment, this table contains only hash entries (BLOBs are NULL) — the actual bytes are in the library DB. The viewer tries both.

---

### `element_instances`

```sql
CREATE TABLE element_instances (
    guid          TEXT PRIMARY KEY,
    geometry_hash TEXT,
    FOREIGN KEY (geometry_hash) REFERENCES base_geometries(geometry_hash)
);
```

The instancing join. Multiple GUIDs can share one `geometry_hash`. This is how a building with 800 identical doors becomes 800 rows in `element_instances` but one row in `base_geometries` / `component_geometries`.

Query: find all elements sharing a geometry
```sql
SELECT m.guid, m.storey, m.element_name
FROM element_instances i
JOIN elements_meta m ON m.guid = i.guid
WHERE i.geometry_hash = 'a3f7c91b2d4e8f01';
```

---

### `element_transforms`

```sql
CREATE TABLE element_transforms (
    guid         TEXT PRIMARY KEY,
    center_x     REAL,           -- World-space placement, metres, IFC coords
    center_y     REAL,
    center_z     REAL,
    transform_source TEXT,       -- 'iterator_matrix' | 'placement_fallback'
    rotation_x   REAL DEFAULT 0, -- Euler angles in radians (ZYX decomposition)
    rotation_y   REAL DEFAULT 0,
    rotation_z   REAL DEFAULT 0
);
```

World-space placement for each element. The rotation is decomposed from the IFC placement matrix via ZYX Euler:

```python
# extractIFCtoDB.py §EULER_DECOMPOSE
sy = math.sqrt(rot3[0,0]**2 + rot3[1,0]**2)
if sy > 1e-6:
    rot_x = math.atan2(rot3[2,1], rot3[2,2])
    rot_y = math.atan2(-rot3[2,0], sy)
    rot_z = math.atan2(rot3[1,0], rot3[0,0])
```

The geometry BLOB is in local coordinates relative to the element's tack point (the IFC local placement origin). The viewer applies `center + rotation` to position the mesh in world space. There is no scale — all geometry is already in metres.

---

### `rel_contained_in_space` and `rel_aggregates`

```sql
CREATE TABLE rel_contained_in_space (
    element_guid TEXT,
    space_guid   TEXT,
    PRIMARY KEY (element_guid, space_guid)
);

CREATE TABLE rel_aggregates (
    parent_guid TEXT NOT NULL,
    child_guid  TEXT NOT NULL,
    PRIMARY KEY (parent_guid, child_guid)
);
```

Direct serialisation of `IfcRelContainedInSpatialStructure` and `IfcRelAggregates`. Used for room-level queries and BOM aggregation.

---

### `surface_styles`

```sql
CREATE TABLE surface_styles (
    style_name         TEXT PRIMARY KEY,
    surface_r          REAL,
    surface_g          REAL,
    surface_b          REAL,
    transparency       REAL DEFAULT 0.0,   -- 0=opaque, 1=fully transparent
    specular_r         REAL,
    specular_g         REAL,
    specular_b         REAL,
    specular_ratio     REAL,
    specular_exponent  REAL,
    reflectance_method TEXT DEFAULT 'NOTDEFINED',
    side               TEXT DEFAULT 'BOTH',
    source             TEXT
);
```

IFC surface style data. The viewer reads `surface_r/g/b` and `transparency` to build `THREE.MeshPhongMaterial`. The specular fields are available for PBR-capable renderers.

---

### `material_layers`

```sql
CREATE TABLE material_layers (
    layer_set_name TEXT NOT NULL,
    sequence       INTEGER NOT NULL,
    material_name  TEXT,
    thickness_m    REAL,           -- metres
    is_ventilated  INTEGER DEFAULT 0,
    PRIMARY KEY (layer_set_name, sequence)
);
```

Wall and slab layer stacks from `IfcMaterialLayerSet`. Used by quantity takeoff — total material volume is `SUM(thickness_m * element_area)` per layer.

---

## Library DB Schema (`_library.db`)

The library is the geometry pool. It has two tables.

### `component_geometries`

```sql
CREATE TABLE component_geometries (
    geometry_hash  TEXT PRIMARY KEY,
    vertices       BLOB NOT NULL,   -- Float32, little-endian, XYZ triples, local coords, metres
    faces          BLOB NOT NULL,   -- Int32, little-endian, triangle index triples
    normals        BLOB,            -- Float32, per-vertex normals (optional; viewer recomputes if NULL)
    vertex_count   INTEGER NOT NULL,
    face_count     INTEGER NOT NULL
);
```

Same hash scheme as `base_geometries`. The hash is the identity. If two buildings produce the same hash, they share one row. The hash function:

```python
# extractIFCtoDB.py §GEOMETRY_HASH
import hashlib
def geometry_hash(vertices_blob, faces_blob):
    """SHA256-based 16-char hash of centered geometry."""
    return hashlib.sha256(vertices_blob + faces_blob).hexdigest()[:16]
```

### `surface_styles`

```sql
CREATE TABLE surface_styles (
    style_name TEXT PRIMARY KEY,
    red   REAL,
    green REAL,
    blue  REAL,
    alpha REAL
);
```

Simplified style table in the library — RGB + alpha only. The full specular data lives in the extracted DB's `surface_styles`.

---

## City Index DB Schema (`city_index.db`)

For multi-building deployments, a lightweight index coordinates on-demand loading.

```sql
CREATE TABLE building_summary (
    building      TEXT NOT NULL,
    discipline    TEXT NOT NULL,
    element_count INTEGER NOT NULL,
    center_x      REAL, center_y REAL, center_z REAL,    -- IFC coords, metres
    min_x REAL, min_y REAL, min_z REAL,
    max_x REAL, max_y REAL, max_z REAL,
    PRIMARY KEY (building, discipline)
);

CREATE TABLE building_archetype (
    building       TEXT PRIMARY KEY,
    archetype      TEXT NOT NULL,   -- maps to {archetype}_extracted.db + {archetype}_library.db
    total_elements INTEGER NOT NULL
);
```

The viewer fetches only this file on startup (~5KB). Individual building DBs are fetched on demand as the camera approaches. The city index never contains geometry.

Query used by city.js on initialisation:
```sql
-- Discipline summary for ARC+STR (envelope only) — for building centroid pins
SELECT building, SUM(element_count),
       AVG(center_x), AVG(center_y), AVG(center_z)
FROM building_summary
WHERE discipline IN ('ARC','STR')
GROUP BY building;
```

---

## BLOB Encoding

This is the machine-level contract between the extractor (Python) and the renderer (JavaScript).

### Vertex BLOB

- Format: **IEEE 754 single-precision float (float32), little-endian**
- Layout: `[x0, y0, z0, x1, y1, z1, ...]`
- Stride: 12 bytes per vertex (3 × 4 bytes)
- Coordinate system: **IFC local** — X=element-local east, Y=element-local north, Z=up
- Units: **metres** (the IfcOpenShell iterator returns metres natively; no scaling is applied)
- Origin: the IFC local placement origin of the element (tack point), **not** recentred to geometry centroid

Python serialisation:
```python
# extractIFCtoDB.py §BLOB_WRITE
verts = np.array(geo.verts, dtype=np.float64).reshape(-1, 3)
vblob = verts.astype(np.float32).tobytes()   # float64 → float32, native endian (always LE on x86)
```

Byte count check: `vertex_count = len(vblob) // 12`

### Face BLOB

- Format: **signed 32-bit integer (int32), little-endian**
- Layout: `[i0, i1, i2, i3, i4, i5, ...]` — triangle list, 3 indices per triangle
- Stride: 12 bytes per triangle (3 × 4 bytes)
- Winding order: inherited from IfcOpenShell tessellator output — counter-clockwise (standard OpenGL/WebGL convention)

Python serialisation:
```python
# extractIFCtoDB.py §BLOB_WRITE
faces = np.array(geo.faces, dtype=np.int32).reshape(-1, 3)
fblob = faces.astype(np.int32).tobytes()
```

Byte count check: `face_count = len(fblob) // 12`

### JavaScript deserialisation

```javascript
// scene.js §BLOB_TO_GEOMETRY
A.blobToGeometry = function(vBlob, fBlob) {
    const vArr = new Float32Array(vBlob.buffer, vBlob.byteOffset, vBlob.byteLength / 4);
    const fArr = new Uint32Array(fBlob.buffer, fBlob.byteOffset, fBlob.byteLength / 4);

    if (vArr.length < 9 || fArr.length < 3) return null;

    // Coordinate transform: IFC (X east, Y north, Z up) → Three.js (X east, Y up, Z south)
    const positions = new Float32Array(vArr.length);
    for (let i = 0; i < vArr.length; i += 3) {
        positions[i]     = vArr[i];      // X → X  (east, unchanged)
        positions[i + 1] = vArr[i + 2];  // Z → Y  (up becomes Three.js Y)
        positions[i + 2] = -vArr[i + 1]; // -Y → Z (north negated to south)
    }

    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geo.setIndex(new THREE.BufferAttribute(fArr, 1));
    geo.computeVertexNormals();          // normals computed from faces; per-vertex normals in DB are not used
    geo.computeBoundingSphere();
    return geo;
};
```

The coordinate transform is the only mutation applied to the BLOB data. The positions array is a new allocation — the original BLOB bytes are not modified.

Note: `fArr` uses `Uint32Array` in JavaScript even though the DB stores `int32`. Face indices are never negative, so the reinterpretation is safe.

---

## The Instancing Pipeline

The viewer's draw-call budget is managed by grouping elements that share a `geometry_hash`.

**Phase 1 — collect all elements for the building:**
```javascript
// streaming.js §DS_QUEUED
SELECT m.guid, i.geometry_hash, m.material_rgba, m.discipline,
       t.center_x, t.center_y, t.center_z,
       t.rotation_x, t.rotation_y, t.rotation_z,
       m.storey, m.ifc_class
FROM elements_meta m
JOIN element_instances i ON m.guid = i.guid
JOIN element_transforms t ON t.guid = m.guid
WHERE m.building = ?
  AND i.geometry_hash IS NOT NULL
  AND m.ifc_class != 'IfcOpeningElement'
```

**Phase 2 — fetch BLOBs for all unique hashes (batch of 200):**
```javascript
// streaming.js §BLOB_FETCH
SELECT geometry_hash, vertices, faces
FROM component_geometries          -- library DB table name
WHERE geometry_hash IN (?,?,?,...) -- up to 200 placeholders
```

The viewer tries `component_geometries` first, then `base_geometries`, in both the library DB and the extracted DB.

**Phase 3 — flush: one draw call per unique hash**

```javascript
// streaming.js §FLUSH_INSTANCED  (simplified)
for (const [hash, elements] of Object.entries(pendingInstances)) {
    if (elements.length >= 2) {
        // ONE draw call for N identical geometries — THREE.InstancedMesh
        const iMesh = new THREE.InstancedMesh(geo, mat, elements.length);
        for (let i = 0; i < elements.length; i++) {
            const pos = ifc2three(el.cx, el.cy, el.cz);
            _m4.compose(pos, quaternionFromEuler(el.rotX, el.rotZ, -el.rotY), unitScale);
            iMesh.setMatrixAt(i, _m4);
        }
    } else {
        // Single occurrence — individual THREE.Mesh
        const mesh = new THREE.Mesh(geo, mat);
        mesh.position.set(...ifc2three(el.cx, el.cy, el.cz));
    }
}
```

Mobile path (S232): single-instance elements are additionally merged by `storey|discipline|material` into one `THREE.Mesh` per bucket, baking the world transform into vertex positions. This trades per-element pick capability for ~200 total draw calls on complex models.

---

## Coordinate Systems

| System | X | Y | Z | Used in |
|--------|---|---|---|---------|
| IFC | East | North | Up | DB storage (all tables) |
| Three.js | East | Up | South | Renderer, camera, raycaster |
| Blender | East | South | Up | Python extraction pipeline |

The only transform that matters for a viewer is IFC → Three.js:

```javascript
// scene.js §IFC2THREE
A.ifc2three = function(ix, iy, iz) {
    return {
        x:  ix - A.modelOffset.x,   // east, offset to model centre
        y:  iz - A.modelOffset.z,   // IFC Z (up) → Three.js Y (up)
        z: -(iy - A.modelOffset.y)  // IFC Y (north) → Three.js -Z (south)
    };
};
```

`modelOffset` is computed once at load time as the centroid of all element transforms. This keeps floating-point precision high for large site coordinates (IFC world origin may be tens of thousands of metres from the building).

Euler rotation in the transform: the IFC placement matrix is decomposed to ZYX Euler angles and stored as `rotation_x/y/z`. The viewer applies them as `mesh.rotation.set(rotX, rotZ, -rotY)` — mapping IFC ZYX to Three.js XYZ with the Y/Z swap.

---

## IndexedDB Caching Layer

The browser maintains a persistent cache in IndexedDB so DB files are not re-fetched on subsequent visits.

```javascript
// scene.js §CACHED_FETCH
A.CACHE_DB_NAME = 'bim_ootb_cache';
A.CACHE_STORE   = 'dbs';

A.cachedFetch = async function(url) {
    const cacheDb = await A.openCacheDB();
    // Try IndexedDB first
    const cached = await idbGet(cacheDb, url);
    if (cached) return cached;          // §CACHE_HIT — no network request
    // Fetch, store, return
    const buf = await fetch(url).then(r => r.arrayBuffer());
    await idbPut(cacheDb, url, buf);    // §CACHE_MISS — stored for next time
    return buf;
};
```

The key is the URL. The value is the raw `ArrayBuffer` of the SQLite file. On cache hit, the DB is opened directly from memory: `new SQL.Database(new Uint8Array(buf))`.

This means geometry (the library DB, rarely changing) and metadata (the extracted DB, updated with the model) have identical cache mechanics. A future improvement is to serve the library DB with `Cache-Control: immutable` and the extracted DB with an ETag, letting the browser manage invalidation at the HTTP layer rather than the application layer.

---

## What Makes This Novel

Most 3D pipeline architectures have these layers as separate systems:

```
Parser → Scene graph (in-memory) → Spatial index → Renderer
```

This schema collapses all four into one SQLite file:

| Concern | Traditional | This schema |
|---------|------------|-------------|
| Geometry storage | glTF / OBJ / binary blob files | `component_geometries.vertices` BLOB — same file as metadata |
| Instancing | glTF mesh reuse, manual dedup | Hash-addressed: identical geometry is physically one row |
| Spatial index | Separate BVH built at runtime | `elements_rtree` — SQLite virtual table, persisted, no build step |
| Filtering | Traverse and test every node | `WHERE discipline = 'STR' AND storey = 'L2'` — SQL |
| Metadata | glTF extras / separate JSON | `elements_meta` joined to transforms and geometry in one query |
| Multi-model federation | Separate file per building, manual merge | City index DB — one SQL query returns all building centroids |

The geometry hash is the key innovation. It means:
1. **Deduplication is free** — `INSERT OR IGNORE` at extraction time; duplicates never enter the library
2. **Instancing requires no viewer logic** — group by hash, count > 1 means `InstancedMesh`
3. **Cross-building sharing is automatic** — two buildings with the same door share one library row
4. **Cache invalidation is content-addressed** — if the hash is the same, the geometry is the same

---

## Minimum Viable Viewer

A conformant viewer must:

1. Open `_extracted.db` and `_library.db` with sql.js (or equivalent SQLite WASM)
2. Query `elements_meta + element_instances + element_transforms` for elements
3. For each unique `geometry_hash`, query `component_geometries` or `base_geometries` for the BLOBs
4. Deserialise BLOBs as `Float32Array` (vertices) and `Uint32Array` (faces) per the BLOB encoding spec
5. Apply the IFC → renderer coordinate transform to vertex positions
6. Apply `center + rotation` from `element_transforms` to place each mesh
7. Read `material_rgba` from `elements_meta` for per-element colour

Everything else (R-tree culling, instancing, storey filter, section cuts) is a performance or UX optimisation on top of this core loop.

---

## File Naming Convention

| Pattern | Contains |
|---------|---------|
| `{Building}_extracted.db` | Semantic index — elements, transforms, spatial structure |
| `{Building}_library.db` | Geometry pool — BLOBs only |
| `city_index.db` | Multi-building index — centroids, bboxes, archetypes |

The `archetype` column in `building_archetype` maps a building name to its DB file pair. Buildings with identical floor plans share one archetype (and therefore one library DB).

---

## Versioning

The schema version is stored in `project_metadata`:

```sql
INSERT INTO project_metadata VALUES ('schema_version', '1.0');
```

Breaking changes increment the major version. Additive changes (new tables, new nullable columns) increment the minor version. A viewer must reject DBs with a higher major version than it understands.
