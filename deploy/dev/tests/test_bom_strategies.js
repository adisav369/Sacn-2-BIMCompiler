/**
 * test_bom_strategies.js — §S272 BOM Engine Phase 1
 * Tests for bom_strategies.js — 8 strategy functions
 * Issue: Prove each strategy computes correct positions and counts
 */
'use strict';

var S = require('../bom_engine/bom_strategies.js');

var _pass = 0, _fail = 0;

function assert(cond, msg) {
  if (!cond) { _fail++; console.log('  FAIL: ' + msg); }
  else { _pass++; }
}

function assertEq(a, b, msg) {
  assert(a === b, msg + ' — got ' + a + ', expected ' + b);
}

function assertClose(a, b, msg, tol) {
  tol = tol || 0.01;
  assert(Math.abs(a - b) < tol, msg + ' — got ' + a + ', expected ~' + b);
}

function assertArr(a, b, msg) {
  assert(a.length === b.length, msg + ' length — got ' + a.length + ', expected ' + b.length);
  for (var i = 0; i < Math.min(a.length, b.length); i++) {
    assertClose(a[i], b[i], msg + '[' + i + ']');
  }
}

// ── UNIFORM ────────────────────────────────────────────────────────────────

console.log('§STRAT UNIFORM');

(function() {
  // 3 windows in 6000mm wall, 1200mm each, 1800mm spacing, 200mm edge
  var r = S.UNIFORM({ available: 6000, childSize: 1200, spacing: 1800, edgeOffset: 200, minCount: 0, maxCount: null });
  assertEq(r.count, 3, 'UNIFORM: 3 windows in 6m wall');
  assertClose(r.positions[0], 800, 'UNIFORM: first pos = edgeOffset + childSize/2');
  assertClose(r.positions[1], 2600, 'UNIFORM: second pos');
  assertClose(r.positions[2], 4400, 'UNIFORM: third pos');
})();

(function() {
  // Zero available space
  var r = S.UNIFORM({ available: 0, childSize: 1200, spacing: 1800, edgeOffset: 0, minCount: 0, maxCount: null });
  assertEq(r.count, 0, 'UNIFORM: 0 available → 0 count');
  assertEq(r.positions.length, 0, 'UNIFORM: 0 available → empty positions');
})();

(function() {
  // 1 child — childSize > available after edge offset
  var r = S.UNIFORM({ available: 300, childSize: 1200, spacing: 0, edgeOffset: 0, minCount: 0, maxCount: null });
  assertEq(r.count, 0, 'UNIFORM: childSize > available → 0');
})();

(function() {
  // 1 child fits exactly
  var r = S.UNIFORM({ available: 1200, childSize: 1200, spacing: 0, edgeOffset: 0, minCount: 0, maxCount: null });
  assertEq(r.count, 1, 'UNIFORM: exact fit → 1');
  assertClose(r.positions[0], 600, 'UNIFORM: centered at 600');
})();

(function() {
  // min_count enforced
  var r = S.UNIFORM({ available: 1000, childSize: 1200, spacing: 1200, edgeOffset: 0, minCount: 2, maxCount: null });
  assertEq(r.count, 2, 'UNIFORM: minCount=2 enforced even when space tight');
})();

(function() {
  // max_count capped
  var r = S.UNIFORM({ available: 10000, childSize: 500, spacing: 500, edgeOffset: 0, minCount: 0, maxCount: 3 });
  assertEq(r.count, 3, 'UNIFORM: maxCount=3 caps result');
  assertEq(r.positions.length, 3, 'UNIFORM: 3 positions');
})();

(function() {
  // spacing=0 uses childSize as step
  var r = S.UNIFORM({ available: 3000, childSize: 1000, spacing: 0, edgeOffset: 0, minCount: 0, maxCount: null });
  assertEq(r.count, 3, 'UNIFORM: spacing=0 → step=childSize → 3');
})();

// ── PACKED ─────────────────────────────────────────────────────────────────

console.log('§STRAT PACKED');

