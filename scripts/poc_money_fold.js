// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_money_fold.js — W-MONEY-FOLD : the read-side financial folds compute money EXACTLY (== golden),
//   not with raw Number + round2.  Implementing ENGINE_FULL_ERP_ISSUES.md §I-L (NEXT item 7).
//
// ISSUE THIS WITNESS PROVES (names it, per CLAUDE.md "tests expose issues"):
//   build/erp/report_overlay.js folded Trial-Balance / Receipt / P&L money with raw Number `+`/`reduce`
//   and round2(n)=Math.round((n+EPSILON)*100)/100.  That matches java.math.BigDecimal ONLY for positive,
//   sub-$9e13, clean-2dp data.  It DIVERGES from golden on the values double-entry is built from:
//     · SIGNED sub-cent  — Math.round rounds .5 toward +inf; Java HALF_UP rounds away-from-zero
//                          (balance dr-cr, P&L net cr-dr, tax = total - subtotal).
//     · MAGNITUDE > 2^53 cents (~$9e13) — float drops cents on large clean-2dp totals.
//   The fix routes every fold accumulation through BigDecimal (== java.math.BigDecimal, PROVEN 446/446 by
//   scripts/test_bigdecimal_conformance.js) and carries the money leaf as an exact 2dp STRING.
//
// PROOF SHAPE per vector:  (1) the INLINED OLD raw fold (verbatim pre-fix code) DIVERGES from golden — the
//   bug was real, not invented;  (2) the LIVE fold (require build/erp/report_overlay.js) == golden;
//   golden = independent BigDecimal string math (the trustworthy oracle).  §-logged; read the log.

'use strict';
var path = require('path');
var DIR = path.join(__dirname, '..', 'build', 'erp');
var BigDecimal = require(path.join(DIR, 'bigdecimal.js'));
var CORE = require(path.join(DIR, 'report_overlay.js'));   // the LIVE fold under audit
var HALF_UP = BigDecimal.RoundingMode.HALF_UP;
function bd(v) { return (v == null || v === '') ? BigDecimal.ZERO : BigDecimal.of(String(v)); }
function g2(b) { return b.setScale(2, HALF_UP).toString(); }   // golden 2dp string

// ── the OLD raw-Number folds — VERBATIM pre-fix code (the thing under audit), so the divergence is the
//    real historical behaviour, not a strawman. (round2 + Number reduce, exactly as it shipped.) ──────────
function round2(n) { return Math.round((n + Number.EPSILON) * 100) / 100; }
function oldFoldTrialBalance(factRows) {
  var by = {};
  (factRows || []).forEach(function (r) {
    var a = r.account_id; if (!by[a]) by[a] = { account_id: a, dr: 0, cr: 0 };
    by[a].dr += Number(r.amtacctdr) || 0; by[a].cr += Number(r.amtacctcr) || 0;
  });
  var lines = Object.keys(by).map(function (a) {
    var b = by[a]; return { account_id: +a, dr: round2(b.dr), cr: round2(b.cr), balance: round2(b.dr - b.cr) };
  });
  var totalDr = round2(lines.reduce(function (s, l) { return s + l.dr; }, 0));
  var totalCr = round2(lines.reduce(function (s, l) { return s + l.cr; }, 0));
  return { lines: lines, totalDr: totalDr, totalCr: totalCr };
}
function oldFoldReceiptTax(lines, total, amtCol) {
  var subtotal = round2((lines || []).reduce(function (s, r) { return s + (Number(r[amtCol]) || 0); }, 0));
  var subRaw = (lines || []).reduce(function (s, r) { return s + (Number(r[amtCol]) || 0); }, 0);
  return { subtotal: subtotal, tax: round2(Number(total) - subRaw) };   // tax off the RAW float sum, as shipped
}
function oldFoldPnLNet(rows) {   // single revenue account, net = Σ(cr - dr)
  var net = 0;
  (rows || []).forEach(function (r) { net += (Number(r.amtacctcr) || 0) - (Number(r.amtacctdr) || 0); });
  return round2(net);
}

