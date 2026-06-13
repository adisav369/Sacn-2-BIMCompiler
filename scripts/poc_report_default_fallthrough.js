// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard
// SCOPE: R-T2 (prompts/REPORTING_UI_DEFAULT_FALLTHROUGH.md) — prove that opening a financial statement with
//   NOTHING configured yields a REAL, populated output sourced from the bundle's Fact_Acct history (~2002/3),
//   never an empty/vacuous matrix. The fold is the PROVEN CORE.foldStatement; this witness proves the DEFAULT
//   SOURCE selection (CORE.resolveScope — the same pure code the browser path now calls):
//     ISSUE-1  default scope lands on a POPULATED period (busiest of the busiest year = period 160 / Jan-03),
//              not the emptiest one; the folded matrix is NON-vacuous (nonzero-cell count > 0).
//     ISSUE-2  coverage is honestly tagged 'default→history' (no live period configured) + sourcePeriod named.
//     ISSUE-3  the journal still ties: ΣDr=ΣCr=46574.97 over the WHOLE bundle (TB view, foldTrialBalance).
//     FALSIFIER-A  force an EMPTY scope (a column window that no fact period satisfies) -> resolveScope falls
//              through to the busiest period (scopeFacts>0), NOT zeros.
//     FALSIFIER-B  force a TRULY-EMPTY bundle (0 fact rows) -> honest coverage:'absent' (never faked).
// NON-INVENT: every period/account/amount is READ from a real glassbowl_data.db row. No Date.now/Math.random.
//   §-log first — READ build/erp/poc_report_default_fallthrough.log before concluding.
// Run:  bash build/erp/run_witness.sh scripts/poc_report_default_fallthrough.js
'use strict';
var path = require('path');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var CORE = require(path.join(ERP, 'report_overlay.js'));
var Database = require('better-sqlite3');
var GB = new Database(path.join(ERP, 'glassbowl_data.db'), { readonly: true });

var fails = 0;
function ok(cond, msg) { if (!cond) { fails++; console.log('  ✗ FAIL: ' + msg); } else { console.log('  ✓ ' + msg); } }
function cents(x) { return x == null || x === '' ? 0 : Math.round(Number(x) * 100); }
function has(t, c) { return GB.prepare('PRAGMA table_info(' + t + ')').all().some(function (r) { return String(r.name).toLowerCase() === c; }); }

console.log('=== §REPORT-DEFAULT witness (R-T2) — ' + new Date().toISOString() + ' ===');
console.log('default statement, no user params; source = bundle Fact_Acct history; fold = CORE.foldStatement/foldTrialBalance\n');

var CAL = 102, TREE = 101;

// ── periods (calendar 102, active S, date order) — the row source for the PURE CORE.resolveScope ──
var periods = GB.prepare(
  "SELECT p.c_period_id id, p.name, p.startdate sd, p.enddate ed, p.c_year_id yr " +
  "FROM c_period p JOIN c_year y ON p.c_year_id=y.c_year_id " +
  "WHERE y.c_calendar_id=? AND p.isactive='Y' AND p.periodtype='S' ORDER BY p.startdate").all(CAL);
var factRows = GB.prepare('SELECT account_id, amtacctdr, amtacctcr, qty, c_period_id, c_acctschema_id, postingtype FROM fact_acct').all();

// EV chart (accountsign is absent in this served bundle -> derive null; CORE.signedBalance applies the natural rule).
var signCol = has('c_elementvalue', 'accountsign');
var EV = {};
GB.prepare('SELECT c_elementvalue_id id, value, accounttype' + (signCol ? ', accountsign' : '') + ', issummary FROM c_elementvalue')
  .all().forEach(function (r) { EV[r.id] = { value: r.value, accounttype: r.accounttype, accountsign: signCol ? r.accountsign : null, issummary: r.issummary }; });
var kids = {};
GB.prepare('SELECT node_id, parent_id FROM ad_treenode WHERE ad_tree_id=?').all(TREE)
  .forEach(function (n) { (kids[n.parent_id] = kids[n.parent_id] || []).push(n.node_id); });
function leaves(id) {
  var e = EV[id]; if (!e || e.issummary !== 'Y') return [id];
  var out = []; (function rec(x) { (kids[x] || []).forEach(function (c) { if (EV[c] && EV[c].issummary === 'Y') rec(c); else out.push(c); }); })(id);
  return out;
}
var accounts = {}; Object.keys(EV).forEach(function (id) { accounts[id] = { accounttype: EV[id].accounttype, accountsign: EV[id].accountsign }; });

