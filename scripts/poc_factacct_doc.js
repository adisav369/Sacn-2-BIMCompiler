#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_factacct_doc.js — W-FACTACCT-DOC (FABLE5_MORDER_EQUIVALENCE.md §H-1.1).
//
// SPEC (§H-1.1): the posting oracle captured into build/erp/glassbowl_data.db (fact_acct, client 11) must
//   carry PER-DOCUMENT granularity — ad_table_id / record_id / line_id on EVERY row — and must EQUAL the
//   live source (Docker Postgres `idempiere_test`, the same instance extract_fact_acct.sh reads) ROW BY ROW,
//   keyed by fact_acct_id, on (ad_table_id, record_id, line_id, account_id, c_acctschema_id, DR, CR) in
//   INTEGER CENTS. Row-level equality subsumes the per-document sums; the per-doc rollup is logged so H-1.2
//   can cite its fixture universe. The row count + ΣDR=ΣCR must reconcile to the existing AGGREGATE anchor
//   (300 rows, 46574.97 — test_report_fin.js), proving the granular capture is the SAME journal, not a fork.
//
// ORACLE = the live Postgres; the sqlite capture is what every downstream H-1 witness reads. If docker/pg is
//   unreachable this witness exits 1 with §BLOCKED (honest ⛔, never a synthesized oracle).
// NON-INVENT: both sides are real GardenWorld data; nothing derived here, pure capture-fidelity diff.
// Deterministic: recorded ids, integer cents, no Date.now/Math.random.
// Implementing FABLE5_MORDER_EQUIVALENCE.md §H-1.1 — Witness: W-FACTACCT-DOC
// Run: bash build/erp/run_witness.sh scripts/poc_factacct_doc.js   (log: build/erp/poc_factacct_doc.log)
'use strict';
var path = require('path');
var cp = require('child_process');
var Database = require('better-sqlite3');

var PG = { container: 'postgres', user: 'adempiere', db: 'idempiere_test' };
var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function cents(n) { return Math.round(Number(n || 0) * 100); }

function pg(sql) {
  var out = cp.execFileSync('docker', ['exec', PG.container, 'psql', '-U', PG.user, '-d', PG.db, '-t', '-A', '-c', sql],
    { encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] });
  return out.split('\n').filter(function (l) { return l.trim().length; });
}

console.log('═══ W-FACTACCT-DOC — per-document fact_acct capture == live iDempiere oracle, row by row ═══');
console.log('    capture = glassbowl_data.db fact_acct · oracle = docker ' + PG.container + '/' + PG.db + ' (client 11)\n');

try { pg('SELECT 1'); }
catch (e) {
  console.log('§BLOCKED oracle Postgres (docker `' + PG.container + '`/`' + PG.db + '`) unreachable — bring up the idempiere_test docker instance. No oracle, no verdict.');
  process.exit(1);
}

// ── row canonicalisation: fact_acct_id -> "table/record/line/account/schema/DRc/CRc" ────────────────────
function rowKey(r) {
  return r.t + '/' + r.rec + '/' + r.line + '/' + r.acct + '/' + r.sch + '/' + r.dr + '/' + r.cr;
}
// the capture (line_id imported as '' where the oracle has NULL — normalise both sides to 0)
var mine = {};
db.prepare(
  "SELECT fact_acct_id id, ad_table_id t, record_id rec, " +
  "CASE WHEN line_id IS NULL OR line_id='' THEN 0 ELSE CAST(line_id AS INT) END line, " +
  "account_id acct, c_acctschema_id sch, amtacctdr dr, amtacctcr cr FROM fact_acct"
).all().forEach(function (r) { mine[r.id] = rowKey({ t: r.t, rec: r.rec, line: r.line, acct: r.acct, sch: r.sch, dr: cents(r.dr), cr: cents(r.cr) }); });

// the live oracle, same canonical shape
var theirs = {};
pg("SELECT fact_acct_id, ad_table_id, record_id, coalesce(line_id,0), account_id, c_acctschema_id, " +
   "round(amtacctdr,2), round(amtacctcr,2) FROM adempiere.fact_acct WHERE ad_client_id=11 ORDER BY fact_acct_id"
).forEach(function (l) {
  var f = l.split('|');
  theirs[Number(f[0])] = rowKey({ t: Number(f[1]), rec: Number(f[2]), line: Number(f[3]), acct: Number(f[4]), sch: Number(f[5]), dr: cents(f[6]), cr: cents(f[7]) });
});

