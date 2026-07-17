// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
'use strict';
/**
 * doc_poster.js — reusable per-document GL derivation (W-DOC-POSTER).
 *
 * GAP-A FIX (POSTING_PREVIEW_PANEL.md): the per-document posting manifest that turns a completed
 * document into journal lines lived ONLY inside the FOLD witnesses (poc_fold_complete.deriveInvoice,
 * duplicated in poc_post_harden.derive) — never a shipped verb. This module EXTRACTS that derivation
 * VERBATIM so the live Posting-Preview seam consumes the SAME logic the FOLD oracle proved — no fork,
 * no re-derive. Faithfulness is proven by scripts/poc_doc_poster.js (== real fact_acct(318), and ==
 * poc_fold_complete's own agg).
 *
 * PURE + db-agnostic: `db` is ANY handle exposing `.prepare(sql).get(params)` / `.all(params)`
 *   (better-sqlite3 native in node; the sql.js facade in erp_preview.js in the browser). `R` is the
 *   post_resolver seam (node require, or injected window.PostResolver). NEVER invents an account or an
 *   amount — accounts come from R.resolve (master columns), amounts from real document rows, integer
 *   cents, no Date.now/Math.random.
 *
 * Returns the per-account fold the readPostings VM shape expects:
 *   { lines:[{account_id,value,name,amtacctdr,amtacctcr}], balanced, sumDr, sumCr, absent:[token], basis }
 *   account_id = the natural C_ElementValue id (== fact_acct.account_id); amtacctdr/cr in DOLLARS (the VM
 *   formats to 2dp). basis ∈ {invoice, order, none} — which source rows the manifest was derived from.
 */

function cents(n) { return Math.round(Number(n || 0) * 100); }
function num(x) { return Number(x); }

// ── Case-insensitive row reads (NEW_CLIENT_MGMT.md BLOCKER fix, 2026-06-11) ──
// The deployed ad_seed.db stores DOCUMENT tables in canonical CamelCase (C_BPartner_ID, M_Product_ID); SQLite
// returns unaliased result keys in the DECLARED case, so the lowercase reads below (hdr.c_bpartner_id, l.m_product_id)
// come back `undefined` → receivable+revenue go absent → blank/coverage:partial preview. Expose lowercase key
// ALIASES on every row so reads resolve regardless of stored case. ADDITIVE + NON-INVENT: all-lowercase rows
// (ad_full/glassbowl/migrated shards) lowercase to themselves → no-op → every FOLD witness stays byte-green. One
// place covers node better-sqlite3 AND the browser sql.js facade (erp_preview.js), both flowing through this consumer.
function lc(row) {
  if (!row || typeof row !== 'object') return row;
  Object.keys(row).forEach(function (k) { var lk = k.toLowerCase(); if (lk !== k && !(lk in row)) row[lk] = row[k]; });
  return row;
}
function getRow(db, sql, params) { return lc(db.prepare(sql).get(params)); }
function allRows(db, sql, params) { return (db.prepare(sql).all(params) || []).map(lc); }

// ── INVOICE sales manifest — EXTRACTED VERBATIM from poc_fold_complete.deriveInvoice (W-FOLD-COMPLETE) ──
// DR {BPartner.Receivable}=grandtotal / CR {Product.Revenue}=linenetamt per line / CR {Tax.Due}=taxamt.
function deriveInvoice(db, R, invId, schema) {
  var hdr = getRow(db, 'SELECT c_invoice_id,c_bpartner_id,grandtotal,issotrx FROM c_invoice WHERE c_invoice_id=?', num(invId));
  if (!hdr) return null;
  var lines = allRows(db, 'SELECT m_product_id,linenetamt FROM c_invoiceline WHERE c_invoice_id=?', num(invId));
  var taxes = allRows(db, 'SELECT c_tax_id,taxamt FROM c_invoicetax WHERE c_invoice_id=?', num(invId));
  var by = {}, absent = [];
  function add(side, el, amt) {
    var k = el.id;
    if (!by[k]) by[k] = { account_id: el.id, value: el.value, name: el.name, dr: 0, cr: 0 };
    if (side === 'DR') by[k].dr += cents(amt); else by[k].cr += cents(amt);
  }
  function el(res) { if (res.acct == null || !res.element) { absent.push(res.token); return null; } return res.element; }
  var rcv = el(R.resolve(db, '{BPartner.Receivable}', num(hdr.c_bpartner_id), schema));
  if (rcv) add('DR', rcv, hdr.grandtotal);
  lines.forEach(function (l) { var e = el(R.resolve(db, '{Product.Revenue}', num(l.m_product_id), schema)); if (e) add('CR', e, l.linenetamt); });
  taxes.forEach(function (t) { var e = el(R.resolve(db, '{Tax.Due}', num(t.c_tax_id), schema)); if (e) add('CR', e, t.taxamt); });
  return { by: by, absent: absent };
}

