// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
'use strict';
/**
 * diff_oracle_cells.js — the HOT-CELL registry the diff-oracle harness iterates.
 *   Spec: prompts/ERP_KERNEL_BUILD.md §P3b (gravity order §18.2; one cell at a time, EXTRACT
 *   from the Java oracle, never invent), docs/ERP.md §18.10 (the oracle), §0.13 (two spines:
 *   derivation-cheap / settlement-costly).
 *
 * Each cell: { docType, action, oracle (Java citation), run(env) -> {groups, verbs, matcher, ops} }.
 *   env = { ad, er, all, ruleQuery, E, R, K, manifest, freshDb(), diffSet }.
 *   group = { name, mine:[keyStr], oracle:[keyStr] } — the harness diffs the two effect sets.
 *   A cell that needs a genuinely NEW bespoke verb sets `newVerb:'reason'` (a §18.6 finding).
 *
 * Build order = gravity, hot-cell first (§18.2 hub ranking C_Order#1 → Invoice → Payment):
 *   [done] SOO:CO  C_Order sales  → createShipment (+createInvoice) — derivation spine.
 *   [P3b]  POO:CO  C_Order purch  → createReceipt + M_MatchPO        — settlement spine.
 *   [P3b]  MMR:CO  M_InOut receipt→ StorageOnHand + matchPO.
 *   [P3b]  API:CO  C_Invoice      → M_MatchInv + journal.
 *   [P3b]  APP:CO  C_Payment      → allocation + journal.
 *   [P3b]  GLJ:CO  GL_Journal     → posting (Fact_Acct).
 */

// ── helper: dispatch a document-event through the kernel, return {res, db} ───
// Every cell drives the SAME §18.6 ladder (state-machine → guards → handler → apply).
function dispatch(env, db, cellCtx, doc) {
  var base = {
    wfmc: env.manifest.wfmc, guards: cellCtx.guards || [], evalGuard: env.E.evalGuard,
    query: function (s, p) { return env.K.query(db, s, p); },
    // gravity (Task 3): when the harness sets env.cellTag, stamp it as the op's user_tag so the
    // shared op-log can be GROUP BY'd by (DocType,action) — the product's self-ranked backlog.
    actor: env.cellTag || cellCtx.actor || 'oracle:diff', baseTs: cellCtx.baseTs || 1000
  };
  return env.K.dispatch(db, base, doc);
}

// ── SOO:CO — sales-order Complete → shipment derivation (the proven baseline) ──
// Oracle: org.compiere.model.MOrder.completeIt() — on Complete, if the DocType auto-generates
// a shipment (isautogenerateinout) it creates an M_InOut + a line per ordered line (the
// QtyOrdered moves). Pure derivation: NO settlement edge, NO inventory-mutation op (the
// shipment lines ARE the inventory fact, §18.9). This reproduces §KERNEL/§WIRE derivation.
var SOO_CO = {
  docType: 'C_Order', action: 'CO',
  oracle: 'org.compiere.model.MOrder.completeIt() — isautogenerateinout fan-out',
  run: function (env) {
    var E = env.E, R = env.R, K = env.K;
    var cell = R.loadCell(env.ruleQuery, 'C_Order', 'CO');
    K.register('C_Order', 'CO', function (doc) { return E.completeOrder(doc.order, doc.lines, doc.policy); });

    // SO orders that GardenWorld shipped (issotrx=Y, has an M_InOut) — these are the oracle set.
    var orders = env.all("SELECT * FROM c_order WHERE issotrx='Y' AND c_order_id IN (SELECT c_order_id FROM m_inout)");
    var mine = [], oracle = [], totalOps = 0;
    orders.forEach(function (order) {
      var lines = env.all("SELECT c_orderline_id, m_product_id, qtyordered FROM c_orderline WHERE c_order_id=@oid", { oid: order.c_order_id });
      var policy = cell.getPolicy(order.c_doctype_id);          // real Y/Y flags from DOCPOLICY (data)
      var db = env.freshDb();
      var d = dispatch(env, db, { actor: 'user:demo', baseTs: 1000 },
        { docType: 'C_Order', action: 'CO', status: 'DR', order: order, lines: lines, policy: policy });
      if (!d.ok) throw new Error('dispatch (order ' + order.documentno + ') ' + d.stage + ':' + d.reason);
      totalOps += d.applied;
      // mine = the emitted effect-op group's shipment lines (§18.8 — compare the op-group).
      (d.ops || []).filter(function (o) { return o.table === 'M_InOutLine'; })
        .forEach(function (o) { mine.push(order.c_order_id + ':' + o.m_product_id + ':' + o.movementqty); });
      env.closeDb(db);
    });
    // oracle = GardenWorld's actual M_InOutLine rows, namespaced by the source order.
    orders.forEach(function (order) {
      env.all("SELECT iol.m_product_id pid, iol.movementqty q FROM m_inoutline iol JOIN m_inout io ON io.m_inout_id=iol.m_inout_id WHERE io.c_order_id=@oid", { oid: order.c_order_id })
        .forEach(function (r) { oracle.push(order.c_order_id + ':' + r.pid + ':' + r.q); });
    });
    return {
      groups: [{ name: 'createShipment ops == oracle M_InOutLine (per source order)', mine: mine, oracle: oracle }],
      verbs: ['createShipment'], matcher: false, ops: totalOps
    };
  }
};

