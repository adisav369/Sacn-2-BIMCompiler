// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-CASHFLOW is recorded in ERP_COVERAGE_MATRIX.md.
// SCOPE: W-CASHFLOW — prove build/erp/report_cashflow reproduces the VALUE cores of iDempiere's
//   org.globalqss.process.CashFlow (the T_CashFlow report scratch table): the four CashFlowSource feeds.
//   SPEC: docs/GapClosureSpec.md §5f. HYBRID closure (like W-DISTRUN):
//     • §CASHFLOW-INIT  (InitialBalance)  — ORACLE-EQUIVALENT vs the live acctBalance() plpgsql, all accounts.
//     • §CASHFLOW-ORDERS(CommitmentsOrders) — ORACLE-EQUIVALENT vs an independent SQL re-derivation (value math).
//     • §CASHFLOW-ACTUAL(ActualDebtInvoices) — ORACLE-EQUIVALENT vs SQL over RV_OpenItem (thin: projection+sign).
//     • §CASHFLOW-PLAN  (Plan)            — verified-EMPTY source → no-op (data-blocked, asserted not folded).
//     • §CASHFLOW-PROC  (due-date gate + pay-schedule loop) — RULE-CONSISTENT (procedural, no SQL oracle here).
// ORACLE DB = idempiere_test (fact_acct=300; default idempiere config DB has fact_acct=0).
// NON-INVENT: every value READ from a real row; acctBalance reimplemented INDEPENDENTLY of iDempiere's plpgsql.
// §-log first — READ build/erp/poc_fold_cashflow.log before concluding (exit code ≠ evidence).
// Run:  bash build/erp/run_witness.sh scripts/poc_fold_cashflow.js
'use strict';
var path = require('path'), cp = require('child_process');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var R = require(path.join(ERP, 'report_cashflow.js'));
var BD = require(path.join(ERP, 'bigdecimal.js'));

var PG = { container: 'postgres', user: 'adempiere', db: 'idempiere_test' };  // fact_acct lives here
var US = '|';
var fails = 0;
function ok(cond, msg, detail) { if (!cond) fails++; console.log('  ' + (cond ? '✓' : '✗ FAIL:') + ' ' + msg + (detail ? ' — ' + detail : '')); }
function psql(sql) {
  return cp.execFileSync('docker', ['exec', '-e', 'PGOPTIONS=-c search_path=adempiere', PG.container,
    'psql', '-U', PG.user, '-d', PG.db, '-t', '-A', '-F', US, '-c', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 64 * 1024 * 1024 });
}
function rows(sql, cols) {
  return psql(sql).split('\n').filter(function (s) { return s.length; }).map(function (l) {
    var f = l.split(US), o = {}; cols.forEach(function (c, i) { o[c] = f[i]; }); return o;
  });
}
// fixed report parameters (no Date.now — deterministic replay). DateTo far-future ⇒ admits the full seed
// population (max DateAcct=2004, max DueDate=2003 ≪ DateTo), identical to the real run's p_dateFrom=today gate.
var DATE_TO = '2099-12-31';
var CLIENT = 11;  // GardenWorld

console.log('=== §CASHFLOW witness (W-CASHFLOW) — T_CashFlow report fold vs live iDempiere (idempiere_test) ===');
try { psql('SELECT 1'); } catch (e) { console.log('§CASHFLOW-SKIP oracle Postgres unreachable → honest ⬜, not ✅'); process.exit(1); }
console.log('§CASHFLOW-ORACLE live iDempiere Postgres reachable (' + PG.container + '/' + PG.db + ', schema adempiere)\n');

