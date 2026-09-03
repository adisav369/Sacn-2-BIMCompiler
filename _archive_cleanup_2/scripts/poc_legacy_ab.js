#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_legacy_ab.js — the legacy A/B on THIS box (firm-up of the "1–3 orders faster" estimate).
 *   Runs the SAME representative document-complete write-set three ways and times each:
 *     A) Postgres 15 (the running docker container), one BEGIN..COMMIT per document, synchronous_commit=on
 *        → the DB-layer cost a "server on board" pays: process + local socket + MVCC + WAL fsync-per-commit.
 *     B) better-sqlite3 local, WAL + synchronous=FULL, one transaction per document
 *        → "if we kept SYNCHRONOUS durability, but local and embedded" (no server, no network).
 *     C) in-RAM op-group fold + hash-chain append, NO fsync
 *        → THIS architecture's critical path (durability is async, relayed later — the §19.6 trade).
 *
 *   The document-complete = the §18.8 op-group fan-out: 6 row-writes across order/invoice/lines/journal,
 *   IDENTICAL on all three engines so the comparison is fair.
 *
 *   HONEST FRAME (stated in the log): bar A is RAW Postgres — it EXCLUDES iDempiere's PO.java, OSGi, JDBC
 *   network, and model validation, ALL of which only make real legacy slower. So A is a CONSERVATIVE FLOOR
 *   on legacy; the speedup vs a full iDempiere stack is LARGER than what this prints. And C's win is partly
 *   the durability trade (A/B fsync per doc; C appends async) — B is included precisely to separate
 *   "server+network+MVCC" from "the fsync itself". Determinism: ids/amounts index-derived; TIME is measured.
 *
 * Run: node scripts/poc_legacy_ab.js 2>&1 | tee build/erp/poc_legacy_ab.log
 *   (requires the running `postgres` docker container; bar A is skipped + flagged if unreachable.)
 */
'use strict';
var crypto = require('crypto');
var fs = require('fs');
var cp = require('child_process');

function ms() { return Number(process.hrtime.bigint()) / 1e6; }
function f2(x) { return x.toFixed(2); }
function perSec(n, t) { return Math.round(n / (t / 1000)).toLocaleString('en-US'); }
function sha256(s) { return crypto.createHash('sha256').update(s).digest('hex'); }
function amtOf(k) { return ((k % 997) + 1) * 100; }                       // index-derived cents

// ── A) Postgres via `docker exec -i postgres psql` ─────────────────────────────────────────────
function runPostgres(N) {
  // a representative document-complete: 4 INSERT statements / 6 rows, in its own synchronous transaction.
  var lines = [
    'DROP TABLE IF EXISTS bx_fact, bx_iline, bx_inv, bx_ord;',
    'CREATE TABLE bx_ord(id int primary key, docstatus text, grandtotal int);',
    'CREATE TABLE bx_inv(id int primary key, ord_id int, grandtotal int);',
    'CREATE TABLE bx_iline(id serial primary key, inv_id int, qty int, price int);',
    'CREATE TABLE bx_fact(id serial primary key, account text, amount int);',
    'SET synchronous_commit=on;'
  ];
  for (var k = 1; k <= N; k++) {
    var a = amtOf(k);
    lines.push('BEGIN;',
      'INSERT INTO bx_ord VALUES(' + k + ",'CO'," + a + ');',
      'INSERT INTO bx_inv VALUES(' + k + ',' + k + ',' + a + ');',
      'INSERT INTO bx_iline(inv_id,qty,price) VALUES(' + k + ',1,' + a + '),(' + k + ',2,' + (a / 2 | 0) + ');',
      "INSERT INTO bx_fact(account,amount) VALUES('AR'," + a + "),('Revenue'," + (-a) + ');',
      'COMMIT;');
  }
  var tmp = '/tmp/bx_pgbench.sql'; fs.writeFileSync(tmp, lines.join('\n') + '\n');
  var execOpts = { stdio: ['pipe', 'ignore', 'pipe'], maxBuffer: 1 << 26 };
  // baseline: a trivial run measures the fixed psql/docker startup to SUBTRACT from the batch wall.
  var b0 = ms(); cp.execSync('docker exec -i postgres psql -U postgres -d postgres -q -v ON_ERROR_STOP=1 -c "SELECT 1;"', execOpts); var baseline = ms() - b0;
  var t0 = ms();
  cp.execSync('docker exec -i postgres psql -U postgres -d postgres -q -v ON_ERROR_STOP=1 -f - < ' + tmp, { shell: '/bin/bash', stdio: ['pipe', 'ignore', 'pipe'], maxBuffer: 1 << 26 });
  var wall = ms() - t0;
  cp.execSync('docker exec -i postgres psql -U postgres -d postgres -q -c "DROP TABLE IF EXISTS bx_fact,bx_iline,bx_inv,bx_ord;"', execOpts);
  return { ms: Math.max(wall - baseline, 0.001), rawWall: wall, baseline: baseline };
}

