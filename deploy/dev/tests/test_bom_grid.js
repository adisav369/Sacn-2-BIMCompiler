/**
 * test_bom_grid.js — §S272 BOM Engine Phase 2
 * Tests for bom_grid.js — GridLineManager
 * Issue: Prove level-scoped grids, shared keys, clamping, editable flags
 */
'use strict';

var BG = require('../bom_engine/bom_grid.js');
var BN = require('../bom_engine/bom_node.js');
var GridLineManager = BG.GridLineManager;
var BOMNode = BN.BOMNode;

var _pass = 0, _fail = 0;

function assert(cond, msg) {
  if (!cond) { _fail++; console.log('  FAIL: ' + msg); }
  else { _pass++; }
}

function assertEq(a, b, msg) {
  assert(a === b, msg + ' — got ' + String(a) + ', expected ' + String(b));
}

function assertClose(a, b, msg, tol) {
  tol = tol || 1;
  assert(Math.abs(a - b) < tol, msg + ' — got ' + a + ', expected ~' + b);
}

/** Create a BOMNode with grid properties set */
function makeGridNode(id, opts) {
  var n = new BOMNode({
    id: id,
    fillAxis: opts.axis || 'x',
    allocatedSize: opts.size || { w: 1000, d: 500, h: 2800 }
  });
  n._createsGrid   = opts.createsGrid !== false;
  n._gridEditable  = opts.editable !== false;
  n._gridSharedKey = opts.sharedKey || null;
  n._dragAxis      = opts.dragAxis || null;
  // Set currentAABB for position computation
  n.currentAABB = opts.aabb || { x: 0, y: 0, z: 0, w: 1000, d: 500, h: 2800 };
  return n;
}

// ── Basic add/get ──────────────────────────────────────────────────────────

console.log('§GRID basic add/get');

(function() {
  var mgr = new GridLineManager();

  var parent = new BOMNode({ id: 'floor' });
  parent.currentAABB = { x: 0, y: 0, z: 0, w: 8000, d: 6000, h: 3000 };

  var wall = makeGridNode('wall_n', {
    aabb: { x: 0, y: 0, z: 0, w: 8000, d: 200, h: 2800 }
  });
  parent.addChild(wall);

  var grids = mgr.addGridsForLevel([wall], 0);
  assertEq(grids.length, 1, 'add: 1 grid created');
  assertEq(grids[0].bomNodeId, 'wall_n', 'add: bomNodeId');
  assertEq(grids[0].level, 0, 'add: level = 0');
  assertEq(grids[0].axis, 'x', 'add: axis = x');
  assertClose(grids[0].position, 4000, 'add: position = center of wall');
  assert(grids[0].editable, 'add: editable by default');
})();

// ── Non-grid nodes skipped ─────────────────────────────────────────────────

console.log('§GRID non-grid nodes skipped');

(function() {
  var mgr = new GridLineManager();
  var noGrid = makeGridNode('chair', { createsGrid: false });
  var grids = mgr.addGridsForLevel([noGrid], 0);
  assertEq(grids.length, 0, 'skip: no grid for non-grid node');
})();

// ── Display-only (not editable) ────────────────────────────────────────────

console.log('§GRID display-only');

(function() {
  var mgr = new GridLineManager();

  var parent = new BOMNode({ id: 'floor' });
  parent.currentAABB = { x: 0, y: 0, z: 0, w: 8000, d: 6000, h: 3000 };

  var wall = makeGridNode('wall_s', {
    editable: false,
    aabb: { x: 0, y: 5800, z: 0, w: 8000, d: 200, h: 2800 }
  });
  parent.addChild(wall);

  mgr.addGridsForLevel([wall], 0);

  var editable = mgr.getEditableGrids(0);
  assertEq(editable.length, 0, 'display: 0 editable grids');

  var display = mgr.getDisplayGrids(0);
  assertEq(display.length, 1, 'display: 1 display grid');
  assert(!display[0].editable, 'display: grid is NOT editable');

  assert(!mgr.isEditable(display[0].id), 'isEditable: false');
})();

