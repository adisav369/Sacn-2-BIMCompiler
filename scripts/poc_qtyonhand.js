#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_qtyonhand.js — W-FOLD-QTYONHAND (FOLD_MODEL_LOGIC.md §F-2 task #2 — the StorageOnHand QTY spine).
//
// SPEC (MStorageOnHand / MTransaction — iDempiere org.compiere.model.MTransaction + MStorageOnHand):
//   Inventory quantity is stored NOWHERE as a master field — it is a FOLD of every inventory movement.
//   Each completed movement document (MInOut receipt/shipment, MMovement transfer, MInventory count,
//   MProduction) records ONE MTransaction row and calls MStorageOnHand.add(qty) in lockstep. The engine
//   logic is the SIGN CONVENTION: a MovementType code carries its polarity in its TRAILING CHAR
//   ('+' = into stock, '-' = out of stock), so a vendor receipt V+ ADDS and a customer shipment C-
//   SUBTRACTS the same positive line qty. On-hand(product,locator,asi) = Σ ( sign(movementtype) × |qty| ).
//
//   The fold (erp_engine.qtyOnHand + movementSign) reconstructs the signed contribution from
//   (movementtype, |movementqty|) — it does NOT trust a pre-signed column — and accumulates per cell.
//   Proven to ORACLE-EQUIVALENCE on TWO independent oracles:
//     (a) SIGN RULE     — per event, reconstructed signed qty == the ledger's stored movementqty.
//     (b) ACCUMULATION  — Σ per (product,locator,asi) == m_storageonhand.qtyonhand, maxDiff=0.
//   These are separate iDempiere code paths (MTransaction.create vs MStorageOnHand.add), so the diff is a
//   real equivalence check (no movement dropped or double-counted), not a tautology.
//
//   SOURCE DECOMPOSITION — each MTransaction names its source line (m_inoutline / m_movementline /
//   m_inventoryline / m_productionline), so on-hand is attributed to receipt/shipment/movement/inventory/
//   production. THIS is the spine the backflush DECREMENT (W-FOLD-BACKFLUSH) and the replenishment trigger
//   (task #3) ride: a production P- consumes leaves, a replenishment PO lands a V+. In GardenWorld's seed
//   only V+/C-/M± occur; MInventory + MProduction movements are absent (m_production=0) — NAMED, not folded.
//
// NON-INVENT: the real ledger (glassbowl_data.db, client 11), verbatim; deterministic (qty in integer
//   centi-units, no float accumulation; no Date.now/Math.random). READ build/erp/poc_qtyonhand.log.
// Implementing FOLD_MODEL_LOGIC.md §F-2 (MStorageOnHand) — Witness: W-FOLD-QTYONHAND
// Run: node scripts/poc_qtyonhand.js 2>&1 | tee build/erp/poc_qtyonhand.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var E = require('./erp_engine');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function ci(n) { return Math.round(Number(n || 0) * 100); }  // qty → integer centi-units (deterministic)
function cellKey(t) { return t.m_product_id + '/' + t.m_locator_id + '/' + t.m_attributesetinstance_id; }
function maxDiff(da, o) { var ks = {}; Object.keys(da).forEach(function (k) { ks[k] = 1; }); Object.keys(o).forEach(function (k) { ks[k] = 1; }); var md = 0, at = null; Object.keys(ks).forEach(function (k) { var d = Math.abs((da[k] || 0) - (o[k] || 0)); if (d > md) { md = d; at = k; } }); return { md: md, at: at }; }

var txns = db.prepare('SELECT * FROM m_transaction ORDER BY m_transaction_id').all();
var soh = {}; db.prepare('SELECT m_product_id,m_locator_id,m_attributesetinstance_id,qtyonhand FROM m_storageonhand').all()
  .forEach(function (r) { soh[r.m_product_id + '/' + r.m_locator_id + '/' + r.m_attributesetinstance_id] = ci(r.qtyonhand); });

console.log('═══ W-FOLD-QTYONHAND — on-hand = Σ(sign(MovementType)×|qty|) == iDempiere m_storageonhand ═══');
console.log('    fold = erp_engine.qtyOnHand + movementSign · ledger = m_transaction(' + txns.length + ') · oracle = m_storageonhand(' + Object.keys(soh).length + ')\n');

// ── (a) SIGN RULE: per event, engine's reconstructed signed qty == stored movementqty ──
var signMismatch = 0;
txns.forEach(function (t) {
  var reconstructed = E.movementSign(t.movementtype) * Math.abs(ci(t.movementqty));
  if (reconstructed !== ci(t.movementqty)) { signMismatch++; }
});
verdict(signMismatch === 0, '(a) SIGN RULE — sign(MovementType)×|qty| reproduces the stored signed ledger for every event',
  'events=' + txns.length + ' mismatches=' + signMismatch);

