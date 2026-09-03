#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_wf_harden.js — W-WF-HARDEN witness.
// Implementing ERP_EXECUTION_ROADMAP.md §PHASE B B-2 + prompts/FABLE5_WORKFLOW_ORACLE.md §W-1..§W-4
//   (the LAST ⬜ in the equivalence ledger: ad_workflow) — Witness: W-WF-HARDEN
//
// ISSUE THIS TEST PROVES/DISPROVES: ad_workflow.js claims to walk the AD workflow graph the way
// iDempiere's MWFProcess/MWFActivity/MWFNodeNext/StateEngine do. Until now that claim was
// mechanism-level only (W-WF: graph reads + seqno routing, 🟡). This witness ORACLE-DIFFS the walk
// against REAL traces written by real iDempiere:
//
//   ORACLE side (two arms):
//     (1) the live PG `idempiere_test` (docker `postgres`) — the SAME GardenWorld instance every
//         §H-3/B-1 harden diffed against — carries 11 ad_wf_process / 13 ad_wf_activity /
//         13 ad_wf_eventaudit rows written when real GardenWorld documents were processed
//         (verified 2026-06-12). Captured verbatim → build/erp/oracle/wf_oracle.json (versioned).
//     (2) the REAL compiled iDempiere classes (org.adempiere.base/target/classes) driven headless by
//         scripts/logic_oracle/WorkflowOracle.java — the B-1 LogicOracle technique one level up:
//         org.compiere.process.StateEngine REAL legal-transition table AND REAL mutators (setState),
//         + MWFNodeNext.isValidFor:215-243 std-user doc gate verbatim over compiled DocAction
//         constants (omissions named in WorkflowOracle.java header).
//
//   ENGINE side: ad_workflow.js `replay` over the SAME workflow definitions (build/erp/ad_full.db,
//   md5-set cross-checked == live PG) + the SAME document context. Document context at workflow
//   start is EXTRACTED, never authored: C_Order.DocStatus/DocAction AD_Column defaultvalues
//   ('DR'/'CO' — the state a draft order carries when the user invokes Complete), cross-checked
//   identical in ad_full.db and the live PG; the traced order's CURRENT row (docstatus='CO') then
//   anchors the replay's THREADED final doc status.
//
// DIFF (per §W-2, all 11 processes): (a) node SEQUENCE == ad_wf_activity/ad_wf_eventaudit order ·
// (b) transitions taken == trace adjacency AND every std-user gate verdict == compiled-constants
// oracle GATE · (c) per-activity terminal WFState + eventtype + process WFState == trace, and every
// engine state hop replays through the REAL compiled StateEngine mutators.
//
// HONEST SKIPS (named, counted, never synthesized — §HARDEN-SKIPS): node actions / WF features the
// 13 captured activities never exercise; the single conditioned transition (workflow 115, untraced);
// MWFNextCondition.evaluate headless (drags PO/Env/DB).
//
// §FALSIFIER (load-bearing): (1) flip the captured DocAction 'CO'→'--' → the walk takes the OTHER
// transition (183→184) on BOTH sides, diverging from the unflipped walk; (2) drop node 185 from the
// definition → replay FAILS LOUDLY (CA abort + diff flags), never a silent skip; (3) the compiled
// StateEngine REJECTS closed→open hops the engine never emits (diff not vacuous on legal hops).
//
// NON-INVENT: definitions = ad_full.db (md5-set == live PG); traces + doc rows = live PG; oracle =
// real compiled iDempiere classes. pg/javac down ⇒ honest SKIP exit 1. Read-only, deterministic.
// Run: bash build/erp/run_witness.sh scripts/poc_wf_harden.js   (then READ build/erp/poc_wf_harden.log)
'use strict';
var path = require('path');
var fs = require('fs');
var cp = require('child_process');
var crypto = require('crypto');
var Database = require('better-sqlite3');
var WF = require(path.join(__dirname, '..', 'build', 'erp', 'ad_workflow.js'));
var FSM = require(path.join(__dirname, '..', 'build', 'erp', 'ad_docfsm.js'));
var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var ORACLE_JSON = path.join(__dirname, '..', 'build', 'erp', 'oracle', 'wf_oracle.json');

