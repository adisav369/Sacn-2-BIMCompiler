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

  // ── Console log capture + IndexedDB persistence for bug reports ───────────
  // Hooks console.log/warn/error to buffer §-tagged lines in memory + IndexedDB.
  // IndexedDB store: 'bim_ootb_logs' / 'entries' — survives tab close.
  // Memory buffer: last 100 lines (fast access for reportBug).
  // Called once at setup — guards against double-hook.
  if (!window._bimLogBuffer) {
    window._bimLogBuffer = [];
    window._bimLogDb = null;

    // Open/create IndexedDB for logs
    try {
      var logReq = indexedDB.open('bim_ootb_logs', 1);
      logReq.onupgradeneeded = function() {
        var db = logReq.result;
        if (!db.objectStoreNames.contains('entries')) {
          var store = db.createObjectStore('entries', { keyPath: 'id', autoIncrement: true });
          store.createIndex('ts', 'ts');
        }
      };
      logReq.onsuccess = function() { window._bimLogDb = logReq.result; };
      logReq.onerror = function() {}; // silent — memory buffer still works
    } catch(e) {}

    var _origLog = console.log, _origWarn = console.warn, _origErr = console.error;
    var _idbQueue = [];   // batch queue for IndexedDB writes
    var _idbTimer = null;
    var _flushIdb = function() {
      _idbTimer = null;
      if (!window._bimLogDb || _idbQueue.length === 0) return;
      try {
        var tx = window._bimLogDb.transaction('entries', 'readwrite');
        var store = tx.objectStore('entries');
        var batch = _idbQueue.splice(0, _idbQueue.length);
        for (var i = 0; i < batch.length; i++) store.add(batch[i]);
      } catch(e) {}
    };
    var _repeatCounts = {};  // dedup counter for repetitive § tags
    var _capture = function(level, args) {
      var line = Array.prototype.slice.call(args).join(' ');
      if (line.indexOf('§') >= 0 || level !== 'log') {
        // Throttle repetitive § tags — keep 1st, every 10th, warn/error always
        if (level === 'log') {
          var tagMatch = line.match(/§\w+/);
          if (tagMatch) {
            var tag = tagMatch[0];
            _repeatCounts[tag] = (_repeatCounts[tag] || 0) + 1;
            var n = _repeatCounts[tag];
            if (n > 1 && n % 10 !== 0) return; // skip 2-9, 11-19, etc.
          }
        }
        var ts = new Date().toISOString();
        var buf = window._bimLogBuffer;
        buf.push(ts + ' [' + level + '] ' + line);
        if (buf.length > 100) buf.shift();
        _idbQueue.push({ ts: ts, level: level, msg: line });
        if (!_idbTimer) _idbTimer = setTimeout(_flushIdb, 2000);
      }
    };
    console.log = function() { _capture('log', arguments); _origLog.apply(console, arguments); };
    console.warn = function() { _capture('WARN', arguments); _origWarn.apply(console, arguments); };
    console.error = function() { _capture('ERROR', arguments); _origErr.apply(console, arguments); };
    // Flush on page unload
    window.addEventListener('beforeunload', _flushIdb);
  }

  // ── A.reportBug() — one-click bug report to GitHub ─────────────────────────
  // 1. Captures canvas screenshot → clipboard
  // 2. Collects browser, OS, building, element count, last §-tagged console lines
  // 3. Opens pre-filled GitHub issue with all context
  A.reportBug = function() {
    // Show confirmation dialog
    var overlay = document.createElement('div');
    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100vw;height:100vh;z-index:10000;background:rgba(0,0,0,0.6);backdrop-filter:blur(4px);display:flex;justify-content:center;align-items:center';
    overlay.onclick = function(e) { if (e.target === overlay) { overlay.remove(); } };
    overlay.innerHTML = '<div style="background:rgba(10,10,30,0.97);border-radius:14px;padding:24px 28px;border:1px solid rgba(255,138,101,0.4);font-family:\'Segoe UI\',sans-serif;color:#e0e0e0;max-width:400px;width:90%;text-align:center">' +
      '<img src="help.png" alt="Help" style="height:40px;margin-bottom:10px">' +
      '<div style="font-size:16px;font-weight:700;color:#ff8a65;margin-bottom:8px">Report a Bug</div>' +
      '<div style="color:#aaa;font-size:12px;margin-bottom:12px;line-height:1.6">' +
        'Your report will include:<br>' +
        '&#10003; Browser &amp; screen info<br>' +
        '&#10003; Building &amp; element count<br>' +
        '&#10003; Console debug log (last 50 lines)<br>' +
        'You can paste a screenshot in the report if needed.' +
      '</div>' +
      '<div style="margin-bottom:12px"><textarea id="_bug_desc" placeholder="Describe the problem (optional)..." style="width:90%;height:60px;background:#222;color:#eee;border:1px solid #555;border-radius:6px;padding:8px;font-size:12px;resize:vertical"></textarea></div>' +
      '<div style="display:flex;gap:10px;justify-content:center;flex-wrap:wrap">' +
        '<button id="_bug_github" style="padding:8px 18px;background:#ff8a65;color:#000;border:none;border-radius:8px;font-size:12px;font-weight:700;cursor:pointer">Submit to GitHub</button>' +
        '<button id="_bug_email" style="padding:8px 18px;background:#4fc3f7;color:#000;border:none;border-radius:8px;font-size:12px;font-weight:700;cursor:pointer">Send via Email</button>' +
        '<button id="_bug_cancel" style="padding:8px 18px;background:#333;color:#aaa;border:1px solid #555;border-radius:8px;font-size:12px;cursor:pointer">Cancel</button>' +
      '</div>' +
      '<div style="color:#555;font-size:9px;margin-top:10px">No GitHub account? Use Email.</div>' +
    '</div>';
    document.body.appendChild(overlay);
    document.getElementById('_bug_cancel').onclick = function() { overlay.remove(); };
    document.getElementById('_bug_github').onclick = function() {
      var desc = document.getElementById('_bug_desc').value;
      overlay.remove();
      A._doReportBug('github', desc);
    };
    document.getElementById('_bug_email').onclick = function() {
      var desc = document.getElementById('_bug_desc').value;
      overlay.remove();
      A._doReportBug('email', desc);
    };
  };

  A._doReportBug = function(mode, userDesc) {

    // Collect context
    var ua = navigator.userAgent;
    var platform = navigator.platform || 'unknown';
    var screen = window.screen ? window.screen.width + 'x' + window.screen.height : 'unknown';
    var building = '';
    var elementCount = 0;
    try {
      if (A.db) {
        var r = A.dbQueryFirst("SELECT value FROM project_metadata WHERE key='building_name'");
        if (r) building = r[0];
        var c = A.dbQueryFirst("SELECT COUNT(*) FROM elements_meta");
        if (c) elementCount = c[0];
      }
    } catch(e) {}

    // Pull logs — try IndexedDB first (has previous sessions), fallback to memory
    var _openIssue = function(logs) {
      var desc = userDesc ? userDesc : '(no description provided)';
      var envBlock = [
        'Browser: ' + ua,
        'Platform: ' + platform,
        'Screen: ' + screen,
        'Building: ' + (building || '(none loaded)'),
        'Elements: ' + elementCount,
        'URL: ' + location.href,
      ].join('\n');

      if (mode === 'email') {
        // Email — plain text, screenshot auto-downloaded separately
        var subject = 'BIM OOTB Bug Report — ' + (building || 'no building');
        var emailBody = [
          'Bug Report',
          '==========',
          '',
          'Description: ' + desc,
          '',
          'Environment:',
          envBlock,
          '',
          'Console Log (last 50 lines):',
          logs || '(no logs captured)',
          '',
          '---',
          'Please attach a screenshot if needed (use PrtScn or snipping tool).',
          'Auto-generated by BIM OOTB bug reporter',
        ].join('\n');

        window.location.href = 'mailto:red1org@gmail.com?subject=' +
          encodeURIComponent(subject) + '&body=' + encodeURIComponent(emailBody);
      } else {
        // GitHub issue — markdown
        var body = [
          '## What happened?',
          desc,
          '',
          '## Environment',
          '| | |',
          '|---|---|',
          '| Browser | ' + ua + ' |',
          '| Platform | ' + platform + ' |',
          '| Screen | ' + screen + ' |',
          '| Building | ' + (building || '(none loaded)') + ' |',
          '| Elements | ' + elementCount + ' |',
          '| URL | ' + location.href + ' |',
          '',
          '**Screenshot** — paste here if needed (use PrtScn or snipping tool, then Ctrl+V):',
          '',
          '<details><summary>Console log (last 50 lines)</summary>',
          '',
          '```',
          logs || '(no logs captured)',
          '```',
          '</details>',
          '',
          '---',
          '_Auto-generated by BIM OOTB bug reporter_',
        ].join('\n');

        var title = encodeURIComponent('Bug: ' + (userDesc ? userDesc.slice(0, 60) : ''));
        var url = 'https://github.com/red1oon/BIMCompiler/issues/new?title=' + title +
                  '&body=' + encodeURIComponent(body) + '&labels=bug';

        // GitHub URL limit ~8KB — truncate logs if needed
        if (url.length > 8000) {
          var shortLogs = logs.split('\n').slice(-20).join('\n');
          body = body.replace(/```[\s\S]*?```/, '```\n' + shortLogs + '\n```');
          url = 'https://github.com/red1oon/BIMCompiler/issues/new?title=' + title +
                '&body=' + encodeURIComponent(body) + '&labels=bug';
        }

        window.open(url, '_blank');
      }
    };

    // Try IndexedDB for full history (includes previous sessions)
    if (window._bimLogDb) {
      try {
        var tx = window._bimLogDb.transaction('entries', 'readonly');
        var store = tx.objectStore('entries');
        var all = store.getAll();
        all.onsuccess = function() {
          var entries = all.result || [];
          // Last 50 entries from IndexedDB
          var idbLogs = entries.slice(-50).map(function(e) {
            return e.ts + ' [' + e.level + '] ' + e.msg;
          }).join('\n');
          _openIssue(idbLogs);
        };
        all.onerror = function() {
          _openIssue((window._bimLogBuffer || []).slice(-50).join('\n'));
        };
      } catch(e) {
        _openIssue((window._bimLogBuffer || []).slice(-50).join('\n'));
      }
    } else {
      _openIssue((window._bimLogBuffer || []).slice(-50).join('\n'));
    }
  };

  // ── Floating bug-report FAB — show on idle, hide on camera move ───────────
  // Uses pointer/touch/wheel events on canvas — works regardless of controls init order.
  var _bugFab = document.getElementById('bug-fab');
  if (_bugFab) {
    var _idleTimer = null;
    var _showFab = function() { if (_bugFab) _bugFab.style.display = 'block'; };
    var _hideFab = function() { if (_bugFab) _bugFab.style.display = 'none'; };
    var _onMove = function() {
      _hideFab();
      if (_idleTimer) clearTimeout(_idleTimer);
      _idleTimer = setTimeout(_showFab, 2000);
    };
    var cvs = document.querySelector('canvas');
    if (cvs) {
      cvs.addEventListener('pointerdown', _onMove);
      cvs.addEventListener('wheel', _onMove);
    }
    // Show after initial 10s idle
    setTimeout(_showFab, 10000);
  }

  console.log('§HELPERS_READY collectMeshes+filterInstancedMesh+dbQuery+reportBug+bugFab');
}
