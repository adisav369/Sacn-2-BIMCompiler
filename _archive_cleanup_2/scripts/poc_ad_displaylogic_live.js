// W-AD-DISPLAYLOGIC-LIVE (UI_UNPARK_RESUME.md B-1 §1) — prove the idempiere.html record form hides
// fields whose AD DisplayLogic is FALSE for the open record, via the PROVEN evaluator (ad_evaluator.js,
// W-LOGIC-EVAL 3044/3044). Sales Order 100: 27 of 60 displayed fields carry false logic (e.g.
// ChargeAmt[@HasCharges@='Y']) → they MUST NOT render; §AD-DISPLAYLOGIC-LIVE names every hidden col.
// NAMED RESIDUAL: window-context vars (@OrderType@, @$Element_*@ — set by callout/session in real
// iDempiere) are unpopulated here → their expressions evaluate against the record only (empty-context
// false = faithful Evaluator semantics, not a parser gap).
// Run: ERP_ROOT=/tmp/wt-uibridge/erp node scripts/poc_ad_displaylogic_live.js  (default ~/bim-ootb/erp)
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
  const lines = [];
  pg.on('console', m => { const t = m.text(); if (t.indexOf('§AD-DISPLAYLOGIC-LIVE') === 0) { lines.push(t); console.log('  ' + t.slice(0, 200) + '…'); } });
  await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenAdmin&window=143&record=100`, { waitUntil: 'networkidle' });
  await pg.waitForSelector('#idmp-form', { timeout: 20000 });
  const shown = await pg.$$eval('#idmp-form .idmp-fld', es => es.length);
  const chargeAmt = await pg.locator('#idmp-form [data-ad-column="ChargeAmt"]').count();   // @HasCharges@='Y' false
  const docNo = await pg.locator('#idmp-form [data-ad-column="DocumentNo"]').count();      // no logic → must render
  const hid = lines.length && /hidden=(\d+)/.exec(lines[0]);
  console.log('  shown=' + shown + ' hiddenByLogic=' + (hid ? hid[1] : 0) + ' ChargeAmt-rendered=' + chargeAmt + ' DocumentNo-rendered=' + docNo);
  const pass = lines.length > 0 && hid && Number(hid[1]) > 0 && chargeAmt === 0 && docNo === 1;
  console.log(pass
    ? '🟢 W-AD-DISPLAYLOGIC-LIVE PASS — live form hides fields by real AD DisplayLogic via the proven evaluator (falsifier: no-logic field still renders)'
    : '🔴 W-AD-DISPLAYLOGIC-LIVE FAIL');
  await br.close(); server.close(); process.exit(pass ? 0 : 2);
})().catch(e => { console.log('🔴 ' + e.message); process.exit(1); });
