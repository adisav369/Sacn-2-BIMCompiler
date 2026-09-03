// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard
// SCOPE: W-PA-REPORT-BROWSER — prove the BROWSER path of report_overlay.statement() works: fold pa_report 100
//   (Balance Sheet) + 101 (Income Statement) + 102 (Statement of Cash Flows) reading EVERY input from the
//   SERVED BUNDLE glassbowl_data.db ALONE
//   (pa_*, c_period/c_year, ad_treenode, c_elementvalue.accountsign, fact_acct — all loaded by
//   scripts/extract_pa_report.sh), and diff cent-exact vs the INDEPENDENT live idempiere_test oracle. This
//   replicates report_overlay.js statementInputs() verbatim, so a green run == the bundle carries what the app
//   needs and the in-app fold == iDempiere. Distinct from poc_pa_report.js, which sourced metadata from ad_full.db.
// NON-INVENT: every row READ from the bundle or the live oracle. No Date.now/Math.random. Integer-cents diff.
//   §-log first — READ build/erp/poc_statement_browser.log before concluding.
// Run:  node scripts/poc_statement_browser.js 2>&1 | tee build/erp/poc_statement_browser.log
'use strict';
var path = require('path'), cp = require('child_process');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var CORE = require(path.join(ERP, 'report_overlay.js'));
var Database = require('better-sqlite3');
var GB = new Database(path.join(ERP, 'glassbowl_data.db'), { readonly: true });   // the BUNDLE the browser fetches
var TREE = 101, CAL = 102;

var fails = 0;
function ok(cond, msg) { if (!cond) { fails++; console.log('  ✗ FAIL: ' + msg); } else { console.log('  ✓ ' + msg); } }
function psql(sql) {
  return cp.execSync('docker exec -i postgres psql -U adempiere -d idempiere_test -t -A -F"|" -c ' + JSON.stringify(sql), { encoding: 'utf8' }).trim();
}
function cents(x) { return x == null || x === '' ? 0 : Math.round(Number(x) * 100); }

console.log('=== §PA-REPORT-BROWSER witness — ' + new Date().toISOString() + ' ===');
console.log('inputs = glassbowl_data.db ONLY (the served bundle); oracle = LIVE idempiere_test.fact_acct (client 11)\n');

// ── 0. the bundle MUST carry the statement contract (the data gate this lane closed) ──
var tabs = GB.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('pa_report','pa_reportline','pa_reportcolumn','pa_reportsource','c_period','c_year','ad_treenode','fact_acct','c_elementvalue')").all().map(function (r){return r.name;});
['pa_report','pa_reportline','pa_reportcolumn','pa_reportsource','c_period','c_year','ad_treenode','fact_acct','c_elementvalue'].forEach(function (t) {
  ok(tabs.indexOf(t) >= 0, 'bundle carries ' + t);
});
var hasSign = GB.prepare("PRAGMA table_info(c_elementvalue)").all().some(function (c){return String(c.name).toLowerCase()==='accountsign';});
ok(hasSign, 'c_elementvalue carries accountsign (the fold sign rule)');
var nSign = hasSign ? GB.prepare("SELECT count(*) n FROM c_elementvalue WHERE accountsign='N'").get().n : 0;
console.log('§PA-BROWSER-FACTS tables=' + tabs.length + '/9 accountsign_N=' + nSign);

