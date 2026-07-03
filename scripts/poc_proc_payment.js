// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-PROC-PAYMENT is ✅ DONE in AD_PROCESS_FOLD_LANE.md §P2-tail-leg5.
// SCOPE: W-PROC-PAYMENT — AD_Process 313 ("Rpt C_Payment", "Payment Print", blank classname, isReport=Y) dispatched
//   through build/erp/ad_process.js resolves to the report:c_payment key and folds a C_Payment → a VOUCHER via a
//   NEW report_overlay.foldVoucher (KIND 1, header-only). A payment is the first HEADER-ONLY document in the lane:
//   it has NO line table — its amount is PayAmt on the header — so foldReceipt (subtotal=Σline) is the WRONG fold
//   (an unapplied payment with 0 lines would yield tax=PayAmt). foldVoucher folds the real header money columns
//   exactly (BigDecimal), never a synthesized/fabricated line.
//   Proves: (a) the blank classname resolves to report:c_payment, registered;
//   (b) a real C_Payment (100, DocumentNo 400000, PayAmt 98.50) folds to a voucher: docno + date + partner +
//   per-amount breakdown (PayAmt/Discount/Write-off/Over-Under/Tax) + total=PayAmt + headerOnly=true + NO `lines`
//   key (so the host's line-table branch is skipped);
//   (c) each folded amount == its real C_Payment column EXACTLY (BigDecimal, independent re-derivation);
//   (d) the ALLOCATION INVARIANT — PayAmt + Discount + Write-off − Over/Under == the settled C_Invoice's GrandTotal
//   (C_Payment 100 → C_Invoice 101: 98.50 + 1.90 + 0.30 − 0.00 == 100.70). A real double-entry property of an AR
//   receipt, RE-DERIVED from the folded amounts vs the invoice row, never asserted — proves the voucher is honest;
//   (e) a §FALSIFIER (bend PayAmt +5.00) shifts BOTH the folded PayAmt and the total by exactly +5.00;
//   (f) the GUARD — 317 "C_Payment NotAllocated", 318 "RV_Payment", 146 "RV_UnReconciled Payments" (report-VIEWs,
//   isReport=Y) + 149 "C_Payment_Process" (isReport=N) stay absent-handler. The match is SPECIFIC (`rpt c_payment\b`
//   | "payment print"), NOT a bare `c_payment`, so "C_Payment NotAllocated" is NOT mis-routed.
// NON-INVENT: C_Payment 100 + C_Invoice 101 are REAL ad_full.db rows. A payment's amount is its header PayAmt.
// §-log first — READ build/erp/poc_proc_payment.log before concluding (exit code ≠ evidence).
// Run:  node scripts/poc_proc_payment.js 2>&1 | tee build/erp/poc_proc_payment.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var P  = require(path.join(ERP, 'ad_process.js'));
var RC = require(path.join(ERP, 'report_overlay.js'));
var DB_PATH = path.join(ERP, 'ad_full.db');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log((ok ? '🟢' : '🔴') + ' ' + label + (detail ? '  — ' + detail : '')); }

console.log('═══ W-PROC-PAYMENT — Rpt C_Payment (AD_Process 313) folds a C_Payment → voucher via foldVoucher (header-only) ═══\n');

var db = new Database(DB_PATH, { readonly: true });
P.installDefaultHandlers(RC);
var PAY_ID = 100;                                            // a real payment (DocumentNo 400000, PayAmt 98.50, settles C_Invoice 101)

