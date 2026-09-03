// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-PROC-INV is ✅ DONE in AD_PROCESS_FOLD_LANE.md §P2-leg2.
// SCOPE: W-PROC-INV — AD_Process 119 (org.compiere.process.InvoiceGenerate, "Generate Invoices") dispatched
//   through build/erp/ad_process.js folds a Completed Sales Order → a C_Invoice op-group via the EXISTING
//   erp_engine.buildDoc archetype (KIND 2, newVerbs=0 — the DOC_SPECS.createInvoice shape). The order-to-cash
//   CLOSER, sibling of InOutGenerate. Proves:
//   (a) dispatch resolves the real classname to the registered KIND-2 handler; (b) a fully-invoiced CO order
//   (the seed's real state) honestly yields ZERO invoice lines — never a fabricated invoice (HONESTY INVARIANT);
//   (c) an order with un-invoiced qty under InvoiceRule='I' (Immediate) folds to QtyInvoiced = QtyOrdered −
//   QtyInvoiced, diffed against an INDEPENDENT re-derivation; (d) the C_Invoice header is copied from the order
//   (IsSOTrx, Bill_Location, currency, paymentterm, pricelist, order link); (e) a §FALSIFIER (restore
//   QtyInvoiced) breaks the fold; (f) a non-Immediate InvoiceRule ('D'/'O'/'S') is NAMED-DEFERRED (flagged),
//   never silently invoiced (those invoice from the shipment/schedule corpus).
// NON-INVENT: order 108 + its line/product are REAL ad_full.db rows (rule='I', fully invoiced). The seed's CO
//   orders are fully invoiced, so the positive-fold branch BENDS QtyInvoiced on the real line (an explicit,
//   labelled perturbation) to expose un-invoiced qty — every other value stays a real row. toInvoice =
//   QtyOrdered − QtyInvoiced (source InvoiceGenerate.java:299).
// §-log first — READ build/erp/poc_proc_inv.log before concluding (exit code ≠ evidence).
// Run:  node scripts/poc_proc_inv.js 2>&1 | tee build/erp/poc_proc_inv.log
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

console.log('═══ W-PROC-INV — InvoiceGenerate (AD_Process 119) folds a CO Sales Order → C_Invoice op-group ═══\n');

var db = new Database(DB_PATH, { readonly: true });
P.registerInvoiceGenerate(E);
var INV = 'org.compiere.process.InvoiceGenerate';
var ORDER_ID = 108;                                           // a real CO/SO order (invoicerule='I', fully invoiced)

var rawOrder = db.prepare('SELECT * FROM c_order WHERE c_order_id=?').get(ORDER_ID);
var rawLines = db.prepare('SELECT * FROM c_orderline WHERE c_order_id=? ORDER BY line').all(ORDER_ID);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 1 · §INV-DISPATCH — 119 resolves the real classname → the registered KIND-2 handler.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('— §INV-DISPATCH (real AD_Process 119 row → classname → registered handler) —');
var proc119 = P.readProcess(db, 119);
console.log('§INV-DISPATCH proc=119 value=' + proc119.value + ' classname=' + proc119.classname + ' isReport=' + proc119.isReport + ' hasHandler=' + P.hasHandler(INV));
verdict(proc119.classname === INV && proc119.isReport === false && P.hasHandler(INV),
        'AD_Process 119 = InvoiceGenerate resolves to a registered KIND-2 handler', 'value=' + proc119.value);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 2 · §INV-EMPTY — the REAL order 108 is fully invoiced (QtyOrdered==QtyInvoiced) → honest ZERO lines.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §INV-EMPTY (fully-invoiced CO order → honest empty, no fabricated invoice) —');
var emptyCtx = { fetchOrder: function () { return rawOrder; }, fetchOrderLines: function () { return rawLines; } };
var rEmpty = P.dispatch(db, { AD_Process_ID: 119, Record_ID: ORDER_ID, params: { AD_Org_ID: rawOrder.ad_org_id, DocAction: 'CO' } }, emptyCtx);
var ordTot = rawLines.reduce(function (a, l) { return a + Number(l.qtyordered || 0); }, 0);
var invTot = rawLines.reduce(function (a, l) { return a + Number(l.qtyinvoiced || 0); }, 0);
console.log('§INV-EMPTY order=' + ORDER_ID + ' rule=' + rawOrder.invoicerule + ' lines=' + rawLines.length + ' ordered=' + ordTot + ' invoiced=' + invTot +
            ' → dispatched=' + rEmpty.dispatched + ' ok=' + rEmpty.ok + ' lineCount=' + (rEmpty.result && rEmpty.result.lineCount) + ' msg="' + rEmpty.message + '"');
verdict(rEmpty.dispatched === true && rEmpty.ok === true && rEmpty.result.lineCount === 0 && (!rEmpty.result.ops || !rEmpty.result.ops.length) && /nothing to invoice/.test(rEmpty.message),
        'fully-invoiced order → 0 invoice lines, honest "@Created@ = 0" (no fabricated C_Invoice)', 'ordered==invoiced==' + ordTot);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 3 · §INV-FOLD — BEND QtyInvoiced on the real line to expose un-invoiced qty → fold a C_Invoice op-group.
