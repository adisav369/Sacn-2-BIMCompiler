#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-STR-TESSELLATE scope (read this block first)
 * SCOPE: Slice-5 witness for the STRUCTURAL walker's GENERATIVE half
 *   (prompts/STR_ROUTEWALKING_SPEC.md §2A.3/§4/§5) — the space-frame as ONE measured unit ×n over
 *   a measured surface (`instanced-by n`, extent=f(n)). GENERATIVE: reconstructs COUNT + COVERAGE
 *   within tol, NEVER bit-exact. Oracle = pristine Terminal_extracted.db, NEVER output.db.
 *   Read the §-log; exit code is not the evidence (Log Mandate).
 *
 * METHOD: require the UNMODIFIED deploy/dev/str_walker.js; measure the 33,324 real IfcPlate cloud
 *   (unit, surface domain, band density); predict the count; walk the tessellation; test extent=f(n).
 *
 * CLAIMS (each names the issue it proves or disproves):
 *   C1 UNIT-EMERGES     — the repeated unit = MEASURED modal bbox; share ≥95% (measured 96.6%) ⇒
 *                         it is genuinely ONE tiled unit, not assumed.
 *   C2 SURFACE-SHELL    — it tiles a thin 2-manifold SHELL: LOCALLY thin (median 1m-cell z-spread
 *                         ≪ global z-span) yet curving globally, covering ≥85% of the footprint.
 *                         (The shell curves in x AND y — z=f(x,y); the walker's z=f(x) is a mid-surface
 *                         simplification, stated honestly.)
 *   C3 COUNT-RECONSTRUCT— GENERATIVE prediction: interior band-density × nBands reconstructs the
 *                         extracted count within 10% (measured 1.3%). The tessellation is regular.
 *   C4 EXTENT=f(N)      — instanced-by-n: walk(n=1) → exactly 1 unit (collapse vanishes); walk(n=k)
 *                         grows monotonically; walk(full) ≈ extracted count over the full domain.
 *   C5 NON-INVENT+TAIL  — unit/domain/density measured; provenance derived:str-walk; the 1,121
 *                         non-modal plates (3.4%) are REPORTED not dropped; NOT claimed bit-exact.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var SW = require(path.join(ROOT, 'deploy/dev/str_walker.js'));
var TERMINAL_DB = path.join(ROOT, 'deploy/buildings/Terminal_extracted.db');
var LOG = path.join(ROOT, 'logs', 'witness_str_tessellate_' +
  new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + '.log');

var COUNT_TOL = 0.10;          // ≤10% generative count error (measured 1.3%)
var SHARE_MIN = 0.95;          // modal unit share (measured 96.6%)

var _lines = [];
function log(s) { _lines.push(s); console.log(s); }
var pass = 0, fail = 0;
function assert(claim, cond, detail) {
  if (cond) { pass++; log('  ✅ ' + claim + ' — ' + detail); }
  else { fail++; log('  ❌ ' + claim + ' — ' + detail); }
}

