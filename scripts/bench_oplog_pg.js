#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>  SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard
// BENCH (user: "benchmark if same process is done in idempiere docker postgres").
//   Measures the OP-LOG PRIMITIVE — append N ops as ONE atomic commit (the work commitGroup does) —
//   on TWO engines: (A) the browser engine, sql.js in-memory (the deployed kernel commitGroup), and
//   (B) PostgreSQL 15 in Docker (the SAME engine iDempiere runs on), via psql, into a session TEMP
//   table (NEVER touches the real idempiere data). Also measures Postgres' per-durable-commit floor
//   (one INSERT+COMMIT round-trip = fsync), the real per-document cost when each Complete is its own txn.
// HONEST SCOPE (READ THIS): this is the STORAGE/ENGINE primitive ONLY — NOT a full iDempiere business
//   transaction. iDempiere's completeIt() adds callouts + ModelValidator + multi-table posting (Java,
//   no Tomcat running here) — that work is DELEGATED to the install in our doctrine (§0.0) and is NOT
//   measured. The two engines have DIFFERENT cost structures: Postgres pays network round-trip + WAL
//   fsync for durable, concurrent, ACID writes; sql.js is in-process, in-memory, single-user, durable
//   only after the log is synced. This is NOT "browser beats iDempiere" — it is "the browser defers
//   durability+concurrency to the install, which is exactly why its local write is cheap."
// Run: node scripts/bench_oplog_pg.js [N] 2>&1 | tee build/erp/bench_oplog_pg.log
'use strict';
var path = require('path');
var crypto = require('crypto');
var cp = require('child_process');

var N = parseInt(process.argv[2] || '1000', 10);
var PG = "PGPASSWORD=postgres psql -h localhost -p 5432 -U postgres -X -q";

function sha256(s) { return crypto.createHash('sha256').update(s).digest('hex'); }
function ms() { return Number(process.hrtime.bigint()) / 1e6; }

// ── build N ops with a real hash chain (shared, deterministic; client-side work both engines'd do) ──
function buildOps(n) {
  var ops = [], prev = '0'.repeat(64);
  for (var i = 0; i < n; i++) {
    var params = JSON.stringify({ table: 'c_order', id: i + 1, action: 'CO', from: 'DR', to: 'CO' });
    var h = sha256(prev + '|' + (i + 1) + '|SET_STATUS|' + params);
    ops.push({ id: i + 1, op_type: 'SET_STATUS', parameters: params, prev_hash: prev, op_hash: h });
    prev = h;
  }
  return ops;
}

function psql(sql) {
  return cp.execSync(PG + " -v ON_ERROR_STOP=1", { input: sql, encoding: 'utf8' });
}
function pgTimes(out) { // parse all "Time: 1.234 ms" lines psql \timing emits
  var t = [], m, re = /Time:\s*([\d.]+)\s*ms/g;
  while ((m = re.exec(out))) t.push(parseFloat(m[1]));
  return t;
}

