# S228a — ImportFormatToDB: Format-Agnostic Extraction Layer
# ⚠ DO NOT REMOVE — Scope: Pure conversion layer, no UI, no worker, no IndexedDB. Read the log after every run.

## Principle: Separation of Concern

```
┌────────────────────────────┐
│  UI Layer (import.js)      │  ← knows about drop zones, buttons, cards
│  Format Router             │  ← knows file extensions
└──────────┬─────────────────┘
           │ file + ext
           ▼
┌────────────────────────────┐
│  Parser Layer              │  ← knows Three.js loaders (or web-ifc)
│  (mesh_import_worker.js)   │  ← runs in Web Worker
│  (import_worker.js)        │  ← existing IFC worker
└──────────┬─────────────────┘
           │ parsed scene graph (Three.js Object3D)
           ▼
┌────────────────────────────────────────────────────────────┐
│  ImportFormatToDB  ← THIS MODULE                           │
│  semantic_enrichment.js  +  scene_to_db.js                 │
│                                                            │
│  Input:  parsed scene graph (any format)                   │
│  Output: { elements[], transforms[], geometries[], meta }  │
│          = exact contract for buildImportDBs()             │
│                                                            │
│  PURE FUNCTIONS. No DOM. No Worker API. No IndexedDB.      │
│  Testable in Node.js, browser console, or standalone HTML. │
└──────────┬─────────────────────────────────────────────────┘
           │ data contract
           ▼
┌────────────────────────────┐
│  DB Layer                  │
│  (import_db_builder.js)    │  ← UNCHANGED. Receives same contract from IFC or mesh.
└────────────────────────────┘
```

**Two files, two concerns:**
- `semantic_enrichment.js` — classification logic (name→IFC, material→IFC, Z→storey, GUID gen)
- `scene_to_db.js` — geometry extraction (traverse scene, transform vertices, recenter, package)

Both are pure. Both are testable in isolation. Together they form **ImportFormatToDB**.

---

## File 1: `deploy/dev/semantic_enrichment.js`

### Purpose
Classify dumb geometry nodes into IFC semantics. No geometry processing. No Three.js dependency. Pure string matching + arithmetic.

### API

```javascript
// semantic_enrichment.js — S228: Classify geometry into IFC semantics
// PURE FUNCTIONS. No dependencies. Testable anywhere.

/**
 * Classify a mesh node into an IFC class + discipline.
 * 4-tier cascade: node name → material name → parent name → default.
 * @param {string} nodeName    - mesh node name from scene graph
 * @param {string} materialName - material name (may be null)
 * @param {string} parentName   - parent node name (may be null)
 * @returns {{ ifcClass: string, disc: string }}
 */
function classify(nodeName, materialName, parentName) { ... }

/**
 * Assign a storey name based on Z-elevation (metres).
 * Fixed 3m floor-to-floor banding (residential default).
 * @param {number} elevationZ - world Z coordinate in metres
 * @returns {string} storey name
 */
function classifyStorey(elevationZ) { ... }

/**
 * Generate a deterministic GUID from geometry signature.
 * Same input always produces same output. Prefixed by format.
 * @param {string} prefix     - format prefix ('DAE', 'OBJ', 'GLB', etc.)
 * @param {string} nodeName   - mesh node name
 * @param {number} vertexCount
 * @param {number[]} bboxMin  - [x, y, z]
 * @param {number[]} bboxMax  - [x, y, z]
 * @returns {string} GUID (e.g. 'DAE_a3f8c01b2d4e6f09')
 */
function generateGUID(prefix, nodeName, vertexCount, bboxMin, bboxMax) { ... }

/**
 * Extract RGBA string from Three.js material object.
 * Returns "r,g,b,a" format matching IFC worker output.
 * @param {object} material - Three.js Material (or null)
 * @returns {string} e.g. "0.700,0.300,0.200,1.000"
 */
function extractRGBA(material) { ... }
```

### Classification Tables

