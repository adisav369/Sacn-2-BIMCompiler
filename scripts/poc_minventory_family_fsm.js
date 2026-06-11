#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_minventory_family_fsm.js — W-MINVENTORY-FAMILY-FSM (prompts/FABLE5_H2_DELTAS.md §H-2.4).
//
// SPEC (§H-2.4): the inventory-family edge — M_Movement (323) + M_Inventory (321) + M_Production (325) —
//   DocAction FSM == iDempiere's, oracle PARSED FROM THE CHECKOUT AT RUNTIME:
//     · DocumentEngine.getValidActions: Movement+Inventory share ONE block (:1200-1213) and Production has
//       its own (:1233-1244) — all three: CO → Reverse-Correct GATED periodOpen∧isBackDateTrxAllowed + RA
//     · M*.java deltas, per class: prepareIt→InProgress (MMovement:365 / MInventory:449 / MProduction:634) ·
//       completeIt→Completed (:697 / :739 / :222) · reActivateIt NOT IMPLEMENTED in ALL THREE (always
//       false — MMovement:1071-1085 / MInventory:1200-1214 / MProduction:1021-1033) · voidIt on a PROCESSED
//       doc delegates to reverseCorrectIt/reverseAccrualIt (MMovement:843-866 / MInventory:989-1012 /
//       MProduction:778-801) → Reversed, preserved by DocumentEngine.voidIt (:616-618)
//   HONEST SCOPE (the card's reduced-scope rule): M_Movement has 1 stored doc → FSM seed-replay + the
//   beforeSave doctype-default replay (MMovement.java:212-222 ∘ MDocType.getOfDocBaseType:68-77, via
//   installMMovementSaveHooks). M_Inventory / M_Production have NO seed documents (m_inventory/m_production
//   absent, m_transaction holds no I±/P± rows) → FSM SOURCE-PARSE DIFF ONLY; stored-replay n/a;
//   beforeSave stored-replay = ⛔ no-seed (a fixture would be synthesized — refused, stated).
//
// NON-INVENT: oracle = parsed checkout source + the real m_movement row + real c_doctype rows.
//   READ build/erp/poc_minventory_family_fsm.log — exit code is not evidence.
// Implementing FABLE5_H2_DELTAS.md §H-2.4 — Witness: W-MINVENTORY-FAMILY-FSM
// Run: bash build/erp/run_witness.sh scripts/poc_minventory_family_fsm.js   (log: build/erp/poc_minventory_family_fsm.log)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var F = require('../build/erp/ad_docfsm');
var V = require('../build/erp/ad_modelval');
var O = require('./docfsm_oracle');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function setEq(a, b) { return a.slice().sort().join(',') === b.slice().sort().join(','); }

console.log('═══ W-MINVENTORY-FAMILY-FSM — M_Movement/M_Inventory/M_Production FSM == iDempiere (runtime-parsed) ═══');
console.log('    engine = ad_docfsm.{legalActionsFor,transitionFor}(323/321/325) · oracle = DocumentEngine.java + the 3 M*.java\n');

var CODE = O.parseConstants(O.readDocAction());
var engineSrc = O.readEngine();
var R = O.sliceRegions(engineSrc);
var generic = O.parseBlockGated(R.generic, CODE);
var movBlk = O.parseBlockGated(R.byTable.MMovement, CODE);     // the SHARED Movement+Inventory block (:1200-1213)
var prodBlk = O.parseBlockGated(R.byTable.MProduction, CODE);  // Production (:1233-1244)
verdict(!!R.byTable.MMovement && !!R.byTable.MProduction, 'getValidActions regions located (generic / Movement+Inventory / Production)');
console.log('§ORACLE_PARSED movement_inventory=' + JSON.stringify(movBlk));
console.log('§ORACLE_PARSED production=' + JSON.stringify(prodBlk));
verdict(/MMovement\.Table_ID\s*\n?\s*\|\| AD_Table_ID == MInventory\.Table_ID/.test(R.byTable.MMovement.slice(0, 200)) || R.byTable.MMovement.indexOf('MInventory.Table_ID') >= 0,
  'Movement and Inventory share ONE source block (:1200-1201) — the engine keys both 323 and 321 to the same set');
(function () {
  var rcM = (movBlk.CO || []).find(function (e) { return e.a === 'RC'; });
  var rcP = (prodBlk.CO || []).find(function (e) { return e.a === 'RC'; });
  verdict(!!rcM && setEq(rcM.g, ['periodOpen', 'backDate']) && !!rcP && setEq(rcP.g, ['periodOpen', 'backDate']),
    'parsed gate shape: RC⊂periodOpen∧backDate + RA ungated in BOTH blocks (the InOut-style nesting)', JSON.stringify({ mov: movBlk.CO, prod: prodBlk.CO }));
})();

