// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard. Honour until W-PROC-MOVEMENT is ✅ DONE in AD_PROCESS_FOLD_LANE.md §P2-tail-leg2.
// SCOPE: W-PROC-MOVEMENT — AD_Process 290 ("Rpt M_Movement", "Inventory Move Print", blank classname, isReport=Y)
//   dispatched through build/erp/ad_process.js resolves to the report:m_movement key and folds an M_Movement → a
//   receipt via the EXISTING report_overlay.foldReceipt over the NEW REPORT_MAP.m_movement row (KIND 1, NO new fold
//   code — the warehouse sibling of Rpt M_InOut §P2-tail-leg1). A material movement is a NON-FINANCIAL document →
//   subtotal/tax/total stay null; only qty=MovementQty is carried (honest, never a fabricated total).
//   Proves: (a) the blank classname resolves to report:m_movement (the JASPER_STARTER report-key path), registered;
//   (b) a real M_Movement (100, DocumentNo 10000000, 1 line, product 123, MovementQty 4) folds to lines + financial=
//   false (subtotal/tax/total all null) + docno=DocumentNo + date=MovementDate + partner=null (internal move, no BP);
//   (c) every folded line qty == its M_MovementLine.MovementQty, price/amount null (independent re-derivation);
//   (d) a §FALSIFIER (bend a line MovementQty +7) shifts the folded qty exactly (proves the fold READS the rows);
//   (e) the GUARD — 122 "M_Movement_Process" + 286 "M_MovementConfirm_Process" (isReport=N, imperative) stay
//   absent-handler (resolveClassname only fires for report procs → these never reach report:m_movement).
// NON-INVENT: M_Movement 100 + its line/product are REAL ad_full.db rows. A material movement carries qty, not money.
// §-log first — READ build/erp/poc_proc_movement.log before concluding (exit code ≠ evidence).
// Run:  node scripts/poc_proc_movement.js 2>&1 | tee build/erp/poc_proc_movement.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var P  = require(path.join(ERP, 'ad_process.js'));
var RC = require(path.join(ERP, 'report_overlay.js'));
var DB_PATH = path.join(ERP, 'ad_full.db');

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log((ok ? '🟢' : '🔴') + ' ' + label + (detail ? '  — ' + detail : '')); }

console.log('═══ W-PROC-MOVEMENT — Rpt M_Movement (AD_Process 290) folds an M_Movement → receipt via foldReceipt (non-financial) ═══\n');

var db = new Database(DB_PATH, { readonly: true });
P.installDefaultHandlers(RC);
var MOVE_ID = 100;                                            // a real movement (DocumentNo 10000000, 1 line, product 123, qty 4)

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
// 1 · §MOVEMENT-DISPATCH — 290 (blank classname) resolves to report:m_movement, registered.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('— §MOVEMENT-DISPATCH (real AD_Process 290 row → blank classname → report:m_movement) —');
var proc290 = P.readProcess(db, 290);
var resolved = P.resolveClassname(proc290);
console.log('§MOVEMENT-DISPATCH proc=290 value=' + proc290.value + ' name="' + proc290.name + '" classname="' + proc290.classname + '" isReport=' + proc290.isReport + ' resolved=' + resolved + ' hasHandler=' + P.hasHandler(resolved));
verdict(proc290.classname === '' && proc290.isReport === true && resolved === 'report:m_movement' && P.hasHandler('report:m_movement'),
        'AD_Process 290 = Rpt M_Movement (blank classname) resolves to the registered report:m_movement handler', 'value=' + proc290.value);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 2 · §MOVEMENT-FOLD — fold M_Movement 100 → a receipt; NON-FINANCIAL (subtotal/tax/total null), qty carried.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §MOVEMENT-FOLD (M_Movement 100 → receipt: lines + non-financial via foldReceipt) —');
var rawHdr = db.prepare('SELECT * FROM m_movement WHERE m_movement_id=?').get(MOVE_ID);
var rawLines = db.prepare('SELECT * FROM m_movementline WHERE m_movement_id=? ORDER BY line').all(MOVE_ID);
var r = P.dispatch(db, { AD_Process_ID: 290, Record_ID: MOVE_ID, params: {} }, ctx());
var rec = r.result || {};
console.log('§MOVEMENT-FOLD dispatched=' + r.dispatched + ' classname=' + r.classname + ' kind=' + r.kind + ' rows=' + r.rows +
            ' docno=' + rec.docno + ' date=' + rec.date + ' partner=' + rec.partner + ' subtotal=' + rec.subtotal + ' tax=' + rec.tax + ' total=' + rec.total + ' financial=' + rec.financial);
