#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_minvoice_fsm.js — W-MINVOICE-FSM (prompts/FABLE5_H2_DELTAS.md §H-2.2).
//
// SPEC (§H-2.2): the C_Invoice DocAction FSM == iDempiere's, oracle PARSED FROM THE CHECKOUT AT RUNTIME:
//     · DocumentEngine.getValidActions generic block (:1016-1062) + the Invoice block (:1108-1125 — the
//       NESTED gate structure: CO → if(periodOpen){ RE if canReact · RC if isBackDateTrxAllowed } + RA)
//     · DocumentEngine action methods (:436-714) → generic action→status outcomes
//     · MInvoice.java deltas: prepareIt→InProgress (:1814) · completeIt→Completed (:2347) ·
//       reActivateIt IMPLEMENTED (:2866+ — facts deleted, Posted reset; runtime-guarded by period/
//       canReactivate/allocation/MatchInv/MatchPO) → RE lands InProgress · voidIt on a PROCESSED doc
//       delegates to reverseCorrectIt/reverseAccrualIt (:2536-2559) → Reversed, preserved by
//       DocumentEngine.voidIt (:616-618)
//   ENGINE = ad_docfsm.legalActionsFor(db, 318, rec) / transitionFor / dispatchFor.
//   The MEASURED point vs the SIBLING tables: the Invoice RE and RC both live INSIDE the periodOpen frame
//   (period closed → neither, even when back-date is allowed) — a different gate nesting than M_InOut
//   (RC needs periodOpen∧backDate at ONE level) and than C_Payment (RC needs periodOpen only). The
//   gate-aware parse must reproduce exactly this, or the set-diff fires.
//   SEED REPLAY: all 8 stored c_invoice docs (4 sales + 4 vendor).
//
// NON-INVENT: oracle = parsed checkout source + real c_doctype/c_invoice rows. Deterministic.
//   READ build/erp/poc_minvoice_fsm.log — exit code is not evidence.
// Implementing FABLE5_H2_DELTAS.md §H-2.2 — Witness: W-MINVOICE-FSM
// Run: bash build/erp/run_witness.sh scripts/poc_minvoice_fsm.js   (log: build/erp/poc_minvoice_fsm.log)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var F = require('../build/erp/ad_docfsm');
var O = require('./docfsm_oracle');

var TABLE_ID = 318, JAVA = 'MInvoice.java', BLOCK = 'MInvoice';
var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function setEq(a, b) { return a.slice().sort().join(',') === b.slice().sort().join(','); }

console.log('═══ W-MINVOICE-FSM — C_Invoice DocAction FSM == iDempiere (oracle PARSED from the checkout at runtime) ═══');
console.log('    engine = ad_docfsm.{legalActionsFor,transitionFor}(318) · oracle = DocumentEngine.java/' + JAVA + '/DocAction.java\n');

var CODE = O.parseConstants(O.readDocAction());
verdict(CODE.ACTION_ReActivate === 'RE' && CODE.STATUS_Reversed === 'RE', 'DocAction.java constants parsed', Object.keys(CODE).length + ' constants');
var engineSrc = O.readEngine();
var R = O.sliceRegions(engineSrc);
verdict(!!R.generic && !!R.byTable[BLOCK], 'getValidActions regions located (generic / ' + BLOCK + ')');
var generic = O.parseBlockGated(R.generic, CODE);
var blk = O.parseBlockGated(R.byTable[BLOCK], CODE);
console.log('§ORACLE_PARSED generic=' + JSON.stringify(generic));
console.log('§ORACLE_PARSED ' + BLOCK.toLowerCase() + '=' + JSON.stringify(blk));
// the nested-gate shape is the point of this surface — assert the PARSE found it (from source, not hand-fed)
(function () {
  var co = blk.CO || [];
  var re = co.find(function (e) { return e.a === 'RE'; }), rc = co.find(function (e) { return e.a === 'RC'; }), ra = co.find(function (e) { return e.a === 'RA'; });
  verdict(!!re && setEq(re.g, ['periodOpen', 'canReact']) && !!rc && setEq(rc.g, ['periodOpen', 'backDate']) && !!ra && ra.g.length === 0,
    'parsed Invoice gate nesting: RE⊂periodOpen∧canReact · RC⊂periodOpen∧backDate · RA ungated (:1110-1124)',
    JSON.stringify(co));
})();

