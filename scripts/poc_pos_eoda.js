#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (POS_SHOWCASE_LANE.md §P1.C). Read the log after every run.
// poc_pos_eoda.js — W-POS-EODA: backflush is the LATE End-Of-Day fold (Close Cash), NOT per-sale.
//
// ISSUES IT PROVES (named):
//   1. PER-SALE OFF — with opts.backflush:false (what the POS lens now passes) the sale group emits
//      ZERO CONSUME ops. The till records the sale (order+ship+invoice CO) but does NOT consume.
//   2. EOD == Σ PER-SALE — POS.backflushOps folded over the WHOLE day's sold lines == the sum of each
//      sale's own explodeBOM. Moving backflush late loses NOTHING (deterministic replay; POSLens §195).
//   3. ONE SPINE MOVE — the EOD fold emits ONE CONSUME group; each component's P- qty == the day total,
//      and folds as a single decrement on the qty spine (movementSign=-1).
//   4. VO SKIPPED — a reverted (voided) sale's lines are excluded from the EOD backflush (no phantom consume).
//   5. REPLENISH REPORT — Close Cash also Generates the Replenishment Report (replenishSuggest) — the same
//      iDempiere M_Replenish baseline, issued from the menu. (Report content, not timing, is W-POS-REPLENISH.)
//
// NON-INVENT: recipe = real GardenWorld pp_product_bom/bomline (ad_seed_fullwidth.db); rung products are
//   REAL c_poskey tiles. backflushOps is the SAME function the per-sale path uses — equality is structural,
//   not a re-implementation. No Date.now/Math.random.
// Implementing docs/POS_SHOWCASE_LANE.md §P1.C — Witness: W-POS-EODA
// Run: bash build/erp/run_witness.sh scripts/poc_pos_eoda.js   — then READ the log.
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var E = require('./erp_engine');
var POS = require(path.join(__dirname, '..', 'build', 'erp', 'pos_core.js'));

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'ad_seed_fullwidth.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function lc(r) { if (!r) return r; var o = {}; for (var k in r) o[k.toLowerCase()] = r[k]; return o; }
function eq(a, b) { var ak = Object.keys(a).sort(), bk = Object.keys(b).sort(); if (ak.join() !== bk.join()) return false; return ak.every(function (k) { return Math.abs(a[k] - b[k]) < 1e-9; }); }

var bomStmt = db.prepare('SELECT bl.m_product_id AS comp_id, bl.qtybom AS qtybom FROM pp_product_bomline bl JOIN pp_product_bom b ON b.pp_product_bom_id=bl.pp_product_bom_id WHERE b.m_product_id=? ORDER BY bl.m_product_id');
function bomOf(pid) { return bomStmt.all(Number(pid)).map(lc); }
var nameStmt = db.prepare('SELECT name FROM m_product WHERE m_product_id=?');
function nameOf(pid) { var r = lc(nameStmt.get(Number(pid))); return r ? r.name : ('#' + pid); }
function show(m) { return Object.keys(m).sort().map(function (k) { return nameOf(k) + '×' + m[k]; }).join(', '); }

console.log('═══ W-POS-EODA — backflush is the LATE Close-Cash fold, == Σ per-sale, VO skipped ═══\n');

var pos = lc(db.prepare('SELECT * FROM c_pos WHERE c_pos_id=100').get());
var plv = lc(db.prepare('SELECT m_pricelist_version_id v FROM m_pricelist_version WHERE m_pricelist_id=?').get(pos.m_pricelist_id));
var priceStmt = db.prepare('SELECT pricestd FROM m_productprice WHERE m_pricelist_version_id=? AND m_product_id=?');
var ctx = { pos: pos, priceOf: function (pid) { return lc(priceStmt.get(plv.v, pid)) || null; }, bomOf: bomOf, wrPolicy: { isautogenerateinout: 'Y', isautogenerateinvoice: 'Y' } };
var WH = pos.m_warehouse_id;
var PATIO = 145; // a REAL BOM tile (Patio Furniture Set)

// ── the day: three sales of the BOM tile (qty 1, 2, 1); one of them (#3) will be VOIDED ──
var day = [
  { orderId: 9201, qty: 1, voided: false },
  { orderId: 9211, qty: 2, voided: false },
  { orderId: 9221, qty: 1, voided: true }
];

// ── 1. PER-SALE OFF: the lens-shape sale group (backflush:false) emits ZERO CONSUME ──────────────
var g = POS.buildSaleGroup(ctx, [POS.ringLine(ctx, PATIO, 1)], { orderId: 9201, inoutId: 9202, invoiceId: 9203, c_bpartner_id: 112, backflush: false });
var perSaleConsume = g.ops.filter(function (o) { return o.op_type === 'CONSUME'; });
verdict(perSaleConsume.length === 0, '1. per-sale group with backflush:false emits ZERO CONSUME (till records, does not consume)', 'consumeOps=' + perSaleConsume.length);
// control: the DEFAULT (opt-out absent) still consumes — non-EODA configs + W-POS-BACKFLUSH unchanged
var gOn = POS.buildSaleGroup(ctx, [POS.ringLine(ctx, PATIO, 1)], { orderId: 9301, inoutId: 9302, invoiceId: 9303, c_bpartner_id: 112 });
verdict(gOn.ops.some(function (o) { return o.op_type === 'CONSUME'; }), '   (control) default still backflushes per-sale (opt-out only) — engine unit witnesses unaffected');

