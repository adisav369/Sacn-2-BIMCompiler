// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// Implementing CRUD_OVERLAY.md §Process — Witness: W-CRUD-PROCESS
// Proves/disproves: (1) the `process` verb derives docstatus CO when every docAction.requires col is
// present, IP (in-progress, unsatisfied condition) when one is empty; (2) c_allocationline is Process
// N/A (no verb, no docAction, no outcome); (3) NON-INVENT — to/from/outcome derive ONLY from the keyed
// descriptor + presence of values, nothing fabricated; requires ⊆ the entry's own fields[] columns.
// §-log first: writes build/erp/crud_process_witness.log; READ the log, not the exit code.
'use strict';
var CORE = require('../build/erp/crud_overlay.js');     // node path → module.exports = CORE
var STORE = require('../build/erp/crud_ops.json');
var fs = require('fs');

var lines = [], pass = 0, fail = 0;
function log(s) { lines.push(s); }
function check(cond, msg) { if (cond) { pass++; log('§CRUD-PROC PASS ' + msg); } else { fail++; log('§CRUD-PROC FAIL ' + msg); } }

var PROC = ['c_order', 'm_inout', 'c_invoice', 'c_payment'];

PROC.forEach(function (k) {
  var e = Object.assign({}, STORE[k], { key: k });
  var reqs = (e.docAction && e.docAction.requires) || [];
  var cols = (e.fields || []).map(function (f) { return f.col; });

  check((e.verbs || []).indexOf('process') >= 0, k + ' has process verb');
  check(!!e.docAction, k + ' has docAction descriptor (oracle=' + (e.docAction && e.docAction.oracle) + ')');
  check(reqs.length > 0 && reqs.every(function (c) { return cols.indexOf(c) >= 0; }), k + ' requires ⊆ own fields cols [' + reqs.join('+') + ']');

  // all requires present → CO / success
  var full = {}; (e.fields || []).forEach(function (f) { full[f.col] = (f.type === 'number' || f.type === 'fk') ? 1 : '2026-01-01'; });
  var opCO = CORE.buildOp('process', e, full, full, { id: 1 });
  check(opCO.op_type === 'DOC_ACTION' && opCO.to === 'CO' && opCO.outcome === 'success',
    k + ' all-requires-met → DOC_ACTION CO/success [from=' + opCO.from + ' to=' + opCO.to + ']');

  // one required col emptied → IP / in-progress, and it is named in unmet
  var partial = Object.assign({}, full); partial[reqs[0]] = '';
  var opIP = CORE.buildOp('process', e, partial, partial, { id: 1 });
  check(opIP.to === 'IP' && opIP.outcome === 'in-progress' && opIP.unmet.indexOf(reqs[0]) >= 0,
    k + ' missing ' + reqs[0] + ' → IP/in-progress unmet=[' + opIP.unmet.join(',') + ']');

  // non-invent: from comes ONLY from the descriptor (default DR), not fabricated
  check(opCO.from === (e.docAction.from || 'DR'), k + ' from derives from descriptor (non-invent)');
});

// c_allocationline — Process N/A (reconciliation line, no docstatus, no completeIt)
var al = Object.assign({}, STORE['c_allocationline'], { key: 'c_allocationline' });
check((al.verbs || []).indexOf('process') < 0, 'c_allocationline has NO process verb (N/A)');
check(!al.docAction, 'c_allocationline has NO docAction (N/A)');
check(CORE.docActionOutcome(al, {}) === null, 'c_allocationline docActionOutcome=null (no lifecycle)');

log('§CRUD-PROC SUMMARY pass=' + pass + ' fail=' + fail);
fs.writeFileSync(__dirname + '/../build/erp/crud_process_witness.log', lines.join('\n') + '\n');
console.log(lines.join('\n'));
process.exit(fail === 0 ? 0 : 1);
