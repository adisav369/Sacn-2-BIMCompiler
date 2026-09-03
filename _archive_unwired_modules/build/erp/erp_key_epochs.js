// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// erp_key_epochs.js — SERVERLESS_HARDENING_RESUME.md §H-6 — Witness: W-ROTATE
// Key lifecycle (rotation / revoke / offboarding) WITHOUT re-signing history. Composes on the kernel's
// hash chain (build/erp/kernel_ops.js: prev_hash/op_hash give integrity+order, key-independent) and a
// signer (build/erp/erp_snapshot_sign.js: ECDSA P-256 over the op_hash hex). This module is the
// AUTHENTICITY layer: it derives a key-epoch schedule from the SIGNED control ops already in the chain
// (ROTATE / REVOKE) and verifies every op's signature under the key valid AT ITS seq — so the past is
// never re-signed. It does NOT bloat kernel_ops.js (composes like erp_period_close.js).
//
// Model (the §H-6 spec):
//   • Genesis key K0 is active from the first op.
//   • A ROTATE op carries {kind:'ROTATE', newKid, signed_by} and is COUNTER-SIGNED by the OUTGOING
//     (currently-active) key. On a valid counter-signature the active key becomes newKid for ops AFTER it.
//     History (ops before it) keeps verifying under the old key — nothing is restated.
//   • A REVOKE op carries {kind:'REVOKE', revokedKid, newKid?, signed_by}. The revoked key's FUTURE ops
//     (seq >= the revoke) reject; its PAST ops stay valid (it really did author them). An optional newKid
//     installs the successor in the same op (graceful offboarding / compromise recovery).
//   • A normal op carries {signed_by} = the kid that signed it; it is valid iff that kid is the ACTIVE key
//     at its seq AND not revoked as of its seq AND its signature verifies under that kid's pubkey.
//
// The epoch schedule is DERIVED, never invented: it is read off the signed ROTATE/REVOKE ops in the log.
(function () {
  'use strict';

  // verifyEpochSigs(db, opts) — walk kernel_ops in id (seq) order, verifying each op's signature under
  // the key valid at its seq, applying ROTATE/REVOKE transitions in line. Hash-chain integrity is the
  // kernel's job (KernelOps.verifyChain with NO signer) — this checks authenticity per-epoch only.
  //   opts = { pubByKid:{kid->pubJwk}, genesisKid, verify: async(msgHex, sigHex, pubJwk)->bool }
  //   returns { ok, len, brokeAt?, why?, activeKid }  (brokeAt = the offending op id)
  async function verifyEpochSigs(db, opts) {
    var pubByKid = opts.pubByKid, verify = opts.verify;
    var activeKid = opts.genesisKid;
    var revoked = {};                                  // kid -> the id at/after which it is rejected
    var r = db.exec('SELECT id, op_type, parameters, op_hash, sig FROM kernel_ops ORDER BY id');
    if (!r.length) return { ok: true, len: 0, activeKid: activeKid };
    var rows = r[0].values;
    for (var i = 0; i < rows.length; i++) {
      var id = rows[i][0], opHash = rows[i][3], sig = rows[i][4];
      var p = rows[i][2]; try { p = JSON.parse(p); } catch (e) { p = {}; }
      var signer = p.signed_by, kind = p.kind || 'NORMAL';
      if (opHash == null) return _fail(id, 'unsealed (hash chain not sealed)');
      var pendingActive = null;

      if (kind === 'ROTATE') {
        // must be counter-signed by the OUTGOING (currently-active) key.
        if (signer !== activeKid) return _fail(id, 'ROTATE not counter-signed by outgoing key (signer=' + signer + ' active=' + activeKid + ')');
        if (!(await verify(opHash, sig, pubByKid[activeKid]))) return _fail(id, 'ROTATE counter-signature invalid under outgoing key ' + activeKid);
        if (!pubByKid[p.newKid]) return _fail(id, 'ROTATE installs unknown key ' + p.newKid);
        pendingActive = p.newKid;
      } else if (kind === 'REVOKE') {
        // signed by the current authority (the outgoing/active key) — graceful offboarding.
        if (signer !== activeKid) return _fail(id, 'REVOKE not authorised by active key (signer=' + signer + ' active=' + activeKid + ')');
        if (!(await verify(opHash, sig, pubByKid[activeKid]))) return _fail(id, 'REVOKE signature invalid under active key ' + activeKid);
        revoked[p.revokedKid] = id;                   // revokedKid's ops at id >= this are rejected
        if (p.newKid) {                               // optional successor install
          if (!pubByKid[p.newKid]) return _fail(id, 'REVOKE installs unknown key ' + p.newKid);
          pendingActive = p.newKid;
        }
      } else {
        // normal op: the signer must be the ACTIVE key, not revoked, with a valid signature.
        if (revoked[signer] != null && id >= revoked[signer]) return _fail(id, 'op signed by REVOKED key ' + signer + ' (revoked at ' + revoked[signer] + ')');
        if (signer !== activeKid) return _fail(id, 'op signed by non-active key ' + signer + ' (active=' + activeKid + ') — superseded key cannot sign new ops');
        if (!(await verify(opHash, sig, pubByKid[signer]))) return _fail(id, 'signature invalid under ' + signer);
      }
      if (pendingActive) activeKid = pendingActive;
    }
    console.log('§EPOCH verifyEpochSigs OK len=' + rows.length + ' finalActiveKid=' + activeKid);
    return { ok: true, len: rows.length, activeKid: activeKid };
  }

  function _fail(id, why) { console.log('§EPOCH verify FAIL at id=' + id + ' why=' + why); return { ok: false, brokeAt: id, why: why }; }

  // singleKeyVerify(db, opts) — the NO-EPOCH baseline (the §FALSIFIER's straw man): verify EVERY op under
  // ONE fixed key, ignoring rotation/revocation. A compromised key can then forge forever — this function
  // exists only to show that contrast against verifyEpochSigs.
  //   opts = { pub:pubJwk, verify: async(msgHex, sigHex, pubJwk)->bool }
  async function singleKeyVerify(db, opts) {
    var r = db.exec('SELECT id, op_hash, sig FROM kernel_ops ORDER BY id');
    if (!r.length) return { ok: true, len: 0 };
    var rows = r[0].values;
    for (var i = 0; i < rows.length; i++) {
      if (rows[i][1] == null) return { ok: false, brokeAt: rows[i][0], why: 'unsealed' };
      if (!(await opts.verify(rows[i][1], rows[i][2], opts.pub))) return { ok: false, brokeAt: rows[i][0], why: 'signature invalid' };
    }
    return { ok: true, len: rows.length };
  }

  var _api = { verifyEpochSigs: verifyEpochSigs, singleKeyVerify: singleKeyVerify };
  if (typeof module !== 'undefined' && module.exports) { module.exports = _api; }
  if (typeof window !== 'undefined') { window.ErpKeyEpochs = _api; }
  if (typeof console !== 'undefined') { console.log('§EPOCH_LOADED v1 (key-epoch verification — rotate/revoke without re-signing history)'); }
})();
