// W-KERNEL-MANIFOLD harness — prompts/BONSAI_KERNEL_RESEARCH.md §4 hard-part #2 "Boolean robustness".
// Manifold (Apache-2.0) coincident-face subtraction vs OCCT cut on the SAME input. Manifold must yield
// a valid closed 2-manifold (status=NoError, genus=0, Euler-closed) = the documented robust BOP fallback.
// Node 18's V8 is too old for the occt WASM tail-calls, so we drive Chromium via puppeteer (like the
// other kernel witnesses). Run: node build/kernel/poc_kernel_manifold.js
const http = require('http'), fs = require('fs'), path = require('path');
const REPO = path.join(process.env.HOME, 'bim-compiler');
const puppeteer = require(path.join(REPO, 'node_modules', 'puppeteer'));
const MIME = { '.html':'text/html', '.js':'text/javascript', '.mjs':'text/javascript',
  '.wasm':'application/wasm', '.json':'application/json', '.css':'text/css' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(q.url.split('?')[0]); if (p === '/') p = '/build/kernel/spike_manifold.html';
  fs.readFile(path.join(REPO, p), (e, b) => {
    if (e) { r.writeHead(404); r.end('404'); return; }
    r.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream' }); r.end(b);
  });
});
(async () => {
  await new Promise(r => server.listen(0, r)); const port = server.address().port;
  const br = await puppeteer.launch({ headless: 'new',
    args: ['--no-sandbox', '--use-gl=angle', '--use-angle=swiftshader',
           '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const pg = await br.newPage();
  const logs = [];
  pg.on('console', m => { const t = m.text(); if (/^§KERNEL-MANIFOLD/.test(t)) { logs.push(t); console.log('  ' + t); } });
  pg.on('pageerror', e => console.log('  ERR ' + String(e).slice(0, 300)));
  await pg.goto(`http://localhost:${port}/build/kernel/spike_manifold.html`, { waitUntil: 'load', timeout: 60000 });
  await pg.waitForFunction('window.__done === true', { timeout: 90000 }).catch(() => console.log('  ✗ timeout waiting for __done'));
  await br.close(); server.close();
  const verdict = logs.find(t => t.startsWith('§KERNEL-MANIFOLD VERDICT'));
  const pass = verdict && verdict.includes('PASS');
  console.log('── W-KERNEL-MANIFOLD ' + (pass ? 'PASS' : 'FAIL') + ' ──');
  process.exit(pass ? 0 : 1);
})();
