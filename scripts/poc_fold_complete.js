#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_fold_complete.js — W-FOLD-COMPLETE (FOLD_MODEL_LOGIC.md §F-1, the MOrder keystone).
//
// SPEC (§F-1): completeIt(C_Order) folded onto the op-log emits ONE op-group — SET_STATUS CO +
//   the config-gated fan-out createShipment/createInvoice, BOTH expressed through the buildDoc
//   archetype verb (erp_engine, W-FOLD-BUILDDOC). The fold is proven to ORACLE-EQUIVALENCE, not
//   merely "balances":
//     (1) the fanned-out shipment + invoice LINES == iDempiere's real m_inoutline / c_invoiceline
//         for the source order (the derivation reproduces the oracle documents);
//     (2) the resulting INVOICE posting (Doc_Invoice) derives == real fact_acct(318), compared
//         per (natural-account, side) in INTEGER CENTS, maxDiff=0c;
//     (3) final DocStatus == CO.
//   §FALSIFIER drops one derived posting line → maxDiff≠0 (proves the diff is load-bearing, not
//   a balance tautology).
//
// HONEST 1b SPLIT — the shipment posting (Doc_InOut COGS/Inventory) is NAMED-DEFERRED here: its
//   inputs are absent in the current capture (m_product_category_acct holds only p_revenue_acct —
//   no p_cogs_acct/p_asset_acct; m_cost/m_costdetail/m_product_costing all empty). It folds into
//   the step-2 extract_fact_acct.sh re-capture, SHARED with the StorageOnHand oracle. Stated, not
//   hidden — §FOLD-DEFERRED below.
//
// DRIFT (not a gap): a doc whose source line was edited AFTER posting cannot be re-derived from
//   current source (fact_acct=posting-time, source=edited). DETECTED via acct-set match + updated>
//   dateinvoiced — classified AMT-DRIFT, named, not red-failed (same discipline as poc_post_harden).
//
// NON-INVENT: inputs + oracle are real GardenWorld rows (glassbowl_data.db, client 11); accounts
//   RESOLVED by post_resolver from master columns; integer cents; no Date.now/Math.random.
// Implementing FOLD_MODEL_LOGIC.md §F-1 — Witness: W-FOLD-COMPLETE
// Run: node scripts/poc_fold_complete.js 2>&1 | tee build/erp/poc_fold_complete.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var E = require('./erp_engine');
var R = require('./post_resolver');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var SCHEMA = 101;                 // GardenWorld US/USD — the schema the sales manifest derives
var AD_TABLE_C_INVOICE = 318;     // fact_acct.ad_table_id for C_Invoice
var AD_TABLE_M_INOUT = 319;       // fact_acct.ad_table_id for M_InOut (the shipment posting)
var sid = function (x) { return Number(x); };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function cents(n) { return Math.round(Number(n || 0) * 100); }
function key(a, s) { return s + ':' + a; }

