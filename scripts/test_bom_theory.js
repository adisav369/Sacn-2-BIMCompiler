#!/usr/bin/env node
/**
 * test_bom_theory.js — ⚠ DO NOT REMOVE — Scope guard
 * Scope: empirically test the BOM-unification theory against the WHOLE iDempiere
 *        AD dictionary (ad_seed.db, ~1003 tables, 26k columns). Read the log
 *        after the run — the §BOM_TEST lines ARE the verdict. Exit code is not it.
 *
 * THEORY (docs/ERP.md + this session): every domain FK is either
 *   (A) a STRUCTURE edge — CONTAINMENT (parent owns child) or DERIVATION
 *       (one source document replicated into purpose-specific docs by a verb), or
 *   (B) a COMPONENT CITATION (a leaf naming master/reference data by quantity),
 *   and STRUCTURE edges form DAGs (BOM = recursive parent→children).
 *
 * Each test names the issue it proves/disproves:
 *   T1 resolution  — % FKs resolving to a real target table (bounds confidence).
 *   T2 acyclicity  — containment & derivation graphs are DAGs. A CYCLE FALSIFIES
 *                    the BOM-tree claim. (the falsifiable core)
 *   T3 spine       — list derivation edges; should read as document flow.
 *   T4 coverage    — fraction of tables that are documents / parents / leaves.
 *   T5 hubs        — rank docs by derivation IN-degree; source docs (C_Order …)
 *                    MUST surface as hubs, else the source-replication claim fails.
 *
 * Deterministic. Extract-only. No invention.
 */
'use strict';
var path = require('path');
var execFileSync = require('child_process').execFileSync;

var DB = process.env.AD_SEED_DB ||
  path.join(process.env.HOME, 'bim-ootb', 'viewer', 'ad_seed.db');

function q(sql) {
  var out = execFileSync('sqlite3', [DB, '-json', sql],
    { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 }).trim();
  return out ? JSON.parse(out) : [];
}
function L(m) { console.log('§BOM_TEST ' + m); }

// System / multi-tenant / audit citations — universal noise, not domain edges.
var SYS = { ad_client_id: 1, ad_org_id: 1, createdby: 1, updatedby: 1 };

// ── Load the gamut ───────────────────────────────────────────────────────────
L('source=' + DB);
var tableRows = q("SELECT AD_Table_ID AS id, TableName AS name FROM AD_Table");
var tableById = {}, nameByLc = {};
tableRows.forEach(function (r) { tableById[r.id] = r.name; nameByLc[r.name.toLowerCase()] = r.name; });

// Documents = tables that have a DocStatus column.
var docRows = q("SELECT DISTINCT t.TableName AS name FROM AD_Column c " +
  "JOIN AD_Table t ON c.AD_Table_ID=t.AD_Table_ID WHERE c.ColumnName='DocStatus'");
var isDoc = {}; docRows.forEach(function (r) { isDoc[r.name] = true; });

// Containment pairs (childTable → parentTable) from the AD_Tab hierarchy.
var tabRows = q("SELECT t.AD_Window_ID AS win, t.SeqNo AS seq, t.TabLevel AS lvl, " +
  "tb.TableName AS tbl FROM AD_Tab t JOIN AD_Table tb ON t.AD_Table_ID=tb.AD_Table_ID " +
  "WHERE t.IsActive='Y' ORDER BY t.AD_Window_ID, t.SeqNo");
var contain = {};                 // "child>parent" -> true
var containEdges = [];            // [child, parent]
(function () {
  var byWin = {};
  tabRows.forEach(function (r) { (byWin[r.win] = byWin[r.win] || []).push(r); });
  Object.keys(byWin).forEach(function (w) {
    var tabs = byWin[w], stack = {};
    tabs.forEach(function (t) {
      stack[t.lvl] = t.tbl;
      if (t.lvl > 0 && stack[t.lvl - 1] && stack[t.lvl - 1] !== t.tbl) {
        var key = t.tbl + '>' + stack[t.lvl - 1];
        if (!contain[key]) { contain[key] = true; containEdges.push([t.tbl, stack[t.lvl - 1]]); }
      }
    });
  });
})();

// ── Enumerate FK columns (declared FK ref types) ─────────────────────────────
var fkRows = q("SELECT t.TableName AS src, c.ColumnName AS col, c.AD_Reference_ID AS ref " +
  "FROM AD_Column c JOIN AD_Table t ON c.AD_Table_ID=t.AD_Table_ID " +
  "WHERE c.AD_Reference_ID IN (18,19,30) AND c.IsActive='Y'");

function resolveTarget(col) {                       // strip _ID, match a real table
  if (!/_ID$/i.test(col)) return null;
  var base = col.replace(/_ID$/i, '');
  return nameByLc[base.toLowerCase()] || null;
}

