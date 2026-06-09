#!/usr/bin/env node
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// poc_ad_logic_live.js — W-AD-LOGIC-LIVE (AD_BEHAVIOR_HANDOFF §1 "fields react to logic", UI-unpark).
//
// SPEC: crud_overlay.applyAdLogic() drives the live form DOM from each field's AD logic — visible→hide the row,
//   readonly→disable, required→mark — via CORE.effectiveFlags (→ window.AdEvaluator), re-applied on every edit.
//   This witness proves the VERDICT applyAdLogic applies: for REAL ad_field logic strings (ad_full.db, lowercase),
//   effectiveFlags flips {visible/readonly/required} as the referenced @Col@ context toggles. The DOM toggle itself
//   (row.style.display / input.disabled / the * marker) is the browser §AD-LOGIC-LIVE / Playwright wiring step.
//
// NON-INVENT: every logic string is a real ad_field row; the contexts are built by PARSING the @Col@=val the AD
//   declares, never hand-set verdicts. Deterministic. READ build/erp/poc_ad_logic_live.log — exit code ≠ evidence.
// Implementing AD_BEHAVIOR_HANDOFF.md §1 + ERP_COVERAGE_MATRIX §logic (🟡→✅ when live) — Witness: W-AD-LOGIC-LIVE
// Run: node scripts/poc_ad_logic_live.js 2>&1 | tee build/erp/poc_ad_logic_live.log
'use strict';
var path = require('path');
var Database = require('better-sqlite3');
global.window = global.window || {};                                          // crud_overlay.adEval() reads window.AdEvaluator
global.window.AdEvaluator = require(path.join(__dirname, '..', 'build', 'erp', 'ad_evaluator.js'));
var CORE = require(path.join(__dirname, '..', 'build', 'erp', 'crud_overlay.js'));
var db = new Database(path.join(__dirname, '..', 'build', 'erp', 'ad_full.db'), { readonly: true });

var fails = 0;
function verdict(ok, label, detail) { if (!ok) fails++; console.log('   ' + (ok ? '🟢' : '🔴') + ' ' + label + (detail ? ' — ' + detail : '')); }

console.log('═══ W-AD-LOGIC-LIVE — crud_overlay.effectiveFlags flips on real ad_field logic (UI-unpark §1) ═══\n');

// pull real single-clause equality logics (deterministically flippable) for each of the 3 logic dimensions
function realLogics(col, n) {
  return db.prepare('SELECT name, ' + col + ' AS lg FROM ad_field WHERE ' + col + " LIKE '@%@=%' AND " + col + " NOT LIKE '%&%' AND " + col + " NOT LIKE '%|%' AND name IS NOT NULL LIMIT ?").all(n);
}
// parse "@Col@='V'" / "@Col@=V" → {ref, val}; ctxTrue sets ref=val, ctxFalse flips it
function ctxs(logic) {
  var m = /@(\w+)@\s*=\s*'?([^'&|]+?)'?\s*$/.exec(String(logic).trim());
  if (!m) return null;
  var ref = m[1], val = m[2], other = (val === 'Y' ? 'N' : (val === 'N' ? 'Y' : val + '_X'));
  var tT = {}; tT[ref] = val; var tF = {}; tF[ref] = other;
  return { ref: ref, val: val, truthy: tT, falsy: tF };
}

// ── §1a DisplayLogic → visible flips (the show/hide applyAdLogic applies to the row) ──────────────────
var dispRows = realLogics('displaylogic', 6), checkedD = 0, flipD = 0;
dispRows.forEach(function (r) {
  var c = ctxs(r.lg); if (!c) return; checkedD++;
  var f = { col: 'x', displaylogic: r.lg };
  var vis = CORE.effectiveFlags(f, c.truthy, c.truthy).visible;
  var hid = CORE.effectiveFlags(f, c.falsy, c.falsy).visible;
  var flip = vis === true && hid === false;
  if (flip) flipD++;
  console.log('§AD-LOGIC-LIVE dim=display field=' + JSON.stringify(r.name) + ' logic="' + r.lg + '" @' + c.ref + '=' + c.val + '→visible=' + vis + ' / →' + JSON.stringify(c.falsy[c.ref]) + '→visible=' + hid + ' flip=' + flip);
});
verdict(checkedD > 0 && flipD === checkedD, flipD + '/' + checkedD + ' real DisplayLogic fields flip visible↔hidden on context (row show/hide is AD-driven)');

// ── §1b ReadOnlyLogic → readonly flips (disable) ; §1c MandatoryLogic → required flips (mark) ──────────
function dimFlip(col, attr, n) {
  var rows = realLogics(col, n), checked = 0, flip = 0;
  rows.forEach(function (r) {
    var c = ctxs(r.lg); if (!c) return; checked++;
    var f = { col: 'x' }; f[col] = r.lg;
    var on = CORE.effectiveFlags(f, c.truthy, c.truthy)[attr];
    var off = CORE.effectiveFlags(f, c.falsy, c.falsy)[attr];
    if (on === true && off === false) flip++;
    console.log('§AD-LOGIC-LIVE dim=' + attr + ' field=' + JSON.stringify(r.name) + ' logic="' + r.lg + '" →' + attr + '=' + on + '/' + off);
  });
  verdict(checked === 0 || flip === checked, flip + '/' + checked + ' real ' + col + ' fields flip ' + attr + ' on context', checked === 0 ? 'no single-clause sample in seed (n/a)' : '');
  return checked;
}
dimFlip('readonlylogic', 'readonly', 4);
dimFlip('mandatorylogic', 'required', 4);

// ── §FALSIFIER: a field with NO logic must NOT react — flat booleans pass through unchanged ────────────
var flat = { col: 'x', readonly: false, required: false };
var e1 = CORE.effectiveFlags(flat, { DocStatus: 'CO' }, { DocStatus: 'CO' });
var e2 = CORE.effectiveFlags(flat, { DocStatus: 'DR' }, { DocStatus: 'DR' });
verdict(e1.visible && e2.visible && !e1.readonly && !e2.readonly,
  '§FALSIFIER no-logic field is INERT to context (visible=true, flat booleans pass through — not a phantom reaction)');
console.log('§FALSIFIER no-logic field visible=' + e1.visible + '/' + e2.visible + ' readonly=' + e1.readonly + '/' + e2.readonly + ' (must be stable)');

console.log('\n' + (fails === 0 ? '🟢 W-AD-LOGIC-LIVE PASS' : '🔴 W-AD-LOGIC-LIVE FAIL (' + fails + ')') +
  ' — effectiveFlags flips on real AD logic; applyAdLogic applies it to the live form DOM (browser §AD-LOGIC-LIVE = the deploy step).');
db.close();
process.exit(fails === 0 ? 0 : 1);