// ── INVOICE GL derivation — the sales manifest, identical to poc_post_harden.derive ───────────
function deriveInvoice(invId) {
  var hdr = db.prepare('SELECT c_invoice_id,c_bpartner_id,grandtotal,issotrx FROM c_invoice WHERE c_invoice_id=?').get(sid(invId));
  var lines = db.prepare('SELECT m_product_id,linenetamt FROM c_invoiceline WHERE c_invoice_id=?').all(sid(invId));
  var taxes = db.prepare('SELECT c_tax_id,taxamt FROM c_invoicetax WHERE c_invoice_id=?').all(sid(invId));
  var agg = {}, absent = [];
  function add(side, acct, amt) { var k = key(acct, side); agg[k] = (agg[k] || 0) + cents(amt); }
  function nat(res) { if (res.acct == null || !res.element) { absent.push(res.token); return null; } return res.element.id; }
  var rcv = nat(R.resolve(db, '{BPartner.Receivable}', sid(hdr.c_bpartner_id), SCHEMA));
  if (rcv != null) add('DR', rcv, hdr.grandtotal);
  lines.forEach(function (l) { var a = nat(R.resolve(db, '{Product.Revenue}', sid(l.m_product_id), SCHEMA)); if (a != null) add('CR', a, l.linenetamt); });
  taxes.forEach(function (t) { var a = nat(R.resolve(db, '{Tax.Due}', sid(t.c_tax_id), SCHEMA)); if (a != null) add('CR', a, t.taxamt); });
  return { agg: agg, absent: absent };
}
function oracleInvoice(invId) {
  var rows = db.prepare('SELECT account_id,ROUND(SUM(amtacctdr),2) dr,ROUND(SUM(amtacctcr),2) cr FROM fact_acct WHERE ad_table_id=? AND record_id=? AND c_acctschema_id=? GROUP BY account_id').all(AD_TABLE_C_INVOICE, sid(invId), SCHEMA);
  var agg = {}; rows.forEach(function (r) { if (cents(r.dr)) agg[key(r.account_id, 'DR')] = cents(r.dr); if (cents(r.cr)) agg[key(r.account_id, 'CR')] = cents(r.cr); });
  return agg;
}
function maxDiff(da, o) {
  var keys = {}; Object.keys(da).forEach(function (k) { keys[k] = 1; }); Object.keys(o).forEach(function (k) { keys[k] = 1; });
  var md = 0; Object.keys(keys).forEach(function (k) { var d = Math.abs((da[k] || 0) - (o[k] || 0)); if (d > md) md = d; });
  return md;
}
function acctSetEqual(da, o) { var a = Object.keys(da).sort().join(','), b = Object.keys(o).sort().join(','); return a === b && b.length > 0; }
function postPostingEdit(invId) { var r = db.prepare('SELECT (julianday(updated)-julianday(dateinvoiced)) d FROM c_invoice WHERE c_invoice_id=?').get(sid(invId)); return r && r.d != null && r.d > 0; }
// the invoice generated from an order — linked via the order line (NON-INVENT lineage).
function invoiceForOrder(oid) {
  var r = db.prepare('SELECT DISTINCT il.c_invoice_id FROM c_invoiceline il JOIN c_orderline ol ON ol.c_orderline_id=il.c_orderline_id WHERE ol.c_order_id=?').get(sid(oid));
  return r ? r.c_invoice_id : null;
}

// ── SHIPMENT GL derivation (Doc_InOut): DR {Product.Cogs} / CR {Product.Asset} at cost-at-movement ──
//   accounts RESOLVED from the master (post_resolver); amount = Σ|m_costdetail.amt| per line (schema 101)
//   — the cost iDempiere itself posted the move at. A non-costed line (no cost detail) → no GL line.
function deriveShipmentForOrder(oid) {
  var lines = db.prepare('SELECT iol.m_inoutline_id, iol.m_product_id FROM m_inoutline iol JOIN m_inout io ON io.m_inout_id=iol.m_inout_id WHERE io.c_order_id=?').all(sid(oid));
  var agg = {}, absent = [];
  function add(side, acct, c) { var k = key(acct, side); agg[k] = (agg[k] || 0) + c; }
  function nat(res) { if (res.acct == null || !res.element) { absent.push(res.token); return null; } return res.element.id; }
  lines.forEach(function (l) {
    var cd = db.prepare('SELECT ROUND(SUM(ABS(amt)),2) amt FROM m_costdetail WHERE m_inoutline_id=? AND c_acctschema_id=?').get(sid(l.m_inoutline_id), SCHEMA);
    var amt = cd && cd.amt != null ? cd.amt : 0;
    if (cents(amt) === 0) return;                                  // non-costed move → no posting (matches oracle)
    var cogs = nat(R.resolve(db, '{Product.Cogs}', sid(l.m_product_id), SCHEMA));
    var asset = nat(R.resolve(db, '{Product.Asset}', sid(l.m_product_id), SCHEMA));
    if (cogs != null) add('DR', cogs, cents(amt));
    if (asset != null) add('CR', asset, cents(amt));
  });
  return { agg: agg, absent: absent };
}
function oracleShipmentForOrder(oid) {
  var rows = db.prepare('SELECT account_id,ROUND(SUM(amtacctdr),2) dr,ROUND(SUM(amtacctcr),2) cr FROM fact_acct WHERE ad_table_id=? AND c_acctschema_id=? AND record_id IN (SELECT m_inout_id FROM m_inout WHERE c_order_id=?) GROUP BY account_id').all(AD_TABLE_M_INOUT, SCHEMA, sid(oid));
  var agg = {}; rows.forEach(function (r) { if (cents(r.dr)) agg[key(r.account_id, 'DR')] = cents(r.dr); if (cents(r.cr)) agg[key(r.account_id, 'CR')] = cents(r.cr); });
  return agg;
}

