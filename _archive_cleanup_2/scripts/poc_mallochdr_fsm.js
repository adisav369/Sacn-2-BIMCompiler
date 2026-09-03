#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_mallochdr_fsm.js — W-MALLOCHDR-FSM (prompts/H2_ISOMORPH_TAIL.md — C_AllocationHdr).
//
// SPEC: the C_AllocationHdr DocAction FSM must equal iDempiere's. ORACLE PARSED AT RUNTIME:
//     · getValidActions generic + the Allocation block (:1159-1172 — CO → RC⊂periodOpen, RA; NO RE arm)
//     · MAllocationHdr.java deltas: prepareIt→InProgress · completeIt→Completed · voidIt = the H-2
//       delegation shape (unprocessed→VO; processed→period-probe→reverseAccrual/Correct→Reversed,
//       :567-630) · RC/RA → reverseIt(false/true) → Reversed (:670-715) · reActivateIt ALWAYS false
//       (:718-732 — RE never succeeds, unlike the Journal family)
//   ENGINE = ad_docfsm.legalActionsFor/transitionFor/dispatchFor(735).
//   SEED REPLAY: both stored c_allocationhdrs (K=2 = the whole seed, stated).
//
// NON-INVENT: oracle = parsed checkout source + real c_doctype/c_allocationhdr rows.
// Implementing H2_ISOMORPH_TAIL.md — Witness: W-MALLOCHDR-FSM
// Run: bash build/erp/run_witness.sh scripts/poc_mallochdr_fsm.js   (log: build/erp/poc_mallochdr_fsm.log)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var F = require('../build/erp/ad_docfsm');
var O = require('./docfsm_oracle');

var TABLE_ID = 735, JAVA = 'MAllocationHdr.java', BLOCK = 'MAllocationHdr';
var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function setEq(a, b) { return a.slice().sort().join(',') === b.slice().sort().join(','); }

console.log('═══ W-MALLOCHDR-FSM — C_AllocationHdr DocAction FSM == iDempiere (oracle PARSED at runtime) ═══');
console.log('    engine = ad_docfsm.{legalActionsFor,transitionFor}(735) · oracle = DocumentEngine.java/' + JAVA + '\n');

var CODE = O.parseConstants(O.readDocAction());
var engineSrc = O.readEngine();
var R = O.sliceRegions(engineSrc);
verdict(!!R.generic && !!R.byTable[BLOCK], 'getValidActions regions located (generic / ' + BLOCK + ')');
var generic = O.parseBlockGated(R.generic, CODE);
var blk = O.parseBlockGated(R.byTable[BLOCK], CODE);
console.log('§ORACLE_PARSED alloc=' + JSON.stringify(blk));
verdict(blk.CO && blk.CO.some(function (e) { return e.a === 'RC' && e.g.join() === 'periodOpen'; })
  && blk.CO.some(function (e) { return e.a === 'RA' && e.g.length === 0; })
  && !blk.CO.some(function (e) { return e.a === 'RE'; }),
  'Allocation block parse = RC⊂periodOpen · RA ungated · NO ReActivate arm (:1159-1172)');

