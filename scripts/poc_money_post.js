#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_money_post.js — W-FOLD-PAYMENT (FOLD_MODEL_LOGIC.md §F-2, the Money delta — PAYMENT half).
//
// SPEC (§F-2 MPayment): completeIt(C_Payment) for a receipt posts ONE balanced pair —
//   DR {Bank.InTransit} / CR {Bank.UnallocatedCash}, amount = payamt. Accounts RESOLVED from the
//   bank account's config (c_bankaccount_acct) by post_resolver; amount read from c_payment.payamt.
//   Proven to ORACLE-EQUIVALENCE: derived (account,side) cents == real fact_acct(335), maxDiff=0c.
//   §FALSIFIER drops the DR → maxDiff≠0.
//
// HONEST SCOPE — this is the Money delta's SIMPLE half. The ALLOCATION posting (Doc_AllocationHdr,
//   fact_acct ad_table_id 735) is the DEEP half and is NAMED-DEFERRED: it clears DR UnallocatedCash /
//   CR Receivable but ALSO posts proportional discount + write-off WITH tax-correction sub-cents
//   (iDempiere C_AcctSchema.IsDiscountCorrectsTax — e.g. alloc 101 carries 0.11/0.13/0.02 tax adjusts).
//   That tax-correction is a genuine document-specific delta (one of the ~5 deep ones in the archetype
//   table), scoped as the next step — not folded here. §FOLD-DEFERRED below states it precisely.
//
// NON-INVENT: inputs + oracle are real GardenWorld rows (glassbowl_data.db, client 11); integer cents;
//   no Date.now/Math.random. READ build/erp/poc_money_post.log — exit code is not evidence.
// Implementing FOLD_MODEL_LOGIC.md §F-2 (MPayment) — Witness: W-FOLD-PAYMENT
// Run: node scripts/poc_money_post.js 2>&1 | tee build/erp/poc_money_post.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var R = require('./post_resolver');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var SCHEMA = 101;
var AD_TABLE_C_PAYMENT = 335;
var sid = function (x) { return Number(x); };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function cents(n) { return Math.round(Number(n || 0) * 100); }
function key(a, s) { return s + ':' + a; }
function maxDiff(da, o) { var ks = {}; Object.keys(da).forEach(function (k) { ks[k] = 1; }); Object.keys(o).forEach(function (k) { ks[k] = 1; }); var md = 0; Object.keys(ks).forEach(function (k) { var d = Math.abs((da[k] || 0) - (o[k] || 0)); if (d > md) md = d; }); return md; }

// ── DERIVE: Doc_Payment receipt — DR {Bank.InTransit} / CR {Bank.UnallocatedCash} = payamt ──
function derivePayment(payId) {
  var p = db.prepare('SELECT c_payment_id,c_bankaccount_id,payamt,isreceipt FROM c_payment WHERE c_payment_id=?').get(sid(payId));
  var agg = {}, absent = [];
  function add(side, acct, amt) { var k = key(acct, side); agg[k] = (agg[k] || 0) + cents(amt); }
  function nat(res) { if (res.acct == null || !res.element) { absent.push(res.token); return null; } return res.element.id; }
  var inTransit = nat(R.resolve(db, '{Bank.InTransit}', sid(p.c_bankaccount_id), SCHEMA));
  var unalloc = nat(R.resolve(db, '{Bank.UnallocatedCash}', sid(p.c_bankaccount_id), SCHEMA));
  // receipt: money in → DR in-transit, CR unallocated. (payment out would reverse; both GardenWorld pmts are receipts.)
  var drAcct = p.isreceipt === 'Y' ? inTransit : unalloc;
  var crAcct = p.isreceipt === 'Y' ? unalloc : inTransit;
  if (drAcct != null) add('DR', drAcct, p.payamt);
  if (crAcct != null) add('CR', crAcct, p.payamt);
  return { agg: agg, absent: absent, isreceipt: p.isreceipt, payamt: p.payamt };
}
function oraclePayment(payId) {
  var rows = db.prepare('SELECT account_id,ROUND(SUM(amtacctdr),2) dr,ROUND(SUM(amtacctcr),2) cr FROM fact_acct WHERE ad_table_id=? AND record_id=? AND c_acctschema_id=? GROUP BY account_id').all(AD_TABLE_C_PAYMENT, sid(payId), SCHEMA);
  var agg = {}; rows.forEach(function (r) { if (cents(r.dr)) agg[key(r.account_id, 'DR')] = cents(r.dr); if (cents(r.cr)) agg[key(r.account_id, 'CR')] = cents(r.cr); });
  return agg;
}

