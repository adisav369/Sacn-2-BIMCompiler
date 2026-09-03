#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_chain.js — W-CHAIN acceptance (prompts/DISTRIBUTED_POC.md #1; DistributedERP.md §9-B).
 *   Proves: a per-op hash chain over kernel_ops makes the log tamper-EVIDENT — verifyChain()
 *   walks the ordered log and reports "tamper at op N" on any altered payload; a clean log
 *   reports "chain OK len=N". This is NEW — nothing in the codebase chains today (kernel_ops.js
 *   has no prev_hash). This POC doubles as the spec for that production change.
 *
 *   Determinism (§7): the op id / type / parameters are edge-minted INPUTS; the hash is a pure
 *   function of (prev_hash, canonical(op)). No Date.now()/Math.random() — every run is identical.
 *   Hash: SHA-256 (Node crypto here; browser kernel_ops.js port uses crypto.subtle, async).
 *
 *   Runs on sql.js (the BROWSER binding) against a kernel_ops table = drop-in for the real one.
 *
 * Run: node scripts/poc_chain.js 2>&1 | tee build/erp/poc_chain.log
 */
'use strict';
var crypto = require('crypto');
var initSqlJs = require('sql.js');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

// canonical(op) — fixed field order so the hash is stable across devices/replays.
function canonical(op) { return op.id + '|' + op.op_type + '|' + op.parameters; }
function sha256(s) { return crypto.createHash('sha256').update(s).digest('hex'); }
var GENESIS = '0'.repeat(64);

// chainOne — pure: the op's hash = SHA-256(prev_hash | canonical(op)). prev_hash links to the tip.
function chainOne(op, prevHash) {
  var prev = prevHash || GENESIS;
  return { prev_hash: prev, op_hash: sha256(prev + '|' + canonical(op)) };
}

var SCHEMA =
  'CREATE TABLE kernel_ops (' +
  ' id INTEGER PRIMARY KEY, op_type TEXT NOT NULL, parameters TEXT NOT NULL,' +
  ' prev_hash TEXT NOT NULL, op_hash TEXT NOT NULL)';

function commit(db, op) {
  var tipRow = db.exec('SELECT op_hash FROM kernel_ops ORDER BY id DESC LIMIT 1');
  var tip = (tipRow.length && tipRow[0].values.length) ? tipRow[0].values[0][0] : GENESIS;
  var c = chainOne(op, tip);
  db.run('INSERT INTO kernel_ops (id, op_type, parameters, prev_hash, op_hash) VALUES (?,?,?,?,?)',
         [op.id, op.op_type, op.parameters, c.prev_hash, c.op_hash]);
  return c.op_hash;
}

// verifyChain — walk ordered log; recompute each op_hash from (running prev | canonical).
// Detects (a) an altered payload (recomputed hash != stored) and (b) a broken prev_hash link.
function verifyChain(db) {
  var r = db.exec('SELECT id, op_type, parameters, prev_hash, op_hash FROM kernel_ops ORDER BY id');
  if (!r.length) return { ok: true, len: 0 };
  var rows = r[0].values, prev = GENESIS;
  for (var i = 0; i < rows.length; i++) {
    var op = { id: rows[i][0], op_type: rows[i][1], parameters: rows[i][2] };
    var storedPrev = rows[i][3], storedHash = rows[i][4];
    if (storedPrev !== prev) return { ok: false, brokeAt: op.id, why: 'prev_hash link' };
    var recomputed = sha256(prev + '|' + canonical(op));
    if (recomputed !== storedHash) return { ok: false, brokeAt: op.id, why: 'payload altered' };
    prev = storedHash;
  }
  return { ok: true, len: rows.length, tip: prev };
}

(async function () {
  var SQL = await initSqlJs();
  console.log('═══ POC-CHAIN — W-CHAIN: tamper-evident kernel_ops hash chain ═══\n');

  // ── fixture: 5 edge-minted ops (ids/params are INPUTS, not computed) ──
  var OPS = [
    { id: 1, op_type: 'SESSION_START', parameters: '{"ts":"2026-05-31T09:00:00Z"}' },
    { id: 2, op_type: 'SELL',          parameters: '{"item":"burger","qty":1}' },
    { id: 3, op_type: 'SELL',          parameters: '{"item":"cola","qty":2}' },
    { id: 4, op_type: 'AD_SAVE',       parameters: '{"table":"C_Order","id":"u7-aaa","new":42}' },
    { id: 5, op_type: 'SELL',          parameters: '{"item":"fries","qty":1}' }
  ];

  // CASE 1 — build a clean chain → verify OK len=N.
  var db1 = new SQL.Database(); db1.run(SCHEMA);
  var tip; OPS.forEach(function (op) { tip = commit(db1, op); });
  console.log('§CHAIN built len=' + OPS.length + ' tip=' + tip.slice(0, 12) + '…');
  var v1 = verifyChain(db1);
  console.log('§CHAIN verify clean ok=' + v1.ok + ' len=' + v1.len);
  verdict(v1.ok && v1.len === OPS.length, 'clean log → chain OK len=' + OPS.length, 'ok=' + v1.ok + ' len=' + v1.len);

  // CASE 2 — determinism: rebuild identical fixture in a fresh DB → identical tip hash.
  var db2 = new SQL.Database(); db2.run(SCHEMA);
  var tip2; OPS.forEach(function (op) { tip2 = commit(db2, op); });
  console.log('§CHAIN determinism tipA=' + tip.slice(0,12) + '… tipB=' + tip2.slice(0,12) + '…');
  verdict(tip === tip2, 'same ops → same tip hash (deterministic, holder-irrelevant)', 'A==B:' + (tip === tip2));

  // CASE 3 — tamper op N's payload AFTER chaining → verify breaks at EXACTLY op N.
  var N = 3;
  db1.run("UPDATE kernel_ops SET parameters = ? WHERE id = ?", ['{"item":"cola","qty":9999}', N]);
  var v3 = verifyChain(db1);
  console.log('§CHAIN tamper op=' + N + ' detected=' + (!v3.ok) + ' brokeAt=' + v3.brokeAt + ' why=' + v3.why);
  verdict(!v3.ok && v3.brokeAt === N, 'altered op ' + N + ' → tamper detected at exactly op ' + N, 'brokeAt=' + v3.brokeAt);

  // CASE 4 — ops BEFORE the tamper still verify; the break is localised, not global.
  //   Rebuild clean, tamper the LAST op → first N-1 ops would verify up to the break.
  var db3 = new SQL.Database(); db3.run(SCHEMA);
  OPS.forEach(function (op) { commit(db3, op); });
  db3.run("UPDATE kernel_ops SET parameters = ? WHERE id = ?", ['{"item":"fries","qty":7}', 5]);
  var v4 = verifyChain(db3);
  console.log('§CHAIN localised tamper@5 brokeAt=' + v4.brokeAt);
  verdict(!v4.ok && v4.brokeAt === 5, 'tamper localises to the altered op (break at 5, not earlier)', 'brokeAt=' + v4.brokeAt);

  console.log('\n§CHAIN ' + (fails ? 'FAIL — ' + fails + ' checks red' : 'PASS — per-op hash chain is tamper-evident: clean→OK, any altered op→detected at exactly that op, deterministic across rebuilds'));
  process.exit(fails ? 1 : 0);
})();
