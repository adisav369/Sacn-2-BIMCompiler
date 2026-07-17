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
//   EXECUTED by the Fable 5 session 2026-07-17: scripts/generate_post_oracle.sh clones idempiere_test →
//   idempiere_b3, scripts/logic_oracle/PostingOracleTest.java (vendor OSGi test harness) drives the REAL
//   compiled posters over the GardenWorld-model seed, the committed fact_acct is captured as the TEXT fixture
//   build/erp/oracle/post_b3_fixture.json, and THIS witness diffs doc_poster.derivePostings against it —
//   per document, per schema, integer cents, maxDiff=0c (§B3-GEN/§B3-POST bands below).
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

// ── §B3-DERIVE: the 2 ∅-by-design classes still emit ∅ (no manifest, structurally non-posting) ─────────
console.log('');
var SCHEMA = 101; // GardenWorld US/USD primary schema
var designEmpty = true;
TARGETS.filter(function (t) { return t.arm === '∅-design'; }).forEach(function (t) {
  var r = DP.derivePostings(db, { table: t.table, id: 1 }, SCHEMA);
  var empty = r.lines.length === 0 && r.basis === 'none';
  if (!empty) designEmpty = false;
  console.log('§B3-DERIVE ' + t.table + ' derivePostings.lines=' + r.lines.length + ' basis=' + r.basis);
});
verdict(designEmpty, 'derivePostings emits ∅ for the 2 ∅-by-design classes (no Doc_ poster → no manifest)', 'as designed');

// ── §W-2/§W-3: the 6 G-seed classes — GENERATED oracle (real compiled posters on scratch clone) vs
//    the extended derivePostings manifests, per document, per schema, INTEGER CENTS, maxDiff=0c ────────
console.log('');
var fs = require('fs');
var FIXTURE = path.join(__dirname, '..', 'build', 'erp', 'oracle', 'post_b3_fixture.json');
var G_CLASSES = [
  { table: 'A_Asset_Addition',    id: 53137, doctab: 'a_asset_addition',    pk: 'a_asset_addition_id',    poster: 'Doc_AssetAddition' },
  { table: 'A_Depreciation_Entry',id: 53121, doctab: 'a_depreciation_entry',pk: 'a_depreciation_entry_id',poster: 'Doc_DepreciationEntry' },
  { table: 'A_Asset_Reval',       id: 53275, doctab: 'a_asset_reval',       pk: 'a_asset_reval_id',       poster: 'Doc_AssetReval' },
  { table: 'A_Asset_Transfer',    id: 53128, doctab: 'a_asset_transfer',    pk: 'a_asset_transfer_id',    poster: 'Doc_AssetTransfer' },
  { table: 'A_Asset_Disposed',    id: 53127, doctab: 'a_asset_disposed',    pk: 'a_asset_disposed_id',    poster: 'Doc_AssetDisposed' },
  { table: 'C_ProjectIssue',      id: 623,   doctab: 'c_projectissue',      pk: 'c_projectissue_id',      poster: 'Doc_ProjectIssue' }
];

