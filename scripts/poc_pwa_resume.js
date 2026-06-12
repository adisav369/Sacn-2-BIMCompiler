// ⚠ DO NOT REMOVE — Scope guard (user live-test 2026-06-12: "WH does not have the DB"). Read the log after every run.
// poc_pwa_resume.js — W-PWA-RESUME: an OCI-era pwa_last_db (bucket copy deleted when building dbs
// moved to GH Pages) must NOT brick a bare viewer open.
// ISSUES IT PROVES: 1. §PWA_RESUME of a dead URL → §PWA_RESUME_CLEAR (key cleansed) → ONE redirect to
// the landing ../index.html (the only door that knows where every building db lives). 2. §FALSIFIER:
// an EXPLICIT ?db= 404 stays on the viewer showing the error (recovery only touches RESUMED opens).
// Run: ROOT=<worktree> node scripts/poc_pwa_resume.js  (default /tmp/wt-posfix)
const puppeteer = require('puppeteer');
const http = require('http'), fs = require('fs'), path = require('path');
const ROOT = process.env.ROOT || '/tmp/wt-posfix';
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.db': 'application/octet-stream', '.wasm': 'application/wasm', '.css': 'text/css', '.png': 'image/png' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(q.url.split('?')[0]);
  fs.readFile(path.join(ROOT, p), (e, b) => {
    if (e) { r.writeHead(404); r.end('404'); return; }
    r.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream' }); r.end(b);
  });
});
(async () => {
  await new Promise(r => server.listen(0, r)); const port = server.address().port;
  const br = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox', '--disable-dev-shm-usage'] });
  const pg = await br.newPage();
  const logs = [];
  pg.on('console', m => { const t = m.text(); if (/§(PWA|INIT)/.test(t)) { logs.push(t); console.log('  | ' + t.slice(0, 130)); } });
  await pg.goto(`http://localhost:${port}/viewer/viewer.html?db=nonexistent_seed_probe.db`, { waitUntil: 'domcontentloaded' });
  await pg.evaluate(() => localStorage.setItem('pwa_last_db', 'https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb/o/buildings/warehouse_gardenworld.db'));
  await pg.goto(`http://localhost:${port}/viewer/viewer.html`, { waitUntil: 'domcontentloaded' });
  await new Promise(r => setTimeout(r, 15000));
  const href = await pg.evaluate(() => location.pathname);
  const key = await pg.evaluate(() => localStorage.getItem('pwa_last_db'));
  const sawClear = logs.some(t => t.includes('§PWA_RESUME_CLEAR'));
  const landed = /index\.html$|^\/$/.test(href) || href === '/index.html';
  console.log('§PWA-RESUME-PROBE sawClear=' + sawClear + ' landedAt=' + href + ' keyAfter=' + (key === null ? 'cleansed' : key));
  // falsifier: explicit ?db= failure must NOT redirect (error stays visible)
  await pg.goto(`http://localhost:${port}/viewer/viewer.html?db=nonexistent_seed_probe.db`, { waitUntil: 'domcontentloaded' });
  await new Promise(r => setTimeout(r, 8000));
  const href2 = await pg.evaluate(() => location.pathname);
  const stayed = /viewer\.html$/.test(href2);
  console.log('§PWA-RESUME-FALSIFIER explicit-?db=-404 stays-on-viewer=' + stayed);
  const pass = sawClear && landed && key === null && stayed;
  console.log(pass ? '🟢 W-PWA-RESUME PASS' : '🔴 W-PWA-RESUME FAIL');
  await br.close(); server.close();
  process.exit(pass ? 0 : 1);
})().catch(e => { console.error('FATAL', e); process.exit(1); });
