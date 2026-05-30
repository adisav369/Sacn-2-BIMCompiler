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
  // tiny static server for build/erp/ — content-types so the sql.js bundle (.wasm/.db/.js) loads
  const MIME = { '.html': 'text/html', '.js': 'application/javascript', '.wasm': 'application/wasm', '.db': 'application/octet-stream', '.json': 'application/json' };
  const server = http.createServer((req, res) => {
    const f = path.join(ROOT, req.url === '/' ? 'glassbowl.html' : decodeURIComponent(req.url.split('?')[0]));
    fs.readFile(f, (e, d) => { if (e) { res.writeHead(404); res.end('nf'); } else { res.writeHead(200, { 'Content-Type': MIME[path.extname(f)] || 'application/octet-stream' }); res.end(d); } });
  });
  await new Promise(r => server.listen(PORT, r));
  console.log('═══ GLASSBOWL wiring check — headless Chromium on http://localhost:' + PORT + ' ═══\n');

  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
  const errs = [], logs = [];
  page.on('pageerror', e => errs.push(e.message));
  page.on('console', m => { logs.push(m.text()); if (m.type() === 'error') errs.push('console:' + m.text()); });

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

  // click a node → a RECENT-ITEMS bar drops into the stack. SVG <g> trips Playwright's visibility
  // precheck, so we fire a real DOM click event on the group — exercises the click→pick→pushBar path.
  await page.evaluate(() => document.querySelector('#svg g.node').dispatchEvent(new MouseEvent('click', { bubbles: true })));
  let detail = await page.locator('#stack').innerText();
  check(/this is/i.test(detail) && detail.length > 30, 'click a bubble → recent-items bar populates', detail.replace(/\n/g, ' ').slice(0, 70));

  // the HOT reconciliation cell (c_invoice) → business language: matching=yes + times used (op-log depth)
  await page.evaluate(() => window.pick('c_invoice'));
  detail = await page.locator('#stack').innerText().then(t => t.replace(/\s+/g, ' '));
  check(/Invoice/i.test(detail), 'c_invoice bar names it in plain language', detail.slice(0, 60));
  check(/reconciliation:\s*yes/i.test(detail), 'c_invoice shows "needs matching/reconciliation: yes"', /reconciliation[^]{0,8}/i.exec(detail) ? RegExp.lastMatch : '?');
  check(/times used|tracked \d+ run/i.test(detail), 'c_invoice shows usage / op-log depth annotation', detail.slice(0, 90));
  const shot2 = path.join(ROOT, 'glassbowl_invoice.png');
  await page.screenshot({ path: shot2 });

  // a sales-flow cell (c_order) → plain-English actions
  await page.evaluate(() => window.pick('c_order'));
  const odetail = await page.locator('#stack').innerText();
  check(/creates a shipment|completes the order/i.test(odetail), 'c_order bar shows plain-English actions', odetail.replace(/\n/g, ' ').slice(0, 80));

  // ── FOCUS filter: clicking a bubble shows only its own links (Order → its lines), dims the rest ──
  const dimmedEdges = await page.evaluate(() => [...document.querySelectorAll('#svg line')].filter(l => +l.getAttribute('stroke-opacity') < 0.05).length);
  const litFocusEdges = await page.evaluate(() => [...document.querySelectorAll('#svg line')].filter(l => +l.getAttribute('stroke-opacity') > 0.8).length);
  check(dimmedEdges > 0 && litFocusEdges > 0, 'click focuses a bubble — its links stay, the rest dim away', 'lit=' + litFocusEdges + ' dimmed=' + dimmedEdges);
  // click empty background → focus clears, edges return to normal
  await page.evaluate(() => document.getElementById('svg').dispatchEvent(new MouseEvent('click', { bubbles: true })));
  await page.waitForTimeout(40);
  const dimmedAfter = await page.evaluate(() => [...document.querySelectorAll('#svg line')].filter(l => +l.getAttribute('stroke-opacity') < 0.05).length);
  check(dimmedAfter === 0, 'click empty space clears the focus filter', 'dimmed=' + dimmedAfter);

  // ── RecentChanges accordion: bars stack, minimise keeps the title, ✕ dismisses ──
  const barCount = await page.locator('#stack .bar').count();
  check(barCount >= 3, 'recent-items stack accumulates a bar per look-up', 'bars=' + barCount);
  // minimise the top bar (header click) → it collapses but KEEPS its title (you can return)
  await page.locator('#stack .bar').first().locator('.bh').click();
  await page.waitForTimeout(40);
  const topCollapsed = await page.locator('#stack .bar').first().evaluate(e => e.classList.contains('col'));
  const topTitle = await page.locator('#stack .bar').first().locator('.bh b').innerText();
  check(topCollapsed && topTitle.length > 0, 'minimise rolls a bar to a title-only bar that remains', 'collapsed=' + topCollapsed + ' title="' + topTitle + '"');
  // dismiss a bar (✕) → it leaves the stack
  await page.locator('#stack .bar').first().locator('.bx').click();
  await page.waitForTimeout(200);
  const barAfter = await page.locator('#stack .bar').count();
  check(barAfter === barCount - 1, '✕ dismisses a bar from the recent log', barCount + '→' + barAfter);

  // ── interactivity: About panel, drag, zoom, reset ──
  await page.locator('#aboutBtn').click();
  await page.waitForTimeout(60);
  const aboutOpen = await page.locator('#about').evaluate(e => e.classList.contains('open'));
  const aboutTxt = await page.locator('#about').innerText();
  check(aboutOpen && /Sales flow/i.test(aboutTxt), 'About panel opens with the business explainer', aboutTxt.replace(/\n/g, ' ').slice(0, 60));
  await page.locator('#aboutBtn').click();   // close again

  // ── info panel collapses (the appendix ⓘ moved into the panel; toolbar is now just Trace+Reset) ──
  const panelW0 = await page.locator('#panel').evaluate(e => e.getBoundingClientRect().width);
  await page.locator('#panelToggle').click();
  await page.waitForTimeout(220);
  const panelW1 = await page.locator('#panel').evaluate(e => e.getBoundingClientRect().width);
  check(panelW0 > 300 && panelW1 < 5, 'info panel collapses → graph takes the full canvas', Math.round(panelW0) + '→' + Math.round(panelW1));
  await page.locator('#panelToggle').click();
  await page.waitForTimeout(220);
  const panelW2 = await page.locator('#panel').evaluate(e => e.getBoundingClientRect().width);
  check(panelW2 > 300, 'info panel expands back', 'w=' + Math.round(panelW2));
  // drag the gripper → the panel resizes wider (sizable margin)
  const grip = await page.locator('#pgrip').boundingBox();
  const wBefore = await page.locator('#panel').evaluate(e => e.getBoundingClientRect().width);
  await page.mouse.move(grip.x + 3, grip.y + grip.height / 2);
  await page.mouse.down(); await page.mouse.move(grip.x + 3 - 100, grip.y + grip.height / 2, { steps: 6 }); await page.mouse.up();
  await page.waitForTimeout(60);
  const wAfter = await page.locator('#panel').evaluate(e => e.getBoundingClientRect().width);
  check(wAfter > wBefore + 40, 'drag the gripper resizes the info panel (sizable)', Math.round(wBefore) + '→' + Math.round(wAfter));

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

  // ── Phase 2a: LINEAGE TRACE (W-LIFECYCLE) — trace lights exactly the 5-doc chain for order 101 ──
  await page.locator('#traceBtn').click();
  await page.waitForSelector('#strip.open', { timeout: 4000 }).catch(() => {});
  const stripOpen = await page.locator('#strip').evaluate(e => e.classList.contains('open'));
  const stripTxt = (await page.locator('#strip').innerText()).replace(/\s+/g, ' ');
  check(stripOpen, 'trace opens the step-strip', stripTxt.slice(0, 70));
  const stepCount = await page.locator('#strip .step').count();
  check(stepCount === 5, 'step-strip shows the 5 lifecycle documents', 'steps=' + stepCount);
  // the strip carries the REAL document numbers + the partial-payment amount (extracted, not invented)
  check(/80001/.test(stripTxt) && /200001/.test(stripTxt) && /98\.50/.test(stripTxt), 'step-strip shows real doc numbers + amounts (80001, 200001, 98.50)', stripTxt.slice(0, 120));
  // exactly the 5 chain bubbles are LIT (full-opacity), the rest dimmed — and the edges between them
  const litNodes = await page.evaluate(() => [...document.querySelectorAll('#svg circle')].filter(c => +c.getAttribute('fill-opacity') === 1).length);
  check(litNodes === 5, 'exactly 5 bubbles lit (the chain), rest dimmed', 'lit=' + litNodes);
  const litEdges = await page.locator('#svg line.litedge').count();
  check(litEdges >= 4, 'derivation/settlement edges between the chain bubbles are lit', 'litEdges=' + litEdges);
  const shotT = path.join(ROOT, 'glassbowl_trace.png');
  await page.screenshot({ path: shotT });
  // the sql.js DATA BUNDLE cross-check: in-browser re-walk of glassbowl_data.db must AGREE with the chain
  await page.waitForFunction(() => window.__xchecked, { timeout: 5000 }).catch(() => {});
  const xc = logs.find(l => /§LIFECYCLE-XCHECK/.test(l)) || '';
  check(/agree=Y/.test(xc), 'sql.js bundle re-walk AGREES with the chain (§LIFECYCLE-XCHECK)', xc.slice(0, 90) || 'no xcheck log');
  // exit trace → strip closes, all bubbles bright again
  await page.locator('#stripX').click();
  await page.waitForTimeout(60);
  const stripClosed = await page.locator('#strip').evaluate(e => !e.classList.contains('open'));
  const litAfter = await page.evaluate(() => [...document.querySelectorAll('#svg circle')].filter(c => +c.getAttribute('fill-opacity') === 1).length);
  check(stripClosed && litAfter > 5, 'exit trace restores the full map', 'closed=' + stripClosed + ' bright=' + litAfter);

  // ── Phase 2-viz: ORBIT (W-ORBIT) — the trackball arranges the view; bubbles stay static, at-rest is flat ──
  const ballBox = await page.locator('#ball').boundingBox();
  check(!!ballBox, 'trackball sphere present at the bottom', ballBox ? 'at x=' + Math.round(ballBox.x) : 'missing');
  const cxRest = await page.$$eval('#svg circle', cs => cs.map(c => +c.getAttribute('cx')).sort((a, b) => a - b).join(','));
  const yawRest = await page.evaluate(() => window.__yaw);
  check(yawRest === 0, 'starts at rest (yaw=0) — the static flat bowl persists', 'yaw=' + yawRest);
  // drag the ball → yaw changes and the depth planes shear (node screen-x positions move)
  await page.mouse.move(ballBox.x + ballBox.width / 2, ballBox.y + ballBox.height / 2);
  await page.mouse.down(); await page.mouse.move(ballBox.x + ballBox.width / 2 + 80, ballBox.y + ballBox.height / 2 + 24, { steps: 8 }); await page.mouse.up();
  await page.waitForTimeout(60);
  const yawOrbit = await page.evaluate(() => window.__yaw);
  const cxOrbit = await page.$$eval('#svg circle', cs => cs.map(c => +c.getAttribute('cx')).sort((a, b) => a - b).join(','));
  check(Math.abs(yawOrbit) > 0.1, 'trackball drag orbits the camera (yaw changes)', 'yaw=' + yawOrbit.toFixed(2));
  check(cxRest !== cxOrbit, 'orbit shears the depth planes apart (bubbles move on screen, static in 3D)', 'positions changed=' + (cxRest !== cxOrbit));
  const shotO = path.join(ROOT, 'glassbowl_orbit.png');
  await page.screenshot({ path: shotO });
  // reset → back to the at-rest flat view, pixel-identical (W-ORBIT: identity at yaw=pitch=0)
  await page.locator('#resetBtn').click();
  await page.waitForTimeout(60);
  const yawReset = await page.evaluate(() => window.__yaw);
  const cxReset = await page.$$eval('#svg circle', cs => cs.map(c => +c.getAttribute('cx')).sort((a, b) => a - b).join(','));
  check(yawReset === 0 && cxReset === cxRest, 'reset restores the at-rest flat bowl (identity projection at rest)', 'yaw=' + yawReset + ' restored=' + (cxReset === cxRest));

  // toggle reference spine off → fewer lines (legend filter wired)
  const before = await page.locator('#svg line').count();
  await page.locator('#legend input[type=checkbox]').nth(3).uncheck();   // 'reference' is 4th in the legend
  await page.waitForTimeout(200);
  const after = await page.locator('#svg line').count();
  check(after < before, 'legend toggle filters edges (reference off → fewer lines)', before + '→' + after);
  check(after === 7 + 10 + 32, 'remaining edges == the 3 structural spines (49)', 'after=' + after);
  const shot3 = path.join(ROOT, 'glassbowl_spines.png');
  await page.screenshot({ path: shot3 });
  console.log('\n   screenshots: ' + [shot1, shot2, shot3, shotT, shotO].map(s => path.basename(s)).join(', '));

  await browser.close();
  server.close();
  console.log('\n═══ VERDICT ═══');
  console.log('§GLASSBOWL-WIRING ' + (fails ? 'FAIL — ' + fails + ' checks red' : 'PASS — engine graph paints + is interactive in a real browser'));
  process.exit(fails ? 1 : 0);
})();