// the invoice an order generated — linked via the order line (NON-INVENT lineage; poc_fold_complete:75).
function invoiceForOrder(db, oid) {
  var r = getRow(db, 'SELECT DISTINCT il.c_invoice_id AS id FROM c_invoiceline il JOIN c_orderline ol ON ol.c_orderline_id=il.c_orderline_id WHERE ol.c_order_id=?', num(oid));
  return r ? r.id : null;
}

// projected manifest for a DRAFT order (no invoice yet) — SAME tokens, off the ORDER rows. Equals the
// invoice manifest when qtyinvoiced==qtyordered; for a true draft it is a PROJECTION (no fact_acct oracle).
function deriveOrder(db, R, oid, schema) {
  var hdr = getRow(db, 'SELECT c_order_id,c_bpartner_id,grandtotal FROM c_order WHERE c_order_id=?', num(oid));
  if (!hdr) return null;
  var lines = allRows(db, 'SELECT m_product_id,linenetamt FROM c_orderline WHERE c_order_id=?', num(oid));
  var taxes = [];
  try { taxes = allRows(db, 'SELECT c_tax_id,taxamt FROM c_ordertax WHERE c_order_id=?', num(oid)); } catch (e) { taxes = []; }
  var by = {}, absent = [];
  function add(side, el, amt) { var k = el.id; if (!by[k]) by[k] = { account_id: el.id, value: el.value, name: el.name, dr: 0, cr: 0 }; if (side === 'DR') by[k].dr += cents(amt); else by[k].cr += cents(amt); }
  function el(res) { if (res.acct == null || !res.element) { absent.push(res.token); return null; } return res.element; }
  var rcv = el(R.resolve(db, '{BPartner.Receivable}', num(hdr.c_bpartner_id), schema));
  if (rcv) add('DR', rcv, hdr.grandtotal);
  lines.forEach(function (l) { var e = el(R.resolve(db, '{Product.Revenue}', num(l.m_product_id), schema)); if (e) add('CR', e, l.linenetamt); });
  taxes.forEach(function (t) { var e = el(R.resolve(db, '{Tax.Due}', num(t.c_tax_id), schema)); if (e) add('CR', e, t.taxamt); });
  return { by: by, absent: absent };
}

// ── B-3 0-seed manifests (W-POST-B3 §W-3, prompts/FABLE5_B3_POSTING_ORACLE.md) ──────────────────────
// EXTRACTED from the org.compiere.acct posters (per-line citations below); oracle = the REAL compiled
// posters driven on a scratch clone by scripts/generate_post_oracle.sh → build/erp/oracle/
// post_b3_fixture.json. Accounts here are C_ValidCombination ids from per-asset/project acct config
// rows (not {Master.Role} tokens), resolved to the natural element via c_validcombination — the same
// hop the posters make (MAccount.get). NEVER invents: every id/amount is a captured row.

