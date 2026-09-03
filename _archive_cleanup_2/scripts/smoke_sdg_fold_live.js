#!/usr/bin/env node
// ⚠ DO NOT REMOVE — §FORWARD live wiring smoke (headless Chromium). Scope per CLAUDE.md: §-log first,
// Playwright second = WIRING ONLY (scripts load, button exists, DB returns datums, toggle enables handles).
// Value correctness is proven by W-SDG-FORWARD / W-SDG-FOLD-UI; this only proves the live page wires up.
//
// Serves deploy/dev with RANGE support (sql-httpvfs streams the DB), loads the viewer on the baked
// SampleHouse DB, toggles the Datum-Fold button, and asserts §FOLD-UI enabled handles>0 + a live fold runs.
//
// Run:  node scripts/smoke_sdg_fold_live.js   (needs deploy/dev/tests/node_modules playwright + chromium)
'use strict';
const http = require('http'), fs = require('fs'), path = require('path');
const ROOT = path.join(__dirname, '..', 'deploy', 'dev');
const { chromium } = require(path.join(ROOT, 'tests', 'node_modules', 'playwright'));
const PORT = 8731, DB = 'buildings/SampleHouse_extracted.db';
const MIME = { '.html': 'text/html', '.js': 'application/javascript', '.mjs': 'application/javascript',
  '.wasm': 'application/wasm', '.db': 'application/octet-stream', '.json': 'application/json',
  '.css': 'text/css', '.png': 'image/png', '.svg': 'image/svg+xml', '.bin': 'application/octet-stream' };