// ── 1. statementInputs(), replicated against the BUNDLE (== report_overlay.js host path) ──
var EV = {}; GB.prepare('SELECT c_elementvalue_id id, value, accounttype, accountsign, issummary FROM c_elementvalue').all().forEach(function (r) { EV[r.id] = r; });
var kids = {}; GB.prepare('SELECT node_id, parent_id FROM ad_treenode WHERE ad_tree_id=?').all(TREE).forEach(function (n) { (kids[n.parent_id] = kids[n.parent_id] || []).push(n.node_id); });
function leaves(id) { var e = EV[id]; if (!e || e.issummary !== 'Y') return [id]; var out = []; (function rec(x){ (kids[x]||[]).forEach(function (c){ if (EV[c] && EV[c].issummary==='Y') rec(c); else out.push(c); }); })(id); return out; }
var periods = GB.prepare("SELECT p.c_period_id id, p.name, p.startdate sd, p.enddate ed, p.c_year_id yr FROM c_period p JOIN c_year y ON p.c_year_id=y.c_year_id WHERE y.c_calendar_id=? AND p.isactive='Y' AND p.periodtype='S' ORDER BY p.startdate").all(CAL);
var periodIdx = {}; periods.forEach(function (p, i) { periodIdx[p.id] = i; });
// report period = latest posted period of the MOST-ACTIVE fiscal year (== report_overlay.periodModel; NOT the naive
// "latest fact period", which lands in GardenWorld's emptiest year and folds every YTD/Period column to zero).
var factCnt = {}; GB.prepare('SELECT c_period_id id, COUNT(*) n FROM fact_acct GROUP BY c_period_id').all().forEach(function (r){ factCnt[r.id] = r.n; });
var yearFacts = {}; periods.forEach(function (p){ if (factCnt[p.id]) yearFacts[p.yr] = (yearFacts[p.yr]||0) + factCnt[p.id]; });
var bestYr = null, bestN = -1; Object.keys(yearFacts).forEach(function (y){ if (yearFacts[y] > bestN){ bestN = yearFacts[y]; bestYr = Number(y); } });
var reportIdx = -1; periods.forEach(function (p, i){ if (p.yr === bestYr && factCnt[p.id]) reportIdx = i; });
if (reportIdx < 0) reportIdx = periods.length - 1;
console.log('§PA-BROWSER-PERIOD total=' + periods.length + ' reportIdx=' + reportIdx + ' (' + periods[reportIdx].name + ', yr=' + bestYr + ', ' + bestN + ' facts in that year)');
function windowFor(pt, rel) { var idx = reportIdx + (rel||0); var set = new Set(); if (idx<0||idx>=periods.length) return set; var tgt = periods[idx];
  if (pt==='P') set.add(tgt.id); else if (pt==='Y') periods.forEach(function (p){ if (p.yr===tgt.yr && p.sd<=tgt.ed && p.ed<=tgt.ed) set.add(p.id); });
  else if (pt==='T') periods.forEach(function (p){ if (p.ed<=tgt.ed) set.add(p.id); }); else set.add(tgt.id); return set; }
var factRows = GB.prepare('SELECT account_id, amtacctdr, amtacctcr, qty, c_period_id, c_acctschema_id, postingtype FROM fact_acct').all();
var accounts = {}; Object.keys(EV).forEach(function (id){ accounts[id] = { accounttype: EV[id].accounttype, accountsign: EV[id].accountsign }; });

