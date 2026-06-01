#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * test_kernel_identity.js — A1 / G-IDENTITY-LIVE: exercises the REAL bim-ootb/viewer/kernel_ops.js
 *   (not a copy) to confirm op_uuid identity is wired in. Spec: docs/ERP.md §0.21 (D1–D4) +
 *   scripts/poc_identity.js (the witness shape, proven against the erp_kernel.js prototype).
 *   This mirrors those verdicts against the ACTUAL deployed kernel.
 *
 *   Proves (§0.21 — identity is a recorded edge INPUT, never recomputed on replay):
 *     §IDENTITY mint    — commitOp mints a distinct op_uuid per op (a recorded edge input)
 *     §IDENTITY merge   — two devices' logs UNION with NO op_uuid clash, while the local `id`
 *                         collides 1,2 vs 1,2 (the old rowid-only identity lost half the ops)
 *     §IDENTITY replay  — replayOps RE-READS the recorded op_uuid; two replays + the record all
 *                         agree (D2/D3: identity read, never re-minted on the replay path)
 *     §IDENTITY newdoc  — a caller-supplied (edge-minted) op_uuid is honoured verbatim (D4 seam)
 *     §KRN_CHAIN        — W-CHAIN seal/verify still pass in id order (op_uuid is NOT in the hash,
 *                         so the chain is byte-identical — W-CHAIN unbroken)
 *
 *   Each real device = a fresh module instance (require-cache bust) so ensureTable runs per device,
 *   exactly as a separate page load would (kernel_ops.js keeps one _tableCreated flag per module).
 *
 *   Determinism note: op_uuid is edge-minted (crypto.randomUUID) so its VALUE varies run-to-run —
 *   that is the point (it is an input). Every verdict asserts a RELATIONSHIP (distinctness, replay
 *   equality, verbatim honouring), not a fixed value. The replay path itself mints nothing.
 *
 * Run: node scripts/test_kernel_identity.js 2>&1 | tee build/erp/test_kernel_identity.log
 */
'use strict';
// Node <20 doesn't always expose `crypto` as a bare global (browsers do). Shim only if absent.
if (typeof global.crypto === 'undefined') { global.crypto = require('crypto').webcrypto; }
global.window = { APP: {} };
global.APP = global.window.APP;   // bare APP ref inside _persistToIdb (browser global)
global.indexedDB = { open: function () { var r = {}; setTimeout(function () { r.result = { createObjectStore: function () {}, transaction: function () { return { objectStore: function () { return { put: function () {} }; } }; } }; if (r.onsuccess) r.onsuccess(); }, 0); return r; } };

var path = require('path');
var nodeCrypto = require('crypto');
var initSqlJs = require('sql.js');
var KERNEL = path.join(process.env.HOME, 'bim-ootb', 'viewer', 'kernel_ops.js');

// freshKernel — a fresh "device": its own module instance with its own _tableCreated flag, so
// ensureTable creates the schema per device (faithful to a separate page load). Capture the ref
// immediately: each require overwrites global.window.KernelOps.
function freshKernel() {
  delete require.cache[require.resolve(KERNEL)];
  require(KERNEL);
  return global.window.KernelOps;
}

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function sha(s) { return nodeCrypto.createHash('sha256').update(s).digest('hex'); }
function readRows(db) {
  var r = db.exec('SELECT id, op_uuid, timestamp, op_type, parameters FROM kernel_ops WHERE undone = 0 ORDER BY id');
  if (!r.length) return [];
  return r[0].values.map(function (v) { return { id: v[0], op_uuid: v[1], timestamp: v[2], op_type: v[3], parameters: v[4] }; });
}

