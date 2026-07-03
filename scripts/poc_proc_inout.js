// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-PROC-SHIP is ✅ DONE in AD_PROCESS_FOLD_LANE.md §P2.
// SCOPE: W-PROC-SHIP — AD_Process 118 (org.compiere.process.InOutGenerate, "Generate Shipments") dispatched
//   through build/erp/ad_process.js folds a Completed Sales Order → an M_InOut (shipment) op-group via the
//   EXISTING erp_engine.buildDoc archetype (KIND 2, newVerbs=0 — the DOC_SPECS.createShipment shape). Proves:
//   (a) dispatch resolves the real classname to the registered KIND-2 handler; (b) a fully-delivered CO order
//   (the seed's real state) honestly yields ZERO shipment lines — never a fabricated shipment (HONESTY INVARIANT);
//   (c) an order with undelivered qty folds to MovementQty = min(toDeliver, onHand) under DeliveryRule='A'
//   (Availability), diffed against an INDEPENDENT re-derivation; (d) the Availability CAP is load-bearing
//   (onHand < toDeliver → deliver is clamped to onHand); (e) a §FALSIFIER (bent QtyDelivered) breaks the diff;
//   (f) a non-F/A DeliveryRule is NAMED-DEFERRED (flagged), not silently shipped.
// NON-INVENT: order 100 + its line/product/warehouse are REAL ad_full.db rows; on-hand is the REAL summed
//   m_storageonhand for the product in the order's warehouse. The seed's CO orders are fully delivered, so the
//   positive-fold branch BENDS QtyDelivered on the real line (an explicit, labelled perturbation) to expose
//   undelivered qty — every other value stays a real row. toDeliver = QtyOrdered − QtyDelivered (source line 278).
// §-log first — READ build/erp/poc_proc_inout.log before concluding (exit code ≠ evidence).
// Run:  node scripts/poc_proc_inout.js 2>&1 | tee build/erp/poc_proc_inout.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var P  = require(path.join(ERP, 'ad_process.js'));
var E  = require(path.join(ERP, 'erp_engine.js'));
var BD = require(path.join(ERP, 'bigdecimal.js'));
var DB_PATH = path.join(ERP, 'ad_full.db');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log((ok ? '🟢' : '🔴') + ' ' + label + (detail ? '  — ' + detail : '')); }

console.log('═══ W-PROC-SHIP — InOutGenerate (AD_Process 118) folds a CO Sales Order → M_InOut op-group ═══\n');

var db = new Database(DB_PATH, { readonly: true });
P.registerInOutGenerate(E);
var SHIP = 'org.compiere.process.InOutGenerate';
var ORDER_ID = 100;                                            // a real CO/SO order (deliveryrule='A', wh 103)

// on-hand of a product in a warehouse — REAL summed m_storageonhand over that warehouse's locators.
function onHandOf(pid, wh) {
  var r = db.prepare('SELECT COALESCE(SUM(s.qtyonhand),0) oh FROM m_storageonhand s JOIN m_locator l ON l.m_locator_id=s.m_locator_id WHERE s.m_product_id=? AND l.m_warehouse_id=?').get(pid, wh);
  return r ? Number(r.oh) : 0;
}
var rawOrder = db.prepare('SELECT * FROM c_order WHERE c_order_id=?').get(ORDER_ID);
var rawLines = db.prepare('SELECT * FROM c_orderline WHERE c_order_id=? ORDER BY line').all(ORDER_ID);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 1 · §SHIP-DISPATCH — 118 resolves the real classname → the registered KIND-2 handler.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('— §SHIP-DISPATCH (real AD_Process 118 row → classname → registered handler) —');
var proc118 = P.readProcess(db, 118);
console.log('§SHIP-DISPATCH proc=118 value=' + proc118.value + ' classname=' + proc118.classname + ' isReport=' + proc118.isReport + ' hasHandler=' + P.hasHandler(SHIP));
verdict(proc118.classname === SHIP && proc118.isReport === false && P.hasHandler(SHIP),
        'AD_Process 118 = InOutGenerate resolves to a registered KIND-2 handler', 'value=' + proc118.value);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 2 · §SHIP-EMPTY — the REAL order 100 is fully delivered (QtyOrdered==QtyDelivered) → honest ZERO lines.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §SHIP-EMPTY (fully-delivered CO order → honest empty, no fabricated shipment) —');
