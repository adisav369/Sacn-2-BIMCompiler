// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// report_bank_register.js — foldBankRegister: the T_BankRegister (bank register) report fold.
//   Witness: W-BANKREGISTER. Implementing GapClosureSpec.md §5g — closing the T_BankRegister gap of
//   ERP_COVERAGE_MATRIX.md (the last `T_*` report scratch-table member).
//
// PURE VERB. A faithful port of org.compiere.report.BankRegister — createBalanceLine() + createDetailLines().
//   This report is THIN / near-tautological (the recon flagged it): its only real logic is the bank-account
//   CONFIG-CHAIN join that selects the right Fact_Acct rows, plus a DISTINCT-vs-SUM quirk; `Balance = AmtAcctDr
//   − AmtAcctCr` merely passes through. So the load-bearing claim is the JOIN SELECTION, not arithmetic.
//
// The two INSERT…SELECTs over the SAME join:
//   Fact_Acct fa  (AD_Table_ID = C_Payment)
//     ⋈ C_Payment p (docstatus IN 'CO','CL')      on p.C_Payment_ID = fa.Record_ID
//     ⋈ C_BankAccount ba                          on ba.C_BankAccount_ID = p.C_BankAccount_ID  (ba.C_Bank_ID = param)
//     ⋈ C_Bank b
//     ⋈ C_BankAccount_Acct baa
//     ⋈ C_ValidCombination vc  (vc.id = baa.B_InTransit_Acct OR vc.id = baa.B_Asset_Acct)
//     ⋈ C_ElementValue ev      (INNER on ev.id = vc.Account_ID — existence required)
//     ⟕ C_BPartner bp          (LEFT on bp.id = fa.C_BPartner_ID)
//   WHERE fa.Account_ID = vc.Account_ID  (← the bank-account selection; dropping it = the §FALSIFIER)
//   • createDetailLines:  SELECT DISTINCT … TRUNC(DateAcct) BETWEEN from AND to → per-payment lines
//   • createBalanceLine:  SUM(Dr), SUM(Cr), SUM(Dr−Cr) … TRUNC(DateAcct) < from → ONE beginning-balance line
//   NOTE the iDempiere QUIRK reproduced faithfully (non-invent): the baa/vc join MULTIPLIES each fact row;
//   the detail's SELECT DISTINCT collapses the duplicates, but the balance line's SUM does NOT → it counts the
//   multiplicity. Both behaviours are matched against the live SQL, warts and all.
//   The T_BankRegister row-writes + the AD_PrintFormat lookup are the ACTION, not the report core.
//
// NON-INVENT: every value READ from a real Fact_Acct/C_Payment/C_BankAccount(_Acct)/C_ValidCombination row.
//   EXACT BigDecimal. No Date.now / no Math.random. ISO date strings (sliced to day) sort == chronologically.
'use strict';

