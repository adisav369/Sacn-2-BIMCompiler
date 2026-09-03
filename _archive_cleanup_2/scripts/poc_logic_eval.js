// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_logic_eval.js — W-LOGIC-EVAL witness. Opens build/erp/ad_full.db (lowercase tables), pulls the REAL
// displaylogic/readonlylogic/mandatorylogic strings, and runs build/erp/ad_evaluator.js across them.
// Implementing ERP_COVERAGE_MATRIX.md §DisplayLogic/§ReadOnlyLogic/§MandatoryLogic — Witness: W-LOGIC-EVAL
// Run: node scripts/poc_logic_eval.js 2>&1 | tee build/erp/poc_logic_eval.log   (read the log; exit code != evidence)
'use strict';
var path = require('path');
var Database = require('better-sqlite3');                                   // db-open idiom (poc_kanban.js:22,40)
var DB_PATH = path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
// ad_evaluator has no kernel/crypto dep: load directly via module.exports.
var E = require(path.join(__dirname, '..', 'build', 'erp', 'ad_evaluator.js'));

var fails = 0;
function verdict(ok, label, detail) {
  if (!ok) fails++;
  console.log((ok ? '🟢' : '🔴') + ' ' + label + (detail ? '  — ' + detail : ''));
}

console.log('═══ W-LOGIC-EVAL — AD logic-expression evaluator (real ad_full.db expressions) ═══\n');

