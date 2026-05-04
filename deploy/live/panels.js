// panels.js — Panel collapse, storey/disc filters, building list, HUD, swipe
function setupPanels(A) {
  // Panel collapse
  A.togglePanel = function(id) {
    const body = document.getElementById(id);
    body.classList.toggle('collapsed');
  };

  // Storey isolator
  A.activeStoreyFilter = null;
  A.storeyMeshGroups = {};

  A.populateStoreys = function(building) {
    if (!A.db || !building) return;
    const rows = A.db.exec(`
      SELECT DISTINCT storey FROM elements_meta
      WHERE building = ? AND storey IS NOT NULL
      ORDER BY storey
    `, [building]);
    const panel = document.getElementById('storey-panel');
    const body = document.getElementById('storey-body');
    if (!rows.length || !rows[0].values.length) { panel.style.display = 'none'; return; }

    const storeys = rows[0].values.map(r => r[0]);
    body.innerHTML = `<button class="${A.activeStoreyFilter === null ? 'active' : ''}"
      onclick="filterStorey(null)" style="margin-top:4px">All Storeys</button>` +
      storeys.map(s => `<button class="${A.activeStoreyFilter === s ? 'active' : ''}"
        onclick="filterStorey('${s}')">${s}</button>`).join('');
    panel.style.display = 'block';
    if (window.innerWidth <= 600) {
      setTimeout(() => { const b = document.getElementById('storey-body'); if (b) b.classList.add('collapsed'); }, 5000);
    }
  };

  A.filterStorey = function(storey) {
    A.activeStoreyFilter = storey;
    A.scene.traverse(obj => {
      if (obj.isMesh && obj !== A.ground && obj.userData.storey !== undefined) {
        obj.visible = storey === null || obj.userData.storey === storey;
      }
      // S232: InstancedMesh — per-instance storey filter via zero-scale matrix
      if (obj.isInstancedMesh && A._instanceMeta[obj.id]) {
        const meta = A._instanceMeta[obj.id];
        const _m4 = new THREE.Matrix4();
        const _zero = new THREE.Matrix4().makeScale(0, 0, 0);
        let anyVisible = false;
        for (let i = 0; i < meta.length; i++) {
          if (storey === null || meta[i].storey === storey) {
            if (meta[i]._origMatrix) { obj.setMatrixAt(i, meta[i]._origMatrix); }
            anyVisible = true;
          } else {
            // Save original matrix on first hide
            if (!meta[i]._origMatrix) {
              meta[i]._origMatrix = new THREE.Matrix4();
              obj.getMatrixAt(i, meta[i]._origMatrix);
            }
            obj.setMatrixAt(i, _zero);
          }
        }
        obj.instanceMatrix.needsUpdate = true;
        obj.visible = anyVisible;
      }
    });
    document.querySelectorAll('#storey-body button').forEach(btn => {
      const btnStorey = btn.onclick.toString().match(/filterStorey\('(.+?)'\)/)?.[1] || null;
      btn.className = (btnStorey === storey || (storey === null && !btn.onclick.toString().includes("'"))) ? 'active' : '';
    });
    console.log(`[S200] §STOREY_FILTER ${storey || 'ALL'}`);
  };

  // Discipline toggle
  A.hiddenDiscs = new Set();

  A.populateDiscs = function(building) {
    if (!A.db || !building) return;
    const rows = A.db.exec(`
      SELECT discipline, COUNT(*) FROM elements_meta
      WHERE building = ? AND discipline IS NOT NULL
      GROUP BY discipline ORDER BY COUNT(*) DESC
    `, [building]);
    const panel = document.getElementById('disc-panel');
    const body = document.getElementById('disc-body');
    if (!rows.length || !rows[0].values.length) { panel.style.display = 'none'; return; }

    body.innerHTML = rows[0].values.map(([d, cnt]) => {
      const hex = '#' + (A.DISC_COLORS[d] || A.DEFAULT_COLOR).toString(16).padStart(6, '0');
      const on = !A.hiddenDiscs.has(d);
      return `<button class="${on ? 'active' : ''}" onclick="toggleDisc('${d}')" style="margin-top:2px">
        <span style="display:inline-block;width:8px;height:8px;border-radius:2px;background:${hex};margin-right:4px"></span>
        ${d} <span style="color:#888;font-size:10px">${cnt.toLocaleString()}</span></button>`;
    }).join('');
    panel.style.display = 'block';
    if (window.innerWidth <= 600) {
      setTimeout(() => { const b = document.getElementById('disc-body'); if (b) b.classList.add('collapsed'); }, 4000);
    }
  };

  A.toggleDisc = function(disc) {
    if (A.hiddenDiscs.has(disc)) {
      A.hiddenDiscs.delete(disc);
    } else {
      A.hiddenDiscs.add(disc);
    }
    A.scene.traverse(obj => {
      if (obj.isMesh && obj !== A.ground && obj.userData.disc) {
        const discVisible = !A.hiddenDiscs.has(obj.userData.disc);
        const storeyVisible = A.activeStoreyFilter === null || obj.userData.storey === A.activeStoreyFilter;
        obj.visible = discVisible && storeyVisible;
      }
      // S232: InstancedMesh — per-instance disc+storey filter via zero-scale matrix
      if (obj.isInstancedMesh && A._instanceMeta[obj.id]) {
        const meta = A._instanceMeta[obj.id];
        const _zero = new THREE.Matrix4().makeScale(0, 0, 0);
        let anyVisible = false;
        for (let i = 0; i < meta.length; i++) {
          const dv = !A.hiddenDiscs.has(meta[i].disc);
          const sv = A.activeStoreyFilter === null || meta[i].storey === A.activeStoreyFilter;
          if (dv && sv) {
            if (meta[i]._origMatrix) { obj.setMatrixAt(i, meta[i]._origMatrix); }
            anyVisible = true;
          } else {
            if (!meta[i]._origMatrix) {
              meta[i]._origMatrix = new THREE.Matrix4();
              obj.getMatrixAt(i, meta[i]._origMatrix);
            }
            obj.setMatrixAt(i, _zero);
          }
        }
        obj.instanceMatrix.needsUpdate = true;
        obj.visible = anyVisible;
      }
    });
    document.querySelectorAll('#disc-body button').forEach(btn => {
      const m = btn.onclick.toString().match(/toggleDisc\('(.+?)'\)/);
      if (m) btn.className = A.hiddenDiscs.has(m[1]) ? '' : 'active';
    });
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

  // Swipe-to-dismiss (mobile)
  (function() {
    if (!('ontouchstart' in window)) return;
    let startX = 0;
    const panelIds = ['hud','search-box','storey-panel','disc-panel','info-panel'];
    let panelsHidden = false;

    function hideAll() {
      panelIds.forEach(pid => {
        const el = document.getElementById(pid);
        if (el) el.classList.add('swipe-hidden');
      });
      panelsHidden = true;
      const s = document.getElementById('status');
      if (s) s.textContent = '<> Swipe to show panels';
    }

    function showAll() {
      panelIds.forEach(pid => {
        const el = document.getElementById(pid);
        if (el) el.classList.remove('swipe-hidden');
      });
      panelsHidden = false;
      const s = document.getElementById('status');
      if (s) s.textContent = '';
    }

    document.addEventListener('touchstart', e => { startX = e.touches[0].clientX; }, {passive:true});
    document.addEventListener('touchend', e => {
      const dx = e.changedTouches[0].clientX - startX;
      if (Math.abs(dx) > 60) {
        if (panelsHidden) showAll(); else hideAll();
      }
    }, {passive:true});

    setTimeout(() => {
      const s = document.getElementById('status');
      if (s) s.textContent = '<> Swipe to hide panels';
      setTimeout(() => { if (s && s.textContent.includes('Swipe')) s.textContent = ''; }, 4000);
    }, 3000);
  })();
}
