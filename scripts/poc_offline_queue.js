#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_offline_queue.js — witness for CONSTRAINT_MITIGATION_LANE item 4 (bounded offline queue + backoff).
 *   Spec: docs/FoldEngineConstraints.md §3 bottleneck 3 / §4 P1  ·  Module: build/erp/offline_queue.js
 *
 * Issue this PROVES: an offline device's op queue is BOUNDED (bytes & age), the cap forces a local
 *   checkpoint + purges folded&relayed ops, NEVER loses an unrelayed op, and relay retry is exponential
 *   backoff capped at 5 min.
 * §FALSIFIER: purge() that drops ANY unrelayed op = data loss → MUST never happen. Backoff > 5 min → FAIL.
 *
 * Thresholds injected small + clock injected so the witness is deterministic (the LOGIC is the claim, the
 * 50 MB / 7 d numbers are config). READ THE LOG. Run: node scripts/poc_offline_queue.js
 */
'use strict';
var path = require('path');
var OfflineQueue = require(path.join(__dirname, '..', 'build', 'erp', 'offline_queue.js'));

var FAILS = [];
function check(name, cond, detail) { console.log((cond ? '   ✓ ' : '   ✗ ') + name + (detail ? ' — ' + detail : '')); if (!cond) FAILS.push(name); }
function op(uuid, n) { return { op_uuid: uuid, op_type: 'POST', parameters: { n: n, pad: 'x'.repeat(100) } }; }

console.log('═══ §OFFLINE-QUEUE — bounded queue + checkpoint-purge + backoff relay (item 4) ═══');

// ── A. BYTES cap → onCap forces checkpoint → purge keeps unrelayed ────────────────────────────────
var clock = 1000;
var checkpointForced = 0;
// small byte cap so the cap fires deterministically; onCap marks the OLD folded ops as folded.
var q = new OfflineQueue({
  maxBytes: 600, maxAgeMs: 7 * 24 * 3600 * 1000, backoffBaseMs: 1000, backoffCapMs: 300000,
  now: function () { return clock; },
  onCap: function () {
    checkpointForced++;
    // a local checkpoint folds everything currently queued; mark the relayed ones as folded.
    q._q.forEach(function (r) { r.folded = true; });   // checkpoint folds the open period
  }
});
// enqueue 3 ops (~130 B each). Relay acks the first two (relayed); the third stays UNRELAYED.
var r1 = q.enqueue(op('u1', 1)); var r2 = q.enqueue(op('u2', 2)); var r3 = q.enqueue(op('u3', 3));
q.markRelayed([1, 2]);                                  // u1,u2 relayed; u3 NOT
check('A no cap yet under threshold', !r1.capFired && !r2.capFired && !r3.capFired, 'bytes=' + q.bytes());
// push over the byte cap → cap fires → onCap folds all → purge drops folded&relayed (u1,u2), keeps u3.
var r4 = q.enqueue(op('u4', 4));
check('A bytes cap fired', r4.capFired === true);
check('A cap forced a local checkpoint', checkpointForced === 1, 'forced=' + checkpointForced);
var remaining = q._q.map(function (r) { return r.op.op_uuid; });
check('A FALSIFIER — unrelayed op u3 SURVIVES the cap+purge (no data loss)', remaining.indexOf('u3') >= 0, 'remaining=[' + remaining + ']');
check('A folded&relayed u1,u2 were dropped', remaining.indexOf('u1') < 0 && remaining.indexOf('u2') < 0, 'remaining=[' + remaining + ']');
// u4 was enqueued THEN cap fired (onCap folds it) but u4 is unrelayed → must be kept.
check('A newly-enqueued unrelayed u4 kept', remaining.indexOf('u4') >= 0, 'remaining=[' + remaining + ']');

// ── B. AGE cap → fires when oldest op exceeds maxAgeMs ────────────────────────────────────────────
var clk2 = 0;
var ageQ = new OfflineQueue({ maxBytes: 1e9, maxAgeMs: 5000, now: function () { return clk2; },
  onCap: function () { ageQ._q.forEach(function (r) { r.folded = true; }); } });
clk2 = 0; ageQ.enqueue(op('a1', 1)); ageQ.markRelayed([1]);
clk2 = 3000; var b2 = ageQ.enqueue(op('a2', 2));
check('B no age cap before maxAgeMs', !b2.capFired, 'oldestAgeMs=' + ageQ.oldestAgeMs());
clk2 = 6000; var b3 = ageQ.enqueue(op('a3', 3));          // oldest (a1) now 6000ms > 5000ms → cap
check('B age cap fired past maxAgeMs', b3.capFired === true, 'oldestAgeMs=6000 > 5000');

// ── C. EXPONENTIAL BACKOFF capped at 5 min ────────────────────────────────────────────────────────
var bq = new OfflineQueue({ backoffBaseMs: 1000, backoffCapMs: 300000 });
var seq = []; for (var i = 0; i < 12; i++) seq.push(bq.nextBackoff());
console.log('§OFFQ backoff-seq=[' + seq.join(',') + ']');
check('C backoff grows exponentially from base', seq[0] === 1000 && seq[1] === 2000 && seq[2] === 4000);
check('C FALSIFIER — backoff NEVER exceeds 5 min cap', seq.every(function (m) { return m <= 300000; }), 'max=' + Math.max.apply(null, seq));
check('C backoff saturates at the cap', seq[seq.length - 1] === 300000);
bq.resetBackoff();
check('C resetBackoff returns to base', bq.nextBackoff() === 1000);

// ── D. flush success marks relayed + resets backoff; failure keeps unrelayed + bumps backoff ───────
(async function () {
  var fq = new OfflineQueue({ backoffBaseMs: 1000, backoffCapMs: 300000 });
  fq.enqueue(op('f1', 1)); fq.enqueue(op('f2', 2));
  var okClient = { push: function () { return Promise.resolve({ ok: true }); } };
  var badClient = { push: function () { return Promise.reject(new Error('offline')); } };

  var failRes = await fq.flush(badClient);
  check('D flush failure keeps ops unrelayed (no loss)', failRes.failed === true && fq._q.every(function (r) { return !r.relayed; }));
  check('D flush failure bumped backoff', failRes.backoffMs === 1000, 'backoffMs=' + failRes.backoffMs);
  var failRes2 = await fq.flush(badClient);
  check('D repeated failure grows backoff', failRes2.backoffMs === 2000, 'backoffMs=' + failRes2.backoffMs);

  var okRes = await fq.flush(okClient);
  check('D flush success relays all unrelayed', okRes.pushed === 2 && fq._q.every(function (r) { return r.relayed; }));
  check('D flush success reset backoff', fq.nextBackoff() === 1000);

  console.log('§OFFLINE-QUEUE OVERALL=' + (FAILS.length === 0 ? 'PASS' : 'FAIL (' + FAILS.join('; ') + ')'));
  process.exit(FAILS.length === 0 ? 0 : 1);
})();
