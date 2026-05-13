// ad_ui.js — Implementing ERP_AD_UI.md §2–§5, §10, §12 — Witness: W-ERP-ADUI
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
  var _currentClient = 'system';  // 'system' | 'gardenworld'

  // Windows with actual data in each client
  var SYSTEM_WINDOWS = [102, 100, 101, 105, 151, 108]; // AD self-browse
  var GW_WINDOWS = [123, 140, 144, 108, 176, 291];     // Business data
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

    // Breadcrumb
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

    // KPI hero cards — dynamic from AD metadata
    _renderKPICards();

    // Search box
    var search = document.createElement('input');
    search.type = 'search';
    search.placeholder = 'Search menus\u2026';
    search.style.cssText = 'width:100%;padding:12px 14px;background:#1a1a24;color:#e8e8ed;' +
      'border:1px solid rgba(255,255,255,0.08);border-radius:12px;font-size:14px;' +
      'margin-bottom:12px;outline:none;min-height:48px;transition:border-color 0.2s;';
    search.onfocus = function() { this.style.borderColor = 'rgba(108,159,255,0.4)'; };
    search.onblur = function() { this.style.borderColor = 'rgba(255,255,255,0.08)'; };
    _contentEl.appendChild(search);

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

    // Search filter
    search.addEventListener('input', function () {
      var q = this.value.toLowerCase();
      var items = treeEl.querySelectorAll('[data-menu-item]');
      for (var i = 0; i < items.length; i++) {
        var name = (items[i].dataset.menuName || '').toLowerCase();
        items[i].style.display = (!q || name.indexOf(q) >= 0) ? '' : 'none';
      }
      // Show all folders if searching
      var folders = treeEl.querySelectorAll('[data-folder]');
      for (var j = 0; j < folders.length; j++) {
        folders[j].querySelector('.folder-children').style.display = q ? 'block' : '';
      }
    });

    // Auto-charts dashboard on home screen
    _renderHomeCharts();

    console.log('§AD_UI showMenu roots=' + tree.length + ' client=' + _currentClient +
                ' recent=' + _recentWindows.length);
  }

  function _renderKPICards() {
    var kpis;
    if (_currentClient === 'system') {
      kpis = [
        { label: 'Windows', sql: 'SELECT COUNT(*) FROM AD_Window', icon: '\u25A3', colour: '#6c9fff' },
        { label: 'Tables', sql: 'SELECT COUNT(*) FROM AD_Table', icon: '\u2637', colour: '#a78bfa' },
        { label: 'Fields', sql: 'SELECT COUNT(*) FROM AD_Field', icon: '\u2630', colour: '#54d9a8' },
        { label: 'Menus', sql: 'SELECT COUNT(*) FROM AD_Menu', icon: '\u2261', colour: '#ff9f43' }
      ];
    } else {
      kpis = [
        { label: 'Partners', sql: 'SELECT COUNT(*) FROM C_BPartner', icon: '\u263A', colour: '#6c9fff' },
        { label: 'Products', sql: 'SELECT COUNT(*) FROM M_Product', icon: '\u2B22', colour: '#54d9a8' },
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
      card.style.cssText = 'background:linear-gradient(135deg,#1e1e2a,#252535);' +
        'border:1px solid rgba(255,255,255,0.06);border-radius:14px;padding:14px;' +
        'text-align:center;cursor:default;';
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

  function _renderHomeCharts() {
    var chartSection = document.createElement('div');
    chartSection.style.cssText = 'margin-top:12px;display:grid;' +
      'grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:12px;' +
      'animation:fadeIn 0.4s ease 0.1s both;';

    var queries;
    if (_currentClient === 'system') {
      queries = [
        { label: 'Tables by Row Count (Top 10)', sql: "SELECT name, (SELECT COUNT(*) FROM [AD_Column] WHERE AD_Table_ID = t.AD_Table_ID) as cols FROM AD_Table t ORDER BY cols DESC LIMIT 10" },
        { label: 'Windows by Tab Count', sql: "SELECT w.Name, COUNT(t.AD_Tab_ID) as cnt FROM AD_Window w JOIN AD_Tab t ON w.AD_Window_ID = t.AD_Window_ID WHERE w.IsActive='Y' GROUP BY w.AD_Window_ID ORDER BY cnt DESC LIMIT 10" },
        { label: 'Field Types', sql: "SELECT CASE AD_Reference_ID WHEN 10 THEN 'String' WHEN 13 THEN 'ID' WHEN 19 THEN 'TableDirect' WHEN 20 THEN 'Table' WHEN 30 THEN 'Search' WHEN 38 THEN 'YesNo' WHEN 17 THEN 'List' WHEN 15 THEN 'Date' WHEN 11 THEN 'Integer' WHEN 12 THEN 'Amount' ELSE 'Other' END as type, COUNT(*) as cnt FROM AD_Column GROUP BY type ORDER BY cnt DESC LIMIT 10" }
      ];
    } else {
      queries = [
        { label: 'Products by Category', sql: "SELECT pc.Name, COUNT(p.M_Product_ID) as cnt FROM M_Product p LEFT JOIN M_Product_Category pc ON p.M_Product_Category_ID = pc.M_Product_Category_ID GROUP BY pc.Name ORDER BY cnt DESC" },
        { label: 'Partners (Customer vs Vendor)', sql: "SELECT CASE WHEN IsCustomer='Y' THEN 'Customer' WHEN IsVendor='Y' THEN 'Vendor' ELSE 'Other' END as type, COUNT(*) as cnt FROM C_BPartner GROUP BY type" },
        { label: 'Product Prices', sql: "SELECT p.Name, pp.PriceStd FROM M_Product p JOIN M_ProductPrice pp ON p.M_Product_ID = pp.M_Product_ID WHERE pp.PriceStd > 0 ORDER BY pp.PriceStd DESC LIMIT 10" }
      ];
    }

    for (var q = 0; q < queries.length; q++) {
      var result = ADCharts.runQuery(_db, queries[q].sql);
      if (!result.rows || !result.rows.length || result.error) continue;

      var chartCard = document.createElement('div');
      chartCard.style.cssText = 'background:linear-gradient(135deg,#1e1e2a,#252535);' +
        'border:1px solid rgba(255,255,255,0.06);border-radius:14px;padding:12px;overflow:hidden;';
      var canvas = document.createElement('canvas');
      canvas.width = 440;
      canvas.height = 200;
      canvas.style.cssText = 'display:block;width:100%;height:auto;';
      chartCard.appendChild(canvas);
      chartSection.appendChild(chartCard);

      var labels = result.rows.map(function (r) { return r[0]; });
      var values = result.rows.map(function (r) { return Number(r[1]) || 0; });
      // Use pie for type distributions (<=6 slices), bar for ranked lists
      if (values.length <= 6 && queries[q].label.indexOf('vs') >= 0 || queries[q].label.indexOf('Type') >= 0) {
        ADCharts.drawPieChart(canvas, labels, values, queries[q].label);
      } else {
        ADCharts.drawBarChart(canvas, labels, values, queries[q].label);
      }
    }

    _contentEl.appendChild(chartSection);
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

    // Breadcrumb
    var bc = win.name;
    if (_currentRecords.length > 0) {
      var rec = _currentRecords[_currentRecordIdx];
      var identField = _findIdentifier(tab);
      if (identField && rec[identField]) bc += ' \u25B8 ' + rec[identField];
    }
    _breadcrumbEl.innerHTML = '<span style="cursor:pointer;color:#888" data-action="home">\u2630</span> ' +
      '<span style="color:#eee;font-size:15px;font-weight:bold;flex:1">' + _esc(bc) + '</span>' +
      '<span data-action="help" style="cursor:pointer;color:#4fc3f7;font-size:14px;' +
      'padding:4px 10px;border:1px solid #4fc3f7;border-radius:6px;min-height:32px;' +
      'display:inline-flex;align-items:center">?</span>';
    _breadcrumbEl.style.display = 'flex';
    _breadcrumbEl.style.alignItems = 'center';
    _breadcrumbEl.querySelector('[data-action="home"]').addEventListener('pointerup', function () {
      showMenu();
    });
    _breadcrumbEl.querySelector('[data-action="help"]').addEventListener('pointerup', function () {
      _toggleHelp();
    });

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

    // Record card
    if (_currentRecords.length === 0) {
      var empty = document.createElement('div');
      empty.style.cssText = 'text-align:center;color:#888;padding:40px;font-size:14px;';
      empty.textContent = 'No records in ' + tab.tableName;
      _contentEl.appendChild(empty);
    } else {
      _renderRecordCard(tab);
    }

    // Record counter
    var counter = document.createElement('div');
    counter.style.cssText = 'text-align:center;color:#555;font-size:11px;padding:8px;';
    counter.textContent = _currentRecords.length > 0
      ? (_currentRecordIdx + 1) + ' / ' + _currentRecords.length + ' \u2190 swipe \u2192'
      : '';
    _contentEl.appendChild(counter);
  }

  function _renderRecordCard(tab) {
    var rec = _currentRecords[_currentRecordIdx];
    if (!rec) return;

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
      var row = document.createElement('div');
      row.style.cssText = 'display:flex;justify-content:space-between;align-items:center;' +
        'padding:10px 0;border-bottom:1px solid rgba(255,255,255,0.04);min-height:48px;';

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
    _contentEl.appendChild(card);
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

    // Default: text input (string, text, search, tableDirect, table)
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

    _breadcrumbEl.innerHTML = '<span style="color:#eee;font-size:15px;font-weight:bold">' +
      _esc(_currentWindow.name) + ' \u2014 List</span>';

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
    if (!_currentWindow || !_currentWindow.tabs.length) {
      console.log('§AD_UI charts: no window open');
      return;
    }
    var tab = _currentWindow.tabs[_currentTabIdx];
    if (!_chartOverlay) {
      _chartOverlay = document.createElement('div');
      _chartOverlay.id = 'chart-overlay';
      document.body.appendChild(_chartOverlay);
    }
    ADCharts.renderOverlay(_chartOverlay, _db, tab.tableName);
  }

  function _showMore() {
    _contentEl.innerHTML = '';
    _breadcrumbEl.innerHTML = '<span style="color:#eee;font-size:15px;font-weight:bold">' +
      '\u2699 Settings</span>';

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
    showMenu();

    console.log('§AD_UI init done');
  }

  // ── Public API ─────────────────────────────────────────────────────

  var ADUI = {
    init:       init,
    showMenu:   showMenu,
    openWindow: openWindow
  };

  if (typeof window !== 'undefined') window.ADUI = ADUI;
  if (typeof module !== 'undefined' && module.exports) module.exports = ADUI;

  console.log('§AD_UI_LOADED v1');
})();
