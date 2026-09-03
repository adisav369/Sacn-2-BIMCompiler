#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Item 1 (FRONTEND_LANE_MASTER.md §OUTSTANDING — PRIVATE DRAFT RESTORE-POINT).
//   Read the log after every run.
// poc_draft_restore.js — W-DRAFT-RESTORE: the engine half of the "no official dot while typing" model.
//   Save is the PUBLISH boundary (private→official). An unsaved edit is PRIVATE + local — it MUST NOT be
//   committed to the op-log (other docs read the committed tip via readTip/tipValues, never a half-typed
//   buffer). Leaving the form buffers the typed values into a per-(table,id) store with a dirty-pip and seals
//   NO official dot; on return the default is the saved official tip, the buffer is an OPT-IN restore. A
//   validated Save folds draft→ONE official dot and clears the buffer; discard drops it. The op-log stays the
//   only official truth.
//
// ISSUES IT PROVES (named):
//   §DRAFT-PRIVATE  — leaving a DIRTY form refreshes the private buffer but commits ZERO ops; the official tip
//                     (tipValues, what other docs read) is UNCHANGED — the unsaved value does not leak.
//   §DRAFT-DEFAULT  — on reopen the DEFAULT baseline is the saved official tip, NOT the buffered typing; the
//                     buffer never intrudes unless opted into.
//   §DRAFT-OPTIN    — the opt-in restore returns exactly the unsaved typed values (the back-pip's payload).
//   §DRAFT-COMMIT   — a validated Save commits EXACTLY ONE official op (tip now carries the value) AND clears
//                     the buffer (one official dot, pip gone).
//   §DRAFT-CLEAN    — leaving a CLEAN form (no changes) buffers nothing and clears any stale pip (no false pip).
//   §DRAFT-DRIFT    — if the saved tip moved underneath the draft, draftDrift flags the changed cols so the
//                     restore can WARN ("record changed underneath", the item-1 decision owed) — not clobber.
//   §FALSIFIER      — before any typing draftGet is null; a buffer is never an op (op count proves it); the
//                     pip list reflects exactly the buffered drafts (no fabrication).
//
// NON-INVENT: every official value traces to a real committed op row; the draft buffer is storage-only and
//   carries no authority; deterministic ids + baseTs (no Date.now in path).
// Implementing FRONTEND_LANE_MASTER.md §OUTSTANDING item 1 — Witness: W-DRAFT-RESTORE
// Run: bash build/erp/run_witness.sh scripts/poc_draft_restore.js — then READ the log.
'use strict';
var path = require('path');
var initSqlJs = require('sql.js');

global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var KO = global.window.KernelOps;
var CORE = require(path.join(__dirname, '..', 'build', 'erp', 'crud_overlay.js'));

var fails = 0;
function verdict(ok, label, detail) {
  if (!ok) fails++;
  console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : ''));
}
// case-insensitive getter (tip/changes keys vary in AD column casing).
function ci(o, c) { if (!o) return undefined; var lk = String(c).toLowerCase(); for (var k in o) if (k.toLowerCase() === lk) return o[k]; }

// A localStorage-shaped Map mock — the injected store the browser supplies as window.localStorage.
function makeStorage() {
  var m = {};
  return {
    getItem: function (k) { return Object.prototype.hasOwnProperty.call(m, k) ? m[k] : null; },
    setItem: function (k, v) { m[k] = String(v); },
    removeItem: function (k) { delete m[k]; },
    key: function (i) { return Object.keys(m)[i]; },
    get length() { return Object.keys(m).length; }
  };
}

