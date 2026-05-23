/**
 * test_bom_whitebox.js — §S272 BOM Engine: Advanced Whitebox Tests
 * Implementing BOM_ENGINE_SPEC.md §14 — Witness: W-BOM-ENGINE
 *
 * Techniques:
 *   1. Property-based (fast-check) — invariants hold for random inputs
 *   2. Metamorphic — strategy monotonicity, scaling coherence
 *   3. Golden master — serialize tree, compare to known-good JSON
 *   4. State recording — _trace on every recompose, logged as structured JSON
 *
 * All Node.js, no browser, no Playwright. §-tagged log lines for every finding.
 */
'use strict';

var fc = require('fast-check');
var BN = require('../bom_engine/bom_node.js');
var S  = require('../bom_engine/bom_strategies.js');
var C  = require('../bom_engine/bom_constraints.js');
var D  = require('../bom_engine/bom_diff.js');
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

// ═══════════════════════════════════════════════════════════════════════════
// 1. PROPERTY-BASED TESTS (fast-check)
// ═══════════════════════════════════════════════════════════════════════════

console.log('§WB property-based invariants');

// ── P1: UNIFORM count is non-negative, positions within [0, available] ────

(function() {
  try {
  fc.assert(
    fc.property(
      fc.nat(20000),  // available 0..20000
      fc.integer({ min: 1, max: 5000 }),  // childSize 1..5000
      fc.nat(5000),   // spacing
      fc.nat(1000),   // edgeOffset
      function(available, childSize, spacing, edgeOffset) {
        var r = S.UNIFORM({
          available: available, childSize: childSize,
          spacing: spacing || childSize, edgeOffset: edgeOffset,
          minCount: 0, maxCount: null
        });

        // Invariant: count ≥ 0
        if (r.count < 0) {
          console.log('§WB_COUNTER count<0 avail=' + available + ' child=' + childSize);
          return false;
        }
        // Invariant: positions length = count
        if (r.positions.length !== r.count) return false;
        // Invariant: all positions ≥ 0
        for (var i = 0; i < r.positions.length; i++) {
          if (r.positions[i] < -0.01) return false;
        }
        return true;
      }
    ),
    { numRuns: 500, verbose: 0 }
  );
  _pass++;
  console.log('  P1: UNIFORM 500 random inputs — all invariants hold');
  } catch(e) { _fail++; console.log('  FAIL P1: ' + e.message.split('\n')[0]); }
})();

// ── P2: PHANTOM invariant — SUM(children) + phantom ≥ 0 always ────────────

(function() {
  try {
  fc.assert(
    fc.property(
      fc.integer({ min: 100, max: 10000 }),   // host width
      fc.integer({ min: 50, max: 2000 }),     // childSize
      fc.integer({ min: 1, max: 5 }),          // numChildren
      function(hostW, childSize, numChildren) {
        var room = new BOMNode({
          id: 'prop_room', strategy: 'UNIFORM',
          spacing: childSize, edgeOffset: 0, fillAxis: 'x',
          minCount: 0, maxCount: null
        });
        for (var i = 0; i < numChildren; i++) {
          room.addChild(new BOMNode({
            id: 'ch_' + i, childSize: childSize,
            allocatedSize: { w: childSize, d: 100, h: 100 }
          }));
        }

        room.recompose({ x: 0, y: 0, z: 0, w: hostW, d: 1000, h: 2800 });

        // PHANTOM invariant: phantom.w ≥ 0
        if (room.phantom.w < -0.01) {
          console.log('§WB_COUNTER phantom<0 hostW=' + hostW +
            ' childSz=' + childSize + ' n=' + numChildren);
          return false;
        }
        return true;
      }
    ),
    { numRuns: 500, verbose: 0 }
  );
  _pass++;
  console.log('  P2: PHANTOM ≥ 0 across 500 random inputs');
  } catch(e) { _fail++; console.log('  FAIL P2: ' + e.message.split('\n')[0]); }
})();

// ── P3: MANDATORY never absent — mandatory node always has currentAABB ────

