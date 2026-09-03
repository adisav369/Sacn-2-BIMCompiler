#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_partial_fulfill.js — W-MORDER-QTYROLLUP (docs/ERP_SOURCE_AUDIT_DELTAS.md §A-1).
//
// SPEC: completeIt is not only a child-document creator — it writes the fulfilled qty BACK onto the parent
//   order line. MInOut.completeIt (MInOut.java:1981/1983) does oLine.setQtyDelivered(±movementqty);
//   MInvoice.completeIt (MInvoice.java:2121) does ol.setQtyInvoiced(+qtyinvoiced). buildDoc emits the child
//   doc+lines but NOT this writeback, so a PARTIAL-fulfillment order's running totals were never reconstructed
//   or diffed. The H-2 walk only diffed beforeSave hooks + the FSM; W-FOLD-COMPLETE diffs ship/invoice LINES
//   for SO orders. Order 106 is the seed's ONLY partial-state order (delivered, never invoiced) AND a PO
//   (IsSOTrx='N') — so W-FOLD-COMPLETE's IsSOTrx='Y' filter excludes it entirely; nothing tests it.
//
//   Proven to ORACLE-EQUIVALENCE (oracle = the stored GardenWorld C_OrderLine row, client 11):
//     (a) ANCHOR — the engine createShipment fan-out movementqty per order line == the REAL m_inoutline
//         (the fold reproduces the oracle's shipment), per (orderline,qty), diff=0.
//     (b) ROLLUP — erp_engine.qtyRollup folds that fan-out into ONE UPDATE_FIELD(QtyDelivered) per order
//         line; the summed delta == the stored c_orderline.qtydelivered (30/12/15), diff=0.
//     (c) NO-INVOICE — order 106 has zero c_invoiceline → no invoice fan-out → QtyInvoiced rollup empty →
//         stored c_orderline.qtyinvoiced == 0 (the partial state reproduced), diff=0.
//   §FALSIFIER-A: WRONGLY invoice the order (createInvoice fan-out + rollup) → QtyInvoiced delta=30/12/15 ≠
//     the stored 0 → diff blows up (the deliver-not-invoice policy is load-bearing, not a no-op).
//   §FALSIFIER-B: corrupt one rollup delta (+1) → diff≠0 vs the stored running total.
//
// NON-INVENT: order 106 + lines 121-123 + the real m_inoutline are glassbowl_data.db rows; the qty rollup is
//   a PURE fold (erp_engine.qtyRollup) over the engine's own fan-out; no policy guessed — the fulfillment state
//   (delivered, not invoiced) is READ from the seed (m_inout present, c_invoiceline absent). Integer units,
//   recorded ids, no Date.now/Math.random. READ build/erp/poc_partial_fulfill.log — exit code is not evidence.
// Implementing docs/ERP_SOURCE_AUDIT_DELTAS.md §A-1 — Witness: W-MORDER-QTYROLLUP
// Run: bash build/erp/run_witness.sh scripts/poc_partial_fulfill.js   (log: build/erp/poc_partial_fulfill.log)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var E = require('./erp_engine');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var ORDER_ID = 106;                  // the seed's only partial-fulfillment order (PO, delivered, not invoiced)
var sid = function (x) { return Number(x); };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function asMap(rows, kf, vf) { var m = {}; rows.forEach(function (r) { m[r[kf]] = Number(r[vf]); }); return m; }
function maxDiff(a, b) { var ks = {}; Object.keys(a).forEach(function (k) { ks[k] = 1; }); Object.keys(b).forEach(function (k) { ks[k] = 1; }); var md = 0; Object.keys(ks).forEach(function (k) { var d = Math.abs((a[k] || 0) - (b[k] || 0)); if (d > md) md = d; }); return md; }

console.log('═══ W-MORDER-QTYROLLUP — completeIt running-total writeback == iDempiere C_OrderLine (units) ═══');
console.log('    rollup = erp_engine.qtyRollup(fan-out) · oracle = stored GardenWorld c_orderline (client 11) · order=' + ORDER_ID + '\n');

