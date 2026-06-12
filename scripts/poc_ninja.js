#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_ninja.js — W-NINJA-READ / W-NINJA-GATE / W-NINJA-RUN witness. Builds the fixture workbook from
// canonical build/erp/ad_full.db (BACKUP = STORED columns), round-trips it through the real .xlsx file
// format, then drives build/erp/ninja_excel.js:
//   READ — manifest extraction; ISSUE PROVED: SheetJS does not evaluate formulas, so a stale cached
//          =CONCATENATE annotation (moved-row workbook) would mis-token every binding → §6.3 re-derivation
//          must win over the cache (§FALSIFIER: derived ≠ stale cache, reader returns derived).
//   GATE — ISSUE PROVED: a dangling #bind / empty TABLE / uncovered @token@ must REFUSE to run (silent
//          skip = wrong report); three falsifiers, each must be refused with a named error.
//   RUN  — verify-by-example: Process SQL (independent path SUM(lines)+SUM(tax)) folded over the db,
//          written back, compared to BACKUP (stored grandtotal/documentno/count) → maxDiff=0c.
//          ISSUE PROVED: equality with the sample is load-bearing — a deliberately wrong binding
//          (swapped documentno) must be FALSIFIED (diff ≠ 0), or the oracle proves nothing.
// Implementing internal/NinjaExcelAdaptation.md §5.1/§5.5/§6.2/§6.3.
// Run: bash build/erp/run_witness.sh scripts/poc_ninja.js   (read the log; exit code != evidence)
'use strict';
var path = require('path');
var XLSX = require('xlsx');
var Database = require('better-sqlite3');
var N = require(path.join(__dirname, '..', 'build', 'erp', 'ninja_excel.js'));
var fixture = require(path.join(__dirname, 'make_ninja_fixture.js'));

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

console.log('═══ W-NINJA — NinjaExcel RUN engine (BACKUP=stored cols vs Process SQL=independent path, ad_full.db) ═══\n');

// ── fixture: extract from the db, write the real .xlsx, read it BACK (full format round-trip) ──────────
var fx = fixture.build();
console.log('§NINJA_FIXTURE out=' + path.basename(fx.out) + ' invoices=' + fx.invoices + ' processRows=' + fx.processRows + ' subtotal=' + fx.subtotal + ' count=' + fx.count);
var wb = XLSX.readFile(fx.out);
verdict(wb.SheetNames.join(',') === 'BACKUP,Input,Process', 'workbook round-trips with the three-sheet model', wb.SheetNames.join(','));

// ── W-NINJA-READ — manifest + §6.3 re-derivation over the stale cache ──────────────────────────────────
console.log('\n── W-NINJA-READ — manifest extraction + formula re-derivation (§6.3) ──');
var man = N.readManifest(wb);
console.log('§NINJA_READ rows=' + man.rows.length + ' binds=' + JSON.stringify(man.binds) + ' inputTokens=' + man.inputTokens.length);
verdict(man.rows.length === 9, '9 Process rows read (4 docno + 4 amount + 1 count)', 'got=' + man.rows.length);
verdict(man.binds['1'] === '2002-01-01' && man.binds['2'] === '2003-12-31', 'binds #1/#2 read by adjacency from Input', JSON.stringify(man.binds));
verdict(man.inputTokens.length === 9, '9 @token@ holes found on Input', 'got=' + man.inputTokens.length);
var r2 = man.rows[0];                                                  // sheet row 2 = the stale-cache row
console.log('§NINJA_REDERIVE sheetRow=2 cached="' + r2.cachedAnnotation + '" derived="' + r2.annotation.slice(0, 40) + '…" stale=' + r2.staleAnnotation);
verdict(r2.cachedAnnotation === '@STALE_MOVED_ROW@99', '§FALSIFIER setup: cached annotation IS stale in the file', r2.cachedAnnotation);
verdict(r2.staleAnnotation === true && r2.annotation === '@' + r2.select + '@2', 'reader RE-DERIVES the token (cache ignored) — moved-row workbook cannot mis-token', 'derived=@<select>@2');
verdict(man.rows.every(function (r, i) { return r.row === i + 2; }), '=ROW() re-derived from sheet position for all rows');

// ── W-NINJA-GATE — refuse-to-run falsifiers (§5.5) ─────────────────────────────────────────────────────
console.log('\n── W-NINJA-GATE — completeness gate refuses, never silently skips (§5.5) ──');
var g0 = N.gate(man);
verdict(g0.ok, 'complete manifest passes the gate', g0.errors.join('; '));

function cloneMan() { return N.readManifest(XLSX.readFile(fx.out)); }  // fresh manifest per falsifier