// ── shared settlement plumbing: data-driven matcher opts (zero JS literals) ──
// ordering ← MATCHPOLICY:M_InOut:CO (FIFO default, editable), access ← ACCESS:<role> (§0.8).
// idL/idR/qtyL/qtyR/partition/orgOf are STRUCTURE (which columns hold identities) — the caller
// supplies them; ordering + allowOrgs are POLICY/ACCESS and come from the cell (data). §0.14.
var SETTLE_ROLE = 103;   // GardenWorld User — org list incl. org 11 (the settlement data org)
function matcherOpts(env, structure) {
  var cell = env.R.loadCell(env.ruleQuery, 'M_InOut', 'CO');
  return env.R.matchOptsFromCell(cell, SETTLE_ROLE, structure).opts;
}

// ── POO:CO — purchase-order Complete (the P2P entry) ─────────────────────────
// Oracle: org.compiere.model.MOrder.completeIt() — SAME method as the sales side; the
// shipment/receipt fan-out is gated by the DocType flag isautogenerateinout. FINDING (extracted,
// not assumed): GardenWorld's purchase DocType 126 has isautogenerateinout=N, so completing a PO
// does NOT synchronously create a receipt — the M_InOut V+ is an INDEPENDENT document-event
// (handled by MMR:CO), and M_MatchPO is created at receipt/invoice completion, NOT at PO complete.
// So the prompt's "POO:CO → receipt + M_MatchPO" does not hold against the oracle; the decision
// table correctly suppresses the fan-out — the SAME completeOrder verb, no new verb.
var POO_CO = {
  docType: 'C_Order', action: 'CO',
  oracle: 'org.compiere.model.MOrder.completeIt() — purchase side, isautogenerateinout gate',
  label: 'POO',
  run: function (env) {
    var E = env.E, R = env.R, K = env.K;
    var cell = R.loadCell(env.ruleQuery, 'C_Order', 'CO');
    K.register('C_Order', 'CO', function (doc) { return E.completeOrder(doc.order, doc.lines, doc.policy); });
    // purchase orders that GardenWorld received against (issotrx=N, has an M_InOut).
    var orders = env.all("SELECT * FROM c_order WHERE issotrx='N' AND c_order_id IN (SELECT c_order_id FROM m_inout)");
    var mine = [], totalOps = 0;
    orders.forEach(function (order) {
      var lines = env.all("SELECT c_orderline_id, m_product_id, qtyordered FROM c_orderline WHERE c_order_id=@oid", { oid: order.c_order_id });
      var policy = cell.getPolicy(order.c_doctype_id);     // DOCPOLICY:126 → isautogenerateinout=N (data)
      var db = env.freshDb();
      var d = dispatch(env, db, { actor: 'buyer:demo', baseTs: 1000 },
        { docType: 'C_Order', action: 'CO', status: 'DR', order: order, lines: lines, policy: policy });
      if (!d.ok) throw new Error('dispatch PO ' + order.documentno + ' ' + d.stage + ':' + d.reason);
      totalOps += d.applied;
      (d.ops || []).filter(function (o) { return o.table === 'M_InOutLine'; })
        .forEach(function (o) { mine.push(order.c_order_id + ':' + o.m_product_id + ':' + o.movementqty); });
      env.closeDb(db);
    });
    // ORACLE: synchronous PO-complete fan-out = [] (DocType 126 does not auto-generate the
    // receipt; the receipt is a separate MMR document — verified: each PO's M_InOut is its own row).
    return {
      groups: [{ name: 'PO-complete synchronous receipt fan-out == oracle (none — receipt is independent MMR)', mine: mine, oracle: [] }],
      verbs: ['completeOrder'], matcher: false, ops: totalOps,
      newVerb: null,
      finding: 'DOCPOLICY:126 isautogenerateinout=N — PO complete derives NO receipt; the M_InOut V+ is an independent MMR document-event, M_MatchPO created at receipt/invoice completion (not PO complete). Same completeOrder verb; decision table suppresses fan-out.'
    };
  }
};

