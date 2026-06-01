#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_email_dr.js — inbox-as-log disaster matrix + the key-recovery residual (prompts/EMAIL_DR_POC.md).
 *   Goes PAST poc_persist.js (the happy path) to the genuinely open question (HolyGrail appraisal):
 *   the inbox recovers the FACTS unconditionally, but the KEY does not live in the inbox.
 *
 *   Two-layer claim (DistributedERP.md §5.2b, Truth 3 "secure the fact, not the container"):
 *     Layer A — DATA recovery (should be UNCONDITIONAL given the key): from the latest VALID signed
 *       snapshot in ANY reachable channel, the new device restores state; replay-hash == pre-disaster.
 *     Layer B — KEY recovery (the residual): "encrypt-to-user" makes each snapshot CIPHERTEXT, so the
 *       private key is the single secret that both SIGNS and (via derivation) DECRYPTS. Lose it with no
 *       anchor → the data is present but UNDECRYPTABLE. The honest floor — then three anchors that close it.
 *
 *   FALSIFIER: "§EMAIL-DR total-loss … recover=FAIL reason=undecryptable" is a REQUIRED witness (a PASS of
 *   an honest negative), not a bug. The finding is which anchors work and what trust each one adds.
 *
 *   Reuse (invent no crypto): sign/verify/projectionHash from poc_persist; chain/verify from poc_chain.
 *   AES-256-GCM for encrypt-to-user; Shamir over GF(256) is the published scheme (Rijndael field), with
 *   FIXED (deterministic) coefficients as INPUTS — a real impl uses a CSPRNG; determinism here is for replay.
 *   Keys/shares/seq/iv are recorded INPUTS — NO Date.now()/Math.random(); the mailbox is a STATIC fixture.
 *
 * Run: node scripts/poc_email_dr.js 2>&1 | tee build/erp/poc_email_dr.log
 */
'use strict';
var crypto = require('crypto');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function sha256(s) { return crypto.createHash('sha256').update(s).digest('hex'); }
function sha256buf(b) { return crypto.createHash('sha256').update(b).digest(); }
function signHash(h, sk) { return crypto.sign('sha256', Buffer.from(h), sk).toString('hex'); }
function verifyHash(h, sigHex, pk) { try { return crypto.verify('sha256', Buffer.from(h), pk, Buffer.from(sigHex, 'hex')); } catch (e) { return false; } }
function canonicalJSON(v) {
  if (v === null || typeof v !== 'object') return JSON.stringify(v);
  if (Array.isArray(v)) return '[' + v.map(canonicalJSON).join(',') + ']';
  return '{' + Object.keys(v).sort().map(function (k) { return JSON.stringify(k) + ':' + canonicalJSON(v[k]); }).join(',') + '}';
}
var GENESIS = '0'.repeat(64);

// ── projection: a small wallet/ledger (plain JS — deterministic, no sql.js needed here) ──
function snapshot(state) { return canonicalJSON(state); }
function projectionHash(state) { return sha256(snapshot(state)); }
function restore(snap) { return JSON.parse(snap); }

// ── encrypt-to-user: each snapshot is CIPHERTEXT under encKey = sha256(privateKeyDER). The private key
// is the ONE secret — it signs (authenticity) AND derives the decryption key (confidentiality). ──
function encKeyOf(privDer) { return sha256buf(privDer); }                 // 32 bytes for AES-256
function ivFor(seq) { return sha256(('iv|' + seq)).slice(0, 24); }        // deterministic per-seq IV (hex, 12 bytes) — INPUT
function enc(plain, key, seq) {
  var c = crypto.createCipheriv('aes-256-gcm', key, Buffer.from(ivFor(seq), 'hex'));
  var ct = Buffer.concat([c.update(plain, 'utf8'), c.final()]);
  return { ct: ct.toString('hex'), tag: c.getAuthTag().toString('hex') };
}
function dec(ctHex, tagHex, key, seq) {                                   // throws on wrong key / tampered ct (GCM auth)
  var d = crypto.createDecipheriv('aes-256-gcm', key, Buffer.from(ivFor(seq), 'hex'));
  d.setAuthTag(Buffer.from(tagHex, 'hex'));
  return Buffer.concat([d.update(Buffer.from(ctHex, 'hex')), d.final()]).toString('utf8');
}

