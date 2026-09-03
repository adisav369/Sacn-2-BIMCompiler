#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (SO_FULL_CRUD_GAP.md T2 / GAP 2). Read the log after every run (Log Mandate).
// poc_crud_list_visibility.js — the witness for SO_FULL_CRUD_GAP.md T2 (GAP 2): CREATE/DELETE list visibility.
//
// ISSUE IT PROVES (names it, per CLAUDE.md "tests expose issues"):
//   GAP 2 — CRUD_CREATE and CRUD_DELETE persist as signed op-log truth, but the read-the-tip overlay was
//   UPDATE-scoped (CORE.tipValues only): a newly CREATED Sales Order row did not appear in the list/dossier
//   and a DELETED (tombstoned) one did not hide. The list query must UNION the immutable bundle rows with
//   CRUD_CREATE tip rows and HIDE CRUD_DELETE-tombstoned ids — replay the op-log filtered to that table,
//   latest-wins, in commit order — while glassbowl_data.db stays the IMMUTABLE baseline.
//
// WHAT IT PROVES (each NAMED):
//   L1  CORE.listTip is PURE (db + baseRows in, merged rows out) — no DOM.
//   L2  CREATE — a CRUD_CREATE for c_order surfaces as a NEW row in the list tip (source=sidecar).
//   L3  DELETE — a CRUD_DELETE tombstone HIDES an existing baseline row from the list tip.
//   L4  REHYDRATE — re-reading the SAME sidecar db (page-reload analogue) yields the SAME tip (state survives).
//   L5  BASELINE IMMUTABLE — the bundle rows passed in are never mutated (created/hidden live only in the overlay).
//
// NON-INVENT: the create/delete ops are the REAL CORE.buildOp shapes committed through the REAL
//   build/erp/kernel_ops.js commitGroup (the exact commitCrud group shape). No fixtures, no synthetic log rows.
//
// DETERMINISM (§7): no Date.now()/Math.random() — ids index-derived; commitGroup called with explicit baseTs.
//
// Run: node scripts/poc_crud_list_visibility.js 2>&1 | tee build/erp/poc_crud_list_visibility.log  — then READ the log.
'use strict';
var path = require('path');
var initSqlJs = require('sql.js');

global.window = global.window || {};
global.crypto = global.crypto || require('crypto').webcrypto;
var CORE = require(path.join(__dirname, '..', 'build', 'erp', 'crud_overlay.js'));
require(path.join(__dirname, '..', 'build', 'erp', 'kernel_ops.js'));
var KO = global.window.KernelOps;

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

// commitCrud's EXACT group shape (the only thing the witness can't reach is the DOM/withSidecar wrapper).
async function commitCreate(db, table, fields, baseTs, opUuid) {
  return KO.commitGroup(db, [{ op_type: 'CRUD_CREATE', op_uuid: opUuid || null, params: { table: table, id: null, fields: fields, cas: null } }], { baseTs: baseTs });
}
async function commitDelete(db, table, id, baseTs, opUuid) {
  return KO.commitGroup(db, [{ op_type: 'CRUD_DELETE', op_uuid: opUuid || null, params: { table: table, id: id, tombstone: true, reversible: true } }], { baseTs: baseTs });
}

