#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// Implementing SERVERLESS_HARDENING_RESUME.md §H-5 — Witness: W-EQUIVOCATION
/**
 * poc_equivocation.js — W-EQUIVOCATION: detect a Byzantine/equivocating relay that hands DIFFERENT
 *   orderings of the same ops to different clients (split-brain CAUSED by the "dumb" sequencer we told
 *   everyone to trust). Answers DistributedERP §6/§9-E, where the facilitator is assumed honest-but-dumb
 *   (order-preserving by design) — we proved it cannot FORGE effects (W-SIGN), never that it cannot
 *   EQUIVOCATE. This is the row a distributed-systems skeptic attacks hardest.
 *
 *   BOUND TO CORE (not a toy): build/erp/kernel_ops.js (sealChain/verifyChain over a per-client log ->
 *   a chain tip computed in id order) + build/erp/erp_snapshot_sign.js (signTip/verifyTip, ECDSA P-256;
 *   EACH CLIENT signs the tip it OBSERVED with its OWN per-client keypair). Two clients hold their own
 *   db/log so they can carry different orderings of the SAME two ops over one seq window.
 *
 *   MODEL: two ops a,b over one seq window. An equivocating relay hands client A order [a,b] and client
 *   B order [b,a]. To make the divergence ATTRIBUTABLE TO ORDER (not an id/uuid artifact -- kernel
 *   _canonical hashes id+timestamp+type+params), each client records, per op, the SAME op_uuid + SAME
 *   payload but a `seq` field = the position the relay told THAT client. The two clients' logs therefore
 *   carry the identical pair of ops, differing ONLY in observed order -> seal to different tips. Each
 *   client signs its observed tip with its own key; gossip surfaces the mismatch.
 *
 *     §TWO-VIEWS     -- relay hands A [a,b] and B [b,a] over one seq window (both per-client logs shown).
 *     §DIVERGENT-TIP -- A and B seal DIFFERENT chain tips for the same {a,b} over the same seq range, and
 *                      the divergence is attributable to ORDER (same ids/uuids/payloads; only seq differs).
 *     §DETECT        -- gossiping signed tips flags the mismatch. Honest relay (both see [a,b]) -> identical
 *                      tips -> NO flag; equivocating relay -> mismatch -> flag.
 *     §ATTRIBUTABLE  -- each signed tip verifies under THAT client's pinned key (verifyTip true) -> it proves
 *                      what that client saw; you cannot forge client A's signature onto a tip A never saw,
 *                      so the split pins on the relay.
 *     §FALSIFIER     -- WITHOUT tip-gossip the divergence is SILENT: each client's own chain verifies fine
 *                      in isolation (verifyChain.ok true for both) -> both think they're canonical -> gossip
 *                      is the load-bearing mechanism that surfaces the split-brain.
 *
 *   DETERMINISTIC: recorded ts (BASE_TS, no Date.now on any hash path), fixed op_uuids, no Math.random in
 *   any seal/fold path. Tips are byte-identical across runs (signatures vary by ECDSA nonce -- never hashed,
 *   never compared for equality; only verifyTip is asserted on them).
 *
 * Run: node scripts/poc_equivocation.js 2>&1 | tee build/erp/poc_equivocation.log
 */
'use strict';
if (typeof global.crypto === 'undefined') { global.crypto = require('crypto').webcrypto; }
global.window = { APP: {} }; global.APP = global.window.APP;
global.indexedDB = { open: function () { var r = {}; setTimeout(function () { r.result = { createObjectStore: function () {}, transaction: function () { return { objectStore: function () { return { put: function () {} }; } }; } }; if (r.onsuccess) r.onsuccess(); }, 0); return r; } };

var path = require('path');
var initSqlJs = require('sql.js');
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var K = global.window.KernelOps;
var SIGN = require(path.join(__dirname, '..', 'build', 'erp', 'erp_snapshot_sign.js'));

var BASE_TS = 1700000000000;                 // recorded ts -- INPUT, no Date.now on any hash/fold path
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
var _real = console.log; function mute() { console.log = function () {}; } function unmute() { console.log = _real; }

