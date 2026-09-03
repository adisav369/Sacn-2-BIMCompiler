#!/usr/bin/env node
/**
 * test_coherence.js — S270e First-iteration coherence test
 * Issue: grid drag changes parent hostAABB but children don't redistribute.
 *        Structure breaks — gaps open, overlaps appear.
 *
 * Tests the single operation: parent envelope changes on one axis →
 * call recompose() → children adjust → gaps=0, overlaps=0.
 *
 * Uses REAL bom_engine code, synthetic data modelling a storey with walls,
 * windows, slab, and roof. No browser. No mocks. §-tagged logs.
 */
'use strict';

var BN = require('../bom_engine/bom_node.js');
var BomDiff = require('../bom_engine/bom_diff.js');
var BOMNode = BN.BOMNode;

var _pass = 0, _fail = 0;

function assert(cond, msg) {
  if (cond) { _pass++; console.log('  PASS  ' + msg); }
  else      { _fail++; console.log('  FAIL  ' + msg); }
}

function approx(a, b, tol, msg) {
  var ok = Math.abs(a - b) <= (tol || 1);
  assert(ok, msg + ' (expected ' + b + ', got ' + a + ')');
}

function section(name) { console.log('\n── ' + name + ' ──'); }

// ── Helper: sum children dims on axis, check no gap/overlap ──────────────

function dimOnAxis(aabb, axis) {
  if (axis === 'x') return aabb.w;
  if (axis === 'y') return aabb.d;
  return aabb.h;
}

function originOnAxis(aabb, axis) {
  if (axis === 'x') return aabb.x;
  if (axis === 'y') return aabb.y;
  return aabb.z;
}

function coherenceCheck(parent, axis) {
  var children = parent.getChildren();
  if (!children.length) return { gaps: 0, overlaps: 0, coverage: 1.0, details: [] };

  // Sort children by origin on axis
  var sorted = children.slice().sort(function(a, b) {
    return originOnAxis(a.currentAABB, axis) - originOnAxis(b.currentAABB, axis);
  });

  var parentDim = dimOnAxis(parent.currentAABB, axis);
  var parentOrigin = originOnAxis(parent.currentAABB, axis);
  var parentEnd = parentOrigin + parentDim;
  var sumDims = 0;
  var gaps = 0, overlaps = 0;
  var details = [];

  // Phantom absorbs remainder — it's a single contiguous block of free space
  var phantomDim = parent.phantom ? dimOnAxis(parent.phantom, axis) : 0;

  for (var i = 0; i < sorted.length; i++) {
    var child = sorted[i];
    sumDims += dimOnAxis(child.currentAABB, axis);

    if (i > 0) {
      var prevEnd = originOnAxis(sorted[i-1].currentAABB, axis) +
                    dimOnAxis(sorted[i-1].currentAABB, axis);
      var curStart = originOnAxis(child.currentAABB, axis);
      var delta = curStart - prevEnd;

      if (delta > 5) {  // 5mm tolerance
        // Is this gap the phantom space? Phantom fills exactly one gap.
        if (Math.abs(delta - phantomDim) <= 5 && phantomDim > 0) {
          phantomDim = 0; // consumed — phantom accounts for this gap
        } else {
          gaps++;
          details.push('GAP ' + sorted[i-1].id + '↔' + child.id + ' gap=' + delta.toFixed(0) + 'mm');
        }
      } else if (delta < -5) {
        overlaps++;
        details.push('OVERLAP ' + sorted[i-1].id + '↔' + child.id + ' overlap=' + (-delta).toFixed(0) + 'mm');
      }
    }
  }

  // Coverage: children + phantom should fill parent
  var totalPhantom = parent.phantom ? dimOnAxis(parent.phantom, axis) : 0;
  var coverage = (sumDims + totalPhantom) / parentDim;

  return { gaps: gaps, overlaps: overlaps, coverage: coverage, details: details, sumDims: sumDims, phantomDim: totalPhantom };
}

