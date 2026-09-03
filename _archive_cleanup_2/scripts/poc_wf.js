#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_wf.js — W-WF witness. Opens canonical build/erp/ad_full.db and drives build/erp/ad_workflow.js over a
// REAL workflow: walk the node graph from the start node creating an activity per node (WF 50000), route a
// split node by std-user-next vs an explicit approval decision (node 200033), and §FALSIFIER a leaf node
// (walk terminates, no invented loop) + an approval override changing the route.
// Implementing ERP_COVERAGE_MATRIX.md §AD_Workflow (ranked GAP #4) — Witness: W-WF
// Run: node scripts/poc_wf.js 2>&1 | tee build/erp/poc_wf.log   (read the log; exit code != evidence)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var W = require(path.join(__dirname, '..', 'build', 'erp', 'ad_workflow.js'));
var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

console.log('═══ W-WF — workflow node-walk + activity + routing (real ad_full.db AD_WF_* graph) ═══\n');
var db = new Database(DB_PATH, { readonly: true });

// ── coverage: the graph is AD data ──────────────────────────────────────────────────────────────────────
var cov = W.coverageScan(db);
console.log('§WF_COVERAGE workflows=' + cov.workflows + ' nodes=' + cov.nodes + ' nexts=' + cov.nexts + ' (mechanism: 1–2 walked; corpus named-deferred)');
verdict(cov.workflows === 58 && cov.nodes === 262 && cov.nexts === 207, 'AD workflow graph matches matrix (58 wf / 262 nodes / 207 nexts)', 'wf=' + cov.workflows + ' n=' + cov.nodes + ' x=' + cov.nexts);

// ── walk a real workflow (50000): node-walk + activity per node + next routing ──────────────────────────
console.log('\n── walk WF 50000 (Manufacturing Mgmt Setup) from its start node ──');
var r = W.walk(db, 50000);
r.activities.forEach(function (a) { console.log('§WF node=' + a.node + ' action=' + a.action + ' next=' + (a.next == null ? 'END' : a.next) + ' activity=' + a.status); });
verdict(r.path.join('>') === '50000>50002>50001' && r.activities.length === 3 && r.completed,
  'walk follows the real graph 50000>50002>50001, 3 activities created, terminates', 'path=' + r.path.join('>'));

// ── routing a SPLIT node (200033 has 2 nexts: 200034 seq10, 200036 seq100) ──────────────────────────────
console.log('\n── route a split node (200033 → {200034 seq10, 200036 seq100}) ──');
var stdNext = W.nextNode(db, 200033, {});                              // default = std-user-next (lowest seqno)
console.log('§WF route node=200033 stdUserNext=' + stdNext + ' (lowest seqno)');
verdict(stdNext === 200034, 'unrouted split picks the std-user-next 200034 (seqno 10), not 200036', 'next=' + stdNext);

var approved = W.nextNode(db, 200033, { route: { 200033: 200036 } });  // explicit approval decision overrides
console.log('§WF route node=200033 approvalDecision→' + approved + ' (override)');
verdict(approved === 200036, 'an explicit approval decision routes to 200036 (routing is load-bearing)', 'next=' + approved);

// ── §FALSIFIER 1: a leaf node terminates the walk (no invented loop / next) ─────────────────────────────
console.log('\n── §FALSIFIER — graph edges are load-bearing ──');
var leafNexts = W.nodeNexts(db, 50001);                                // 50001 is the last node, no next
console.log('§FALSIFIER node=50001 nexts=' + leafNexts.length + ' → walk must END here');
verdict(leafNexts.length === 0 && r.activities[r.activities.length - 1].next == null,
  'leaf node 50001 has no next → walk terminates (no invented edge)', 'leafNexts=' + leafNexts.length);

// ── §FALSIFIER 2: an approval decision to a NON-existent edge falls back to std (cannot route off-graph) ──
var offGraph = W.nextNode(db, 200033, { route: { 200033: 999999 } });
console.log('§FALSIFIER node=200033 route→999999(off-graph) → actual=' + offGraph);
verdict(offGraph === 200034, 'an off-graph approval decision cannot route off the real edges (falls back to std-next)', 'next=' + offGraph);

console.log('\n' + (fails === 0 ? '🟢 W-WF PASS' : '🔴 W-WF FAIL (' + fails + ')') +
  ' — workflow walked on canonical ad_full.db; activities created, routing load-bearing, edges enforced. Re-verdict AD_Workflow + GAP #4 (⛔→🟡).');
db.close();
process.exit(fails === 0 ? 0 : 1);
