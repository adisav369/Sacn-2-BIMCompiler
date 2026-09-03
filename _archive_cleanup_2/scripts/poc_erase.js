#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_erase.js — W-ERASE: right-to-erasure on an immutable, sign-everything op-log (crypto-shredding).
 *   Implementing SERVERLESS_HARDENING_RESUME.md §H-4 — Witness: W-ERASE.
 *
 *   THE ISSUE THIS SETTLES: GDPR/CCPA "delete this person's PII" vs. an append-only + hash-chained +
 *   signed log — you cannot unlink an op without breaking the chain (§7 records inputs; W-CHAIN/W-SIGN
 *   hash + sign them). The sharpest contradiction in the model; today ABSENT in docs/DistributedERP.md §9.
 *
 *   THE MODEL: PII (customer name/email) rides as a PER-DATA-SUBJECT AES-GCM ciphertext envelope INSIDE
 *   op.parameters (the ciphertext is the recorded input, §7); non-PII (account/cents) stays in CLEAR and
 *   folds normally through the SHIPPED erp_period_close.foldBalances. The op + prev_hash/op_hash +
 *   signature are over ciphertext+metadata. Erasure = destroy the subject key (crypto-shred). Then:
 *   the ciphertext is unchanged -> the chain still verifies and the tip is IDENTICAL; the plaintext is
 *   irrecoverable; the ledger is untouched (it folds account/cents, never PII).
 *
 *   BOUND TO CORE (not a toy): build/erp/kernel_ops.js (commitOp/sealChain/verifyChain/replayOps, real
 *   W-SIGN signer installed) + build/erp/erp_period_close.js foldBalances (the shipped double-entry fold).
 *   The ONLY new primitive is build/erp/erp_pii_vault.js (subject-keyed AES-GCM via webcrypto).
 *   Deterministic: recorded ts (an INPUT, no Date.now on any fold/replay/hash path); integer cents.
 *
 *     §ENVELOPE        — PII = per-subject ciphertext in op.parameters; non-PII (account/cents) clear ->
 *                        folds normally. Prove: the committed op carries an UNREADABLE ciphertext (no
 *                        plaintext substring in the stored params) AND foldBalances still books correctly.
 *     §ERASE           — drop the subject key -> vault.open() THROWS (plaintext irrecoverable); yet
 *                        verifyChain.ok is STILL true and the chain tip is IDENTICAL before vs after
 *                        erasure (the hash is over the ciphertext, which is unchanged).
 *     §BOOKS-INTACT    — foldBalances is byte/cent-identical before vs after erasure (maxDiffCents === 0).
 *     §AUDIT-RESIDUAL  — the op row + its hash REMAIN (the event provably happened) but the subject is
 *                        unidentifiable -> "tombstone the identity, keep the accounting fact".
 *     §FALSIFIER       — store the SAME PII in CLEARTEXT instead: "erasure" can only be done by
 *                        deleting/rewriting the op, which BREAKS the chain (verifyChain.ok=false or the
 *                        tip changes) -> crypto-shredding is load-bearing; only the envelope model lets you
 *                        erase PII while keeping a verifying chain.
 *
 * Run: node scripts/poc_erase.js 2>&1 | tee build/erp/poc_erase.log
 */
'use strict';
if (typeof global.crypto === 'undefined') { global.crypto = require('crypto').webcrypto; }
global.window = { APP: {} }; global.APP = global.window.APP;
global.indexedDB = { open: function () { var r = {}; setTimeout(function () { r.result = { createObjectStore: function () {}, transaction: function () { return { objectStore: function () { return { put: function () {} }; } }; } }; if (r.onsuccess) r.onsuccess(); }, 0); return r; } };

var path = require('path');
var initSqlJs = require('sql.js');
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var K = global.window.KernelOps;
var PC = require(path.join(__dirname, '..', 'build', 'erp', 'erp_period_close.js'));   // shipped foldBalances
var Vault = require(path.join(__dirname, '..', 'build', 'erp', 'erp_pii_vault.js'));   // the new primitive

