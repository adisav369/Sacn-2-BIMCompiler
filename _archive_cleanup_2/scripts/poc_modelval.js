#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_modelval.js — W-MODELVAL witness. Opens canonical build/erp/ad_full.db and drives the timing-hook
// engine build/erp/ad_modelval.js around real records: a BEFORE_SAVE qty hook and a BEFORE_COMPLETE
// has-lines hook fire and PASS on real rows, then a §FALSIFIER (qty<=0 / 0-line order) is BLOCKED — proving
// the engine reaches the COMPLETE timing (today the engine has only a single SET_STATUS op, groupOps.len=1).
// Implementing ERP_COVERAGE_MATRIX.md §AD_ModelValidator (ranked GAP #9) — Witness: W-MODELVAL
// Run: node scripts/poc_modelval.js 2>&1 | tee build/erp/poc_modelval.log   (read the log; exit code != evidence)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var M = require(path.join(__dirname, '..', 'build', 'erp', 'ad_modelval.js'));
var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

console.log('═══ W-MODELVAL — model-validator timing-hook engine (real ad_full.db records → BEFORE/AFTER dispatch) ═══\n');
var db = new Database(DB_PATH, { readonly: true });

// ── the AD source: 3 registered ModelValidator classes (Java bodies named-deferred) ─────────────────────
var registered = M.readValidators(db);
console.log('§MODELVAL_REGISTERED ad_modelvalidator=' + registered.length + ' (Java classes, named-deferred):');
registered.forEach(function (r) { console.log('   • ' + r.modelvalidationclass + ' [' + r.entitytype + ']'); });

// install the ported timing hooks (the mechanism)
M.installDefaultHooks();
console.log('§MODELVAL_TIMINGS modeled=' + M.TIMINGS.length + ' [' + M.TIMINGS.join(',') + '] portedHooks=' + M.registeredCount());
verdict(M.TIMINGS.indexOf('BEFORE_COMPLETE') >= 0 && M.registeredCount() >= 4,
  'timing engine reaches BEFORE/AFTER × NEW/SAVE/COMPLETE (NOT a single SET_STATUS op)', 'timings=' + M.TIMINGS.length + ' hooks=' + M.registeredCount());

// ── ctx: data accessor a doc hook needs (line count for the has-lines invariant) ────────────────────────
var ctx = { lineCount: function (info) { return db.prepare('SELECT COUNT(*) AS n FROM c_orderline WHERE c_order_id=?').get(info.record.c_order_id).n; } };

// ── BEFORE_SAVE on a real C_OrderLine (qty=1) → passes ──────────────────────────────────────────────────
console.log('\n── hooks fire and PASS on real rows ──');
var line = db.prepare('SELECT * FROM c_orderline WHERE c_orderline_id=100').get();
var s1 = M.fireHooks('BEFORE_SAVE', { table: 'C_OrderLine', record: line }, ctx);
console.log('§MODELVAL_HOOK timing=BEFORE_SAVE table=C_OrderLine fired=' + s1.fired + ' ok=' + s1.ok);
verdict(s1.ok && s1.fired === 1, 'BEFORE_SAVE qty hook fires and passes (qtyordered=' + line.qtyordered + ' > 0)', 'fired=' + s1.fired);

// ── BEFORE_COMPLETE on a real C_Order (id 100, 1 line) → both hooks fire, pass ──────────────────────────
var order = db.prepare('SELECT * FROM c_order WHERE c_order_id=100').get();
var c1 = M.fireHooks('BEFORE_COMPLETE', { table: 'C_Order', record: order }, ctx);
console.log('§MODELVAL_HOOK timing=BEFORE_COMPLETE table=C_Order fired=' + c1.fired + ' ok=' + c1.ok + ' (lines=' + ctx.lineCount({ record: order }) + ')');
verdict(c1.ok && c1.fired === 2, 'BEFORE_COMPLETE fires 2 hooks (hasLines + totalNonNegative) and passes', 'fired=' + c1.fired);

// ── §FALSIFIER 1: a line with qty<=0 is BLOCKED at BEFORE_SAVE ───────────────────────────────────────────
console.log('\n── §FALSIFIER — a hook that should block does ──');
var badLine = Object.assign({}, line, { qtyordered: 0 });
var f1 = M.fireHooks('BEFORE_SAVE', { table: 'C_OrderLine', record: badLine }, ctx);
console.log('§FALSIFIER timing=BEFORE_SAVE qtyordered=0 → ok=' + f1.ok + ' blocked=' + f1.blocked + ' error="' + f1.error + '"');
verdict(f1.ok === false && /qty/i.test(f1.error), 'qty<=0 line is BLOCKED before save (hook is load-bearing)', 'blocked=' + f1.blocked);

// ── §FALSIFIER 2: a 0-line order is BLOCKED at BEFORE_COMPLETE ───────────────────────────────────────────
var emptyCtx = { lineCount: function () { return 0; } };                        // synthetic empty order
var f2 = M.fireHooks('BEFORE_COMPLETE', { table: 'C_Order', record: order }, emptyCtx);
console.log('§FALSIFIER timing=BEFORE_COMPLETE lines=0 → ok=' + f2.ok + ' blocked=' + f2.blocked + ' error="' + f2.error + '"');
verdict(f2.ok === false && f2.blocked === 'MOrder.hasLines', '0-line order is BLOCKED before complete (reaches the COMPLETE timing)', 'blocked=' + f2.blocked);

// gate-off control: an un-hooked (table,timing) is an explicit no-op pass, not a hidden block
var none = M.fireHooks('AFTER_VOID', { table: 'C_Payment', record: {} }, ctx);
verdict(none.ok && none.fired === 0, 'un-hooked (C_Payment, AFTER_VOID) → explicit fired=0 no-op (not a hidden pass/block)', 'fired=' + none.fired);

console.log('\n' + (fails === 0 ? '🟢 W-MODELVAL PASS' : '🔴 W-MODELVAL FAIL (' + fails + ')') +
  ' — timing-hook engine dispatches BEFORE/AFTER validators on canonical ad_full.db; blocks are load-bearing. Re-verdict ModelValidator + GAP #9 (⛔→🟡).');
db.close();
process.exit(fails === 0 ? 0 : 1);
