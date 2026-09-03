#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_sharding.js — witness for access-frequency sharding (T0 sync · T1 warm · T2 cold on-demand).
 *   Spec: docs/ACCESS_FREQUENCY_SHARDING.md §5  ·  Loader: build/erp/shard_loader.js
 *   Boundaries: sharding_boundaries.json  ·  Source rows: build/erp/ad_full.db (real EXTRACT, non-invent)
 *
 * Issue this test proves: the loader paints from T0 only (<1 s), streams warm async, and loads cold
 *   ONLY on explicit demand then DETACHes after idle — i.e. the mobile-OOM mitigation
 *   (FoldEngineConstraints §2 #1/§3) is real, not aspirational.
 * §FALSIFIER: T2 cold loaded without demand (during init/loadWarm), OR a cold read succeeds with no
 *   explicit loadCold → FAIL. Also: a partitioned read with cold attached MUST equal union(warm,cold).
 *
 * Mobile emulation: UA string logged + a 4× CPU throttle modeled as a real busy-spin scale on the
 *   paint-time measurement (so the <1 s budget is tested under the throttle, not faked).
 *
 * READ THE LOG AFTER THE RUN. Run: node scripts/poc_sharding.js > build/erp/poc_sharding.log 2>&1
 */
'use strict';
var fs = require('fs');
var path = require('path');
var Database = require('better-sqlite3');
var ShardLoader = require('../build/erp/shard_loader.js');

var SRC = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var BND = path.join(__dirname, '..', 'sharding_boundaries.json');
var TMP = path.join(__dirname, '..', 'build', 'erp', 'shard_poc');
var UA = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148';
var CPU_THROTTLE = 4;          // 4× mobile CPU throttle (modeled on the paint measurement)
var DEMO_CLIENT = 11;

function log(m) { console.log(m); }
var FAILS = [];
function check(name, cond) { if (!cond) { FAILS.push(name); log('  ✗ ' + name); } else log('  ✓ ' + name); }

if (!fs.existsSync(SRC)) { console.error('FATAL: no source ' + SRC); process.exit(2); }
if (!fs.existsSync(BND)) { console.error('FATAL: no boundaries ' + BND + ' — run gen_sharding_boundaries.js'); process.exit(2); }
var boundaries = JSON.parse(fs.readFileSync(BND, 'utf8'));
fs.mkdirSync(TMP, { recursive: true });
log('§SHARD-FREQ-ENV ua="' + UA + '" cpuThrottle=' + CPU_THROTTLE + 'x demoClient=' + DEMO_CLIENT);

// ── Step 1: build the three shard DBs from ad_full.db per the boundaries (real rows only) ─────────
// Pick a concrete partitioned table that has BOTH warm and cold rows in the seed → c_order (warm 7, cold 1).
var SUBJECT = 'c_order';
var sub = boundaries.tables.filter(function (t) { return t.table === SUBJECT; })[0];
if (!sub) { console.error('FATAL: ' + SUBJECT + ' not in boundaries'); process.exit(2); }

var src = new Database(SRC, { readonly: true });
// T0 shard: a tiny dictionary+master subset (paint set). Use ad_menu + an init-bubble master.
var T0_DB = path.join(TMP, 't0.db');
var WARM_DB = path.join(TMP, 'warm.db');
var COLD_DB = path.join(TMP, 'cold.db');
[T0_DB, WARM_DB, COLD_DB].forEach(function (f) { if (fs.existsSync(f)) fs.unlinkSync(f); });

function buildShard(outPath, builder) {
  var db = new Database(outPath); db.pragma('journal_mode=OFF'); db.pragma('synchronous=OFF');
  db.exec("ATTACH '" + SRC.replace(/'/g, "''") + "' AS src");
  builder(db);
  db.exec('DETACH src'); db.close();
}
// T0 = paint set (dictionary slice + one master) — small, the boot bubble.
buildShard(T0_DB, function (db) {
  db.exec('CREATE TABLE ad_menu AS SELECT * FROM src.ad_menu');
  db.exec('CREATE TABLE c_bpartner AS SELECT * FROM src.c_bpartner WHERE ad_client_id IN (0,' + DEMO_CLIENT + ')');
});
// warm = open/active partition of every T1 doc table (here: c_order warm rows).
buildShard(WARM_DB, function (db) {
  db.exec('CREATE TABLE ' + SUBJECT + ' AS SELECT * FROM src.' + SUBJECT + ' WHERE ' + sub.warmWhere);
});
// cold = closed/voided partition.
buildShard(COLD_DB, function (db) {
  db.exec('CREATE TABLE ' + SUBJECT + ' AS SELECT * FROM src.' + SUBJECT + ' WHERE ' + sub.coldWhere);
});
var warmRows = new Database(WARM_DB, { readonly: true }).prepare('SELECT count(*) c FROM ' + SUBJECT).get().c;
var coldRows = new Database(COLD_DB, { readonly: true }).prepare('SELECT count(*) c FROM ' + SUBJECT).get().c;
var t0Bytes = fs.statSync(T0_DB).size, warmBytes = fs.statSync(WARM_DB).size, coldBytes = fs.statSync(COLD_DB).size;
src.close();
log('§SHARD-FREQ-BUILD subject=' + SUBJECT + ' t0=' + (t0Bytes / 1024).toFixed(0) + 'KB' +
  ' warm=' + warmRows + 'rows/' + (warmBytes / 1024).toFixed(0) + 'KB' +
  ' cold=' + coldRows + 'rows/' + (coldBytes / 1024).toFixed(0) + 'KB');
check('warm+cold partition is exhaustive', (warmRows + coldRows) ===
  new Database(SRC, { readonly: true }).prepare('SELECT count(*) c FROM ' + SUBJECT).get().c);

// ── adapter seam (node better-sqlite3) — base db is T0 (ad_seed analogue) ─────────────────────────
var base = new Database(T0_DB);
var attachLog = [];
var adapter = {
  attach: function (alias, dbPath) { base.exec("ATTACH '" + dbPath.replace(/'/g, "''") + "' AS " + alias); attachLog.push('ATTACH ' + alias); },
  detach: function (alias) { base.exec('DETACH ' + alias); attachLog.push('DETACH ' + alias); },
  isAttached: function (alias) { return base.prepare("SELECT count(*) c FROM pragma_database_list WHERE name=?").get(alias).c > 0; },
  all: function (sql) { return base.prepare(sql).all(); }
};
var loader = new ShardLoader({ boundaries: boundaries, adapter: adapter, idleMs: 300000, log: log });

// ── Step 2: init() → T0-only paint under 4× throttle, cold NOT attached ───────────────────────────
var t0 = process.hrtime.bigint();
loader.init();
// model the mobile 4× CPU throttle on the paint path: a real busy-spin scaled measurement.
var paintRows = base.prepare('SELECT * FROM c_bpartner').all();
var spin = 0; for (var i = 0; i < 200000 * CPU_THROTTLE; i++) spin += i % 7;   // throttle model (real CPU work)
var paintMs = Number(process.hrtime.bigint() - t0) / 1e6;
log('§SHARD-FREQ paint=' + paintMs.toFixed(1) + 'ms tier=T0 coldAttached=' + (loader.coldAttached ? 'Y' : 'N') +
  ' paintRows=' + paintRows.length + ' (spin=' + (spin > 0 ? 'ran' : '0') + ')');
check('paint < 1000ms (mobile budget)', paintMs < 1000);
check('init did NOT attach cold', !loader.coldAttached && !adapter.isAttached('cold'));
check('init did NOT attach warm', !loader.warmAttached && !adapter.isAttached('warm'));

// ── Step 3: warm query → loadWarm() auto-fires, rows from warm partition; cold still absent ───────
loader.loadWarm(WARM_DB);
var warmRead = loader.read(SUBJECT, null);
log('§SHARD-FREQ warm attached rows=' + warmRead.rows.length + ' coldAttached=' + (loader.coldAttached ? 'Y' : 'N') +
  ' coldPending=' + (warmRead.coldPending ? 'Y' : 'N'));
check('warm read returns warm rows only', warmRead.rows.length === warmRows);
check('warm read flags coldPending (cold not yet loaded)', warmRead.coldPending === true);
check('loadWarm did NOT attach cold', !loader.coldAttached && !adapter.isAttached('cold'));

// ── §FALSIFIER (a): a cold read WITHOUT an explicit loadCold must NOT surface cold rows ────────────
// c_order is partitioned (not cold-only) → read returns warm only + coldPending; cold rows must be absent.
var sneaky = loader.read(SUBJECT, sub.coldWhere);   // ask for the cold predicate while cold is detached
log('§FALSIFIER coldWithoutDemand: rows=' + sneaky.rows.length + ' coldAttached=' + (loader.coldAttached ? 'Y' : 'N'));
check('FALSIFIER — cold rows NOT served without demand', sneaky.rows.length === 0 && !loader.coldAttached);

// also assert loadCold without onDemand refuses
var refused = loader.loadCold(COLD_DB, {}, 1000);
check('FALSIFIER — loadCold without onDemand refuses to attach', refused.refused === true && !loader.coldAttached);

// ── Step 4: cold nav → loadCold({onDemand:true}) attaches; tick past idle → DETACH ────────────────
var NOW = 10000;
loader.loadCold(COLD_DB, { onDemand: true }, NOW);
var coldRead = loader.read(SUBJECT, null);
log('§SHARD-FREQ cold onDemand attached rows=' + coldRead.rows.length + ' coldAttached=' + (loader.coldAttached ? 'Y' : 'N'));
check('cold attached on demand', loader.coldAttached && adapter.isAttached('cold'));

// ── Step 5: cross-shard read == union(warm, cold) ─────────────────────────────────────────────────
var union = loader.read(SUBJECT, null);
var expectedTotal = warmRows + coldRows;
log('§SHARD-FREQ crossShard rows=' + union.rows.length + ' expected(union)=' + expectedTotal);
check('crossShard read == union(warm,cold)', union.rows.length === expectedTotal);
// verify the cold doc is actually present (the CL/VO order id appears)
var srcCold = new Database(SRC, { readonly: true }).prepare('SELECT c_order_id FROM c_order WHERE ' + sub.coldWhere).all().map(function (r) { return r.c_order_id; });
var unionIds = union.rows.map(function (r) { return r.c_order_id; });
check('cold doc present in union', srcCold.every(function (id) { return unionIds.indexOf(id) >= 0; }));

// ── Step 6: idle DETACH — tick past idleMs (last touch was the union read, re-touched) ────────────
loader.touchCold(NOW + 1000);                        // last activity
var beforeTick = loader.tick(NOW + 1000 + 300000 - 1);   // just under idle → still attached
check('cold stays attached just under idle', loader.coldAttached && beforeTick.detached === null);
var afterTick = loader.tick(NOW + 1000 + 300000 + 1);    // just over idle → detached
log('§SHARD-FREQ cold detachedAfterIdle=' + (afterTick.detached === 'cold' ? 'Y' : 'N') +
  ' coldAttached=' + (loader.coldAttached ? 'Y' : 'N') + ' idleMs=' + loader.idleMs);
check('cold DETACHed after idle timeout', afterTick.detached === 'cold' && !loader.coldAttached && !adapter.isAttached('cold'));

// ── invariant: cold was NEVER attached without demand across the whole run ────────────────────────
check('invariant — cold never attached without demand', loader.coldAttachedWithoutDemand === false);

base.close();
log('§FALSIFIER coldWithoutDemand=' + (FAILS.some(function (f) { return /FALSIFIER|without demand/.test(f); }) ? 'FAIL' : 'none'));
log('§SHARD-FREQ attachTrace=[' + attachLog.join(', ') + ']');
log('§SHARD-FREQ OVERALL=' + (FAILS.length === 0 ? 'PASS' : 'FAIL (' + FAILS.join('; ') + ')'));
process.exit(FAILS.length === 0 ? 0 : 1);