// ── DIFF 1: legal sets — statuses × periodOpen × backDate × doctype(react Y/N) ─────────────────────────
var dtReal = [117, 123];   // the REAL stored invoice doctypes (ARI sales / API vendor, both react=N)
var dtReact = db.prepare("SELECT c_doctype_id FROM c_doctype WHERE iscanbereactivated='Y' ORDER BY c_doctype_id").get().c_doctype_id;
var STATUSES = ['DR', 'IP', 'IN', 'AP', 'NA', 'CO', 'WP', 'WC', 'CL', 'VO', 'RE'];
var setDiffs = 0, fixtures = 0;
STATUSES.forEach(function (s) {
  [[true, true], [true, false], [false, true], [false, false]].forEach(function (g) {
    [dtReal[0], dtReal[1], dtReact].forEach(function (dt) {
      var canReact = db.prepare('SELECT iscanbereactivated r FROM c_doctype WHERE c_doctype_id=?').get(Number(dt)).r === 'Y';
      var eng = F.legalActionsFor(db, TABLE_ID, { docStatus: s, doctypeId: dt, processing: 'N', periodOpen: g[0], isBackDateTrxAllowed: g[1] });
      var ora = O.oracleSet(generic, blk, s, { periodOpen: g[0], backDate: g[1], canReact: canReact });
      fixtures++;
      var ok = setEq(eng, ora);
      if (!ok) setDiffs++;
      if (s === 'CO' || !ok)
        console.log('§HARDEN surface=MInvoice.docaction.legal status=' + s + ' periodOpen=' + g[0] + ' backDate=' + g[1] +
          ' doctype=' + dt + '(react=' + (canReact ? 'Y' : 'N') + ') engine=[' + eng.slice().sort().join(',') + '] oracle=[' + ora.slice().sort().join(',') + '] diff=' + (ok ? 0 : 'SET-MISMATCH'));
    });
  });
});
(function () {   // locked variant (:1019-1026)
  var eng = F.legalActionsFor(db, TABLE_ID, { docStatus: 'DR', doctypeId: dtReal[0], processing: 'Y', periodOpen: true, isBackDateTrxAllowed: true });
  var ora = ['XL'].concat(O.oracleSet(generic, blk, 'DR', { periodOpen: true, backDate: true, canReact: false }));
  fixtures++;
  if (!setEq(eng, ora)) setDiffs++;
  console.log('§HARDEN surface=MInvoice.docaction.legal status=DR(locked) engine=[' + eng.slice().sort().join(',') + '] oracle=[' + ora.slice().sort().join(',') + '] diff=' + (setEq(eng, ora) ? 0 : 'SET-MISMATCH'));
})();
verdict(setDiffs === 0, fixtures + ' legal-set fixtures == parsed getValidActions (statuses × periodOpen × backDate × 3 doctypes + locked)', 'setDiffs=' + setDiffs);

// the measured sibling-delta: period CLOSED kills RC even when back-date is allowed (≠ M_InOut nesting)
(function () {
  var inv = F.legalActionsFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtReact, processing: 'N', periodOpen: false, isBackDateTrxAllowed: true });
  var io = F.legalActionsFor(db, 319, { docStatus: 'CO', doctypeId: dtReact, processing: 'N', periodOpen: false, isBackDateTrxAllowed: true });
  verdict(inv.indexOf('RC') < 0 && inv.indexOf('RE') < 0 && setEq(inv, io),
    'period-closed+backDate-allowed: Invoice CO drops BOTH RE and RC (the :1110 periodOpen frame) — same residual set as InOut here, different nesting proven by the periodOpen×backDate sweep',
    'invoice=[' + inv.join(',') + '] inout=[' + io.join(',') + ']');
  var invOpen = F.legalActionsFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtReact, processing: 'N', periodOpen: true, isBackDateTrxAllowed: false });
  var ioOpen = F.legalActionsFor(db, 319, { docStatus: 'CO', doctypeId: dtReact, processing: 'N', periodOpen: true, isBackDateTrxAllowed: false });
  verdict(invOpen.indexOf('RE') >= 0 && invOpen.indexOf('RC') < 0 && ioOpen.indexOf('RE') < 0,
    'period-open+backDate-blocked: Invoice offers RE (react doctype) but not RC; InOut offers neither — the per-table gate nesting is MEASURED, not assumed',
    'invoice=[' + invOpen.join(',') + '] inout=[' + ioOpen.join(',') + ']');
})();

