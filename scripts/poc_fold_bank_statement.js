// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-FOLD-BANKSTMT is ✅ DONE in ERP_COVERAGE_MATRIX.md.
// SCOPE: W-FOLD-BANKSTMT — prove build/erp/report_bank_statement.foldBankStatement reproduces the posting core
//   of org.compiere.acct.Doc_BankStatement.createFacts for C_BankStatement (AD_Table_ID=392, record 100, CO):
//   13 fact_acct rows across 2 acct schemas (101=USD, 200000=EUR), ORACLE-EQUIVALENT maxDiff=0c.
// ORACLE: live iDempiere Postgres, db=idempiere_test (fact_acct=300, C_BankStatement record 100 has 13 rows).
//   INDEPENDENT re-derivation: independent SQL aggregates DR/CR per (acct_elem, schema, side) from fact_acct,
//   then the engine's fold is diffed against it.
// TOKEN MANIFEST:
//   {Bank.Asset}    DR stmtAmt (converted) — c_bankaccount_acct.b_asset_acct keyed by c_bankaccount_id+schema
//   {Bank.InTransit}CR trxAmt  (converted) — c_bankaccount_acct.b_intransit_acct
//   {Charge.Expense}DR|CR chargeAmt — c_charge_acct.ch_expense_acct keyed by c_charge_id+schema
//   {Bank.InterestExp} DR |interestAmt| (interestAmt<0) — c_bankaccount_acct.b_interestexp_acct
//   {AcctSchema.CurrencyBalance} DR 0.01 (schema 200000 only, currency rounding) — c_acctschema_gl.currencybalancing_acct
// §FALSIFIER: bend stmtAmt of line 100 by +100 → DR {Bank.Asset} diverges; engine≠oracle (load-bearing, not cosmetic).
// NON-INVENT: every value READ from a real row. BigDecimal-exact (integer cents). §-log first — READ
//   build/erp/poc_fold_bank_statement.log before concluding (exit code ≠ evidence).
// Run: bash build/erp/run_witness.sh scripts/poc_fold_bank_statement.js
'use strict';
var path = require('path'), cp = require('child_process');
var Database = require('better-sqlite3');
var R  = require('./post_resolver');
var F  = require(path.join(__dirname, '..', 'build', 'erp', 'report_bank_statement.js'));

var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var PG = { container: 'postgres', user: 'adempiere', db: 'idempiere_test' };
var US = '|';
var fails = 0;
function ok(cond, msg, detail) { if (!cond) fails++; console.log('  ' + (cond ? '✓' : '✗ FAIL:') + ' ' + msg + (detail ? ' — ' + detail : '')); }
function psql(sql) {
  return cp.execFileSync('docker', ['exec', '-e', 'PGOPTIONS=-c search_path=adempiere', PG.container,
    'psql', '-U', PG.user, '-d', PG.db, '-t', '-A', '-F', US, '-c', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 8 * 1024 * 1024 });
}
function rows(sql, cols) {
  return psql(sql).split('\n').filter(Boolean).map(function (l) {
    var f = l.split(US), o = {}; cols.forEach(function (c, i) { o[c] = f[i]; }); return o;
  });
}

console.log('=== §BANKSTMT witness (W-FOLD-BANKSTMT) — C_BankStatement GL fold vs fact_acct(392) ===');
console.log('    Engine: report_bank_statement.foldBankStatement · tokens: post_resolver {Bank.*}/{Charge.Expense}/{AcctSchema.CurrencyBalance}');
console.log('    Oracle: live iDempiere Postgres (' + PG.container + '/' + PG.db + ') · c_bankstatement_id=100 (CO)\n');

try { psql('SELECT 1'); } catch (e) {
  console.log('§BANKSTMT-SKIP oracle Postgres unreachable → honest ⬜, not ✅');
  process.exit(1);
}
console.log('§BANKSTMT-ORACLE live iDempiere Postgres reachable (' + PG.container + '/' + PG.db + ')\n');

var db = new Database(DB_PATH, { readonly: true });

// ── deps.resolve: post_resolver seam (per schema) ──────────────────────────────────────────────────────────
var deps = {
  resolve: function (token, keyId, schemaId) {
    var r = R.resolve(db, token, Number(keyId), Number(schemaId));
    return (r.acct == null || !r.element) ? null : r.element.id;
  }
};

// ── src: pull data from SQLite ad_full.db ──────────────────────────────────────────────────────────────────
var stmt = db.prepare(
  "SELECT bs.c_bankstatement_id, bs.c_bankaccount_id, bs.statementdate, ba.c_currency_id " +
  "FROM c_bankstatement bs JOIN c_bankaccount ba ON ba.c_bankaccount_id=bs.c_bankaccount_id " +
  "WHERE bs.docstatus='CO' LIMIT 1"
).get();

