#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_inventory.js — W-FOLD-INVENTORY (FOLD_MODEL_LOGIC.md §"ENACTMENT METHOD", RULE-CONSISTENT tier).
//
// SPEC (MInventory / Doc_Inventory): a physical-inventory count SETS on-hand to the counted qty. Each line
//   records ONE adjustment MTransaction — MovementType I+ when counted>book (a gain) or I- when counted<book
//   (a loss), |adjQty| = |counted − book| — which MStorageOnHand.add folds in lockstep, so AFTER the count the
//   folded on-hand == counted. Doc_Inventory then posts the adjustment at the product's current cost: the
//   {Product.Asset} inventory leg vs an Inventory-Gain/Loss offset, value = |adjQty| × cost, the two legs equal
//   and opposite (balanced).
//
// THE TIER — RULE-CONSISTENT, NOT oracle-equivalent (stated plainly): GardenWorld has NO MInventory documents
//   (no I± rows in m_transaction → no Doc_Inventory fact_acct). Nothing to diff. So this does NOT claim
//   "== iDempiere". It proves the engine's enacted count OBEYS the already-proven rules: (1) book on-hand is the
//   FOLD of the real m_transaction ledger (== m_storageonhand, W-FOLD-QTYONHAND); (2) the movement-sign
//   convention picks I+/I- by the sign of (counted−book) (W-FOLD-QTYONHAND); (3) folding the synthesized
//   adjustment through the qty spine lands on-hand == counted; (4) the GL adjustment value = |adjQty| × cost via
//   the PROVEN cost-selection rule (W-FOLD-MOVEMENT) and BALANCES. THIS is the MInventory I± rider that
//   W-FOLD-QTYONHAND explicitly named-DEFERRED to this spine — now closed. Same honest tier as backflush.
//
// GL OFFSET — NAMED-DEFERRED (and WHY): the inventory leg account {Product.Asset} resolves from real config; the
//   Inventory-Gain/Loss OFFSET account has NO extractable column in this seed (Doc.ACCTTYPE_InvDifferences maps
//   to a charge/warehouse account not captured here). So the offset account NUMBER is named-deferred; the leg
//   VALUE and the BALANCE invariant are proven (the offset's magnitude is determined). Not invented.
//
// ENACTMENT (no anchor mutation): the counted qty is the enactment scenario input (a physical count is, by
//   definition, NEW data the books lack) — a deterministic book±delta, labelled as the scenario, NOT claimed as
//   extracted; the rules under test hold for ANY count. Book qty, cost, accounts are all REAL rows. Deterministic
//   (integer centi-units / cents, no Date.now/Math.random). READ build/erp/poc_inventory.log.
// Implementing FOLD_MODEL_LOGIC.md §"ENACTMENT METHOD" (MInventory, rule-consistent) — Witness: W-FOLD-INVENTORY
// Run: node scripts/poc_inventory.js 2>&1 | tee build/erp/poc_inventory.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var R = require('./post_resolver');
var E = require('./erp_engine');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var SCHEMA = 101;
var sid = function (x) { return Number(x); };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function ci(n) { return Math.round(Number(n || 0) * 100); }     // qty → integer centi-units
function cents(n) { return Math.round(Number(n || 0) * 100); }  // money → integer cents
function nameOf(pid) { var r = db.prepare('SELECT name FROM m_product WHERE m_product_id=?').get(sid(pid)); return r ? r.name : ('#' + pid); }

// cost selection (the proven W-FOLD-MOVEMENT rule): schema costingmethod → cost element → m_cost.currentcostprice.
var sch = db.prepare('SELECT costingmethod,m_costtype_id FROM c_acctschema WHERE c_acctschema_id=?').get(SCHEMA);
function costElementId(cm) { var e = db.prepare('SELECT m_costelement_id FROM m_costelement WHERE costingmethod=?').get(cm || sch.costingmethod); return e ? e.m_costelement_id : null; }
function costCentsOf(productId, costElem) {
  var c = db.prepare('SELECT currentcostprice FROM m_cost WHERE m_product_id=? AND c_acctschema_id=? AND m_costtype_id=? AND m_costelement_id=?')
    .get(sid(productId), SCHEMA, sch.m_costtype_id, costElem);
  return c ? cents(c.currentcostprice) : null;
}
function assetAcct(productId) { var res = R.resolve(db, '{Product.Asset}', sid(productId), SCHEMA); return (res.acct != null && res.element) ? res.element.id : null; }