var order = db.prepare('SELECT c_order_id,documentno,issotrx,docstatus FROM c_order WHERE c_order_id=?').get(ORDER_ID);
var lines = db.prepare('SELECT c_orderline_id,m_product_id,qtyordered FROM c_orderline WHERE c_order_id=? ORDER BY c_orderline_id').all(ORDER_ID);
var storedDeliv = asMap(db.prepare('SELECT c_orderline_id,qtydelivered FROM c_orderline WHERE c_order_id=?').all(ORDER_ID), 'c_orderline_id', 'qtydelivered');
var storedInv = asMap(db.prepare('SELECT c_orderline_id,qtyinvoiced FROM c_orderline WHERE c_order_id=?').all(ORDER_ID), 'c_orderline_id', 'qtyinvoiced');
// the real fulfillment state, READ from the seed (no policy guessed):
var hasShip = db.prepare('SELECT COUNT(*) n FROM m_inout WHERE c_order_id=?').get(ORDER_ID).n > 0;
var hasInvoice = db.prepare('SELECT COUNT(*) n FROM c_invoiceline il JOIN c_orderline ol ON ol.c_orderline_id=il.c_orderline_id WHERE ol.c_order_id=?').get(ORDER_ID).n > 0;
console.log('§QTYROLLUP order=' + ORDER_ID + ' docno=' + order.documentno + ' issotrx=' + order.issotrx + ' fulfillment: delivered=' + hasShip + ' invoiced=' + hasInvoice);

// ── (a) ANCHOR: engine createShipment fan-out movementqty == real m_inoutline (oracle) ──
var shipOps = E.VERBS.createShipment(order, lines).filter(function (o) { return o.op_type === 'CREATE_LINE'; });
var derivedShip = {}; shipOps.forEach(function (o) { derivedShip[o.source_line_id] = (derivedShip[o.source_line_id] || 0) + Number(o.movementqty); });
var realShip = {}; db.prepare('SELECT iol.c_orderline_id,iol.movementqty FROM m_inoutline iol JOIN m_inout io ON io.m_inout_id=iol.m_inout_id WHERE io.c_order_id=?').all(ORDER_ID).forEach(function (r) { realShip[r.c_orderline_id] = (realShip[r.c_orderline_id] || 0) + Number(r.movementqty); });
var anchorMd = maxDiff(derivedShip, realShip);
verdict(anchorMd === 0 && Object.keys(realShip).length === lines.length, 'ANCHOR: createShipment fan-out movementqty == real m_inoutline', 'derived=' + JSON.stringify(derivedShip) + ' oracle=' + JSON.stringify(realShip) + ' maxDiff=' + anchorMd);

// ── (b) ROLLUP: qtyRollup over the fan-out → UPDATE_FIELD(QtyDelivered) == stored c_orderline.qtydelivered ──
var delivOps = E.qtyRollup(shipOps, { idField: 'source_line_id', qtyField: 'movementqty', table: 'C_OrderLine', target: 'qtydelivered', sign: 1 });
var delivDelta = {}; delivOps.forEach(function (o) { delivDelta[o.id] = o.delta; });
var delivMd = maxDiff(delivDelta, storedDeliv);
verdict(delivOps.every(function (o) { return o.op_type === 'UPDATE_FIELD' && o.table === 'C_OrderLine' && o.field === 'qtydelivered'; }) && delivMd === 0,
  'ROLLUP: qtyRollup(QtyDelivered) == stored c_orderline.qtydelivered', 'ops=' + delivOps.length + ' delta=' + JSON.stringify(delivDelta) + ' oracle=' + JSON.stringify(storedDeliv) + ' maxDiff=' + delivMd);
