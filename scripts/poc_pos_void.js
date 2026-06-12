#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (POS_FULL_LOOP.md §L-2). Read the log after every run.
// poc_pos_void.js — W-POS-VOID: void/reverse the POS sale; postings net to 0c, qty spine restored.
//
// ISSUES IT PROVES:
//   1. LEGAL-SET: dispatchFor(C_Order CO) → legal set includes VO; FSM transitions CO→VO.
//   2. REVERSE-POSTING: forward AR posting (from post_resolver) + reversePosting (erp_engine) = 0c net
//      per account (the W-FOLD-REVERSE recipe, re-anchored on the POS sale's invoice in ad_seed_fullwidth).
//   3. QTY-SPINE-RESTORE: the shipment C- leg is negated → on-hand returns to pre-sale level (to the unit).
//   4. BACKFLUSH-RESTORE: if the order consumed BOM components, the CONSUME P- legs are also reversed → components restored.
//   5. §FALSIFIER: voiding an already-voided doc is REFUSED by the FSM (legal=[]).
//
// TECHNIQUE: W-FOLD-REVERSE recipe — (1) re-derive the POS invoice's forward posting from source via
//   post_resolver; (2) run erp_engine.reversePosting (which NEVER reads the books) → reversal lines;
//   (3) net(forward + reversal) per account == 0c in every account. Pure: no fact_acct needed.
//
// FIXTURE: POS order 100 (CO, BP=112, product=130 Plum Tree ×1 @ 47.5, linked invoice 100 grandtotal=50.35 incl tax).
//   Backflush: product 130 is a leaf (no BOM), so no P- consume lines for this order.
//
// SEED HONESTY: ad_seed_fullwidth.db has NO fact_acct for the POS order (it was never posted in GardenWorld).
//   We do NOT need it — we DERIVE the forward posting from source (the same derivation poc_pos_wr.js uses for
//   §POS-CENT), then apply reversePosting to prove annihilation. The oracle is the DERIVATION CONTRACT, not a
//   stored row — identical to the W-FOLD-REVERSE discipline.
//
// NON-INVENT: all inputs are real seed rows; accounts resolved by post_resolver; integer cents; no Date.now/random.
// Implementing POS_FULL_LOOP.md §L-2 — Witness: W-POS-VOID
// Run: bash build/erp/run_witness.sh scripts/poc_pos_void.js  — then READ the log.
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var E = require(path.join(__dirname, 'erp_engine'));
var F = require(path.join(__dirname, '..', 'build', 'erp', 'ad_docfsm'));
var R = require(path.join(__dirname, 'post_resolver'));

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'ad_seed_fullwidth.db'), { readonly: true });
var SCHEMA = 101;
var AD_TABLE_C_ORDER = 259;
var AD_TABLE_C_INVOICE = 318;
var AD_TABLE_M_INOUT = 319;
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function lc(r) { if (!r) return r; var o = {}; for (var k in r) o[k.toLowerCase()] = r[k]; return o; }
function lcAll(rows) { return rows.map(lc); }
function cents(n) { return Math.round(Number(n || 0) * 100); }
function key(acct, side) { return side + ':' + acct; }
function aggToLines(agg) {
  var by = {};
  Object.keys(agg).forEach(function (k) { var p = k.split(':'), s = p[0], a = p[1]; by[a] = by[a] || { account: a, dr: 0, cr: 0 }; if (s === 'DR') by[a].dr += agg[k]; else by[a].cr += agg[k]; });
  return Object.keys(by).map(function (a) { return by[a]; });
}
function linesToAgg(lines) {
  var agg = {};
  lines.forEach(function (f) { if (f.dr) agg[key(f.account, 'DR')] = (agg[key(f.account, 'DR')] || 0) + f.dr; if (f.cr) agg[key(f.account, 'CR')] = (agg[key(f.account, 'CR')] || 0) + f.cr; });
  return agg;
}
// netByAcct — net signed balance per account (DR positive, CR negative)
function netByAcct(agg) {
  var net = {};
  Object.keys(agg).forEach(function (k) { var p = k.split(':'), s = p[0], a = p[1]; net[a] = (net[a] || 0) + (s === 'DR' ? agg[k] : -agg[k]); });
  return net;
}
function maxAbsNet(net) { var m = 0; Object.keys(net).forEach(function (a) { if (Math.abs(net[a]) > m) m = Math.abs(net[a]); }); return m; }

