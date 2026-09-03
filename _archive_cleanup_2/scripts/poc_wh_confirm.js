#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (POS_ENGINE_LANE.md §E-5 / POS_ADDON_SPEC.md §P-12). Read the log after every run.
// poc_wh_confirm.js — W-WH-CONFIRM: the confirmation-layer fold (m_inoutconfirm/m_inoutlineconfirm,
//   doctype 148 "MM Shipment with Pick" path).
//
// ORACLE: real iDempiere Java, read verbatim 2026-06-12 (~/idempiere-dev-setup …/org/compiere/model/) —
//   MInOut.prepareIt:1551 (spawn) · MInOut.completeIt:1648 (gate→IP) · pendingCustomerConfirmations:2203
//   (XC never blocks) · MInOutConfirm.completeIt:394-480 · createDifferenceDoc:604-669 ·
//   MInOutLineConfirm.processLine + beforeSave:202 (Difference = Target − Confirmed − Scrapped).
//   RULE-CONSISTENCY arm (named): GardenWorld has NO completed confirmation cycle to fact-diff against —
//   the PG-replay technique is not drivable here; instead every folded rule cites its source line AND the
//   seed's OWN anchor rows are asserted (confirm 100 = XC/DR/unprocessed on a CO shipment — the live
//   GardenWorld example of the XC-does-not-block rule; its line 100 target=10 confirmed=10 = the
//   suggestion seeding).
//
// ISSUES IT PROVES (named):
//   1. ON-HAND MOVES AT CONFIRM-COMPLETE, NOT AT SALE — with doctype 148 the InOut answers IP at the
//      gate (unprocessed PC confirm) and its movements do NOT enter the qty spine; only after the
//      confirmation completes does the InOut CO and the fold move on-hand — by the PICKED qty.
//   2. THE SPAWN IS DICTIONARY-DRIVEN — IsPickQAConfirm=Y on the real doctype row spawns ONE PC confirm
//      with one line per ship line (TargetQty=MovementQty, ConfirmedQty=TargetQty suggestion); re-spawn
//      is a no-op (idempotent, :create checkExisting).
//   3. §FALSIFIER mismatched qty — EXTRACTED ANSWER (createDifferenceDoc's only two branches):
//      SO-side pick short (confirm 2 of 3, no scrap) → NO difference document; the ship line's
//      MovementQty is REDUCED to the confirmed qty and DifferenceQty=1 stays on the confirm line.
//      Scrap ≠ 0 → an M_Inventory difference doc IS created. PO-side linked diff → AP credit memo.
//      A dispute on a IsSplitWhenDifference=Y doctype → honest named refusal (no seed case, not folded).
//
// NON-INVENT: doctype/products/warehouse/anchor rows = real ad_seed_fullwidth.db; deterministic ids
//   (950xxx band) + explicit baseTs; confirmed/scrapped qtys = the operator's keyed inputs (fixtures).
// Implementing docs/POS_ADDON_SPEC.md §P-12 — Witness: W-WH-CONFIRM
// Run: bash build/erp/run_witness.sh scripts/poc_wh_confirm.js   — then READ the log.
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var initSqlJs = require('sql.js');
var E = require('./erp_engine');
var IC = require(path.join(__dirname, '..', 'build', 'erp', 'inout_confirm.js'));

global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var KO = global.window.KernelOps;

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function lc(r) { if (!r) return r; var o = {}; for (var k in r) o[k.toLowerCase()] = r[k]; return o; }

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'ad_seed_fullwidth.db'), { readonly: true });