// ── MMR:CO — vendor receipt Complete → M_MatchPO (po↔receipt) + StorageOnHand ──
// Oracle: org.compiere.model.MInOut.completeIt() — on Complete it (1) calls MStorageOnHand.add per
// line (the inventory move) and (2) creates M_MatchPO linking each receipt line to its PO line
// (receiptLine.C_OrderLine_ID). In OUR model the receipt LINES ARE the inventory fact (§18.9) — so
// NO storage-mutation op is emitted; StorageOnHand is a computed projection. The matchPO is the
// generic matcher (partition=BPartner, key=product, qty within tol) — the SAME E.match, no new verb.
// EXTRACT REFINEMENT: the matcher must be scoped to ORDER-LINKED receipts (io.c_order_id NOT NULL);
// receipts with no PO (102,104) coincidentally share partner+product+qty with a PO and otherwise
// produce 2 false positives the FK-following oracle never makes. The scoping is caller STRUCTURE
// (which rows are candidates), not a change to the matcher — the thesis holds.
// P3c TASK 1: dispatched PER RECEIPT-DOCUMENT (was one synthetic receiptDocId:'ALL' event). Each
// receipt matches ONLY its own lines within its partition; consumed PO lines are withheld from later
// receipts. Σ(per-document) == the universe count (19) is the isolation regression guard (§perdoc).
var MMR_CO = {
  docType: 'M_InOut', action: 'CO',
  oracle: 'org.compiere.model.MInOut.completeIt() — MStorageOnHand.add + MMatchPO.create(po↔receipt)',
  run: function (env) {
    var E = env.E, K = env.K;
    var poLines = env.all("SELECT ol.c_orderline_id, ol.m_product_id, ol.qtyordered, o.c_bpartner_id bp, o.ad_org_id org FROM c_orderline ol JOIN c_order o ON o.c_order_id=ol.c_order_id WHERE o.issotrx='N'");
    var opts = matcherOpts(env, { idL: 'c_orderline_id', idR: 'm_inoutline_id', qtyL: 'qtyordered', qtyR: 'movementqty', partition: function (r) { return r.bp; }, orgOf: function (r) { return r.org; } });

    // P3c Task 1 — PER-DOCUMENT dispatch (close the P3b universe-in-one-event shortcut, §0.18b).
    // UNIVERSE reference (the P3b behaviour) = match the WHOLE order-linked receipt pool at once.
    // This stays as the isolation regression guard: Σ(per-document) MUST equal it (0 drop, 0 extra).
    var recvAll = env.all("SELECT l.m_inoutline_id, l.m_product_id, l.movementqty, io.c_bpartner_id bp, io.ad_org_id org FROM m_inoutline l JOIN m_inout io ON io.m_inout_id=l.m_inout_id WHERE io.movementtype='V+' AND io.c_order_id IS NOT NULL");
    var universePairs = E.match(poLines, recvAll, opts).map(function (p) { return p[0] + ':' + p[1]; });

    // handler: PURE (doc,ctx)→ops. SET_STATUS + a MATCH op per pair. NO storage-mutation op.
    K.register('M_InOut', 'CO', function (doc) {
      var ops = [{ op_type: 'SET_STATUS', table: 'M_InOut', id: doc.receiptDocId, doc_status: 'CO' }];
      doc.pairs.forEach(function (p) { ops.push({ op_type: 'MATCH', table: 'M_MatchPO', source_line_id: p[0], counterpart_line_id: p[1], match_type: 'po', qty: 0 }); });
      return ops;
    });

    // each order-linked receipt completes INDEPENDENTLY: ITS own lines vs the partition-visible
    // PO lines, minus PO lines already consumed by an earlier receipt (no double-claim, §18.8).
    var receipts = env.all("SELECT DISTINCT io.m_inout_id, io.documentno FROM m_inoutline l JOIN m_inout io ON io.m_inout_id=l.m_inout_id WHERE io.movementtype='V+' AND io.c_order_id IS NOT NULL ORDER BY io.m_inout_id");
    var db = env.freshDb();
    var consumedPo = {}, mine = [], allOps = [], totalOps = 0;
    receipts.forEach(function (rc) {
      var rlines = env.all("SELECT l.m_inoutline_id, l.m_product_id, l.movementqty, io.c_bpartner_id bp, io.ad_org_id org FROM m_inoutline l JOIN m_inout io ON io.m_inout_id=l.m_inout_id WHERE l.m_inout_id=@id", { id: rc.m_inout_id });
      var poAvail = poLines.filter(function (pl) { return !consumedPo[pl.c_orderline_id]; });
      var pairs = E.match(poAvail, rlines, opts);     // ONE receipt's lines vs partition-visible PO lines
      pairs.forEach(function (p) { consumedPo[p[0]] = 1; });
      var d = dispatch(env, db, { actor: 'whse:demo', baseTs: 2000 + rc.m_inout_id },
        { docType: 'M_InOut', action: 'CO', status: 'DR', receiptDocId: rc.m_inout_id, pairs: pairs });
      if (!d.ok) throw new Error('dispatch MMR receipt ' + rc.m_inout_id + ' ' + d.stage + ':' + d.reason);
      totalOps += d.applied; allOps = allOps.concat(d.ops || []);
      (d.ops || []).filter(function (o) { return o.op_type === 'MATCH'; }).forEach(function (o) { mine.push(o.source_line_id + ':' + o.counterpart_line_id); });
    });
    env.closeDb(db);
    // §18.9 invariant: NO inventory-mutation op anywhere across the per-document run.
    var mutationOps = allOps.filter(function (o) { return /STORAGE|DEDUCT|INVENTORY/.test(o.op_type); });
    var oracle = env.all("SELECT c_orderline_id, m_inoutline_id FROM m_matchpo WHERE c_orderline_id IS NOT NULL AND m_inoutline_id IS NOT NULL AND c_invoiceline_id IS NULL").map(function (r) { return r.c_orderline_id + ':' + r.m_inoutline_id; });
    var iso = env.diffSet(mine, universePairs);   // per-document == universe?  (the isolation guard)
    return {
      groups: [
        { name: 'M_MatchPO (po↔receipt) reproduces oracle', mine: mine, oracle: oracle },
        { name: 'NO inventory-mutation op (storage is a computed projection, §18.9)', mine: mutationOps.map(function (o) { return o.op_type; }), oracle: [] }
      ],
      verbs: ['setStatus'], matcher: true, ops: totalOps,
      perdoc: { docs: receipts.length, checks: [{ label: 'M_MatchPO', matched: mine.length, universe: universePairs.length, ok: iso.ok }] }
    };
  }
};

