/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 *
 * dlod.js — §6.8 Frustum + Storey DLOD (Dynamic Level of Detail)
 * S258: R-tree tested but SQL WASM overhead (~90ms/tick) > JS traverse (~4ms/tick).
 * Kept: frustum + storey with orbit-target tracking + time machine cooperate.
 * 48K → ~2K visible, 122K → ~3K visible, sub-6ms per tick.
 */
function setupDLOD(A) {
  // ── State ──
  A._dlodEnabled = false;
  A._dlodFrame = 0;
  A._dlodPaused = false;     // true = cooperate with time machine (skip TM-hidden meshes)

  var EVAL_EVERY = 6;             // frames between evaluations
  var MIN_ELEMENTS = 5000;        // don't enable for small buildings
  var STOREY_RANGE = 3;           // show N storeys above/below look target
  var _frustum = new THREE.Frustum();
  var _projScreenMatrix = new THREE.Matrix4();
  var _sphere = new THREE.Sphere();
  var _zeroScale = new THREE.Matrix4().makeScale(0, 0, 0);
  var _lastCamX = 0, _lastCamY = 0, _lastCamZ = 0;  // §S260b: skip tick when camera idle
  var _lastTargX = 0, _lastTargY = 0, _lastTargZ = 0;

  // Storey Y-positions cache (built once after streaming)
  var _storeyLevels = [];  // [{name, y}, ...] sorted by y ascending
  var _storeyBuilt = false;

  function _buildStoreyLevels() {
    if (_storeyBuilt) return;
    _storeyBuilt = true;
    var storeyY = {};
    // §S260b: Build from _batchStoreyMap (BatchedMesh) + individual meshes
    // BatchedMesh doesn't have per-element positions, so query A.db for storey→avg Z
    if (A.db && A._batchStoreyMap && Object.keys(A._batchStoreyMap).length > 0) {
      try {
        var rows = A.db.exec("SELECT storey, AVG(center_z) FROM element_transforms t JOIN elements_meta m ON t.guid=m.guid WHERE storey != '' GROUP BY storey");
        if (rows.length && rows[0].values) {
          for (var ri = 0; ri < rows[0].values.length; ri++) {
            var s = rows[0].values[ri][0];
            var z = rows[0].values[ri][1];
            if (s) {
              var p = A.ifc2three(0, 0, z);
              storeyY[s] = p.y;
            }
          }
        }
      } catch(e) {}
    }
    // Fallback: individual meshes (non-BatchedMesh path or mixed)
    if (Object.keys(storeyY).length === 0) {
      A.scene.traverse(function(obj) {
        if (obj.isMesh && obj.userData.storey && obj.userData.guid) {
          var s = obj.userData.storey;
          if (storeyY[s] === undefined) storeyY[s] = obj.position.y;
        }
      });
    }
    _storeyLevels = Object.entries(storeyY)
      .map(function(e) { return { name: e[0], y: e[1] }; })
      .sort(function(a, b) { return a.y - b.y; });
    console.log('[DLOD] §DLOD_STOREYS count=' + _storeyLevels.length +
      ' levels=' + _storeyLevels.map(function(s) { return s.name; }).join(','));
  }

  // Visible storeys based on orbit target + camera distance
  // Close-up: show 7 storeys. Far away: show all (whole building visible).
  function _visibleStoreys() {
    if (!_storeyLevels.length) return null; // show all

    // Camera distance to target — when far, show more storeys
    var camDist = A.controls
      ? A.camera.position.distanceTo(A.controls.target)
      : 100;
    // Building height from storey data
    var bldHeight = _storeyLevels[_storeyLevels.length - 1].y - _storeyLevels[0].y;
    // If camera is far enough to see the whole building, show all storeys
    if (bldHeight > 0 && camDist > bldHeight * 2) return null;

    var camY = A.controls ? A.controls.target.y : A.camera.position.y;
    var bestIdx = 0, bestDist = Infinity;
    for (var i = 0; i < _storeyLevels.length; i++) {
      var d = Math.abs(_storeyLevels[i].y - camY);
      if (d < bestDist) { bestDist = d; bestIdx = i; }
    }
    var visible = {};
    var lo = Math.max(0, bestIdx - STOREY_RANGE);
    var hi = Math.min(_storeyLevels.length - 1, bestIdx + STOREY_RANGE);
    for (var j = lo; j <= hi; j++) {
      visible[_storeyLevels[j].name] = true;
    }
    return visible;
  }

  // ── Enable/disable ──
  A.dlodEnable = function() {
    if (A.streamedCount < MIN_ELEMENTS) {
      console.log('[DLOD] §DLOD_SKIP count=' + A.streamedCount + ' < ' + MIN_ELEMENTS);
      return;
    }
    A._dlodEnabled = true;
    A._dlodFrame = EVAL_EVERY - 1;  // next dlodTick fires immediately (no 6-frame delay)
    _storeyBuilt = false;
    console.log('[DLOD] §DLOD_ENABLE count=' + A.streamedCount);
  };

  A.dlodDisable = function(reason) {
    if (!A._dlodEnabled) return;
    A._dlodEnabled = false;
    _restoreAll();
    console.log('[DLOD] §DLOD_DISABLE reason=' + (reason || 'unknown'));
  };

  // ── Main tick — called from animate loop ──
  A.dlodTick = function() {
    if (!A._dlodEnabled) return;
    A._dlodFrame++;
    if (A._dlodFrame % EVAL_EVERY !== 0) return;

    // §S260b: Skip when camera hasn't moved — no work needed, prevents micro-stutter
    var cp = A.camera.position, ct = A.controls ? A.controls.target : cp;
    if (Math.abs(cp.x - _lastCamX) < 0.01 && Math.abs(cp.y - _lastCamY) < 0.01 &&
        Math.abs(cp.z - _lastCamZ) < 0.01 && Math.abs(ct.x - _lastTargX) < 0.01 &&
        Math.abs(ct.y - _lastTargY) < 0.01 && Math.abs(ct.z - _lastTargZ) < 0.01) {
      return;
    }
    _lastCamX = cp.x; _lastCamY = cp.y; _lastCamZ = cp.z;
    _lastTargX = ct.x; _lastTargY = ct.y; _lastTargZ = ct.z;

    _buildStoreyLevels();
    var t0 = performance.now();

    // Build camera frustum
    A.camera.updateMatrixWorld();
    _projScreenMatrix.multiplyMatrices(A.camera.projectionMatrix, A.camera.matrixWorldInverse);
    _frustum.setFromProjectionMatrix(_projScreenMatrix);

    var visStoreys = _visibleStoreys();

    var visCount = 0, hidCount = 0, skipCount = 0;
    var storeyFilter = A.activeStoreyFilter;
    var hiddenDiscs = A.hiddenDiscs;

    A.scene.traverse(function(obj) {
      // ── Individual meshes ──
      if (obj.isMesh && obj.userData.guid && !obj.userData.isBboxPlaceholder) {
        // Respect existing storey/disc filters
        if (storeyFilter !== null && storeyFilter !== undefined &&
            obj.userData.storey !== storeyFilter) { skipCount++; return; }
        if (hiddenDiscs && hiddenDiscs.size > 0 &&
            hiddenDiscs.has(obj.userData.disc)) { skipCount++; return; }

        // Cooperate with time machine: don't override TM-hidden elements
        if (A._dlodPaused && !obj.visible) { skipCount++; return; }

        // Storey distance check
        if (visStoreys && obj.userData.storey && !visStoreys[obj.userData.storey]) {
          obj.visible = false;
          obj.userData._dlodHidden = true;
          hidCount++;
          return;
        }

        // Frustum check — use bounding sphere
        if (obj.geometry && obj.geometry.boundingSphere) {
          _sphere.copy(obj.geometry.boundingSphere);
          _sphere.applyMatrix4(obj.matrixWorld);
          if (!_frustum.intersectsSphere(_sphere)) {
            obj.visible = false;
            obj.userData._dlodHidden = true;
            hidCount++;
            return;
          }
        }

        obj.visible = true;
        obj.userData._dlodHidden = false;
        visCount++;
      }

      // ── InstancedMesh: storey-based culling per instance ──
      if (obj.isInstancedMesh && A._instanceMeta[obj.id] && visStoreys) {
        var meta = A._instanceMeta[obj.id];
        var changed = false;
        for (var i = 0; i < meta.length; i++) {
          var m = meta[i];
          if (storeyFilter !== null && storeyFilter !== undefined &&
              m.storey !== storeyFilter) continue;
          if (hiddenDiscs && hiddenDiscs.size > 0 &&
              hiddenDiscs.has(m.disc)) continue;

          if (!visStoreys[m.storey]) {
            if (!m._origMatrix) {
              m._origMatrix = new THREE.Matrix4();
              obj.getMatrixAt(i, m._origMatrix);
            }
            obj.setMatrixAt(i, _zeroScale);
            changed = true;
            hidCount++;
          } else if (m._origMatrix) {
            obj.setMatrixAt(i, m._origMatrix);
            m._origMatrix = null;
            changed = true;
            visCount++;
          }
        }
        if (changed) obj.instanceMatrix.needsUpdate = true;
      }

      // ── BatchedMesh: storey-based culling per slot ──
      if (obj.isBatchedMesh && A._batchMeta[obj.id] && visStoreys) {
        var meta = A._batchMeta[obj.id];
        var anyVis = false;
        for (var i = 0; i < meta.length; i++) {
          var m = meta[i];
          if (storeyFilter !== null && storeyFilter !== undefined &&
              m.storey !== storeyFilter) continue;
          if (hiddenDiscs && hiddenDiscs.size > 0 &&
              hiddenDiscs.has(m.disc)) continue;

          if (!visStoreys[m.storey]) {
            obj.setVisibleAt(m.slotId, false);
            hidCount++;
          } else {
            obj.setVisibleAt(m.slotId, true);
            anyVis = true;
            visCount++;
          }
        }
        obj.visible = anyVis;
      }
    });

    var ms = (performance.now() - t0).toFixed(1);
    if (hidCount > 0 && A.markDirty) A.markDirty();  // §S260b: trigger render after visibility change
    // Log every 10th evaluation (once per second at 60fps)
    if (A._dlodFrame % (EVAL_EVERY * 10) === 0) {
      var camStorey = visStoreys ? Object.keys(visStoreys).join('+') : 'all';
      console.log('[DLOD] §DLOD_FRUSTUM vis=' + visCount +
        ' hid=' + hidCount + ' skip=' + skipCount +
        ' storeys=' + camStorey + ' ms=' + ms);
    }
  };

  // ── Restore all DLOD-hidden meshes ──
  function _restoreAll() {
    A.scene.traverse(function(obj) {
      if (obj.isMesh && obj.userData._dlodHidden) {
        obj.visible = true;
        obj.userData._dlodHidden = false;
      }
      if (obj.isInstancedMesh && A._instanceMeta[obj.id]) {
        var meta = A._instanceMeta[obj.id];
        var changed = false;
        for (var i = 0; i < meta.length; i++) {
          if (meta[i]._origMatrix) {
            obj.setMatrixAt(i, meta[i]._origMatrix);
            meta[i]._origMatrix = null;
            changed = true;
          }
        }
        if (changed) obj.instanceMatrix.needsUpdate = true;
      }
      if (obj.isBatchedMesh && A._batchMeta[obj.id]) {
        var meta = A._batchMeta[obj.id];
        for (var i = 0; i < meta.length; i++) {
          obj.setVisibleAt(meta[i].slotId, true);
        }
        obj.visible = true;
      }
    });
    console.log('[DLOD] §DLOD_RESTORE all meshes visible');
  }
}
