// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ⚠ DO NOT REMOVE — Scope guard
// Scope: headless §-witness for the Report verb R1 (prompts/CRUD_P_R_REPORT.md, docs/CRUD_P_R_REPORT_SPEC.md).
//   Proves, against the REAL report_overlay.js CORE + the REAL glassbowl_data.db rows, that a Receipt is a
//   FOLD — the rendered subtotal/tax/total EQUAL an INDEPENDENT re-fold of the raw rows (no hand-authored
//   number), tax reconciles to the header grandtotal TO THE CENT, and a non-financial document folds
//   qty-only with no fabricated money. §-log first; READ the log before any conclusion.
// Run:  node scripts/test_report_overlay.js 2>&1 | tee build/erp/test_report_overlay.log
'use strict';
var path = require('path'), cp = require('child_process');
var ERP = path.join(__dirname, '..', 'build', 'erp');
var CORE = require(path.join(ERP, 'report_overlay.js'));
var DB = path.join(ERP, 'glassbowl_data.db');

var fails = 0;
function ok(cond, msg) { if (!cond) { fails++; console.log('  ✗ FAIL: ' + msg); } else { console.log('  ✓ ' + msg); } }
function q(sql) { var out = cp.execSync('sqlite3 -json "' + DB + '" "' + sql.replace(/"/g, '\\"') + '"', { encoding: 'utf8' }).trim(); return out ? JSON.parse(out) : []; }
function q1(sql) { var r = q(sql); return r.length ? r[0] : null; }
function fmtN(n) { return n == null ? 'n/a' : Number(n).toFixed(2); }
// CORE money fields are now exact 2dp STRINGS (§I-L BigDecimal fold) — compare to the cent, not by ===/round2.
function cents(x) { return x == null ? 0 : Math.round(Number(x) * 100); }

console.log('=== §REPORT witness — ' + new Date().toISOString() + ' ===\n');

// foldDoc — pull the REAL header + lines + names from the bundle and fold via CORE (the exact browser path).
function foldDoc(key, id) {
  var map = CORE.REPORT_MAP[key];
  var header = q1('SELECT * FROM ' + map.key + ' WHERE ' + map.pk + '=' + id);
  var lines = q('SELECT * FROM ' + map.lineTable + ' WHERE ' + map.fk + '=' + id + ' ORDER BY line');
  var bp = header.c_bpartner_id != null ? q1('SELECT name FROM c_bpartner WHERE c_bpartner_id=' + header.c_bpartner_id) : null;
  var names = { partner: bp ? bp.name : null, products: {} };
  lines.forEach(function (r) { var pid = r[map.fkProduct]; if (pid != null && names.products[pid] === undefined) { var p = q1('SELECT name FROM m_product WHERE m_product_id=' + pid); names.products[pid] = p ? p.name : null; } });
  return { map: map, header: header, lines: lines, names: names, rec: CORE.foldReceipt(map, header, lines, names) };
}

// ── ISSUE R1.1: a Receipt is a FOLD — rendered totals == an INDEPENDENT re-fold of the rows ──
// The witness recomputes subtotal straight from sqlite (SUM(linenetamt)) and compares to CORE's fold.
// If they match, the layout invented no number (handAuthored=0). Proven on a single-line and a multi-line doc.
console.log('[ISSUE R1.1] fold == independent re-fold (no hand-authored amount)');
[['c_order', 101], ['c_order', 104]].forEach(function (t) {
  var key = t[0], id = t[1], f = foldDoc(key, id), rec = f.rec, map = f.map;
  var indep = q1('SELECT ROUND(SUM(' + map.amount + '),2) sub, COUNT(*) n FROM ' + map.lineTable + ' WHERE ' + map.fk + '=' + id);
  var indepSub = CORE.round2(indep.sub);
  console.log('§REPORT-RECEIPT doc=' + key + '#' + id + ' lines=' + rec.lines.length + ' subtotal=' + fmtN(rec.subtotal) + ' tax=' + fmtN(rec.tax) + ' total=' + fmtN(rec.total) + ' folds-from=bundle handAuthored=0');
  ok(rec.lines.length === indep.n, key + '#' + id + ' line count folded (' + rec.lines.length + ') == rows in bundle (' + indep.n + ')');
  ok(cents(rec.subtotal) === cents(indepSub), key + '#' + id + ' folded subtotal ' + fmtN(rec.subtotal) + ' == independent SUM(linenetamt) ' + fmtN(indepSub));
});
console.log('');

// ── ISSUE R1.2: tax reconciles — total − subtotal, TO THE CENT, against the header grandtotal ──
console.log('[ISSUE R1.2] tax = total − subtotal reconciles to header.grandtotal (cent-exact)');
[['c_order', 101], ['c_order', 104]].forEach(function (t) {
  var key = t[0], id = t[1], rec = foldDoc(key, id).rec;
  var grand = q1('SELECT grandtotal FROM ' + key + ' WHERE ' + key + '_id=' + id).grandtotal;
  var diffC = cents(rec.total) - (cents(rec.subtotal) + cents(rec.tax));
  console.log('§REPORT-RECEIPT-RECON doc=' + key + '#' + id + ' subtotal=' + fmtN(rec.subtotal) + ' tax=' + fmtN(rec.tax) + ' total=' + fmtN(rec.total) + ' header.grandtotal=' + fmtN(grand) + ' maxDiff=' + diffC + 'c');
  ok(cents(rec.total) === cents(grand), key + '#' + id + ' folded total == header.grandtotal');
  ok(diffC === 0, key + '#' + id + ' subtotal + tax == total to the cent (diff=' + diffC + 'c)');
});
console.log('');

// ── ISSUE R1.3: a non-financial document folds qty-only — NO fabricated money ──
console.log('[ISSUE R1.3] non-financial document (m_inout) — qty only, subtotal/tax stay n/a (never invented)');
var inoutId = (q1("SELECT m_inout_id FROM m_inout ORDER BY m_inout_id LIMIT 1") || {}).m_inout_id;
if (inoutId != null) {
  var io = foldDoc('m_inout', inoutId).rec;
  console.log('§REPORT-RECEIPT doc=m_inout#' + inoutId + ' lines=' + io.lines.length + ' financial=' + io.financial + ' subtotal=' + fmtN(io.subtotal) + ' tax=' + fmtN(io.tax) + ' folds-from=bundle handAuthored=0');
  ok(io.financial === false, 'm_inout flagged non-financial (no amount column in the map)');
  ok(io.subtotal === null && io.tax === null, 'm_inout subtotal/tax stay null — no money fabricated');
} else { console.log('  (no m_inout row in bundle — skipped)'); }
console.log('');

// ── ISSUE R1.4: names are TRUTH-BOUND — resolved from the bundle, not echoed ids ──
console.log('[ISSUE R1.4] partner + product names resolved from the bundle (truth-bound, not "#id")');
var f101 = foldDoc('c_order', 101).rec;
console.log('§REPORT-NAMES doc=c_order#101 partner="' + f101.partner + '" line0="' + (f101.lines[0] && f101.lines[0].name) + '"');
ok(f101.partner && String(f101.partner).charAt(0) !== '#', 'partner resolved to a name (' + f101.partner + '), not "#id"');
ok(f101.lines[0] && f101.lines[0].name && String(f101.lines[0].name).charAt(0) !== '#', 'line product resolved to a name (' + (f101.lines[0] && f101.lines[0].name) + ')');
console.log('');

console.log('=== RESULT: ' + (fails === 0 ? 'ALL PASS' : fails + ' FAIL') + ' ===');
process.exit(fails === 0 ? 0 : 1);