// combination -> element account id (c_validcombination.account_id)
function vcAcct(db, combo) {
  if (combo == null || num(combo) === 0) return null;
  var r = getRow(db, 'SELECT account_id FROM c_validcombination WHERE c_validcombination_id=?', num(combo));
  return r ? num(r.account_id) : null;
}
// element shape for the fold; c_elementvalue (value/name) is optional in the handle (fixture DBs)
function elOf(db, accountId, absent, token) {
  if (accountId == null) { absent.push(token); return null; }
  var e = null;
  try { e = getRow(db, 'SELECT c_elementvalue_id AS id, value, name FROM c_elementvalue WHERE c_elementvalue_id=?', accountId); } catch (err) { e = null; }
  return e || { id: accountId, value: '', name: '' };
}
// MAssetAcct.forA_Asset_ID:161-186 — acct row valid at dateAcct (ValidFrom<=date, ORDER BY ValidFrom
// DESC NULLS LAST). The transfer's completeIt creates a NEW row with ValidFrom=its DateAcct — this
// time-slice is what keeps pre-transfer documents derivable (order-dependence solved by data).
function assetAcctFor(db, assetId, schema, dateAcct) {
  return getRow(db,
    "SELECT * FROM a_asset_acct WHERE a_asset_id=? AND c_acctschema_id=? AND postingtype='A'" +
    " AND (validfrom IS NULL OR validfrom='' OR validfrom<=?)" +
    " ORDER BY (validfrom IS NULL OR validfrom='') ASC, validfrom DESC LIMIT 1",
    [num(assetId), num(schema), String(dateAcct || '9999-12-31')]);
}
// MConversionRate.getRate:243-252 VERBATIM shape (default Spot type): date BETWEEN ValidFrom AND
// ValidTo, client/org-scoped, ORDER BY AD_Client_ID DESC, AD_Org_ID DESC, ValidFrom DESC — the
// TENANT rate outranks the system rate (this seed carries both, 0.85 vs 0.8006 — run-8 finding).
function fxRate(db, curFrom, curTo, dateAcct, clientId, orgId) {
  if (num(curFrom) === num(curTo)) return 1;
  var r = getRow(db,
    "SELECT cr.multiplyrate AS rate FROM c_conversion_rate cr JOIN c_conversiontype ct" +
    " ON ct.c_conversiontype_id=cr.c_conversiontype_id AND ct.isdefault='Y'" +
    " WHERE cr.c_currency_id=? AND cr.c_currency_id_to=? AND ? BETWEEN cr.validfrom AND cr.validto" +
    " AND cr.ad_client_id IN (0,?) AND cr.ad_org_id IN (0,?)" +
    " ORDER BY cr.ad_client_id DESC, cr.ad_org_id DESC, cr.validfrom DESC LIMIT 1",
    [num(curFrom), num(curTo), String(dateAcct || '9999-12-31'), num(clientId || 0), num(orgId || 0)]);
  return r ? Number(r.rate) : null;
}
function schemaRow(db, schema) { return getRow(db, 'SELECT * FROM c_acctschema WHERE c_acctschema_id=?', num(schema)); }

function b3New() {
  var d = { by: {}, absent: [] };
  d.add = function (side, el, amtCents) {
    var k = el.id;
    if (!d.by[k]) d.by[k] = { account_id: el.id, value: el.value, name: el.name, dr: 0, cr: 0 };
    if (side === 'DR') d.by[k].dr += amtCents; else d.by[k].cr += amtCents;
  };
  return d;
}