// ── API:CO — vendor invoice Complete → M_MatchInv + M_MatchPO (the settlement + GL join) ──
// Oracle: org.compiere.model.MInvoice.completeIt() (lines 2077-2190) — for each invoice line, if
// !isSOTrx && M_InOutLine_ID!=0 → MMatchInv(line, receiptLine, min(movementQty,qtyInvoiced)); if
// C_OrderLine_ID!=0 → MMatchPO.create(line, orderLine). The oracle FOLLOWS the invoice line's own
// FKs. Our generic matcher RE-DERIVES the identical pairs from partition(BPartner)+product+qty —
// DOCUMENTED DIVERGENCE (§18.10): the matcher is the GENERATE form of iDempiere's FK navigation; it
// agrees on GardenWorld (18/18 both) and additionally works when the FK is absent (invoice-before-
// receipt). Journal posting (Fact_Acct) is ASYNC in iDempiere (§18.10) and UNPOSTED in this dataset
// (fact_acct=0) — that sub-effect is dataless (docker-fallback), logged, not faked.
// P3c TASK 1: dispatched PER INVOICE-DOCUMENT (was one synthetic invoiceDocId:'ALL' event). Each
// invoice reconciles ONLY its own lines within its partition; consumed receipt/PO lines are withheld
// from later invoices. Σ(per-document) == universe (18 + 18) is the isolation guard (§perdoc).
var API_CO = {
  docType: 'C_Invoice', action: 'CO',
  oracle: 'org.compiere.model.MInvoice.completeIt():2077-2190 — MMatchInv + MMatchPO',
  run: function (env) {
    var E = env.E, K = env.K;
    var recv = env.all("SELECT l.m_inoutline_id, l.m_product_id, l.movementqty, io.c_bpartner_id bp, io.ad_org_id org FROM m_inoutline l JOIN m_inout io ON io.m_inout_id=l.m_inout_id WHERE io.movementtype='V+'");
    var poLines = env.all("SELECT ol.c_orderline_id, ol.m_product_id, ol.qtyordered, o.c_bpartner_id bp, o.ad_org_id org FROM c_orderline ol JOIN c_order o ON o.c_order_id=ol.c_order_id WHERE o.issotrx='N'");
    var miOpts = matcherOpts(env, { idL: 'm_inoutline_id', idR: 'c_invoiceline_id', qtyL: 'movementqty', qtyR: 'qtyinvoiced', partition: function (r) { return r.bp; }, orgOf: function (r) { return r.org; } });
    var poOpts = matcherOpts(env, { idL: 'c_orderline_id', idR: 'c_invoiceline_id', qtyL: 'qtyordered', qtyR: 'qtyinvoiced', partition: function (r) { return r.bp; }, orgOf: function (r) { return r.org; } });

    // P3c Task 1 — UNIVERSE reference (P3b behaviour): match ALL vendor-invoice lines at once.
    // Σ(per-document) MUST equal this (0 drop, 0 extra) — the isolation regression guard.
    var vinvAll = env.all("SELECT il.c_invoiceline_id, il.m_product_id, il.qtyinvoiced, inv.c_bpartner_id bp, inv.ad_org_id org FROM c_invoiceline il JOIN c_invoice inv ON inv.c_invoice_id=il.c_invoice_id WHERE inv.issotrx='N'");
    var universeMI = E.match(recv, vinvAll, miOpts).map(function (p) { return p[0] + ':' + p[1]; });
    var universePO = E.match(poLines, vinvAll, poOpts).map(function (p) { return p[0] + ':' + p[1]; });

    K.register('C_Invoice', 'CO', function (doc) {
      var ops = [{ op_type: 'SET_STATUS', table: 'C_Invoice', id: doc.invoiceDocId, doc_status: 'CO' }];
      doc.miPairs.forEach(function (p) { ops.push({ op_type: 'MATCH', table: 'M_MatchInv', source_line_id: p[0], counterpart_line_id: p[1], match_type: 'inv', qty: 0 }); });
      doc.poPairs.forEach(function (p) { ops.push({ op_type: 'MATCH', table: 'M_MatchPO', source_line_id: p[0], counterpart_line_id: p[1], match_type: 'po', qty: 0 }); });
      return ops;
    });

    // each vendor invoice reconciles INDEPENDENTLY: ITS own lines vs the partition-visible receipt
    // and PO lines, minus counterpart lines already consumed by an earlier invoice (no double-claim).
    var invoices = env.all("SELECT DISTINCT inv.c_invoice_id FROM c_invoiceline il JOIN c_invoice inv ON inv.c_invoice_id=il.c_invoice_id WHERE inv.issotrx='N' ORDER BY inv.c_invoice_id");
    var db = env.freshDb();
    var consumedRecv = {}, consumedPo = {}, mineMI = [], minePO = [], totalOps = 0;
    invoices.forEach(function (iv) {
      var vlines = env.all("SELECT il.c_invoiceline_id, il.m_product_id, il.qtyinvoiced, inv.c_bpartner_id bp, inv.ad_org_id org FROM c_invoiceline il JOIN c_invoice inv ON inv.c_invoice_id=il.c_invoice_id WHERE il.c_invoice_id=@id", { id: iv.c_invoice_id });
      var recvAvail = recv.filter(function (r) { return !consumedRecv[r.m_inoutline_id]; });
      var poAvail = poLines.filter(function (pl) { return !consumedPo[pl.c_orderline_id]; });
      var miPairs = E.match(recvAvail, vlines, miOpts);   // this invoice ↔ partition receipts
      var poPairs = E.match(poAvail, vlines, poOpts);     // this invoice ↔ partition PO lines
      miPairs.forEach(function (p) { consumedRecv[p[0]] = 1; });
      poPairs.forEach(function (p) { consumedPo[p[0]] = 1; });
      var d = dispatch(env, db, { actor: 'ap:demo', baseTs: 3000 + iv.c_invoice_id },
        { docType: 'C_Invoice', action: 'CO', status: 'DR', invoiceDocId: iv.c_invoice_id, miPairs: miPairs, poPairs: poPairs });
      if (!d.ok) throw new Error('dispatch API invoice ' + iv.c_invoice_id + ' ' + d.stage + ':' + d.reason);
      totalOps += d.applied;
      (d.ops || []).filter(function (o) { return o.op_type === 'MATCH' && o.table === 'M_MatchInv'; }).forEach(function (o) { mineMI.push(o.source_line_id + ':' + o.counterpart_line_id); });
      (d.ops || []).filter(function (o) { return o.op_type === 'MATCH' && o.table === 'M_MatchPO'; }).forEach(function (o) { minePO.push(o.source_line_id + ':' + o.counterpart_line_id); });
    });
    env.closeDb(db);
    var oracleMI = env.all("SELECT m_inoutline_id, c_invoiceline_id FROM m_matchinv WHERE m_inoutline_id IS NOT NULL AND c_invoiceline_id IS NOT NULL").map(function (r) { return r.m_inoutline_id + ':' + r.c_invoiceline_id; });
    var oraclePO = env.all("SELECT c_orderline_id, c_invoiceline_id FROM m_matchpo WHERE c_orderline_id IS NOT NULL AND c_invoiceline_id IS NOT NULL").map(function (r) { return r.c_orderline_id + ':' + r.c_invoiceline_id; });
    var isoMI = env.diffSet(mineMI, universeMI), isoPO = env.diffSet(minePO, universePO);
    return {
      groups: [
        { name: 'M_MatchInv (receipt↔invoice) reproduces oracle [matcher re-derives the FK]', mine: mineMI, oracle: oracleMI },
        { name: 'M_MatchPO (PO↔invoice) reproduces oracle [matcher re-derives the FK]', mine: minePO, oracle: oraclePO }
      ],
      verbs: ['setStatus'], matcher: true, ops: totalOps,
      perdoc: { docs: invoices.length, checks: [
        { label: 'M_MatchInv', matched: mineMI.length, universe: universeMI.length, ok: isoMI.ok },
        { label: 'M_MatchPO', matched: minePO.length, universe: universePO.length, ok: isoPO.ok }
      ] },
      finding: 'Journal posting (Fact_Acct) is ASYNC in iDempiere and UNPOSTED in this dataset (fact_acct=0 rows) — the GL sub-effect of API:CO is dataless; static-oracle diff covers the two Match* effects, journal posting → docker-fallback.'
    };
  }
};

