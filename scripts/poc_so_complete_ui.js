#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (SO_FULL_CRUD_GAP.md T1, GAP 1 + Part B). Read the log after every run (Log Mandate).
// poc_so_complete_ui.js — W-SO-COMPLETE-UI: the UI Complete becomes the REAL document process.
//
// ISSUES IT PROVES (each NAMED, per CLAUDE.md "tests expose issues"):
//   GAP 1 (Part A) — buildDocActionGroup returned exactly [SET_STATUS]: completing a Sales Order from
//     the UI flipped docstatus→CO and created NOTHING downstream (no M_InOut, no C_Invoice, no GL).
//     Fix: the overlay ASSEMBLES the consequence ops the PROVEN engine extracts (ERPEngine.completeOrder,
//     W-FOLD-COMPLETE oracle-equivalent) into ONE commitGroup — [CREATE M_InOut(+lines), CREATE
//     C_Invoice(+lines), SET_STATUS CO] — folded all-or-none, sealed once from the tip.
//   Part B (history) — a Z-scrub-back after Complete reversed ONLY the status dot (foldBackDocOp called
//     KernelOps.undoOp once). Fix: the committed op carries the group (op.gid), the recorded history
//     moment carries it (v.docOp.gid), and CORE.foldBackGroup/foldForwardGroup fold the WHOLE gid —
//     un-ship, un-invoice, status→DR in REVERSE commit order — through the SAME fold plumbing
//     (no second history lane; deployed glassbowl.html foldDocOps is UNCHANGED).
//   GL postings — COVERAGE-GATED on this surface, honestly: the bundle carries no c_ordertax (a fresh
//     order's invoice tax legs are non-derivable) and post_resolver is not mounted; gl=gated is LOGGED,
//     never faked (the posting-preview data-gate pattern). Ship/Invoice creation works regardless.
//
// NON-INVENT: the engine is the REAL scripts/erp_engine.js (UMD — byte-identical to the deployed
//   erp/erp_engine.js); the kernel is the REAL build/erp/kernel_ops.js; the op assembly is the REAL
//   CORE.buildDocActionGroup; order/lines are real GardenWorld rows (glassbowl_data.db, readonly);
//   fan-out flags are the EXTRACTED DOCPOLICY (crud_ops.json __meta.docPolicy ← erp_rules.db ← real
//   c_doctype) — never defaulted. The witness re-states glassbowl.html's recordDocMoment/foldDocOps
//   VERBATIM (deployed lines 778/791) — the only DOM-bound link; the fold LOGIC under test is CORE.
//
// DETERMINISM (§7): no Date.now/Math.random — commitGroup called with explicit baseTs + gids supplied.
//
// §-CLAIMS (written BEFORE the fix — each must appear in this log):
//   §SO-COMPLETE order=.. ship=.. invoice=.. gl=<n|gated> sealed=Y gid=..
//   §FOLD-BACK key=c_order group=<gid> reversed=ship,invoice,status
//   §FOLD-FORWARD key=c_order group=<gid> reapplied=ship,invoice,status
//
// Run: bash build/erp/run_witness.sh scripts/poc_so_complete_ui.js   — then READ build/erp/poc_so_complete_ui.log
'use strict';
var path = require('path');
var fs = require('fs');
var initSqlJs = require('sql.js');
var Database = require('better-sqlite3');

global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;
var CORE = require(path.join(__dirname, '..', 'build', 'erp', 'crud_overlay.js'));   // REAL overlay core
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));                // REAL signed kernel
var KO = global.window.KernelOps;
var E = require(path.join(__dirname, 'erp_engine'));                                 // REAL engine (UMD == deployed)
var STORE = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'build', 'erp', 'crud_ops.json'), 'utf8'));

var bundle = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var ORDER_ID = 108;   // GardenWorld '60000' — c_doctype 133 (On Credit Order, WI): DOCPOLICY io=Y inv=Y, 1 line

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

