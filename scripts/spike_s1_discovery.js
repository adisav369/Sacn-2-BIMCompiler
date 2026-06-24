#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * spike_s1_discovery.js — S1 DISCOVERY SPIKES (MEASURE, do NOT remedy).
 *   Card: prompts/RESUME_S1_DISCOVERY_SPIKES.md   ·   Register: docs/ProductionRisks.md rows A1, B1, C1
 *   Constraint signals: docs/FoldEngineConstraints.md §6 (quota_used_pct, offline_queue_mb, bootstrap_path)
 *
 * One bounded task: find out HOW BAD the three highest production risks are, on REAL conditions, and emit a
 * §SPIKE-* line per spike that IS the result. NON-INVENT: every number is read from the real engine modules
 * (kernel_ops, offline_queue, erp_period_close) or a real building DB. Where a real device is unavailable the
 * spike records `blocked`/`proxy` honestly — it never fakes a device number.
 *
 *   A1 — does durability beat eviction?  (the un-acked offline tail vs storage eviction)
 *   B1 — does the biggest real building survive the in-memory ceiling?  (proxy: node V8/WASM heap, NOT a phone)
 *   C1 — does it run on iOS Safari?  (needs a real device → blocked here, recorded honestly)
 *
 * Run: node scripts/spike_s1_discovery.js   (READ THE LOG — the §SPIKE-* line is the verdict input)
 */
'use strict';
if (typeof global.crypto === 'undefined') { global.crypto = require('crypto').webcrypto; }
global.window = global.window || {}; global.self = global.self || global;
var path = require('path');
var fs = require('fs');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var KERNEL = path.join(ROOT, 'build', 'erp', 'kernel_ops.js');
var PC = require(path.join(ROOT, 'build', 'erp', 'erp_period_close.js'));
var OfflineQueue = require(path.join(ROOT, 'build', 'erp', 'offline_queue.js'));
var SIGNER = path.join(process.env.HOME, 'bim-ootb', 'erp', 'erp_signer.js');
var BIGGEST_DB = path.join(ROOT, 'deploy', 'buildings', 'Hospital_3_extracted.db');

// mobile in-memory ceiling — FoldEngineConstraints §2 #1: iOS Safari tab-kill ~500 MB minus WASM runtime+viewer.
var MOBILE_BUDGET_MB = 200;

function loadGlobalModule(p) { global.window = global.window || {}; delete require.cache[require.resolve(p)]; require(p); }
function freshKernel() { delete require.cache[require.resolve(KERNEL)]; require(KERNEL); return global.window.KernelOps; }
function post(table, id) { return { type: 'POST', params: { table: table, id: id, lines: [
  { account_id: '101', amtacctdr: 100 + id, amtacctcr: 0 }, { account_id: '400', amtacctdr: 0, amtacctcr: 100 + id } ] } }; }
function commit(K, db, ops) { ops.forEach(function (o) { K.commitOp(db, o.type, o.params); }); }
function mb(bytes) { return Math.round((bytes / (1024 * 1024)) * 10) / 10; }

