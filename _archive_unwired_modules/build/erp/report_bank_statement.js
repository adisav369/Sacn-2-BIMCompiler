// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// report_bank_statement.js — foldBankStatement: the Doc_BankStatement (C_BankStatement, AD_Table_ID=392) GL fold.
//   Witness: W-FOLD-BANKSTMT. Implementing GapClosureSpec.md §5i — P3.6 of GAP_CLOSURE_LANE.md
//   ("the one un-folded posted doc table: C_BankStatement → Doc_Bank token manifest + witness").
//
// PURE VERB (GAP_CLOSURE_LANE constraint: consume the engine, never fork). A faithful port of the value core of
//   org.compiere.acct.Doc_BankStatement.createFacts (the CMB doc type posting):
//     Per acct schema, per active bank statement line:
//       DR {Bank.Asset}        stmtAmt  (converted to schema currency)
//       CR {Bank.InTransit}    trxAmt   (converted; DR when trxAmt negative, but seed is positive)
//       DR/CR {Charge.Expense} |chargeAmt| (< 0 → DR, > 0 → CR; zero = no entry)
//       DR {Bank.InterestExp}  |interestAmt| (when interestAmt < 0; signum < 0 → negate → positive → DR)
//       CR {Bank.InterestRev}  |interestAmt| (when interestAmt > 0; signum >= 0 → negate → negative → CR)
//     After all lines: if ΣDR≠ΣCR and schema.usecurrencybalancing='Y' →
//       DR or CR {AcctSchema.CurrencyBalance} |ΣCR-ΣDR| (Fact.balanceAccounting)
//
// CURRENCY: amounts are in bank-account currency (c_bankaccount.c_currency_id). Per schema: if schema currency
//   differs from bank currency, convert via currencyConvert(amt, bankCcy, schemaCcy, stmtDate, convType).
//   Port of Fact.setAmtSource/convertAmount using the multiplyrate (ROUND HALF_UP, schema StdPrecision).
//
// Fact.createLine(docLine, accountDr, accountCr, C_Currency_ID, Amt) semantics (Fact.java:186-193):
//   if Amt < 0: CR to accountCr; else DR to accountDr. — ONE FactLine, not two.
//
// ACCOUNTS resolved via deps.resolve(token, keyId, schemaId) → c_elementvalue_id (nullable).
// AMOUNTS: BigDecimal-exact cents (integer) for schema-101 USD; for schema-200000 EUR: ROUND(amt × rate, 2) HALF_UP.
//
// NON-INVENT: every value READ from a real row. No Date.now / no Math.random.
// opts.* are FALSIFIER hooks only.
'use strict';

