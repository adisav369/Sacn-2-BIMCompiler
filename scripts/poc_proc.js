// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_proc.js — W-PROC witness. Opens build/erp/ad_full.db (lowercase tables), dispatches REAL AD_Process
// rows through build/erp/ad_process.js: a report proc wired to report_overlay's folds, a doc-action proc,
// validated params (pass + a missing-mandatory falsifier), and an UNREGISTERED classname (absent-handler).
// Implementing ERP_COVERAGE_MATRIX.md §AD_Process — Witness: W-PROC
// Run: node scripts/poc_proc.js 2>&1 | tee build/erp/poc_proc.log   (read the log; exit code != evidence)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');                                   // db-open idiom (poc_access.js:9)
var P  = require(path.join(__dirname, '..', 'build', 'erp', 'ad_process.js'));
var RC = require(path.join(__dirname, '..', 'build', 'erp', 'report_overlay.js'));  // the report folds (CORE export)
var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');

var fails = 0;
function verdict(ok, label, detail) {
  if (!ok) fails++;
  console.log((ok ? '🟢' : '🔴') + ' ' + label + (detail ? '  — ' + detail : ''));
}

console.log('═══ W-PROC — AD_Process dispatch spine (real ad_full.db rows → registry → result) ═══\n');

var db = new Database(DB_PATH, { readonly: true });

// ── helpers: row → object ─────────────────────────────────────────────────────────────────────────────
function rowsObj(rows) { return rows; }   // better-sqlite3 .all() already returns objects

// Install the default handlers, wiring report:* to report_overlay's CORE folds (NOT reinvented).
P.installDefaultHandlers(RC);
console.log('registry: ' + P.registeredClassnames().length + ' handlers = [' + P.registeredClassnames().join(', ') + ']\n');

// ── ctx: data accessors the report/docaction handlers call (sqlite fetchers off ad_full.db) ─────────────
function firstId(table, pk) { var r = db.prepare('SELECT ' + pk + ' AS id FROM ' + table + ' ORDER BY ' + pk + ' LIMIT 1').get(); return r && r.id; }
var ctx = {
  fetchHeader: function (key, info) {
    var map = RC.REPORT_MAP[key]; var id = (info && info.Record_ID != null) ? info.Record_ID : firstId(map.key, map.pk);
    if (id == null) return null;
    return db.prepare('SELECT * FROM ' + map.key + ' WHERE ' + map.pk + '=?').get(id) || null;
  },
  fetchLines: function (key, info) {
    var map = RC.REPORT_MAP[key]; var id = (info && info.Record_ID != null) ? info.Record_ID : firstId(map.key, map.pk);
    if (id == null) return [];
    return db.prepare('SELECT * FROM ' + map.lineTable + ' WHERE ' + map.fk + '=? ORDER BY line').all(id);
  },
  names: function (key, hdr) {
    var nm = { products: {} };
    if (hdr && hdr.c_bpartner_id != null) { var bp = db.prepare('SELECT name FROM c_bpartner WHERE c_bpartner_id=?').get(hdr.c_bpartner_id); nm.partner = bp && bp.name; }
    return nm;
  },
  fetchFacts: function () { return db.prepare('SELECT account_id, amtacctdr, amtacctcr FROM fact_acct').all(); },
  fetchAccounts: function () { var m = {}; db.prepare('SELECT c_elementvalue_id AS id, value, name, accounttype FROM c_elementvalue').all().forEach(function (r) { m[r.id] = r; }); return m; },
  countFacts: function () { return db.prepare('SELECT count(*) AS c FROM fact_acct').get().c; },
  countDocTypes: function () { return db.prepare('SELECT count(*) AS c FROM c_doctype').get().c; }
};

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 1 · §PROC run — a REAL report proc dispatched to a registered handler with validated params → a result.
//     110 "Rpt C_Order" (blank classname, isreport=Y) resolves to report:c_order -> foldReceipt.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('— §PROC run (real AD_Process rows → registry → result) —');
var r110 = P.dispatch(db, { AD_Process_ID: 110, params: {} }, ctx);
console.log('§PROC run AD_Process_ID=110 classname=' + r110.classname + ' params=' + r110.validate.supplied.length +
            ' rows=' + r110.rows + ' dispatched=' + r110.dispatched + ' kind=' + r110.kind + ' msg="' + r110.message + '"');
verdict(r110.dispatched === true && r110.classname === 'report:c_order' && r110.rows > 0 && r110.result && r110.result.foldsFrom === 'bundle',
        'Order Print (110) dispatched to report:c_order → real receipt folded', 'lines=' + r110.rows + ' total=' + (r110.result && r110.result.total));

// 116 "Rpt C_Invoice" -> report:c_invoice
var r116 = P.dispatch(db, { AD_Process_ID: 116, params: {} }, ctx);
console.log('§PROC run AD_Process_ID=116 classname=' + r116.classname + ' params=' + r116.validate.supplied.length +
            ' rows=' + r116.rows + ' dispatched=' + r116.dispatched + ' total=' + (r116.result && r116.result.total));
verdict(r116.dispatched === true && r116.classname === 'report:c_invoice' && r116.rows > 0,
        'Invoice Print (116) dispatched to report:c_invoice → receipt folded', 'lines=' + r116.rows);

// a doc-action proc: 175 FactAcctReset (org.compiere.process.FactAcctReset) — supply its ONE mandatory para.
var r175 = P.dispatch(db, { AD_Process_ID: 175, params: { AD_Client_ID: 11 } }, ctx);
console.log('§PROC run AD_Process_ID=175 classname=' + r175.classname + ' params=' + r175.validate.supplied.length +
            ' rows=' + r175.rows + ' dispatched=' + r175.dispatched + ' kind=' + r175.kind + ' msg="' + r175.message + '"');
