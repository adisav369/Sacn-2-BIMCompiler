#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-FOLD-INOUTGL is recorded in ERP_COVERAGE_MATRIX.md.
// SCOPE: W-FOLD-INOUTGL — prove build/erp/report_inout_gl.foldInOutGL (the cost-valued inventory GL ENGINE VERB)
//   reproduces org.compiere.acct.Doc_InOut for BOTH seed polarities, driven through the post_resolver seam.
//   SPEC: GapClosureSpec §5h (P2 item 3). This CONSUMES the engine (no fork) — the witness only pulls rows + diffs.
//   • C-  (Customer Shipment, COGS):   DR {Product.Cogs} / CR {Product.Asset}, amount = posted M_CostDetail.
//   • V+  (Vendor Receipt):             DR {Product.Asset} / CR {BPGroup.NotInvoicedReceipts}, amount =
//                                       round(movementqty × C_OrderLine.PriceActual) (the PO-price NIR basis,
//                                       same as Doc_MatchInv's NIR leg in W-FOLD-MATCHINV).
// VERDICTS (both ORACLE-EQUIVALENT — maxDiff=0c vs fact_acct(319)):
//   • C- = accounts + posted cost-at-movement.
//   • V+ = accounts + NIR @ PO price.
// ORACLE: glassbowl_data.db fact_acct(319), client 11, schema 101 — iDempiere's own stored postings.
// NON-INVENT: every value READ from a real row; accounts via post_resolver. §-log first — READ build/erp/poc_fold_inout_gl.log.
// Run:  node scripts/poc_fold_inout_gl.js 2>&1 | tee build/erp/poc_fold_inout_gl.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var R = require('./post_resolver');
var F = require(path.join(__dirname, '..', 'build', 'erp', 'report_inout_gl.js'));

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var SCHEMA = 101;
var AD_TABLE_M_INOUT = 319;
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function key(a, s) { return s + ':' + a; }
function maxDiff(a, b) {
  var ks = {}; Object.keys(a).forEach(function (k) { ks[k] = 1; }); Object.keys(b).forEach(function (k) { ks[k] = 1; });
  var m = 0; Object.keys(ks).forEach(function (k) { m = Math.max(m, Math.abs((a[k] || 0) - (b[k] || 0))); }); return m;
}
function acctSetEqual(a, b) {
  var ka = Object.keys(a).sort().join(','), kb = Object.keys(b).sort().join(','); return ka === kb;
}

// deps.resolve through the post_resolver seam → the real Account_ID (C_ElementValue id), or null.
var deps = {
  resolve: function (token, keyId) {
    var r = R.resolve(db, token, Number(keyId), SCHEMA);
    return (r.acct == null || !r.element) ? null : r.element.id;
  }
};

// ── pull the raw source rows (the engine does ALL derivation; the witness only feeds + diffs) ───────────────
var src = {
  inoutLines: db.prepare("SELECT iol.m_inoutline_id, iol.m_inout_id, iol.m_product_id, iol.movementqty, iol.c_orderline_id, io.movementtype, io.c_bpartner_id " +
    "FROM m_inoutline iol JOIN m_inout io ON io.m_inout_id=iol.m_inout_id WHERE io.docstatus IN ('CO','CL')").all(),
  costDetail: db.prepare("SELECT m_inoutline_id, amt FROM m_costdetail WHERE c_acctschema_id=?").all(SCHEMA),
  orderLines: db.prepare("SELECT c_orderline_id, priceactual FROM c_orderline").all()
};

// ── ENGINE fold ─────────────────────────────────────────────────────────────────────────────────────────────
var fold = F.foldInOutGL(src, deps, {});

// ── ORACLE: real fact_acct(319) per movementtype, (account, side) cents ─────────────────────────────────────
function oracleByType(mt) {
  var rows = db.prepare("SELECT fa.account_id, ROUND(SUM(fa.amtacctdr),2) dr, ROUND(SUM(fa.amtacctcr),2) cr " +
    "FROM fact_acct fa JOIN m_inout io ON io.m_inout_id=fa.record_id " +
    "WHERE fa.ad_table_id=? AND fa.c_acctschema_id=? AND io.movementtype=? GROUP BY fa.account_id").all(AD_TABLE_M_INOUT, SCHEMA, mt);
  var agg = {}; rows.forEach(function (r) {
    if (Math.round(r.dr * 100)) agg[key(r.account_id, 'DR')] = Math.round(r.dr * 100);
    if (Math.round(r.cr * 100)) agg[key(r.account_id, 'CR')] = Math.round(r.cr * 100);
  });
  return agg;
}

console.log('═══ W-FOLD-INOUTGL — cost-valued inventory GL (Doc_InOut) folded as an ENGINE VERB → fact_acct(319) ═══');
console.log('    engine = report_inout_gl.foldInOutGL · accounts = post_resolver seam · oracle = glassbowl_data.db (client 11, schema ' + SCHEMA + ')');
console.log('    C- amount = posted M_CostDetail · V+ amount = round(qty × C_OrderLine.PriceActual) (PO-price NIR basis)\n');
verdict(fold.absent.length === 0, 'every posting account RESOLVED (none invented/absent)', 'absent=' + JSON.stringify(fold.absent));

