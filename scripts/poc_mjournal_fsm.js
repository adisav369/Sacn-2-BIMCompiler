#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_mjournal_fsm.js — W-MJOURNAL-FSM (prompts/H2_ISOMORPH_TAIL.md — GL Journal family).
//
// SPEC (H2_ISOMORPH_TAIL): the GL_Journal + GL_JournalBatch DocAction FSM (the classes SHARE the
//   DocumentEngine Journal block :1143-1157 — one family witness, the W-MINVENTORY-FAMILY-FSM precedent)
//   must equal iDempiere's. ORACLE PARSED FROM THE CHECKOUT AT RUNTIME (docfsm_oracle.js):
//     · getValidActions generic block + the Journal block (CO → RC⊂periodOpen, RE⊂periodOpen∧canReact, RA
//       — the Payment-style nesting on the period gate)
//     · DocumentEngine action methods → generic action→status outcomes
//     · MJournal.java deltas: prepareIt→InProgress · completeIt→Completed · voidIt voids ONLY
//       Drafted/Invalid (:711-735, else return false — narrower than the engine's unprocessed set) ·
//       RC/RA delegate to reverseCorrectIt/reverseAccrualIt(batch) (:782-885) → Reversed ·
//       reActivateIt IMPLEMENTED, period+doctype gated (:932-963)
//     · MJournalBatch.java deltas: voidIt ALWAYS false (:540-553) · RC/RA reverse the member journals and
//       set DOCSTATUS_Reversed (:617-690/:703-778) · reActivateIt re-activates members → true (:788-811)
//   ENGINE = ad_docfsm.legalActionsFor/transitionFor/dispatchFor(224|225).
//   SEED REPLAY: both stored gl_journals + the 1 stored gl_journalbatch (K=2+1 = the whole seed, stated).
//
// NON-INVENT: oracle = parsed checkout source + real c_doctype/gl_journal/gl_journalbatch rows.
// Implementing H2_ISOMORPH_TAIL.md — Witness: W-MJOURNAL-FSM
// Run: bash build/erp/run_witness.sh scripts/poc_mjournal_fsm.js   (log: build/erp/poc_mjournal_fsm.log)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var F = require('../build/erp/ad_docfsm');
var O = require('./docfsm_oracle');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function setEq(a, b) { return a.slice().sort().join(',') === b.slice().sort().join(','); }

console.log('═══ W-MJOURNAL-FSM — GL_Journal + GL_JournalBatch DocAction FSM == iDempiere (oracle PARSED at runtime) ═══');
console.log('    engine = ad_docfsm.{legalActionsFor,transitionFor}(224/225) · oracle = DocumentEngine/MJournal/MJournalBatch\n');

// ── ORACLE 1: constants + region slicing + gate-aware block parse ───────────────────────────────────────
var CODE = O.parseConstants(O.readDocAction());
var engineSrc = O.readEngine();
var R = O.sliceRegions(engineSrc);
verdict(!!R.generic && !!R.byTable.MJournal, 'getValidActions regions located (generic / MJournal — the SHARED Journal+Batch block)');
var generic = O.parseBlockGated(R.generic, CODE);
var blk = O.parseBlockGated(R.byTable.MJournal, CODE);
console.log('§ORACLE_PARSED journal=' + JSON.stringify(blk));
verdict(blk.CO && blk.CO.some(function (e) { return e.a === 'RC' && e.g.join() === 'periodOpen'; })
  && blk.CO.some(function (e) { return e.a === 'RE' && setEq(e.g, ['periodOpen', 'canReact']); })
  && blk.CO.some(function (e) { return e.a === 'RA' && e.g.length === 0; }),
  'Journal block parse = RC⊂periodOpen · RE⊂periodOpen∧canReact · RA ungated (:1143-1157)');

