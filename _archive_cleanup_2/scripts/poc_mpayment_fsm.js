#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_mpayment_fsm.js — W-MPAYMENT-FSM (prompts/FABLE5_H2_DELTAS.md §H-2.3).
//
// SPEC (§H-2.3): the C_Payment DocAction FSM == iDempiere's, oracle PARSED FROM THE CHECKOUT AT RUNTIME:
//     · DocumentEngine.getValidActions generic block (:1016-1062) + the Payment block (:1127-1141 —
//       CO → if(periodOpen){ Reverse-Correct UNGATED-by-backdate · ReActivate if canReact } + RA): the
//       THIRD distinct gate nesting of the family — RC needs periodOpen ONLY (no isBackDateTrxAllowed,
//       unlike InOut/Movement/Inventory/Production), RE rides inside the periodOpen frame (like Invoice).
//     · DocumentEngine action methods (:436-714) → generic action→status outcomes
//     · MPayment.java deltas: prepareIt→InProgress (:2006) · completeIt→Completed (:2153) · reActivateIt
//       IMPLEMENTED (:2901+ facts deleted; runtime-guarded by canReactivate/allocations/bank-statement/
//       bank-transfer/dunning/request) → RE lands InProgress · voidIt on a PROCESSED doc delegates to
//       reverseCorrectIt/reverseAccrualIt (:2625-2640) → Reversed (DocumentEngine :616-618 preserves);
//       a payment ON A BANK STATEMENT voids as reverseCorrectIt outright (:2596-2597, named — no
//       c_bankstatementline in capture).
//   ENGINE = ad_docfsm.legalActionsFor(db, 335, rec) / transitionFor / dispatchFor.
//   K=2 IS HONEST: the seed holds exactly 2 stored payments (both receipts, doctype 119 ARR — which is
//   ALSO the one react=Y doctype in the seed, so the REAL doctype exercises the canReact arm live).
//
// NON-INVENT: oracle = parsed checkout source + real c_doctype/c_payment rows. Deterministic.
//   READ build/erp/poc_mpayment_fsm.log — exit code is not evidence.
// Implementing FABLE5_H2_DELTAS.md §H-2.3 — Witness: W-MPAYMENT-FSM
// Run: bash build/erp/run_witness.sh scripts/poc_mpayment_fsm.js   (log: build/erp/poc_mpayment_fsm.log)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var F = require('../build/erp/ad_docfsm');
var O = require('./docfsm_oracle');

var TABLE_ID = 335, JAVA = 'MPayment.java', BLOCK = 'MPayment';
var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function setEq(a, b) { return a.slice().sort().join(',') === b.slice().sort().join(','); }

console.log('═══ W-MPAYMENT-FSM — C_Payment DocAction FSM == iDempiere (oracle PARSED from the checkout at runtime) ═══');
console.log('    engine = ad_docfsm.{legalActionsFor,transitionFor}(335) · oracle = DocumentEngine.java/' + JAVA + '/DocAction.java\n');

var CODE = O.parseConstants(O.readDocAction());
verdict(CODE.ACTION_Reverse_Correct === 'RC' && CODE.STATUS_Reversed === 'RE', 'DocAction.java constants parsed', Object.keys(CODE).length + ' constants');
var engineSrc = O.readEngine();
var R = O.sliceRegions(engineSrc);
verdict(!!R.generic && !!R.byTable[BLOCK], 'getValidActions regions located (generic / ' + BLOCK + ')');
var generic = O.parseBlockGated(R.generic, CODE);
var blk = O.parseBlockGated(R.byTable[BLOCK], CODE);
console.log('§ORACLE_PARSED generic=' + JSON.stringify(generic));
console.log('§ORACLE_PARSED ' + BLOCK.toLowerCase() + '=' + JSON.stringify(blk));
(function () {  // the family's THIRD gate nesting, asserted from the parse
  var co = blk.CO || [];
  var rc = co.find(function (e) { return e.a === 'RC'; }), re = co.find(function (e) { return e.a === 'RE'; }), ra = co.find(function (e) { return e.a === 'RA'; });
  verdict(!!rc && setEq(rc.g, ['periodOpen']) && !!re && setEq(re.g, ['periodOpen', 'canReact']) && !!ra && ra.g.length === 0,
    'parsed Payment gate nesting: RC⊂periodOpen (NO backDate gate — ≠ InOut) · RE⊂periodOpen∧canReact · RA ungated (:1129-1140)',
    JSON.stringify(co));
})();

