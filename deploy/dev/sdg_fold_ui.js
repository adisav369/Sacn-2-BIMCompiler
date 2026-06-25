/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
// sdg_fold_ui.js — wire the rosetta-GREEN forward fold (sdg_fold.js) to the live scene.
// Implementing SPATIAL_DEPENDENCY_GRAPH.md §FORWARD (UI slice) — Witness: W-SDG-FOLD-UI
//
// Grab a datum-plane handle, drag it along its axis → the owned + forward set re-folds LIVE on the canvas.
// This module is the thin shell: it (1) loads the graph from the in-memory DB, (2) turns a FoldResult into
// per-mesh scene deltas (three.js space), (3) applies them to meshes by GUID, and (4) draws draggable datum
// handles + pointer glue. The fold MATH lives in sdg_fold.js (pure, witnessed); this file only moves pixels.
//
// Coordinate map (deltas only — the constant modelOffset cancels): IFC (x,y,z) → three (x, z, −y).
//
// Two parts:
//   PURE (node-testable): buildGraph(query), sceneDelta(foldResult, graph)
//   BROWSER (smoke-tested): indexMeshes(A), applyFold/reset, enableDatumDrag/disableDatumDrag

(function (global) {
  'use strict';

  var SDGFold = (typeof require !== 'undefined') ? require('./sdg_fold.js') : global.SDGFold;
  var AX3 = { X: 'x', Y: 'z', Z: 'y' };   // IFC axis → three.js axis (for the scale component of a stretch)

  // ── buildGraph(query) — read the 5 edge tables + transforms into the shape foldDatumDrag expects. ──────
  // `query(sql)` returns rows as arrays of values (the browser A.dbQuery contract). Missing tables → [].
  function buildGraph(query) {
    var g = { transforms: {}, aabb: {}, datums: {}, anchored: [], spans: [], aggregates: [], fillsHost: [] };
    query('SELECT guid,center_x,center_y,center_z FROM element_transforms')
      .forEach(function (r) { g.transforms[r[0]] = { cx: r[1], cy: r[2], cz: r[3] }; });
    query('SELECT datum_id,axis,coord FROM datum_plane')
      .forEach(function (r) { g.datums[r[0]] = { axis: r[1], coord: r[2] }; });
    query('SELECT element_guid,datum_id,axis,offset_mm FROM rel_anchored')
      .forEach(function (r) { g.anchored.push({ guid: r[0], datumId: r[1], axis: r[2], offsetMm: r[3] }); });
    query('SELECT element_guid,axis,datum_lo_id,datum_hi_id,span_m FROM rel_spans')
      .forEach(function (r) { g.spans.push({ guid: r[0], axis: r[1], loId: r[2], hiId: r[3], spanM: r[4] }); });
    query('SELECT parent_guid,child_guid FROM rel_aggregates')
      .forEach(function (r) { g.aggregates.push({ parent: r[0], child: r[1] }); });
    query('SELECT opening_guid,host_guid,filling_guid FROM rel_fills_host')
      .forEach(function (r) { g.fillsHost.push({ opening: r[0], host: r[1], filling: r[2] }); });
    return g;
  }

  // ── sceneDelta(foldResult, graph) — FoldResult → { guid: {tx,ty,tz, sx,sy,sz} } in three.js space. ─────
  // moved → pure translate (IFC→three), scale 1. stretched → center-shift translate + axis scale = new/old
  // (the held end stays put; the engine's center-shift is exactly the scale-about-fixed-end translation).
  function sceneDelta(foldResult, graph) {
    var out = {};
    (foldResult.moved || []).forEach(function (m) {
      out[m.guid] = { tx: m.dx, ty: m.dz, tz: -m.dy, sx: 1, sy: 1, sz: 1 };  // IFC (dx,dy,dz) → three (x,z,−y)
    });
    var spanSrc = {};
    (graph.spans || []).forEach(function (s) { spanSrc[s.guid + '|' + s.axis] = s; });
    (foldResult.stretched || []).forEach(function (s) {
      var src = spanSrc[s.guid + '|' + s.axis];
      var oldSpan = src ? src.spanM : (s.new_span_m - (s.d_hi_mm - s.d_lo_mm) / 1000);
      var f = oldSpan ? s.new_span_m / oldSpan : 1;
      var shiftM = (s.d_lo_mm + s.d_hi_mm) / 2 / 1000;             // center shift on the IFC span axis
      var di = { X: 0, Y: 0, Z: 0 }; di[s.axis] = shiftM;
      var e = out[s.guid] || { tx: 0, ty: 0, tz: 0, sx: 1, sy: 1, sz: 1 };
      e.tx += di.X; e.ty += di.Z; e.tz += -di.Y;                  // IFC→three translate
      e[{ x: 'sx', y: 'sy', z: 'sz' }[AX3[s.axis]]] = f;          // scale the matching three axis
      out[s.guid] = e;
    });
    return out;
  }

  // ─────────────────────────── BROWSER-ONLY below (needs THREE + the viewer app A) ───────────────────────

  // indexMeshes(A) → { guid: {kind:'mesh'|'batched'|'instanced', obj, slot} } — one walk, cached on A.
  function indexMeshes(A) {
    if (A._sdgIndex) return A._sdgIndex;
    var idx = {};
    Object.keys(A.guidMap || {}).forEach(function (mid) { /* mesh.id → guid; resolved lazily below */ });
    (A.scene ? A.scene.children : []).forEach(function () {});
    // Walk the scene once, classifying each renderable.
    var visit = function (o) {
      if (o.isInstancedMesh && A._instanceMeta && A._instanceMeta[o.id]) {
        A._instanceMeta[o.id].forEach(function (m, i) { if (m && m.guid) idx[m.guid] = { kind: 'instanced', obj: o, slot: i }; });
      } else if (o.isBatchedMesh && A._batchMeta && A._batchMeta[o.id]) {
        A._batchMeta[o.id].forEach(function (m) { if (m && m.guid) idx[m.guid] = { kind: 'batched', obj: o, slot: m.slotId }; });
      } else if (o.isMesh && A.guidMap && A.guidMap[o.id]) {
        idx[A.guidMap[o.id]] = { kind: 'mesh', obj: o };
      }
      (o.children || []).forEach(visit);
    };
    if (A.scene) A.scene.children.forEach(visit);
    A._sdgIndex = idx;
    return idx;
  }

  // applyFold(A, foldResult, graph) — move/scale meshes by GUID; remember originals so reset() is exact.
  function applyFold(A, foldResult, graph) {
    var THREE = global.THREE;
    var deltas = sceneDelta(foldResult, graph);
    var idx = indexMeshes(A);
    A._sdgOrig = A._sdgOrig || { mesh: {}, mat: {} };
    var n = 0;
    Object.keys(deltas).forEach(function (guid) {
      var d = deltas[guid], hit = idx[guid];
      if (!hit) return;
      if (hit.kind === 'mesh') {
        var o = hit.obj;
        if (!A._sdgOrig.mesh[guid]) A._sdgOrig.mesh[guid] = { p: o.position.clone(), s: o.scale.clone() };
        var op = A._sdgOrig.mesh[guid];
        o.position.set(op.p.x + d.tx, op.p.y + d.ty, op.p.z + d.tz);
        o.scale.set(op.s.x * d.sx, op.s.y * d.sy, op.s.z * d.sz);
        n++;
      } else if (THREE) {                                         // batched / instanced — recompose the slot matrix
        var key = guid;
        if (!A._sdgOrig.mat[key]) {
          var m0 = new THREE.Matrix4();
          hit.obj.getMatrixAt(hit.slot, m0);
          A._sdgOrig.mat[key] = m0.clone();
        }
        var m = A._sdgOrig.mat[key].clone();
        var p = new THREE.Vector3(), qt = new THREE.Quaternion(), sc = new THREE.Vector3();
        m.decompose(p, qt, sc);
        p.set(p.x + d.tx, p.y + d.ty, p.z + d.tz);
        sc.set(sc.x * d.sx, sc.y * d.sy, sc.z * d.sz);
        m.compose(p, qt, sc);
        hit.obj.setMatrixAt(hit.slot, m);
        if (hit.obj.instanceMatrix) hit.obj.instanceMatrix.needsUpdate = true;
        if (hit.obj.isBatchedMesh) hit.obj.updateMatrix && hit.obj.updateMatrix();
        n++;
      }
    });
    console.log('§FOLD-UI applied moved=' + (foldResult.moved || []).length + ' stretched=' + (foldResult.stretched || []).length + ' meshes=' + n);
    return n;
  }

  // reset(A) — restore every touched mesh to its saved original (exact).
  function reset(A) {
    var THREE = global.THREE;
    if (!A._sdgOrig) return;
    var idx = A._sdgIndex || {};
    Object.keys(A._sdgOrig.mesh).forEach(function (guid) {
      var hit = idx[guid]; if (!hit || hit.kind !== 'mesh') return;
      var o = hit.obj, op = A._sdgOrig.mesh[guid];
      o.position.copy(op.p); o.scale.copy(op.s);
    });
    Object.keys(A._sdgOrig.mat).forEach(function (guid) {
      var hit = idx[guid]; if (!hit) return;
      hit.obj.setMatrixAt(hit.slot, A._sdgOrig.mat[guid]);
      if (hit.obj.instanceMatrix) hit.obj.instanceMatrix.needsUpdate = true;
      if (hit.obj.isBatchedMesh) hit.obj.updateMatrix && hit.obj.updateMatrix();
    });
    A._sdgOrig = { mesh: {}, mat: {} };
  }

  // enableDatumDrag(A) — draw datum planes as draggable handles; drag along the axis → live fold.
  // Smoke-level glue (the value-bearing math is buildGraph/sceneDelta/applyFold above).
  function enableDatumDrag(A) {
    var THREE = global.THREE; if (!THREE || !A.scene) { console.warn('§FOLD-UI no THREE/scene'); return; }
    A._sdgGraph = buildGraph(function (sql) { return A.dbQuery(sql); });
    var datums = A._sdgGraph.datums;
    if (!Object.keys(datums).length) { console.warn('§FOLD-UI no datums in DB — bake SDG edges into the building DB first'); return; }
    var grp = new THREE.Group(); grp.name = 'sdg_datums'; A.scene.add(grp);
    A._sdgHandles = [];
    Object.keys(datums).forEach(function (id) {
      var dt = datums[id];
      var geo = new THREE.PlaneGeometry(40, 40);
      var mat = new THREE.MeshBasicMaterial({ color: 0xffaa00, transparent: true, opacity: 0.12, side: THREE.DoubleSide, depthWrite: false });
      var pl = new THREE.Mesh(geo, mat);
      // orient the plane normal along the datum axis, position at its coord (datum coords are in IFC space)
      if (dt.axis === 'Z') { pl.rotation.x = -Math.PI / 2; pl.position.y = dt.coord - A.modelOffset.z; }
      else if (dt.axis === 'X') { pl.rotation.y = Math.PI / 2; pl.position.x = dt.coord - A.modelOffset.x; }
      else { pl.position.z = -(dt.coord - A.modelOffset.y); }
      pl.userData.sdgDatumId = +id; pl.userData.sdgAxis = dt.axis;
      grp.add(pl); A._sdgHandles.push(pl);
    });
    console.log('§FOLD-UI enabled handles=' + A._sdgHandles.length);

    // pointer glue: pick a handle, drag along its axis, fold live, commit/cancel on pointerup.
    var drag = null;
    var ndc = function (e) { return new THREE.Vector2((e.clientX / window.innerWidth) * 2 - 1, -(e.clientY / window.innerHeight) * 2 + 1); };
    A._sdgDown = function (e) {
      A.raycaster.setFromCamera(ndc(e), A.camera);
      var hit = A.raycaster.intersectObjects(A._sdgHandles, false)[0];
      if (!hit) return;
      drag = { id: hit.object.userData.sdgDatumId, axis: hit.object.userData.sdgAxis, start: hit.point.clone(), handle: hit.object };
      A.controls && (A.controls.enabled = false);
    };
    A._sdgMove = function (e) {
      if (!drag) return;
      A.raycaster.setFromCamera(ndc(e), A.camera);
      // project pointer onto the datum's world axis → signed deltaMm
      var threeAxis = AX3[drag.axis];
      var planeNormal = new THREE.Vector3(threeAxis === 'x' ? 1 : 0, threeAxis === 'y' ? 1 : 0, threeAxis === 'z' ? 1 : 0);
      var pt = new THREE.Vector3();
      A.raycaster.ray.intersectPlane(new THREE.Plane(new THREE.Vector3(0, threeAxis === 'y' ? 0 : 1, 0), 0), pt);
      var deltaThree = (pt[threeAxis] - drag.start[threeAxis]);
      // three→IFC magnitude on this axis (sign: three y=+IFC z, three x=+IFC x, three z=−IFC y)
      var deltaMm = deltaThree * 1000 * (drag.axis === 'Y' ? -1 : 1);
      reset(A);
      var res = SDGFold.foldDatumDrag(A._sdgGraph, drag.id, deltaMm);
      applyFold(A, res, A._sdgGraph);
      drag.handle.position[threeAxis] += 0;  // handle stays; could follow — kept static for clarity
      A.requestRender && A.requestRender();
    };
    A._sdgUp = function () { drag = null; A.controls && (A.controls.enabled = true); };
    A.canvas.addEventListener('pointerdown', A._sdgDown);
    A.canvas.addEventListener('pointermove', A._sdgMove);
    A.canvas.addEventListener('pointerup', A._sdgUp);
  }

  function disableDatumDrag(A) {
    reset(A);
    if (A._sdgHandles) {
      var grp = A.scene && A.scene.getObjectByName('sdg_datums');
      if (grp) A.scene.remove(grp);
      A._sdgHandles = null;
    }
    if (A.canvas && A._sdgDown) {
      A.canvas.removeEventListener('pointerdown', A._sdgDown);
      A.canvas.removeEventListener('pointermove', A._sdgMove);
      A.canvas.removeEventListener('pointerup', A._sdgUp);
    }
    A._sdgIndex = null;
    console.log('§FOLD-UI disabled');
  }

  var api = { buildGraph: buildGraph, sceneDelta: sceneDelta, indexMeshes: indexMeshes,
    applyFold: applyFold, reset: reset, enableDatumDrag: enableDatumDrag, disableDatumDrag: disableDatumDrag };

  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else global.SDGFoldUI = api;
})(typeof window !== 'undefined' ? window : globalThis);
