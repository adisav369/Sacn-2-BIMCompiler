// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard
// Scope: headless §-witness for the Report verb R2 (Financial Report) — prompts/CRUD_P_R_REPORT.md §R2,
//   docs/CRUD_P_R_REPORT_SPEC.md §1.2.1. Proves, against the REAL report_overlay.js CORE + the REAL
//   fact_acct rows now bundled in glassbowl_data.db (extracted from Docker idempiere_test, client 11),
//   that the Trial Balance is a FOLD that BALANCES TO THE CENT (ΣDr==ΣCr), reconciles to an independent
//   re-sum of the rows, and that the P&L folds from the same journal. §-log first; READ before concluding.
// Run:  node scripts/test_report_fin.js 2>&1 | tee build/erp/test_report_fin.log
'use strict';
var path = require('path'), cp = require('child_process');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var CORE = require(path.join(ERP, 'report_overlay.js'));
var DB = path.join(ERP, 'glassbowl_data.db');

var fails = 0;
function ok(cond, msg) { if (!cond) { fails++; console.log('  ✗ FAIL: ' + msg); } else { console.log('  ✓ ' + msg); } }
function q(sql) { var out = cp.execSync('sqlite3 -json "' + DB + '" "' + sql.replace(/"/g, '\\"') + '"', { encoding: 'utf8' }).trim(); return out ? JSON.parse(out) : []; }
function q1(sql) { var r = q(sql); return r.length ? r[0] : null; }
function n2(n) { return Number(n).toFixed(2); }
// CORE money fields are now exact 2dp STRINGS (§I-L BigDecimal fold) — compare to the cent, not by ===/round2.
function cents(x) { return x == null ? 0 : Math.round(Number(x) * 100); }

console.log('=== §REPORT-FIN witness — ' + new Date().toISOString() + ' ===\n');

// pull the REAL bundled rows (the exact browser path: fact_acct + chart of accounts from the bundle).
var facts = q('SELECT account_id, amtacctdr, amtacctcr FROM fact_acct');
var accRows = q('SELECT c_elementvalue_id, value, name, accounttype FROM c_elementvalue');
var accounts = {}; accRows.forEach(function (a) { accounts[a.c_elementvalue_id] = { value: a.value, name: a.name, accounttype: a.accounttype }; });

// ── ISSUE R2.1: the Trial Balance FOLDS and BALANCES to the cent (double-entry, re-derived) ──
console.log('[ISSUE R2.1] Trial Balance folds from fact_acct and balances Dr==Cr to the cent');
var tb = CORE.foldTrialBalance(facts, accounts);
console.log('§REPORT-FIN trial-balance Dr=' + n2(tb.totalDr) + ' Cr=' + n2(tb.totalCr) + ' balanced=' + (tb.balanced ? 'Y' : 'N') + ' maxDiff=' + tb.maxDiffCents + 'c folds-from=' + tb.foldsFrom + ' rows=' + tb.rows);
ok(tb.rows === facts.length, 'folded over every bundled fact row (' + tb.rows + ')');
ok(tb.balanced && tb.maxDiffCents === 0, 'ΣDr (' + n2(tb.totalDr) + ') == ΣCr (' + n2(tb.totalCr) + ') to the cent (diff=' + tb.maxDiffCents + 'c)');

// ── ISSUE R2.2: the folded totals == an INDEPENDENT re-sum of the rows (no hand-authored balance) ──
console.log('\n[ISSUE R2.2] folded totals == independent SUM over fact_acct (no asserted number)');
var indep = q1('SELECT ROUND(SUM(amtacctdr),2) dr, ROUND(SUM(amtacctcr),2) cr, COUNT(*) n FROM fact_acct');
console.log('§REPORT-FIN-RECON foldedDr=' + n2(tb.totalDr) + ' sqliteDr=' + n2(indep.dr) + ' foldedCr=' + n2(tb.totalCr) + ' sqliteCr=' + n2(indep.cr) + ' rows=' + indep.n);
ok(cents(tb.totalDr) === cents(indep.dr), 'folded ΣDr == independent SQLite SUM(amtacctdr)');
ok(cents(tb.totalCr) === cents(indep.cr), 'folded ΣCr == independent SQLite SUM(amtacctcr)');

// ── ISSUE R2.3: account meta is truth-bound — accounts resolve to the chart of accounts, not "#id" ──
console.log('\n[ISSUE R2.3] account lines carry chart-of-accounts names (truth-bound)');
var named = tb.lines.filter(function (l) { return l.name && String(l.value).charAt(0) !== '#'; });
var sample = tb.lines.slice(0, 4).map(function (l) { return l.value + ':' + (l.name || '?') + ' Dr' + n2(l.dr) + '/Cr' + n2(l.cr); });
console.log('§REPORT-FIN accounts=' + tb.lines.length + ' named=' + named.length + ' sample=[' + sample.join(' | ') + ']');
ok(named.length === tb.lines.length, 'every account line resolved a value+name from c_elementvalue (' + named.length + '/' + tb.lines.length + ')');

// ── ISSUE R2.4: the P&L folds from the SAME journal (revenue − expense = net income) ──
console.log('\n[ISSUE R2.4] P&L folds from the same fact_acct (revenue − expense = net income)');
var pnl = CORE.foldPnL(facts, accounts);
console.log('§REPORT-FIN pnl revenue=' + n2(pnl.revenue) + ' expense=' + n2(pnl.expense) + ' netIncome=' + n2(pnl.netIncome) + ' accounts=' + pnl.lines.length + ' folds-from=' + pnl.foldsFrom);
ok(pnl.lines.length > 0, 'P&L folded at least one revenue/expense account (' + pnl.lines.length + ')');
ok(cents(pnl.netIncome) === (cents(pnl.revenue) - cents(pnl.expense)), 'netIncome == revenue − expense (cent-exact)');

console.log('\n=== RESULT: ' + (fails === 0 ? 'ALL PASS' : fails + ' FAIL') + ' ===');
process.exit(fails === 0 ? 0 : 1);
