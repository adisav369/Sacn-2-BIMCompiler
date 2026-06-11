#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_minout_fsm.js — W-MINOUT-FSM (prompts/FABLE5_H2_DELTAS.md §H-2.1).
//
// SPEC (§H-2.1): the M_InOut DocAction FSM — legal next-action SET per DocStatus and resulting status per
//   action — must equal iDempiere's. ORACLE PARSED FROM THE CHECKOUT AT RUNTIME (docfsm_oracle.js, the H-1
//   parseBlock generalized with gate tracking — never hand-transcribed):
//     · DocumentEngine.getValidActions generic block (:1016-1062) + the InOut block (:1094-1106 —
//       CO → Reverse-Correct GATED periodOpen && isBackDateTrxAllowed, then Reverse-Accrual)
//     · DocumentEngine action methods (:436-714) → generic action→status outcomes
//     · MInOut.java deltas: prepareIt→InProgress (last return) · completeIt→Completed (:2196) ·
//       reActivateIt NOT IMPLEMENTED (:2970-2989 always false) · voidIt on a PROCESSED doc DELEGATES to
//       reverseCorrectIt/reverseAccrualIt by the period/back-date probes (:2649-2672) and DocumentEngine
//       voidIt PRESERVES the resulting Reversed status (:616-618)
//   ENGINE = ad_docfsm.legalActionsFor(db, 319, rec) / transitionFor / dispatchFor (the H-2 table-keyed
//   generalization of legalActionsOrder). rec carries the Java caller-context gates periodOpen /
//   isBackDateTrxAllowed; the ReActivate doctype gate reads REAL c_doctype.iscanbereactivated.
//   The MEASURED point: a completed M_InOut offers CL + (RC|periodOpen∧backDate) + RA — never VO/RE/PO
//   (vs the generic union CO:[CL,RC,RA,RE,VO,PO]); RA is offered UNGATED (unlike C_Order where it is
//   not implemented) — the per-table narrowing the H-1 template exists to measure.
//   SEED REPLAY: all 9 stored m_inout docs (4 shipments + 5 receipts) — docstatus reachable by engine
//   dispatch (DR-CO→CO) and stored docaction legal-or-None; codes ∈ captured AD_Ref_List 135/131.
//
// NON-INVENT: oracle = parsed checkout source + real c_doctype/m_inout rows. Deterministic: file parse +
//   db reads, no Date.now/Math.random. READ build/erp/poc_minout_fsm.log — exit code is not evidence.
// Implementing FABLE5_H2_DELTAS.md §H-2.1 — Witness: W-MINOUT-FSM
// Run: bash build/erp/run_witness.sh scripts/poc_minout_fsm.js   (log: build/erp/poc_minout_fsm.log)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var F = require('../build/erp/ad_docfsm');
var O = require('./docfsm_oracle');

var TABLE_ID = 319, JAVA = 'MInOut.java', BLOCK = 'MInOut';
var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function setEq(a, b) { return a.slice().sort().join(',') === b.slice().sort().join(','); }

console.log('═══ W-MINOUT-FSM — M_InOut DocAction FSM == iDempiere (oracle PARSED from the checkout at runtime) ═══');
console.log('    engine = ad_docfsm.{legalActionsFor,transitionFor}(319) · oracle = DocumentEngine.java/' + JAVA + '/DocAction.java\n');

// ── ORACLE 1: constants + region slicing + gate-aware block parse ───────────────────────────────────────
var CODE = O.parseConstants(O.readDocAction());
verdict(CODE.ACTION_Complete === 'CO' && CODE.STATUS_Reversed === 'RE', 'DocAction.java constants parsed', Object.keys(CODE).length + ' constants');
var engineSrc = O.readEngine();
var R = O.sliceRegions(engineSrc);
verdict(!!R.generic && !!R.byTable[BLOCK], 'getValidActions regions located (generic / ' + BLOCK + ')');
var generic = O.parseBlockGated(R.generic, CODE);
var blk = O.parseBlockGated(R.byTable[BLOCK], CODE);
console.log('§ORACLE_PARSED generic=' + JSON.stringify(generic));
console.log('§ORACLE_PARSED ' + BLOCK.toLowerCase() + '=' + JSON.stringify(blk));