if (!stmt) { console.log('§BANKSTMT-SKIP no CO bank statement in ad_full.db'); process.exit(1); }
console.log('§BANKSTMT-STMT c_bankstatement_id=' + stmt.c_bankstatement_id + ' c_bankaccount_id=' + stmt.c_bankaccount_id + ' bankCcy=' + stmt.c_currency_id);

var lines = db.prepare(
  "SELECT c_bankstatementline_id, line, trxamt, stmtamt, c_charge_id, chargeamt, interestamt, " +
  "COALESCE(isactive,'Y') AS isactive " +
  "FROM c_bankstatementline WHERE c_bankstatement_id=? ORDER BY line"
).all(stmt.c_bankstatement_id);
console.log('§BANKSTMT-LINES count=' + lines.length + ': ' + lines.map(function (l) { return 'line' + l.line + '(stmt=' + l.stmtamt + ',trx=' + l.trxamt + ',chg=' + l.chargeamt + ',int=' + l.interestamt + ')'; }).join(' · '));

var schemas = db.prepare(
  "SELECT s.c_acctschema_id, s.c_currency_id, COALESCE(c.stdprecision, 2) AS stdprecision, " +
  "COALESCE(gl.usecurrencybalancing,'N') AS usecurrencybalancing " +
  "FROM c_acctschema s " +
  "LEFT JOIN c_currency c ON c.c_currency_id=s.c_currency_id " +
  "LEFT JOIN c_acctschema_gl gl ON gl.c_acctschema_id=s.c_acctschema_id AND gl.isactive='Y' " +
  "WHERE s.isactive='Y' ORDER BY s.c_acctschema_id"
).all();
console.log('§BANKSTMT-SCHEMAS count=' + schemas.length + ': ' + schemas.map(function (s) { return s.c_acctschema_id + '(ccy=' + s.c_currency_id + ',stdPrec=' + s.stdprecision + ',currBal=' + s.usecurrencybalancing + ')'; }).join(', '));

var convRates = db.prepare("SELECT c_currency_id, c_currency_id_to, multiplyrate, c_conversiontype_id FROM c_conversion_rate WHERE isactive='Y'").all();
var acctSchemaGl = db.prepare("SELECT c_acctschema_id, usecurrencybalancing, currencybalancing_acct FROM c_acctschema_gl WHERE isactive='Y'").all();

var src = { statement: stmt, lines: lines, schemas: schemas, convRates: convRates, acctSchemaGl: acctSchemaGl };

// ── ORACLE: independent SQL aggregation from fact_acct(392) in idempiere_test ────────────────────────────────
// Groups by (c_acctschema_id, c_elementvalue, side) → cents
var oraRows = rows(
  "SELECT fa.c_acctschema_id, ev.c_elementvalue_id, " +
  "       ROUND(SUM(fa.amtacctdr)*100) AS dr_cents, ROUND(SUM(fa.amtacctcr)*100) AS cr_cents " +
  "FROM fact_acct fa " +
  "JOIN c_elementvalue ev ON ev.c_elementvalue_id=fa.account_id " +
  "WHERE fa.ad_table_id=392 AND fa.record_id=" + stmt.c_bankstatement_id +
  " GROUP BY fa.c_acctschema_id, ev.c_elementvalue_id ORDER BY fa.c_acctschema_id, ev.c_elementvalue_id",
  ['c_acctschema_id', 'c_elementvalue_id', 'dr_cents', 'cr_cents']
);
console.log('\n§BANKSTMT-ORACLE fact_acct(392) record=' + stmt.c_bankstatement_id + ' groups=' + oraRows.length);

// Build oracle agg per schema: { schemaId: { 'DR:elem': cents, 'CR:elem': cents, ... } }
var oraBySchema = {};
oraRows.forEach(function (r) {
  var s = Number(r.c_acctschema_id), e = Number(r.c_elementvalue_id);
  if (!oraBySchema[s]) oraBySchema[s] = {};
  var dr = Math.round(Number(r.dr_cents)), cr = Math.round(Number(r.cr_cents));
  if (dr) oraBySchema[s]['DR:' + e] = (oraBySchema[s]['DR:' + e] || 0) + dr;
  if (cr) oraBySchema[s]['CR:' + e] = (oraBySchema[s]['CR:' + e] || 0) + cr;
});

// Also get total oracle fact count for sanity
var oraCount = +psql("SELECT COUNT(*) FROM fact_acct WHERE ad_table_id=392 AND record_id=" + stmt.c_bankstatement_id).trim();
console.log('§BANKSTMT-ORACLE total fact_acct rows=' + oraCount + ' across ' + Object.keys(oraBySchema).length + ' schemas\n');