// ── 1 · §LOGIC_EVAL — one line per evaluated field, ≥1 per operator class, hand-derived expected. ───────
// Each sample: a REAL AD expression (verified present in ad_full.db, see SQL audit in §2 below) + a ctx +
// the hand-derived expected boolean. attr names which surface kind the string came from.
console.log('— §LOGIC_EVAL samples (operator-class coverage; expected hand-derived) —');
var SAMPLES = [
  // equality (@X@=Y)
  { attr: 'display', expr: "@IsSummary@=N",                          ctx: { IsSummary: 'N' },              exp: true,  cls: 'equality' },
  { attr: 'display', expr: "@IsSummary@=N",                          ctx: { IsSummary: 'Y' },              exp: false, cls: 'equality' },
  // quoted equality
  { attr: 'display', expr: "@IsBOM@='Y'",                            ctx: { IsBOM: 'Y' },                  exp: true,  cls: 'equality-quoted' },
  // not-equal (@X@!Y) and the !0 / !'' idioms
  { attr: 'readonly', expr: "@C_Payment_ID@!0",                      ctx: { C_Payment_ID: 1234 },          exp: true,  cls: 'not-equal' },
  { attr: 'readonly', expr: "@C_Payment_ID@!0",                      ctx: {},                              exp: false, cls: 'not-equal(_ID empty->0)' },
  { attr: 'readonly', expr: "@DateLastRun@!''",                      ctx: { DateLastRun: '' },             exp: false, cls: 'not-equal-empty' },
  // caret not-equal (^)
  { attr: 'display', expr: "@GraphType@^P",                          ctx: { GraphType: 'L' },              exp: true,  cls: 'not-equal-caret' },
  { attr: 'display', expr: "@GraphType@^P",                          ctx: { GraphType: 'P' },              exp: false, cls: 'not-equal-caret' },
  // context-ref (@#X@ global, @$X@ accounting) — incl var-vs-var
  { attr: 'display', expr: "@#IsLiberoEnabled@=Y",                   ctx: { '#IsLiberoEnabled': 'Y' },     exp: true,  cls: 'context-ref-#' },
  { attr: 'display', expr: "@$Element_PJ@=Y",                        ctx: { '$Element_PJ': 'Y' },          exp: true,  cls: 'context-ref-$' },
  { attr: 'display', expr: "@C_Currency_ID@!@$C_Currency_ID@",       ctx: { C_Currency_ID: 100, '$C_Currency_ID': 102 }, exp: true, cls: 'var-vs-var' },
  // AND (&)
  { attr: 'display', expr: "@IsSummary@='N' & @IsBOM@='Y' & @IsVerified@='N'", ctx: { IsSummary: 'N', IsBOM: 'Y', IsVerified: 'N' }, exp: true, cls: 'AND' },
  { attr: 'display', expr: "@IsSummary@='N' & @IsBOM@='Y' & @IsVerified@='N'", ctx: { IsSummary: 'N', IsBOM: 'Y', IsVerified: 'Y' }, exp: false, cls: 'AND' },
  // OR (|)
  { attr: 'readonly', expr: "@C_Invoice_ID@!0 | @C_Payment_ID@!0",   ctx: { C_Invoice_ID: 0, C_Payment_ID: 7 }, exp: true, cls: 'OR' },
  { attr: 'readonly', expr: "@C_Invoice_ID@!0 | @C_Payment_ID@!0",   ctx: {},                              exp: false, cls: 'OR' },
  // parens overriding left-to-right
  { attr: 'display', expr: "@IsSummary@='N' & (@ProductType@=I | @ProductType@=S)", ctx: { IsSummary: 'N', ProductType: 'S' }, exp: true,  cls: 'parens' },
  { attr: 'display', expr: "@IsSummary@='N' & (@ProductType@=I | @ProductType@=S)", ctx: { IsSummary: 'N', ProductType: 'R' }, exp: false, cls: 'parens' },
  // list membership (@X@=A,R,S)
  { attr: 'display', expr: "@LineType@=C&@CalculationType@=A,R,S",   ctx: { LineType: 'C', CalculationType: 'R' }, exp: true,  cls: 'list' },
  { attr: 'display', expr: "@LineType@=C&@CalculationType@=A,R,S",   ctx: { LineType: 'C', CalculationType: 'X' }, exp: false, cls: 'list' },
  // numeric comparison (>)
  { attr: 'display', expr: "@Record_ID@>0",                         ctx: { Record_ID: 5 },                exp: true,  cls: 'greater-than' },
  { attr: 'display', expr: "@Record_ID@>0",                         ctx: { Record_ID: 0 },                exp: false, cls: 'greater-than' },
  // negation $! (grammar completeness; not present in seed data — synthetic on a real var)
  { attr: 'display', expr: "$!(@Processed@=Y)",                     ctx: { Processed: 'N' },              exp: true,  cls: 'negation-$!' },
  { attr: 'display', expr: "$!(@Processed@=Y)",                     ctx: { Processed: 'Y' },              exp: false, cls: 'negation-$!' },
  // constant exprs that appear literally in the AD (Y=N false; 1=1 true)
  { attr: 'display', expr: "Y=N",                                   ctx: {},                              exp: false, cls: 'const-false' },
  { attr: 'readonly', expr: "1=1",                                  ctx: {},                              exp: true,  cls: 'const-true' },
];
var sampFail = 0;
SAMPLES.forEach(function (s) {
  var got;
  try { got = E.evaluate(s.expr, s.ctx, s.ctx); } catch (e) { got = 'THROW:' + e.message; }
  var ok = (got === s.exp);
  if (!ok) sampFail++;
  console.log('§LOGIC_EVAL attr=' + s.attr + ' cls=' + s.cls + ' expr="' + s.expr + '" ctx=' + JSON.stringify(s.ctx) + ' result=' + got + ' expected=' + s.exp + ' ' + (ok ? 'OK' : 'MISMATCH'));
});
verdict(sampFail === 0, 'sample operator-class verdicts (' + SAMPLES.length + ')', sampFail + ' mismatch');

// Confirm every sample expr (minus the 2 synthetic $! ones) is a REAL string in ad_full.db.
var db = new Database(DB_PATH, { readonly: true });
function existsExpr(expr) {
  var q = "SELECT 1 FROM ad_field WHERE displaylogic=@e UNION SELECT 1 FROM ad_field WHERE readonlylogic=@e " +
          "UNION SELECT 1 FROM ad_field WHERE mandatorylogic=@e UNION SELECT 1 FROM ad_column WHERE readonlylogic=@e " +
          "UNION SELECT 1 FROM ad_column WHERE mandatorylogic=@e UNION SELECT 1 FROM ad_tab WHERE displaylogic=@e " +
          "UNION SELECT 1 FROM ad_tab WHERE readonlylogic=@e LIMIT 1";
  return !!db.prepare(q).get({ e: expr });
}
var synthetic = SAMPLES.filter(function (s) { return s.cls === 'negation-$!'; }).map(function (s) { return s.expr; });
var realCheck = SAMPLES.filter(function (s) { return synthetic.indexOf(s.expr) === -1; });
var notReal = [];
realCheck.forEach(function (s) { if (!existsExpr(s.expr)) notReal.push(s.expr); });
verdict(notReal.length === 0, 'every non-synthetic sample is a REAL ad_full.db string',
        notReal.length ? 'NOT FOUND: ' + JSON.stringify(notReal.filter(function(v,i){return notReal.indexOf(v)===i;})) : 'all present; $! samples = grammar-completeness on real vars');

