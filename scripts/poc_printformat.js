// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard
// SCOPE: W-PRINTFORMAT — prove report_overlay.foldPrint reproduces iDempiere's PrintData DATA TREE + break
//   subtotals (NOT pixels — ReportingFold.md §1 honest boundary) for the REAL master-detail format
//   120 "Invoice Header" -> 'P' item (C_Invoice_ID) -> 121 "Invoice LineTax", for EVERY client-11 invoice in the
//   seed. Inputs = the SERVED BUNDLE glassbowl_data.db ALONE (ad_printformat/_item + materialized print views,
//   loaded by scripts/extract_printformat.sh). Oracle = INDEPENDENT live idempiere_test BASE TABLES:
//   c_invoiceline rows (the detail tree), c_invoicetax (the 999999 tax rows) and c_invoice.grandtotal — a stored
//   total WRITTEN BY REAL iDEMPIERE (MInvoice), so the IsSummarized break == grandtotal is a fold-vs-independent-
//   product claim, the strong class.
// NON-INVENT: every row READ from the bundle or live base tables. No Date.now/Math.random. Integer-cents diff.
//   §-log first — READ build/erp/poc_printformat.log before concluding.
// Run:  node scripts/poc_printformat.js 2>&1 | tee build/erp/poc_printformat.log
'use strict';
var path = require('path'), cp = require('child_process');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var CORE = require(path.join(ERP, 'report_overlay.js'));
var Database = require('better-sqlite3');
var GB = new Database(path.join(ERP, 'glassbowl_data.db'), { readonly: true });   // the served bundle

var fails = 0;
function ok(cond, msg) { if (!cond) { fails++; console.log('  ✗ FAIL: ' + msg); } else { console.log('  ✓ ' + msg); } }
function psql(sql) {
  return cp.execSync('docker exec -i postgres psql -U adempiere -d idempiere_test -t -A -F"|" -c ' + JSON.stringify(sql), { encoding: 'utf8' }).trim();
}
function cents(x) { return (x == null || x === '') ? 0 : Math.round(Number(x) * 100); }

console.log('=== §PRINTFORMAT witness (W-PRINTFORMAT) — ' + new Date().toISOString() + ' ===');
console.log('fold = report_overlay.foldPrint over the BUNDLE (format 120 -> P -> 121); oracle = LIVE idempiere_test BASE tables\n');

// ── inputs from the bundle ALONE (the browser path) ─────────────────────────
var formatsById = {};
GB.prepare('SELECT ad_printformat_id, name, tablename, isactive FROM ad_printformat').all()
  .forEach(function (f) { formatsById[f.ad_printformat_id] = f; });
var itemsByFormat = {};
GB.prepare('SELECT * FROM ad_printformatitem WHERE isactive=\'Y\'').all()
  .forEach(function (i) { (itemsByFormat[i.ad_printformat_id] = itemsByFormat[i.ad_printformat_id] || []).push(i); });
console.log('§PRINTFORMAT-FACTS formats=' + Object.keys(formatsById).length +
  ' P-items=' + GB.prepare("SELECT count(*) n FROM ad_printformatitem WHERE printformattype='P' AND isactive='Y'").get().n +
  ' header_v=' + GB.prepare('SELECT count(*) n FROM c_invoice_header_v').get().n +
  ' linetax_v=' + GB.prepare('SELECT count(*) n FROM c_invoice_linetax_v').get().n + '   [all from the bundle]');

// host row source: the materialized print views (PG evaluated them at extract time; the verb only interprets).
function rowsOf(format, link) {
  var t = String(format.tablename || '').toLowerCase();
  if (t !== 'c_invoice_header_v' && t !== 'c_invoice_linetax_v') return [];     // honest: only the extracted views
  var sql = 'SELECT * FROM ' + t + (link ? ' WHERE ' + link.column + '=?' : '');
  return link ? GB.prepare(sql).all(link.value) : GB.prepare(sql).all();
}

var F120 = formatsById[120], F121 = formatsById[121];
ok(!!F120 && !!F121, 'bundle carries format 120 "' + (F120 && F120.name) + '" + child 121 "' + (F121 && F121.name) + '"');
var pItem = (itemsByFormat[120] || []).filter(function (i) { return i.printformattype === 'P'; })[0];
ok(!!pItem && pItem.ad_printformatchild_id === 121 && String(pItem.columnname).toLowerCase() === 'c_invoice_id',
  "the 'P' item links 120 -> 121 via C_Invoice_ID (the master-detail metadata, not hardcoded)");