function firstId(table, pk) { var r = db.prepare('SELECT ' + pk + ' AS id FROM ' + table + ' ORDER BY ' + pk + ' LIMIT 1').get(); return r && r.id; }
function ctx(hdrMut) {
  return {
    fetchHeader: function (key, info) {
      var map = RC.REPORT_MAP[key]; var id = (info && info.Record_ID != null) ? info.Record_ID : firstId(map.key, map.pk);
      if (id == null) return null;
      var h = db.prepare('SELECT * FROM ' + map.key + ' WHERE ' + map.pk + '=?').get(id) || null;
      return (h && hdrMut) ? Object.assign({}, h, hdrMut(h)) : h;
    },
    fetchLines: function () { return []; },                 // a payment is header-only — no line table
    names: function (key, hdr) {
      var nm = { products: {} };
      if (hdr && hdr.c_bpartner_id != null) { var bp = db.prepare('SELECT name FROM c_bpartner WHERE c_bpartner_id=?').get(hdr.c_bpartner_id); nm.partner = bp && bp.name; }
      return nm;
    }
  };
}

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 1 · §PAYMENT-DISPATCH — 313 (blank classname) resolves to report:c_payment, registered.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('— §PAYMENT-DISPATCH (real AD_Process 313 row → blank classname → report:c_payment) —');
var proc = P.readProcess(db, 313);
var resolved = P.resolveClassname(proc);
console.log('§PAYMENT-DISPATCH proc=313 value=' + proc.value + ' name="' + proc.name + '" classname="' + proc.classname + '" isReport=' + proc.isReport + ' resolved=' + resolved + ' hasHandler=' + P.hasHandler(resolved));
verdict(proc.classname === '' && proc.isReport === true && resolved === 'report:c_payment' && P.hasHandler('report:c_payment'),
        'AD_Process 313 = Rpt C_Payment (blank classname) resolves to the registered report:c_payment handler', 'value=' + proc.value);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 2 · §PAYMENT-FOLD — fold C_Payment 100 → a voucher; header-only (no lines), amounts from real columns.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §PAYMENT-FOLD (C_Payment 100 → voucher: header amounts via foldVoucher, NO lines) —');
var rawHdr = db.prepare('SELECT * FROM c_payment WHERE c_payment_id=?').get(PAY_ID);
var r = P.dispatch(db, { AD_Process_ID: 313, Record_ID: PAY_ID, params: {} }, ctx());
var rec = r.result || {};
var amtOf = function (col) { var a = (rec.amounts || []).filter(function (x) { return x.col === col; })[0]; return a ? a.value : undefined; };
console.log('§PAYMENT-FOLD dispatched=' + r.dispatched + ' classname=' + r.classname + ' kind=' + r.kind + ' rows=' + r.rows +
            ' docno=' + rec.docno + ' date=' + rec.date + ' partner=' + rec.partner + ' headerOnly=' + rec.headerOnly +
            ' hasLinesKey=' + ('lines' in rec) + ' total=' + rec.total + ' amounts={pay=' + amtOf('payamt') + ' disc=' + amtOf('discountamt') + ' woff=' + amtOf('writeoffamt') + ' tax=' + amtOf('taxamt') + '}');
verdict(r.dispatched === true && r.classname === 'report:c_payment' && rec.foldsFrom === 'bundle' &&
        rec.headerOnly === true && !('lines' in rec) && rec.financial === true &&
        rec.docno === rawHdr.documentno && rec.partner === 'Standard' && rec.total === '98.50',
        'C_Payment 100 folds to a header-only VOUCHER (docno=DocumentNo, partner resolved, total=PayAmt, NO lines key)',
        'docno=' + rec.docno + ' total=' + rec.total + ' headerOnly=' + rec.headerOnly + ' hasLinesKey=' + ('lines' in rec));

// ORACLE part 1: each folded amount == its real C_Payment column EXACTLY (BigDecimal display, 2dp string).
var BD = require(path.join(ERP, 'bigdecimal.js'));
function m2(v) { return BD.of(String(v == null || v === '' ? 0 : v)).setScale(2, BD.RoundingMode.HALF_UP).toString(); }
var colMism = 0;
[['payamt'], ['discountamt'], ['writeoffamt'], ['overunderamt'], ['taxamt']].forEach(function (c) {
  var col = c[0], got = amtOf(col), exp = m2(rawHdr[col]);
  if (got !== exp) { colMism++; console.log('   ⚠ FINDING ' + col + ' folded=' + got + ' expected=' + exp); }
});
console.log('§PAYMENT-FOLD per-amount fold == real column (2dp): mismatches=' + colMism);
verdict(colMism === 0, 'every folded amount == its real C_Payment column exactly (BigDecimal 2dp, no fabricated value)', 'amounts=' + rec.amounts.length);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 3 · §PAYMENT-ALLOCATION-INVARIANT — PayAmt + Discount + Write-off − Over/Under == settled C_Invoice GrandTotal.
//     A real iDempiere AR-receipt property, RE-DERIVED from the folded amounts vs the invoice row (not asserted).
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §PAYMENT-ALLOCATION-INVARIANT (folded PayAmt+Discount+Write-off−Over/Under == settled invoice GrandTotal) —');
var inv = db.prepare('SELECT c_invoice_id, documentno, grandtotal FROM c_invoice WHERE c_invoice_id=?').get(rawHdr.c_invoice_id);
var appliedB = BD.of(amtOf('payamt')).add(BD.of(amtOf('discountamt'))).add(BD.of(amtOf('writeoffamt'))).subtract(BD.of(amtOf('overunderamt')));
var applied = appliedB.setScale(2, BD.RoundingMode.HALF_UP).toString();
var invTotal = m2(inv.grandtotal);
console.log('§PAYMENT-ALLOCATION-INVARIANT applied=' + applied + ' (PayAmt ' + amtOf('payamt') + ' + Disc ' + amtOf('discountamt') + ' + WOff ' + amtOf('writeoffamt') + ' − OverUnder ' + amtOf('overunderamt') + ') vs C_Invoice ' + inv.c_invoice_id + ' GrandTotal=' + invTotal);
verdict(applied === invTotal && applied === '100.70',
        'the voucher amounts settle the invoice to the cent (98.50 + 1.90 + 0.30 − 0.00 == 100.70 = GrandTotal) — re-derived, honest', 'applied=' + applied);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 4 · §PAYMENT-FALSIFIER — bend PayAmt +5.00 → folded PayAmt AND total both shift by exactly +5.00.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §PAYMENT-FALSIFIER (bend PayAmt +5.00 → folded PayAmt and total shift by exactly +5.00) —');