var AR = 1300, SALES = 4000, AMT = 250, BASE_TS = 1700000000000;   // AMT in dollars; foldBalances stores cents
var C = 100;
var SUBJECT = 'CUST-7';
var PII = { name: 'Jane Q. Public', email: 'jane.public@example.com', subject_id: SUBJECT };
var fails = 0, ts = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
var _real = console.log; function mute() { console.log = function () {}; } function unmute() { console.log = _real; }
function readRows(db) { var r = db.exec('SELECT id,op_type,parameters,prev_hash,op_hash,sig FROM kernel_ops ORDER BY id'); return (r.length ? r[0].values : []).map(function (x) { return { id: x[0], op_type: x[1], parameters: x[2], prev_hash: x[3], op_hash: x[4], sig: x[5] }; }); }
function paramsStr(db) { var r = db.exec("SELECT parameters FROM kernel_ops WHERE op_type='POST' ORDER BY id"); return (r.length ? r[0].values : []).map(function (x) { return x[0]; }).join(' '); }

// a real W-SIGN signer (HMAC over op_hash via webcrypto) — the chain is SIGNED, not just hashed, so the
// erasure guarantee is tested under the full "sign everything" regime, not a hash-only weakening.
function makeSigner() {
  var keyP = global.crypto.subtle.importKey('raw', new TextEncoder().encode('controller-edge-key-0001'),
    { name: 'HMAC', hash: 'SHA-256' }, false, ['sign', 'verify']);
  function hex(buf) { return Array.from(new Uint8Array(buf)).map(function (b) { return b.toString(16).padStart(2, '0'); }).join(''); }
  function unhex(h) { var u = new Uint8Array(h.length / 2); for (var i = 0; i < u.length; i++) u[i] = parseInt(h.substr(i * 2, 2), 16); return u; }
  return {
    sign: async function (h) { var k = await keyP; return hex(await global.crypto.subtle.sign('HMAC', k, new TextEncoder().encode(h))); },
    verify: async function (h, sig) { if (!sig) return false; var k = await keyP; return await global.crypto.subtle.verify('HMAC', k, unhex(sig), new TextEncoder().encode(h)); }
  };
}