// ── 2 · §LOGIC_COVERAGE — parse EVERY real logic row across the 7 surfaces. Target 100% parse. ─────────
console.log('\n— §LOGIC_COVERAGE (parse every real AD logic string across the 7 surfaces) —');
var SURFACES = [
  { tbl: 'ad_field',  col: 'displaylogic'   },
  { tbl: 'ad_field',  col: 'readonlylogic'  },
  { tbl: 'ad_field',  col: 'mandatorylogic' },
  { tbl: 'ad_column', col: 'readonlylogic'  },
  { tbl: 'ad_column', col: 'mandatorylogic' },
  { tbl: 'ad_tab',    col: 'displaylogic'   },
  { tbl: 'ad_tab',    col: 'readonlylogic'  },
];
var total = 0, parsed = 0, residuals = [];
SURFACES.forEach(function (s) {
  var rows = db.prepare("SELECT DISTINCT " + s.col + " AS e FROM " + s.tbl + " WHERE " + s.col + " IS NOT NULL AND trim(" + s.col + ")<>''").all();
  // total counts ALL rows (not just distinct) for the matrix tally; parse-coverage measured on distinct exprs.
  var rowCount = db.prepare("SELECT count(*) AS c FROM " + s.tbl + " WHERE " + s.col + " IS NOT NULL AND trim(" + s.col + ")<>''").get().c;
  var distOk = 0, distFail = 0;
  rows.forEach(function (r) {
    var res = E.tryParse(r.e);
    if (res.ok) distOk++; else { distFail++; if (residuals.length < 30) residuals.push({ tbl: s.tbl, col: s.col, expr: r.e, err: res.error }); }
  });
  total += rowCount; parsed += rowCount * (distFail === 0 ? 1 : (distOk / rows.length));
  console.log('§LOGIC_COVERAGE surface=' + s.tbl + '.' + s.col + ' rows=' + rowCount + ' distinct=' + rows.length + ' parse_ok=' + distOk + ' parse_fail=' + distFail);
});
// Exact per-row parse pass count (rows, not distinct) so the matrix number is honest.
// @SQL= rows are a SEPARATE surface (Evaluator.parseSQLLogic) — counted apart, not as boolean-grammar failures.
var rowTotal = 0, rowBoolOk = 0, rowSql = 0, rowFail = 0, rowResiduals = [];
SURFACES.forEach(function (s) {
  var rows = db.prepare("SELECT " + s.col + " AS e FROM " + s.tbl + " WHERE " + s.col + " IS NOT NULL AND trim(" + s.col + ")<>''").all();
  rows.forEach(function (r) {
    rowTotal++;
    var res = E.tryParse(r.e);
    if (res.sql) rowSql++;
    else if (res.ok) rowBoolOk++;
    else { rowFail++; if (rowResiduals.length < 30) rowResiduals.push(r.e); }
  });
});
var boolDenom = rowTotal - rowSql;   // boolean-grammar rows (excludes the @SQL= surface)
console.log('§LOGIC_COVERAGE evaluated=' + rowBoolOk + ' of ' + boolDenom + ' boolean-logic rows (' + (100 * rowBoolOk / boolDenom).toFixed(2) + '% parse); +' + rowSql + ' @SQL= rows (separate surface, deferred); ' + rowFail + ' genuine parse-fail; total=' + rowTotal);
if (rowResiduals.length) console.log('§LOGIC_COVERAGE genuine-residuals(' + rowResiduals.length + '): ' + JSON.stringify(rowResiduals));
verdict(rowFail === 0, 'parse coverage 100% of boolean-logic rows (' + boolDenom + ' rows; ' + rowSql + ' @SQL= named-deferred)',
        rowFail === 0 ? '' : rowFail + ' rows fail to parse');

