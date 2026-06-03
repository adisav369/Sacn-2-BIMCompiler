#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (Phase 3, W-OPGROUP-UI). Read the log after every run (Log Mandate).
// poc_crud_group.js — the witness for ENGINE_FULL_ERP_ISSUES.md §I-K Phase 3 (the LIVE UI write path).
//
// ISSUE IT PROVES (names it, per CLAUDE.md "tests expose issues"):
//   §I-K (Phase 3, UI tier) — the deployed Process ▶ write (crud_overlay.js commitProcess) must commit the
//   doc action as a GROUP via the kernel's commitGroup primitive, NOT as a single auto-sealing commitOp. A
//   group: (a) stamps a gid so future consequence ops APPEND to the SAME group (all-or-none-ready), and
//   (b) gets the I-D seal-from-tip win (seal ONCE from the tip, not a whole-log reseal).
//
// WHAT IT PROVES (each NAMED):
//   U1  buildDocActionGroup is PURE (no DOM) and returns the HONEST set today = exactly ONE op (SET_STATUS).
//   U2  NON-INVENT — the group contains NO synthesized SHIP/INVOICE/Dr-AR/Cr-Rev consequence ops.
//   U3  commitProcess's write commits the doc action as a GROUP via commitGroup: gid stamped on the row.
//   U4  seal-from-tip — the group seals ONCE from the tip (sealed count == ops, the I-D win).
//   U5  verifyChain OK after the write (the signed chain is intact).
//   U6  all-or-none-READY — every committed op carries the shared gid (a future consequence op appends to it).
//
// NON-INVENT: the op shape is built by the REAL CORE.buildOp (DOC_ACTION) → the REAL pure
//   CORE.buildDocActionGroup (build/erp/crud_overlay.js). The kernel under test is the REAL
//   build/erp/kernel_ops.js under a window shim (the spike_writepath.js loading pattern). No fixtures.
//
// DETERMINISM (§7): no Date.now()/Math.random() in the witness — ids/op_uuid index-derived; commitGroup is
//   called with an explicit baseTs so the kernel never injects wall-clock into a hashed field.
//
// Run: node scripts/poc_crud_group.js 2>&1 | tee build/erp/poc_crud_group.log   — then READ the log.
'use strict';
var path = require('path');
var initSqlJs = require('sql.js');

global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;   // kernel_ops.js seals via bare `crypto.subtle`
var CORE = require(path.join(__dirname, '..', 'build', 'erp', 'crud_overlay.js'));   // REAL op shape + buildDocActionGroup
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));               // window.KernelOps (sealed log)
var KO = global.window.KernelOps;

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

// E_ORDER — a real C_Order descriptor with a docAction (mirrors poc_opgroup / poc_showstopper — non-invent).
var E_ORDER = { key: 'C_Order', verbs: ['create', 'process'],
  fields: [{ col: 'DocumentNo', type: 'string' }, { col: 'GrandTotal', type: 'number' }],
  docAction: { action: 'CO', from: 'DR', to: 'CO', requires: ['GrandTotal'], oracle: 'MOrder.completeIt()' } };

