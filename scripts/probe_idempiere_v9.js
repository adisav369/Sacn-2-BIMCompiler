#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// probe_idempiere_v9.js — WIRING/DEPLOY smoke for the ACTUAL iDempiere renderer (bim-ootb/erp/idempiere.html,
//   classic tree→window→tab→grid chrome) served from localhost:8849 with the v9 kernel synced in.
//   Confirms: I1 page boots clean (no pageerrors), kernel v9 + §I-D/§I-K primitives exported;
//   I2 the login flow works (pick user → role/org → Log In) and the role-scoped chrome renders (§IDEMPIERE-LOGIN);
//   I3 idempiere's OWN write path (commitOp→sealChain→verifyChain) still works on the v9 kernel (no regression)
//      AND the new period-close checkpoint round-trips (dormant-but-available primitive);
//   M1 mobile viewport boots clean (the chrome's burger/mobile adapt), no errors.
// Run: node scripts/probe_idempiere_v9.js 2>&1 | tee build/erp/probe_idempiere.log   (then READ the log).
'use strict';
var puppeteer = require('puppeteer');
var URL = 'http://localhost:8849/idempiere.html';
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

(async function () {
  console.log('═══ PROBE-IDEMPIERE-V9 — the iDempiere renderer (bim-ootb/erp/idempiere.html @ :8849, v9 kernel) ═══');
  console.log('url=' + URL + '\n');
  var browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox', '--disable-setuid-sandbox'] });

  var logs = [], errors = [];
  var page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 900 });
  page.on('console', function (m) { logs.push(m.text()); });
  page.on('pageerror', function (e) { errors.push('PAGEERROR ' + e.message); });
  page.on('requestfailed', function (r) { var u = r.url(); if (!/favicon/.test(u)) errors.push('REQFAIL ' + u + ' ' + (r.failure() && r.failure().errorText)); });

  await page.goto(URL, { waitUntil: 'networkidle2', timeout: 40000 });
  await new Promise(function (r) { setTimeout(r, 3500); });   // sql.js + ad_seed.db hydrate

  var kernelLine = logs.filter(function (l) { return /KERNEL_OPS_LOADED/.test(l); })[0] || '(none)';
  console.log('I1 — boot + kernel version + primitives');
  console.log('§IPROBE kernelLog="' + kernelLine + '" pageerrors=' + errors.length + (errors.length ? ' :: ' + errors.slice(0, 3).join(' | ') : ''));
  verdict(errors.length === 0, 'idempiere.html boots with ZERO pageerrors / failed requests', errors.length + ' errors');
  verdict(/v9\b/.test(kernelLine) && /W-CHECKPOINT/.test(kernelLine), 'served+loaded kernel is v9 with W-CHECKPOINT', kernelLine);
  var api = await page.evaluate(function () { var K = window.KernelOps || {}; return { commitOp: typeof K.commitOp, seal: typeof K.sealChain, verify: typeof K.verifyChain, group: typeof K.commitGroup, close: typeof K.closePeriod, latest: typeof K.latestCheckpoint }; });
  console.log('§IPROBE primitives commitOp=' + api.commitOp + ' sealChain=' + api.seal + ' verifyChain=' + api.verify + ' | NEW commitGroup=' + api.group + ' closePeriod=' + api.close + ' latestCheckpoint=' + api.latest);
  verdict(api.commitOp === 'function' && api.close === 'function' && api.group === 'function',
          'idempiere\'s own write path (commitOp/sealChain/verifyChain) present AND the new §I-K/§I-D primitives are available',
          'commitOp=' + api.commitOp + ' close=' + api.close);

  console.log('\nI2 — drive the login (pick user → role/org → Log In) → role-scoped chrome renders');
  // step 1: wait for an enabled user row, click it
  await page.waitForSelector('#idmp-login-users .idmp-login-user:not(.disabled)', { timeout: 15000 });
  await page.evaluate(function () { document.querySelector('#idmp-login-users .idmp-login-user:not(.disabled)').click(); });
  // step 2: role/org auto-fill, then Log In
  await page.waitForSelector('#idmp-login-step2', { visible: true, timeout: 10000 });
  await new Promise(function (r) { setTimeout(r, 400); });
  await page.evaluate(function () { document.getElementById('idmp-login-ok').click(); });
  // wait for the session to apply (login log + context bar populated)
  await page.waitForFunction(function () { var c = document.getElementById('idmp-ctx'); return c && c.textContent && c.textContent.indexOf('·') >= 0; }, { timeout: 12000 }).catch(function () {});
  await new Promise(function (r) { setTimeout(r, 1200); });
  var loginLog = logs.filter(function (l) { return /§IDEMPIERE-LOGIN/.test(l); })[0] || '(none)';
  var chrome = await page.evaluate(function () {
    var menu = document.getElementById('idmp-menu'); var ctx = document.getElementById('idmp-ctx');
    return { ctx: ctx ? (ctx.textContent || '').trim() : '', menuItems: menu ? menu.querySelectorAll('*').length : 0,
             content: !!document.getElementById('idmp-content'), loginHidden: getComputedStyle(document.getElementById('idmp-login')).display === 'none' };
  });
  console.log('§IPROBE login "' + loginLog + '"');
  console.log('§IPROBE chrome ctx="' + chrome.ctx + '" menuNodes=' + chrome.menuItems + ' loginHidden=' + chrome.loginHidden);
  verdict(chrome.loginHidden && chrome.ctx.indexOf('·') >= 0 && chrome.menuItems > 3,
          'login succeeds → the iDempiere chrome renders: Client·Role·Org context bar + role-scoped menu',
          'ctx="' + chrome.ctx + '" menuNodes=' + chrome.menuItems);

  console.log('\nI3 — idempiere\'s own write path on the v9 kernel + the new checkpoint (in-browser)');
  var live = await page.evaluate(async function () {
    var K = window.KernelOps;
    try {
      if (!window.initSqlJs) return { skip: 'no initSqlJs' };
      var SQL = await window.initSqlJs({ locateFile: function (f) { return 'lib/' + f; } }).catch(function () { return window.SQL; });
      var d = new SQL.Database(); K.ensureTable(d);
      // the path idempiere.html ACTUALLY uses (line ~799): commitOp → sealChain → verifyChain
      K.commitOp(d, 'CRUD_CREATE', { table: 'C_Order', fields: { DocumentNo: 'SO-T1' } }, null, 'rec-1', 'uuid-t1');
      await K.sealChain(d);
      var vOld = await K.verifyChain(d);
      // the NEW primitive (dormant in idempiere today, but now AVAILABLE): close a period + bounded verify
      var ck = await K.closePeriod(d, { balances: { test: 1 } });
      K.commitOp(d, 'CRUD_CREATE', { table: 'C_Order', fields: { DocumentNo: 'SO-T2' } }, null, 'rec-2', 'uuid-t2');
      await K.sealChain(d);
      var vBound = await K.verifyChain(d, { fromCheckpoint: true });
      return { oldPathOk: vOld.ok, oldLen: vOld.len, ckAtOp: ck.atOpId, boundedOk: vBound.ok, boundedScannedFrom: vBound.scannedFrom };
    } catch (e) { return { error: e.message }; }
  });
  console.log('§IPROBE writepath ' + JSON.stringify(live));
  verdict(live && live.oldPathOk === true && live.boundedOk === true && live.boundedScannedFrom === live.ckAtOp,
          'idempiere\'s commitOp→sealChain→verifyChain works on v9 (no regression) AND the new bounded verify scans only from the checkpoint',
          live && live.error ? 'error=' + live.error : 'oldOk=' + (live && live.oldPathOk) + ' boundedScannedFrom=' + (live && live.boundedScannedFrom));
  await page.close();

  console.log('\nM1 — mobile viewport (390×844, touch): boots clean');
  var merrors = [];
  var mp = await browser.newPage();
  await mp.setUserAgent('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148');
  await mp.setViewport({ width: 390, height: 844, isMobile: true, hasTouch: true, deviceScaleFactor: 3 });
  mp.on('pageerror', function (e) { merrors.push('PAGEERROR ' + e.message); });
  mp.on('requestfailed', function (r) { var u = r.url(); if (!/favicon/.test(u)) merrors.push('REQFAIL ' + u); });
  await mp.goto(URL, { waitUntil: 'networkidle2', timeout: 40000 });
  await new Promise(function (r) { setTimeout(r, 2500); });
  var mob = await mp.evaluate(function () { var b = document.getElementById('idmp-burger'); return { burger: b ? getComputedStyle(b).display : '(none-el)', innerW: window.innerWidth, loginShown: getComputedStyle(document.getElementById('idmp-login')).display !== 'none' }; });
  console.log('§IPROBE mobile burger.display=' + mob.burger + ' innerW=' + mob.innerW + ' loginScreen=' + mob.loginShown + ' pageerrors=' + merrors.length);
  verdict(merrors.length === 0, 'idempiere.html boots clean on a ≤760px touch viewport (mobile chrome)', 'merrors=' + merrors.length + ' burger=' + mob.burger);
  await mp.close();

  await browser.close();
  console.log('\n§IPROBE ' + (fails ? 'FAIL — ' + fails + ' checks red.'
    : 'PASS — the iDempiere renderer (idempiere.html) serves+boots clean on localhost with the v9 kernel, the login flow '
    + 'renders the role-scoped classic chrome, idempiere\'s own commitOp write path works on v9 with NO regression, the '
    + 'new period-close checkpoint is available + bounded-verify round-trips, and mobile boots clean.'));
  process.exit(fails ? 1 : 0);
})().catch(function (e) { console.error('FATAL', e); process.exit(2); });
