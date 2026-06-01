#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_remote_pos.js — the REMOTE breakdown (POS sale → admin at HQ; 10k batch → graphs).
 *   The on-box A/B (poc_legacy_ab.js) showed Postgres ≈ local SQLite (~1ms, both fsync-bound) — so the
 *   ~100× there was the DURABILITY trade, not server-removal. REMOTE is where server-removal pays off:
 *   legacy talks to a CENTRAL DB over a WAN, paying a synchronous round-trip PER statement; this design
 *   applies the op-group LOCALLY (in-RAM) and RELAYS the signed group async (DistributedERP.md §6).
 *
 *   Method = MEASURED locals + a TRANSPARENT network model (no hidden constants):
 *     - in-RAM apply (POS op-group, and 10k-record batch) — measured here, this run.
 *     - sql.js graph-feed queries over a 10k-row projection — measured here (the "nice graphs" data).
 *     - Postgres server-side per-doc proc+fsync = 1.03 ms — MEASURED in poc_legacy_ab.js (same box, same
 *       6-write doc), reused as a labelled constant so we don't re-spawn docker.
 *     - network: RTT is the one knob, shown across LAN / metro / cross-region / intercontinental.
 *
 *   Legacy round-trips per document R: a naive JDBC driver does one per statement; our doc = 6 writes, so
 *   R=6 is CONSERVATIVE — a real iDempiere completeIt() adds many validation SELECTs + model loads (R≈20–50+),
 *   making the legacy figure here a FLOOR. Determinism: data index-derived; TIME measured (process.hrtime).
 *
 * Run: node scripts/poc_remote_pos.js 2>&1 | tee build/erp/poc_remote_pos.log
 */
'use strict';
var crypto = require('crypto');
var fs = require('fs');
var cp = require('child_process');
var initSqlJs = require('sql.js');

function ms() { return Number(process.hrtime.bigint()) / 1e6; }
function f2(x) { return x.toFixed(2); }
function f0(x) { return Math.round(x).toLocaleString('en-US'); }
function sha256(s) { return crypto.createHash('sha256').update(s).digest('hex'); }
function bench(fn, reps) { fn(); var best = Infinity; for (var i = 0; i < (reps || 4); i++) { var t = ms(); fn(); var d = ms() - t; if (d < best) best = d; } return best; }
function amtOf(k) { return ((k % 997) + 1) * 100; }

// ── MEASURED constant from poc_legacy_ab.js (same box, same 6-write document-complete, fsync/commit) ──
var PG_PROC_SYNC_MS = 1.03;       // Postgres per-doc proc + WAL fsync, OWN transaction (EXCLUDES network)
var RTTS = [{ name: 'LAN', rtt: 0.5 }, { name: 'metro (same city)', rtt: 10 }, { name: 'cross-region', rtt: 50 }, { name: 'intercontinental', rtt: 150 }];

// measure Postgres doing N documents (6 writes each) in ONE transaction — the FAIR batch case (fsync amortised).
function runPgBatch(N) {
  var L = ['DROP TABLE IF EXISTS bx_fact,bx_iline,bx_inv,bx_ord;',
    'CREATE TABLE bx_ord(id int primary key, docstatus text, grandtotal int);',
    'CREATE TABLE bx_inv(id int primary key, ord_id int, grandtotal int);',
    'CREATE TABLE bx_iline(id serial primary key, inv_id int, qty int, price int);',
    'CREATE TABLE bx_fact(id serial primary key, account text, amount int);',
    'SET synchronous_commit=on;', 'BEGIN;'];
  for (var k = 1; k <= N; k++) { var a = amtOf(k); L.push('INSERT INTO bx_ord VALUES(' + k + ",'CO'," + a + ');', 'INSERT INTO bx_inv VALUES(' + k + ',' + k + ',' + a + ');', 'INSERT INTO bx_iline(inv_id,qty,price) VALUES(' + k + ',1,' + a + '),(' + k + ',2,' + (a / 2 | 0) + ');', "INSERT INTO bx_fact(account,amount) VALUES('AR'," + a + "),('Revenue'," + (-a) + ');'); }
  L.push('COMMIT;');
  fs.writeFileSync('/tmp/bx_pgbatch.sql', L.join('\n') + '\n');
  var o = { stdio: ['pipe', 'ignore', 'pipe'], maxBuffer: 1 << 27 };
  var b0 = ms(); cp.execSync('docker exec -i postgres psql -U postgres -d postgres -q -c "SELECT 1;"', o); var base = ms() - b0;
  var t0 = ms(); cp.execSync('docker exec -i postgres psql -U postgres -d postgres -q -v ON_ERROR_STOP=1 -f - < /tmp/bx_pgbatch.sql', { shell: '/bin/bash', stdio: ['pipe', 'ignore', 'pipe'], maxBuffer: 1 << 27 }); var wall = ms() - t0;
  cp.execSync('docker exec -i postgres psql -U postgres -d postgres -q -c "DROP TABLE IF EXISTS bx_fact,bx_iline,bx_inv,bx_ord;"', o);
  return Math.max(wall - base, 0.001);
}

