#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * test_kernel_persist.js — A4 / W-PERSIST-LIVE: exercises bim-ootb/viewer/erp_persist.js (+ the real
 *   erp_signer.js for the signed-snapshot hook) to confirm the live durable-local + recovery behaviour.
 *   Spec: scripts/poc_persist.js + ERP.md §9-A/§5.2b. Mirrors poc_persist's verdicts against the
 *   ACTUAL module the page loads.
 *
 *   Proves:
 *     §PERSIST persisted   — navigator.storage.persist() path logs persisted=<granted> on load
 *     §PERSIST roundtrip   — export → wipe → import → projection-hash == pre-export hash (disposable container)
 *     §PERSIST snapshot     — emitSnapshot signs a full snapshot at the edge (the recovery hook)
 *
 * Run: node scripts/test_kernel_persist.js 2>&1 | tee build/erp/test_kernel_persist.log
 */
'use strict';
if (typeof global.crypto === 'undefined') { global.crypto = require('crypto').webcrypto; }
// shim navigator.storage.persist → granted, to witness the durable path (the browser decides at runtime)
global.navigator = { storage: { persist: function () { return Promise.resolve(true); } } };
global.window = {};

var path = require('path');
var nodeCrypto = require('crypto');
var initSqlJs = require('sql.js');
require(path.join(process.env.HOME, 'bim-ootb', 'viewer', 'erp_signer.js'));   // window.ErpSigner
require(path.join(process.env.HOME, 'bim-ootb', 'viewer', 'erp_persist.js'));  // window.ErpPersist
var Sig = global.window.ErpSigner;
var P = global.window.ErpPersist;

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function sha(s) { return nodeCrypto.createHash('sha256').update(s).digest('hex'); }
function projHash(db) { var r = db.exec('SELECT token,balance,claimed_by FROM wallet ORDER BY token'); return sha(JSON.stringify(r.length ? r[0].values : [])); }

(async function () {
  console.log('═══ TEST-KERNEL-PERSIST — durable-local + signed-snapshot hook (real erp_persist.js) ═══\n');
  var SQL = await initSqlJs();

  // ── #persisted: the durable-storage request path ──
  var granted = await P.requestPersist();
  verdict(granted === true, 'navigator.storage.persist() → persisted=true on load', 'granted=' + granted);

  // ── build a live wallet projection, then export → wipe → import ──
  var live = new SQL.Database();
  live.run('CREATE TABLE wallet (token TEXT PRIMARY KEY, balance REAL, claimed_by TEXT)');
  live.run("INSERT INTO wallet VALUES ('GIFT-50', 50, NULL)");
  live.run("UPDATE wallet SET balance=40 WHERE token='GIFT-50'");                       // spend 10
  live.run("UPDATE wallet SET balance=25, claimed_by='cust-7' WHERE token='GIFT-50'");  // spend 15 + claim
  var preHash = projHash(live);
  console.log('§PERSIST live balance=25 preHash=' + preHash.slice(0, 12));

  var restored = P.roundTrip(SQL, live);    // export → re-import via the real module
  live.close();                              // "evicted" — local copy gone
  var postHash = projHash(restored);
  console.log('§PERSIST export→wipe→import postHash=' + postHash.slice(0, 12));
  verdict(preHash === postHash, 'export→wipe→import round-trips → hash == pre-export hash (disposable container)', 'pre==post:' + (preHash === postHash));

  // ── #snapshot: the signed-snapshot recovery hook (edge-signed full state) ──
  var kp = await Sig.mintKeypair();
  var signer = Sig.makeSigner(kp);
  var snap = JSON.stringify(restored.exec('SELECT token,balance,claimed_by FROM wallet ORDER BY token')[0].values);
  var env = await P.emitSnapshot(snap, 1, signer);
  var sigOk = await signer.verify(env.hash, env.sig);
  console.log('§PERSIST snapshot seq=' + env.seq + ' hash=' + env.hash.slice(0, 12) + ' sigVerifies=' + sigOk);
  verdict(!!env.sig && sigOk, 'emitSnapshot edge-signs a full snapshot (the recovery hook; full flow in poc_persist)', 'sigVerifies=' + sigOk);

  console.log('\n§PERSIST ' + (fails ? 'FAIL — ' + fails + ' checks red'
    : 'PASS — storage made durable; the local container is disposable (export round-trips); the recovery hook edge-signs the snapshot'));
  process.exit(fails ? 1 : 0);
})();
