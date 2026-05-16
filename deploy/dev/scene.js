/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
// scene.js — Three.js scene, camera, controls, lighting, ground
function setupScene(A) {
  const canvas = document.getElementById('canvas');
  A.canvas = canvas;

  // §S258: ColorManagement.enabled=false set in loader.js (before any THREE.Color created)
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, preserveDrawingBuffer: true });
  renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.setPixelRatio(window.devicePixelRatio);
  renderer.setClearColor(0x1a1a2e);
  renderer.shadowMap.enabled = true;
  renderer.localClippingEnabled = true;
  renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
  // §S258: r156 defaults to physically-correct lights (lux/candela) — intensity 1.0 is near-dark.
  // r128 used legacy multiplier mode. Restore it.
  renderer.useLegacyLights = true;
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
  // §S258: Light intensities bumped to compensate for r156 Phong shader differences
  const ambient = new THREE.AmbientLight(0x606080, 0.8);
  scene.add(ambient);
  A.ambient = ambient;

  const sun = new THREE.DirectionalLight(0xfff0dd, 1.2);
  sun.position.set(200, 400, 300);
  sun.castShadow = true;
  scene.add(sun);
  A.sun = sun;

  const hemi = new THREE.HemisphereLight(0x8888cc, 0x444422, 0.5);
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

  // BLOB → Three.js BufferGeometry (optional precomputed normals BLOB)
  A.blobToGeometry = function(vBlob, fBlob, nBlob) {
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
      if (nBlob && nBlob.byteLength >= 12) {
        // Precomputed normals — apply same Y↔Z swap as positions
        const nArr = new Float32Array(nBlob.buffer, nBlob.byteOffset, nBlob.byteLength / 4);
        const normals = new Float32Array(nArr.length);
        for (let i = 0; i < nArr.length; i += 3) {
          normals[i]     = nArr[i];
          normals[i + 1] = nArr[i + 2];
          normals[i + 2] = -nArr[i + 1];
        }
        geo.setAttribute('normal', new THREE.BufferAttribute(normals, 3));
        if (A) { A._normalsPrecomputed = (A._normalsPrecomputed || 0) + 1; }
      } else {
        geo.computeVertexNormals();
        if (A) { A._normalsComputed = (A._normalsComputed || 0) + 1; }
      }
      geo.computeBoundingSphere();
      // §S258: BVH deferred — don't build during streaming (86K builds = ~9s lag).
      // acceleratedRaycast falls back to normal raycast when boundsTree is absent.
      // BVH built lazily in background after streaming completes (see streaming.js).
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
    'g':  function() {
      if (A.measureActive || A._clashMatrixDiv) {
        A.status.textContent = 'Close Measure/Clash first'; return;
      }
      if (typeof window.open2DPlans === 'function') window.open2DPlans();
    },
    'x':  function() {
      // In 2D grid mode, scissors is managed by grid_overlay — don't toggle raw section
      if (A._gridOverlayState && A._gridOverlayState.active) {
        console.log('§KBD_X grid active — toggling section within 2D');
        if (A.toggleSection) A.toggleSection();
        return;
      }
      var b = document.getElementById('section-btn'); if (b) b.click();
    },
    '4':  function() { if (typeof A.export4D5D === 'function') A.export4D5D(); },
    'f':  function() { if (typeof A.openFindPanel === 'function') A.openFindPanel(''); },
    'c':  function() {
      // Block in 2D mode — Measure (parent of Clash) is greyed out
      if (A._gridOverlayState && A._gridOverlayState.active) {
        A.status.textContent = 'Exit 2D first'; return;
      }
      if (A._clashMatrixDiv) { A._clashMatrixDiv.remove(); A._clashMatrixDiv = null; return; }
      if (A._loadClashRules) A._loadClashRules(function(r) {
        A._currentClashRules = r;
        A._showClashMatrix(r, document.body);
        // Register matrix for Tab/arrow navigation after DOM is created
        setTimeout(function() {
          if (A._clashMatrixDiv && typeof window.makeListKeyNav === 'function') {
            var matNav = window.makeListKeyNav(
              function() { return Array.from(A._clashMatrixDiv.querySelectorAll('[data-pair]')); },
              function() {},
              function(idx) {
                var cells = Array.from(A._clashMatrixDiv.querySelectorAll('[data-pair]'));
                if (cells[idx]) cells[idx].click();
              }
            );
            var matClose = function() {
              if (A._clashRevealActive && A._dismissClashes) A._dismissClashes();
              if (A._clashMatrixDiv) { A._clashMatrixDiv.remove(); A._clashMatrixDiv = null; }
              if (A._clashModeActive && A._exitClashMode) A._exitClashMode();
            };
            _registerPanel('clash', A._clashMatrixDiv, matNav, matClose);
            _focusPanel('clash');
            // Watch for clash list popup — re-arms when list changes (new cell clicked)
            var _lastClashList = null;
            var _clashListWatcher = setInterval(function() {
              if (!A._clashMatrixDiv) { clearInterval(_clashListWatcher); return; }
              if (A._clashListDiv && A._clashListDiv !== _lastClashList) {
                _lastClashList = A._clashListDiv;
                A._clashListDiv._kbdWired = true;
                // Unregister old clashlist if exists
                for (var pi = _panels.length - 1; pi >= 0; pi--) {
                  if (_panels[pi].id === 'clashlist') { _panels.splice(pi, 1); break; }
                }
                var clashListNav = window.makeListKeyNav(
                  function() { return Array.from(A._clashListDiv.querySelectorAll('[data-clash-idx]')); },
                  function(indices) {
                    // Multi-select: highlight all selected, zoom to frame them all
                    if (indices.length > 1 && A._currentClashes && A.dbQuery && A.ifc2three) {
                      var cc = A._currentClashes;
                      // Map ListKeyNav cursor indices → actual data-clash-idx values
                      var rows = Array.from(A._clashListDiv.querySelectorAll('[data-clash-idx]'));
                      var clashIndices = [];
                      rows.forEach(function(r) { r.style.background = ''; });
                      indices.forEach(function(i) {
                        if (rows[i]) {
                          rows[i].style.background = 'rgba(79,195,247,0.25)';
                          var ci = parseInt(rows[i].getAttribute('data-clash-idx'));
                          if (!isNaN(ci)) clashIndices.push(ci);
                        }
                      });
                      if (!clashIndices.length) return;
                      // Clear previous highlights
                      if (A._clashHighlights) {
                        A._clashHighlights.forEach(function(h) { A.measureGroup.remove(h); });
                      }
                      A._clashHighlights = [];
                      // Query positions per clash pair for midpoint spheres
                      var minV = { x: Infinity, y: Infinity, z: Infinity };
                      var maxV = { x: -Infinity, y: -Infinity, z: -Infinity };
                      clashIndices.forEach(function(ci) {
                        if (!cc[ci]) return;
                        var pr = A.dbQuery(
                          'SELECT center_x, center_y, center_z FROM element_transforms WHERE guid IN (?, ?)',
                          [cc[ci][0], cc[ci][1]]
                        );
                        if (pr.length < 2) return;
                        var pA = A.ifc2three(pr[0][0], pr[0][1], pr[0][2]);
                        var pB = A.ifc2three(pr[1][0], pr[1][1], pr[1][2]);
                        var clashMid = new THREE.Vector3(
                          (pA.x + pB.x) / 2, (pA.y + pB.y) / 2, (pA.z + pB.z) / 2
                        );
                        // Highlight sphere at clash midpoint
                        var sGeo = new THREE.SphereGeometry(0.3, 8, 8);
                        var sMat = new THREE.MeshBasicMaterial({ color: 0xff4444, transparent: true, opacity: 0.7, depthTest: false });
                        var sphere = new THREE.Mesh(sGeo, sMat);
                        sphere.position.copy(clashMid);
                        A.measureGroup.add(sphere);
                        A._clashHighlights.push(sphere);
                        // Expand bounding box
                        if (clashMid.x < minV.x) minV.x = clashMid.x; if (clashMid.x > maxV.x) maxV.x = clashMid.x;
                        if (clashMid.y < minV.y) minV.y = clashMid.y; if (clashMid.y > maxV.y) maxV.y = clashMid.y;
                        if (clashMid.z < minV.z) minV.z = clashMid.z; if (clashMid.z > maxV.z) maxV.z = clashMid.z;
                      });
                      if (!A._clashHighlights.length) return;
                      // Fly camera to frame the bounding box
                      var mid = new THREE.Vector3(
                        (minV.x + maxV.x) / 2, (minV.y + maxV.y) / 2, (minV.z + maxV.z) / 2
                      );
                      var span = Math.max(maxV.x - minV.x, maxV.y - minV.y, maxV.z - minV.z, 2);
                      var camDir = A.camera.position.clone().sub(A.controls.target).normalize();
                      var dist = span * 1.5;
                      var targetPos = mid.clone().add(camDir.multiplyScalar(dist));
                      // Animate (20 frames)
                      var startPos = A.camera.position.clone();
                      var startTarget = A.controls.target.clone();
                      var frame = 0;
                      function step() {
                        frame++;
                        var t = frame / 20;
                        t = t * (2 - t); // ease-out
                        A.camera.position.lerpVectors(startPos, targetPos, t);
                        A.controls.target.lerpVectors(startTarget, mid, t);
                        A.controls.update();
                        A.markDirty();
                        if (frame < 20) requestAnimationFrame(step);
                      }
                      requestAnimationFrame(step);
                      console.log('§CLASH_MULTI count=' + indices.length + ' span=' + span.toFixed(1));
                    } else if (indices.length === 1 && A._flyToClash) {
                      var sRows = Array.from(A._clashListDiv.querySelectorAll('[data-clash-idx]'));
                      var sIdx = sRows[indices[0]] ? parseInt(sRows[indices[0]].getAttribute('data-clash-idx')) : indices[0];
                      A._flyToClash(sIdx);
                    }
                  },
                  function(idx) {
                    var rows = Array.from(A._clashListDiv.querySelectorAll('[data-clash-idx]'));
                    if (rows[idx]) rows[idx].click();
                  }
                );
                var clashListClose = function() {
                  // BUG-5 fix: unregister panel + reset watcher ref on close
                  for (var _ri = _panels.length - 1; _ri >= 0; _ri--) {
                    if (_panels[_ri].id === 'clashlist') { _panels.splice(_ri, 1); break; }
                  }
                  _lastClashList = null;
                  if (A._clashListDiv) { A._clashListDiv.remove(); A._clashListDiv = null; }
                  console.log('§CLASHLIST_CLOSE unregistered, watcher reset');
                };
                _registerPanel('clashlist', A._clashListDiv, clashListNav, clashListClose);
                // BUG-5 fix: delay focus to allow DOM layout before offsetWidth check
                setTimeout(function() { _focusPanel('clashlist'); }, 50);
              }
            }, 300);
          }
        }, 200);
      });
    },
    'm':  function() {
      if (A._gridOverlayState && A._gridOverlayState.active) {
        A.status.textContent = 'Exit 2D first'; return;
      }
      if (typeof A.toggleMeasure === 'function') A.toggleMeasure();
    },
    's':  function() { if (typeof window.toggleSunglass === 'function') window.toggleSunglass(); },
    'p':  function() { if (typeof window.toggleFlyAround === 'function') window.toggleFlyAround(); },
    'sc': function() { if (typeof A.screenshot === 'function') A.screenshot(); },
    '-':  function() { if (typeof window.toggleAllPanels === 'function') window.toggleAllPanels(); },
    '+':  function() { if (typeof window.toggleAllPanels === 'function') window.toggleAllPanels(); },
    '=':  function() { if (typeof window.toggleAllPanels === 'function') window.toggleAllPanels(); }
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
    { seq: 'S',  name: 'Sunglasses (X-ray)' },
    { seq: '4',  name: '4D / 5D Analytics' },
    { seq: 'P',  name: 'Fly Around (Plane)' },
    { seq: '-',  name: 'Toggle Panels (hide/show)' }
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

    var html = '<div style="padding:6px 14px;color:#888;font-size:10px;border-bottom:1px solid #222;text-align:center">' +
      '<div style="padding:10px 14px;border-bottom:1px solid #333">' +
      '<input id="cmd-search" type="text" placeholder="Type a command..." ' +
      'style="width:100%;background:#222;color:#eee;border:1px solid #555;border-radius:6px;' +
      'padding:8px 10px;font-size:13px;outline:none;box-sizing:border-box">' +
      '</div>' +
      '<div id="cmd-list" style="max-height:260px;overflow-y:auto;padding:4px 0"></div>' +
      '<div style="padding:8px 14px;border-top:1px solid #333;text-align:center">' +
      '<span id="cmd-report" style="color:#ff8a65;font-size:12px;cursor:pointer;font-weight:600">' +
      '\uD83D\uDEDF Report Bug</span>' +
      '<span style="color:#555;margin:0 8px">|</span>' +
      '<a id="cmd-docs" href="https://red1oon.github.io/BIMCompiler/MOBILE_DEPLOY/" target="_blank" ' +
      'style="color:#4fc3f7;font-size:12px;text-decoration:none;font-weight:600">' +
      '\uD83D\uDCDA Documentation</a></div>';
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
  var _focusStack = [];  // Esc pops back to previous panel

  function _registerPanel(id, el, nav, closeFn) {
    _panels.push({ id: id, el: el, nav: nav, close: closeFn || null });
    console.log('§PANEL_REGISTER id=' + id + ' hasNav=' + !!nav + ' hasClose=' + !!closeFn + ' totalPanels=' + _panels.length + ' allIds=[' + _panels.map(function(p){return p.id;}).join(',') + ']');
    // Desktop only — no focus glow on mobile touch
    if (!window._isMobile) {
      el.addEventListener('pointerdown', function() { _focusPanel(id); });
    }
  }
  function _focusPanel(id) {
    // Push current to stack before switching
    var prevId = _focusedPanel ? _focusedPanel.id : 'none';
    if (_focusedPanel) {
      _focusStack.push(_focusedPanel.id);
      if (_focusStack.length > 10) _focusStack.shift();
      _focusedPanel.el.style.boxShadow = '';
    }
    _focusedPanel = null;
    var found = false, checkedIds = [];
    for (var i = 0; i < _panels.length; i++) {
      var p = _panels[i];
      if (p.id === id) {
        var vis = p.el.style.display !== 'none' && p.el.offsetWidth > 0;
        checkedIds.push(p.id + '(vis=' + vis + ',w=' + p.el.offsetWidth + ')');
        if (vis) { _focusedPanel = p; found = true; break; }
      }
    }
    if (_focusedPanel) {
      _focusedPanel.el.style.boxShadow = 'inset 3px 0 0 #4fc3f7';
      var body = _focusedPanel.el.querySelector('.panel-body');
      var expanded = false;
      if (body && body.classList.contains('collapsed')) {
        body.classList.remove('collapsed');
        expanded = true;
      }
      var hasNav = !!_focusedPanel.nav;
      var hasClose = !!_focusedPanel.close;
      console.log('§PANEL_FOCUS id=' + id + ' prev=' + prevId + ' hasNav=' + hasNav + ' hasClose=' + hasClose + ' expanded=' + expanded + ' stack=[' + _focusStack.join(',') + ']');
    } else {
      console.log('§PANEL_FOCUS_FAIL id=' + id + ' checked=[' + checkedIds.join(',') + '] totalPanels=' + _panels.length + ' allIds=[' + _panels.map(function(p){return p.id;}).join(',') + ']');
    }
  }
  function _blurPanel() {
    if (!_focusedPanel) { console.log('§PANEL_BLUR no-op (none focused)'); return; }
    var id = _focusedPanel.id;
    _focusedPanel.el.style.boxShadow = '';
    _focusedPanel = null;
    if (_focusStack.length) {
      var prevId = _focusStack.pop();
      console.log('§PANEL_BLUR id=' + id + ' → pop stack → ' + prevId + ' remaining=[' + _focusStack.join(',') + ']');
      _focusPanel(prevId);
    } else {
      console.log('§PANEL_BLUR id=' + id + ' → stack empty → unfocused');
    }
  }
  function _cyclePanel(dir) {
    var visible = _panels.filter(function(p) {
      return p.el.style.display !== 'none' && p.el.offsetWidth > 0;
    });
    if (!visible.length) { console.log('§PANEL_TAB no visible panels (total=' + _panels.length + ')'); return; }
    var idx = _focusedPanel ? visible.indexOf(_focusedPanel) : -1;
    var next = (idx + dir + visible.length) % visible.length;
    console.log('§PANEL_TAB dir=' + dir + ' from=' + (_focusedPanel ? _focusedPanel.id : 'none') + ' idx=' + idx + ' next=' + next + ' visible=[' + visible.map(function(p){return p.id;}).join(',') + ']');
    _focusPanel(visible[next].id);
  }

  A._registerPanel = _registerPanel;
  window._registerPanel = _registerPanel;
  window._focusPanel = _focusPanel;
  window._panels = _panels;

  // ── Keyboard handler ──────────────────────────────────────────
  // ORIGINAL shortcuts preserved. Sequence engine + panel focus added on top.
  window.addEventListener('keydown', function(e) {
    if (window._isMobile) return; // §5 mobile guard

    // Command palette open? Let it handle its own keys
    if (document.getElementById('cmd-palette')) { console.log('§KBD_ROUTE palette active, pass-through key=' + e.key); return; }

    // Always-on modifier shortcuts (unchanged from original)
    if (e.altKey && e.key === 'z') { e.preventDefault(); console.log('§KBD_ROUTE alt+z → xray'); A.toggleXray(); return; }
    if (e.key === 'F11') { e.preventDefault(); console.log('§KBD_ROUTE F11 → fullscreen'); A.toggleFullscreen(); return; }

    var noMod = !e.ctrlKey && !e.altKey && !e.metaKey;
    var notInput = e.target.tagName !== 'INPUT' && e.target.tagName !== 'TEXTAREA';

    // Tab — cycle panel focus (§2)
    if (e.key === 'Tab' && notInput) {
      e.preventDefault();
      console.log('§KBD_ROUTE tab shift=' + e.shiftKey + ' panels=' + _panels.length + ' focused=' + (_focusedPanel ? _focusedPanel.id : 'none'));
      _cyclePanel(e.shiftKey ? -1 : 1);
      return;
    }

    // Panel-focused keys: arrows, space, ctrl+space, escape, typeahead
    if (_focusedPanel && _focusedPanel.nav) {
      if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].indexOf(e.key) >= 0 ||
          (e.key === ' ' && noMod) ||
          (e.ctrlKey && e.key === ' ') ||
          (e.key === 'PageUp') || (e.key === 'PageDown') ||
          (e.key === 'Home') || (e.key === 'End') ||
          (e.shiftKey && ['ArrowUp', 'ArrowDown'].indexOf(e.key) >= 0) ||
          (e.ctrlKey && e.key === 'a') ||
          (e.key === 'Enter')) {
        e.preventDefault();
        console.log('§KBD_ROUTE panel=' + _focusedPanel.id + ' key=' + e.key + ' shift=' + e.shiftKey + ' ctrl=' + e.ctrlKey);
        _focusedPanel.nav.onKey(e);
        return;
      }
      if (e.key === 'Escape') {
        e.preventDefault();
        console.log('§KBD_ROUTE esc panel=' + _focusedPanel.id + ' hasClose=' + !!_focusedPanel.close);
        if (_focusedPanel.close) { _focusedPanel.close(); console.log('§PANEL_CLOSE id=' + _focusedPanel.id); }
        _blurPanel();
        return;
      }
      // Typeahead within focused panel (single printable char, no modifier)
      if (noMod && notInput && e.key.length === 1 && e.key !== '?' && _focusedPanel.nav.onTypeahead) {
        console.log('§KBD_ROUTE typeahead panel=' + _focusedPanel.id + ' char=' + e.key);
        _focusedPanel.nav.onTypeahead(e.key);
        return;
      }
    }

    if (!noMod || !notInput) { console.log('§KBD_ROUTE drop key=' + e.key + ' noMod=' + noMod + ' notInput=' + notInput); return; }

    // Arrow ←→ — step section slider when section panel is visible
    if ((e.key === 'ArrowLeft' || e.key === 'ArrowRight') && !_focusedPanel) {
      var secPanel = document.getElementById('section-slider-panel');
      var slider = document.getElementById('section-slider');
      if (secPanel && secPanel.style.display !== 'none' && slider) {
        e.preventDefault();
        var step = parseFloat(slider.step) || 0.1;
        var val = parseFloat(slider.value) + (e.key === 'ArrowRight' ? step : -step);
        val = Math.max(parseFloat(slider.min), Math.min(parseFloat(slider.max), val));
        slider.value = val;
        if (typeof A.updateSectionPlane === 'function') A.updateSectionPlane(val);
        console.log('§KBD_SLIDER key=' + e.key + ' val=' + val.toFixed(2) + ' min=' + slider.min + ' max=' + slider.max + ' step=' + slider.step);
        return;
      }
    }

    // ? — command palette
    if (e.key === '?') { e.preventDefault(); console.log('§KBD_ROUTE ? → palette'); showCommandPalette(); return; }

    // Esc with no panel focused — no-op
    if (e.key === 'Escape') { console.log('§KBD_ROUTE esc no panel → no-op'); return; }

    // Key sequence engine
    clearTimeout(_seqTimer);
    var prevSeq = _seq;
    _seq += e.key.toLowerCase();

    var hasExact = !!_shortcuts[_seq];
    var hasLonger = _isPrefix(_seq);
    console.log('§KBD_SEQ_ENGINE input=' + e.key + ' prevSeq="' + prevSeq + '" seq="' + _seq + '" exact=' + hasExact + ' prefix=' + hasLonger);

    if (hasExact && !hasLonger) {
      console.log('§KBD_SEQ_FIRE seq=' + _seq + ' (immediate, no longer prefix)');
      _dispatchSeq(_seq);
      _seq = '';
      _showSeqHint('');
      return;
    }
    if (hasLonger) {
      e.preventDefault();
      console.log('§KBD_SEQ_WAIT seq=' + _seq + ' (prefix of longer, waiting ' + _SEQ_MS + 'ms)');
      _showSeqHint(_seq);
      _seqTimer = setTimeout(function() {
        if (_shortcuts[_seq]) {
          console.log('§KBD_SEQ_FIRE seq=' + _seq + ' (timeout, exact match)');
          _shortcuts[_seq]();
        } else {
          console.log('§KBD_SEQ_TIMEOUT seq=' + _seq + ' (no match, discarded)');
        }
        _seq = '';
        _showSeqHint('');
      }, _SEQ_MS);
      return;
    }
    // No match, no prefix — reset
    console.log('§KBD_SEQ_DISCARD seq=' + _seq + ' (no match, no prefix)');
    _seq = '';
    _showSeqHint('');
  });
}