// ── Shared key ─────────────────────────────────────────────────────────────

console.log('§GRID shared key');

(function() {
  var mgr = new GridLineManager();

  var parent = new BOMNode({ id: 'wall' });
  parent.currentAABB = { x: 0, y: 0, z: 0, w: 6000, d: 200, h: 2800 };

  var win1 = makeGridNode('win_1', {
    sharedKey: 'WIN_TYPE_A',
    aabb: { x: 1000, y: 0, z: 900, w: 1200, d: 200, h: 900 }
  });
  var win2 = makeGridNode('win_2', {
    sharedKey: 'WIN_TYPE_A',
    aabb: { x: 3000, y: 0, z: 900, w: 1200, d: 200, h: 900 }
  });
  parent.addChild(win1);
  parent.addChild(win2);

  mgr.addGridsForLevel([win1, win2], 1);

  var group = mgr.getSharedGroup('WIN_TYPE_A');
  assertEq(group.length, 2, 'shared: 2 grids in group');

  // Set position on one → both move
  mgr.setPosition(group[0].id, 2000);
  assertClose(group[0].position, 2000, 'shared: grid[0] moved');
  assertClose(group[1].position, 2000, 'shared: grid[1] moved too');
})();

// ── Position clamping ──────────────────────────────────────────────────────

console.log('§GRID position clamping');

(function() {
  var mgr = new GridLineManager();

  var parent = new BOMNode({ id: 'room' });
  parent.currentAABB = { x: 100, y: 0, z: 0, w: 4000, d: 3000, h: 2800 };

  var partition = makeGridNode('partition', {
    aabb: { x: 2000, y: 0, z: 0, w: 200, d: 3000, h: 2800 }
  });
  parent.addChild(partition);

  var grids = mgr.addGridsForLevel([partition], 1);
  var g = grids[0];

  // minPos=100 (parent.x), maxPos=4100 (parent.x + parent.w)
  assertClose(g.minPos, 100, 'clamp: minPos = parent.x');
  assertClose(g.maxPos, 4100, 'clamp: maxPos = parent.x + parent.w');

  // Try to set beyond max
  mgr.setPosition(g.id, 9000);
  assertClose(g.position, 4100, 'clamp: clamped to maxPos');

  // Try to set below min
  mgr.setPosition(g.id, -500);
  assertClose(g.position, 100, 'clamp: clamped to minPos');

  // Normal position
  mgr.setPosition(g.id, 2500);
  assertClose(g.position, 2500, 'clamp: normal position accepted');
})();

// ── Non-editable setPosition is no-op ──────────────────────────────────────

console.log('§GRID non-editable setPosition');

(function() {
  var mgr = new GridLineManager();

  var parent = new BOMNode({ id: 'room' });
  parent.currentAABB = { x: 0, y: 0, z: 0, w: 6000, d: 4000, h: 2800 };

  var wall = makeGridNode('struct_wall', {
    editable: false,
    aabb: { x: 3000, y: 0, z: 0, w: 200, d: 4000, h: 2800 }
  });
  parent.addChild(wall);

  var grids = mgr.addGridsForLevel([wall], 0);
  var origPos = grids[0].position;

  mgr.setPosition(grids[0].id, 5000);
  assertClose(grids[0].position, origPos, 'non-editable: position unchanged');
})();

// ── Remove level ───────────────────────────────────────────────────────────

console.log('§GRID remove level');

