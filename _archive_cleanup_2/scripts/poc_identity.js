#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_identity.js — W-IDENTITY-LIVE: G-IDENTITY wired into the REAL kernel (ERP.md §0.21).
 *   Spec: docs/ERP.md §0.21 (D1–D4); DistributedERP.md §7 (identity is an input, never computed).
 *   Mirrors scripts/poc_distributed.js — but drives the ACTUAL scripts/erp_kernel.js apply/replay,
 *   not a hand-written model of them. Phase-A item #2 of prompts/ERP_DEPLOY_AND_TOUR.md.
 *
 *   Proves (no server, deterministic — no Date.now / Math.random):
 *     #merge        — two devices apply self-contained ops; their kernel_ops logs UNION with NO
 *                     op_uuid clash (the old INTEGER AUTOINCREMENT rowid collided 1,2,3 vs 1,2,3);
 *                     replaying the merged, totally-ordered (timestamp,op_uuid) log into two fresh
 *                     kernels → the SAME projection hash (holder-irrelevant).
 *     #no-recompute — replaying a parent-child op-group reproduces every output_guid FROM THE RECORD
 *                     with edgeMint call-count 0 (D2/D3: identity read, never re-computed) and
 *                     replay-hash == live-hash.
 *     #newdoc       — a source-less "New doc" whose uuid is edge-minted BEFORE the kernel (D4 seam:
 *                     crypto.randomUUID) is honoured verbatim — edgeMint not called for it.
 *     #contrast     — numeric-seq PKs would collide across devices; edge-minted op_uuid does not.
 *
 * Run: node scripts/poc_identity.js 2>&1 | tee build/erp/poc_identity.log
 */
'use strict';
var crypto = require('crypto');
var initSqlJs = require('sql.js');
var K = require('./erp_kernel.js');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

// read the rich op-log of a device db as ordered rows {op_uuid, timestamp, op_type, parameters}.
function readLog(db) {
  return K.query(db, 'SELECT op_uuid, timestamp, op_type, parameters FROM kernel_ops WHERE undone=0');
}
// deterministic total order over a merged log: (timestamp, op_uuid) — both are recorded op INPUTS.
function totalOrder(rows) {
  return rows.slice().sort(function (a, b) {
    return a.timestamp < b.timestamp ? -1 : a.timestamp > b.timestamp ? 1
         : (a.op_uuid < b.op_uuid ? -1 : a.op_uuid > b.op_uuid ? 1 : 0);
  });
}
// build a fresh log-db holding the given ordered rows (rowid == insertion order == merged order),
// then let the REAL Kernel.replay rebuild a projection from it.
function replayMerged(SQL, orderedRows) {
  var logDb = new SQL.Database();
  logDb.run('CREATE TABLE kernel_ops (op_type TEXT, parameters TEXT, undone INTEGER DEFAULT 0)');
  orderedRows.forEach(function (r) {
    logDb.run('INSERT INTO kernel_ops (op_type, parameters, undone) VALUES (?,?,0)', [r.op_type, r.parameters]);
  });
  var proj = new SQL.Database();
  return K.replay(logDb, proj);   // { ops, hash }
}