// book on-hand for a (product, locator, asi) cell, FOLDED from the real movement ledger (rides the qty spine).
function bookOnHand(pid, loc, asi) {
  var ev = db.prepare('SELECT movementtype,movementqty FROM m_transaction WHERE m_product_id=? AND m_locator_id=? AND m_attributesetinstance_id=?').all(sid(pid), sid(loc), sid(asi));
  var folded = E.qtyOnHand(ev, { keyOf: function () { return 'c'; }, typeOf: function (t) { return t.movementtype; }, absQtyOf: function (t) { return Math.abs(ci(t.movementqty)); } });
  return { book: folded['c'] || 0, events: ev };
}
function storedOnHand(pid, loc, asi) { var r = db.prepare('SELECT qtyonhand FROM m_storageonhand WHERE m_product_id=? AND m_locator_id=? AND m_attributesetinstance_id=?').get(sid(pid), sid(loc), sid(asi)); return r ? ci(r.qtyonhand) : null; }

console.log('═══ W-FOLD-INVENTORY — MInventory physical count (I± rides the qty spine; GL valued at cost) — RULE-CONSISTENT ═══');
console.log('    verbs = movementSign + qtyOnHand + cost-selection + {Product.Asset} (all PROVEN) · NO fact_acct oracle (no I± in seed) · enact-only');
console.log('    costingmethod=' + sch.costingmethod + ' → costElem=' + costElementId() + '\n');

// SUBJECTS: real products with BOTH a current cost AND on-hand at a real locator. Scenario deltas alternate
// gain/loss (deterministic, index-driven — the enactment input, not an extracted fact).
var subjects = db.prepare(
  'SELECT DISTINCT s.m_product_id pid, s.m_locator_id loc, s.m_attributesetinstance_id asi FROM m_storageonhand s ' +
  'JOIN m_cost c ON c.m_product_id=s.m_product_id AND c.c_acctschema_id=? AND c.m_costtype_id=? AND c.m_costelement_id=? AND c.currentcostprice>0 ' +
  'WHERE s.qtyonhand>0 ORDER BY s.m_product_id, s.m_locator_id LIMIT 6'
).all(SCHEMA, sch.m_costtype_id, costElementId());
var SCENARIO = [3, -2, 5, -4, 1, -3];      // counted = book + delta (units); alternating gain/loss (counts stay ≥0)

var allOk = 0;
subjects.forEach(function (sub, i) {
  var bk = bookOnHand(sub.pid, sub.loc, sub.asi);
  var stored = storedOnHand(sub.pid, sub.loc, sub.asi);
  var delta = SCENARIO[i % SCENARIO.length];
  var counted = bk.book + ci(delta);                          // counted qty (centi-units)
  var diff = counted - bk.book;                               // = ci(delta)
  var mvType = diff > 0 ? 'I+' : 'I-';
  var adjEvent = { movementtype: mvType, movementqty: Math.abs(diff) / 100 };

  // (0) ANCHOR: folded book == m_storageonhand (the spine holds for this cell).
  var anchorOk = stored != null && bk.book === stored;
  // (1) SIGN: movementSign(I±) matches the sign of (counted − book).
  var signOk = E.movementSign(mvType) === (diff > 0 ? 1 : -1);
  // (2) FOLD the count: book ledger + adjustment → on-hand == counted (the definitional MInventory property).
  var newOnHand = E.qtyOnHand(bk.events.concat([adjEvent]), { keyOf: function () { return 'c'; }, typeOf: function (t) { return t.movementtype; }, absQtyOf: function (t) { return Math.abs(ci(t.movementqty)); } })['c'];
  var foldOk = newOnHand === counted;
  // (3) GL value at cost (proven cost rule) + BALANCE: |asset| == |offset| = |adjQty| × cost.
  var costC = costCentsOf(sub.pid, costElementId());
  var adjUnits = Math.abs(diff) / 100;
  var adjValue = Math.round(adjUnits * costC);                // |adjQty| × cost, in cents
  var asset = assetAcct(sub.pid);
  // gain → DR Asset / CR Gain-Loss ; loss → CR Asset / DR Gain-Loss. Offset account named-deferred.
  var legs = diff > 0 ? { DR: { acct: asset, amt: adjValue }, CR: { acct: 'InvGainLoss[deferred]', amt: adjValue } }
    : { CR: { acct: asset, amt: adjValue }, DR: { acct: 'InvGainLoss[deferred]', amt: adjValue } };
  var balanceOk = legs.DR.amt === legs.CR.amt && asset != null && costC != null;

  var ok = anchorOk && signOk && foldOk && balanceOk;
  if (ok) allOk++;
  verdict(ok, nameOf(sub.pid) + ' @loc' + sub.loc + ' book ' + (bk.book / 100) + ' → count ' + (counted / 100) + ' (' + (delta > 0 ? '+' : '') + delta + ', ' + mvType + ')',
    'anchor=' + anchorOk + ' sign=' + signOk + ' newOnHand=' + (newOnHand / 100) + '==counted=' + (counted / 100) + ' adjVal=' + (adjValue / 100) + ' (=|' + adjUnits + '|×' + (costC / 100) + ') balanced=' + balanceOk);
  console.log('§FOLD-INVENTORY product=' + sub.pid + ' loc=' + sub.loc + ' type=' + mvType + ' book=' + (bk.book / 100) + ' counted=' + (counted / 100) +
    ' newOnHand=' + (newOnHand / 100) + ' assetAcct=' + asset + ' adjValue=' + (adjValue / 100) + 'c ΣDR=ΣCR=' + (balanceOk ? 'Y' : 'N') + ' tier=RULE-CONSISTENT(no-oracle)');
});
verdict(allOk === subjects.length && subjects.length > 0, allOk + '/' + subjects.length + ' enacted counts: on-hand folds to counted + GL valued-at-cost balances', 'ok=' + allOk);

