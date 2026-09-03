#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// gen_replica_snapshot.js — build build/erp/relay_snapshot.json: a canonical, sealed op-log snapshot
// for the 3-host replica mockup (ERP.md §0.20 / W-PERSIST "cloud-of-truth, host is disposable").
// The SAME file is published to GH + OCI + here; any client replays it to an identical chain tip.
// Run: node scripts/gen_replica_snapshot.js
'use strict';
if (typeof global.crypto === 'undefined') { global.crypto = require('crypto').webcrypto; }
global.window = { APP: {} }; global.APP = global.window.APP;
global.indexedDB = { open: function () { var r = {}; setTimeout(function () { r.result = { createObjectStore: function () {}, transaction: function () { return { objectStore: function () { return { put: function () {} }; } }; } }; if (r.onsuccess) r.onsuccess(); }, 0); return r; } };
var path = require('path'), fs = require('fs');
var initSqlJs = require('sql.js');
require(path.join(process.env.HOME, 'bim-ootb', 'viewer', 'kernel_ops.js'));
var K = global.window.KernelOps;
var SIGN = require(path.join(__dirname, '..', 'build', 'erp', 'erp_snapshot_sign.js'));
var OUT = path.join(__dirname, '..', 'build', 'erp', 'relay_snapshot.json');
var KEYF = path.join(__dirname, '..', 'build', 'erp', '.demo_controller_key.json');

(async function () {
  var SQL = await initSqlJs();
  var db = new SQL.Database();
  // a small ERP lifecycle: create order → complete → invoice → allocate → pay (deterministic fixtures)
  var ops = [
    ['CREATE',   { actor: 'A', target: 'ORD-1001', doc_type: 'C_Order',   amount: 1200 }],
    ['COMPLETE', { actor: 'A', target: 'ORD-1001' }],
    ['CREATE',   { actor: 'A', target: 'INV-1001', doc_type: 'C_Invoice', amount: 1200 }],
    ['ALLOCATE', { actor: 'A', target: 'INV-1001' }],
    ['PAY',      { actor: 'A', target: 'INV-1001', amount: 1200 }]
  ];
  ops.forEach(function (o) { K.commitOp(db, o[0], o[1]); });
  await K.sealChain(db);
  var v = await K.verifyChain(db);
  var rows = db.exec('SELECT seq_id, op_uuid, timestamp, op_type, parameters, input_guids, output_guid FROM (SELECT id AS seq_id, op_uuid, timestamp, op_type, parameters, input_guids, output_guid FROM kernel_ops ORDER BY id)')[0].values
    .map(function (r) { return { seq: r[0], op_uuid: r[1], timestamp: r[2], op_type: r[3], parameters: r[4], input_guids: r[5], output_guid: r[6] }; });
  // sign the chain head with the controller key (the HolyGrail signed-checkpoint primitive)
  var priv = JSON.parse(fs.readFileSync(KEYF, 'utf8'));
  var sig = await SIGN.signTip(priv, v.tip);
  var snap = { schema: 'erp-replica/v1', len: v.len, tip: v.tip, sig: sig, alg: 'ES256', signed_by: 'controller', ops: rows };
  fs.writeFileSync(OUT, JSON.stringify(snap, null, 2));
  var sigOk = await SIGN.verifyTip(v.tip, sig);   // self-check under the pinned key
  console.log('§SNAPSHOT wrote ' + OUT + ' len=' + v.len + ' tip=' + (v.tip || '').slice(0, 16) + '… signed=' + sigOk);
})().catch(function (e) { console.error('FATAL', e); process.exit(1); });
