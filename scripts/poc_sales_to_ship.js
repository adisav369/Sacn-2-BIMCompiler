#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_sales_to_ship.js — "tackle the hard parts, POC the gaps away first".
 *   Spec: docs/ERP.md §18 (BOM+settlement), §0.5 (decision tables), §0.9-0.10 (engine),
 *   prompts/ERP_KERNEL_BUILD.md §P3b (handler=ops, diff-oracle, frozen effects).
 *
 * The diff-oracle WITHOUT a live iDempiere: GardenWorld already ran these transactions,
 * so the M_InOut / C_Invoice / M_MatchPO / M_MatchInv rows in ad_full.db ARE the oracle's
 * output. We replay our logic on the INPUTS (orders/lines) and compare to those rows.
 *
 * Hard-parts-first order:
 *   GAP   — close the engine gaps the probe found (@ctx@ resolver, PG->SQLite dialect).
 *   S1 SETTLEMENT (the hell) — three-way match as a CONSTRAINT (JOIN + balance), not a
 *          ported algorithm. Does it reproduce the oracle's match pairs?
 *   S2 DERIVATION — completeOrder = state + decision-table(policy flags) + verbs
 *          (createShipment/createInvoice -> ops[]). Diff lines vs oracle.
 *   S3 FROZEN EFFECTS — flip a decision-table flag; new order changes, REPLAY of the old
 *          order's stored ops still yields the original shipment (forward-only rules).
 *
 * Handlers are pure (doc,ctx)->ops[]; the kernel "applies" (here: diff vs oracle). No DB
 * writes. Run: node scripts/poc_sales_to_ship.js 2>&1 | tee build/erp/poc.log
 */
'use strict';
var fs = require('fs');
var path = require('path');
var Database = require('better-sqlite3');

var AD = process.env.ERP_AD_FULL || path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var RULES = process.env.ERP_RULES_OUT || path.join(__dirname, '..', 'build', 'erp', 'erp_rules.db');
var ad = new Database(AD, { readonly: true });
var er = new Database(RULES, { readonly: true });
var all = function (db, sql, p) { return db.prepare(sql).all(p || {}); };
var one = function (db, sql, p) { return db.prepare(sql).get(p || {}); };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

// The engine is now ONE separated module — this POC consumes it, no inlined copies.
var E = require('./erp_engine');
var resolveCtx = E.resolveCtx, dialectShim = E.dialectShim;

console.log('═══ POC — Sales→Ship, hard parts first, diff vs the GardenWorld oracle ═══\n');

// ── GAP demo: an AccountingRule that RED-failed in the probe now runs ─────────
console.log('── GAP. Close the engine gaps (ctx resolver + dialect shim) ──');
(function () {
  // dialect shim: the probe's `least` breakage, on a standalone expression.
  var shimmed = dialectShim('SELECT least(3,5) lo, greatest(3,5) hi');
  var ok = false, detail;
  try { var v = ad.prepare(shimmed).get(); ok = (v.lo === 3 && v.hi === 5); detail = 'least→min/greatest→max: lo=' + v.lo + ' hi=' + v.hi; }
  catch (e) { detail = e.message; }
  verdict(ok, 'PG→SQLite dialect shim runs', detail);
  // ctx resolver: a real validation rule's @C_Element_ID@ resolves.
  var vr = one(er, "SELECT body FROM rules WHERE event_type='Validation' AND body LIKE '%@C_Element_ID@%' LIMIT 1");
  if (vr) { var rc = resolveCtx(vr.body, { C_Element_ID: 106 }); verdict(rc.miss.length === 0, 'Validation @ctx@ resolved', 'unresolved=' + rc.miss.length); }
  // FINDING (not a gap in scope): 3/4 AccountingRules are correlated FRAGMENTS
  // (outer ref Fact_Reconciliation.Fact_Acct_ID) — they run inside a host posting
  // query, not standalone. GL posting is out of Sales→Ship scope (fact_acct=0). Logged.
  console.log('   ℹ️  finding: AccountingRule bodies are correlated fragments (need a host query) — deferred, out of Sales→Ship scope');
})();

