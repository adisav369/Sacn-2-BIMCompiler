#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_ninja_rule.js — W-NINJA-RULE witness. Drives build/erp/ninja_rule.js (§7 v1 grammar) against the
// REAL AD dictionary in build/erp/ad_full.db:
//   EXTRACTED VOCAB PROVED: 'Invoices'→C_Invoice via ad_table.Name; 'completed'→docstatus='CO' via
//        ad_ref_list (never hardcoded); 'is Sales Transaction'→issotrx='Y' via ad_column.Name (YesNo ref).
//   AMBIGUITY-IS-HONEST PROVED: a date range fans out to one candidate PER date column of C_Invoice;
//        pickByExample runs them ALL — the BACKUP sample falsifies the wrong ones; a surviving tie is
//        REPORTED (tie=true), never auto-picked (confirm-each §9.1). Auto-pick would be inventing.
//   §FALSIFIER: same phrase with status 'drafted' → 0 accepted (the sample kills a wrong rule);
//        unknown entity 'Foobars' → explicit error, refuse (no fuzzy rescue).
//   INTEGRATION: the human-picked winner becomes a Process row → RUN engine → cell == BACKUP (0c).
// Implementing internal/NinjaExcelAdaptation.md §7 — Witness: W-NINJA-RULE.
// Run: bash build/erp/run_witness.sh scripts/poc_ninja_rule.js   (read the log; exit code != evidence)
'use strict';
var path = require('path');
var XLSX = require('xlsx');
var Database = require('better-sqlite3');
var N = require(path.join(__dirname, '..', 'build', 'erp', 'ninja_excel.js'));
var R = require(path.join(__dirname, '..', 'build', 'erp', 'ninja_rule.js'));
var fixture = require(path.join(__dirname, 'make_ninja_fixture.js'));

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

console.log('═══ W-NINJA-RULE — business phrase → SQL candidates from the AD dictionary (ad_full.db, zero-LLM) ═══\n');

var db = new Database(fixture.DB_PATH, { readonly: true });
function query(sql, params) { return db.prepare(sql).all(params); }
function execScalar(sql) { var row = db.prepare(sql).raw().get(); return row ? row[0] : null; }

var dict = R.loadDict(query);
console.log('§RULE_DICT tables=' + dict.tableCount + ' (vocabulary = ad_table/ad_column/ad_ref_list, extracted not authored)');
verdict(dict.tableCount > 900, 'dictionary loaded from the real seed', 'tables=' + dict.tableCount);

// ── R1: COUNT phrase — every slot resolves from the dictionary ──────────────────────────────────────────
console.log('\n── R1: "COUNT of Invoices, completed, is Sales Transaction, from 2002-01-01 to 2003-12-31" ──');
var r1 = R.compile(dict, 'COUNT of Invoices, completed, is Sales Transaction, from 2002-01-01 to 2003-12-31');
console.log('§RULE_COMPILE entity=' + r1.entity + ' select=' + r1.select + ' filters=' + JSON.stringify(r1.filters) +
  ' dateCandidates=' + (r1.candidates || []).length);
verdict(r1.ok, 'phrase compiles', (r1.errors || []).join('; '));
verdict(r1.entity === 'C_Invoice', "entity 'Invoices' → C_Invoice via ad_table.Name (plural-insensitive)", r1.entity);
verdict(r1.filters.indexOf("docstatus='CO'") >= 0, "'completed' → docstatus='CO' EXTRACTED from ad_ref_list", JSON.stringify(r1.filters));
verdict(r1.filters.indexOf("issotrx='Y'") >= 0, "'is Sales Transaction' → issotrx='Y' via ad_column.Name (YesNo)", JSON.stringify(r1.filters));
verdict(r1.candidates.length >= 5, 'date range fans out over EVERY date column of C_Invoice (no auto-pick)', 'candidates=' + r1.candidates.length);

// the BACKUP sample (stored count = 4) falsifies the wrong date columns; a tie is for the HUMAN
var fx = fixture.build();
var wb = XLSX.readFile(fx.out);
var sampleCount = wb.Sheets.BACKUP.B11.v;
var p1 = R.pickByExample(r1.candidates, execScalar, sampleCount);
console.log('§RULE_PICK sample=' + sampleCount + ' accepted=[' + p1.accepted.map(function (a) { return a.candidate.dateColumn; }).join(',') +
  '] rejected=[' + p1.rejected.map(function (x) { return x.candidate.dateColumn + '→' + x.got; }).join(',') + '] tie=' + p1.tie);
verdict(p1.accepted.length >= 1, 'sample accepts ≥1 date-column candidate', 'accepted=' + p1.accepted.length);
verdict(p1.rejected.length >= 1, 'sample FALSIFIES ≥1 wrong date column (the fan-out is load-bearing)', 'rejected=' + p1.rejected.length);
verdict(p1.tie === (p1.accepted.length > 1), 'a surviving tie is REPORTED for the human, never auto-picked', 'tie=' + p1.tie);