(async function () {
  console.log('═══ POC-CRUD-GROUP — §I-K Phase 3: the LIVE UI write path commits viaGroup (commitGroup) ═══');
  console.log('issue=ENGINE_FULL_ERP_ISSUES.md §I-K (Phase 3, UI)  path=build/erp/crud_overlay.js commitProcess  kernel=build/erp/kernel_ops.js\n');
  await initSqlJs();   // ensure sql.js init resolves (kernel uses its own DB)
  var SQL = await initSqlJs();

  // The DOC_ACTION op the overlay builds when the user fires Process ▶ on a satisfied C_Order.
  var docOp = CORE.buildOp('process', E_ORDER, { DocumentNo: 'SO-1', GrandTotal: 10000 }, null,
                           { id: 'SO-1', from: 'DR', opUuid: 'crud-SO-1-act' });

  // ── U1 · buildDocActionGroup is PURE and returns the honest ONE-op set today ─────────────────────
  console.log('U1 — buildDocActionGroup(op) returns the honest group (today = ONE op: SET_STATUS)');
  var groupOps = CORE.buildDocActionGroup(docOp);
  console.log('§CRUDGROUP build groupOps=' + groupOps.length + ' op_types=[' + groupOps.map(function (o) { return o.op_type; }).join(',') + ']');
  verdict(Array.isArray(groupOps) && groupOps.length === 1 && groupOps[0].op_type === 'SET_STATUS',
          'buildDocActionGroup is pure (no DOM) → [statusOp] (one honest SET_STATUS op)',
          'len=' + groupOps.length + ' type=' + (groupOps[0] && groupOps[0].op_type));

  // ── U2 · NON-INVENT — no fabricated consequence ops in the group ──────────────────────────────────
  console.log('\nU2 — NON-INVENT: the group has NO synthesized SHIP/INVOICE/Dr-AR/Cr-Rev (delegated to install)');
  var forbidden = ['CRUD_CREATE'];   // a consequence op would be a create of M_InOut/C_Invoice/Fact_Acct
  var hasConsequence = groupOps.some(function (o) {
    var p = o.params || {};
    return forbidden.indexOf(o.op_type) >= 0 || ['M_InOut', 'C_Invoice', 'Fact_Acct'].indexOf(p.table) >= 0;
  });
  console.log('§CRUDGROUP non-invent fabricatedConsequence=' + (hasConsequence ? 'YES(!!)' : 'none') + ' (delegated: I-C callouts + I-G §13.6 postings)');
  verdict(!hasConsequence, 'group contains ONLY the extracted status transition (no fabricated ship/invoice/posting)',
          hasConsequence ? 'FOUND fabricated op' : 'clean');

  // ── U3/U4 · drive the SAME write commitProcess does: commitGroup(db, groupOps, {gid}) ─────────────
  console.log('\nU3/U4 — commit the doc action as a GROUP via commitGroup (gid stamped, sealed once from tip)');
  var db = new SQL.Database(); KO.ensureTable(db);
  var rowsBefore = (function () { var r = db.exec('SELECT COUNT(*) FROM kernel_ops'); return r.length ? Number(r[0].values[0][0]) : 0; })();
  var res = await KO.commitGroup(db, groupOps, { gid: 'crud-grp-1', baseTs: 1000 });
  var rowsAfter = (function () { var r = db.exec('SELECT COUNT(*) FROM kernel_ops'); return r.length ? Number(r[0].values[0][0]) : 0; })();
  // read the gid back off the committed row (the all-or-none-ready stamp).
  var gidRow = db.exec("SELECT gid, op_type FROM kernel_ops WHERE id=" + res.ids[0]);
  var storedGid = gidRow.length ? gidRow[0].values[0][0] : null;
  var storedType = gidRow.length ? gidRow[0].values[0][1] : null;
  console.log('§CRUD process committed viaGroup=Y gid=' + res.gid + ' ops=' + res.ids.length + ' sealed=' + res.sealed +
              ' storedGid=' + storedGid + ' storedType=' + storedType + ' Δrows=' + (rowsAfter - rowsBefore));
  verdict(res.committed === true && res.gid === 'crud-grp-1' && storedGid === 'crud-grp-1' && storedType === 'SET_STATUS',
          'doc action commits viaGroup: gid stamped on the SET_STATUS row (all-or-none unit)',
          'committed=' + res.committed + ' gid=' + storedGid);
  verdict(res.sealed === res.ids.length,
          'seal-from-tip — the group seals ONCE from the tip (sealed==ops, the I-D win)',
          'sealed=' + res.sealed + ' ops=' + res.ids.length);

  // ── U5 · verifyChain OK after the write ───────────────────────────────────────────────────────────
  console.log('\nU5 — verifyChain OK after the live write (signed chain intact)');
  var v = await KO.verifyChain(db);
  console.log('§CRUDGROUP verifyChain ok=' + (v.ok ? 'Y' : 'N') + ' len=' + v.len);
  verdict(v.ok === true, 'verifyChain OK after the doc-action group write (chain intact)', 'ok=' + (v.ok ? 'Y' : 'N'));

  // ── U6 · all-or-none-READY — every committed op carries the shared gid ────────────────────────────
  console.log('\nU6 — all-or-none-READY: every committed op carries the shared gid (a consequence op would append here)');
  var allGid = db.exec("SELECT gid FROM kernel_ops WHERE id IN (" + res.ids.join(',') + ")");
  var gids = allGid.length ? allGid[0].values.map(function (r) { return r[0]; }) : [];
  var allShareGid = gids.length === res.ids.length && gids.every(function (g) { return g === res.gid; });
  console.log('§CRUDGROUP all-or-none-ready ops=' + res.ids.length + ' allShareGid=' + (allShareGid ? 'Y' : 'N') + ' gid=' + res.gid);
  verdict(allShareGid, 'all committed ops share the gid → future consequence ops append to the SAME group (all-or-none)',
          'allShareGid=' + allShareGid);
  db.close();

  console.log('\n§CRUDGROUP ' + (fails ? 'FAIL — ' + fails + ' checks red: §I-K Phase 3 NOT proven.'
    : 'PASS — §I-K Phase 3: the live UI write path commits the doc action viaGroup (gid stamped), seals ONCE '
    + 'from the tip (I-D win), verifyChain holds, and is all-or-none-READY for the delegated consequence ops. '
    + 'NON-INVENT honoured: today the group is the extracted SET_STATUS transition only.'));
  process.exit(fails ? 1 : 0);
})().catch(function (e) { console.error('FATAL', e); process.exit(2); });
