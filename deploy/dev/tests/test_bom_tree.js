/**
 * test_bom_tree.js — §S272 BOM Engine Phase 2
 * Tests for bom_tree.js — materializeLevel + getAffectedBranch
 * Issue: Prove DB→BOMNode bridge produces correct tree with real SQL
 */
'use strict';

var initSqlJs = require('sql.js');
var BomTree = require('../bom_engine/bom_tree.js');

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

/**
 * Create in-memory sql.js DB with BOM schema + test data.
 */
function createTestDb(SQL) {
  var db = new SQL.Database();

  // Schema — m_bom
  db.run(
    "CREATE TABLE m_bom (" +
    "  bom_id TEXT PRIMARY KEY, bom_name TEXT, description TEXT, " +
    "  target_ifc_class TEXT, group_by TEXT, is_active INTEGER DEFAULT 1, " +
    "  bom_level INTEGER DEFAULT 0, bom_type TEXT, bom_category TEXT, " +
    "  doc_base_type TEXT, doc_sub_type TEXT, seq_no INTEGER, " +
    "  origin_x REAL DEFAULT 0, origin_y REAL DEFAULT 0, origin_z REAL DEFAULT 0, " +
    "  entity_type TEXT DEFAULT 'D', " +
    "  aabb_width_mm REAL DEFAULT 0, aabb_depth_mm REAL DEFAULT 0, aabb_height_mm REAL DEFAULT 0, " +
    "  aabb_qualifier TEXT" +
    ")"
  );

  // Schema — m_bom_line (full Phase 2 schema)
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
    "  grid_shared_key TEXT, grid_editable INTEGER DEFAULT 1" +
    ")"
  );

  // ── Test data: SH_GF (Ground Floor) with 3 children ──

  // Parent BOM: ground floor, 8000×6000×3000mm
  db.run(
    "INSERT INTO m_bom (bom_id, bom_type, bom_level, origin_x, origin_y, origin_z, " +
    "  aabb_width_mm, aabb_depth_mm, aabb_height_mm) " +
    "VALUES ('SH_GF', 'FLOOR', 1, 0, 0, 0, 8000, 6000, 3000)"
  );

  // Child 1: exterior wall (mandatory, creates grid)
  db.run(
    "INSERT INTO m_bom_line (bom_id, child_product_id, layout_strategy, " +
    "  allocated_width_mm, allocated_depth_mm, allocated_height_mm, " +
    "  dx, dy, dz, sequence, mandatory, creates_grid, grid_editable, " +
    "  drag_axis, element_ref, fill_axis) " +
    "VALUES ('SH_GF', 'WALL_EXT_N', 'SPAN', " +
    "  8000, 200, 2800, 0, 0, 0, 10, 1, 1, 0, NULL, 'wall_ext_n_guid', 'x')"
  );

  // Child 2: window (optional, uniform distribution)
  db.run(
    "INSERT INTO m_bom_line (bom_id, child_product_id, layout_strategy, " +
    "  allocated_width_mm, allocated_depth_mm, allocated_height_mm, " +
    "  dx, dy, dz, sequence, min_space_mm, fill_axis) " +
    "VALUES ('SH_GF', 'WIN_1200x900', 'UNIFORM', " +
    "  1200, 200, 900, 0, 0, 0.9, 20, 1800, 'x')"
  );

  // Child 3: door (mandatory)
  db.run(
    "INSERT INTO m_bom_line (bom_id, child_product_id, layout_strategy, " +
    "  allocated_width_mm, allocated_depth_mm, allocated_height_mm, " +
    "  dx, dy, dz, sequence, mandatory, element_ref) " +
    "VALUES ('SH_GF', 'DOOR_900x2100', 'FIXED', " +
    "  900, 200, 2100, 0.1, 0, 0, 30, 1, 'door_gf_guid')"
  );

  // Child 4: PHANTOM (should be skipped)
  db.run(
    "INSERT INTO m_bom_line (bom_id, child_product_id, component_type, " +
    "  allocated_width_mm, allocated_depth_mm, allocated_height_mm, sequence) " +
    "VALUES ('SH_GF', 'PHANTOM_SH_GF', 'PHANTOM', 0, 0, 0, 999)"
  );

  // Root BOM: building
  db.run(
    "INSERT INTO m_bom (bom_id, bom_type, bom_level, " +
    "  aabb_width_mm, aabb_depth_mm, aabb_height_mm) " +
    "VALUES ('SH_BUILDING', 'BUILDING', 0, 12000, 10000, 6000)"
  );

  // Building → Floor link
  db.run(
    "INSERT INTO m_bom_line (bom_id, child_product_id, layout_strategy, " +
    "  allocated_width_mm, allocated_depth_mm, allocated_height_mm, sequence) " +
    "VALUES ('SH_BUILDING', 'SH_GF', 'UNIFORM', 8000, 6000, 3000, 10)"
  );

  return db;
}