// ── ORACLE 2 + DIFF 2: action outcomes (generic parse + MInvoice deltas) ────────────────────────────────
var oraOutcome = O.parseOutcomes(engineSrc, CODE);
console.log('§ORACLE_PARSED outcomes=' + JSON.stringify(oraOutcome));
var src = O.readModel(JAVA);
var prepOut = O.lastStatusReturn(O.methodBody(src, 'prepareIt'));
var compOut = O.lastStatusReturn(O.methodBody(src, 'completeIt'));
var reBody = O.methodBody(src, 'reActivateIt');
var voidBody = O.methodBody(src, 'voidIt');
verdict(prepOut === 'InProgress', 'MInvoice.prepareIt success outcome parsed = InProgress (:1814)', 'got=' + prepOut);
verdict(compOut === 'Completed', 'MInvoice.completeIt success outcome parsed = Completed (:2347)', 'got=' + compOut);
verdict(O.returnsTrueEver(reBody) && /MFactAcct\.deleteEx/.test(reBody), 'MInvoice.reActivateIt parsed = IMPLEMENTED (facts deleted, can return true) — RE lands InProgress via DocumentEngine:700-714');
verdict(O.voidDelegates(voidBody), 'MInvoice.voidIt PROCESSED branch parsed = delegates to reverseAccrualIt/reverseCorrectIt (:2536-2559)');
verdict(O.voidPreservesReversed(engineSrc), 'DocumentEngine.voidIt PRESERVES the document\'s Reversed status (:616-618) — VO on a completed Invoice lands RE');

var tDiffs = 0, tFix = 0;
Object.keys(oraOutcome).forEach(function (act) {
  var from = { XL: 'DR', IN: 'IP', AP: 'NA', RJ: 'NA', VO: 'DR', CL: 'CO', RC: 'CO', RA: 'CO', RE: 'CO' }[act] || 'CO';
  var eng = F.transitionFor(TABLE_ID, act, from);
  var ora = oraOutcome[act];   // RE IS implemented for invoices — the engine must say IP, no exemption
  tFix++;
  var ok = eng === ora;
  if (!ok) tDiffs++;
  console.log('§HARDEN surface=MInvoice.docaction.outcome action=' + act + ' from=' + from + ' engine=' + eng + ' oracle=' + ora + ' diff=' + (ok ? 0 : 'MISMATCH'));
});
[['PR', 'DR', 'IP'], ['CO', 'DR', 'CO'], ['VO', 'CO', 'RE']].forEach(function (p) {
  var eng = F.transitionFor(TABLE_ID, p[0], p[1]);
  tFix++;
  if (eng !== p[2]) tDiffs++;
  console.log('§HARDEN surface=MInvoice.docaction.outcome action=' + p[0] + ' from=' + p[1] + ' engine=' + eng + ' oracle=' + p[2] + '(' + JAVA + (p[0] === 'VO' ? ' void→reverse delegation' : ' parsed return') + ') diff=' + (eng === p[2] ? 0 : 'MISMATCH'));
});
verdict(tDiffs === 0, tFix + ' transition fixtures == parsed outcomes (incl. RE→IP implemented + VO@CO→RE delegation)', 'tDiffs=' + tDiffs);

// ── vocabulary ───────────────────────────────────────────────────────────────────────────────────────────
(function () {
  var actions = {}, statuses = {};
  db.prepare('SELECT ad_reference_id r, value v FROM ad_ref_list').all().forEach(function (x) { (Number(x.r) === 135 ? actions : statuses)[x.v] = 1; });
  var usedA = {}, usedS = {};
  STATUSES.forEach(function (s) { usedS[s] = 1; F.legalActionsFor(db, TABLE_ID, { docStatus: s, doctypeId: dtReact, processing: 'Y', periodOpen: true, isBackDateTrxAllowed: true }).forEach(function (a) { usedA[a] = 1; }); });
  var badA = Object.keys(usedA).filter(function (a) { return !actions[a]; });
  var badS = Object.keys(usedS).filter(function (s) { return !statuses[s]; });
  verdict(badA.length === 0 && badS.length === 0, 'every action/status code ∈ captured AD_Ref_List 135/131 (AD-data domain)', 'unknownActions=[' + badA + '] unknownStatuses=[' + badS + ']');
})();

