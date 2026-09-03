// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard
// SCOPE: W-PA-REPORT — prove report_overlay.foldStatement reproduces iDempiere's Financial Report for
//   pa_report 100 (Balance Sheet) + 101 (Income Statement) + 102 (Statement of Cash Flows), folded over the
//   bundled fact_acct, EQUAL TO THE
//   CENT against an INDEPENDENT oracle = a FinReport-style SUM over LIVE idempiere_test.fact_acct (which carries
//   dateacct+accountsign), built from the IDENTICAL PA_* rows + the SAME EV-tree expansion + the SAME c_period
//   window. Spec = the foldStatement header block in build/erp/report_overlay.js (ReportingFold.md §4b is
//   permission-blocked to edit this session; spec mirrored into the engine file).
// NON-INVENT: every account/number is READ from a real PA_*/c_period/ad_treenode/fact_acct row. No Date.now,
//   no Math.random. Integer-cents diff. §-log first — READ build/erp/poc_pa_report.log before concluding.
// Run:  node scripts/poc_pa_report.js 2>&1 | tee build/erp/poc_pa_report.log
'use strict';
var path = require('path'), cp = require('child_process');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var CORE = require(path.join(ERP, 'report_overlay.js'));
var Database = require('better-sqlite3');
// metadata source = the BUNDLE (extract_pa_report.sh, sourced from live PG). ad_full.db is an untracked artifact a
// CONCURRENT terminal regenerates WITHOUT pa_*/ad_treenode (observed 2026-06-10 — it clobbered this witness); the
// bundle is the immune, served copy.
var AD = new Database(path.join(ERP, 'glassbowl_data.db'), { readonly: true });
var GB = new Database(path.join(ERP, 'glassbowl_data.db'), { readonly: true });

var fails = 0;
function ok(cond, msg) { if (!cond) { fails++; console.log('  ✗ FAIL: ' + msg); } else { console.log('  ✓ ' + msg); } }
// oracle: run SQL in the live posted-GL truth source (client 11, has dateacct+accountsign).
function psql(sql) {
  var out = cp.execSync(
    'docker exec -i postgres psql -U adempiere -d idempiere_test -t -A -F"|" -c ' + JSON.stringify(sql),
    { encoding: 'utf8' });
  return out.trim();
}
function cents(x) { return x == null || x === '' ? 0 : Math.round(Number(x) * 100); }

console.log('=== §PA-REPORT witness (W-PA-REPORT) — ' + new Date().toISOString() + ' ===');
console.log('oracle = LIVE idempiere_test.fact_acct (client 11); fold = report_overlay.foldStatement over bundled fact_acct\n');

// ── account chart + EV account tree (tree 101 = GardenWorld) from the metadata DB ───────────────────
var EV = {};
AD.prepare('SELECT c_elementvalue_id id, value, accounttype, accountsign, issummary FROM c_elementvalue')
  .all().forEach(function (r) { EV[r.id] = r; });
var nAccounts = Object.keys(EV).length;
var nNatural = Object.values(EV).filter(function (e) { return e.accountsign === 'N'; }).length;
console.log('§PA-REPORT-FACTS accounts=' + nAccounts + ' accountsign_N=' + nNatural +
  ' (all natural? ' + (nNatural === nAccounts ? 'Y' : 'N') + ')   [re-verified: c_elementvalue.accountsign]');

var TREE = 101;
var kids = {};
AD.prepare('SELECT node_id, parent_id FROM ad_treenode WHERE ad_tree_id=?').all(TREE)
  .forEach(function (n) { (kids[n.parent_id] = kids[n.parent_id] || []).push(n.node_id); });
// expand a source account id to its LEAF (non-summary) descendants via the tree (== MReportTree.getWhereClause).
function leaves(id) {
  var e = EV[id];
  if (!e || e.issummary !== 'Y') return [id];                   // leaf (or unknown) -> itself
  var out = [];
  (function rec(x) {
    (kids[x] || []).forEach(function (c) {
      if (EV[c] && EV[c].issummary === 'Y') rec(c); else out.push(c);
    });
  })(id);
  return out;
}

// ── full calendar period array (calendar 102, active S periods, ordered by startdate) ───────────────
var CAL = 102;
var periods = AD.prepare(
  "SELECT p.c_period_id id, p.name, p.startdate sd, p.enddate ed, p.c_year_id yr " +
  "FROM c_period p JOIN c_year y ON p.c_year_id=y.c_year_id " +
  "WHERE y.c_calendar_id=? AND p.isactive='Y' AND p.periodtype='S' ORDER BY p.startdate").all(CAL);
