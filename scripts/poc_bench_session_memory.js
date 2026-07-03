#!/usr/bin/env node
// W-BENCH-MEMORY — session-scale memory profile.
// Simulates an ERP session: N commitGroup calls (record creates/edits) accumulating on one in-memory db.
// Measures process.memoryUsage().heapUsed at each checkpoint to show whether heap stabilises or leaks.
// Issue it proves: "constant fold allocations cause unbounded memory growth" — true or false?
// Run: bash build/erp/run_witness.sh scripts/poc_bench_session_memory.js
'use strict';
var path = require('path'), fs = require('fs');
var initSqlJs = require('sql.js');

global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var KO = global.window.KernelOps;

var SEED = path.join(__dirname, '..', 'build', 'erp', 'post_poc', 'ad_seed.db');
var CHECKPOINTS = [0, 100, 500, 1000, 2000, 5000];
var fails = 0;

function mb(bytes) { return (bytes / 1024 / 1024).toFixed(2); }
function verdict(ok, label, detail) {
  if (!ok) fails++;
  console.log('  ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : ''));
}

(async function () {
  console.log('\n═══ W-BENCH-MEMORY — session-scale heap profile ═══\n');
  var SQL = await initSqlJs();
  var db = new SQL.Database(new Uint8Array(fs.readFileSync(SEED)));
  KO.ensureTable(db);

  var baseTs = 1718200000000;
  var opCounter = 0;
  var baselineHeap = process.memoryUsage().heapUsed;
  var prevHeap = baselineHeap;
  var cpIdx = 0;
  var results = [];

  // warm up: ensure table exists in the heap before baseline
  db.exec('SELECT COUNT(*) FROM kernel_ops');

  console.log('§SESSION-MEMORY baseline heapMB=' + mb(baselineHeap) + '\n');

  while (cpIdx < CHECKPOINTS.length) {
    var target = CHECKPOINTS[cpIdx];

    // run ops up to next checkpoint
    while (opCounter < target) {
      var op = {
        op_type: 'CRUD_UPDATE',
        op_uuid: 'bench-mem-' + opCounter,
        params: {
          table: 'AD_Window',
          id: (100 + (opCounter % 50)),
          changes: { Description: { old: 'v' + (opCounter - 1), new: 'v' + opCounter } },
          actor: 100
        }
      };
      await KO.commitGroup(db, [op], { gid: 'mem-' + opCounter, baseTs: baseTs + opCounter });
      opCounter++;
    }

    var heap = process.memoryUsage().heapUsed;
    var deltaMB = mb(heap - baselineHeap);
    var stepMB  = mb(heap - prevHeap);
    prevHeap = heap;
    results.push({ ops: target, heapMB: mb(heap), deltaMB: +deltaMB });
    console.log('§SESSION-MEMORY ops=' + target + ' heapMB=' + mb(heap) + ' deltaMB=+' + deltaMB + ' stepMB=+' + stepMB);
    cpIdx++;
  }

  console.log('');

  // pass criterion: total delta from 0→5K ops < 80MB (headroom for any GC timing variation)
  var totalDelta = results[results.length - 1].deltaMB;
  verdict(totalDelta < 80, 'heap delta 0→5K ops under 80 MB', 'delta=' + totalDelta + ' MB');

  // pass criterion: step from 2K→5K ops not more than 3× the step from 0→100 ops (no runaway growth)
  var earlyStep = results[1].deltaMB;  // 0→100
  var lateStep  = results[results.length - 1].deltaMB - results[results.length - 2].deltaMB; // 2K→5K
  verdict(lateStep <= Math.max(earlyStep * 3, 5), 'late-session growth ≤ 3× early growth (no unbounded leak)',
    'early=' + earlyStep.toFixed(2) + 'MB late=' + lateStep.toFixed(2) + 'MB');

  console.log('\n' + (fails === 0 ? '✅ W-BENCH-MEMORY PASS' : '❌ W-BENCH-MEMORY FAIL (' + fails + ' failures)'));
  process.exit(fails ? 1 : 0);
})();
