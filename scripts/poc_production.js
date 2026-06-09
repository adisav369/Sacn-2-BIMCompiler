#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_production.js — W-FOLD-PRODUCTION (FOLD_MODEL_LOGIC.md §"ENACTMENT METHOD", RULE-CONSISTENT tier).
//
// SPEC (MProduction / Doc_Production): completing a production document records ONE finished-good receipt
//   (MovementType P+, +producedQty) and ONE consumption per exploded component (MovementType P-, −usedQty),
//   each an MTransaction row that MStorageOnHand.add folds in lockstep. The component usage IS the BOM
//   explosion — the SAME recursive verb proven in W-FOLD-BACKFLUSH (erp_engine.explodeBOM). So a production
//   is: explode the recipe → synthesize the P+/P- movement ledger → the on-hand DELTA folds through the
//   ALREADY-PROVEN qty spine (erp_engine.movementSign / qtyOnHand): finished +Q, each leaf −used.
//
// THE TIER — RULE-CONSISTENT, NOT oracle-equivalent (stated plainly, never dressed up): GardenWorld has NO
//   production documents (m_production=0 → no MProduction fact_acct, no P± in m_transaction). There is NOTHING
//   to diff against. So this witness does NOT claim "== iDempiere output". It proves the engine's enacted
//   production OBEYS the rules already proven elsewhere: (1) the BOM explosion (== independent path-enumeration,
//   W-FOLD-BACKFLUSH), (2) the movement-sign convention (P+ → +1, P- → −1, W-FOLD-QTYONHAND), and (3) the
//   on-hand fold of the synthesized P± ledger lands the exact finished-+Q / leaf-−used deltas. THIS is the
//   StorageOnHand DECREMENT that W-FOLD-BACKFLUSH and W-FOLD-QTYONHAND both explicitly named-DEFERRED to this
//   spine — now closed. Same honest tier as backflush; the falsifiers keep it load-bearing.
//
// GL — NAMED-DEFERRED (and WHY, precisely): a Doc_Production GL posting books DR finished-good {Product.Asset}
//   / CR each component {Product.Asset} at cost. In this seed the LEAF COMPONENTS carry NO m_cost row (only
//   finished goods + traded items do), so the component-cost CR side cannot be valued WITHOUT INVENTING a cost
//   — which the prime directive forbids. The cost-selection RULE itself is proven (W-FOLD-MOVEMENT); only the
//   component cost DATA is absent. So the production MOVEMENT fold is proven here; the cost-valued GL is named,
//   not faked.
//
// ENACTMENT (no anchor mutation): the production qty is anchored to the REAL order-line qty for the same BOM
//   product (the qty backflush used) — non-invent; the synthesized P± ledger lives only in memory. NON-INVENT:
//   recipe = real m_product_bom (client 11); deterministic (integer centi-units, no Date.now/Math.random).
//   READ build/erp/poc_production.log — exit code is not evidence.
// Implementing FOLD_MODEL_LOGIC.md §"ENACTMENT METHOD" (MProduction, rule-consistent) — Witness: W-FOLD-PRODUCTION
// Run: node scripts/poc_production.js 2>&1 | tee build/erp/poc_production.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var E = require('./erp_engine');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var LOC = 101;                 // the production warehouse locator (scenario input; one cell-space for the fold)
var ASI = 0;
var sid = function (x) { return Number(x); };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function ci(n) { return Math.round(Number(n || 0) * 100); }     // qty → integer centi-units (deterministic)
function cellKey(t) { return t.m_product_id + '/' + t.m_locator_id + '/' + t.m_attributesetinstance_id; }

// host-injected recipe resolver (keeps the engine pure) — identical to W-FOLD-BACKFLUSH
var bomStmt = db.prepare('SELECT comp_id, qtybom FROM m_product_bom WHERE parent_id=?');
function bomOf(pid) { return bomStmt.all(sid(pid)); }
function nameOf(pid) { var r = db.prepare('SELECT name FROM m_product WHERE m_product_id=?').get(sid(pid)); return r ? r.name : ('#' + pid); }
// the FALSIFIER's wrong model: flat one-level explosion (treats sub-assemblies as leaves)
function flatExplode(pid, qty) { var out = {}; bomOf(pid).forEach(function (l) { out[l.comp_id] = (out[l.comp_id] || 0) + qty * l.qtybom; }); return out; }

