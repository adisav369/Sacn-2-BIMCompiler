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
  // clean slate: a prior run's persisted scene must not leak into this run's assertions
  await page.evaluate(() => { try { localStorage.clear(); } catch (e) {} });
  await page.reload({ waitUntil: 'networkidle' });
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
  const areaRest = await page.$$eval('#svg circle', cs => { const xs = cs.map(c => +c.getAttribute('cx')), ys = cs.map(c => +c.getAttribute('cy')); return (Math.max(...xs) - Math.min(...xs)) * (Math.max(...ys) - Math.min(...ys)); });
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
  // W-ARRANGE (the trackball's main job): orbiting organises the bubbles into the spread layered CUBE — the
  // 2D screen area they occupy grows vs the blob (they space out, not bunch). Angle-independent (unlike x-span).
  const areaOrbit = await page.$$eval('#svg circle', cs => { const xs = cs.map(c => +c.getAttribute('cx')), ys = cs.map(c => +c.getAttribute('cy')); return (Math.max(...xs) - Math.min(...xs)) * (Math.max(...ys) - Math.min(...ys)); });
  check(areaOrbit > areaRest * 1.1, 'W-ARRANGE: orbiting spreads the bubbles into the layered cube (screen area grows)', 'restArea=' + Math.round(areaRest / 1000) + 'k orbitArea=' + Math.round(areaOrbit / 1000) + 'k');
  const shotO = path.join(ROOT, 'glassbowl_orbit.png');
  await page.screenshot({ path: shotO });
  // reset → back to the at-rest flat view, pixel-identical (W-ORBIT: identity at yaw=pitch=0)
  await page.locator('#resetBtn').click();
  await page.waitForTimeout(60);
  const yawReset = await page.evaluate(() => window.__yaw);
  const cxReset = await page.$$eval('#svg circle', cs => cs.map(c => +c.getAttribute('cx')).sort((a, b) => a - b).join(','));
  check(yawReset === 0 && cxReset === cxRest, 'reset restores the at-rest flat bowl (identity projection at rest)', 'yaw=' + yawReset + ' restored=' + (cxReset === cxRest));

  // ── Reset RE-HOMES dragged bubbles (not just the camera) ──
  await page.evaluate(() => window.setTrace(false));
  await page.waitForTimeout(30);
  const homeCx = await page.$$eval('#svg circle', cs => cs.map(c => +c.getAttribute('cx')).sort((a, b) => a - b).join(','));
  const cb2 = await page.locator('#svg circle').first().boundingBox();
  await page.mouse.move(cb2.x + cb2.width / 2, cb2.y + cb2.height / 2);
  await page.mouse.down(); await page.mouse.move(cb2.x + 160, cb2.y + 130, { steps: 6 }); await page.mouse.up();
  await page.waitForTimeout(40);
  const movedCx = await page.$$eval('#svg circle', cs => cs.map(c => +c.getAttribute('cx')).sort((a, b) => a - b).join(','));
  await page.locator('#resetBtn').click(); await page.waitForTimeout(40);
  const rehomeCx = await page.$$eval('#svg circle', cs => cs.map(c => +c.getAttribute('cx')).sort((a, b) => a - b).join(','));
  check(movedCx !== homeCx && rehomeCx === homeCx, 'reset re-homes dragged bubbles (not just the camera)', 'moved=' + (movedCx !== homeCx) + ' rehomed=' + (rehomeCx === homeCx));

  // ── click a lifecycle bubble → its recent bar offers "trace this flow" ──
  await page.evaluate(() => window.pick('c_order'));
  const traceBtns = await page.locator('#stack .bar').first().locator('.tracebtn').count();
  check(traceBtns === 1, 'lifecycle bubble bar offers a "trace this flow" button', 'btn=' + traceBtns);
  await page.locator('#stack .bar').first().locator('.tracebtn').click();
  await page.waitForTimeout(50);
  check(await page.locator('#strip').evaluate(e => e.classList.contains('open')), 'clicking the bubble’s trace button opens the trace', 'opened');

  // ── record SEARCH: pick a different order from the bundle → trace THAT record in-browser ──
  const opts = await page.$$eval('#reclist option', os => os.map(o => o.value));
  const other = opts.find(v => v !== '80001');
  check(opts.length > 1 && !!other, 'record search datalist populated from the bundle (multiple records)', 'opts=' + opts.length + ' other=' + other);
  if (other) {
    await page.evaluate((v) => { const i = document.getElementById('recsearch'); i.value = v; window.doSearch(); }, other);
    await page.waitForTimeout(50);
    const stripDoc = (await page.locator('#strip').innerText()).replace(/\s+/g, ' ');
    check(stripDoc.includes(other), 'searching a record traces THAT record (step-strip updates)', stripDoc.slice(0, 80));
  }
  await page.evaluate(() => window.setTrace(false));

  // ── Task 1 (W-DATA-BARS): the REAL rows surface in the clicked bubble's accordion bar ──
  // PROVES: the map (FK structure) is augmented with the actual records, lazy-loaded from the bundle —
  //         c_invoice's bar must contain the §0.19 invoice (documentno 200001, grandtotal 100.70), not invented.
  await page.evaluate(() => window.pick('c_invoice'));
  await page.waitForFunction(() => {
    const bar = document.querySelector('#stack .bar[data-id="c_invoice"] .recrows[data-rows]');
    return !!bar;
  }, { timeout: 6000 }).catch(() => {});
  const invRows = await page.evaluate(() => {
    const b = document.querySelector('#stack .bar[data-id="c_invoice"] .recrows');
    return b ? { txt: b.innerText.replace(/\s+/g, ' '), n: +(b.getAttribute('data-rows') || 0) } : { txt: '', n: 0 };
  });
  check(invRows.n > 0 && /200001/.test(invRows.txt) && /100\.70/.test(invRows.txt),
    'W-DATA-BARS: c_invoice bar surfaces the REAL row (200001 + 100.70) from the bundle',
    'rows=' + invRows.n + ' txt="' + invRows.txt.slice(0, 70) + '"');
  check(/the actual records/i.test(invRows.txt),
    'W-DATA-BARS: bundle bar is headed "the actual records"', invRows.txt.slice(0, 40));

  // a NON-bundle table (gl_journal is a real graph node, NOT in the bundle whitelist) → honest note, no fake rows
  await page.evaluate(() => window.pick('gl_journal'));
  await page.waitForFunction(() => {
    const b = document.querySelector('#stack .bar[data-id="gl_journal"] .recrows');
    return b && /not carried in this dataset/i.test(b.innerText);
  }, { timeout: 4000 }).catch(() => {});
  const glTxt = await page.evaluate(() => {
    const b = document.querySelector('#stack .bar[data-id="gl_journal"] .recrows');
    return b ? b.innerText.replace(/\s+/g, ' ') : '';
  });
  const glHasRows = await page.evaluate(() => !!document.querySelector('#stack .bar[data-id="gl_journal"] .recrows[data-rows]'));
  check(/not carried in this dataset/i.test(glTxt) && !glHasRows,
    'W-DATA-BARS: a non-bundle table shows the honest "not carried" note (no fake rows)',
    'note="' + glTxt.slice(0, 50) + '" fakeRows=' + glHasRows);

  // ── Task 3 (W-DOSSIER): the right-click DOSSIER — Data | Rules | Columns | History, all EXTRACTED ──
  // PROVES: the 5–7 iDempiere windows fuse into one movable panel; each tab populates from data alone;
  //         History's ↶ reversal preview is PURE-READ (className toggle, no localStorage/db write — the T3 seam).

  // gen-side op-log is real + non-empty (G is the inlined graph var) — the History tab's data source.
  const oplogLen = await page.evaluate(() => (window.G ? G.oplog.length : 0));
  check(oplogLen > 0, 'W-DOSSIER: graph.oplog is present + non-empty (real kernel_ops inlined)', 'oplog=' + oplogLen);

  // right-click semantics via the exposed API: openDossier('c_invoice') opens the panel with ≥3 tabs
  await page.evaluate(() => window.openDossier('c_invoice'));
  await page.waitForTimeout(40);
  const dossOpen = await page.locator('#dossier').evaluate(e => e.classList.contains('open'));
  const tabCount = await page.locator('#dossier .dtab').count();
  check(dossOpen && tabCount >= 3, 'W-DOSSIER: openDossier opens #dossier with ≥3 tabs', 'open=' + dossOpen + ' tabs=' + tabCount);

  // Data tab (default) → real bundle row 200001 surfaces (waits for the lazy sql.js bundle)
  await page.waitForFunction(() => /200001/.test(document.getElementById('dossierBody').innerText), { timeout: 6000 }).catch(() => {});
  const dataTxt = await page.locator('#dossierBody').innerText().then(t => t.replace(/\s+/g, ' '));
  check(/200001/.test(dataTxt), 'W-DOSSIER: Data tab shows the REAL invoice 200001 from the bundle', dataTxt.slice(0, 60));

  // Rules tab → lists ≥1 validation rule, and the count must EQUAL the gen-side §DOSSIER inlined count
  const genRuleCount = await page.evaluate(() => { const n = G.nodes.find(x => x.id === 'c_invoice'); return n.dossier.ruleCount; });
  await page.locator('#dossier .dtab', { hasText: 'Rules' }).click();
  await page.waitForTimeout(40);
  const ruleRows = await page.locator('#dossier .rule').count();
  check(ruleRows >= 1 && ruleRows === genRuleCount, 'W-DOSSIER: Rules tab lists the rules, count == gen-side §DOSSIER count', 'shown=' + ruleRows + ' gen=' + genRuleCount);

  // Columns tab → ad_column metadata (≥1 column, FK target rendered)
  await page.locator('#dossier .dtab', { hasText: 'Columns' }).click();
  await page.waitForTimeout(40);
  const colRows = await page.locator('#dossier .col').count();
  check(colRows >= 1, 'W-DOSSIER: Columns tab renders ad_column metadata', 'cols=' + colRows);

  // History tab → ≥1 real op row; ↶ preview adds .reversed WITHOUT any localStorage/db write (pure-read)
  await page.evaluate(() => { try { localStorage.clear(); } catch (e) {} });
  const lsBefore = await page.evaluate(() => localStorage.length);
  await page.locator('#dossier .dtab', { hasText: 'History' }).click();
  await page.waitForTimeout(40);
  const opRows = await page.locator('#dossier .oprow').count();
  check(opRows >= 1, 'W-DOSSIER: History tab renders ≥1 real op from graph.oplog', 'ops=' + opRows);
  await page.locator('#dossier .oprow .oprev').first().click();
  await page.waitForTimeout(40);
  const reversed = await page.locator('#dossier .oprow.reversed').count();
  const noteTxt = await page.locator('#dossier .oprow.reversed .opnote').first().innerText().catch(() => '');
  const lsAfter = await page.evaluate(() => localStorage.length);
  check(reversed === 1 && /would reverse \d+ tracked op/i.test(noteTxt) && lsAfter === lsBefore,
    'W-DOSSIER: ↶ preview greys the row (read-only) — no localStorage write',
    'reversed=' + reversed + ' note="' + noteTxt.slice(0, 50) + '" ls=' + lsBefore + '→' + lsAfter);
  // ↷ un-greys (the read-only undo/redo preview, both directions)
  await page.locator('#dossier .oprow.reversed .oprev').first().click();
  await page.waitForTimeout(40);
  const reversedAfter = await page.locator('#dossier .oprow.reversed').count();
  check(reversedAfter === 0, 'W-DOSSIER: ↷ un-greys the row (reversal preview toggles both ways)', 'reversed=' + reversedAfter);
  const shotD = path.join(ROOT, 'glassbowl_dossier.png');
  await page.screenshot({ path: shotD });
  // close the dossier so it does not occlude later assertions
  await page.locator('#dossier .dx').click();
  await page.waitForTimeout(30);

  // ── Task 2 (W-ACTIONS): DOUBLE-TAP a bubble → the ACTION BLURB (the friendly per-bubble chooser) ──
  // PROVES: double-click opens an anchored blurb of REAL actions; lifecycle bubbles offer Trace, the CRUD
  //         teaser is visible but inert (read-only T3 seam), the blurb follows the bubble on orbit + hides on bg.
  await page.evaluate(() => { window.setOrbit(0, 0); window.hideBlurb && window.hideBlurb(); });
  // dblclick a node <g> → #blurb opens with ≥2 action buttons (single-click path stays untouched)
  await page.evaluate(() => document.querySelector('#svg g.node').dispatchEvent(new MouseEvent('dblclick', { bubbles: true })));
  await page.waitForTimeout(40);
  const blurbOpen = await page.locator('#blurb').evaluate(e => e.classList.contains('open') && getComputedStyle(e).display !== 'none');
  const blurbActs = await page.locator('#blurb .act:not(.crud)').count();
  check(blurbOpen && blurbActs >= 2, 'W-ACTIONS: double-tap a bubble opens #blurb with ≥2 action buttons', 'open=' + blurbOpen + ' acts=' + blurbActs);

  // the CRUD teaser is rendered but DISABLED (visible future, inert now — no handler, no write)
  const crudCount = await page.locator('#blurb .act.crud.disabled').count();
  const crudTitle = await page.locator('#blurb .act.crud.disabled').first().getAttribute('title').catch(() => '');
  check(crudCount >= 1 && /coming later \(T3\)/i.test(crudTitle || ''), 'W-ACTIONS: CRUD teaser shown greyed/disabled (read-only T3 seam)', 'crud=' + crudCount + ' title="' + (crudTitle || '') + '"');

  // open the blurb for a LIFECYCLE bubble (c_order) → its ▸ Trace button opens the strip
  await page.evaluate(() => { window.setTrace(false); window.openBlurb('c_order'); });
  await page.waitForTimeout(30);
  const orderTrace = await page.locator('#blurb .act', { hasText: 'Trace' }).count();
  check(orderTrace === 1, 'W-ACTIONS: a lifecycle bubble (c_order) blurb offers ▸ Trace', 'traceBtn=' + orderTrace);
  await page.locator('#blurb .act', { hasText: 'Trace' }).first().click();
  await page.waitForTimeout(50);
  check(await page.locator('#strip').evaluate(e => e.classList.contains('open')), 'W-ACTIONS: clicking the blurb’s ▸ Trace opens the lifecycle strip (#strip.open)', 'opened');
  await page.evaluate(() => window.setTrace(false));

  // a NON-lifecycle bubble (c_bpartner, a reference master) OMITS Trace but still offers ≥2 read actions
  await page.evaluate(() => window.openBlurb('c_bpartner'));
  await page.waitForTimeout(30);
  const bpTrace = await page.locator('#blurb .act', { hasText: 'Trace' }).count();
  const bpActs = await page.locator('#blurb .act:not(.crud)').count();
  check(bpTrace === 0 && bpActs >= 2, 'W-ACTIONS: a non-lifecycle bubble omits Trace (offers View data / Dossier)', 'trace=' + bpTrace + ' acts=' + bpActs);

  // the blurb FOLLOWS the bubble on orbit — its left position changes when the camera orbits
  await page.evaluate(() => window.openBlurb('c_order'));
  await page.waitForTimeout(30);
  const blurbLeft0 = await page.locator('#blurb').evaluate(e => e.style.left);
  await page.evaluate(() => window.setOrbit(0.6, 0.2));
  await page.waitForTimeout(40);
  const blurbLeft1 = await page.locator('#blurb').evaluate(e => e.style.left);
  check(blurbLeft0 && blurbLeft1 && blurbLeft0 !== blurbLeft1, 'W-ACTIONS: the blurb follows the bubble on orbit (left changes)', 'left ' + blurbLeft0 + '→' + blurbLeft1);
  await page.evaluate(() => window.setOrbit(0, 0));

  // background click hides the blurb (dismiss on click-away). A real click-away is pointerdown (resets the
  // drag/moved flag) then click on the empty background — mirror that so the !moved-gated handler fires.
  await page.evaluate(() => { const s = document.getElementById('svg'); s.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, clientX: 5, clientY: 5 })); s.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, clientX: 5, clientY: 5 })); s.dispatchEvent(new MouseEvent('click', { bubbles: true })); });
  await page.waitForTimeout(40);
  const blurbHidden = await page.locator('#blurb').evaluate(e => !e.classList.contains('open'));
  check(blurbHidden, 'W-ACTIONS: background click hides the action blurb (dismiss on click-away)', 'hidden=' + blurbHidden);

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

  // ── Task 4 (W-SWIPE-PICKER): the swipe/scroll record list at the trackball (mobile-first, less typing) ──
  // PROVES: the typed search is AUGMENTED (not replaced) by a scrollable, tappable list of real bundle orders;
  //         tapping a non-seed row traces THAT record — zero keyboard. (issue: no-typing record pick on mobile)
  await page.evaluate(() => window.setTrace(true));
  await page.waitForFunction(() => document.querySelectorAll('#recpick .rpr').length > 1, { timeout: 6000 }).catch(() => {});
  const pickOpen = await page.locator('#recpick').evaluate(e => e.classList.contains('open') && getComputedStyle(e).display !== 'none');
  const pickRows = await page.locator('#recpick .rpr').count();
  check(pickOpen && pickRows > 1, 'W-SWIPE-PICKER: trace mode shows a scrollable record list (>1 real rows) at the trackball', 'open=' + pickOpen + ' rows=' + pickRows);
  const pickTxt = await page.locator('#recpick').innerText().then(t => t.replace(/\s+/g, ' '));
  check(/80001/.test(pickTxt), 'W-SWIPE-PICKER: the list carries real bundle rows incl. the seed (80001)', pickTxt.slice(0, 70));
  const scrollable = await page.locator('#recpick').evaluate(e => e.scrollHeight > e.clientHeight + 2);
  check(scrollable, 'W-SWIPE-PICKER: the list is vertically scrollable (touch pan-y — more rows than fit)', 'scrollable=' + scrollable);
  // the typed search is AUGMENTED, not removed — the datalist stays populated (desktop fallback unbroken)
  const dlOpts = await page.locator('#reclist option').count();
  check(dlOpts > 1, 'W-SWIPE-PICKER: the typed #recsearch datalist is preserved (augment, not replace)', 'opts=' + dlOpts);
  // TAP a non-seed row → it traces THAT record (the step-strip updates to its documentno), no keyboard
  const otherIdx = await page.evaluate(() => { const rows = [...document.querySelectorAll('#recpick .rpr')]; for (let i = 0; i < rows.length; i++) { if (!/80001/.test(rows[i].innerText)) return i; } return -1; });
  if (otherIdx >= 0) {
    const otherDn = (await page.locator('#recpick .rpr').nth(otherIdx).locator('b').innerText()).replace(/[^0-9]/g, '');
    await page.locator('#recpick .rpr').nth(otherIdx).click();
    await page.waitForTimeout(60);
    const pickStrip = (await page.locator('#strip').innerText()).replace(/\s+/g, ' ');
    check(pickStrip.includes(otherDn), 'W-SWIPE-PICKER: tapping a non-seed row traces THAT record (strip updates, no typing)', 'tapped #' + otherDn + ' strip="' + pickStrip.slice(0, 50) + '"');
  }
  const swipeLog = logs.find(l => /§SWIPE-PICKER/.test(l)) || '';
  check(/rows=\d+/.test(swipeLog) && /seed=80001/.test(swipeLog), 'W-SWIPE-PICKER: §SWIPE-PICKER whitebox log confirms real rows + seed', swipeLog.slice(0, 70));
  const shotP = path.join(ROOT, 'glassbowl_swipe.png');
  await page.screenshot({ path: shotP });
  await page.evaluate(() => window.setTrace(false));

  // ── Task 5 (W-AUDIO): WebAudio "ear candy" — synthesized, mute toggle persisted, gesture-unlock ──
  // PROVES: a persisted mute toggle exists; a pick schedules a WebAudio node (AudioContext created + node
  //         scheduled); mute silences scheduling — jive on mundane work that never blocks interaction.
  const muteExists = await page.locator('#muteBtn').count();
  check(muteExists === 1, 'W-AUDIO: a mute toggle button exists', 'muteBtn=' + muteExists);
  // first-gesture unlock: a real pointerdown lazily creates the AudioContext (autoplay policy honoured)
  await page.evaluate(() => window.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true })));
  await page.waitForTimeout(30);
  const ctxMade = await page.evaluate(() => !!(window.__audio && window.__audio.ctx));
  check(ctxMade, 'W-AUDIO: first user gesture unlocks the AudioContext (created lazily, not at load)', 'ctx=' + ctxMade);
  // a pick schedules a WebAudio gain node → the scheduled counter increments (sound is actually synthesized)
  const schedBefore = await page.evaluate(() => window.__audioScheduled || 0);
  await page.evaluate(() => { window.__audio.muted = false; window.pick('c_order'); });
  await page.waitForTimeout(30);
  const schedAfter = await page.evaluate(() => window.__audioScheduled || 0);
  check(schedAfter > schedBefore, 'W-AUDIO: a pick schedules a WebAudio node (ear candy is real, not a stub)', schedBefore + '→' + schedAfter);
  // MUTE silences scheduling — with muted=true a pick schedules NOTHING (respects the user)
  await page.evaluate(() => { window.__audio.muted = true; });
  const mB = await page.evaluate(() => window.__audioScheduled || 0);
  await page.evaluate(() => window.pick('c_invoice'));
  await page.waitForTimeout(30);
  const mA = await page.evaluate(() => window.__audioScheduled || 0);
  check(mA === mB, 'W-AUDIO: mute silences scheduling (a muted pick schedules no node)', mB + '→' + mA);
  await page.evaluate(() => { window.__audio.muted = false; });
  // the mute state is PERSISTED in the scene localStorage (survives a refresh)
  await page.evaluate(() => { try { localStorage.clear(); } catch (e) {} window.toggleMute(); });   // mute on → save()
  const mutePersisted = await page.evaluate(() => { try { return JSON.parse(localStorage.getItem('glassbowl.scene') || '{}').mute === true; } catch (e) { return false; } });
  check(mutePersisted, 'W-AUDIO: mute is persisted in the scene localStorage (survives refresh)', 'persisted=' + mutePersisted);
  await page.evaluate(() => { window.toggleMute(); try { localStorage.clear(); } catch (e) {} });   // back to unmuted + clean slate
  const audioLog = logs.find(l => /§AUDIO unlocked/.test(l)) || '';
  check(/§AUDIO unlocked/.test(audioLog), 'W-AUDIO: §AUDIO unlocked whitebox log confirms the context came up', audioLog.slice(0, 60));

  // ── Task 6 (W-QR-INPUT): scan a QR carrying a record id → trace/open it (read-only slice) ──
  // PROVES: a camera/QR affordance exists; an unsupported browser shows an honest fallback (never broken-silent);
  //         a decoded payload matching a bundle record traces it; closing the panel stops the stream.
  const qrBtnExists = await page.locator('#qrbtn').count();
  check(qrBtnExists === 1, 'W-QR-INPUT: a camera/QR affordance exists in the input panel', 'qrbtn=' + qrBtnExists);
  // the QR affordance rides the trace-mode input panel (#recwrap) — enter trace so it is visible
  await page.evaluate(() => window.setTrace(true));
  await page.waitForSelector('#qrbtn', { state: 'visible', timeout: 4000 }).catch(() => {});
  const qrSup = await page.evaluate(() => ('BarcodeDetector' in window) && !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia));
  await page.locator('#qrbtn').click();
  await page.waitForTimeout(90);
  const camOpen = await page.locator('#qrcam').evaluate(e => e.classList.contains('open'));
  const qstat = await page.locator('#qrstatus').innerText().then(t => t.replace(/\s+/g, ' '));
  if (!qrSup) check(camOpen && /not supported/i.test(qstat), 'W-QR-INPUT: unsupported browser shows the honest "not supported" fallback (no broken-silent feature)', 'open=' + camOpen + ' status="' + qstat.slice(0, 50) + '"');
  else check(camOpen && /(point the camera|unavailable|denied)/i.test(qstat), 'W-QR-INPUT: supported browser opens the camera affordance with an honest status', 'open=' + camOpen + ' status="' + qstat.slice(0, 50) + '"');
  await page.evaluate(() => window.qrClose());
  // the lookup SLICE (pure-read): a decoded payload matching a bundle record traces it — no documentno typed
  await page.evaluate(() => window.setTrace(true));
  await page.waitForFunction(() => window.__recs > 0, { timeout: 6000 }).catch(() => {});
  const qrTraced = await page.evaluate(() => window.qrLookup('80002'));   // 80002 → order 102, a non-seed record
  await page.waitForTimeout(60);
  const qrStrip = (await page.locator('#strip').innerText()).replace(/\s+/g, ' ');
  check(qrTraced === true && /80002/.test(qrStrip), 'W-QR-INPUT: a decoded payload matching a bundle record traces it (scan→find, read-only)', 'traced=' + qrTraced + ' strip="' + qrStrip.slice(0, 50) + '"');
  // an UNMATCHED payload returns false honestly (never fabricates a record — extract-only)
  const qrMiss = await page.evaluate(() => window.qrLookup('NOPE-9999'));
  check(qrMiss === false, 'W-QR-INPUT: an unmatched payload returns false (no faked record)', 'miss=' + qrMiss);
  // closing the panel STOPS the camera stream (no hot camera left running) — proven with a stub track
  const streamStopped = await page.evaluate(() => {
    let stopped = false;
    window.__qr.stream = { getTracks: () => [{ stop: () => { stopped = true; } }] };
    window.__qr.scanning = true;
    window.qrClose();
    return stopped && window.__qr.stream === null;
  });
  check(streamStopped, 'W-QR-INPUT: closing the panel stops the camera stream (no hot camera left running)', 'stopped=' + streamStopped);
  const qrLog = logs.find(l => /§QR-INPUT supported=/.test(l)) || '';
  check(/§QR-INPUT supported=/.test(qrLog), 'W-QR-INPUT: §QR-INPUT support-detection whitebox log emitted at load', qrLog.slice(0, 50));
  await page.evaluate(() => window.setTrace(false));

  // ── SCENE PERSISTENCE: the screen survives a hard refresh (continue exactly where you left off) ──
  await page.evaluate(() => { window.setOrbit(0.6, 0.25); window.pick('c_payment'); });
  await page.waitForTimeout(40);
  const barsPre = await page.locator('#stack .bar').count();
  await page.evaluate(() => window.dispatchEvent(new Event('pagehide')));   // triggers save()
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForSelector('#svg circle', { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(120);
  const restored = await page.evaluate(() => window.__restored === true);
  const yawR = await page.evaluate(() => window.__yaw);
  const barsPost = await page.locator('#stack .bar').count();
  check(restored && Math.abs(yawR - 0.6) < 0.02 && barsPost >= 1 && barsPost === barsPre, 'scene SURVIVES a hard refresh (orbit + recent-items log restored)', 'restored=' + restored + ' yaw=' + (yawR || 0).toFixed(2) + ' bars=' + barsPre + '→' + barsPost);
  await page.evaluate(() => { try { localStorage.clear(); } catch (e) {} });   // leave a clean slate

  // ── MOBILE: the yellow border handle toggles the panel; the About panel stays in frame ──
  // PROVES: the small yellow #phandle at the panel border slides the panel open/closed on tap (mobile-friendly,
  //         large target); the "What am I looking at" panel is bounded to the viewport (its bottom is NOT cut off).
  const handleExists = await page.locator('#phandle').count();
  const handleColor = await page.locator('#phandle').evaluate(e => getComputedStyle(e).backgroundColor);
  check(handleExists === 1 && /255,\s*212,\s*121/.test(handleColor), 'MOBILE: yellow panel handle present at the border (#ffd479)', 'handle=' + handleExists + ' bg=' + handleColor);
  // tap the handle → the panel collapses; tap again → it opens (the slide-open/closed toggle)
  await page.evaluate(() => window.setPanelCollapsed(false));
  await page.waitForTimeout(60);
  const wWide = await page.locator('#panel').evaluate(e => e.getBoundingClientRect().width);
  await page.locator('#phandle').click(); await page.waitForTimeout(220);
  const wCollapsed = await page.locator('#panel').evaluate(e => e.getBoundingClientRect().width);
  await page.locator('#phandle').click(); await page.waitForTimeout(220);
  const wReopen = await page.locator('#panel').evaluate(e => e.getBoundingClientRect().width);
  check(wWide > 200 && wCollapsed < 5 && wReopen > 200, 'MOBILE: tapping the yellow handle slides the panel closed, tapping again opens it', wWide + '→' + wCollapsed + '→' + wReopen);

  // mobile viewport: a FRESH load starts with the panel collapsed; the About panel stays fully in frame.
  // (neuter setItem first so the pagehide/beforeunload save() can't re-persist a scene during the reload —
  //  otherwise the reloaded page would "restore" and never count as a fresh load.)
  await page.setViewportSize({ width: 390, height: 720 });
  await page.evaluate(() => { try { localStorage.clear(); localStorage.setItem = function () {}; } catch (e) {} });
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForSelector('#svg circle', { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(120);
  const mobileCollapsed = await page.locator('#wrap').evaluate(e => e.classList.contains('collapsed'));
  check(mobileCollapsed, 'MOBILE: a fresh load on a small screen starts with the panel collapsed (graph gets the screen)', 'collapsed=' + mobileCollapsed);
  // open the About panel → its bottom must be within the frame (the reported overflow is fixed)
  await page.evaluate(() => document.getElementById('about').classList.add('open'));
  await page.waitForTimeout(60);
  const ab = await page.evaluate(() => { const r = document.getElementById('about').getBoundingClientRect(); return { top: Math.round(r.top), bottom: Math.round(r.bottom), ih: window.innerHeight }; });
  check(ab.bottom <= ab.ih && ab.top >= 0, 'MOBILE: the "What am I looking at" panel stays fully in frame (bottom not cut off)', 'top=' + ab.top + ' bottom=' + ab.bottom + ' vh=' + ab.ih);
  const shotM = path.join(ROOT, 'glassbowl_mobile.png');
  await page.screenshot({ path: shotM });
  // the About panel has a close (✕) that dismisses it (reachable when it fills the small screen)
  await page.locator('#about .aboutx').click();
  await page.waitForTimeout(40);
  const abClosed = await page.locator('#about').evaluate(e => !e.classList.contains('open'));
  check(abClosed, 'MOBILE: the About panel has a close (✕) that dismisses it', 'closed=' + abClosed);
  await page.evaluate(() => { try { localStorage.clear(); } catch (e) {} });

  await browser.close();
  server.close();
  console.log('\n═══ VERDICT ═══');
  console.log('§GLASSBOWL-WIRING ' + (fails ? 'FAIL — ' + fails + ' checks red' : 'PASS — engine graph paints + is interactive in a real browser'));
  process.exit(fails ? 1 : 0);
})();
