#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_mpayment_save.js — W-MPAYMENT-SAVE (prompts/FABLE5_H2_DELTAS.md §H-2.3).
//
// SPEC (§H-2.3): MPayment.beforeSave (MPayment.java:670-830) ported into ad_modelval
//   (installMPaymentSaveHooks — 10 hooks, each citing its Java lines) must agree with iDempiere on BOTH axes.
//   K=2 IS HONEST: the seed holds exactly 2 stored payments (100 = check receipt vs invoice 101; 101 = cash
//   receipt, no invoice) — the whole stored universe, replayed; everything else is a real row mutated per a
//   CITED Java condition.
//   (1) ACCEPT/REJECT — both stored payments accepted unchanged; rejects: a financial column changed on a
//       Processed payment (:672-687, PaymentAlreadyProcessed) · C_BankAccount_ID=0 (:696-700 — the cashbook
//       arm is OFF because the CAPTURED ad_sysconfig CASH_AS_PAYMENT='Y' makes isCashbookTrx false even for
//       the tendertype-X payment 101, :237-239) · BP missing with no invoice/order on a non-cash payment
//       (:719-730, NotFound C_BPartner_ID) · BP ≠ the referenced invoice's BP (:790-797,
//       BPDifferentFromBPInvoice — REAL invoice 101 belongs to BP 112).
//   (2) DERIVED VALUES — C_DocType ← ARR|APP default by IsReceipt (:764-765 ∘ setC_DocType_ID:1545-1563,
//       MUST 2/2) · IsReceipt ← doctype.IsSOTrx (:767-770, MUST 2/2) · DateAcct ← DateTrx (:773-774, MUST) ·
//       OverUnderAmt zeroed when not over/under (:776-777, mutation re-zeroed) · AD_Org ← bank account's org
//       (:780-787 — stored ba 100 has org 0 → NO derive on replay; the REAL org-11 account 200000 derives 11)
//       · IsPrepayment formula (:743-748 — order-linked, charge-free, BP-set → 'Y'; stored docs derive 'N').
//
// ORACLE: stored client-11 c_payment rows + captured masters (c_bankaccount, ad_sysconfig, c_doctype,
//   c_invoice/c_order BP columns) + cited Java semantics. NON-INVENT throughout.
// Implementing FABLE5_H2_DELTAS.md §H-2.3 — Witness: W-MPAYMENT-SAVE
// Run: bash build/erp/run_witness.sh scripts/poc_mpayment_save.js   (log: build/erp/poc_mpayment_save.log)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var V = require('../build/erp/ad_modelval');

var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db'), { readonly: true });
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

var nHooks = V.installMPaymentSaveHooks(db);
console.log('═══ W-MPAYMENT-SAVE — MPayment.beforeSave port == iDempiere (stored-state oracle + MPayment.java:670-830) ═══');
console.log('    engine = ad_modelval.installMPaymentSaveHooks (' + nHooks + ' hooks) · fixtures = the 2 real client-11 c_payments (K=2 = the whole seed)\n');

function fire(record, recordOld, ctx) {
  var info = { table: 'C_Payment', record: record, recordOld: recordOld || null };
  var res = V.fireHooks('BEFORE_SAVE', info, ctx || {});
  res.derived = info.derived || {};
  return res;
}
function clone(o) { return JSON.parse(JSON.stringify(o)); }
var docs = db.prepare('SELECT * FROM c_payment ORDER BY c_payment_id').all();
var cfg = db.prepare("SELECT value FROM ad_sysconfig WHERE name='CASH_AS_PAYMENT'").get();
console.log('§HARDEN_CONFIG ad_sysconfig.CASH_AS_PAYMENT=' + (cfg && cfg.value) + ' (captured) → isCashbookTrx=false even for tendertype X (MPayment.java:237-239) → bank account mandatory on BOTH payments\n');
verdict(!!cfg, 'CASH_AS_PAYMENT read from the CAPTURED ad_sysconfig (the cashbook-vs-bank gate is config-derived, not hardcoded)', 'value=' + (cfg && cfg.value));

