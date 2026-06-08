#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_persist.js — W-PERSIST + email-recovery acceptance
 *   (prompts/DISTRIBUTED_POC.md #5; DistributedERP.md §9-A, §5.2b).
 *   Proves the "secure the fact, not the container" / eviction answer:
 *     1. export → wipe → import round-trips: `replay-hash == pre-export hash` (the local copy
 *        is disposable).
 *     2. email-recovery: signed full-snapshot emails restore state even when (a) the mailbox is
 *        shuffled (we pick the chain tip by signed `seq`, NOT arrival order) and (b) the phone is
 *        wiped; a SINGLE latest signed snapshot suffices (single-email recovery robustness, §5.2b).
 *     3. a forged snapshot (bad signature) is rejected; recovery falls back to the latest VALID one.
 *
 *   Keys edge-minted INPUTS (the user mints once; §7). ECDSA P-256 / SHA-256 → browser crypto.subtle.
 *   Runs on sql.js (browser binding); uses real db.export()/import for the round-trip.
 *
 * Run: node scripts/poc_persist.js 2>&1 | tee build/erp/poc_persist.log
 */
'use strict';
var crypto = require('crypto');
var initSqlJs = require('sql.js');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function sha256(s) { return crypto.createHash('sha256').update(s).digest('hex'); }
function signHash(h, sk) { return crypto.sign('sha256', Buffer.from(h), sk).toString('hex'); }
function verifyHash(h, sigHex, pk) { try { return crypto.verify('sha256', Buffer.from(h), pk, Buffer.from(sigHex, 'hex')); } catch (e) { return false; } }

var PROJ = 'CREATE TABLE wallet (token TEXT PRIMARY KEY, balance REAL, claimed_by TEXT)';
function projectionHash(db) {
  var r = db.exec('SELECT token,balance,claimed_by FROM wallet ORDER BY token');
  return sha256(JSON.stringify(r.length ? r[0].values : []));
}
function snapshot(db) {  // the durable, signable state payload
  var r = db.exec('SELECT token,balance,claimed_by FROM wallet ORDER BY token');
  return JSON.stringify(r.length ? r[0].values : []);
}
function restoreFromSnapshot(SQL, snapJson) {
  var db = new SQL.Database(); db.run(PROJ);
  JSON.parse(snapJson).forEach(function (row) {
    db.run('INSERT INTO wallet (token,balance,claimed_by) VALUES (?,?,?)', [row[0], row[1], row[2]]);
  });
  return db;
}

(async function () {
  var SQL = await initSqlJs();
  console.log('═══ POC-PERSIST — W-PERSIST + inbox-as-recoverable-signed-log ═══\n');

  var user = crypto.generateKeyPairSync('ec', { namedCurve: 'P-256' });

  // build wallet state through a few spends (the live local DB)
  var live = new SQL.Database(); live.run(PROJ);
  live.run("INSERT INTO wallet VALUES ('GIFT-50', 50, NULL)");
  live.run("UPDATE wallet SET balance = 40 WHERE token='GIFT-50'");   // spend 10
  live.run("UPDATE wallet SET balance = 25, claimed_by='cust-7' WHERE token='GIFT-50'"); // spend 15 + claim
  var preHash = projectionHash(live);
  console.log('§PERSIST live balance=25 preHash=' + preHash.slice(0, 12) + '…');

  // CASE 1 — export → wipe → import → hash equal.
  var bytes = live.export();                 // serialise (what goes to IndexedDB / a file)
  live.close();                              // "evicted" — local copy gone
  var restored = new SQL.Database(bytes);    // re-import
  var postHash = projectionHash(restored);
  console.log('§PERSIST export→wipe→import size=' + (bytes.length / 1024).toFixed(1) + 'KB postHash=' + postHash.slice(0, 12) + '…');
  verdict(preHash === postHash, 'export→wipe→import round-trips → replay-hash == pre-export hash', 'pre==post:' + (preHash === postHash));

  // CASE 2 — email-recovery. Each use emits a signed FULL snapshot to the user's inbox.
  var mailbox = [];
  function emitEmail(db, seq) {
    var snap = snapshot(db), h = sha256(seq + '|' + snap);
    mailbox.push({ seq: seq, snap: snap, hash: h, sig: signHash(h, user.privateKey) });
  }
  emitEmail(restored, 1);
  restored.run("UPDATE wallet SET balance = 20 WHERE token='GIFT-50'"); emitEmail(restored, 2);  // later spend
  restored.run("INSERT INTO wallet VALUES ('COUPON-X', 5, NULL)");       emitEmail(restored, 3);  // newest
  var tipHash = projectionHash(restored);
  restored.close();   // phone lost entirely

  // mailbox arrives OUT OF ORDER (prove we don't trust arrival order)
  var shuffled = [mailbox[2], mailbox[0], mailbox[1]];
  console.log('§PERSIST mailbox arrival-order=' + shuffled.map(function (m) { return m.seq; }).join(',') + ' (shuffled)');

  // recovery: keep only VALID signatures, pick the chain tip by max signed seq.
  var valid = shuffled.filter(function (m) { return verifyHash(m.hash, m.sig, user.publicKey) && sha256(m.seq + '|' + m.snap) === m.hash; });
  var tip = valid.reduce(function (a, b) { return b.seq > a.seq ? b : a; });
  var recovered = restoreFromSnapshot(SQL, tip.snap);
  var recHash = projectionHash(recovered);
  console.log('§PERSIST recover pickedSeq=' + tip.seq + ' recHash=' + recHash.slice(0, 12) + '… tipHash=' + tipHash.slice(0, 12) + '…');
  verdict(recHash === tipHash, 'recover from latest signed email (by seq, not arrival) → state restored', 'rec==tip:' + (recHash === tipHash));
  verdict(tip.seq === 3, 'single latest signed snapshot suffices for recovery (§5.2b robustness)', 'pickedSeq=' + tip.seq);

  // CASE 3 — a forged snapshot (tampered balance, attacker has no user key) is rejected.
  var forged = { seq: 4, snap: JSON.stringify([['GIFT-50', 999999, 'attacker']]), hash: null, sig: 'deadbeef' };
  forged.hash = sha256(forged.seq + '|' + forged.snap);
  var mailbox2 = shuffled.concat([forged]);
  var valid2 = mailbox2.filter(function (m) { return verifyHash(m.hash, m.sig, user.publicKey) && sha256(m.seq + '|' + m.snap) === m.hash; });
  var tip2 = valid2.reduce(function (a, b) { return b.seq > a.seq ? b : a; });
  console.log('§PERSIST forged seq=4 admitted=' + (valid2.indexOf(forged) >= 0) + ' tipAfter=' + tip2.seq);
  verdict(valid2.indexOf(forged) < 0 && tip2.seq === 3, 'forged email rejected → recovery falls back to latest VALID snapshot', 'tipAfter=' + tip2.seq);

  console.log('\n§PERSIST ' + (fails ? 'FAIL — ' + fails + ' checks red' : 'PASS — local copy disposable (export round-trips); inbox is the recoverable signed log (seq-not-arrival, single-snapshot, forgery-rejecting)'));
  process.exit(fails ? 1 : 0);
})();