var PG = { container: 'postgres', user: 'adempiere', db: 'idempiere_test' };
var IDEMPIERE = path.join(process.env.HOME || '/home/red1', 'idempiere-dev-setup', 'idempiere');
var ORACLE_CP = [
  path.join(IDEMPIERE, 'org.adempiere.base', 'target', 'classes'),
  path.join(IDEMPIERE, 'org.idempiere.p2', 'target', 'repository', 'plugins', '*')
].join(':');
var ORACLE_SRC = path.join(__dirname, 'logic_oracle', 'WorkflowOracle.java');
var ORACLE_OUT = '/tmp/wf_oracle_build';

var US = '\x1f';
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function md5(s) { return crypto.createHash('md5').update(String(s), 'utf8').digest('hex'); }
function norm(v) { return v == null ? '' : String(v); }
function pgRaw(sql) {
  return cp.execFileSync('docker', ['exec', PG.container, 'psql', '-U', PG.user, '-d', PG.db, '-t', '-A', '-F', US, '-c', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 64 * 1024 * 1024 });
}
function pgRows(sql) { return pgRaw(sql).split('\n').filter(function (s) { return s.length; }).map(function (l) { return l.split(US); }); }

console.log('═══ W-WF-HARDEN — ad_workflow.js diffed vs REAL iDempiere workflow traces + compiled StateEngine (B-2, the LAST ⬜) ═══\n');