(async function () {
  var SQL = await initSqlJs();
  var db = new SQL.Database();
  KO.ensureTable(db);
  var store = makeStorage();
  var T = 'c_order', ID = 7001;
  var ts = 1700000000000;
  function group(ops, gid) { return KO.commitGroup(db, ops, { gid: gid, baseTs: ts += 1000 }); }
  function opCount() { var r = db.exec('SELECT COUNT(*) FROM kernel_ops'); return r.length ? +r[0].values[0][0] : 0; }

  // ── Set the official baseline: CREATE c_order 7001, then SAVE GrandTotal 1000 (a committed update) so the
  //    official tip is well-defined. This is the "last saved" the form returns to by default.
  await group([{ op_type: 'CRUD_CREATE', params: { table: T, key: T,
    fields: { C_Order_ID: ID, DocumentNo: 'SO-7001', GrandTotal: 1000, DocStatus: 'DR' },
    stdDefaults: { actor: 'alice' } } }], 'g-create');
  await group([{ op_type: 'CRUD_UPDATE', params: { table: T, id: ID,
    changes: { GrandTotal: { old: 1000, 'new': 1000 } }, actor: 'alice' } }], 'g-base');
  var savedTip = CORE.tipValues(db, T, ID);
  var baseline = { GrandTotal: ci(savedTip, 'GrandTotal'), DocStatus: 'DR', DocumentNo: 'SO-7001' };
  console.log('\n── baseline saved tip: GrandTotal=' + ci(savedTip, 'GrandTotal') + ' (this is the default on reopen)');

  // §FALSIFIER (part 1): no typing yet → no buffer, no pip.
  console.log('\n§FALSIFIER — clean start: buffer + pip empty');
  verdict(CORE.draftGet(store, T, ID) === null, 'draftGet null before any typing', 'opt-in target absent');
  verdict(CORE.draftList(store).length === 0, 'pip list empty', 'no false pip');

  // ── User opens the form and TYPES GrandTotal 1000 -> 1500 but does NOT save, then LEAVES (closeForm/unload).
  var typed = { GrandTotal: '1500', DocStatus: 'DR', DocumentNo: 'SO-7001' };
  var opsBefore = opCount();
  var rec = CORE.draftPut(store, T, ID, typed, { baseline: baseline, tipSnapshot: baseline, ts: ts, actor: 'bob' });

  console.log('\n§DRAFT-PRIVATE — leave dirty: buffered, but ZERO ops and tip unchanged');
  verdict(!!rec && rec.cols.indexOf('GrandTotal') >= 0, 'draft buffered the changed col', 'cols=' + (rec ? rec.cols.join(',') : '(none)'));
  verdict(opCount() === opsBefore, 'NO op committed on leave', 'ops ' + opsBefore + '→' + opCount() + ' (no official dot)');
  var tipAfterLeave = CORE.tipValues(db, T, ID);
  verdict(String(ci(tipAfterLeave, 'GrandTotal')) === '1000', 'official tip UNCHANGED for other docs', 'tip GrandTotal=' + ci(tipAfterLeave, 'GrandTotal') + ' (1500 did not leak)');

  console.log('\n§DRAFT-DEFAULT — reopen lands on the SAVED tip, not the buffer');
  var defaultOnReopen = CORE.tipValues(db, T, ID);   // the form's openForm reads the committed record, not the draft
  verdict(String(ci(defaultOnReopen, 'GrandTotal')) === '1000', 'default baseline = saved official tip', 'GrandTotal=' + ci(defaultOnReopen, 'GrandTotal'));

  console.log('\n§DRAFT-OPTIN — the back-pip restore returns the unsaved typing');
  var restored = CORE.draftGet(store, T, ID);
  verdict(!!restored && String(ci(restored.vals, 'GrandTotal')) === '1500', 'opt-in restore yields the typed value', 'restored GrandTotal=' + (restored ? ci(restored.vals, 'GrandTotal') : '(none)'));

  console.log('\n§DRAFT-COMMIT — validated Save: ONE official op + buffer cleared');
  var beforeSave = opCount();
  await group([{ op_type: 'CRUD_UPDATE', params: { table: T, id: ID,
    changes: { GrandTotal: { old: 1000, 'new': 1500 } }, actor: 'bob' } }], 'g-save');
  CORE.draftClear(store, T, ID);   // Save folds draft -> official dot, then clears the private buffer
  verdict(opCount() === beforeSave + 1, 'exactly ONE official op committed on Save', 'ops ' + beforeSave + '→' + opCount());
  verdict(String(ci(CORE.tipValues(db, T, ID), 'GrandTotal')) === '1500', 'official tip now carries the saved value', 'tip GrandTotal=1500');
  verdict(CORE.draftGet(store, T, ID) === null, 'buffer cleared (pip gone)', 'draftGet null after Save');
  verdict(CORE.draftList(store).length === 0, 'pip list empty after commit');

  console.log('\n§DRAFT-CLEAN — leaving a CLEAN form buffers nothing (no false pip)');
  // type a stale draft, then re-open and leave WITHOUT changes (vals === current tip) -> stale pip cleared.
  CORE.draftPut(store, T, ID, { GrandTotal: '9999' }, { baseline: { GrandTotal: '1500' } });
  verdict(CORE.draftGet(store, T, ID) !== null, 'stale draft present before clean-leave');
  var cleanVals = { GrandTotal: '1500', DocStatus: 'DR', DocumentNo: 'SO-7001' };   // matches saved tip
  var cleanRec = CORE.draftPut(store, T, ID, cleanVals, { baseline: { GrandTotal: '1500', DocStatus: 'DR', DocumentNo: 'SO-7001' } });
  verdict(cleanRec === null, 'clean leave buffers nothing', 'draftPut returned null');
  verdict(CORE.draftGet(store, T, ID) === null, 'stale pip CLEARED on clean leave', 'no false pip');

  console.log('\n§DRAFT-DRIFT — record changed underneath -> flag, do not clobber');
  // buffer a draft snapshotted at GrandTotal=1500, then ANOTHER actor commits 1500->1700; the draft snapshot
  // is now stale. draftDrift must flag GrandTotal so the restore can warn.
  var driftDraft = CORE.draftPut(store, T, ID, { GrandTotal: '1600' }, { baseline: { GrandTotal: '1500' }, tipSnapshot: { GrandTotal: '1500' } });
  await group([{ op_type: 'CRUD_UPDATE', params: { table: T, id: ID,
    changes: { GrandTotal: { old: 1500, 'new': 1700 } }, actor: 'carol' } }], 'g-underneath');
  var curTip = CORE.tipValues(db, T, ID);
  var drift = CORE.draftDrift(driftDraft, curTip);
  verdict(drift.drifted && drift.cols.indexOf('GrandTotal') >= 0, 'drift detected on the moved col', 'cols=' + drift.cols.join(',') + ' (tip now ' + ci(curTip, 'GrandTotal') + ', snapshot 1500)');
  var noDrift = CORE.draftDrift({ tipSnapshot: { GrandTotal: '1700' }, cols: ['GrandTotal'] }, curTip);
  verdict(!noDrift.drifted, 'no drift when snapshot matches current tip', 'matched 1700');

  console.log('\n' + (fails === 0 ? '🟢 W-DRAFT-RESTORE PASS — private draft buffer: no official dot while typing, opt-in restore, one dot on Save, drift surfaced'
                                  : '🔴 W-DRAFT-RESTORE FAIL — ' + fails + ' check(s) failed'));
  process.exit(fails === 0 ? 0 : 1);
})();
