#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_invoice_post_ap.js — W-FOLD-AP-INVOICE (FOLD_MODEL_LOGIC.md "REMAINING TAIL" #1, the purchase manifest).
//
// SPEC: post_resolver's DERIVED GL lines for a real vendor (IsSOTrx=N) C_Invoice must EQUAL iDempiere's actual
//   posted fact_acct lines, per (natural-account, side) in INTEGER CENTS, maxDiff=0c — the AP twin of the proven
//   sales manifest (W-POST-HARDEN). The AP manifest, derived from the real GardenWorld oracle (never invented):
//     CR {Vendor.V_Liability}        = GrandTotal     (per-vendor c_bp_vendor_acct, mirrors customer receivable)
//     DR {Product.InventoryClearing} = LineNetAmt/line (matched-receipt item lines → 51400 Inventory Clearing,
//                                                        via product->category, mirrors {Product.Revenue})
//     DR {Tax.Credit}                = TaxAmt/tax row  (input VAT; zero in this seed → emitted only when nonzero)
//   Same FOLD shape as the sales manifest, a DIFFERENT token set — the purchase half of Doc_Invoice equivalence.
//
// METRIC: aggregate by (account_id, side) so iDempiere's per-line splitting folds to the same per-account total
//   (GL-equivalence granularity). SCOPE: c_acctschema_id=101 (GardenWorld US/USD). Oracle = GardenWorld's real
//   fact_acct (client 11, ad_table_id=318 C_Invoice), captured by scripts/extract_fact_acct.sh — NEVER authored.
//
// NON-INVENT: every account RESOLVED by post_resolver.resolve from real master columns; amounts read from the real
//   c_invoice/line/tax rows; the oracle is a straight DB copy. Deterministic (recorded ids, integer cents, no
//   Date.now/Math.random). READ build/erp/poc_invoice_post_ap.log — exit code is not evidence.
// Implementing FOLD_MODEL_LOGIC.md REMAINING-TAIL #1 (PO-invoice GL value-derivation) — Witness: W-FOLD-AP-INVOICE
// Run: node scripts/poc_invoice_post_ap.js 2>&1 | tee build/erp/poc_invoice_post_ap.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var R = require('./post_resolver');

var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db');
var db = new Database(DB_PATH, { readonly: true });
var SCHEMA = 101;                 // GardenWorld US/USD
var AD_TABLE_C_INVOICE = 318;     // fact_acct.ad_table_id for C_Invoice
var sid = function (x) { return Number(x); };

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function cents(n) { return Math.round(Number(n || 0) * 100); }
function key(acct, side) { return side + ':' + acct; }

console.log('═══ W-FOLD-AP-INVOICE — vendor C_Invoice GL derivation == iDempiere oracle (per-account, cents) ═══');
console.log('    derive = post_resolver + PURCHASE manifest · oracle = real fact_acct (client 11) · schema=' + SCHEMA + '\n');

