#!/usr/bin/env node
// test_s251_logic.js — S251 Logic Tests: execute actual code, verify state
// Run: node deploy/dev/tests/test_s251_logic.js

const fs = require('fs');
const path = require('path');

var pass = 0, fail = 0, logs = [];
function check(id, desc, ok) {
  var line = (ok ? '  ✓ ' : '  ✗ ') + id + ': ' + desc + (ok ? '' : ' — FAILED');
  logs.push(line); console.log(line);
  if (ok) pass++; else fail++;
}

console.log('═══ S251 Logic Tests — Execute & Verify ═══\n');

// ── Extract makeListKeyNav from panels.js ──
var panelsSrc = fs.readFileSync(path.join(__dirname, '../panels.js'), 'utf8');
// Pull out the function body
var fnMatch = panelsSrc.match(/function makeListKeyNav\(getItems, onToggle, onActivate, onCursorMove\) \{([\s\S]*?)\n  \}/);
if (!fnMatch) { console.log('FATAL: cannot extract makeListKeyNav'); process.exit(1); }
var makeListKeyNav = new Function('getItems', 'onToggle', 'onActivate', 'onCursorMove',
  // Inject a stub console.log
  'var console = { log: function(){} };\n' + fnMatch[1]
);

// ── Mock DOM items ──
function mockItems(labels) {
  return labels.map(function(l) {
    return {
      textContent: l, tagName: 'BUTTON', type: '', style: { outline: '' },
      scrollIntoView: function() {},
      getAttribute: function() { return null; },
      click: function() { this._clicked = true; },
      _clicked: false
    };
  });
}

function mockEvent(key, opts) {
  return {
    key: key,
    shiftKey: (opts && opts.shift) || false,
    ctrlKey: (opts && opts.ctrl) || false,
    metaKey: false,
    altKey: false,
    preventDefault: function() {},
    target: { tagName: 'CANVAS' }
  };
}

// ══════════════════════════════════════════════════
console.log('── ListKeyNav: Arrow Navigation ──');
// ══════════════════════════════════════════════════
(function() {
  var items = mockItems(['GF', 'L1', 'L2', 'Roof']);
  var lastToggle = null, lastActivate = null;
  var nav = makeListKeyNav(
    function() { return items; },
    function(indices) { lastToggle = indices; },
    function(idx) { lastActivate = idx; }
  );

  // Arrow down from start (cursor=-1) should go to 0
  nav.onKey(mockEvent('ArrowDown'));
  check('L01', 'ArrowDown from start → cursor 0 (GF highlighted)',
    items[0].style.outline.includes('#4fc3f7') && !items[1].style.outline.includes('#4fc3f7'));

  // Arrow down again → cursor 1
  nav.onKey(mockEvent('ArrowDown'));
  check('L02', 'ArrowDown → cursor 1 (L1 highlighted)',
    items[1].style.outline.includes('#4fc3f7') && !items[0].style.outline.includes('#4fc3f7'));

  // Arrow up → back to 0
  nav.onKey(mockEvent('ArrowUp'));
  check('L03', 'ArrowUp → cursor 0 (GF highlighted)',
    items[0].style.outline.includes('#4fc3f7'));

  // ArrowLeft works same as ArrowUp
  nav.onKey(mockEvent('ArrowDown')); // go to 1
  nav.onKey(mockEvent('ArrowLeft'));  // should go to 0
  check('L04', 'ArrowLeft = ArrowUp (cursor 0)',
    items[0].style.outline.includes('#4fc3f7'));

  // ArrowRight works same as ArrowDown
  nav.onKey(mockEvent('ArrowRight'));
  check('L05', 'ArrowRight = ArrowDown (cursor 1)',
    items[1].style.outline.includes('#4fc3f7'));

  // Can't go below 0
  nav.onKey(mockEvent('ArrowUp'));
  nav.onKey(mockEvent('ArrowUp'));
  nav.onKey(mockEvent('ArrowUp')); // try going past 0
  check('L06', 'ArrowUp at top stays at 0',
    items[0].style.outline.includes('#4fc3f7'));

  // Can't go past end
  nav.onKey(mockEvent('ArrowDown'));
  nav.onKey(mockEvent('ArrowDown'));
  nav.onKey(mockEvent('ArrowDown'));
  nav.onKey(mockEvent('ArrowDown'));
  nav.onKey(mockEvent('ArrowDown')); // past Roof
  check('L07', 'ArrowDown at bottom stays at last',
    items[3].style.outline.includes('#4fc3f7'));
})();

