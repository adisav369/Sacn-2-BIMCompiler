// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-AGING is ✅ DONE in ERP_COVERAGE_MATRIX.md.
// SCOPE: W-AGING — prove build/erp/report_aging.foldAging reproduces iDempiere's Aging report (the T_Aging
//   scratch table) folded over the LIVE rv_openitem, EQUAL TO THE CENT against an INDEPENDENT oracle.
// SPEC: docs/GapClosureSpec.md §5a (gap shape = transient scratch, oracle option (b)).
// ORACLE (non-tautological): iDempiere's OWN source view rv_openitem (live PG, schema adempiere) is the SOURCE;
//   the oracle is a HAND-WRITTEN SQL CASE bucketer over the SAME view — a second, independent implementation of
//   the MAging bucket rules. The fold (JS if-chain) and the oracle (SQL CASE) share only the rows + iDempiere's
//   own DaysDue; the bucketing logic is coded twice, independently. Labelled "grounded on rv_openitem", NOT
//   "== T_Aging" (we did not trigger the SvrProcess; the view + independent bucketer is GapClosureSpec §3 (b)).
// NON-INVENT: every amount/daysdue READ from a real rv_openitem row. INTEGER CENTS diff. No Date.now/random.
// §-log first — READ build/erp/poc_fold_aging.log before concluding (exit code ≠ evidence).
// Run:  bash build/erp/run_witness.sh scripts/poc_fold_aging.js
'use strict';
var path = require('path'), cp = require('child_process');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var AGING = require(path.join(ERP, 'report_aging.js'));

var PG = { container: 'postgres', user: 'adempiere', db: 'idempiere' };
var US = '|';
var fails = 0;
function ok(cond, msg, detail) { if (!cond) fails++; console.log('  ' + (cond ? '✓' : '✗ FAIL:') + ' ' + msg + (detail ? ' — ' + detail : '')); }
function psql(sql) {
  // schema via PGOPTIONS (no `SET …;` echo polluting the result lines), rv_openitem lives in schema adempiere
  return cp.execFileSync('docker', ['exec', '-e', 'PGOPTIONS=-c search_path=adempiere', PG.container,
    'psql', '-U', PG.user, '-d', PG.db, '-t', '-A', '-F', US, '-c', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 64 * 1024 * 1024 });
}
function lines(sql) { return psql(sql).split('\n').filter(function (s) { return s.length; }); }
function cents(x) { return (x == null || x === '') ? 0 : Math.round(Number(x) * 100); }

console.log('=== §AGING witness (W-AGING) — T_Aging fold vs independent SQL bucketer over live rv_openitem ===');

// ── 0. entrance: live oracle reachable ──────────────────────────────────────────────────────────────────
try { lines('SELECT 1'); } catch (e) { console.log('§AGING-SKIP oracle Postgres unreachable → honest ⬜, not ✅'); process.exit(1); }
console.log('§AGING-ORACLE live iDempiere Postgres reachable (' + PG.container + '/' + PG.db + ', schema adempiere); source view = rv_openitem (DaysDue is iDempiere\'s own)\n');

// ── 1. SOURCE: pull every open item from rv_openitem (the rows iDempiere's Aging.doIt drives over) ────────
// StatementDate = today ⇒ m_statementOffset = 0 ⇒ DaysDue = rv_openitem.DaysDue (read verbatim; non-invent).
var SRC = "SELECT oi.c_bpartner_id, bp.c_bp_group_id, oi.c_currency_id, oi.c_invoice_id, oi.issotrx, " +
  "to_char(oi.duedate,'YYYY-MM-DD HH24:MI:SS') duedate, oi.daysdue, oi.grandtotal, oi.openamt " +
  "FROM rv_openitem oi JOIN c_bpartner bp ON oi.c_bpartner_id=bp.c_bpartner_id " +
  "ORDER BY oi.issotrx, oi.c_bpartner_id, oi.c_currency_id, oi.c_invoice_id";
var items = lines(SRC).map(function (l) {
  var f = l.split(US);
  return {
    c_bpartner_id: +f[0], c_bp_group_id: +f[1], c_currency_id: +f[2], c_invoice_id: +f[3],
    issotrx: f[4], duedate: f[5], daysdue: +f[6],
    grandtotal_c: cents(f[7]), openamt_c: cents(f[8])
  };
});
var nSO = items.filter(function (i) { return i.issotrx === 'Y'; }).length;
var nPO = items.filter(function (i) { return i.issotrx === 'N'; }).length;
console.log('§AGING-SRC rv_openitem open items=' + items.length + ' (SO/AR=' + nSO + ', PO/AP=' + nPO + ')');
ok(items.length > 0, 'live rv_openitem returned open items to age', 'n=' + items.length);

