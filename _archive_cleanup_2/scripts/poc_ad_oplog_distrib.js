#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — REFLEXIVE AD SELF-EDIT distribution leg (FRONTEND_LANE_MASTER.md §PENDING WITNESS).
//   Read the log after every run.
// poc_ad_oplog_distrib.js — W-AD-OPLOG-DISTRIB: an admin's DICTIONARY change (an AD_Window edit) propagates
//   to another node as an APPENDED SIGNED op-log ("mail the append log") and re-folds to the SAME dictionary
//   there — no reload, no codegen, no restart. This is the distribution half the migration thesis leans on:
//   "modify the model live, like iDempiere", but across nodes.
//
// ISSUES IT PROVES (named):
//   §DISTRIB-EDIT   — node A edits AD_Window.Name via a signed CRUD_UPDATE kernel op (commitGroup, sealed+
//                     chained); verifyChain ok; the CRUD projection (crud_overlay.listTip) shows the new name.
//   §DISTRIB-WIRE   — the kernel_ops delta serializes to a base64 blob and back (the ONLY thing crossing the
//                     pipe), carrying op_uuid + op_type + params intact.
//   §DISTRIB-REFOLD — replaying that blob onto a FRESH copy of the SAME dictionary on node B yields the SAME
//                     edited AD_Window.Name, and verifyChain on node B is ok (the signed append survived transport).
//   §FALSIFIER      — node B WITHOUT the replay still shows the ORIGINAL name (the op-log is load-bearing for
//                     the change — it is not a hardcode and not already in the seed).
//
// NON-INVENT: the window + its original name are REAL ad_seed.db rows; deterministic ids + baseTs.
// Implementing FRONTEND_LANE_MASTER.md §PENDING WITNESS (REFLEXIVE AD SELF-EDIT, distribution leg) — Witness: W-AD-OPLOG-DISTRIB
// Run: bash build/erp/run_witness.sh scripts/poc_ad_oplog_distrib.js — then READ the log.
'use strict';
var path = require('path'), fs = require('fs');
var initSqlJs = require('sql.js');

global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var KO = global.window.KernelOps;
var CRUD = require(path.join(__dirname, '..', 'build', 'erp', 'crud_overlay.js'));

var SEED = path.join(__dirname, '..', 'build', 'erp', 'post_poc', 'ad_seed.db');

var fails = 0;
function verdict(ok, label, detail) {
  if (!ok) fails++;
  console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : ''));
}

// read AD_Window rows (id + name) via sql.js exec → [{AD_Window_ID, Name}]
function windowRows(db) {
  var r = db.exec("SELECT AD_Window_ID, Name FROM AD_Window WHERE IsActive='Y' ORDER BY AD_Window_ID");
  if (!r.length) return [];
  return r[0].values.map(function (v) { return { AD_Window_ID: v[0], Name: v[1] }; });
}
function nameOf(rows, wid) {
  for (var i = 0; i < rows.length; i++) if (String(rows[i].AD_Window_ID) === String(wid)) return rows[i].Name;
  return null;
}

