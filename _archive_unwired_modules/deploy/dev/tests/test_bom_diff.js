/**
 * test_bom_diff.js — §S272 BOM Engine Phase 1
 * Tests for bom_diff.js — diff engine
 * Issue: Prove diff produces correct KEEP/MOVE/SCALE/ADD/REMOVE commands
 */
'use strict';

var D = require('../bom_engine/bom_diff.js');

var _pass = 0, _fail = 0;

function assert(cond, msg) {
  if (!cond) { _fail++; console.log('  FAIL: ' + msg); }
  else { _pass++; }
}

function assertEq(a, b, msg) {
  assert(a === b, msg + ' — got ' + JSON.stringify(a) + ', expected ' + JSON.stringify(b));
}

// ── No changes ─────────────────────────────────────────────────────────────

console.log('§DIFF no changes');

(function() {
  var cmds = D.diff(
    [{ id: 'a', x: 0, y: 0, z: 0, w: 1000, d: 500, h: 2800 }],
    [{ id: 'a', x: 0, y: 0, z: 0, w: 1000, d: 500, h: 2800 }]
  );
  assertEq(cmds.length, 0, 'identical → no commands');
})();

// ── MOVE ───────────────────────────────────────────────────────────────────

console.log('§DIFF MOVE');

(function() {
  var cmds = D.diff(
    [{ id: 'win1', x: 1000, y: 0, z: 0, w: 1200, d: 200, h: 900 }],
    [{ id: 'win1', x: 2500, y: 0, z: 0, w: 1200, d: 200, h: 900 }]
  );
  assertEq(cmds.length, 1, 'MOVE: 1 command');
  assertEq(cmds[0].type, 'MOVE', 'MOVE: type');
  assertEq(cmds[0].id, 'win1', 'MOVE: id');
  assertEq(cmds[0].from.x, 1000, 'MOVE: from.x');
  assertEq(cmds[0].to.x, 2500, 'MOVE: to.x');
})();

// ── SCALE ──────────────────────────────────────────────────────────────────

console.log('§DIFF SCALE');

(function() {
  var cmds = D.diff(
    [{ id: 'wall1', x: 0, y: 0, z: 0, w: 4000, d: 200, h: 2800 }],
    [{ id: 'wall1', x: 0, y: 0, z: 0, w: 6000, d: 200, h: 2800 }]
  );
  assertEq(cmds.length, 1, 'SCALE: 1 command');
  assertEq(cmds[0].type, 'SCALE', 'SCALE: type');
  assertEq(cmds[0].from.w, 4000, 'SCALE: from.w');
  assertEq(cmds[0].to.w, 6000, 'SCALE: to.w');
})();

(function() {
  // Move + scale combined → SCALE command
  var cmds = D.diff(
    [{ id: 'slab', x: 0, y: 0, z: 0, w: 3000, d: 3000, h: 200 }],
    [{ id: 'slab', x: 500, y: 0, z: 0, w: 5000, d: 3000, h: 200 }]
  );
  assertEq(cmds[0].type, 'SCALE', 'MOVE+SCALE → SCALE command');
  assertEq(cmds[0].to.x, 500, 'SCALE carries new position');
})();

// ── ADD ────────────────────────────────────────────────────────────────────

console.log('§DIFF ADD');

(function() {
  var cmds = D.diff(
    [],
    [{ id: 'new1', x: 1000, y: 0, z: 0, w: 1200, d: 200, h: 900, productId: 'WIN_1200x900' }]
  );
  assertEq(cmds.length, 1, 'ADD: 1 command');
  assertEq(cmds[0].type, 'ADD', 'ADD: type');
  assertEq(cmds[0].productId, 'WIN_1200x900', 'ADD: productId');
})();

// ── REMOVE ─────────────────────────────────────────────────────────────────

console.log('§DIFF REMOVE');

(function() {
  var cmds = D.diff(
    [{ id: 'old1', x: 0, y: 0, z: 0, w: 1000, d: 500, h: 2800 }],
    []
  );
  assertEq(cmds.length, 1, 'REMOVE: 1 command');
  assertEq(cmds[0].type, 'REMOVE', 'REMOVE: type');
  assertEq(cmds[0].id, 'old1', 'REMOVE: id');
})();