(async function () {
  console.log('═══ W-SO-COMPLETE-UI — T1: UI Complete = the document process (fan-out group) + History folds the WHOLE group ═══');
  console.log('    issue=prompts/SO_FULL_CRUD_GAP.md GAP 1 + T1 Part B   seam=build/erp/crud_overlay.js buildDocActionGroup/foldBackGroup\n');
  var SQL = await initSqlJs();
  var db = new SQL.Database(); KO.ensureTable(db);   // the sidecar (the page's signed op-log)

  // ── A0. EXTRACTED inputs: real order + lines + DOCPOLICY (data, never defaulted) ──────────────
  console.log('── A0. extracted inputs: order ' + ORDER_ID + ' header+lines (bundle) + DOCPOLICY flags (crud_ops.json __meta ← erp_rules.db) ──');
  var order = bundle.prepare('SELECT * FROM c_order WHERE c_order_id=?').get(ORDER_ID);
  var lines = bundle.prepare('SELECT c_orderline_id, m_product_id, qtyordered FROM c_orderline WHERE c_order_id=?').all(ORDER_ID);
  verdict(!!order && lines.length >= 1, 'order ' + ORDER_ID + ' (' + (order && order.documentno) + ') with ' + lines.length + ' line(s) read from the IMMUTABLE bundle', 'doctype=' + (order && order.c_doctype_id));
  var policy = CORE.docPolicyFor(STORE, order.c_doctype_id);
  verdict(!!policy && policy.isautogenerateinout === 'Y' && policy.isautogenerateinvoice === 'Y',
    'DOCPOLICY:' + order.c_doctype_id + ' EXTRACTED (io=Y inv=Y) via CORE.docPolicyFor — flags are data, not assumption',
    JSON.stringify(policy));
  var polSO = CORE.docPolicyFor(STORE, 132);   // Standard Order: the N/N control
  verdict(!!polSO && polSO.isautogenerateinout === 'N' && polSO.isautogenerateinvoice === 'N',
    'control: DOCPOLICY:132 (Standard Order) = N/N — the decision table is the gate, not code', JSON.stringify(polSO));
  verdict(CORE.docPolicyFor(STORE, 999999) === null, 'unknown doctype → null (gate, never a defaulted Y)', '999999→null');

  // ── A1. buildDocActionGroup assembles the ENGINE fan-out + SET_STATUS last ────────────────────
  console.log('\n── A1. GAP 1: buildDocActionGroup(op, fanout) = [CREATE m_inout(+line), CREATE c_invoice(+line), SET_STATUS co] ──');
  var entry = STORE.c_order; entry.key = 'c_order';
  var vals = { c_bpartner_id: order.c_bpartner_id, grandtotal: order.grandtotal };   // requires satisfied from the real row
  var op = CORE.buildOp('process', entry, vals, order, { id: ORDER_ID, from: 'DR', opUuid: 'so-co-108' });
  verdict(op.op_type === 'DOC_ACTION' && op.to === 'CO' && op.outcome === 'success', 'DOC_ACTION op: DR→CO success (requires met from real row)', 'to=' + op.to);

  var engineOps = E.completeOrder(order, lines, policy).filter(function (o) { return o.op_type !== 'SET_STATUS'; });
  var fanout = { ops: engineOps, glGate: 'no-order-side-acct/tax-linkage' };
  var group = CORE.buildDocActionGroup(op, fanout);
  var types = group.map(function (g) { return g.op_type + ':' + ((g.params && g.params.table) || ''); });
  console.log('   group=[' + types.join(' | ') + ']');
  verdict(group.length === 1 + lines.length * 2 + 2, 'group = 2 CREATE_DOCUMENT + ' + (lines.length * 2) + ' CREATE_LINE + 1 SET_STATUS = ' + group.length + ' ops', types.join(','));
  verdict(group[0].op_type === 'CREATE_DOCUMENT' && group[0].params.table === 'M_InOut', 'op[0] = CREATE_DOCUMENT M_InOut (the shipment)', JSON.stringify(group[0].params));
  var invDoc = group.filter(function (g) { return g.op_type === 'CREATE_DOCUMENT' && g.params.table === 'C_Invoice'; });
  verdict(invDoc.length === 1, 'exactly one CREATE_DOCUMENT C_Invoice (the invoice)', JSON.stringify(invDoc[0] && invDoc[0].params));
  verdict(group[group.length - 1].op_type === 'SET_STATUS' && group[group.length - 1].params.to === 'CO', 'SET_STATUS CO is LAST (consequences first, status seals the gesture)');
  // the overlay only ASSEMBLES — params are the engine ops VERBATIM (numbers from the engine, not re-derived)
  var shipLine = group.filter(function (g) { return g.op_type === 'CREATE_LINE' && g.params.table === 'M_InOutLine'; })[0];
  verdict(!!shipLine && shipLine.params === engineOps.filter(function (o) { return o.table === 'M_InOutLine'; })[0]
          && shipLine.params.movementqty === lines[0].qtyordered,
    'shipment line params === the engine op object VERBATIM (movementqty=' + lines[0].qtyordered + ' = qtyordered, engine-supplied)', JSON.stringify(shipLine && shipLine.params));
  var invLine = group.filter(function (g) { return g.op_type === 'CREATE_LINE' && g.params.table === 'C_InvoiceLine'; })[0];
  verdict(!!invLine && invLine.params.qtyinvoiced === lines[0].qtyordered, 'invoice line qtyinvoiced=' + lines[0].qtyordered + ' (engine-supplied)', JSON.stringify(invLine && invLine.params));
  // honesty gates: no fanout arg → the old single-op group; in-progress outcome → never fans out
  var bare = CORE.buildDocActionGroup(op);
  verdict(bare.length === 1 && bare[0].op_type === 'SET_STATUS', 'back-compat: no fanout arg → [SET_STATUS] only (honest old path intact)');
  var ipOp = CORE.buildOp('process', entry, {}, order, { id: ORDER_ID, from: 'DR' });   // requires unmet → IP
  verdict(ipOp.to === 'IP' && ipOp.outcome === 'in-progress', 'control: requires unmet → IP (no Complete)', 'to=' + ipOp.to);

  // ── A2. commitGroup folds the WHOLE group all-or-none; tip shows ship + invoice + CO ──────────
  console.log('\n── A2. commitGroup: all-or-none, ONE gid, sealed once; sidecar tip = M_InOut + C_Invoice + docstatus CO ──');
  var res = await KO.commitGroup(db, group, { baseTs: 1000, gid: 'so-complete-108-g1' });
  var v = await KO.verifyChain(db);
  verdict(res.committed === true && res.ids.length === group.length, 'commitGroup committed ALL ' + group.length + ' ops (all-or-none)', 'ids=' + res.ids.join(','));
  verdict(!!res.gid && v.ok === true, 'ONE gid binds the group; verifyChain OK (sealed once from the tip)', 'gid=' + res.gid + ' sealed=' + res.sealed);
  var tipStatus = CORE.readTip(db, 'c_order', ORDER_ID);
  var shipsTip = CORE.tipDocs(db, 'm_inout');
  var invsTip = CORE.tipDocs(db, 'c_invoice');
  verdict(tipStatus === 'CO', 'read-the-tip docstatus = CO', 'got=' + tipStatus);
  verdict(shipsTip.length === 1 && Number(shipsTip[0].doc.source_id) === ORDER_ID, 'M_InOut row EXISTS in the sidecar tip (source_id=' + ORDER_ID + ')', JSON.stringify(shipsTip[0] && shipsTip[0].doc));
  verdict(invsTip.length === 1 && Number(invsTip[0].doc.source_id) === ORDER_ID, 'C_Invoice row EXISTS in the sidecar tip (source_id=' + ORDER_ID + ')', JSON.stringify(invsTip[0] && invsTip[0].doc));
  // GL coverage gate — honest, evidenced: the bundle has no c_ordertax → fresh-order tax legs non-derivable
  var hasOrderTax = bundle.prepare("SELECT COUNT(*) n FROM sqlite_master WHERE type='table' AND name='c_ordertax'").get().n > 0;
  verdict(!hasOrderTax, 'GL gate is EVIDENCED: bundle has NO c_ordertax → invoice tax legs non-derivable for a fresh order → gl=gated (logged, never faked)');
  console.log('§SO-COMPLETE order=' + ORDER_ID + ' ship=' + shipsTip.length + ' invoice=' + invsTip.length + ' gl=gated sealed=Y gid=' + res.gid);

  // ── B1. the history MOMENT carries the group (deployed recordDocMoment stores op verbatim) ────
  console.log('\n── B1. Part B: ONE history moment carries the WHOLE group (v.docOp.gid) — glassbowl.html unchanged ──');
  // glassbowl.html (deployed :778) — VERBATIM logic: v.docOp = op
  var viewHist = [], viewIdx = -1, docSeq = 0, vlRestoring = false;
  function recordDocMoment(label, opArg) {
    if (vlRestoring) return;
    var vv = { hint: label, docSeq: ++docSeq, docOp: opArg || null };
    viewHist = viewHist.slice(0, viewIdx + 1); viewHist.push(vv); viewIdx = viewHist.length - 1;
    console.log('§DOC-DOT idx=' + viewIdx + ' total=' + viewHist.length + ' label=' + label + (opArg ? ' op=' + opArg.op_type : ''));
  }
  // commitProcess (post-fix) stamps the group onto the op BEFORE docDot:
  op.gid = res.gid; op.groupN = res.ids.length;
  viewHist.push({ hint: 'view', docOp: null }); viewIdx = 0;            // dot 0: a plain pre-complete view
  recordDocMoment(CORE.docLabel(op, 'Order'), op);                       // dot 1: THE complete gesture (one dot, whole group)
  verdict(viewHist.length === 2 && viewHist[1].docOp && viewHist[1].docOp.gid === res.gid,
    'ONE doc dot; its docOp CARRIES the group', 'docOp.gid=' + (viewHist[1].docOp && viewHist[1].docOp.gid) + ' groupN=' + (viewHist[1].docOp && viewHist[1].docOp.groupN));

  // ── B2. scrub BACK → crudFoldBack → CORE.foldBackGroup reverses the WHOLE gid (reverse order) ──
  console.log('\n── B2. Z scrub-back reverses ship + invoice + status (whole gid, REVERSE commit order) ──');
  var lastBack = null, lastFwd = null;
  // the DOM wrappers' delegation (foldBackDocOp/foldForwardDocOp post-fix), minus DOM:
  function crudFoldBack(key, fromStatus, toStatus) {
    var g = CORE.foldBackGroup(db, KO);
    if (g.gid) console.log('§FOLD-BACK key=' + key + ' group=' + g.gid + ' reversed=' + g.labels.join(',') + ' ops=' + g.undone.length + ' status=' + (toStatus || '?') + '→' + fromStatus);
    else console.log('§FOLD-BACK key=' + key + ' status=' + (toStatus || '?') + '→' + fromStatus + ' undone_id=' + ((g.undone[0] && g.undone[0].id) || 'null'));
    lastBack = g;
  }
  function crudFoldForward(key, toStatus) {
    var g = CORE.foldForwardGroup(db, KO);
    if (g.gid) console.log('§FOLD-FORWARD key=' + key + ' group=' + g.gid + ' reapplied=' + g.labels.join(',') + ' ops=' + g.redone.length + ' status=→' + toStatus);
    else console.log('§FOLD-FORWARD key=' + key + ' status=→' + toStatus);
    lastFwd = g;
  }
  // glassbowl.html (deployed :791) — VERBATIM logic:
  function foldDocOps(from, to) {
    if (from === to) return; var j, vv;
    if (from > to) { for (j = from; j > to; j--) { vv = viewHist[j]; if (vv && vv.docOp && vv.docOp.op_type === 'DOC_ACTION' && typeof crudFoldBack === 'function') crudFoldBack(vv.docOp.key, vv.docOp.from, vv.docOp.to); } }
    else { for (j = from + 1; j <= to; j++) { vv = viewHist[j]; if (vv && vv.docOp && vv.docOp.op_type === 'DOC_ACTION' && typeof crudFoldForward === 'function') crudFoldForward(vv.docOp.key, vv.docOp.to); } }
  }
  function scrubTo(i) { if (i < 0 || i >= viewHist.length || i === viewIdx) return; foldDocOps(viewIdx, i); viewIdx = i; }

  scrubTo(0);   // Z back past the Complete dot
  verdict(!!lastBack && lastBack.gid === res.gid, 'fold-back resolved THE group from the tip', 'gid=' + (lastBack && lastBack.gid));
  verdict(!!lastBack && lastBack.undone.length === group.length, 'EVERY op of the group reversed (' + group.length + '), not just the status dot', 'undone=' + (lastBack && lastBack.undone.length));
  var idsDesc = lastBack.undone.map(function (u) { return u.id; });
  var strictlyDesc = idsDesc.every(function (id, i) { return i === 0 || id < idsDesc[i - 1]; });
  verdict(strictlyDesc && lastBack.undone[0].op_type === 'SET_STATUS', 'REVERSE order on back: status undone first, ids strictly descending', 'order=' + idsDesc.join('>'));
  verdict(lastBack.labels.join(',') === 'ship,invoice,status', 'reversed=ship,invoice,status (un-ship, un-invoice, status back)', 'labels=' + lastBack.labels.join(','));
  verdict(CORE.readTip(db, 'c_order', ORDER_ID) === null, 'tip docstatus folds back to pre-complete (null → descriptor DR)', 'readTip=' + CORE.readTip(db, 'c_order', ORDER_ID));
  verdict(CORE.tipDocs(db, 'm_inout').length === 0 && CORE.tipDocs(db, 'c_invoice').length === 0, 'M_InOut AND C_Invoice GONE from the tip (sidecar = pre-complete state)');

  // ── B3. scrub FORWARD → the whole group refolds (commit order) ────────────────────────────────
  console.log('\n── B3. scrub forward re-applies the WHOLE group (commit order) ──');
  scrubTo(1);
  verdict(!!lastFwd && lastFwd.gid === res.gid && lastFwd.redone.length === group.length, 'fold-forward redid all ' + group.length + ' ops of the gid', 'redone=' + (lastFwd && lastFwd.redone.length));
  var idsAsc = lastFwd.redone.map(function (u) { return u.id; });
  var strictlyAsc = idsAsc.every(function (id, i) { return i === 0 || id > idsAsc[i - 1]; });
  verdict(strictlyAsc, 'forward order = commit order (ids strictly ascending)', 'order=' + idsAsc.join('<'));
  verdict(CORE.readTip(db, 'c_order', ORDER_ID) === 'CO' && CORE.tipDocs(db, 'm_inout').length === 1 && CORE.tipDocs(db, 'c_invoice').length === 1,
    'tip is back: docstatus=CO, ship + invoice present again');

  // ── B4. gid-scoping: a later SINGLE op folds back alone — the complete group is untouched ─────
  console.log('\n── B4. control: a later single-op gesture folds back ALONE (gid-scoped, no over-reach) ──');
  await KO.commitGroup(db, [{ op_type: 'CRUD_UPDATE', op_uuid: 'upd-1', params: { table: 'c_order', id: ORDER_ID, changes: { description: { old: null, new: 'x' } } } }], { baseTs: 2000, gid: 'single-upd-g2' });
  var g2 = CORE.foldBackGroup(db, KO);
  verdict(g2.gid === 'single-upd-g2' && g2.undone.length === 1, 'single-op group: fold-back undoes exactly 1 op', 'gid=' + g2.gid + ' undone=' + g2.undone.length);
  verdict(CORE.readTip(db, 'c_order', ORDER_ID) === 'CO' && CORE.tipDocs(db, 'm_inout').length === 1, 'the complete group is UNTOUCHED by the sibling fold (still CO + shipped)');

  db.close(); bundle.close();
  console.log('\n═══ W-SO-COMPLETE-UI results ═══');
  console.log(fails === 0
    ? '🟢 ALL PASS — GAP 1 closed: UI Complete commits the engine fan-out (ship+invoice+status) as ONE sealed group, GL honestly gated; '
      + 'Part B closed: the history moment carries the gid and Z fold-back/forward replays the WHOLE group (reverse on back) through the EXISTING fold lane.'
    : '🔴 ' + fails + ' FAIL(S)');
  process.exit(fails ? 1 : 0);
})().catch(function (e) { console.error('FATAL', e); process.exit(2); });
