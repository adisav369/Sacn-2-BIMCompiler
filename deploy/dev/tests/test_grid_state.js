#!/usr/bin/env node
/**
 * test_grid_state.js — Whitebox verification of GridState module
 *
 * Issues tested:
 *   T1:  init sets positions and labels correctly
 *   T2:  snapshotOriginals records label → position mapping
 *   T3:  addLine inserts in sorted order with label
 *   T4:  addLine dedup — rejects positions within minGap
 *   T5:  addLine cap — rejects after 15 lines per axis
 *   T6:  addLine registers original position immediately
 *   T7:  getDeltas returns zero when no grid moved
 *   T8:  getDeltas returns correct delta after position change
 *   T9:  getDeltas uses label-based lookup (immune to index shift)
 *   T10: getDeltas filters by threshold
 *   T11: removeLine removes by label and cleans originals
 *   T12: getLines returns original positions for engine
 *   T13: resortLabels migrates originals to new labels
 *   T14: addLine after snapshot — new line has zero delta
 *   T15: drag one grid, neighbours have zero delta
 *   T16: pullback after drag — delta tracks correctly
 *   T17: ceiling Y accessor works
 *   T18: reset clears all state
 *
 * Run: node deploy/dev/tests/test_grid_state.js
 */
var fs = require('fs');
var path = require('path');
var vm = require('vm');

var pass = 0, fail = 0, total = 0;
var logLines = [];

function test(name, fn) {
  total++;
  try {
    fn();
    pass++;
    console.log('  PASS  ' + name);
  } catch (e) {
    fail++;
    console.log('  FAIL  ' + name + ' — ' + e.message);
  }
}

function assert(cond, msg) {
  if (!cond) throw new Error(msg || 'assertion failed');
}

function assertEq(a, b, msg) {
  if (a !== b) throw new Error((msg || '') + ' expected ' + JSON.stringify(b) + ' got ' + JSON.stringify(a));
}

function assertClose(a, b, tol, msg) {
  if (Math.abs(a - b) > tol)
    throw new Error((msg || '') + ' expected ' + b + ' got ' + a + ' (tol=' + tol + ')');
}

function logTag(tag, msg) {
  var line = '§' + tag + ' ' + msg;
  logLines.push(line);
  console.log('    ' + line);
}

function loadGridState() {
  var src = fs.readFileSync(path.resolve(__dirname, '..', 'grid_state.js'), 'utf8');
  var ctx = { module: { exports: {} }, window: {}, console: console };
  vm.createContext(ctx);
  vm.runInContext(src, ctx);
  return ctx.module.exports;
}

// ═══════════════════════════════════════════════════════════════════════════
console.log('\n═══ GridState Module Tests ═══\n');

var GS = loadGridState();

// ── T1-T6: Init, Snapshot, AddLine ──
console.log('── T1-T6: Init, Snapshot, AddLine ──');

test('T1 Issue: init sets positions and labels correctly', function() {
  GS.init([0, 10, 20], [0, 8]);
  var pos = GS.getPositions();
  assertEq(pos.x.length, 3, 'X positions');
  assertEq(pos.z.length, 2, 'Z positions');
  var labels = GS.getLabels();
  assertEq(labels.x[0], 'A', 'First X label');
  assertEq(labels.x[2], 'C', 'Third X label');
  assertEq(labels.z[0], '1', 'First Z label');
  logTag('INIT', 'x=' + labels.x.join(',') + ' z=' + labels.z.join(','));
});

test('T2 Issue: snapshotOriginals records label → position mapping', function() {
  GS.init([0, 10], [0, 8]);
  GS.snapshotOriginals();
  assertEq(GS.getOriginal('A'), 0, 'A original');
  assertEq(GS.getOriginal('B'), 10, 'B original');
  assertEq(GS.getOriginal('1'), 0, 'Z-1 original');
  assertEq(GS.getOriginal('2'), 8, 'Z-2 original');
  logTag('SNAPSHOT', 'A=0 B=10 1=0 2=8');
});

test('T3 Issue: addLine inserts in sorted order with label', function() {
  GS.init([0, 20], [0, 8]);
  var result = GS.addLine('x', 10);
  assert(result !== null, 'Should accept');
  var pos = GS.getPositions();
  assertEq(pos.x.length, 3, 'Now 3 X positions');
  assertEq(pos.x[1], 10, 'Inserted at sorted position');
  var labels = GS.getLabels();
  assert(labels.x.length === 3, 'Now 3 X labels');
  logTag('ADD_LINE', 'x=10 inserted at idx=' + result.index + ' label=' + result.label);
});

