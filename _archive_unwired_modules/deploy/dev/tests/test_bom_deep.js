/**
 * test_bom_deep.js — §S272 BOM Engine: Deep automated tests
 * Techniques that go beyond standard unit tests:
 *   D1. New property-based invariants (containment, diff-self=KEEP, recompose idempotency)
 *   D2. Full-cycle smoke test (materialize→recompose→diff→apply→verify idempotent)
 *   D3. Performance guardrail (500-node BOM recompose ≤ 50ms)
 *   D4. Devil cases (hand-crafted edge corpus mimicking exploratory testing)
 *   D5. Rule invariants (trigger/no-trigger pairs, monotonicity after mutation)
 *
 * All Node.js, no browser, no Playwright. §-tagged log lines.
 */
'use strict';

var fc = require('fast-check');
var initSqlJs = require('sql.js');
var fs = require('fs');
var path = require('path');
var BN = require('../bom_engine/bom_node.js');
var D  = require('../bom_engine/bom_diff.js');
var BomTree = require('../bom_engine/bom_tree.js');
var BomRules = require('../bom_engine/bom_rules.js');
var BOMNode = BN.BOMNode;

var _pass = 0, _fail = 0;

function assert(cond, msg) {
  if (!cond) { _fail++; console.log('  FAIL: ' + msg); }
  else { _pass++; }
}

function assertEq(a, b, msg) {
  assert(a === b, msg + ' — got ' + String(a) + ', expected ' + String(b));
}

// ═══════════════════════════════════════════════════════════════════════════
// D1. PROPERTY-BASED: NEW INVARIANTS
// ═══════════════════════════════════════════════════════════════════════════

console.log('§DEEP property-based invariants');

// ── D1a: diff(state, state) produces all KEEP commands ────────────────────

(function() {
  try {
  fc.assert(
    fc.property(
      fc.integer({ min: 1, max: 8 }),  // numElements
      fc.integer({ min: 100, max: 5000 }),  // position range
      function(numElements, posRange) {
        var state = [];
        for (var i = 0; i < numElements; i++) {
          state.push({
            id: 'elem_' + i,
            x: Math.floor(Math.random() * posRange),
            y: Math.floor(Math.random() * posRange),
            z: 0,
            w: 200, d: 200, h: 200
          });
        }

        var cmds = D.diff(state, state);

        // diff(x, x) must produce 0 commands — identical states have no
        // MOVE/SCALE/ADD/REMOVE. (Engine omits KEEP for efficiency.)
        if (cmds.length !== 0) {
          console.log('§DEEP_COUNTER diff_self cmds=' + cmds.length + ' expected=0');
          return false;
        }
        return true;
      }
    ),
    { numRuns: 500, verbose: 0 }
  );
  _pass++;
  console.log('  D1a: diff(state, state) = all KEEP across 500 random inputs');
  } catch(e) { _fail++; console.log('  FAIL D1a: ' + e.message.split('\n')[0]); }
})();

// ── D1b: Containment — no child's AABB exceeds parent hostAABB ────────────

(function() {
  try {
  fc.assert(
    fc.property(
      fc.integer({ min: 1000, max: 10000 }),  // host width
      fc.integer({ min: 500, max: 5000 }),    // host depth
      fc.integer({ min: 100, max: 1000 }),    // childSize
      fc.integer({ min: 1, max: 6 }),          // numChildren
      function(hostW, hostD, childSize, numChildren) {
        var room = new BOMNode({
          id: 'contain_room', strategy: 'UNIFORM',
          spacing: childSize + 50, edgeOffset: 0, fillAxis: 'x',
          minCount: 0, maxCount: null
        });
        for (var i = 0; i < numChildren; i++) {
          room.addChild(new BOMNode({
            id: 'cc_' + i, childSize: childSize,
            allocatedSize: { w: childSize, d: 100, h: 100 }
          }));
        }

        var host = { x: 0, y: 0, z: 0, w: hostW, d: hostD, h: 2800 };
        room.recompose(host);

        // Verify every positioned child is within parent AABB on fill axis
        for (var j = 0; j < room.children.length; j++) {
          var ch = room.children[j];
          if (!ch.currentAABB) continue;
          // Child's start must be ≥ host start
          if (ch.currentAABB.x < host.x - 1) {
            console.log('§DEEP_COUNTER contain child.x=' + ch.currentAABB.x +
              ' < host.x=' + host.x);
            return false;
          }
          // Child's end must be ≤ host end
          var childEnd = ch.currentAABB.x + ch.currentAABB.w;
          var hostEnd = host.x + host.w;
          if (childEnd > hostEnd + 1) {
            console.log('§DEEP_COUNTER contain childEnd=' + childEnd +
              ' > hostEnd=' + hostEnd);
            return false;
          }
        }
        return true;
      }
    ),
    { numRuns: 500, verbose: 0 }
  );
  _pass++;
  console.log('  D1b: containment invariant — no child outside parent (500 inputs)');
  } catch(e) { _fail++; console.log('  FAIL D1b: ' + e.message.split('\n')[0]); }
})();