(async function () {
  console.log('═══ POC-ERASE — right-to-erasure on an immutable signed log (crypto-shredding), on the real kernel ═══\n');
  var SQL = await initSqlJs();
  K.setSigner(makeSigner());   // W-SIGN ON — every op is hashed AND signed

  // ── build the envelope: PII -> per-subject AES-GCM ciphertext; non-PII (account/cents) stays clear ──
  var vault = Vault.createVault();
  var subjectKey = await Vault.createSubjectKey();
  vault.put(SUBJECT, subjectKey);
  var envelope = await Vault.seal(subjectKey, PII);     // {ct, iv} — the recorded op input (§7)

  // ── §ENVELOPE — commit a POST whose params carry the ciphertext envelope + the CLEAR account/cents ──
  console.log('§ENVELOPE — PII rides as per-subject ciphertext in the op; account/cents stay clear and fold');
  mute();
  var db = new SQL.Database(); K.ensureTable(db);
  // a normal sale invoice: DR AR / CR Sales (the accounting fact, in clear) + the PII envelope (encrypted)
  K.commitOp(db, 'POST', {
    table: 'C_Invoice', id: 'INV-1', subject_id: SUBJECT,
    lines: [{ account_id: AR, amtacctdr: AMT, amtacctcr: 0 }, { account_id: SALES, amtacctdr: 0, amtacctcr: AMT }],
    pii_envelope: envelope                                // <-- ciphertext, the only place PII lives
  }, null, null, null, BASE_TS + (ts++));
  await K.sealChain(db);
  unmute();

  var stored = paramsStr(db);
  var ciphertextUnreadable = stored.indexOf(PII.name) === -1 && stored.indexOf(PII.email) === -1 && stored.indexOf('jane') === -1;
  var booksBefore = PC.foldBalances(K.replayOps(db)).bal;
  var booksRight = (booksBefore[AR] === AMT * C) && (booksBefore[SALES] === -AMT * C);
  verdict(ciphertextUnreadable, 'no PII plaintext anywhere in the stored op params (only the AES-GCM ciphertext)', 'name/email/“jane” present=' + (!ciphertextUnreadable));
  verdict(booksRight, 'foldBalances books the sale correctly from the CLEAR account/cents (PII not needed to fold)', 'AR=' + booksBefore[AR] + 'c SALES=' + booksBefore[SALES] + 'c');
  // and the envelope IS readable while the key lives (proves the ciphertext genuinely carries the PII)
  var openedBefore = await Vault.open(vault.get(SUBJECT), envelope);
  verdict(openedBefore.email === PII.email, 'while the key lives, vault.open() recovers the PII (the ciphertext really carries it)', 'email=' + openedBefore.email);

  // capture the chain state BEFORE erasure (tip + verify), for the identical-tip proof.
  var vBefore = await K.verifyChain(db);
  var tipBefore = vBefore.tip;

  // ── §ERASE — destroy the subject key (crypto-shred). open() throws; chain still verifies; tip identical ──
  console.log('\n§ERASE — destroy the subject key -> plaintext irrecoverable; chain still verifies; tip UNCHANGED');
  var hadKey = vault.destroy(SUBJECT);                    // THE SHRED — the only key handle is gone
  var openThrew = false, openErr = '';
  try { await Vault.open(vault.get(SUBJECT), envelope); } catch (e) { openThrew = true; openErr = e.message; }
  var vAfter = await K.verifyChain(db);
  var tipAfter = vAfter.tip;
  verdict(hadKey && openThrew, 'after destroy(), vault.open() THROWS -> the PII plaintext is irrecoverable (erased)', 'threw=' + openThrew);
  verdict(vAfter.ok === true, 'verifyChain STILL ok after erasure (hash/sig are over the ciphertext, untouched)', 'chain.ok=' + vAfter.ok);
  verdict(tipAfter === tipBefore && tipBefore != null, 'chain tip is BYTE-IDENTICAL before vs after erasure', 'tip ' + (tipBefore || '').slice(0, 12) + '… == ' + (tipAfter || '').slice(0, 12) + '…');

  // ── §BOOKS-INTACT — foldBalances cent-identical before vs after the erasure ──
  console.log('\n§BOOKS-INTACT — the ledger folds account/cents, never PII -> books are cent-identical after erasure');
  var booksAfter = PC.foldBalances(K.replayOps(db)).bal;
  var eq = PC.balEqual(booksBefore, booksAfter);
  verdict(eq.maxDiffCents === 0, 'books byte/cent-identical before vs after key destruction', 'maxDiff=' + eq.maxDiffCents + 'c');

  // ── §AUDIT-RESIDUAL — the op + hash remain (event happened) but the subject is now unidentifiable ──
  console.log('\n§AUDIT-RESIDUAL — the op row + hash remain (an event provably happened); the identity is a tombstone');
  var rows = readRows(db);
  var postRow = rows.filter(function (r) { return r.op_type === 'POST'; })[0];
  var opPresent = !!postRow && postRow.op_hash != null && postRow.sig != null;
  // the residual params: the accounting fact + a ciphertext that no longer decrypts = a tombstone marker.
  var residual = JSON.parse(postRow.parameters);
  var tombstoned = false, tombErr = '';
  try { await Vault.open(vault.get(residual.subject_id), residual.pii_envelope); } catch (e) { tombstoned = true; tombErr = e.message; }
  verdict(opPresent, 'the POST op row + its op_hash + signature are STILL in the log (the accounting fact is kept)', 'hash=' + (postRow.op_hash || '').slice(0, 10) + '… signed=' + (postRow.sig ? 'Y' : 'N'));
  verdict(tombstoned && residual.subject_id === SUBJECT && !!residual.pii_envelope,
    'the subject_id + envelope remain as a TOMBSTONE but no longer decrypt -> identity unrecoverable, fact retained', 'subject=' + residual.subject_id + ' decrypts=' + (!tombstoned));

  // ── §FALSIFIER — store the SAME PII in CLEARTEXT -> "erasure" requires deleting/rewriting the op,
  //    which BREAKS the chain. Proves crypto-shredding is load-bearing (envelope != cosmetic). ──
  console.log('\n§FALSIFIER — PII in CLEARTEXT: erasing it = delete/rewrite the op -> the chain breaks (envelope is load-bearing)');
  mute();
  var db2 = new SQL.Database(); K.ensureTable(db2);
  K.commitOp(db2, 'POST', {
    table: 'C_Invoice', id: 'INV-1', subject_id: SUBJECT,
    lines: [{ account_id: AR, amtacctdr: AMT, amtacctcr: 0 }, { account_id: SALES, amtacctdr: 0, amtacctcr: AMT }],
    customer_name: PII.name, customer_email: PII.email     // <-- PII in CLEARTEXT (the bug)
  }, null, null, null, BASE_TS + (ts++));
  // a second op so there is a chain LINK that the rewrite must break.
  K.commitOp(db2, 'POST', {
    table: 'C_Invoice', id: 'INV-2',
    lines: [{ account_id: AR, amtacctdr: AMT, amtacctcr: 0 }, { account_id: SALES, amtacctdr: 0, amtacctcr: AMT }]
  }, null, null, null, BASE_TS + (ts++));
  await K.sealChain(db2);
  var vClean = await K.verifyChain(db2);
  var clearTipBefore = vClean.tip;
  unmute();
  verdict(vClean.ok, 'cleartext-PII chain verifies BEFORE any erasure attempt', 'ok=' + vClean.ok);
  // attempt erasure WITHOUT re-sealing: scrub the PII out of the stored params in place (a "redaction").
  mute();
  var pid = db2.exec("SELECT id, parameters FROM kernel_ops WHERE op_type='POST' ORDER BY id")[0].values[0];
  var scrubbed = JSON.parse(pid[1]); delete scrubbed.customer_name; delete scrubbed.customer_email;
  db2.run('UPDATE kernel_ops SET parameters=? WHERE id=?', [JSON.stringify(scrubbed), pid[0]]);
  var vBroken = await K.verifyChain(db2);                 // do NOT re-seal: the stored op_hash now mismatches
  unmute();
  verdict(!vBroken.ok, 'redacting cleartext PII in place BREAKS the chain (stored op_hash no longer matches)', 'ok=' + vBroken.ok + ' brokeAt=' + vBroken.brokeAt + ' why=' + vBroken.why);
  // Most charitable repair for the cleartext model: drop the now-stale sigs so sealChain RE-SIGNS the new
  // hashes (give cleartext-erasure its best shot at a verifying chain). It STILL yields a DIFFERENT tip =
  // history provably rewritten - there is no clean cleartext erasure, which is exactly what shredding fixes.
  mute();
  db2.run("UPDATE kernel_ops SET sig=NULL");             // let the re-seal re-sign over the new hashes
  await K.sealChain(db2);
  var vReseal = await K.verifyChain(db2);
  unmute();
  verdict(vReseal.ok && vReseal.tip !== clearTipBefore,
    're-sealing (even re-signing) to repair verifies but yields a DIFFERENT tip = history rewritten (not erasure) -> cleartext cannot be erased cleanly',
    'newTip=' + (vReseal.tip || '').slice(0, 12) + '… != oldTip=' + (clearTipBefore || '').slice(0, 12) + '…');

  console.log('\n   ▸ headline: after destroying the subject key — chain verifies (ok=' + vAfter.ok + '), tip IDENTICAL, books maxDiff=' + eq.maxDiffCents + 'c,');
  console.log('     PII irrecoverable. Crypto-shredding satisfies right-to-erasure WITHOUT breaking the immutable signed log;');
  console.log('     the falsifier shows cleartext PII can only be "erased" by rewriting history (chain breaks / tip changes).');
  console.log('\n═══ ' + (fails ? '🔴 ' + fails + ' FAILED' : '🟢 ALL PASS') + ' ═══');
  process.exit(fails ? 1 : 0);
})().catch(function (e) { unmute(); console.error('FATAL', e); process.exit(1); });
