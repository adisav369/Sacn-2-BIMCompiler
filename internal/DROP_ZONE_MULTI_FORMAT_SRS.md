# Drop Zone Multi-Format Import — SRS
# ⚠ DO NOT REMOVE — Spec scope: DAE/3DS/OBJ/GLB import alongside IFC. Read the log after every run.

**Status:** DRAFT
**Sprint:** S227 (planned)
**Author:** Extracted from S220 IFC import architecture + Gerard Tchahba use-case analysis
**Depends on:** `import_db_builder.js` (S220), `import_worker.js` (S220), `landing2.html` drop zone

---

## §1 Problem Statement

BIM OOTB currently accepts only `.ifc` files. Users of non-BIM-native tools (Chief Architect Home Designer Pro, SketchUp, Rhino, ArchiCAD-lite) export geometry-only formats (DAE, 3DS, OBJ, GLB) that lack:
- IFC class semantics (IfcWall, IfcDoor, etc.)
- GUIDs (globally unique element identifiers)
- Storey assignment (spatial hierarchy)
- Discipline classification (ARC, STR, MEP, etc.)
- Structured material metadata

The viewer panels (storey filter, discipline toggle, element picker) all query `elements_meta` — without semantic data these panels are empty.

**Goal:** Accept non-IFC formats, extract what semantics exist, infer the rest via heuristics, and populate the same IFC-centric 4-table DB schema. The DB schema *is* IFC — `ifc_class`, `storey`, `discipline`, `guid` — so any format that arrives in this schema has been effectively IFC-ified. The Drop Zone becomes an **IFC on-ramp** for users whose tools cannot export IFC natively.

---

## §2 Architecture Overview

```
                        ┌─────────────────────┐
                        │    DROP ZONE UI      │
                        │  (landing2.html)     │
                        └──────┬──────────────┘
                               │ file.name extension
                               ▼
                    ┌─────────────────────────┐
                    │   FORMAT ROUTER (§3)     │
                    │   detect(ext) → worker   │
                    └──┬───────┬────────┬─────┘
                       │       │        │
                 .ifc  │  .dae │  .obj  │  .glb/.gltf/.3ds/.fbx
                       │  .3ds │  .stl  │
                       ▼       ▼        ▼
               ┌──────────┐ ┌────────────┐ ┌──────────────┐
               │ IFC      │ │ MESH       │ │ MESH         │
               │ Worker   │ │ Worker     │ │ Worker       │
               │ (web-ifc)│ │ (Three.js) │ │ (Three.js)   │
               │ EXISTING │ │ NEW (§4)   │ │ SAME as DAE  │
               └────┬─────┘ └─────┬──────┘ └──────┬───────┘
                    │              │                │
                    ▼              ▼                ▼
              ┌──────────────────────────────────────────┐
              │ SEMANTIC ENRICHMENT ENGINE (§5)           │
              │ node_name → ifc_class (heuristic)        │
              │ z-height  → storey    (banding)          │
              │ material  → discipline (keyword)         │
              │ hash(name+pos) → guid  (deterministic)   │
              └──────────────────┬───────────────────────┘
                                 │
                                 ▼
              ┌──────────────────────────────────────────┐
              │ import_db_builder.js (EXISTING, §6)      │
              │ buildImportDBs(SQL, data) — UNCHANGED    │
              │ → elements_meta, element_transforms,     │
              │   element_instances, component_geometries│
              └──────────────────┬───────────────────────┘
                                 │
                                 ▼
              ┌──────────────────────────────────────────┐
              │ IndexedDB → Viewer panels work as-is     │
              └──────────────────────────────────────────┘
```

**Principle:** One new worker (`mesh_import_worker.js`) handles ALL non-IFC formats. Three.js loaders are pluggable — same scene graph traversal, same output contract.

---

## §3 Format Router

### §3.1 Extension Detection

```javascript
const FORMAT_MAP = {
  '.ifc':  'ifc',        // → import_worker.js (existing)
  '.dae':  'mesh',       // → mesh_import_worker.js (new)
  '.3ds':  'mesh',
  '.obj':  'mesh',
  '.glb':  'mesh',
  '.gltf': 'mesh',
  '.fbx':  'mesh',
  '.stl':  'mesh',       // no node names, geometry-only
};
```

### §3.2 Routing Logic (in landing2.html)

