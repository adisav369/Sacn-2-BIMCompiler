// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-PROC-PICKER is ✅ DONE in ERP_COVERAGE_MATRIX.md.
// SCOPE: W-PROC-PICKER — prove ad_process.js pickUsedProcesses returns the ACTUALLY-USED subset of AD_Process
//   via AD_Process ⋈ AD_Process_Access ⋈ AD_WF_Node, against an INDEPENDENT live-oracle SQL over the SAME
//   tables in the live iDempiere Postgres DB. The corpus (337 classnames) stays named-deferred; this is the
//   MECHANISM that picks the used subset on-demand (GAP_CLOSURE_LANE §P3.5).
// ORACLE: independent SQL over `docker exec postgres psql ... idempiere` (the same DB used to generate ad_full.db):
//   (a) byAccess = SELECT DISTINCT ad_process_id FROM ad_process ⋈ ad_process_access (isactive='Y')
//   (b) byWorkflow = SELECT DISTINCT ad_process_id FROM ad_process ⋈ ad_wf_node (isactive='Y', ad_process_id!=NULL)
//   (c) union of (a) and (b)
// The engine (SQLite ad_full.db) and oracle (PG) must return the SAME set of ad_process_id values.
// §FALSIFIER: restrict picker to a non-existent role → byAccess.length=0 (selection is load-bearing, not cosmetic).
// NON-INVENT. §-log first — READ build/erp/poc_proc_picker.log before concluding (exit code ≠ evidence).
// Run: bash build/erp/run_witness.sh scripts/poc_proc_picker.js
'use strict';
var path = require('path'), cp = require('child_process');
var Database = require('better-sqlite3');
var P = require(path.join(__dirname, '..', 'build', 'erp', 'ad_process.js'));

var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var PG = { container: 'postgres', user: 'adempiere', db: 'idempiere' };
var US = '|';
var fails = 0;
function ok(cond, msg, detail) { if (!cond) fails++; console.log('  ' + (cond ? '✓' : '✗ FAIL:') + ' ' + msg + (detail ? ' — ' + detail : '')); }
function psql(sql) {
  return cp.execFileSync('docker', ['exec', '-e', 'PGOPTIONS=-c search_path=adempiere', PG.container,
    'psql', '-U', PG.user, '-d', PG.db, '-t', '-A', '-F', US, '-c', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 8 * 1024 * 1024 });
}
function pgIds(sql) {
  return psql(sql).split('\n').filter(Boolean).map(function (l) { return Number(l.split(US)[0]); }).sort(function (a, b) { return a - b; });
}

console.log('=== §PROC-PICKER witness (W-PROC-PICKER) — on-demand AD_Process picker vs live-oracle ===');
console.log('    Mechanism: AD_Process ⋈ AD_Process_Access ⋈ AD_WF_Node (GAP_CLOSURE_LANE §P3.5)');
console.log('    Engine: build/erp/ad_process.pickUsedProcesses on ad_full.db');
console.log('    Oracle: live iDempiere Postgres (' + PG.container + '/' + PG.db + ')\n');

try { psql('SELECT 1'); } catch (e) {
  console.log('§PROC-PICKER-SKIP oracle Postgres unreachable → honest ⬜, not ✅');
  process.exit(1);
}
console.log('§PROC-PICKER-ORACLE live iDempiere Postgres reachable (' + PG.container + '/' + PG.db + ')\n');

var db = new Database(DB_PATH, { readonly: true });

// ── ENGINE: call the picker ─────────────────────────────────────────────────────────────────────────────────
var picked = P.pickUsedProcesses(db);
var engAccessIds = picked.byAccess.map(function (r) { return Number(r.ad_process_id); }).sort(function (a, b) { return a - b; });
var engWfIds    = picked.byWorkflow.map(function (r) { return Number(r.ad_process_id); }).sort(function (a, b) { return a - b; });
var engUnionIds = picked.union.map(function (r) { return Number(r.ad_process_id); }).sort(function (a, b) { return a - b; });

console.log('§PROC-PICKER-ENGINE byAccess=' + engAccessIds.length + ' byWorkflow=' + engWfIds.length + ' union=' + engUnionIds.length);

// ── ORACLE: independent SQL join over live PG ────────────────────────────────────────────────────────────────
var oraAccessIds = pgIds(
  "SELECT DISTINCT p.ad_process_id " +
  "FROM ad_process p " +
  "JOIN ad_process_access pa ON pa.ad_process_id=p.ad_process_id AND pa.isactive='Y' " +
  "WHERE p.isactive='Y' ORDER BY p.ad_process_id"
);
var oraWfIds = pgIds(
  "SELECT DISTINCT p.ad_process_id " +
  "FROM ad_process p " +
  "JOIN ad_wf_node n ON n.ad_process_id=p.ad_process_id AND n.isactive='Y' " +
  "WHERE p.isactive='Y' ORDER BY p.ad_process_id"
);
// union
var oraUnionSeen = {}, oraUnionIds = [];
oraAccessIds.concat(oraWfIds).forEach(function (id) { if (!oraUnionSeen[id]) { oraUnionSeen[id] = true; oraUnionIds.push(id); } });
oraUnionIds.sort(function (a, b) { return a - b; });

