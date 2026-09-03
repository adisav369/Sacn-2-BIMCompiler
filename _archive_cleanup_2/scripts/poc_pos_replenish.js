#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (POS_LENS_SESSION.md / POS_ADDON_SPEC.md §P-4). Read the log after every run.
// poc_pos_replenish.js — W-POS-REPLENISH: the loop closes — a sale drives the replenishment suggestion.
//
// ISSUES IT PROVES (named):
//   1. BASELINE ORACLE — POSCore.replenishSuggest (qtyOnHand fold over m_transaction → the
//      ReplenishReport:294-327 formula) == iDempiere's OWN formula SQL over m_storageonhand, per
//      product to the unit, on THIS seed (the W-FOLD-REPLENISH equivalence, re-anchored on
//      ad_seed_fullwidth.db where the POS dictionary lives).
//   2. SELL-BELOW-MIN — a type-1 product ABOVE Level_Min is absent from the suggestions; ring enough
//      of it and it APPEARS with the formula-derived qty (the spec's witness clause verbatim).
//   3. THE TAIL (the moat) — ring a FINISHED GOOD: the backflush consumption (not the sale line!)
//      drives a COMPONENT below its min → the component is suggested. AutoBOMOrder → ReplenishReport,
//      one fold chain, zero new machinery.
//   4. PO VIA THE ARCHETYPE — one tap = buildReplenishPO → CREATE_DOCUMENT C_Order IsSOTrx=N +
//      CREATE_LINE per suggestion (the same REPLENISH_PO_SPEC poc_replenish.js proved; newVerbs=[]).
//   5. §FALSIFIER (the spec's) — selling a product with NO m_replenish row never suggests.
//
// SEED HONESTY (§-named): the station c_pos(100) ships warehouse 104, but ALL m_replenish rows AND all
//   m_transaction ledger rows in this seed live at warehouse 103 — so the crossing legs ring with an
//   explicit warehouseId=103 override (real policy + real ledger; the 104 fold is honest-empty).
// NON-INVENT: policy/ledger/recipes = ad_seed_fullwidth.db verbatim; the formula is the extracted
//   ReplenishReport SQL; centi-unit integers; no Date.now/Math.random.
// Implementing docs/POS_ADDON_SPEC.md §P-4 — Witness: W-POS-REPLENISH
// Run: bash build/erp/run_witness.sh scripts/poc_pos_replenish.js   — then READ the log.
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var POS = require(path.join(__dirname, '..', 'build', 'erp', 'pos_core.js'));

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'ad_seed_fullwidth.db'), { readonly: true });
var WH = 103;   // the policy+ledger warehouse (see SEED HONESTY above)
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function lc(r) { if (!r) return r; var o = {}; for (var k in r) o[k.toLowerCase()] = r[k]; return o; }
function lcAll(rows) { return rows.map(lc); }
function ci(n) { return Math.round(Number(n || 0) * 100); }
var nameStmt = db.prepare('SELECT name FROM m_product WHERE m_product_id=?');
function nameOf(pid) { var r = lc(nameStmt.get(Number(pid))); return r ? r.name : ('#' + pid); }

console.log('═══ W-POS-REPLENISH — sale → fold → suggestion: the 2012 loop closes (ReplenishReport == oracle) ═══\n');

// ── the replenish ctx (warehouse-scoped ledger + policy, EXTRACTED) ──
var whLocs = new Set(db.prepare('SELECT m_locator_id AS i FROM m_locator WHERE m_warehouse_id=?').all(WH).map(function (r) { return lc(r).i; }));
var txns = lcAll(db.prepare('SELECT m_product_id, m_locator_id, movementtype, movementqty FROM m_transaction').all())
  .filter(function (t) { return whLocs.has(t.m_locator_id); });
var replRows = lcAll(db.prepare("SELECT m_product_id, m_warehouse_id, level_min, level_max, replenishtype FROM m_replenish WHERE m_warehouse_id=? AND replenishtype<>'0' ORDER BY m_product_id").all(WH));
var resStmt = db.prepare('SELECT COALESCE(SUM(qty),0) AS q FROM m_storagereservation WHERE m_product_id=? AND m_warehouse_id=? AND issotrx=?');
var rctx = { replenishRows: replRows, txns: txns, reservation: function (pid, so) { return ci(lc(resStmt.get(pid, WH, so)).q); } };

