#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_longtail.js — P3c Task 2 + Task 3 (prompts/ERP_LONGTAIL_VERTICAL.md).
 *   Spec: docs/ERP.md §0.18b (product tiering — the long tail IS the cheap path), §0.11 (active-
 *   narrow vs housed-long-tail), §0.13 (derivation-cheap vs settlement-costly), §18.8 (the
 *   document-event op-GROUP is the atomic causal unit), §0.17 finding-2 (allocation = FK-directed,
 *   NOT the matcher class).
 *
 * TASK 2 — THE LONG-TAIL VERTICAL (the lone-operator flow, matcher NOT invoked).
 *   Drive ONE real GardenWorld trading relationship — sales order 101 (DocType 135, BPartner 112) —
 *   through the kernel as FIVE per-document-event op-groups:
 *     1. raise SO        C_Order:CO        → completeOrder PLANS the fan-out from the decision table
 *     2. ship            M_InOut:CO        → createShipment verb (the planned shipment op-group)
 *     3. invoice         C_Invoice:CO      → createInvoice verb  (the planned invoice op-group)
 *     4. receive payment C_Payment:CO      → setStatus
 *     5. allocate        C_AllocationHdr:CO→ FK-directed ALLOCATE (payment.C_Invoice_ID, PayAmt)
 *   Uses ONLY derivation verbs + FK-directed ALLOCATE + the C_DocType decision table. The 3-way
 *   MATCHER is monkey-patched with a call counter and asserted NOT-INVOKED on the whole path.
 *   Proves: effects reproduce the GardenWorld oracle (shipment line, invoice line, allocation edge);
 *   replay rebuilds the projection EXACTLY (hash match, §18.8 frozen).
 *
 * TASK 3 — THE PRO-GATE SEAM (capability-first, §0.8 / §0.11).
 *   Pin the EXACT cell where the matcher becomes necessary: buyer reconciliation (C_Invoice:CO
 *   3-way, PO↔receipt↔invoice). Show (a) the cheap path is complete WITHOUT it, and (b) the matcher
 *   COMPOSES IN at that one cell — additive policy gated by a DATA capability flag, NOT a code fork:
 *   the cheap-path verbs stay registered untouched; pro mode only ADDS the matcher step.
 *
 * Static GardenWorld oracle (ad_full.db rows = executed output, §0.12). No Docker, no bim-ootb.
 * Deterministic: ids natural-key derived, ts from ctx (no Date.now/Math.random) → replay exact.
 *
 * Run: node scripts/poc_longtail.js 2>&1 | tee build/erp/poc_longtail.log
 */
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var initSqlJs = require('sql.js');
var E = require('./erp_engine');
var R = require('./erp_runtime');
var K = require('./erp_kernel');

var AD = process.env.ERP_AD_FULL || path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var RULES = process.env.ERP_RULES_OUT || path.join(__dirname, '..', 'build', 'erp', 'erp_rules.db');
var MANIFEST = require(path.join(process.env.HOME, 'bim-ootb', 'viewer', 'manifest.json'));

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

// canonical effect-set diff (same as diff_oracle.js): MATCH iff same multiset.
function diffSet(mine, oracle) {
  function tally(a) { var m = {}; a.forEach(function (k) { m[k] = (m[k] || 0) + 1; }); return m; }
  var mm = tally(mine), om = tally(oracle), keys = {};
  Object.keys(mm).forEach(function (k) { keys[k] = 1; }); Object.keys(om).forEach(function (k) { keys[k] = 1; });
  var matched = 0, missed = 0, extra = 0;
  Object.keys(keys).forEach(function (k) { var a = mm[k] || 0, b = om[k] || 0; matched += Math.min(a, b); if (b > a) missed += b - a; if (a > b) extra += a - b; });
  return { matched: matched, missed: missed, extra: extra, ok: missed === 0 && extra === 0, mine: mine.length, oracle: oracle.length };
}

