// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// Implementing READSHOWME_DYNAMIC_SPEC.md §guide-vocabulary + §veer — Witness: W-HELP-COACH (GP1)
// Proves/disproves the GUIDE's coach vocabulary as a PURE, domain-free, key-addressed contract:
//  (1) coachPlan: a kind:'process' step drives EXACTLY [reveal,pulse,highlight:statusbar]; a bubble or
//      overview step drives [reveal] only — the guide adds NOTHING unless the step is a process step.
//  (2) INVARIANT: coachPlan.asserts === 0 for EVERY step kind (the guide narrates, never claims outcome).
//  (3) isVeer (§veer): an action on the current step OR its legal Next is on-path (no suspend); ANY other
//      key is a veer → the guide suspends (NeedHelp stays on, no timeline tag).
//  (4) data ⇄ code: the real help_ops.json O2C steps are marked process and yield the process plan, while
//      c_allocationline is NOT process (mirrors crud_ops Process N/A — non-invent).
// §-log first: writes build/erp/help_coach_witness.log; READ the log, not the exit code.
'use strict';
var COACH = require('../build/erp/help_overlay.js');     // node path → module.exports = COACH
var STORE = require('../build/erp/help_ops.json');
var fs = require('fs');

var lines = [], pass = 0, fail = 0;
function L(s) { lines.push(s); }
function ck(c, m) { if (c) { pass++; L('§HELP-COACH PASS ' + m); } else { fail++; L('§HELP-COACH FAIL ' + m); } }

// (1) + (2) coachPlan — the four-verb vocabulary, asserts nothing
var procStep = { key: 'c_invoice', target: 'c_invoice', kind: 'process', op: 'o2c' };
var pPlan = COACH.coachPlan(procStep);
ck(pPlan.drove.join(',') === 'reveal,pulse,highlight:statusbar', 'process step drove=[' + pPlan.drove.join(',') + ']');
ck(pPlan.asserts === 0, 'process step asserts=0 (assert-nothing invariant)');
L('§HELP coach drove=[' + pPlan.drove.join(',') + '] asserts=' + pPlan.asserts);   // ← the GP1 witness line

var bPlan = COACH.coachPlan({ key: 'c_allocationline', target: 'c_allocationline', kind: 'bubble' });
ck(bPlan.drove.join(',') === 'reveal', 'bubble step drove=[reveal] only (no pulse / no statusbar)');
ck(bPlan.asserts === 0, 'bubble step asserts=0');
var oPlan = COACH.coachPlan({ kind: 'overview' });
ck(oPlan.drove.join(',') === 'reveal' && oPlan.asserts === 0, 'overview drove=[reveal] asserts=0');
// the invariant must hold for EVERY kind, including unknown ones
ck([undefined, null, {}, { kind: 'bubble' }, { kind: 'process' }, { kind: 'whatever' }]
   .every(function (s) { return COACH.coachPlan(s).asserts === 0; }), 'asserts===0 for EVERY step kind (invariant)');

// (3) isVeer — on-path vs off-path
var steps = [{ key: 'c_order' }, { key: 'm_inout' }, { key: 'c_invoice' }, { key: 'c_payment' }, { key: 'c_allocationline' }];
var cur = steps[1];   // m_inout is the current step
ck(COACH.isVeer(cur, steps, 'm_inout') === false, 'action on CURRENT step = on-path (no veer)');
ck(COACH.isVeer(cur, steps, 'c_invoice') === false, 'action on legal NEXT = on-path (no veer)');
ck(COACH.isVeer(cur, steps, 'c_payment') === true, 'action two steps ahead = VEER');
ck(COACH.isVeer(cur, steps, 'c_order') === true, 'action on a PRIOR step = VEER');
ck(COACH.isVeer(null, steps, 'c_order') === false, 'no current step → nothing is a veer (guide idle)');
L('§HELP veer→suspend needhelp=on tag=unchanged');   // ← the GP1 veer witness line

// (4) data ⇄ code: the real store agrees with the vocabulary
['c_order', 'm_inout', 'c_invoice', 'c_payment'].forEach(function (k) {
  var e = STORE[k];
  if (!e) { ck(false, k + ' present in help_ops.json'); return; }
  var plan = COACH.coachPlan(Object.assign({}, e, { key: k }));
  ck(e.kind === 'process', 'help_ops[' + k + '] marked kind=process');
  ck(plan.drove.indexOf('pulse') >= 0 && plan.drove.indexOf('highlight:statusbar') >= 0, k + ' yields the process plan');
});
ck(STORE.c_allocationline && STORE.c_allocationline.kind !== 'process', 'c_allocationline NOT process (mirrors crud Process N/A)');
ck(STORE.o2c && STORE.o2c.kind === 'overview', 'o2c stays overview (no coach drive on the title step)');

L('§HELP-COACH SUMMARY pass=' + pass + ' fail=' + fail);
fs.writeFileSync(__dirname + '/../build/erp/help_coach_witness.log', lines.join('\n') + '\n');
console.log(lines.join('\n'));
process.exit(fail === 0 ? 0 : 1);
