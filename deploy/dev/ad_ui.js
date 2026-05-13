// ad_ui.js — Implementing ERP_AD_UI.md §2–§5, §10, §12, §14, §16 — Witness: W-ERP-ADUI
// AD-driven UI renderer: bottom nav, menu screen, window/tab/field cards.
// Depends on: ad_parser.js, ad_data.js, ad_charts.js, kernel_ops.js
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
(function () {
  'use strict';

  var _db = null;
  var _contentEl = null;
  var _navEl = null;
  var _breadcrumbEl = null;
  var _chartOverlay = null;
  var _currentScreen = 'home';   // home | window
  var _currentWindow = null;     // window object from ADParser
  var _currentTabIdx = 0;
  var _currentRecords = [];
  var _currentRecordIdx = 0;
  var _parentRecord = null;      // for master-detail
  var _recentWindows = [];       // [{id, name}] from localStorage
  var _helpPanel = null;          // right-side help panel element
  var _helpVisible = false;
  var _heatmapHitRegions = [];    // for tap-to-drill on treemap
  var _graphAutoMaxed = false;    // auto-maximize globe on first load
  var _currentClient = 'system';  // 'system' | 'gardenworld'
  var GW_WINDOW_SET = null; // built on init from tables that actually have rows

  // ── §2. Bottom navigation bar ──────────────────────────────────────

  function _renderBottomNav() {
    if (!_navEl) return;
    var items = [
      { icon: '\uD83C\uDFE0', label: 'Home',   action: 'home' },
      { icon: '\uD83D\uDCCB', label: 'List',   action: 'list' },
      { icon: '\u2795',       label: 'New',    action: 'new' },
      { icon: '\uD83D\uDCCA', label: 'Charts', action: 'charts' },
      { icon: '\u2699\uFE0F', label: 'More',   action: 'more' }
    ];
    _navEl.innerHTML = '';
    _navEl.style.cssText = 'position:fixed;bottom:0;left:0;right:0;z-index:10;' +
      'background:rgba(18,18,24,0.92);backdrop-filter:blur(12px);' +
      '-webkit-backdrop-filter:blur(12px);border-top:1px solid rgba(255,255,255,0.06);' +
      'display:flex;min-height:52px;';

    for (var i = 0; i < items.length; i++) {
      var btn = document.createElement('button');
      btn.dataset.nav = items[i].action;
      btn.innerHTML = '<div style="font-size:18px">' + items[i].icon + '</div>' +
        '<div style="font-size:10px;margin-top:2px">' + items[i].label + '</div>';
      var navActive = items[i].action === _currentScreen;
      var navColour = navActive ? '#6c9fff' : '#555';
      btn.style.cssText = 'flex:1;background:none;border:none;color:' + navColour +
        ';padding:6px 0;cursor:pointer;min-height:52px;display:flex;' +
        'flex-direction:column;align-items:center;justify-content:center;' +
        'transition:color 0.15s;font-weight:' + (navActive ? '600' : '400') + ';';
      btn.addEventListener('pointerup', _navHandler(items[i].action));
      _navEl.appendChild(btn);
    }
    console.log('§AD_UI bottomNav rendered');
  }

  function _navHandler(action) {
    return function (e) {
      e.preventDefault();
      console.log('§AD_UI nav action=' + action);
      if (action === 'home') showMenu();
      else if (action === 'list') _showRecordList();
      else if (action === 'new') _createNewRecord();
      else if (action === 'charts') _showCharts();
      else if (action === 'more') _showMore();
    };
  }

  // ── §3. Menu screen (Home) ─────────────────────────────────────────

  function showMenu() {
    _currentScreen = 'home';
    _renderBottomNav();
    _contentEl.innerHTML = '';

    // Breadcrumb — reset style from window view
    _breadcrumbEl.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:10;' +
      'background:rgba(18,18,24,0.92);backdrop-filter:blur(12px);' +
      '-webkit-backdrop-filter:blur(12px);border-bottom:1px solid rgba(255,255,255,0.06);' +
      'padding:12px 16px;min-height:48px;display:flex;align-items:center;gap:8px;';
    _breadcrumbEl.innerHTML = '<span style="font-size:16px;font-weight:bold;color:#eee">' +
      '\u2630 ERP OOTB</span>';

    // Client switcher — pill toggle
    var switcher = document.createElement('div');
    switcher.style.cssText = 'display:flex;margin-bottom:16px;background:#1a1a24;' +
      'border-radius:12px;padding:3px;gap:3px;';
    var clients = [
      { id: 'system', label: 'System', colour: '#6c9fff' },
      { id: 'gardenworld', label: 'GardenWorld', colour: '#ff9f43' }
    ];
    for (var ci = 0; ci < clients.length; ci++) {
      var cBtn = document.createElement('button');
      cBtn.textContent = clients[ci].label;
      cBtn.dataset.client = clients[ci].id;
      var isActive = (_currentClient === clients[ci].id);
      cBtn.style.cssText = 'flex:1;padding:10px;border:none;font-size:13px;font-weight:600;' +
        'cursor:pointer;min-height:44px;border-radius:10px;transition:all 0.2s;background:' +
        (isActive ? clients[ci].colour : 'transparent') + ';color:' +
        (isActive ? '#121218' : '#666') + ';';
      cBtn.addEventListener('pointerup', function () {
        _currentClient = this.dataset.client;
        console.log('§AD_UI switchClient client=' + _currentClient);
        showMenu();
      });
      switcher.appendChild(cBtn);
    }
    _contentEl.appendChild(switcher);

    // Data constellation — interactive graph replaces KPI cards
    _renderHomeGraph();

    // Recent windows
    _loadRecent();
    if (_recentWindows.length) {
      var recentEl = document.createElement('div');
      recentEl.style.cssText = 'margin-bottom:12px;font-size:13px;color:#888;';
      recentEl.textContent = 'Recent: ';
      for (var r = 0; r < _recentWindows.length && r < 5; r++) {
        var rw = _recentWindows[r];
        var rLink = document.createElement('a');
        rLink.href = '#';
        rLink.textContent = rw.name;
        rLink.style.cssText = 'color:#4fc3f7;text-decoration:none;margin-right:8px;';
        rLink.dataset.windowId = rw.id;
        rLink.addEventListener('pointerup', function (ev) {
          ev.preventDefault();
          openWindow(Number(this.dataset.windowId));
        });
        recentEl.appendChild(rLink);
      }
      _contentEl.appendChild(recentEl);
    }

    // Build set of windows that have browsable data
    if (!GW_WINDOW_SET) _buildWindowSets();

    // Menu tree — filtered by client
    var tree = ADParser.getMenuTree(_db);
    var treeEl = document.createElement('div');
    var windowSet = (_currentClient === 'system') ? _systemWindowSet : GW_WINDOW_SET;
    _renderMenuNodes(treeEl, tree, windowSet);
    _contentEl.appendChild(treeEl);

    // (Search is now a floating overlay — see _toggleSearchOverlay / Alt+S)

    console.log('§AD_UI showMenu roots=' + tree.length + ' client=' + _currentClient +
                ' recent=' + _recentWindows.length);
  }

  // ── KPI cards ──────────────────────────────────────────────────────

  function _renderKPICards() {
    var kpis;
    if (_currentClient === 'system') {
      kpis = [
        { label: 'Windows', sql: 'SELECT COUNT(*) FROM AD_Window', icon: '\u25A3', colour: '#6c9fff', windowId: 102 },
        { label: 'Tables', sql: 'SELECT COUNT(*) FROM AD_Table', icon: '\u2637', colour: '#a78bfa', windowId: 100 },
        { label: 'Fields', sql: 'SELECT COUNT(*) FROM AD_Field', icon: '\u2630', colour: '#54d9a8' },
        { label: 'Menus', sql: 'SELECT COUNT(*) FROM AD_Menu', icon: '\u2261', colour: '#ff9f43', windowId: 105 }
      ];
    } else {
      kpis = [
        { label: 'Partners', sql: 'SELECT COUNT(*) FROM C_BPartner', icon: '\u263A', colour: '#6c9fff', windowId: 123 },
        { label: 'Products', sql: 'SELECT COUNT(*) FROM M_Product', icon: '\u2B22', colour: '#54d9a8', windowId: 140 },
        { label: 'Prices', sql: 'SELECT COUNT(*) FROM M_ProductPrice', icon: '\u2696', colour: '#ff9f43' },
        { label: 'Categories', sql: 'SELECT COUNT(*) FROM M_Product_Category', icon: '\u2606', colour: '#a78bfa' }
      ];
    }

    var grid = document.createElement('div');
    grid.style.cssText = 'display:grid;grid-template-columns:repeat(auto-fit,minmax(100px,1fr));' +
      'gap:10px;margin-bottom:16px;animation:fadeIn 0.3s ease;';

    for (var k = 0; k < kpis.length; k++) {
      var val = 0;
      try {
        var r = _db.exec(kpis[k].sql);
        if (r.length) val = Number(r[0].values[0][0]);
      } catch (e) { /* table missing */ }

      var card = document.createElement('div');
      card.dataset.kpiWindow = kpis[k].windowId || '';
      card.style.cssText = 'background:linear-gradient(135deg,#1e1e2a,#252535);' +
        'border:1px solid rgba(255,255,255,0.06);border-radius:14px;padding:14px;' +
        'text-align:center;cursor:' + (kpis[k].windowId ? 'pointer' : 'default') + ';' +
        'transition:transform 0.15s;';
      if (kpis[k].windowId) {
        card.addEventListener('pointerup', function () {
          var wid = Number(this.dataset.kpiWindow);
          if (wid) openWindow(wid);
        });
        card.onpointerenter = function() { this.style.transform = 'scale(1.04)'; };
        card.onpointerleave = function() { this.style.transform = ''; };
      }
      card.innerHTML = '<div style="font-size:22px;margin-bottom:4px">' + kpis[k].icon + '</div>' +
        '<div style="font-size:24px;font-weight:700;color:' + kpis[k].colour + '">' +
        val.toLocaleString() + '</div>' +
        '<div style="font-size:11px;color:#888;margin-top:2px;text-transform:uppercase;' +
        'letter-spacing:1px">' + kpis[k].label + '</div>';
      grid.appendChild(card);
    }

    _contentEl.appendChild(grid);
    console.log('§AD_UI KPI rendered client=' + _currentClient);
  }

  // ── Data constellation (graph view on home) ────────────────────────

  var _graphCanvas = null;
  var _graphContainer = null;  // the div that goes fullscreen

  function _graphDrillCallback(tableName, windowId, record) {
    var wid = windowId;
    if (!wid) {
      try {
        var wr = _db.exec(
          'SELECT w.AD_Window_ID FROM AD_Window w ' +
          'JOIN AD_Tab t ON w.AD_Window_ID = t.AD_Window_ID ' +
          'JOIN AD_Table tbl ON t.AD_Table_ID = tbl.AD_Table_ID ' +
          "WHERE tbl.TableName = ? AND w.IsActive = 'Y' LIMIT 1", [tableName]);
        if (wr.length && wr[0].values.length) wid = Number(wr[0].values[0][0]);
      } catch (e) { /* no window */ }
    }
    if (wid) {
      openWindow(wid);
      if (record) {
        var keyCol = tableName + '_ID';
        var keyVal = record[keyCol];
        if (keyVal !== undefined) {
          for (var ri = 0; ri < _currentRecords.length; ri++) {
            if (_currentRecords[ri][keyCol] === keyVal) {
              _currentRecordIdx = ri;
              _renderWindow();
              console.log('§AD_UI drillToRecord key=' + keyCol + ' val=' + keyVal + ' idx=' + ri);
              break;
            }
          }
        }
      }
    }
  }

  function _graphLongPressCallback(node) {
    if (node.windowId) {
      openWindow(node.windowId);
    } else if (node.record) {
      _showToast(node.label);
    }
  }

  function _renderHomeGraph() {
    if (typeof ADGraph === 'undefined') {
      // Fallback to heatmap if ad_graph.js not loaded
      _renderHeatmap('home');
      return;
    }

    var container = document.createElement('div');
    container.dataset.graphContainer = '1';
    container.style.cssText = 'background:linear-gradient(135deg,#0e0e14,#1a1a28);' +
      'border:1px solid rgba(255,255,255,0.06);border-radius:14px;' +
      'margin-bottom:16px;overflow:hidden;position:relative;';

    var canvas = document.createElement('canvas');
    // Use viewport dimensions — always available, never 0
    var vw = (typeof window !== 'undefined' && window.innerWidth > 100) ? window.innerWidth : 480;
    var vh = (typeof window !== 'undefined' && window.innerHeight > 100) ? window.innerHeight : 600;
    // In landscape (vw > vh), make canvas square-ish using vh as reference
    // In portrait, use full width, 70% height
    var cw = Math.min(vw, 960);  // cap at 960 for desktop
    var ch = (vw > vh) ? Math.round(vh * 0.6) : Math.round(cw * 0.7);
    ch = Math.max(ch, 280);
    canvas.width = cw;
    canvas.height = ch;
    canvas.style.cssText = 'display:block;width:100%;height:' + ch + 'px;' +
      'cursor:grab;touch-action:none;';
    container.appendChild(canvas);
    _graphCanvas = canvas;
    _graphContainer = container;

    // Fullscreen toggle — uses browser Fullscreen API (hides URL bar)
    var fsBtn = document.createElement('button');
    fsBtn.textContent = '\u26F6';  // ⛶
    fsBtn.style.cssText = 'position:absolute;top:6px;right:6px;background:rgba(0,0,0,0.5);' +
      'border:1px solid rgba(255,255,255,0.15);color:#aaa;font-size:16px;' +
      'cursor:pointer;width:28px;height:28px;border-radius:6px;z-index:5;' +
      'display:flex;align-items:center;justify-content:center;line-height:1;';

    function _resizeGraph(fullscreen) {
      if (fullscreen) {
        container.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:0;z-index:60;' +
          'background:#0a0a12;border:none;border-radius:0;margin:0;overflow:hidden;';
        // Match actual viewport — no distortion in landscape
        var vw = window.innerWidth;
        var vh = window.innerHeight;
        canvas.width = vw;
        canvas.height = vh;
        canvas.style.cssText = 'display:block;width:100%;height:100%;cursor:grab;touch-action:none;';
        fsBtn.textContent = '\u2212';
        fsBtn.style.top = '10px';
        fsBtn.style.right = '10px';
        fsBtn.style.width = '36px';
        fsBtn.style.height = '36px';
        fsBtn.style.fontSize = '20px';
      } else {
        container.style.cssText = 'background:linear-gradient(135deg,#0e0e14,#1a1a28);' +
          'border:1px solid rgba(255,255,255,0.06);border-radius:14px;' +
          'margin-bottom:16px;overflow:hidden;position:relative;';
        var rvw = window.innerWidth || 480;
        var rvh = window.innerHeight || 600;
        var rw = Math.min(rvw, 960);
        var rh = (rvw > rvh) ? Math.round(rvh * 0.6) : Math.round(rw * 0.7);
        rh = Math.max(rh, 280);
        canvas.width = rw;
        canvas.height = rh;
        canvas.style.cssText = 'display:block;width:100%;height:' + rh + 'px;cursor:grab;touch-action:none;';
        fsBtn.textContent = '\u26F6';
        fsBtn.style.top = '6px';
        fsBtn.style.right = '6px';
        fsBtn.style.width = '28px';
        fsBtn.style.height = '28px';
        fsBtn.style.fontSize = '16px';
      }
      ADGraph.destroy();
      ADGraph.init(canvas, _db, _currentClient,
        _graphDrillCallback, _graphLongPressCallback);
      console.log('§AD_UI graphFullscreen=' + fullscreen +
        ' w=' + canvas.width + ' h=' + canvas.height);
    }

    fsBtn.addEventListener('pointerup', function (e) {
      e.stopPropagation();
      if (!document.fullscreenElement) {
        // Enter true fullscreen (hides URL bar)
        var target = container;
        var rfs = target.requestFullscreen || target.webkitRequestFullscreen;
        if (rfs) {
          rfs.call(target).then(function () { _resizeGraph(true); })
            .catch(function () { _resizeGraph(true); }); // fallback if promise rejected
        } else {
          _resizeGraph(true); // fallback: no Fullscreen API
        }
      } else {
        (document.exitFullscreen || document.webkitExitFullscreen).call(document);
      }
    });

    // Listen for fullscreen exit (ESC or browser back)
    document.addEventListener('fullscreenchange', function () {
      if (!document.fullscreenElement && container.dataset.graphContainer) {
        _resizeGraph(false);
      }
    });

    container.appendChild(fsBtn);

    _contentEl.appendChild(container);

    // Init graph
    ADGraph.init(canvas, _db, _currentClient,
      _graphDrillCallback, _graphLongPressCallback);

    // Auto-maximize globe on first load
    if (!_graphAutoMaxed) {
      _graphAutoMaxed = true;
      setTimeout(function () { _resizeGraph(true); }, 100);
    }

    console.log('§AD_UI graphView rendered client=' + _currentClient);
  }

  // ── §16. Context-aware heatmap panel ────────────────────────────────

  var TYPE_COLOURS = {
    system:     '#6c9fff',  // AD_ tables — blue
    commercial: '#ff9f43',  // C_ tables — amber
    material:   '#54d9a8',  // M_ tables — green
    other:      '#a78bfa'   // everything else — purple
  };

  function _renderHeatmap(context) {
    // context = 'home' | 'window'
    var container = document.createElement('div');
    container.style.cssText = 'background:linear-gradient(135deg,#1e1e2a,#252535);' +
      'border:1px solid rgba(255,255,255,0.06);border-radius:14px;padding:12px;' +
      'margin-bottom:16px;animation:fadeIn 0.3s ease;';

    var canvas = document.createElement('canvas');
    canvas.width = 480;
    canvas.height = 260;
    canvas.style.cssText = 'display:block;width:100%;height:auto;cursor:pointer;';
    container.appendChild(canvas);

    if (context === 'home') {
      _drawHomeHeatmap(canvas);
    } else if (context === 'window') {
      _drawWindowHeatmap(canvas);
    }

    _contentEl.appendChild(container);
  }

  function _drawHomeHeatmap(canvas) {
    var items = [];

    if (_currentClient === 'system') {
      // System: AD metadata volumes
      var sysQueries = [
        { label: 'Columns', table: 'AD_Column', colour: '#6c9fff' },
        { label: 'Fields', table: 'AD_Field', colour: '#54d9a8' },
        { label: 'Tables', table: 'AD_Table', colour: '#a78bfa' },
        { label: 'Tabs', table: 'AD_Tab', colour: '#ff9f43' },
        { label: 'Windows', table: 'AD_Window', colour: '#38d9d9' },
        { label: 'Menus', table: 'AD_Menu', colour: '#ffd93d' },
        { label: 'References', table: 'AD_Reference', colour: '#ff85a2' },
        { label: 'Ref Lists', table: 'AD_Ref_List', colour: '#7bed9f' }
      ];
      for (var si = 0; si < sysQueries.length; si++) {
        var cnt = ADData.countRecords(_db, sysQueries[si].table);
        if (cnt > 0) items.push({
          label: sysQueries[si].label + ' (' + cnt + ')',
          value: cnt, colour: sysQueries[si].colour,
          tableName: sysQueries[si].table, windowId: null
        });
      }
    } else {
      // GardenWorld: semantic business categories
      var custCnt = 0, vendCnt = 0;
      try {
        var cr = _db.exec("SELECT COUNT(*) FROM C_BPartner WHERE IsCustomer='Y'");
        custCnt = cr.length ? Number(cr[0].values[0][0]) : 0;
      } catch (e) {}
      try {
        var vr = _db.exec("SELECT COUNT(*) FROM C_BPartner WHERE IsVendor='Y'");
        vendCnt = vr.length ? Number(vr[0].values[0][0]) : 0;
      } catch (e) {}
      var prodCnt = ADData.countRecords(_db, 'M_Product');
      var catCnt = ADData.countRecords(_db, 'M_Product_Category');
      var priceCnt = ADData.countRecords(_db, 'M_ProductPrice');
      var contactCnt = ADData.countRecords(_db, 'AD_User');

      if (custCnt > 0) items.push({ label: 'Customers (' + custCnt + ')', value: custCnt,
        colour: '#6c9fff', tableName: 'C_BPartner', windowId: 123 });
      if (vendCnt > 0) items.push({ label: 'Vendors (' + vendCnt + ')', value: vendCnt,
        colour: '#ff9f43', tableName: 'C_BPartner', windowId: 123 });
      if (prodCnt > 0) items.push({ label: 'Products (' + prodCnt + ')', value: prodCnt,
        colour: '#54d9a8', tableName: 'M_Product', windowId: 140 });
      if (catCnt > 0) items.push({ label: 'Categories (' + catCnt + ')', value: catCnt,
        colour: '#a78bfa', tableName: 'M_Product_Category', windowId: null });
      if (priceCnt > 0) items.push({ label: 'Prices (' + priceCnt + ')', value: priceCnt,
        colour: '#ffd93d', tableName: 'M_ProductPrice', windowId: null });
      if (contactCnt > 0) items.push({ label: 'Contacts (' + contactCnt + ')', value: contactCnt,
        colour: '#38d9d9', tableName: 'AD_User', windowId: null });
    }

    var title = (_currentClient === 'system' ? 'System' : 'GardenWorld') + ' — Data Landscape';
    _heatmapHitRegions = ADCharts.drawTreemap(canvas, items, title);

    // Tap to drill
    canvas.addEventListener('pointerup', function (e) {
      var rect = canvas.getBoundingClientRect();
      var scaleX = canvas.width / rect.width;
      var scaleY = canvas.height / rect.height;
      var cx = (e.clientX - rect.left) * scaleX;
      var cy = (e.clientY - rect.top) * scaleY;
      for (var i = 0; i < _heatmapHitRegions.length; i++) {
        var r = _heatmapHitRegions[i];
        if (cx >= r.x && cx <= r.x + r.w && cy >= r.y && cy <= r.y + r.h) {
          console.log('§AD_UI heatmapTap label=' + r.item.label);
          if (r.item.windowId) {
            openWindow(r.item.windowId);
          } else {
            _drillToTable(r.item.tableName);
          }
          break;
        }
      }
    });

    console.log('§AD_UI heatmap home items=' + items.length + ' client=' + _currentClient);
  }

  function _drawWindowHeatmap(canvas) {
    if (!_currentWindow || !_currentRecords.length) return;
    var tab = _currentWindow.tabs[_currentTabIdx];
    var completeness = ADData.getFieldCompleteness(_currentRecords, tab.fields);
    if (!completeness.length) return;

    ADCharts.drawCompleteness(canvas, completeness,
      _currentWindow.name + ' — Field Completeness (' + _currentRecords.length + ' records)');

    console.log('§AD_UI heatmap window=' + _currentWindow.name + ' fields=' + completeness.length);
  }

  function _drillToTable(tableName) {
    // Find a window whose header tab uses this table
    try {
      var r = _db.exec(
        'SELECT w.AD_Window_ID FROM AD_Window w ' +
        'JOIN AD_Tab t ON w.AD_Window_ID = t.AD_Window_ID ' +
        'JOIN AD_Table tbl ON t.AD_Table_ID = tbl.AD_Table_ID ' +
        'WHERE tbl.TableName = ? AND t.TabLevel = 0 AND w.IsActive = \'Y\' LIMIT 1',
        [tableName]);
      if (r.length && r[0].values.length) {
        openWindow(Number(r[0].values[0][0]));
      } else {
        _showToast(tableName + ': no window found');
      }
    } catch (e) {
      _showToast('Drill failed: ' + e.message);
    }
  }

  var _systemWindowSet = {};

  function _buildWindowSets() {
    // Find all windows whose header tab points to a table with rows
    GW_WINDOW_SET = {};
    _systemWindowSet = {};
    try {
      var r = _db.exec(
        'SELECT DISTINCT w.AD_Window_ID, tbl.TableName ' +
        'FROM AD_Window w JOIN AD_Tab t ON w.AD_Window_ID = t.AD_Window_ID ' +
        'JOIN AD_Table tbl ON t.AD_Table_ID = tbl.AD_Table_ID ' +
        'WHERE w.IsActive = \'Y\' AND t.IsActive = \'Y\' AND t.TabLevel = 0'
      );
      if (!r.length) return;
      for (var i = 0; i < r[0].values.length; i++) {
        var wid = r[0].values[i][0];
        var tbl = r[0].values[i][1];
        try {
          var cnt = _db.exec('SELECT COUNT(*) FROM [' + tbl + ']');
          if (cnt.length && Number(cnt[0].values[0][0]) > 0) {
            // AD_ tables = system, others = GardenWorld
            if (tbl.indexOf('AD_') === 0) {
              _systemWindowSet[wid] = true;
            } else {
              GW_WINDOW_SET[wid] = true;
            }
          }
        } catch (e) { /* table doesn't exist */ }
      }
    } catch (e) {
      console.log('§AD_UI _buildWindowSets error: ' + e.message);
    }
    console.log('§AD_UI windowSets system=' + Object.keys(_systemWindowSet).length +
                ' gw=' + Object.keys(GW_WINDOW_SET).length);
  }

  function _hasMatchingLeaf(nodes, windowSet) {
    for (var i = 0; i < nodes.length; i++) {
      if (nodes[i].isSummary) {
        if (_hasMatchingLeaf(nodes[i].children, windowSet)) return true;
      } else if (nodes[i].action === 'W' && nodes[i].windowId && windowSet[nodes[i].windowId]) {
        return true;
      }
    }
    return false;
  }

  function _renderMenuNodes(parentEl, nodes, windowSet) {
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      if (node.isSummary) {
        // Skip folders with no matching children
        if (windowSet && !_hasMatchingLeaf(node.children, windowSet)) continue;

        var folder = document.createElement('div');
        folder.dataset.folder = '1';
        folder.dataset.menuItem = '1';
        folder.dataset.menuName = node.name;

        var header = document.createElement('div');
        header.style.cssText = 'padding:12px 14px;cursor:pointer;display:flex;' +
          'align-items:center;gap:10px;min-height:48px;border-bottom:1px solid rgba(255,255,255,0.04);' +
          'transition:background 0.15s;';
        header.onpointerenter = function() { this.style.background = 'rgba(255,255,255,0.03)'; };
        header.onpointerleave = function() { this.style.background = 'none'; };
        header.innerHTML = '<span class="folder-arrow" style="color:#666;font-size:10px;' +
          'transition:transform 0.2s">\u25B6</span>' +
          '<span style="color:#bbb;font-size:14px;font-weight:500">' + _esc(node.name) + '</span>';

        var children = document.createElement('div');
        children.className = 'folder-children';
        children.style.cssText = 'display:none;padding-left:20px;';
        _renderMenuNodes(children, node.children, windowSet);

        header.addEventListener('pointerup', (function (ch, hd) {
          return function () {
            var open = ch.style.display !== 'none';
            ch.style.display = open ? 'none' : 'block';
            hd.querySelector('.folder-arrow').textContent = open ? '\u25B6' : '\u25BC';
          };
        })(children, header));

        folder.appendChild(header);
        folder.appendChild(children);
        parentEl.appendChild(folder);
      } else if (node.action === 'W' && node.windowId) {
        // Skip windows without data if filtering
        if (windowSet && !windowSet[node.windowId]) continue;

        var leaf = document.createElement('div');
        leaf.dataset.menuItem = '1';
        leaf.dataset.menuName = node.name;
        leaf.style.cssText = 'padding:12px 14px 12px 20px;cursor:pointer;' +
          'display:flex;align-items:center;gap:10px;min-height:48px;' +
          'border-bottom:1px solid rgba(255,255,255,0.03);transition:background 0.15s;';
        leaf.onpointerenter = function() { this.style.background = 'rgba(255,255,255,0.04)'; };
        leaf.onpointerleave = function() { this.style.background = 'none'; };
        var dotColour = (_currentClient === 'system') ? '#6c9fff' : '#ff9f43';
        leaf.innerHTML = '<span style="width:6px;height:6px;border-radius:50%;' +
          'background:' + dotColour + ';flex-shrink:0"></span>' +
          '<span style="color:#ddd;font-size:14px;flex:1">' + _esc(node.name) + '</span>';
        leaf.dataset.windowId = node.windowId;
        leaf.addEventListener('pointerup', function () {
          openWindow(Number(this.dataset.windowId));
        });
        parentEl.appendChild(leaf);
      }
    }
  }

  // ── §4. Window screen (List + Card) ────────────────────────────────

  function openWindow(windowId) {
    // Destroy graph animation when leaving home
    if (typeof ADGraph !== 'undefined' && _currentScreen === 'home') {
      ADGraph.destroy();
    }
    console.log('§AD_UI openWindow id=' + windowId);
    var win = ADParser.getWindow(_db, windowId);
    if (!win) {
      console.log('§AD_UI openWindow NOT FOUND id=' + windowId);
      return;
    }

    _currentWindow = win;
    _currentTabIdx = 0;
    _currentScreen = 'window';
    _parentRecord = null;

    // Save to recent
    _addRecent(win.id, win.name);
    _renderBottomNav();

    // Load records for header tab
    _loadTabRecords();
    _renderWindow();

    console.log('§AD_UI openWindow name=' + win.name + ' tabs=' + win.tabs.length);
  }

  function _loadTabRecords() {
    if (!_currentWindow || !_currentWindow.tabs.length) {
      _currentRecords = [];
      return;
    }
    var tab = _currentWindow.tabs[_currentTabIdx];
    var where = null;

    // Master-detail: if tabLevel > 0 and parent exists, filter by FK
    if (tab.tabLevel > 0 && _parentRecord) {
      var parentTab = _currentWindow.tabs[0];
      var parentKey = parentTab.tableName + '_ID';
      if (_parentRecord[parentKey] !== undefined) {
        where = parentKey + ' = ' + _parentRecord[parentKey];
      }
    }
    if (tab.whereClause) {
      where = where ? (where + ' AND ' + tab.whereClause) : tab.whereClause;
    }

    _currentRecords = ADData.readRecords(_db, tab.tableName, where, tab.orderByClause || null);
    _currentRecordIdx = 0;
  }

  function _renderWindow() {
    if (!_currentWindow) return;
    _contentEl.innerHTML = '';
    var win = _currentWindow;
    var tab = win.tabs[_currentTabIdx];

    // §15 App bar: back + title + help
    var recName = '';
    if (_currentRecords.length > 0) {
      var rec = _currentRecords[_currentRecordIdx];
      var identField = _findIdentifier(tab);
      if (identField && rec[identField]) recName = rec[identField];
    }
    _breadcrumbEl.innerHTML = '';
    _breadcrumbEl.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:10;' +
      'background:rgba(18,18,24,0.92);backdrop-filter:blur(12px);' +
      '-webkit-backdrop-filter:blur(12px);border-bottom:1px solid rgba(255,255,255,0.06);' +
      'padding:0 8px;min-height:48px;display:flex;align-items:center;gap:4px;';

    // Back arrow
    var backBtn = document.createElement('button');
    backBtn.innerHTML = '\u2190';
    backBtn.style.cssText = 'background:none;border:none;color:#6c9fff;font-size:20px;' +
      'cursor:pointer;padding:8px;min-width:44px;min-height:44px;';
    backBtn.addEventListener('pointerup', function () { showMenu(); });
    _breadcrumbEl.appendChild(backBtn);

    // Title: window name + record name
    var titleEl = document.createElement('div');
    titleEl.style.cssText = 'flex:1;overflow:hidden;';
    titleEl.innerHTML = '<div style="color:#eee;font-size:14px;font-weight:600;' +
      'white-space:nowrap;overflow:hidden;text-overflow:ellipsis">' + _esc(win.name) + '</div>' +
      (recName ? '<div style="color:#888;font-size:11px;white-space:nowrap;overflow:hidden;' +
      'text-overflow:ellipsis">' + _esc(recName) + '</div>' : '');
    _breadcrumbEl.appendChild(titleEl);

    // Help button
    var helpBtn = document.createElement('button');
    helpBtn.textContent = '?';
    helpBtn.style.cssText = 'background:none;border:1px solid #4fc3f7;color:#4fc3f7;' +
      'font-size:13px;cursor:pointer;padding:4px 10px;border-radius:6px;' +
      'min-width:32px;min-height:32px;';
    helpBtn.addEventListener('pointerup', function () { _toggleHelp(); });
    _breadcrumbEl.appendChild(helpBtn);

    // Tab rail (§4 step 2)
    var rail = document.createElement('div');
    rail.style.cssText = 'display:flex;overflow-x:auto;border-bottom:1px solid rgba(255,255,255,0.06);' +
      'margin-bottom:16px;-webkit-overflow-scrolling:touch;gap:2px;';
    for (var t = 0; t < win.tabs.length; t++) {
      var tabBtn = document.createElement('button');
      tabBtn.textContent = win.tabs[t].name;
      tabBtn.dataset.tabIdx = t;
      var isActive = (t === _currentTabIdx);
      var tabColour = (_currentClient === 'system') ? '#6c9fff' : '#ff9f43';
      tabBtn.style.cssText = 'background:' + (isActive ? 'rgba(108,159,255,0.1)' : 'none') +
        ';border:none;border-bottom:2px solid ' +
        (isActive ? tabColour : 'transparent') + ';color:' +
        (isActive ? tabColour : '#666') + ';padding:10px 16px;font-size:13px;font-weight:' +
        (isActive ? '600' : '400') + ';cursor:pointer;white-space:nowrap;min-height:44px;' +
        'border-radius:8px 8px 0 0;transition:all 0.15s;';
      tabBtn.addEventListener('pointerup', function () {
        var idx = Number(this.dataset.tabIdx);
        _switchTab(idx);
      });
      rail.appendChild(tabBtn);
    }
    _contentEl.appendChild(rail);

    // §18 CRUD toolbar — compact navigation + actions
    var toolbar = document.createElement('div');
    toolbar.style.cssText = 'display:flex;align-items:center;gap:4px;margin-bottom:12px;' +
      'padding:4px;background:#1a1a24;border-radius:10px;';

    var tbPrev = document.createElement('button');
    tbPrev.innerHTML = '\u25C0';
    tbPrev.title = 'Previous (Arrow Left)';
    tbPrev.disabled = _currentRecordIdx <= 0;
    tbPrev.style.cssText = _crudBtnStyle(tbPrev.disabled);
    tbPrev.addEventListener('pointerup', function () { _navRecord(-1); });

    var tbCounter = document.createElement('span');
    tbCounter.style.cssText = 'flex:1;text-align:center;color:#888;font-size:12px;' +
      'font-variant-numeric:tabular-nums;';
    tbCounter.textContent = _currentRecords.length > 0
      ? (_currentRecordIdx + 1) + ' / ' + _currentRecords.length : '0';

    var tbNext = document.createElement('button');
    tbNext.innerHTML = '\u25B6';
    tbNext.title = 'Next (Arrow Right)';
    tbNext.disabled = _currentRecordIdx >= _currentRecords.length - 1;
    tbNext.style.cssText = _crudBtnStyle(tbNext.disabled);
    tbNext.addEventListener('pointerup', function () { _navRecord(1); });

    var tbNew = document.createElement('button');
    tbNew.innerHTML = '+';
    tbNew.title = 'New record';
    tbNew.style.cssText = _crudBtnStyle(false, '#54d9a8');
    tbNew.addEventListener('pointerup', function () { _createNewRecord(); });

    var tbDel = document.createElement('button');
    tbDel.innerHTML = '\u2715';
    tbDel.title = 'Delete record';
    tbDel.disabled = _currentRecords.length === 0;
    tbDel.style.cssText = _crudBtnStyle(tbDel.disabled, '#f44336');
    tbDel.addEventListener('pointerup', function () { _deleteCurrentRecord(); });

    toolbar.appendChild(tbPrev);
    toolbar.appendChild(tbCounter);
    toolbar.appendChild(tbNext);
    toolbar.appendChild(tbNew);
    toolbar.appendChild(tbDel);
    _contentEl.appendChild(toolbar);

    // Multi-panel layout: master top, detail panels below side-by-side
    var panelContainer = document.createElement('div');
    panelContainer.style.cssText = 'display:flex;flex-direction:column;gap:12px;';

    // Main record card (top — full width)
    var mainPanel = document.createElement('div');

    if (_currentRecords.length === 0) {
      var empty = document.createElement('div');
      empty.style.cssText = 'text-align:center;color:#888;padding:40px;font-size:14px;';
      empty.textContent = 'No records in ' + tab.tableName;
      mainPanel.appendChild(empty);
    } else {
      _renderRecordCard(tab, mainPanel);
    }
    panelContainer.appendChild(mainPanel);

    // Detail sub-tab panels — only tabs with data, side by side
    if (_currentRecords.length > 0 && win.tabs.length > 1 && tab.tabLevel === 0) {
      var parentKey = tab.tableName + '_ID';
      var parentId = _currentRecords[_currentRecordIdx][parentKey];
      var detailContainer = document.createElement('div');
      detailContainer.style.cssText = 'display:flex;gap:10px;flex-wrap:wrap;';
      var detailCount = 0;

      for (var dt = 1; dt < win.tabs.length; dt++) {
        var detailTab = win.tabs[dt];
        if (detailTab.tabLevel !== 1) continue; // only direct children
        var detailWhere = parentKey + ' = ' + parentId;
        var detailRecords = ADData.readRecords(_db, detailTab.tableName, detailWhere);
        if (detailRecords.length === 0) continue; // skip empty tabs

        detailCount++;
        var detailPanel = document.createElement('div');
        detailPanel.dataset.detailTab = dt;
        detailPanel.style.cssText = 'flex:1;min-width:220px;max-height:40vh;overflow-y:auto;' +
          'background:linear-gradient(135deg,#1a1a24,#222230);border:1px solid rgba(255,255,255,0.06);' +
          'border-radius:14px;padding:12px;';

        var detailHeader = document.createElement('div');
        detailHeader.style.cssText = 'color:#ff9f43;font-size:13px;font-weight:600;' +
          'margin-bottom:8px;padding-bottom:6px;border-bottom:1px solid rgba(255,255,255,0.06);' +
          'display:flex;justify-content:space-between;align-items:center;';
        detailHeader.innerHTML = _esc(detailTab.name) +
          '<span style="color:#666;font-size:11px;font-weight:400">' + detailRecords.length + '</span>';
        detailPanel.appendChild(detailHeader);

        var detailIdent = _findIdentifier(detailTab);
        for (var di = 0; di < detailRecords.length && di < 10; di++) {
          var dRec = detailRecords[di];
          var dCard = document.createElement('div');
          dCard.style.cssText = 'padding:8px 10px;border-bottom:1px solid rgba(255,255,255,0.04);' +
            'font-size:13px;cursor:pointer;transition:background 0.15s;min-height:36px;';
          dCard.onpointerenter = function() { this.style.background = 'rgba(255,255,255,0.04)'; };
          dCard.onpointerleave = function() { this.style.background = ''; };

          var dName = detailIdent ? (dRec[detailIdent] || '(unnamed)') : ('Record ' + (di + 1));
          var dFields = [];
          for (var dfi = 0; dfi < detailTab.fields.length && dFields.length < 2; dfi++) {
            var df = detailTab.fields[dfi];
            if (df.isKey || !df.isDisplayed || df.columnName === detailIdent) continue;
            var dv = dRec[df.columnName];
            if (dv !== null && dv !== undefined && dv !== '') {
              if ((df.referenceType === 'tableDirect' || df.referenceType === 'table') && dv) {
                var fkN = ADData.resolveFK(_db, df.columnName, dv);
                if (fkN) dv = fkN;
              }
              dFields.push(df.name + ': ' + String(dv).substring(0, 20));
            }
          }

          dCard.innerHTML = '<div style="color:#eee;font-weight:500">' + _esc(dName) + '</div>' +
            (dFields.length ? '<div style="color:#666;font-size:11px;margin-top:2px">' +
            _esc(dFields.join(' \u00b7 ')) + '</div>' : '');
          dCard.dataset.tabIdx = dt;
          dCard.addEventListener('pointerup', function () {
            _switchTab(Number(this.dataset.tabIdx));
          });
          detailPanel.appendChild(dCard);
        }
        if (detailRecords.length > 10) {
          var moreLink = document.createElement('div');
          moreLink.style.cssText = 'color:#4fc3f7;font-size:12px;padding:6px;text-align:center;cursor:pointer;';
          moreLink.textContent = '+ ' + (detailRecords.length - 10) + ' more\u2026';
          moreLink.dataset.tabIdx = dt;
          moreLink.addEventListener('pointerup', function () {
            _switchTab(Number(this.dataset.tabIdx));
          });
          detailPanel.appendChild(moreLink);
        }

        detailContainer.appendChild(detailPanel);
        console.log('§AD_UI detailPanel tab=' + detailTab.name + ' records=' + detailRecords.length);
      }

      if (detailCount > 0) panelContainer.appendChild(detailContainer);
    }

    _contentEl.appendChild(panelContainer);

    // Help panel auto-refresh on record change
    if (_helpVisible) _updateHelpContent();
  }

  function _renderRecordCard(tab, parentEl) {
    var rec = _currentRecords[_currentRecordIdx];
    if (!rec) return;
    var target = parentEl || _contentEl;

    var card = document.createElement('div');
    card.style.cssText = 'background:linear-gradient(135deg,#1e1e2a,#252535);' +
      'border:1px solid rgba(255,255,255,0.06);border-radius:16px;' +
      'padding:20px;margin:0 4px;animation:fadeIn 0.3s ease;' +
      'box-shadow:0 4px 24px rgba(0,0,0,0.3);';

    var fields = tab.fields;
    for (var i = 0; i < fields.length; i++) {
      var f = fields[i];
      // Hidden fields: isKey, not displayed
      if (f.isKey || !f.isDisplayed) continue;

      // DisplayLogic evaluation
      if (f.displayLogic && !ADParser.evaluateDisplayLogic(f.displayLogic, rec)) continue;

      var val = rec[f.columnName];
      var isEmpty = (val === null || val === undefined || val === '');
      var row = document.createElement('div');
      row.style.cssText = 'display:flex;justify-content:space-between;align-items:center;' +
        'padding:' + (isEmpty ? '4px' : '10px') + ' 0;border-bottom:1px solid rgba(255,255,255,0.04);' +
        'min-height:' + (isEmpty ? '28px' : '48px') + ';' +
        (isEmpty ? 'opacity:0.5;' : '');

      // Label (tap for help)
      var label = document.createElement('span');
      label.style.cssText = 'color:#888;font-size:12px;flex:0 0 40%;cursor:pointer;';
      label.textContent = f.name;
      label.dataset.fieldCol = f.columnName;
      label.addEventListener('pointerup', function (ev) {
        ev.stopPropagation();
        _showFieldHelp(this.dataset.fieldCol);
      });
      row.appendChild(label);

      // Value / input
      var valEl = _renderFieldValue(f, val, rec);
      valEl.style.flex = '1';
      row.appendChild(valEl);

      // Mandatory indicator
      if (f.isMandatory && (val === null || val === undefined || val === '')) {
        row.style.borderLeft = '3px solid #f44336';
        row.style.paddingLeft = '6px';
      }

      card.appendChild(row);
    }

    // Swipe gesture on card
    _attachSwipe(card);
    target.appendChild(card);
  }

  function _renderFieldValue(field, value, record) {
    var el;
    var displayVal = value !== null && value !== undefined ? String(value) : '';

    if (field.isReadOnly) {
      el = document.createElement('span');
      el.style.cssText = 'color:#eee;font-size:14px;text-align:right;';
      el.textContent = displayVal;
      return el;
    }

    var type = field.referenceType;

    if (type === 'list') {
      el = document.createElement('select');
      el.style.cssText = 'background:#333;color:#eee;border:1px solid #555;' +
        'border-radius:6px;padding:6px;font-size:13px;width:100%;min-height:44px;';
      // Load options
      var ref = ADParser.resolveReference(_db, field.referenceId);
      if (ref.type === 'list') {
        var opt0 = document.createElement('option');
        opt0.value = '';
        opt0.textContent = '—';
        el.appendChild(opt0);
        for (var o = 0; o < ref.options.length; o++) {
          var opt = document.createElement('option');
          opt.value = ref.options[o].value;
          opt.textContent = ref.options[o].name;
          if (ref.options[o].value === displayVal) opt.selected = true;
          el.appendChild(opt);
        }
      }
      el.dataset.col = field.columnName;
      el.addEventListener('change', _inlineEditHandler());
      return el;
    }

    if (type === 'yesno') {
      el = document.createElement('button');
      var isY = displayVal === 'Y';
      el.textContent = isY ? 'Yes' : 'No';
      el.style.cssText = 'background:' + (isY ? '#2e7d32' : '#555') + ';color:#eee;' +
        'border:none;border-radius:6px;padding:6px 14px;font-size:13px;' +
        'cursor:pointer;min-height:44px;';
      el.dataset.col = field.columnName;
      el.dataset.val = displayVal;
      el.addEventListener('pointerup', function () {
        var newVal = this.dataset.val === 'Y' ? 'N' : 'Y';
        this.dataset.val = newVal;
        this.textContent = newVal === 'Y' ? 'Yes' : 'No';
        this.style.background = newVal === 'Y' ? '#2e7d32' : '#555';
        _saveField(this.dataset.col, newVal);
      });
      return el;
    }

    if (type === 'date' || type === 'datetime') {
      el = document.createElement('input');
      el.type = 'date';
      el.value = displayVal ? displayVal.substring(0, 10) : '';
      el.style.cssText = 'background:#333;color:#eee;border:1px solid #555;' +
        'border-radius:6px;padding:6px;font-size:13px;width:100%;min-height:44px;';
      el.dataset.col = field.columnName;
      el.addEventListener('change', _inlineEditHandler());
      return el;
    }

    if (type === 'amount' || type === 'number' || type === 'integer' || type === 'quantity') {
      el = document.createElement('input');
      el.type = 'number';
      el.value = displayVal;
      el.style.cssText = 'background:#333;color:#eee;border:1px solid #555;' +
        'border-radius:6px;padding:6px;font-size:13px;width:100%;text-align:right;' +
        'min-height:44px;';
      el.dataset.col = field.columnName;
      el.addEventListener('change', _inlineEditHandler());
      return el;
    }

    // §14: FK resolution for tableDirect / table fields
    if ((type === 'tableDirect' || type === 'table' || type === 'search') && displayVal) {
      var fkName = ADData.resolveFK(_db, field.columnName, value);
      if (fkName) {
        el = document.createElement('span');
        el.style.cssText = 'color:#4fc3f7;font-size:14px;text-align:right;cursor:pointer;';
        el.textContent = fkName;
        el.title = field.columnName + ' = ' + displayVal;
        el.dataset.col = field.columnName;
        el.dataset.fkId = displayVal;
        el.addEventListener('pointerup', function () {
          // Tap FK → drill to that record's window
          _drillToTable(this.dataset.col.replace(/_ID$/, ''));
        });
        return el;
      }
    }

    // Default: text input (string, text)
    el = document.createElement('input');
    el.type = 'text';
    el.value = displayVal;
    el.style.cssText = 'background:#333;color:#eee;border:1px solid #555;' +
      'border-radius:6px;padding:6px;font-size:13px;width:100%;min-height:44px;';
    el.dataset.col = field.columnName;
    el.addEventListener('change', _inlineEditHandler());
    return el;
  }

  function _inlineEditHandler() {
    return function () {
      _saveField(this.dataset.col, this.value);
    };
  }

  function _saveField(colName, value) {
    if (!_currentWindow || !_currentRecords.length) return;
    var tab = _currentWindow.tabs[_currentTabIdx];
    var rec = _currentRecords[_currentRecordIdx];
    rec[colName] = value;
    try {
      ADData.saveRecord(_db, tab.tableName, rec, tab.fields);
      console.log('§AD_UI saveField col=' + colName + ' val=' + value);
    } catch (e) {
      console.log('§AD_UI saveField ERROR col=' + colName + ' err=' + e.message);
      _showToast('Save failed: ' + e.message);
    }
  }

  // ── CRUD toolbar helpers ────────────────────────────────────────────

  function _crudBtnStyle(disabled, colour) {
    var c = colour || '#6c9fff';
    return 'background:none;border:1px solid ' + (disabled ? '#333' : c) +
      ';color:' + (disabled ? '#444' : c) + ';font-size:14px;cursor:' +
      (disabled ? 'default' : 'pointer') + ';padding:6px 12px;border-radius:8px;' +
      'min-width:44px;min-height:36px;font-weight:bold;transition:all 0.15s;' +
      'opacity:' + (disabled ? '0.4' : '1') + ';';
  }

  function _navRecord(dir) {
    var newIdx = _currentRecordIdx + dir;
    if (newIdx < 0 || newIdx >= _currentRecords.length) return;
    _currentRecordIdx = newIdx;
    console.log('§AD_UI navRecord idx=' + _currentRecordIdx + ' total=' + _currentRecords.length);
    _renderWindow();
  }

  function _deleteCurrentRecord() {
    if (!_currentWindow || !_currentRecords.length) return;
    var tab = _currentWindow.tabs[_currentTabIdx];
    var rec = _currentRecords[_currentRecordIdx];
    var keyCol = tab.tableName + '_ID';
    var keyVal = rec[keyCol];

    if (!confirm('Delete this record? (' + keyCol + '=' + keyVal + ')')) return;

    try {
      ADData.deleteRecord(_db, tab.tableName, keyCol, keyVal);
      console.log('§AD_UI deleteRecord table=' + tab.tableName + ' id=' + keyVal);
      _loadTabRecords();
      if (_currentRecordIdx >= _currentRecords.length) {
        _currentRecordIdx = Math.max(0, _currentRecords.length - 1);
      }
      _renderWindow();
    } catch (e) {
      _showToast('Delete failed: ' + e.message);
    }
  }

  // ── §18. Arrow key navigation ─────────────────────────────────────

  // ── Client switching ────────────────────────────────────────────

  var _clients = ['system', 'gardenworld'];

  function _switchClient(direction) {
    var idx = _clients.indexOf(_currentClient);
    var next = (idx + direction + _clients.length) % _clients.length;
    if (_clients[next] === _currentClient) return;
    _currentClient = _clients[next];
    // Toast showing new client name
    _showClientToast(_currentClient);
    showMenu();
    console.log('§AD_UI switchClient client=' + _currentClient + ' dir=' + direction);
  }

  function _showClientToast(client) {
    var label = client === 'system' ? 'System' : 'GardenWorld';
    var colour = client === 'system' ? '#6c9fff' : '#ff9f43';
    var toast = document.createElement('div');
    toast.style.cssText = 'position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);' +
      'z-index:80;padding:16px 32px;border-radius:14px;font-size:18px;font-weight:700;' +
      'color:#fff;letter-spacing:1px;pointer-events:none;' +
      'background:rgba(12,12,18,0.6);backdrop-filter:blur(16px);' +
      '-webkit-backdrop-filter:blur(16px);' +
      'border:1px solid ' + colour + ';' +
      'box-shadow:0 0 24px ' + colour + '33;' +
      'animation:fadeInOut 1s ease forwards;';
    toast.textContent = label;
    // Inject animation if not already present
    if (!document.getElementById('client-toast-style')) {
      var s = document.createElement('style');
      s.id = 'client-toast-style';
      s.textContent = '@keyframes fadeInOut{0%{opacity:0;transform:translate(-50%,-50%) scale(0.9)}' +
        '20%{opacity:1;transform:translate(-50%,-50%) scale(1)}' +
        '80%{opacity:1;transform:translate(-50%,-50%) scale(1)}' +
        '100%{opacity:0;transform:translate(-50%,-50%) scale(0.95)}}';
      document.head.appendChild(s);
    }
    document.body.appendChild(toast);
    setTimeout(function () { if (toast.parentNode) toast.parentNode.removeChild(toast); }, 1000);
  }

  // ── Edge swipe for mobile client switching ─────────────────────

  function _initEdgeSwipe() {
    if (typeof document === 'undefined') return;
    var EDGE = 30;    // px from screen edge to trigger
    var MIN_DRAG = 80; // px minimum horizontal drag
    var _swipeState = null;

    document.addEventListener('pointerdown', function (e) {
      var x = e.clientX;
      var w = window.innerWidth;
      if (x < EDGE || x > w - EDGE) {
        _swipeState = { startX: x, startY: e.clientY, edge: x < EDGE ? 'left' : 'right' };
      }
    });

    document.addEventListener('pointermove', function (e) {
      if (!_swipeState) return;
      // Cancel if vertical movement is dominant (scroll)
      var dy = Math.abs(e.clientY - _swipeState.startY);
      var dx = Math.abs(e.clientX - _swipeState.startX);
      if (dy > dx * 1.5) { _swipeState = null; }
    });

    document.addEventListener('pointerup', function (e) {
      if (!_swipeState) return;
      var dx = e.clientX - _swipeState.startX;
      var absDx = Math.abs(dx);
      var absDy = Math.abs(e.clientY - _swipeState.startY);

      if (absDx > MIN_DRAG && absDx > absDy * 1.5) {
        // Valid horizontal swipe from edge
        if (_swipeState.edge === 'left' && dx > 0) {
          // Left edge, swiped right → next client
          _switchClient(1);
        } else if (_swipeState.edge === 'right' && dx < 0) {
          // Right edge, swiped left → previous client
          _switchClient(-1);
        }
      }
      _swipeState = null;
    });

    console.log('§AD_UI edgeSwipe init edge=' + EDGE + 'px min=' + MIN_DRAG + 'px');
  }

  function _initKeyboard() {
    if (typeof document === 'undefined') return;
    document.addEventListener('keydown', function (e) {
      // Alt+S — toggle search overlay (works on any screen, even in inputs)
      if (e.altKey && (e.key === 's' || e.key === 'S')) {
        e.preventDefault();
        _toggleSearchOverlay();
        return;
      }

      // Don't capture if user is typing
      var tag = e.target.tagName;
      if (tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA') return;

      // Arrow keys on home screen → switch client
      if (_currentScreen === 'home') {
        if (e.key === 'ArrowLeft') {
          e.preventDefault();
          _switchClient(-1);
        } else if (e.key === 'ArrowRight') {
          e.preventDefault();
          _switchClient(1);
        }
        return;
      }

      if (_currentScreen !== 'window') return;

      if (e.key === 'ArrowLeft') {
        e.preventDefault();
        _navRecord(-1);
      } else if (e.key === 'ArrowRight') {
        e.preventDefault();
        _navRecord(1);
      } else if (e.key === 'ArrowUp' && _currentTabIdx > 0) {
        e.preventDefault();
        _switchTab(_currentTabIdx - 1);
      } else if (e.key === 'ArrowDown' && _currentWindow &&
                 _currentTabIdx < _currentWindow.tabs.length - 1) {
        e.preventDefault();
        _switchTab(_currentTabIdx + 1);
      }
    });
    console.log('§AD_UI keyboard init');
  }

  // ── §5. Master-detail navigation ──────────────────────────────────

  function _switchTab(idx) {
    if (!_currentWindow) return;
    var tab = _currentWindow.tabs[idx];
    console.log('§AD_UI switchTab idx=' + idx + ' name=' + tab.name + ' level=' + tab.tabLevel);

    if (tab.tabLevel > 0 && _currentRecords.length > 0) {
      _parentRecord = _currentRecords[_currentRecordIdx];
    }
    _currentTabIdx = idx;
    _loadTabRecords();
    _renderWindow();
  }

  // ── Swipe gestures ────────────────────────────────────────────────

  function _attachSwipe(el) {
    var startX = 0, startY = 0;
    el.addEventListener('pointerdown', function (e) {
      startX = e.clientX;
      startY = e.clientY;
    });
    el.addEventListener('pointerup', function (e) {
      var dx = e.clientX - startX;
      var dy = e.clientY - startY;
      if (Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy)) {
        if (dx < 0 && _currentRecordIdx < _currentRecords.length - 1) {
          _currentRecordIdx++;
          _renderWindow();
        } else if (dx > 0 && _currentRecordIdx > 0) {
          _currentRecordIdx--;
          _renderWindow();
        }
      } else if (dy < -60 && Math.abs(dy) > Math.abs(dx)) {
        // Swipe up: show detail tabs
        if (_currentWindow.tabs.length > 1) {
          var nextLevel = _currentTabIdx + 1;
          if (nextLevel < _currentWindow.tabs.length) _switchTab(nextLevel);
        }
      } else if (dy > 60 && Math.abs(dy) > Math.abs(dx)) {
        // Swipe down: back to parent
        if (_currentTabIdx > 0) _switchTab(0);
      }
    });
  }

  // ── Nav actions ───────────────────────────────────────────────────

  function _showRecordList() {
    if (!_currentWindow) { showMenu(); return; }
    _loadTabRecords();
    _contentEl.innerHTML = '';
    var tab = _currentWindow.tabs[_currentTabIdx];

    _breadcrumbEl.innerHTML = '';
    var listBack = document.createElement('button');
    listBack.innerHTML = '\u2190';
    listBack.style.cssText = 'background:none;border:none;color:#6c9fff;font-size:20px;' +
      'cursor:pointer;padding:8px;min-width:44px;min-height:44px;';
    listBack.addEventListener('pointerup', function () { _renderWindow(); });
    _breadcrumbEl.appendChild(listBack);
    var listTitle = document.createElement('span');
    listTitle.style.cssText = 'color:#eee;font-size:15px;font-weight:bold';
    listTitle.textContent = _currentWindow.name + ' \u2014 List';
    _breadcrumbEl.appendChild(listTitle);

    for (var i = 0; i < _currentRecords.length; i++) {
      var rec = _currentRecords[i];
      var ident = _findIdentifier(tab);
      var name = ident ? (rec[ident] || '(no name)') : ('Record ' + (i + 1));

      var item = document.createElement('div');
      item.style.cssText = 'padding:12px;border-bottom:1px solid #333;cursor:pointer;' +
        'min-height:44px;display:flex;align-items:center;color:#eee;font-size:14px;';
      item.textContent = name;
      item.dataset.idx = i;
      item.addEventListener('pointerup', function () {
        _currentRecordIdx = Number(this.dataset.idx);
        _renderWindow();
      });
      _contentEl.appendChild(item);
    }
    console.log('§AD_UI showList count=' + _currentRecords.length);
  }

  function _tableExists(tableName) {
    try {
      _db.exec('SELECT 1 FROM ' + tableName + ' LIMIT 0');
      return true;
    } catch (e) { return false; }
  }

  function _createNewRecord() {
    if (!_currentWindow || !_currentWindow.tabs.length) return;
    var tab = _currentWindow.tabs[_currentTabIdx];
    if (!_tableExists(tab.tableName)) {
      console.log('§AD_UI createNew SKIPPED — table missing: ' + tab.tableName);
      _showToast('Table ' + tab.tableName + ' has no data yet (AD metadata only)');
      return;
    }
    try {
      var rec = {};
      for (var i = 0; i < tab.fields.length; i++) {
        var f = tab.fields[i];
        if (f.defaultValue) rec[f.columnName] = f.defaultValue;
      }
      var result = ADData.saveRecord(_db, tab.tableName, rec, tab.fields);
      console.log('§AD_UI createNew table=' + tab.tableName + ' id=' + result.id);
      _loadTabRecords();
      _currentRecordIdx = _currentRecords.length - 1;
      _renderWindow();
    } catch (e) {
      console.log('§AD_UI createNew ERROR table=' + tab.tableName + ' err=' + e.message);
      _showToast('Cannot create: ' + e.message);
    }
  }

  function _showCharts() {
    if (!_chartOverlay) {
      _chartOverlay = document.createElement('div');
      _chartOverlay.id = 'chart-overlay';
      document.body.appendChild(_chartOverlay);
    }

    if (_currentWindow && _currentWindow.tabs.length) {
      // Window open → show prebuilt charts + field completeness heatmap
      var tab = _currentWindow.tabs[_currentTabIdx];
      ADCharts.renderOverlay(_chartOverlay, _db, tab.tableName);

      // Append field completeness heatmap
      if (_currentRecords.length > 0) {
        var heatCanvas = document.createElement('canvas');
        heatCanvas.width = 480;
        heatCanvas.height = 260;
        heatCanvas.style.cssText = 'display:block;width:100%;height:auto;margin-top:12px;';
        _chartOverlay.appendChild(heatCanvas);
        var completeness = ADData.getFieldCompleteness(_currentRecords, tab.fields);
        ADCharts.drawCompleteness(heatCanvas, completeness,
          _currentWindow.name + ' — Field Completeness (' + _currentRecords.length + ' records)');
      }
    } else {
      // Home → show table treemap
      _chartOverlay.innerHTML = '';
      _chartOverlay.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:48px;' +
        'background:rgba(20,20,20,0.97);z-index:50;overflow-y:auto;padding:16px;';
      var closeBtn = document.createElement('button');
      closeBtn.textContent = '\u2715 Close';
      closeBtn.style.cssText = 'position:absolute;top:8px;right:12px;background:none;' +
        'border:1px solid #555;color:#ccc;padding:6px 14px;border-radius:6px;' +
        'font-size:13px;cursor:pointer;min-height:44px;';
      closeBtn.addEventListener('pointerup', function () {
        _chartOverlay.style.display = 'none';
      });
      _chartOverlay.appendChild(closeBtn);

      var heatCanvas = document.createElement('canvas');
      heatCanvas.width = 480;
      heatCanvas.height = 320;
      heatCanvas.style.cssText = 'display:block;width:100%;height:auto;margin-top:40px;';
      _chartOverlay.appendChild(heatCanvas);
      _drawHomeHeatmap(heatCanvas);
      _chartOverlay.style.display = 'block';
    }
    console.log('§AD_UI showCharts window=' + (_currentWindow ? _currentWindow.name : 'home'));
  }

  function _showMore() {
    _contentEl.innerHTML = '';
    _breadcrumbEl.innerHTML = '';
    var moreBack = document.createElement('button');
    moreBack.innerHTML = '\u2190';
    moreBack.style.cssText = 'background:none;border:none;color:#6c9fff;font-size:20px;' +
      'cursor:pointer;padding:8px;min-width:44px;min-height:44px;';
    moreBack.addEventListener('pointerup', function () {
      if (_currentWindow) _renderWindow(); else showMenu();
    });
    _breadcrumbEl.appendChild(moreBack);
    var moreTitle = document.createElement('span');
    moreTitle.style.cssText = 'color:#eee;font-size:15px;font-weight:bold';
    moreTitle.textContent = '\u2699 Settings';
    _breadcrumbEl.appendChild(moreTitle);

    var items = [
      { label: 'Share Link', action: function () {
        var url = location.origin + location.pathname;
        if (_currentWindow) url += '?window=' + _currentWindow.id;
        if (navigator.clipboard) navigator.clipboard.writeText(url);
        console.log('§AD_UI share url=' + url);
      }},
      { label: 'Open in BIM', action: function () {
        if (typeof BroadcastChannel !== 'undefined') {
          var ch = new BroadcastChannel('bim_erp');
          ch.postMessage({ type: 'ERP_FOCUS_STOREY', windowId: _currentWindow ? _currentWindow.id : null });
          ch.close();
        }
      }},
      { label: 'About', action: function () {
        alert('ERP OOTB — AD-driven UI\nNo server. No iDempiere runtime.\nPowered by SQLite + WASM.');
      }}
    ];

    for (var i = 0; i < items.length; i++) {
      var btn = document.createElement('button');
      btn.textContent = items[i].label;
      btn.style.cssText = 'display:block;width:100%;text-align:left;padding:14px 16px;' +
        'background:none;border:none;border-bottom:1px solid #333;color:#eee;' +
        'font-size:14px;cursor:pointer;min-height:44px;';
      btn.addEventListener('pointerup', items[i].action);
      _contentEl.appendChild(btn);
    }
    console.log('§AD_UI showMore');
  }

  // ── BroadcastChannel §10 ──────────────────────────────────────────

  function _initBroadcast() {
    if (typeof BroadcastChannel === 'undefined') return;
    var ch = new BroadcastChannel('bim_erp');
    ch.addEventListener('message', function (e) {
      if (e.data && e.data.type === 'ERP_ELEMENT_PICKED') {
        console.log('§AD_UI broadcast ERP_ELEMENT_PICKED guid=' + e.data.guid);
        // Could focus a record by GUID — future enhancement
      }
    });
    console.log('§AD_UI broadcast channel open');
  }

  // ── Helpers ───────────────────────────────────────────────────────

  function _findIdentifier(tab) {
    for (var i = 0; i < tab.fields.length; i++) {
      if (tab.fields[i].isIdentifier) return tab.fields[i].columnName;
    }
    // Fallback: Name column
    for (var j = 0; j < tab.fields.length; j++) {
      if (tab.fields[j].columnName === 'Name') return 'Name';
    }
    return null;
  }

  function _esc(s) {
    if (!s) return '';
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
                     .replace(/"/g, '&quot;');
  }

  function _loadRecent() {
    try {
      var raw = localStorage.getItem('erp_recent_windows');
      _recentWindows = raw ? JSON.parse(raw) : [];
    } catch (e) { _recentWindows = []; }
  }

  function _addRecent(id, name) {
    _recentWindows = _recentWindows.filter(function (r) { return r.id !== id; });
    _recentWindows.unshift({ id: id, name: name });
    if (_recentWindows.length > 10) _recentWindows.length = 10;
    try { localStorage.setItem('erp_recent_windows', JSON.stringify(_recentWindows)); }
    catch (e) { /* quota */ }
  }

  // ── Help panel (iDempiere-style right panel) ───────────────────────

  function _ensureHelpPanel() {
    if (_helpPanel) return;
    _helpPanel = document.createElement('div');
    _helpPanel.id = 'help-panel';
    _helpPanel.style.cssText = 'position:fixed;top:52px;right:0;bottom:48px;width:280px;' +
      'background:#252525;border-left:1px solid #444;z-index:40;overflow-y:auto;' +
      'padding:16px;display:none;transition:transform 0.2s;';
    document.body.appendChild(_helpPanel);
  }

  function _toggleHelp() {
    _ensureHelpPanel();
    _helpVisible = !_helpVisible;
    _helpPanel.style.display = _helpVisible ? 'block' : 'none';
    if (_helpVisible) _updateHelpContent();
    console.log('§AD_UI help visible=' + _helpVisible);
  }

  function _updateHelpContent(fieldName) {
    _ensureHelpPanel();
    if (!_currentWindow) return;
    var win = _currentWindow;
    var tab = win.tabs[_currentTabIdx];
    var html = '';

    // Window help
    html += '<div style="color:#4fc3f7;font-size:14px;font-weight:bold;margin-bottom:8px">' +
      _esc(win.name) + '</div>';
    if (win.description) {
      html += '<div style="color:#ccc;font-size:13px;margin-bottom:8px">' +
        _esc(win.description) + '</div>';
    }
    if (win.help) {
      html += '<div style="color:#aaa;font-size:12px;margin-bottom:12px;' +
        'padding:8px;background:#2a2a2a;border-radius:6px;border-left:3px solid #4fc3f7">' +
        _esc(win.help) + '</div>';
    }

    // Tab help
    html += '<div style="color:#ff9800;font-size:13px;font-weight:bold;margin-bottom:6px;' +
      'border-top:1px solid #333;padding-top:10px">Tab: ' + _esc(tab.name) + '</div>';
    if (tab.description) {
      html += '<div style="color:#ccc;font-size:12px;margin-bottom:4px">' +
        _esc(tab.description) + '</div>';
    }
    if (tab.help) {
      html += '<div style="color:#aaa;font-size:12px;margin-bottom:12px;' +
        'padding:8px;background:#2a2a2a;border-radius:6px;border-left:3px solid #ff9800">' +
        _esc(tab.help) + '</div>';
    }
    html += '<div style="color:#888;font-size:11px;margin-bottom:12px">Table: ' +
      _esc(tab.tableName) + ' \u00b7 Records: ' + _currentRecords.length + '</div>';

    // Field list with descriptions
    html += '<div style="color:#4fc3f7;font-size:12px;font-weight:bold;margin-bottom:6px;' +
      'border-top:1px solid #333;padding-top:10px">Fields</div>';
    for (var i = 0; i < tab.fields.length; i++) {
      var f = tab.fields[i];
      if (f.isKey || !f.isDisplayed) continue;
      var isHighlight = fieldName && f.columnName === fieldName;
      html += '<div style="padding:4px 0;border-bottom:1px solid #2a2a2a;' +
        (isHighlight ? 'background:#333;margin:0 -8px;padding:4px 8px;border-radius:4px;' : '') + '">' +
        '<div style="color:' + (isHighlight ? '#4fc3f7' : '#ccc') + ';font-size:12px;font-weight:' +
        (isHighlight ? 'bold' : 'normal') + '">' + _esc(f.name) +
        '<span style="color:#555;font-size:10px;margin-left:6px">' + f.referenceType + '</span>' +
        (f.isMandatory ? '<span style="color:#f44336;margin-left:4px">*</span>' : '') +
        '</div>';
      if (f.description) {
        html += '<div style="color:#888;font-size:11px">' + _esc(f.description) + '</div>';
      }
      html += '</div>';
    }

    // Close button at bottom
    html += '<div style="margin-top:16px;text-align:center">' +
      '<button style="background:none;border:1px solid #555;color:#888;padding:8px 20px;' +
      'border-radius:6px;font-size:12px;cursor:pointer;min-height:44px" ' +
      'onclick="document.getElementById(\'help-panel\').style.display=\'none\'">Close</button></div>';

    _helpPanel.innerHTML = html;
  }

  // Show field help when tapping a label
  function _showFieldHelp(fieldName) {
    if (!_helpVisible) {
      _helpVisible = true;
      _ensureHelpPanel();
      _helpPanel.style.display = 'block';
    }
    _updateHelpContent(fieldName);
  }

  // ── Toast notification ────────────────────────────────────────────

  function _showToast(msg) {
    var toast = document.createElement('div');
    toast.textContent = msg;
    toast.style.cssText = 'position:fixed;bottom:60px;left:50%;transform:translateX(-50%);' +
      'background:#333;color:#ff9800;padding:10px 20px;border-radius:8px;font-size:13px;' +
      'z-index:100;border:1px solid #555;max-width:80%;text-align:center;';
    document.body.appendChild(toast);
    setTimeout(function () {
      if (toast.parentNode) toast.parentNode.removeChild(toast);
    }, 3000);
  }

  // ── Floating Search Overlay (Alt+S) ────────────────────────────────
  // Glass panel with orange-bordered inner search box.
  // Lives inside _graphContainer so it's visible during fullscreen.

  var _searchOverlay = null;
  var _searchInput = null;
  var _searchResultsEl = null;
  var _searchTimer = null;
  var _dragState = null;

  function _ensureSearchOverlay() {
    if (_searchOverlay) return;

    _searchOverlay = document.createElement('div');
    _searchOverlay.id = 'search-overlay';
    // Outer glass panel
    _searchOverlay.style.cssText = 'display:none;position:absolute;top:48px;right:12px;z-index:70;' +
      'width:340px;max-width:calc(100vw - 24px);padding:10px;' +
      'background:rgba(12,12,18,0.45);' +
      'border:1px solid rgba(255,255,255,0.08);border-radius:18px;' +
      'box-shadow:0 1px 0 rgba(255,255,255,0.05) inset,' +
      '0 20px 60px rgba(0,0,0,0.5);' +
      'backdrop-filter:blur(24px);-webkit-backdrop-filter:blur(24px);';

    // Inner orange-bordered frame (glass gap = the 10px padding above)
    var inner = document.createElement('div');
    inner.style.cssText = 'border:1px solid rgba(232,167,53,0.5);border-radius:12px;' +
      'overflow:hidden;background:rgba(12,12,18,0.3);';

    // Drag handle — minimal, just a thin grip line
    var grip = document.createElement('div');
    grip.style.cssText = 'height:20px;cursor:grab;display:flex;align-items:center;' +
      'justify-content:center;user-select:none;';
    grip.innerHTML = '<div style="width:32px;height:3px;border-radius:2px;' +
      'background:rgba(255,255,255,0.12)"></div>';

    // Drag logic — uses absolute positioning within container
    grip.addEventListener('pointerdown', function (e) {
      e.preventDefault();
      grip.style.cursor = 'grabbing';
      var rect = _searchOverlay.getBoundingClientRect();
      var parentRect = _searchOverlay.parentElement.getBoundingClientRect();
      _dragState = {
        startX: e.clientX, startY: e.clientY,
        origLeft: rect.left - parentRect.left,
        origTop: rect.top - parentRect.top
      };
      _searchOverlay.style.right = 'auto';
      _searchOverlay.style.left = _dragState.origLeft + 'px';
      _searchOverlay.style.top = _dragState.origTop + 'px';
    });
    document.addEventListener('pointermove', function (e) {
      if (!_dragState) return;
      var dx = e.clientX - _dragState.startX;
      var dy = e.clientY - _dragState.startY;
      _searchOverlay.style.left = (_dragState.origLeft + dx) + 'px';
      _searchOverlay.style.top = (_dragState.origTop + dy) + 'px';
    });
    document.addEventListener('pointerup', function () {
      if (_dragState) {
        _dragState = null;
        grip.style.cursor = 'grab';
      }
    });

    inner.appendChild(grip);

    // Search input — glass, no background, orange caret
    _searchInput = document.createElement('input');
    _searchInput.type = 'search';
    _searchInput.placeholder = 'Search\u2026';
    _searchInput.style.cssText = 'width:100%;padding:12px 14px;background:transparent;' +
      'color:#fff;border:none;font-size:17px;font-weight:500;outline:none;min-height:44px;' +
      'caret-color:#e8a735;letter-spacing:0.3px;' +
      'border-bottom:1px solid rgba(232,167,53,0.12);';
    inner.appendChild(_searchInput);

    // Results container
    _searchResultsEl = document.createElement('div');
    _searchResultsEl.style.cssText = 'max-height:240px;overflow-y:auto;';
    // Thin scrollbar
    _searchResultsEl.innerHTML = '<style>#search-overlay ::-webkit-scrollbar{width:3px}' +
      '#search-overlay ::-webkit-scrollbar-thumb{background:rgba(232,167,53,0.3);border-radius:2px}</style>';
    inner.appendChild(_searchResultsEl);

    _searchOverlay.appendChild(inner);

    // Input handler — FTS5 debounced
    _searchInput.addEventListener('input', function () {
      var q = this.value.trim();
      clearTimeout(_searchTimer);
      if (!q || q.length < 2) {
        _searchResultsEl.innerHTML = '';
        return;
      }
      _searchTimer = setTimeout(function () {
        _doFTSSearch(q, _searchResultsEl);
      }, 300);
    });

    // Keyboard nav
    _searchInput.addEventListener('keydown', function (e) {
      var items = _searchResultsEl.querySelectorAll('[data-search-hit]');
      if (e.key === 'Escape') {
        _toggleSearchOverlay();
        return;
      }
      if (!items.length) return;
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        _selectedIdx = Math.min(_selectedIdx + 1, items.length - 1);
        _highlightSearchItem(items, _selectedIdx);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        _selectedIdx = Math.max(_selectedIdx - 1, 0);
        _highlightSearchItem(items, _selectedIdx);
      } else if (e.key === 'Enter' && _selectedIdx >= 0 && _selectedIdx < items.length) {
        e.preventDefault();
        items[_selectedIdx].click();
      }
    });

    // Append to graph container if available (visible during fullscreen), else body
    var host = _graphContainer || document.body;
    host.appendChild(_searchOverlay);
    console.log('§AD_UI searchOverlay created host=' + (host === document.body ? 'body' : 'graphContainer'));
  }

  // Glass chime — synthesised with Web Audio API (no file needed)
  function _glassChime(opening) {
    try {
      var ctx = new (window.AudioContext || window.webkitAudioContext)();
      var osc = ctx.createOscillator();
      var gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(opening ? 1800 : 1200, ctx.currentTime);
      osc.frequency.exponentialRampToValueAtTime(opening ? 2800 : 800, ctx.currentTime + 0.08);
      gain.gain.setValueAtTime(0.06, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.15);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(ctx.currentTime);
      osc.stop(ctx.currentTime + 0.15);
      setTimeout(function () { ctx.close(); }, 300);
    } catch (e) { /* no audio context — silent */ }
  }

  function _toggleSearchOverlay() {
    _ensureSearchOverlay();
    // Re-parent to graph container if it changed
    if (_graphContainer && _searchOverlay.parentElement !== _graphContainer) {
      _graphContainer.appendChild(_searchOverlay);
    }
    var visible = _searchOverlay.style.display !== 'none';
    if (visible) {
      _searchOverlay.style.display = 'none';
      _glassChime(false);
      console.log('§AD_UI search hide');
    } else {
      _searchOverlay.style.right = '12px';
      _searchOverlay.style.left = 'auto';
      _searchOverlay.style.top = '48px';
      _searchOverlay.style.display = 'block';
      _searchInput.value = '';
      _searchResultsEl.innerHTML = '';
      _searchInput.focus();
      _glassChime(true);
      console.log('§AD_UI search show');
    }
  }

  // ── FTS5 Smart Search (R1) ──────────────────────────────────────

  function _doFTSSearch(query, resultsEl) {
    if (typeof ERPSearch === 'undefined' || !ERPSearch.isIndexed()) {
      _hideSearchResults();
      return;
    }

    var hits = ERPSearch.search(query, 15);
    if (!hits.length) {
      resultsEl.innerHTML = '<div style="padding:16px;color:#666;font-size:13px;text-align:center">' +
        'No results for "' + _esc(query) + '"</div>';
      resultsEl.style.display = 'block';
      console.log('§AD_UI search query="' + query + '" hits=0');
      return;
    }

    // Single exact match → auto-jump to window
    if (hits.length === 1 && hits[0].window_id) {
      _hideSearchResults();
      console.log('§AD_UI search auto-jump window=' + hits[0].window_id +
                  ' record=' + hits[0].record_id);
      openWindow(Number(hits[0].window_id));
      return;
    }

    // Render results — white bold names, thin glass drawer, no borders
    var html = '';
    for (var i = 0; i < hits.length; i++) {
      var h = hits[i];
      var dotColour = ERPSearch.statusColour(h.doc_status);
      var label = ERPSearch.tableLabel(h.table_name);
      html += '<div data-search-hit="1" data-window-id="' + (h.window_id || 0) +
        '" data-table="' + _esc(h.table_name) + '" data-record-id="' + h.record_id + '"' +
        ' style="padding:8px 14px;cursor:pointer;transition:background 0.12s;' +
        'display:flex;align-items:center;gap:8px">' +
        '<span style="width:6px;height:6px;border-radius:50%;background:' + dotColour +
        ';flex-shrink:0"></span>' +
        '<div style="flex:1;min-width:0">' +
        '<div style="color:#fff;font-weight:600;font-size:13px;white-space:nowrap;' +
        'overflow:hidden;text-overflow:ellipsis">' +
        _esc(h.display_text) + '</div>' +
        '<div style="color:#666;font-size:10px;font-weight:400">' + _esc(label) +
        (h.doc_status ? ' \u00b7 ' + h.doc_status : '') +
        '</div></div></div>';
    }
    resultsEl.innerHTML = html;
    resultsEl.style.display = 'block';
    _selectedIdx = -1;

    // Click handlers
    var items = resultsEl.querySelectorAll('[data-search-hit]');
    for (var j = 0; j < items.length; j++) {
      items[j].addEventListener('pointerup', function () {
        var wid = Number(this.dataset.windowId);
        _hideSearchResults();
        if (wid) {
          console.log('§AD_UI search click window=' + wid + ' record=' + this.dataset.recordId);
          openWindow(wid);
        }
      });
      items[j].addEventListener('pointerenter', function () {
        this.style.background = 'rgba(255,255,255,0.06)';
      });
      items[j].addEventListener('pointerleave', function () {
        this.style.background = 'none';
      });
    }

    console.log('§AD_UI search query="' + query + '" hits=' + hits.length);
  }

  function _hideSearchResults() {
    var el = document.getElementById('search-results');
    if (el) el.style.display = 'none';
    _selectedIdx = -1;
  }

  var _selectedIdx = -1;

  function _highlightSearchItem(items, idx) {
    for (var i = 0; i < items.length; i++) {
      items[i].style.background = (i === idx) ? 'rgba(108,159,255,0.15)' : 'none';
    }
    if (items[idx]) items[idx].scrollIntoView({ block: 'nearest' });
  }

  // ── Init ──────────────────────────────────────────────────────────

  /**
   * Init AD UI renderer.
   * @param {Object}  db         sql.js database
   * @param {Element} contentEl  main content container
   * @param {Element} navEl      bottom nav container
   * @param {Element} breadcrumbEl  breadcrumb bar
   */
  function init(db, contentEl, navEl, breadcrumbEl) {
    _db = db;
    _contentEl = contentEl;
    _navEl = navEl;
    _breadcrumbEl = breadcrumbEl;

    ADParser.init(db);
    _initBroadcast();
    _initKeyboard();
    _initEdgeSwipe();

    // Build FTS5 search index (R1)
    if (typeof ERPSearch !== 'undefined') {
      var idx = ERPSearch.buildIndex(db);
      console.log('§AD_UI fts5 indexed rows=' + idx.rows + ' ms=' + idx.ms);
    }

    showMenu();

    console.log('§AD_UI init done');
  }

  // ── Public API ─────────────────────────────────────────────────────

  var ADUI = {
    init:       init,
    showMenu:   showMenu,
    openWindow: openWindow,
    // Exposed for testing — CRUD toolbar / arrow keys
    navRecord:  _navRecord,
    getRecordIdx: function () { return _currentRecordIdx; },
    getRecordCount: function () { return _currentRecords.length; },
    getCurrentScreen: function () { return _currentScreen; },
    switchTab:  _switchTab,
    getTabIdx:  function () { return _currentTabIdx; }
  };

  if (typeof window !== 'undefined') window.ADUI = ADUI;
  if (typeof module !== 'undefined' && module.exports) module.exports = ADUI;

  console.log('§AD_UI_LOADED v10');
})();