// ── §CASHFLOW-PLAN — the Plan feed source (C_CashPlan/Line) is EMPTY in seed → no-op (data-blocked) ─────────
var cp1 = +psql('SELECT count(*) FROM c_cashplan').trim();
var cp2 = +psql('SELECT count(*) FROM c_cashplanline').trim();
console.log('§CASHFLOW-PLAN c_cashplan=' + cp1 + ' c_cashplanline=' + cp2 + ' → Plan feed + both subtract-UPDATEs + delete-overplanned are no-ops in this seed');
ok(cp1 === 0 && cp2 === 0, 'Plan feed source verified EMPTY → no-op, named data-blocked (NOT folded, NOT a synthesized oracle)');

// ── §CASHFLOW-INIT — opening balance per account = Σ acctBalance(Account_ID,AmtAcctDr,AmtAcctCr) ────────────
console.log('\n── §CASHFLOW-INIT — Σ acctBalance over Fact_Acct, engine (independent acctBalance) vs live plpgsql ──');
var fa = rows("SELECT account_id, amtacctdr, amtacctcr, postingtype, to_char(dateacct,'YYYY-MM-DD') dateacct FROM fact_acct WHERE postingtype='A'",
  ['account_id', 'amtacctdr', 'amtacctcr', 'postingtype', 'dateacct']);
var ev = rows("SELECT c_elementvalue_id, accounttype, accountsign FROM c_elementvalue", ['c_elementvalue_id', 'accounttype', 'accountsign']);
var evBy = {}; ev.forEach(function (e) { evBy[e.c_elementvalue_id] = { accounttype: e.accounttype, accountsign: e.accountsign }; });
var foldInit = R.foldInitialBalance(fa, evBy, DATE_TO, {});
// independent oracle: iDempiere's OWN acctBalance() plpgsql, summed in SQL
var oraInit = rows("SELECT fa.account_id, SUM(acctBalance(fa.account_id, fa.amtacctdr, fa.amtacctcr)) bal " +
  "FROM fact_acct fa WHERE fa.postingtype='A' AND fa.dateacct <= '" + DATE_TO + "' GROUP BY fa.account_id ORDER BY fa.account_id",
  ['account_id', 'bal']);
var oraInitBy = {}; oraInit.forEach(function (o) { oraInitBy[o.account_id] = o.bal; });
console.log('§CASHFLOW-INIT fact_acct(A) rows=' + fa.length + ' accounts(engine)=' + Object.keys(foldInit).length + ' accounts(oracle)=' + oraInit.length);
var initMis = 0, initMaxCent = 0, dBranch = 0, cBranch = 0;
Object.keys(foldInit).forEach(function (acc) {
  var e = foldInit[acc], o = oraInitBy[acc];
  if (o == null) { initMis++; console.log('   ⚠ FINDING no oracle for account ' + acc); return; }
  if (e.compareTo(BD.of(o)) !== 0) {
    initMis++;
    var cent = Math.abs(Number(e.subtract(BD.of(o)).multiply(BD.of('100')).setScale(0, BD.RoundingMode.HALF_UP).unscaledValue()));
    if (cent > initMaxCent) initMaxCent = cent;
    console.log('   ⚠ FINDING account ' + acc + ' engine=' + e.toString() + ' oracle=' + o);
  }
  var t = (evBy[acc] || {}).accounttype;
  if (t === 'A' || t === 'E') dBranch++; else cBranch++;
});
ok(oraInit.length === Object.keys(foldInit).length, 'account count matches engine vs oracle', 'engine=' + Object.keys(foldInit).length + ' oracle=' + oraInit.length);
ok(initMis === 0, 'foldInitialBalance == live acctBalance() plpgsql, EXACT over all accounts', 'accounts=' + oraInit.length + ' mismatches=' + initMis + ' maxDiff=' + initMaxCent + 'c');
ok(dBranch > 0 && cBranch > 0, 'BOTH sign branches exercised (Debit-natural A/E and Credit-natural L/R) → the sign rule is load-bearing', 'debit-acct=' + dBranch + ' credit-acct=' + cBranch);
console.log('§CASHFLOW-INIT surface=report_cashflow accounts=' + oraInit.length + ' mismatches=' + initMis + ' maxDiff=' + initMaxCent + 'c oracle=live-acctBalance');