// ═══════════════════════════════════════════════════════════════════════════
// SCENARIO A: Storey with walls on X axis
// Models SC_GF_STR — ground floor with left wall, right wall, windows between
// ═══════════════════════════════════════════════════════════════════════════

section('A: Storey envelope — walls + windows on X');

// Parent: storey envelope, 10m wide, 6m deep, 3m high
var storey = new BOMNode({
  id: 'GF_STR',
  strategy: 'UNIFORM',
  fillAxis: 'x'
});

// Left wall — mandatory, FIXED at x=0, 200mm thick
var wallL = new BOMNode({
  id: 'WALL_L', strategy: 'FIXED', mandatory: true,
  tack: { dx: 0, dy: 0, dz: 0 },
  allocatedSize: { w: 200, d: 6000, h: 3000 },
  fitPriority: 1
});

// Right wall — mandatory, FIXED at right edge, 200mm thick
var wallR = new BOMNode({
  id: 'WALL_R', strategy: 'FIXED', mandatory: true,
  tack: { dx: 9800, dy: 0, dz: 0 },
  allocatedSize: { w: 200, d: 6000, h: 3000 },
  fitPriority: 1
});

// Windows — optional, UNIFORM, 1200mm each
var win1 = new BOMNode({
  id: 'WIN_1', strategy: 'UNIFORM',
  childSize: 1200,
  allocatedSize: { w: 1200, d: 100, h: 1500 }
});
var win2 = new BOMNode({
  id: 'WIN_2', strategy: 'UNIFORM',
  childSize: 1200,
  allocatedSize: { w: 1200, d: 100, h: 1500 }
});

// Floor slab — SPAN stretches to fill (no allocatedSize — SPAN computes it)
var slab = new BOMNode({
  id: 'SLAB', strategy: 'SPAN',
  childSize: 200
});

storey.addChild(wallL);
storey.addChild(wallR);
storey.addChild(win1);
storey.addChild(win2);

// A1: Initial recompose at original size
var hostOriginal = { x: 0, y: 0, z: 0, w: 10000, d: 6000, h: 3000 };
var r1 = storey.recompose(hostOriginal);

console.log('§COH_A1 initial recompose:');
var ch1 = coherenceCheck(storey, 'x');
console.log('  §COHERENCE_COV parent=GF_STR coverage=' + ch1.coverage.toFixed(3) +
  ' children=' + storey.getChildren().length +
  ' phantom.w=' + ch1.phantomDim.toFixed(0) +
  ' gaps=' + ch1.gaps + ' overlaps=' + ch1.overlaps);

assert(ch1.gaps === 0, 'A1: no gaps at original size');
assert(ch1.overlaps === 0, 'A1: no overlaps at original size');
assert(r1.conflicts.length === 0, 'A1: no conflicts');

// Snapshot before
var beforeState = storey.getChildren().map(function(c) {
  return { id: c.id, x: c.currentAABB.x, w: c.currentAABB.w };
});
console.log('  before:', JSON.stringify(beforeState));

// A2: Grid drag — parent grows 2m on X (10m → 12m)
// wallR has _anchorFace or tack at right edge — should auto-follow
section('A2: Drag +2m on X (grow)');
var hostGrown = { x: 0, y: 0, z: 0, w: 12000, d: 6000, h: 3000 };

var r2 = storey.recompose(hostGrown);
var ch2 = coherenceCheck(storey, 'x');

console.log('§COH_A2 after +2m drag:');
console.log('  §COHERENCE_COV parent=GF_STR coverage=' + ch2.coverage.toFixed(3) +
  ' children=' + storey.getChildren().length +
  ' phantom.w=' + ch2.phantomDim.toFixed(0) +
  ' gaps=' + ch2.gaps + ' overlaps=' + ch2.overlaps);

var afterGrow = storey.getChildren().map(function(c) {
  return { id: c.id, x: c.currentAABB.x, w: c.currentAABB.w };
});
console.log('  after:', JSON.stringify(afterGrow));

