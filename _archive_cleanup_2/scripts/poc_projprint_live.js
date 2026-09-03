// ⚠ DO NOT REMOVE — W-PROC-PROJPRINT-LIVE (AD_PROCESS_FOLD_LANE.md §P2-leg3). Prove idempiere.html dispatches
//   the Project Print report (AD_Process 217, "Rpt C_Project", blank classname) live through the W-PROC spine:
//   1. _ensureProcHandlers registers report:c_project (handlers list).
//   2. ?process=217 deep link (procSet-gated) resolves the blank classname → report:c_project and folds a real
//      C_Project via the EXISTING report_overlay.foldReceipt (KIND 1, no new fold code).
//   3. The result card renders the receipt (lines + Subtotal/Tax/Total), chip OK, NOT the absent-handler card.
//   The non-empty cent-exact fold (project 101, Subtotal 600) is proven HEADLESS + oracle-equivalent in
//   W-PROC-PROJPRINT (poc_proc_projprint.js); the menu launch folds the first project honestly (whatever it is).
// §-log first. Run: ERP_ROOT=/tmp/wt-pp/erp node scripts/poc_projprint_live.js  (default ROOT=~/bim-ootb/erp)
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

  console.log('— Project Print (AD_Process 217, Rpt C_Project) via ?process=217 deep link, GardenAdmin');
  await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenAdmin&process=217`, { waitUntil: 'networkidle' });
  await pg.waitForSelector('.idmp-procresult', { timeout: 25000 }).catch(() => fail('no result card after Project Print (procSet-gated? check §AD-PROC-LIVE deeplink log)'));

  const hl = logs.find(t => t.startsWith('§AD-PROC-LIVE handlers='));
  if (!hl || !/report:c_project/.test(hl)) fail('report:c_project not in registered handlers: ' + hl);

  const denied = logs.find(t => /deeplink proc=217 DENIED/.test(t));
  if (denied) fail('proc 217 not in GardenAdmin procSet — pick a login/role with ad_process_access to 217');

  const l217 = logs.find(t => t.startsWith('§AD-PROC-LIVE proc=217'));
  if (!l217 || !/classname=report:c_project/.test(l217)) fail('proc 217 did not resolve to report:c_project: ' + l217);
  if (!l217 || !/dispatched=Y/.test(l217)) fail('proc 217 did not dispatch: ' + l217);

  const chip = await pg.$eval('.idmp-procresult .ok-chip', e => e.textContent).catch(() => '');
  if (chip !== 'OK') fail('result chip not "OK" (got "' + chip + '")');
  if (await pg.$('.idmp-procresult.absent')) fail('Project Print wrongly rendered as absent-handler (it resolved + folded)');
  const hasTable = await pg.$('.idmp-procresult table');
  if (!hasTable) fail('no receipt table rendered in the Project Print result card');
  const msg = await pg.$$eval('.idmp-procresult .msg', es => es.map(e => e.textContent).join(' | ')).catch(() => '');
  console.log('  §AD-PROC-LIVE projprint card msg: "' + msg + '"');
  if (!/Subtotal|Total/.test(msg)) fail('receipt totals (Subtotal/Total) missing from the card: "' + msg + '"');

  // The menu launch folds the FIRST project (100 "Standard", PlannedAmt 0 → honest 0.00). To prove the NON-ZERO
  // in-browser fold reads the real CamelCase bundle (PlannedAmt/Value) correctly, fold project 101 ("Landscape",
  // PlannedAmt 600) through the EXACT path the registered handler uses: window.__report.core.foldReceipt over
  // REPORT_MAP.c_project, against the served ad_seed.db via SQLite WASM (columns lowercased like the host _sqlRows).
  const fold101 = await pg.evaluate(() => {
    const RC = window.__report && window.__report.core; if (!RC) return { err: 'no report core' };
    const map = RC.REPORT_MAP['c_project']; if (!map) return { err: 'no c_project map' };
    const sdb = window.__idmpDb;
    function rows(sql) { const res = sdb.exec(sql); if (!res.length) return []; return res[0].values.map(v => { const o = {}; res[0].columns.forEach((c, i) => o[String(c).toLowerCase()] = v[i]); return o; }); }
    const hdr = rows('SELECT * FROM ' + map.key + ' WHERE ' + map.pk + '=101 LIMIT 1')[0];
    const lines = rows('SELECT * FROM ' + map.lineTable + ' WHERE ' + map.fk + '=101 ORDER BY line');
    if (!hdr) return { err: 'project 101 not in served bundle' };
    const rec = RC.foldReceipt(map, hdr, lines, { products: {} });
    return { docno: rec.docno, subtotal: rec.subtotal, total: rec.total, tax: rec.tax, lines: rec.lines.length, financial: rec.financial };
  });
  console.log('  §AD-PROC-LIVE project-101 in-browser fold: ' + JSON.stringify(fold101));
  if (fold101.err) fail('project-101 browser fold: ' + fold101.err);
  else if (!(fold101.docno === 'Landscape' && fold101.subtotal === '600.00' && fold101.total === '600.00' && fold101.tax === '0.00' && fold101.lines === 1 && fold101.financial === true))
    fail('project-101 browser fold not cent-exact (CamelCase PlannedAmt/Value read failed?): ' + JSON.stringify(fold101));
  else console.log('  §AD-PROC-LIVE project-101 folds to 600.00 in-browser — the c_project map reads the real CamelCase bundle exactly');

  if (pageErr) fail(pageErr + ' pageerror(s)');
  console.log(pass
    ? '🟢 W-PROC-PROJPRINT-LIVE PASS — proc 217 (blank classname) resolves via the W-PROC spine to report:c_project; a real C_Project folds through the existing foldReceipt into a receipt card (no new fold code), 0 pageerrors'
    : '🔴 W-PROC-PROJPRINT-LIVE FAIL');
  await br.close(); server.close(); process.exit(pass ? 0 : 2);
})().catch(e => { console.log('🔴 ' + e.message); process.exit(1); });