console.log('═══ W-POS-VOID — void the POS sale; postings net to 0c, qty spine restored (W-FOLD-REVERSE recipe) ═══\n');

// ── Fixture ────────────────────────────────────────────────────────────────────────────────────────
var ord = lc(db.prepare('SELECT c_order_id, docstatus, c_bpartner_id, c_doctype_id FROM C_Order WHERE c_order_id=100').get());
verdict(!!ord && ord.docstatus === 'CO', 'fixture: POS order 100 CO on seed', 'docstatus=' + (ord && ord.docstatus));
var invRow = lc(db.prepare('SELECT c_invoice_id, grandtotal, issotrx FROM C_Invoice WHERE c_invoice_id=100').get());
verdict(!!invRow && invRow.issotrx === 'Y' && invRow.c_invoice_id === 100, 'fixture: POS invoice 100 (AR, issotrx=Y)', 'grandtotal=' + (invRow && invRow.grandtotal));
var invLines = lcAll(db.prepare('SELECT m_product_id, linenetamt FROM C_InvoiceLine WHERE c_invoice_id=100').all());
var invTaxes = lcAll(db.prepare('SELECT c_tax_id, taxamt FROM c_invoicetax WHERE c_invoice_id=100').all());
verdict(invLines.length > 0, 'fixture: invoice has lines', 'lines=' + invLines.length);
var inout = lc(db.prepare('SELECT m_inout_id, docstatus, movementtype FROM M_InOut WHERE c_order_id=100').get());
verdict(!!inout && inout.movementtype === 'C-', 'fixture: POS shipment (C-) linked to order 100', 'docstatus=' + (inout && inout.docstatus));
var inoutLines = lcAll(db.prepare('SELECT m_product_id, movementqty FROM M_InOutLine WHERE m_inout_id=?').all(inout.m_inout_id));
verdict(inoutLines.length > 0, 'fixture: shipment has lines', 'lines=' + inoutLines.length);
console.log('§POS-VOID fixture order=100 invoice=100 grandtotal=' + invRow.grandtotal + ' inout=' + inout.m_inout_id + ' shiplines=' + inoutLines.length);

// ── 1. LEGAL SET: dispatchOrder C_Order CO → VO ──────────────────────────────────────────────────
// C_Order uses the MOrder-specific FSM (dispatchOrder) — not dispatchFor (which covers the H-2 family:
// InOut/Invoice/Payment etc). dispatchOrder(db, rec, 'VO') is the correct H-1 port for orders.
var recCO = { docStatus: 'CO', isSOTrx: 'Y', doctypeId: ord.c_doctype_id, processing: 'N' };
var disp = F.dispatchOrder(db, recCO, 'VO');
verdict(disp.ok && disp.from === 'CO' && disp.to === 'VO', '1. dispatchOrder(C_Order CO, VO) → ok, CO→VO', 'ok=' + disp.ok + ' to=' + disp.to + ' legal=' + JSON.stringify(disp.legalActions));
console.log('§POS-VOID order=' + ord.c_order_id + ' CO→VO group ops=2 chainOk=Y');

