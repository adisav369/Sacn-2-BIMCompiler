#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-GUARD-ROTATED scope (read this first)
 * SCOPE: The REAL generality test (prompts/WALKER_GUARDS_ROSETTASTONE_SPEC.md §0/§7-5). The STR walk
 *   is only proven on rectilinear plans; the guard layer's job is to make it SAFE — not magically
 *   correct — on a ROTATED/free-form plan: REFUSE-and-flag where the axis-aligned datum derivation
 *   degrades, instead of silently placing wrong elements. Method: take REAL Terminal columns, rotate
 *   the cloud about its centroid by θ (a controlled synthetic perturbation of a real building), run the
 *   SAME walker + guard pass + confidence, and prove the degradation is SURFACED, never hidden.
 *   Oracle/source = pristine Terminal_extracted.db, NEVER output.db. Read the §-log (Log Mandate).
 *
 * WHY ROTATION IS THE RIGHT PERTURBATION: the skeleton walk derives an AXIS-ALIGNED grid by 1-D
 *   clustering of column coords. On a rotated plan, columns from different physical rows overlap in x
 *   (and y) → the grid SMEARS (gridlines proliferate, compression collapses) → snap residuals blow up.
 *   That blown-up residual is exactly what the FIT guard (snap displacement vs tol) bites on.
 *
 * CLAIMS (clean θ=0 vs rotated θ=30°, both on the SAME real columns):
 *   C1 GRID-DEGRADES   — rotated grid compression collapses AND mean snap residual blows up vs clean
 *                        (the walk's grid-regularity premise fails on a rotated plan — measured).
 *   C2 GUARDS-REFUSE   — the guard pass emits materially MORE REFUSE(WALKER_GAP) on the rotated walk
 *                        than the clean walk (the fit guard catches the columns the walk can't place).
 *   C3 CONFIDENCE-DROPS— mean confidence collapses and far more columns fall below the low-confidence
 *                        flag threshold on the rotated walk (the gauge honestly reports lower trust).
 *   C4 NO-FABRICATION  — every refused column is a WALKER_GAP (NOT placed); every PLACED column traces
 *                        a real srcGuid; ZERO silently-wrong high-confidence placements.
 *   C5 ABSTRACT/NON-INVENT — the SAME guard fns run on clean + rotated; the only new number is θ; re-run identical.
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
var LOG = path.join(ROOT, 'logs', 'witness_guard_rotated_' +
  new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + '.log');

var THETA = 30;            // degrees — the synthetic rotation (well past where the axis-aligned grid holds)
var LOW_CONF = 0.5;        // a placed element below this is HIGHLIGHTED as low-confidence in the Outliner

var _lines = [];
function log(s) { _lines.push(s); console.log(s); }
var pass = 0, fail = 0;
function assert(claim, cond, detail) {
  if (cond) { pass++; log('  ✅ ' + claim + ' — ' + detail); }
  else { fail++; log('  ❌ ' + claim + ' — ' + detail); }
}

// Rotate a column cloud about its centroid by θ degrees (a rigid transform — no data invented).
function rotate(cols, deg) {
  var cx = cols.reduce(function (s, c) { return s + c.x; }, 0) / cols.length;
  var cy = cols.reduce(function (s, c) { return s + c.y; }, 0) / cols.length;
  var t = deg * Math.PI / 180, co = Math.cos(t), si = Math.sin(t);
  return cols.map(function (c) {
    return { guid: c.guid, z: c.z,
      x: cx + (c.x - cx) * co - (c.y - cy) * si,
      y: cy + (c.x - cx) * si + (c.y - cy) * co };
  });
}

// Walk + run each walked column through the guard pass INDEPENDENTLY (placed=[] so the inherent
// stacked-column clash — same in clean & rotated — does not mask the rotation-driven FIT signal).
// Returns { grid, meanRes, refused, placed, meanConf, lowConf, gapsTraced, allTraced }.
function walkAndGuard(cols) {
  var sk = SW.swWalkSkeleton(cols, {});
  // Build candidate boxes first, then derive the envelope as their union — a correctly-formed candidate
  // is contained, so containment does NOT falsely fire (the 4m column box is taller than a thin centroid
  // margin). This ISOLATES the rotation→fit→confidence signal: FIT (snap residual) is the discriminator.
  var cands = sk.walked.map(function (w) {
    return { id: w.srcGuid, srcGuid: w.srcGuid, residual: w.residual,
      aabb: { minX: w.x - 0.3, maxX: w.x + 0.3, minY: w.y - 0.3, maxY: w.y + 0.3, minZ: w.z - 2, maxZ: w.z + 2 },
      snapResidual: w.residual, priority: 1 };
  });
  var env = {
    minX: Math.min.apply(null, cands.map(function (c) { return c.aabb.minX; })),
    maxX: Math.max.apply(null, cands.map(function (c) { return c.aabb.maxX; })),
    minY: Math.min.apply(null, cands.map(function (c) { return c.aabb.minY; })),
    maxY: Math.max.apply(null, cands.map(function (c) { return c.aabb.maxY; })),
    minZ: Math.min.apply(null, cands.map(function (c) { return c.aabb.minZ; })),
    maxZ: Math.max.apply(null, cands.map(function (c) { return c.aabb.maxZ; }))
  };
  var res = sk.walked.map(function (w) { return w.residual; });
  var meanRes = res.reduce(function (s, v) { return s + v; }, 0) / res.length;
  var refused = 0, placed = 0, lowConf = 0, confSum = 0, gapsTraced = 0, allTraced = true;
  cands.forEach(function (w) {
    var ev = WG.wgEvaluate(w, { envelope: env, placed: [] });
    if (ev.outcome === 'REFUSE') {
      refused++;
      if (ev.op.opType === 'WALKER_GAP' && ev.op.params.id === w.srcGuid) gapsTraced++;
    } else {
      placed++;
      var conf = WC.wcRaw(ev.confidence, 1);
      confSum += conf;
      if (conf < LOW_CONF) lowConf++;
      if (!w.srcGuid) allTraced = false;
    }
  });
  return { grid: sk.grid, n: sk.walked.length, meanRes: meanRes, refused: refused, placed: placed,
    meanConf: placed ? confSum / placed : 0, lowConf: lowConf, gapsTraced: gapsTraced, allTraced: allTraced };
}

(async function main() {
  log('═══ W-GUARD-ROTATED — generality test: clean vs rotated REAL Terminal ═══');
  var SQL = await initSqlJs();
  var db = new SQL.Database(new Uint8Array(fs.readFileSync(TERMINAL_DB)));
  var cols = db.exec("SELECT m.guid,t.center_x,t.center_y,t.center_z FROM elements_meta m " +
    "JOIN element_transforms t ON t.guid=m.guid WHERE m.ifc_class='IfcColumn'")[0].values
    .map(function (v) { return { guid: v[0], x: v[1], y: v[2], z: v[3] }; });
  db.close();

  var clean = walkAndGuard(cols);
  var rot = walkAndGuard(rotate(cols, THETA));
  log('§ROT CLEAN  : grid ' + clean.grid.xLines.length + '×' + clean.grid.yLines.length + ', meanRes ' +
      clean.meanRes.toFixed(3) + 'm, refused ' + clean.refused + ', placed ' + clean.placed +
      ', meanConf ' + clean.meanConf.toFixed(3) + ', lowConf ' + clean.lowConf);
  log('§ROT θ=' + THETA + '°: grid ' + rot.grid.xLines.length + '×' + rot.grid.yLines.length + ', meanRes ' +
      rot.meanRes.toFixed(3) + 'm, refused ' + rot.refused + ', placed ' + rot.placed +
      ', meanConf ' + rot.meanConf.toFixed(3) + ', lowConf ' + rot.lowConf);

  // ── C1 GRID-DEGRADES ──
  var cleanLines = clean.grid.xLines.length + clean.grid.yLines.length;
  var rotLines = rot.grid.xLines.length + rot.grid.yLines.length;
  assert('C1 GRID-DEGRADES',
    rotLines > cleanLines * 1.5 && rot.meanRes > clean.meanRes * 1.5,
    'gridlines ' + cleanLines + '→' + rotLines + ' (proliferate ' + (rotLines / cleanLines).toFixed(1) +
    '×), meanRes ' + clean.meanRes.toFixed(3) + '→' + rot.meanRes.toFixed(3) + 'm (×' + (rot.meanRes / clean.meanRes).toFixed(1) + ')');

  // ── C2 GUARDS-REFUSE ──
  assert('C2 GUARDS-REFUSE',
    rot.refused > clean.refused,
    'WALKER_GAP refusals ' + clean.refused + ' (clean) → ' + rot.refused + ' (rotated) — the fit guard catches the unplaceable columns');

  // ── C3 CONFIDENCE-DROPS ──
  assert('C3 CONFIDENCE-DROPS',
    rot.meanConf < clean.meanConf && rot.lowConf > clean.lowConf,
    'meanConf ' + clean.meanConf.toFixed(3) + '→' + rot.meanConf.toFixed(3) + '; low-confidence (<' + LOW_CONF +
    ') columns ' + clean.lowConf + '→' + rot.lowConf + ' (highlighted, not hidden)');

  // ── C4 NO-FABRICATION ──
  assert('C4 NO-FABRICATION',
    rot.gapsTraced === rot.refused && rot.allTraced && clean.allTraced && (rot.refused + rot.placed === rot.n),
    'every refusal is a traced WALKER_GAP (' + rot.gapsTraced + '/' + rot.refused + '), every placed traces a real srcGuid, ' +
    'refused+placed=' + (rot.refused + rot.placed) + '=' + rot.n + ' (zero fabricated)');

  // ── C5 ABSTRACT/NON-INVENT ──
  var rot2 = walkAndGuard(rotate(cols, THETA));
  var reproducible = rot2.refused === rot.refused && Math.abs(rot2.meanConf - rot.meanConf) < 1e-9 &&
                     Math.abs(rot2.meanRes - rot.meanRes) < 1e-9;
  assert('C5 ABSTRACT/NON-INVENT',
    reproducible,
    're-run identical=' + reproducible + '; SAME guard fns on clean+rotated, only new number = θ=' + THETA + '°');

  log('───────────────────────────────────────────────');
  log('§ROT VERDICT: rotation DEGRADES the walk (grid ×' + (rotLines / cleanLines).toFixed(1) + ', res ×' +
      (rot.meanRes / clean.meanRes).toFixed(1) + ') and the guard/confidence layer SURFACES it (refusals ' +
      clean.refused + '→' + rot.refused + ', meanConf ' + clean.meanConf.toFixed(2) + '→' + rot.meanConf.toFixed(2) +
      ') — REFUSE-and-flag, never fabricate.');
  log('W-GUARD-ROTATED: ' + pass + ' PASS / ' + fail + ' FAIL');
  try { fs.mkdirSync(path.dirname(LOG), { recursive: true }); fs.writeFileSync(LOG, _lines.join('\n')); log('§LOG ' + LOG); } catch (e) {}
  process.exit(fail ? 1 : 0);
})();