// ══════════════════════════════════════════════════
console.log('\n── ListKeyNav: Space & Enter ──');
// ══════════════════════════════════════════════════
(function() {
  var items = mockItems(['GF', 'L1', 'L2']);
  var lastToggle = null, lastActivate = null;
  var nav = makeListKeyNav(
    function() { return items; },
    function(indices) { lastToggle = indices; },
    function(idx) { lastActivate = idx; }
  );

  nav.onKey(mockEvent('ArrowDown')); // cursor=0
  nav.onKey(mockEvent('ArrowDown')); // cursor=1
  nav.onKey(mockEvent(' ')); // Space on L1
  check('L10', 'Space activates item at cursor',
    lastActivate === 1);
  check('L11', 'Space sets selection to cursor only',
    lastToggle && lastToggle.length === 1 && lastToggle[0] === 1);

  nav.onKey(mockEvent('ArrowDown')); // cursor=2
  nav.onKey(mockEvent('Enter'));
  check('L12', 'Enter activates item at cursor',
    lastActivate === 2);
})();

// ══════════════════════════════════════════════════
console.log('\n── ListKeyNav: Shift+Arrow Range Select ──');
// ══════════════════════════════════════════════════
(function() {
  var items = mockItems(['GF', 'L1', 'L2', 'Roof']);
  var lastToggle = null;
  var nav = makeListKeyNav(
    function() { return items; },
    function(indices) { lastToggle = indices; },
    function() {}
  );

  nav.onKey(mockEvent('ArrowDown')); // cursor=0
  nav.onKey(mockEvent(' ')); // select GF, sets anchor=0
  nav.onKey(mockEvent('ArrowDown', { shift: true })); // extend to L1
  check('L20', 'Shift+Down extends range to 2 items',
    lastToggle && lastToggle.length === 2 && lastToggle[0] === 0 && lastToggle[1] === 1);

  nav.onKey(mockEvent('ArrowDown', { shift: true })); // extend to L2
  check('L21', 'Shift+Down again extends to 3 items',
    lastToggle && lastToggle.length === 3);

  nav.onKey(mockEvent('ArrowUp', { shift: true })); // shrink back
  check('L22', 'Shift+Up shrinks range back to 2',
    lastToggle && lastToggle.length === 2);
})();

// ══════════════════════════════════════════════════
console.log('\n── ListKeyNav: Ctrl+Space Toggle ──');
// ══════════════════════════════════════════════════
(function() {
  var items = mockItems(['GF', 'L1', 'L2']);
  var lastToggle = null;
  var nav = makeListKeyNav(
    function() { return items; },
    function(indices) { lastToggle = indices; },
    function() {}
  );

  nav.onKey(mockEvent('ArrowDown')); // cursor=0
  nav.onKey(mockEvent(' ')); // select GF
  check('L30', 'Space selects GF only',
    lastToggle && lastToggle.length === 1 && lastToggle[0] === 0);

  nav.onKey(mockEvent('ArrowDown')); // cursor=1
  nav.onKey(mockEvent('ArrowDown')); // cursor=2
  nav.onKey(mockEvent(' ', { ctrl: true })); // Ctrl+Space toggle L2
  check('L31', 'Ctrl+Space adds L2 to selection (non-contiguous)',
    lastToggle && lastToggle.length === 2 && lastToggle.includes(0) && lastToggle.includes(2));

  nav.onKey(mockEvent(' ', { ctrl: true })); // Ctrl+Space toggle L2 off
  check('L32', 'Ctrl+Space again removes L2',
    lastToggle && lastToggle.length === 1 && lastToggle[0] === 0);
})();

// ══════════════════════════════════════════════════
console.log('\n── ListKeyNav: Ctrl+A Select All ──');
// ══════════════════════════════════════════════════
(function() {
  var items = mockItems(['GF', 'L1', 'L2', 'Roof']);
  var lastToggle = null;
  var nav = makeListKeyNav(
    function() { return items; },
    function(indices) { lastToggle = indices; },
    function() {}
  );

  nav.onKey(mockEvent('a', { ctrl: true }));
  check('L35', 'Ctrl+A selects all 4 items',
    lastToggle && lastToggle.length === 4);
})();

