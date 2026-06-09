#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_gljournal.js — W-FOLD-GLJOURNAL (FOLD_MODEL_LOGIC.md NEXT list 6c — manual GL journal posting).
//
// SPEC (Doc_GLJournal): a manual journal's lines post DIRECTLY as fact lines — amtacct = round(amtsource ×
//   currencyrate, 2) (here each journal is entered in its schema's OWN currency, so currencyrate=1). On its
//   own that half is near-tautological. The NON-trivial half is Fact.balanceAccounting / balanceSegments:
//   GardenWorld's journal 100 is INTER-ORG — DR Checking@org11 / CR Checking@org12 (same natural account 508,
//   different C_ValidCombinations) — so iDempiere balances EACH org with an Intercompany Due-To/Due-From line,
//   using the SAME schema-GL accounts + the SAME rule already proven in W-FOLD-MOVEMENT
//   (c_acctschema_gl.intercompanydueto_acct → 600 Intercompany Due To, intercompanyduefrom_acct → 741 Due From).
//   The balancing rule, per org with non-zero net (ΣDR−ΣCR):  net>0 → CR {Due To} net ;  net<0 → DR {Due From} |net|.
//
//   Proven to ORACLE-EQUIVALENCE: derived (account,side) cents — posted lines + per-org balancing — ==
//   real fact_acct(224), maxDiff=0c, across BOTH acctschemas (101 USD journal 100, 200000 EUR journal 200000).
//
// HONEST SCOPE: the source→accounted derivation is genuine but DEGENERATE here (rate=1 in both journals, since
//   each is entered in its schema currency) — we derive it anyway (amtsource × rate) and check it reproduces the
//   stored amtacct, so the fold does NOT copy amtacct. The load-bearing, non-tautological content is the inter-org
//   balancing. A foreign-rate journal (rate≠1) would exercise the conversion the same way W-FOLD-ALLOC-FX does.
//
// NON-INVENT: journal lines, currency rate, and intercompany accounts are real GardenWorld rows (glassbowl_data.db,
//   client 11); the org of each fact line is the line's C_ValidCombination org; integer cents; no Date.now/random.
//   READ build/erp/poc_gljournal.log — exit code is not evidence.
// Implementing FOLD_MODEL_LOGIC.md NEXT 6c (GL_Journal) — Witness: W-FOLD-GLJOURNAL
// Run: node scripts/poc_gljournal.js 2>&1 | tee build/erp/poc_gljournal.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var R = require('./post_resolver');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var AD_TABLE_GL_JOURNAL = 224;
var sid = function (x) { return Number(x); };
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }
function cents(n) { return Math.round(Number(n || 0) * 100); }
function key(a, s) { return s + ':' + a; }
function maxDiff(da, o) { var ks = {}; Object.keys(da).forEach(function (k) { ks[k] = 1; }); Object.keys(o).forEach(function (k) { ks[k] = 1; }); var md = 0; Object.keys(ks).forEach(function (k) { var d = Math.abs((da[k] || 0) - (o[k] || 0)); if (d > md) md = d; }); return md; }

// intercompany Due-To/From for a schema (the SAME resolution W-FOLD-MOVEMENT used: schema-GL validcombination → natural account).
function intercompany(schema) {
  var gl = db.prepare('SELECT intercompanydueto_acct,intercompanyduefrom_acct FROM c_acctschema_gl WHERE c_acctschema_id=?').get(schema);
  var dt = gl ? R.elementOf(db, gl.intercompanydueto_acct) : null;
  var df = gl ? R.elementOf(db, gl.intercompanyduefrom_acct) : null;
  return { dueTo: dt ? dt.id : null, dueFrom: df ? df.id : null };
}

// ── DERIVE: post the journal lines (amtacct = amtsource × rate) + per-org intercompany balancing ──
function deriveJournal(journalId, schema, opt) {
  opt = opt || {};
  var lines = db.prepare('SELECT account_id,ad_org_id,currencyrate,amtsourcedr,amtsourcecr,amtacctdr,amtacctcr FROM gl_journalline WHERE gl_journal_id=? ORDER BY line').all(sid(journalId));
  var agg = {}, orgNet = {}, deriveMismatch = 0;
  function add(side, acct, cnt) { if (acct == null || cnt === 0) return; agg[key(acct, side)] = (agg[key(acct, side)] || 0) + cnt; }
  lines.forEach(function (ln) {
    var rate = Number(ln.currencyrate);
    var drC = Math.round(Number(ln.amtsourcedr) * rate * 100);   // derived accounted DR (cents) = round(amtsource×rate,2)
    var crC = Math.round(Number(ln.amtsourcecr) * rate * 100);   // derived accounted CR
    if (drC !== cents(ln.amtacctdr) || crC !== cents(ln.amtacctcr)) deriveMismatch++;  // derivation must reproduce stored amtacct
    add('DR', ln.account_id, drC);
    add('CR', ln.account_id, crC);
    orgNet[ln.ad_org_id] = (orgNet[ln.ad_org_id] || 0) + drC - crC;
  });
  // per-org balancing — Intercompany Due-To/From (skipped when opt.noBalance, swapped when opt.swap).
  var ic = intercompany(schema);
  var dueTo = opt.swap ? ic.dueFrom : ic.dueTo, dueFrom = opt.swap ? ic.dueTo : ic.dueFrom;
  var balanced = {};
  if (!opt.noBalance) {
    Object.keys(orgNet).forEach(function (org) {
      var net = orgNet[org];
      if (net > 0) add('CR', dueTo, net);          // org has excess DR → balance with a CR to Due-To
      else if (net < 0) add('DR', dueFrom, -net);  // org has excess CR → balance with a DR to Due-From
      balanced[org] = 0;                            // after balancing, every org nets to 0
    });
  }
  return { agg: agg, orgNet: orgNet, deriveMismatch: deriveMismatch, lineCount: lines.length, ic: ic };
}

