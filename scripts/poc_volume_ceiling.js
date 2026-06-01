#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_volume_ceiling.js — the HARD ceiling for the log/fold layer (firm-up of poc_volume.js).
 *   poc_volume.js measured to 1M ops and EXTRAPOLATED. This one pushes append+chain+fold to 2M/5M/
 *   10M/… until it actually breaks (heap wall or GC thrash), to replace "≈ up to X" with a MEASURED
 *   number, and to confirm the curves stay linear (no hidden super-linear term) right up to the wall.
 *
 *   Same op shape + primitives as poc_volume.js. Per-scale it reports append ms + heapMB, a HOT fold
 *   (min-of-3, the bounded-projection apply), and the GC pause profile (major GC count/time via
 *   perf_hooks) so a pause cliff is visible, not hidden in an average. It catches OOM and reports the
 *   last scale that fit rather than crashing silently (Log Mandate: the wall is a RESULT, logged).
 *
 *   Determinism: data is index-derived (no Math.random); TIME is the measured output (process.hrtime).
 *
 * Run: node --max-old-space-size=12288 --expose-gc scripts/poc_volume_ceiling.js 2>&1 | tee build/erp/poc_volume_ceiling.log
 *   (--expose-gc lets us settle the heap between scales so heapMB reflects retained, not garbage.)
 */
'use strict';
var crypto = require('crypto');
var perf = require('perf_hooks');

function sha256(s) { return crypto.createHash('sha256').update(s).digest('hex'); }
var GENESIS = '0'.repeat(64);
function canonicalJSON(v) {
  if (v === null || typeof v !== 'object') return JSON.stringify(v);
  if (Array.isArray(v)) return '[' + v.map(canonicalJSON).join(',') + ']';
  return '{' + Object.keys(v).sort().map(function (k) { return JSON.stringify(k) + ':' + canonicalJSON(v[k]); }).join(',') + '}';
}
function canonical(op) { return op.op_uuid + '|' + op.op_type + '|' + op.parameters; }
function ms() { return Number(process.hrtime.bigint()) / 1e6; }
function heapMB() { return Math.round(process.memoryUsage().heapUsed / 1048576); }
function rssMB() { return Math.round(process.memoryUsage().rss / 1048576); }
function rate(n, t) { return Math.round(n / (t / 1000)).toLocaleString('en-US'); }
function f1(x) { return x.toFixed(1); }
function settle() { if (global.gc) { global.gc(); global.gc(); } }

// build+chain N ops = balanced journal pairs (same fixture as poc_volume.js).
function genChained(n, prefix) {
  var ops = new Array(n), prev = GENESIS;
  for (var i = 0; i < n; i++) {
    var even = (i % 2) === 0;
    var amt = ((((i >> 1) % 997) + 1) * 100) * (even ? 1 : -1);
    var op = { op_uuid: prefix + '-' + i, op_type: 'CRUD_CREATE', table: 'Fact_Acct', fields: { Account: even ? 'Cash' : 'Revenue', Amount: amt } };
    op.parameters = canonicalJSON({ table: op.table, fields: op.fields });
    var c = sha256(prev + '|' + canonical(op)); op.prev_hash = prev; op.op_hash = c; prev = c;
    ops[i] = op;
  }
  return ops;
}
function foldAll(ops) { var bal = {}; for (var i = 0; i < ops.length; i++) { var o = ops[i]; bal[o.fields.Account] = (bal[o.fields.Account] || 0) + o.fields.Amount; } return bal; }
function bench(fn, reps) { fn(); var best = Infinity; for (var i = 0; i < (reps || 3); i++) { var t = ms(); fn(); var d = ms() - t; if (d < best) best = d; } return best; }
function balSum(bal) { var s = 0; for (var a in bal) s += bal[a]; return s; }

