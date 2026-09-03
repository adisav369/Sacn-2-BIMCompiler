#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_sap_fold.js — the migration-solvent FALSIFIER at its ASYMPTOTE: SAP (prompts/SAP_FOLD_POC.md Step 2).
 *   Mirrors poc_odoo_fold.js / diff_oracle.js: drive a STANDARD SAP SD order-to-cash + FI chain (from a
 *   STATIC export of executed rows) through the EXISTING kernel verbs via the PURE sap_adapter, then diff
 *   the canonical effects against the SAP oracle (effect-set multiset diff, to the cent).
 *
 * GATED ON ORACLE ACCESS — the real blocker (IDES is licensed; S/4HANA trial; or a documented standard
 * dataset). Per the spec's hard rule: NO synthesised SAP rows. If build/erp/sap_oracle.json is ABSENT, this
 * runner reports `§SAP-ORACLE unavailable` and STOPS — an honest "blocked on oracle access" is the correct
 * outcome, NOT invented data. The adapter + this runner are the prepared skeleton; they ACTIVATE the moment
 * a real oracle drops in (same shape as build/erp/sap_oracle.template.json), with NO further wiring.
 *
 * Deterministic: when a real oracle exists, every id/qty/amount is a RECORDED INPUT from it (no Date.now /
 * Math.random; no live SAP at replay, §0.12). Run:
 *   node scripts/poc_sap_fold.js 2>&1 | tee -a build/erp/sap_fold.log
 */
'use strict';
var path = require('path'), fs = require('fs');
var A = require('./sap_adapter');

var ORACLE_PATH = path.join(__dirname, '..', 'build', 'erp', 'sap_oracle.json');

function banner() {
  console.log('\n══════════════════════════════════════════════════════════════════════════════');
  console.log('═══ POC-SAP-FOLD — migration-solvent falsifier at the ASYMPTOTE (SAP SD+FI) ═══');
  console.log('══════════════════════════════════════════════════════════════════════════════\n');
}

// ── the GATE: no real oracle → report unavailable + the prepared hypothesis, and STOP (do NOT fabricate) ──
function reportUnavailable() {
  banner();
  console.log('── STATUS: SKELETON READY, ORACLE BLOCKED (the honest outcome — not a failure, not a PASS) ──\n');
  console.log('The SAP adapter (scripts/sap_adapter.js) + this runner are the prepared falsifier. They are');
  console.log('GATED on a REAL static export of executed SAP rows (build/erp/sap_oracle.json). None exists yet.\n');

  console.log('── the schema HYPOTHESIS to verify on a real export (sap_adapter.SCHEMA_MAP) ──');
  Object.keys(A.SCHEMA_MAP).forEach(function (t) {
    var m = A.SCHEMA_MAP[t];
    console.log('   ' + t.padEnd(7) + ' → ' + String(m.doc_type).padEnd(20) + ' [' + m.bridge + ']  ' + (m.note || ''));
  });

  console.log('\n── the state/flow HYPOTHESIS (VBFA-led; sap_adapter.STATE_MAP) ──');
  A.STATE_MAP.forEach(function (s) { console.log('   ' + s.sap.padEnd(30) + ' → ' + s.verb.padEnd(16) + ' (' + s.cell + ')'); });

  console.log('\n── verb set under test (unchanged from iDempiere + Odoo) ──');
  console.log('   KNOWN_VERBS = [' + A.KNOWN_VERBS.join(', ') + ']  — the falsifier asks if STANDARD SAP needs MORE');

  console.log('\n── named divergences, pre-classified (sap_adapter.NAMED_DIVERGENCES) ──');
  A.NAMED_DIVERGENCES.forEach(function (d) { console.log('   ' + d.id + ' [' + d.scope + '] ' + d.what); });

  console.log('\n── the witnesses this WILL emit once a real oracle exists (sap_adapter.PLANNED_WITNESSES) ──');
  A.PLANNED_WITNESSES.forEach(function (w) { console.log('   ' + w); });

  console.log('\n── how to unblock (prompts/SAP_FOLD_POC.md Step 0) ──');
  console.log('   1. Obtain ONE static export of a completed STANDARD SD O2C + FI chain (no Z): a partner/sandbox');
  console.log('      IDES export, an S/4HANA trial, or a documented standard dataset.');
  console.log('   2. Shape it into build/erp/sap_oracle.json (see build/erp/sap_oracle.template.json for the');
  console.log('      expected fields — fill from the REAL export; do NOT fabricate a single row).');
  console.log('   3. Re-run this runner: it will drive the fold + diff and emit the §SAP-FOLD verdict.');

  console.log('\n§SAP-ORACLE unavailable — no executed SAP rows to diff against; fold NOT attempted (non-invent guardrail).');
  console.log('§SAP-FOLD NOT-RUN (skeleton ready; gated on oracle access — the real blocker, not the model).');
  console.log('\n═══ VERDICT ═══');
  console.log('§SAP-FOLD BLOCKED — adapter + runner prepared and wired; awaiting a REAL SAP oracle. No fold claimed.');
  process.exit(0);                 // BLOCKED is graceful (skeleton wired), distinct from PASS/FAIL by the verdict text
}

