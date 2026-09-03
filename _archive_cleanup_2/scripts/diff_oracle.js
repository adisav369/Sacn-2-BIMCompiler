#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * diff_oracle.js — the DIFF-ORACLE HARNESS (prompts/ERP_KERNEL_BUILD.md §P3b Task 1).
 *   Spec: docs/ERP.md §18.10 (the oracle: extract don't port), §0.12 (the oracle needs
 *   NO live iDempiere), §18.8 (scope the diff to the document-event op-group).
 *
 * WHAT IT IS. One reusable harness that, per HOT CELL (DocType,action):
 *   1. registers the cell's PURE handler (verbs from erp_engine, opts from loadCell — zero
 *      JS literals), dispatches the document-event through Kernel.dispatch (the §18.6 ladder),
 *   2. NORMALISES the emitted effect-op group + the oracle rows to a canonical effect set
 *      (semantic level, through the ad_table_map slotting — real AD table ⇄ 5-table role),
 *   3. DIFFS them and logs `§ORACLE cell=(DocType,action) checks=N fixtures=N diff=MATCH`.
 *
 * THE ORACLE IS STATIC, NOT LIVE (§0.12). GardenWorld already executed every O2C/P2P/GL
 * transaction, so the M_InOut / C_Invoice / M_MatchPO / M_MatchInv / C_Allocation / GL_Journal
 * rows ALREADY IN ad_full.db ARE the oracle's output. We replay our handler on the INPUTS and
 * compare to those rows — deterministic (no Date.now / sequences / id drift), which the LIVE
 * Docker path is not. **Docker live iDempiere is the documented FALLBACK** for a cell with no
 * GardenWorld data (e.g. GLJ→Fact_Acct: fact_acct=0 rows) or a genuinely AMBIGUOUS case the
 * static data can't oracle (§18.10). Such cells are logged `oracle=NO-DATA(docker-fallback)`,
 * never silently skipped.
 *
 * EXTRACT, DON'T PORT (§18.10). Each cell cites its Java oracle method
 * (// Oracle: org.compiere.model.MXxx.completeIt() — …) as the pre-flight; each oracle check
 * is one named fixture. A new bespoke VERB (a cell that does NOT fit verbs+table+matcher) is
 * a FINDING — logged loud (`§HANDLER new-verb …`), never buried.
 *
 * Run: node scripts/diff_oracle.js 2>&1 | tee build/erp/diff_oracle.log
 */
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var initSqlJs = require('sql.js');
var E = require('./erp_engine');
var R = require('./erp_runtime');
var K = require('./erp_kernel');
var CELLS = require('./diff_oracle_cells');

var AD = process.env.ERP_AD_FULL || path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var RULES = process.env.ERP_RULES_OUT || path.join(__dirname, '..', 'build', 'erp', 'erp_rules.db');
var MANIFEST = require(path.join(process.env.HOME, 'bim-ootb', 'viewer', 'manifest.json'));

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

// ── the canonical diff: two effect SETS keyed by a stable string ─────────────
// mine/oracle are arrays of key strings. MATCH iff same multiset (0 missed, 0 extra).
function diffSet(mine, oracle) {
  function tally(arr) { var m = {}; arr.forEach(function (k) { m[k] = (m[k] || 0) + 1; }); return m; }
  var mm = tally(mine), om = tally(oracle), keys = {};
  Object.keys(mm).forEach(function (k) { keys[k] = 1; });
  Object.keys(om).forEach(function (k) { keys[k] = 1; });
  var matched = 0, missed = 0, extra = 0;
  Object.keys(keys).forEach(function (k) {
    var a = mm[k] || 0, b = om[k] || 0;
    matched += Math.min(a, b);
    if (b > a) missed += b - a;     // oracle had, we didn't
    if (a > b) extra += a - b;      // we made up
  });
  return { matched: matched, missed: missed, extra: extra, ok: missed === 0 && extra === 0, mine: mine.length, oracle: oracle.length };
}

// run the suite. opts.shareDb -> one persisted op-log across cells (Task 3 gravity). Returns
// { fails, db } so a caller can query the accumulated kernel_ops. Exits the process when CLI.
async function run(opts) {
  var ad = new Database(AD, { readonly: true });
  var er = new Database(RULES, { readonly: true });
  var all = function (sql, p) { return ad.prepare(sql).all(p || {}); };
  function ruleQuery(sql) { return er.prepare(sql).all(); }
  var SQL = await initSqlJs();

  console.log('═══ DIFF-ORACLE — handlers vs the GardenWorld static oracle (§18.10, §0.12) ═══');
  console.log('    oracle = ad_full.db rows (already-executed GardenWorld txns); docker = fallback only\n');

  // env handed to each cell: data access, the engine trio, a projection factory, manifest.
  // freshDb()/closeDb(db) are paired so a caller (the gravity seed, Task 3) can swap in ONE
  // SHARED, persisted op-log: set opts.shareDb -> every cell dispatches into the same kernel_ops
  // (accumulating gravity from use) and closeDb is a no-op. Default: a fresh db per cell.
  opts = opts || {};
  var shared = opts.shareDb ? (function () { var d = new SQL.Database(); K.initProjection(d); return d; })() : null;
  var env = {
    ad: ad, er: er, all: all, ruleQuery: ruleQuery,
    E: E, R: R, K: K, manifest: MANIFEST,
    freshDb: function () { if (shared) return shared; var db = new SQL.Database(); K.initProjection(db); return db; },
    closeDb: function (db) { if (!shared) db.close(); },
    diffSet: diffSet
  };

  var ranCells = 0;
  for (var ci = 0; ci < CELLS.length; ci++) {
    var cell = CELLS[ci];
    var name = '(' + cell.docType + ',' + (cell.label ? cell.label + ':' : '') + cell.action + ')';
    console.log('── cell ' + name + '  // Oracle: ' + cell.oracle + ' ──');

    if (cell.dataless) {                       // a cell the static oracle can't ground (§0.12)
      console.log('   ⚪ ' + cell.dataless);
      console.log('§ORACLE cell=' + name + ' checks=0 fixtures=0 oracle=NO-DATA(docker-fallback) — ' + cell.dataless + '\n');
      if (opts.collect) opts.collect.push({ cell: name, docType: cell.docType, action: cell.action, label: cell.label || null, oracle: cell.oracle || cell.dataless || null, verbs: [], matcher: false, ops: 0, dataless: true });
      continue;
    }

    env.cellTag = '(' + cell.docType + ',' + (cell.label ? cell.label + ':' : '') + cell.action + ')';  // gravity stamp
    var result;
    try { result = cell.run(env); }
    catch (e) { verdict(false, name + ' run threw', e.message + '\n' + (e.stack || '')); console.log(); continue; }

    var checks = 0, fixtures = 0, allMatch = true;
    (result.groups || []).forEach(function (g) {
      var d = diffSet(g.mine, g.oracle);
      checks++; fixtures += d.oracle;
      if (!d.ok) allMatch = false;
      verdict(d.ok, name + ' ' + g.name,
        'oracle=' + d.oracle + ' mine=' + d.mine + ' matched=' + d.matched + ' missed=' + d.missed + ' extra=' + d.extra);
    });

    var verbs = result.verbs || [];
    var matcher = result.matcher ? 'Y' : 'N';
    // P3c Glassbowl: optionally collect authoritative per-cell engine facts for the explorer
    // (the engine-as-data surface, docs/GLASSBOWL.md). Backward-compatible: no-op without opts.collect.
    if (opts.collect) opts.collect.push({ cell: name, docType: cell.docType, action: cell.action, label: cell.label || null, oracle: cell.oracle || cell.dataless || null, verbs: verbs, matcher: result.matcher === true, ops: result.ops != null ? result.ops : 0, dataless: !!cell.dataless });
    // a genuinely new bespoke verb / shape is a §18.6 FINDING — logged loud, never buried.
    if (result.newVerb) console.log('   ⚠ §HANDLER new-verb cell=' + name + ' reason=' + result.newVerb);
    if (result.finding) console.log('   ℹ §HANDLER finding cell=' + name + ' — ' + result.finding);
    // P3c Task 1: per-document isolation guard — Σ(per-document matches) == the P3b universe count.
    if (result.perdoc) {
      var pd = result.perdoc;
      var allOk = pd.checks.every(function (c) { return c.ok; });
      var parts = pd.checks.map(function (c) { return c.label + ' matched=' + c.matched + ' universe=' + c.universe + (c.ok ? ' OK' : ' DROP/EXTRA'); });
      verdict(allOk, name + ' per-document isolation == universe (no cross-document leak)', parts.join(' | '));
      console.log('§VERTICAL perdoc cell=' + name + ' docs=' + pd.docs + ' | ' + parts.join(' | ') + ' isolation=' + (allOk ? 'OK' : 'LEAK'));
    }
    console.log('§HANDLER cell=' + name + ' verbs=[' + verbs.join(',') + '] matcher=' + matcher + ' ops=' + (result.ops != null ? result.ops : '?') + ' diff=' + (allMatch ? 'MATCH' : 'MISMATCH'));
    console.log('§ORACLE cell=' + name + ' checks=' + checks + ' fixtures=' + fixtures + ' diff=' + (allMatch ? 'MATCH' : 'MISMATCH') + '\n');
    ranCells++;
  }

  ad.close(); er.close();
  console.log('═══ VERDICT ═══');
  console.log('§ORACLE-SUITE cells=' + ranCells + '/' + CELLS.length + ' ' + (fails ? 'FAIL — ' + fails + ' checks red' : 'PASS — every diffed cell reproduces the GardenWorld oracle'));
  return { fails: fails, db: shared, query: function (s, p) { return shared ? K.query(shared, s, p) : []; } };
}

module.exports = { run: run };

// CLI: run the static-oracle suite and exit on the verdict.
if (require.main === module) {
  run({}).then(function (r) { process.exit(r.fails ? 1 : 0); });
}
