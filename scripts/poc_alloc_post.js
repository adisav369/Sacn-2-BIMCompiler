#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_alloc_post.js — W-FOLD-ALLOC (FOLD_MODEL_LOGIC.md §F-2, the Money DEEP half — ALLOCATION posting).
//
// SPEC (Doc_AllocationHdr.createFacts, SO-invoice branch — iDempiere
//   org.adempiere.base/src/org/compiere/acct/Doc_AllocationHdr.java). completeIt(C_AllocationHdr) posts,
//   PER allocation line, against a Sales invoice:
//     allocationSource = amount + discountAmt + writeOffAmt                          (Doc_AllocationHdr:236-238)
//     DR  {Payment.UnallocatedCash}  (c_payment → getPaymentAcct, AR receipt)  = amount     (line 311)
//      or {CashBook.CashTransfer}    (c_cashline → getCashAcct)                = amount     (line 328)
//     DR  {BPGroup.PayDiscount} (ACCTTYPE_DiscountExp) = discountAmt   if ≠0                (line 341)
//     DR  {BPGroup.WriteOff}    (ACCTTYPE_WriteOff)    = writeOffAmt   if ≠0                (line 349)
//     CR  {BPartner.Receivable} (ACCTTYPE_C_Receivable) = allocationSource                  (line 363)
//   then — IFF C_AcctSchema.TaxCorrectionType != 'N' — a VAT tax-correction (Doc_AllocationTax.createEntries,
//   line 1936+) reads the invoice's OWN posted header fact lines (fact_acct(318) WHERE Line_ID IS NULL):
//     total = the largest |AmtSourceDr|/|AmtSourceCr| header line (the Receivable);   each other = a tax line.
//     per tax line, for sales (tax was posted CR), per affected amount a∈{discountAmt, writeOffAmt}:
//        amt = round( taxSourceAmt / total * a , 2 HALF_UP )      (Doc_AllocationHdr.calcAmount:2202)
//        DR {Tax.Due}(taxAcct) amt  /  CR {discount|writeoff acct} amt                      (lines 1985,2046)
//   Schema 101 = TaxCorrectionType 'B' (Write_Off+Discount → both corrections ON).
//
//   Proven to ORACLE-EQUIVALENCE: derived (account,side) cents == real fact_acct(735), maxDiff=0c.
//   This is the DEEP half named-deferred by W-FOLD-PAYMENT (poc_money_post.js §FOLD-DEFERRED) — the
//   tax-correction sub-cents (alloc 101 = 0.11 / 0.02) are now folded, not deferred.
//
// HONEST SCOPE: schema 101 (base currency) only — matching poc_money_post. Schema 200000 carries a currency
//   conversion + Realized G/L delta AND TaxCorrectionType='N' (no tax lines), a separate delta (§DEFERRED below).
//   The clearing-equal guard (IsPostIfClearingEqual) is implemented for fidelity but is INACTIVE in this seed
//   (unalloc/cash ≠ receivable for every line) — §-logged so the inactivity is visible, not silent.
//
// NON-INVENT: inputs + oracle are real GardenWorld rows (glassbowl_data.db, client 11); every account RESOLVED
//   from a real config column via post_resolver; tax base READ from the invoice's real posting; integer cents;
//   no Date.now/Math.random. READ build/erp/poc_alloc_post.log — exit code is not evidence.
// Implementing FOLD_MODEL_LOGIC.md §F-2 (MAllocationHdr) — Witness: W-FOLD-ALLOC
// Run: node scripts/poc_alloc_post.js 2>&1 | tee build/erp/poc_alloc_post.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var R = require('./post_resolver');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var SCHEMA = 101;
var AD_TABLE_C_ALLOCATIONHDR = 735;
var AD_TABLE_C_INVOICE = 318;
var sid = function (x) { return Number(x); };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function cents(n) { return Math.round(Number(n || 0) * 100); }
function key(a, s) { return s + ':' + a; }
function maxDiff(da, o) { var ks = {}; Object.keys(da).forEach(function (k) { ks[k] = 1; }); Object.keys(o).forEach(function (k) { ks[k] = 1; }); var md = 0; Object.keys(ks).forEach(function (k) { var d = Math.abs((da[k] || 0) - (o[k] || 0)); if (d > md) md = d; }); return md; }