// Diff
var diff2 = BomDiff.diff(
  beforeState.map(function(s) { return { id: s.id, x: s.x, y: 0, z: 0, w: s.w, d: 6000, h: 3000 }; }),
  afterGrow.map(function(s) { return { id: s.id, x: s.x, y: 0, z: 0, w: s.w, d: 6000, h: 3000 }; })
);
var diffCounts = { MOVE: 0, SCALE: 0, ADD: 0, REMOVE: 0, KEEP: 0 };
diff2.forEach(function(c) { diffCounts[c.type] = (diffCounts[c.type] || 0) + 1; });
console.log('  §BOM_DIFF ' + JSON.stringify(diffCounts));

assert(ch2.gaps === 0, 'A2: no gaps after grow');
assert(ch2.overlaps === 0, 'A2: no overlaps after grow');
approx(wallL.currentAABB.w, 200, 1, 'A2: left wall stays 200mm thick');
approx(wallR.currentAABB.x, 11800, 1, 'A2: right wall at new edge');
approx(wallR.currentAABB.w, 200, 1, 'A2: right wall stays 200mm thick');

// A3: Grid drag — parent shrinks 3m on X (10m → 7m)
// wallR auto-follows right edge
section('A3: Drag -3m on X (shrink)');
var hostShrunk = { x: 0, y: 0, z: 0, w: 7000, d: 6000, h: 3000 };

var r3 = storey.recompose(hostShrunk);
var ch3 = coherenceCheck(storey, 'x');

console.log('§COH_A3 after -3m drag:');
console.log('  §COHERENCE_COV parent=GF_STR coverage=' + ch3.coverage.toFixed(3) +
  ' children=' + storey.getChildren().length +
  ' phantom.w=' + ch3.phantomDim.toFixed(0) +
  ' gaps=' + ch3.gaps + ' overlaps=' + ch3.overlaps);

var afterShrink = storey.getChildren().map(function(c) {
  return { id: c.id, x: c.currentAABB.x, w: c.currentAABB.w };
});
console.log('  after:', JSON.stringify(afterShrink));

assert(ch3.gaps === 0, 'A3: no gaps after shrink');
assert(ch3.overlaps === 0, 'A3: no overlaps after shrink');
assert(wallL.currentAABB, 'A3: mandatory left wall still present');
assert(wallR.currentAABB, 'A3: mandatory right wall still present');
approx(wallR.currentAABB.x, 6800, 1, 'A3: right wall auto-follows to x=6800');

// ═══════════════════════════════════════════════════════════════════════════
// SCENARIO B: SPAN child (slab) stretches on X
// ═══════════════════════════════════════════════════════════════════════════

section('B: SPAN child stretches with parent');

var room = new BOMNode({
  id: 'ROOM_A', strategy: 'SPAN', fillAxis: 'x'
});
var floorSlab = new BOMNode({
  id: 'FLOOR_SLAB', strategy: 'SPAN',
  childSize: 200
});
room.addChild(floorSlab);

// B1: Original — SPAN child stretches to fill host on fill axis (x)
room.recompose({ x: 0, y: 0, z: 0, w: 5000, d: 4000, h: 3000 });
console.log('  §COH_B1 slab.w=' + floorSlab.currentAABB.w);
approx(floorSlab.currentAABB.w, 5000, 1, 'B1: slab spans full 5m');

// B2: Grow to 8m — slab stretches
room.recompose({ x: 0, y: 0, z: 0, w: 8000, d: 4000, h: 3000 });
approx(floorSlab.currentAABB.w, 8000, 1, 'B2: slab stretches to 8m');

// B3: Shrink to 3m — slab shrinks
room.recompose({ x: 0, y: 0, z: 0, w: 3000, d: 4000, h: 3000 });
approx(floorSlab.currentAABB.w, 3000, 1, 'B3: slab shrinks to 3m');

// ═══════════════════════════════════════════════════════════════════════════
// SCENARIO C: Z axis — roof height change
// Wall height follows roof. Thickness untouched.
// ═══════════════════════════════════════════════════════════════════════════