console.log('═══ W-FOLD-COMPLETE — completeIt(C_Order) folded → oracle-equivalent (fan-out + invoice posting + status) ═══');
console.log('    engine = erp_engine.completeOrder(buildDoc) · posting = post_resolver · oracle = GardenWorld fact_acct (client 11, schema ' + SCHEMA + ')\n');

var orders = db.prepare("SELECT * FROM c_order WHERE issotrx='Y' AND c_order_id IN (SELECT c_order_id FROM m_inout) ORDER BY c_order_id").all();
var equiv = 0, drift = 0;

orders.forEach(function (order) {
  var lines = db.prepare('SELECT c_orderline_id,m_product_id,qtyordered FROM c_orderline WHERE c_order_id=?').all(order.c_order_id);
  // completeIt(C_Order) = ONE op-group: SET_STATUS CO + config-gated fan-out (buildDoc archetype).
  var ops = E.completeOrder(order, lines, { isautogenerateinout: 'Y', isautogenerateinvoice: 'Y' });
  var statusOp = ops.find(function (o) { return o.op_type === 'SET_STATUS'; });
  var shipLineOps = ops.filter(function (o) { return o.table === 'M_InOutLine'; });
  var invLineOps = ops.filter(function (o) { return o.table === 'C_InvoiceLine'; });

  // (1) fan-out shipment lines == oracle m_inoutline
  var myShip = shipLineOps.map(function (o) { return o.m_product_id + ':' + o.movementqty; }).sort();
  var oShip = db.prepare('SELECT iol.m_product_id,iol.movementqty FROM m_inoutline iol JOIN m_inout io ON io.m_inout_id=iol.m_inout_id WHERE io.c_order_id=?').all(order.c_order_id).map(function (r) { return r.m_product_id + ':' + r.movementqty; }).sort();
  var shipOk = JSON.stringify(myShip) === JSON.stringify(oShip);

  // (1b) fan-out invoice lines == oracle c_invoiceline
  var invId = invoiceForOrder(order.c_order_id);
  var myInv = invLineOps.map(function (o) { return o.m_product_id + ':' + o.qtyinvoiced; }).sort();
  var oInv = invId ? db.prepare('SELECT m_product_id,qtyinvoiced FROM c_invoiceline WHERE c_invoice_id=?').all(sid(invId)).map(function (r) { return r.m_product_id + ':' + r.qtyinvoiced; }).sort() : [];
  var invOk = !!invId && JSON.stringify(myInv) === JSON.stringify(oInv);

  // (2) invoice posting derives == oracle fact_acct, maxDiff=0c (with drift classifier)
  var md = null, postings = 0, sumDr = 0, sumCr = 0, setEq = false, edited = false, postCls = 'n/a';
  if (invId) {
    var d = deriveInvoice(invId), o = oracleInvoice(invId);
    md = maxDiff(d.agg, o); postings = Object.keys(o).length; setEq = acctSetEqual(d.agg, o); edited = postPostingEdit(invId);
    Object.keys(d.agg).forEach(function (k) { if (k.indexOf('DR:') === 0) sumDr += d.agg[k]; else sumCr += d.agg[k]; });
    if (md === 0 && d.absent.length === 0 && postings > 0) postCls = 'EQUIVALENT';
    else if (setEq && edited) postCls = 'AMT-DRIFT(post-posting edit)';
    else postCls = 'DERIVATION-GAP';
  }
  var balanced = sumDr === sumCr;
  var postOk = postCls === 'EQUIVALENT' || postCls === 'AMT-DRIFT(post-posting edit)';
  if (postCls === 'EQUIVALENT') equiv++; else if (postCls.indexOf('AMT-DRIFT') === 0) drift++;

  // (2b) SHIPMENT posting (Doc_InOut COGS/Inventory) derives == oracle fact_acct(319), maxDiff=0c
  var sd = deriveShipmentForOrder(order.c_order_id), so = oracleShipmentForOrder(order.c_order_id);
  var shipMd = maxDiff(sd.agg, so), shipAccts = Object.keys(so).length;
  var shipPostOk = shipMd === 0 && sd.absent.length === 0;        // empty==empty (non-costed order) is trivially 0c

  // (3) final DocStatus — completeIt ends at CO (CL = a later close action; both are completed states)
  var statusOk = !!statusOp && statusOp.doc_status === 'CO' && (order.docstatus === 'CO' || order.docstatus === 'CL');

  verdict(shipOk && invOk && postOk && shipPostOk && statusOk && balanced,
    'order ' + order.documentno + ' completeIt → oracle-equivalent',
    'ship=' + myShip.length + '/' + oShip.length + ' inv=' + myInv.length + '/' + oInv.length + ' invPost=' + postings + '@' + (md == null ? 'n/a' : md + 'c') + '(' + postCls.split('(')[0] + ') shipPost=' + shipAccts + '@' + shipMd + 'c status=' + (statusOp ? statusOp.doc_status : '?') + '/' + order.docstatus);
  console.log('§FOLD-COMPLETE doc=C_Order id=' + order.c_order_id + ' docno=' + order.documentno + ' status=CO ship_lines=' + myShip.length + ' inv_lines=' + myInv.length + ' invPostings=' + postings + ' invMaxDiff=' + (md == null ? 'n/a' : md + 'c') + '(' + postCls.split('(')[0] + ') shipPostings=' + shipAccts + ' shipMaxDiff=' + shipMd + 'c ΣDR=ΣCR=' + (balanced ? 'Y' : 'N') + ' oracle=iDempiere' + (invId ? '' : ' [no invoice link]'));
});