(function() {
  var mgr = new GridLineManager();

  var parent = new BOMNode({ id: 'floor' });
  parent.currentAABB = { x: 0, y: 0, z: 0, w: 8000, d: 6000, h: 3000 };

  var w1 = makeGridNode('w1', { aabb: { x: 0, y: 0, z: 0, w: 200, d: 6000, h: 2800 } });
  var w2 = makeGridNode('w2', {
    sharedKey: 'SK1',
    aabb: { x: 4000, y: 0, z: 0, w: 200, d: 6000, h: 2800 }
  });
  parent.addChild(w1);
  parent.addChild(w2);

  mgr.addGridsForLevel([w1, w2], 0);
  assertEq(mgr.getDisplayGrids(0).length, 2, 'before remove: 2 grids');
  assertEq(mgr.getSharedGroup('SK1').length, 1, 'before remove: 1 in shared');

  mgr.removeGridsForLevel(0);
  assertEq(mgr.getDisplayGrids(0).length, 0, 'after remove: 0 grids');
  assertEq(mgr.getSharedGroup('SK1').length, 0, 'after remove: shared cleared');
  assertEq(mgr.getLevels().length, 0, 'after remove: 0 levels');
})();

// ── Multi-level ────────────────────────────────────────────────────────────

console.log('§GRID multi-level');

(function() {
  var mgr = new GridLineManager();

  var parent = new BOMNode({ id: 'bldg' });
  parent.currentAABB = { x: 0, y: 0, z: 0, w: 20000, d: 10000, h: 9000 };

  // Level 0 grid
  var n0 = makeGridNode('floor_grid', { aabb: { x: 0, y: 0, z: 0, w: 8000, d: 6000, h: 3000 } });
  parent.addChild(n0);
  mgr.addGridsForLevel([n0], 0);

  // Level 1 grids
  var n1a = makeGridNode('room_a', { aabb: { x: 0, y: 0, z: 0, w: 3000, d: 4000, h: 2800 } });
  var n1b = makeGridNode('room_b', { aabb: { x: 3500, y: 0, z: 0, w: 3000, d: 4000, h: 2800 } });
  n0.addChild(n1a);
  n0.addChild(n1b);
  mgr.addGridsForLevel([n1a, n1b], 1);

  var levels = mgr.getLevels();
  assertEq(levels.length, 2, 'multi: 2 levels');
  assertEq(levels[0], 0, 'multi: level 0');
  assertEq(levels[1], 1, 'multi: level 1');

  assertEq(mgr.getDisplayGrids(0).length, 1, 'multi: 1 grid at level 0');
  assertEq(mgr.getDisplayGrids(1).length, 2, 'multi: 2 grids at level 1');

  // Remove level 1 — level 0 stays
  mgr.removeGridsForLevel(1);
  assertEq(mgr.getDisplayGrids(0).length, 1, 'multi: level 0 intact');
  assertEq(mgr.getDisplayGrids(1).length, 0, 'multi: level 1 gone');
})();

// ── getAffectedBomNodeIds ──────────────────────────────────────────────────

console.log('§GRID getAffectedBomNodeIds');

(function() {
  var mgr = new GridLineManager();

  var parent = new BOMNode({ id: 'wall' });
  parent.currentAABB = { x: 0, y: 0, z: 0, w: 6000, d: 200, h: 2800 };

  var w1 = makeGridNode('win_a', {
    sharedKey: 'WIN_T1',
    aabb: { x: 1000, y: 0, z: 900, w: 1200, d: 200, h: 900 }
  });
  var w2 = makeGridNode('win_b', {
    sharedKey: 'WIN_T1',
    aabb: { x: 3000, y: 0, z: 900, w: 1200, d: 200, h: 900 }
  });
  parent.addChild(w1);
  parent.addChild(w2);

  var grids = mgr.addGridsForLevel([w1, w2], 0);
  var ids = mgr.getAffectedBomNodeIds(grids[0].id);
  assertEq(ids.length, 2, 'affected: 2 nodes via shared key');
  assert(ids.indexOf('win_a') >= 0, 'affected: win_a');
  assert(ids.indexOf('win_b') >= 0, 'affected: win_b');
})();

(function() {
  var mgr = new GridLineManager();
  var ids = mgr.getAffectedBomNodeIds('BOGUS');
  assertEq(ids.length, 0, 'affected: unknown grid → 0');
})();

// ── Summary ────────────────────────────────────────────────────────────────

console.log('\n§GRID_SUMMARY ' + _pass + ' passed, ' + _fail + ' failed, ' + (_pass + _fail) + ' total');
if (_fail > 0) process.exit(1);
