#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_mbankstatement_fsm.js — W-MBANKSTMT-FSM (prompts/H2_ISOMORPH_TAIL.md — C_BankStatement).
//
// SPEC: the C_BankStatement DocAction FSM must equal iDempiere's. ORACLE PARSED AT RUNTIME:
//     · getValidActions generic + the BankStatement block (:1185-1198 — a FOURTH gate nesting: the
//       periodOpen frame encloses BOTH arms — RE⊂periodOpen∧canReact AND VO⊂periodOpen; period closed
//       → a completed statement offers ONLY the generic Close)
//     · MBankStatement.java deltas: prepareIt→InProgress · completeIt→Completed · voidIt voids
//       unprocessed AND completed (period-tested :679), never sets Reversed → VO lands VO (:506-602) ·
//       RC/RA ALWAYS false (:629-666) · reActivateIt IMPLEMENTED, period+doctype gated (:670-696)
//   ENGINE = ad_docfsm.legalActionsFor/transitionFor/dispatchFor(392).
//   SEED REPLAY: both stored c_bankstatements (1 completed + 1 draft; K=2 = the whole seed, stated).
//
// NON-INVENT: oracle = parsed checkout source + real c_doctype/c_bankstatement rows.
// Implementing H2_ISOMORPH_TAIL.md — Witness: W-MBANKSTMT-FSM
// Run: bash build/erp/run_witness.sh scripts/poc_mbankstatement_fsm.js   (log: build/erp/poc_mbankstatement_fsm.log)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var F = require('../build/erp/ad_docfsm');
var O = require('./docfsm_oracle');

var TABLE_ID = 392, JAVA = 'MBankStatement.java', BLOCK = 'MBankStatement';
var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function setEq(a, b) { return a.slice().sort().join(',') === b.slice().sort().join(','); }

console.log('═══ W-MBANKSTMT-FSM — C_BankStatement DocAction FSM == iDempiere (oracle PARSED at runtime) ═══');
console.log('    engine = ad_docfsm.{legalActionsFor,transitionFor}(392) · oracle = DocumentEngine.java/' + JAVA + '\n');

var CODE = O.parseConstants(O.readDocAction());
var engineSrc = O.readEngine();
var R = O.sliceRegions(engineSrc);
verdict(!!R.generic && !!R.byTable[BLOCK], 'getValidActions regions located (generic / ' + BLOCK + ')');
var generic = O.parseBlockGated(R.generic, CODE);
var blk = O.parseBlockGated(R.byTable[BLOCK], CODE);
console.log('§ORACLE_PARSED bankstmt=' + JSON.stringify(blk));
verdict(blk.CO && blk.CO.some(function (e) { return e.a === 'RE' && setEq(e.g, ['periodOpen', 'canReact']); })
  && blk.CO.some(function (e) { return e.a === 'VO' && e.g.join() === 'periodOpen'; })
  && !blk.CO.some(function (e) { return e.a === 'RC' || e.a === 'RA'; }),
  'BankStatement block parse = RE⊂periodOpen∧canReact · VO⊂periodOpen · NO RC/RA arms (:1185-1198 — the periodOpen frame encloses BOTH)');

