# S228 — Drop Zone Multi-Format Import
# ⚠ DO NOT REMOVE — Scope: DAE/OBJ/GLB/3DS/FBX/STL import into BIM OOTB viewer. Read the log after every run.

## Spec
`internal/DROP_ZONE_MULTI_FORMAT_SRS.md` — full architecture, format notes, strategic context.

## Principle
All non-IFC formats arrive at the same IFC-centric DB schema (`elements_meta`, `element_transforms`, `element_instances`, `component_geometries`). The DB schema IS IFC. The Drop Zone is an IFC on-ramp.

## Pipeline Position
```
Source (DAE/OBJ/GLB/...)  →  Stage 1: EXTRACT to input DB  ←  THIS TASK
                          →  Stage 2: BOM + DAGCompiler     (future, format-agnostic)
                          →  Rosetta Stone gates             (future, proves Stage 2)
```

---

## Part A — Format Router (modify `deploy/dev/import.js`)

### A.1 Replace `.ifc`-only filter with format detection

Current code (lines 285, 301):
```javascript
if (file && /\.ifc$/i.test(file.name)) {
```

Replace with format router:
```javascript
// S228: Multi-format detection
const FORMAT_ROUTES = {
  'ifc':  'ifc',
  'dae':  'mesh',
  'obj':  'mesh',
  'glb':  'mesh',
  'gltf': 'mesh',
  '3ds':  'mesh',
  'fbx':  'mesh',
  'stl':  'mesh',
};

function detectFormat(filename) {
  const ext = filename.split('.').pop().toLowerCase();
  return { ext: ext, route: FORMAT_ROUTES[ext] || null };
}
```

### A.2 Routing in drop handler + file picker

```javascript
// In drop handler and fileInput.change handler:
const { ext, route } = detectFormat(file.name);
if (route === 'ifc') {
  A.importIFC(file);
} else if (route === 'mesh') {
  A.importMesh(file, ext);
} else {
  document.getElementById('import-status').textContent =
    'Unsupported: .' + ext + ' — Accepted: IFC, DAE, OBJ, GLB, 3DS, FBX, STL';
}
```

### A.3 Update file input accept attribute

Current: `accept=".ifc"`
Change to: `accept=".ifc,.dae,.obj,.glb,.gltf,.3ds,.fbx,.stl"`

### A.4 Update drop zone label text

Show accepted formats in the drop zone hint.

### A.5 Building name strip

Current `import_db_builder.js` line 14 strips `.ifc` only:
```javascript
var buildingName = (data.meta.filename || data.meta.name || 'Import').replace(/\.ifc$/i, '');
```
Change to strip any known extension:
```javascript
var buildingName = (data.meta.filename || data.meta.name || 'Import').replace(/\.(ifc|dae|obj|glb|gltf|3ds|fbx|stl)$/i, '');
```

---

## Part B — Semantic Enrichment Module (NEW: `deploy/dev/semantic_enrichment.js`)

Standalone module, no dependencies. Used by mesh worker. Testable in isolation.

### B.1 Name-to-IFC Classification Table