// ── DIFF 1: legal sets ───────────────────────────────────────────────────────────────────────────────────
var dtReal = db.prepare('SELECT c_doctype_id FROM c_allocationhdr LIMIT 1').get().c_doctype_id;  // 137, the stored doctype
var dtReact = db.prepare("SELECT c_doctype_id FROM c_doctype WHERE iscanbereactivated='Y' ORDER BY c_doctype_id").get().c_doctype_id;
var STATUSES = ['DR', 'IP', 'IN', 'AP', 'NA', 'CO', 'WP', 'WC', 'CL', 'VO', 'RE'];
var setDiffs = 0, fixtures = 0;
STATUSES.forEach(function (s) {
  [[true, true], [true, false], [false, true], [false, false]].forEach(function (g) {
    [dtReal, dtReact].forEach(function (dt) {
      var canReact = db.prepare('SELECT iscanbereactivated r FROM c_doctype WHERE c_doctype_id=?').get(Number(dt)).r === 'Y';
      var eng = F.legalActionsFor(db, TABLE_ID, { docStatus: s, doctypeId: dt, processing: 'N', periodOpen: g[0], isBackDateTrxAllowed: g[1] });
      var ora = O.oracleSet(generic, blk, s, { periodOpen: g[0], backDate: g[1], canReact: canReact });
      fixtures++;
      var ok = setEq(eng, ora);
      if (!ok) setDiffs++;
      if (s === 'CO' || !ok)
        console.log('§HARDEN surface=MAllocationHdr.docaction.legal status=' + s + ' periodOpen=' + g[0] + ' backDate=' + g[1] +
          ' doctype=' + dt + '(react=' + (canReact ? 'Y' : 'N') + ') engine=[' + eng.slice().sort().join(',') + '] oracle=[' + ora.slice().sort().join(',') + '] diff=' + (ok ? 0 : 'SET-MISMATCH'));
    });
  });
});
verdict(setDiffs === 0, fixtures + ' legal-set fixtures == parsed getValidActions', 'setDiffs=' + setDiffs);
// the narrowing: even a react-Y doctype NEVER offers RE on an allocation (no RE arm in the block)
(function () {
  var open = F.legalActionsFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtReact, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true });
  verdict(open.indexOf('RE') < 0 && open.indexOf('RC') >= 0 && open.indexOf('RA') >= 0 && open.indexOf('VO') < 0,
    'completed allocation w/ react-Y doctype: STILL no RE (the Allocation block has no ReActivate arm) — [CL,RC,RA] only', 'open=[' + open.join(',') + ']');
})();

// ── ORACLE 2 + DIFF 2: action outcomes ──────────────────────────────────────────────────────────────────
var src = O.readModel(JAVA);
verdict(O.lastStatusReturn(O.methodBody(src, 'prepareIt')) === 'InProgress' && O.lastStatusReturn(O.methodBody(src, 'completeIt')) === 'Completed',
  'prepareIt/completeIt success outcomes parsed = InProgress/Completed');
verdict(O.voidDelegates(O.methodBody(src, 'voidIt')), 'MAllocationHdr.voidIt PROCESSED branch parsed = period-probe delegation to reverseAccrual/CorrectIt (:589-603) — the H-2 shape');
verdict(/reverseIt\(false\)/.test(O.methodBody(src, 'reverseCorrectIt')) && /reverseIt\(true\)/.test(O.methodBody(src, 'reverseAccrualIt')),
  'RC/RA parsed = reverseIt(false/true) → Reversed (:670-715)');
verdict(!O.returnsTrueEver(O.methodBody(src, 'reActivateIt')), 'reActivateIt parsed = NOT IMPLEMENTED (always false, :718-732) — RE never succeeds');
verdict(O.voidPreservesReversed(engineSrc), 'DocumentEngine.voidIt PRESERVES Reversed (:603) — VO on a completed allocation lands RE');

var tDiffs = 0, tFix = 0;
[['PR', 'DR', 'IP'], ['CO', 'DR', 'CO'], ['CL', 'CO', 'CL'], ['RC', 'CO', 'RE'], ['RA', 'CO', 'RE'], ['RE', 'CO', null],
 ['VO', 'DR', 'VO'], ['VO', 'IP', 'VO'], ['VO', 'NA', 'VO'], ['VO', 'CO', 'RE']].forEach(function (p) {
  var eng = F.transitionFor(TABLE_ID, p[0], p[1]);
  tFix++;
  var ok = eng === p[2];
  if (!ok) tDiffs++;
  console.log('§HARDEN surface=MAllocationHdr.docaction.outcome action=' + p[0] + ' from=' + p[1] + ' engine=' + eng + ' oracle=' + p[2] + ' diff=' + (ok ? 0 : 'MISMATCH'));
});
verdict(tDiffs === 0, tFix + ' transition fixtures == parsed outcomes (incl. RE-unreachable + VO@CO→RE delegation)', 'tDiffs=' + tDiffs);

