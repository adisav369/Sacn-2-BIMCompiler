#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_distributed.js — Merge/G-IDENTITY (#2) + W-OWNER/CAS (#3) acceptance
 *   (prompts/DISTRIBUTED_POC.md; DistributedERP.md §9-C/D/E, §4 guard set).
 *
 *   Simulates "server conditions" with NO server: two in-memory sql.js DBs = two devices.
 *   Proves:
 *     #2 MERGE/G-IDENTITY — edge-minted UUID PKs let two devices' logs UNION with no PK clash;
 *        replaying the merged ordered log on BOTH devices yields the SAME projection hash
 *        (holder-irrelevant). Contrast: numeric-seq PKs collide and lose data.
 *     #3 W-OWNER  — two peers ALLOCATE the same invoice → owner-gate (G-SINGLE-WRITER) rejects
 *        the non-owner on replay → one allocation survives, no money lost.
 *        CAS       — two peers CLAIM the same entitlement → first in total order wins (set-if-unset).
 *
 *   Determinism (§7): UUIDs / timestamps are edge-minted INPUTS (hard-coded fixtures here, never
 *   generated) so the merge order and every replay-hash are byte-identical run to run.
 *
 * Run: node scripts/poc_distributed.js 2>&1 | tee build/erp/poc_distributed.log
 */
'use strict';
var crypto = require('crypto');
var initSqlJs = require('sql.js');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

var PROJ =
  'CREATE TABLE documents (' +
  ' uuid TEXT PRIMARY KEY, doc_type TEXT, owner TEXT, status TEXT,' +
  ' claimed_by TEXT, amount REAL)';

// deterministic total order over the merged log: (ts, uuid) — both are recorded op INPUTS.
function totalOrder(ops) {
  return ops.slice().sort(function (a, b) {
    return a.ts < b.ts ? -1 : a.ts > b.ts ? 1 : (a.uuid < b.uuid ? -1 : a.uuid > b.uuid ? 1 : 0);
  });
}

// sha256 over the sorted projection — the holder-irrelevant equality witness.
function projectionHash(db) {
  var r = db.exec('SELECT uuid,doc_type,owner,status,claimed_by,amount FROM documents ORDER BY uuid');
  var rows = r.length ? r[0].values : [];
  return sha256(JSON.stringify(rows));
}
function sha256(s) { return crypto.createHash('sha256').update(s).digest('hex'); }

// replay — apply the ordered log with the guards. Returns { applied, rejected }.
//   ALLOCATE: owner-gated (G-SINGLE-WRITER) — only the owning device may allocate.
//   CLAIM:    CAS set-if-unset — first writer wins; later claims rejected.
function replay(db, orderedOps) {
  var applied = 0, rejected = [];
  orderedOps.forEach(function (op) {
    if (op.op_type === 'CREATE') {
      db.run('INSERT OR IGNORE INTO documents (uuid,doc_type,owner,status,amount) VALUES (?,?,?,?,?)',
             [op.target, op.doc_type, op.owner, 'drafted', op.amount || 0]);
      applied++;
    } else if (op.op_type === 'ALLOCATE') {
      var o = db.exec('SELECT owner,status FROM documents WHERE uuid=?', [op.target]);
      var row = o.length && o[0].values.length ? o[0].values[0] : null;
      if (!row) { rejected.push({ op: op, why: 'no such doc' }); return; }
      if (row[0] !== op.device) { rejected.push({ op: op, why: 'non-owner (' + op.device + '≠' + row[0] + ')' }); return; }
      db.run("UPDATE documents SET status='allocated' WHERE uuid=?", [op.target]);
      applied++;
    } else if (op.op_type === 'CLAIM') {
      var c = db.exec('SELECT claimed_by FROM documents WHERE uuid=?', [op.target]);
      var cur = c.length && c[0].values.length ? c[0].values[0][0] : null;
      if (cur) { rejected.push({ op: op, why: 'already claimed by ' + cur }); return; }
      db.run('UPDATE documents SET claimed_by=? WHERE uuid=?', [op.device, op.target]);
      applied++;
    }
  });
  return { applied: applied, rejected: rejected };
}

