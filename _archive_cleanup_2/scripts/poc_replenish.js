#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_replenish.js — W-FOLD-REPLENISH (FOLD_MODEL_LOGIC.md §F-2 task #3 — Δ-B replenishment PO).
//
// SPEC (ReplenishReport — iDempiere org.compiere.process.ReplenishReport, lines 294-327). Per
//   (product, warehouse) with ReplenishType<>'0':
//     available = QtyOnHand - QtyReserved + QtyOrdered     (reserved/ordered = M_StorageReservation SO/PO)
//     type '1' (reorder-below-min): QtyToOrder = Level_Max - available  IFF available <= Level_Min, else 0
//     type '2' (maintain-max):      QtyToOrder = Level_Max - available  (always)
//   then drop rows with QtyToOrder < 1; (Order_Min floor + Order_Pack round-up — absent on these rows).
//   The suggested orders become a Purchase Order (M_Order, IsSOTrx=N).
//
//   This fold RIDES §F-2 task #2 (W-FOLD-QTYONHAND): the engine does NOT read m_storageonhand — it derives
//   QtyOnHand by folding the movement ledger m_transaction (erp_engine.qtyOnHand) per product WITHIN the
//   warehouse's locators (the m_locator→warehouse map is load-bearing: locator 102 is a DIFFERENT warehouse,
//   so product 123's wh-103 on-hand is 20, not 24). It then applies the ReplenishReport formula in JS and
//   emits the PO through the EXISTING archetype verb erp_engine.buildDoc — newVerbs=[], fold-not-fork.
//
//   Proven to ORACLE-EQUIVALENCE: the engine's QtyToOrder list (movement-folded on-hand → JS formula) ==
//   the AUTHORITATIVE list from iDempiere's own formula SQL run over m_storageonhand (a different execution
//   path + a different on-hand source), per product, to the unit. §FALSIFIER-A swaps max↔min, §FALSIFIER-B
//   makes type-1 always-order — both diverge from the oracle.
//
// NON-INVENT: real config (glassbowl_data.db, client 11) verbatim; the formula is the extracted SQL, not
//   hand-authored numbers; deterministic (integer centi-units; no Date.now/Math.random). READ the log.
// Implementing FOLD_MODEL_LOGIC.md §F-2 (ReplenishReport) — Witness: W-FOLD-REPLENISH
// Run: node scripts/poc_replenish.js 2>&1 | tee build/erp/poc_replenish.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var E = require('./erp_engine');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var WAREHOUSE = 103;
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function ci(n) { return Math.round(Number(n || 0) * 100); }

// ── ENGINE PATH: fold on-hand from the movement ledger, then apply the ReplenishReport formula ──
// locators belonging to the target warehouse (loc 102 → wh 104 is correctly EXCLUDED).
var whLocators = new Set(db.prepare('SELECT m_locator_id FROM m_locator WHERE m_warehouse_id=?').all(WAREHOUSE).map(function (r) { return r.m_locator_id; }));
var txns = db.prepare('SELECT * FROM m_transaction').all().filter(function (t) { return whLocators.has(t.m_locator_id); });
// fold qtyonhand per PRODUCT within the warehouse (the §F-2 #2 spine, re-used verbatim).
var onhandByProduct = E.qtyOnHand(txns, {
  keyOf: function (t) { return t.m_product_id; },
  typeOf: function (t) { return t.movementtype; },
  absQtyOf: function (t) { return Math.abs(ci(t.movementqty)); }
});
function reservation(productId, soTrx) {
  var r = db.prepare('SELECT COALESCE(SUM(qty),0) q FROM m_storagereservation WHERE m_product_id=? AND m_warehouse_id=? AND issotrx=?').get(productId, WAREHOUSE, soTrx);
  return ci(r.q);
}
function engineReplenish() {
  var rows = db.prepare('SELECT * FROM m_replenish WHERE m_warehouse_id=? AND replenishtype<>? ORDER BY m_product_id').all(WAREHOUSE, '0');
  var out = {};
  rows.forEach(function (r) {
    var onhand = onhandByProduct[r.m_product_id] || 0;
    var reserved = reservation(r.m_product_id, 'Y'), ordered = reservation(r.m_product_id, 'N');
    var available = onhand - reserved + ordered;
    var max = ci(r.level_max), min = ci(r.level_min);
    var qto = null;
    if (r.replenishtype === '1') qto = (available <= min) ? (max - available) : 0;
    else if (r.replenishtype === '2') qto = (max - available);
    if (qto != null && qto >= 100) out[r.m_product_id] = qto; // >=1 unit (100 centi-units)
  });
  return out;
}