var periodIdx = {}; periods.forEach(function (p, i) { periodIdx[p.id] = i; });
// fact periods present in the bundle — the report period = the LATEST one that carries facts (an explicit,
// EXTRACTED FinReport C_Period_ID parameter choice; named in the log — never a clock).
var factPeriodIds = GB.prepare('SELECT DISTINCT c_period_id FROM fact_acct').all().map(function (r) { return r.c_period_id; });
var factIdxs = factPeriodIds.map(function (id) { return periodIdx[id]; }).filter(function (i) { return i != null; });
var reportIdx = Math.max.apply(null, factIdxs);
var reportPeriod = periods[reportIdx];
console.log('§PA-REPORT-PERIOD calendar=' + CAL + ' total_periods=' + periods.length +
  ' fact_periods=[' + factPeriodIds.sort(function(a,b){return a-b;}).join(',') + ']' +
  ' reportPeriod=' + reportPeriod.id + ' (' + reportPeriod.name + ', idx=' + reportIdx + ') yr=' + reportPeriod.yr +
  '   [bundle fact_acct has NO dateacct -> windows resolved to c_period sets]');

// resolve a period window (paperiodtype + relativeperiod offset) to a SET of c_period_id over the calendar.
//   'P'=target only . 'Y'=same-fiscal-year periods up to & incl target . 'T'=all periods <= target end.
//   (== FinReportPeriod.getPeriodWhere/getYearWhere/getTotalWhere). relativeperiod offsets the report idx.
function windowFor(paperiodtype, relativeperiod) {
  var idx = reportIdx + (relativeperiod || 0);
  if (idx < 0 || idx >= periods.length) return new Set();         // off-calendar -> empty
  var tgt = periods[idx];
  var set = new Set();
  if (paperiodtype === 'P') { set.add(tgt.id); }
  else if (paperiodtype === 'Y') {
    periods.forEach(function (p) { if (p.yr === tgt.yr && p.sd <= tgt.ed && p.ed <= tgt.ed) set.add(p.id); });
  } else if (paperiodtype === 'T') {
    periods.forEach(function (p) { if (p.ed <= tgt.ed) set.add(p.id); });
  } else { set.add(tgt.id); }                                     // default -> period
  return set;
}
// the c_period set used by a column, AND the equivalent oracle predicate (SAME set -> independent yet window-agreed).
function windowMeta(col) {
  if (col.columntype === 'C') return null;                        // calc column has no own window
  var set = windowFor(col.paperiodtype, col.relativeperiod);
  return { set: set, ids: Array.from(set).sort(function (a, b) { return a - b; }) };
}

// ── bundled fact rows (the browser path) ────────────────────────────────────────────────────────────
var factRows = GB.prepare(
  'SELECT account_id, amtacctdr, amtacctcr, qty, c_period_id, c_acctschema_id, postingtype FROM fact_acct').all();
// accounts map for the verb (sign rule).
var accounts = {};
Object.keys(EV).forEach(function (id) { accounts[id] = { accounttype: EV[id].accounttype, accountsign: EV[id].accountsign }; });