function runReport(reportId) {
  var report = GB.prepare('SELECT pa_report_id, name, pa_reportlineset_id, pa_reportcolumnset_id, c_acctschema_id, c_calendar_id FROM pa_report WHERE pa_report_id=?').get(reportId);
  var lines = GB.prepare("SELECT pa_reportline_id, seqno, name, linetype, calculationtype, oper_1_id, oper_2_id, paamounttype, paperiodtype FROM pa_reportline WHERE pa_reportlineset_id=? AND isactive='Y' ORDER BY seqno").all(report.pa_reportlineset_id);
  var cols = GB.prepare("SELECT pa_reportcolumn_id, seqno, name, columntype, calculationtype, oper_1_id, oper_2_id, paamounttype, paperiodtype, relativeperiod, postingtype FROM pa_reportcolumn WHERE pa_reportcolumnset_id=? AND isactive='Y' ORDER BY seqno").all(report.pa_reportcolumnset_id);
  var sourcesByLine = {};
  lines.forEach(function (l) { sourcesByLine[l.pa_reportline_id] = GB.prepare("SELECT c_elementvalue_id ev FROM pa_reportsource WHERE pa_reportline_id=? AND isactive='Y'").all(l.pa_reportline_id).map(function (s){ return { account_ids: leaves(s.ev) }; }); });
  var periodWindows = {}; cols.forEach(function (c){ if (c.columntype!=='C') periodWindows[c.pa_reportcolumn_id] = windowFor(c.paperiodtype, c.relativeperiod); });
  var folded = CORE.foldStatement(report, lines, cols, sourcesByLine, factRows, accounts, periodWindows);

  // oracle: independent FinReport-style SUM over LIVE fact_acct, per (S-line, segment col).
  var maxDiff = 0, cellCount = 0, schema = report.c_acctschema_id, diffCells = [];
  lines.forEach(function (l) {
    if (l.linetype !== 'S') return;
    var leafIds = {}; (sourcesByLine[l.pa_reportline_id]||[]).forEach(function (s){ (s.account_ids||[]).forEach(function (a){ if (a!=null && a!=='' && !isNaN(a)) leafIds[a]=1; }); });
    var ids = Object.keys(leafIds);
    cols.forEach(function (c) {
      if (c.columntype === 'C') return;
      var win = periodWindows[c.pa_reportcolumn_id]; var winIds = win ? Array.from(win) : [];
      var amt = (l.paamounttype != null && l.paamounttype !== '') ? l.paamounttype : c.paamounttype;
      var expr = ({B:'acctbalance(account_id,amtacctdr,amtacctcr)',S:'amtacctdr-amtacctcr',C:'amtacctcr',D:'amtacctdr',Q:'qty',R:'acctbalance(account_id,qty,0)'})[amt] || '0';
      var oracleVal;
      if (!ids.length || !winIds.length) oracleVal = 0;
      else { var pt = (c.postingtype!=null && c.postingtype!=='') ? " AND postingtype='"+c.postingtype+"'" : '';
        oracleVal = Number(psql("SELECT ROUND(COALESCE(SUM("+expr+"),0),2) FROM fact_acct WHERE ad_client_id=11 AND c_acctschema_id="+schema+" AND account_id IN ("+ids.join(',')+") AND c_period_id IN ("+winIds.join(',')+")"+pt) || 0); }
      var d = Math.abs(cents(folded.cells[l.pa_reportline_id][c.pa_reportcolumn_id]) - cents(oracleVal));
      cellCount++; if (d > maxDiff) maxDiff = d;
      if (d > 0) diffCells.push({ line: l.pa_reportline_id, col: c.pa_reportcolumn_id, diffC: d });
    });
  });
  console.log('§PA-BROWSER report=' + report.pa_report_id + ' "' + report.name + '" lines=' + lines.length + ' cols=' + cols.length + ' segCells=' + cellCount + ' maxDiff=' + maxDiff + 'c (source=glassbowl_data.db)');
  if (diffCells.length) console.log('   diverging: ' + JSON.stringify(diffCells.slice(0, 8)));
  return { report: report, lines: lines, cols: cols, sourcesByLine: sourcesByLine, periodWindows: periodWindows, folded: folded, maxDiff: maxDiff, cellCount: cellCount };
}

console.log('\n[ISSUE] in-app foldStatement(100 Balance Sheet) from the bundle == oracle, cent-exact');
var r100 = runReport(100);
ok(r100.maxDiff === 0, 'Balance Sheet (100) maxDiff=' + r100.maxDiff + 'c over ' + r100.cellCount + ' cells');
console.log('\n[ISSUE] in-app foldStatement(101 Income Statement) from the bundle == oracle, cent-exact');
var r101 = runReport(101);
ok(r101.maxDiff === 0, 'Income Statement (101) maxDiff=' + r101.maxDiff + 'c over ' + r101.cellCount + ' cells');
console.log('\n[ISSUE] in-app foldStatement(102 Statement of Cash Flows) from the bundle == oracle, cent-exact');
var r102 = runReport(102);
ok(r102.maxDiff === 0, 'Statement of Cash Flows (102) maxDiff=' + r102.maxDiff + 'c over ' + r102.cellCount + ' cells');

// NON-VACUITY: maxDiff=0c over an ALL-ZERO statement proves nothing (0==0). Each statement must carry real activity.
console.log('\n[ISSUE] statements are NON-vacuous (real nonzero cells, not a 0==0 tie)');
function nonzeroCells(r) { var n = 0; r.lines.forEach(function (l) { r.cols.forEach(function (c) { if (cents(r.folded.cells[l.pa_reportline_id][c.pa_reportcolumn_id]) !== 0) n++; }); }); return n; }
var nz100 = nonzeroCells(r100), nz101 = nonzeroCells(r101), nz102 = nonzeroCells(r102);
console.log('§PA-BROWSER-NONVACUOUS BS-nonzero-cells=' + nz100 + ' IS-nonzero-cells=' + nz101 + ' CF-nonzero-cells=' + nz102);
ok(nz100 > 0, 'Balance Sheet is NON-vacuous (' + nz100 + ' nonzero cells)');
ok(nz101 > 0, 'Income Statement is NON-vacuous (' + nz101 + ' nonzero cells)');
ok(nz102 > 0, 'Statement of Cash Flows is NON-vacuous (' + nz102 + ' nonzero cells)');