// ── 2. ENGINE fold ───────────────────────────────────────────────────────────────────────────────────────
var folded = AGING.foldAging(items, 0);
console.log('§AGING-FOLD groups=' + folded.length + ' (key = IsSOTrx,C_BPartner,C_Currency; non-list-invoices)');

// ── 3. ORACLE: hand-written SQL CASE bucketer over the SAME rv_openitem (independent of the JS if-chain) ───
// dd := oi.daysdue. Each bucket's predicate is the MAging.add condition, coded directly in SQL. amt = openamt.
function sumc(pred) { return "COALESCE(SUM(CASE WHEN " + pred + " THEN round(oi.openamt*100) ELSE 0 END),0)"; }
var dd = "oi.daysdue";
var ORACLE = "SELECT oi.issotrx, oi.c_bpartner_id, oi.c_currency_id, " +
  "COALESCE(SUM(round(oi.grandtotal*100)),0) invoicedamt, COALESCE(SUM(round(oi.openamt*100)),0) openamt, " +
  "trunc(SUM(" + dd + ")::numeric/COUNT(*)) daysdue, to_char(MIN(oi.duedate),'YYYY-MM-DD HH24:MI:SS') duedate, " +
  // not-due (dd<=0) buckets
  sumc(dd + "=0") + " due0, " +
  sumc(dd + "<=0 AND " + dd + ">=-7") + " due0_7, " +
  sumc(dd + "<=0 AND " + dd + ">=-30") + " due0_30, " +
  sumc(dd + "<=-1 AND " + dd + ">=-7") + " due1_7, " +
  sumc(dd + "<=-8 AND " + dd + ">=-30") + " due8_30, " +
  sumc(dd + "<=-31 AND " + dd + ">=-60") + " due31_60, " +
  sumc(dd + "<=-31") + " due31_plus, " +
  sumc(dd + "<=-61 AND " + dd + ">=-90") + " due61_90, " +
  sumc(dd + "<=-61") + " due61_plus, " +
  sumc(dd + "<=-91") + " due91_plus, " +
  sumc(dd + "<=0") + " dueamt, " +
  // past-due (dd>0) buckets
  sumc(dd + ">0 AND " + dd + "<=7") + " pastdue1_7, " +
  sumc(dd + ">0 AND " + dd + "<=30") + " pastdue1_30, " +
  sumc(dd + ">=8 AND " + dd + "<=30") + " pastdue8_30, " +
  sumc(dd + ">=31 AND " + dd + "<=60") + " pastdue31_60, " +
  sumc(dd + ">=31") + " pastdue31_plus, " +
  sumc(dd + ">=61 AND " + dd + "<=90") + " pastdue61_90, " +
  sumc(dd + ">=61") + " pastdue61_plus, " +
  sumc(dd + ">=91") + " pastdue91_plus, " +
  sumc(dd + ">0") + " pastdueamt " +
  "FROM rv_openitem oi GROUP BY oi.issotrx, oi.c_bpartner_id, oi.c_currency_id " +
  "ORDER BY oi.issotrx, oi.c_bpartner_id, oi.c_currency_id";
// column order of the SELECT above, after the 3 keys:
var ORC = ['InvoicedAmt', 'OpenAmt', 'DaysDue', 'DueDate',
  'Due0', 'Due0_7', 'Due0_30', 'Due1_7', 'Due8_30', 'Due31_60', 'Due31_Plus', 'Due61_90', 'Due61_Plus', 'Due91_Plus', 'DueAmt',
  'PastDue1_7', 'PastDue1_30', 'PastDue8_30', 'PastDue31_60', 'PastDue31_Plus', 'PastDue61_90', 'PastDue61_Plus', 'PastDue91_Plus', 'PastDueAmt'];
var oracle = {};
lines(ORACLE).forEach(function (l) {
  var f = l.split(US);
  var key = f[0] + '|' + f[1] + '|' + f[2];
  var row = {};
  ORC.forEach(function (c, i) { row[c] = f[i + 3]; });   // f[3]… are the value columns
  oracle[key] = row;
});
console.log('§AGING-ORACLE-SQL independent SQL CASE bucketer groups=' + Object.keys(oracle).length + ' (hand-coded, distinct source from the JS fold)\n');
ok(Object.keys(oracle).length === folded.length, 'group count matches engine vs SQL oracle', 'engine=' + folded.length + ' sql=' + Object.keys(oracle).length);

