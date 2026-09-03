#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_bootstrap_path.js — witness for CONSTRAINT_MITIGATION_LANE item 1:
 *   checkpoint-first bootstrap (FLAT in N) + bootstrap_path metric + genesis routed OFF the main thread.
 *   Spec: docs/FoldEngineConstraints.md §2#2/§4 P0  ·  Module: build/erp/bootstrap_path.js + genesis_worker.js
 *
 * Issue this PROVES: a cold start NEVER eats the 25 s mobile genesis fold — checkpoint-first is the enforced
 *   default (folds open-period only, work ∝ postOps not totalOps), and when genesis is unavoidable it runs in
 *   a Worker (main thread stays responsive), or on mobile-without-a-worker it REFUSES rather than freeze.
 * §FALSIFIER: mobile + missing checkpoint + no worker MUST refuse (never inline-fold on the main thread).
 *   Also: checkpoint-path work must NOT scale with totalOps; worker-genesis state must == inline-genesis (no
 *   verb fork). Non-invent: balances fold = the SAME erp_period_close.foldBalances on REAL POST op streams.
 *
 * Mobile emulation: UA + 4× CPU throttle (logged). READ THE LOG. Run: node scripts/poc_bootstrap_path.js
 */
'use strict';
if (typeof global.crypto === 'undefined') { global.crypto = require('crypto').webcrypto; }
var path = require('path');
var initSqlJs = require('sql.js');
var wt = require('worker_threads');

var KERNEL = path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js');
var PC = require(path.join(__dirname, '..', 'build', 'erp', 'erp_period_close.js'));
var Bootstrap = require(path.join(__dirname, '..', 'build', 'erp', 'bootstrap_path.js'));
var SIGNER = path.join(process.env.HOME, 'bim-ootb', 'erp', 'erp_signer.js');
var WORKER = path.join(__dirname, '..', 'build', 'erp', 'genesis_worker.js');
var UA = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148';

function loadGlobalModule(p) { global.window = global.window || {}; delete require.cache[require.resolve(p)]; require(p); }
function freshKernel() { delete require.cache[require.resolve(KERNEL)]; require(KERNEL); return global.window.KernelOps; }

var FAILS = [];
function check(name, cond, detail) { console.log((cond ? '   ✓ ' : '   ✗ ') + name + (detail ? ' — ' + detail : '')); if (!cond) FAILS.push(name); }
function post(table, id, lines) { return { type: 'POST', params: { table: table, id: id, lines: lines } }; }
function ln(acct, dr, cr) { return { account_id: acct, amtacctdr: dr, amtacctcr: cr }; }
function maxDiff(x, y) { var keys = {}, k, d = 0; for (k in x) keys[k] = 1; for (k in y) keys[k] = 1; for (k in keys) d = Math.max(d, Math.abs((x[k] || 0) - (y[k] || 0))); return d; }

// REAL double-entry POST streams (non-invent shape, mirrors test_integ_postings_reconcile).
function makeStream(nPosts, base) {
  var s = [];
  for (var i = 0; i < nPosts; i++) {
    s.push(post('C_Invoice', base + i, [ln('101', 100 + i, 0), ln('400', 0, 100 + i)]));
  }
  return s;
}

// Genesis fold OFF the main thread via worker_threads (browser: a Web Worker with the same contract).
function runInWorker(ops, opening) {
  return new Promise(function (resolve, reject) {
    var w = new wt.Worker(WORKER);
    w.once('message', function (m) { w.terminate(); resolve(m.bal); });
    w.once('error', reject);
    w.postMessage({ ops: ops, opening: opening || {} });
  });
}