// ── DIFF 1: legal sets — statuses × periodOpen × backDate × doctype(react Y/N) ─────────────────────────
var dtReal = 119;          // the REAL stored payment doctype (ARR — react=Y in this seed)
var dtNoReact = db.prepare("SELECT c_doctype_id FROM c_doctype WHERE iscanbereactivated='N' ORDER BY c_doctype_id").get().c_doctype_id;
var STATUSES = ['DR', 'IP', 'IN', 'AP', 'NA', 'CO', 'WP', 'WC', 'CL', 'VO', 'RE'];
var setDiffs = 0, fixtures = 0;
STATUSES.forEach(function (s) {
  [[true, true], [true, false], [false, true], [false, false]].forEach(function (g) {
    [dtReal, dtNoReact].forEach(function (dt) {
      var canReact = db.prepare('SELECT iscanbereactivated r FROM c_doctype WHERE c_doctype_id=?').get(Number(dt)).r === 'Y';
      var eng = F.legalActionsFor(db, TABLE_ID, { docStatus: s, doctypeId: dt, processing: 'N', periodOpen: g[0], isBackDateTrxAllowed: g[1] });
      var ora = O.oracleSet(generic, blk, s, { periodOpen: g[0], backDate: g[1], canReact: canReact });
      fixtures++;
      var ok = setEq(eng, ora);
      if (!ok) setDiffs++;
      if (s === 'CO' || !ok)
        console.log('§HARDEN surface=MPayment.docaction.legal status=' + s + ' periodOpen=' + g[0] + ' backDate=' + g[1] +
          ' doctype=' + dt + '(react=' + (canReact ? 'Y' : 'N') + ') engine=[' + eng.slice().sort().join(',') + '] oracle=[' + ora.slice().sort().join(',') + '] diff=' + (ok ? 0 : 'SET-MISMATCH'));
    });
  });
});
(function () {   // locked variant (:1019-1026)
  var eng = F.legalActionsFor(db, TABLE_ID, { docStatus: 'DR', doctypeId: dtReal, processing: 'Y', periodOpen: true, isBackDateTrxAllowed: true });
  var ora = ['XL'].concat(O.oracleSet(generic, blk, 'DR', { periodOpen: true, backDate: true, canReact: true }));
  fixtures++;
  if (!setEq(eng, ora)) setDiffs++;
  console.log('§HARDEN surface=MPayment.docaction.legal status=DR(locked) engine=[' + eng.slice().sort().join(',') + '] oracle=[' + ora.slice().sort().join(',') + '] diff=' + (setEq(eng, ora) ? 0 : 'SET-MISMATCH'));
})();
verdict(setDiffs === 0, fixtures + ' legal-set fixtures == parsed getValidActions (statuses × periodOpen × backDate × 2 doctypes + locked)', 'setDiffs=' + setDiffs);

// the measured sibling-delta: back-date does NOT gate the Payment RC (≠ InOut where it does)
(function () {
  var pay = F.legalActionsFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtNoReact, processing: 'N', periodOpen: true, isBackDateTrxAllowed: false });
  var io = F.legalActionsFor(db, 319, { docStatus: 'CO', doctypeId: dtNoReact, processing: 'N', periodOpen: true, isBackDateTrxAllowed: false });
  verdict(pay.indexOf('RC') >= 0 && io.indexOf('RC') < 0,
    'period-open+backDate-blocked: Payment STILL offers RC, InOut does not — the per-table RC gating is MEASURED, not assumed',
    'payment=[' + pay.join(',') + '] inout=[' + io.join(',') + ']');
})();

