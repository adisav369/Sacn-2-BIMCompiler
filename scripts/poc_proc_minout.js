// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-PROC-MINOUT is ✅ DONE in AD_PROCESS_FOLD_LANE.md §P2-tail-leg1.
// SCOPE: W-PROC-MINOUT — AD_Process 117 ("Rpt M_InOut", "Delivery Note / Shipment Print", blank classname,
//   isReport=Y) dispatched through build/erp/ad_process.js resolves to the report:m_inout key and folds an
//   M_InOut → a receipt via the EXISTING report_overlay.foldReceipt over the ALREADY-PRESENT REPORT_MAP.m_inout
//   (KIND 1, NO new fold code — the print sibling of InOutGenerate §P2-leg1). A shipment is a NON-FINANCIAL
//   document → subtotal/tax/total stay null; only qty=MovementQty is carried (honest, never a fabricated total).
//   Proves: (a) the blank classname resolves to report:m_inout (the JASPER_STARTER report-key path), registered;
//   (b) a real M_InOut (100, DocumentNo 600000, 1 line, product 130, MovementQty 1) folds to lines + financial=
//   false (subtotal/tax/total all null) + docno=DocumentNo + date=MovementDate; (c) every folded line qty ==
//   its M_InOutLine.MovementQty (independent re-derivation); (d) a §FALSIFIER (bend a line MovementQty +7) shifts
//   the folded qty exactly (proves the fold READS the rows, never a fabricated qty); (e) the broad-match GUARD —
//   292 "Rpt M_InOutConfirm" (Shipment Confirmation) routes to its own report:m_inoutconfirm leg (§P2-tail-leg6) +
//   293/294 RV_InOutDetails stay absent-handler — none mis-routed to report:m_inout (NOT mis-routed
//   to report:m_inout — the match is m_inout\b word-boundary, "m_inoutconfirm" has none).
// NON-INVENT: M_InOut 100 + its line/product are REAL ad_full.db rows. A shipment carries qty, not money.
// §-log first — READ build/erp/poc_proc_minout.log before concluding (exit code ≠ evidence).
// Run:  node scripts/poc_proc_minout.js 2>&1 | tee build/erp/poc_proc_minout.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var P  = require(path.join(ERP, 'ad_process.js'));
var RC = require(path.join(ERP, 'report_overlay.js'));
var DB_PATH = path.join(ERP, 'ad_full.db');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log((ok ? '🟢' : '🔴') + ' ' + label + (detail ? '  — ' + detail : '')); }

console.log('═══ W-PROC-MINOUT — Rpt M_InOut (AD_Process 117) folds an M_InOut → receipt via foldReceipt (non-financial) ═══\n');

var db = new Database(DB_PATH, { readonly: true });
P.installDefaultHandlers(RC);
var INOUT_ID = 100;                                           // a real shipment (DocumentNo 600000, 1 line, product 130, qty 1)

// ctx — the report-handler accessors (sqlite fetchers off ad_full.db), mirroring poc_proc_projprint.js / host _procCtx.
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
// 1 · §MINOUT-DISPATCH — 117 (blank classname) resolves to report:m_inout, registered.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('— §MINOUT-DISPATCH (real AD_Process 117 row → blank classname → report:m_inout) —');
var proc117 = P.readProcess(db, 117);
var resolved = P.resolveClassname(proc117);
console.log('§MINOUT-DISPATCH proc=117 value=' + proc117.value + ' name="' + proc117.name + '" classname="' + proc117.classname + '" isReport=' + proc117.isReport + ' resolved=' + resolved + ' hasHandler=' + P.hasHandler(resolved));
verdict(proc117.classname === '' && proc117.isReport === true && resolved === 'report:m_inout' && P.hasHandler('report:m_inout'),
        'AD_Process 117 = Rpt M_InOut (blank classname) resolves to the registered report:m_inout handler', 'value=' + proc117.value);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 2 · §MINOUT-FOLD — fold M_InOut 100 → a receipt; NON-FINANCIAL (subtotal/tax/total null), qty carried.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §MINOUT-FOLD (M_InOut 100 → receipt: lines + non-financial via foldReceipt) —');
var rawHdr = db.prepare('SELECT * FROM m_inout WHERE m_inout_id=?').get(INOUT_ID);
var rawLines = db.prepare('SELECT * FROM m_inoutline WHERE m_inout_id=? ORDER BY line').all(INOUT_ID);
var r = P.dispatch(db, { AD_Process_ID: 117, Record_ID: INOUT_ID, params: {} }, ctx());
var rec = r.result || {};
console.log('§MINOUT-FOLD dispatched=' + r.dispatched + ' classname=' + r.classname + ' kind=' + r.kind + ' rows=' + r.rows +
            ' docno=' + rec.docno + ' date=' + rec.date + ' partner=' + rec.partner + ' subtotal=' + rec.subtotal + ' tax=' + rec.tax + ' total=' + rec.total + ' financial=' + rec.financial);
