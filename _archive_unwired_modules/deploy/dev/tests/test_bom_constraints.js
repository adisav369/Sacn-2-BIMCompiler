/**
 * test_bom_constraints.js — §S272 BOM Engine Phase 1
 * Tests for bom_constraints.js — fitCheck, overlapCheck, bufferCheck, mandatoryCheck, computePhantom
 * Issue: Prove constraint checks detect violations and compute PHANTOM correctly
 */
'use strict';

var C = require('../bom_engine/bom_constraints.js');

var _pass = 0, _fail = 0;

function assert(cond, msg) {
  if (!cond) { _fail++; console.log('  FAIL: ' + msg); }
  else { _pass++; }
}

function assertEq(a, b, msg) {
  assert(a === b, msg + ' — got ' + a + ', expected ' + b);
}

function assertClose(a, b, msg) {
  assert(Math.abs(a - b) < 0.1, msg + ' — got ' + a + ', expected ~' + b);
}

// ── fitCheck ───────────────────────────────────────────────────────────────

console.log('§CONST fitCheck');

(function() {
  // Child fits perfectly
  var r = C.fitCheck(
    { x: 0, y: 0, z: 0, w: 4000, d: 3000, h: 2800 },
    { x: 0, y: 0, z: 0, w: 4000, d: 3000, h: 2800 }
  );
  assert(r.ok, 'fitCheck: exact fit → ok');
})();

(function() {
  // Child inside host
  var r = C.fitCheck(
    { x: 100, y: 100, z: 0, w: 2000, d: 2000, h: 2800 },
    { x: 0, y: 0, z: 0, w: 4000, d: 3000, h: 2800 }
  );
  assert(r.ok, 'fitCheck: child inside → ok');
})();

(function() {
  // Child exceeds host on x_max
  var r = C.fitCheck(
    { x: 0, y: 0, z: 0, w: 5000, d: 3000, h: 2800 },
    { x: 0, y: 0, z: 0, w: 4000, d: 3000, h: 2800 }
  );
  assert(!r.ok, 'fitCheck: x overflow → conflict');
  assert(r.conflicts.indexOf('x_max') >= 0, 'fitCheck: x_max flagged');
})();

(function() {
  // Child below host origin
  var r = C.fitCheck(
    { x: -100, y: 0, z: 0, w: 2000, d: 2000, h: 2800 },
    { x: 0, y: 0, z: 0, w: 4000, d: 3000, h: 2800 }
  );
  assert(!r.ok, 'fitCheck: x_min below origin → conflict');
  assert(r.conflicts.indexOf('x_min') >= 0, 'fitCheck: x_min flagged');
})();

(function() {
  // Multiple axis violations
  var r = C.fitCheck(
    { x: -10, y: -10, z: -10, w: 5000, d: 4000, h: 3500 },
    { x: 0, y: 0, z: 0, w: 4000, d: 3000, h: 2800 }
  );
  assertEq(r.conflicts.length, 6, 'fitCheck: all 6 axes violated');
})();

// ── overlapCheck ───────────────────────────────────────────────────────────

console.log('§CONST overlapCheck');

(function() {
  // No overlap — side by side
  var r = C.overlapCheck([
    { id: 'a', x: 0, y: 0, z: 0, w: 1000, d: 1000, h: 2800 },
    { id: 'b', x: 1000, y: 0, z: 0, w: 1000, d: 1000, h: 2800 }
  ]);
  assert(r.ok, 'overlapCheck: side by side → ok');
})();

(function() {
  // Overlapping cubes
  var r = C.overlapCheck([
    { id: 'a', x: 0, y: 0, z: 0, w: 1000, d: 1000, h: 1000 },
    { id: 'b', x: 500, y: 500, z: 0, w: 1000, d: 1000, h: 1000 }
  ]);
  assert(!r.ok, 'overlapCheck: overlapping → conflict');
  assertEq(r.overlaps.length, 1, 'overlapCheck: 1 pair');
  assertEq(r.overlaps[0].a, 'a', 'overlapCheck: pair a');
  assertEq(r.overlaps[0].b, 'b', 'overlapCheck: pair b');
})();

(function() {
  // Three items, 2 overlapping
  var r = C.overlapCheck([
    { id: 'a', x: 0, y: 0, z: 0, w: 600, d: 600, h: 600 },
    { id: 'b', x: 500, y: 0, z: 0, w: 600, d: 600, h: 600 },
    { id: 'c', x: 2000, y: 0, z: 0, w: 600, d: 600, h: 600 }
  ]);
  assertEq(r.overlaps.length, 1, 'overlapCheck: only a-b overlap');
})();

(function() {
  // Empty / single — ok
  assert(C.overlapCheck([]).ok, 'overlapCheck: empty → ok');
  assert(C.overlapCheck([{ id: 'a', x: 0, y: 0, z: 0, w: 100, d: 100, h: 100 }]).ok, 'overlapCheck: single → ok');
})();

// ── bufferCheck ────────────────────────────────────────────────────────────

console.log('§CONST bufferCheck');

(function() {
  // Sufficient buffer
  var r = C.bufferCheck([
    { id: 'a', pos: 500, size: 1000 },
    { id: 'b', pos: 2000, size: 1000 }
  ], 200);
  // gap = 2000-500 - 1000/2 - 1000/2 = 500, need 200 → ok
  assert(r.ok, 'bufferCheck: 500mm gap for 200mm buffer → ok');
})();

