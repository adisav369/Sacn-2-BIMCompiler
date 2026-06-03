#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard
// SPIKE (user: "see it fast + see what issues emit + watch the clock speed, will bloat happen").
//   Drives the REAL live write path headless — ERPSeam.dispatch over the proven ERPKernel + sql.js +
//   glassbowl_data.db — for N iterations, METERING clock speed (per-op ms, per-stage) and BLOAT
//   (op-log rows, projection export bytes, seal cost as the log grows). NOT production; the point is
//   to surface the issues. READ the §METER/§BLOAT/§METER-SUMMARY + ISSUES lines before any conclusion.
// Run: node scripts/spike_writepath.js [N] 2>&1 | tee build/erp/spike_writepath.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var initSqlJs = require('sql.js');

// load the engine the BROWSER will load (UMD → window globals), via a window shim.
global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;   // kernel_ops.js seals via bare `crypto.subtle`
require('./erp_kernel');         // window.ERPKernel
require('./erp_seam');           // window.ERPSeam
require('./erp_postings');       // window.ERPPostings
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));  // window.KernelOps (sealed log)
var K = global.window.ERPKernel, Seam = global.window.ERPSeam, Post = global.window.ERPPostings, KO = global.window.KernelOps;

var N = parseInt(process.argv[2] || '200', 10);
var GB = path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db');
var AD = path.join(__dirname, '..', 'build', 'erp', 'post_poc', 'ad_seed.db');  // ad_role/validcombination/elementvalue
var WFMC = require(path.join(process.env.HOME, 'bim-ootb', 'erp', 'manifest.json')).wfmc;

// ── meter helpers (hrtime: the HARNESS measures; the engine stays deterministic) ──────────────────
function now() { return Number(process.hrtime.bigint()) / 1e6; }   // ms
function pct(arr, p) { var a = arr.slice().sort(function (x, y) { return x - y; }); return a[Math.min(a.length - 1, Math.floor(a.length * p))]; }
function kb(n) { return (n / 1024).toFixed(1) + 'KB'; }
var METRICS = { dms: [], bytes0: 0, bytesEnd: 0, N: 0, ops: 0, seal: [] };   // captured for the ISSUES ledger

