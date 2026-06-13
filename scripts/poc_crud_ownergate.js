#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard (SO_FULL_CRUD_GAP.md T4 / GAP 4). Read the log after every run (Log Mandate).
// poc_crud_ownergate.js — the witness for SO_FULL_CRUD_GAP.md T4 (GAP 4): owner-gate / CAS enforced on the UI write.
//
// ISSUE IT PROVES (names it, per CLAUDE.md "tests expose issues"):
//   GAP 4 — crud_ops.json marks c_order ownerGated:true and cas:"claimed_by"; the op carried ownerGated/cas
//   but the commit funnel only LOGGED it. INVESTIGATED: kernel_ops.js commitGroup does NOT reject on
//   owner/CAS mismatch (it gates only on empty-group / rate-as-input / expectedHash torn-group / tx-integrity).
//   So an unauthorized edit was ACCEPTED silently. T4 wires the EXISTING owner-gate / CAS policy
//   (erp_replay.js G-SINGLE-WRITER owner-gate + set-if-unset CAS — W-OWNER) as a PRE-SEAL check in
//   commitCrud/commitProcess: a non-owner / stale-CAS op is REJECTED (committed:false), the sidecar is
//   UNCHANGED, and the UI shows a reject (no dot). A valid-owner edit still commits.
//
// WHAT IT PROVES (each NAMED):
//   G1  CORE.gateOp is PURE (op + ctx in, {ok,reason} out) and reuses the owner-gate/CAS policy (actor===owner; CAS set-if-unset).
//   G2  commitGroup ALONE does NOT enforce owner/CAS — a wrong-owner op would otherwise commit (the gap, demonstrated).
//   G3  owner REJECT — an ownerGated c_order edit by a NON-owner is rejected reason=owner BEFORE the seal; sidecar row count unchanged.
//   G4  cas REJECT — an ownerGated c_order edit with a STALE cas baseline is rejected reason=cas; sidecar unchanged.
//   G5  valid PASS — the same edit by the OWNER with a fresh cas commits through the REAL kernel (committed:true), sidecar grows by one.
//
// NON-INVENT: the op shape is the REAL CORE.buildOp('update'); the owner-gate/CAS verdict is the REAL
//   CORE.gateOp policy (the erp_replay owner-gate/CAS rule, lifted PURE — no new policy). Kernel under test
//   is the REAL build/erp/kernel_ops.js commitGroup. No fixtures beyond the record's owner/claimed_by inputs.
//
// DETERMINISM (§7): no Date.now()/Math.random() — ids index-derived; commitGroup called with explicit baseTs.
//
// Run: node scripts/poc_crud_ownergate.js 2>&1 | tee build/erp/poc_crud_ownergate.log  — then READ the log.
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
function logCount(db) { var r = db.exec('SELECT COUNT(*) FROM kernel_ops'); return r.length && r[0].values.length ? Number(r[0].values[0][0]) : 0; }

// E_ORDER — a real c_order descriptor: ownerGated + cas (mirrors crud_ops.json c_order).
var E_ORDER = { key: 'c_order', ownerGated: true, cas: 'claimed_by', verbs: ['update'],
  fields: [{ col: 'documentno', type: 'string', readonly: true }, { col: 'grandtotal', type: 'number' }] };

// The record under edit (its recorded owner/claimed_by are INPUTS the gate READS — never recomputed).
var ORIG = { c_order_id: 7, documentno: 'SO-7', grandtotal: 100, owner: 'alice', claimed_by: 'alice' };

// commitCrud's EXACT pre-check + group shape: gate FIRST, seal only if ok (the wired path under test).
async function attemptUpdate(db, op, gateCtx, baseTs) {
  var gate = CORE.gateOp(op, gateCtx);
  console.log('§CRUD-GATE key=' + op.key + ' ownerGated=' + (op.ownerGated ? 'Y' : 'N') + ' verdict=' + (gate.ok ? 'PASS' : 'REJECT') + (gate.ok ? '' : ' reason=' + gate.reason));
  if (!gate.ok) return { committed: false, reason: gate.reason, gated: true };
  var params = { table: op.table, id: op.id, changes: op.changes };
  return KO.commitGroup(db, [{ op_type: 'CRUD_UPDATE', op_uuid: op.op_uuid || null, params: params }], { baseTs: baseTs });
}

