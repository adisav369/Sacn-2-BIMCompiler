// loader.js — Progressive script loader with per-library progress
const _loadStart = performance.now();
const _elapsedEl = document.getElementById('load-elapsed');
const _timerIv = setInterval(() => {
  const s = Math.floor((performance.now() - _loadStart) / 1000);
  _elapsedEl.textContent = `${Math.floor(s/60)}:${String(s%60).padStart(2,'0')}`;
}, 1000);

const LIBS = [
  { name: 'Three.js',       url: 'https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js' },
  { name: 'OrbitControls',   url: 'https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js' },
  { name: 'SQLite (WASM+RTree)', url: 'https://cdn.jsdelivr.net/npm/rtree-sql.js@1.7.0/dist/sql-wasm.js' },
  { name: 'SheetJS (Excel)', url: 'https://cdn.sheetjs.com/xlsx-0.20.3/package/dist/xlsx.full.min.js' },
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

async function loadAllLibs() {
  // Three.js must load before OrbitControls (dependency)
  await fetchWithProgress(LIBS[0].url, 0);  // Three.js
  await fetchWithProgress(LIBS[1].url, 1);  // OrbitControls (needs THREE global)
  // sql.js is needed for DB; load it before starting viewer
  await fetchWithProgress(LIBS[2].url, 2);

  // Critical path done — start viewer now, don't wait for SheetJS (4MB, non-critical)
  clearInterval(_timerIv);
  document.getElementById('load-overlay').style.display = 'none';
  document.getElementById('canvas').style.display = 'block';

  try {
    initViewer();
  } catch(e) {
    document.getElementById('status').textContent = `Init error: ${e.message}`;
    console.error('[S205] §INIT_VIEWER_ERROR', e);
  }

  // SheetJS loads in background — excel.js handles typeof XLSX === 'undefined' gracefully
  fetchWithProgress(LIBS[3].url, 3).catch(e => {
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
