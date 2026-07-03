#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-STR-BRIDGE-ARCONLY scope (read this block first)
 * SCOPE: The user case — a real user drops an ARC-ONLY job and uses the modeller to WALK the other
 *   disciplines (VISION-LOCK). swbInit must AUTO-PICK the grid source: STR columns → swDeriveGrid
 *   (column-framed); no STR columns → swDeriveSemiGrid from ARC walls (wall-bearing), imposing NO
 *   column frame (W-STR-GENERAL-SC: fabricate nothing). Oracle/source = pristine *_extracted.db,
 *   NEVER output.db. Read the §-log; exit code is not the evidence (Log Mandate).
 *
 * CLAIMS:
 *   C1 COLUMN-FRAMED   — Terminal (158 STR columns) → swbInit picks column-framed, 18×10 grid, 108
 *                        girders, system='column-framed' (REGRESSION guard — the existing path holds).
 *   C2 WALLBEARING-PICK— SC (0 STR columns) → swbInit derives the SEMI-GRID from ARC walls, system=
 *                        'wall-bearing', gridSource='derived:arc-semigrid', grid ≥5×5 (the ARC-only walk).
 *   C3 NO-FABRICATION  — the wall-bearing state has 0 walked columns AND 0 girders → swbTabData shows
 *                        0/0, no column frame fabricated; honest (the non-invent keystone).
 *   C4 GRACEFUL        — swbTabData + swbOnGridMove on a wall-bearing building return valid data and do
 *                        not throw (empty walked/girders handled); the grid still moves.
 *   C5 ABSTRACT        — the SAME swbInit auto-picks by DATA (column count), no building name; the SC
 *                        semi-grid lines are means of real wall coords (non-invent).
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var SW = require(path.join(ROOT, 'deploy/dev/str_walker.js'));
var BRIDGE = require(path.join(ROOT, 'deploy/dev/str_walker_bridge.js'));
var KERNEL_SRC = path.join(ROOT, 'deploy/dev/kernel_ops.js');
var TERMINAL_DB = path.join(ROOT, 'deploy/buildings/Terminal_extracted.db');
var SC_DB = path.join(ROOT, 'deploy/buildings/Schependomlaan_extracted.db');
var LOG = path.join(ROOT, 'logs', 'witness_str_bridge_arconly_' +
  new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + '.log');

var _lines = [];
function log(s) { _lines.push(s); console.log(s); }
var pass = 0, fail = 0;
function assert(claim, cond, detail) {
  if (cond) { pass++; log('  ✅ ' + claim + ' — ' + detail); }
  else { fail++; log('  ❌ ' + claim + ' — ' + detail); }
}
function loadKernelOps() {
  var win = {};
  new Function('window', 'indexedDB', 'console', fs.readFileSync(KERNEL_SRC, 'utf8'))(win, undefined, console);
  return win.KernelOps;
}

