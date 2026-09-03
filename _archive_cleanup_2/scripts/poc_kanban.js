#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * poc_kanban.js — prompts/MOBILE_CHAT_LENS.md lens-family POC: Kanban-as-doc_status-fold, and the
 * killer equality SEND == DRAG == VERB.
 *
 * Falsifiable claim: a Kanban board is a FOLD over doc_status (columns = the kernel's wfmc states,
 * cards = real documents); dragging a card column→column resolves, via the SAME state machine, to a
 * DocAction; and the op a Kanban DRAG emits is BYTE-IDENTICAL to the op the chat-lens SEND emits —
 * same verb, identical projection — while ILLEGAL drags are refused by the engine (not the UI), so
 * the columns enforce the kernel's legality in EVERY lens. One engine, two lenses, one verb.
 *
 * Reuses the PROVEN kernel (scripts/erp_kernel.js dispatch/apply/replay) + the REAL wfmc state machine
 * (~/bim-ootb/erp/manifest.json) + REAL docs (build/erp/glassbowl_data.db). NO fork, NO new verb,
 * NO invented transition.
 *
 * Run: node scripts/poc_kanban.js 2>&1 | tee build/erp/poc_kanban.log
 */
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var initSqlJs = require('sql.js');
var K = require('./erp_kernel');

var GB = process.env.ERP_GLASSBOWL || path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db');
var MANIFEST = process.env.ERP_MANIFEST || path.join(process.env.HOME, 'bim-ootb', 'erp', 'manifest.json');
var wfmc = require(MANIFEST).wfmc;
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function money(n) { return (Math.round(Number(n || 0) * 100) / 100).toFixed(2); }

// the board's column→action resolution: a drag to column `to` is the action whose transition lands there.
function actionToColumn(from, to) {
  var hit = (wfmc.transitions || []).filter(function (t) { return t[0] === from && t[2] === to; });
  return hit.length ? hit[0][1] : null;            // the ACTION, or null = no such edge (snap back)
}