// ── Run tests ──────────────────────────────────────────────────────────────

initSqlJs().then(function(SQL) {

  var db = createTestDb(SQL);

  // ── loadBom ──────────────────────────────────────────────────────────────

  console.log('§TREE loadBom');

  (function() {
    var bom = BomTree.loadBom(db, 'SH_GF');
    assert(bom !== null, 'loadBom: SH_GF found');
    assertEq(bom.bomId, 'SH_GF', 'loadBom: bomId');
    assertEq(bom.bomType, 'FLOOR', 'loadBom: bomType');
    assertClose(bom.aabbW, 8000, 'loadBom: aabbW');
    assertClose(bom.aabbD, 6000, 'loadBom: aabbD');
    assertClose(bom.aabbH, 3000, 'loadBom: aabbH');
  })();

  (function() {
    var bom = BomTree.loadBom(db, 'NONEXISTENT');
    assert(bom === null, 'loadBom: nonexistent → null');
  })();

  // ── materializeLevel ─────────────────────────────────────────────────────

  console.log('§TREE materializeLevel');

  (function() {
    var result = BomTree.materializeLevel(db, 'SH_GF');
    assert(result.parentNode !== null, 'materialize: parent exists');
    assertEq(result.parentNode.id, 'SH_GF', 'materialize: parent id');

    // 3 children (PHANTOM skipped)
    assertEq(result.children.length, 3, 'materialize: 3 children (PHANTOM skipped)');

    // Child order by sequence: wall(10), window(20), door(30)
    assertEq(result.children[0].id, 'WALL_EXT_N', 'child[0] = wall');
    assertEq(result.children[1].id, 'WIN_1200x900', 'child[1] = window');
    assertEq(result.children[2].id, 'DOOR_900x2100', 'child[2] = door');
  })();

  (function() {
    var result = BomTree.materializeLevel(db, 'SH_GF');

    // Wall properties
    var wall = result.children[0];
    assert(wall.mandatory, 'wall: mandatory');
    assertEq(wall.strategy, 'SPAN', 'wall: strategy = SPAN');
    assert(wall._createsGrid, 'wall: creates grid');
    assert(!wall._gridEditable, 'wall: grid NOT editable (display-only)');
    assertClose(wall.allocatedSize.w, 8000, 'wall: allocatedSize.w');

    // Window properties
    var win = result.children[1];
    assert(!win.mandatory, 'win: not mandatory');
    assertEq(win.strategy, 'UNIFORM', 'win: strategy = UNIFORM');
    assertClose(win.spacing, 1800, 'win: spacing = 1800');
    assertClose(win.allocatedSize.w, 1200, 'win: width = 1200');
    // dz = 0.9m → 900mm
    assertClose(win.tack.dz, 900, 'win: tack.dz = 900mm');

    // Door properties
    var door = result.children[2];
    assert(door.mandatory, 'door: mandatory');
    assertEq(door.strategy, 'FIXED', 'door: strategy = FIXED');
    assertEq(door._elementRef, 'door_gf_guid', 'door: elementRef');
    // dx = 0.1m → 100mm
    assertClose(door.tack.dx, 100, 'door: tack.dx = 100mm');
  })();

  (function() {
    // Host AABB from DB
    var result = BomTree.materializeLevel(db, 'SH_GF');
    assertClose(result.hostAABB.w, 8000, 'hostAABB.w from DB');
    assertClose(result.hostAABB.d, 6000, 'hostAABB.d from DB');
    assertClose(result.hostAABB.h, 3000, 'hostAABB.h from DB');
  })();

  (function() {
    // Host AABB override
    var customHost = { x: 100, y: 200, z: 0, w: 10000, d: 8000, h: 3500 };
    var result = BomTree.materializeLevel(db, 'SH_GF', customHost);
    assertClose(result.hostAABB.w, 10000, 'hostAABB override.w');
  })();

  (function() {
    // Nonexistent BOM
    var result = BomTree.materializeLevel(db, 'BOGUS');
    assert(result.parentNode === null, 'materialize bogus: null parent');
    assertEq(result.children.length, 0, 'materialize bogus: 0 children');
  })();

  // ── materialize + recompose integration ──────────────────────────────────

  console.log('§TREE materialize → recompose');

  (function() {
    var result = BomTree.materializeLevel(db, 'SH_GF');
    var r = result.parentNode.recompose(result.hostAABB);

    // Parent should have AABB
    assert(result.parentNode.currentAABB !== null, 'recompose: parent has AABB');

    // Mandatory children should be positioned
    var wall = result.children[0];
    var door = result.children[2];
    assert(wall.currentAABB !== null, 'recompose: wall positioned');
    assert(door.currentAABB !== null, 'recompose: door positioned');

    // PHANTOM computed
    assert(result.parentNode.phantom !== null, 'recompose: phantom computed');

    assertEq(r.conflicts.length, 0, 'recompose: no conflicts');
  })();

  // ── Parent-child wiring ──────────────────────────────────────────────────

  console.log('§TREE parent-child wiring');

  (function() {
    var result = BomTree.materializeLevel(db, 'SH_GF');

    // Children know parent
    for (var i = 0; i < result.children.length; i++) {
      assertEq(result.children[i].getParentBOM(), result.parentNode,
        'child[' + i + '] parent = parentNode');
    }

    // Parent knows children
    assertEq(result.parentNode.getChildren().length, 3, 'parent has 3 children');
  })();

  // ── getAffectedBranch ────────────────────────────────────────────────────

  console.log('§TREE getAffectedBranch');

  (function() {
    var result = BomTree.materializeLevel(db, 'SH_GF');

    // Simulate attach map: grid 'G1' attached to door guid
    var attachMap = {
      'G1': [{ guid: 'door_gf_guid' }],
      'G2': [{ guid: 'unknown_guid' }]
    };

    var affected = BomTree.getAffectedBranch(result.children, attachMap, 'G1');
    assertEq(affected.length, 1, 'affected: 1 parent');
    assertEq(affected[0].id, 'SH_GF', 'affected: parent is SH_GF');
  })();

  (function() {
    var result = BomTree.materializeLevel(db, 'SH_GF');

    // Unknown grid → no affected
    var affected = BomTree.getAffectedBranch(result.children, {}, 'G99');
    assertEq(affected.length, 0, 'affected: unknown grid → 0');
  })();

  (function() {
    var result = BomTree.materializeLevel(db, 'SH_GF');

    // Grid attached to wall (which has element_ref)
    var attachMap = { 'G3': [{ guid: 'wall_ext_n_guid' }] };
    var affected = BomTree.getAffectedBranch(result.children, attachMap, 'G3');
    assertEq(affected.length, 1, 'affected: wall → 1 parent');
  })();

  // ── listRoots ────────────────────────────────────────────────────────────

  console.log('§TREE listRoots');

  (function() {
    var roots = BomTree.listRoots(db);
    assert(roots.length >= 1, 'listRoots: at least 1');
    assertEq(roots[0].bom_id, 'SH_BUILDING', 'listRoots: SH_BUILDING');
  })();

  // ── 2-level materialize ──────────────────────────────────────────────────

  console.log('§TREE 2-level materialize');

  (function() {
    // Building → Floor
    var bldgResult = BomTree.materializeLevel(db, 'SH_BUILDING');
    assertEq(bldgResult.children.length, 1, '2L: building has 1 child (SH_GF)');

    // Recompose building level
    bldgResult.parentNode.recompose(bldgResult.hostAABB);

    // Floor child should have AABB
    var floor = bldgResult.children[0];
    assert(floor.currentAABB !== null, '2L: floor positioned');

    // Now materialize floor level using floor's AABB
    var floorResult = BomTree.materializeLevel(db, 'SH_GF', floor.currentAABB);
    assertEq(floorResult.children.length, 3, '2L: floor has 3 children');

    // Recompose floor level
    floorResult.parentNode.recompose(floor.currentAABB);

    // All children should be positioned
    for (var i = 0; i < floorResult.children.length; i++) {
      var ch = floorResult.children[i];
      if (ch.mandatory) {
        assert(ch.currentAABB !== null, '2L: mandatory ' + ch.id + ' positioned');
      }
    }
  })();

  // ── Summary ──────────────────────────────────────────────────────────────

  db.close();

  console.log('\n§TREE_SUMMARY ' + _pass + ' passed, ' + _fail + ' failed, ' + (_pass + _fail) + ' total');
  if (_fail > 0) process.exit(1);

}).catch(function(err) {
  console.error('sql.js init failed:', err);
  process.exit(1);
});