```javascript
var NAME_TO_IFC = [
  // Ordered by specificity — first match wins
  // ARC
  { pattern: /\b(exterior.?wall|ext.?wall)\b/i,              ifcClass: 'IfcWall',           disc: 'ARC' },
  { pattern: /\b(interior.?wall|int.?wall|partition)\b/i,     ifcClass: 'IfcWall',           disc: 'ARC' },
  { pattern: /\bwall\b/i,                                     ifcClass: 'IfcWall',           disc: 'ARC' },
  { pattern: /\bdoor\b/i,                                     ifcClass: 'IfcDoor',           disc: 'ARC' },
  { pattern: /\bwindow\b/i,                                   ifcClass: 'IfcWindow',         disc: 'ARC' },
  { pattern: /\b(slab|floor)\b/i,                             ifcClass: 'IfcSlab',           disc: 'ARC' },
  { pattern: /\broof\b/i,                                     ifcClass: 'IfcRoof',           disc: 'ARC' },
  { pattern: /\bceiling\b/i,                                  ifcClass: 'IfcCovering',       disc: 'ARC' },
  { pattern: /\bstair/i,                                      ifcClass: 'IfcStairFlight',    disc: 'ARC' },
  { pattern: /\brailing\b/i,                                  ifcClass: 'IfcRailing',        disc: 'ARC' },
  { pattern: /\bramp\b/i,                                     ifcClass: 'IfcRamp',           disc: 'ARC' },
  { pattern: /\bcurtain.?wall\b/i,                            ifcClass: 'IfcCurtainWall',    disc: 'ARC' },
  // STR
  { pattern: /\bcolumn\b/i,                                   ifcClass: 'IfcColumn',         disc: 'STR' },
  { pattern: /\bbeam\b/i,                                     ifcClass: 'IfcBeam',           disc: 'STR' },
  { pattern: /\b(footing|foundation)\b/i,                     ifcClass: 'IfcFooting',        disc: 'STR' },
  { pattern: /\bpile\b/i,                                     ifcClass: 'IfcPile',           disc: 'STR' },
  // PLB
  { pattern: /\bpipe\b/i,                                     ifcClass: 'IfcPipeSegment',    disc: 'PLB' },
  { pattern: /\b(sink|toilet|basin|shower|bath|faucet)\b/i,   ifcClass: 'IfcSanitaryTerminal', disc: 'PLB' },
  // ACMV
  { pattern: /\bduct\b/i,                                     ifcClass: 'IfcDuctSegment',    disc: 'ACMV' },
  // ELEC
  { pattern: /\b(cable|wire)\b/i,                             ifcClass: 'IfcCableSegment',   disc: 'ELEC' },
  { pattern: /\blight\b/i,                                    ifcClass: 'IfcLightFixture',   disc: 'ELEC' },
  { pattern: /\b(outlet|socket|switch)\b/i,                   ifcClass: 'IfcOutlet',         disc: 'ELEC' },
  { pattern: /\b(appliance|fridge|oven|washer|dryer)\b/i,     ifcClass: 'IfcElectricAppliance', disc: 'ELEC' },
  // FP
  { pattern: /\bsprinkler\b/i,                                ifcClass: 'IfcFireSuppressionTerminal', disc: 'FP' },
  // Furnishing (last — broad patterns)
  { pattern: /\b(furniture|sofa|table|chair|desk|bed|cabinet|shelf)\b/i, ifcClass: 'IfcFurnishingElement', disc: 'ARC' },
];

var MATERIAL_TO_IFC = [
  { pattern: /\bconcrete\b/i,   ifcClass: 'IfcSlab',           disc: 'STR' },
  { pattern: /\bsteel\b/i,      ifcClass: 'IfcBeam',           disc: 'STR' },
  { pattern: /\bbrick\b/i,      ifcClass: 'IfcWall',           disc: 'ARC' },
  { pattern: /\bglass\b/i,      ifcClass: 'IfcWindow',         disc: 'ARC' },
  { pattern: /\bcopper\b/i,     ifcClass: 'IfcPipeSegment',    disc: 'PLB' },
  { pattern: /\bpvc\b/i,        ifcClass: 'IfcPipeSegment',    disc: 'PLB' },
];

var STOREY_BANDS = [
  { min: -Infinity, max: -0.5,      name: 'Basement' },
  { min: -0.5,      max: 3.5,       name: 'Ground Floor' },
  { min: 3.5,       max: 6.5,       name: 'Level 1' },
  { min: 6.5,       max: 9.5,       name: 'Level 2' },
  { min: 9.5,       max: 12.5,      name: 'Level 3' },
  { min: 12.5,      max: Infinity,  name: 'Upper Levels' },
];

var DEFAULT_CLASS = { ifcClass: 'IfcBuildingElementProxy', disc: 'ARC' };
```

