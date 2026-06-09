#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_alloc_fx.js — W-FOLD-ALLOC-FX (FOLD_MODEL_LOGIC.md REMAINING-TAIL #3, the foreign-currency allocation half).
//
// SPEC: Doc_AllocationHdr posts to EVERY acctschema. Schema 200000 is GardenWorld's SECOND schema (EUR, ccy 102);
//   the source documents are USD (ccy 100, = schema 101's currency). The fold is the SAME line manifest as
//   W-FOLD-ALLOC (poc_alloc_post.js) but with two schema-200000 deltas, BOTH derived from the real oracle:
//     (1) CURRENCY CONVERSION — each fact leg's accounted amount = round(AmtSource × multiplyRate, 2 HALF_UP),
//         the rate read from c_conversion_rate (default Spot type, valid on dateacct: USD→EUR = 0.85 on 2002-02-22).
//         iDempiere converts each FactLine INDEPENDENTLY: the DR legs convert their own source amounts, the CR
//         Receivable converts the source allocationSource — so per-leg rounding differences accumulate.
//     (2) NO TAX-CORRECTION — schema 200000 TaxCorrectionType='N' (schema 101 was 'B'), so the VAT sub-cents
//         folded by W-FOLD-ALLOC are ABSENT here.
//   Then Fact.balanceAccounting posts ONE CurrencyBalancing line (c_acctschema_gl.currencybalancing_acct → 724
//   "Currency balancing") absorbing the per-document accounted imbalance ΣAcctDr−ΣAcctCr (alloc 101 = 0.01 CR).
//   This is the §DEFERRED named by W-FOLD-ALLOC (poc_alloc_post.js, the createInvoiceGainLoss / FX-balancing delta).
//
//   Proven to ORACLE-EQUIVALENCE: derived (account,side) cents == real fact_acct(735) schema 200000, maxDiff=0c.
//
// NON-INVENT: source amounts from real c_allocationline; rate/balancing-account/default-type READ from real config
//   (c_conversion_rate / c_acctschema_gl / c_conversiontype); accounts RESOLVED via post_resolver at schema 200000;
//   the rate's exact decimal is preserved as TEXT and multiplied in BigInt so the HALF_UP rounding can't float-drift
//   (site/bigdecimal.js discipline). Deterministic, integer cents, no Date.now/Math.random.
//   READ build/erp/poc_alloc_fx.log — exit code is not evidence.
// Implementing FOLD_MODEL_LOGIC.md REMAINING-TAIL #3 (Allocation schema-200000 FX) — Witness: W-FOLD-ALLOC-FX
// Run: node scripts/poc_alloc_fx.js 2>&1 | tee build/erp/poc_alloc_fx.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var R = require('./post_resolver');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var SCHEMA = 200000;              // the SECOND acctschema (EUR)
var AD_TABLE_C_ALLOCATIONHDR = 735;
var sid = function (x) { return Number(x); };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function cents(n) { return Math.round(Number(n || 0) * 100); }
function key(a, s) { return s + ':' + a; }
function maxDiff(da, o) { var ks = {}; Object.keys(da).forEach(function (k) { ks[k] = 1; }); Object.keys(o).forEach(function (k) { ks[k] = 1; }); var md = 0; Object.keys(ks).forEach(function (k) { var d = Math.abs((da[k] || 0) - (o[k] || 0)); if (d > md) md = d; }); return md; }

// schema/source currencies (READ, not assumed): schema 200000 = EUR; the source/document ccy = schema 101's currency (USD).
var SCHEMA_CCY = db.prepare('SELECT c_currency_id FROM c_acctschema WHERE c_acctschema_id=?').get(SCHEMA).c_currency_id;
var SRC_CCY = db.prepare('SELECT c_currency_id FROM c_acctschema WHERE c_acctschema_id=101').get().c_currency_id;
var DEFAULT_CONVTYPE = db.prepare("SELECT c_conversiontype_id FROM c_conversiontype WHERE isdefault='Y'").get().c_conversiontype_id;
var BALANCING_VC = db.prepare('SELECT currencybalancing_acct FROM c_acctschema_gl WHERE c_acctschema_id=?').get(SCHEMA).currencybalancing_acct;
var BALANCING_ACCT = (function () { var r = R.elementOf(db, BALANCING_VC); return r ? r.id : null; })();

// pick the default-Spot rate valid on dateacct (string compare on 'YYYY-MM-DD ...' is monotonic); most-recent validfrom wins.
function pickRate(dateacct) {
  var rows = db.prepare(
    'SELECT multiplyrate FROM c_conversion_rate WHERE c_currency_id=? AND c_currency_id_to=? AND c_conversiontype_id=? ' +
    'AND validfrom<=? AND validto>=? ORDER BY validfrom DESC'
  ).all(SRC_CCY, SCHEMA_CCY, DEFAULT_CONVTYPE, dateacct, dateacct);
  return rows.length ? rows[0].multiplyrate : null;
}