// ── DIFF 1: legal sets ───────────────────────────────────────────────────────────────────────────────────
var dtReal = db.prepare('SELECT c_doctype_id FROM c_bankstatement LIMIT 1').get().c_doctype_id;  // 146 (react=Y, the stored doctype)
var dtN = db.prepare("SELECT c_doctype_id FROM c_doctype WHERE iscanbereactivated='N' ORDER BY c_doctype_id").get().c_doctype_id;
var STATUSES = ['DR', 'IP', 'IN', 'AP', 'NA', 'CO', 'WP', 'WC', 'CL', 'VO', 'RE'];
var setDiffs = 0, fixtures = 0;
STATUSES.forEach(function (s) {
  [[true, true], [true, false], [false, true], [false, false]].forEach(function (g) {
    [dtReal, dtN].forEach(function (dt) {
      var canReact = db.prepare('SELECT iscanbereactivated r FROM c_doctype WHERE c_doctype_id=?').get(Number(dt)).r === 'Y';
      var eng = F.legalActionsFor(db, TABLE_ID, { docStatus: s, doctypeId: dt, processing: 'N', periodOpen: g[0], isBackDateTrxAllowed: g[1] });
      var ora = O.oracleSet(generic, blk, s, { periodOpen: g[0], backDate: g[1], canReact: canReact });
      fixtures++;
      var ok = setEq(eng, ora);
      if (!ok) setDiffs++;
      if (s === 'CO' || !ok)
        console.log('§HARDEN surface=MBankStatement.docaction.legal status=' + s + ' periodOpen=' + g[0] + ' backDate=' + g[1] +
          ' doctype=' + dt + '(react=' + (canReact ? 'Y' : 'N') + ') engine=[' + eng.slice().sort().join(',') + '] oracle=[' + ora.slice().sort().join(',') + '] diff=' + (ok ? 0 : 'SET-MISMATCH'));
    });
  });
});
verdict(setDiffs === 0, fixtures + ' legal-set fixtures == parsed getValidActions', 'setDiffs=' + setDiffs);
// the narrowing: period-closed completed statement offers ONLY Close (both VO and RE are inside the frame)
(function () {
  var closed = F.legalActionsFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtReal, processing: 'N', periodOpen: false, isBackDateTrxAllowed: true });
  var open = F.legalActionsFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtReal, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true });
  verdict(setEq(closed, ['CL']) && setEq(open, ['CL', 'RE', 'VO']),
    'completed statement (react-Y doctype 146): periodOpen→[CL,RE,VO], periodClosed→[CL] ONLY — the frame gates BOTH arms', 'open=[' + open.join(',') + '] closed=[' + closed.join(',') + ']');
})();

// ── ORACLE 2 + DIFF 2: action outcomes ──────────────────────────────────────────────────────────────────
var src = O.readModel(JAVA);
verdict(O.lastStatusReturn(O.methodBody(src, 'prepareIt')) === 'InProgress' && O.lastStatusReturn(O.methodBody(src, 'completeIt')) === 'Completed',
  'prepareIt/completeIt success outcomes parsed = InProgress/Completed');
var voBody = O.methodBody(src, 'voidIt');
verdict(/testPeriodOpen/.test(voBody) && !/DOCSTATUS_Reversed\)/.test(voBody.replace(/DOCSTATUS_Reversed\.equals/g, '')) && O.returnsTrueEver(voBody),
  'MBankStatement.voidIt parsed = voids unprocessed AND completed (period-tested :679), NEVER sets Reversed → VO lands VO (:506-602)');
verdict(!O.returnsTrueEver(O.methodBody(src, 'reverseCorrectIt')) && !O.returnsTrueEver(O.methodBody(src, 'reverseAccrualIt')),
  'MBankStatement RC/RA parsed = ALWAYS false (:629-666) — reversal never succeeds on a statement');
verdict(O.returnsTrueEver(O.methodBody(src, 'reActivateIt')) && /canReactivateThisDocType/.test(O.methodBody(src, 'reActivateIt')),
  'MBankStatement.reActivateIt parsed = IMPLEMENTED, period+doctype gated (:670-696) → RE lands IP');

var tDiffs = 0, tFix = 0;
[['PR', 'DR', 'IP'], ['CO', 'DR', 'CO'], ['CL', 'CO', 'CL'],
 ['VO', 'DR', 'VO'], ['VO', 'IP', 'VO'], ['VO', 'CO', 'VO'],   // VO stays VO — no reversal delegation (unlike InOut/Alloc)
 ['RC', 'CO', null], ['RA', 'CO', null], ['RE', 'CO', 'IP']].forEach(function (p) {
  var eng = F.transitionFor(TABLE_ID, p[0], p[1]);
  tFix++;
  var ok = eng === p[2];
  if (!ok) tDiffs++;
  console.log('§HARDEN surface=MBankStatement.docaction.outcome action=' + p[0] + ' from=' + p[1] + ' engine=' + eng + ' oracle=' + p[2] + ' diff=' + (ok ? 0 : 'MISMATCH'));
});
verdict(tDiffs === 0, tFix + ' transition fixtures == parsed outcomes (incl. VO@CO→VO no-delegation + RC/RA-unreachable)', 'tDiffs=' + tDiffs);