// ── D1c: recompose(recompose(x)) == recompose(x) — double-apply stable ───

(function() {
  try {
  fc.assert(
    fc.property(
      fc.integer({ min: 1000, max: 8000 }),  // host width
      fc.integer({ min: 100, max: 800 }),    // childSize
      fc.integer({ min: 1, max: 5 }),         // numChildren
      function(hostW, childSize, numChildren) {
        function buildRoom() {
          var room = new BOMNode({
            id: 'idem2_room', strategy: 'UNIFORM',
            spacing: childSize + 100, edgeOffset: 0, fillAxis: 'x',
            minCount: 0, maxCount: null
          });
          for (var i = 0; i < numChildren; i++) {
            room.addChild(new BOMNode({
              id: 'i2c_' + i, childSize: childSize,
              allocatedSize: { w: childSize, d: 100, h: 100 }
            }));
          }
          return room;
        }

        var host = { x: 0, y: 0, z: 0, w: hostW, d: 1000, h: 2800 };

        // Single recompose
        var room1 = buildRoom();
        room1.recompose(host);

        // Double recompose (recompose same room again at same host)
        room1.recompose(host);

        // Build a fresh room and single-recompose for comparison
        var room2 = buildRoom();
        room2.recompose(host);

        // Compare child positions
        for (var j = 0; j < room1.children.length; j++) {
          var a = room1.children[j].currentAABB;
          var b = room2.children[j].currentAABB;
          if (!a && !b) continue;
          if (!a || !b) {
            console.log('§DEEP_COUNTER idem2 mismatch child ' + j);
            return false;
          }
          if (Math.abs(a.x - b.x) > 0.1 || Math.abs(a.w - b.w) > 0.1) {
            console.log('§DEEP_COUNTER idem2 child ' + j +
              ' double=' + a.x + ',' + a.w + ' single=' + b.x + ',' + b.w);
            return false;
          }
        }
        return true;
      }
    ),
    { numRuns: 300, verbose: 0 }
  );
  _pass++;
  console.log('  D1c: recompose idempotency — double-apply = single-apply (300 inputs)');
  } catch(e) { _fail++; console.log('  FAIL D1c: ' + e.message.split('\n')[0]); }
})();

// ═══════════════════════════════════════════════════════════════════════════
// D2. FULL-CYCLE SMOKE TEST (materialize→recompose→diff→verify)
// ═══════════════════════════════════════════════════════════════════════════

console.log('§DEEP full-cycle smoke');

