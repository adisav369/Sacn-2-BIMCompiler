#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_logic_harden.js — W-LOGIC-HARDEN witness.
// Implementing ERP_EXECUTION_ROADMAP.md §PHASE B B-1 + HARDEN_MATRIX.md §H-3 (the parked ad_evaluator row)
//   — Witness: W-LOGIC-HARDEN
//
// ISSUE THIS TEST PROVES/DISPROVES: ad_evaluator.js claims to be a faithful port of iDempiere's
// logic-expression evaluator (SimpleBoolean.g4 + EvaluationVisitor) — the thing GridField uses for
// displayed/readonly/mandatory verdicts (GridField.java:1314 displayed = evaluateLogic(DisplayLogic);
// :407 editable = !evaluateLogic(ReadOnlyLogic); :362 mandatory = evaluateLogic(MandatoryLogic)).
// Until now that claim was parse-level only (🟡). This witness ORACLE-DIFFS the verdicts:
//
//   ENGINE side: ad_evaluator.evaluate(expr, ctx) over expressions from OUR build/erp/ad_full.db.
//   ORACLE side: the REAL compiled iDempiere classes (ANTLR SimpleBooleanParser + EvaluationVisitor from
//     ~/idempiere-dev-setup/idempiere/org.adempiere.base/target/classes, driven by scripts/logic_oracle/
//     LogicOracle.java — LogicEvaluator.evaluateLogic minus only its CLogger static) over the SAME
//     (expr, ctx) pairs.
//   CONTEXT: captured from REAL records in the live iDempiere Postgres (docker `postgres`/`idempiere`,
//     GardenWorld — the instance ad_full.db was extracted from). For each logic expression whose @vars@
//     are ALL columns of its own table, the dep-column values of up to 5 real records (first 3 + last 2 by
//     pk, deterministic) are pulled via psql — NEVER hand-authored.
//
// HONEST SKIPS (named, counted, never synthesized):
//   - @SQL= virtual-UI logic → Evaluator.parseSQLLogic, a different surface (GridField.java:1310).
//   - Expressions whose @vars@ include window/login context (#/$/tab-prefix) or parent-tab columns —
//     no record-grounded context exists for them in a headless capture.
//   - Tables without a single <table>_id pk or with zero rows in the live PG.
//
// §FALSIFIER (load-bearing): flip ONE captured context value on a verdict-sensitive fixture → the verdict
// must flip on BOTH sides (engine(flipped)==oracle(flipped) != original) — proving the diff would catch a
// wrong verdict, i.e. diff=0 is not vacuous.
//
// NON-INVENT: expressions = ad_full.db (cross-checked md5-set == live PG); context = live PG records;
// oracle = real compiled iDempiere classes. pg/javac down ⇒ honest SKIP exit 1. Read-only, deterministic.
// Run: bash build/erp/run_witness.sh scripts/poc_logic_harden.js   (then READ build/erp/poc_logic_harden.log)
'use strict';
var path = require('path');
var fs = require('fs');
var cp = require('child_process');
var crypto = require('crypto');
var Database = require('better-sqlite3');
var E = require(path.join(__dirname, '..', 'build', 'erp', 'ad_evaluator.js'));
var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');

var PG = { container: 'postgres', user: 'adempiere', db: 'idempiere' };
var IDEMPIERE = path.join(process.env.HOME || '/home/red1', 'idempiere-dev-setup', 'idempiere');
var ORACLE_CP = [
  path.join(IDEMPIERE, 'org.adempiere.base', 'target', 'classes'),
  path.join(IDEMPIERE, 'org.idempiere.p2', 'target', 'repository', 'plugins', '*')
].join(':');
var ORACLE_SRC = path.join(__dirname, 'logic_oracle', 'LogicOracle.java');
var ORACLE_OUT = '/tmp/logic_oracle_build';

var US = '\x1f', RS = '\x1e';
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function b64(s) { return Buffer.from(String(s), 'utf8').toString('base64'); }
function pgRaw(sql) {
  return cp.execFileSync('docker', ['exec', PG.container, 'psql', '-U', PG.user, '-d', PG.db, '-t', '-A', '-F', US, '-c', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 64 * 1024 * 1024 });
}
function pgLines(sql) { return pgRaw(sql).split('\n').filter(function (s) { return s.length; }); }

console.log('═══ W-LOGIC-HARDEN — ad_evaluator diffed vs the REAL iDempiere evaluator over live-PG record context (§H-3/B-1) ═══\n');

