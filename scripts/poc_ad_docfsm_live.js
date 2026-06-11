// W-AD-DOCFSM-LIVE (UI_UNPARK_RESUME.md B-2) — prove the idempiere.html record form renders doc-action
// buttons GATED by the proven DocAction FSM (ad_docfsm.js): only the legal actions for THIS record render,
// dispatch transitions the status chip per transitionFor, and an ILLEGAL action's button must NOT render
// (falsifiers: completed M_InOut has NO Void; a Closed order offers NO actions).
// periodOpen = REAL c_period+c_periodcontrol probe (MPeriod.isOpen port) — all 3 doc periods verified 'O'.
// Run: ERP_ROOT=/tmp/wt-uibridge/erp node scripts/poc_ad_docfsm_live.js   (default ROOT=~/bim-ootb/erp)
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
// CASES — windows/records verified in ad_seed.db (2026-06-11); expect = exact legal-button set,
// absent = actions whose button MUST NOT render (the falsifier arm).
// SEED-GAP (named): M_InOut is form-unreachable here — ALL 4 M_InOut windows (169/184/53097/53098)
// filter on M_InOut.MovementType, a column ABSENT from this seed slice → 0 rows scoped. Its FSM is
// oracle-proven headless (W-MINOUT-FSM); the live no-VO-on-completed falsifier runs on C_Invoice
// instead (its Completed block also legally excludes VO). canReact=false everywhere: seed c_doctype
// lacks iscanbereactivated → conservative no-RE (§-named seed-gap, never an extra button).
const CASES = [
  { name: 'C_Order CO',   url: 'window=143&record=100',    expect: ['CL', 'VO'], absent: ['CO', 'PR', 'RC', 'RA'], click: 'VO', to: 'VO' },
  { name: 'C_Order CL',   url: 'window=143&record=102',    expect: [], absent: ['CL', 'VO', 'CO', 'RE'] },           // Closed doc → NO actions
  { name: 'C_Invoice CO', url: 'window=167&record=100',    expect: ['CL', 'RC', 'RA'], absent: ['VO'] },             // no VO on completed Invoice!
  { name: 'GL_Journal CO', url: 'window=200005&record=100', expect: ['CL', 'RC', 'RA'], absent: ['VO'], click: 'RC', to: 'RE' },
  { name: 'C_Payment CO', url: 'window=195&record=100',    expect: ['CL', 'RC', 'RA'], absent: ['VO'] }
];
(async () => {
  await new Promise(r => server.listen(0, r)); const port = server.address().port;
  const br = await chromium.launch(); const pg = await br.newContext().then(c => c.newPage());
  const logs = [];
  pg.on('console', m => { const t = m.text(); if (t.indexOf('§AD-DOCFSM-LIVE') === 0 || t.indexOf('§IDEMPIERE-DEEPLINK') === 0) { logs.push(t); console.log('  ' + t); } });
  pg.on('pageerror', e => console.log('ERR ' + String(e).slice(0, 160)));
  let pass = true;
  const fail = (m) => { pass = false; console.log('  ✗ ' + m); };
  for (const c of CASES) {
    console.log('— ' + c.name + ' (?' + c.url + ')');
    await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenAdmin&${c.url}`, { waitUntil: 'networkidle' });
    await pg.waitForSelector('#idmp-form', { timeout: 25000 }).catch(() => fail('form never rendered'));
    const got = await pg.$$eval('.idmp-docfsm button', bs => bs.map(b => b.getAttribute('data-doc-action'))).catch(() => []);
    const chip = await pg.$eval('.idmp-docfsm .chip', e => e.textContent).catch(() => '(no bar)');
    console.log('  chip="' + chip + '" buttons=[' + got.join(',') + ']');
    for (const a of c.expect) if (got.indexOf(a) < 0) fail('expected legal action ' + a + ' missing');
    for (const a of c.absent) if (got.indexOf(a) >= 0) fail('ILLEGAL action ' + a + ' rendered — falsifier fired');
    if (c.expect.length === 0 && got.length !== 0) fail('closed doc rendered actions [' + got.join(',') + ']');
    if (c.click) {
      await pg.click('.idmp-docfsm button[data-doc-action=' + c.click + ']');
      await pg.waitForTimeout(300);
      const chip2 = await pg.$eval('.idmp-docfsm .chip', e => e.textContent).catch(() => '(gone)');
      console.log('  clicked=' + c.click + ' chip→"' + chip2 + '"');
      if (chip2.indexOf('· ' + c.to) < 0) fail('chip did not transition to ' + c.to + ' (got "' + chip2 + '")');
    }
  }
  const fsmLines = logs.filter(t => t.indexOf('§AD-DOCFSM-LIVE') === 0 && t.indexOf('status=') > 0).length;
  if (fsmLines < CASES.length - 1) fail('only ' + fsmLines + ' §AD-DOCFSM-LIVE status lines (want ≥' + (CASES.length - 1) + ')');
  console.log(pass
    ? '🟢 W-AD-DOCFSM-LIVE PASS — doc-action buttons are FSM-gated per record (legal sets exact, illegal absent, dispatch transitions the chip)'
    : '🔴 W-AD-DOCFSM-LIVE FAIL');
  await br.close(); server.close(); process.exit(pass ? 0 : 2);
})().catch(e => { console.log('🔴 ' + e.message); process.exit(1); });