// ── DERIVE: the AP-invoice manifest, resolved to natural accounts (element.id == fact_acct.account_id) ──
function derive(invId) {
  var hdr = db.prepare('SELECT c_invoice_id,c_bpartner_id,grandtotal,issotrx FROM c_invoice WHERE c_invoice_id=?').get(sid(invId));
  var lines = db.prepare('SELECT m_product_id,c_charge_id,linenetamt FROM c_invoiceline WHERE c_invoice_id=?').all(sid(invId));
  var taxes = db.prepare('SELECT c_tax_id,taxamt FROM c_invoicetax WHERE c_invoice_id=?').all(sid(invId));
  var agg = {}, absent = [];
  function add(side, acct, amt) { if (cents(amt) === 0) return; var k = key(acct, side); agg[k] = (agg[k] || 0) + cents(amt); }
  function nat(res) { if (res.acct == null || !res.element) { absent.push(res.token + ' ' + (res.absent || 'no-element')); return null; } return res.element.id; }

  // CR Vendor Liability = GrandTotal (one natural account, per-vendor)
  var liab = nat(R.resolve(db, '{Vendor.V_Liability}', sid(hdr.c_bpartner_id), SCHEMA));
  if (liab != null) add('CR', liab, hdr.grandtotal);
  // DR Inventory Clearing per product line (matched receipt); a charge line would DR its charge acct (none in seed → flagged)
  lines.forEach(function (l) {
    if (l.m_product_id) { var a = nat(R.resolve(db, '{Product.InventoryClearing}', sid(l.m_product_id), SCHEMA)); if (a != null) add('DR', a, l.linenetamt); }
    else { absent.push('charge-line C_Charge_ID=' + l.c_charge_id + ' (charge manifest not built)'); }
  });
  // DR Input VAT per tax row (zero in this seed → add() no-ops on 0)
  taxes.forEach(function (t) { var a = nat(R.resolve(db, '{Tax.Credit}', sid(t.c_tax_id), SCHEMA)); if (a != null) add('DR', a, t.taxamt); });
  return { hdr: hdr, agg: agg, absent: absent };
}

// ── ORACLE: real fact_acct lines for this document, schema 101, aggregated the same way ────────────────
function oracle(invId) {
  var rows = db.prepare(
    'SELECT account_id, ROUND(SUM(amtacctdr),2) dr, ROUND(SUM(amtacctcr),2) cr FROM fact_acct ' +
    'WHERE ad_table_id=? AND record_id=? AND c_acctschema_id=? GROUP BY account_id'
  ).all(AD_TABLE_C_INVOICE, invId, SCHEMA);
  var agg = {};
  rows.forEach(function (r) { if (cents(r.dr)) agg[key(r.account_id, 'DR')] = cents(r.dr); if (cents(r.cr)) agg[key(r.account_id, 'CR')] = cents(r.cr); });
  return agg;
}

// ── DIFF one document ──────────────────────────────────────────────────────────────────────────────────
function diffDoc(invId, mutate) {
  var d = derive(invId), o = oracle(invId);
  var da = d.agg;
  if (mutate) { var moved = {}; Object.keys(da).forEach(function (k) { moved[mutate(k)] = (moved[mutate(k)] || 0) + da[k]; }); da = moved; }
  var keys = {}; Object.keys(da).forEach(function (k) { keys[k] = 1; }); Object.keys(o).forEach(function (k) { keys[k] = 1; });
  var maxDiff = 0, perAcct = [];
  Object.keys(keys).forEach(function (k) { var diff = Math.abs((da[k] || 0) - (o[k] || 0)); if (diff > maxDiff) maxDiff = diff; perAcct.push(k + ' d=' + ((da[k] || 0) - (o[k] || 0))); });
  var ksDa = Object.keys(da).sort().join(','), ksO = Object.keys(o).sort().join(',');
  var acctSetEqual = ksDa === ksO && ksO.length > 0;
  return { hdr: d.hdr, maxDiff: maxDiff, accts: Object.keys(o).length, derivedKeys: Object.keys(da).length, absent: d.absent, perAcct: perAcct, acctSetEqual: acctSetEqual };
}

// ── the fixture set: every vendor C_Invoice the oracle posted in schema 101 ────────────────────────────
var allDocs = db.prepare('SELECT DISTINCT record_id FROM fact_acct WHERE ad_table_id=? AND c_acctschema_id=? ORDER BY record_id').all(AD_TABLE_C_INVOICE, SCHEMA).map(function (r) { return r.record_id; });
var apDocs = allDocs.filter(function (id) { var h = db.prepare('SELECT issotrx FROM c_invoice WHERE c_invoice_id=?').get(sid(id)); return h && h.issotrx !== 'Y'; });
console.log('§AP_FIXTURES doc=C_Invoice(IsSOTrx=N) schema=' + SCHEMA + ' count=' + apDocs.length + ' ids=[' + apDocs.join(',') + ']\n');

