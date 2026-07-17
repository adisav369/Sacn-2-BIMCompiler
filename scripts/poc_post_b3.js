#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_post_b3.js — W-POST-B3 (prompts/FABLE5_B3_POSTING_ORACLE.md §W-1 triage + §W-5 witness).
//
// SPEC (B-3): the 8 doc classes whose FSM is source-parsed (ad_docfsm.js:185-205, the "0-seed" bands) but
//   that carry ZERO posted fact_acct rows — their accounting fan-out has never been oracle-verified. This
//   witness establishes, deterministically and WITHOUT INVENTING, the honest posting status of each:
//     (1) fact_acct(id) == 0 in the captured oracle (build/erp/glassbowl_data.db, GardenWorld client 11) —
//         re-asserted here, not assumed;
//     (2) each class's ARM, from EXTRACTED facts: does a Doc_<Class> poster exist in the compiled posting
//         factory (org.compiere.acct.Doc.get), and does any SOURCE document exist in the live seed;
//     (3) derivePostings emits ∅ for these classes (doc_poster carries only the sales manifest) while STILL
//         emitting a non-empty fold for C_Order — proving the ∅ is class-specific, not a dead verb.
//
// THE DOCTRINE RESULT this witness records (the load-bearing finding): every one of the 8 has ZERO source
//   documents in GardenWorld — the only real seed. They split TWO ways:
//     · ∅-BY-DESIGN (C_BankTransfer, C_DepositBatch): NO Doc_<Class> in the posting factory → posting is
//       simply not in their contract. oracle=∅ is CORRECT and structurally proven. ✅ DONE here.
//     · G-seed / Fable-5 lane (C_ProjectIssue + the 5 Fixed-Asset classes): a poster EXISTS and WOULD post
//       real facts, but 0 source documents exist in the seed. The posting manifest is source-parseable from
//       the compiled createFacts (logged below, line-cited). To oracle them, PREPARE GardenWorld-model seed
//       documents (asset/project docs built along the existing GardenWorld demo pattern), drive the REAL
//       compiled Doc_<Class> on a scratch idempiere_test, capture fact_acct.
//   USER RULING (2026-07-17): seed-data prep along the GardenWorld model is USABILITY / PoC seed prep — it is
//   NOT a prime-rule violation. The oracle is still the REAL compiled poster's output over that seed; only the
//   demo INPUT is prepared, honestly labelled as seed. (What the prime rule forbids is HAND-AUTHORING the
//   expected fact_acct — that stays banned. Preparing seed + letting the poster compute the facts is fine.)
//   This ARM IS ASSIGNED TO A FABLE 5 SESSION (deep get-it-right-first-time posting loop). This triage witness
//   just classifies + hands off; it does NOT itself generate.
//
// NON-INVENT: fact_acct emptiness is re-read live from the captured oracle. Source-doc counts + factory
//   presence are EXTRACTED (live PG `idempiere_test` + iDempiere source), re-runnable, provenance below —
//   never hand-guessed. READ THE LOG (build/erp/poc_post_b3.log); exit ≠ evidence.
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var DP = require('./doc_poster');

var ORACLE = path.join(__dirname, '..', 'build', 'erp', 'glassbowl_data.db');
var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