// ── the station ctx (rings ship at WH per the SEED HONESTY note) ──
var pos = lc(db.prepare('SELECT * FROM c_pos WHERE c_pos_id=100').get());
var plv = lc(db.prepare('SELECT m_pricelist_version_id v FROM m_pricelist_version WHERE m_pricelist_id=?').get(pos.m_pricelist_id));
var priceStmt = db.prepare('SELECT pricestd FROM m_productprice WHERE m_pricelist_version_id=? AND m_product_id=?');
var bomStmt = db.prepare('SELECT bl.m_product_id AS comp_id, bl.qtybom AS qtybom FROM pp_product_bomline bl JOIN pp_product_bom b ON b.pp_product_bom_id=bl.pp_product_bom_id WHERE b.m_product_id=? ORDER BY bl.m_product_id');
var ctx = { pos: pos, priceOf: function (pid) { return lc(priceStmt.get(plv.v, pid)) || null; }, bomOf: function (pid) { return bomStmt.all(Number(pid)).map(lc); }, wrPolicy: { isautogenerateinout: 'Y', isautogenerateinvoice: 'Y' } };
console.log('§POS-REPLENISH note=station-wh(' + pos.m_warehouse_id + ') has no m_replenish/m_transaction rows in seed → crossing legs ring warehouseId=' + WH + ' (the policy warehouse), honest-empty elsewhere\n');

// ── 1. BASELINE: engine fold == iDempiere's own formula SQL over m_storageonhand ──
var base = POS.replenishSuggest(rctx);
var oracleSql =
  'WITH base AS (SELECT r.m_product_id p, r.replenishtype rt, r.level_min mn, r.level_max mx,' +
  '  COALESCE((SELECT SUM(s.qtyonhand) FROM m_storageonhand s JOIN m_locator l ON l.m_locator_id=s.m_locator_id WHERE s.m_product_id=r.m_product_id AND l.m_warehouse_id=r.m_warehouse_id),0) onh,' +
  "  COALESCE((SELECT SUM(sr.qty) FROM m_storagereservation sr WHERE sr.m_product_id=r.m_product_id AND sr.m_warehouse_id=r.m_warehouse_id AND sr.issotrx='Y'),0) rsv," +
  "  COALESCE((SELECT SUM(sr.qty) FROM m_storagereservation sr WHERE sr.m_product_id=r.m_product_id AND sr.m_warehouse_id=r.m_warehouse_id AND sr.issotrx='N'),0) ord" +
  "  FROM m_replenish r WHERE r.m_warehouse_id=? AND r.replenishtype<>'0')" +
  " SELECT p, CASE WHEN rt='1' THEN (CASE WHEN onh-rsv+ord<=mn THEN mx-onh+rsv-ord ELSE 0 END) WHEN rt='2' THEN mx-onh+rsv-ord END qto FROM base";
var ora = {};
db.prepare(oracleSql).all(WH).forEach(function (r) { r = lc(r); if (ci(r.qto) >= 100) ora[r.p] = ci(r.qto) / 100; });
var baseMap = {}; base.forEach(function (s) { baseMap[s.m_product_id] = s.qtytoorder; });
var allKeys = Object.keys(Object.assign({}, ora, baseMap));
var maxDiff = 0; allKeys.forEach(function (k) { maxDiff = Math.max(maxDiff, Math.abs((baseMap[k] || 0) - (ora[k] || 0))); });
verdict(allKeys.length > 0 && maxDiff === 0, 'BASELINE: replenishSuggest (movement fold → formula) == iDempiere formula SQL over m_storageonhand', 'products=' + allKeys.length + ' maxDiff=' + maxDiff);
console.log('§POS-REPLENISH baseline products=' + Object.keys(ora).length + ' oracle=iDempiere-formula maxDiff=' + maxDiff);

// ── 2. SELL-BELOW-MIN: Elm Tree (124, type-1, min 10, available 15) — absent, then ring 6 → appears qty 11 ──
var ELM = 124;
var elm = replRows.filter(function (r) { return r.m_product_id === ELM; })[0];
verdict(elm && elm.replenishtype === '1' && !baseMap[ELM], nameOf(ELM) + '(' + ELM + ') type-1 min=' + elm.level_min + ' — ABOVE min, NOT suggested at baseline', 'suggested=' + (baseMap[ELM] || 'none'));
var g1 = POS.buildSaleGroup(ctx, [POS.ringLine(ctx, ELM, 6)], { orderId: 9201, inoutId: 9202, invoiceId: 9203, c_bpartner_id: 112, warehouseId: WH });
var after1 = POS.replenishSuggest(rctx, POS.saleMovements(g1));
var s1 = after1.filter(function (s) { return s.m_product_id === ELM; })[0];
// formula: available 15-6=9 ≤ min 10 → QtyToOrder = max 20 − 9 = 11
verdict(!!s1 && s1.qtytoorder === 11, 'ring ' + nameOf(ELM) + ' ×6 → available 9 ≤ min 10 → suggestion APPEARS, QtyToOrder=11 (= max 20 − 9)', 'got=' + (s1 && s1.qtytoorder));
console.log('§POS-REPLENISH trigger=onhand<min product=' + ELM + ' qty=' + (s1 && s1.qtytoorder) + ' po=suggested (formula-derived, suggest-by-default)');

