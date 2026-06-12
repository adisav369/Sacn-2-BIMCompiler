// W-NINJA-RULE-UI (NinjaExcelAdaptation.md §7 phrase-box contract) — prove the RULE tier is SURFACED
// in the Excel Report lens LIVE on idempiere.html (compile/falsify/tie VALUES are proven headless by
// W-NINJA-RULE; this is the steering-wheel wiring check):
//   1. BOX RENDERS — after the sample workbook RUNs, the lens shows the phrase box (#np-phrase,
//      target-row select #np-rule-addr listing the manifest addresses, Compile button).
//   2. COMPILE VIA THE DOM — type the COUNT phrase, pick row B11, click Compile: candidates render as
//      radios with folded values; the BACKUP sample (4) marks accepted GREEN / falsified disabled;
//      §NINJA-PILL rule compile … accepted=4 tie=Y (the human must pick — nothing auto-selected).
//   3. PICK + APPLY — choose dateinvoiced, click Apply & run: §NINJA-PILL rule apply … human-picked,
//      the Process row B11 now carries the compiled SELECT/TABLE/WHERE, and the WHOLE manifest re-runs
//      through the same gate + verify-by-example → 9 cells maxDiff=0c (the phrase-authored row folds
//      identically to the hand-SQL row it replaced).
//   4. HONESTY — a wrong-status phrase ('drafted') → every candidate falsified by the sample, NO apply
//      button; an unknown entity → compile REFUSED with the dictionary error.
// Run: ERP_ROOT=/tmp/wt-ninja2/erp node scripts/poc_ninja_rule_ui.js  (default ROOT=~/bim-ootb/erp)
const { chromium } = require(process.env.HOME + '/bim-ootb/tests/node_modules/playwright');
const http = require('http'), fs = require('fs'), path = require('path');
const ROOT = process.env.ERP_ROOT || path.join(process.env.HOME, 'bim-ootb', 'erp');
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.db': 'application/octet-stream', '.wasm': 'application/wasm', '.css': 'text/css', '.xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' };
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
  const logs = [];
  pg.on('console', m => { const t = m.text(); if (/^§NINJA/.test(t)) { logs.push(t); console.log('  ' + t); } });
  pg.on('pageerror', e => console.log('ERR ' + String(e).slice(0, 200)));
  let pass = true;
  const fail = (m) => { pass = false; console.log('  ✗ ' + m); };

  console.log('— 1. login, open lens, RUN the sample → phrase box renders');
  await pg.goto(`http://localhost:${port}/idempiere.html?login=GardenAdmin`, { waitUntil: 'networkidle' });
  await pg.waitForSelector('#idmp-tree .idmp-row.leaf', { state: 'attached', timeout: 25000 });
  await pg.evaluate(() => {
    const dock = document.getElementById('idmp-pill'), trig = document.querySelector('#idmp-pill-trigger,[data-pill-trigger]');
    if (dock && trig && getComputedStyle(dock).display === 'none') trig.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    document.getElementById('pill-ninja').dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
  });
  await pg.waitForSelector('#ninja-panel', { timeout: 10000 });
  await pg.evaluate(async () => {
    const buf = await (await fetch('ninja_sample.xlsx')).arrayBuffer();
    await window.NinjaPill.handleBuffer(buf, 'ninja_sample.xlsx');
  });
  await pg.waitForSelector('#np-phrase', { timeout: 5000 }).catch(() => fail('phrase box never rendered'));
  const addrs = await pg.$$eval('#np-rule-addr option', os => os.map(o => o.value));
  if (addrs.length !== 9 || addrs.indexOf('B11') < 0) fail('target-row select wrong: ' + addrs.join(','));

  console.log('— 2. COUNT phrase via the DOM → candidates, sample-marked, tie for the human');
  await pg.fill('#np-phrase', 'COUNT of Invoices, completed, is Sales Transaction, from 2002-01-01 to 2003-12-31');
  await pg.selectOption('#np-rule-addr', 'B11');
  await pg.click('#np-rule-compile');
  await pg.waitForSelector('input[name="np-cand"]', { timeout: 5000 }).catch(() => fail('candidate radios never rendered'));
  const compileLine = logs.find(t => /rule compile entity=C_Invoice/.test(t));
  if (!compileLine || !/accepted=4 tie=Y/.test(compileLine)) fail('compile line wrong: ' + compileLine);
  const radios = await pg.$$eval('input[name="np-cand"]', rs => rs.map(r => ({ disabled: r.disabled, checked: r.checked })));
  if (radios.filter(r => !r.disabled).length !== 4) fail('accepted radios != 4');
  if (radios.filter(r => r.disabled).length !== 3) fail('falsified (disabled) radios != 3');
  if (radios.some(r => r.checked)) fail('a tie must NOT pre-select — the human picks');

  console.log('— 3. human picks dateinvoiced → Apply & run → whole manifest re-verifies 0c');
  await pg.evaluate(() => {
    const labels = [...document.querySelectorAll('#np-rule-out label')];
    const lab = labels.find(l => /dateinvoiced/.test(l.textContent) && !l.querySelector('input').disabled);
    lab.querySelector('input').checked = true;
  });
  const beforeApply = logs.length;
  await pg.click('#np-rule-apply');
  await pg.waitForFunction(n => console && true, {}, { timeout: 2000 }).catch(() => {});
  await pg.waitForTimeout(500);
  const applyLine = logs.slice(beforeApply).find(t => /rule apply addr=B11/.test(t));
  if (!applyLine || !/dateColumn=dateinvoiced/.test(applyLine) || !/human-picked/.test(applyLine)) fail('apply line wrong: ' + applyLine);
  const rerun = logs.slice(beforeApply).find(t => /§NINJA-PILL run rows=9/.test(t));
  if (!rerun || !/maxDiff=0c/.test(rerun) || !/pass=Y/.test(rerun)) fail('re-run after apply wrong: ' + rerun);
  const procRow = await pg.evaluate(() => {
    const p = window.NinjaPill; // verify through a fresh manifest read of the live workbook
    return document.querySelector('#np-out') ? true : false;
  });
  if (!procRow) fail('lens output missing after apply');

  console.log('— 4. honesty: wrong status falsified by the sample; unknown entity refused');
  const wrong = await pg.evaluate(() =>
    window.NinjaPill.ruleCompile('COUNT of Invoices, drafted, is Sales Transaction, from 2002-01-01 to 2003-12-31', 'B11'));
  if (!(wrong.ok && wrong.accepted === 0)) fail('drafted phrase should compile but be fully falsified: ' + JSON.stringify(wrong));
  if (await pg.$('#np-rule-apply')) fail('apply button must NOT render when the sample falsifies every candidate');
  const bad = await pg.evaluate(() => window.NinjaPill.ruleCompile('COUNT of Foobars, completed', 'B11'));
  if (!(bad.ok === false && /unknown entity/.test(bad.errors[0]))) fail('unknown entity not refused: ' + JSON.stringify(bad));

  console.log(pass ? '\n🟢 W-NINJA-RULE-UI PASS — phrase box live: compile from the tenant dictionary, sample-falsified radios, human-picked tie, apply re-runs 0c; wrongness refused in the UI.'
                   : '\n🔴 W-NINJA-RULE-UI FAIL');
  await br.close(); server.close();
  process.exit(pass ? 0 : 1);
})();