// Doc_AssetAddition.createFacts:60-93 — gate A_SourceType=IMP | A_CapvsExp=Exp → EMPTY facts (the
// config-gated zero); else DR a_asset_acct.A_Asset_Acct / CR getP_Asset_Acct, amt=AssetSourceAmt in
// DOC currency (getC_Currency_ID) → converted per schema (the ONLY B-3 poster that posts doc-currency).
function deriveAssetAddition(db, id, schema) {
  var hdr = getRow(db, 'SELECT * FROM a_asset_addition WHERE a_asset_addition_id=?', num(id));
  if (!hdr) return null;
  var d = b3New();
  if (hdr.a_sourcetype === 'IMP' || hdr.a_capvsexp === 'Exp') return d;   // zero-by-config, Doc_AssetAddition:67-72
  var acct = assetAcctFor(db, hdr.a_asset_id, schema, hdr.dateacct);
  if (!acct) { d.absent.push('a_asset_acct#' + hdr.a_asset_id + '/' + schema); return d; }
  var as = schemaRow(db, schema);
  var rate = fxRate(db, hdr.c_currency_id, as ? as.c_currency_id : hdr.c_currency_id, hdr.dateacct, hdr.ad_client_id, hdr.ad_org_id);
  if (rate == null) { d.absent.push('fxrate#' + hdr.c_currency_id + '->' + (as && as.c_currency_id)); return d; }
  var amt = Math.round(cents(hdr.assetsourceamt) * rate);
  var drEl = elOf(db, vcAcct(db, acct.a_asset_acct), d.absent, '{AssetAcct.Asset}');
  if (drEl) d.add('DR', drEl, amt);
  // getP_Asset_Acct:107-136 — PRJ → project acct; MAN+charge → charge; INV+line-project → project;
  // else product expense (product 0 → DEFAULT category, ProductCost.getAccountDefault:250-298
  // ORDER BY IsDefault DESC, Created)
  var crComboRow = null;
  if (hdr.a_sourcetype === 'PRJ' && num(hdr.c_project_id) > 0) {
    var prj = getRow(db, 'SELECT projectcategory FROM c_project WHERE c_project_id=?', num(hdr.c_project_id));
    var col = (prj && prj.projectcategory === 'A') ? 'pj_asset_acct' : 'pj_wip_acct';
    var pa = getRow(db, 'SELECT ' + col + ' AS acct FROM c_project_acct WHERE c_project_id=? AND c_acctschema_id=?', [num(hdr.c_project_id), num(schema)]);
    crComboRow = pa && pa.acct;
  } else if (hdr.a_sourcetype === 'MAN' && num(hdr.c_charge_id) > 0) {
    var ch = getRow(db, 'SELECT ch_expense_acct AS acct FROM c_charge_acct WHERE c_charge_id=? AND c_acctschema_id=?', [num(hdr.c_charge_id), num(schema)]);
    crComboRow = ch && ch.acct;
  } else if (num(hdr.m_product_id) > 0) {
    var pc = getRow(db, 'SELECT a.p_expense_acct AS acct FROM m_product p JOIN m_product_category_acct a ON a.m_product_category_id=p.m_product_category_id AND a.c_acctschema_id=? WHERE p.m_product_id=?', [num(schema), num(hdr.m_product_id)]);
    crComboRow = pc && pc.acct;
  } else {
    var dft = getRow(db, "SELECT a.p_expense_acct AS acct FROM m_product_category c JOIN m_product_category_acct a ON a.m_product_category_id=c.m_product_category_id AND a.c_acctschema_id=? ORDER BY c.isdefault DESC, c.created LIMIT 1", num(schema));
    crComboRow = dft && dft.acct;
  }
  var crEl = elOf(db, vcAcct(db, crComboRow), d.absent, '{Addition.SourceAcct}');
  if (crEl) d.add('CR', crEl, amt);
  return d;
}

// Doc_DepreciationEntry.createFacts:66-91 — other-acctschema → EMPTY; per depexp line of the entry
// (entry_id + the entry's schema) DR depexp.DR_Account_ID / CR CR_Account_ID at Expense (schema currency).
function deriveDepreciationEntry(db, id, schema) {
  var hdr = getRow(db, 'SELECT * FROM a_depreciation_entry WHERE a_depreciation_entry_id=?', num(id));
  if (!hdr) return null;
  var d = b3New();
  if (num(hdr.c_acctschema_id) !== num(schema)) return d;                 // other-schema ∅, Doc_DepreciationEntry:70-71
  var lines = allRows(db, 'SELECT * FROM a_depreciation_exp WHERE a_depreciation_entry_id=? AND c_acctschema_id=? ORDER BY a_depreciation_exp_id', [num(id), num(hdr.c_acctschema_id)]);
  lines.forEach(function (l) {
    var amt = cents(l.expense);
    var drEl = elOf(db, vcAcct(db, l.dr_account_id), d.absent, '{DepExp.DR}');
    var crEl = elOf(db, vcAcct(db, l.cr_account_id), d.absent, '{DepExp.CR}');
    if (drEl) d.add('DR', drEl, amt);
    if (crEl) d.add('CR', crEl, amt);
  });
  return d;
}

