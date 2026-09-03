// W-KERNEL-WEBIFC harness — prompts/BONSAI_KERNEL_RESEARCH.md §3 spine / §4 #3 / §RESUME edge-probe.
// web-ifc round-trip: a REAL wall (reference/residential/Ifc4_WallElementedCase.ifc, the Drywall panel of
// "Wall #1") re-authored as ONE kernel_ops op-row -> written to IFC4 -> re-imported -> IfcExtrudedAreaSolid
// read back == source (XDim/YDim/Depth/dir). Driven through puppeteer Chromium to match the established
// build/kernel/poc_kernel_* pattern (and dodge Node18 V8/WASM surprises).
// Run: node build/kernel/poc_kernel_webifc.js   (log: tee build/kernel/poc_kernel_webifc.log)
const http = require('http'), fs = require('fs'), path = require('path');
const REPO = path.join(process.env.HOME, 'bim-compiler');
const puppeteer = require(path.join(REPO, 'node_modules', 'puppeteer'));
const MIME = { '.html':'text/html', '.js':'text/javascript', '.mjs':'text/javascript',
  '.wasm':'application/wasm', '.json':'application/json', '.css':'text/css' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(q.url.split('?')[0]); if (p === '/') p = '/build/kernel/spike_webifc.html';
  fs.readFile(path.join(REPO, p), (e, b) => {
    if (e) { r.writeHead(404); r.end('404'); return; }
    r.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream' }); r.end(b);
  });
});
(async () => {
  await new Promise(r => server.listen(0, r)); const port = server.address().port;
  const br = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox'] });
  const pg = await br.newPage();
  const logs = [];
  pg.on('console', m => { const t = m.text(); if (/^§KERNEL-WEBIFC/.test(t)) { logs.push(t); console.log('  ' + t); } });
  pg.on('pageerror', e => console.log('  ERR ' + String(e).slice(0, 300)));
  await pg.goto(`http://localhost:${port}/build/kernel/spike_webifc.html`, { waitUntil: 'load', timeout: 60000 });
  await pg.waitForFunction('window.__done === true', { timeout: 90000 }).catch(() => console.log('  ✗ timeout waiting for __done'));
  await br.close(); server.close();
  const verdict = logs.find(t => t.startsWith('§KERNEL-WEBIFC VERDICT'));
  const pass = verdict && verdict.includes('PASS');
  console.log('── W-KERNEL-WEBIFC ' + (pass ? 'PASS' : 'FAIL') + ' ──');
  process.exit(pass ? 0 : 1);
})();
