// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// report_cashflow.js — foldCashFlow parts: the T_CashFlow (cash-flow report) scratch-table fold.
//   Witness: W-CASHFLOW. Implementing GapClosureSpec.md §5f — closing the T_CashFlow gap of
//   ERP_COVERAGE_MATRIX.md (the multi-source cash-flow report scratch table).
//
// PURE VERBS. A faithful port of the value cores of org.globalqss.process.CashFlow.doIt() — the four
//   CashFlowSource feeds it writes into T_CashFlow:
//     (1) InitialBalance  — SUM(acctBalance(Account_ID,AmtAcctDr,AmtAcctCr)) over Fact_Acct (sqlIni)
//     (2) Plan            — C_CashPlanLine ⋈ C_CashPlan (sqlPlan)            [EMPTY source in seed → no-op]
//     (3) CommitmentsOrders — open C_Order pending = GrandTotal × Σ((QtyOrdered-QtyInvoiced)·PriceActual)
//                             /TotalLines − paid   (sqlOpenOrders + the per-order netting)
//     (4) ActualDebtInvoices — RV_OpenItem.OpenAmt, sign-flipped by IsSOTrx (sqlActual)
//
// WHAT IS A VERB HERE vs WHAT IS THE ACTION:
//   The fold reproduces the VALUE arithmetic of each feed (oracle-backable: each has a SQL/function form).
//   The per-order DUE-DATE insertion gating (paymentTermDueDate ≤ DateTo) and the MOrderPaySchedule
//   accumulation loop are PROCEDURAL (no live SQL/function oracle without an app-server run) → exposed so a
//   witness can prove their INVARIANTS (rule-consistent), distinct from the maxDiff=0c value claims.
//   The X_T_CashFlow.save() row-writes are the document-creation ACTION, not the report — out of scope.
//
// NON-INVENT: every value READ from a real Fact_Acct/C_Order/C_OrderLine/C_Payment/RV_OpenItem row. EXACT
//   BigDecimal; no Date.now / no Math.random. The Plan feed is a verified-empty no-op in this seed (asserted
//   in the witness, not assumed). acctBalance reimplemented as a path INDEPENDENT of iDempiere's plpgsql.
'use strict';

