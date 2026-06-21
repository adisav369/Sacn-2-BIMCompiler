// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-INVOICEGL is ✅ DONE in ERP_COVERAGE_MATRIX.md.
// SCOPE: W-INVOICEGL — prove build/erp/report_invoice_gl.foldInvoiceGL reproduces the REPORT core of
//   iDempiere's InvoiceNGL (Invoice Not-realized Gain/Loss → the T_InvoiceGL scratch table): per open
//   invoice ⋈ Fact_Acct, the currency-revaluation amounts (AmtRevalDr/Cr), the reval diffs, OpenAmt and
//   the percent proration — against an INDEPENDENT oracle. SPEC: docs/GapClosureSpec §5d.
// ORACLE (non-tautological, §3 option b): the live iDempiere Postgres (idempiere_test — the ORACLE DB,
//   fact_acct=300; the default `idempiere` config DB has fact_acct=0). The oracle re-runs the EXACT
//   InvoiceNGL INSERT…SELECT logic in SQL using iDempiere's OWN plpgsql currencyConvert() + invoiceOpen()
//   functions + the three post-UPDATEs (diff/percent/proration). The engine independently reimplements
//   currencyRate/currencyRound/currencyConvert + the diffs/percent/proration in JS over the SAME base
//   rows — two independent paths over identical inputs. Labelled "== live iDempiere currency engine".
// NON-INVENT: every value READ from a real row. EXACT BigDecimal. No Date.now/random. EMU/Euro fixed-rate
//   branches + allocation/credit-memo corrections are NO-OPS in this seed (asserted in-witness, not assumed).
// §-log first — READ build/erp/poc_fold_invoice_gl.log before concluding (exit code ≠ evidence).
// Run:  bash build/erp/run_witness.sh scripts/poc_fold_invoice_gl.js
'use strict';
var path = require('path'), cp = require('child_process');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var R = require(path.join(ERP, 'report_invoice_gl.js'));
var BD = require(path.join(ERP, 'bigdecimal.js'));

var PG = { container: 'postgres', user: 'adempiere', db: 'idempiere_test' };   // ORACLE DB (fact_acct populated)
var US = '|';
var DATE = '2008-06-13';     // DateReval — a process parameter (>= the active rate's ValidFrom); same on both sides
var CONVTYPE = 114;          // C_ConversionTypeReval_ID = Spot ('S', the default type carrying live rates)
var SCHEMAS = [101, 200000]; // run per acct schema (US Dollar / Euro), as the process does
var fails = 0;
function ok(cond, msg, detail) { if (!cond) fails++; console.log('  ' + (cond ? '✓' : '✗ FAIL:') + ' ' + msg + (detail ? ' — ' + detail : '')); }
function psql(sql) {
  return cp.execFileSync('docker', ['exec', '-e', 'PGOPTIONS=-c search_path=adempiere', PG.container,
    'psql', '-U', PG.user, '-d', PG.db, '-t', '-A', '-F', US, '-c', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 64 * 1024 * 1024 });
}
function rows(sql, cols) {
  return psql(sql).split('\n').filter(function (s) { return s.length; }).map(function (l) {
    var f = l.split(US), o = {}; cols.forEach(function (c, i) { o[c] = f[i]; }); return o;
  });
}

console.log('=== §INVOICEGL witness (W-INVOICEGL) — T_InvoiceGL revaluation fold vs live iDempiere currency engine ===');
try { psql('SELECT 1'); } catch (e) { console.log('§INVOICEGL-SKIP oracle Postgres unreachable → honest ⬜, not ✅'); process.exit(1); }
console.log('§INVOICEGL-ORACLE live iDempiere Postgres reachable (' + PG.container + '/' + PG.db + ', schema adempiere)\n');

// ── 0. seed-boundary assertions (EMU + allocations are no-ops → flexible-rate + OpenAmt=GrandTotal) ─────────
var ccyMeta = rows("SELECT c_currency_id, iso_code, isemumember FROM c_currency WHERE c_currency_id IN (100,102)", ['id', 'iso', 'emu']);
ok(ccyMeta.every(function (c) { return c.emu === 'N'; }),
  'no involved currency is an EMU member → currencyRate flexible path is the live one (EMU branches no-op)',
  ccyMeta.map(function (c) { return c.iso + '=' + c.emu; }).join(','));
var allocN = +psql("SELECT count(*) FROM c_allocationline al JOIN c_allocationhdr a ON al.c_allocationhdr_id=a.c_allocationhdr_id JOIN c_invoice ci ON al.c_invoice_id=ci.c_invoice_id WHERE ci.ispaid='N' AND a.isactive='Y'").trim();
ok(allocN === 0, 'open invoices carry no active allocations → OpenAmt=GrandTotal, Percent=100 (proration verified no-op)', 'allocLines=' + allocN);