// ── APP:CO — payment Complete → allocation (close the P2P loop) ──────────────
// Oracle: org.compiere.model.MPayment.completeIt()/allocateIt() + MAllocationHdr/MAllocationLine —
// a payment is allocated to the invoice the user picked (C_Payment.C_Invoice_ID), at the applied
// amount (PayAmt), which may be PARTIAL of the invoice total. FINDING (refines §0.14, which listed
// allocation in "the matcher class"): allocation is NOT the quantity-exact 3-way matcher — payment
// 100 (PayAmt 98.5) settles invoice 101 (GrandTotal 100.7), a PARTIAL, USER-DIRECTED settlement.
// It is reproduced by FOLLOWING the payment's own C_Invoice_ID FK + the ALLOCATE verb (which the
// kernel already owns), making allocation a derivation-by-FK — CHEAPER than 3-way match, not harder.
// matcher=N: the qty-equality constraint does not apply (amounts are partial).
var APP_CO = {
  docType: 'C_Payment', action: 'CO',
  oracle: 'org.compiere.model.MPayment + MAllocationHdr/Line — user-directed C_Invoice_ID, PayAmt (partial)',
  run: function (env) {
    var K = env.K;
    var pays = env.all("SELECT c_payment_id, payamt, c_invoice_id FROM c_payment WHERE c_invoice_id IS NOT NULL");
    K.register('C_Payment', 'CO', function (doc) {
      var ops = [{ op_type: 'SET_STATUS', table: 'C_Payment', id: doc.payment_id, doc_status: 'CO' }];
      // follow the payment's own C_Invoice_ID FK (user-directed) — emit an ALLOCATE edge at PayAmt.
      ops.push({ op_type: 'ALLOCATE', payment_id: doc.payment_id, invoice_id: doc.invoice_id, amount: doc.amount });
      return ops;
    });
    var db = env.freshDb();
    var mine = [], totalOps = 0;
    pays.forEach(function (p) {
      var d = dispatch(env, db, { actor: 'ar:demo', baseTs: 4000 + p.c_payment_id },
        { docType: 'C_Payment', action: 'CO', status: 'DR', payment_id: p.c_payment_id, invoice_id: p.c_invoice_id, amount: p.payamt });
      if (!d.ok) throw new Error('dispatch APP ' + d.stage + ':' + d.reason);
      totalOps += d.applied;
      (d.ops || []).filter(function (o) { return o.op_type === 'ALLOCATE'; })
        .forEach(function (o) { mine.push(o.payment_id + ':' + o.invoice_id + ':' + o.amount); });
    });
    env.closeDb(db);
    // oracle: payment-driven allocation lines (C_Payment_ID NOT NULL). The NULL-payment line
    // (invoice 100, 50.35) is a non-payment allocation (cash/credit-memo) — a different doc-event.
    var oracle = env.all("SELECT c_payment_id, c_invoice_id, amount FROM c_allocationline WHERE c_payment_id IS NOT NULL").map(function (r) { return r.c_payment_id + ':' + r.c_invoice_id + ':' + r.amount; });
    return {
      groups: [{ name: 'ALLOCATE (payment↔invoice, PayAmt) reproduces oracle allocation [FK-directed, not qty-matched]', mine: mine, oracle: oracle }],
      verbs: ['allocate'], matcher: false, ops: totalOps,
      newVerb: 'allocation is partial-amount + user-directed (PayAmt 98.5 ≠ invoice GrandTotal 100.7) — the qty-exact 3-way matcher does NOT apply. Reproduced by FOLLOWING C_Payment.C_Invoice_ID + the existing ALLOCATE verb (derivation-by-FK). This REFINES §0.14: allocation is NOT the matcher class; it is cheaper (FK navigation), not part of the settlement-lattice "hell".'
    };
  }
};