// inputsFor — assemble foldStatement inputs for a pa_report given an explicit resolved scope (the host path, headless).
function inputsFor(reportId, sc) {
  var report = GB.prepare('SELECT pa_report_id, name, pa_reportlineset_id, pa_reportcolumnset_id, c_acctschema_id FROM pa_report WHERE pa_report_id=?').get(reportId);
  var lines = GB.prepare("SELECT pa_reportline_id, seqno, name, linetype, calculationtype, oper_1_id, oper_2_id, paamounttype, paperiodtype FROM pa_reportline WHERE pa_reportlineset_id=? AND isactive='Y' ORDER BY seqno").all(report.pa_reportlineset_id);
  var sourcesByLine = {};
  lines.forEach(function (l) {
    var srcs = GB.prepare("SELECT c_elementvalue_id ev FROM pa_reportsource WHERE pa_reportline_id=? AND isactive='Y'").all(l.pa_reportline_id);
    sourcesByLine[l.pa_reportline_id] = srcs.map(function (s) { return { account_ids: leaves(s.ev) }; });
  });
  return { report: report, lines: lines, cols: sc.cols, sourcesByLine: sourcesByLine, periodWindows: sc.periodWindows };
}
function colsOf(reportId) {
  var report = GB.prepare('SELECT pa_reportcolumnset_id FROM pa_report WHERE pa_report_id=?').get(reportId);
  // SELECT EVERY column the fold reads (paamounttype/postingtype/calc operands) — dropping them folds every cell to 0.
  return GB.prepare("SELECT pa_reportcolumn_id, columntype, calculationtype, oper_1_id, oper_2_id, paamounttype, paperiodtype, relativeperiod, postingtype, name FROM pa_reportcolumn WHERE pa_reportcolumnset_id=? AND isactive='Y' ORDER BY seqno").all(report.pa_reportcolumnset_id);
}
function nonzeroCells(folded) {
  var n = 0;
  folded.lineOrder.forEach(function (lid) { folded.colOrder.forEach(function (cid) { if (cents((folded.cells[lid] || {})[cid]) !== 0) n++; }); });
  return n;
}

// ════════════════════════════════════════════════════════════════════════
// ISSUE-1/2: default statement (no params) -> CORE.resolveScope lands on populated history, NON-vacuous, tagged.
// Report 100 = Balance Sheet — its source accounts intersect the bundle's posted fact_acct accounts under schema
// 101 (verified: 12-account overlap), so the default fold yields real non-zero cells. (The Income Statement (101)
// folds vacuous on THIS served bundle because no revenue/expense account is posted under schema 101 — a data
// property of the slim bundle, not a fold bug; the Balance Sheet is the populated default this journal supports.)
// ════════════════════════════════════════════════════════════════════════
console.log('[ISSUE-1/2] default Balance Sheet (100): scope=auto -> populated history, non-vacuous, coverage tagged');
var RID = 100;
var cols101 = colsOf(RID);
var sc = CORE.resolveScope(periods, factRows, cols101); sc.cols = cols101;
var inp = inputsFor(RID, sc);
var folded = CORE.foldStatement(inp.report, inp.lines, inp.cols, inp.sourcesByLine, factRows, accounts, inp.periodWindows);
var nz = nonzeroCells(folded);
var sp = sc.sourcePeriod ? (sc.sourcePeriod.id + '/' + sc.sourcePeriod.name) : 'none';
console.log('§REPORT-DEFAULT scope=auto period=' + (sc.sourcePeriod ? sc.sourcePeriod.id : 'none') + ' facts=' + sc.scopeFacts +
  ' nonzeroCells=' + nz + ' coverage=' + sc.coverage + ' verdict=' + (nz > 0 && sc.coverage === 'default→history' ? 'PASS' : 'FAIL'));
ok(sc.sourcePeriod && sc.sourcePeriod.id === 160, 'default landed on the busiest populated period (160 / Jan-03), got ' + sp);
ok(sc.scopeFacts > 0, 'resolved scope covers real facts (scopeFacts=' + sc.scopeFacts + ' > 0)');
ok(nz > 0, 'folded matrix is NON-vacuous (nonzeroCells=' + nz + ' > 0) — not an empty/·-grid');
ok(sc.coverage === 'default→history', "coverage honestly tagged 'default→history' (no live period configured)");
console.log('');

