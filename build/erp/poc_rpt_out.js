// ⚠ DO NOT REMOVE — Scope guard
// Scope: real-browser §-witness for R4 — after-the-receipt OUTPUT (Print / Share / Save) on report_overlay.js.
//   THE CLAIM (CRUD_P_R_REPORT_SPEC §4 / FRONTEND_LANE_MASTER §OUTSTANDING): the receipt panel was view-only
//   (✕ close only). R4 adds Print / Share / Save — edge-only, server-free — that emit the SAME folded rec:
//     1. §RPT-OUT-BUTTONS — the rendered receipt panel carries exactly the 3 output buttons (print/share/save).
//     2. §RPT-OUT-SAVE — clicking Save triggers a real Blob download named receipt_*.html (no server).
//     3. §RPT-OUT-PRINT — clicking Print builds a print iframe + logs §RPT-OUT print (no value re-queried).
//     4. §RPT-OUT-SHARE — clicking Share calls navigator.share/clipboard with the folded receipt text.
//   NON-INVENT: rec is a GENUINE fold — CORE.foldReceipt over a c_order header+lines fixture (subtotal=Σlines,
//   tax=total−subtotal via BigDecimal); the witness's issue is the OUTPUT controls, not the fold (proven in
//   poc_money_fold). §-log first — READ poc_rpt_out.log before any conclusion.
// Run:  node build/erp/poc_rpt_out.js 2>&1 | tee build/erp/poc_rpt_out.log   (cwd = bim-compiler)
'use strict';
const { chromium } = require('/home/red1/bim-compiler/deploy/dev/tests/node_modules/playwright');
const http = require('http'), fs = require('fs'), path = require('path');
const DIR = __dirname;                                          // build/erp
const HARNESS = `<!doctype html><meta charset=utf-8><body>
<script>window.fname = function(k){return k;};</script>
<script src="bigdecimal.js"></script>
<script src="report_overlay.js"></script>`;
const MIME = { '.js':'text/javascript' };
const server = http.createServer((req, res) => {
  const p = decodeURIComponent(req.url.split('?')[0]);
  if (p === '/' || p === '/harness.html') { res.writeHead(200, {'Content-Type':'text/html'}); res.end(HARNESS); return; }
  fs.readFile(path.join(DIR, p), (e, buf) => {
    if (e) { res.writeHead(404); res.end('404'); return; }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream' }); res.end(buf);
  });
});

(async () => {
  await new Promise(r => server.listen(0, r));
  const port = server.address().port;
  const logs = [], errs = [];
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ acceptDownloads: true });
  const page = await ctx.newPage();
  page.on('console', m => logs.push(m.text()));
  page.on('pageerror', e => errs.push(e.message));
  await page.goto(`http://localhost:${port}/harness.html`, { waitUntil: 'networkidle' });
  await page.waitForFunction(() => window.__report && window.__report._test, { timeout: 8000 });

  // Render a GENUINE fold (c_order header+lines fixture → foldReceipt does the BigDecimal re-sum).
  const fold = await page.evaluate(() => {
    const C = window.__report.core, map = C.REPORT_MAP.c_order;
    const header = { c_order_id: 101, grandtotal: '162.00', dateordered: '2026-06-01' };
    const lines = [
      { line: 1, m_product_id: 5, qtyordered: '2', priceactual: '50.00', linenetamt: '100.00' },
      { line: 2, m_product_id: 7, qtyordered: '1', priceactual: '50.00', linenetamt: '50.00' }
    ];
    const names = { partner: 'GardenWorld', products: { 5: 'Mulch', 7: 'Spade' } };
    const rec = C.foldReceipt(map, header, lines, names);
    window.__report._test.render(rec);
    return { subtotal: rec.subtotal, tax: rec.tax, total: rec.total,
             text: window.__report._test.receiptText(rec).slice(0, 0) || 'ok',
             buttons: Array.from(document.querySelectorAll('#reportPanel .rpb')).map(b => b.dataset.a) };
  });
  console.log('§RPT-OUT-BUTTONS buttons=[' + fold.buttons.join(',') + '] fold(subtotal=' + fold.subtotal +
    ' tax=' + fold.tax + ' total=' + fold.total + ')');

  // SAVE — expect a Blob download
  let dl = null;
  const dlP = page.waitForEvent('download', { timeout: 4000 }).catch(() => null);
  await page.click('#reportPanel .rpb[data-a=save]');
  dl = await dlP;
  console.log('§RPT-OUT-SAVE download=' + (dl ? dl.suggestedFilename() : 'NONE'));

  // SHARE — stub navigator.share/clipboard to record the call, then click
  await page.evaluate(() => {
    window.__shared = null;
    navigator.share = (d) => { window.__shared = { keys: Object.keys(d), text: (d.text||'').slice(0,24) }; return Promise.resolve(); };
    try { Object.defineProperty(navigator, 'canShare', { value: () => false, configurable: true }); } catch(e){}
  });
  await page.click('#reportPanel .rpb[data-a=share]');
  await page.waitForTimeout(200);
  const shared = await page.evaluate(() => window.__shared);
  console.log('§RPT-OUT-SHARE shared=' + JSON.stringify(shared));

  // PRINT — iframe + log (no dialog headless)
  await page.click('#reportPanel .rpb[data-a=print]');
  await page.waitForTimeout(200);
  const printLog = logs.find(l => l.startsWith('§RPT-OUT print')) || '(none)';
  console.log('§RPT-OUT-PRINT ' + printLog);

  await page.screenshot({ path: path.join(DIR, 'rpt_out.png') });

  const pass = fold.buttons.join(',') === 'print,share,save' &&
    dl && /^receipt_.*\.html$/.test(dl.suggestedFilename()) &&
    shared && shared.text.indexOf('Receipt') === 0 &&
    printLog.indexOf('§RPT-OUT print') === 0 && errs.length === 0;
  console.log('§RPT-OUT-R4 ' + (pass ? 'PASS' : 'FAIL') + ' pageErrors=' + (errs.length ? errs.join('|') : 0));

  await browser.close(); server.close(); process.exit(pass ? 0 : 1);
})().catch(e => { console.error('PROBE-ERR', e); server.close(); process.exit(2); });
