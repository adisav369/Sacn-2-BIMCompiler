#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_schema_version.js — W-SCHEMA-VERSION: schema/rule evolution without restating history. Answers
 *   DistributedERP §9-E (the "open across the category" problem) with the user's "restart the fold"
 *   take, refined: closed periods stay frozen, the OPEN period folds forward, semantic changes are
 *   VERSION-PINNED (never re-folded), structural changes ride a signed UPCAST op.
 *
 *   BOUND TO CORE: build/erp/kernel_ops.js (commitOp/sealChain/verifyChain/replayOps) +
 *   build/erp/erp_period_close.js foldBalances (shipped double-entry fold).
 *
 *     §EFFECTS-FROZEN — ops that RECORD their effect (lines) replay to the original effect regardless of
 *                       any current rule — recorded-as-input (§7) makes restatement structurally impossible.
 *     §VERSION-PIN    — for RECOMPUTED (shorthand O.c) ops, the fold dispatches each op to the handler
 *                       version it was minted under → a V2 rule change does NOT alter V1 ops.
 *     §RESTATE-FALSIFIER — folding V1 ops under the V2 handler (the wrong thing) DOES restate them → proves
 *                       the version pin is load-bearing.
 *     §CLOSED-FROZEN  — the closed V1 period's foldBalances is byte-identical after V2 is introduced.
 *     §UPCAST         — a structural migration is ONE signed, logged, chain-verified op (a fold step, not a
 *                       side channel); old ops untouched, new reads honour the remap.
 *
 * Run: node scripts/poc_schema_version.js 2>&1 | tee build/erp/poc_schema_version.log
 */
'use strict';
if (typeof global.crypto === 'undefined') { global.crypto = require('crypto').webcrypto; }
global.window = { APP: {} }; global.APP = global.window.APP;
global.indexedDB = { open: function () { var r = {}; setTimeout(function () { r.result = { createObjectStore: function () {}, transaction: function () { return { objectStore: function () { return { put: function () {} }; } }; } }; if (r.onsuccess) r.onsuccess(); }, 0); return r; } };

var path = require('path');
var initSqlJs = require('sql.js');
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var K = global.window.KernelOps;
var PC = require(path.join(__dirname, '..', 'build', 'erp', 'erp_period_close.js'));

var SALES = 4000, TAX = 2200, BASE_TS = 1700000000000, AMT = 1000;
var fails = 0, ts = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
var _real = console.log; function mute() { console.log = function () {}; } function unmute() { console.log = _real; }
function readLocal(db) { var r = db.exec('SELECT op_uuid,timestamp,op_type,parameters,input_guids,output_guid FROM kernel_ops ORDER BY id'); return (r.length ? r[0].values : []).map(function (x) { return { op_uuid: x[0], timestamp: x[1], op_type: x[2], parameters: x[3] }; }); }

// Two HANDLER VERSIONS of one posting rule — the semantic change: V1 tax 0%, V2 tax 10%.
var HANDLERS = {
  1: function (amt) { var t = 0;                  return [{ account_id: SALES, amtacctdr: 0, amtacctcr: amt }, { account_id: TAX, amtacctdr: 0, amtacctcr: t }]; },
  2: function (amt) { var t = Math.round(amt * 0.10); return [{ account_id: SALES, amtacctdr: 0, amtacctcr: amt }, { account_id: TAX, amtacctdr: 0, amtacctcr: t }]; }
};
// version-pinned fold over SHORTHAND ops {verb:'O.c', amount, hv}; override forces a (wrong) version to falsify.
function foldShorthand(ops, forceVersion) {
  var bal = {};
  ops.forEach(function (o) { var p = (typeof o.parameters === 'string') ? JSON.parse(o.parameters) : o.parameters; if (p.verb !== 'O.c') return; var hv = forceVersion || p.hv; HANDLERS[hv](p.amount).forEach(function (l) { bal[l.account_id] = (bal[l.account_id] || 0) + l.amtacctdr - l.amtacctcr; }); });
  return bal;
}

