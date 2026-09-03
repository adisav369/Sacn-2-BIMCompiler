// W-AD-MENU-PRF-LIVE (UI_UNPARK_RESUME.md B-4 — the §2 P/R/F residual) — prove the idempiere.html menu
// prunes Process/Report leaves by REAL ad_process_access grants and Form (X) leaves by REAL
// ad_form_access grants, per role, IN THE LIVE DOM:
//   roles differ (Admin 406-proc / User 356-proc / WebSvc 2-proc grants); the Admin-only leaves
//   "Reset Accounting" (P, process 176) + "Import File Loader" (X, form 101) render for GardenAdmin
//   and MUST NOT render for GardenUser (the falsifier arm).
// 'F' (workflow) / 'I' / 'T' leaves stay unscoped — this seed's ad_menu has no workflow/task/info id
// column to gate on (named residual).
// Run: ERP_ROOT=/tmp/wt-uibridge/erp node scripts/poc_ad_menu_prf_live.js   (default ROOT=~/bim-ootb/erp)
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
const ADMIN_ONLY = ['Reset Accounting', 'Import File Loader'];   // P(176) + X(form 101), granted 102 NOT 103
(async () => {
  await new Promise(r => server.listen(0, r)); const port = server.address().port;
  const br = await chromium.launch(); const pg = await br.newContext().then(c => c.newPage());
  pg.on('console', m => { const t = m.text(); if (t.indexOf('§IDMP-SESSION scopeMenu') === 0 || t.indexOf('§IDMP-SESSION accessible') === 0) console.log('  ' + t); });
  pg.on('pageerror', e => console.log('ERR ' + String(e).slice(0, 160)));
  let pass = true; const fail = m => { pass = false; console.log('  ✗ ' + m); };

  // per-role scoping straight off the session engine (all roles, § lines = the value proof)
  await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenAdmin`, { waitUntil: 'networkidle' });
  await pg.waitForFunction(() => window.__idmpDb && window.IdmpSession && window.ADParser, { timeout: 25000 });
  const perRole = await pg.evaluate(() => {
    var db = window.__idmpDb, SES = window.IdmpSession, tree = window.ADParser.getMenuTree(db);
    return db.exec('SELECT ad_role_id,name FROM ad_role ORDER BY ad_role_id')[0].values.map(function (rv) {
      var winSet = SES.accessibleWindows(db, rv[0]);
      var procSet = SES.accessibleProcesses(db, rv[0]);
      var formSet = SES.accessibleForms(db, rv[0]);
      var s = SES.scopeMenu(tree, winSet, procSet, formSet);
      return { role: rv[0], name: rv[1], procs: s.visibleProcs + '/' + s.totalProcs, forms: s.visibleForms + '/' + s.totalForms };
    });
  });
  console.log('§AD-MENU-PRF-LIVE per-role P/R+X scoping (source=ad_process_access/ad_form_access):');
  perRole.forEach(x => console.log('  role=' + x.role + ' "' + x.name + '" procLeaves=' + x.procs + ' formLeaves=' + x.forms));
  const dis = new Set(perRole.map(x => x.procs));
  if (dis.size < 2) fail('roles do not differ in visible process leaves');

  // LIVE DOM: Admin sees the admin-only leaves…
  const admHas = await Promise.all(ADMIN_ONLY.map(t => pg.locator('#idmp-tree >> text="' + t + '"').count()));
  console.log('  GardenAdmin DOM: ' + ADMIN_ONLY.map((t, i) => t + '=' + admHas[i]).join(' · '));
  admHas.forEach((n, i) => { if (n < 1) fail('Admin menu lacks "' + ADMIN_ONLY[i] + '"'); });

  // …GardenUser must NOT (falsifier: a leaf without the grant renders anyway)
  await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenUser`, { waitUntil: 'networkidle' });
  await pg.waitForSelector('#idmp-tree .idmp-node, #idmp-tree div', { timeout: 25000 }).catch(() => fail('user menu never rendered'));
  const usrHas = await Promise.all(ADMIN_ONLY.map(t => pg.locator('#idmp-tree >> text="' + t + '"').count()));
  console.log('  GardenUser DOM: ' + ADMIN_ONLY.map((t, i) => t + '=' + usrHas[i]).join(' · '));
  usrHas.forEach((n, i) => { if (n > 0) fail('FALSIFIER fired — GardenUser sees ungranted "' + ADMIN_ONLY[i] + '"'); });

  console.log(pass
    ? '🟢 W-AD-MENU-PRF-LIVE PASS — P/R/X menu leaves are pruned by real process/form grants per role in the live DOM'
    : '🔴 W-AD-MENU-PRF-LIVE FAIL');
  await br.close(); server.close(); process.exit(pass ? 0 : 2);
})().catch(e => { console.log('🔴 ' + e.message); process.exit(1); });