// convert(srcCents, rateStr) = round(srcCents × rate, HALF_UP) in BigInt — rate = parsed (num / 10^scale), exact.
function convert(srcCents, rateStr) {
  if (srcCents === 0) return 0;
  var neg = srcCents < 0; var s = Math.abs(srcCents);
  var dot = rateStr.indexOf('.');
  var digits = dot < 0 ? rateStr : rateStr.slice(0, dot) + rateStr.slice(dot + 1);
  var scale = dot < 0 ? 0 : rateStr.length - dot - 1;
  var num = BigInt(digits);                 // rate = num / 10^scale
  var D = 10n ** BigInt(scale);
  var P = BigInt(s) * num;                   // = srcCents × rate × 10^scale
  var q = P / D, r = P % D;
  if (2n * r >= D) q += 1n;                  // HALF_UP (operands positive)
  var out = Number(q);
  return neg ? -out : out;
}

// ── DERIVE: the FX allocation posting for one header ──
function deriveAllocFx(hdrId) {
  var hdr = db.prepare('SELECT dateacct FROM c_allocationhdr WHERE c_allocationhdr_id=?').get(sid(hdrId));
  var rate = pickRate(hdr.dateacct);
  var lines = db.prepare('SELECT * FROM c_allocationline WHERE c_allocationhdr_id=? ORDER BY c_allocationline_id').all(sid(hdrId));
  var agg = {}, absent = [];
  function add(side, acct, cnt) { if (cnt === 0 || acct == null) return; var k = key(acct, side); agg[k] = (agg[k] || 0) + cnt; }
  function nat(res) { if (res.acct == null || !res.element) { absent.push(res.token); return null; } return res.element.id; }

  lines.forEach(function (ln) {
    var amount = cents(ln.amount), disc = cents(ln.discountamt), wo = cents(ln.writeoffamt);
    var allocationSource = amount + disc + wo;                 // source-currency receivable (USD cents)
    var receivable = nat(R.resolve(db, '{BPartner.Receivable}', sid(ln.c_bpartner_id), SCHEMA));

    var cashAcct = null;
    if (ln.c_payment_id) {
      var pay = db.prepare('SELECT c_bankaccount_id FROM c_payment WHERE c_payment_id=?').get(sid(ln.c_payment_id));
      cashAcct = nat(R.resolve(db, '{Bank.UnallocatedCash}', sid(pay.c_bankaccount_id), SCHEMA));
    } else if (ln.c_cashline_id) {
      var cl = db.prepare('SELECT c_cashbook_id FROM c_cashline WHERE c_cashline_id=?').get(sid(ln.c_cashline_id));
      cashAcct = nat(R.resolve(db, '{CashBook.CashTransfer}', sid(cl.c_cashbook_id), SCHEMA));
    }
    // each leg converts its OWN source amount independently (the FX delta vs schema 101):
    if (cashAcct != null) add('DR', cashAcct, convert(amount, rate));
    if (disc !== 0) add('DR', nat(R.resolve(db, '{BPGroup.PayDiscount}', sid(ln.c_bpartner_id), SCHEMA)), convert(disc, rate));
    if (wo !== 0) add('DR', nat(R.resolve(db, '{BPGroup.WriteOff}', sid(ln.c_bpartner_id), SCHEMA)), convert(wo, rate));
    if (receivable != null) add('CR', receivable, convert(allocationSource, rate));
    // schema 200000 TaxCorrectionType='N' → NO tax-correction legs (the W-FOLD-ALLOC sub-cents are absent here).
  });

  // Fact.balanceAccounting: ONE CurrencyBalancing line absorbs the per-document accounted imbalance.
  var sumDr = 0, sumCr = 0;
  Object.keys(agg).forEach(function (k) { if (k.indexOf('DR:') === 0) sumDr += agg[k]; else sumCr += agg[k]; });
  var imbalance = sumDr - sumCr;                               // +ve → Dr heavy → add CR balancing
  if (imbalance !== 0) add(imbalance > 0 ? 'CR' : 'DR', BALANCING_ACCT, Math.abs(imbalance));

  return { agg: agg, absent: absent, rate: rate, lineCount: lines.length, imbalance: imbalance };
}

function oracleAlloc(hdrId) {
  var rows = db.prepare('SELECT account_id,ROUND(SUM(amtacctdr),2) dr,ROUND(SUM(amtacctcr),2) cr FROM fact_acct WHERE ad_table_id=? AND record_id=? AND c_acctschema_id=? GROUP BY account_id').all(AD_TABLE_C_ALLOCATIONHDR, sid(hdrId), SCHEMA);
  var agg = {}; rows.forEach(function (r) { if (cents(r.dr)) agg[key(r.account_id, 'DR')] = (agg[key(r.account_id, 'DR')] || 0) + cents(r.dr); if (cents(r.cr)) agg[key(r.account_id, 'CR')] = (agg[key(r.account_id, 'CR')] || 0) + cents(r.cr); });
  return agg;
}