// ── synthesize the P+/P- movement ledger for a production of `pid × Q` ───────────────────────────────────
//   finished good: ONE P+ (+Q); each exploded leaf: ONE P- (−used). The engine fold reconstructs the sign
//   from the MovementType — it never trusts a pre-signed qty (mirrors the real MTransaction spine).
function synthProduction(pid, Q, explode) {
  var leaves = explode(bomOf, pid, Q);                          // {leafId: usedQty}
  var events = [{ m_product_id: sid(pid), m_locator_id: LOC, m_attributesetinstance_id: ASI, movementtype: 'P+', movementqty: Q }];
  Object.keys(leaves).forEach(function (leaf) {
    events.push({ m_product_id: sid(leaf), m_locator_id: LOC, m_attributesetinstance_id: ASI, movementtype: 'P-', movementqty: leaves[leaf] });
  });
  return { events: events, leaves: leaves };
}
function foldDeltas(events) {
  return E.qtyOnHand(events, { keyOf: cellKey, typeOf: function (t) { return t.movementtype; }, absQtyOf: function (t) { return Math.abs(ci(t.movementqty)); } });
}

console.log('═══ W-FOLD-PRODUCTION — MProduction movement fold (P+/P- ride the proven qty spine) — RULE-CONSISTENT ═══');
console.log('    verbs = explodeBOM + movementSign + qtyOnHand (all PROVEN) · NO fact_acct oracle (m_production=0) · enact-only\n');

// the REAL sold BOM products (parent in m_product_bom AND on an order line) + their real order qty — the anchor.
var sold = db.prepare('SELECT DISTINCT ol.m_product_id pid, ol.qtyordered qty FROM c_orderline ol WHERE ol.m_product_id IN (SELECT DISTINCT parent_id FROM m_product_bom) ORDER BY ol.m_product_id').all();
console.log('§PROD-SCENARIO produce = [' + sold.map(function (s) { return nameOf(s.pid) + '(' + s.pid + ')×' + s.qty; }).join(', ') + '] (qty anchored to real c_orderline)\n');

var ok_all = 0;
sold.forEach(function (s) {
  var syn = synthProduction(s.pid, s.qty, E.explodeBOM);
  var nested = bomOf(s.pid).some(function (l) { return bomOf(l.comp_id).length > 0; });

  // (1) SIGN RULE on the production MovementTypes: P+ → +1, P- → −1 (the proven convention, applied to P±).
  var signOk = E.movementSign('P+') === 1 && E.movementSign('P-') === -1;

  // (2) FOLD the synthesized ledger → finished +Q, each leaf −used (the StorageOnHand delta from production).
  var deltas = foldDeltas(syn.events);
  var finOk = deltas[s.pid + '/' + LOC + '/' + ASI] === ci(s.qty);
  var leafOk = Object.keys(syn.leaves).every(function (leaf) { return deltas[leaf + '/' + LOC + '/' + ASI] === -ci(syn.leaves[leaf]); });

  var ok = signOk && finOk && leafOk;
  if (ok) ok_all++;
  verdict(ok, 'produce ' + nameOf(s.pid) + ' ×' + s.qty + ' → finished +' + s.qty + ', ' + Object.keys(syn.leaves).length + ' leaves decremented' + (nested ? ' (NESTED)' : ''),
    'finishedDelta=' + (deltas[s.pid + '/' + LOC + '/' + ASI] / 100) + ' leafSigns=' + (leafOk ? 'all −used' : 'MISMATCH'));
  console.log('§FOLD-PRODUCTION parent=' + nameOf(s.pid) + '(' + s.pid + ') Q=' + s.qty + ' P+1/P-' + Object.keys(syn.leaves).length +
    ' finished=+' + (deltas[s.pid + '/' + LOC + '/' + ASI] / 100) + ' Σleaf-consumed=−' + (Object.keys(syn.leaves).reduce(function (a, k) { return a + ci(syn.leaves[k]); }, 0) / 100) + ' tier=RULE-CONSISTENT(no-oracle)');
});
verdict(ok_all === sold.length && sold.length > 0, ok_all + '/' + sold.length + ' enacted productions fold the P± ledger through the qty spine (finished +Q, leaves −used)', 'ok=' + ok_all);

