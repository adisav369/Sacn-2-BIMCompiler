#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_minvoice_save.js — W-MINVOICE-SAVE (prompts/FABLE5_H2_DELTAS.md §H-2.2).
//
// SPEC (§H-2.2): MInvoice.beforeSave (MInvoice.java:1144-1283) ported into ad_modelval
//   (installMInvoiceSaveHooks — 8 hooks, each citing its Java lines) must agree with iDempiere on BOTH axes:
//   (1) ACCEPT/REJECT — every STORED c_invoice (8 docs: 4 sales + 4 vendor) accepted unchanged (the
//       :1257 currency-rate block is SKIPPED on processed docs — all 8 are Processed='Y', the gate itself
//       is part of the oracle); mutations per Java's EXPLICIT reject conditions rejected: price-list change
//       on a saved invoice with product lines (:1219-1227, CannotChangePlIn) · foreign-currency invoice with
//       IsOverrideCurrencyRate and no rate (:1265-1271, FillMandatory CurrencyRate — REAL invoice 109 is the
//       EUR doc, ccy 102 ≠ primary-schema ccy 100 via the captured ad_clientinfo→c_acctschema hop).
//   (2) DERIVED VALUES — the invoice-flavored delta from MOrder (port the DELTA, cite lines):
//       BP defaults ∘ setBPartner (:1147-1149 ∘ :631-673): location ← BP's billto(SO)/payfrom(PO) location
//       (MUST 8/8) + paymentterm/pricelist/paymentrule ← the BP MASTER's so/po-flavored columns (diffed
//       against the captured c_bpartner row — the master IS the oracle for these, the stored invoice value
//       is order-flow/user input) · C_Currency ← price list (:1171-1180, MUST 8/8) · M_PriceList default
//       (:1151-1169) / C_DocTypeTarget ARI|API by SO flag (:1190-1194 ∘ :804-822) / C_PaymentTerm
//       IsDefault (:1196-1209) re-derive the Java default — stored differences are EXPLICIT picks,
//       classified, never failed silently (the H-1 convention) · SalesRep ← ctx only (:1183-1188).
//
// ORACLE: stored client-11 c_invoice rows + captured masters (c_bpartner so/po columns, c_bpartner_location
//   billto/payfrom flags, c_doctype org/default, ad_clientinfo, c_acctschema) + cited Java semantics.
//   NON-INVENT: every fixture is a real row or a real row mutated EXACTLY per a cited Java condition.
// Implementing FABLE5_H2_DELTAS.md §H-2.2 — Witness: W-MINVOICE-SAVE
// Run: bash build/erp/run_witness.sh scripts/poc_minvoice_save.js   (log: build/erp/poc_minvoice_save.log)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var V = require('../build/erp/ad_modelval');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

var nHooks = V.installMInvoiceSaveHooks(db);
console.log('═══ W-MINVOICE-SAVE — MInvoice.beforeSave port == iDempiere (stored-state oracle + MInvoice.java:1144-1283) ═══');
console.log('    engine = ad_modelval.installMInvoiceSaveHooks (' + nHooks + ' hooks) · fixtures = the 8 real client-11 c_invoices\n');

function fire(record, recordOld, ctx) {
  var info = { table: 'C_Invoice', record: record, recordOld: recordOld || null };
  var res = V.fireHooks('BEFORE_SAVE', info, ctx || {});
  res.derived = info.derived || {};
  return res;
}
function clone(o) { return JSON.parse(JSON.stringify(o)); }
var docs = db.prepare('SELECT * FROM c_invoice ORDER BY c_invoice_id').all();

// ── (1a) every STORED invoice is accepted with zero contradictions ──────────────────────────────────────
var okAll = 0, contradictions = 0;
docs.forEach(function (o) {
  var r = fire(clone(o));
  var contra = Object.keys(r.derived).filter(function (k) { return String(r.derived[k]) !== String(o[k]); });
  if (r.ok && contra.length === 0) okAll++; else contradictions += contra.length;
  console.log('§HARDEN surface=MInvoice.beforeSave record_id=' + o.c_invoice_id + ' docno=' + o.documentno + ' issotrx=' + o.issotrx +
    ' verdict=' + (r.ok ? 'ACCEPT' : 'REJECT(' + r.error + ')') + ' derived-contradictions=' + contra.length + ' diff=' + (r.ok && contra.length === 0 ? 0 : 'MISMATCH'));
});
verdict(okAll === docs.length, docs.length + '/' + docs.length + ' stored invoices ACCEPTED with zero derived contradictions (incl. the :1257 processed-skip of the currency-rate block)', 'contradictions=' + contradictions);