// Doc_AssetReval.createFacts:55-77 — pair1 DR AssetAcct / CR RevalCostOffset = (Cost_Change − A_Asset_Cost);
// pair2 DR RevalCostOffset / CR AccumDep = (Change_Acum_Depr − A_Accumulated_Depr). Doc columns, schema currency.
function deriveAssetReval(db, id, schema) {
  var hdr = getRow(db, 'SELECT * FROM a_asset_reval WHERE a_asset_reval_id=?', num(id));
  if (!hdr) return null;
  var d = b3New();
  var acct = assetAcctFor(db, hdr.a_asset_id, schema, hdr.dateacct);
  if (!acct) { d.absent.push('a_asset_acct#' + hdr.a_asset_id + '/' + schema); return d; }
  var costDelta = cents(hdr.a_asset_cost_change) - cents(hdr.a_asset_cost);
  var acumDelta = cents(hdr.a_change_acumulated_depr) - cents(hdr.a_accumulated_depr);
  var assetEl = elOf(db, vcAcct(db, acct.a_asset_acct), d.absent, '{AssetAcct.Asset}');
  var offEl = elOf(db, vcAcct(db, acct.a_reval_cost_offset_acct), d.absent, '{AssetAcct.RevalCostOffset}');
  var accumEl = elOf(db, vcAcct(db, acct.a_accumdepreciation_acct), d.absent, '{AssetAcct.AccumDep}');
  if (assetEl && offEl) { d.add('DR', assetEl, costDelta); d.add('CR', offEl, costDelta); }
  if (offEl && accumEl) { d.add('DR', offEl, acumDelta); d.add('CR', accumEl, acumDelta); }
  return d;
}

// Doc_AssetTransfer.createFacts:44-72 — DR new/CR old AssetAcct at workfile A_Asset_Cost (only if they
// differ); DR old/CR new AccumDep at workfile A_Accumulated_Depr (only if they differ). The doc's OWN
// combo columns, workfile per (asset, postingtype, PRIMARY schema — MDepreciationWorkfile.get:351-364
// defaults C_AcctSchema_ID to the client's primary). Same lines for EVERY schema (the poster does not
// re-map combos per schema — replicated bug-compat).
function deriveAssetTransfer(db, id, schema, primarySchema) {
  var hdr = getRow(db, 'SELECT * FROM a_asset_transfer WHERE a_asset_transfer_id=?', num(id));
  if (!hdr) return null;
  var d = b3New();
  var wk = getRow(db, "SELECT * FROM a_depreciation_workfile WHERE a_asset_id=? AND postingtype='A' AND c_acctschema_id=?",
    [num(hdr.a_asset_id), num(primarySchema || 101)]);
  if (!wk) { d.absent.push('a_depreciation_workfile#' + hdr.a_asset_id); return d; }
  if (num(hdr.a_asset_new_acct) !== num(hdr.a_asset_acct)) {
    var drEl = elOf(db, vcAcct(db, hdr.a_asset_new_acct), d.absent, '{Transfer.AssetNew}');
    var crEl = elOf(db, vcAcct(db, hdr.a_asset_acct), d.absent, '{Transfer.AssetOld}');
    var amt = cents(wk.a_asset_cost);
    if (drEl) d.add('DR', drEl, amt);
    if (crEl) d.add('CR', crEl, amt);
  }
  if (num(hdr.a_accumdepreciation_new_acct) !== num(hdr.a_accumdepreciation_acct)) {
    var drEl2 = elOf(db, vcAcct(db, hdr.a_accumdepreciation_acct), d.absent, '{Transfer.AccumOld}');
    var crEl2 = elOf(db, vcAcct(db, hdr.a_accumdepreciation_new_acct), d.absent, '{Transfer.AccumNew}');
    var amt2 = cents(wk.a_accumulated_depr);
    if (drEl2) d.add('DR', drEl2, amt2);
    if (crEl2) d.add('CR', crEl2, amt2);
  }
  return d;
}