```javascript
// semantic_enrichment.js — S228: Classify dumb geometry into IFC semantics
// No dependencies. Pure functions. Used by mesh_import_worker.js.

const NAME_TO_IFC = [
  // Ordered by specificity — first match wins
  // Architectural
  { pattern: /\b(exterior.?wall|ext.?wall)\b/i,    ifcClass: 'IfcWall',           disc: 'ARC' },
  { pattern: /\b(interior.?wall|int.?wall|partition)\b/i, ifcClass: 'IfcWall',    disc: 'ARC' },
  { pattern: /\bwall\b/i,                           ifcClass: 'IfcWall',           disc: 'ARC' },
  { pattern: /\bdoor\b/i,                           ifcClass: 'IfcDoor',           disc: 'ARC' },
  { pattern: /\bwindow\b/i,                         ifcClass: 'IfcWindow',         disc: 'ARC' },
  { pattern: /\b(slab|floor)\b/i,                   ifcClass: 'IfcSlab',           disc: 'ARC' },
  { pattern: /\broof\b/i,                           ifcClass: 'IfcRoof',           disc: 'ARC' },
  { pattern: /\bceiling\b/i,                        ifcClass: 'IfcCovering',       disc: 'ARC' },
  { pattern: /\bstair/i,                            ifcClass: 'IfcStairFlight',    disc: 'ARC' },
  { pattern: /\brailing\b/i,                        ifcClass: 'IfcRailing',        disc: 'ARC' },
  { pattern: /\bramp\b/i,                           ifcClass: 'IfcRamp',           disc: 'ARC' },
  { pattern: /\bcurtain.?wall\b/i,                  ifcClass: 'IfcCurtainWall',    disc: 'ARC' },
  // Structural
  { pattern: /\bcolumn\b/i,                         ifcClass: 'IfcColumn',         disc: 'STR' },
  { pattern: /\bbeam\b/i,                           ifcClass: 'IfcBeam',           disc: 'STR' },
  { pattern: /\b(footing|foundation)\b/i,           ifcClass: 'IfcFooting',        disc: 'STR' },
  { pattern: /\bpile\b/i,                           ifcClass: 'IfcPile',           disc: 'STR' },
  // MEP
  { pattern: /\bpipe\b/i,                           ifcClass: 'IfcPipeSegment',    disc: 'PLB' },
  { pattern: /\bduct\b/i,                           ifcClass: 'IfcDuctSegment',    disc: 'ACMV' },
  { pattern: /\b(cable|wire)\b/i,                   ifcClass: 'IfcCableSegment',   disc: 'ELEC' },
  { pattern: /\blight\b/i,                          ifcClass: 'IfcLightFixture',   disc: 'ELEC' },
  { pattern: /\b(outlet|socket|switch)\b/i,         ifcClass: 'IfcOutlet',         disc: 'ELEC' },
  { pattern: /\b(sink|toilet|basin|shower|bath|faucet)\b/i, ifcClass: 'IfcSanitaryTerminal', disc: 'PLB' },
  { pattern: /\bsprinkler\b/i,                      ifcClass: 'IfcFireSuppressionTerminal', disc: 'FP' },
  // Furnishing
  { pattern: /\b(furniture|sofa|table|chair|desk|bed|cabinet|shelf)\b/i, ifcClass: 'IfcFurnishingElement', disc: 'ARC' },
  { pattern: /\b(appliance|fridge|oven|washer|dryer)\b/i, ifcClass: 'IfcElectricAppliance', disc: 'ELEC' },
];

const MATERIAL_TO_IFC = [
  { pattern: /\bconcrete\b/i,   ifcClass: 'IfcSlab',           disc: 'STR' },
  { pattern: /\bsteel\b/i,      ifcClass: 'IfcBeam',           disc: 'STR' },
  { pattern: /\bbrick\b/i,      ifcClass: 'IfcWall',           disc: 'ARC' },
  { pattern: /\bglass\b/i,      ifcClass: 'IfcWindow',         disc: 'ARC' },
  { pattern: /\bcopper\b/i,     ifcClass: 'IfcPipeSegment',    disc: 'PLB' },
  { pattern: /\bpvc\b/i,        ifcClass: 'IfcPipeSegment',    disc: 'PLB' },
];

const DEFAULT_CLASS = { ifcClass: 'IfcBuildingElementProxy', disc: 'ARC' };

function classifyByName(nodeName) {
  for (const rule of NAME_TO_IFC) {
    if (rule.pattern.test(nodeName)) return { ifcClass: rule.ifcClass, disc: rule.disc };
  }
  return null;
}

function classifyByMaterial(materialName) {
  if (!materialName) return null;
  for (const rule of MATERIAL_TO_IFC) {
    if (rule.pattern.test(materialName)) return { ifcClass: rule.ifcClass, disc: rule.disc };
  }
  return null;
}

// 4-tier cascade: node name → material → parent name → default
function classify(nodeName, materialName, parentName) {
  return classifyByName(nodeName)
      || classifyByMaterial(materialName)
      || (parentName ? classifyByName(parentName) : null)
      || DEFAULT_CLASS;
}
```

