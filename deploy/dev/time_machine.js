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

  var _zeroMatrix = null; // lazy init
  var _savedInstanceMatrices = {}; // meshId → { idx → Matrix4 }

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
    var frontier = {};  // guid → true (being installed: start_ts <= cursor < end_ts)
    var recent = {};    // guid → true (just finished: end_ts within last linger window)
    var lingerMs = tickMs() * 3; // linger for 3 ticks after completion

    for (var i = 0; i < _ops.length; i++) {
      var op = _ops[i];
      if (op.start_ts > cursorMs) break;
      var guid = op.output_guid;
      if (!guid && op.input_guids && op.input_guids.length) guid = op.input_guids[0];
      if (!guid) continue;

      if (op.end_ts <= cursorMs) {
        placed[guid] = true;
        // Recently finished — yellow linger
        if (cursorMs - op.end_ts < lingerMs) recent[guid] = true;
      } else {
        frontier[guid] = true;
      }
    }

    app.scene.traverse(function(obj) {
      if (!obj.userData) return;

      // ── Single mesh (has userData.guid) ──
      if (obj.userData.guid) {
        var g = obj.userData.guid;
        if (frontier[g]) {
          // Being installed — orange glow
          obj.visible = true;
          if (obj.isMesh) applyHighlight(obj, 0xff8c00, 0.75);
        } else if (recent[g]) {
          // Just finished — faded yellow linger
          obj.visible = true;
          if (obj.isMesh) applyHighlight(obj, 0xffdd44, 0.5);
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
          if (placed[ig] || frontier[ig]) {
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

    if (app.markDirty) app.markDirty();
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

  function updateStatus() {
    var pbar = document.getElementById('tm-progress-bar');
    var range = _projectEnd - _projectStart;
    if (pbar && range > 0) pbar.style.width = Math.round((_cursor - _projectStart) / range * 100) + '%';

    // Count placed and find latest active element
    var placed = 0, active = 0, lastName = '', lastPhase = '';
    for (var i = 0; i < _ops.length; i++) {
      if (_ops[i].start_ts > _cursor) break;
      placed++;
      if (_cursor < _ops[i].end_ts) {
        active++;
        var p = _ops[i].parameters;
        lastPhase = (p && p.phase) || '';
        lastName = (p && p.name) || (p && p.cls) || '';
      }
    }

    var status = document.getElementById('tm-status');
    var label = document.getElementById('tm-label');
    var d = new Date(_cursor);
    if (label) label.textContent = d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'}) + ' — ' + (lastPhase || '—');
    if (status) status.textContent = (lastName || '—') + ' | ' + placed + '/' + _ops.length + ' placed | ' + active + ' active';
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
  function saveVisibility() {
    _savedVisibility = [];
    var app = A();
    if (!app || !app.scene) return;
    app.scene.traverse(function(obj) {
      if (obj.userData && obj.userData.guid) {
        _savedVisibility.push({ obj: obj, vis: obj.visible });
      }
    });
  }

  function restoreVisibility() {
    clearHighlight();
    // Restore instance matrices
    var app = A();
    if (app && app.scene && app._instanceMeta) {
      for (var meshId in _savedInstanceMatrices) {
        var matrices = _savedInstanceMatrices[meshId];
        // Find the mesh in scene
        app.scene.traverse(function(obj) {
          if (obj.isInstancedMesh && obj.id == meshId) {
            for (var idx in matrices) {
              obj.setMatrixAt(parseInt(idx), matrices[idx]);
            }
            obj.instanceMatrix.needsUpdate = true;
            obj.visible = true;
          }
        });
      }
    }
    _savedInstanceMatrices = {};
    _savedVisibility.forEach(function(s) { s.obj.visible = s.vis; });
    _savedVisibility = [];
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
      '<div class="tm-drag" style="width:100%;text-align:center;font-size:9px;color:#555;padding:2px 0;cursor:grab">· · ·</div>' +
      '<div id="tm-status" style="width:100%;text-align:center;font-size:10px;color:#888;padding:2px 0;height:16px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' +
        '4D Construction — weighted parallel playback</div>' +
      '<div style="display:flex;gap:4px;align-items:center;width:100%">' +
        '<span id="tm-label" style="color:#4fc3f7;font-weight:bold;font-size:11px;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">—</span>' +
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
        '<button id="tm-close" style="width:26px">✕</button>' +
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

  // ── Weighted Gantt injection using SEQUENCE_RULES + LABOR_RATES ──
  function injectGantt() {
    var app = A();
    if (!app || !app.db) return false;
    var db = app.db;

    // Ensure kernel_ops table with end timestamp support
    db.run(
      'CREATE TABLE IF NOT EXISTS kernel_ops (' +
      '  id INTEGER PRIMARY KEY, timestamp INTEGER NOT NULL,' +
      '  op_type TEXT NOT NULL, parameters TEXT NOT NULL,' +
      '  input_guids TEXT, output_guid TEXT, undone INTEGER DEFAULT 0)'
    );

    var r;
    try {
      // Exclude IfcOpeningElement (no mesh) — matches streaming.js filter
      r = db.exec(
        'SELECT m.guid, m.ifc_class, m.element_name, m.storey, m.discipline ' +
        'FROM elements_meta m ' +
        'LEFT JOIN element_transforms t ON t.guid = m.guid ' +
        "WHERE m.ifc_class != 'IfcOpeningElement' " +
        'ORDER BY COALESCE(t.center_z, 0), COALESCE(t.center_x, 0), COALESCE(t.center_y, 0)'
      );
    } catch(e) { console.log('§TIME_MACHINE_GANTT table error: ' + e.message); return false; }
    if (!r.length || !r[0].values.length) return false;

    // Witness: total elements being scheduled
    var totalDbElements = r[0].values.length;
    console.log('§TIME_MACHINE_GANTT scheduling ' + totalDbElements + ' elements (excl. IfcOpeningElement)');

    // Get productivity: how many of this class can 1 crew install per 8-hour day?
    // Returns seconds per element
    var SR = window.SEQUENCE_RULES || {};
    var LR = window.LABOR_RATES || {};
    var SD = window.SEQUENCE_DEFAULT || {phase:'Architecture',sequence:6,resource:null};

    // Match IFC class to SEQUENCE_RULES — longest key match wins
    function matchRule(cls) {
      if (!cls) return SD;
      var bestKey = null, bestLen = 0;
      for (var key in SR) {
        if (cls.indexOf(key) >= 0 && key.length > bestLen) {
          bestKey = key; bestLen = key.length;
        }
      }
      return bestKey ? SR[bestKey] : SD;
    }

    function matchProductivity(cls, resource) {
      if (!cls || !resource || !LR[resource]) return 0;
      var labor = LR[resource];
      var bestPk = null, bestLen = 0;
      for (var pk in labor.productivity) {
        if (cls.indexOf(pk) >= 0 && pk.length > bestLen) {
          bestPk = pk; bestLen = pk.length;
        }
      }
      return bestPk ? labor.productivity[bestPk] : 0;
    }

    function getSecondsPerElement(cls) {
      var rule = matchRule(cls);
      var resource = rule.resource;
      if (!resource || !LR[resource]) return 120; // default 2 min
      var prod = matchProductivity(cls, resource);
      if (prod <= 0) return 120;
      // prod = units per day. 1 day = 8 hours = 28800 seconds
      return Math.round(28800 / prod);
    }

    function getPhase(cls) { return matchRule(cls).phase; }
    function getSequence(cls) { return matchRule(cls).sequence; }

    // Storey rank
    function storeyRank(s) {
      if (!s) return 999;
      var u = s.toUpperCase();
      if (u.includes('BASEMENT') || u.startsWith('B')) return -10 + (parseInt(u.replace(/\D/g,'')) || 0);
      if (u === 'GROUND' || u === 'G' || u === 'GF' || u.includes('GROUND')) return 0;
      if (u.includes('ROOF')) return 900;
      var n = parseInt(u.replace(/\D/g,''));
      return isNaN(n) ? 50 : n;
    }

    // Build element list
    var elements = r[0].values.map(function(row) {
      return {
        guid: row[0], cls: row[1], name: row[2], storey: row[3], disc: row[4],
        seq: getSequence(row[1]), sRank: storeyRank(row[3]),
        phase: getPhase(row[1]),
        installSecs: getSecondsPerElement(row[1])
      };
    });

    // Sort: sequence (phase order) → storey (bottom-up) → Z (from SQL)
    elements.sort(function(a, b) {
      if (a.seq !== b.seq) return a.seq - b.seq;
      if (a.sRank !== b.sRank) return a.sRank - b.sRank;
      return 0;
    });

    // Schedule: elements start sequentially within same sequence+storey group.
    // Different sequence groups start after previous finishes on same storey.
    // Parallel: different resources on same storey can overlap.
    //
    // Simplified: walk elements in sorted order.
    // Within same (sequence, storey), elements start one after another.
    // New (sequence, storey) group starts after prev group on same storey finishes.
    // Minimum project = 10 days.

    var totalElements = elements.length;
    var minProjectMs = 10 * 86400000; // 10 days minimum

    // Calculate raw total install seconds
    var totalInstallSecs = 0;
    elements.forEach(function(el) { totalInstallSecs += el.installSecs; });

    // Scale factor: if raw time < 10 days of work hours, stretch to fill
    var rawMs = totalInstallSecs * 1000;
    var scaleFactor = 1;
    var workDayMs = 8 * 3600000;
    var rawDays = rawMs / workDayMs;
    if (rawDays < 10) scaleFactor = (10 * workDayMs) / rawMs;

    // Project start: calculated backwards
    var projectDays = Math.max(10, Math.ceil(rawDays * scaleFactor));
    var startDate = new Date();
    startDate.setDate(startDate.getDate() - projectDays);
    startDate.setHours(7, 0, 0, 0);
    var baseMs = startDate.getTime();

    // Track cursor per (seq, storey) for sequential placement within groups
    // and per storey for phase sequencing
    var groupCursor = {}; // "seq|storey" → next available ms
    var storeyPhaseDone = {}; // "storey|seq" → end ms (for phase dependencies)
    var count = 0;

    elements.forEach(function(el) {
      var groupKey = el.seq + '|' + el.sRank;
      var storeySRank = el.sRank;

      // Find earliest start: after previous element in same group
      var earliest = groupCursor[groupKey] || baseMs;

      // Also after previous phase on same storey finished
      for (var prevSeq = 1; prevSeq < el.seq; prevSeq++) {
        var prevKey = storeySRank + '|' + prevSeq;
        if (storeyPhaseDone[prevKey] && storeyPhaseDone[prevKey] > earliest) {
          earliest = storeyPhaseDone[prevKey];
        }
      }

      // Ensure within working hours (7am-3pm)
      var d = new Date(earliest);
      if (d.getHours() >= 15) {
        d.setDate(d.getDate() + 1); d.setHours(7, 0, 0, 0);
        earliest = d.getTime();
      } else if (d.getHours() < 7) {
        d.setHours(7, 0, 0, 0);
        earliest = d.getTime();
      }

      var durationMs = Math.round(el.installSecs * scaleFactor * 1000);
      var endMs = earliest + durationMs;

      db.run(
        'INSERT INTO kernel_ops (timestamp,op_type,parameters,input_guids,output_guid,undone) VALUES(?,?,?,?,?,0)',
        [earliest, 'ELEMENT_PLACE',
         JSON.stringify({phase: el.phase, cls: el.cls, name: el.name, storey: el.storey, _end_ts: endMs}),
         JSON.stringify([el.guid]), el.guid]
      );
      count++;

      // Advance group cursor
      groupCursor[groupKey] = endMs;
      // Track phase completion for storey
      var phaseKey = storeySRank + '|' + el.seq;
      if (!storeyPhaseDone[phaseKey] || endMs > storeyPhaseDone[phaseKey]) {
        storeyPhaseDone[phaseKey] = endMs;
      }
    });

    var endDate = new Date(Math.max.apply(null, Object.values(groupCursor)));
    // Verify full coverage: DB elements vs injected ops vs scene meshes
    var missing = totalDbElements - count;
    var sceneGuids = 0;
    var a = A();
    if (a && a.scene) {
      var seen = {};
      a.scene.traverse(function(obj) {
        if (obj.userData && obj.userData.guid && !seen[obj.userData.guid]) {
          seen[obj.userData.guid] = true; sceneGuids++;
        }
      });
    }
    console.log('§TIME_MACHINE_GANTT injected=' + count + ' dbElements=' + totalDbElements +
      ' sceneMeshGUIDs=' + sceneGuids +
      (missing > 0 ? ' — WARNING: ' + missing + ' DB elements NOT scheduled' : ' — ALL DB elements scheduled') +
      ', ' + projectDays + ' days, scale=' + scaleFactor.toFixed(2) +
      ', start=' + startDate.toLocaleDateString() + ' end=' + endDate.toLocaleDateString());
    return true;
  }

  // ── Activate / Deactivate ──
  function activate() {
    if (_active) return;
    _ops = loadOps();
    // Auto-clear stale ELEMENT_PLACE ops missing weighted _end_ts
    if (_ops.length && _ops[0].op_type === 'ELEMENT_PLACE' && !_ops[0].parameters._end_ts) {
      try { A().db.run("DELETE FROM kernel_ops WHERE op_type = 'ELEMENT_PLACE'"); } catch(e) {}
      _ops = [];
      console.log('§TIME_MACHINE cleared stale unweighted ops — will re-inject');
    }
    if (!_ops.length) {
      // Show setup progress in status bar
      var st = document.getElementById('tm-status');
      if (st) st.textContent = 'Setting up 4D construction timeline...';
      _panel.style.display = 'flex';
      if (!injectGantt()) {
        if (st) st.textContent = 'No elements found in database';
        console.log('§TIME_MACHINE no ops and no elements — nothing to show');
        return;
      }
      if (st) st.textContent = 'Loading timeline...';
      _ops = loadOps();
      if (!_ops.length) return;
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
    _active = false;
    _panel.style.display = 'none';
    restoreVisibility();
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
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  window.toggleTimeMachine = toggle;
})();