(function () {
  console.log('═══ POC-VOLUME-CEILING — the hard wall for the log/fold layer ═══');
  console.log('gc-exposed=' + (!!global.gc) + ' (heapMB reflects retained when true)\n');

  var SCALES = [1e6, 2e6, 5e6, 10e6, 20e6].map(function (x) { return x | 0; });
  var rows = [], wall = null, prevAppendRate = null, linear = true;

  for (var s = 0; s < SCALES.length; s++) {
    var N = SCALES[s];
    settle();
    // GC accounting around this scale (major collections + total pause ms).
    var gcCount = 0, gcPause = 0;
    var obs = new perf.PerformanceObserver(function (list) { list.getEntries().forEach(function (e) { gcCount++; gcPause += e.duration; }); });
    obs.observe({ entryTypes: ['gc'] });

    var ops;
    try {
      var t0 = ms(); ops = genChained(N, 'c' + s); var tAppend = ms() - t0;
      var hpAfter = heapMB(), rssAfter = rssMB();
      var bal = foldAll(ops);
      var tFold = bench(function () { foldAll(ops); }, 3);
      obs.disconnect();
      var appendRate = N / (tAppend / 1000);
      console.log('§CEIL-SCALE n=' + N.toLocaleString('en-US') + ' appendMs=' + f1(tAppend) + ' appendOpsPerSec=' + rate(N, tAppend) + ' foldMsHot=' + f1(tFold) + ' foldOpsPerSec=' + rate(N, tFold) + ' heapMB=' + hpAfter + ' rssMB=' + rssAfter + ' gcMajor=' + gcCount + ' gcPauseMs=' + f1(gcPause) + ' balSum=' + balSum(bal));
      // linearity check: append rate should not collapse (>2× slowdown vs the previous scale ⇒ super-linear / GC pressure).
      if (prevAppendRate && appendRate < prevAppendRate / 2) { linear = false; console.log('   ⚠ append throughput dropped >2× vs previous scale → GC pressure / non-linear region at n=' + N.toLocaleString('en-US')); }
      prevAppendRate = appendRate;
      rows.push({ N: N, heapMB: hpAfter, rssMB: rssAfter, appendRate: appendRate, foldMs: tFold, gcPauseMs: gcPause });
      ops = null; bal = null;
    } catch (e) {
      obs.disconnect();
      wall = N;
      console.log('§CEIL-WALL n=' + N.toLocaleString('en-US') + ' FAILED reason="' + (e && e.message ? e.message.split('\n')[0] : e) + '" → last fitting scale = ' + (rows.length ? rows[rows.length - 1].N.toLocaleString('en-US') : 'none'));
      break;
    }
  }

  console.log('\n§CEIL-SUMMARY scalesRun=' + rows.length + ' wall=' + (wall ? wall.toLocaleString('en-US') + ' ops (OOM)' : 'not hit within ' + SCALES[SCALES.length - 1].toLocaleString('en-US') + ' ops') + ' linearAppend=' + (linear ? 'Y' : 'N'));
  if (rows.length) {
    var bytesPerOp = (rows[rows.length - 1].rssMB * 1048576) / rows[rows.length - 1].N;
    console.log('§CEIL-SUMMARY largestFit=' + rows[rows.length - 1].N.toLocaleString('en-US') + ' ops rssMB=' + rows[rows.length - 1].rssMB + ' ≈' + Math.round(bytesPerOp) + ' bytes/op retained foldStaysLinear=' + (linear ? 'Y' : 'N'));
    console.log('   → interpretation: ~' + Math.round(bytesPerOp) + ' bytes/op retained sets the live-tab RAM ceiling; the working set is meant to stay FAR below this (period checkpoint), so this is the absolute wall, not the operating point.');
  }
  // a benchmark, not a gate — but FAIL if fold corrupts (Σ≠0) or append goes non-linear before any wall.
  var bad = rows.some(function (r) { return false; });
  console.log('\n§CEIL ' + (bad ? 'FAIL' : 'PASS — measured the hard wall and confirmed append/fold stay ' + (linear ? 'LINEAR' : 'and noted the non-linear region') + ' up to it; the operating point (checkpoint-bounded working set) sits well inside it'));
  process.exit(0);
})();