verdict(r175.dispatched === true && r175.classname === 'org.compiere.process.FactAcctReset' && r175.kind === 'docaction',
        'Resubmit Posting (175) dispatched to a doc-action handler', 'rows=' + r175.rows);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 2 · §PROC_PARAM_VALIDATE — prepare() step: mandatory-para validation passes; a missing one is REJECTED.
//     310 FinTrialBalance (org.compiere.report.TrialBalance) has 2 mandatory paras: C_AcctSchema_ID, PostingType.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §PROC_PARAM_VALIDATE (mandatory paras: pass + missing-mandatory falsifier) —');
var okParams = { C_AcctSchema_ID: 101, PostingType: 'A' };
var r310ok = P.dispatch(db, { AD_Process_ID: 310, params: okParams }, ctx);
console.log('§PROC_PARAM_VALIDATE proc=310 required=' + r310ok.validate.required.length + ' supplied=' + r310ok.validate.supplied.length +
            ' missing=[' + r310ok.validate.missing.join(',') + '] ok=' + r310ok.validate.ok + ' dispatched=' + r310ok.dispatched);
verdict(r310ok.validate.ok === true && r310ok.validate.required.length === 2 && r310ok.dispatched === true,
        'TrialBalance (310) with both mandatory paras → validation passes, dispatched', 'required=[' + r310ok.validate.required.join(',') + ']');

// falsifier: drop the mandatory PostingType -> must REJECT before doIt() (dispatched=false, reason=param-validation-failed)
var r310bad = P.dispatch(db, { AD_Process_ID: 310, params: { C_AcctSchema_ID: 101 } }, ctx);
console.log('§PROC_PARAM_VALIDATE proc=310 required=' + r310bad.validate.required.length + ' supplied=' + r310bad.validate.supplied.length +
            ' missing=[' + r310bad.validate.missing.join(',') + '] ok=' + r310bad.validate.ok + ' dispatched=' + r310bad.dispatched + ' reason=' + r310bad.reason);
verdict(r310bad.validate.ok === false && r310bad.dispatched === false && r310bad.reason === 'param-validation-failed' &&
        r310bad.validate.missing.indexOf('PostingType') >= 0,
        'TrialBalance (310) missing a mandatory para → REJECTED before doIt (not a silent run)', 'missing=PostingType');

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 3 · §FALSIFIER — an UNREGISTERED classname → explicit "absent handler" (NOT a silent no-op).
//     203 Balance_Update (org.compiere.report.FinBalance) is a REAL classname with NO registered handler.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §FALSIFIER (real proc, classname NOT in registry → explicit absent-handler) —');
var r203 = P.dispatch(db, { AD_Process_ID: 203, params: {} }, ctx);
console.log('§FALSIFIER AD_Process_ID=203 classname=' + r203.classname + ' dispatched=' + r203.dispatched + ' reason=' + r203.reason +
            ' msg="' + r203.message + '"');
verdict(r203.dispatched === false && r203.reason === 'absent-handler' && /Failed to create new process instance/.test(r203.message),
        'unregistered classname → explicit absent-handler (proves classname→handler resolution is real)',
        'a silent no-op would have dispatched=true');
// contrast: prove the SAME spine DOES dispatch when the classname IS registered (load-bearing resolution)
console.log('§FALSIFIER load-bearing: 110(registered).dispatched=' + r110.dispatched + ' vs 203(unregistered).dispatched=' + r203.dispatched);
verdict(r110.dispatched === true && r203.dispatched === false,
        'classname resolution is LOAD-BEARING: registered=dispatch, unregistered=absent — not a relabel', '');

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 4 · §PROC_COVERAGE — honest dispatched-vs-total. Walk EVERY ad_process row; count how many resolve to a
//     registered handler. Name the unported remainder explicitly (the point: MECHANISM, not corpus).
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §PROC_COVERAGE (every real ad_process row classified; dispatched vs total) —');
var allProcs = db.prepare('SELECT ad_process_id FROM ad_process ORDER BY ad_process_id').all();
var total = allProcs.length;
var withClassname = db.prepare("SELECT count(*) AS c FROM ad_process WHERE classname IS NOT NULL AND classname<>''").get().c;
var dispatched = 0, absent = 0, parseErrs = 0;
allProcs.forEach(function (row) {
  var cn;
  // honest handler-resolution: does this proc's resolved classname have a registered handler? (independent
  // of params — a registered handler that merely needs params is still "dispatched-capable", not absent.)
  try { cn = P.resolveClassname(P.readProcess(db, row.ad_process_id)); }
  catch (e) { parseErrs++; return; }
  if (cn && P.hasHandler(cn)) dispatched++; else absent++;
});
var registered = P.registeredClassnames().length;
console.log('§PROC_COVERAGE registered=' + registered + ' dispatched=' + dispatched + ' of ' + total +
            ' AD_Process (classname≠null ' + withClassname + ') absent=' + absent + ' parseErrors=' + parseErrs);
console.log('  unported remainder = ' + absent + ' procs whose classname has no JS handler — the 54k-LOC SvrProcess ' +
            'corpus (FinBalance, M_Production_Run, FactReconcile, …) is NAMED-DEFERRED, not ported. The spine is the mechanism, not the corpus.');
verdict(parseErrs === 0 && registered >= 4 && dispatched >= 4 && total === 476 && (dispatched + absent) === total,
        'every one of ' + total + ' ad_process rows classified without error; ' + dispatched + ' resolve to a handler, ' + absent + ' named-deferred',
        registered + ' handlers registered');

db.close();

console.log('\n' + (fails ? '🔴 ' + fails + ' FAIL' : '═══ ALL PASS ═══'));
process.exit(fails ? 1 : 0);