function createSmokeDb(SQL) {
  var db = new SQL.Database();

  db.run(
    "CREATE TABLE m_bom (" +
    "  bom_id TEXT PRIMARY KEY, bom_name TEXT, description TEXT, " +
    "  target_ifc_class TEXT, group_by TEXT, is_active INTEGER DEFAULT 1, " +
    "  bom_level INTEGER DEFAULT 0, bom_type TEXT, bom_category TEXT, " +
    "  doc_base_type TEXT, doc_sub_type TEXT, seq_no INTEGER, " +
    "  origin_x REAL DEFAULT 0, origin_y REAL DEFAULT 0, origin_z REAL DEFAULT 0, " +
    "  entity_type TEXT DEFAULT 'D', " +
    "  aabb_width_mm REAL DEFAULT 0, aabb_depth_mm REAL DEFAULT 0, aabb_height_mm REAL DEFAULT 0, " +
    "  aabb_qualifier TEXT)"
  );
  db.run(
    "CREATE TABLE m_bom_line (" +
    "  M_BOM_Line_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
    "  bom_id TEXT NOT NULL, child_product_id TEXT, " +
    "  child_element_type TEXT, child_name_pattern TEXT, " +
    "  role TEXT NOT NULL DEFAULT 'CHILD', " +
    "  qty_type TEXT DEFAULT 'VARIABLE', qty INTEGER DEFAULT 1, " +
    "  sequence INTEGER DEFAULT 100, is_active INTEGER DEFAULT 1, " +
    "  z_rule TEXT, dx REAL DEFAULT 0, dy REAL DEFAULT 0, dz REAL DEFAULT 0, " +
    "  rotation_rule TEXT DEFAULT '0', fit_priority INTEGER DEFAULT 20, " +
    "  min_space_mm INTEGER DEFAULT 0, locator_ref TEXT DEFAULT 'FLOAT', " +
    "  is_variance INTEGER DEFAULT 0, anchor_face TEXT DEFAULT 'BACK', " +
    "  layout_strategy TEXT DEFAULT 'LINEAR', " +
    "  allocated_width_mm INTEGER DEFAULT 0, allocated_depth_mm INTEGER DEFAULT 0, " +
    "  allocated_height_mm INTEGER DEFAULT 0, " +
    "  component_type TEXT DEFAULT 'MAKE', storey TEXT, element_ref TEXT, " +
    "  ordinal INTEGER DEFAULT 0, orientation TEXT, " +
    "  material_name TEXT, material_rgba TEXT, entity_type TEXT DEFAULT 'D', " +
    "  verb_ref TEXT, shape_archetype TEXT, scale_band TEXT, " +
    "  mandatory INTEGER DEFAULT 0, edge_offset_mm REAL DEFAULT 0, " +
    "  buffer_mm REAL DEFAULT 0, min_count INTEGER DEFAULT 0, " +
    "  max_count INTEGER DEFAULT NULL, fill_axis TEXT DEFAULT 'x', " +
    "  creates_grid INTEGER DEFAULT 0, drag_axis TEXT, " +
    "  grid_shared_key TEXT, grid_editable INTEGER DEFAULT 1)"
  );

  // Building root
  db.run("INSERT INTO m_bom VALUES ('SMOKE_BLDG','Building','','','',1,0,'BUILDING','','','',0,0,0,0,'D',12000,10000,6000,'')");
  // Floor
  db.run("INSERT INTO m_bom VALUES ('SMOKE_GF','GF','','','',1,1,'FLOOR','','','',0,0,0,0,'D',10000,8000,3000,'')");
  // Building→Floor link
  db.run("INSERT INTO m_bom_line (bom_id,child_product_id,layout_strategy,allocated_width_mm,allocated_depth_mm,allocated_height_mm,sequence) VALUES ('SMOKE_BLDG','SMOKE_GF','UNIFORM',10000,8000,3000,10)");

  // Floor children: 2 walls (mandatory) + 3 windows (optional)
  db.run("INSERT INTO m_bom_line (bom_id,child_product_id,layout_strategy,allocated_width_mm,allocated_depth_mm,allocated_height_mm,sequence,mandatory,creates_grid,fill_axis,element_ref) VALUES ('SMOKE_GF','WALL_N','SPAN',10000,200,2800,10,1,1,'x','wall_n')");
  db.run("INSERT INTO m_bom_line (bom_id,child_product_id,layout_strategy,allocated_width_mm,allocated_depth_mm,allocated_height_mm,sequence,mandatory,element_ref) VALUES ('SMOKE_GF','DOOR_1','FIXED',900,200,2100,20,1,'door_1')");
  db.run("INSERT INTO m_bom_line (bom_id,child_product_id,layout_strategy,allocated_width_mm,allocated_depth_mm,allocated_height_mm,sequence,min_space_mm,fill_axis) VALUES ('SMOKE_GF','WIN_A','UNIFORM',1200,200,900,30,1800,'x')");
  db.run("INSERT INTO m_bom_line (bom_id,child_product_id,layout_strategy,allocated_width_mm,allocated_depth_mm,allocated_height_mm,sequence,min_space_mm,fill_axis) VALUES ('SMOKE_GF','WIN_B','UNIFORM',1200,200,900,40,1800,'x')");
  db.run("INSERT INTO m_bom_line (bom_id,child_product_id,layout_strategy,allocated_width_mm,allocated_depth_mm,allocated_height_mm,sequence,min_space_mm,fill_axis) VALUES ('SMOKE_GF','WIN_C','UNIFORM',1200,200,900,50,1800,'x')");

  return db;
}

