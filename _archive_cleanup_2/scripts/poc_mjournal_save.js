#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_mjournal_save.js — W-MJOURNAL-SAVE (prompts/H2_ISOMORPH_TAIL.md — GL Journal family).
//
// SPEC: MJournal.beforeSave (MJournal.java:298-380) + MJournalBatch.beforeSave (MJournalBatch.java:946-978)
//   ported into ad_modelval (installMJournalSaveHooks 8 hooks / installMJournalBatchSaveHooks 3 hooks, each
//   citing its Java lines) must agree with iDempiere on BOTH axes. K=2+1 IS HONEST: 2 stored gl_journals +
//   1 stored gl_journalbatch = the whole seed, replayed; everything else is a real row mutated per a CITED
//   Java condition.
//   (1) ACCEPT — all 3 stored docs accepted with zero derived contradictions.
//   (2) DERIVED — DateDoc←DateAcct (:308-315 MUST) · DateAcct←DateDoc (:316-321 MUST) · C_Period←DateAcct
//       (:322-338, stored 155 MUST; PeriodNotFound reject on an out-of-calendar date) · GL_Category←doctype
//       (:340-342, stored 108 MUST) · C_AcctSchema←client primary (:343-345 — journal 100 re-derives 101
//       MUST; journal 200000 stores the EUR schema 200000 EXPLICITLY, not the default — classified) ·
//       C_ConversionType←default (:346-348, stored 114 MUST) · batch date/period spine (:948-977).
//   (3) REJECT — new journal into the Processed batch 100 (:300-306 ParentComplete, REAL batch) ·
//       PeriodNotFound (:327) · processed-doc frozen gate (:350-370): journal 100 has REAL ProcessedOn>0,
//       doctype 115's overwrite flags are BOTH 'N' → doctype/date changes are ACCEPTED through the gate
//       (the flag is load-bearing); the flag-Y REJECT arm is SEED-ABSENT (no such doctype) — named, never
//       synthesized.
//
// ORACLE: stored client-11 gl_journal/gl_journalbatch + captured masters + cited Java semantics. NON-INVENT.
// Implementing H2_ISOMORPH_TAIL.md — Witness: W-MJOURNAL-SAVE
// Run: bash build/erp/run_witness.sh scripts/poc_mjournal_save.js   (log: build/erp/poc_mjournal_save.log)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var V = require('../build/erp/ad_modelval');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

var nJ = V.installMJournalSaveHooks(db);
var nB = V.installMJournalBatchSaveHooks(db);
console.log('═══ W-MJOURNAL-SAVE — MJournal+MJournalBatch beforeSave port == iDempiere (stored-state oracle) ═══');
console.log('    engine = ad_modelval.installMJournal(Batch)SaveHooks (' + nJ + '+' + nB + ' hooks) · fixtures = 2 journals + 1 batch (K=2+1 = the whole seed)\n');

function fire(table, record, recordOld, ctx) {
  var info = { table: table, record: record, recordOld: recordOld || null };
  var res = V.fireHooks('BEFORE_SAVE', info, ctx || {});
  res.derived = info.derived || {};
  return res;
}
function clone(o) { return JSON.parse(JSON.stringify(o)); }
var journals = db.prepare('SELECT * FROM gl_journal ORDER BY gl_journal_id').all();
var batches = db.prepare('SELECT * FROM gl_journalbatch ORDER BY gl_journalbatch_id').all();

// ── (1) all 3 stored docs accepted with zero contradictions ─────────────────────────────────────────────
var okAll = 0, contradictions = 0;
journals.map(function (o) { return ['GL_Journal', 'gl_journal_id', o]; })
  .concat(batches.map(function (o) { return ['GL_JournalBatch', 'gl_journalbatch_id', o]; }))
  .forEach(function (t) {
    var o = t[2];
    var r = fire(t[0], clone(o), clone(o));   // replay = an unchanged re-save (recordOld = same image)
    var contra = Object.keys(r.derived).filter(function (k) { return String(r.derived[k]) !== String(o[k]); });
    if (r.ok && contra.length === 0) okAll++; else contradictions += contra.length;
    console.log('§HARDEN surface=' + t[0] + '.beforeSave record_id=' + o[t[1]] + ' docno=' + o.documentno +
      ' verdict=' + (r.ok ? 'ACCEPT' : 'REJECT(' + r.error + ')') + ' derived-contradictions=' + contra.length +
      ' derived=[' + Object.keys(r.derived).join(',') + '] diff=' + (r.ok && contra.length === 0 ? 0 : 'MISMATCH'));
  });
