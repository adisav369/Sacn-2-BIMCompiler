#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// make_ninja_fixture.js — build the NinjaExcel witness workbook from canonical build/erp/ad_full.db.
// EXTRACT, DON'T INVENT: every BACKUP value is a STORED column read verbatim (c_invoice.documentno,
// c_invoice.grandtotal, a COUNT) — the filled sample a user would keep. The Process sheet is the
// "user with SQL skills" artifact: its SQL computes the same cells via the INDEPENDENT path
// (SUM(c_invoiceline.linenetamt) + SUM(c_invoicetax.taxamt)), so verify-by-example is a real oracle
// (stored vs computed), not a circular re-query — the W-PRINTFORMAT pattern.
//
// Three sheets per the original schema (internal/NinjaExcel.md §2):
//   BACKUP  = filled sample (stored values; subtotal = the template's own =SUM with cached value)
//   Input   = same layout with @field_<row>_<col>@ holes + the #1/#2 bind cells (parameter surface)
//   Process = ANNOTATION | ROW | SELECT | TABLE | JOIN | WHERE | ADDRESS | Direction | VALUES
// One Process ANNOTATION cell is written with a deliberately STALE cached value (formula intact) —
// the §6.3 re-derivation falsifier for W-NINJA-READ.
// Implementing internal/NinjaExcelAdaptation.md §6.2 — Witness fixture for W-NINJA-READ/GATE/RUN.
// Run: node scripts/make_ninja_fixture.js   (or require(): build(dbPath, outPath))
'use strict';
var path = require('path');
var XLSX = require('xlsx');
var Database = require('better-sqlite3');

var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var OUT_PATH = path.join(__dirname, '..', 'build', 'erp', 'fixtures', 'ninja_invoice_summary.xlsx');

// the independent-path SELECT (lines + tax subselects) — the Process sheet's "user SQL"
var AMOUNT_SELECT = "COALESCE((SELECT SUM(il.linenetamt) FROM c_invoiceline il WHERE il.c_invoice_id=i.c_invoice_id),0)" +
                    " + COALESCE((SELECT SUM(t.taxamt) FROM c_invoicetax t WHERE t.c_invoice_id=i.c_invoice_id),0)";