// ── 4. DIFF — every monetary cell, integer cents ─────────────────────────────────────────────────────────
var MONEY = ['InvoicedAmt', 'OpenAmt'].concat(AGING.BUCKETS);
var maxDiff = 0, cells = 0, mismatches = 0, daysMis = 0, dateMis = 0;
folded.forEach(function (r) {
  var key = r.IsSOTrx + '|' + r.C_BPartner_ID + '|' + r.C_Currency_ID;
  var o = oracle[key];
  if (!o) { mismatches++; fails++; console.log('   ⚠ FINDING no oracle row for group ' + key); return; }
  MONEY.forEach(function (col) {
    var e = r[col] | 0, ov = o[col] | 0, d = Math.abs(e - ov);
    cells++;
    if (d > maxDiff) maxDiff = d;
    if (d !== 0) { mismatches++; console.log('   ⚠ FINDING cell-mismatch group=' + key + ' col=' + col + ' engine=' + e + 'c oracle=' + ov + 'c diff=' + d + 'c'); }
  });
  if ((r.DaysDue | 0) !== (parseInt(o.DaysDue, 10) | 0)) { daysMis++; console.log('   ⚠ FINDING DaysDue group=' + key + ' engine=' + r.DaysDue + ' oracle=' + o.DaysDue); }
  if (r.DueDate !== o.DueDate) { dateMis++; console.log('   ⚠ FINDING DueDate group=' + key + ' engine=' + r.DueDate + ' oracle=' + o.DueDate); }
});
console.log('§AGING surface=report_aging cells=' + cells + ' (groups=' + folded.length + ' × money-cols=' + MONEY.length + ') maxDiff=' + maxDiff + 'c oracle=rv_openitem-SQL-bucketer');
console.log('§AGING non-money: DaysDue mismatches=' + daysMis + ' DueDate(earliest) mismatches=' + dateMis);
ok(mismatches === 0 && maxDiff === 0, 'foldAging == independent SQL bucketer to the cent over every bucket', 'maxDiff=' + maxDiff + 'c cells=' + cells);
ok(daysMis === 0, 'DaysDue (running avg, int-div) matches the oracle', 'mis=' + daysMis);
ok(dateMis === 0, 'DueDate (earliest) matches the oracle', 'mis=' + dateMis);

// witness the fold is two-sided / non-vacuous: at least one bucket is populated (else diff=0 is trivially true)
var anyNonZero = folded.some(function (r) { return AGING.BUCKETS.some(function (b) { return (r[b] | 0) !== 0; }); });
ok(anyNonZero, 'at least one bucket is non-zero (diff is over real money, not all-zero columns)');

// ── 5. §FALSIFIER — corrupt ONE bucket boundary so a REAL row crosses it → diff MUST go non-zero ──────────
// GardenWorld demo invoices are all ~8200-8900 days overdue, so a literal ±1-day boundary shift moves no row.
// The load-bearing falsifier shifts a PastDue*_Plus threshold to a value the real DaysDue population STRADDLES.
console.log('\n── §FALSIFIER — a boundary shift that a real DaysDue value crosses must break the diff ──');
(function () {
  var ddVals = items.map(function (i) { return i.daysdue; }).sort(function (a, b) { return a - b; });
  // pick a threshold strictly between the two largest distinct DaysDue values so ≥1 row crosses it
  var hi = ddVals[ddVals.length - 1], lower = null;
  for (var i = ddVals.length - 2; i >= 0; i--) { if (ddVals[i] < hi) { lower = ddVals[i]; break; } }
  if (lower === null) { ok(false, 'falsifier needs ≥2 distinct DaysDue values', 'distinct=' + new Set(ddVals).size); return; }
  var bad = lower + 1;   // PastDue61_Plus now requires dd >= bad → rows with dd in [lower, bad) drop out
  var bent = AGING.foldAging(items, 0, { PastDue61_Plus: { lo: bad } });
  var d = 0;
  bent.forEach(function (r) {
    var base = folded.filter(function (x) { return x.IsSOTrx === r.IsSOTrx && x.C_BPartner_ID === r.C_BPartner_ID && x.C_Currency_ID === r.C_Currency_ID; })[0];
    if (base) d += Math.abs((r.PastDue61_Plus | 0) - (base.PastDue61_Plus | 0));
  });
  console.log('§AGING-FALSIFIER bent PastDue61_Plus boundary 61→' + bad + ' (straddles DaysDue ' + lower + '/' + hi + ') → PastDue61_Plus delta=' + d + 'c');
  ok(d > 0, 'corrupting a bucket boundary breaks the diff (the metric is load-bearing, not vacuous)', 'delta=' + d + 'c');
})();

console.log('\n' + (fails ? '🔴 ' + fails + ' check(s) FAILED — W-AGING NOT proven' : '✅ ALL CHECKS PASS — foldAging ORACLE-EQUIVALENT (grounded on rv_openitem) — W-AGING'));
process.exit(fails ? 1 : 0);