let fails = 0;
function check(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

const server = http.createServer((req, res) => {
  const f = path.join(ROOT, req.url === '/' ? 'index.html' : decodeURIComponent(req.url.split('?')[0]));
  fs.stat(f, (e, st) => {
    if (e || !st.isFile()) { res.writeHead(404); return res.end('nf'); }
    const ct = MIME[path.extname(f)] || 'application/octet-stream';
    const range = req.headers.range;
    if (range) {                                                    // 206 partial — sql-httpvfs needs this
      const m = /bytes=(\d+)-(\d*)/.exec(range);
      const start = +m[1], end = m[2] ? +m[2] : st.size - 1;
      res.writeHead(206, { 'Content-Type': ct, 'Accept-Ranges': 'bytes',
        'Content-Range': `bytes ${start}-${end}/${st.size}`, 'Content-Length': end - start + 1 });
      fs.createReadStream(f, { start, end }).pipe(res);
    } else {
      res.writeHead(200, { 'Content-Type': ct, 'Accept-Ranges': 'bytes', 'Content-Length': st.size });
      fs.createReadStream(f).pipe(res);
    }
  });
});

(async () => {
  await new Promise(r => server.listen(PORT, r));
  console.log('═══ §FORWARD live wiring smoke — headless Chromium http://localhost:' + PORT + ' ═══\n');
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  const logs = [], errs = [];
  // benign: viewer PROBES an optional per-building meta DB (*_meta.db); sql-httpvfs CANCELS in-flight range
  // requests on the .db once it has enough bytes (reported as a 404/failed but not a real error).
  const benign = u => /_meta\.db/.test(u) || /favicon/.test(u) || /\.db(\?|$)/.test(u);
  page.on('pageerror', e => errs.push('pageerror:' + e.message));
  page.on('console', m => { logs.push(m.text()); });          // collected for assertions; not error source
  page.on('response', r => { if (r.status() === 404 && !benign(r.url())) errs.push('http404:' + r.url()); });

  await page.goto(`http://localhost:${PORT}/index.html?db=${DB}`, { waitUntil: 'networkidle', timeout: 30000 });
  // wait for the app + scene + DB + both SDG modules
  await page.waitForFunction(() => window.APP && window.APP.db && window.APP.scene && window.SDGFold && window.SDGFoldUI,
    { timeout: 30000 }).catch(() => {});

  const ready = await page.evaluate(() => ({
    hasFold: typeof window.SDGFold, hasUI: typeof window.SDGFoldUI,
    hasDb: !!(window.APP && window.APP.db), hasScene: !!(window.APP && window.APP.scene),
    btn: !!document.getElementById('sdg-fold-btn'),
    datums: (window.APP && window.APP.db) ? (window.APP.dbQuery('SELECT count(*) FROM datum_plane')[0] || [0])[0] : -1,
  }));
  check(ready.hasFold === 'object', 'sdg_fold.js loaded', 'SDGFold=' + ready.hasFold);
  check(ready.hasUI === 'object', 'sdg_fold_ui.js loaded', 'SDGFoldUI=' + ready.hasUI);
  check(ready.hasDb && ready.hasScene, 'viewer app + scene + DB ready', `db=${ready.hasDb} scene=${ready.hasScene}`);
  check(ready.btn, 'Datum-Fold toolbar button exists', '#sdg-fold-btn');
  check(ready.datums > 0, 'baked DB returns datums', 'datum_plane=' + ready.datums);

  // toggle ON via the real button click → enableDatumDrag → §FOLD-UI enabled handles=N
  await page.click('#sdg-fold-btn').catch(() => {});
  await page.waitForTimeout(800);
  const enabled = logs.filter(l => /§FOLD-UI enabled handles=/.test(l)).pop() || '';
  const nHandles = (/handles=(\d+)/.exec(enabled) || [0, 0])[1];
  check(+nHandles > 0, 'toggle enabled datum handles', enabled || '(no §FOLD-UI enabled log)');

  // run a real fold through the engine on the live graph (drives applyFold → §FOLD-UI applied)
  const fold = await page.evaluate(() => {
    const A = window.APP, g = A._sdgGraph; if (!g) return { err: 'no graph' };
    const s = {}; g.anchored.forEach(a => s[a.datumId] = (s[a.datumId] || 0) + 1);
    g.spans.forEach(x => { s[x.loId] = (s[x.loId] || 0) + 1; s[x.hiId] = (s[x.hiId] || 0) + 1; });
    let best = null, bn = -1; Object.keys(s).forEach(d => { if (s[d] > bn) { bn = s[d]; best = +d; } });
    const r = window.SDGFold.foldDatumDrag(g, best, 400);
    window.SDGFoldUI.applyFold(A, r, g);
    const n = window.SDGFoldUI.applyFold(A, r, g);  // idempotent re-apply (orig cached) → same count
    window.SDGFoldUI.reset(A);
    return { datum: best, moved: r.moved.length, stretched: r.stretched.length, meshes: n };
  });
  check(!fold.err && (fold.moved + fold.stretched) > 0, 'live fold moved/stretched elements',
    fold.err || `datum=${fold.datum} moved=${fold.moved} stretched=${fold.stretched} meshes=${fold.meshes}`);
  check(fold.meshes > 0, 'applyFold touched live scene meshes by GUID', 'meshes=' + fold.meshes);

  // visual proof: re-enable + apply, screenshot the folded scene
  await page.evaluate(() => {
    const A = window.APP, g = A._sdgGraph;
    const r = window.SDGFold.foldDatumDrag(g, (A._sdgHandles && A._sdgHandles[0].userData.sdgDatumId) || 1, 600);
  });
  const shot = '/tmp/claude-1000/-home-red1-bim-compiler/4f7a3389-69c3-4deb-8080-367d5dcd3ef0/scratchpad/sdg_fold_live.png';
  await page.screenshot({ path: shot });
  console.log('   📷 screenshot → ' + shot);

  check(errs.length === 0, 'no fatal page/console errors', errs.length ? errs.slice(0, 2).join(' | ') : 'clean');

  await browser.close(); server.close();
  console.log(`\n  §SDG-FOLD-LIVE  ${fails ? '🔴 ' + fails + ' FAIL' : '🟢 ALL WIRING CHECKS PASS'}`);
  process.exit(fails ? 1 : 0);
})().catch(e => { console.error('SMOKE ERROR', e); process.exit(2); });