### B.2 Storey Assignment by Elevation Banding

```javascript
const STOREY_BANDS = [
  { min: -Infinity, max: -0.5,     name: 'Basement' },
  { min: -0.5,      max: 3.5,      name: 'Ground Floor' },
  { min: 3.5,       max: 6.5,      name: 'Level 1' },
  { min: 6.5,       max: 9.5,      name: 'Level 2' },
  { min: 9.5,       max: 12.5,     name: 'Level 3' },
  { min: 12.5,      max: Infinity,  name: 'Upper Levels' },
];

function classifyStorey(elevationZ) {
  for (const band of STOREY_BANDS) {
    if (elevationZ >= band.min && elevationZ < band.max) return band.name;
  }
  return 'Unknown';
}
```

### B.3 Deterministic GUID Generation

```javascript
// Simple hash — no crypto dependency needed in worker
function hashStr(s) {
  var h = 0;
  for (var i = 0; i < s.length; i++) {
    h = ((h << 5) - h + s.charCodeAt(i)) | 0;
  }
  // Convert to unsigned hex
  return (h >>> 0).toString(16).padStart(8, '0');
}

// Deterministic: same file always produces same GUIDs
function generateGUID(prefix, nodeName, vertexCount, bboxMin, bboxMax) {
  var sig = nodeName + '|' + vertexCount +
    '|' + bboxMin[0].toFixed(4) + ',' + bboxMin[1].toFixed(4) + ',' + bboxMin[2].toFixed(4) +
    '|' + bboxMax[0].toFixed(4) + ',' + bboxMax[1].toFixed(4) + ',' + bboxMax[2].toFixed(4);
  return prefix.toUpperCase() + '_' + hashStr(sig) + hashStr(sig + '_salt');
}
```

### B.4 Color Extraction Helper

```javascript
// Extract RGBA string from Three.js material (matches IFC worker format: "r,g,b,a")
function extractRGBA(material) {
  if (!material) return '0.700,0.700,0.700,1.000';
  var mat = Array.isArray(material) ? material[0] : material;
  if (mat && mat.color) {
    var c = mat.color;
    var a = (mat.opacity !== undefined) ? mat.opacity : 1.0;
    return c.r.toFixed(3) + ',' + c.g.toFixed(3) + ',' + c.b.toFixed(3) + ',' + a.toFixed(3);
  }
  return '0.700,0.700,0.700,1.000';
}
```

### B.5 Exports (for worker `importScripts`)

```javascript
// Expose for mesh_import_worker.js (loaded via importScripts in worker context)
if (typeof self !== 'undefined') {
  self.SemanticEnrichment = {
    classify:        classify,
    classifyStorey:  classifyStorey,
    generateGUID:    generateGUID,
    extractRGBA:     extractRGBA,
    NAME_TO_IFC:     NAME_TO_IFC,      // exposed for unit test
    MATERIAL_TO_IFC: MATERIAL_TO_IFC,  // exposed for unit test
  };
}
```

---

## Part C — Mesh Import Worker (NEW: `deploy/dev/mesh_import_worker.js`)

### C.1 Loader CDN Setup

```javascript
// mesh_import_worker.js — S228: Parse non-IFC 3D files via Three.js loaders
// Input:  postMessage({ arrayBuffer, filename, ext })
// Output: same contract as import_worker.js (IFC worker)
//         { type: 'done', meta, elements, geometries, transforms }

importScripts('semantic_enrichment.js');

// Three.js core (ES module build for workers)
// Note: ColladaLoader needs DOMParser — available in modern workers (Chrome 76+, Firefox 65+)
var THREE_CDN = 'https://unpkg.com/three@0.170.0/build/three.cjs';
var LOADER_CDN = 'https://unpkg.com/three@0.170.0/examples/jsm/loaders/';
```

### C.2 Loader Selection