### Export

```javascript
if (typeof self !== 'undefined') {
  self.SemanticEnrichment = { classify, classifyStorey, generateGUID, extractRGBA };
}
if (typeof module !== 'undefined') {
  module.exports = { classify, classifyStorey, generateGUID, extractRGBA, NAME_TO_IFC, MATERIAL_TO_IFC };
}
```

---

## File 2: `deploy/dev/scene_to_db.js`

### Purpose
Walk a Three.js scene graph, extract geometry, apply semantic enrichment, return the DB-ready data contract. Depends on `semantic_enrichment.js` only.

### API

```javascript
// scene_to_db.js — S228: Convert parsed 3D scene → DB contract
// Depends: SemanticEnrichment (from semantic_enrichment.js)
// Input:  Three.js scene + metadata
// Output: { elements[], transforms[], geometries[], meta }
//         = exact shape for buildImportDBs(SQL, data)

/**
 * Convert a parsed Three.js scene to the DB contract.
 * @param {THREE.Object3D} scene  - parsed scene from any Three.js loader
 * @param {string} filename       - original filename (e.g. 'house.dae')
 * @param {string} ext            - format extension (e.g. 'dae')
 * @param {object} [options]      - optional overrides
 * @param {boolean} [options.yUpToZUp=true] - apply Y-up → Z-up rotation
 * @param {number}  [options.autoScaleThreshold=500] - mm→m threshold
 * @returns {{ elements, transforms, geometries, meta }}
 */
function sceneToDb(scene, filename, ext, options) { ... }
```

### Core Logic

```javascript
function sceneToDb(scene, filename, ext, options) {
  var opts = options || {};
  var yUpToZUp = (opts.yUpToZUp !== false);  // default true
  var autoScaleThreshold = opts.autoScaleThreshold || 500;
  var prefix = ext.toUpperCase();

  // Y-up → Z-up for formats that need it
  var Y_UP_FORMATS = { dae:1, obj:1, glb:1, gltf:1 };
  if (yUpToZUp && Y_UP_FORMATS[ext]) {
    scene.rotation.x = -Math.PI / 2;
    scene.updateMatrixWorld(true);
  }

  var elements = [], geometries = [], transforms = [];
  var discCounts = {};
  var storeySet = {};
  var meshIndex = 0;

  scene.traverse(function(child) {
    if (!child.isMesh) return;
    var geom = child.geometry;
    if (!geom || !geom.attributes || !geom.attributes.position) return;

    var result = extractMesh(child, prefix, meshIndex);
    if (!result) return;

    elements.push(result.element);
    transforms.push(result.transform);
    geometries.push(result.geometry);

    discCounts[result.element.discipline] = (discCounts[result.element.discipline] || 0) + 1;
    storeySet[result.element.storey] = true;
    meshIndex++;
  });

  return {
    elements: elements,
    geometries: geometries,
    transforms: transforms,
    meta: {
      name: filename.replace(/\.[^.]+$/, ''),
      filename: filename,
      elementCount: elements.length,
      geomCount: geometries.length,
      disciplines: discCounts,
      storeys: Object.keys(storeySet),
      sourceFormat: '.' + ext,
    },
  };
}
```

### Mesh Extraction (single mesh → element + transform + geometry)

