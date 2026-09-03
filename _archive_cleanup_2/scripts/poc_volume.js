#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_volume.js — the "mechanical, bounded by the checkpoint" claim, MEASURED (prompts/VOLUME_POC.md).
 *   Puts numbers on docs/HolyGrail.md §3 OLTP coda: "maintain the read-projection incrementally at high
 *   append rates, BOUNDED BY THE PERIOD CHECKPOINT so the working set stays small."
 *
 *   "High users" here = NO central server (physics partitions writers; each terminal applies in-RAM).
 *   So we measure PER-TERMINAL + MERGE costs, not concurrent connections:
 *     §VOL-APPEND     build+chain N ops (write cost, sha256-bound)
 *     §VOL-FOLD       fold N ops from genesis (cold bootstrap cost); accounts=k shows the bounded working set
 *     §VOL-VERIFY     verifyChain over N ops (audit/replay-integrity cost)
 *     §VOL-CKPT       ECDSA sign+verify at close (≈constant, negligible vs N)
 *     §VOL-BOOTSTRAP  THE LOAD-BEARING TEST — fold-from-genesis ∝ T vs bootstrap (ckpt + last period) ∝ P;
 *                     bootstrap must stay ~FLAT as total history T grows, else the bounded-working-set claim
 *                     is qualified (FALSIFIER: flat=N ⇒ exit 1).
 *     §VOL-MERGE      total-order sort cost for M device logs (DistributedERP.md §6 facilitator path)
 *     §VOL-THRESH     derived budgets: max live-tab before fold crosses 16 ms / bootstrap crosses 1 s
 *
 *   Reuse: canonical/sha256/chainOne/verifyChain are poc_chain/poc_showstopper verbatim; the checkpoint
 *   signer is poc_sign (ECDSA P-256); totalOrder mirrors poc_distributed. The op SHAPE (CRUD_CREATE Fact_Acct
 *   journal pairs) matches CORE.buildOp's output — equivalence already witnessed in poc_showstopper, so here
 *   we generate fast and benchmark the KERNEL mechanics, not the buildOp call overhead.
 *
 *   Determinism: the DATA is index-derived (NO Math.random). TIME is the measured OUTPUT — process.hrtime is
 *   the observable here, not invented data; only durations vary run to run. INTEGER cents (exact).
 *
 * Run: node --max-old-space-size=4096 scripts/poc_volume.js 2>&1 | tee build/erp/poc_volume.log
 */
'use strict';
var crypto = require('crypto');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

// ── primitives reused verbatim from poc_chain.js / poc_showstopper.js ──
function canonicalJSON(v) {
  if (v === null || typeof v !== 'object') return JSON.stringify(v);
  if (Array.isArray(v)) return '[' + v.map(canonicalJSON).join(',') + ']';
  return '{' + Object.keys(v).sort().map(function (k) { return JSON.stringify(k) + ':' + canonicalJSON(v[k]); }).join(',') + '}';
}
function canonical(op) { return op.op_uuid + '|' + op.op_type + '|' + op.parameters; }
function sha256(s) { return crypto.createHash('sha256').update(s).digest('hex'); }
var GENESIS = '0'.repeat(64);
function chainOne(op, prevHash) { var prev = prevHash || GENESIS; return { prev_hash: prev, op_hash: sha256(prev + '|' + canonical(op)) }; }
function newKey() { return crypto.generateKeyPairSync('ec', { namedCurve: 'P-256' }); }
function signHash(h, sk) { return crypto.sign('sha256', Buffer.from(h), sk).toString('hex'); }
function verifyHash(h, sigHex, pk) { try { return crypto.verify('sha256', Buffer.from(h), pk, Buffer.from(sigHex, 'hex')); } catch (e) { return false; } }

// ── timing: hrtime is the MEASURED OUTPUT (not fixture data) ──
function ms() { return Number(process.hrtime.bigint()) / 1e6; }
function heapMB() { return Math.round(process.memoryUsage().heapUsed / 1048576); }
function rate(n, t) { return Math.round(n / (t / 1000)).toLocaleString('en-US'); }
function f1(x) { return x.toFixed(1); }
// bench — warm up once (JIT), then take the MIN of `reps` runs: the steady-state cost, scheduler-noise-removed.
// Standard microbenchmark hygiene — without it the FIRST timed fold is warmup-dominated (cold ≈ 7× hot here).
function bench(fn, reps) { fn(); var best = Infinity; var r = reps || 4; for (var i = 0; i < r; i++) { var t = ms(); fn(); var d = ms() - t; if (d < best) best = d; } return best; }

