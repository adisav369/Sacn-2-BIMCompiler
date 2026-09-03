// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-PROC-MINOUTCONFIRM is ✅ DONE in AD_PROCESS_FOLD_LANE.md §P2-tail-leg6.
// SCOPE: W-PROC-MINOUTCONFIRM — AD_Process 292 ("Rpt M_InOutConfirm", "Shipment Confirmation", blank classname,
//   isReport=Y) dispatched through build/erp/ad_process.js resolves to the report:m_inoutconfirm key and folds an
//   M_InOutConfirm → a receipt via the EXISTING report_overlay.foldReceipt (KIND 1, NO new fold verb). This is the
//   LINE-SORT + PRODUCT-JOIN variant: the confirm-line table m_inoutlineconfirm has (a) NO `line` column → the new
//   REPORT_MAP.m_inoutconfirm carries lineSort='m_inoutlineconfirm_id' for the ORDER BY, and (b) NO m_product_id →
//   lineProductVia resolves each line's product through the parent shipment line (m_inoutline_id →
//   m_inoutline.m_product_id) before folding. A confirmation is NON-FINANCIAL → subtotal/tax/total null; the carried
//   value is qty=ConfirmedQty (honest, never a fabricated total).
//   Proves: (a) the blank classname resolves to report:m_inoutconfirm (the JASPER_STARTER report-key path), registered;
//   (b) a real M_InOutConfirm (100, DocumentNo 10000000, 1 confirm-line, ConfirmedQty 10) folds to lines + financial=
//   false (subtotal/tax/total all null) + docno=DocumentNo + date=null (no business date col) + partner=null (no BP col);
//   (c) §CONFIRM-PRODUCT-JOIN — the folded line's product resolves THROUGH THE JOIN to m_product 128 "Azalea Bush"
//   (the confirm-line itself has no m_product_id; the product is on the underlying m_inoutline) — the value that
//   distinguishes this leg from a plain receipt fold;
//   (d) every folded line qty == its m_inoutlineconfirm.ConfirmedQty (independent re-derivation);
//   (e) a §FALSIFIER (bend a line ConfirmedQty +7) shifts the folded qty exactly (proves the fold READS the rows);
//   (f) the GUARD — 280 M_InOutConfirm_Process ("Process Confirmation", isReport=N) + 284 RV_InOutLineConfirm Open +
//   285 RV_InOutConfirm Open (isReport=Y report-VIEWs) do NOT resolve to report:m_inoutconfirm → stay absent-handler.
// NON-INVENT: M_InOutConfirm 100, its confirm-line, the parent m_inoutline + m_product are REAL ad_full.db rows.
// §-log first — READ build/erp/poc_proc_minoutconfirm.log before concluding (exit code ≠ evidence).
// Run:  node scripts/poc_proc_minoutconfirm.js 2>&1 | tee build/erp/poc_proc_minoutconfirm.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var P  = require(path.join(ERP, 'ad_process.js'));
var RC = require(path.join(ERP, 'report_overlay.js'));
var DB_PATH = path.join(ERP, 'ad_full.db');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log((ok ? '🟢' : '🔴') + ' ' + label + (detail ? '  — ' + detail : '')); }

console.log('═══ W-PROC-MINOUTCONFIRM — Rpt M_InOutConfirm (AD_Process 292) folds an M_InOutConfirm → receipt via foldReceipt (line-sort + product-join variant) ═══\n');

var db = new Database(DB_PATH, { readonly: true });
P.installDefaultHandlers(RC);
var CONFIRM_ID = 100;                                        // a real confirmation (DocumentNo 10000000, 1 line, ConfirmedQty 10, product via join = 128)