```javascript
function extractMesh(mesh, prefix, index) {
  var geom = mesh.geometry;
  var pos = geom.attributes.position;
  var vCount = pos.count;
  if (vCount === 0) return null;

  // World transform
  mesh.updateWorldMatrix(true, false);
  var m = mesh.matrixWorld.elements;

  // Transform vertices to world space
  var worldVerts = new Float32Array(vCount * 3);
  var sumX = 0, sumY = 0, sumZ = 0;
  var minX = Infinity, minY = Infinity, minZ = Infinity;
  var maxX = -Infinity, maxY = -Infinity, maxZ = -Infinity;

  for (var i = 0; i < vCount; i++) {
    var lx = pos.getX(i), ly = pos.getY(i), lz = pos.getZ(i);
    var wx = m[0]*lx + m[4]*ly + m[8]*lz  + m[12];
    var wy = m[1]*lx + m[5]*ly + m[9]*lz  + m[13];
    var wz = m[2]*lx + m[6]*ly + m[10]*lz + m[14];
    worldVerts[i*3] = wx; worldVerts[i*3+1] = wy; worldVerts[i*3+2] = wz;
    sumX += wx; sumY += wy; sumZ += wz;
    if (wx < minX) minX = wx; if (wx > maxX) maxX = wx;
    if (wy < minY) minY = wy; if (wy > maxY) maxY = wy;
    if (wz < minZ) minZ = wz; if (wz > maxZ) maxZ = wz;
  }

  // Centroid
  var cx = sumX / vCount, cy = sumY / vCount, cz = sumZ / vCount;

  // Auto-scale (mm → m)
  var maxCoord = Math.max(Math.abs(maxX), Math.abs(maxY), Math.abs(maxZ));
  if (maxCoord > 500) {
    var s = 0.001;
    cx *= s; cy *= s; cz *= s;
    for (var j = 0; j < worldVerts.length; j++) worldVerts[j] *= s;
    minX *= s; minY *= s; minZ *= s;
    maxX *= s; maxY *= s; maxZ *= s;
  }

  // Re-center at origin
  var centered = new Float32Array(vCount * 3);
  for (var j = 0; j < vCount; j++) {
    centered[j*3]   = worldVerts[j*3]   - cx;
    centered[j*3+1] = worldVerts[j*3+1] - cy;
    centered[j*3+2] = worldVerts[j*3+2] - cz;
  }

  // Faces
  var indices;
  if (geom.index) {
    indices = new Int32Array(geom.index.array);
  } else {
    indices = new Int32Array(vCount);
    for (var j = 0; j < vCount; j++) indices[j] = j;
  }

  // Semantic enrichment
  var SE = self.SemanticEnrichment;
  var nodeName   = mesh.name || 'unnamed';
  var parentName = mesh.parent ? (mesh.parent.name || '') : '';
  var matObj     = Array.isArray(mesh.material) ? mesh.material[0] : mesh.material;
  var matName    = matObj ? (matObj.name || '') : '';
  var cls        = SE.classify(nodeName, matName, parentName);
  var storey     = SE.classifyStorey(cz);
  var rgba       = SE.extractRGBA(mesh.material);
  var guid       = SE.generateGUID(prefix, nodeName, vCount, [minX,minY,minZ], [maxX,maxY,maxZ]);
  var displayName = (nodeName !== 'unnamed') ? nodeName : (matName || 'Element_' + index);

  return {
    element:   { guid: guid, ifcClass: cls.ifcClass, name: displayName, storey: storey, discipline: cls.disc, material: rgba },
    transform: { guid: guid, cx: cx, cy: cy, cz: cz, rx: 0, ry: 0, rz: 0 },
    geometry:  { guid: guid, geomHash: guid, vertices: centered.buffer, indices: indices.buffer },
  };
}
```

### Export

```javascript
if (typeof self !== 'undefined') {
  self.SceneToDb = { sceneToDb: sceneToDb, extractMesh: extractMesh };
}
if (typeof module !== 'undefined') {
  module.exports = { sceneToDb, extractMesh };
}
```

---

## Test Plan: `deploy/dev/test/test_import_format_to_db.html`