function build(dbPath, outPath) {
  var db = new Database(dbPath || DB_PATH, { readonly: true });
  // STORED columns only — the BACKUP sample (4 completed SO invoices, the period the report covers)
  var inv = db.prepare(                                       // lowercase aliases: ad_full stores lowercase
    "SELECT documentno AS documentno, grandtotal AS grandtotal FROM c_invoice " +   // names, ad_seed mixed-case
    "WHERE issotrx='Y' AND docstatus='CO' " +
    "AND dateinvoiced >= '2002-01-01' AND dateinvoiced <= '2003-12-31' ORDER BY c_invoice_id").all();
  var count = db.prepare(
    "SELECT COUNT(*) AS n FROM c_invoice WHERE issotrx='Y' AND docstatus='CO' " +
    "AND dateinvoiced >= '2002-01-01' AND dateinvoiced <= '2003-12-31'").get().n;
  db.close();
  if (inv.length !== 4) throw new Error('expected 4 SO invoices in ad_full.db, got ' + inv.length);

  var wb = XLSX.utils.book_new();

  // ── BACKUP — the filled sample report (stored values; layout the user formatted in Excel) ───────────
  var subtotal = inv.reduce(function (s, r) { return s + Math.round(r.grandtotal * 100); }, 0) / 100;
  var backup = {
    A1: { t: 's', v: 'GardenWorld — Sales Invoice Summary' },
    A2: { t: 's', v: 'Period:' }, B2: { t: 's', v: '2002-01-01' }, C2: { t: 's', v: 'to' }, D2: { t: 's', v: '2003-12-31' },
    A4: { t: 's', v: 'DocumentNo' }, B4: { t: 's', v: 'GrandTotal' },
    A9: { t: 's', v: 'Subtotal' }, B9: { t: 'n', f: 'SUM(B5:B8)', v: subtotal },   // template's own SUM, preserved
    A11: { t: 's', v: 'Invoice count' }, B11: { t: 'n', v: count },
    '!ref': 'A1:D11'
  };
  inv.forEach(function (r, i) {
    backup['A' + (5 + i)] = { t: 's', v: r.documentno };
    backup['B' + (5 + i)] = { t: 'n', v: r.grandtotal };
  });
  XLSX.utils.book_append_sheet(wb, backup, 'BACKUP');

  // ── Input — same layout, @field_<row>_<col>@ holes at the data cells + #1/#2 bind cells ─────────────
  var input = {
    A1: backup.A1, A2: backup.A2, B2: backup.B2, C2: backup.C2, D2: backup.D2,
    A4: backup.A4, B4: backup.B4,
    A9: backup.A9, B9: { t: 'n', f: 'SUM(B5:B8)', v: 0 },                          // recalcs in Excel once filled
    A11: backup.A11, B11: { t: 's', v: '@field_11_2@' },
    G1: { t: 's', v: '#1' }, H1: { t: 's', v: '2002-01-01' },                      // bind labels + values (adjacency)
    G2: { t: 's', v: '#2' }, H2: { t: 's', v: '2003-12-31' },
    '!ref': 'A1:H11'
  };
  for (var i = 0; i < 4; i++) {
    input['A' + (5 + i)] = { t: 's', v: '@field_' + (5 + i) + '_1@' };
    input['B' + (5 + i)] = { t: 's', v: '@field_' + (5 + i) + '_2@' };
  }
  XLSX.utils.book_append_sheet(wb, input, 'Input');

  // ── Process — the user-authored query table (original column order, ProcessSheetGenerator:70) ───────
  var header = ['ANNOTATION', 'ROW', 'SELECT', 'TABLE', 'JOIN', 'WHERE', 'ADDRESS', 'Direction', 'VALUES'];
  var rows = [];
  inv.forEach(function (r, i) {
    var where = "i.documentno='" + r.documentno + "' AND i.dateinvoiced >= #1 AND i.dateinvoiced <= #2";
    rows.push(['i.documentno', 'c_invoice i', '', where, 'A' + (5 + i)]);          // docno cell
    rows.push([AMOUNT_SELECT, 'c_invoice i', '', where, 'B' + (5 + i)]);           // amount via independent path
  });
  rows.push(["COUNT(*)", 'c_invoice i', '',
             "i.issotrx='Y' AND i.docstatus='CO' AND i.dateinvoiced >= #1 AND i.dateinvoiced <= #2", 'B11']);

  var proc = { '!ref': 'A1:I' + (1 + rows.length) };
  header.forEach(function (h, c) { proc[XLSX.utils.encode_cell({ r: 0, c: c })] = { t: 's', v: h }; });
  rows.forEach(function (r, idx) {
    var sheetRow = idx + 2;                                                        // 1-based, after header
    var ann = '@' + r[0] + '@' + sheetRow;
    // STALE-CACHE FALSIFIER (§6.3): row 2's ANNOTATION cache simulates a moved-row workbook saved
    // without recalc — formula intact, cached value wrong. The reader must return the DERIVED token.
    var cached = (sheetRow === 2) ? '@STALE_MOVED_ROW@99' : ann;
    proc['A' + sheetRow] = { t: 's', f: 'CONCATENATE("@",C' + sheetRow + ',"@",B' + sheetRow + ')', v: cached };
    proc['B' + sheetRow] = { t: 'n', f: 'ROW()', v: sheetRow };
    proc['C' + sheetRow] = { t: 's', v: r[0] };
    proc['D' + sheetRow] = { t: 's', v: r[1] };
    if (r[2]) proc['E' + sheetRow] = { t: 's', v: r[2] };
    proc['F' + sheetRow] = { t: 's', v: r[3] };
    proc['G' + sheetRow] = { t: 's', v: r[4] };
    proc['H' + sheetRow] = { t: 's', v: '>' };
  });
  XLSX.utils.book_append_sheet(wb, proc, 'Process');

  XLSX.writeFile(wb, outPath || OUT_PATH);
  return { out: outPath || OUT_PATH, invoices: inv.length, processRows: rows.length, subtotal: subtotal, count: count };
}

if (require.main === module) {
  var res = build();
  console.log('§NINJA_FIXTURE out=' + res.out + ' invoices=' + res.invoices + ' processRows=' + res.processRows +
              ' subtotal=' + res.subtotal + ' count=' + res.count + ' (BACKUP=stored cols only)');
}
module.exports = { build: build, DB_PATH: DB_PATH, OUT_PATH: OUT_PATH };
