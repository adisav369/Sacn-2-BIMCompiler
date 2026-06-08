// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// erp_pii_vault.js — Implementing SERVERLESS_HARDENING_RESUME.md §H-4 — Witness: W-ERASE
//
// Right-to-erasure on an immutable, sign-everything op-log (the GDPR/CCPA contradiction): you cannot
// unlink an op without breaking the hash chain (§7 records inputs; W-CHAIN/W-SIGN hash + sign them).
// The resolution is CRYPTO-SHREDDING: PII rides as a per-data-subject AES-GCM encrypted envelope INSIDE
// op.parameters (the ciphertext is the recorded input the kernel hashes/signs); non-PII (account/cents)
// stays in clear and folds normally through the SHIPPED erp_period_close.foldBalances. Erasure = destroy
// the subject's key -> the plaintext is irrecoverable, yet the op + prev_hash/op_hash + signature are over
// the ciphertext, so the chain STILL verifies and the tip is UNCHANGED, and the ledger is untouched.
//
// This module COMPOSES on the kernel (it adds ONE new primitive - a subject-keyed AES-GCM vault via
// webcrypto) - it does NOT bloat kernel_ops.js, mirroring how erp_period_close.js composes the fold.
// Determinism note: seal() uses a webcrypto random IV (encryption is NOT a fold/replay/hash path - the
// ciphertext is RECORDED as an op input, §7, then only ever read). No Date.now / no Math.random on any
// fold, replay, or hash path; the kernel hashes the recorded ciphertext bytes verbatim.
(function () {
  'use strict';

  // resolve a webcrypto SubtleCrypto in both node (global.crypto.subtle) and the browser (window.crypto.subtle).
  function _subtle() {
    var c = (typeof crypto !== 'undefined') ? crypto
          : (typeof global !== 'undefined' && global.crypto) ? global.crypto
          : (typeof window !== 'undefined' && window.crypto) ? window.crypto : null;
    if (!c || !c.subtle) throw new Error('erp_pii_vault: webcrypto SubtleCrypto unavailable');
    return c.subtle;
  }
  function _rng(n) {
    var c = (typeof crypto !== 'undefined') ? crypto
          : (typeof global !== 'undefined' && global.crypto) ? global.crypto
          : (typeof window !== 'undefined' && window.crypto) ? window.crypto : null;
    if (!c || !c.getRandomValues) throw new Error('erp_pii_vault: getRandomValues unavailable');
    return c.getRandomValues(new Uint8Array(n));
  }
  function _b64(u8) { return (typeof Buffer !== 'undefined') ? Buffer.from(u8).toString('base64') : btoa(String.fromCharCode.apply(null, u8)); }
  function _unb64(b64) { if (typeof Buffer !== 'undefined') { return new Uint8Array(Buffer.from(b64, 'base64')); } var s = atob(b64); var u = new Uint8Array(s.length); for (var i = 0; i < s.length; i++) u[i] = s.charCodeAt(i); return u; }
  function _enc(str) { return new TextEncoder().encode(str); }
  function _dec(u8) { return new TextDecoder().decode(u8); }

  // createSubjectKey - a fresh per-data-subject AES-GCM key (extractable:false -> it lives only as a
  // CryptoKey handle; destroying the handle is the shred). One key per data subject (customer/employee).
  async function createSubjectKey() {
    return await _subtle().generateKey({ name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
  }

  // seal - encrypt a plaintext PII object under the subject key -> { ct, iv } (base64). The ciphertext is
  // what rides in op.parameters and what the kernel hashes/signs (the recorded input, §7).
  async function seal(subjectKey, plaintextObj) {
    var iv = _rng(12);                                  // 96-bit GCM nonce
    var pt = _enc(JSON.stringify(plaintextObj));
    var ctBuf = await _subtle().encrypt({ name: 'AES-GCM', iv: iv }, subjectKey, pt);
    return { ct: _b64(new Uint8Array(ctBuf)), iv: _b64(iv) };
  }

  // open - decrypt an envelope back to the plaintext object. THROWS after the subject key is destroyed
  // (the key is gone -> AES-GCM auth fails / no key -> irrecoverable plaintext = the erasure guarantee).
  async function open(subjectKey, envelope) {
    if (!subjectKey) throw new Error('erp_pii_vault: subject key destroyed - plaintext irrecoverable (erased)');
    var ct = _unb64(envelope.ct), iv = _unb64(envelope.iv);
    var ptBuf = await _subtle().decrypt({ name: 'AES-GCM', iv: iv }, subjectKey, ct);
    return JSON.parse(_dec(new Uint8Array(ptBuf)));
  }

  // createVault - an in-memory subject->key map. destroy(subjectId) = crypto-shred (drop the only key
  // handle; the ciphertext at rest becomes permanently undecryptable - no plaintext, no rewrite needed).
  function createVault() {
    var keys = {};   // subjectId -> CryptoKey
    return {
      put:    function (subjectId, key) { keys[subjectId] = key; return key; },
      get:    function (subjectId) { return keys[subjectId] || null; },
      has:    function (subjectId) { return Object.prototype.hasOwnProperty.call(keys, subjectId) && keys[subjectId] != null; },
      destroy: function (subjectId) { var had = (Object.prototype.hasOwnProperty.call(keys, subjectId) && keys[subjectId] != null); delete keys[subjectId]; return had; },  // the shred
      size:   function () { return Object.keys(keys).length; }
    };
  }

  var _api = {
    createSubjectKey: createSubjectKey,
    seal: seal,
    open: open,
    createVault: createVault
  };
  if (typeof module !== 'undefined' && module.exports) { module.exports = _api; }
  if (typeof window !== 'undefined') { window.ErpPiiVault = _api; }
  if (typeof console !== 'undefined') { console.log('§PIIVAULT_LOADED v1 (per-subject AES-GCM envelope; destroy-key = crypto-shred)'); }
})();