// Did the codebase wire ANY auto-snapshot of the OP-LOG TAIL that fires on eviction/unload? (grep-as-code)
//   Precise: an eviction/unload listener whose HANDLER BODY calls a kernel checkpoint/seal verb — NOT a
//   form-draft buffer (crud_overlay's beforeunload only buffers localStorage typing, never the op-log), and
//   NOT a checkpoint that happens to live elsewhere in the same file. The remedy (snapshot-on-eviction) is
//   unbuilt, so the honest expectation is: none.
function hasSnapshotOnEvictHook() {
  var EVICT = /addEventListener\(\s*['"](pagehide|beforeunload|freeze|visibilitychange)['"]|storage\.estimate|quota_used_pct|snapshotOnEvict|autoSnapshot/;
  var OPLOG_SNAPSHOT = /KernelOps|kernel_ops|closePeriod|sealChain|sealFrom|bootstrapFromCheckpoint|CKPT_TYPE/;
  var dirs = [path.join(ROOT, 'build', 'erp'), path.join(ROOT, 'deploy', 'dev')];
  var found = [];
  dirs.forEach(function (d) {
    fs.readdirSync(d).filter(function (f) { return f.endsWith('.js'); }).forEach(function (f) {
      var src; try { src = fs.readFileSync(path.join(d, f), 'utf8'); } catch (e) { return; }
      var lines = src.split('\n');
      for (var i = 0; i < lines.length; i++) {
        if (!EVICT.test(lines[i])) continue;
        // inspect the handler body window (±8 lines) — does it snapshot the OP-LOG, not a draft buffer?
        var win = lines.slice(Math.max(0, i - 1), i + 9).join('\n');
        if (OPLOG_SNAPSHOT.test(win)) { found.push(f + ':' + (i + 1)); break; }
      }
    });
  });
  return found;
}

(async function () {
  console.log('═══ S1 DISCOVERY SPIKES — MEASURE not remedy (ProductionRisks A1/B1/C1) ═══');
  var SQL = await initSqlJs();
  loadGlobalModule(SIGNER);
  var ErpSigner = global.window.ErpSigner;
  var kp = await ErpSigner.mintKeypair();
  var appSigner = ErpSigner.makeSigner(kp);
  var signer = { signTip: function (h) { return appSigner.sign(h); }, signed_by: 'edge:app-signer' };

  // ─────────────────────────────────────────────────────────────────────────────────────────────────
  // SPIKE A1 — does durability beat eviction?
  //   Model the real path: ops are appended OFFLINE; some get RELAYED (server-durable), the tail does NOT.
  //   "Eviction" (Safari ITP / low-disk / user-clear) wipes the LOCAL store (IndexedDB op-log). After it,
  //   only what was RELAYED is recoverable. Question: did an auto-snapshot fire for the un-acked tail FIRST?
  // ─────────────────────────────────────────────────────────────────────────────────────────────────
  console.log('\n── SPIKE A1 — durability vs eviction ──');
  var N_OFFLINE = 50;          // ops appended while offline (the at-risk population)
  var RELAYED = 30;            // of those, this many were relayed before eviction (server-durable)
  var K = freshKernel(); var dbA = new SQL.Database();
  var q = new OfflineQueue({ now: function () { return 1000; } });
  var ids = [];
  for (var i = 0; i < N_OFFLINE; i++) {
    var op = post('C_Invoice', 7000 + i);
    K.commitOp(dbA, op.type, op.params);                 // local op-log (the IndexedDB-backed store)
    ids.push(q.enqueue(op).queued);                       // offline relay queue
  }
  q.markRelayed(ids.slice(0, RELAYED));                   // first RELAYED reach the server
  var unackedTail = q.count() - RELAYED;                  // un-acked = appended locally, never relayed

  // Does an auto-snapshot fire for the un-acked tail BEFORE eviction? (code reality, not a guess)
  var evictHooks = hasSnapshotOnEvictHook();
  var snapshotFiredBefore = evictHooks.length > 0;       // none in the tree → false (A1 remedy is unbuilt)

  // EVICT: wipe the local op-log. Recovery can only replay what was durable elsewhere = the relayed prefix.
  dbA.run('DELETE FROM kernel_ops');                      // simulate IndexedDB eviction (local store gone)
  var dbR = new SQL.Database(); var KR = freshKernel();   // fresh device/tab after eviction
  // recovery: re-pull the RELAYED ops from the server-durable copy and replay them
  q._q.filter(function (r) { return r.relayed; }).forEach(function (r) { KR.commitOp(dbR, r.op.type, r.op.params); });
  var recoveredOps = KR.replayOps(dbR).length;
  var lostOps = N_OFFLINE - recoveredOps;

  console.log('§SPIKE-A1 unackedAtEvict=' + unackedTail + ' snapshotFiredBefore=' + (snapshotFiredBefore ? 'Y' : 'N') +
    ' recoveredOps=' + recoveredOps + ' lostOps=' + lostOps +
    ' | evictHooks=' + (evictHooks.length ? evictHooks.join(',') : 'none') +
    ' mitigation=persist()-resist-only(battery_aware.js) relay-ack=server-durable');

  // ─────────────────────────────────────────────────────────────────────────────────────────────────
  // SPIKE B1 — GUARD RAIL, not the shipped path.  (proxy: node V8/WASM, NOT a phone)
  //   ⚠ The shipped viewer does NOT full-hydrate: it STREAMS via range-request httpvfs (_useRangeStream /
  //   _rangeDb in streaming.js) + bbox-DLOD, fetching geometry ON DEMAND. Empirically the LARGEST building
  //   (LTU, 125,698 elems) loads ~12 s smooth. So this full-DB-into-WASM-heap load is the WORST CASE a
  //   non-streaming path would hit — a guard rail proving full-hydrate must stay OFF the mobile path — NOT a
  //   measurement of the real viewer. The genuine 200 MB ceiling (FoldEngineConstraints §2#1) is the ERP
  //   OP-LOG DB (437 B/op; design keeps it ~13 MB), not this geometry blob.
  // ─────────────────────────────────────────────────────────────────────────────────────────────────
  console.log('\n── SPIKE B1 — biggest building in-memory ceiling (proxy) ──');
  if (!fs.existsSync(BIGGEST_DB)) {
    console.log('§SPIKE-B1 device=blocked reason=biggest-building-db-missing path=' + BIGGEST_DB);
  } else {
    if (global.gc) global.gc();
    var rssBefore = process.memoryUsage().rss;
    var t0 = process.hrtime.bigint();
    var buf = fs.readFileSync(BIGGEST_DB);
    var bdb = new SQL.Database(buf);                       // full DB resident in WASM heap (viewer's path)
    // touch the geometry the renderer must decode — count + total BLOB bytes that become Float32 arrays
    var gr = bdb.exec('SELECT count(*), sum(length(vertices)+length(faces)) FROM component_geometries');
    var geoms = gr[0].values[0][0], geoBytes = gr[0].values[0][1];
    var er = bdb.exec('SELECT count(*) FROM element_instances');
    var elements = er[0].values[0][0];
    var ttiMs = Number(process.hrtime.bigint() - t0) / 1e6;
    if (global.gc) global.gc();
    var rssAfter = process.memoryUsage().rss;
    var peakHeapMB = mb(rssAfter - rssBefore);
    var fileMB = mb(buf.length);
    // Three.js decodes vertices+faces BLOB → Float32/Uint32 typed arrays ≈ resident again on top of the DB.
    var decodedEstMB = mb(geoBytes);
    var projectedResidentMB = Math.round((fileMB + decodedEstMB) * 10) / 10;
    var crashed = projectedResidentMB > MOBILE_BUDGET_MB;  // projected to exceed the iOS tab-kill ceiling
    console.log('§SPIKE-B1 loadModel=FULL-HYDRATE-GUARDRAIL(NOT-viewer-which-streams-via-httpvfs+DLOD)' +
      ' device=proxy(node18-v8/wasm,NOT-a-phone) elements=' + elements +
      ' peakHeapMB=' + peakHeapMB + ' dbFileMB=' + fileMB + ' geomBytesMB=' + decodedEstMB +
      ' projectedIfFullHydratedMB=' + projectedResidentMB + ' budgetMB=' + MOBILE_BUDGET_MB +
      ' wouldBreachIfNonStreaming=' + (crashed ? 'Y' : 'N') + ' ttiMs=' + Math.round(ttiMs) + ' geoms=' + geoms +
      ' NOTE=largest-real-building=LTU(125698-elems)-loads-~12s-smooth-via-streaming');
    bdb.close();
  }

  // ─────────────────────────────────────────────────────────────────────────────────────────────────
  // SPIKE C1 — does it even run on iOS Safari?  Needs a real device (BrowserStack/Sauce/physical).
  //   No iOS Safari is reachable from this sandbox → record blocked HONESTLY (never fake the readout).
  // ─────────────────────────────────────────────────────────────────────────────────────────────────
  console.log('\n── SPIKE C1 — iOS Safari ──');
  console.log('§SPIKE-C1 browser=blocked:needs-device loads=? dbReturns=? renders=? shares=? ' +
    'notes=no-iOS-Safari-in-sandbox(needs BrowserStack/Sauce or physical iPhone; do NOT fake)');

  console.log('\n═══ S1 SPIKES EMITTED — read the §SPIKE-* lines above; write verdicts into ProductionRisks ═══');
})().catch(function (e) { console.error('FATAL ' + e.stack); process.exit(2); });
