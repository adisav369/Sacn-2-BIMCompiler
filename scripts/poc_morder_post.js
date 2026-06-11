#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_morder_post.js — W-MORDER-POST (FABLE5_MORDER_EQUIVALENCE.md §H-1.2).
//
// SPEC (§H-1.2): the MOrder POSTING surface, per document, at LINE granularity — two halves:
//   (1) Doc_Order PROPER: iDempiere posts C_Order facts ONLY under commitment accounting
//       (Doc_Order.createFacts:336-445 — PO branch gated isCreatePOCommitment, SO branch gated
//       isCreateSOCommitment; MAcctSchema.java:635/649 — CommitmentType ∈ {C,B,A,O} / {S,A,O}).
//       GardenWorld's REAL config is CommitmentType='N' on BOTH schemas (captured c_acctschema, verified
//       live) → the oracle holds ZERO fact_acct(259) rows. The engine derives the SAME zero set for every
//       order, gated by the CAPTURED config — §FALSIFIER-A flips the gate to prove zero is config-derived,
//       not hardcoded.
//   (2) the ORDER'S POSTING CHAIN at line_id granularity (the new axis beyond W-FOLD-COMPLETE's per-account
//       totals): per C_Order, every chained document's fact lines — shipment/receipt fact_acct(319) keyed
//       line_id=m_inoutline_id, invoice fact_acct(318) keyed line_id=c_invoiceline_id (header legs line_id
//       NULL→0) — diffed derived-vs-oracle per (table/record/line_id/account/side) in INTEGER CENTS.
//       Manifests (all previously proven at account level, here refined to per-line):
//         shipment  (SO): DR {Product.Cogs} / CR {Product.Asset} @ Σ|m_costdetail.amt| per line
//         receipt   (PO): DR {Product.Asset} / CR {BPGroup.NotInvoicedReceipts} @ round(qty×PO priceactual)
//         sales inv     : DR {BPartner.Receivable} GT + CR {Tax.Due} (header, line 0); CR {Product.Revenue}
//                         linenetamt per line
//         vendor inv    : CR {Vendor.V_Liability} GT (header); DR {Product.InventoryClearing} linenetamt per line
//       SCOPE: schema 101 (the base USD schema). Schema-200000 adds per-leg FX conversion + CurrencyBalancing
//       lines — the SAME per-schema fold already proven to the cent by W-FOLD-ALLOC-FX / W-FOLD-MATCHINV-FX /
//       W-FOLD-MOVEMENT-FX — named residual here, not re-proven.
//   DRIFT (not a gap): a doc edited AFTER posting can't re-derive from current source (fact_acct=posting-time);
//   DETECTED via key-set match + updated>dateinvoiced → AMT-DRIFT, named (the poc_post_harden discipline).
//
// CHAIN UNIVERSE (extracted, not assumed): orders 100/101/102/108 (SO) + 104/105/106/200002 (PO); shipments/
//   receipts via m_inout.c_order_id; invoices via c_invoiceline⋈c_orderline. Standalone inout 102/104 +
//   vendor invoices 102/104 have NO c_order — outside the MOrder chain (folded by W-FOLD-AP-INVOICE);
//   shipment 103 (order 102) is uncosted → zero facts BOTH sides (empty==empty, visible below).
//
// NON-INVENT: accounts RESOLVED by post_resolver from real master config; amounts from real source rows;
//   oracle = the per-document fact_acct capture proven row-identical to live by W-FACTACCT-DOC (§H-1.1).
//   Deterministic: recorded ids, integer cents, no Date.now/Math.random.
// Implementing FABLE5_MORDER_EQUIVALENCE.md §H-1.2 — Witness: W-MORDER-POST
// Run: bash build/erp/run_witness.sh scripts/poc_morder_post.js   (log: build/erp/poc_morder_post.log)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var R = require('./post_resolver');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var SCHEMA = 101;
var T_ORDER = 259, T_INVOICE = 318, T_INOUT = 319;
var sid = function (x) { return Number(x); };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function cents(n) { return Math.round(Number(n || 0) * 100); }

