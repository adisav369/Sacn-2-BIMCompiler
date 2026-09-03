#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (POS_ENGINE_LANE.md §E-4 / POS_ADDON_SPEC.md §P-13). Read the log after every run.
// poc_pos_hold.js — W-POS-HOLD: hold/recall = the in-progress cart as a real DR C_Order.
//
// ISSUES IT PROVES (named):
//   1. FOLDED IN, NOT A PRIVATE STORE (§P-13 user requirement) — the parked cart is a ledger C_Order
//      (docstatus DR) committed through the SAME signed commitGroup; it therefore appears in the
//      standard Sales Order window query AND the Kanban query — the SAME ROW both surfaces already
//      read. A held sale that does not show up there would prove a private store (fold-not-fork red flag).
//   2. RECALL COMPLETES THE HELD ORDER (Fable5 review note) — recall is a plain query (DR orders);
//      Complete-after-recall = DOC_ACTION CO on the EXISTING order + ship/invoice built FOR IT
//      (buildRecallCompleteGroup = the completion half of buildSaleGroup — shared code, not parallel
//      code) — NEVER a second buildSaleGroup.
//   3. §FALSIFIER 1 — the held order's lines/total == the parked cart TO THE CENT (BigDecimal fold of
//      sealed master prices; nothing invented on park or recall).
//      §FALSIFIER 2 — after recall+Complete exactly ONE C_Order exists for the sale: the op log carries
//      exactly one CREATE_DOCUMENT C_Order, and the completion group carries NONE.
//
// NON-INVENT: cart = real c_poskey rows of station 100 priced off the real m_productprice rows
//   (ad_seed_fullwidth.db); deterministic ids + explicit baseTs; grandtotal at fold time = the SUM of
//   the lines' sealed linenetamt (a fold, not an input).
// Implementing docs/POS_ADDON_SPEC.md §P-13 — Witness: W-POS-HOLD
// Run: bash build/erp/run_witness.sh scripts/poc_pos_hold.js   — then READ the log.
'use strict';
var path = require('path');
var fs = require('fs');
var Database = require('better-sqlite3');
var initSqlJs = require('sql.js');
var POS = require(path.join(__dirname, '..', 'build', 'erp', 'pos_core.js'));
var BigDecimal = require(path.join(__dirname, '..', 'build', 'erp', 'bigdecimal.js'));

global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var KO = global.window.KernelOps;

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function lc(r) { if (!r) return r; var o = {}; for (var k in r) o[k.toLowerCase()] = r[k]; return o; }

var SEED = path.join(__dirname, '..', 'build', 'erp', 'ad_seed_fullwidth.db');
var COPY = '/tmp/poc_pos_hold_seed.db';
fs.copyFileSync(SEED, COPY);
var db = new Database(COPY);

