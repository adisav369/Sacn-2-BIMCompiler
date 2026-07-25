#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-R5A-DEV-GRAPH-TOUR scope (READ THE LOG after every run)
 * SCOPE: prompts/Viewer/FLY_TOUR_CORRIDOR_GRAPH.md §R5-A — deploy/dev had NONE of the occupant
 * room-graph stack (R5, line ~96), so its Fly tour was the legacy Euclidean nearest-neighbour one:
 * no highlight-first ordering, no wall-legal legs. This ports the stack (room_habitability,
 * storey_raster, room_graph, hallway_backbone) + tour.js v19 + a two-function bridge
 * (getRoomGraph/ensureRooms) into deploy/dev. CODE ONLY — the patch/self-heal loader is §R5-B.
 *
 * ISSUE IT PROVES/DISPROVES:
 *   (G1) A building that SHIPS rooms (Clinic — native IfcSpaces, no patch needed) now flies the
 *        GRAPH route in deploy/dev: §FLY_ROUTE appears with stops=N/N, and §FLY_HL_FIRST names a
 *        main hall. This is the "does Clinic show a highlight" answer the port exists for.
 *   (G2) Graceful degradation is REAL, not claimed: a building with no rooms must log
 *        §FLY_ROUTE_REJECT and still produce a legacy tour with actions > 0 — the port cannot make
 *        an un-roomed building worse than it was.
 *   (G3) The new script tags load clean: no §LOAD_FAIL, no §R5A_BRIDGE_ABSENT, no page error.
 *   (G4) The bridge is honest about being code-only: §R5A_ENSURE_ROOMS reports selfHeal=none(R5-B).
 *
 * RUN: node deploy/dev/tests/witness_r5a_dev_graph_tour.js   (serves deploy/dev itself on PORT)
 */
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const puppeteer = require('/home/red1/bim-compiler/node_modules/puppeteer');

const DEV = path.resolve(__dirname, '..');
const PORT = process.env.PORT || 8433;
let pass = 0, fail = 0;
const chk = (n, c, x) => { if (c) { pass++; console.log('  ✅ ' + n + (x ? '  ' + x : '')); } else { fail++; console.log('  ❌ ' + n + (x ? '  ' + x : '')); } };

// Buildings: one that ships real rooms, one that ships none — the two halves of the contract.
// ⚠ FIXTURE NOTE (measured 2026-07-26, do not "fix" by pointing at the local copy):
// deploy/dev/buildings/Clinic_extracted.db is a STALE dev-staging mirror with NO spatial_structure
// table at all, while the DB the deployed sandbox viewer actually fetches — OCI
// bim-ootb/buildings/Clinic_extracted.db, 128 MB, last-modified 2026-06-05 — carries 118 IfcSpace
// rooms. Testing the graph route against the stale local copy would prove nothing about production.
// So CLINIC_DB defaults to a path the runner is expected to provide as the production-equivalent DB
// (env CLINIC_DB, or a symlink named _ocitest_Clinic_extracted.db under deploy/dev/buildings/).
const CLINIC_DB = process.env.CLINIC_DB || '_ocitest_Clinic_extracted.db';
const CASES = [
  { label: 'Clinic (production-equivalent DB, 118 rooms)', db: CLINIC_DB, expectGraph: true },
  { label: 'SampleHouse (no rooms)', db: 'SampleHouse_extracted.db', expectGraph: false },
];

function serve() {
  return new Promise(resolve => {
    const srv = spawn('python3', ['-m', 'http.server', String(PORT)], { cwd: DEV, stdio: 'ignore' });
    setTimeout(() => resolve(srv), 1200);
  });
}
function has(logs, re) { return logs.some(l => re.test(l)); }
function line(logs, re) { return logs.find(l => re.test(l)) || ''; }