// ── Sort order ─────────────────────────────────────────────────────────────

console.log('§DIFF sort order');

(function() {
  var cmds = D.diff(
    [
      { id: 'keep', x: 0, y: 0, z: 0, w: 100, d: 100, h: 100 },
      { id: 'move', x: 0, y: 0, z: 0, w: 100, d: 100, h: 100 },
      { id: 'remove', x: 0, y: 0, z: 0, w: 100, d: 100, h: 100 }
    ],
    [
      { id: 'keep', x: 0, y: 0, z: 0, w: 100, d: 100, h: 100 },
      { id: 'move', x: 500, y: 0, z: 0, w: 100, d: 100, h: 100 },
      { id: 'add', x: 1000, y: 0, z: 0, w: 100, d: 100, h: 100 }
    ]
  );
  assertEq(cmds.length, 3, 'mixed: 3 commands (keep excluded)');
  assertEq(cmds[0].type, 'REMOVE', 'sort: REMOVE first');
  assertEq(cmds[1].type, 'MOVE', 'sort: MOVE second');
  assertEq(cmds[2].type, 'ADD', 'sort: ADD last');
})();

// ── Idempotent ─────────────────────────────────────────────────────────────

console.log('§DIFF idempotent');

(function() {
  var current = [
    { id: 'a', x: 0, y: 0, z: 0, w: 1000, d: 500, h: 2800 },
    { id: 'b', x: 2000, y: 0, z: 0, w: 1000, d: 500, h: 2800 }
  ];
  var target = [
    { id: 'a', x: 500, y: 0, z: 0, w: 1000, d: 500, h: 2800 },
    { id: 'c', x: 3000, y: 0, z: 0, w: 1200, d: 500, h: 2800 }
  ];
  var cmds1 = D.diff(current, target);
  var cmds2 = D.diff(current, target);
  assertEq(cmds1.length, cmds2.length, 'idempotent: same length');
  for (var i = 0; i < cmds1.length; i++) {
    assertEq(cmds1[i].type, cmds2[i].type, 'idempotent: same type[' + i + ']');
    assertEq(cmds1[i].id, cmds2[i].id, 'idempotent: same id[' + i + ']');
  }
})();

// ── Empty ──────────────────────────────────────────────────────────────────

console.log('§DIFF empty');

(function() {
  assertEq(D.diff([], []).length, 0, 'empty→empty: no commands');
})();

// ── Summarize ──────────────────────────────────────────────────────────────

console.log('§DIFF summarize');

(function() {
  var cmds = [
    { type: 'REMOVE', id: 'a' },
    { type: 'MOVE', id: 'b' },
    { type: 'SCALE', id: 'c' },
    { type: 'ADD', id: 'd' },
    { type: 'ADD', id: 'e' }
  ];
  var s = D.summarize(cmds);
  assertEq(s.remove, 1, 'summarize: 1 remove');
  assertEq(s.move, 1, 'summarize: 1 move');
  assertEq(s.scale, 1, 'summarize: 1 scale');
  assertEq(s.add, 2, 'summarize: 2 add');
})();

// ── Position within tolerance ──────────────────────────────────────────────

console.log('§DIFF tolerance');

(function() {
  // Tiny position difference below tolerance → no MOVE
  var cmds = D.diff(
    [{ id: 'a', x: 1000, y: 0, z: 0, w: 500, d: 500, h: 500 }],
    [{ id: 'a', x: 1000.05, y: 0, z: 0, w: 500, d: 500, h: 500 }]
  );
  assertEq(cmds.length, 0, 'tolerance: 0.05mm diff → no command');
})();

(function() {
  // Just above tolerance → MOVE
  var cmds = D.diff(
    [{ id: 'a', x: 1000, y: 0, z: 0, w: 500, d: 500, h: 500 }],
    [{ id: 'a', x: 1001, y: 0, z: 0, w: 500, d: 500, h: 500 }]
  );
  assertEq(cmds.length, 1, 'tolerance: 1mm diff → MOVE');
})();

// ── Summary ────────────────────────────────────────────────────────────────

console.log('\n§DIFF_SUMMARY ' + _pass + ' passed, ' + _fail + ' failed, ' + (_pass + _fail) + ' total');
if (_fail > 0) process.exit(1);
