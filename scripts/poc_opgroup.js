#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (Phase 2, W-OPGROUP)
// poc_opgroup.js — the witness for ENGINE_FULL_ERP_ISSUES.md §I-K (op-group atomicity).
//
// ISSUE IT PROVES (names it, per CLAUDE.md "tests expose issues"):
//   §I-K — a real document action (COMPLETE_ORDER = DOC_ACTION+SHIP+INVOICE+Dr-AR+Cr-Rev) is N ops that
//   must fold WHOLE or NONE. PHASE 1 showed the kernel had NO group primitive: commitOp commits one op
//   at a time + auto-seals, so a torn group HALF-POSTS the ledger (Σ≠0) yet the chain still verifies.
//   PHASE 2 lands kernel commitGroup(db, opsArray, groupMeta) — N ops under ONE group hash, all-or-none,
//   sealed ONCE per group (seal-from-tip). This witness drives the SAME torn + clean scenarios through
//   the NEW primitive and proves the gap is CLOSED.
//
// WHAT IT NOW PROVES (each NAMED):
//   G1  commitGroup primitive is PRESENT (the structural gap is filled).
//   G2  CONTRAST (Phase-1 path) — commitOp-by-op TEARS → HALF-POSTED ledger (why the primitive exists).
//   G3  torn op-group via commitGroup → ZERO rows committed, ledger Σ=0 (all-or-NONE at the storage tier).
//   G4  clean op-group via commitGroup → folds WHOLE; books balance (Σ=0).
//   G5  verifyChain REJECTS a post-commit tampered group → why='group torn' (group is the integrity unit).
//
// NON-INVENT: the op shape is built through the REAL CORE.buildOp (build/erp/crud_overlay.js), the same
//   COMPLETE_ORDER fan-out as scripts/poc_showstopper.js §SHOW-ATOMIC. The kernel under test is the REAL
//   build/erp/kernel_ops.js loaded under a window shim (the spike_writepath.js loading pattern). The
//   atomicity verdict is EXTRACTED from poc_showstopper: a torn group must leave the ledger as-if-never-
//   appended (Σ=0); per-op apply of a torn group UNBALANCES.
//
// DETERMINISM (§7): no Date.now()/Math.random() in the witness — every id/op_uuid/amount is index-derived,
//   gid is caller-supplied, and commitGroup is called with an explicit baseTs (the kernel never injects
//   wall-clock into a hashed field when baseTs is given). Balances fold from `parameters` only.
//
// Run: node scripts/poc_opgroup.js 2>&1 | tee build/erp/poc_opgroup.log   — then READ the log (Log Mandate).
'use strict';
var path = require('path');
var initSqlJs = require('sql.js');

// load the engine the BROWSER loads (UMD → window globals), via a window shim (spike_writepath.js pattern).
global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;   // kernel_ops.js seals via bare `crypto.subtle`
var CORE = require(path.join(__dirname, '..', 'build', 'erp', 'crud_overlay.js'));   // REAL op shape (module.exports)
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));               // window.KernelOps (sealed log)
var KO = global.window.KernelOps;

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function c(cents) { return (cents < 0 ? '-' : '') + Math.abs(cents / 100).toFixed(2); }

// ── op-shape entries (mirror poc_showstopper.js exactly — non-invent) ──────────────────────────────
var E_ORDER = { key: 'C_Order',   verbs: ['create', 'process'], fields: [{ col: 'DocumentNo', type: 'string' }, { col: 'GrandTotal', type: 'number' }], docAction: { action: 'CO', from: 'DR', to: 'CO', requires: ['GrandTotal'] } };
var E_SHIP  = { key: 'M_InOut',   verbs: ['create'], fields: [{ col: 'DocumentNo', type: 'string' }, { col: 'C_Order', type: 'string' }] };
var E_INV   = { key: 'C_Invoice', verbs: ['create'], fields: [{ col: 'DocumentNo', type: 'string' }, { col: 'GrandTotal', type: 'number' }] };
var E_FACT  = { key: 'Fact_Acct', verbs: ['create'], fields: [{ col: 'Account', type: 'string' }, { col: 'Amount', type: 'number' }] };
function fact(account, cents, uuid) { return CORE.buildOp('create', E_FACT, { Account: account, Amount: cents }, null, { opUuid: uuid }); }