console.log('═══ W-FOLD-PAYMENT — Doc_Payment GL derivation == iDempiere oracle (per-account, cents) ═══');
console.log('    derive = post_resolver {Bank.*} + c_payment.payamt · oracle = real fact_acct(335) · schema=' + SCHEMA + '\n');

var pays = db.prepare('SELECT DISTINCT record_id FROM fact_acct WHERE ad_table_id=? AND c_acctschema_id=? ORDER BY record_id').all(AD_TABLE_C_PAYMENT, SCHEMA).map(function (r) { return r.record_id; });
var equiv = 0;
pays.forEach(function (id) {
  var d = derivePayment(id), o = oraclePayment(id);
  var md = maxDiff(d.agg, o), accts = Object.keys(o).length;
  var ok = md === 0 && d.absent.length === 0 && accts > 0;
  if (ok) equiv++;
  var sumDr = 0, sumCr = 0; Object.keys(d.agg).forEach(function (k) { if (k.indexOf('DR:') === 0) sumDr += d.agg[k]; else sumCr += d.agg[k]; });
  verdict(ok, 'payment ' + id + ' (' + (d.isreceipt === 'Y' ? 'receipt' : 'payment') + ' ' + d.payamt + ') → oracle-equivalent',
    'accts=' + accts + ' maxDiff=' + md + 'c ΣDR=' + sumDr + ' ΣCR=' + sumCr + (d.absent.length ? ' ABSENT=[' + d.absent.join(',') + ']' : ''));
  console.log('§FOLD-COMPLETE doc=C_Payment id=' + id + ' isreceipt=' + d.isreceipt + ' payamt=' + d.payamt + ' postings=' + accts + ' ΣDR=ΣCR=' + (sumDr === sumCr ? 'Y' : 'N') + ' oracle=iDempiere maxDiff=' + md + 'c');
});

verdict(equiv === pays.length && pays.length > 0, equiv + '/' + pays.length + ' Doc_Payment postings ORACLE-EQUIVALENT to the cent', 'equiv=' + equiv);

// ── §FALSIFIER: drop the DR leg → per-account diff must blow up ──
(function () {
  var id = pays[0]; var d = derivePayment(id), o = oraclePayment(id);
  var mut = Object.assign({}, d.agg); var drK = Object.keys(mut).find(function (k) { return k.indexOf('DR:') === 0; }); delete mut[drK];
  var md = maxDiff(mut, o);
  verdict(md > 0, '§FALSIFIER drop the DR(in-transit) leg → maxDiff≠0 (diff is load-bearing)', 'maxDiff=' + md + 'c');
  console.log('§FALSIFIER doc=C_Payment id=' + id + ' mutation=drop-DR maxDiff=' + md + 'c (must be >0)');
})();

console.log('\n§FOLD-DEFERRED Doc_AllocationHdr (fact_acct ad_table_id 735) — the allocation posting clears ' +
  'DR {Bank.UnallocatedCash}/CR {BPartner.Receivable} but ALSO posts proportional discount + write-off WITH ' +
  'tax-correction sub-cents (C_AcctSchema.IsDiscountCorrectsTax; alloc 101 = 0.11/0.13/0.02). That tax-correction ' +
  'is a genuine deep delta — scoped as the next step, NOT folded here. (Also: cash allocation 100 rides Doc_Cash.)');

console.log('\n' + (fails === 0 ? '🟢 W-FOLD-PAYMENT PASS' : '🔴 W-FOLD-PAYMENT FAIL (' + fails + ')') +
  ' — Doc_Payment receipt posting oracle-equivalent to the cent; allocation tax-correction named-deferred. Money delta: payment half GREEN.');
db.close();
process.exit(fails === 0 ? 0 : 1);
