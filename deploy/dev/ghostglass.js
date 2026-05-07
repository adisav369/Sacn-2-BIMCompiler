/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
// ghostglass.js — S240b: 4D construction animation via glass-to-solid transitions
// On Play: all meshes go transparent glass. Tasks progressively snap to solid.
// No new geometry — glass casing IS the existing meshes at near-zero opacity.

function setupGhostGlass(APP) {
  var _state = 'IDLE';    // IDLE | PLAYING | PAUSED
  var _tasks = [];
  var _taskIndex = -1;
  var _materialCache = null;  // Map: mesh.id → { color, opacity, transparent, emissive, emissiveIntensity, depthWrite }
  var _guidMeshMap = null;    // guid → [mesh, ...]
  var _guidPhaseColor = {};   // guid → hex color

  // Phase colours (match rates.js PHASE_COLORS)
  var PHASE_HEX = {
    'Substructure':0xA5A5A5, 'Superstructure':0x4472C4, 'MEP Rough-in':0x70AD47,
    'Architecture':0xED7D31, 'MEP Final':0x5B9BD5, 'Finishes':0xFFC000,
    'Commissioning':0xC55A11, 'Unknown':0x888888
  };

  // Build GUID → mesh lookup (once)
  function buildGuidMap() {
    if (_guidMeshMap) return;
    _guidMeshMap = {};
    // Map ALL guidMap entries (including instanced mesh "_N" suffixes) back to meshes
    var meshById = {};
    APP.collectMeshes(function(o) { return o.isMesh || o.isInstancedMesh; }).forEach(function(obj) {
      meshById[obj.id] = obj;
      var g = APP.guidMap[obj.id] || obj.userData.guid;
      if (g) {
        if (!_guidMeshMap[g]) _guidMeshMap[g] = [];
        _guidMeshMap[g].push(obj);
      }
    });
    // Also scan guidMap for instanced entries (mesh.id + '_' + i)
    for (var key in APP.guidMap) {
      var parts = key.split('_');
      if (parts.length >= 2) {
        var baseId = parseInt(parts[0]);
        var mesh = meshById[baseId];
        if (mesh && mesh.isInstancedMesh) {
          var g = APP.guidMap[key];
          if (g && !_guidMeshMap[g]) _guidMeshMap[g] = [];
          if (g && _guidMeshMap[g].indexOf(mesh) < 0) _guidMeshMap[g].push(mesh);
        }
      }
    }
    console.log('§4D_GLASS_MAP guids=' + Object.keys(_guidMeshMap).length);
  }

  // Snapshot all materials (once per session)
  function snapshotMaterials() {
    if (_materialCache) return;
    _materialCache = new Map();
    APP.collectMeshes(function(o) { return o.isMesh || o.isInstancedMesh; }).forEach(function(obj) {
      var mat = obj.material;
      _materialCache.set(obj.id, {
        color: mat.color.getHex(),
        opacity: mat.opacity,
        transparent: mat.transparent,
        emissive: mat.emissive ? mat.emissive.getHex() : 0,
        emissiveIntensity: mat.emissiveIntensity || 0,
        depthWrite: mat.depthWrite !== false
      });
    });
    console.log('§4D_GLASS_SNAPSHOT meshes=' + _materialCache.size);
  }

  // Clone material if shared (prevent cross-contamination)
  function ensureOwnMaterial(mesh) {
    if (!mesh._4dOwnMaterial) {
      mesh.material = mesh.material.clone();
      mesh._4dOwnMaterial = true;
    }
  }

  function makeGlass(mesh) {
    ensureOwnMaterial(mesh);
    var mat = mesh.material;
    mat.transparent = true;
    mat.opacity = 0.03;
    mat.color.setHex(0xaabbcc);
    mat.depthWrite = false;
    if (mat.emissive) { mat.emissive.setHex(0x000000); mat.emissiveIntensity = 0; }
    mat.needsUpdate = true;
    mesh._4dColor = undefined;
  }

  // Rotating highlight colours — cycles per task
  var ACTIVE_COLORS = [0xff8c00, 0x44ff44, 0xff4444, 0xffff00, 0x00ccff];
  var _colorIndex = 0;

  function makeActive(mesh, phaseColor) {
    ensureOwnMaterial(mesh);
    var mat = mesh.material;
    var activeColor = ACTIVE_COLORS[_colorIndex % ACTIVE_COLORS.length];
    mat.transparent = true;
    mat.opacity = 0.85;
    mat.color.setHex(activeColor);
    mat.depthTest = false;      // shine through everything (clash trick)
    mat.depthWrite = false;
    if (mat.emissive) { mat.emissive.setHex(activeColor); mat.emissiveIntensity = 1.0; }
    mat.needsUpdate = true;
    mesh._4dColor = activeColor;
    mesh.renderOrder = 999;     // draw on top
  }

  function makeBuilt(mesh, phaseColor) {
    ensureOwnMaterial(mesh);
    var mat = mesh.material;
    var cached = _materialCache ? _materialCache.get(mesh.id) : null;
    mat.transparent = false;
    mat.opacity = 1.0;
    if (cached) mat.color.setHex(cached.color);
    mat.depthTest = true;
    mat.depthWrite = true;
    if (mat.emissive) { mat.emissive.setHex(0x000000); mat.emissiveIntensity = 0; }
    mat.needsUpdate = true;
    mesh._4dColor = undefined;
    mesh.renderOrder = 0;
  }

  // Restore all materials from cache
  function restoreAll() {
    if (!_materialCache) return;
    var restored = 0;
    APP.collectMeshes(function(o) { return o.isMesh || o.isInstancedMesh; }).forEach(function(obj) {
      var cached = _materialCache.get(obj.id);
      if (!cached) return;
      // Use cloned material if it exists, otherwise original
      var mat = obj.material;
      mat.color.setHex(cached.color);
      mat.opacity = cached.opacity;
      mat.transparent = cached.transparent;
      mat.depthWrite = cached.depthWrite;
      if (mat.emissive) { mat.emissive.setHex(cached.emissive); mat.emissiveIntensity = cached.emissiveIntensity; }
      mat.needsUpdate = true;
      delete obj._4dColor;
      restored++;
    });
    console.log('§4D_GLASS_RESET restored=' + restored);
  }

  // Seek to task N — the core state applicator
  function seekTo(n) {
    if (!_tasks.length) return;
    n = Math.max(0, Math.min(n, _tasks.length - 1));
    _taskIndex = n;

    var t0 = performance.now();

    // Rotate colour for this task
    _colorIndex = n;

    // Collect GUID sets — each GUID belongs to ONE state only (first assignment wins)
    var builtGuids = {};   // guid → phaseColor
    var activeGuids = {};  // guid → true
    var assigned = {};     // guid → true (prevent duplicates across tasks)
    // First pass: active task GUIDs
    var activeTask = _tasks[n];
    var aGuids = activeTask.guids || [];
    for (var j = 0; j < aGuids.length; j++) {
      if (!assigned[aGuids[j]]) {
        activeGuids[aGuids[j]] = true;
        assigned[aGuids[j]] = true;
      }
    }
    // Second pass: built tasks (0..n-1) — skip GUIDs already assigned to active
    for (var i = 0; i < n; i++) {
      var guids = _tasks[i].guids || [];
      var pc = _guidPhaseColor;
      for (var j = 0; j < guids.length; j++) {
        if (!assigned[guids[j]]) {
          builtGuids[guids[j]] = pc[guids[j]] || 0x888888;
          assigned[guids[j]] = true;
        }
      }
    }

    // Collect ALL guids that belong to any task (for "leftover" detection)
    var allTaskGuids = {};
    for (var ti = 0; ti < _tasks.length; ti++) {
      var tguids = _tasks[ti].guids || [];
      for (var gi = 0; gi < tguids.length; gi++) allTaskGuids[tguids[gi]] = true;
    }

    // Apply states: built + glass immediately, active staggered
    // At last task: leftover elements (not in any task) become BUILT too
    var isLastTask = (n >= _tasks.length - 1);
    var counts = {glass:0, active:0, built:0, leftover:0};
    var activeMeshes = [];  // collect for stagger
    APP.collectMeshes(function(o) { return o.isMesh || o.isInstancedMesh; }).forEach(function(obj) {
      var g = APP.guidMap[obj.id] || obj.userData.guid;
      if (!g) { makeGlass(obj); counts.glass++; return; }
      if (activeGuids[g] !== undefined) {
        // Start as glass, will stagger to active
        makeGlass(obj);
        activeMeshes.push(obj);
        counts.active++;
      }
      else if (builtGuids[g] !== undefined) { makeBuilt(obj, builtGuids[g]); counts.built++; }
      else if (isLastTask && allTaskGuids[g]) {
        // Last task and this guid belongs to a task — mark as built
        makeBuilt(obj, _guidPhaseColor[g] || 0x888888); counts.built++;
      }
      else if (!allTaskGuids[g] && n >= _tasks.length * 0.7) {
        // Past 70% of tasks: unassigned elements progressively appear as built
        makeBuilt(obj, 0x888888); counts.leftover++;
      }
      else { makeGlass(obj); counts.glass++; }
    });

    // Stagger active meshes: ripple in over 2-5 seconds depending on count
    var RIPPLE_MS = activeMeshes.length > 100 ? 5000 : 2000;
    var staggerDelay = activeMeshes.length > 1 ? RIPPLE_MS / activeMeshes.length : 0;
    for (var k = 0; k < activeMeshes.length; k++) {
      (function(mesh, delay) {
        if (delay < 2) { makeActive(mesh, 0xff8c00); return; }
        setTimeout(function() {
          makeActive(mesh, 0xff8c00);
          APP.markDirty();
        }, delay);
      })(activeMeshes[k], k * staggerDelay);
    }

    var elapsed = (performance.now() - t0).toFixed(1);
    console.log('§4D_GLASS_SEEK task=' + n + '/' + _tasks.length +
      ' active=' + counts.active + ' built=' + counts.built + ' glass=' + counts.glass +
      ' leftover=' + (counts.leftover||0) +
      ' stagger=' + Math.round(activeMeshes.length * staggerDelay) + 'ms' +
      ' ms=' + elapsed + ' name="' + _tasks[n].name + '"');
    APP.markDirty();
  }

  // No internal timer — Gantt controls all pacing via 4D_SEEK messages.
  // ghostglass is a pure renderer: 4D_PLAY=glass, 4D_SEEK=show, 4D_RESET=restore.

  // Build phase color lookup from task list
  function buildPhaseColors(tasks) {
    _guidPhaseColor = {};
    for (var i = 0; i < tasks.length; i++) {
      var t = tasks[i];
      var color = PHASE_HEX[t.phase] || 0x888888;
      var guids = t.guids || [];
      for (var j = 0; j < guids.length; j++) {
        _guidPhaseColor[guids[j]] = color;
      }
    }
  }

  // Public API — called by main.js BroadcastChannel handler
  APP._ghostGlass = {
    play: function(tasks, speed) {
      buildGuidMap();
      snapshotMaterials();
      _tasks = tasks;
      buildPhaseColors(tasks);

      // Everything goes glass — Gantt will send 4D_SEEK to fill in tasks
      APP.collectMeshes(function(o) { return o.isMesh || o.isInstancedMesh; }).forEach(makeGlass);
      APP.markDirty();

      _state = 'PLAYING';
      _taskIndex = -1;
      console.log('§4D_GLASS_PLAY tasks=' + tasks.length);
    },

    pause: function() {
      _state = 'PAUSED';
      console.log('§4D_GLASS_PAUSE task=' + _taskIndex + '/' + _tasks.length);
    },

    resume: function(speed) {
      _state = 'PLAYING';
      console.log('§4D_GLASS_RESUME task=' + _taskIndex);
    },

    seek: function(taskIndex) {
      buildGuidMap();
      snapshotMaterials();
      if (_tasks.length) {
        buildPhaseColors(_tasks);
        seekTo(taskIndex);
      }
    },

    reset: function() {
      _state = 'IDLE';
      _tasks = [];
      _taskIndex = -1;
      restoreAll();
      APP.markDirty();
    },

    getState: function() { return _state; },
    getTaskIndex: function() { return _taskIndex; },
    getTaskCount: function() { return _tasks.length; }
  };

  console.log('§GHOSTGLASS_READY');
}