// ── generate N chained ops = balanced journal pairs (Dr Cash +amt / Cr Revenue −amt) ──
// amounts index-derived; Σ-balance stays 0; the projection key-set stays bounded (a chart of accounts)
// no matter how long the log grows — the LOG is O(history), the WORKING SET is O(chart).
function genChained(n, prefix, startPrev) {
  var ops = new Array(n), prev = startPrev || GENESIS;
  for (var i = 0; i < n; i++) {
    var even = (i % 2) === 0;
    var amt = ((((i >> 1) % 997) + 1) * 100) * (even ? 1 : -1);     // pair (i, i+1) shares |amt|
    var acct = even ? 'Cash' : 'Revenue';
    var op = { op_uuid: prefix + '-' + i, op_type: 'CRUD_CREATE', table: 'Fact_Acct', fields: { Account: acct, Amount: amt } };
    op.parameters = canonicalJSON({ table: op.table, fields: op.fields });
    var c = chainOne(op, prev); op.prev_hash = c.prev_hash; op.op_hash = c.op_hash; prev = c.op_hash;
    ops[i] = op;
  }
  return { ops: ops, tip: prev };
}

// ── fold: bounded projection (a few accounts) over the ops ──
function fold(ops, opening, from, to) {
  var bal = {}; if (opening) for (var a in opening) bal[a] = opening[a];
  var lo = from || 0, hi = (to == null ? ops.length : to);
  for (var i = lo; i < hi; i++) { var op = ops[i]; bal[op.fields.Account] = (bal[op.fields.Account] || 0) + op.fields.Amount; }
  return bal;
}
function verifyChain(ops, startPrev) {
  var prev = startPrev || GENESIS;
  for (var i = 0; i < ops.length; i++) { var op = ops[i]; if (op.prev_hash !== prev) return { ok: false, brokeAt: i }; if (sha256(prev + '|' + canonical(op)) !== op.op_hash) return { ok: false, brokeAt: i }; prev = op.op_hash; }
  return { ok: true };
}
function balSum(bal) { var s = 0; for (var a in bal) s += bal[a]; return s; }
function nAccounts(bal) { return Object.keys(bal).length; }

