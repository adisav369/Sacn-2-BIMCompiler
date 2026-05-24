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

console.log('\n═══════════════════════════════════════════════');
console.log('§COH_SUMMARY ' + _pass + ' passed, ' + _fail + ' failed, ' + (_pass + _fail) + ' total');
console.log('═══════════════════════════════════════════════');

if (_fail > 0) process.exit(1);