(function (global) {
  var BD = (typeof require === 'function') ? require('./bigdecimal.js') : global.BigDecimal;
  function bd(v) { return (v == null || v === '') ? BD.ZERO : BD.of(String(v)); }
  function day(ts) { return String(ts || '').slice(0, 10); }

  // joinRows — reproduce the literal BankRegister join (with multiplicity), independent of the oracle SQL.
  // src = { factAcct:[{fact_acct_id, ad_table_id, record_id, account_id, dateacct, amtacctdr, amtacctcr,
  //                    c_bpartner_id, ad_client_id, ad_org_id}],
  //         payments:[{c_payment_id, c_bankaccount_id, docstatus, documentno}],
  //         bankAccounts:[{c_bankaccount_id, c_bank_id}], banks:[{c_bank_id, name}],
  //         bankAccountAcct:[{c_bankaccount_id, b_intransit_acct, b_asset_acct}],
  //         validCombinations:[{c_validcombination_id, account_id}], elementValues:[{c_elementvalue_id}],
  //         bpartners:[{c_bpartner_id, name}] }
  function joinRows(src, params, opts) {
    opts = opts || {};
    var payById = {}; src.payments.forEach(function (p) { payById[p.c_payment_id] = p; });
    var baById = {}; src.bankAccounts.forEach(function (b) { baById[b.c_bankaccount_id] = b; });
    var bankName = {}; src.banks.forEach(function (b) { bankName[b.c_bank_id] = b.name; });
    var bpName = {}; src.bpartners.forEach(function (b) { bpName[b.c_bpartner_id] = b.name; });
    var vcById = {}; src.validCombinations.forEach(function (v) { vcById[v.c_validcombination_id] = v; });
    var evSet = {}; src.elementValues.forEach(function (e) { evSet[e.c_elementvalue_id] = true; });
    var baaByAccount = {};
    src.bankAccountAcct.forEach(function (a) { (baaByAccount[a.c_bankaccount_id] = baaByAccount[a.c_bankaccount_id] || []).push(a); });

    var out = [];
    src.factAcct.forEach(function (fa) {
      if (String(fa.ad_table_id) !== String(params.paymentTableId)) return;     // fa.AD_Table_ID = C_Payment
      var p = payById[fa.record_id];
      if (!p || (p.docstatus !== 'CO' && p.docstatus !== 'CL')) return;
      var ba = baById[p.c_bankaccount_id];
      if (!ba || String(ba.c_bank_id) !== String(params.cBankId)) return;
      (baaByAccount[p.c_bankaccount_id] || []).forEach(function (baa) {
        [baa.b_intransit_acct, baa.b_asset_acct].forEach(function (vcid) {        // the OR (both combos)
          var vc = vcById[vcid];
          if (!vc) return;
          if (!evSet[vc.account_id]) return;                                      // INNER JOIN C_ElementValue
          if (!opts.bendJoin && String(fa.account_id) !== String(vc.account_id)) return;  // ← selection / FALSIFIER
          var dr = bd(fa.amtacctdr), cr = bd(fa.amtacctcr);
          out.push({
            ad_client_id: fa.ad_client_id, ad_org_id: fa.ad_org_id, dateAcct: day(fa.dateacct),
            c_bank_id: ba.c_bank_id, bankName: bankName[ba.c_bank_id],
            c_bpartner_id: fa.c_bpartner_id, bpartner: bpName[fa.c_bpartner_id] || null,
            documentNo: p.documentno, Dr: dr, Cr: cr,
            Balance: opts.bendBalance ? dr.add(cr) : dr.subtract(cr)              // Dr−Cr (opts → Dr+Cr)
          });
        });
      });
    });
    return out;
  }

  // createDetailLines — SELECT DISTINCT over the join, TRUNC(DateAcct) BETWEEN from AND to.
  function foldDetailLines(src, params, opts) {
    var j = joinRows(src, params, opts).filter(function (r) {
      return r.dateAcct >= day(params.dateFrom) && r.dateAcct <= day(params.dateTo);
    });
    var seen = {}, out = [];
    j.forEach(function (r) {
      var k = [r.ad_client_id, r.ad_org_id, r.dateAcct, r.c_bank_id, r.bankName, r.c_bpartner_id, r.bpartner,
        r.documentNo, r.Dr.toString(), r.Cr.toString(), r.Balance.toString()].join('');
      if (seen[k]) return; seen[k] = true; out.push(r);                          // SELECT DISTINCT
    });
    return out;
  }

  // createBalanceLine — SUM(Dr), SUM(Cr), SUM(Dr−Cr) over the join, TRUNC(DateAcct) < from (NO distinct).
  function foldBalanceLine(src, params, opts) {
    var j = joinRows(src, params, opts).filter(function (r) { return r.dateAcct < day(params.dateFrom); });
    var sumDr = BD.ZERO, sumCr = BD.ZERO, sumBal = BD.ZERO;
    j.forEach(function (r) { sumDr = sumDr.add(r.Dr); sumCr = sumCr.add(r.Cr); sumBal = sumBal.add(r.Balance); });
    return { rows: j.length, AmtAcctDr: sumDr, AmtAcctCr: sumCr, Balance: sumBal };
  }

  var API = { joinRows: joinRows, foldDetailLines: foldDetailLines, foldBalanceLine: foldBalanceLine };
  if (typeof module !== 'undefined' && module.exports) module.exports = API;
  else global.ReportBankRegister = API;
})(typeof globalThis !== 'undefined' ? globalThis : this);
