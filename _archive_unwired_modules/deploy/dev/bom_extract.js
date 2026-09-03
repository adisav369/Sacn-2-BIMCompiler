/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
// bom_extract.js — JS BOM extractor (replaces Java IFCtoBOMPipeline for browser)
// Implementing NEW_FROM_REFERENCE.md §5 — Witness: W-BOM-JS
//
// Reads elements_meta + element_transforms from building.db (already in A.db),
// produces a BOM tree cached as JSON in IndexedDB.
// One building, one BOM, one pass. No verb expansion, no component_library.

(function(window) {
'use strict';

// ── BOM Tree Structure ──────────────────────────────────────────────────────
// {
//   building: 'SampleCastle',
//   envelope: { minX, maxX, minY, maxY, minZ, maxZ, width, depth, height },
//   storeys: [
//     { name: '00 begane grond', minZ, maxZ, height,
//       disciplines: [
//         { name: 'ARC',
//           classes: [
//             { ifc_class: 'IfcWall', count: 226, elements: [...guids],
//               aabb: { minX, maxX, minY, maxY, minZ, maxZ } },
//             ...
//           ]
//         }, ...
//       ]
//     }, ...
//   ],
//   storeyHeights: [3.6, 3.2, ...],    // floor-to-floor deltas
//   bayProportions: [1.0, 0.75, ...],   // from GridDims if available
//   elementCount: 3284,
//   extractedAt: '2026-05-21T...'
// }

var BOM_IDB_STORE = 'bim_ootb_bom';

/**
 * extractBOM(A) — main entry point
 * @param {object} A — the APP object with A.db, A.activeBuilding, A.dbQuery
 * @returns {object} BOM tree, also cached in IndexedDB
 */
function extractBOM(A) {
  if (!A || !A.db) {
    console.warn('§BOM_EXTRACT no db');
    return null;
  }
  var t0 = performance.now();
  var building = A.activeBuilding || 'unknown';

  // ── 1. Query all elements with transforms ──
  var rows = A.dbQuery(
    'SELECT m.guid, m.ifc_class, m.storey, m.discipline, m.material_name, m.material_rgba, ' +
    '       t.center_x, t.center_y, t.center_z, t.bbox_x, t.bbox_y, t.bbox_z ' +
    'FROM elements_meta m ' +
    'JOIN element_transforms t ON m.guid = t.guid ' +
    'ORDER BY m.storey, m.discipline, m.ifc_class'
  );

  if (!rows.length) {
    console.warn('§BOM_EXTRACT no elements found');
    return null;
  }

  // ── 2. Build grouped tree ──
  var storeyMap = {};  // storey_name → { disciplines: { disc → { classes: { class → {elements} } } } }
  // Envelope from structural classes only — outliers (proxy, site, furniture) stretch AABB.
  // Fallback to all elements if no structural classes found.
  var ENV_CLASSES = {
    IfcColumn: 1, IfcPile: 1, IfcWall: 1, IfcWallStandardCase: 1,
    IfcSlab: 1, IfcBeam: 1, IfcFooting: 1, IfcCurtainWall: 1, IfcRoof: 1
  };
  var envMinX = Infinity, envMaxX = -Infinity;
  var envMinY = Infinity, envMaxY = -Infinity;
  var envMinZ = Infinity, envMaxZ = -Infinity;
  var allMinX = Infinity, allMaxX = -Infinity;
  var allMinY = Infinity, allMaxY = -Infinity;
  var allMinZ = Infinity, allMaxZ = -Infinity;
  var hasStructural = false;

  for (var i = 0; i < rows.length; i++) {
    var r = rows[i];
    var guid = r[0], ifcClass = r[1], storey = r[2] || 'Unknown';
    var disc = r[3] || 'ARC', matName = r[4], matRgba = r[5];
    var cx = r[6], cy = r[7], cz = r[8];
    var bx = r[9], by = r[10], bz = r[11];  // half-extents

    // element AABB from center + half-extents
    var eMinX = cx - bx, eMaxX = cx + bx;
    var eMinY = cy - by, eMaxY = cy + by;
    var eMinZ = cz - bz, eMaxZ = cz + bz;

    // All-elements envelope (fallback)
    if (eMinX < allMinX) allMinX = eMinX;
    if (eMaxX > allMaxX) allMaxX = eMaxX;
    if (eMinY < allMinY) allMinY = eMinY;
    if (eMaxY > allMaxY) allMaxY = eMaxY;
    if (eMinZ < allMinZ) allMinZ = eMinZ;
    if (eMaxZ > allMaxZ) allMaxZ = eMaxZ;

    // Structural-only envelope
    if (ENV_CLASSES[ifcClass]) {
      hasStructural = true;
      if (eMinX < envMinX) envMinX = eMinX;
      if (eMaxX > envMaxX) envMaxX = eMaxX;
      if (eMinY < envMinY) envMinY = eMinY;
      if (eMaxY > envMaxY) envMaxY = eMaxY;
      if (eMinZ < envMinZ) envMinZ = eMinZ;
      if (eMaxZ > envMaxZ) envMaxZ = eMaxZ;
    }

    // group: storey → discipline → ifc_class
    if (!storeyMap[storey]) storeyMap[storey] = { minZ: Infinity, maxZ: -Infinity, disciplines: {} };
    var s = storeyMap[storey];
    if (eMinZ < s.minZ) s.minZ = eMinZ;
    if (eMaxZ > s.maxZ) s.maxZ = eMaxZ;

    if (!s.disciplines[disc]) s.disciplines[disc] = {};
    var d = s.disciplines[disc];

    if (!d[ifcClass]) d[ifcClass] = {
      count: 0, guids: [],
      minX: Infinity, maxX: -Infinity,
      minY: Infinity, maxY: -Infinity,
      minZ: Infinity, maxZ: -Infinity,
      materials: {}
    };
    var c = d[ifcClass];
    c.count++;
    c.guids.push(guid);
    if (eMinX < c.minX) c.minX = eMinX;
    if (eMaxX > c.maxX) c.maxX = eMaxX;
    if (eMinY < c.minY) c.minY = eMinY;
    if (eMaxY > c.maxY) c.maxY = eMaxY;
    if (eMinZ < c.minZ) c.minZ = eMinZ;
    if (eMaxZ > c.maxZ) c.maxZ = eMaxZ;
    if (matName) c.materials[matName] = (c.materials[matName] || 0) + 1;
  }

  // ── 3. Sort storeys by Z, compute heights ──
  var storeyNames = Object.keys(storeyMap);
  storeyNames.sort(function(a, b) { return storeyMap[a].minZ - storeyMap[b].minZ; });

  var storeys = [];
  var storeyHeights = [];
  for (var si = 0; si < storeyNames.length; si++) {
    var sName = storeyNames[si];
    var sData = storeyMap[sName];
    var height = sData.maxZ - sData.minZ;

    // floor-to-floor: delta to next storey's minZ, or own height if last
    var floorToFloor = height;
    if (si < storeyNames.length - 1) {
      floorToFloor = storeyMap[storeyNames[si + 1]].minZ - sData.minZ;
    }
    storeyHeights.push(Math.round(floorToFloor * 1000) / 1000);

    // build discipline array
    var discArr = [];
    var discNames = Object.keys(sData.disciplines).sort();
    for (var di = 0; di < discNames.length; di++) {
      var dName = discNames[di];
      var dData = sData.disciplines[dName];
      var classArr = [];
      var classNames = Object.keys(dData).sort();
      for (var ci = 0; ci < classNames.length; ci++) {
        var cName = classNames[ci];
        var cData = dData[cName];
        classArr.push({
          ifc_class: cName,
          count: cData.count,
          elements: cData.guids,
          aabb: {
            minX: cData.minX, maxX: cData.maxX,
            minY: cData.minY, maxY: cData.maxY,
            minZ: cData.minZ, maxZ: cData.maxZ
          },
          materials: cData.materials
        });
      }
      discArr.push({ name: dName, classes: classArr });
    }

    storeys.push({
      name: sName,
      minZ: sData.minZ,
      maxZ: sData.maxZ,
      height: Math.round(height * 1000) / 1000,
      disciplines: discArr
    });
  }

  // ── 4. Bay proportions from GridDims (if available) ──
  var bayProportions = null;
  if (window.GridDims && typeof GridDims.detectGrids === 'function') {
    try {
      var grids = GridDims.detectGrids();
      if (grids && grids.xSpans && grids.xSpans.length > 1) {
        var maxSpan = Math.max.apply(null, grids.xSpans);
        bayProportions = grids.xSpans.map(function(s) {
          return Math.round((s / maxSpan) * 100) / 100;
        });
      }
      console.log('§BOM_GRIDS xSpans=' + (grids && grids.xSpans ? grids.xSpans.length : 0));
    } catch(e) {
      console.warn('§BOM_GRIDS_ERR', e.message);
    }
  }

  // ── 5. Structural cadence — column positions per storey ──
  var columnPositions = A.dbQuery(
    'SELECT t.center_x, t.center_y, t.center_z, m.storey ' +
    'FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid ' +
    'WHERE m.ifc_class IN (\'IfcColumn\', \'IfcPile\') ' +
    'ORDER BY m.storey, t.center_x, t.center_y'
  );
  var cadence = null;
  if (columnPositions.length >= 2) {
    var colX = columnPositions.map(function(r) { return r[0]; });
    colX.sort(function(a, b) { return a - b; });
    // deduplicate close positions (within 0.1m)
    var uniqueX = [colX[0]];
    for (var ui = 1; ui < colX.length; ui++) {
      if (colX[ui] - uniqueX[uniqueX.length - 1] > 0.1) uniqueX.push(colX[ui]);
    }
    if (uniqueX.length >= 2) {
      var spacings = [];
      for (var xi = 1; xi < uniqueX.length; xi++) {
        spacings.push(Math.round((uniqueX[xi] - uniqueX[xi - 1]) * 1000) / 1000);
      }
      cadence = { uniqueX: uniqueX, spacings: spacings, count: columnPositions.length };
    }
  }

  // ── 6. Assemble BOM tree ──
  // Fallback: if no structural classes, use all-elements envelope
  if (!hasStructural) {
    envMinX = allMinX; envMaxX = allMaxX;
    envMinY = allMinY; envMaxY = allMaxY;
    envMinZ = allMinZ; envMaxZ = allMaxZ;
    console.log('§BOM_ENVELOPE fallback to all elements (no structural classes)');
  } else {
    console.log('§BOM_ENVELOPE structural-only' +
      ' all=' + Math.round((allMaxX-allMinX)*1000)/1000 + 'x' + Math.round((allMaxY-allMinY)*1000)/1000 +
      ' struct=' + Math.round((envMaxX-envMinX)*1000)/1000 + 'x' + Math.round((envMaxY-envMinY)*1000)/1000);
  }
  var bom = {
    building: building,
    envelope: {
      minX: envMinX, maxX: envMaxX,
      minY: envMinY, maxY: envMaxY,
      minZ: envMinZ, maxZ: envMaxZ,
      width:  Math.round((envMaxX - envMinX) * 1000) / 1000,
      depth:  Math.round((envMaxY - envMinY) * 1000) / 1000,
      height: Math.round((envMaxZ - envMinZ) * 1000) / 1000
    },
    storeys: storeys,
    storeyHeights: storeyHeights,
    bayProportions: bayProportions,
    cadence: cadence,
    elementCount: rows.length,
    extractedAt: new Date().toISOString()
  };

  var ms = Math.round(performance.now() - t0);
  console.log('§BOM_EXTRACT building=' + building +
    ' storeys=' + storeys.length +
    ' elements=' + rows.length +
    ' envelope=' + bom.envelope.width + 'x' + bom.envelope.depth + 'x' + bom.envelope.height + 'm' +
    ' cadence=' + (cadence ? cadence.count + 'cols' : 'none') +
    ' ms=' + ms);

  // ── 7. Cache to IndexedDB ──
  cacheBOM(building, bom);

  return bom;
}

// ── IndexedDB cache ──────────────────────────────────────────────────────────
function cacheBOM(building, bom) {
  try {
    var req = indexedDB.open(BOM_IDB_STORE, 1);
    req.onupgradeneeded = function(e) {
      var db = e.target.result;
      if (!db.objectStoreNames.contains('bom')) {
        db.createObjectStore('bom', { keyPath: 'building' });
      }
    };
    req.onsuccess = function(e) {
      var db = e.target.result;
      var tx = db.transaction('bom', 'readwrite');
      tx.objectStore('bom').put(bom);
      tx.oncomplete = function() {
        console.log('§BOM_CACHE saved building=' + building +
          ' size=' + Math.round(JSON.stringify(bom).length / 1024) + 'KB');
      };
    };
    req.onerror = function(e) {
      console.warn('§BOM_CACHE_ERR', e.target.error);
    };
  } catch(e) {
    console.warn('§BOM_CACHE_ERR', e.message);
  }
}

function loadCachedBOM(building, callback) {
  try {
    var req = indexedDB.open(BOM_IDB_STORE, 1);
    req.onupgradeneeded = function(e) {
      var db = e.target.result;
      if (!db.objectStoreNames.contains('bom')) {
        db.createObjectStore('bom', { keyPath: 'building' });
      }
    };
    req.onsuccess = function(e) {
      var db = e.target.result;
      var tx = db.transaction('bom', 'readonly');
      var get = tx.objectStore('bom').get(building);
      get.onsuccess = function() {
        var bom = get.result || null;
        console.log('§BOM_CACHE_LOAD building=' + building + ' found=' + !!bom);
        callback(bom);
      };
      get.onerror = function() { callback(null); };
    };
    req.onerror = function() { callback(null); };
  } catch(e) {
    console.warn('§BOM_CACHE_LOAD_ERR', e.message);
    callback(null);
  }
}

// ── STD_MEP — default MEP template for small buildings ───────────────────────
// When a building has no MEP discipline data, use standard counts per room area.
var STD_MEP = {
  ELEC: {
    desc: 'Electrical',
    perRoomM2: { lightPoint: 0.15, powerPoint: 0.1, switchPoint: 0.05 },
    perStorey: { dbBoard: 1, riserCable: 1 }
  },
  ACMV: {
    desc: 'Air Conditioning',
    perRoomM2: { diffuser: 0.05, ductRunM: 0.3 },
    perStorey: { ahuUnit: 1 }
  },
  FP: {
    desc: 'Fire Protection',
    perStoreyM2: { sprinklerHead: 0.08, smokeDetector: 0.04 },
    perStorey: { riser: 1, extinguisher: 2 }
  },
  PLMB: {
    desc: 'Plumbing',
    perBathroom: { wcPan: 1, basin: 1, shower: 1, floorTrap: 1 },
    perKitchen: { sink: 1, floorTrap: 1 }
  },
  SANI: {
    desc: 'Sanitary',
    perBathroom: { supplyPoint: 2, wastePoint: 2 },
    perKitchen: { supplyPoint: 1, wastePoint: 1 }
  }
};

/**
 * applySTDMEP(bom) — inject standard MEP into storeys that have no MEP discipline
 */
function applySTDMEP(bom) {
  if (!bom || !bom.storeys) return;
  var applied = 0;
  for (var i = 0; i < bom.storeys.length; i++) {
    var s = bom.storeys[i];
    var hasMEP = s.disciplines.some(function(d) {
      return d.name === 'MEP' || d.name === 'FP' || d.name === 'ELEC' || d.name === 'ACMV';
    });
    if (!hasMEP) {
      // compute storey floor area from AABB
      var arcDisc = s.disciplines.find(function(d) { return d.name === 'ARC'; });
      if (arcDisc) {
        var slabs = arcDisc.classes.find(function(c) { return c.ifc_class === 'IfcSlab'; });
        if (slabs && slabs.aabb) {
          var areaM2 = (slabs.aabb.maxX - slabs.aabb.minX) * (slabs.aabb.maxY - slabs.aabb.minY);
          s._stdMep = {
            source: 'STD_MEP',
            areaM2: Math.round(areaM2 * 100) / 100,
            elec: Math.round(areaM2 * STD_MEP.ELEC.perRoomM2.lightPoint),
            fp: Math.round(areaM2 * STD_MEP.FP.perStoreyM2.sprinklerHead),
            acmv: Math.round(areaM2 * STD_MEP.ACMV.perRoomM2.diffuser)
          };
          applied++;
        }
      }
    }
  }
  if (applied) console.log('§BOM_STD_MEP applied=' + applied + ' storeys (no MEP data)');
}

// ── §SHELL-N-ZSPAN: instanced-by n (TYPICAL_STOREY × n) ──────────────────────────────────────────────
// Implementing CONSTRUCTION_GRID_BOM_DUAL_MODEL.md §SHELL-N-ZSPAN — Witness: W-TYPICAL-N.
// The LAST graph cross-edge (SPATIAL_DEPENDENCY_GRAPH.md): a storey is a product repeated n times (= TILE qty=N
// on the Z axis). Detect repeated storeys by a MEASURED signature — floor-to-floor within tol AND per-(discipline,
// class) count-vector cosine ≥ thresh — and collapse a run into TYPICAL_FLOOR × n. n is the qty on the typical-floor
// order line; a Z-runner's extent folds from n (extent=f(n)). Grep-CLEAN: the signature is keyed by WHATEVER
// (discipline,class) pairs are present — no IFC class-name literal drives any branch; similarity is pure counting.
function _storeySignature(storey) {
  var vec = {};
  (storey.disciplines || []).forEach(function (d) {
    (d.classes || []).forEach(function (c) { vec[d.name + '|' + c.ifc_class] = c.count; });
  });
  return vec;
}
function _cosine(a, b) {
  var keys = {}; Object.keys(a).forEach(function (k) { keys[k] = 1; }); Object.keys(b).forEach(function (k) { keys[k] = 1; });
  var dot = 0, na = 0, nb = 0;
  Object.keys(keys).forEach(function (k) { var x = a[k] || 0, y = b[k] || 0; dot += x * y; na += x * x; nb += y * y; });
  return (na && nb) ? dot / Math.sqrt(na * nb) : 0;
}
// factorizeTypicalStoreys(storeys, storeyHeights, opts) → [{ repStorey, n, memberStoreys, typical, sim }]
// storeys[i] = { name, disciplines:[{name, classes:[{ifc_class,count}]}] } (the extractBOM shape); storeyHeights[i]
// = floor-to-floor for storey i. Greedy single-pass: each unused storey seeds a group, later storeys join iff
// floor-to-floor within ffTol AND signature cosine ≥ simThresh. n = group size; typical = n ≥ 2. Deterministic.
function factorizeTypicalStoreys(storeys, storeyHeights, opts) {
  opts = opts || {};
  var ffTol = opts.ffTol != null ? opts.ffTol : 0.35;
  var simThresh = opts.simThresh != null ? opts.simThresh : 0.92;
  var used = {}, out = [];
  for (var i = 0; i < storeys.length; i++) {
    if (used[i]) continue;
    used[i] = 1;
    var members = [i], sigI = _storeySignature(storeys[i]), ffI = storeyHeights ? storeyHeights[i] : null, sims = [];
    for (var j = i + 1; j < storeys.length; j++) {
      if (used[j]) continue;
      var ffJ = storeyHeights ? storeyHeights[j] : null;
      var ffOk = (ffI != null && ffJ != null) ? Math.abs(ffI - ffJ) <= ffTol : true;
      var sim = _cosine(sigI, _storeySignature(storeys[j]));
      if (ffOk && sim >= simThresh) { members.push(j); used[j] = 1; sims.push(Math.round(sim * 1000) / 1000); }
    }
    out.push({
      repStorey: storeys[i].name, n: members.length,
      memberStoreys: members.map(function (m) { return storeys[m].name; }),
      typical: members.length >= 2, joinSims: sims
    });
  }
  return out;
}

// ── §SHELL-N-ZSPAN: instanced-by n as MEASURED translational symmetry along Z ────────────────────────
// Implementing CONSTRUCTION_GRID_BOM_DUAL_MODEL.md §SHELL-N-ZSPAN + SPATIAL_DEPENDENCY_GRAPH.md (the last edge),
// under the MAIN MISSION: a percept must reconstruct EXACTLY (RosettaStone), LOSSLESSLY (typical + residuals).
//
// An element is "instanced-by n along Z" iff a copy of it (same class, same X/Y) recurs at a fixed pitch p — a
// measured translational symmetry, NOT read from the dirty `storey` field (which has overlapping Z-bands + 'Unknown'
// labels). This is the Z-axis analogue of the roof TILE verb (qty=N). It is the editable BATCH "typical floor × n":
// edit the one stored representative (the fundamental domain) → all n instances re-fold; collapse n→1 → instances
// (and their Z-runner extents) vanish. GREP-CLEAN: `class` is only ever an opaque equality key — no IFC class-name
// literal drives any branch; the trigger is the MEASURED recurrence, never the name.
//
// LOSSLESS DECOMPOSITION (the RosettaStone contract): the element set is partitioned into
//   reps (one per instanced column, stored as-is) ∪ instances (GENERATED = rep + k·p, geometry not stored) ∪
//   residuals (everything non-recurring, stored as-is).
// reconstruct = reps + (regenerate each instance from its rep by +k·p on Z) + residuals  ==  extracted.db, 0.000 mm.
// n is claimed ONLY where a real copy lands within epsMm of rep+k·p; anything that does not land EXACTLY is a
// residual (carried, never invented). coveredFraction = instances / total = the honest, measured typicality.
//
// elements: [{ guid, cls, x, y, z }] (class + element CENTROID). opts: { epsMm, minPitchM, groupMm }.
function _dominantPitch(elements, groupMm, minPitchM) {
  // histogram within-column (cls + X/Y quantized to groupMm) consecutive z-gaps; dominant gap = the floor pitch.
  var col = {}, q = groupMm / 1000;
  for (var i = 0; i < elements.length; i++) {
    var e = elements[i], k = e.cls + '|' + Math.round(e.x / q) + '|' + Math.round(e.y / q);
    (col[k] || (col[k] = [])).push(e.z);
  }
  var hist = {};
  Object.keys(col).forEach(function (k) {
    var zs = col[k].slice().sort(function (a, b) { return a - b; });
    for (var i = 1; i < zs.length; i++) {
      var d = Math.round((zs[i] - zs[i - 1]) * 100) / 100;          // 1 cm bins
      if (d >= minPitchM) hist[d] = (hist[d] || 0) + 1;
    }
  });
  var best = null, bestN = 0;
  Object.keys(hist).forEach(function (d) { if (hist[d] > bestN) { bestN = hist[d]; best = parseFloat(d); } });
  return { pitch: best, support: bestN };
}
// factorizeInstancedZ(elements, opts) → the instanced-by-n decomposition (see contract above).
function factorizeInstancedZ(elements, opts) {
  opts = opts || {};
  var epsMm = opts.epsMm != null ? opts.epsMm : 1e-3;        // "0.000 mm" landing tolerance (default 0.001 mm)
  var groupMm = opts.groupMm != null ? opts.groupMm : 1;     // X/Y column grouping grid
  var minPitchM = opts.minPitchM != null ? opts.minPitchM : 0.5;
  var eps = epsMm / 1000, q = groupMm / 1000;
  var dp = _dominantPitch(elements, groupMm, minPitchM), p = dp.pitch;
  var reps = [], instances = [], residual = [];
  if (!p) { // no vertical recurrence at all (e.g. single-storey) → everything is residual; n=1 honest.
    elements.forEach(function (e) { residual.push(e.guid); });
    return { pitch: null, pitchSupport: 0, reps: reps, instances: instances, residualGuids: residual,
             coveredFraction: 0, n_by_rep: {} };
  }
  // form columns (cls + X/Y quantized to groupMm); within each, lowest-z = rep, others tested against rep+k·p EXACTLY
  var col = {};
  elements.forEach(function (e) {
    var k = e.cls + '|' + Math.round(e.x / q) + '|' + Math.round(e.y / q);
    (col[k] || (col[k] = [])).push(e);
  });
  var n_by_rep = {};
  Object.keys(col).sort().forEach(function (k) {
    var g = col[k].slice().sort(function (a, b) { return a.z - b.z; });
    if (g.length < 2) { residual.push(g[0].guid); return; }
    var rep = g[0], isRep = false;
    for (var i = 1; i < g.length; i++) {
      var e = g[i], kk = Math.round((e.z - rep.z) / p);
      if (kk >= 1 && Math.abs(e.z - (rep.z + kk * p)) <= eps &&
          Math.abs(e.x - rep.x) <= eps && Math.abs(e.y - rep.y) <= eps) {
        instances.push({ repGuid: rep.guid, guid: e.guid, k: kk });   // GENERATED: rep + k·p on Z
        isRep = true;
      } else {
        residual.push(e.guid);                                        // does not land exactly → carried, not invented
      }
    }
    if (isRep) { reps.push(rep.guid); n_by_rep[rep.guid] = 1 + instances.filter(function (x) { return x.repGuid === rep.guid; }).length; }
    else residual.push(rep.guid);
  });
  return { pitch: p, pitchSupport: dp.support, reps: reps, instances: instances, residualGuids: residual,
           coveredFraction: instances.length / elements.length, n_by_rep: n_by_rep };
}

// ── Public API ───────────────────────────────────────────────────────────────
window.BOMExtract = {
  extract: extractBOM,
  loadCached: loadCachedBOM,
  applySTDMEP: applySTDMEP,
  STD_MEP: STD_MEP,
  factorizeTypicalStoreys: factorizeTypicalStoreys,
  factorizeInstancedZ: factorizeInstancedZ,
  _storeySignature: _storeySignature,
  _cosine: _cosine,
  _dominantPitch: _dominantPitch
};

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { factorizeTypicalStoreys: factorizeTypicalStoreys, factorizeInstancedZ: factorizeInstancedZ,
    _storeySignature: _storeySignature, _cosine: _cosine, _dominantPitch: _dominantPitch };
}

})(typeof window !== 'undefined' ? window : globalThis);
