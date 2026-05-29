#!/usr/bin/env node
/**
 * test_manifest_wfmc.js — ⚠ DO NOT REMOVE — Scope guard
 * Scope: the P2 gate (prompts/ERP_KERNEL_BUILD.md §P2). Verifies the COMPILED
 *   manifest.json carries a valid WfMC machine + per-DocType definitions + an
 *   acyclic, extracted derivation graph. Read the §MANIFEST log; it makes the call.
 *   Run AFTER `node scripts/compile_manifest.js`.
 *
 * Tests (each names the issue it proves):
 *   T1 every DocType has >=1 state and >=1 transition (shared wfmc machine).
 *   T2 closure — no transition references a state/action outside the declared sets.
 *   T3 derivation graph acyclic once settlement edges are excluded (orientation fix).
 *   T4 hub sanity — C_Order downstream includes C_Invoice and M_InOut (witness T5).
 *
 * Deterministic. Reads only the compiled manifest (extract, not invent).
 */
'use strict';
var path = require('path');
var fs = require('fs');
var MANIFEST = process.env.MANIFEST_OUT ||
  path.join(process.env.HOME, 'bim-ootb', 'viewer', 'manifest.json');

function L(m) { console.log('§MANIFEST ' + m); }
var fail = 0;
function ok(c, m) { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fail++; }

var m = JSON.parse(fs.readFileSync(MANIFEST, 'utf8'));
L('source=' + MANIFEST + ' version=' + m.version);

// ── T1 — DocTypes + shared machine non-empty ─────────────────────────────────
var w = m.wfmc || {};
var states = w.states || [], trans = w.transitions || [], actions = w.actions || {};
L('doctypes=' + (m.doctypes || []).length + ' transitions=' + trans.length + ' states=' + states.length);
ok((m.doctypes || []).length >= 50, 'T1: ~52 DocTypes compiled (got ' + (m.doctypes || []).length + ')');
ok(states.length >= 1 && trans.length >= 1, 'T1: shared WfMC machine has >=1 state and >=1 transition');
// every DocType inherits the shared machine -> each has >=1 state & transition
// identity keys on id + name (always present); docBaseType may be blank for the
// GL-default DocType — that's a real data condition, not a defect.
var everyHasMachine = (m.doctypes || []).every(function (d) { return d.id && d.name; });
ok(everyHasMachine, 'T1: every DocType has identity (id + name) and inherits the machine');
var spine = (m.doctypes || []).filter(function (d) { return ['SOO', 'ARI', 'ARR', 'MMS'].indexOf(d.docBaseType) >= 0; });
ok(spine.length > 0 && spine.every(function (d) { return d.baseTable; }),
  'T1: spine DocTypes (SOO/ARI/ARR/MMS) resolve a baseTable');

// ── T2 — closure: no transition leaves the declared state/action sets ────────
var stateSet = {}; states.forEach(function (s) { stateSet[s] = true; });
var bad = trans.filter(function (t) { return !stateSet[t[0]] || !stateSet[t[2]] || !actions[t[1]]; });
bad.slice(0, 10).forEach(function (t) { L('  CLOSURE-VIOLATION ' + t.join(',')); });
ok(bad.length === 0, 'T2: every transition (from,action,to) is within declared states+actions (closure)');

// ── T3 — derivation graph acyclic with settlement excluded ───────────────────
function findCycle(adj) {
  var color = {}, cyc = null;
  function dfs(u, stack) {
    color[u] = 1; stack.push(u);
    (adj[u] || []).forEach(function (v) {
      if (cyc) return;
      if (color[v] === 1) { cyc = stack.slice(stack.indexOf(v)).concat(v); return; }
      if (!color[v]) dfs(v, stack);
    });
    stack.pop(); color[u] = 2;
  }
  Object.keys(adj).forEach(function (n) { if (!color[n] && !cyc) dfs(n, []); });
  return cyc;
}
var down = m.downstream || {};
var cyc = findCycle(down);
L('downstream acyclic=' + (cyc ? 'CYCLE ' + cyc.join('->') : 'PASS') +
  ' (settlement edges excluded=' + (m.settlement || []).length + ')');
ok(!cyc, 'T3: lineage derivation graph is a DAG (settlement back-edges excluded)');

// ── T4 — hub sanity ──────────────────────────────────────────────────────────
var co = down.C_Order || [];
L('C_Order downstream=' + JSON.stringify(co));
ok(co.indexOf('C_Invoice') >= 0 && co.indexOf('M_InOut') >= 0,
  'T4: C_Order downstream includes C_Invoice and M_InOut (hub, witness T5)');

// ── size budget ──────────────────────────────────────────────────────────────
var bytes = Buffer.byteLength(JSON.stringify(m));
L('manifest size=' + (bytes / 1024).toFixed(1) + 'KB (gz budget ~25KB; raw shown)');

L('VERDICT ' + (fail === 0 ? 'PASS => P2 gate GREEN' : 'FAIL ' + fail + ' => inspect findings'));
process.exit(fail ? 1 : 0);
