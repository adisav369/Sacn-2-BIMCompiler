// loader.js — Progressive script loader: local-first, CDN fallback
// WASM binary fetch starts immediately — downloads in parallel with JS libs
const _wasmBinaryPromise = fetch('lib/sql-wasm.wasm')
  .then(r => r.ok ? r.arrayBuffer().then(b => new Uint8Array(b)) : null)
  .catch(() => fetch('https://cdn.jsdelivr.net/npm/rtree-sql.js@1.7.0/dist/sql-wasm.wasm')
    .then(r => r.ok ? r.arrayBuffer().then(b => new Uint8Array(b)) : null)
    .catch(() => null));
const _loadStart = performance.now();
const _elapsedEl = document.getElementById('load-elapsed');
const _timerIv = setInterval(() => {
  const s = Math.floor((performance.now() - _loadStart) / 1000);
  _elapsedEl.textContent = `${Math.floor(s/60)}:${String(s%60).padStart(2,'0')}`;
}, 1000);

// Local-first, CDN fallback
const LIBS = [
  { name: 'Three.js',
    url: 'lib/three.min.js',
    cdn: 'https://cdn.jsdelivr.net/npm/three@0.156.0/build/three.min.js' },
  { name: 'OrbitControls',
    url: 'lib/OrbitControls.js',
    cdn: 'lib/OrbitControls.js' },  // r156 ESM→IIFE converted, local only (no CDN UMD)
  { name: 'SQLite (WASM+RTree)',
    url: 'lib/sql-wasm.js',
    cdn: 'https://cdn.jsdelivr.net/npm/rtree-sql.js@1.7.0/dist/sql-wasm.js' },
  { name: 'SheetJS (Excel)',
    url: 'lib/xlsx.full.min.js',
    cdn: 'https://cdn.sheetjs.com/xlsx-0.20.3/package/dist/xlsx.full.min.js' },
];

// Create progress rows
const loadItems = document.getElementById('load-items');
LIBS.forEach((lib, i) => {
  const row = document.createElement('div');
  row.id = `lib-${i}`;
  row.style.cssText = 'margin:4px 0;font-size:12px;color:#aaa';
  row.innerHTML = `
    <div style="display:flex;justify-content:space-between">
      <span>${lib.name}</span>
      <span id="lib-${i}-status" style="color:#666">waiting...</span>
    </div>
    <div style="height:3px;background:#333;border-radius:2px;margin-top:2px">
      <div id="lib-${i}-bar" style="height:100%;width:0%;background:#4fc3f7;border-radius:2px;transition:width 0.05s"></div>
    </div>
  `;
  loadItems.appendChild(row);
});

async function fetchWithProgress(url, index) {
  const statusEl = document.getElementById(`lib-${index}-status`);
  const barEl = document.getElementById(`lib-${index}-bar`);
  statusEl.textContent = 'connecting...';
  statusEl.style.color = '#4fc3f7';

  const resp = await fetch(url);
  if (!resp.ok) throw new Error(url + ' → ' + resp.status);
  const total = +resp.headers.get('Content-Length') || 0;
  const reader = resp.body.getReader();
  const chunks = [];
  let received = 0;
  const t0 = performance.now();

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    received += value.length;
    const pct = total > 0 ? (received / total * 100) : 0;
    const speed = received / ((performance.now() - t0) / 1000);
    const eta = total > 0 ? Math.ceil((total - received) / speed) : '?';
    barEl.style.width = (total > 0 ? pct : 50) + '%';
    statusEl.textContent = total > 0
      ? `${(received/1024).toFixed(0)}/${(total/1024).toFixed(0)}KB  ${(speed/1024).toFixed(0)}KB/s  ~${eta}s`
      : `${(received/1024).toFixed(0)}KB`;
  }

  barEl.style.width = '100%';
  barEl.style.background = '#44cc44';
  const elapsed = ((performance.now() - t0) / 1000).toFixed(1);
  statusEl.textContent = `${(received/1024).toFixed(0)}KB in ${elapsed}s`;
  statusEl.style.color = '#44cc44';

  // Inject as script
  const blob = new Blob(chunks, { type: 'application/javascript' });
  const script = document.createElement('script');
  script.src = URL.createObjectURL(blob);
  return new Promise((resolve, reject) => {
    script.onload = resolve;
    script.onerror = reject;
    document.head.appendChild(script);
  });
}

// Local-first, CDN fallback loader
async function loadLib(index) {
  var lib = LIBS[index];
  try {
    await fetchWithProgress(lib.url, index);
    return;
  } catch(e) {
    console.warn('[loader] §LOCAL_FAIL ' + lib.name + ' — falling back to CDN');
    var statusEl = document.getElementById('lib-' + index + '-status');
    if (statusEl) { statusEl.textContent = 'CDN fallback...'; statusEl.style.color = '#ff8c00'; }
    var barEl = document.getElementById('lib-' + index + '-bar');
    if (barEl) { barEl.style.width = '0%'; barEl.style.background = '#4fc3f7'; }
  }
  await fetchWithProgress(lib.cdn, index);
}