var emptyCtx = { fetchOrder: function () { return rawOrder; }, fetchOrderLines: function () { return rawLines; }, onHandOf: onHandOf };
var rEmpty = P.dispatch(db, { AD_Process_ID: 118, Record_ID: ORDER_ID, params: { M_Warehouse_ID: rawOrder.m_warehouse_id } }, emptyCtx);
var ordTot = rawLines.reduce(function (a, l) { return a + Number(l.qtyordered || 0); }, 0);
var delTot = rawLines.reduce(function (a, l) { return a + Number(l.qtydelivered || 0); }, 0);
console.log('§SHIP-EMPTY order=' + ORDER_ID + ' rule=' + rawOrder.deliveryrule + ' lines=' + rawLines.length + ' ordered=' + ordTot + ' delivered=' + delTot +
            ' → dispatched=' + rEmpty.dispatched + ' ok=' + rEmpty.ok + ' lineCount=' + (rEmpty.result && rEmpty.result.lineCount) + ' msg="' + rEmpty.message + '"');
verdict(rEmpty.dispatched === true && rEmpty.ok === true && rEmpty.result.lineCount === 0 && (!rEmpty.result.ops || !rEmpty.result.ops.length) && /nothing to deliver/.test(rEmpty.message),
        'fully-delivered order → 0 shipment lines, honest "@Created@ = 0" (no fabricated M_InOut)', 'ordered==delivered==' + ordTot);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 3 · §SHIP-FOLD — BEND QtyDelivered on the real line to expose undelivered qty → fold an M_InOut op-group.
//     Availability rule: MovementQty = min(toDeliver, onHand). Diffed vs an independent re-derivation.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §SHIP-FOLD (undelivered qty → M_InOut op-group, MovementQty = min(toDeliver, onHand)) —');
function foldCtx(lineMut) {
  return {
    fetchOrder: function () { return rawOrder; },
    fetchOrderLines: function () { return rawLines.map(function (l) { return lineMut ? Object.assign({}, l, lineMut(l)) : l; }); },
    onHandOf: onHandOf
  };
}
// bend QtyDelivered → 0 on every line (real qtyordered untouched) — toDeliver = qtyordered.
var rFold = P.dispatch(db, { AD_Process_ID: 118, Record_ID: ORDER_ID, params: { M_Warehouse_ID: rawOrder.m_warehouse_id } }, foldCtx(function () { return { qtydelivered: 0 }; }));
var ops = (rFold.result && rFold.result.ops) || [];
var hdr = ops[0] || {};
var lineOps = ops.filter(function (o) { return o.op_type === 'CREATE_LINE'; });
console.log('§SHIP-FOLD dispatched=' + rFold.dispatched + ' ok=' + rFold.ok + ' rows=' + rFold.rows + ' ops=' + ops.length +
            ' header{table=' + hdr.table + ' movementtype=' + hdr.movementtype + ' issotrx=' + hdr.issotrx + ' wh=' + hdr.m_warehouse_id + ' c_order_id=' + hdr.c_order_id + '}');
verdict(rFold.ok === true && hdr.op_type === 'CREATE_DOCUMENT' && hdr.table === 'M_InOut' && hdr.movementtype === 'C-' && hdr.issotrx === 'Y' &&
        hdr.m_warehouse_id === rawOrder.m_warehouse_id && hdr.c_order_id === ORDER_ID && hdr.source_id === ORDER_ID,
        'M_InOut header folded from the order (MovementType C- shipment, warehouse + order linked)', 'movementtype=' + hdr.movementtype);

// ORACLE: independent re-derivation of deliver = min(QtyOrdered − 0, onHand) per line.
var oracle = rawLines.map(function (l) {
  var toDeliver = Number(l.qtyordered || 0) - 0;
  var oh = onHandOf(l.m_product_id, rawOrder.m_warehouse_id);
  return { c_orderline_id: l.c_orderline_id, m_product_id: l.m_product_id, toDeliver: toDeliver, onHand: oh, deliver: Math.min(toDeliver, oh) };
}).filter(function (o) { return o.deliver > 0; });
console.log('§SHIP-ORACLE independent re-derivation lines=' + oracle.length + ' (deliver=min(toDeliver,onHand) per line)');
verdict(lineOps.length === oracle.length && oracle.length > 0, 'shipment-line count == independent re-derivation', 'eng=' + lineOps.length + ' oracle=' + oracle.length);
var oByPid = {}; oracle.forEach(function (o) { oByPid[o.m_product_id] = o; });
var mism = 0;
lineOps.forEach(function (op) {
  var o = oByPid[op.m_product_id];
  if (!o) { mism++; console.log('   ⚠ FINDING no oracle line for product ' + op.m_product_id); return; }
  if (op.table !== 'M_InOutLine') { mism++; console.log('   ⚠ FINDING line table ' + op.table); }
  if (op.source_line_id !== o.c_orderline_id) { mism++; console.log('   ⚠ FINDING source_line_id eng=' + op.source_line_id + ' oracle=' + o.c_orderline_id); }
  if (BD.of(op.movementqty).compareTo(BD.of(o.deliver)) !== 0) { mism++; console.log('   ⚠ FINDING MovementQty product=' + op.m_product_id + ' eng=' + op.movementqty + ' oracle=' + o.deliver + ' (toDeliver=' + o.toDeliver + ' onHand=' + o.onHand + ')'); }
});
console.log('§SHIP-FOLD lines=' + lineOps.length + ' fields{movementqty,source_line} mismatches=' + mism + ' (product 130: toDeliver=' + (oByPid[130] && oByPid[130].toDeliver) + ' onHand=' + (oByPid[130] && oByPid[130].onHand) + ')');
verdict(mism === 0, 'every shipment line == independent re-derivation, EXACT (MovementQty min(toDeliver,onHand), source link)', 'lines=' + lineOps.length + ' mismatches=' + mism);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 4 · §SHIP-AVAILABILITY — the Availability CAP is load-bearing: order MORE than on-hand → deliver = onHand.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §SHIP-AVAILABILITY (toDeliver > onHand → deliver clamped to onHand) —');
var probe = rawLines[0];
var oh130 = onHandOf(probe.m_product_id, rawOrder.m_warehouse_id);
var bigQty = oh130 + 7;                                        // demand strictly above on-hand
var rCap = P.dispatch(db, { AD_Process_ID: 118, Record_ID: ORDER_ID, params: { M_Warehouse_ID: rawOrder.m_warehouse_id } },
  foldCtx(function (l) { return l.c_orderline_id === probe.c_orderline_id ? { qtyordered: bigQty, qtydelivered: 0 } : { qtydelivered: 0 }; }));