// ── B) better-sqlite3 local, synchronous durability (WAL + synchronous=FULL) ───────────────────
function runSqlite(N) {
  var Database = require('better-sqlite3');
  try { fs.unlinkSync('/tmp/bx_legacy.db'); fs.unlinkSync('/tmp/bx_legacy.db-wal'); fs.unlinkSync('/tmp/bx_legacy.db-shm'); } catch (e) {}
  var db = new Database('/tmp/bx_legacy.db');
  db.pragma('journal_mode=WAL'); db.pragma('synchronous=FULL');           // fsync per commit (durable)
  db.exec('CREATE TABLE bx_ord(id int primary key, docstatus text, grandtotal int);' +
          'CREATE TABLE bx_inv(id int primary key, ord_id int, grandtotal int);' +
          'CREATE TABLE bx_iline(id integer primary key, inv_id int, qty int, price int);' +
          'CREATE TABLE bx_fact(id integer primary key, account text, amount int);');
  var oi = db.prepare('INSERT INTO bx_ord VALUES(?,?,?)'), ii = db.prepare('INSERT INTO bx_inv VALUES(?,?,?)');
  var li = db.prepare('INSERT INTO bx_iline(inv_id,qty,price) VALUES(?,?,?)'), fi = db.prepare('INSERT INTO bx_fact(account,amount) VALUES(?,?)');
  var doc = db.transaction(function (k, a) { oi.run(k, 'CO', a); ii.run(k, k, a); li.run(k, 1, a); li.run(k, 2, a / 2 | 0); fi.run('AR', a); fi.run('Revenue', -a); });
  var t0 = ms(); for (var k = 1; k <= N; k++) doc(k, amtOf(k)); var wall = ms() - t0;
  db.close();
  return { ms: wall };
}

// ── C) in-RAM op-group fold + hash-chain append (this architecture; async durability) ──────────
function runInRam(N) {
  var GEN = '0'.repeat(64);
  var t0 = ms(), prev = GEN, bal = {};
  for (var k = 1; k <= N; k++) {
    var a = amtOf(k);
    // the op-group = 6 ops mirroring the same writes; apply (fold) + append (chain) each.
    var group = [
      { t: 'DOC_ACTION', p: 'ord|' + k + '|CO' }, { t: 'CRUD_CREATE', p: 'inv|' + k + '|' + a },
      { t: 'CRUD_CREATE', p: 'iline|' + k + '|1|' + a }, { t: 'CRUD_CREATE', p: 'iline|' + k + '|2|' + (a / 2 | 0) },
      { t: 'CRUD_CREATE', p: 'fact|AR|' + a }, { t: 'CRUD_CREATE', p: 'fact|Revenue|' + (-a) }
    ];
    for (var j = 0; j < group.length; j++) {
      prev = sha256(prev + '|' + group[j].t + '|' + group[j].p);          // append: hash-chain
      if (group[j].p.indexOf('fact|') === 0) { var parts = group[j].p.split('|'); bal[parts[1]] = (bal[parts[1]] || 0) + Number(parts[2]); }  // fold
    }
  }
  return { ms: ms() - t0, tip: prev, bal: bal };
}