// ── oracle: the invoices, lines and taxes from LIVE BASE TABLES (independent of the views we folded) ──
var oInvoices = psql('SELECT c_invoice_id, documentno, grandtotal, totallines FROM c_invoice WHERE ad_client_id=11 ORDER BY c_invoice_id')
  .split('\n').filter(Boolean).map(function (s) { var p = s.split('|'); return { id: +p[0], docno: p[1], grandtotal: p[2], totallines: p[3] }; });
console.log('§PRINTFORMAT-ORACLE invoices=' + oInvoices.length + ' (live c_invoice, client 11)\n');

console.log('[ISSUE] foldPrint(120) reproduces the PrintData master/detail row tree for EVERY seed invoice (rows+order+amounts)');
var totalCells = 0, maxDiff = 0;
oInvoices.forEach(function (inv) {
  // fold ONE document, the iDempiere way (print job for Record_ID = this invoice).
  var m = CORE.foldPrint(F120, formatsById, itemsByFormat, rowsOf, { column: 'c_invoice_id', value: inv.id });
  ok(m.rows.length === 1, 'invoice ' + inv.docno + ': ONE master row (header_v)');
  var master = m.rows[0];
  ok(String(master.cells.documentno) === inv.docno, 'invoice ' + inv.docno + ': master DocumentNo == live c_invoice');
  var dGT = Math.abs(cents(master.cells.grandtotal) - cents(inv.grandtotal));
  if (dGT > maxDiff) maxDiff = dGT; totalCells++;
  ok(dGT === 0, 'invoice ' + inv.docno + ': master GrandTotal == live (' + inv.grandtotal + ')');

  // the detail tree (the 'P' recursion): line rows + the 999998 separator + the 999999 tax rows.
  var det = master.children[0] && master.children[0].manifest;
  ok(!!det && det.format.id === 121, 'invoice ' + inv.docno + ": 'P' child folded with format 121");
  if (!det) return;
  var oLines = psql('SELECT line, linenetamt FROM c_invoiceline WHERE c_invoice_id=' + inv.id + ' ORDER BY line')
    .split('\n').filter(Boolean).map(function (s) { var p = s.split('|'); return { line: +p[0], amt: p[1] }; });
  var oTax = psql('SELECT taxamt FROM c_invoicetax WHERE c_invoice_id=' + inv.id).split('\n').filter(Boolean);
  var expectRows = oLines.length + 1 + oTax.length;                     // lines + 999998 + tax rows (the view tree)
  ok(det.rows.length === expectRows, 'invoice ' + inv.docno + ': detail rows=' + det.rows.length + ' == lines(' + oLines.length + ')+1+tax(' + oTax.length + ')');
  // order: Line ascending (the isorderby/sortno item) — and the REAL line rows match base-table line+amount.
  var sortedAsc = det.rows.every(function (r, i) { return i === 0 || Number(det.rows[i - 1].cells.line) <= Number(r.cells.line); });
  ok(sortedAsc, 'invoice ' + inv.docno + ': detail ordered by Line asc (isorderby/sortno interpreted)');
  oLines.forEach(function (ol, i) {
    var fr = det.rows[i];                                                // ascending order => first N are the real lines
    var d = Math.abs(cents(fr && fr.cells.linenetamt) - cents(ol.amt));
    totalCells++; if (d > maxDiff) maxDiff = d;
    if (d !== 0 || Number(fr.cells.line) !== ol.line) { fails++; console.log('  ✗ FAIL: invoice ' + inv.docno + ' line ' + ol.line + ' fold=' + (fr && fr.cells.linenetamt) + ' oracle=' + ol.amt); }
  });
  // tax rows (999999): linenetamt carries TaxAmt — diff vs live c_invoicetax.
  var taxRows = det.rows.filter(function (r) { return Number(r.cells.line) === 999999; });
  var oTaxC = oTax.map(function (t) { return cents(t); }).sort(function (a, b) { return a - b; });
  var fTaxC = taxRows.map(function (r) { return cents(r.cells.linenetamt); }).sort(function (a, b) { return a - b; });
  var taxOk = oTaxC.length === fTaxC.length && oTaxC.every(function (v, i) { return v === fTaxC[i]; });
  totalCells += oTaxC.length; if (!taxOk && oTaxC.length) maxDiff = Math.max(maxDiff, 1);
  ok(taxOk, 'invoice ' + inv.docno + ': tax rows (999999) == live c_invoicetax (' + oTax.join(',') + ')');

  // THE BREAK: IsSummarized(LineNetAmt) grand function row == c_invoice.grandtotal — a total REAL iDempiere wrote.
  var sum = det.breaks.total && det.breaks.total.linenetamt && det.breaks.total.linenetamt.sum;
  var dS = Math.abs(cents(sum) - cents(inv.grandtotal));
  totalCells++; if (dS > maxDiff) maxDiff = dS;
  ok(dS === 0, 'invoice ' + inv.docno + ': Σ break(LineNetAmt)=' + sum + ' == live GrandTotal=' + inv.grandtotal + ' (independent product output)');
});
console.log('§PRINTFORMAT invoices=' + oInvoices.length + ' diffedCells=' + totalCells + ' maxDiff=' + maxDiff + 'c oracle=idempiere_test(base tables + stored grandtotal)');