console.log('§PROC-PICKER-ORACLE byAccess=' + oraAccessIds.length + ' byWorkflow=' + oraWfIds.length + ' union=' + oraUnionIds.length + '\n');

// ── DIFF ────────────────────────────────────────────────────────────────────────────────────────────────────
function setEq(a, b) { return a.join(',') === b.join(','); }
function symDiff(a, b) { var sa = {}, diff = []; a.forEach(function (x) { sa[x] = 1; }); b.forEach(function (x) { if (!sa[x]) diff.push('+' + x); }); a.forEach(function (x) { if (b.indexOf(x) < 0) diff.push('-' + x); }); return diff; }

// 1. byAccess lens
var accessDiff = symDiff(engAccessIds, oraAccessIds);
console.log('§PROC-PICKER-ACCESS engine=' + engAccessIds.length + ' oracle=' + oraAccessIds.length + ' diff=' + accessDiff.length);
ok(oraAccessIds.length > 0, 'access oracle has real data (processes with access grants)', 'count=' + oraAccessIds.length);
ok(setEq(engAccessIds, oraAccessIds),
   'byAccess: engine process-id set == oracle (AD_Process ⋈ AD_Process_Access)',
   'diff=' + JSON.stringify(accessDiff.slice(0, 5)));

// 2. byWorkflow lens
var wfDiff = symDiff(engWfIds, oraWfIds);
console.log('§PROC-PICKER-WORKFLOW engine=' + engWfIds.length + ' oracle=' + oraWfIds.length + ' diff=' + wfDiff.length);
ok(oraWfIds.length > 0, 'workflow oracle has real data (wf_nodes with process refs)', 'count=' + oraWfIds.length);
ok(setEq(engWfIds, oraWfIds),
   'byWorkflow: engine process-id set == oracle (AD_WF_Node ⋈ AD_Process)',
   'diff=' + JSON.stringify(wfDiff.slice(0, 5)));

// 3. union
var unionDiff = symDiff(engUnionIds, oraUnionIds);
console.log('§PROC-PICKER-UNION engine=' + engUnionIds.length + ' oracle=' + oraUnionIds.length + ' diff=' + unionDiff.length);
ok(setEq(engUnionIds, oraUnionIds),
   'union: engine process-id set == oracle (byAccess ∪ byWorkflow) — ORACLE-EQUIVALENT',
   'diff=' + JSON.stringify(unionDiff.slice(0, 5)));

// Coverage tally: union vs total active processes
var totalActive = +psql("SELECT COUNT(*) FROM ad_process WHERE isactive='Y'").trim();
console.log('\n§PROC-PICKER-COVERAGE totalActive=' + totalActive + ' picked(union)=' + engUnionIds.length + ' named-deferred(corpus)=337');
// In this seed ALL active processes carry access grants → picked == totalActive is valid (no dead procs).
// The mechanism is proven load-bearing by the §FALSIFIER (role filter collapses byAccess to 0).
ok(engUnionIds.length > 0 && engUnionIds.length <= totalActive, 'picker returns a non-empty subset (≤ totalActive)', 'picked=' + engUnionIds.length + ' of ' + totalActive);

// ── §FALSIFIER: non-existent role → byAccess collapses to 0 ─────────────────────────────────────────────────
console.log('\n§PROC-PICKER-FALSIFIER: restrict to non-existent role 99999 → byAccess must be empty');
var pickedFalse = P.pickUsedProcesses(db, { roles: [99999] });
console.log('§PROC-PICKER-FALSIFIER byAccess=' + pickedFalse.byAccess.length + ' (must be 0)');
ok(pickedFalse.byAccess.length === 0, '§FALSIFIER: role=99999 → byAccess empty (access filter is load-bearing, not cosmetic)');
ok(pickedFalse.byWorkflow.length > 0, '§FALSIFIER: byWorkflow still populated (independent lens unaffected by role filter)', 'wf=' + pickedFalse.byWorkflow.length);

console.log('\n' + (fails === 0
  ? '🟢 W-PROC-PICKER PASS — pickUsedProcesses returns the exactly-used subset: ' + engUnionIds.length + ' processes (byAccess=' + engAccessIds.length + ', byWorkflow=' + engWfIds.length + ') == live-oracle; §FALSIFIER fires (role 99999 → byAccess=0)'
  : '🔴 W-PROC-PICKER FAIL — ' + fails + ' assertion(s) failed'));
process.exit(fails > 0 ? 2 : 0);