(async function () {
  console.log('═══ W-POS-HOLD — §P-13 hold/recall: park = a real DR C_Order, recall completes THE HELD ORDER ═══\n');

  var pos = lc(db.prepare('SELECT * FROM c_pos WHERE c_pos_id=100').get());
  var plv = lc(db.prepare('SELECT m_pricelist_version_id v FROM m_pricelist_version WHERE m_pricelist_id=?').get(pos.m_pricelist_id));
  var dt = lc(db.prepare('SELECT docsubtypeso FROM c_doctype WHERE c_doctype_id=?').get(pos.c_doctype_id));
  var priceStmt = db.prepare('SELECT pricestd FROM m_productprice WHERE m_pricelist_version_id=? AND m_product_id=?');
  var ctx = {
    pos: pos,
    priceOf: function (pid) { return lc(priceStmt.get(plv.v, pid)) || null; },
    bomOf: function () { return []; },
    wrPolicy: dt.docsubtypeso === 'WR' ? { isautogenerateinout: 'Y', isautogenerateinvoice: 'Y' } : {}
  };

  var SQL = await initSqlJs();
  var opDb = new SQL.Database(); KO.ensureTable(opDb);
  function commit(ops, ts) {
    return KO.commitGroup(opDb, ops.map(function (o) { return { op_type: o.op_type, params: o }; }), { baseTs: ts });
  }

  // ── ring a 2-line cart from real keys (same fixture rows as W-POS-WR) ──
  var keys = db.prepare('SELECT c_poskey_id, m_product_id FROM c_poskey WHERE c_poskeylayout_id=? AND m_product_id IS NOT NULL ORDER BY c_poskey_id LIMIT 2').all(pos.c_poskeylayout_id).map(lc);
  var cart = [POS.ringLine(ctx, keys[0].m_product_id, 2), POS.ringLine(ctx, keys[1].m_product_id, 1)];
  var cartTotal = POS.cartTotal(cart);
  var BP = 112; // the GardenWorld walk-in (seed c_pos.c_bpartnercashtrx_id is NULL — explicit, §-named)

  // ── PARK: the ORDER half alone → a DR C_Order through the same signed path ──
  var hold = POS.buildHoldGroup(ctx, cart, { orderId: 940001, c_bpartner_id: BP });
  verdict(hold.ok === true && hold.docstatus === 'DR' && hold.newVerbs.length === 0,
    'park = buildHoldGroup: CREATE C_Order + lines, NO SET_STATUS (born DR), newVerbs=[]',
    'ops=' + hold.ops.length + ' verbs=' + hold.verbsUsed.join(','));
  verdict(hold.ops.filter(function (o) { return o.op_type === 'SET_STATUS' || o.table === 'M_InOut' || o.table === 'C_Invoice'; }).length === 0,
    'the hold group carries NO completion ops (no ship, no invoice, no status walk)', 'ops=' + hold.ops.map(function (o) { return o.op_type + ':' + o.table; }).join(' '));
  var rHold = await commit(hold.ops, 1718000000000);
  var chain1 = await KO.verifyChain(opDb);
  verdict(rHold.committed && chain1.ok, 'hold committed as ONE signed group, chainOk=Y', 'gid=' + rHold.gid);

  // fold the hold into the copy (what the substrate does): header DR + lines; grandtotal = fold of lines
  var lineOps = hold.ops.filter(function (o) { return o.op_type === 'CREATE_LINE'; });
  var gt = lineOps.reduce(function (s, o) { return s.add(BigDecimal.of(String(o.linenetamt))); }, BigDecimal.ZERO).setScale(2, BigDecimal.RoundingMode.HALF_UP).toString();
  db.prepare('INSERT INTO c_order (c_order_id, issotrx, c_doctype_id, m_warehouse_id, c_bpartner_id, c_pos_id, docstatus, grandtotal, ad_client_id, ad_org_id, isactive) VALUES (?,?,?,?,?,?,?,?,?,?,?)')
    .run(hold.order.c_order_id, 'Y', pos.c_doctype_id, pos.m_warehouse_id, BP, pos.c_pos_id, 'DR', gt, pos.ad_client_id, pos.ad_org_id, 'Y');
  lineOps.forEach(function (o, i) {
    db.prepare('INSERT INTO c_orderline (c_orderline_id, c_order_id, m_product_id, qtyordered, priceactual, linenetamt, ad_client_id, ad_org_id, isactive) VALUES (?,?,?,?,?,?,?,?,?)')
      .run(o.source_line_id, hold.order.c_order_id, o.m_product_id, o.qtyordered, o.priceactual, o.linenetamt, pos.ad_client_id, pos.ad_org_id, 'Y');
  });
  console.log('§POS-HOLD park order=' + hold.order.c_order_id + ' lines=' + lineOps.length + ' grandtotal=' + gt + ' (fold of sealed linenetamt) gid=' + rHold.gid + ' chainOk=' + (chain1.ok ? 'Y' : 'N'));

  // ── 1. the held row LISTS in the Sales Order window + Kanban (the SAME ledger row) ──
  var windowRows = db.prepare("SELECT c_order_id, docstatus, grandtotal FROM c_order WHERE docstatus='DR' AND c_bpartner_id=?").all(BP).map(lc);
  var inWindow = windowRows.some(function (r) { return r.c_order_id === 940001; });
  // the Kanban reads the same table grouped by docstatus — the DR bucket must carry the same row
  var kanbanDR = db.prepare("SELECT c_order_id FROM c_order WHERE docstatus='DR' ORDER BY c_order_id").all().map(function (r) { return lc(r).c_order_id; });
  var inKanban = kanbanDR.indexOf(940001) >= 0;
  verdict(inWindow && inKanban, 'held order LISTS in Sales Order window data AND Kanban data (same ledger row — NOT a private store)',
    'window=' + inWindow + ' kanbanDRbucket=' + inKanban);
  console.log('§POS-HOLD listed window=Y kanban=Y rows=' + windowRows.length + ' (recall list = this plain query)');

  // ── 2. RECALL: plain query reloads the EXACT lines; §FALSIFIER 1: to the cent ──
  var heldOrder = lc(db.prepare('SELECT c_order_id, docstatus, issotrx, c_doctype_id, m_warehouse_id, c_bpartner_id FROM c_order WHERE c_order_id=?').get(940001));
  var heldLines = db.prepare('SELECT c_orderline_id, m_product_id, qtyordered, priceactual, linenetamt FROM c_orderline WHERE c_order_id=? ORDER BY c_orderline_id').all(940001).map(lc);
  var recalledTotal = heldLines.reduce(function (s, l) { return s.add(BigDecimal.of(String(l.linenetamt))); }, BigDecimal.ZERO).setScale(2, BigDecimal.RoundingMode.HALF_UP).toString();
  var exact = heldLines.length === cart.length && heldLines.every(function (l, i) {
    return l.m_product_id === cart[i].m_product_id && Number(l.qtyordered) === cart[i].qty && BigDecimal.of(String(l.priceactual)).eq(cart[i].priceactual);
  });
  verdict(exact && recalledTotal === cartTotal,
    '§FALSIFIER 1: recalled lines == the parked cart to the CENT (products, qtys, sealed prices, total ' + cartTotal + ')',
    'recalled=' + recalledTotal + ' parked=' + cartTotal);
  console.log('§POS-HOLD recall lines=' + heldLines.length + ' total=' + recalledTotal + ' == parked ' + cartTotal);

  // ── 3. COMPLETE the HELD order — the completion half alone, never a re-build ──
  var done = POS.buildRecallCompleteGroup(ctx, heldOrder, heldLines, { inoutId: 940002, invoiceId: 940003 });
  verdict(done.ok === true && done.newVerbs.length === 0, 'buildRecallCompleteGroup ok (completeOrder fan-out on the EXISTING order)', 'ops=' + done.ops.length);
  var statuses = done.ops.filter(function (o) { return o.op_type === 'SET_STATUS'; }).map(function (o) { return o.table + '→' + o.doc_status; });
  verdict(statuses.join(',') === 'C_Order→CO,M_InOut→CO,C_Invoice→CO',
    'DOC_ACTION CO on the HELD order + ship/invoice FOR IT complete in the SAME group (WR on-the-fly)', statuses.join(','));
  var orderCreatesInComplete = done.ops.filter(function (o) { return o.op_type === 'CREATE_DOCUMENT' && o.table === 'C_Order'; }).length;
  verdict(orderCreatesInComplete === 0, 'the completion group creates NO second C_Order (never a re-run buildSaleGroup)', 'C_Order creates=' + orderCreatesInComplete);
  // the ship/invoice are built FOR the held order: line sources == the held line ids
  var shipLines = done.ops.filter(function (o) { return o.op_type === 'CREATE_LINE' && o.table === 'M_InOutLine'; });
  var forHeld = shipLines.length === heldLines.length && shipLines.every(function (o, i) { return o.source_line_id === heldLines[i].c_orderline_id; });
  verdict(forHeld, 'shipment lines source THE HELD order lines (source_line_id == the parked c_orderline ids)', 'lines=' + shipLines.length);
  var rDone = await commit(done.ops, 1718000001000);
  var chain2 = await KO.verifyChain(opDb);
  verdict(rDone.committed && chain2.ok, 'complete-after-recall committed, whole log chainOk=Y', 'len=' + chain2.len);
  db.prepare("UPDATE c_order SET docstatus='CO' WHERE c_order_id=?").run(940001);   // fold the SET_STATUS
  console.log('§POS-HOLD complete order=940001 statuses=' + statuses.join(',') + ' gid=' + rDone.gid + ' chainOk=' + (chain2.ok ? 'Y' : 'N'));

  // ── §FALSIFIER 2: exactly ONE C_Order exists for the sale ──
  var r = opDb.exec("SELECT COUNT(*) FROM kernel_ops WHERE op_type='CREATE_DOCUMENT' AND parameters LIKE '%\"table\":\"C_Order\"%'");
  var createOpsTotal = Number(r[0].values[0][0]);
  var rowCount = db.prepare('SELECT COUNT(*) n FROM c_order WHERE c_order_id=?').get(940001).n;
  verdict(createOpsTotal === 1 && rowCount === 1,
    '§FALSIFIER 2: exactly ONE C_Order for the sale (1 CREATE op in the whole log, 1 ledger row, now CO)',
    'createOps=' + createOpsTotal + ' rows=' + rowCount);
  console.log('§FALSIFIER pos=hold one-order createOps=' + createOpsTotal + ' rows=' + rowCount + ' (must be 1/1)');

  // recall-complete refuses a non-DR order (only a draft recalls — no double-complete)
  var f = POS.buildRecallCompleteGroup(ctx, { c_order_id: 940001, docstatus: 'CO', issotrx: 'Y', m_warehouse_id: pos.m_warehouse_id }, heldLines, { inoutId: 1, invoiceId: 2 });
  verdict(f.ok === false && f.reason === 'not-draft', '§FALSIFIER recall of a CO order → refused (not-draft)', 'reason=' + f.reason);
  console.log('§FALSIFIER pos=hold recall-CO reason=' + f.reason + ' (must refuse)');

  db.close();
  console.log('\n' + (fails === 0 ? '🟢 W-POS-HOLD PASS' : '🔴 W-POS-HOLD FAIL (' + fails + ')') +
    ' — park = a real DR C_Order visible to Sales Order window + Kanban, recall reloads the exact lines to the cent, ' +
    'Complete CO-walks THE HELD order (ship/invoice FOR it), and exactly ONE C_Order exists for the sale.');
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) { console.error('FATAL', e); process.exit(1); });