var resolved = 0, equiv = 0, badResolve = 0;
apDocs.forEach(function (id) {
  var r = diffDoc(id);
  if (r.acctSetEqual && r.absent.length === 0) resolved++;
  var cls;
  if (r.maxDiff === 0 && r.absent.length === 0 && r.accts > 0) { equiv++; cls = 'EQUIVALENT'; }
  else { badResolve++; cls = 'DERIVATION-GAP'; }
  console.log('§AP-INVOICE surface=Doc_Invoice record_id=' + id + ' bp=' + r.hdr.c_bpartner_id + ' total=' + r.hdr.grandtotal +
    ' accts=' + r.accts + ' diff=' + r.maxDiff + 'c acctSet=' + (r.acctSetEqual ? 'match' : 'MISMATCH') + ' verdict=' + cls + ' oracle=iDempiere' +
    (r.absent.length ? ' ABSENT=[' + r.absent.join('; ') + ']' : '') + (r.maxDiff ? ' delta=[' + r.perAcct.filter(function (x) { return !/d=0$/.test(x); }).join(', ') + ']' : ''));
});

verdict(apDocs.length > 0 && resolved === apDocs.length,
  resolved + '/' + apDocs.length + ' vendor C_Invoice docs RESOLVE iDempiere\'s EXACT posting accounts (the resolver mechanism)',
  'resolved=' + resolved + ' derivation-gap=' + badResolve);
verdict(equiv === apDocs.length && badResolve === 0,
  equiv + '/' + apDocs.length + ' ORACLE-EQUIVALENT to the cent; 0 derivation-gap',
  'equiv=' + equiv);

// ── §FALSIFIER-A: misderive inventory-clearing DR → the vendor-liability account → per-account diff blows up ──
var probe = apDocs[0];
var probeBp = db.prepare('SELECT c_bpartner_id FROM c_invoice WHERE c_invoice_id=?').get(sid(probe)).c_bpartner_id;
var liabRes = R.resolve(db, '{Vendor.V_Liability}', sid(probeBp), SCHEMA);
var liabId = liabRes.element ? liabRes.element.id : 'X';
var fA = diffDoc(probe, function (k) { return k.indexOf('DR:') === 0 ? 'DR:' + liabId : k; });
verdict(fA.maxDiff > 0, '§FALSIFIER-A misderive clearing→liability on doc ' + probe + ' → maxDiff≠0 (diff is load-bearing)', 'maxDiff=' + fA.maxDiff + 'c');
console.log('§FALSIFIER-A doc=' + probe + ' mutation=clearing->liability maxDiff=' + fA.maxDiff + 'c (must be >0)');

// ── §FALSIFIER-B: flip Dr/Cr sides (post liability DR, clearing CR) → the account-set inverts, diff blows up ──
var fB = diffDoc(probe, function (k) { return (k.indexOf('DR:') === 0 ? 'CR:' : 'DR:') + k.split(':')[1]; });
verdict(fB.maxDiff > 0, '§FALSIFIER-B swap Dr/Cr sides on doc ' + probe + ' → maxDiff≠0', 'maxDiff=' + fB.maxDiff + 'c');
console.log('§FALSIFIER-B doc=' + probe + ' mutation=flip-sides maxDiff=' + fB.maxDiff + 'c (must be >0)');

console.log('\n§AP_RESIDUAL charge-line manifest (DR charge expense) + unmatched/service-product expense DR absent in seed ' +
  '(all ' + apDocs.length + ' AP-invoice lines are matched item receipts) · input-VAT line zero in seed · schema-200000 deferred.');

console.log('\n' + (fails === 0 ? '🟢 W-FOLD-AP-INVOICE PASS' : '🔴 W-FOLD-AP-INVOICE FAIL (' + fails + ')') +
  ' — vendor C_Invoice derivation oracle-diffed to the cent against real GardenWorld fact_acct; the purchase half of Doc_Invoice posting.');
db.close();
process.exit(fails === 0 ? 0 : 1);
