#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// Implementing SERVERLESS_HARDENING_RESUME.md §H-6 — Witness: W-ROTATE
/**
 * poc_rotate.js — W-ROTATE: key lifecycle (rotation / revoke / offboarding) WITHOUT re-signing history.
 *
 *   Issue (DistributedERP §9-B): the whole trust model is "the user holds the keys," yet rotation /
 *   compromise / offboarding is only NAMED as "the one irreducible anchor" — never witnessed. Everything
 *   else (W-ERASE §H-4, W-EQUIVOCATION §H-5) rests on this.
 *
 *   Model: the kernel hash chain (build/erp/kernel_ops.js prev_hash/op_hash) is the key-INDEPENDENT
 *   integrity+order layer. Authenticity rides a key-epoch schedule DERIVED from the signed control ops in
 *   the log (build/erp/erp_key_epochs.js): a ROTATE op counter-signed by the OUTGOING key installs a new
 *   key at seq S; ops verify under the key valid AT THEIR seq → the past is never re-signed. A REVOKE op
 *   rejects a key's FUTURE ops while its PAST ops stay valid. Signatures are ECDSA P-256 over the op_hash
 *   via the shipped build/erp/erp_snapshot_sign.js. Deterministic: recorded ts; op params carry only stable
 *   kid labels (key material is out-of-band, like erp_snapshot_sign's pinned key) → op_hash byte-stable
 *   across runs; ECDSA sigs vary by nonce but are verified by validity, never hashed or byte-compared.
 *
 *     §ROTATE-OP    — a ROTATE counter-signed by the OUTGOING key installs the new key; one NOT counter-
 *                     signed by the outgoing key is REJECTED.
 *     §HISTORY-VALID— ops before S still verify under the OLD key after rotation (past untouched).
 *     §FUTURE-GATED — an op after S signed by the OLD key REJECTS; signed by the NEW key ACCEPTS.
 *     §REVOKE       — REVOKE(key) rejects that key's FUTURE ops (compromise/offboarding); its PAST ops
 *                     stay valid (it really did author them).
 *     §FALSIFIER    — a single fixed key accepts a forged post-compromise op (forge-forever); the SAME
 *                     forgery under the epoch model is rejected → epochs are load-bearing.
 *
 * Run: node scripts/poc_rotate.js 2>&1 | tee build/erp/poc_rotate.log
 */
'use strict';
if (typeof global.crypto === 'undefined') { global.crypto = require('crypto').webcrypto; }
global.window = { APP: {} }; global.APP = global.window.APP;
global.indexedDB = { open: function () { var r = {}; setTimeout(function () { r.result = { createObjectStore: function () {}, transaction: function () { return { objectStore: function () { return { put: function () {} }; } }; } }; if (r.onsuccess) r.onsuccess(); }, 0); return r; } };

var path = require('path');
var initSqlJs = require('sql.js');
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var K = global.window.KernelOps;
var SIGN = require(path.join(__dirname, '..', 'build', 'erp', 'erp_snapshot_sign.js'));   // ECDSA P-256 sign/verify
var EP = require(path.join(__dirname, '..', 'build', 'erp', 'erp_key_epochs.js'));        // epoch verification

var BASE_TS = 1700000000000, fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
var _real = console.log; function mute() { console.log = function () {}; } function unmute() { console.log = _real; }

// ── key registry (kid -> {privJwk, pubJwk}); params carry only the kid, key material stays out-of-band ──
var KEYS = {};
async function genKey(kid) {
  var kp = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']);
  KEYS[kid] = { privJwk: await crypto.subtle.exportKey('jwk', kp.privateKey),
                pubJwk:  await crypto.subtle.exportKey('jwk', kp.publicKey) };
}
function pubByKid() { var m = {}; Object.keys(KEYS).forEach(function (k) { m[k] = KEYS[k].pubJwk; }); return m; }
var verifyFn = function (msgHex, sigHex, pubJwk) { return SIGN.verifyTip(msgHex, sigHex, pubJwk); };