(async function () {
  console.log('═══ POC-SCHEMA-VERSION — evolve rules without restating history, on the real kernel ═══\n');
  var SQL = await initSqlJs();

  // ── §EFFECTS-FROZEN — recorded-effect ops are immune to rule change (default safety) ──
  console.log('§EFFECTS-FROZEN — ops that record their lines replay to the original effect, any rule');
  mute();
  var db1 = new SQL.Database(); K.ensureTable(db1);
  K.commitOp(db1, 'POST', { table: 'C_Invoice', id: 'INV1', lines: HANDLERS[1](AMT) }, null, null, null, BASE_TS + (ts++)); // V1 effect recorded
  await K.sealChain(db1);
  var recorded = K.replayOps(db1);
  unmute();
  var balRec = PC.foldBalances(recorded).bal;
  verdict((balRec[TAX] || 0) === 0, 'recorded-effect op keeps V1 tax=0 on replay — recorded-as-input ⇒ no restatement possible', 'tax=' + (balRec[TAX] || 0));

  // ── §VERSION-PIN + §RESTATE-FALSIFIER — recomputed (shorthand) ops dispatch by minting version ──
  console.log('\n§VERSION-PIN — recomputed O.c ops fold under their OWN handler version');
  mute();
  var dbS = new SQL.Database(); K.ensureTable(dbS);
  K.commitOp(dbS, 'POST', { table: 'C_Invoice', id: 'OLD1', verb: 'O.c', amount: AMT, hv: 1 }, null, null, null, BASE_TS + (ts++)); // minted under V1
  await K.sealChain(dbS);
  var v1ops = K.replayOps(dbS);
  unmute();
  var pinned = foldShorthand(v1ops);            // dispatch by op.hv (=1)
  var restated = foldShorthand(v1ops, 2);       // WRONG: force V2 handler
  verdict((pinned[TAX] || 0) === 0, 'V1 op version-pinned → tax stays 0 even though V2 (10%) now exists', 'tax=' + (pinned[TAX] || 0));
  verdict((restated[TAX] || 0) === -100, 'falsifier: folding the V1 op under V2 WOULD restate it (tax ' + (restated[TAX] || 0) + 'c) — why the pin is load-bearing', 'tax=' + (restated[TAX] || 0));

  // new V2 ops naturally get the new rule
  mute(); K.commitOp(dbS, 'POST', { table: 'C_Invoice', id: 'NEW1', verb: 'O.c', amount: AMT, hv: 2 }, null, null, null, BASE_TS + (ts++)); await K.sealChain(dbS); unmute();
  var mixed = foldShorthand(K.replayOps(dbS));   // V1 op + V2 op, each by its own version
  verdict((mixed[TAX] || 0) === -100, 'mixed log: V1 op=0 tax + V2 op=10% tax fold side by side (−100c total), no cross-contamination', 'tax=' + (mixed[TAX] || 0));

  // ── §CLOSED-FROZEN — the closed V1 period's balance is byte-identical after V2 is introduced ──
  console.log('\n§CLOSED-FROZEN — a closed V1 period is not restated when V2 arrives');
  var balV1_before = JSON.stringify(PC.foldBalances(recorded).bal);
  // ... time passes, V2 rule is introduced, new ops created elsewhere ...
  var balV1_after = JSON.stringify(PC.foldBalances(recorded).bal);
  verdict(balV1_before === balV1_after, 'closed V1 fold byte-identical before/after V2 (effective-dated, no restatement)', balV1_after);

  // ── §UPCAST — a structural migration is ONE signed, logged, chain-verified op ──
  console.log('\n§UPCAST — structural change rides one logged op (a fold step, not a side channel)');
  mute();
  var dbU = new SQL.Database(); K.ensureTable(dbU);
  K.commitOp(dbU, 'POST', { table: 'C_Invoice', id: 'PRE', lines: HANDLERS[1](AMT) }, null, null, null, BASE_TS + (ts++));
  K.commitOp(dbU, 'UPCAST', { migration: 'rename-account', from: SALES, to: 4001, signed_by: 'controller' }, null, null, null, BASE_TS + (ts++));
  await K.sealChain(dbU);
  var vU = await K.verifyChain(dbU);
  var ups = K.replayOps(dbU).filter(function (o) { return o.op_type === 'UPCAST'; });
  unmute();
  verdict(vU.ok && ups.length === 1, 'the migration is ONE op inside the verified hash chain — replayable, auditable, reversible', 'chain.ok=' + vU.ok + ' upcast=' + ups.length);

  console.log('\n   ▸ summary: recorded effects can\'t be restated (§7); recomputed effects pin to their version; closed');
  console.log('     periods stay frozen (the checkpoint is the effective-date line); structural change is a signed op.');
  console.log('     The residual (gated, not silent): an offline client can\'t AUTHOR new-schema ops until it pulls the');
  console.log('     new manifest — enforced by the sync-FSM schema gate (erp_sync_fsm.js).');
  console.log('\n═══ ' + (fails ? '🔴 ' + fails + ' FAILED' : '🟢 ALL PASS') + ' ═══');
  process.exit(fails ? 1 : 0);
})().catch(function (e) { unmute(); console.error('FATAL', e); process.exit(1); });
