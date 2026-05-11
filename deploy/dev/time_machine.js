/* time_machine.js — Temporal navigation over kernel_ops log
   Slider drills: DAY → HR → MIN. Actions: Copy Touched, Copy New.
   Read-only overlay — does not mutate op stack.
   Exit restores full scene state. New op auto-exits. */

(function() {
  'use strict';
  function A() { return window.APP || window.A; }

  var _active = false;
  var _panel = null;
  var _mode = 'DAY'; // DAY | HR | MIN
  var _ops = [];     // all ops from DB
  var _days = [];    // distinct day timestamps
  var _selectedDay = null; // ms start-of-day
  var _selectedHr = null;  // 0-23
  var _sliceStart = 0;
  var _sliceEnd = 0;
  var _savedVisibility = []; // restore on exit

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
        return {
          id: row[0], timestamp: row[1], op_type: row[2],
          parameters: row[3] ? JSON.parse(row[3]) : {},
          input_guids: row[4] ? JSON.parse(row[4]) : [],
          output_guid: row[5] || null
        };
      });
    } catch(e) { return []; }
  }

  function distinctDays(ops) {
    var seen = {};
    ops.forEach(function(op) {
      var d = new Date(op.timestamp);
      var key = d.getFullYear() + '-' + (d.getMonth()+1) + '-' + d.getDate();
      if (!seen[key]) seen[key] = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
    });
    return Object.values(seen).sort(function(a,b){ return a - b; });
  }

  // ── Slice computation ──
  function computeSlice(sliderVal) {
    if (_mode === 'DAY') {
      var idx = Math.min(sliderVal, _days.length - 1);
      _selectedDay = _days[idx];
      _sliceStart = _selectedDay;
      _sliceEnd = _selectedDay + 86400000;
    } else if (_mode === 'HR') {
      _selectedHr = sliderVal; // 0-23
      _sliceStart = _selectedDay + sliderVal * 3600000;
      _sliceEnd = _sliceStart + 3600000;
    } else { // MIN
      var min = sliderVal; // 0-59
      _sliceStart = _selectedDay + _selectedHr * 3600000 + min * 60000;
      _sliceEnd = _sliceStart + 60000;
    }
  }

  function opsInSlice() {
    return _ops.filter(function(op) {
      return op.timestamp >= _sliceStart && op.timestamp < _sliceEnd;
    });
  }

  function guidsInSlice(onlyNew) {
    var ops = opsInSlice();
    var guids = {};
    ops.forEach(function(op) {
      if (onlyNew && op.op_type !== 'CREATE') return;
      if (op.output_guid) guids[op.output_guid] = true;
      if (!onlyNew && op.input_guids) {
        op.input_guids.forEach(function(g) { guids[g] = true; });
      }
    });
    return Object.keys(guids);
  }

  // ── Scene isolation ──
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
    _savedVisibility.forEach(function(s) { s.obj.visible = s.vis; });
    _savedVisibility = [];
    if (A() && A().markDirty) A().markDirty();
  }

  function isolateGuids(guids) {
    var app = A();
    if (!app || !app.scene) return;
    var set = {};
    guids.forEach(function(g) { set[g] = true; });
    app.scene.traverse(function(obj) {
      if (obj.userData && obj.userData.guid) {
        obj.visible = !!set[obj.userData.guid];
      }
    });
    if (app.markDirty) app.markDirty();
  }

  function showAll() {
    var app = A();
    if (!app || !app.scene) return;
    app.scene.traverse(function(obj) {
      if (obj.userData && obj.userData.guid) obj.visible = true;
    });
    if (app.markDirty) app.markDirty();
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
      'box-shadow:0 4px 24px rgba(0,0,0,0.5);color:#e0e0e0;font-size:11px;font-family:sans-serif;' +
      'min-width:280px;user-select:none;';

    _panel.innerHTML =
      '<div style="display:flex;gap:4px;align-items:center;width:100%">' +
        '<span id="tm-label" style="color:#4fc3f7;font-weight:bold;min-width:80px">—</span>' +
        '<div style="flex:1;display:flex;gap:3px">' +
          '<button class="tm-mode" data-mode="DAY" style="flex:1">DAY</button>' +
          '<button class="tm-mode" data-mode="HR" style="flex:1">HR</button>' +
          '<button class="tm-mode" data-mode="MIN" style="flex:1">MIN</button>' +
        '</div>' +
      '</div>' +
      '<input id="tm-slider" type="range" min="0" max="1" value="0" style="width:100%;accent-color:#4fc3f7">' +
      '<div style="display:flex;gap:4px;width:100%">' +
        '<button id="tm-play-btn" style="width:36px;font-size:14px" title="Play backwards">◀</button>' +
        '<button id="tm-stop-btn" style="width:36px;font-size:14px" title="Stop">⏹</button>' +
        '<button id="tm-touched" style="flex:1">Copy Touched</button>' +
        '<button id="tm-new" style="flex:1">Copy New</button>' +
        '<button id="tm-close" style="width:30px">✕</button>' +
      '</div>';
    document.body.appendChild(_panel);

    // Style buttons
    var style = document.createElement('style');
    style.textContent =
      '#time-machine-panel button{background:rgba(255,255,255,0.1);color:#e0e0e0;border:1px solid rgba(79,195,247,0.3);' +
      'border-radius:4px;padding:4px 8px;cursor:pointer;font-size:10px}' +
      '#time-machine-panel button:hover{background:rgba(79,195,247,0.2)}' +
      '#time-machine-panel button.tm-active{background:#1a6b8a;color:#fff}' +
      '@media(max-width:600px){#time-machine-panel{min-width:unset;width:90vw;bottom:60px}}';
    document.head.appendChild(style);

    // Mode buttons
    _panel.querySelectorAll('.tm-mode').forEach(function(btn) {
      btn.addEventListener('pointerup', function(e) {
        e.stopPropagation();
        setMode(btn.dataset.mode);
      });
    });

    // Slider
    document.getElementById('tm-slider').addEventListener('input', onSlide);

    // Action buttons
    document.getElementById('tm-touched').addEventListener('pointerup', function(e) {
      e.stopPropagation();
      var guids = guidsInSlice(false);
      copyGuids(guids, 'touched');
    });
    document.getElementById('tm-new').addEventListener('pointerup', function(e) {
      e.stopPropagation();
      var guids = guidsInSlice(true);
      copyGuids(guids, 'new');
    });
    document.getElementById('tm-play-btn').addEventListener('pointerup', function(e) {
      e.stopPropagation(); startPlayback();
    });
    document.getElementById('tm-stop-btn').addEventListener('pointerup', function(e) {
      e.stopPropagation(); stopPlayback(); showAll();
    });
    document.getElementById('tm-close').addEventListener('pointerup', function(e) {
      e.stopPropagation();
      deactivate();
    });
  }

  function setMode(mode) {
    _mode = mode;
    _panel.querySelectorAll('.tm-mode').forEach(function(btn) {
      btn.classList.toggle('tm-active', btn.dataset.mode === mode);
    });
    var slider = document.getElementById('tm-slider');
    if (mode === 'DAY') {
      slider.max = Math.max(_days.length - 1, 0);
      slider.value = 0;
    } else if (mode === 'HR') {
      slider.max = 23;
      slider.value = 0;
    } else {
      slider.max = 59;
      slider.value = 0;
    }
    onSlide();
  }

  function onSlide() {
    var slider = document.getElementById('tm-slider');
    var val = parseInt(slider.value);
    computeSlice(val);
    updateLabel();
    // Isolate elements in current slice
    var guids = guidsInSlice(false);
    if (guids.length > 0) {
      isolateGuids(guids);
    } else {
      showAll();
    }
  }

  function updateLabel() {
    var label = document.getElementById('tm-label');
    if (_mode === 'DAY') {
      var d = new Date(_sliceStart);
      label.textContent = d.toLocaleDateString();
    } else if (_mode === 'HR') {
      var d2 = new Date(_sliceStart);
      label.textContent = d2.toLocaleDateString() + ' ' + String(_selectedHr).padStart(2,'0') + ':00';
    } else {
      var d3 = new Date(_sliceStart);
      label.textContent = d3.toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'});
    }
    // Show op count
    var count = opsInSlice().length;
    label.textContent += ' (' + count + ' ops)';
  }

  function copyGuids(guids, type) {
    if (!guids.length) {
      console.log('§TIME_MACHINE copy ' + type + ' — no elements in slice');
      return;
    }
    var text = guids.join('\n');
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text);
    }
    // Also highlight them
    isolateGuids(guids);
    console.log('§TIME_MACHINE copy ' + type + ' — ' + guids.length + ' GUIDs');

    // Flash button
    var btn = document.getElementById(type === 'new' ? 'tm-new' : 'tm-touched');
    if (btn) {
      btn.style.background = '#4fc3f7';
      setTimeout(function() { btn.style.background = ''; }, 300);
    }
  }

  // ── Reverse Playback ──
  var _playing = false;
  var _playTimer = null;
  var _playIdx = 0;
  var FRAME_MS = 800;      // time per op frame
  var SKIP_FRAME_MS = 150; // compressed gap frame
  var GAP_THRESHOLD = 30000; // 30s = "dead time"

  function startPlayback() {
    if (_playing) { stopPlayback(); return; }
    if (!_ops.length) return;
    _playing = true;
    _playIdx = _ops.length - 1; // start from most recent
    var pb = document.getElementById('tm-play-btn');
    if (pb) { pb.textContent = '⏸'; pb.classList.add('tm-active'); }
    playFrame();
  }

  function stopPlayback() {
    _playing = false;
    if (_playTimer) { clearTimeout(_playTimer); _playTimer = null; }
    var pb = document.getElementById('tm-play-btn');
    if (pb) { pb.textContent = '◀'; pb.classList.remove('tm-active'); }
  }

  function playFrame() {
    if (!_playing || _playIdx < 0) { stopPlayback(); showAll(); return; }

    var op = _ops[_playIdx];
    // Show timestamp
    var label = document.getElementById('tm-label');
    var d = new Date(op.timestamp);
    label.textContent = d.toLocaleTimeString() + ' — ' + op.op_type;

    // Isolate this op's elements
    var guids = [];
    if (op.output_guid) guids.push(op.output_guid);
    if (op.input_guids) guids = guids.concat(op.input_guids);
    if (guids.length > 0) {
      isolateGuids(guids);
    } else {
      showAll();
    }

    // Determine delay to next frame
    var delay = FRAME_MS;
    if (_playIdx > 0) {
      var gap = op.timestamp - _ops[_playIdx - 1].timestamp;
      if (gap > GAP_THRESHOLD) delay = SKIP_FRAME_MS; // compress dead time
    }

    _playIdx--;
    _playTimer = setTimeout(playFrame, delay);
  }

  // ── Synthetic Gantt injection ──
  function injectGantt() {
    var app = A();
    if (!app || !app.db) return false;
    var db = app.db;

    // Ensure table
    db.run(
      'CREATE TABLE IF NOT EXISTS kernel_ops (' +
      '  id INTEGER PRIMARY KEY, timestamp INTEGER NOT NULL,' +
      '  op_type TEXT NOT NULL, parameters TEXT NOT NULL,' +
      '  input_guids TEXT, output_guid TEXT, undone INTEGER DEFAULT 0)'
    );

    var r = db.exec('SELECT guid, ifc_class, element_name, storey, discipline FROM elements ORDER BY storey, ifc_class');
    if (!r.length || !r[0].values.length) return false;

    var phases = [
      { match: ['IfcFooting','IfcPile','IfcFoundation'], phase: 'Substructure', day: 0 },
      { match: ['IfcColumn'], phase: 'Columns', day: 1 },
      { match: ['IfcBeam'], phase: 'Beams', day: 2 },
      { match: ['IfcSlab','IfcRoof'], phase: 'Slabs & Roof', day: 3 },
      { match: ['IfcWall','IfcWallStandardCase','IfcCurtainWall'], phase: 'Walls', day: 4 },
      { match: ['IfcDoor','IfcWindow'], phase: 'Openings', day: 5 },
      { match: ['IfcStair','IfcRailing','IfcRamp'], phase: 'Circulation', day: 6 },
      { match: ['IfcPipeSegment','IfcDuctSegment','IfcFlowTerminal','IfcFlowSegment','IfcFlowFitting'], phase: 'MEP', day: 7 },
      { match: ['IfcFurnishingElement','IfcBuildingElementProxy','IfcCovering'], phase: 'Fitout', day: 8 },
    ];

    function getPhase(cls) {
      for (var i = 0; i < phases.length; i++)
        for (var j = 0; j < phases[i].match.length; j++)
          if (cls && cls.indexOf(phases[i].match[j]) >= 0) return phases[i];
      return { phase: 'Other', day: 9 };
    }

    var grouped = {};
    r[0].values.forEach(function(row) {
      var p = getPhase(row[1]);
      var k = p.day;
      if (!grouped[k]) grouped[k] = { phase: p.phase, els: [] };
      grouped[k].els.push({ guid: row[0], cls: row[1], name: row[2], storey: row[3], disc: row[4] });
    });

    // 10 days ago, 7am start
    var base = new Date(Date.now() - 10 * 86400000);
    base.setHours(7, 0, 0, 0);
    var baseMs = base.getTime();
    var count = 0;

    Object.keys(grouped).sort(function(a,b){return a-b;}).forEach(function(k) {
      var g = grouped[k];
      var dayStart = baseMs + parseInt(k) * 86400000;
      var workMs = 8 * 3600000;
      var interval = Math.max(Math.floor(workMs / g.els.length), 500);

      g.els.forEach(function(el, idx) {
        db.run(
          'INSERT INTO kernel_ops (timestamp,op_type,parameters,input_guids,output_guid,undone) VALUES(?,?,?,?,?,0)',
          [dayStart + idx * interval, 'ELEMENT_PLACE',
           JSON.stringify({phase:g.phase, cls:el.cls, name:el.name, storey:el.storey}),
           JSON.stringify([el.guid]), el.guid]
        );
        count++;
      });
    });

    console.log('§TIME_MACHINE_GANTT injected ' + count + ' construction ops across ' +
      Object.keys(grouped).length + ' phases');
    return true;
  }

  // ── Activate / Deactivate ──
  function activate() {
    if (_active) return;
    _ops = loadOps();
    if (!_ops.length) {
      // Auto-inject construction Gantt from DB elements
      if (!injectGantt()) {
        console.log('§TIME_MACHINE no ops and no elements — nothing to show');
        return;
      }
      _ops = loadOps();
      if (!_ops.length) return;
    }
    _active = true;
    _days = distinctDays(_ops);
    saveVisibility();
    _panel.style.display = 'flex';
    setMode('DAY');
    console.log('§TIME_MACHINE ON — ' + _ops.length + ' ops, ' + _days.length + ' days');
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
    // Intercept kernel_ops.commitOp to auto-close time machine
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

    // Toolbar button (sunglass icon = ⏳ hourglass for time)
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

    // Hook commitOp after a short delay (main.js needs to run first)
    setTimeout(hookCommitOp, 2000);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  window.toggleTimeMachine = toggle;
})();