// ── chain builders ──────────────────────────────────────────────────────────────────────────────────
function newDb(SQL) { return new SQL.Database(); }
function add(db, opType, params, ts) { return K.commitOp(db, opType, params, null, null, null, ts); }
function normal(db, kid, ts)               { return add(db, 'POST',   { signed_by: kid }, ts); }
function rotate(db, outgoing, newKid, ts)  { return add(db, 'ROTATE', { kind: 'ROTATE', newKid: newKid, signed_by: outgoing }, ts); }
function revoke(db, auth, revokedKid, newKid, ts) { return add(db, 'REVOKE', { kind: 'REVOKE', revokedKid: revokedKid, newKid: newKid, signed_by: auth }, ts); }

// seal the hash chain (no kernel signer → sig stays null), then sign each op_hash with the key its params name.
async function sealAndSign(db) {
  await K.sealChain(db);                                  // key-independent integrity+order (sig left null)
  var r = db.exec('SELECT id, parameters, op_hash FROM kernel_ops ORDER BY id');
  var rows = r.length ? r[0].values : [];
  for (var i = 0; i < rows.length; i++) {
    var p = JSON.parse(rows[i][1]); var kid = p.signed_by;
    var sig = await SIGN.signTip(KEYS[kid].privJwk, rows[i][2]);   // ECDSA over the op_hash hex
    db.run('UPDATE kernel_ops SET sig=? WHERE id=?', [sig, rows[i][0]]);
  }
}
function tipOf(db) { var r = db.exec('SELECT op_hash FROM kernel_ops ORDER BY id DESC LIMIT 1'); return r.length ? r[0].values[0][0] : null; }

