#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Item 2 (FRONTEND_LANE_MASTER.md §OUTSTANDING — WIRE FULL DOCACTION SET PER AD).
//   Read the log after every run.
// poc_docaction_full.js — W-DOCACTION-FULL: the CORE↔FSM bridge. Today the CRUD Process ▶ reads a HARDCODED
//   {action:"CO"} from crud_ops.json (every doc table), so only Complete is ever offered — even though the FSM
//   (ad_docfsm.js) DEFINES all 14 actions × 12 statuses on a real C_DocType. This witness proves CORE now
//   surfaces the LEGAL action set for the record's CURRENT status FROM the FSM (CORE.legalDocActions →
//   AdDocFsm.legalActions), and that CORE.docActionOutcome/buildOp route a CHOSEN action through the FSM —
//   CO still honours its field preconditions, RC/RA post a REVERSAL (→ RE) and never a silent un-complete on a
//   posted doc, and an illegal action is reported with the legal set rather than applied.
//
// ISSUES IT PROVES (named):
//   §DOCACTION-SET     — CORE.legalDocActions returns the FSM's per-status legal set (CO→{CL,RC,RA,RE,VO,PO},
//                        DR→{CO,PR,VO,XL}), NOT the hardcoded single CO — the ring can offer the full set.
//   §DOCACTION-CO-GATE — a chosen CO with its docAction.requires met → to=CO (success); with a required col
//                        empty → to=IP (in-progress, the honest non-completion) — the completeIt gate survives.
//   §DOCACTION-REVERSAL— a chosen RC (and RA) on a COMPLETED doc → to=RE, outcome=reversal (reversal=true):
//                        a reversing document, NEVER a silent un-complete back to DR/IP.
//   §DOCACTION-ILLEGAL — a chosen action illegal from the current status (PR from CO) → outcome=illegal with
//                        the legal set echoed; the status is NOT mutated (no bypass of the FSM).
//   §DOCACTION-BUILDOP — buildOp('process', …, {chosen:'RC'}) emits a DOC_ACTION op carrying action=RC, to=RE,
//                        reversal=true — the op the signed kernel would apply.
//   §FALSIFIER         — with NO fsm/chosen opts, docActionOutcome falls back to the LEGACY hardcoded-CO path
//                        unchanged (action=CO, requires-gate) — the bridge is opt-in, existing witnesses safe.
//
// NON-INVENT: the legal set + transitions come from the FSM over the real canonical ad_full.db C_DocType;
//   no Date.now/Math.random; CORE only routes, it never fabricates a status.
// Implementing FRONTEND_LANE_MASTER.md §OUTSTANDING item 2 — Witness: W-DOCACTION-FULL
// Run: node scripts/poc_docaction_full.js 2>&1 | tee build/erp/poc_docaction_full.log — then READ the log.
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
global.window = global.window || {};
var F = require(path.join(__dirname, '..', 'build', 'erp', 'ad_docfsm.js'));
var CORE = require(path.join(__dirname, '..', 'build', 'erp', 'crud_overlay.js'));
var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

console.log('═══ W-DOCACTION-FULL — CORE surfaces the FULL DocAction set per AD/FSM (not hardcoded CO) ═══\n');
var db = new Database(DB_PATH, { readonly: true });
var DT = 132;   // Standard Order (SOO) — the doctype poc_docfsm pins
// the crud_ops.json c_order descriptor (its completeIt preconditions ride here, verbatim).
var entry = { key: 'c_order', docAction: { action: 'CO', from: 'DR', to: 'CO',
  requires: ['c_bpartner_id', 'grandtotal'], oracle: 'org.compiere.model.MOrder.completeIt()' } };
var fsmCtx = { fsm: F, fsmDb: db, doctypeId: DT };

// ── §DOCACTION-SET ──────────────────────────────────────────────────────────────────────────────────────
console.log('§DOCACTION-SET — legal set per status comes from the FSM, not {action:"CO"}');
var coSet = CORE.legalDocActions(F, db, DT, 'CO');
var drSet = CORE.legalDocActions(F, db, DT, 'DR');
console.log('   from=CO legalActions=[' + (coSet ? coSet.actions.join(',') : '∅') + ']  from=DR legalActions=[' + (drSet ? drSet.actions.join(',') : '∅') + ']');
verdict(!!coSet && coSet.actions.join(',') === 'CL,RC,RA,RE,VO,PO', 'completed doc offers the reversal family {CL,RC,RA,RE,VO,PO}', coSet ? coSet.actions.join(',') : '∅');
verdict(!!drSet && drSet.actions.join(',') === 'CO,PR,VO,XL', 'draft doc offers {CO,PR,VO,XL}', drSet ? drSet.actions.join(',') : '∅');
verdict(coSet.actions.length > 1, 'more than the single hardcoded CO is offered', 'count=' + coSet.actions.length);