(function() {
  // 4 tiles packed with 50mm buffer in 5000mm
  var r = S.PACKED({ available: 5000, childSize: 1000, buffer: 50, edgeOffset: 200, minCount: 0, maxCount: null });
  // avail = 4600, step = 1050, count = floor(4650/1050) = 4
  assertEq(r.count, 4, 'PACKED: 4 tiles in 5m');
  assertClose(r.positions[0], 700, 'PACKED: first at edgeOffset + childSize/2');
  assertClose(r.positions[1], 1750, 'PACKED: second');
})();

(function() {
  // Zero buffer = touching
  var r = S.PACKED({ available: 3000, childSize: 1000, buffer: 0, edgeOffset: 0, minCount: 0, maxCount: null });
  assertEq(r.count, 3, 'PACKED: buffer=0, 3 touching children');
})();

(function() {
  // childSize > available
  var r = S.PACKED({ available: 500, childSize: 1000, buffer: 50, edgeOffset: 0, minCount: 0, maxCount: null });
  assertEq(r.count, 0, 'PACKED: childSize > available → 0');
})();

(function() {
  // maxCount limits
  var r = S.PACKED({ available: 10000, childSize: 500, buffer: 100, edgeOffset: 0, minCount: 0, maxCount: 5 });
  assertEq(r.count, 5, 'PACKED: maxCount=5 caps');
})();

// ── CENTERED ───────────────────────────────────────────────────────────────

console.log('§STRAT CENTERED');

(function() {
  // 2 lights centered in 4000mm room
  var r = S.CENTERED({ available: 4000, childSize: 200, spacing: 1000, count: 2 });
  // totalSpan = 1*1000 + 200 = 1200, startOffset = (4000-1200)/2 + 100 = 1500
  assertEq(r.count, 2, 'CENTERED: 2 lights');
  assertClose(r.positions[0], 1500, 'CENTERED: first pos');
  assertClose(r.positions[1], 2500, 'CENTERED: second pos');
})();

(function() {
  // Single item centered
  var r = S.CENTERED({ available: 4000, childSize: 600, spacing: 0, count: 1 });
  assertEq(r.count, 1, 'CENTERED: 1 item');
  assertClose(r.positions[0], 2000, 'CENTERED: centered at midpoint');
})();

(function() {
  // count=0
  var r = S.CENTERED({ available: 4000, childSize: 600, spacing: 1000, count: 0 });
  assertEq(r.count, 0, 'CENTERED: count=0 → empty');
})();

(function() {
  // 3 items
  var r = S.CENTERED({ available: 6000, childSize: 400, spacing: 800, count: 3 });
  // totalSpan = 2*800 + 400 = 2000, startOffset = (6000-2000)/2 + 200 = 2200
  assertEq(r.count, 3, 'CENTERED: 3 items');
  assertClose(r.positions[0], 2200, 'CENTERED[0]');
  assertClose(r.positions[1], 3000, 'CENTERED[1]');
  assertClose(r.positions[2], 3800, 'CENTERED[2]');
})();

// ── REPEAT ─────────────────────────────────────────────────────────────────

console.log('§STRAT REPEAT');

(function() {
  // 3 bathroom sets repeated in 9000mm
  var r = S.REPEAT({ available: 9000, templateSize: 2500, buffer: 500, edgeOffset: 0, minCount: 0, maxCount: null });
  // step = 3000, count = floor(9500/3000) = 3
  assertEq(r.count, 3, 'REPEAT: 3 sets');
  assertClose(r.positions[0], 1250, 'REPEAT: first centered');
})();

(function() {
  // Template larger than available
  var r = S.REPEAT({ available: 2000, templateSize: 3000, buffer: 500, edgeOffset: 0, minCount: 0, maxCount: null });
  assertEq(r.count, 0, 'REPEAT: template > available → 0');
})();

(function() {
  // maxCount capped
  var r = S.REPEAT({ available: 20000, templateSize: 2000, buffer: 500, edgeOffset: 0, minCount: 0, maxCount: 2 });
  assertEq(r.count, 2, 'REPEAT: maxCount=2');
})();

// ── FIXED ──────────────────────────────────────────────────────────────────

console.log('§STRAT FIXED');

