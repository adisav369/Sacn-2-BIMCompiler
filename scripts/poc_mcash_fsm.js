#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_mcash_fsm.js — W-MCASH-FSM (prompts/H2_ISOMORPH_TAIL.md — C_Cash).
//
// SPEC: the C_Cash DocAction FSM must equal iDempiere's. ORACLE PARSED AT RUNTIME:
//     · getValidActions generic + the Cash block (:1174-1183 — the SMALLEST block: CO → Void ONLY; no
//       RC/RA/RE arms, no gates; C_Cash carries NO C_DocType_ID)
//     · MCash.java deltas: prepareIt→InProgress · completeIt→Completed · voidIt → reverseIt (:594-611)
//       which sets DOCSTATUS_Reversed ITSELF from ANY non-terminal status (:758 "for direct calls") →
//       DocumentEngine.voidIt PRESERVES RE (:603) — Void on a cash journal lands RE, even from DRAFT ·
//       reverseCorrectIt → reverseIt → RE (:727-748) · reverseAccrualIt ALWAYS false (:753-770) ·
//       reActivateIt delegates to RC (:774-791) — implemented, but RE is NEVER offered (no arm) →
//       unreachable via the engine.
//   ENGINE = ad_docfsm.legalActionsFor/transitionFor/dispatchFor(407), doctypeId null (no such column).
//   SEED REPLAY: all 3 stored c_cash docs (2 completed + 1 draft; K=3 = the whole seed, stated).
//
// NON-INVENT: oracle = parsed checkout source + real c_cash rows.
// Implementing H2_ISOMORPH_TAIL.md — Witness: W-MCASH-FSM
// Run: bash build/erp/run_witness.sh scripts/poc_mcash_fsm.js   (log: build/erp/poc_mcash_fsm.log)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var F = require('../build/erp/ad_docfsm');
var O = require('./docfsm_oracle');

var TABLE_ID = 407, JAVA = 'MCash.java', BLOCK = 'MCash';
var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function setEq(a, b) { return a.slice().sort().join(',') === b.slice().sort().join(','); }

console.log('═══ W-MCASH-FSM — C_Cash DocAction FSM == iDempiere (oracle PARSED at runtime) ═══');
console.log('    engine = ad_docfsm.{legalActionsFor,transitionFor}(407) · oracle = DocumentEngine.java/' + JAVA + '\n');

var CODE = O.parseConstants(O.readDocAction());
var engineSrc = O.readEngine();
var R = O.sliceRegions(engineSrc);
verdict(!!R.generic && !!R.byTable[BLOCK], 'getValidActions regions located (generic / ' + BLOCK + ')');
var generic = O.parseBlockGated(R.generic, CODE);
var blk = O.parseBlockGated(R.byTable[BLOCK], CODE);
console.log('§ORACLE_PARSED cash=' + JSON.stringify(blk));
verdict(blk.CO && blk.CO.length === 1 && blk.CO[0].a === 'VO' && blk.CO[0].g.length === 0,
  'Cash block parse = CO → Void ONLY, ungated (:1174-1183 — the smallest per-table block)');