(function() {
  // Insufficient buffer
  var r = C.bufferCheck([
    { id: 'a', pos: 500, size: 1000 },
    { id: 'b', pos: 1200, size: 1000 }
  ], 500);
  // gap = 1200 - 500 - 500 - 500 = -300 → deficit
  assert(!r.ok, 'bufferCheck: touching → violation');
  assertEq(r.violations.length, 1, 'bufferCheck: 1 violation');
})();

(function() {
  // buffer=0 → always ok
  var r = C.bufferCheck([
    { id: 'a', pos: 0, size: 100 },
    { id: 'b', pos: 50, size: 100 }
  ], 0);
  assert(r.ok, 'bufferCheck: buffer=0 → ok');
})();

(function() {
  // Single sibling → ok
  var r = C.bufferCheck([{ id: 'a', pos: 500, size: 1000 }], 200);
  assert(r.ok, 'bufferCheck: single → ok');
})();

(function() {
  // Unsorted input — should still work
  var r = C.bufferCheck([
    { id: 'c', pos: 5000, size: 1000 },
    { id: 'a', pos: 500, size: 1000 },
    { id: 'b', pos: 2000, size: 1000 }
  ], 200);
  assert(r.ok, 'bufferCheck: unsorted input → sorted internally → ok');
})();

// ── mandatoryCheck ─────────────────────────────────────────────────────────

console.log('§CONST mandatoryCheck');

(function() {
  var r = C.mandatoryCheck([
    { id: 'wall_ext', mandatory: true, present: true },
    { id: 'window', mandatory: false, present: true },
    { id: 'door', mandatory: true, present: true }
  ]);
  assert(r.ok, 'mandatoryCheck: all present → ok');
})();

(function() {
  var r = C.mandatoryCheck([
    { id: 'wall_ext', mandatory: true, present: true },
    { id: 'door', mandatory: true, present: false }
  ]);
  assert(!r.ok, 'mandatoryCheck: door missing → fail');
  assertEq(r.missing.length, 1, 'mandatoryCheck: 1 missing');
  assertEq(r.missing[0], 'door', 'mandatoryCheck: door is missing');
})();

(function() {
  // Optional missing is fine
  var r = C.mandatoryCheck([
    { id: 'plant', mandatory: false, present: false }
  ]);
  assert(r.ok, 'mandatoryCheck: optional missing → ok');
})();

(function() {
  // Empty list → ok
  assert(C.mandatoryCheck([]).ok, 'mandatoryCheck: empty → ok');
})();

// ── computePhantom ─────────────────────────────────────────────────────────

console.log('§CONST computePhantom');

(function() {
  // Full room, 2 children consume some space
  var r = C.computePhantom(
    { w: 6000, d: 4000, h: 2800 },
    [
      { w: 2000, d: 4000, h: 2800 },
      { w: 3000, d: 4000, h: 2800 }
    ]
  );
  assertClose(r.w, 1000, 'phantom.w = 6000 - 2000 - 3000');
  assertClose(r.d, 0, 'phantom.d = overfilled → clamped to 0');
})();

// Fix: recheck phantom d
(function() {
  var r = C.computePhantom(
    { w: 6000, d: 4000, h: 2800 },
    [
      { w: 2000, d: 1000, h: 2800 },
      { w: 3000, d: 1500, h: 2800 }
    ]
  );
  assertClose(r.w, 1000, 'phantom.w = 1000');
  assertClose(r.d, 1500, 'phantom.d = 4000 - 1000 - 1500');
  assertClose(r.h, 0, 'phantom.h = 2800 - 2800 - 2800 → clamped to 0');
})();

(function() {
  // No children → full phantom
  var r = C.computePhantom({ w: 5000, d: 3000, h: 2800 }, []);
  assertClose(r.w, 5000, 'phantom: no children → full w');
  assertClose(r.d, 3000, 'phantom: no children → full d');
  assertClose(r.h, 2800, 'phantom: no children → full h');
})();

(function() {
  // Exactly filled → phantom = 0
  var r = C.computePhantom(
    { w: 4000, d: 3000, h: 2800 },
    [{ w: 4000, d: 3000, h: 2800 }]
  );
  assertClose(r.w, 0, 'phantom: exact fill → 0');
  assertClose(r.d, 0, 'phantom: exact fill → 0');
  assertClose(r.h, 0, 'phantom: exact fill → 0');
})();

(function() {
  // Overfilled → clamped to 0
  var r = C.computePhantom(
    { w: 4000, d: 3000, h: 2800 },
    [{ w: 5000, d: 4000, h: 3000 }]
  );
  assertClose(r.w, 0, 'phantom: overfilled w → 0');
  assertClose(r.d, 0, 'phantom: overfilled d → 0');
  assertClose(r.h, 0, 'phantom: overfilled h → 0');
})();

// ── Summary ────────────────────────────────────────────────────────────────

console.log('\n§CONST_SUMMARY ' + _pass + ' passed, ' + _fail + ' failed, ' + (_pass + _fail) + ' total');
if (_fail > 0) process.exit(1);