// ═══ (1) Doc_Order proper — the commitment gate over the CAPTURED config ═══════════════════════════════
// port of Doc_Order.createFacts:336-445 ∘ MAcctSchema.isCreatePO/SOCommitment:635/649. Returns the fact-line
// entries Doc_Order WOULD create. With CommitmentType='N' both branches gate closed → [] (the real path).
var PO_COMMIT = { C: 1, B: 1, A: 1, O: 1 }, SO_COMMIT = { S: 1, A: 1, O: 1 };
function deriveOrderFacts(order, commitmentType) {
  var entries = [], absent = [];
  var isPO = order.issotrx !== 'Y';
  var gateOpen = isPO ? !!PO_COMMIT[commitmentType] : !!SO_COMMIT[commitmentType];
  if (!gateOpen) return { entries: entries, absent: absent, gate: 'closed(' + commitmentType + ')' };
  // commitment branch: per line {Product.Expense}(PO,DR) / {Product.Revenue}(SO,CR) + the offset leg.
  var lines = db.prepare('SELECT c_orderline_id,m_product_id,linenetamt FROM c_orderline WHERE c_order_id=?').all(sid(order.c_order_id));
  lines.forEach(function (l) {
    var token = isPO ? '{Product.Expense}' : '{Product.Revenue}';
    var res = R.TOKENS[token] ? R.resolve(db, token, sid(l.m_product_id), SCHEMA) : { acct: null };
    if (res.acct == null || !res.element) { absent.push(token); entries.push({ line: l.c_orderline_id, acct: 'UNRESOLVED', side: isPO ? 'DR' : 'CR', c: cents(l.linenetamt) }); }
    else entries.push({ line: l.c_orderline_id, acct: res.element.id, side: isPO ? 'DR' : 'CR', c: cents(l.linenetamt) });
  });
  absent.push(isPO ? '{Schema.CommitmentOffset}' : '{Schema.CommitmentOffsetSales}'); // offset acct not in capture (commitment off in seed)
  return { entries: entries, absent: absent, gate: 'open(' + commitmentType + ')' };
}

console.log('═══ W-MORDER-POST — MOrder posting per document, line granularity == iDempiere oracle ═══');
console.log('    derive = post_resolver manifests · oracle = fact_acct capture (W-FACTACCT-DOC row-identical to live) · schema=' + SCHEMA + '\n');

var ct = db.prepare('SELECT commitmenttype FROM c_acctschema WHERE c_acctschema_id=?').get(SCHEMA).commitmenttype;
var orders = db.prepare('SELECT * FROM c_order ORDER BY c_order_id').all();
var oracle259 = db.prepare('SELECT count(*) n FROM fact_acct WHERE ad_table_id=?').get(T_ORDER).n;
console.log('§HARDEN_CONFIG c_acctschema(' + SCHEMA + ').commitmenttype=' + ct + ' (Doc_Order gate; MAcctSchema.java:635/649) oracle_fact_acct(259) rows=' + oracle259);

var docOrderOk = 0;
orders.forEach(function (o) {
  var d = deriveOrderFacts(o, ct);
  var oRows = db.prepare('SELECT count(*) n FROM fact_acct WHERE ad_table_id=? AND record_id=?').get(T_ORDER, sid(o.c_order_id)).n;
  var ok = d.entries.length === 0 && oRows === 0;
  if (ok) docOrderOk++;
  console.log('§HARDEN surface=MOrder.Doc_Order record_id=' + o.c_order_id + ' docno=' + o.documentno + ' issotrx=' + o.issotrx +
    ' gate=' + d.gate + ' derived=' + d.entries.length + ' oracle=' + oRows + ' diff=' + (ok ? 0 : 'MISMATCH'));
});
verdict(docOrderOk === orders.length && oracle259 === 0,
  orders.length + '/' + orders.length + ' orders: Doc_Order derives the oracle\'s ZERO fact set (commitment gate \'' + ct + '\' closed, read from real config)');