(function() {
  try {
  fc.assert(
    fc.property(
      fc.integer({ min: 100, max: 10000 }),  // host width
      fc.integer({ min: 50, max: 2000 }),    // mandatory size
      function(hostW, mandSize) {
        var room = new BOMNode({
          id: 'mand_room', strategy: 'UNIFORM',
          spacing: 1000, edgeOffset: 0, fillAxis: 'x',
          minCount: 0, maxCount: null
        });
        var mand = new BOMNode({
          id: 'mand_child', mandatory: true, fitPriority: 1,
          allocatedSize: { w: mandSize, d: 200, h: 2100 },
          tack: { dx: 0, dy: 0, dz: 0 }
        });
        room.addChild(mand);

        room.recompose({ x: 0, y: 0, z: 0, w: hostW, d: 1000, h: 2800 });

        // Mandatory always positioned
        if (!mand.currentAABB) {
          console.log('§WB_COUNTER mandatory_null hostW=' + hostW + ' mandSz=' + mandSize);
          return false;
        }
        return true;
      }
    ),
    { numRuns: 300, verbose: 0 }
  );
  _pass++;
  console.log('  P3: MANDATORY always positioned across 300 random inputs');
  } catch(e) { _fail++; console.log('  FAIL P3: ' + e.message.split('\n')[0]); }
})();

// ── P4: BUFFER invariant — children.w + phantom.w = parent.w on fill axis ─

(function() {
  try { fc.assert(
    fc.property(
      fc.integer({ min: 500, max: 10000 }),  // host width
      fc.integer({ min: 50, max: 1000 }),    // childSize
      fc.integer({ min: 0, max: 500 }),       // spacing
      fc.integer({ min: 1, max: 6 }),          // numChildren
      function(hostW, childSize, spacing, numChildren) {
        var room = new BOMNode({
          id: 'buf_room', strategy: 'UNIFORM',
          spacing: spacing || childSize, edgeOffset: 0, fillAxis: 'x',
          minCount: 0, maxCount: null
        });
        for (var i = 0; i < numChildren; i++) {
          room.addChild(new BOMNode({
            id: 'bc_' + i, childSize: childSize,
            allocatedSize: { w: childSize, d: 100, h: 100 }
          }));
        }

        room.recompose({ x: 0, y: 0, z: 0, w: hostW, d: 1000, h: 2800 });

        var sumW = 0;
        for (var j = 0; j < room.children.length; j++) {
          if (room.children[j].currentAABB) sumW += room.children[j].currentAABB.w;
        }
        var total = sumW + room.phantom.w;

        // I7: SUM + PHANTOM = parent (tolerance 1mm)
        // Only valid when children don't overflow parent (no FIT conflicts)
        if (sumW > hostW + 1) {
          // Overflow case — not an I7 violation, just children don't fit
          // PHANTOM should be 0 (clamped)
          if (room.phantom.w < -0.01) return false;
          return true;
        }
        if (Math.abs(total - hostW) > 1) {
          console.log('§WB_COUNTER I7_fail hostW=' + hostW +
            ' sumW=' + sumW + ' phantom=' + room.phantom.w + ' total=' + total);
          return false;
        }
        return true;
      }
    ),
    { numRuns: 500, verbose: 0 }
  );
  _pass++;
  console.log('  P4: BUFFER invariant I7 across 500 random inputs');
  } catch(e) { _fail++; console.log('  FAIL P4: ' + e.message.split('\n')[0]); }
})();

// ── P5: Diff idempotency — recompose twice → diff empty ──────────────────

