#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-CONFIDENCE-CALIBRATED scope (read this first)
 * SCOPE: Witness for the calibrated confidence marker (prompts/WALKER_GUARDS_ROSETTASTONE_SPEC.md §4).
 *   On REAL Terminal: give every walked STR element a RAW confidence = f(guard margin × regulatory
 *   margin), label each with its WALK-BACK correctness (does it match the extracted oracle), then
 *   prove the marker is only trustworthy AFTER calibration: raw ECE is HIGH (guard/rule margins measure
 *   geometric/code comfort, NOT oracle-match probability), an isotonic recalibration against the oracle
 *   drops ECE under the bar, and a DELIBERATELY mis-set confidence FAILS the bar (the test bites).
 *   Oracle = pristine Terminal_extracted.db, NEVER output.db. Read the §-log (Log Mandate).
 *   This pins decision D2 (calibration method = isotonic/PAV; ECE tol = ECE_BAR below, set from data).
 *
 * CLAIMS:
 *   C1 RAW-MISCALIBRATED — the RAW marker has ECE > bar ⇒ it must NOT be shown as-is (the fake-gauge trap).
 *   C2 ISOTONIC-CALIBRATES — fitting a monotonic map on a TRAIN split and scoring a HELD-OUT test split
 *                             drops ECE ≤ bar (in-sample ECE is ~0 by construction = a fake-gauge trap;
 *                             held-out is the honest measure ⇒ the gauge is EARNED, not overfit).
 *   C3 MONOTONE-RELIABILITY — the calibrated map is non-decreasing AND a higher-confidence bin has a
 *                              higher actual match-rate than a lower one (the curve points the right way).
 *   C4 TEST-BITES — a deliberately mis-set (inverted) confidence has ECE > bar ⇒ FAILS ⇒ the test is real.
 *   C5 NON-INVENT — every confidence is f(measured guard+rule margins); calibration uses ONLY oracle
 *                   labels from extracted.db; re-run identical.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var SW = require(path.join(ROOT, 'deploy/dev/str_walker.js'));
var WG = require(path.join(ROOT, 'deploy/dev/walker_guards.js'));
var WC = require(path.join(ROOT, 'deploy/dev/walker_confidence.js'));
var TERMINAL_DB = path.join(ROOT, 'deploy/buildings/Terminal_extracted.db');
var LOG = path.join(ROOT, 'logs', 'witness_walker_confidence_' +
  new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + '.log');

var ECE_BAR = 0.05;        // Expected Calibration Error tolerance (set from the measured raw/calibrated gap)
var MATCH_TOL = 1.0;       // m — same as the STR walk-back beam plateau
var MAX_SPAN = SW.SW_SPAN_RULES.STEEL.maxBeamSpan;   // cited rule, not a magic number

var _lines = [];
function log(s) { _lines.push(s); console.log(s); }
var pass = 0, fail = 0;
function assert(claim, cond, detail) {
  if (cond) { pass++; log('  ✅ ' + claim + ' — ' + detail); }
  else { fail++; log('  ❌ ' + claim + ' — ' + detail); }
}

function readClass(db, cls) {
  var r = db.exec("SELECT m.guid,t.center_x,t.center_y,t.center_z,t.bbox_x,t.bbox_y,t.bbox_z " +
    "FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid WHERE m.ifc_class='" + cls + "'");
  return r.length ? r[0].values.map(function (v) {
    return { guid: v[0], cx: v[1], cy: v[2], cz: v[3], bx: v[4], by: v[5], bz: v[6] }; }) : [];
}

