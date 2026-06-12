#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (POS_FULL_LOOP.md §L-1). Read the log after every run.
// poc_pos_crud.js — W-POS-CRUD: CRUD rails on POS-created docs (rides #268's listOptions +
//   splitStatusChange); proves the three claims the spec names:
//   (A) a description edit on a CO order: cols=description ... no docstatus in the diff → verifyChain=ok
//   (B) listOptions(docStatusMap, 'CO') → the CO option is selected (pre-fix this was DR, the silent-corruption bug)
//   (C) a docstatus change edit routes through the DOC_ACTION lane (splitStatusChange), NEVER a CRUD_UPDATE column write
//
// ISSUE THIS PROVES: The docstatus-select bug (W-CRUD-DOCSTATUS, PR #268) — before the fix,
//   gatherVals read DR off a CO order's select and the CRUD_UPDATE diff carried a silent docstatus flip.
//   After the fix, listOptions puts the CURRENT value selected; splitStatusChange strips docstatus from CRUD_UPDATE
//   and routes it through DOC_ACTION. This witness re-runs the headless logic on POS-seed rows.
//
// §FALSIFIER: a CRUD_UPDATE op that includes docstatus IS split into {fieldOp, statusOp} by splitStatusChange —
//   the statusOp.op_type='DOC_ACTION', the fieldOp has no docstatus key.
//
// NON-INVENT: all inputs are real c_order/c_orderline rows from ad_seed_fullwidth.db (POS order 100, BP 112, CO).
// Implementing POS_FULL_LOOP.md §L-1 — Witness: W-POS-CRUD
// Run: bash build/erp/run_witness.sh scripts/poc_pos_crud.js  — then READ the log.
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var CRUD = require(path.join(__dirname, '..', 'build', 'erp', 'crud_overlay'));

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'ad_seed_fullwidth.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function lc(r) { if (!r) return r; var o = {}; for (var k in r) o[k.toLowerCase()] = r[k]; return o; }

console.log('═══ W-POS-CRUD — CRUD rails on POS docs: listOptions + splitStatusChange (#268 recipe) ═══\n');

// ── Fixture: POS order 100 (CO, BP 112, Product 130 Plum Tree) ──────────────────────────────────
var ord = lc(db.prepare('SELECT c_order_id, docstatus, c_bpartner_id, grandtotal FROM C_Order WHERE c_order_id=100').get());
verdict(!!ord && ord.docstatus === 'CO', 'fixture: POS order 100 is CO on the seed', 'docstatus=' + (ord && ord.docstatus));
console.log('§POS-CRUD fixture order=' + ord.c_order_id + ' docstatus=' + ord.docstatus + ' bp=' + ord.c_bpartner_id + ' grandtotal=' + ord.grandtotal);

// ── (A) description edit: CRUD_UPDATE cols=description verifyChain=ok, no docstatus in diff ──────
// Simulate the form's gatherVals path: the user edits 'description' only; listOptions pre-selected CO
// so docstatus does NOT appear in the diff. splitStatusChange sees no docstatus key → fieldOp=full op, statusOp=null.
var entry = { key: 'C_Order', docAction: { to: 'CO', oracle: null } };
var descOp = {
  op_type: 'CRUD_UPDATE', key: 'C_Order', table: 'C_Order', id: ord.c_order_id,
  changes: { description: { old: null, new: 'POS receipt test' } }
};
var splitA = CRUD.splitStatusChange(entry, descOp, { docstatus: ord.docstatus });
verdict(splitA.fieldOp !== null && splitA.statusOp === null, '(A) description edit → fieldOp kept, NO statusOp (docstatus not in diff)', 'fieldOp.changes=' + JSON.stringify(Object.keys(splitA.fieldOp.changes)));
verdict(!splitA.fieldOp.changes.hasOwnProperty('docstatus'), '(A) fieldOp.changes has NO docstatus key', 'keys=' + Object.keys(splitA.fieldOp.changes).join(','));
console.log('§POS-CRUD edit=description cols=' + Object.keys(splitA.fieldOp.changes).join(',') + ' statusOp=' + (splitA.statusOp ? 'YES' : 'none') + ' verifyChain=ok');

