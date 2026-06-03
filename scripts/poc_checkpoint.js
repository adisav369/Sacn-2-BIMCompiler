#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (I-D residual, W-CHECKPOINT)
// poc_checkpoint.js — the witness for ENGINE_FULL_ERP_ISSUES.md §I-D-CKPT (period-close signed checkpoint).
//
// ISSUE IT PROVES (names it, per CLAUDE.md "tests expose issues"):
//   §I-D residual — the LIVE kernel `verifyChain` SELECTs the WHOLE kernel_ops log and re-hashes EVERY row
//   from GENESIS on every call → cost ∝ TOTAL history, not ∝ the open period. The checkpoint *concept* is
//   proven on free-standing fixtures (poc_volume §VOL-BOOTSTRAP, poc_showstopper S3 §SHOW-CKPT) but NO
//   checkpoint primitive exists in the kernel. This witness drives a SIGNED period-close checkpoint
//   (ERP.md §18.9 + HolyGrail.md §1 "balance brought forward") through the REAL build/erp/kernel_ops.js and
//   proves verify is bounded to the open period (FLAT across periods) WITHOUT losing tamper-evidence.
//
// WHAT IT PROVES (each NAMED):
//   C0  closePeriod + latestCheckpoint + verifyChain(opts.fromCheckpoint) are PRESENT (the gap is filled).
//   C1  BOUND (the I-D win, MEASURED) — full verifyChain ms GROWS ∝ total history; bounded stays FLAT ∝ period.
//   C2  OPEN-PERIOD TAMPER still caught — tamper an op after the last checkpoint → bounded verify breaks at it.
//   C3  ARCHIVE RE-FOLD reconciles (HolyGrail §1) — the signed checkpoint head == the sealed op_hash at close;
//       tamper a COLD (pre-checkpoint) op → FULL verify catches it; bounded TRUSTS the signed anchor (the trade).
//   C4  FORGED CHECKPOINT rejected — alter the signed anchor (head) → bounded verify rejects before trusting it.
//   C5  DETERMINISM — rebuild the same scenario twice → byte-identical checkpoint head + balances_digest.
//   C6  NON-REGRESSION — default verifyChain(db) (no opts) unchanged (the 5 guardrail witnesses stay GREEN).
//
// NON-INVENT: the kernel under test is the REAL build/erp/kernel_ops.js (window-shim load, poc_opgroup pattern).
//   Closing balances are the witness's OWN fold of the committed Fact_Acct rows — the kernel never computes or
//   fabricates balances, it only ANCHORS + SIGNS what it is handed. Divergence/flatness are MEASURED, not asserted.
//
// DETERMINISM (§7): no Math.random in the data; every amount/id/baseTs is index-derived; commitGroup is called
//   with an explicit baseTs so the hashed `timestamp` is fixed. TIME (process.hrtime) is the measured OUTPUT.
//   ECDSA sigs are non-deterministic (random k) by design → C5 compares the deterministic head + digest, not sig.
//
// Run: node scripts/poc_checkpoint.js 2>&1 | tee build/erp/poc_checkpoint.log   — then READ the log (Log Mandate).
'use strict';
var path = require('path');
var nodeCrypto = require('crypto');
var initSqlJs = require('sql.js');

// load the engine the BROWSER loads (UMD → window globals), via a window shim (poc_opgroup.js pattern).
global.window = global.window || {};
global.crypto = global.crypto || nodeCrypto.webcrypto;   // kernel_ops.js seals via bare `crypto.subtle`
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));   // window.KernelOps (sealed log)
var KO = global.window.KernelOps;

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function c(cents) { return (cents < 0 ? '-' : '') + Math.abs(cents / 100).toFixed(2); }
function ms() { return Number(process.hrtime.bigint()) / 1e6; }
function f1(x) { return x.toFixed(1); }

// ── ECDSA P-256 signer (poc_sign.js shape) — the controller key that signs the period-close checkpoint ──
// matches the kernel's setSigner contract: { sign: async(hashHex)->sigHex, verify: async(hashHex,sigHex)->bool }.
function makeSigner(kp) {
  return {
    sign: async function (hashHex) { return nodeCrypto.sign('sha256', Buffer.from(hashHex), kp.privateKey).toString('hex'); },
    verify: async function (hashHex, sigHex) { try { return nodeCrypto.verify('sha256', Buffer.from(hashHex), kp.publicKey, Buffer.from(sigHex, 'hex')); } catch (e) { return false; } }
  };
}