(function() {
  // Proportional repositioning: parent grows from 4000 to 6000
  var r = S.FIXED({ available: 6000, origAvailable: 4000, origPositions: [1000, 2000, 3000] });
  assertEq(r.count, 3, 'FIXED: count preserved');
  assertClose(r.positions[0], 1500, 'FIXED: 1000 * 1.5 = 1500');
  assertClose(r.positions[1], 3000, 'FIXED: 2000 * 1.5 = 3000');
  assertClose(r.positions[2], 4500, 'FIXED: 3000 * 1.5 = 4500');
})();

(function() {
  // Same size — no change
  var r = S.FIXED({ available: 4000, origAvailable: 4000, origPositions: [1000, 3000] });
  assertClose(r.positions[0], 1000, 'FIXED: no resize → same pos');
  assertClose(r.positions[1], 3000, 'FIXED: no resize → same pos');
})();

(function() {
  // Empty positions
  var r = S.FIXED({ available: 4000, origAvailable: 4000, origPositions: [] });
  assertEq(r.count, 0, 'FIXED: empty → 0');
})();

(function() {
  // origAvailable = 0 — return original positions as-is
  var r = S.FIXED({ available: 4000, origAvailable: 0, origPositions: [1000, 2000] });
  assertEq(r.count, 2, 'FIXED: origAvailable=0 → copy');
  assertClose(r.positions[0], 1000, 'FIXED: origAvailable=0 → no scaling');
})();

// ── SPAN ───────────────────────────────────────────────────────────────────

console.log('§STRAT SPAN');

(function() {
  // Single child fills 6000mm with 100mm edge offset
  var r = S.SPAN({ available: 6000, edgeOffset: 100 });
  assertEq(r.count, 1, 'SPAN: always 1');
  assertClose(r.size, 5800, 'SPAN: size = available - 2*edge');
  assertClose(r.positions[0], 3000, 'SPAN: centered');
})();

(function() {
  // No edge offset
  var r = S.SPAN({ available: 4000, edgeOffset: 0 });
  assertClose(r.size, 4000, 'SPAN: full fill');
  assertClose(r.positions[0], 2000, 'SPAN: centered at half');
})();

(function() {
  // Edge offset eats all space
  var r = S.SPAN({ available: 200, edgeOffset: 150 });
  assertEq(r.count, 0, 'SPAN: edge > half → 0');
})();

// ── ROUTE ──────────────────────────────────────────────────────────────────

console.log('§STRAT ROUTE');

(function() {
  // Straight segment stub
  var r = S.ROUTE({ startAnchor: { x: 0, y: 0, z: 0 }, endAnchor: { x: 5000, y: 0, z: 3000 }, crossSection: 150 });
  assertEq(r.segments.length, 1, 'ROUTE: 1 segment (stub)');
  assertEq(r.segments[0].start.x, 0, 'ROUTE: start x');
  assertEq(r.segments[0].end.x, 5000, 'ROUTE: end x');
  assertEq(r.segments[0].crossSection, 150, 'ROUTE: crossSection');
})();

// ── LINEAR ─────────────────────────────────────────────────────────────────

console.log('§STRAT LINEAR');

(function() {
  // LINEAR = UNIFORM alias
  var r = S.LINEAR({ available: 6000, childSize: 1200, spacing: 1800, edgeOffset: 200, minCount: 0, maxCount: null });
  assertEq(r.count, 3, 'LINEAR: same as UNIFORM → 3');
})();

// ── dispatch ───────────────────────────────────────────────────────────────

console.log('§STRAT dispatch');

(function() {
  var r = S.dispatch('UNIFORM', { available: 3000, childSize: 1000, spacing: 0, edgeOffset: 0, minCount: 0, maxCount: null });
  assertEq(r.count, 3, 'dispatch(UNIFORM): works');
})();

(function() {
  var threw = false;
  try { S.dispatch('BOGUS', {}); } catch(e) { threw = true; }
  assert(threw, 'dispatch(BOGUS): throws');
})();

(function() {
  var r = S.dispatch('SPAN', { available: 4000, edgeOffset: 0 });
  assertEq(r.count, 1, 'dispatch(SPAN): works');
})();

// ── Summary ────────────────────────────────────────────────────────────────

console.log('\n§STRAT_SUMMARY ' + _pass + ' passed, ' + _fail + ' failed, ' + (_pass + _fail) + ' total');
if (_fail > 0) process.exit(1);
