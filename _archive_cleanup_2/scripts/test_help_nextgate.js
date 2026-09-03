// Implementing READSHOWME_DYNAMIC_SPEC.md §Next-gated-on-success — Witness: W-HELP-NEXTGATE (GP2)
// Proves/disproves the GUIDE's Next-gate as a PURE decision wired to the REAL CRUD outcome — the two
// overlays' actual contract (CRUD derives docstatus; the guide gates Next on it, reading the live value):
//  (1) met-requires  → CRUD docActionOutcome to=CO → nextGate advances (Y) + the timeline tag may fire.
//  (2) unmet-requires→ CRUD docActionOutcome to=IP → nextGate HOLDS (N): re-highlight, no advance, no tag.
//  (3) exception     → nextGate action=errorReport, advanced=N (hand to the single ErrorReport class).
//  (4) INVARIANT: nextGate advances ONLY when live === expected success (CO); CL/none/unknown all HOLD.
// §-log first: writes build/erp/help_nextgate_witness.log; READ the log, not the exit code.
'use strict';
var COACH = require('../build/erp/help_overlay.js');     // node → module.exports = COACH (incl. nextGate)
var CORE = require('../build/erp/crud_overlay.js');       // node → module.exports = CORE (incl. docActionOutcome)
var STORE = require('../build/erp/crud_ops.json');
var fs = require('fs');

var lines = [], pass = 0, fail = 0;
function L(s) { lines.push(s); }
function ck(c, m) { if (c) { pass++; L('§HELP-NEXTGATE PASS ' + m); } else { fail++; L('§HELP-NEXTGATE FAIL ' + m); } }

var entry = Object.assign({}, STORE.c_order, { key: 'c_order' });
var reqs = (entry.docAction && entry.docAction.requires) || [];
ck(reqs.length >= 2, 'c_order has ≥2 requires to exercise met/unmet [' + reqs.join('+') + ']');

// (1) met → CO → advance. Build values with EVERY required col present (non-invent: derived from descriptor).
var metVals = {}; reqs.forEach(function (c, i) { metVals[c] = (c === 'grandtotal' ? 100.70 : 100 + i); });
var metOut = CORE.docActionOutcome(entry, metVals);
ck(metOut && metOut.to === 'CO', 'CRUD: met-requires derives to=CO (outcome=' + (metOut && metOut.outcome) + ')');
var metGate = COACH.nextGate(metOut.to, 'CO');
ck(metGate.advanced === true && metGate.action === 'advance', 'guide: CO → advance');
L('§HELP next gate docstatus=' + metGate.gate + ' advanced=' + (metGate.advanced ? 'Y' : 'N'));   // ← GP2 witness line

// (2) unmet → IP → hold. Drop one required col (leave it empty).
var unmetVals = {}; reqs.forEach(function (c, i) { if (i > 0) unmetVals[c] = 100 + i; });   // first req omitted
var unmetOut = CORE.docActionOutcome(entry, unmetVals);
ck(unmetOut && unmetOut.to === 'IP', 'CRUD: unmet-requires derives to=IP (unmet=' + (unmetOut && unmetOut.unmet.join(',')) + ')');
var unmetGate = COACH.nextGate(unmetOut.to, 'CO');
ck(unmetGate.advanced === false && unmetGate.action === 'hold', 'guide: IP → hold (no advance, no tag)');
L('§HELP next gate docstatus=' + unmetGate.gate + ' advanced=' + (unmetGate.advanced ? 'Y' : 'N'));   // ← GP2 witness line

// (3) exception → ErrorReport (no advance, no tag)
var exGate = COACH.nextGate('exception', 'CO');
ck(exGate.advanced === false && exGate.action === 'errorReport', 'guide: exception → errorReport (no advance)');

// (4) INVARIANT — advance ONLY on live === expected success
ck(COACH.nextGate('CO', 'CO').advanced === true, 'invariant: CO==expect advances');
ck(COACH.nextGate('CL', 'CO').advanced === false, 'invariant: CL (other) holds');
ck(COACH.nextGate('', 'CO').advanced === false, 'invariant: none/empty holds (not yet processed)');
ck(COACH.nextGate('IP', 'CO').advanced === false, 'invariant: IP holds');
ck(['CO', 'CL', 'IP', '', 'DR', 'exception'].every(function (s) { var g = COACH.nextGate(s, 'CO'); return g.advanced === (s === 'CO'); }),
   'invariant: advanced===true IFF live===expected success, else false');

L('§HELP-NEXTGATE SUMMARY pass=' + pass + ' fail=' + fail);
fs.writeFileSync(__dirname + '/../build/erp/help_nextgate_witness.log', lines.join('\n') + '\n');
console.log(lines.join('\n'));
process.exit(fail === 0 ? 0 : 1);