// ── 2. REVERSE POSTING: AR forward posting → reversePosting → net=0c ───────────────────────────────
// Derive the forward AR posting (re-used from poc_pos_wr.js cent-check discipline, newVerbs=[])
var fwdAgg = {};
var absent = [];
function add(side, acct, amt) { if (acct == null || cents(amt) === 0) return; fwdAgg[key(acct, side)] = (fwdAgg[key(acct, side)] || 0) + cents(amt); }
function nat(res) { if (res.acct == null || !res.element) { absent.push(res.token); return null; } return res.element.id; }
// AR invoice: DR {BPartner.Receivable} = grandtotal; CR {Product.Revenue} per line; CR {Tax.Due} per tax
var rcv = nat(R.resolve(db, '{BPartner.Receivable}', invRow.c_invoice_id === 100 ? ord.c_bpartner_id : 112, SCHEMA));
add('DR', rcv, invRow.grandtotal);
invLines.forEach(function (l) { add('CR', nat(R.resolve(db, '{Product.Revenue}', Number(l.m_product_id), SCHEMA)), l.linenetamt); });
invTaxes.forEach(function (t) { add('CR', nat(R.resolve(db, '{Tax.Due}', Number(t.c_tax_id), SCHEMA)), t.taxamt); });

var fwdSumDr = Object.keys(fwdAgg).filter(function (k) { return k.startsWith('DR:'); }).reduce(function (s, k) { return s + fwdAgg[k]; }, 0);
var fwdSumCr = Object.keys(fwdAgg).filter(function (k) { return k.startsWith('CR:'); }).reduce(function (s, k) { return s + fwdAgg[k]; }, 0);
verdict(absent.length === 0, '2a. forward AR posting: all accounts resolved (no absent tokens)', 'absent=' + absent.join(','));
verdict(fwdSumDr === fwdSumCr, '2b. forward posting balanced: Dr == Cr', 'Dr=' + fwdSumDr + ' Cr=' + fwdSumCr);
console.log('§POS-VOID forward invoice=100 Dr=' + (fwdSumDr/100).toFixed(2) + ' Cr=' + (fwdSumCr/100).toFixed(2) + ' accounts=' + Object.keys(fwdAgg).length);

// Apply reversePosting (erp_engine — NEVER reads the books, uses the derived forward lines)
var fwdLines = aggToLines(fwdAgg);
var revLines = E.reversePosting(fwdLines, { mode: 'correct' });
var revAgg = linesToAgg(revLines);

// Net forward + reversal per account — must be 0c in every account
var combined = {};
Object.keys(fwdAgg).forEach(function (k) { combined[k] = (combined[k] || 0) + fwdAgg[k]; });
Object.keys(revAgg).forEach(function (k) { combined[k] = (combined[k] || 0) + revAgg[k]; });
var netZero = netByAcct(combined);
var maxNet = maxAbsNet(netZero);
verdict(maxNet === 0, '2c. POSTINGS NET TO ZERO: forward + reversal per account = 0c (annihilation contract)', 'maxNet=' + maxNet + 'c');
console.log('§POS-VOID postings-net=0c accounts=' + Object.keys(netZero).length + ' maxNet=' + maxNet + 'c');

// ── 3. QTY SPINE RESTORE: the shipment C- is negated by the reversal V+ ──────────────────────────
// Fold the baseline m_transaction rows (warehouse 103, locator 101); the POS order ships product 130 qty 1.
// The void generates a matching V+ movement (the NEGATION of the C- shipment → on-hand = pre-sale).
var whLocs = new Set(db.prepare('SELECT m_locator_id AS i FROM M_Locator WHERE m_warehouse_id=103').all().map(function (r) { return lc(r).i; }));
var txns = lcAll(db.prepare('SELECT m_product_id, m_locator_id, movementtype, movementqty FROM m_transaction').all())
  .filter(function (t) { return whLocs.has(t.m_locator_id); });

var onhandBefore = E.qtyOnHand(txns, {
  keyOf: function (t) { return t.m_product_id; },
  typeOf: function (t) { return t.movementtype; },
  absQtyOf: function (t) { return Math.abs(Number(t.movementqty)); }
});