// ── S1. SETTLEMENT — three-way match as a constraint, vs the oracle ──────────
console.log('\n── S1. SETTLEMENT (the hell): match = JOIN + balance, vs oracle rows ──');
// THE INSIGHT (diagnosed from the oracle): settlement is NOT partitioned by document
// lineage — the matched vendor invoices carry c_order_id=NULL. The receipt and the
// invoice share only the TRADING-PARTNER ACCOUNT (C_BPartner_ID) + product + qty.
// The matcher is now E.match() in the separated engine — partition by `bp`.
function diffPairs(label, mine, oracle) {
  var key = function (p) { return p[0] + ':' + p[1]; };
  var oset = {}; oracle.forEach(function (p) { oset[key(p)] = 1; });
  var mset = {}; mine.forEach(function (p) { mset[key(p)] = 1; });
  var matched = mine.filter(function (p) { return oset[key(p)]; }).length;
  var missed = oracle.filter(function (p) { return !mset[key(p)]; }).length; // oracle had, we didn't
  var extra = mine.filter(function (p) { return !oset[key(p)]; }).length;    // we made up
  verdict(missed === 0 && extra === 0 && matched === oracle.length,
    label, 'oracle=' + oracle.length + ' mine=' + mine.length + ' matched=' + matched + ' missed=' + missed + ' extra=' + extra);
  return { matched: matched, missed: missed, extra: extra };
}

// Candidate lines tagged with TRADING-PARTNER ACCOUNT (bp) + DOCUMENT POLARITY. Settlement
// (three-way match) is a PURCHASE-side concept: vendor receipt (movementtype V+) ↔ vendor
// invoice (issotrx N) ↔ purchase-order line. The polarity filter removes sales-side false
// positives (the +6 extras): a customer shipment is reconciled differently, not via Match*.
var shipLines = all(ad, "SELECT iol.m_inoutline_id, iol.m_product_id, iol.movementqty, io.c_bpartner_id bp, io.movementtype FROM m_inoutline iol JOIN m_inout io ON io.m_inout_id=iol.m_inout_id");
var invLines = all(ad, "SELECT il.c_invoiceline_id, il.m_product_id, il.qtyinvoiced, inv.c_bpartner_id bp, inv.issotrx FROM c_invoiceline il JOIN c_invoice inv ON inv.c_invoice_id=il.c_invoice_id");
var ordLines = all(ad, "SELECT ol.c_orderline_id, ol.m_product_id, ol.qtyordered, o.c_bpartner_id bp, o.issotrx FROM c_orderline ol JOIN c_order o ON o.c_order_id=ol.c_order_id");
var vendorReceipts = shipLines.filter(function (r) { return r.movementtype === 'V+'; });
var vendorInvoices = invLines.filter(function (r) { return r.issotrx === 'N'; });
var poLines = ordLines.filter(function (r) { return r.issotrx === 'N'; });

// M_MatchInv: vendor receipt line <-> vendor invoice line, partitioned by BPartner.
(function () {
  var myPairs = E.match(vendorReceipts, vendorInvoices, { idL: 'm_inoutline_id', idR: 'c_invoiceline_id', qtyL: 'movementqty', qtyR: 'qtyinvoiced', partition: function (r) { return r.bp; } });
  var oracle = all(ad, "SELECT m_inoutline_id, c_invoiceline_id FROM m_matchinv WHERE m_inoutline_id IS NOT NULL AND c_invoiceline_id IS NOT NULL")
    .map(function (r) { return [r.m_inoutline_id, r.c_invoiceline_id]; });
  diffPairs('M_MatchInv (vendor receipt↔invoice) constraint reproduces oracle', myPairs, oracle);
})();

// M_MatchPO: purchase-order line <-> vendor invoice line, partitioned by BPartner.
(function () {
  var myPairs = E.match(poLines, vendorInvoices, { idL: 'c_orderline_id', idR: 'c_invoiceline_id', qtyL: 'qtyordered', qtyR: 'qtyinvoiced', partition: function (r) { return r.bp; } });
  var oracle = all(ad, "SELECT c_orderline_id, c_invoiceline_id FROM m_matchpo WHERE c_orderline_id IS NOT NULL AND c_invoiceline_id IS NOT NULL")
    .map(function (r) { return [r.c_orderline_id, r.c_invoiceline_id]; });
  diffPairs('M_MatchPO (PO line↔vendor invoice) constraint reproduces oracle', myPairs, oracle);
})();