//     Immediate rule: QtyInvoiced = QtyOrdered − QtyInvoiced. Diffed vs an independent re-derivation.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §INV-FOLD (un-invoiced qty → C_Invoice op-group, QtyInvoiced = QtyOrdered − QtyInvoiced) —');
function foldCtx(lineMut) {
  return {
    fetchOrder: function () { return rawOrder; },
    fetchOrderLines: function () { return rawLines.map(function (l) { return lineMut ? Object.assign({}, l, lineMut(l)) : l; }); }
  };
}
// bend QtyInvoiced → 0 on every line (real qtyordered untouched) — toInvoice = qtyordered.
var rFold = P.dispatch(db, { AD_Process_ID: 119, Record_ID: ORDER_ID, params: { AD_Org_ID: rawOrder.ad_org_id, DocAction: 'CO' } }, foldCtx(function () { return { qtyinvoiced: 0 }; }));
var ops = (rFold.result && rFold.result.ops) || [];
var hdr = ops[0] || {};
var lineOps = ops.filter(function (o) { return o.op_type === 'CREATE_LINE'; });
console.log('§INV-FOLD dispatched=' + rFold.dispatched + ' ok=' + rFold.ok + ' rows=' + rFold.rows + ' ops=' + ops.length +
            ' header{table=' + hdr.table + ' issotrx=' + hdr.issotrx + ' bp=' + hdr.c_bpartner_id + ' billloc=' + hdr.c_bpartner_location_id + ' cur=' + hdr.c_currency_id + ' pt=' + hdr.c_paymentterm_id + ' pl=' + hdr.m_pricelist_id + ' c_order_id=' + hdr.c_order_id + '}');
verdict(rFold.ok === true && hdr.op_type === 'CREATE_DOCUMENT' && hdr.table === 'C_Invoice' && hdr.issotrx === 'Y' &&
        hdr.c_bpartner_id === rawOrder.c_bpartner_id && hdr.c_bpartner_location_id === rawOrder.bill_location_id &&
        hdr.c_currency_id === rawOrder.c_currency_id && hdr.c_paymentterm_id === rawOrder.c_paymentterm_id &&
        hdr.c_order_id === ORDER_ID && hdr.source_id === ORDER_ID,
        'C_Invoice header folded from the order (IsSOTrx, Bill_Location, currency, paymentterm, order linked)', 'billloc=' + hdr.c_bpartner_location_id + ' (Bill_Location=' + rawOrder.bill_location_id + ')');

// ORACLE: independent re-derivation of toInvoice = QtyOrdered − 0 per line (skip 0-with-product).
var oracle = rawLines.map(function (l) {
  var toInvoice = Number(l.qtyordered || 0) - 0;
  return { c_orderline_id: l.c_orderline_id, m_product_id: l.m_product_id, toInvoice: toInvoice };
}).filter(function (o) { return !(o.toInvoice === 0 && o.m_product_id) && o.toInvoice > 0; });
console.log('§INV-ORACLE independent re-derivation lines=' + oracle.length + ' (toInvoice=QtyOrdered−QtyInvoiced per line)');
verdict(lineOps.length === oracle.length && oracle.length > 0, 'invoice-line count == independent re-derivation', 'eng=' + lineOps.length + ' oracle=' + oracle.length);
var oByPid = {}; oracle.forEach(function (o) { oByPid[o.m_product_id] = o; });
var mism = 0;
lineOps.forEach(function (op) {
  var o = oByPid[op.m_product_id];
  if (!o) { mism++; console.log('   ⚠ FINDING no oracle line for product ' + op.m_product_id); return; }
  if (op.table !== 'C_InvoiceLine') { mism++; console.log('   ⚠ FINDING line table ' + op.table); }
  if (op.source_line_id !== o.c_orderline_id) { mism++; console.log('   ⚠ FINDING source_line_id eng=' + op.source_line_id + ' oracle=' + o.c_orderline_id); }
  if (BD.of(op.qtyinvoiced).compareTo(BD.of(o.toInvoice)) !== 0) { mism++; console.log('   ⚠ FINDING QtyInvoiced product=' + op.m_product_id + ' eng=' + op.qtyinvoiced + ' oracle=' + o.toInvoice); }
});
console.log('§INV-FOLD lines=' + lineOps.length + ' fields{qtyinvoiced,source_line} mismatches=' + mism);
verdict(mism === 0, 'every invoice line == independent re-derivation, EXACT (QtyInvoiced = QtyOrdered − QtyInvoiced, source link)', 'lines=' + lineOps.length + ' mismatches=' + mism);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 4 · §INV-PARTIAL — partial prior invoicing: QtyInvoiced=4 of 10 → toInvoice=6 (subtract is load-bearing).
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §INV-PARTIAL (QtyInvoiced=4 of QtyOrdered=10 → toInvoice=6, the subtraction is load-bearing) —');
var probe = rawLines[0];
var rPart = P.dispatch(db, { AD_Process_ID: 119, Record_ID: ORDER_ID, params: { AD_Org_ID: rawOrder.ad_org_id, DocAction: 'CO' } },
  foldCtx(function (l) { return l.c_orderline_id === probe.c_orderline_id ? { qtyinvoiced: 4 } : { qtyinvoiced: 0 }; }));