// ── §FALSIFIER A: drop the 'P' item -> the detail section disappears (recursion is load-bearing) ──
console.log("\n[§FALSIFIER A] drop the 'P' item -> detail tree lost");
(function () {
  var items120 = (itemsByFormat[120] || []).filter(function (i) { return i.printformattype !== 'P'; });
  var tampered = {}; Object.keys(itemsByFormat).forEach(function (k) { tampered[k] = itemsByFormat[k]; });
  tampered[120] = items120;
  var m = CORE.foldPrint(F120, formatsById, tampered, rowsOf, { column: 'c_invoice_id', value: oInvoices[0].id });
  var nChild = m.rows[0] ? m.rows[0].children.length : -1;
  console.log("§PRINTFORMAT-FALSIFIER-A dropped 'P' item -> children=" + nChild + ' (was 1)');
  ok(nChild === 0, "§FALSIFIER A fired: no 'P' item => no detail section (the tree comes FROM the metadata)");
})();

// ── §FALSIFIER B: perturb ONE detail amount by 1c -> the IsSummarized break diverges from live GrandTotal ──
console.log('\n[§FALSIFIER B] +1c on one detail row -> break total diverges from the stored GrandTotal');
(function () {
  var inv = oInvoices.filter(function (i) { return cents(i.grandtotal) > 0; })[0];
  var bumped = false;
  function tamperedRows(format, link) {
    var rows = rowsOf(format, link);
    if (String(format.tablename).toLowerCase() === 'c_invoice_linetax_v' && !bumped) {
      rows = rows.map(function (r) { return Object.assign({}, r); });
      for (var i = 0; i < rows.length; i++) {
        if (rows[i].linenetamt != null && rows[i].linenetamt !== '') {
          rows[i].linenetamt = String((cents(rows[i].linenetamt) + 1) / 100); bumped = true; break;
        }
      }
    }
    return rows;
  }
  var m = CORE.foldPrint(F120, formatsById, itemsByFormat, tamperedRows, { column: 'c_invoice_id', value: inv.id });
  var det = m.rows[0].children[0].manifest;
  var sum = det.breaks.total.linenetamt.sum;
  var d = Math.abs(cents(sum) - cents(inv.grandtotal));
  console.log('§PRINTFORMAT-FALSIFIER-B invoice ' + inv.docno + ' +1c on one row -> Σ=' + sum + ' vs GrandTotal=' + inv.grandtotal + ' diff=' + d + 'c');
  ok(d === 1, '§FALSIFIER B fired: a 1c mis-sum is DETECTED by the break-vs-grandtotal diff (the diff has teeth)');
})();

// ── §FALSIFIER C: flatten the recursion (rowsOf ignores the link) -> details leak across invoices ──
console.log('\n[§FALSIFIER C] ignore the master link -> the per-invoice detail tree breaks (recursion is real)');
(function () {
  function flatRows(format, link) {
    if (String(format.tablename).toLowerCase() === 'c_invoice_linetax_v') return rowsOf(format, null);  // link dropped
    return rowsOf(format, link);
  }
  var inv = oInvoices[0];
  var m = CORE.foldPrint(F120, formatsById, itemsByFormat, flatRows, { column: 'c_invoice_id', value: inv.id });
  var det = m.rows[0].children[0].manifest;
  var all = GB.prepare('SELECT count(*) n FROM c_invoice_linetax_v').get().n;
  console.log('§PRINTFORMAT-FALSIFIER-C unlinked detail rows=' + det.rows.length + ' (scoped was lines+2; whole view=' + all + ')');
  ok(det.rows.length === all, '§FALSIFIER C fired: without the link the section is the WHOLE view — master scoping is load-bearing');
})();

console.log('\n=== RESULT: ' + (fails === 0 ? 'ALL PASS' : fails + ' FAIL') + ' ===');
process.exit(fails === 0 ? 0 : 1);
