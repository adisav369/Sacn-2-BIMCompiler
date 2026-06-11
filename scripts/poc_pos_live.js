// W-POS-LIVE (POS_LENS_SESSION.md / POS_ADDON_SPEC.md §P-1..§P-4 wiring) — prove the POS LENS rides the
// rails LIVE on idempiere.html (values are verified by the headless W-POS-* witnesses; this is the
// wiring check, per TestArchitecture §Browser Testing):
//   1. GATE — after GardenAdmin login the POS pill is ON the bar (showWhen:pos-station via
//      window.IdmpPillPosGate — ad_seed.db carries c_pos 100); §POS-LENS loaded.
//   2. OPEN — pill click mounts the lens: §POS-LIVE open station=100 tiles=16 (all 16 keylayout-100
//      tiles, dictionary-priced, handAuthored=0).
//   3. SALE — ring 2 tiles, pick the walk-in BP (112 Standard), Complete → ONE signed group commits
//      through window.ERP.opDb (kernel_ops.commitGroup): §POS-SALE … newVerbs=[] chainOk=Y; receipt
//      card shows signed=Y.
//   4. REPLENISH — the suggestion panel folds live: §POS-LIVE-REPLENISH suggestions=8 (the W-POS-REPLENISH
//      baseline, == iDempiere formula headless).
//   5. HONESTY — Complete with an EMPTY cart or NO partner refuses (no silent commit; seed
//      c_pos.BPartnerCashTrx is NULL → explicit choice required).
// Run: ERP_ROOT=/tmp/wt-poslens/erp node scripts/poc_pos_live.js  (default ROOT=~/bim-ootb/erp)
const { chromium } = require(process.env.HOME + '/bim-ootb/tests/node_modules/playwright');
const http = require('http'), fs = require('fs'), path = require('path');
const ROOT = process.env.ERP_ROOT || path.join(process.env.HOME, 'bim-ootb', 'erp');
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
  const br = await chromium.launch(); const pg = await br.newContext().then(c => c.newPage());
  const logs = [];
  pg.on('console', m => { const t = m.text(); if (/^(§POS|§KANBAN-HOST|§SEAM)/.test(t)) { logs.push(t); console.log('  ' + t); } });
  pg.on('pageerror', e => console.log('ERR ' + String(e).slice(0, 200)));
  let pass = true;
  const fail = (m) => { pass = false; console.log('  ✗ ' + m); };

  // ── 1+2. login → pill gated ON → open the lens ──
  console.log('— 1. GardenAdmin login: POS pill on the bar (pos-station gate) → open');
  await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenAdmin`, { waitUntil: 'networkidle' });
  await pg.waitForSelector('#idmp-tree .idmp-row.leaf', { state: 'attached', timeout: 25000 });
  if (!logs.find(t => t.startsWith('§POS-LENS loaded'))) fail('pos_lens.js not loaded');
  const pill = await pg.$('#pill-pos');
  if (!pill) fail('POS pill #pill-pos not on the bar (pos-station gate should be TRUE on ad_seed.db)');
  // registry pills fire on pointerup (PR #170 §A chrome — the poc_rule_client_scope pattern); open the dock if collapsed
  await pg.evaluate(() => {
    const dock = document.getElementById('idmp-pill'), trig = document.querySelector('#idmp-pill-trigger,[data-pill-trigger]');
    if (dock && trig && getComputedStyle(dock).display === 'none') trig.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    document.getElementById('pill-pos').dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
  });
  await pg.waitForSelector('.pos-tile', { timeout: 15000 }).catch(() => fail('lens never mounted (no .pos-tile)'));
  const open = logs.find(t => t.startsWith('§POS-LIVE open'));
  if (!open || !/tiles=16/.test(open)) fail('lens open line wrong: ' + open);
  const tiles = await pg.$$eval('.pos-tile', ts => ts.length);
  if (tiles !== 16) fail('expected 16 dictionary tiles, got ' + tiles);

  // ── 5a. honesty first: Complete with EMPTY cart must refuse (no §POS-SALE) ──
  console.log('— 2. empty-cart Complete refuses');
  await pg.click('.pos-complete');
  await pg.waitForTimeout(300);
  if (logs.find(t => t.startsWith('§POS-SALE'))) fail('empty cart committed a sale');

  // ── 3. ring 2 tiles → pick BP 112 → Complete = ONE signed group ──
  console.log('— 3. ring 2 tiles, BP=112(Standard), Complete → signed group');
  await pg.click('.pos-tile[data-pid="124"]');       // Elm Tree, 57.00 (the W-POS-RING product)
  await pg.click('.pos-tile[data-pid="124"]');       // ×2 — same line, qty folds
  const t2 = await pg.$$eval('.pos-tile', ts => ts[0].getAttribute('data-pid'));
  await pg.click(`.pos-tile[data-pid="${t2}"]`);     // a second product line
  // 5b: Complete with NO partner picked must refuse (seed c_pos.BPartnerCashTrx is NULL)
  await pg.click('.pos-complete'); await pg.waitForTimeout(300);
  if (logs.find(t => t.startsWith('§POS-SALE'))) fail('sale committed without a partner');
  await pg.selectOption('.pos-bp', '112');           // Standard — the GardenWorld walk-in
  await pg.click('.pos-complete');
  await pg.waitForFunction(() => document.querySelector('.pos-receipt') && document.querySelector('.pos-receipt').textContent.includes('✓'), null, { timeout: 15000 })
    .catch(() => fail('receipt never rendered'));
  const sale = logs.find(t => t.startsWith('§POS-SALE'));
  if (!sale || !sale.includes('newVerbs=[]') || !sale.includes('chainOk=Y')) fail('sale line wrong: ' + sale);
  if (!logs.find(t => t.startsWith('§POS-DOC order='))) fail('no §POS-DOC completeIt line');
  const rc = await pg.$eval('.pos-receipt', e => e.textContent);
  if (!rc.includes('signed=Y')) fail('receipt not signed: ' + rc);

  // ── 4. the replenishment fold renders live ──
  console.log('— 4. replenishment suggestions fold live');
  const repl = logs.filter(t => t.startsWith('§POS-LIVE-REPLENISH')).pop();
  if (!repl || !/suggestions=8/.test(repl)) fail('replenish fold wrong (want the 8-product W-POS-REPLENISH baseline): ' + repl);
  const replRows = await pg.$eval('.pos-replenish', e => e.textContent);
  if (!replRows.includes('order')) fail('suggestion rows not rendered on-screen');

  console.log('\n' + (pass ? '🟢 W-POS-LIVE PASS — the POS lens rides the rails live: gated pill, 16 dictionary tiles, one signed group (chainOk=Y, newVerbs=[]), live replenishment fold, refusals honest.'
    : '🔴 W-POS-LIVE FAIL'));
  await br.close(); server.close();
  process.exit(pass ? 0 : 1);
})().catch(e => { console.error('FATAL', e); process.exit(1); });