var partLine = (rPart.result.ops || []).filter(function (o) { return o.op_type === 'CREATE_LINE' && o.m_product_id === probe.m_product_id; })[0];
var expectPart = Number(probe.qtyordered) - 4;
console.log('§INV-PARTIAL product=' + probe.m_product_id + ' ordered=' + probe.qtyordered + ' priorInvoiced=4 → QtyInvoiced=' + (partLine && partLine.qtyinvoiced) + ' (expect ' + expectPart + ')');
verdict(partLine && BD.of(partLine.qtyinvoiced).compareTo(BD.of(expectPart)) === 0 && expectPart > 0,
        'Immediate rule invoices only the REMAINING qty (QtyOrdered − prior QtyInvoiced), not the full ordered qty', 'toInvoice=' + (partLine && partLine.qtyinvoiced) + ' == ' + expectPart);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 5 · §INV-FALSIFIER — restore QtyInvoiced to QtyOrdered (toInvoice→0) → the line must VANISH from the fold.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §INV-FALSIFIER (QtyInvoiced == QtyOrdered → toInvoice=0 → no invoice line) —');
var rNull = P.dispatch(db, { AD_Process_ID: 119, Record_ID: ORDER_ID, params: { AD_Org_ID: rawOrder.ad_org_id, DocAction: 'CO' } },
  foldCtx(function (l) { return { qtyinvoiced: l.qtyordered }; }));
console.log('§INV-FALSIFIER all lines fully invoiced → lineCount=' + (rNull.result && rNull.result.lineCount));
verdict(rNull.result && rNull.result.lineCount === 0,
        'fully-invoicing the lines removes them from the fold (toInvoice is load-bearing, not vacuous)', '');

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 6 · §INV-DEFERRED — a non-Immediate InvoiceRule ('D' AfterDelivery) is NAMED-DEFERRED, never silently invoiced.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §INV-DEFERRED (InvoiceRule "D" AfterDelivery → named-deferred, honest) —');
var rDef = P.dispatch(db, { AD_Process_ID: 119, Record_ID: ORDER_ID, params: { AD_Org_ID: rawOrder.ad_org_id, DocAction: 'CO' } }, {
  fetchOrder: function () { return Object.assign({}, rawOrder, { invoicerule: 'D' }); },
  fetchOrderLines: function () { return rawLines.map(function (l) { return Object.assign({}, l, { qtyinvoiced: 0 }); }); }
});
console.log('§INV-DEFERRED rule=D → lineCount=' + (rDef.result && rDef.result.lineCount) + ' deferredRule=' + (rDef.result && rDef.result.deferredRule) + ' msg="' + rDef.message + '"');
verdict(rDef.ok === true && rDef.result.lineCount === 0 && rDef.result.deferredRule === 'D' && /named-deferred/.test(rDef.message),
        'unported InvoiceRule is named-deferred (flagged in the result), NOT a silent or fabricated invoice', 'deferredRule=D');

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 7 · §INV-GATE — a non-SO / non-CO order is honestly rejected (the selection predicate is load-bearing).
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §INV-GATE (DR order or DRaft order → honest rejection, no invoice) —');
var rGate = P.dispatch(db, { AD_Process_ID: 119, Record_ID: ORDER_ID, params: { AD_Org_ID: rawOrder.ad_org_id, DocAction: 'CO' } }, {
  fetchOrder: function () { return Object.assign({}, rawOrder, { docstatus: 'DR' }); },
  fetchOrderLines: function () { return rawLines.map(function (l) { return Object.assign({}, l, { qtyinvoiced: 0 }); }); }
});
console.log('§INV-GATE docstatus=DR → dispatched=' + rGate.dispatched + ' ok=' + rGate.ok + ' reason=' + rGate.reason + ' msg="' + rGate.message + '"');
verdict(rGate.dispatched === true && rGate.ok === false && rGate.reason === 'order-not-invoiceable' && /not Completed/.test(rGate.message),
        'a Drafted order is honestly rejected (Generate Invoices invoices CO/CL only), no fabricated invoice', 'reason=' + rGate.reason);

db.close();
console.log('\n' + (fails ? '🔴 ' + fails + ' check(s) FAILED — W-PROC-INV NOT proven' : '═══ ALL PASS — InvoiceGenerate folds a CO Sales Order → C_Invoice op-group (Immediate), oracle-equivalent — W-PROC-INV ═══'));
process.exit(fails ? 1 : 0);
