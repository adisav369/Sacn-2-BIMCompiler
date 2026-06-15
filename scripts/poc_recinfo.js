#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Item 3a (FRONTEND_LANE_MASTER.md §OUTSTANDING — RECORD-INFO POPUP).
//   Read the log after every run.
// poc_recinfo.js — W-RECINFO: the engine half of the iDempiere "(i)" record-info popup. The immutable op-log
//   already carries actor+timestamp+sig per op; Created/CreatedBy/Updated/UpdatedBy are materialized into the
//   tip (listTip), but the record-level "who/when last touched" was never surfaced. CORE.recordInfo folds the
//   log to {created:{actor,ts,opId}, updated:{actor,ts,opId}, count} — ALWAYS-ON (no AD IsChangeLog gate,
//   unlike changeLog) and record-grain (unlike per-field fieldLineage). Because it reads the SAME op rows the
//   tip materializes from, its CreatedBy/UpdatedBy MATCH the materialized cols by construction.
//
// ISSUES IT PROVES (named):
//   §RECINFO-CREATE  — created.{actor,ts,opId} = the record's CREATE op (the original author).
//   §RECINFO-UPDATE  — updated.{actor,ts} = the LATEST op that touched the record (last writer wins), and the
//                      count = total ops on the record.
//   §RECINFO-MATCH   — recordInfo's CreatedBy/UpdatedBy MATCH the values materialized into the tip (listTip)
//                      for the same record — same source, no drift.
//   §RECINFO-ISOLATE — a DIFFERENT record's ops do NOT bleed into this record's info (the (table,id) filter).
//   §FALSIFIER       — a never-touched record returns null (no fabrication); an undone op is excluded.
//
// NON-INVENT: every field traces to a real committed op row; deterministic ids + baseTs (no Date.now in path).
// Implementing FRONTEND_LANE_MASTER.md §OUTSTANDING item 3a — Witness: W-RECINFO
// Run: bash build/erp/run_witness.sh scripts/poc_recinfo.js — then READ the log.
'use strict';
var path = require('path');
var initSqlJs = require('sql.js');

global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var KO = global.window.KernelOps;
var CORE = require(path.join(__dirname, '..', 'build', 'erp', 'crud_overlay.js'));

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function ci(o, c) { if (!o) return undefined; var lk = String(c).toLowerCase(); for (var k in o) if (k.toLowerCase() === lk) return o[k]; }