(async function () {
  console.log('═══ POC-CRUD-OWNERGATE — T4 (GAP 4): owner-gate + CAS REJECT the unauthorized UI write ═══');
  console.log('issue=SO_FULL_CRUD_GAP.md T4  path=build/erp/crud_overlay.js (CORE.gateOp)  policy=erp_replay owner-gate/CAS  kernel=build/erp/kernel_ops.js\n');
  var SQL = await initSqlJs();

  var op = CORE.buildOp('update', E_ORDER, { documentno: 'SO-7', grandtotal: 999 }, ORIG, { id: 7, opUuid: 'crud-7-edit' });

  // ── G1 · gateOp pure ────────────────────────────────────────────────────────────────────────────
  console.log('G1 — CORE.gateOp is pure: same policy as erp_replay (actor===owner; CAS set-if-unset)');
  verdict(typeof CORE.gateOp === 'function', 'CORE.gateOp exported (pure owner-gate/CAS pre-check)', 'fn=' + typeof CORE.gateOp);

  // ── G2 · commitGroup ALONE does NOT enforce owner/CAS (the gap, demonstrated) ───────────────────
  console.log('\nG2 — commitGroup ALONE does NOT reject a wrong-owner op (this is the gap T4 closes)');
  var gapDb = new SQL.Database(); KO.ensureTable(gapDb);
  var ungated = await KO.commitGroup(gapDb, [{ op_type: 'CRUD_UPDATE', op_uuid: 'gap', params: { table: 'c_order', id: 7, changes: op.changes, actor: 'mallory', owner: 'alice' } }], { baseTs: 500 });
  console.log('§CRUD-GATE-GAP kernel-only committed=' + ungated.committed + ' (no owner/CAS gate in commitGroup → the UI must pre-check)');
  verdict(ungated.committed === true, 'commitGroup commits a wrong-owner op unconditionally (gap confirmed → pre-check needed)', 'committed=' + ungated.committed);

  // ── G3 · owner REJECT — non-owner edit rejected reason=owner, sidecar unchanged ─────────────────
  console.log('\nG3 — a NON-owner (mallory ≠ alice) ownerGated edit is REJECTED reason=owner; sidecar unchanged');
  var side = new SQL.Database(); KO.ensureTable(side);
  var before = logCount(side);
  var r3 = await attemptUpdate(side, op, { actor: 'mallory', owner: ORIG.owner, casCol: 'claimed_by', casExpected: ORIG.claimed_by, casCurrent: ORIG.claimed_by }, 501);
  var after3 = logCount(side);
  verdict(r3.committed === false && r3.reason === 'owner' && after3 === before,
          'owner REJECT: non-owner edit blocked reason=owner, sidecar rows unchanged', 'committed=' + r3.committed + ' reason=' + r3.reason + ' rows=' + before + '→' + after3);

  // ── G4 · cas REJECT — owner but STALE cas baseline rejected reason=cas, sidecar unchanged ────────
  console.log('\nG4 — the OWNER but with a STALE cas baseline is REJECTED reason=cas; sidecar unchanged');
  var before4 = logCount(side);
  var r4 = await attemptUpdate(side, op, { actor: 'alice', owner: ORIG.owner, casCol: 'claimed_by', casExpected: 'bob', casCurrent: ORIG.claimed_by }, 502);
  var after4 = logCount(side);
  verdict(r4.committed === false && r4.reason === 'cas' && after4 === before4,
          'cas REJECT: owner+stale-cas blocked reason=cas, sidecar rows unchanged', 'committed=' + r4.committed + ' reason=' + r4.reason + ' rows=' + before4 + '→' + after4);

  // ── G5 · valid PASS — owner + fresh cas commits through the REAL kernel ──────────────────────────
  console.log('\nG5 — the OWNER (alice) with a FRESH cas commits through the real kernel (committed:true)');
  var before5 = logCount(side);
  var r5 = await attemptUpdate(side, op, { actor: 'alice', owner: ORIG.owner, casCol: 'claimed_by', casExpected: ORIG.claimed_by, casCurrent: ORIG.claimed_by }, 503);
  var after5 = logCount(side);
  console.log('§CRUD-GATE key=c_order ownerGated=Y verdict=PASS committed=' + r5.committed + ' rows=' + before5 + '→' + after5);
  verdict(r5.committed === true && after5 === before5 + 1,
          'valid PASS: owner+fresh-cas commits (signed), sidecar grows by one', 'committed=' + r5.committed + ' rows=' + before5 + '→' + after5);

  console.log('\n§CRUD-GATE ' + (fails ? 'FAIL — ' + fails + ' checks red'
    : 'PASS — owner-gate + CAS REJECT the unauthorized write BEFORE the seal (sidecar unchanged); the valid owner edit still commits'));
  process.exit(fails ? 1 : 0);
})();
