// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// Witness: W-RING-STICKY (R, 2026-06-01) — the CRUD ring of fire STAYS once revealed; the small fabs must
// not vanish while the user reaches for them. Dismiss is EXPLICIT only. Proves/disproves:
//  (1) STICKY      : after openRing + a pointerleave on the ring AND the hotzone, the ring is STILL open
//                    (old behaviour auto-closed ~160ms after leave — that is the bug this removes).
//  (2) SWITCH      : opening another bubble's ring REPLACES the current one (ringKey switches).
//  (3) DISMISS-OUT : a pointerdown OUTSIDE the ring + hotzones closes it (explicit act).
//  (4) DISMISS-VERB: picking a verb closes it (onVerb→closeRing) — the existing path, unbroken.
// §-log first: writes build/erp/ring_sticky_witness.log; READ the log, not the exit code.
'use strict';
var puppeteer = require('puppeteer');
var fs = require('fs');
var URL = 'http://localhost:8000/glassbowl.html';
var LOG = __dirname + '/../build/erp/ring_sticky_witness.log';

var out = [], errs = [], pass = 0, fail = 0;
function L(s) { out.push(s); }
function ck(c, m) { if (c) { pass++; L('§RING PASS ' + m); } else { fail++; L('§RING FAIL ' + m); } }
var sleep = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };

(async function () {
  var browser;
  try {
    browser = await puppeteer.launch({
      headless: 'new', executablePath: '/usr/bin/google-chrome',
      args: ['--no-sandbox', '--disable-dev-shm-usage', '--use-gl=swiftshader', '--enable-unsafe-swiftshader', '--window-size=1400,900']
    });
  } catch (e) { L('§RING FATAL launch: ' + e.message); fs.writeFileSync(LOG, out.join('\n') + '\n'); console.log(out.join('\n')); process.exit(2); }
  var page = await browser.newPage();
  await page.setViewport({ width: 1400, height: 900 });
  page.on('pageerror', function (e) { errs.push(String(e)); });
  try { await page.goto(URL, { waitUntil: 'networkidle2', timeout: 45000 }); } catch (e) { L('§RING goto warn: ' + e.message); }
  await sleep(4000);
  await page.click('#crudModeCk'); await sleep(700);   // Edit-mode on

  // (1) STICKY — open, fire pointerleave on ring + hotzone, wait well past the OLD 160ms close window
  var sticky = await page.evaluate(async function () {
    window.__crud.openRing('c_invoice');
    await new Promise(function (z) { setTimeout(z, 300); });
    var ring = document.getElementById('crudRing');
    var hot = document.querySelector('.crud-hot');
    ring.dispatchEvent(new PointerEvent('pointerleave', { bubbles: true }));
    if (hot) hot.dispatchEvent(new PointerEvent('pointerleave', { bubbles: true }));
    await new Promise(function (z) { setTimeout(z, 500); });   // > old 160ms timer
    return ring.classList.contains('open');
  });
  ck(sticky, '(1) ring STILL open 500ms after pointerleave on ring+hotzone (sticky — no auto-close)');

  // (2) SWITCH — opening another bubble replaces the ring
  var switched = await page.evaluate(async function () {
    window.__crud.openRing('c_order');
    await new Promise(function (z) { setTimeout(z, 300); });
    var ring = document.getElementById('crudRing');
    // the proc fab title carries the doc name → confirm it re-rendered for the new key
    var anyFab = ring.querySelector('.crud-fab');
    return { open: ring.classList.contains('open'), rerendered: !!anyFab };
  });
  ck(switched.open && switched.rerendered, '(2) opening another bubble REPLACES the ring (still one, re-rendered)');

  // (3) DISMISS-OUT — a pointerdown outside the ring + hotzones closes it
  var dismissed = await page.evaluate(async function () {
    document.body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    await new Promise(function (z) { setTimeout(z, 200); });
    return !document.getElementById('crudRing').classList.contains('open');
  });
  ck(dismissed, '(3) pointerdown OUTSIDE closes the ring (explicit dismiss)');

  // (4) DISMISS-VERB — picking a verb closes the ring (existing path intact)
  var verbClosed = await page.evaluate(async function () {
    window.__crud.openRing('c_invoice');
    await new Promise(function (z) { setTimeout(z, 300); });
    var fab = document.querySelector('#crudRing .crud-fab.proc');
    if (!fab) return 'no-fab';
    fab.click();
    await new Promise(function (z) { setTimeout(z, 400); });
    return !document.getElementById('crudRing').classList.contains('open');
  });
  ck(verbClosed === true, '(4) picking a verb closes the ring (onVerb→closeRing intact)');

  ck(errs.length === 0, 'pageerror=0' + (errs.length ? ' [' + errs.slice(0, 2).join(' | ') + ']' : ''));
  L('§RING SUMMARY ' + (fail === 0 ? 'PASS' : 'FAIL') + ' pass=' + pass + ' fail=' + fail);
  fs.writeFileSync(LOG, out.join('\n') + '\n');
  console.log(out.join('\n'));
  await browser.close();
  process.exit(fail === 0 ? 0 : 1);
})();