// ── 1. ENGINE inputs: raw base-table dumps (engine does ALL joins/filters/math in JS) ──────────────────────
var src = {
  factAcct: rows("SELECT fact_acct_id, ad_table_id, record_id, c_acctschema_id, account_id, amtsourcedr, amtsourcecr, amtacctdr, amtacctcr FROM fact_acct WHERE ad_table_id=318",
    ['fact_acct_id', 'ad_table_id', 'record_id', 'c_acctschema_id', 'account_id', 'amtsourcedr', 'amtsourcecr', 'amtacctdr', 'amtacctcr']),
  invoices: rows("SELECT c_invoice_id, grandtotal, c_currency_id, issotrx, ispaid, ad_client_id, ad_org_id FROM c_invoice",
    ['c_invoice_id', 'grandtotal', 'c_currency_id', 'issotrx', 'ispaid', 'ad_client_id', 'ad_org_id']),
  elementValues: rows("SELECT c_elementvalue_id, accounttype FROM c_elementvalue", ['c_elementvalue_id', 'accounttype']),
  acctSchemas: rows("SELECT c_acctschema_id, c_currency_id FROM c_acctschema", ['c_acctschema_id', 'c_currency_id']),
  rates: rows("SELECT c_currency_id, c_currency_id_to, c_conversiontype_id, multiplyrate, validfrom, validto, ad_client_id, ad_org_id, isactive FROM c_conversion_rate",
    ['c_currency_id', 'c_currency_id_to', 'c_conversiontype_id', 'multiplyrate', 'validfrom', 'validto', 'ad_client_id', 'ad_org_id', 'isactive']),
  currencies: rows("SELECT c_currency_id, stdprecision FROM c_currency", ['c_currency_id', 'stdprecision']),
  allocations: []   // verified empty above; the invoiceOpen allocation loop is ported but inert here
};
console.log('§INVOICEGL-SRC base{factAcct(318)=' + src.factAcct.length + ', invoices=' + src.invoices.length +
  ', rates=' + src.rates.length + '} dateReval=' + DATE + ' convType=' + CONVTYPE + '\n');

// ── 2. ORACLE: the production InvoiceNGL INSERT…SELECT + post-UPDATEs, run in PG (its OWN currency funcs) ────
function oracleSQL(as) {
  return "WITH base AS (SELECT DISTINCT i.c_invoice_id, ii.grandtotal, invoiceOpen(i.c_invoice_id,0) openamt, " +
    "fa.fact_acct_id, fa.amtsourcedr-fa.amtsourcecr asrcbal, fa.amtacctdr-fa.amtacctcr aacctbal, " +
    "currencyConvert(fa.amtsourcedr, i.c_currency_id, a.c_currency_id, '" + DATE + "'::timestamp, " + CONVTYPE + ", i.ad_client_id, i.ad_org_id) revaldr0, " +
    "currencyConvert(fa.amtsourcecr, i.c_currency_id, a.c_currency_id, '" + DATE + "'::timestamp, " + CONVTYPE + ", i.ad_client_id, i.ad_org_id) revalcr0, " +
    "fa.amtacctdr, fa.amtacctcr, i.c_currency_id invccy, a.c_currency_id asccy, i.issotrx " +
    "FROM c_invoice_v i JOIN c_invoice ii ON i.c_invoice_id=ii.c_invoice_id " +
    "INNER JOIN fact_acct fa ON fa.ad_table_id=318 AND fa.record_id=i.c_invoice_id AND (ii.grandtotal=fa.amtsourcedr OR ii.grandtotal=fa.amtsourcecr) " +
    "INNER JOIN c_acctschema a ON fa.c_acctschema_id=a.c_acctschema_id " +
    "WHERE i.ispaid='N' AND EXISTS (SELECT 1 FROM c_elementvalue ev WHERE ev.c_elementvalue_id=fa.account_id AND ev.accounttype IN ('A','L')) " +
    "AND fa.c_acctschema_id=" + as + "), " +
    "d AS (SELECT *, revaldr0-amtacctdr drdiff0, revalcr0-amtacctcr crdiff0, " +
    "CASE WHEN grandtotal=openamt THEN 100 WHEN grandtotal<>0 THEN round(openamt*100/grandtotal,6) ELSE 100 END pct FROM base) " +
    "SELECT c_invoice_id, fact_acct_id, grandtotal, openamt, pct, asrcbal, aacctbal, " +
    "CASE WHEN pct<>100 THEN revaldr0*pct/100 ELSE revaldr0 END revaldr, " +
    "CASE WHEN pct<>100 THEN revalcr0*pct/100 ELSE revalcr0 END revalcr, " +
    "CASE WHEN pct<>100 THEN drdiff0*pct/100 ELSE drdiff0 END drdiff, " +
    "CASE WHEN pct<>100 THEN crdiff0*pct/100 ELSE crdiff0 END crdiff, " +
    "invccy, asccy, issotrx FROM d ORDER BY c_invoice_id, fact_acct_id";
}
var OCOLS = ['c_invoice_id', 'fact_acct_id', 'grandtotal', 'openamt', 'pct', 'asrcbal', 'aacctbal', 'revaldr', 'revalcr', 'drdiff', 'crdiff', 'invccy', 'asccy', 'issotrx'];
var NUM = {
  GrandTotal: 'grandtotal', OpenAmt: 'openamt', Percent: 'pct', AmtSourceBalance: 'asrcbal', AmtAcctBalance: 'aacctbal',
  AmtRevalDr: 'revaldr', AmtRevalCr: 'revalcr', AmtRevalDrDiff: 'drdiff', AmtRevalCrDiff: 'crdiff'
};