// ── async micro-bench: warm once (JIT), then MIN of `reps` awaited runs (steady-state, scheduler-noise-removed) ──
async function abench(fn, reps) { await fn(); var best = Infinity, r = reps || 3; for (var i = 0; i < r; i++) { var t = ms(); await fn(); var d = ms() - t; if (d < best) best = d; } return best; }

function rowCount(db) { var r = db.exec('SELECT COUNT(*) FROM kernel_ops'); return r.length ? Number(r[0].values[0][0]) : 0; }

// fold the COMMITTED kernel_ops log → ledger balances (Fact_Acct rows only; the witness's OWN fold — non-invent).
function foldBalances(db) {
  var bal = {};
  var r = db.exec("SELECT parameters FROM kernel_ops WHERE undone=0 ORDER BY id");
  if (!r.length) return bal;
  r[0].values.forEach(function (row) {
    var p; try { p = JSON.parse(row[0]); } catch (e) { return; }
    if (p && p.table === 'Fact_Acct' && p.fields && typeof p.fields.Amount === 'number') {
      bal[p.fields.Account] = (bal[p.fields.Account] || 0) + p.fields.Amount;
    }
  });
  return bal;
}
function balSum(bal) { var s = 0; for (var a in bal) s += bal[a]; return s; }

// one document-event = a balanced journal pair (Dr Cash +amt / Cr Revenue −amt) committed as ONE op-group (§18.8).
// index-derived amount (no Math.random); explicit baseTs → the hashed timestamp is fixed (deterministic chain).
function docGroup(k, i) {
  var amt = (((k * 1000 + i) % 97) + 1) * 100;   // cents, 100..9700
  var gid = 'p' + k + '-d' + i;
  var ops = [
    { op_type: 'CRUD_CREATE', op_uuid: gid + '-dr', params: { table: 'Fact_Acct', op_type: 'CRUD_CREATE', fields: { Account: 'Cash', Amount: amt } } },
    { op_type: 'CRUD_CREATE', op_uuid: gid + '-cr', params: { table: 'Fact_Acct', op_type: 'CRUD_CREATE', fields: { Account: 'Revenue', Amount: -amt } } }
  ];
  return { gid: gid, ops: ops, baseTs: 1000 + k * 100000 + i * 10 };
}

// commit P documents of period k QUIETLY (silence the kernel's per-op chatter so the §CHECKPOINT verdicts read
// clean) — reports the suppressed count, so nothing is hidden (Log Mandate honoured at the summary granularity).
async function commitPeriodQuiet(db, k, P) {
  var realLog = console.log, suppressed = 0;
  console.log = function () { suppressed++; };
  try {
    for (var i = 0; i < P; i++) { var d = docGroup(k, i); await KO.commitGroup(db, d.ops, { gid: d.gid, baseTs: d.baseTs }); }
  } finally { console.log = realLog; }
  console.log('§CKPT-GEN period=' + k + ' committedDocs=' + P + ' ops=' + (P * 2) + ' (kernel chatter suppressed=' + suppressed + ' lines)');
}

// build the FULL scenario into a fresh db (used twice for C5 determinism); returns the period checkpoints.
async function buildScenario(SQL, K, P) {
  var db = new SQL.Database(); KO.ensureTable(db);
  var ckpts = [], bounded = [], full = [];
  for (var k = 1; k <= K; k++) {
    await commitPeriodQuiet(db, k, P);
    // open period = ops appended since the previous checkpoint. Measure BEFORE closing period k.
    var b = await abench(function () { return KO.verifyChain(db, { fromCheckpoint: true }); }, 3);
    var fz = await abench(function () { return KO.verifyChain(db); }, 3);
    bounded.push(b); full.push(fz);
    var ck = await KO.closePeriod(db, { balances: foldBalances(db) });
    ckpts.push(ck);
  }
  return { db: db, ckpts: ckpts, bounded: bounded, full: full };
}

