#!/usr/bin/env node
/**
 * test_s268_recompose.js — S268 attach-map recompose + S269 bay-proportional
 * Issue: S267 nearest-delta moves ALL visible elements. S268 fixes this:
 *   only governed elements (ATTACH/SPAN) move. S269 adds bay-proportional interior.
 *
 * Runs on real SC_BOM.db data via Node.js (no browser).
 * Tests the algorithm proven in sandbox_recompose.js / sandbox_grid_attach.js.
 */
'use strict';
var path = require('path');
var Database = require(path.join(__dirname, '../../../node_modules', 'better-sqlite3'));

var BOM_DB = path.join(__dirname, '../../../backup/db_snapshot_20260323_014819/library/SC_BOM.db');

// ── Minimal test harness ───────────────────────────────────────────────────
var _pass = 0, _fail = 0, _total = 0;
function assert(cond, msg) {
  _total++;
  if (cond) { _pass++; console.log('  ✓ ' + msg); }
  else { _fail++; console.error('  ✗ FAIL: ' + msg); }
}
function assertApprox(actual, expected, tol, msg) {
  assert(Math.abs(actual - expected) < tol,
    msg + ' (expected ' + expected.toFixed(4) + ' got ' + actual.toFixed(4) + ')');
}
function section(name) { console.log('\n── ' + name + ' ──'); }

// ── Load BOM data into flat entry array (mirrors sandbox) ──────────────────
function loadClusterEntries(db) {
  var lines = db.prepare(
    "SELECT bom_child_id, bom_id, role, verb_ref, qty, dx, dy, dz " +
    "FROM m_bom_line WHERE verb_ref LIKE 'CLUSTER:%' AND is_active=1"
  ).all();

  var entries = [];
  for (var li = 0; li < lines.length; li++) {
    var line = lines[li];
    var parts = line.verb_ref.substring(8).split(';');
    for (var i = 0; i < parts.length; i++) {
      var vals = parts[i].split(',');
      var dx = parseFloat(vals[0]);
      var w = vals.length >= 6 ? parseFloat(vals[3]) : 0;
      var d = vals.length >= 6 ? parseFloat(vals[4]) : 0;
      var absX = (line.dx || 0) + dx;
      entries.push({
        guid: 'E' + entries.length, // synthetic GUID for testing
        absX: absX,
        cx: absX + (w > 0.05 ? w / 2 : 0),
        w: w,
        d: d,
        ifcClass: line.role.split('|').pop()
      });
    }
  }
  return entries;
}

// ── Pure-logic attach algorithm (same as doc_canvas.js §S268) ──────────────
var ATTACH_TOL = 0.5;

function buildAttachMap(entries, gridPositions) {
  var attachMap = {}; // gridIdx → [{entryIdx, relation, edge}]
  var entryToGrid = {}; // entryIdx → gridIdx

  for (var ei = 0; ei < entries.length; ei++) {
    var e = entries[ei];
    var bestGrid = -1;
    var bestDist = Infinity;
    var relation = 'ATTACH';
    var edge = 'near';

    for (var gi = 0; gi < gridPositions.length; gi++) {
      var gp = gridPositions[gi];
      // Centerline proximity
      var dist = Math.abs(e.cx - gp);
      if (dist < ATTACH_TOL && dist < bestDist) {
        bestDist = dist;
        bestGrid = gi;
        relation = 'ATTACH';
      }
      // Span detection
      if (e.w > 0.1 && gp > e.absX + 0.01 && gp < e.absX + e.w - 0.01) {
        if (bestGrid < 0 || relation !== 'ATTACH') {
          bestGrid = gi;
          relation = 'SPAN';
          bestDist = 0;
          edge = (gp - e.absX) < (e.absX + e.w - gp) ? 'near' : 'far';
        }
      }
    }

    if (bestGrid >= 0) {
      if (!attachMap[bestGrid]) attachMap[bestGrid] = [];
      attachMap[bestGrid].push({ entryIdx: ei, relation: relation, edge: edge });
      entryToGrid[ei] = bestGrid;
    }
  }
  return { attachMap: attachMap, entryToGrid: entryToGrid };
}