```javascript
function handleImportFile(file) {
  const ext = '.' + file.name.split('.').pop().toLowerCase();
  const format = FORMAT_MAP[ext];

  if (format === 'ifc') {
    // existing path — import_worker.js (web-ifc WASM)
    startIfcImport(file);
  } else if (format === 'mesh') {
    // new path — mesh_import_worker.js (Three.js loaders)
    startMeshImport(file, ext);
  } else {
    showStatus('Unsupported format: ' + ext +
      '. Accepted: .ifc, .dae, .3ds, .obj, .glb, .gltf, .fbx, .stl');
  }
}
```

### §3.3 UI Changes

- Drop zone `accept` attribute: `.ifc,.dae,.3ds,.obj,.glb,.gltf,.fbx,.stl`
- Drop zone label: "Drop IFC, DAE, OBJ, GLB, or other 3D file"
- After detection, status shows: "Importing DAE file..." (format-aware)
- File input click also accepts all formats

### §3.4 Companion File Detection (Materials List)

```javascript
// If user drops a .csv alongside (or after) a .dae, treat as Materials List sidecar
// Store in IndexedDB keyed to same building name
// Phase 2 feature — not required for initial PoC
```

---

## §4 Mesh Import Worker (`mesh_import_worker.js`)

### §4.1 Three.js Loader Selection

```javascript
const LOADER_MAP = {
  '.dae':  'ColladaLoader',
  '.3ds':  'TDSLoader',
  '.obj':  'OBJLoader',
  '.glb':  'GLTFLoader',
  '.gltf': 'GLTFLoader',
  '.fbx':  'FBXLoader',
  '.stl':  'STLLoader',
};
```

Three.js loaders work in Web Workers via `import()` from CDN (same pattern as web-ifc in S220).

**CDN source:** `https://unpkg.com/three@0.170.0/examples/jsm/loaders/`

### §4.2 Scene Graph Traversal

All Three.js loaders produce a `THREE.Group` / `THREE.Scene`. The worker walks this tree:

```javascript
function traverseScene(object, elements, geometries, transforms) {
  object.traverse(child => {
    if (!child.isMesh) return;

    const nodeName = child.name || child.parent?.name || 'unnamed_' + child.id;
    const guid     = generateGUID(nodeName, child);
    const geomHash = hashGeometry(child.geometry);

    // Extract vertices + faces from BufferGeometry
    const { vertices, indices } = extractGeometry(child);
    const { cx, cy, cz }       = computeCentroid(vertices);
    const recentered            = recenterVertices(vertices, cx, cy, cz);

    // Semantic enrichment (§5)
    const ifcClass   = classifyByName(nodeName, child);
    const discipline = classifyDiscipline(ifcClass, nodeName, child);
    const storey     = classifyStorey(cy);  // cy = Z-up elevation
    const rgba       = extractColor(child);

    elements.push({
      guid, ifcClass, name: nodeName, storey, discipline, material: rgba
    });
    transforms.push({ guid, cx, cy, cz, rx: 0, ry: 0, rz: 0 });
    geometries.push({ guid, geomHash, vertices: recentered.buffer, indices: indices.buffer });
  });
}
```

### §4.3 Geometry Extraction from BufferGeometry

```javascript
function extractGeometry(mesh) {
  const geom = mesh.geometry;
  const pos  = geom.attributes.position;

  // Apply mesh world matrix to get world coordinates
  mesh.updateWorldMatrix(true, false);
  const matrix = mesh.matrixWorld;

  const vertices = new Float32Array(pos.count * 3);
  const v = new THREE.Vector3();
  for (let i = 0; i < pos.count; i++) {
    v.set(pos.getX(i), pos.getY(i), pos.getZ(i));
    v.applyMatrix4(matrix);
    vertices[i*3]   = v.x;
    vertices[i*3+1] = v.y;
    vertices[i*3+2] = v.z;
  }

  // Faces: indexed or implicit
  let indices;
  if (geom.index) {
    indices = new Int32Array(geom.index.array);
  } else {
    // Non-indexed: generate sequential indices
    indices = new Int32Array(pos.count);
    for (let i = 0; i < pos.count; i++) indices[i] = i;
  }

  return { vertices, indices };
}
```

### §4.4 Coordinate System Handling

| Format | Up axis | Action |
|--------|---------|--------|
| DAE    | Y-up (default) or Z-up (per `<up_axis>`) | Swap if Y-up: `(x,y,z) → (x,-z,y)` |
| 3DS    | Z-up | No swap needed |
| OBJ    | Y-up | Swap: `(x,y,z) → (x,-z,y)` |
| GLB    | Y-up | Swap: `(x,y,z) → (x,-z,y)` |
| FBX    | Y-up or Z-up (per file) | Three.js FBXLoader auto-corrects |
| STL    | Undefined (assume Z-up) | No swap |

