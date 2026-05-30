'use strict';
/**
 * test_glassbowl.js — FIRST-TIME wiring check for the Glassbowl engine explorer.
 *   Spec/witness: docs/GLASSBOWL.md (W-GLASSBOWL). This is a SECONDARY (wiring) check per
 *   CLAUDE.md — the PRIMARY proof is §GLASSBOWL in build/erp/system_explorer.log (data extracted
 *   from ad_full/erp_rules/kernel_ops, 0 hand-authored). Here we only prove the SVG actually
 *   PAINTS and is interactive: bubbles drawn, edges drawn, legend present, click→detail populates.
 *
 *   Issue it proves: "the generated glassbowl.html renders the engine graph in a real browser and
 *   responds to a node click" — i.e. the inlined data + force layout + click handler are wired,
 *   not just syntactically valid.
 *
 * Run (from deploy/dev/tests): node test_glassbowl.js   (serves build/erp on a local port headless)
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const ROOT = path.join(__dirname, '..', '..', '..', 'build', 'erp');
const PORT = 8137;
let fails = 0;
function check(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

(async () => {
  // tiny static server for build/erp/
  const server = http.createServer((req, res) => {
    const f = path.join(ROOT, req.url === '/' ? 'glassbowl.html' : decodeURIComponent(req.url.split('?')[0]));
    fs.readFile(f, (e, d) => { if (e) { res.writeHead(404); res.end('nf'); } else { res.writeHead(200, { 'Content-Type': f.endsWith('.html') ? 'text/html' : 'application/json' }); res.end(d); } });
  });
  await new Promise(r => server.listen(PORT, r));
  console.log('═══ GLASSBOWL wiring check — headless Chromium on http://localhost:' + PORT + ' ═══\n');

  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
  const errs = [];
  page.on('pageerror', e => errs.push(e.message));
  page.on('console', m => { if (m.type() === 'error') errs.push('console:' + m.text()); });

  await page.goto('http://localhost:' + PORT + '/glassbowl.html', { waitUntil: 'networkidle' });
  await page.waitForSelector('#svg circle', { timeout: 8000 }).catch(() => {});

  const circles = await page.locator('#svg circle').count();
  const lines = await page.locator('#svg line').count();
  const labels = await page.locator('#svg text').count();
  const legendBoxes = await page.locator('#legend input[type=checkbox]').count();
  const coldText = await page.locator('#coldbox').innerText().catch(() => '');

  check(errs.length === 0, 'no page/console errors', errs.length ? errs.slice(0, 3).join(' | ') : 'clean');
  check(circles >= 13, 'bubbles painted (>=13 doc nodes)', 'circles=' + circles);
  check(lines >= 40, 'FK edges painted', 'lines=' + lines);
  check(labels >= 13, 'node labels painted', 'texts=' + labels);
  check(legendBoxes === 4, 'spine legend has 4 toggles', 'checkboxes=' + legendBoxes);
  check(/155/.test(coldText), 'cold-backlog badge shows 155', coldText.replace(/\n/g, ' ').slice(0, 60));

  // force layout actually SPREAD the nodes (not all stacked at center) — proves the sim ran.
  const bbox = await page.evaluate(() => {
    const cs = [...document.querySelectorAll('#svg circle')];
    const xs = cs.map(c => +c.getAttribute('cx')), ys = cs.map(c => +c.getAttribute('cy'));
    return { spanX: Math.max(...xs) - Math.min(...xs), spanY: Math.max(...ys) - Math.min(...ys) };
  });
  check(bbox.spanX > 300 && bbox.spanY > 200, 'force layout spread the bubbles (sim converged)', 'spanX=' + Math.round(bbox.spanX) + ' spanY=' + Math.round(bbox.spanY));

  // screenshot #1: the default bowl
  const shot1 = path.join(ROOT, 'glassbowl_render.png');
  await page.screenshot({ path: shot1 });

  // click a node → detail panel populates. SVG <g> trips Playwright's visibility precheck, so we
  // fire a real DOM click event on the group — this genuinely exercises the inline onclick→pick path.
  await page.evaluate(() => document.querySelector('#svg g.node').dispatchEvent(new MouseEvent('click', { bubbles: true })));
  let detail = await page.locator('#detail').innerText();
  check(/table/i.test(detail) && detail.length > 40, 'click a bubble → detail panel populates', detail.replace(/\n/g, ' ').slice(0, 70));

  // pick the HOT settlement cell (c_invoice) → must surface matcher=Y + its oracle citation
  await page.evaluate(() => window.pick('c_invoice'));
  detail = await page.locator('#detail').innerText();
  check(/C_Invoice/.test(detail), 'c_invoice detail names its cell', detail.replace(/\n/g, ' ').slice(0, 60));
  check(/matcher\s*Y/i.test(detail.replace(/\s+/g, ' ')), 'c_invoice shows matcher=Y (the settlement cell)', /matcher[^]{0,8}/i.exec(detail.replace(/\n/g, ' ')) ? RegExp.lastMatch : '?');
  check(/MInvoice|gravity/i.test(detail), 'c_invoice shows oracle/gravity annotation', detail.replace(/\n/g, ' ').slice(0, 90));
  const shot2 = path.join(ROOT, 'glassbowl_invoice.png');
  await page.screenshot({ path: shot2 });

  // pick a derivation cell (c_order) → matcher=N + a derivation verb
  await page.evaluate(() => window.pick('c_order'));
  const odetail = await page.locator('#detail').innerText();
  check(/createShipment|completeOrder/.test(odetail), 'c_order shows derivation verbs', odetail.replace(/\n/g, ' ').slice(0, 80));

  // toggle reference spine off → fewer lines (proves the legend filter is wired)
  const before = await page.locator('#svg line').count();
  await page.locator('#legend input[type=checkbox]').nth(3).uncheck();   // 'reference' is 4th
  await page.waitForTimeout(200);
  const after = await page.locator('#svg line').count();
  check(after < before, 'legend toggle filters edges (reference off → fewer lines)', before + '→' + after);
  // with reference off, remaining edges == containment+derivation+settlement
  const expectedSpine = 7 + 10 + 32;
  check(after === expectedSpine, 'remaining edges == the 3 structural spines (7+10+32)', 'after=' + after + ' expected=' + expectedSpine);
  const shot3 = path.join(ROOT, 'glassbowl_spines.png');
  await page.screenshot({ path: shot3 });
  console.log('\n   screenshots: ' + [shot1, shot2, shot3].map(s => path.basename(s)).join(', '));

  await browser.close();
  server.close();
  console.log('\n═══ VERDICT ═══');
  console.log('§GLASSBOWL-WIRING ' + (fails ? 'FAIL — ' + fails + ' checks red' : 'PASS — engine graph paints + is interactive in a real browser'));
  process.exit(fails ? 1 : 0);
})();