// ── 3. DIFF per acct schema — every money field, EXACT (BigDecimal compareTo) ──────────────────────────────
var totalCells = 0, totalRows = 0, crossCcyRows = 0;
SCHEMAS.forEach(function (as) {
  var oracle = rows(oracleSQL(as), OCOLS);
  var folded = R.foldInvoiceGL(src, { acctSchemaId: as, dateReval: DATE, convTypeReval: CONVTYPE, apar: 'A', isAllCurrencies: false, currencyId: 0 });
  console.log('— acct schema ' + as + ': oracle rows=' + oracle.length + ' engine rows=' + folded.length);
  ok(oracle.length === folded.length, 'row count matches (schema ' + as + ')', 'oracle=' + oracle.length + ' engine=' + folded.length);
  var oByKey = {}; oracle.forEach(function (o) { oByKey[o.c_invoice_id + ':' + o.fact_acct_id] = o; });
  var mism = 0, cells = 0;
  folded.forEach(function (r) {
    totalRows++;
    if (r.InvCcy !== r.SchemaCcy) crossCcyRows++;
    var o = oByKey[r.C_Invoice_ID + ':' + r.Fact_Acct_ID];
    if (!o) { mism++; console.log('   ⚠ FINDING no oracle row for invoice=' + r.C_Invoice_ID + ' fact=' + r.Fact_Acct_ID); return; }
    Object.keys(NUM).forEach(function (k) {
      cells++; totalCells++;
      if (r[k].compareTo(BD.of(o[NUM[k]])) !== 0) { mism++; console.log('   ⚠ FINDING ' + k + ' inv=' + r.C_Invoice_ID + ' fact=' + r.Fact_Acct_ID + ' engine=' + r[k].toString() + ' oracle=' + o[NUM[k]]); }
    });
  });
  ok(mism === 0, 'foldInvoiceGL == live iDempiere currencyConvert/invoiceOpen, EXACT (schema ' + as + ')', 'cells=' + cells + ' mismatches=' + mism);
});
ok(crossCcyRows > 0, 'cross-currency rows are present & diffed (the revaluation math is actually exercised)', 'crossCcyRows=' + crossCcyRows);
console.log('§INVOICEGL surface=report_invoice_gl rows=' + totalRows + ' cells=' + totalCells + ' crossCcy=' + crossCcyRows + ' oracle=idempiere_test currencyConvert');

// ── 4. §FALSIFIER — bend the engine's looked-up rate → AmtRevalDr/Diff must diverge from the live oracle ────
console.log('\n── §FALSIFIER — bending the engine conversion rate must break the AmtRevalDr diff ──');
(function () {
  var as = 200000;   // Euro schema — every USD invoice is cross-currency here, so a bent rate provably moves AmtRevalDr
  var oracle = rows(oracleSQL(as), OCOLS);
  var oByKey = {}; oracle.forEach(function (o) { oByKey[o.c_invoice_id + ':' + o.fact_acct_id] = o; });
  var bent = R.foldInvoiceGL(src, { acctSchemaId: as, dateReval: DATE, convTypeReval: CONVTYPE, apar: 'A', isAllCurrencies: false, currencyId: 0, bendRate: { factor: '1.1' } });
  var diverged = 0, probe = null;
  bent.forEach(function (r) {
    if (r.InvCcy === r.SchemaCcy) return;   // same-ccy rows return amount unchanged (rate not applied) — skip
    var o = oByKey[r.C_Invoice_ID + ':' + r.Fact_Acct_ID];
    if (o && r.AmtRevalDr.compareTo(BD.of(o.revaldr)) !== 0) { diverged++; if (!probe) probe = { inv: r.C_Invoice_ID, engine: r.AmtRevalDr.toString(), oracle: o.revaldr }; }
  });
  if (probe) console.log('§INVOICEGL-FALSIFIER bent rate ×1.1 → inv ' + probe.inv + ' AmtRevalDr engine=' + probe.engine + ' oracle=' + probe.oracle);
  ok(diverged > 0, 'perturbing the conversion rate breaks the AmtRevalDr diff (the metric is load-bearing, not vacuous)', 'divergedRows=' + diverged);
})();

console.log('\n' + (fails ? '🔴 ' + fails + ' check(s) FAILED — W-INVOICEGL NOT proven' : '✅ ALL CHECKS PASS — foldInvoiceGL revaluation core ORACLE-EQUIVALENT (live iDempiere currency engine) — W-INVOICEGL'));
process.exit(fails ? 1 : 0);
