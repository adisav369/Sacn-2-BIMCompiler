// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-PROC-PROJPRINT is ✅ DONE in AD_PROCESS_FOLD_LANE.md §P2-leg3.
// SCOPE: W-PROC-PROJPRINT — AD_Process 217 ("Rpt C_Project", Project Print, blank classname, isReport=Y)
//   dispatched through build/erp/ad_process.js resolves to the report:c_project key and folds a C_Project →
//   a receipt via the EXISTING report_overlay.foldReceipt (KIND 1, NO new fold code — a new REPORT_MAP row).
//   Proves: (a) the blank classname resolves to report:c_project (the JASPER_STARTER report-key path);
//   (b) a real project (101 "Landscape": 1 line, PlannedAmt 600) folds to lines + subtotal/total/tax, EXACT
//   (subtotal = Σ line PlannedAmt, total = header PlannedAmt) diffed against an INDEPENDENT BigDecimal
//   re-derivation; (c) docno reads C_Project.Value (no DocumentNo on a project); (d) a §FALSIFIER (bent line
//   PlannedAmt) breaks the subtotal diff; (e) the broad-match GUARD — RV_ProjectCycle (218) and the other
//   RV_Project* report-VIEW procs stay absent-handler (NOT mis-routed to foldReceipt).
// NON-INVENT: project 101 + its line/product are REAL ad_full.db rows. Money via bigdecimal.js (== Java).
// §-log first — READ build/erp/poc_proc_projprint.log before concluding (exit code ≠ evidence).
// Run:  node scripts/poc_proc_projprint.js 2>&1 | tee build/erp/poc_proc_projprint.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var P  = require(path.join(ERP, 'ad_process.js'));
var RC = require(path.join(ERP, 'report_overlay.js'));
var BD = require(path.join(ERP, 'bigdecimal.js'));
var DB_PATH = path.join(ERP, 'ad_full.db');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log((ok ? '🟢' : '🔴') + ' ' + label + (detail ? '  — ' + detail : '')); }

console.log('═══ W-PROC-PROJPRINT — Rpt C_Project (AD_Process 217) folds a C_Project → receipt via foldReceipt ═══\n');

var db = new Database(DB_PATH, { readonly: true });
P.installDefaultHandlers(RC);
var PROJECT_ID = 101;                                          // a real project (Landscape: 1 line, PlannedAmt 600)

// ctx — the report-handler accessors (sqlite fetchers off ad_full.db), mirroring poc_proc.js / host _procCtx.
function firstId(table, pk) { var r = db.prepare('SELECT ' + pk + ' AS id FROM ' + table + ' ORDER BY ' + pk + ' LIMIT 1').get(); return r && r.id; }
function ctx(lineMut) {
  return {
    fetchHeader: function (key, info) {
      var map = RC.REPORT_MAP[key]; var id = (info && info.Record_ID != null) ? info.Record_ID : firstId(map.key, map.pk);
      if (id == null) return null;
      return db.prepare('SELECT * FROM ' + map.key + ' WHERE ' + map.pk + '=?').get(id) || null;
    },
    fetchLines: function (key, info) {
      var map = RC.REPORT_MAP[key]; var id = (info && info.Record_ID != null) ? info.Record_ID : firstId(map.key, map.pk);
      if (id == null) return [];
      var rows = db.prepare('SELECT * FROM ' + map.lineTable + ' WHERE ' + map.fk + '=? ORDER BY line').all(id);
      return lineMut ? rows.map(function (r) { return Object.assign({}, r, lineMut(r)); }) : rows;
    },
    names: function (key, hdr) {
      var nm = { products: {} };
      if (hdr && hdr.c_bpartner_id != null) { var bp = db.prepare('SELECT name FROM c_bpartner WHERE c_bpartner_id=?').get(hdr.c_bpartner_id); nm.partner = bp && bp.name; }
      return nm;
    }
  };
}

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 1 · §PROJPRINT-DISPATCH — 217 (blank classname) resolves to report:c_project, registered.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('— §PROJPRINT-DISPATCH (real AD_Process 217 row → blank classname → report:c_project) —');
var proc217 = P.readProcess(db, 217);
var resolved = P.resolveClassname(proc217);
console.log('§PROJPRINT-DISPATCH proc=217 value=' + proc217.value + ' classname="' + proc217.classname + '" isReport=' + proc217.isReport + ' resolved=' + resolved + ' hasHandler=' + P.hasHandler(resolved));
verdict(proc217.classname === '' && proc217.isReport === true && resolved === 'report:c_project' && P.hasHandler('report:c_project'),
        'AD_Process 217 = Rpt C_Project (blank classname) resolves to the registered report:c_project handler', 'value=' + proc217.value);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 2 · §PROJPRINT-FOLD — fold project 101 → a receipt; subtotal/total/tax EXACT vs an independent re-derivation.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §PROJPRINT-FOLD (project 101 → receipt: lines + subtotal/total/tax via foldReceipt) —');
