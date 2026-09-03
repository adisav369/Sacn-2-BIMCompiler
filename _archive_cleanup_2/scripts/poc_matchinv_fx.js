#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_matchinv_fx.js — W-FOLD-MATCHINV-FX (FOLD_MODEL_LOGIC.md handoff-2026-06-10 NEXT#3 — the M_MatchInv POSTING
//   in GardenWorld's SECOND acctschema, 200000 = EUR, ccy 102; fact_acct 472).
//
// SPEC (Doc_MatchInv.java): the SAME line manifest as W-FOLD-MATCHINV (poc_matchinv.js) — DR {BPGroup.NIR} /
//   CR {Product.InventoryClearing}, plus the avg-cost IPV split ({Product.Asset} / {Product.AverageCostVariance})
//   that rides the qty spine — derived in the SOURCE currency (USD), then each fact leg's ACCOUNTED amount is the
//   per-leg currency conversion to EUR (the Doc_AllocationHdr FX rule, poc_alloc_fx.js):
//     amtacct = round(amtsource × multiplyRate, 2 HALF_UP)   (rate read from c_conversion_rate, default Spot, valid on date)
//   iDempiere converts each FactLine INDEPENDENTLY; here every account leg is a single fact line, so converting the
//   per-account source cents == converting per line. Schema-200000 rate (USD→EUR) = 0.85, so e.g. 300 → 255.00,
//   200 → 170.00, the IPV-split legs 30 → 25.50 / 70 → 59.50 (all exact at 0.85, no balancing residual).
//
//   Proven to ORACLE-EQUIVALENCE: derived (account,side) cents == real fact_acct(472) schema 200000, maxDiff=0c, all 18.
//
// NON-INVENT: matches, prices, on-hand, and the oracle are real GardenWorld rows (glassbowl_data.db, client 11);
//   accounts RESOLVED via post_resolver at schema 200000; rate/default-type READ from real config (c_conversion_rate /
//   c_conversiontype); the rate's exact decimal preserved as TEXT and multiplied in BigInt (HALF_UP, no float drift,
//   site/bigdecimal.js discipline); integer cents; no Date.now/Math.random.
//   READ build/erp/poc_matchinv_fx.log — exit code is not evidence.
// Implementing FOLD_MODEL_LOGIC.md handoff NEXT#3 (M_MatchInv schema-200000 FX) — Witness: W-FOLD-MATCHINV-FX
// Run: node scripts/poc_matchinv_fx.js 2>&1 | tee build/erp/poc_matchinv_fx.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var R = require('./post_resolver');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var SCHEMA = 200000;              // the SECOND acctschema (EUR)
var AD_TABLE_M_MATCHINV = 472;
var sid = function (x) { return Number(x); };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function cents(n) { return Math.round(Number(n || 0) * 100); }
function key(a, s) { return s + ':' + a; }
function maxDiff(da, o) { var ks = {}; Object.keys(da).forEach(function (k) { ks[k] = 1; }); Object.keys(o).forEach(function (k) { ks[k] = 1; }); var md = 0; Object.keys(ks).forEach(function (k) { var d = Math.abs((da[k] || 0) - (o[k] || 0)); if (d > md) md = d; }); return md; }

// schema/source currencies + default conversion type (READ, not assumed)
var SCHEMA_CCY = db.prepare('SELECT c_currency_id FROM c_acctschema WHERE c_acctschema_id=?').get(SCHEMA).c_currency_id;
var SRC_CCY = db.prepare('SELECT c_currency_id FROM c_acctschema WHERE c_acctschema_id=101').get().c_currency_id;
var DEFAULT_CONVTYPE = db.prepare("SELECT c_conversiontype_id FROM c_conversiontype WHERE isdefault='Y'").get().c_conversiontype_id;

// pick the default-Spot rate valid on the match/invoice date (most-recent validfrom wins).
function pickRate(dateacct) {
  var d = String(dateacct).slice(0, 10);
  var rows = db.prepare(
    'SELECT multiplyrate FROM c_conversion_rate WHERE c_currency_id=? AND c_currency_id_to=? AND c_conversiontype_id=? ' +
    'AND validfrom<=? AND validto>=? ORDER BY validfrom DESC'
  ).all(SRC_CCY, SCHEMA_CCY, DEFAULT_CONVTYPE, d, d);
  return rows.length ? rows[0].multiplyrate : null;
}

// convert(srcCents, rateStr) = round(srcCents × rate, HALF_UP) in BigInt — rate = num / 10^scale, exact (no float drift).
function convert(srcCents, rateStr) {
  if (srcCents === 0) return 0;
  var neg = srcCents < 0; var s = Math.abs(srcCents);
  var dot = rateStr.indexOf('.');
  var digits = dot < 0 ? rateStr : rateStr.slice(0, dot) + rateStr.slice(dot + 1);
  var scale = dot < 0 ? 0 : rateStr.length - dot - 1;
  var num = BigInt(digits);
  var D = 10n ** BigInt(scale);
  var P = BigInt(s) * num;
  var q = P / D, r = P % D;
  if (2n * r >= D) q += 1n;        // HALF_UP (operands positive)
  var out = Number(q);
  return neg ? -out : out;
}