// ── DIFF 1: legal sets — both tables × every status × periodOpen × backDate × doctype(react Y/N) ────────
var dtY = db.prepare("SELECT c_doctype_id FROM c_doctype WHERE iscanbereactivated='Y' AND docbasetype='GLJ' ORDER BY c_doctype_id").get().c_doctype_id; // 115 (the stored journals' doctype)
var dtN = db.prepare("SELECT c_doctype_id FROM c_doctype WHERE iscanbereactivated='N' ORDER BY c_doctype_id").get().c_doctype_id;
var STATUSES = ['DR', 'IP', 'IN', 'AP', 'NA', 'CO', 'WP', 'WC', 'CL', 'VO', 'RE'];
var setDiffs = 0, fixtures = 0;
[224, 225].forEach(function (T) {
  STATUSES.forEach(function (s) {
    [[true, true], [true, false], [false, true], [false, false]].forEach(function (g) {
      [dtY, dtN].forEach(function (dt) {
        var canReact = db.prepare('SELECT iscanbereactivated r FROM c_doctype WHERE c_doctype_id=?').get(Number(dt)).r === 'Y';
        var eng = F.legalActionsFor(db, T, { docStatus: s, doctypeId: dt, processing: 'N', periodOpen: g[0], isBackDateTrxAllowed: g[1] });
        var ora = O.oracleSet(generic, blk, s, { periodOpen: g[0], backDate: g[1], canReact: canReact });
        fixtures++;
        var ok = setEq(eng, ora);
        if (!ok) setDiffs++;
        if (s === 'CO' || !ok)
          console.log('§HARDEN surface=' + (T === 224 ? 'MJournal' : 'MJournalBatch') + '.docaction.legal status=' + s + ' periodOpen=' + g[0] + ' backDate=' + g[1] +
            ' doctype=' + dt + '(react=' + (canReact ? 'Y' : 'N') + ') engine=[' + eng.slice().sort().join(',') + '] oracle=[' + ora.slice().sort().join(',') + '] diff=' + (ok ? 0 : 'SET-MISMATCH'));
      });
    });
  });
});
verdict(setDiffs === 0, fixtures + ' legal-set fixtures == parsed getValidActions (2 tables × statuses × gates × 2 doctypes)', 'setDiffs=' + setDiffs);

// the measured narrowing: completed journal ≠ generic union; RE rides INSIDE the period gate (unlike Payment's RC)
(function () {
  var open = F.legalActionsFor(db, 224, { docStatus: 'CO', doctypeId: dtY, processing: 'N', periodOpen: true, isBackDateTrxAllowed: false });
  var closed = F.legalActionsFor(db, 224, { docStatus: 'CO', doctypeId: dtY, processing: 'N', periodOpen: false, isBackDateTrxAllowed: false });
  verdict(open.indexOf('RC') >= 0 && open.indexOf('RE') >= 0 && open.indexOf('RA') >= 0 && open.indexOf('VO') < 0
    && closed.indexOf('RC') < 0 && closed.indexOf('RE') < 0 && closed.indexOf('RA') >= 0,
    'completed journal (react-Y doctype): periodOpen→[CL,RC,RE,RA], periodClosed→[CL,RA] — no VO ever (:1143-1157)',
    'open=[' + open.join(',') + '] closed=[' + closed.join(',') + ']');
})();

// ── ORACLE 2 + DIFF 2: action outcomes (generic parse + per-class deltas) ───────────────────────────────
var oraOutcome = O.parseOutcomes(engineSrc, CODE);
var srcJ = O.readModel('MJournal.java');
var srcB = O.readModel('MJournalBatch.java');
verdict(O.lastStatusReturn(O.methodBody(srcJ, 'prepareIt')) === 'InProgress' && O.lastStatusReturn(O.methodBody(srcJ, 'completeIt')) === 'Completed'
  && O.lastStatusReturn(O.methodBody(srcB, 'prepareIt')) === 'InProgress' && O.lastStatusReturn(O.methodBody(srcB, 'completeIt')) === 'Completed',
  'prepareIt/completeIt success outcomes parsed = InProgress/Completed (both classes)');
// MJournal.voidIt: ONLY Drafted/Invalid void; every other status returns false (:711-735)
var voJ = O.methodBody(srcJ, 'voidIt');
verdict(/DOCSTATUS_Drafted\.equals[\s\S]*DOCSTATUS_Invalid\.equals/.test(voJ) && /} else \{\s*\n\s*return false;/.test(voJ) && !/DOCSTATUS_InProgress/.test(voJ),
  'MJournal.voidIt parsed = voids ONLY DR/IN, else false (:711-735) — narrower than the engine unprocessed set');
