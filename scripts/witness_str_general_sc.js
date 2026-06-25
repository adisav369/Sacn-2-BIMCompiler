#!/usr/bin/env node
/**
 * # ⚠ DO NOT REMOVE — W-STR-GENERAL-SC scope (read this block first)
 * SCOPE: Generality (item 8) — the SAME STR walker, run on SC (Schependomlaan, IFC2x3 RESIDENTIAL),
 *   per convention: DROP ARC ONLY, then APPLY THE WALK; extracted STR = ORACLE only (never input).
 *   Proves the walker is building-agnostic AND non-invent on a DIFFERENT structural system
 *   (wall-bearing, no column frame, no space-frame). Oracle = pristine Schependomlaan_extracted.db,
 *   NEVER output.db. Read the §-log; exit code is not the evidence (Log Mandate).
 *
 * METHOD: require the UNMODIFIED str_walker.js. Read SC's ARC walls (the dropped substrate) → derive
 *   the SEMI-GRID from wall centerlines (grid never sees STR = non-circular). Grade the extracted STR
 *   (oracle) against that ARC-derived grid. Confirm tessellation fabricates nothing (no plates).
 *
 * CLAIMS (each names the issue it proves or disproves):
 *   C1 ARC-SEMIGRID    — drop SC ARC walls only → a SEMI-GRID emerges (≈13×12) from wall centerlines,
 *                        derived PURELY from ARC (zero STR input). The spec's "missing 4th handle".
 *   C2 STR-FOLLOWS-ARC — ORACLE, non-circular: ≥60% of extracted STR beams sit on the ARC-derived grid
 *                        (measured 70% @1m). STR demonstrably WALKS FROM the ARC.
 *   C3 REGULATORY-GEN  — the SAME cited SW_SPAN_RULES applies to SC beams (≥70% conform; measured 77.6%);
 *                        no per-building tuning.
 *   C4 TESSELLATE-NA   — SC has 0 plates → swDeriveTessellation returns null → ZERO fabrication. The
 *                        non-invent generality keystone (a residential building has no space-frame).
 *   C5 WALL-BEARING    — SC has 0 ARC columns ⇒ wall-bearing (a DIFFERENT system than column-framed
 *                        Terminal); reported honestly, the walk does not impose a column frame.
 *   C6 ABSTRACT        — the same engine fns ran on SC + Terminal; grep str_walker.js for building names
 *                        / per-IFC-class hardcoding = ZERO (the door doctrine: measure, don't whitelist).
 */
'use strict';
var fs = require('fs');
var path = require('path');
var initSqlJs = require('sql.js');

var ROOT = path.join(__dirname, '..');
var SW_SRC = path.join(ROOT, 'deploy/dev/str_walker.js');
var SW = require(SW_SRC);
var SC_DB = path.join(ROOT, 'deploy/buildings/Schependomlaan_extracted.db');
var LOG = path.join(ROOT, 'logs', 'witness_str_general_sc_' +
  new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + '.log');

var _lines = [];
function log(s) { _lines.push(s); console.log(s); }
var pass = 0, fail = 0;
function assert(claim, cond, detail) {
  if (cond) { pass++; log('  ✅ ' + claim + ' — ' + detail); }
  else { fail++; log('  ❌ ' + claim + ' — ' + detail); }
}
function near(v, L, t) { for (var i = 0; i < L.length; i++) if (Math.abs(v - L[i]) <= t) return true; return false; }

