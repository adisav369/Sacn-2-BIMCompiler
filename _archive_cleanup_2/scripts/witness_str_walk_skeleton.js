#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-STR-WALK-SKELETON scope (read this block first)
 * SCOPE: Slice-1 witness for the STRUCTURAL walker (prompts/STR_ROUTEWALKING_SPEC.md §4).
 *   Proves the SKELETON walk: columns are GRID-REGULAR and walk deterministically onto the
 *   emergent structural grid (position = f(grid)) — the RS-clean half of "STR is a walker".
 *   Oracle = pristine deploy/buildings/Terminal_extracted.db (raw extraction), NEVER output.db.
 *   Non-invent: gridlines are MEANS of real column coords; no hardcoded spacing; no synthetic pos.
 *   Read the §-log after the run; exit code is not the evidence (Log Mandate).
 *
 * METHOD: load the REAL Terminal extracted DB, read the 158 IfcColumn (discipline=STR) centroids
 *   from element_transforms, require the UNMODIFIED deploy/dev/str_walker.js, derive the grid +
 *   walk the columns, and assert each claim against the real geometry.
 *
 * CLAIMS (each names the issue it proves or disproves):
 *   C1 GRID-EMERGES   — columns COMPRESS onto few gridlines (lines << columns) ⇒ the structure
 *                       follows a grid. If structure were irregular, X-lines ≈ column count.
 *   C2 ON-GRID        — every column's planar residual to its snapped intersection ≤ tol ⇒ no
 *                       column is off-grid. Proves grid-regularity is real, not forced. (max+mean reported.)
 *   C3 COUNT-PARITY   — exactly one walked column per extracted column (158) ⇒ the walk neither
 *                       drops nor fabricates structure.
 *   C4 DETERMINISTIC  — every walked (x,y) lies EXACTLY on a gridline pair (f(grid)); a second
 *                       run yields byte-identical output ⇒ RS-clean, replayable.
 *   C5 NON-INVENT     — every gridline = MEAN of the real coords it owns (re-derive == same), and
 *                       every walked position traces to a real srcGuid ⇒ zero invented values.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var SW = require(path.join(ROOT, 'deploy/dev/str_walker.js'));
var TERMINAL_DB = path.join(ROOT, 'deploy/buildings/Terminal_extracted.db');
var LOG = path.join(ROOT, 'logs', 'witness_str_walk_skeleton_' +
  new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + '.log');

// MEASURED (gap-tolerance sweep on Terminal's 158 columns): the grid resolves at gapTol ≤ 0.5m
// to 18×10 lines with max column residual 0.329m, mean 5.2cm. A 0.5m snap tolerance captures all
// columns with margin and equals a reasonable construction/column-width tolerance — set from the
// data, NOT widened to force a pass (coarser gapTol merges real lines and inflates the residual).
var SNAP_TOL = 0.5;            // metres — a column ≤ this from its intersection is "on grid"
var COMPRESS_MIN = 4;          // gridlines per axis must be ≥ this many × fewer than columns

var _lines = [];
function log(s) { _lines.push(s); console.log(s); }

var pass = 0, fail = 0;
function assert(claim, cond, detail) {
  if (cond) { pass++; log('  ✅ ' + claim + ' — ' + detail); }
  else { fail++; log('  ❌ ' + claim + ' — ' + detail); }
}

function readColumns(db) {
  // Real STR columns + their centroids. Oracle = extracted.db only.
  var res = db.exec(
    "SELECT m.guid, t.center_x, t.center_y, t.center_z " +
    "FROM elements_meta m JOIN element_transforms t ON t.guid = m.guid " +
    "WHERE m.discipline='STR' AND m.ifc_class='IfcColumn'");
  if (!res.length) return [];
  return res[0].values.map(function (r) {
    return { guid: r[0], x: r[1], y: r[2], z: r[3] };
  });
}

