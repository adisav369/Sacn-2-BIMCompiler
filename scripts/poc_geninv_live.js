// ⚠ DO NOT REMOVE — W-PROC-INV-LIVE (AD_PROCESS_FOLD_LANE.md §P2-leg2). Prove idempiere.html dispatches the
//   InvoiceGenerate process (AD_Process 119) live through the W-PROC spine + the KIND-2 handler:
//   1. _ensureProcHandlers registers org.compiere.process.InvoiceGenerate (handlers list + engine=Y).
//   2. ?process=119 deep link (procSet-gated) opens the §GENINV-LIVE ORDER PICKER folded from real CO/CL Sales
//      Orders, supplying AD_Org_ID (a mandatory para) from the chosen order + DocAction=Complete.
//   3. Running on a served CO order (all fully invoiced in this seed) → the handler honestly yields
//      "@Created@ = 0 (nothing to invoice)": dispatched=Y ok=Y, NO invoice table, NO fabricated C_Invoice.
//   4. The positive op-group fold (QtyInvoiced = QtyOrdered − QtyInvoiced, Immediate rule) is proven HEADLESS +
//      oracle-equivalent in W-PROC-INV (poc_proc_inv.js); the seed's CO orders are fully invoiced, so the only
//      HONEST live outcome here is the empty result — logged, not faked (no silent fold).
// §-log first. Run: ERP_ROOT=/tmp/wt-inv/erp node scripts/poc_geninv_live.js  (default ROOT=~/bim-ootb/erp)
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
  pg.on('console', m => { const t = m.text(); if (/^(§AD-PROC-LIVE|§GENINV-LIVE)/.test(t)) { logs.push(t); console.log('  ' + t); } });
  pg.on('pageerror', e => { pageErr++; console.log('ERR ' + String(e).slice(0, 160)); });
  let pass = true; const fail = (m) => { pass = false; console.log('  ✗ FAIL: ' + m); };

  console.log('— GenerateInvoice (AD_Process 119, InvoiceGenerate) via ?process=119 deep link, GardenAdmin');
  await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenAdmin&process=119`, { waitUntil: 'networkidle' });

  // 1. handler registered + engine wired
  await pg.waitForSelector('select[data-geninv-order]', { timeout: 25000 }).catch(() => fail('order picker never rendered'));
  const hl = logs.find(t => t.startsWith('§AD-PROC-LIVE handlers='));
  if (!hl || !/InvoiceGenerate/.test(hl)) fail('InvoiceGenerate not in registered handlers: ' + hl);
  if (!hl || !/engine=Y/.test(hl)) fail('ERPEngine not wired: ' + hl);

  // 2. picker folded from REAL CO/CL Sales Orders
  const opts = await pg.$$eval('select[data-geninv-order] option', os => os.map(o => ({ v: o.value, org: o.getAttribute('data-org') })).filter(o => o.v));
  if (!opts.length) fail('order picker has no completed Sales Orders');
  else console.log('  §GENINV-LIVE picker CO/CL Sales Orders=' + opts.length + ' (first org=' + opts[0].org + ')');

  // 3. run on the first CO order → honest empty (fully invoiced), no fabricated invoice
  await pg.selectOption('select[data-geninv-order]', opts[0].v);
  await pg.click('button[data-proc-run]');
  await pg.waitForSelector('.idmp-procresult', { timeout: 8000 }).catch(() => fail('no result card after Generate Invoice'));
  const l119 = logs.find(t => t.startsWith('§AD-PROC-LIVE proc=119'));
  if (!l119 || !/classname=org\.compiere\.process\.InvoiceGenerate/.test(l119)) fail('proc 119 did not dispatch to InvoiceGenerate: ' + l119);
  if (!l119 || !/dispatched=Y ok=Y/.test(l119)) fail('proc 119 did not dispatch ok (mandatory AD_Org_ID/DocAction para missing?): ' + l119);
  if (!l119 || !/rows=0/.test(l119)) fail('expected 0 invoice rows on a fully-invoiced order: ' + l119);
  const chip = await pg.$eval('.idmp-procresult .ok-chip', e => e.textContent).catch(() => '');
  if (chip !== 'OK') fail('result chip not "OK" (got "' + chip + '")');
  const msg = await pg.$$eval('.idmp-procresult .msg', es => es.map(e => e.textContent).join(' | ')).catch(() => '');
  if (!/nothing to invoice|@Created@ = 0/.test(msg)) fail('honest empty message missing: "' + msg + '"');
  else console.log('  §GENINV-LIVE honest empty: "' + msg + '"');
  const tableRows = await pg.$$eval('.idmp-procresult table tr', rs => rs.length).catch(() => 0);
  if (tableRows > 0) fail('a fabricated invoice table was rendered for a fully-invoiced order (HONESTY INVARIANT breach)');
  if (await pg.$('.idmp-procresult.absent')) fail('rejection wrongly rendered as absent-handler (it dispatched + ran)');

  if (pageErr) fail(pageErr + ' pageerror(s)');
  console.log('  §GENINV-LIVE seed-limit: served CO orders are fully invoiced → positive invoice fold proven headless/oracle-equivalent (W-PROC-INV); live shows the honest empty result.');
  console.log(pass
    ? '🟢 W-PROC-INV-LIVE PASS — proc 119 dispatches via the W-PROC spine to the KIND-2 InvoiceGenerate handler; the picker folds real CO/CL Sales Orders; a fully-invoiced order honestly yields 0 lines (no fabricated invoice), 0 pageerrors'
    : '🔴 W-PROC-INV-LIVE FAIL');
  await br.close(); server.close(); process.exit(pass ? 0 : 2);
})().catch(e => { console.log('🔴 ' + e.message); process.exit(1); });