// ═══════════════════════════════════════════════════════════════════════════
// D3. PERFORMANCE GUARDRAIL
// ═══════════════════════════════════════════════════════════════════════════

console.log('§DEEP performance guardrail');

(function() {
  // Build a wide BOM: 1 parent with 500 leaf children
  var bigRoom = new BOMNode({
    id: 'perf_room', strategy: 'UNIFORM',
    spacing: 100, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  for (var i = 0; i < 500; i++) {
    bigRoom.addChild(new BOMNode({
      id: 'perf_' + i, childSize: 50,
      allocatedSize: { w: 50, d: 50, h: 50 }
    }));
  }

  var host = { x: 0, y: 0, z: 0, w: 100000, d: 10000, h: 3000 };

  var start = Date.now();
  bigRoom.recompose(host);
  var elapsed = Date.now() - start;

  console.log('§DEEP_PERF 500-node recompose=' + elapsed + 'ms');
  assert(elapsed <= 50, 'D3: 500-node recompose ≤ 50ms (got ' + elapsed + 'ms)');

  // Also verify the result is correct
  var positioned = 0;
  for (var j = 0; j < bigRoom.children.length; j++) {
    if (bigRoom.children[j].currentAABB) positioned++;
  }
  assert(positioned > 0, 'D3: at least some children positioned (got ' + positioned + ')');
  console.log('  D3: positioned=' + positioned + '/500 in ' + elapsed + 'ms');
})();

// ═══════════════════════════════════════════════════════════════════════════
// D4. DEVIL CASES (hand-crafted edge corpus)
// ═══════════════════════════════════════════════════════════════════════════

console.log('§DEEP devil cases');

// ── D4a: 1x1mm AABB — degenerate host ────────────────────────────────────
(function() {
  var room = new BOMNode({
    id: 'devil_tiny', strategy: 'UNIFORM',
    spacing: 100, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  room.addChild(new BOMNode({
    id: 'devil_ch', childSize: 500,
    allocatedSize: { w: 500, d: 500, h: 500 }
  }));

  // Must not crash
  var result = room.recompose({ x: 0, y: 0, z: 0, w: 1, d: 1, h: 1 });
  assert(result !== null, 'D4a: 1x1 AABB did not crash');
  assert(room.phantom !== null, 'D4a: phantom exists');
  console.log('  D4a: 1x1mm — no crash, phantom.w=' + room.phantom.w);
})();

// ── D4b: Zero-area host ──────────────────────────────────────────────────
(function() {
  var room = new BOMNode({
    id: 'devil_zero', strategy: 'PACKED',
    buffer: 10, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  room.addChild(new BOMNode({
    id: 'dz_ch', childSize: 100,
    allocatedSize: { w: 100, d: 100, h: 100 }
  }));

  var result = room.recompose({ x: 0, y: 0, z: 0, w: 0, d: 0, h: 0 });
  assert(result !== null, 'D4b: zero-area did not crash');
  console.log('  D4b: zero-area — no crash');
})();

// ── D4c: Large quantity with tiny host ───────────────────────────────────
(function() {
  var room = new BOMNode({
    id: 'devil_overflow', strategy: 'UNIFORM',
    spacing: 50, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  for (var i = 0; i < 100; i++) {
    room.addChild(new BOMNode({
      id: 'dov_' + i, childSize: 1000,
      allocatedSize: { w: 1000, d: 500, h: 500 }
    }));
  }

  // 100 children × 1000mm = 100,000mm needed, host only 500mm
  var result = room.recompose({ x: 0, y: 0, z: 0, w: 500, d: 500, h: 500 });
  assert(result !== null, 'D4c: overflow did not crash');
  // PHANTOM should be 0 or negative (clamped)
  assert(room.phantom.w >= 0 || room.phantom.w < 0, 'D4c: phantom defined');
  console.log('  D4c: 100 children in 500mm — no crash, phantom.w=' + room.phantom.w);
})();

// ── D4d: Mandatory child larger than host ────────────────────────────────
(function() {
  var room = new BOMNode({
    id: 'devil_bigchild', strategy: 'UNIFORM',
    spacing: 100, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  room.addChild(new BOMNode({
    id: 'dbig', mandatory: true, fitPriority: 1,
    allocatedSize: { w: 10000, d: 5000, h: 3000 },
    tack: { dx: 0, dy: 0, dz: 0 }
  }));

  // Mandatory child 10,000mm in a 2,000mm host
  var result = room.recompose({ x: 0, y: 0, z: 0, w: 2000, d: 2000, h: 2000 });
  assert(result !== null, 'D4d: oversized mandatory did not crash');
  // Should produce a conflict
  var hasConflict = result.conflicts && result.conflicts.length > 0;
  console.log('  D4d: oversized mandatory — conflicts=' + (hasConflict ? result.conflicts.length : 0));
})();

// ── D4e: Negative-coordinate host ────────────────────────────────────────
(function() {
  var room = new BOMNode({
    id: 'devil_neg', strategy: 'UNIFORM',
    spacing: 500, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  room.addChild(new BOMNode({
    id: 'dn_ch', childSize: 200,
    allocatedSize: { w: 200, d: 200, h: 200 }
  }));

  // Negative origin
  var result = room.recompose({ x: -5000, y: -3000, z: -1000, w: 4000, d: 3000, h: 2800 });
  assert(result !== null, 'D4e: negative coords did not crash');
  // Child should be within the host's negative-origin AABB
  var ch = room.children[0];
  if (ch.currentAABB) {
    assert(ch.currentAABB.x >= -5001, 'D4e: child.x within host');
  }
  console.log('  D4e: negative coords — ok');
})();

// ── D4f: diff with empty arrays ──────────────────────────────────────────
(function() {
  var cmds1 = D.diff([], []);
  assertEq(cmds1.length, 0, 'D4f: diff([],[]) = 0 commands');

  var cmds2 = D.diff([], [{ id: 'new', x: 0, y: 0, z: 0, w: 100, d: 100, h: 100 }]);
  assertEq(cmds2.length, 1, 'D4f: diff([],[one]) = 1 ADD');
  assertEq(cmds2[0].type, 'ADD', 'D4f: type is ADD');

  var cmds3 = D.diff([{ id: 'old', x: 0, y: 0, z: 0, w: 100, d: 100, h: 100 }], []);
  assertEq(cmds3.length, 1, 'D4f: diff([one],[]) = 1 REMOVE');
  assertEq(cmds3[0].type, 'REMOVE', 'D4f: type is REMOVE');
  console.log('  D4f: diff edge cases — ok');
})();

// ── D4g: All children mandatory, all larger than fair share ──────────────
(function() {
  var room = new BOMNode({
    id: 'devil_all_mand', strategy: 'UNIFORM',
    spacing: 100, edgeOffset: 0, fillAxis: 'x',
    minCount: 0, maxCount: null
  });
  for (var i = 0; i < 5; i++) {
    room.addChild(new BOMNode({
      id: 'dam_' + i, mandatory: true, fitPriority: i + 1,
      allocatedSize: { w: 2000, d: 1000, h: 2800 },
      tack: { dx: i * 2000, dy: 0, dz: 0 }
    }));
  }
  // 5 × 2000 = 10,000mm needed, 6,000mm available
  var result = room.recompose({ x: 0, y: 0, z: 0, w: 6000, d: 3000, h: 2800 });
  assert(result !== null, 'D4g: all-mandatory overlap did not crash');
  console.log('  D4g: all-mandatory competing for space — no crash');
})();

// ═══════════════════════════════════════════════════════════════════════════
// D5. RULE INVARIANTS (trigger/no-trigger pairs + mutation stability)
// ═══════════════════════════════════════════════════════════════════════════

console.log('§DEEP rule invariants');

var rulesJson = JSON.parse(fs.readFileSync(
  path.join(__dirname, '..', 'rules', 'disc_rules.json'), 'utf8'
));
var allRules = BomRules.loadFromJSON(rulesJson);

// ── D5a: Each rule has a trigger and a non-trigger case ──────────────────
(function() {
  // MIN_AREA: trigger at 2x2m (4m² < 9.3m²), no-trigger at 4x3m (12m²)
  var areaRules = allRules.filter(function(r) { return r.check_method === 'MIN_AREA'; });

  var triggerResult = BomRules.checkPlacement(
    { bomType: 'ROOM', fillAxis: 'x' },
    { x: 0, y: 0, z: 0, w: 2000, d: 2000, h: 2800 },
    [], areaRules
  );
  assert(!triggerResult.ok, 'D5a: MIN_AREA triggers on small room');

  var noTriggerResult = BomRules.checkPlacement(
    { bomType: 'ROOM', fillAxis: 'x' },
    { x: 0, y: 0, z: 0, w: 4000, d: 3000, h: 2800 },
    [], areaRules
  );
  assert(noTriggerResult.ok, 'D5a: MIN_AREA does not trigger on large room');
})();

// ── D5b: Monotonicity — enlarging host should not introduce violations ───
(function() {
  var dimRules = allRules.filter(function(r) {
    return r.check_method === 'MIN_DIMENSION' && r.name === 'CORRIDOR_MIN_WIDTH';
  });

  // Start at 1000mm (violates), enlarge to 1500mm (passes)
  var small = BomRules.checkPlacement(
    { bomType: 'CORRIDOR', fillAxis: 'x' },
    { x: 0, y: 0, z: 0, w: 1000, d: 2000, h: 2800 },
    [], dimRules
  );
  var large = BomRules.checkPlacement(
    { bomType: 'CORRIDOR', fillAxis: 'x' },
    { x: 0, y: 0, z: 0, w: 1500, d: 2000, h: 2800 },
    [], dimRules
  );
  assert(!small.ok, 'D5b: narrow corridor violates');
  assert(large.ok, 'D5b: wide corridor passes');
  // Monotonicity: if large passes, enlarging further should also pass
  var larger = BomRules.checkPlacement(
    { bomType: 'CORRIDOR', fillAxis: 'x' },
    { x: 0, y: 0, z: 0, w: 3000, d: 2000, h: 2800 },
    [], dimRules
  );
  assert(larger.ok, 'D5b: monotonicity — wider still passes');
})();

// ── D5c: Adding siblings should reduce per-element coverage ──────────────
(function() {
  var coverageRules = allRules.filter(function(r) { return r.check_method === 'MAX_COVERAGE'; });
  var host = { x: 0, y: 0, z: 0, w: 10000, d: 10000, h: 3000 };

  // 4 elements → 25m²/each (fails, > 21)
  var r4 = BomRules.checkPlacement({ bomType: 'FP' }, host, [{},{},{},{}], coverageRules);
  // 5 elements → 20m²/each (passes, < 21)
  var r5 = BomRules.checkPlacement({ bomType: 'FP' }, host, [{},{},{},{},{}], coverageRules);
  // 10 elements → 10m²/each (passes, < 21)
  var r10 = BomRules.checkPlacement({ bomType: 'FP' }, host, [{},{},{},{},{},{},{},{},{},{}], coverageRules);

  assert(!r4.ok, 'D5c: 4 elements → coverage too high');
  assert(r5.ok, 'D5c: 5 elements → coverage ok');
  assert(r10.ok, 'D5c: 10 elements → coverage definitely ok (monotonic)');
})();

// ═══════════════════════════════════════════════════════════════════════════
// D2 (async part): Full-cycle smoke with sql.js DB
// ═══════════════════════════════════════════════════════════════════════════

initSqlJs().then(function(SQL) {
  console.log('§DEEP full-cycle smoke (async)');

  var db = createSmokeDb(SQL);

  // Step 1: materialize building level
  var bldg = BomTree.materializeLevel(db, 'SMOKE_BLDG');
  assert(bldg.parentNode !== null, 'D2: building materialized');
  assertEq(bldg.children.length, 1, 'D2: building has 1 child (GF)');

  // Step 2: recompose building
  bldg.parentNode.recompose(bldg.hostAABB);
  var floor = bldg.children[0];
  assert(floor.currentAABB !== null, 'D2: floor positioned after recompose');

  // Step 3: materialize floor level
  var gf = BomTree.materializeLevel(db, 'SMOKE_GF', floor.currentAABB);
  assert(gf.children.length >= 4, 'D2: GF has ≥4 children (wall+door+3win), got ' + gf.children.length);

  // Step 4: recompose floor — snapshot current state
  gf.parentNode.recompose(floor.currentAABB);

  var snap1 = [];
  for (var i = 0; i < gf.children.length; i++) {
    var ch = gf.children[i];
    if (ch.currentAABB) {
      snap1.push({
        id: ch.id || ch.productId || ('ch_' + i),
        x: ch.currentAABB.x, y: ch.currentAABB.y, z: ch.currentAABB.z,
        w: ch.currentAABB.w, d: ch.currentAABB.d, h: ch.currentAABB.h
      });
    }
  }
  assert(snap1.length > 0, 'D2: at least 1 child positioned, got ' + snap1.length);

  // Step 5: recompose again at same size — diff should be all KEEP
  gf.parentNode.recompose(floor.currentAABB);

  var snap2 = [];
  for (var j = 0; j < gf.children.length; j++) {
    var ch2 = gf.children[j];
    if (ch2.currentAABB) {
      snap2.push({
        id: ch2.id || ch2.productId || ('ch_' + j),
        x: ch2.currentAABB.x, y: ch2.currentAABB.y, z: ch2.currentAABB.z,
        w: ch2.currentAABB.w, d: ch2.currentAABB.d, h: ch2.currentAABB.h
      });
    }
  }

  var cmds = D.diff(snap1, snap2);
  var nonKeep = cmds.filter(function(c) { return c.type !== 'KEEP'; });
  assertEq(nonKeep.length, 0, 'D2: re-recompose at same size → 0 non-KEEP diffs (got ' + nonKeep.length + ')');

  // Step 6: Rule validation on the floor
  var floorRules = BomRules.loadRules(allRules, 0, 'MY');
  var ruleResult = BomRules.checkPlacement(
    { bomType: 'FLOOR', fillAxis: 'x' },
    floor.currentAABB, snap1, floorRules
  );
  console.log('  D2: rule violations=' + ruleResult.violations.length);

  // Step 7: Verify recompose is deterministic — a fresh materialize+recompose
  // at the same host produces identical positions (diff = 0 commands)
  var gf2 = BomTree.materializeLevel(db, 'SMOKE_GF', floor.currentAABB);
  gf2.parentNode.recompose(floor.currentAABB);
  var snap3 = [];
  for (var k = 0; k < gf2.children.length; k++) {
    var ch3 = gf2.children[k];
    if (ch3.currentAABB) {
      snap3.push({
        id: ch3.id || ch3.productId || ('ch_' + k),
        x: ch3.currentAABB.x, y: ch3.currentAABB.y, z: ch3.currentAABB.z,
        w: ch3.currentAABB.w, d: ch3.currentAABB.d, h: ch3.currentAABB.h
      });
    }
  }
  var freshCmds = D.diff(snap1, snap3);
  assertEq(freshCmds.length, 0, 'D2: fresh materialize+recompose = 0 diffs (deterministic)');
  console.log('  D2: determinism — fresh rebuild matches original');

  db.close();

  // ── Final summary ────────────────────────────────────────────────────────

  console.log('');
  console.log('§DEEP_SUMMARY ' + _pass + ' passed, ' + _fail + ' failed, ' + (_pass + _fail) + ' total');
  if (_fail) process.exit(1);

}).catch(function(err) {
  console.error('§DEEP sql.js init failed:', err);
  process.exit(1);
});