// ctx — the report-handler accessors (sqlite fetchers off ad_full.db), mirroring the host _procCtx. fetchLines is
// GENERIC over the map: ORDER BY (map.lineSort || 'line'); when map.lineProductVia is set, resolve each line's
// product through the parent-line join and set it on the row BEFORE folding (foldReceipt reads r[fkProduct] unchanged).
function firstId(table, pk) { var r = db.prepare('SELECT ' + pk + ' AS id FROM ' + table + ' ORDER BY ' + pk + ' LIMIT 1').get(); return r && r.id; }
function scalar(table, pk, id, col) { if (id == null) return null; var r = db.prepare('SELECT ' + col + ' AS v FROM ' + table + ' WHERE ' + pk + '=?').get(id); return r ? r.v : null; }
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
      var rows = db.prepare('SELECT * FROM ' + map.lineTable + ' WHERE ' + map.fk + '=? ORDER BY ' + (map.lineSort || 'line')).all(id);
      if (map.lineProductVia) { var via = map.lineProductVia;
        rows = rows.map(function (r) { var o = Object.assign({}, r); var lid = o[via.fk]; if (lid != null) o[map.fkProduct] = scalar(via.table, via.pk, lid, via.product); return o; });
      }
      return lineMut ? rows.map(function (r) { return Object.assign({}, r, lineMut(r)); }) : rows;
    },
    names: function (key, hdr, lines) {
      var nm = { products: {} };
      if (hdr && hdr.c_bpartner_id != null) { var bp = db.prepare('SELECT name FROM c_bpartner WHERE c_bpartner_id=?').get(hdr.c_bpartner_id); nm.partner = bp && bp.name; }
      (lines || []).forEach(function (r) { var pid = r.m_product_id; if (pid != null && nm.products[pid] === undefined) { var p = db.prepare('SELECT name FROM m_product WHERE m_product_id=?').get(pid); nm.products[pid] = p && p.name; } });
      return nm;
    }
  };
}

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 1 · §CONFIRM-DISPATCH — 292 (blank classname, isReport=Y) resolves to report:m_inoutconfirm, registered.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('— §CONFIRM-DISPATCH (real AD_Process 292 row → blank classname → report:m_inoutconfirm) —');
var proc292 = P.readProcess(db, 292);
var resolved = P.resolveClassname(proc292);
console.log('§CONFIRM-DISPATCH proc=292 value=' + proc292.value + ' name="' + proc292.name + '" classname="' + proc292.classname + '" isReport=' + proc292.isReport + ' resolved=' + resolved + ' hasHandler=' + P.hasHandler(resolved));
verdict(proc292.classname === '' && proc292.isReport === true && resolved === 'report:m_inoutconfirm' && P.hasHandler('report:m_inoutconfirm'),
        'AD_Process 292 = Rpt M_InOutConfirm (blank classname) resolves to the registered report:m_inoutconfirm handler', 'value=' + proc292.value);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 2 · §CONFIRM-FOLD — fold M_InOutConfirm 100 → a receipt; NON-FINANCIAL (subtotal/tax/total null), qty carried.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §CONFIRM-FOLD (M_InOutConfirm 100 → receipt: lines + non-financial via foldReceipt) —');
var rawHdr = db.prepare('SELECT * FROM m_inoutconfirm WHERE m_inoutconfirm_id=?').get(CONFIRM_ID);
var rawLines = db.prepare('SELECT * FROM m_inoutlineconfirm WHERE m_inoutconfirm_id=? ORDER BY m_inoutlineconfirm_id').all(CONFIRM_ID);
var r = P.dispatch(db, { AD_Process_ID: 292, Record_ID: CONFIRM_ID, params: {} }, ctx());
var rec = r.result || {};
console.log('§CONFIRM-FOLD dispatched=' + r.dispatched + ' classname=' + r.classname + ' kind=' + r.kind + ' rows=' + r.rows +
            ' docno=' + rec.docno + ' date=' + rec.date + ' partner=' + rec.partner + ' subtotal=' + rec.subtotal + ' tax=' + rec.tax + ' total=' + rec.total + ' financial=' + rec.financial);
verdict(r.dispatched === true && r.classname === 'report:m_inoutconfirm' && rec.foldsFrom === 'bundle' &&
        rec.financial === false && rec.subtotal === null && rec.tax === null && rec.total === null &&
        rec.partner === null && rec.date === null && rec.lines.length === rawLines.length && rec.docno === rawHdr.documentno,
        'M_InOutConfirm 100 folds to a NON-FINANCIAL receipt (lines from m_inoutlineconfirm, docno=DocumentNo, partner/date null, no fabricated total)',
        'docno=' + rec.docno + ' lines=' + rec.lines.length + ' financial=' + rec.financial + ' partner=' + rec.partner + ' date=' + rec.date);