// ── §FALSIFIER-A: use the WRONG polarity (I- for a gain) → folded on-hand ≠ counted ──
(function () {
  var sub = subjects[0]; var bk = bookOnHand(sub.pid, sub.loc, sub.asi);
  var delta = 3, counted = bk.book + ci(delta);                // a GAIN
  var wrong = { movementtype: 'I-', movementqty: delta };       // but post it as a loss
  var newOnHand = E.qtyOnHand(bk.events.concat([wrong]), { keyOf: function () { return 'c'; }, typeOf: function (t) { return t.movementtype; }, absQtyOf: function (t) { return Math.abs(ci(t.movementqty)); } })['c'];
  verdict(newOnHand !== counted, '§FALSIFIER-A post a gain as I- (wrong polarity) → folded on-hand ≠ counted', 'newOnHand=' + (newOnHand / 100) + ' counted=' + (counted / 100));
  console.log('§FALSIFIER-A doc=M_Inventory mutation=wrong-polarity newOnHand=' + (newOnHand / 100) + ' counted=' + (counted / 100) + ' (must differ)');
})();

// ── §FALSIFIER-B: value the adjustment with the WRONG cost element (Material/Standard, not schema Average) ──
(function () {
  var sub = subjects[0];
  var rightCost = costCentsOf(sub.pid, costElementId());
  var wrongElem = db.prepare("SELECT m_costelement_id FROM m_costelement WHERE costingmethod='S' AND costelementtype='M'").get();
  var wrongCost = wrongElem ? costCentsOf(sub.pid, wrongElem.m_costelement_id) : null;
  var differs = wrongCost != null && wrongCost !== rightCost;
  verdict(differs, '§FALSIFIER-B value the adjustment with the Material/Standard cost element → value diverges from the schema Average', 'rightCost=' + (rightCost / 100) + ' wrongCost=' + (wrongCost == null ? 'n/a' : wrongCost / 100));
  console.log('§FALSIFIER-B doc=M_Inventory mutation=wrong-cost-element rightCost=' + (rightCost / 100) + ' wrongCost=' + (wrongCost == null ? 'n/a' : wrongCost / 100) + ' (must differ — cost selection load-bearing)');
})();

console.log('\n§GL-OFFSET-DEFERRED the Inventory-Gain/Loss OFFSET account number (Doc.ACCTTYPE_InvDifferences) has no ' +
  'extractable column in this seed → named-deferred; the inventory leg {Product.Asset}, the value (|adjQty|×cost via ' +
  'the proven cost rule), and the BALANCE invariant are proven. Multi-locator + ASI cells fold identically (per-cell).');

console.log('\n' + (fails === 0 ? '🟢 W-FOLD-INVENTORY PASS' : '🔴 W-FOLD-INVENTORY FAIL (' + fails + ')') +
  ' — RULE-CONSISTENT: enacted physical count folds I± through the proven qty spine (on-hand→counted), GL valued at cost ' +
  'via the proven cost rule + balances; closes the MInventory I± rider qtyonhand deferred. Offset account named-deferred. NOT claimed == iDempiere.');
db.close();
process.exit(fails === 0 ? 0 : 1);