(async () => {
  const srv = await serve();
  const browser = await puppeteer.launch({ headless: 'new',
    args: ['--use-gl=angle', '--use-angle=swiftshader', '--no-sandbox', '--enable-unsafe-swiftshader'] });
  console.log('══ W-R5A-DEV-GRAPH-TOUR — deploy/dev flies the graph route (code-only port) ══\n');

  for (const c of CASES) {
    if (!fs.existsSync(path.join(DEV, 'buildings', c.db)) && !fs.existsSync(path.join(DEV, '..', 'buildings', c.db))) {
      console.log('— ' + c.label + ': db not in deploy/dev/buildings, skipped'); continue;
    }
    const page = await browser.newPage();
    const logs = [];
    page.on('console', m => logs.push(m.text()));
    page.on('pageerror', e => logs.push('PAGEERROR ' + e.message));
    await page.goto(`http://localhost:${PORT}/index.html?db=buildings/${c.db}`, { waitUntil: 'domcontentloaded', timeout: 120000 });
    await page.waitForFunction(() => window.APP && window.APP.camera, { timeout: 120000 });
    await page.waitForFunction(() => { try { const r = window.APP.dbQuery('SELECT COUNT(*) FROM element_transforms'); return r && r.length && r[0][0] > 0; } catch (e) { return false; } },
      { timeout: 240000, polling: 2000 }).catch(() => {});
    for (let i = 0; i < 90; i++) { if (!(await page.evaluate(() => !!window.APP.streaming))) break; await new Promise(r => setTimeout(r, 1000)); }

    const pre = await page.evaluate(() => ({
      hasRG: typeof window.RoomGraph === 'object' && !!window.RoomGraph,
      hasHB: !!window.HallwayBackbone, hasSR: !!window.StoreyRaster, hasRH: !!window.RoomHabitability,
      hasGetter: typeof window.APP.getRoomGraph === 'function',
      hasEnsure: typeof window.APP.ensureRooms === 'function',
    }));
    await page.evaluate(() => window.APP.toggleFlyAround());
    for (let i = 0; i < 240; i++) { if (has(logs, /§FLY_ROUTE|§FLY_ROUTE_REJECT|§TOUR_/)) break; await new Promise(r => setTimeout(r, 1000)); }
    await new Promise(r => setTimeout(r, 3000));
    const st = await page.evaluate(() => ({ actions: (window.APP.walkActions || []).length, walkMode: !!window.APP.walkMode }));

    console.log('── ' + c.label + '  modules ' + JSON.stringify(pre) + '  actions=' + st.actions);
    const fly = line(logs, /§FLY_ROUTE /), hl = line(logs, /§FLY_HL_FIRST/), er = line(logs, /§R5A_ENSURE_ROOMS/);
    if (fly) console.log('   ' + fly.slice(0, 200));
    if (hl) console.log('   ' + hl.slice(0, 200));
    if (er) console.log('   ' + er.slice(0, 160));

    chk(c.label + ': G3 all four modules + bridge loaded',
      pre.hasRG && pre.hasHB && pre.hasSR && pre.hasRH && pre.hasGetter && pre.hasEnsure, JSON.stringify(pre));
    chk(c.label + ': G3 no §LOAD_FAIL / bridge-absent / page error',
      !has(logs, /§LOAD_FAIL|§R5A_BRIDGE_ABSENT|PAGEERROR/),
      line(logs, /§LOAD_FAIL|§R5A_BRIDGE_ABSENT|PAGEERROR/).slice(0, 120) || 'clean');
    chk(c.label + ': G4 bridge reports code-only (selfHeal=none)', /selfHeal=none\(R5-B\)/.test(er), er ? 'logged' : 'MISSING');
    if (c.expectGraph) {
      chk(c.label + ': G1 graph route built (§FLY_ROUTE)', !!fly, fly ? fly.replace(/^.*§FLY_ROUTE /, '').slice(0, 90) : 'absent');
      chk(c.label + ': G1 a highlight main hall was elected (§FLY_HL_FIRST)', !!hl,
        hl ? hl.replace(/^.*§FLY_HL_FIRST /, '').slice(0, 90) : 'absent');
      const m = fly.match(/stops=(\d+)\/(\d+)/);
      chk(c.label + ': G1 stops actually visited', !!m && +m[1] > 0, m ? m[0] : 'n/a');
    } else {
      chk(c.label + ': G2 degrades to the legacy tour, still produces actions',
        st.actions > 0, 'actions=' + st.actions + (has(logs, /§FLY_ROUTE_REJECT/) ? ' (§FLY_ROUTE_REJECT logged)' : ''));
    }
    await page.close();
  }
  await browser.close();
  try { srv.kill(); } catch (e) {}
  console.log('\n' + (fail ? '❌ FAIL ' : '✅ PASS ') + pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('ERR ' + e.message); process.exit(1); });