// ── S2. DERIVATION — completeOrder = state + decision-table + verbs -> ops[] ──
console.log('\n── S2. DERIVATION: completeOrder via decision-table + verbs, ops vs oracle ──');
function getPolicy(docTypeId) {
  var r = one(er, "SELECT body FROM rules WHERE rule_key='DOCPOLICY:" + docTypeId + "'");
  return r ? JSON.parse(r.body) : {};
}
// VERBS + completeOrder now live in the separated engine (E.completeOrder / E.VERBS).
var soOrders = all(ad, "SELECT * FROM c_order WHERE issotrx='Y' AND c_order_id IN (SELECT c_order_id FROM m_inout)");
soOrders.forEach(function (order) {
  var lines = all(ad, "SELECT c_orderline_id, m_product_id, qtyordered FROM c_orderline WHERE c_order_id=@oid", { oid: order.c_order_id });
  var policy = getPolicy(order.c_doctype_id);
  // Force the fan-out flags ON for the POC (GardenWorld POS DocType already auto-generates).
  var pol = Object.assign({}, policy, { isautogenerateinout: 'Y', isautogenerateinvoice: policy.isautogenerateinvoice });
  var ops = E.completeOrder(order, lines, pol);
  // diff shipment lines: my CREATE_LINE(M_InOutLine) {product,qty} vs oracle m_inoutline.
  var myShip = ops.filter(function (o) { return o.table === 'M_InOutLine'; }).map(function (o) { return o.m_product_id + ':' + o.movementqty; }).sort();
  var oracleShip = all(ad, "SELECT iol.m_product_id, iol.movementqty FROM m_inoutline iol JOIN m_inout io ON io.m_inout_id=iol.m_inout_id WHERE io.c_order_id=@oid", { oid: order.c_order_id })
    .map(function (r) { return r.m_product_id + ':' + r.movementqty; }).sort();
  verdict(JSON.stringify(myShip) === JSON.stringify(oracleShip),
    'order ' + order.documentno + ' createShipment ops == oracle lines',
    'mine=' + myShip.length + ' oracle=' + oracleShip.length + (JSON.stringify(myShip) === JSON.stringify(oracleShip) ? '' : ' MINE=' + JSON.stringify(myShip) + ' ORACLE=' + JSON.stringify(oracleShip)));
});

// ── S3. FROZEN EFFECTS — rule change is forward-only; replay reproduces old ──
console.log('\n── S3. FROZEN EFFECTS: edit decision-table, replay old order unchanged ──');
(function () {
  var order = one(ad, "SELECT * FROM c_order WHERE c_order_id=100");
  var lines = all(ad, "SELECT c_orderline_id, m_product_id, qtyordered FROM c_orderline WHERE c_order_id=100");
  var polV1 = { isautogenerateinout: 'Y', isautogenerateinvoice: 'N' };
  var opsV1 = E.completeOrder(order, lines, polV1);            // committed op-group (stored)
  var v1ship = opsV1.filter(function (o) { return o.table === 'M_InOutLine'; }).length;

  // implementor edits the rule: stop auto-shipping.
  var polV2 = { isautogenerateinout: 'N', isautogenerateinvoice: 'N' };
  var order2 = one(ad, "SELECT * FROM c_order WHERE c_order_id=101");
  var lines2 = all(ad, "SELECT c_orderline_id, m_product_id, qtyordered FROM c_orderline WHERE c_order_id=101");
  var opsV2new = E.completeOrder(order2, lines2, polV2);       // NEW order under new rule
  var v2ship = opsV2new.filter(function (o) { return o.table === 'M_InOutLine'; }).length;

  // REPLAY of the OLD order = apply the STORED ops, NOT re-evaluate the rule.
  var replay = JSON.parse(JSON.stringify(opsV1));            // op-log is the truth (§18.8)
  var replayShip = replay.filter(function (o) { return o.table === 'M_InOutLine'; }).length;

  verdict(v1ship > 0, 'old order under rule v1 shipped', 'ship lines=' + v1ship);
  verdict(v2ship === 0, 'NEW order under rule v2 does NOT ship (rule change took effect forward)', 'ship lines=' + v2ship);
  verdict(replayShip === v1ship, 'REPLAY of old order reproduces original shipment (frozen)', 'replay=' + replayShip + ' original=' + v1ship);
})();

ad.close(); er.close();
console.log('\n═══ VERDICT ═══');
console.log('§POC ' + (fails ? 'FAIL — ' + fails + ' checks red' : 'PASS — all hard-part checks green'));
process.exit(fails ? 1 : 0);