verdict(r.dispatched === true && r.classname === 'report:m_movement' && rec.foldsFrom === 'bundle' &&
        rec.financial === false && rec.subtotal === null && rec.tax === null && rec.total === null &&
        rec.partner === null && rec.lines.length === rawLines.length && rec.docno === rawHdr.documentno,
        'M_Movement 100 folds to a NON-FINANCIAL receipt (lines from M_MovementLine, docno=DocumentNo, partner null, no fabricated total)',
        'docno=' + rec.docno + ' lines=' + rec.lines.length + ' financial=' + rec.financial + ' partner=' + rec.partner);

// ORACLE: independent re-derivation. A material movement is non-financial → there is NO money to fold; the only
// carried value is qty=MovementQty per line. subtotal/tax/total must be null (honest "n/a"); each folded qty ==
// its source MovementQty; price/amount null (a locator→locator move carries no price).
var qmism = 0;
rec.lines.forEach(function (ol, i) {
  var src = rawLines[i];
  if (Number(ol.qty) !== Number(src.movementqty)) { qmism++; console.log('   ⚠ FINDING line ' + i + ' qty eng=' + ol.qty + ' src=' + src.movementqty); }
  if (ol.price !== null) { qmism++; console.log('   ⚠ FINDING line ' + i + ' price NOT null (a movement carries no price): ' + ol.price); }
  if (ol.amount !== null) { qmism++; console.log('   ⚠ FINDING line ' + i + ' amount NOT null (a movement carries no money): ' + ol.amount); }
});
console.log('§MOVEMENT-FOLD per-line qty=MovementQty (price/amount null) mismatches=' + qmism);
verdict(qmism === 0, 'every folded line qty == its M_MovementLine.MovementQty; price/amount honestly null (no money on a material movement)', 'lines=' + rec.lines.length);

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 3 · §MOVEMENT-FALSIFIER — bend a line MovementQty +7 → the folded qty must move (proves it's read, not fabricated).
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §MOVEMENT-FALSIFIER (bend line MovementQty +7 → folded qty shifts by exactly +7) —');
var rBent = P.dispatch(db, { AD_Process_ID: 290, Record_ID: MOVE_ID, params: {} }, ctx(function (l) { return { movementqty: Number(l.movementqty || 0) + 7 }; }));
var bentQ = Number(rBent.result.lines[0].qty), baseQ = Number(rec.lines[0].qty);
console.log('§MOVEMENT-FALSIFIER line0 qty bent=' + bentQ + ' expect=' + (baseQ + 7) + ' (baseline=' + baseQ + ')');
verdict(bentQ === baseQ + 7 && bentQ !== baseQ,
        'bending a line MovementQty shifts the folded qty exactly (the fold reads the rows, never a fabricated qty)', 'Δ = +7');

// ════════════════════════════════════════════════════════════════════════════════════════════════════════
// 4 · §MOVEMENT-GUARD — 122 M_Movement_Process + 286 M_MovementConfirm_Process stay ABSENT-handler.
//     They are imperative (isReport=N) → resolveClassname never synthesizes a report key for them.
// ════════════════════════════════════════════════════════════════════════════════════════════════════════
console.log('\n— §MOVEMENT-GUARD (imperative M_Movement_Process/M_MovementConfirm_Process are NOT mis-routed to report:m_movement) —');
var guardIds = [122, 286];
var leaked = [];
guardIds.forEach(function (id) {
  var pr; try { pr = P.readProcess(db, id); } catch (e) { return; }
  var rv = P.resolveClassname(pr);
  console.log('§MOVEMENT-GUARD proc=' + id + ' value=' + pr.value + ' name="' + pr.name + '" isReport=' + pr.isReport + ' resolved="' + rv + '"');
  if (rv === 'report:m_movement') leaked.push(id);
});
verdict(leaked.length === 0,
        'no imperative M_Movement* proc is mis-routed to report:m_movement (resolveClassname only fires for isReport procs)', 'leaked=[' + leaked.join(',') + ']');

// 286 has no report route → it is NEVER dispatched to a fabricated report. The honest non-fold is either
// 'absent-handler' (blank classname, no registry entry) or — reached first — 'param-validation-failed'.
var r286 = P.dispatch(db, { AD_Process_ID: 286, params: {} }, ctx());
console.log('§MOVEMENT-GUARD 286 M_MovementConfirm_Process dispatched=' + r286.dispatched + ' reason=' + r286.reason + ' result=' + (r286.result ? 'present' : 'none'));
verdict(r286.dispatched === false && (r286.reason === 'absent-handler' || r286.reason === 'param-validation-failed') && !r286.result,
        'M_MovementConfirm_Process (286) is an honest non-fold, NEVER a fabricated receipt', 'reason=' + r286.reason);

db.close();
console.log('\n' + (fails ? '🔴 ' + fails + ' check(s) FAILED — W-PROC-MOVEMENT NOT proven' : '═══ ALL PASS — Rpt M_Movement folds an M_Movement → non-financial receipt via the existing foldReceipt (KIND-1, no new fold code) — W-PROC-MOVEMENT ═══'));
process.exit(fails ? 1 : 0);