// ── 0. entrance: oracle PG up + WorkflowOracle compiles ─────────────────────────────────────────────────
try { pgRows('SELECT 1'); } catch (e) { console.log('§HARDEN-SKIP oracle Postgres (idempiere_test) unreachable → honest ⬜, not ✅'); process.exit(1); }
var t0 = pgRows('SELECT (SELECT count(*) FROM ad_wf_process),(SELECT count(*) FROM ad_wf_activity),(SELECT count(*) FROM ad_wf_eventaudit)')[0];
console.log('§HARDEN-ORACLE live iDempiere Postgres reachable (' + PG.container + '/' + PG.db + ') — REAL traces: ad_wf_process=' + t0[0] + ' ad_wf_activity=' + t0[1] + ' ad_wf_eventaudit=' + t0[2]);
verdict(t0[0] === '11' && t0[1] === '13' && t0[2] === '13', 'trace corpus is the verified entrance corpus (11/13/13 — small K, stated honestly)');
try {
  fs.mkdirSync(ORACLE_OUT, { recursive: true });
  cp.execFileSync('javac', ['-cp', ORACLE_CP, '-d', ORACLE_OUT, ORACLE_SRC], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
} catch (e) { console.log('§HARDEN-SKIP javac failed (' + String(e.stderr || e.message).slice(0, 200) + ') → honest ⬜'); process.exit(1); }
console.log('§HARDEN-ORACLE-BUILD WorkflowOracle.java compiled against real StateEngine + DocAction (org.adempiere.base/target/classes)\n');

function runOracle(lines) {
  var out = cp.execFileSync('java', ['-cp', ORACLE_CP + ':' + ORACLE_OUT, 'WorkflowOracle'],
    { input: lines.join('\n') + '\n', encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'], maxBuffer: 16 * 1024 * 1024 });
  var map = {};
  out.split('\n').filter(function (s) { return s.length; }).forEach(function (l) {
    var i = l.indexOf('\t'); map[l.slice(0, i)] = l.slice(i + 1);
  });
  return map;
}

var db = new Database(DB_PATH, { readonly: true });

// ── 1. §HARDEN-SRC — definition md5-sets: ad_full.db == live PG (the B-1 pattern, kind=wf) ─────────────
console.log('── source cross-check: the workflow DEFINITIONS the engine walks exist IDENTICALLY in ad_full.db and the live PG ──');
var DEF_KINDS = [
  { kind: 'wf_def', cols: ['ad_workflow_id', 'ad_wf_node_id', 'ad_table_id', 'workflowtype', 'isactive'], table: 'ad_workflow' },
  { kind: 'wf_node', cols: ['ad_wf_node_id', 'ad_workflow_id', 'action', 'docaction', 'splitelement', 'joinelement', 'waittime', 'isactive'], table: 'ad_wf_node' },
  { kind: 'wf_nodenext', cols: ['ad_wf_nodenext_id', 'ad_wf_node_id', 'ad_wf_next_id', 'seqno', 'isstduserworkflow', 'transitioncode', 'isactive'], table: 'ad_wf_nodenext' },
  { kind: 'wf_nextcond', cols: ['ad_wf_nextcondition_id', 'ad_wf_nodenext_id', 'seqno', 'andor', 'ad_column_id', 'operation', 'value', 'value2', 'isactive'], table: 'ad_wf_nextcondition' }
];
var srcDiffTotal = 0;
DEF_KINDS.forEach(function (k) {
  var ours = new Set(db.prepare('SELECT ' + k.cols.join(',') + ' FROM ' + k.table).all()
    .map(function (r) { return md5(k.cols.map(function (c) { return norm(r[c]); }).join('|')); }));
  var pg = new Set(pgRows('SELECT ' + k.cols.map(function (c) { return 'COALESCE(' + c + '::text,\'\')'; }).join(',') + ' FROM ' + k.table)
    .map(function (f) { return md5(f.join('|')); }));
  var miss = 0; ours.forEach(function (h) { if (!pg.has(h)) miss++; });
  var extra = 0; pg.forEach(function (h) { if (!ours.has(h)) extra++; });
  srcDiffTotal += miss + extra;
  console.log('§HARDEN-SRC kind=' + k.kind + ' ours=' + ours.size + ' pg=' + pg.size + ' setdiff=' + (miss + extra));
  verdict(miss + extra === 0, k.kind + ' definition row set: ad_full.db == live PG', 'n=' + ours.size);
});
console.log('§HARDEN-SRC kind=wf setdiff=' + srcDiffTotal + ' (ad_workflow+ad_wf_node+ad_wf_nodenext+ad_wf_nextcondition)');

// ── 2. §W-1 capture the oracle trace (versioned fixture, never hand-authored) ───────────────────────────
console.log('\n── §W-1 capture: the 11 REAL workflow processes + activities + event audits + the docs they rode ──');
var procs = pgRows('SELECT ad_wf_process_id, ad_workflow_id, ad_table_id, record_id, wfstate, processed FROM ad_wf_process ORDER BY ad_wf_process_id')
  .map(function (f) { return { id: +f[0], wf: +f[1], table: +f[2], record: +f[3], wfstate: f[4], processed: f[5] }; });
var acts = pgRows('SELECT ad_wf_activity_id, ad_wf_process_id, ad_wf_node_id, wfstate, processed FROM ad_wf_activity ORDER BY ad_wf_process_id, ad_wf_activity_id')
  .map(function (f) { return { id: +f[0], proc: +f[1], node: +f[2], wfstate: f[3], processed: f[4] }; });
var audits = pgRows('SELECT ad_wf_eventaudit_id, ad_wf_process_id, ad_wf_node_id, eventtype, wfstate FROM ad_wf_eventaudit ORDER BY ad_wf_process_id, ad_wf_eventaudit_id')
  .map(function (f) { return { id: +f[0], proc: +f[1], node: +f[2], eventtype: f[3], wfstate: f[4] }; });
// the document context (EXTRACTED): C_Order at-start state = its AD_Column defaultvalues; cross-check both schemas
var defOurs = {}, defPg = {};
db.prepare("SELECT c.columnname cn, c.defaultvalue dv FROM ad_column c JOIN ad_table t ON c.ad_table_id=t.ad_table_id WHERE t.tablename='C_Order' AND c.columnname IN ('DocStatus','DocAction')").all()
  .forEach(function (r) { defOurs[r.cn] = r.dv; });
pgRows("SELECT columnname, defaultvalue FROM ad_column WHERE ad_table_id=259 AND columnname IN ('DocStatus','DocAction')")
  .forEach(function (f) { defPg[f[0]] = f[1]; });
console.log('§HARDEN-CTX C_Order at-start defaults EXTRACTED: ad_full.db DocStatus=' + defOurs.DocStatus + '/DocAction=' + defOurs.DocAction + ' · live PG DocStatus=' + defPg.DocStatus + '/DocAction=' + defPg.DocAction);
verdict(defOurs.DocStatus === 'DR' && defOurs.DocAction === 'CO' && defPg.DocStatus === defOurs.DocStatus && defPg.DocAction === defOurs.DocAction,
  'document context is record-grounded (AD_Column defaultvalues, identical both schemas) — never authored');
var orderRow = pgRows('SELECT docstatus, docaction, processed FROM c_order WHERE c_order_id=200002')[0];
console.log('§HARDEN-CTX traced doc c_order=200002 CURRENT row (live PG): docstatus=' + orderRow[0] + ' docaction=' + orderRow[1] + ' processed=' + orderRow[2]);
var bpCount = +pgRows('SELECT count(*) FROM c_bpartner WHERE c_bpartner_id IN (' + procs.filter(function (p) { return p.table === 291; }).map(function (p) { return p.record; }).join(',') + ')')[0][0];
verdict(bpCount === procs.filter(function (p) { return p.table === 291; }).length, 'every traced C_BPartner record exists in the live PG', 'n=' + bpCount);
fs.mkdirSync(path.dirname(ORACLE_JSON), { recursive: true });
fs.writeFileSync(ORACLE_JSON, JSON.stringify({
  capturedFrom: PG.container + '/' + PG.db, capturedAt: '2026-06-12',
  note: 'REAL iDempiere workflow traces (GardenWorld) — oracle for W-WF-HARDEN. Never edit by hand.',
  defaults: { C_Order: defOurs }, c_order_200002: { docstatus: orderRow[0], docaction: orderRow[1], processed: orderRow[2] },
  processes: procs, activities: acts, eventaudits: audits
}, null, 1));
console.log('§HARDEN-CAPTURE fixtures=' + procs.length + ' processes (' + acts.length + ' activities, ' + audits.length + ' eventaudits) → ' + path.relative(process.cwd(), ORACLE_JSON));

// ── 3. §W-2 the diff: replay each process, assert sequence + transitions + states == trace ──────────────
console.log('\n── §W-2 diff: ad_workflow.replay vs the REAL trace, per process ──');
function ctxFor(p) {
  if (p.table === 259) return { DocStatus: defOurs.DocStatus, DocAction: defOurs.DocAction };  // C_Order doc-process
  return null;                                                                                  // C_BPartner: no docstatus column
}
var diff = 0, allHops = {}, allGates = [];
var perProc = [];
procs.forEach(function (p) {
  var r = WF.replay(db, p.wf, ctxFor(p), { fsm: FSM.transition });
  var ta = acts.filter(function (a) { return a.proc === p.id; });
  var te = audits.filter(function (a) { return a.proc === p.id; });
  var d = [];
  // (a) node sequence
  if (r.path.join(',') !== ta.map(function (a) { return a.node; }).join(',')) d.push('seq-vs-activities engine=[' + r.path + '] trace=[' + ta.map(function (a) { return a.node; }) + ']');
  if (r.path.join(',') !== te.map(function (a) { return a.node; }).join(',')) d.push('seq-vs-eventaudit engine=[' + r.path + '] trace=[' + te.map(function (a) { return a.node; }) + ']');
  // (b) transitions taken == trace adjacency
  for (var i = 0; i + 1 < ta.length; i++) {
    var tr = r.transitions[i];
    if (!tr || tr.from !== ta[i].node || tr.to !== ta[i + 1].node) d.push('transition[' + i + '] engine=' + JSON.stringify(tr) + ' trace=' + ta[i].node + '→' + ta[i + 1].node);
  }
  // (c) states: per-activity terminal WFState + eventtype, process WFState
  r.activities.forEach(function (a, i) {
    if (!ta[i] || a.state !== ta[i].wfstate) d.push('activity[' + i + '].wfstate engine=' + a.state + ' trace=' + (ta[i] && ta[i].wfstate));
    if (!te[i] || a.eventType !== te[i].eventtype || a.state !== te[i].wfstate) d.push('eventaudit[' + i + '] engine=' + a.eventType + '/' + a.state + ' trace=' + (te[i] && (te[i].eventtype + '/' + te[i].wfstate)));
  });
  if (r.processState !== p.wfstate) d.push('process.wfstate engine=' + r.processState + ' trace=' + p.wfstate);
  r.stateHops.forEach(function (h) { allHops[h.from + '|' + h.to] = 1; });
  allGates = allGates.concat(r.gates);
  if (d.length) { diff++; fails++; d.forEach(function (m) { console.log('   ⚠ FINDING process=' + p.id + ' ' + m); }); }
  perProc.push({ p: p, r: r, ok: !d.length });
  console.log('§HARDEN-PROC process=' + p.id + ' wf=' + p.wf + ' (' + r.name + ') record=' + p.table + '/' + p.record +
    ' path=[' + r.path.join('→') + '] states=[' + r.activities.map(function (a) { return a.state; }).join(',') + ']' +
    ' events=[' + r.activities.map(function (a) { return a.eventType; }).join(',') + '] terminal=' + r.processState +
    ' trace=' + p.wfstate + ' diff=' + d.length);
});
console.log('§HARDEN surface=ad_workflow fixtures=' + procs.length + ' diff=' + diff + ' oracle=iDempiere-PG-trace');
verdict(diff === 0, 'all ' + procs.length + ' real processes replay EXACTLY (sequence + transitions + activity/process states + event types)');

// threaded doc anchor: the replayed Process_Order leaves the doc where the live PG row sits today
var orderReplay = perProc.filter(function (x) { return x.p.table === 259; })[0];
console.log('§HARDEN-DOC replayed C_Order docstatus=' + orderReplay.r.doc.DocStatus + ' == live c_order(200002).docstatus=' + orderRow[0]);
verdict(orderReplay.r.doc.DocStatus === orderRow[0], 'threaded doc status at walk end == the CURRENT live row of the doc the trace rode');

// ── 4. §W-3 semantics arm: every engine verdict re-derived by the REAL compiled classes ─────────────────
console.log('\n── §W-3 semantics arm: compiled StateEngine mutators + DocAction std-user gate ──');
var probes = [], pid = 0;
Object.keys(allHops).sort().forEach(function (k) { var f = k.split('|'); probes.push({ id: 'h' + (pid++), line: 'h' + (pid - 1) + '\tSTATE\t' + f[0] + '\t' + f[1], from: f[0], to: f[1], legal: true }); });
[['CC', 'OR'], ['CT', 'OR'], ['CA', 'CC']].forEach(function (f) { probes.push({ id: 'h' + (pid++), line: 'h' + (pid - 1) + '\tSTATE\t' + f[0] + '\t' + f[1], from: f[0], to: f[1], legal: false }); });
var gateLines = allGates.map(function (g, i) { return { id: 'g' + i, line: 'g' + i + '\tGATE\t' + g.DocStatus + '\t' + g.DocAction, g: g }; });
var oc = runOracle(probes.map(function (p) { return p.line; }).concat(gateLines.map(function (g) { return g.line; })));
var hopBad = 0;
probes.forEach(function (p) {
  var f = (oc[p.id] || '').split('\t');                    // isValid \t applied \t resultState
  var ok = p.legal ? (f[1] === 'T' && f[2] === p.to) : (f[1] === 'F' && f[2] === p.from);
  if (!ok) { hopBad++; console.log('   ⚠ FINDING state-hop ' + p.from + '→' + p.to + ' legal=' + p.legal + ' oracle=' + oc[p.id]); }
});
console.log('§HARDEN-STATE hops=' + Object.keys(allHops).length + ' (engine) + 3 illegal probes — compiled StateEngine setState agrees on ' + (probes.length - hopBad) + '/' + probes.length);
verdict(hopBad === 0, 'every engine state hop is applied IDENTICALLY by the real compiled StateEngine; closed→open hops REJECTED', Object.keys(allHops).sort().join(' '));
var gateBad = 0;
gateLines.forEach(function (g) { if (oc[g.id] !== g.g.verdict) { gateBad++; console.log('   ⚠ FINDING gate nodenext=' + g.g.nodenext + ' engine=' + g.g.verdict + ' oracle=' + oc[g.id]); } });
console.log('§HARDEN-GATE stduser-gate verdicts=' + gateLines.length + ' diff=' + gateBad + ' oracle=compiled-DocAction-constants');
verdict(gateBad === 0, 'every std-user transition gate verdict == the verbatim isValidFor block over compiled DocAction constants', 'n=' + gateLines.length);
fails += hopBad + gateBad;

// ── 5. §FALSIFIER — the diff must be able to FAIL ───────────────────────────────────────────────────────
console.log('\n── §FALSIFIER — flipped context reroutes BOTH sides; a dropped node fails LOUDLY ──');
// (1) flip the captured DocAction 'CO' → '--' (a doc NOT being completed): std-user gate must shut on
//     both sides and the walk must take the OTHER transition (183→184), diverging from the trace path.
(function () {
  var base = WF.replay(db, 116, { DocStatus: defOurs.DocStatus, DocAction: defOurs.DocAction }, { fsm: FSM.transition });
  var flip = WF.replay(db, 116, { DocStatus: defOurs.DocStatus, DocAction: '--' }, { fsm: FSM.transition });
  var oGate = runOracle(['fb\tGATE\t' + defOurs.DocStatus + '\t' + defOurs.DocAction, 'ff\tGATE\t' + defOurs.DocStatus + '\t--']);
  // oracle-side transition choice from oracle gate verdicts over the same ordered transitions:
  var nexts = WF.nodeNexts(db, 183);
  function chooseByGate(g) { for (var i = 0; i < nexts.length; i++) { if (nexts[i].stduser === 'Y' ? g === 'T' : true) return nexts[i].next; } return null; }
  var oBase = chooseByGate(oGate.fb), oFlip = chooseByGate(oGate.ff);
  console.log('§HARDEN-FALSIFIER flip DocAction CO→-- : engine base=[' + base.path.join('→') + '] flipped=[' + flip.path.join('→') + '] · oracle first-valid base=183→' + oBase + ' flipped=183→' + oFlip);
  verdict(base.path[1] === 185 && flip.path[1] === 184 && oBase === 185 && oFlip === 184 && flip.path.join() !== base.path.join(),
    'flipped context takes the OTHER transition on BOTH sides (engine==oracle, both diverge from the real trace walk)');
})();
// (2) drop node 185 from a COPY of the definition → the replay must abort LOUDLY (MWFActivity.run:948-952
//     semantics: missing node → Aborted), and the diff-vs-trace must flag it — never a silent skip.
(function () {
  var mem = new Database(':memory:');
  var COPY = {                                                  // wf 116's definition slice, verbatim copy
    ad_workflow: 'WHERE ad_workflow_id=116',
    ad_wf_node: 'WHERE ad_workflow_id=116',
    ad_wf_nodenext: 'WHERE ad_wf_node_id IN (SELECT ad_wf_node_id FROM ad_wf_node WHERE ad_workflow_id=116)',
    ad_wf_nextcondition: 'WHERE 1=0'                            // wf 116 has none (the single row rides wf 115)
  };
  Object.keys(COPY).forEach(function (t) {
    var cols = db.prepare('SELECT name FROM pragma_table_info(?)').all(t).map(function (r) { return r.name; });
    mem.exec('CREATE TABLE ' + t + ' (' + cols.map(function (c) { return '"' + c + '"'; }).join(',') + ')');
    var ins = mem.prepare('INSERT INTO ' + t + ' VALUES (' + cols.map(function () { return '?'; }).join(',') + ')');
    db.prepare('SELECT ' + cols.map(function (c) { return '"' + c + '"'; }).join(',') + ' FROM ' + t + ' ' + COPY[t]).all()
      .forEach(function (r) { ins.run(cols.map(function (c) { return r[c]; })); });
  });
  mem.exec('DELETE FROM ad_wf_node WHERE ad_wf_node_id=185');   // THE DROP
  var r = WF.replay(mem, 116, { DocStatus: defOurs.DocStatus, DocAction: defOurs.DocAction }, { fsm: FSM.transition });
  var traceNodes = acts.filter(function (a) { return a.proc === 200004; }).map(function (a) { return a.node; });
  var flagged = r.abort != null && r.processState === 'CA' && r.path.join(',') !== traceNodes.join(',');
  console.log('§HARDEN-FALSIFIER drop node=185 : replay abort=' + r.abort + ' terminal=' + r.processState + ' path=[' + r.path.join('→') + '] vs trace=[' + traceNodes.join('→') + '] → diff FLAGS it=' + (flagged ? 'Y' : 'N'));
  verdict(flagged, 'a dropped definition node makes the replay FAIL LOUDLY (CA abort per MWFActivity.run:948-952) and the diff catches it');
  mem.close();
})();

// ── 6. §HARDEN-SKIPS — everything the 13 captured activities never exercise (named + counted) ───────────
console.log('\n── §HARDEN-SKIPS — small-K honesty: what the trace does NOT prove ──');
var actionCounts = db.prepare('SELECT action, COUNT(*) n FROM ad_wf_node GROUP BY action ORDER BY n DESC').all();
var exercised = { Z: 1, D: 1, W: 1 };
var unex = actionCounts.filter(function (a) { return !exercised[a.action]; });
console.log('§HARDEN-SKIPS node-actions exercised={Z,D,W} of ' + actionCounts.length + ' seed action types; UNEXERCISED: ' +
  unex.map(function (a) { return a.action + '(' + a.n + ' nodes)'; }).join(' ') + ' — replay THROWS on them, never invents');
console.log('§HARDEN-SKIPS conditioned-transitions=1 (ad_wf_nextcondition 102 → nodenext 100, wf 115 Process_Requisition — UNTRACED; MWFNextCondition.evaluate also undrivable headless: drags PO/Env/DB)');
console.log('§HARDEN-SKIPS split/join=AND: 0 nodes in seed (all 262 X/X) — AND-split & AND-join semantics untested');
console.log('§HARDEN-SKIPS transitioncode: 0 non-null in seed — never evaluated anywhere');
console.log('§HARDEN-SKIPS workflowtypes traced: P (wf 116) + V (wf 131) of seed {G=18,M=4,P=29,V=1,W=6} — 56 of 58 workflows have NO trace');
console.log('§HARDEN-SKIPS StateEngine paths abort/terminate/resume: not in any trace — validated ONLY via the compiled legal-table/mutator probes (§HARDEN-STATE), not via a real audit row');
console.log('§HARDEN-SKIPS K=11 processes / 13 activities — the claim is "11 real processes, diff=0", NOT corpus-wide equivalence');

db.close();
console.log('\n' + (fails ? '🔴 ' + fails + ' verdict(s)/finding(s) FAILED' : '✅ ALL VERDICTS PASS — ad_workflow ORACLE-EQUIVALENT vs real iDempiere traces (11 processes) + real compiled StateEngine/DocAction classes'));
process.exit(fails ? 1 : 0);