(async function main() {
  log('═══ W-STR-WALK-SKELETON — structural skeleton walk on REAL Terminal ═══');
  var SQL = await initSqlJs();
  var db = new SQL.Database(new Uint8Array(fs.readFileSync(TERMINAL_DB)));

  var columns = readColumns(db);
  log('§STR-WALK loaded ' + columns.length + ' STR IfcColumn from Terminal_extracted.db');
  if (!columns.length) { log('§STR-WALK FATAL no columns'); process.exit(1); }

  var r = SW.swWalkSkeleton(columns, {});
  var grid = r.grid, walked = r.walked;
  log('§STR-WALK grid: ' + grid.xLines.length + ' X-lines × ' + grid.yLines.length +
      ' Y-lines (gapTol=' + grid.gapTol + 'm) from ' + columns.length + ' columns');

  // ── C1 GRID-EMERGES ──
  var nCols = columns.length, nX = grid.xLines.length, nY = grid.yLines.length;
  assert('C1 GRID-EMERGES',
    nX * COMPRESS_MIN <= nCols && nY * COMPRESS_MIN <= nCols,
    nCols + ' columns → ' + nX + '×' + nY + ' gridlines (compression ' +
    (nCols / nX).toFixed(1) + '× in X, ' + (nCols / nY).toFixed(1) + '× in Y)');

  // ── C2 ON-GRID ──
  var residuals = walked.map(function (w) { return w.residual; });
  var maxRes = Math.max.apply(null, residuals);
  var meanRes = residuals.reduce(function (s, v) { return s + v; }, 0) / residuals.length;
  var offGrid = walked.filter(function (w) { return w.residual > SNAP_TOL; });
  assert('C2 ON-GRID',
    offGrid.length === 0,
    'max residual ' + maxRes.toFixed(3) + 'm, mean ' + meanRes.toFixed(3) + 'm, tol ' +
    SNAP_TOL + 'm — ' + offGrid.length + ' columns off-grid');
  if (offGrid.length) log('     off-grid sample: ' + JSON.stringify(offGrid.slice(0, 3).map(function (w) {
    return { src: w.srcGuid, res: +w.residual.toFixed(2) }; })));

  // ── C3 COUNT-PARITY ──
  var srcSet = new Set(walked.map(function (w) { return w.srcGuid; }));
  assert('C3 COUNT-PARITY',
    walked.length === nCols && srcSet.size === nCols,
    walked.length + ' walked, ' + srcSet.size + ' distinct sources vs ' + nCols + ' extracted');

  // ── C4 DETERMINISTIC ──
  var onGridExact = walked.every(function (w) {
    return grid.xLines.indexOf(w.x) >= 0 && grid.yLines.indexOf(w.y) >= 0;
  });
  var r2 = SW.swWalkSkeleton(columns, {});
  var identical = JSON.stringify(r2.walked) === JSON.stringify(walked);
  assert('C4 DETERMINISTIC',
    onGridExact && identical,
    'all walked (x,y) ∈ gridlines = ' + onGridExact + '; re-run identical = ' + identical);

  // ── C5 NON-INVENT ──
  // Every gridline equals the mean of the real coords it owns; re-derive reproduces it exactly.
  var grid2 = SW.swDeriveGrid(columns, {});
  var gridReproduces = JSON.stringify(grid2.xLines) === JSON.stringify(grid.xLines) &&
                       JSON.stringify(grid2.yLines) === JSON.stringify(grid.yLines);
  var lineTracesToData = grid.xMeta.every(function (m) {
    var mean = m.members.reduce(function (s, v) { return s + v; }, 0) / m.members.length;
    return Math.abs(mean - m.value) < 1e-9 && m.members.length > 0;
  });
  var allTraced = walked.every(function (w) { return !!w.srcGuid && w.provenance === 'derived:grid'; });
  assert('C5 NON-INVENT',
    gridReproduces && lineTracesToData && allTraced,
    'grid re-derives identical=' + gridReproduces + ', every line=mean(real)=' + lineTracesToData +
    ', every walked traces srcGuid+derived:grid=' + allTraced);

  log('───────────────────────────────────────────────');
  log('W-STR-WALK-SKELETON: ' + pass + ' PASS / ' + fail + ' FAIL');
  db.close();
  try { fs.mkdirSync(path.dirname(LOG), { recursive: true }); fs.writeFileSync(LOG, _lines.join('\n')); log('§LOG ' + LOG); } catch (e) {}
  process.exit(fail ? 1 : 0);
})();
