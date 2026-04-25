// scene_to_db.js — S228: Convert parsed 3D scene → DB contract
// Depends: SemanticEnrichment (from semantic_enrichment.js)
// Implementing S228_import_format_to_db.md §File 2 — Witness: W-SCENEDB

function extractMesh(mesh, prefix, index, yUpSwap) {
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
    // S228c: Y-up → IFC Z-up per-vertex swap (same as import_worker.js)
    // (x, y, z) → (x, -z, y) — viewer's ifc2three then renders correctly
    if (yUpSwap) {
      var tmp = wy;
      wy = -wz;
      wz = tmp;
    }
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

// S228c: auto-detect up-axis by scanning scene bounding box
// Buildings: height axis is typically the smallest-range axis starting near 0.
// Y-up: Y has height range, Z has footprint range. Z-up: Z has height, Y has footprint.
function detectUpAxis(scene) {
  var minY = Infinity, maxY = -Infinity;
  var minZ = Infinity, maxZ = -Infinity;
  scene.traverse(function(child) {
    if (!child.isMesh || !child.geometry || !child.geometry.attributes || !child.geometry.attributes.position) return;
    child.updateWorldMatrix(true, false);
    var pos = child.geometry.attributes.position;
    var m = child.matrixWorld.elements;
    // Sample first/last/middle vertices for speed
    var samples = [0, Math.floor(pos.count / 2), pos.count - 1];
    for (var s = 0; s < samples.length; s++) {
      var i = samples[s];
      if (i >= pos.count) continue;
      var lx = pos.getX(i), ly = pos.getY(i), lz = pos.getZ(i);
      var wy = m[1]*lx + m[5]*ly + m[9]*lz  + m[13];
      var wz = m[2]*lx + m[6]*ly + m[10]*lz + m[14];
      if (wy < minY) minY = wy; if (wy > maxY) maxY = wy;
      if (wz < minZ) minZ = wz; if (wz > maxZ) maxZ = wz;
    }
  });
  var rangeY = maxY - minY;
  var rangeZ = maxZ - minZ;
  // If Z range is much smaller than Y range, data is already Z-up (height in Z)
  // Heuristic: if Z range < Y range * 0.5, it's Z-up
  if (rangeZ > 0 && rangeY > 0 && rangeZ < rangeY * 0.5) return 'z-up';
  // If Y range is smaller (height in Y), it's Y-up
  if (rangeY > 0 && rangeZ > 0 && rangeY < rangeZ * 0.5) return 'y-up';
  // If both are close, check which starts nearer to 0 (height starts at ground)
  if (minY >= -1 && minZ < -1) return 'y-up';  // Y starts at ground
  if (minZ >= -1 && minY < -1) return 'z-up';  // Z starts at ground
  return 'y-up';  // default for OBJ/DAE/GLB
}

function sceneToDb(scene, filename, ext, options) {
  var opts = options || {};
  var yUpToZUp = (opts.yUpToZUp !== false);  // default true
  var prefix = ext.toUpperCase();

  // S228c: per-vertex Y-up → IFC Z-up swap (NOT scene rotation — that double-swaps with viewer's ifc2three)
  // Auto-detect: some exporters (Rhino) write Z-up OBJ despite the standard
  var Y_UP_FORMATS = { dae:1, obj:1, glb:1, gltf:1 };
  var yUpSwap = false;
  if (yUpToZUp && Y_UP_FORMATS[ext]) {
    var detected = detectUpAxis(scene);
    yUpSwap = (detected === 'y-up');
    console.log('[S228c] §AXIS_DETECT ext=' + ext + ' detected=' + detected + ' yUpSwap=' + yUpSwap);
  }

  var elements = [], geometries = [], transforms = [];
  var discCounts = {};
  var storeySet = {};
  var meshIndex = 0;

  scene.traverse(function(child) {
    if (!child.isMesh) return;
    var geom = child.geometry;
    if (!geom || !geom.attributes || !geom.attributes.position) return;

    var result = extractMesh(child, prefix, meshIndex, yUpSwap);
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

// Export
if (typeof self !== 'undefined') {
  self.SceneToDb = { sceneToDb: sceneToDb, extractMesh: extractMesh };
}
if (typeof module !== 'undefined') {
  module.exports = { sceneToDb: sceneToDb, extractMesh: extractMesh };
}