if (!fs.existsSync(FIXTURE)) {
  console.log('§B3-SKIP fixture absent (' + FIXTURE + ') — run: bash scripts/generate_post_oracle.sh → honest ⬜, not ✅');
  fails++;
} else {
  var fx = JSON.parse(fs.readFileSync(FIXTURE, 'utf8'));
  var fdb = new Database(':memory:');
  Object.keys(fx.tables).forEach(function (t) {
    var spec = fx.tables[t];
    fdb.prepare('CREATE TABLE ' + t + ' (' + spec.cols.join(',') + ')').run();
    var ins = fdb.prepare('INSERT INTO ' + t + ' VALUES (' + spec.cols.map(function () { return '?'; }).join(',') + ')');
    // psql CSV capture is all strings — store numeric-looking values as NUMBERS so SQLite's typed
    // comparisons (WHERE ad_table_id=53137) and the cents() math see the same storage class
    spec.rows.forEach(function (r) {
      ins.run(r.map(function (v) { return /^-?\d+(\.\d+)?$/.test(v) ? Number(v) : v; }));
    });
  });
  var schemas = fdb.prepare('SELECT c_acctschema_id s FROM c_acctschema ORDER BY 1').all().map(function (r) { return r.s; });
  function cents(x) { return Math.round(Number(x || 0) * 100); }

  var grandMax = 0, grandDocs = 0, grandRows = 0;
  G_CLASSES.forEach(function (g) {
    // §B3-GEN — the generated oracle for this class (provenance: the committed scratch-clone capture)
    var facts = fdb.prepare('SELECT * FROM fact_acct WHERE ad_table_id=?').all(g.id);
    var sumDr = 0, sumCr = 0;
    facts.forEach(function (f) { sumDr += cents(f.amtacctdr); sumCr += cents(f.amtacctcr); });
    console.log('§B3-GEN class=' + g.table + ' scratch=' + fx.db + ' posted_rows=' + facts.length +
      ' ΣDRc=' + sumDr + ' ΣCRc=' + sumCr + ' provenance=compiled-' + g.poster);
    grandRows += facts.length;

    // §B3-POST — per document, per schema, per (account, side): oracle == derivePostings, integer cents
    var docs = fdb.prepare('SELECT ' + g.pk + ' id FROM ' + g.doctab + ' ORDER BY 1').all().map(function (r) { return r.id; });
    var classMax = 0, checked = 0;
    docs.forEach(function (docId) {
      schemas.forEach(function (schema) {
        var want = {};                                                    // account -> {dr, cr} (cents)
        facts.filter(function (f) { return Number(f.record_id) === Number(docId) && Number(f.c_acctschema_id) === Number(schema); })
          .forEach(function (f) {
            var k = String(f.account_id);
            if (!want[k]) want[k] = { dr: 0, cr: 0 };
            want[k].dr += cents(f.amtacctdr); want[k].cr += cents(f.amtacctcr);
          });
        var got = {};
        var r = DP.derivePostings(fdb, { table: g.table, id: docId, primarySchema: schemas[0] }, schema);
        r.lines.forEach(function (l) {
          var k = String(l.account_id);
          if (!got[k]) got[k] = { dr: 0, cr: 0 };
          got[k].dr += cents(l.amtacctdr); got[k].cr += cents(l.amtacctcr);
        });
        Object.keys(want).concat(Object.keys(got)).forEach(function (k) {
          var w = want[k] || { dr: 0, cr: 0 }, o = got[k] || { dr: 0, cr: 0 };
          var d = Math.max(Math.abs(w.dr - o.dr), Math.abs(w.cr - o.cr));
          if (d > classMax) classMax = d;
          if (d !== 0) console.log('   🔴 diff class=' + g.table + ' doc=' + docId + ' schema=' + schema +
            ' account=' + k + ' oracle=' + w.dr + '/' + w.cr + 'c engine=' + o.dr + '/' + o.cr + 'c');
        });
        checked++;
      });
    });
    if (classMax > grandMax) grandMax = classMax;
    grandDocs += docs.length;
    console.log('§B3-POST class=' + g.table + ' arm=G docs=' + docs.length + ' cells=' + checked +
      ' maxDiff=' + classMax + 'c oracle=compiled-' + g.poster);
    verdict(classMax === 0 && docs.length > 0, g.table + ' derivePostings == generated oracle (maxDiff=0c, ' + docs.length + ' docs × ' + schemas.length + ' schemas)');
  });
  verdict(grandRows > 0, 'the generated oracle is NON-EMPTY (' + grandRows + ' fact rows over ' + grandDocs + ' seed docs)', 'a vacuous ∅-vs-∅ pass is refused');

  // ── §B3-FALSIFIER (load-bearing, the §W-5 pair) ────────────────────────────────────────────────────
  console.log('');
  // (1) the Doc_AssetAddition config-gate (A_CapvsExp=Exp → ∅, Doc_AssetAddition.java:67-72): flip a
  //     posting addition's gate column in the fixture copy → the engine emits ∅ for it; flip back → rows.
  //     The ORACLE side of the same gate is the seed's own Exp addition (posted_rows=0 for that doc).
  var addPosting = fdb.prepare("SELECT a_asset_addition_id id FROM a_asset_addition WHERE a_sourcetype<>'IMP' AND a_capvsexp<>'Exp' ORDER BY 1 LIMIT 1").get();
  if (addPosting) {
    var before = DP.derivePostings(fdb, { table: 'A_Asset_Addition', id: addPosting.id }, schemas[0]);
    fdb.prepare("UPDATE a_asset_addition SET a_capvsexp='Exp' WHERE a_asset_addition_id=?").run(addPosting.id);
    var flipped = DP.derivePostings(fdb, { table: 'A_Asset_Addition', id: addPosting.id }, schemas[0]);
    fdb.prepare("UPDATE a_asset_addition SET a_capvsexp='Cap' WHERE a_asset_addition_id=?").run(addPosting.id);
    var ok1 = before.lines.length > 0 && flipped.lines.length === 0;
    verdict(ok1, '§FALSIFIER gate-flip: A_CapvsExp Cap→Exp turns the addition manifest ∅ (zero is CONFIG-derived, not hardcoded)',
      'doc=' + addPosting.id + ' lines ' + before.lines.length + '→' + flipped.lines.length);
    console.log('§B3-FALSIFIER gate-flip addition=' + addPosting.id + ' lines=' + before.lines.length + '→' + flipped.lines.length + ' (Doc_AssetAddition.java:67-72)');
  } else { console.log('§B3-FALSIFIER SKIP no posting addition in fixture'); fails++; }
  // (2) scale ONE source amount → maxDiff≠0c (the diff is load-bearing, poc_doc_poster §FALSIFIER shape)
  var dep = fdb.prepare('SELECT a_depreciation_exp_id id, expense FROM a_depreciation_exp WHERE a_depreciation_entry_id>0 ORDER BY 1 LIMIT 1').get();
  if (dep) {
    var entry = fdb.prepare('SELECT a_depreciation_entry_id id FROM a_depreciation_entry ORDER BY 1 LIMIT 1').get();
    fdb.prepare('UPDATE a_depreciation_exp SET expense=expense*2 WHERE a_depreciation_exp_id=?').run(dep.id);
    var scaled = DP.derivePostings(fdb, { table: 'A_Depreciation_Entry', id: entry.id }, schemas[0]);
    fdb.prepare('UPDATE a_depreciation_exp SET expense=? WHERE a_depreciation_exp_id=?').run(dep.expense, dep.id);
    var wantDr = 0;
    fdb.prepare('SELECT * FROM fact_acct WHERE ad_table_id=53121 AND record_id=?').all(entry.id)
      .forEach(function (f) { wantDr += cents(f.amtacctdr); });
    var gotDr = 0; scaled.lines.forEach(function (l) { gotDr += cents(l.amtacctdr); });
    var ok2 = gotDr !== wantDr;
    verdict(ok2, '§FALSIFIER scale-one-line: doubling one depexp expense breaks the diff (maxDiff≠0c — the metric is load-bearing)',
      'oracle ΣDR=' + wantDr + 'c vs scaled engine ΣDR=' + gotDr + 'c');
    console.log('§B3-FALSIFIER scale-line depexp=' + dep.id + ' oracleΣDR=' + wantDr + 'c scaledΣDR=' + gotDr + 'c (must differ)');
  } else { console.log('§B3-FALSIFIER SKIP no processed depexp in fixture'); fails++; }
  fdb.close();
}

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

