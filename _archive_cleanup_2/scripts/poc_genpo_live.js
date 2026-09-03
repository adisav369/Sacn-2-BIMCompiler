// ⚠ DO NOT REMOVE — W-PROC-GENPO-LIVE (AD_PROCESS_FOLD_LANE.md §P1). Prove idempiere.html dispatches the
//   ProjectGenOrder process (AD_Process 164) live through the W-PROC spine + the KIND-2 handler:
//   1. _ensureProcHandlers registers org.compiere.process.ProjectGenOrder and the erp_engine archetype is wired
//      (§AD-PROC-LIVE handlers list includes ProjectGenOrder, engine=Y).
//   2. ?process=164 deep link (procSet-gated by ad_process_access) opens the §GENPO-LIVE PROJECT PICKER folded
//      from real c_project rows (no menu leaf exists — the AD trigger is the C_Project.GenerateTo button).
//   3. Picking project 101 (the served seed's only project — it carries NO Warehouse/PriceList-Version) and
//      running → the handler's getProject GATE honestly REJECTS: dispatched=Y ok=N reason=project-not-ready,
//      card shows "Project has no Price List" — NO fabricated order, NO op-group table (HONESTY INVARIANT).
//   4. The positive op-group fold (a real C_Order from a gate-passing project) is proven HEADLESS + oracle-
//      equivalent in W-PROC-GENPO (poc_proc_genorder.js); NO served tenant carries a gate-passing project, so
//      the only HONEST live outcome on this seed is the rejection — logged, not faked (no silent cap).
// §-log first. Run: ERP_ROOT=/tmp/wt-genpo/erp node scripts/poc_genpo_live.js  (default ROOT=~/bim-ootb/erp)
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
  const logs = []; let pageErr = 0;
  pg.on('console', m => { const t = m.text(); if (/^(§AD-PROC-LIVE|§GENPO-LIVE)/.test(t)) { logs.push(t); console.log('  ' + t); } });
  pg.on('pageerror', e => { pageErr++; console.log('ERR ' + String(e).slice(0, 160)); });
  let pass = true; const fail = (m) => { pass = false; console.log('  ✗ FAIL: ' + m); };

  console.log('— GeneratePO (AD_Process 164, ProjectGenOrder) via ?process=164 deep link, GardenAdmin');
  await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenAdmin&process=164`, { waitUntil: 'networkidle' });

  // 1. handler registered + engine wired
  await pg.waitForSelector('select[data-genpo-project]', { timeout: 25000 }).catch(() => fail('project picker never rendered (deep link / picker broken)'));
  const hl = logs.find(t => t.startsWith('§AD-PROC-LIVE handlers='));
  if (!hl || !/ProjectGenOrder/.test(hl)) fail('ProjectGenOrder not in registered handlers: ' + hl);
  if (!hl || !/engine=Y/.test(hl)) fail('erp_engine (ERPEngine) not wired for the KIND-2 archetype: ' + hl);

  // 2. picker folded from REAL c_project rows
  const opts = await pg.$$eval('select[data-genpo-project] option', os => os.map(o => ({ v: o.value, t: o.textContent })).filter(o => o.v));
  if (!opts.length) fail('project picker has no real project options');
  const has101 = opts.find(o => Number(o.v) === 101);
  if (!has101) fail('project 101 (the seed project) not offered in the picker: ' + JSON.stringify(opts));
  else console.log('  §GENPO-LIVE picker options=' + opts.length + ' incl. project 101 "' + has101.t.trim() + '"');

  // 3. pick project 101 → run → honest gate REJECTION (no fabricated order)
  await pg.selectOption('select[data-genpo-project]', '101');
  await pg.click('button[data-proc-run]');
  await pg.waitForSelector('.idmp-procresult', { timeout: 8000 }).catch(() => fail('no result card after Generate Order'));
  const l164 = logs.find(t => t.startsWith('§AD-PROC-LIVE proc=164'));
  if (!l164 || !/classname=org\.compiere\.process\.ProjectGenOrder/.test(l164)) fail('proc 164 did not dispatch to ProjectGenOrder: ' + l164);
  if (!l164 || !/dispatched=Y/.test(l164)) fail('proc 164 not dispatched (handler absent?): ' + l164);
  if (!l164 || !/reason=project-not-ready/.test(l164)) fail('gate did not honestly reject the thin project: ' + l164);
  const chip = await pg.$eval('.idmp-procresult .ok-chip', e => e.textContent).catch(() => '');
  if (chip !== 'Failed') fail('result chip not "Failed" for the honest rejection (got "' + chip + '")');
  const msg = await pg.$eval('.idmp-procresult .msg', e => e.textContent).catch(() => '');
  if (!/Price List|Warehouse/.test(msg)) fail('rejection card lacks the getProject reason: "' + msg + '"');
  else console.log('  §GENPO-LIVE honest rejection card: "' + msg + '"');
  // 4. NO fabricated order — no op-group line table rendered
  const tableRows = await pg.$$eval('.idmp-procresult table tr', rs => rs.length).catch(() => 0);
  if (tableRows > 0) fail('a fabricated order table was rendered for a thin project (HONESTY INVARIANT breach)');
  if (await pg.$('.idmp-procresult.absent')) fail('rejection wrongly rendered as absent-handler (it dispatched + ran the gate)');

  if (pageErr) fail(pageErr + ' pageerror(s)');
  console.log('  §GENPO-LIVE seed-limit: no served tenant carries a gate-passing project (warehouse+pricelist) → the positive C_Order fold is proven headless/oracle-equivalent (W-PROC-GENPO), live shows the honest gate rejection.');
  console.log(pass
    ? '🟢 W-PROC-GENPO-LIVE PASS — proc 164 dispatches via the W-PROC spine to the KIND-2 ProjectGenOrder handler; the picker folds real projects; a thin project is honestly REJECTED (no fabricated order), 0 pageerrors'
    : '🔴 W-PROC-GENPO-LIVE FAIL');
  await br.close(); server.close(); process.exit(pass ? 0 : 2);
})().catch(e => { console.log('🔴 ' + e.message); process.exit(1); });
