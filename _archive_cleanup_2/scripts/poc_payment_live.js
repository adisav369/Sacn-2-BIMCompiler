// ⚠ DO NOT REMOVE — W-PROC-PAYMENT-LIVE (AD_PROCESS_FOLD_LANE.md §P2-tail-leg5). Prove idempiere.html dispatches
//   the Payment Print report (AD_Process 313, "Rpt C_Payment", blank classname) live through the W-PROC spine:
//   1. _ensureProcHandlers registers report:c_payment (handlers list).
//   2. ?process=313 deep link (procSet-gated, role 102 GardenWorld Admin has access) resolves the blank classname
//      → report:c_payment and folds a real C_Payment via the NEW report_overlay.foldVoucher (KIND 1, HEADER-ONLY —
//      a payment has no line table). This is the FIRST header-only fold in the lane.
//   3. The result card renders the VOUCHER: an Amount→Value breakdown (PayAmt/Discount/Write-off/…), chip OK, NOT
//      the absent-handler card, and NOT an empty line-item table (the headerOnly branch renders, the line branch
//      is skipped because the voucher has no `.lines` key).
//   The amount-exact fold + the allocation invariant (98.50 + 1.90 + 0.30 − 0.00 == 100.70 = settled C_Invoice 101
//   GrandTotal) are proven HEADLESS in W-PROC-PAYMENT (poc_proc_payment.js); here we re-fold C_Payment 100 in-browser
//   off the served CamelCase ad_seed.db to prove the host reads the real bundle (C_Payment_ID/PayAmt/DocumentNo) exactly.
// §-log first. Run: ERP_ROOT=/tmp/wt-payment/erp node scripts/poc_payment_live.js  (default ROOT=~/bim-ootb/erp)
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
  pg.on('console', m => { const t = m.text(); if (/^§AD-PROC-LIVE/.test(t)) { logs.push(t); console.log('  ' + t); } });
  pg.on('pageerror', e => { pageErr++; console.log('ERR ' + String(e).slice(0, 160)); });
  let pass = true; const fail = (m) => { pass = false; console.log('  ✗ FAIL: ' + m); };

  console.log('— Payment Print (AD_Process 313, Rpt C_Payment) via ?process=313 deep link, GardenAdmin');
  await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenAdmin&process=313`, { waitUntil: 'networkidle' });
  await pg.waitForSelector('.idmp-procresult', { timeout: 25000 }).catch(() => fail('no result card after Payment Print (procSet-gated? check §AD-PROC-LIVE deeplink log)'));

  const hl = logs.find(t => t.startsWith('§AD-PROC-LIVE handlers='));
  if (!hl || !/report:c_payment/.test(hl)) fail('report:c_payment not in registered handlers: ' + hl);

  const denied = logs.find(t => /deeplink proc=313 DENIED/.test(t));
  if (denied) fail('proc 313 not in GardenAdmin procSet — pick a login/role with ad_process_access to 313');

  const l313 = logs.find(t => t.startsWith('§AD-PROC-LIVE proc=313'));
  if (!l313 || !/classname=report:c_payment/.test(l313)) fail('proc 313 did not resolve to report:c_payment: ' + l313);
  if (!l313 || !/dispatched=Y/.test(l313)) fail('proc 313 did not dispatch: ' + l313);

  const chip = await pg.$eval('.idmp-procresult .ok-chip', e => e.textContent).catch(() => '');
  if (chip !== 'OK') fail('result chip not "OK" (got "' + chip + '")');
  if (await pg.$('.idmp-procresult.absent')) fail('Payment Print wrongly rendered as absent-handler (it resolved + folded)');
  const hasTable = await pg.$('.idmp-procresult table');
  if (!hasTable) fail('no voucher table rendered in the Payment Print result card');
  // The voucher card must show the amount breakdown + the document total (a header-only voucher, not an empty
  // line-item table). The msg carries "Document total …".
  const msg = await pg.$$eval('.idmp-procresult .msg', es => es.map(e => e.textContent).join(' | ')).catch(() => '');
  console.log('  §AD-PROC-LIVE payment card msg: "' + msg + '"');
  if (!/Document total/.test(msg)) fail('the voucher card must show "Document total" (header-only render): "' + msg + '"');
  if (/Subtotal|Qty/.test(msg)) fail('a payment is header-only — the card must NOT render a receipt/line-item table: "' + msg + '"');

  // Prove the in-browser fold reads the real CamelCase served bundle (C_Payment_ID/PayAmt/DocumentNo) exactly:
  // fold C_Payment 100 ("400000", PayAmt 98.50) through the EXACT path the registered handler uses
  // (window.__report.core.foldVoucher over REPORT_MAP.c_payment), against the served ad_seed.db via SQLite WASM
  // (columns lowercased like the host _sqlRows). headerOnly=true, no lines key, total=98.50, allocation→100.70.
  const fold100 = await pg.evaluate(() => {
    const RC = window.__report && window.__report.core; if (!RC) return { err: 'no report core' };
    if (typeof RC.foldVoucher !== 'function') return { err: 'no foldVoucher' };
    const map = RC.REPORT_MAP['c_payment']; if (!map) return { err: 'no c_payment map' };
    const sdb = window.__idmpDb;
    function rows(sql) { const res = sdb.exec(sql); if (!res.length) return []; return res[0].values.map(v => { const o = {}; res[0].columns.forEach((c, i) => o[String(c).toLowerCase()] = v[i]); return o; }); }
    const hdr = rows('SELECT * FROM ' + map.key + ' WHERE ' + map.pk + '=100 LIMIT 1')[0];
    if (!hdr) return { err: 'C_Payment 100 not in served bundle' };
    const bp = hdr.c_bpartner_id != null ? rows('SELECT name FROM c_bpartner WHERE c_bpartner_id=' + Number(hdr.c_bpartner_id))[0] : null;
    const rec = RC.foldVoucher(map, hdr, { partner: bp ? bp.name : null });
    const amt = (c) => { const a = (rec.amounts || []).filter(x => x.col === c)[0]; return a ? a.value : null; };
    return { docno: rec.docno, total: rec.total, partner: rec.partner, headerOnly: rec.headerOnly, hasLinesKey: ('lines' in rec),
             pay: amt('payamt'), disc: amt('discountamt'), woff: amt('writeoffamt'), over: amt('overunderamt') };
  });
  console.log('  §AD-PROC-LIVE C_Payment-100 in-browser fold: ' + JSON.stringify(fold100));
  if (fold100.err) fail('C_Payment-100 browser fold: ' + fold100.err);
  else {
    const applied = (Number(fold100.pay) + Number(fold100.disc) + Number(fold100.woff) - Number(fold100.over)).toFixed(2);
    if (!(fold100.docno === '400000' && fold100.total === '98.50' && fold100.partner === 'Standard' && fold100.headerOnly === true && fold100.hasLinesKey === false && applied === '100.70'))
      fail('C_Payment-100 browser fold not amount-exact / header-only / invariant-holding (CamelCase PayAmt/DocumentNo read failed?): ' + JSON.stringify(fold100) + ' applied=' + applied);
    else console.log('  §AD-PROC-LIVE C_Payment-100 folds to a header-only voucher (docno 400000, total 98.50, partner Standard, NO lines) — and PayAmt+Discount+Write-off−Over/Under = 100.70 = settled invoice GrandTotal, in-browser off the real CamelCase bundle');
  }

  if (pageErr) fail(pageErr + ' pageerror(s)');
  console.log(pass
    ? '🟢 W-PROC-PAYMENT-LIVE PASS — proc 313 (blank classname) resolves via the W-PROC spine to report:c_payment; a real C_Payment folds through the NEW foldVoucher into a header-only voucher card (amounts settle the invoice to the cent), 0 pageerrors'
    : '🔴 W-PROC-PAYMENT-LIVE FAIL');
  await br.close(); server.close(); process.exit(pass ? 0 : 2);
})().catch(e => { console.log('🔴 ' + e.message); process.exit(1); });