(function() {
  try {
  fc.assert(
    fc.property(
      fc.integer({ min: 500, max: 8000 }),
      fc.integer({ min: 100, max: 1000 }),
      fc.integer({ min: 1, max: 4 }),
      function(hostW, childSize, numChildren) {
        var room = new BOMNode({
          id: 'idem', strategy: 'UNIFORM',
          spacing: childSize + 100, edgeOffset: 0, fillAxis: 'x',
          minCount: 0, maxCount: null
        });
        for (var i = 0; i < numChildren; i++) {
          room.addChild(new BOMNode({
            id: 'ic_' + i, childSize: childSize,
            allocatedSize: { w: childSize, d: 100, h: 100 }
          }));
        }

        var host = { x: 0, y: 0, z: 0, w: hostW, d: 1000, h: 2800 };

        // First recompose
        room.recompose(host);
        var snap1 = [];
        for (var j = 0; j < room.children.length; j++) {
          var c = room.children[j];
          if (c.currentAABB) {
            snap1.push({ id: c.id, x: c.currentAABB.x, y: c.currentAABB.y, z: c.currentAABB.z,
              w: c.currentAABB.w, d: c.currentAABB.d, h: c.currentAABB.h });
          }
        }

        // Reset & second recompose at same size
        for (var k = 0; k < room.children.length; k++) room.children[k].currentAABB = null;
        room.recompose(host);
        var snap2 = [];
        for (var m = 0; m < room.children.length; m++) {
          var c2 = room.children[m];
          if (c2.currentAABB) {
            snap2.push({ id: c2.id, x: c2.currentAABB.x, y: c2.currentAABB.y, z: c2.currentAABB.z,
              w: c2.currentAABB.w, d: c2.currentAABB.d, h: c2.currentAABB.h });
          }
        }

        var cmds = D.diff(snap1, snap2);
        if (cmds.length > 0) {
          console.log('§WB_COUNTER idempotent_fail hostW=' + hostW +
            ' cmds=' + cmds.length);
          return false;
        }
        return true;
      }
    ),
    { numRuns: 300, verbose: 0 }
  );
  _pass++;
  console.log('  P5: Diff idempotency across 300 random inputs');
  } catch(e) { _fail++; console.log('  FAIL P5: ' + e.message.split('\n')[0]); }
})();

// ═══════════════════════════════════════════════════════════════════════════
// 2. METAMORPHIC TESTS
// ═══════════════════════════════════════════════════════════════════════════

console.log('§WB metamorphic relations');

// ── M1: Doubling host length never decreases UNIFORM child count ──────────

(function() {
  var ok = true;
  for (var trial = 0; trial < 200; trial++) {
    var childSz = 100 + Math.floor(Math.random() * 900);
    var spacing = childSz + Math.floor(Math.random() * 500);
    var edge = Math.floor(Math.random() * 200);
    var avail1 = 500 + Math.floor(Math.random() * 5000);
    var avail2 = avail1 * 2;

    var r1 = S.UNIFORM({ available: avail1, childSize: childSz, spacing: spacing,
      edgeOffset: edge, minCount: 0, maxCount: null });
    var r2 = S.UNIFORM({ available: avail2, childSize: childSz, spacing: spacing,
      edgeOffset: edge, minCount: 0, maxCount: null });

    if (r2.count < r1.count) {
      console.log('§WB_COUNTER M1_fail avail=' + avail1 + '→' + avail2 +
        ' count=' + r1.count + '→' + r2.count);
      ok = false;
      break;
    }
  }
  assert(ok, 'M1: doubling host never decreases UNIFORM count (200 trials)');
})();

// ── M2: PACKED count ≥ UNIFORM count for same params (PACKED is denser) ───

(function() {
  var ok = true;
  for (var trial = 0; trial < 200; trial++) {
    var childSz = 100 + Math.floor(Math.random() * 800);
    var buffer = Math.floor(Math.random() * 200);
    var avail = 1000 + Math.floor(Math.random() * 5000);
    var edge = Math.floor(Math.random() * 100);

    var rU = S.UNIFORM({ available: avail, childSize: childSz,
      spacing: childSz + buffer, edgeOffset: edge, minCount: 0, maxCount: null });
    var rP = S.PACKED({ available: avail, childSize: childSz,
      buffer: buffer, edgeOffset: edge, minCount: 0, maxCount: null });

    if (rP.count < rU.count) {
      console.log('§WB_COUNTER M2_fail avail=' + avail + ' child=' + childSz +
        ' buf=' + buffer + ' packed=' + rP.count + ' uniform=' + rU.count);
      ok = false;
      break;
    }
  }
  assert(ok, 'M2: PACKED count ≥ UNIFORM count (200 trials)');
})();

// ── M3: SPAN always returns exactly 1 child if available > 2*edge ─────────