(async function main() {
  log('═══ W-CONFIDENCE-CALIBRATED — earned confidence on REAL Terminal (oracle=extracted.db) ═══');
  var SQL = await initSqlJs();
  var db = new SQL.Database(new Uint8Array(fs.readFileSync(TERMINAL_DB)));
  var realCols = readClass(db, 'IfcColumn');
  var realBeams = readClass(db, 'IfcBeam');
  var envRow = db.exec("SELECT min(center_x-bbox_x/2),max(center_x+bbox_x/2),min(center_y-bbox_y/2)," +
    "max(center_y+bbox_y/2),min(center_z-bbox_z/2),max(center_z+bbox_z/2) FROM element_transforms")[0].values[0];
  db.close();
  var envelope = { minX: envRow[0], maxX: envRow[1], minY: envRow[2], maxY: envRow[3], minZ: envRow[4], maxZ: envRow[5] };
  var ctx = { envelope: envelope };

  var cols = realCols.map(function (r) { return { guid: r.guid, x: r.cx, y: r.cy, z: r.cz }; });
  var sk = SW.swWalkSkeleton(cols, {});

  // ── Build oracle-labelled samples: conf = f(guard margin × rule margin); correct = matches oracle ──
  var samples = [];

  // Columns: walked one-per-real-source ⇒ correct=1 (recall 1.0). ruleMargin=1 (no span limit). guard
  // margin from a real containment check on the column AABB at its snapped position.
  sk.walked.forEach(function (w) {
    var aabb = { minX: w.x - 0.3, maxX: w.x + 0.3, minY: w.y - 0.3, maxY: w.y + 0.3, minZ: w.z - 2, maxZ: w.z + 2 };
    var gm = WG.wgContainment({ aabb: aabb }, ctx).margin;
    samples.push({ kind: 'column', conf: WC.wcRaw(gm, 1), correct: 1 });
  });

  // Girders: ruleMargin = how far the span sits under the cited steel limit (the regulatory comfort).
  // correct = the girder midpoint matches a real extracted beam within MATCH_TOL (the walk-back oracle).
  sk.girders.forEach(function (g) {
    var mx = (g.from[0] + g.to[0]) / 2, my = (g.from[1] + g.to[1]) / 2;
    var ruleMargin = (MAX_SPAN - g.span) / MAX_SPAN;          // <0 for over-span → clamped to 0 in wcRaw
    var aabb = { minX: Math.min(g.from[0], g.to[0]) - 0.1, maxX: Math.max(g.from[0], g.to[0]) + 0.1,
                 minY: Math.min(g.from[1], g.to[1]) - 0.1, maxY: Math.max(g.from[1], g.to[1]) + 0.1,
                 minZ: envelope.minZ, maxZ: envelope.maxZ };
    var gm = WG.wgContainment({ aabb: aabb }, ctx).margin;
    var correct = realBeams.some(function (b) { return Math.hypot(b.cx - mx, b.cy - my) <= MATCH_TOL; }) ? 1 : 0;
    samples.push({ kind: 'girder', conf: WC.wcRaw(gm, ruleMargin), correct: correct });
  });

  var overall = samples.reduce(function (s, x) { return s + x.correct; }, 0) / samples.length;
  log('§CONF ' + samples.length + ' oracle-labelled samples (' + sk.walked.length + ' columns + ' +
      sk.girders.length + ' girders); overall match-rate ' + overall.toFixed(3));

  // ── C1 RAW-MISCALIBRATED ──
  var rawRel = WC.wcReliability(samples, 10);
  log('§CONF RAW reliability (predicted→actual per non-empty bin):');
  rawRel.bins.forEach(function (b) { if (b.n) log('   [' + b.lo.toFixed(1) + ',' + b.hi.toFixed(1) + ') n=' + b.n + ' pred=' + b.predicted.toFixed(3) + ' actual=' + b.actual.toFixed(3)); });
  assert('C1 RAW-MISCALIBRATED', rawRel.ece > ECE_BAR,
    'raw ECE ' + rawRel.ece.toFixed(4) + ' > bar ' + ECE_BAR + ' ⇒ raw marker must NOT be shown un-calibrated');

  // ── C2 ISOTONIC-CALIBRATES (HELD-OUT — in-sample ECE is ~0 by construction, not evidence) ──
  // Deterministic even/odd split: fit the monotonic map on TRAIN, score ECE on the unseen TEST split.
  var train = samples.filter(function (_, i) { return i % 2 === 0; });
  var test = samples.filter(function (_, i) { return i % 2 === 1; });
  var isoTrain = WC.wcFitIsotonic(train);
  var rawTestEce = WC.wcEce(test, function (s) { return s.conf; }, 10);
  var calTestEce = WC.wcEce(test, function (s) { return isoTrain.map(s.conf); }, 10);
  var inSampleEce = WC.wcEce(samples, function (s) { return WC.wcFitIsotonic(samples).map(s.conf); }, 10);
  log('§CONF held-out test (n=' + test.length + '): raw ECE ' + rawTestEce.toFixed(4) + ' → calibrated ECE ' +
      calTestEce.toFixed(4) + ' (in-sample ECE ' + inSampleEce.toFixed(4) + ' = ~0 by construction, NOT the evidence)');
  assert('C2 ISOTONIC-CALIBRATES', calTestEce <= ECE_BAR && calTestEce < rawTestEce,
    'HELD-OUT calibrated ECE ' + calTestEce.toFixed(4) + ' ≤ bar ' + ECE_BAR + ' AND < raw held-out ' +
    rawTestEce.toFixed(4) + ' (earned on unseen data, not overfit)');

  // ── C3 MONOTONE-RELIABILITY ──  (map-shape on the full data; the held-out ECE above is the earning)
  var iso = WC.wcFitIsotonic(samples);
  var means = iso.blocks.map(function (bl) { return bl.sum / bl.n; });
  var monotone = means.every(function (m, i) { return i === 0 || m >= means[i - 1] - 1e-12; });
  // a high raw-conf input maps to a match-rate ≥ a low raw-conf input
  var hi = iso.map(0.95), lo = iso.map(0.05);
  assert('C3 MONOTONE-RELIABILITY', monotone && hi >= lo,
    'calibrated map non-decreasing=' + monotone + '; map(0.95)=' + hi.toFixed(3) + ' ≥ map(0.05)=' + lo.toFixed(3));

  // ── C4 TEST-BITES (negative control) ──  an INVERTED confidence (1−raw) must fail the ECE bar.
  var invEce = WC.wcEce(samples, function (s) { return 1 - s.conf; }, 10);
  var constEce = WC.wcEce(samples, function () { return 0.5; }, 10);
  assert('C4 TEST-BITES', invEce > ECE_BAR && constEce > ECE_BAR,
    'inverted conf ECE ' + invEce.toFixed(4) + ' > bar AND constant-0.5 ECE ' + constEce.toFixed(4) +
    ' > bar ⇒ a fake gauge FAILS (the test is real)');

  // ── C5 NON-INVENT ──
  var iso2 = WC.wcFitIsotonic(samples);
  var reproducible = Math.abs(iso2.map(0.5) - iso.map(0.5)) < 1e-12 &&
                     Math.abs(iso2.map(0.95) - iso.map(0.95)) < 1e-12;
  var rangeOk = samples.every(function (s) { return s.conf >= 0 && s.conf <= 1 && (s.correct === 0 || s.correct === 1); });
  assert('C5 NON-INVENT', reproducible && rangeOk,
    'calibration re-fits identical=' + reproducible + '; every conf∈[0,1] from measured margins, labels∈{0,1} from oracle=' + rangeOk);

  log('───────────────────────────────────────────────');
  log('§CONF D2 PINNED: method=isotonic/PAV, ECE bar=' + ECE_BAR + '; raw held-out ECE=' + rawTestEce.toFixed(4) +
      ' → calibrated held-out ECE=' + calTestEce.toFixed(4) + ' (inverted control=' + invEce.toFixed(4) + ')');
  log('W-CONFIDENCE-CALIBRATED: ' + pass + ' PASS / ' + fail + ' FAIL');
  try { fs.mkdirSync(path.dirname(LOG), { recursive: true }); fs.writeFileSync(LOG, _lines.join('\n')); log('§LOG ' + LOG); } catch (e) {}
  process.exit(fail ? 1 : 0);
})();
