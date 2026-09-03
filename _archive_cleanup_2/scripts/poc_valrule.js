#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_valrule.js — W-VALRULE witness. Opens canonical build/erp/ad_full.db (lowercase tables) and drives
// REAL ad_val_rule rows through build/erp/ad_valrule.js: static where-clauses applied as filters, a
// @token@ rule substituted from context, the explicit DEFER paths (unresolved token / SQL-Q AD_Rule /
// comment-subselect), and a §FALSIFIER membership test (a paid invoice is EXCLUDED by the NotPaid filter).
// Implementing ERP_COVERAGE_MATRIX.md §AD_Val_Rule (ranked GAP #7) — Witness: W-VALRULE
// Run: node scripts/poc_valrule.js 2>&1 | tee build/erp/poc_valrule.log   (read the log; exit code != evidence)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var V = require(path.join(__dirname, '..', 'build', 'erp', 'ad_valrule.js'));
var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

console.log('═══ W-VALRULE — AD_Val_Rule SQL-where interpreter (real ad_full.db rows → filter/membership) ═══\n');
var db = new Database(DB_PATH, { readonly: true });

// ── coverage: classify all 332 Type-S rules (what is directly applicable vs token vs deferred) ──────────
var cov = V.coverageScan(db);
console.log('§VALRULE_COVERAGE total=' + cov.total + ' static=' + cov.static + ' token=' + cov.token +
  ' sql-token=' + cov['sql-token'] + ' unsafe=' + cov.unsafe + ' empty=' + cov.empty +
  '  (static+token = directly interpretable headless)');
var adrule = db.prepare("SELECT COUNT(*) AS n FROM ad_rule").get().n;        // the AD_Rule sibling surface
var adruleQ = db.prepare("SELECT COUNT(*) AS n FROM ad_rule WHERE ruletype='Q'").get().n;
console.log('   AD_Rule = ' + adrule + ' (ruletype Q = SQL: ' + adruleQ + ' Fact_Reconciliation rules, NOT Groovy) → n/a-in-seed (fact_acct/fact_reconciliation empty + Postgres SQL)');
verdict(cov.static + cov.token + cov['sql-token'] + cov.unsafe + cov.empty === cov.total,
  'every Type-S rule classified, none silently dropped', 'sum=' + cov.total);
verdict(cov.static + cov.token > 0, 'a real interpretable population exists (static+token)', 'interpretable=' + (cov.static + cov.token));

// ── STATIC clauses applied as real filters (the MValRule semantics) — assert against ground-truth counts ─
console.log('\n── static where-clauses applied as filters (bound table → rows that pass) ──');
function showStatic(id, table, expectRows) {
  var r = V.evalValRule(db, id, { table: table });
  console.log('§VALRULE id=' + id + ' "' + r.name + '" sql="' + r.sql + '" table=' + r.table + ' rows=' + r.rows + ' filtered=' + (r.rows < countAll(r.table)));
  verdict(r.ok && r.rows === expectRows, 'rule ' + id + ' (' + r.name + ') filters to ' + expectRows + ' rows', 'got=' + r.rows);
  return r;
}
function countAll(t) { return db.prepare('SELECT COUNT(*) AS n FROM ' + t).get().n; }
showStatic(143, 'c_invoice', 4);        // IsSOTrx='Y'        → 4 of 8 invoices
showStatic(146, null,        6);        // C_Invoice.IsPaid<>'Y' (table inferred) → 6 notpaid
showStatic(187, null,        1);        // C_Order.DocStatus='CL' (inferred)      → 1 closed
showStatic(200047, null,     914);      // AD_Table.IsView='N' (inferred)         → 914 non-view tables

// ── @token@ substitution: resolve from context, THEN apply (the 216/332 token rules) ────────────────────
console.log('\n── @token@ rule resolved from context then applied ──');
var t = V.evalValRule(db, 211, { ctx: { AD_Table_ID: 318 } });             // AD_Tab.AD_Table_ID=@AD_Table_ID@
console.log('§VALRULE id=211 "' + t.name + '" sql="' + t.sql + '" table=' + t.table + ' rows=' + t.rows + ' token-resolved=Y');
verdict(t.ok && t.sql.indexOf('@') < 0 && t.rows === 6, 'token rule 211 substitutes @AD_Table_ID@→318 and filters to 6 tabs', 'sql=' + t.sql + ' rows=' + t.rows);

// the explicit DEFER paths (never a silent pass)
var d1 = V.evalValRule(db, 211, { ctx: {} });                              // no ctx → unresolved
verdict(!d1.ok && d1.deferred === 'unresolved-tokens' && d1.unresolved.indexOf('AD_Table_ID') >= 0,
  'unresolved token → explicit DEFER (not silent pass)', 'deferred=' + d1.deferred + ' unresolved=' + JSON.stringify(d1.unresolved));
var d2 = V.evalValRule(db, 52056, { ctx: {} });                            // comment + subselect → unsafe defer
verdict(!d2.ok && d2.deferred, 'comment/subselect rule 52056 → explicit DEFER', 'deferred=' + d2.deferred);
console.log('§VALRULE_DEFER id=211(no-ctx)=' + d1.deferred + ' id=52056=' + d2.deferred + ' AD_Rule(SQL-Q,n/a-in-seed)=' + adrule);

// ── §FALSIFIER: the filter is LOAD-BEARING — a PAID invoice is EXCLUDED by the NotPaid rule (146) ───────
console.log('\n── §FALSIFIER — membership: a paid invoice must NOT survive the NotPaid filter ──');
var paid   = V.evalValRule(db, 146, { candidatePk: { col: 'c_invoice_id', id: 100 } });  // inv 100 ispaid='Y'
var unpaid = V.evalValRule(db, 146, { candidatePk: { col: 'c_invoice_id', id: 109 } });  // inv 109 ispaid='N'
console.log('§FALSIFIER rule=146(NotPaid) paidInvoice=100 member=' + paid.member + ' (must be false)  unpaidInvoice=109 member=' + unpaid.member + ' (must be true)');
verdict(paid.member === false, 'paid invoice 100 is EXCLUDED by the NotPaid filter (rule excludes the row it should)', 'member=' + paid.member);
verdict(unpaid.member === true, 'unpaid invoice 109 SURVIVES the NotPaid filter', 'member=' + unpaid.member);
// gate-off control: with no where-clause both would be admitted → proves the clause itself does the exclusion
var gateOff = !!db.prepare("SELECT 1 FROM c_invoice WHERE c_invoice_id=100").get();
verdict(gateOff && paid.member === false, '§FALSIFIER control: gate-OFF admits invoice 100, the clause is what excludes it', 'gateOff-admits=' + gateOff);

console.log('\n' + (fails === 0 ? '🟢 W-VALRULE PASS' : '🔴 W-VALRULE FAIL (' + fails + ')') +
  ' — AD_Val_Rule SQL-where interpreted on canonical ad_full.db; static+token applied as real filters, defers explicit. Re-verdict AD_Val_Rule + GAP #7 (⛔→🟡).');
db.close();
process.exit(fails === 0 ? 0 : 1);