// CALC-COLUMN cross-check: an INDEPENDENT reimplementation of iDempiere doCalculations (A/S/R) — computed from the
// oracle-verified segment cells — diffed vs the engine's calc-column cells. Calc cols are excluded from the oracle
// diff above, so this is the guard that catches operator bugs (e.g. R folded as first-operand instead of range-sum).
console.log('\n[ISSUE] calc columns compose per iDempiere doCalculations (A=o1+o2, S=o1-o2, R=inclusive col range)');
function calcColMismatch(r) {
  var idx = {}; r.cols.forEach(function (c, i) { idx[c.pa_reportcolumn_id] = i; });
  var bad = 0, exercised = 0;
  r.cols.filter(function (c) { return c.columntype === 'C' && 'ASR'.indexOf(c.calculationtype) >= 0; }).forEach(function (c) {
    var i1 = idx[c.oper_1_id], i2 = idx[c.oper_2_id];
    r.lines.forEach(function (l) {
      var cell = r.folded.cells[l.pa_reportline_id];
      var a = i1 != null ? cents(cell[r.cols[i1].pa_reportcolumn_id]) : 0, b = i2 != null ? cents(cell[r.cols[i2].pa_reportcolumn_id]) : 0, exp;
      if (c.calculationtype === 'A') exp = a + b;
      else if (c.calculationtype === 'S') exp = a - b;
      else { var lo = Math.min(i1, i2), hi = Math.max(i1, i2); exp = 0; for (var k = lo; k <= hi; k++) exp += cents(cell[r.cols[k].pa_reportcolumn_id]); }
      if (exp !== 0) exercised++;
      if (exp !== cents(cell[c.pa_reportcolumn_id])) bad++;
    });
  });
  return { bad: bad, exercised: exercised };
}
var cc = calcColMismatch(r101);
console.log('§PA-BROWSER-CALCCOL IS calc-col cells mismatched=' + cc.bad + ' nonzero-exercised=' + cc.exercised);
ok(cc.bad === 0, 'IS calc columns (incl. R "Budget Spent") compose per iDempiere (' + cc.exercised + ' nonzero cells exercised)');

// §FALSIFIER: drop one source leaf from a non-zero BS line -> its cell must diverge from the oracle.
console.log('\n[§FALSIFIER] drop one bundle source leaf -> the affected line cell diverges');
(function () {
  var victim = null;
  r100.lines.forEach(function (l) {
    if (victim || l.linetype !== 'S') return;
    var all = []; (r100.sourcesByLine[l.pa_reportline_id]||[]).forEach(function (s){ (s.account_ids||[]).forEach(function (a){ all.push(a); }); });
    if (!all.length) return;
    if (r100.cols.some(function (c){ return c.columntype!=='C' && cents(r100.folded.cells[l.pa_reportline_id][c.pa_reportcolumn_id])!==0; })) victim = { line: l, leaves: all };
  });
  ok(!!victim, 'found a non-zero BS line to perturb');
  if (!victim) return;
  var dropped = victim.leaves[0];
  var perturbed = {}; Object.keys(r100.sourcesByLine).forEach(function (k){ perturbed[k] = r100.sourcesByLine[k]; });
  perturbed[victim.line.pa_reportline_id] = (r100.sourcesByLine[victim.line.pa_reportline_id]||[]).map(function (s){ return { account_ids: (s.account_ids||[]).filter(function (a){ return a != dropped; }) }; });
  var refold = CORE.foldStatement(r100.report, r100.lines, r100.cols, perturbed, factRows, accounts, r100.periodWindows);
  var diverged = false, where = null;
  r100.cols.forEach(function (c) { if (c.columntype === 'C') return;
    var b = r100.folded.cells[victim.line.pa_reportline_id][c.pa_reportcolumn_id], a = refold.cells[victim.line.pa_reportline_id][c.pa_reportcolumn_id];
    if (cents(b) !== cents(a)) { diverged = true; if (!where) where = { col: c.pa_reportcolumn_id, before: b, after: a }; } });
  console.log('§PA-BROWSER-FALSIFIER dropped leaf=' + dropped + ' from line=' + victim.line.pa_reportline_id + ' -> cellChanged=' + (diverged ? 'Y' : 'N') + (where ? ' (' + where.before + ' -> ' + where.after + ')' : ''));
  ok(diverged, '§FALSIFIER fired: dropping a bundle source leaf changed the cell');
})();

console.log('\n=== RESULT: ' + (fails === 0 ? 'ALL PASS' : fails + ' FAIL') + ' ===');
process.exit(fails === 0 ? 0 : 1);
