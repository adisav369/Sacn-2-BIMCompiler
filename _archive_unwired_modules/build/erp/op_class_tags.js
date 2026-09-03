// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * op_class_tags.js — CONSTRAINT_MITIGATION_LANE item 5: dictionary-tag the shared op-classes.
 *   Spec: docs/FoldEngineConstraints.md §4 P1 + §1 finding 1 (the 0.1% shared op-class).  Proven CAS path:
 *   W-QUORUM-CAS (scripts/poc_quorum_cas.js, 18 ms window, oversell=0). This module DECIDES WHEN that
 *   path fires — it does NOT re-implement it (no verb fork): the quorum-CAS commit is the existing relay path.
 *
 * The finding: conflict probability is a non-issue at target scale; the quorum-CAS is only needed for the
 * ~0.1% of ops that touch an INDIVISIBLE shared resource (stock units, credit headroom — the "one seat"
 * race). Running quorum-CAS on EVERY op pays the quorum-RTT for the 99.9% that can never conflict. Tag the
 * shared classes so the CAS path is taken ONLY for them; everything else takes the fast local-first commit.
 *
 * The tag = a small DICTIONARY registry (op_type + target table → contended resource). Default is
 * needsQuorum=FALSE (fast path); a class is contended ONLY if explicitly tagged here.
 *
 *   ERP.OpClass.needsQuorum(op) -> bool          // route to quorum-CAS iff true
 *   ERP.OpClass.classOf(op)     -> { shared, tag, resource } | { shared:false }
 *
 * §FALSIFIER: a KNOWN contended op (stock allocation / credit consumption) that returns needsQuorum=false
 *   would silently risk an oversell → the witness asserts every known contended class is tagged. And the
 *   inverse: tagging must restrict CAS to ≈0.1% (a mix where >>0.1% route to CAS means the tag is too broad).
 */
'use strict';

(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else { root.ERP = root.ERP || {}; root.ERP.OpClass = factory(); }
}(typeof self !== 'undefined' ? self : this, function () {

  // ── the dictionary tag: indivisible/contended classes that REQUIRE quorum-CAS ────────────────────
  // Grounded in the contended resources named by W-QUORUM-CAS + the iDempiere stock/credit model:
  //   STOCK   — an allocation against a finite on-hand quantity (the "one unit, two sellers" race).
  //   CREDIT  — consuming a finite credit headroom on a business partner.
  // op_type is the kernel op label; table is the affected AD table. A match on EITHER an explicit op_type
  // OR (op_type + table) tags the op shared. Everything not listed is fast-path (local-first) by default.
  var SHARED = [
    { tag: 'STOCK',  opType: 'ALLOCATE',       resource: 'indivisible stock unit (avail-bound)' },
    { tag: 'STOCK',  opType: 'RESERVE_STOCK',  resource: 'on-hand reservation' },
    { tag: 'STOCK',  opType: 'POST', table: 'M_Storage',     resource: 'on-hand quantity' },
    { tag: 'STOCK',  opType: 'POST', table: 'M_StorageOnHand', resource: 'on-hand quantity' },
    { tag: 'CREDIT', opType: 'CONSUME_CREDIT', resource: 'business-partner credit headroom' },
    { tag: 'CREDIT', opType: 'POST', table: 'C_BPartner', column: 'SO_CreditUsed', resource: 'credit headroom' }
  ];

  function _table(op) {
    var p = op && op.parameters;
    if (typeof p === 'string') { try { p = JSON.parse(p); } catch (e) { p = null; } }
    return p && (p.table || p.AD_Table || null);
  }

  function classOf(op) {
    if (!op) return { shared: false };
    var ot = op.op_type || op.type;
    var tbl = _table(op);
    for (var i = 0; i < SHARED.length; i++) {
      var s = SHARED[i];
      if (s.opType !== ot) continue;
      if (s.table && String(s.table).toLowerCase() !== String(tbl || '').toLowerCase()) continue;
      return { shared: true, tag: s.tag, resource: s.resource };
    }
    return { shared: false };
  }

  function needsQuorum(op) { return classOf(op).shared === true; }

  return { needsQuorum: needsQuorum, classOf: classOf, SHARED: SHARED };
}));