// The two ops over ONE seq window -- same payload, same edge-minted op_uuid for each; the ONLY thing the
// relay varies between clients is the ORDER (captured per-client as the `seq` field it was told).
var OP_A = { uuid: 'op-a-uuid', type: 'POST', payload: { table: 'C_Order', id: 'A', actor: 'A' } };
var OP_B = { uuid: 'op-b-uuid', type: 'POST', payload: { table: 'C_Order', id: 'B', actor: 'B' } };

// A client commits the ops in the order the relay HANDED it, recording seq = observed position, then seals.
// Returns { tip, log:[{uuid,seq}] }. Bound to the real kernel: commitOp + sealChain.
async function clientObserve(SQL, orderedOps) {
  var db = new SQL.Database(); K.ensureTable(db);
  mute();
  for (var i = 0; i < orderedOps.length; i++) {
    var o = orderedOps[i];
    // seq is part of params -> part of _canonical -> ORDER is what drives the tip, not the row id alone.
    var params = { table: o.payload.table, id: o.payload.id, actor: o.payload.actor, seq: i };
    K.commitOp(db, o.type, params, null, null, o.uuid, BASE_TS + i);
  }
  var seal = await K.sealChain(db);
  unmute();
  var log = orderedOps.map(function (o, i) { return { uuid: o.uuid, seq: i }; });
  return { db: db, tip: seal.tip, log: log };
}

async function genKeyPair() {
  var kp = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']);
  var pubJwk  = await crypto.subtle.exportKey('jwk', kp.publicKey);
  return { priv: kp.privateKey, pubJwk: pubJwk };
}