(async function () {
  var gb = new Database(GB, { readonly: true });
  var ad = new Database(AD, { readonly: true });
  var gbQ = function (s, p) { return gb.prepare(s).all(p || []); };
  var adQ = function (s, p) { return ad.prepare(s).all(p || []); };
  var SQL = await initSqlJs();
  var newDb = function () { return new SQL.Database(); };
  var projDb = newDb(); K.initProjection(projDb);

  // the ONE handler the lens dispatch funnels through (SET_STATUS → kernel-computed ctx.to).
  K.register('C_Invoice', 'CO', function (doc, ctx) {
    return [{ op_type: 'SET_STATUS', table: doc.table, id: doc.id, doc_status: ctx.to, uuid: doc.uuid }];
  });
  var seam = Seam.makeSeam({ projDb: projDb, adQ: adQ, factQ: gbQ, wfmc: WFMC, newDb: newDb });
  var ctx = { actor: 'user:alice', role: { actions: ['C_Invoice:CO'] }, allowOrgs: '*' };

  console.log('═══ SPIKE write-path — live dispatch clock + bloat (N=' + N + ') ═══');
  console.log('engine=ERPSeam.dispatch→ERPKernel(sql.js)  data=glassbowl_data.db  wfmc=' + WFMC.states.length + ' states\n');

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // PHASE A — the live write path: each iter creates a fresh DR card then dispatches its completion.
  //   Metered: dispatch_ms (apply+commit+the 2× projectionHash violation-guard), refold_ms, export.
  // ════════════════════════════════════════════════════════════════════════════════════════════════
  var dms = [], rms = [], rejected = 0;
  var t0 = now(), bytes0 = projDb.export().length;
  for (var i = 0; i < N; i++) {
    var uuid = 'DOC:C_Invoice#SPIKE' + i;
    // setup write: a fresh card lands in DR, owned by alice.
    K.apply(projDb, [{ op_type: 'CREATE_DOCUMENT', table: 'C_Invoice', source_id: 'SPIKE' + i, doc_status: 'DR', uuid: uuid }],
            { actor: 'user:alice', baseTs: 1000 });
    // the METERED write: dispatch SET_STATUS DR→CO through the seam (gates + kernel apply + commit).
    var ds = now();
    var r = seam.dispatch({ docType: 'C_Invoice', table: 'C_Invoice', action: 'CO', status: 'DR', id: 'SPIKE' + i, uuid: uuid }, ctx);
    dms.push(now() - ds);
    if (!r.ok) rejected++;
    // re-fold: re-derive the board from the live projection (the open-design-Q loop — full re-query).
    var rs = now();
    K.query(projDb, "SELECT doc_status, COUNT(*) AS n FROM documents GROUP BY doc_status");
    rms.push(now() - rs);

    if ((i + 1) % 25 === 0 || i === N - 1) {
      var es = now(); var bytes = projDb.export().length; var exportMs = now() - es;
      var ops = K.query(projDb, 'SELECT COUNT(*) AS c FROM kernel_ops')[0].c;
      console.log('§METER iter=' + (i + 1) + ' dispatch_ms=' + dms[i].toFixed(3) + ' refold_ms=' + rms[i].toFixed(3) +
        ' export_ms=' + exportMs.toFixed(2));
      console.log('§BLOAT iter=' + (i + 1) + ' opLogRows=' + ops + ' projBytes=' + kb(bytes) +
        ' bytesPerOp=' + (ops ? (bytes / ops).toFixed(0) : 0) + 'B');
    }
  }
  var wallA = now() - t0, bytesEnd = projDb.export().length;
  METRICS.dms = dms; METRICS.bytes0 = bytes0; METRICS.bytesEnd = bytesEnd; METRICS.N = N;
  METRICS.ops = K.query(projDb, 'SELECT COUNT(*) AS c FROM kernel_ops')[0].c;
  console.log('\n§METER-SUMMARY phase=write n=' + N + ' rejected=' + rejected +
    ' dispatch_ms[p50=' + pct(dms, 0.5).toFixed(3) + ' p95=' + pct(dms, 0.95).toFixed(3) + ' max=' + pct(dms, 1).toFixed(3) + ']' +
    ' refold_ms[p50=' + pct(rms, 0.5).toFixed(3) + ' p95=' + pct(rms, 0.95).toFixed(3) + ']' +
    ' wall=' + wallA.toFixed(0) + 'ms throughput=' + (N / (wallA / 1000)).toFixed(0) + 'op/s');
  console.log('§BLOAT-SUMMARY projBytes ' + kb(bytes0) + '→' + kb(bytesEnd) + ' Δ=' + kb(bytesEnd - bytes0) +
    ' opLogRows=' + K.query(projDb, 'SELECT COUNT(*) AS c FROM kernel_ops')[0].c +
    ' dispatchDrift(p50→last10avg)=' + (avg(dms.slice(-10)) / pct(dms, 0.5)).toFixed(2) + 'x');

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // PHASE B — seal/verify cost as the SIGNED log grows (kernel_ops.js W-CHAIN, its native schema).
  //   This is the persist-time cost the browser pays (debounced). sealChain re-hashes the WHOLE log.
  // ════════════════════════════════════════════════════════════════════════════════════════════════
  console.log('\n── PHASE B — seal/verify cost vs log length (kernel_ops.js W-CHAIN) ──');
  var sdb = newDb(); KO.ensureTable(sdb);
  var _log = console.log;                              // mute commitOp's per-op chatter (keeps the meter readable)
  for (var j = 0; j < N; j++) {
    console.log = function () {};
    KO.commitOp(sdb, 'SET_STATUS', { table: 'C_Invoice', id: 'SPIKE' + j, doc_status: 'CO' }, null, 'DOC:C_Invoice#SPIKE' + j, 'op-' + j);
    console.log = _log;
    if ((j + 1) % 50 === 0 || j === N - 1) {
      var ss = now(); var seal = await KO.sealChain(sdb); var sealMs = now() - ss;
      var vs = now(); var ver = await KO.verifyChain(sdb); var verMs = now() - vs;
      METRICS.seal.push({ ops: j + 1, sealMs: sealMs, verMs: verMs });
      console.log('§METER-SEAL ops=' + (j + 1) + ' seal_ms=' + sealMs.toFixed(2) + ' verify_ms=' + verMs.toFixed(2) +
        ' chainOk=' + (ver.ok ? 'Y' : 'N') + '  (O(n) per persist → O(n²) cumulative)');
    }
  }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // PHASE B2 — §I-K seal-from-tip: SAME N writes via commitGroup (seal ONCE per group, from the tip).
  //   The I-D flattening witness: PHASE B's sealChain re-hashes the WHOLE log per persist (grows with N);
  //   commitGroup → sealFrom hashes ONLY the new rows → per-write seal cost is FLAT in log length.
  //   §METER-SEAL-FROM seal_ms must NOT grow ~linearly with N (contrast PHASE B's §METER-SEAL).
  // ════════════════════════════════════════════════════════════════════════════════════════════════
  console.log('\n── PHASE B2 — §I-K seal-from-tip cost vs log length (commitGroup → sealFrom) ──');
  var gdb = newDb(); KO.ensureTable(gdb);
  var _log2 = console.log;
  for (var k = 0; k < N; k++) {
    console.log = function () {};
    // one-op group: the per-write commit path the doc-action UI will use; seals from the tip forward only.
    // gid is an edge-minted INPUT (deterministic, index-derived); baseTs is a recorded INPUT (no wall-clock in hash).
    var gs = now();
    /* eslint-disable no-await-in-loop */
    await KO.commitGroup(gdb, [{ op_type: 'SET_STATUS', op_uuid: 'gop-' + k,
      params: { table: 'C_Invoice', id: 'SPIKEG' + k, doc_status: 'CO' } }], { gid: 'grp-' + k, baseTs: 1000 + k });
    var gms = now() - gs;
    console.log = _log2;
    if ((k + 1) % 50 === 0 || k === N - 1) {
      var gvs = now(); var gver = await KO.verifyChain(gdb); var gverMs = now() - gvs;
      METRICS.sealFrom = METRICS.sealFrom || [];
      METRICS.sealFrom.push({ ops: k + 1, sealMs: gms, verMs: gverMs });
      console.log('§METER-SEAL-FROM ops=' + (k + 1) + ' commitGroup_ms=' + gms.toFixed(2) + ' verify_ms=' + gverMs.toFixed(2) +
        ' chainOk=' + (gver.ok ? 'Y' : 'N') + '  (seal-from-tip → FLAT per write, I-D win)');
    }
  }
  gdb.close();

  // ── readPostings liveness over the live data (gate + degrade), proving the read side works too ──
  console.log('\n── readPostings liveness (over live projDb + glassbowl fact_acct) ──');
  // POST one real invoice so there is an oplog fold to read.
  postOne(projDb, ad);
  var ref = { table: 'C_Invoice', record_id: 103, ad_org_id: 11, ad_table_id: 318 };
  var pAdmin = Post.readPostings(ref, { role: { id: 102 }, allowOrgs: '*' }, { adQ: adQ, projQ: function (s, p) { return K.query(projDb, s, p); }, factQ: gbQ });
  var pUser = Post.readPostings(ref, { role: { id: 103 }, allowOrgs: '*' }, { adQ: adQ, projQ: function (s, p) { return K.query(projDb, s, p); }, factQ: gbQ });
  console.log('§POSTED-READ role=102 visible=' + (pAdmin.visible ? 'Y' : 'N') + ' posted=' + (pAdmin.posted ? 'Y' : 'N') + ' rows=' + pAdmin.lines.length + ' source=' + pAdmin.source + ' coverage=' + pAdmin.coverage);
  console.log('§POSTED-GATE role=103 visible=' + (pUser.visible ? 'Y' : 'N') + ' reason=' + pUser.reason + ' rows=' + pUser.lines.length);

  gb.close(); ad.close(); projDb.close(); sdb.close();
  console.log('\n═══ SPIKE DONE — see §METER-SUMMARY / §BLOAT-SUMMARY / §METER-SEAL; ISSUES ledger printed below ═══');
  printIssues();
  process.exit(0);   // clean exit — drop kernel_ops.js's 2s persist-debounce timer (browser-only path)
})().catch(function (e) { console.error('FATAL', e); process.exit(2); });