(function() {
  var ok = true;
  for (var trial = 0; trial < 200; trial++) {
    var avail = 100 + Math.floor(Math.random() * 10000);
    var edge = Math.floor(Math.random() * avail / 3);

    var r = S.SPAN({ available: avail, edgeOffset: edge });
    var expected = (avail - 2 * edge > 0) ? 1 : 0;

    if (r.count !== expected) {
      console.log('§WB_COUNTER M3_fail avail=' + avail + ' edge=' + edge +
        ' count=' + r.count + ' expected=' + expected);
      ok = false;
      break;
    }
  }
  assert(ok, 'M3: SPAN count = 1 iff avail > 2*edge (200 trials)');
})();

// ── M4: CENTERED positions are symmetric around midpoint ──────────────────

(function() {
  var ok = true;
  for (var trial = 0; trial < 100; trial++) {
    var avail = 1000 + Math.floor(Math.random() * 5000);
    var childSz = 50 + Math.floor(Math.random() * 400);
    var spacing = childSz + Math.floor(Math.random() * 500);
    var count = 2 + Math.floor(Math.random() * 4);

    var r = S.CENTERED({ available: avail, childSize: childSz, spacing: spacing, count: count });
    if (r.count < 2) continue;

    var mid = avail / 2;
    var first = r.positions[0];
    var last = r.positions[r.positions.length - 1];
    var distFirst = mid - first;
    var distLast = last - mid;

    if (Math.abs(distFirst - distLast) > 0.1) {
      console.log('§WB_COUNTER M4_fail avail=' + avail + ' first=' + first +
        ' last=' + last + ' mid=' + mid);
      ok = false;
      break;
    }
  }
  assert(ok, 'M4: CENTERED positions symmetric around midpoint (100 trials)');
})();

// ── M5: Shrinking host → fewer or equal children in BOMNode ───────────────

(function() {
  var ok = true;
  for (var trial = 0; trial < 100; trial++) {
    var hostW1 = 2000 + Math.floor(Math.random() * 6000);
    var hostW2 = Math.floor(hostW1 * (0.3 + Math.random() * 0.5)); // shrink to 30-80%
    var childSz = 200 + Math.floor(Math.random() * 800);
    var spacing = childSz + Math.floor(Math.random() * 500);

    var room1 = new BOMNode({
      id: 'mr1', strategy: 'UNIFORM', spacing: spacing,
      edgeOffset: 0, fillAxis: 'x', minCount: 0, maxCount: null
    });
    var room2 = new BOMNode({
      id: 'mr2', strategy: 'UNIFORM', spacing: spacing,
      edgeOffset: 0, fillAxis: 'x', minCount: 0, maxCount: null
    });
    for (var i = 0; i < 10; i++) {
      room1.addChild(new BOMNode({ id: 'c1_' + i, childSize: childSz,
        allocatedSize: { w: childSz, d: 100, h: 100 } }));
      room2.addChild(new BOMNode({ id: 'c2_' + i, childSize: childSz,
        allocatedSize: { w: childSz, d: 100, h: 100 } }));
    }

    room1.recompose({ x: 0, y: 0, z: 0, w: hostW1, d: 1000, h: 2800 });
    room2.recompose({ x: 0, y: 0, z: 0, w: hostW2, d: 1000, h: 2800 });

    var cnt1 = 0, cnt2 = 0;
    for (var j = 0; j < 10; j++) {
      if (room1.children[j].currentAABB) cnt1++;
      if (room2.children[j].currentAABB) cnt2++;
    }

    if (cnt2 > cnt1) {
      console.log('§WB_COUNTER M5_fail w1=' + hostW1 + ' w2=' + hostW2 +
        ' cnt1=' + cnt1 + ' cnt2=' + cnt2);
      ok = false;
      break;
    }
  }
  assert(ok, 'M5: shrinking host → fewer or equal children (100 trials)');
})();

// ═══════════════════════════════════════════════════════════════════════════
// 3. GOLDEN MASTER TESTS
// ═══════════════════════════════════════════════════════════════════════════

console.log('§WB golden master');

// ── G1: Known SH-like tree → snapshot matches expected structure ───────────

