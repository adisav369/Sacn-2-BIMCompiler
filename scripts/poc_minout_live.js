// ⚠ DO NOT REMOVE — W-PROC-MINOUT-LIVE (AD_PROCESS_FOLD_LANE.md §P2-tail-leg1). Prove idempiere.html dispatches
//   the Delivery Note / Shipment Print report (AD_Process 117, "Rpt M_InOut", blank classname) live through the
//   W-PROC spine:
//   1. _ensureProcHandlers registers report:m_inout (handlers list).
//   2. ?process=117 deep link (procSet-gated, role 102 GardenWorld Admin has access) resolves the blank classname
//      → report:m_inout and folds a real M_InOut via the EXISTING report_overlay.foldReceipt (KIND 1, no new fold
//      code — the print sibling of InOutGenerate §P2-leg1).
//   3. The result card renders the shipment lines, chip OK, NOT the absent-handler card. A shipment is NON-
//      FINANCIAL → there is NO Subtotal/Tax/Total line (honest, never a fabricated total).
//   The cent/qty-exact fold (M_InOut 100, DocumentNo 600000, qty 1) is proven HEADLESS + oracle-equivalent in
//   W-PROC-MINOUT (poc_proc_minout.js); here we re-fold it in-browser off the served CamelCase ad_seed.db to
//   prove the host reads the real bundle (M_InOut_ID/MovementQty/DocumentNo) exactly.
// §-log first. Run: ERP_ROOT=/tmp/wt-minout/erp node scripts/poc_minout_live.js  (default ROOT=~/bim-ootb/erp)
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

  console.log('— Delivery Note / Shipment Print (AD_Process 117, Rpt M_InOut) via ?process=117 deep link, GardenAdmin');
  await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenAdmin&process=117`, { waitUntil: 'networkidle' });
  await pg.waitForSelector('.idmp-procresult', { timeout: 25000 }).catch(() => fail('no result card after Shipment Print (procSet-gated? check §AD-PROC-LIVE deeplink log)'));

  const hl = logs.find(t => t.startsWith('§AD-PROC-LIVE handlers='));
  if (!hl || !/report:m_inout/.test(hl)) fail('report:m_inout not in registered handlers: ' + hl);

  const denied = logs.find(t => /deeplink proc=117 DENIED/.test(t));
  if (denied) fail('proc 117 not in GardenAdmin procSet — pick a login/role with ad_process_access to 117');

  const l117 = logs.find(t => t.startsWith('§AD-PROC-LIVE proc=117'));
  if (!l117 || !/classname=report:m_inout/.test(l117)) fail('proc 117 did not resolve to report:m_inout: ' + l117);
  if (!l117 || !/dispatched=Y/.test(l117)) fail('proc 117 did not dispatch: ' + l117);

  const chip = await pg.$eval('.idmp-procresult .ok-chip', e => e.textContent).catch(() => '');
  if (chip !== 'OK') fail('result chip not "OK" (got "' + chip + '")');
  if (await pg.$('.idmp-procresult.absent')) fail('Shipment Print wrongly rendered as absent-handler (it resolved + folded)');
  const hasTable = await pg.$('.idmp-procresult table');
  if (!hasTable) fail('no shipment table rendered in the Shipment Print result card');
  // NON-FINANCIAL: the card must NOT show a fabricated Subtotal/Tax/Total line (a shipment carries qty, not money).
  const msg = await pg.$$eval('.idmp-procresult .msg', es => es.map(e => e.textContent).join(' | ')).catch(() => '');
  console.log('  §AD-PROC-LIVE minout card msg: "' + msg + '"');
  if (/Subtotal|Total/.test(msg)) fail('a shipment is non-financial — the card must NOT show Subtotal/Tax/Total: "' + msg + '"');

  // Prove the in-browser fold reads the real CamelCase served bundle (M_InOut_ID/DocumentNo/MovementQty) exactly:
  // fold M_InOut 100 ("600000", 1 line product 130 qty 1) through the EXACT path the registered handler uses
  // (window.__report.core.foldReceipt over REPORT_MAP.m_inout), against the served ad_seed.db via SQLite WASM
  // (columns lowercased like the host _sqlRows). financial=false; subtotal/tax/total null; qty=1; price/amount null.
  const fold100 = await pg.evaluate(() => {
    const RC = window.__report && window.__report.core; if (!RC) return { err: 'no report core' };
    const map = RC.REPORT_MAP['m_inout']; if (!map) return { err: 'no m_inout map' };
    const sdb = window.__idmpDb;
    function rows(sql) { const res = sdb.exec(sql); if (!res.length) return []; return res[0].values.map(v => { const o = {}; res[0].columns.forEach((c, i) => o[String(c).toLowerCase()] = v[i]); return o; }); }
    const hdr = rows('SELECT * FROM ' + map.key + ' WHERE ' + map.pk + '=100 LIMIT 1')[0];
    const lines = rows('SELECT * FROM ' + map.lineTable + ' WHERE ' + map.fk + '=100 ORDER BY line');
    if (!hdr) return { err: 'M_InOut 100 not in served bundle' };
    const rec = RC.foldReceipt(map, hdr, lines, { products: {} });
    return { docno: rec.docno, subtotal: rec.subtotal, total: rec.total, tax: rec.tax, lines: rec.lines.length, financial: rec.financial, qty0: rec.lines[0] && rec.lines[0].qty, price0: rec.lines[0] && rec.lines[0].price, amount0: rec.lines[0] && rec.lines[0].amount };
  });
  console.log('  §AD-PROC-LIVE M_InOut-100 in-browser fold: ' + JSON.stringify(fold100));
  if (fold100.err) fail('M_InOut-100 browser fold: ' + fold100.err);
  else if (!(fold100.docno === '600000' && fold100.subtotal === null && fold100.total === null && fold100.tax === null && fold100.lines === 1 && fold100.financial === false && Number(fold100.qty0) === 1 && fold100.price0 === null && fold100.amount0 === null))
    fail('M_InOut-100 browser fold not qty-exact / non-financial (CamelCase MovementQty/DocumentNo read failed?): ' + JSON.stringify(fold100));
  else console.log('  §AD-PROC-LIVE M_InOut-100 folds to a non-financial receipt (docno 600000, qty 1, no money) in-browser — the m_inout map reads the real CamelCase bundle exactly');

  if (pageErr) fail(pageErr + ' pageerror(s)');
  console.log(pass
    ? '🟢 W-PROC-MINOUT-LIVE PASS — proc 117 (blank classname) resolves via the W-PROC spine to report:m_inout; a real M_InOut folds through the existing foldReceipt into a non-financial receipt card (no new fold code), 0 pageerrors'
    : '🔴 W-PROC-MINOUT-LIVE FAIL');
  await br.close(); server.close(); process.exit(pass ? 0 : 2);
})().catch(e => { console.log('🔴 ' + e.message); process.exit(1); });