section('C: Z-axis — roof height affects wall height');

var storeyZ = new BOMNode({
  id: 'GF_Z', strategy: 'SPAN', fillAxis: 'z'
});
var wallZ = new BOMNode({
  id: 'WALL_Z', strategy: 'SPAN',
  childSize: 200
});
storeyZ.addChild(wallZ);

// C1: Original 3m height — fillAxis=z, SPAN stretches height
storeyZ.recompose({ x: 0, y: 0, z: 0, w: 10000, d: 6000, h: 3000 });
console.log('  §COH_C1 wall.h=' + wallZ.currentAABB.h + ' wall.w=' + wallZ.currentAABB.w);
approx(wallZ.currentAABB.h, 3000, 1, 'C1: wall spans 3m height');
approx(wallZ.currentAABB.w, 200, 1, 'C1: wall thickness unchanged (200mm)');

// C2: Roof raised to 4m — wall stretches height
storeyZ.recompose({ x: 0, y: 0, z: 0, w: 10000, d: 6000, h: 4000 });
approx(wallZ.currentAABB.h, 4000, 1, 'C2: wall stretches to 4m');
approx(wallZ.currentAABB.w, 200, 1, 'C2: wall thickness still 200mm');

// C3: Roof lowered to 2.5m — wall shrinks height
storeyZ.recompose({ x: 0, y: 0, z: 0, w: 10000, d: 6000, h: 2500 });
approx(wallZ.currentAABB.h, 2500, 1, 'C3: wall shrinks to 2.5m');

// ═══════════════════════════════════════════════════════════════════════════
// SCENARIO D: Y axis — depth change
// Back wall follows depth. Side walls stretch depth.
// ═══════════════════════════════════════════════════════════════════════════

section('D: Y-axis — depth change');

var storeyY = new BOMNode({
  id: 'GF_Y', strategy: 'SPAN', fillAxis: 'y'
});
var wallBack = new BOMNode({
  id: 'WALL_BACK', strategy: 'SPAN',
  childSize: 200
});
storeyY.addChild(wallBack);

// D1: Original 6m depth — SPAN stretches on Y (depth)
storeyY.recompose({ x: 0, y: 0, z: 0, w: 10000, d: 6000, h: 3000 });
console.log('  §COH_D1 wallBack.d=' + wallBack.currentAABB.d);
approx(wallBack.currentAABB.d, 6000, 1, 'D1: back wall spans 6m depth');

// D2: Grow to 8m depth — wall stretches
storeyY.recompose({ x: 0, y: 0, z: 0, w: 10000, d: 8000, h: 3000 });
approx(wallBack.currentAABB.d, 8000, 1, 'D2: back wall stretches to 8m depth');

// ═══════════════════════════════════════════════════════════════════════════
// SCENARIO E: UNIFORM recount — windows fill available space
// Parent grows → more windows fit. Parent shrinks → windows removed.
// ═══════════════════════════════════════════════════════════════════════════

section('E: UNIFORM recount — windows');

var wallHost = new BOMNode({
  id: 'WALL_HOST', strategy: 'UNIFORM', fillAxis: 'x',
  spacing: 1500
});
// 4 windows, 1200mm each, 1500mm spacing
for (var wi = 0; wi < 4; wi++) {
  wallHost.addChild(new BOMNode({
    id: 'WIN_' + wi, strategy: 'UNIFORM',
    childSize: 1200,
    allocatedSize: { w: 1200, d: 100, h: 1500 }
  }));
}

// E1: Wall 8m — windows at 1500mm spacing
wallHost.recompose({ x: 0, y: 0, z: 0, w: 8000, d: 200, h: 3000 });
var filledE1 = wallHost.getChildren().filter(function(c) { return c.currentAABB; });
var chE1 = coherenceCheck(wallHost, 'x');
console.log('§COH_E1 8m wall: filled=' + filledE1.length +
  ' coverage=' + chE1.coverage.toFixed(3) +
  ' gaps=' + chE1.gaps + ' overlaps=' + chE1.overlaps);