// Doc_AssetDisposed.createFacts:65-86 — from the A_Asset_Change 'DIS' row OF THIS SCHEMA (per-schema
// amounts, MAssetChange.get with C_AcctSchema_ID): CR AssetAcct=AssetValueAmt / DR AccumDep=
// AssetAccumDepreciationAmt / DR DisposalLoss=AssetBookValueAmt. Schema currency, no conversion.
function deriveAssetDisposed(db, id, schema) {
  var hdr = getRow(db, 'SELECT * FROM a_asset_disposed WHERE a_asset_disposed_id=?', num(id));
  if (!hdr) return null;
  var d = b3New();
  var ch = getRow(db, "SELECT * FROM a_asset_change WHERE a_asset_id=? AND changetype='DIS' AND c_acctschema_id=? ORDER BY a_asset_change_id LIMIT 1",
    [num(hdr.a_asset_id), num(schema)]);
  if (!ch) { d.absent.push('a_asset_change#DIS/' + hdr.a_asset_id + '/' + schema); return d; }
  var acct = assetAcctFor(db, hdr.a_asset_id, schema, hdr.dateacct);
  if (!acct) { d.absent.push('a_asset_acct#' + hdr.a_asset_id + '/' + schema); return d; }
  var assetEl = elOf(db, vcAcct(db, acct.a_asset_acct), d.absent, '{AssetAcct.Asset}');
  var accumEl = elOf(db, vcAcct(db, acct.a_accumdepreciation_acct), d.absent, '{AssetAcct.AccumDep}');
  var lossEl = elOf(db, vcAcct(db, acct.a_disposal_loss_acct), d.absent, '{AssetAcct.DisposalLoss}');
  if (assetEl) d.add('CR', assetEl, cents(ch.assetvalueamt));
  if (accumEl) d.add('DR', accumEl, cents(ch.assetaccumdepreciationamt));
  if (lossEl) d.add('DR', lossEl, cents(ch.assetbookvalueamt));
  return d;
}

// Doc_ProjectIssue.createFacts:125-199 — DR project WIP acct (Asset acct if ProjectCategory='A') /
// CR product Asset acct (Expense if service) at COST: m_inoutline POCost | timeexpense laborCost |
// else the product's current cost × qty (schema costingmethod → cost element → m_cost.currentcostprice,
// the W-FOLD-MOVEMENT hop). Per-schema cost, schema currency.
function deriveProjectIssue(db, id, schema) {
  var hdr = getRow(db, 'SELECT * FROM c_projectissue WHERE c_projectissue_id=?', num(id));
  if (!hdr) return null;
  var d = b3New();
  if (num(hdr.m_inoutline_id) > 0) { d.absent.push('POCost#m_inoutline=' + hdr.m_inoutline_id); return d; }
  if (num(hdr.s_timeexpenseline_id) > 0) { d.absent.push('LaborCost#tel=' + hdr.s_timeexpenseline_id); return d; }
  var as = schemaRow(db, schema);
  var cost = getRow(db,
    "SELECT c.currentcostprice AS p FROM m_cost c JOIN m_costelement e ON e.m_costelement_id=c.m_costelement_id" +
    " AND e.costelementtype='M' AND e.costingmethod=? WHERE c.m_product_id=? AND c.c_acctschema_id=? AND c.m_costtype_id=?",
    [as ? String(as.costingmethod) : '', num(hdr.m_product_id), num(schema), as ? num(as.m_costtype_id) : 0]);
  var amt = Math.round(cents(cost ? cost.p : 0) * Number(hdr.movementqty));
  var prj = getRow(db, 'SELECT projectcategory FROM c_project WHERE c_project_id=?', num(hdr.c_project_id));
  var col = (prj && prj.projectcategory === 'A') ? 'pj_asset_acct' : 'pj_wip_acct';
  var pa = getRow(db, 'SELECT ' + col + ' AS acct FROM c_project_acct WHERE c_project_id=? AND c_acctschema_id=?', [num(hdr.c_project_id), num(schema)]);
  var prod = getRow(db, 'SELECT producttype, m_product_category_id FROM m_product WHERE m_product_id=?', num(hdr.m_product_id));
  var isService = prod && prod.producttype === 'S';
  var pcol = isService ? 'p_expense_acct' : 'p_asset_acct';
  var pacct = prod ? getRow(db, 'SELECT ' + pcol + ' AS acct FROM m_product_category_acct WHERE m_product_category_id=? AND c_acctschema_id=?', [num(prod.m_product_category_id), num(schema)]) : null;
  var drEl = elOf(db, vcAcct(db, pa && pa.acct), d.absent, '{Project.' + (col === 'pj_asset_acct' ? 'Asset' : 'WIP') + '}');
  var crEl = elOf(db, vcAcct(db, pacct && pacct.acct), d.absent, '{Product.' + (isService ? 'Expense' : 'Asset') + '}');
  if (drEl) d.add('DR', drEl, amt);
  if (crEl) d.add('CR', crEl, amt);
  return d;
}