(async function () {
  var SQL = await initSqlJs();
  console.log('═══ POC-REMOTE-POS — remote POS sale + 10k batch + graphs: ours vs central-DB legacy ═══');
  console.log('MEASURED locals + a TRANSPARENT network model. PG per-doc(sync)=' + PG_PROC_SYNC_MS + 'ms (poc_legacy_ab); PG batch measured below. EXCLUDES iDempiere ORM/OSGi → legacy is a FLOOR.\n');

  // ── measure local in-RAM apply: a POS sale op-group (ring + complete + pay ≈ 4 ops) ──
  var SALE = 100000;
  var tSale = bench(function () { var prev = '0'.repeat(64), bal = 0; for (var i = 0; i < SALE; i++) { var ops = ['DOC_ACTION|ord|CO', 'CRUD_CREATE|line|' + i, 'CRUD_CREATE|pay|' + i, 'CRUD_CREATE|fact|' + (i % 997)]; for (var j = 0; j < ops.length; j++) { prev = sha256(prev + '|' + ops[j]); if (ops[j].indexOf('fact') > -1) bal += (i % 997); } } }, 3);
  var saleMs = tSale / SALE;        // per-sale local apply (fold + 4-op hash-chain), no network, no fsync
  console.log('§RPOS-LOCAL posSaleApplyMs=' + f2(saleMs) + ' (in-RAM op-group fold + hash-chain; ' + f0(1 / (saleMs / 1000)) + ' sales/s local)');

  // ── ours, 10k batch: build the sql.js projection (10k docs × 6 rows) + chain + the graph-feed queries ──
  var db = new SQL.Database();
  db.run('CREATE TABLE bx_ord(id INTEGER PRIMARY KEY, ds TEXT, gt INT);CREATE TABLE bx_inv(id INTEGER PRIMARY KEY, ord INT, gt INT);CREATE TABLE bx_iline(id INTEGER PRIMARY KEY, inv INT, qty INT, price INT);CREATE TABLE bx_fact(id INTEGER PRIMARY KEY, account TEXT, amount INT);');
  var so = db.prepare('INSERT INTO bx_ord VALUES(?,?,?)'), si = db.prepare('INSERT INTO bx_inv VALUES(?,?,?)'), sl = db.prepare('INSERT INTO bx_iline(inv,qty,price) VALUES(?,?,?)'), sf = db.prepare('INSERT INTO bx_fact(account,amount) VALUES(?,?)');
  var prevC = '0'.repeat(64);
  var tBatch = ms(); db.run('BEGIN');
  for (var i = 1; i <= 10000; i++) { var a = amtOf(i); so.run(i, 'CO', a); si.run(i, i, a); sl.run(i, 1, a); sl.run(i, 2, a / 2 | 0); sf.run('AR', a); sf.run('Revenue', -a); prevC = sha256(prevC + '|doc|' + i + '|' + a); }   // 6 rows + 1 chain link per doc
  db.run('COMMIT'); var batchApplyMs = ms() - tBatch; so.free(); si.free(); sl.free(); sf.free();
  var tGraph = bench(function () {
    db.exec("SELECT account, SUM(amount) FROM bx_fact GROUP BY account");                       // ledger by account
    db.exec("SELECT (gt/5000) AS bucket, COUNT(*) FROM bx_ord GROUP BY bucket ORDER BY bucket"); // order-size histogram
    db.exec("SELECT inv, SUM(qty*price) AS s FROM bx_iline GROUP BY inv ORDER BY s DESC LIMIT 10"); // top invoices
  });
  var oursB = batchApplyMs + tGraph;
  console.log('§RPOS-LOCAL batch10kApplyMs=' + f2(batchApplyMs) + ' graphQueries3xMs=' + f2(tGraph) + ' → oursBatchTotal=' + f2(oursB) + 'ms (local sql.js; 0 network)');

  // ── measure the FAIR legacy batch: 10k docs in ONE Postgres transaction (fsync amortised) ──
  var pgBatchMs = null;
  try { pgBatchMs = runPgBatch(10000); console.log('§RPOS-PGBATCH pg10kOneTxnMs=' + f2(pgBatchMs) + ' (server compute + ONE fsync; EXCLUDES network)'); }
  catch (e) { console.log('§RPOS-PGBATCH SKIPPED reason="' + (e && e.message ? e.message.split('\n')[0] : e) + '"'); }

  // ════ SCENARIO A — one POS sale, order visible to admin at the remote HQ ════
  console.log('\n── Scenario A: cashier rings ONE sale; HQ admin must see the order (cashier-perceived latency) ──');
  console.log('   legacy modelled two ways: BATCHED client (R=1 round-trip, the smart case) and NAIVE (R=6, one per statement):');
  RTTS.forEach(function (s) {
    var legacyBatched = PG_PROC_SYNC_MS + 1 * s.rtt;     // smart client: whole doc in 1 stored-proc round-trip
    var legacyNaive = PG_PROC_SYNC_MS + 6 * s.rtt;       // naive: a round-trip per statement
    console.log('     ' + s.name.padEnd(18) + ' RTT=' + (s.rtt + 'ms').padEnd(7) + ' legacy=' + (f2(legacyBatched) + '–' + f2(legacyNaive) + 'ms').padEnd(16) + ' ours=' + (f2(saleMs) + 'ms').padEnd(8) + ' → ' + f0(legacyBatched / saleMs) + '–' + f0(legacyNaive / saleMs) + '× faster');
  });
  console.log('   HQ convergence: legacy = the instant the cashier finished waiting (it WAS the central write); ours = ' + f2(saleMs) + 'ms local + one-way relay (RTT/2), ASYNC — off the till\'s critical path.');
  console.log('   OFFLINE (the structural difference): link down → legacy till CANNOT sell (it blocks on the central DB); ours sells locally and converges when the link returns (§6 facilitator just orders+relays).');

  // ════ SCENARIO B — initiate a 10k-record batch, receive graphs ════
  console.log('\n── Scenario B: process 10,000 documents, return 3 graphs (end-to-end wall) ──');
  RTTS.forEach(function (s) {
    var legacyNaive = 10000 * (PG_PROC_SYNC_MS + 6 * s.rtt) + 3 * s.rtt;          // client loops per-record (pathological remote)
    var legacyBatch = pgBatchMs != null ? (s.rtt + pgBatchMs + 3 * s.rtt) : null; // server-side job: kick + measured compute + 3 graph round-trips
    console.log('     ' + s.name.padEnd(18) + ' RTT=' + (s.rtt + 'ms').padEnd(7) + ' legacy(naive)=' + (f0(legacyNaive) + 'ms').padEnd(12) + ' legacy(server-batch)=' + ((legacyBatch != null ? f0(legacyBatch) + 'ms' : 'n/a').padEnd(10)) + ' ours=' + (f2(oursB) + 'ms').padEnd(9));
  });
  if (pgBatchMs != null) {
    var lb50 = 50 + pgBatchMs + 3 * 50;
    console.log('   FAIR comparison (server-side batch vs ours, cross-region 50ms): legacy≈' + f0(lb50) + 'ms vs ours≈' + f2(oursB) + 'ms → ' + f2(lb50 / oursB) + '× faster.');
    console.log('   honest read: in a proper server batch the COMPUTE is comparable (both ~tens–hundreds of ms for 10k); the legacy overhead is the network (kick + a round-trip PER chart) + the fact graphs re-query the REMOTE DB. Ours renders all 3 charts from the LOCAL projection, 0 round-trips. The pathological gap (naive) is real only if a client loops per-record over the WAN.');
  }

  console.log('\n§RPOS DONE — remote is where server-removal shows: a single sale is RTT-bound for legacy (tens–hundreds of ms, and it BLOCKS when offline) vs ~' + f2(saleMs) + 'ms local + async relay for ours. A 10k batch+graphs: comparable COMPUTE, but ours pays no network and re-queries nothing — ' + (pgBatchMs != null ? f2((50 + pgBatchMs + 150) / oursB) + '× at 50ms RTT' : 'n/a') + '. Legacy figures EXCLUDE iDempiere ORM/OSGi → a floor.');
  process.exit(0);
})();
