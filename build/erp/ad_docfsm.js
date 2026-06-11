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

  // ── MOrder-specific FSM (H-1.4, ADDITIVE) ────────────────────────────────────────────────────────────
  // legalActionsOrder(db, rec) — faithful port of DocumentEngine.getValidActions:1008-1090 for C_Order:
  // the GENERIC status block (:1018-1060) + the Order block (:1065-1090). Narrower than the generic
  // STATUS_ACTIONS above (which is the whole-engine union): completed ORDERS offer Close/Void(+ReActivate
  // when the doctype allows) — Reverse-Correct/-Accrual are NOT offered (order reversal rides INSIDE voidIt,
  // MOrder.java:3016; reverseAccrualIt is not implemented for orders, MOrder.java:3023-3042 returns false).
  // rec = { docStatus, isSOTrx ('Y'/'N'), doctypeId, processing ('Y' when locked) }. Reads ONLY layer-1 (db).
  function legalActionsOrder(db, rec) {
    var a = [];
    if (rec.processing === 'Y') a.push('XL');                       // :1019-1026 locked → Unlock
    var s = rec.docStatus;
    if (s === 'NA') a.push('PR', 'VO');                             // :1029-1033
    else if (s === 'DR' || s === 'IP' || s === 'IN') a.push('CO', 'PR', 'VO');   // :1035-1042
    else if (s === 'AP') a.push('CO', 'VO');                        // :1044-1048
    else if (s === 'CO') a.push('CL');                              // :1050-1053 generic
    else if (s === 'WP' || s === 'WC') a.push('VO', 'PR');          // :1055-1060
    // the Order block (:1065-1090)
    var dt = rec.doctypeId ? db.prepare('SELECT docsubtypeso,iscanbereactivated FROM c_doctype WHERE c_doctype_id=?').get(Number(rec.doctypeId)) : null;
    if (s === 'CO') {
      a.push('VO');                                                 // :1080
      if (dt && dt.iscanbereactivated === 'Y') a.push('RE');        // :1082-1083 canReactivateThisDocType
    } else if (s === 'WP') {
      a.push('RE', 'CL');                                           // :1085-1089
    }
    return a;
  }

  // transitionOrder(action, fromStatus, opts) — the RESULTING status for C_Order, per DocumentEngine's
  // action methods (:436-714 — each sets m_status after document success) with the MOrder deltas:
  //   CO → 'CO' normally; 'WP' for a Prepay doctype (MOrder.completeIt:2145); 'IP' when DocAction was a
  //        bare Prepare (:2117 just-prepare). PR → 'IP' (MOrder.prepareIt:1705).
  //   RC → voidIt internally (MOrder.reverseCorrectIt:3016) → engine status 'RE' (DocumentEngine:648-663).
  //   RA → null: MOrder.reverseAccrualIt is NOT implemented (always false, :3023-3042) — never succeeds.
  // opts = { docSubTypeSO } for the prepay split. Pure; doctype-conditioned via opts only.
  var ORDER_OUTCOME = { XL: 'DR', IN: 'IN', PR: 'IP', AP: 'AP', RJ: 'NA', VO: 'VO', CL: 'CL', RC: 'RE', RA: null, RE: 'IP', PO: null };
  function transitionOrder(action, fromStatus, opts) {
    if (action === 'CO') return (opts && opts.docSubTypeSO === 'PR') ? 'WP' : 'CO';
    if (action === 'PO') return fromStatus;                          // postIt leaves status unchanged (:574-590)
    return Object.prototype.hasOwnProperty.call(ORDER_OUTCOME, action) ? ORDER_OUTCOME[action] : null;
  }

  // dispatchOrder(db, rec, action, opts) — gate by the ORDER legal set, then transition (the A-5 seam shape).
  function dispatchOrder(db, rec, action, opts) {
    var legal = legalActionsOrder(db, rec);
    if (legal.indexOf(action) < 0) return { ok: false, reason: 'illegal-action', from: rec.docStatus, action: action, legalActions: legal };
    var to = transitionOrder(action, rec.docStatus, opts);
    if (to == null) return { ok: false, reason: action === 'RA' ? 'not-implemented(MOrder.reverseAccrualIt:3042)' : 'no-transition', from: rec.docStatus, action: action };
    return { ok: true, from: rec.docStatus, action: action, to: to, legalActions: legal };
  }

  // ── Document-family FSM, table-keyed (H-2, ADDITIVE — prompts/FABLE5_H2_DELTAS.md) ──────────────────
  // legalActionsFor(db, adTableId, rec) — generalizes legalActionsOrder to the walked DEEP-delta tables:
  // the GENERIC status block (DocumentEngine.getValidActions:1016-1062) + the per-table narrowing block.
  // rec adds the two caller-context gates the Java signature passes in (:1009-1010): periodOpen,
  // isBackDateTrxAllowed. The ReActivate gate reads the REAL c_doctype.iscanbereactivated
  // (canReactivateThisDocType:1523). Un-walked tables throw — the isomorph tail adds them deliberately,
  // never silently inherits the generic union (the exact error the per-table narrowing exists to kill).
  var DOC_FAMILY = {
    319: { name: 'M_InOut',     reActivate: false },  // MInOut.reActivateIt:2970-2989 always false
    318: { name: 'C_Invoice',   reActivate: true  },  // MInvoice.reActivateIt:2866+ implemented (gated)
    335: { name: 'C_Payment',   reActivate: true  },  // MPayment.reActivateIt:2901+ implemented (gated)
    323: { name: 'M_Movement',  reActivate: false },  // MMovement.reActivateIt:1071-1085 always false
    321: { name: 'M_Inventory', reActivate: false },  // MInventory.reActivateIt:1200-1214 always false
    325: { name: 'M_Production',reActivate: false }   // MProduction.reActivateIt:1021-1033 always false
  };
  function legalActionsFor(db, adTableId, rec) {
    if (Number(adTableId) === 259) return legalActionsOrder(db, rec);   // the H-1 port, untouched
    var fam = DOC_FAMILY[Number(adTableId)];
    if (!fam) throw new Error('legalActionsFor: ad_table_id ' + adTableId + ' not walked (H-2 family only — add via the isomorph tail, never default to the generic union)');
    var a = [];
    if (rec.processing === 'Y') a.push('XL');                           // :1019-1026 locked → Unlock
    var s = rec.docStatus;
    if (s === 'NA') a.push('PR', 'VO');                                 // :1029-1033
    else if (s === 'DR' || s === 'IP' || s === 'IN') a.push('CO', 'PR', 'VO');  // :1035-1042
    else if (s === 'AP') a.push('CO', 'VO');                            // :1044-1048
    else if (s === 'CO') a.push('CL');                                  // :1050-1053 generic
    else if (s === 'WP' || s === 'WC') a.push('VO', 'PR');              // :1055-1060
    if (s === 'CO') {                                                   // the per-table Completed block
      var dt = rec.doctypeId ? db.prepare('SELECT iscanbereactivated FROM c_doctype WHERE c_doctype_id=?').get(Number(rec.doctypeId)) : null;
      var canReact = !!(dt && dt.iscanbereactivated === 'Y');
      var po = !!rec.periodOpen, bd = !!rec.isBackDateTrxAllowed;
      var t = Number(adTableId);
      if (t === 319 || t === 323 || t === 321 || t === 325) {           // InOut :1096-1106 · Movement+Inventory :1200-1213 · Production :1233-1244
        if (po && bd) a.push('RC');
        a.push('RA');
      } else if (t === 318) {                                           // Invoice :1108-1125
        if (po) { if (canReact) a.push('RE'); if (bd) a.push('RC'); }
        a.push('RA');
      } else if (t === 335) {                                           // Payment :1127-1141
        if (po) { a.push('RC'); if (canReact) a.push('RE'); }
        a.push('RA');
      }
    }
    return a;
  }

  // transitionFor(adTableId, action, fromStatus) — the RESULTING status per the class's action methods:
  //   PR → IP / CO → CO (every walked class's prepareIt/completeIt success return — MInOut.java:1614/2596,
  //   MInvoice.java:1814/2347, MPayment.java:2006/2153, MMovement.java:365/697, MInventory.java:449/739,
  //   MProduction.java:634/222). VO from an UNPROCESSED status (DR/IP/IN/AP/NA) → VO; VO from CO DELEGATES
  //   to reverseCorrectIt (period open + back-date allowed) or reverseAccrualIt — both set DOCSTATUS_Reversed,
  //   and DocumentEngine.voidIt PRESERVES it (:616-618 "if (!getDocStatus().equals(STATUS_Reversed))") → RE.
  //   RC/RA → RE (implemented in all six). RE → IP where the class implements reActivateIt (Invoice/Payment),
  //   null where the body always returns false (InOut/Movement/Inventory/Production). PO → unchanged.
  function transitionFor(adTableId, action, fromStatus) {
    var fam = DOC_FAMILY[Number(adTableId)];
    if (!fam) return null;
    if (action === 'CO') return 'CO';
    if (action === 'PR') return 'IP';
    if (action === 'PO') return fromStatus;
    if (action === 'RE') return fam.reActivate ? 'IP' : null;
    if (action === 'VO') {
      if ('DR,IP,IN,AP,NA'.indexOf(fromStatus) >= 0) return 'VO';       // not-processed branch
      if (fromStatus === 'CO' || fromStatus === 'WP' || fromStatus === 'WC') return 'RE';  // reversal delegation
      return null;                                                      // CL/RE/VO: "Document Closed"
    }
    var GENERIC = { XL: 'DR', IN: 'IN', AP: 'AP', RJ: 'NA', CL: 'CL', RC: 'RE', RA: 'RE' };
    return Object.prototype.hasOwnProperty.call(GENERIC, action) ? GENERIC[action] : null;
  }

  // dispatchFor(db, adTableId, rec, action) — gate by the per-table legal set, then transition (A-5 seam).
  function dispatchFor(db, adTableId, rec, action) {
    var legal = legalActionsFor(db, adTableId, rec);
    if (legal.indexOf(action) < 0) return { ok: false, reason: 'illegal-action', from: rec.docStatus, action: action, legalActions: legal };
    var to = transitionFor(adTableId, action, rec.docStatus);
    if (to == null) {
      var fam = DOC_FAMILY[Number(adTableId)];
      return { ok: false, reason: action === 'RE' ? 'not-implemented(' + fam.name + '.reActivateIt returns false)' : 'no-transition', from: rec.docStatus, action: action };
    }
    return { ok: true, from: rec.docStatus, action: action, to: to, legalActions: legal };
  }

  // reachableStatuses() — every status reachable as a transition TARGET (the FSM's range; vs the engine's 2).
  function reachableStatuses() {
    var s = {}; Object.keys(TRANSITION).forEach(function (a) { var m = TRANSITION[a]; Object.keys(m).forEach(function (f) { s[m[f]] = 1; }); });
    return Object.keys(s).sort();
  }

  var API = {
    STATUS_ACTIONS: STATUS_ACTIONS, TRANSITION: TRANSITION, readDocType: readDocType,
    legalActions: legalActions, transition: transition, dispatch: dispatch, reachableStatuses: reachableStatuses,
    legalActionsOrder: legalActionsOrder, transitionOrder: transitionOrder, dispatchOrder: dispatchOrder,
    DOC_FAMILY: DOC_FAMILY, legalActionsFor: legalActionsFor, transitionFor: transitionFor, dispatchFor: dispatchFor
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = API;   // node witness
  if (typeof window !== 'undefined') window.AdDocFsm = API;                     // browser
  else if (global) global.AdDocFsm = API;
})(typeof globalThis !== 'undefined' ? globalThis : this);