// ── EXTRACTED 2026-07-17 (provenance, re-runnable — NOT invented) ──────────────────────────────────────
// source-doc + fact_acct counts:
//   docker exec postgres psql -U adempiere -d idempiere_test -At -c "SELECT count(*) FROM <table>;"
//   docker exec postgres psql -U adempiere -d idempiere_test -At -c \
//     "SELECT ad_table_id,count(*) FROM fact_acct WHERE ad_table_id IN (...) GROUP BY ad_table_id;"  → (none)
// poster presence: ls ~/idempiere-dev-setup/idempiere/org.adempiere.base/src/org/compiere/acct/Doc_*.java
//   + grep BankTransfer|DepositBatch org/compiere/acct/Doc.java  → NO factory case (structurally non-posting)
var TARGETS = [
  { id: 200246, table: 'C_BankTransfer',    poster: null,                  src: 0, arm: '∅-design',
    createFacts: '(none — no Doc_ class; Doc.get has no case for C_BankTransfer)' },
  { id: 200056, table: 'C_DepositBatch',    poster: null,                  src: 0, arm: '∅-design',
    createFacts: '(none — no Doc_ class; Doc.get has no case for C_DepositBatch)' },
  { id: 623,    table: 'C_ProjectIssue',    poster: 'Doc_ProjectIssue',    src: 0, arm: 'G-seed(Fable5)',
    createFacts: 'Doc_ProjectIssue.createFacts:125+ DR getAccount(WIP/asset) / CR m_line.getAccount(inventory), amt=cost (createCostDetail; null cost → return null)' },
  { id: 53137, table: 'A_Asset_Addition',   poster: 'Doc_AssetAddition',   src: 0, arm: 'G-seed(Fable5)',
    createFacts: 'Doc_AssetAddition.createFacts:60+ gate A_SourceType=Imported | A_CapvsExp=Expense → empty facts; else DR P_Asset/Charge acct, amt=AssetSourceAmt' },
  { id: 53127, table: 'A_Asset_Disposed',   poster: 'Doc_AssetDisposed',   src: 0, arm: 'G-seed(Fable5)',
    createFacts: 'Doc_AssetDisposed.createFacts:65+ A_Asset_Acct / A_Accumdepreciation_Acct / A_Disposal_Loss_Acct' },
  { id: 53275, table: 'A_Asset_Reval',      poster: 'Doc_AssetReval',      src: 0, arm: 'G-seed(Fable5)',
    createFacts: 'Doc_AssetReval.createFacts:55+ A_Asset_Acct/A_Reval_Cost_Offset_Acct + Accumdep offset pair' },
  { id: 53128, table: 'A_Asset_Transfer',   poster: 'Doc_AssetTransfer',   src: 0, arm: 'G-seed(Fable5)',
    createFacts: 'Doc_AssetTransfer.createFacts:44+ A_Asset_New/A_Asset + Accumdepreciation_New/Accumdepreciation pairs' },
  { id: 53121, table: 'A_Depreciation_Entry', poster: 'Doc_DepreciationEntry', src: 0, arm: 'G-seed(Fable5)',
    createFacts: 'Doc_DepreciationEntry.createFacts:66+ per depexp line DR getDR_Account_ID / CR getCR_Account_ID, amt=getExpense (other-acctschema → empty)' }
];

console.log('═══ W-POST-B3 — 0-seed posting triage (FABLE5_B3_POSTING_ORACLE.md §W-1) — EXTRACT/PROVE, never invent ═══');
console.log('    oracle = build/erp/glassbowl_data.db fact_acct (GardenWorld client 11, 300 rows) · source-counts extracted live 2026-07-17\n');

var db = new Database(ORACLE, { readonly: true });

// ── §W-1 / §B3-ORACLE: every target has ZERO posted rows in the captured oracle (re-asserted, not assumed) ──
var totalRows = db.prepare('SELECT count(*) c FROM fact_acct').get().c;
var anyPosted = false;
TARGETS.forEach(function (t) {
  var c = db.prepare('SELECT count(*) c FROM fact_acct WHERE ad_table_id=?').get(t.id).c;
  if (c > 0) anyPosted = true;
  console.log('§B3-ORACLE id=' + t.id + ' ' + t.table + ' fact_acct_rows=' + c + ' src_docs=' + t.src +
    ' poster=' + (t.poster || 'NONE') + ' arm=' + t.arm);
});
verdict(!anyPosted, 'all 8 targets have 0 posted fact_acct rows in the captured oracle (' + totalRows + ' total)', 'confirms the 0-seed premise');

// ── §W-1 / §B3-TRIAGE: arm classification per class, from EXTRACTED facts ──────────────────────────────
console.log('');
var designCount = 0, seedBlockedCount = 0;
TARGETS.forEach(function (t) {
  console.log('§B3-TRIAGE ' + t.table + ' arm=' + t.arm + ' createFacts=' + t.createFacts);
  if (t.arm === '∅-design') designCount++; else seedBlockedCount++;
});
verdict(designCount === 2 && seedBlockedCount === 6, 'triage: 2 ∅-by-design (no poster, DONE) + 6 G-seed (poster exists, 0 source docs → GardenWorld-model seed prep, Fable-5 lane)', 'design=' + designCount + ' g-seed=' + seedBlockedCount);

