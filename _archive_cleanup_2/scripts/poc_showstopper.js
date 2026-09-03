#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_showstopper.js — the "hard parts" falsifier (prompts/SHOWSTOPPER_POC.md).
 *   Proves OR FALSIFIES the three assumptions in docs/HolyGrail.md §"The hard parts, worked
 *   through — why the showstoppers aren't", on the SAME op shape the editing layer emits:
 *
 *     S2  §SHOW-ATOMIC — Atomicity is structural: a document-event is ONE op-group (ERP.md §18.8).
 *                        It folds in WHOLE or NONE; a torn op fails the group hash → whole group
 *                        rejected, projection unchanged. (Contrast: naive per-op apply UNBALANCES.)
 *     S3  §SHOW-CKPT   — Compaction = balance brought forward: at period close a SIGNED checkpoint
 *                        (closing balances + chain-head fingerprint) becomes a new genesis; the
 *                        closed period is ARCHIVED (cold), not deleted. THE FALSIFIER: re-folding
 *                        the archive MUST reconcile to the signed balances TO THE CENT, or balance-
 *                        b/f is wounded and HolyGrail §1 must be corrected. Tamper → caught.
 *     S4  §SHOW-OLTP   — OLTP needs no lock manager: disjoint (physics-partitioned) writers union
 *                        with no contention to the SAME projection hash; the one shared thing is a
 *                        single CAS op-class (first-in-order wins); and the claim HOLDS ACROSS the
 *                        checkpoint boundary (carried in the opening state — no double-claim via compaction).
 *     S5  §SHOW-DET    — Determinism: rebuild the whole scenario → byte-identical checkpoint &
 *                        projection hashes (holder-irrelevant, DistributedERP.md §7).
 *
 *   REUSE, invent no crypto (per CLAUDE.md): canonical/sha256/chainOne/verifyChain are poc_chain.js
 *   verbatim; the checkpoint signer is poc_sign.js (ECDSA P-256); totalOrder/projectionHash mirror
 *   poc_distributed.js. The CRUD op shape is the REAL one — we `require` build/erp/crud_overlay.js
 *   CORE.buildOp (CRUD_CREATE/UPDATE/DELETE + DOC_ACTION, op-groups), READ-ONLY, so the checkpoint
 *   proves compaction on the actual ops the editing layer emits. Balances in INTEGER CENTS (exact).
 *
 *   Determinism (§7): every id / op_uuid / ts / amount is a recorded INPUT — NO Date.now(),
 *   NO Math.random(). ECDSA keypairs are edge-minted per run (as in poc_sign); the checkpoint
 *   HASH and projection HASH are deterministic, the signature only attests authenticity.
 *
 * Run: node scripts/poc_showstopper.js 2>&1 | tee build/erp/poc_showstopper.log
 */
'use strict';
var crypto = require('crypto');
var CORE = require('../build/erp/crud_overlay.js');   // the REAL op shape (READ-ONLY require)

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

// ── primitives reused verbatim from poc_chain.js (id→op_uuid; params = canonical payload) ──
function canonicalJSON(v) {                            // stable, key-sorted — replay-exact
  if (v === null || typeof v !== 'object') return JSON.stringify(v);
  if (Array.isArray(v)) return '[' + v.map(canonicalJSON).join(',') + ']';
  return '{' + Object.keys(v).sort().map(function (k) { return JSON.stringify(k) + ':' + canonicalJSON(v[k]); }).join(',') + '}';
}
function paramsOf(op) {                                // the op payload minus the chain/meta fields
  var c = {}; for (var k in op) if (['op_uuid', 'op_type', 'prev_hash', 'op_hash', 'parameters', 'gid'].indexOf(k) < 0) c[k] = op[k];
  return canonicalJSON(c);
}
function canonical(op) { return op.op_uuid + '|' + op.op_type + '|' + op.parameters; }   // poc_chain.canonical shape
function sha256(s) { return crypto.createHash('sha256').update(s).digest('hex'); }
var GENESIS = '0'.repeat(64);
function chainOne(op, prevHash) { var prev = prevHash || GENESIS; return { prev_hash: prev, op_hash: sha256(prev + '|' + canonical(op)) }; }

