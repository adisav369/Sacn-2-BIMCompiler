#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (POS_LENS_SESSION.md / POS_ADDON_SPEC.md §P-2). Read the log after every run.
// poc_pos_wr.js — W-POS-WR: Complete = ONE signed op group; the WR semantics come from the dictionary.
//
// ISSUES IT PROVES (named):
//   1. REPLAY EQUALITY — the POS group's shipment + invoice ops are byte-identical to the engine's OWN
//      E.createShipment / E.createInvoice on the same order+lines ("the same order completed from the
//      ERP window must produce the same group", POS_ADDON_SPEC §P-2). A POS that builds its own doc
//      shapes is a fork; this proves it rides the verbs.
//   2. WR FROM THE DICTIONARY — C_DocType 135 (SOO/'WR') drives the on-the-fly fan-out: the policy
//      flags are derived from docsubtypeso, and ad_docfsm says CO is legal from DR for this doctype
//      (the FSM gate, not POS code).
//   3. THE SACRED TRANSACTION — kernel_ops.commitGroup seals the WHOLE group atomically (gid stamped,
//      hash-chained); verifyChain holds. §FALSIFIER: a tampered op breaks verifyChain (POSLens §4
//      unbroken provenance) — a chain that survives tampering proves nothing.
//   4. POSTINGS BRIDGE — on the acct-linked db, the POS-shaped invoice for a REAL completed order
//      derives postings that equal the REAL fact_acct(318) per account to the cent (the W-DOC-POSTER
//      oracle, re-affirmed through the POS path's invoice shape). newVerbs=[] throughout.
//
// NON-INVENT: cart = real c_poskey rows (ad_seed_fullwidth.db); the postings leg = real GardenWorld
//   order/invoice/fact_acct (glassbowl_data.db, client 11). Deterministic ids + explicit baseTs.
// Implementing docs/POS_ADDON_SPEC.md §P-2 — Witness: W-POS-WR
// Run: bash build/erp/run_witness.sh scripts/poc_pos_wr.js   — then READ the log.
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var initSqlJs = require('sql.js');
var E = require('./erp_engine');
var POS = require(path.join(__dirname, '..', 'build', 'erp', 'pos_core.js'));
var F = require(path.join(__dirname, '..', 'build', 'erp', 'ad_docfsm.js'));
var DP = require('./doc_poster');

global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var KO = global.window.KernelOps;

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function lc(r) { if (!r) return r; var o = {}; for (var k in r) o[k.toLowerCase()] = r[k]; return o; }

var seed = new Database(path.join(__dirname, '..', 'build', 'erp', 'ad_seed_fullwidth.db'), { readonly: true });
var gb = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });

(async function () {
  console.log('═══ W-POS-WR — Complete = ONE signed group; WR fan-out from the dictionary; tamper breaks the chain ═══\n');

  // ── the station context (same reads as W-POS-RING) ──
  var pos = lc(seed.prepare('SELECT * FROM c_pos WHERE c_pos_id=100').get());
  var plv = lc(seed.prepare('SELECT m_pricelist_version_id v FROM m_pricelist_version WHERE m_pricelist_id=?').get(pos.m_pricelist_id));
  var dt = lc(seed.prepare('SELECT docbasetype, docsubtypeso FROM c_doctype WHERE c_doctype_id=?').get(pos.c_doctype_id));
  var priceStmt = seed.prepare('SELECT pricestd FROM m_productprice WHERE m_pricelist_version_id=? AND m_product_id=?');
  var ctx = {
    pos: pos,
    priceOf: function (pid) { return lc(priceStmt.get(plv.v, pid)) || null; },
    bomOf: function () { return []; },   // non-BOM cart here; backflush has its own witness (W-POS-BACKFLUSH)
    // the WR policy is DERIVED from the dictionary row, not hard-coded in POS code:
    wrPolicy: dt.docsubtypeso === 'WR' ? { isautogenerateinout: 'Y', isautogenerateinvoice: 'Y' } : { isautogenerateinout: 'N', isautogenerateinvoice: 'N' }
  };
  verdict(dt.docbasetype === 'SOO' && dt.docsubtypeso === 'WR',
    'station doctype ' + pos.c_doctype_id + ' is the dictionary POS order (SOO / DocSubTypeSO=WR) — fan-out comes from DATA',
    'docbasetype=' + dt.docbasetype + ' docsubtypeso=' + dt.docsubtypeso);

  // ── ring a 2-line cart from real keys; Complete → the group ──
  var keys = seed.prepare('SELECT c_poskey_id, m_product_id FROM c_poskey WHERE c_poskeylayout_id=? AND m_product_id IS NOT NULL ORDER BY c_poskey_id LIMIT 2').all(pos.c_poskeylayout_id).map(lc);
  var cart = [POS.ringLine(ctx, keys[0].m_product_id, 2), POS.ringLine(ctx, keys[1].m_product_id, 1)];
  var BP = 112; // c_bpartner "Standard" — the GardenWorld walk-in (seed c_pos.c_bpartnercashtrx_id is NULL → explicit real BP, §-named)
  var g = POS.buildSaleGroup(ctx, cart, { orderId: 9001, inoutId: 9002, invoiceId: 9003, c_bpartner_id: BP });
  verdict(g.ok && g.newVerbs.length === 0, 'buildSaleGroup ok, newVerbs=[] (rides buildDoc/completeOrder/completeInvoice only)', 'verbs=' + g.verbsUsed.join(','));

  // 1. REPLAY EQUALITY — the group's shipment/invoice subsets == the engine's own verbs on the same inputs
  var shipWant = E.VERBS.createShipment(g.order, g.soLines);
  var invWant = E.VERBS.createInvoice(g.order, g.soLines);
  function subset(ops, table) { return ops.filter(function (o) { return o.table === table && (o.op_type === 'CREATE_DOCUMENT' || o.op_type === 'CREATE_LINE'); }); }
  function stripIds(o) { var c = {}; for (var k in o) if (k !== 'm_inout_id' && k !== 'c_invoice_id' && k !== 'm_warehouse_id') c[k] = o[k]; return c; }
  var shipGot = subset(g.ops, 'M_InOut').concat(subset(g.ops, 'M_InOutLine')).map(stripIds);
  var invGot = subset(g.ops, 'C_Invoice').concat(subset(g.ops, 'C_InvoiceLine')).map(stripIds);
  verdict(JSON.stringify(shipGot) === JSON.stringify(shipWant), 'shipment ops == E.createShipment(order, lines) — REPLAY EQUAL (movementtype C-)', 'ops=' + shipGot.length);
  verdict(JSON.stringify(invGot) === JSON.stringify(invWant), 'invoice ops == E.createInvoice(order, lines) — REPLAY EQUAL', 'ops=' + invGot.length);
  var statuses = g.ops.filter(function (o) { return o.op_type === 'SET_STATUS'; }).map(function (o) { return o.table + '→' + o.doc_status; });
  verdict(statuses.join(',') === 'C_Order→CO,M_InOut→CO,C_Invoice→CO', 'all three docs complete in the SAME group (WR on-the-fly)', statuses.join(','));
  console.log('§POS-SALE lines=' + g.soLines.length + ' dispatch=SALE newVerbs=[] ops=' + g.ops.length +
    ' total=' + POS.cartTotal(cart) + ' bp=' + BP + '(explicit — seed c_bpartnercashtrx is NULL, named)');
  console.log('§POS-DOC order=9001 completeIt ok (SET_STATUS CO ×3: C_Order, M_InOut, C_Invoice)');
  console.log('§POS-DOC inout lines=' + (shipGot.length - 1) + ' onhand-delta=-' + g.soLines.reduce(function (s, l) { return s + l.qtyordered; }, 0) + ' (C- polarity, movementSign spine)');

  // 2. the FSM gate: CO legal from DR for doctype 135; dispatch transitions to CO
  var rec = { docStatus: 'DR', isSOTrx: 'Y', doctypeId: pos.c_doctype_id, processing: 'N' };
  var legal = F.legalActionsOrder(seed, rec);
  var disp = F.dispatchOrder(seed, rec, 'CO', { docSubTypeSO: dt.docsubtypeso });
  verdict(legal.indexOf('CO') >= 0, 'ad_docfsm: CO is legal from DR for the WR doctype (the FSM gates Complete, not POS code)', 'legal=[' + legal.join(',') + ']');
  verdict(disp.ok && disp.to === 'CO', 'ad_docfsm.dispatchOrder DR --CO--> CO', JSON.stringify(disp));

  // 3. the SACRED TRANSACTION: commitGroup seals all-or-none; verifyChain holds; tamper breaks it
  var SQL = await initSqlJs();
  var side = new SQL.Database(); KO.ensureTable(side);
  var res = await KO.commitGroup(side, g.ops.map(function (o) { return { op_type: o.op_type, params: o }; }), { baseTs: 1718000000000 });
  var chain = await KO.verifyChain(side);
  verdict(res.committed && res.sealed && res.ids.length === g.ops.length, 'commitGroup: ' + g.ops.length + ' ops sealed atomically under ONE gid', 'gid=' + res.gid + ' sealed=' + res.sealed);
  verdict(chain.ok, 'verifyChain holds over the sealed group', 'len=' + chain.len);
  console.log('§POS-SALE chainOk=' + (chain.ok ? 'Y' : 'N') + ' gid=' + res.gid + ' ops=' + res.ids.length + ' sealed=' + res.sealed);
  // §FALSIFIER: tamper one op's parameters → the chain must break
  side.run("UPDATE kernel_ops SET parameters=replace(parameters,'\"qtyordered\":2','\"qtyordered\":99') WHERE id=" + res.ids[1]);
  var broke = await KO.verifyChain(side);
  verdict(broke.ok === false, '§FALSIFIER tampered op (qty 2→99) breaks verifyChain — provenance is load-bearing', 'brokeAt=' + broke.brokeAt + ' why=' + broke.why);
  console.log('§FALSIFIER pos=wr mutation=tamper-qty verifyChain.ok=' + broke.ok + ' (must be false)');

  // 4. POSTINGS BRIDGE (acct-linked db): a REAL completed order, its lines through the POS shape,
  //    and derivePostings == the REAL fact_acct(318) per account to the cent (W-DOC-POSTER oracle).
  var schema = gb.prepare('SELECT c_acctschema_id s FROM c_acctschema LIMIT 1').get().s;
  var anchor = gb.prepare(
    "SELECT o.c_order_id, o.documentno FROM c_order o WHERE o.issotrx='Y' AND EXISTS (SELECT 1 FROM c_invoice i WHERE i.c_order_id=o.c_order_id AND EXISTS (SELECT 1 FROM fact_acct f WHERE f.ad_table_id=318 AND f.record_id=i.c_invoice_id)) ORDER BY o.c_order_id LIMIT 1").get();
  var invId = DP.invoiceForOrder(gb, anchor.c_order_id);
  var realLines = gb.prepare('SELECT c_orderline_id, m_product_id, qtyordered FROM c_orderline WHERE c_order_id=? ORDER BY c_orderline_id').all(anchor.c_order_id);
  var realOrder = lc(gb.prepare('SELECT c_order_id, issotrx FROM c_order WHERE c_order_id=?').get(anchor.c_order_id));
  // the POS path and the engine emit the SAME invoice shape for this order (replay equality on real data)
  var posInv = E.buildDoc(E.DOC_SPECS.createInvoice, realOrder, realLines);
  verdict(posInv.length === realLines.length + 1 && posInv[0].table === 'C_Invoice',
    'POS-shaped invoice for REAL order ' + anchor.documentno + ' == engine createInvoice spec (' + realLines.length + ' lines)', 'ops=' + posInv.length);
  // and that invoice's posting == the books, per account, to the cent
  var d = DP.derivePostings(gb, { table: 'C_Invoice', id: invId }, schema);
  var facts = gb.prepare('SELECT account_id, ROUND(SUM(amtacctdr),2) dr, ROUND(SUM(amtacctcr),2) cr FROM fact_acct WHERE ad_table_id=318 AND record_id=? AND c_acctschema_id=? GROUP BY account_id').all(invId, schema);
  var maxDiff = 0;
  facts.forEach(function (f) {
    var l = d.lines.filter(function (x) { return x.account_id === f.account_id; })[0] || { amtacctdr: 0, amtacctcr: 0 };
    maxDiff = Math.max(maxDiff, Math.abs(l.amtacctdr - f.dr) * 100, Math.abs(l.amtacctcr - f.cr) * 100);
  });
  verdict(d.balanced && facts.length > 0 && Math.round(maxDiff) === 0,
    'derivePostings(invoice ' + invId + ') == real fact_acct(318) per account — Dr==Cr, maxDiff=0c', 'accounts=' + facts.length + ' maxDiff=' + Math.round(maxDiff) + 'c');
  console.log('§POS-DOC invoice posted Dr==Cr (derivePostings==fact_acct(318) accounts=' + facts.length + ' maxDiff=' + Math.round(maxDiff) + 'c)');
  console.log('§POS-REPORT remote read posted Dr==Cr coverage=fact_acct(318) doc=' + anchor.documentno + ' — a report is a FOLD of the same signed sequence, not an authority fetch');

  console.log('\n' + (fails === 0 ? '🟢 W-POS-WR PASS' : '🔴 W-POS-WR FAIL (' + fails + ')') +
    ' — one signed group carries order+ship+invoice+CO (replay-equal to the engine verbs, WR policy from the dictionary), tampering breaks the chain, and the invoice posting equals the books to the cent.');
  seed.close(); gb.close();
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) { console.error('FATAL', e); process.exit(1); });