```javascript
var LOADER_MAP = {
  'dae':  { module: 'ColladaLoader.js',  className: 'ColladaLoader' },
  'obj':  { module: 'OBJLoader.js',      className: 'OBJLoader' },
  'glb':  { module: 'GLTFLoader.js',     className: 'GLTFLoader' },
  'gltf': { module: 'GLTFLoader.js',     className: 'GLTFLoader' },
  '3ds':  { module: 'TDSLoader.js',      className: 'TDSLoader' },
  'fbx':  { module: 'FBXLoader.js',      className: 'FBXLoader' },
  'stl':  { module: 'STLLoader.js',      className: 'STLLoader' },
};
```

### C.3 Scene Graph Traversal (core logic)

```javascript
function traverseScene(scene, ext, filename) {
  var elements = [], geometries = [], transforms = [];
  var prefix = ext.toUpperCase();  // DAE_, OBJ_, GLB_, etc.
  var discCounts = {};
  var storeySet = {};

  scene.traverse(function(child) {
    if (!child.isMesh) return;
    var geom = child.geometry;
    if (!geom || !geom.attributes || !geom.attributes.position) return;

    var pos = geom.attributes.position;

    // World matrix
    child.updateWorldMatrix(true, false);
    var matrix = child.matrixWorld;

    // Transform vertices to world coordinates
    var vCount = pos.count;
    var worldVerts = new Float32Array(vCount * 3);
    var minX = Infinity, minY = Infinity, minZ = Infinity;
    var maxX = -Infinity, maxY = -Infinity, maxZ = -Infinity;
    var sumX = 0, sumY = 0, sumZ = 0;

    for (var i = 0; i < vCount; i++) {
      var lx = pos.getX(i), ly = pos.getY(i), lz = pos.getZ(i);
      var m = matrix.elements;
      // Apply 4x4 matrix
      var wx = m[0]*lx + m[4]*ly + m[8]*lz  + m[12];
      var wy = m[1]*lx + m[5]*ly + m[9]*lz  + m[13];
      var wz = m[2]*lx + m[6]*ly + m[10]*lz + m[14];
      worldVerts[i*3]   = wx;
      worldVerts[i*3+1] = wy;
      worldVerts[i*3+2] = wz;
      sumX += wx; sumY += wy; sumZ += wz;
      if (wx < minX) minX = wx; if (wx > maxX) maxX = wx;
      if (wy < minY) minY = wy; if (wy > maxY) maxY = wy;
      if (wz < minZ) minZ = wz; if (wz > maxZ) maxZ = wz;
    }

    // Centroid
    var cx = sumX / vCount, cy = sumY / vCount, cz = sumZ / vCount;

    // Auto-scale: if max coord > 500m, assume mm → m
    var maxCoord = Math.max(Math.abs(maxX), Math.abs(maxY), Math.abs(maxZ));
    var scale = (maxCoord > 500) ? 0.001 : 1.0;
    if (scale !== 1.0) {
      cx *= scale; cy *= scale; cz *= scale;
      for (var j = 0; j < worldVerts.length; j++) worldVerts[j] *= scale;
      minX *= scale; minY *= scale; minZ *= scale;
      maxX *= scale; maxY *= scale; maxZ *= scale;
    }

    // Re-center at origin (same as IFC worker)
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
    var nodeName   = child.name || 'unnamed';
    var parentName = child.parent ? (child.parent.name || '') : '';
    var matName    = child.material ? (Array.isArray(child.material) ? child.material[0]?.name : child.material.name) : '';
    var cls        = self.SemanticEnrichment.classify(nodeName, matName, parentName);
    var storey     = self.SemanticEnrichment.classifyStorey(cz);  // Z = elevation in IFC coords
    var rgba       = self.SemanticEnrichment.extractRGBA(child.material);
    var guid       = self.SemanticEnrichment.generateGUID(prefix, nodeName, vCount,
                       [minX, minY, minZ], [maxX, maxY, maxZ]);
    var geomHash   = guid;  // 1:1 for now (no instancing dedup yet)

    elements.push({
      guid: guid,
      ifcClass: cls.ifcClass,
      name: nodeName !== 'unnamed' ? nodeName : (matName || 'Element ' + elements.length),
      storey: storey,
      discipline: cls.disc,
      material: rgba,
    });

    transforms.push({ guid: guid, cx: cx, cy: cy, cz: cz, rx: 0, ry: 0, rz: 0 });

    geometries.push({
      guid: guid,
      geomHash: geomHash,
      vertices: centered.buffer,
      indices: indices.buffer,
    });

    // Counts
    discCounts[cls.disc] = (discCounts[cls.disc] || 0) + 1;
    storeySet[storey] = true;
  });

  return {
    elements: elements,
    geometries: geometries,
    transforms: transforms,
    meta: {
      name:         filename.replace(/\.[^.]+$/, ''),
      filename:     filename,
      elementCount: elements.length,
      geomCount:    geometries.length,
      disciplines:  discCounts,
      storeys:      Object.keys(storeySet),
      sourceFormat: '.' + ext,
    },
  };
}
```