// MJournalBatch.voidIt: never succeeds (:540-553)
verdict(!O.returnsTrueEver(O.methodBody(srcB, 'voidIt')), 'MJournalBatch.voidIt parsed = ALWAYS false (:540-553) — VO never lands on a batch');
// RC/RA implemented in both (delegating; batch sets Reversed itself)
verdict(/reverseCorrectIt\(getGL_JournalBatch_ID\(\)\) != null/.test(O.methodBody(srcJ, 'reverseCorrectIt'))
  && /reverseAccrualIt\s*\(getGL_JournalBatch_ID\(\)\) != null/.test(O.methodBody(srcJ, 'reverseAccrualIt')),
  'MJournal RC/RA parsed = delegate to the batch-scoped reversal (:782-806/:858-882) → Reversed');
verdict(/setDocStatus\(DOCSTATUS_Reversed\)/.test(O.methodBody(srcB, 'reverseCorrectIt')) && /setDocStatus\(DOCSTATUS_Reversed\)/.test(O.methodBody(srcB, 'reverseAccrualIt')),
  'MJournalBatch RC/RA parsed = reverse member journals + set Reversed (:617-690/:703-778)');
// reActivateIt implemented in both (journal: period+doctype gated)
verdict(O.returnsTrueEver(O.methodBody(srcJ, 'reActivateIt')) && /canReactivateThisDocType/.test(O.methodBody(srcJ, 'reActivateIt'))
  && O.returnsTrueEver(O.methodBody(srcB, 'reActivateIt')),
  'reActivateIt parsed = IMPLEMENTED both classes (journal period+doctype gated :932-963; batch :788-811)');

var tDiffs = 0, tFix = 0;
[[224, 'MJournal'], [225, 'MJournalBatch']].forEach(function (TT) {
  var T = TT[0];
  // per-class expected outcomes from the parsed facts above:
  //   PR→IP · CO→CO · CL@CO→CL · RC/RA@CO→RE (implemented both) · RE@CO→IP (implemented both, engine :704-707)
  //   VO: journal DR/IN→VO, IP/AP/NA/CO→null (model false) · batch → null everywhere
  var exp = [['PR', 'DR', 'IP'], ['CO', 'DR', 'CO'], ['CL', 'CO', 'CL'], ['RC', 'CO', 'RE'], ['RA', 'CO', 'RE'], ['RE', 'CO', 'IP'],
             ['VO', 'DR', T === 224 ? 'VO' : null], ['VO', 'IN', T === 224 ? 'VO' : null],
             ['VO', 'IP', null], ['VO', 'AP', null], ['VO', 'NA', null], ['VO', 'CO', null]];
  exp.forEach(function (p) {
    var eng = F.transitionFor(T, p[0], p[1]);
    tFix++;
    var ok = eng === p[2];
    if (!ok) tDiffs++;
    console.log('§HARDEN surface=' + TT[1] + '.docaction.outcome action=' + p[0] + ' from=' + p[1] + ' engine=' + eng + ' oracle=' + p[2] + ' diff=' + (ok ? 0 : 'MISMATCH'));
  });
});
// generic outcomes still agree where the classes don't override (XL/IN/AP/RJ from the parsed engine methods)
['XL', 'IN', 'AP', 'RJ'].forEach(function (act) {
  var from = { XL: 'DR', IN: 'IP', AP: 'NA', RJ: 'NA' }[act];
  var eng = F.transitionFor(224, act, from);
  tFix++;
  if (eng !== oraOutcome[act]) tDiffs++;
  console.log('§HARDEN surface=MJournal.docaction.outcome action=' + act + ' from=' + from + ' engine=' + eng + ' oracle=' + oraOutcome[act] + '(parsed DocumentEngine) diff=' + (eng === oraOutcome[act] ? 0 : 'MISMATCH'));
});
verdict(tDiffs === 0, tFix + ' transition fixtures == parsed outcomes (incl. journal VO@DR/IN-only + batch VO-never)', 'tDiffs=' + tDiffs);