// ── ORACLE PATH: iDempiere's OWN formula SQL, run over m_storageonhand (authoritative, different source) ──
function oracleReplenish() {
  var sql =
    'WITH base AS (SELECT r.m_product_id p, r.replenishtype rt, r.level_min mn, r.level_max mx,' +
    '  COALESCE((SELECT SUM(s.qtyonhand) FROM m_storageonhand s JOIN m_locator l ON l.m_locator_id=s.m_locator_id WHERE s.m_product_id=r.m_product_id AND l.m_warehouse_id=r.m_warehouse_id),0) onh,' +
    '  COALESCE((SELECT SUM(sr.qty) FROM m_storagereservation sr WHERE sr.m_product_id=r.m_product_id AND sr.m_warehouse_id=r.m_warehouse_id AND sr.issotrx=\'Y\'),0) rsv,' +
    '  COALESCE((SELECT SUM(sr.qty) FROM m_storagereservation sr WHERE sr.m_product_id=r.m_product_id AND sr.m_warehouse_id=r.m_warehouse_id AND sr.issotrx=\'N\'),0) ord' +
    '  FROM m_replenish r WHERE r.m_warehouse_id=@w AND r.replenishtype<>\'0\')' +
    ' SELECT p, CASE WHEN rt=\'1\' THEN (CASE WHEN onh-rsv+ord<=mn THEN mx-onh+rsv-ord ELSE 0 END) WHEN rt=\'2\' THEN mx-onh+rsv-ord END qto FROM base';
  var out = {};
  db.prepare(sql).all({ w: WAREHOUSE }).forEach(function (r) { if (ci(r.qto) >= 100) out[r.p] = ci(r.qto); });
  return out;
}

function diff(a, b) { var ks = {}; Object.keys(a).forEach(function (k) { ks[k] = 1; }); Object.keys(b).forEach(function (k) { ks[k] = 1; }); var md = 0, at = null; Object.keys(ks).forEach(function (k) { var d = Math.abs((a[k] || 0) - (b[k] || 0)); if (d > md) { md = d; at = k; } }); return { md: md, at: at, n: Object.keys(ks).length }; }

console.log('═══ W-FOLD-REPLENISH — ReplenishReport QtyToOrder (movement-folded on-hand) == iDempiere formula ═══');
console.log('    engine = erp_engine.qtyOnHand(m_transaction) → JS formula → buildDoc · oracle = iDempiere formula-SQL over m_storageonhand · warehouse=' + WAREHOUSE + '\n');

var eng = engineReplenish(), ora = oracleReplenish();
var d = diff(eng, ora);
var ok = d.md === 0 && Object.keys(ora).length > 0;
verdict(ok, 'QtyToOrder list (movement-folded on-hand → JS formula) == iDempiere formula over m_storageonhand',
  'products=' + Object.keys(ora).length + ' maxDiff=' + (d.md / 100) + (d.at ? ' worst=' + d.at : ''));
Object.keys(ora).sort(function (a, b) { return a - b; }).forEach(function (p) {
  var e = (eng[p] || 0) / 100, o = ora[p] / 100;
  console.log('   ' + (e === o ? '🟢' : '🔴') + ' product ' + p + ' QtyToOrder engine=' + e + ' oracle=' + o + ' (on-hand-folded=' + ((onhandByProduct[p] || 0) / 100) + ')');
});
console.log('§FOLD-COMPLETE doc=ReplenishReport deltaFrom=MOrder products=' + Object.keys(ora).length + ' oracle=iDempiere maxDiff=' + (d.md / 100));