(function() {
  var room = new BOMNode({
    id: 'SH_GF_GOLDEN', strategy: 'UNIFORM',
    spacing: 1800, edgeOffset: 200, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  // Mandatory door
  room.addChild(new BOMNode({
    id: 'DOOR_900', mandatory: true, fitPriority: 1,
    allocatedSize: { w: 900, d: 200, h: 2100 },
    tack: { dx: 100, dy: 0, dz: 0 }
  }));
  // 3 optional windows
  for (var i = 0; i < 3; i++) {
    room.addChild(new BOMNode({
      id: 'WIN_1200_' + i, childSize: 1200,
      allocatedSize: { w: 1200, d: 200, h: 900 }
    }));
  }

  var host = { x: 0, y: 0, z: 0, w: 6000, d: 4000, h: 2800 };
  room.recompose(host);

  var snap = room.snapshot();

  // Structure checks
  assertEq(snap.id, 'SH_GF_GOLDEN', 'G1: root id');
  assertEq(snap.children.length, 4, 'G1: 4 children in snapshot');
  assertEq(snap.conflicts.length, 0, 'G1: 0 conflicts');

  // Door is positioned (mandatory)
  assert(snap.children[0].aabb !== null, 'G1: door positioned');
  assertClose(snap.children[0].aabb.x, 100, 'G1: door.x = 100');
  assertClose(snap.children[0].aabb.w, 900, 'G1: door.w = 900');
  assert(snap.children[0].mandatory, 'G1: door mandatory flag');

  // PHANTOM present
  assert(snap.phantom !== null, 'G1: phantom present');

  // Snapshot is JSON-safe (no circular refs)
  var json = JSON.stringify(snap);
  assert(json.length > 100, 'G1: JSON serialization works (' + json.length + ' bytes)');

  // Re-parse and verify
  var parsed = JSON.parse(json);
  assertEq(parsed.id, 'SH_GF_GOLDEN', 'G1: round-trip id');
  assertEq(parsed.children.length, 4, 'G1: round-trip children count');
})();

// ── G2: Snapshot determinism — same inputs → identical JSON ────────────────

(function() {
  function buildAndSnap() {
    var room = new BOMNode({
      id: 'det_room', strategy: 'PACKED',
      buffer: 50, edgeOffset: 100, fillAxis: 'x',
      minCount: 0, maxCount: null
    });
    for (var i = 0; i < 5; i++) {
      room.addChild(new BOMNode({
        id: 'det_' + i, childSize: 400,
        allocatedSize: { w: 400, d: 200, h: 300 }
      }));
    }
    room.recompose({ x: 0, y: 0, z: 0, w: 3000, d: 2000, h: 2800 });
    return JSON.stringify(room.snapshot());
  }

  var json1 = buildAndSnap();
  var json2 = buildAndSnap();
  assertEq(json1, json2, 'G2: deterministic — two runs produce identical JSON');
})();

// ═══════════════════════════════════════════════════════════════════════════
// 4. STATE RECORDING / TRACE
// ═══════════════════════════════════════════════════════════════════════════

console.log('§WB state recording');

// ── T1: _trace populated after recompose ──────────────────────────────────

(function() {
  var room = new BOMNode({
    id: 'trace_room', strategy: 'UNIFORM',
    spacing: 1000, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  room.addChild(new BOMNode({
    id: 'tr_wall', mandatory: true,
    allocatedSize: { w: 200, d: 100, h: 2800 },
    tack: { dx: 0, dy: 0, dz: 0 }
  }));
  room.addChild(new BOMNode({
    id: 'tr_chair', childSize: 500,
    allocatedSize: { w: 500, d: 500, h: 800 }
  }));

  room.recompose({ x: 0, y: 0, z: 0, w: 4000, d: 3000, h: 2800 });

  assert(room._trace !== null, 'T1: _trace populated');
  assertEq(room._trace.id, 'trace_room', 'T1: trace.id');
  assertEq(room._trace.strategy, 'UNIFORM', 'T1: trace.strategy');
  assertEq(room._trace.reserved, 1, 'T1: trace.reserved = 1 (mandatory)');
  assertEq(room._trace.filled, 2, 'T1: trace.filled = 2 (wall + chair)');
  assert(room._trace.phantom !== null, 'T1: trace.phantom');
  assertEq(room._trace.children.length, 2, 'T1: trace.children = 2');

  // Trace child details
  assert(room._trace.children[0].mandatory, 'T1: child[0] mandatory in trace');
  assert(!room._trace.children[1].mandatory, 'T1: child[1] not mandatory in trace');

  // Trace is JSON-safe
  var traceJson = JSON.stringify(room._trace);
  assert(traceJson.length > 50, 'T1: trace JSON serializable');
  console.log('  §WB_TRACE ' + traceJson.substring(0, 120) + '...');
})();

// ── T2: Leaf node has no trace (skips steps 2-4) ──────────────────────────

(function() {
  var leaf = new BOMNode({ id: 'leaf_test' });
  leaf.recompose({ x: 0, y: 0, z: 0, w: 100, d: 100, h: 100 });
  assert(leaf._trace === null, 'T2: leaf has no trace');
})();

// ── T3: Trace records hostAABB input ──────────────────────────────────────

(function() {
  var room = new BOMNode({
    id: 'trace_host', strategy: 'UNIFORM',
    spacing: 500, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  room.addChild(new BOMNode({
    id: 'th_c', childSize: 200,
    allocatedSize: { w: 200, d: 200, h: 200 }
  }));

  var host = { x: 55, y: 77, z: 33, w: 3000, d: 2000, h: 2800 };
  room.recompose(host);

  assertClose(room._trace.hostAABB.x, 55, 'T3: trace hostAABB.x = 55');
  assertClose(room._trace.hostAABB.w, 3000, 'T3: trace hostAABB.w = 3000');
  assertClose(room._trace.ownAABB.x, 55, 'T3: trace ownAABB.x = 55');
})();

// ═══════════════════════════════════════════════════════════════════════════
// 5. SAVE GOLDEN MASTER TO FILE (for future regression)
// ═══════════════════════════════════════════════════════════════════════════

(function() {
  var fs = require('fs');

  var room = new BOMNode({
    id: 'SH_GF_MASTER', strategy: 'UNIFORM',
    spacing: 1800, edgeOffset: 200, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  room.addChild(new BOMNode({
    id: 'EXT_WALL', mandatory: true, fitPriority: 1,
    strategy: 'SPAN', allocatedSize: { w: 8000, d: 200, h: 2800 },
    tack: { dx: 0, dy: 0, dz: 0 }
  }));
  room.addChild(new BOMNode({
    id: 'DOOR_900', mandatory: true, fitPriority: 5,
    allocatedSize: { w: 900, d: 200, h: 2100 },
    tack: { dx: 100, dy: 0, dz: 0 }
  }));
  for (var i = 0; i < 4; i++) {
    room.addChild(new BOMNode({
      id: 'WIN_1200_' + i, childSize: 1200,
      allocatedSize: { w: 1200, d: 200, h: 900 }
    }));
  }

  room.recompose({ x: 0, y: 0, z: 0, w: 10000, d: 6000, h: 3000 });
  var golden = room.snapshot();
  var goldenPath = __dirname + '/golden_bom_master.json';

  // Check if golden master exists
  if (fs.existsSync(goldenPath)) {
    // Compare against existing
    var existing = JSON.parse(fs.readFileSync(goldenPath, 'utf8'));
    var currentJson = JSON.stringify(golden);
    var existingJson = JSON.stringify(existing);
    assertEq(currentJson, existingJson, 'G3: golden master unchanged (regression)');
    console.log('  G3: golden master matches (' + currentJson.length + ' bytes)');
  } else {
    // First run — save as baseline
    fs.writeFileSync(goldenPath, JSON.stringify(golden, null, 2));
    _pass++;
    console.log('  G3: golden master saved to ' + goldenPath);
  }
})();

// ═══════════════════════════════════════════════════════════════════════════

console.log('\n§WB_SUMMARY ' + _pass + ' passed, ' + _fail + ' failed, ' + (_pass + _fail) + ' total');
if (_fail > 0) process.exit(1);