// ── 3 · §FALSIFIER — toggle ONE context var on a REAL expr; a field's verdict FLIPS. ──────────────────
console.log('\n— §FALSIFIER (toggle one ctx var on a real expr → verdict flips; a static engine cannot) —');
var fExpr = db.prepare("SELECT readonlylogic AS e FROM ad_column WHERE readonlylogic='@Processed@=Y' LIMIT 1").get();
var fExprStr = fExpr ? fExpr.e : '@Processed@=Y';
var beforeRO = E.evaluate(fExprStr, { Processed: 'N' }, {});   // not processed -> editable (readonly=false)
var afterRO  = E.evaluate(fExprStr, { Processed: 'Y' }, {});   // processed     -> locked  (readonly=true)
console.log('§FALSIFIER expr="' + fExprStr + '" attr=readonly Processed:N->result=' + beforeRO + '  Processed:Y->result=' + afterRO + '  flipped=' + (beforeRO !== afterRO));
verdict(beforeRO === false && afterRO === true, 'readonly verdict flips rw->ro when Processed N->Y (real ad_column row)');

// second falsifier on display: DocStatus-style state change on a real list/eq expr
var dExpr = "@DocStatus@!DR";          // canonical doc-state idiom (matrix witness line)
var drHide = E.evaluate(dExpr, { DocStatus: 'DR' }, {});       // drafting -> false (hidden)
var coShow = E.evaluate(dExpr, { DocStatus: 'CO' }, {});       // completed -> true (shown)
console.log('§FALSIFIER expr="' + dExpr + '" attr=display DocStatus:DR->result=' + drHide + '  DocStatus:CO->result=' + coShow + '  flipped=' + (drHide !== coShow));
verdict(drHide === false && coShow === true, 'display verdict flips hide->show when DocStatus DR->CO');

// ── 4 · parseDepends spot-check (dependency extraction, Evaluator.java:111-136) ───────────────────────
var dep = E.parseDepends("@IsSummary@='N' & (@ProductType@=I | @ProductType@=S)");
verdict(JSON.stringify(dep) === JSON.stringify(['IsSummary', 'ProductType', 'ProductType']),
        'parseDepends extracts column deps', JSON.stringify(dep));

// ── 5 · WIRING — crud_overlay.js effectiveFlags() drives verdicts via AdEvaluator (real expr, node path). ─
console.log('\n— §LOGIC_EVAL via crud_overlay.js effectiveFlags (wiring proof, real ad_column exprs) —');
global.window = global.window || {};                 // crud_overlay.adEval() looks up window.AdEvaluator first
global.window.AdEvaluator = E;
var CRUD = require(path.join(__dirname, '..', 'build', 'erp', 'crud_overlay.js'));
// a field carrying a REAL readonlylogic + a REAL mandatorylogic from ad_column.
var fld = { col: 'c_payment_id', readonly: false, required: false,
            readonlylogic: '@Processed@=Y', mandatorylogic: '@IsApproved@=Y' };
var effDraft = CRUD.effectiveFlags(fld, { Processed: 'N', IsApproved: 'N' }, {});
var effDone  = CRUD.effectiveFlags(fld, { Processed: 'Y', IsApproved: 'Y' }, {});
console.log('effectiveFlags draft=' + JSON.stringify(effDraft) + '  done=' + JSON.stringify(effDone));
verdict(effDraft.readonly === false && effDraft.required === false && effDone.readonly === true && effDone.required === true,
        'crud_overlay.effectiveFlags derives readonly+required from AD logic via AdEvaluator',
        'flat fallback overridden by logic when present');
// validateField uses the evaluated readonly: a change to a logic-locked field is rejected.
var why = CRUD.validateField({}, fld, '999', '7', { Processed: 'Y', IsApproved: 'Y' }, {});
verdict(why === 'readonly', 'validateField rejects edit to a field locked by readonlylogic=@Processed@=Y', 'why=' + why);
// and a field with NO logic string keeps the flat path EXACTLY (fallback preserved).
var flat = { col: 'x', readonly: true, required: false };
var effFlat = CRUD.effectiveFlags(flat, {}, {});
verdict(effFlat.readonly === true && effFlat.visible === true, 'no-logic field keeps flat f.readonly fallback', JSON.stringify(effFlat));

db.close();

console.log('\n' + (fails ? '🔴 ' + fails + ' FAIL' : '═══ ALL PASS ═══'));
process.exit(fails ? 1 : 0);