(async function () {
  console.log('═══ POC-EQUIVOCATION — detect+attribute a Byzantine relay split, on the real kernel + signer ═══\n');
  var SQL = await initSqlJs();

  // each client holds its OWN per-client ECDSA keypair -- it signs ONLY what it saw (custody at the edge).
  var keyA = await genKeyPair();
  var keyB = await genKeyPair();

  // -- §TWO-VIEWS -- the equivocating relay hands A [a,b] and B [b,a] over ONE seq window --
  console.log('§TWO-VIEWS — equivocating relay: A sees [a,b], B sees [b,a] (same two ops, one seq window)');
  var A = await clientObserve(SQL, [OP_A, OP_B]);   // A's view: a then b
  var B = await clientObserve(SQL, [OP_B, OP_A]);   // B's view: b then a
  var viewA = A.log.map(function (l) { return l.uuid.slice(3, 4) + '@' + l.seq; }).join(',');
  var viewB = B.log.map(function (l) { return l.uuid.slice(3, 4) + '@' + l.seq; }).join(',');
  var sameUuids = A.log.map(function (l) { return l.uuid; }).sort().join('|') ===
                  B.log.map(function (l) { return l.uuid; }).sort().join('|');
  console.log('   ▸ A.log = [' + viewA + ']   B.log = [' + viewB + ']');
  verdict(sameUuids && viewA !== viewB, 'both clients hold the SAME ops (uuids match) in DIFFERENT order (the equivocation)', 'A=[' + viewA + '] B=[' + viewB + ']');

  // -- §DIVERGENT-TIP -- A and B seal DIFFERENT tips for the same {a,b}, attributable to ORDER --
  console.log('\n§DIVERGENT-TIP — same ops, different observed order → different sealed chain tips');
  console.log('   ▸ A.tip = ' + A.tip.slice(0, 16) + '…   B.tip = ' + B.tip.slice(0, 16) + '…');
  // attribution control: re-seal a client that sees the SAME order as A -> identical tip (so the divergence
  // is the ORDER, not any per-client randomness -- there is none on the hash path).
  var Actrl = await clientObserve(SQL, [OP_A, OP_B]);
  verdict(A.tip !== B.tip, 'A.tip ≠ B.tip for the identical op-set (the split-brain the relay caused)', 'differ=' + (A.tip !== B.tip));
  verdict(A.tip === Actrl.tip, 'control: a client seeing the SAME order [a,b] seals the IDENTICAL tip → divergence is ORDER, not randomness', 'A==Actrl=' + (A.tip === Actrl.tip));

  // -- §DETECT -- gossiping signed tips flags the mismatch; an honest relay does NOT --
  console.log('\n§DETECT — exchange signed tips: equivocating relay → mismatch flagged; honest relay → no flag');
  var sigA = await SIGN.signTip(keyA.priv, A.tip);
  var sigB = await SIGN.signTip(keyB.priv, B.tip);
  // gossip = each client publishes {signed tip, observed seq-range}. The peer flags if tips differ over the
  // same range. (seq-range identical: both cover positions 0..1 of the one window.)
  function gossipFlag(tipX, tipY) { return tipX !== tipY; }   // mismatch over the same seq-range = equivocation
  var equivFlag = gossipFlag(A.tip, B.tip);
  verdict(equivFlag, 'equivocating relay: A.tip vs B.tip mismatch over the same seq-range → equivocation FLAGGED', 'flag=' + equivFlag);
  // honest relay: both clients see [a,b] -> identical tips -> no flag (the negative control).
  var Ah = await clientObserve(SQL, [OP_A, OP_B]);
  var Bh = await clientObserve(SQL, [OP_A, OP_B]);
  verdict(!gossipFlag(Ah.tip, Bh.tip), 'honest relay: both see [a,b] → identical tips → NO flag (no false positive)', 'A.tip==B.tip=' + (Ah.tip === Bh.tip));

  // -- §ATTRIBUTABLE -- each signed tip verifies under THAT client's key -> unforgeable, pins on the relay --
  console.log('\n§ATTRIBUTABLE — each signed tip verifies under that client\'s OWN key (proves what THAT client saw)');
  var verA = await SIGN.verifyTip(A.tip, sigA, keyA.pubJwk);
  var verB = await SIGN.verifyTip(B.tip, sigB, keyB.pubJwk);
  verdict(verA && verB, 'A\'s sig verifies on A.tip under A\'s key; B\'s sig verifies on B.tip under B\'s key → both attestations authentic', 'verA=' + verA + ' verB=' + verB);
  // forgery attempt: try to pin A's signature onto B's tip (the relay claiming "A actually saw [b,a]").
  var forge1 = await SIGN.verifyTip(B.tip, sigA, keyA.pubJwk);   // A's sig does NOT cover B's tip
  // and try B's tip+sig under A's key (claiming B's view is A's) -- wrong key.
  var forge2 = await SIGN.verifyTip(B.tip, sigB, keyA.pubJwk);   // B's tip+sig under A's pinned key
  verdict(!forge1 && !forge2, 'cannot forge A\'s signature onto the tip A never saw → the equivocation pins on the RELAY, not a client', 'forgeAontoB=' + forge1 + ' wrongKey=' + forge2);

  // -- §FALSIFIER -- WITHOUT gossip the split is SILENT: each chain verifies fine in isolation --
  console.log('\n§FALSIFIER — without tip-gossip the divergence is SILENT (each chain verifies in isolation)');
  mute();
  var vA = await K.verifyChain(A.db);
  var vB = await K.verifyChain(B.db);
  unmute();
  verdict(vA.ok && vB.ok, 'BOTH clients\' own chains verify OK in isolation → each believes it is canonical (the silent split)', 'A.ok=' + vA.ok + ' B.ok=' + vB.ok);
  verdict(vA.tip !== vB.tip && equivFlag, 'only GOSSIP surfaces the split (tips differ yet both verify) → tip-gossip is load-bearing, not cosmetic', 'isolatedDetect=none gossipDetect=' + equivFlag);

  console.log('\n   ▸ summary: an equivocating relay hands disjoint orderings → divergent SIGNED tips. Each client');
  console.log('     signs only what it saw (own key), so the mismatch is detected on gossip AND attributable to');
  console.log('     the relay, unforgeable. Honest relay → identical tips → no flag. Without gossip the split is');
  console.log('     silent (both chains verify alone) → gossip is the load-bearing detector. Residual: gossip must');
  console.log('     actually happen (a heartbeat cadence) — the live-divergence detector is §H-11 (tier-2).');
  console.log('\n═══ ' + (fails ? '🔴 ' + fails + ' FAILED' : '🟢 ALL PASS') + ' ═══');
  process.exit(fails ? 1 : 0);
})().catch(function (e) { unmute(); console.error('FATAL', e); process.exit(1); });