var bucket = { CONTAINMENT: 0, DERIVATION: 0, CITATION: 0, SELF: 0, UNRESOLVED: 0, SYSTEM: 0 };
var derivEdges = [];              // [src, tgt] both documents
var derivInDeg = {};              // tgtDoc -> Set(srcDoc)
var total = 0;
fkRows.forEach(function (f) {
  total++;
  if (SYS[f.col.toLowerCase()]) { bucket.SYSTEM++; return; }
  var tgt = resolveTarget(f.col);
  if (!tgt) { bucket.UNRESOLVED++; return; }
  if (tgt === f.src) { bucket.SELF++; return; }            // recursive containment
  if (contain[f.src + '>' + tgt]) { bucket.CONTAINMENT++; return; }
  if (isDoc[f.src] && isDoc[tgt]) {
    bucket.DERIVATION++;
    derivEdges.push([f.src, tgt]);
    (derivInDeg[tgt] = derivInDeg[tgt] || {})[f.src] = true;
    return;
  }
  bucket.CITATION++;
});

// ── T1 resolution ────────────────────────────────────────────────────────────
var domain = total - bucket.SYSTEM;
var resolved = domain - bucket.UNRESOLVED;
L('--- T1 resolution ---');
L('FK columns total=' + total + ' system=' + bucket.SYSTEM + ' domain=' + domain);
L('resolved=' + resolved + ' (' + (100 * resolved / domain).toFixed(1) + '%) unresolved=' +
  bucket.UNRESOLVED + ' (_ID-convention misses, e.g. SalesRep_ID→AD_User)');
L('buckets: containment=' + bucket.CONTAINMENT + ' derivation=' + bucket.DERIVATION +
  ' citation=' + bucket.CITATION + ' selfRecursive=' + bucket.SELF);

// ── T2 acyclicity (the falsifiable core) ─────────────────────────────────────
function findCycle(edges) {                          // edges: [from,to]; DFS color
  var adj = {}; edges.forEach(function (e) { (adj[e[0]] = adj[e[0]] || []).push(e[1]); });
  var color = {}, cyc = null;                        // 0/undef=white 1=gray 2=black
  function dfs(u, stack) {
    color[u] = 1; stack.push(u);
    var nb = adj[u] || [];
    for (var i = 0; i < nb.length && !cyc; i++) {
      var v = nb[i];
      if (color[v] === 1) { cyc = stack.slice(stack.indexOf(v)).concat(v); return; }
      if (!color[v]) dfs(v, stack);
    }
    stack.pop(); color[u] = 2;
  }
  Object.keys(adj).forEach(function (n) { if (!color[n] && !cyc) dfs(n, []); });
  return cyc;
}
L('--- T2 acyclicity (cycle => BOM-tree FALSIFIED) ---');
var cC = findCycle(containEdges);
L('containment edges=' + containEdges.length + ' => ' + (cC ? 'CYCLE: ' + cC.join('->') : 'DAG (acyclic) PASS'));
var cD = findCycle(derivEdges);
L('derivation edges=' + derivEdges.length + ' => ' + (cD ? 'CYCLE: ' + cD.join('->') : 'DAG (acyclic) PASS'));

// ── T3 spine (eyeball the document flow) ─────────────────────────────────────
L('--- T3 derivation spine (src doc -> source doc) ---');
var seen = {};
derivEdges.forEach(function (e) {
  var k = e[0] + '->' + e[1];
  if (!seen[k]) { seen[k] = true; }
});
Object.keys(seen).sort().slice(0, 40).forEach(function (k) { L('  ' + k); });
L('  (distinct derivation edges=' + Object.keys(seen).length + ')');

// ── T4 coverage ──────────────────────────────────────────────────────────────
var parents = {}; containEdges.forEach(function (e) { parents[e[1]] = true; });
L('--- T4 coverage ---');
L('tables=' + tableRows.length + ' documents=' + Object.keys(isDoc).length +
  ' containmentParents=' + Object.keys(parents).length);

// ── T5 source-document hubs (test C_Order claim) ─────────────────────────────
L('--- T5 derivation hubs (doc, #distinct docs that derive from it) ---');
var hubs = Object.keys(derivInDeg).map(function (d) {
  return [d, Object.keys(derivInDeg[d]).length];
}).sort(function (a, b) { return b[1] - a[1]; });
hubs.slice(0, 15).forEach(function (h) { L('  ' + h[0] + '  in-degree=' + h[1]); });
var orderRank = hubs.findIndex(function (h) { return h[0] === 'C_Order'; });
L('C_Order hub rank=' + (orderRank < 0 ? 'NOT A HUB (claim FAILS)' : '#' + (orderRank + 1) +
  ' in-degree=' + hubs[orderRank][1] + ' (source-replication CONFIRMED)'));

L('VERDICT: see T2 (acyclic?) + T5 (C_Order hub?) — those make the call.');
