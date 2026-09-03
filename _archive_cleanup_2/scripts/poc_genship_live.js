// ⚠ DO NOT REMOVE — W-PROC-SHIP-LIVE (AD_PROCESS_FOLD_LANE.md §P2). Prove idempiere.html dispatches the
//   InOutGenerate process (AD_Process 118) live through the W-PROC spine + the KIND-2 handler:
//   1. _ensureProcHandlers registers org.compiere.process.InOutGenerate (handlers list + engine=Y).
//   2. ?process=118 deep link (procSet-gated) opens the §GENSHIP-LIVE ORDER PICKER folded from real CO Sales
//      Orders, defaulting M_Warehouse_ID (a mandatory para) from the chosen order.
//   3. Running on a served CO order (all fully delivered in this seed) → the handler honestly yields
//      "@Created@ = 0 (nothing to deliver)": dispatched=Y ok=Y, NO shipment table, NO fabricated M_InOut.
//   4. The positive op-group fold (MovementQty = min(toDeliver, onHand), Availability cap) is proven HEADLESS +
//      oracle-equivalent in W-PROC-SHIP (poc_proc_inout.js); the seed's CO orders are fully delivered, so the
//      only HONEST live outcome here is the empty result — logged, not faked (no silent cap).
// §-log first. Run: ERP_ROOT=/tmp/wt-ship/erp node scripts/poc_genship_live.js  (default ROOT=~/bim-ootb/erp)
const { chromium } = require(process.env.HOME + '/bim-ootb/tests/node_modules/playwright');
const http = require('http'), fs = require('fs'), path = require('path');
const ROOT = process.env.ERP_ROOT || path.join(process.env.HOME, 'bim-ootb', 'erp');
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.db': 'application/octet-stream', '.wasm': 'application/wasm', '.css': 'text/css' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(q.url.split('?')[0]); if (p === '/') p = '/idempiere.html';
  fs.readFile(path.join(ROOT, p), (e, b) => { if (e) { r.writeHead(404); r.end('404'); return; } r.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream' }); r.end(b); });
});
(async () => {
  await new Promise(r => server.listen(0, r)); const port = server.address().port;
  const br = await chromium.launch(); const pg = await br.newContext().then(c => c.newPage());
  const logs = []; let pageErr = 0;
  pg.on('console', m => { const t = m.text(); if (/^(§AD-PROC-LIVE|§GENSHIP-LIVE)/.test(t)) { logs.push(t); console.log('  ' + t); } });
  pg.on('pageerror', e => { pageErr++; console.log('ERR ' + String(e).slice(0, 160)); });
  let pass = true; const fail = (m) => { pass = false; console.log('  ✗ FAIL: ' + m); };

  console.log('— GenerateShipment (AD_Process 118, InOutGenerate) via ?process=118 deep link, GardenAdmin');
  await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenAdmin&process=118`, { waitUntil: 'networkidle' });

  // 1. handler registered + engine wired
  await pg.waitForSelector('select[data-genship-order]', { timeout: 25000 }).catch(() => fail('order picker never rendered'));
  const hl = logs.find(t => t.startsWith('§AD-PROC-LIVE handlers='));
  if (!hl || !/InOutGenerate/.test(hl)) fail('InOutGenerate not in registered handlers: ' + hl);
  if (!hl || !/engine=Y/.test(hl)) fail('ERPEngine not wired: ' + hl);

  // 2. picker folded from REAL CO Sales Orders
  const opts = await pg.$$eval('select[data-genship-order] option', os => os.map(o => ({ v: o.value, wh: o.getAttribute('data-wh') })).filter(o => o.v));
  if (!opts.length) fail('order picker has no completed Sales Orders');
  else console.log('  §GENSHIP-LIVE picker CO Sales Orders=' + opts.length + ' (first wh=' + opts[0].wh + ')');

  // 3. run on the first CO order → honest empty (fully delivered), no fabricated shipment
  await pg.selectOption('select[data-genship-order]', opts[0].v);
  await pg.click('button[data-proc-run]');
  await pg.waitForSelector('.idmp-procresult', { timeout: 8000 }).catch(() => fail('no result card after Generate Shipment'));
  const l118 = logs.find(t => t.startsWith('§AD-PROC-LIVE proc=118'));
  if (!l118 || !/classname=org\.compiere\.process\.InOutGenerate/.test(l118)) fail('proc 118 did not dispatch to InOutGenerate: ' + l118);
  if (!l118 || !/dispatched=Y ok=Y/.test(l118)) fail('proc 118 did not dispatch ok (mandatory warehouse para missing?): ' + l118);
  if (!l118 || !/rows=0/.test(l118)) fail('expected 0 shipment rows on a fully-delivered order: ' + l118);
  const chip = await pg.$eval('.idmp-procresult .ok-chip', e => e.textContent).catch(() => '');
  if (chip !== 'OK') fail('result chip not "OK" (got "' + chip + '")');
  const msg = await pg.$$eval('.idmp-procresult .msg', es => es.map(e => e.textContent).join(' | ')).catch(() => '');
  if (!/nothing to deliver|@Created@ = 0/.test(msg)) fail('honest empty message missing: "' + msg + '"');
  else console.log('  §GENSHIP-LIVE honest empty: "' + msg + '"');
  const tableRows = await pg.$$eval('.idmp-procresult table tr', rs => rs.length).catch(() => 0);
  if (tableRows > 0) fail('a fabricated shipment table was rendered for a fully-delivered order (HONESTY INVARIANT breach)');
  if (await pg.$('.idmp-procresult.absent')) fail('rejection wrongly rendered as absent-handler (it dispatched + ran)');

  if (pageErr) fail(pageErr + ' pageerror(s)');
  console.log('  §GENSHIP-LIVE seed-limit: served CO orders are fully delivered → positive shipment fold proven headless/oracle-equivalent (W-PROC-SHIP); live shows the honest empty result.');
  console.log(pass
    ? '🟢 W-PROC-SHIP-LIVE PASS — proc 118 dispatches via the W-PROC spine to the KIND-2 InOutGenerate handler; the picker folds real CO Sales Orders; a fully-delivered order honestly yields 0 lines (no fabricated shipment), 0 pageerrors'
    : '🔴 W-PROC-SHIP-LIVE FAIL');
  await br.close(); server.close(); process.exit(pass ? 0 : 2);
})().catch(e => { console.log('🔴 ' + e.message); process.exit(1); });