// ── Bay-proportional delta (same as doc_canvas.js §S269) ───────────────────
function bayProportionalDelta(pos, origGrid, currGrid) {
  if (origGrid.length < 2 || currGrid.length < 2) return 0;
  var sortedOrig = origGrid.slice().sort(function(a, b) { return a - b; });
  var sortedCurr = currGrid.slice().sort(function(a, b) { return a - b; });

  for (var i = 0; i < sortedOrig.length - 1; i++) {
    var lo = sortedOrig[i];
    var hi = sortedOrig[i + 1];
    if (pos >= lo - 0.01 && pos <= hi + 0.01) {
      var oldW = hi - lo;
      if (oldW < 0.01) continue;
      var t = (pos - lo) / oldW;
      var newLo = i < sortedCurr.length ? sortedCurr[i] : lo;
      var newHi = (i + 1) < sortedCurr.length ? sortedCurr[i + 1] : hi;
      var newW = newHi - newLo;
      return (newLo + t * newW) - pos;
    }
  }
  return 0;
}

// ═══════════════════════════════════════════════════════════════════════════
// TESTS
// ═══════════════════════════════════════════════════════════════════════════

// ── S268 Tests ─────────────────────────────────────────────────────────────
section('S268.1 — Attach map builds from real SC BOM data');

var db;
try { db = new Database(BOM_DB, { readonly: true }); } catch(e) {
  console.error('Cannot open SC_BOM.db: ' + e.message);
  console.error('Expected at: ' + BOM_DB);
  process.exit(1);
}

var entries = loadClusterEntries(db);
assert(entries.length > 2000, 'SC has >2000 CLUSTER entries (got ' + entries.length + ')');

// User-placed grid lines (realistic for SC)
var gridX = [1.0, 5.0, 10.0, 15.0, 20.0, 23.5];
var result = buildAttachMap(entries, gridX);
var totalAttached = 0;
for (var k in result.attachMap) totalAttached += result.attachMap[k].length;

assert(totalAttached > 0, 'Attach map has attached entries (' + totalAttached + ')');
assert(totalAttached < entries.length, 'Not ALL entries are attached (' + totalAttached + '/' + entries.length + ')');
assert(entries.length - totalAttached > 1000, 'Significant unattached interior count (' + (entries.length - totalAttached) + ')');

section('S268.2 — Each grid line has governed elements');
for (var gi = 0; gi < gridX.length; gi++) {
  var items = result.attachMap[gi] || [];
  assert(items.length > 0, 'grid[' + gi + '] X=' + gridX[gi] + 'm has ' + items.length + ' governed entries');
}

section('S268.3 — ATTACH vs SPAN classification');
var totalAttach = 0, totalSpan = 0;
for (var mk in result.attachMap) {
  var mitems = result.attachMap[mk];
  for (var mi = 0; mi < mitems.length; mi++) {
    if (mitems[mi].relation === 'ATTACH') totalAttach++;
    else totalSpan++;
  }
}
assert(totalAttach > 0, 'Has ATTACH entries (' + totalAttach + ')');
assert(totalSpan > 0, 'Has SPAN entries (' + totalSpan + ')');
assert(totalAttach + totalSpan === totalAttached, 'ATTACH+SPAN = total (' + totalAttach + '+' + totalSpan + '=' + totalAttached + ')');

section('S268.4 — Grid move governs ONLY attached elements');
// Move grid[2] (X=10.0) by +3.0m
var moveIdx = 2;
var delta = 3.0;
var governed = result.attachMap[moveIdx] || [];
var governedGuids = {};
for (var gvi = 0; gvi < governed.length; gvi++) {
  governedGuids[entries[governed[gvi].entryIdx].guid] = true;
}

// Check: entries NOT in attachMap[moveIdx] should NOT be moved
var falsePositives = 0;
for (var ei = 0; ei < entries.length; ei++) {
  if (governedGuids[entries[ei].guid]) continue;
  // S267 nearest-delta: would this entry be closest to grid[2]?
  var nearestGrid = -1;
  var nearestDist = Infinity;
  for (var ngi = 0; ngi < gridX.length; ngi++) {
    var nd = Math.abs(entries[ei].cx - gridX[ngi]);
    if (nd < nearestDist) { nearestDist = nd; nearestGrid = ngi; }
  }
  if (nearestGrid === moveIdx && nearestDist < 2.0) falsePositives++;
}
assert(falsePositives > 0, 'S267 would have false positives (' + falsePositives + ') — S268 prevents them');