(async function () {
  console.log('═══ POC-CRUD-LIST-VISIBILITY — T2 (GAP 2): created rows show, tombstoned rows hide, from the sidecar ═══');
  console.log('issue=SO_FULL_CRUD_GAP.md T2  path=build/erp/crud_overlay.js (CORE.listTip)  kernel=build/erp/kernel_ops.js\n');
  var SQL = await initSqlJs();
  var side = new SQL.Database();           // the signed sidecar log (separate from glassbowl_data.db)
  KO.ensureTable(side);

  // The IMMUTABLE baseline list rows (what renderDataTab reads from glassbowl_data.db — never mutated).
  var PK = 'c_order_id';
  var baseRows = [
    { c_order_id: 1000, documentno: 'SO-1000', grandtotal: 100, docstatus: 'DR' },
    { c_order_id: 1001, documentno: 'SO-1001', grandtotal: 200, docstatus: 'CO' },
    { c_order_id: 1002, documentno: 'SO-1002', grandtotal: 300, docstatus: 'DR' }
  ];
  var baseRowsFrozen = JSON.stringify(baseRows);

  // ── L1 · pure + empty-log identity ──────────────────────────────────────────────────────────────
  console.log('L1 — CORE.listTip is pure; with an empty sidecar it returns the baseline unchanged');
  var t0 = CORE.listTip(side, 'c_order', PK, baseRows);
  console.log('§CRUD-LIST key=c_order created=0 hidden=0 rows=' + t0.rows.length + ' source=sidecar');
  verdict(typeof CORE.listTip === 'function' && t0.rows.length === 3 && t0.created.length === 0 && t0.hidden.length === 0,
          'listTip pure: empty sidecar → baseline (3 rows, 0 created, 0 hidden)', 'rows=' + t0.rows.length);

  // ── L2 · CREATE surfaces a new row ──────────────────────────────────────────────────────────────
  console.log('\nL2 — a CRUD_CREATE c_order surfaces in the list tip (source=sidecar)');
  await commitCreate(side, 'c_order', { documentno: 'SO-NEW-1', grandtotal: 555, docstatus: 'DR' }, 1000, 'crud-create-1');
  var t1 = CORE.listTip(side, 'c_order', PK, baseRows);
  var createdRow = t1.rows.filter(function (r) { return r.documentno === 'SO-NEW-1'; })[0];
  console.log('§CRUD-LIST key=c_order created=' + t1.created.length + ' hidden=' + t1.hidden.length + ' rows=' + t1.rows.length + ' source=sidecar');
  verdict(!!createdRow && t1.rows.length === 4 && t1.created.length === 1,
          'CREATE: new row SO-NEW-1 appears in list tip (4 rows, 1 created)', 'rows=' + t1.rows.length + ' newGrand=' + (createdRow && createdRow.grandtotal));

  // ── L3 · DELETE hides an existing baseline row ──────────────────────────────────────────────────
  console.log('\nL3 — a CRUD_DELETE tombstone HIDES an existing baseline row');
  await commitDelete(side, 'c_order', 1001, 1001, 'crud-delete-1');
  var t2 = CORE.listTip(side, 'c_order', PK, baseRows);
  var has1001 = t2.rows.some(function (r) { return r[PK] === 1001; });
  console.log('§CRUD-LIST key=c_order created=' + t2.created.length + ' hidden=' + t2.hidden.length + ' rows=' + t2.rows.length + ' source=sidecar');
  verdict(!has1001 && t2.hidden.indexOf(1001) >= 0 && t2.rows.length === 3,
          'DELETE: baseline row 1001 hidden from list tip (3 visible: 2 base + 1 created)', 'rows=' + t2.rows.length + ' hidden=' + JSON.stringify(t2.hidden));

  // ── L4 · REHYDRATE — export+reopen the sidecar (page-reload analogue) → SAME tip ────────────────
  console.log('\nL4 — rehydrate the sidecar (page-reload analogue) → the tip survives');
  var buf = side.export();
  var side2 = new SQL.Database(new Uint8Array(buf));
  KO.ensureTable(side2);
  var t3 = CORE.listTip(side2, 'c_order', PK, baseRows);
  var stillNew = t3.rows.some(function (r) { return r.documentno === 'SO-NEW-1'; });
  var still1001Gone = !t3.rows.some(function (r) { return r[PK] === 1001; });
  console.log('§CRUD-LIST key=c_order created=' + t3.created.length + ' hidden=' + t3.hidden.length + ' rows=' + t3.rows.length + ' source=sidecar(rehydrated)');
  verdict(stillNew && still1001Gone && t3.rows.length === 3,
          'REHYDRATE: created shows + tombstoned hides after sidecar reopen (state survives)', 'rows=' + t3.rows.length);

  // ── L5 · BASELINE IMMUTABLE — the passed-in bundle rows are untouched ───────────────────────────
  console.log('\nL5 — the immutable baseline rows are never mutated (overlay-only)');
  verdict(JSON.stringify(baseRows) === baseRowsFrozen, 'baseline rows unchanged (glassbowl_data.db analogue immutable)', 'frozen==now:' + (JSON.stringify(baseRows) === baseRowsFrozen));

  console.log('\n§CRUD-LIST ' + (fails ? 'FAIL — ' + fails + ' checks red'
    : 'PASS — created rows surface + tombstoned rows hide from the SIGNED sidecar; baseline immutable; survives rehydrate'));
  process.exit(fails ? 1 : 0);
})();