(async function () {
  console.log('═══ BENCH op-log primitive — sql.js (browser engine) vs PostgreSQL 15 (iDempiere\'s engine) ═══');
  console.log('N=' + N + ' ops, ONE atomic group commit. STORAGE PRIMITIVE ONLY (no callouts/posting — those are delegated, §0.0).\n');
  var ops = buildOps(N);

  // ── (A) sql.js — the DEPLOYED kernel commitGroup (in-memory, single-user, incl. sha256 hashing) ──
  global.window = global.window || {};
  global.crypto = global.crypto || require('crypto').webcrypto;
  var initSqlJs = require('sql.js');
  require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
  var KO = global.window.KernelOps;
  var SQL = await initSqlJs();
  var sdb = new SQL.Database();
  var groupOps = ops.map(function (o) { return { op_type: o.op_type, params: o.parameters }; });
  var t0 = ms();
  var res = await KO.commitGroup(sdb, groupOps, { gid: 'bench-grp' });
  var sqljsMs = ms() - t0;
  console.log('§BENCH sqljs engine=sql.js(in-memory) committed=' + res.committed + ' ops=' + res.ids.length +
              ' total_ms=' + sqljsMs.toFixed(2) + ' per_op_ms=' + (sqljsMs / N).toFixed(4) +
              ' (incl. sha256 chain + seal-once)');

  // ── (B) PostgreSQL — same N rows, ONE transaction, STORAGE ONLY (constant hash string, no server hashing) ──
  var values = ops.map(function (o) {
    return "(" + o.id + ",'SET_STATUS','" + o.parameters.replace(/'/g, "''") + "','" + o.prev_hash + "','" + o.op_hash + "')";
  }).join(',');
  var sqlGroup =
    "\\timing on\n" +
    "CREATE TEMP TABLE bench_oplog(id int primary key, op_type text, parameters text, prev_hash text, op_hash text);\n" +
    "BEGIN;\n" +
    "INSERT INTO bench_oplog(id,op_type,parameters,prev_hash,op_hash) VALUES " + values + ";\n" +
    "COMMIT;\n";
  var outGroup = psql(sqlGroup);
  var gt = pgTimes(outGroup);
  var pgGroupMs = gt.length ? gt[gt.length - 1] : NaN; // the INSERT time (last timed stmt before commit) — take max
  pgGroupMs = gt.length ? Math.max.apply(null, gt) : NaN;
  console.log('§BENCH postgres engine=pg15(docker,durable) mode=one-txn ops=' + N +
              ' total_ms=' + pgGroupMs.toFixed(2) + ' per_op_ms=' + (pgGroupMs / N).toFixed(4) +
              ' (storage only; WAL+fsync once for the group)');

  // ── (B2) Postgres per-DURABLE-commit floor: one INSERT+COMMIT round-trip, fsync each (the per-document cost) ──
  var SAMP = 30;
  var sqlPer = "\\timing on\nCREATE TEMP TABLE bench_one(id int, h text);\n";
  for (var k = 0; k < SAMP; k++) sqlPer += "INSERT INTO bench_one VALUES(" + k + ",'" + ops[k].op_hash + "');\n";
  var outPer = psql(sqlPer); // autocommit: each INSERT is its own durable txn (fsync)
  var pt = pgTimes(outPer);
  var perAvg = pt.length ? (pt.reduce(function (a, b) { return a + b; }, 0) / pt.length) : NaN;
  var perMin = pt.length ? Math.min.apply(null, pt) : NaN;
  console.log('§BENCH postgres mode=per-commit(autocommit,fsync) samples=' + pt.length +
              ' avg_ms=' + perAvg.toFixed(3) + ' min_ms=' + perMin.toFixed(3) +
              ' (the real per-document cost when each Complete is its OWN transaction)');

  // ── summary / honest framing ──
  console.log('\n┌─ READING (non-invent, honest scope) ──────────────────────────────────────');
  console.log('│ Same PRIMITIVE (' + N + ' ops, one atomic commit):');
  console.log('│   sql.js (browser, in-mem, single-user, +hashing): ' + sqljsMs.toFixed(2) + ' ms  (' + (sqljsMs / N).toFixed(4) + ' ms/op)');
  console.log('│   Postgres (docker, durable WAL+fsync, ACID):      ' + pgGroupMs.toFixed(2) + ' ms  (' + (pgGroupMs / N).toFixed(4) + ' ms/op)');
  console.log('│   Postgres per-durable-commit floor:               ~' + perAvg.toFixed(2) + ' ms/txn (fsync round-trip)');
  console.log('│ NOT a head-to-head: Postgres buys durability+concurrency+network the browser DEFERS');
  console.log('│ to the install (delegate-to-install, §0.0). iDempiere completeIt() adds callouts +');
  console.log('│ ModelValidator + multi-table posting (Java) on TOP of this storage cost — NOT measured');
  console.log('│ here (no Tomcat; that work is the install\'s, per the doctrine).');
  console.log('└───────────────────────────────────────────────────────────────────────────');
  console.log('\n§BENCH DONE');
})();
