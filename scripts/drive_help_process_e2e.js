// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// Witness: W-GUIDE-PROCESS-E2E (GP1·GP2·GP3 wired) — the FULL O2C coach↔Process loop in a REAL browser.
// The units are headless-proven (W-HELP-COACH / W-HELP-NEXTGATE / W-CRUD-WRITELOOP-OVERLAY); this proves
// they COMPOSE end-to-end through the key-addressed bus + shared DOM, on the deployed page:
//  A) GATE-HOLD : on a process step (Edit-mode ON) pressing Next BEFORE processing HOLDS (advanced=N).
//  B) PULSE     : ShowMe drives reveal+pulse+highlight:statusbar; the CRUD ring reveals the ▶ (no auto-fire).
//  C) COMMIT    : clicking ▶ commits a REAL signed op (verifyChain=ok) and paints #docStatusBar s-CO;
//                 read-the-tip recovers CO in-browser.
//  D) GATE-ADVANCE: pressing Next now reads the live CO and ADVANCES (advanced=Y) + the help step moves on.
//  E) VEER      : an off-path action on the bus SUSPENDS the guide (NeedHelp on, no timeline tag).
// §-log first (browser console → file): writes build/erp/help_process_e2e_witness.log; READ the log.
'use strict';
var puppeteer = require('puppeteer');
var fs = require('fs');
var URL = 'http://localhost:8000/glassbowl.html';
var LOG = __dirname + '/../build/erp/help_process_e2e_witness.log';

var out = [], con = [], errs = [], pass = 0, fail = 0;
function L(s) { out.push(s); }
function ck(c, m) { if (c) { pass++; L('§E2E PASS ' + m); } else { fail++; L('§E2E FAIL ' + m); } }
var sleep = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };
function sawCon(re) { return con.some(function (l) { return re.test(l); }); }
function lastCon(re) { var m = con.filter(function (l) { return re.test(l); }); return m.length ? m[m.length - 1] : null; }

(async function () {
  var browser;
  try {
    browser = await puppeteer.launch({
      headless: 'new', executablePath: '/usr/bin/google-chrome',
      args: ['--no-sandbox', '--disable-dev-shm-usage', '--use-gl=swiftshader', '--enable-unsafe-swiftshader', '--window-size=1400,900']
    });
  } catch (e) { L('§E2E FATAL launch: ' + e.message); fs.writeFileSync(LOG, out.join('\n') + '\n'); console.log(out.join('\n')); process.exit(2); }
  var page = await browser.newPage();
  await page.setViewport({ width: 1400, height: 900 });
  page.on('console', function (m) { con.push(m.text()); });
  page.on('pageerror', function (e) { errs.push(String(e)); });
  try { await page.goto(URL, { waitUntil: 'networkidle2', timeout: 45000 }); } catch (e) { L('§E2E goto warn: ' + e.message); }
  await sleep(4000);

  ck(errs.length === 0, 'boot pageerror=0' + (errs.length ? ' [' + errs.slice(0, 2).join(' | ') + ']' : ''));
  ck(sawCon(/§HELP layer mounted/) && sawCon(/§CRUD layer mounted/) && sawCon(/KERNEL_OPS_LOADED/), 'both overlays + signed kernel mounted');

  // modes on; jump to the c_invoice process step (index 3: o2c,c_order,m_inout,c_invoice,c_payment,c_allocationline)
  await page.click('#needHelpCk'); await sleep(700);
  await page.click('#crudModeCk'); await sleep(700);
  var atStep = await page.evaluate(function () { window.__help.goTo(3, false); var s = window.__help.steps()[3]; return s && s.key; });
  ck(atStep === 'c_invoice', 'navigated to the c_invoice process step (key=' + atStep + ')');

  // A) GATE-HOLD — Next before processing (bar empty) must NOT advance
  con.length = 0;
  await page.click('#hcNext'); await sleep(400);
  var holdGate = lastCon(/§HELP next gate/);
  ck(/advanced=N/.test(holdGate || ''), 'A: Next-before-process HOLDS (' + (holdGate || 'no gate log') + ')');

  // B) PULSE — ShowMe reveals + pulses the Process ▶ (no auto-fire)
  con.length = 0;
  await page.click('#hcShow'); await sleep(700);
  ck(sawCon(/§HELP coach .*drove=\[reveal,pulse,highlight:statusbar\] asserts=0/), 'B: ShowMe drove the full coach vocab, asserts=0');
  var ringHasProc = await page.evaluate(function () { var f = document.querySelector('#crudRing .crud-fab.proc'); return !!(f && !f.disabled); });
  ck(ringHasProc && sawCon(/§CRUD pulse key=c_invoice proc revealed/), 'B: CRUD ring revealed the ▶ on pulse (not fired)');

  // C) COMMIT — click ▶ → real signed write + bar CO + read-the-tip CO
  con.length = 0;
  var clicked = await page.evaluate(async function () { var f = document.querySelector('#crudRing .crud-fab.proc'); if (!f) return false; f.click(); await new Promise(function (z) { setTimeout(z, 700); }); return true; });
  ck(clicked, 'C: ▶ clickable (ring present)');
  var commit = lastCon(/§CRUD process committed/);
  ck(/verifyChain=ok/.test(commit || ''), 'C: real signed commit verifyChain=ok (' + (commit || 'no commit log') + ')');
  var bar = await page.evaluate(function () { var b = document.getElementById('docStatusBar'); return { cls: b && b.className, txt: b && b.textContent }; });
  ck(/\bs-CO\b/.test(bar.cls || ''), 'C: #docStatusBar shows s-CO (cls="' + bar.cls + '")');
  var tipOk = await page.evaluate(function () {
    var h = window.__crud.history(); if (!h.length) return null;
    var top = h[0]; return (top.params && top.params.to === 'CO' && window.__crud.readTip(top.params.table, top.params.id) === 'CO') ? (top.params.table + '#' + top.params.id) : false;
  });
  ck(!!tipOk, 'C: read-the-tip recovers CO for the committed doc (' + tipOk + ')');

  // D) GATE-ADVANCE — Next now reads live CO and advances the help step
  con.length = 0;
  await page.click('#hcNext'); await sleep(500);
  var advGate = lastCon(/§HELP next gate/);
  ck(/docstatus=CO advanced=Y/.test(advGate || ''), 'D: Next gate reads CO → ADVANCED=Y (' + (advGate || 'no gate log') + ')');
  ck(sawCon(/§READSHOWME step=4/), 'D: help step advanced to 4 (c_payment) after the gate opened');

  // E) VEER — an off-path action suspends (NeedHelp on, no tag)
  con.length = 0;
  await page.evaluate(function () { window.dispatchEvent(new CustomEvent('overlay:action', { detail: { verb: 'view', key: 'c_order' } })); });
  await sleep(300);
  var veer = lastCon(/§HELP veer→suspend/);
  ck(/needhelp=on tag=unchanged/.test(veer || ''), 'E: off-path action → suspend, NeedHelp on, no tag (' + (veer || 'no veer log') + ')');
  var stillOn = await page.evaluate(function () { return document.getElementById('needHelpCk').checked && window.__help.suspended(); });
  ck(stillOn, 'E: NeedHelp stays checked + guide suspended (not killed)');

  L('§E2E SUMMARY ' + (fail === 0 ? 'PASS' : 'FAIL') + ' pass=' + pass + ' fail=' + fail + ' pageerror=' + errs.length);
  fs.writeFileSync(LOG, out.join('\n') + '\n');
  console.log(out.join('\n'));
  await browser.close();
  process.exit(fail === 0 ? 0 : 1);
})();