var capLine = (rCap.result.ops || []).filter(function (o) { return o.op_type === 'CREATE_LINE' && o.m_product_id === probe.m_product_id; })[0];
console.log('§SHIP-AVAILABILITY product=' + probe.m_product_id + ' ordered=' + bigQty + ' onHand=' + oh130 + ' → MovementQty=' + (capLine && capLine.movementqty));
verdict(capLine && BD.of(capLine.movementqty).compareTo(BD.of(oh130)) === 0 && bigQty > oh130,
        'Availability rule CLAMPS deliver to on-hand when demand exceeds stock (not the raw ordered qty)', 'deliver=' + (capLine && capLine.movementqty) + ' == onHand=' + oh130 + ' < ordered=' + bigQty);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 5 · §SHIP-FALSIFIER — restore QtyDelivered to QtyOrdered (toDeliver→0) → the line must VANISH from the fold.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §SHIP-FALSIFIER (QtyDelivered == QtyOrdered → toDeliver=0 → no shipment line) —');
var rNull = P.dispatch(db, { AD_Process_ID: 118, Record_ID: ORDER_ID, params: { M_Warehouse_ID: rawOrder.m_warehouse_id } },
  foldCtx(function (l) { return { qtydelivered: l.qtyordered }; }));
console.log('§SHIP-FALSIFIER all lines fully delivered → lineCount=' + (rNull.result && rNull.result.lineCount));
verdict(rNull.result && rNull.result.lineCount === 0,
        'fully-delivering the lines removes them from the fold (toDeliver is load-bearing, not vacuous)', '');

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 6 · §SHIP-DEFERRED — a non-F/A DeliveryRule is NAMED-DEFERRED (flagged), never silently shipped.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §SHIP-DEFERRED (DeliveryRule "L" CompleteLine → named-deferred, honest) —');
var rDef = P.dispatch(db, { AD_Process_ID: 118, Record_ID: ORDER_ID, params: { M_Warehouse_ID: rawOrder.m_warehouse_id } }, {
  fetchOrder: function () { return Object.assign({}, rawOrder, { deliveryrule: 'L' }); },
  fetchOrderLines: function () { return rawLines.map(function (l) { return Object.assign({}, l, { qtydelivered: 0 }); }); },
  onHandOf: onHandOf
});
console.log('§SHIP-DEFERRED rule=L → lineCount=' + (rDef.result && rDef.result.lineCount) + ' deferredRule=' + (rDef.result && rDef.result.deferredRule) + ' msg="' + rDef.message + '"');
verdict(rDef.ok === true && rDef.result.lineCount === 0 && rDef.result.deferredRule === 'L' && /named-deferred/.test(rDef.message),
        'unported DeliveryRule is named-deferred (flagged in the result), NOT a silent or fabricated shipment', 'deferredRule=L');

db.close();
console.log('\n' + (fails ? '🔴 ' + fails + ' check(s) FAILED — W-PROC-SHIP NOT proven' : '═══ ALL PASS — InOutGenerate folds a CO Sales Order → M_InOut op-group (Availability), oracle-equivalent — W-PROC-SHIP ═══'));
process.exit(fails ? 1 : 0);