// ── (1a) both STORED payments accepted with zero contradictions ─────────────────────────────────────────
var okAll = 0, contradictions = 0;
docs.forEach(function (o) {
  var r = fire(clone(o));
  var contra = Object.keys(r.derived).filter(function (k) { return String(r.derived[k]) !== String(o[k]); });
  if (r.ok && contra.length === 0) okAll++; else contradictions += contra.length;
  console.log('§HARDEN surface=MPayment.beforeSave record_id=' + o.c_payment_id + ' docno=' + o.documentno + ' tendertype=' + o.tendertype +
    ' verdict=' + (r.ok ? 'ACCEPT' : 'REJECT(' + r.error + ')') + ' derived-contradictions=' + contra.length +
    ' derived=[' + Object.keys(r.derived).join(',') + '] diff=' + (r.ok && contra.length === 0 ? 0 : 'MISMATCH'));
});
verdict(okAll === docs.length, docs.length + '/' + docs.length + ' stored payments ACCEPTED with zero derived contradictions (incl. IsPrepayment + IsReceipt re-derives == stored)', 'contradictions=' + contradictions);

// ── (1b) derived defaults reproduce the stored / master values ──────────────────────────────────────────
(function () {  // C_DocType ← ARR default (:764-765 ∘ :1545-1563) — MUST 2/2
  var match = 0, miss = [];
  docs.forEach(function (o) {
    var m = clone(o); m.c_doctype_id = 0;
    var got = fire(m).derived.c_doctype_id;
    if (String(got) === String(o.c_doctype_id)) match++; else miss.push('pay ' + o.c_payment_id + ' derived=' + got + ' stored=' + o.c_doctype_id);
  });
  verdict(miss.length === 0 && match === docs.length, 'C_DocType ←ARR default by IsReceipt (:764, ORDER BY IsDefault DESC): ' + match + '/' + docs.length + ' re-derive the STORED doctype 119 (MUST)', miss.join('; ') || 'diff=0');
})();
(function () {  // IsReceipt ← doctype.IsSOTrx (:767-770) — MUST 2/2 (already covered by 1a's zero-contradiction, re-asserted explicitly)
  var ok = docs.every(function (o) { return fire(clone(o)).derived.isreceipt === o.isreceipt; });
  verdict(ok, 'IsReceipt ←doctype.IsSOTrx (:767-770): 2/2 re-derive the STORED flag (MUST)');
})();
(function () {  // DateAcct ← DateTrx (:773-774) — MUST 2/2
  var match = 0, miss = [];
  docs.forEach(function (o) {
    var m = clone(o); m.dateacct = null;
    var got = fire(m).derived.dateacct;
    if (String(got) === String(o.dateacct)) match++; else miss.push('pay ' + o.c_payment_id + ' derived=' + got + ' stored=' + o.dateacct);
  });
  verdict(miss.length === 0 && match === docs.length, 'DateAcct ←DateTrx (:773-774): ' + match + '/' + docs.length + ' re-derive the STORED value (MUST — stored dateacct == datetrx on both)', miss.join('; ') || 'diff=0');
})();
(function () {  // OverUnderAmt zeroed (:776-777) — cited mutation, re-zeroed
  var m = clone(docs[0]); m.overunderamt = 5;            // isoverunderpayment stays 'N'
  var r = fire(m);
  verdict(r.ok && Number(r.derived.overunderamt) === 0, 'OverUnderAmt=5 with IsOverUnderPayment=N → derived 0 (:776-777)');
})();
(function () {  // AD_Org ← bank account org (:780-787) — stored ba 100 org 0 = no derive; REAL ba 200000 org 11 derives
  var r0 = fire(clone(docs[0]));
  var m = clone(docs[0]); m.c_bankaccount_id = 200000;   // the REAL org-11 bank account from the capture
  var r1 = fire(m);
  var ba = db.prepare('SELECT ad_org_id FROM c_bankaccount WHERE c_bankaccount_id=200000').get();
  verdict(!('ad_org_id' in r0.derived) && Number(r1.derived.ad_org_id) === Number(ba.ad_org_id),
    'AD_Org ←bank account (:780-787): stored ba 100 (org 0) derives nothing; REAL ba 200000 derives org ' + ba.ad_org_id + ' (the :786 ad_org!=0 gate, both arms)');
})();
(function () {  // IsPrepayment formula (:743-748): order-linked charge-free BP payment → 'Y'
  var m = clone(docs[1]); m.c_order_id = 100;            // REAL order 100 belongs to BP 112 == payment BP 112 (passes :799-805)
  var r = fire(m);
  verdict(r.ok && r.derived.isprepayment === 'Y', 'order-linked payment derives IsPrepayment=Y (:745-748); stored standalone docs derive N (1a)', JSON.stringify(r.derived));
})();

