#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Item 0 headliner (FRONTEND_LANE_MASTER.md §OUTSTANDING — BLUE FUTURE).
//   Read the log after every run.
// poc_blue_future.js — W-BLUE-FUTURE: a speculative FORWARD branch of the whole transactional state. The past
//   stays immutable (read-only); the future is a discardable sandbox. Long-press the last dot → blue mode; ops
//   carry a branch_id and are INVISIBLE to official reads. Run the FULL CompleteIt fan-out in blue (SET_STATUS +
//   createShipment + createInvoice), drill into the blue children, then DISCARD (shirk the blues) or ACCEPT-UP-TO
//   (long-click a blue dot → those ops go white/permanent, later dots stay blue). This only falls out because
//   state IS a fold of an append-only log — not a bolt-on simulation.
//
// ISSUES IT PROVES (named):
//   §BLUE-INVISIBLE   — ops committed with a branch_id do NOT appear in the OFFICIAL projection (replayOps
//                       default = branch_id IS NULL); they DO appear in the branch projection. Other docs
//                       reading official state cannot see or act on blue docs.
//   §BLUE-COMPLETEIT  — the blue dot runs the FULL engine completeOrder fan-out: SET_STATUS(CO) + a M_InOut
//                       (createShipment) + a C_Invoice (createInvoice) with their lines — real children, blue.
//   §BLUE-ZOOM        — the blue children are drillable: given the order id we resolve the speculative M_InOut /
//                       C_Invoice CREATE_DOCUMENT ops from the branch (the Zoom target list).
//   §BLUE-DISCARD     — discardBranch folds the WHOLE branch away atomically; official projection is byte-for-
//                       byte what it was BEFORE blue (the order is still DR; no shipment/invoice exist).
//   §BLUE-ACCEPT-UPTO — long-click a blue dot: ops up to it turn official (branch_id NULL) and become visible to
//                       official reads; ops after it stay blue. verifyChain STILL OK (accept ∉ hashed fields).
//   §FALSIFIER        — before accept, the official order status is DR (blue CO is invisible); the chain verifies
//                       identically with blue ops present (branch tag is not hashed).
//
// NON-INVENT: order + lines are explicit rows; completeOrder is the REAL engine verb; deterministic ids/baseTs.
// Implementing FRONTEND_LANE_MASTER.md §OUTSTANDING item 0 — Witness: W-BLUE-FUTURE
// Run: bash build/erp/run_witness.sh scripts/poc_blue_future.js — then READ the log.
'use strict';
var path = require('path');
var initSqlJs = require('sql.js');

global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var KO = global.window.KernelOps;
var ENG = require(path.join(__dirname, '..', 'build', 'erp', 'erp_engine.js'));

var fails = 0;
function verdict(ok, label, detail) {
  if (!ok) fails++;
  console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : ''));
}

// fold the official docstatus of an order from the OFFICIAL projection (branch defaults to NULL).
function officialStatus(db, orderId, branch) {
  var ops = KO.replayOps(db, 'SET_STATUS', branch);
  var st = 'DR';
  ops.forEach(function (o) { if (o.parameters && o.parameters.c_order_id === orderId) st = o.parameters.doc_status; });
  return st;
}
// count CREATE_DOCUMENT ops of a given table in a projection (official vs branch).
function docCount(db, table, branch) {
  return KO.replayOps(db, 'CREATE_DOCUMENT', branch)
    .filter(function (o) { return o.parameters && String(o.parameters.table).toLowerCase() === table.toLowerCase(); }).length;
}