function finish(d, basis) {
  if (!d) return { lines: [], balanced: false, sumDr: 0, sumCr: 0, absent: [], basis: 'none' };
  var lines = Object.keys(d.by).map(function (k) {
    var a = d.by[k];
    return { account_id: a.account_id, value: a.value, name: a.name, amtacctdr: a.dr / 100, amtacctcr: a.cr / 100 };
  });
  var sumDr = 0, sumCr = 0;
  Object.keys(d.by).forEach(function (k) { sumDr += d.by[k].dr; sumCr += d.by[k].cr; });
  return { lines: lines, balanced: lines.length > 0 && sumDr === sumCr, sumDr: sumDr, sumCr: sumCr, absent: d.absent, basis: basis };
}

/**
 * derivePostings(db, recordRef, schema, R) -> the fold for the doc the action would post.
 *   recordRef = { table:'C_Order'|'C_Invoice', id }. C_Order → its generated invoice manifest (oracle
 *   path); a true-draft order with no invoice → the projected order manifest (basis='order', no oracle).
 *   Shipment COGS/Inventory leg is the §8 follow-up (cost data named-deferred in seed) — NOT in this slice.
 */
function derivePostings(db, recordRef, schema, R) {
  R = R || _R();
  if (!R) throw new Error('doc_poster: post_resolver (R) unavailable');
  var table = recordRef.table || recordRef.doc_type;
  var id = num(recordRef.id != null ? recordRef.id : recordRef.record_id);
  if (table === 'C_Invoice') return finish(deriveInvoice(db, R, id, schema), 'invoice');
  if (table === 'C_Order') {
    var invId = invoiceForOrder(db, id);
    if (invId != null) return finish(deriveInvoice(db, R, invId, schema), 'invoice');
    return finish(deriveOrder(db, R, id, schema), 'order');           // draft projection — no oracle
  }
  // B-3 0-seed classes (W-POST-B3 §W-3) — these read per-asset/project acct config, not R tokens
  if (table === 'A_Asset_Addition') return finish(deriveAssetAddition(db, id, schema), 'fa-addition');
  if (table === 'A_Depreciation_Entry') return finish(deriveDepreciationEntry(db, id, schema), 'fa-depreciation');
  if (table === 'A_Asset_Reval') return finish(deriveAssetReval(db, id, schema), 'fa-reval');
  if (table === 'A_Asset_Transfer') return finish(deriveAssetTransfer(db, id, schema, recordRef.primarySchema), 'fa-transfer');
  if (table === 'A_Asset_Disposed') return finish(deriveAssetDisposed(db, id, schema), 'fa-disposal');
  if (table === 'C_ProjectIssue') return finish(deriveProjectIssue(db, id, schema), 'project-issue');
  return { lines: [], balanced: false, sumDr: 0, sumCr: 0, absent: [], basis: 'none' };
}

function _R() { try { return (typeof require !== 'undefined') ? require('./post_resolver') : null; } catch (e) { return null; } }

var _api = { derivePostings: derivePostings, deriveInvoice: deriveInvoice, deriveOrder: deriveOrder, invoiceForOrder: invoiceForOrder };
// UMD tail — node (require) + browser live host (window.DocPoster). erp_preview.js injects window.PostResolver as R.
if (typeof module !== 'undefined' && module.exports) { module.exports = _api; }
if (typeof window !== 'undefined') { window.DocPoster = _api; }