(async function () {
  console.log('═══ W-WH-CONFIRM — §P-12 confirmation fold: doctype 148 spawns PC, InOut waits IP, on-hand moves AT CONFIRM ═══\n');

  // ── the dictionary drives everything: real doctype rows ──
  var dt148 = lc(db.prepare('SELECT c_doctype_id, name, docbasetype, ispickqaconfirm, isshipconfirm, issplitwhendifference FROM c_doctype WHERE c_doctype_id=148').get());
  var pol = IC.confirmPolicy(dt148);
  verdict(dt148.docbasetype === 'MMS' && pol.pick === true && pol.ship === false,
    'doctype 148 "' + dt148.name + '": IsPickQAConfirm=Y → the PICK gate comes from the DICTIONARY row', JSON.stringify(pol));

  // the deliver-later shipment (§P-12 shape): 2 real products, real warehouse 104, deterministic ids
  // client/org from the seed's own shipment convention (m_inout 108 = client 11 org 11 — the row the
  // anchor confirm hangs off); the confirm INHERITS these (the real ctor behaviour)
  var ioConv = lc(db.prepare('SELECT ad_client_id, ad_org_id FROM m_inout WHERE m_inout_id=108').get());
  var inout = { m_inout_id: 950001, c_doctype_id: 148, issotrx: 'Y', m_warehouse_id: 104, docstatus: 'IP', ref_inout_id: null,
    ad_client_id: ioConv.ad_client_id, ad_org_id: ioConv.ad_org_id };
  var lines = [
    { m_inoutline_id: 950101, m_product_id: 123, movementqty: 3 },   // Oak Tree (real seed product)
    { m_inoutline_id: 950102, m_product_id: 130, movementqty: 2 }    // Plum Tree
  ];

  // ── 2. the SPAWN (MInOut.prepareIt:1551) — one PC confirm, lines = suggestion seeding ──
  var spawn = IC.createConfirmationOps(inout, lines, dt148, [], { confirmId: 950201, lineIdOf: function (i) { return 950301 + i; } });
  verdict(spawn.length === 3 && spawn[0].table === 'M_InOutConfirm' && spawn[0].confirmtype === 'PC' && spawn[0].docstatus === 'DR',
    'spawn: ONE M_InOutConfirm (type PC, DR) + 2 M_InOutLineConfirm', 'ops=' + spawn.length);
  verdict(spawn[1].targetqty === 3 && spawn[1].confirmedqty === 3 && spawn[2].targetqty === 2 && spawn[2].confirmedqty === 2,
    'confirm lines: TargetQty=MovementQty, ConfirmedQty=TargetQty (the suggestion — setInOutLine, anchored by seed row 100 t=10/c=10)',
    't/c=' + spawn[1].targetqty + '/' + spawn[1].confirmedqty + ',' + spawn[2].targetqty + '/' + spawn[2].confirmedqty);
  // the seed's own anchor row: suggestion seeding + difference 0
  var anchorLine = lc(db.prepare('SELECT targetqty, confirmedqty, differenceqty, scrappedqty FROM m_inoutlineconfirm WHERE m_inoutlineconfirm_id=100').get());
  verdict(Number(anchorLine.targetqty) === Number(anchorLine.confirmedqty) && IC.differenceQty(anchorLine) === 0,
    'SEED ANCHOR: GardenWorld confirm line 100 obeys the same seeding (target==confirmed, difference 0)', JSON.stringify(anchorLine));
  // mandatory dictionary coverage (audit + DocumentNo = substrate stamps, the named omission)
  var AUDIT = { created: 1, createdby: 1, updated: 1, updatedby: 1, documentno: 1 };
  [['M_InOutConfirm', spawn[0]], ['M_InOutLineConfirm', spawn[1]]].forEach(function (t) {
    var tid = lc(db.prepare('SELECT ad_table_id FROM ad_table WHERE LOWER(tablename)=LOWER(?)').get(t[0])).ad_table_id;
    var mand = db.prepare("SELECT columnname FROM ad_column WHERE ad_table_id=? AND ismandatory='Y'").all(tid).map(function (r) { return lc(r).columnname.toLowerCase(); });
    var missing = mand.filter(function (c) { return !AUDIT[c] && !(c in t[1]); });
    verdict(missing.length === 0, t[0] + ': ALL dictionary-mandatory cols covered (audit+DocumentNo = named substrate stamps)',
      missing.length ? 'MISSING ' + missing.join(',') : mand.length + ' mandatory');
  });
  // idempotent: an unprocessed PC already exists → spawn is a no-op (:create checkExisting / :1199 wait)
  var again = IC.createConfirmationOps(inout, lines, dt148, [{ m_inoutconfirm_id: 950201, confirmtype: 'PC', processed: 'N' }], { confirmId: 999, lineIdOf: function () { return 999; } });
  verdict(again.length === 0, 're-spawn with an existing PC confirm → no-op (idempotent, no duplicate confirm)', 'ops=' + again.length);
  console.log('§WH-CONFIRM spawn doctype=148 type=PC lines=2 suggestion=target idempotent=Y');

  // ── 1. the GATE: InOut waits IP; on-hand does NOT move at sale ──
  var confirmRow = { m_inoutconfirm_id: 950201, confirmtype: 'PC', processed: 'N' };
  var gate1 = IC.completeInOutGate([confirmRow]);
  verdict(gate1.ok === false && gate1.status === 'IP' && gate1.open[0] === 950201,
    'completeIt GATE: unprocessed PC confirm → "@Open@: @M_InOutConfirm_ID@", InOut answers IP and WAITS (:1648)', JSON.stringify(gate1));
  // the qty spine: only CO documents' movements fold (the storage update sits BELOW the gate)
  function onhand(events) {
    return E.qtyOnHand(events, { keyOf: function (t) { return t.m_product_id; }, typeOf: function (t) { return t.movementtype; }, absQtyOf: function (t) { return Math.abs(t.movementqty); } });
  }
  var committedMovements = [];                       // nothing CO yet for these products
  var before = onhand(committedMovements);
  verdict((before[123] || 0) === 0 && (before[130] || 0) === 0,
    'ON-HAND UNMOVED while the InOut waits at IP (the sale moved nothing — the §P-12 honesty)', 'oak=' + (before[123] || 0) + ' plum=' + (before[130] || 0));
  console.log('§WH-CONFIRM gate=IP open=950201 onhand-delta=0 (movement sits BELOW the gate)');

  // the seed's OWN XC anchor: inout 108 is CO while its XC confirm 100 is unprocessed → XC never blocks
  var seedConfirm = lc(db.prepare('SELECT m_inoutconfirm_id, confirmtype, processed FROM m_inoutconfirm WHERE m_inoutconfirm_id=100').get());
  var seedInOut = lc(db.prepare('SELECT docstatus FROM m_inout WHERE m_inout_id=(SELECT m_inout_id FROM m_inoutconfirm WHERE m_inoutconfirm_id=100)').get());
  var gateXC = IC.completeInOutGate([seedConfirm]);
  verdict(seedConfirm.confirmtype === 'XC' && seedConfirm.processed === 'N' && seedInOut.docstatus === 'CO' && gateXC.ok === true,
    'SEED ANCHOR: GardenWorld shipment 108 is CO with its XC confirm unprocessed — the gate folds the SAME verdict (XC never blocks, :2208)',
    'seed=' + seedInOut.docstatus + ' gate.ok=' + gateXC.ok);
  console.log('§WH-CONFIRM xc-anchor seed-inout=CO xc-processed=N gate.ok=true (rule == GardenWorld own books)');

  // ── confirm-complete (FULL): operator confirms exactly the suggestion ──
  var SQL = await initSqlJs();
  var opDb = new SQL.Database(); KO.ensureTable(opDb);
  var rSpawn = await KO.commitGroup(opDb, spawn.map(function (o) { return { op_type: o.op_type, params: o }; }), { baseTs: 1718000000000 });
  var clFull = [
    { m_inoutlineconfirm_id: 950301, m_inoutline_id: 950101, m_product_id: 123, targetqty: 3, confirmedqty: 3, scrappedqty: 0 },
    { m_inoutlineconfirm_id: 950302, m_inoutline_id: 950102, m_product_id: 130, targetqty: 2, confirmedqty: 2, scrappedqty: 0 }
  ];
  var done = IC.completeConfirmOps(confirmRow, clFull, inout, dt148, {});
  verdict(done.ok === true && done.fullyConfirmed === true && done.newVerbs.length === 0,
    'confirm-complete (full): every line fully confirmed, NO difference docs, newVerbs=[]', 'ops=' + done.ops.length);
  var co = done.ops[done.ops.length - 1];
  verdict(co.op_type === 'SET_STATUS' && co.table === 'M_InOutConfirm' && co.doc_status === 'CO' && co.processed === 'Y',
    'the confirm walks DR→CO and Processed=Y in the SAME group (:478-479)', JSON.stringify(co));
  var rDone = await KO.commitGroup(opDb, done.ops.map(function (o) { return { op_type: o.op_type, params: o }; }), { baseTs: 1718000001000 });
  var chain = await KO.verifyChain(opDb);
  verdict(rSpawn.committed && rDone.committed && chain.ok, 'spawn + confirm-complete sealed as signed groups, chainOk=Y', 'len=' + chain.len);

  // gate re-check → open; InOut completes NOW; on-hand moves NOW, by the PICKED qty (C- polarity)
  var gate2 = IC.completeInOutGate([{ m_inoutconfirm_id: 950201, confirmtype: 'PC', processed: 'Y' }]);
  verdict(gate2.ok === true, 'gate re-check after confirm-complete → InOut may complete', 'ok=' + gate2.ok);
  lines.forEach(function (l) { committedMovements.push({ m_product_id: l.m_product_id, movementtype: 'C-', movementqty: l.movementqty }); });
  var after = onhand(committedMovements);
  verdict(after[123] === -3 && after[130] === -2,
    'ON-HAND MOVES AT CONFIRM-COMPLETE: the fold drops 3 Oak + 2 Plum only after the confirm processed', 'oak=' + after[123] + ' plum=' + after[130]);
  console.log('§WH-CONFIRM onhand-at=confirm-complete oak=' + after[123] + ' plum=' + after[130] + ' chainOk=' + (chain.ok ? 'Y' : 'N') + ' gid=' + rDone.gid);

  // ── 3. §FALSIFIER mismatched qty — the EXTRACTED difference rules ──
  // (a) SO-side pick SHORT (2 of 3, no scrap): NO difference doc; MovementQty REDUCED; diff on the line
  var clShort = [{ m_inoutlineconfirm_id: 950311, m_inoutline_id: 950101, m_product_id: 123, targetqty: 3, confirmedqty: 2, scrappedqty: 0 }];
  var short_ = IC.completeConfirmOps({ m_inoutconfirm_id: 950211, confirmtype: 'PC', processed: 'N' }, clShort, inout, dt148, {});
  var shipUpd = short_.ops.filter(function (o) { return o.table === 'M_InOutLine'; })[0];
  var diffDocs = short_.ops.filter(function (o) { return o.table === 'C_Invoice' || o.table === 'M_Inventory'; });
  verdict(short_.ok === true && short_.fullyConfirmed === false && shipUpd.movementqty === 2 && diffDocs.length === 0 &&
    short_.differences[0].differenceqty === 1,
    '§FALSIFIER short pick (2 of 3, SO-side, no scrap) → NO difference doc; MovementQty REDUCED to 2; DifferenceQty=1 on the confirm line (createDifferenceDoc:611/637 — neither branch fires; EXTRACTED, not invented)',
    'movementqty=' + shipUpd.movementqty + ' diffDocs=' + diffDocs.length);
  console.log('§FALSIFIER wh=confirm short-pick movementqty=2 differenceqty=1 difference-doc=NONE (SO-side rule, source-cited)');
  // on-hand honesty: the spine moves by what was PICKED (2), never the asked 3
  var pickedFold = onhand([{ m_product_id: 123, movementtype: 'C-', movementqty: shipUpd.movementqty }]);
  verdict(pickedFold[123] === -2, 'the qty spine moves by the PICKED 2, not the asked 3 (picker truth wins)', 'delta=' + pickedFold[123]);

  // (b) scrap ≠ 0 → an M_Inventory difference doc IS created (:637-661)
  var clScrap = [{ m_inoutlineconfirm_id: 950321, m_inoutline_id: 950101, m_product_id: 123, targetqty: 3, confirmedqty: 2, scrappedqty: 1 }];
  var scrap = IC.completeConfirmOps({ m_inoutconfirm_id: 950221, confirmtype: 'PC', processed: 'N' }, clScrap, inout, dt148, { inventoryId: 950401, inventoryLineIdOf: function (i) { return 950411 + i; } });
  var invDoc = scrap.ops.filter(function (o) { return o.table === 'M_Inventory'; })[0];
  var invLine = scrap.ops.filter(function (o) { return o.table === 'M_InventoryLine'; })[0];
  verdict(!!invDoc && !!invLine && invLine.qtyinternaluse === 1 && invDoc.m_warehouse_id === 104,
    'scrapped=1 → M_Inventory difference doc + line @ scrappedQty on the shipment warehouse (:637, the real branch)', 'inv=' + (invDoc && invDoc.m_inventory_id));
  // (c) PO-side linked diff → AP credit memo (:611)
  var poInOut = { m_inout_id: 950002, issotrx: 'N', ref_inout_id: 950001, m_warehouse_id: 104 };
  var clPO = [{ m_inoutlineconfirm_id: 950331, m_inoutline_id: 950131, m_product_id: 123, targetqty: 5, confirmedqty: 4, scrappedqty: 0 }];
  var po = IC.completeConfirmOps({ m_inoutconfirm_id: 950231, confirmtype: 'SC', processed: 'N' }, clPO, poInOut, dt148, { creditMemoId: 950501, creditMemoLineIdOf: function (i) { return 950511 + i; } });
  var cmDoc = po.ops.filter(function (o) { return o.table === 'C_Invoice'; })[0];
  var cmLine = po.ops.filter(function (o) { return o.table === 'C_InvoiceLine'; })[0];
  verdict(!!cmDoc && cmDoc.docbasetype === 'APC' && !!cmLine && cmLine.qtyinvoiced === 1,
    'PO-side linked short-receipt → AP CREDIT MEMO line @ DifferenceQty=1 (:611-634, the other real branch)', 'cm=' + (cmDoc && cmDoc.c_invoice_id));
  console.log('§FALSIFIER wh=confirm scrap→M_Inventory=' + (invDoc ? 'Y' : 'N') + ' po-diff→creditMemo=' + (cmDoc ? 'Y' : 'N'));
  // (d) dispute on a split-when-difference doctype → honest named refusal (no seed case)
  var split = IC.completeConfirmOps({ m_inoutconfirm_id: 950241, confirmtype: 'PC', processed: 'N' }, clShort, inout, { issplitwhendifference: 'Y' }, {});
  verdict(split.ok === false && split.reason === 'split-when-difference-not-folded',
    '§FALSIFIER split-shipment dispute → HONEST refusal (IsSplitWhenDifference path named, not invented — no seed data)', 'reason=' + split.reason);
  console.log('§FALSIFIER wh=confirm split-dispute reason=' + split.reason + ' (named omission, refused not faked)');

  db.close();
  console.log('\n' + (fails === 0 ? '🟢 W-WH-CONFIRM PASS' : '🔴 W-WH-CONFIRM FAIL (' + fails + ')') +
    ' — doctype 148 spawns the PC confirm from the dictionary, the InOut waits at IP and on-hand moves only at ' +
    'confirm-complete (by the PICKED qty); short-pick reduces MovementQty with NO difference doc (SO-side), scrap ' +
    'creates the M_Inventory doc, PO-side diff creates the AP credit memo, and the split path refuses honestly.');
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) { console.error('FATAL', e); process.exit(1); });
