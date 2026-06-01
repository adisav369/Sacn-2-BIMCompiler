#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_sign.js — W-SIGN acceptance (prompts/DISTRIBUTED_POC.md #4; DistributedERP.md §9-B, §5.2).
 *   Proves the step from tamper-EVIDENT (W-CHAIN) to un-FORGEABLE:
 *     - an op signed by key A verifies under A, FAILS under key B;
 *     - "present but not forge": a holder (no issuer private key) who edits a payload and
 *       perfectly re-chains + re-signs with their OWN key is still rejected under the issuer key,
 *       detected at exactly the first forged op.
 *
 *   Layering: the W-CHAIN op_hash = SHA-256(prev_hash | canonical(op)) stays DETERMINISTIC
 *   (integrity + order). The signature attests OVER that op_hash (authenticity) — it is NOT in
 *   the hash, so the chain is still byte-identical across devices while the sig is per-issuer.
 *   Keys are edge-minted INPUTS (each device/merchant mints its keypair once; §7).
 *   ECDSA P-256 / SHA-256 — maps directly to the browser's `crypto.subtle` (kernel_ops.js port).
 *
 * Run: node scripts/poc_sign.js 2>&1 | tee build/erp/poc_sign.log
 */
'use strict';
var crypto = require('crypto');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

function canonical(op) { return op.id + '|' + op.op_type + '|' + op.parameters; }
function sha256(s) { return crypto.createHash('sha256').update(s).digest('hex'); }
var GENESIS = '0'.repeat(64);

function newKey() { return crypto.generateKeyPairSync('ec', { namedCurve: 'P-256' }); }
function signHash(opHash, sk) { return crypto.sign('sha256', Buffer.from(opHash), sk).toString('hex'); }
function verifyHash(opHash, sigHex, pk) {
  try { return crypto.verify('sha256', Buffer.from(opHash), pk, Buffer.from(sigHex, 'hex')); }
  catch (e) { return false; }
}

// build a fully chained + signed log from raw ops, signed by sk.
function buildSignedLog(rawOps, sk, startPrev) {
  var prev = startPrev || GENESIS, out = [];
  rawOps.forEach(function (op) {
    var h = sha256(prev + '|' + canonical(op));
    out.push({ id: op.id, op_type: op.op_type, parameters: op.parameters, prev_hash: prev, op_hash: h, sig: signHash(h, sk) });
    prev = h;
  });
  return out;
}

// verify: recompute the chain AND check each signature against the issuer's public key.
function verifySigned(ops, issuerPub) {
  var prev = GENESIS;
  for (var i = 0; i < ops.length; i++) {
    var op = ops[i], h = sha256(prev + '|' + canonical(op));
    if (op.prev_hash !== prev) return { ok: false, brokeAt: op.id, why: 'chain link' };
    if (h !== op.op_hash) return { ok: false, brokeAt: op.id, why: 'payload altered' };
    if (!verifyHash(op.op_hash, op.sig, issuerPub)) return { ok: false, brokeAt: op.id, why: 'signature' };
    prev = h;
  }
  return { ok: true, len: ops.length };
}

(function () {
  console.log('═══ POC-SIGN — W-SIGN: present-but-not-forge ═══\n');

  var issuer = newKey();   // the merchant / device that mints the signed entitlement
  var other  = newKey();   // an unrelated key (wrong verifier)
  var holder = newKey();   // the customer who HOLDS the token but lacks the issuer's private key

  var RAW = [
    { id: 1, op_type: 'ISSUE',  parameters: '{"token":"GIFT-50","limit":50,"to":"cust-7"}' },
    { id: 2, op_type: 'SIGN',   parameters: '{"valid_until":"2026-12-31"}' },
    { id: 3, op_type: 'CREDIT', parameters: '{"balance":50}' },
    { id: 4, op_type: 'SPEND',  parameters: '{"amount":10,"balance":40}' },
    { id: 5, op_type: 'SPEND',  parameters: '{"amount":15,"balance":25}' }
  ];

  // CASE 1 — issuer-signed log verifies under the issuer's public key.
  var signed = buildSignedLog(RAW, issuer.privateKey);
  var v1 = verifySigned(signed, issuer.publicKey);
  console.log('§SIGN issuer-signed len=' + signed.length + ' verify ok=' + v1.ok);
  verdict(v1.ok && v1.len === RAW.length, 'op signed by issuer → verifies under issuer key', 'ok=' + v1.ok);

  // CASE 2 — SAME log verified under the WRONG key fails (at the first op).
  var v2 = verifySigned(signed, other.publicKey);
  console.log('§SIGN wrong-key verify ok=' + v2.ok + ' brokeAt=' + v2.brokeAt + ' why=' + v2.why);
  verdict(!v2.ok && v2.why === 'signature', 'op signed by A FAILS under key B', 'brokeAt=' + v2.brokeAt + ' why=' + v2.why);

  // CASE 3 — FORGE attempt: holder keeps the genuine issuer-signed op1,op2 (which they were given),
  //   edits op3's balance 50→5000, then perfectly re-chains + re-signs op3..5 with their OWN key.
  //   Chain is internally consistent — so the failure isolates to the SIGNATURE, not the hash.
  var rawTail = [
    { id: 3, op_type: 'CREDIT', parameters: '{"balance":5000}' },   // <- forged balance
    RAW[3], RAW[4]
  ];
  var forgedTail = buildSignedLog(rawTail, holder.privateKey, signed[1].op_hash);
  var forged = signed.slice(0, 2).concat(forgedTail);   // genuine op1,op2 + holder-signed op3..5
  var v3 = verifySigned(forged, issuer.publicKey);
  console.log('§SIGN forge(present-but-not-forge) ok=' + v3.ok + ' brokeAt=' + v3.brokeAt + ' why=' + v3.why);
  verdict(!v3.ok && v3.brokeAt === 3 && v3.why === 'signature',
          'holder can PRESENT but not FORGE: edited+re-signed op rejected at exactly op 3',
          'brokeAt=' + v3.brokeAt + ' why=' + v3.why);

  // CASE 4 — determinism of the CHAIN is unaffected by signing (op_hash excludes the sig).
  var signedAgain = buildSignedLog(RAW, issuer.privateKey);
  var hashesEqual = signed.every(function (op, i) { return op.op_hash === signedAgain[i].op_hash; });
  var sigsDiffer  = signed.some(function (op, i) { return op.sig !== signedAgain[i].sig; });
  console.log('§SIGN layering chain-stable=' + hashesEqual + ' sig-nondeterministic=' + sigsDiffer);
  verdict(hashesEqual, 'W-CHAIN hash stays deterministic even though signatures vary (clean layering)', 'hashes-equal=' + hashesEqual);

  console.log('\n§SIGN ' + (fails ? 'FAIL — ' + fails + ' checks red' : 'PASS — signature gates authenticity over the deterministic chain: wrong key fails, holder cannot forge, chain stays stable'));
  process.exit(fails ? 1 : 0);
})();