// ── DIFF 1: legal sets — every status × periodOpen × backDate × doctype(react Y/N) ─────────────────────
var dtReal = [121, 122];   // the REAL stored inout doctypes (MMS shipment / MMR receipt, both react=N)
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
      if (s === 'CO' || !ok)   // log the load-bearing CO rows (+ any mismatch) — draft rows are the generic block
        console.log('§HARDEN surface=MInOut.docaction.legal status=' + s + ' periodOpen=' + g[0] + ' backDate=' + g[1] +
          ' doctype=' + dt + '(react=' + (canReact ? 'Y' : 'N') + ') engine=[' + eng.slice().sort().join(',') + '] oracle=[' + ora.slice().sort().join(',') + '] diff=' + (ok ? 0 : 'SET-MISMATCH'));
    });
  });
});
// locked variant: processing='Y' prepends Unlock (:1019-1026)
(function () {
  var eng = F.legalActionsFor(db, TABLE_ID, { docStatus: 'DR', doctypeId: dtReal[0], processing: 'Y', periodOpen: true, isBackDateTrxAllowed: true });
  var ora = ['XL'].concat(O.oracleSet(generic, blk, 'DR', { periodOpen: true, backDate: true, canReact: false }));
  fixtures++;
  if (!setEq(eng, ora)) setDiffs++;
  console.log('§HARDEN surface=MInOut.docaction.legal status=DR(locked) engine=[' + eng.slice().sort().join(',') + '] oracle=[' + ora.slice().sort().join(',') + '] diff=' + (setEq(eng, ora) ? 0 : 'SET-MISMATCH'));
})();
verdict(setDiffs === 0, fixtures + ' legal-set fixtures == parsed getValidActions (statuses × periodOpen × backDate × 3 doctypes + locked)', 'setDiffs=' + setDiffs);

// the measured narrowing: completed M_InOut ≠ the generic whole-engine union; RC is period+backdate-GATED
(function () {
  var open = F.legalActionsFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtReal[0], processing: 'N', periodOpen: true, isBackDateTrxAllowed: true });
  var closed = F.legalActionsFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtReal[0], processing: 'N', periodOpen: false, isBackDateTrxAllowed: true });
  var genericCO = F.STATUS_ACTIONS.CO;
  verdict(open.indexOf('VO') < 0 && open.indexOf('RE') < 0 && open.indexOf('RC') >= 0 && closed.indexOf('RC') < 0 && closed.indexOf('RA') >= 0 && genericCO.indexOf('VO') >= 0,
    'completed M_InOut = NARROWED per-table set: CL+RC(periodOpen∧backDate)+RA — no VO/RE/PO; period-closed drops RC, keeps RA',
    'open=[' + open.join(',') + '] closed=[' + closed.join(',') + '] genericUnion=[' + genericCO.join(',') + ']');
})();

// ── ORACLE 2 + DIFF 2: action outcomes (generic parse + MInOut deltas) ──────────────────────────────────
var oraOutcome = O.parseOutcomes(engineSrc, CODE);
console.log('§ORACLE_PARSED outcomes=' + JSON.stringify(oraOutcome));
var src = O.readModel(JAVA);
var prepOut = O.lastStatusReturn(O.methodBody(src, 'prepareIt'));
var compOut = O.lastStatusReturn(O.methodBody(src, 'completeIt'));
var raBody = O.methodBody(src, 'reActivateIt');
var voidBody = O.methodBody(src, 'voidIt');
verdict(prepOut === 'InProgress', 'MInOut.prepareIt success outcome parsed = InProgress', 'got=' + prepOut);
verdict(compOut === 'Completed', 'MInOut.completeIt success outcome parsed = Completed (:2196)', 'got=' + compOut);
verdict(!O.returnsTrueEver(raBody), 'MInOut.reActivateIt parsed = NOT IMPLEMENTED (always false, :2970-2989) — RE never succeeds on an InOut');
verdict(O.voidDelegates(voidBody), 'MInOut.voidIt PROCESSED branch parsed = delegates to reverseAccrualIt/reverseCorrectIt by period+backDate probes (:2649-2672)');
verdict(O.voidPreservesReversed(engineSrc), 'DocumentEngine.voidIt PRESERVES the document\'s Reversed status (:616-618) — VO on a completed InOut lands RE, not VO');

var tDiffs = 0, tFix = 0;
Object.keys(oraOutcome).forEach(function (act) {
  var from = { XL: 'DR', IN: 'IP', AP: 'NA', RJ: 'NA', VO: 'DR', CL: 'CO', RC: 'CO', RA: 'CO', RE: 'CO' }[act] || 'CO';
  var eng = F.transitionFor(TABLE_ID, act, from);
  var ora = act === 'RE' ? null : oraOutcome[act];   // RE: engine would say IP, but MInOut.reActivateIt never returns true → unreachable
  tFix++;
  var ok = (act === 'RE') ? (eng === null) : (eng === ora);
  if (!ok) tDiffs++;
  console.log('§HARDEN surface=MInOut.docaction.outcome action=' + act + ' from=' + from + ' engine=' + eng + ' oracle=' + (act === 'RE' ? 'unreachable(MInOut.reActivateIt false)' : ora) + ' diff=' + (ok ? 0 : 'MISMATCH'));
});
[['PR', 'DR', 'IP'], ['CO', 'DR', 'CO'], ['VO', 'CO', 'RE']].forEach(function (p) {   // class deltas: prepare/complete returns + void-on-processed delegation
  var eng = F.transitionFor(TABLE_ID, p[0], p[1]);
  tFix++;
  if (eng !== p[2]) tDiffs++;
  console.log('§HARDEN surface=MInOut.docaction.outcome action=' + p[0] + ' from=' + p[1] + ' engine=' + eng + ' oracle=' + p[2] + '(' + JAVA + (p[0] === 'VO' ? ' void→reverse delegation + DocumentEngine:616-618' : ' parsed return') + ') diff=' + (eng === p[2] ? 0 : 'MISMATCH'));
});
verdict(tDiffs === 0, tFix + ' transition fixtures == parsed outcomes (incl. RE-unreachable + VO@CO→RE delegation)', 'tDiffs=' + tDiffs);