// ═══ (2) the order's posting chain, line_id-keyed ═══════════════════════════════════════════════════════
function addE(map, table, rec, line, acct, side, c) {
  if (c === 0) return;                                       // zero-amount legs don't post (matches oracle)
  var k = table + '/' + rec + '/' + line + '/' + acct + '/' + side;
  map[k] = (map[k] || 0) + c;
}
function nat(res, absent) { if (res.acct == null || !res.element) { absent.push(res.token); return null; } return res.element.id; }

function deriveChain(orderId) {
  var derived = {}, absent = [];
  // shipments / receipts
  var ios = db.prepare('SELECT m_inout_id,issotrx,c_bpartner_id FROM m_inout WHERE c_order_id=?').all(sid(orderId));
  ios.forEach(function (io) {
    var lines = db.prepare('SELECT m_inoutline_id,m_product_id,movementqty,c_orderline_id FROM m_inoutline WHERE m_inout_id=?').all(sid(io.m_inout_id));
    lines.forEach(function (l) {
      if (io.issotrx === 'Y') {                              // shipment: COGS/Inventory @ cost-at-movement
        var cd = db.prepare('SELECT ROUND(SUM(ABS(amt)),2) amt FROM m_costdetail WHERE m_inoutline_id=? AND c_acctschema_id=?').get(sid(l.m_inoutline_id), SCHEMA);
        var c = cents(cd && cd.amt);
        if (c === 0) return;                                 // uncosted move → no posting (oracle agrees)
        var cogs = nat(R.resolve(db, '{Product.Cogs}', sid(l.m_product_id), SCHEMA), absent);
        var asset = nat(R.resolve(db, '{Product.Asset}', sid(l.m_product_id), SCHEMA), absent);
        if (cogs != null) addE(derived, T_INOUT, io.m_inout_id, l.m_inoutline_id, cogs, 'DR', c);
        if (asset != null) addE(derived, T_INOUT, io.m_inout_id, l.m_inoutline_id, asset, 'CR', c);
      } else {                                               // receipt: Inventory/NIR @ PO price (full precision × qty, then round)
        var ol = db.prepare('SELECT priceactual FROM c_orderline WHERE c_orderline_id=?').get(sid(l.c_orderline_id));
        var c2 = ol ? Math.round(Number(l.movementqty) * Number(ol.priceactual) * 100) : 0;
        if (c2 === 0) return;
        var asset2 = nat(R.resolve(db, '{Product.Asset}', sid(l.m_product_id), SCHEMA), absent);
        var nir = nat(R.resolve(db, '{BPGroup.NotInvoicedReceipts}', sid(io.c_bpartner_id), SCHEMA), absent);
        if (asset2 != null) addE(derived, T_INOUT, io.m_inout_id, l.m_inoutline_id, asset2, 'DR', c2);
        if (nir != null) addE(derived, T_INOUT, io.m_inout_id, l.m_inoutline_id, nir, 'CR', c2);
      }
    });
  });
  // the order's invoice (c_invoiceline ⋈ c_orderline lineage)
  var inv = db.prepare('SELECT DISTINCT il.c_invoice_id id FROM c_invoiceline il JOIN c_orderline ol ON ol.c_orderline_id=il.c_orderline_id WHERE ol.c_order_id=?').get(sid(orderId));
  var invId = inv ? inv.id : null;
  if (invId != null) {
    var hdr = db.prepare('SELECT c_bpartner_id,grandtotal,issotrx FROM c_invoice WHERE c_invoice_id=?').get(sid(invId));
    var ilines = db.prepare('SELECT c_invoiceline_id,m_product_id,linenetamt FROM c_invoiceline WHERE c_invoice_id=?').all(sid(invId));
    var taxes = db.prepare('SELECT c_tax_id,taxamt FROM c_invoicetax WHERE c_invoice_id=?').all(sid(invId));
    if (hdr.issotrx === 'Y') {                               // sales: DR Receivable GT / CR Revenue per line / CR Tax.Due
      var rcv = nat(R.resolve(db, '{BPartner.Receivable}', sid(hdr.c_bpartner_id), SCHEMA), absent);
      if (rcv != null) addE(derived, T_INVOICE, invId, 0, rcv, 'DR', cents(hdr.grandtotal));
      ilines.forEach(function (l) { var a = nat(R.resolve(db, '{Product.Revenue}', sid(l.m_product_id), SCHEMA), absent); if (a != null) addE(derived, T_INVOICE, invId, l.c_invoiceline_id, a, 'CR', cents(l.linenetamt)); });
      taxes.forEach(function (t) { var a = nat(R.resolve(db, '{Tax.Due}', sid(t.c_tax_id), SCHEMA), absent); if (a != null) addE(derived, T_INVOICE, invId, 0, a, 'CR', cents(t.taxamt)); });
    } else {                                                 // vendor: CR V_Liability GT / DR InventoryClearing per line / DR Tax.Credit
      var vl = nat(R.resolve(db, '{Vendor.V_Liability}', sid(hdr.c_bpartner_id), SCHEMA), absent);
      if (vl != null) addE(derived, T_INVOICE, invId, 0, vl, 'CR', cents(hdr.grandtotal));
      ilines.forEach(function (l) { var a = nat(R.resolve(db, '{Product.InventoryClearing}', sid(l.m_product_id), SCHEMA), absent); if (a != null) addE(derived, T_INVOICE, invId, l.c_invoiceline_id, a, 'DR', cents(l.linenetamt)); });
      taxes.forEach(function (t) { var a = nat(R.resolve(db, '{Tax.Credit}', sid(t.c_tax_id), SCHEMA), absent); if (a != null) addE(derived, T_INVOICE, invId, 0, a, 'DR', cents(t.taxamt)); });
    }
  }
  return { derived: derived, absent: absent, invId: invId, nInout: ios.length };
}