(async function () {
  var SQL = await initSqlJs();
  var db = new SQL.Database();
  KO.ensureTable(db);

  // ── Official baseline: order 7001 exists as DRAFT (a real CREATE op, official). ──
  var ts = 1_700_000_000_000;
  await KO.commitGroup(db, [{ op_type: 'CRUD_CREATE', params: { table: 'c_order', key: 'c_order',
    fields: { C_Order_ID: 7001, DocumentNo: 'SO-7001', DocStatus: 'DR', issotrx: 'Y' }, stdDefaults: { actor: 'alice' } } }],
    { gid: 'g-create-7001', baseTs: ts += 1000 });
  await KO.commitGroup(db, [{ op_type: 'SET_STATUS', params: { table: 'C_Order', c_order_id: 7001, doc_status: 'DR' } }],
    { gid: 'g-status-7001', baseTs: ts += 1000 });

  console.log('── W-BLUE-FUTURE ──');
  var officialOpsBefore = KO.replayOps(db, null).length;   // official projection size before blue
  var statusBefore = officialStatus(db, 7001);

  // ── Enter blue: run the FULL CompleteIt on order 7001 inside a private branch. ──
  var BRANCH = 'blue-7001-sandbox';
  var order = { c_order_id: 7001, issotrx: 'Y' };
  var lines = [{ c_orderline_id: 1, c_order_id: 7001, m_product_id: 555, qtyordered: 3 }];
  var policy = { isautogenerateinout: 'Y', isautogenerateinvoice: 'Y' };
  var fanout = ENG.completeOrder(order, lines, policy);   // REAL engine verb: [SET_STATUS, M_InOut+line, C_Invoice+line]
  // map engine ops → kernel ops; SET_STATUS carries the order id for the status fold.
  var blueOps = fanout.map(function (op) {
    if (op.op_type === 'SET_STATUS') return { op_type: 'SET_STATUS', params: { table: 'C_Order', c_order_id: 7001, doc_status: op.doc_status } };
    return { op_type: op.op_type, params: op };
  });
  var blueDot1 = await KO.commitGroup(db, blueOps, { gid: 'g-blue-complete-7001', baseTs: ts += 1000, branch_id: BRANCH });
  // a SECOND blue dot (a follow-up speculative edit) — to prove accept-up-to leaves later dots blue.
  var blueDot2 = await KO.commitGroup(db, [{ op_type: 'CRUD_UPDATE', params: { table: 'c_order', id: 7001,
    changes: { Description: { old: '', 'new': 'rush ship' } }, actor: 'alice' } }],
    { gid: 'g-blue-note-7001', baseTs: ts += 1000, branch_id: BRANCH });

  // §BLUE-COMPLETEIT — the fan-out really produced a shipment + invoice (in the branch projection).
  var blueInOut = docCount(db, 'M_InOut', BRANCH), blueInv = docCount(db, 'C_Invoice', BRANCH);
  verdict(blueInOut === 1 && blueInv === 1, '§BLUE-COMPLETEIT full fan-out in blue (M_InOut + C_Invoice created)',
          'M_InOut=' + blueInOut + ' C_Invoice=' + blueInv);

  // §BLUE-INVISIBLE — official projection unchanged; blue CO + children invisible to official reads.
  var officialAfter = KO.replayOps(db, null).length;
  var officialInOut = docCount(db, 'M_InOut', undefined), officialInv = docCount(db, 'C_Invoice', undefined);
  verdict(officialAfter === officialOpsBefore && officialInOut === 0 && officialInv === 0,
          '§BLUE-INVISIBLE official projection sees NONE of the blue ops',
          'officialOps before=' + officialOpsBefore + ' after=' + officialAfter + ' offInOut=' + officialInOut + ' offInv=' + officialInv);
  verdict(officialStatus(db, 7001) === 'DR' && officialStatus(db, 7001, BRANCH) === 'CO',
          '§FALSIFIER official=DR (blue CO invisible) but branch=CO',
          'official=' + officialStatus(db, 7001) + ' branch=' + officialStatus(db, 7001, BRANCH));

  // §BLUE-ZOOM — drill from the order to its speculative children (Zoom target list, from the branch).
  var zoomTargets = KO.replayOps(db, 'CREATE_DOCUMENT', BRANCH)
    .filter(function (o) { return o.parameters && Number(o.parameters.source_id) === 7001; })
    .map(function (o) { return o.parameters.table; });
  verdict(zoomTargets.length === 2 && zoomTargets.indexOf('M_InOut') >= 0 && zoomTargets.indexOf('C_Invoice') >= 0,
          '§BLUE-ZOOM drill-through resolves blue children', 'targets=' + JSON.stringify(zoomTargets));

  // chain verifies WITH blue ops present (branch tag is not hashed).
  var chainBlue = await KO.verifyChain(db);
  verdict(chainBlue.ok, '§FALSIFIER chain verifies with blue ops present (tag ∉ hash)', 'len=' + chainBlue.len);

  // ── ACCEPT-UP-TO: long-click the FIRST blue dot (the CompleteIt group) → it goes white; the 2nd stays blue. ──
  var firstBlueLastId = Math.max.apply(null, KO.branchOps(db, BRANCH).filter(function (o) { return o.gid === 'g-blue-complete-7001'; }).map(function (o) { return o.id; }));
  var acc = await KO.acceptBranchUpTo(db, BRANCH, firstBlueLastId);
  var officialInOut2 = docCount(db, 'M_InOut', undefined), officialInv2 = docCount(db, 'C_Invoice', undefined);
  verdict(officialStatus(db, 7001) === 'CO' && officialInOut2 === 1 && officialInv2 === 1 && acc.chain.ok,
          '§BLUE-ACCEPT-UPTO accepted CompleteIt → official CO + children real; chain still OK',
          'official=' + officialStatus(db, 7001) + ' offInOut=' + officialInOut2 + ' offInv=' + officialInv2 + ' chainOk=' + acc.chain.ok);
  verdict(acc.remaining === 1, '§BLUE-ACCEPT-UPTO later dot stays blue (accept-up-to-here, not accept-all)',
          'remainingBlue=' + acc.remaining);

  // ── DISCARD: shirk the remaining blue (the note) → official unchanged, blue note gone. ──
  var dis = KO.discardBranch(db, BRANCH);
  var noteOfficial = KO.replayOps(db, 'CRUD_UPDATE', undefined).filter(function (o) { return o.parameters && o.parameters.changes && o.parameters.changes.Description; }).length;
  verdict(dis.discarded === 1 && noteOfficial === 0 && KO.branchOps(db, BRANCH).length === 0,
          '§BLUE-DISCARD shirk remaining blue → folded away, never official',
          'discarded=' + dis.discarded + ' noteInOfficial=' + noteOfficial + ' branchOpsLeft=' + KO.branchOps(db, BRANCH).length);

  var chainEnd = await KO.verifyChain(db);
  verdict(chainEnd.ok, '§FALSIFIER chain verifies after accept+discard', 'len=' + chainEnd.len);

  console.log((fails === 0 ? '✅ W-BLUE-FUTURE ALL PASS' : '🔴 W-BLUE-FUTURE ' + fails + ' FAIL') +
              ' — speculative forward branch: invisible→CompleteIt→Zoom→accept-up-to / discard, all on the log');
  process.exit(fails === 0 ? 0 : 1);
})();