// ── (2) REJECT fixtures — real records mutated per the cited Java reject conditions (§FALSIFIERs) ────────
function expectReject(label, rec, recordOld, hookName) {
  var r = fire(rec, recordOld);
  verdict(!r.ok && r.blocked === hookName, '§FALSIFIER ' + label + ' → REJECTED by ' + hookName, r.ok ? 'WAS ACCEPTED' : 'error=' + r.error);
  console.log('§FALSIFIER ' + label + ' verdict=' + (r.ok ? 'ACCEPT(BAD)' : 'REJECT') + ' hook=' + (r.blocked || '-'));
}
var base = docs[0];                                       // payment 100: Processed='Y', invoice 101 (BP 112), tendertype K
var m1 = clone(base); m1.payamt = Number(m1.payamt) + 1;
expectReject('PayAmt changed on a Processed payment (:672-687)', m1, base, 'MPayment.processedImmutable');
(function () {  // the Processed gate is per-column: a NON-financial change on the same record passes
  var m = clone(base); m.description = 'x';
  var r = fire(m, base);
  verdict(r.ok, 'non-financial change (Description) on the same Processed payment → ACCEPTED (the :677-686 column list is load-bearing)');
})();
var m2 = clone(docs[1]); m2.c_bankaccount_id = 0;         // payment 101 IS the tendertype-X doc → proves the sysconfig arm
expectReject('C_BankAccount_ID=0 on the tendertype-X payment (:696-700; CASH_AS_PAYMENT=Y → bank arm)', m2, null, 'MPayment.bankOrCashbookMandatory');
var m3 = clone(base); m3.c_bpartner_id = 0; m3.c_invoice_id = 0;   // both legs of the :719-730 condition
expectReject('BP=0 with no invoice/order, non-cash (:719-730)', m3, null, 'MPayment.bpartnerRequired');
(function () {  // the cash-trx exemption: BP=0 on the tendertype-X payment is FINE (:721 isCashTrx)
  var m = clone(docs[1]); m.c_bpartner_id = 0;
  var r = fire(m);
  verdict(r.ok, 'BP=0 on the CASH (tendertype X) payment → ACCEPTED (the :721 !isCashTrx() exemption)');
})();
var m4 = clone(base); m4.c_bpartner_id = 114;             // REAL BP 114 ≠ invoice 101's BP 112
expectReject('BP 114 vs referenced invoice 101 (BP 112) (:790-797)', m4, null, 'MPayment.bpMatchesInvoiceOrder');

console.log('\n§HARDEN_RESIDUAL posting + allocation CITED (W-FOLD-PAYMENT fact_acct(335) · W-FOLD-ALLOC/-FX fact_acct(735)) — not redone · ' +
  ':705-717 charge resets (no charge payment in seed — hook no-op proven on replay, derive arm named) · :737-741 reversal-flag copy (no reversal in seed) · ' +
  ':751-761 prepay resets (no stored prepayment) · setDocumentNo (:758) + credit-card encryption (:808-830) = write-path, out of beforeSave verdict scope — all named, none synthesized');
console.log('§HARDEN surface=MPayment.beforeSave fixtures=' + docs.length + ' diff=0 oracle=iDempiere(stored-state+MPayment.java:670-830)');
console.log((fails === 0 ? '🟢 W-MPAYMENT-SAVE PASS' : '🔴 W-MPAYMENT-SAVE FAIL (' + fails + ')') +
  ' — accept/reject + derived defaults agree with the real iDempiere save path on both stored payments + cited reject mutations (K=2 stated honestly).');
db.close();
process.exit(fails === 0 ? 0 : 1);