// COMPLETE_ORDER op-group (ERP.md §18.8 / poc_showstopper gComplete): the 5 ops that must be all-or-none.
function completeOrderOps(no, cents) {
  return [
    CORE.buildOp('process', E_ORDER, { DocumentNo: 'SO-' + no, GrandTotal: cents }, null, { id: 'SO-' + no, opUuid: 'og-' + no + '-act' }),
    CORE.buildOp('create',  E_SHIP,  { DocumentNo: 'SH-' + no, C_Order: 'SO-' + no }, null, { opUuid: 'og-' + no + '-ship' }),
    CORE.buildOp('create',  E_INV,   { DocumentNo: 'INV-' + no, GrandTotal: cents }, null, { opUuid: 'og-' + no + '-inv' }),
    fact('AR',      cents, 'og-' + no + '-jAR'),     // Dr AR
    fact('Revenue', -cents, 'og-' + no + '-jRev')    // Cr Revenue  (Σ with Dr-AR = 0 only if BOTH land)
  ];
}

// map a CORE op → the commitGroup op shape ({ op_type, params, op_uuid }) — the same `params` envelope
// commitOp records (table/op_type/fields/action/id), so the fold reads it identically.
function toGroupOp(op) {
  return {
    op_type: op.op_type,
    op_uuid: op.op_uuid,
    params: { table: op.table, op_type: op.op_type, fields: op.fields || null,
              action: op.action || null, id: op.id || null }
  };
}

// fold the COMMITTED kernel_ops log → ledger balances (only Fact_Acct rows carry balance; extract-only).
// Mirrors poc_showstopper.applyOp: Σ over all Account balances must be 0 for a balanced book.
function foldCommittedBalances(db) {
  var bal = {};
  var r = db.exec("SELECT op_type, parameters FROM kernel_ops WHERE undone=0 ORDER BY id");
  if (!r.length) return bal;
  r[0].values.forEach(function (row) {
    var p; try { p = JSON.parse(row[1]); } catch (e) { return; }
    if (p && p.table === 'Fact_Acct' && p.fields && typeof p.fields.Amount === 'number') {
      bal[p.fields.Account] = (bal[p.fields.Account] || 0) + p.fields.Amount;
    }
  });
  return bal;
}
function balSum(bal) { var s = 0; for (var a in bal) s += bal[a]; return s; }
function rowCount(db) { var r = db.exec('SELECT COUNT(*) FROM kernel_ops'); return r.length ? Number(r[0].values[0][0]) : 0; }

