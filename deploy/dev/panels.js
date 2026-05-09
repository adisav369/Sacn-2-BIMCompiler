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
  function makeListKeyNav(getItems, onToggle, onActivate) {
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
      if (!items.length) return;
      cursor = Math.max(0, Math.min(items.length - 1, cursor + delta));
      scrollTo(cursor);
      // Visual highlight
      items.forEach(function(el, j) {
        el.style.outline = (j === cursor) ? '2px solid #4fc3f7' : '';
      });
    }

    function extendRange(delta) {
      if (anchor < 0) anchor = cursor >= 0 ? cursor : 0;
      moveCursor(delta);
      var lo = Math.min(anchor, cursor), hi = Math.max(anchor, cursor);
      selected = new Set();
      for (var i = lo; i <= hi; i++) selected.add(i);
      _emit();
    }

    function _emit() {
      onToggle(Array.from(selected));
      console.log('§LISTNAV_SELECT count=' + selected.size);
    }

    return {
      onKey: function(e) {
        var items = getItems();
        if (e.key === 'ArrowUp')   { moveCursor(-1); return; }
        if (e.key === 'ArrowDown') { moveCursor(+1); return; }
        if (e.key === 'PageUp')    { moveCursor(-5); return; }
        if (e.key === 'PageDown')  { moveCursor(+5); return; }
        if (e.key === 'Home')      { cursor = 0; scrollTo(0); return; }
        if (e.key === 'End')       { cursor = items.length - 1; scrollTo(cursor); return; }
        if (e.shiftKey && e.key === 'ArrowUp')   { extendRange(-1); return; }
        if (e.shiftKey && e.key === 'ArrowDown') { extendRange(+1); return; }
        if (e.ctrlKey && e.key === 'a') {
          selected = new Set();
          items.forEach(function(_, i) { selected.add(i); });
          _emit();
          return;
        }
        if (e.key === ' ' && !e.ctrlKey) {
          selected = new Set([cursor]); anchor = cursor;
          _emit();
          if (onActivate) onActivate(cursor);
          return;
        }
        if (e.ctrlKey && e.key === ' ') {
          if (selected.has(cursor)) selected.delete(cursor); else selected.add(cursor);
          anchor = cursor;
          _emit();
          return;
        }
        if (e.key === 'Enter' && onActivate) { onActivate(cursor); return; }
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

  // Wire ListKeyNav to storey + DISC panels after populate
  var _storeyNav = null, _discNav = null;
  A._wireListKeyNav = function() {
    var storeyPanel = document.getElementById('storey-panel');
    var discPanel = document.getElementById('disc-panel');

    if (storeyPanel && !_storeyNav) {
      _storeyNav = makeListKeyNav(
        function() { return Array.from(document.querySelectorAll('#storey-body button')); },
        function(indices) {
          // Single-select: activate the storey at cursor
          var btns = Array.from(document.querySelectorAll('#storey-body button'));
          if (indices.length === 1 && btns[indices[0]]) btns[indices[0]].click();
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
          if (indices.length === 1 && btns[indices[0]]) btns[indices[0]].click();
        },
        function(idx) {
          var btns = Array.from(document.querySelectorAll('#disc-body button'));
          if (btns[idx]) btns[idx].click();
        }
      );
      if (typeof _registerPanel === 'function') _registerPanel('disc', discPanel, _discNav);
      console.log('§LISTNAV_WIRE panel=disc');
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
    // Also catch dynamically created panels (grid, issues, clash)
    var extras = document.querySelectorAll('.glass-panel, #issues-panel, #clash-matrix-panel');
    extras.forEach(function(el) { el.classList.toggle('swipe-hidden', panelsHidden); });
    var btn = document.getElementById('panel-toggle-btn');
    if (btn) btn.textContent = panelsHidden ? '+' : '−';
    console.log('§PANEL_TOGGLE panelsHidden=' + panelsHidden);
  };
}