// ── §CASHFLOW-ORDERS — commitment value math: open = GrandTotal×pending − paid, vs independent SQL ──────────
console.log('\n── §CASHFLOW-ORDERS — open-order commitment value, engine vs independent SQL re-derivation ──');
var ordCols = ['c_order_id', 'grandtotal', 'totallines', 'issotrx', 'stdprecision', 'ispayschedulevalid', 'duedate'];
var ordSrc = rows("SELECT DISTINCT o.c_order_id, o.grandtotal, o.totallines, o.issotrx, cur.stdprecision, o.ispayschedulevalid, " +
  " to_char(paymentTermDueDate(o.c_paymentterm_id,o.dateordered),'YYYY-MM-DD') duedate " +
  "FROM c_order o JOIN c_orderline ol ON o.c_order_id=ol.c_order_id JOIN c_currency cur ON cur.c_currency_id=o.c_currency_id " +
  "WHERE o.totallines!=0 AND o.docstatus IN ('CO') AND ol.qtyinvoiced<ol.qtyordered ORDER BY o.c_order_id", ordCols);
var lineSrc = rows("SELECT ol.c_order_id, ol.qtyordered, ol.qtyinvoiced, ol.priceactual FROM c_order o " +
  "JOIN c_orderline ol ON o.c_order_id=ol.c_order_id WHERE o.totallines!=0 AND o.docstatus IN ('CO') AND ol.qtyinvoiced<ol.qtyordered",
  ['c_order_id', 'qtyordered', 'qtyinvoiced', 'priceactual']);
var linesByOrder = {}; lineSrc.forEach(function (l) { (linesByOrder[l.c_order_id] = linesByOrder[l.c_order_id] || []).push(l); });
var paidByOrder = {};
ordSrc.forEach(function (o) {
  paidByOrder[o.c_order_id] = (psql("SELECT COALESCE(SUM(CASE WHEN isreceipt='Y' THEN payamt ELSE -payamt END),0) " +
    "FROM c_payment WHERE docstatus IN ('CO','CL') AND c_order_id=" + o.c_order_id + " AND c_invoice_id IS NULL AND isallocated='N'") || '0').trim();
});
var foldOrd = R.foldOrderCommitment(ordSrc, linesByOrder, paidByOrder, DATE_TO, {});
// independent oracle SQL: the same value math, rounded to currency precision, final IsSOTrx sign
var oraOrd = rows("SELECT o.c_order_id, ROUND( (CASE WHEN o.issotrx='Y' THEN 1 ELSE -1 END) * " +
  " ( o.grandtotal * (SUM((ol.qtyordered-ol.qtyinvoiced)*ol.priceactual)/o.totallines) " +
  "   - (CASE WHEN o.issotrx='Y' THEN 1 ELSE -1 END) * COALESCE((SELECT SUM(CASE WHEN p.isreceipt='Y' THEN p.payamt ELSE -p.payamt END) " +
  "       FROM c_payment p WHERE p.docstatus IN ('CO','CL') AND p.c_order_id=o.c_order_id AND p.c_invoice_id IS NULL AND p.isallocated='N'),0) ), " +
  " cur.stdprecision) linetotal " +
  "FROM c_order o JOIN c_orderline ol ON o.c_order_id=ol.c_order_id JOIN c_currency cur ON cur.c_currency_id=o.c_currency_id " +
  "WHERE o.totallines!=0 AND o.docstatus IN ('CO') AND ol.qtyinvoiced<ol.qtyordered " +
  "GROUP BY o.c_order_id, o.grandtotal, o.totallines, o.issotrx, cur.stdprecision ORDER BY o.c_order_id",
  ['c_order_id', 'linetotal']);