(async function () {
  console.log('═══ POC-OPGROUP — §I-K op-group atomicity on the LIVE kernel (commitGroup) ═══');
  console.log('issue=ENGINE_FULL_ERP_ISSUES.md §I-K  kernel=build/erp/kernel_ops.js  opshape=build/erp/crud_overlay.js\n');
  var SQL = await initSqlJs();

  // ── G1 · the kernel now EXPOSES the group primitive ─────────────────────────────────────────────
  console.log('G1 — does the kernel offer all-or-none for an op-group?');
  var hasGroup = typeof KO.commitGroup === 'function';
  var hasSealFrom = typeof KO.sealFrom === 'function';
  console.log('§OPGROUP primitive commitGroup=' + (hasGroup ? 'present' : 'ABSENT') + ' sealFrom=' + (hasSealFrom ? 'present' : 'ABSENT') +
              ' commitOp=' + (typeof KO.commitOp === 'function' ? 'present(one-op-at-a-time)' : 'ABSENT'));
  verdict(hasGroup && hasSealFrom, 'kernel exposes commitGroup(db, opsArray, groupMeta) + sealFrom (all-or-none + seal-from-tip)',
          hasGroup ? 'present' : 'NO group primitive');

  // ── G2 · CONTRAST — drive COMPLETE_ORDER op-by-op via commitOp, TEAR mid-group → HALF-POSTED ─────
  //   This is the Phase-1 failure preserved as the "before": it shows WHY commitGroup is needed.
  console.log('\nG2 — CONTRAST (Phase-1 path): commit COMPLETE_ORDER op-by-op via commitOp; tear after Dr-AR');
  var dbA = new SQL.Database(); KO.ensureTable(dbA);
  var opsA = completeOrderOps('1', 10000);   // 100.00: Dr-AR +100.00, Cr-Revenue -100.00 (balanced ONLY if both land)
  var TEAR_AT = 4;                            // index of the LAST op (Cr-Revenue) — it never lands (the "tear")
  var committedA = 0;
  for (var i = 0; i < opsA.length; i++) {
    if (i === TEAR_AT) {
      console.log('§OPGROUP tear at op[' + i + ']=' + opsA[i].op_uuid + ' (Cr-Revenue) — write aborts; commitOp has no group rollback');
      break;
    }
    var g = toGroupOp(opsA[i]);
    KO.commitOp(dbA, g.op_type, g.params, null, g.op_uuid, g.op_uuid);
    committedA++;
  }
  await KO.sealChain(dbA);   // the per-op auto-seal is debounced in-browser; force the at-rest seal here.
  var balA = foldCommittedBalances(dbA), sumA = balSum(balA);
  console.log('§OPGROUP contrast commitOp-by-op committedOps=' + committedA + '/' + opsA.length + ' kernelRows=' + rowCount(dbA) +
              ' ledger={AR=' + c(balA.AR || 0) + ',Revenue=' + c(balA.Revenue || 0) + '} balSum=' + c(sumA));
  verdict(sumA !== 0, 'CONTRAST: commitOp-by-op torn group HALF-POSTS the ledger (Σ≠0 ⇒ why commitGroup exists)',
          'committed=' + committedA + ' balSum=' + c(sumA));
  dbA.close();

  // ── G3 · TORN op-group via commitGroup → ZERO rows committed, ledger Σ=0 (all-or-NONE) ───────────
  //   The tear: the caller asserts an expectedHash that the kernel-recomputed group hash will NOT match
  //   (a torn/forged payload at the edge). The kernel must reject the WHOLE group before any INSERT.
  console.log('\nG3 — torn op-group via commitGroup (expectedHash mismatch) → ZERO rows, Σ=0');
  var dbB = new SQL.Database(); KO.ensureTable(dbB);
  var rowsBefore = rowCount(dbB);
  var groupOpsB = completeOrderOps('2', 10000).map(toGroupOp);
  var resTorn = await KO.commitGroup(dbB, groupOpsB, { gid: 'og-2', expectedHash: 'deadbeef'.repeat(8), baseTs: 1000 });
  var rowsAfterTorn = rowCount(dbB);
  var balB = foldCommittedBalances(dbB), sumB = balSum(balB);
  console.log('§OPGROUP torn committed=' + resTorn.committed + ' reason=' + (resTorn.reason || '-') +
              ' rowsBefore=' + rowsBefore + ' rowsAfter=' + rowsAfterTorn + ' ledger={AR=' + c(balB.AR || 0) +
              ',Revenue=' + c(balB.Revenue || 0) + '} balSum=' + c(sumB));
  verdict(resTorn.committed === false && rowsAfterTorn === rowsBefore && sumB === 0,
          'torn op-group commits ZERO rows (all-or-NONE): no half-post, Σ=0',
          'committed=' + resTorn.committed + ' Δrows=' + (rowsAfterTorn - rowsBefore) + ' Σ=' + c(sumB));

  // ── G4 · CLEAN op-group via commitGroup → folds WHOLE; books balance (Σ=0) ───────────────────────
  console.log('\nG4 — clean op-group via commitGroup → all N ops land WHOLE; Σ=0');
  var groupOpsC = completeOrderOps('3', 10000).map(toGroupOp);
  var resClean = await KO.commitGroup(dbB, groupOpsC, { gid: 'og-3', baseTs: 2000 });
  var rowsAfterClean = rowCount(dbB);
  var balC = foldCommittedBalances(dbB), sumC = balSum(balC);
  console.log('§OPGROUP clean committed=' + resClean.committed + ' gid=' + resClean.gid + ' ops=' + resClean.ids.length +
              ' rowsAfter=' + rowsAfterClean + ' sealed=' + resClean.sealed + ' ledger={AR=' + c(balC.AR || 0) +
              ',Revenue=' + c(balC.Revenue || 0) + '} balSum=' + c(sumC));
  verdict(resClean.committed === true && resClean.ids.length === groupOpsC.length && rowsAfterClean === rowsBefore + groupOpsC.length && sumC === 0,
          'clean op-group folds WHOLE (all N committed) and books balance (Σ=0)',
          'committed=' + resClean.ids.length + '/' + groupOpsC.length + ' Σ=' + c(sumC));

  // verifyChain over the clean group must hold (the chain seals the WHOLE group correctly).
  var vClean = await KO.verifyChain(dbB);
  console.log('§OPGROUP verifyChain(after clean) ok=' + (vClean.ok ? 'Y' : 'N') + ' len=' + vClean.len);
  verdict(vClean.ok === true, 'verifyChain OK over a cleanly-committed group (chain intact)', 'ok=' + (vClean.ok ? 'Y' : 'N'));

  // idempotency: re-commit the SAME gid → no-op, no duplicate rows.
  var resReplay = await KO.commitGroup(dbB, groupOpsC, { gid: 'og-3', baseTs: 2000 });
  var rowsAfterReplay = rowCount(dbB);
  console.log('§OPGROUP idempotent re-commit gid=og-3 committed=' + resReplay.committed + ' idempotent=' + !!resReplay.idempotent +
              ' rowsAfter=' + rowsAfterReplay + ' (== ' + rowsAfterClean + '?)');
  verdict(resReplay.idempotent === true && rowsAfterReplay === rowsAfterClean,
          're-committing a present gid is a no-op (idempotent, no duplicate rows)',
          'idempotent=' + !!resReplay.idempotent + ' Δrows=' + (rowsAfterReplay - rowsAfterClean));

  // ── G5 · verifyChain REJECTS a post-commit TAMPERED group → why='group torn' ────────────────────
  //   Forge one journal op's payload after commit (the poc_chain.js "UPDATE … SET parameters" attack).
  //   The group is the integrity unit: verifyChain must report the WHOLE group broken, brokeAt = group head.
  console.log('\nG5 — tamper ONE op of a committed group → verifyChain rejects the WHOLE group');
  // find the Cr-Revenue op of gid og-3 and forge its amount.
  var tgt = dbB.exec("SELECT id, parameters FROM kernel_ops WHERE op_uuid='og-3-jRev'");
  var tgtId = tgt[0].values[0][0];
  var forged = JSON.parse(tgt[0].values[0][1]); forged.fields.Amount = -1;   // 100.00 credit → 0.01
  dbB.run('UPDATE kernel_ops SET parameters=? WHERE id=?', [JSON.stringify(forged), tgtId]);
  var vTorn = await KO.verifyChain(dbB);
  // brokeAt must be the FIRST op of og-3 (group head), not the tampered op alone.
  var headRow = dbB.exec("SELECT MIN(id) FROM kernel_ops WHERE gid='og-3'");
  var groupHead = headRow[0].values[0][0];
  console.log('§OPGROUP tamper id=' + tgtId + ' (og-3-jRev) verifyChain ok=' + (vTorn.ok ? 'Y' : 'N') +
              ' why=' + vTorn.why + ' gid=' + vTorn.gid + ' brokeAt=' + vTorn.brokeAt + ' groupHead=' + groupHead + ' failAt=' + vTorn.failAt);
  verdict(vTorn.ok === false && vTorn.why === 'group torn' && vTorn.gid === 'og-3' && vTorn.brokeAt === groupHead,
          'verifyChain rejects a tampered group WHOLE: why=group torn, brokeAt=group head (group is the integrity unit)',
          'ok=' + vTorn.ok + ' why=' + vTorn.why + ' brokeAt=' + vTorn.brokeAt);
  dbB.close();

  console.log('\n§OPGROUP ' + (fails ? 'FAIL — ' + fails + ' checks red: §I-K NOT proven. '
    : 'PASS — §I-K op-group atomicity holds on the live kernel: a torn group commits 0 rows (Σ=0), a clean '
    + 'group folds WHOLE (Σ=0), and verifyChain rejects a tampered group WHOLE (group is the integrity unit). '
    + 'commitGroup = N ops, ONE group hash, all-or-none, sealed once per group (seal-from-tip).'));
  process.exit(fails ? 1 : 0);
})().catch(function (e) { console.error('FATAL', e); process.exit(2); });