var rawHdr = db.prepare('SELECT * FROM c_project WHERE c_project_id=?').get(PROJECT_ID);
var rawLines = db.prepare('SELECT * FROM c_projectline WHERE c_project_id=? ORDER BY line').all(PROJECT_ID);
var r = P.dispatch(db, { AD_Process_ID: 217, Record_ID: PROJECT_ID, params: {} }, ctx());
var rec = r.result || {};
console.log('§PROJPRINT-FOLD dispatched=' + r.dispatched + ' classname=' + r.classname + ' kind=' + r.kind + ' rows=' + r.rows +
            ' docno=' + rec.docno + ' date=' + rec.date + ' partner=' + rec.partner + ' subtotal=' + rec.subtotal + ' tax=' + rec.tax + ' total=' + rec.total + ' financial=' + rec.financial);
verdict(r.dispatched === true && r.classname === 'report:c_project' && rec.foldsFrom === 'bundle' && rec.financial === true &&
        rec.lines.length === rawLines.length && rec.docno === rawHdr.value,
        'project 101 folds to a receipt (lines from C_ProjectLine, docno = C_Project.Value)', 'docno=' + rec.docno + ' lines=' + rec.lines.length);

// ORACLE: independent BigDecimal re-derivation. subtotal = Σ line PlannedAmt; total = header PlannedAmt; tax = total − subtotal.
var subB = rawLines.reduce(function (a, l) { return a.add(BD.of(String(l.plannedamt == null ? 0 : l.plannedamt))); }, BD.ZERO);
var totB = BD.of(String(rawHdr.plannedamt == null ? 0 : rawHdr.plannedamt));
var taxB = totB.subtract(subB);
function m2(b) { return b.setScale(2, BD.RoundingMode.HALF_UP).toString(); }
console.log('§PROJPRINT-ORACLE subtotal=' + m2(subB) + ' total=' + m2(totB) + ' tax=' + m2(taxB) + ' (Σ PlannedAmt vs header PlannedAmt)');
verdict(rec.subtotal === m2(subB) && rec.total === m2(totB) && rec.tax === m2(taxB),
        'subtotal/total/tax == independent re-derivation, EXACT (BigDecimal, == Java)', 'eng{' + rec.subtotal + '/' + rec.total + '/' + rec.tax + '} oracle{' + m2(subB) + '/' + m2(totB) + '/' + m2(taxB) + '}');