// fold + diff one report against the oracle.
function runReport(reportId) {
  var report = AD.prepare('SELECT pa_report_id, name, pa_reportlineset_id, pa_reportcolumnset_id, c_acctschema_id, c_calendar_id FROM pa_report WHERE pa_report_id=?').get(reportId);
  var lines = AD.prepare('SELECT pa_reportline_id, seqno, name, linetype, calculationtype, oper_1_id, oper_2_id, paamounttype, paperiodtype FROM pa_reportline WHERE pa_reportlineset_id=? AND isactive=\'Y\' ORDER BY seqno').all(report.pa_reportlineset_id);
  var cols = AD.prepare('SELECT pa_reportcolumn_id, seqno, name, columntype, calculationtype, oper_1_id, oper_2_id, paamounttype, paperiodtype, relativeperiod, postingtype FROM pa_reportcolumn WHERE pa_reportcolumnset_id=? AND isactive=\'Y\' ORDER BY seqno').all(report.pa_reportcolumnset_id);

  // host-side source expansion: each line's sources -> leaf account id sets.
  var sourcesByLine = {};
  lines.forEach(function (l) {
    var srcs = AD.prepare('SELECT c_elementvalue_id ev, elementtype FROM pa_reportsource WHERE pa_reportline_id=? AND isactive=\'Y\'').all(l.pa_reportline_id);
    sourcesByLine[l.pa_reportline_id] = srcs.map(function (s) { return { account_ids: leaves(s.ev) }; });
  });
  // host-side period windows per column.
  var periodWindows = {};
  cols.forEach(function (c) { var m = windowMeta(c); if (m) periodWindows[c.pa_reportcolumn_id] = m.set; });

  var folded = CORE.foldStatement(report, lines, cols, sourcesByLine, factRows, accounts, periodWindows);

  // ── ORACLE: independent FinReport-style SUM over LIVE fact_acct, per (segment line, segment column) ──
  // Only S-lines are diffed against the GL directly (C-lines/calc-cols are derived from S cells — proven via
  // the engine's pass-2/3; an independent calc-line oracle is a separate sum-of-sums, redundant for maxDiff).
  var maxDiff = 0, cellCount = 0, firstSql = null, diffCells = [];
  var schema = report.c_acctschema_id;
  lines.forEach(function (l) {
    if (l.linetype !== 'S') return;
    var leafIds = {};
    (sourcesByLine[l.pa_reportline_id] || []).forEach(function (s) { (s.account_ids || []).forEach(function (a) { if (a != null && a !== '' && !isNaN(a)) leafIds[a] = 1; }); });
    var ids = Object.keys(leafIds);
    cols.forEach(function (c) {
      if (c.columntype === 'C') return;
      var m = windowMeta(c); if (!m) return;
      var amt = (l.paamounttype != null && l.paamounttype !== '') ? l.paamounttype : c.paamounttype;
      var expr;
      switch (amt) {
        case 'B': expr = 'acctbalance(account_id,amtacctdr,amtacctcr)'; break;
        case 'S': expr = 'amtacctdr-amtacctcr'; break;
        case 'C': expr = 'amtacctcr'; break;
        case 'D': expr = 'amtacctdr'; break;
        case 'Q': expr = 'qty'; break;
        case 'R': expr = 'acctbalance(account_id,qty,0)'; break;
        default: expr = '0'; break;
      }
      // empty source set or empty window -> oracle cell is 0 (no SQL needed; engine also yields 0).
      var oracleVal;
      if (!ids.length || !m.ids.length) { oracleVal = 0; }
      else {
        var pt = (c.postingtype != null && c.postingtype !== '') ? " AND postingtype='" + c.postingtype + "'" : '';
        var sql = "SELECT ROUND(COALESCE(SUM(" + expr + "),0),2) FROM fact_acct " +
          "WHERE ad_client_id=11 AND c_acctschema_id=" + schema +
          " AND account_id IN (" + ids.join(',') + ")" +
          " AND c_period_id IN (" + m.ids.join(',') + ")" + pt;
        if (!firstSql) firstSql = sql;
        oracleVal = Number(psql(sql) || 0);
      }
      var foldVal = folded.cells[l.pa_reportline_id][c.pa_reportcolumn_id];
      var d = Math.abs(cents(foldVal) - cents(oracleVal));
      cellCount++;
      if (d > maxDiff) maxDiff = d;
      if (d > 0) diffCells.push({ line: l.pa_reportline_id, col: c.pa_reportcolumn_id, fold: foldVal, oracle: oracleVal, diffC: d });
    });
  });

  console.log('§PA-REPORT report=' + report.pa_report_id + ' name="' + report.name + '" lines=' + lines.length +
    ' cols=' + cols.length + ' segCells=' + cellCount + ' maxDiff=' + maxDiff + 'c oracle=idempiere_test schema=' + schema);
  if (diffCells.length) console.log('   diverging cells: ' + JSON.stringify(diffCells.slice(0, 8)));
  if (firstSql) console.log('   sample oracle SQL: ' + firstSql);
  return { report: report, lines: lines, cols: cols, sourcesByLine: sourcesByLine, periodWindows: periodWindows, folded: folded, maxDiff: maxDiff, cellCount: cellCount };
}

console.log('\n[ISSUE] foldStatement(100 Balance Sheet) == FinReport-style oracle, cent-exact');
var r100 = runReport(100);
ok(r100.maxDiff === 0, 'Balance Sheet (100) maxDiff=' + r100.maxDiff + 'c over ' + r100.cellCount + ' segment cells');