async function loadAllLibs() {
  // Three.js must load before OrbitControls (dependency)
  await loadLib(0);  // Three.js

  // §S258: Disable color management IMMEDIATELY after Three.js loads, before ANY Color/Material
  // r156 defaults to ColorManagement.enabled=true which reinterprets all color values as linear.
  // Must be set before OrbitControls or any other code touches THREE.Color.
  THREE.ColorManagement.enabled = false;

  await loadLib(1);  // OrbitControls (needs THREE global)

  // §6.5 BVH acceleration — three-mesh-bvh monkey-patch (non-blocking)
  console.log('§THREE_VERSION r=' + (THREE.REVISION || '?'));
  console.log('§COLOR_MGMT enabled=' + THREE.ColorManagement.enabled);
  console.log('§BVH_LOADING importing three-mesh-bvh@0.7.8 from CDN...');
  var _bvhT0 = performance.now();
  try {
    const bvh = await import('https://cdn.jsdelivr.net/npm/three-mesh-bvh@0.7.8/+esm');
    var _bvhMs = (performance.now() - _bvhT0).toFixed(0);
    console.log('§BVH_FETCHED ms=' + _bvhMs + ' exports=' + Object.keys(bvh).join(','));
    if (!bvh.computeBoundsTree) throw new Error('computeBoundsTree not exported');
    if (!bvh.acceleratedRaycast) throw new Error('acceleratedRaycast not exported');
    THREE.BufferGeometry.prototype.computeBoundsTree = bvh.computeBoundsTree;
    THREE.BufferGeometry.prototype.disposeBoundsTree = bvh.disposeBoundsTree;
    THREE.Mesh.prototype.raycast = bvh.acceleratedRaycast;
    window._bvhReady = true;
    console.log('§BVH_INIT three-mesh-bvh v0.7.8 (r156-compat) monkey-patch applied in ' + _bvhMs + 'ms');
    // Verify: test raycast on a dummy geometry
    try {
      var _testGeo = new THREE.BufferGeometry();
      _testGeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array([0,0,0, 1,0,0, 0,1,0]), 3));
      _testGeo.setIndex(new THREE.BufferAttribute(new Uint16Array([0,1,2]), 1));
      _testGeo.computeBoundsTree();
      var _hasBT = !!_testGeo.boundsTree;
      _testGeo.dispose();
      console.log('§BVH_SELFTEST boundsTree=' + _hasBT);
      if (!_hasBT) { window._bvhReady = false; console.warn('§BVH_SELFTEST_FAIL boundsTree not created'); }
    } catch(e2) {
      window._bvhReady = false;
      console.warn('§BVH_SELFTEST_FAIL ' + e2.message);
    }
  } catch(e) {
    window._bvhReady = false;
    console.warn('§BVH_INIT_FAIL ' + e.message + ' — raycasting at normal speed');
  }

  // sql.js is needed for DB; load it before starting viewer
  await loadLib(2);

  // Critical path done — wait for main.js to define initViewer (may still be loading on mobile)
  clearInterval(_timerIv);

  function _startViewer() {
    document.getElementById('load-overlay').style.display = 'none';
    document.getElementById('canvas').style.display = 'block';
    try {
      initViewer();
    } catch(e) {
      document.getElementById('status').textContent = `Init error: ${e.message}`;
      console.error('[S205] §INIT_VIEWER_ERROR', e);
    }
  }

  if (typeof initViewer === 'function') {
    _startViewer();
  } else {
    // main.js hasn't loaded yet — poll briefly (mobile: local WASM faster than script parse)
    var _waitCount = 0;
    var _waitIv = setInterval(function() {
      if (typeof initViewer === 'function') { clearInterval(_waitIv); _startViewer(); }
      else if (++_waitCount > 100) { // 5s max
        clearInterval(_waitIv);
        document.getElementById('status').textContent = 'Error: main.js failed to load';
        console.error('§INIT_VIEWER_TIMEOUT initViewer not defined after 5s');
      }
    }, 50);
  }

  // SheetJS loads in background — excel.js handles typeof XLSX === 'undefined' gracefully
  loadLib(3).catch(e => {
    console.warn('[loader] §SHEETJS_LOAD_FAIL (Excel export unavailable):', e.message);
  });
}

function retryLoad() {
  location.reload();
}

loadAllLibs().catch(e => {
  document.getElementById('status').textContent = `Load error: ${e.message}`;
  document.getElementById('load-items').innerHTML += `
    <div style="color:#ff6644;margin-top:10px">Failed: ${e.message}</div>
    <button onclick="retryLoad()" style="margin-top:10px;padding:8px 20px;background:#4fc3f7;color:#000;border:none;border-radius:6px;font-size:14px;font-weight:700;cursor:pointer;width:100%">Tap to Retry</button>
  `;
});
