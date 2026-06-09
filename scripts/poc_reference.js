#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_reference.js — W-REFERENCE witness. Opens canonical build/erp/ad_full.db and drives
// build/erp/ad_reference.js: AD_Ref_Table FK membership (a real id exists, a bogus id does NOT → reject) and
// AD_Column.ValueFormat mask validation+transform (the real 'L' Rating mask + a §FALSIFIER).
// Implementing ERP_COVERAGE_MATRIX.md §AD_Ref_Table / §AD_Column·ValueFormat (GAP #10) — Witness: W-REFERENCE
// Run: node scripts/poc_reference.js 2>&1 | tee build/erp/poc_reference.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var R = require(path.join(__dirname, '..', 'build', 'erp', 'ad_reference.js'));
var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

console.log('═══ W-REFERENCE — AD_Ref_Table FK membership + AD_Column.ValueFormat mask ═══\n');
var db = new Database(DB_PATH, { readonly: true });
var cov = R.coverageScan(db);
console.log('§REFERENCE_COVERAGE refTables=' + cov.refTables + ' colsWithVFormat=' + cov.colsWithVFormat);

// ── AD_Ref_Table FK membership: reference 4 → AD_Reference.AD_Reference_ID ───────────────────────────────
console.log('\n── AD_Ref_Table FK membership (does a candidate id exist in the FK table?) ──');
var rt = R.readRefTable(db, 4);
console.log('§REFTABLE reference=4 fkTable=' + rt.fkTable + ' keyCol=' + rt.keyCol);
var good = R.fkExists(db, 4, 1);          // ad_reference_id 1 exists
console.log('§FK_CHECK table=' + good.fkTable + ' id=1 exists=' + good.exists + ' (accept)');
verdict(good.ok && good.exists === true, 'a real FK id (AD_Reference 1) is admitted (exists=true)', 'exists=' + good.exists);

// §FALSIFIER: a bogus FK id is rejected
var bad = R.fkExists(db, 4, 999999);
console.log('§FALSIFIER table=' + bad.fkTable + ' id=999999 exists=' + bad.exists + ' (reject)');
verdict(bad.ok && bad.exists === false, 'a bogus FK id (999999) is REJECTED (exists=false) — membership is load-bearing', 'exists=' + bad.exists);

// ── AD_Column.ValueFormat: the real 'L' mask (one letter → UPPER) on C_BPartner.Rating ──────────────────
console.log('\n── AD_Column.ValueFormat mask validate+transform (real vformat="L") ──');
var f1 = R.applyVFormat('a', 'L');
console.log('§VFORMAT mask=L in="a" ok=' + f1.ok + ' formatted="' + f1.formatted + '"');
verdict(f1.ok && f1.formatted === 'A', "'L' mask accepts a letter and upper-cases it (a→A)", JSON.stringify(f1));

// §FALSIFIER: a digit fails the 'L' (letter) mask
var f2 = R.applyVFormat('5', 'L');
console.log('§FALSIFIER mask=L in="5" ok=' + f2.ok + ' reason="' + f2.reason + '"');
verdict(f2.ok === false, "a digit '5' is REJECTED by the 'L' letter mask (mask is load-bearing)", 'ok=' + f2.ok);
// a richer mask proves the per-char engine (00-LL = 2 digits + 2 upper letters)
var f3 = R.applyVFormat('12ab', '00LL');
verdict(f3.ok && f3.formatted === '12AB', "multi-char mask '00LL' validates 2 digits + upper-cases 2 letters (12ab→12AB)", JSON.stringify(f3));

console.log('\n' + (fails === 0 ? '🟢 W-REFERENCE PASS' : '🔴 W-REFERENCE FAIL (' + fails + ')') +
  ' — AD_Ref_Table FK membership + ValueFormat mask on canonical ad_full.db. Re-verdict AD_Ref_Table + ValueFormat + GAP #10 (⛔→🟡).');
db.close();
process.exit(fails === 0 ? 0 : 1);