console.log('═══ W-FOLD-ALLOC-FX — Doc_AllocationHdr GL in the EUR schema (200000) == iDempiere oracle (cents) ═══');
console.log('    derive = post_resolver + per-leg FX conversion + CurrencyBalancing · oracle = real fact_acct(735) · schema=' + SCHEMA);
console.log('    src ccy=' + SRC_CCY + ' → schema ccy=' + SCHEMA_CCY + ' · default convType=' + DEFAULT_CONVTYPE + ' · balancing acct=' + BALANCING_ACCT + '\n');

var hdrs = db.prepare('SELECT DISTINCT record_id FROM fact_acct WHERE ad_table_id=? AND c_acctschema_id=? ORDER BY record_id').all(AD_TABLE_C_ALLOCATIONHDR, SCHEMA).map(function (r) { return r.record_id; });
var equiv = 0;
hdrs.forEach(function (id) {
  var d = deriveAllocFx(id), o = oracleAlloc(id);
  var md = maxDiff(d.agg, o), accts = Object.keys(o).length;
  var ok = md === 0 && d.absent.length === 0 && accts > 0;
  if (ok) equiv++;
  verdict(ok, 'FX allocation ' + id + ' → oracle-equivalent',
    'lines=' + d.lineCount + ' rate=' + d.rate + ' postings=' + accts + ' maxDiff=' + md + 'c balancing=' + d.imbalance + 'c' + (d.absent.length ? ' ABSENT=[' + d.absent.join(',') + ']' : ''));
  console.log('§FOLD-COMPLETE doc=C_AllocationHdr schema=' + SCHEMA + ' id=' + id + ' rate=' + d.rate + ' postings=' + accts + ' currencyBalancing=' + d.imbalance + 'c oracle=iDempiere maxDiff=' + md + 'c');
});

verdict(equiv === hdrs.length && hdrs.length > 0, equiv + '/' + hdrs.length + ' Doc_AllocationHdr EUR-schema postings ORACLE-EQUIVALENT to the cent (FX conversion + currency-balancing)', 'equiv=' + equiv);

// ── §FALSIFIER-A: drop the CurrencyBalancing line → the per-account diff blows up (the 0.01 is load-bearing) ──
(function () {
  var id = hdrs.find(function (h) { return deriveAllocFx(h).imbalance !== 0; }) || hdrs[0];
  var d = deriveAllocFx(id), o = oracleAlloc(id);
  var mut = {}; Object.keys(d.agg).forEach(function (k) { mut[k] = d.agg[k]; });
  var balK = key(BALANCING_ACCT, d.imbalance > 0 ? 'CR' : 'DR');
  delete mut[balK];
  var md = maxDiff(mut, o);
  verdict(md > 0, '§FALSIFIER-A drop the CurrencyBalancing line on alloc ' + id + ' → maxDiff≠0 (the FX rounding line is load-bearing)', 'maxDiff=' + md + 'c');
  console.log('§FALSIFIER-A doc=' + id + ' mutation=drop-currency-balancing maxDiff=' + md + 'c (must be >0)');
})();

// ── §FALSIFIER-B: use the WRONG rate (the post-2003 0.80064 rate, invalid on 2002-02-22) → diff blows up ──
(function () {
  var id = hdrs[hdrs.length - 1];
  var wrongRate = db.prepare("SELECT multiplyrate FROM c_conversion_rate WHERE c_currency_id=? AND c_currency_id_to=? ORDER BY validfrom DESC LIMIT 1").get(SRC_CCY, SCHEMA_CCY).multiplyrate;
  var realRate = pickRate(db.prepare('SELECT dateacct FROM c_allocationhdr WHERE c_allocationhdr_id=?').get(id).dateacct);
  // re-derive cash leg with the wrong rate and compare a single converted amount
  var ln = db.prepare('SELECT amount FROM c_allocationline WHERE c_allocationhdr_id=? LIMIT 1').get(id);
  var withReal = convert(cents(ln.amount), realRate), withWrong = convert(cents(ln.amount), wrongRate);
  verdict(withReal !== withWrong, '§FALSIFIER-B the date-invalid 0.80064 rate gives a different accounted amount than the valid 0.85', 'real=' + withReal + 'c wrong=' + withWrong + 'c rate ' + realRate + ' vs ' + wrongRate);
  console.log('§FALSIFIER-B doc=' + id + ' mutation=wrong-rate real=' + withReal + 'c(' + realRate + ') wrong=' + withWrong + 'c(' + wrongRate + ') (must differ)');
})();

console.log('\n§FX_NOTE schema 200000 carries NO tax-correction (TaxCorrectionType=\'N\') — the W-FOLD-ALLOC VAT sub-cents ' +
  'do not apply here; the FX-rounding currency-balancing line is the schema-200000-specific delta. This closes the ' +
  '§DEFERRED named by W-FOLD-ALLOC (poc_alloc_post.js).');

console.log('\n' + (fails === 0 ? '🟢 W-FOLD-ALLOC-FX PASS' : '🔴 W-FOLD-ALLOC-FX FAIL (' + fails + ')') +
  ' — foreign-currency Doc_AllocationHdr posting (per-leg conversion + currency-balancing) oracle-equivalent to the cent.');
db.close();
process.exit(fails === 0 ? 0 : 1);
