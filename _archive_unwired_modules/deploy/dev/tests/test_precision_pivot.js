// ⚠ DO NOT REMOVE — Scope: prove PRECISION_PIVOT.md — 🏺 Auto-Pivot (re-center orbit on drag-end).
// Witnesses (whitebox §-log — read the log, not the exit code):
//   W-PIVOT-TOGGLE   : toggleCamPivot() emits §pivot ON / §pivot OFF.
//   W-PIVOT-RECENTER : while ON, toggle-on + each controls 'end' emits one
//                      §pivot recenter target=(x,y,z) hit=mesh|bbox|ahead; while OFF none appear.
// Building loaded so the screen-centre ray hits a real mesh (hit=mesh).
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const URL = 'http://localhost:8080/dev/index.html?db=/buildings/Duplex_extracted.db&lib=/buildings/Duplex_library.db';
const LOG = path.join(__dirname, 'log', 'precision_pivot.log');

(async () => {
  const lines = [];
  const rec = (s) => { lines.push(s); };
  const browser = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage();
  page.on('console', m => rec(m.text()));
  page.on('pageerror', e => rec('§PAGEERROR ' + e.message));

  rec('§TEST_LOAD url=' + URL);
  await page.goto(URL, { waitUntil: 'load', timeout: 30000 }).catch(e => rec('§GOTO_FAIL ' + e.message));
  await page.waitForFunction(() => window.APP && window.APP.scene && window.APP.controls, { timeout: 20000 }).catch(e => rec('§APP_FAIL ' + e.message));
  // let the building stream so the centre ray has meshes to hit
  await page.waitForFunction(() => {
    const el = document.getElementById('s-active');
    return el && (el.textContent.includes('DONE') || el.style.color === 'rgb(68, 204, 68)');
  }, { timeout: 45000, polling: 500 }).catch(e => rec('§STREAM_FAIL ' + e.message));

  const wired = await page.evaluate(() => typeof window.toggleCamPivot === 'function').catch(() => false);
  rec('§TEST_WIRED toggleCamPivot=' + wired);

  // Phase 1: toggle ON → expect §pivot ON + one immediate §pivot recenter
  await page.evaluate(() => { console.log('§MARK on-toggle'); window.toggleCamPivot(); });
  await page.waitForTimeout(300);

  // Phase 2: simulate a drag finishing → OrbitControls fires 'end' → expect a 2nd recenter
  await page.evaluate(() => { console.log('§MARK drag-end'); window.APP.controls.dispatchEvent({ type: 'end' }); });
  await page.waitForTimeout(300);

  // Phase 3: toggle OFF → §pivot OFF, then a drag-end must produce NO new recenter (listener detached)
  await page.evaluate(() => { console.log('§MARK off-toggle'); window.toggleCamPivot(); });
  await page.waitForTimeout(200);
  await page.evaluate(() => { console.log('§MARK drag-end-after-off'); window.APP.controls.dispatchEvent({ type: 'end' }); });
  await page.waitForTimeout(300);

  await browser.close();

  fs.mkdirSync(path.dirname(LOG), { recursive: true });
  fs.writeFileSync(LOG, lines.join('\n'));

  // --- Verdict from the log ---
  const txt = lines.join('\n');
  const on  = /§pivot ON/.test(txt);
  const off = /§pivot OFF/.test(txt);
  // recenter count between off-toggle marker and end = must stay 0 after OFF
  const idxOff = lines.findIndex(l => l.includes('§MARK off-toggle'));
  const after  = idxOff >= 0 ? lines.slice(idxOff) : [];
  const recenterAfterOff = after.filter(l => /§pivot recenter/.test(l)).length;
  const recenterTotal = lines.filter(l => /§pivot recenter/.test(l)).length;
  const hitMesh = /§pivot recenter .*hit=mesh/.test(txt);
  const err = (txt.match(/§PAGEERROR/g) || []).length;

  console.log('--- VERDICT (precision pivot) ---');
  console.log('W-wired      toggleCamPivot exists :', wired ? 'PASS' : 'FAIL');
  console.log('W-PIVOT-TOGGLE  §pivot ON          :', on ? 'PASS' : 'FAIL');
  console.log('W-PIVOT-TOGGLE  §pivot OFF         :', off ? 'PASS' : 'FAIL');
  console.log('W-PIVOT-RECENTER total recenters   :', recenterTotal >= 2 ? 'PASS (' + recenterTotal + ')' : 'FAIL (' + recenterTotal + ', want >=2)');
  console.log('W-PIVOT-RECENTER hit=mesh seen     :', hitMesh ? 'PASS' : 'WARN (no mesh hit — empty view?)');
  console.log('W-PIVOT-RECENTER none after OFF    :', recenterAfterOff === 0 ? 'PASS' : 'FAIL (' + recenterAfterOff + ')');
  console.log('boot errors                        :', err === 0 ? 'PASS (0)' : 'FAIL (' + err + ')');
  console.log('log saved                          :', LOG);
})();
