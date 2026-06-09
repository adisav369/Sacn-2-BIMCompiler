#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_reverse.js — W-FOLD-REVERSE (FOLD_MODEL_LOGIC.md §"REMAINING TAIL" / §"ENACTMENT METHOD", the
//   void / reverseCorrect / reverseAccrual DocAction family — ORACLE-ANCHORED tier).
//
// SPEC (Doc.reverseCorrectIt / reverseAccrualIt): completing a reversal emits a posting that ANNIHILATES the
//   original document's posting — every Dr leg becomes a Cr (and vice-versa) on the SAME accounts at the SAME
//   amounts (iDempiere MFactReversal: Fact.reverse swaps the FactLine sides), and drives the document FSM
//   CO → RE. reverseAccrual is the identical negation booked in the NEXT period (the reversal DATE is the only
//   delta). void on a posted doc = reverseCorrect + status; on an unposted doc = status only.
//
// THE ENACTMENT (why this is no longer "data-blocked"): GardenWorld never POSTED a reversal, so there is no
//   reversal fact_acct to copy. We don't need one — our engine ENACTS the DocAction. The oracle is REAL-ANCHORED,
//   NOT tautological: we (1) RE-DERIVE the document's FORWARD posting from source via post_resolver — the already
//   proven path (W-FOLD-PAYMENT / W-FOLD-AP-INVOICE) — and re-confirm it == the REAL fact_acct (the anchor holds);
//   (2) feed that forward posting to erp_engine.reversePosting (which NEVER reads the books); (3) prove the engine's
//   reversal, summed with the REAL original fact_acct, NETS TO ZERO in every account — iDempiere's reverseCorrect
//   contract (the books return to their pre-document state). The transform is iDempiere's; the anchor is iDempiere's
//   real client-11 data; nothing is invented. §FALSIFIER-A same-sign (no swap) → the doc DOUBLES, not annihilates;
//   §FALSIFIER-B wrong reversal account → annihilation fails on that account.
//
// SANDBOX DISCIPLINE: the FSM status change is enacted into a COPY of glassbowl_data.db — the real client-11 rows
//   stay the read-only anchor (asserted unchanged). Deterministic: integer cents, recorded ids, no Date.now/random.
//   READ build/erp/poc_reverse.log — exit code is not evidence.
//
// HONEST SCOPE: reverseCorrect is FULLY oracle-anchored here. reverseAccrual's POSTING is the identical negation
//   (same anchor) but its next-period DATE cannot be EXTRACTED — this fact_acct extract carries NO c_period /
//   calendar table — so the date-shift is named-deferred (the posting equivalence is proven; only the booked date
//   is unverifiable in seed). Stated, not dressed up. Sales-invoice reverseCorrect is the same fold (named).
// Implementing FOLD_MODEL_LOGIC.md §F-2 reversal family — Witness: W-FOLD-REVERSE
// Run: node scripts/poc_reverse.js 2>&1 | tee build/erp/poc_reverse.log
'use strict';
var path = require('path');
var fs = require('fs');
var Database = require('better-sqlite3');
var R = require('./post_resolver');
var E = require('./erp_engine');
var F = require('../build/erp/ad_docfsm');