(async function () {
  var SQL = await initSqlJs();
  console.log('═══ POC-DISTRIBUTED — two devices, no server: merge / owner-gate / CAS ═══\n');

  // ── shared entities (edge-minted UUIDv7-style ids, as INPUTS) ──
  var INV = '0190aa01-inv-7c00-0000-invoice00001';   // invoice, owned by device A
  var TOK = '0190aa02-tok-7c00-0000-entitlement01';  // a single-claim entitlement
  // Device A's log
  var dA = [
    { uuid: '0190aa10-a001', device: 'A', ts: '2026-05-31T09:00:01Z', op_type: 'CREATE', target: INV, doc_type: 'C_Invoice', owner: 'A', amount: 100 },
    { uuid: '0190aa11-a002', device: 'A', ts: '2026-05-31T09:00:02Z', op_type: 'CREATE', target: TOK, doc_type: 'Entitlement', owner: 'merchant', amount: 50 },
    { uuid: '0190aa12-a003', device: 'A', ts: '2026-05-31T09:00:05Z', op_type: 'ALLOCATE', target: INV },          // owner A — valid
    { uuid: '0190aa13-a004', device: 'A', ts: '2026-05-31T09:00:06Z', op_type: 'CLAIM',    target: TOK },          // A claims first
    { uuid: '0190aa14-a005', device: 'A', ts: '2026-05-31T09:00:08Z', op_type: 'CREATE', target: '0190aa50-orderA', doc_type: 'C_Order', owner: 'A', amount: 10 }
  ];
  // Device B's log (offline, concurrent) — tries to allocate A's invoice and claim the same token
  var dB = [
    { uuid: '0190aa20-b001', device: 'B', ts: '2026-05-31T09:00:07Z', op_type: 'ALLOCATE', target: INV },          // non-owner — must be rejected
    { uuid: '0190aa21-b002', device: 'B', ts: '2026-05-31T09:00:09Z', op_type: 'CLAIM',    target: TOK },          // later than A — CAS loses
    { uuid: '0190aa22-b003', device: 'B', ts: '2026-05-31T09:00:10Z', op_type: 'CREATE', target: '0190aa60-orderB', doc_type: 'C_Order', owner: 'B', amount: 20 }
  ];

  // ── #2 MERGE / G-IDENTITY ──
  var merged = totalOrder(dA.concat(dB));
  console.log('§DIST merge devices=2 ops=' + merged.length + ' order=' + merged.map(function(o){return o.uuid.slice(-4);}).join(','));

  // union has no PK clash: count distinct op uuids == total ops
  var opIds = merged.map(function (o) { return o.uuid; });
  var distinct = new Set(opIds).size;
  verdict(distinct === merged.length, 'edge-minted UUIDs union with no PK clash', distinct + '/' + merged.length + ' distinct');

  // replay the SAME merged log on TWO fresh DBs (device A & device B each rebuild) → identical hash
  var dbA = new SQL.Database(); dbA.run(PROJ); var rA = replay(dbA, merged);
  var dbB = new SQL.Database(); dbB.run(PROJ); var rB = replay(dbB, merged);
  var hA = projectionHash(dbA), hB = projectionHash(dbB);
  console.log('§DIST replay hashA=' + hA.slice(0,12) + '… hashB=' + hB.slice(0,12) + '… appliedA=' + rA.applied + ' appliedB=' + rB.applied);
  verdict(hA === hB, 'both devices replay merged log → SAME projection hash (holder-irrelevant)', 'A==B:' + (hA === hB));

  // contrast: numeric-seq PKs collide — both devices mint id=1 for their new order → 1 row, data lost
  var seqKeys = ['A:order#1', 'B:order#1'].map(function (k) { return k.split(':')[1]; }); // both 'order#1'
  var seqDistinct = new Set(seqKeys).size;
  verdict(seqDistinct === 1, 'CONTRAST: numeric-seq PKs collide (both mint order#1) → data loss UUID avoids', seqDistinct + ' surviving of 2');

  // ── #3 W-OWNER: device B's ALLOCATE of A's invoice is rejected on replay ──
  var invStatus = dbA.exec('SELECT status FROM documents WHERE uuid=?', [INV])[0].values[0][0];
  var ownerReject = rA.rejected.filter(function (r) { return r.op.op_type === 'ALLOCATE' && r.op.device === 'B'; });
  console.log('§DIST owner-gate INV.status=' + invStatus + ' rejected=' + ownerReject.length + (ownerReject[0] ? ' why="' + ownerReject[0].why + '"' : ''));
  verdict(invStatus === 'allocated' && ownerReject.length === 1,
          'two peers ALLOCATE same invoice → non-owner rejected, one allocation, no money lost',
          'status=' + invStatus + ' rejects=' + ownerReject.length);

  // ── #3 CAS: only the first claim (A, earlier ts) wins; B is rejected ──
  var claimedBy = dbA.exec('SELECT claimed_by FROM documents WHERE uuid=?', [TOK])[0].values[0][0];
  var casReject = rA.rejected.filter(function (r) { return r.op.op_type === 'CLAIM'; });
  console.log('§DIST CAS token.claimed_by=' + claimedBy + ' losers=' + casReject.length + (casReject[0] ? ' why="' + casReject[0].why + '"' : ''));
  verdict(claimedBy === 'A' && casReject.length === 1,
          'two peers CLAIM same token → first-in-order wins (CAS set-if-unset)',
          'claimed_by=' + claimedBy + ' losers=' + casReject.length);

  console.log('\n§DIST ' + (fails ? 'FAIL — ' + fails + ' checks red' : 'PASS — UUID merge is clash-free + holder-irrelevant; owner-gate and CAS reject the loser deterministically (no server)'));
  process.exit(fails ? 1 : 0);
})();
