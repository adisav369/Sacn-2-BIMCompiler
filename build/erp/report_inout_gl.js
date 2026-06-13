// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// report_inout_gl.js — foldInOutGL: the Doc_InOut (material shipment / vendor receipt) GL fold.
//   Witness: W-FOLD-INOUTGL. Implementing GapClosureSpec.md §5h — closing P2 item 3 of GAP_CLOSURE_LANE.md
//   ("cost-valued inventory GL — MInOut COGS/Inventory value via the cost-selection rule already proven in
//   W-FOLD-MOVEMENT; integrate into the engine"). This LIFTS the witness-local shipment derivation of
//   poc_fold_complete (deriveShipmentForOrder) + the cost-selection of poc_movement INTO a reusable engine verb.
//
// PURE VERB (per GAP_CLOSURE_LANE §6 "consume the engine, never fork it"). A faithful port of the value core of
//   org.compiere.acct.Doc_InOut for the two movement polarities present in the seed:
//     C-  (Customer Shipment / delivery, COGS):  DR {Product.Cogs}   CR {Product.Asset}
//     V+  (Vendor Receipt):                       DR {Product.Asset}  CR {BPGroup.NotInvoicedReceipts}
//   Accounts RESOLVED via the injected `deps.resolve` (post_resolver through the window.ERP seam) — never invented.
//
// AMOUNT (non-invent — DIRECTION-determined, both EXACT vs the stored fact_acct):
//   • C-  (shipment, COGS): the inventory is relieved at the cost iDempiere POSTED the move at → Σ|M_CostDetail.amt|
//       per line. A line with no cost-detail (zero-cost product) → no COGS posting (matches the oracle).
//   • V+  (vendor receipt): the Not-Invoiced-Receipts accrual is booked at the PURCHASE price → round(movementqty ×
//       C_OrderLine.PriceActual, 2) per line (the SAME PO-price basis Doc_MatchInv uses for its NIR leg,
//       W-FOLD-MATCHINV). A receipt line with no PO link → no posting.
//
// NON-INVENT: every value READ from a real M_InOutLine / M_CostDetail / C_OrderLine row. Integer cents.
//   No Date.now / no Math.random. opts.* are FALSIFIER hooks only.
'use strict';

(function (global) {
  function cents(n) { return Math.round(Number(n || 0) * 100); }
  function key(acct, side) { return side + ':' + acct; }

  // foldInOutGL(src, deps, opts) — derive the M_InOut GL.
  // src = { inoutLines:[{m_inoutline_id, m_inout_id, m_product_id, movementqty, movementtype, c_bpartner_id,
  //                      c_orderline_id}],
  //         costDetail:[{m_inoutline_id, amt}],            // schema-filtered; posted cost-at-movement (C-)
  //         orderLines:[{c_orderline_id, priceactual}] }   // PO price (V+)
  // deps.resolve(token, keyId) → account_id (the C_ElementValue id) or null.
  // Returns { agg:{'DR:430':32750,…} cents, byType:{ 'C-':{…}, 'V+':{…} }, detail:[…], absent:[…] }.
  function foldInOutGL(src, deps, opts) {
    opts = opts || {};
    var resolve = deps.resolve;
    var cdByLine = {};
    (src.costDetail || []).forEach(function (d) {
      cdByLine[d.m_inoutline_id] = (cdByLine[d.m_inoutline_id] || 0) + Math.abs(cents(d.amt));
    });
    var poPrice = {};
    (src.orderLines || []).forEach(function (o) { poPrice[o.c_orderline_id] = o.priceactual; });

    var agg = {}, byType = {}, absent = [], detail = [];
    function add(mt, side, acct, c) {
      if (acct == null) return;
      agg[key(acct, side)] = (agg[key(acct, side)] || 0) + c;
      (byType[mt] = byType[mt] || {});
      byType[mt][key(acct, side)] = (byType[mt][key(acct, side)] || 0) + c;
    }

    src.inoutLines.forEach(function (l) {
      var mt = l.movementtype, amtCents, amtSrc, drTok, crTok, drKey, crKey;
      // amount source is DIRECTION-determined (Doc_InOut): an OUTBOUND shipment relieves inventory at the cost
      // iDempiere POSTED the move at (M_CostDetail); a non-costed line has no detail → no COGS posting. An INBOUND
      // receipt creates inventory at the product cost (the cost-selection rule), writing no COGS cost-detail.
      if (mt === 'C-') {                                                  // Customer Shipment → COGS
        amtCents = cdByLine[l.m_inoutline_id] || 0; amtSrc = 'posted';
        drTok = '{Product.Cogs}'; crTok = '{Product.Asset}'; drKey = l.m_product_id; crKey = l.m_product_id;
      } else if (mt === 'V+') {                                           // Vendor Receipt → NIR @ PO price
        var price = poPrice[l.c_orderline_id];
        if (opts.bendPrice != null && l.c_orderline_id != null) price = opts.bendPrice;   // FALSIFIER
        amtCents = price == null ? 0 : Math.round(Number(l.movementqty) * Number(price) * 100); amtSrc = 'po-price';
        drTok = '{Product.Asset}'; crTok = '{BPGroup.NotInvoicedReceipts}'; drKey = l.m_product_id; crKey = l.c_bpartner_id;
      } else return;                                                      // other movement types out of scope
      if (amtCents === 0) return;                                         // non-costed move → no posting (matches oracle)

      var dr = resolve(drTok, drKey); if (dr == null) absent.push(drTok + '@' + drKey);
      var cr = resolve(crTok, crKey); if (cr == null) absent.push(crTok + '@' + crKey);
      if (opts.dropDr && mt === opts.dropDr) dr = null;                   // FALSIFIER: drop a side
      var dAcct = opts.swapAccounts ? cr : dr, cAcct = opts.swapAccounts ? dr : cr;  // FALSIFIER: swap
      add(mt, 'DR', dAcct, amtCents);
      add(mt, 'CR', cAcct, amtCents);
      detail.push({ line: l.m_inoutline_id, mt: mt, amtCents: amtCents, src: amtSrc, dr: dr, cr: cr });
    });
    return { agg: agg, byType: byType, absent: absent, detail: detail };
  }

  var API = { foldInOutGL: foldInOutGL };
  if (typeof module !== 'undefined' && module.exports) module.exports = API;
  else global.ReportInOutGL = API;
})(typeof globalThis !== 'undefined' ? globalThis : this);
