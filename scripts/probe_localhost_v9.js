#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// probe_localhost_v9.js — WIRING/DEPLOY smoke test (NOT a value witness — see CLAUDE.md §Browser Testing:
//   §-log first, Playwright/probe second for wiring). Loads the served glassbowl ERP from localhost:8848
//   (site/ = coherent v9 publish) in a real Chromium and confirms:
//     D1 the ERP page loads with NO pageerrors / failed requests
//     D2 kernel v9 is the served+loaded kernel (§KERNEL_OPS_LOADED v9) + the NEW §I-D primitives are exported
//     D3 the iDempiere ERP UI actually rendered (SVG spine graph has cells; recent-records list present)
//     D4 a REAL in-browser period-close checkpoint round-trips (commitGroup → closePeriod → bounded verify)
//     M1 mobile viewport (≤760px): the mobile-only #diagBtn becomes visible (CSS @media), still no errors
// Run: node scripts/probe_localhost_v9.js 2>&1 | tee build/erp/probe_localhost.log   (then READ the log).
'use strict';
var puppeteer = require('puppeteer');
var URL = 'http://localhost:8848/glassbowl.html';
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

(async function () {
  console.log('═══ PROBE-LOCALHOST-V9 — served glassbowl ERP (site/ @ :8848) wiring smoke ═══');
  console.log('url=' + URL + '  (site/ = coherent build/erp v9 publish)\n');
  var browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox', '--disable-setuid-sandbox'] });

  // ── DESKTOP ──────────────────────────────────────────────────────────────────────────────────
  var logs = [], errors = [];
  var page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 900 });
  page.on('console', function (m) { logs.push(m.text()); });
  page.on('pageerror', function (e) { errors.push('PAGEERROR ' + e.message); });
  page.on('requestfailed', function (r) { var u = r.url(); if (!/favicon/.test(u)) errors.push('REQFAIL ' + u + ' ' + (r.failure() && r.failure().errorText)); });

  await page.goto(URL, { waitUntil: 'networkidle2', timeout: 30000 });
  await new Promise(function (r) { setTimeout(r, 3000); });   // let the lazy sql.js bundle + graph settle

  var kernelLine = logs.filter(function (l) { return /KERNEL_OPS_LOADED/.test(l); })[0] || '(none)';
  console.log('D1/D2 — page load + kernel version + §I-D primitives');
  console.log('§PROBE kernelLog="' + kernelLine + '" pageerrors=' + errors.length + (errors.length ? ' :: ' + errors.slice(0, 3).join(' | ') : ''));
  verdict(errors.length === 0, 'ERP page loads with ZERO pageerrors / failed requests', errors.length + ' errors');
  verdict(/v9\b/.test(kernelLine) && /W-CHECKPOINT/.test(kernelLine), 'served+loaded kernel is v9 with W-CHECKPOINT', kernelLine);

  var api = await page.evaluate(function () {
    var KO = window.KernelOps || {};
    return { close: typeof KO.closePeriod, latest: typeof KO.latestCheckpoint, group: typeof KO.commitGroup,
             verify: typeof KO.verifyChain, sealFrom: typeof KO.sealFrom };
  });
  console.log('§PROBE primitives closePeriod=' + api.close + ' latestCheckpoint=' + api.latest + ' commitGroup=' + api.group + ' verifyChain=' + api.verify);
  verdict(api.close === 'function' && api.latest === 'function' && api.group === 'function',
          'the §I-D primitives (closePeriod/latestCheckpoint) + §I-K commitGroup are exported in the browser',
          'close=' + api.close + ' latest=' + api.latest + ' group=' + api.group);

  console.log('\nD3 — the iDempiere ERP UI actually rendered (not just scripts loaded)');
  var ui = await page.evaluate(function () {
    var cc = document.getElementById('cellcount');
    var svg = document.getElementById('svg');
    return { cellcount: cc ? (cc.textContent || '').trim() : '(no #cellcount)',
             svgChildren: svg ? svg.querySelectorAll('*').length : 0,
             reclist: !!document.getElementById('reclist'),
             panel: !!document.getElementById('panel') };
  });
  console.log('§PROBE ui cellcount="' + ui.cellcount + '" svgChildren=' + ui.svgChildren + ' reclist=' + ui.reclist + ' panel=' + ui.panel);
  verdict(ui.svgChildren > 5 && ui.panel, 'ERP spine graph rendered (SVG has nodes) + dossier panel present (the AD/ERP view is live)',
          'svgChildren=' + ui.svgChildren);

  console.log('\nD4 — a REAL in-browser period-close checkpoint round-trips (crypto.subtle, sql.js)');
  // the page loads sqljs LAZILY (withBundle) — ensure window.initSqlJs is present before exercising the kernel.
  await page.evaluate(function () {
    if (window.initSqlJs) return Promise.resolve();
    return new Promise(function (res, rej) {
      var s = document.createElement('script'); s.src = 'sqljs/sql-wasm.js';
      s.onload = function () { res(); }; s.onerror = function () { rej(new Error('sqljs load failed')); };
      document.head.appendChild(s);
    });
  });
  await new Promise(function (r) { setTimeout(r, 500); });
  var live = await page.evaluate(async function () {
    var KO = window.KernelOps;
    try {
      if (!window.initSqlJs) return { skip: 'no initSqlJs' };
      var SQL = await window.initSqlJs({ locateFile: function (f) { return 'sqljs/' + f; } });
      var db = new SQL.Database(); KO.ensureTable(db);
      // period 1: one balanced doc-group, then close (signer is opt-in; W-CHAIN-only anchor here)
      var g1 = await KO.commitGroup(db, [
        { op_type: 'CRUD_CREATE', op_uuid: 'pb-dr', params: { table: 'Fact_Acct', op_type: 'CRUD_CREATE', fields: { Account: 'Cash', Amount: 500 } } },
        { op_type: 'CRUD_CREATE', op_uuid: 'pb-cr', params: { table: 'Fact_Acct', op_type: 'CRUD_CREATE', fields: { Account: 'Revenue', Amount: -500 } } }
      ], { gid: 'pb-g1', baseTs: 1000 });
      var ck = await KO.closePeriod(db, { balances: { Cash: 500, Revenue: -500 } });
      // open period 2: one op AFTER the checkpoint — bounded verify must scan only from the checkpoint
      await KO.commitGroup(db, [{ op_type: 'CRUD_CREATE', op_uuid: 'pb2', params: { table: 'X', op_type: 'CRUD_CREATE', fields: {} } }], { gid: 'pb-g2', baseTs: 2000 });
      var vb = await KO.verifyChain(db, { fromCheckpoint: true });
      var vf = await KO.verifyChain(db);
      return { committed: g1.committed, ckAtOp: ck.atOpId, ckHead: (ck.headHash || '').slice(0, 10),
               boundedOk: vb.ok, boundedScannedFrom: vb.scannedFrom, boundedLen: vb.len, fullOk: vf.ok, fullLen: vf.len };
    } catch (e) { return { error: e.message }; }
  });
  console.log('§PROBE live-checkpoint ' + JSON.stringify(live));
  verdict(live && live.committed === true && live.boundedOk === true && live.fullOk === true && live.boundedScannedFrom === live.ckAtOp,
          'in-browser: commitGroup committed, closePeriod anchored, bounded verify OK and scanned ONLY from the checkpoint (full verify also OK)',
          live && live.error ? 'error=' + live.error : 'boundedScannedFrom=' + (live && live.boundedScannedFrom) + ' ckAtOp=' + (live && live.ckAtOp));

  // desktop #diagBtn must be HIDDEN (it is the mobile-only control)
  var deskDiag = await page.evaluate(function () { var d = document.getElementById('diagBtn'); return d ? getComputedStyle(d).display : '(none-el)'; });
  await page.close();

  // ── MOBILE (≤760px) ──────────────────────────────────────────────────────────────────────────
  console.log('\nM1 — mobile viewport (390×844, touch): the mobile-only #diagBtn appears, no errors');
  var merrors = [];
  var mp = await browser.newPage();
  await mp.setUserAgent('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148');
  await mp.setViewport({ width: 390, height: 844, isMobile: true, hasTouch: true, deviceScaleFactor: 3 });
  mp.on('pageerror', function (e) { merrors.push('PAGEERROR ' + e.message); });
  mp.on('requestfailed', function (r) { var u = r.url(); if (!/favicon/.test(u)) merrors.push('REQFAIL ' + u); });
  await mp.goto(URL, { waitUntil: 'networkidle2', timeout: 30000 });
  await new Promise(function (r) { setTimeout(r, 2000); });
  var mob = await mp.evaluate(function () {
    var d = document.getElementById('diagBtn'), ph = document.getElementById('phandle');
    return { diagDisplay: d ? getComputedStyle(d).display : '(none-el)',
             phandle: ph ? getComputedStyle(ph).display : '(none-el)',
             isMobileFn: (typeof window.matchMedia === 'function') ? window.matchMedia('(max-width:760px)').matches : null,
             innerW: window.innerWidth };
  });
  console.log('§PROBE mobile diagBtn.display=' + mob.diagDisplay + ' (desktop was ' + deskDiag + ') phandle=' + mob.phandle + ' matchMedia≤760=' + mob.isMobileFn + ' innerW=' + mob.innerW + ' pageerrors=' + merrors.length);
  verdict(deskDiag === 'none' && mob.diagDisplay === 'block' && merrors.length === 0,
          'mobile UI adapts: the mobile-only #diagBtn is hidden on desktop and SHOWN on a ≤760px touch viewport, no errors',
          'desktop=' + deskDiag + ' mobile=' + mob.diagDisplay + ' merrors=' + merrors.length);
  await mp.close();

  await browser.close();
  console.log('\n§PROBE ' + (fails ? 'FAIL — ' + fails + ' checks red: the v9 ERP did NOT smoke clean on localhost.'
    : 'PASS — served glassbowl ERP loads clean (0 pageerrors), the kernel is v9 with the §I-D + §I-K primitives '
    + 'exported, the iDempiere spine UI renders, an in-browser period-close checkpoint round-trips (bounded verify '
    + 'scans only from the checkpoint), and the mobile UI reveals its touch-only control. Wiring verified on localhost.'));
  process.exit(fails ? 1 : 0);
})().catch(function (e) { console.error('FATAL', e); process.exit(2); });