// ── §W-3-analogue: derivePostings emits ∅ for every B-3 class (sales-only manifest) — and NON-∅ for C_Order ──
console.log('');
var SCHEMA = 101; // GardenWorld US/USD (the schema doc_poster derives)
var allEmpty = true;
TARGETS.forEach(function (t) {
  var r = DP.derivePostings(db, { table: t.table, id: 1 }, SCHEMA);
  var empty = r.lines.length === 0 && r.basis === 'none';
  if (!empty) allEmpty = false;
  console.log('§B3-DERIVE ' + t.table + ' derivePostings.lines=' + r.lines.length + ' basis=' + r.basis);
});
verdict(allEmpty, 'derivePostings emits ∅ (lines=0, basis=none) for all 8 B-3 classes — no sales manifest applies', 'as designed');

// ── §B3-FALSIFIER (load-bearing): the ∅ is CLASS-SPECIFIC, and the =0 oracle check DISCRIMINATES ──────
console.log('');
// (a) a real posting table (C_Invoice=318 / C_Order=259) carries rows → the =0 check is NOT vacuously true.
var postedProbe = db.prepare('SELECT ad_table_id, count(*) c FROM fact_acct GROUP BY ad_table_id ORDER BY c DESC LIMIT 1').get();
verdict(postedProbe && postedProbe.c > 0, '§FALSIFIER real posting table has rows → the fact_acct=0 check discriminates (not vacuous)',
  'busiest ad_table_id=' + (postedProbe ? postedProbe.ad_table_id : '?') + ' rows=' + (postedProbe ? postedProbe.c : 0));
console.log('§B3-FALSIFIER discriminates: busiest ad_table_id=' + (postedProbe ? postedProbe.ad_table_id : '?') + ' rows=' + (postedProbe ? postedProbe.c : 0) + ' (>0) vs B-3 ids all 0');
// (b) derivePostings is a LIVE verb: it returns a non-empty fold for a real completed C_Order.
var ord = db.prepare("SELECT c_order_id FROM c_order WHERE docstatus='CO' LIMIT 1").get();
if (ord) {
  var ro = DP.derivePostings(db, { table: 'C_Order', id: ord.c_order_id }, SCHEMA);
  verdict(ro.lines.length > 0, '§FALSIFIER derivePostings(C_Order) is NON-empty → the ∅ for B-3 classes is class-specific, not a dead verb',
    'C_Order ' + ord.c_order_id + ' lines=' + ro.lines.length + ' basis=' + ro.basis);
  console.log('§B3-FALSIFIER live-verb C_Order=' + ord.c_order_id + ' lines=' + ro.lines.length + ' basis=' + ro.basis + ' (must be >0)');
} else {
  console.log('§B3-FALSIFIER SKIP no completed C_Order in oracle db (verb-liveness proven by poc_doc_poster instead)');
}

// ── §B3-SKIPS: the honest terminal states + the one BLOCKED user question ──────────────────────────────
console.log('');
console.log('§B3-DONE ∅-by-design (2): C_BankTransfer, C_DepositBatch — no Doc_ poster in factory; oracle=∅ is CORRECT + structural. CLOSED.');
console.log('§B3-ASSIGN G-seed → FABLE 5 (6): C_ProjectIssue, A_Asset_Addition/Disposed/Reval/Transfer, A_Depreciation_Entry —');
console.log('§B3-ASSIGN   poster exists, manifest source-parsed (above). Prepare GardenWorld-model seed docs (usability/PoC seed —');
console.log('§B3-ASSIGN   USER RULING 2026-07-17: NOT a prime-rule violation), drive REAL compiled Doc_<Class> on scratch PG,');
console.log('§B3-ASSIGN   capture fact_acct = oracle, diff derivePostings maxDiff=0c. Prime rule still bans HAND-AUTHORING facts.');
console.log('§B3-ASSIGN   Handed off to a Fable 5 session (deep get-it-right-first-time posting loop) — NOT generated by this triage.');

db.close();
console.log('\n' + (fails === 0 ? '🟢 W-POST-B3 PASS' : '🔴 W-POST-B3 FAIL (' + fails + ')') +
  ' — 8 classes triaged: 2 ∅-design (✅ CLOSED) + 6 G-seed (poster manifest parsed → GardenWorld-model seed prep, assigned to Fable 5).');
process.exit(fails === 0 ? 0 : 1);