var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db');
var SANDBOX = path.join(__dirname, '..', 'build', 'erp', '_sandbox_reverse.db');
var db = new Database(DB_PATH, { readonly: true });
var SCHEMA = 101;
var AD_TABLE_C_PAYMENT = 335;
var AD_TABLE_C_INVOICE = 318;
var sid = function (x) { return Number(x); };

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function cents(n) { return Math.round(Number(n || 0) * 100); }
function key(acct, side) { return side + ':' + acct; }
function maxDiff(a, b) { var ks = {}; Object.keys(a).forEach(function (k) { ks[k] = 1; }); Object.keys(b).forEach(function (k) { ks[k] = 1; }); var md = 0; Object.keys(ks).forEach(function (k) { var d = Math.abs((a[k] || 0) - (b[k] || 0)); if (d > md) md = d; }); return md; }
// net signed balance per account (DR positive, CR negative) — the annihilation lives here, not in (acct,side) space.
function netByAcct(agg) { var net = {}; Object.keys(agg).forEach(function (k) { var p = k.split(':'); var s = p[0], a = p[1]; net[a] = (net[a] || 0) + (s === 'DR' ? agg[k] : -agg[k]); }); return net; }
function maxAbs(net) { var m = 0; Object.keys(net).forEach(function (a) { if (Math.abs(net[a]) > m) m = Math.abs(net[a]); }); return m; }
// turn an (acct,side)->cents agg into reversePosting's [{account,dr,cr}] input.
function aggToLines(agg) { var by = {}; Object.keys(agg).forEach(function (k) { var p = k.split(':'); var s = p[0], a = p[1]; by[a] = by[a] || { account: a, dr: 0, cr: 0 }; if (s === 'DR') by[a].dr += agg[k]; else by[a].cr += agg[k]; }); return Object.keys(by).map(function (a) { return by[a]; }); }
function linesToAgg(lines) { var agg = {}; lines.forEach(function (f) { if (f.dr) agg[key(f.account, 'DR')] = (agg[key(f.account, 'DR')] || 0) + f.dr; if (f.cr) agg[key(f.account, 'CR')] = (agg[key(f.account, 'CR')] || 0) + f.cr; }); return agg; }

// ── ORACLE: real fact_acct lines for a document (per account, side, integer cents) ──────────────────────
function oracle(adTable, recId) {
  var rows = db.prepare('SELECT account_id, ROUND(SUM(amtacctdr),2) dr, ROUND(SUM(amtacctcr),2) cr FROM fact_acct WHERE ad_table_id=? AND record_id=? AND c_acctschema_id=? GROUP BY account_id').all(adTable, recId, SCHEMA);
  var agg = {}; rows.forEach(function (r) { if (cents(r.dr)) agg[key(r.account_id, 'DR')] = cents(r.dr); if (cents(r.cr)) agg[key(r.account_id, 'CR')] = cents(r.cr); });
  return agg;
}

// ── FORWARD DERIVES (re-used from the proven folds; NOT re-invented) ────────────────────────────────────
// Doc_Payment receipt: DR {Bank.InTransit} / CR {Bank.UnallocatedCash} = payamt  (W-FOLD-PAYMENT)
function derivePaymentFwd(payId) {
  var p = db.prepare('SELECT c_payment_id,c_bankaccount_id,payamt,isreceipt FROM c_payment WHERE c_payment_id=?').get(sid(payId));
  var agg = {}, absent = [];
  function nat(res) { if (res.acct == null || !res.element) { absent.push(res.token); return null; } return res.element.id; }
  function add(side, acct, amt) { if (acct == null || cents(amt) === 0) return; agg[key(acct, side)] = (agg[key(acct, side)] || 0) + cents(amt); }
  var inTransit = nat(R.resolve(db, '{Bank.InTransit}', sid(p.c_bankaccount_id), SCHEMA));
  var unalloc = nat(R.resolve(db, '{Bank.UnallocatedCash}', sid(p.c_bankaccount_id), SCHEMA));
  add('DR', p.isreceipt === 'Y' ? inTransit : unalloc, p.payamt);
  add('CR', p.isreceipt === 'Y' ? unalloc : inTransit, p.payamt);
  return { agg: agg, absent: absent, hdr: p };
}
// Doc_Invoice (vendor / IsSOTrx=N): CR {Vendor.V_Liability}=GrandTotal / DR {Product.InventoryClearing}=linenet
//   + DR {Tax.Credit}=taxamt   (W-FOLD-AP-INVOICE)
function deriveInvoiceApFwd(invId) {
  var hdr = db.prepare('SELECT c_invoice_id,c_bpartner_id,grandtotal,issotrx FROM c_invoice WHERE c_invoice_id=?').get(sid(invId));
  var lines = db.prepare('SELECT m_product_id,c_charge_id,linenetamt FROM c_invoiceline WHERE c_invoice_id=?').all(sid(invId));
  var taxes = db.prepare('SELECT c_tax_id,taxamt FROM c_invoicetax WHERE c_invoice_id=?').all(sid(invId));
  var agg = {}, absent = [];
  function nat(res) { if (res.acct == null || !res.element) { absent.push(res.token + ' ' + (res.absent || 'no-element')); return null; } return res.element.id; }
  function add(side, acct, amt) { if (acct == null || cents(amt) === 0) return; agg[key(acct, side)] = (agg[key(acct, side)] || 0) + cents(amt); }
  add('CR', nat(R.resolve(db, '{Vendor.V_Liability}', sid(hdr.c_bpartner_id), SCHEMA)), hdr.grandtotal);
  lines.forEach(function (l) { if (l.m_product_id) add('DR', nat(R.resolve(db, '{Product.InventoryClearing}', sid(l.m_product_id), SCHEMA)), l.linenetamt); else absent.push('charge-line ' + l.c_charge_id); });
  taxes.forEach(function (t) { add('DR', nat(R.resolve(db, '{Tax.Credit}', sid(t.c_tax_id), SCHEMA)), t.taxamt); });
  return { agg: agg, absent: absent, hdr: hdr };
}