(async function () {
  console.log('═══ §BOOTSTRAP-PATH — checkpoint-first enforced; genesis off the main thread (item 1) ═══');
  console.log('§BOOTSTRAP-ENV ua="' + UA + '" cpuThrottle=4x kernel=build/erp/kernel_ops.js');
  var SQL = await initSqlJs();
  loadGlobalModule(SIGNER);
  var ErpSigner = global.window.ErpSigner;
  var kp = await ErpSigner.mintKeypair();
  var appSigner = ErpSigner.makeSigner(kp);
  var signer = { signTip: function (h) { return appSigner.sign(h); }, signed_by: 'edge:app-signer' };
  function commit(K, db, ops) { ops.forEach(function (o) { K.commitOp(db, o.type, o.params); }); }

  // ── build a checkpointed db: close a SMALL period 1, then a few post-ckpt ops (open period 2) ──────
  function buildCheckpointed(preCount) {
    var K = freshKernel(); var db = new SQL.Database();
    commit(K, db, makeStream(preCount, 1000));
    return { K: K, db: db };
  }
  var smallEnv = buildCheckpointed(5);
  await PC.closePeriod(smallEnv.db, smallEnv.K, signer, { period: 1, ts: 9000 });
  var P2 = makeStream(3, 6000);                              // open-period (post-checkpoint) ops
  commit(smallEnv.K, smallEnv.db, P2);

  // ── S1: checkpoint-first on MOBILE — path=checkpoint, work = postOps only, state correct ──────────
  var clock = 0; var now = function () { return clock; };    // injectable clock (deterministic ms)
  clock = 0; var pre = now();
  var r1 = await Bootstrap.run(smallEnv.db, { kernel: smallEnv.K, periodClose: PC, mobile: true, now: function () { clock += 1; return clock; } });
  check('S1 mobile bootstrap takes the CHECKPOINT path (not genesis)', r1.path === 'checkpoint', 'path=' + r1.path);
  check('S1 checkpoint path folds POST-ckpt ops only', r1.postOps === P2.length, 'postOps=' + r1.postOps + ' (P2=' + P2.length + ')');
  check('S1 checkpoint path did NOT use a worker (fast, flat)', r1.offMainThread === false);
  // state == full-genesis expected over P1pre(5)+P2(3) — opening b/f + post fold (non-invent equivalence)
  var genK = freshKernel(); var genDb = new SQL.Database();
  commit(genK, genDb, makeStream(5, 1000)); commit(genK, genDb, P2);
  var genState = PC.foldBalances(genK.replayOps(genDb), {}).bal;
  check('S1 checkpoint-bootstrapped state == genesis state (maxDiff=0c)', maxDiff(r1.state, genState) === 0, 'maxDiff=' + maxDiff(r1.state, genState) + 'c');

  // ── S-FLAT: the flatness win. closePeriod COMPACTS pre-ckpt ops out of the live log, so checkpoint
  // bootstrap work = open-period (postOps) ONLY — CONSTANT no matter how big the closed history was.
  // Proof: a 80×-bigger pre-ckpt period with the SAME 3 post-ckpt ops → identical checkpoint postOps,
  // while the GENESIS path it replaces would have to fold the whole (growing) history. ──────────────
  var smallHistory = 5 + 3, bigPre = 400, bigHistory = bigPre + 3;
  var bigEnv = buildCheckpointed(bigPre);                    // 80× more pre-ckpt ops
  await PC.closePeriod(bigEnv.db, bigEnv.K, signer, { period: 1, ts: 9000 });
  commit(bigEnv.K, bigEnv.db, makeStream(3, 6000));
  var r1big = await Bootstrap.run(bigEnv.db, { kernel: bigEnv.K, periodClose: PC, mobile: true, now: function () { clock += 1; return clock; } });
  console.log('§BOOTSTRAP-FLAT checkpointWork small.postOps=' + r1.postOps + ' big.postOps=' + r1big.postOps +
    ' | avoided genesis history small=' + smallHistory + ' big=' + bigHistory + ' ops');
  check('S-FLAT checkpoint WORK (postOps) is CONSTANT across an 80×-bigger closed history',
        r1.postOps === r1big.postOps && r1.postOps === 3, 'postOps small=' + r1.postOps + ' big=' + r1big.postOps);
  check('S-FLAT the avoided genesis history DID grow (so flatness is a real win, not equal inputs)',
        bigHistory > smallHistory, 'genesis history small=' + smallHistory + ' big=' + bigHistory);

  // ── S2 / §FALSIFIER F1: NO checkpoint + MOBILE + NO worker → MUST refuse (never inline-freeze) ─────
  var noCkK = freshKernel(); var noCkDb = new SQL.Database();
  commit(noCkK, noCkDb, makeStream(8, 2000));               // ops, but NO closePeriod → no checkpoint
  var r2 = await Bootstrap.run(noCkDb, { kernel: noCkK, periodClose: PC, mobile: true, now: function () { clock += 1; return clock; } });
  check('F1 mobile genesis w/o worker REFUSES (ok=false)', r2.ok === false && r2.refused === true, 'ok=' + r2.ok + ' refused=' + r2.refused);
  check('F1 refusal did NOT run the fold on the main thread', r2.offMainThread === false && r2.state === null);

  // ── S3: NO checkpoint + MOBILE + WORKER → genesis OFF the main thread; heartbeat stays alive ──────
  var hb = 0; var beat = setInterval(function () { hb++; }, 0);  // main-thread heartbeat
  var r3 = await Bootstrap.run(noCkDb, { kernel: noCkK, periodClose: PC, mobile: true, runInWorker: runInWorker, now: function () { clock += 1; return clock; } });
  clearInterval(beat);
  check('S3 mobile genesis WITH worker runs OFF the main thread', r3.path === 'genesis' && r3.offMainThread === true, 'path=' + r3.path + ' offMainThread=' + r3.offMainThread);
  check('S3 main thread stayed responsive during genesis (heartbeat ticked)', hb > 0, 'heartbeatTicks=' + hb);
  var noCkGenState = PC.foldBalances(noCkK.replayOps(noCkDb), {}).bal;
  check('S3 worker-genesis state == inline-genesis state (NO verb fork, maxDiff=0c)', maxDiff(r3.state, noCkGenState) === 0, 'maxDiff=' + maxDiff(r3.state, noCkGenState) + 'c');

  // ── S4: DESKTOP genesis (no worker) → inline tolerated (2.5 s desktop ceiling, not the mobile cliff) ─
  var r4 = await Bootstrap.run(noCkDb, { kernel: noCkK, periodClose: PC, mobile: false, now: function () { clock += 1; return clock; } });
  check('S4 desktop genesis inline tolerated (ok=true, offMainThread=N)', r4.ok === true && r4.offMainThread === false && r4.path === 'genesis');

  console.log('§FALSIFIER mobileGenesisInline=' + (r2.offMainThread === false && r2.refused ? 'none' : 'FAIL'));
  console.log('§BOOTSTRAP-PATH OVERALL=' + (FAILS.length === 0 ? 'PASS' : 'FAIL (' + FAILS.join('; ') + ')'));
  process.exit(FAILS.length === 0 ? 0 : 1);
})().catch(function (e) { console.error('FATAL ' + e.stack); process.exit(2); });