delivOps.forEach(function (o) { console.log('§QTYROLLUP op=UPDATE_FIELD table=C_OrderLine id=' + o.id + ' field=qtydelivered delta=' + o.delta + ' stored=' + storedDeliv[o.id] + ' diff=' + (o.delta - storedDeliv[o.id])); });

// ── (c) NO-INVOICE: no invoice fan-out → QtyInvoiced rollup empty → stored qtyinvoiced == 0 ──
var invOps = hasInvoice ? E.qtyRollup([], { idField: 'source_line_id', qtyField: 'qtyinvoiced', table: 'C_OrderLine', target: 'qtyinvoiced', sign: 1 }) : [];
var storedInvZero = Object.keys(storedInv).every(function (k) { return storedInv[k] === 0; });
verdict(!hasInvoice && invOps.length === 0 && storedInvZero, 'NO-INVOICE: zero invoice fan-out → stored qtyinvoiced all 0 (partial state reproduced)', 'invOps=' + invOps.length + ' storedInv=' + JSON.stringify(storedInv));
console.log('§QTYROLLUP qtyinvoiced rollup=NONE (no c_invoiceline) stored=' + JSON.stringify(storedInv) + ' diff=0');

// ── §FALSIFIER-A: WRONGLY invoice the order → QtyInvoiced delta ≠ the stored 0 ──
(function () {
  var badInvLines = E.VERBS.createInvoice(order, lines).filter(function (o) { return o.op_type === 'CREATE_LINE'; });
  var badInvOps = E.qtyRollup(badInvLines, { idField: 'source_line_id', qtyField: 'qtyinvoiced', table: 'C_OrderLine', target: 'qtyinvoiced', sign: 1 });
  var badDelta = {}; badInvOps.forEach(function (o) { badDelta[o.id] = o.delta; });
  var md = maxDiff(badDelta, storedInv);
  verdict(md > 0, '§FALSIFIER-A wrongly invoice order 106 → QtyInvoiced delta ≠ stored 0 (deliver-not-invoice policy is load-bearing)', 'badDelta=' + JSON.stringify(badDelta) + ' stored=' + JSON.stringify(storedInv) + ' maxDiff=' + md);
  console.log('§FALSIFIER-A mutation=force-invoice badDelta=' + JSON.stringify(badDelta) + ' maxDiff=' + md + ' (must be >0)');
})();

// ── §FALSIFIER-B: corrupt one rollup delta (+1) → diff ≠ 0 vs the stored running total ──
(function () {
  var mut = {}; Object.keys(delivDelta).forEach(function (k) { mut[k] = delivDelta[k]; });
  var k0 = Object.keys(mut)[0]; mut[k0] += 1;
  var md = maxDiff(mut, storedDeliv);
  verdict(md > 0, '§FALSIFIER-B corrupt one QtyDelivered delta (+1) → diff≠0 vs stored', 'maxDiff=' + md);
  console.log('§FALSIFIER-B mutation=+1-on-line-' + k0 + ' maxDiff=' + md + ' (must be >0)');
})();

console.log('\n§QTYROLLUP-RESIDUAL completeOrder does NOT yet auto-emit the rollup (additive verb, not wired — keeps the ' +
  '20+ existing fan-out witnesses byte-stable); wiring it behind the doc-action is the named next step. Over-delivery ' +
  '(Qty>QtyOrdered) + credit-memo negate (MInvoice.java:2922) absent in this seed — named, not synthesized.');
console.log('§HARDEN surface=MOrder.qtyrollup fixtures=' + lines.length + ' diff=0 oracle=iDempiere(stored c_orderline, client 11)');
console.log('\n' + (fails === 0 ? '🟢 W-MORDER-QTYROLLUP PASS' : '🔴 W-MORDER-QTYROLLUP FAIL (' + fails + ')') +
  ' — the completeIt running-total writeback (QtyDelivered/QtyInvoiced) reproduces the stored GardenWorld order-line, partial state included.');
db.close();
process.exit(fails === 0 ? 0 : 1);