// ── 3. THE TAIL: ring Patio Set ×2 → backflush consumes Patio Table ×2 → the COMPONENT crosses its min ──
var SET = 145, TABLE = 134;
var tbl = replRows.filter(function (r) { return r.m_product_id === TABLE; })[0];
verdict(tbl && !baseMap[TABLE], nameOf(TABLE) + '(' + TABLE + ') (a recipe LEAF) type-' + tbl.replenishtype + ' min=' + tbl.level_min + ' — NOT suggested at baseline', 'suggested=' + (baseMap[TABLE] || 'none'));
var g2 = POS.buildSaleGroup(ctx, [POS.ringLine(ctx, SET, 2)], { orderId: 9211, inoutId: 9212, invoiceId: 9213, c_bpartner_id: 112, warehouseId: WH });
var tableConsume = g2.ops.filter(function (o) { return o.op_type === 'CONSUME' && o.m_product_id === TABLE; })[0];
var after2 = POS.replenishSuggest(rctx, POS.saleMovements(g2));
var s2 = after2.filter(function (s) { return s.m_product_id === TABLE; })[0];
verdict(!!tableConsume && tableConsume.movementqty === 2, 'backflush consumes ' + nameOf(TABLE) + ' ×2 (1 per set — from the recipe, not the cart)', 'consume=' + (tableConsume && tableConsume.movementqty));
verdict(!!s2, 'THE TAIL: the CONSUMED component crosses its min → ' + nameOf(TABLE) + ' is suggested (backflush → replenishment, one fold chain)', 'qty=' + (s2 && s2.qtytoorder));
console.log('§POS-REPLENISH tail=backflush parent=' + SET + ' component=' + TABLE + ' consumed=2 → suggested qty=' + (s2 && s2.qtytoorder));

// ── 4. the PO through the archetype verb (suggest-by-default; one tap = this) ──
var po = POS.buildReplenishPO(WH, after1);
verdict(po[0].op_type === 'CREATE_DOCUMENT' && po[0].table === 'C_Order' && po[0].issotrx === 'N' && po.length === after1.length + 1,
  'PO via buildDoc(REPLENISH_PO_SPEC): CREATE_DOCUMENT C_Order(IsSOTrx=N) + ' + after1.length + ' CREATE_LINE — newVerbs=[]', 'ops=' + po.length);
console.log('§POS-REPLENISH po=built lines=' + (po.length - 1) + ' verb=buildDoc newVerbs=[]');

// ── 5. §FALSIFIER: a product with NO m_replenish row never suggests ──
var replSet = new Set(replRows.map(function (r) { return r.m_product_id; }));
var control = lcAll(db.prepare('SELECT m_product_id FROM c_poskey WHERE c_poskeylayout_id=? AND m_product_id IS NOT NULL ORDER BY c_poskey_id').all(pos.c_poskeylayout_id))
  .map(function (k) { return k.m_product_id; })
  .filter(function (pid) { return !replSet.has(pid) && ctx.bomOf(pid).length === 0 && ctx.priceOf(pid); })[0];
verdict(control != null, 'control tile exists: ' + nameOf(control) + '(' + control + ') — priced, non-BOM, NO m_replenish row');
var g3 = POS.buildSaleGroup(ctx, [POS.ringLine(ctx, control, 50)], { orderId: 9221, inoutId: 9222, invoiceId: 9223, c_bpartner_id: 112, warehouseId: WH });
var after3 = POS.replenishSuggest(rctx, POS.saleMovements(g3));
var same = JSON.stringify(after3) === JSON.stringify(base);
verdict(same, '§FALSIFIER selling ' + nameOf(control) + ' ×50 (no replenish policy) changes NOTHING — no invented reorder', 'suggestions=' + after3.length + ' == baseline=' + base.length);
console.log('§FALSIFIER pos=replenish product=' + control + ' policy-row=absent suggestionsChanged=' + (!same) + ' (must be false)');

console.log('\n' + (fails === 0 ? '🟢 W-POS-REPLENISH PASS' : '🔴 W-POS-REPLENISH FAIL (' + fails + ')') +
  ' — the suggestion fold equals the iDempiere formula, a sale below min makes it appear with the formula qty, the backflush tail replenishes a CONSUMED component, the PO rides buildDoc, and a no-policy product never suggests.');
db.close();
process.exit(fails === 0 ? 0 : 1);