// ── helpers to compare a LIVE/OLD field (Number or String) against a golden 2dp string ──────────────────
function fx2(v) { return (v == null) ? 'null' : Number(v).toFixed(2); }   // normalise OLD Number to 2dp str
var pass = true, checks = 0;
function expect(label, gotStr, goldStr, mustEqual) {
  checks++;
  var ok = (gotStr === goldStr) === mustEqual;
  if (!ok) pass = false;
  console.log('   ' + (ok ? 'OK ' : 'XX ') + label + ': got=' + gotStr + ' gold=' + goldStr +
              ' (' + (mustEqual ? 'expect ==' : 'expect DIVERGE') + ')' + (ok ? '' : '  <-- FAIL'));
  return ok;
}

console.log('═══ POC-MONEY-FOLD — §I-L read-side money folds: exact == golden, not raw Number+round2 ═══');
console.log('issue=ENGINE_FULL_ERP_ISSUES.md §I-L  fold=build/erp/report_overlay.js  oracle=bigdecimal.js (==java.math.BigDecimal, proven)');
console.log('§MONEY primitive foldTrialBalance=' + (typeof CORE.foldTrialBalance) +
            ' foldReceipt=' + (typeof CORE.foldReceipt) + ' foldPnL=' + (typeof CORE.foldPnL) +
            ' money-leaf=string(' + (typeof g2(bd('1'))) + ')');

// ════════════════════════════════════════════════════════════════════════════════════════════════════
// VECTOR S — SIGNED sub-cent (the half-cent trap): balance dr-cr, P&L net, tax remainder.
//   Sub-cent inputs arise from conversion/tax/price×qty remainders carried into the bundle.
// ════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n§MONEY VECTOR-S signed sub-cent (Math.round toward +inf vs Java HALF_UP away-from-zero):');

// S1 — TB line balance: dr=100.50, cr=100.505 → exact balance = -0.01; old round2 gives 0.00 (masks).
var sTB = [{ account_id: 7, amtacctdr: '100.50', amtacctcr: '100.505' }];
var goldBalS = g2(bd('100.50').subtract(bd('100.505')));                 // -0.01
var liveS = CORE.foldTrialBalance(sTB, {});
var oldS = oldFoldTrialBalance(sTB);
expect('S1 OLD  TB balance', fx2(oldS.lines[0].balance), goldBalS, false);   // diverges (0.00)
expect('S1 LIVE TB balance', String(liveS.lines[0].balance),  goldBalS, true);

// S2 — Receipt tax = total - Σ(3dp lines): lines 33.335×3 = 100.005, total 100.00 → tax exact -0.01; old 0.00.
var sLines = [{ linenetamt: '33.335' }, { linenetamt: '33.335' }, { linenetamt: '33.335' }];
var goldTaxS = g2(bd('100.00').subtract(bd('33.335').add(bd('33.335')).add(bd('33.335'))));   // -0.01
var liveRec = CORE.foldReceipt(CORE.REPORT_MAP.c_order,
  { c_order_id: 1, documentno: 'SO-S', grandtotal: '100.00' }, sLines, {});
var oldRec = oldFoldReceiptTax(sLines, '100.00', 'linenetamt');
expect('S2 OLD  receipt tax', fx2(oldRec.tax),     goldTaxS, false);
expect('S2 LIVE receipt tax', String(liveRec.tax), goldTaxS, true);

// S3 — P&L net = Σ(cr - dr) landing on a negative half-cent. Use the MEASURED-divergent pair (100.50/100.505,
//   same float subtraction as S1) — NOT 10.00/10.005, whose float noise rounds correctly by luck (so it would
//   not prove the bug; the witness caught that and I corrected the vector, never the assertion).
var sPnl = [{ account_id: 4, amtacctcr: '100.50', amtacctdr: '100.505' }];
var acctR = { 4: { value: '4000', name: 'Revenue', accounttype: 'R' } };
var goldNetS = g2(bd('100.50').subtract(bd('100.505')));                 // -0.01
var livePnl = CORE.foldPnL(sPnl, acctR);
var oldNetS = oldFoldPnLNet(sPnl);
expect('S3 OLD  P&L net',  fx2(oldNetS),               goldNetS, false);
expect('S3 LIVE P&L net',  String(livePnl.lines[0].net), goldNetS, true);

