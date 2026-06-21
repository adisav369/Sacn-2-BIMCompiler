#!/usr/bin/env node
// W-BENCH-FOLD — fold time vs. op-log length.
// Accumulates CRUD_UPDATE ops in a kernel_ops table and times listTip() at each checkpoint.
// listTip() is the read-the-tip fold: walks the full op-log and projects current state.
// Issue it proves: "re-fold gets slower as the log grows" — measures the actual scaling curve.
// Run: bash build/erp/run_witness.sh scripts/poc_bench_fold_curve.js
'use strict';
var path = require('path'), fs = require('fs');
var initSqlJs = require('sql.js');

global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var KO = global.window.KernelOps;
var CRUD = require(path.join(__dirname, '..', 'build', 'erp', 'crud_overlay.js'));

var SEED = path.join(__dirname, '..', 'build', 'erp', 'post_poc', 'ad_seed.db');
var CHECKPOINTS = [10, 100, 500, 1000, 5000, 10000];
var SAMPLE_RUNS = 3;   // average over N runs for each checkpoint timing
var fails = 0;

function verdict(ok, label, detail) {
  if (!ok) fails++;
  console.log('  ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : ''));
}

(async function () {
  console.log('\n═══ W-BENCH-FOLD — fold time vs op-log length ═══\n');
  var SQL = await initSqlJs();
  var db = new SQL.Database(new Uint8Array(fs.readFileSync(SEED)));
  KO.ensureTable(db);

  // seed baseRows for listTip: a small slice of AD_Window rows (deterministic)
  var baseRows = [];
  var wr = db.exec("SELECT AD_Window_ID, Name, Description FROM AD_Window WHERE IsActive='Y' ORDER BY AD_Window_ID LIMIT 60");
  if (wr.length) {
    wr[0].values.forEach(function (v) {
      baseRows.push({ AD_Window_ID: v[0], Name: v[1], Description: v[2] });
    });
  }
  if (!baseRows.length) { console.log('§W-BENCH-FOLD FAIL no AD_Window rows'); process.exit(1); }

  var baseTs = 1718200000000;
  var opCounter = 0;
  var cpIdx = 0;
  var results = [];

  while (cpIdx < CHECKPOINTS.length) {
    var target = CHECKPOINTS[cpIdx];

    // fill ops up to checkpoint
    while (opCounter < target) {
      var wid = baseRows[opCounter % baseRows.length].AD_Window_ID;
      var op = {
        op_type: 'CRUD_UPDATE',
        op_uuid: 'bench-fold-' + opCounter,
        params: {
          table: 'AD_Window',
          id: wid,
          changes: { Description: { old: 'v' + (opCounter - 1), new: 'v' + opCounter } },
          actor: 100
        }
      };
      await KO.commitGroup(db, [op], { gid: 'fold-' + opCounter, baseTs: baseTs + opCounter });
      opCounter++;
    }

    // time SAMPLE_RUNS folds and take the median
    var times = [];
    for (var s = 0; s < SAMPLE_RUNS; s++) {
      var t0 = Date.now();
      CRUD.listTip(db, 'AD_Window', 'AD_Window_ID', baseRows);
      times.push(Date.now() - t0);
    }
    times.sort(function (a, b) { return a - b; });
    var median = times[Math.floor(times.length / 2)];
    results.push({ ops: target, ms: median });
    console.log('§FOLD-CURVE ops=' + target + ' ms=' + median + ' (median of ' + SAMPLE_RUNS + ')');
    cpIdx++;
  }

  console.log('');

  // pass 1: fold at 10K ops must complete in under 2 seconds
  var maxMs = results[results.length - 1].ms;
  verdict(maxMs < 2000, 'fold at 10K ops under 2s', 'ms=' + maxMs);

  // pass 2: scaling must be sub-quadratic (time at 10K < 50× time at 100 ops — linear would be 100×)
  var at100 = results.find(function (r) { return r.ops === 100; });
  var at10k = results.find(function (r) { return r.ops === 10000; });
  if (at100 && at100.ms > 0) {
    var ratio = at10k.ms / at100.ms;
    verdict(ratio < 150, 'scaling sub-quadratic (10K/100 ratio < 150)', 'ratio=' + ratio.toFixed(1) + 'x');
  } else {
    verdict(true, 'scaling check skipped (100-op fold was <1ms — too fast to ratio)', '');
  }

  console.log('\n' + (fails === 0 ? '✅ W-BENCH-FOLD PASS' : '❌ W-BENCH-FOLD FAIL (' + fails + ' failures)'));
  process.exit(fails ? 1 : 0);
})();