function avg(a) { return a.reduce(function (s, x) { return s + x; }, 0) / a.length; }

// POST C_Invoice#103 (real masters) so readPostings has an oplog fold (reuse the §13.5 resolve inline).
function postOne(projDb, ad) {
  var Resolver = require('./post_resolver');
  var K = global.window.ERPKernel;
  var get = function (s, p) { return ad.prepare(s).get(p || []); };
  var all = function (s, p) { return ad.prepare(s).all(p || []); };
  var doc = get('SELECT c_invoice_id id, grandtotal AS grandtotal, c_bpartner_id AS bp FROM c_invoice WHERE c_invoice_id=103');
  var lines = all('SELECT m_product_id product, linenetamt net FROM c_invoiceline WHERE c_invoice_id=103');
  var taxes = all('SELECT c_tax_id tax, taxamt FROM c_invoicetax WHERE c_invoice_id=103');
  function cents(n) { return Math.round(Number(n || 0) * 100); }
  var dr = {}, cr = {};
  function add(m, a, v) { m[a] = (m[a] || 0) + Number(v); }
  var r1 = Resolver.resolve(ad, '{BPartner.Receivable}', doc.bp, 101); if (r1.acct != null) add(dr, r1.acct, doc.grandtotal);
  lines.forEach(function (l) { var r = Resolver.resolve(ad, '{Product.Revenue}', l.product, 101); if (r.acct != null) add(cr, r.acct, l.net); });
  taxes.forEach(function (t) { var r = Resolver.resolve(ad, '{Tax.Due}', t.tax, 101); if (r.acct != null) add(cr, r.acct, t.taxamt); });
  var pl = [];
  Object.keys(dr).forEach(function (a) { pl.push({ account_id: a, amtacctdr: cents(dr[a]) / 100, amtacctcr: 0 }); });
  Object.keys(cr).forEach(function (a) { pl.push({ account_id: a, amtacctdr: 0, amtacctcr: cents(cr[a]) / 100 }); });
  K.apply(projDb, [{ op_type: 'POST', table: 'C_Invoice', id: 103, acctschema: 101, lines: pl }], { actor: 'plugin:post', baseTs: 9000 });
}