Standalone HTML. Loads only `semantic_enrichment.js` + `scene_to_db.js` + Three.js core. No workers, no sql.js, no IndexedDB.

### Test 1: Semantic Enrichment (pure string logic)

```javascript
var SE = self.SemanticEnrichment;

// Name classification
assertEq(SE.classify('Exterior_Wall_01', null, null).ifcClass, 'IfcWall');
assertEq(SE.classify('Front_Door', null, null).ifcClass, 'IfcDoor');
assertEq(SE.classify('Window_Bay', null, null).ifcClass, 'IfcWindow');
assertEq(SE.classify('Roof_Main', null, null).ifcClass, 'IfcRoof');
assertEq(SE.classify('Ground_Floor_Slab', null, null).ifcClass, 'IfcSlab');
assertEq(SE.classify('Column_A1', null, null).ifcClass, 'IfcColumn');
assertEq(SE.classify('Column_A1', null, null).disc, 'STR');
assertEq(SE.classify('Light_Kitchen', null, null).ifcClass, 'IfcLightFixture');

// Fallback → IfcBuildingElementProxy
assertEq(SE.classify('geo_54', null, null).ifcClass, 'IfcBuildingElementProxy');
assertEq(SE.classify('geo_54', null, null).disc, 'ARC');

// Material fallback
assertEq(SE.classify('geo_54', 'Concrete_Grey', null).ifcClass, 'IfcSlab');
assertEq(SE.classify('geo_54', 'Glass_Clear', null).ifcClass, 'IfcWindow');

// Parent fallback
assertEq(SE.classify('geo_54', null, 'Wall_Group').ifcClass, 'IfcWall');

// Cascade: node wins over material
assertEq(SE.classify('Door_Front', 'Steel_Frame', null).ifcClass, 'IfcDoor');  // not IfcBeam

// Storey banding
assertEq(SE.classifyStorey(-2.0), 'Basement');
assertEq(SE.classifyStorey(0.0), 'Ground Floor');
assertEq(SE.classifyStorey(1.5), 'Ground Floor');
assertEq(SE.classifyStorey(4.5), 'Level 1');
assertEq(SE.classifyStorey(7.0), 'Level 2');
assertEq(SE.classifyStorey(15.0), 'Upper Levels');

// GUID determinism
var g1 = SE.generateGUID('DAE', 'Wall', 100, [0,0,0], [5,3,0.2]);
var g2 = SE.generateGUID('DAE', 'Wall', 100, [0,0,0], [5,3,0.2]);
assertEq(g1, g2);  // deterministic
assert(g1.startsWith('DAE_'));

// GUID uniqueness
var g3 = SE.generateGUID('DAE', 'Door', 100, [0,0,0], [1,2,0.05]);
assert(g1 !== g3);

// RGBA
assertEq(SE.extractRGBA(null), '0.700,0.700,0.700,1.000');
```

### Test 2: Scene-to-DB (geometry extraction)