// ── R2: SUM phrase — money in cents against the stored subtotal ─────────────────────────────────────────
console.log('\n── R2: "SUM GrandTotal of Invoices, completed, is Sales Transaction, from 2002-01-01 to 2003-12-31" ──');
var r2 = R.compile(dict, 'SUM GrandTotal of Invoices, completed, is Sales Transaction, from 2002-01-01 to 2003-12-31');
var sampleSum = wb.Sheets.BACKUP.B9.v;                                   // stored-column subtotal 541.02
var p2 = R.pickByExample(r2.candidates, execScalar, sampleSum);
console.log('§RULE_SUM select=' + r2.select + ' sample=' + sampleSum + ' accepted=' +
  p2.accepted.map(function (a) { return a.candidate.dateColumn + '→' + a.got; }).join(',') + ' tie=' + p2.tie);
verdict(r2.ok && r2.select === 'SUM(grandtotal)', "measure 'SUM GrandTotal' → SUM(grandtotal) via ad_column", r2.select);
verdict(p2.accepted.length >= 1 && p2.accepted.every(function (a) { return R.cents(a.got) === R.cents(sampleSum); }),
  'accepted candidates equal the sample to the cent', 'maxOff=0c sample=' + sampleSum);

// ── §FALSIFIERS: wrong status killed by the sample; unknown entity refused ──────────────────────────────
console.log('\n── §FALSIFIERS — the oracle and the closed grammar both refuse wrongness ──');
var r3 = R.compile(dict, 'SUM GrandTotal of Invoices, drafted, is Sales Transaction, from 2002-01-01 to 2003-12-31');
var p3 = R.pickByExample(r3.candidates, execScalar, sampleSum);
console.log("§FALSIFIER status=drafted accepted=" + p3.accepted.length + ' (sample ' + sampleSum + ' kills every candidate)');
verdict(r3.ok && r3.filters.indexOf("docstatus='DR'") >= 0, "'drafted' still compiles (it IS in ad_ref_list) → docstatus='DR'", JSON.stringify(r3.filters));
verdict(p3.accepted.length === 0, '§FALSIFIER wrong status → 0 accepted: a plausible-but-wrong rule cannot bind', 'accepted=' + p3.accepted.length);
var r4 = R.compile(dict, 'COUNT of Foobars, completed');
console.log('§FALSIFIER entity=Foobars ok=' + r4.ok + ' error="' + r4.errors[0] + '"');
verdict(!r4.ok && /unknown entity/.test(r4.errors[0]), '§FALSIFIER unknown entity → explicit refuse, no fuzzy rescue');
var r5 = R.compile(dict, 'COUNT of Invoices, sideways');
verdict(!r5.ok && /matches no qualifier form/.test(r5.errors[0]), '§FALSIFIER unknown qualifier word → explicit refuse', r5.errors[0]);

// ── INTEGRATION: human-picked winner (confirm-each) → Process row → RUN engine → BACKUP oracle ─────────
console.log('\n── integration: picked winner becomes the Process row for Input!B11 → RUN → verify-by-example ──');
var human = p1.accepted.filter(function (a) { return a.candidate.dateColumn === 'dateinvoiced'; })[0] || p1.accepted[0];
console.log('§RULE_CONFIRM_EACH human picks dateColumn=' + human.candidate.dateColumn + ' from tie=[' +
  p1.accepted.map(function (a) { return a.candidate.dateColumn; }).join(',') + '] (the labeled HUMAN step)');
var man = N.readManifest(wb);
var b11row = man.rows.filter(function (r) { return r.address === 'B11'; })[0];
b11row.select = human.candidate.select; b11row.table = human.candidate.table; b11row.where = human.candidate.where;
function exec(sql, params) { var row = db.prepare(sql).raw().get(params); return row ? row[0] : null; }
var results = N.run(man, exec);
var v = N.verifyByExample(wb, 'BACKUP', results);
console.log('§RULE_RUN cells=' + v.cells + ' maxDiff=' + v.maxDiffCents + 'c stringMismatches=' + v.stringMismatches +
  ' (B11 row now RULE-compiled, rest hand-SQL — the authoring ladder on one surface)');
verdict(v.cells === 9 && v.pass, 'RULE-compiled row runs through the same engine to the same oracle (maxDiff=0c)', 'maxDiff=' + v.maxDiffCents + 'c');

db.close();
console.log('\n' + (fails === 0 ? '🟢 W-NINJA-RULE PASS' : '🔴 W-NINJA-RULE FAIL (' + fails + ')') +
  ' — §7 v1 grammar compiles from extracted vocabulary; sample falsifies wrong candidates; ties go to the human; winner folds 0c.');
process.exit(fails === 0 ? 0 : 1);
