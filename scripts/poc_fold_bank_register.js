// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-BANKREGISTER is recorded in ERP_COVERAGE_MATRIX.md.
// SCOPE: W-BANKREGISTER — prove build/erp/report_bank_register reproduces org.compiere.report.BankRegister
//   (the T_BankRegister report scratch table): createBalanceLine() + createDetailLines(). SPEC: GapClosureSpec §5g.
// HONEST VERDICT: ORACLE-EQUIVALENT but THIN / near-tautological — the only real logic is the bank-account
//   CONFIG-CHAIN join selection (the load-bearing claim, exercised by the §FALSIFIER that drops it) + the
//   DISTINCT-detail vs SUM-balance multiplicity quirk; `Balance = Dr − Cr` passes through and Cr=0 in this seed,
//   so the arithmetic is UNTESTED (named, not claimed). This closes the last `T_*` member; it is NOT a math-bearing fold.
// ORACLE (§3 option b): the EXACT BankRegister INSERT…SELECTs, run as SELECTs on live idempiere_test; the engine
//   does the SAME join in JS — two independent paths. fact_acct lives in idempiere_test (default idempiere=0).
// NON-INVENT: every value READ from a real row. §-log first — READ build/erp/poc_fold_bank_register.log first.
// Run:  bash build/erp/run_witness.sh scripts/poc_fold_bank_register.js
'use strict';
var path = require('path'), cp = require('child_process');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var R = require(path.join(ERP, 'report_bank_register.js'));
var BD = require(path.join(ERP, 'bigdecimal.js'));

var PG = { container: 'postgres', user: 'adempiere', db: 'idempiere_test' };
var US = '|';
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

var BANK = 100;                 // MoneyBank (the only bank with payment postings in seed)
var PAY_TABLE = 335;            // ad_table_id of C_Payment
// fixed report parameters (no Date.now). All 8 driving postings are dated 2002-02-22, so:
var DETAIL = { cBankId: BANK, paymentTableId: PAY_TABLE, dateFrom: '2002-02-22', dateTo: '2002-02-22' }; // → detail rows
var BAL = { cBankId: BANK, paymentTableId: PAY_TABLE, dateFrom: '2002-02-23', dateTo: '2002-02-23' };    // → balance line over all prior

console.log('=== §BANKREGISTER witness (W-BANKREGISTER) — T_BankRegister fold vs live BankRegister SQL (idempiere_test) ===');
try { psql('SELECT 1'); } catch (e) { console.log('§BANKREGISTER-SKIP oracle Postgres unreachable → honest ⬜, not ✅'); process.exit(1); }
console.log('§BANKREGISTER-ORACLE live iDempiere Postgres reachable (' + PG.container + '/' + PG.db + ', schema adempiere)');
console.log('§BANKREGISTER-NOTE THIN fold — load-bearing claim = the config-chain JOIN selection; Balance=Dr−Cr passes through (Cr=0 in seed, arithmetic NAMED not tested)\n');

// ── ENGINE: pull raw base tables, join + fold in JS (independent of the oracle SQL) ─────────────────────────
var src = {
  factAcct: rows("SELECT fact_acct_id, ad_table_id, record_id, account_id, to_char(dateacct,'YYYY-MM-DD') dateacct, " +
    "amtacctdr, amtacctcr, COALESCE(c_bpartner_id,0) c_bpartner_id, ad_client_id, ad_org_id FROM fact_acct WHERE ad_table_id=" + PAY_TABLE,
    ['fact_acct_id', 'ad_table_id', 'record_id', 'account_id', 'dateacct', 'amtacctdr', 'amtacctcr', 'c_bpartner_id', 'ad_client_id', 'ad_org_id']),
  payments: rows("SELECT c_payment_id, c_bankaccount_id, docstatus, documentno FROM c_payment", ['c_payment_id', 'c_bankaccount_id', 'docstatus', 'documentno']),
  bankAccounts: rows("SELECT c_bankaccount_id, c_bank_id FROM c_bankaccount", ['c_bankaccount_id', 'c_bank_id']),
  banks: rows("SELECT c_bank_id, name FROM c_bank", ['c_bank_id', 'name']),
  bankAccountAcct: rows("SELECT c_bankaccount_id, b_intransit_acct, b_asset_acct FROM c_bankaccount_acct", ['c_bankaccount_id', 'b_intransit_acct', 'b_asset_acct']),
  validCombinations: rows("SELECT c_validcombination_id, account_id FROM c_validcombination", ['c_validcombination_id', 'account_id']),
  elementValues: rows("SELECT c_elementvalue_id FROM c_elementvalue", ['c_elementvalue_id']),
  bpartners: rows("SELECT c_bpartner_id, name FROM c_bpartner", ['c_bpartner_id', 'name'])
};
var engDetail = R.foldDetailLines(src, DETAIL, {});
var engBal = R.foldBalanceLine(src, BAL, {});
console.log('§BANKREGISTER-FOLD base{fact_acct(pay)=' + src.factAcct.length + '} → detail(DISTINCT)=' + engDetail.length +
  ' rows, balance-line over ' + engBal.rows + ' join rows = Dr ' + engBal.AmtAcctDr.toString() + ' / Cr ' + engBal.AmtAcctCr.toString() + ' / Bal ' + engBal.Balance.toString());

