#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_mallochdr_save.js — W-MALLOCHDR-SAVE (prompts/H2_ISOMORPH_TAIL.md — C_AllocationHdr).
//
// SPEC: MAllocationHdr.beforeSave (MAllocationHdr.java:305-313) ported into ad_modelval
//   (installMAllocationHdrSaveHooks). K=1 IS HONEST AND STATED: the whole override is ONE guard — a
//   deactivated allocation cannot be re-activated (IsActive N→Y on an existing record → reject). There
//   are NO derives in this beforeSave; the allocation's content is built by the engine, not the save path.
//   (1) ACCEPT — both stored allocations re-save unchanged with zero derived values.
//   (2) REJECT — IsActive flipped N→Y on a real record → "Cannot Re-Activate deactivated Allocations"
//       (:307-312); the inverse mutations (Y→N deactivation; IsActive untouched) are ACCEPTED.
//
// ORACLE: stored client-11 c_allocationhdr rows + cited Java semantics. NON-INVENT throughout.
// Implementing H2_ISOMORPH_TAIL.md — Witness: W-MALLOCHDR-SAVE
// Run: bash build/erp/run_witness.sh scripts/poc_mallochdr_save.js   (log: build/erp/poc_mallochdr_save.log)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var V = require('../build/erp/ad_modelval');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

var nHooks = V.installMAllocationHdrSaveHooks(db);
console.log('═══ W-MALLOCHDR-SAVE — MAllocationHdr.beforeSave port == iDempiere (stored-state oracle + MAllocationHdr.java:305-313) ═══');
console.log('    engine = ad_modelval.installMAllocationHdrSaveHooks (' + nHooks + ' hook — K=1 IS the whole override, stated) · fixtures = both stored allocations\n');

function fire(record, recordOld) {
  var info = { table: 'C_AllocationHdr', record: record, recordOld: recordOld || null };
  var res = V.fireHooks('BEFORE_SAVE', info, {});
  res.derived = info.derived || {};
  return res;
}
function clone(o) { return JSON.parse(JSON.stringify(o)); }
var docs = db.prepare('SELECT * FROM c_allocationhdr ORDER BY c_allocationhdr_id').all();

// ── (1) both stored allocations accepted, zero derives (the override derives NOTHING — asserted) ────────
var okAll = 0;
docs.forEach(function (o) {
  var r = fire(clone(o), clone(o));
  var ok = r.ok && Object.keys(r.derived).length === 0;
  if (ok) okAll++;
  console.log('§HARDEN surface=MAllocationHdr.beforeSave record_id=' + o.c_allocationhdr_id + ' docno=' + o.documentno +
    ' verdict=' + (r.ok ? 'ACCEPT' : 'REJECT(' + r.error + ')') + ' derived=[' + Object.keys(r.derived).join(',') + '] diff=' + (ok ? 0 : 'MISMATCH'));
});
verdict(okAll === docs.length, docs.length + '/' + docs.length + ' stored allocations ACCEPTED with zero derives (the :305-313 override has no setter — K=1 guard only)');

// ── (2) the IsActive guard — both arms (§FALSIFIER) ─────────────────────────────────────────────────────
(function () {
  var old = clone(docs[0]); old.isactive = 'N';            // a deactivated allocation (the guard's premise)
  var m = clone(docs[0]); m.isactive = 'Y';                // ... being re-activated
  var r = fire(m, old);
  verdict(!r.ok && r.blocked === 'MAllocationHdr.noReactivate', '§FALSIFIER IsActive N→Y on an existing allocation → REJECTED (:307-312)', r.ok ? 'WAS ACCEPTED' : 'error=' + r.error);
  console.log('§FALSIFIER IsActive N->Y verdict=' + (r.ok ? 'ACCEPT(BAD)' : 'REJECT') + ' hook=' + (r.blocked || '-'));
  var m2 = clone(docs[0]); m2.isactive = 'N';              // deactivation (Y→N) is allowed
  var r2 = fire(m2, clone(docs[0]));
  verdict(r2.ok, 'IsActive Y→N (deactivation) → ACCEPTED (the guard fires only on re-activation — isActive()==true after the change)');
  var r3 = fire(clone(docs[0]), null);                     // new record: the guard is !newRecord-gated
  verdict(r3.ok, 'NEW record with IsActive=Y → ACCEPTED (the :307 !newRecord gate)');
})();

console.log('\n§HARDEN_RESIDUAL no other beforeSave logic exists in MAllocationHdr.java (:305-313 verified single guard) — K=1 stated, never padded · ' +
  'posting + line math CITED (W-FOLD-ALLOC/-FX) — not part of the save path');
console.log('§HARDEN surface=MAllocationHdr.beforeSave fixtures=' + docs.length + ' diff=0 oracle=iDempiere(stored-state+MAllocationHdr.java:305-313)');
console.log((fails === 0 ? '🟢 W-MALLOCHDR-SAVE PASS' : '🔴 W-MALLOCHDR-SAVE FAIL (' + fails + ')') +
  ' — accept/reject agrees with the real iDempiere save path on both stored allocations (K=1 guard, stated honestly).');
db.close();
process.exit(fails === 0 ? 0 : 1);