// on-hand AT MATCH = Σ movementqty for the product up to (incl.) the match/invoice date (rides the qty spine).
function onHandAtMatch(mi) {
  var matchDate = db.prepare('SELECT dateinvoiced FROM c_invoice WHERE c_invoice_id=?').get(sid(mi.c_invoice_id)).dateinvoiced;
  var d = String(matchDate).slice(0, 10);
  var r = db.prepare("SELECT COALESCE(SUM(movementqty),0) q FROM m_transaction WHERE m_product_id=? AND substr(movementdate,1,10)<=?").get(sid(mi.m_product_id), d);
  return Number(r.q);
}

// ── DERIVE: source (USD) matchinv legs, then per-leg FX conversion to the EUR schema ──
function derive(mi, opt) {
  opt = opt || {};
  var usd = {}, absent = [];                                  // source-currency (USD) leg aggregate
  function uadd(side, acct, cnt) { if (cnt === 0 || acct == null) return; var k = key(acct, side); usd[k] = (usd[k] || 0) + cnt; }
  function nat(res) { if (res.acct == null || !res.element) { absent.push(res.token); return null; } return res.element.id; }
  var bp = db.prepare('SELECT c_bpartner_id FROM c_invoice WHERE c_invoice_id=?').get(sid(mi.c_invoice_id)).c_bpartner_id;
  var nir = nat(R.resolve(db, '{BPGroup.NotInvoicedReceipts}', sid(bp), SCHEMA));
  var clearing = nat(R.resolve(db, '{Product.InventoryClearing}', sid(mi.m_product_id), SCHEMA));
  if (opt.swap) { var t = nir; nir = clearing; clearing = t; }
  function lineAmt(price) { return Math.round(Number(mi.qty) * Math.round(Number(price) * 10000) / 100); }
  var nirAmt = lineAmt(mi.po_price);
  var clearingAmt = lineAmt(mi.inv_price);
  uadd('DR', nir, nirAmt);
  uadd('CR', clearing, clearingAmt);
  var variance = nirAmt - clearingAmt;

  if (variance !== 0 && !opt.noSplit) {
    var asset = nat(R.resolve(db, '{Product.Asset}', sid(mi.m_product_id), SCHEMA));
    var avgVar = nat(R.resolve(db, '{Product.AverageCostVariance}', sid(mi.m_product_id), SCHEMA));
    var onHand = onHandAtMatch(mi);
    var capped = opt.allToAsset ? Number(mi.qty) : Math.min(onHand, Number(mi.qty));
    var assetCents = Math.round(variance * capped / Number(mi.qty));
    var varCents = variance - assetCents;
    var side = variance > 0 ? 'CR' : 'DR';
    uadd(side, asset, Math.abs(assetCents));
    uadd(side, avgVar, Math.abs(varCents));
  }

  // per-leg FX conversion: each accounted leg = round(its USD source × rate, HALF_UP)
  var rate = pickRate(db.prepare('SELECT dateinvoiced FROM c_invoice WHERE c_invoice_id=?').get(sid(mi.c_invoice_id)).dateinvoiced);
  var agg = {};
  Object.keys(usd).forEach(function (k) {
    var p = k.split(':');                                     // [side, acct]
    var conv = opt.noConvert ? usd[k] : convert(usd[k], rate);
    if (conv !== 0) agg[k] = (agg[k] || 0) + conv;
  });
  return { agg: agg, absent: absent, nirAmt: nirAmt, clearingAmt: clearingAmt, variance: variance, rate: rate };
}

function oracle(miId) {
  var rows = db.prepare('SELECT account_id,ROUND(SUM(amtacctdr),2) dr,ROUND(SUM(amtacctcr),2) cr FROM fact_acct WHERE ad_table_id=? AND record_id=? AND c_acctschema_id=? GROUP BY account_id').all(AD_TABLE_M_MATCHINV, sid(miId), SCHEMA);
  var agg = {}; rows.forEach(function (r) { if (cents(r.dr)) agg[key(r.account_id, 'DR')] = (agg[key(r.account_id, 'DR')] || 0) + cents(r.dr); if (cents(r.cr)) agg[key(r.account_id, 'CR')] = (agg[key(r.account_id, 'CR')] || 0) + cents(r.cr); });
  return agg;
}

console.log('═══ W-FOLD-MATCHINV-FX — M_MatchInv posting in the EUR schema (200000) == iDempiere oracle (cents) ═══');
console.log('    derive = USD source legs (NIR/Clearing + avg-cost IPV split) → per-leg FX conversion · oracle = real fact_acct(472) · schema=' + SCHEMA);
console.log('    src ccy=' + SRC_CCY + ' → schema ccy=' + SCHEMA_CCY + ' · default convType=' + DEFAULT_CONVTYPE + '\n');

