#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (CRUD_EDIT_PERSIST.md residual: docstatus-select). Read the log after every run (Log Mandate).
// poc_crud_docstatus.js — W-CRUD-DOCSTATUS: the docstatus list-widget corruption bug.
//
// ISSUE IT PROVES (named, per CLAUDE.md "tests expose issues") — observed UI_UNPARK_RESUME.md B-3:
//   The edit form's docstatus <select> did NOT render the record's CURRENT value selected (populateRefs
//   read `data-cur` which fieldInput never set, and never emitted `selected`) → the select landed on the
//   FIRST __meta key (DR). gatherVals then read DR off a CO order, and the save diff emitted
//   `cols=docstatus` (CO→DR) on an edit that never touched status — SILENT live-data corruption.
//   Second arm: even an EXPLICIT status change rode CRUD_UPDATE as a plain column write, which
//   readTip (SET_STATUS truth, what doProcess derives `from` off) never sees → split-brain.
//
// WHAT IT PROVES (each NAMED):
//   D1  CONTROL (pre-fix shape): first-option render off a CO order makes buildOp emit a docstatus flip.
//   D2  listOptions renders the CURRENT value (CO) selected — not the first key (DR).
//   D3  a current value ABSENT from the ref map is PREPENDED + selected, never silently swapped.
//   D4  edit an UNRELATED column on a CO order → the op carries NO docstatus; the persist line is clean.
//   D5  no-op save (zero changed columns) → splitStatusChange yields neither op (nothing to commit).
//   D6  FALSIFIER: an EXPLICIT status change CO→CL still emits docstatus — as a DOC_ACTION (SET_STATUS)
//       through the kernel; readTip returns CL; tipValues carries NO docstatus (never a column write).
//   D7  GATING: explicitly setting docAction.to (CO) with unmet requires demotes to IP — the form gets
//       the SAME docActionOutcome gate as the Process ▶, no bypass lane.
//   D8  MIXED edit (description + docstatus) splits: fieldOp=description only, statusOp=the transition.
//
// NON-INVENT: op shapes from the REAL CORE.buildOp/splitStatusChange/buildDocActionGroup
//   (build/erp/crud_overlay.js); kernel = the REAL build/erp/kernel_ops.js under a window shim;
//   the docStatus ref map is read from the REAL build/erp/crud_ops.json __meta. No fixtures.
//
// DETERMINISM (§7): no Date.now()/Math.random() — ids index-derived; commitGroup gets explicit baseTs.
//
// Run: bash build/erp/run_witness.sh scripts/poc_crud_docstatus.js   — then READ build/erp/poc_crud_docstatus.log.
'use strict';
var path = require('path');
var fs = require('fs');
var initSqlJs = require('sql.js');

global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;
var CORE = require(path.join(__dirname, '..', 'build', 'erp', 'crud_overlay.js'));   // REAL listOptions + splitStatusChange + buildOp
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));               // window.KernelOps (signed log)
var KO = global.window.KernelOps;

// the REAL ref map + a real c_order descriptor (mirrors crud_ops.json entry: docAction CO requires bp+total)
var OPS = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'build', 'erp', 'crud_ops.json'), 'utf8'));
var DOCSTATUS = OPS.__meta.docStatus;   // {DR, IP, CO, CL} — extracted, not invented
var E_ORDER = { key: 'c_order', verbs: ['update', 'process'],
  docAction: { action: 'CO', from: 'DR', to: 'CO', requires: ['c_bpartner_id', 'grandtotal'], oracle: 'org.compiere.model.MOrder.completeIt()' },
  fields: [
    { col: 'c_order_id',   type: 'fk', readonly: true },
    { col: 'documentno',   type: 'string', readonly: true },
    { col: 'description',  type: 'string' },
    { col: 'grandtotal',   type: 'number' },
    { col: 'c_bpartner_id', type: 'fk', ref: 'c_bpartner' },
    { col: 'docstatus',    type: 'list', ref: 'docStatus', required: true }
  ] };