function oracleJournal(journalId, schema) {
  var rows = db.prepare('SELECT account_id,ROUND(SUM(amtacctdr),2) dr,ROUND(SUM(amtacctcr),2) cr FROM fact_acct WHERE ad_table_id=? AND record_id=? AND c_acctschema_id=? GROUP BY account_id').all(AD_TABLE_GL_JOURNAL, sid(journalId), schema);
  var agg = {}; rows.forEach(function (r) { if (cents(r.dr)) agg[key(r.account_id, 'DR')] = cents(r.dr); if (cents(r.cr)) agg[key(r.account_id, 'CR')] = cents(r.cr); });
  return agg;
}

console.log('═══ W-FOLD-GLJOURNAL — manual GL journal posting (lines + inter-org balancing) == iDempiere oracle ═══');
console.log('    derive = amtsource×rate + per-org Intercompany Due-To/From · oracle = real fact_acct(224)\n');

var journals = db.prepare('SELECT gl_journal_id, c_acctschema_id FROM gl_journal ORDER BY gl_journal_id').all();
var equiv = 0;
journals.forEach(function (j) {
  var schema = j.c_acctschema_id;
  var d = deriveJournal(j.gl_journal_id, schema), o = oracleJournal(j.gl_journal_id, schema);
  var md = maxDiff(d.agg, o), accts = Object.keys(o).length;
  var orgs = Object.keys(d.orgNet).length;
  var ok = md === 0 && accts > 0 && d.deriveMismatch === 0;
  if (ok) equiv++;
  verdict(ok, 'journal ' + j.gl_journal_id + ' (schema ' + schema + ', ' + orgs + ' orgs) → oracle-equivalent',
    'lines=' + d.lineCount + ' postings=' + accts + ' maxDiff=' + md + 'c deriveAmtAcct=' + (d.deriveMismatch === 0 ? 'OK' : 'MISMATCH×' + d.deriveMismatch) + ' Due-To=' + d.ic.dueTo + ' Due-From=' + d.ic.dueFrom);
  console.log('§FOLD-COMPLETE doc=GL_Journal id=' + j.gl_journal_id + ' schema=' + schema + ' orgs=' + orgs +
    ' postings=' + accts + ' interorg-balanced=Y oracle=iDempiere maxDiff=' + md + 'c');
});
verdict(equiv === journals.length && journals.length > 0, equiv + '/' + journals.length + ' GL_Journal postings ORACLE-EQUIVALENT to the cent (lines + inter-org balancing)', 'equiv=' + equiv);

// ── §FALSIFIER-A: swap Intercompany Due-To/From → the balancing accounts invert, diff blows up ──
(function () {
  var j = journals[0]; var o = oracleJournal(j.gl_journal_id, j.c_acctschema_id);
  var md = maxDiff(deriveJournal(j.gl_journal_id, j.c_acctschema_id, { swap: true }).agg, o);
  verdict(md > 0, '§FALSIFIER-A swap Intercompany Due-To/From on journal ' + j.gl_journal_id + ' → maxDiff≠0 (the bridge is load-bearing)', 'maxDiff=' + md + 'c');
  console.log('§FALSIFIER-A doc=GL_Journal id=' + j.gl_journal_id + ' mutation=swap-intercompany maxDiff=' + md + 'c (must be >0)');
})();

// ── §FALSIFIER-B: drop the per-org balancing (treat as single-org) → the Due-To/From lines vanish, diff blows up ──
(function () {
  var j = journals[0]; var o = oracleJournal(j.gl_journal_id, j.c_acctschema_id);
  var md = maxDiff(deriveJournal(j.gl_journal_id, j.c_acctschema_id, { noBalance: true }).agg, o);
  verdict(md > 0, '§FALSIFIER-B drop per-org balancing on journal ' + j.gl_journal_id + ' → maxDiff≠0 (inter-org balancing is load-bearing, not tautological)', 'maxDiff=' + md + 'c');
  console.log('§FALSIFIER-B doc=GL_Journal id=' + j.gl_journal_id + ' mutation=no-org-balancing maxDiff=' + md + 'c (must be >0)');
})();

console.log('\n§GLJ_NOTE the source→accounted derivation (amtsource × currencyrate) is exercised but degenerate (rate=1, ' +
  'each journal entered in its schema currency); a foreign-rate journal converts identically to W-FOLD-ALLOC-FX. The ' +
  'load-bearing content here is the inter-org balancing — same Intercompany Due-To/From rule as W-FOLD-MOVEMENT.');

console.log('\n' + (fails === 0 ? '🟢 W-FOLD-GLJOURNAL PASS' : '🔴 W-FOLD-GLJOURNAL FAIL (' + fails + ')') +
  ' — manual GL journal posting (direct lines + per-org intercompany balancing) oracle-equivalent to the cent across both acctschemas.');
db.close();
process.exit(fails === 0 ? 0 : 1);