// ── GLJ:CO — GL journal Complete → posting (the ledger) ──────────────────────
// Oracle: org.compiere.model.MJournal.completeIt() → Doc_GLJournal posts to Fact_Acct. The journal
// LINES (GL_JournalLine) are manually entered (not derived); completion POSTS them to Fact_Acct.
// DATALESS: fact_acct=0 rows in this GardenWorld snapshot (posting is async and was not run), so
// there is NO static oracle output to diff against — this is exactly the §0.12 case where the LIVE
// Docker iDempiere is required to oracle the cell. Logged loud as docker-fallback, never faked.
var GLJ_CO = {
  docType: 'GL_Journal', action: 'CO',
  oracle: 'org.compiere.model.MJournal.completeIt() → Doc_GLJournal → Fact_Acct posting',
  dataless: 'GLJ:CO posts to Fact_Acct, but fact_acct=0 rows in this snapshot (async posting not run) — no static oracle output. LIVE Docker iDempiere required to oracle this cell (§0.12 ambiguous/data-less case). gl_journal=2 / gl_journalline=4 exist as INPUT, but the posted ledger does not.'
};

// gravity/hub order (§18.2): C_Order (#1) sales then purchase, receipt, invoice, payment, ledger.
module.exports = [SOO_CO, POO_CO, MMR_CO, API_CO, APP_CO, GLJ_CO];