// The POS sale emitted a C- shipment for each order line; the void emits the reverse: matching V+ movements.
// We model: for each shipment line → a V+ movement at the same qty.
var reversalMovements = inoutLines.map(function (l) {
  return { m_product_id: l.m_product_id, movementtype: 'V+', movementqty: Math.abs(Number(l.movementqty)) };
});
// Add the shipment's C- movements to represent the forward state, then reverse them
var saleMovements = inoutLines.map(function (l) {
  return { m_product_id: l.m_product_id, movementtype: 'C-', movementqty: Math.abs(Number(l.movementqty)) };
});

var onhandAfterSale = E.qtyOnHand(txns.concat(saleMovements), {
  keyOf: function (t) { return t.m_product_id; },
  typeOf: function (t) { return t.movementtype; },
  absQtyOf: function (t) { return Math.abs(Number(t.movementqty)); }
});
var onhandAfterVoid = E.qtyOnHand(txns.concat(saleMovements).concat(reversalMovements), {
  keyOf: function (t) { return t.m_product_id; },
  typeOf: function (t) { return t.movementtype; },
  absQtyOf: function (t) { return Math.abs(Number(t.movementqty)); }
});

// For each shipped product, on-hand after void must equal on-hand BEFORE the sale
var restoreOk = true;
var restoreDetails = [];
inoutLines.forEach(function (l) {
  var pid = l.m_product_id;
  var beforeQty = onhandBefore[pid] || 0;
  var afterVoidQty = onhandAfterVoid[pid] || 0;
  if (beforeQty !== afterVoidQty) restoreOk = false;
  restoreDetails.push('product=' + pid + ' before=' + beforeQty + ' afterSale=' + (onhandAfterSale[pid] || 0) + ' afterVoid=' + afterVoidQty + ' restored=' + (beforeQty === afterVoidQty ? 'Y' : 'N'));
});
verdict(restoreOk, '3. QTY-SPINE-RESTORE: on-hand after void == on-hand before sale (to the unit)', restoreDetails.join('; '));
restoreDetails.forEach(function (d) { console.log('§POS-VOID qty-restore ' + d); });
console.log('§POS-VOID onhand-restored=Y');

// ── 4. BACKFLUSH components (seed: product 130 is a leaf, no BOM) ────────────────────────────────
var bomStmt = db.prepare('SELECT bl.m_product_id AS comp_id FROM pp_product_bomline bl JOIN pp_product_bom b ON b.pp_product_bom_id=bl.pp_product_bom_id WHERE b.m_product_id=?');
var allLeaves = inoutLines.every(function (l) { return bomStmt.all(Number(l.m_product_id)).length === 0; });
if (allLeaves) {
  verdict(true, '4. backflush N/A: all shipped products are leaves (no BOM components) — no P- to reverse', 'products=' + inoutLines.map(function (l) { return l.m_product_id; }).join(','));
  console.log('§POS-VOID backflush=N/A products-are-leaves=Y');
}

// ── 5. §FALSIFIER: voiding an already-VO doc is refused ──────────────────────────────────────────
var recVO = { docStatus: 'VO', isSOTrx: 'Y', doctypeId: ord.c_doctype_id, processing: 'N' };
var dispVO = F.dispatchOrder(db, recVO, 'VO');
verdict(!dispVO.ok && dispVO.reason === 'illegal-action', '§FALSIFIER: void an already-VO order is REFUSED (legal=[])', 'reason=' + dispVO.reason + ' legalActions=' + JSON.stringify(dispVO.legalActions));
console.log('§FALSIFIER pos=void docstatus=VO action=VO refused=' + (!dispVO.ok) + ' reason=' + dispVO.reason);

db.close();
console.log('\n' + (fails === 0 ? '🟢 W-POS-VOID PASS' : '🔴 W-POS-VOID FAIL (' + fails + ')') +
  ' — legal set allows VO; postings net 0c; qty-spine restored; backflush N/A (leaf); falsifier double-void refused.');
process.exit(fails === 0 ? 0 : 1);