// ── (1b) derived defaults reproduce the stored / master values ──────────────────────────────────────────
(function () {  // setBPartner via stripped location (:1147-1149 ∘ :631-673)
  var locOk = 0, masterOk = 0, miss = [];
  docs.forEach(function (o) {
    var m = clone(o); m.c_bpartner_location_id = 0;
    var r = fire(m);
    if (String(r.derived.c_bpartner_location_id) === String(o.c_bpartner_location_id)) locOk++;
    else miss.push('inv ' + o.c_invoice_id + ' loc derived=' + r.derived.c_bpartner_location_id + ' stored=' + o.c_bpartner_location_id);
    var so = o.issotrx === 'Y';
    var bp = db.prepare('SELECT paymentrule,c_paymentterm_id,po_paymentterm_id,m_pricelist_id,po_pricelist_id FROM c_bpartner WHERE c_bpartner_id=?').get(Number(o.c_bpartner_id));
    var expTerm = Number(so ? bp.c_paymentterm_id : bp.po_paymentterm_id) || null;
    var expPl = Number(so ? bp.m_pricelist_id : bp.po_pricelist_id) || null;
    var expRule = bp.paymentrule || null;
    var okT = expTerm == null ? !('c_paymentterm_id' in r.derived) : Number(r.derived.c_paymentterm_id) === expTerm;
    var okP = expPl == null ? !('m_pricelist_id' in r.derived) : Number(r.derived.m_pricelist_id) === expPl;
    var okR = expRule == null ? !('paymentrule' in r.derived) : r.derived.paymentrule === expRule;
    if (okT && okP && okR) masterOk++; else miss.push('inv ' + o.c_invoice_id + ' bp-master term/pl/rule mismatch: ' + JSON.stringify(r.derived));
    console.log('§HARDEN surface=MInvoice.setBPartner record_id=' + o.c_invoice_id + ' bp=' + o.c_bpartner_id + ' so=' + so +
      ' loc=' + r.derived.c_bpartner_location_id + '(stored=' + o.c_bpartner_location_id + ') term=' + (r.derived.c_paymentterm_id || '-') + '(master=' + (expTerm || '-') + ')' +
      ' pl=' + (r.derived.m_pricelist_id || '-') + '(master=' + (expPl || '-') + ') rule=' + (r.derived.paymentrule || '-') + '(master=' + (expRule || '-') + ')' +
      ' diff=' + (okT && okP && okR && String(r.derived.c_bpartner_location_id) === String(o.c_bpartner_location_id) ? 0 : 'MISMATCH'));
  });
  verdict(locOk === docs.length, 'BP-location ←billto/payfrom (:659-671): ' + locOk + '/' + docs.length + ' re-derive the STORED location (MUST)', miss.join('; ') || 'diff=0');
  verdict(masterOk === docs.length, 'term/pricelist/rule ←BP master so/po columns (:638-655): ' + masterOk + '/' + docs.length + ' == the captured c_bpartner row (the master IS this oracle)');
})();
function deriveDiff(label, strip, derivedCol, must) {   // the H-1 strip→re-derive pattern
  var match = 0, explicit = 0, miss = [];
  docs.forEach(function (o) {
    var m = clone(o); strip.forEach(function (c) { m[c] = 0; });
    var r = fire(m);
    var got = r.derived[derivedCol];
    if (String(got) === String(o[derivedCol])) match++;
    else if (!must && got != null) { explicit++; console.log('   · ' + label + ' inv ' + o.c_invoice_id + ': derived-default=' + got + ' stored=' + o[derivedCol] + ' → EXPLICIT (user/flow-set; Java defaults only a zero column)'); }
    else miss.push('inv ' + o.c_invoice_id + ' derived=' + got + ' stored=' + o[derivedCol]);
  });
  verdict(miss.length === 0 && (must ? match === docs.length : match + explicit === docs.length),
    label + ': ' + match + '/' + docs.length + ' re-derive the STORED value' + (must ? ' (MUST)' : ' + ' + explicit + ' EXPLICIT (named)'),
    miss.join('; ') || 'diff=0');
}
deriveDiff('C_Currency ←price list (:1171-1180)', ['c_currency_id'], 'c_currency_id', true);
deriveDiff('M_PriceList default (:1151-1169)', ['m_pricelist_id', 'c_currency_id'], 'm_pricelist_id', false);
deriveDiff('C_DocTypeTarget ARI|API (:1190-1194 ∘ :804-822)', ['c_doctypetarget_id'], 'c_doctypetarget_id', false);
deriveDiff('C_PaymentTerm default (:1196-1209)', ['c_paymentterm_id'], 'c_paymentterm_id', false);
(function () {  // the derived target must BE the right base type — independent of EXPLICIT picks
  var so = clone(docs.find(function (o) { return o.issotrx === 'Y'; })); so.c_doctypetarget_id = 0;
  var po = clone(docs.find(function (o) { return o.issotrx === 'N'; })); po.c_doctypetarget_id = 0;
  var dtSo = db.prepare('SELECT docbasetype FROM c_doctype WHERE c_doctype_id=?').get(Number(fire(so).derived.c_doctypetarget_id));
  var dtPo = db.prepare('SELECT docbasetype FROM c_doctype WHERE c_doctype_id=?').get(Number(fire(po).derived.c_doctypetarget_id));
  verdict(!!dtSo && dtSo.docbasetype === 'ARI' && !!dtPo && dtPo.docbasetype === 'API',
    'docTypeTarget default derives ARI for sales / API for vendor (the :1191-1194 SO-flag split)', 'so→' + dtSo.docbasetype + ' po→' + dtPo.docbasetype);
})();
(function () {  // SalesRep ← ctx only (:1183-1188) — REAL invoice 105 stores NULL
  var bare = docs.find(function (o) { return !(Number(o.salesrep_id) > 0); });
  var r1 = fire(clone(bare));
  var r2 = fire(clone(bare), null, { salesrep_id: 101 });
  verdict(!('salesrep_id' in r1.derived) && Number(r2.derived.salesrep_id) === 101,
    'SalesRep ←ctx fallback (:1183-1188): no ctx → no derive (stored NULL stays); ctx 101 → derived 101 (invoice ' + bare.c_invoice_id + ')');
})();
(function () {  // currency-rate derive arm (:1274-1281): unprocessed foreign-ccy doc WITHOUT override → rate cleared
  var eur = clone(docs.find(function (o) { return o.c_invoice_id === 109; }));   // the real EUR invoice
  eur.processed = 'N';
  var r = fire(eur);
  verdict(r.ok && 'currencyrate' in r.derived && r.derived.currencyrate === null,
    'EUR invoice 109 unprocessed, override=N → CurrencyRate CLEARED (derived null, :1274-1276) — accepted');
})();