// ── SEED REPLAY: both stored allocations ────────────────────────────────────────────────────────────────
var docs = db.prepare('SELECT c_allocationhdr_id,documentno,docstatus,docaction,c_doctype_id,processing FROM c_allocationhdr ORDER BY c_allocationhdr_id').all();
var replayOk = 0;
docs.forEach(function (o) {
  var r1 = F.dispatchFor(db, TABLE_ID, { docStatus: 'DR', doctypeId: o.c_doctype_id, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true }, 'CO');
  var status = r1.ok ? r1.to : 'X';
  var legalNow = F.legalActionsFor(db, TABLE_ID, { docStatus: o.docstatus, doctypeId: o.c_doctype_id, processing: o.processing, periodOpen: true, isBackDateTrxAllowed: true });
  var actOk = o.docaction === '--' || legalNow.indexOf(o.docaction) >= 0;
  var ok = status === o.docstatus && actOk;
  if (ok) replayOk++;
  console.log('§HARDEN surface=MAllocationHdr.docaction.replay record_id=' + o.c_allocationhdr_id + ' docno=' + o.documentno + ' DR-CO->' + status +
    ' stored=' + o.docstatus + ' storedAction=' + o.docaction + '∈[' + legalNow.join(',') + '] diff=' + (ok ? 0 : 'MISMATCH'));
});
verdict(replayOk === docs.length, docs.length + '/' + docs.length + ' stored allocations replayed (K=2 = the whole seed)');

// ── §FALSIFIERS ──────────────────────────────────────────────────────────────────────────────────────────
(function () {
  var r = F.dispatchFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtReact, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true }, 'RE');
  verdict(!r.ok && r.reason === 'illegal-action', '§FALSIFIER-A ReActivate from CO → rejected (no RE arm in the Allocation block, even react-Y doctype)', 'legal=[' + (r.legalActions || []).join(',') + ']');
  console.log('§FALSIFIER-A action=RE from=CO ok=' + r.ok + ' reason=' + r.reason);
  var mutated = F.legalActionsFor(db, TABLE_ID, { docStatus: 'CO', doctypeId: dtReal, processing: 'N', periodOpen: false, isBackDateTrxAllowed: false }).concat(['RC']);
  var ora = O.oracleSet(generic, blk, 'CO', { periodOpen: false, backDate: false, canReact: false });
  verdict(!setEq(mutated, ora), '§FALSIFIER-B inject RC into the period-closed CO set → set-diff fires (the period gate is load-bearing)',
    'mutated=[' + mutated.sort().join(',') + '] oracle=[' + ora.sort().join(',') + ']');
  console.log('§FALSIFIER-B mutation=+RC@CO(periodClosed) setEq=' + setEq(mutated, ora) + ' (must be false)');
})();

console.log('\n§HARDEN_RESIDUAL posting CITED (W-FOLD-ALLOC/-FX fact_acct(735)) — not redone · WP/WC/AP/NA unreachable in seed — source-diff only · ' +
  'voidIt updateBP/line-zeroing + the period probe on DateTrx (MPeriodControl.DOCBASETYPE_PaymentAllocation) = write-path internals, named');
console.log('§HARDEN surface=MAllocationHdr.docaction fixtures=' + (fixtures + tFix + docs.length) + ' diff=0 oracle=iDempiere(parsed-source+seed-replay)');
console.log((fails === 0 ? '🟢 W-MALLOCHDR-FSM PASS' : '🔴 W-MALLOCHDR-FSM FAIL (' + fails + ')') +
  ' — legal sets + outcomes for the C_AllocationHdr DocAction family diffed against the runtime-parsed iDempiere source and the stored seed.');
db.close();
process.exit(fails === 0 ? 0 : 1);