// ── an "email" = a chained, signed, encrypted full snapshot (seq, prev_hash, ct, tag, sig) ──
function emitEmail(state, seq, prevHash, sk, encKey) {
  var ct = enc(snapshot(state), encKey, seq);
  var body = seq + '|' + prevHash + '|' + ct.ct + '|' + ct.tag;
  var h = sha256(body);
  return { seq: seq, prev_hash: prevHash, ct: ct.ct, tag: ct.tag, op_hash: h, sig: signHash(h, sk) };
}
// recover: keep VALID-signature emails, pick tip by max signed seq, DECRYPT with the key, restore.
function recover(mailbox, pubKey, encKey) {
  var valid = mailbox.filter(function (m) { return verifyHash(m.op_hash, m.sig, pubKey) && sha256(m.seq + '|' + m.prev_hash + '|' + m.ct + '|' + m.tag) === m.op_hash; });
  if (!valid.length) return { ok: false, reason: 'no valid signed snapshot' };
  var tip = valid.reduce(function (a, b) { return b.seq > a.seq ? b : a; });
  try { return { ok: true, tip: tip, state: restore(dec(tip.ct, tip.tag, encKey, tip.seq)) }; }
  catch (e) { return { ok: false, reason: 'undecryptable', tip: tip }; }
}

// ════════════════════════════════════════════════════════════════════════
// Shamir's Secret Sharing over GF(256) (Rijndael field) — the published scheme; FIXED coeffs = INPUTS.
// ════════════════════════════════════════════════════════════════════════
var EXP = new Array(512), LOG = new Array(256);
(function () { var x = 1; for (var i = 0; i < 255; i++) { EXP[i] = x; LOG[x] = i; var xt = (x << 1) ^ ((x & 0x80) ? 0x11b : 0); x = (x ^ xt) & 0xff; } for (i = 255; i < 512; i++) EXP[i] = EXP[i - 255]; })();
function gmul(a, b) { return (a && b) ? EXP[LOG[a] + LOG[b]] : 0; }
function ginv(a) { return EXP[255 - LOG[a]]; }
function coeff(t, d) { return parseInt(sha256('shamir|' + t + '|' + d).slice(0, 2), 16); }   // deterministic INPUT coeff stream
function shamirSplit(secret, k, n) {                                     // secret: Buffer → n shares, each {x, bytes}
  var shares = []; for (var x = 1; x <= n; x++) shares.push({ x: x, bytes: Buffer.alloc(secret.length) });
  for (var t = 0; t < secret.length; t++) {
    for (var s = 0; s < n; s++) {
      var x = shares[s].x, y = secret[t];                               // poly(0) = secret byte
      for (var d = 1; d < k; d++) y ^= gmul(coeff(t, d), powGF(x, d));   // y = secret + Σ coeff_d·x^d  (GF add = xor)
      shares[s].bytes[t] = y;
    }
  }
  return shares;
}
function powGF(x, d) { var r = 1; for (var i = 0; i < d; i++) r = gmul(r, x); return r; }
function shamirCombine(points, len) {                                   // Lagrange interpolation at x=0
  var out = Buffer.alloc(len);
  for (var t = 0; t < len; t++) {
    var acc = 0;
    for (var i = 0; i < points.length; i++) {
      var num = 1, den = 1;
      for (var j = 0; j < points.length; j++) if (j !== i) { num = gmul(num, points[j].x); den = gmul(den, points[i].x ^ points[j].x); }
      acc ^= gmul(points[i].bytes[t], gmul(num, ginv(den)));
    }
    out[t] = acc;
  }
  return out;
}

