#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (POS_LENS_SESSION.md / POS_ADDON_SPEC.md §P-3). Read the log after every run.
// poc_pos_backflush.js — W-POS-BACKFLUSH: the WIRING of AutoBOMOrder into the sale group.
//
// ISSUES IT PROVES (named):
//   1. WIRING — ringing a BOM product puts CONSUME leaf ops INTO THE SAME GROUP as the sale
//      (POS_ADDON_SPEC §P-3 "emit the consumption movements in the SAME group"), and the leaf dict
//      equals BOTH E.explodeBOM called directly AND an independent path-enumeration (the W-FOLD-BACKFLUSH
//      oracle pattern, re-run on the SEED's real pp_product_bom/pp_product_bomline schema — the lens's
//      actual recipe source). A wiring that re-implements the explosion is a fork; equality proves the verb.
//   2. POLARITY — every CONSUME op is movementtype 'P-' so the qty spine (movementSign/qtyOnHand) folds
//      the consumption as a DECREMENT (the fold lane's proven P± polarity).
//   3. §FALSIFIER (the spec's) — a NON-BOM product must emit ZERO consumption ops: consuming components
//      a recipe never named is invented stock loss.
//
// NON-INVENT: recipe = real GardenWorld pp_product_bom/bomline (ad_seed_fullwidth.db); the rung product
//   (Patio Furniture Set 145) is a REAL c_poskey tile on the station layout. No Date.now/Math.random.
// Implementing docs/POS_ADDON_SPEC.md §P-3 — Witness: W-POS-BACKFLUSH
// Run: bash build/erp/run_witness.sh scripts/poc_pos_backflush.js   — then READ the log.
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var E = require('./erp_engine');
var POS = require(path.join(__dirname, '..', 'build', 'erp', 'pos_core.js'));

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'ad_seed_fullwidth.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function lc(r) { if (!r) return r; var o = {}; for (var k in r) o[k.toLowerCase()] = r[k]; return o; }

// ── host-injected recipe resolver over the SEED's REAL schema (the lens reads the same rows) ──
var bomStmt = db.prepare('SELECT bl.m_product_id AS comp_id, bl.qtybom AS qtybom FROM pp_product_bomline bl JOIN pp_product_bom b ON b.pp_product_bom_id=bl.pp_product_bom_id WHERE b.m_product_id=? ORDER BY bl.m_product_id');
function bomOf(pid) { return bomStmt.all(Number(pid)).map(lc); }
var nameStmt = db.prepare('SELECT name FROM m_product WHERE m_product_id=?');
function nameOf(pid) { var r = lc(nameStmt.get(Number(pid))); return r ? r.name : ('#' + pid); }

// the independent oracle: flat DFS path-enumeration (poc_backflush.js's, different impl from explodeBOM)
function pathEnumerate(pid, qty) {
  var out = {};
  (function walk(p, mult) {
    var lines = bomOf(p);
    if (!lines.length) { out[p] = (out[p] || 0) + mult; return; }
    lines.forEach(function (l) { walk(l.comp_id, mult * l.qtybom); });
  })(pid, qty);
  return out;
}
function eq(a, b) { var ak = Object.keys(a).sort(), bk = Object.keys(b).sort(); if (ak.join() !== bk.join()) return false; return ak.every(function (k) { return Math.abs(a[k] - b[k]) < 1e-9; }); }
function show(m) { return Object.keys(m).sort().map(function (k) { return nameOf(k) + '×' + m[k]; }).join(', '); }

console.log('═══ W-POS-BACKFLUSH — ring a BOM tile → CONSUME leaves in the SAME group == explodeBOM == path-enumeration ═══\n');

// ── station ctx (same reads as W-POS-RING/WR) ──
var pos = lc(db.prepare('SELECT * FROM c_pos WHERE c_pos_id=100').get());
var plv = lc(db.prepare('SELECT m_pricelist_version_id v FROM m_pricelist_version WHERE m_pricelist_id=?').get(pos.m_pricelist_id));
var priceStmt = db.prepare('SELECT pricestd FROM m_productprice WHERE m_pricelist_version_id=? AND m_product_id=?');
var ctx = { pos: pos, priceOf: function (pid) { return lc(priceStmt.get(plv.v, pid)) || null; }, bomOf: bomOf, wrPolicy: { isautogenerateinout: 'Y', isautogenerateinvoice: 'Y' } };