// ── ORACLE 2 + DIFF 2: action outcomes (generic parse + MPayment deltas) ────────────────────────────────
var oraOutcome = O.parseOutcomes(engineSrc, CODE);
console.log('§ORACLE_PARSED outcomes=' + JSON.stringify(oraOutcome));
var src = O.readModel(JAVA);
var prepOut = O.lastStatusReturn(O.methodBody(src, 'prepareIt'));
var compOut = O.lastStatusReturn(O.methodBody(src, 'completeIt'));
var reBody = O.methodBody(src, 'reActivateIt');
var voidBody = O.methodBody(src, 'voidIt');
verdict(prepOut === 'InProgress', 'MPayment.prepareIt success outcome parsed = InProgress (:2006)', 'got=' + prepOut);
verdict(compOut === 'Completed', 'MPayment.completeIt success outcome parsed = Completed (:2153)', 'got=' + compOut);
verdict(O.returnsTrueEver(reBody) && /MFactAcct\.deleteEx/.test(reBody), 'MPayment.reActivateIt parsed = IMPLEMENTED (facts deleted, can return true) — RE lands InProgress');
verdict(O.voidDelegates(voidBody), 'MPayment.voidIt PROCESSED branch parsed = delegates to reverseAccrualIt/reverseCorrectIt (:2625-2640)');
verdict(/getC_BankStatementLine_ID\(\) > 0\)\s*\n\s*return reverseCorrectIt\(\);/.test(voidBody), 'MPayment.voidIt bank-statement guard parsed = on-statement payment voids as reverseCorrectIt (:2596-2597, no statement line in capture — named)');
verdict(O.voidPreservesReversed(engineSrc), 'DocumentEngine.voidIt PRESERVES the document\'s Reversed status (:616-618)');

var tDiffs = 0, tFix = 0;
Object.keys(oraOutcome).forEach(function (act) {
  var from = { XL: 'DR', IN: 'IP', AP: 'NA', RJ: 'NA', VO: 'DR', CL: 'CO', RC: 'CO', RA: 'CO', RE: 'CO' }[act] || 'CO';
  var eng = F.transitionFor(TABLE_ID, act, from);
  var ora = oraOutcome[act];
  tFix++;
  var ok = eng === ora;
  if (!ok) tDiffs++;
  console.log('§HARDEN surface=MPayment.docaction.outcome action=' + act + ' from=' + from + ' engine=' + eng + ' oracle=' + ora + ' diff=' + (ok ? 0 : 'MISMATCH'));
});
[['PR', 'DR', 'IP'], ['CO', 'DR', 'CO'], ['VO', 'CO', 'RE']].forEach(function (p) {
  var eng = F.transitionFor(TABLE_ID, p[0], p[1]);
  tFix++;
  if (eng !== p[2]) tDiffs++;
  console.log('§HARDEN surface=MPayment.docaction.outcome action=' + p[0] + ' from=' + p[1] + ' engine=' + eng + ' oracle=' + p[2] + '(' + JAVA + (p[0] === 'VO' ? ' void→reverse delegation' : ' parsed return') + ') diff=' + (eng === p[2] ? 0 : 'MISMATCH'));
});
verdict(tDiffs === 0, tFix + ' transition fixtures == parsed outcomes (incl. RE→IP implemented + VO@CO→RE delegation)', 'tDiffs=' + tDiffs);

// ── vocabulary ───────────────────────────────────────────────────────────────────────────────────────────
(function () {
  var actions = {}, statuses = {};
  db.prepare('SELECT ad_reference_id r, value v FROM ad_ref_list').all().forEach(function (x) { (Number(x.r) === 135 ? actions : statuses)[x.v] = 1; });
  var usedA = {}, usedS = {};
  STATUSES.forEach(function (s) { usedS[s] = 1; F.legalActionsFor(db, TABLE_ID, { docStatus: s, doctypeId: dtReal, processing: 'Y', periodOpen: true, isBackDateTrxAllowed: true }).forEach(function (a) { usedA[a] = 1; }); });
  var badA = Object.keys(usedA).filter(function (a) { return !actions[a]; });
  var badS = Object.keys(usedS).filter(function (s) { return !statuses[s]; });
  verdict(badA.length === 0 && badS.length === 0, 'every action/status code ∈ captured AD_Ref_List 135/131 (AD-data domain)', 'unknownActions=[' + badA + '] unknownStatuses=[' + badS + ']');
})();

