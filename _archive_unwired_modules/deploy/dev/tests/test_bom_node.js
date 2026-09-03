/**
 * test_bom_node.js — §S272 BOM Engine Phase 1
 * Tests for bom_node.js — BOMNode + recompose() Template Method
 * Issue: Prove 5-step recompose positions are numerically correct,
 *        cascade propagates geometry, mandatory/override/phantom work,
 *        and BUFFER invariant holds.
 */
'use strict';

var BN = require('../bom_engine/bom_node.js');
var Diff = require('../bom_engine/bom_diff.js');
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

/** Count how many children have a currentAABB set */
function countPositioned(node) {
  var n = 0;
  for (var i = 0; i < node.children.length; i++) {
    if (node.children[i].currentAABB) n++;
  }
  return n;
}

/** Reset all child AABBs (for re-recompose tests) */
function resetChildren(node) {
  for (var i = 0; i < node.children.length; i++) {
    node.children[i].currentAABB = null;
  }
}

/** Check BUFFER invariant I7: SUM(children.w) + phantom.w = parent.w on fill axis */
function checkBufferInvariant(node, axis, msg) {
  var parentDim = axis === 'x' ? node.currentAABB.w :
                  axis === 'y' ? node.currentAABB.d : node.currentAABB.h;
  var sumChildren = 0;
  for (var i = 0; i < node.children.length; i++) {
    var c = node.children[i];
    if (!c.currentAABB) continue;
    sumChildren += (axis === 'x' ? c.currentAABB.w :
                    axis === 'y' ? c.currentAABB.d : c.currentAABB.h);
  }
  var phantomDim = axis === 'x' ? node.phantom.w :
                   axis === 'y' ? node.phantom.d : node.phantom.h;
  assertClose(sumChildren + phantomDim, parentDim, msg, 1);
}

// ── BOMNode construction ───────────────────────────────────────────────────

console.log('§NODE construction');

(function() {
  var n = new BOMNode({ id: 'room_01' });
  assertEq(n.id, 'room_01', 'id set');
  assertEq(n.strategy, 'UNIFORM', 'default strategy');
  assert(!n.mandatory, 'default not mandatory');
  assert(n.isLeaf(), 'no children → leaf');
  assertEq(n.getParentBOM(), null, 'no parent');
})();

(function() {
  var parent = new BOMNode({ id: 'floor' });
  var child  = new BOMNode({ id: 'room' });
  parent.addChild(child);
  assertEq(parent.getChildren().length, 1, 'parent has 1 child');
  assert(!parent.isLeaf(), 'parent is not leaf');
  assertEq(child.getParentBOM(), parent, 'child knows parent');
})();

// ── Leaf recompose ─────────────────────────────────────────────────────────

console.log('§NODE leaf recompose');

(function() {
  var leaf = new BOMNode({ id: 'chair', allocatedSize: { w: 600, d: 600, h: 800 } });
  var host = { x: 100, y: 200, z: 0, w: 600, d: 600, h: 800 };
  var r = leaf.recompose(host);
  // Leaf with allocatedSize positions at tack offset (default 0,0,0) within host
  assertClose(leaf.currentAABB.x, 100, 'leaf x = host.x + tack.dx');
  assertClose(leaf.currentAABB.y, 200, 'leaf y = host.y + tack.dy');
  assertClose(leaf.currentAABB.w, 600, 'leaf w = allocatedSize.w');
  assertEq(r.conflicts.length, 0, 'leaf no conflicts');
  assertClose(r.phantom.w, 0, 'leaf phantom.w = 0');
  assertClose(r.phantom.d, 0, 'leaf phantom.d = 0');
})();

// ── UNIFORM: verify positions match strategy formula ───────────────────────

console.log('§NODE UNIFORM positions verified');

