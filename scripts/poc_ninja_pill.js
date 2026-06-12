// W-NINJA-PILL (NinjaExcelAdaptation.md §6.2 browser pill) — prove the NinjaExcel lens rides the rails
// LIVE on idempiere.html (VALUES are proven by the headless W-NINJA/W-NINJA-MAKE/W-NINJA-RULE witnesses;
// this is the wiring check, per TestArchitecture §Browser Testing):
//   1. GATE-IN — after GardenAdmin login the 'Excel Report' pill is ON the bar (#pill-ninja,
//      pills_idmp.json id=ninja, grid glyph); §NINJA-PILL loaded.
//   2. OPEN — pill pointerup mounts the lens panel (#ninja-panel) over the REAL loaded tenant db:
//      §NINJA-PILL open db=Y engine=Y; SheetJS lazy-loads on first use (§NINJA-PILL xlsx=loaded —
//      NOT on the page hot path).
//   3. RUN — the shipped ninja_sample.xlsx (generated from ad_seed.db STORED columns) folds over the
//      page's sql.js db: 9 cells, verify-by-example PASS maxDiff=0c vs its BACKUP sample, download
//      link rendered. ISSUE PROVED: the engine's injected-executor seam really is host-agnostic
//      (better-sqlite3 headless == sql.js in-page on the same data).
//   4. MAKE — a BACKUP-only workbook (built in-page from the sample) scaffolds Input+Process:
//      §NINJA-PILL make tables=1 dynamic=9.
//   5. HONESTY — a workbook with a blanked TABLE cell is REFUSED by the gate in the live UI
//      (§NINJA-PILL gate=REFUSED), never silently skipped.
// Run: ERP_ROOT=/tmp/wt-ninja/erp node scripts/poc_ninja_pill.js  (default ROOT=~/bim-ootb/erp)
const { chromium } = require(process.env.HOME + '/bim-ootb/tests/node_modules/playwright');
const http = require('http'), fs = require('fs'), path = require('path');
const ROOT = process.env.ERP_ROOT || path.join(process.env.HOME, 'bim-ootb', 'erp');
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.db': 'application/octet-stream', '.wasm': 'application/wasm', '.css': 'text/css', '.xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(q.url.split('?')[0]); if (p === '/') p = '/idempiere.html';
  fs.readFile(path.join(ROOT, p), (e, b) => {
    if (e) { r.writeHead(404); r.end('404'); return; }
    r.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream' }); r.end(b);
  });
});
(async () => {
  await new Promise(r => server.listen(0, r)); const port = server.address().port;
  const br = await chromium.launch(); const pg = await br.newContext().then(c => c.newPage());
  const logs = [];
  pg.on('console', m => { const t = m.text(); if (/^§NINJA/.test(t)) { logs.push(t); console.log('  ' + t); } });
  pg.on('pageerror', e => console.log('ERR ' + String(e).slice(0, 200)));
  let pass = true;
  const fail = (m) => { pass = false; console.log('  ✗ ' + m); };

  // ── 1. login → pill on the bar ──
  console.log('— 1. GardenAdmin login: Excel Report pill on the bar');
  await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenAdmin`, { waitUntil: 'networkidle' });
  await pg.waitForSelector('#idmp-tree .idmp-row.leaf', { state: 'attached', timeout: 25000 });
  if (!logs.find(t => t.startsWith('§NINJA-PILL loaded'))) fail('ninja_pill.js not loaded');
  if (!(await pg.$('#pill-ninja'))) fail('#pill-ninja not on the bar');

  // ── 2. open the lens (registry pills fire on pointerup; open the dock if collapsed) ──
  console.log('— 2. open the lens over the loaded tenant db');
  await pg.evaluate(() => {
    const dock = document.getElementById('idmp-pill'), trig = document.querySelector('#idmp-pill-trigger,[data-pill-trigger]');
    if (dock && trig && getComputedStyle(dock).display === 'none') trig.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    document.getElementById('pill-ninja').dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
  });
  await pg.waitForSelector('#ninja-panel', { timeout: 10000 }).catch(() => fail('lens panel never mounted'));
  const open = logs.find(t => t.startsWith('§NINJA-PILL open'));
  if (!open || !/db=Y engine=Y/.test(open)) fail('open line wrong: ' + open);

  // ── 3. RUN the shipped sample over the page's sql.js db (the seam-equality check) ──
  console.log('— 3. RUN ninja_sample.xlsx → verify-by-example vs its BACKUP (sql.js == better-sqlite3 seam)');
  const run = await pg.evaluate(async () => {
    const buf = await (await fetch('ninja_sample.xlsx')).arrayBuffer();
    const r = await window.NinjaPill.handleBuffer(buf, 'ninja_sample.xlsx');
    return { mode: r.mode, cells: r.verify && r.verify.cells, maxDiff: r.verify && r.verify.maxDiffCents,
             strings: r.verify && r.verify.stringMismatches, pass: r.verify && r.verify.pass };
  });
  if (!(run.mode === 'run' && run.cells === 9 && run.maxDiff === 0 && run.strings === 0 && run.pass)) {
    fail('RUN verify wrong: ' + JSON.stringify(run));
  }
  if (!logs.find(t => /§NINJA-PILL xlsx=loaded/.test(t))) fail('SheetJS lazy-load line missing');
  if (!(await pg.$('#np-out a[download]'))) fail('filled-workbook download link not rendered');

  // ── 4. MAKE from a BACKUP-only workbook (built in-page from the sample) ──
  console.log('— 4. MAKE: BACKUP-only sample → Input+Process scaffold');
  const make = await pg.evaluate(async () => {
    const buf = await (await fetch('ninja_sample.xlsx')).arrayBuffer();
    const full = XLSX.read(buf, { type: 'array' });
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, full.Sheets.BACKUP, 'BACKUP');
    const bytes = XLSX.write(wb, { type: 'array', bookType: 'xlsx' });
    const r = await window.NinjaPill.handleBuffer(bytes, 'backup_only.xlsx');
    return { mode: r.mode, tables: r.analysis && r.analysis.tables.length, dynamic: r.analysis && r.analysis.dynamicCells.length };
  });
  if (!(make.mode === 'make' && make.tables === 1 && make.dynamic === 9)) fail('MAKE wrong: ' + JSON.stringify(make));

  // ── 5. HONESTY: blanked TABLE cell → live gate refusal ──
  console.log('— 5. gate refusal in the live UI (blanked TABLE)');
  const refused = await pg.evaluate(async () => {
    const buf = await (await fetch('ninja_sample.xlsx')).arrayBuffer();
    const wb = XLSX.read(buf, { type: 'array' });
    delete wb.Sheets.Process.D2;                                       // first row loses its TABLE
    const bytes = XLSX.write(wb, { type: 'array', bookType: 'xlsx' });
    const r = await window.NinjaPill.handleBuffer(bytes, 'broken.xlsx');
    return { mode: r.mode, ok: r.gate && r.gate.ok, errors: r.gate && r.gate.errors.length };
  });
  if (!(refused.mode === 'run' && refused.ok === false && refused.errors >= 1)) fail('gate refusal wrong: ' + JSON.stringify(refused));
  if (!logs.find(t => /§NINJA-PILL gate=REFUSED/.test(t))) fail('gate=REFUSED line missing');

  console.log(pass ? '\n🟢 W-NINJA-PILL PASS — pill on bar, lens opens, sample RUNs 0c on sql.js, MAKE scaffolds, gate refuses live.'
                   : '\n🔴 W-NINJA-PILL FAIL');
  await br.close(); server.close();
  process.exit(pass ? 0 : 1);
})();
