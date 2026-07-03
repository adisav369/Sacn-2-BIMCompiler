// W-KERNEL-FOLD harness — prompts/BONSAI_KERNEL_RESEARCH.md §5 Item 1 (GO/NO-GO).
// CLAIM: a parametric feature stored as a kernel_ops-shaped ROW regenerates IDENTICAL geometry
// when REPLAYED through the reused occt-wasm kernel => geometry = deterministic FOLD over the op-log.
//
// occt-wasm needs WASM tail-calls (V8 11.2+); Node18 = V8 10.2 CANNOT host it, so we drive the page
// in puppeteer's Chromium (matches the repo _live witness pattern). occt-wasm is single-thread
// (0 SharedArrayBuffer refs) => NO COOP/COEP required (=> runs on GH Pages too).
// Run: node build/kernel/poc_kernel_fold.js
const http = require('http'), fs = require('fs'), path = require('path');
const REPO = path.join(process.env.HOME, 'bim-compiler');
const puppeteer = require(path.join(REPO, 'node_modules', 'puppeteer'));
const ROOT = REPO;   // serve repo root so /node_modules/occt-wasm/... and /build/kernel/spike.html resolve
const MIME = { '.html':'text/html', '.js':'text/javascript', '.mjs':'text/javascript',
  '.wasm':'application/wasm', '.json':'application/json', '.css':'text/css' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(q.url.split('?')[0]); if (p === '/') p = '/build/kernel/spike.html';
  fs.readFile(path.join(ROOT, p), (e, b) => {
    if (e) { r.writeHead(404); r.end('404'); return; }
    r.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream' }); r.end(b);
  });
});
(async () => {
  await new Promise(r => server.listen(0, r)); const port = server.address().port;
  const br = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox'] });
  const pg = await br.newPage();
  const logs = [];
  pg.on('console', m => { const t = m.text(); if (/^§KERNEL/.test(t)) { logs.push(t); console.log('  ' + t); } });
  pg.on('pageerror', e => console.log('  ERR ' + String(e).slice(0, 300)));
  await pg.goto(`http://localhost:${port}/build/kernel/spike.html`, { waitUntil: 'load', timeout: 60000 });
  await pg.waitForFunction('window.__done === true', { timeout: 90000 }).catch(() => console.log('  ✗ timeout waiting for __done'));
  await br.close(); server.close();
  const verdict = logs.find(t => t.startsWith('§KERNEL-FOLD VERDICT'));
  const pass = verdict && verdict.includes('PASS');
  console.log('── W-KERNEL-FOLD ' + (pass ? 'PASS' : 'FAIL') + ' ──');
  process.exit(pass ? 0 : 1);
})();