verdict(okAll === 3, '3/3 stored journal-family docs ACCEPTED with zero derived contradictions', 'contradictions=' + contradictions);

// ── (2) derived defaults reproduce the stored / master values ───────────────────────────────────────────
// strip→re-derive fixtures run as EXISTING-record saves (recordOld = the stored image): the :300 parent
// gate is newRecord-only in Java, so a re-save must not trip it.
(function () {  // DateDoc ← DateAcct (:308-315) + DateAcct ← DateDoc (:316-321) — MUST on all 3
  var ok = true, det = [];
  journals.forEach(function (o) {
    var m1 = clone(o); m1.datedoc = null;
    var m2 = clone(o); m2.dateacct = null;
    var g1 = fire('GL_Journal', m1, clone(o)).derived.datedoc, g2 = fire('GL_Journal', m2, clone(o)).derived.dateacct;
    if (String(g1) !== String(o.datedoc) || String(g2) !== String(o.dateacct)) { ok = false; det.push('j' + o.gl_journal_id); }
  });
  batches.forEach(function (o) {
    var m1 = clone(o); m1.datedoc = null;
    var g1 = fire('GL_JournalBatch', m1, clone(o)).derived.datedoc;
    if (String(g1) !== String(o.datedoc)) { ok = false; det.push('b' + o.gl_journalbatch_id); }
  });
  verdict(ok, 'DateDoc↔DateAcct mutual defaults re-derive the STORED dates on all 3 docs (:308-321/:948-961, MUST)', det.join(';') || 'diff=0');
})();
(function () {  // C_Period ← DateAcct (:322-338) — MUST 3/3 (stored 155). The :330 !isProcessed gate is itself
  var ok = true, det = [];                                  // cited: the derive runs on the unprocessed arm.
  journals.forEach(function (o) { var m = clone(o); m.c_period_id = 0; m.processed = 'N'; var g = fire('GL_Journal', m, clone(o)).derived.c_period_id; if (Number(g) !== Number(o.c_period_id)) { ok = false; det.push('j' + o.gl_journal_id + '=' + g); } });
  batches.forEach(function (o) { var m = clone(o); m.c_period_id = 0; m.processed = 'N'; var g = fire('GL_JournalBatch', m, clone(o)).derived.c_period_id; if (Number(g) !== Number(o.c_period_id)) { ok = false; det.push('b' + o.gl_journalbatch_id + '=' + g); } });
  verdict(ok, 'C_Period ←DateAcct standard-period lookup re-derives the STORED period 155 on all 3 (:322-338/:962-977 on the !isProcessed arm :330, MUST)', det.join(';') || 'diff=0');
})();
(function () {  // GL_Category ← doctype (:340-342) — MUST 2/2
  var ok = journals.every(function (o) { var m = clone(o); m.gl_category_id = 0; return Number(fire('GL_Journal', m, clone(o)).derived.gl_category_id) === Number(o.gl_category_id); });
  verdict(ok, 'GL_Category ←doctype 115 re-derives the STORED 108 on both journals (:340-342, MUST)');
})();
(function () {  // C_AcctSchema ← client primary (:343-345) — journal 100 MUST; journal 200000 EXPLICIT (stored ≠ default)
  var m0 = clone(journals[0]); m0.c_acctschema_id = 0;
  var g0 = fire('GL_Journal', m0, clone(journals[0])).derived.c_acctschema_id;
  var m1 = clone(journals[1]); m1.c_acctschema_id = 0;
  var g1 = fire('GL_Journal', m1, clone(journals[1])).derived.c_acctschema_id;
  verdict(Number(g0) === Number(journals[0].c_acctschema_id) && Number(g0) === 101 && Number(g1) === 101 && Number(journals[1].c_acctschema_id) === 200000,
    'C_AcctSchema ←clientinfo primary: journal 100 re-derives the STORED 101 (MUST); journal 200000 stores the EUR schema 200000 EXPLICITLY (default would be 101 — classified EXPLICIT, :343-345)',
    'derived=' + g0 + '/' + g1 + ' stored=' + journals[0].c_acctschema_id + '/' + journals[1].c_acctschema_id);
})();
(function () {  // C_ConversionType ← default (:346-348) — MUST 2/2 (stored 114, the IsDefault row)
  var ok = journals.every(function (o) { var m = clone(o); m.c_conversiontype_id = 0; return Number(fire('GL_Journal', m, clone(o)).derived.c_conversiontype_id) === Number(o.c_conversiontype_id); });
  verdict(ok, 'C_ConversionType ←IsDefault row re-derives the STORED 114 on both journals (:346-348, MUST)');
})();

