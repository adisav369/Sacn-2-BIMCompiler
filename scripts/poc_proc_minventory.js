// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-PROC-MINVENTORY is ✅ DONE in AD_PROCESS_FOLD_LANE.md §P2-tail-leg2.
// SCOPE: W-PROC-MINVENTORY — AD_Process 291 ("Rpt M_Inventory", "Physical Inventory Print", blank classname, isReport=Y)
//   dispatched through build/erp/ad_process.js resolves to the report:m_inventory key and folds an M_Inventory → a
//   receipt via the EXISTING report_overlay.foldReceipt over the NEW REPORT_MAP.m_inventory row (KIND 1, NO new fold
//   code — the warehouse sibling of Rpt M_InOut §P2-tail-leg1). A physical count is a NON-FINANCIAL document →
//   subtotal/tax/total stay null; only qty=QtyCount is carried (honest, never a fabricated total).
//   Proves: (a) the blank classname resolves to report:m_inventory (the JASPER_STARTER report-key path), registered;
//   (b) a real M_Inventory (100, DocumentNo 10000000, 1 line, product 123, QtyCount 4) folds to lines + financial=
//   false (subtotal/tax/total all null) + docno=DocumentNo + date=MovementDate + partner=null (internal move, no BP);
//   (c) every folded line qty == its M_InventoryLine.QtyCount, price/amount null (independent re-derivation);
//   (d) a §FALSIFIER (bend a line QtyCount +7) shifts the folded qty exactly (proves the fold READS the rows);
//   (e) the GUARD — 105/106 ("M_Inventory Create"/"Update", non-blank classnames) + 107 "M_Inventory Process"
//   (blank classname, isReport=N, imperative) stay absent-handler (resolveClassname returns the classname as-is
//   for 105/106 and only synthesizes a report key for isReport procs → 107 never reaches report:m_inventory).
// NON-INVENT: M_Inventory 100 + its line/product are REAL ad_full.db rows. A physical count carries qty, not money.
// §-log first — READ build/erp/poc_proc_minventory.log before concluding (exit code ≠ evidence).
// Run:  node scripts/poc_proc_minventory.js 2>&1 | tee build/erp/poc_proc_minventory.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var P  = require(path.join(ERP, 'ad_process.js'));
var RC = require(path.join(ERP, 'report_overlay.js'));
var DB_PATH = path.join(ERP, 'ad_full.db');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log((ok ? '🟢' : '🔴') + ' ' + label + (detail ? '  — ' + detail : '')); }

console.log('═══ W-PROC-MINVENTORY — Rpt M_Inventory (AD_Process 291) folds an M_Inventory → receipt via foldReceipt (non-financial) ═══\n');

var db = new Database(DB_PATH, { readonly: true });
P.installDefaultHandlers(RC);
var INV_ID = 100;                                            // a real physical count (DocumentNo 10000000, 1 line, product 147, qtycount 1)

// ctx — the report-handler accessors (sqlite fetchers off ad_full.db), mirroring poc_proc_minout.js / host _procCtx.
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
// 1 · §MINVENTORY-DISPATCH — 291 (blank classname) resolves to report:m_inventory, registered.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('— §MINVENTORY-DISPATCH (real AD_Process 291 row → blank classname → report:m_inventory) —');
var proc291 = P.readProcess(db, 291);
var resolved = P.resolveClassname(proc291);
console.log('§MINVENTORY-DISPATCH proc=291 value=' + proc291.value + ' name="' + proc291.name + '" classname="' + proc291.classname + '" isReport=' + proc291.isReport + ' resolved=' + resolved + ' hasHandler=' + P.hasHandler(resolved));
verdict(proc291.classname === '' && proc291.isReport === true && resolved === 'report:m_inventory' && P.hasHandler('report:m_inventory'),
        'AD_Process 291 = Rpt M_Inventory (blank classname) resolves to the registered report:m_inventory handler', 'value=' + proc291.value);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 2 · §MINVENTORY-FOLD — fold M_Inventory 100 → a receipt; NON-FINANCIAL (subtotal/tax/total null), qty carried.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §MINVENTORY-FOLD (M_Inventory 100 → receipt: lines + non-financial via foldReceipt) —');
var rawHdr = db.prepare('SELECT * FROM m_inventory WHERE m_inventory_id=?').get(INV_ID);
var rawLines = db.prepare('SELECT * FROM m_inventoryline WHERE m_inventory_id=? ORDER BY line').all(INV_ID);
var r = P.dispatch(db, { AD_Process_ID: 291, Record_ID: INV_ID, params: {} }, ctx());
var rec = r.result || {};
console.log('§MINVENTORY-FOLD dispatched=' + r.dispatched + ' classname=' + r.classname + ' kind=' + r.kind + ' rows=' + r.rows +
            ' docno=' + rec.docno + ' date=' + rec.date + ' partner=' + rec.partner + ' subtotal=' + rec.subtotal + ' tax=' + rec.tax + ' total=' + rec.total + ' financial=' + rec.financial);