// ── ORACLE 1: createDetailLines — the live SELECT DISTINCT ──────────────────────────────────────────────────
var oraDetail = rows("SELECT DISTINCT to_char(date_trunc('day',fa.dateacct),'YYYY-MM-DD') d, b.c_bank_id, b.name, " +
  "COALESCE(bp.c_bpartner_id,0) bpid, bp.name bpname, p.documentno, fa.amtacctdr, fa.amtacctcr, fa.amtacctdr-fa.amtacctcr bal " +
  "FROM fact_acct fa " +
  "JOIN c_payment p ON p.c_payment_id=fa.record_id AND p.docstatus IN ('CO','CL') " +
  "JOIN c_bankaccount ba ON ba.c_bankaccount_id=p.c_bankaccount_id " +
  "JOIN c_bank b ON ba.c_bank_id=b.c_bank_id " +
  "JOIN c_bankaccount_acct baa ON p.c_bankaccount_id=baa.c_bankaccount_id " +
  "JOIN c_validcombination vc ON (vc.c_validcombination_id=baa.b_intransit_acct OR vc.c_validcombination_id=baa.b_asset_acct) " +
  "JOIN c_elementvalue ev ON ev.c_elementvalue_id=vc.account_id " +
  "LEFT JOIN c_bpartner bp ON bp.c_bpartner_id=fa.c_bpartner_id " +
  "WHERE fa.ad_table_id=" + PAY_TABLE + " AND b.c_bank_id=" + BANK +
  " AND date_trunc('day',fa.dateacct) BETWEEN '" + DETAIL.dateFrom + "' AND '" + DETAIL.dateTo + "' AND fa.account_id=vc.account_id",
  ['d', 'c_bank_id', 'name', 'bpid', 'bpname', 'documentno', 'amtacctdr', 'amtacctcr', 'bal']);
console.log('§BANKREGISTER-ORACLE-DETAIL live SELECT DISTINCT rows=' + oraDetail.length);
ok(engDetail.length === oraDetail.length && oraDetail.length > 0, 'detail row count (after DISTINCT) matches engine vs live SQL', 'engine=' + engDetail.length + ' oracle=' + oraDetail.length);
// match each engine detail row to an oracle row by the DISTINCT key
function dkey(d, bank, doc, dr, cr, bal) { return [d, bank, doc, BD.of(dr).toString(), BD.of(cr).toString(), BD.of(bal).toString()].join('|'); }
var oraKeys = {}; oraDetail.forEach(function (o) { oraKeys[dkey(o.d, o.c_bank_id, o.documentno, o.amtacctdr, o.amtacctcr, o.bal)] = true; });
var dMis = 0;
engDetail.forEach(function (r) {
  var k = dkey(r.dateAcct, r.c_bank_id, r.documentNo, r.Dr, r.Cr, r.Balance);
  if (!oraKeys[k]) { dMis++; console.log('   ⚠ FINDING engine detail row absent in oracle: ' + k); }
});
ok(dMis === 0, 'every engine detail row (date/bank/doc/Dr/Cr/Balance) matches the live SQL DISTINCT set', 'mismatches=' + dMis);

// ── ORACLE 2: createBalanceLine — the live SUM (no DISTINCT, counts join multiplicity) ─────────────────────
var oraBal = rows("SELECT COALESCE(SUM(fa.amtacctdr),0) dr, COALESCE(SUM(fa.amtacctcr),0) cr, COALESCE(SUM(fa.amtacctdr-fa.amtacctcr),0) bal, count(*) n " +
  "FROM fact_acct fa " +
  "JOIN c_payment p ON p.c_payment_id=fa.record_id AND p.docstatus IN ('CO','CL') " +
  "JOIN c_bankaccount ba ON ba.c_bankaccount_id=p.c_bankaccount_id " +
  "JOIN c_bank b ON ba.c_bank_id=b.c_bank_id " +
  "JOIN c_bankaccount_acct baa ON p.c_bankaccount_id=baa.c_bankaccount_id " +
  "JOIN c_validcombination vc ON (vc.c_validcombination_id=baa.b_intransit_acct OR vc.c_validcombination_id=baa.b_asset_acct) " +
  "JOIN c_elementvalue ev ON ev.c_elementvalue_id=vc.account_id " +
  "WHERE fa.ad_table_id=" + PAY_TABLE + " AND b.c_bank_id=" + BANK +
  " AND date_trunc('day',fa.dateacct) < '" + BAL.dateFrom + "' AND fa.account_id=vc.account_id",
  ['dr', 'cr', 'bal', 'n'])[0];