**Detection:** Three.js loaders set `scene.userData.upAxis` or the DAE `<up_axis>` tag. Check and swap accordingly.

### §4.5 GUID Generation

IFC has `GloballyUniqueId`. Non-IFC files don't. Generate deterministic GUIDs:

```javascript
function generateGUID(nodeName, mesh) {
  // Hash of: node name + vertex count + bounding box
  // Deterministic: same file always produces same GUIDs
  const bbox = new THREE.Box3().setFromBufferAttribute(mesh.geometry.attributes.position);
  const sig  = nodeName + '|' + mesh.geometry.attributes.position.count +
               '|' + bbox.min.x.toFixed(4) + ',' + bbox.min.y.toFixed(4) + ',' + bbox.min.z.toFixed(4) +
               '|' + bbox.max.x.toFixed(4) + ',' + bbox.max.y.toFixed(4) + ',' + bbox.max.z.toFixed(4);
  return 'DAE_' + sha256hex(sig).substring(0, 22);  // 22-char hash, prefixed
}
```

**Prefix convention:** `DAE_`, `OBJ_`, `GLB_`, `STL_` — identifies origin format, never collides with IFC GUIDs (which are 22-char base64).

### §4.6 Output Contract

The worker emits the **exact same message format** as `import_worker.js`:

```javascript
postMessage({
  type: 'done',
  meta: {
    name:         filename,
    filename:     filename,
    elementCount: elements.length,
    geomCount:    geometries.length,
    disciplines:  { ARC: n, STR: n, ... },
    storeys:      [...uniqueStoreys],
    sourceFormat: ext,              // NEW field — '.dae', '.obj', etc.
  },
  elements:   [...],   // same shape as IFC worker
  geometries: [...],   // same shape
  transforms: [...],   // same shape
});
```

`import_db_builder.js` receives this **unchanged**. No modifications to the DB builder.

### §4.7 Auto-Scale Heuristic (inherited from S220)

Same rule: if `max(abs(vertex coordinate)) > 500`, assume millimetres, divide all by 1000.

---

## §5 Semantic Enrichment Engine

This is the core of Tier 2 — inferring BIM semantics from dumb geometry.

### §5.1 IFC Class from Node Name

```javascript
const NAME_TO_IFC = [
  // Ordered by specificity — first match wins
  { pattern: /\b(exterior.?wall|ext.?wall)\b/i,  ifcClass: 'IfcWall',       disc: 'ARC' },
  { pattern: /\b(interior.?wall|int.?wall|partition)\b/i, ifcClass: 'IfcWall', disc: 'ARC' },
  { pattern: /\bwall\b/i,                         ifcClass: 'IfcWall',       disc: 'ARC' },
  { pattern: /\bdoor\b/i,                         ifcClass: 'IfcDoor',       disc: 'ARC' },
  { pattern: /\bwindow\b/i,                       ifcClass: 'IfcWindow',     disc: 'ARC' },
  { pattern: /\b(slab|floor)\b/i,                 ifcClass: 'IfcSlab',       disc: 'ARC' },
  { pattern: /\broof\b/i,                         ifcClass: 'IfcRoof',       disc: 'ARC' },
  { pattern: /\bceiling\b/i,                      ifcClass: 'IfcCovering',   disc: 'ARC' },
  { pattern: /\bstair/i,                          ifcClass: 'IfcStairFlight',disc: 'ARC' },
  { pattern: /\brailing\b/i,                      ifcClass: 'IfcRailing',    disc: 'ARC' },
  { pattern: /\bramp\b/i,                         ifcClass: 'IfcRamp',       disc: 'ARC' },
  { pattern: /\bcurtain.?wall\b/i,                ifcClass: 'IfcCurtainWall',disc: 'ARC' },
  { pattern: /\bcolumn\b/i,                       ifcClass: 'IfcColumn',     disc: 'STR' },
  { pattern: /\bbeam\b/i,                         ifcClass: 'IfcBeam',       disc: 'STR' },
  { pattern: /\bfooting\b/i,                      ifcClass: 'IfcFooting',    disc: 'STR' },
  { pattern: /\bfoundation\b/i,                   ifcClass: 'IfcFooting',    disc: 'STR' },
  { pattern: /\bpile\b/i,                         ifcClass: 'IfcPile',       disc: 'STR' },
  { pattern: /\bpipe\b/i,                         ifcClass: 'IfcPipeSegment',disc: 'PLB' },
  { pattern: /\bduct\b/i,                         ifcClass: 'IfcDuctSegment',disc: 'ACMV' },
  { pattern: /\b(cable|wire)\b/i,                 ifcClass: 'IfcCableSegment',disc: 'ELEC' },
  { pattern: /\blight\b/i,                        ifcClass: 'IfcLightFixture',disc: 'ELEC' },
  { pattern: /\b(outlet|socket|switch)\b/i,       ifcClass: 'IfcOutlet',     disc: 'ELEC' },
  { pattern: /\b(sink|toilet|basin|shower|bath|faucet)\b/i, ifcClass: 'IfcSanitaryTerminal', disc: 'PLB' },
  { pattern: /\bsprinkler\b/i,                    ifcClass: 'IfcFireSuppressionTerminal', disc: 'FP' },
  { pattern: /\b(furniture|sofa|table|chair|desk|bed|cabinet|shelf)\b/i, ifcClass: 'IfcFurnishingElement', disc: 'ARC' },
  { pattern: /\b(appliance|fridge|oven|washer|dryer)\b/i, ifcClass: 'IfcElectricAppliance', disc: 'ELEC' },
];

const DEFAULT_IFC_CLASS  = 'IfcBuildingElementProxy';
const DEFAULT_DISCIPLINE = 'ARC';
```