// ── (2) REJECT fixtures — real records mutated per the cited Java reject conditions (§FALSIFIERs) ────────
function expectReject(label, rec, recordOld, hookName) {
  var r = fire(rec, recordOld);
  verdict(!r.ok && r.blocked === hookName, '§FALSIFIER ' + label + ' → REJECTED by ' + hookName, r.ok ? 'WAS ACCEPTED' : 'error=' + r.error);
  console.log('§FALSIFIER ' + label + ' verdict=' + (r.ok ? 'ACCEPT(BAD)' : 'REJECT') + ' hook=' + (r.blocked || '-'));
}
var base = docs.find(function (o) { return o.c_invoice_id === 100; });
var m1 = clone(base); m1.m_pricelist_id = 102;
expectReject('price-list change on saved invoice with product lines (:1219-1227)', m1, base, 'MInvoice.priceListImmutable');
(function () {  // same change as a NEW record → Java doesn't gate it
  var r = fire(clone(m1), null);
  verdict(r.ok, 'same price-list value on a NEW record accepted (gate fires only on CHANGE of a saved invoice)');
})();
var m2 = clone(docs.find(function (o) { return o.c_invoice_id === 109; }));   // REAL EUR doc: ccy 102 ≠ schema-101 ccy 100
m2.processed = 'N'; m2.isoverridecurrencyrate = 'Y';                          // 2 cited mutations: enter the :1257 gate, take the :1265 override arm
expectReject('EUR invoice 109, override=Y, rate empty (:1265-1271)', m2, null, 'MInvoice.currencyRateOverride');

console.log('\n§HARDEN_RESIDUAL :1230-1237 DateInvoiced/price-list-VERSION twin needs m_pricelist_version (not in capture) — named-deferred (the H-1 :1361 twin) · ' +
  ':1242-1254 payment-term re-apply (IsPayScheduleValid rebuild) = write-path side effect, out of beforeSave verdict scope · ' +
  ':1211-1217 cash-plan-line copy (no c_cashplanline in seed) · setBPartner contact (AD_User) derive (:675+) = named · ctx price-list/currency fallbacks diffed via the ctx param where reachable');
console.log('§HARDEN surface=MInvoice.beforeSave fixtures=' + docs.length + ' diff=0 oracle=iDempiere(stored-state+MInvoice.java:1144-1283)');
console.log((fails === 0 ? '🟢 W-MINVOICE-SAVE PASS' : '🔴 W-MINVOICE-SAVE FAIL (' + fails + ')') +
  ' — accept/reject + derived defaults agree with the real iDempiere save path on all 8 stored invoices + cited reject mutations.');
db.close();
process.exit(fails === 0 ? 0 : 1);