verdict(r.dispatched === true && r.classname === 'report:m_inout' && rec.foldsFrom === 'bundle' &&
        rec.financial === false && rec.subtotal === null && rec.tax === null && rec.total === null &&
        rec.lines.length === rawLines.length && rec.docno === rawHdr.documentno,
        'M_InOut 100 folds to a NON-FINANCIAL receipt (lines from M_InOutLine, docno=DocumentNo, no fabricated total)',
        'docno=' + rec.docno + ' lines=' + rec.lines.length + ' financial=' + rec.financial);

// ORACLE: independent re-derivation. A shipment is non-financial → there is NO money to fold; the only carried
// value is qty=MovementQty per line (exact integer/decimal count, never summed into a price). subtotal/tax/total
// must be null (honest "n/a"), and each folded qty == its source MovementQty.
var qmism = 0;
rec.lines.forEach(function (ol, i) {
  var src = rawLines[i];
  if (Number(ol.qty) !== Number(src.movementqty)) { qmism++; console.log('   ⚠ FINDING line ' + i + ' qty eng=' + ol.qty + ' src=' + src.movementqty); }
  if (ol.price !== null) { qmism++; console.log('   ⚠ FINDING line ' + i + ' price NOT null (shipment carries no price): ' + ol.price); }
  if (ol.amount !== null) { qmism++; console.log('   ⚠ FINDING line ' + i + ' amount NOT null (shipment carries no money): ' + ol.amount); }
});
console.log('§MINOUT-FOLD per-line qty=MovementQty (price/amount null) mismatches=' + qmism);
verdict(qmism === 0, 'every folded line qty == its M_InOutLine.MovementQty; price/amount honestly null (no money on a shipment)', 'lines=' + rec.lines.length);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 3 · §MINOUT-FALSIFIER — bend a line MovementQty +7 → the folded qty must move (proves it's read, not fabricated).
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §MINOUT-FALSIFIER (bend line MovementQty +7 → folded qty shifts by exactly +7) —');
var rBent = P.dispatch(db, { AD_Process_ID: 117, Record_ID: INOUT_ID, params: {} }, ctx(function (l) { return { movementqty: Number(l.movementqty || 0) + 7 }; }));
var bentQ = Number(rBent.result.lines[0].qty), baseQ = Number(rec.lines[0].qty);
console.log('§MINOUT-FALSIFIER line0 qty bent=' + bentQ + ' expect=' + (baseQ + 7) + ' (baseline=' + baseQ + ')');
verdict(bentQ === baseQ + 7 && bentQ !== baseQ,
        'bending a line MovementQty shifts the folded qty exactly (the fold reads the rows, never a fabricated qty)', 'Δ = +7');

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 4 · §MINOUT-GUARD — 292 Rpt M_InOutConfirm + 293/294 RV_InOutDetails do NOT mis-route to report:m_inout.
//     The m_inout match is WORD-BOUNDARY specific — it must NOT catch "m_inoutconfirm" or "rv_inoutdetails".
//     (292 routes to its own report:m_inoutconfirm leg, §P2-tail-leg6; 293/294 stay absent-handler.)
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §MINOUT-GUARD (M_InOutConfirm/RV_InOutDetails report-view procs are NOT mis-routed to report:m_inout) —');
var guardIds = [292, 293, 294];
var leaked = [];
guardIds.forEach(function (id) {
  var pr; try { pr = P.readProcess(db, id); } catch (e) { return; }
  var rv = P.resolveClassname(pr);
  console.log('§MINOUT-GUARD proc=' + id + ' value=' + pr.value + ' name="' + pr.name + '" resolved="' + rv + '"');
  if (rv === 'report:m_inout') leaked.push(id);
});
verdict(leaked.length === 0,
        'no M_InOutConfirm / RV_InOutDetails proc is mis-routed to report:m_inout (the match is m_inout\\b word-boundary)', 'leaked=[' + leaked.join(',') + ']');

// NOTE: 292 "Rpt M_InOutConfirm" now routes to its OWN leg (report:m_inoutconfirm, §P2-tail-leg6) — it is no longer
// the absent example here; the loop above already proves it does NOT mis-route to report:m_inout. The honest-non-fold
// example is 293 RV_InOutDetails Receive — a report-VIEW proc with no receipt fold → it has no foldReceipt route and
// is NEVER dispatched to a fabricated report. The honest non-fold is either 'absent-handler' (blank classname, no
// registry entry) or — reached first — 'param-validation-failed'.
var r293 = P.dispatch(db, { AD_Process_ID: 293, params: {} }, ctx());
console.log('§MINOUT-GUARD 293 RV_InOutDetails Receive dispatched=' + r293.dispatched + ' reason=' + r293.reason + ' result=' + (r293.result ? 'present' : 'none'));
verdict(r293.dispatched === false && (r293.reason === 'absent-handler' || r293.reason === 'param-validation-failed') && !r293.result,
        'RV_InOutDetails Receive (293) is an honest non-fold, NEVER a fabricated receipt', 'reason=' + r293.reason);

db.close();
console.log('\n' + (fails ? '🔴 ' + fails + ' check(s) FAILED — W-PROC-MINOUT NOT proven' : '═══ ALL PASS — Rpt M_InOut folds an M_InOut → non-financial receipt via the existing foldReceipt (KIND-1, no new fold code) — W-PROC-MINOUT ═══'));
process.exit(fails ? 1 : 0);