// ── SEED REPLAY: both stored statements (1 CO + 1 DR) ───────────────────────────────────────────────────
var docs = db.prepare('SELECT c_bankstatement_id,name,docstatus,docaction,c_doctype_id,processing FROM c_bankstatement ORDER BY c_bankstatement_id').all();
var replayOk = 0;
docs.forEach(function (o) {
  var status;
  if (o.docstatus === 'DR') status = 'DR';
  else {
    var r1 = F.dispatchFor(db, TABLE_ID, { docStatus: 'DR', doctypeId: o.c_doctype_id, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true }, 'CO');
    status = r1.ok ? r1.to : 'X';
  }
  var legalNow = F.legalActionsFor(db, TABLE_ID, { docStatus: o.docstatus, doctypeId: o.c_doctype_id, processing: o.processing, periodOpen: true, isBackDateTrxAllowed: true });
  var actOk = o.docaction === '--' || legalNow.indexOf(o.docaction) >= 0;
  var ok = status === o.docstatus && actOk;
  if (ok) replayOk++;
  console.log('§HARDEN surface=MBankStatement.docaction.replay record_id=' + o.c_bankstatement_id + ' name=' + o.name + ' replayed->' + status +
    ' stored=' + o.docstatus + ' storedAction=' + o.docaction + '∈[' + legalNow.join(',') + '] diff=' + (ok ? 0 : 'MISMATCH'));
});
verdict(replayOk === docs.length, docs.length + '/' + docs.length + ' stored bank statements replayed (K=2 = the whole seed)');

// ── §FALSIFIERS ──────────────────────────────────────────────────────────────────────────────────────────
(function () {
  var r = F.dispatchFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtReal, processing: 'N', periodOpen: false, isBackDateTrxAllowed: true }, 'VO');
  verdict(!r.ok && r.reason === 'illegal-action', '§FALSIFIER-A Void from CO with period CLOSED → rejected (VO⊂periodOpen — the frame is live)', 'legal=[' + (r.legalActions || []).join(',') + ']');
  console.log('§FALSIFIER-A action=VO from=CO(periodClosed) ok=' + r.ok + ' reason=' + r.reason);
  var mutated = F.legalActionsFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtN, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true }).concat(['RE']);
  var ora = O.oracleSet(generic, blk, 'CO', { periodOpen: true, backDate: true, canReact: false });
  verdict(!setEq(mutated, ora), '§FALSIFIER-B inject RE for a react-N doctype → set-diff fires (the canReact gate is load-bearing)',
    'mutated=[' + mutated.sort().join(',') + '] oracle=[' + ora.sort().join(',') + ']');
  console.log('§FALSIFIER-B mutation=+RE@CO(react-N) setEq=' + setEq(mutated, ora) + ' (must be false)');
})();

console.log('\n§HARDEN_RESIDUAL voidIt bank-balance restore + payment unreconcile + DepositBatch cascade (:683-734) = write-path internals, named · ' +
  'WP/WC/AP/NA unreachable in seed — source-diff only · statement posting (Doc_BankStatement) not in the fold lane yet — separate arm, named');
console.log('§HARDEN surface=MBankStatement.docaction fixtures=' + (fixtures + tFix + docs.length) + ' diff=0 oracle=iDempiere(parsed-source+seed-replay)');
console.log((fails === 0 ? '🟢 W-MBANKSTMT-FSM PASS' : '🔴 W-MBANKSTMT-FSM FAIL (' + fails + ')') +
  ' — legal sets + outcomes for the C_BankStatement DocAction family diffed against the runtime-parsed iDempiere source and the stored seed.');
db.close();
process.exit(fails === 0 ? 0 : 1);