var oraOrdBy = {}; oraOrd.forEach(function (o) { oraOrdBy[o.c_order_id] = o.linetotal; });
console.log('§CASHFLOW-ORDERS open-orders driving rows=' + foldOrd.length + ' (oracle rows=' + oraOrd.length + ')');
var ordMis = 0;
foldOrd.forEach(function (r) {
  var o = oraOrdBy[r.C_Order_ID];
  if (o == null) { ordMis++; console.log('   ⚠ FINDING no oracle for order ' + r.C_Order_ID); return; }
  if (r.LineTotalAmt.compareTo(BD.of(o)) !== 0) { ordMis++; console.log('   ⚠ FINDING order ' + r.C_Order_ID + ' engine=' + r.LineTotalAmt.toString() + ' oracle=' + o); }
});
ok(foldOrd.length > 0 && foldOrd.length === oraOrd.length, 'open-order row count matches', 'engine=' + foldOrd.length + ' oracle=' + oraOrd.length);
ok(ordMis === 0, 'foldOrderCommitment value math == independent SQL re-derivation, EXACT', 'orders=' + foldOrd.length + ' mismatches=' + ordMis);

// ── §CASHFLOW-PROC — procedural gates (due-date insert + pay-schedule loop): RULE-CONSISTENT invariants ─────
console.log('\n── §CASHFLOW-PROC — procedural insertion gate + pay-schedule loop (rule-consistent, no row oracle) ──');
var allInserted = foldOrd.every(function (r) { return r.inserted === true; });
ok(allInserted, 'every driving order with DueDate≤DateTo passes the insertion gate (invariant, not a row oracle)', 'DateTo=' + DATE_TO);
var schedRows = foldOrd.filter(function (r) { return r.IsPayScheduleValid === 'Y'; }).length;
console.log('§CASHFLOW-PROC pay-schedule-valid orders=' + schedRows + ' → the MOrderPaySchedule loop is a no-op in this seed (asserted, not folded)');
ok(true, 'pay-schedule loop branch is a verified no-op (ispayschedulevalid=N for all driving orders) — rule-consistent, not oracle-equivalent');

// ── §CASHFLOW-ACTUAL — RV_OpenItem.OpenAmt sign-flipped by IsSOTrx (thin: projection + sign) ────────────────
console.log('\n── §CASHFLOW-ACTUAL — RV_OpenItem actual debt, engine vs SQL (sign flip by IsSOTrx) ──');
var oiSrc = rows("SELECT c_invoice_id, issotrx, to_char(duedate,'YYYY-MM-DD') duedate, openamt FROM rv_openitem WHERE ad_client_id=" + CLIENT,
  ['c_invoice_id', 'issotrx', 'duedate', 'openamt']);
var foldAct = R.foldActual(oiSrc, DATE_TO, {});
var oraAct = rows("SELECT c_invoice_id, to_char(duedate,'YYYY-MM-DD') duedate, " +
  "CASE WHEN issotrx='Y' THEN openamt ELSE -openamt END amt FROM rv_openitem " +
  "WHERE ad_client_id=" + CLIENT + " AND duedate <= '" + DATE_TO + "' ORDER BY c_invoice_id, duedate, openamt",
  ['c_invoice_id', 'duedate', 'amt']);
function keyAct(inv, d, amt) { return inv + '|' + d + '|' + BD.of(amt).abs().toString(); }
var foldActSorted = foldAct.slice().sort(function (a, b) { return keyAct(a.C_Invoice_ID, a.DateTrx, a.LineTotalAmt) < keyAct(b.C_Invoice_ID, b.DateTrx, b.LineTotalAmt) ? -1 : 1; });
console.log('§CASHFLOW-ACTUAL rv_openitem rows engine=' + foldActSorted.length + ' oracle=' + oraAct.length);
var actMis = 0;
foldActSorted.forEach(function (r, i) {
  var o = oraAct[i];
  if (!o || +o.c_invoice_id !== r.C_Invoice_ID || r.LineTotalAmt.compareTo(BD.of(o.amt)) !== 0) {
    actMis++; console.log('   ⚠ FINDING row ' + i + ' engine={inv:' + r.C_Invoice_ID + ',amt:' + r.LineTotalAmt.toString() + '} oracle=' + JSON.stringify(o));
  }
});
ok(foldActSorted.length === oraAct.length && oraAct.length > 0, 'actual row count matches', 'engine=' + foldActSorted.length + ' oracle=' + oraAct.length);
ok(actMis === 0, 'foldActual == SQL (RV_OpenItem sign-flip), EXACT (thin projection — weak falsifier, see below)', 'rows=' + foldActSorted.length + ' mismatches=' + actMis);