// ════════════════════════════════════════════════════════════════════════════════════════════════════
// VECTOR M — MAGNITUDE > 2^53 cents (~$9e13): clean 2dp, the case that bites a NORMAL ledger.
// ════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n§MONEY VECTOR-M magnitude > 2^53 cents (float drops the cent on large clean-2dp totals):');
// two debit rows on one account: 99999999999999.99 + 0.02 → exact 100000000000000.01; float → ...000.00
var mTB = [{ account_id: 9, amtacctdr: '99999999999999.99', amtacctcr: '0' },
           { account_id: 9, amtacctdr: '0.02',              amtacctcr: '0' }];
var goldDrM = g2(bd('99999999999999.99').add(bd('0.02')));               // 100000000000000.01
var liveM = CORE.foldTrialBalance(mTB, {});
var oldM = oldFoldTrialBalance(mTB);
expect('M1 OLD  TB account dr', fx2(oldM.lines[0].dr),   goldDrM, false);  // float lost the cent
expect('M1 LIVE TB account dr', String(liveM.lines[0].dr), goldDrM, true);
expect('M1 LIVE TB total  dr',  String(liveM.totalDr),     goldDrM, true);

// ════════════════════════════════════════════════════════════════════════════════════════════════════
// VECTOR B — `balanced` is EXACT (isZero), never a float compare: a balanced 3dp ledger stays balanced,
//   and an imbalance is NOT masked.  ΣDr == ΣCr is the trial balance's whole job.
// ════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n§MONEY VECTOR-B exact balanced (no float-masked imbalance):');
// balanced: Dr 10.005 == Cr (10.00 + 0.005) exactly → balanced=true (exact).
var bBal = [{ account_id: 1, amtacctdr: '10.005', amtacctcr: '0' },
            { account_id: 2, amtacctdr: '0', amtacctcr: '10.00' },
            { account_id: 3, amtacctdr: '0', amtacctcr: '0.005' }];
var liveBal = CORE.foldTrialBalance(bBal, {});
checks++; if (liveBal.balanced !== true) { pass = false; console.log('   XX B1 LIVE balanced=' + liveBal.balanced + ' expect true  <-- FAIL'); }
else console.log('   OK  B1 LIVE balanced=true maxDiffCents=' + liveBal.maxDiffCents + ' (exact isZero, ΣDr==ΣCr)');
// imbalance by exactly 1 cent → NOT masked: balanced=false, maxDiffCents=1.
var bImb = [{ account_id: 1, amtacctdr: '10.01', amtacctcr: '0' }, { account_id: 2, amtacctdr: '0', amtacctcr: '10.00' }];
var liveImb = CORE.foldTrialBalance(bImb, {});
checks++; if (liveImb.balanced !== false || liveImb.maxDiffCents !== 1) { pass = false; console.log('   XX B2 LIVE balanced=' + liveImb.balanced + ' maxDiffCents=' + liveImb.maxDiffCents + ' expect false/1  <-- FAIL'); }
else console.log('   OK  B2 LIVE balanced=false maxDiffCents=1 (1c imbalance surfaced, not masked)');

// ── verdict ─────────────────────────────────────────────────────────────────────────────────────────
console.log('\n§MONEY-FOLD checks=' + checks + ' allPass=' + pass);
if (pass) {
  console.log('§MONEY-FOLD PASS — §I-L: the read-side folds compute money EXACTLY. The OLD raw-Number+round2 ' +
    'fold DIVERGES from java.math.BigDecimal on signed sub-cent (S: balance/tax/net round the wrong way) and ' +
    'on magnitude > 2^53 cents (M: float drops the cent); the LIVE BigDecimal folds reproduce the proven ' +
    'golden exactly and report `balanced` via exact isZero (B: no float-masked imbalance). No value is ' +
    'hand-authored; every divergence is MEASURED, every golden is BigDecimal string math.');
  process.exit(0);
} else {
  console.log('§MONEY-FOLD FAIL — a fold did not match golden (or an OLD case unexpectedly matched). Read above.');
  process.exit(1);
}
