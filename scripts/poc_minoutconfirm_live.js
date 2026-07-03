// ⚠ DO NOT REMOVE — W-PROC-MINOUTCONFIRM-LIVE (AD_PROCESS_FOLD_LANE.md §P2-tail-leg6). Prove idempiere.html
//   dispatches the Shipment Confirmation report (AD_Process 292, "Rpt M_InOutConfirm", blank classname) live
//   through the W-PROC spine — the LINE-SORT + PRODUCT-JOIN variant:
//   1. _ensureProcHandlers registers report:m_inoutconfirm (handlers list).
//   2. ?process=292 deep link (procSet-gated; role 102 GardenWorld Admin has access) resolves the blank classname
//      → report:m_inoutconfirm and folds a real M_InOutConfirm via the EXISTING report_overlay.foldReceipt (KIND 1,
//      no new fold verb). The host _procCtx.fetchLines honours map.lineSort (no `line` col) + map.lineProductVia
//      (the product is on the parent m_inoutline, NOT the confirm-line) before folding.
//   3. The result card renders the confirmation line(s), chip OK, NOT the absent-handler card. A confirmation is
//      NON-FINANCIAL → there is NO Subtotal/Tax/Total line (honest, never a fabricated total).
//   The qty-exact fold + product-join is proven HEADLESS + oracle-equivalent in W-PROC-MINOUTCONFIRM
//   (poc_proc_minoutconfirm.js); here we re-fold it in-browser off the served CamelCase ad_seed.db — replicating
//   the host's lineSort + lineProductVia path — to prove the host reads the real bundle (ConfirmedQty + the
//   m_inoutline_id→m_inoutline.m_product_id join → product 128 "Azalea Bush") exactly.
// §-log first. Run: ERP_ROOT=/tmp/wt-leg6/erp node scripts/poc_minoutconfirm_live.js  (default ROOT=~/bim-ootb/erp)
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

  console.log('— Shipment Confirmation (AD_Process 292, Rpt M_InOutConfirm) via ?process=292 deep link, GardenAdmin');
  await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenAdmin&process=292`, { waitUntil: 'networkidle' });
  await pg.waitForSelector('.idmp-procresult', { timeout: 25000 }).catch(() => fail('no result card after Shipment Confirmation (procSet-gated? check §AD-PROC-LIVE deeplink log)'));

  const hl = logs.find(t => t.startsWith('§AD-PROC-LIVE handlers='));
  if (!hl || !/report:m_inoutconfirm/.test(hl)) fail('report:m_inoutconfirm not in registered handlers: ' + hl);

  const denied = logs.find(t => /deeplink proc=292 DENIED/.test(t));
  if (denied) fail('proc 292 not in GardenAdmin procSet — pick a login/role with ad_process_access to 292');

  const l292 = logs.find(t => t.startsWith('§AD-PROC-LIVE proc=292'));
  if (!l292 || !/classname=report:m_inoutconfirm/.test(l292)) fail('proc 292 did not resolve to report:m_inoutconfirm: ' + l292);
  if (!l292 || !/dispatched=Y/.test(l292)) fail('proc 292 did not dispatch: ' + l292);

  const chip = await pg.$eval('.idmp-procresult .ok-chip', e => e.textContent).catch(() => '');
  if (chip !== 'OK') fail('result chip not "OK" (got "' + chip + '")');
  if (await pg.$('.idmp-procresult.absent')) fail('Shipment Confirmation wrongly rendered as absent-handler (it resolved + folded)');
  const hasTable = await pg.$('.idmp-procresult table');
  if (!hasTable) fail('no confirmation table rendered in the Shipment Confirmation result card');
  // NON-FINANCIAL: the card must NOT show a fabricated Subtotal/Tax/Total line (a confirmation carries qty, not money).
  const msg = await pg.$$eval('.idmp-procresult .msg', es => es.map(e => e.textContent).join(' | ')).catch(() => '');
  console.log('  §AD-PROC-LIVE confirm card msg: "' + msg + '"');
  if (/Subtotal|Total/.test(msg)) fail('a confirmation is non-financial — the card must NOT show Subtotal/Tax/Total: "' + msg + '"');

  // Prove the in-browser fold reads the real CamelCase served bundle exactly THROUGH the lineSort + lineProductVia
  // path the host _procCtx uses: order by map.lineSort (the confirm-line has no `line` col), resolve each line's
  // product via the m_inoutline_id→m_inoutline.m_product_id join (the confirm-line carries no product), then fold.
  // EXPECT: docno 10000000, 1 line, financial=false, subtotal/tax/total null, qty=ConfirmedQty 10, product "Azalea
  // Bush" (128) resolved through the join, partner null (no c_bpartner_id on the confirm header).
  const fold = await pg.evaluate(() => {
    const RC = window.__report && window.__report.core; if (!RC) return { err: 'no report core' };
    const map = RC.REPORT_MAP['m_inoutconfirm']; if (!map) return { err: 'no m_inoutconfirm map' };
    const sdb = window.__idmpDb;
    function rows(sql) { const res = sdb.exec(sql); if (!res.length) return []; return res[0].values.map(v => { const o = {}; res[0].columns.forEach((c, i) => o[String(c).toLowerCase()] = v[i]); return o; }); }
    const hdr = rows('SELECT * FROM ' + map.key + ' WHERE ' + map.pk + '=100 LIMIT 1')[0];
    if (!hdr) return { err: 'M_InOutConfirm 100 not in served bundle' };
    const lines = rows('SELECT * FROM ' + map.lineTable + ' WHERE ' + map.fk + '=100 ORDER BY ' + (map.lineSort || 'line'));
    const lineHadProduct = lines[0] && lines[0][map.fkProduct] != null;   // the confirm-line itself must NOT carry the product
    // lineProductVia: resolve the product through the parent shipment line (exactly as host _procCtx.fetchLines).
    const via = map.lineProductVia, prods = {};
    lines.forEach(r => { const lid = r[via.fk]; if (lid != null) { const pr = rows('SELECT ' + via.product + ' AS p FROM ' + via.table + ' WHERE ' + via.pk + '=' + Number(lid))[0]; r[map.fkProduct] = pr ? pr.p : null; } });
    lines.forEach(r => { const pid = r[map.fkProduct]; if (pid != null && prods[pid] === undefined) { const n = rows('SELECT name AS n FROM m_product WHERE m_product_id=' + Number(pid))[0]; prods[pid] = n ? n.n : null; } });
    const rec = RC.foldReceipt(map, hdr, lines, { products: prods });
    return { docno: rec.docno, subtotal: rec.subtotal, total: rec.total, tax: rec.tax, lines: rec.lines.length, financial: rec.financial, partner: rec.partner,
             qty0: rec.lines[0] && rec.lines[0].qty, price0: rec.lines[0] && rec.lines[0].price, amount0: rec.lines[0] && rec.lines[0].amount,
             prod0: rec.lines[0] && rec.lines[0].name, pid0: rec.lines[0] && rec.lines[0].m_product_id, lineHadProduct: lineHadProduct };
  });
  console.log('  §AD-PROC-LIVE M_InOutConfirm-100 in-browser fold: ' + JSON.stringify(fold));
  if (fold.err) fail('M_InOutConfirm-100 browser fold: ' + fold.err);
  else if (!(String(fold.docno) === '10000000' && fold.subtotal === null && fold.total === null && fold.tax === null && fold.lines === 1 && fold.financial === false && fold.partner === null && Number(fold.qty0) === 10 && fold.price0 === null && fold.amount0 === null))
    fail('M_InOutConfirm-100 browser fold not qty-exact / non-financial (CamelCase ConfirmedQty/DocumentNo read failed?): ' + JSON.stringify(fold));
  else if (fold.lineHadProduct) fail('the confirm-line unexpectedly carried its own product — the join would be untested: ' + JSON.stringify(fold));
  else if (!(fold.prod0 === 'Azalea Bush' && Number(fold.pid0) === 128)) fail('the line product did NOT resolve through the m_inoutline join (expected 128 "Azalea Bush"): ' + JSON.stringify(fold));
  else console.log('  §AD-PROC-LIVE M_InOutConfirm-100 folds to a non-financial receipt (docno 10000000, qty 10, product "Azalea Bush" via the parent-line join, no money) in-browser — the lineSort + lineProductVia path reads the real CamelCase bundle exactly');

  if (pageErr) fail(pageErr + ' pageerror(s)');
  console.log(pass
    ? '🟢 W-PROC-MINOUTCONFIRM-LIVE PASS — proc 292 (blank classname) resolves via the W-PROC spine to report:m_inoutconfirm; a real M_InOutConfirm folds through the existing foldReceipt (lineSort + product-join, no new fold verb) into a non-financial receipt card, 0 pageerrors'
    : '🔴 W-PROC-MINOUTCONFIRM-LIVE FAIL');
  await br.close(); server.close(); process.exit(pass ? 0 : 2);
})().catch(e => { console.log('🔴 ' + e.message); process.exit(1); });