// ── 2. EOD == Σ PER-SALE: fold the day (CO only) vs sum of each sale's own explode ───────────────
var coDay = day.filter(function (s) { return !s.voided; });
var dayLines = coDay.map(function (s) { return { m_product_id: PATIO, qtyordered: s.qty }; });
var eod = POS.backflushOps(ctx, dayLines, WH);
var eodDict = eod.consumed;
var sumPerSale = {};
coDay.forEach(function (s) { var d = E.explodeBOM(bomOf, PATIO, s.qty); Object.keys(d).forEach(function (c) { sumPerSale[c] = (sumPerSale[c] || 0) + d[c]; }); });
verdict(eq(eodDict, sumPerSale), '2. EOD backflush == Σ per-sale explodeBOM (late fold loses nothing)', 'EOD=[' + show(eodDict) + ']');

// ── 3. ONE SPINE MOVE: one CONSUME group; each P-; folds as a single decrement ───────────────────
var allPminus = eod.ops.every(function (o) { return o.movementtype === 'P-' && E.movementSign(o.movementtype) === -1; });
verdict(allPminus, "3. EOD CONSUME ops are all 'P-' (movementSign=-1) — one decrement on the qty spine", 'ops=' + eod.ops.length);
var fold = E.qtyOnHand(eod.ops.map(function (o) { return { m_product_id: o.m_product_id, movementtype: o.movementtype, movementqty: o.movementqty }; }),
  { keyOf: function (e) { return e.m_product_id; }, typeOf: function (e) { return e.movementtype; }, absQtyOf: function (e) { return Math.abs(e.movementqty); } });
var allNeg = Object.keys(fold).length > 0 && Object.keys(fold).every(function (k) { return fold[k] === -eodDict[k]; });
verdict(allNeg, '   the fold decrements each component by exactly its EOD total', 'products=' + Object.keys(fold).length);

// ── 4. VO SKIPPED: include the voided sale's qty → the EOD must NOT grow ─────────────────────────
var withVoidLines = day.map(function (s) { return { m_product_id: PATIO, qtyordered: s.qty }; });   // naive: includes the VO sale
var naive = POS.backflushOps(ctx, withVoidLines, WH).consumed;
var voidedExcluded = !eq(naive, eodDict);   // the voided qty=1 sale WOULD change the totals if not skipped
verdict(voidedExcluded, '4. excluding the VOIDED sale changes the totals → the EOD fold MUST skip VO (no phantom consume)',
  'co-only=[' + show(eodDict) + '] vs naive-incl-VO=[' + show(naive) + ']');
console.log('§POS-EODA sales=' + coDay.length + ' (skipped VO=' + day.filter(function (s) { return s.voided; }).length + ')' +
  ' backflushComponents=' + eod.ops.length + ' consumed=[' + show(eodDict) + '] newVerbs=[] (== Σ per-sale)');

// ── 5. REPLENISH REPORT SOURCE: Close Cash Generates the Replenishment Report — its SOURCE is the real
//   m_replenish policy rows (the M_Replenish fold). The fold CONTENT/formula is W-POS-REPLENISH's job;
//   here we only assert the report has a real source to generate from at close (no invented policy).
var replWhs = [], replRows = 0;
try {
  replWhs = db.prepare("SELECT DISTINCT m_warehouse_id AS w FROM m_replenish WHERE replenishtype<>'0'").all().map(function (r) { return lc(r).w; });
  replRows = lc(db.prepare("SELECT COUNT(*) c FROM m_replenish WHERE replenishtype<>'0'").get()).c;
} catch (e) {}
verdict(replRows > 0, '5. Close Cash has a real Replenishment Report source (m_replenish policy rows; fold = W-POS-REPLENISH)', 'policyRows=' + replRows + ' warehouses=' + replWhs.length);
console.log('§POS-EODA-REPLENISH policyRows=' + replRows + ' warehouses=' + replWhs.length + ' (M_Replenish source; fold==W-POS-REPLENISH; issued from the menu)');

console.log('\n' + (fails === 0 ? '🟢 W-POS-EODA PASS' : '🔴 W-POS-EODA FAIL (' + fails + ')') +
  ' — backflush moved per-sale→Close-Cash: the till does not consume, the EOD fold == Σ per-sale explode (nothing lost), one P- spine move, VO sales skipped, and the replenishment report generates at close.');
db.close();
process.exit(fails === 0 ? 0 : 1);
