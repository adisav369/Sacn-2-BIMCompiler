// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-PROC-PPORDER is ✅ DONE in AD_PROCESS_FOLD_LANE.md §P2-tail-leg4.
// SCOPE: W-PROC-PPORDER — AD_Process 53028 ("Rpt PP_Order", "Manufacturing Order", blank classname, isReport=Y)
//   dispatched through build/erp/ad_process.js resolves to the report:pp_order key and folds a PP_Order → a receipt
//   via the EXISTING report_overlay.foldReceipt over the NEW REPORT_MAP.pp_order row (KIND 1, NO new fold code —
//   a 4th document family beyond sales/inventory: MANUFACTURING). A manufacturing order is a NON-FINANCIAL
//   document → subtotal/tax/total stay null; the carried value per BOM line is qty=QtyRequiered (the required
//   component quantity), never money.
//   Proves: (a) the blank classname resolves to report:pp_order (the JASPER_STARTER report-key path), registered;
//   (b) a real PP_Order (50000, DocumentNo 8000, 2 BOM lines, products 50008/50013) folds to lines + financial=
//   false (subtotal/tax/total all null) + docno=DocumentNo + date=DatePromised + partner=null (internal, no BP);
//   (c) every folded line qty == its PP_Order_BOMLine.QtyRequiered, price/amount null (independent re-derivation,
//   incl. the non-integer 5050.50505051 — proves the fold carries the raw value, no rounding);
//   (d) a §FALSIFIER (bend a line QtyRequiered +7) shifts the folded qty exactly (proves the fold READS the rows);
//   (e) the GUARD — 120 "RV_PP_Order_Transactions" + 53030 "Manufacturing Orders Review" (both isReport=Y report-
//   VIEWs) + 53026 "PP_Order" (isReport=N) stay absent-handler. The match uses WORD-BOUNDARIES (`pp_order\b` skips
//   "rv_pp_order_transactions" — `_` is a word char; `manufacturing order\b` skips "manufacturing orders review"
//   — the "s" breaks the boundary), and resolveClassname only fires for isReport procs (so 53026 never reaches it).
// NON-INVENT: PP_Order 50000 + its BOM lines/products are REAL ad_full.db rows. A manufacturing order carries qty.
// §-log first — READ build/erp/poc_proc_pporder.log before concluding (exit code ≠ evidence).
// Run:  node scripts/poc_proc_pporder.js 2>&1 | tee build/erp/poc_proc_pporder.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var P  = require(path.join(ERP, 'ad_process.js'));
var RC = require(path.join(ERP, 'report_overlay.js'));
var DB_PATH = path.join(ERP, 'ad_full.db');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log((ok ? '🟢' : '🔴') + ' ' + label + (detail ? '  — ' + detail : '')); }

console.log('═══ W-PROC-PPORDER — Rpt PP_Order (AD_Process 53028) folds a PP_Order → receipt via foldReceipt (non-financial) ═══\n');

var db = new Database(DB_PATH, { readonly: true });
P.installDefaultHandlers(RC);
var PPO_ID = 50000;                                          // a real manufacturing order (DocumentNo 8000, 2 BOM lines)

// ctx — the report-handler accessors (sqlite fetchers off ad_full.db), mirroring poc_proc_minventory.js / host _procCtx.
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
// 1 · §PPORDER-DISPATCH — 53028 (blank classname) resolves to report:pp_order, registered.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('— §PPORDER-DISPATCH (real AD_Process 53028 row → blank classname → report:pp_order) —');
var proc = P.readProcess(db, 53028);
var resolved = P.resolveClassname(proc);
console.log('§PPORDER-DISPATCH proc=53028 value=' + proc.value + ' name="' + proc.name + '" classname="' + proc.classname + '" isReport=' + proc.isReport + ' resolved=' + resolved + ' hasHandler=' + P.hasHandler(resolved));
verdict(proc.classname === '' && proc.isReport === true && resolved === 'report:pp_order' && P.hasHandler('report:pp_order'),
        'AD_Process 53028 = Rpt PP_Order (blank classname) resolves to the registered report:pp_order handler', 'value=' + proc.value);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 2 · §PPORDER-FOLD — fold PP_Order 50000 → a receipt; NON-FINANCIAL (subtotal/tax/total null), qty carried.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §PPORDER-FOLD (PP_Order 50000 → receipt: 2 BOM lines + non-financial via foldReceipt) —');
var rawHdr = db.prepare('SELECT * FROM pp_order WHERE pp_order_id=?').get(PPO_ID);
var rawLines = db.prepare('SELECT * FROM pp_order_bomline WHERE pp_order_id=? ORDER BY line').all(PPO_ID);
var r = P.dispatch(db, { AD_Process_ID: 53028, Record_ID: PPO_ID, params: {} }, ctx());
var rec = r.result || {};
console.log('§PPORDER-FOLD dispatched=' + r.dispatched + ' classname=' + r.classname + ' kind=' + r.kind + ' rows=' + r.rows +
            ' docno=' + rec.docno + ' date=' + rec.date + ' partner=' + rec.partner + ' subtotal=' + rec.subtotal + ' tax=' + rec.tax + ' total=' + rec.total + ' financial=' + rec.financial);