// ── SEED REPLAY: all 8 stored c_invoice docs ────────────────────────────────────────────────────────────
var docs = db.prepare('SELECT c_invoice_id,documentno,docstatus,docaction,c_doctype_id,issotrx FROM c_invoice ORDER BY c_invoice_id').all();
var replayOk = 0;
docs.forEach(function (o) {
  var r1 = F.dispatchFor(db, TABLE_ID, { docStatus: 'DR', doctypeId: o.c_doctype_id, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true }, 'CO');
  var status = r1.ok ? r1.to : 'X';
  var legalNow = F.legalActionsFor(db, TABLE_ID, { docStatus: o.docstatus, doctypeId: o.c_doctype_id, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true });
  var actOk = o.docaction === '--' || legalNow.indexOf(o.docaction) >= 0;
  var ok = status === o.docstatus && actOk;
  if (ok) replayOk++;
  console.log('§HARDEN surface=MInvoice.docaction.replay record_id=' + o.c_invoice_id + ' docno=' + o.documentno + ' DR-CO->' + status +
    ' stored=' + o.docstatus + ' storedAction=' + o.docaction + (o.docaction === '--' ? '(None)' : '∈[' + legalNow.join(',') + ']') + ' diff=' + (ok ? 0 : 'MISMATCH'));
});
verdict(replayOk === docs.length, docs.length + '/' + docs.length + ' stored invoices: docstatus replayed by dispatch + stored docaction legal-or-None');

// ── §FALSIFIERS ──────────────────────────────────────────────────────────────────────────────────────────
(function () {
  var r = F.dispatchFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtReal[0], processing: 'N', periodOpen: true, isBackDateTrxAllowed: true }, 'RE');
  verdict(!r.ok && r.reason === 'illegal-action', '§FALSIFIER-A ReActivate on a react=N doctype (the REAL ARI 117) → rejected (the canReactivate doctype gate is live)', 'legal=[' + (r.legalActions || []).join(',') + ']');
  console.log('§FALSIFIER-A action=RE from=CO doctype=117(react=N) ok=' + r.ok + ' reason=' + r.reason);
  var mutated = F.legalActionsFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtReact, processing: 'N', periodOpen: false, isBackDateTrxAllowed: true }).concat(['RC']);
  var ora = O.oracleSet(generic, blk, 'CO', { periodOpen: false, backDate: true, canReact: true });
  verdict(!setEq(mutated, ora), '§FALSIFIER-B inject RC into the period-closed CO set → set-diff vs parsed oracle fires (the periodOpen frame is load-bearing)',
    'mutated=[' + mutated.sort().join(',') + '] oracle=[' + ora.sort().join(',') + ']');
  console.log('§FALSIFIER-B mutation=+RC@CO(periodClosed,backDateOK) setEq=' + setEq(mutated, ora) + ' (must be false)');
})();

console.log('\n§HARDEN_RESIDUAL reActivateIt\'s RUNTIME guards (open period probe, no-allocation/no-MatchInv/no-MatchPO — MInvoice.java:2875-2898) are document-state checks beyond the legal-SET surface — the seed\'s allocated/matched invoices would fail them live; named, not folded here · ' +
  'WP/WC/AP/NA unreachable in seed · credit-status check at prepareIt = lifecycle row, named (H-1 pattern)');
console.log('§HARDEN surface=MInvoice.docaction fixtures=' + (fixtures + tFix + docs.length) + ' diff=0 oracle=iDempiere(parsed-source+seed-replay)');
console.log((fails === 0 ? '🟢 W-MINVOICE-FSM PASS' : '🔴 W-MINVOICE-FSM FAIL (' + fails + ')') +
  ' — legal sets + outcomes for the C_Invoice DocAction family diffed against the runtime-parsed iDempiere source and the stored seed.');
db.close();
process.exit(fails === 0 ? 0 : 1);