var BUNDLE = { c_order_id: 106, documentno: 'ORD-6', description: 'orig', grandtotal: 120, c_bpartner_id: 117, docstatus: 'CO' };  // a COMPLETED order

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function vals(over) { var v = {}; Object.keys(BUNDLE).forEach(function (k) { v[k] = BUNDLE[k]; }); Object.keys(over || {}).forEach(function (k) { v[k] = over[k]; }); return v; }

(async function () {
  console.log('═══ POC-CRUD-DOCSTATUS — W-CRUD-DOCSTATUS: docstatus select renders CURRENT + status never a silent column write ═══');
  console.log('issue=prompts/CRUD_EDIT_PERSIST.md residual (UI_UNPARK_RESUME.md B-3)  path=build/erp/crud_overlay.js  kernel=build/erp/kernel_ops.js\n');
  var SQL = await initSqlJs();

  // D1 — CONTROL: the pre-fix render. No selected marking → the select reads back the FIRST key (DR).
  console.log('D1 — CONTROL (pre-fix shape): first-option render off a CO order emits a phantom docstatus flip');
  var firstKey = Object.keys(DOCSTATUS)[0];
  var opBug = CORE.buildOp('update', E_ORDER, vals({ description: 'touched', docstatus: firstKey }), BUNDLE, { id: 106 });
  console.log('§CRUD-DOCSTATUS control firstKey=' + firstKey + ' emitted=' + Object.keys(opBug.changes).join(','));
  verdict(firstKey === 'DR' && !!opBug.changes.docstatus && opBug.changes.docstatus['new'] === 'DR',
          'control names the bug: first __meta key is DR → un-selected render flips a CO order to DR in the diff',
          'changes=' + JSON.stringify(opBug.changes));

  // D2 — render arm: listOptions marks the CURRENT value selected.
  console.log('\nD2 — listOptions renders the CURRENT value selected (not the first key)');
  var lo = CORE.listOptions(DOCSTATUS, 'CO');
  var sel = lo.filter(function (o) { return o.selected; });
  console.log('§CRUD-LIST col=docstatus cur="CO" options=' + lo.length + ' selected="' + (sel.length ? sel[0].value : '(first)') + '"');
  verdict(sel.length === 1 && sel[0].value === 'CO', 'the CO order renders with CO selected (exactly one selected option)', JSON.stringify(sel));
  verdict(lo[0].value === 'DR' && !lo[0].selected, 'the first key (DR) is present but NOT selected', JSON.stringify(lo[0]));
  verdict(lo.length === Object.keys(DOCSTATUS).length, 'all ' + Object.keys(DOCSTATUS).length + ' ref options render', 'got=' + lo.length);

  // D3 — a current value absent from the ref map is kept, never swapped.
  var lo2 = CORE.listOptions(DOCSTATUS, 'VO');   // e.g. a Voided doc whose status is not in this slim ref
  console.log('§CRUD-LIST col=docstatus cur="VO" options=' + lo2.length + ' selected="' + lo2.filter(function (o) { return o.selected; }).map(function (o) { return o.value; })[0] + '"');
  verdict(lo2[0].value === 'VO' && lo2[0].selected === true && lo2.length === Object.keys(DOCSTATUS).length + 1,
          'a current value ABSENT from the ref map is PREPENDED + selected (never silently flipped)', JSON.stringify(lo2[0]));

  // D4 — diff arm: editing an UNRELATED column with the fixed render emits NO docstatus.
  console.log('\nD4 — edit an unrelated column on a CO order → persist line carries NO docstatus');
  var db = new SQL.Database(); KO.ensureTable(db);
  var op = CORE.buildOp('update', E_ORDER, vals({ description: 'red1', docstatus: 'CO' }), BUNDLE, { id: 106, opUuid: 'crud-ord-106-u1' });
  var sp = CORE.splitStatusChange(E_ORDER, op, vals({ description: 'red1', docstatus: 'CO' }));
  verdict(sp.statusOp === null, 'an untouched docstatus produces NO status op', 'statusOp=' + JSON.stringify(sp.statusOp));
  var res = await KO.commitGroup(db, [{ op_type: 'CRUD_UPDATE', op_uuid: sp.fieldOp.op_uuid, params: { table: sp.fieldOp.table, id: sp.fieldOp.id, changes: sp.fieldOp.changes } }], { baseTs: 1000 });
  var v = await KO.verifyChain(db);
  var cols = Object.keys(sp.fieldOp.changes).join(',');
  console.log('§CRUD-PERSIST key=c_order id=106 op=CRUD_UPDATE cols=' + cols + ' source=sidecar gid=' + res.gid + ' ops=' + res.ids.length + ' sealed=' + res.sealed + ' verifyChain=' + (v.ok ? 'ok' : 'FAIL'));
  verdict(res.committed === true && v.ok === true && cols.indexOf('docstatus') < 0 && cols === 'description',
          'the persist line carries ONLY the edited column — NO phantom docstatus', 'cols=' + cols);
  verdict(CORE.readTip(db, 'c_order', 106) === null, 'readTip (status truth) untouched by the field edit', 'tip=' + CORE.readTip(db, 'c_order', 106));

  // D5 — no-op save: zero changed columns commits nothing.
  console.log('\nD5 — no-op save (zero changed columns) commits NOTHING');
  var opNoop = CORE.buildOp('update', E_ORDER, vals({}), BUNDLE, { id: 106 });
  var spNoop = CORE.splitStatusChange(E_ORDER, opNoop, vals({}));
  console.log('§CRUD update key=c_order no-op (0 changed columns) — nothing committed');
  verdict(spNoop.statusOp === null && Object.keys(spNoop.fieldOp.changes).length === 0,
          'identical values → no status op and an empty change-set (saveForm suppresses the commit)', JSON.stringify(spNoop.fieldOp.changes));

  // D6 — FALSIFIER: an EXPLICIT status change still emits docstatus — via the DOC_ACTION lane.
  console.log('\nD6 — FALSIFIER: explicit CO→CL emits docstatus through DOC_ACTION gating (SET_STATUS), not a column write');
  var opX = CORE.buildOp('update', E_ORDER, vals({ docstatus: 'CL' }), BUNDLE, { id: 106, opUuid: 'crud-ord-106-st1' });
  var spX = CORE.splitStatusChange(E_ORDER, opX, vals({ docstatus: 'CL' }));
  console.log('§CRUD-STATUS-SPLIT key=c_order docstatus ' + spX.statusOp.from + '→' + spX.statusOp.to + ' lane=DOC_ACTION fieldCols=' + (spX.fieldOp ? Object.keys(spX.fieldOp.changes).join(',') : '(none)'));
  verdict(spX.statusOp && spX.statusOp.op_type === 'DOC_ACTION' && spX.statusOp.from === 'CO' && spX.statusOp.to === 'CL',
          'explicit change → a DOC_ACTION op CO→CL (docstatus IS emitted, on the right lane)', JSON.stringify(spX.statusOp && { from: spX.statusOp.from, to: spX.statusOp.to }));
  verdict(spX.fieldOp === null, 'NO CRUD_UPDATE rides along (docstatus stripped from the column write)', 'fieldOp=' + JSON.stringify(spX.fieldOp));
  var group = CORE.buildDocActionGroup(spX.statusOp);   // the SAME group commitProcess commits
  verdict(group && group.length === 1 && group[0].op_type === 'SET_STATUS', 'it folds to the kernel SET_STATUS group (readTip\'s source of truth)', JSON.stringify(group && group[0].op_type));
  var resX = await KO.commitGroup(db, group, { baseTs: 2000 });
  var vX = await KO.verifyChain(db);
  console.log('§CRUD process committed key=c_order viaGroup=Y gid=' + resX.gid + ' ops=' + resX.ids.length + ' sealed=' + resX.sealed + ' to=CL verifyChain=' + (vX.ok ? 'ok' : 'FAIL'));
  verdict(resX.committed === true && vX.ok === true, 'the explicit transition commits SIGNED (verifyChain OK)', 'committed=' + resX.committed);
  verdict(CORE.readTip(db, 'c_order', 106) === 'CL', 'readTip (the FSM truth doProcess folds from) now reads CL', 'tip=' + CORE.readTip(db, 'c_order', 106));
  var tipV = CORE.tipValues(db, 'c_order', 106);
  verdict(!Object.prototype.hasOwnProperty.call(tipV, 'docstatus'), 'tipValues carries NO docstatus — status was NEVER a silent column write', 'tipValues=' + JSON.stringify(tipV));

  // D7 — GATING: explicitly picking docAction.to (CO) with unmet requires demotes to IP (same gate as Process ▶).
  console.log('\nD7 — explicit DR→CO with unmet requires demotes to IP (docActionOutcome gate, no bypass)');
  var draft = vals({ docstatus: 'DR', grandtotal: '', c_bpartner_id: '' });   // a draft missing the requires
  var opG = CORE.buildOp('update', E_ORDER, vals({ docstatus: 'CO', grandtotal: '', c_bpartner_id: '' }), draft, { id: 106 });
  var spG = CORE.splitStatusChange(E_ORDER, opG, vals({ docstatus: 'CO', grandtotal: '', c_bpartner_id: '' }));
  console.log('§CRUD-STATUS-SPLIT key=c_order docstatus DR→' + spG.statusOp.to + ' lane=DOC_ACTION outcome=' + spG.statusOp.outcome + ' unmet=' + spG.statusOp.unmet.join(','));
  verdict(spG.statusOp.to === 'IP' && spG.statusOp.outcome === 'in-progress' && spG.statusOp.unmet.length === 2,
          'unmet requires (c_bpartner_id, grandtotal) → the form completion demotes to IP exactly like Process ▶', 'to=' + spG.statusOp.to + ' unmet=' + spG.statusOp.unmet.join(','));

  // D8 — MIXED edit splits into the two lanes.
  console.log('\nD8 — mixed edit (description + docstatus) splits: field lane + status lane');
  var opM = CORE.buildOp('update', E_ORDER, vals({ description: 'both', docstatus: 'CL' }), BUNDLE, { id: 106 });
  var spM = CORE.splitStatusChange(E_ORDER, opM, vals({ description: 'both', docstatus: 'CL' }));
  console.log('§CRUD-STATUS-SPLIT key=c_order docstatus CO→CL lane=DOC_ACTION fieldCols=' + Object.keys(spM.fieldOp.changes).join(','));
  verdict(spM.fieldOp && Object.keys(spM.fieldOp.changes).join(',') === 'description' && spM.statusOp && spM.statusOp.to === 'CL',
          'fieldOp = description ONLY; statusOp = the CO→CL transition', 'fieldCols=' + Object.keys(spM.fieldOp.changes).join(','));

  db.close();
  console.log('\n§CRUD-DOCSTATUS ' + (fails ? 'FAIL — ' + fails + ' checks red.'
    : 'PASS — the docstatus select renders the record\'s CURRENT value selected (absent values prepended, never flipped); '
    + 'an unrelated edit emits NO docstatus; a no-op save commits nothing; an EXPLICIT status change routes through '
    + 'DOC_ACTION gating (SET_STATUS, requires-gated like Process ▶) — never a silent column write. Split-brain closed.'));
  process.exit(fails ? 1 : 0);
})().catch(function (e) { console.error('FATAL', e); process.exit(2); });
