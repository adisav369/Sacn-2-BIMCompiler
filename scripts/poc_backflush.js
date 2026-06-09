#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_backflush.js — W-FOLD-BACKFLUSH (FOLD_MODEL_LOGIC.md §F-2 Δ-A, the AutoBOMOrder behaviour).
//
// SPEC (docs/POSLens.md §6): ringing a finished good folds DOWN the recipe and consumes its leaf
//   components — RECURSIVELY (a component may itself be a BOM). "Backflush is deterministic replay of
//   the BOM — the same verb that compiles a building, run at point of sale." Here it runs on the REAL
//   GardenWorld recipe, hung on a REAL sale (Patio Chair ×30, Fertilizer #50 ×40 — actual order lines).
//
// ORACLE (honest — GardenWorld has NO production docs, m_production=0, so there is no fact_acct to diff;
//   the oracle is the RECIPE EXPLOSION itself): two INDEPENDENT implementations must agree —
//   (1) engine E.explodeBOM (per-node dict merge) vs (2) a flat DFS path-enumeration (multiply qtybom
//   along every root→leaf path, accumulate per leaf). Agreement proves the recursion is correct, not
//   self-referential. PLUS a multi-path invariant: the cap-screw (50002) is in BOTH leg sub-assemblies,
//   so it must accumulate 8+8=16 per chair — a naive (non-accumulating / single-path) walk gets it wrong.
//   §FALSIFIER: a FLAT one-level explosion treats the sub-assemblies as leaves → MISSES screws/glue/legs
//   → differs from the recursive fold. Proves the recursion is load-bearing (not decoration).
//
// NON-INVENT: recipe = real pp_product_bom/bomline (client 11) captured to glassbowl_data.db; sale qty =
//   real c_orderline; no Date.now/Math.random. The StorageOnHand DECREMENT + replenishment ride the qty
//   spine (task #2) and are NAMED-DEFERRED here — this witness proves the EXPLOSION (the AutoBOMOrder core).
// Implementing FOLD_MODEL_LOGIC.md §F-2 Δ-A — Witness: W-FOLD-BACKFLUSH
// Run: node scripts/poc_backflush.js 2>&1 | tee build/erp/poc_backflush.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var E = require('./erp_engine');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var sid = function (x) { return Number(x); };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

// ── host-injected recipe resolver (keeps the engine pure) ──
var bomStmt = db.prepare('SELECT comp_id, qtybom FROM m_product_bom WHERE parent_id=?');
function bomOf(pid) { return bomStmt.all(sid(pid)); }
var nameStmt = db.prepare('SELECT name FROM m_product WHERE m_product_id=?');
function nameOf(pid) { var r = nameStmt.get(sid(pid)); return r ? r.name : ('#' + pid); }

// ── the INDEPENDENT oracle: flat DFS path-enumeration (different impl from E.explodeBOM) ──
function pathEnumerate(pid, qty) {
  var out = {};
  (function walk(p, mult) {
    var lines = bomOf(p);
    if (!lines.length) { out[p] = (out[p] || 0) + mult; return; }   // leaf
    lines.forEach(function (l) { walk(l.comp_id, mult * l.qtybom); });
  })(pid, qty);
  return out;
}
// ── the FALSIFIER's wrong model: flat one-level (no recursion) ──
function flatExplode(pid, qty) { var out = {}; bomOf(pid).forEach(function (l) { out[l.comp_id] = (out[l.comp_id] || 0) + qty * l.qtybom; }); return out; }
function eq(a, b) { var ak = Object.keys(a).sort(), bk = Object.keys(b).sort(); if (ak.join() !== bk.join()) return false; return ak.every(function (k) { return Math.abs(a[k] - b[k]) < 1e-9; }); }
function show(m) { return Object.keys(m).sort().map(function (k) { return nameOf(k) + '×' + m[k]; }).join(', '); }

console.log('═══ W-FOLD-BACKFLUSH — recursive BOM explosion (AutoBOMOrder) on real GardenWorld recipe + sale ═══');
console.log('    verb = erp_engine.explodeBOM · oracle = independent path-enumeration · recipe = pp_product_bom (client 11)\n');