assert(filledE1.length > 0, 'E1: at least one window placed');
assert(chE1.overlaps === 0, 'E1: no overlaps');

// E2: Wall grows to 15m — more windows should fit
wallHost.recompose({ x: 0, y: 0, z: 0, w: 15000, d: 200, h: 3000 });
var filledE2 = wallHost.getChildren().filter(function(c) { return c.currentAABB; });
var chE2 = coherenceCheck(wallHost, 'x');
console.log('§COH_E2 15m wall: filled=' + filledE2.length +
  ' coverage=' + chE2.coverage.toFixed(3) +
  ' gaps=' + chE2.gaps + ' overlaps=' + chE2.overlaps);
assert(chE2.overlaps === 0, 'E2: no overlaps in larger wall');

// E3: Wall shrinks to 3m — some windows removed
wallHost.recompose({ x: 0, y: 0, z: 0, w: 3000, d: 200, h: 3000 });
var filledE3 = wallHost.getChildren().filter(function(c) { return c.currentAABB; });
var chE3 = coherenceCheck(wallHost, 'x');
console.log('§COH_E3 3m wall: filled=' + filledE3.length +
  ' coverage=' + chE3.coverage.toFixed(3) +
  ' gaps=' + chE3.gaps + ' overlaps=' + chE3.overlaps);
assert(chE3.overlaps === 0, 'E3: no overlaps in smaller wall');

// ═══════════════════════════════════════════════════════════════════════════
// SCENARIO F: FIXED child — proportional repositioning on drag
// Door stays at proportional position when wall stretches.
// ═══════════════════════════════════════════════════════════════════════════

section('F: FIXED child — proportional position');

var wallFixed = new BOMNode({
  id: 'WALL_F', strategy: 'FIXED', fillAxis: 'x'
});
var door = new BOMNode({
  id: 'DOOR_1', strategy: 'FIXED', mandatory: true,
  tack: { dx: 3000, dy: 0, dz: 0 },
  allocatedSize: { w: 900, d: 200, h: 2100 },
  fitPriority: 1
});
wallFixed.addChild(door);

// F1: Door at x=3m in 10m wall
wallFixed.recompose({ x: 0, y: 0, z: 0, w: 10000, d: 200, h: 3000 });
approx(door.currentAABB.x, 3000, 1, 'F1: door at x=3m');
approx(door.currentAABB.w, 900, 1, 'F1: door width 900mm');

// F2: Wall grows to 14m — door mandatory, stays at tack
wallFixed.recompose({ x: 0, y: 0, z: 0, w: 14000, d: 200, h: 3000 });
approx(door.currentAABB.x, 3000, 1, 'F2: mandatory door stays at tack x=3m');
approx(door.currentAABB.w, 900, 1, 'F2: door width unchanged');

// ═══════════════════════════════════════════════════════════════════════════
// SCENARIO G: Idempotency — recompose twice = same result
// ═══════════════════════════════════════════════════════════════════════════

section('G: Idempotency — recompose×2 = same');

var storeyG = new BOMNode({
  id: 'GF_G', strategy: 'UNIFORM', fillAxis: 'x',
  spacing: 2000
});
storeyG.addChild(new BOMNode({
  id: 'WALL_G_L', strategy: 'FIXED', mandatory: true,
  tack: { dx: 0, dy: 0, dz: 0 },
  allocatedSize: { w: 200, d: 6000, h: 3000 },
  fitPriority: 1
}));
storeyG.addChild(new BOMNode({
  id: 'WIN_G', strategy: 'UNIFORM',
  childSize: 1200,
  allocatedSize: { w: 1200, d: 100, h: 1500 }
}));

var hostG = { x: 0, y: 0, z: 0, w: 10000, d: 6000, h: 3000 };

// First recompose
storeyG.recompose(hostG);
var snap1 = storeyG.snapshot();

// Second recompose — same host
storeyG.recompose(hostG);
var snap2 = storeyG.snapshot();