ok(oraCount > 0, 'oracle has real fact_acct rows to diff against', 'count=' + oraCount);

// ── ENGINE fold ─────────────────────────────────────────────────────────────────────────────────────────────
var fold = F.foldBankStatement(src, deps, {});
console.log('§BANKSTMT-ENGINE absent=' + fold.absent.length + ' schemas=' + Object.keys(fold.bySchema).length);
ok(fold.absent.length === 0, 'all posting accounts RESOLVED (none invented/absent)', 'absent=' + JSON.stringify(fold.absent));

// ── per-schema DIFF ─────────────────────────────────────────────────────────────────────────────────────────
function maxDiff(eng, ora) {
  var ks = {}, m = 0;
  Object.keys(eng).forEach(function (k) { ks[k] = 1; }); Object.keys(ora).forEach(function (k) { ks[k] = 1; });
  Object.keys(ks).forEach(function (k) { m = Math.max(m, Math.abs((eng[k] || 0) - (ora[k] || 0))); }); return m;
}

var grandMd = 0, grandRows = 0;
Object.keys(fold.bySchema).forEach(function (sid) {
  var sch = fold.bySchema[sid];
  var engAgg = sch.agg, oraAgg = oraBySchema[Number(sid)] || {};
  var md = maxDiff(engAgg, oraAgg);
  grandMd = Math.max(grandMd, md);
  var oraRowCount = oraRows.filter(function (r) { return Number(r.c_acctschema_id) === Number(sid); }).length;
  grandRows += oraRowCount;
  console.log('§BANKSTMT-SCHEMA sid=' + sid + ' engineKeys=' + Object.keys(engAgg).length +
    ' oracleGroups=' + oraRowCount + ' engineΣDR=' + (sch.sumDR / 100).toFixed(2) +
    ' ΣCR=' + (sch.sumCR / 100).toFixed(2) + ' maxDiff=' + md + 'c');
  ok(oraRowCount > 0, 'schema ' + sid + ': oracle has postings to diff against');
  ok(md === 0, 'schema ' + sid + ': engine == oracle EXACT (maxDiff=0c) — ORACLE-EQUIVALENT', 'maxDiff=' + md + 'c');
  ok(sch.sumDR === sch.sumCR, 'schema ' + sid + ': balanced (ΣDR=ΣCR)', (sch.sumDR / 100).toFixed(2) + '=' + (sch.sumCR / 100).toFixed(2));
});

console.log('\n§BANKSTMT-TOTAL schemas=' + Object.keys(fold.bySchema).length + ' oracleRows=' + grandRows + ' grandMaxDiff=' + grandMd + 'c');
ok(grandMd === 0, 'ALL schemas: engine == fact_acct(392) EXACT — ORACLE-EQUIVALENT', 'maxDiff=' + grandMd + 'c over ' + grandRows + ' oracle groups');

// ── §FALSIFIER: bend stmtAmt of line 100 by +100 → DR diverges ─────────────────────────────────────────────
console.log('\n§BANKSTMT-FALSIFIER: bend stmtAmt line 100 by +100 (override first line stmtamt to 198)');
var bentLines = lines.map(function (l) {
  return l.c_bankstatementline_id === lines[0].c_bankstatementline_id
    ? Object.assign({}, l, { stmtamt: Number(l.stmtamt) + 100 })
    : l;
});
var bentSrc = Object.assign({}, src, { lines: bentLines });
var bentFold = F.foldBankStatement(bentSrc, deps, {});
// Check schema 101 first — find the BankAsset account
var schIds = Object.keys(fold.bySchema);
var bentMd = 0;
schIds.forEach(function (sid) {
  var ora = oraBySchema[Number(sid)] || {};
  var eng = bentFold.bySchema[sid] ? bentFold.bySchema[sid].agg : {};
  bentMd = Math.max(bentMd, maxDiff(eng, ora));
});
console.log('§BANKSTMT-FALSIFIER bentStmtAmt: engine vs oracle maxDiff=' + bentMd + 'c (must be > 0)');
ok(bentMd > 0, '§FALSIFIER fires: stmtAmt +100 → DR {Bank.Asset} diverges from oracle (engine is load-bearing, not tautological)', 'maxDiff=' + bentMd + 'c');

console.log('\n' + (fails === 0
  ? '🟢 W-FOLD-BANKSTMT PASS — Doc_BankStatement GL folded oracle-equivalent across ' + Object.keys(fold.bySchema).length + ' schemas (' + grandRows + ' oracle groups, maxDiff=0c); §FALSIFIER fires (stmtAmt +100 → maxDiff=' + bentMd + 'c > 0)'
  : '🔴 W-FOLD-BANKSTMT FAIL — ' + fails + ' assertion(s) failed'));
process.exit(fails > 0 ? 2 : 0);
