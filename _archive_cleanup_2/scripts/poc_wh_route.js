#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_wh_route.js — Witness W-WH-ROUTE for docs/SPATIAL_PICKING_SPEC.md §S-2.
 *
 * Issues this witness proves/disproves:
 *  T0 §WH_DRAFT       — the seed has NO drafted movement (m_movement 100 is CO), so the pick
 *                       is DRAFTED via the existing ERPEngine.buildDoc archetype from REAL rows
 *                       (c_doctype 143 MMM, locators/products/qty from m_storageonhand +
 *                       m_movementline 100) — newVerbs=[], no invented ids.
 *  T1 §WH_ROUTE_ORDER — route orders by the recipe tree's walk sequence (m_bom_line.ordinal,
 *                       §S-1), NOT by line number: lines fed in scrambled order still come out
 *                       bin 101 (walk_seq 1) before bin 102 (walk_seq 7).
 *  T2 §WH_ROUTE_DET   — deterministic: same lines + same tree ⇒ byte-identical route, and a
 *                       PERMUTED input line array ⇒ the SAME route (tiebreak = line key, not
 *                       array position; no clock, no RNG).
 *  T3 §WH_ROUTE_COVER — steps cover ALL lines exactly once (count + per-line-key bijection).
 *  T4 §WH_FALSIFIER   — a line whose locator is OFF-MODEL (tree restricted to drop bin 102)
 *                       surfaces as an explicit unroutable step at the tail — a route that
 *                       silently drops it is the failure this witness exists to catch.
 *
 * Run: bash build/erp/run_witness.sh scripts/poc_wh_route.js
 * Read the log (build/erp/poc_wh_route.log) after every run.
 */
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var WHRoute = require('../build/erp/wh_route.js');
var E = require('./erp_engine.js');

var ROOT = path.join(__dirname, '..');
var WH_DB = path.join(ROOT, 'build', 'erp', 'warehouse_gardenworld.db');
var SEED = path.join(ROOT, 'build', 'erp', 'ad_seed_fullwidth.db');

var fails = 0;
function verdict(ok, label, detail) {
  if (!ok) fails++;
  console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : ''));
}

var wh = new Database(WH_DB, { readonly: true });
var seed = new Database(SEED, { readonly: true });

// ── the tree: EXTRACTED from the compiled warehouse db (§S-1 substrate) ──────
var bomLines = wh.prepare('SELECT bom_id, child_product_id, role, ordinal, element_ref FROM m_bom_line').all();
var tree = WHRoute.treeFromBom(bomLines);
console.log('§WH_TREE bins=' + tree.order.length + ' order=' + tree.order.join(','));
verdict(tree.order.length === 11, 'tree carries all 11 bins', tree.order.length + '/11');
verdict(tree.order[0] === '101' && tree.byLocator['102'].walk_seq === 7,
  'walk order rides m_bom_line.ordinal (101 first, 102 at seq 7)');

// ── T0: draft the pick via buildDoc from REAL rows ──────────────────────────
console.log('— T0: draft (buildDoc, real rows only) —');
// the one real movement line in the seed: its qty (4) is the EXTRACTED line-qty rule;
// per-line qty = min(4, qtyonhand) — never more than the bin really holds.
var seedLine = seed.prepare('SELECT movementqty FROM m_movementline WHERE m_movementline_id=100').get();
// (full-width seed keeps DECLARED case — C_DocType_ID — so alias to lowercase here)
var doctype = seed.prepare("SELECT c_doctype_id AS c_doctype_id FROM c_doctype WHERE docbasetype='MMM' COLLATE NOCASE").get();
var toLoc = seed.prepare("SELECT m_locator_id AS m_locator_id FROM m_locator WHERE value='HQ Transit'").get();
// pick rows: product 123 + 127 at HQ bin 101, product 123 at Store bin 102 — every row real
// (m_storageonhand), the 2-bin walk is honest GardenWorld scale (spec §4: demo aisle).
var pickRows = seed.prepare(
  'SELECT m_product_id, m_locator_id, qtyonhand FROM m_storageonhand ' +
  'WHERE (m_locator_id=101 AND m_product_id IN (123,127)) OR (m_locator_id=102 AND m_product_id=123) ' +
  'ORDER BY m_locator_id, m_product_id').all();
