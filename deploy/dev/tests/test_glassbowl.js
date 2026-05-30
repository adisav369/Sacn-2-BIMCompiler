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

  // click a node → inspector populates. SVG <g> trips Playwright's visibility precheck, so we fire
  // a real DOM click event on the group — this genuinely exercises the click→pick path.
  await page.evaluate(() => document.querySelector('#svg g.node').dispatchEvent(new MouseEvent('click', { bubbles: true })));
  let detail = await page.locator('#detail').innerText();
  check(/this is/i.test(detail) && detail.length > 30, 'click a bubble → inspector populates', detail.replace(/\n/g, ' ').slice(0, 70));

  // the HOT reconciliation cell (c_invoice) → business language: matching=yes + times used
  await page.evaluate(() => window.pick('c_invoice'));
  detail = await page.locator('#detail').innerText().then(t => t.replace(/\s+/g, ' '));
  check(/Invoice/i.test(detail), 'c_invoice inspector names it in plain language', detail.slice(0, 60));
  check(/reconciliation:\s*yes/i.test(detail), 'c_invoice shows "needs matching/reconciliation: yes"', /reconciliation[^]{0,8}/i.exec(detail) ? RegExp.lastMatch : '?');
  check(/times used/i.test(detail), 'c_invoice shows usage annotation', detail.slice(0, 90));
  const shot2 = path.join(ROOT, 'glassbowl_invoice.png');
  await page.screenshot({ path: shot2 });

  // a sales-flow cell (c_order) → plain-English actions
  await page.evaluate(() => window.pick('c_order'));
  const odetail = await page.locator('#detail').innerText();
  check(/creates a shipment|completes the order/i.test(odetail), 'c_order shows plain-English actions', odetail.replace(/\n/g, ' ').slice(0, 80));

  // ── interactivity: About panel, drag, zoom, reset ──
  await page.locator('#aboutBtn').click();
  await page.waitForTimeout(60);
  const aboutOpen = await page.locator('#about').evaluate(e => e.classList.contains('open'));
  const aboutTxt = await page.locator('#about').innerText();
  check(aboutOpen && /Sales flow/i.test(aboutTxt), 'About panel opens with the business explainer', aboutTxt.replace(/\n/g, ' ').slice(0, 60));
  await page.locator('#aboutBtn').click();   // close again

  // DRAG a bubble → node positions change (the layout is no longer static)
  const cxBefore = await page.$$eval('#svg circle', cs => cs.map(c => +c.getAttribute('cx')).sort((a, b) => a - b).join(','));
  const cbox = await page.locator('#svg circle').first().boundingBox();
  await page.mouse.move(cbox.x + cbox.width / 2, cbox.y + cbox.height / 2);
  await page.mouse.down(); await page.mouse.move(cbox.x + cbox.width / 2 + 100, cbox.y + cbox.height / 2 + 50, { steps: 6 }); await page.mouse.up();
  await page.waitForTimeout(80);
  const cxAfter = await page.$$eval('#svg circle', cs => cs.map(c => +c.getAttribute('cx')).sort((a, b) => a - b).join(','));
  check(cxBefore !== cxAfter, 'drag a bubble moves it (layout is interactive, not static)', 'positions changed=' + (cxBefore !== cxAfter));

  // ZOOM via wheel → viewport scale grows
  await page.mouse.move(450, 400);
  await page.mouse.wheel(0, -360);
  await page.waitForTimeout(60);
  const tZoom = await page.locator('#vp').getAttribute('transform');
  const scale = parseFloat((tZoom.match(/scale\(([0-9.]+)\)/) || [])[1] || '1');
  check(scale > 1.05, 'scroll wheel zooms in (viewport scale > 1)', 'transform=' + tZoom);

  // RESET view → transform back to identity
  await page.locator('#resetBtn').click();
  await page.waitForTimeout(60);
  const tReset = (await page.locator('#vp').getAttribute('transform')).replace(/\s+/g, '');
  check(/translate\(0,0\)scale\(1\)/.test(tReset), 'reset view restores the default viewport', tReset);

  // toggle reference spine off → fewer lines (legend filter wired)
  const before = await page.locator('#svg line').count();
  await page.locator('#legend input[type=checkbox]').nth(3).uncheck();   // 'reference' is 4th in the legend
  await page.waitForTimeout(200);
  const after = await page.locator('#svg line').count();
  check(after < before, 'legend toggle filters edges (reference off → fewer lines)', before + '→' + after);
  check(after === 7 + 10 + 32, 'remaining edges == the 3 structural spines (49)', 'after=' + after);
  const shot3 = path.join(ROOT, 'glassbowl_spines.png');
  await page.screenshot({ path: shot3 });
  console.log('\n   screenshots: ' + [shot1, shot2, shot3].map(s => path.basename(s)).join(', '));

  await browser.close();
  server.close();
  console.log('\n═══ VERDICT ═══');
  console.log('§GLASSBOWL-WIRING ' + (fails ? 'FAIL — ' + fails + ' checks red' : 'PASS — engine graph paints + is interactive in a real browser'));
  process.exit(fails ? 1 : 0);
})();