var s1 = JSON.stringify(snap1);
var s2 = JSON.stringify(snap2);
assert(s1 === s2, 'G1: recompose×2 produces identical snapshot');

// Diff should be all KEEP
var state1 = storeyG.getChildren().map(function(c) {
  return { id: c.id, x: c.currentAABB.x, y: c.currentAABB.y, z: c.currentAABB.z,
           w: c.currentAABB.w, d: c.currentAABB.d, h: c.currentAABB.h };
});
var diffG = BomDiff.diff(state1, state1);
var nonKeep = diffG.filter(function(c) { return c.type !== 'KEEP'; });
assert(nonKeep.length === 0, 'G2: diff of identical state = all KEEP (' + diffG.length + ' entries)');

// ═══════════════════════════════════════════════════════════════════════════
// SCENARIO H: Mandatory survives shrink, optional removed
// ═══════════════════════════════════════════════════════════════════════════

section('H: Mandatory survives, optional removed on shrink');

var wallH = new BOMNode({
  id: 'WALL_H', strategy: 'UNIFORM', fillAxis: 'x',
  spacing: 1500
});
var doorH = new BOMNode({
  id: 'DOOR_H', strategy: 'FIXED', mandatory: true,
  tack: { dx: 500, dy: 0, dz: 0 },
  allocatedSize: { w: 900, d: 200, h: 2100 },
  fitPriority: 1
});
var winH = new BOMNode({
  id: 'WIN_H', strategy: 'UNIFORM',
  childSize: 1200,
  allocatedSize: { w: 1200, d: 100, h: 1500 }
});

wallH.addChild(doorH);
wallH.addChild(winH);

// H1: 5m wall — both fit
wallH.recompose({ x: 0, y: 0, z: 0, w: 5000, d: 200, h: 3000 });
assert(doorH.currentAABB !== null, 'H1: mandatory door placed');
console.log('§COH_H1 door.x=' + doorH.currentAABB.x + ' win placed=' + (winH.currentAABB !== null));

// H2: Shrink to 1000mm — only mandatory fits, window should not overlap
wallH.recompose({ x: 0, y: 0, z: 0, w: 1000, d: 200, h: 3000 });
assert(doorH.currentAABB !== null, 'H2: mandatory door still present after extreme shrink');
var chH2 = coherenceCheck(wallH, 'x');
console.log('§COH_H2 1000mm wall: gaps=' + chH2.gaps + ' overlaps=' + chH2.overlaps +
  ' coverage=' + chH2.coverage.toFixed(3));
assert(chH2.overlaps === 0, 'H2: no overlaps after shrink');

// ═══════════════════════════════════════════════════════════════════════════
// SCENARIO I: Full wall with openings — metadata-driven resize
// Wall=SPAN (infill), Door=FIXED/mandatory/max1, Windows=UNIFORM/VARIABLE
// Wall grows → windows may ADD, door stays, wall infills between them
// ═══════════════════════════════════════════════════════════════════════════

section('I: Metadata-driven wall resize — door stays, windows add, wall infills');

// Wall is SPAN parent — stretches on X. Its children are the openings.
var wallI = new BOMNode({
  id: 'WALL_EXT', strategy: 'UNIFORM', fillAxis: 'x',
  spacing: 2000
});

// Door: FIXED, mandatory=1, max_count=1, qty_type=FIXED → never moves, exactly 1
var doorI = new BOMNode({
  id: 'DOOR_ENTRY', strategy: 'FIXED', mandatory: true,
  tack: { dx: 1000, dy: 0, dz: 0 },
  allocatedSize: { w: 900, d: 200, h: 2100 },
  fitPriority: 1,
  // These metadata props control: singular, never resized
  maxCount: 1
});