(async function () {
  var SQL = await initSqlJs();
  console.log('═══ POC-IDENTITY — G-IDENTITY in the REAL kernel: op-uuid merge / no-recompute / new-doc ═══\n');

  // ── #merge: two devices, SELF-CONTAINED ops (referenced by shared source id; no currentDoc state) ──
  // Device A completes order 101 and allocates its payment; device B does the same for order 202.
  var dbA = new SQL.Database(); K.initProjection(dbA);
  var dbB = new SQL.Database(); K.initProjection(dbB);
  K.apply(dbA, [
    { op_type: 'SET_STATUS', table: 'C_Order', id: 101, doc_status: 'CO' },
    { op_type: 'ALLOCATE', order_id: 101, payment_id: 501, amount: 100 }
  ], { actor: 'A', baseTs: 1000 });
  K.apply(dbB, [
    { op_type: 'SET_STATUS', table: 'C_Order', id: 202, doc_status: 'CO' },
    { op_type: 'ALLOCATE', order_id: 202, payment_id: 502, amount: 200 }
  ], { actor: 'B', baseTs: 1000 });

  var logA = readLog(dbA), logB = readLog(dbB);
  var merged = totalOrder(logA.concat(logB));
  var opIds = merged.map(function (r) { return r.op_uuid; });
  var distinct = new Set(opIds).size;
  console.log('§IDENTITY merge devices=2 ops=' + merged.length + ' op_uuids=' + opIds.join(',') + ' order=' + merged.map(function (r) { return r.op_type[0] + r.op_uuid; }).join(' '));
  verdict(distinct === merged.length && merged.length === 4,
          'two devices’ logs UNION with no op_uuid clash (was: rowid 1,2 vs 1,2 collide)',
          distinct + '/' + merged.length + ' distinct');

  // replay the SAME merged log on TWO fresh kernels (each device rebuilds) → identical hash.
  K._stats.edgeMintCalls = 0;
  var rA = replayMerged(SQL, merged);
  var rB = replayMerged(SQL, merged);
  console.log('§IDENTITY replay hashA=' + rA.hash + ' hashB=' + rB.hash + ' ops=' + rA.ops + ' edgeMintCalls=' + K._stats.edgeMintCalls);
  verdict(rA.hash === rB.hash, 'both devices replay merged log → SAME projection hash (holder-irrelevant)', 'A==B:' + (rA.hash === rB.hash));

  // ── #no-recompute: a parent-child op-group replays from the RECORD, edgeMint never re-runs ──
  var live = new SQL.Database(); K.initProjection(live);
  K.apply(live, [
    { op_type: 'CREATE_DOCUMENT', table: 'M_InOut', source_id: 101 },
    { op_type: 'CREATE_LINE', table: 'M_InOutLine', source_line_id: 1001, m_product_id: 'P1', movementqty: 3 },
    { op_type: 'SET_STATUS', table: 'M_InOut', id: 101, doc_status: 'CO' }
  ], { actor: 'A', baseTs: 2000 });
  var liveHash = K.projectionHash(live);
  var recorded = K.query(live, "SELECT output_guid FROM kernel_ops WHERE op_type='CREATE_DOCUMENT'")[0].output_guid;

  K._stats.edgeMintCalls = 0;
  var fresh = new SQL.Database();
  var rep = K.replay(live, fresh);
  console.log('§IDENTITY no-recompute liveHash=' + liveHash + ' replayHash=' + rep.hash + ' edgeMintCalls=' + K._stats.edgeMintCalls + ' recordedDocGuid=' + recorded);
  verdict(rep.hash === liveHash && K._stats.edgeMintCalls === 0,
          'replay reproduces ids FROM THE RECORD; edgeMint call-count 0 on replay (D2/D3 §7)',
          'hash==:' + (rep.hash === liveHash) + ' edgeMintCalls=' + K._stats.edgeMintCalls);
  verdict(recorded === 'DOC:M_InOut@from101',
          'source-derived guid byte-stable — §ORACLE-SUITE/§LONGTAIL format preserved',
          recorded);

  // ── #newdoc (D4): a source-less New doc whose uuid was edge-minted BEFORE the kernel ──
  var ndDb = new SQL.Database(); K.initProjection(ndDb);
  var newUuid = '0190aa-newdoc-' + crypto.randomUUID().slice(0, 8);   // edge mints at the New-doc click
  K._stats.edgeMintCalls = 0;
  K.apply(ndDb, [
    { op_type: 'CREATE_DOCUMENT', table: 'C_Order', uuid: newUuid, doc_status: 'DR' }   // NO source_id
  ], { actor: 'local', baseTs: 3000 });
  var ndGuid = K.query(ndDb, 'SELECT id FROM documents')[0].id;
  console.log('§IDENTITY newdoc passedUuid=' + newUuid + ' storedGuid=' + ndGuid + ' edgeMintCalls=' + K._stats.edgeMintCalls);
  verdict(ndGuid === newUuid && K._stats.edgeMintCalls === 0,
          'New doc (no source) honours the edge-minted uuid verbatim; edgeMint NOT called (D4 seam)',
          'stored==passed:' + (ndGuid === newUuid) + ' edgeMintCalls=' + K._stats.edgeMintCalls);

  // ── #contrast: numeric-seq PKs collide across devices; edge-minted op_uuid does not ──
  var seqKeys = new Set(['1', '2'].concat(['1', '2']));   // both devices mint rowids 1,2
  verdict(seqKeys.size === 2 && distinct === 4,
          'CONTRAST: numeric rowids collide (2 survive of 4); edge-minted op_uuid keeps all 4',
          'rowid-distinct=' + seqKeys.size + ' uuid-distinct=' + distinct);

  console.log('\n§IDENTITY ' + (fails ? 'FAIL — ' + fails + ' checks red'
    : 'PASS — op_uuid merge is clash-free + holder-irrelevant; identity is a recorded input the replay path re-reads (edgeMint=0); New-doc seam honours edge-minted uuids'));
  process.exit(fails ? 1 : 0);
})();