(function () {
  console.log('═══ POC-LEGACY-AB — same document-complete, three ways, on this box ═══');
  console.log('document-complete = §18.8 op-group: 6 row-writes (order/invoice/2 lines/2 journal), identical across engines\n');

  var rows = {};
  // bar A — Postgres (conservative legacy FLOOR; excludes iDempiere ORM/OSGi/JDBC).
  var NPG = 2000;
  try {
    var pg = runPostgres(NPG); rows.pg = { N: NPG, ms: pg.ms, dps: NPG / (pg.ms / 1000), perDoc: pg.ms / NPG };
    console.log('§AB-PG n=' + NPG + ' docs/s=' + perSec(NPG, pg.ms) + ' perDocMs=' + f2(pg.ms / NPG) + ' (sync_commit=on; baselineMs=' + f2(pg.baseline) + ' subtracted; rawWallMs=' + f2(pg.rawWall) + ')');
    console.log('   ↳ RAW Postgres only — excludes iDempiere PO.java/OSGi/JDBC-network/model-validation → a CONSERVATIVE FLOOR; real legacy is slower.');
  } catch (e) {
    console.log('§AB-PG SKIPPED reason="' + (e && e.message ? e.message.split('\n')[0] : e) + '" (postgres container unreachable)');
  }

  // bar B — local SQLite with SYNCHRONOUS durability.
  var NSL = 5000; var sl = runSqlite(NSL); rows.sl = { N: NSL, ms: sl.ms, dps: NSL / (sl.ms / 1000), perDoc: sl.ms / NSL };
  console.log('§AB-SQLITE n=' + NSL + ' docs/s=' + perSec(NSL, sl.ms) + ' perDocMs=' + f2(sl.ms / NSL) + ' (better-sqlite3, WAL + synchronous=FULL → fsync/commit; no server, no network)');

  // bar C — in-RAM fold + chain append (async durability — the §19.6 trade).
  var NIR = 50000; var ir = runInRam(NIR); rows.ir = { N: NIR, ms: ir.ms, dps: NIR / (ir.ms / 1000), perDoc: ir.ms / NIR };
  console.log('§AB-INRAM n=' + NIR + ' docs/s=' + perSec(NIR, ir.ms) + ' perDocMs=' + f2(ir.ms / NIR) + ' (op-group fold + hash-chain append; durability ASYNC, relayed later)');

  // ── ratios ──
  console.log('\n§AB-RATIO (per-document latency, lower=faster):');
  if (rows.pg) console.log('   in-RAM vs Postgres-sync  = ' + f2(rows.pg.perDoc / rows.ir.perDoc) + '× faster   (' + f2(rows.pg.perDoc) + 'ms → ' + f2(rows.ir.perDoc) + 'ms)');
  console.log('   in-RAM vs SQLite-sync    = ' + f2(rows.sl.perDoc / rows.ir.perDoc) + '× faster   (' + f2(rows.sl.perDoc) + 'ms → ' + f2(rows.ir.perDoc) + 'ms)');
  if (rows.pg) console.log('   SQLite-sync vs Postgres  = ' + f2(rows.pg.perDoc / rows.sl.perDoc) + '× faster   (isolates server+socket+MVCC from the fsync itself)');
  console.log('\n   reading: in-RAM removes BOTH the server round-trip (Postgres→SQLite gap) AND the synchronous fsync (SQLite→in-RAM gap).');
  console.log('   the fsync gap is the §19.6 durability trade (async convergence); the server gap is pure win. Real iDempiere adds ORM/OSGi on top of bar A → the true speedup exceeds the in-RAM-vs-Postgres figure above.');

  console.log('\n§AB ' + (rows.pg ? 'DONE' : 'PARTIAL (PG skipped)') + ' — measured the legacy floor on this box; in-RAM critical path is ' + (rows.pg ? f2(rows.pg.perDoc / rows.ir.perDoc) + '× faster than raw Postgres and ' : '') + f2(rows.sl.perDoc / rows.ir.perDoc) + '× faster than even local synchronous SQLite. The win is server-removal + the async-durability trade, named honestly.');
  process.exit(0);
})();