// Windows: UNIFORM, mandatory=0, qty_type=VARIABLE → count adjusts to fill
var winI1 = new BOMNode({
  id: 'WIN_I1', strategy: 'UNIFORM',
  childSize: 1200,
  allocatedSize: { w: 1200, d: 100, h: 1500 }
});
var winI2 = new BOMNode({
  id: 'WIN_I2', strategy: 'UNIFORM',
  childSize: 1200,
  allocatedSize: { w: 1200, d: 100, h: 1500 }
});
var winI3 = new BOMNode({
  id: 'WIN_I3', strategy: 'UNIFORM',
  childSize: 1200,
  allocatedSize: { w: 1200, d: 100, h: 1500 }
});

wallI.addChild(doorI);
wallI.addChild(winI1);
wallI.addChild(winI2);
wallI.addChild(winI3);

// I1: Original 8m wall
wallI.recompose({ x: 0, y: 0, z: 0, w: 8000, d: 200, h: 3000 });

// Door stays at tack
approx(doorI.currentAABB.x, 1000, 1, 'I1: door at tack x=1000');
approx(doorI.currentAABB.w, 900, 1, 'I1: door width=900 (never changes)');
approx(doorI.currentAABB.h, 2100, 1, 'I1: door height=2100 (never changes)');

// Windows keep their size
var winsI1 = [winI1, winI2, winI3].filter(function(w) { return w.currentAABB; });
console.log('§COH_I1 8m wall: door.x=' + doorI.currentAABB.x + ' door.w=' + doorI.currentAABB.w +
  ' windows=' + winsI1.length);
for (var wi1 = 0; wi1 < winsI1.length; wi1++) {
  approx(winsI1[wi1].currentAABB.w, 1200, 1, 'I1: window ' + winsI1[wi1].id + ' width=1200');
}

// I2: Wall grows to 14m — more space, windows may add but door stays
wallI.recompose({ x: 0, y: 0, z: 0, w: 14000, d: 200, h: 3000 });

approx(doorI.currentAABB.x, 1000, 1, 'I2: door still at x=1000 after grow');
approx(doorI.currentAABB.w, 900, 1, 'I2: door still 900mm after grow');

var winsI2 = [winI1, winI2, winI3].filter(function(w) { return w.currentAABB; });
console.log('§COH_I2 14m wall: door.x=' + doorI.currentAABB.x +
  ' windows=' + winsI2.length);
for (var wi2 = 0; wi2 < winsI2.length; wi2++) {
  approx(winsI2[wi2].currentAABB.w, 1200, 1, 'I2: window ' + winsI2[wi2].id + ' still 1200mm (never resized)');
}

// I3: Wall shrinks to 4m — door must stay (mandatory), some windows may not fit
wallI.recompose({ x: 0, y: 0, z: 0, w: 4000, d: 200, h: 3000 });

assert(doorI.currentAABB !== null, 'I3: mandatory door survives shrink');
approx(doorI.currentAABB.w, 900, 1, 'I3: door still 900mm');

var winsI3 = [winI1, winI2, winI3].filter(function(w) { return w.currentAABB; });
console.log('§COH_I3 4m wall: door.x=' + doorI.currentAABB.x +
  ' windows=' + winsI3.length);

// Column: allocatedSize is never mutated — check the original objects
approx(winI1.allocatedSize.w, 1200, 1, 'I3: WIN_I1.allocatedSize unchanged');
approx(doorI.allocatedSize.w, 900, 1, 'I3: DOOR_ENTRY.allocatedSize unchanged');
approx(doorI.allocatedSize.h, 2100, 1, 'I3: DOOR_ENTRY.allocatedSize.h unchanged');

// ═══════════════════════════════════════════════════════════════════════════
// SCENARIO J: Column — FIXED, mandatory, never moves on any resize
// ═══════════════════════════════════════════════════════════════════════════

section('J: Column — FIXED mandatory, never moves');

var frameJ = new BOMNode({
  id: 'FRAME_J', strategy: 'UNIFORM', fillAxis: 'x', spacing: 5000
});
var col1 = new BOMNode({
  id: 'COL_A', strategy: 'FIXED', mandatory: true,
  tack: { dx: 0, dy: 0, dz: 0 },
  allocatedSize: { w: 300, d: 300, h: 3000 },
  fitPriority: 1
});
var col2 = new BOMNode({
  id: 'COL_B', strategy: 'FIXED', mandatory: true,
  tack: { dx: 5000, dy: 0, dz: 0 },
  allocatedSize: { w: 300, d: 300, h: 3000 },
  fitPriority: 1
});
frameJ.addChild(col1);
frameJ.addChild(col2);