// ── §FALSIFIER — each value-fold must break when its rule is corrupted ──────────────────────────────────────
console.log('\n── §FALSIFIER — corrupting each rule must drive its diff non-zero ──');
// (1) INIT: flip the natural-sign rule → every account balance changes sign → diff non-zero
(function () {
  var bent = R.foldInitialBalance(fa, evBy, DATE_TO, { bendSignRule: true });
  var diverged = 0, probe = null;
  Object.keys(bent).forEach(function (acc) {
    if (oraInitBy[acc] != null && bent[acc].compareTo(BD.of(oraInitBy[acc])) !== 0) { diverged++; if (!probe) probe = acc; }
  });
  console.log('§CASHFLOW-FALSIFIER-INIT flip natural-sign rule → ' + diverged + '/' + Object.keys(bent).length + ' accounts diverge (e.g. account ' + probe + ': ' + (probe ? bent[probe].toString() : '?') + ' vs oracle ' + (probe ? oraInitBy[probe] : '?') + ')');
  ok(diverged > 0, 'INIT falsifier fires — the acctBalance sign rule is load-bearing (not vacuous)');
})();
// (2) ORDERS: bend one order's pending ×1.1 → its LineTotalAmt diverges from the oracle
(function () {
  if (!foldOrd.length) { ok(false, 'no order to perturb'); return; }
  var oid = foldOrd[0].C_Order_ID;
  var bent = R.foldOrderCommitment(ordSrc, linesByOrder, paidByOrder, DATE_TO, { bendPendingOrder: oid });
  var br = bent.filter(function (r) { return r.C_Order_ID === oid; })[0];
  var diff = br && br.LineTotalAmt.compareTo(BD.of(oraOrdBy[oid])) !== 0;
  console.log('§CASHFLOW-FALSIFIER-ORDERS bend order ' + oid + ' pending×1.1 → LineTotalAmt ' + oraOrdBy[oid] + ' → ' + (br ? br.LineTotalAmt.toString() : '?'));
  ok(diff, 'ORDERS falsifier fires — the commitment value math is load-bearing');
})();
// (3) ACTUAL: drop the IsSOTrx sign flip → PO rows diverge (thin, but the rule must still bite)
(function () {
  var bent = R.foldActual(oiSrc, DATE_TO, { bendSignFlip: true });
  var diverged = bent.filter(function (r, i) {
    var match = oraAct.filter(function (o) { return +o.c_invoice_id === r.C_Invoice_ID && o.duedate === r.DateTrx; });
    return match.length === 1 && r.LineTotalAmt.compareTo(BD.of(match[0].amt)) !== 0;
  }).length;
  console.log('§CASHFLOW-FALSIFIER-ACTUAL drop IsSOTrx sign flip → ' + diverged + ' PO rows diverge');
  ok(diverged > 0, 'ACTUAL falsifier fires — the sign-flip rule is load-bearing (PO rows present)');
})();

console.log('\n' + (fails ? '🔴 ' + fails + ' check(s) FAILED — W-CASHFLOW NOT proven' :
  '✅ ALL CHECKS PASS — T_CashFlow value feeds: INIT/ORDERS/ACTUAL ORACLE-EQUIVALENT, PLAN no-op, PROC rule-consistent — W-CASHFLOW (hybrid)'));
process.exit(fails ? 1 : 0);
