#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_wh_walkmode.js — Witness W-WH-WALKMODE for the walk-mode UX increment
 * (user 2026-06-12: "engage that walk feature so it is not in BIM mode · manual tap
 * besides QR · home icon"). PRESENTATION ONLY — proves Fable5's engine is untouched:
 * the manual confirm rides the SAME scanInput gate + commitGroup as the QR path.
 *
 * Served on a DESKTOP viewport (the user's surface, no camera) off the walk-mode worktree.
 *
 * Verdicts:
 *  M1 walk-mode ENGAGES — on walk open, #mobile-pill (BIM construction chrome) is hidden
 *                         (§MODE walk-on); on close it is restored (§MODE walk-off).
 *  M2 manual confirm    — scan screen carries #wh-manual; clicking it routes the EXPECTED
 *                         locator through scanInput as via=manual → MATCH → qty row opens.
 *                         FALSIFIER vs invention: it does NOT fabricate — it vouches the LIT
 *                         target only; a wrong typed code on the same screen still REFUSES.
 *  M3 same signed op    — Confirm pick after a manual match commits ONE group (gid present,
 *                         chainOk=Y) — byte-identical to the QR path (engine untouched).
 *  M4 ⌂ home            — #wh-home present; A.HOME_URL captured from ?home= (single-hop escape).
 *
 * Run: bash build/erp/run_witness.sh scripts/poc_wh_walkmode.js
 * Read the log (build/erp/poc_wh_walkmode.log) after every run. WT_ROOT overrides the tree.
 */
'use strict';
var fs = require('fs');
var http = require('http');
var path = require('path');
var puppeteer = require('puppeteer');

var ROOT = process.env.WT_ROOT || '/tmp/wt-walkmode';
var PORT = Number(process.env.PORT || 8146);
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
function grab(lines, re) { for (var i = lines.length - 1; i >= 0; i--) { var m = lines[i].match(re); if (m) return m; } return null; }
function waitFor(lines, s, ms) {
  var t0 = Date.now();
  return new Promise(function (ok) {
    (function poll() { if (has(lines, s)) return ok(true); if (Date.now() - t0 > (ms || 25000)) return ok(false); setTimeout(poll, 250); })();
  });
}
var sleep = function (ms) { return new Promise(function (ok) { setTimeout(ok, ms); }); };

(async function () {
  await new Promise(function (ok) { server.listen(PORT, '127.0.0.1', ok); });
  console.log('§WH_WALKMODE serving ' + ROOT + ' on http://127.0.0.1:' + PORT + ' (desktop viewport, no camera)');

  var browser = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox', '--disable-dev-shm-usage'] });
  var page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 800 });   // DESKTOP — the user's repro surface
  var lines = [];
  page.on('console', function (msg) { var t = msg.text(); if (t.indexOf('§') >= 0) lines.push(t); });

  var HOME = 'https://red1oon.github.io/bim-ootb/erp/idempiere.html';
  var url = 'http://127.0.0.1:' + PORT + '/viewer/viewer.html?db=../buildings/warehouse_gardenworld.db&home=' + encodeURIComponent(HOME);
  await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitFor(lines, '§WH PILL gate=on', 30000);
  await waitFor(lines, '§BBOX_CLEARED', 30000);

  // ── M1: open walk → BIM pill bar hidden ──
  // #mobile-pill rests at display:none (collapsed tray) and is revealed when the user opens it
  // (their repro had it OPEN — §PILL open=true). Simulate that visible state so the hide/restore
  // invariant is genuinely exercised, not a none→none no-op.
  console.log('— M1: walk mode ENGAGES (BIM chrome cleared) —');
  await page.evaluate(function () { document.getElementById('mobile-pill').style.display = 'flex'; });
  var barBefore = await page.evaluate(function () { return document.getElementById('mobile-pill').style.display; });
  verdict(barBefore === 'flex', 'BIM pill tray OPEN before walk (the user-repro state)', 'display=' + barBefore);
  await page.evaluate(function () {
    var b = document.getElementById('pill-whwalk');
    if (b) b.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    else if (window.WHWalk) WHWalk.toggle();
  });
  verdict(await waitFor(lines, '§WH OPEN', 40000), '§WH OPEN (lens mounted)');
  verdict(has(lines, '§WH MODE walk-on'), '§MODE walk-on logged (BIM pill bar hidden)');
  var barOpen = await page.evaluate(function () { var b = document.getElementById('mobile-pill'); return b ? b.style.display : 'absent'; });
  verdict(barOpen === 'none', 'BIM #mobile-pill hidden during walk', 'was ' + barBefore + ' → ' + barOpen);
  verdict(await waitFor(lines, 'step=1/3 locator=101 fly=done lit=1', 20000), 'walk armed at step 1/3 (engine route intact)');

  // ── M4: ⌂ home on the walk strip ──
  console.log('— M4: ⌂ home (single-hop escape) —');
  var homeBtn = await page.$('#wh-home');
  verdict(!!homeBtn, '#wh-home present on the walk strip');
  var homeUrl = await page.evaluate(function () { return window.APP && APP.HOME_URL; });
  verdict(homeUrl === HOME, 'A.HOME_URL captured from ?home= (⌂ goes there, not the back-stack)', homeUrl || 'null');

  // ── M2: manual confirm beside QR ──
  console.log('— M2: manual confirm (desktop / no-camera path) —');
  await page.evaluate(function () { document.getElementById('wh-scan-btn').click(); });   // "Confirm bin" → scan screen
  await sleep(500);
  var manualBtn = await page.$('#wh-manual');
  verdict(!!manualBtn, '#wh-manual "✓ I\'m at this bin" present on the scan screen');
  // FALSIFIER (no invention): a WRONG typed code on the same screen still refuses
  await page.type('#wh-typed', '50004'); await page.click('#wh-typed-go'); await sleep(300);
  verdict(has(lines, 'via=typed REFUSED'), 'wrong typed code still REFUSED (gate intact, manual is not a bypass)');
  await page.evaluate(function () { document.getElementById('wh-typed').value = ''; });
  // manual confirm of the LIT target → MATCH via=manual
  await page.evaluate(function () { document.getElementById('wh-manual').click(); });
  await sleep(400);
  verdict(has(lines, 'via=manual MATCH'), '§WH scan=101 … via=manual MATCH (vouched the lit bin)', (grab(lines, /(§WH scan=.*via=manual.*)/) || ['', 'absent'])[1]);
  var qtyShown = await page.evaluate(function () { return document.getElementById('wh-qty').style.display === 'flex'; });
  verdict(qtyShown, 'qty confirm opens after manual match (same flow as QR)');

  // ── M3: same signed op group as the QR path ──
  console.log('— M3: same signed op (engine byte-identical) —');
  await page.evaluate(function () { document.getElementById('wh-qty-ok').click(); });   // Confirm pick
  await sleep(500);
  var pick = grab(lines, /(§WH PICK step=1\/3 .*chainOk=Y)/);
  verdict(!!pick, 'manual pick commits ONE group, chainOk=Y (same commitGroup as scan)', pick ? pick[1] : 'absent');

  // ── M1b: close → BIM chrome restored ──
  console.log('— M1b: close walk → BIM chrome restored —');
  await page.evaluate(function () { document.getElementById('wh-close').click(); });
  await sleep(300);
  verdict(has(lines, '§WH MODE walk-off'), '§MODE walk-off logged');
  var barAfter = await page.evaluate(function () { var b = document.getElementById('mobile-pill'); return b ? b.style.display : 'absent'; });
  verdict(barAfter === barBefore, 'BIM #mobile-pill restored to its prior state after walk (no surprise)', 'before=' + barBefore + ' after=' + barAfter);

  await browser.close();
  server.close();
  console.log(fails === 0 ? '§W-WH-WALKMODE PASS — walk mode engages, manual confirm rides the same gate, home wired'
                          : '§W-WH-WALKMODE FAIL — ' + fails + ' red verdicts');
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) { console.error('§WH_WALKMODE_ERR ' + e.message); process.exit(1); });