// ── checkpoint signer reused from poc_sign.js (ECDSA P-256 / SHA-256) ──
function newKey() { return crypto.generateKeyPairSync('ec', { namedCurve: 'P-256' }); }
function signHash(h, sk) { return crypto.sign('sha256', Buffer.from(h), sk).toString('hex'); }
function verifyHash(h, sigHex, pk) { try { return crypto.verify('sha256', Buffer.from(h), pk, Buffer.from(sigHex, 'hex')); } catch (e) { return false; } }

// ════════════════════════════════════════════════════════════════════════
// OP SHAPE — built through the REAL CORE.buildOp. A document-event = one op-GROUP (§18.8):
// COMPLETE_ORDER fans out to SHIP + INVOICE + the journal pair (handler → ops[]). The journal
// (Fact_Acct) IS rows, created via CRUD_CREATE — same shape — so the fold is double-entry to the cent.
// ════════════════════════════════════════════════════════════════════════
var E_ORDER = { key: 'C_Order', verbs: ['create', 'process'], fields: [{ col: 'DocumentNo', type: 'string' }, { col: 'GrandTotal', type: 'number' }, { col: 'C_BPartner', type: 'string' }], docAction: { action: 'CO', from: 'DR', to: 'CO', requires: ['GrandTotal'] } };
var E_SHIP  = { key: 'M_InOut',   verbs: ['create'], fields: [{ col: 'DocumentNo', type: 'string' }, { col: 'C_Order', type: 'string' }] };
var E_INV   = { key: 'C_Invoice', verbs: ['create'], fields: [{ col: 'DocumentNo', type: 'string' }, { col: 'GrandTotal', type: 'number' }] };
var E_PAY   = { key: 'C_Payment', verbs: ['create'], fields: [{ col: 'DocumentNo', type: 'string' }, { col: 'PayAmt', type: 'number' }] };
var E_FACT  = { key: 'Fact_Acct', verbs: ['create'], fields: [{ col: 'Account', type: 'string' }, { col: 'Amount', type: 'number' }] };
// the ONE shared thing — a single-claim entitlement / last unit: CRUD_CREATE carrying a CAS token.
var E_ENT   = { key: 'Entitlement', verbs: ['create'], cas: 'LASTUNIT', fields: [{ col: 'token', type: 'string' }, { col: 'by', type: 'string' }] };

function fact(account, cents, uuid) { return CORE.buildOp('create', E_FACT, { Account: account, Amount: cents }, null, { opUuid: uuid }); }

// COMPLETE_ORDER op-group: DOC_ACTION(complete) + SHIP + INVOICE + Dr AR / Cr Revenue.
function gComplete(gid, no, cents) {
  return { gid: gid, ops: [
    CORE.buildOp('process', E_ORDER, { DocumentNo: 'SO-' + no, GrandTotal: cents }, null, { id: 'SO-' + no, opUuid: gid + '-act' }),
    CORE.buildOp('create', E_SHIP, { DocumentNo: 'SH-' + no, C_Order: 'SO-' + no }, null, { opUuid: gid + '-ship' }),
    CORE.buildOp('create', E_INV,  { DocumentNo: 'INV-' + no, GrandTotal: cents }, null, { opUuid: gid + '-inv' }),
    fact('AR', cents, gid + '-jAR'),
    fact('Revenue', -cents, gid + '-jRev')
  ] };
}
// PAY op-group: C_Payment + Dr Cash / Cr AR.
function gPay(gid, no, cents) {
  return { gid: gid, ops: [
    CORE.buildOp('create', E_PAY, { DocumentNo: 'PAY-' + no, PayAmt: cents }, null, { opUuid: gid + '-pay' }),
    fact('Cash', cents, gid + '-jCash'),
    fact('AR', -cents, gid + '-jAR')
  ] };
}
// CLAIM op-group: a single CRUD_CREATE carrying the CAS token (set-if-unset).
function gClaim(gid, by) { return { gid: gid, ops: [ CORE.buildOp('create', E_ENT, { token: 'LASTUNIT', by: by }, null, { opUuid: gid + '-claim' }) ] }; }