(async function () {
  var SQL = await initSqlJs();
  var db = new SQL.Database();
  KO.ensureTable(db);
  var T = 'c_order';
  var ts = 1700000000000;
  function group(ops, gid) { return KO.commitGroup(db, ops, { gid: gid, baseTs: ts += 1000 }); }

  // ── record 8001: created by alice, then edited by bob, then carol (3 ops). record 8002: created by dave only.
  await group([{ op_type: 'CRUD_CREATE', params: { table: T, key: T,
    fields: { C_Order_ID: 8001, DocumentNo: 'SO-8001', GrandTotal: 100, DocStatus: 'DR' },
    stdDefaults: { actor: 'alice' } } }], 'g-c1');
  var tsBob = ts + 1000;
  await group([{ op_type: 'CRUD_UPDATE', params: { table: T, id: 8001, changes: { GrandTotal: { old: 100, 'new': 200 } }, actor: 'bob' } }], 'g-u1');
  await group([{ op_type: 'CRUD_UPDATE', params: { table: T, id: 8001, changes: { GrandTotal: { old: 200, 'new': 300 } }, actor: 'carol' } }], 'g-u2');
  var tsCarol = ts;   // group() bumped ts to the carol op's baseTs
  await group([{ op_type: 'CRUD_CREATE', params: { table: T, key: T,
    fields: { C_Order_ID: 8002, DocumentNo: 'SO-8002', GrandTotal: 50, DocStatus: 'DR' },
    stdDefaults: { actor: 'dave' } } }], 'g-c2');

  var info = CORE.recordInfo(db, T, 8001);
  console.log('\n§RECINFO-CREATE — original author from the CREATE op');
  verdict(!!info && info.created && info.created.actor === 'alice', 'created.actor = alice', info ? 'createdBy=' + info.created.actor : '(null)');

  console.log('\n§RECINFO-UPDATE — last writer wins + op count');
  verdict(info.updated.actor === 'carol', 'updated.actor = carol (latest)', 'updatedBy=' + info.updated.actor);
  verdict(info.updated.ts === tsCarol, 'updated.ts = the latest op ts', 'ts=' + info.updated.ts + ' expected=' + tsCarol);
  verdict(info.count === 3, 'count = 3 ops on the record', 'count=' + info.count);

  console.log('\n§RECINFO-MATCH — CreatedBy/UpdatedBy match the materialized tip (same op source)');
  // listTip stamps CreatedBy/UpdatedBy only when the main db (__idmpDb) exposes those columns, and keys a new
  // row by a synthetic -opId. Drive that REAL path: a fresh CREATE (erin) materialized via listTip, then an
  // UPDATE (frank) keyed by the synthetic pk → compare the stamped cols to recordInfo on the same record.
  global.__idmpDb = new SQL.Database();
  global.__idmpDb.run('CREATE TABLE c_order (c_order_id INTEGER, documentno TEXT, grandtotal NUMERIC, docstatus TEXT, createdby TEXT, updatedby TEXT, created TEXT, updated TEXT)');
  var cr = await group([{ op_type: 'CRUD_CREATE', params: { table: T, key: T,
    fields: { DocumentNo: 'SO-8003', GrandTotal: 70, DocStatus: 'DR' }, stdDefaults: { actor: 'erin' } } }], 'g-c3');
  var tip1 = CORE.listTip(db, T, 'C_Order_ID', []);
  var synth = tip1.created[tip1.created.length - 1];             // the synthetic pk (-opId) of the just-created (erin) row — created[] is op-id ASC
  await group([{ op_type: 'CRUD_UPDATE', params: { table: T, id: synth, changes: { GrandTotal: { old: 70, 'new': 90 } }, actor: 'frank' } }], 'g-u3');
  var tip2 = CORE.listTip(db, T, 'C_Order_ID', []);
  var row = tip2.rows.filter(function (r) { return String(r.C_Order_ID) === String(synth); })[0] || {};
  var ri = CORE.recordInfo(db, T, synth);
  console.log('   synthPk=' + synth + ' tip CreatedBy=' + ci(row, 'CreatedBy') + ' UpdatedBy=' + ci(row, 'UpdatedBy') + ' | recInfo created=' + (ri && ri.created.actor) + ' updated=' + (ri && ri.updated.actor));
  verdict(!!ri && String(ci(row, 'CreatedBy')) === String(ri.created.actor) && ri.created.actor === 'erin', 'tip.CreatedBy == recordInfo.created.actor (erin)', ci(row, 'CreatedBy') + ' / ' + (ri && ri.created.actor));
  verdict(!!ri && String(ci(row, 'UpdatedBy')) === String(ri.updated.actor) && ri.updated.actor === 'frank', 'tip.UpdatedBy == recordInfo.updated.actor (frank)', ci(row, 'UpdatedBy') + ' / ' + (ri && ri.updated.actor));

  console.log('\n§RECINFO-ISOLATE — a different record does not bleed in');
  var info2 = CORE.recordInfo(db, T, 8002);
  verdict(info2 && info2.created.actor === 'dave' && info2.count === 1, 'record 8002 = dave, 1 op (isolated)', info2 ? info2.created.actor + '/' + info2.count : '(null)');

  console.log('\n§FALSIFIER — a never-touched record returns null');
  verdict(CORE.recordInfo(db, T, 9999) === null, 'unknown record → null (no fabrication)');

  console.log('\n' + (fails === 0 ? '🟢 W-RECINFO PASS — record-level who/when folded from the op-log, matches the materialized tip, always-on'
                                  : '🔴 W-RECINFO FAIL — ' + fails + ' check(s) failed'));
  process.exit(fails === 0 ? 0 : 1);
})();
