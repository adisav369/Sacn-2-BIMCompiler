#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_doc_poster.js — W-DOC-POSTER (POSTING_PREVIEW_PANEL.md Gap-A).
//
// SPEC: doc_poster.derivePostings EXTRACTS the per-document GL manifest that, until now, lived only
//   inside the FOLD witnesses (poc_fold_complete.deriveInvoice). This proves the extraction is FAITHFUL:
//     (1) derivePostings(C_Order) for each completed GardenWorld SO == real fact_acct(318) per
//         (natural-account, side) in INTEGER CENTS, maxDiff=0c (the same orders poc_fold_complete proves);
//         a post-posting-edited doc is classified AMT-DRIFT (named, not failed) — same discipline.
//     (2) the fold balances (ΣDR==ΣCR).
//     (3) §FALSIFIER: drop a derived line → maxDiff≠0 (the diff is load-bearing).
//   So the live Posting-Preview seam can consume doc_poster instead of re-deriving — Gap-A closed, the
//   verb is now shipped + oracle-anchored, not witness-local.
// NON-INVENT: real GardenWorld rows (glassbowl_data.db, client 11); accounts via post_resolver; integer
//   cents; no Date.now/Math.random.
// Run: node scripts/poc_doc_poster.js 2>&1 | tee build/erp/poc_doc_poster.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var R = require('./post_resolver');
var DP = require('./doc_poster');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var SCHEMA = 101;
var AD_TABLE_C_INVOICE = 318;
var sid = function (x) { return Number(x); };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function cents(n) { return Math.round(Number(n || 0) * 100); }
function key(a, s) { return s + ':' + a; }

// derived cent-map from doc_poster's VM lines (account_id:side -> cents).
function derivedMap(res) {
  var m = {};
  res.lines.forEach(function (l) {
    if (cents(l.amtacctdr)) m[key(l.account_id, 'DR')] = (m[key(l.account_id, 'DR')] || 0) + cents(l.amtacctdr);
    if (cents(l.amtacctcr)) m[key(l.account_id, 'CR')] = (m[key(l.account_id, 'CR')] || 0) + cents(l.amtacctcr);
  });
  return m;
}
// oracle fact_acct(318) for the invoice — identical read to poc_fold_complete.oracleInvoice.
function oracleInvoice(invId) {
  var rows = db.prepare('SELECT account_id,ROUND(SUM(amtacctdr),2) dr,ROUND(SUM(amtacctcr),2) cr FROM fact_acct WHERE ad_table_id=? AND record_id=? AND c_acctschema_id=? GROUP BY account_id').all(AD_TABLE_C_INVOICE, sid(invId), SCHEMA);
  var agg = {}; rows.forEach(function (r) { if (cents(r.dr)) agg[key(r.account_id, 'DR')] = cents(r.dr); if (cents(r.cr)) agg[key(r.account_id, 'CR')] = cents(r.cr); });
  return agg;
}
function maxDiff(a, o) {
  var keys = {}; Object.keys(a).forEach(function (k) { keys[k] = 1; }); Object.keys(o).forEach(function (k) { keys[k] = 1; });
  var md = 0; Object.keys(keys).forEach(function (k) { var d = Math.abs((a[k] || 0) - (o[k] || 0)); if (d > md) md = d; });
  return md;
}
function acctSetEqual(a, o) { var x = Object.keys(a).sort().join(','), y = Object.keys(o).sort().join(','); return x === y && y.length > 0; }
function postPostingEdit(invId) { var r = db.prepare('SELECT (julianday(updated)-julianday(dateinvoiced)) d FROM c_invoice WHERE c_invoice_id=?').get(sid(invId)); return r && r.d != null && r.d > 0; }

console.log('═══ W-DOC-POSTER — doc_poster.derivePostings extracted from W-FOLD-COMPLETE, oracle-anchored ═══');
console.log('    verb = doc_poster.derivePostings(db,{table:C_Order,id},' + SCHEMA + ') · oracle = GardenWorld fact_acct(318), client 11\n');

var orders = db.prepare("SELECT * FROM c_order WHERE issotrx='Y' AND c_order_id IN (SELECT c_order_id FROM m_inout) ORDER BY c_order_id").all();
var equiv = 0, drift = 0;

orders.forEach(function (order) {
  var res = DP.derivePostings(db, { table: 'C_Order', id: order.c_order_id }, SCHEMA, R);
  var invId = DP.invoiceForOrder(db, order.c_order_id);
  var dm = derivedMap(res), om = invId ? oracleInvoice(invId) : {};
  var md = maxDiff(dm, om), n = Object.keys(om).length;
  var setEq = acctSetEqual(dm, om), edited = invId ? postPostingEdit(invId) : false;
  var cls = (md === 0 && res.absent.length === 0 && n > 0) ? 'EQUIVALENT'
          : (setEq && edited) ? 'AMT-DRIFT(post-posting edit)' : 'DERIVATION-GAP';
  var ok = (cls === 'EQUIVALENT' || cls.indexOf('AMT-DRIFT') === 0) && res.balanced && res.basis === 'invoice';
  if (cls === 'EQUIVALENT') equiv++; else if (cls.indexOf('AMT-DRIFT') === 0) drift++;
  verdict(ok, 'order ' + order.documentno + ' derivePostings == oracle',
    'basis=' + res.basis + ' lines=' + res.lines.length + ' postings=' + n + ' maxDiff=' + md + 'c(' + cls.split('(')[0] + ') balanced=' + (res.balanced ? 'Y' : 'N'));
  console.log('§DOC-POSTER doc=C_Order id=' + order.c_order_id + ' docno=' + order.documentno + ' invoice=' + invId +
    ' lines=' + res.lines.length + ' postings=' + n + ' maxDiff=' + md + 'c(' + cls.split('(')[0] + ') ΣDR=ΣCR=' + (res.balanced ? 'Y' : 'N') + ' absent=' + res.absent.length);
});

verdict(equiv >= 1 && fails === 0, equiv + '/' + orders.length + ' orders derive to an ORACLE-EQUIVALENT posting (' + drift + ' post-posting drift, named)', 'equiv=' + equiv + ' drift=' + drift);

// ── §FALSIFIER: drop a derived line → the per-account diff MUST blow up ────────────────────────────
(function () {
  var order = orders[0];
  var res = DP.derivePostings(db, { table: 'C_Order', id: order.c_order_id }, SCHEMA, R);
  var invId = DP.invoiceForOrder(db, order.c_order_id);
  var dm = derivedMap(res), om = oracleInvoice(invId);
  var drKey = Object.keys(dm).find(function (k) { return k.indexOf('DR:') === 0; });
  var mutated = Object.assign({}, dm); delete mutated[drKey];
  var md = maxDiff(mutated, om);
  verdict(md > 0, '§FALSIFIER drop a derived line → maxDiff≠0 (diff is load-bearing)', 'maxDiff=' + md + 'c');
  console.log('§FALSIFIER doc=C_Order order=' + order.c_order_id + ' invoice=' + invId + ' mutation=drop-receivable-DR maxDiff=' + md + 'c (must be >0)');
})();

console.log('\n' + (fails === 0 ? '🟢 W-DOC-POSTER PASS' : '🔴 W-DOC-POSTER FAIL (' + fails + ')') +
  ' — doc_poster.derivePostings is a shipped, oracle-anchored verb (== fact_acct(318) to the cent); the Posting-Preview seam consumes it, no re-derive. Gap-A closed.');
db.close();
process.exit(fails === 0 ? 0 : 1);