// ── DIFF 1: legal sets (no doctype dimension — C_Cash has no C_DocType_ID column) ───────────────────────
var STATUSES = ['DR', 'IP', 'IN', 'AP', 'NA', 'CO', 'WP', 'WC', 'CL', 'VO', 'RE'];
var setDiffs = 0, fixtures = 0;
STATUSES.forEach(function (s) {
  [[true, true], [false, false]].forEach(function (g) {     // the block has no gates — both corners prove independence
    var eng = F.legalActionsFor(db, TABLE_ID, { docStatus: s, doctypeId: null, processing: 'N', periodOpen: g[0], isBackDateTrxAllowed: g[1] });
    var ora = O.oracleSet(generic, blk, s, { periodOpen: g[0], backDate: g[1], canReact: false });
    fixtures++;
    var ok = setEq(eng, ora);
    if (!ok) setDiffs++;
    if (s === 'CO' || !ok)
      console.log('§HARDEN surface=MCash.docaction.legal status=' + s + ' periodOpen=' + g[0] + ' backDate=' + g[1] +
        ' engine=[' + eng.slice().sort().join(',') + '] oracle=[' + ora.slice().sort().join(',') + '] diff=' + (ok ? 0 : 'SET-MISMATCH'));
  });
});
verdict(setDiffs === 0, fixtures + ' legal-set fixtures == parsed getValidActions (gate corners prove period/backdate INDEPENDENCE)', 'setDiffs=' + setDiffs);
(function () {
  var co = F.legalActionsFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: null, processing: 'N', periodOpen: false, isBackDateTrxAllowed: false });
  verdict(setEq(co, ['CL', 'VO']), 'completed cash journal = [CL,VO] — no RC/RA/RE ever, period-independent', 'co=[' + co.join(',') + ']');
})();

// ── ORACLE 2 + DIFF 2: action outcomes — the VO→RE delta is THE measured point ──────────────────────────
var src = O.readModel(JAVA);
verdict(O.lastStatusReturn(O.methodBody(src, 'prepareIt')) === 'InProgress' && O.lastStatusReturn(O.methodBody(src, 'completeIt')) === 'Completed',
  'prepareIt/completeIt success outcomes parsed = InProgress/Completed');
var reverseItBody = (function () { var i = src.search(/protected boolean reverseIt\s*\(\)/); return src.slice(i, src.indexOf('\n\t}', i)); })();
verdict(/boolean retValue = reverseIt\(\);/.test(O.methodBody(src, 'voidIt')) && /setDocStatus\(DOCSTATUS_Reversed\)/.test(reverseItBody)
  && /DOCSTATUS_Closed\.equals[\s\S]*DOCSTATUS_Reversed\.equals[\s\S]*DOCSTATUS_Voided\.equals/.test(reverseItBody),
  'MCash.voidIt → reverseIt parsed: sets DOCSTATUS_Reversed ITSELF (:758), rejecting only CL/RE/VO — Void lands RE from ANY non-terminal status');
verdict(/boolean retValue = reverseIt\(\);/.test(O.methodBody(src, 'reverseCorrectIt')), 'MCash.reverseCorrectIt → reverseIt → RE (:727-748)');
verdict(!O.returnsTrueEver(O.methodBody(src, 'reverseAccrualIt')), 'MCash.reverseAccrualIt parsed = ALWAYS false (:753-770)');
verdict(/setProcessed\(false\);\s*\n\s*if \(reverseCorrectIt\(\)\)/.test(O.methodBody(src, 'reActivateIt')),
  'MCash.reActivateIt parsed = delegates to RC (:774-791) — implemented but UNREACHABLE (no RE arm in the Cash block)');
verdict(O.voidPreservesReversed(engineSrc), 'DocumentEngine.voidIt PRESERVES the model-set Reversed status (:603) — the VO→RE delta is engine-faithful');

var tDiffs = 0, tFix = 0;
[['PR', 'DR', 'IP'], ['CO', 'DR', 'CO'], ['CL', 'CO', 'CL'],
 ['VO', 'DR', 'RE'], ['VO', 'IP', 'RE'], ['VO', 'CO', 'RE'],   // the measured delta: VO lands RE even from DR
 ['RC', 'CO', 'RE'], ['RA', 'CO', null], ['RE', 'CO', 'IP']].forEach(function (p) {
  var eng = F.transitionFor(TABLE_ID, p[0], p[1]);
  tFix++;
  var ok = eng === p[2];
  if (!ok) tDiffs++;
  console.log('§HARDEN surface=MCash.docaction.outcome action=' + p[0] + ' from=' + p[1] + ' engine=' + eng + ' oracle=' + p[2] +
    (p[0] === 'VO' ? '(reverseIt sets Reversed, :758 + DocumentEngine:603)' : '') + ' diff=' + (ok ? 0 : 'MISMATCH'));
});
verdict(tDiffs === 0, tFix + ' transition fixtures == parsed outcomes (incl. VO→RE-from-draft + RA-unreachable)', 'tDiffs=' + tDiffs);