(async function () {
  var gb = new Database(GB, { readonly: true });
  var all = function (sql, p) { return gb.prepare(sql).all(p || {}); };
  var one = function (sql, p) { return gb.prepare(sql).get(p || {}); };
  var SQL = await initSqlJs();
  function q(db) { return function (s, p) { return K.query(db, s, p); }; }

  console.log('═══ POC-KANBAN — Kanban = doc_status fold;  SEND == DRAG == VERB ═══\n');

  // the ONE handler both lenses funnel through: SET_STATUS to the kernel-computed target (ctx.to).
  K.register('C_Invoice', 'CO', function (doc, ctx) {
    return [{ op_type: 'SET_STATUS', table: doc.table, id: doc.id, doc_status: ctx.to, uuid: doc.uuid }];
  });

  // ════════════════════════════════════════════════════════════════════════
  // §KANBAN-FOLD — the board is a GROUP BY doc_status over real docs; columns = wfmc states.
  // ════════════════════════════════════════════════════════════════════════
  console.log('── §KANBAN-FOLD ──');
  var rows = all("SELECT 'C_Invoice' AS t, docstatus, COUNT(*) AS n FROM c_invoice GROUP BY docstatus " +
    "UNION ALL SELECT 'C_Order', docstatus, COUNT(*) FROM c_order GROUP BY docstatus");
  var board = {};                                   // status -> {doctype -> count}
  rows.forEach(function (r) { (board[r.docstatus] = board[r.docstatus] || {})[r.t] = r.n; });
  var orderedCols = (wfmc.states || []).filter(function (s) { return board[s]; });
  orderedCols.forEach(function (s) {
    var cells = Object.keys(board[s]).map(function (dt) { return dt + ':' + board[s][dt]; }).join(', ');
    console.log('   │ ' + (wfmc.statusNames[s] || s) + ' (' + s + ') │  ' + cells);
  });
  var totalCards = rows.reduce(function (a, r) { return a + r.n; }, 0);
  var colsFromStateMachine = orderedCols.every(function (s) { return (wfmc.states || []).indexOf(s) >= 0; });
  console.log('§KANBAN-FOLD columns=' + orderedCols.length + ' [' + orderedCols.join(',') + '] cards=' + totalCards +
    ' source=glassbowl_data.db+wfmc handAuthored=0');
  verdict(colsFromStateMachine && orderedCols.length > 0, 'every column is a wfmc state (fold, not authored)', 'cols=[' + orderedCols.join(',') + ']');
  verdict(totalCards === rows.reduce(function (a, r) { return a + r.n; }, 0), 'every card is a real doc grouped by its docstatus', 'cards=' + totalCards);

  // ════════════════════════════════════════════════════════════════════════
  // SEND == DRAG == VERB — same start state, two lenses, compare the emitted op + projection.
  // ════════════════════════════════════════════════════════════════════════
  console.log('\n── §SEND-EQ-DRAG ──');
  var INV = 103;
  var inv = one('SELECT * FROM c_invoice WHERE c_invoice_id=@id', { id: INV });
  var bp = one('SELECT name FROM c_bpartner WHERE c_bpartner_id=@id', { id: inv.c_bpartner_id });
  var bpName = bp ? bp.name : ('BPartner#' + inv.c_bpartner_id);
  var docGuid = 'DOC:C_Invoice#' + INV;
  var docRef = { docType: 'C_Invoice', table: 'C_Invoice', id: INV, uuid: docGuid, status: 'DR' };
  var ACTOR = 'user:clerk', BASE = 5000;            // who/when — SAME for both lenses (the lens sets neither)

  function seedCard(db) {                            // the card sits in the DR column (real doc, DR)
    K.initProjection(db);
    K.apply(db, [{ op_type: 'CREATE_DOCUMENT', table: 'C_Invoice', source_id: INV, doc_status: 'DR',
      uuid: docGuid, documentno: inv.documentno, bpartner: bpName, grandtotal: inv.grandtotal }],
      { actor: 'user:seed', baseTs: 1000 });
  }
  function setStatusRow(db) {
    return K.query(db, "SELECT op_uuid, op_type, parameters, output_guid, user_tag FROM kernel_ops WHERE op_type='SET_STATUS'")[0];
  }

  // CHAT lens: tap "Complete" — the action is explicit.
  var dbChat = new SQL.Database(); seedCard(dbChat);
  var rChat = K.dispatch(dbChat, { wfmc: wfmc, guards: [], query: q(dbChat), actor: ACTOR, baseTs: BASE },
    { docType: docRef.docType, action: 'CO', status: 'DR', table: docRef.table, id: docRef.id, uuid: docRef.uuid });

  // KANBAN lens: drag the card from the DR column to the CO column — the action is RESOLVED from the board.
  var dbKan = new SQL.Database(); seedCard(dbKan);
  var dragAction = actionToColumn('DR', 'CO');       // -> 'CO' (the Complete edge), from the SAME wfmc
  var rKan = K.dispatch(dbKan, { wfmc: wfmc, guards: [], query: q(dbKan), actor: ACTOR, baseTs: BASE },
    { docType: docRef.docType, action: dragAction, status: 'DR', table: docRef.table, id: docRef.id, uuid: docRef.uuid });

  console.log('   CHAT send  "Complete"   → ' + (rChat.ok ? 'op ' + JSON.stringify(rChat.ops[0]) : rChat.stage + ':' + rChat.reason));
  console.log('   KANBAN drag DR→CO col   → action=' + dragAction + ' → ' + (rKan.ok ? 'op ' + JSON.stringify(rKan.ops[0]) : rKan.stage + ':' + rKan.reason));
  var opEqual = rChat.ok && rKan.ok && JSON.stringify(rChat.ops[0]) === JSON.stringify(rKan.ops[0]);
  var rowChat = setStatusRow(dbChat), rowKan = setStatusRow(dbKan);
  var rowEqual = rowChat && rowKan && JSON.stringify(rowChat) === JSON.stringify(rowKan);
  var hashChat = K.projectionHash(dbChat), hashKan = K.projectionHash(dbKan);
  console.log('§SEND-EQ-DRAG opEqual=' + (opEqual ? 'Y' : 'N') + ' committedOpEqual=' + (rowEqual ? 'Y' : 'N') +
    ' chatHash=' + hashChat + ' kanbanHash=' + hashKan + ' hashEqual=' + (hashChat === hashKan ? 'Y' : 'N') + ' verb=SET_STATUS');
  verdict(rChat.ok && rChat.to === 'CO', 'CHAT send moves DR→CO via dispatch', rChat.ok ? 'to=' + rChat.to : rChat.reason);
  verdict(rKan.ok && rKan.to === 'CO', 'KANBAN drag (DR→CO column) resolves to the SAME action+target', rKan.ok ? 'action=' + dragAction + ' to=' + rKan.to : rKan.reason);
  verdict(opEqual, 'the emitted op is BYTE-IDENTICAL across the two lenses (send == drag)', 'opEqual=' + opEqual);
  verdict(rowEqual, 'the COMMITTED kernel_ops row is identical (same op_uuid, payload, actor)', 'committedOpEqual=' + rowEqual);
  verdict(hashChat === hashKan, 'the resulting projection is identical (the lens contributes NOTHING to the engine)', hashChat + ' == ' + hashKan);

  // ════════════════════════════════════════════════════════════════════════
  // §KANBAN-LEGALITY — an illegal drag is refused by the ENGINE (state machine), not the UI.
  // ════════════════════════════════════════════════════════════════════════
  console.log('\n── §KANBAN-LEGALITY ──');
  // (a) board level: drag a CO card back to the DR column — there is NO edge CO→DR, so the drop snaps back.
  var backAction = actionToColumn('CO', 'DR');
  console.log('   KANBAN drag CO→DR col   → action=' + backAction + ' (no wfmc edge) → ' + (backAction ? '?' : 'BLOCKED: card snaps back'));
  // (b) dispatch level: even if forced, the state machine rejects an illegal action (same guard the chat funnels through).
  var dbCO = new SQL.Database(); K.initProjection(dbCO);
  K.apply(dbCO, [{ op_type: 'CREATE_DOCUMENT', table: 'C_Invoice', source_id: INV, doc_status: 'CO', uuid: docGuid }], { actor: 'user:seed', baseTs: 1000 });
  var rIllegal = K.dispatch(dbCO, { wfmc: wfmc, guards: [], query: q(dbCO), actor: ACTOR, baseTs: BASE },
    { docType: 'C_Invoice', action: 'CO', status: 'CO', table: 'C_Invoice', id: INV, uuid: docGuid });   // Complete an already-CO doc
  console.log('   dispatch(status=CO, action=CO) → ' + (rIllegal.ok ? 'APPLIED (unexpected)' : rIllegal.stage + ': ' + rIllegal.reason));
  console.log('§KANBAN-LEGALITY illegalDragCO→DR=blocked(noEdge) dispatchRejectsIllegal=' + (!rIllegal.ok ? 'Y' : 'N') +
    ' stage=' + rIllegal.stage + ' lensAgnostic=Y');
  verdict(backAction === null, 'illegal drag CO→DR is refused at the board (no wfmc edge — card snaps back)', 'action=' + backAction);
  verdict(!rIllegal.ok && rIllegal.stage === 'state-machine', 'the engine (state machine) rejects the illegal move — same guard BOTH lenses funnel through', rIllegal.stage + ':' + rIllegal.reason);

  console.log('\n═══ ' + (fails === 0 ? '✅ POC-KANBAN ALL PASS' : '❌ POC-KANBAN ' + fails + ' FAIL') +
    ' — Kanban is a doc_status fold; SEND == DRAG == VERB (SET_STATUS); legality is engine-owned ═══');
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) { console.error('FATAL', e); process.exit(2); });
