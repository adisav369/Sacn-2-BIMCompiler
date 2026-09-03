#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_ninja_make.js — W-NINJA-MAKE witness. Takes a BACKUP-ONLY workbook (the filled sample a user
// drops) and drives the MAKE verb in build/erp/ninja_excel.js:
//   §5.3 ISSUE PROVED: identifyTableStructures() was a TODO stub in the original (NinjaExcel.md §4) —
//        pure run-length geometry must find the A4:B8 grid (header row + 4 data rows), no AI.
//   §5.2 ISSUE PROVED: suggestedQueryType was never computed — parse the formula text itself.
//   §5.4 ISSUE PROVED: SELECT proposals come from adjacent labels — and a proposal is NOT an answer:
//        'documentno'/'grandtotal' happen to be real ad_column names, 'invoicecount' is NOT → the
//        witness checks proposals against the real dictionary and shows the user-edit is required.
//   CONFIRM-EACH (§9.1) PROVED: the raw scaffold REFUSES to run (empty TABLE → §5.5 gate), and only
//        after the labeled USER-FINISH step does RUN pass verify-by-example (maxDiff=0c vs BACKUP).
// Implementing internal/NinjaExcelAdaptation.md §5.2–§5.4 + §6.2 MAKE — Witness: W-NINJA-MAKE.
// Run: bash build/erp/run_witness.sh scripts/poc_ninja_make.js   (read the log; exit code != evidence)
'use strict';
var path = require('path');
var XLSX = require('xlsx');
var Database = require('better-sqlite3');
var N = require(path.join(__dirname, '..', 'build', 'erp', 'ninja_excel.js'));
var fixture = require(path.join(__dirname, 'make_ninja_fixture.js'));

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

console.log('═══ W-NINJA-MAKE — analyze BACKUP → scaffold Input+Process (stubs §5.2–§5.4 closed, no AI) ═══\n');

// ── the user's artifact: a workbook holding ONLY the filled sample ──────────────────────────────────────
var fx = fixture.build();
var full = XLSX.readFile(fx.out);
var wb = XLSX.utils.book_new();
XLSX.utils.book_append_sheet(wb, full.Sheets.BACKUP, 'BACKUP');
verdict(wb.SheetNames.length === 1, 'input is BACKUP-only (the sample is all the Maker gets)', wb.SheetNames.join(','));

// ── §5.2 — suggestedQueryType parsed from the formula text (the author's own intent) ───────────────────
console.log('\n── §5.2 suggestedQueryType from formula text ──');
var qt = [['SUM(B5:B8)', 'SUM'], ['COUNT(A5:A8)', 'COUNT'], ['AVERAGE(B5:B8)', 'AVG'], ['B5', 'SELECT']];
qt.forEach(function (p) {
  var got = N.suggestedQueryType(p[0]);
  verdict(got === p[1], '"' + p[0] + '" → ' + p[1], 'got=' + got);
});

// ── §5.3 — table structure by pure run-length geometry ─────────────────────────────────────────────────
console.log('\n── §5.3 identifyTableStructures as shape (the original TODO stub, closed) ──');
var a = N.analyzeBackup(wb.Sheets.BACKUP);
var t = a.tables[0];
console.log('§MAKE_TABLES count=' + a.tables.length + ' header=row' + (t ? t.headerRow + 1 : '-') +
  ' data=rows' + (t ? (t.dataStart + 1) + '-' + (t.dataEnd + 1) : '-') +
  ' cols=' + (t ? t.cols.map(function (c) { return c.header; }).join('|') : '-'));
verdict(a.tables.length === 1, 'exactly ONE table found (Period row / Subtotal row / count row are NOT tables)', 'got=' + a.tables.length);
verdict(t && t.headerRow === 3 && t.dataStart === 4 && t.dataEnd === 7, 'grid = header row 4, data rows 5–8 (run-length over span, formula row 9 excluded)');
verdict(t && t.cols.map(function (c) { return c.header; }).join('|') === 'DocumentNo|GrandTotal', 'headers read from the all-string row above the run');

// ── §5.4 + §3 — dynamic cells with label proposals; formulas PRESERVED ──────────────────────────────────
console.log('\n── §5.4 label-adjacency proposals + §3 formula preservation ──');
console.log('§MAKE_DYNAMIC cells=' + a.dynamicCells.length + ' (' +
  a.dynamicCells.filter(function (d) { return d.kind === 'table-cell'; }).length + ' table + ' +
  a.dynamicCells.filter(function (d) { return d.kind === 'point-cell'; }).length + ' point) preservedFormulas=' +
  a.preservedFormulas.map(function (p) { return p.address + ':' + p.queryType; }).join(','));
verdict(a.dynamicCells.length === 9, '9 dynamic cells (8 grid + 1 point B11 via left-label adjacency)', 'got=' + a.dynamicCells.length);
verdict(a.preservedFormulas.length === 1 && a.preservedFormulas[0].address === 'B9' && a.preservedFormulas[0].queryType === 'SUM',
  'subtotal B9 = PRESERVED formula (template-owned, never a query row)', JSON.stringify(a.preservedFormulas[0]));
var b11 = a.dynamicCells.filter(function (d) { return d.address === 'B11'; })[0];
verdict(!!b11 && b11.label === 'Invoice count', 'point-cell B11 labeled from the cell to its left', b11 && b11.label);

// proposals vs the REAL dictionary — a proposal is a proposal, not an answer (confirm-each §9.1)
var db = new Database(fixture.DB_PATH, { readonly: true });
var realCols = {};
db.prepare("SELECT lower(columnname) AS c FROM ad_column WHERE ad_table_id=318").all()
  .forEach(function (r) { realCols[r.c] = true; });