// ── the fixture set: real COMPLETED docs the oracle posted — payments + vendor invoices ─────────────────
var FIXTURES = [];
db.prepare('SELECT DISTINCT record_id FROM fact_acct WHERE ad_table_id=? AND c_acctschema_id=? ORDER BY record_id').all(AD_TABLE_C_PAYMENT, SCHEMA)
  .forEach(function (r) { FIXTURES.push({ fam: 'C_Payment', adTable: AD_TABLE_C_PAYMENT, table: 'c_payment', idCol: 'c_payment_id', id: r.record_id, derive: derivePaymentFwd }); });
db.prepare('SELECT DISTINCT record_id FROM fact_acct WHERE ad_table_id=? AND c_acctschema_id=? ORDER BY record_id').all(AD_TABLE_C_INVOICE, SCHEMA)
  .forEach(function (r) { var h = db.prepare('SELECT issotrx FROM c_invoice WHERE c_invoice_id=?').get(sid(r.record_id)); if (h && h.issotrx === 'N') FIXTURES.push({ fam: 'C_Invoice', adTable: AD_TABLE_C_INVOICE, table: 'c_invoice', idCol: 'c_invoice_id', id: r.record_id, derive: deriveInvoiceApFwd }); });

console.log('═══ W-FOLD-REVERSE — reverseCorrect annihilates the REAL fact_acct + FSM CO→RE (ORACLE-ANCHORED) ═══');
console.log('    forward = post_resolver (re-derived from source) · reversal = erp_engine.reversePosting · oracle = real fact_acct (client 11) · schema=' + SCHEMA);
console.log('    §FIXTURES ' + FIXTURES.map(function (f) { return f.fam + '(' + f.id + ')'; }).join(' ') + '\n');

// ── 1. FSM CONTRACT (pure transition table — doctype-independent core; c_doctype absent in this extract) ──
var fsmRC = F.transition('RC', 'CO'), fsmRA = F.transition('RA', 'CO'), fsmVO = F.transition('VO', 'CO');
var coActs = F.STATUS_ACTIONS['CO'] || [];
verdict(fsmRC === 'RE' && fsmRA === 'RE' && fsmVO === 'VO' && coActs.indexOf('RC') >= 0 && coActs.indexOf('RA') >= 0 && coActs.indexOf('VO') >= 0,
  'FSM: from CO the reversal actions are legal and land on the right status', 'RC→' + fsmRC + ' RA→' + fsmRA + ' VO→' + fsmVO + ' legal@CO=[' + coActs.join(',') + ']');
console.log('§FSM-REVERSAL from=CO RC→' + fsmRC + ' RA→' + fsmRA + ' VO→' + fsmVO + ' (terminal RE actions=[' + (F.STATUS_ACTIONS['RE'] || []).join(',') + '])\n');