verdict(pickRows.length === 3, '3 real m_storageonhand pick rows', JSON.stringify(pickRows));
var lines = pickRows.map(function (r, i) {
  return { line: (i + 1) * 10, m_product_id: r.m_product_id,
           qty: Math.min(seedLine.movementqty, r.qtyonhand),
           m_locator_id: r.m_locator_id, m_locatorto_id: toLoc.m_locator_id };
});
var parent = { m_warehouse_id: 103, c_doctype_id: doctype.c_doctype_id };
var ops = WHRoute.decoratePickOps(E.buildDoc(WHRoute.PICK_DOC_SPEC, parent, lines), lines);
var createDoc = ops.filter(function (o) { return o.op_type === 'CREATE_DOCUMENT'; });
var createLines = ops.filter(function (o) { return o.op_type === 'CREATE_LINE'; });
var draftOk = createDoc.length === 1 && createDoc[0].table === 'M_Movement' &&
  createDoc[0].docstatus === 'DR' && createDoc[0].c_doctype_id === 143 &&
  createLines.length === 3 && createLines.every(function (o, i) {
    return o.table === 'M_MovementLine' && o.movementqty === lines[i].qty &&
      o.m_locator_id === lines[i].m_locator_id && o.m_locatorto_id === lines[i].m_locatorto_id;
  });
verdict(draftOk, 'buildDoc spine: 1×CREATE_DOCUMENT(M_Movement,DR,doctype 143) + 3×CREATE_LINE w/ real locator pairs');
console.log('§WH_DRAFT doc=M_Movement DR doctype=' + createDoc[0].c_doctype_id + ' lines=' + createLines.length +
  ' qty=[' + createLines.map(function (o) { return o.movementqty; }).join(',') + '] newVerbs=[] (buildDoc archetype)');

// ── T1: route orders by walk_seq, not line number ────────────────────────────
console.log('— T1: order —');
var scrambled = [lines[2], lines[0], lines[1]];           // bin-102 line FIRST in the input
var steps = WHRoute.route(scrambled, tree);
var visit = steps.map(function (s) { return s.m_locator_id; });
var orderOk = JSON.stringify(visit) === JSON.stringify(['101', '101', '102']);
verdict(orderOk, 'visit order 101,101,102 (walk_seq) despite 102-line fed first', visit.join(','));
verdict(steps[0].rack === 'RACK_HQ' && steps[0].aisle === 'AISLE_A' &&
        steps[2].rack === 'RACK_STORE_CENTRAL' && steps[2].aisle === 'AISLE_C',
  'steps carry rack+aisle from the tree', steps.map(function (s) { return s.rack; }).join(','));
console.log('§WH_ROUTE_ORDER input=[102,101,101] → route=[' + visit.join(',') + '] walk_seq=[' +
  steps.map(function (s) { return s.walk_seq; }).join(',') + ']');

// ── T2: deterministic + permutation-invariant ────────────────────────────────
console.log('— T2: determinism —');
var again = WHRoute.route(scrambled, tree);
var fromSorted = WHRoute.route(lines, tree);
var det = JSON.stringify(steps) === JSON.stringify(again);
var perm = JSON.stringify(steps) === JSON.stringify(fromSorted);
verdict(det, 'same lines + same tree ⇒ identical route (repeat call)');
verdict(perm, 'permuted input array ⇒ SAME route (tiebreak = line key, not position)');
console.log('§WH_ROUTE_DET repeat=' + (det ? 'identical' : 'DIFFERS') + ' permuted=' + (perm ? 'identical' : 'DIFFERS'));

// ── T3: coverage — all lines exactly once ────────────────────────────────────
console.log('— T3: coverage —');
var keys = steps.map(function (s) { return s.line.line; }).sort(function (a, b) { return a - b; });
var coverOk = steps.length === lines.length && JSON.stringify(keys) === JSON.stringify([10, 20, 30]);
verdict(coverOk, 'steps cover ALL ' + lines.length + ' lines exactly once', 'keys=' + keys.join(','));
console.log('§WH_ROUTE_COVER steps=' + steps.length + '/' + lines.length + ' lineKeys=[' + keys.join(',') + '] each-once=' + (coverOk ? 'Y' : 'N'));

// ── T4: falsifier — off-model locator = explicit unroutable step ─────────────
console.log('— T4: falsifier —');
// restrict the tree (drop bin 102) instead of inventing a ghost id: the 102-line is now off-model.
var restricted = WHRoute.treeFromBom(bomLines.filter(function (l) {
  return !(l.role === 'BIN' && l.element_ref === '[102]');
}));
var steps2 = WHRoute.route(scrambled, restricted);
var un = steps2.filter(function (s) { return s.unroutable; });
var falsOk = steps2.length === lines.length && un.length === 1 && un[0].m_locator_id === '102' &&
  un[0].step === 3 && un[0].walk_seq === null;
verdict(falsOk, 'off-model locator 102 → ONE explicit unroutable tail step, nothing dropped',
  'steps=' + steps2.length + ' unroutable=' + un.length);
console.log('§WH_FALSIFIER off-model locator=102 → step=' + (un[0] ? un[0].step + '/' + un[0].of : '?') +
  ' unroutable=Y dropped=' + (steps2.length === lines.length ? 'NONE' : 'YES (FAIL)'));

console.log(fails === 0 ? '§W-WH-ROUTE PASS — all verdicts green' : '§W-WH-ROUTE FAIL fails=' + fails);
process.exit(fails === 0 ? 0 : 1);