### C.4 Coordinate System Handling

```javascript
// Y-up → Z-up swap (for DAE/OBJ/GLB/FBX with Y-up convention)
// Applied to the scene root BEFORE traversal
function applyUpAxisCorrection(scene, ext) {
  // Formats with Y-up convention that need rotation to Z-up (IFC convention)
  var Y_UP_FORMATS = { 'dae': true, 'obj': true, 'glb': true, 'gltf': true };
  if (Y_UP_FORMATS[ext]) {
    // Rotate -90 degrees around X axis: Y-up → Z-up
    scene.rotation.x = -Math.PI / 2;
    scene.updateMatrixWorld(true);
  }
  // 3DS and STL are already Z-up. FBX: Three.js FBXLoader auto-corrects.
}
```

### C.5 Worker Message Handler (entry point)

```javascript
self.onmessage = async function(e) {
  var data = e.data;
  var arrayBuffer = data.arrayBuffer;
  var filename = data.filename;
  var ext = data.ext;

  try {
    postProgress(5, 'Loading 3D engine...');

    // Load Three.js and appropriate loader
    // (Implementation depends on whether importScripts or dynamic import is used —
    //  see §C.1 spike note in SRS §12.4 about DOMParser in workers)

    postProgress(20, 'Parsing ' + ext.toUpperCase() + ' file...');
    // Parse file with selected loader → scene
    // var scene = await parseFile(arrayBuffer, ext);

    postProgress(40, 'Extracting geometry...');
    // applyUpAxisCorrection(scene, ext);

    postProgress(50, 'Classifying elements...');
    // var result = traverseScene(scene, ext, filename);

    postProgress(90, 'Packaging...');

    // Transfer ArrayBuffers for zero-copy
    var transferables = [];
    // for (var g of result.geometries) {
    //   transferables.push(g.vertices, g.indices);
    // }

    // postMessage({ type: 'done', ...result }, transferables);

  } catch(err) {
    postMessage({ type: 'error', message: err.message || String(err) });
  }
};

function postProgress(pct, phase) {
  postMessage({ type: 'progress', pct: pct, phase: phase });
}
```

### C.6 Technical Spike Required: Three.js in Web Worker

Three.js loaders in a Web Worker have constraints:
- **ColladaLoader** needs `DOMParser` — available in Chrome 76+, Firefox 65+, Safari 15+
- **FBXLoader** needs `TextDecoder` + binary parsing — should work
- **GLTFLoader** needs `TextDecoder` — should work
- **STLLoader** — pure binary, no DOM needed

**Spike task:** Before implementing, test ColladaLoader in a worker with `DOMParser`. If it fails in target browsers, fallback: parse on main thread, transfer result.

---

## Part D — Wire `importMesh` into `import.js`

### D.1 New method on A (parallels `A.importIFC`)

