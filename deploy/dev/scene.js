/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
// scene.js — Three.js scene, camera, controls, lighting, ground
function setupScene(A) {
  const canvas = document.getElementById('canvas');
  A.canvas = canvas;

  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, preserveDrawingBuffer: true });
  renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.setPixelRatio(window.devicePixelRatio);
  renderer.setClearColor(0x1a1a2e);
  renderer.shadowMap.enabled = true;
  renderer.localClippingEnabled = true;
  A.renderer = renderer;

  const scene = new THREE.Scene();
  A.scene = scene;

  const camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.5, 50000);
  camera.position.set(300, 200, 400);
  A.camera = camera;

  const controls = new THREE.OrbitControls(camera, canvas);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.maxDistance = 20000;
  controls.minPolarAngle = 0;
  controls.maxPolarAngle = Math.PI;  // Full vertical range (0=top, π=bottom)
  controls.mouseButtons = {
    LEFT: THREE.MOUSE.ROTATE,
    MIDDLE: THREE.MOUSE.DOLLY,
    RIGHT: THREE.MOUSE.PAN
  };
  controls.touches = {
    ONE: THREE.TOUCH.ROTATE,
    TWO: THREE.TOUCH.DOLLY_PAN
  };
  controls.enablePan = true;
  controls.panSpeed = 1.5;
  controls.screenSpacePanning = true;
  controls.zoomSpeed = 1.2;
  controls.rotateSpeed = 0.8;
  controls.keyPanSpeed = 20;
  A.controls = controls;

  // Shift+Left = pan (for trackpad users without middle/right mouse)
  canvas.addEventListener('pointerdown', (e) => {
    if (e.shiftKey && e.button === 0) {
      controls.mouseButtons.LEFT = THREE.MOUSE.PAN;
    }
  });
  canvas.addEventListener('pointerup', () => {
    controls.mouseButtons.LEFT = THREE.MOUSE.ROTATE;
  });

  // Lighting
  const ambient = new THREE.AmbientLight(0x606080, 0.6);
  scene.add(ambient);
  A.ambient = ambient;

  const sun = new THREE.DirectionalLight(0xfff0dd, 1.0);
  sun.position.set(200, 400, 300);
  sun.castShadow = true;
  scene.add(sun);
  A.sun = sun;

  const hemi = new THREE.HemisphereLight(0x8888cc, 0x444422, 0.4);
  scene.add(hemi);
  A.hemi = hemi;

  // Ground plane — positioned after DB load to sit below the lowest building
  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(50000, 50000),
    new THREE.MeshLambertMaterial({ color: 0x222233, side: THREE.DoubleSide })
  );
  ground.rotation.x = -Math.PI / 2;
  ground.receiveShadow = true;
  ground.visible = false;
  scene.add(ground);
  A.ground = ground;

  // State
  A.db = null;
  A.libDb = null;
  A.buildingCentres = {};
  A.discCounts = {};
  A.meshCache = {};
  A.streamedCount = 0;
  A.totalElements = 0;
  A.modelOffset = { x: 0, y: 0, z: 0 };
  A.activeBuilding = null;
  A.activeBuildingTotal = 0;
  A.buildingsRendered = new Set();
  A.status = document.getElementById('status');
  A.guidMap = {};
  A.pointerDownPos = { x: 0, y: 0 };

  // Raycaster
  A.raycaster = new THREE.Raycaster();
  A.mouse = new THREE.Vector2();

  // IFC (X=east, Y=north, Z=up) → Three.js (X=east, Y=up, Z=south)
  A.ifc2three = function(ix, iy, iz) {
    return { x: ix - A.modelOffset.x, y: iz - A.modelOffset.z, z: -(iy - A.modelOffset.y) };
  };

  // IndexedDB cache
  A.CACHE_DB_NAME = 'bim_ootb_cache';
  A.CACHE_STORE = 'dbs';

  A.openCacheDB = function() {
    return new Promise((resolve, reject) => {
      const req = indexedDB.open(A.CACHE_DB_NAME, 1);
      req.onupgradeneeded = () => req.result.createObjectStore(A.CACHE_STORE);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => resolve(null);
    });
  };

  A.cachedFetch = async function(url) {
    const cacheDb = await A.openCacheDB();
    if (cacheDb) {
      try {
        const cached = await new Promise((resolve, reject) => {
          const tx = cacheDb.transaction(A.CACHE_STORE, 'readonly');
          const req = tx.objectStore(A.CACHE_STORE).get(url);
          req.onsuccess = () => resolve(req.result);
          req.onerror = () => resolve(null);
        });
        if (cached) {
          console.log(`[S203] §CACHE_HIT ${url} size=${(cached.byteLength/1024/1024).toFixed(1)}MB`);
          return cached;
        }
      } catch(e) { /* cache miss */ }
    }

    const resp = await fetch(url);
    if (!resp.ok) throw new Error(`Failed to fetch ${url}: ${resp.status}`);
    const contentLength = parseInt(resp.headers.get('Content-Length') || '0', 10);
    let buf;
    if (contentLength > 0 && resp.body) {
      const reader = resp.body.getReader();
      const chunks = []; let received = 0;
      const fileName = url.split('/').pop();
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        chunks.push(value); received += value.length;
        const pct = Math.round((received / contentLength) * 100);
        if (A.status) A.status.textContent = `Downloading ${fileName}... ${pct}% (${(received/1024/1024).toFixed(0)}/${(contentLength/1024/1024).toFixed(0)}MB)`;
      }
      const full = new Uint8Array(received);
      let offset = 0;
      for (const chunk of chunks) { full.set(chunk, offset); offset += chunk.length; }
      buf = full.buffer;
    } else {
      buf = await resp.arrayBuffer();
    }

    if (cacheDb) {
      try {
        const tx = cacheDb.transaction(A.CACHE_STORE, 'readwrite');
        tx.objectStore(A.CACHE_STORE).put(buf, url);
      } catch(e) { console.log(`[S203] §CACHE_WRITE_ERR ${e.message}`); }
    }

    console.log(`[S203] §CACHE_MISS ${url} size=${(buf.byteLength/1024/1024).toFixed(1)}MB — cached for next time`);
    return buf;
  };

  // BLOB → Three.js BufferGeometry
  A.blobToGeometry = function(vBlob, fBlob) {
    try {
      const vArr = new Float32Array(vBlob.buffer, vBlob.byteOffset, vBlob.byteLength / 4);
      const fArr = new Uint32Array(fBlob.buffer, fBlob.byteOffset, fBlob.byteLength / 4);

      if (vArr.length < 9 || fArr.length < 3) return null;

      const positions = new Float32Array(vArr.length);
      for (let i = 0; i < vArr.length; i += 3) {
        positions[i]     = vArr[i];
        positions[i + 1] = vArr[i + 2];
        positions[i + 2] = -vArr[i + 1];
      }

      const geo = new THREE.BufferGeometry();
      geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
      geo.setIndex(new THREE.BufferAttribute(fArr, 1));
      geo.computeVertexNormals();
      geo.computeBoundingSphere();
      return geo;
    } catch (e) {
      return null;
    }
  };

  // Resize handler
  A._onResize = () => {
    A.camera.aspect = window.innerWidth / window.innerHeight;
    A.camera.updateProjectionMatrix();
    A.renderer.setSize(window.innerWidth, window.innerHeight);
  };
  window.addEventListener('resize', A._onResize);

  // ══════════════════════════════════════════════════════════════
  // S251: Key Sequence Engine + Command Palette + Panel Focus
  // Implementing S251_keyboard_modes.md — Witness: W-KBD
  // ══════════════════════════════════════════════════════════════

  // §1 — Sequence engine: buffer + debounce for multi-key shortcuts (SC, SU, etc.)
  var _seq = '';
  var _seqTimer = null;
  var _SEQ_MS = 600;

  var _shortcuts = {
    'g':  function() { if (typeof window.open2DPlans === 'function') window.open2DPlans(); },
    'x':  function() { var b = document.getElementById('section-btn'); if (b) b.click(); },
    '4':  function() { if (typeof A.export4D5D === 'function') A.export4D5D(); },
    'f':  function() { if (typeof A.openFindPanel === 'function') A.openFindPanel(''); },
    'c':  function() { if (A._loadClashRules) A._loadClashRules(function(r) { A._showClashMatrix(r, document.body); }); },
    'm':  function() { if (typeof A.toggleMeasure === 'function') A.toggleMeasure(); },
    'sc': function() { if (typeof A.screenshot === 'function') A.screenshot(); },
    'su': function() { A.toggleXray(); }
  };

  function _dispatchSeq(seq) {
    if (_shortcuts[seq]) {
      _shortcuts[seq]();
      console.log('§KBD_SEQ seq=' + seq);
      return true;
    }
    return false;
  }

  function _isPrefix(seq) {
    var keys = Object.keys(_shortcuts);
    for (var i = 0; i < keys.length; i++) {
      if (keys[i].length > seq.length && keys[i].indexOf(seq) === 0) return true;
    }
    return false;
  }

  // §1.2 — Sequence hint (transient label while waiting for second key)
  function _showSeqHint(text) {
    var el = document.getElementById('kbd-seq-hint');
    if (!el) {
      el = document.createElement('div');
      el.id = 'kbd-seq-hint';
      el.style.cssText = 'position:fixed;bottom:48px;right:16px;z-index:200;' +
        'background:rgba(0,0,0,0.7);color:#4fc3f7;font-family:monospace;font-size:18px;' +
        'padding:4px 10px;border-radius:6px;pointer-events:none;transition:opacity 0.2s';
      document.body.appendChild(el);
    }
    el.textContent = text ? text.toUpperCase() + '\u258C' : '';
    el.style.opacity = text ? '1' : '0';
  }

  // §5 — Command Palette (? key or 🛟 button)
  var _paletteEntries = [
    { seq: 'G',  name: '2D Grid' },
    { seq: 'X',  name: 'Section Cut' },
    { seq: 'F',  name: 'Find / Navigate' },
    { seq: 'C',  name: 'Clash Matrix' },
    { seq: 'M',  name: 'Measure' },
    { seq: 'SC', name: 'Screenshot' },
    { seq: 'SU', name: 'Sunglasses (X-ray)' },
    { seq: '4',  name: '4D / 5D Analytics' }
  ];

  function showCommandPalette() {
    var existing = document.getElementById('cmd-palette');
    if (existing) { existing.remove(); console.log('§KBD_HELP close'); return; }
    console.log('§KBD_HELP open');

    var pal = document.createElement('div');
    pal.id = 'cmd-palette';
    pal.style.cssText = 'position:fixed;top:18%;left:50%;transform:translateX(-50%);' +
      'z-index:10001;background:rgba(10,10,30,0.97);border:1px solid rgba(79,195,247,0.3);' +
      'border-radius:12px;width:320px;box-shadow:0 8px 32px rgba(0,0,0,0.6);' +
      'font-family:\'Segoe UI\',sans-serif;overflow:hidden';

    var html = '<div style="padding:10px 14px;border-bottom:1px solid #333">' +
      '<input id="cmd-search" type="text" placeholder="Type a command..." ' +
      'style="width:100%;background:#222;color:#eee;border:1px solid #555;border-radius:6px;' +
      'padding:8px 10px;font-size:13px;outline:none;box-sizing:border-box">' +
      '</div>' +
      '<div id="cmd-list" style="max-height:260px;overflow-y:auto;padding:4px 0"></div>' +
      '<div style="padding:8px 14px;border-top:1px solid #333;text-align:center">' +
      '<span id="cmd-report" style="color:#ff8a65;font-size:12px;cursor:pointer;font-weight:600">' +
      '\uD83D\uDEDF Report Bug / Get Help</span></div>';
    pal.innerHTML = html;
    document.body.appendChild(pal);

    var searchInput = document.getElementById('cmd-search');
    var listEl = document.getElementById('cmd-list');
    var cursor = 0;

    function renderList(filter) {
      var f = (filter || '').toLowerCase();
      var matches = _paletteEntries.filter(function(e) {
        return e.name.toLowerCase().indexOf(f) >= 0 || e.seq.toLowerCase().indexOf(f) >= 0;
      });
      listEl.innerHTML = '';
      matches.forEach(function(entry, i) {
        var row = document.createElement('div');
        row.className = 'cmd-row';
        row.setAttribute('data-idx', String(i));
        row.style.cssText = 'padding:8px 14px;cursor:pointer;display:flex;align-items:center;' +
          'justify-content:space-between;font-size:13px;color:#e0e0e0;' +
          (i === cursor ? 'background:rgba(79,195,247,0.15)' : '');
        row.innerHTML = '<span>' + entry.name + '</span>' +
          '<kbd style="background:#333;color:#4fc3f7;padding:2px 8px;border-radius:4px;' +
          'font-family:monospace;font-size:12px;border:1px solid #555">' + entry.seq + '</kbd>';
        row.addEventListener('click', function() {
          pal.remove();
          var seq = entry.seq.toLowerCase();
          if (_shortcuts[seq]) _shortcuts[seq]();
          console.log('§KBD_PALETTE_RUN seq=' + entry.seq);
        });
        row.addEventListener('mouseenter', function() {
          cursor = i;
          highlightRows();
        });
        listEl.appendChild(row);
      });
      return matches;
    }

    function highlightRows() {
      var rows = listEl.querySelectorAll('.cmd-row');
      for (var i = 0; i < rows.length; i++) {
        rows[i].style.background = (i === cursor) ? 'rgba(79,195,247,0.15)' : '';
      }
    }

    var currentMatches = renderList('');
    searchInput.focus();

    searchInput.addEventListener('input', function() {
      cursor = 0;
      currentMatches = renderList(this.value);
    });

    searchInput.addEventListener('keydown', function(e) {
      if (e.key === 'Escape') { pal.remove(); console.log('§KBD_HELP close'); return; }
      if (e.key === 'ArrowDown') { e.preventDefault(); cursor = Math.min(cursor + 1, currentMatches.length - 1); highlightRows(); }
      if (e.key === 'ArrowUp')   { e.preventDefault(); cursor = Math.max(cursor - 1, 0); highlightRows(); }
      if (e.key === 'Enter') {
        e.preventDefault();
        if (currentMatches[cursor]) {
          pal.remove();
          var seq = currentMatches[cursor].seq.toLowerCase();
          if (_shortcuts[seq]) _shortcuts[seq]();
          console.log('§KBD_PALETTE_RUN seq=' + currentMatches[cursor].seq);
        }
      }
    });

    // Report Bug link — calls existing APP.reportBug() (helpers.js)
    document.getElementById('cmd-report').addEventListener('click', function() {
      pal.remove();
      if (A.reportBug) A.reportBug();
    });

    // Click outside closes palette
    pal.addEventListener('click', function(e) { e.stopPropagation(); });
    setTimeout(function() {
      document.addEventListener('click', function _closePal() {
        var p = document.getElementById('cmd-palette');
        if (p) p.remove();
        document.removeEventListener('click', _closePal);
      }, { once: true });
    }, 100);
  }

  // Expose for 🛟 button
  A.showCommandPalette = showCommandPalette;
  window.showCommandPalette = showCommandPalette;

  // §2 — Panel Focus Model (Tab to cycle, arrows within, mouse steals focus)
  var _panels = [];
  var _focusedPanel = null;

  function _registerPanel(id, el, nav) {
    _panels.push({ id: id, el: el, nav: nav });
    el.addEventListener('pointerdown', function() { _focusPanel(id); });
  }
  function _focusPanel(id) {
    if (_focusedPanel) _blurPanel();
    _focusedPanel = null;
    for (var i = 0; i < _panels.length; i++) {
      if (_panels[i].id === id && _panels[i].el.offsetParent !== null) {
        _focusedPanel = _panels[i];
        break;
      }
    }
    if (_focusedPanel) {
      _focusedPanel.el.style.boxShadow = 'inset 3px 0 0 #4fc3f7';
      console.log('§PANEL_FOCUS id=' + id);
    }
  }
  function _blurPanel() {
    if (!_focusedPanel) return;
    _focusedPanel.el.style.boxShadow = '';
    console.log('§PANEL_BLUR id=' + _focusedPanel.id);
    _focusedPanel = null;
  }
  function _cyclePanel(dir) {
    // Only cycle visible panels
    var visible = _panels.filter(function(p) { return p.el.offsetParent !== null; });
    if (!visible.length) return;
    var idx = _focusedPanel ? visible.indexOf(_focusedPanel) : -1;
    var next = (idx + dir + visible.length) % visible.length;
    _focusPanel(visible[next].id);
    console.log('§PANEL_TAB id=' + visible[next].id);
  }

  A._registerPanel = _registerPanel;
  window._registerPanel = _registerPanel;

  // ── Keyboard handler ──────────────────────────────────────────
  // ORIGINAL shortcuts preserved. Sequence engine + panel focus added on top.
  window.addEventListener('keydown', function(e) {
    if (window._isMobile) return; // §5 mobile guard

    // Command palette open? Let it handle its own keys
    if (document.getElementById('cmd-palette')) return;

    // Always-on modifier shortcuts (unchanged from original)
    if (e.altKey && e.key === 'z') { e.preventDefault(); A.toggleXray(); return; }
    if (e.key === 'F11') { e.preventDefault(); A.toggleFullscreen(); return; }

    var noMod = !e.ctrlKey && !e.altKey && !e.metaKey;
    var notInput = e.target.tagName !== 'INPUT' && e.target.tagName !== 'TEXTAREA';

    // Tab — cycle panel focus (§2)
    if (e.key === 'Tab' && notInput) {
      e.preventDefault();
      _cyclePanel(e.shiftKey ? -1 : 1);
      return;
    }

    // Panel-focused keys: arrows, space, ctrl+space, escape, typeahead
    if (_focusedPanel && _focusedPanel.nav) {
      if (['ArrowUp', 'ArrowDown'].indexOf(e.key) >= 0 ||
          (e.key === ' ' && noMod) ||
          (e.ctrlKey && e.key === ' ') ||
          (e.key === 'PageUp') || (e.key === 'PageDown') ||
          (e.key === 'Home') || (e.key === 'End') ||
          (e.shiftKey && ['ArrowUp', 'ArrowDown'].indexOf(e.key) >= 0) ||
          (e.ctrlKey && e.key === 'a') ||
          (e.key === 'Enter')) {
        e.preventDefault();
        _focusedPanel.nav.onKey(e);
        return;
      }
      if (e.key === 'Escape') { e.preventDefault(); _blurPanel(); return; }
      // Typeahead within focused panel (single printable char, no modifier)
      if (noMod && notInput && e.key.length === 1 && e.key !== '?' && _focusedPanel.nav.onTypeahead) {
        _focusedPanel.nav.onTypeahead(e.key);
        return;
      }
    }

    if (!noMod || !notInput) return;

    // ? — command palette
    if (e.key === '?') { e.preventDefault(); showCommandPalette(); return; }

    // Esc with no panel focused — no-op
    if (e.key === 'Escape') return;

    // Key sequence engine
    clearTimeout(_seqTimer);
    _seq += e.key.toLowerCase();

    // Exact match?
    if (_dispatchSeq(_seq)) {
      _seq = '';
      _showSeqHint('');
      return;
    }
    // Prefix of a longer sequence? Wait for next key.
    if (_isPrefix(_seq)) {
      e.preventDefault();
      _showSeqHint(_seq);
      _seqTimer = setTimeout(function() {
        console.log('§KBD_SEQ_TIMEOUT seq=' + _seq);
        _seq = '';
        _showSeqHint('');
      }, _SEQ_MS);
      return;
    }
    // No match, no prefix — reset
    _seq = '';
    _showSeqHint('');
  });
}