// J1: Original 10m frame
frameJ.recompose({ x: 0, y: 0, z: 0, w: 10000, d: 6000, h: 3000 });
approx(col1.currentAABB.x, 0, 1, 'J1: column A at x=0');
approx(col2.currentAABB.x, 5000, 1, 'J1: column B at x=5000');
approx(col1.currentAABB.w, 300, 1, 'J1: column A 300mm (never changes)');
approx(col1.currentAABB.d, 300, 1, 'J1: column A depth 300mm');
approx(col1.currentAABB.h, 3000, 1, 'J1: column A height 3000mm');

// J2: Frame grows to 15m — columns stay at tack
frameJ.recompose({ x: 0, y: 0, z: 0, w: 15000, d: 6000, h: 3000 });
approx(col1.currentAABB.x, 0, 1, 'J2: column A still x=0');
approx(col2.currentAABB.x, 5000, 1, 'J2: column B still x=5000');
approx(col1.currentAABB.w, 300, 1, 'J2: column A still 300mm');

// J3: Frame shrinks to 6m — both columns still present (mandatory)
frameJ.recompose({ x: 0, y: 0, z: 0, w: 6000, d: 6000, h: 3000 });
assert(col1.currentAABB !== null, 'J3: column A survives shrink');
assert(col2.currentAABB !== null, 'J3: column B survives shrink');
approx(col1.currentAABB.w, 300, 1, 'J3: column A still 300mm');

// allocatedSize never mutated
approx(col1.allocatedSize.w, 300, 1, 'J3: COL_A.allocatedSize.w unchanged');
approx(col1.allocatedSize.h, 3000, 1, 'J3: COL_A.allocatedSize.h unchanged');

console.log('§COH_J columns: A.x=' + col1.currentAABB.x + ' B.x=' + col2.currentAABB.x +
  ' A.w=' + col1.currentAABB.w + ' A.h=' + col1.currentAABB.h);

// ═══════════════════════════════════════════════════════════════════════════
// SCENARIO K: Slab — SPAN on X and Y, height fixed
// ═══════════════════════════════════════════════════════════════════════════

section('K: Slab — SPAN stretches length+depth, height fixed');

var storeyK = new BOMNode({
  id: 'GF_K', strategy: 'SPAN', fillAxis: 'x'
});
var slabK = new BOMNode({
  id: 'SLAB_K', strategy: 'SPAN',
  childSize: 200  // thickness
});
storeyK.addChild(slabK);

// K1: Original 10m × 6m
storeyK.recompose({ x: 0, y: 0, z: 0, w: 10000, d: 6000, h: 200 });
approx(slabK.currentAABB.w, 10000, 1, 'K1: slab spans 10m on X');
approx(slabK.currentAABB.h, 200, 1, 'K1: slab height=200mm (thickness, fixed by childSize)');

// K2: Grows to 14m × 8m
storeyK.recompose({ x: 0, y: 0, z: 0, w: 14000, d: 8000, h: 200 });
approx(slabK.currentAABB.w, 14000, 1, 'K2: slab stretches to 14m');
approx(slabK.currentAABB.h, 200, 1, 'K2: slab thickness still 200mm');

// allocatedSize is null for SPAN — it uses stratResult.size
assert(slabK.allocatedSize === null, 'K2: SPAN slab has no allocatedSize (engine computes it)');

// ═══════════════════════════════════════════════════════════════════════════

console.log('\n═══════════════════════════════════════════════');
console.log('§COH_SUMMARY ' + _pass + ' passed, ' + _fail + ' failed, ' + (_pass + _fail) + ' total');
console.log('═══════════════════════════════════════════════');

if (_fail > 0) process.exit(1);