// ── §FALSIFIER-A: flip ONE leaf P- to P+ (wrong polarity) → that leaf increments instead of decrements ──
(function () {
  var s = sold[0]; var syn = synthProduction(s.pid, s.qty, E.explodeBOM);
  var firstLeaf = syn.events.find(function (e) { return e.movementtype === 'P-'; });
  var mutated = syn.events.map(function (e) { return e === firstLeaf ? Object.assign({}, e, { movementtype: 'P+' }) : e; });
  var deltas = foldDeltas(mutated);
  var got = deltas[firstLeaf.m_product_id + '/' + LOC + '/' + ASI];
  var bad = got !== -ci(firstLeaf.movementqty);    // expected −used; flipped gives +used
  verdict(bad, '§FALSIFIER-A flip one component P- → P+ (wrong polarity) → that leaf goes +used not −used', 'leaf=' + nameOf(firstLeaf.m_product_id) + ' got=' + (got / 100) + ' expected=−' + firstLeaf.movementqty);
  console.log('§FALSIFIER-A doc=M_Production mutation=flip-component-polarity leaf=' + firstLeaf.m_product_id + ' got=' + (got / 100) + ' (must ≠ −' + firstLeaf.movementqty + ')');
})();

// ── §FALSIFIER-B: build the ledger from a FLAT explosion → sub-assembly leaves (e.g. screws) are MISSED ──
(function () {
  var s = sold.find(function (x) { return bomOf(x.pid).some(function (l) { return bomOf(l.comp_id).length > 0; }); }) || sold[0];
  var recursive = synthProduction(s.pid, s.qty, E.explodeBOM).leaves;
  var flat = synthProduction(s.pid, s.qty, function (b, p, q) { return flatExplode(p, q); }).leaves;
  var recKeys = Object.keys(recursive).sort().join(), flatKeys = Object.keys(flat).sort().join();
  var differs = recKeys !== flatKeys;
  verdict(differs, '§FALSIFIER-B flat (non-recursive) explosion → consumes the wrong component set (misses sub-assembly leaves)', 'recursiveLeaves=' + Object.keys(recursive).length + ' flatLeaves=' + Object.keys(flat).length);
  console.log('§FALSIFIER-B doc=M_Production mutation=flat-explosion recursiveLeaves=' + Object.keys(recursive).length + ' flatLeaves=' + Object.keys(flat).length + ' (must differ — recursion load-bearing)');
})();

console.log('\n§GL-DEFERRED Doc_Production GL (DR finished {Product.Asset} / CR component {Product.Asset} at cost) — the ' +
  'cost-selection RULE is proven (W-FOLD-MOVEMENT) but the LEAF-component m_cost DATA is absent in this seed (only ' +
  'finished goods + traded items carry cost), so the component-cost CR side cannot be valued without inventing. The ' +
  'production MOVEMENT fold is proven; the cost-valued GL is named, not faked.');

console.log('\n' + (fails === 0 ? '🟢 W-FOLD-PRODUCTION PASS' : '🔴 W-FOLD-PRODUCTION FAIL (' + fails + ')') +
  ' — RULE-CONSISTENT: enacted MProduction P± ledger folds through the proven qty spine (finished +Q, leaves −used); ' +
  'closes the StorageOnHand DECREMENT that backflush/qtyonhand deferred. GL named-deferred (component cost absent in seed). NOT claimed == iDempiere.');
db.close();
process.exit(fails === 0 ? 0 : 1);