```javascript
A.importMesh = async function(file, ext) {
  var status = document.getElementById('import-status');
  var progressBar = document.getElementById('import-progress-bar');
  if (status) status.textContent = 'Reading ' + ext.toUpperCase() + ' file...';
  if (progressBar) { progressBar.style.width = '0%'; progressBar.parentElement.style.display = 'block'; }

  var sizeMB = (file.size / 1024 / 1024).toFixed(1);
  console.log('[S228] §MESH_IMPORT_START file=' + file.name + ' ext=' + ext + ' size=' + sizeMB + 'MB');

  var arrayBuffer = await file.arrayBuffer();

  return new Promise(function(resolve, reject) {
    var workerUrl = new URL('mesh_import_worker.js?v=1', location.href).href;
    var worker = new Worker(workerUrl);

    worker.onmessage = async function(e) {
      var msg = e.data;
      if (msg.type === 'progress') {
        if (status) status.textContent = msg.phase;
        if (progressBar) progressBar.style.width = msg.pct + '%';
        return;
      }
      if (msg.type === 'error') {
        console.log('[S228] §MESH_IMPORT_ERROR ' + msg.message);
        if (status) status.textContent = 'Import failed: ' + msg.message;
        if (progressBar) progressBar.style.background = '#cc4444';
        worker.terminate();
        reject(new Error(msg.message));
        return;
      }
      if (msg.type === 'done') {
        if (status) status.textContent = 'Building database...';
        console.log('[S228] §MESH_PARSED elements=' + msg.meta.elementCount +
          ' geom=' + msg.meta.geomCount + ' format=' + msg.meta.sourceFormat);

        try {
          var SQL = await initSqlJs({ locateFile: function(f) { return 'https://sql.js.org/dist/' + f; } });
          var dbs = buildImportDBs(SQL, msg);

          var record = {
            meta: msg.meta,
            extractedDb: dbs.extractedDb,
            libraryDb: dbs.extractedDb,
          };
          await saveImport(file.name, record);

          console.log('[S228] §MESH_SAVED key=' + file.name +
            ' db=' + (dbs.extractedDb.byteLength / 1024).toFixed(0) + 'KB');

          if (status) status.textContent = 'Imported ' + msg.meta.elementCount + ' elements from ' + ext.toUpperCase();
          if (progressBar) { progressBar.style.width = '100%'; progressBar.style.background = '#44cc44'; }
          if (A.renderImportCards) A.renderImportCards();

          worker.terminate();
          resolve(record);
        } catch(dbErr) {
          console.log('[S228] §MESH_DB_ERROR ' + dbErr.message);
          if (status) status.textContent = 'DB build failed: ' + dbErr.message;
          worker.terminate();
          reject(dbErr);
        }
      }
    };

    worker.onerror = function(err) {
      console.log('[S228] §MESH_WORKER_ERROR ' + err.message);
      if (status) status.textContent = 'Worker error: ' + err.message;
      worker.terminate();
      reject(err);
    };

    worker.postMessage({ arrayBuffer: arrayBuffer, filename: file.name, ext: ext }, [arrayBuffer]);
  });
};
```

### D.2 Card display: strip any extension (not just .ifc)

Line 243 of current import.js:
```javascript
var displayName = (item.meta.filename || item.meta.name || '').replace(/\.ifc$/i, '');
```
Change to:
```javascript
var displayName = (item.meta.filename || item.meta.name || '').replace(/\.(ifc|dae|obj|glb|gltf|3ds|fbx|stl)$/i, '');
```

### D.3 Card: show source format badge

After the element count line, add:
```javascript
var formatBadge = (meta.sourceFormat && meta.sourceFormat !== '.ifc')
  ? ' <span style="background:rgba(79,195,247,0.15);padding:2px 6px;border-radius:4px;font-size:11px">'
    + meta.sourceFormat.toUpperCase().replace('.','') + '</span>'
  : '';
```

---

## Part E — Testing

### E.1 Test DAE file