// ── (b) ACCUMULATION: engine fold per cell == m_storageonhand.qtyonhand ──
var derived = E.qtyOnHand(txns, {
  keyOf: cellKey,
  typeOf: function (t) { return t.movementtype; },
  absQtyOf: function (t) { return Math.abs(ci(t.movementqty)); }
});
var d = maxDiff(derived, soh);
var cells = Object.keys(soh).length;
verdict(d.md === 0 && cells > 0, '(b) ACCUMULATION — Σ signed movement per (product,locator,asi) == qtyonhand',
  'cells=' + cells + ' maxDiff=' + (d.md / 100) + (d.at ? ' worst-cell=' + d.at : ''));
console.log('§FOLD-COMPLETE doc=M_StorageOnHand deltaFrom=MOrder cells=' + cells + ' events=' + txns.length + ' Σqty=' + (Object.keys(derived).reduce(function (s, k) { return s + derived[k]; }, 0) / 100) + ' oracle=iDempiere maxDiff=' + (d.md / 100));

// per-cell witness (so the log shows the real numbers, not just an aggregate)
Object.keys(soh).sort().forEach(function (k) { var a = (derived[k] || 0) / 100, b = soh[k] / 100; if (a !== b) console.log('   🔴 cell ' + k + ' derived=' + a + ' oracle=' + b); });

// ── SOURCE DECOMPOSITION — attribute every movement to its document type ──
var bySource = {};
txns.forEach(function (t) {
  var src = t.m_inoutline_id ? (E.movementSign(t.movementtype) > 0 ? 'receipt(V+)' : 'shipment(C-)')
    : t.m_movementline_id ? 'movement(M±)'
      : t.m_inventoryline_id ? 'inventory(I±)'
        : t.m_productionline_id ? 'production(P±)' : 'opening/none';
  bySource[src] = bySource[src] || { n: 0, qty: 0 };
  bySource[src].n++; bySource[src].qty += ci(t.movementqty);  // movementqty is stored signed (sign rule proven in (a))
});
console.log('\n   source decomposition (Σ on-hand by movement origin):');
Object.keys(bySource).forEach(function (s) { console.log('     ' + s.padEnd(14) + ' events=' + bySource[s].n + ' Σ=' + (bySource[s].qty / 100)); });
console.log('   §SOURCE-DEFERRED inventory(I±) + production(P±) absent in seed (m_production=0) — the backflush ' +
  'DECREMENT and replenishment trigger ride THIS spine; named, not folded.');

// ── §FALSIFIER A: flip one C- shipment polarity to '+' → its cell diff must blow up ──
(function () {
  var shipment = txns.find(function (t) { return t.movementtype === 'C-'; });
  var mutated = txns.map(function (t) { return t === shipment ? Object.assign({}, t, { movementtype: 'C+' }) : t; });
  var dd = E.qtyOnHand(mutated, { keyOf: cellKey, typeOf: function (t) { return t.movementtype; }, absQtyOf: function (t) { return Math.abs(ci(t.movementqty)); } });
  var f = maxDiff(dd, soh);
  verdict(f.md > 0, '§FALSIFIER-A flip one C- shipment to C+ (wrong polarity) → on-hand diverges', 'maxDiff=' + (f.md / 100) + ' at=' + f.at);
  console.log('§FALSIFIER doc=M_StorageOnHand mutation=flip-shipment-polarity maxDiff=' + (f.md / 100) + ' (must be >0)');
})();

// ── §FALSIFIER B: drop the internal M- movement → the relocated cell-pair no longer balances ──
(function () {
  var moved = txns.filter(function (t) { return t.movementtype !== 'M-'; });
  var dd = E.qtyOnHand(moved, { keyOf: cellKey, typeOf: function (t) { return t.movementtype; }, absQtyOf: function (t) { return Math.abs(ci(t.movementqty)); } });
  var f = maxDiff(dd, soh);
  verdict(f.md > 0, '§FALSIFIER-B drop the internal M- movement → the source locator over-counts', 'maxDiff=' + (f.md / 100) + ' at=' + f.at);
  console.log('§FALSIFIER doc=M_StorageOnHand mutation=drop-internal-movement maxDiff=' + (f.md / 100) + ' (must be >0)');
})();

console.log('\n' + (fails === 0 ? '🟢 W-FOLD-QTYONHAND PASS' : '🔴 W-FOLD-QTYONHAND FAIL (' + fails + ')') +
  ' — on-hand qty folds from the movement ledger (sign rule + accumulation) == iDempiere m_storageonhand to the unit. ' +
  'The inventory spine is GREEN; backflush/replenishment deltas ride it.');
db.close();
process.exit(fails === 0 ? 0 : 1);
