/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
// helpers.js — Shared scene + DB utilities (S239)
// Prevents: 29x scene.traverse duplication, raw db.exec without null-guard,
//           repeated InstancedMesh filter boilerplate across panels/picking/walk/nlp
// Loaded after scene.js, before streaming.js so all modules can use A.collectMeshes etc.

function setupHelpers(A) {

  // ── A.collectMeshes(predicate) ─────────────────────────────────────────────
  // Returns array of scene objects matching predicate. Excludes ground plane.
  // Replaces 17+ inline scene.traverse() mesh-collection loops.
  //
  // Usage: A.collectMeshes(o => o.isMesh && o.userData.disc === 'ARC')
  //        A.collectMeshes(o => o.isInstancedMesh)
  //        A.collectMeshes(o => o.isLineSegments && o.userData.building)
  A.collectMeshes = function(predicate) {
    const result = [];
    if (!A.scene) return result;
    A.scene.traverse(function(obj) {
      if (obj === A.ground) return;
      if (predicate(obj)) result.push(obj);
    });
    return result;
  };

  // ── A.filterInstancedMesh(mesh, filterFn) ─────────────────────────────────
  // Show/hide individual instances via zero-scale matrix (S232 pattern).
  // filterFn(meta) → true = visible, false = hidden
  // meta = { storey, disc, guid, ... } from A._instanceMeta[mesh.id][i]
  //
  // Replaces duplicated blocks in panels.js:42-62, panels.js:111-129
  A.filterInstancedMesh = function(mesh, filterFn) {
    if (!mesh.isInstancedMesh) return;
    const meta = A._instanceMeta && A._instanceMeta[mesh.id];
    if (!meta) return;
    const _zero = new THREE.Matrix4().makeScale(0, 0, 0);
    let anyVisible = false;
    for (let i = 0; i < meta.length; i++) {
      if (filterFn(meta[i])) {
        if (meta[i]._origMatrix) mesh.setMatrixAt(i, meta[i]._origMatrix);
        anyVisible = true;
      } else {
        if (!meta[i]._origMatrix) {
          meta[i]._origMatrix = new THREE.Matrix4();
          mesh.getMatrixAt(i, meta[i]._origMatrix);
        }
        mesh.setMatrixAt(i, _zero);
      }
    }
    mesh.instanceMatrix.needsUpdate = true;
    mesh.visible = anyVisible;
  };

  // ── A.dbQuery(sql, params) ────────────────────────────────────────────────
  // Safe db.exec wrapper. Returns [] if db not ready or no results.
  // Each item in the returned array is a row-values array (same shape as db.exec rows[0].values).
  //
  // Usage: A.dbQuery('SELECT guid FROM elements_meta WHERE building=?', [A.activeBuilding])
  //        → [ ['guid1'], ['guid2'], ... ]
  A.dbQuery = function(sql, params) {
    if (!A.db) return [];
    try {
      const rows = A.db.exec(sql, params || []);
      if (!rows || !rows.length) return [];
      return rows[0].values || [];
    } catch(e) {
      console.warn('§HELPERS_QUERY_ERR', e.message, sql.slice(0, 80));
      return [];
    }
  };

  // ── A.dbQueryFirst(sql, params) ───────────────────────────────────────────
  // Convenience: returns first row as array, or null.
  A.dbQueryFirst = function(sql, params) {
    const rows = A.dbQuery(sql, params);
    return rows.length ? rows[0] : null;
  };

  console.log('§HELPERS_READY collectMeshes+filterInstancedMesh+dbQuery');
}