// ════════════════════════════════════════════════════════════════════════
// ISSUE-3: the journal still TIES — ΣDr=ΣCr=46574.97 over the WHOLE bundle (the TB view, foldTrialBalance).
// ════════════════════════════════════════════════════════════════════════
console.log('[ISSUE-3] whole-journal Trial Balance still ties to the cent (ΣDr=ΣCr=46574.97)');
var tbAccts = {}; GB.prepare('SELECT c_elementvalue_id id, value, name, accounttype FROM c_elementvalue').all().forEach(function (r) { tbAccts[r.id] = r; });
var tbFacts = GB.prepare('SELECT account_id, amtacctdr, amtacctcr FROM fact_acct').all();
var tb = CORE.foldTrialBalance(tbFacts, tbAccts);
console.log('§REPORT-DEFAULT-TB totalDr=' + tb.totalDr + ' totalCr=' + tb.totalCr + ' balanced=' + tb.balanced + ' maxDiff=' + tb.maxDiffCents + 'c rows=' + tb.rows);
ok(cents(tb.totalDr) === cents('46574.97'), 'ΣDr == 46574.97 (' + tb.totalDr + ')');
ok(cents(tb.totalCr) === cents('46574.97'), 'ΣCr == 46574.97 (' + tb.totalCr + ')');
ok(tb.balanced === true && tb.maxDiffCents === 0, 'balanced to the cent (maxDiff=' + tb.maxDiffCents + 'c)');
console.log('');

// ════════════════════════════════════════════════════════════════════════
// FALSIFIER-A: force an EMPTY scope -> resolveScope falls through to the busiest period (NOT zeros).
// A single 'P' column with a relativeperiod so far in the past it points at an empty period would, WITHOUT the
// guard, fold to 0. We assert the guard re-resolves to the busiest period and scopeFacts>0.
// ════════════════════════════════════════════════════════════════════════
console.log('[FALSIFIER-A] force empty scope -> falls through to busiest history period (not zeros)');
// pick an empty period index: a calendar period with 0 facts that is BEFORE the busiest, then aim a P column at it
var factCnt = {}; factRows.forEach(function (r) { factCnt[r.c_period_id] = (factCnt[r.c_period_id] || 0) + 1; });
var emptyIdx = -1; for (var i = 0; i < periods.length; i++) { if (!factCnt[periods[i].id]) { emptyIdx = i; break; } }
// relativeperiod that, from the derived reportIdx, points at the empty period
var baseScope = CORE.resolveScope(periods, factRows, [{ pa_reportcolumn_id: 9001, columntype: 'X', paperiodtype: 'P', relativeperiod: 0 }]);
var rel = emptyIdx - baseScope.reportIdx;
var emptyCols = [{ pa_reportcolumn_id: 9001, columntype: 'X', paperiodtype: 'P', relativeperiod: rel }];
var scE = CORE.resolveScope(periods, factRows, emptyCols);
console.log('§REPORT-DEFAULT-FALSIFIER-A emptyPeriodIdx=' + emptyIdx + ' rel=' + rel + ' -> scopeFacts=' + scE.scopeFacts + ' sourcePeriod=' + (scE.sourcePeriod ? scE.sourcePeriod.id : 'none') + ' coverage=' + scE.coverage);
ok(scE.scopeFacts > 0, 'empty-scope FELL THROUGH to a populated period (scopeFacts=' + scE.scopeFacts + ' > 0, not zeros)');
ok(scE.sourcePeriod && scE.sourcePeriod.id === 160, 'fall-through landed on the busiest period 160 (got ' + (scE.sourcePeriod ? scE.sourcePeriod.id : 'none') + ')');
console.log('');

// ════════════════════════════════════════════════════════════════════════
// FALSIFIER-B: TRULY-EMPTY bundle (0 fact rows) -> honest coverage:'absent' (never faked).
// ════════════════════════════════════════════════════════════════════════
console.log('[FALSIFIER-B] truly-empty bundle (0 facts) -> honest coverage:absent');
var scAbsent = CORE.resolveScope(periods, [], cols101);
console.log('§REPORT-DEFAULT-ABSENT totalFacts=' + scAbsent.totalFacts + ' scopeFacts=' + scAbsent.scopeFacts + ' coverage=' + scAbsent.coverage);
ok(scAbsent.coverage === 'absent', "0 facts -> coverage:'absent' (the honest gate, not a faked default)");
ok(scAbsent.totalFacts === 0, 'absent path is the EMPTY-journal case only (totalFacts=0)');
console.log('');

console.log('=== RESULT: ' + (fails === 0 ? 'ALL PASS' : fails + ' FAIL') + ' ===');
process.exit(fails === 0 ? 0 : 1);