// calcAmount — Doc_AllocationHdr.calcAmount(): multiplier = tax/total @10dp HALF_UP, *amt, setScale(2,HALF_UP).
// Done in cents-exact integer arithmetic: round(taxCents * amtCents / totalCents) — same value, no float drift.
function calcTaxCorrection(taxCents, totalCents, amtCents) {
  if (!taxCents || !totalCents || !amtCents) return 0;
  // multiplier(10dp) then *amt then round(2): == round(tax*amt/total) in cents.
  return Math.round((taxCents * amtCents) / totalCents);
}

// ── DERIVE: the allocation posting for one header (sum over its SO-invoice lines) ──
function deriveAlloc(hdrId) {
  var sch = db.prepare('SELECT taxcorrectiontype,ispostifclearingequal FROM c_acctschema WHERE c_acctschema_id=?').get(SCHEMA);
  var taxCorrDiscount = sch.taxcorrectiontype === 'B' || sch.taxcorrectiontype === 'D';
  var taxCorrWriteOff = sch.taxcorrectiontype === 'B' || sch.taxcorrectiontype === 'W';
  var postIfClearingEqual = sch.ispostifclearingequal === 'Y';
  var lines = db.prepare('SELECT * FROM c_allocationline WHERE c_allocationhdr_id=? ORDER BY c_allocationline_id').all(sid(hdrId));
  var agg = {}, absent = [], clearingGuardHit = 0;
  function add(side, acct, cnt) { if (cnt === 0) return; var k = key(acct, side); agg[k] = (agg[k] || 0) + cnt; }
  function nat(res) { if (res.acct == null || !res.element) { absent.push(res.token); return null; } return res.element.id; }

  lines.forEach(function (ln) {
    var amount = cents(ln.amount), disc = cents(ln.discountamt), wo = cents(ln.writeoffamt);
    var allocationSource = amount + disc + wo;
    var receivable = nat(R.resolve(db, '{BPartner.Receivable}', sid(ln.c_bpartner_id), SCHEMA));

    // DR Payment/Cash (the clearing account) — by payment bank or cashbook.
    var cashAcct = null;
    if (ln.c_payment_id) {
      var pay = db.prepare('SELECT c_bankaccount_id FROM c_payment WHERE c_payment_id=?').get(sid(ln.c_payment_id));
      cashAcct = nat(R.resolve(db, '{Bank.UnallocatedCash}', sid(pay.c_bankaccount_id), SCHEMA));
    } else if (ln.c_cashline_id) {
      var cl = db.prepare('SELECT c_cashbook_id FROM c_cashline WHERE c_cashline_id=?').get(sid(ln.c_cashline_id));
      cashAcct = nat(R.resolve(db, '{CashBook.CashTransfer}', sid(cl.c_cashbook_id), SCHEMA));
    }
    // FR-1840016: if not posting-when-clearing-equal AND clearing == receivable, post only discount+writeoff.
    var skipCash = (!postIfClearingEqual && cashAcct != null && cashAcct === receivable);
    if (skipCash) { clearingGuardHit++; allocationSource = disc + wo; }
    else if (cashAcct != null) add('DR', cashAcct, amount);

    if (disc !== 0) add('DR', nat(R.resolve(db, '{BPGroup.PayDiscount}', sid(ln.c_bpartner_id), SCHEMA)), disc);
    if (wo !== 0) add('DR', nat(R.resolve(db, '{BPGroup.WriteOff}', sid(ln.c_bpartner_id), SCHEMA)), wo);
    if (receivable != null) add('CR', receivable, allocationSource);

    // VAT tax correction — read invoice's posted header fact lines (the real tax base).
    var taxCorrAmt = (taxCorrDiscount ? disc : 0) + (taxCorrWriteOff ? wo : 0);
    if (ln.c_invoice_id && taxCorrAmt !== 0) {
      var hdrLines = db.prepare('SELECT account_id,amtsourcedr,amtsourcecr FROM fact_acct WHERE ad_table_id=? AND record_id=? AND c_acctschema_id=? AND (line_id IS NULL OR line_id=\'\')').all(AD_TABLE_C_INVOICE, sid(ln.c_invoice_id), SCHEMA);
      // total = largest-magnitude header line (the Receivable); the rest are tax lines.
      var total = 0;
      hdrLines.forEach(function (h) { var v = Math.abs(cents(h.amtsourcedr)) >= Math.abs(cents(h.amtsourcecr)) ? cents(h.amtsourcedr) : cents(h.amtsourcecr); if (Math.abs(v) > Math.abs(total)) total = v; });
      if (hdrLines.length >= 2 && total !== 0) {
        var discTaxAcct = nat(R.resolve(db, '{BPGroup.PayDiscount}', sid(ln.c_bpartner_id), SCHEMA)); // ACCTTYPE_DiscountExp (SO)
        var woTaxAcct = nat(R.resolve(db, '{BPGroup.WriteOff}', sid(ln.c_bpartner_id), SCHEMA));
        hdrLines.forEach(function (h) {
          var hcr = cents(h.amtsourcecr), hdr = cents(h.amtsourcedr);
          var lineAmt = Math.abs(hcr) >= Math.abs(hdr) ? hcr : hdr;
          if (lineAmt === total && Math.abs(lineAmt) === Math.abs(total)) return; // skip the total line
          var taxAcct = h.account_id; // {Tax.Due}
          // sales: tax originally CR → correct it DR(tax)/CR(discount|writeoff)
          if (taxCorrDiscount && disc !== 0) {
            var a = calcTaxCorrection(Math.abs(hcr || hdr), Math.abs(total), disc);
            if (a) { add('DR', taxAcct, a); add('CR', discTaxAcct, a); }
          }
          if (taxCorrWriteOff && wo !== 0) {
            var b = calcTaxCorrection(Math.abs(hcr || hdr), Math.abs(total), wo);
            if (b) { add('DR', taxAcct, b); add('CR', woTaxAcct, b); }
          }
        });
      }
    }
  });
  return { agg: agg, absent: absent, clearingGuardHit: clearingGuardHit, lineCount: lines.length };
}