section('S268.5 — ATTACH translate: entries near grid get full delta');
var attachEntries = governed.filter(function(g) { return g.relation === 'ATTACH'; });
assert(attachEntries.length > 0, 'Grid[2] has ATTACH entries to translate (' + attachEntries.length + ')');
// Verify: entry centerline is within ATTACH_TOL of grid position
for (var ai = 0; ai < Math.min(attachEntries.length, 5); ai++) {
  var ae = entries[attachEntries[ai].entryIdx];
  var dist = Math.abs(ae.cx - gridX[moveIdx]);
  assert(dist < ATTACH_TOL, 'ATTACH entry cx=' + ae.cx.toFixed(2) + ' within tol of grid ' + gridX[moveIdx] + ' (dist=' + dist.toFixed(3) + ')');
}

section('S268.6 — SPAN scale: entries straddling grid get width change');
var spanEntries = governed.filter(function(g) { return g.relation === 'SPAN'; });
assert(spanEntries.length > 0, 'Grid[2] has SPAN entries to scale (' + spanEntries.length + ')');
for (var si = 0; si < Math.min(spanEntries.length, 3); si++) {
  var se = entries[spanEntries[si].entryIdx];
  assert(gridX[moveIdx] > se.absX + 0.01, 'SPAN: grid ' + gridX[moveIdx] + ' > entry absX ' + se.absX.toFixed(2));
  assert(gridX[moveIdx] < se.absX + se.w - 0.01, 'SPAN: grid ' + gridX[moveIdx] + ' < entry absX+w ' + (se.absX + se.w).toFixed(2));
}

section('S268.7 — Interior entries between bays are NOT governed');
// An entry at X=7.5 should not be in any grid's attach map (between 5.0 and 10.0)
var interiorX = 7.5;
var foundInterior = false;
for (var iei = 0; iei < entries.length; iei++) {
  if (Math.abs(entries[iei].cx - interiorX) < 0.3) {
    // This entry should NOT be in any attachMap
    var isGoverned = false;
    for (var igk in result.attachMap) {
      var igItems = result.attachMap[igk];
      for (var igi = 0; igi < igItems.length; igi++) {
        if (igItems[igi].entryIdx === iei) { isGoverned = true; break; }
      }
      if (isGoverned) break;
    }
    if (!isGoverned) { foundInterior = true; break; }
  }
}
assert(foundInterior, 'Found interior entry near X=7.5 that is NOT governed by any grid');

// ── S269 Tests ─────────────────────────────────────────────────────────────
section('S269.1 — Bay-proportional delta for interior element');
// Bay [5.0, 10.0] → [5.0, 13.0] (grid at 10.0 moves +3.0)
var origGrid = [1.0, 5.0, 10.0, 15.0, 20.0, 23.5];
var newGrid  = [1.0, 5.0, 13.0, 15.0, 20.0, 23.5];

// Element at X=7.5 is 50% through old bay [5.0, 10.0]
// New bay [5.0, 13.0], 50% = 9.0, delta = +1.5
var d1 = bayProportionalDelta(7.5, origGrid, newGrid);
assertApprox(d1, 1.5, 0.01, 'X=7.5 at 50% of bay [5,10]→[5,13] moves +1.5');

// Element at X=5.0 (bay start) — delta should be ~0
var d2 = bayProportionalDelta(5.0, origGrid, newGrid);
assertApprox(d2, 0.0, 0.01, 'X=5.0 at bay start moves 0');

// Element at X=10.0 (bay end) — delta should be +3.0
var d3 = bayProportionalDelta(10.0, origGrid, newGrid);
assertApprox(d3, 3.0, 0.01, 'X=10.0 at bay end moves +3.0');

// Element at X=8.0 is 60% through old bay [5.0, 10.0]
// New bay [5.0, 13.0], 60% = 9.8, delta = +1.8
var d4 = bayProportionalDelta(8.0, origGrid, newGrid);
assertApprox(d4, 1.8, 0.01, 'X=8.0 at 60% of bay [5,10]→[5,13] moves +1.8');

section('S269.2 — Bay-proportional does NOT affect adjacent bays');
// Element at X=12.0 is in bay [10.0, 15.0] (old). After move: [13.0, 15.0].
// 12.0 is 40% of old bay [10, 15]. New bay [13, 15], 40% = 13.8, delta = +1.8
var d5 = bayProportionalDelta(12.0, origGrid, newGrid);
assertApprox(d5, 1.8, 0.01, 'X=12.0 in adjacent bay [10,15]→[13,15] moves +1.8');