(async function () {
  console.log('═══ TEST-KERNEL-IDENTITY — op_uuid identity in the REAL kernel_ops.js (§0.21) ═══\n');
  var SQL = await initSqlJs();

  // ── #mint + #merge: two devices each commit 2 self-contained ops on a FRESH kernel instance ──
  var KA = freshKernel(); var dbA = new SQL.Database();
  KA.commitOp(dbA, 'SET_STATUS', { table: 'C_Order', id: 101, doc_status: 'CO' });
  KA.commitOp(dbA, 'ALLOCATE',   { order_id: 101, payment_id: 501, amount: 100 });
  var KB = freshKernel(); var dbB = new SQL.Database();
  KB.commitOp(dbB, 'SET_STATUS', { table: 'C_Order', id: 202, doc_status: 'CO' });
  KB.commitOp(dbB, 'ALLOCATE',   { order_id: 202, payment_id: 502, amount: 200 });

  var rowsA = readRows(dbA), rowsB = readRows(dbB);
  var ids   = rowsA.map(function (r) { return r.id; }).concat(rowsB.map(function (r) { return r.id; }));
  var uuids = rowsA.map(function (r) { return r.op_uuid; }).concat(rowsB.map(function (r) { return r.op_uuid; }));
  var idDistinct = new Set(ids).size, uuidDistinct = new Set(uuids).size;
  console.log('§IDENTITY merge devices=2 ops=' + uuids.length + ' ids=' + ids.join(',') +
              ' uuids=' + uuids.map(function (u) { return u ? u.slice(0, 8) : 'null'; }).join(','));
  verdict(uuids.every(Boolean) && uuidDistinct === 4,
          'commitOp mints a distinct op_uuid per op (recorded edge input)', uuidDistinct + '/4 distinct');
  verdict(idDistinct === 2 && uuidDistinct === 4,
          'CONTRAST: local id collides (2 distinct of 4); edge-minted op_uuid keeps all 4',
          'id-distinct=' + idDistinct + ' uuid-distinct=' + uuidDistinct);

  // ── #replay: merge the device logs (preserving op_uuid), then replayOps RE-READS them ──
  // Total order over the merged log = (timestamp, op_uuid) — both recorded INPUTS.
  var merged = rowsA.concat(rowsB).slice().sort(function (a, b) {
    return a.timestamp < b.timestamp ? -1 : a.timestamp > b.timestamp ? 1
         : (a.op_uuid < b.op_uuid ? -1 : a.op_uuid > b.op_uuid ? 1 : 0);
  });
  function buildMergedLog(orderedRows) {            // a fresh device rebuilding from the merged log
    var K = freshKernel(); var db = new SQL.Database(); K.ensureTable(db);   // REAL DDL → op_uuid column
    orderedRows.forEach(function (r) {
      db.run('INSERT INTO kernel_ops (op_uuid, timestamp, op_type, parameters) VALUES (?,?,?,?)',
             [r.op_uuid, r.timestamp, r.op_type, r.parameters]);
    });
    return { K: K, db: db };
  }
  var liveSeq = merged.map(function (r) { return r.op_uuid; }).join('|');
  var m1 = buildMergedLog(merged), m2 = buildMergedLog(merged);   // two devices replay the same merged log
  var rep1 = m1.K.replayOps(m1.db), rep1b = m1.K.replayOps(m1.db), rep2 = m2.K.replayOps(m2.db);
  var seq1  = rep1.map(function (o) { return o.op_uuid; }).join('|');
  var seq1b = rep1b.map(function (o) { return o.op_uuid; }).join('|');
  var seq2  = rep2.map(function (o) { return o.op_uuid; }).join('|');
  console.log('§IDENTITY replay ops=' + rep1.length + ' liveHash=' + sha(liveSeq).slice(0, 12) +
              ' replayHash=' + sha(seq1).slice(0, 12) + ' deviceB=' + sha(seq2).slice(0, 12));
  verdict(seq1 === liveSeq && seq1b === liveSeq,
          'replayOps RE-READS recorded op_uuid; two replays + the record agree (edgeMint=0 on replay)',
          'replay==live:' + (seq1 === liveSeq) + ' stable:' + (seq1 === seq1b));
  verdict(seq2 === seq1,
          'two devices replay the merged log → SAME op_uuid sequence (holder-irrelevant)',
          'A==B:' + (seq2 === seq1));

  // ── #newdoc (D4): a source-less New doc whose uuid was edge-minted BEFORE the kernel ──
  var KN = freshKernel(); var dbN = new SQL.Database();
  var edgeUuid = 'edge-' + global.crypto.randomUUID();   // minted at the New-doc click, passed in
  KN.commitOp(dbN, 'CREATE_DOCUMENT', { table: 'C_Order', doc_status: 'DR' }, null, null, edgeUuid);
  var storedUuid = readRows(dbN)[0].op_uuid;
  console.log('§IDENTITY newdoc passedUuid=' + edgeUuid.slice(0, 16) + ' storedUuid=' + storedUuid.slice(0, 16));
  verdict(storedUuid === edgeUuid,
          'New doc (no source) honours the edge-minted op_uuid verbatim (D4 seam)',
          'stored==passed:' + (storedUuid === edgeUuid));

  // ── W-CHAIN unbroken: seal + verify still pass in id order with op_uuid present ──
  var KC = freshKernel(); var dbC = new SQL.Database();
  KC.commitOp(dbC, 'SESSION_START', { ts: 't0' });
  KC.commitOp(dbC, 'SET_STATUS', { table: 'C_Order', id: 1, doc_status: 'CO' });
  var s = await KC.sealChain(dbC);
  var v = await KC.verifyChain(dbC);
  verdict(s.sealed === 2 && v.ok && v.len === 2,
          'W-CHAIN seal/verify still pass in id order (op_uuid not in hash → chain unbroken)',
          'sealed=' + s.sealed + ' verifyOK=' + v.ok + ' len=' + v.len);

  console.log('\n§IDENTITY ' + (fails ? 'FAIL — ' + fails + ' checks red'
    : 'PASS — op_uuid is a clash-free, holder-irrelevant recorded input; the replay path re-reads it (mints nothing); New-doc seam honours edge-minted uuids; W-CHAIN unbroken'));
  process.exit(fails ? 1 : 0);
})();