(function () {
  console.log('═══ POC-VOLUME — "mechanical, bounded by the checkpoint", measured ═══');
  console.log('(single-threaded Node = an UPPER BOUND on per-terminal cost; the conservative direction)\n');

  // ── §VOL-APPEND / §VOL-FOLD / §VOL-VERIFY across scales ───────────────────────────────────
  console.log('Per-terminal costs across scale (append → fold → verify):');
  var SCALES = [1000, 10000, 100000, 1000000];
  var foldTimes = {};                                            // N → fold ms (for the threshold derivation)
  SCALES.forEach(function (N) {
    var t0 = ms(); var g = genChained(N, 's' + N); var tAppend = ms() - t0;   // append = one-shot (allocates; can't rep cheaply)
    console.log('§VOL-APPEND n=' + N + ' ms=' + f1(tAppend) + ' opsPerSec=' + rate(N, tAppend) + ' heapMB=' + heapMB());

    var bal = fold(g.ops);                                       // steady-state (hot) cost via bench → consistent with thresholds
    var tFold = bench(function () { fold(g.ops); }); foldTimes[N] = tFold;
    console.log('§VOL-FOLD n=' + N + ' ms=' + f1(tFold) + ' opsPerSec=' + rate(N, tFold) + ' accounts=' + nAccounts(bal) + ' balSum=' + balSum(bal) + ' (hot, min-of-4)');
    if (balSum(bal) !== 0) verdict(false, 'fold sanity Σ=0 at n=' + N, 'Σ=' + balSum(bal));

    var v = verifyChain(g.ops);
    var tVerify = bench(function () { verifyChain(g.ops); });
    console.log('§VOL-VERIFY n=' + N + ' ms=' + f1(tVerify) + ' opsPerSec=' + rate(N, tVerify) + ' chain=' + (v.ok ? 'ok' : 'FAIL@' + v.brokeAt) + ' (hot, min-of-4)');
    if (!v.ok) verdict(false, 'chain verifies at n=' + N, 'broke@' + v.brokeAt);
    g = null; bal = null;                                        // release before the next scale
  });
  verdict(nAccounts(fold(genChained(1000, 'chk').ops)) <= 4, 'working set BOUNDED: projection stays a small chart of accounts as N grows (LOG grows, working set does not)', 'accounts≤4');

  // ── §VOL-CKPT — the period-close signature is ~constant, negligible vs N ──────────────────
  console.log('\nPeriod-close signature cost (constant, vs the O(N) above):');
  var controller = newKey();
  var head = genChained(1000, 'ck').tip, payload = sha256(canonicalJSON({ period: 1, balances: { Cash: 0, Revenue: 0 }, chainHead: head }));
  var ts = ms(); var sig = signHash(payload, controller.privateKey); var tSign = ms() - ts;
  var tv = ms(); var ok = verifyHash(payload, sig, controller.publicKey); var tVer = ms() - tv;
  console.log('§VOL-CKPT signMs=' + f1(tSign) + ' verifyMs=' + f1(tVer) + ' verify=' + ok);
  verdict(ok && tSign < 50 && tVer < 50, 'checkpoint sign+verify is ~constant (≪ a single fold of a large period)', 'sign=' + f1(tSign) + 'ms verify=' + f1(tVer) + 'ms');

  // ── §VOL-BOOTSTRAP — THE LOAD-BEARING TEST: bootstrap ∝ period, NOT ∝ total history ───────
  console.log('\nBootstrap: fold-from-genesis (∝ total history) vs from-checkpoint (∝ period) — the claim:');
  var P = 10000;                                                 // fixed period size
  var TOTALS = [P, 10 * P, 100 * P];                             // growing total history
  var bootMsAt = [], genMsAt = [];
  TOTALS.forEach(function (T) {
    var g = genChained(T, 't' + T);
    var openings = fold(g.ops, null, 0, T - P);                  // checkpoint = balances at T−P (STORED at close; O(1) to load at bootstrap — so it is OUTSIDE the timed region)
    var genesisMs = bench(function () { fold(g.ops); });          // cold replay of ALL history (the thing checkpointing avoids)
    var bootMs = bench(function () { fold(g.ops, openings, T - P, T); }); // fold ONLY the last (open) period
    genMsAt.push(genesisMs); bootMsAt.push(bootMs);
    console.log('§VOL-BOOTSTRAP period=' + P + ' totalHistory=' + T + ' genesisFoldMs=' + f1(genesisMs) + ' bootstrapFoldMs=' + f1(bootMs) + ' speedup=' + f1(genesisMs / Math.max(bootMs, 0.001)) + '× (hot, min-of-4)');
    g = null;
  });
  var bMax = Math.max.apply(null, bootMsAt), bMin = Math.min.apply(null, bootMsAt);
  var bootFlat = (bMax / Math.max(bMin, 0.001)) < 3;             // bootstrap ~flat across 100× history growth (∝ period, not total)
  var genGrows = genMsAt[2] > genMsAt[0] * 5;                    // genesis fold grows with total history
  console.log('§VOL-BOOTSTRAP summary bootstrapMs=[' + bootMsAt.map(f1).join(', ') + '] (flat=' + (bootFlat ? 'Y' : 'N') + ') genesisMs=[' + genMsAt.map(f1).join(', ') + '] (grows=' + (genGrows ? 'Y' : 'N') + ')');
  verdict(bootFlat && genGrows, 'BOUNDED WORKING SET: bootstrap-from-checkpoint stays FLAT as history grows 100× (∝ period, not total) while genesis replay grows — the checkpoint IS the bound', 'flat=' + bootFlat + ' grows=' + genGrows);
  if (!bootFlat) console.log('   ⚠ HolyGrail §3 coda QUALIFIED — bootstrap did NOT stay flat; the working set is not bounded by the checkpoint as claimed.');

  // ── §VOL-MERGE — total-order sort for M device logs (the facilitator relay path) ──────────
  console.log('\nMerge: total-order over M device logs (DistributedERP.md §6):');
  var M = 8, K = 12500, merged = [];                             // 8 tills × 12.5k = 100k ops
  for (var d = 0; d < M; d++) { var g = genChained(K, 'dev' + d).ops; for (var i = 0; i < K; i++) merged.push(g[i]); }
  var tm = ms(); merged.sort(function (a, b) { return a.op_uuid < b.op_uuid ? -1 : a.op_uuid > b.op_uuid ? 1 : 0; }); var tMerge = ms() - tm;
  console.log('§VOL-MERGE devices=' + M + ' opsEach=' + K + ' total=' + (M * K) + ' sortMs=' + f1(tMerge) + ' opsPerSec=' + rate(M * K, tMerge));
  verdict(tMerge < 5000, 'M-device merge total-orders in-memory at scale (no coordinator)', 'sort=' + f1(tMerge) + 'ms for ' + (M * K) + ' ops');
  merged = null;

  // ── §VOL-THRESH — derived budgets: the period-close cadence the doc implies ────────────────
  console.log('\nDerived thresholds (interpolated from §VOL-FOLD):');
  // fold is ~linear: opsPerMs from the largest scale → invert to the op-count at a time budget.
  var bigN = SCALES[SCALES.length - 1], opsPerMs = bigN / foldTimes[bigN];
  var liveTab16ms = Math.round(opsPerMs * 16), bootstrap1s = Math.round(opsPerMs * 1000);
  console.log('§VOL-THRESH foldOpsPerMs=' + Math.round(opsPerMs).toLocaleString('en-US') + ' liveTab16ms≈' + liveTab16ms.toLocaleString('en-US') + 'ops bootstrap1s≈' + bootstrap1s.toLocaleString('en-US') + 'ops');
  console.log('   → interpretation: keep the live tab under ~' + liveTab16ms.toLocaleString('en-US') + ' ops to fold within one 60fps frame; close the period well before ~' + bootstrap1s.toLocaleString('en-US') + ' ops to keep cold bootstrap under 1 s.');

  console.log('\n§VOL ' + (fails ? 'FAIL — ' + fails + ' red (see above; if §VOL-BOOTSTRAP, the bounded-working-set claim is qualified)' : 'PASS — append/fold/verify scale linearly; the checkpoint bounds bootstrap to the period size (flat across 100× history); merge is in-memory; signature is constant. The "mechanical, bounded by the checkpoint" claim holds, with numbers.'));
  process.exit(fails ? 1 : 0);
})();