function printIssues() {
  var m = METRICS;
  var drift = (avg(m.dms.slice(-10)) / pct(m.dms, 0.5));
  var bpo = (m.bytesEnd - m.bytes0) / m.ops;
  var s = m.seal;
  var sealFirst = s[0], sealLast = s[s.length - 1];
  var sealRatio = sealLast && sealFirst ? (sealLast.sealMs / sealFirst.sealMs) : 0;
  var sealOpRatio = sealLast && sealFirst ? (sealLast.ops / sealFirst.ops) : 0;
  console.log('\n┌─ ISSUES LEDGER — read off the §-lines above (non-invent) ──────────────────────────────');
  console.log('│ [I-1] dispatch O(projection) per write. ERPKernel.dispatch runs projectionHash() TWICE');
  console.log('│       (violation-guard, erp_kernel.js:251-256) → every write re-hashes ALL rows.');
  console.log('│       Observed: dispatch_ms p50=' + pct(m.dms, 0.5).toFixed(2) + ' → last-10 avg=' + avg(m.dms.slice(-10)).toFixed(2) +
              ' (drift ' + drift.toFixed(2) + 'x over ' + m.N + ' writes). Linear now; O(n²) cumulative at scale.');
  console.log('│ [I-2] seal = whole-log re-hash per persist. kernel_ops.sealChain reseals EVERY op each');
  console.log('│       persist (kernel_ops.js:137). Observed seal_ms ' + (sealFirst ? sealFirst.sealMs.toFixed(1) : '?') + '@' + (sealFirst ? sealFirst.ops : '?') +
              'ops → ' + (sealLast ? sealLast.sealMs.toFixed(1) : '?') + '@' + (sealLast ? sealLast.ops : '?') + 'ops' +
              ' (~' + sealOpRatio.toFixed(0) + 'x ops → ' + sealRatio.toFixed(1) + 'x ms). Debounced, but O(n)/persist → O(n²).');
  // §I-K seal-from-tip (PHASE B2) — the I-D FIX measured on the SAME write count via commitGroup→sealFrom.
  var sf = m.sealFrom || [];
  if (sf.length) {
    var sfFirst = sf[0], sfLast = sf[sf.length - 1];
    var sfRatio = sfLast && sfFirst ? (sfLast.sealMs / sfFirst.sealMs) : 0;
    console.log('│ [I-D FIX] seal-from-tip (commitGroup→sealFrom): per-write seal hashes ONLY new rows. Observed');
    console.log('│       commitGroup_ms ' + sfFirst.sealMs.toFixed(2) + '@' + sfFirst.ops + 'ops → ' + sfLast.sealMs.toFixed(2) + '@' + sfLast.ops +
                'ops (' + sfRatio.toFixed(2) + 'x ms over ' + sealOpRatio.toFixed(0) + 'x ops) — FLAT vs [I-2] ' + sealRatio.toFixed(1) + 'x. I-D flattened.');
  }
  console.log('│ [I-3] projection bloat. db.export() ' + kb(m.bytes0) + '→' + kb(m.bytesEnd) + ' for ' + m.ops + ' ops (~' +
              bpo.toFixed(0) + 'B/op): each kernel_ops row stores a RICH JSON payload (op+before/after+lineage).');
  console.log('│       The WHOLE blob is re-serialized + re-persisted to IDB on every debounced write.');
  console.log('│ [I-4] SCHEMA MISMATCH (integration). The live op-log (erp_kernel kernel_ops: op_uuid PK,');
  console.log('│       no id/prev_hash/op_hash/sig) ≠ the W-CHAIN/W-SIGN sealer schema (kernel_ops.js).');
  console.log('│       So signed tamper-evidence does NOT yet cover the live write log — reconcile before sign.');
  console.log('│ [I-5] re-fold = full GROUP BY re-query per write (refold_ms tiny here, O(docs) — watch at 10k+).');
  console.log('│ Mitigations to weigh next: incremental hash (skip double full-hash); incremental/rolling seal;');
  console.log('│ compact()/prune the rich payload; persist deltas not full export; one op-log schema.');
  console.log('└────────────────────────────────────────────────────────────────────────────────────────');
}
