// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ad_modelval.js — Model-validator timing-hook engine (W-MODELVAL). The "Model Validators" leg of the
// coverage matrix: dispatch registered validators around a record/document action at named TIMINGS
// (BEFORE/AFTER × NEW/SAVE/DELETE + the doc-timings PREPARE/COMPLETE/VOID). Faithful port of iDempiere's
// ModelValidationEngine.fireModelChange / fireDocValidate: a validator that returns a non-null error string
// ABORTS the operation. Self-contained, no kernel dep (mirrors ad_process.js / ad_callout.js shape).
//
// SEAM (docs/ERP_BACKEND_SEPARATION.md §2 A-4): this engine GATES an action (may BLOCK before-COMPLETE); it
// does NOT post. The GL derivation (A-1) is DOWNSTREAM and ignorant of why a doc was admitted — fireHooks
// runs FIRST on the doc-action path; only if it returns ok does derive(A-1)→fold(layer-3) proceed. A hook
// reads the record/ctx and returns a verdict; it never derives a posting or routes a workflow.
//
// EXTRACT, DON'T INVENT: the 3 registered classes come from ad_full.db (ad_modelvalidator) — their Java
// bodies are named-deferred (no port); the 2–3 hooks here are faithful ports of documented M*.beforeSave /
// completeIt invariants (qty>0; a doc must have lines), each computed from real record/db columns. An
// un-hooked (table,timing) is an explicit no-op result, never a hidden pass. Determinism: pure checks +
// read-only lookups, no Date.now/Math.random. Implementing ERP_COVERAGE_MATRIX.md §AD_ModelValidator /
// §beforeSave-afterSave (ranked GAP #9) — Witness: W-MODELVAL
(function (global) {
  'use strict';

  // iDempiere timing constants we model (ModelValidator TYPE_* + docValidate TIMING_*).
  var TIMINGS = ['BEFORE_NEW', 'AFTER_NEW', 'BEFORE_SAVE', 'AFTER_SAVE', 'BEFORE_DELETE', 'AFTER_DELETE',
                 'BEFORE_PREPARE', 'BEFORE_COMPLETE', 'AFTER_COMPLETE', 'BEFORE_VOID', 'AFTER_VOID'];

  // REGISTRY[table][timing] = [ {name, fn} ]  — fn(ctx, info) -> null | "error string" (iDempiere semantics).
  var REGISTRY = {};
  function registerValidator(table, timing, name, fn) {
    var t = table.toLowerCase();
    (REGISTRY[t] = REGISTRY[t] || {});
    (REGISTRY[t][timing] = REGISTRY[t][timing] || []).push({ name: name, fn: fn });
  }
  function registeredCount() {
    var n = 0, t, ti; for (t in REGISTRY) for (ti in REGISTRY[t]) n += REGISTRY[t][ti].length; return n;
  }

  // readValidators(db) — the AD source: the registered ModelValidator classes (Java bodies named-deferred).
  function readValidators(db) {
    return db.prepare('SELECT ad_modelvalidator_id,name,modelvalidationclass,entitytype FROM ad_modelvalidator ORDER BY ad_modelvalidator_id').all();
  }

  // installDefaultHooks — faithful ports of real M*.beforeSave / completeIt invariants.
  function installDefaultHooks() {
    // MOrderLine.beforeSave: a line must carry a positive quantity (TYPE_BEFORE_NEW/CHANGE).
    function qtyPositive(ctx, info) {
      var r = info.record || {};
      var q = Number(r.QtyOrdered != null ? r.QtyOrdered : r.qtyordered);
      if (!(q > 0)) return 'QtyOrdered must be > 0 (got ' + q + ')';
      return null;
    }
    registerValidator('C_OrderLine', 'BEFORE_SAVE', 'MOrderLine.qtyPositive', qtyPositive);
    registerValidator('C_InvoiceLine', 'BEFORE_SAVE', 'MInvoiceLine.qtyPositive', function (ctx, info) {
      var r = info.record || {}; var q = Number(r.QtyInvoiced != null ? r.QtyInvoiced : r.qtyinvoiced);
      return q > 0 ? null : 'QtyInvoiced must be > 0 (got ' + q + ')';
    });

    // MOrder.completeIt precondition: the document must have at least one line (docValidate BEFORE_COMPLETE).
    registerValidator('C_Order', 'BEFORE_COMPLETE', 'MOrder.hasLines', function (ctx, info) {
      var n = ctx.lineCount ? ctx.lineCount(info) : (info.lineCount || 0);
      return n > 0 ? null : 'Cannot complete an order with no lines';
    });
    // a second BEFORE_COMPLETE hook to prove ordered multi-hook dispatch (grand total must be non-negative).
    registerValidator('C_Order', 'BEFORE_COMPLETE', 'MOrder.totalNonNegative', function (ctx, info) {
      var r = info.record || {}; var g = Number(r.GrandTotal != null ? r.GrandTotal : r.grandtotal);
      return g >= 0 ? null : 'GrandTotal must be >= 0 (got ' + g + ')';
    });
  }

  // installMOrderSaveHooks(db) — faithful port of MOrder.beforeSave (MOrder.java:1183-1396), the H-1.3
  // archetype delta. Each hook cites its Java lines. iDempiere's beforeSave BOTH derives defaults (setters)
  // and gates (return false): here a hook writes derived values into info.derived (the caller applies them —
  // same seam as the callout's derived map) and returns an error string only for the Java reject paths.
  // Non-blocking Java log.saveWarning paths land in info.warnings. ADDITIVE: registers under
  // ('C_Order','BEFORE_SAVE'); the existing generic hooks are untouched. Reads ONLY layer-1 (db) — no sibling.
  function installMOrderSaveHooks(db) {
    function d(info) { return (info.derived = info.derived || {}); }
    function w(info) { return (info.warnings = info.warnings || []); }
    function chg(info, col) { // is_ValueChanged: only meaningful on a non-new record with an old image
      return !!(info.recordOld && info.record && String(info.recordOld[col]) !== String(info.record[col]));
    }
    var H = [
      ['MOrder.clientNotZero', function (ctx, info) {        // :1195-1199  "AD_Client_ID = 0" → reject
        return Number(info.record.ad_client_id) === 0 ? 'AD_Client_ID = 0' : null;
      }],
      ['MOrder.warehouseMandatory', function (ctx, info) {   // :1206-1215  ctx fallback else FillMandatoryException
        if (Number(info.record.m_warehouse_id) > 0) return null;
        if (ctx && Number(ctx.m_warehouse_id) > 0) { d(info).m_warehouse_id = Number(ctx.m_warehouse_id); return null; }
        return 'FillMandatory M_Warehouse_ID';
      }],
      ['MOrder.warehouseOrgCheck', function (ctx, info) {    // :1216-1223  org conflict = WARNING, not reject
        var wh = db.prepare('SELECT ad_org_id FROM m_warehouse WHERE m_warehouse_id=?').get(Number(info.record.m_warehouse_id));
        if (wh && Number(wh.ad_org_id) !== Number(info.record.ad_org_id)) w(info).push('WarehouseOrgConflict');
        return null;
      }],
      ['MOrder.bpLocationConsistency', function (ctx, info) {// :1239-1252  cleared (set null), not rejected
        var r = info.record;
        if (Number(r.c_bpartner_location_id) > 0) {
          var loc = db.prepare('SELECT c_bpartner_id FROM c_bpartner_location WHERE c_bpartner_location_id=?').get(Number(r.c_bpartner_location_id));
          if (!loc || Number(loc.c_bpartner_id) !== Number(r.c_bpartner_id)) d(info).c_bpartner_location_id = null;
        }
        if (Number(r.ad_user_id) > 0) {
          var u = db.prepare('SELECT c_bpartner_id FROM ad_user WHERE ad_user_id=?').get(Number(r.ad_user_id));
          if (!u || Number(u.c_bpartner_id) !== Number(r.c_bpartner_id)) d(info).ad_user_id = null;
        }
        return null;
      }],
      ['MOrder.billDefaults', function (ctx, info) {         // :1272-1279  Bill_* default to the BP/location
        var r = info.record;
        if (!Number(r.bill_bpartner_id)) { d(info).bill_bpartner_id = Number(r.c_bpartner_id); d(info).bill_location_id = Number(r.c_bpartner_location_id); }
        else if (!Number(r.bill_location_id)) d(info).bill_location_id = Number(r.c_bpartner_location_id);
        return null;
      }],
      ['MOrder.priceListDefault', function (ctx, info) {     // :1282-1290  IsSOPriceList=IsSOTrx ORDER BY IsDefault DESC
        var r = info.record;                                 //   (tie past IsDefault is unspecified in Java; pk-ordered here)
        if (!Number(r.m_pricelist_id)) {
          var pl = db.prepare("SELECT m_pricelist_id FROM m_pricelist WHERE issopricelist=? AND isactive='Y' ORDER BY isdefault DESC, m_pricelist_id").get(r.issotrx === 'Y' ? 'Y' : 'N');
          if (pl) d(info).m_pricelist_id = pl.m_pricelist_id;
        }
        return null;
      }],
      ['MOrder.currencyFromPriceList', function (ctx, info) {// :1292-1300  currency follows the price list
        var r = info.record;
        if (!Number(r.c_currency_id)) {
          var plId = (info.derived && info.derived.m_pricelist_id) || Number(r.m_pricelist_id);
          var pl = db.prepare('SELECT c_currency_id FROM m_pricelist WHERE m_pricelist_id=?').get(Number(plId));
          if (pl && pl.c_currency_id) d(info).c_currency_id = pl.c_currency_id;
        }
        return null;
      }],
      ['MOrder.docTypeTargetDefault', function (ctx, info) { // :1311-1312  default = the Standard SO doctype ('SO')
        var r = info.record;
        if (!Number(r.c_doctypetarget_id)) {
          var dt = db.prepare("SELECT c_doctype_id FROM c_doctype WHERE docbasetype='SOO' AND docsubtypeso='SO' AND isactive='Y' ORDER BY c_doctype_id").get();
          if (dt) d(info).c_doctypetarget_id = dt.c_doctype_id;
        }
        return null;
      }],
      ['MOrder.paymentTermDefault', function (ctx, info) {   // :1315-1327  IsDefault='Y' payment term
        var r = info.record;
        if (!Number(r.c_paymentterm_id)) {
          var pt = db.prepare("SELECT c_paymentterm_id FROM c_paymentterm WHERE isdefault='Y' AND isactive='Y' ORDER BY c_paymentterm_id").get();
          if (pt) d(info).c_paymentterm_id = pt.c_paymentterm_id;
        }
        return null;
      }],
      ['MOrder.prepayNoCash', function (ctx, info) {         // :1373-1379  PrepayOrder('PR') + PAYMENTRULE_Cash('B') → reject
        var r = info.record;
        var dt = db.prepare('SELECT docsubtypeso FROM c_doctype WHERE c_doctype_id=?').get(Number(r.c_doctypetarget_id));
        if (dt && dt.docsubtypeso === 'PR' && r.paymentrule === 'B') return 'Invalid PaymentRule (Prepay Order must not be Cash)';
        return null;
      }],
      ['MOrder.priceListImmutable', function (ctx, info) {   // :1352-1359  IDEMPIERE-1597 CannotChangePl when product lines exist
        if (!info.recordOld || !chg(info, 'm_pricelist_id')) return null;
        var n = db.prepare('SELECT count(*) n FROM c_orderline WHERE c_order_id=? AND m_product_id>0').get(Number(info.record.c_order_id)).n;
        return n > 0 ? 'CannotChangePl' : null;
        // the :1361-1369 DateOrdered/price-list-VERSION twin needs m_pricelist_version (not in capture) — named-deferred
      }]
    ];
    H.forEach(function (h) { registerValidator('C_Order', 'BEFORE_SAVE', h[0], h[1]); });
    return H.length;
  }

  // ── H-2 deep-delta beforeSave ports (prompts/FABLE5_H2_DELTAS.md) — ADDITIVE, one installer per class,
  // every hook citing its Java lines; same derived/warnings seam as installMOrderSaveHooks. ──────────────

  // installMInOutSaveHooks(db) — faithful port of MInOut.beforeSave (MInOut.java:1304-1370).
  function installMInOutSaveHooks(db) {
    function d(info) { return (info.derived = info.derived || {}); }
    function w(info) { return (info.warnings = info.warnings || []); }
    function chg(info, col) { return !!(info.recordOld && info.record && String(info.recordOld[col]) !== String(info.record[col])); }
    var H = [
      ['MInOut.movementTypeDerive', function (ctx, info) {   // :1306-1308 ∘ getMovementType:1275-1287
        var r = info.record;
        if (info.recordOld && !chg(info, 'c_doctype_id')) return null;   // fires on new record or doctype change
        if (!(Number(r.c_doctype_id) > 0)) { w(info).push('FillMandatory C_DocType_ID'); return null; }  // :1294-1297 logs, does not abort
        var dt = db.prepare('SELECT docbasetype,issotrx FROM c_doctype WHERE c_doctype_id=?').get(Number(r.c_doctype_id));
        var mt = null;
        if (dt && dt.docbasetype === 'MMS') mt = dt.issotrx === 'Y' ? 'C-' : 'V-';        // :1281-1282 Delivery → CustomerShipment/VendorReturns
        else if (dt && dt.docbasetype === 'MMR') mt = dt.issotrx === 'Y' ? 'C+' : 'V+';   // :1283-1284 Receipt → CustomerReturns/VendorReceipts
        if (mt) d(info).movementtype = mt;
        return null;
      }],
      ['MInOut.warehouseOrgConflict', function (ctx, info) { // :1310-1318 NEW record only → reject
        if (info.recordOld) return null;
        var wh = db.prepare('SELECT ad_org_id FROM m_warehouse WHERE m_warehouse_id=?').get(Number(info.record.m_warehouse_id));
        return (wh && Number(wh.ad_org_id) !== Number(info.record.ad_org_id)) ? 'WarehouseOrgConflict' : null;
      }],
      ['MInOut.deliveryRuleDefault', function (ctx, info) {  // :1319-1324 Force→Availability when neg-inv disallowed; empty→Availability
        var r = info.record;
        var wh = db.prepare('SELECT isdisallownegativeinv FROM m_warehouse WHERE m_warehouse_id=?').get(Number(r.m_warehouse_id));
        var rule = r.deliveryrule;
        if ((wh && wh.isdisallownegativeinv === 'Y' && rule === 'F') || rule == null || rule === '') d(info).deliveryrule = 'A';
        return null;
      }],
      ['MInOut.orderXorRMA', function (ctx, info) {          // :1326-1331 Order and RMA are mutually exclusive
        var r = info.record;
        return (Number(r.c_order_id) > 0 && Number(r.m_rma_id) > 0) ? 'OrderOrRMA' : null;
      }],
      ['MInOut.salesRepFromOrder', function (ctx, info) {    // :1356-1366 SalesRep defaults from the order (RMA leg n/a in seed, named)
        var r = info.record;
        if (Number(r.salesrep_id) > 0 || !(Number(r.c_order_id) > 0)) return null;
        var o = db.prepare('SELECT salesrep_id FROM c_order WHERE c_order_id=?').get(Number(r.c_order_id));
        if (o && Number(o.salesrep_id) > 0) d(info).salesrep_id = Number(o.salesrep_id);
        return null;
      }]
      // :1333-1338 RMA→shipment-doctype derive + :1340-1355 shipper-account/freight = data-absent in seed (no m_rma,
      // no CustomerAccount freightcostrule) — named-deferred, never synthesized.
    ];
    H.forEach(function (h) { registerValidator('M_InOut', 'BEFORE_SAVE', h[0], h[1]); });
    return H.length;
  }

  // installMInvoiceSaveHooks(db) — faithful port of MInvoice.beforeSave (MInvoice.java:1144-1283).
  function installMInvoiceSaveHooks(db) {
    function d(info) { return (info.derived = info.derived || {}); }
    function chg(info, col) { return !!(info.recordOld && info.record && String(info.recordOld[col]) !== String(info.record[col])); }
    function pick(r, info, col) { var dv = info.derived && info.derived[col]; return dv != null ? dv : r[col]; }
    var H = [
      ['MInvoice.bpDefaults', function (ctx, info) {         // :1147-1149 ∘ setBPartner:631-673 — location + term/pricelist/rule from the BP
        var r = info.record;
        if (Number(r.c_bpartner_location_id) > 0) return null;
        var so = r.issotrx === 'Y';
        var bp = db.prepare('SELECT paymentrule,c_paymentterm_id,po_paymentterm_id,m_pricelist_id,po_pricelist_id FROM c_bpartner WHERE c_bpartner_id=?').get(Number(r.c_bpartner_id));
        if (!bp) return null;
        var pt = so ? bp.c_paymentterm_id : bp.po_paymentterm_id;      // :638-644
        if (Number(pt) > 0) d(info).c_paymentterm_id = Number(pt);
        var pl = so ? bp.m_pricelist_id : bp.po_pricelist_id;          // :646-651
        if (Number(pl) > 0) d(info).m_pricelist_id = Number(pl);
        if (bp.paymentrule) d(info).paymentrule = bp.paymentrule;      // :653-655
        var locs = db.prepare("SELECT c_bpartner_location_id,isbillto,ispayfrom FROM c_bpartner_location WHERE c_bpartner_id=? AND isactive='Y' ORDER BY c_bpartner_location_id").all(Number(r.c_bpartner_id));
        var loc = 0;                                                   // :659-667 ascending loop overwrites → LAST match wins
        locs.forEach(function (l) { if ((l.isbillto === 'Y' && so) || (l.ispayfrom === 'Y' && !so)) loc = l.c_bpartner_location_id; });
        if (!loc && locs.length) loc = locs[0].c_bpartner_location_id; // :669-670 fall back to first
        if (loc) d(info).c_bpartner_location_id = Number(loc);
        return null;                                                   // :675+ contact (AD_User) derive = named residual
      }],
      ['MInvoice.priceListDefault', function (ctx, info) {   // :1151-1169 ctx (SO-flag must match) else client default
        var r = info.record;
        if (Number(pick(r, info, 'm_pricelist_id')) > 0) return null;
        if (ctx && Number(ctx.m_pricelist_id) > 0) {
          var cpl = db.prepare('SELECT issopricelist FROM m_pricelist WHERE m_pricelist_id=?').get(Number(ctx.m_pricelist_id));
          if (cpl && (cpl.issopricelist === 'Y') === (r.issotrx === 'Y')) { d(info).m_pricelist_id = Number(ctx.m_pricelist_id); return null; }
        }
        var pl = db.prepare("SELECT m_pricelist_id FROM m_pricelist WHERE issopricelist=? AND isactive='Y' ORDER BY isdefault DESC, m_pricelist_id").get(r.issotrx === 'Y' ? 'Y' : 'N');
        if (pl) d(info).m_pricelist_id = pl.m_pricelist_id;            // tie past IsDefault unspecified in Java; pk-ordered (H-1 convention)
        return null;
      }],
      ['MInvoice.currencyFromPriceList', function (ctx, info) {  // :1171-1180 currency follows the price list, else ctx
        var r = info.record;
        if (Number(r.c_currency_id) > 0) return null;
        var plId = pick(r, info, 'm_pricelist_id');
        var pl = db.prepare('SELECT c_currency_id FROM m_pricelist WHERE m_pricelist_id=?').get(Number(plId));
        if (pl && pl.c_currency_id) d(info).c_currency_id = pl.c_currency_id;
        else if (ctx && Number(ctx.c_currency_id) > 0) d(info).c_currency_id = Number(ctx.c_currency_id);
        return null;
      }],
      ['MInvoice.salesRepFromCtx', function (ctx, info) {    // :1183-1188 ctx fallback only
        var r = info.record;
        if (!(Number(r.salesrep_id) > 0) && ctx && Number(ctx.salesrep_id) > 0) d(info).salesrep_id = Number(ctx.salesrep_id);
        return null;
      }],
      ['MInvoice.docTypeTargetDefault', function (ctx, info) {  // :1190-1194 ∘ setC_DocTypeTarget_ID:804-822 — ARI/API by SO flag
        var r = info.record;
        if (Number(r.c_doctypetarget_id) > 0) return null;
        var dt = db.prepare("SELECT c_doctype_id FROM c_doctype WHERE docbasetype=? AND ad_org_id IN (0,?) AND isactive='Y' ORDER BY isdefault DESC, ad_org_id DESC, c_doctype_id").get(r.issotrx === 'Y' ? 'ARI' : 'API', Number(r.ad_org_id));
        if (dt) d(info).c_doctypetarget_id = dt.c_doctype_id;
        return null;
      }],
      ['MInvoice.paymentTermDefault', function (ctx, info) { // :1196-1209 ctx else IsDefault='Y'
        var r = info.record;
        if (Number(pick(r, info, 'c_paymentterm_id')) > 0) return null;
        if (ctx && Number(ctx.c_paymentterm_id) > 0) { d(info).c_paymentterm_id = Number(ctx.c_paymentterm_id); return null; }
        var pt = db.prepare("SELECT c_paymentterm_id FROM c_paymentterm WHERE isdefault='Y' AND isactive='Y' ORDER BY c_paymentterm_id").get();
        if (pt) d(info).c_paymentterm_id = pt.c_paymentterm_id;
        return null;
      }],
      ['MInvoice.priceListImmutable', function (ctx, info) { // :1219-1239 IDEMPIERE-1597 CannotChangePlIn when product lines exist
        if (!info.recordOld || !chg(info, 'm_pricelist_id')) return null;
        var n = db.prepare('SELECT count(*) n FROM c_invoiceline WHERE c_invoice_id=? AND m_product_id>0').get(Number(info.record.c_invoice_id)).n;
        return n > 0 ? 'CannotChangePlIn' : null;
        // the :1230-1237 DateInvoiced/price-list-VERSION twin needs m_pricelist_version (not in capture) — named-deferred (H-1 pattern)
      }],
      ['MInvoice.currencyRateOverride', function (ctx, info) {  // :1256-1283 primary-schema FX override gate (skipped when Processed)
        var r = info.record;
        if (r.processed === 'Y') return null;
        var s1 = db.prepare('SELECT c_acctschema1_id FROM ad_clientinfo WHERE ad_client_id=?').get(Number(r.ad_client_id));
        if (!s1) return null;
        var sc = db.prepare('SELECT c_currency_id FROM c_acctschema WHERE c_acctschema_id=?').get(Number(s1.c_acctschema1_id));
        if (sc && Number(sc.c_currency_id) !== Number(r.c_currency_id)) {
          if (r.isoverridecurrencyrate === 'Y') {
            if (!(Number(r.currencyrate) > 0)) return 'FillMandatory CurrencyRate';   // :1267-1271
          } else d(info).currencyrate = null;                                          // :1274-1276
        } else d(info).currencyrate = null;                                            // :1279-1281
        return null;
      }]
      // :1211-1217 cash-plan line (no c_cashplanline in seed) + :1242-1254 payment-term re-apply (write-path
      // schedule rebuild, out of beforeSave verdict scope) = named residuals.
    ];
    H.forEach(function (h) { registerValidator('C_Invoice', 'BEFORE_SAVE', h[0], h[1]); });
    return H.length;
  }

  // installMPaymentSaveHooks(db) — faithful port of MPayment.beforeSave (MPayment.java:670-830).
  function installMPaymentSaveHooks(db) {
    function d(info) { return (info.derived = info.derived || {}); }
    function chg(info, col) { return !!(info.recordOld && info.record && String(info.recordOld[col]) !== String(info.record[col])); }
    function cashAsPayment(r) {  // MSysConfig.CASH_AS_PAYMENT, Java default true (MPayment.java:238)
      var row = db.prepare('SELECT value FROM ad_sysconfig WHERE name=? AND ad_client_id IN (0,?) ORDER BY ad_client_id DESC').get('CASH_AS_PAYMENT', Number(r.ad_client_id));
      return row ? row.value === 'Y' : true;
    }
    function isCashTrx(r) { return r.tendertype === 'X'; }              // :228-231 TENDERTYPE_Cash
    function isCashbookTrx(r) { return isCashTrx(r) && !cashAsPayment(r); }  // :237-239
    var H = [
      ['MPayment.processedImmutable', function (ctx, info) { // :672-687 financial fields frozen once Processed
        var r = info.record;
        if (r.processed !== 'Y' || chg(info, 'processed')) return null;
        var cols = ['c_bankaccount_id', 'c_bpartner_id', 'c_charge_id', 'c_currency_id', 'c_doctype_id', 'dateacct', 'datetrx', 'discountamt', 'payamt', 'writeoffamt'];
        for (var i = 0; i < cols.length; i++) if (chg(info, cols[i])) return 'PaymentAlreadyProcessed';
        return null;
      }],
      ['MPayment.bankOrCashbookMandatory', function (ctx, info) {  // :689-701 cashbook XOR bank account
        var r = info.record;
        if (isCashbookTrx(r)) return Number(r.c_cashbook_id) > 0 ? null : 'Mandatory: C_CashBook_ID';
        return Number(r.c_bankaccount_id) > 0 ? null : 'Mandatory: C_BankAccount_ID';
      }],
      ['MPayment.chargeResets', function (ctx, info) {       // :705-717 charge payment resets order/invoice/amount flags
        var r = info.record;
        if (!(Number(r.c_charge_id) > 0) || (info.recordOld && !chg(info, 'c_charge_id'))) return null;
        d(info).c_order_id = 0; d(info).c_invoice_id = 0; d(info).writeoffamt = 0; d(info).discountamt = 0;
        d(info).isoverunderpayment = 'N'; d(info).overunderamt = 0; d(info).isprepayment = 'N';
        return null;
      }],
      ['MPayment.bpartnerRequired', function (ctx, info) {   // :719-730 BP or invoice/order required (non-cash)
        var r = info.record;
        if (Number(r.c_charge_id) > 0 || Number(r.c_bpartner_id) > 0 || isCashTrx(r)) return null;
        return (Number(r.c_invoice_id) > 0 || Number(r.c_order_id) > 0) ? null : 'NotFound: C_BPartner_ID';
      }],
      ['MPayment.prepaymentDerive', function (ctx, info) {   // :732-749 IsPrepayment from charge/BP/order/project (reversal leg n/a in seed)
        var r = info.record;
        if (info.recordOld && !(chg(info, 'c_charge_id') || chg(info, 'c_invoice_id') || chg(info, 'c_order_id') || chg(info, 'c_project_id'))) return null;
        if (Number(r.reversal_id) > 0) return null;          // :737-741 copies the reversal's flag — no reversal in seed, named
        var pre = !(Number(r.c_charge_id) > 0) && Number(r.c_bpartner_id) > 0 &&
                  (Number(r.c_order_id) > 0 || (Number(r.c_project_id) > 0 && !(Number(r.c_invoice_id) > 0)));
        d(info).isprepayment = pre ? 'Y' : 'N';
        return null;
      }],
      ['MPayment.docTypeReceipt', function (ctx, info) {     // :763-770 ∘ setC_DocType_ID(boolean):1545-1563
        var r = info.record;
        if (!(Number(r.c_doctype_id) > 0)) {
          var dt = db.prepare("SELECT c_doctype_id FROM c_doctype WHERE isactive='Y' AND docbasetype=? ORDER BY isdefault DESC, c_doctype_id").get(r.isreceipt === 'Y' ? 'ARR' : 'APP');
          if (dt) d(info).c_doctype_id = dt.c_doctype_id;
        } else {
          var dt2 = db.prepare('SELECT issotrx FROM c_doctype WHERE c_doctype_id=?').get(Number(r.c_doctype_id));
          if (dt2) d(info).isreceipt = dt2.issotrx;
        }
        return null;
      }],
      ['MPayment.dateAcctDefault', function (ctx, info) {    // :773-774 DateAcct defaults to DateTrx
        var r = info.record;
        if (r.dateacct == null || r.dateacct === '') d(info).dateacct = r.datetrx;
        return null;
      }],
      ['MPayment.overUnderZero', function (ctx, info) {      // :776-777 not over/under → amount zeroed
        var r = info.record;
        if (r.isoverunderpayment !== 'Y' && Number(r.overunderamt) !== 0) d(info).overunderamt = 0;
        return null;
      }],
      ['MPayment.orgFromBankAccount', function (ctx, info) { // :780-787 payment org follows the bank account's org (when set)
        var r = info.record;
        if (Number(r.c_charge_id) > 0 || (info.recordOld && !chg(info, 'c_bankaccount_id'))) return null;
        var ba = db.prepare('SELECT ad_org_id FROM c_bankaccount WHERE c_bankaccount_id=?').get(Number(r.c_bankaccount_id));
        if (ba && Number(ba.ad_org_id) !== 0) d(info).ad_org_id = Number(ba.ad_org_id);
        return null;
      }],
      ['MPayment.bpMatchesInvoiceOrder', function (ctx, info) {  // :790-806 BP must equal the invoice's / order's BP
        var r = info.record;
        if (!(Number(r.c_bpartner_id) > 0)) return null;
        if (Number(r.c_invoice_id) > 0) {
          var inv = db.prepare('SELECT c_bpartner_id FROM c_invoice WHERE c_invoice_id=?').get(Number(r.c_invoice_id));
          if (inv && Number(inv.c_bpartner_id) !== Number(r.c_bpartner_id)) return 'BPDifferentFromBPInvoice';
        }
        if (Number(r.c_order_id) > 0) {
          var ord = db.prepare('SELECT c_bpartner_id FROM c_order WHERE c_order_id=?').get(Number(r.c_order_id));
          if (ord && Number(ord.c_bpartner_id) !== Number(r.c_bpartner_id)) return 'BPDifferentFromBPOrder';
        }
        return null;
      }]
      // :751-761 prepay resets (no prepayment in seed) · :758-762 setDocumentNo (write-path numbering) ·
      // :808-830 credit-card encryption (crypto write-path) = named residuals.
    ];
    H.forEach(function (h) { registerValidator('C_Payment', 'BEFORE_SAVE', h[0], h[1]); });
    return H.length;
  }

  // installMMovementSaveHooks(db) — faithful port of MMovement.beforeSave (MMovement.java:210-224).
  function installMMovementSaveHooks(db) {
    function d(info) { return (info.derived = info.derived || {}); }
    var H = [
      ['MMovement.docTypeDefault', function (ctx, info) {    // :212-222 ∘ MDocType.getOfDocBaseType:68-77 — first MMM doctype
        var r = info.record;
        if (Number(r.c_doctype_id) > 0) return null;
        var dt = db.prepare("SELECT c_doctype_id FROM c_doctype WHERE docbasetype='MMM' AND isactive='Y' ORDER BY isdefault DESC, c_doctype_id").get();
        if (!dt) return 'NotFound C_DocType_ID';             // :218-221 (unreachable in this seed — MMM exists)
        d(info).c_doctype_id = dt.c_doctype_id;
        return null;
      }]
    ];
    H.forEach(function (h) { registerValidator('M_Movement', 'BEFORE_SAVE', h[0], h[1]); });
    return H.length;
  }

  // fireHooks(timing, info, ctx) — run every validator registered for (info.table, timing) IN ORDER; the
  // FIRST non-null error aborts (the iDempiere fireModelChange/fireDocValidate semantics). Returns
  // { timing, table, fired, ok, blocked?, error? }.
  function fireHooks(timing, info, ctx) {
    ctx = ctx || {};
    var t = (info.table || '').toLowerCase();
    var hooks = (REGISTRY[t] && REGISTRY[t][timing]) || [];
    for (var i = 0; i < hooks.length; i++) {
      var err;
      try { err = hooks[i].fn(ctx, info); }
      catch (e) { err = 'hook threw: ' + (e && e.message); }
      if (err) return { timing: timing, table: info.table, fired: i + 1, ok: false, blocked: hooks[i].name, error: err };
    }
    return { timing: timing, table: info.table, fired: hooks.length, ok: true };
  }

  // hooksFor(table) — which timings carry a hook (coverage introspection).
  function hooksFor(table) {
    var t = (table || '').toLowerCase(), r = REGISTRY[t] || {}, out = {};
    Object.keys(r).forEach(function (ti) { out[ti] = r[ti].map(function (h) { return h.name; }); });
    return out;
  }

  var API = {
    TIMINGS: TIMINGS, REGISTRY: REGISTRY, registerValidator: registerValidator, registeredCount: registeredCount,
    readValidators: readValidators, installDefaultHooks: installDefaultHooks,
    installMOrderSaveHooks: installMOrderSaveHooks, fireHooks: fireHooks, hooksFor: hooksFor,
    installMInOutSaveHooks: installMInOutSaveHooks, installMInvoiceSaveHooks: installMInvoiceSaveHooks,
    installMPaymentSaveHooks: installMPaymentSaveHooks, installMMovementSaveHooks: installMMovementSaveHooks
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = API;   // node witness
  if (typeof window !== 'undefined') window.AdModelVal = API;                   // browser
  else if (global) global.AdModelVal = API;
})(typeof globalThis !== 'undefined' ? globalThis : this);