(function (global) {
  var BD = (typeof require === 'function') ? require('./bigdecimal.js') : global.BigDecimal;
  var HALF_UP = BD.RoundingMode.HALF_UP;
  function bd(v) { return (v == null || v === '') ? BD.ZERO : BD.of(String(v)); }
  // ISO 'YYYY-MM-DD…' strings sort lexicographically == chronologically (no Date parsing needed).
  function day(ts) { return String(ts || '').slice(0, 10); }

  // acctBalance(accountType, accountSign, dr, cr) — port of adempiere.acctbalance(p_account_id,p_dr,p_cr).
  //   balance = dr - cr ; with natural sign ('N') resolved to Debit for Asset/Expense accounts else Credit;
  //   a Credit-sign account flips to cr - dr. opts.bendSignRule inverts the natural-sign derivation (FALSIFIER).
  function acctBalance(accountType, accountSign, dr, cr, bendSignRule) {
    var sign = accountSign;
    if (sign === 'N') {
      var isDebitNatural = (accountType === 'A' || accountType === 'E');
      if (bendSignRule) isDebitNatural = !isDebitNatural;   // FALSIFIER: flip every account's natural sign
      sign = isDebitNatural ? 'D' : 'C';
    }
    return (sign === 'C') ? bd(cr).subtract(bd(dr)) : bd(dr).subtract(bd(cr));
  }

  // foldInitialBalance — per posting account, Σ acctBalance over Fact_Acct(PostingType='A', DateAcct<=asOf).
  //   The report sums ONE selected account's whereClause; folding per-account lets the witness diff all of
  //   them (and exercises both the Debit-natural and Credit-natural branches). Returns { account_id: BD }.
  //   evByAccount = { account_id: {accounttype, accountsign} }. opts.bendSignRule → FALSIFIER.
  function foldInitialBalance(factAcct, evByAccount, asOf, opts) {
    opts = opts || {};
    var out = {};
    factAcct.forEach(function (f) {
      if (f.postingtype !== 'A') return;
      if (asOf && day(f.dateacct) > day(asOf)) return;               // DateAcct <= asOf
      var ev = evByAccount[f.account_id] || { accounttype: '', accountsign: 'N' };
      var b = acctBalance(ev.accounttype, ev.accountsign, f.amtacctdr, f.amtacctcr, opts.bendSignRule);
      out[f.account_id] = (out[f.account_id] || BD.ZERO).add(b);
    });
    return out;
  }

  // foldOrderCommitment — per open order, the CommitmentsOrders value math.
  //   open = GrandTotal × pending − paid ; pending = Σ((QtyOrdered-QtyInvoiced)·PriceActual)/TotalLines ;
  //   paid = Σ(IsReceipt? PayAmt : -PayAmt) over unallocated direct payments, negated for a PO ; the final
  //   LineTotalAmt is negated for a PO. Rounds to currency StdPrecision when open.scale exceeds it. The
  //   DueDate≤DateTo insertion gate and pay-schedule loop are surfaced (inserted/dueDate) for invariant tests.
  //   orders=[{c_order_id, grandtotal, totallines, issotrx, stdprecision, ispayschedulevalid, duedate}],
  //   linesByOrder={ id:[{qtyordered,qtyinvoiced,priceactual}] }, paidByOrder={ id: signed-sum }.
  function foldOrderCommitment(orders, linesByOrder, paidByOrder, dateTo, opts) {
    opts = opts || {};
    return orders.map(function (o) {
      var isSO = o.issotrx === 'Y';
      var num = (linesByOrder[o.c_order_id] || []).reduce(function (acc, l) {
        return acc.add(bd(l.qtyordered).subtract(bd(l.qtyinvoiced)).multiply(bd(l.priceactual)));
      }, BD.ZERO);
      var pending = num.divide(bd(o.totallines), 28, HALF_UP);
      if (opts.bendPendingOrder && String(opts.bendPendingOrder) === String(o.c_order_id)) {
        pending = pending.multiply(bd('1.10'));                       // FALSIFIER: bend one order's pending
      }
      var open = bd(o.grandtotal).multiply(pending);
      var paid = bd(paidByOrder[o.c_order_id] || 0);
      if (!isSO) paid = paid.negate();
      open = open.subtract(paid);
      var prec = +o.stdprecision;
      if (open.scale() > prec) open = open.setScale(prec, HALF_UP);
      var lineTotal = isSO ? open : open.negate();                    // non-schedule branch sign
      var inserted = day(o.duedate) <= day(dateTo);                   // DueDate<=DateTo insertion gate
      return {
        C_Order_ID: +o.c_order_id, IsSOTrx: o.issotrx, IsPayScheduleValid: o.ispayschedulevalid,
        pending: pending, open: open, LineTotalAmt: lineTotal, DateTrx: day(o.duedate), inserted: inserted
      };
    });
  }

  // foldActual — RV_OpenItem.OpenAmt sign-flipped by IsSOTrx, gated DueDate<=DateTo (sqlActual).
  //   openItems=[{c_invoice_id, issotrx, duedate, openamt}]. opts.bendSignFlip → FALSIFIER (drop the negate).
  function foldActual(openItems, dateTo, opts) {
    opts = opts || {};
    return openItems.filter(function (oi) { return day(oi.duedate) <= day(dateTo); }).map(function (oi) {
      var amt = bd(oi.openamt);
      if (oi.issotrx !== 'Y' && !opts.bendSignFlip) amt = amt.negate();
      return { C_Invoice_ID: +oi.c_invoice_id, IsSOTrx: oi.issotrx, DateTrx: day(oi.duedate), LineTotalAmt: amt };
    });
  }

  var API = {
    acctBalance: acctBalance,
    foldInitialBalance: foldInitialBalance,
    foldOrderCommitment: foldOrderCommitment,
    foldActual: foldActual
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = API;
  else global.ReportCashFlow = API;
})(typeof globalThis !== 'undefined' ? globalThis : this);