(async function main() {
  log('═══ W-STR-TESSELLATE — space-frame = unit ×n over a measured surface (generative) ═══');
  var SQL = await initSqlJs();
  var db = new SQL.Database(new Uint8Array(fs.readFileSync(TERMINAL_DB)));
  function rows(s) { var r = db.exec(s); return r.length ? r[0].values : []; }
  var plates = rows("SELECT t.center_x,t.center_y,t.center_z,t.bbox_x,t.bbox_y,t.bbox_z " +
    "FROM elements_meta m JOIN element_transforms t ON m.guid=t.guid " +
    "WHERE m.discipline='STR' AND m.ifc_class='IfcPlate'")
    .map(function (r) { return { x: r[0], y: r[1], z: r[2], bx: r[3], by: r[4], bz: r[5] }; });
  log('§STR-TESS loaded ' + plates.length + ' IfcPlate from Terminal');

  var tess = SW.swDeriveTessellation(plates, {});
  log('§STR-TESS unit ' + tess.unit.bx + '×' + tess.unit.by + '×' + tess.unit.bz +
      ' | domain ' + (tess.domain.maxX - tess.domain.minX).toFixed(0) + '×' +
      (tess.domain.maxY - tess.domain.minY).toFixed(0) + 'm, z ' +
      tess.domain.minZ.toFixed(0) + '→' + tess.domain.maxZ.toFixed(0) + 'm | ' +
      tess.nBands + ' bands, density ' + tess.bandDensity.toFixed(0) + '/band');

  // ── C1 UNIT-EMERGES ──
  assert('C1 UNIT-EMERGES', tess.modalShare >= SHARE_MIN,
    'modal unit share ' + (100 * tess.modalShare).toFixed(1) + '% (≥' + (100 * SHARE_MIN) + '%)');

  // ── C2 SURFACE-SHELL (locally thin, globally curved, covers the footprint) ──
  var zSpanGlobal = tess.domain.maxZ - tess.domain.minZ;
  var cellZ = {}, cellSet = {};
  plates.forEach(function (p) {
    var k = Math.floor(p.x) + ',' + Math.floor(p.y);
    (cellZ[k] = cellZ[k] || []).push(p.z); cellSet[k] = 1;
  });
  var spreads = Object.keys(cellZ).map(function (k) { var a = cellZ[k]; return Math.max.apply(null, a) - Math.min.apply(null, a); }).sort(function (a, b) { return a - b; });
  var medLocal = spreads[Math.floor(spreads.length / 2)];
  var footCells = Math.ceil(tess.domain.maxX - tess.domain.minX) * Math.ceil(tess.domain.maxY - tess.domain.minY);
  var coverage = Object.keys(cellSet).length / footCells;
  assert('C2 SURFACE-SHELL', medLocal * 10 < zSpanGlobal && coverage >= 0.85,
    'local 1m-cell z-spread median ' + medLocal.toFixed(2) + 'm ≪ global ' + zSpanGlobal.toFixed(1) +
    'm (thin shell) & footprint coverage ' + (100 * coverage).toFixed(1) + '% (≥85%)');

  // ── C3 COUNT-RECONSTRUCT ──
  var err = Math.abs(tess.predictedN - tess.extractedN) / tess.extractedN;
  assert('C3 COUNT-RECONSTRUCT', err <= COUNT_TOL,
    'predicted ' + tess.predictedN + ' vs extracted ' + tess.extractedN + ' = ' +
    (100 * err).toFixed(1) + '% error (≤' + (100 * COUNT_TOL) + '%)');

  // ── C4 EXTENT=f(N) ──
  var w1 = SW.swWalkTessellation(tess, 1, {});
  var wHalf = SW.swWalkTessellation(tess, Math.round(tess.extractedN / 2), {});
  var wFull = SW.swWalkTessellation(tess, tess.extractedN, {});
  var monotonic = w1.length === 1 && wHalf.length > w1.length && wFull.length >= wHalf.length;
  var fullErr = Math.abs(wFull.length - tess.extractedN) / tess.extractedN;
  assert('C4 EXTENT=f(N)', monotonic && fullErr <= COUNT_TOL,
    'walk(1)=' + w1.length + ' (collapse), walk(half)=' + wHalf.length + ', walk(full)=' + wFull.length +
    ' vs ' + tess.extractedN + ' = ' + (100 * fullErr).toFixed(1) + '%');

  // ── C5 NON-INVENT + TAIL ──
  var allTraced = wFull.every(function (u) {
    return u.provenance === 'derived:str-walk' &&
      u.bx === tess.unit.bx && u.by === tess.unit.by && u.bz === tess.unit.bz;
  });
  assert('C5 NON-INVENT+TAIL', allTraced && tess.tail > 0,
    'every unit derived:str-walk + measured unit dims = ' + allTraced + '; non-modal tail ' +
    tess.tail + ' (' + (100 * tess.tail / tess.extractedN).toFixed(1) + '%) reported, NOT bit-exact');

  log('───────────────────────────────────────────────');
  log('W-STR-TESSELLATE: ' + pass + ' PASS / ' + fail + ' FAIL');
  db.close();
  try { fs.mkdirSync(path.dirname(LOG), { recursive: true }); fs.writeFileSync(LOG, _lines.join('\n')); log('§LOG ' + LOG); } catch (e) {}
  process.exit(fail ? 1 : 0);
})();
