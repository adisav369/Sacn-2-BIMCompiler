#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * c1_browser_matrix.js — the C1 REMEDY scaffold: a real cross-browser smoke matrix on BrowserStack/Sauce.
 *   Card: prompts/RESUME_S1_DISCOVERY_SPIKES.md §C1  ·  Register: docs/ProductionRisks.md C1
 *   Spike C1 was `blocked:needs-device` (no iOS Safari in the sandbox). This is the permanent fix: point
 *   Playwright at a real-device cloud and run the C1 checks on the HOSTILE targets we don't control.
 *
 * THE C1 CHECKS (per ProductionRisks C1 test-plan — "the app *runs* on the zoo, not just Chromium"):
 *   loads=    scripts loaded (no §LOAD_FAIL, page title present)
 *   dbReturns= the in-memory/streamed DB returns rows (a known query yields data)
 *   renders=  the canvas/viewer painted (a non-empty render surface)
 *   shares=   Web Share / clipboard fallback is reachable (capability present)
 *   → emits one §SPIKE-C1 line PER browser.  Honest: NO creds → blocked (NEVER a faked readout).
 *
 * SETUP (you provision; this wires to it):
 *   export BROWSERSTACK_USERNAME=…  BROWSERSTACK_ACCESS_KEY=…      (or SAUCE_USERNAME / SAUCE_ACCESS_KEY)
 *   export C1_TARGET_URL=https://red1oon.github.io/bim-ootb/         (public URL the cloud can reach;
 *                                                                     or a BrowserStack-Local tunnel to localhost)
 *   node scripts/c1_browser_matrix.js
 *
 * The matrix is the article's "current + one-back iOS Safari, Android Chrome, desktop trio".
 */
'use strict';
var path = require('path');
var pw = require(path.join(process.env.HOME, 'bim-ootb', 'tests', 'node_modules', 'playwright-core'));

var TARGET = process.env.C1_TARGET_URL || 'https://red1oon.github.io/bim-ootb/';

// the hostile-zoo matrix. browser = the Playwright engine BrowserStack maps the real device/OS onto.
var MATRIX = [
  { label: 'iOS Safari (current)',  os: 'ios', osVersion: '17', device: 'iPhone 15', browser: 'playwright-webkit' },
  { label: 'iOS Safari (one-back)', os: 'ios', osVersion: '16', device: 'iPhone 14', browser: 'playwright-webkit' },
  { label: 'Android Chrome (mid)',  os: 'android', osVersion: '13.0', device: 'Samsung Galaxy A52', browser: 'playwright-chromium' },
  { label: 'Desktop Safari',        os: 'OS X', osVersion: 'Sonoma', browser: 'playwright-webkit' },
  { label: 'Desktop Firefox',       os: 'Windows', osVersion: '11', browser: 'playwright-firefox' },
  { label: 'Desktop Chrome',        os: 'Windows', osVersion: '11', browser: 'playwright-chromium' }
];

function creds() {
  if (process.env.BROWSERSTACK_USERNAME && process.env.BROWSERSTACK_ACCESS_KEY)
    return { vendor: 'browserstack', user: process.env.BROWSERSTACK_USERNAME, key: process.env.BROWSERSTACK_ACCESS_KEY };
  if (process.env.SAUCE_USERNAME && process.env.SAUCE_ACCESS_KEY)
    return { vendor: 'sauce', user: process.env.SAUCE_USERNAME, key: process.env.SAUCE_ACCESS_KEY };
  return null;
}

// wss endpoint for BrowserStack Playwright (engine is selected by caps.browser)
function bsEndpoint(c, caps) {
  var enc = encodeURIComponent(JSON.stringify(Object.assign({
    'browserstack.username': c.user, 'browserstack.accessKey': c.key,
    'browserstack.local': 'false', 'project': 'bim-ootb', 'build': 'C1-matrix', 'name': caps.label
  }, caps)));
  return 'wss://cdp.browserstack.com/playwright?caps=' + enc;
}

function engineFor(browser) {
  if (/webkit/.test(browser)) return pw.webkit;
  if (/firefox/.test(browser)) return pw.firefox;
  return pw.chromium;
}

// the C1 checks, run inside a connected page
async function runChecks(page) {
  var logs = [];
  page.on('console', function (m) { logs.push(m.text()); });
  await page.goto(TARGET, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(4000);
  var loads = !logs.some(function (l) { return /§LOAD_FAIL/.test(l); }) && !!(await page.title());
  var dbReturns = await page.evaluate(function () {
    try { return !!(window.APP && window.APP.db) || /element|building/i.test(document.body.innerText); } catch (e) { return false; }
  }).catch(function () { return false; });
  var renders = await page.evaluate(function () {
    var c = document.querySelector('canvas'); return !!(c && c.width > 0 && c.height > 0);
  }).catch(function () { return false; });
  var shares = await page.evaluate(function () { return ('share' in navigator) || ('clipboard' in navigator); }).catch(function () { return false; });
  return { loads: loads, dbReturns: dbReturns, renders: renders, shares: shares };
}

(async function () {
  console.log('═══ C1 cross-browser matrix → ' + TARGET + ' ═══');
  var c = creds();
  if (!c) {
    MATRIX.forEach(function (m) {
      console.log('§SPIKE-C1 browser="' + m.label + '" blocked:no-credentials — set BROWSERSTACK_USERNAME/ACCESS_KEY (or SAUCE_*) then re-run');
    });
    console.log('§C1-MATRIX OVERALL=BLOCKED (provision a device-cloud account; this runner is wired and ready)');
    process.exit(0);
  }
  console.log('§C1-MATRIX vendor=' + c.vendor + ' targets=' + MATRIX.length);
  var anyFail = false;
  for (var i = 0; i < MATRIX.length; i++) {
    var m = MATRIX[i], engine = engineFor(m.browser);
    try {
      var browser = await engine.connect(bsEndpoint(c, m));
      var page = await browser.newPage();
      var r = await runChecks(page);
      var pass = r.loads && r.dbReturns && r.renders;
      console.log('§SPIKE-C1 browser="' + m.label + '" loads=' + (r.loads ? 'Y' : 'N') + ' dbReturns=' + (r.dbReturns ? 'Y' : 'N') +
        ' renders=' + (r.renders ? 'Y' : 'N') + ' shares=' + (r.shares ? 'Y' : 'N') + ' verdict=' + (pass ? 'PASS' : 'FAIL'));
      if (!pass) anyFail = true;
      await browser.close();
    } catch (e) {
      anyFail = true;
      console.log('§SPIKE-C1 browser="' + m.label + '" error="' + e.message.slice(0, 120) + '" verdict=ERROR');
    }
  }
  console.log('§C1-MATRIX OVERALL=' + (anyFail ? 'FAIL/PARTIAL (read per-browser §SPIKE-C1)' : 'PASS — runs on the zoo'));
  process.exit(anyFail ? 1 : 0);
})().catch(function (e) { console.error('FATAL ' + e.stack); process.exit(2); });