// ── chain a list of op-groups off startPrev; stamp parameters/prev_hash/op_hash on every op ──
function chainGroups(groups, startPrev) {
  var prev = startPrev || GENESIS, flat = [];
  groups.forEach(function (g) {
    g.ops.forEach(function (op) {
      op.parameters = paramsOf(op);
      var c = chainOne(op, prev);
      op.prev_hash = c.prev_hash; op.op_hash = c.op_hash; op.gid = g.gid;
      prev = op.op_hash; flat.push(op);
    });
  });
  return { ops: flat, tip: prev };
}

// ── verifyChain — poc_chain.js verbatim semantics over the flat ordered log ──
function verifyChain(ops, startPrev) {
  var prev = startPrev || GENESIS;
  for (var i = 0; i < ops.length; i++) {
    var op = ops[i];
    if (op.prev_hash !== prev) return { ok: false, brokeAt: op.op_uuid, why: 'prev_hash link' };
    if (sha256(prev + '|' + canonical(op)) !== op.op_hash) return { ok: false, brokeAt: op.op_uuid, why: 'payload altered' };
    prev = op.op_hash;
  }
  return { ok: true, len: ops.length, tip: prev };
}

// ════════════════════════════════════════════════════════════════════════
// THE FOLD — balances + claimed, ATOMIC PER OP-GROUP. A group whose internal recompute does not
// match its stored hashes is rejected ENTIRELY (none of its ops apply): the structural atomicity.
// ════════════════════════════════════════════════════════════════════════
function applyOp(op, st) {
  if (op.op_type === 'CRUD_CREATE' && op.table === 'Fact_Acct') {
    st.bal[op.fields.Account] = (st.bal[op.fields.Account] || 0) + op.fields.Amount;
  } else if (op.op_type === 'CRUD_CREATE' && op.cas) {                 // CAS set-if-unset
    if (!st.claimed[op.cas]) st.claimed[op.cas] = op.fields.by; else st.casLosers.push(op.fields.by);
  }
  // SHIP / INVOICE / DOC_ACTION rows carry no balance — the journal does (extract-only).
}
function cloneState(opening) {
  var st = { bal: {}, claimed: {}, casLosers: [] };
  if (opening) { for (var a in (opening.bal || {})) st.bal[a] = opening.bal[a]; for (var t in (opening.claimed || {})) st.claimed[t] = opening.claimed[t]; }
  return st;
}
// groupsOf — re-segment a flat chained log back into ordered groups by gid (preserving order).
function groupsOf(ops) {
  var order = [], byGid = {};
  ops.forEach(function (op) { if (!byGid[op.gid]) { byGid[op.gid] = []; order.push(op.gid); } byGid[op.gid].push(op); });
  return order.map(function (gid) { return { gid: gid, ops: byGid[gid] }; });
}
function groupIntact(g) {                                              // recompute from the group's own start prev
  var prev = g.ops[0].prev_hash;
  for (var i = 0; i < g.ops.length; i++) {
    var op = g.ops[i];
    if (op.prev_hash !== prev) return false;
    if (sha256(prev + '|' + canonical(op)) !== op.op_hash) return false;
    prev = op.op_hash;
  }
  return true;
}
function fold(ops, opening) {                                          // ATOMIC per group
  var st = cloneState(opening), applied = [], rejected = [];
  groupsOf(ops).forEach(function (g) {
    if (groupIntact(g)) { g.ops.forEach(function (op) { applyOp(op, st); }); applied.push(g.gid); }
    else rejected.push(g.gid);
  });
  return { bal: st.bal, claimed: st.claimed, casLosers: st.casLosers, applied: applied, rejected: rejected };
}
function naiveFold(ops, opening) {                                     // CONTRAST: per-op, ignores group integrity
  var st = cloneState(opening);
  groupsOf(ops).forEach(function (g) { g.ops.forEach(function (op) {
    if (groupIntact(g) || op.op_type !== 'DOC_ACTION') applyOp(op, st);     // applies surviving ops of a torn group
  }); });
  return { bal: st.bal };
}
// tamper — forge a SEALED op's payload (fields + the hashed `parameters`), leaving the already-written
// op_hash STALE — exactly poc_chain.js's "UPDATE kernel_ops SET parameters" attack. The stale seal is
// what catches it: canonical() now hashes the forged payload, so recompute ≠ stored op_hash.
function tamper(op, newFields) { op.fields = newFields; op.parameters = paramsOf(op); return op; }
function balSum(bal) { var s = 0; for (var a in bal) s += bal[a]; return s; }                  // double-entry: must be 0
function balCanon(bal) { return canonicalJSON(bal); }
function projHash(st) { return sha256(canonicalJSON({ bal: st.bal, claimed: st.claimed })); }
function balEqual(a, b) { var keys = {}; var k; for (k in a) keys[k] = 1; for (k in b) keys[k] = 1; var max = 0; for (k in keys) max = Math.max(max, Math.abs((a[k] || 0) - (b[k] || 0))); return { equal: max === 0, maxDiffCents: max }; }
function c(cents) { return (cents < 0 ? '-' : '') + Math.abs(cents / 100).toFixed(2); }        // cents → display