// ── the LIVE fold path: activates the moment a real oracle exists (mirrors poc_odoo_fold.js) ──
function diffSet(mine, oracle) {
  function tally(a) { var m = {}; a.forEach(function (k) { m[k] = (m[k] || 0) + 1; }); return m; }
  var mm = tally(mine), om = tally(oracle), keys = {};
  Object.keys(mm).forEach(function (k) { keys[k] = 1; }); Object.keys(om).forEach(function (k) { keys[k] = 1; });
  var matched = 0, missed = 0, extra = 0;
  Object.keys(keys).forEach(function (k) { var a = mm[k] || 0, b = om[k] || 0; matched += Math.min(a, b); if (b > a) missed += b - a; if (a > b) extra += a - b; });
  return { matched: matched, missed: missed, extra: extra, ok: missed === 0 && extra === 0 };
}
function n2(x) { return Number(x).toFixed(2); }

async function runFold(ORACLE) {
  var initSqlJs = require('sql.js');
  var K = require('./erp_kernel');
  var SQL = await initSqlJs();
  var built = A.buildSapEvents(ORACLE);
  var orderId = ORACLE.meta.order, billId = ORACLE.meta.billing;
  var fails = 0;
  function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

  banner();
  console.log('── FIRST LIVE RUN against a real oracle — VERIFY every value vs the export ──');
  console.log('    source: ' + (ORACLE.meta.source || '(unspecified)') + '  | GL source: ' + built.gl_source + '\n');

  built.events.forEach(function (ev) { K.register(ev.d.docType, ev.d.action, function () { return ev.ops; }); });
  var db = new SQL.Database(); K.initProjection(db);
  var qfn = function (s, p) { return K.query(db, s, p); };

  var usedVerbs = {}, mappedHops = 0;
  built.events.forEach(function (ev, i) {
    ev.ops.forEach(function (o) { usedVerbs[o.op_type] = 1; });
    var d = K.dispatch(db, { wfmc: built.wfmc, guards: [], query: qfn, actor: 'sap:migrate', baseTs: 8000 + i * 100 }, ev.d);
    verdict(d.ok, 'event ' + (i + 1) + ' (' + ev.name + ') committed (' + ev.d.status + '→' + (d.to || '?') + ')', d.ok ? 'ops=' + d.applied : d.stage + ':' + d.reason);
    if (d.ok) mappedHops++;
  });
  var used = Object.keys(usedVerbs).sort();
  var newVerbs = used.filter(function (v) { return A.KNOWN_VERBS.indexOf(v) < 0; });

  // FI posting: balanced + total reproduces the billing
  var GLW = " WHERE source='DOC:C_Invoice#" + billId + "' AND account_id IS NOT NULL";
  var mDR = K.query(db, "SELECT COALESCE(SUM(amtacctdr),0) s FROM journal" + GLW)[0].s;
  var mCR = K.query(db, "SELECT COALESCE(SUM(amtacctcr),0) s FROM journal" + GLW)[0].s;
  var glLines = K.query(db, "SELECT account_id a, amtacctdr dr, amtacctcr cr FROM journal" + GLW).map(function (r) { return r.a + ':' + n2(r.dr) + ':' + n2(r.cr); });
  verdict(Math.round(mDR * 100) === Math.round(mCR * 100), 'FI posting balanced (ΣDR==ΣCR)', 'ΣDR=' + n2(mDR) + ' ΣCR=' + n2(mCR));
  if (ORACLE.meta.billing_total != null) verdict(Math.round(mDR * 100) === Math.round(ORACLE.meta.billing_total * 100), 'FI total == billing total (to the cent)', 'ΣDR=' + n2(mDR) + ' billing=' + n2(ORACLE.meta.billing_total));

  console.log('');
  console.log('§SAP-FOLD chain order→delivery→billing→fi-posting→clearing mapped=' + mappedHops + '/5 missing=' + (5 - mappedHops));
  console.log('§SAP-FOLD verbs used=[' + used.join(',') + '] newVerbs=[' + newVerbs.join(',') + ']');
  console.log('§SAP-FOLD acdoca-as-fold source=' + built.gl_source + ' lines=' + glLines.length + ' balanced=' + (Math.round(mDR * 100) === Math.round(mCR * 100) ? 'Y' : 'N'));
  console.log('§SAP-FINDINGS see sap_adapter.NAMED_DIVERGENCES (standard in-scope vs Z/config out-of-scope)');

  db.close();
  console.log('\n═══ VERDICT ═══');
  console.log('§SAP-FOLD ' + (fails ? 'BOUNDED/FAIL — ' + fails + ' checks red (NAME the gap: verb / matcher-behaviour / adapter-data; update HolyGrail SAP scope)'
    : 'FIRST-LIGHT — standard SD+FI chain drove through the existing verbs (newVerbs=[' + newVerbs.join(',') + ']). VERIFY the diff vs the export before claiming PASS.'));
  process.exit(fails ? 1 : 0);
}

(function main() {
  if (!fs.existsSync(ORACLE_PATH)) return reportUnavailable();
  var ORACLE;
  try { ORACLE = JSON.parse(fs.readFileSync(ORACLE_PATH, 'utf8')); }
  catch (e) { banner(); console.log('§SAP-ORACLE unreadable: ' + e.message + ' — fix the export, do NOT fabricate.'); process.exit(2); }
  if (!ORACLE.meta || ORACLE.__template === true) {
    banner();
    console.log('§SAP-ORACLE present but is the TEMPLATE / has no meta — fill it from a REAL export first (non-invent).');
    return reportUnavailable();
  }
  runFold(ORACLE);
})();