test('T4 Issue: addLine dedup — rejects positions within minGap', function() {
  GS.init([0, 10], [0, 8]);
  var result = GS.addLine('x', 1.5);  // within 2.0 of x=0
  assertEq(result, null, 'Should reject (too close to 0)');
  var pos = GS.getPositions();
  assertEq(pos.x.length, 2, 'Still 2 X positions');
  logTag('DEDUP', 'x=1.5 rejected (within 2.0 of x=0)');
});

test('T5 Issue: addLine cap — rejects after 15 lines per axis', function() {
  var xPos = [];
  for (var i = 0; i < 15; i++) xPos.push(i * 5);
  GS.init(xPos, [0, 8]);
  var result = GS.addLine('x', 100);
  assertEq(result, null, 'Should reject (cap=15)');
  logTag('CAP', '16th line rejected');
});

test('T6 Issue: addLine registers original position immediately', function() {
  GS.init([0, 20], [0, 8]);
  GS.snapshotOriginals();
  var result = GS.addLine('x', 10);
  assert(result !== null, 'Should accept');
  var orig = GS.getOriginal(result.label);
  assertEq(orig, 10, 'New line original = insertion position');
  logTag('ADD_ORIG', 'label=' + result.label + ' orig=' + orig);
});

// ── T7-T10: Delta Computation ──
console.log('\n── T7-T10: Delta Computation ──');

test('T7 Issue: getDeltas returns empty when no grid moved', function() {
  GS.init([0, 10, 20], [0, 8]);
  GS.snapshotOriginals();
  var deltas = GS.getDeltas();
  assertEq(deltas.length, 0, 'No movement → no deltas');
  logTag('DELTA_ZERO', 'no movement → 0 deltas');
});

test('T8 Issue: getDeltas returns correct delta after position change', function() {
  GS.init([0, 10], [0, 8]);
  GS.snapshotOriginals();
  GS.setPosition('x', 1, 13);  // Move B from 10 to 13
  var deltas = GS.getDeltas();
  assertEq(deltas.length, 1, 'One grid moved');
  assertEq(deltas[0].label, 'B', 'B moved');
  assertClose(deltas[0].absDelta, 3, 0.001, 'Delta = +3');
  assertClose(deltas[0].currentPos, 13, 0.001, 'Current = 13');
  assertClose(deltas[0].originalPos, 10, 0.001, 'Original = 10');
  logTag('DELTA_MOVE', 'B: 10→13, delta=+3');
});

test('T9 Issue: getDeltas uses label-based lookup (immune to index shift)', function() {
  GS.init([0, 20], [0, 8]);
  GS.snapshotOriginals();  // A=0, B=20
  // Insert at x=10 → A, A', B (B shifts from index 1 to index 2)
  GS.addLine('x', 10);
  var labels = GS.getLabels();
  assertEq(labels.x[2], 'B', 'B at index 2');
  // Move B from 20 to 22
  GS.setPosition('x', 2, 22);
  var deltas = GS.getDeltas();
  // Should find B with delta=+2, not a phantom delta from index mismatch
  var bDelta = deltas.filter(function(d) { return d.label === 'B'; });
  assertEq(bDelta.length, 1, 'B delta found');
  assertClose(bDelta[0].absDelta, 2, 0.001, 'B delta = +2');
  // New line (A') should have zero delta
  var apDelta = deltas.filter(function(d) { return d.label === "A'"; });
  assertEq(apDelta.length, 0, "A' should have zero delta (not in results)");
  logTag('DELTA_LABEL', 'B shifted idx 1→2, delta still +2 (label-based)');
});

test('T10 Issue: getDeltas filters by threshold', function() {
  GS.init([0, 10], [0, 8]);
  GS.snapshotOriginals();
  GS.setPosition('x', 1, 10.005);  // Tiny move: 0.005
  var deltas = GS.getDeltas(0.01);  // threshold 0.01
  assertEq(deltas.length, 0, 'Below threshold → filtered out');
  var deltasLow = GS.getDeltas(0.001);  // lower threshold
  assertEq(deltasLow.length, 1, 'Above lower threshold → included');
  logTag('DELTA_THRESHOLD', '0.005 filtered at 0.01, included at 0.001');
});

// ── T11-T13: Remove, GetLines, Resort ──
console.log('\n── T11-T13: Remove, GetLines, Resort ──');

test('T11 Issue: removeLine removes by label and cleans originals', function() {
  GS.init([0, 10, 20], [0, 8]);
  GS.snapshotOriginals();
  var removed = GS.removeLine('x', 'B');
  assert(removed, 'Should remove B');
  var pos = GS.getPositions();
  assertEq(pos.x.length, 2, 'Now 2 X positions');
  assertEq(GS.getOriginal('B'), undefined, 'B original cleaned');
  logTag('REMOVE', 'B removed, originals cleaned');
});