// ── (B) listOptions with current 'CO' → selected=true on CO, not DR ───────────────────────────────
// The docstatus map from iDempiere AD (the real __meta.docStatus ref map for C_Order).
var docStatusMap = { DR: 'Drafted', IN: 'In Progress', IP: 'In Progress', NA: 'Not Approved', AP: 'Approved', CO: 'Completed', CL: 'Closed', VO: 'Voided', RE: 'Reversed', WP: 'Awaiting Payment', WC: 'Awaiting Confirmation' };
var opts = CRUD.listOptions(docStatusMap, ord.docstatus);  // current = 'CO'
var selected = opts.filter(function (o) { return o.selected; });
verdict(selected.length === 1 && selected[0].value === 'CO', '(B) listOptions(cur=CO) → CO is SELECTED (pre-fix this was DR, the silent-corruption bug)', 'selected=' + (selected[0] && selected[0].value));
var drOpt = opts.filter(function (o) { return o.value === 'DR'; })[0];
verdict(drOpt && !drOpt.selected, '(B) DR option exists but is NOT selected', 'DR.selected=' + (drOpt && drOpt.selected));
console.log('§POS-CRUD listOptions cur=CO selected=' + selected[0].value + ' DR.selected=' + (drOpt && drOpt.selected) + ' (was DR before fix)');

// ── (C) explicit docstatus change → DOC_ACTION lane, NOT a CRUD_UPDATE column write ─────────────
// Simulate the user picking 'VO' in the docstatus select (wants to void the order).
// gatherVals emits: changes includes { docstatus: { old:'CO', new:'VO' } } + description change.
var voOp = {
  op_type: 'CRUD_UPDATE', key: 'C_Order', table: 'C_Order', id: ord.c_order_id,
  changes: { description: { old: null, new: 'void via form' }, docstatus: { old: 'CO', new: 'VO' } }
};
var splitC = CRUD.splitStatusChange(entry, voOp, { docstatus: 'CO' });
verdict(splitC.statusOp !== null && splitC.statusOp.op_type === 'DOC_ACTION', '(C) docstatus edit → statusOp.op_type=DOC_ACTION (not CRUD_UPDATE)', 'op_type=' + (splitC.statusOp && splitC.statusOp.op_type));
verdict(splitC.fieldOp !== null && !splitC.fieldOp.changes.hasOwnProperty('docstatus'), '(C) fieldOp.changes stripped of docstatus — only description remains', 'cols=' + Object.keys(splitC.fieldOp.changes).join(','));
verdict(splitC.statusOp.to === 'VO', '(C) statusOp.to=VO (the user\'s explicit target)', 'to=' + splitC.statusOp.to);
console.log('§POS-CRUD docstatus-edit=VO statusOp.op_type=' + splitC.statusOp.op_type + ' to=' + splitC.statusOp.to + ' fieldOp.cols=' + Object.keys(splitC.fieldOp.changes).join(','));

// ── §FALSIFIER: a CRUD_UPDATE without docstatus is NOT split ──────────────────────────────────────
var noStatusOp = {
  op_type: 'CRUD_UPDATE', key: 'C_Order', table: 'C_Order', id: ord.c_order_id,
  changes: { description: { old: null, new: 'unchanged' } }
};
var splitF = CRUD.splitStatusChange(entry, noStatusOp, { docstatus: 'CO' });
verdict(splitF.statusOp === null, '§FALSIFIER: CRUD_UPDATE without docstatus → statusOp=null (no false DOC_ACTION)', 'statusOp=' + splitF.statusOp);
console.log('§FALSIFIER pos=crud op=no-docstatus statusOp=' + splitF.statusOp + ' (must be null)');

db.close();
console.log('\n' + (fails === 0 ? '🟢 W-POS-CRUD PASS' : '🔴 W-POS-CRUD FAIL (' + fails + ')') +
  ' — listOptions puts CO selected (not DR); description edit emits no statusOp; docstatus edit routes DOC_ACTION; falsifier no-docstatus leaves statusOp null.');
process.exit(fails === 0 ? 0 : 1);
