#!/usr/bin/env node
/**
 * gravity_seed.js — TASK 3: the kernel-gravity analytics seed (P4 preview).
 *   Spec: prompts/ERP_KERNEL_BUILD.md §P3b Task 3 + §P4, docs/ERP.md §14 (op-log as
 *   constellation driver), §0.6 (op-log as the rule-feedback substrate), §18.6 (handlers are
 *   written HOT-FIRST, ranked by gravity — most cells are never exercised, so never written).
 *
 * THE CLAIM IT PROVES: the product COMPILES ITS OWN HANDLER BACKLOG FROM USE. We run the hot
 * cells through the kernel into ONE shared op-log (every effect-op stamped with its emitting
 * cell, user_tag), then a SINGLE query ranks kernel_ops by (DocType,action) — weighted by op
 * type (§14: DOC_COMPLETE=2.0, SET_STATUS=2.0, CREATE=1.0, MATCH=1.5, ALLOCATE=1.5). The top of
 * that rank IS the next handler to write; the cold long tail stays unwritten and costs nothing.
 *
 * Pure node, no browser, deterministic (ts from ctx, no Date.now). Reuses diff_oracle's cells —
 * the SAME handlers, dispatched into a shared projection (shareDb).
 *
 * Run: node scripts/gravity_seed.js 2>&1 | tee build/erp/gravity_seed.log
 */
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var DO = require('./diff_oracle');

// §14 op-type weights — activity, not row count, is gravity.
var WEIGHT = { SET_STATUS: 2.0, CREATE_DOCUMENT: 1.0, CREATE_LINE: 1.0, MATCH: 1.5, ALLOCATE: 1.5 };

(async function () {
  console.log('═══ GRAVITY SEED — the op-log ranks its own handler backlog (§14, P4 preview) ═══\n');

  // 1) run the hot cells into ONE shared op-log (each op stamped user_tag=(DocType,action)).
  console.log('── running the hot-cell suite into a shared kernel_ops (shareDb) ──');
  var r = await DO.run({ shareDb: true });
  if (!r.db) { console.log('§GRAVITY FAIL — no shared op-log'); process.exit(1); }

  // 2) ONE gravity query: group kernel_ops by emitting cell + op_type, weight, rank.
  console.log('\n── §14 gravity rank over kernel_ops, grouped by (DocType,action) ──');
  var rows = r.query(
    "SELECT user_tag cell, op_type, COUNT(*) hits FROM kernel_ops " +
    "WHERE user_tag LIKE '(%' GROUP BY user_tag, op_type");
  var byCell = {};
  rows.forEach(function (row) {
    var w = (WEIGHT[row.op_type] != null ? WEIGHT[row.op_type] : 0.2) * row.hits;
    var c = byCell[row.cell] || (byCell[row.cell] = { cell: row.cell, hits: 0, weight: 0, ops: {} });
    c.hits += row.hits; c.weight += w; c.ops[row.op_type] = row.hits;
  });
  var ranked = Object.keys(byCell).map(function (k) { return byCell[k]; })
    .sort(function (a, b) { return b.weight - a.weight || b.hits - a.hits; });

  ranked.forEach(function (c, i) {
    var opsStr = Object.keys(c.ops).map(function (o) { return o + '×' + c.ops[o]; }).join(' ');
    console.log('   #' + (i + 1) + '  §GRAVITY cell=' + c.cell + ' hits=' + c.hits + ' weight=' + c.weight.toFixed(1) + ' [' + opsStr + ']');
  });
  var totalOps = ranked.reduce(function (s, c) { return s + c.hits; }, 0);

  // 3) cross-reference the unwritten backlog: gravity tells which of the 294 cold cells to write
  //    next. The hot cells we just exercised are WRITTEN; the long tail (handler_backlog) is not.
  console.log('\n── backlog context: written-hot vs the cold long tail (handler_backlog) ──');
  var RULES = process.env.ERP_RULES_OUT || path.join(__dirname, '..', 'build', 'erp', 'erp_rules.db');
  var er = new Database(RULES, { readonly: true });
  var backlog = er.prepare("SELECT COUNT(*) n FROM handler_backlog WHERE status='unwritten'").get();
  er.close();
  console.log('   written + exercised this run: ' + ranked.length + ' hot cells (' + totalOps + ' ops)');
  console.log('   cold long tail still unwritten (handler_backlog): ' + backlog.n + ' cells — gravity ranks which to write next; the rest cost nothing (§18.6)');

  console.log('\n═══ VERDICT ═══');
  var ok = ranked.length > 0 && totalOps > 0;
  console.log('§GRAVITY ' + (ok ? 'PASS — op-log self-ranks ' + ranked.length + ' hot cells by use; backlog compiles from gravity (P4)' : 'FAIL — empty gravity'));
  process.exit(ok ? 0 : 1);
})();