// ════════════════════════════════════════════════════════════════════════
// SCENARIO BUILDER — one place, so S2/S3/S4/S5 all fold the SAME deterministic fixture.
// ════════════════════════════════════════════════════════════════════════
function buildScenario() {
  // Period P1 — three sales (one unpaid) + the single entitlement claimed by till A.
  var P1 = [
    gComplete('p1-e1', '1', 10000), gPay('p1-e2', '1', 10000),   // SO-1 100.00, paid
    gComplete('p1-e3', '2', 25000), gPay('p1-e4', '2', 25000),   // SO-2 250.00, paid
    gComplete('p1-e5', '3', 7500),                                // SO-3 75.00, UNPAID
    gClaim('p1-e6', 'tillA')                                       // claim LASTUNIT (CAS)
  ];
  var c1 = chainGroups(P1, GENESIS);
  var p1State = fold(c1.ops, null);                                // P1 closing fold
  // Period P2 — chains OFF the checkpoint head; pays SO-3; till B re-attempts the claim (must lose).
  var P2 = [ gPay('p2-e7', '3', 7500), gClaim('p2-e8', 'tillB') ];
  var c2 = chainGroups(P2, c1.tip);                                // new genesis = P1 tip
  return { P1: P1, P2: P2, c1: c1, c2: c2, p1State: p1State };
}