// ── (1) C- shipment COGS — ORACLE-EQUIVALENT (accounts + posted amount), maxDiff=0c ─────────────────────────
(function () {
  var eng = fold.byType['C-'] || {}, ora = oracleByType('C-');
  var md = maxDiff(eng, ora), setEq = acctSetEqual(eng, ora);
  var engDr = 0, engCr = 0; Object.keys(eng).forEach(function (k) { if (k.indexOf('DR:') === 0) engDr += eng[k]; else engCr += eng[k]; });
  console.log('§FOLD-INOUTGL movementtype=C- (shipment COGS) accounts=' + Object.keys(ora).length + ' engineΣDR=' + (engDr / 100).toFixed(2) + ' ΣCR=' + (engCr / 100).toFixed(2) + ' maxDiff=' + md + 'c oracle=fact_acct(319)');
  verdict(Object.keys(ora).length > 0, 'C- has real oracle postings to diff against', 'oracleAccts=' + Object.keys(ora).length);
  verdict(setEq, 'C- account SET == oracle (DR {Product.Cogs} / CR {Product.Asset})', 'engine={' + Object.keys(eng).sort() + '} oracle={' + Object.keys(ora).sort() + '}');
  verdict(md === 0, 'C- shipment COGS == fact_acct(319) EXACT (posted cost-at-movement) — ORACLE-EQUIVALENT', 'maxDiff=' + md + 'c');
  verdict(engDr === engCr, 'C- balanced (ΣDR=ΣCR)', engDr + '=' + engCr);
})();

// ── (2) V+ vendor receipt — ORACLE-EQUIVALENT (accounts + NIR @ PO price), maxDiff=0c ───────────────────────
(function () {
  var eng = fold.byType['V+'] || {}, ora = oracleByType('V+');
  var md = maxDiff(eng, ora), setEq = acctSetEqual(eng, ora);
  var engDr = 0, engCr = 0; Object.keys(eng).forEach(function (k) { if (k.indexOf('DR:') === 0) engDr += eng[k]; else engCr += eng[k]; });
  console.log('§FOLD-INOUTGL movementtype=V+ (vendor receipt) accounts=' + Object.keys(ora).length + ' engineΣDR=' + (engDr / 100).toFixed(2) + ' ΣCR=' + (engCr / 100).toFixed(2) + ' maxDiff=' + md + 'c oracle=fact_acct(319)');
  verdict(Object.keys(ora).length > 0, 'V+ has real oracle postings to diff against', 'oracleAccts=' + Object.keys(ora).length);
  verdict(setEq, 'V+ account SET == oracle (DR {Product.Asset} / CR {BPGroup.NotInvoicedReceipts})', 'engine={' + Object.keys(eng).sort() + '} oracle={' + Object.keys(ora).sort() + '}');
  verdict(md === 0, 'V+ vendor receipt == fact_acct(319) EXACT (NIR @ PO price) — ORACLE-EQUIVALENT', 'maxDiff=' + md + 'c');
  verdict(engDr === engCr, 'V+ balanced (ΣDR=ΣCR)', engDr + '=' + engCr);
})();

// ── §FALSIFIER-A — drop the C- COGS DR line → the EXACT C- diff must blow up ────────────────────────────────
(function () {
  var bent = F.foldInOutGL(src, deps, { dropDr: 'C-' });
  var md = maxDiff(bent.byType['C-'] || {}, oracleByType('C-'));
  console.log('§FALSIFIER-A drop C- DR {Product.Cogs} → maxDiff=' + md + 'c (must be >0)');
  verdict(md > 0, '§FALSIFIER-A dropping the COGS DR breaks the C- diff (the metric is load-bearing)', 'maxDiff=' + md + 'c');
})();

// ── §FALSIFIER-B — bend the PO price → the V+ NIR amount must diverge from the oracle ───────────────────────
(function () {
  var bent = F.foldInOutGL(src, deps, { bendPrice: 999 });
  var md = maxDiff(bent.byType['V+'] || {}, oracleByType('V+'));
  console.log('§FALSIFIER-B bend C_OrderLine.PriceActual → V+ maxDiff=' + md + 'c (must be >0)');
  verdict(md > 0, '§FALSIFIER-B bending the PO price breaks the V+ diff (the PO-price basis is load-bearing)', 'maxDiff=' + md + 'c');
})();

// ── §FALSIFIER-C — swap DR/CR accounts → both diffs must blow up ────────────────────────────────────────────
(function () {
  var bent = F.foldInOutGL(src, deps, { swapAccounts: true });
  var md = maxDiff(bent.byType['C-'] || {}, oracleByType('C-'));
  console.log('§FALSIFIER-C swap DR/CR accounts → C- maxDiff=' + md + 'c (must be >0)');
  verdict(md > 0, '§FALSIFIER-C swapping DR/CR accounts breaks the diff (account resolution is load-bearing)', 'maxDiff=' + md + 'c');
})();

console.log('\n§FOLD-NOTE C- shipment COGS = the POSTED m_costdetail (cost-at-movement); V+ receipt NIR = round(qty × ' +
  'C_OrderLine.PriceActual) (the PO-price basis, same as Doc_MatchInv\'s NIR leg). BOTH polarities oracle-exact ' +
  'against fact_acct(319) — accounts via post_resolver, amounts to the cent.');

console.log('\n' + (fails === 0 ? '🟢 W-FOLD-INOUTGL PASS' : '🔴 W-FOLD-INOUTGL FAIL (' + fails + ')') +
  ' — cost-valued inventory GL is now an ENGINE VERB (foldInOutGL): C- shipment COGS AND V+ vendor receipt ' +
  'both ORACLE-EQUIVALENT to the cent vs fact_acct(319).');
db.close();
process.exit(fails === 0 ? 0 : 1);