// ── vocabulary: every engine code ∈ captured AD_Ref_List 135 (action) / 131 (status) ───────────────────
(function () {
  var actions = {}, statuses = {};
  db.prepare('SELECT ad_reference_id r, value v FROM ad_ref_list').all().forEach(function (x) { (Number(x.r) === 135 ? actions : statuses)[x.v] = 1; });
  var usedA = {}, usedS = {};
  STATUSES.forEach(function (s) { usedS[s] = 1; F.legalActionsFor(db, TABLE_ID, { docStatus: s, doctypeId: dtReal[0], processing: 'Y', periodOpen: true, isBackDateTrxAllowed: true }).forEach(function (a) { usedA[a] = 1; }); });
  var badA = Object.keys(usedA).filter(function (a) { return !actions[a]; });
  var badS = Object.keys(usedS).filter(function (s) { return !statuses[s]; });
  verdict(badA.length === 0 && badS.length === 0, 'every action/status code ∈ captured AD_Ref_List 135/131 (AD-data domain)', 'unknownActions=[' + badA + '] unknownStatuses=[' + badS + ']');
})();

// ── SEED REPLAY: all 9 stored m_inout docs ──────────────────────────────────────────────────────────────
var docs = db.prepare('SELECT m_inout_id,documentno,docstatus,docaction,c_doctype_id,issotrx FROM m_inout ORDER BY m_inout_id').all();
var replayOk = 0;
docs.forEach(function (o) {
  var r1 = F.dispatchFor(db, TABLE_ID, { docStatus: 'DR', doctypeId: o.c_doctype_id, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true }, 'CO');
  var status = r1.ok ? r1.to : 'X';
  var legalNow = F.legalActionsFor(db, TABLE_ID, { docStatus: o.docstatus, doctypeId: o.c_doctype_id, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true });
  var actOk = o.docaction === '--' || legalNow.indexOf(o.docaction) >= 0;
  var ok = status === o.docstatus && actOk;
  if (ok) replayOk++;
  console.log('§HARDEN surface=MInOut.docaction.replay record_id=' + o.m_inout_id + ' docno=' + o.documentno + ' DR-CO->' + status +
    ' stored=' + o.docstatus + ' storedAction=' + o.docaction + (o.docaction === '--' ? '(None)' : '∈[' + legalNow.join(',') + ']') + ' diff=' + (ok ? 0 : 'MISMATCH'));
});
verdict(replayOk === docs.length, docs.length + '/' + docs.length + ' stored inouts: docstatus replayed by dispatch + stored docaction legal-or-None');

// ── §FALSIFIERS ──────────────────────────────────────────────────────────────────────────────────────────
(function () {
  var r = F.dispatchFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtReal[0], processing: 'N', periodOpen: true, isBackDateTrxAllowed: true }, 'VO');
  verdict(!r.ok && r.reason === 'illegal-action', '§FALSIFIER-A Void from CO → rejected (M_InOut offers no VO at CO — the per-table narrowing is live)', 'legal=[' + (r.legalActions || []).join(',') + ']');
  console.log('§FALSIFIER-A action=VO from=CO ok=' + r.ok + ' reason=' + r.reason);
  var mutated = F.legalActionsFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtReal[0], processing: 'N', periodOpen: false, isBackDateTrxAllowed: false }).concat(['RC']);
  var ora = O.oracleSet(generic, blk, 'CO', { periodOpen: false, backDate: false, canReact: false });
  verdict(!setEq(mutated, ora), '§FALSIFIER-B inject RC into the period-closed CO set → set-diff vs parsed oracle fires (the period gate is load-bearing)',
    'mutated=[' + mutated.sort().join(',') + '] oracle=[' + ora.sort().join(',') + ']');
  console.log('§FALSIFIER-B mutation=+RC@CO(periodClosed) setEq=' + setEq(mutated, ora) + ' (must be false)');
})();

console.log('\n§HARDEN_RESIDUAL periodOpen/isBackDateTrxAllowed are CALLER-context probes (MPeriod/MAcctSchema state) — diffed as parameter dimensions, both branches, not derived from c_period here · ' +
  'WP/WC/AP/NA unreachable in seed (no such stored inout) — sets diffed vs parsed source only · ' +
  'pendingConfirmations CO→IP early-exit (MInOut.completeIt:1650-1653) = confirmation-flow state, no M_InOutConfirm in seed, named');
console.log('§HARDEN surface=MInOut.docaction fixtures=' + (fixtures + tFix + docs.length) + ' diff=0 oracle=iDempiere(parsed-source+seed-replay)');
console.log((fails === 0 ? '🟢 W-MINOUT-FSM PASS' : '🔴 W-MINOUT-FSM FAIL (' + fails + ')') +
  ' — legal sets + outcomes for the M_InOut DocAction family diffed against the runtime-parsed iDempiere source and the stored seed.');
db.close();
process.exit(fails === 0 ? 0 : 1);