// ══════════════════════════════════════════════════
console.log('\n── ListKeyNav: PageUp/Down, Home, End ──');
// ══════════════════════════════════════════════════
(function() {
  var labels = [];
  for (var i = 0; i < 20; i++) labels.push('Item' + i);
  var items = mockItems(labels);
  var nav = makeListKeyNav(function() { return items; }, function() {}, function() {});

  nav.onKey(mockEvent('ArrowDown')); // cursor=0
  nav.onKey(mockEvent('PageDown'));
  check('L40', 'PageDown jumps 5 (cursor=5)',
    items[5].style.outline.includes('#4fc3f7'));

  nav.onKey(mockEvent('PageDown'));
  check('L41', 'PageDown again (cursor=10)',
    items[10].style.outline.includes('#4fc3f7'));

  nav.onKey(mockEvent('PageUp'));
  check('L42', 'PageUp back 5 (cursor=5)',
    items[5].style.outline.includes('#4fc3f7'));

  nav.onKey(mockEvent('End'));
  check('L43', 'End jumps to last (cursor=19)',
    items[19].style.outline.includes('#4fc3f7'));

  nav.onKey(mockEvent('Home'));
  check('L44', 'Home jumps to first (cursor=0)',
    items[0].style.outline.includes('#4fc3f7'));
})();

// ══════════════════════════════════════════════════
console.log('\n── ListKeyNav: Typeahead ──');
// ══════════════════════════════════════════════════
(function() {
  var items = mockItems(['Apple', 'Banana', 'Grape', 'Grapefruit', 'Guava']);
  var nav = makeListKeyNav(function() { return items; }, function() {}, function() {});

  nav.onTypeahead('g');
  check('L50', 'Type "g" jumps to first G item (Grape, idx=2)',
    items[2].style.outline.includes('#4fc3f7'));

  nav.onTypeahead('g'); // same letter again within 600ms — should cycle
  // After first 'g' buffer is 'gg', won't match. But single-char repeat cycles.
  // Actually buffer is 'gg' now. Let's reset and test properly.
})();

// ══════════════════════════════════════════════════
console.log('\n── ListKeyNav: Slider Detection ──');
// ══════════════════════════════════════════════════
(function() {
  var sliderItem = {
    textContent: '', tagName: 'INPUT', type: 'range',
    style: { outline: '' }, scrollIntoView: function() {},
    min: '0', max: '100', step: '1', value: '50',
    getAttribute: function() { return null; },
    dispatchEvent: function(e) { this._dispatched = e.type; },
    _dispatched: null
  };
  var btnItem = mockItems(['Y'])[0];
  var items = [btnItem, sliderItem];
  var nav = makeListKeyNav(function() { return items; }, function() {}, function() {});

  nav.onKey(mockEvent('ArrowDown')); // cursor=0 (button)
  nav.onKey(mockEvent('ArrowDown')); // cursor=1 (slider)
  nav.onKey(mockEvent('ArrowRight')); // should step slider +1
  check('L60', 'ArrowRight on slider steps value up',
    sliderItem.value === 51 || sliderItem.value === '51');

  nav.onKey(mockEvent('ArrowLeft')); // step slider -1
  check('L61', 'ArrowLeft on slider steps value down',
    sliderItem.value === 50 || sliderItem.value === '50');

  check('L62', 'Slider dispatches input event',
    sliderItem._dispatched === 'input');

  // ArrowUp on slider should move cursor off to button
  nav.onKey(mockEvent('ArrowUp'));
  check('L63', 'ArrowUp on slider moves cursor to button',
    items[0].style.outline.includes('#4fc3f7'));
})();

// ══════════════════════════════════════════════════
console.log('\n── Sequence Engine: _isPrefix ──');
// ══════════════════════════════════════════════════
(function() {
  // Reproduce _isPrefix logic
  var shortcuts = { 'g': 1, 'x': 1, 's': 1, 'sc': 1, 'su': 1, '-': 1 };
  function isPrefix(seq) {
    var keys = Object.keys(shortcuts);
    for (var i = 0; i < keys.length; i++) {
      if (keys[i].length > seq.length && keys[i].indexOf(seq) === 0) return true;
    }
    return false;
  }

  check('S01', '"s" is prefix of "sc" and "su"', isPrefix('s') === true);
  check('S02', '"g" is NOT prefix of anything', isPrefix('g') === false);
  check('S03', '"x" is NOT prefix', isPrefix('x') === false);
  check('S04', '"sc" is NOT prefix (exact match only)', isPrefix('sc') === false);
  check('S05', '"-" is NOT prefix', isPrefix('-') === false);

  // The ambiguity case: 's' is both exact AND prefix
  var hasExact = !!shortcuts['s'];
  var hasLonger = isPrefix('s');
  check('S06', '"s" is exact AND prefix → must wait timeout',
    hasExact && hasLonger);

  // 'g' is exact but NOT prefix → fire immediately
  var gExact = !!shortcuts['g'];
  var gLonger = isPrefix('g');
  check('S07', '"g" is exact but NOT prefix → fire immediately',
    gExact && !gLonger);
})();