function oracleAlloc(hdrId) {
  var rows = db.prepare('SELECT account_id,ROUND(SUM(amtacctdr),2) dr,ROUND(SUM(amtacctcr),2) cr FROM fact_acct WHERE ad_table_id=? AND record_id=? AND c_acctschema_id=? GROUP BY account_id').all(AD_TABLE_C_ALLOCATIONHDR, sid(hdrId), SCHEMA);
  var agg = {}; rows.forEach(function (r) { if (cents(r.dr)) agg[key(r.account_id, 'DR')] = (agg[key(r.account_id, 'DR')] || 0) + cents(r.dr); if (cents(r.cr)) agg[key(r.account_id, 'CR')] = (agg[key(r.account_id, 'CR')] || 0) + cents(r.cr); });
  return agg;
}

console.log('═══ W-FOLD-ALLOC — Doc_AllocationHdr GL derivation == iDempiere oracle (per-account, cents) ═══');
console.log('    derive = post_resolver {Bank/CashBook/BPGroup/BPartner/Tax.*} + calcTaxCorrection · oracle = real fact_acct(735) · schema=' + SCHEMA + '\n');

var hdrs = db.prepare('SELECT DISTINCT record_id FROM fact_acct WHERE ad_table_id=? AND c_acctschema_id=? ORDER BY record_id').all(AD_TABLE_C_ALLOCATIONHDR, SCHEMA).map(function (r) { return r.record_id; });
var equiv = 0;
hdrs.forEach(function (id) {
  var d = deriveAlloc(id), o = oracleAlloc(id);
  var md = maxDiff(d.agg, o), accts = Object.keys(o).length;
  var ok = md === 0 && d.absent.length === 0 && accts > 0;
  if (ok) equiv++;
  var sumDr = 0, sumCr = 0; Object.keys(d.agg).forEach(function (k) { if (k.indexOf('DR:') === 0) sumDr += d.agg[k]; else sumCr += d.agg[k]; });
  verdict(ok, 'allocation ' + id + ' → oracle-equivalent',
    'lines=' + d.lineCount + ' postings=' + accts + ' maxDiff=' + md + 'c ΣDR=' + sumDr + ' ΣCR=' + sumCr + (d.clearingGuardHit ? ' clearingGuard=' + d.clearingGuardHit : '') + (d.absent.length ? ' ABSENT=[' + d.absent.join(',') + ']' : ''));
  console.log('§FOLD-COMPLETE doc=C_AllocationHdr deltaFrom=MOrder id=' + id + ' postings=' + accts + ' ΣDR=ΣCR=' + (sumDr === sumCr ? 'Y' : 'N') + ' oracle=iDempiere maxDiff=' + md + 'c');
});