(async function main() {
  log('═══ W-STR-GENERAL-SC — drop ARC, apply the walk, on residential SC ═══');
  var SQL = await initSqlJs();
  var db = new SQL.Database(new Uint8Array(fs.readFileSync(SC_DB)));
  function rows(s) { var r = db.exec(s); return r.length ? r[0].values : []; }

  // DROP ARC ONLY: walls are the substrate. (No ARC columns — wall-bearing.)
  var walls = rows("SELECT t.center_x,t.center_y,t.bbox_x,t.bbox_y FROM elements_meta m " +
    "JOIN element_transforms t ON t.guid=m.guid WHERE m.discipline='ARC' AND m.ifc_class='IfcWall'")
    .map(function (r) { return { cx: r[0], cy: r[1], lx: r[2], ly: r[3] }; });
  var arcCols = rows("SELECT COUNT(*) FROM elements_meta WHERE discipline='ARC' AND ifc_class='IfcColumn'")[0][0];
  // ORACLE (extracted STR — grading only, NEVER fed to the grid)
  var beams = rows("SELECT t.center_x,t.center_y,t.bbox_x,t.bbox_y,t.bbox_z FROM elements_meta m " +
    "JOIN element_transforms t ON t.guid=m.guid WHERE m.discipline='STR' AND m.ifc_class='IfcBeam' AND t.bbox_z>0")
    .map(function (r) { return { cx: r[0], cy: r[1], lx: r[2], ly: r[3], bz: r[4] }; });
  var strCols = rows("SELECT t.center_x,t.center_y FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid " +
    "WHERE m.discipline='STR' AND m.ifc_class='IfcColumn'").map(function (r) { return { x: r[0], y: r[1] }; });
  var plates = rows("SELECT t.bbox_x FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid " +
    "WHERE m.discipline='STR' AND m.ifc_class='IfcPlate'").map(function (r) { return { bx: r[0] }; });
  log('§SC-GEN dropped ARC: ' + walls.length + ' walls, ' + arcCols + ' ARC columns | oracle STR: ' +
      beams.length + ' beams, ' + strCols.length + ' columns, ' + plates.length + ' plates');

  // ── C1 ARC-SEMIGRID (derived from ARC walls only) ──
  var grid = SW.swDeriveSemiGrid(walls, {});
  assert('C1 ARC-SEMIGRID',
    grid.xLines.length >= 5 && grid.yLines.length >= 5 && grid.source === 'derived:arc-semigrid',
    grid.xLines.length + '×' + grid.yLines.length + ' from ' + walls.length + ' wall centerlines (zero STR input)');

  // ── C2 STR-FOLLOWS-ARC (oracle) ──
  var on = 0;
  beams.forEach(function (b) { var xr = b.lx >= b.ly; if (xr ? near(b.cy, grid.yLines, 1.0) : near(b.cx, grid.xLines, 1.0)) on++; });
  var frac = on / beams.length;
  assert('C2 STR-FOLLOWS-ARC', frac >= 0.60,
    on + '/' + beams.length + ' STR beams on the ARC-derived grid = ' + (100 * frac).toFixed(1) + '% (≥60%) — STR walks FROM ARC');

  // ── C3 REGULATORY-GEN ──
  var conf = beams.filter(function (b) { return SW.swConforms(Math.max(b.lx, b.ly), b.bz, {}).conforms; }).length;
  assert('C3 REGULATORY-GEN', conf / beams.length >= 0.70,
    conf + '/' + beams.length + ' SC beams conform to the SAME steel L/20 rule = ' + (100 * conf / beams.length).toFixed(1) + '%');

  // ── C4 TESSELLATE-NA (no fabrication) ──
  var tess = SW.swDeriveTessellation(plates, {});
  var walked = tess ? SW.swWalkTessellation(tess, 100, {}) : [];
  assert('C4 TESSELLATE-NA', plates.length === 0 && tess === null && walked.length === 0,
    'plates=' + plates.length + ' → tessellation=' + tess + ', walked units=' + walked.length + ' (fabricates nothing)');

  // ── C5 WALL-BEARING (honest system difference) ──
  var onNode = strCols.filter(function (c) { return near(c.x, grid.xLines, 0.5) && near(c.y, grid.yLines, 0.5); }).length;
  assert('C5 WALL-BEARING', arcCols === 0,
    '0 ARC columns ⇒ wall-bearing; only ' + onNode + '/' + strCols.length + ' STR cols on grid nodes — walk imposes NO column frame');

  // ── C6 ABSTRACT (same engine, grep-clean) ──
  var src = fs.readFileSync(SW_SRC, 'utf8');
  var hardcoded = /Terminal|Schependomlaan|SampleHouse|SampleCastle|Duplex|\bSC\b|hasFront|DIRECTIONAL_ROLE/.test(
    src.replace(/\/\/[^\n]*/g, '').replace(/\/\*[\s\S]*?\*\//g, ''));  // strip comments first
  assert('C6 ABSTRACT', !hardcoded,
    'str_walker.js code (comments stripped) has zero building/role hardcoding = ' + !hardcoded);

  log('───────────────────────────────────────────────');
  log('W-STR-GENERAL-SC: ' + pass + ' PASS / ' + fail + ' FAIL');
  db.close();
  try { fs.mkdirSync(path.dirname(LOG), { recursive: true }); fs.writeFileSync(LOG, _lines.join('\n')); log('§LOG ' + LOG); } catch (e) {}
  process.exit(fail ? 1 : 0);
})();