var props = {}; a.dynamicCells.forEach(function (d) { props[d.selectProposal] = true; });
console.log('§MAKE_PROPOSALS ' + Object.keys(props).map(function (p) { return p + (realCols[p] ? '=real-column' : '=NEEDS-USER'); }).join(' '));
verdict(realCols.documentno && realCols.grandtotal, 'proposals documentno/grandtotal match real ad_column names (extracted check)');
verdict(!realCols.invoicecount, "proposal 'invoicecount' is NOT a column → the user-edit step is real, not ceremony");

// ── scaffold: generated Input+Process; raw scaffold REFUSES to run (gate) ───────────────────────────────
console.log('\n── MAKE scaffold → gate refuses until the user finishes (confirm-each §9.1) ──');
N.makeScaffold(wb);
var scaffoldPath = fx.out.replace('invoice_summary', 'make_scaffold');
XLSX.writeFile(wb, scaffoldPath);
var wb2 = XLSX.readFile(scaffoldPath);                                   // full file round-trip
var tokens = Object.keys(wb2.Sheets.Input).filter(function (k) { return k[0] !== '!' && /^@.+@$/.test(String(wb2.Sheets.Input[k].v)); });
console.log('§MAKE_SCAFFOLD sheets=' + wb2.SheetNames.join(',') + ' inputTokens=' + tokens.length +
  ' inputB9formula=' + !!(wb2.Sheets.Input.B9 && wb2.Sheets.Input.B9.f));
verdict(tokens.length === 9, 'Input scaffold holds 9 @field_<row>_<col>@ holes', 'got=' + tokens.length);
verdict(!!(wb2.Sheets.Input.B9 && wb2.Sheets.Input.B9.f), 'Input B9 keeps the =SUM formula (Excel recalc owns the subtotal)');
var manRaw = N.readManifest(wb2);
var gRaw = N.gate(manRaw);
console.log('§MAKE_GATE_RAW ok=' + gRaw.ok + ' errors=' + gRaw.errors.length + ' (every row: empty TABLE → user must fill)');
verdict(!gRaw.ok && gRaw.errors.length >= 9 && /empty TABLE/.test(gRaw.errors[0]), 'RAW scaffold is REFUSED by the gate (a proposal is not runnable)', gRaw.errors[0]);

// ── USER-FINISH (the human's part, done programmatically and labeled as such) → RUN → oracle ───────────
console.log('\n── USER-FINISH: fill TABLE/WHERE/binds as the SQL-skills user → RUN → verify-by-example ──');
var proc = wb2.Sheets.Process, backup = wb2.Sheets.BACKUP;
var AMOUNT = "COALESCE((SELECT SUM(il.linenetamt) FROM c_invoiceline il WHERE il.c_invoice_id=i.c_invoice_id),0)" +
             " + COALESCE((SELECT SUM(t.taxamt) FROM c_invoicetax t WHERE t.c_invoice_id=i.c_invoice_id),0)";
for (var r = 2; r <= 10; r++) {                                          // 9 scaffold rows
  var addr = proc['G' + r].v, col = addr[0], docRow = addr.slice(1);
  proc['D' + r] = { t: 's', v: 'c_invoice i' };
  if (addr === 'B11') {
    proc['C' + r] = { t: 's', v: 'COUNT(*)' };
    proc['F' + r] = { t: 's', v: "i.issotrx='Y' AND i.docstatus='CO' AND i.dateinvoiced >= #1 AND i.dateinvoiced <= #2" };
  } else {
    var docno = backup['A' + docRow].v;                                  // the user reads their own sample
    proc['C' + r] = { t: 's', v: col === 'A' ? 'i.documentno' : AMOUNT };
    proc['F' + r] = { t: 's', v: "i.documentno='" + docno + "' AND i.dateinvoiced >= #1 AND i.dateinvoiced <= #2" };
  }
}
wb2.Sheets.Input.G1 = { t: 's', v: '#1' }; wb2.Sheets.Input.H1 = { t: 's', v: '2002-01-01' };
wb2.Sheets.Input.G2 = { t: 's', v: '#2' }; wb2.Sheets.Input.H2 = { t: 's', v: '2003-12-31' };

var man = N.readManifest(wb2), g = N.gate(man);
verdict(g.ok, 'finished manifest passes the gate', g.errors.join('; '));
function exec(sql, params) { var row = db.prepare(sql).raw().get(params); return row ? row[0] : null; }
var results = N.run(man, exec);
N.writeBack(wb2, 'Input', results);
var v = N.verifyByExample(wb2, 'BACKUP', results);
console.log('§MAKE_RUN cells=' + v.cells + ' maxDiff=' + v.maxDiffCents + 'c stringMismatches=' + v.stringMismatches +
  ' (amounts via INDEPENDENT lines+tax path, BACKUP=stored grandtotal)');
verdict(v.cells === 9 && v.pass, 'MAKE scaffold + user-finish runs to the BACKUP oracle (maxDiff=0c)', 'maxDiff=' + v.maxDiffCents + 'c');

db.close();
console.log('\n' + (fails === 0 ? '🟢 W-NINJA-MAKE PASS' : '🔴 W-NINJA-MAKE FAIL (' + fails + ')') +
  ' — §5.2/§5.3/§5.4 stubs closed deterministically; scaffold refuses raw, passes after user-finish, oracle 0c.');
process.exit(fails === 0 ? 0 : 1);
