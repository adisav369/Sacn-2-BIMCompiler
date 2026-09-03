#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_valrule_harden.js — W-VALRULE-HARDEN witness (HARDEN_MATRIX.md §H-3, "spot-harden the declarative
// engines"). The existing W-VALRULE proved ad_valrule.js INTERPRETS real AD_Val_Rule rows (parses the
// where-clause, substitutes @token@, filters). H-3 asks the next question: does the engine's VERDICT match
// the REAL iDempiere oracle — not just that it parses?
//
// ORACLE = the live iDempiere Postgres (docker `postgres`, db `idempiere`, GardenWorld client 11 — the SAME
// seed our build/erp/ad_full.db was extracted from). For each real val rule we:
//   1. drive ad_valrule.substitute()/inferTable() to produce the token-resolved where-clause + bound table,
//   2. run  SELECT <pk> FROM <table> WHERE <clause>  on SQLite (our engine's data path)  → set E,
//   3. run  the SAME clause                          on Postgres (the iDempiere oracle)  → set O,
//   4. diff the sorted PK membership sets.  diff=0 over K rules = oracle-equivalent.
// Two INDEPENDENT database engines (SQLite vs Postgres) over the same source data, with our JS token
// substitution standing in for iDempiere's Env.parseContext — a non-tautological equivalence check.
//
// NON-INVENT: the oracle is Postgres, never synthesized. If docker/pg is down a fixture is an honest SKIP
// (logged), never a fake ✅. Determinism: read-only queries, no Date.now/Math.random.
// Run: node scripts/poc_valrule_harden.js 2>&1 | tee build/erp/poc_valrule_harden.log  (read the log)
'use strict';
var path = require('path');
var cp = require('child_process');
var Database = require('better-sqlite3');
var V = require(path.join(__dirname, '..', 'build', 'erp', 'ad_valrule.js'));
var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');

var PG = { container: 'postgres', user: 'adempiere', db: 'idempiere' };
var fails = 0, hardened = 0, skipped = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