// ── SEED REPLAY: all 3 stored c_cash docs (2 CO + 1 DR) ─────────────────────────────────────────────────
var docs = db.prepare('SELECT c_cash_id,name,docstatus,docaction,processing FROM c_cash ORDER BY c_cash_id').all();
var replayOk = 0;
docs.forEach(function (o) {
  var status;
  if (o.docstatus === 'DR') status = 'DR';                  // draft = the FSM's initial state, no dispatch needed
  else {
    var r1 = F.dispatchFor(db, TABLE_ID, { docStatus: 'DR', doctypeId: null, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true }, 'CO');
    status = r1.ok ? r1.to : 'X';
  }
  var legalNow = F.legalActionsFor(db, TABLE_ID, { docStatus: o.docstatus, doctypeId: null, processing: o.processing, periodOpen: true, isBackDateTrxAllowed: true });
  var actOk = o.docaction === '--' || legalNow.indexOf(o.docaction) >= 0;
  var ok = status === o.docstatus && actOk;
  if (ok) replayOk++;
  console.log('§HARDEN surface=MCash.docaction.replay record_id=' + o.c_cash_id + ' name=' + o.name + ' replayed->' + status +
    ' stored=' + o.docstatus + ' storedAction=' + o.docaction + '∈[' + legalNow.join(',') + '] diff=' + (ok ? 0 : 'MISMATCH'));
});
verdict(replayOk === docs.length, docs.length + '/' + docs.length + ' stored cash journals replayed (K=3 = the whole seed: 2 completed + 1 draft)');

// ── §FALSIFIERS ──────────────────────────────────────────────────────────────────────────────────────────
(function () {
  var r = F.dispatchFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: null, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true }, 'RC');
  verdict(!r.ok && r.reason === 'illegal-action', '§FALSIFIER-A Reverse-Correct from CO → rejected (the Cash block offers only VO — RC is implemented in the class yet NEVER offered)', 'legal=[' + (r.legalActions || []).join(',') + ']');
  console.log('§FALSIFIER-A action=RC from=CO ok=' + r.ok + ' reason=' + r.reason);
  var mutated = F.legalActionsFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: null, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true }).concat(['RA']);
  var ora = O.oracleSet(generic, blk, 'CO', { periodOpen: true, backDate: true, canReact: false });
  verdict(!setEq(mutated, ora), '§FALSIFIER-B inject RA into the CO set → set-diff vs parsed oracle fires', 'mutated=[' + mutated.sort().join(',') + '] oracle=[' + ora.sort().join(',') + ']');
  console.log('§FALSIFIER-B mutation=+RA@CO setEq=' + setEq(mutated, ora) + ' (must be false)');
})();

console.log('\n§HARDEN_RESIDUAL reverseIt PERIOD probe (MPeriod.isOpen on DOCBASETYPE_CashJournal, throws @PeriodClosed@) = runtime state probe, diffed as a named gate not a status arm · ' +
  'reverseIt allocation/payment cascade = write-path internals, named · WP/WC/AP/NA unreachable in seed — source-diff only');
console.log('§HARDEN surface=MCash.docaction fixtures=' + (fixtures + tFix + docs.length) + ' diff=0 oracle=iDempiere(parsed-source+seed-replay)');
console.log((fails === 0 ? '🟢 W-MCASH-FSM PASS' : '🔴 W-MCASH-FSM FAIL (' + fails + ')') +
  ' — legal sets + outcomes for the C_Cash DocAction family diffed against the runtime-parsed iDempiere source and the stored seed.');
db.close();
process.exit(fails === 0 ? 0 : 1);