var rBent = P.dispatch(db, { AD_Process_ID: 313, Record_ID: PAY_ID, params: {} }, ctx(function (h) { return { payamt: BD.of(String(h.payamt)).add(BD.of('5')).toString() }; }));
var bAmt = (rBent.result.amounts.filter(function (x) { return x.col === 'payamt'; })[0] || {}).value;
var bTot = rBent.result.total;
console.log('§PAYMENT-FALSIFIER bent PayAmt=' + bAmt + ' total=' + bTot + ' (baseline PayAmt=' + amtOf('payamt') + ' total=' + rec.total + ')');
verdict(bAmt === '103.50' && bTot === '103.50' && bAmt !== amtOf('payamt'),
        'bending PayAmt shifts the folded PayAmt and the voucher total exactly +5.00 (the fold reads the header, never fabricated)', 'Δ = +5.00');

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 5 · §PAYMENT-GUARD — 317/318/146 (RV_* payment report-VIEWs) + 149 (C_Payment_Process, isReport=N) stay ABSENT.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §PAYMENT-GUARD (RV_* payment report-views + C_Payment_Process are NOT mis-routed to report:c_payment) —');
var guardIds = [317, 318, 146, 149];
var leaked = [];
guardIds.forEach(function (id) {
  var pr; try { pr = P.readProcess(db, id); } catch (e) { return; }
  var rv = P.resolveClassname(pr);
  console.log('§PAYMENT-GUARD proc=' + id + ' value="' + pr.value.trim() + '" name="' + pr.name.trim() + '" isReport=' + pr.isReport + ' resolved="' + rv + '"');
  if (rv === 'report:c_payment') leaked.push(id);
});
verdict(leaked.length === 0,
        'no RV_* payment report-view / C_Payment_Process is mis-routed to report:c_payment (match is "rpt c_payment\\b"|"payment print", not bare c_payment)', 'leaked=[' + leaked.join(',') + ']');

// 317 "C_Payment NotAllocated" is a report-VIEW (no foldVoucher route) → an honest non-fold, NEVER a fabricated voucher.
var r317 = P.dispatch(db, { AD_Process_ID: 317, params: {} }, ctx());
console.log('§PAYMENT-GUARD 317 C_Payment NotAllocated dispatched=' + r317.dispatched + ' reason=' + r317.reason + ' result=' + (r317.result ? 'present' : 'none'));
verdict(r317.dispatched === false && (r317.reason === 'absent-handler' || r317.reason === 'param-validation-failed') && !r317.result,
        'C_Payment NotAllocated (317) is an honest non-fold, NEVER a fabricated voucher', 'reason=' + r317.reason);

db.close();
console.log('\n' + (fails ? '🔴 ' + fails + ' check(s) FAILED — W-PROC-PAYMENT NOT proven' : '═══ ALL PASS — Rpt C_Payment folds a payment → header-only voucher via foldVoucher; amounts settle the invoice to the cent (KIND-1, first header-only fold) — W-PROC-PAYMENT ═══'));
process.exit(fails ? 1 : 0);