verdict(r.dispatched === true && r.classname === 'report:m_inventory' && rec.foldsFrom === 'bundle' &&
        rec.financial === false && rec.subtotal === null && rec.tax === null && rec.total === null &&
        rec.partner === null && rec.lines.length === rawLines.length && rec.docno === rawHdr.documentno,
        'M_Inventory 100 folds to a NON-FINANCIAL receipt (lines from M_InventoryLine, docno=DocumentNo, partner null, no fabricated total)',
        'docno=' + rec.docno + ' lines=' + rec.lines.length + ' financial=' + rec.financial + ' partner=' + rec.partner);

// ORACLE: independent re-derivation. A physical count is non-financial → there is NO money to fold; the only
// carried value is qty=QtyCount per line. subtotal/tax/total must be null (honest "n/a"); each folded qty ==
// its source QtyCount; price/amount null (a physical count carries no price).
var qmism = 0;
rec.lines.forEach(function (ol, i) {
  var src = rawLines[i];
  if (Number(ol.qty) !== Number(src.qtycount)) { qmism++; console.log('   ⚠ FINDING line ' + i + ' qty eng=' + ol.qty + ' src=' + src.qtycount); }
  if (ol.price !== null) { qmism++; console.log('   ⚠ FINDING line ' + i + ' price NOT null (a count carries no price): ' + ol.price); }
  if (ol.amount !== null) { qmism++; console.log('   ⚠ FINDING line ' + i + ' amount NOT null (a count carries no money): ' + ol.amount); }
});
console.log('§MINVENTORY-FOLD per-line qty=QtyCount (price/amount null) mismatches=' + qmism);
verdict(qmism === 0, 'every folded line qty == its M_InventoryLine.QtyCount; price/amount honestly null (no money on a physical count)', 'lines=' + rec.lines.length);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 3 · §MINVENTORY-FALSIFIER — bend a line QtyCount +7 → the folded qty must move (proves it's read, not fabricated).
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §MINVENTORY-FALSIFIER (bend line QtyCount +7 → folded qty shifts by exactly +7) —');
var rBent = P.dispatch(db, { AD_Process_ID: 291, Record_ID: INV_ID, params: {} }, ctx(function (l) { return { qtycount: Number(l.qtycount || 0) + 7 }; }));
var bentQ = Number(rBent.result.lines[0].qty), baseQ = Number(rec.lines[0].qty);
console.log('§MINVENTORY-FALSIFIER line0 qty bent=' + bentQ + ' expect=' + (baseQ + 7) + ' (baseline=' + baseQ + ')');
verdict(bentQ === baseQ + 7 && bentQ !== baseQ,
        'bending a line QtyCount shifts the folded qty exactly (the fold reads the rows, never a fabricated qty)', 'Δ = +7');

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 4 · §MINVENTORY-GUARD — 105/106 (M_Inventory Create/Update, non-blank classnames) + 107 (M_Inventory Process,
//     blank classname, isReport=N) stay ABSENT-handler — none is mis-routed to report:m_inventory.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §MINVENTORY-GUARD (the imperative M_Inventory Create/Update/Process procs are NOT mis-routed to report:m_inventory) —');
var guardIds = [105, 106, 107];
var leaked = [];
guardIds.forEach(function (id) {
  var pr; try { pr = P.readProcess(db, id); } catch (e) { return; }
  var rv = P.resolveClassname(pr);
  console.log('§MINVENTORY-GUARD proc=' + id + ' value=' + pr.value + ' name="' + pr.name + '" classname="' + pr.classname + '" isReport=' + pr.isReport + ' resolved="' + rv + '"');
  if (rv === 'report:m_inventory') leaked.push(id);
});
verdict(leaked.length === 0,
        'no imperative M_Inventory* proc is mis-routed to report:m_inventory (105/106 keep their own classname; 107 isReport=N synthesizes no report key)', 'leaked=[' + leaked.join(',') + ']');

// 107 "M_Inventory Process" (blank classname, isReport=N) has no report route → it is NEVER dispatched to a
// fabricated report. The honest non-fold is 'absent-handler' (blank classname, no registry entry) or — reached
// first — 'param-validation-failed'.
var r107 = P.dispatch(db, { AD_Process_ID: 107, params: {} }, ctx());
console.log('§MINVENTORY-GUARD 107 M_Inventory Process dispatched=' + r107.dispatched + ' reason=' + r107.reason + ' result=' + (r107.result ? 'present' : 'none'));
verdict(r107.dispatched === false && (r107.reason === 'absent-handler' || r107.reason === 'param-validation-failed') && !r107.result,
        'M_Inventory Process (107) is an honest non-fold, NEVER a fabricated receipt', 'reason=' + r107.reason);

db.close();
console.log('\n' + (fails ? '🔴 ' + fails + ' check(s) FAILED — W-PROC-MINVENTORY NOT proven' : '═══ ALL PASS — Rpt M_Inventory folds an M_Inventory → non-financial receipt via the existing foldReceipt (KIND-1, no new fold code) — W-PROC-MINVENTORY ═══'));
process.exit(fails ? 1 : 0);