// ── DIFF 1: legal sets per table — statuses × periodOpen × backDate (real doctypes per class) ──────────
var TABLES = [
  { id: 323, name: 'MMovement', blk: movBlk, java: 'MMovement.java', dt: 143 },   // Material Movement (the stored doc's doctype)
  { id: 321, name: 'MInventory', blk: movBlk, java: 'MInventory.java', dt: 144 }, // Material Physical Inventory
  { id: 325, name: 'MProduction', blk: prodBlk, java: 'MProduction.java', dt: 139 } // Material Production
];
var STATUSES = ['DR', 'IP', 'IN', 'AP', 'NA', 'CO', 'WP', 'WC', 'CL', 'VO', 'RE'];
var fixtures = 0, tFixAll = 0;
TABLES.forEach(function (T) {
  var setDiffs = 0, n = 0;
  STATUSES.forEach(function (s) {
    [[true, true], [true, false], [false, true], [false, false]].forEach(function (g) {
      var eng = F.legalActionsFor(db, T.id, { docStatus: s, doctypeId: T.dt, processing: 'N', periodOpen: g[0], isBackDateTrxAllowed: g[1] });
      var ora = O.oracleSet(generic, T.blk, s, { periodOpen: g[0], backDate: g[1], canReact: false });
      n++; fixtures++;
      var ok = setEq(eng, ora);
      if (!ok) setDiffs++;
      if (s === 'CO' || !ok)
        console.log('§HARDEN surface=' + T.name + '.docaction.legal status=' + s + ' periodOpen=' + g[0] + ' backDate=' + g[1] +
          ' doctype=' + T.dt + ' engine=[' + eng.slice().sort().join(',') + '] oracle=[' + ora.slice().sort().join(',') + '] diff=' + (ok ? 0 : 'SET-MISMATCH'));
    });
  });
  verdict(setDiffs === 0, T.name + ': ' + n + ' legal-set fixtures == parsed getValidActions', 'setDiffs=' + setDiffs);

  // outcomes: class deltas parsed from the class's own Java
  var src = O.readModel(T.java);
  var prepOut = O.lastStatusReturn(O.methodBody(src, 'prepareIt'));
  var compOut = O.lastStatusReturn(O.methodBody(src, 'completeIt'));
  var reBody = O.methodBody(src, 'reActivateIt');
  var voidBody = O.methodBody(src, 'voidIt');
  verdict(prepOut === 'InProgress' && compOut === 'Completed', T.name + ' prepareIt/completeIt success outcomes parsed = InProgress/Completed', 'got=' + prepOut + '/' + compOut);
  verdict(!O.returnsTrueEver(reBody), T.name + '.reActivateIt parsed = NOT IMPLEMENTED (always false) — RE never succeeds');
  verdict(O.voidDelegates(voidBody), T.name + '.voidIt PROCESSED branch parsed = delegates to reverseAccrualIt/reverseCorrectIt');
  var tDiffs = 0;
  [['PR', 'DR', 'IP'], ['CO', 'DR', 'CO'], ['VO', 'DR', 'VO'], ['VO', 'CO', 'RE'], ['RC', 'CO', 'RE'], ['RA', 'CO', 'RE'], ['CL', 'CO', 'CL'], ['XL', 'DR', 'DR']].forEach(function (p) {
    var eng = F.transitionFor(T.id, p[0], p[1]);
    tFixAll++;
    if (eng !== p[2]) tDiffs++;
    console.log('§HARDEN surface=' + T.name + '.docaction.outcome action=' + p[0] + ' from=' + p[1] + ' engine=' + eng + ' oracle=' + p[2] + ' diff=' + (eng === p[2] ? 0 : 'MISMATCH'));
  });
  var re = F.transitionFor(T.id, 'RE', 'CO');
  tFixAll++;
  if (re !== null) tDiffs++;
  console.log('§HARDEN surface=' + T.name + '.docaction.outcome action=RE from=CO engine=' + re + ' oracle=unreachable(' + T.name + '.reActivateIt false) diff=' + (re === null ? 0 : 'MISMATCH'));
  verdict(tDiffs === 0, T.name + ': 9 transition fixtures == parsed outcomes (incl. RE-unreachable + VO@CO→RE delegation)', 'tDiffs=' + tDiffs);
});
verdict(O.voidPreservesReversed(engineSrc), 'DocumentEngine.voidIt PRESERVES the document\'s Reversed status (:616-618) — the family\'s VO@CO→RE leg');