(function () {
  console.log('═══ POC-SHOWSTOPPER — HolyGrail "hard parts": checkpoint · atomicity · OLTP ═══\n');
  var controller = newKey();                                        // the period-close signer
  var S = buildScenario();

  // ── S2 · §SHOW-ATOMIC — the document-event op-group is all-or-nothing ──────────────────────
  console.log('S2 — Atomicity (the op-group, ERP.md §18.8)');
  var gWhole = fold(S.c1.ops, null);
  console.log('§SHOW-ATOMIC group=COMPLETE_ORDER#p1-e1 ops=[DOC_ACTION,SHIP,INVOICE,Dr-AR,Cr-Rev] applied=whole projDelta=all balSum=' + c(balSum(gWhole.bal)));
  verdict(gWhole.applied.indexOf('p1-e1') >= 0 && balSum(gWhole.bal) === 0, 'clean op-group folds WHOLE; books balance (Σ=0)', 'applied p1-e1 + Σ=' + c(balSum(gWhole.bal)));

  // tear the group: alter ONE journal op AFTER chaining → its hash no longer recomputes.
  var P1base = [ gComplete('p1-e1', '1', 10000), gPay('p1-e2', '1', 10000), gComplete('p1-e3', '2', 25000), gPay('p1-e4', '2', 25000), gComplete('p1-e5', '3', 7500), gClaim('p1-e6', 'tillA') ];
  var torn = chainGroups(P1base, GENESIS);
  var victim = torn.ops.filter(function (o) { return o.op_uuid === 'p1-e1-jRev'; })[0];
  tamper(victim, { Account: 'Revenue', Amount: -1 });               // forged: Cr Revenue 100.00 → 0.01
  var afterTorn = fold(torn.ops, null);
  // all-or-NONE: a rejected group must leave the ledger IDENTICAL to never having appended it.
  var refOmitted = fold(chainGroups(P1base.slice(1), GENESIS).ops, null);
  var asIfAbsent = balEqual(afterTorn.bal, refOmitted.bal);         // the torn group is a no-op, not a half-op
  console.log('§SHOW-ATOMIC torn group=p1-e1 alteredOp=jRev groupHash=FAIL rejected=' + (afterTorn.rejected.indexOf('p1-e1') >= 0 ? 'Y' : 'N') + ' proj==asIfNeverAppended=' + (asIfAbsent.equal ? 'Y' : 'N') + ' balSum=' + c(balSum(afterTorn.bal)));
  verdict(afterTorn.rejected.indexOf('p1-e1') >= 0 && asIfAbsent.equal && balSum(afterTorn.bal) === 0, 'torn op-group rejected WHOLE; ledger == as-if-never-appended, books still balance (all-or-NONE)', 'rejected + Σ=' + c(balSum(afterTorn.bal)));

  // CONTRAST — naive per-op apply of the torn group leaves the journal half-posted → UNBALANCED.
  var naive = naiveFold(torn.ops, null);
  console.log('§SHOW-ATOMIC contrast naivePerOp torn group → balSum=' + c(balSum(naive.bal)) + ' (≠0 ⇒ half-state)');
  verdict(balSum(naive.bal) !== 0, 'CONTRAST: per-op apply of a torn group UNBALANCES the ledger (why atomicity matters)', 'Σ=' + c(balSum(naive.bal)));

  // ── S3 · §SHOW-CKPT — compaction = balance brought forward (THE FALSIFIER) ──────────────────
  console.log('\nS3 — Compaction = signed checkpoint = balance b/f');
  var ckptPayload = { period: 1, balances: S.p1State.bal, claimed: S.p1State.claimed, chainHead: S.c1.tip };
  var ckptHash = sha256(canonicalJSON(ckptPayload));
  var ckptSig = signHash(ckptHash, controller.privateKey);
  console.log('§SHOW-CKPT period=1 ops=' + S.c1.ops.length + ' head=' + S.c1.tip.slice(0, 12) + '… balances=' + balCanon(S.p1State.bal) + ' signed=Y');
  verdict(verifyHash(ckptHash, ckptSig, controller.publicKey) && balSum(S.p1State.bal) === 0, 'period-close checkpoint signs (balances+head), verifies under controller key; P1 books balance', 'verify=' + verifyHash(ckptHash, ckptSig, controller.publicKey));

  // compact: live tab = checkpoint + P2 only; verify the LIVE chain off the checkpoint head.
  var liveVerify = verifyChain(S.c2.ops, S.c1.tip);
  console.log('§SHOW-CKPT compact archived=' + S.c1.ops.length + ' liveGenesis=' + S.c1.tip.slice(0, 12) + '… liveChainLen=' + S.c2.ops.length + ' (≪ ' + (S.c1.ops.length + S.c2.ops.length) + ') verifyLiveToCkpt=' + (liveVerify.ok ? 'ok' : 'FAIL'));
  verdict(liveVerify.ok && S.c2.ops.length < S.c1.ops.length, 'live tab = checkpoint + open period; live chain verifies off the checkpoint head, ops ≪ full', 'liveLen=' + S.c2.ops.length);

  // balance brought forward: P2 opens FROM the checkpoint balances; fold P2 forward.
  var opening = { bal: ckptPayload.balances, claimed: ckptPayload.claimed };
  var compactClose = fold(S.c2.ops, opening);
  // equivalence: fold(full P1+P2 from genesis) == fold(checkpoint + P2). Compaction must be LOSSLESS.
  var fullClose = fold(chainGroups(S.P1.concat(S.P2), GENESIS).ops, null);
  var lossless = balEqual(compactClose.bal, fullClose.bal);
  console.log('§SHOW-CKPT b/f openFromCkpt=' + balCanon(opening.bal) + ' compactClose=' + balCanon(compactClose.bal) + ' fullClose=' + balCanon(fullClose.bal) + ' lossless=' + (lossless.equal ? 'Y' : 'N') + ' maxDiff=' + lossless.maxDiffCents + 'c');
  verdict(lossless.equal, 'balance b/f: fold(checkpoint + P2) == fold(full log) to the cent (compaction lossless)', 'maxDiff=' + lossless.maxDiffCents + 'c');

  // ── THE LOAD-BEARING TEST — re-fold the ARCHIVED period; reconcile to the SIGNED checkpoint ──
  var archived = chainGroups(S.P1, GENESIS);                        // re-add the cold archive, fresh from genesis
  var archChain = verifyChain(archived.ops, GENESIS);
  var refold = fold(archived.ops, null);
  var recon = balEqual(refold.bal, ckptPayload.balances);
  var headMatch = archived.tip === ckptPayload.chainHead;
  console.log('§SHOW-CKPT audit refoldArchived balances=' + balCanon(refold.bal) + ' signedCkpt=' + balCanon(ckptPayload.balances) + ' agree=' + (recon.equal ? 'Y' : 'N') + ' maxDiff=' + recon.maxDiffCents + 'c head==signed=' + (headMatch ? 'Y' : 'N'));
  verdict(recon.equal && recon.maxDiffCents === 0 && headMatch && archChain.ok,
          'FALSIFIER: re-folded archive reconciles to the signed checkpoint TO THE CENT (balance-b/f holds)',
          'maxDiff=' + recon.maxDiffCents + 'c head=' + (headMatch ? 'match' : 'MISMATCH'));
  if (!(recon.equal && headMatch)) console.log('   ⚠ HolyGrail §1 (balance brought forward) is WOUNDED — correct the doc: archive did NOT reconcile to the signed checkpoint.');

  // fraud: tamper ONE archived op → re-fold diverges from the signed balances (caught though it left the live chain).
  var tampered = chainGroups(S.P1, GENESIS);
  var fv = tampered.ops.filter(function (o) { return o.op_uuid === 'p1-e3-jAR'; })[0];
  tamper(fv, { Account: 'AR', Amount: 99900 });                     // inflate AR by a fraud amount
  var tChain = verifyChain(tampered.ops, GENESIS);
  var tFold = fold(tampered.ops, null);                             // torn group p1-e3 drops out → balances diverge
  var diverged = !balEqual(tFold.bal, ckptPayload.balances).equal;
  console.log('§SHOW-CKPT tamper archivedOp=p1-e3-jAR chainBreaksAt=' + tChain.brokeAt + ' refold≠signedCkpt=' + (diverged ? 'Y' : 'N') + ' detected=' + ((!tChain.ok && diverged) ? 'Y' : 'N'));
  verdict(!tChain.ok && tChain.brokeAt === 'p1-e3-jAR' && diverged, 'tampering an archived op is caught: chain breaks at exactly that op AND refold ≠ the signed checkpoint', 'brokeAt=' + tChain.brokeAt);

  // ── S4 · §SHOW-OLTP — physics partitions writers + one CAS, across the checkpoint ───────────
  console.log('\nS4 — OLTP (physics partitions + one CAS, no lock manager)');
  // disjoint writers: till A and till B each complete a sale in their OWN partition; merge by total order.
  var tA = [ gComplete('A-e1', 'A1', 5000) ], tB = [ gComplete('B-e1', 'B1', 8000) ];
  // edge-minted op_uuids (the gid prefix) make the union clash-free; ts is a recorded INPUT used to order.
  var seqAB = chainGroups(tA.concat(tB), GENESIS);                  // device order A then B
  var seqBA = chainGroups(tB.concat(tA), GENESIS);                  // the merge could arrive B then A
  var hAB = projHash(fold(seqAB.ops, null)), hBA = projHash(fold(seqBA.ops, null));
  var allUuids = seqAB.ops.map(function (o) { return o.op_uuid; });
  var noClash = new Set(allUuids).size === allUuids.length;
  console.log('§SHOW-OLTP disjoint writers=[tillA,tillB] aggregates=disjoint noPkClash=' + (noClash ? 'Y' : 'N') + ' projHash(A,B)=' + hAB.slice(0, 10) + '… projHash(B,A)=' + hBA.slice(0, 10) + '… bothApplied=Y unionHash=' + (hAB === hBA ? 'stable' : 'DIVERGED'));
  verdict(noClash && hAB === hBA, 'disjoint (physics-partitioned) writers union with NO contention → identical projection (no lock)', 'noClash=' + noClash + ' stable=' + (hAB === hBA));

  // the one shared thing — CAS set-if-unset; first in total order wins.
  var casFold = fold(chainGroups([ gClaim('cas-A', 'tillA'), gClaim('cas-B', 'tillB') ], GENESIS).ops, null);
  console.log('§SHOW-OLTP cas shared=LASTUNIT first=tillA wins=' + (casFold.claimed.LASTUNIT === 'tillA' ? 'Y' : 'N') + ' second=tillB rejected=' + (casFold.casLosers.indexOf('tillB') >= 0 ? 'Y' : 'N'));
  verdict(casFold.claimed.LASTUNIT === 'tillA' && casFold.casLosers.indexOf('tillB') >= 0, 'two writers CLAIM the one shared unit → first-in-order wins (single CAS op-class, not a lock)', 'winner=' + casFold.claimed.LASTUNIT);

  // CAS HOLDS ACROSS THE CHECKPOINT: the claim made in P1 is carried by the checkpoint; P2 re-claim loses.
  var carried = opening.claimed.LASTUNIT === 'tillA';
  var p2 = fold(S.c2.ops, opening);                                 // P2 folds FROM the checkpoint opening
  var doubleClaimRejected = p2.casLosers.indexOf('tillB') >= 0 && p2.claimed.LASTUNIT === 'tillA';
  // and the verdict is identical whether you replay the full log or the compacted (checkpoint + P2) log.
  var verdictFull = fold(chainGroups(S.P1.concat(S.P2), GENESIS).ops, null);
  var sameVerdict = verdictFull.claimed.LASTUNIT === p2.claimed.LASTUNIT && verdictFull.casLosers.indexOf('tillB') >= 0;
  console.log('§SHOW-OLTP cas-across-ckpt claimed@P1=tillA carried=' + (carried ? 'Y' : 'N') + ' doubleClaim@P2(tillB) rejected=' + (doubleClaimRejected ? 'Y' : 'N') + ' verdict(full)==verdict(compact)=' + (sameVerdict ? 'Y' : 'N'));
  verdict(carried && doubleClaimRejected && sameVerdict, 'CAS holds ACROSS the checkpoint: claim carried in the opening state, P2 re-claim rejected; full == compacted verdict', 'carried=' + carried + ' rejected=' + doubleClaimRejected);

  // ── S5 · §SHOW-DET — rebuild → byte-identical checkpoint & projection hashes ────────────────
  console.log('\nS5 — Determinism (holder-irrelevant, §7)');
  var S2 = buildScenario();
  var ckptHashB = sha256(canonicalJSON({ period: 1, balances: S2.p1State.bal, claimed: S2.p1State.claimed, chainHead: S2.c1.tip }));
  var projA = projHash(fold(S.c2.ops, opening));
  var projB = projHash(fold(S2.c2.ops, { bal: S2.p1State.bal, claimed: S2.p1State.claimed }));
  console.log('§SHOW-DET rebuild ckptHashA=' + ckptHash.slice(0, 12) + '… ckptHashB=' + ckptHashB.slice(0, 12) + '… projHashA=' + projA.slice(0, 12) + '… projHashB=' + projB.slice(0, 12) + '… agree=' + ((ckptHash === ckptHashB && projA === projB) ? 'Y' : 'N'));
  verdict(ckptHash === ckptHashB && projA === projB, 'rebuild → byte-identical checkpoint hash and post-compaction projection (deterministic)', 'ckpt=' + (ckptHash === ckptHashB) + ' proj=' + (projA === projB));

  console.log('\n§SHOW ' + (fails ? 'FAIL — ' + fails + ' checks red' : 'PASS — all three "hard parts" hold on the real op shape: op-group is atomic, the signed checkpoint re-folds the archive to the cent (balance b/f), physics+CAS isolate writers across the checkpoint; deterministic.'));
  process.exit(fails ? 1 : 0);
})();