// the REAL sold BOM products (parent in m_product_bom AND on an order line) with their sold qty
var sold = db.prepare('SELECT DISTINCT ol.m_product_id pid, ol.qtyordered qty FROM c_orderline ol WHERE ol.m_product_id IN (SELECT DISTINCT parent_id FROM m_product_bom) ORDER BY ol.m_product_id').all();
console.log('§BACKFLUSH-SALES sold BOM products = [' + sold.map(function (s) { return nameOf(s.pid) + '(' + s.pid + ')×' + s.qty; }).join(', ') + ']\n');

sold.forEach(function (s) {
  var mine = E.explodeBOM(bomOf, s.pid, s.qty);
  var oracle = pathEnumerate(s.pid, s.qty);
  var agree = eq(mine, oracle);
  // is this a NESTED recipe (a direct component is itself a BOM)? → recursion actually fires
  var nested = bomOf(s.pid).some(function (l) { return bomOf(l.comp_id).length > 0; });
  verdict(agree, 'ring ' + nameOf(s.pid) + ' ×' + s.qty + ' → explodeBOM == independent path-enumeration' + (nested ? ' (NESTED, recursion fires)' : ''),
    'leaves=' + Object.keys(mine).length + (agree ? '' : ' MINE=' + JSON.stringify(mine) + ' ORACLE=' + JSON.stringify(oracle)));
  console.log('§FOLD-BACKFLUSH parent=' + nameOf(s.pid) + '(' + s.pid + ') ringQty=' + s.qty + ' nested=' + (nested ? 'Y' : 'N') + ' leaves=' + Object.keys(mine).length + ' consumed=[' + show(mine) + ']');
});

// ── multi-path accumulation invariant: the cap-screw (50002) is in BOTH leg sub-assemblies of the chair ──
(function () {
  var chair = sold.find(function (s) { return bomOf(s.pid).some(function (l) { return bomOf(l.comp_id).length > 0; }); });
  if (!chair) { console.log('§BACKFLUSH-INVARIANT no nested recipe sold — skipped'); return; }
  var ex = E.explodeBOM(bomOf, chair.pid, chair.qty);
  // screw 50002 appears at qtybom 8 in each of the two assemblies (50000, 50001), each ×1 in the chair
  var screw = 50002, perChair = 16;                      // 8 (back-leg asm) + 8 (front-leg asm)
  var got = ex[screw] || 0, want = perChair * chair.qty;
  verdict(got === want, 'multi-path accumulation: ' + nameOf(screw) + ' summed across BOTH leg assemblies = ' + perChair + '/chair',
    'got=' + got + ' want=' + want + ' (' + perChair + '×' + chair.qty + ')');
  console.log('§BACKFLUSH-INVARIANT screw(50002)=' + got + ' == 16×' + chair.qty + ' (accumulates over 2 paths — a single-path walk would give 8×' + chair.qty + ')');

  // ── §FALSIFIER: flat one-level explosion treats sub-assemblies as leaves → misses the screws/glue/legs ──
  var flat = flatExplode(chair.pid, chair.qty);
  var recursive = ex;
  var differs = !eq(flat, recursive) && (flat[screw] == null);
  verdict(differs, '§FALSIFIER flat (non-recursive) explosion ≠ recursive AND omits the screw → recursion is load-bearing',
    'flatLeaves=' + Object.keys(flat).length + ' recursiveLeaves=' + Object.keys(recursive).length + ' flatHasScrew=' + (flat[screw] != null));
  console.log('§FALSIFIER flat=[' + show(flat) + '] vs recursive omits-none — flat misses sub-assembly leaves');
})();

console.log('\n§FOLD-DEFERRED the StorageOnHand DECREMENT (consume leaves → on-hand) rides the qty-fold spine (task #2); ' +
  'the replenishment PO (onhand<min) is Δ-B (task #6). This witness proves the EXPLOSION — the AutoBOMOrder core.');

console.log('\n' + (fails === 0 ? '🟢 W-FOLD-BACKFLUSH PASS' : '🔴 W-FOLD-BACKFLUSH FAIL (' + fails + ')') +
  ' — recursive BOM backflush reproduces the real recipe (two independent impls agree, multi-path accumulation correct, ' +
  'flat-explosion falsifier fires). The AutoBOMOrder explosion is one engine verb (explodeBOM), fold-not-fork.');
db.close();
process.exit(fails === 0 ? 0 : 1);