// ── §B3-SKIPS: the honest terminal states ──────────────────────────────────────────────────────────────
console.log('');
console.log('§B3-DONE ∅-by-design (2): C_BankTransfer, C_DepositBatch — no Doc_ poster in factory; oracle=∅ is CORRECT + structural. CLOSED.');
console.log('§B3-SKIPS none deferred: A_Depreciation_Entry is NOT redone from the DepreciationPerf lane (that lane measured the');
console.log('§B3-SKIPS   workfile-BUILD cost, docs/DepreciationPerf.md — it never posted fact_acct); the posting oracle is THIS card\'s.');
console.log('§B3-GEN-NOTE oracle generated per the 2026-07-17 USER RULING: GardenWorld-model seed INPUT (PostingOracleTest.java,');
console.log('§B3-GEN-NOTE   vendor FixedAssetsTest recipe) → REAL compiled posters on scratch clone → capture. Facts never hand-authored.');

db.close();
console.log('\n' + (fails === 0 ? '🟢 W-POST-B3 PASS' : '🔴 W-POST-B3 FAIL (' + fails + ')') +
  ' — 8 classes: 2 ∅-design (✅ structural) + 6 G-seed oracled against the REAL compiled posters (maxDiff=0c gate above).');
process.exit(fails === 0 ? 0 : 1);