(async function () {
  console.log('═══ POC-ROTATE — key rotation / revoke / offboarding WITHOUT re-signing history (real kernel + signer) ═══\n');
  var SQL = await initSqlJs();
  await genKey('K0'); await genKey('K1'); await genKey('K2');
  var GEN = 'K0';

  // ── §ROTATE-OP ──────────────────────────────────────────────────────────────────────────────────
  console.log('§ROTATE-OP — ROTATE counter-signed by the OUTGOING key installs the new key; otherwise rejected');
  // valid: K0 ops, ROTATE(K0->K1) counter-signed by K0, K1 op.
  mute();
  var dbA = newDb(SQL);
  normal(dbA, 'K0', BASE_TS + 1); normal(dbA, 'K0', BASE_TS + 2);
  rotate(dbA, 'K0', 'K1', BASE_TS + 3);
  normal(dbA, 'K1', BASE_TS + 4);
  await sealAndSign(dbA);
  var integ = await K.verifyChain(dbA);                  // hash chain only (no signer set)
  var vA = await EP.verifyEpochSigs(dbA, { pubByKid: pubByKid(), genesisKid: GEN, verify: verifyFn });
  // not-counter-signed: same chain but ROTATE signed_by a stranger (K1, not the outgoing K0).
  var dbBad = newDb(SQL);
  normal(dbBad, 'K0', BASE_TS + 1);
  rotate(dbBad, 'K1', 'K1', BASE_TS + 2);               // signed_by K1 — NOT the outgoing key
  normal(dbBad, 'K1', BASE_TS + 3);
  await sealAndSign(dbBad);
  var vBad = await EP.verifyEpochSigs(dbBad, { pubByKid: pubByKid(), genesisKid: GEN, verify: verifyFn });
  unmute();
  verdict(integ.ok, 'hash chain integrity intact (key-independent layer)', 'tip=' + (integ.tip || '').slice(0, 12) + '…');
  verdict(vA.ok && vA.activeKid === 'K1', 'valid ROTATE accepted; active key advanced K0→K1', 'finalActive=' + vA.activeKid);
  verdict(!vBad.ok, 'ROTATE not counter-signed by the outgoing key is REJECTED', 'brokeAt=' + vBad.brokeAt + ' why=' + vBad.why);

  // ── §HISTORY-VALID ──────────────────────────────────────────────────────────────────────────────
  console.log('\n§HISTORY-VALID — ops before the rotation still verify under the OLD key (past not re-signed)');
  // ops id1,id2 in dbA were signed by K0; after rotating to K1 they STILL verify under K0 directly.
  var pre = dbA.exec('SELECT id, op_hash, sig FROM kernel_ops WHERE id IN (1,2) ORDER BY id');
  var pr = pre[0].values;
  var h1 = await SIGN.verifyTip(pr[0][1], pr[0][2], KEYS.K0.pubJwk);
  var h2 = await SIGN.verifyTip(pr[1][1], pr[1][2], KEYS.K0.pubJwk);
  var underNew = await SIGN.verifyTip(pr[0][1], pr[0][2], KEYS.K1.pubJwk);  // sanity: NOT verifiable under the new key
  verdict(h1 && h2 && vA.ok, 'pre-rotation K0 ops verify under K0 after the rotation (history untouched)', 'op1=' + h1 + ' op2=' + h2);
  verdict(!underNew, 'those same ops do NOT verify under the new key K1 (the past is genuinely old-key-anchored)', 'underK1=' + underNew);

  // ── §FUTURE-GATED ───────────────────────────────────────────────────────────────────────────────
  console.log('\n§FUTURE-GATED — after S, the OLD key is rejected; the NEW key is accepted');
  mute();
  var dbOld = newDb(SQL);   // post-rotation op signed by the OLD key K0
  normal(dbOld, 'K0', BASE_TS + 1); rotate(dbOld, 'K0', 'K1', BASE_TS + 2); normal(dbOld, 'K0', BASE_TS + 3);
  await sealAndSign(dbOld);
  var vOld = await EP.verifyEpochSigs(dbOld, { pubByKid: pubByKid(), genesisKid: GEN, verify: verifyFn });
  var dbNew = newDb(SQL);   // identical but the post-rotation op is signed by the NEW key K1
  normal(dbNew, 'K0', BASE_TS + 1); rotate(dbNew, 'K0', 'K1', BASE_TS + 2); normal(dbNew, 'K1', BASE_TS + 3);
  await sealAndSign(dbNew);
  var vNew = await EP.verifyEpochSigs(dbNew, { pubByKid: pubByKid(), genesisKid: GEN, verify: verifyFn });
  unmute();
  verdict(!vOld.ok && vOld.brokeAt === 3, 'a post-rotation op signed by the OLD key K0 is REJECTED', 'brokeAt=' + vOld.brokeAt + ' why=' + vOld.why);
  verdict(vNew.ok, 'the same op signed by the NEW key K1 is ACCEPTED', 'ok=' + vNew.ok + ' active=' + vNew.activeKid);

  // ── §REVOKE ─────────────────────────────────────────────────────────────────────────────────────
  console.log('\n§REVOKE — REVOKE(key) rejects its FUTURE ops; its PAST ops stay valid');
  mute();
  // chain: K0, ROTATE(K0->K1), K1 op, REVOKE(K1, install K2), K2 op.
  var dbR = newDb(SQL);
  normal(dbR, 'K0', BASE_TS + 1);
  rotate(dbR, 'K0', 'K1', BASE_TS + 2);
  normal(dbR, 'K1', BASE_TS + 3);                        // id3: a genuine K1 op BEFORE revocation
  revoke(dbR, 'K1', 'K1', 'K2', BASE_TS + 4);           // id4: K1 offboards itself, installs K2
  normal(dbR, 'K2', BASE_TS + 5);
  await sealAndSign(dbR);
  var vR = await EP.verifyEpochSigs(dbR, { pubByKid: pubByKid(), genesisKid: GEN, verify: verifyFn });
  // a FORGED future op by the revoked K1 (attacker reuses the compromised key after offboarding).
  var dbForge = newDb(SQL);
  normal(dbForge, 'K0', BASE_TS + 1); rotate(dbForge, 'K0', 'K1', BASE_TS + 2); normal(dbForge, 'K1', BASE_TS + 3);
  revoke(dbForge, 'K1', 'K1', 'K2', BASE_TS + 4); normal(dbForge, 'K2', BASE_TS + 5);
  normal(dbForge, 'K1', BASE_TS + 6);                   // id6: forged — K1 used AFTER its revocation
  await sealAndSign(dbForge);
  var vForge = await EP.verifyEpochSigs(dbForge, { pubByKid: pubByKid(), genesisKid: GEN, verify: verifyFn });
  // the past K1 op (id3) still verifies directly under K1.
  var p3 = dbR.exec('SELECT op_hash, sig FROM kernel_ops WHERE id=3')[0].values[0];
  var past = await SIGN.verifyTip(p3[0], p3[1], KEYS.K1.pubJwk);
  unmute();
  verdict(vR.ok && vR.activeKid === 'K2', 'graceful offboard chain verifies; active advanced to the successor K2', 'active=' + vR.activeKid);
  verdict(past, "the revoked key's PAST op (id3) still verifies (it really did author it)", 'pastK1=' + past);
  verdict(!vForge.ok && vForge.brokeAt === 6, "a FUTURE op by the revoked key K1 is REJECTED", 'brokeAt=' + vForge.brokeAt + ' why=' + vForge.why);

  // ── §FALSIFIER ──────────────────────────────────────────────────────────────────────────────────
  console.log('\n§FALSIFIER — a single fixed key forges forever; epochs reject the same forgery → epochs load-bearing');
  mute();
  // single-key world: K0 only, attacker appends a forged op signed by the stolen K0.
  var dbSingle = newDb(SQL);
  normal(dbSingle, 'K0', BASE_TS + 1); normal(dbSingle, 'K0', BASE_TS + 2);
  normal(dbSingle, 'K0', BASE_TS + 3);                  // forged post-compromise op — still K0
  await sealAndSign(dbSingle);
  var vSingle = await EP.singleKeyVerify(dbSingle, { pub: KEYS.K0.pubJwk, verify: verifyFn });
  // epoch world: rotate away from K0 BEFORE the compromise; the same forged-by-K0 op is now superseded.
  var dbEpoch = newDb(SQL);
  normal(dbEpoch, 'K0', BASE_TS + 1); rotate(dbEpoch, 'K0', 'K1', BASE_TS + 2);
  normal(dbEpoch, 'K0', BASE_TS + 3);                   // forged: attacker only has the superseded K0
  await sealAndSign(dbEpoch);
  var vEpoch = await EP.verifyEpochSigs(dbEpoch, { pubByKid: pubByKid(), genesisKid: GEN, verify: verifyFn });
  unmute();
  verdict(vSingle.ok, 'single fixed key: the forged post-compromise op is ACCEPTED (forge-forever — the bug)', 'singleKeyVerify.ok=' + vSingle.ok);
  verdict(!vEpoch.ok && vEpoch.brokeAt === 3, 'epoch model: the SAME forgery is REJECTED (rotation supersedes the stolen key)', 'brokeAt=' + vEpoch.brokeAt + ' why=' + vEpoch.why);

  // ── determinism: same params → byte-identical op_hash tip across a rebuild (sigs vary, never hashed) ──
  console.log('\n§DETERMINISM — same recorded inputs → byte-identical hash tip (sigs vary by nonce, never hashed)');
  mute();
  var d1 = newDb(SQL); normal(d1, 'K0', BASE_TS + 1); rotate(d1, 'K0', 'K1', BASE_TS + 2); normal(d1, 'K1', BASE_TS + 3); await sealAndSign(d1);
  var d2 = newDb(SQL); normal(d2, 'K0', BASE_TS + 1); rotate(d2, 'K0', 'K1', BASE_TS + 2); normal(d2, 'K1', BASE_TS + 3); await sealAndSign(d2);
  unmute();
  verdict(tipOf(d1) === tipOf(d2), 'two rebuilds produce the identical chain tip', tipOf(d1).slice(0, 12) + '… == ' + tipOf(d2).slice(0, 12) + '…');

  console.log('\n   ▸ headline: rotate (counter-signed) and revoke a key WITHOUT re-signing history — past verifies under its');
  console.log('     era key, future is gated to the active key, revoked keys lose the future but keep the past.');
  console.log('\n═══ ' + (fails ? '🔴 ' + fails + ' FAILED' : '🟢 ALL PASS') + ' ═══');
  process.exit(fails ? 1 : 0);
})();