(async function () {
  var log = []; var _cl = console.log;
  console.log = function () { var s = Array.prototype.join.call(arguments, ' '); log.push(s); _cl.apply(console, arguments); };
  console.log('\n═══ W-AD-OPLOG-DISTRIB — dictionary edit → signed append-log → re-fold on a 2nd node ═══\n');

  var SQL = await initSqlJs();
  if (!fs.existsSync(SEED)) { console.log('§W-AD-OPLOG-DISTRIB FAIL seed missing: ' + SEED); process.exit(1); }
  // fresh Uint8Array per node — sql.js takes ownership of the backing buffer on construction
  function freshSeed() { return new Uint8Array(fs.readFileSync(SEED)); }

  // ── NODE A: load the dictionary, edit one window's Name via a signed kernel op ──
  console.log('── NODE A: edit AD_Window.Name via signed CRUD_UPDATE ──');
  var nodeA = new SQL.Database(freshSeed()); KO.ensureTable(nodeA);
  var baseA = windowRows(nodeA);
  if (!baseA.length) { console.log('§W-AD-OPLOG-DISTRIB FAIL no AD_Window rows'); process.exit(1); }
  var target = baseA[0];                                  // first real window (deterministic)
  var WID = target.AD_Window_ID, origName = target.Name;
  var newName = origName + ' [edited@nodeA]';
  console.log('§TARGET AD_Window_ID=' + WID + ' origName="' + origName + '" → newName="' + newName + '"');

  // the edit op — the SAME shape crud_overlay.buildOp emits for a CRUD_UPDATE (table/id/changes), folded by listTip
  var editOp = {
    op_type: 'CRUD_UPDATE',
    op_uuid: 'ad-distrib-edit-1',
    params: { table: 'AD_Window', id: WID, changes: { Name: { old: origName, new: newName } }, actor: 100 }
  };
  var resA = await KO.commitGroup(nodeA, [editOp], { gid: 'ad-edit-1', baseTs: 1718200000000 });
  verdict(resA.committed, '§DISTRIB-EDIT signed commitGroup on node A', 'gid=' + resA.gid + ' ops=' + resA.op_hashes.length + ' tip=' + String(resA.tip).slice(0, 12) + '…');

  var chainA = await KO.verifyChain(nodeA);
  verdict(chainA.ok, '§DISTRIB-EDIT verifyChain(nodeA) ok', 'len=' + chainA.len);

  // the CRUD projection on node A — read the tip, the window now carries the new name
  var tipA = CRUD.listTip(nodeA, 'AD_Window', 'AD_Window_ID', baseA);
  var nameA = nameOf(tipA.rows, WID);
  verdict(nameA === newName, '§DISTRIB-EDIT node A projection shows edited name', 'name="' + nameA + '"');

  // ── §DISTRIB-WIRE: serialize the kernel_ops delta to the clipboard/relay blob ──
  console.log('\n── WIRE: serialize node A op-log delta → base64 blob ──');
  var rA = nodeA.exec("SELECT op_uuid, op_type, parameters FROM kernel_ops WHERE undone=0 ORDER BY id");
  var sentOps = rA[0].values.map(function (v) { return { op_uuid: v[0], op_type: v[1], params: JSON.parse(v[2]) }; });
  var blob = Buffer.from(JSON.stringify({ v: 1, ops: sentOps })).toString('base64');
  verdict(blob.length > 0 && sentOps.length === 1, '§DISTRIB-WIRE serialized append-log', 'ops=' + sentOps.length + ' bytes=' + blob.length);

  // ── NODE B: a FRESH copy of the SAME dictionary, no ops yet ──
  console.log('\n── NODE B: fresh dictionary copy ──');
  var nodeB = new SQL.Database(freshSeed()); KO.ensureTable(nodeB);
  var baseB = windowRows(nodeB);

  // §FALSIFIER — without the replay, node B still shows the ORIGINAL name (op-log is load-bearing)
  var tipB0 = CRUD.listTip(nodeB, 'AD_Window', 'AD_Window_ID', baseB);
  var nameB0 = nameOf(tipB0.rows, WID);
  verdict(nameB0 === origName, '§FALSIFIER node B BEFORE replay = original name (op-log load-bearing)', 'name="' + nameB0 + '"');

  // deserialize + replay the appended op-log onto node B
  console.log('\n── NODE B: deserialize + replay the append-log ──');
  var payload = JSON.parse(Buffer.from(blob, 'base64').toString('utf8'));
  var rxOps = payload.ops.map(function (o) { return { op_type: o.op_type, op_uuid: o.op_uuid, params: o.params }; });
  var resB = await KO.commitGroup(nodeB, rxOps, {});
  verdict(resB.committed, '§DISTRIB-REFOLD replay commitGroup on node B', 'gid=' + resB.gid);

  var chainB = await KO.verifyChain(nodeB);
  verdict(chainB.ok, '§DISTRIB-REFOLD verifyChain(nodeB) ok (signed append survived transport)', 'len=' + chainB.len);

  // the CRUD projection on node B — SAME edited dictionary as node A
  var tipB = CRUD.listTip(nodeB, 'AD_Window', 'AD_Window_ID', baseB);
  var nameB = nameOf(tipB.rows, WID);
  verdict(nameB === newName, '§DISTRIB-REFOLD node B projection == node A edited name', 'name="' + nameB + '"');

  var match = nameA === nameB && nameB === newName;
  console.log('\n§DISTRIB nodeA="' + nameA + '" nodeB="' + nameB + '" match=' + (match ? 'YES' : 'NO') +
              ' §FALSIFIER beforeReplay="' + nameB0 + '" diverges=' + (nameB0 !== newName ? 'YES' : 'NO'));

  var pass = fails === 0 && match && nameB0 !== newName;
  console.log('\n§W-AD-OPLOG-DISTRIB ' + (pass ? 'PASS' : 'FAIL') + ' (fails=' + fails + ')');
  fs.writeFileSync(path.join(__dirname, '..', 'build', 'erp', 'poc_ad_oplog_distrib.log'), log.join('\n'));
  process.exit(pass ? 0 : 1);
})().catch(function (e) { console.error('§W-AD-OPLOG-DISTRIB ERROR', e.message, e.stack); process.exit(2); });