(async function main() {
  log('═══ W-STR-BRIDGE-ARCONLY — swbInit auto-picks column-framed vs wall-bearing ═══');
  var SQL = await initSqlJs();

  // ── C1 COLUMN-FRAMED (Terminal regression) ──
  var tdb = new SQL.Database(new Uint8Array(fs.readFileSync(TERMINAL_DB)));
  var ts = BRIDGE.swbInit(tdb, {});
  var tTab = BRIDGE.swbTabData();
  assert('C1 COLUMN-FRAMED',
    ts && ts.system === 'column-framed' && ts.columnCount === 158 &&
    ts.base.grid.xLines.length === 18 && ts.base.grid.yLines.length === 10 &&
    ts.base.girders.length === 108 && tTab.system === 'column-framed',
    'Terminal → system=' + (ts && ts.system) + ', ' + (ts && ts.columnCount) + ' cols, grid ' +
    (ts && ts.base.grid.xLines.length) + '×' + (ts && ts.base.grid.yLines.length) + ', ' +
    (ts && ts.base.girders.length) + ' girders');
  tdb.close();

  // ── C2 WALLBEARING-PICK (SC stripped to ARC-only = the user's "drop ARC only" job) ──
  // SC itself has 23 STR columns; the USER case is an ARC-ONLY drop. Simulate it faithfully by
  // dropping every non-ARC row in-memory (the pristine ARC walls remain) → swbInit sees 0 STR columns.
  var sdb = new SQL.Database(new Uint8Array(fs.readFileSync(SC_DB)));
  sdb.run("DELETE FROM elements_meta WHERE discipline <> 'ARC'");
  log('§ARCONLY stripped SC to ARC-only: ' +
      sdb.exec("SELECT COUNT(*) FROM elements_meta WHERE discipline='STR' AND ifc_class='IfcColumn'")[0].values[0][0] +
      ' STR columns remain, ' +
      sdb.exec("SELECT COUNT(*) FROM elements_meta WHERE discipline='ARC' AND ifc_class IN ('IfcWall','IfcWallStandardCase')")[0].values[0][0] + ' ARC walls');
  var ss = BRIDGE.swbInit(sdb, {});
  assert('C2 WALLBEARING-PICK',
    ss && ss.system === 'wall-bearing' && ss.columnCount === 0 && ss.gridSource === 'derived:arc-semigrid' &&
    ss.base.grid.xLines.length >= 5 && ss.base.grid.yLines.length >= 5,
    'SC → system=' + (ss && ss.system) + ', ' + (ss && ss.wallCount) + ' ARC walls → semi-grid ' +
    (ss && ss.base.grid.xLines.length) + '×' + (ss && ss.base.grid.yLines.length) + ' (' + (ss && ss.gridSource) + ')');

  // ── C3 NO-FABRICATION ──
  var sTab = BRIDGE.swbTabData();
  assert('C3 NO-FABRICATION',
    ss.base.walked.length === 0 && ss.base.girders.length === 0 &&
    sTab.columns === 0 && sTab.girders === 0 && sTab.system === 'wall-bearing',
    'wall-bearing state: ' + ss.base.walked.length + ' walked cols, ' + ss.base.girders.length +
    ' girders; tab shows ' + sTab.columns + '/' + sTab.girders + ' — no column frame fabricated');

  // ── C4 GRACEFUL (tab + grid-move on a wall-bearing building must not throw) ──
  var KernelOps = loadKernelOps(); KernelOps.ensureTable(sdb);
  var commit = function (t, p, i, o) { return KernelOps.commitOp(sdb, t, p, i, o); };
  var threw = false, moveRes = null;
  try {
    var datum = ss.base.grid.xLines[0];
    moveRes = BRIDGE.swbOnGridMove({ axis: 'x', datum: datum, delta: 0.5 }, commit, {});
  } catch (e) { threw = true; log('   threw: ' + e.message); }
  assert('C4 GRACEFUL',
    !threw && sTab.grid.indexOf('×') > 0 && Array.isArray(sTab.elements) && moveRes !== null,
    'tab grid=' + sTab.grid + ', elements[]=' + Array.isArray(sTab.elements) + '; grid-move on wall-bearing did not throw (committed ' + (moveRes && moveRes.committed) + ')');

  // ── C5 ABSTRACT / NON-INVENT ──
  // same swbInit auto-picked by data; the SC semi-grid lines are means of real wall coords (re-derive == same)
  var walls = sdb.exec("SELECT t.center_x,t.center_y,t.bbox_x,t.bbox_y FROM elements_meta m " +
    "JOIN element_transforms t ON t.guid=m.guid WHERE m.discipline='ARC' AND m.ifc_class IN ('IfcWall','IfcWallStandardCase')")[0]
    .values.map(function (r) { return { cx: r[0], cy: r[1], lx: r[2], ly: r[3] }; });
  var reGrid = SW.swDeriveSemiGrid(walls, {});
  var reproduces = JSON.stringify(reGrid.xLines) === JSON.stringify(ss.base.grid.xLines) &&
                   JSON.stringify(reGrid.yLines) === JSON.stringify(ss.base.grid.yLines);
  assert('C5 ABSTRACT',
    reproduces && ss.system !== ts.system,
    'auto-pick by data (col-count) → SC=wall-bearing, Terminal=column-framed; SC semi-grid re-derives identical=' + reproduces);
  sdb.close();

  log('───────────────────────────────────────────────');
  log('W-STR-BRIDGE-ARCONLY: ' + pass + ' PASS / ' + fail + ' FAIL');
  try { fs.mkdirSync(path.dirname(LOG), { recursive: true }); fs.writeFileSync(LOG, _lines.join('\n')); log('§LOG ' + LOG); } catch (e) {}
  process.exit(fail ? 1 : 0);
})();