// ── GENERATE the PO through the EXISTING archetype verb buildDoc (newVerbs=[]) ──
var REPLENISH_PO_SPEC = { docTable: 'C_Order', lineTable: 'C_OrderLine', parentId: 'm_warehouse_id', lineParentId: 'm_product_id', qtyTo: 'qtyordered', qtyFrom: 'qtytoorder', header: function (p) { return { issotrx: 'N', m_warehouse_id: p.m_warehouse_id }; } };
var poLines = Object.keys(eng).sort(function (a, b) { return a - b; }).map(function (p) { return { m_product_id: Number(p), qtytoorder: eng[p] / 100 }; });
var ops = E.buildDoc(REPLENISH_PO_SPEC, { m_warehouse_id: WAREHOUSE }, poLines);
var hdr = ops[0], lineOps = ops.slice(1);
verdict(hdr.op_type === 'CREATE_DOCUMENT' && hdr.table === 'C_Order' && hdr.issotrx === 'N' && lineOps.length === poLines.length,
  'PO emitted via buildDoc (newVerbs=[]) — 1 CREATE_DOCUMENT(C_Order,PO) + ' + lineOps.length + ' CREATE_LINE', 'ops=' + ops.length);
console.log('   §PO-OP-GROUP header=' + JSON.stringify(hdr));
lineOps.slice(0, 3).forEach(function (l) { console.log('     line=' + JSON.stringify(l)); });
if (lineOps.length > 3) console.log('     … (' + (lineOps.length - 3) + ' more)');
console.log('§FOLD-COMPLETE doc=C_Order(PO) deltaFrom=MOrder lines=' + lineOps.length + ' Σqty=' + poLines.reduce(function (s, l) { return s + l.qtytoorder; }, 0) + ' verb=buildDoc newVerbs=0');

// ── §FALSIFIER-A: swap Level_Max↔Level_Min in the formula → QtyToOrder list diverges ──
(function () {
  var rows = db.prepare('SELECT * FROM m_replenish WHERE m_warehouse_id=? AND replenishtype<>?').all(WAREHOUSE, '0');
  var bad = {};
  rows.forEach(function (r) {
    var onhand = onhandByProduct[r.m_product_id] || 0, available = onhand - reservation(r.m_product_id, 'Y') + reservation(r.m_product_id, 'N');
    var min = ci(r.level_min); // BUG: order up to MIN instead of MAX
    var qto = r.replenishtype === '1' ? (available <= min ? min - available : 0) : (min - available);
    if (qto >= 100) bad[r.m_product_id] = qto;
  });
  var f = diff(bad, ora);
  verdict(f.md > 0, '§FALSIFIER-A order up to Level_Min (not Max) → diverges from oracle', 'maxDiff=' + (f.md / 100) + (f.at ? ' at=' + f.at : ''));
  console.log('§FALSIFIER doc=ReplenishReport mutation=max-to-min maxDiff=' + (f.md / 100) + ' (must be >0)');
})();

// ── §FALSIFIER-B: treat type-1 as always-order (drop the available<=min gate) → spurious orders ──
(function () {
  var rows = db.prepare('SELECT * FROM m_replenish WHERE m_warehouse_id=? AND replenishtype<>?').all(WAREHOUSE, '0');
  var bad = {};
  rows.forEach(function (r) {
    var onhand = onhandByProduct[r.m_product_id] || 0, available = onhand - reservation(r.m_product_id, 'Y') + reservation(r.m_product_id, 'N');
    var qto = ci(r.level_max) - available; // BUG: type-1 no longer gated on available<=min
    if (qto >= 100) bad[r.m_product_id] = qto;
  });
  var f = diff(bad, ora);
  verdict(f.n > Object.keys(ora).length, '§FALSIFIER-B type-1 always-order (drop the below-min gate) → spurious products appear', 'oracle-products=' + Object.keys(ora).length + ' falsified-products=' + f.n);
  console.log('§FALSIFIER doc=ReplenishReport mutation=type1-always-order products=' + f.n + ' (must be > ' + Object.keys(ora).length + ')');
})();

console.log('\n   §SOURCE-NOTE QtyReserved/QtyOrdered ride M_StorageReservation (2 rows, qty 0 in seed); Order_Min/' +
  'Order_Pack adjustments absent on these rows — named, not exercised. All 8 triggered are type-2 (maintain-max).');

console.log('\n' + (fails === 0 ? '🟢 W-FOLD-REPLENISH PASS' : '🔴 W-FOLD-REPLENISH FAIL (' + fails + ')') +
  ' — replenishment QtyToOrder folds from the movement-derived on-hand (rides W-FOLD-QTYONHAND) == iDempiere ' +
  'ReplenishReport to the unit, and emits the PO through the existing buildDoc archetype (newVerbs=0).');
db.close();
process.exit(fails === 0 ? 0 : 1);