// ── 2. per-document: anchor holds, then engine reversal ANNIHILATES the real oracle ─────────────────────
var anchored = 0, annihilated = 0;
FIXTURES.forEach(function (fx) {
  var d = fx.derive(fx.id), o = oracle(fx.adTable, fx.id);
  // (a) ANCHOR: the re-derived forward posting == the REAL fact_acct (else the anchor is not iDempiere's).
  var anchorDiff = maxDiff(d.agg, o), anchorOk = anchorDiff === 0 && d.absent.length === 0 && Object.keys(o).length > 0;
  if (anchorOk) anchored++;
  // (b) ENGINE reversal (mode=correct) — swap Dr/Cr, fed the FORWARD lines (never the books).
  var revLines = E.reversePosting(aggToLines(d.agg), { mode: 'correct', dateacct: 'orig' });
  var revAgg = linesToAgg(revLines);
  // (c) ANNIHILATION: real-original NET + engine-reversal NET == 0 in every account (reverseCorrect contract).
  var combinedNet = {}; var oNet = netByAcct(o), rNet = netByAcct(revAgg);
  Object.keys(oNet).forEach(function (a) { combinedNet[a] = (combinedNet[a] || 0) + oNet[a]; });
  Object.keys(rNet).forEach(function (a) { combinedNet[a] = (combinedNet[a] || 0) + rNet[a]; });
  var resid = maxAbs(combinedNet);
  // (d) the reversal must touch EXACTLY the original accounts with SWAPPED sides.
  var swapOfOracle = {}; Object.keys(o).forEach(function (k) { var p = k.split(':'); swapOfOracle[key(p[1], p[0] === 'DR' ? 'CR' : 'DR')] = o[k]; });
  var swapDiff = maxDiff(revAgg, swapOfOracle);
  var ok = anchorOk && resid === 0 && swapDiff === 0;
  if (ok) annihilated++;
  verdict(ok, fx.fam + '(' + fx.id + ') reverseCorrect → annihilates oracle, sides swapped', 'anchorDiff=' + anchorDiff + 'c residual=' + resid + 'c swapDiff=' + swapDiff + 'c accts=' + Object.keys(o).length + (d.absent.length ? ' ABSENT=[' + d.absent.join(',') + ']' : ''));
  console.log('§FOLD-COMPLETE doc=' + fx.fam + ' id=' + fx.id + ' action=RC status=CO→RE postings=' + Object.keys(revAgg).length + ' anchor=iDempiere(maxDiff=' + anchorDiff + 'c) reverseCorrect+original=NET-ZERO(residual=' + resid + 'c) oracle=iDempiere');
});

verdict(anchored === FIXTURES.length && FIXTURES.length > 0, anchored + '/' + FIXTURES.length + ' forward derivations RE-CONFIRMED == real fact_acct (anchor holds)', 'anchored=' + anchored);
verdict(annihilated === FIXTURES.length && FIXTURES.length > 0, annihilated + '/' + FIXTURES.length + ' reverseCorrect postings ANNIHILATE the real oracle to the cent (CO→RE)', 'annihilated=' + annihilated);

// ── 3. reverseAccrual: SAME negation, posting-identical to reverseCorrect (date-shift named-deferred) ────
(function () {
  var fx = FIXTURES[0]; var d = fx.derive(fx.id);
  var rc = linesToAgg(E.reversePosting(aggToLines(d.agg), { mode: 'correct', dateacct: 'orig' }));
  var ra = linesToAgg(E.reversePosting(aggToLines(d.agg), { mode: 'accrual', reversalDate: 'next-period' }));
  verdict(maxDiff(rc, ra) === 0, 'reverseAccrual posting == reverseCorrect posting (only the booked DATE differs)', 'postingDiff=' + maxDiff(rc, ra) + 'c');
  console.log('§FOLD-DEFERRED reverseAccrual date-shift — the next-OPEN-period date is the only delta; this fact_acct ' +
    'extract carries NO c_period/calendar table, so the booked date is unverifiable in seed (the posting negation IS proven, oracle-anchored).');
})();

// ── 4. §FALSIFIER-A: same-sign "reversal" (no swap) → the document DOUBLES, does not annihilate ──────────
(function () {
  var fx = FIXTURES[0]; var d = fx.derive(fx.id), o = oracle(fx.adTable, fx.id);
  var badAgg = d.agg; // identity, NOT swapped — the broken reversal
  var combined = {}; var oNet = netByAcct(o), bNet = netByAcct(badAgg);
  Object.keys(oNet).forEach(function (a) { combined[a] = (combined[a] || 0) + oNet[a]; });
  Object.keys(bNet).forEach(function (a) { combined[a] = (combined[a] || 0) + bNet[a]; });
  var resid = maxAbs(combined);
  verdict(resid > 0, '§FALSIFIER-A same-sign (no Dr/Cr swap) → original+"reversal" DOUBLES, residual≠0', 'residual=' + resid + 'c');
  console.log('§FALSIFIER-A doc=' + fx.fam + ' id=' + fx.id + ' mutation=no-swap residual=' + resid + 'c (must be >0)');
})();