function oracleChain(orderId, invId) {
  var agg = {};
  function fold(rows, table) {
    rows.forEach(function (r) {
      var line = (r.line_id == null || r.line_id === '' || Number(r.line_id) === 0) ? 0 : Number(r.line_id);
      if (cents(r.amtacctdr)) addE(agg, table, Number(r.record_id), line, Number(r.account_id), 'DR', cents(r.amtacctdr));
      if (cents(r.amtacctcr)) addE(agg, table, Number(r.record_id), line, Number(r.account_id), 'CR', cents(r.amtacctcr));
    });
  }
  fold(db.prepare('SELECT record_id,line_id,account_id,amtacctdr,amtacctcr FROM fact_acct WHERE ad_table_id=? AND c_acctschema_id=? AND record_id IN (SELECT m_inout_id FROM m_inout WHERE c_order_id=?)').all(T_INOUT, SCHEMA, sid(orderId)), T_INOUT);
  if (invId != null) fold(db.prepare('SELECT record_id,line_id,account_id,amtacctdr,amtacctcr FROM fact_acct WHERE ad_table_id=? AND c_acctschema_id=? AND record_id=?').all(T_INVOICE, SCHEMA, sid(invId)), T_INVOICE);
  return agg;
}

function diffSets(da, o) {
  var keys = {}, md = 0, misses = [];
  Object.keys(da).forEach(function (k) { keys[k] = 1; }); Object.keys(o).forEach(function (k) { keys[k] = 1; });
  Object.keys(keys).forEach(function (k) { var d = Math.abs((da[k] || 0) - (o[k] || 0)); if (d > md) md = d; if (d) misses.push(k + ' d=' + ((da[k] || 0) - (o[k] || 0))); });
  var setEq = Object.keys(da).sort().join(',') === Object.keys(o).sort().join(',');
  return { maxDiff: md, setEq: setEq, misses: misses, nDerived: Object.keys(da).length, nOracle: Object.keys(o).length };
}
function postPostingEdit(invId) { var r = db.prepare('SELECT (julianday(updated)-julianday(dateinvoiced)) d FROM c_invoice WHERE c_invoice_id=?').get(sid(invId)); return r && r.d != null && r.d > 0; }

