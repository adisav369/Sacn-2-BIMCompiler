// ⚠ DO NOT REMOVE — W-PROC-MOVEMENT-LIVE (AD_PROCESS_FOLD_LANE.md §P2-tail-leg2). Prove idempiere.html dispatches
//   the Inventory Move Print report (AD_Process 290, "Rpt M_Movement", blank classname) live through the W-PROC spine:
//   1. _ensureProcHandlers registers report:m_movement (handlers list).
//   2. ?process=290 deep link (procSet-gated, role 102 GardenWorld Admin has access) resolves the blank classname
//      → report:m_movement and folds a real M_Movement via the EXISTING report_overlay.foldReceipt (KIND 1, no new
//      fold code — the warehouse sibling of Rpt M_InOut §P2-tail-leg1).
//   3. The result card renders the movement lines, chip OK, NOT the absent-handler card. A material movement is NON-
//      FINANCIAL → there is NO Subtotal/Tax/Total line (honest, never a fabricated total).
//   The cent/qty-exact fold (M_Movement 100, DocumentNo 10000000, qty 4) is proven HEADLESS + oracle-equivalent in
//   W-PROC-MOVEMENT (poc_proc_movement.js); here we re-fold it in-browser off the served CamelCase ad_seed.db to
//   prove the host reads the real bundle (M_Movement_ID/MovementQty/DocumentNo) exactly via the lc() alias.
// §-log first. Run: ERP_ROOT=/tmp/wt-movement/erp node scripts/poc_movement_live.js  (default ROOT=~/bim-ootb/erp)
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

  console.log('— Inventory Move Print (AD_Process 290, Rpt M_Movement) via ?process=290 deep link, GardenAdmin');
  await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenAdmin&process=290`, { waitUntil: 'networkidle' });
  await pg.waitForSelector('.idmp-procresult', { timeout: 25000 }).catch(() => fail('no result card after Inventory Move Print (procSet-gated? check §AD-PROC-LIVE deeplink log)'));

  const hl = logs.find(t => t.startsWith('§AD-PROC-LIVE handlers='));
  if (!hl || !/report:m_movement/.test(hl)) fail('report:m_movement not in registered handlers: ' + hl);

  const denied = logs.find(t => /deeplink proc=290 DENIED/.test(t));
  if (denied) fail('proc 290 not in GardenAdmin procSet — pick a login/role with ad_process_access to 290');

  const l290 = logs.find(t => t.startsWith('§AD-PROC-LIVE proc=290'));
  if (!l290 || !/classname=report:m_movement/.test(l290)) fail('proc 290 did not resolve to report:m_movement: ' + l290);
  if (!l290 || !/dispatched=Y/.test(l290)) fail('proc 290 did not dispatch: ' + l290);

  const chip = await pg.$eval('.idmp-procresult .ok-chip', e => e.textContent).catch(() => '');
  if (chip !== 'OK') fail('result chip not "OK" (got "' + chip + '")');
  if (await pg.$('.idmp-procresult.absent')) fail('Inventory Move Print wrongly rendered as absent-handler (it resolved + folded)');
  const hasTable = await pg.$('.idmp-procresult table');
  if (!hasTable) fail('no movement table rendered in the Inventory Move Print result card');
  // NON-FINANCIAL: the card must NOT show a fabricated Subtotal/Tax/Total line (a movement carries qty, not money).
  const msg = await pg.$$eval('.idmp-procresult .msg', es => es.map(e => e.textContent).join(' | ')).catch(() => '');
  console.log('  §AD-PROC-LIVE movement card msg: "' + msg + '"');
  if (/Subtotal|Total/.test(msg)) fail('a material movement is non-financial — the card must NOT show Subtotal/Tax/Total: "' + msg + '"');

  // Prove the in-browser fold reads the real CamelCase served bundle (M_Movement_ID/DocumentNo/MovementQty) exactly:
  // fold M_Movement 100 ("10000000", 1 line product 123 qty 4) through the EXACT path the registered handler uses
  // (window.__report.core.foldReceipt over REPORT_MAP.m_movement), against the served ad_seed.db via SQLite WASM
  // (columns lowercased like the host _sqlRows). financial=false; subtotal/tax/total null; qty=4; price/amount null.
  const fold100 = await pg.evaluate(() => {
    const RC = window.__report && window.__report.core; if (!RC) return { err: 'no report core' };
    const map = RC.REPORT_MAP['m_movement']; if (!map) return { err: 'no m_movement map' };
    const sdb = window.__idmpDb;
    function rows(sql) { const res = sdb.exec(sql); if (!res.length) return []; return res[0].values.map(v => { const o = {}; res[0].columns.forEach((c, i) => o[String(c).toLowerCase()] = v[i]); return o; }); }
    const hdr = rows('SELECT * FROM ' + map.key + ' WHERE ' + map.pk + '=100 LIMIT 1')[0];
    const lines = rows('SELECT * FROM ' + map.lineTable + ' WHERE ' + map.fk + '=100 ORDER BY line');
    if (!hdr) return { err: 'M_Movement 100 not in served bundle' };
    const rec = RC.foldReceipt(map, hdr, lines, { products: {} });
    return { docno: rec.docno, subtotal: rec.subtotal, total: rec.total, tax: rec.tax, partner: rec.partner, lines: rec.lines.length, financial: rec.financial, qty0: rec.lines[0] && rec.lines[0].qty, price0: rec.lines[0] && rec.lines[0].price, amount0: rec.lines[0] && rec.lines[0].amount };
  });
  console.log('  §AD-PROC-LIVE M_Movement-100 in-browser fold: ' + JSON.stringify(fold100));
  if (fold100.err) fail('M_Movement-100 browser fold: ' + fold100.err);
  else if (!(fold100.docno === '10000000' && fold100.subtotal === null && fold100.total === null && fold100.tax === null && fold100.partner === null && fold100.lines === 1 && fold100.financial === false && Number(fold100.qty0) === 4 && fold100.price0 === null && fold100.amount0 === null))
    fail('M_Movement-100 browser fold not qty-exact / non-financial (CamelCase MovementQty/DocumentNo read failed?): ' + JSON.stringify(fold100));
  else console.log('  §AD-PROC-LIVE M_Movement-100 folds to a non-financial receipt (docno 10000000, qty 4, no money, partner null) in-browser — the m_movement map reads the real CamelCase bundle exactly');

  if (pageErr) fail(pageErr + ' pageerror(s)');
  console.log(pass
    ? '🟢 W-PROC-MOVEMENT-LIVE PASS — proc 290 (blank classname) resolves via the W-PROC spine to report:m_movement; a real M_Movement folds through the existing foldReceipt into a non-financial receipt card (no new fold code), 0 pageerrors'
    : '🔴 W-PROC-MOVEMENT-LIVE FAIL');
  await br.close(); server.close(); process.exit(pass ? 0 : 2);
})().catch(e => { console.log('🔴 ' + e.message); process.exit(1); });