// the BOM product on a REAL key tile: Patio Furniture Set (145)
var PATIO = 145;
var key = lc(db.prepare('SELECT c_poskey_id FROM c_poskey WHERE c_poskeylayout_id=? AND m_product_id=?').get(pos.c_poskeylayout_id, PATIO));
verdict(!!key, nameOf(PATIO) + '(' + PATIO + ') is a REAL tile on layout ' + pos.c_poskeylayout_id + ' (c_poskey ' + (key && key.c_poskey_id) + ')');
var nested = bomOf(PATIO).some(function (l) { return bomOf(l.comp_id).length > 0; });
console.log('§BACKFLUSH-RECIPE parent=' + nameOf(PATIO) + ' direct=[' + bomOf(PATIO).map(function (l) { return nameOf(l.comp_id) + '×' + l.qtybom; }).join(', ') + '] nested=' + (nested ? 'Y' : 'N') + '\n');

// ── 1. the sale group carries the CONSUME leaves; dict == explodeBOM == path-enumeration ──
var cart = [POS.ringLine(ctx, PATIO, 1)];
var g = POS.buildSaleGroup(ctx, cart, { orderId: 9101, inoutId: 9102, invoiceId: 9103, c_bpartner_id: 112 });
var consumeOps = g.ops.filter(function (o) { return o.op_type === 'CONSUME'; });
var groupDict = {};
consumeOps.forEach(function (o) { groupDict[o.m_product_id] = (groupDict[o.m_product_id] || 0) + o.movementqty; });
var direct = E.explodeBOM(bomOf, PATIO, 1);
var oracle = pathEnumerate(PATIO, 1);
verdict(eq(groupDict, direct), 'group CONSUME ops == E.explodeBOM(' + PATIO + ', 1) — the wiring rides the verb', 'leaves=' + consumeOps.length);
verdict(eq(direct, oracle), 'explodeBOM == independent path-enumeration (two impls agree' + (nested ? ', NESTED recursion fires' : '') + ')', 'leaves=' + Object.keys(oracle).length);
console.log('§POS-BACKFLUSH parent=' + nameOf(PATIO) + '(' + PATIO + ') components=[' + show(groupDict) + '] consumed=Y sameGroup=Y');

// ── 2. polarity: every CONSUME is P- and the qty spine folds it as a decrement ──
var allPminus = consumeOps.every(function (o) { return o.movementtype === 'P-' && E.movementSign(o.movementtype) === -1; });
verdict(allPminus, "every CONSUME op is movementtype 'P-' (movementSign=-1) — folds as a DECREMENT on the qty spine", 'ops=' + consumeOps.length);
var fold = E.qtyOnHand(POS.saleMovements(g), { keyOf: function (e) { return e.m_product_id; }, typeOf: function (e) { return e.movementtype; }, absQtyOf: function (e) { return Math.abs(e.movementqty); } });
var allNeg = Object.keys(fold).every(function (k) { return fold[k] < 0; });
verdict(allNeg, 'qtyOnHand over the sale movements: every touched product DECREMENTS (ship C- + consume P-)', 'products=' + Object.keys(fold).length);

// ── 3. §FALSIFIER: a non-BOM product emits ZERO consumption ──
var LEAF = 124; // Elm Tree — priced, no pp_product_bom parent row
verdict(bomOf(LEAF).length === 0, nameOf(LEAF) + '(' + LEAF + ') has no recipe (real non-BOM control)');
var g2 = POS.buildSaleGroup(ctx, [POS.ringLine(ctx, LEAF, 5)], { orderId: 9111, inoutId: 9112, invoiceId: 9113, c_bpartner_id: 112 });
var c2 = g2.ops.filter(function (o) { return o.op_type === 'CONSUME'; });
verdict(c2.length === 0, '§FALSIFIER non-BOM sale emits ZERO CONSUME ops (no invented consumption)', 'consumeOps=' + c2.length);
console.log('§FALSIFIER pos=backflush product=' + LEAF + ' bomLines=0 consumeOps=' + c2.length + ' (must be 0)');

console.log('\n' + (fails === 0 ? '🟢 W-POS-BACKFLUSH PASS' : '🔴 W-POS-BACKFLUSH FAIL (' + fails + ')') +
  ' — the sale group consumes exactly the recipe leaves (explodeBOM == independent enumeration), with P- polarity on the qty spine, and a non-BOM sale consumes nothing.');
db.close();
process.exit(fails === 0 ? 0 : 1);
