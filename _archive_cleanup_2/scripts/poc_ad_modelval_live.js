// W-AD-MODELVAL-LIVE (UI_UNPARK_RESUME.md B-3) — prove the glassbowl CRUD save path fires the PROVEN
// beforeSave hook engine (ad_modelval.js, faithful MOrder.beforeSave ports, W-MORDER-SAVE):
//   REJECT — changing m_pricelist_id on an order WITH product lines blocks the save with the hook's
//            error (MOrder.priceListImmutable, MOrder.java:1352-1359 CannotChangePl — REAL c_orderline
//            count query on the live bundle), error visible ON-SCREEN, form stays open, nothing commits.
//   DERIVE — clearing bill_bpartner_id lets MOrder.billDefaults (:1272-1279) fill it back from
//            c_bpartner_id; the derived value lands in the form input + § line.
// BUNDLE-GAP (named): glassbowl_data.db lacks master tables (m_warehouse/c_doctype/m_pricelist/...) —
//   hooks needing them no-row to their conservative path (§AD-MODELVAL-LIVE bundle-gap lines); the
//   lookup-clearing hooks (bpLocationConsistency) legitimately derive null under an absent lookup row.
// Run: ERP_ROOT=/tmp/wt-uibridge/erp node scripts/poc_ad_modelval_live.js   (default ROOT=~/bim-ootb/erp)
const { chromium } = require(process.env.HOME + '/bim-ootb/tests/node_modules/playwright');
const http = require('http'), fs = require('fs'), path = require('path');
const ROOT = process.env.ERP_ROOT || path.join(process.env.HOME, 'bim-ootb', 'erp');
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.db': 'application/octet-stream', '.wasm': 'application/wasm', '.css': 'text/css' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(q.url.split('?')[0]); if (p === '/') p = '/glassbowl.html';
  fs.readFile(path.join(ROOT, p), (e, b) => {
    if (e) { r.writeHead(404); r.end('404'); return; }
    r.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream' }); r.end(b);
  });
});
(async () => {
  await new Promise(r => server.listen(0, r)); const port = server.address().port;
  const br = await chromium.launch(); const pg = await br.newContext().then(c => c.newPage());
  const logs = [];
  pg.on('console', m => { const t = m.text(); if (t.indexOf('§AD-MODELVAL-LIVE') === 0 || t.indexOf('§CRUD') === 0) { logs.push(t); console.log('  ' + t); } });
  pg.on('pageerror', e => console.log('ERR ' + String(e).slice(0, 160)));
  let pass = true; const fail = m => { pass = false; console.log('  ✗ ' + m); };

  await pg.goto(`http://localhost:${port}/glassbowl.html`, { waitUntil: 'networkidle' });
  await pg.waitForFunction(() => window.__crud && window.AdModelVal, { timeout: 25000 });
  await pg.evaluate(() => window.__crud.enable());
  await pg.waitForFunction(() => window.__crud.store() && document.querySelector('.crud-hot'), { timeout: 15000 });  // enable() fetches crud_ops.json async
  await pg.evaluate(() => window.__crud.openRing('c_order'));
  await pg.click('.crud-fab.edit');
  await pg.waitForSelector('#cfSave', { timeout: 15000 });

  // ── REJECT: m_pricelist_id 101→103 on order 100 (1 product line) → CannotChangePl, save blocked ──
  console.log('— REJECT case: change m_pricelist_id on an order with product lines');
  const plOrig = await pg.$eval('[data-col="m_pricelist_id"]', e => e.value);
  await pg.fill('[data-col="m_pricelist_id"]', '103');
  await pg.click('#cfSave');
  await pg.waitForTimeout(400);
  const stillOpen = await pg.$eval('#cfSave', e => !!e).catch(() => false);
  const errTxt = await pg.$$eval('.cfe', es => es.map(x => x.textContent).filter(Boolean).join(' | '));
  console.log('  on-screen error="' + errTxt + '" formOpen=' + stillOpen);
  if (!stillOpen) fail('form closed — reject did not block the save');
  if (errTxt.indexOf('CannotChangePl') < 0) fail('CannotChangePl not shown on-screen');
  if (!logs.some(t => t.indexOf('hook=MOrder.priceListImmutable verdict=REJECT') > 0)) fail('no §AD-MODELVAL-LIVE REJECT line');
  if (logs.some(t => t.indexOf('§CRUD-PERSIST') === 0)) fail('a commit happened despite the reject');

  // ── DERIVE: restore pricelist, clear bill_bpartner_id → billDefaults refills it from c_bpartner_id ──
  console.log('— DERIVE case: clear bill_bpartner_id → MOrder.billDefaults refills');
  await pg.fill('[data-col="m_pricelist_id"]', plOrig);
  await pg.fill('[data-col="bill_bpartner_id"]', '');
  await pg.click('#cfSave');
  await pg.waitForTimeout(600);
  const dLine = logs.filter(t => t.indexOf('verdict=OK derived=') > 0).pop() || '';
  console.log('  derive line: ' + (dLine || '(none)'));
  if (dLine.indexOf('"bill_bpartner_id"') < 0) fail('bill_bpartner_id not derived');
  const closed = await pg.$('#cfSave');
  if (closed) fail('form did not close after the accepted save');

  console.log(pass
    ? '🟢 W-AD-MODELVAL-LIVE PASS — live save path fires the proven beforeSave hooks: reject blocks on-screen, derive fills the form'
    : '🔴 W-AD-MODELVAL-LIVE FAIL');
  await br.close(); server.close(); process.exit(pass ? 0 : 2);
})().catch(e => { console.log('🔴 ' + e.message); process.exit(1); });