Create a minimal hand-crafted DAE (`deploy/dev/test/sample_house.dae`) with these named nodes:
- `Exterior_Wall_North`, `Exterior_Wall_South` → should classify as IfcWall/ARC
- `Ground_Floor_Slab` → IfcSlab/ARC
- `Roof` → IfcRoof/ARC
- `Front_Door` → IfcDoor/ARC
- `Window_01`, `Window_02` → IfcWindow/ARC
- `Foundation` → IfcFooting/STR
- `Light_Fixture_01` → IfcLightFixture/ELEC
- `Generic_Object` → IfcBuildingElementProxy/ARC (fallback)

Each node: simple box geometry at different Z heights to test storey banding.

### E.2 Verification checklist (from SRS §9.2)

```
§9.2-COUNT    SELECT COUNT(*) FROM elements_meta = 10 (mesh count)
§9.2-GUID     SELECT COUNT(DISTINCT guid) = COUNT(*)
§9.2-RENDER   All elements visible in viewer
§9.2-STOREY   Panel shows Ground Floor (z<3.5m elements)
§9.2-DISC     Panel shows ARC + STR + ELEC
§9.2-PICK     Click element → info panel shows name + ifc_class
§9.2-CLASS    Exterior_Wall_North → IfcWall
§9.2-FALLBACK Generic_Object → IfcBuildingElementProxy
§9.2-COLOR    Colored materials show correct RGBA
§9.2-SCALE    Coordinates in metres
```

### E.3 Semantic enrichment unit test (`deploy/dev/test/test_semantic.html`)

Standalone HTML page that loads `semantic_enrichment.js` and runs assertions:

```javascript
// Test classify()
assert(classify('Exterior_Wall_01', null, null).ifcClass === 'IfcWall');
assert(classify('Front_Door', null, null).ifcClass === 'IfcDoor');
assert(classify('Window_Bay', null, null).ifcClass === 'IfcWindow');
assert(classify('geo_54', null, null).ifcClass === 'IfcBuildingElementProxy');  // fallback
assert(classify('geo_54', 'concrete', null).ifcClass === 'IfcSlab');           // material fallback
assert(classify('geo_54', null, 'Wall_Group').ifcClass === 'IfcWall');         // parent fallback

// Test classifyStorey()
assert(classifyStorey(-1.0) === 'Basement');
assert(classifyStorey(1.5)  === 'Ground Floor');
assert(classifyStorey(4.5)  === 'Level 1');
assert(classifyStorey(15.0) === 'Upper Levels');

// Test generateGUID() determinism
var g1 = generateGUID('DAE', 'Wall', 100, [0,0,0], [5,3,0.2]);
var g2 = generateGUID('DAE', 'Wall', 100, [0,0,0], [5,3,0.2]);
assert(g1 === g2, 'Same input → same GUID');
assert(g1.startsWith('DAE_'), 'GUID prefix');
```

---

## File Manifest

| File | Action | Part |
|------|--------|------|
| `deploy/dev/import.js` | MODIFY — format router + `importMesh` method | A, D |
| `deploy/dev/import_db_builder.js` | MINOR — strip multi-format extensions | A.5 |
| `deploy/dev/semantic_enrichment.js` | NEW — classification engine | B |
| `deploy/dev/mesh_import_worker.js` | NEW — Three.js mesh parser worker | C |
| `deploy/dev/test/sample_house.dae` | NEW — test fixture | E.1 |
| `deploy/dev/test/test_semantic.html` | NEW — unit tests for enrichment | E.3 |

## Execution Order

1. **Part B first** — `semantic_enrichment.js` is standalone, testable in isolation. Write it, test it with E.3.
2. **Part A** — Format router in `import.js`. Minimal change, enables routing.
3. **Part C** — Mesh worker. Start with ColladaLoader only (spike DOMParser in worker first).
4. **Part D** — Wire `importMesh`, card updates.
5. **Part E** — Test DAE, end-to-end verification.

## Blocked On

- **Gerard's HDP DAE export** — needed to tune NAME_TO_IFC patterns for HDP naming (`geo_54`?)
- **Three.js worker spike** — ColladaLoader + DOMParser in worker (§C.6)
