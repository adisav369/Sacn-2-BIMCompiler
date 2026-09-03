#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_wh_cache.js — Witness W-WH-CACHE for the P0 cache-clobber bug
 * (prompts/FRONTEND_LANE_MASTER.md §OUTSTANDING P0 BUG, user live report 2026-06-12
 * "cannot access building when refresh").
 *
 * ISSUE THIS TEST EXPOSES: kernel_ops.js _persistToIdb(db) keys the IndexedDB write by
 * APP.DB_URL but exports WHATEVER db handle was committed on — wh_walk commits on its own
 * in-memory op db (W.opDb), so opening the Walk persisted a 16KB op-only db OVER the cached
 * building (user log: §KRN_PERSIST url=…warehouse_gardenworld.db size=16KB, was 80KB) →
 * reload §CACHE_HIT served it → no geometry tables → building never loads.
 *
 * Verdicts:
 *  C1 legit persist   — BUILDING_OPEN (committed on APP.db) still persists (§KRN_PERSIST,
 *                       size ≥ 50KB). FALSIFIER for the fix: the guard must NOT kill the
 *                       S243 survive-refresh path.
 *  C2 foreign-db skip — walk-open drafts on W.opDb → §KRN_PERSIST_SKIP logged, and NO
 *                       §KRN_PERSIST under the building url with size < 50KB (the clobber).
 *  C3 reload survives — page.reload() → §CACHE_HIT + §CENTRES_RESULT rows=1 + §WH PILL
 *                       gate=on. THE BUG REPRO: pre-fix this run goes RED here.
 *
 * Run: bash build/erp/run_witness.sh scripts/poc_wh_cache.js
 * Read the log (build/erp/poc_wh_cache.log) after every run.
 * WT_ROOT overrides the served worktree (default /tmp/wt-whcache).
 */
'use strict';
var fs = require('fs');
var http = require('http');
var path = require('path');
var puppeteer = require('puppeteer');

var ROOT = process.env.WT_ROOT || '/tmp/wt-whcache';
var PORT = Number(process.env.PORT || 8143);
var MIME = { '.html': 'text/html', '.js': 'application/javascript', '.mjs': 'application/javascript',
  '.css': 'text/css', '.json': 'application/json', '.wasm': 'application/wasm',
  '.db': 'application/octet-stream', '.png': 'image/png', '.svg': 'image/svg+xml', '.ico': 'image/x-icon' };

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

var server = http.createServer(function (req, res) {
  var p = decodeURIComponent(req.url.split('?')[0]);
  var f = path.join(ROOT, p);
  if (!f.startsWith(ROOT)) { res.writeHead(403); res.end(); return; }
  if (!fs.existsSync(f) || fs.statSync(f).isDirectory()) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(f)] || 'application/octet-stream' });
  fs.createReadStream(f).pipe(res);
});

function has(lines, s) { return lines.some(function (l) { return l.indexOf(s) >= 0; }); }
function waitFor(lines, s, ms) {
  var t0 = Date.now();
  return new Promise(function (ok) {
    (function poll() {
      if (has(lines, s)) return ok(true);
      if (Date.now() - t0 > (ms || 25000)) return ok(false);
      setTimeout(poll, 250);
    })();
  });
}
// every §KRN_PERSIST line carries size=<n>KB — the clobber writes < 50KB (op-only db)
function clobberLines(lines) {
  return lines.filter(function (l) {
    var m = l.match(/§KRN_PERSIST url=.*size=(\d+)KB/);
    return m && Number(m[1]) < 50;
  });
}

(async function () {
  await new Promise(function (ok) { server.listen(PORT, '127.0.0.1', ok); });
  console.log('§WH_CACHE serving ' + ROOT + ' on http://127.0.0.1:' + PORT + ' (localhost only)');

  var browser = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox', '--disable-dev-shm-usage'] });
  var page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 800 });   // DESKTOP — the user's repro surface
  var lines = [];
  page.on('console', function (msg) { var t = msg.text(); if (t.indexOf('§') >= 0) lines.push(t); });

  var url = 'http://127.0.0.1:' + PORT + '/viewer/viewer.html?db=../buildings/warehouse_gardenworld.db';
  await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 });

  // ── C1: building loads + the LEGIT persist (APP.db) still fires ──
  console.log('— C1: load + legit persist (the fix must not kill S243 survive-refresh) —');
  verdict(await waitFor(lines, '§CENTRES_RESULT rows=1'), 'building bootstrap (§CENTRES_RESULT rows=1)');
  verdict(await waitFor(lines, '§WH PILL gate=on'), '§WH PILL gate=on (warehouse model)');
  await waitFor(lines, '§BBOX_CLEARED', 30000);
  var legit = await waitFor(lines, '§KRN_PERSIST url=', 20000);   // BUILDING_OPEN op → debounced persist
  verdict(legit, 'legit §KRN_PERSIST fired for APP.db (BUILDING_OPEN survives refresh)');
  verdict(clobberLines(lines).length === 0, 'no sub-50KB persist before the walk (baseline clean)');

  // ── C2: walk-open commits on W.opDb — must SKIP, never clobber ──
  console.log('— C2: foreign-db commit (walk draft on W.opDb) —');
  await page.evaluate(function () {
    var b = document.getElementById('pill-whwalk');
    if (b) b.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    else if (window.WHWalk) WHWalk.toggle();
  });
  verdict(await waitFor(lines, '§WH OPEN', 40000), '§WH OPEN (draft committed on the walk op db)');
  await new Promise(function (ok) { setTimeout(ok, 4000); });     // outlive the persist debounce
  var skipped = has(lines, '§KRN_PERSIST_SKIP');
  var clob = clobberLines(lines);
  verdict(skipped, '§KRN_PERSIST_SKIP logged (guard took the foreign db)');
  verdict(clob.length === 0, 'NO sub-50KB clobber persist under the building url', clob[0] || 'none');

  // ── C3: THE USER'S REPRO — refresh must still load the building ──
  console.log('— C3: reload survives (pre-fix: 16KB cache served → dead) —');
  lines.length = 0;
  await page.reload({ waitUntil: 'domcontentloaded', timeout: 30000 });
  var hit = await waitFor(lines, '§CACHE_HIT', 25000);
  var centres = await waitFor(lines, '§CENTRES_RESULT rows=1', 25000);
  var gate2 = await waitFor(lines, '§WH PILL gate=on', 25000);
  verdict(hit, 'reload served from IDB cache (§CACHE_HIT)');
  verdict(centres, 'building loads after reload (§CENTRES_RESULT rows=1)');
  verdict(gate2, 'walk pill gate=on after reload (db intact, locator bins present)');

  await browser.close();
  server.close();
  console.log(fails === 0 ? '§W-WH-CACHE PASS — guard holds, refresh survives the walk'
                          : '§W-WH-CACHE FAIL — ' + fails + ' red verdicts (read the lines above)');
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) { console.error('§WH_CACHE_ERR ' + e.message); process.exit(1); });
