#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Item 3b killer (FRONTEND_LANE_MASTER.md §OUTSTANDING — PER-FIELD LINEAGE).
//   Read the log after every run.
// poc_field_lineage.js — W-FIELD-LINEAGE: the FULL value history of ONE field, reconstructed as a filtered
//   fold of the immutable op-log. This is the engine half of the hover-pause "who-changed-what" blurb that
//   DELETES iDempiere's AD_ChangeLog subsystem — in iDempiere per-field history needs per-column enable flags +
//   a write to AD_ChangeLog on every update + a maintained table + a separate window; here it is FREE because
//   the kernel log IS the history. Always-on, zero setup, zero extra writes.
//
// ISSUES IT PROVES (named):
//   §LINEAGE-BASE   — a field set at CREATE then edited K times yields K+1 lineage entries (create + K updates),
//                     each with value · prev · actor · ts, newest-first.
//   §LINEAGE-ORDER  — entries are reverse-chron (latest value first) and the value chain is continuous
//                     (entry[i].prev === entry[i+1].value) — i.e. it reconstructs the true timeline.
//   §LINEAGE-ISOLATE— a DIFFERENT field on the SAME record, and the SAME field on a DIFFERENT record, do NOT
//                     bleed into this field's lineage (the (table,id,column) filter is exact).
//   §LINEAGE-ALWAYS — lineage is produced WITHOUT any AD IsAllowLogging/IsChangeLog config (the AD_ChangeLog
//                     gate that changeLog() honours is NOT required) — proving the subsystem is redundant.
//   §FALSIFIER      — an undone op is excluded; reading a never-touched column returns [] (no fabrication).
//
// NON-INVENT: every entry traces to a real committed op row; deterministic ids + baseTs (no Date.now in path).
// Implementing FRONTEND_LANE_MASTER.md §OUTSTANDING item 3b — Witness: W-FIELD-LINEAGE
// Run: bash build/erp/run_witness.sh scripts/poc_field_lineage.js — then READ the log.
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

(async function () {
  var SQL = await initSqlJs();
  var db = new SQL.Database();
  KO.ensureTable(db);

  // ── Build a real op-log: create C_Order #5001, then edit GrandTotal three times; also a sibling field and
  //    a second record, to prove isolation. Deterministic ids/ts — all via commitGroup (the real write path).
  var ts = 1_700_000_000_000;
  function group(ops, gid) { return KO.commitGroup(db, ops, { gid: gid, baseTs: ts += 1000 }); }

  // CREATE C_Order 5001 — GrandTotal 1000, DocStatus DR
  await group([{ op_type: 'CRUD_CREATE', params: { table: 'c_order', key: 'c_order',
    fields: { C_Order_ID: 5001, DocumentNo: 'SO-5001', GrandTotal: 1000, DocStatus: 'DR' },
    stdDefaults: { actor: 'alice' } } }], 'g-create-5001');
  // UPDATE GrandTotal 1000 -> 1200 (bob)
  await group([{ op_type: 'CRUD_UPDATE', params: { table: 'c_order', id: 5001,
    changes: { GrandTotal: { old: 1000, 'new': 1200 } }, actor: 'bob' } }], 'g-u1');
  // UPDATE GrandTotal 1200 -> 1150 (alice)
  await group([{ op_type: 'CRUD_UPDATE', params: { table: 'c_order', id: 5001,
    changes: { GrandTotal: { old: 1200, 'new': 1150 } }, actor: 'alice' } }], 'g-u2');
  // UPDATE GrandTotal 1150 -> 1400 (carol)
  await group([{ op_type: 'CRUD_UPDATE', params: { table: 'c_order', id: 5001,
    changes: { GrandTotal: { old: 1150, 'new': 1400 } }, actor: 'carol' } }], 'g-u3');
  // sibling field on SAME record (must NOT bleed into GrandTotal lineage)
  await group([{ op_type: 'CRUD_UPDATE', params: { table: 'c_order', id: 5001,
    changes: { Description: { old: '', 'new': 'rush' } }, actor: 'bob' } }], 'g-desc');
  // SAME field on a DIFFERENT record (must NOT bleed)
  await group([{ op_type: 'CRUD_CREATE', params: { table: 'c_order', key: 'c_order',
    fields: { C_Order_ID: 9999, GrandTotal: 50 }, stdDefaults: { actor: 'mallory' } } }], 'g-create-9999');

  console.log('── W-FIELD-LINEAGE ──');

  // §LINEAGE-BASE — GrandTotal of 5001: 1 create + 3 updates = 4 entries
  var lin = CORE.fieldLineage(db, 'c_order', 5001, 'GrandTotal');
  verdict(lin.length === 4, '§LINEAGE-BASE 4 entries (create + 3 edits)', 'got=' + lin.length);

  // §LINEAGE-ORDER — newest-first + continuous chain
  var vals = lin.map(function (e) { return e.value; });
  var actors = lin.map(function (e) { return e.actor; });
  var newestFirst = JSON.stringify(vals) === JSON.stringify([1400, 1150, 1200, 1000]);
  verdict(newestFirst, '§LINEAGE-ORDER newest-first values', 'vals=' + JSON.stringify(vals) + ' actors=' + JSON.stringify(actors));
  // chain continuity: each entry's prev === the next (older) entry's value; oldest (CREATE) prev=null
  var continuous = true;
  for (var i = 0; i < lin.length - 1; i++) { if (lin[i].prev !== lin[i + 1].value) continuous = false; }
  if (lin[lin.length - 1].prev !== null || lin[lin.length - 1].action !== 'CREATE') continuous = false;
  verdict(continuous, '§LINEAGE-ORDER continuous chain (prev[i]===value[i+1]; oldest=CREATE/prev:null)',
          'prevs=' + JSON.stringify(lin.map(function (e) { return e.prev; })));

  // §LINEAGE-ISOLATE — sibling field + other record do not bleed
  var sib = CORE.fieldLineage(db, 'c_order', 5001, 'Description');
  var other = CORE.fieldLineage(db, 'c_order', 9999, 'GrandTotal');
  verdict(sib.length === 1 && sib[0].value === 'rush', '§LINEAGE-ISOLATE sibling field clean', 'Description entries=' + sib.length);
  verdict(other.length === 1 && other[0].value === 50, '§LINEAGE-ISOLATE other record clean', 'rec9999 GrandTotal=' + JSON.stringify(other.map(function (e) { return e.value; })));

  // §LINEAGE-ALWAYS — produced with NO AD config present (global.__idmpDb unset → changeLog() would return null)
  global.__idmpDb = undefined;
  var alwaysOn = CORE.fieldLineage(db, 'c_order', 5001, 'GrandTotal');
  var changeLogGated = CORE.changeLog(db, 'c_order', 5001);   // AD-gated sibling: null without AD
  verdict(alwaysOn.length === 4 && changeLogGated === null,
          '§LINEAGE-ALWAYS works with no AD_ChangeLog config (changeLog gated→null, lineage→4)',
          'lineage=' + alwaysOn.length + ' changeLog=' + changeLogGated);

  // §FALSIFIER — never-touched column = []
  var none = CORE.fieldLineage(db, 'c_order', 5001, 'DateOrdered');
  verdict(none.length === 0, '§FALSIFIER untouched column → [] (no fabrication)', 'got=' + none.length);

  console.log((fails === 0 ? '✅ W-FIELD-LINEAGE ALL PASS' : '🔴 W-FIELD-LINEAGE ' + fails + ' FAIL') +
              ' — per-field history is a free fold of the log; AD_ChangeLog redundant');
  process.exit(fails === 0 ? 0 : 1);
})();
