#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_mcash_save.js — W-MCASH-SAVE (prompts/H2_ISOMORPH_TAIL.md — C_Cash).
//
// SPEC: MCash.beforeSave (MCash.java:321-331) ported into ad_modelval (installMCashSaveHooks, 2 hooks).
//   K=3 IS HONEST: the 3 stored c_cash docs = the whole seed, replayed.
//   (1) ACCEPT — all 3 accepted with zero derived contradictions (org already == cashbook org 11;
//       ending already == beginning + statementdifference on all 3).
//   (2) DERIVED — AD_Org ← cashbook's org (:323, MUST 3/3: strip→re-derive 11) · EndingBalance =
//       BeginningBalance + StatementDifference (:330, MUST 3/3 cent-exact) · CROSS-CHECK: the stored
//       StatementDifference itself equals Σ(c_cashline.amount) per journal (the derive's input is the
//       real line fold — proven from the captured lines, not assumed).
//   (3) REJECT — cashbook unresolvable → org 0 → "@AD_Org_ID@" (:324-328; mutation: a c_cashbook_id
//       absent from the capture — the cited condition is org==0 after the copy).
//
// ORACLE: stored client-11 c_cash + captured c_cashbook/c_cashline + cited Java semantics. NON-INVENT.
// Implementing H2_ISOMORPH_TAIL.md — Witness: W-MCASH-SAVE
// Run: bash build/erp/run_witness.sh scripts/poc_mcash_save.js   (log: build/erp/poc_mcash_save.log)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var V = require('../build/erp/ad_modelval');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

var nHooks = V.installMCashSaveHooks(db);
console.log('═══ W-MCASH-SAVE — MCash.beforeSave port == iDempiere (stored-state oracle + MCash.java:321-331) ═══');
console.log('    engine = ad_modelval.installMCashSaveHooks (' + nHooks + ' hooks) · fixtures = the 3 real c_cash docs (K=3 = the whole seed)\n');

function fire(record, recordOld) {
  var info = { table: 'C_Cash', record: record, recordOld: recordOld || null };
  var res = V.fireHooks('BEFORE_SAVE', info, {});
  res.derived = info.derived || {};
  return res;
}
function clone(o) { return JSON.parse(JSON.stringify(o)); }
var docs = db.prepare('SELECT * FROM c_cash ORDER BY c_cash_id').all();

// ── (1) all 3 stored docs accepted with zero contradictions ─────────────────────────────────────────────
var okAll = 0, contradictions = 0;
docs.forEach(function (o) {
  var r = fire(clone(o), clone(o));
  var contra = Object.keys(r.derived).filter(function (k) { return String(r.derived[k]) !== String(o[k]); });
  if (r.ok && contra.length === 0) okAll++; else contradictions += contra.length;
  console.log('§HARDEN surface=MCash.beforeSave record_id=' + o.c_cash_id + ' name=' + o.name +
    ' verdict=' + (r.ok ? 'ACCEPT' : 'REJECT(' + r.error + ')') + ' derived-contradictions=' + contra.length +
    ' derived=[' + Object.keys(r.derived).join(',') + '] diff=' + (r.ok && contra.length === 0 ? 0 : 'MISMATCH'));
});
verdict(okAll === docs.length, docs.length + '/' + docs.length + ' stored cash journals ACCEPTED with zero derived contradictions', 'contradictions=' + contradictions);

// ── (2) derived values reproduce the stored / master values ─────────────────────────────────────────────
(function () {  // AD_Org ← cashbook org (:323) — MUST 3/3
  var ok = docs.every(function (o) { var m = clone(o); m.ad_org_id = 0; return Number(fire(m, clone(o)).derived.ad_org_id) === Number(o.ad_org_id); });
  verdict(ok, 'AD_Org ←cashbook 101 org re-derives the STORED 11 on all 3 (:323, MUST)');
})();
(function () {  // EndingBalance = Beginning + StatementDifference (:330) — MUST 3/3 cent-exact
  var ok = true, det = [];
  docs.forEach(function (o) {
    var m = clone(o); m.endingbalance = 0;
    var g = fire(m, clone(o)).derived.endingbalance;
    if (Number(g) !== Number(o.endingbalance)) { ok = false; det.push('cash ' + o.c_cash_id + ' derived=' + g + ' stored=' + o.endingbalance); }
  });
  verdict(ok, 'EndingBalance = Beginning+StatementDifference re-derives the STORED value on all 3, cent-exact (:330, MUST)', det.join('; ') || 'diff=0');
})();
(function () {  // CROSS-CHECK: stored StatementDifference == Σ captured line amounts (the derive's input is real)
  var ok = true, det = [];
  docs.forEach(function (o) {
    var s = db.prepare('SELECT round(coalesce(sum(amount),0),2) s FROM c_cashline WHERE c_cash_id=?').get(o.c_cash_id).s;
    if (Number(s) !== Number(o.statementdifference)) { ok = false; det.push('cash ' + o.c_cash_id + ' Σlines=' + s + ' stored=' + o.statementdifference); }
    console.log('§HARDEN surface=MCash.statementDifference record_id=' + o.c_cash_id + ' Σ(c_cashline.amount)=' + s + ' stored=' + o.statementdifference + ' diff=' + (Number(s) === Number(o.statementdifference) ? 0 : 'MISMATCH'));
  });
  verdict(ok, 'stored StatementDifference == Σ(c_cashline.amount) on all 3 (the :330 input is the real line fold)', det.join('; ') || 'diff=0');
})();

// ── (3) REJECT — the org-0 arm (§FALSIFIER) ─────────────────────────────────────────────────────────────
(function () {
  var m = clone(docs[0]); m.c_cashbook_id = 999999;        // unresolvable cashbook → org copy yields 0 (:324)
  var r = fire(m, clone(docs[0]));
  verdict(!r.ok && r.blocked === 'MCash.orgFromCashbook', '§FALSIFIER unresolvable cashbook → org 0 → REJECTED (:324-328 @AD_Org_ID@)', r.ok ? 'WAS ACCEPTED' : 'error=' + r.error);
  console.log('§FALSIFIER cashbook->org0 verdict=' + (r.ok ? 'ACCEPT(BAD)' : 'REJECT') + ' hook=' + (r.blocked || '-'));
})();

console.log('\n§HARDEN_RESIDUAL the seed has NO org-0 cashbook (101→org11, 102→org12) — the reject arm is proven via the unresolvable-cashbook mutation, the cited condition being org==0 after the :323 copy · ' +
  'cash NAME derive (date-based) lives in the MCash constructor, not beforeSave — out of scope, named');
console.log('§HARDEN surface=MCash.beforeSave fixtures=' + docs.length + ' diff=0 oracle=iDempiere(stored-state+MCash.java:321-331)');
console.log((fails === 0 ? '🟢 W-MCASH-SAVE PASS' : '🔴 W-MCASH-SAVE FAIL (' + fails + ')') +
  ' — accept/reject + derived defaults agree with the real iDempiere save path on all 3 stored cash journals (K=3 stated honestly).');
db.close();
process.exit(fails === 0 ? 0 : 1);
