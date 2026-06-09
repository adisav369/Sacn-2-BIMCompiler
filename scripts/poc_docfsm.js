#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_docfsm.js — W-DOCFSM witness. Opens canonical build/erp/ad_full.db and drives build/erp/ad_docfsm.js
// over a REAL C_DocType (Standard Order, 132): the legal-action set per status, the full reversal-family
// transitions (CO→VO/CL/RE/IP), the FSM range (reaches >2 of 12 statuses), and a §FALSIFIER (an illegal
// action from a status is rejected; a terminal status offers none).
// Implementing ERP_COVERAGE_MATRIX.md §C_DocType FSM (ranked GAP #5) — Witness: W-DOCFSM
// Run: node scripts/poc_docfsm.js 2>&1 | tee build/erp/poc_docfsm.log   (read the log; exit code != evidence)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var F = require(path.join(__dirname, '..', 'build', 'erp', 'ad_docfsm.js'));
var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

console.log('═══ W-DOCFSM — C_DocType FSM beyond CO (real ad_full.db doctype → legal actions + transitions) ═══\n');
var db = new Database(DB_PATH, { readonly: true });

// ── coverage: the vocabulary is AD data; the FSM range vs the engine's prior 2-of-12 ────────────────────
var nDocType = db.prepare('SELECT COUNT(*) AS n FROM c_doctype').get().n;
var nActions = db.prepare('SELECT COUNT(*) AS n FROM ad_ref_list WHERE ad_reference_id=135').get().n;
var nStatus  = db.prepare('SELECT COUNT(*) AS n FROM ad_ref_list WHERE ad_reference_id=131').get().n;
var reach = F.reachableStatuses();
console.log('§DOCTYPE_FSM_COVERAGE doctypes=' + nDocType + ' actions=' + nActions + ' statuses=' + nStatus +
  ' reachableAsTarget=' + reach.length + ' [' + reach.join(',') + ']  (engine prior: 2 of 12 = CO,IP)');
verdict(nDocType === 52 && nActions === 14 && nStatus === 12, 'AD vocabulary matches matrix (52 doctype / 14 actions / 12 statuses)', 'dt=' + nDocType + ' a=' + nActions + ' s=' + nStatus);
verdict(reach.length >= 8 && reach.indexOf('VO') >= 0 && reach.indexOf('CL') >= 0 && reach.indexOf('RE') >= 0,
  'FSM reaches >2 statuses incl. the reversal family (VO/CL/RE), not just CO/IP', 'reachable=' + reach.length);

// ── a REAL C_DocType (132 Standard Order, SOO) — legal actions per status + transitions ─────────────────
var DT = 132;
var la = F.legalActions(db, DT, 'CO');
console.log('\n§DOCTYPE_FSM doctype=' + DT + ' docBaseType=' + la.docBaseType + ' from=CO legalActions=[' + la.actions.join(',') + ']');
verdict(la.actions.join(',') === 'CL,RC,RA,RE,VO,PO', 'Completed order offers the reversal family {CL,RC,RA,RE,VO,PO}', la.actions.join(','));

console.log('\n── transitions dispatched through the FSM (from → action → to) ──');
[['DR', 'CO', 'CO'], ['IP', 'CO', 'CO'], ['CO', 'VO', 'VO'], ['CO', 'CL', 'CL'], ['CO', 'RC', 'RE'], ['CO', 'RE', 'IP'], ['DR', 'PR', 'IP'], ['CO', 'PO', 'CO']].forEach(function (tc) {
  var r = F.dispatch(db, DT, tc[0], tc[1]);
  console.log('§DOCTYPE_FSM from=' + tc[0] + ' action=' + tc[1] + ' to=' + (r.ok ? r.to : 'REJECT') + ' legalActions=[' + (r.legalActions || []).join(',') + ']');
  verdict(r.ok && r.to === tc[2], 'transition ' + tc[0] + ' --' + tc[1] + '--> ' + tc[2], r.ok ? 'to=' + r.to : r.reason);
});

// ── §FALSIFIER — an illegal action is rejected; a terminal status offers none ───────────────────────────
console.log('\n── §FALSIFIER — illegal transitions rejected ──');
var bad1 = F.dispatch(db, DT, 'CO', 'PR');         // Prepare from Completed — not legal
console.log('§FALSIFIER from=CO action=PR → ok=' + bad1.ok + ' reason=' + bad1.reason);
verdict(bad1.ok === false && bad1.reason === 'illegal-action', 'Prepare-from-Completed is REJECTED (not in CO legal set)', 'reason=' + bad1.reason);
var bad2 = F.dispatch(db, DT, 'CL', 'CO');         // Complete from Closed — terminal
console.log('§FALSIFIER from=CL action=CO → ok=' + bad2.ok + ' reason=' + bad2.reason);
verdict(bad2.ok === false, 'Complete-from-Closed is REJECTED (CL is terminal, legalActions=[])', 'reason=' + bad2.reason);
var termCL = F.legalActions(db, DT, 'CL');
verdict(termCL.actions.length === 0, 'a Closed document offers NO actions (terminal)', 'actions=' + JSON.stringify(termCL.actions));

console.log('\n' + (fails === 0 ? '🟢 W-DOCFSM PASS' : '🔴 W-DOCFSM FAIL (' + fails + ')') +
  ' — C_DocType FSM dispatches the full action set on canonical ad_full.db; reaches the reversal family, rejects illegal. Re-verdict C_DocType FSM + GAP #5 (⛔→🟡).');
db.close();
process.exit(fails === 0 ? 0 : 1);