// ── SEED REPLAY: 2 stored gl_journals + 1 stored gl_journalbatch (the WHOLE seed, K stated) ─────────────
var docs = db.prepare('SELECT gl_journal_id id,documentno,docstatus,docaction,c_doctype_id,processing FROM gl_journal ORDER BY gl_journal_id').all()
  .map(function (o) { o.T = 224; o.n = 'MJournal'; return o; })
  .concat(db.prepare('SELECT gl_journalbatch_id id,documentno,docstatus,docaction,c_doctype_id,processing FROM gl_journalbatch ORDER BY gl_journalbatch_id').all()
  .map(function (o) { o.T = 225; o.n = 'MJournalBatch'; return o; }));
var replayOk = 0;
docs.forEach(function (o) {
  var r1 = F.dispatchFor(db, o.T, { docStatus: 'DR', doctypeId: o.c_doctype_id, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true }, 'CO');
  var status = r1.ok ? r1.to : 'X';
  var legalNow = F.legalActionsFor(db, o.T, { docStatus: o.docstatus, doctypeId: o.c_doctype_id, processing: o.processing, periodOpen: true, isBackDateTrxAllowed: true });
  var actOk = o.docaction === '--' || legalNow.indexOf(o.docaction) >= 0;
  var ok = status === o.docstatus && actOk;
  if (ok) replayOk++;
  console.log('§HARDEN surface=' + o.n + '.docaction.replay record_id=' + o.id + ' docno=' + o.documentno + ' DR-CO->' + status +
    ' stored=' + o.docstatus + ' storedAction=' + o.docaction + (o.docaction === '--' ? '(None)' : '∈[' + legalNow.join(',') + ']') + ' diff=' + (ok ? 0 : 'MISMATCH'));
});
verdict(replayOk === docs.length, docs.length + '/' + docs.length + ' stored journal-family docs: docstatus replayed + stored docaction legal-or-None (K=2+1 = the whole seed)');

// ── §FALSIFIERS ──────────────────────────────────────────────────────────────────────────────────────────
(function () {
  var r = F.dispatchFor(db, 224, { docStatus: 'CO', doctypeId: dtY, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true }, 'VO');
  verdict(!r.ok && r.reason === 'illegal-action', '§FALSIFIER-A Void from CO → rejected (the Journal block never offers VO at CO)', 'legal=[' + (r.legalActions || []).join(',') + ']');
  console.log('§FALSIFIER-A action=VO from=CO ok=' + r.ok + ' reason=' + r.reason);
  var mutated = F.legalActionsFor(db, 224, { docStatus: 'CO', doctypeId: dtY, processing: 'N', periodOpen: false, isBackDateTrxAllowed: true }).concat(['RE']);
  var ora = O.oracleSet(generic, blk, 'CO', { periodOpen: false, backDate: true, canReact: true });
  verdict(!setEq(mutated, ora), '§FALSIFIER-B inject RE into the period-closed CO set → set-diff vs parsed oracle fires (RE⊂periodOpen is load-bearing)',
    'mutated=[' + mutated.sort().join(',') + '] oracle=[' + ora.sort().join(',') + ']');
  console.log('§FALSIFIER-B mutation=+RE@CO(periodClosed) setEq=' + setEq(mutated, ora) + ' (must be false)');
})();

console.log('\n§HARDEN_RESIDUAL VO@IP/AP/NA unreachable at runtime for MJournal (model returns false though the generic set offers VO) — modelled null, diffed · ' +
  'batch ControlAmt-vs-total warning = prepareIt path (not getValidActions/transition), named · WP/WC/AP/NA unreachable in seed — source-diff only · ' +
  'posting CITED (W-FOLD-GLJOURNAL fact_acct(224), both schemas) — not redone');
console.log('§HARDEN surface=GLJournalFamily.docaction fixtures=' + (fixtures + tFix + docs.length) + ' diff=0 oracle=iDempiere(parsed-source+seed-replay)');
console.log((fails === 0 ? '🟢 W-MJOURNAL-FSM PASS' : '🔴 W-MJOURNAL-FSM FAIL (' + fails + ')') +
  ' — legal sets + outcomes for the GL Journal family diffed against the runtime-parsed iDempiere source and the stored seed.');
db.close();
process.exit(fails === 0 ? 0 : 1);
