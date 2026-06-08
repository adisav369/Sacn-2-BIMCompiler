#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard
// Scope: §13.7 readPostings acceptance (prompts/BACKEND_LANE_S2.md item 2). Prove the role-gated
//   read-fold: an accounting role sees the balanced fold (source/coverage labelled); a non-accounting
//   role is refused AT THE ENGINE (zero rows leaked); the degrade ladder absent→partial→complete is
//   explicit and non-invent. READ build/erp/poc_postings.log before any conclusion.
//   complete is exercised by a clearly-labelled SHAPE FIXTURE (the bundled Fact_Acct is TOTALS, no
//   record key — per-record complete awaits the §13.6 re-extract; not done speculatively).
// Run: node scripts/poc_postings.js 2>&1 | tee build/erp/poc_postings.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var initSqlJs = require('sql.js');
var K = require('./erp_kernel');
var Resolver = require('./post_resolver');
var P = require('./erp_postings');

var SEED = process.env.POST_SEED || path.join(__dirname, '..', 'build', 'erp', 'post_poc', 'ad_seed.db');
var GLASSBOWL = path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db');
var INVOICE_ID = 103;                 // §13.5 real GardenWorld sales invoice (200002, org 11)
var C_INVOICE_AD_TABLE = 318;         // AD_Table_ID of C_Invoice (recordRef key; fixture only)

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function cents(n) { return Math.round(Number(n || 0) * 100); }

var MANIFEST = {
  doc_type: 'C_Invoice', acctschema: 101,
  charge: [{ dr: [{ acct: '{BPartner.Receivable}', amt: 'doc.GrandTotal' }],
             cr: [{ acct: '{Product.Revenue}', amt: 'line.LineNetAmt' }, { acct: '{Tax.Due}', amt: 'tax.TaxAmt' }] }]
};