var mBind = cloneMan(); delete mBind.binds['2'];                       // (a) dangling bind
var gBind = N.gate(mBind);
verdict(!gBind.ok && /bind #2 referenced but not provided/.test(gBind.errors.join(' ')), '§FALSIFIER dangling bind #2 → REFUSED', gBind.errors[0]);

var mTable = cloneMan(); mTable.rows[4].table = '';                    // (b) empty TABLE
var gTable = N.gate(mTable);
verdict(!gTable.ok && /empty TABLE/.test(gTable.errors.join(' ')), '§FALSIFIER empty TABLE → REFUSED', (gTable.errors.filter(function (e) { return /TABLE/.test(e); })[0]));

var mTok = cloneMan(); mTok.rows.pop();                                // (c) count row gone → B11 token uncovered
var gTok = N.gate(mTok);
verdict(!gTok.ok && /dangling token @field_11_2@/.test(gTok.errors.join(' ')), '§FALSIFIER uncovered @token@ at Input!B11 → REFUSED', (gTok.errors.filter(function (e) { return /dangling/.test(e); })[0]));
var threw = false;
try { N.run(mTok, function () { return 0; }); } catch (e) { threw = /gate refused/.test(e.message); }
verdict(threw, 'run() on a gated-out manifest THROWS (refuse-to-run is the contract)');
console.log('§NINJA_GATE pass=' + g0.ok + ' falsifiers refused=3/3');

// ── W-NINJA-RUN — fold over the db, write back, verify-by-example (§5.1) ───────────────────────────────
console.log('\n── W-NINJA-RUN — verify-by-example: folded values == filled BACKUP sample ──');
var db = new Database(fixture.DB_PATH, { readonly: true });
function exec(sql, params) { var row = db.prepare(sql).raw().get(params); return row ? row[0] : null; }

var results = N.run(man, exec);
results.forEach(function (r) { console.log('§NINJA_RUN ' + r.address + ' = ' + r.value); });
N.writeBack(wb, 'Input', results);
var v = N.verifyByExample(wb, 'BACKUP', results);
console.log('§NINJA_VERIFY cells=' + v.cells + ' maxDiff=' + v.maxDiffCents + 'c stringMismatches=' + v.stringMismatches);
verdict(v.cells === 9 && v.pass, 'all 9 cells equal the BACKUP sample (stored vs independent-path fold)', 'maxDiff=' + v.maxDiffCents + 'c strings=' + v.stringMismatches);

// the template's own =SUM subtotal: written amounts must recompute to BACKUP's cached subtotal
var sumCents = results.filter(function (r) { return /^B[5-8]$/.test(r.address); })
                      .reduce(function (s, r) { return s + N.cents(r.value); }, 0);
var bkSub = wb.Sheets.BACKUP.B9;
console.log('§NINJA_SUBTOTAL written=' + (sumCents / 100) + ' backupCached=' + bkSub.v + ' formulaPreserved=' + (wb.Sheets.Input.B9 && !!wb.Sheets.Input.B9.f));
verdict(sumCents === N.cents(bkSub.v), 'template =SUM(B5:B8) would recompute to the BACKUP subtotal', sumCents / 100 + ' vs ' + bkSub.v);
verdict(!!(wb.Sheets.Input.B9 && wb.Sheets.Input.B9.f), 'subtotal stays a FORMULA (never computed by the engine — Excel recalc owns it)');

// §FALSIFIER: a wrong binding must be CAUGHT — swap one row's documentno, equality must break
var mWrong = cloneMan();
mWrong.rows[1].where = mWrong.rows[1].where.replace("'200000'", "'200001'");   // B5 now folds invoice 200001
var wrong = N.run(mWrong, exec);
var vWrong = N.verifyByExample(wb, 'BACKUP', wrong);
console.log('§FALSIFIER swapped-binding maxDiff=' + vWrong.maxDiffCents + 'c (must be ≠0 — the equality oracle is load-bearing)');
verdict(vWrong.maxDiffCents > 0, '§FALSIFIER wrong binding (swapped documentno) is FALSIFIED by the sample', 'maxDiff=' + vWrong.maxDiffCents + 'c');

// persist the filled artifact for eyeballing in Excel (formatting/graphs lane untouched)
var filledPath = fx.out.replace('.xlsx', '_filled.xlsx');
XLSX.writeFile(wb, filledPath);
console.log('§NINJA_ARTIFACT filled=' + path.basename(filledPath));

db.close();
console.log('\n' + (fails === 0 ? '🟢 W-NINJA PASS' : '🔴 W-NINJA FAIL (' + fails + ')') +
  ' — READ re-derives §6.3, GATE refuses 3/3, RUN verify-by-example maxDiff=0c vs stored BACKUP (' +
  'NinjaExcelAdaptation.md first build step, headless).');
process.exit(fails === 0 ? 0 : 1);