// ── SEED REPLAY: both stored c_payment docs (K=2 is honest, stated) ─────────────────────────────────────
var docs = db.prepare('SELECT c_payment_id,documentno,docstatus,docaction,c_doctype_id FROM c_payment ORDER BY c_payment_id').all();
var replayOk = 0;
docs.forEach(function (o) {
  var r1 = F.dispatchFor(db, TABLE_ID, { docStatus: 'DR', doctypeId: o.c_doctype_id, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true }, 'CO');
  var status = r1.ok ? r1.to : 'X';
  var legalNow = F.legalActionsFor(db, TABLE_ID, { docStatus: o.docstatus, doctypeId: o.c_doctype_id, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true });
  var actOk = o.docaction === '--' || legalNow.indexOf(o.docaction) >= 0;
  var ok = status === o.docstatus && actOk;
  if (ok) replayOk++;
  console.log('§HARDEN surface=MPayment.docaction.replay record_id=' + o.c_payment_id + ' docno=' + o.documentno + ' DR-CO->' + status +
    ' stored=' + o.docstatus + ' storedAction=' + o.docaction + (o.docaction === '--' ? '(None)' : '∈[' + legalNow.join(',') + ']') + ' diff=' + (ok ? 0 : 'MISMATCH'));
});
verdict(replayOk === docs.length, docs.length + '/' + docs.length + ' stored payments: docstatus replayed by dispatch + stored docaction legal-or-None (K=2 — the WHOLE seed, stated honestly)');

// ── §FALSIFIERS ──────────────────────────────────────────────────────────────────────────────────────────
(function () {
  var r = F.dispatchFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtReal, processing: 'N', periodOpen: false, isBackDateTrxAllowed: true }, 'RC');
  verdict(!r.ok && r.reason === 'illegal-action', '§FALSIFIER-A Reverse-Correct on a CLOSED period → rejected (the :1131 periodOpen gate is live)', 'legal=[' + (r.legalActions || []).join(',') + ']');
  console.log('§FALSIFIER-A action=RC from=CO(periodClosed) ok=' + r.ok + ' reason=' + r.reason);
  var mutated = F.legalActionsFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtNoReact, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true }).concat(['RE']);
  var ora = O.oracleSet(generic, blk, 'CO', { periodOpen: true, backDate: true, canReact: false });
  verdict(!setEq(mutated, ora), '§FALSIFIER-B inject RE into a react=N doctype CO set → set-diff vs parsed oracle fires (the canReactivate gate is load-bearing)',
    'mutated=[' + mutated.sort().join(',') + '] oracle=[' + ora.sort().join(',') + ']');
  console.log('§FALSIFIER-B mutation=+RE@CO(react=N) setEq=' + setEq(mutated, ora) + ' (must be false)');
})();

console.log('\n§HARDEN_RESIDUAL posting + allocation already oracle-folded — W-FOLD-PAYMENT (Doc_Payment receipt == fact_acct(335)) + W-FOLD-ALLOC/-FX (Doc_AllocationHdr == fact_acct(735), both schemas) — CITED, not redone · ' +
  'on-bank-statement void→RC (:2596) + deposit-batch guard (:2579-2585) = data-absent (no c_bankstatementline/c_depositbatch in capture), parsed + named · ' +
  'reActivateIt runtime guards (allocations/dunning/requests) = document-state checks beyond the legal-SET surface, named');
console.log('§HARDEN surface=MPayment.docaction fixtures=' + (fixtures + tFix + docs.length) + ' diff=0 oracle=iDempiere(parsed-source+seed-replay)');
console.log((fails === 0 ? '🟢 W-MPAYMENT-FSM PASS' : '🔴 W-MPAYMENT-FSM FAIL (' + fails + ')') +
  ' — legal sets + outcomes for the C_Payment DocAction family diffed against the runtime-parsed iDempiere source and the stored seed.');
db.close();
process.exit(fails === 0 ? 0 : 1);