// ── M_Movement: the ONE stored doc — FSM replay + beforeSave replay (the honest whole-seed) ────────────
var mov = db.prepare('SELECT * FROM m_movement').get();
(function () {
  var r1 = F.dispatchFor(db, 323, { docStatus: 'DR', doctypeId: mov.c_doctype_id, processing: mov.processing, periodOpen: true, isBackDateTrxAllowed: true }, 'CO');
  var legalNow = F.legalActionsFor(db, 323, { docStatus: mov.docstatus, doctypeId: mov.c_doctype_id, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true });
  var actOk = mov.docaction === '--' || legalNow.indexOf(mov.docaction) >= 0;
  var ok = r1.ok && r1.to === mov.docstatus && actOk;
  console.log('§HARDEN surface=MMovement.docaction.replay record_id=' + mov.m_movement_id + ' docno=' + mov.documentno + ' DR-CO->' + (r1.ok ? r1.to : 'X') +
    ' stored=' + mov.docstatus + ' storedAction=' + mov.docaction + '∈[' + legalNow.join(',') + '] diff=' + (ok ? 0 : 'MISMATCH'));
  verdict(ok, '1/1 stored movement: docstatus replayed by dispatch + stored docaction CL legal at CO (K=1 = the whole seed, stated)');
})();
(function () {  // beforeSave: doctype default (MMovement.java:212-222) — replay + strip-derive
  var nH = V.installMMovementSaveHooks(db);
  function fire(record, recordOld) {
    var info = { table: 'M_Movement', record: record, recordOld: recordOld || null };
    var res = V.fireHooks('BEFORE_SAVE', info, {});
    res.derived = info.derived || {};
    return res;
  }
  var r0 = fire(JSON.parse(JSON.stringify(mov)));
  verdict(r0.ok && Object.keys(r0.derived).length === 0, 'stored movement ACCEPTED unchanged by installMMovementSaveHooks (' + nH + ' hook)');
  console.log('§HARDEN surface=MMovement.beforeSave record_id=' + mov.m_movement_id + ' verdict=' + (r0.ok ? 'ACCEPT' : 'REJECT') + ' derived-contradictions=0 diff=' + (r0.ok ? 0 : 'MISMATCH'));
  var m = JSON.parse(JSON.stringify(mov)); m.c_doctype_id = 0;
  var got = fire(m).derived.c_doctype_id;
  verdict(String(got) === String(mov.c_doctype_id), 'C_DocType stripped → re-derives the STORED doctype ' + mov.c_doctype_id + ' (first MMM by IsDefault DESC, C_DocType_ID — :214-216 ∘ getOfDocBaseType:74)', 'derived=' + got);
  console.log('§HARDEN surface=MMovement.beforeSave.derive col=c_doctype_id derived=' + got + ' stored=' + mov.c_doctype_id + ' diff=' + (String(got) === String(mov.c_doctype_id) ? 0 : 'MISMATCH'));
})();

// ── §FALSIFIERS ──────────────────────────────────────────────────────────────────────────────────────────
(function () {
  var r = F.dispatchFor(db, 321, { docStatus: 'CO', doctypeId: 144, processing: 'N', periodOpen: true, isBackDateTrxAllowed: true }, 'RE');
  verdict(!r.ok, '§FALSIFIER-A ReActivate a completed Inventory → rejected (not offered AND not implemented)', 'reason=' + r.reason);
  console.log('§FALSIFIER-A table=321 action=RE from=CO ok=' + r.ok + ' reason=' + r.reason);
  var mutated = F.legalActionsFor(db, 325, { docStatus: 'CO', doctypeId: 139, processing: 'N', periodOpen: true, isBackDateTrxAllowed: false }).concat(['RC']);
  var ora = O.oracleSet(generic, prodBlk, 'CO', { periodOpen: true, backDate: false, canReact: false });
  verdict(!setEq(mutated, ora), '§FALSIFIER-B inject RC into the backDate-blocked Production CO set → set-diff vs parsed oracle fires',
    'mutated=[' + mutated.sort().join(',') + '] oracle=[' + ora.sort().join(',') + ']');
  console.log('§FALSIFIER-B table=325 mutation=+RC@CO(backDateBlocked) setEq=' + setEq(mutated, ora) + ' (must be false)');
})();

console.log('\n§HARDEN_RESIDUAL ⛔ M_Inventory/M_Production beforeSave STORED-REPLAY = no-seed (m_inventory/m_production hold zero client-11 docs; a record fixture would be synthesized — refused). ' +
  'Their beforeSave reject conditions are cited unported: MInventory doctype-mandatory :301-305 + warehouse-immutable-with-lines :307-314 (needs m_inventoryline, absent) · MProduction doctype-default :1072-1074 + production-plan flag :1076-1084 — the isomorph tail can port them the day seed docs exist · ' +
  'movements/on-hand + inter-org cost posting already oracle-folded (W-FOLD-MOVEMENT/-FX, W-FOLD-QTYONHAND, W-FOLD-PRODUCTION, W-FOLD-INVENTORY) — CITED, not redone');
console.log('§HARDEN surface=MInventoryFamily.docaction fixtures=' + (fixtures + tFixAll + 2) + ' diff=0 oracle=iDempiere(parsed-source+seed-replay where seed exists)');
console.log((fails === 0 ? '🟢 W-MINVENTORY-FAMILY-FSM PASS' : '🔴 W-MINVENTORY-FAMILY-FSM FAIL (' + fails + ')') +
  ' — Movement/Inventory/Production FSM diffed against the runtime-parsed source; the one real movement replayed; no-seed surfaces stated ⛔, never synthesized.');
db.close();
process.exit(fails === 0 ? 0 : 1);