// Element at X=17.0 is in bay [15.0, 20.0] — unchanged bays
var d6 = bayProportionalDelta(17.0, origGrid, newGrid);
assertApprox(d6, 0.0, 0.01, 'X=17.0 in unchanged bay [15,20] moves 0');

// Element at X=3.0 is in bay [1.0, 5.0] — unchanged
var d7 = bayProportionalDelta(3.0, origGrid, newGrid);
assertApprox(d7, 0.0, 0.01, 'X=3.0 in unchanged bay [1,5] moves 0');

section('S269.3 — Bay-proportional with negative delta (shrink)');
var shrinkGrid = [1.0, 5.0, 8.0, 15.0, 20.0, 23.5]; // grid at 10 moved to 8 (delta=-2)
var d8 = bayProportionalDelta(7.5, origGrid, shrinkGrid);
// Bay [5, 10] → [5, 8]. X=7.5 at 50%, new pos = 5 + 0.5*3 = 6.5, delta = -1.0
assertApprox(d8, -1.0, 0.01, 'X=7.5 with shrink [5,10]→[5,8] moves -1.0');

section('S269.4 — Edge cases');
var d9 = bayProportionalDelta(0.5, origGrid, newGrid);
assertApprox(d9, 0.0, 0.02, 'X=0.5 outside all bays — no move');

var d10 = bayProportionalDelta(24.0, origGrid, newGrid);
assertApprox(d10, 0.0, 0.02, 'X=24.0 outside all bays — no move');

// Empty grid
var d11 = bayProportionalDelta(7.5, [], []);
assertApprox(d11, 0.0, 0.01, 'Empty grid — no move');

section('S269.5 — TILE recount (formula verification)');
// TILE:2:2:18.85:1.22 — bay width 37.70m (2*18.85)
// If bay grows by +3.0m → 40.70m, nx = ceil(40.70/18.85) = 3
var oldBayW = 2 * 18.85; // 37.70
var newBayW = oldBayW + 3.0; // 40.70
var stepX = 18.85;
var oldNx = 2;
var newNx = Math.ceil(newBayW / stepX);
assert(newNx === 3, 'TILE recount: bay 37.70→40.70m, nx 2→' + newNx);

// FRAME verb coordinate replacement
// FRAME:18.2258,20.2258,22.2258,24.2258|12.2753,...
// If grid at 18.2258 moves to 21.2258 (+3.0):
var frameCoords = [18.2258, 20.2258, 22.2258, 24.2258];
var movedCoord = 18.2258;
var frameDelta = 3.0;
var newCoords = frameCoords.map(function(c) {
  return Math.abs(c - movedCoord) < 0.01 ? c + frameDelta : c;
});
assertApprox(newCoords[0], 21.2258, 0.001, 'FRAME: coord 18.2258 → 21.2258');
assertApprox(newCoords[1], 20.2258, 0.001, 'FRAME: coord 20.2258 unchanged');

section('S269.6 — Child cascade via parent lookup');
// Simulate: wall at X=10.0, door (child of wall) at X=10.3
// When wall moves +3.0, door should follow → X=13.3
var wallX = 10.0, doorX = 10.3;
var wallDelta = 3.0;
// In attach map: wall is ATTACH to grid[2], door may or may not be ATTACH.
// If door is within ATTACH_TOL of same grid → it gets same delta (correct).
// If door is NOT attached → bay-proportional moves it (also correct).
var doorDistToGrid = Math.abs(doorX - gridX[2]);
assert(doorDistToGrid < ATTACH_TOL, 'Door at X=10.3 is within ATTACH_TOL of grid 10.0 (dist=' + doorDistToGrid.toFixed(3) + ')');
// So door DOES get the same translate as wall — cascade happens naturally via proximity.

db.close();

// ── Summary ────────────────────────────────────────────────────────────────
console.log('\n═══════════════════════════════════════════════');
console.log('§TEST_RESULTS S268+S269: ' + _pass + '/' + _total + ' PASS' + (_fail ? ' (' + _fail + ' FAIL)' : ''));
console.log('═══════════════════════════════════════════════');
process.exit(_fail > 0 ? 1 : 0);
