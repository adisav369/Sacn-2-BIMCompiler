/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
// panels.js — Panel collapse, storey/disc filters, building list, HUD, swipe
function setupPanels(A) {
  // Prevent touch/click on floating panels from reaching canvas underneath
  ['hud','search-box','storey-panel','disc-panel','info-panel','issues-panel','status'].forEach(function(pid) {
    var el = document.getElementById(pid);
    if (el) el.addEventListener('pointerdown', function(e) { e.stopPropagation(); });
  });

  // Panel collapse
  A.togglePanel = function(id) {
    const body = document.getElementById(id);
    body.classList.toggle('collapsed');
  };

  // ══════════════════════════════════════════════════════════════
  // S251 §8: ListKeyNav — universal keyboard navigator for list panels
  // Implementing S251_keyboard_modes.md — Witness: W-KBD
  // ══════════════════════════════════════════════════════════════
  function makeListKeyNav(getItems, onToggle, onActivate, onCursorMove) {
    var cursor = -1;
    var anchor = -1;
    var selected = new Set();
    var _taBuffer = '';
    var _taTimer = null;

    function scrollTo(i) {
      var items = getItems();
      if (items[i]) items[i].scrollIntoView({ block: 'nearest' });
    }

    function moveCursor(delta) {
      var items = getItems();
      if (!items.length) { console.log('§LISTNAV_MOVE empty list, no-op'); return; }
      var prev = cursor;
      cursor = Math.max(0, Math.min(items.length - 1, cursor + delta));
      scrollTo(cursor);
      // Visual highlight
      items.forEach(function(el, j) {
        el.style.outline = (j === cursor) ? '2px solid #4fc3f7' : '';
      });
      var label = items[cursor] ? (items[cursor].textContent || '').trim().slice(0, 20) : '?';
      console.log('§LISTNAV_MOVE prev=' + prev + ' now=' + cursor + ' label="' + label + '" total=' + items.length);
      if (onCursorMove) onCursorMove(cursor);
    }

    function extendRange(delta) {
      if (anchor < 0) anchor = cursor >= 0 ? cursor : 0;
      moveCursor(delta);
      var lo = Math.min(anchor, cursor), hi = Math.max(anchor, cursor);
      selected = new Set();
      for (var i = lo; i <= hi; i++) selected.add(i);
      console.log('§LISTNAV_RANGE anchor=' + anchor + ' cursor=' + cursor + ' lo=' + lo + ' hi=' + hi);
      _emit();
    }

    function _emit() {
      onToggle(Array.from(selected));
      console.log('§LISTNAV_SELECT count=' + selected.size + ' indices=[' + Array.from(selected).join(',') + ']');
    }

    return {
      onKey: function(e) {
        var items = getItems();
        // If cursor is on a slider, ←→ steps the slider value
        var curItem = items[cursor];
        if (curItem && curItem.tagName === 'INPUT' && curItem.type === 'range') {
          if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
            var step = parseFloat(curItem.step) || 1;
            var val = parseFloat(curItem.value) + (e.key === 'ArrowRight' ? step : -step);
            val = Math.max(parseFloat(curItem.min), Math.min(parseFloat(curItem.max), val));
            curItem.value = val;
            // Fire oninput handler
            curItem.dispatchEvent(new Event('input'));
            console.log('§LISTNAV_SLIDER val=' + val.toFixed(2));
            return;
          }
          // ↑↓ moves cursor off the slider to next/prev item
          if (e.key === 'ArrowUp') { moveCursor(-1); return; }
          if (e.key === 'ArrowDown') { moveCursor(+1); return; }
        }
        // Shift+Arrow must be checked BEFORE plain Arrow
        if (e.shiftKey && (e.key === 'ArrowUp' || e.key === 'ArrowLeft'))   { console.log('§LISTNAV_KEY shift+up'); extendRange(-1); return; }
        if (e.shiftKey && (e.key === 'ArrowDown' || e.key === 'ArrowRight')) { console.log('§LISTNAV_KEY shift+down'); extendRange(+1); return; }
        if (e.key === 'ArrowUp' || e.key === 'ArrowLeft')   { moveCursor(-1); return; }
        if (e.key === 'ArrowDown' || e.key === 'ArrowRight') { moveCursor(+1); return; }
        if (e.key === 'PageUp')    { console.log('§LISTNAV_KEY pageup'); moveCursor(-5); return; }
        if (e.key === 'PageDown')  { console.log('§LISTNAV_KEY pagedown'); moveCursor(+5); return; }
        if (e.key === 'Home')      { console.log('§LISTNAV_KEY home'); cursor = -1; moveCursor(1); return; }
        if (e.key === 'End')       { console.log('§LISTNAV_KEY end'); cursor = items.length; moveCursor(-1); return; }
        if (e.ctrlKey && e.key === 'a') {
          selected = new Set();
          items.forEach(function(_, i) { selected.add(i); });
          console.log('§LISTNAV_KEY ctrl+a selectAll=' + selected.size);
          _emit();
          return;
        }
        if (e.key === ' ' && !e.ctrlKey) {
          selected = new Set([cursor]); anchor = cursor;
          console.log('§LISTNAV_KEY space activate cursor=' + cursor);
          _emit();
          if (onActivate) onActivate(cursor);
          return;
        }
        if (e.ctrlKey && e.key === ' ') {
          var action = selected.has(cursor) ? 'remove' : 'add';
          if (selected.has(cursor)) selected.delete(cursor); else selected.add(cursor);
          anchor = cursor;
          console.log('§LISTNAV_KEY ctrl+space ' + action + ' cursor=' + cursor);
          _emit();
          return;
        }
        if (e.key === 'Enter' && onActivate) { console.log('§LISTNAV_KEY enter cursor=' + cursor); onActivate(cursor); return; }
      },
      onTypeahead: function(ch) {
        clearTimeout(_taTimer);
        _taBuffer += ch.toLowerCase();
        var items = getItems();
        var labels = [];
        items.forEach(function(el) { labels.push((el.textContent || '').trim().toLowerCase()); });
        var matches = [];
        labels.forEach(function(l, i) { if (l.indexOf(_taBuffer) === 0) matches.push(i); });
        if (matches.length) {
          var next = matches[0];
          // Same single char pressed again → cycle to next match
          if (_taBuffer.length === 1 && matches.indexOf(cursor) >= 0) {
            next = matches[(matches.indexOf(cursor) + 1) % matches.length];
          }
          cursor = next;
          scrollTo(cursor);
          var items2 = getItems();
          items2.forEach(function(el, j) {
            el.style.outline = (j === cursor) ? '2px solid #4fc3f7' : '';
          });
          console.log('§LISTNAV_TYPEAHEAD buf=' + _taBuffer + ' match=' + cursor);
        }
        _taTimer = setTimeout(function() { _taBuffer = ''; }, 600);
      },
      onClick: function(index, e) {
        if (e.ctrlKey || e.metaKey) {
          if (selected.has(index)) selected.delete(index); else selected.add(index);
          anchor = index;
        } else if (e.shiftKey && anchor >= 0) {
          var lo = Math.min(anchor, index), hi = Math.max(anchor, index);
          selected = new Set();
          for (var i = lo; i <= hi; i++) selected.add(i);
        } else {
          selected = new Set([index]); anchor = index; cursor = index;
        }
        _emit();
      },
      getSelected: function() { return Array.from(selected); }
    };
  }

  // Expose for dynamic panel registration (clash matrix, etc.)
  window.makeListKeyNav = makeListKeyNav;

  // Wire ListKeyNav to storey + DISC panels after populate
  var _storeyNav = null, _discNav = null;
  A._wireListKeyNav = function() {
    var storeyPanel = document.getElementById('storey-panel');
    var discPanel = document.getElementById('disc-panel');

    if (storeyPanel && !_storeyNav) {
      _storeyNav = makeListKeyNav(
        function() { return Array.from(document.querySelectorAll('#storey-body button')); },
        function(indices) {
          var btns = Array.from(document.querySelectorAll('#storey-body button'));
          // Multi-select: show all selected storeys, hide the rest
          if (indices.length >= 1) {
            // Extract storey names from button onclick
            var selectedStoreys = [];
            indices.forEach(function(i) {
              if (!btns[i]) { console.log('§STOREY_TOGGLE btn[' + i + '] missing, total=' + btns.length); return; }
              var m = btns[i].onclick ? btns[i].onclick.toString().match(/filterStorey\('(.+?)'\)/) : null;
              if (m) selectedStoreys.push(m[1]);
              else selectedStoreys.push(null); // "All Storeys" button
            });
            console.log('§STOREY_TOGGLE indices=[' + indices.join(',') + '] storeys=[' + selectedStoreys.join(',') + ']');
            // If "All Storeys" is in selection, show all
            if (selectedStoreys.indexOf(null) >= 0) {
              console.log('§STOREY_TOGGLE → all (null in selection)');
              A.filterStorey(null);
            } else if (selectedStoreys.length === 1) {
              console.log('§STOREY_TOGGLE → single: ' + selectedStoreys[0]);
              A.filterStorey(selectedStoreys[0]);
            } else {
              // Multi-storey: show meshes matching any selected storey
              A.activeStoreyFilter = selectedStoreys;
              A.collectMeshes(function(o) { return o.isMesh && o.userData.storey !== undefined; }).forEach(function(obj) {
                obj.visible = selectedStoreys.indexOf(obj.userData.storey) >= 0;
              });
              A.collectMeshes(function(o) { return o.isInstancedMesh; }).forEach(function(mesh) {
                A.filterInstancedMesh(mesh, function(meta) {
                  return selectedStoreys.indexOf(meta.storey) >= 0;
                });
              });
              // Highlight selected buttons
              btns.forEach(function(btn, j) { btn.className = indices.indexOf(j) >= 0 ? 'active' : ''; });
              console.log('§STOREY_MULTI storeys=' + selectedStoreys.join(','));
              if (A.markDirty) A.markDirty();
            }
          }
        },
        function(idx) {
          var btns = Array.from(document.querySelectorAll('#storey-body button'));
          if (btns[idx]) btns[idx].click();
        }
      );
      if (typeof _registerPanel === 'function') _registerPanel('storey', storeyPanel, _storeyNav);
      console.log('§LISTNAV_WIRE panel=storey');
    }

    if (discPanel && !_discNav) {
      _discNav = makeListKeyNav(
        function() { return Array.from(document.querySelectorAll('#disc-body button')); },
        function(indices) {
          var btns = Array.from(document.querySelectorAll('#disc-body button'));
          // Multi-select: toggle each selected disc
          if (indices.length >= 1) {
            // Get all disc names
            var allDiscs = [];
            btns.forEach(function(btn) {
              var m = btn.onclick ? btn.onclick.toString().match(/toggleDisc\('(.+?)'\)/) : null;
              if (m) allDiscs.push(m[1]);
            });
            // Show only selected, hide rest
            var selectedDiscs = [];
            indices.forEach(function(i) {
              if (allDiscs[i]) selectedDiscs.push(allDiscs[i]);
            });
            // Reset all hidden, then hide non-selected
            A.hiddenDiscs = new Set();
            allDiscs.forEach(function(d) {
              if (selectedDiscs.indexOf(d) < 0) A.hiddenDiscs.add(d);
            });
            // Apply visibility
            A.collectMeshes(function(o) { return o.isMesh && o.userData.disc; }).forEach(function(obj) {
              var discVisible = !A.hiddenDiscs.has(obj.userData.disc);
              var storeyVisible = A.activeStoreyFilter === null ||
                (Array.isArray(A.activeStoreyFilter) ? A.activeStoreyFilter.indexOf(obj.userData.storey) >= 0 : obj.userData.storey === A.activeStoreyFilter);
              obj.visible = discVisible && storeyVisible;
            });
            A.collectMeshes(function(o) { return o.isInstancedMesh; }).forEach(function(mesh) {
              A.filterInstancedMesh(mesh, function(meta) {
                return !A.hiddenDiscs.has(meta.disc) &&
                  (A.activeStoreyFilter === null ||
                   (Array.isArray(A.activeStoreyFilter) ? A.activeStoreyFilter.indexOf(meta.storey) >= 0 : meta.storey === A.activeStoreyFilter));
              });
            });
            btns.forEach(function(btn, j) { btn.className = indices.indexOf(j) >= 0 ? 'active' : ''; });
            console.log('§DISC_MULTI selected=' + selectedDiscs.join(',') + ' hidden=' + A.hiddenDiscs.size);
            if (A.markDirty) A.markDirty();
          }
        },
        function(idx) {
          var btns = Array.from(document.querySelectorAll('#disc-body button'));
          if (btns[idx]) btns[idx].click();
        }
      );
      if (typeof _registerPanel === 'function') _registerPanel('disc', discPanel, _discNav);
      console.log('§LISTNAV_WIRE panel=disc');
    }

    // Toolbar — horizontal, ←→ traversal, Space/Enter clicks
    var toolbox = document.getElementById('search-box');
    if (toolbox && !A._toolbarNav) {
      A._toolbarNav = makeListKeyNav(
        function() { return Array.from(document.querySelectorAll('#search-body button')); },
        function() { /* no multi-select for toolbar */ },
        function(idx) {
          var btns = Array.from(document.querySelectorAll('#search-body button'));
          if (btns[idx]) btns[idx].click();
        }
      );
      if (typeof _registerPanel === 'function') _registerPanel('toolbar', toolbox, A._toolbarNav);
      console.log('§LISTNAV_WIRE panel=toolbar');
    }

    // Section slider panel — buttons, sliders, AND close toggle
    var secPanel = document.getElementById('section-slider-panel');
    if (secPanel && !A._sectionNav) {
      A._sectionNav = makeListKeyNav(
        function() { return Array.from(secPanel.querySelectorAll('button, input[type="range"], .panel-toggle')); },
        function() {},
        function(idx) {
          var items = Array.from(secPanel.querySelectorAll('button, input[type="range"], .panel-toggle'));
          if (items[idx]) items[idx].click();
        }
      );
      var secClose = function() { if (typeof window.toggleSection === 'function') window.toggleSection(); };
      if (typeof _registerPanel === 'function') _registerPanel('section', secPanel, A._sectionNav, secClose);
      console.log('§LISTNAV_WIRE panel=section');
    }

    // Sunglasses slider panel — register with close
    var sunPanel = document.getElementById('sunglass-slider-panel');
    if (sunPanel && !A._sunglassNav) {
      A._sunglassNav = makeListKeyNav(
        function() { return Array.from(sunPanel.querySelectorAll('button, input[type="range"], .panel-toggle')); },
        function() {},
        function(idx) {
          var items = Array.from(sunPanel.querySelectorAll('button, input[type="range"], .panel-toggle'));
          if (items[idx]) items[idx].click();
        }
      );
      var sunClose = function() { if (typeof window.toggleSunglass === 'function') window.toggleSunglass(); };
      if (typeof _registerPanel === 'function') _registerPanel('sunglass', sunPanel, A._sunglassNav, sunClose);
      console.log('§LISTNAV_WIRE panel=sunglass');
    }
  };

  // Storey isolator
  A.activeStoreyFilter = null;
  A.storeyMeshGroups = {};

  A.populateStoreys = function(building) {
    if (!A.db || !building) return;
    const rows = A.dbQuery(`
      SELECT DISTINCT storey FROM elements_meta
      WHERE building = ? AND storey IS NOT NULL
      ORDER BY storey
    `, [building]);
    const panel = document.getElementById('storey-panel');
    const body = document.getElementById('storey-body');
    if (!rows.length) { panel.style.display = 'none'; return; }

    const storeys = rows.map(r => r[0]);
    body.innerHTML = `<button class="${A.activeStoreyFilter === null ? 'active' : ''}"
      onclick="filterStorey(null)" style="margin-top:4px">${typeof _TRL!=='undefined'&&_TRL.ui_all_storeys||'All Storeys'}</button>` +
      storeys.map(s => `<button class="${A.activeStoreyFilter === s ? 'active' : ''}"
        onclick="filterStorey('${s}')">${s}</button>`).join('');
    panel.style.display = 'block';

    setTimeout(() => { const b = document.getElementById('storey-body'); if (b) b.classList.add('collapsed'); }, 5000);
    // S251: Wire ListKeyNav after buttons are populated
    if (A._wireListKeyNav) A._wireListKeyNav();
  };

  A.filterStorey = function(storey) {
    A.activeStoreyFilter = storey;
    // S239: Regular meshes — show/hide by storey
    A.collectMeshes(o => o.isMesh && o.userData.storey !== undefined).forEach(obj => {
      obj.visible = storey === null || obj.userData.storey === storey;
    });
    // S232/S239: InstancedMesh — per-instance storey filter via zero-scale matrix
    A.collectMeshes(o => o.isInstancedMesh).forEach(mesh => {
      A.filterInstancedMesh(mesh, meta => storey === null || meta.storey === storey);
    });
    document.querySelectorAll('#storey-body button').forEach(btn => {
      const btnStorey = btn.onclick.toString().match(/filterStorey\('(.+?)'\)/)?.[1] || null;
      btn.className = (btnStorey === storey || (storey === null && !btn.onclick.toString().includes("'"))) ? 'active' : '';
    });
    console.log(`[S200] §STOREY_FILTER ${storey || 'ALL'}`);
    if (A.markDirty) A.markDirty();
  };

  // Discipline toggle
  A.hiddenDiscs = new Set();

  A.populateDiscs = function(building) {
    if (!A.db || !building) return;
    const rows = A.dbQuery(`
      SELECT discipline, COUNT(*) FROM elements_meta
      WHERE building = ? AND discipline IS NOT NULL
      GROUP BY discipline ORDER BY COUNT(*) DESC
    `, [building]);
    const panel = document.getElementById('disc-panel');
    const body = document.getElementById('disc-body');
    if (!rows.length) { panel.style.display = 'none'; return; }

    body.innerHTML = rows.map(([d, cnt]) => {
      const hex = '#' + (A.DISC_COLORS[d] || A.DEFAULT_COLOR).toString(16).padStart(6, '0');
      const on = !A.hiddenDiscs.has(d);
      return `<button class="${on ? 'active' : ''}" onclick="toggleDisc('${d}')" style="margin-top:2px">
        <span style="display:inline-block;width:8px;height:8px;border-radius:2px;background:${hex};margin-right:4px"></span>
        ${d} <span style="color:#888;font-size:10px">${cnt.toLocaleString()}</span></button>`;
    }).join('');
    panel.style.display = 'block';

    setTimeout(() => { const b = document.getElementById('disc-body'); if (b) b.classList.add('collapsed'); }, 4000);
    // S251: Wire ListKeyNav after buttons are populated
    if (A._wireListKeyNav) A._wireListKeyNav();
  };

  A.toggleDisc = function(disc) {
    if (A.hiddenDiscs.has(disc)) {
      A.hiddenDiscs.delete(disc);
    } else {
      A.hiddenDiscs.add(disc);
    }
    // S239: Regular meshes — show/hide by disc + storey
    A.collectMeshes(o => o.isMesh && o.userData.disc).forEach(obj => {
      const discVisible = !A.hiddenDiscs.has(obj.userData.disc);
      const storeyVisible = A.activeStoreyFilter === null || obj.userData.storey === A.activeStoreyFilter;
      obj.visible = discVisible && storeyVisible;
    });
    // S232/S239: InstancedMesh — per-instance disc+storey filter
    A.collectMeshes(o => o.isInstancedMesh).forEach(mesh => {
      A.filterInstancedMesh(mesh, meta => {
        return !A.hiddenDiscs.has(meta.disc) &&
          (A.activeStoreyFilter === null || meta.storey === A.activeStoreyFilter);
      });
    });
    document.querySelectorAll('#disc-body button').forEach(btn => {
      const m = btn.onclick.toString().match(/toggleDisc\('(.+?)'\)/);
      if (m) btn.className = A.hiddenDiscs.has(m[1]) ? '' : 'active';
    });
    if (A.markDirty) A.markDirty();
  };

  // Building list
  A.allBuildingCards = [];

  A.populateBuildingList = function() {
    const list = document.getElementById('building-list');
    // Dedupe: strip grid prefix (S0_0_, T0_, etc.) → group by archetype, keep first instance
    const seen = {};
    for (const [name, bc] of Object.entries(A.buildingCentres)) {
      const arch = name.replace(/^[ST]\d+_\d*_?/, '');
      if (!seen[arch] || bc.count > seen[arch].count) {
        seen[arch] = { name, count: bc.count };
      }
    }
    const sorted = Object.entries(seen)
      .sort((a, b) => b[1].count - a[1].count);
    A.allBuildingCards = [];
    list.innerHTML = '';
    for (const [arch, info] of sorted) {
      const card = document.createElement('div');
      card.className = 'bld-card';
      card.innerHTML = `<span>${arch}</span><span class="cnt">${info.count.toLocaleString()}</span>`;
      card.onclick = () => A.flyTo(info.name);
      list.appendChild(card);
      A.allBuildingCards.push({ name: arch.toLowerCase(), el: card });
    }
  };

  // Search filter
  const searchInput = document.getElementById('search');
  searchInput.addEventListener('input', () => {
    const q = searchInput.value.trim().toLowerCase();
    for (const card of A.allBuildingCards) {
      card.el.style.display = (!q || card.name.includes(q)) ? '' : 'none';
    }
  });

  // HUD
  A.updateHUD = function() {
    const barsEl = document.getElementById('disc-bars');
    const total = Object.values(A.discCounts).reduce((a, b) => a + b, 0);
    barsEl.innerHTML = Object.entries(A.discCounts).map(([disc, cnt]) => {
      const pct = (cnt / total * 100).toFixed(1);
      const color = '#' + (A.DISC_COLORS[disc] || A.DEFAULT_COLOR).toString(16).padStart(6, '0');
      return `<span class="disc-bar" style="background:${color};width:${Math.max(pct*1.5, 3)}px" title="${disc}: ${cnt.toLocaleString()} (${pct}%)"></span>`;
    }).join('') + '<br><small style="color:#888">' +
      Object.entries(A.discCounts).slice(0, 6).map(([d, c]) => `${d}:${c.toLocaleString()}`).join(' ') + '</small>';
  };

  // Panel toggle (S250 §5 — hides ALL UI chrome for clean screenshots)
  var panelIds = ['hud','search-box','storey-panel','disc-panel','info-panel',
                  'status','grid-overlay-panel','dev-banner',
                  'section-slider-panel','undo-redo-btns'];
  var panelsHidden = false;
  window.toggleAllPanels = function() {
    panelsHidden = !panelsHidden;
    panelIds.forEach(function(pid) {
      var el = document.getElementById(pid);
      if (el) el.classList.toggle('swipe-hidden', panelsHidden);
    });
    // Also catch dynamically created panels (grid, issues, clash, find, nlp, etc.)
    // Abstract: hide everything with position:fixed that is NOT the canvas or the toggle button itself
    var extras = document.querySelectorAll('.glass-panel, #issues-panel, #find-panel, #nlp-bar, #nlp-chips, #nav-hud');
    extras.forEach(function(el) { el.classList.toggle('swipe-hidden', panelsHidden); });
    // Clash matrix has no id — find it via APP reference
    if (A._clashMatrixDiv) A._clashMatrixDiv.classList.toggle('swipe-hidden', panelsHidden);
    var btn = document.getElementById('panel-toggle-btn');
    if (btn) btn.textContent = panelsHidden ? '+' : '−';
    console.log('§PANEL_TOGGLE panelsHidden=' + panelsHidden);
  };
}