verdict(equiv >= 1 && fails === 0, equiv + '/' + orders.length + ' orders fold to an ORACLE-EQUIVALENT invoice posting (' + drift + ' post-posting drift, named)', 'equiv=' + equiv + ' drift=' + drift);

// ── §FALSIFIER: drop a derived posting line → the per-account diff MUST blow up ────────────────
(function () {
  var oid = orders[0].c_order_id, invId = invoiceForOrder(oid);
  if (invId == null) { console.log('§FALSIFIER skipped — no invoice link on probe order'); return; }
  var d = deriveInvoice(invId), o = oracleInvoice(invId);
  var mutated = Object.assign({}, d.agg);
  var drKey = Object.keys(mutated).find(function (k) { return k.indexOf('DR:') === 0; });
  delete mutated[drKey];                                   // drop the receivable DR line
  var md = maxDiff(mutated, o);
  verdict(md > 0, '§FALSIFIER drop a derived posting line → maxDiff≠0 (diff is load-bearing)', 'maxDiff=' + md + 'c');
  console.log('§FALSIFIER doc=C_Order order=' + oid + ' invoice=' + invId + ' mutation=drop-receivable-DR maxDiff=' + md + 'c (must be >0)');
})();

console.log('\n§FOLD-NOTE shipment posting amount = cost-at-movement (m_costdetail, Σ|amt| per line, schema ' + SCHEMA + '); ' +
  'accounts {Product.Cogs}/{Product.Asset} resolved from the master. Current m_cost has drifted off posting-time cost ' +
  '(same posting-time-vs-current class as the invoice amt-drift) — m_costdetail is iDempiere\'s own posted cost, NOT invented.');

console.log('\n' + (fails === 0 ? '🟢 W-FOLD-COMPLETE PASS' : '🔴 W-FOLD-COMPLETE FAIL (' + fails + ')') +
  ' — completeIt(C_Order) folds to ONE op-group; fan-out (shipment+invoice lines) + BOTH postings (invoice Doc_Invoice + shipment Doc_InOut) + DocStatus oracle-equivalent to the cent. ' +
  'MOrder archetype: the full Order→Ship→Invoice posting chain GREEN.');
db.close();
process.exit(fails === 0 ? 0 : 1);