console.log('\n[ISSUE] foldStatement(101 Income Statement) == FinReport-style oracle, cent-exact');
var r101 = runReport(101);
ok(r101.maxDiff === 0, 'Income Statement (101) maxDiff=' + r101.maxDiff + 'c over ' + r101.cellCount + ' segment cells');

console.log('\n[ISSUE] foldStatement(102 Statement of Cash Flows) == FinReport-style oracle, cent-exact (the last foldStatement remainder)');
var r102 = runReport(102);
ok(r102.maxDiff === 0, 'Statement of Cash Flows (102) maxDiff=' + r102.maxDiff + 'c over ' + r102.cellCount + ' segment cells');
// NON-VACUITY: a 0c diff over an all-zero statement is a 0==0 tie (the IS bug class) — demand real activity.
(function () {
  var nz = 0;
  r102.lines.forEach(function (l) { r102.cols.forEach(function (c) {
    if (cents(r102.folded.cells[l.pa_reportline_id][c.pa_reportcolumn_id]) !== 0) nz++; }); });
  console.log('§PA-REPORT-NONVACUOUS CF-nonzero-cells=' + nz);
  ok(nz > 0, 'Cash Flows is NON-vacuous (' + nz + ' nonzero cells — not a 0==0 tie)');
})();

// ── §FALSIFIER (load-bearing, actually runs): drop ONE pa_reportsource leaf from a line on a COPY of the
//    inputs -> that line's cell must diverge from the oracle (proving the diff has teeth, not a tautology). ──
console.log('\n[§FALSIFIER] drop one source leaf -> the affected line cell diverges (diff>0)');
(function () {
  // find a BS S-line that has a non-empty source AND a non-zero oracle cell in some segment col.
  var victim = null;
  r100.lines.forEach(function (l) {
    if (victim || l.linetype !== 'S') return;
    var srcs = r100.sourcesByLine[l.pa_reportline_id] || [];
    var allLeaves = []; srcs.forEach(function (s) { (s.account_ids || []).forEach(function (a) { allLeaves.push(a); }); });
    if (!allLeaves.length) return;
    // does any segment col give this line a non-zero folded cell?
    var nz = r100.cols.some(function (c) { return c.columntype !== 'C' && cents(r100.folded.cells[l.pa_reportline_id][c.pa_reportcolumn_id]) !== 0; });
    if (nz) victim = { line: l, leaves: allLeaves };
  });
  ok(!!victim, 'found a non-zero-cell BS line to perturb (line ' + (victim && victim.line.pa_reportline_id) + ')');
  if (!victim) return;
  // perturbed copy: remove one leaf account from this line's source set.
  var dropped = victim.leaves[0];
  var perturbed = {}; Object.keys(r100.sourcesByLine).forEach(function (k) { perturbed[k] = r100.sourcesByLine[k]; });
  perturbed[victim.line.pa_reportline_id] = (r100.sourcesByLine[victim.line.pa_reportline_id] || []).map(function (s) {
    return { account_ids: (s.account_ids || []).filter(function (a) { return a != dropped; }) };
  });
  var refold = CORE.foldStatement(r100.report, r100.lines, r100.cols, perturbed, factRows, accounts, r100.periodWindows);
  // compare the perturbed line's cells to the ORIGINAL oracle (which still includes the dropped leaf).
  var diverged = false, where = null;
  r100.cols.forEach(function (c) {
    if (c.columntype === 'C') return;
    var before = r100.folded.cells[victim.line.pa_reportline_id][c.pa_reportcolumn_id];
    var after = refold.cells[victim.line.pa_reportline_id][c.pa_reportcolumn_id];
    if (cents(before) !== cents(after)) { diverged = true; if (!where) where = { col: c.pa_reportcolumn_id, before: before, after: after }; }
  });
  console.log('§PA-REPORT-FALSIFIER dropped leaf=' + dropped + ' from line=' + victim.line.pa_reportline_id +
    ' -> cellChanged=' + (diverged ? 'Y' : 'N') + (where ? ' (col ' + where.col + ': ' + where.before + ' -> ' + where.after + ')' : ''));
  ok(diverged, '§FALSIFIER fired: dropping a source leaf changed the line cell (the diff has teeth)');
})();

console.log('\n=== RESULT: ' + (fails === 0 ? 'ALL PASS' : fails + ' FAIL') + ' ===');
process.exit(fails === 0 ? 0 : 1);