(function (global) {
  var HALF_UP = 4; // BigDecimal.ROUND_HALF_UP

  // currencyConvert — round(amt × multiplyRate, stdPrecision) HALF_UP
  function currencyConvert(amtNum, rate, precision) {
    if (rate == null || Number(rate) === 1) return Math.round(Number(amtNum) * 100);  // same ccy, integer cents
    // for cross-currency: round to stdPrecision decimal places then express as cents
    var mult = Math.pow(10, Number(precision || 2));
    var raw = Number(amtNum) * Number(rate);
    return Math.round(raw * mult) * (100 / mult);  // cents at stdPrecision
  }

  // foldBankStatement(src, deps, opts) — derive the C_BankStatement GL for ALL active acct schemas.
  //   src = {
  //     statement:   { c_bankstatement_id, c_bankaccount_id, statementdate, c_currency_id (bank ccy) },
  //     lines:       [{c_bankstatementline_id, line, trxamt, stmtamt, c_charge_id, chargeamt, interestamt, isactive}],
  //     schemas:     [{c_acctschema_id, c_currency_id, stdprecision, usecurrencybalancing}],
  //     convRates:   [{c_currency_id, c_currency_id_to, multiplyrate, c_conversiontype_id}],
  //     acctSchemaGl:[{c_acctschema_id, usecurrencybalancing, currencybalancing_acct}]  — from c_acctschema_gl
  //   }
  //   deps.resolve(token, keyId, schemaId) → c_elementvalue_id (or null).
  //   Returns { bySchema:{ '101': { agg, detail, balance, absent }, … }, absent:[] }.
  function foldBankStatement(src, deps, opts) {
    opts = opts || {};
    var resolve = deps.resolve;
    var stmt = src.statement;
    var bankCcy = Number(stmt.c_currency_id);
    var bankAcctId = Number(stmt.c_bankaccount_id);

    // build conversion rate lookup: [fromCcy][toCcy] → multiplyRate
    var convMap = {};
    (src.convRates || []).forEach(function (r) {
      var f = Number(r.c_currency_id), t = Number(r.c_currency_id_to);
      if (!convMap[f]) convMap[f] = {};
      convMap[f][t] = Number(r.multiplyrate);
    });
    // same-currency implicit rate
    function getRate(from, to) {
      if (from === to) return 1;
      return (convMap[from] && convMap[from][to]) || null;
    }

    // build acctSchemaGl lookup by schema id
    var glMap = {};
    (src.acctSchemaGl || []).forEach(function (g) { glMap[Number(g.c_acctschema_id)] = g; });

    var bySchema = {}, allAbsent = [];

    (src.schemas || []).forEach(function (schema) {
      var schemaId = Number(schema.c_acctschema_id);
      var schemaCcy = Number(schema.c_currency_id);
      var stdPrec = Number(schema.stdprecision != null ? schema.stdprecision : 2);
      var rate = getRate(bankCcy, schemaCcy);  // may be null if no rate found
      var gl = glMap[schemaId] || {};
      var useCurrBal = gl.usecurrencybalancing === 'Y';

      var agg = {}, detail = [], absent = [];
      function addLine(side, token, keyId, centVal) {
        var acctElem = resolve(token, keyId, schemaId);
        if (acctElem == null) { absent.push({ token: token, key: keyId, schema: schemaId }); allAbsent.push(token + '@' + keyId + '/schema=' + schemaId); return; }
        var k = side + ':' + acctElem;
        agg[k] = (agg[k] || 0) + centVal;
        detail.push({ side: side, token: token, acct: acctElem, cents: centVal });
      }
      function convertCents(amtNum) { return currencyConvert(amtNum, rate, stdPrec); }

      var activeLines = (src.lines || []).filter(function (l) { return l.isactive !== 'N'; });
      activeLines.forEach(function (l) {
        var stmtAmtC = convertCents(l.stmtamt);
        var trxAmtC  = convertCents(l.trxamt);

        // BankAsset DR stmtAmt (Doc_BankStatement.createFacts line 204-209)
        if (stmtAmtC !== 0) addLine('DR', '{Bank.Asset}', bankAcctId, Math.abs(stmtAmtC));

        // BankInTransit CR trxAmt (line 240: createLine(line, BankInTransit, currId, trxAmt.negate()))
        // trxAmt.negate() = -trxAmt. If -trxAmt < 0 → CR. In seed trxAmt > 0, so -trxAmt < 0 → CR.
        // The two-account form: amt<0 → CR to second account (BankInTransit). For trxAmt>0: CR trxAmt.
        if (trxAmtC !== 0) addLine('CR', '{Bank.InTransit}', bankAcctId, Math.abs(trxAmtC));

        // Charge (line 256-267): if chargeAmt > 0: CR; if chargeAmt < 0: DR |chargeAmt|. No entry if 0.
        var chargeAmtC = convertCents(l.chargeamt);
        if (l.c_charge_id && chargeAmtC !== 0 && !opts.dropCharge) {
          var chargeKey = opts.bendCharge != null ? opts.bendCharge : Number(l.c_charge_id);
          if (chargeAmtC > 0) addLine('CR', '{Charge.Expense}', chargeKey, chargeAmtC);
          else addLine('DR', '{Charge.Expense}', chargeKey, Math.abs(chargeAmtC));
        }

        // Interest (line 268-278): two-account form with SAME account for both DR/CR.
        // interestAmt < 0 → InterestExp; amt = interestAmt.negate() (positive) → DR (first account).
        // interestAmt > 0 → InterestRev; amt = interestAmt.negate() (negative) → CR (second account).
        var intAmtC = convertCents(l.interestamt);
        if (intAmtC !== 0) {
          if (intAmtC < 0) addLine('DR', '{Bank.InterestExp}', bankAcctId, Math.abs(intAmtC));  // expense
          else             addLine('CR', '{Bank.InterestRev}', bankAcctId, intAmtC);             // income
        }
      });

      // Currency balancing line (Fact.balanceAccounting — after all lines if ΣDR≠ΣCR)
      var sumDR = 0, sumCR = 0;
      Object.keys(agg).forEach(function (k) {
        if (k.indexOf('DR:') === 0) sumDR += agg[k]; else sumCR += agg[k];
      });
      var balance = { netCents: sumCR - sumDR };  // positive = need more DR; negative = need more CR
      if (useCurrBal && balance.netCents !== 0) {
        var balAcctElem = resolve('{AcctSchema.CurrencyBalance}', schemaId, schemaId);
        if (balAcctElem != null) {
          if (balance.netCents > 0) { addLine('DR', '{AcctSchema.CurrencyBalance}', schemaId, balance.netCents); }
          else                      { addLine('CR', '{AcctSchema.CurrencyBalance}', schemaId, Math.abs(balance.netCents)); }
        } else {
          absent.push({ token: '{AcctSchema.CurrencyBalance}', key: schemaId, schema: schemaId });
        }
      }

      // re-compute final sums after potential balance line
      sumDR = sumCR = 0;
      Object.keys(agg).forEach(function (k) {
        if (k.indexOf('DR:') === 0) sumDR += agg[k]; else sumCR += agg[k];
      });

      bySchema[schemaId] = { agg: agg, detail: detail, absent: absent, sumDR: sumDR, sumCR: sumCR, balance: balance, schemaId: schemaId };
    });

    return { bySchema: bySchema, absent: allAbsent };
  }

  var API = { foldBankStatement: foldBankStatement };
  if (typeof module !== 'undefined' && module.exports) module.exports = API;
  if (typeof window !== 'undefined') window.ReportBankStatement = API;
  else if (global) global.ReportBankStatement = API;
})(typeof globalThis !== 'undefined' ? globalThis : this);