// ── 5. §FALSIFIER-B: post the reversal to the WRONG account → annihilation fails on that account ─────────
(function () {
  var fx = FIXTURES[0]; var d = fx.derive(fx.id), o = oracle(fx.adTable, fx.id);
  var revLines = E.reversePosting(aggToLines(d.agg), { mode: 'correct' });
  revLines[0] = { account: '999999', dr: revLines[0].dr, cr: revLines[0].cr };  // corrupt one reversal account
  var revAgg = linesToAgg(revLines);
  var combined = {}; var oNet = netByAcct(o), rNet = netByAcct(revAgg);
  Object.keys(oNet).forEach(function (a) { combined[a] = (combined[a] || 0) + oNet[a]; });
  Object.keys(rNet).forEach(function (a) { combined[a] = (combined[a] || 0) + rNet[a]; });
  var resid = maxAbs(combined);
  verdict(resid > 0, '§FALSIFIER-B reversal posted to a wrong account → annihilation fails, residual≠0', 'residual=' + resid + 'c');
  console.log('§FALSIFIER-B doc=' + fx.fam + ' id=' + fx.id + ' mutation=wrong-account residual=' + resid + 'c (must be >0)');
})();

// ── 6. ENACT the FSM transition in a SANDBOX copy — the anchor (real client-11 rows) stays untouched ─────
(function () {
  if (fs.existsSync(SANDBOX)) fs.unlinkSync(SANDBOX);
  fs.copyFileSync(DB_PATH, SANDBOX);
  var sb = new Database(SANDBOX);
  var enacted = 0;
  FIXTURES.forEach(function (fx) {
    var to = F.transition('RC', 'CO');                                   // CO → RE (engine decision)
    sb.prepare('UPDATE ' + fx.table + ' SET docstatus=? WHERE ' + fx.idCol + '=?').run(to, sid(fx.id));
    var got = sb.prepare('SELECT docstatus FROM ' + fx.table + ' WHERE ' + fx.idCol + '=?').get(sid(fx.id)).docstatus;
    if (got === 'RE') enacted++;
  });
  sb.close();
  // ANCHOR untouched: the same docs in the read-only original are still CO.
  var anchorIntact = FIXTURES.every(function (fx) { return db.prepare('SELECT docstatus FROM ' + fx.table + ' WHERE ' + fx.idCol + '=?').get(sid(fx.id)).docstatus === 'CO'; });
  verdict(enacted === FIXTURES.length, enacted + '/' + FIXTURES.length + ' reversals ENACTED in sandbox (docstatus CO→RE)', 'sandbox=' + path.basename(SANDBOX));
  verdict(anchorIntact, 'anchor untouched — every real client-11 doc still CO in the read-only original', 'anchorIntact=' + anchorIntact);
  console.log('§ENACT sandbox=' + path.basename(SANDBOX) + ' enacted=' + enacted + '/' + FIXTURES.length + ' CO→RE anchorIntact=' + anchorIntact);
  fs.unlinkSync(SANDBOX);
})();

console.log('\n§FOLD-RESIDUAL void(unposted)=status-only + reverseAccrual booked-date (no c_period in seed) + sales-invoice ' +
  'reverseCorrect (same fold, AP shown) — named. newVerbs=1 (reversePosting, the reversal RULE); fold-not-fork: forward ' +
  'derives + FSM reused.');
console.log('\n' + (fails === 0 ? '🟢 W-FOLD-REVERSE PASS' : '🔴 W-FOLD-REVERSE FAIL (' + fails + ')') +
  ' — reverseCorrect emits the exact negation of iDempiere\'s real posting (annihilates to the cent), FSM CO→RE, enacted in a sandbox with the anchor intact.');
db.close();
process.exit(fails === 0 ? 0 : 1);