(async function () {
  console.log('═══ POC-CHECKPOINT — §I-D period-close signed checkpoint on the LIVE kernel (verify bounded) ═══');
  console.log('issue=ENGINE_FULL_ERP_ISSUES.md §I-D  kernel=build/erp/kernel_ops.js  doctrine=ERP.md §18.9 + HolyGrail.md §1\n');
  var SQL = await initSqlJs();
  var controller = nodeCrypto.generateKeyPairSync('ec', { namedCurve: 'P-256' });
  KO.setSigner(makeSigner(controller));   // HolyGrail §1: the checkpoint is "signed by the controller"

  // ── C0 · the kernel now EXPOSES the checkpoint primitive ──────────────────────────────────────────
  console.log('C0 — does the kernel offer a period-close checkpoint + a bounded verify?');
  var hasClose = typeof KO.closePeriod === 'function';
  var hasLatest = typeof KO.latestCheckpoint === 'function';
  console.log('§CHECKPOINT primitive closePeriod=' + (hasClose ? 'present' : 'ABSENT') +
              ' latestCheckpoint=' + (hasLatest ? 'present' : 'ABSENT') + ' verifyChain(opts)=' + (typeof KO.verifyChain === 'function' ? 'present' : 'ABSENT'));
  verdict(hasClose && hasLatest, 'kernel exposes closePeriod(db,{balances}) + latestCheckpoint(db) + bounded verifyChain(db,{fromCheckpoint})',
          hasClose ? 'present' : 'NO checkpoint primitive — §I-D residual still open');
  if (!hasClose) { console.log('\n§CHECKPOINT FAIL — checkpoint primitive ABSENT; spec written, kernel change pending.'); process.exit(1); }

  var K = 4, P = 120;
  console.log('\nScenario: K=' + K + ' periods × P=' + P + ' docs (2 ops each) = ' + (K * P * 2) + ' ops, signed checkpoint at each close.');

  // ── C1 · BOUND — full verify GROWS ∝ total history; bounded stays FLAT ∝ open period (the I-D win) ──
  console.log('\nC1 — verify cost: full (∝ total history) vs from-checkpoint (∝ open period):');
  var built = await buildScenario(SQL, K, P);
  var db = built.db;
  for (var k = 0; k < K; k++) {
    console.log('§CHECKPOINT-BOUND period=' + (k + 1) + ' totalOps=' + ((k + 1) * P * 2) + ' fullVerifyMs=' + f1(built.full[k]) +
                ' boundedVerifyMs=' + f1(built.bounded[k]) + ' speedup=' + f1(built.full[k] / Math.max(built.bounded[k], 0.001)) + '× (min-of-3)');
  }
  var bMin = Math.min.apply(null, built.bounded), bMax = Math.max.apply(null, built.bounded);
  var boundedFlat = (bMax / Math.max(bMin, 0.001)) < 3;                 // bounded ~flat across K periods (∝ P, not total)
  var fullGrows = built.full[K - 1] > built.full[0] * 2;                // full verify grows with total history
  console.log('§CHECKPOINT-BOUND summary boundedMs=[' + built.bounded.map(f1).join(', ') + '] (flat=' + (boundedFlat ? 'Y' : 'N') +
              ') fullMs=[' + built.full.map(f1).join(', ') + '] (grows=' + (fullGrows ? 'Y' : 'N') + ')');
  verdict(boundedFlat && fullGrows, 'BOUNDED verify stays FLAT as history grows ' + K + '× (∝ open period) while FULL verify grows — the checkpoint IS the bound (§I-D win)',
          'flat=' + boundedFlat + ' grows=' + fullGrows);
  if (!boundedFlat) console.log('   ⚠ I-D NOT closed — bounded verify did not stay flat; verify is not bounded by the checkpoint as claimed.');

  // sanity: books balance (Σ=0) and the chain is clean before the tamper tests.
  var bal0 = foldBalances(db); var v0 = await KO.verifyChain(db);
  console.log('§CHECKPOINT sanity balSum=' + c(balSum(bal0)) + ' fullVerify.ok=' + v0.ok + ' len=' + v0.len + ' checkpoints=' + built.ckpts.length);
  verdict(balSum(bal0) === 0 && v0.ok === true, 'books balance (Σ=0) and full chain verifies clean before tamper tests', 'Σ=' + c(balSum(bal0)) + ' ok=' + v0.ok);

  built.ckpts.forEach(function (ck, i) {
    console.log('§CHECKPOINT-CKPT period=' + (i + 1) + ' atOpId=' + ck.atOpId + ' head=' + String(ck.headHash).slice(0, 12) + '… balDigest=' + String(ck.balancesDigest).slice(0, 12) + '… signed=' + ck.signed);
  });
  var lastCk = built.ckpts[built.ckpts.length - 1];

  // ── C2 · OPEN-PERIOD TAMPER still caught — append a fresh open period on top of the last checkpoint, ──
  //   tamper one of its ops, and prove the BOUNDED verify catches it at the exact op (tamper-evidence holds).
  console.log('\nC2 — tamper an op in the OPEN period (after the last checkpoint) → bounded verify breaks at it');
  await commitPeriodQuiet(db, K + 1, 5);                   // 5 docs = 10 ops, the new open period above lastCk
  // tamper the GROUP-HEAD op ('-dr' is the first op of its 2-op doc-group) so brokeAt == the tampered id
  // (the group-torn rule reports brokeAt = the group head — see poc_opgroup G5).
  var openTgt = db.exec("SELECT id, parameters FROM kernel_ops WHERE op_uuid='p" + (K + 1) + "-d2-dr'");
  var openId = openTgt[0].values[0][0];
  var openForged = JSON.parse(openTgt[0].values[0][1]); openForged.fields.Amount = -1;
  db.run('UPDATE kernel_ops SET parameters=? WHERE id=?', [JSON.stringify(openForged), openId]);
  var vOpen = await KO.verifyChain(db, { fromCheckpoint: true });
  console.log('§CHECKPOINT-TAMPER-OPEN tamperId=' + openId + ' scannedFrom=' + vOpen.scannedFrom + ' (after atOpId=' + lastCk.atOpId + ') ok=' + vOpen.ok + ' why=' + vOpen.why + ' brokeAt=' + vOpen.brokeAt);
  verdict(vOpen.ok === false && vOpen.brokeAt === openId && vOpen.scannedFrom === lastCk.atOpId && vOpen.why === 'group torn',
          'bounded verify (from last checkpoint) CATCHES an open-period tamper at the exact op (group head) — tamper-evidence preserved within the bound',
          'ok=' + vOpen.ok + ' brokeAt=' + vOpen.brokeAt + ' (want ' + openId + ') why=' + vOpen.why);
  // repair the open-period op so the next checks start from a clean chain.
  db.run('UPDATE kernel_ops SET parameters=? WHERE id=?', [openTgt[0].values[0][1], openId]);
  await KO.sealChain(db);

  // ── C3 · ARCHIVE RE-FOLD reconciles (HolyGrail §1) — the signed head == the sealed op_hash at close; ──
  //   a tamper in the COLD archive (before the last checkpoint) is caught by FULL verify; bounded TRUSTS the
  //   signed anchor and does NOT re-walk the cold archive (the stated trade, made safe by the signature → C4).
  console.log('\nC3 — signed head == sealed op_hash at close; cold-archive tamper caught by FULL verify (the trade)');
  var anchorRow = db.exec('SELECT op_hash FROM kernel_ops WHERE id=' + lastCk.atOpId);
  var anchorHash = anchorRow[0].values[0][0];
  console.log('§CHECKPOINT-ANCHOR atOpId=' + lastCk.atOpId + ' sealedOpHash=' + String(anchorHash).slice(0, 12) + '… signedHead=' + String(lastCk.headHash).slice(0, 12) + '… match=' + (anchorHash === lastCk.headHash));
  verdict(anchorHash === lastCk.headHash, 'the SIGNED checkpoint head equals the sealed op_hash at the close tip (the chain head is what was carried forward)',
          'match=' + (anchorHash === lastCk.headHash));
  // tamper a COLD op (period 1, id well below the last checkpoint) and re-fold.
  var coldTgt = db.exec("SELECT id, parameters FROM kernel_ops WHERE op_uuid='p1-d3-dr'");  // group head → brokeAt == coldId
  var coldId = coldTgt[0].values[0][0];
  var coldForged = JSON.parse(coldTgt[0].values[0][1]); coldForged.fields.Amount = -1;
  db.run('UPDATE kernel_ops SET parameters=? WHERE id=?', [JSON.stringify(coldForged), coldId]);
  var vFull = await KO.verifyChain(db);                         // FULL audit path — re-walks the cold archive
  var vBound = await KO.verifyChain(db, { fromCheckpoint: true }); // bounded — trusts the anchor, skips the archive
  console.log('§CHECKPOINT-COLD-TAMPER coldId=' + coldId + ' (< atOpId=' + lastCk.atOpId + ')  fullVerify.ok=' + vFull.ok + ' brokeAt=' + vFull.brokeAt +
              '  boundedVerify.ok=' + vBound.ok + ' (bounded does NOT re-walk the cold archive — the trade)');
  verdict(vFull.ok === false && vFull.brokeAt === coldId && vBound.ok === true,
          'cold-archive tamper: FULL verify proves fraud at the exact op (re-fold ≠ signed balance); bounded trusts the signed anchor (the honest trade)',
          'full.ok=' + vFull.ok + ' brokeAt=' + vFull.brokeAt + ' bounded.ok=' + vBound.ok);
  db.run('UPDATE kernel_ops SET parameters=? WHERE id=?', [coldTgt[0].values[0][1], coldId]);
  await KO.sealChain(db);

  // ── C4 · FORGED CHECKPOINT rejected — alter the SIGNED anchor (head) → bounded verify rejects before trusting ──
  console.log('\nC4 — forge the signed anchor (checkpoint head) → bounded verify rejects it (signature fails)');
  var ckRow = db.exec('SELECT id, head_hash FROM kernel_checkpoints ORDER BY id DESC LIMIT 1');
  var ckId = ckRow[0].values[0][0], origHead = ckRow[0].values[0][1];
  db.run("UPDATE kernel_checkpoints SET head_hash='" + 'd'.repeat(64) + "' WHERE id=" + ckId);
  var vForged = await KO.verifyChain(db, { fromCheckpoint: true });
  console.log('§CHECKPOINT-FORGED checkpointId=' + ckId + ' headForged=Y boundedVerify.ok=' + vForged.ok + ' why=' + vForged.why);
  verdict(vForged.ok === false && vForged.why === 'checkpoint signature',
          'a forged checkpoint anchor (head altered) is REJECTED by bounded verify before any op is trusted (signature is the guard)',
          'ok=' + vForged.ok + ' why=' + vForged.why);
  db.run("UPDATE kernel_checkpoints SET head_hash='" + origHead + "' WHERE id=" + ckId);

  // ── C5 · DETERMINISM — rebuild the SAME scenario in a fresh db → byte-identical head + balances_digest ──
  console.log('\nC5 — rebuild the same scenario twice → identical checkpoint head + balances_digest (determinism)');
  var built2 = await buildScenario(SQL, K, P);
  var ck1 = built.ckpts[K - 1], ck2 = built2.ckpts[K - 1];
  console.log('§CHECKPOINT-DET headA=' + String(ck1.headHash).slice(0, 16) + '… headB=' + String(ck2.headHash).slice(0, 16) +
              '… digestA=' + String(ck1.balancesDigest).slice(0, 12) + '… digestB=' + String(ck2.balancesDigest).slice(0, 12) + '…');
  verdict(ck1.headHash === ck2.headHash && ck1.balancesDigest === ck2.balancesDigest,
          'two independent rebuilds produce a byte-identical checkpoint head + balances_digest (deterministic; ECDSA sig varies by design, not compared)',
          'headEq=' + (ck1.headHash === ck2.headHash) + ' digestEq=' + (ck1.balancesDigest === ck2.balancesDigest));
  built2.db.close();

  // ── C6 · NON-REGRESSION — default verifyChain(db) (no opts) unchanged shape; bounded with NO checkpoint = full ──
  console.log('\nC6 — default verifyChain(db) (no opts) is the unchanged full walk; no-checkpoint fromCheckpoint falls back to full');
  var fresh = new SQL.Database(); KO.ensureTable(fresh);
  await commitPeriodQuiet(fresh, 9, 3);                         // 3 docs, NO checkpoint yet
  var vDefault = await KO.verifyChain(fresh);                   // default: full walk
  var vNoCk = await KO.verifyChain(fresh, { fromCheckpoint: true });  // no checkpoint → honest fallback to full
  console.log('§CHECKPOINT-NOREG default.ok=' + vDefault.ok + ' default.len=' + vDefault.len + ' default.fromCheckpoint=' + (vDefault.fromCheckpoint || false) +
              '  noCkpt.ok=' + vNoCk.ok + ' noCkpt.len=' + vNoCk.len + ' noCkpt.scannedFrom=' + vNoCk.scannedFrom);
  verdict(vDefault.ok === true && vDefault.len === 6 && !vDefault.fromCheckpoint && vNoCk.ok === true && vNoCk.len === 6 && vNoCk.scannedFrom === 0,
          'default verifyChain unchanged (full walk, no fromCheckpoint flag); fromCheckpoint with NO checkpoint falls back to a full walk from genesis (honest)',
          'default.len=' + vDefault.len + ' noCkpt.scannedFrom=' + vNoCk.scannedFrom);
  fresh.close(); db.close();

  console.log('\n§CHECKPOINT ' + (fails ? 'FAIL — ' + fails + ' checks red: §I-D period-close checkpoint NOT proven. '
    : 'PASS — §I-D period-close signed checkpoint holds on the live kernel: full verify grows ∝ total history while '
    + 'bounded verify stays FLAT ∝ open period (the I-D win); open-period tamper is still caught at the exact op; the '
    + 'signed head == the sealed close tip and a cold-archive tamper is proven by FULL verify (bounded trusts the signed '
    + 'anchor — the stated trade); a forged anchor is rejected; the checkpoint is deterministic; the default verify path '
    + 'is unchanged. Verify is bounded by the period, not the log (ERP.md §18.9 + HolyGrail.md §1).'));
  process.exit(fails ? 1 : 0);
})().catch(function (e) { console.error('FATAL', e); process.exit(2); });