### §5.2 Material-Name Fallback

If node name matches nothing, try the material name:

```javascript
const MATERIAL_TO_IFC = [
  { pattern: /\bconcrete\b/i,   ifcClass: 'IfcSlab',    disc: 'STR' },
  { pattern: /\bsteel\b/i,      ifcClass: 'IfcBeam',    disc: 'STR' },
  { pattern: /\bbrick\b/i,      ifcClass: 'IfcWall',    disc: 'ARC' },
  { pattern: /\bglass\b/i,      ifcClass: 'IfcWindow',  disc: 'ARC' },
  { pattern: /\bwood\b/i,       ifcClass: 'IfcBuildingElementProxy', disc: 'ARC' },
  { pattern: /\bcopper\b/i,     ifcClass: 'IfcPipeSegment', disc: 'PLB' },
  { pattern: /\bpvc\b/i,        ifcClass: 'IfcPipeSegment', disc: 'PLB' },
];
```

### §5.3 Classification Cascade

```
1. Match node name against NAME_TO_IFC         → if hit: use ifcClass + disc
2. Match material name against MATERIAL_TO_IFC → if hit: use ifcClass + disc
3. Match parent node name against NAME_TO_IFC  → if hit: inherit ifcClass + disc
4. Fall through → IfcBuildingElementProxy + ARC
```

Every element gets classified. No NULLs for `ifc_class` or `discipline`.

### §5.4 Storey Assignment by Elevation Banding

```javascript
const STOREY_BANDS = [
  { min: -Infinity, max: -0.5,  name: 'Basement'     },
  { min: -0.5,      max: 3.5,   name: 'Ground Floor'  },
  { min: 3.5,       max: 6.5,   name: 'Level 1'       },
  { min: 6.5,       max: 9.5,   name: 'Level 2'       },
  { min: 9.5,       max: 12.5,  name: 'Level 3'       },
  { min: 12.5,      max: Infinity, name: 'Upper Levels' },
];

function classifyStorey(elevation_z) {
  for (const band of STOREY_BANDS) {
    if (elevation_z >= band.min && elevation_z < band.max) return band.name;
  }
  return 'Unknown';
}
```