(async function () {
  var ad = new Database(SEED, { readonly: true });
  var adQ = function (sql, p) { return ad.prepare(sql).all(p || []); };
  var get = function (sql, p) { return ad.prepare(sql).get(p || []); };
  var SQL = await initSqlJs();
  var projDb = new SQL.Database();
  K.initProjection(projDb);
  var projQ = function (sql, p) { return K.query(projDb, sql, p || []); };

  console.log('═══ POC-POSTINGS — readPostings(§13.7): role-gated fold + degrade ladder ═══\n');

  // ── arrange: POST C_Invoice#103 through the kernel (real masters, real balanced 3-line entry) ──
  var doc = get('SELECT c_invoice_id AS id, grandtotal AS grandtotal, c_bpartner_id AS bp FROM c_invoice WHERE c_invoice_id=?', [INVOICE_ID]);
  var lines = adQ('SELECT m_product_id AS product, linenetamt AS net FROM c_invoiceline WHERE c_invoice_id=?', [INVOICE_ID]);
  var taxes = adQ('SELECT c_tax_id AS tax, taxamt AS taxamt FROM c_invoicetax WHERE c_invoice_id=?', [INVOICE_ID]);
  var drMap = {}, crMap = {};
  function addTo(m, a, v) { var k = String(a); m[k] = (m[k] || 0) + Number(v); }
  function R(token, masterId) { return Resolver.resolve(ad, token, masterId, MANIFEST.acctschema); }
  MANIFEST.charge[0].dr.forEach(function (c) { var r = R(c.acct, doc.bp); if (r.acct != null) addTo(drMap, r.acct, doc.grandtotal); });
  MANIFEST.charge[0].cr.forEach(function (c) {
    if (c.amt === 'line.LineNetAmt') lines.forEach(function (ln) { var r = R(c.acct, ln.product); if (r.acct != null) addTo(crMap, r.acct, ln.net); });
    else taxes.forEach(function (tx) { var r = R(c.acct, tx.tax); if (r.acct != null) addTo(crMap, r.acct, tx.taxamt); });
  });
  var postLines = [];
  Object.keys(drMap).forEach(function (a) { postLines.push({ account_id: a, amtacctdr: cents(drMap[a]) / 100, amtacctcr: 0, role: 'DR' }); });
  Object.keys(crMap).forEach(function (a) { postLines.push({ account_id: a, amtacctdr: 0, amtacctcr: cents(crMap[a]) / 100, role: 'CR' }); });
  K.apply(projDb, [{ op_type: 'POST', table: MANIFEST.doc_type, id: doc.id, acctschema: MANIFEST.acctschema, lines: postLines }],
          { actor: 'plugin:post.salesinvoice', baseTs: 7000 });
  console.log('arranged: POSTed C_Invoice#' + doc.id + ' → ' + postLines.length + ' journal lines (GrandTotal=' + doc.grandtotal + ')\n');

  var refPosted  = { table: 'C_Invoice', record_id: INVOICE_ID, ad_org_id: 11, ad_table_id: C_INVOICE_AD_TABLE };
  var refUnposted = { table: 'C_Invoice', record_id: 999, ad_org_id: 11, ad_table_id: C_INVOICE_AD_TABLE };
  var ADMIN = { role: { id: 102 }, allowOrgs: '*' };   // GardenWorld Admin, isshowacct=Y
  var USER  = { role: { id: 103 }, allowOrgs: '*' };   // GardenWorld User,  isshowacct=N

  // ── §POSTED-READ — accounting role sees the balanced fold (partial: oplog only, no record-keyed fact) ──
  console.log('── §POSTED-READ (role=Admin, posted) ──');
  var pr = P.readPostings(refPosted, ADMIN, { adQ: adQ, projQ: projQ, factQ: null });
  verdict(pr.visible && pr.posted && pr.balanced && pr.lines.length === postLines.length && pr.source === 'oplog' && pr.coverage === 'partial',
    'Admin sees balanced fold, source=oplog coverage=partial', 'rows=' + pr.lines.length + ' balanced=' + pr.balanced + ' source=' + pr.source + ' cov=' + pr.coverage);
  pr.lines.forEach(function (l) { console.log('     acct=' + l.account_id + ' (' + l.value + ' ' + l.name + ') DR=' + l.amtacctdr + ' CR=' + l.amtacctcr); });
  console.log('§POSTED-READ record=C_Invoice#' + INVOICE_ID + ' role=102 isshowacct=Y posted=' + (pr.posted ? 'Y' : 'N') +
    ' rows=' + pr.lines.length + ' balanced=' + (pr.balanced ? 'Y' : 'N') + ' source=' + pr.source + ' coverage=' + pr.coverage);

  // ── §POSTED-GATE — non-accounting role refused at the engine, ZERO rows leaked ──
  console.log('\n── §POSTED-GATE (role=User, isshowacct=N) ──');
  var pg = P.readPostings(refPosted, USER, { adQ: adQ, projQ: projQ, factQ: null });
  verdict(pg.visible === false && pg.reason === 'role-not-accounting' && pg.lines.length === 0,
    'User refused: visible=N reason=role-not-accounting rows=0 (zero leak)', 'visible=' + pg.visible + ' reason=' + pg.reason + ' rows=' + pg.lines.length);
  console.log('§POSTED-GATE role=103 isshowacct=N → visible=' + (pg.visible ? 'Y' : 'N') + ' reason=' + pg.reason + ' rows=' + pg.lines.length);

  // ── §POSTED-GATE (org scope) — a record outside the role's orgs returns empty, never rows ──
  console.log('\n── §POSTED-GATE (org scope, allowOrgs excludes data org) ──');
  var po = P.readPostings(refPosted, { role: { id: 102 }, allowOrgs: [50000] }, { adQ: adQ, projQ: projQ, factQ: null });
  verdict(po.visible === false && po.reason === 'out-of-scope' && po.lines.length === 0,
    'out-of-scope record refused (allowOrgs excludes org 11)', 'visible=' + po.visible + ' reason=' + po.reason + ' rows=' + po.lines.length);
  console.log('§POSTED-GATE role=102 allowOrgs=[50000] org=11 → visible=' + (po.visible ? 'Y' : 'N') + ' reason=' + po.reason + ' rows=' + po.lines.length);

  // ── §POSTED-COVERAGE absent — nothing posted, no fact ──
  console.log('\n── §POSTED-COVERAGE (absent / partial / complete) ──');
  var ca = P.readPostings(refUnposted, ADMIN, { adQ: adQ, projQ: projQ, factQ: null });
  verdict(ca.visible && !ca.posted && ca.source === 'none' && ca.coverage === 'absent' && ca.lines.length === 0,
    'unposted record → source=none coverage=absent (install-local note)', 'source=' + ca.source + ' cov=' + ca.coverage + ' note=' + ca.note);
  console.log('§POSTED-COVERAGE record=C_Invoice#999 source=' + ca.source + ' coverage=' + ca.coverage + ' note=install-local-first');

  // partial (== §POSTED-READ above)
  console.log('§POSTED-COVERAGE record=C_Invoice#' + INVOICE_ID + ' source=' + pr.source + ' coverage=' + pr.coverage + ' note=run-local-install-for-full-history');

  // ── complete — SHAPE FIXTURE (realdata=N): a record-keyed fact_acct cent-equal to the fold ──
  // The bundled fact_acct is TOTALS (no record key) so this branch is UNREACHABLE on real data; the
  // fixture proves the §13.6 cent-gate executes + labels complete, and the off-by-1c case proves it
  // DISCRIMINATES (mismatch → partial), so it is not a tautology. NOT GardenWorld fact.
  var foldRows = projQ("SELECT account_id, amtacctdr, amtacctcr FROM journal WHERE source='DOC:C_Invoice#" + INVOICE_ID + "'");
  function makeFactFixture(perturbCents) {
    var f = new SQL.Database();
    f.run('CREATE TABLE fact_acct (ad_table_id INT, record_id INT, account_id INT, amtacctdr REAL, amtacctcr REAL)');
    foldRows.forEach(function (r, i) {
      var dr = Number(r.amtacctdr || 0), cr = Number(r.amtacctcr || 0);
      if (perturbCents && i === 0) { if (dr > 0) dr += perturbCents / 100; else cr += perturbCents / 100; }   // off-by-1c on one line
      f.run('INSERT INTO fact_acct VALUES (?,?,?,?,?)', [C_INVOICE_AD_TABLE, INVOICE_ID, r.account_id, dr, cr]);
    });
    return function (sql, p) { return K.query(f, sql, p || []); };
  }
  var pc = P.readPostings(refPosted, ADMIN, { adQ: adQ, projQ: projQ, factQ: makeFactFixture(0) });
  verdict(pc.visible && pc.posted && pc.source === 'fact_acct' && pc.coverage === 'complete',
    'cent-equal record-keyed fact → source=fact_acct coverage=complete (fixture)', 'source=' + pc.source + ' cov=' + pc.coverage);
  console.log('§POSTED-COVERAGE record=C_Invoice#' + INVOICE_ID + ' source=' + pc.source + ' coverage=' + pc.coverage + ' fact=fixture realdata=N');

  var pd = P.readPostings(refPosted, ADMIN, { adQ: adQ, projQ: projQ, factQ: makeFactFixture(1) });
  verdict(pd.source === 'oplog' && pd.coverage === 'partial',
    'off-by-1c fact → gate DISCRIMINATES, falls back to coverage=partial (not a tautology)', 'source=' + pd.source + ' cov=' + pd.coverage);
  console.log('§POSTED-COVERAGE off-by-1c fact → source=' + pd.source + ' coverage=' + pd.coverage + ' (cent-gate rejects mismatch)');

  // ── honesty check — the REAL bundled fact_acct has NO record key → complete unreachable on it ──
  var gb = new Database(GLASSBOWL, { readonly: true });
  var gbFactQ = function (sql, p) { return gb.prepare(sql).all(p || []); };
  var realHasKey = P.factHasRecordKey(gbFactQ);
  verdict(realHasKey === false, 'bundled fact_acct is TOTALS (no ad_table_id/record_id) → per-record complete needs §13.6 re-extract', 'factHasRecordKey=' + realHasKey);
  console.log('§POSTED-COVERAGE bundled-fact recordKeyed=' + (realHasKey ? 'Y' : 'N') + ' → real-data complete=UNREACHABLE (await §13.6 re-extract)');
  gb.close();

  ad.close(); projDb.close();
  console.log('\n═══ ' + (fails === 0 ? '✅ POC-POSTINGS ALL PASS' : '❌ POC-POSTINGS ' + fails + ' FAIL') +
    ' — gate engine-side (zero leak); fold balanced; degrade ladder explicit + non-invent ═══');
  process.exit(fails === 0 ? 0 : 1);
})().catch(function (e) { console.error('FATAL', e); process.exit(2); });
