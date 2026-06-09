// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ad_docfsm.js — C_DocType document FSM (W-DOCFSM). The "C_DocType FSM" leg of the coverage matrix: the
// legal-action set per DocStatus + the action→resulting-status transition table, a faithful port of
// iDempiere's DocumentEngine.getValidActions / processIt. Extends Lane-3's DocAction spine (which folded
// only CO/IP) to the full 14 actions × 12 statuses, keyed to a REAL C_DocType row. Self-contained, no kernel
// dep (mirrors ad_process.js / ad_modelval.js shape).
//
// SEAM (docs/ERP_BACKEND_SEPARATION.md §2 A-5): a PURE status machine. It decides which actions are legal
// from a status and what status an action yields — it knows NOTHING about posting amounts (A-1) or workflow
// routing (A-6). The doc-action path consults it to gate/dispatch; the reversal family (VO/RC/RA/RE/CL)
// reuses the existing handlers. No side-channel to any sibling module.
//
// EXTRACT, DON'T INVENT: the action vocabulary (AD_Reference 135, 14 values) and status vocabulary
// (AD_Reference 131, 12 values) are AD data (ad_full.db, lowercase ad_ref_list); the transition logic is the
// DOCUMENTED DocumentEngine state machine (ported, cited), not guessed. legalActions reads the real
// c_doctype row so the FSM is per-document-type-addressable; finer doctype-conditioned gating (proforma /
// DocSubType specifics) is the named-deferred remainder. Determinism: pure table lookups, no Date/random.
// Implementing ERP_COVERAGE_MATRIX.md §C_DocType FSM (ranked GAP #5) — Witness: W-DOCFSM
(function (global) {
  'use strict';

  // ── getValidActions(docStatus) → the legal DocAction set (port of DocumentEngine.getValidActions) ──────
  // Action codes (AD_Ref 135): CO Complete · PR Prepare · AP Approve · RJ Reject · VO Void · CL Close ·
  //   RC Reverse-Correct · RA Reverse-Accrual · RE Re-Activate · PO Post · IN Invalidate · WC Wait-Complete ·
  //   XL Unlock. Status codes (AD_Ref 131): DR IP IN AP NA CO WC WP CL VO RE ??.
  var STATUS_ACTIONS = {
    DR: ['CO', 'PR', 'VO', 'XL'],
    IP: ['CO', 'VO', 'XL', 'IN', 'WC'],
    IN: ['CO', 'PR', 'VO', 'XL'],
    AP: ['CO', 'RJ', 'VO', 'XL'],
    NA: ['AP', 'RJ', 'VO', 'XL'],
    CO: ['CL', 'RC', 'RA', 'RE', 'VO', 'PO'],
    WC: ['CO', 'VO', 'XL'],
    WP: ['CO', 'CL', 'RE', 'VO'],
    CL: [], VO: [], RE: [], '??': []                       // terminal / unknown
  };

  // ── transition(action, fromStatus) → toStatus (port of DocumentEngine.processIt status changes) ─────────
  var TRANSITION = {
    PR: { DR: 'IP', IP: 'IP', IN: 'IP' },                  // prepareIt
    CO: { DR: 'CO', IP: 'CO', IN: 'CO', AP: 'CO', NA: 'CO', WC: 'CO', WP: 'CO' },  // completeIt
    VO: { DR: 'VO', IP: 'VO', IN: 'VO', AP: 'VO', NA: 'VO', CO: 'VO', WC: 'VO', WP: 'VO' },  // voidIt
    CL: { CO: 'CL', WP: 'CL' },                            // closeIt
    RC: { CO: 'RE' },                                      // reverseCorrectIt
    RA: { CO: 'RE' },                                      // reverseAccrualIt
    RE: { CO: 'IP' },                                      // reActivateIt
    PO: { CO: 'CO' },                                      // postIt (status unchanged)
    RJ: { AP: 'NA', NA: 'NA', IP: 'NA' },                  // reject
    AP: { NA: 'IP', IP: 'IP' },                            // approve (re-enters flow)
    IN: { IP: 'IN' },                                      // invalidateIt
    WC: { IP: 'WC' },                                      // waitComplete
    XL: { DR: 'DR', IP: 'IP', IN: 'IN', CO: 'CO', AP: 'AP', NA: 'NA', WC: 'WC', WP: 'WP' }  // unlock (no change)
  };

  function readDocType(db, doctypeId) {
    return db.prepare('SELECT c_doctype_id,name,docbasetype,issotrx,docsubtypeso FROM c_doctype WHERE c_doctype_id=?').get(doctypeId);
  }

  // legalActions(db, doctypeId, fromStatus) — the per-C_DocType legal-action set for a status. Reads the real
  // c_doctype row (so the FSM is doctype-addressable) and returns the status-driven options.
  function legalActions(db, doctypeId, fromStatus) {
    var dt = readDocType(db, doctypeId);
    if (!dt) throw new Error('no c_doctype ' + doctypeId);
    var acts = (STATUS_ACTIONS[fromStatus] || []).slice();
    return { doctype: dt.c_doctype_id, docBaseType: dt.docbasetype, fromStatus: fromStatus, actions: acts };
  }

  // transition(action, fromStatus) → toStatus or null (illegal). Pure, doctype-independent core.
  function transition(action, fromStatus) {
    var m = TRANSITION[action];
    return (m && Object.prototype.hasOwnProperty.call(m, fromStatus)) ? m[fromStatus] : null;
  }

  // dispatch(db, doctypeId, fromStatus, action) — gate (is the action legal here?) THEN transition.
  function dispatch(db, doctypeId, fromStatus, action) {
    var la = legalActions(db, doctypeId, fromStatus);
    var legal = la.actions.indexOf(action) >= 0;
    if (!legal) return { ok: false, reason: 'illegal-action', doctype: la.doctype, from: fromStatus, action: action, legalActions: la.actions };
    var to = transition(action, fromStatus);
    if (to == null) return { ok: false, reason: 'no-transition', doctype: la.doctype, from: fromStatus, action: action };
    return { ok: true, doctype: la.doctype, docBaseType: la.docBaseType, from: fromStatus, action: action, to: to, legalActions: la.actions };
  }

  // reachableStatuses() — every status reachable as a transition TARGET (the FSM's range; vs the engine's 2).
  function reachableStatuses() {
    var s = {}; Object.keys(TRANSITION).forEach(function (a) { var m = TRANSITION[a]; Object.keys(m).forEach(function (f) { s[m[f]] = 1; }); });
    return Object.keys(s).sort();
  }

  var API = {
    STATUS_ACTIONS: STATUS_ACTIONS, TRANSITION: TRANSITION, readDocType: readDocType,
    legalActions: legalActions, transition: transition, dispatch: dispatch, reachableStatuses: reachableStatuses
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = API;   // node witness
  if (typeof window !== 'undefined') window.AdDocFsm = API;                     // browser
  else if (global) global.AdDocFsm = API;
})(typeof globalThis !== 'undefined' ? globalThis : this);