```javascript
var STD = self.SceneToDb;

// Build a minimal Three.js scene programmatically
var scene = new THREE.Group();

// Wall: box at z=1.5 (Ground Floor)
var wallGeom = new THREE.BoxGeometry(5, 3, 0.2);
var wallMat  = new THREE.MeshBasicMaterial({ color: 0x884422 });
var wall     = new THREE.Mesh(wallGeom, wallMat);
wall.name    = 'Exterior_Wall_North';
wall.position.set(0, 1.5, 0);
scene.add(wall);

// Door: box at z=1.0 (Ground Floor)
var doorGeom = new THREE.BoxGeometry(0.9, 2.1, 0.05);
var doorMat  = new THREE.MeshBasicMaterial({ color: 0x443322 });
var door     = new THREE.Mesh(doorGeom, doorMat);
door.name    = 'Front_Door';
door.position.set(2, 1.05, 0.1);
scene.add(door);

// Roof: box at z=6.0 (Level 1)
var roofGeom = new THREE.BoxGeometry(6, 0.3, 4);
var roofMat  = new THREE.MeshBasicMaterial({ color: 0x882222 });
var roof     = new THREE.Mesh(roofGeom, roofMat);
roof.name    = 'Roof';
roof.position.set(0, 6.0, 0);
scene.add(roof);

// Generic unnamed: fallback
var genGeom = new THREE.BoxGeometry(1, 1, 1);
var gen     = new THREE.Mesh(genGeom);
gen.name    = 'geo_54';
gen.position.set(3, 0.5, 2);
scene.add(gen);

// Run conversion (skip Y-up rotation for this test — already Z-up)
var result = STD.sceneToDb(scene, 'test_house.dae', 'dae', { yUpToZUp: false });

// Verify output contract shape
assertEq(result.elements.length, 4);
assertEq(result.transforms.length, 4);
assertEq(result.geometries.length, 4);
assertEq(result.meta.elementCount, 4);
assertEq(result.meta.sourceFormat, '.dae');

// Verify classification
var wallEl = result.elements.find(e => e.name === 'Exterior_Wall_North');
assertEq(wallEl.ifcClass, 'IfcWall');
assertEq(wallEl.discipline, 'ARC');

var doorEl = result.elements.find(e => e.name === 'Front_Door');
assertEq(doorEl.ifcClass, 'IfcDoor');

var roofEl = result.elements.find(e => e.name === 'Roof');
assertEq(roofEl.ifcClass, 'IfcRoof');

var genEl = result.elements.find(e => e.name.startsWith('geo_54') || e.name.startsWith('Element_'));
assertEq(genEl.ifcClass, 'IfcBuildingElementProxy');

// Verify storey assignment
assertEq(wallEl.storey, 'Ground Floor');
assertEq(roofEl.storey, 'Level 1');

// Verify GUIDs are unique
var guids = result.elements.map(e => e.guid);
assertEq(new Set(guids).size, guids.length);

// Verify geometry has data
for (var g of result.geometries) {
  assert(g.vertices.byteLength > 0, 'vertices not empty');
  assert(g.indices.byteLength > 0, 'indices not empty');
}

// Verify disciplines in meta
assert(result.meta.disciplines.ARC >= 3);

// Verify it feeds buildImportDBs without error
// (if sql.js is loaded, can test end-to-end here too)
```

### Test 3: Round-trip — sceneToDb → buildImportDBs → SQL queries

```javascript
// Optional: loads sql.js + import_db_builder.js too
var SQL = await initSqlJs({ locateFile: f => 'https://sql.js.org/dist/' + f });
var dbs = buildImportDBs(SQL, result);
assert(dbs.extractedDb.byteLength > 0);

// Open and query
var db = new SQL.Database(new Uint8Array(dbs.extractedDb));
var count = db.exec('SELECT COUNT(*) FROM elements_meta')[0].values[0][0];
assertEq(count, 4);

var walls = db.exec("SELECT guid, ifc_class FROM elements_meta WHERE ifc_class='IfcWall'");
assertEq(walls[0].values.length, 1);

var storeys = db.exec("SELECT DISTINCT storey FROM elements_meta WHERE storey IS NOT NULL");
assert(storeys[0].values.length >= 2);  // Ground Floor + Level 1

db.close();
```

---

## Execution Order

1. **Write `semantic_enrichment.js`** — pure functions, zero deps
2. **Write test_import_format_to_db.html Test 1** — verify classification logic
3. **Write `scene_to_db.js`** — depends on semantic_enrichment.js + Three.js types only
4. **Write Test 2** — verify scene traversal + output contract
5. **Write Test 3** — verify round-trip to DB
6. **Then** (separate prompt): wire into worker + UI (the plumbing in S228_drop_zone_multi_format.md)

## File Manifest

| File | Action | Depends on |
|------|--------|------------|
| `deploy/dev/semantic_enrichment.js` | NEW | nothing |
| `deploy/dev/scene_to_db.js` | NEW | semantic_enrichment.js |
| `deploy/dev/test/test_import_format_to_db.html` | NEW | both above + Three.js CDN + optionally sql.js + import_db_builder.js |

No existing files modified. This is a standalone, testable layer.