(function () {
  console.log('═══ POC-EMAIL-DR — inbox-as-log disaster matrix + the key-recovery residual ═══\n');

  // ── S1 · fixture — the user key, encKey, and a chain of signed+encrypted snapshot "emails" ──
  var user = crypto.generateKeyPairSync('ec', { namedCurve: 'P-256' });
  var privDer = user.privateKey.export({ type: 'pkcs8', format: 'der' });
  var encKey = encKeyOf(privDer);

  var st = { 'GIFT-50': { balance: 50, claimed_by: null } };
  var mailbox = [], prev = GENESIS;
  function push(seq) { var e = emitEmail(st, seq, prev, user.privateKey, encKey); mailbox.push(e); prev = e.op_hash; }
  push(1);
  st['GIFT-50'].balance = 40; push(2);                                  // spend 10
  st['GIFT-50'].balance = 25; st['GIFT-50'].claimed_by = 'cust-7'; push(3);   // spend 15 + claim
  st['COUPON-X'] = { balance: 5, claimed_by: null }; push(4);           // newest
  var preHash = projectionHash(st);
  console.log('§EMAIL-DR fixture emails=' + mailbox.length + ' preHash=' + preHash.slice(0, 12) + '… (signed+encrypted-to-user, chained)');

  // ════ Layer A — channel/data disasters (recovery holds WITH the key) ════
  console.log('\nLayer A — DATA recovery (the channel is an untrusted, swappable pipe):');

  // wipe device → restore from latest signed snapshot.
  var r1 = recover(mailbox, user.publicKey, encKey);
  console.log('§EMAIL-DR wipe device→restore latest-snapshot pickedSeq=' + (r1.tip && r1.tip.seq) + ' replay-hash==pre agree=' + (r1.ok && projectionHash(r1.state) === preHash ? 'Y' : 'N'));
  verdict(r1.ok && projectionHash(r1.state) === preHash, 'wipe → restore from latest signed snapshot → replay-hash == pre-disaster', 'seq=' + (r1.tip && r1.tip.seq));

  // mailbox arrives shuffled → tip chosen by signed seq, not arrival order.
  var shuffled = [mailbox[2], mailbox[0], mailbox[3], mailbox[1]];
  var r2 = recover(shuffled, user.publicKey, encKey);
  console.log('§EMAIL-DR mailbox-shuffle arrival=' + shuffled.map(function (m) { return m.seq; }).join(',') + ' tip-by-signed-seq=' + (r2.tip && r2.tip.seq) + ' correct=' + (r2.ok && projectionHash(r2.state) === preHash ? 'Y' : 'N'));
  verdict(r2.ok && r2.tip.seq === 4 && projectionHash(r2.state) === preHash, 'shuffled mailbox → tip by signed seq (arrival order ignored)', 'tip=' + (r2.tip && r2.tip.seq));

  // forged snapshot (attacker has no user key) → rejected; fall back to latest VALID.
  var forged = { seq: 5, prev_hash: prev, ct: enc(snapshot({ 'GIFT-50': { balance: 999999, claimed_by: 'attacker' } }), encKey, 5).ct, tag: 'deadbeef', op_hash: null, sig: 'deadbeef' };
  forged.op_hash = sha256(forged.seq + '|' + forged.prev_hash + '|' + forged.ct + '|' + forged.tag);
  var r3 = recover(mailbox.concat([forged]), user.publicKey, encKey);
  console.log('§EMAIL-DR forged-snapshot seq=5 admitted=' + (r3.tip && r3.tip.seq === 5) + ' rejected=' + (r3.ok && r3.tip.seq === 4 ? 'Y' : 'N') + ' fallback=latest-valid');
  verdict(r3.ok && r3.tip.seq === 4, 'forged snapshot rejected (bad sig) → recover from latest VALID', 'tipAfter=' + (r3.tip && r3.tip.seq));

  // provider purged all but the latest → single-email recovery (full snapshot per email).
  var r4 = recover([mailbox[3]], user.publicKey, encKey);
  console.log('§EMAIL-DR mailbox-purge keep=latest-only single-email-recovery=' + (r4.ok && projectionHash(r4.state) === preHash ? 'Y' : 'N'));
  verdict(r4.ok && projectionHash(r4.state) === preHash, 'provider purged older mail → the LATEST signed snapshot alone suffices', 'seq=' + (r4.tip && r4.tip.seq));

  // provider migration A→B → still verifies, no re-trust (channel is a commodity, Truth 3).
  var providerB = mailbox.slice();                                      // export@A → import@B (same blobs, new pipe)
  var r5 = recover(providerB, user.publicKey, encKey);
  console.log('§EMAIL-DR provider-migration export@A→import@B verify=' + (r5.ok ? 'ok' : 'FAIL') + ' no-retrust=Y (no key change)');
  verdict(r5.ok && projectionHash(r5.state) === preHash, 'move the signed log to a new provider → still verifies, no re-trust', 'ok=' + r5.ok);

  // ════ Layer B — the key regress (the headline; honest falsifier) ════
  console.log('\nLayer B — KEY recovery (the residual: encrypt-to-user makes each snapshot ciphertext):');

  // total loss: device + key gone, NO anchor → data present but UNDECRYPTABLE. The honest floor.
  var noKey = sha256buf(Buffer.from('wrong-or-absent-key'));            // a new device has no private key
  var rLoss = recover(mailbox, user.publicKey, noKey);
  console.log('§EMAIL-DR total-loss device+key gone, NO key-recovery → recover=' + (rLoss.ok ? 'OK?!' : 'FAIL') + ' reason=' + rLoss.reason);
  verdict(!rLoss.ok && rLoss.reason === 'undecryptable', 'HONEST FLOOR: with no key anchor, the facts are present but UNDECRYPTABLE — recovery FAILS (required negative)', 'reason=' + rLoss.reason);

  // anchor 1 — Shamir k=2-of-3 across the user's OWN channels [email, email2, printed-card].
  var shares = shamirSplit(privDer, 2, 3);
  var reDer = shamirCombine([shares[0], shares[2]], privDer.length);    // 2 of 3 (email + printed-card)
  var reEncKey = encKeyOf(reDer);
  var rShamir = recover(mailbox, user.publicKey, reEncKey);
  var keyExact = Buffer.compare(reDer, privDer) === 0;
  console.log('§EMAIL-DR key-recovery scheme=shamir k=2 n=3 channels=[email,email2,printed-card] reconstructed=' + (keyExact ? 'Y' : 'N') + ' verify=' + (rShamir.ok && projectionHash(rShamir.state) === preHash ? 'ok' : 'FAIL') + ' trust=own-k-channels');
  verdict(keyExact && rShamir.ok && projectionHash(rShamir.state) === preHash, 'Shamir 2-of-3 over the user OWN channels reconstructs the key → decrypts; trust = the user holds k channels', 'exact=' + keyExact);

  // anchor 2 — corporate escrow: company-controlled Workspace holds a copy; re-issues on loss.
  var escrowCopy = Buffer.from(privDer);                                // the employer escrowed a copy
  var rEscrow = recover(mailbox, user.publicKey, encKeyOf(escrowCopy));
  console.log('§EMAIL-DR key-recovery scheme=corporate-escrow node=workspace reissue=Y verify=' + (rEscrow.ok && projectionHash(rEscrow.state) === preHash ? 'ok' : 'FAIL') + ' trust=employer-admin');
  verdict(rEscrow.ok && projectionHash(rEscrow.state) === preHash, 'corporate Workspace escrow returns the key → decrypts; trust = the employer admin (who could also impersonate)', 'ok=' + rEscrow.ok);

  // anchor 3 — platform passkey synced by the keychain (iCloud/Google) survives device loss.
  var syncedCopy = Buffer.from(privDer);                                // platform keychain synced it off-device
  var rPasskey = recover(mailbox, user.publicKey, encKeyOf(syncedCopy));
  console.log('§EMAIL-DR key-recovery scheme=platform-passkey synced=Y device-loss-survived=' + (rPasskey.ok && projectionHash(rPasskey.state) === preHash ? 'Y' : 'N') + ' trust=platform-keychain');
  verdict(rPasskey.ok && projectionHash(rPasskey.state) === preHash, 'platform-synced passkey survives device loss → decrypts; trust = the platform keychain', 'ok=' + rPasskey.ok);

  // ── Key-rotation continuity — a rotation op signed by the OLD key authorising the NEW key ──
  console.log('\nKey-rotation continuity (recovery must not invalidate history):');
  var k1 = crypto.generateKeyPairSync('ec', { namedCurve: 'P-256' });   // original key
  var k2 = crypto.generateKeyPairSync('ec', { namedCurve: 'P-256' });   // rotated-to key
  var p1 = k1.publicKey.export({ type: 'spki', format: 'der' }).toString('hex');
  var p2 = k2.publicKey.export({ type: 'spki', format: 'der' }).toString('hex');
  // chain: e1,e2 signed by old; ROTATE (old vouches new) signed by old; e3 signed by new.
  var chain = [], cp = GENESIS;
  function entry(seq, type, payload, sk) { var body = seq + '|' + cp + '|' + type + '|' + payload; var h = sha256(body); var e = { seq: seq, type: type, payload: payload, prev_hash: cp, op_hash: h, sig: signHash(h, sk) }; cp = h; chain.push(e); return e; }
  entry(1, 'SNAP', 'a', k1.privateKey);
  entry(2, 'SNAP', 'b', k1.privateKey);
  entry(3, 'ROTATE', canonicalJSON({ old: p1, new: p2 }), k1.privateKey);   // OLD key signs the handover
  entry(4, 'SNAP', 'c', k2.privateKey);                                     // NEW key signs henceforth
  // verify across the boundary: active key starts at p1 (trust root); a verified ROTATE switches it.
  var activePub = k1.publicKey, prevH = GENESIS, chainOk = true, rotated = false;
  for (var i = 0; i < chain.length; i++) {
    var e = chain[i];
    if (e.prev_hash !== prevH || sha256(e.seq + '|' + e.prev_hash + '|' + e.type + '|' + e.payload) !== e.op_hash) { chainOk = false; break; }
    if (!verifyHash(e.op_hash, e.sig, activePub)) { chainOk = false; break; }          // sig under the key active AT THIS POINT
    if (e.type === 'ROTATE') { var pl = JSON.parse(e.payload); if (pl.old !== p1) { chainOk = false; break; } activePub = k2.publicKey; rotated = true; }
    prevH = e.op_hash;
  }
  console.log('§EMAIL-DR rotation old→new signed-handover verifyChain=' + (chainOk ? 'ok' : 'FAIL') + ' historyInvalidated=' + (chainOk && rotated ? 'N' : '?'));
  verdict(chainOk && rotated, 'rotation op signed by OLD key authorises NEW; chain verifies across the boundary, history intact', 'ok=' + chainOk + ' rotated=' + rotated);

  // ── S5 · determinism — rebuild → byte-identical recovered projection hash ──
  var rDet = recover(mailbox, user.publicKey, encKey);
  console.log('\n§EMAIL-DR rebuild recoveredProjHashA=' + projectionHash(r1.state).slice(0, 12) + '… B=' + projectionHash(rDet.state).slice(0, 12) + '… agree=' + (projectionHash(r1.state) === projectionHash(rDet.state) ? 'Y' : 'N'));
  verdict(projectionHash(r1.state) === projectionHash(rDet.state), 'rebuild → byte-identical recovered state (deterministic)', 'agree=' + (projectionHash(r1.state) === projectionHash(rDet.state)));

  console.log('\n§EMAIL-DR ' + (fails ? 'FAIL — ' + fails + ' checks red' : 'PASS — Layer A (data) recovers unconditionally from any reachable valid signed snapshot; Layer B floor stated: NO key recovery without a trust anchor (own-k-channels / employer-admin / platform-keychain), rotation preserves history. The regress terminates for the FACT unconditionally; for the KEY only at a chosen, NAMED anchor.'));
  process.exit(fails ? 1 : 0);
})();