test('T12 Issue: getLines returns original positions for engine', function() {
  GS.init([0, 10], [0, 8]);
  GS.snapshotOriginals();
  GS.setPosition('x', 1, 15);  // Drag B to 15
  var lines = GS.getLines();
  // Engine should see original positions, not current
  var bLine = lines.filter(function(l) { return l.id === 'B'; });
  assertEq(bLine.length, 1, 'B line exists');
  assertClose(bLine[0].pos, 10, 0.001, 'Engine sees original pos=10, not current 15');
  logTag('GETLINES', 'B: engine pos=10 (original), current=15');
});

test('T13 Issue: resortLabels migrates originals to new labels', function() {
  GS.init([20, 0, 10], [0, 8]);  // unsorted
  GS.snapshotOriginals();
  // originals: A=20, B=0, C=10
  GS.resortLabels();
  // After sort: positions [0, 10, 20], labels [A, B, C]
  // A was 20 (old label A) → now label C (position 20)
  // B was 0 (old label B) → now label A (position 0)
  var pos = GS.getPositions();
  assertEq(pos.x[0], 0, 'Sorted: first is 0');
  assertEq(pos.x[2], 20, 'Sorted: last is 20');
  // Original for new label A should be 0 (was old B)
  assertClose(GS.getOriginal('A'), 0, 0.001, 'A original = 0');
  assertClose(GS.getOriginal('C'), 20, 0.001, 'C original = 20');
  logTag('RESORT', 'originals migrated: A=0 B=10 C=20');
});

// ── T14-T18: Integration scenarios ──
console.log('\n── T14-T18: Integration Scenarios ──');

test('T14 Issue: addLine after snapshot — new line has zero delta', function() {
  GS.init([0, 20], [0, 8]);
  GS.snapshotOriginals();
  GS.addLine('x', 10);  // New line after snapshot
  var deltas = GS.getDeltas();
  assertEq(deltas.length, 0, 'New line has orig=insertion pos → zero delta');
  logTag('ADD_AFTER_SNAP', 'new line at x=10 → zero delta');
});

test('T15 Issue: drag one grid, neighbours have zero delta', function() {
  GS.init([0, 10, 20], [0, 5, 10]);
  GS.snapshotOriginals();
  GS.setPosition('x', 1, 13);  // Drag B from 10 to 13
  var deltas = GS.getDeltas();
  assertEq(deltas.length, 1, 'Only one delta');
  assertEq(deltas[0].label, 'B', 'Only B moved');
  logTag('NEIGHBOUR_ZERO', 'drag B: A=0, C=0 (unchanged)');
});

test('T16 Issue: pullback after drag — delta tracks correctly', function() {
  GS.init([0, 10], [0, 8]);
  GS.snapshotOriginals();
  GS.setPosition('x', 1, 16);  // Drag B to 16
  var d1 = GS.getDeltas();
  assertClose(d1[0].absDelta, 6, 0.001, 'First drag: +6');
  GS.setPosition('x', 1, 13);  // Pullback to 13
  var d2 = GS.getDeltas();
  assertClose(d2[0].absDelta, 3, 0.001, 'Pullback: +3 (not cumulative)');
  logTag('PULLBACK', 'drag +6 → pullback to +3: delta=3');
});

test('T17 Issue: ceiling Y accessor works', function() {
  GS.init([0, 10], [0, 8]);
  assertEq(GS.getCeilingY(), null, 'Initially null');
  GS.setCeilingY(3.6);
  assertEq(GS.getCeilingY(), 3.6, 'Set to 3.6');
  var lines = GS.getLines();
  var ceil = lines.filter(function(l) { return l.id === 'CEIL'; });
  assertEq(ceil.length, 1, 'CEIL in getLines');
  assertClose(ceil[0].pos, 3.6, 0.001, 'CEIL pos=3.6');
  logTag('CEILING', 'CEIL y=3.6 in getLines');
});

test('T18 Issue: reset clears all state', function() {
  GS.init([0, 10, 20], [0, 5, 10]);
  GS.snapshotOriginals();
  GS.setCeilingY(3.6);
  GS.reset();
  var pos = GS.getPositions();
  assertEq(pos.x.length, 0, 'X positions cleared');
  assertEq(pos.z.length, 0, 'Z positions cleared');
  assertEq(GS.getCeilingY(), null, 'Ceiling cleared');
  assertEq(GS.getOriginal('A'), undefined, 'Originals cleared');
  logTag('RESET', 'all state cleared');
});

// ═══════════════════════════════════════════════════════════════════════════
console.log('\n═══ Summary ═══');
console.log('  PASS: ' + pass + '  FAIL: ' + fail + '  TOTAL: ' + total);
console.log('  §-tagged log lines: ' + logLines.length);
if (fail > 0) {
  console.log('\n  ✗ FAILURES — see above');
  process.exit(1);
} else {
  console.log('\n  ✓ ALL PASS');
  process.exit(0);
}
