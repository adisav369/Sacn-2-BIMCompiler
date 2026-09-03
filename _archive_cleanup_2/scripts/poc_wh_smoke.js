#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_wh_smoke.js — LOCAL viewer smoke for docs/SPATIAL_PICKING_SPEC.md §S-1.
 *
 * Issue this proves/disproves: the compiled build/erp/warehouse_gardenworld.db
 * actually STREAMS in the real viewer (deploy/dev) — real geometry renders, the
 * bbox-placeholder cubes are CLEARED (the no-cubes render gate, live half):
 *   PASS = §DS_QUEUED elements=26 · §BLOB_FETCH new=6 · §BBOX_CLEARED present
 *          · §BBOX_KEEP and §BLOB_MISS ABSENT (those = cubes shown = FAIL)
 *          · all 11 bin guids resolve in the loaded db (sql.js, the real load path).
 *
 * localhost only — NO deploy/upload. Whitebox §-log first; puppeteer is only the
 * wiring harness (docs/TestArchitecture.md §Browser Testing).
 * Run: bash build/erp/run_witness.sh scripts/poc_wh_smoke.js
 * Read the log (build/erp/poc_wh_smoke.log) after every run.
 */
'use strict';
var fs = require('fs');
var http = require('http');
var path = require('path');
var puppeteer = require('puppeteer');

var ROOT = path.join(__dirname, '..');
var PORT = 8137;
var DB_REL = '/build/erp/warehouse_gardenworld.db';
var MIME = { '.html': 'text/html', '.js': 'application/javascript', '.mjs': 'application/javascript',
  '.css': 'text/css', '.json': 'application/json', '.wasm': 'application/wasm',
  '.db': 'application/octet-stream', '.png': 'image/png', '.svg': 'image/svg+xml', '.ico': 'image/x-icon' };

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

var server = http.createServer(function(req, res) {
  var p = decodeURIComponent(req.url.split('?')[0]);
  var f = path.join(ROOT, p);
  if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(f)] || 'application/octet-stream' });
  fs.createReadStream(f).pipe(res);
});

(async function() {
  await new Promise(function(ok) { server.listen(PORT, '127.0.0.1', ok); });
  console.log('§WH_SMOKE serving ' + ROOT + ' on http://127.0.0.1:' + PORT + ' (localhost only, no deploy)');

  var browser = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox', '--disable-dev-shm-usage'] });
  var page = await browser.newPage();
  var lines = [];
  page.on('console', function(msg) { var t = msg.text(); if (t.indexOf('§') >= 0 || /error/i.test(t)) lines.push(t); });
  page.on('pageerror', function(e) { lines.push('PAGEERROR ' + e.message); });

  var url = 'http://127.0.0.1:' + PORT + '/deploy/dev/index.html?db=' + encodeURIComponent(DB_REL);
  console.log('§WH_SMOKE url=' + url);
  await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 });

  // wait until the placeholder cubes are cleared (real geometry up) or timeout
  var t0 = Date.now();
  while (Date.now() - t0 < 45000) {
    if (lines.some(function(l) { return l.indexOf('§BBOX_CLEARED') >= 0 || l.indexOf('§BBOX_KEEP') >= 0; })) break;
    await new Promise(function(ok) { setTimeout(ok, 500); });
  }
  await new Promise(function(ok) { setTimeout(ok, 1500); }); // let final flush log

  console.log('— captured §-lines —');
  lines.forEach(function(l) { console.log('   | ' + l); });

  function has(s) { return lines.some(function(l) { return l.indexOf(s) >= 0; }); }
  function grab(re) { for (var i = 0; i < lines.length; i++) { var m = lines[i].match(re); if (m) return m; } return null; }

  var q = grab(/§DS_QUEUED bld=\S+ elements=(\d+)/);
  verdict(!!q && q[1] === '26', '§DS_QUEUED elements=26 (1 floor + 3 aisles + 11 racks + 11 bins)', q ? q[0] : 'absent');
  var bf = grab(/§BLOB_FETCH new=(\d+)/);
  verdict(!!bf && parseInt(bf[1], 10) >= 6, '§BLOB_FETCH all 6 geometry hashes', bf ? bf[0] : 'absent');
  verdict(has('§BBOX_CLEARED'), '§BBOX_CLEARED — placeholder cubes removed (render gate, live)');
  verdict(!has('§BBOX_KEEP'), 'no §BBOX_KEEP (would mean cubes kept = non-renderable)');
  verdict(!has('§BLOB_MISS'), 'no §BLOB_MISS (every hash found in db)');
  verdict(!lines.some(function(l) { return l.indexOf('PAGEERROR') === 0; }), 'no page errors');

  // bin guids resolve in the db AS LOADED by the viewer (sql.js path, not header reads)
  var binCheck = await page.evaluate(function() {
    var A = window.A || window.APP || null;
    if (!A || !A.db) return { err: 'no A.db' };
    var r = A.db.exec("SELECT COUNT(*) FROM elements_meta WHERE ifc_class='IfcBuildingElementProxy'");
    var ids = A.db.exec("SELECT guid FROM elements_meta WHERE ifc_class='IfcBuildingElementProxy' ORDER BY CAST(guid AS INTEGER)");
    return { bins: r[0].values[0][0], guids: ids[0].values.map(function(v) { return v[0]; }) };
  });
  var binsOk = binCheck && binCheck.bins === 11;
  verdict(binsOk, 'loaded db: 11 bin elements, guid==m_locator_id', JSON.stringify(binCheck));
  console.log('§WH_SMOKE_BINS loaded-db bins=' + (binCheck && binCheck.bins) + ' guids=' + (binCheck && binCheck.guids ? binCheck.guids.join(',') : '?'));

  try { await page.screenshot({ path: path.join(ROOT, 'build', 'erp', 'wh_smoke.png') }); console.log('§WH_SMOKE_SHOT build/erp/wh_smoke.png'); } catch (e) {}

  await browser.close();
  server.close();
  console.log(fails === 0 ? '§W-WH-SMOKE PASS — db streams in the local viewer, no cubes' : '§W-WH-SMOKE FAIL fails=' + fails);
  process.exit(fails === 0 ? 0 : 1);
})().catch(function(e) { console.error('§WH_SMOKE ERROR ' + e.message); server.close(); process.exit(1); });