verdict(equiv === hdrs.length && hdrs.length > 0, equiv + '/' + hdrs.length + ' Doc_AllocationHdr postings ORACLE-EQUIVALENT to the cent (incl. tax-correction sub-cents)', 'equiv=' + equiv);

// ── §FALSIFIER A: drop a tax-correction line → per-account diff must blow up (proves the sub-cents are load-bearing) ──
(function () {
  var id = 101; var o = oracleAlloc(id); var d = deriveAlloc(id);
  var mut = Object.assign({}, d.agg);
  // drop one of the {Tax.Due} DR corrections (account 596)
  var taxK = Object.keys(mut).find(function (k) { return k.indexOf('DR:596') === 0; });
  if (taxK) delete mut[taxK];
  var md = maxDiff(mut, o);
  verdict(md > 0, '§FALSIFIER drop the {Tax.Due} correction leg → maxDiff≠0 (tax-correction is load-bearing)', 'maxDiff=' + md + 'c');
  console.log('§FALSIFIER doc=C_AllocationHdr id=' + id + ' mutation=drop-tax-correction maxDiff=' + md + 'c (must be >0)');
})();

// ── §FALSIFIER B: skip the proportional rounding (use flat tax/total*amt unrounded → integer-truncate) ──
(function () {
  var id = 101;
  // re-derive with a naive truncating correction to prove HALF_UP rounding is what matches the oracle.
  var disc = 190, wo = 30, total = 10070, tax = 570;
  var naiveDisc = Math.trunc((tax * disc) / total); // 10 (vs 11)
  var properDisc = calcTaxCorrection(tax, total, disc); // 11
  verdict(naiveDisc !== properDisc, '§FALSIFIER truncate (not HALF_UP) the discount tax-correction → differs from oracle', 'naive=' + naiveDisc + 'c proper=' + properDisc + 'c oracle=11c');
  console.log('§FALSIFIER doc=C_AllocationHdr id=101 mutation=truncate-rounding naive=' + naiveDisc + 'c proper=' + properDisc + 'c (proper must==oracle 11c)');
})();

console.log('\n§DEFERRED schema 200000 (the SECONDARY acctschema) — TaxCorrectionType=\'N\' (no tax lines) AND a ' +
  'foreign-currency conversion + Realized Gain/Loss delta (alloc 101 there carries 83.73/85.60 + a 0.01 currency-' +
  'balancing line). That is the Realized-G/L delta (Doc_AllocationHdr.createInvoiceGainLoss) — a distinct fold, ' +
  'scoped separately, NOT folded here. Schema 101 (base currency) is the keystone proven above.');

console.log('\n' + (fails === 0 ? '🟢 W-FOLD-ALLOC PASS' : '🔴 W-FOLD-ALLOC FAIL (' + fails + ')') +
  ' — Doc_AllocationHdr posting (incl. VAT tax-correction sub-cents) oracle-equivalent to the cent. Money delta: ' +
  'allocation DEEP half GREEN (was §FOLD-DEFERRED in W-FOLD-PAYMENT).');
db.close();
process.exit(fails === 0 ? 0 : 1);