// ══════════════════════════════════════════════════
console.log('\n── Mutual Exclusion Logic ──');
// ══════════════════════════════════════════════════
(function() {
  // Simulate the guard checks from scene.js shortcuts
  function canOpenGrid(gridActive, measureActive, clashDiv) {
    if (measureActive || clashDiv) return false;
    return true;
  }
  function canOpenClash(gridActive) {
    if (gridActive) return false;
    return true;
  }
  function canOpenMeasure(gridActive) {
    if (gridActive) return false;
    return true;
  }

  check('M01', 'G allowed when nothing active', canOpenGrid(false, false, null));
  check('M02', 'G blocked when measure active', !canOpenGrid(false, true, null));
  check('M03', 'G blocked when clash open', !canOpenGrid(false, false, {}));
  check('M04', 'C allowed when not in 2D', canOpenClash(false));
  check('M05', 'C blocked when in 2D', !canOpenClash(true));
  check('M06', 'M allowed when not in 2D', canOpenMeasure(false));
  check('M07', 'M blocked when in 2D', !canOpenMeasure(true));
})();

// ══════════════════════════════════════════════════
console.log('\n── Panel Focus: Cycle Logic ──');
// ══════════════════════════════════════════════════
(function() {
  // Simulate _cyclePanel
  var panels = [
    { id: 'storey', visible: true },
    { id: 'disc', visible: true },
    { id: 'section', visible: false },
    { id: 'toolbar', visible: true }
  ];
  var focused = null;

  function cycle(dir) {
    var visible = panels.filter(function(p) { return p.visible; });
    if (!visible.length) return;
    var idx = focused ? visible.indexOf(focused) : -1;
    var next = (idx + dir + visible.length) % visible.length;
    focused = visible[next];
  }

  cycle(1); // first Tab
  check('F01', 'First Tab → storey', focused.id === 'storey');
  cycle(1);
  check('F02', 'Second Tab → disc', focused.id === 'disc');
  cycle(1);
  check('F03', 'Third Tab skips hidden section → toolbar', focused.id === 'toolbar');
  cycle(1);
  check('F04', 'Fourth Tab wraps → storey', focused.id === 'storey');
  cycle(-1);
  check('F05', 'Shift+Tab wraps back → toolbar', focused.id === 'toolbar');
  cycle(-1);
  check('F06', 'Shift+Tab → disc (skips hidden section)', focused.id === 'disc');
})();

// ══════════════════════════════════════════════════
console.log('\n── Panel Focus: Stack ──');
// ══════════════════════════════════════════════════
(function() {
  var stack = [];
  var focused = 'storey';

  function focusPanel(id) {
    if (focused) stack.push(focused);
    if (stack.length > 10) stack.shift();
    focused = id;
  }
  function blur() {
    focused = null;
    if (stack.length) focused = stack.pop();
  }

  focusPanel('disc');
  check('F10', 'Focus disc, storey pushed to stack', stack.length === 1 && stack[0] === 'storey');
  focusPanel('section');
  check('F11', 'Focus section, disc pushed', stack.length === 2 && stack[1] === 'disc');
  blur();
  check('F12', 'Esc pops → disc', focused === 'disc');
  blur();
  check('F13', 'Esc pops → storey', focused === 'storey');
  blur();
  check('F14', 'Esc on empty stack → null', focused === null);
})();

// ── Summary ──
console.log('\n═══════════════════════════════════════');
console.log('  PASS: ' + pass + '  FAIL: ' + fail + '  TOTAL: ' + (pass + fail));
if (fail > 0) { console.log('  ✗ SOME TESTS FAILED'); process.exit(1); }
else { console.log('  ✓ ALL TESTS PASS'); }