function diffMaps(a, b) {
  var bad = [], ids = {};
  Object.keys(a).forEach(function (k) { ids[k] = 1; }); Object.keys(b).forEach(function (k) { ids[k] = 1; });
  Object.keys(ids).forEach(function (id) { if (a[id] !== b[id]) bad.push(id + ': capture=' + (a[id] || 'MISSING') + ' oracle=' + (b[id] || 'MISSING')); });
  return bad;
}
var bad = diffMaps(mine, theirs);
var nMine = Object.keys(mine).length, nTheirs = Object.keys(theirs).length;
verdict(bad.length === 0 && nMine === nTheirs && nMine > 0,
  'every captured row == live oracle row (table/record/line/account/schema/DR/CR, cents)',
  'capture=' + nMine + ' oracle=' + nTheirs + ' rowDiffs=' + bad.length);
bad.slice(0, 5).forEach(function (b2) { console.log('      ✗ ' + b2); });

// ── granularity: ad_table_id + record_id on EVERY row; line_id where the oracle has it ──────────────────
var g = db.prepare(
  "SELECT count(*) n, sum(ad_table_id IS NOT NULL AND ad_table_id!='') ht, " +
  "sum(record_id IS NOT NULL AND record_id!='') hr, " +
  "sum(line_id IS NOT NULL AND line_id!='' AND line_id!=0) hl FROM fact_acct"
).get();
var oLine = Number(pg('SELECT count(*) FROM adempiere.fact_acct WHERE ad_client_id=11 AND line_id IS NOT NULL')[0]);
verdict(g.ht === g.n && g.hr === g.n, 'ad_table_id + record_id populated on every row', g.ht + '/' + g.n + ' + ' + g.hr + '/' + g.n);
verdict(g.hl === oLine, 'line_id populated exactly where the oracle has one', 'capture=' + g.hl + ' oracle=' + oLine);

// ── reconcile to the AGGREGATE anchor (the pre-H-1 capture contract: 300 rows, Dr=Cr=46574.97) ──────────
var agg = db.prepare('SELECT count(*) n, round(sum(amtacctdr),2) dr, round(sum(amtacctcr),2) cr FROM fact_acct').get();
verdict(agg.n === 300 && cents(agg.dr) === cents(agg.cr) && cents(agg.dr) === 4657497,
  'granular capture reconciles to the aggregate anchor (same journal, not a fork)',
  'rows=' + agg.n + ' ΣDR=' + agg.dr + ' ΣCR=' + agg.cr);

// ── the per-document fixture universe H-1.2+ diffs against ──────────────────────────────────────────────
var docs = db.prepare('SELECT ad_table_id t, count(DISTINCT record_id) docs, count(*) rows FROM fact_acct GROUP BY ad_table_id ORDER BY ad_table_id').all();
var totalDocs = 0;
docs.forEach(function (d) { totalDocs += d.docs; console.log('§HARDEN_DOCS ad_table_id=' + d.t + ' docs=' + d.docs + ' rows=' + d.rows); });

// ── §FALSIFIER: drop one captured row → the row-diff MUST fire (the diff is load-bearing) ───────────────
var probe = Object.keys(mine)[0];
var mutated = Object.assign({}, mine); delete mutated[probe];
var fbad = diffMaps(mutated, theirs);
verdict(fbad.length > 0, '§FALSIFIER drop captured row ' + probe + ' → rowDiffs≠0', 'rowDiffs=' + fbad.length);
console.log('§FALSIFIER drop=fact_acct_id:' + probe + ' rowDiffs=' + fbad.length + ' (must be >0)');

console.log('\n§HARDEN capture=fact_acct_doc rows=' + nMine + ' docs=' + totalDocs + ' has_record_id=Y has_table_id=Y has_line_id=' + g.hl +
  ' rowDiff=' + bad.length + ' oracle=iDempiere(live-pg)');
console.log((fails === 0 ? '🟢 W-FACTACCT-DOC PASS' : '🔴 W-FACTACCT-DOC FAIL (' + fails + ')') +
  ' — the per-document posting oracle is faithful to the live instance, row by row; H-1.2 may diff against it.');
db.close();
process.exit(fails === 0 ? 0 : 1);
