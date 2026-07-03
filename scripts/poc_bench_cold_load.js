#!/usr/bin/env node
// W-BENCH-LOAD — DB size vs. cold-load time.
// Inflates a copy of ad_seed.db by inserting padding rows (simulating born tenants accumulating data)
// then measures SQL.Database(buf) hydration time at each size tier.
// Issue it proves: "page load gets slower as more tenants are born" — measures the actual curve.
// Run: bash build/erp/run_witness.sh scripts/poc_bench_cold_load.js
'use strict';
var path = require('path'), fs = require('fs');
var initSqlJs = require('sql.js');

var SEED = path.join(__dirname, '..', 'build', 'erp', 'post_poc', 'ad_seed.db');
var ROW_PAYLOAD = 'x'.repeat(512);  // 512-byte row — realistic tenant data row size
var TIERS = [
  { label: 'baseline (seed as-is)', extraRows: 0 },
  { label: '~2x seed (~24 MB)',     extraRows: 12000 },
  { label: '~3x seed (~38 MB)',     extraRows: 26000 },
  { label: '~5x seed (~63 MB)',     extraRows: 52000 },
];
var LOAD_RUNS = 5;   // repeat each load N times, take median
var fails = 0;

function kb(bytes) { return (bytes / 1024).toFixed(0); }
function verdict(ok, label, detail) {
  if (!ok) fails++;
  console.log('  ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : ''));
}

(async function () {
  console.log('\n═══ W-BENCH-LOAD — DB size vs. cold-load time ═══\n');
  var SQL = await initSqlJs();

  var seedBuf = fs.readFileSync(SEED);
  var results = [];

  for (var ti = 0; ti < TIERS.length; ti++) {
    var tier = TIERS[ti];

    // build inflated buffer once per tier
    var inflated;
    if (tier.extraRows === 0) {
      inflated = new Uint8Array(seedBuf);
    } else {
      // inflate on a temp db, export buffer
      var tmp = new SQL.Database(new Uint8Array(seedBuf));
      tmp.run('CREATE TABLE IF NOT EXISTS bench_pad (id INTEGER PRIMARY KEY, payload TEXT)');
      tmp.run('BEGIN');
      for (var r = 0; r < tier.extraRows; r++) {
        tmp.run('INSERT INTO bench_pad (payload) VALUES (?)', [ROW_PAYLOAD + r]);
      }
      tmp.run('COMMIT');
      inflated = tmp.export();
      tmp.close();
    }

    // time LOAD_RUNS cold loads (SQL.Database constructor = the cold-load cost)
    var times = [];
    for (var s = 0; s < LOAD_RUNS; s++) {
      var copy = new Uint8Array(inflated);   // fresh copy — constructor takes ownership
      var t0 = Date.now();
      var db = new SQL.Database(copy);
      db.exec('SELECT COUNT(*) FROM AD_Window');  // first query = hydration complete
      var elapsed = Date.now() - t0;
      db.close();
      times.push(elapsed);
    }
    times.sort(function (a, b) { return a - b; });
    var median = times[Math.floor(times.length / 2)];
    var sizeKb = kb(inflated.byteLength);

    results.push({ label: tier.label, sizeKb: +sizeKb, ms: median });
    console.log('§COLD-LOAD tier="' + tier.label + '" sizeKB=' + sizeKb + ' ms=' + median + ' (median of ' + LOAD_RUNS + ')');
  }

  console.log('');

  // pass 1: baseline load under 500ms
  verdict(results[0].ms < 500, 'baseline cold-load under 500ms', 'ms=' + results[0].ms);

  // pass 2: 5x seed cold-load under 3s (acceptable UX ceiling)
  var last = results[results.length - 1];
  verdict(last.ms < 3000, '5x seed cold-load under 3s', 'ms=' + last.ms);

  // pass 3: scaling sub-quadratic (5x size should not cause >10x load time)
  var ratio = last.ms / Math.max(results[0].ms, 1);
  verdict(ratio < 10, 'load-time growth < 10x at 5x DB size', 'ratio=' + ratio.toFixed(1) + 'x');

  console.log('\n' + (fails === 0 ? '✅ W-BENCH-LOAD PASS' : '❌ W-BENCH-LOAD FAIL (' + fails + ' failures)'));
  process.exit(fails ? 1 : 0);
})();