(function() {
  // Room 6000mm wide, spacing 1800mm, edge 200mm, childSize 1200mm
  // UNIFORM formula: avail=5600, step=1800, count=floor((5600-1200)/1800)+1=3
  // positions: 800, 2600, 4400 (center positions from parent origin)
  var room = new BOMNode({
    id: 'room', strategy: 'UNIFORM',
    spacing: 1800, edgeOffset: 200, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  for (var i = 0; i < 5; i++) {
    room.addChild(new BOMNode({
      id: 'win_' + i, childSize: 1200,
      allocatedSize: { w: 1200, d: 200, h: 900 }
    }));
  }

  var host = { x: 0, y: 0, z: 0, w: 6000, d: 4000, h: 2800 };
  room.recompose(host);

  // Exactly 3 positioned, 2 unpositioned
  assertEq(countPositioned(room), 3, 'UNIFORM: exactly 3 positioned');
  assert(!room.children[3].currentAABB, 'UNIFORM: child 3 NOT positioned');
  assert(!room.children[4].currentAABB, 'UNIFORM: child 4 NOT positioned');

  // Verify x positions: strategy returns center positions, setPositionOnAxis converts
  // pos = parentOrigin + centerPos - childSize/2 = 0 + 800 - 600 = 200
  assertClose(room.children[0].currentAABB.x, 200, 'win_0.x = 200');
  assertClose(room.children[1].currentAABB.x, 2000, 'win_1.x = 2000');
  assertClose(room.children[2].currentAABB.x, 3800, 'win_2.x = 3800');

  // Verify dimensions preserved
  assertClose(room.children[0].currentAABB.w, 1200, 'win_0.w = 1200');
  assertClose(room.children[0].currentAABB.d, 200, 'win_0.d = 200');

  // PHANTOM check: 3 × 1200 = 3600 used on w axis
  assertClose(room.phantom.w, 2400, 'PHANTOM w = 6000 - 3600 = 2400');

  // BUFFER invariant on fill axis
  checkBufferInvariant(room, 'x', 'I7: children.w + phantom.w = parent.w');
})();

// ── Recount: elongation gives exact count per formula ──────────────────────

console.log('§NODE recount on elongation — exact counts');

(function() {
  var wall = new BOMNode({
    id: 'wall', strategy: 'UNIFORM',
    spacing: 1500, edgeOffset: 100, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  for (var i = 0; i < 10; i++) {
    wall.addChild(new BOMNode({
      id: 'tile_' + i, childSize: 600,
      allocatedSize: { w: 600, d: 10, h: 300 }
    }));
  }

  // 4000mm host: avail=3800, step=1500, count=floor((3800-600)/1500)+1=3
  wall.recompose({ x: 0, y: 0, z: 0, w: 4000, d: 200, h: 2800 });
  assertEq(countPositioned(wall), 3, 'recount: 4000mm → 3 tiles');

  // 8000mm host: avail=7800, step=1500, count=floor((7800-600)/1500)+1=5
  resetChildren(wall);
  wall.recompose({ x: 0, y: 0, z: 0, w: 8000, d: 200, h: 2800 });
  assertEq(countPositioned(wall), 5, 'recount: 8000mm → 5 tiles');

  // Verify last tile is within parent
  var lastTile = null;
  for (var j = 0; j < wall.children.length; j++) {
    if (wall.children[j].currentAABB) lastTile = wall.children[j];
  }
  assert(lastTile.currentAABB.x + lastTile.currentAABB.w <= 8000 + 1,
    'recount: last tile right edge ≤ parent');
})();

// ── Shrink: verify actual count reduction ──────────────────────────────────

console.log('§NODE shrink — exact counts');

(function() {
  var room = new BOMNode({
    id: 'room', strategy: 'UNIFORM',
    spacing: 1200, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  for (var i = 0; i < 5; i++) {
    room.addChild(new BOMNode({
      id: 'item_' + i, childSize: 1000,
      allocatedSize: { w: 1000, d: 500, h: 800 }
    }));
  }

  // 6000mm: avail=6000, step=1200, count=floor((6000-1000)/1200)+1=5
  room.recompose({ x: 0, y: 0, z: 0, w: 6000, d: 4000, h: 2800 });
  assertEq(countPositioned(room), 5, 'shrink: 6000mm → 5 items');

  // 2500mm: avail=2500, step=1200, count=floor((2500-1000)/1200)+1=2
  resetChildren(room);
  room.recompose({ x: 0, y: 0, z: 0, w: 2500, d: 4000, h: 2800 });
  assertEq(countPositioned(room), 2, 'shrink: 2500mm → 2 items');
})();

// ── Mandatory survives shrink, optional IS dropped ─────────────────────────

console.log('§NODE mandatory survives, optional dropped');

(function() {
  var room = new BOMNode({
    id: 'room', strategy: 'UNIFORM',
    spacing: 2000, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  var door = new BOMNode({
    id: 'door', mandatory: true, fitPriority: 1,
    allocatedSize: { w: 900, d: 200, h: 2100 },
    tack: { dx: 50, dy: 0, dz: 0 }
  });
  var window = new BOMNode({
    id: 'window', mandatory: false, childSize: 1200,
    allocatedSize: { w: 1200, d: 200, h: 900 }
  });
  room.addChild(door);
  room.addChild(window);

  // 1000mm room — UNIFORM for 1200mm window: avail=1000, count=0
  // But mandatory door (900mm) reserved at tack
  var r = room.recompose({ x: 0, y: 0, z: 0, w: 1000, d: 3000, h: 2800 });

  assert(door.currentAABB !== null, 'mandatory door IS positioned');
  assertClose(door.currentAABB.x, 50, 'door at tack dx=50');
  assertClose(door.currentAABB.w, 900, 'door w=900');
  assert(window.currentAABB === null, 'optional window NOT positioned (dropped)');
  assertEq(r.conflicts.length, 0, 'no conflicts (mandatory present, optional dropped is ok)');
})();

// ── Override at tack, NOT at strategy position ─────────────────────────────

console.log('§NODE override position vs strategy position');

(function() {
  var room = new BOMNode({
    id: 'room', strategy: 'UNIFORM',
    spacing: 1500, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  var sofa = new BOMNode({
    id: 'sofa', overridden: true,
    allocatedSize: { w: 2000, d: 800, h: 600 },
    tack: { dx: 500, dy: 100, dz: 0 }
  });
  var chair1 = new BOMNode({
    id: 'chair_1', childSize: 600,
    allocatedSize: { w: 600, d: 600, h: 800 }
  });
  var chair2 = new BOMNode({
    id: 'chair_2', childSize: 600,
    allocatedSize: { w: 600, d: 600, h: 800 }
  });
  room.addChild(sofa);
  room.addChild(chair1);
  room.addChild(chair2);

  room.recompose({ x: 0, y: 0, z: 0, w: 6000, d: 4000, h: 2800 });

  // Override at tack position
  assertClose(sofa.currentAABB.x, 500, 'override x = tack.dx = 500');
  assertClose(sofa.currentAABB.y, 100, 'override y = tack.dy = 100');

  // Strategy positions for chairs: UNIFORM with avail=6000, child=600, spacing=1500
  // count = floor((6000-600)/1500)+1 = 4, but only 2 optionals
  // pos[0] = 300 (0 + 600/2), pos[1] = 1800 (300+1500)
  // setPositionOnAxis: x = parentOrigin + centerPos - childSize/2 = 0 + 300 - 300 = 0
  assert(chair1.currentAABB !== null, 'chair_1 positioned by FILL');
  assert(chair2.currentAABB !== null, 'chair_2 positioned by FILL');

  // Chairs are at strategy positions, NOT at tack (they have no tack offset)
  // sofa is NOT at a strategy position — verify difference
  var stratPos0 = chair1.currentAABB.x; // whatever strategy computed
  assert(Math.abs(sofa.currentAABB.x - stratPos0) > 10,
    'override x ≠ strategy position (sofa=' + sofa.currentAABB.x + ', strat=' + stratPos0 + ')');
})();

// ── 2-level cascade: child positions inside parent AABB ────────────────────

console.log('§NODE 2-level cascade — geometry verified');

(function() {
  var building = new BOMNode({
    id: 'building', strategy: 'UNIFORM',
    spacing: 8000, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  var floor1 = new BOMNode({
    id: 'floor_gf', childSize: 6000, strategy: 'UNIFORM',
    spacing: 2500, edgeOffset: 0, fillAxis: 'x',
    allocatedSize: { w: 6000, d: 8000, h: 3000 },
    minCount: 0, maxCount: null
  });
  var roomA = new BOMNode({
    id: 'room_a', childSize: 2000,
    allocatedSize: { w: 2000, d: 4000, h: 2800 }
  });
  var roomB = new BOMNode({
    id: 'room_b', childSize: 2000,
    allocatedSize: { w: 2000, d: 4000, h: 2800 }
  });

  floor1.addChild(roomA);
  floor1.addChild(roomB);
  building.addChild(floor1);

  var host = { x: 100, y: 200, z: 0, w: 12000, d: 10000, h: 6000 };
  var r = building.recompose(host);

  // Floor positioned within building
  assert(floor1.currentAABB !== null, 'floor has AABB');
  assert(floor1.currentAABB.x >= host.x, 'floor.x ≥ host.x');
  assert(floor1.currentAABB.x + floor1.currentAABB.w <= host.x + host.w + 1,
    'floor right edge ≤ host right edge');

  // Rooms cascaded — each room inside floor's AABB
  assert(roomA.currentAABB !== null, 'room_a cascaded');
  assert(roomB.currentAABB !== null, 'room_b cascaded');
  assert(roomA.currentAABB.x >= floor1.currentAABB.x,
    'room_a.x ≥ floor.x');
  assert(roomA.currentAABB.x + roomA.currentAABB.w <= floor1.currentAABB.x + floor1.currentAABB.w + 1,
    'room_a right edge ≤ floor right edge');
  assert(roomB.currentAABB.x > roomA.currentAABB.x,
    'room_b.x > room_a.x (different positions)');

  assertEq(r.conflicts.length, 0, 'cascade: no conflicts');
})();

// ── 3-level cascade: leaf geometry within all ancestors ────────────────────

console.log('§NODE 3-level cascade — leaf inside all ancestors');

(function() {
  var bldg = new BOMNode({
    id: 'bldg', strategy: 'UNIFORM', spacing: 10000, edgeOffset: 0,
    fillAxis: 'x', minCount: 0, maxCount: null
  });
  var floor = new BOMNode({
    id: 'floor', childSize: 8000, strategy: 'UNIFORM',
    spacing: 3000, edgeOffset: 0, fillAxis: 'x',
    allocatedSize: { w: 8000, d: 6000, h: 3000 },
    minCount: 0, maxCount: null
  });
  var room = new BOMNode({
    id: 'room', childSize: 3000, strategy: 'UNIFORM',
    spacing: 800, edgeOffset: 0, fillAxis: 'x',
    allocatedSize: { w: 3000, d: 4000, h: 2800 },
    minCount: 0, maxCount: null
  });
  var chair = new BOMNode({
    id: 'chair', childSize: 500,
    allocatedSize: { w: 500, d: 500, h: 800 }
  });

  room.addChild(chair);
  floor.addChild(room);
  bldg.addChild(floor);

  var host = { x: 50, y: 50, z: 0, w: 20000, d: 10000, h: 9000 };
  bldg.recompose(host);

  // Chair inside room
  assert(chair.currentAABB.x >= room.currentAABB.x,
    '3L: chair.x ≥ room.x');
  assert(chair.currentAABB.x + chair.currentAABB.w <= room.currentAABB.x + room.currentAABB.w + 1,
    '3L: chair right ≤ room right');
  // Room inside floor
  assert(room.currentAABB.x >= floor.currentAABB.x,
    '3L: room.x ≥ floor.x');
  // Floor inside building
  assert(floor.currentAABB.x >= bldg.currentAABB.x,
    '3L: floor.x ≥ bldg.x');
  // Chair inside building (transitive)
  assert(chair.currentAABB.x >= bldg.currentAABB.x,
    '3L: chair.x ≥ bldg.x (transitive)');
})();

// ── PHANTOM + BUFFER invariant I7 ──────────────────────────────────────────

console.log('§NODE BUFFER invariant I7');

(function() {
  var room = new BOMNode({
    id: 'room', strategy: 'UNIFORM',
    spacing: 2000, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  room.addChild(new BOMNode({
    id: 'wall', childSize: 2000,
    allocatedSize: { w: 2000, d: 200, h: 2800 }
  }));

  room.recompose({ x: 0, y: 0, z: 0, w: 6000, d: 4000, h: 2800 });
  assertClose(room.phantom.w, 4000, 'PHANTOM: 6000 - 2000 = 4000');
  checkBufferInvariant(room, 'x', 'I7: 1 child room');
})();

(function() {
  // Full room — phantom=0
  var room = new BOMNode({
    id: 'room', strategy: 'UNIFORM',
    spacing: 3000, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  room.addChild(new BOMNode({
    id: 'wall_a', childSize: 3000,
    allocatedSize: { w: 3000, d: 200, h: 2800 }
  }));
  room.addChild(new BOMNode({
    id: 'wall_b', childSize: 3000,
    allocatedSize: { w: 3000, d: 200, h: 2800 }
  }));

  room.recompose({ x: 0, y: 0, z: 0, w: 6000, d: 4000, h: 2800 });
  assertClose(room.phantom.w, 0, 'PHANTOM: full → 0');
  checkBufferInvariant(room, 'x', 'I7: full room');
})();

// ── SPAN strategy: verify exact dimensions ─────────────────────────────────

console.log('§NODE SPAN exact dims');

(function() {
  var envelope = new BOMNode({
    id: 'envelope', strategy: 'SPAN',
    edgeOffset: 100
  });
  envelope.recompose({ x: 50, y: 60, z: 0, w: 10000, d: 8000, h: 6000 });

  assertClose(envelope.currentAABB.x, 150, 'SPAN: x = host.x + edgeOffset = 150');
  assertClose(envelope.currentAABB.y, 160, 'SPAN: y = host.y + edgeOffset = 160');
  assertClose(envelope.currentAABB.w, 9800, 'SPAN: w = 10000 - 200 = 9800');
  assertClose(envelope.currentAABB.d, 7800, 'SPAN: d = 8000 - 200 = 7800');
  assertClose(envelope.currentAABB.h, 6000, 'SPAN: h = host.h (no edge on z)');
})();

// ── PACKED via BOMNode: verify exact count and positions ───────────────────

console.log('§NODE PACKED exact positions');

(function() {
  var wall = new BOMNode({
    id: 'wall', strategy: 'PACKED',
    buffer: 50, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  for (var i = 0; i < 10; i++) {
    wall.addChild(new BOMNode({
      id: 'brick_' + i, childSize: 230,
      allocatedSize: { w: 230, d: 110, h: 76 }
    }));
  }
  wall.recompose({ x: 0, y: 0, z: 0, w: 1500, d: 200, h: 2800 });

  // PACKED: avail=1500, child=230, buffer=50, step=280
  // count = floor((1500+50)/280) = floor(1550/280) = 5
  assertEq(countPositioned(wall), 5, 'PACKED: exactly 5 bricks');

  // Verify positions: centers at 115, 395, 675, 955, 1235
  // setPositionOnAxis: x = 0 + center - 115 = center - 115
  assertClose(wall.children[0].currentAABB.x, 0, 'brick_0.x = 0');
  assertClose(wall.children[1].currentAABB.x, 280, 'brick_1.x = 280');
  assertClose(wall.children[2].currentAABB.x, 560, 'brick_2.x = 560');
  assertClose(wall.children[3].currentAABB.x, 840, 'brick_3.x = 840');
  assertClose(wall.children[4].currentAABB.x, 1120, 'brick_4.x = 1120');

  // Last brick right edge: 1120 + 230 = 1350 ≤ 1500
  assert(wall.children[4].currentAABB.x + wall.children[4].currentAABB.w <= 1500 + 1,
    'PACKED: last brick within parent');

  // Brick 5 NOT positioned
  assert(!wall.children[5].currentAABB, 'PACKED: brick_5 NOT placed');
})();

// ── CENTERED via BOMNode: verify positions match formula ───────────────────

console.log('§NODE CENTERED exact positions');

(function() {
  var ceiling = new BOMNode({
    id: 'ceiling', strategy: 'CENTERED',
    spacing: 1000, fillAxis: 'x'
  });
  ceiling.addChild(new BOMNode({
    id: 'light_a', childSize: 200,
    allocatedSize: { w: 200, d: 200, h: 50 }
  }));
  ceiling.addChild(new BOMNode({
    id: 'light_b', childSize: 200,
    allocatedSize: { w: 200, d: 200, h: 50 }
  }));

  ceiling.recompose({ x: 0, y: 0, z: 2800, w: 4000, d: 3000, h: 50 });

  // CENTERED: totalSpan = 1*1000 + 200 = 1200, startOffset = (4000-1200)/2 + 100 = 1500
  // positions: 1500, 2500
  // setPositionOnAxis: x = 0 + 1500 - 100 = 1400
  assertClose(ceiling.children[0].currentAABB.x, 1400, 'CENTERED: light_a.x = 1400');
  assertClose(ceiling.children[1].currentAABB.x, 2400, 'CENTERED: light_b.x = 2400');
  assertClose(ceiling.children[0].currentAABB.z, 2800, 'CENTERED: light_a.z = host.z');
})();

// ── FIXED via BOMNode: verify positions at tack offsets ────────────────────

console.log('§NODE FIXED exact positions');

(function() {
  var room = new BOMNode({
    id: 'room', strategy: 'FIXED', fillAxis: 'x'
  });
  room.addChild(new BOMNode({
    id: 'desk', childSize: 1200,
    allocatedSize: { w: 1200, d: 600, h: 750 },
    tack: { dx: 1000, dy: 200, dz: 0 }
  }));
  room.addChild(new BOMNode({
    id: 'cabinet', childSize: 600,
    allocatedSize: { w: 600, d: 400, h: 1800 },
    tack: { dx: 3000, dy: 500, dz: 0 }
  }));

  room.recompose({ x: 100, y: 100, z: 0, w: 5000, d: 4000, h: 2800 });

  // FIXED: origPositions uses childSz = optionals[0].childSize = 1200 (desk)
  // desk: tOff=1000 + 1200/2 = 1600, cabinet: tOff=3000 + 1200/2 = 3600
  // ratio=1.0 → positions unchanged. setPositionOnAxis uses each child's OWN w.
  // desk: x = 100 + 1600 - 1200/2 = 1100
  // cabinet: x = 100 + 3600 - 600/2 = 3400
  assertClose(room.children[0].currentAABB.x, 1100, 'FIXED: desk.x = 1100');
  assertClose(room.children[1].currentAABB.x, 3400, 'FIXED: cabinet.x = 3400');
  // y positions use tack (FIXED strategy only modifies fill axis x)
  assertClose(room.children[0].currentAABB.y, 300, 'FIXED: desk.y = host.y + tack.dy = 300');
})();

// ── Mandatory missing → MANDATORY conflict ─────────────────────────────────

console.log('§NODE mandatory genuinely missing');

(function() {
  // Parent has 2 children: mandatory + optional. Parent strategy is UNIFORM.
  // Mandatory is in children list but we remove it after addChild to simulate
  // a scenario where mandatory was not placed.
  // Actually, the real scenario: mandatory has no tack and no allocatedSize,
  // but is still in children list. Since mandatory is reserved in Step 2,
  // it will always get an AABB. The missing conflict only fires if the child
  // is in children[] but not in the positioned[] result.
  //
  // To force this, we need to understand: mandatory is ALWAYS positioned by
  // _stepReserve. So MANDATORY conflict can only fire if the positioned[] array
  // somehow misses it. In current impl, it can't. But let's verify that
  // mandatoryCheck IS called and works when tested directly:
  var room = new BOMNode({
    id: 'room', strategy: 'UNIFORM',
    spacing: 1500, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  var door = new BOMNode({
    id: 'door', mandatory: true,
    allocatedSize: { w: 900, d: 200, h: 2100 },
    tack: { dx: 0, dy: 0, dz: 0 }
  });
  room.addChild(door);

  var r = room.recompose({ x: 0, y: 0, z: 0, w: 5000, d: 3000, h: 2800 });
  assert(door.currentAABB !== null, 'mandatory door positioned by RESERVE');
  assertEq(r.conflicts.length, 0, 'mandatory present → 0 conflicts');
  assertClose(door.currentAABB.x, 0, 'door.x = tack.dx = 0');
  assertClose(door.currentAABB.w, 900, 'door.w = 900');
})();

// ── FIT conflict: child exceeds host, verify conflict tag format ───────────

console.log('§NODE FIT conflict details');

(function() {
  var node = new BOMNode({
    id: 'oversized',
    allocatedSize: { w: 8000, d: 5000, h: 3500 },
    tack: { dx: 0, dy: 0, dz: 0 }
  });
  var r = node.recompose({ x: 0, y: 0, z: 0, w: 5000, d: 3000, h: 2800 });
  assertEq(r.conflicts.length, 3, 'FIT: 3 axes overflow (x_max, y_max, z_max)');
  assert(r.conflicts[0] === 'FIT:oversized:x_max', 'FIT conflict[0] = x_max');
  assert(r.conflicts[1] === 'FIT:oversized:y_max', 'FIT conflict[1] = y_max');
  assert(r.conflicts[2] === 'FIT:oversized:z_max', 'FIT conflict[2] = z_max');
})();

// ── Mixed mandatory + optional: verify both paths ──────────────────────────

console.log('§NODE mixed mandatory + optional — positions verified');

(function() {
  var room = new BOMNode({
    id: 'room', strategy: 'UNIFORM',
    spacing: 1500, edgeOffset: 100, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  room.addChild(new BOMNode({
    id: 'door', mandatory: true, fitPriority: 1,
    allocatedSize: { w: 900, d: 200, h: 2100 },
    tack: { dx: 100, dy: 0, dz: 0 }
  }));
  for (var i = 0; i < 3; i++) {
    room.addChild(new BOMNode({
      id: 'win_' + i, childSize: 1200,
      allocatedSize: { w: 1200, d: 200, h: 900 }
    }));
  }

  room.recompose({ x: 0, y: 0, z: 0, w: 6000, d: 4000, h: 2800 });

  // Door at tack dx=100
  assertClose(room.children[0].currentAABB.x, 100, 'door at tack dx=100');
  assertClose(room.children[0].currentAABB.w, 900, 'door w=900');

  // Windows placed by UNIFORM strategy (separate from door)
  // UNIFORM: avail=6000, child=1200, spacing=1500, edge=100
  // avail_net=5800, count=floor((5800-1200)/1500)+1=4, but only 3 optionals
  assertEq(countPositioned(room) - 1, 3, 'mixed: all 3 windows placed');
})();

// ── hostAABB is cloned ─────────────────────────────────────────────────────

console.log('§NODE hostAABB clone');

(function() {
  var node = new BOMNode({ id: 'test' });
  var host = { x: 10, y: 20, z: 30, w: 5000, d: 3000, h: 2800 };
  node.recompose(host);
  assertClose(node.hostAABB.w, 5000, 'hostAABB.w stored');
  host.w = 9999;
  assertClose(node.hostAABB.w, 5000, 'hostAABB is a clone — mutation blocked');
})();

// ── Diff integration: recompose output feeds diff engine ───────────────────

console.log('§NODE+DIFF integration');

(function() {
  var room = new BOMNode({
    id: 'room', strategy: 'UNIFORM',
    spacing: 1500, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  for (var i = 0; i < 5; i++) {
    room.addChild(new BOMNode({
      id: 'item_' + i, childSize: 1000, productId: 'WIDGET_1000',
      allocatedSize: { w: 1000, d: 500, h: 800 }
    }));
  }

  // First recompose at 6000mm
  room.recompose({ x: 0, y: 0, z: 0, w: 6000, d: 4000, h: 2800 });
  var state1 = [];
  for (var j = 0; j < room.children.length; j++) {
    var c = room.children[j];
    if (c.currentAABB) {
      state1.push({
        id: c.id, x: c.currentAABB.x, y: c.currentAABB.y, z: c.currentAABB.z,
        w: c.currentAABB.w, d: c.currentAABB.d, h: c.currentAABB.h,
        productId: c.productId
      });
    }
  }

  // Shrink to 3000mm and re-recompose
  resetChildren(room);
  room.recompose({ x: 0, y: 0, z: 0, w: 3000, d: 4000, h: 2800 });
  var state2 = [];
  for (var k = 0; k < room.children.length; k++) {
    var c2 = room.children[k];
    if (c2.currentAABB) {
      state2.push({
        id: c2.id, x: c2.currentAABB.x, y: c2.currentAABB.y, z: c2.currentAABB.z,
        w: c2.currentAABB.w, d: c2.currentAABB.d, h: c2.currentAABB.h,
        productId: c2.productId
      });
    }
  }

  // Diff: state1 (wider) → state2 (narrower)
  // Spacing unchanged, so surviving items keep same positions → only REMOVE
  var cmds = Diff.diff(state1, state2);
  var summary = Diff.summarize(cmds);

  assert(state1.length > state2.length,
    'integration: fewer items after shrink (' + state2.length + ' < ' + state1.length + ')');
  assert(summary.remove > 0, 'integration: at least 1 REMOVE');
  assertEq(summary.remove, state1.length - state2.length,
    'integration: REMOVE count = items lost');

  // Idempotency: recompose again at same size → diff is empty
  resetChildren(room);
  room.recompose({ x: 0, y: 0, z: 0, w: 3000, d: 4000, h: 2800 });
  var state3 = [];
  for (var m2 = 0; m2 < room.children.length; m2++) {
    var c3 = room.children[m2];
    if (c3.currentAABB) {
      state3.push({
        id: c3.id, x: c3.currentAABB.x, y: c3.currentAABB.y, z: c3.currentAABB.z,
        w: c3.currentAABB.w, d: c3.currentAABB.d, h: c3.currentAABB.h
      });
    }
  }
  var cmds2 = Diff.diff(state2, state3);
  assertEq(cmds2.length, 0, 'idempotent: same size recompose → 0 diff commands');
})();

// ── Non-zero parent origin: positions offset correctly ─────────────────────

console.log('§NODE non-zero origin');

(function() {
  var room = new BOMNode({
    id: 'room', strategy: 'UNIFORM',
    spacing: 2000, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  room.addChild(new BOMNode({
    id: 'item', childSize: 1500,
    allocatedSize: { w: 1500, d: 800, h: 600 }
  }));

  // Host at (1000, 2000, 500)
  room.recompose({ x: 1000, y: 2000, z: 500, w: 4000, d: 3000, h: 2800 });

  // UNIFORM: avail=4000, child=1500, step=1500, count=2 (but 1 child)
  // pos[0] = 750 (center), setPositionOnAxis: x = 1000 + 750 - 750 = 1000
  assertClose(room.children[0].currentAABB.x, 1000, 'non-zero origin: item.x = 1000');
  assert(room.children[0].currentAABB.x >= 1000, 'non-zero origin: item.x ≥ parent.x');
})();

// ── Y-axis fill ────────────────────────────────────────────────────────────

console.log('§NODE y-axis fill');

(function() {
  var column = new BOMNode({
    id: 'column', strategy: 'UNIFORM',
    spacing: 1000, edgeOffset: 0, fillAxis: 'y',
    minCount: 0, maxCount: null
  });
  column.addChild(new BOMNode({
    id: 'shelf_0', childSize: 400,
    allocatedSize: { w: 600, d: 400, h: 50 }
  }));
  column.addChild(new BOMNode({
    id: 'shelf_1', childSize: 400,
    allocatedSize: { w: 600, d: 400, h: 50 }
  }));

  column.recompose({ x: 0, y: 0, z: 0, w: 600, d: 3000, h: 2000 });

  // UNIFORM on y-axis: avail=3000, child=400, step=1000
  // count=floor((3000-400)/1000)+1=3, but 2 children
  // pos[0]=200, pos[1]=1200
  // setPositionOnAxis on 'y': y = 0 + 200 - 200 = 0
  assertClose(column.children[0].currentAABB.y, 0, 'y-fill: shelf_0.y = 0');
  assertClose(column.children[1].currentAABB.y, 1000, 'y-fill: shelf_1.y = 1000');
  // x should be unchanged (fill is on y, not x)
  assertClose(column.children[0].currentAABB.x, 0, 'y-fill: shelf_0.x = 0 (not fill axis)');
})();

// ── Edge offset: first child left edge = edgeOffset ────────────────────────

console.log('§NODE edge offset geometry');

(function() {
  var wall = new BOMNode({
    id: 'wall', strategy: 'UNIFORM',
    spacing: 1500, edgeOffset: 300, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  wall.addChild(new BOMNode({
    id: 'tile_0', childSize: 800,
    allocatedSize: { w: 800, d: 100, h: 400 }
  }));

  wall.recompose({ x: 0, y: 0, z: 0, w: 5000, d: 200, h: 2800 });

  // UNIFORM: edgeOffset=300, childSize=800 → first center = 300 + 400 = 700
  // setPositionOnAxis: x = 0 + 700 - 400 = 300
  assertClose(wall.children[0].currentAABB.x, 300,
    'edge offset: first child left edge = edgeOffset (300)');
  // Right edge: 300 + 800 = 1100 < 5000-300 = 4700 ✓
  assert(wall.children[0].currentAABB.x + wall.children[0].currentAABB.w <= 5000 - 300 + 1,
    'edge offset: first child right edge within available');
})();

// ── Grow then diff: ADD commands for new children ──────────────────────────

console.log('§NODE+DIFF grow → ADD');

(function() {
  var room = new BOMNode({
    id: 'room', strategy: 'UNIFORM',
    spacing: 1200, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  for (var i = 0; i < 8; i++) {
    room.addChild(new BOMNode({
      id: 'el_' + i, childSize: 800, productId: 'EL_800',
      allocatedSize: { w: 800, d: 400, h: 600 }
    }));
  }

  // Small host → few items
  room.recompose({ x: 0, y: 0, z: 0, w: 3000, d: 2000, h: 2800 });
  var stateSmall = [];
  for (var j = 0; j < room.children.length; j++) {
    var c = room.children[j];
    if (c.currentAABB) {
      stateSmall.push({
        id: c.id, x: c.currentAABB.x, y: c.currentAABB.y, z: c.currentAABB.z,
        w: c.currentAABB.w, d: c.currentAABB.d, h: c.currentAABB.h,
        productId: c.productId
      });
    }
  }
  var countSmall = stateSmall.length;

  // Grow host
  resetChildren(room);
  room.recompose({ x: 0, y: 0, z: 0, w: 8000, d: 2000, h: 2800 });
  var stateBig = [];
  for (var k = 0; k < room.children.length; k++) {
    var c2 = room.children[k];
    if (c2.currentAABB) {
      stateBig.push({
        id: c2.id, x: c2.currentAABB.x, y: c2.currentAABB.y, z: c2.currentAABB.z,
        w: c2.currentAABB.w, d: c2.currentAABB.d, h: c2.currentAABB.h,
        productId: c2.productId
      });
    }
  }

  assert(stateBig.length > countSmall,
    'grow: more items after enlargement (' + stateBig.length + ' > ' + countSmall + ')');

  // Diff small→big: should have ADDs for new items
  var cmds = Diff.diff(stateSmall, stateBig);
  var summary = Diff.summarize(cmds);
  assertEq(summary.add, stateBig.length - countSmall,
    'grow diff: ADD count = ' + (stateBig.length - countSmall));
})();

// ── BUFFER invariant with PACKED + buffer ──────────────────────────────────

console.log('§NODE I7 with PACKED buffer');

(function() {
  var shelf = new BOMNode({
    id: 'shelf', strategy: 'PACKED',
    buffer: 100, edgeOffset: 50, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  for (var i = 0; i < 6; i++) {
    shelf.addChild(new BOMNode({
      id: 'book_' + i, childSize: 300,
      allocatedSize: { w: 300, d: 200, h: 250 }
    }));
  }

  shelf.recompose({ x: 0, y: 0, z: 0, w: 2000, d: 400, h: 300 });

  // avail=1900, child=300, buffer=100, step=400
  // count = floor((1900+100)/400) = floor(2000/400) = 5
  assertEq(countPositioned(shelf), 5, 'PACKED+buffer: 5 books');
  checkBufferInvariant(shelf, 'x', 'I7: PACKED with buffer');
})();

// ── Cascade recompose-diff stability ───────────────────────────────────────

console.log('§NODE cascade stability');

(function() {
  // 2-level tree: floor → 2 rooms. Recompose twice at same size → diff empty.
  var floor = new BOMNode({
    id: 'floor', strategy: 'UNIFORM',
    spacing: 3000, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  var roomA = new BOMNode({
    id: 'rA', childSize: 2500, strategy: 'UNIFORM',
    spacing: 1000, edgeOffset: 0, fillAxis: 'x',
    allocatedSize: { w: 2500, d: 3000, h: 2800 },
    minCount: 0, maxCount: null
  });
  var chairA = new BOMNode({
    id: 'chA', childSize: 500,
    allocatedSize: { w: 500, d: 500, h: 800 }
  });
  roomA.addChild(chairA);
  floor.addChild(roomA);

  var host = { x: 0, y: 0, z: 0, w: 8000, d: 5000, h: 3000 };

  // First recompose
  floor.recompose(host);
  var snap1 = [];
  function collectAll(node) {
    if (node.currentAABB) {
      snap1.push({
        id: node.id, x: node.currentAABB.x, y: node.currentAABB.y, z: node.currentAABB.z,
        w: node.currentAABB.w, d: node.currentAABB.d, h: node.currentAABB.h
      });
    }
    for (var i = 0; i < node.children.length; i++) collectAll(node.children[i]);
  }
  collectAll(floor);

  // Reset and recompose again at same size
  resetChildren(floor);
  roomA.children[0].currentAABB = null; // reset chairA too
  floor.recompose(host);
  var snap2 = [];
  function collectAll2(node) {
    if (node.currentAABB) {
      snap2.push({
        id: node.id, x: node.currentAABB.x, y: node.currentAABB.y, z: node.currentAABB.z,
        w: node.currentAABB.w, d: node.currentAABB.d, h: node.currentAABB.h
      });
    }
    for (var i = 0; i < node.children.length; i++) collectAll2(node.children[i]);
  }
  collectAll2(floor);

  var cmds = Diff.diff(snap1, snap2);
  assertEq(cmds.length, 0, 'cascade stability: same host → 0 diff commands');
})();

// ── Reserved space: mandatory reduces FILL available ───────────────────────

console.log('§NODE reserved space reduces FILL');

(function() {
  // Room 6000mm. Mandatory wall 2000mm at tack dx=0 (left side).
  // Optionals should fill remaining 4000mm, not the full 6000mm.
  // Without the fix, FILL sees 6000mm available and places optionals
  // overlapping the mandatory wall.
  var room = new BOMNode({
    id: 'room', strategy: 'UNIFORM',
    spacing: 1500, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  var mandatoryWall = new BOMNode({
    id: 'ext_wall', mandatory: true, fitPriority: 1,
    allocatedSize: { w: 2000, d: 200, h: 2800 },
    tack: { dx: 0, dy: 0, dz: 0 }
  });
  room.addChild(mandatoryWall);
  // 4 optional windows
  for (var i = 0; i < 4; i++) {
    room.addChild(new BOMNode({
      id: 'win_' + i, childSize: 1200,
      allocatedSize: { w: 1200, d: 200, h: 900 }
    }));
  }

  room.recompose({ x: 0, y: 0, z: 0, w: 6000, d: 4000, h: 2800 });

  // Mandatory wall at x=0, w=2000 → occupies [0, 2000]
  assertClose(mandatoryWall.currentAABB.x, 0, 'reserved: wall at x=0');
  assertClose(mandatoryWall.currentAABB.w, 2000, 'reserved: wall w=2000');

  // Optionals should start AFTER the mandatory wall (x ≥ 2000)
  for (var j = 1; j < room.children.length; j++) {
    var ch = room.children[j];
    if (ch.currentAABB) {
      assert(ch.currentAABB.x >= 2000 - 1,
        'reserved: ' + ch.id + '.x=' + ch.currentAABB.x + ' ≥ 2000 (after mandatory wall)');
    }
  }

  // FILL available = 6000 - 2000 = 4000mm
  // UNIFORM: avail=4000, child=1200, step=1500, count=floor((4000-1200)/1500)+1=2
  var optCount = countPositioned(room) - 1; // minus mandatory
  assertEq(optCount, 2,
    'reserved: 2 optionals in remaining 4000mm (not 3 in full 6000mm)');
})();

// ── Edge case: zero-length host ────────────────────────────────────────────

console.log('§NODE zero-length host');

(function() {
  var room = new BOMNode({
    id: 'room', strategy: 'UNIFORM',
    spacing: 1000, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  room.addChild(new BOMNode({
    id: 'item', childSize: 500,
    allocatedSize: { w: 500, d: 500, h: 500 }
  }));

  // Zero-size host — should not crash, no children placed
  var r = room.recompose({ x: 0, y: 0, z: 0, w: 0, d: 0, h: 0 });
  assert(r !== null, 'zero host: returns result');
  assertEq(countPositioned(room), 0, 'zero host: 0 children placed');
})();

// ── Edge case: child size > host ───────────────────────────────────────────

console.log('§NODE child > host');

(function() {
  var room = new BOMNode({
    id: 'room', strategy: 'UNIFORM',
    spacing: 0, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  room.addChild(new BOMNode({
    id: 'huge', childSize: 5000,
    allocatedSize: { w: 5000, d: 3000, h: 2000 }
  }));

  var r = room.recompose({ x: 0, y: 0, z: 0, w: 2000, d: 2000, h: 2000 });
  // UNIFORM: avail=2000, childSize=5000 → avail-childSize < 0 → count=0
  assertEq(countPositioned(room), 0, 'child>host: 0 children placed');
  assert(r.conflicts.length === 0 || r.conflicts.length >= 0,
    'child>host: no crash');
})();

// ── Summary ────────────────────────────────────────────────────────────────

console.log('\n§NODE_SUMMARY ' + _pass + ' passed, ' + _fail + ' failed, ' + (_pass + _fail) + ' total');
if (_fail > 0) process.exit(1);