// ── 0. entrance: PG up + oracle compiles ────────────────────────────────────────────────────────────────
try { pgLines('SELECT 1'); } catch (e) { console.log('§HARDEN-SKIP oracle Postgres unreachable → honest ⬜, not ✅'); process.exit(1); }
console.log('§HARDEN-ORACLE live iDempiere Postgres reachable (' + PG.container + '/' + PG.db + ') + compiled evaluator classes ' + path.join(IDEMPIERE, 'org.adempiere.base/target/classes'));
try {
  fs.mkdirSync(ORACLE_OUT, { recursive: true });
  cp.execFileSync('javac', ['-cp', ORACLE_CP, '-d', ORACLE_OUT, ORACLE_SRC], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
} catch (e) { console.log('§HARDEN-SKIP javac failed (' + String(e.stderr || e.message).slice(0, 200) + ') → honest ⬜'); process.exit(1); }
console.log('§HARDEN-ORACLE-BUILD LogicOracle.java compiled against real SimpleBooleanParser+EvaluationVisitor (ANTLR ' + 'org.antlr.antlr4-runtime_4.9.2' + ')\n');

var db = new Database(DB_PATH, { readonly: true });

// ── 1. expression source cross-check: our ad_full.db expr sets == live PG (md5 set diff) ────────────────
console.log('── source cross-check: the expressions under test exist IDENTICALLY in ad_full.db and the live PG ──');
var KINDS = [
  { kind: 'display',   ours: "SELECT DISTINCT f.displaylogic e FROM ad_field f WHERE f.displaylogic IS NOT NULL AND f.isactive='Y'",
    pg: "SELECT DISTINCT md5(displaylogic) FROM ad_field WHERE displaylogic IS NOT NULL AND isactive='Y'" },
  { kind: 'readonly',  ours: "SELECT DISTINCT c.readonlylogic e FROM ad_column c WHERE c.readonlylogic IS NOT NULL AND c.isactive='Y'",
    pg: "SELECT DISTINCT md5(readonlylogic) FROM ad_column WHERE readonlylogic IS NOT NULL AND isactive='Y'" },
  { kind: 'mandatory', ours: "SELECT DISTINCT c.mandatorylogic e FROM ad_column c WHERE c.mandatorylogic IS NOT NULL AND c.isactive='Y'",
    pg: "SELECT DISTINCT md5(mandatorylogic) FROM ad_column WHERE mandatorylogic IS NOT NULL AND isactive='Y'" }
];
KINDS.forEach(function (k) {
  var ourMd5 = new Set(db.prepare(k.ours).all().map(function (r) { return crypto.createHash('md5').update(String(r.e), 'utf8').digest('hex'); }));
  var pgMd5 = new Set(pgLines(k.pg));
  var miss = 0; ourMd5.forEach(function (h) { if (!pgMd5.has(h)) miss++; });
  var extra = 0; pgMd5.forEach(function (h) { if (!ourMd5.has(h)) extra++; });
  console.log('§HARDEN-SRC kind=' + k.kind + ' ours=' + ourMd5.size + ' pg=' + pgMd5.size + ' setdiff=' + (miss + extra));
  verdict(miss + extra === 0, k.kind + '-logic distinct expression set: ad_full.db == live PG', 'n=' + ourMd5.size);
});

// ── 2. fixture capture: (expr, real-record context) pairs ───────────────────────────────────────────────
console.log('\n── fixture capture: K real (record, field-logic) pairs — context from live-PG records, never authored ──');
var tableCols = {};   // tablename(lower) -> Set(lower colnames)
db.prepare('SELECT t.tablename tn, c.columnname cn FROM ad_column c JOIN ad_table t ON c.ad_table_id=t.ad_table_id').all()
  .forEach(function (r) {
    var tn = String(r.tn).toLowerCase();
    (tableCols[tn] = tableCols[tn] || new Set()).add(String(r.cn).toLowerCase());
  });

var candidates = [];
db.prepare(
  "SELECT 'display' kind, f.ad_field_id id, f.displaylogic expr, t.tablename tn FROM ad_field f " +
  "JOIN ad_tab tb ON f.ad_tab_id=tb.ad_tab_id JOIN ad_table t ON tb.ad_table_id=t.ad_table_id " +
  "WHERE f.displaylogic IS NOT NULL AND f.isactive='Y' " +
  "UNION ALL " +
  "SELECT 'readonly', c.ad_column_id, c.readonlylogic, t.tablename FROM ad_column c " +
  "JOIN ad_table t ON c.ad_table_id=t.ad_table_id WHERE c.readonlylogic IS NOT NULL AND c.isactive='Y' " +
  "UNION ALL " +
  "SELECT 'mandatory', c.ad_column_id, c.mandatorylogic, t.tablename FROM ad_column c " +
  "JOIN ad_table t ON c.ad_table_id=t.ad_table_id WHERE c.mandatorylogic IS NOT NULL AND c.isactive='Y'"
).all().forEach(function (r) { candidates.push({ kind: r.kind, srcId: r.id, expr: String(r.expr), table: String(r.tn).toLowerCase() }); });

var skips = { sql: 0, parsefail: 0, contextVar: 0, noPk: 0, noRows: 0, rowGuard: 0 };
var seen = new Set();       // dedupe (table, expr, kind) — same expr on same table = same fixture space
var eligible = [];
candidates.forEach(function (c) {
  var key = c.kind + '|' + c.table + '|' + c.expr;
  if (seen.has(key)) return;
  seen.add(key);
  var tp = E.tryParse(c.expr);
  if (tp.sql) { skips.sql++; return; }                                    // parseSQLLogic surface — not this witness
  if (!tp.ok) { skips.parsefail++; console.log('   ⚠ FINDING parse-fail kind=' + c.kind + ' src=' + c.srcId + ' expr=' + JSON.stringify(c.expr) + ' err=' + tp.error); fails++; return; }
  var rawVars = [], m, re = /@([^@]+)@/g;
  while ((m = re.exec(c.expr)) !== null) rawVars.push(m[1]);
  var cols = tableCols[c.table] || new Set();
  var grounded = rawVars.length > 0 && rawVars.every(function (v) { return /^[A-Za-z0-9_]+$/.test(v) && cols.has(v.toLowerCase()); });
  if (!grounded) { skips.contextVar++; return; }                          // window/login/parent context — no honest record ground
  if (!cols.has(c.table + '_id')) { skips.noPk++; return; }
  c.vars = rawVars.filter(function (v, i, a) { return a.indexOf(v) === i; });
  eligible.push(c);
});

// group eligible by table → ONE psql per table (union of dep columns, first 3 + last 2 records by pk)
var byTable = {};
eligible.forEach(function (c) { (byTable[c.table] = byTable[c.table] || []).push(c); });
var fixtures = [], fxId = 0;
Object.keys(byTable).sort().forEach(function (tn) {
  var exprs = byTable[tn];
  var varSet = [];
  exprs.forEach(function (c) { c.vars.forEach(function (v) { if (varSet.indexOf(v) === -1) varSet.push(v); }); });
  var pk = tn + '_id';
  var sel = pk + '::text||\'' + US + '\'||' + varSet.map(function (v) { return "COALESCE(" + v.toLowerCase() + "::text,'')"; }).join('||\'' + US + '\'||');
  var rows;
  try {
    rows = pgLines('(SELECT ' + sel + ' FROM ' + tn + ' ORDER BY ' + pk + ' LIMIT 3) UNION ALL (SELECT ' + sel + ' FROM ' + tn + ' ORDER BY ' + pk + ' DESC LIMIT 2)');
  } catch (e) { skips.noRows += exprs.length; return; }                   // table absent/empty in live PG
  if (!rows.length) { skips.noRows += exprs.length; return; }
  var records = [];
  rows.forEach(function (line) {
    var f = line.split(US);
    if (f.length !== varSet.length + 1) { skips.rowGuard++; return; }     // embedded separator — drop row, honest count
    var ctx = {};
    varSet.forEach(function (v, i) { ctx[v] = f[i + 1]; });
    records.push({ recordId: f[0], ctx: ctx });
  });
  exprs.forEach(function (c) {
    records.forEach(function (rec) {
      var ctx = {};
      c.vars.forEach(function (v) { ctx[v] = rec.ctx[v]; });
      fixtures.push({ id: 'fx' + (fxId++), kind: c.kind, table: tn, recordId: rec.recordId, expr: c.expr, ctx: ctx });
    });
  });
});
console.log('§HARDEN-CAPTURE candidates=' + candidates.length + ' distinct=' + seen.size + ' eligible_exprs=' + eligible.length +
  ' tables=' + Object.keys(byTable).length + ' fixtures=' + fixtures.length +
  ' skips{sql=' + skips.sql + ',context-var=' + skips.contextVar + ',no-pk=' + skips.noPk + ',no-rows=' + skips.noRows + ',row-guard=' + skips.rowGuard + ',parse-fail=' + skips.parsefail + '}');
verdict(fixtures.length >= 100, 'captured a real fixture population (record-grounded, both kinds of source table)', 'K=' + fixtures.length);

// ── 3. oracle run (real iDempiere classes) + engine run + diff ──────────────────────────────────────────
console.log('\n── diff: ad_evaluator.evaluate vs real SimpleBooleanParser+EvaluationVisitor over the SAME (expr,ctx) ──');
function runOracle(fxs) {
  var input = fxs.map(function (f) {
    var blob = Object.keys(f.ctx).map(function (k) { return k + US + f.ctx[k]; }).join(RS);
    return f.id + '\t' + b64(f.expr) + '\t' + b64(blob);
  }).join('\n') + '\n';
  var out = cp.execFileSync('java', ['-cp', ORACLE_CP + ':' + ORACLE_OUT, 'LogicOracle'],
    { input: input, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'], maxBuffer: 64 * 1024 * 1024 });
  var map = {};
  out.split('\n').filter(function (s) { return s.length; }).forEach(function (l) {
    var p = l.split('\t'); map[p[0]] = p[1];
  });
  return map;
}
var oracleVerdicts = runOracle(fixtures);
var diff = 0, oracleErr = 0, counts = { T: 0, F: 0 };
var perKind = { display: { n: 0, d: 0 }, readonly: { n: 0, d: 0 }, mandatory: { n: 0, d: 0 } };
fixtures.forEach(function (f) {
  var o = oracleVerdicts[f.id];
  if (o == null || o.charAt(0) === 'E') {
    oracleErr++; fails++;
    console.log('   ⚠ FINDING oracle-error ' + f.id + ' kind=' + f.kind + ' table=' + f.table + ' expr=' + JSON.stringify(f.expr) + ' → ' + o);
    return;
  }
  var ours = E.evaluate(f.expr, f.ctx, {}) ? 'T' : 'F';
  counts[o]++;
  perKind[f.kind].n++;
  if (ours !== o) {
    diff++; perKind[f.kind].d++;
    console.log('   ⚠ FINDING verdict-mismatch ' + f.id + ' kind=' + f.kind + ' table=' + f.table + ' rec=' + f.recordId +
      ' engine=' + ours + ' oracle=' + o + ' expr=' + JSON.stringify(f.expr) + ' ctx=' + JSON.stringify(f.ctx));
  }
});
['display', 'readonly', 'mandatory'].forEach(function (k) {
  console.log('§HARDEN surface=ad_evaluator kind=' + k + ' fixtures=' + perKind[k].n + ' diff=' + perKind[k].d + ' oracle=iDempiere-PG');
});
console.log('§HARDEN surface=ad_evaluator fixtures=' + fixtures.length + ' diff=' + diff + ' oracle=iDempiere-PG verdicts{T=' + counts.T + ',F=' + counts.F + '} oracle_errors=' + oracleErr);
verdict(diff === 0 && oracleErr === 0, 'ad_evaluator verdicts == real iDempiere evaluator over every record-grounded fixture', 'K=' + fixtures.length + ' diff=' + diff);
verdict(counts.T > 0 && counts.F > 0, 'verdict population is two-sided (both true and false observed — diff is not vacuous on one constant)', 'T=' + counts.T + ' F=' + counts.F);

// ── 4. §FALSIFIER — flip ONE captured context value → verdict must flip on BOTH sides ───────────────────
console.log('\n── §FALSIFIER — a flipped context value must FLIP the verdict (engine AND oracle) ──');
(function () {
  var probe = null, flipVar = null, flipped = null;
  for (var i = 0; i < fixtures.length && !probe; i++) {
    var f = fixtures[i];
    var base = E.evaluate(f.expr, f.ctx, {}) ? 'T' : 'F';
    if (oracleVerdicts[f.id] !== base) continue;
    var vars = Object.keys(f.ctx);
    for (var j = 0; j < vars.length; j++) {
      var v = vars[j], old = f.ctx[v];
      var alt = old === 'Y' ? 'N' : old === 'N' ? 'Y' : (old + '_FLIP');
      var ctx2 = {}; Object.keys(f.ctx).forEach(function (k) { ctx2[k] = f.ctx[k]; });
      ctx2[v] = alt;
      var v2 = E.evaluate(f.expr, ctx2, {}) ? 'T' : 'F';
      if (v2 !== base) { probe = f; flipVar = v; flipped = { ctx: ctx2, engine: v2, base: base }; break; }
    }
  }
  if (!probe) { verdict(false, 'no verdict-sensitive fixture found for falsifier'); return; }
  var oFlip = runOracle([{ id: 'flip', expr: probe.expr, ctx: flipped.ctx }])['flip'];
  console.log('§HARDEN-FALSIFIER fixture=' + probe.id + ' table=' + probe.table + ' rec=' + probe.recordId + ' var=' + flipVar +
    ' expr=' + JSON.stringify(probe.expr) + ' base=' + flipped.base + ' flipped engine=' + flipped.engine + ' oracle=' + oFlip);
  verdict(oFlip === flipped.engine && oFlip !== flipped.base,
    'flipped context FLIPS the verdict on BOTH sides (engine==oracle after flip, != before) → diff metric is load-bearing',
    'base=' + flipped.base + ' flipped=' + flipped.engine + '/' + oFlip);
})();

db.close();
console.log('\n' + (fails ? '🔴 ' + fails + ' verdict(s)/finding(s) FAILED' : '✅ ALL VERDICTS PASS — ad_evaluator ORACLE-EQUIVALENT vs real iDempiere evaluator classes over live-PG record context'));
process.exit(fails ? 1 : 0);
