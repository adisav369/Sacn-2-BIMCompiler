// navigate.js — S233: Find & Navigate — indoor wayfinding
// Implementing S233_find_and_navigate.md — Witness: W-NAV
// Dependencies: walk.js (findNearestDoorPosition, walkStoreyLevels, advanceWalkStep),
//   nlp.js (A._nlpExecute, A.inputWasVoice), scene.js (ifc2three), streaming.js (A.db)

function setupNavigate(A) {
  'use strict';

  // ── CSS injection ──
  var style = document.createElement('style');
  style.textContent = [
    '#find-panel {',
    '  position: fixed; top: 50%; right: 16px; transform: translateY(-50%);',
    '  z-index: 50; width: 320px; max-width: 40vw;',
    '  background: rgba(30, 25, 8, 0.92); backdrop-filter: blur(20px);',
    '  -webkit-backdrop-filter: blur(20px);',
    '  border: 1px solid rgba(255, 191, 0, 0.15); border-radius: 12px;',
    '  padding: 0; font-family: "Segoe UI", system-ui, sans-serif;',
    '  color: #ffe0a0; box-shadow: 0 12px 40px rgba(0,0,0,0.5);',
    '  display: none; max-height: 70vh; overflow: hidden;',
    '}',
    '#find-panel .find-search-bar {',
    '  display: flex; align-items: center; gap: 8px; padding: 10px 14px;',
    '  border-bottom: 1px solid rgba(255,191,0,0.1);',
    '}',
    '#find-panel .find-search-bar .find-icon { font-size: 16px; opacity: 0.5; flex-shrink: 0; }',
    '#find-panel .find-filters {',
    '  display: flex; gap: 6px; padding: 6px 14px;',
    '  border-bottom: 1px solid rgba(255,191,0,0.08);',
    '}',
    '#find-panel select, #find-panel input[type="text"] {',
    '  padding: 6px 8px; background: rgba(0,0,0,0.25); color: #ffe0a0;',
    '  border: 1px solid rgba(255,191,0,0.12); border-radius: 6px; font-size: 13px;',
    '}',
    '#find-panel #find-name {',
    '  flex: 1; border: none; background: transparent; color: #ffe0a0;',
    '  font-size: 15px; outline: none; padding: 4px 0;',
    '}',
    '#find-panel #find-name::placeholder { color: rgba(255,224,160,0.3); }',
    '#find-panel select { flex: 1; font-size: 12px; }',
    '#find-panel select option { background: #1e1908; color: #ffe0a0; }',
    '#find-results { max-height: 280px; overflow-y: auto; }',
    '.find-result-item {',
    '  padding: 8px 14px; cursor: pointer;',
    '  border-bottom: 1px solid rgba(255,191,0,0.05);',
    '  transition: background 0.1s; font-size: 13px; display: flex; align-items: center; gap: 10px;',
    '}',
    '.find-result-item:hover { background: rgba(255,191,0,0.1); }',
    '.find-result-item.active { background: rgba(255,191,0,0.18); }',
    '.find-result-item .ri-icon { font-size: 14px; opacity: 0.4; flex-shrink: 0; }',
    '.find-result-item .ri-body { flex: 1; min-width: 0; }',
    '.find-result-item .ri-name { color: #ffe0a0; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }',
    '.find-result-item .ri-meta { color: rgba(255,224,160,0.4); font-size: 11px; }',
    '#find-actions { padding: 8px 14px; display: flex; gap: 8px; border-top: 1px solid rgba(255,191,0,0.1); }',
    '#find-actions button { flex: 1; padding: 8px 0; border-radius: 8px; border: none; font-size: 13px; font-weight: 600; cursor: pointer; transition: all 0.15s; }',
    '.find-nav-btn { background: rgba(255,191,0,0.25); color: #ffd54f; }',
    '.find-nav-btn:hover { background: rgba(255,191,0,0.4); }',
    '.find-close-btn { background: none; border: none !important; color: rgba(255,224,160,0.3); font-size: 16px; position: absolute; top: 10px; right: 12px; cursor: pointer; padding: 4px 8px; }',
    '.find-close-btn:hover { color: #ffe0a0; }',
    '#find-count { font-size: 11px; color: rgba(255,224,160,0.35); padding: 4px 14px 2px; }',
    // Nav HUD
    '#nav-hud {',
    '  position: fixed; top: 0; left: 0; width: 100%; height: 100%;',
    '  pointer-events: none; z-index: 40;',
    '}',
    '#nav-direction-cue {',
    '  position: fixed; top: 30%; left: 50%; transform: translate(-50%, -50%);',
    '  background: rgba(255, 170, 0, 0.5); border-radius: 16px;',
    '  font-size: 64px; padding: 20px 30px; color: #fff; text-align: center;',
    '  line-height: 1.2; opacity: 0; transition: opacity 0.3s;',
    '  pointer-events: none; z-index: 41;',
    '}',
    '#nav-direction-cue.visible { opacity: 1; }',
    '#nav-direction-cue .cue-label { font-size: 16px; font-weight: 600; margin-top: 4px; }',
    '#nav-bottom-bar {',
    '  position: fixed; bottom: 110px; left: 50%; transform: translateX(-50%);',
    '  background: rgba(255, 170, 0, 0.4); backdrop-filter: blur(8px);',
    '  border-radius: 12px; padding: 10px 20px; color: #fff; font-size: 13px;',
    '  pointer-events: auto; z-index: 41; white-space: nowrap;',
    '  text-align: center;',
    '}',
  ].join('\n');
  document.head.appendChild(style);

  // ── State ──
  var nav = {
    results: [],       // [{guid, ifc_class, element_name, storey, discipline, cx, cy, cz}]
    activeIdx: -1,     // selected result index
    waypoints: [],     // [{x,y,z, storey}] in IFC coords
    stepIdx: 0,        // current waypoint index
    active: false,     // navigation in progress
    voiceMode: false,  // speak direction cues
    grid: null,        // occupancy grid for current storey
    gridCache: {},     // storey → grid
    pointerLocked: false,
  };

  // ── Expose state for tests ──
  A.navActive = false;
  A.navCurrentStep = 0;

  // ══════════════════════════════════════════════════════════════
  // SECTION A: FIND PANEL
  // ══════════════════════════════════════════════════════════════

  var panel = document.createElement('div');
  panel.id = 'find-panel';
  var _t = function(k, fb) { return (typeof _TRL !== 'undefined' && _TRL[k]) || fb; };
  panel.innerHTML = [
    '<button class="find-close-btn" id="find-close">&times;</button>',
    '<div class="find-search-bar">',
    '  <span class="find-icon">\uD83D\uDD0D</span>',
    '  <input type="text" id="find-name" data-trl-placeholder="ui_find_placeholder" placeholder="' + _t('ui_find_placeholder', 'Search elements...') + '">',
    '</div>',
    '<div class="find-filters">',
    '  <select id="find-type"><option value="">' + _t('ui_find_all_types', 'All types') + '</option></select>',
    '  <select id="find-storey"><option value="">' + _t('ui_all_storeys', 'All Storeys') + '</option></select>',
    '</div>',
    '<div id="find-count"></div>',
    '<div id="find-results"></div>',
    '<div id="find-actions">',
    '  <button class="find-nav-btn" id="find-navigate-btn" data-action="navigate">' + _t('ui_find_navigate', '\u25B6 Navigate') + '</button>',
    '</div>',
  ].join('');
  document.body.appendChild(panel);

  // Nav HUD elements
  var navHud = document.createElement('div');
  navHud.id = 'nav-hud';
  navHud.style.display = 'none';
  navHud.innerHTML = '<div id="nav-direction-cue"><span class="cue-icon"></span><div class="cue-label"></div></div>' +
    '<div id="nav-bottom-bar"></div>';
  document.body.appendChild(navHud);

  var elType = document.getElementById('find-type');
  var elStorey = document.getElementById('find-storey');
  var elName = document.getElementById('find-name');
  var elResults = document.getElementById('find-results');
  var elCount = document.getElementById('find-count');
  var elNavBtn = document.getElementById('find-navigate-btn');
  var elClose = document.getElementById('find-close');
  var elCue = document.getElementById('nav-direction-cue');
  var elBar = document.getElementById('nav-bottom-bar');

  // ── Open find panel (called from nlp.js) ──
  A.openFindPanel = function(searchTerm) {
    nav.voiceMode = !!A.inputWasVoice;
    // Exit walk mode from previous navigation — ensures next Navigate starts from main entrance
    if (A.walkModeActive) {
      if (nav.active) stopNavigation();
      A.walkModeActive = false;
      if (A.controls) A.controls.enabled = true;
      if (A.camera) A.camera.rotation.reorder('XYZ');
      var walkBtn = document.getElementById('walk-mode-btn');
      if (walkBtn) walkBtn.classList.remove('active');
      console.log('[S233] §FIND_OPEN_RESET_WALK exited walk mode for fresh search');
    }
    // Full reset — clear previous search state
    nav.results = [];
    nav.activeIdx = -1;
    nav.gridCache = {}; // clear stale grid caches
    routeTemplateCache = {}; // clear route templates too
    elType.value = '';
    elStorey.value = '';
    elResults.innerHTML = '';
    elCount.textContent = '';
    clearHighlight();
    // Set search term and open
    panel.style.display = 'block';
    elName.value = searchTerm || '';
    populateDropdowns();
    runSearch();
    console.log('[S233] §FIND_OPEN term="' + (searchTerm || '') + '" voice=' + nav.voiceMode);
  };

  function closeFindPanel() {
    panel.style.display = 'none';
    if (nav.active) stopNavigation();
    clearHighlight();
    console.log('[S233] §FIND_CLOSE');
  }
  A.closeFindPanel = closeFindPanel; // exposed for nlp.js bar close
  elClose.onclick = closeFindPanel;

  // ── Populate dropdowns — show all types/storeys, with match counts when searching ──
  function populateDropdowns() {
    if (!A.db) return;
    var bld = A.activeBuilding || '';
    var name = elName.value.trim();
    var savedType = elType.value;
    var savedStorey = elStorey.value;
    try {
      // Get match counts per type (only if there's a search term)
      var matchByType = {};
      if (name) {
        var mtSql = 'SELECT ifc_class, COUNT(*) as cnt FROM elements_meta WHERE' +
          ' (LOWER(element_name) LIKE LOWER(?) OR LOWER(ifc_class) LIKE LOWER(?))' +
          (bld ? ' AND building = ?' : '') + ' GROUP BY ifc_class';
        var mtParams = ['%' + name + '%', '%' + name + '%'];
        if (bld) mtParams.push(bld);
        var mtRows = A.db.exec(mtSql, mtParams);
        if (mtRows.length > 0) mtRows[0].values.forEach(function(r) { matchByType[r[0]] = r[1]; });
      }

      // All types in building, sorted by match count (matches first)
      var typeSql = 'SELECT ifc_class, COUNT(*) as cnt FROM elements_meta' +
        (bld ? ' WHERE building = ?' : '') + ' GROUP BY ifc_class ORDER BY cnt DESC';
      var types = A.db.exec(typeSql, bld ? [bld] : []);
      elType.innerHTML = '<option value="">All types</option>';
      if (types.length > 0) {
        // Sort: types with matches first, then the rest
        var sorted = types[0].values.slice().sort(function(a, b) {
          var ma = matchByType[a[0]] || 0, mb = matchByType[b[0]] || 0;
          if (mb !== ma) return mb - ma; // matches first
          return b[1] - a[1]; // then by total count
        });
        sorted.forEach(function(r) {
          var opt = document.createElement('option');
          opt.value = r[0];
          var mc = matchByType[r[0]];
          opt.textContent = friendlyClass(r[0]) + (mc ? ' \u2714 ' + mc + ' matches' : '') + ' (' + r[1] + ')';
          if (mc) opt.style.fontWeight = 'bold';
          elType.appendChild(opt);
        });
      }
      if (savedType) elType.value = savedType;

      // Get match counts per storey
      var matchByStorey = {};
      if (name) {
        var msSql = 'SELECT storey, COUNT(*) as cnt FROM elements_meta WHERE storey IS NOT NULL' +
          ' AND (LOWER(element_name) LIKE LOWER(?) OR LOWER(ifc_class) LIKE LOWER(?))' +
          (bld ? ' AND building = ?' : '') + ' GROUP BY storey';
        var msParams = ['%' + name + '%', '%' + name + '%'];
        if (bld) msParams.push(bld);
        var msRows = A.db.exec(msSql, msParams);
        if (msRows.length > 0) msRows[0].values.forEach(function(r) { matchByStorey[r[0]] = r[1]; });
      }

      // All storeys, sorted by elevation
      var storeySql = 'SELECT m.storey, COUNT(*) as cnt FROM elements_meta m' +
        ' JOIN element_transforms t ON m.guid = t.guid' +
        ' WHERE m.storey IS NOT NULL' + (bld ? ' AND m.building = ?' : '') +
        ' GROUP BY m.storey ORDER BY MIN(t.center_z)';
      var storeys = A.db.exec(storeySql, bld ? [bld] : []);
      elStorey.innerHTML = '<option value="">All storeys</option>';
      if (storeys.length > 0) {
        storeys[0].values.forEach(function(r) {
          if (!r[0]) return;
          var opt = document.createElement('option');
          opt.value = r[0];
          var mc = matchByStorey[r[0]];
          opt.textContent = r[0] + (mc ? ' \u2714 ' + mc + ' matches' : '') + ' (' + r[1] + ')';
          if (mc) opt.style.fontWeight = 'bold';
          elStorey.appendChild(opt);
        });
      }
      if (savedStorey) elStorey.value = savedStorey;
    } catch(e) { console.warn('[S233] dropdown error', e); }
  }

  // ── Run search query ──
  function runSearch() {
    nav.results = [];
    nav.activeIdx = -1;
    elResults.innerHTML = '';
    elCount.textContent = '';
    if (!A.db) return;

    var bld = A.activeBuilding || '';
    var type = elType.value;
    var storey = elStorey.value;
    var name = elName.value.trim();

    var sql = 'SELECT m.guid, m.ifc_class, m.element_name, m.storey, m.discipline,' +
      ' t.center_x, t.center_y, t.center_z' +
      ' FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid WHERE 1=1';
    var params = [];
    if (bld) { sql += ' AND m.building = ?'; params.push(bld); }
    if (type) { sql += ' AND m.ifc_class = ?'; params.push(type); }
    if (storey) { sql += ' AND m.storey = ?'; params.push(storey); }
    if (name) { sql += ' AND (LOWER(m.element_name) LIKE LOWER(?) OR LOWER(m.ifc_class) LIKE LOWER(?))'; params.push('%' + name + '%', '%' + name + '%'); }
    sql += ' ORDER BY m.storey, m.ifc_class, m.element_name LIMIT 50';

    try {
      var rows = A.db.exec(sql, params);
      if (rows.length > 0) {
        nav.results = rows[0].values.map(function(r) {
          return { guid: r[0], ifc_class: r[1], element_name: r[2], storey: r[3], discipline: r[4], cx: r[5], cy: r[6], cz: r[7] };
        });
      }
    } catch(e) { console.warn('[S233] search error', e); }

    if (nav.results.length > 0) {
      elCount.textContent = (typeof _TRL!=='undefined'&&_TRL.ui_find_matches||'{n} found').replace('{n}', nav.results.length);
      renderResults();
      // Auto-select first result so Navigate works immediately after filter change
      if (nav.activeIdx < 0) selectResult(0);
    } else {
      // No results — find nearest suggestions
      var suggestions = findSuggestions(bld, name);
      elCount.textContent = typeof _TRL!=='undefined'&&_TRL.ui_find_no_matches||'0 matches';
      renderSuggestions(suggestions, name);
    }
    console.log('[S233] §FIND_SEARCH results=' + nav.results.length);
  }

  // ── Nearest-match suggestions when search returns 0 ──
  function findSuggestions(bld, name) {
    if (!A.db || !name) return [];
    var suggestions = [];

    // Strategy 1: match each word separately (user typed "fire pum" → match "fire" OR "pum")
    var words = name.toLowerCase().split(/\s+/).filter(function(w) { return w.length >= 2; });
    if (words.length > 0) {
      var wordClauses = words.map(function() { return '(LOWER(m.element_name) LIKE ? OR LOWER(m.ifc_class) LIKE ?)'; });
      var wordParams = [];
      words.forEach(function(w) { wordParams.push('%' + w + '%', '%' + w + '%'); });
      var sql = 'SELECT DISTINCT m.element_name, m.ifc_class, m.storey, COUNT(*) as cnt' +
        ' FROM elements_meta m WHERE (' + wordClauses.join(' OR ') + ')' +
        (bld ? ' AND m.building = ?' : '') +
        ' GROUP BY m.element_name, m.ifc_class, m.storey ORDER BY cnt DESC LIMIT 8';
      if (bld) wordParams.push(bld);
      try {
        var rows = A.db.exec(sql, wordParams);
        if (rows.length > 0) {
          rows[0].values.forEach(function(r) {
            suggestions.push({ name: r[0], ifc_class: r[1], storey: r[2], count: r[3], reason: 'partial match' });
          });
        }
      } catch(e) { /* ignore */ }
    }

    // Strategy 2: if still nothing, check if filters (type/storey) are too restrictive
    if (suggestions.length === 0 && (elType.value || elStorey.value)) {
      var relaxSql = 'SELECT DISTINCT m.element_name, m.ifc_class, m.storey, COUNT(*) as cnt' +
        ' FROM elements_meta m WHERE (LOWER(m.element_name) LIKE LOWER(?) OR LOWER(m.ifc_class) LIKE LOWER(?))' +
        (bld ? ' AND m.building = ?' : '') +
        ' GROUP BY m.element_name, m.ifc_class, m.storey ORDER BY cnt DESC LIMIT 5';
      var relaxParams = ['%' + name + '%', '%' + name + '%'];
      if (bld) relaxParams.push(bld);
      try {
        var rRows = A.db.exec(relaxSql, relaxParams);
        if (rRows.length > 0) {
          rRows[0].values.forEach(function(r) {
            suggestions.push({ name: r[0], ifc_class: r[1], storey: r[2], count: r[3], reason: 'try removing filters' });
          });
        }
      } catch(e) { /* ignore */ }
    }

    // Strategy 3: show what IS available (top element names containing any 3+ char substring)
    if (suggestions.length === 0 && name.length >= 3) {
      var sub = name.substring(0, 3).toLowerCase();
      var subSql = 'SELECT DISTINCT m.element_name, m.ifc_class, m.storey, COUNT(*) as cnt' +
        ' FROM elements_meta m WHERE LOWER(m.element_name) LIKE ?' +
        (bld ? ' AND m.building = ?' : '') +
        ' GROUP BY m.element_name, m.ifc_class, m.storey ORDER BY cnt DESC LIMIT 5';
      var subParams = ['%' + sub + '%'];
      if (bld) subParams.push(bld);
      try {
        var sRows = A.db.exec(subSql, subParams);
        if (sRows.length > 0) {
          sRows[0].values.forEach(function(r) {
            suggestions.push({ name: r[0], ifc_class: r[1], storey: r[2], count: r[3], reason: 'similar' });
          });
        }
      } catch(e) { /* ignore */ }
    }

    console.log('[S233] §FIND_SUGGEST count=' + suggestions.length + ' for="' + name + '"');
    return suggestions;
  }

  // ── Render suggestions as clickable items ──
  function renderSuggestions(suggestions, originalTerm) {
    elResults.innerHTML = '';
    if (suggestions.length === 0) {
      elResults.innerHTML = '<div style="color:rgba(255,224,160,0.4);font-size:12px;padding:8px;">' +
        'No elements matching "' + escHtml(originalTerm) + '"</div>';
      return;
    }
    var hdr = document.createElement('div');
    hdr.style.cssText = 'color:rgba(255,224,160,0.5);font-size:11px;padding:4px 0 6px 0;';
    hdr.textContent = typeof _TRL!=='undefined'&&_TRL.ui_find_did_you_mean||'Did you mean:';
    elResults.appendChild(hdr);

    suggestions.forEach(function(s) {
      var div = document.createElement('div');
      div.className = 'find-result-item';
      var sDispName = friendlyName(s.name, s.ifc_class);
      var sDispClass = friendlyClass(s.ifc_class);
      div.innerHTML = '<div class="ri-name">' + escHtml(sDispName) + '</div>' +
        '<div class="ri-meta">' + escHtml(sDispClass) + ' &middot; ' + escHtml(s.storey || '?') +
        ' &middot; ' + s.count + ' found' +
        (s.reason === 'try removing filters' ? ' &middot; <em>try removing filters</em>' : '') + '</div>';
      // Click suggestion → put it in search box and re-search
      div.onclick = function() {
        elName.value = s.name || s.ifc_class;
        // Clear restrictive filters if suggestion came from relaxed search
        if (s.reason === 'try removing filters') {
          elType.value = '';
          elStorey.value = '';
        }
        populateDropdowns();
        runSearch();
      };
      elResults.appendChild(div);
    });
  }

  // ── Render result list ──
  function renderResults() {
    elResults.innerHTML = '';
    nav.results.forEach(function(r, i) {
      var div = document.createElement('div');
      div.className = 'find-result-item';
      var dispName = friendlyName(r.element_name, r.ifc_class);
      var dispClass = friendlyClass(r.ifc_class);
      var icon = classIcon(r.ifc_class);
      div.innerHTML = '<span class="ri-icon">' + icon + '</span>' +
        '<div class="ri-body"><div class="ri-name">' + escHtml(dispName) + '</div>' +
        '<div class="ri-meta">' + escHtml(dispClass) + ' · ' + escHtml(r.storey || '?') + '</div></div>';
      // Both onclick (desktop) and touchend (mobile) — touchend avoids scroll/tap conflict
      function handleTap(e) {
        e.stopPropagation();
        selectResult(i);
      }
      div.addEventListener('click', handleTap);
      // Mobile: track touch start to discriminate tap vs scroll
      var touchStartY = 0;
      div.addEventListener('touchstart', function(e) {
        if (e.touches.length === 1) touchStartY = e.touches[0].clientY;
      }, { passive: true });
      div.addEventListener('touchend', function(e) {
        if (e.changedTouches && e.changedTouches.length === 1) {
          var dy = Math.abs(e.changedTouches[0].clientY - touchStartY);
          if (dy < 10) { e.preventDefault(); handleTap(e); }
        }
      });
      elResults.appendChild(div);
    });
    // Show navigate hint if results exist
    if (nav.results.length > 0) {
      elNavBtn.style.display = '';
      elNavBtn.textContent = typeof _TRL!=='undefined'&&_TRL.ui_find_navigate_sel||'\u25B6 Navigate to selected';
    }
  }

  function escHtml(s) { return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }

  // ── Humanise IFC names for display ──
  // "M_Single-Flush:0762 x 2032mm:0762 x 2032mm:150173" → "Single-Flush 762×2032mm"
  // "IfcFlowTerminal" → "Flow Terminal"
  function friendlyName(elementName, ifcClass) {
    var name = elementName || '';
    // Strip Revit prefix (M_, C_, etc.) and trailing Revit ID (":123456")
    name = name.replace(/^[A-Z]_/, '');
    // Split on colon — take first meaningful part
    var parts = name.split(':').filter(function(p) { return p.trim(); });
    if (parts.length >= 2) {
      // First part = type, second = dimensions usually
      var typePart = parts[0].trim();
      var dimPart = parts[1].trim();
      // If last part is just a number (Revit ID), drop it
      var lastPart = parts[parts.length - 1].trim();
      if (/^\d{4,}$/.test(lastPart)) parts.pop();
      // Deduplicate: "0762 x 2032mm:0762 x 2032mm" → just one
      var seen = {};
      var unique = [];
      parts.forEach(function(p) {
        var key = p.trim().toLowerCase();
        if (!seen[key]) { seen[key] = true; unique.push(p.trim()); }
      });
      name = unique.join(' \u2014 '); // em dash
    }
    // If still empty, humanise IFC class
    if (!name || name.length < 2) name = friendlyClass(ifcClass);
    return name;
  }

  function friendlyClass(ifcClass) {
    if (!ifcClass) return '?';
    // "IfcFlowTerminal" → "Flow Terminal", "IfcWallStandardCase" → "Wall"
    var c = ifcClass.replace(/^Ifc/, '').replace(/StandardCase$/, '').replace(/Standard$/, '');
    // Insert space before capitals: "FlowTerminal" → "Flow Terminal"
    c = c.replace(/([a-z])([A-Z])/g, '$1 $2');
    return c;
  }

  function classIcon(ifcClass) {
    var c = (ifcClass || '').toLowerCase();
    if (c.includes('door')) return '\uD83D\uDEAA';
    if (c.includes('wall')) return '\u25A8';
    if (c.includes('window')) return '\u25A1';
    if (c.includes('stair')) return '\u2B06';
    if (c.includes('slab') || c.includes('floor')) return '\u25AC';
    if (c.includes('column')) return '\u2502';
    if (c.includes('beam')) return '\u2500';
    if (c.includes('roof')) return '\u25B3';
    if (c.includes('pipe') || c.includes('flow')) return '\u25CB';
    if (c.includes('space') || c.includes('room')) return '\u25A2';
    return '\u25C6';
  }

  // ── Select result → highlight only (no camera jump) ──
  // Camera stays put. Navigate button handles the walk-to experience.
  function selectResult(idx) {
    nav.activeIdx = idx;
    // Update active class
    var items = elResults.querySelectorAll('.find-result-item');
    items.forEach(function(el, i) { el.classList.toggle('active', i === idx); });

    var r = nav.results[idx];
    if (!r) return;

    // Yellow highlight on element — no camera movement
    var pos = A.ifc2three(r.cx, r.cy, r.cz);
    var target = new THREE.Vector3(pos.x, pos.y, pos.z);
    highlightElement(r.guid, target);

    // Update navigate button
    elNavBtn.textContent = typeof _TRL!=='undefined'&&_TRL.ui_find_navigate||'\u25B6 Navigate';
    elNavBtn.style.display = '';

    // Status feedback
    var dispName = friendlyName(r.element_name, r.ifc_class);
    if (A.status) A.status.textContent = dispName + ' · ' + (r.storey || '?');

    console.log('[S233] §FIND_SELECT idx=' + idx + ' guid=' + r.guid.substring(0, 12));
  }

  // ── Highlight element (yellow wireframe box + pulse) ──
  var _highlight = null;
  var _highlightPulse = null;
  function highlightElement(guid, worldPos) {
    clearHighlight();
    var hlMat = new THREE.LineBasicMaterial({ color: 0xffff00, linewidth: 2 });
    // Find mesh by guid (works on desktop, fails on mobile merged meshes)
    var mesh = null;
    if (typeof A.findMeshByGuid === 'function') mesh = A.findMeshByGuid(guid);
    if (mesh && mesh.geometry) {
      mesh.geometry.computeBoundingBox();
      var bb = mesh.geometry.boundingBox;
      var size = new THREE.Vector3(); bb.getSize(size);
      var center = new THREE.Vector3(); bb.getCenter(center);
      var hlGeo = new THREE.BoxGeometry(size.x || 1, size.y || 1, size.z || 1);
      var hlEdges = new THREE.EdgesGeometry(hlGeo);
      var hlLine = new THREE.LineSegments(hlEdges, hlMat);
      hlLine.position.copy(center);
      mesh.add(hlLine);
      _highlight = hlLine;
    } else if (worldPos) {
      // Fallback: visible 2m marker box at element position (for mobile merged meshes)
      var hlGeo2 = new THREE.BoxGeometry(2, 2, 2);
      var hlEdges2 = new THREE.EdgesGeometry(hlGeo2);
      var hlLine2 = new THREE.LineSegments(hlEdges2, hlMat);
      hlLine2.position.copy(worldPos);
      A.scene.add(hlLine2);
      _highlight = hlLine2;
    }
    // Pulse animation — blink the highlight for visibility
    if (_highlight) {
      clearInterval(_highlightPulse);
      var vis = true;
      _highlightPulse = setInterval(function() {
        if (!_highlight) { clearInterval(_highlightPulse); return; }
        vis = !vis;
        _highlight.visible = vis;
      }, 400);
      // Stop pulsing after 6s — stay visible
      setTimeout(function() {
        clearInterval(_highlightPulse);
        if (_highlight) _highlight.visible = true;
      }, 6000);
    }
  }
  function clearHighlight() {
    clearInterval(_highlightPulse);
    if (_highlight && _highlight.parent) _highlight.parent.remove(_highlight);
    _highlight = null;
  }

  // ── Find main entrance — furthest exterior door on ground floor from building centre ──
  function findMainEntrance() {
    if (!A.db) return null;
    try {
      // Get the storey with the MOST doors at or above ground level (z >= 0).
      // "TOF Footing" at z=-1 is underground — not a real entrance.
      var stRows = A.db.exec(
        "SELECT m.storey, COUNT(*) as cnt, MIN(t.center_z) as min_z FROM elements_meta m" +
        " JOIN element_transforms t ON m.guid = t.guid" +
        " WHERE m.ifc_class IN ('IfcDoor', 'IfcDoorStandardCase')" +
        " GROUP BY m.storey HAVING min_z >= -0.5 ORDER BY min_z ASC, cnt DESC LIMIT 1");
      var lowestStorey = (stRows.length > 0 && stRows[0].values.length > 0) ? stRows[0].values[0][0] : null;

      // Get all doors on ground floor
      var sql = "SELECT t.center_x, t.center_y, t.center_z FROM elements_meta m" +
        " JOIN element_transforms t ON m.guid = t.guid" +
        " WHERE m.ifc_class IN ('IfcDoor', 'IfcDoorStandardCase')";
      var params = [];
      if (lowestStorey) { sql += ' AND m.storey = ?'; params.push(lowestStorey); }
      var rows = A.db.exec(sql, params);
      if (!rows.length || !rows[0].values.length) return null;

      // Find building centre
      var bldCentre = Object.values(A.buildingCentres || {})[0];
      if (!bldCentre) return rows[0].values[0] ? { x: rows[0].values[0][0], y: rows[0].values[0][1], z: rows[0].values[0][2] } : null;

      // Pick door FURTHEST from building centre = most likely exterior/main entrance
      var best = null, bestDist = -1;
      for (var i = 0; i < rows[0].values.length; i++) {
        var dx = rows[0].values[i][0] - bldCentre.ix;
        var dy = rows[0].values[i][1] - bldCentre.iy;
        var dist = dx * dx + dy * dy;
        if (dist > bestDist) { bestDist = dist; best = { x: rows[0].values[i][0], y: rows[0].values[i][1], z: rows[0].values[i][2] }; }
      }
      console.log('[S233] §NAV_ENTRANCE door=(' + best.x.toFixed(1) + ',' + best.y.toFixed(1) + ',' + best.z.toFixed(1) +
        ') dist=' + Math.sqrt(bestDist).toFixed(1) + 'm from centre' +
        ' bldCentre=(' + (bldCentre?bldCentre.ix.toFixed(1):'?') + ',' + (bldCentre?bldCentre.iy.toFixed(1):'?') + ')' +
        ' storey="' + (lowestStorey||'?') + '" doors=' + rows[0].values.length);
      return best;
    } catch(e) {
      console.warn('[S233] §NAV_ENTRANCE_ERR', e.message);
      return null;
    }
  }

  // ── Filter change listeners — all filters cross-update dropdowns + results ──
  elType.onchange = function() { populateDropdowns(); runSearch(); };
  elStorey.onchange = function() { populateDropdowns(); runSearch(); };
  elName.addEventListener('input', debounce(function() {
    populateDropdowns();
    runSearch();
  }, 300));

  function debounce(fn, ms) {
    var t; return function() { clearTimeout(t); t = setTimeout(fn, ms); };
  }


  // ══════════════════════════════════════════════════════════════
  // SECTION B: OCCUPANCY GRID + A* PATHFINDING
  // ══════════════════════════════════════════════════════════════

  var CELL_SIZE = 2; // metres

  // Build occupancy grid for a storey from DB wall/column positions
  function buildGrid(storey) {
    if (nav.gridCache[storey]) return nav.gridCache[storey];
    if (!A.db) return null;

    var bld = A.activeBuilding || '';
    // Get all elements on this storey for bounding box
    var bbSql = 'SELECT MIN(t.center_x), MAX(t.center_x), MIN(t.center_y), MAX(t.center_y)' +
      ' FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid' +
      ' WHERE m.storey = ?' + (bld ? ' AND m.building = ?' : '');
    var bbParams = [storey]; if (bld) bbParams.push(bld);
    var bbRows;
    try { bbRows = A.db.exec(bbSql, bbParams); } catch(e) { return null; }
    if (!bbRows.length || !bbRows[0].values.length) return null;

    var r = bbRows[0].values[0];
    var minX = r[0], maxX = r[1], minY = r[2], maxY = r[3];
    // Pad bounding box
    minX -= CELL_SIZE; minY -= CELL_SIZE; maxX += CELL_SIZE; maxY += CELL_SIZE;

    var cols = Math.ceil((maxX - minX) / CELL_SIZE);
    var rows = Math.ceil((maxY - minY) / CELL_SIZE);
    if (cols < 1 || rows < 1 || cols > 500 || rows > 500) return null; // sanity

    // Grid: 0 = walkable, 1 = occupied
    var grid = new Uint8Array(cols * rows);

    // Query wall/column positions
    var wallSql = 'SELECT t.center_x, t.center_y' +
      ' FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid' +
      ' WHERE m.storey = ? AND m.ifc_class IN ' +
      "('IfcWall','IfcWallStandardCase','IfcColumn','IfcCurtainWall','IfcRailing')" +
      (bld ? ' AND m.building = ?' : '');
    var wallParams = [storey]; if (bld) wallParams.push(bld);
    try {
      var wallRows = A.db.exec(wallSql, wallParams);
      if (wallRows.length > 0) {
        wallRows[0].values.forEach(function(w) {
          var cx = Math.floor((w[0] - minX) / CELL_SIZE);
          var cy = Math.floor((w[1] - minY) / CELL_SIZE);
          // Mark cell and neighbours (wall thickness > 1 cell sometimes)
          for (var dx = -1; dx <= 1; dx++) {
            for (var dy = -1; dy <= 1; dy++) {
              var nx = cx + dx, ny = cy + dy;
              if (nx >= 0 && nx < cols && ny >= 0 && ny < rows) {
                grid[ny * cols + nx] = 1;
              }
            }
          }
        });
      }
    } catch(e) { /* no walls */ }

    // Mark door cells as preferred (lower cost) — store separately
    var doorCells = {};
    try {
      var doorSql = 'SELECT t.center_x, t.center_y FROM elements_meta m' +
        ' JOIN element_transforms t ON m.guid = t.guid' +
        ' WHERE m.storey = ? AND m.ifc_class IN (\'IfcDoor\',\'IfcDoorStandardCase\')' +
        (bld ? ' AND m.building = ?' : '');
      var doorParams = [storey]; if (bld) doorParams.push(bld);
      var doorRows = A.db.exec(doorSql, doorParams);
      if (doorRows.length > 0) {
        doorRows[0].values.forEach(function(d) {
          var cx2 = Math.floor((d[0] - minX) / CELL_SIZE);
          var cy2 = Math.floor((d[1] - minY) / CELL_SIZE);
          if (cx2 >= 0 && cx2 < cols && cy2 >= 0 && cy2 < rows) {
            doorCells[cy2 * cols + cx2] = true;
            grid[cy2 * cols + cx2] = 0; // doors are always walkable (cut through wall)
          }
        });
      }
    } catch(e) { /* no doors */ }

    var occupied = 0;
    for (var gi = 0; gi < grid.length; gi++) { if (grid[gi] === 1) occupied++; }
    var result = { grid: grid, cols: cols, rows: rows, minX: minX, minY: minY, doorCells: doorCells };
    nav.gridCache[storey] = result;
    console.log('[S233] §GRID_BUILD storey="' + storey + '" ' + cols + 'x' + rows + '=' + (cols*rows) +
      ' cells occupied=' + occupied + ' walkable=' + (cols*rows - occupied) +
      ' doors=' + Object.keys(doorCells).length +
      ' bbox=(' + minX.toFixed(1) + ',' + minY.toFixed(1) + ')→(' + maxX.toFixed(1) + ',' + maxY.toFixed(1) + ')');
    return result;
  }

  // Convert IFC X,Y to grid col,row
  function toCell(g, ix, iy) {
    return { c: Math.floor((ix - g.minX) / CELL_SIZE), r: Math.floor((iy - g.minY) / CELL_SIZE) };
  }
  function fromCell(g, c, r) {
    return { x: g.minX + (c + 0.5) * CELL_SIZE, y: g.minY + (r + 0.5) * CELL_SIZE };
  }

  // A* pathfinding on occupancy grid
  function astar(g, startC, startR, endC, endR) {
    if (startC < 0 || startC >= g.cols || startR < 0 || startR >= g.rows) return null;
    if (endC < 0 || endC >= g.cols || endR < 0 || endR >= g.rows) return null;

    // If start or end is in a wall, find nearest walkable cell
    if (g.grid[startR * g.cols + startC] === 1) {
      var sc = findNearestWalkable(g, startC, startR);
      if (!sc) return null;
      startC = sc.c; startR = sc.r;
    }
    if (g.grid[endR * g.cols + endC] === 1) {
      var ec = findNearestWalkable(g, endC, endR);
      if (!ec) return null;
      endC = ec.c; endR = ec.r;
    }

    var key = function(c, r) { return r * g.cols + c; };
    var open = [{ c: startC, r: startR, g: 0, f: 0 }];
    var closed = {};
    var parent = {};
    var gScore = {};
    gScore[key(startC, startR)] = 0;

    var endKey = key(endC, endR);
    var dirs = [[-1,0],[1,0],[0,-1],[0,1],[-1,-1],[-1,1],[1,-1],[1,1]]; // 8-connected
    var maxIter = g.cols * g.rows * 2; // safety limit
    var iter = 0;

    while (open.length > 0 && iter++ < maxIter) {
      // Find lowest f
      var bestI = 0;
      for (var oi = 1; oi < open.length; oi++) {
        if (open[oi].f < open[bestI].f) bestI = oi;
      }
      var cur = open.splice(bestI, 1)[0];
      var ck = key(cur.c, cur.r);
      if (ck === endKey) {
        // Reconstruct path
        var path = [{ c: endC, r: endR }];
        var pk = endKey;
        while (parent[pk] !== undefined) {
          pk = parent[pk];
          path.unshift({ c: pk % g.cols, r: Math.floor(pk / g.cols) });
        }
        return path;
      }
      closed[ck] = true;

      for (var di = 0; di < dirs.length; di++) {
        var nc = cur.c + dirs[di][0], nr = cur.r + dirs[di][1];
        if (nc < 0 || nc >= g.cols || nr < 0 || nr >= g.rows) continue;
        var nk = key(nc, nr);
        if (closed[nk]) continue;
        if (g.grid[nk] === 1) continue; // wall

        // Diagonal costs more
        var moveCost = (dirs[di][0] !== 0 && dirs[di][1] !== 0) ? 1.414 : 1.0;
        // Door cells cost less (preferred waypoint)
        if (g.doorCells && g.doorCells[nk]) moveCost *= 0.5;
        var ng = cur.g + moveCost;

        if (gScore[nk] === undefined || ng < gScore[nk]) {
          gScore[nk] = ng;
          parent[nk] = ck;
          // Heuristic: Euclidean distance
          var hx = nc - endC, hy = nr - endR;
          var h = Math.sqrt(hx * hx + hy * hy);
          // Check if already in open
          var inOpen = false;
          for (var oj = 0; oj < open.length; oj++) {
            if (key(open[oj].c, open[oj].r) === nk) {
              open[oj].g = ng; open[oj].f = ng + h;
              inOpen = true; break;
            }
          }
          if (!inOpen) open.push({ c: nc, r: nr, g: ng, f: ng + h });
        }
      }
    }
    return null; // no path
  }

  function findNearestWalkable(g, c, r) {
    for (var radius = 1; radius < 20; radius++) {
      for (var dx = -radius; dx <= radius; dx++) {
        for (var dy = -radius; dy <= radius; dy++) {
          if (Math.abs(dx) !== radius && Math.abs(dy) !== radius) continue;
          var nx = c + dx, ny = r + dy;
          if (nx >= 0 && nx < g.cols && ny >= 0 && ny < g.rows && g.grid[ny * g.cols + nx] === 0) {
            return { c: nx, r: ny };
          }
        }
      }
    }
    return null;
  }

  // ══════════════════════════════════════════════════════════════
  // SECTION B3: VERTICAL TRANSPORT — STAIRS & LIFTS
  // ══════════════════════════════════════════════════════════════

  function findVerticalTransport() {
    if (!A.db) return [];
    var bld = A.activeBuilding || '';
    var sql = 'SELECT m.guid, m.ifc_class, t.center_x, t.center_y, t.center_z' +
      ' FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid' +
      " WHERE m.ifc_class IN ('IfcStair','IfcStairFlight','IfcTransportElement')" +
      (bld ? ' AND m.building = ?' : '');
    try {
      var rows = A.db.exec(sql, bld ? [bld] : []);
      if (!rows.length) return [];
      return rows[0].values.map(function(r) {
        return { guid: r[0], ifc_class: r[1], x: r[2], y: r[3], z: r[4] };
      });
    } catch(e) { return []; }
  }

  // Match stair Z to storey levels, return {x, y, fromStorey, toStorey}
  function matchStairsToStoreys(vt) {
    var levels = A.walkStoreyLevels || [];
    if (levels.length < 2) return [];
    var links = [];
    vt.forEach(function(s) {
      // Find which two storeys this stair connects by Z proximity
      var bestFrom = null, bestTo = null, bestDist = Infinity;
      for (var i = 0; i < levels.length - 1; i++) {
        var midZ = (levels[i].floorZ + levels[i + 1].floorZ) / 2;
        var dist = Math.abs(s.z - midZ);
        if (dist < bestDist) {
          bestDist = dist;
          bestFrom = levels[i];
          bestTo = levels[i + 1];
        }
      }
      if (bestFrom && bestTo) {
        links.push({ x: s.x, y: s.y, fromStorey: bestFrom.storey, toStorey: bestTo.storey,
          fromZ: bestFrom.floorZ, toZ: bestTo.floorZ, ifc_class: s.ifc_class });
      }
    });
    console.log('[S233] §VERT_TRANSPORT ' + links.length + ' storey links from ' + vt.length + ' stairs/lifts');
    return links;
  }

  // ══════════════════════════════════════════════════════════════
  // SECTION B4: ROUTE TEMPLATE — Precomputed Corridor Graph
  // Auto-generated from occupancy grid. Graph A* on ~10-50 nodes
  // instead of 5K grid cells. Named waypoints for human-readable
  // directions. Cache per storey, reused for every navigation.
  // ══════════════════════════════════════════════════════════════

  var routeTemplateCache = {};

  // Build route template from occupancy grid — extract corridor skeleton
  function buildRouteTemplate(storey) {
    if (routeTemplateCache[storey]) return routeTemplateCache[storey];

    var g = buildGrid(storey);
    if (!g) return null;

    var nodes = [];
    var nodeMap = {}; // "c,r" → node index

    // Step 1: Collect seed nodes — doors, stairs, and grid-edge entrances
    // Doors are natural waypoints
    if (g.doorCells) {
      var doorKeys = Object.keys(g.doorCells);
      for (var di = 0; di < doorKeys.length; di++) {
        var dk = parseInt(doorKeys[di]);
        var dc = dk % g.cols, dr = Math.floor(dk / g.cols);
        var key = dc + ',' + dr;
        if (!nodeMap[key]) {
          var ifc = fromCell(g, dc, dr);
          nodeMap[key] = nodes.length;
          nodes.push({ id: 'door_' + di, c: dc, r: dr, x: ifc.x, y: ifc.y, label: 'Door', type: 'door' });
        }
      }
    }

    // Step 2: Find corridor junctions — walkable cells with 3+ walkable cardinal neighbours
    // and corridor endpoints — walkable cells with exactly 1 walkable cardinal neighbour
    var cardinals = [[0,-1],[0,1],[-1,0],[1,0]];
    for (var jr = 1; jr < g.rows - 1; jr++) {
      for (var jc = 1; jc < g.cols - 1; jc++) {
        if (g.grid[jr * g.cols + jc] !== 0) continue; // wall
        var walkCount = 0;
        for (var cd = 0; cd < 4; cd++) {
          var nc = jc + cardinals[cd][0], nr = jr + cardinals[cd][1];
          if (nc >= 0 && nc < g.cols && nr >= 0 && nr < g.rows && g.grid[nr * g.cols + nc] === 0) {
            walkCount++;
          }
        }
        // Junction (3+ cardinal neighbours) or dead end (1 neighbour)
        if (walkCount >= 3 || walkCount === 1) {
          var jkey = jc + ',' + jr;
          if (!nodeMap[jkey]) {
            var jifc = fromCell(g, jc, jr);
            var jtype = walkCount >= 3 ? 'junction' : 'endpoint';
            nodeMap[jkey] = nodes.length;
            nodes.push({ id: jtype + '_' + nodes.length, c: jc, r: jr, x: jifc.x, y: jifc.y,
              label: jtype === 'junction' ? 'Junction' : 'End', type: jtype });
          }
        }
      }
    }

    if (nodes.length < 2) {
      console.log('[S233] §ROUTE_TEMPLATE_SKIP storey="' + storey + '" nodes=' + nodes.length + ' (too few)');
      return null;
    }

    // Step 3: Build edges — connect nodes that have walkable grid path between them
    // Use BFS on grid from each node to find directly reachable neighbour nodes.
    // BFS stops at other nodes (they form their own connections) but we BFS from
    // every node to ensure bidirectional discovery (node A may not reach B via BFS
    // if another node C blocks the path, but B may reach A from the other side).
    var edgeSet = {}; // "from,to" → cost (deduplicate)
    var MAX_BFS = 80; // max cells to search for a direct connection

    for (var ni = 0; ni < nodes.length; ni++) {
      var visited = {};
      var queue = [{ c: nodes[ni].c, r: nodes[ni].r, dist: 0 }];
      visited[nodes[ni].c + ',' + nodes[ni].r] = true;

      while (queue.length > 0) {
        var cur = queue.shift();
        if (cur.dist > MAX_BFS) continue;

        // Check if this cell is another node
        var ck = cur.c + ',' + cur.r;
        if (ck !== nodes[ni].c + ',' + nodes[ni].r && nodeMap[ck] !== undefined) {
          var nj = nodeMap[ck];
          // Deduplicate: use sorted pair as key
          var lo = Math.min(ni, nj), hi = Math.max(ni, nj);
          var ekey = lo + ',' + hi;
          var edgeDist = cur.dist * CELL_SIZE;
          if (!edgeSet[ekey] || edgeDist < edgeSet[ekey]) {
            edgeSet[ekey] = edgeDist; // keep shortest path
          }
          continue; // don't BFS through other nodes
        }

        // Expand to walkable neighbours (8-connected)
        var dirs8 = [[-1,0],[1,0],[0,-1],[0,1],[-1,-1],[-1,1],[1,-1],[1,1]];
        for (var dd = 0; dd < dirs8.length; dd++) {
          var nnc = cur.c + dirs8[dd][0], nnr = cur.r + dirs8[dd][1];
          if (nnc < 0 || nnc >= g.cols || nnr < 0 || nnr >= g.rows) continue;
          var nk2 = nnc + ',' + nnr;
          if (visited[nk2]) continue;
          if (g.grid[nnr * g.cols + nnc] !== 0) continue; // wall
          visited[nk2] = true;
          var stepDist = (dirs8[dd][0] !== 0 && dirs8[dd][1] !== 0) ? 1.414 : 1.0;
          queue.push({ c: nnc, r: nnr, dist: cur.dist + stepDist });
        }
      }
    }

    // Convert edge set to array
    var edges = [];
    var ekeys = Object.keys(edgeSet);
    for (var ek = 0; ek < ekeys.length; ek++) {
      var parts = ekeys[ek].split(',');
      edges.push({ from: parseInt(parts[0]), to: parseInt(parts[1]), cost: edgeSet[ekeys[ek]] });
    }

    // Fix orphans: connect isolated nodes to nearest connected node (Euclidean)
    // This handles doors embedded in walls where BFS can't traverse
    var connected = {};
    for (var ei2 = 0; ei2 < edges.length; ei2++) {
      connected[edges[ei2].from] = true;
      connected[edges[ei2].to] = true;
    }
    for (var oi = 0; oi < nodes.length; oi++) {
      if (connected[oi]) continue; // already has edges
      // Find nearest connected node by Euclidean distance
      var bestJ = -1, bestD = Infinity;
      for (var oj = 0; oj < nodes.length; oj++) {
        if (oj === oi || !connected[oj]) continue;
        var odx = nodes[oi].x - nodes[oj].x, ody = nodes[oi].y - nodes[oj].y;
        var od = Math.sqrt(odx * odx + ody * ody);
        if (od < bestD) { bestD = od; bestJ = oj; }
      }
      if (bestJ >= 0) {
        edges.push({ from: oi, to: bestJ, cost: bestD });
        connected[oi] = true;
      }
    }

    // Label nodes by nearest IfcSpace or IfcRoom if available
    labelNodes(nodes, storey);

    var template = { nodes: nodes, edges: edges, storey: storey, nodeMap: nodeMap, grid: g };
    routeTemplateCache[storey] = template;

    console.log('[S233] §ROUTE_TEMPLATE storey="' + storey + '" nodes=' + nodes.length +
      ' edges=' + edges.length + ' types=' +
      nodes.reduce(function(acc, n) { acc[n.type] = (acc[n.type] || 0) + 1; return acc; }, {}));

    return template;
  }

  // Label nodes with nearest IfcSpace name from DB
  function labelNodes(nodes, storey) {
    if (!A.db) return;
    var bld = A.activeBuilding || '';
    try {
      var sql = 'SELECT m.element_name, t.center_x, t.center_y FROM elements_meta m' +
        ' JOIN element_transforms t ON m.guid = t.guid' +
        " WHERE m.ifc_class IN ('IfcSpace','IfcRoom')" +
        ' AND m.storey = ?' + (bld ? ' AND m.building = ?' : '');
      var params = [storey]; if (bld) params.push(bld);
      var rows = A.db.exec(sql, params);
      if (!rows.length || !rows[0].values.length) return;

      var spaces = rows[0].values.map(function(r) { return { name: r[0], x: r[1], y: r[2] }; });

      for (var i = 0; i < nodes.length; i++) {
        var bestSpace = null, bestDist = Infinity;
        for (var si = 0; si < spaces.length; si++) {
          var sdx = nodes[i].x - spaces[si].x, sdy = nodes[i].y - spaces[si].y;
          var sd = sdx * sdx + sdy * sdy;
          if (sd < bestDist) { bestDist = sd; bestSpace = spaces[si]; }
        }
        // Only label if space is within 10m
        if (bestSpace && Math.sqrt(bestDist) < 10) {
          nodes[i].label = bestSpace.name || nodes[i].label;
        }
      }
    } catch(e) { /* no spaces */ }
  }

  // Graph A* on route template nodes
  function graphAStar(template, startIfc, endIfc) {
    var nodes = template.nodes;
    var edges = template.edges;

    // Find nearest node to start and end positions
    var startNode = nearestNode(nodes, startIfc.x, startIfc.y);
    var endNode = nearestNode(nodes, endIfc.x, endIfc.y);
    if (startNode < 0 || endNode < 0) return null;
    if (startNode === endNode) {
      // Same node — return direct path via that node
      return [nodes[startNode], nodes[endNode]];
    }

    // Build adjacency list
    var adj = {};
    for (var i = 0; i < nodes.length; i++) adj[i] = [];
    for (var ei = 0; ei < edges.length; ei++) {
      adj[edges[ei].from].push({ to: edges[ei].to, cost: edges[ei].cost });
      adj[edges[ei].to].push({ to: edges[ei].from, cost: edges[ei].cost });
    }

    // A* on graph
    var open = [{ node: startNode, g: 0, f: 0 }];
    var closed = {};
    var gScore = {};
    var parent = {};
    gScore[startNode] = 0;

    var maxIter = nodes.length * 10;
    var iter = 0;

    while (open.length > 0 && iter++ < maxIter) {
      // Find lowest f
      var bestI = 0;
      for (var oi = 1; oi < open.length; oi++) {
        if (open[oi].f < open[bestI].f) bestI = oi;
      }
      var cur = open.splice(bestI, 1)[0];
      if (cur.node === endNode) {
        // Reconstruct path
        var path = [endNode];
        var pk = endNode;
        while (parent[pk] !== undefined) {
          pk = parent[pk];
          path.unshift(pk);
        }
        return path.map(function(ni) { return nodes[ni]; });
      }
      closed[cur.node] = true;

      var neighbours = adj[cur.node] || [];
      for (var ai = 0; ai < neighbours.length; ai++) {
        var nb = neighbours[ai];
        if (closed[nb.to]) continue;
        var ng = cur.g + nb.cost;
        if (gScore[nb.to] === undefined || ng < gScore[nb.to]) {
          gScore[nb.to] = ng;
          parent[nb.to] = cur.node;
          // Heuristic: Euclidean distance to end node
          var hx = nodes[nb.to].x - nodes[endNode].x, hy = nodes[nb.to].y - nodes[endNode].y;
          var h = Math.sqrt(hx * hx + hy * hy);
          var inOpen = false;
          for (var oj = 0; oj < open.length; oj++) {
            if (open[oj].node === nb.to) {
              open[oj].g = ng; open[oj].f = ng + h;
              inOpen = true; break;
            }
          }
          if (!inOpen) open.push({ node: nb.to, g: ng, f: ng + h });
        }
      }
    }
    console.log('[S233] §GRAPH_ASTAR_FAIL startNode=' + startNode + ' endNode=' + endNode +
      ' visited=' + Object.keys(closed).length + '/' + nodes.length);
    return null; // no path on graph
  }

  // Find nearest graph node to an IFC position
  function nearestNode(nodes, x, y) {
    var best = -1, bestDist = Infinity;
    for (var i = 0; i < nodes.length; i++) {
      var dx = nodes[i].x - x, dy = nodes[i].y - y;
      var d = dx * dx + dy * dy;
      if (d < bestDist) { bestDist = d; best = i; }
    }
    return best;
  }

  // Convert graph A* result to waypoints with labels and interpolated steps
  function graphPathToWaypoints(graphPath, startIfc, endIfc, storey) {
    if (!graphPath || graphPath.length < 1) return null;

    var floorZ = 0;
    if (A.walkStoreyLevels) {
      for (var li = 0; li < A.walkStoreyLevels.length; li++) {
        if (A.walkStoreyLevels[li].storey === storey) { floorZ = A.walkStoreyLevels[li].floorZ; break; }
      }
    }

    // Build waypoints: start → graph nodes → end, interpolated at ~4m steps
    var STEP = 4;
    var waypoints = [];
    var points = [{ x: startIfc.x, y: startIfc.y, label: 'Start' }];
    for (var gi = 0; gi < graphPath.length; gi++) {
      points.push({ x: graphPath[gi].x, y: graphPath[gi].y, label: graphPath[gi].label || '' });
    }
    points.push({ x: endIfc.x, y: endIfc.y, label: 'Destination' });

    // Interpolate each segment
    for (var pi = 0; pi < points.length - 1; pi++) {
      var ax = points[pi].x, ay = points[pi].y;
      var bx = points[pi + 1].x, by = points[pi + 1].y;
      var dx = bx - ax, dy = by - ay;
      var segDist = Math.sqrt(dx * dx + dy * dy);
      var segSteps = Math.max(1, Math.ceil(segDist / STEP));

      for (var si = 0; si < segSteps; si++) {
        var t = si / segSteps;
        var wp = { x: ax + dx * t, y: ay + dy * t, z: floorZ, storey: storey };
        // Label the first point of each segment with the graph node label
        if (si === 0 && points[pi].label) wp.label = points[pi].label;
        waypoints.push(wp);
      }
    }
    // Add final destination
    waypoints.push({ x: endIfc.x, y: endIfc.y, z: floorZ, storey: storey, label: 'Destination' });

    return waypoints;
  }

  // Expose route template for tests and future ERP integration
  A.buildRouteTemplate = buildRouteTemplate;
  A.getRouteTemplate = function(storey) { return routeTemplateCache[storey] || null; };

  // ══════════════════════════════════════════════════════════════
  // SECTION C: BUILD FULL PATH (multi-storey)
  // ══════════════════════════════════════════════════════════════

  function buildPath(startIfc, targetIfc, targetStorey) {
    // Ensure storey levels are cached
    if (typeof A.cacheStoreyLevels === 'function' && (!A.walkStoreyLevels || A.walkStoreyLevels.length === 0)) {
      A.cacheStoreyLevels();
    }
    var levels = A.walkStoreyLevels || [];

    // Find start storey — the ground floor where the entrance door is.
    // Use entrance door's storey (from findMainEntrance query) rather than levels[0]
    // which may be foundation/underground.
    var startStorey = targetStorey; // fallback
    if (A.db) {
      try {
        var entrRows = A.db.exec(
          "SELECT m.storey, MIN(t.center_z) as min_z FROM elements_meta m" +
          " JOIN element_transforms t ON m.guid = t.guid" +
          " WHERE m.ifc_class IN ('IfcDoor','IfcDoorStandardCase')" +
          " GROUP BY m.storey HAVING min_z >= -0.5 ORDER BY min_z ASC LIMIT 1");
        if (entrRows.length > 0 && entrRows[0].values.length > 0) {
          startStorey = entrRows[0].values[0][0];
        }
      } catch(e) { /* fallback to targetStorey */ }
    }
    if (!startStorey && levels.length > 0) startStorey = levels[0].storey;
    console.log('[S233] §BUILD_PATH start=(' + startIfc.x.toFixed(1) + ',' + startIfc.y.toFixed(1) + ')' +
      ' target=(' + targetIfc.x.toFixed(1) + ',' + targetIfc.y.toFixed(1) + ')' +
      ' startStorey="' + startStorey + '" targetStorey="' + targetStorey + '"' +
      ' levels=' + levels.length + ' sameStorey=' + (startStorey === targetStorey));

    // If start is on same storey as target, single-storey path
    if (startStorey === targetStorey || levels.length < 2) {
      return buildSingleStoreyPath(startIfc, targetIfc, targetStorey);
    }

    // Multi-storey: find vertical transport
    var vt = findVerticalTransport();
    var links = matchStairsToStoreys(vt);

    // Build storey sequence
    var startLevel = -1, endLevel = -1;
    for (var li = 0; li < levels.length; li++) {
      if (levels[li].storey === startStorey) startLevel = li;
      if (levels[li].storey === targetStorey) endLevel = li;
    }
    if (startLevel < 0 || endLevel < 0) {
      // Fallback: direct path ignoring storeys
      return buildSingleStoreyPath(startIfc, targetIfc, targetStorey);
    }

    var waypoints = [];
    var currentPos = { x: startIfc.x, y: startIfc.y };
    var direction = endLevel > startLevel ? 1 : -1;

    for (var si = startLevel; si !== endLevel; si += direction) {
      var fromStorey = levels[si].storey;
      var toStorey = levels[si + direction].storey;

      // Find a stair/lift connecting these storeys
      var link = null;
      for (var lk = 0; lk < links.length; lk++) {
        if ((links[lk].fromStorey === fromStorey && links[lk].toStorey === toStorey) ||
            (links[lk].fromStorey === toStorey && links[lk].toStorey === fromStorey)) {
          link = links[lk]; break;
        }
      }

      if (link) {
        // Path on current storey to the stair
        var stairPos = { x: link.x, y: link.y };
        var pathToStair = buildSingleStoreyPath(currentPos, stairPos, fromStorey);
        if (pathToStair) waypoints = waypoints.concat(pathToStair);

        // Add storey transition waypoint
        waypoints.push({ x: link.x, y: link.y, z: levels[si + direction].floorZ,
          storey: toStorey, transition: direction > 0 ? 'up' : 'down', transitionName: toStorey });

        currentPos = { x: link.x, y: link.y };
      }
    }

    // Final path on target storey to the element
    var finalPath = buildSingleStoreyPath(currentPos, targetIfc, targetStorey);
    if (finalPath) waypoints = waypoints.concat(finalPath);

    return waypoints.length > 0 ? waypoints : null;
  }

  // Interpolate a straight line into ~4m steps so navigation feels like walking
  function interpolateLine(startIfc, endIfc, storey) {
    var STEP = 4; // metres per tap
    var dx = endIfc.x - startIfc.x, dy = endIfc.y - startIfc.y;
    var dist = Math.sqrt(dx * dx + dy * dy);
    var steps = Math.max(2, Math.ceil(dist / STEP));
    var floorZ = 0;
    if (A.walkStoreyLevels) {
      for (var li = 0; li < A.walkStoreyLevels.length; li++) {
        if (A.walkStoreyLevels[li].storey === storey) { floorZ = A.walkStoreyLevels[li].floorZ; break; }
      }
    }
    var wp = [];
    for (var i = 0; i <= steps; i++) {
      var t = i / steps;
      wp.push({ x: startIfc.x + dx * t, y: startIfc.y + dy * t, z: floorZ, storey: storey });
    }
    return wp;
  }

  function buildSingleStoreyPath(startIfc, endIfc, storey) {
    // Priority 1: Route template (graph A*) — fast, named waypoints
    var template = buildRouteTemplate(storey);
    if (template && template.nodes.length >= 2) {
      var graphPath = graphAStar(template, startIfc, endIfc);
      console.log('[S233] §GRAPH_TRY start=(' + startIfc.x.toFixed(1) + ',' + startIfc.y.toFixed(1) + ')' +
        ' end=(' + endIfc.x.toFixed(1) + ',' + endIfc.y.toFixed(1) + ')' +
        ' result=' + (graphPath ? graphPath.length + ' nodes' : 'null'));
      if (graphPath && graphPath.length >= 2) {
        var wpFromGraph = graphPathToWaypoints(graphPath, startIfc, endIfc, storey);
        if (wpFromGraph && wpFromGraph.length >= 2) {
          console.log('[S233] §PATH_ROUTE_TEMPLATE storey="' + storey + '" graph_nodes=' + graphPath.length +
            ' waypoints=' + wpFromGraph.length + ' labels=[' +
            graphPath.map(function(n) { return n.label; }).join(', ') + ']');
          return wpFromGraph;
        }
      }
      console.log('[S233] §PATH_ROUTE_TEMPLATE_FAIL storey="' + storey + '" graphPath=' +
        (graphPath ? graphPath.length : 'null') + ' falling back to grid A*');
    }

    // Priority 2: Grid A* — cell-based pathfinding
    var g = buildGrid(storey);
    if (!g) {
      // No grid possible — interpolated straight line (4m steps)
      console.log('[S233] §PATH_NO_GRID storey="' + storey + '" using interpolated straight line');
      return interpolateLine(startIfc, endIfc, storey);
    }

    var sc = toCell(g, startIfc.x, startIfc.y);
    var ec = toCell(g, endIfc.x, endIfc.y);
    console.log('[S233] §PATH_GRID_ASTAR storey="' + storey + '"' +
      ' start_cell=(' + sc.c + ',' + sc.r + ') end_cell=(' + ec.c + ',' + ec.r + ')' +
      ' start_walkable=' + (g.grid[sc.r * g.cols + sc.c] === 0) +
      ' end_walkable=' + (g.grid[ec.r * g.cols + ec.c] === 0));
    var cellPath = astar(g, sc.c, sc.r, ec.c, ec.r);

    if (!cellPath) {
      // A* failed — interpolated straight line fallback (4m steps)
      console.log('[S233] §PATH_ASTAR_FAIL storey="' + storey + '" using interpolated straight line');
      return interpolateLine(startIfc, endIfc, storey);
    }

    // Simplify: remove collinear waypoints, then coalesce short segments
    var simplified = [cellPath[0]];
    for (var i = 1; i < cellPath.length - 1; i++) {
      var prev = cellPath[i - 1], cur = cellPath[i], next = cellPath[i + 1];
      var dx1 = cur.c - prev.c, dy1 = cur.r - prev.r;
      var dx2 = next.c - cur.c, dy2 = next.r - cur.r;
      if (dx1 !== dx2 || dy1 !== dy2) simplified.push(cur); // direction changed = keep
    }
    if (cellPath.length > 1) simplified.push(cellPath[cellPath.length - 1]);

    // Coalesce: merge segments < MIN_STEP_M so each tap moves a meaningful distance
    var MIN_STEP_CELLS = 2; // 2 cells × 2m = 4m minimum per tap
    var coalesced = [simplified[0]];
    for (var ci = 1; ci < simplified.length; ci++) {
      var last = coalesced[coalesced.length - 1];
      var dcx = simplified[ci].c - last.c, dcy = simplified[ci].r - last.r;
      var cellDist = Math.sqrt(dcx * dcx + dcy * dcy);
      if (cellDist < MIN_STEP_CELLS && ci < simplified.length - 1) continue; // skip tiny segment (keep last)
      coalesced.push(simplified[ci]);
    }
    simplified = coalesced;

    // Convert back to IFC coords
    // Get floor Z for this storey
    var floorZ = 0;
    if (A.walkStoreyLevels) {
      for (var li2 = 0; li2 < A.walkStoreyLevels.length; li2++) {
        if (A.walkStoreyLevels[li2].storey === storey) { floorZ = A.walkStoreyLevels[li2].floorZ; break; }
      }
    }

    return simplified.map(function(cell) {
      var ifc = fromCell(g, cell.c, cell.r);
      return { x: ifc.x, y: ifc.y, z: floorZ, storey: storey };
    });
  }

  // ══════════════════════════════════════════════════════════════
  // SECTION D: TURN-BY-TURN NAVIGATION ENGINE
  // ══════════════════════════════════════════════════════════════

  var EYE_HEIGHT = 1.6;

  elNavBtn.onclick = function() {
    if (nav.activeIdx < 0 && nav.results.length > 0) nav.activeIdx = 0;
    if (nav.activeIdx < 0) return;
    startNavigation(nav.results[nav.activeIdx]);
  };

  function startNavigation(target) {
    // ── §NAV_DIAG: comprehensive pre-flight diagnostic ──
    console.log('[S233] §NAV_DIAG target="' + (target.element_name||'?') + '" class=' + target.ifc_class +
      ' storey="' + target.storey + '" pos=(' + target.cx.toFixed(1) + ',' + target.cy.toFixed(1) + ',' + target.cz.toFixed(1) + ')' +
      ' walkActive=' + !!A.walkModeActive + ' db=' + !!A.db +
      ' modelOffset=' + (A.modelOffset ? '(' + A.modelOffset.x.toFixed(1) + ',' + A.modelOffset.y.toFixed(1) + ',' + A.modelOffset.z.toFixed(1) + ')' : 'null') +
      ' camera=' + (A.camera ? '(' + A.camera.position.x.toFixed(1) + ',' + A.camera.position.y.toFixed(1) + ',' + A.camera.position.z.toFixed(1) + ')' : 'null') +
      ' bldCentres=' + Object.keys(A.buildingCentres || {}).length +
      ' storeyLevels=' + (A.walkStoreyLevels ? A.walkStoreyLevels.length : 0));

    // Smart start: if already walking → start from current position.
    // Otherwise → find the main entrance (furthest exterior door on ground floor).
    var startPos;
    var startLabel;
    if (A.walkModeActive && A.camera) {
      var cx = A.camera.position.x + A.modelOffset.x;
      var cy = -(A.camera.position.z) + A.modelOffset.y;
      var cz = A.camera.position.y + A.modelOffset.z;
      startPos = { x: cx, y: cy, z: cz };
      startLabel = 'current position';
    } else {
      startPos = findMainEntrance();
      startLabel = 'main entrance';
    }
    if (!startPos) {
      A.status.textContent = typeof _TRL!=='undefined'&&_TRL.ui_find_no_start||'No start position \u2014 cannot navigate';
      console.log('[S233] §NAV_NO_START db=' + !!A.db + ' bldCentres=' + JSON.stringify(Object.keys(A.buildingCentres || {})));
      return;
    }
    console.log('[S233] §NAV_START_POS from="' + startLabel + '" ifc=(' + startPos.x.toFixed(1) + ',' + startPos.y.toFixed(1) + ',' + (startPos.z||0).toFixed(1) + ')');

    // Cache storey levels BEFORE building path (buildPath needs them for startStorey)
    if (typeof A.cacheStoreyLevels === 'function') A.cacheStoreyLevels();

    // Build path
    var wp = buildPath(startPos, { x: target.cx, y: target.cy }, target.storey);
    if (!wp || wp.length < 2) {
      A.status.textContent = typeof _TRL!=='undefined'&&_TRL.ui_find_no_path||'No path found';
      console.log('[S233] §NAV_NO_PATH start=' + JSON.stringify(startPos) + ' target=' + JSON.stringify({ x: target.cx, y: target.cy }) + ' storey=' + target.storey);
      return;
    }
    console.log('[S233] §NAV_PATH waypoints=' + wp.length + ' from=' + startLabel +
      ' start=(' + startPos.x.toFixed(1) + ',' + startPos.y.toFixed(1) + ')' +
      ' target=(' + target.cx.toFixed(1) + ',' + target.cy.toFixed(1) + ')');

    nav.waypoints = wp;
    nav.stepIdx = 0;
    nav.active = true;
    nav.targetName = friendlyName(target.element_name, target.ifc_class);
    nav.targetIfc = { x: target.cx, y: target.cy, z: target.cz };
    A.navActive = true;
    A.navCurrentStep = 0;

    // Highlight target element NOW — visible throughout the entire walk
    var targetThree = A.ifc2three(target.cx, target.cy, target.cz);
    highlightElement(target.guid, new THREE.Vector3(targetThree.x, targetThree.y, targetThree.z));

    // Enter walk mode manually — DO NOT call setWalkAnchor() because it:
    // 1. Finds nearest door to camera (which is near target, not main entrance)
    // 2. Positions camera at that door (overriding our waypoint)
    // 3. Starts GPS/orientation that fights our camera control
    // Instead, activate walk mode minimally and position camera ourselves.
    // Keep OrbitControls ENABLED — user can pinch/orbit/mouse freely during navigation.
    // Walk button does orderly waypoint advance; free camera is always available.
    if (!A.walkModeActive) {
      A.walkModeActive = true;
      if (A.controls) A.controls.enabled = true;
      // Create drive-thru button (we'll override it for nav)
      if (typeof A.startDriveThru === 'function') A.startDriveThru();
      // Cache storey levels for floor height
      if (typeof A.cacheStoreyLevels === 'function') A.cacheStoreyLevels();
      var walkBtn = document.getElementById('walk-mode-btn');
      if (walkBtn) walkBtn.classList.add('active');
      // Hide anchor prompt if visible
      var prompt = document.getElementById('walk-anchor-prompt');
      if (prompt) prompt.style.display = 'none';
      console.log('[S233] §NAV_WALK_ACTIVATED minimal (no setWalkAnchor)');
    }

    // Snap camera to main entrance (waypoint 0) — the definitive starting point
    moveCameraToWaypoint(0, true);

    // Log all waypoints for debugging
    console.log('[S233] §NAV_WAYPOINTS_DUMP count=' + wp.length + ' [' +
      wp.map(function(w, i) { return i + ':(' + w.x.toFixed(0) + ',' + w.y.toFixed(0) + (w.label ? ',"' + w.label + '"' : '') + ')'; }).join(' → ') + ']');

    // Show HUD
    navHud.style.display = 'block';
    panel.style.display = 'none'; // hide find panel during nav
    updateNavHud();

    // Override drive-thru button
    overrideDriveButton();

    // Desktop keyboard listener
    document.addEventListener('keydown', navKeyHandler);

    // Desktop pointer lock DISABLED — user keeps free orbit/pinch/mouse control
    // setupPointerLock();

    var totalDist = computeTotalDistance();
    console.log('[S233] §NAV_START target="' + nav.targetName + '" from=' + startLabel + ' waypoints=' + wp.length + ' dist=' + totalDist.toFixed(1) + 'm voice=' + nav.voiceMode);

    speak('Navigate to ' + nav.targetName + ' from ' + startLabel + '. ' + Math.round(totalDist) + ' metres.');
  }

  A.startNavigation = startNavigation; // expose for tests
  A._nav = nav; // expose nav state for tests (waypoints, stepIdx, active)

  function stopNavigation() {
    nav.active = false;
    nav.waypoints = [];
    nav.stepIdx = 0;
    A.navActive = false;
    A.navCurrentStep = 0;
    navHud.style.display = 'none';

    // Restore drive-thru button
    restoreDriveButton();

    // Remove keyboard listener
    document.removeEventListener('keydown', navKeyHandler);

    // Release pointer lock
    if (document.pointerLockElement) document.exitPointerLock();

    // Fully exit walk mode — restore orbit controls and camera
    A.walkModeActive = false;
    if (A.controls) A.controls.enabled = true;
    if (A.camera) A.camera.rotation.reorder('XYZ');
    var walkBtn = document.getElementById('walk-mode-btn');
    if (walkBtn) walkBtn.classList.remove('active');

    console.log('[S233] §NAV_STOP walk_exited=true');
  }

  A.navJumpToEnd = function() {
    if (!nav.active || nav.waypoints.length === 0) return;
    nav.stepIdx = nav.waypoints.length - 1;
    A.navCurrentStep = nav.stepIdx;
    moveCameraToWaypoint(nav.stepIdx, false);
    onArrival();
  };

  // ── Move camera to waypoint ──
  function moveCameraToWaypoint(idx, instant) {
    var wp = nav.waypoints[idx];
    if (!wp) { console.warn('[S233] §NAV_WP_MISSING idx=' + idx); return; }

    var floorZ = wp.z || 0;
    var pos = A.ifc2three(wp.x, wp.y, floorZ + EYE_HEIGHT);
    var targetPos = new THREE.Vector3(pos.x, pos.y, pos.z);

    console.log('[S233] §NAV_MOVE_CAM wp=' + idx + '/' + nav.waypoints.length +
      ' ifc=(' + wp.x.toFixed(1) + ',' + wp.y.toFixed(1) + ',z=' + floorZ.toFixed(1) + ')' +
      ' three=(' + targetPos.x.toFixed(1) + ',' + targetPos.y.toFixed(1) + ',' + targetPos.z.toFixed(1) + ')' +
      ' instant=' + !!instant + (wp.label ? ' label="' + wp.label + '"' : '') +
      ' before_cam=(' + A.camera.position.x.toFixed(1) + ',' + A.camera.position.y.toFixed(1) + ',' + A.camera.position.z.toFixed(1) + ')');

    if (instant) {
      A.camera.position.copy(targetPos);
      console.log('[S233] §NAV_CAM_SNAPPED to=(' + A.camera.position.x.toFixed(1) + ',' + A.camera.position.y.toFixed(1) + ',' + A.camera.position.z.toFixed(1) + ')');
    } else {
      // Smooth lerp over 0.5s
      lerpCamera(targetPos, 500);
    }

    // Look toward next waypoint — skip if user controls camera (pointer lock or deviceOrientation)
    var nextIdx = Math.min(idx + 1, nav.waypoints.length - 1);
    if (nextIdx !== idx) {
      var nextWp = nav.waypoints[nextIdx];
      var nextFloorZ = nextWp.z || floorZ;
      var lookPos = A.ifc2three(nextWp.x, nextWp.y, nextFloorZ + EYE_HEIGHT);
      var lookTarget = new THREE.Vector3(lookPos.x, lookPos.y, lookPos.z);
      // Skip lookAt when pointer lock (desktop FPS) or deviceOrientation (mobile) active
      if (!nav.pointerLocked && !(A.walkModeActive && A._walkUnlocked)) {
        A.camera.lookAt(lookTarget);
      }
    }
  }

  function lerpCamera(target, durationMs) {
    var start = A.camera.position.clone();
    var startTime = performance.now();
    function tick() {
      var t = Math.min(1, (performance.now() - startTime) / durationMs);
      t = t * t * (3 - 2 * t); // smoothstep
      A.camera.position.lerpVectors(start, target, t);
      if (t < 1) requestAnimationFrame(tick);
    }
    tick();
  }

  // ── Advance to next waypoint ──
  A.advanceNavStep = function() {
    if (!nav.active) return;

    // If user orbited/pinched off-path, recalculate from current camera position
    var wp = nav.waypoints[nav.stepIdx];
    if (wp && A.camera) {
      var camIfc = { x: A.camera.position.x + (A.modelOffset ? A.modelOffset.x : 0),
                     y: -(A.camera.position.z) + (A.modelOffset ? A.modelOffset.y : 0) };
      var dx = camIfc.x - wp.x, dy = camIfc.y - wp.y;
      var offPath = Math.sqrt(dx * dx + dy * dy);
      if (offPath > 8) { // >8m off path = user wandered, recalculate
        var target = nav.results[nav.activeIdx];
        if (target) {
          var newWp = buildPath(camIfc, { x: target.cx, y: target.cy }, target.storey || wp.storey);
          if (newWp && newWp.length >= 2) {
            nav.waypoints = newWp;
            nav.stepIdx = 0;
            A.navCurrentStep = 0;
            console.log('[S233] §NAV_REPATH off=' + offPath.toFixed(1) + 'm new_waypoints=' + newWp.length);
            speak('Recalculating route');
          }
        }
      }
    }

    if (nav.stepIdx >= nav.waypoints.length - 1) {
      console.log('[S233] §NAV_STEP_ARRIVE final step=' + nav.stepIdx);
      onArrival();
      return;
    }
    nav.stepIdx++;
    A.navCurrentStep = nav.stepIdx;
    console.log('[S233] §NAV_STEP_ADV step=' + nav.stepIdx + '/' + nav.waypoints.length +
      ' wp=(' + nav.waypoints[nav.stepIdx].x.toFixed(1) + ',' + nav.waypoints[nav.stepIdx].y.toFixed(1) + ')' +
      (nav.waypoints[nav.stepIdx].label ? ' label="' + nav.waypoints[nav.stepIdx].label + '"' : ''));
    moveCameraToWaypoint(nav.stepIdx, false);
    updateNavHud();
    showDirectionCue();

    // Voice cue
    if (nav.voiceMode) {
      var cue = getDirectionCue();
      if (cue.label) speak(cue.label);
    }
  };

  function goBackStep() {
    if (!nav.active || nav.stepIdx <= 0) return;
    nav.stepIdx--;
    A.navCurrentStep = nav.stepIdx;
    moveCameraToWaypoint(nav.stepIdx, false);
    updateNavHud();
  }

  function resetToStart() {
    if (!nav.active) return;
    nav.stepIdx = 0;
    A.navCurrentStep = 0;
    moveCameraToWaypoint(0, false);
    updateNavHud();
    speak('Returning to start');
  }

  function onArrival() {
    var target = nav.results[nav.activeIdx];
    if (target) {
      var pos = A.ifc2three(target.cx, target.cy, target.cz);
      highlightElement(target.guid, new THREE.Vector3(pos.x, pos.y, pos.z));
      // Show info panel
      if (typeof A.showInfoPanel === 'function') {
        A.showInfoPanel(target.guid);
      } else {
        var ip = document.getElementById('info-panel');
        if (ip) {
          ip.style.display = 'block';
          var elClass = document.getElementById('info-class');
          var elNm = document.getElementById('info-name');
          if (elClass) elClass.textContent = target.ifc_class;
          if (elNm) elNm.textContent = target.element_name || '';
        }
      }
    }
    speak('Arrived at ' + (nav.targetName || 'target'));
    showCue('arrival', 'ARRIVED');
    console.log('[S233] §NAV_ARRIVE step=' + nav.stepIdx + '/' + nav.waypoints.length);

    // End navigation but STAY in walk mode — user continues exploring from here.
    // Fade HUD after 3s, restore normal walk arrow (forward movement, not waypoint).
    setTimeout(function() {
      nav.active = false;
      A.navActive = false;
      navHud.style.display = 'none';
      restoreDriveButton(); // walk arrow goes back to normal forward movement
      document.removeEventListener('keydown', navKeyHandler);
      if (A.status) A.status.textContent = typeof _TRL!=='undefined'&&_TRL.ui_find_arrived||'Arrived \u2014 walk freely or Find again';
      console.log('[S233] §NAV_CONTINUE_WALK');
    }, 3000);
    // Do NOT call stopNavigation() — that exits walk mode entirely
  }

  // ── Direction cues ──
  function getDirectionCue() {
    if (nav.stepIdx <= 0 || nav.stepIdx >= nav.waypoints.length - 1) {
      return { icon: '\u2191', label: 'Go straight', cls: 'straight' };
    }
    var prev = nav.waypoints[nav.stepIdx - 1];
    var cur = nav.waypoints[nav.stepIdx];
    var next = nav.waypoints[nav.stepIdx + 1];

    // Check storey transition
    if (cur.transition) {
      var upDown = cur.transition === 'up' ? 'Go up' : 'Go down';
      return { icon: cur.transition === 'up' ? '\u2B06' : '\u2B07',
        label: upDown + ' to ' + (cur.transitionName || 'next floor'), cls: cur.transition };
    }

    // Bearing change
    var b1 = Math.atan2(cur.y - prev.y, cur.x - prev.x);
    var b2 = Math.atan2(next.y - cur.y, next.x - cur.x);
    var delta = ((b2 - b1) * 180 / Math.PI + 360) % 360;
    if (delta > 180) delta -= 360;

    // Named waypoint label from route template (e.g. "at Cross Aisle", "at Door")
    var atLabel = next.label ? ' at ' + next.label : '';

    if (Math.abs(delta) < 15) return { icon: '\u2191', label: 'Go straight' + atLabel, cls: 'straight' };
    if (Math.abs(delta) < 30) return delta > 0 ?
      { icon: '\u2197', label: 'Slight left' + atLabel, cls: 'slight-left' } :
      { icon: '\u2198', label: 'Slight right' + atLabel, cls: 'slight-right' };
    if (Math.abs(delta) < 150) return delta > 0 ?
      { icon: '\u2190', label: 'Turn left' + atLabel, cls: 'left' } :
      { icon: '\u2192', label: 'Turn right' + atLabel, cls: 'right' };
    return { icon: '\u21B0', label: 'U-turn' + atLabel, cls: 'uturn' };
  }

  function showDirectionCue() {
    var cue = getDirectionCue();
    showCue(cue.cls, cue.label, cue.icon);
  }

  function showCue(cls, label, icon) {
    elCue.querySelector('.cue-icon').textContent = icon || '';
    elCue.querySelector('.cue-label').textContent = label || '';
    elCue.className = 'visible nav-cue-' + cls;
    clearTimeout(nav._cueTimer);
    nav._cueTimer = setTimeout(function() { elCue.className = ''; }, 2500);
  }

  // ── HUD bottom bar ──
  function updateNavHud() {
    var remaining = computeRemainingDistance();
    var direct = computeDirectDistance();
    var total = nav.waypoints.length;
    var step = nav.stepIdx + 1;
    elBar.textContent = nav.targetName + '  \u2022  ' + remaining.toFixed(0) + 'm (' + direct.toFixed(0) + 'm direct)  \u2022  ' + step + '/' + total;
  }

  // Straight-line distance from current waypoint to target element
  function computeDirectDistance() {
    if (!nav.targetIfc || nav.stepIdx >= nav.waypoints.length) return 0;
    var cur = nav.waypoints[nav.stepIdx];
    var dx = nav.targetIfc.x - cur.x;
    var dy = nav.targetIfc.y - cur.y;
    var dz = (nav.targetIfc.z || 0) - (cur.z || 0);
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  function computeTotalDistance() {
    var d = 0;
    for (var i = 1; i < nav.waypoints.length; i++) {
      var dx = nav.waypoints[i].x - nav.waypoints[i - 1].x;
      var dy = nav.waypoints[i].y - nav.waypoints[i - 1].y;
      d += Math.sqrt(dx * dx + dy * dy);
    }
    return d;
  }

  function computeRemainingDistance() {
    var d = 0;
    for (var i = nav.stepIdx + 1; i < nav.waypoints.length; i++) {
      var dx = nav.waypoints[i].x - nav.waypoints[i - 1].x;
      var dy = nav.waypoints[i].y - nav.waypoints[i - 1].y;
      d += Math.sqrt(dx * dx + dy * dy);
    }
    return d;
  }

  // ══════════════════════════════════════════════════════════════
  // SECTION E: CONTROLS — WALK BUTTON OVERRIDE + DESKTOP
  // ══════════════════════════════════════════════════════════════

  var _origDriveTouchStart = null;

  function overrideDriveButton() {
    var btn = document.getElementById('drive-thru-btn');
    if (!btn) return;

    // Save original handler by cloning the button (removes all listeners)
    var newBtn = btn.cloneNode(true);
    btn.parentNode.replaceChild(newBtn, btn);
    A._driveBtn = newBtn;

    var navTap = function(e) {
      e.preventDefault();
      if (nav.active) {
        A.advanceNavStep();
      }
    };
    newBtn.addEventListener('touchstart', navTap, { passive: false });
    newBtn.addEventListener('mousedown', navTap);
    // Disable hold — no interval
    newBtn.addEventListener('touchend', function(e) { e.preventDefault(); });
    newBtn.addEventListener('mouseup', function() {});
    _origDriveTouchStart = navTap; // track for cleanup
  }

  function restoreDriveButton() {
    // Remove and let walk.js recreate if still in walk mode
    var btn = document.getElementById('drive-thru-btn');
    if (btn) btn.remove();
    A._driveBtn = null;
    // If walk mode is still active, recreate original drive button
    if (A.walkModeActive && typeof A.startDriveThru === 'function') {
      A.startDriveThru();
    }
  }

  // ── Desktop keyboard controls ──
  function navKeyHandler(e) {
    if (!nav.active) return;
    switch (e.key) {
      case 'ArrowUp': case 'w': case 'W': case 'Enter': case ' ':
        e.preventDefault();
        A.advanceNavStep();
        break;
      case 'ArrowDown': case 's': case 'S':
        e.preventDefault();
        goBackStep();
        break;
      case 'Home':
        e.preventDefault();
        resetToStart();
        break;
      case 'Escape':
        e.preventDefault();
        closeFindPanel();
        break;
    }
  }

  // ── Desktop pointer lock (FPS mouse look) ──
  function setupPointerLock() {
    // Only on desktop (no touch)
    if ('ontouchstart' in window) return;
    var canvas = document.getElementById('canvas') || document.querySelector('canvas');
    if (!canvas) return;

    canvas.addEventListener('click', function plClick() {
      if (nav.active && !document.pointerLockElement) {
        canvas.requestPointerLock();
      }
    });

    document.addEventListener('pointerlockchange', function() {
      nav.pointerLocked = !!document.pointerLockElement;
    });

    document.addEventListener('mousemove', function(e) {
      if (!nav.active || !nav.pointerLocked) return;
      // FPS-style mouse look
      var sensitivity = 0.002;
      A.camera.rotation.y -= e.movementX * sensitivity;
      A.camera.rotation.x -= e.movementY * sensitivity;
      // Clamp pitch
      A.camera.rotation.x = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, A.camera.rotation.x));
    });

    // Click during nav = advance (if pointer locked)
    canvas.addEventListener('mousedown', function(e) {
      if (nav.active && nav.pointerLocked && e.button === 0) {
        A.advanceNavStep();
      }
    });
  }

  // ── Voice output (modality-matched) ──
  function speak(text) {
    if (!nav.voiceMode) return;
    if (!window.speechSynthesis) return;
    var u = new SpeechSynthesisUtterance(text);
    u.rate = 1.1;
    u.pitch = 1.0;
    window.speechSynthesis.speak(u);
  }

  // ── ESC from keyboard (also in non-nav mode to close find panel) ──
  document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape' && panel.style.display !== 'none' && !nav.active) {
      closeFindPanel();
    }
  });

  // ── Pre-process route templates on streaming complete ──
  // Builds occupancy grids + route templates for all storeys as soon as DB is ready.
  // Navigation will be instant — no cold-start grid build on first Navigate click.
  A.preProcessRouteTemplates = function() {
    if (!A.db) { console.log('[S233] §NAV_PREPROCESS no db'); return; }
    var bld = A.activeBuilding || '';
    try {
      var sql = 'SELECT DISTINCT storey FROM elements_meta WHERE storey IS NOT NULL';
      var params = [];
      if (bld) { sql += ' AND building = ?'; params.push(bld); }
      var rows = A.db.exec(sql, params);
      if (!rows.length || !rows[0].values.length) { console.log('[S233] §NAV_PREPROCESS no storeys'); return; }
      var storeys = rows[0].values.map(function(r) { return r[0]; });
      var totalNodes = 0, totalEdges = 0;
      for (var i = 0; i < storeys.length; i++) {
        var tmpl = buildRouteTemplate(storeys[i]);
        if (tmpl) { totalNodes += tmpl.nodes.length; totalEdges += tmpl.edges.length; }
      }
      console.log('[S233] §NAV_PREPROCESS storeys=' + storeys.length + ' totalNodes=' + totalNodes +
        ' totalEdges=' + totalEdges + ' storeyList=[' + storeys.join(', ') + ']');
    } catch(e) { console.warn('[S233] §NAV_PREPROCESS_ERR', e.message); }
  };

  // Auto-preprocess when streaming completes — observe s-active turning green
  var _ppObserver = new MutationObserver(function(mutations) {
    for (var i = 0; i < mutations.length; i++) {
      var el = mutations[i].target;
      if (el && el.textContent && el.textContent.indexOf('DONE') >= 0) {
        _ppObserver.disconnect();
        // Small delay to let populateStoreys/Discs finish first
        setTimeout(function() { A.preProcessRouteTemplates(); }, 500);
        return;
      }
    }
  });
  var _sActive = document.getElementById('s-active');
  if (_sActive) {
    // If already done (e.g. navigate.js loaded after streaming finished)
    if (_sActive.textContent.indexOf('DONE') >= 0) {
      setTimeout(function() { A.preProcessRouteTemplates(); }, 500);
    } else {
      _ppObserver.observe(_sActive, { childList: true, characterData: true, subtree: true });
    }
  }

  console.log('[S233] §NAV_MODULE_LOADED');
}