**Note:** Band thresholds assume ~3m floor-to-floor. Works for residential (Gerard's case). Commercial buildings may need wider bands. The 3m default is conservative — most houses are 2.4m-3.0m floor-to-floor.

### §5.5 Adaptive Storey Detection (Enhancement)

If the model has distinct Z-clusters (many elements at similar heights), detect natural breaks:

```javascript
function detectStoreyBreaks(elevations) {
  // Sort all element centroids by Z
  // Find gaps > 2m between clusters
  // Use cluster midpoints as storey boundaries
  // Fall back to fixed bands if < 2 clusters detected
}
```

Phase 2 — fixed bands first, adaptive later.

### §5.6 Color Extraction

```javascript
function extractColor(mesh) {
  if (!mesh.material) return '0.7,0.7,0.7,1.0';  // default grey

  const mat = Array.isArray(mesh.material) ? mesh.material[0] : mesh.material;

  if (mat.color) {
    const c = mat.color;
    const a = (mat.opacity !== undefined) ? mat.opacity : 1.0;
    return c.r.toFixed(3) + ',' + c.g.toFixed(3) + ',' + c.b.toFixed(3) + ',' + a.toFixed(3);
  }

  return '0.7,0.7,0.7,1.0';  // fallback grey
}
```

---

## §6 DB Builder — No Changes Required

`import_db_builder.js` (S220) already accepts the generic contract:

```javascript
buildImportDBs(SQL, {
  meta:       { name, filename, elementCount, geomCount, disciplines, storeys },
  elements:   [{ guid, ifcClass, name, storey, discipline, material }],
  transforms: [{ guid, cx, cy, cz, rx, ry, rz }],
  geometries: [{ guid, geomHash, vertices: ArrayBuffer, indices: ArrayBuffer }],
})
```

The mesh worker outputs this exact shape. **Zero changes to the DB builder.**

### §6.1 One New Column (Optional, Phase 2)

```sql
ALTER TABLE project_metadata ADD COLUMN source_format TEXT;
-- Values: 'ifc', 'dae', '3ds', 'obj', 'glb', 'fbx', 'stl'
-- Stored via: INSERT INTO project_metadata VALUES ('source_format', '.dae')
```

Viewer can show "Imported from DAE" in info panel. No schema migration needed — just an extra `INSERT` row in `project_metadata`.

---

## §7 Format-Specific Notes

### §7.1 DAE (Collada) — Primary Target

| Property | Availability | Extraction |
|----------|-------------|------------|
| Node names | YES — `<node name="Wall_Exterior">` | Direct from scene graph |
| Materials | YES — `<material>` with `<effect>` colors | Three.js parses to `MeshPhongMaterial` |
| Textures | YES — `<image>` refs (may be broken paths) | Texture coords extracted, images may 404 |
| Hierarchy | YES — nested `<node>` groups | Parent name = context for classification |
| Transforms | YES — `<matrix>` or `<translate>`+`<rotate>` | Three.js applies automatically |
| Up axis | YES — `<up_axis>Y_UP</up_axis>` | Read from XML, swap if Y-up |
| GUIDs | NO | Generate deterministic hash |
| IFC classes | NO | Infer from node name (§5.1) |
| Storeys | NO | Infer from Z-height (§5.4) |

**SketchUp DAE** exports preserve component names as node names. A well-organized SketchUp model will have usable names.

**HDP DAE** exports reportedly use generic names (`geo_54`). Classification will fall through to `IfcBuildingElementProxy` + material fallback. This is the worst case — still renders, just less semantic.

### §7.2 OBJ — Common Export

| Property | Availability | Notes |
|----------|-------------|-------|
| Object names | YES — `o ObjectName` or `g GroupName` | Usable for classification |
| Materials | YES — `.mtl` sidecar file | Must detect and load sidecar |
| Textures | YES — via .mtl refs | May need path resolution |
| Hierarchy | FLAT — no nesting | Groups only, no parent context |
| Up axis | Y-up (convention) | Swap to Z-up |

**OBJ sidecar:** `.mtl` file must be in same directory or embedded. The worker should check for `mtllib` reference and fetch it.

### §7.3 GLB/GLTF — Modern Standard

| Property | Availability | Notes |
|----------|-------------|-------|
| Node names | YES | Scene graph preserved |
| Materials | YES — PBR (metallic-roughness) | Rich material data |
| Hierarchy | YES — full scene tree | Best non-IFC source |
| Animations | YES | Ignored for BIM |
| Extensions | YES — `KHR_materials_*` | Three.js handles |

GLB is single-file binary (no sidecar). Best non-IFC format for this purpose.

### §7.4 3DS — Legacy

| Property | Availability | Notes |
|----------|-------------|-------|
| Object names | YES — 10 char limit | Often truncated |
| Materials | YES | Basic color + texture |
| Hierarchy | FLAT | No parent context |
| Up axis | Z-up | No swap needed |

Legacy format, still common in HDP/architectural tools.

### §7.5 FBX — Autodesk

| Property | Availability | Notes |
|----------|-------------|-------|
| Node names | YES | Full names preserved |
| Materials | YES | Rich material model |
| Hierarchy | YES | Full scene tree |
| Animations | YES | Ignored for BIM |
| Properties | YES — custom properties | Potential semantic gold (§8) |

FBX may contain custom properties from the source tool. Phase 2: mine these for semantic data.

### §7.6 STL — Geometry Only

| Property | Availability | Notes |
|----------|-------------|-------|
| Names | NO — single mesh per file | One element, one `IfcBuildingElementProxy` |
| Materials | NO | Default grey |
| Hierarchy | NONE | Single solid |

STL is the worst case. Single mesh, no metadata. Still useful for: 3D-printed parts, terrain, single component preview. The entire file becomes one `IfcBuildingElementProxy`.

---

## §8 Materials List Sidecar (Phase 2)

Gerard's HDP can export a Materials List as CSV/HTML. This is the semantic data that the DAE discards.

### §8.1 Concept

```
User drops: house.dae + materials_list.csv
            ↓               ↓
       geometry          product catalog
            ↓               ↓
     mesh_import_worker  csv_parser
            ↓               ↓
         elements ←──── name matching
            ↓
     enriched elements_meta
```

### §8.2 Matching Strategy

| Strategy | How | Confidence |
|----------|-----|------------|
| Exact name match | CSV row "Exterior Wall" = DAE node "Exterior Wall" | HIGH |
| Fuzzy name | CSV "Ext. Wall" ~ DAE "ExteriorWall_01" | MEDIUM |
| Material match | CSV "Cedar Siding" = DAE material "Cedar_Siding" | MEDIUM |
| Count match | CSV qty 4 = DAE 4 nodes named "Window*" | LOW |
| Manual mapping | User-provided JSON: `{"geo_54": "Exterior Wall"}` | HIGH |

Phase 2 feature. Requires Gerard's actual CSV to design the parser.

---

## §9 Testing Strategy

### §9.1 Test Files Required

| File | Source | Purpose |
|------|--------|---------|
| `test_house.dae` | Hand-crafted in Blender (Archimesh) | Controlled: known node names, 10 elements |
| `sketchup_house.dae` | SketchUp 3D Warehouse download | Real-world: SketchUp naming conventions |
| `hdp_house.dae` | Gerard's HDP export (pending) | Target: actual HDP naming (`geo_54`?) |
| `test_house.obj` | Blender OBJ export of same house | Cross-format: same geometry, different parser |
| `test_house.glb` | Blender GLB export of same house | Cross-format: scene tree preserved |

### §9.2 Verification Criteria

| Check | How | Witness |
|-------|-----|---------|
| Element count | `SELECT COUNT(*) FROM elements_meta` matches mesh count | §9.2-COUNT |
| GUID uniqueness | `SELECT COUNT(DISTINCT guid) = COUNT(*)` | §9.2-GUID |
| Geometry renders | All elements visible in viewer (no invisible meshes) | §9.2-RENDER |
| Storey filter works | Panel shows Ground Floor / Level 1 (for multi-storey) | §9.2-STOREY |
| Discipline toggle works | Panel shows ARC (minimum) | §9.2-DISC |
| Element picker works | Click → info panel shows name + ifc_class | §9.2-PICK |
| Named elements classified | "Wall_Exterior" → IfcWall, not IfcBuildingElementProxy | §9.2-CLASS |
| Generic elements fallback | "geo_54" → IfcBuildingElementProxy + ARC | §9.2-FALLBACK |
| Color extracted | Colored materials show correct RGBA | §9.2-COLOR |
| Auto-scale | mm-unit model scaled to metres | §9.2-SCALE |

### §9.3 Rosetta Stone Applicability

This spec covers **Stage 1 only: extraction to input DB**. The Rosetta Stone gates verify **Stage 2: BOM recompilation via DAGCompiler** — they prove round-trip fidelity regardless of source format.

Pipeline position of this work:

```
Source (IFC/DAE/OBJ/...)
    ↓
  Stage 1: EXTRACT to input DB   ← THIS SPEC (browser extraction)
    ↓
  Classification YAML             ← human-authored (same as IFC path)
    ↓
  Stage 2: DAGCompiler recompile  ← existing pipeline, format-agnostic
    ↓
  Rosetta Stone G1-G6 gates       ← verifies Stage 2, not Stage 1
```

Once the extraction DB is correctly populated, the DAGCompiler doesn't care whether the source was IFC, DAE, or hand-typed SQL. A DAE-sourced building **can** pass Rosetta Stone gates — provided a classification YAML is authored and the BOM compiles.

**Browser viewer (this spec)** shows inferred semantics from Stage 1 heuristics. The UI should note the source:

```
"Imported from DAE — classifications inferred from mesh names"
```

This is the viewer-only path. The full BIM path (DAE → extract → YAML → BOM → compile → gates) follows the same pipeline as IFC — just with a different Stage 1 front-end.

---

## §10 File Manifest

| File | Status | Role |
|------|--------|------|
| `deploy/dev/landing2.html` | MODIFY | Add format router (§3), update drop zone UI |
| `deploy/dev/mesh_import_worker.js` | NEW | Three.js-based mesh parser (§4) |
| `deploy/dev/semantic_enrichment.js` | NEW | Name→IFC classification engine (§5) |
| `deploy/dev/import_db_builder.js` | UNCHANGED | Same 4-table schema, same function |
| `deploy/dev/import_worker.js` | UNCHANGED | IFC path untouched |
| `deploy/dev/import.js` | MINOR | Update format detection in UI handler |
| `deploy/dev/panels.js` | UNCHANGED | Queries same DB schema |
| `deploy/dev/streaming.js` | UNCHANGED | Reads same component_geometries |

---

## §11 Implementation Phases

### Phase 1 — DAE PoC (S227)
- Format router in landing2.html
- `mesh_import_worker.js` with ColladaLoader only
- `semantic_enrichment.js` with NAME_TO_IFC + fixed storey bands
- Test with hand-crafted `test_house.dae`
- Verify all 10 §9.2 witnesses

### Phase 2 — Multi-Format + Gerard's HDP (S228)
- Add OBJ, GLB, 3DS loaders to mesh worker
- Test with Gerard's actual HDP export
- Tune NAME_TO_IFC patterns for HDP naming conventions
- Materials List CSV sidecar (if Gerard provides)
- Adaptive storey detection (§5.5)

### Phase 3 — Production Hardening (S229)
- Error handling for corrupt/empty files
- Large file streaming (>100MB models)
- FBX custom property mining
- Promote to sandbox

---

## §12 Strategic Context — User Base & Competitive Landscape

### §12.1 Locked-Out User Base

These tools **cannot natively export IFC**. Their users are locked out of openBIM workflows:

| Tool | Users | IFC Status | Export Formats | Strategic Value |
|------|-------|------------|----------------|-----------------|
| **SketchUp** | **1M+ active subscribers** (Trimble, Nov 2024). 5,649 companies adopted by 2026. Top use: architecture (340 firms), interior design (319). | Rudimentary IFC export (Pro only, Windows-only for IFC2x3). Revit rejects SketchUp IFC walls. | DAE, OBJ, FBX, 3DS, KMZ | **Largest pool.** 1M users, most can't produce usable IFC. Our Drop Zone gives them a BIM path with zero workflow change — just export DAE/OBJ as they already do. |
| **Chief Architect / HDP** | **"Best-selling home design software for DIY"** — dominates US residential market. Professional + consumer tiers. Private company, no public user count, but top-rated in category. | **No IFC support at all.** Not import, not export. Complete BIM dead-end. | DAE, 3DS | **Gerard's case.** ~500K estimated residential users (builders, designers, DIY). Zero BIM path today. Our on-ramp is their ONLY option short of redrawing in Revit. |
| **Rhino3D** | McNeel private, 700+ resellers, offices in 12 countries. Strong in parametric/computational design. | IFC only via third-party plugin (Geometry Gym). Not native. Rhino Inside bridges to Revit but doesn't solve standalone IFC export. | OBJ, FBX, 3DS, DAE, STEP, 3DM | **Computational designers.** Grasshopper → Rhino → our Drop Zone gives parametric-to-BIM without Revit dependency. Niche but high-value. |
| **Blender** | **Millions of users** (~4.6M monthly web visits). 5,102 survey respondents (2025). Free, open source. | IFC via Bonsai addon (IfcOpenShell). Works but requires BIM knowledge — steep learning curve. | DAE, OBJ, FBX, GLB, 3DS, STL | **Largest total user base.** Most Blender users are artists/game devs, not BIM specialists. Bonsai serves the BIM-aware minority. Our Drop Zone serves the majority who just want to see their model in a BIM context without learning IFC. |
| **FBX ecosystem** | FBX included in **80%+ of US architecture & engineering programs** (2018-2023 growth: 120%). Autodesk standard. | FBX is geometry + animation, no BIM semantics. | — | **Education pipeline.** Students learn FBX in school, graduate into firms that need IFC. Our Drop Zone bridges this gap. |
| **STL/OBJ general** | Universal exchange formats. Every 3D tool exports these. | Geometry only. Zero semantics. | — | **Lowest common denominator.** Even the most obscure CAD tool can export OBJ. Our Drop Zone accepts it. |

**Total addressable user base: conservatively 2-3 million users** who produce 3D geometry but cannot produce IFC. Every one of them is a potential BIM OOTB user.

### §12.2 Existing Conversion Approaches & Why They Fall Short

| Approach | How It Works | Strength | Weakness | Our Advantage |
|----------|-------------|----------|----------|---------------|
| **Automapki Autoconverter** | Desktop app, DAE→IFC via File>Save As. Preserves materials, component instances. | Supports 50+ format pairs. Material textures carried over. | **Geometry wrapping only.** Wraps meshes in `IfcBuildingElementProxy` — no semantic classification. No storey assignment. No discipline. The result is technically IFC but semantically empty. Paid desktop software. | We classify by name/material heuristics. Viewer panels light up. Free, browser-native. |
| **Bonsai (BlenderBIM)** | Import mesh → manually assign IFC class per element → export IFC. Reclassify `IfcBuildingElementProxy` → `IfcWall`, etc. | Full IFC authoring. Proper type assignment with material layers/profiles. Geometry Gym adds parametric IFC. | **Manual per-element classification.** User must know IFC, understand types, assign each element. A 200-element house = 200 manual assignments. Requires Blender installation + BIM expertise. | We auto-classify from node names. Zero BIM knowledge needed. Zero install. |
| **Cloud2BIM** | Open-source point cloud → IFC. Wall/slab segmentation, opening detection, room zoning. 7× faster than competitors. | Fully automatic from laser scans. Handles non-orthogonal geometry. Published in Automation in Construction (2025). | **Point cloud input only.** Not applicable to CAD mesh exports (DAE/OBJ). Different problem domain (as-built survey, not design model). Requires LiDAR hardware. | We accept design-tool exports directly. No hardware needed. |
| **Open Design Alliance Scan-to-BIM SDK** | ML-assisted semantic segmentation (Point Transformer V2). Trained on Stanford S3DIS dataset. | High accuracy on indoor scenes. Commercial SDK with support. | **Point cloud only.** ML model trained on scan data, not CAD geometry. Commercial license. | Same — wrong input domain. |
| **CubiCasa** | Phone camera → floor plan → IFC. AI classification (95% F1 on 80 categories). 5K annotated dataset. | Phone-based, consumer-friendly. Good for real estate (floor plan generation). | **2D floor plan only.** No 3D model. Output is schematic IFC, not geometric. Won't handle a 3D DAE export. | We handle full 3D geometry with depth, not just 2D plans. |
| **IfcOpenShell IfcConvert** | CLI tool, converts IFC → OBJ/DAE/GLB. | Gold standard for IFC decomposition. | **One-way: IFC → mesh only.** Does not convert mesh → IFC. The reverse path (our problem) is not supported. | We do the reverse: mesh → IFC-centric DB. |

### §12.3 Why Our Approach Is Unique

Every existing tool either:
1. **Wraps geometry without semantics** (Automapki) — technically IFC, practically useless
2. **Requires manual BIM expertise** (Bonsai) — powerful but inaccessible
3. **Solves a different input** (Cloud2BIM, CubiCasa) — point clouds or 2D, not 3D CAD exports
4. **Goes the wrong direction** (IfcConvert) — IFC → mesh, not mesh → IFC

**Our position:**
- **Automatic semantic inference** from node names + materials + Z-height (no manual classification)
- **Browser-native** (no install, no Blender, no desktop app)
- **IFC-centric DB schema** as the single target (not IFC-the-file-format, but IFC-the-data-model)
- **Viewer included** — drop file, see BIM model, panels work, immediately useful
- **Pipeline-ready** — same DB feeds DAGCompiler for BOM recompilation downstream

The competitors convert format-to-format. We convert **dumb geometry to IFC-structured data** — which is a fundamentally different proposition.

---



| # | Question | Blocked by |
|---|----------|------------|
| 1 | What node names does HDP DAE actually produce? | Gerard's sample file |
| 2 | Does HDP Materials List CSV have any linkable keys to DAE nodes? | Gerard's CSV export |
| 3 | Should STL import one mesh = one element, or try to split by disconnected components? | Design decision |
| 4 | Three.js loaders in Web Worker — do all loaders work without DOM? ColladaLoader needs DOMParser. | Technical spike |
| 5 | OBJ `.mtl` sidecar — how to handle when user drops `.obj` without `.mtl`? | Default grey fallback |

### §12.4 Technical Note: Web Worker + DOMParser

ColladaLoader uses `DOMParser` to parse XML. Web Workers have `DOMParser` in modern browsers (Chrome 76+, Firefox 65+, Safari 15+). If a target browser lacks it, fallback: parse in main thread, transfer result to worker. This is a known constraint — test early.

---

## §13 Traceability

| Spec Section | Implements |
|-------------|------------|
| §3 Format Router | Gerard use-case: HDP → DAE → viewer |
| §4 Mesh Worker | Three.js loader → same DB contract as IFC |
| §5 Semantic Enrichment | Tier 2: infer IFC classes from dumb geometry |
| §5.4 Storey Banding | Residential floor-to-floor assumption |
| §6 DB Builder unchanged | Principle: one schema, many sources |
| §7 Format notes | Each format's strengths and limitations |
| §8 Materials List | Gerard's HDP semantic bridge |
| §9.3 No Rosetta gates | Honest: inferred ≠ proven |
