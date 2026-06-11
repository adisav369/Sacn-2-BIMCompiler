// §SIZE-GATE probe (IDMP_FULLWIDTH_SEED §1) — first-load `§IDEMPIERE boot ms` + seed bytes,
// old slice vs full-width seed. Fresh Playwright context each run = NO IndexedDB/SW cache,
// i.e. the FIRST-LOAD path (steady-state loads come from the IDB key either way).
// Run: ERP_ROOT=<root-with-target-seed> SEED_TAG=<label> node build/erp/poc_seed_bootms.js
const { chromium } = require(process.env.HOME + '/bim-ootb/tests/node_modules/playwright');
const http = require('http'), fs = require('fs'), path = require('path');
const ROOT = process.env.ERP_ROOT, TAG = process.env.SEED_TAG || '?';
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.db': 'application/octet-stream', '.wasm': 'application/wasm', '.css': 'text/css' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(q.url.split('?')[0]); if (p === '/') p = '/idempiere.html';
  fs.readFile(path.join(ROOT, p), (e, b) => {
    if (e) { r.writeHead(404); r.end('404'); return; }
    r.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream' }); r.end(b);
  });
});
(async () => {
  await new Promise(r => server.listen(0, r)); const port = server.address().port;
  const bytes = fs.statSync(path.join(ROOT, 'ad_seed.db')).size;
  const runs = [];
  for (let i = 0; i < 3; i++) {
    const br = await chromium.launch(); const pg = await br.newContext().then(c => c.newPage());
    const ms = await new Promise((resolve) => {
      pg.on('console', m => { const t = m.text(); const x = /§IDEMPIERE boot db=(\S+) ms=(\d+)/.exec(t); if (x) resolve({ src: x[1], ms: +x[2] }); });
      pg.goto(`http://localhost:${port}/idempiere.html`, { waitUntil: 'networkidle' }).catch(() => {});
      setTimeout(() => resolve(null), 30000);
    });
    await br.close();
    if (ms) runs.push(ms);
  }
  const med = runs.map(r => r.ms).sort((a, b) => a - b)[Math.floor(runs.length / 2)];
  console.log('§SIZE-GATE seed=' + TAG + ' bytes=' + bytes + ' (' + (bytes / 1048576).toFixed(1) + 'MB) firstload-boot-ms=[' +
    runs.map(r => r.ms).join(',') + '] median=' + med + ' src=' + (runs[0] ? runs[0].src : 'none'));
  server.close(); process.exit(runs.length ? 0 : 1);
})().catch(e => { console.log('🔴 ' + e.message); process.exit(1); });