// per-line qty/price exact
var lmism = 0;
rec.lines.forEach(function (ol, i) {
  var src = rawLines[i];
  if (Number(ol.qty) !== Number(src.plannedqty)) { lmism++; console.log('   ⚠ FINDING line ' + i + ' qty eng=' + ol.qty + ' src=' + src.plannedqty); }
  if (Number(ol.price) !== Number(src.plannedprice)) { lmism++; console.log('   ⚠ FINDING line ' + i + ' price eng=' + ol.price + ' src=' + src.plannedprice); }
  if (BD.of(String(ol.amount)).compareTo(BD.of(String(src.plannedamt))) !== 0) { lmism++; console.log('   ⚠ FINDING line ' + i + ' amount eng=' + ol.amount + ' src=' + src.plannedamt); }
});
console.log('§PROJPRINT-FOLD per-line qty/price/amount mismatches=' + lmism);
verdict(lmism === 0, 'every folded line == its C_ProjectLine row (qty=PlannedQty, price=PlannedPrice, amount=PlannedAmt)', 'lines=' + rec.lines.length);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 3 · §PROJPRINT-FALSIFIER — bend a line PlannedAmt → the subtotal must move (proves it's read, not fabricated).
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §PROJPRINT-FALSIFIER (bend line PlannedAmt +111 → subtotal shifts by exactly +111) —');
var rBent = P.dispatch(db, { AD_Process_ID: 217, Record_ID: PROJECT_ID, params: {} }, ctx(function (l) { return { plannedamt: Number(l.plannedamt || 0) + 111 }; }));
var bentSub = rBent.result.subtotal, expectSub = m2(subB.add(BD.of(String(111 * rawLines.length))));
console.log('§PROJPRINT-FALSIFIER subtotal bent=' + bentSub + ' expect=' + expectSub + ' (baseline=' + rec.subtotal + ')');
verdict(bentSub === expectSub && bentSub !== rec.subtotal,
        'bending a line PlannedAmt shifts the subtotal exactly (the fold reads the rows, never a fabricated total)', 'Δ per line = +111');

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 4 · §PROJPRINT-GUARD — RV_ProjectCycle (218) + the RV_Project* report-VIEW procs stay ABSENT-handler.
//     The c_project match is SPECIFIC — it must NOT mis-route a "Project ..." report-view to foldReceipt.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §PROJPRINT-GUARD (RV_Project* report-view procs are NOT mis-routed to report:c_project) —');
var guardIds = [218, 226, 228, 229, 234];
var leaked = [];
guardIds.forEach(function (id) {
  var pr; try { pr = P.readProcess(db, id); } catch (e) { return; }
  var rv = P.resolveClassname(pr);
  console.log('§PROJPRINT-GUARD proc=' + id + ' value=' + pr.value + ' resolved="' + rv + '"');
  if (rv === 'report:c_project') leaked.push(id);
});
verdict(leaked.length === 0,
        'no RV_Project* report-VIEW proc is mis-routed to report:c_project (the match is C_Project-specific)', 'leaked=[' + leaked.join(',') + ']');

// 218 has no foldReceipt route → it is NEVER dispatched to a fabricated report. The honest rejection is either
// 'absent-handler' (blank classname, no registry entry) or — reached first — 'param-validation-failed' (218
// carries a mandatory param we did not supply). Both are honest non-folds; neither produces a receipt.
var r218 = P.dispatch(db, { AD_Process_ID: 218, params: {} }, ctx());
console.log('§PROJPRINT-GUARD 218 RV_ProjectCycle dispatched=' + r218.dispatched + ' reason=' + r218.reason + ' result=' + (r218.result ? 'present' : 'none'));
verdict(r218.dispatched === false && (r218.reason === 'absent-handler' || r218.reason === 'param-validation-failed') && !r218.result,
        'RV_ProjectCycle (218) is an honest non-fold (a report-view fold is a later leg), NEVER a fabricated receipt', 'reason=' + r218.reason);

db.close();
console.log('\n' + (fails ? '🔴 ' + fails + ' check(s) FAILED — W-PROC-PROJPRINT NOT proven' : '═══ ALL PASS — Rpt C_Project folds a C_Project → receipt via the existing foldReceipt (KIND-1, no new fold code) — W-PROC-PROJPRINT ═══'));
process.exit(fails ? 1 : 0);