verdict(r.dispatched === true && r.classname === 'report:pp_order' && rec.foldsFrom === 'bundle' &&
        rec.financial === false && rec.subtotal === null && rec.tax === null && rec.total === null &&
        rec.partner === null && rec.lines.length === rawLines.length && rec.lines.length === 2 && rec.docno === rawHdr.documentno,
        'PP_Order 50000 folds to a NON-FINANCIAL receipt (2 lines from PP_Order_BOMLine, docno=DocumentNo, partner null, no fabricated total)',
        'docno=' + rec.docno + ' lines=' + rec.lines.length + ' financial=' + rec.financial + ' partner=' + rec.partner);

// ORACLE: independent re-derivation. A manufacturing order is non-financial → no money to fold; the carried value
// is qty=QtyRequiered per BOM line. subtotal/tax/total must be null (honest "n/a"); each folded qty == its source
// QtyRequiered EXACTLY (incl. the non-integer 5050.50505051 — the fold must NOT round it); price/amount null.
var qmism = 0;
rec.lines.forEach(function (ol, i) {
  var src = rawLines[i];
  if (Number(ol.qty) !== Number(src.qtyrequiered)) { qmism++; console.log('   ⚠ FINDING line ' + i + ' qty eng=' + ol.qty + ' src=' + src.qtyrequiered); }
  if (ol.price !== null) { qmism++; console.log('   ⚠ FINDING line ' + i + ' price NOT null (a BOM line carries no price): ' + ol.price); }
  if (ol.amount !== null) { qmism++; console.log('   ⚠ FINDING line ' + i + ' amount NOT null (a BOM line carries no money): ' + ol.amount); }
});
console.log('§PPORDER-FOLD per-line qty=QtyRequiered (price/amount null) mismatches=' + qmism + ' (line0 qty=' + rec.lines[0].qty + ' = raw ' + rawLines[0].qtyrequiered + ')');
verdict(qmism === 0, 'every folded line qty == its PP_Order_BOMLine.QtyRequiered EXACTLY (incl. 5050.50505051, no rounding); price/amount honestly null', 'lines=' + rec.lines.length);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 3 · §PPORDER-FALSIFIER — bend a line QtyRequiered +7 → the folded qty must move (proves it's read, not fabricated).
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §PPORDER-FALSIFIER (bend line QtyRequiered +7 → folded qty shifts by exactly +7) —');
var rBent = P.dispatch(db, { AD_Process_ID: 53028, Record_ID: PPO_ID, params: {} }, ctx(function (l) { return { qtyrequiered: Number(l.qtyrequiered || 0) + 7 }; }));
var bentQ = Number(rBent.result.lines[0].qty), baseQ = Number(rec.lines[0].qty);
console.log('§PPORDER-FALSIFIER line0 qty bent=' + bentQ + ' expect=' + (baseQ + 7) + ' (baseline=' + baseQ + ')');
verdict(Math.abs(bentQ - (baseQ + 7)) < 1e-6 && bentQ !== baseQ,
        'bending a line QtyRequiered shifts the folded qty exactly (the fold reads the rows, never a fabricated qty)', 'Δ = +7');

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 4 · §PPORDER-GUARD — 120 RV_PP_Order_Transactions + 53030 Manufacturing Orders Review + 53026 PP_Order(proc)
//     stay ABSENT-handler — none is mis-routed to report:pp_order (word-boundary match + isReport gate).
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §PPORDER-GUARD (RV_PP_Order_Transactions / Manufacturing Orders Review / PP_Order-process are NOT mis-routed to report:pp_order) —');
var guardIds = [120, 53030, 53026];
var leaked = [];
guardIds.forEach(function (id) {
  var pr; try { pr = P.readProcess(db, id); } catch (e) { return; }
  var rv = P.resolveClassname(pr);
  console.log('§PPORDER-GUARD proc=' + id + ' value="' + pr.value.trim() + '" name="' + pr.name.trim() + '" isReport=' + pr.isReport + ' resolved="' + rv + '"');
  if (rv === 'report:pp_order') leaked.push(id);
});
verdict(leaked.length === 0,
        'no RV_PP_Order_Transactions / Manufacturing Orders Review / PP_Order-process is mis-routed to report:pp_order (word-boundary match + isReport gate)', 'leaked=[' + leaked.join(',') + ']');

// 120 is a report-VIEW (no foldReceipt route, blank classname) → an honest non-fold, NEVER a fabricated receipt.
var r120 = P.dispatch(db, { AD_Process_ID: 120, params: {} }, ctx());
console.log('§PPORDER-GUARD 120 RV_PP_Order_Transactions dispatched=' + r120.dispatched + ' reason=' + r120.reason + ' result=' + (r120.result ? 'present' : 'none'));
verdict(r120.dispatched === false && (r120.reason === 'absent-handler' || r120.reason === 'param-validation-failed') && !r120.result,
        'RV_PP_Order_Transactions (120) is an honest non-fold, NEVER a fabricated receipt', 'reason=' + r120.reason);

db.close();
console.log('\n' + (fails ? '🔴 ' + fails + ' check(s) FAILED — W-PROC-PPORDER NOT proven' : '═══ ALL PASS — Rpt PP_Order folds a manufacturing order → non-financial receipt via the existing foldReceipt (KIND-1, no new fold code, 4th document family) — W-PROC-PPORDER ═══'));
process.exit(fails ? 1 : 0);