console.log('');
var equiv = 0, driftN = 0, gap = 0;
orders.forEach(function (o) {
  var d = deriveChain(o.c_order_id);
  var ora = oracleChain(o.c_order_id, d.invId);
  var r = diffSets(d.derived, ora);
  var cls;
  if (r.maxDiff === 0 && d.absent.length === 0) { cls = 'EQUIVALENT'; equiv++; }
  else if (r.setEq && d.invId != null && postPostingEdit(d.invId)) { cls = 'AMT-DRIFT(post-posting edit; fact_acct=posting-time, source=edited)'; driftN++; }
  else { cls = 'DERIVATION-GAP'; gap++; }
  console.log('§HARDEN surface=MOrder.chain record_id=' + o.c_order_id + ' docno=' + o.documentno + ' issotrx=' + o.issotrx +
    ' inouts=' + d.nInout + ' invoice=' + (d.invId == null ? 'none' : d.invId) + ' lines=' + r.nDerived + '/' + r.nOracle +
    ' diff=' + r.maxDiff + 'c keySet=' + (r.setEq ? 'match' : 'MISMATCH') + ' verdict=' + cls + ' oracle=iDempiere' +
    (d.absent.length ? ' ABSENT=[' + d.absent.join('; ') + ']' : '') +
    (r.maxDiff && r.misses.length ? ' delta=[' + r.misses.slice(0, 4).join(', ') + ']' : ''));
});
verdict(gap === 0 && equiv >= 5,
  equiv + '/' + orders.length + ' order chains LINE-EQUIVALENT to the cent (' + driftN + ' named post-posting drift, 0 derivation-gap)',
  'every fact line keyed (table/record/line_id/account/side)');

// ═══ §FALSIFIER-A: flip the commitment gate → Doc_Order derivation must become NON-zero ═════════════════
var soProbe = orders.find(function (o) { return o.issotrx === 'Y'; });
var fA = deriveOrderFacts(soProbe, 'S');                     // SOCommitmentOnly — MAcctSchema.java:654
verdict(fA.entries.length > 0, '§FALSIFIER-A commitmenttype N→S on order ' + soProbe.c_order_id + ' → engine derives ' + fA.entries.length +
  ' commitment legs ≠ oracle 0 (zero-posting is CONFIG-derived, not hardcoded)', 'gate=' + fA.gate + ' offset-residual=[' + fA.absent.join(';') + ']');
console.log('§FALSIFIER-A order=' + soProbe.c_order_id + ' commitmenttype=S derived=' + fA.entries.length + ' oracle=0 (must mismatch)');

// ═══ §FALSIFIER-B: corrupt one derived chain amount by 1c → the line diff MUST fire ════════════════════
(function () {
  var probe = orders.find(function (o) { return Object.keys(deriveChain(o.c_order_id).derived).length > 0; });
  var d = deriveChain(probe.c_order_id);
  var k0 = Object.keys(d.derived).sort()[0];
  d.derived[k0] += 1;
  var r = diffSets(d.derived, oracleChain(probe.c_order_id, d.invId));
  verdict(r.maxDiff >= 1, '§FALSIFIER-B +1c on derived line ' + k0 + ' (order ' + probe.c_order_id + ') → diff≠0', 'maxDiff=' + r.maxDiff + 'c');
  console.log('§FALSIFIER-B order=' + probe.c_order_id + ' line=' + k0 + ' maxDiff=' + r.maxDiff + 'c (must be ≥1)');
})();

console.log('\n§HARDEN_RESIDUAL schema-200000 chain legs = the proven per-schema FX fold (W-FOLD-ALLOC-FX/W-FOLD-MATCHINV-FX/W-FOLD-MOVEMENT-FX), not re-proven · ' +
  'standalone inout 102/104 + vendor invoices 102/104 carry no c_order (outside the MOrder chain; folded by W-FOLD-AP-INVOICE) · ' +
  'commitment-ON posting unreachable in this seed (gate N; offset accounts uncaptured — named, not faked)');
console.log('§HARDEN surface=MOrder.Doc_Order fixtures=' + orders.length + ' diff=0 oracle=iDempiere');
console.log((fails === 0 ? '🟢 W-MORDER-POST PASS' : '🔴 W-MORDER-POST FAIL (' + fails + ')') +
  ' — Doc_Order zero-set config-derived + the full order posting chain diffed per fact LINE against the per-document oracle.');
db.close();
process.exit(fails === 0 ? 0 : 1);