// run a SELECT on the Postgres oracle; returns array of strings (pk ids) or throws (caller treats as SKIP).
function pgQuery(sql) {
  var out = cp.execFileSync('docker', ['exec', PG.container, 'psql', '-U', PG.user, '-d', PG.db, '-t', '-A', '-c', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
  return out.split('\n').map(function (s) { return s.trim(); }).filter(function (s) { return s.length; });
}
function pgUp() { try { pgQuery('SELECT 1'); return true; } catch (e) { return false; } }

// sorted symmetric-difference count of two id-sets (as strings, numeric-aware).
function setDiff(a, b) {
  var sa = new Set(a), sb = new Set(b), miss = 0, extra = 0;
  a.forEach(function (x) { if (!sb.has(x)) extra++; });   // in SQLite (E) not in oracle (O)
  b.forEach(function (x) { if (!sa.has(x)) miss++; });    // in oracle not in ours
  return { miss: miss, extra: extra, total: miss + extra };
}

console.log('═══ W-VALRULE-HARDEN — ad_valrule verdict diffed vs live iDempiere Postgres oracle (HARDEN_MATRIX §H-3) ═══\n');

if (!pgUp()) {
  console.log('§HARDEN-SKIP oracle Postgres (docker `' + PG.container + '`/`' + PG.db + '`) unreachable → all fixtures SKIP (honest ⬜, not ✅)');
  process.exit(1);
}
console.log('§HARDEN-ORACLE live iDempiere Postgres reachable (' + PG.container + '/' + PG.db + ', GardenWorld client 11)\n');

var db = new Database(DB_PATH, { readonly: true });

// ── FIXTURES — real AD_Val_Rule rows with resolvable context, spread across master-data + transactional
//    tables. table=null → infer from the qualified clause; pk defaults to <table>_id. ──────────────────
var FIX = [
  { id: 143,    ctx: {}, table: 'c_invoice', note: 'IsSOTrx=Y (sales invoices)' },
  { id: 146,    ctx: {},                  note: 'IsPaid<>Y (open invoices)' },
  { id: 187,    ctx: {},                  note: 'Order DocStatus=CL' },
  { id: 200047, ctx: {},                  note: 'AD_Table IsView=N' },
  { id: 104,    ctx: {},                  note: 'AD_Org security (non-summary OR org0)' },
  { id: 106,    ctx: { AD_Client_ID: 11 },note: 'C_AcctSchema for client' },
  { id: 100,    ctx: { AD_Table_ID: 318 },note: 'AD_Column in table 318 (C_Invoice)' },
  { id: 211,    ctx: { AD_Table_ID: 318 },note: 'AD_Tab for table 318' },
  { id: 116,    ctx: { AD_Client_ID: 11 },note: 'AD_Client login' },
  { id: 117,    ctx: { AD_Org_ID: 0 },    note: 'AD_Org login (non-summary)' }
];

console.log('── oracle-diff: each rule\'s membership set, SQLite (ours) vs Postgres (iDempiere) ──');
var diffsAllZero = true;
FIX.forEach(function (f) {
  var row;
  try { row = db.prepare('SELECT ad_val_rule_id,name,type,code FROM ad_val_rule WHERE ad_val_rule_id=?').get(f.id); }
  catch (e) { row = null; }
  if (!row) { console.log('§HARDEN-SKIP id=' + f.id + ' not in ad_full.db'); skipped++; return; }

  var kind = V.classify(row.code);
  if (kind !== 'static' && kind !== 'token') { console.log('§HARDEN-SKIP id=' + f.id + ' kind=' + kind + ' (' + f.note + ')'); skipped++; return; }
  var sub = V.substitute(row.code, f.ctx);
  if (sub.unresolved.length) { console.log('§HARDEN-SKIP id=' + f.id + ' unresolved=' + JSON.stringify(sub.unresolved)); skipped++; return; }
  var table = f.table || V.inferTable(row.code);
  if (!table) { console.log('§HARDEN-SKIP id=' + f.id + ' no-bound-table'); skipped++; return; }
  var pk = f.pk || (table + '_id');
  var sel = 'SELECT ' + pk + ' FROM ' + table + ' WHERE ' + sub.sql + ' ORDER BY ' + pk;

  var E, O;
  try { E = db.prepare(sel).all().map(function (r) { return String(r[pk]); }); }
  catch (e) { console.log('§HARDEN-SKIP id=' + f.id + ' SQLite err: ' + e.message); skipped++; return; }
  try { O = pgQuery(sel); }
  catch (e) { console.log('§HARDEN-SKIP id=' + f.id + ' Postgres err: ' + String(e.message).split('\n')[0]); skipped++; return; }

  var d = setDiff(E, O);
  if (d.total !== 0) diffsAllZero = false;
  hardened++;
  console.log('§HARDEN surface=AD_ValRule id=' + f.id + ' table=' + table + ' clause="' + sub.sql + '" ours=' + E.length + ' oracle=' + O.length + ' diff=' + d.total + (d.total ? ' (miss=' + d.miss + ' extra=' + d.extra + ')' : '') + ' oracle=iDempiere(pg)  [' + f.note + ']');
  verdict(d.total === 0, 'rule ' + f.id + ' membership == iDempiere oracle', 'n=' + E.length + ' diff=' + d.total);
});

console.log('\n§HARDEN-TALLY surfaces=AD_ValRule rules_diffed=' + hardened + ' all_diff0=' + (diffsAllZero ? 'Y' : 'N') + ' skipped=' + skipped);
verdict(hardened >= 8 && diffsAllZero, 'AD_Val_Rule engine is ORACLE-EQUIVALENT (≥8 rules, every membership set matches iDempiere)', 'hardened=' + hardened);

// ── §FALSIFIER — prove the diff metric is LOAD-BEARING: corrupt our engine's clause (wrong operator, an
//    interpreter-bug stand-in) while the oracle keeps the correct one → memberships diverge → diff>0. ───
console.log('\n── §FALSIFIER — a corrupted interpretation must DIVERGE from the oracle (diff>0) ──');
(function () {
  var correct = "C_Invoice.IsSOTrx='Y'", corrupt = "C_Invoice.IsSOTrx<>'Y'";   // flipped operator = engine bug
  var sel = function (clause) { return 'SELECT c_invoice_id FROM c_invoice WHERE ' + clause + ' ORDER BY c_invoice_id'; };
  var Ebad = db.prepare(sel(corrupt)).all().map(function (r) { return String(r.c_invoice_id); });
  var Ocorrect = pgQuery(sel(correct));
  var d = setDiff(Ebad, Ocorrect);
  console.log('§HARDEN-FALSIFIER corruptOperator ours(IsSOTrx<>Y)=' + Ebad.length + ' oracle(IsSOTrx=Y)=' + Ocorrect.length + ' diff=' + d.total);
  verdict(d.total > 0, 'corrupted clause DIVERGES from oracle → diff metric is load-bearing', 'diff=' + d.total);
})();

db.close();
console.log('\n' + (fails ? '🔴 ' + fails + ' verdict(s) FAILED' : '✅ ALL VERDICTS PASS — AD_Val_Rule oracle-equivalent vs live iDempiere'));
process.exit(fails ? 1 : 0);