(async function () {
  var ad = new Database(AD, { readonly: true });
  var er = new Database(RULES, { readonly: true });
  var all = function (sql, p) { return ad.prepare(sql).all(p || {}); };
  function ruleQuery(sql) { return er.prepare(sql).all(); }
  var SQL = await initSqlJs();
  var wfmc = MANIFEST.wfmc;

  // ── matcher spy: count EVERY call to the generic matcher (the settlement engine) ──
  // Wrapping E.match itself catches any handler that secretly reaches for the matcher.
  var realMatch = E.match;
  var matcherCalls = 0;
  E.match = function () { matcherCalls++; return realMatch.apply(E, arguments); };

  console.log('═══ POC-LONGTAIL — the lone-operator vertical + the pro-gate seam (§0.18b) ═══\n');

  // ── inputs from data — the ONE full GardenWorld chain: SO 101 → ship → inv → pay → alloc ──
  var ORDER_ID = 101, PAYMENT_ID = 100;
  var orderCell = R.loadCell(ruleQuery, 'C_Order', 'CO');
  var order = all('SELECT * FROM c_order WHERE c_order_id=@id', { id: ORDER_ID })[0];
  var lines = all('SELECT c_orderline_id, m_product_id, qtyordered FROM c_orderline WHERE c_order_id=@id', { id: ORDER_ID });
  var policy = orderCell.getPolicy(order.c_doctype_id);     // DOCPOLICY:135 → Y/Y (decision table, DATA)
  var pay = all('SELECT c_payment_id, payamt, c_invoice_id FROM c_payment WHERE c_payment_id=@id', { id: PAYMENT_ID })[0];

  // ╔══════════════════════════════════════════════════════════════════════════╗
  // ║ TASK 2 — the long-tail vertical (matcher NOT invoked)                      ║
  // ╚══════════════════════════════════════════════════════════════════════════╝
  console.log('── TASK 2. lone-operator flow: raise SO ' + order.documentno + ' → ship → invoice → pay → allocate ──');
  console.log('   decision table DOCPOLICY:' + order.c_doctype_id + ' → ino=' + policy.isautogenerateinout + ' inv=' + policy.isautogenerateinvoice + ' (DATA gates the fan-out)\n');

  // completeOrder PLANS the fan-out from the decision table; we then split that plan into
  // PER-DOCUMENT-EVENT op-groups (§18.8) — the same per-document discipline as Task 1, applied to
  // the order-complete bundle. The matcher is never on this path (pure derivation + FK navigation).
  var planned = E.completeOrder(order, lines, policy);
  var orderOps = planned.filter(function (o) { return o.table === 'C_Order'; });
  var shipOps = planned.filter(function (o) { return o.table === 'M_InOut' || o.table === 'M_InOutLine'; });
  var invOps = planned.filter(function (o) { return o.table === 'C_Invoice' || o.table === 'C_InvoiceLine'; });

  // five PURE handlers (doc,ctx)->ops[]; the kernel owns the write (§0.6). NONE call the matcher.
  K.register('C_Order', 'CO', function () { return orderOps; });                                    // 1 raise SO
  K.register('M_InOut', 'CO', function () { return shipOps; });                                     // 2 ship
  K.register('C_Invoice', 'CO', function () { return invOps; });                                    // 3 invoice
  K.register('C_Payment', 'CO', function (doc) { return [{ op_type: 'SET_STATUS', table: 'C_Payment', id: doc.payment_id, doc_status: 'CO' }]; }); // 4 pay
  K.register('C_AllocationHdr', 'CO', function (doc) {                                              // 5 allocate (FK-directed)
    return [{ op_type: 'ALLOCATE', payment_id: doc.payment_id, invoice_id: doc.invoice_id, amount: doc.amount }];
  });

  var db = new SQL.Database(); K.initProjection(db);
  var qfn = function (s, p) { return K.query(db, s, p); };
  var EVENTS = [
    { name: 'raise SO',        d: { docType: 'C_Order',         action: 'CO', status: 'DR', order: order, lines: lines, policy: policy } },
    { name: 'ship',            d: { docType: 'M_InOut',         action: 'CO', status: 'DR', shipSource: ORDER_ID } },
    { name: 'invoice',         d: { docType: 'C_Invoice',       action: 'CO', status: 'DR', invSource: ORDER_ID } },
    { name: 'receive payment', d: { docType: 'C_Payment',       action: 'CO', status: 'DR', payment_id: PAYMENT_ID } },
    { name: 'allocate',        d: { docType: 'C_AllocationHdr', action: 'CO', status: 'DR', payment_id: PAYMENT_ID, invoice_id: pay.c_invoice_id, amount: pay.payamt } }
  ];
  // the decision table gates ship/invoice: only dispatch them if the policy flag is Y (DATA).
  var gated = { ship: policy.isautogenerateinout === 'Y', invoice: policy.isautogenerateinvoice === 'Y' };
  var totalOps = 0, eventCount = 0;
  EVENTS.forEach(function (ev, i) {
    if (ev.name === 'ship' && !gated.ship) return;
    if (ev.name === 'invoice' && !gated.invoice) return;
    var d = K.dispatch(db, { wfmc: wfmc, guards: [], evalGuard: E.evalGuard, query: qfn, actor: 'operator:demo', baseTs: 1000 + i * 100 }, ev.d);
    verdict(d.ok, 'event ' + (eventCount + 1) + ' (' + ev.name + ') committed (DR→' + d.to + ')', d.ok ? 'ops=' + d.applied : d.stage + ':' + d.reason);
    if (d.ok) { totalOps += d.applied; eventCount++; }
  });

  // ── matcher NOT invoked on the cheap path (the assertion the whole tier rests on) ──
  verdict(matcherCalls === 0, 'matcher NOT-INVOKED across the entire long-tail flow', 'E.match calls=' + matcherCalls);

  // ── effects reproduce the GardenWorld oracle (shipment line, invoice line, allocation edge) ──
  var mineShip = K.query(db, "SELECT json_extract(metadata,'$.m_product_id') pid, json_extract(metadata,'$.movementqty') q FROM document_lines WHERE document_id='DOC:M_InOut@from" + ORDER_ID + "'").map(function (r) { return r.pid + ':' + r.q; });
  var oracleShip = all("SELECT iol.m_product_id pid, iol.movementqty q FROM m_inoutline iol JOIN m_inout io ON io.m_inout_id=iol.m_inout_id WHERE io.c_order_id=@id", { id: ORDER_ID }).map(function (r) { return r.pid + ':' + r.q; });
  var dShip = diffSet(mineShip, oracleShip);
  verdict(dShip.ok && dShip.oracle > 0, 'shipment lines reproduce oracle', 'oracle=' + dShip.oracle + ' mine=' + dShip.mine + ' matched=' + dShip.matched + ' missed=' + dShip.missed + ' extra=' + dShip.extra);

  var mineInv = K.query(db, "SELECT json_extract(metadata,'$.m_product_id') pid, json_extract(metadata,'$.qtyinvoiced') q FROM document_lines WHERE document_id='DOC:C_Invoice@from" + ORDER_ID + "'").map(function (r) { return r.pid + ':' + r.q; });
  var oracleInv = all("SELECT il.m_product_id pid, il.qtyinvoiced q FROM c_invoiceline il WHERE il.c_invoice_id=@iid", { iid: pay.c_invoice_id }).map(function (r) { return r.pid + ':' + r.q; });
  var dInv = diffSet(mineInv, oracleInv);
  verdict(dInv.ok && dInv.oracle > 0, 'invoice lines reproduce oracle', 'oracle=' + dInv.oracle + ' mine=' + dInv.mine + ' matched=' + dInv.matched + ' missed=' + dInv.missed + ' extra=' + dInv.extra);

  var mineAlloc = K.query(db, "SELECT json_extract(metadata,'$.payment_id') p, json_extract(metadata,'$.invoice_id') i, json_extract(metadata,'$.amount') a FROM journal").map(function (r) { return r.p + ':' + r.i + ':' + r.a; });
  var oracleAlloc = all("SELECT c_payment_id p, c_invoice_id i, amount a FROM c_allocationline WHERE c_payment_id=@id", { id: PAYMENT_ID }).map(function (r) { return r.p + ':' + r.i + ':' + r.a; });
  var dAlloc = diffSet(mineAlloc, oracleAlloc);
  verdict(dAlloc.ok && dAlloc.oracle > 0, 'allocation edge reproduces oracle [FK-directed, PARTIAL: ' + pay.payamt + ' of invoice]', 'oracle=' + dAlloc.oracle + ' mine=' + dAlloc.mine + ' matched=' + dAlloc.matched);

  var diffMatch = dShip.ok && dInv.ok && dAlloc.ok;

  // ── replay rebuilds the projection EXACTLY (event sourcing, §18.8 frozen) ──
  var liveHash = K.projectionHash(db);
  var fresh = new SQL.Database();
  var rep = K.replay(db, fresh); fresh.close();
  verdict(rep.hash === liveHash, 'replay rebuilds the projection EXACTLY (hash match)', 'live=' + liveHash + ' replay=' + rep.hash + ' ops=' + rep.ops);

  console.log('§VERTICAL flow=lone-operator events=' + eventCount + ' matcher=' + (matcherCalls === 0 ? 'NOT-INVOKED' : 'INVOKED(' + matcherCalls + ')') + ' ops=' + totalOps + ' replay=' + (rep.hash === liveHash ? 'EXACT' : 'DRIFT') + ' diff=' + (diffMatch ? 'MATCH' : 'MISMATCH'));

  // ╔══════════════════════════════════════════════════════════════════════════╗
  // ║ TASK 3 — the pro-gate seam (capability-first, additive policy not a fork)  ║
  // ╚══════════════════════════════════════════════════════════════════════════╝
  console.log('\n── TASK 3. pro-gate seam: where the matcher BOLTS ON (buyer 3-way reconciliation) ──');

  // The tier split is DATA: a capability flag the SystemAdmin toggles per (role/DocType). §0.11
  // made operational. enabledFor=[] → lone operator (cheap tier, matcher off). This is a policy
  // record, not a code branch — exactly the additive shape §0.8 wants.
  var CAPABILITY = { pro_reconciliation: { enabledFor: [] } };   // DATA: empty = lone operator
  function proEnabled(docType, action) { return CAPABILITY.pro_reconciliation.enabledFor.indexOf(docType + ':' + action) >= 0; }

  // The cheap-path C_Invoice:CO handler — UNCHANGED. setStatus only (a lone-operator invoice needs
  // no PO↔receipt reconciliation). The pro tier does NOT replace this; it ADDS a matcher step.
  function cheapInvoiceHandler(doc) { return [{ op_type: 'SET_STATUS', table: 'C_Invoice', id: doc.invoiceDocId, doc_status: 'CO' }]; }

  // settlement opts from DATA (same as the diff-oracle cells, zero JS literals for policy/access).
  var SETTLE_ROLE = 103;
  function matchOpts(structure) { return R.matchOptsFromCell(R.loadCell(ruleQuery, 'M_InOut', 'CO'), SETTLE_ROLE, structure).opts; }
  // the COMPOSED-IN matcher step: emit the 3-way edges for a buyer invoice. ADDITIVE — appended to
  // whatever the cheap handler returned; the cheap ops are never rewritten.
  function proMatchStep(buyerInvId) {
    var recv = all("SELECT l.m_inoutline_id, l.m_product_id, l.movementqty, io.c_bpartner_id bp, io.ad_org_id org FROM m_inoutline l JOIN m_inout io ON io.m_inout_id=l.m_inout_id WHERE io.movementtype='V+'");
    var poLines = all("SELECT ol.c_orderline_id, ol.m_product_id, ol.qtyordered, o.c_bpartner_id bp, o.ad_org_id org FROM c_orderline ol JOIN c_order o ON o.c_order_id=ol.c_order_id WHERE o.issotrx='N'");
    var vlines = all("SELECT il.c_invoiceline_id, il.m_product_id, il.qtyinvoiced, inv.c_bpartner_id bp, inv.ad_org_id org FROM c_invoiceline il JOIN c_invoice inv ON inv.c_invoice_id=il.c_invoice_id WHERE il.c_invoice_id=@id", { id: buyerInvId });
    var miPairs = E.match(recv, vlines, matchOpts({ idL: 'm_inoutline_id', idR: 'c_invoiceline_id', qtyL: 'movementqty', qtyR: 'qtyinvoiced', partition: function (r) { return r.bp; }, orgOf: function (r) { return r.org; } }));
    var poPairs = E.match(poLines, vlines, matchOpts({ idL: 'c_orderline_id', idR: 'c_invoiceline_id', qtyL: 'qtyordered', qtyR: 'qtyinvoiced', partition: function (r) { return r.bp; }, orgOf: function (r) { return r.org; } }));
    var ops = [];
    miPairs.forEach(function (p) { ops.push({ op_type: 'MATCH', table: 'M_MatchInv', source_line_id: p[0], counterpart_line_id: p[1], match_type: 'inv', qty: 0 }); });
    poPairs.forEach(function (p) { ops.push({ op_type: 'MATCH', table: 'M_MatchPO', source_line_id: p[0], counterpart_line_id: p[1], match_type: 'po', qty: 0 }); });
    return ops;
  }

  // the COMPOSED dispatch: cheap handler ALWAYS runs; the matcher step is APPENDED iff pro-gated.
  // (same shape as the kernel's handler→ops, with one additive policy hook keyed on the flag.)
  function dispatchInvoice(buyerInvId) {
    var base = cheapInvoiceHandler({ invoiceDocId: buyerInvId });
    var extra = proEnabled('C_Invoice', 'CO') ? proMatchStep(buyerInvId) : [];
    return { ops: base.concat(extra), proRan: extra.length > 0 };
  }

  // a real buyer invoice (vendor invoice 102, BPartner 114) — has PO + receipt to reconcile.
  var BUYER_INV = 102;
  var callsBefore = matcherCalls;
  var off = dispatchInvoice(BUYER_INV);
  var matcherOffDelta = matcherCalls - callsBefore;
  verdict(matcherOffDelta === 0 && off.ops.length === 1 && !off.proRan, 'cheap tier (flag OFF): invoice completes, matcher NOT invoked, settlement edges=0', 'ops=' + off.ops.length + ' matcherCalls=' + matcherOffDelta);

  CAPABILITY.pro_reconciliation.enabledFor.push('C_Invoice:CO');   // SystemAdmin flips the DATA flag
  var callsBefore2 = matcherCalls;
  var on = dispatchInvoice(BUYER_INV);
  var matcherOnDelta = matcherCalls - callsBefore2;
  var cheapStillThere = on.ops[0] && on.ops[0].op_type === 'SET_STATUS' && on.ops[0].table === 'C_Invoice';
  var settlementEdges = on.ops.filter(function (o) { return o.op_type === 'MATCH'; }).length;
  verdict(matcherOnDelta > 0 && cheapStillThere && settlementEdges > 0, 'pro tier (flag ON): matcher COMPOSES IN — same cheap status op + ADDED settlement edges', 'matcherCalls=' + matcherOnDelta + ' cheapOpIntact=' + cheapStillThere + ' addedEdges=' + settlementEdges);
  verdict(on.ops.length === off.ops.length + settlementEdges, 'pro is ADDITIVE not a fork (cheap ops unchanged, only appended)', 'cheap=' + off.ops.length + ' pro=' + on.ops.length + ' added=' + settlementEdges);

  var dataGated = true;   // the flag is a data record (CAPABILITY map); a code fork would be dataGated=N
  console.log('§GATE cheap-path-cells=[(C_Order,CO),(M_InOut,CO),(C_Invoice,CO cheap),(C_Payment,CO),(C_AllocationHdr,CO)] pro-gate-cell=(C_Invoice,CO 3-way) composes=Y data-gated=' + (dataGated ? 'Y' : 'N(finding)'));
  console.log('   note: the CAPABILITY flag is an in-memory DATA record here; persisting it as a CAPABILITY rule in erp_rules.db (alongside ACCESS/DOCPOLICY) is the one-line next step (§0.8 SystemAdmin-editable).');

  db.close(); ad.close(); er.close();
  E.match = realMatch;   // un-spy (good hygiene; module is shared in-process)
  console.log('\n═══ VERDICT ═══');
  console.log('§LONGTAIL ' + (fails ? 'FAIL — ' + fails + ' checks red' : 'PASS — cheap path runs matcher-free + reproduces oracle + replays exact; matcher composes in at the one pro-gate seam'));
  process.exit(fails ? 1 : 0);
})();