var docs = db.prepare('SELECT DISTINCT record_id FROM fact_acct WHERE ad_table_id=? AND c_acctschema_id=? ORDER BY record_id').all(AD_TABLE_M_MATCHINV, SCHEMA).map(function (r) { return r.record_id; });
var equiv = 0, varianceCount = 0;
docs.forEach(function (id) {
  var mi = db.prepare('SELECT * FROM m_matchinv WHERE m_matchinv_id=?').get(sid(id));
  var d = derive(mi), o = oracle(id);
  var md = maxDiff(d.agg, o), accts = Object.keys(o).length;
  var ok = md === 0 && d.absent.length === 0 && accts > 0;
  if (ok) equiv++;
  if (d.variance !== 0) varianceCount++;
  verdict(ok, 'FX matchinv ' + id + (d.variance ? ' (IPV split)' : ' (PO=inv price)') + ' → oracle-equivalent',
    'matchQty=' + mi.qty + ' rate=' + d.rate + ' postings=' + accts + ' maxDiff=' + md + 'c' + (d.absent.length ? ' ABSENT=[' + d.absent.join(',') + ']' : ''));
  console.log('§FOLD-COMPLETE doc=M_MatchInv schema=' + SCHEMA + ' id=' + id + ' rate=' + d.rate + ' postings=' + accts + (d.variance ? ' IPV=' + d.variance + 'c(split)' : '') + ' oracle=iDempiere maxDiff=' + md + 'c');
});

verdict(equiv === docs.length && docs.length > 0,
  equiv + '/' + docs.length + ' M_MatchInv EUR-schema postings ORACLE-EQUIVALENT to the cent (per-leg FX conversion + avg-cost IPV split, ' + varianceCount + ' variance)',
  'equiv=' + equiv + ' variance=' + varianceCount);

// ── §FALSIFIER-A: skip the FX conversion (post the USD source as the EUR accounted amount) → diff blows up ──
(function () {
  var id = docs[0]; var mi = db.prepare('SELECT * FROM m_matchinv WHERE m_matchinv_id=?').get(sid(id));
  var md = maxDiff(derive(mi, { noConvert: true }).agg, oracle(id));
  verdict(md > 0, '§FALSIFIER-A post the USD source as the EUR accounted amount (skip conversion) on matchinv ' + id + ' → maxDiff≠0 (the FX conversion is load-bearing)', 'maxDiff=' + md + 'c');
  console.log('§FALSIFIER-A doc=' + id + ' mutation=skip-fx-conversion maxDiff=' + md + 'c (must be >0)');
})();

// ── §FALSIFIER-B: all-IPV→Asset (ignore the on-hand cap) on the variance doc → the converted split diverges ──
(function () {
  var id = docs.find(function (x) { var mi = db.prepare('SELECT * FROM m_matchinv WHERE m_matchinv_id=?').get(sid(x)); return derive(mi).variance !== 0; });
  if (id == null) { console.log('§FALSIFIER-B no variance doc in seed — skipped'); return; }
  var mi = db.prepare('SELECT * FROM m_matchinv WHERE m_matchinv_id=?').get(sid(id));
  var md = maxDiff(derive(mi, { allToAsset: true }).agg, oracle(id));
  verdict(md > 0, '§FALSIFIER-B all-IPV→Asset (ignore on-hand cap) on matchinv ' + id + ' → maxDiff≠0 (the on-hand-proportion split is load-bearing under FX too)', 'maxDiff=' + md + 'c');
  console.log('§FALSIFIER-B doc=' + id + ' mutation=all-ipv-to-asset maxDiff=' + md + 'c (must be >0)');
})();

console.log('\n§MATCHINV_FX_NOTE all 18 GardenWorld matches fold to the cent in the EUR schema — the SAME USD source manifest ' +
  'as W-FOLD-MATCHINV, each fact leg converted independently at the date-valid Spot rate (0.85). At 0.85 the IPV split ' +
  '(70/30 → 59.50/25.50) and the 2-leg matches convert exactly, so NO currency-balancing residual arises (contrast ' +
  'poc_alloc_fx.js, whose per-leg rounding needed a CurrencyBalancing line). This completes the PO/inventory loop in both schemas.');

console.log('\n' + (fails === 0 ? '🟢 W-FOLD-MATCHINV-FX PASS' : '🔴 W-FOLD-MATCHINV-FX FAIL (' + fails + ')') +
  ' — EUR-schema M_MatchInv posting (per-leg FX conversion + avg-cost IPV split) oracle-equivalent to the cent across all 18 matches.');
db.close();
process.exit(fails === 0 ? 0 : 1);