console.log('§BANKREGISTER-ORACLE-BALANCE live SUM over ' + oraBal.n + ' join rows = Dr ' + oraBal.dr + ' / Cr ' + oraBal.cr + ' / Bal ' + oraBal.bal);
ok(+engBal.rows === +oraBal.n, 'balance-line join-row count matches (the DISTINCT/SUM multiplicity quirk is reproduced)', 'engine=' + engBal.rows + ' oracle=' + oraBal.n);
ok(engBal.AmtAcctDr.compareTo(BD.of(oraBal.dr)) === 0, 'balance-line SUM(AmtAcctDr) == live SQL', 'engine=' + engBal.AmtAcctDr.toString() + ' oracle=' + oraBal.dr);
ok(engBal.AmtAcctCr.compareTo(BD.of(oraBal.cr)) === 0, 'balance-line SUM(AmtAcctCr) == live SQL', 'engine=' + engBal.AmtAcctCr.toString() + ' oracle=' + oraBal.cr);
ok(engBal.Balance.compareTo(BD.of(oraBal.bal)) === 0, 'balance-line SUM(AmtAcctDr−AmtAcctCr) == live SQL', 'engine=' + engBal.Balance.toString() + ' oracle=' + oraBal.bal);
ok(engDetail.length < +oraBal.n, 'DISTINCT detail (' + engDetail.length + ') < SUM-balance join rows (' + oraBal.n + ') — the join multiplicity is real (not invented)');

// ── §FALSIFIER — drop the bank-account selection (fa.Account_ID=vc.Account_ID) → the join leaks rows ─────────
console.log('\n── §FALSIFIER — dropping the config-chain account selection must change the selected row set ──');
(function () {
  var bentBal = R.foldBalanceLine(src, BAL, { bendJoin: true });
  var oraBent = rows("SELECT count(*) n, COALESCE(SUM(fa.amtacctdr-fa.amtacctcr),0) bal FROM fact_acct fa " +
    "JOIN c_payment p ON p.c_payment_id=fa.record_id AND p.docstatus IN ('CO','CL') " +
    "JOIN c_bankaccount ba ON ba.c_bankaccount_id=p.c_bankaccount_id JOIN c_bank b ON ba.c_bank_id=b.c_bank_id " +
    "JOIN c_bankaccount_acct baa ON p.c_bankaccount_id=baa.c_bankaccount_id " +
    "JOIN c_validcombination vc ON (vc.c_validcombination_id=baa.b_intransit_acct OR vc.c_validcombination_id=baa.b_asset_acct) " +
    "JOIN c_elementvalue ev ON ev.c_elementvalue_id=vc.account_id " +
    "WHERE fa.ad_table_id=" + PAY_TABLE + " AND b.c_bank_id=" + BANK + " AND date_trunc('day',fa.dateacct) < '" + BAL.dateFrom + "'",
    ['n', 'bal'])[0];   // NOTE: no fa.account_id=vc.account_id → the leaked superset
  console.log('§BANKREGISTER-FALSIFIER drop account-selection → join rows ' + oraBal.n + ' → ' + oraBent.n + ' (Bal ' + oraBal.bal + ' → ' + oraBent.bal + ')');
  var leaks = +bentBal.rows > +oraBal.n && +bentBal.rows === +oraBent.n;
  ok(leaks, 'dropping fa.Account_ID=vc.Account_ID leaks rows in BOTH engine and SQL (the bank-account selection is load-bearing)', 'engine-bent=' + bentBal.rows + ' clean=' + oraBal.n + ' sql-bent=' + oraBent.n);
  ok(bentBal.Balance.compareTo(BD.of(oraBent.bal)) === 0, 'engine leaked-set SUM == SQL leaked-set SUM (engine reproduces the join faithfully, even when bent)');
})();

console.log('\n' + (fails ? '🔴 ' + fails + ' check(s) FAILED — W-BANKREGISTER NOT proven' :
  '✅ ALL CHECKS PASS — T_BankRegister fold == live BankRegister SQL (THIN: join-selection + DISTINCT/SUM quirk; arithmetic named) — W-BANKREGISTER'));
process.exit(fails ? 1 : 0);