// ORACLE: independent re-derivation. A confirmation is non-financial → there is NO money to fold; the only carried
// value is qty=ConfirmedQty per line. subtotal/tax/total must be null (honest "n/a"); each folded qty == its source
// ConfirmedQty; price/amount null (a confirmation carries no price).
var qmism = 0;
rec.lines.forEach(function (ol, i) {
  var src = rawLines[i];
  if (Number(ol.qty) !== Number(src.confirmedqty)) { qmism++; console.log('   ⚠ FINDING line ' + i + ' qty eng=' + ol.qty + ' src=' + src.confirmedqty); }
  if (ol.price !== null) { qmism++; console.log('   ⚠ FINDING line ' + i + ' price NOT null (a confirmation carries no price): ' + ol.price); }
  if (ol.amount !== null) { qmism++; console.log('   ⚠ FINDING line ' + i + ' amount NOT null (a confirmation carries no money): ' + ol.amount); }
});
console.log('§CONFIRM-FOLD per-line qty=ConfirmedQty (price/amount null) mismatches=' + qmism);
verdict(qmism === 0, 'every folded line qty == its m_inoutlineconfirm.ConfirmedQty; price/amount honestly null (no money on a confirmation)', 'lines=' + rec.lines.length);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 3 · §CONFIRM-PRODUCT-JOIN — the distinguishing assertion: the folded product comes THROUGH the parent-line join.
//     The confirm-line m_inoutlineconfirm has no m_product_id; the product is on m_inoutline (m_inoutline_id → 126 →
//     m_product 128 "Azalea Bush"). Without the join the fold would show '(no product)'.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §CONFIRM-PRODUCT-JOIN (line product resolved through m_inoutline_id → m_inoutline.m_product_id) —');
var line0 = rec.lines[0];
var srcL0 = rawLines[0];
var hasOwnProduct = srcL0 && srcL0.m_product_id !== undefined;   // the confirm-line itself must NOT carry the product
var joinPid = scalar('m_inoutline', 'm_inoutline_id', srcL0 && srcL0.m_inoutline_id, 'm_product_id');
var joinName = scalar('m_product', 'm_product_id', joinPid, 'name');
console.log('§CONFIRM-PRODUCT-JOIN confirmLine.m_inoutline_id=' + (srcL0 && srcL0.m_inoutline_id) + ' confirmLine.hasOwnProduct=' + hasOwnProduct +
            ' joinPid=' + joinPid + ' joinName="' + joinName + '" foldedPid=' + line0.m_product_id + ' foldedName="' + line0.name + '"');
verdict(!hasOwnProduct && joinPid != null && Number(line0.m_product_id) === Number(joinPid) && line0.name === joinName && line0.name === 'Azalea Bush',
        'folded line product resolves THROUGH the parent-shipment-line join (128 "Azalea Bush"), not from the absent confirm-line column',
        'foldedName="' + line0.name + '"');

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 4 · §CONFIRM-FALSIFIER — bend a line ConfirmedQty +7 → the folded qty must move (proves it's read, not fabricated).
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §CONFIRM-FALSIFIER (bend line ConfirmedQty +7 → folded qty shifts by exactly +7) —');
var rBent = P.dispatch(db, { AD_Process_ID: 292, Record_ID: CONFIRM_ID, params: {} }, ctx(function (l) { return { confirmedqty: Number(l.confirmedqty || 0) + 7 }; }));
var bentQ = Number(rBent.result.lines[0].qty), baseQ = Number(rec.lines[0].qty);
console.log('§CONFIRM-FALSIFIER line0 qty bent=' + bentQ + ' expect=' + (baseQ + 7) + ' (baseline=' + baseQ + ')');
verdict(bentQ === baseQ + 7 && bentQ !== baseQ,
        'bending a line ConfirmedQty shifts the folded qty exactly (the fold reads the rows, never a fabricated qty)', 'Δ = +7');

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 5 · §CONFIRM-GUARD — 280 M_InOutConfirm_Process (isReport=N) + 284/285 RV_InOut*Confirm report-VIEWs (isReport=Y)
//     do NOT resolve to report:m_inoutconfirm → stay the honest absent-handler.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §CONFIRM-GUARD (the imperative confirm process + the RV_* confirm report-views are NOT mis-routed) —');
var guardIds = [280, 284, 285];
var leaked = [];
guardIds.forEach(function (id) {
  var pr; try { pr = P.readProcess(db, id); } catch (e) { return; }
  var rv = P.resolveClassname(pr);
  console.log('§CONFIRM-GUARD proc=' + id + ' value=' + pr.value + ' name="' + pr.name + '" isReport=' + pr.isReport + ' resolved="' + rv + '"');
  if (rv === 'report:m_inoutconfirm') leaked.push(id);
});
verdict(leaked.length === 0,
        'no sibling confirm proc (280 imperative, 284/285 report-views) is mis-routed to report:m_inoutconfirm', 'leaked=[' + leaked.join(',') + ']');

// 280 has no report route → it is NEVER dispatched to a fabricated report (honest absent-handler / param-validation).
var r280 = P.dispatch(db, { AD_Process_ID: 280, params: {} }, ctx());
console.log('§CONFIRM-GUARD 280 M_InOutConfirm_Process dispatched=' + r280.dispatched + ' reason=' + r280.reason + ' result=' + (r280.result ? 'present' : 'none'));
verdict(r280.dispatched === false && (r280.reason === 'absent-handler' || r280.reason === 'param-validation-failed') && !r280.result,
        'M_InOutConfirm_Process (280) is an honest non-fold, NEVER a fabricated receipt', 'reason=' + r280.reason);

db.close();
console.log('\n' + (fails ? '🔴 ' + fails + ' check(s) FAILED — W-PROC-MINOUTCONFIRM NOT proven' : '═══ ALL PASS — Rpt M_InOutConfirm folds an M_InOutConfirm → non-financial receipt via the existing foldReceipt (line-sort + product-join, no new fold verb) — W-PROC-MINOUTCONFIRM ═══'));
process.exit(fails ? 1 : 0);