// ── §DOCACTION-CO-GATE ──────────────────────────────────────────────────────────────────────────────────
console.log('\n§DOCACTION-CO-GATE — chosen CO still honours its field preconditions');
var coOk  = CORE.docActionOutcome(entry, { c_bpartner_id: 1001, grandtotal: 250 }, Object.assign({ chosen: 'CO', from: 'DR' }, fsmCtx));
var coIp  = CORE.docActionOutcome(entry, { c_bpartner_id: 1001, grandtotal: '' }, Object.assign({ chosen: 'CO', from: 'DR' }, fsmCtx));
console.log('   reqs-met → ' + coOk.from + '→' + coOk.to + ' (' + coOk.outcome + ')   reqs-unmet → ' + coIp.from + '→' + coIp.to + ' (' + coIp.outcome + ' unmet=' + JSON.stringify(coIp.unmet) + ')');
verdict(coOk.to === 'CO' && coOk.outcome === 'success', 'CO with requires met → CO (success)', coOk.to + '/' + coOk.outcome);
verdict(coIp.to === 'IP' && coIp.outcome === 'in-progress' && coIp.unmet.indexOf('grandtotal') >= 0, 'CO with a required col empty → IP (honest non-completion)', coIp.to + '/' + coIp.outcome);

// ── §DOCACTION-REVERSAL ─────────────────────────────────────────────────────────────────────────────────
console.log('\n§DOCACTION-REVERSAL — RC/RA on a posted doc post a REVERSAL (→RE), never a silent un-complete');
var rc = CORE.docActionOutcome(entry, {}, Object.assign({ chosen: 'RC', from: 'CO' }, fsmCtx));
var ra = CORE.docActionOutcome(entry, {}, Object.assign({ chosen: 'RA', from: 'CO' }, fsmCtx));
console.log('   RC: CO→' + rc.to + ' (' + rc.outcome + ' reversal=' + rc.reversal + ')   RA: CO→' + ra.to + ' (' + ra.outcome + ' reversal=' + ra.reversal + ')');
verdict(rc.to === 'RE' && rc.outcome === 'reversal' && rc.reversal === true, 'RC → RE flagged as a reversal', rc.to + '/' + rc.outcome);
verdict(ra.to === 'RE' && ra.reversal === true, 'RA → RE flagged as a reversal', ra.to + '/' + ra.outcome);
verdict(rc.to !== 'DR' && rc.to !== 'IP', 'RC is NOT a silent un-complete (not →DR/IP)', 'to=' + rc.to);

// ── §DOCACTION-ILLEGAL ──────────────────────────────────────────────────────────────────────────────────
console.log('\n§DOCACTION-ILLEGAL — an action illegal from the current status is reported, not applied');
var bad = CORE.docActionOutcome(entry, {}, Object.assign({ chosen: 'PR', from: 'CO' }, fsmCtx));
console.log('   PR from CO → outcome=' + bad.outcome + ' to=' + bad.to + ' legalActions=[' + (bad.legalActions || []).join(',') + ']');
verdict(bad.outcome === 'illegal' && bad.to === 'CO', 'PR from CO is illegal, status unchanged', bad.outcome + ' to=' + bad.to);
verdict((bad.legalActions || []).indexOf('PR') < 0 && (bad.legalActions || []).length > 0, 'the legal set is echoed back (PR not in it)', (bad.legalActions || []).join(','));

// ── §DOCACTION-BUILDOP ──────────────────────────────────────────────────────────────────────────────────
console.log('\n§DOCACTION-BUILDOP — buildOp emits the chosen DOC_ACTION op the kernel would apply');
var op = CORE.buildOp('process', entry, {}, null, Object.assign({ chosen: 'RC', from: 'CO', id: 5001 }, fsmCtx));
console.log('   op: ' + op.op_type + ' action=' + op.action + ' ' + op.from + '→' + op.to + ' reversal=' + op.reversal);
verdict(op.op_type === 'DOC_ACTION' && op.action === 'RC' && op.to === 'RE' && op.reversal === true, 'DOC_ACTION op carries action=RC to=RE reversal=true', op.action + '/' + op.to);

// ── §FALSIFIER ──────────────────────────────────────────────────────────────────────────────────────────
console.log('\n§FALSIFIER — with NO fsm/chosen opts the LEGACY hardcoded-CO path is unchanged (opt-in bridge)');
var legacy = CORE.docActionOutcome(entry, { c_bpartner_id: 1, grandtotal: 10 });
var legacyUnmet = CORE.docActionOutcome(entry, {});
console.log('   legacy met → action=' + legacy.action + ' to=' + legacy.to + ' (' + legacy.outcome + ')   legacy unmet → to=' + legacyUnmet.to + ' unmet=' + JSON.stringify(legacyUnmet.unmet));
verdict(legacy.action === 'CO' && legacy.to === 'CO' && legacy.outcome === 'success', 'no-opts → legacy CO success (backward compatible)', legacy.action + '/' + legacy.to);
verdict(legacyUnmet.to === 'IP' && legacyUnmet.unmet.length === 2 && legacy.reversal === undefined, 'no-opts → legacy requires-gate (IP), no reversal flag', 'unmet=' + legacyUnmet.unmet.length);

console.log('\n' + (fails === 0 ? '🟢 W-DOCACTION-FULL PASS — CORE surfaces the full FSM action set per record; RC/RA post a reversal, never a silent un-complete; legacy CO path intact'
                                : '🔴 W-DOCACTION-FULL FAIL — ' + fails + ' check(s) failed'));
process.exit(fails === 0 ? 0 : 1);
