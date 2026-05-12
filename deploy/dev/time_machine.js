/* time_machine.js — 4D Construction Timeline
   ⏳ toolbar → draggable panel with weighted construction playback.

   Starts fully built. ◀ deconstructs, ▶ builds. << >> jump to start/end.
   DAY/HR/MIN = playback speed AND slider scope.
   Slider drills into where the player stopped:
     DAY → scrub across project days
     HR  → 24 ticks within the stopped day
     MIN → 60 ticks (seconds) within the stopped minute

   Elements have weighted durations from LABOR_RATES productivity.
   Parallel trades: multiple elements active simultaneously.
   Active elements highlighted orange glow, see-through.
   Auto-injects from IFC classes + SEQUENCE_RULES + LABOR_RATES. */

(function() {
  'use strict';
  function A() { return window.APP || window.A; }

  var _active = false;
  var _panel = null;
  var _mode = 'DAY';
  var _ops = [];          // all ops sorted by start_ts
  var _cursor = 0;        // current time (ms) in the project timeline
  var _projectStart = 0;
  var _projectEnd = 0;
  var _days = [];          // distinct day start timestamps
  var _anchorDay = null;
  var _anchorHr = null;
  var _savedVisibility = [];
  var _highlightMeshes = [];

  // ── Query ops from DB ──
  function loadOps() {
    var app = A();
    if (!app || !app.db) return [];
    try {
      var r = app.db.exec(
        'SELECT id, timestamp, op_type, parameters, input_guids, output_guid ' +
        'FROM kernel_ops WHERE undone = 0 ORDER BY timestamp'
      );
      if (!r.length) return [];
      return r[0].values.map(function(row) {
        var params = row[3] ? JSON.parse(row[3]) : {};
        return {
          id: row[0], start_ts: row[1], op_type: row[2],
          end_ts: params._end_ts || (row[1] + 60000), // default 1 min if no end
          parameters: params,
          input_guids: row[4] ? JSON.parse(row[4]) : [],
          output_guid: row[5] || null
        };
      });
    } catch(e) { return []; }
  }

  function computeDays() {
    var seen = {};
    _ops.forEach(function(op) {
      var d = new Date(op.start_ts);
      var key = d.getFullYear() + '-' + (d.getMonth()+1) + '-' + d.getDate();
      if (!seen[key]) seen[key] = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
    });
    _days = Object.values(seen).sort(function(a,b){ return a - b; });
    if (_ops.length) {
      // projectStart = 1ms BEFORE first op so ⏪ = truly empty (no frontier)
      _projectStart = _ops[0].start_ts - 1;
      _projectEnd = Math.max.apply(null, _ops.map(function(o){ return o.end_ts; }));
    }
  }

  // ── Scene: emerge from nothing ──
  // placed (start_ts <= cursor AND end_ts <= cursor) → solid original material
  // frontier (start_ts <= cursor < end_ts) → orange glow, just being installed
  // future (start_ts > cursor) → invisible
  // At cursor <= projectStart: completely empty scene
  // At cursor >= projectEnd: fully built, all solid, no glow

  var _prevCursor = 0; // track previous cursor for frontier detection
  var _sunCycle = false;  // day/night toggle

  var _zeroMatrix = null; // lazy init
  var _savedInstanceMatrices = {}; // meshId → { idx → Matrix4 }

  // ── Metal sparks (desktop only) ──
  var _sparkSystems = [];   // active spark point clouds
  var _sparkMaterial = null; // shared Points material

  function initSparkMaterial() {
    if (_sparkMaterial) return;
    _sparkMaterial = new THREE.PointsMaterial({
      size: 3, sizeAttenuation: true,
      color: 0xffcc44, transparent: true, opacity: 1,
      depthTest: false, blending: THREE.AdditiveBlending
    });
  }

  function spawnSparks(position, scene) {
    initSparkMaterial();
    var count = 5 + Math.floor(Math.random() * 6); // 5-10 points
    var geom = new THREE.BufferGeometry();
    var pos = new Float32Array(count * 3);
    var vel = new Float32Array(count * 3); // velocities
    for (var i = 0; i < count; i++) {
      pos[i*3]   = position.x + (Math.random()-0.5)*0.3;
      pos[i*3+1] = position.y + (Math.random()-0.5)*0.3;
      pos[i*3+2] = position.z + (Math.random()-0.5)*0.3;
      vel[i*3]   = (Math.random()-0.5)*2;
      vel[i*3+1] = Math.random()*3 + 1;       // upward burst
      vel[i*3+2] = (Math.random()-0.5)*2;
    }
    geom.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    var points = new THREE.Points(geom, _sparkMaterial.clone());
    points.renderOrder = 1000;
    scene.add(points);
    _sparkSystems.push({ points: points, vel: vel, born: performance.now(), life: 500 });
  }

  function updateSparks() {
    var now = performance.now();
    for (var i = _sparkSystems.length - 1; i >= 0; i--) {
      var s = _sparkSystems[i];
      var age = now - s.born;
      if (age > s.life) {
        s.points.parent.remove(s.points);
        s.points.geometry.dispose();
        s.points.material.dispose();
        _sparkSystems.splice(i, 1);
        continue;
      }
      // Animate: gravity + fade
      var dt = 0.016; // ~60fps step
      var posArr = s.points.geometry.attributes.position.array;
      for (var j = 0; j < posArr.length; j += 3) {
        posArr[j]   += s.vel[j]   * dt;
        posArr[j+1] += s.vel[j+1] * dt;
        posArr[j+2] += s.vel[j+2] * dt;
        s.vel[j+1] -= 9.8 * dt; // gravity
      }
      s.points.geometry.attributes.position.needsUpdate = true;
      s.points.material.opacity = 1 - (age / s.life);
    }
  }

  function clearSparks() {
    for (var i = 0; i < _sparkSystems.length; i++) {
      var s = _sparkSystems[i];
      if (s.points.parent) s.points.parent.remove(s.points);
      s.points.geometry.dispose();
      s.points.material.dispose();
    }
    _sparkSystems = [];
  }

  function renderAtTime(cursorMs) {
    var app = A();
    if (!app || !app.scene) return;
    if (!_zeroMatrix) _zeroMatrix = new THREE.Matrix4().makeScale(0, 0, 0);
    _prevCursor = _cursor;
    _cursor = cursorMs;

    // Restore previously highlighted meshes to solid
    clearHighlight();

    // Determine which elements to show and their state
    var placed = {};    // guid → true (fully built: end_ts <= cursor)
    var frontier = {};  // guid → {t: 0-1 progress, isSteel: bool}
    var recent = {};    // guid → fade 0-1 (1 = just finished)
    var arrival = {};   // guid → true (just appeared this tick — white flash)
    var lingerMs = tickMs() * 3; // linger for 3 ticks after completion
    var _isMobileTM = !!(window._isMobile || window._isMobileTM);

    for (var i = 0; i < _ops.length; i++) {
      var op = _ops[i];
      if (op.start_ts > cursorMs) break;
      var guid = op.output_guid;
      if (!guid && op.input_guids && op.input_guids.length) guid = op.input_guids[0];
      if (!guid) continue;

      if (op.end_ts <= cursorMs) {
        placed[guid] = true;
        // Recently finished — amber linger with fade
        var age = cursorMs - op.end_ts;
        if (age < lingerMs) recent[guid] = 1 - (age / lingerMs);
      } else {
        var progress = (cursorMs - op.start_ts) / Math.max(1, op.end_ts - op.start_ts);
        var p = op.parameters || {};
        var cls = p.cls || '';
        var isSteel = /^Ifc(Beam|Column|Member|Plate)$/.test(cls) ||
                      (p.resource === 'STEEL_ERECTOR');
        frontier[guid] = { t: progress, isSteel: isSteel };
        // Arrival = first 15% of install time (white flash)
        if (progress < 0.15) arrival[guid] = true;
      }
    }

    app.scene.traverse(function(obj) {
      if (!obj.userData) return;

      // ── Single mesh (has userData.guid) ──
      if (obj.userData.guid) {
        var g = obj.userData.guid;
        if (frontier[g]) {
          obj.visible = true;
          if (obj.isMesh) {
            var ft = frontier[g].t;
            if (ft < 0.1) {
              // Flash: bright solid yellow — fully opaque, depth ON
              applyFlash(obj, 0xffee00);
            } else if (ft < 0.25) {
              applyFlash(obj, 0xffaa33);
            } else {
              applyHighlight(obj, 0xff8c00, 0.75);
            }
          }
        } else if (recent[g] !== undefined) {
          obj.visible = true;
          if (obj.isMesh) {
            var fade = recent[g];
            // Yellow linger fading to solid
            applyHighlight(obj, 0xffdd44, 0.4 + 0.4 * fade);
          }
        } else if (placed[g]) {
          // Fully built — solid original
          obj.visible = true;
          if (obj._tm_highlighted) restoreMaterial(obj);
        } else {
          // Future — invisible
          obj.visible = false;
          if (obj._tm_highlighted) restoreMaterial(obj);
        }
        return;
      }

      // ── InstancedMesh (per-instance GUIDs in _instanceMeta) ──
      if (obj.isInstancedMesh && app._instanceMeta && app._instanceMeta[obj.id]) {
        var metas = app._instanceMeta[obj.id];
        var meshId = obj.id;
        var anyVisible = false;
        var anyFrontier = false;

        // Save original matrices on first encounter
        if (!_savedInstanceMatrices[meshId]) {
          _savedInstanceMatrices[meshId] = {};
          var tmpM = new THREE.Matrix4();
          for (var mi = 0; mi < metas.length; mi++) {
            obj.getMatrixAt(mi, tmpM);
            _savedInstanceMatrices[meshId][mi] = tmpM.clone();
          }
        }

        for (var mi = 0; mi < metas.length; mi++) {
          var ig = metas[mi].guid;
          if (placed[ig] || frontier[ig] || recent[ig] !== undefined) {
            // Restore original matrix (make visible)
            if (_savedInstanceMatrices[meshId][mi]) {
              obj.setMatrixAt(mi, _savedInstanceMatrices[meshId][mi]);
            }
            anyVisible = true;
            if (frontier[ig]) anyFrontier = true;
          } else {
            // Zero-scale = hidden
            obj.setMatrixAt(mi, _zeroMatrix);
          }
        }
        obj.instanceMatrix.needsUpdate = true;
        obj.visible = anyVisible;

        // Highlight entire instanced mesh if any instance is frontier
        if (anyFrontier) {
          applyHighlight(obj, 0xff8c00, 0.75);
        } else if (obj._tm_highlighted) {
          restoreMaterial(obj);
        }
      }
    });

    // Metal sparks for steel frontier elements (desktop only)
    if (!_isMobileTM && _playing) {
      updateSparks();
      app.scene.traverse(function(obj) {
        if (!obj.isMesh || !obj.visible || !obj.userData || !obj.userData.guid) return;
        var fg = frontier[obj.userData.guid];
        if (fg && fg.isSteel && fg.t < 0.5 && Math.random() < 0.3) {
          // Spawn sparks at mesh world position
          var wp = new THREE.Vector3();
          obj.getWorldPosition(wp);
          spawnSparks(wp, app.scene);
        }
      });
    } else if (!_playing) {
      clearSparks();
    }

    applySunCycle(cursorMs);

    if (app.markDirty) app.markDirty();
    // Force immediate render — mobile browsers defer rAF until touch
    if (app.renderer && app.scene && app.camera) app.renderer.render(app.scene, app.camera);
    updateStatus();
  }

  function applyHighlight(obj, color, opacity) {
    color = color || 0xff8c00;
    opacity = opacity || 0.75;
    if (!obj._tm_highlighted) {
      obj._tm_origMaterial = obj.material;
      obj.material = obj.material.clone();
      obj._tm_highlighted = true;
      _highlightMeshes.push(obj);
    }
    var mat = obj.material;
    mat.color.setHex(color);
    if (mat.emissive) { mat.emissive.setHex(color); mat.emissiveIntensity = 0.6; }
    mat.transparent = true;
    mat.opacity = opacity;
    mat.depthTest = false;
    mat.needsUpdate = true;
    obj.renderOrder = 999;
  }

  // Flash: solid bright element, depthTest ON so it looks physical
  function applyFlash(obj, color) {
    if (!obj._tm_highlighted) {
      obj._tm_origMaterial = obj.material;
      obj.material = obj.material.clone();
      obj._tm_highlighted = true;
      _highlightMeshes.push(obj);
    }
    var mat = obj.material;
    mat.color.setHex(color);
    if (mat.emissive) { mat.emissive.setHex(color); mat.emissiveIntensity = 1.5; }
    mat.transparent = false;
    mat.opacity = 1.0;
    mat.depthTest = true;
    mat.needsUpdate = true;
  }

  function restoreMaterial(obj) {
    if (!obj._tm_highlighted) return;
    // Restore original material reference — no leftover color contamination
    if (obj._tm_origMaterial) {
      obj.material.dispose(); // free cloned material
      obj.material = obj._tm_origMaterial;
      delete obj._tm_origMaterial;
    }
    obj.renderOrder = 0;
    obj._tm_highlighted = false;
  }

  function clearHighlight() {
    for (var i = _highlightMeshes.length - 1; i >= 0; i--) {
      restoreMaterial(_highlightMeshes[i]);
    }
    _highlightMeshes = [];
  }

  // ── Day/night — smooth sky + lighting, no shadow plumbing ──
  var _savedClearColor = null;

  // Smooth color lerp between two hex colors
  function lerpColor(a, b, t) {
    var ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
    var br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
    var r = Math.round(ar + (br - ar) * t);
    var g = Math.round(ag + (bg - ag) * t);
    var bl = Math.round(ab + (bb - ab) * t);
    return (r << 16) | (g << 8) | bl;
  }

  function applySunCycle(cursorMs) {
    if (!_sunCycle) return;
    var app = A();
    if (!app || !app.sun) return;

    // Save original sky color once
    if (_savedClearColor === null && app.renderer) {
      _savedClearColor = app.renderer.getClearColor(new THREE.Color()).getHex();
    }

    var h = new Date(cursorMs).getHours();
    var m = new Date(cursorMs).getMinutes();
    var t = h + m / 60; // 0-24 fractional hour

    // Sun arc: smooth sine curve
    var angle = (t / 24) * Math.PI * 2 - Math.PI / 2;
    var elevation = Math.sin(angle); // -1 midnight, +1 noon
    var azimuth = Math.cos(angle);
    var dayFactor = Math.max(0, elevation); // 0 at night, 1 at noon

    // Sun position — orbit around scene center
    var cx = 0, cy = 0, cz = 0;
    if (app.controls && app.controls.target) {
      cx = app.controls.target.x; cy = app.controls.target.y; cz = app.controls.target.z;
    }
    app.sun.position.set(cx + azimuth * 400, Math.max(elevation * 400, 5), cz + 200);

    // Smooth lighting
    app.sun.intensity = 0.05 + dayFactor * 1.2;
    if (app.ambient) app.ambient.intensity = 0.15 + dayFactor * 0.5;
    if (app.hemi) app.hemi.intensity = 0.1 + dayFactor * 0.3;

    // Smooth sky: interpolate through 4 key colors based on dayFactor
    // 0.0 = night (dark blue), 0.3 = dawn/dusk (warm), 0.6 = day (pale blue), 1.0 = noon (bright)
    var NIGHT = 0x0a0a2e, DAWN = 0x664433, DAY = 0x88aacc, NOON = 0xaaccee;
    var skyColor;
    if (dayFactor < 0.3) {
      skyColor = lerpColor(NIGHT, DAWN, dayFactor / 0.3);
    } else if (dayFactor < 0.6) {
      skyColor = lerpColor(DAWN, DAY, (dayFactor - 0.3) / 0.3);
    } else {
      skyColor = lerpColor(DAY, NOON, (dayFactor - 0.6) / 0.4);
    }
    if (app.renderer) app.renderer.setClearColor(skyColor);
  }

  function restoreSky() {
    var app = A();
    if (app && app.renderer && _savedClearColor !== null) {
      app.renderer.setClearColor(_savedClearColor);
      _savedClearColor = null;
    }
  }

  function updateStatus() {
    var pbar = document.getElementById('tm-progress-bar');
    var range = _projectEnd - _projectStart;
    if (pbar && range > 0) pbar.style.width = Math.round((_cursor - _projectStart) / range * 100) + '%';

    // Count placed, collect readable active element names
    var placed = 0;
    var activeNames = [];
    for (var i = 0; i < _ops.length; i++) {
      if (_ops[i].start_ts > _cursor) break;
      placed++;
      if (_cursor < _ops[i].end_ts) {
        var p = _ops[i].parameters;
        // Prefer element name, fall back to IFC class stripped of "Ifc" prefix
        var nm = (p && p.name) || '';
        if (!nm && p && p.cls) nm = p.cls.replace(/^Ifc/, '');
        if (nm && activeNames.length < 3) activeNames.push(nm);
      }
    }

    var status = document.getElementById('tm-status');
    var label = document.getElementById('tm-label');
    var bigCounter = document.getElementById('tm-big-counter');
    var d = new Date(_cursor);
    if (label) label.textContent = d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'});
    if (status) status.textContent = placed + ' placed | ' + (activeNames.join(', ') || 'idle');
    if (bigCounter) {
      var elapsedMs = _cursor - _projectStart;
      var totalDays = Math.floor(elapsedMs / 86400000);
      var remainHrs = Math.floor((elapsedMs % 86400000) / 3600000);
      bigCounter.textContent = 'DAY ' + totalDays + ' \u2502 HR ' + remainHrs;
    }
  }

  // ── Anchor from cursor ──
  function anchorFromCursor() {
    var d = new Date(_cursor);
    _anchorDay = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
    _anchorHr = d.getHours();
  }

  // ── Tick size in ms based on mode ──
  function tickMs() {
    if (_mode === 'DAY') return 3600000;  // advance 1 hour per tick (24 ticks = 1 day)
    if (_mode === 'HR') return 60000;     // advance 1 minute per tick (60 ticks = 1 hour)
    return 10000;                         // advance 10 seconds per tick (fine grain)
  }

  // ── Scene state save/restore ──
  var _savedInstanceState = {}; // meshId → { vis, matrices: { idx → Matrix4 } }

  function saveVisibility() {
    _savedVisibility = [];
    _savedInstanceState = {};
    var app = A();
    if (!app || !app.scene) return;
    app.scene.traverse(function(obj) {
      if (obj.userData && obj.userData.guid) {
        _savedVisibility.push({ obj: obj, vis: obj.visible });
      }
      // Save InstancedMesh state (visibility + all matrices)
      if (obj.isInstancedMesh && app._instanceMeta && app._instanceMeta[obj.id]) {
        var metas = app._instanceMeta[obj.id];
        var matrices = {};
        var tmpM = new THREE.Matrix4();
        for (var i = 0; i < metas.length; i++) {
          obj.getMatrixAt(i, tmpM);
          matrices[i] = tmpM.clone();
        }
        _savedInstanceState[obj.id] = { vis: obj.visible, matrices: matrices, obj: obj };
      }
    });
  }

  function restoreVisibility() {
    clearHighlight();
    // Restore InstancedMesh matrices and visibility from saved state
    for (var meshId in _savedInstanceState) {
      var state = _savedInstanceState[meshId];
      var obj = state.obj;
      for (var idx in state.matrices) {
        obj.setMatrixAt(parseInt(idx), state.matrices[idx]);
      }
      obj.instanceMatrix.needsUpdate = true;
      obj.visible = state.vis;
    }
    _savedInstanceState = {};
    _savedInstanceMatrices = {};
    // Restore single mesh visibility
    _savedVisibility.forEach(function(s) { s.obj.visible = s.vis; });
    _savedVisibility = [];
    var app = A();
    if (app && app.markDirty) app.markDirty();
  }

  // ── UI ──
  function buildPanel() {
    _panel = document.createElement('div');
    _panel.id = 'time-machine-panel';
    _panel.style.cssText =
      'position:fixed;bottom:80px;left:50%;transform:translateX(-50%);z-index:250;' +
      'display:none;flex-direction:column;align-items:center;gap:6px;padding:10px 16px;' +
      'background:rgba(20,20,40,0.85);backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px);' +
      'border:1px solid rgba(79,195,247,0.3);border-radius:12px;' +
      'box-shadow:0 4px 24px rgba(0,0,0,0.5);color:#e0e0e0;font-family:sans-serif;' +
      'width:340px;user-select:none;touch-action:none;';

    _panel.innerHTML =
      '<div style="display:flex;align-items:center;width:100%;cursor:grab" class="tm-drag">' +
        '<button id="tm-share" style="font-size:9px;padding:2px 6px" title="Copy shareable link">&#x1F517; Share</button>' +
        '<button id="tm-sun" style="font-size:12px;padding:2px 6px" title="Day/night cycle">&#x2600;</button>' +
        '<span id="tm-big-counter" style="flex:1;font-size:18px;font-weight:bold;color:#4fc3f7;text-align:center;letter-spacing:1px">DAY 0 | HR 0</span>' +
        '<button id="tm-close" style="width:22px;height:22px;font-size:12px;padding:0;line-height:1" title="Close">&#x2715;</button>' +
      '</div>' +
      '<div id="tm-status" style="width:100%;text-align:center;font-size:13px;color:#ccc;padding:2px 0;min-height:18px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' +
        '4D Construction Playback</div>' +
      '<div style="display:flex;gap:4px;align-items:center;width:100%">' +
        '<span id="tm-label" style="color:#4fc3f7;font-weight:bold;font-size:13px;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">—</span>' +
        '<div style="display:flex;gap:3px">' +
          '<button class="tm-mode" data-mode="DAY">DAY</button>' +
          '<button class="tm-mode" data-mode="HR">HR</button>' +
          '<button class="tm-mode" data-mode="MIN">MIN</button>' +
        '</div>' +
      '</div>' +
      '<input id="tm-slider" type="range" min="0" max="100" value="50" style="width:100%;accent-color:#4fc3f7">' +
      '<div id="tm-progress" style="width:100%;height:3px;background:rgba(255,255,255,0.1);border-radius:2px;overflow:hidden">' +
        '<div id="tm-progress-bar" style="height:100%;width:100%;background:#4fc3f7;transition:width 0.2s"></div>' +
      '</div>' +
      '<div style="display:flex;gap:3px;width:100%;height:30px">' +
        '<button id="tm-start-btn" style="width:30px;font-size:14px" title="Jump to start">&#x25C0;&#x25C0;</button>' +
        '<button id="tm-rev-btn" style="width:30px;font-size:14px" title="Deconstruct">&#x25C0;</button>' +
        '<button id="tm-stop-btn" style="width:30px;font-size:14px" title="Stop">&#x25A0;</button>' +
        '<button id="tm-fwd-btn" style="width:30px;font-size:14px" title="Build">&#x25B6;</button>' +
        '<button id="tm-end-btn" style="width:30px;font-size:14px" title="Jump to end">&#x25B6;&#x25B6;</button>' +
        '<button id="tm-touched" style="flex:1;font-size:9px">Copy Touched</button>' +
        '<button id="tm-new" style="flex:1;font-size:9px">Copy New</button>' +
      '</div>';
    document.body.appendChild(_panel);

    var style = document.createElement('style');
    style.textContent =
      '#time-machine-panel button{background:rgba(255,255,255,0.1);color:#e0e0e0;border:1px solid rgba(79,195,247,0.3);' +
      'border-radius:4px;padding:4px 4px;cursor:pointer;font-size:10px}' +
      '#time-machine-panel button:hover{background:rgba(79,195,247,0.2)}' +
      '#time-machine-panel button.tm-active{background:#1a6b8a;color:#fff}' +
      '@media(max-width:600px){#time-machine-panel{width:92vw;bottom:60px}}';
    document.head.appendChild(style);

    makeDraggable(_panel);

    // Mode buttons
    _panel.querySelectorAll('.tm-mode').forEach(function(btn) {
      btn.addEventListener('pointerup', function(e) {
        e.stopPropagation(); switchMode(btn.dataset.mode);
      });
    });

    document.getElementById('tm-slider').addEventListener('input', onSlide);

    // Transport buttons
    document.getElementById('tm-start-btn').addEventListener('pointerup', function(e) {
      e.stopPropagation(); stopPlayback(); _cursor = _projectStart; renderAtTime(_cursor); anchorFromCursor(); configSlider();
    });
    document.getElementById('tm-end-btn').addEventListener('pointerup', function(e) {
      e.stopPropagation(); stopPlayback(); _cursor = _projectEnd; renderAtTime(_cursor); anchorFromCursor(); configSlider();
    });
    document.getElementById('tm-rev-btn').addEventListener('pointerup', function(e) {
      e.stopPropagation(); startPlayback(-1);
    });
    document.getElementById('tm-fwd-btn').addEventListener('pointerup', function(e) {
      e.stopPropagation(); startPlayback(+1);
    });
    document.getElementById('tm-stop-btn').addEventListener('pointerup', function(e) {
      e.stopPropagation(); stopPlayback();
    });

    document.getElementById('tm-touched').addEventListener('pointerup', function(e) {
      e.stopPropagation(); copyGuids(false);
    });
    document.getElementById('tm-new').addEventListener('pointerup', function(e) {
      e.stopPropagation(); copyGuids(true);
    });
    document.getElementById('tm-share').addEventListener('pointerup', function(e) {
      e.stopPropagation();
      var url = new URL(location.href);
      url.searchParams.set('tm', 'play');
      if (navigator.clipboard) {
        navigator.clipboard.writeText(url.toString());
        var sb = document.getElementById('tm-share');
        if (sb) { sb.textContent = 'Copied!'; setTimeout(function(){ sb.innerHTML = '&#x1F517; Share'; }, 1500); }
      }
      viewerStatus('4D playback link copied to clipboard');
      console.log('§TIME_MACHINE share URL: ' + url.toString());
    });
    document.getElementById('tm-sun').addEventListener('pointerup', function(e) {
      e.stopPropagation();
      _sunCycle = !_sunCycle;
      var btn = document.getElementById('tm-sun');
      if (btn) btn.classList.toggle('tm-active', _sunCycle);
      if (_sunCycle) applySunCycle(_cursor);
      else restoreSky();
      var app = A();
      if (app && app.renderer && app.scene && app.camera) app.renderer.render(app.scene, app.camera);
    });
    document.getElementById('tm-close').addEventListener('pointerup', function(e) {
      e.stopPropagation(); deactivate();
    });
  }

  // ── Draggable (measure.js pattern + mobile) ──
  function makeDraggable(el) {
    var ox, oy, sx, sy, dragging = false;
    var dragStrip = (window._isMobile) ? 50 : 30;
    if (window._isMobile) {
      el.addEventListener('touchstart', function(e) {
        if (e.target.tagName === 'INPUT' || e.target.tagName === 'BUTTON') return;
        var rect = el.getBoundingClientRect();
        var t = e.touches[0];
        if (t.clientY - rect.top <= dragStrip) e.preventDefault();
      }, { passive: false });
    }
    el.addEventListener('pointerdown', function(e) {
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'BUTTON') return;
      var rect = el.getBoundingClientRect();
      if (e.clientY - rect.top > dragStrip) return;
      dragging = true;
      ox = e.clientX; oy = e.clientY;
      sx = rect.left; sy = rect.top;
      el.setPointerCapture(e.pointerId);
      e.preventDefault();
    });
    el.addEventListener('pointermove', function(e) {
      if (!dragging) return;
      el.style.left = (sx + e.clientX - ox) + 'px';
      el.style.top = (sy + e.clientY - oy) + 'px';
      el.style.right = 'auto'; el.style.bottom = 'auto'; el.style.transform = 'none';
    });
    el.addEventListener('pointerup', function() { dragging = false; });
  }

  // ── Mode switching ──
  function switchMode(mode) {
    _mode = mode;
    _panel.querySelectorAll('.tm-mode').forEach(function(btn) {
      btn.classList.toggle('tm-active', btn.dataset.mode === mode);
    });
    anchorFromCursor();
    configSlider();
  }

  function configSlider() {
    var slider = document.getElementById('tm-slider');
    if (_mode === 'DAY') {
      slider.min = 0;
      slider.max = Math.max(_days.length - 1, 0);
      var dayIdx = 0;
      if (_anchorDay !== null) {
        for (var i = 0; i < _days.length; i++) {
          if (_days[i] <= _anchorDay) dayIdx = i;
        }
      } else { dayIdx = _days.length - 1; }
      slider.value = dayIdx;
    } else if (_mode === 'HR') {
      slider.min = 0; slider.max = 23;
      slider.value = (_anchorHr !== null) ? _anchorHr : 12;
    } else {
      slider.min = 0; slider.max = 59;
      slider.value = new Date(_cursor).getSeconds();
    }
  }

  // ── Slider scrub ──
  function onSlide() {
    var slider = document.getElementById('tm-slider');
    var val = parseInt(slider.value);
    var targetMs;

    if (_mode === 'DAY') {
      var dayIdx = Math.min(val, _days.length - 1);
      _anchorDay = _days[dayIdx];
      targetMs = _anchorDay + 86400000; // end of that day
    } else if (_mode === 'HR') {
      _anchorHr = val;
      if (_anchorDay === null && _days.length) _anchorDay = _days[0];
      targetMs = (_anchorDay || _projectStart) + (val + 1) * 3600000;
    } else {
      if (_anchorDay === null && _days.length) _anchorDay = _days[0];
      if (_anchorHr === null) _anchorHr = 0;
      var anchorMinute = new Date(_cursor).getMinutes();
      targetMs = (_anchorDay || _projectStart) + _anchorHr * 3600000 + anchorMinute * 60000 + (val + 1) * 1000;
    }

    renderAtTime(targetMs);
  }

  function copyGuids(onlyNew) {
    var guids = {};
    for (var i = 0; i < _ops.length; i++) {
      if (_ops[i].start_ts > _cursor) break;
      if (onlyNew && _ops[i].op_type !== 'ELEMENT_PLACE') continue;
      var g = _ops[i].output_guid;
      if (g) guids[g] = true;
    }
    var list = Object.keys(guids);
    if (!list.length) return;
    if (navigator.clipboard) navigator.clipboard.writeText(list.join('\n'));
    console.log('§TIME_MACHINE copy ' + (onlyNew ? 'new' : 'all') + ' — ' + list.length + ' GUIDs');
  }

  // ── Playback ──
  var _playing = false;
  var _playDir = 0;
  var _playTimer = null;
  var TICK_MS = 80;

  function startPlayback(dir) {
    if (_playing && _playDir === dir) { stopPlayback(); return; }
    stopPlayback();
    _playing = true;
    _playDir = dir;
    if (dir < 0 && _cursor <= _projectStart) _cursor = _projectEnd;
    if (dir > 0 && _cursor >= _projectEnd) _cursor = _projectStart;
    var btn = document.getElementById(dir < 0 ? 'tm-rev-btn' : 'tm-fwd-btn');
    if (btn) { btn.textContent = '\u25AE\u25AE'; btn.classList.add('tm-active'); }
    playTick();
  }

  function stopPlayback() {
    _playing = false;
    if (_playTimer) { clearTimeout(_playTimer); _playTimer = null; }
    var rb = document.getElementById('tm-rev-btn');
    var fb = document.getElementById('tm-fwd-btn');
    if (rb) { rb.textContent = '\u25C0'; rb.classList.remove('tm-active'); }
    if (fb) { fb.textContent = '\u25B6'; fb.classList.remove('tm-active'); }
    anchorFromCursor();
    configSlider();
  }

  function playTick() {
    if (!_playing) return;

    _cursor += _playDir * tickMs();
    _cursor = Math.max(_projectStart, Math.min(_cursor, _projectEnd));

    renderAtTime(_cursor);

    // Update slider position during playback
    anchorFromCursor();
    configSlider();

    if ((_playDir < 0 && _cursor <= _projectStart) || (_playDir > 0 && _cursor >= _projectEnd)) {
      stopPlayback();
      return;
    }

    _playTimer = setTimeout(playTick, TICK_MS);
  }

  // ══════════════════════════════════════════════════════════════════
  // Z-DRIVEN CONSTRUCTION SCHEDULE
  // ══════════════════════════════════════════════════════════════════
  //
  // One abstract rule: lower Z finishes before higher Z starts.
  // Within same Z-band (storey): seq from SEQUENCE_RULES for phase order.
  // Same resource on same storey = sequential. Different resource = parallel.
  // Always re-inject on activate — never use stale cached ops.

  function injectGantt() {
    var app = A();
    if (!app || !app.db) return false;
    var db = app.db;

    db.run('CREATE TABLE IF NOT EXISTS kernel_ops (' +
      'id INTEGER PRIMARY KEY, timestamp INTEGER NOT NULL,' +
      'op_type TEXT NOT NULL, parameters TEXT NOT NULL,' +
      'input_guids TEXT, output_guid TEXT, undone INTEGER DEFAULT 0)');

    var SR = window.SEQUENCE_RULES || {};
    var LR = window.LABOR_RATES || {};
    var SD = window.SEQUENCE_DEFAULT || {phase:'Architecture',sequence:6,resource:null};

    function matchRule(cls) {
      if (!cls) return SD;
      var bestKey = null, bestLen = 0;
      for (var key in SR) {
        if (cls.indexOf(key) >= 0 && key.length > bestLen) { bestKey = key; bestLen = key.length; }
      }
      return bestKey ? SR[bestKey] : SD;
    }
    function getInstallSecs(cls) {
      var rule = matchRule(cls);
      var resource = rule.resource;
      if (!resource || !LR[resource]) return 120;
      var labor = LR[resource], bestPk = null, bestLen = 0;
      for (var pk in labor.productivity) {
        if (cls.indexOf(pk) >= 0 && pk.length > bestLen) { bestPk = pk; bestLen = pk.length; }
      }
      var prod = bestPk ? labor.productivity[bestPk] : 0;
      return prod > 0 ? Math.round(28800 / prod) : 120;
    }

    // Query elements with spatial Z
    var r;
    try {
      r = db.exec(
        'SELECT m.guid, m.ifc_class, m.element_name, m.storey, m.discipline, ' +
        'COALESCE(t.center_z, 0) as cz ' +
        'FROM elements_meta m ' +
        'LEFT JOIN element_transforms t ON t.guid = m.guid ' +
        "WHERE m.ifc_class != 'IfcOpeningElement' " +
        'ORDER BY cz, COALESCE(t.center_x, 0), COALESCE(t.center_y, 0)'
      );
    } catch(e) { console.log('§GANTT table error: ' + e.message); return false; }
    if (!r.length || !r[0].values.length) return false;

    var totalDbElements = r[0].values.length;

    // ── Z-bands: group elements into Z-bands (storeys inferred from Z gaps) ──
    // Collect all Z values, find natural breaks, assign band index
    var allZ = r[0].values.map(function(row) { return row[5] || 0; });
    allZ.sort(function(a,b){ return a - b; });

    // Find Z-band boundaries: a gap > 1.5m between consecutive elements = new band
    var GAP_THRESHOLD = 1.5;
    var bandBounds = [allZ[0]]; // start of first band
    for (var zi = 1; zi < allZ.length; zi++) {
      if (allZ[zi] - allZ[zi-1] > GAP_THRESHOLD) bandBounds.push(allZ[zi]);
    }

    function zBand(z) {
      for (var bi = bandBounds.length - 1; bi >= 0; bi--) {
        if (z >= bandBounds[bi]) return bi;
      }
      return 0;
    }

    console.log('§GANTT Z-bands: ' + bandBounds.length + ' bands, boundaries at z=' +
      bandBounds.map(function(b){ return b.toFixed(1); }).join(', '));

    // ── Build elements ──
    var elements = r[0].values.map(function(row) {
      var cls = row[1], storey = row[3] || '', cz = row[5] || 0;
      var rule = matchRule(cls);
      return {
        guid: row[0], cls: cls, name: row[2] || '', storey: storey,
        cz: cz, band: zBand(cz),
        seq: rule.sequence, phase: rule.phase,
        resource: rule.resource || '_DEFAULT',
        installSecs: getInstallSecs(cls)
      };
    });

    // ── Sort: Z-band (bottom-up) → seq (phase order) → center_z (fine) ──
    elements.sort(function(a, b) {
      if (a.band !== b.band) return a.band - b.band;
      if (a.seq !== b.seq) return a.seq - b.seq;
      return a.cz - b.cz;
    });

    // Log band contents
    var bandCounts = {};
    elements.forEach(function(el) {
      if (!bandCounts[el.band]) bandCounts[el.band] = {n:0, minZ:el.cz, maxZ:el.cz, phases:{}};
      var bc = bandCounts[el.band];
      bc.n++;
      if (el.cz < bc.minZ) bc.minZ = el.cz;
      if (el.cz > bc.maxZ) bc.maxZ = el.cz;
      bc.phases[el.phase] = (bc.phases[el.phase] || 0) + 1;
    });
    for (var bk in bandCounts) {
      var bc = bandCounts[bk];
      var pp = [];
      for (var ph in bc.phases) pp.push(ph + ':' + bc.phases[ph]);
      console.log('§GANTT band ' + bk + ' z=[' + bc.minZ.toFixed(1) + ',' + bc.maxZ.toFixed(1) + '] ' +
        bc.n + ' elements: ' + pp.join(', '));
    }

    // ── Scale factor ──
    var totalSecs = 0;
    elements.forEach(function(el) { totalSecs += el.installSecs; });
    var rawMs = totalSecs * 1000;
    // Round the clock — 24/7, no weekends
    var fullDayMs = 24 * 3600000;
    var rawDays = rawMs / fullDayMs;
    var scaleFactor = rawDays < 10 ? (10 * fullDayMs) / rawMs : 1;

    var projectDays = Math.max(10, Math.ceil(rawDays * scaleFactor));
    var startDate = new Date();
    startDate.setDate(startDate.getDate() - projectDays);
    startDate.setHours(0, 0, 0, 0);
    var baseMs = startDate.getTime();

    // ── Schedule ──
    var resourceCursor = {};  // "resource|band" → next ms
    var bandSeqDone    = {};  // "band|seq"      → end ms
    var bandDone       = {};  // band (int)      → end ms (structural seq 1-4)
    var count = 0;

    elements.forEach(function(el) {
      var rcKey = el.resource + '|' + el.band;

      // 1. Same resource in same band = sequential
      var earliest = resourceCursor[rcKey] || baseMs;

      // 2. Phase dependency: higher seq waits for lower seq in same band
      for (var ps = 1; ps < el.seq; ps++) {
        var pk = el.band + '|' + ps;
        if (bandSeqDone[pk] && bandSeqDone[pk] > earliest) earliest = bandSeqDone[pk];
      }

      // 3. Z dependency: structural (seq 1-4) on band N waits for structural on band N-1
      //    Non-structural work can proceed concurrently on lower bands
      if (el.band > 0 && el.seq <= 4) {
        var belowDone = bandDone[el.band - 1];
        if (belowDone && belowDone > earliest) earliest = belowDone;
      }

      var durMs = Math.round(el.installSecs * scaleFactor * 1000);
      var endMs = earliest + durMs;

      db.run(
        'INSERT INTO kernel_ops (timestamp,op_type,parameters,input_guids,output_guid,undone) VALUES(?,?,?,?,?,0)',
        [earliest, 'ELEMENT_PLACE',
         JSON.stringify({phase:el.phase, cls:el.cls, name:el.name, storey:el.storey,
           resource:el.resource, _end_ts:endMs}),
         JSON.stringify([el.guid]), el.guid]
      );
      count++;

      resourceCursor[rcKey] = endMs;
      var seqKey = el.band + '|' + el.seq;
      if (!bandSeqDone[seqKey] || endMs > bandSeqDone[seqKey]) bandSeqDone[seqKey] = endMs;
      if (el.seq <= 4) {
        if (!bandDone[el.band] || endMs > bandDone[el.band]) bandDone[el.band] = endMs;
      }
    });

    var endDate = new Date(Math.max.apply(null, Object.values(resourceCursor)));
    var sceneGuids = 0;
    if (app.scene) {
      var seen = {};
      app.scene.traverse(function(obj) {
        if (obj.userData && obj.userData.guid && !seen[obj.userData.guid]) {
          seen[obj.userData.guid] = true; sceneGuids++;
        }
      });
    }
    console.log('§GANTT injected=' + count + ' dbElements=' + totalDbElements +
      ' sceneMeshGUIDs=' + sceneGuids +
      ', bands=' + bandBounds.length + ', ' + projectDays + ' days, scale=' + scaleFactor.toFixed(2) +
      ', start=' + startDate.toLocaleDateString() + ' end=' + endDate.toLocaleDateString());
    return count > 0;
  }

  // ── Activate / Deactivate ──
  function setToolbarHighlight(on) {
    var btn = document.getElementById('time-machine-btn');
    if (btn) btn.style.background = on ? '#1a6b8a' : '#444';
  }

  function viewerStatus(msg) {
    var app = A();
    if (app && app.status) app.status.textContent = msg;
  }

  function activate() {
    if (_active) return;
    // Mobile merged meshes have no guid — re-stream as individual meshes
    var app = A();
    if (app && app._isMobile) {
      app._isMobile = false;
      var bld = app.activeBuilding;
      app.clearStreamed();
      if (bld) { app.streamBuilding(bld); }
      // Wait for re-stream to finish, then activate
      var _reWait = setInterval(function() {
        if (app.buildingsRendered && app.buildingsRendered.size > 0 && !app.streaming) {
          clearInterval(_reWait);
          activate();
        }
      }, 500);
      return;
    }
    setToolbarHighlight(true);
    _ops = loadOps();
    // Auto-clear stale ELEMENT_PLACE ops missing weighted _end_ts
    if (_ops.length && _ops[0].op_type === 'ELEMENT_PLACE' && !_ops[0].parameters._end_ts) {
      try { A().db.run("DELETE FROM kernel_ops WHERE op_type = 'ELEMENT_PLACE'"); } catch(e) {}
      _ops = [];
      console.log('§TIME_MACHINE cleared stale unweighted ops — will re-inject');
    }
    if (!_ops.length) {
      // Show setup progress
      _panel.style.display = 'flex';
      var st = document.getElementById('tm-status');
      if (st) st.textContent = 'Setting up 4D construction timeline...';
      viewerStatus('Time Machine: generating construction schedule...');
      if (!injectGantt()) {
        if (st) st.textContent = 'No elements found in database';
        viewerStatus('Time Machine: no elements found');
        setToolbarHighlight(false);
        console.log('§TIME_MACHINE no ops and no elements — nothing to show');
        return;
      }
      if (st) st.textContent = 'Loading timeline...';
      _ops = loadOps();
      if (!_ops.length) { setToolbarHighlight(false); return; }
      viewerStatus('Time Machine: ' + _ops.length + ' elements scheduled');
    }
    _active = true;
    computeDays();
    saveVisibility();
    // Start fully built — press ⏪ or ◀ to deconstruct
    _cursor = _projectEnd;
    _anchorDay = _days.length ? _days[_days.length - 1] : null;
    _anchorHr = 15;
    _panel.style.display = 'flex';
    switchMode('DAY');
    updateStatus();
    console.log('§TIME_MACHINE ON — ' + _ops.length + ' ops, ' + _days.length + ' days, ' +
      'project: ' + new Date(_projectStart).toLocaleDateString() + ' → ' + new Date(_projectEnd).toLocaleDateString());
  }

  function deactivate() {
    if (!_active) return;
    stopPlayback();
    clearSparks();
    restoreSky();
    _sunCycle = false;
    _active = false;
    _panel.style.display = 'none';
    setToolbarHighlight(false);
    restoreVisibility();
    viewerStatus('');
    console.log('§TIME_MACHINE OFF — restored');
  }

  function toggle() {
    if (_active) deactivate(); else activate();
  }

  // ── Auto-exit on new op ──
  var _origCommit = null;
  function hookCommitOp() {
    if (window.APP && window.APP.kernelOps && window.APP.kernelOps.commitOp) {
      _origCommit = window.APP.kernelOps.commitOp;
      window.APP.kernelOps.commitOp = function() {
        if (_active) deactivate();
        return _origCommit.apply(this, arguments);
      };
    }
  }

  // ── Init ──
  function init() {
    buildPanel();

    var toolbar = document.querySelector('#search-body > div');
    if (!toolbar) return;
    var btn = document.createElement('button');
    btn.id = 'time-machine-btn';
    btn.title = 'Time Machine';
    btn.textContent = '⏳';
    btn.style.cssText =
      'background:#444;color:#fff;border:1px solid #666;border-radius:4px;' +
      'cursor:pointer;font-size:22px;padding:2px;line-height:1';
    btn.addEventListener('pointerup', function(e) {
      e.stopPropagation(); toggle();
    });
    var homeBtn = document.getElementById('header-flag-btn');
    if (homeBtn) toolbar.insertBefore(btn, homeBtn);
    else toolbar.appendChild(btn);

    setTimeout(hookCommitOp, 2000);

    // URL param: ?tm=1 (open time machine) or ?tm=play (open + auto-play forward)
    var tmParam = new URLSearchParams(location.search).get('tm');
    if (tmParam) {
      // Wait for DB to load before activating
      var _tmWait = setInterval(function() {
        var app = A();
        if (app && app.db && app.scene && app.buildingsRendered && app.buildingsRendered.size > 0 && !app.streaming) {
          clearInterval(_tmWait);
          activate();
          if (tmParam === 'play') {
            // Jump to start then play forward
            _cursor = _projectStart;
            renderAtTime(_cursor);
            startPlayback(+1);
          }
        }
      }, 500);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  window.toggleTimeMachine = toggle;
})();