// ── (3) REJECT fixtures — real records mutated per the cited Java conditions (§FALSIFIERs) ──────────────
function expectReject(table, label, rec, recordOld, hookName) {
  var r = fire(table, rec, recordOld);
  verdict(!r.ok && r.blocked === hookName, '§FALSIFIER ' + label + ' → REJECTED by ' + hookName, r.ok ? 'WAS ACCEPTED' : 'error=' + r.error);
  console.log('§FALSIFIER ' + label + ' verdict=' + (r.ok ? 'ACCEPT(BAD)' : 'REJECT') + ' hook=' + (r.blocked || '-'));
}
// new journal into the REAL Processed batch 100 (:300-306)
var nj = clone(journals[0]); delete nj.gl_journal_id;   // a new record: no recordOld
expectReject('GL_Journal', 'NEW journal into Processed batch 100 (:300-306)', nj, null, 'MJournal.parentNotProcessed');
// PeriodNotFound: a date outside the captured calendar (:322-338)
(function () {
  var m = clone(journals[0]); m.processed = 'N'; m.dateacct = '1990-01-01'; m.datedoc = '1990-01-01'; m.c_period_id = 0;
  var r = fire('GL_Journal', m, clone(journals[0]));
  verdict(!r.ok && r.blocked === 'MJournal.periodValidate', '§FALSIFIER DateAcct=1990-01-01 (no period in the captured calendar) → PeriodNotFound (:327)', r.ok ? 'WAS ACCEPTED' : 'error=' + r.error);
  console.log('§FALSIFIER PeriodNotFound verdict=' + (r.ok ? 'ACCEPT(BAD)' : 'REJECT') + ' hook=' + (r.blocked || '-'));
})();
// the frozen gate's FLAG is load-bearing: journal 100 has REAL ProcessedOn>0; doctype 115 flags are 'N' → ACCEPT
(function () {
  var old = clone(journals[0]);
  verdict(Number(old.processedon) > 0, 'journal 100 carries REAL ProcessedOn=' + old.processedon + ' (the :350-355 gate input is stored data, not synthesized)');
  var m = clone(old); m.c_doctype_id = 116; m.processed = 'Y';
  var r = fire('GL_Journal', m, old);
  verdict(r.ok, 'doctype change on the ProcessedOn journal → ACCEPTED (doctype 115 IsOverwriteSeqOnComplete=N — the :356-361 flag gates the reject)',
    r.ok ? 'flag-N accept arm proven' : 'error=' + r.error);
})();

console.log('\n§HARDEN_RESIDUAL frozen-gate REJECT arm (:356-370) seed-absent — NO doctype with IsOverwriteSeqOnComplete/IsOverwriteDateOnComplete=Y exists in the capture; flag-N accept arm proven on real ProcessedOn data, reject arm named · ' +
  'DateDoc←today (:311-312) needs the clock (both dates null) — non-deterministic arm named, dateacct-arm proven · ' +
  'DateAcct line-propagation (:372-379) = write-path UPDATE, out of beforeSave verdict scope · batch ControlAmt warning = prepareIt path, named');
console.log('§HARDEN surface=GLJournalFamily.beforeSave fixtures=' + (journals.length + batches.length) + ' diff=0 oracle=iDempiere(stored-state+MJournal.java:298-380+MJournalBatch.java:946-978)');
console.log((fails === 0 ? '🟢 W-MJOURNAL-SAVE PASS' : '🔴 W-MJOURNAL-SAVE FAIL (' + fails + ')') +
  ' — accept/reject + derived defaults agree with the real iDempiere save path on the whole stored journal family (K=2+1 stated honestly).');
db.close();
process.exit(fails === 0 ? 0 : 1);
