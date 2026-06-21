#!/usr/bin/env node
// W-BENCH-VERIFY — verifyChain throughput vs. chain length.
// Builds signed op chains of increasing length and times verifyChain() at each checkpoint.
// verifyChain re-computes every op_hash + prev_hash link and checks each sig.
// Issue it proves: "cryptographic chain verification is too slow at production scale" — true or false?
// Run: bash build/erp/run_witness.sh scripts/poc_bench_verify_chain.js
'use strict';
var path = require('path'), fs = require('fs');
var initSqlJs = require('sql.js');

global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var KO = global.window.KernelOps;

var SEED = path.join(__dirname, '..', 'build', 'erp', 'post_poc', 'ad_seed.db');
var CHECKPOINTS = [100, 500, 1000, 5000, 10000];
var fails = 0;

function verdict(ok, label, detail) {
  if (!ok) fails++;
  console.log('  ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : ''));
}

(async function () {
  console.log('\n═══ W-BENCH-VERIFY — verifyChain throughput ═══\n');
  var SQL = await initSqlJs();
  var db = new SQL.Database(new Uint8Array(fs.readFileSync(SEED)));
  KO.ensureTable(db);

  var baseTs = 1718200000000;
  var opCounter = 0;
  var cpIdx = 0;
  var results = [];

  while (cpIdx < CHECKPOINTS.length) {
    var target = CHECKPOINTS[cpIdx];

    while (opCounter < target) {
      var op = {
        op_type: 'CRUD_UPDATE',
        op_uuid: 'bench-verify-' + opCounter,
        params: {
          table: 'AD_Window',
          id: (100 + (opCounter % 40)),
          changes: { Description: { old: 'v' + (opCounter - 1), new: 'v' + opCounter } },
          actor: 100
        }
      };
      await KO.commitGroup(db, [op], { gid: 'vfy-' + opCounter, baseTs: baseTs + opCounter });
      opCounter++;
    }

    var t0 = Date.now();
    var result = await KO.verifyChain(db);
    var elapsed = Date.now() - t0;
    var opsPerSec = elapsed > 0 ? Math.round(target / (elapsed / 1000)) : 999999;

    results.push({ ops: target, ms: elapsed, ok: result.ok, opsPerSec: opsPerSec });
    console.log('§VERIFY-CHAIN ops=' + target + ' ms=' + elapsed + ' ops/sec=' + opsPerSec + ' chainOk=' + result.ok);

    if (!result.ok) {
      console.log('  ⚠ chain integrity broken at ' + target + ' ops — ' + JSON.stringify(result).slice(0, 200));
    }
    cpIdx++;
  }

  console.log('');

  // pass 1: chain must be intact at all sizes
  var allIntact = results.every(function (r) { return r.ok; });
  verdict(allIntact, 'chain integrity intact at all sizes');

  // pass 2: verify 10K ops under 10s
  var last = results[results.length - 1];
  verdict(last.ms < 10000, 'verify 10K ops under 10s', 'ms=' + last.ms);

  // pass 3: throughput at 10K > 500 ops/sec
  verdict(last.opsPerSec > 500, 'throughput > 500 ops/sec at 10K', 'ops/sec=' + last.opsPerSec);

  // pass 4: scaling must be linear or better (100→10K is 100×; time ratio should be <200×)
  var at100 = results.find(function (r) { return r.ops === 100; });
  if (at100 && at100.ms > 0) {
    var ratio = last.ms / at100.ms;
    verdict(ratio < 200, 'scaling linear or better (10K/100 ratio <200)', 'ratio=' + ratio.toFixed(1) + 'x');
  } else {
    verdict(true, 'scaling check skipped (100-op verify <1ms)', '');
  }

  console.log('\n' + (fails === 0 ? '✅ W-BENCH-VERIFY PASS' : '❌ W-BENCH-VERIFY FAIL (' + fails + ' failures)'));
  process.exit(fails ? 1 : 0);
})();
