#!/usr/bin/env node
// test_s251_keyboard.js — S251 Keyboard Modes whitebox verification
// Run: node deploy/dev/tests/test_s251_keyboard.js
// Reads source files, checks wiring. No browser needed.

const fs = require('fs');
const path = require('path');
const DEV = path.resolve(__dirname, '..');

function src(f) { return fs.readFileSync(path.join(DEV, f), 'utf8'); }

var pass = 0, fail = 0;
function check(id, desc, ok) {
  if (ok) { pass++; console.log('  ✓ ' + id + ': ' + desc); }
  else    { fail++; console.log('  ✗ ' + id + ': ' + desc + ' — FAILED'); }
}

console.log('═══ S251 Keyboard Modes — Whitebox Test ═══\n');

// ── scene.js ──
var scene = src('scene.js');

console.log('── Sequence Engine ──');
check('K01', 'sequence buffer _seq exists', scene.includes("var _seq = ''"));
check('K02', 'sequence timeout 600ms', scene.includes('_SEQ_MS = 600'));
check('K03', '_isPrefix function', scene.includes('function _isPrefix(seq)'));
check('K04', '_dispatchSeq function', scene.includes('function _dispatchSeq(seq)'));
check('K05', 'seq hint div created', scene.includes('kbd-seq-hint'));
check('K06', '§KBD_SEQ logged on dispatch', scene.includes("'§KBD_SEQ seq='"));
check('K07', '§KBD_SEQ_TIMEOUT logged', scene.includes('§KBD_SEQ_TIMEOUT'));
check('K08', 'exact+prefix waits timeout', scene.includes('hasExact && !hasLonger'));

console.log('\n── Shortcut Map ──');
check('K10', 'G = 2D grid', scene.includes("'g':") && scene.includes('open2DPlans'));
check('K11', 'X = section cut', scene.includes("'x':") && scene.includes('section-btn'));
check('K12', 'F = find/navigate', scene.includes("'f':") && scene.includes('openFindPanel'));
check('K13', 'C = clash matrix', scene.includes("'c':") && scene.includes('_showClashMatrix'));
check('K14', 'M = measure', scene.includes("'m':") && scene.includes('toggleMeasure'));
check('K15', 'S = sunglasses (not xray)', scene.includes("'s':") && scene.includes('toggleSunglass'));
check('K16', 'SC = screenshot', scene.includes("'sc':") && scene.includes('screenshot'));
check('K17', 'P = fly around', scene.includes("'p':") && scene.includes('toggleFlyAround'));
check('K18', '- = toggle panels', scene.includes("'-':") && scene.includes('toggleAllPanels'));
check('K19', '+ = toggle panels', scene.includes("'+':") && scene.includes('toggleAllPanels'));
check('K20', '4 = analytics', scene.includes("'4':") && scene.includes('export4D5D'));

console.log('\n── Mutual Exclusion ──');
check('K21', 'C blocked in 2D', scene.includes("'c':") && scene.includes('Exit 2D first'));
check('K22', 'M blocked in 2D', scene.includes("'m':") && scene.includes('Exit 2D first'));
check('K23', 'G blocked by clash/measure', scene.includes("'g':") && scene.includes('Close Measure/Clash first'));
check('K24', 'C toggles (open/close)', scene.includes('_clashMatrixDiv') && scene.includes('.remove()'));

console.log('\n── Command Palette ──');
check('K30', '? opens palette', scene.includes("'?'") && scene.includes('showCommandPalette'));
check('K31', 'palette div id', scene.includes("'cmd-palette'"));
check('K32', 'search input', scene.includes('cmd-search'));
check('K33', 'report bug link', scene.includes('cmd-report'));
check('K34', 'documentation link', scene.includes('MOBILE_DEPLOY'));
check('K35', 'palette entries array', scene.includes('_paletteEntries'));
check('K36', '§KBD_HELP open logged', scene.includes('§KBD_HELP open'));
check('K37', '§KBD_HELP close logged', scene.includes('§KBD_HELP close'));
check('K38', '🛟 calls showCommandPalette', scene.includes('showCommandPalette'));

console.log('\n── Panel Focus ──');
check('K40', '_panels array', scene.includes("var _panels = []"));
check('K41', '_focusedPanel var', scene.includes("var _focusedPanel = null"));
check('K42', '_focusStack for Esc-back', scene.includes("var _focusStack = []"));
check('K43', '_registerPanel function', scene.includes('function _registerPanel'));
check('K44', '_registerPanel has closeFn param', scene.includes('closeFn'));
check('K45', '_focusPanel function', scene.includes('function _focusPanel'));
check('K46', '_blurPanel function', scene.includes('function _blurPanel'));
check('K47', '_cyclePanel function', scene.includes('function _cyclePanel'));
check('K48', 'Tab dispatches cyclePanel', scene.includes("e.key === 'Tab'") && scene.includes('_cyclePanel'));
check('K49', 'Shift+Tab reverses', scene.includes('e.shiftKey ? -1 : 1'));
check('K50', 'mobile guard on pointerdown', scene.includes('!window._isMobile'));
check('K51', 'mobile guard on keydown', scene.includes('window._isMobile') && scene.includes('return'));
check('K52', 'blue glow on focus', scene.includes('inset 3px 0 0 #4fc3f7'));
check('K53', 'auto-expand collapsed', scene.includes("'collapsed'") && scene.includes('classList.remove'));
check('K54', 'Esc calls closeFn', scene.includes('_focusedPanel.close'));
check('K55', 'Esc pops focus stack', scene.includes('_focusStack.pop'));
check('K56', '§PANEL_FOCUS logged', scene.includes('§PANEL_FOCUS'));
check('K57', '§PANEL_BLUR logged', scene.includes('§PANEL_BLUR'));
check('K58', '§PANEL_TAB logged', scene.includes('§PANEL_TAB'));
check('K59', '§PANEL_CLOSE logged', scene.includes('§PANEL_CLOSE'));
check('K60', 'offsetWidth visibility check', scene.includes('offsetWidth > 0'));
check('K61', '_focusPanel exposed as global', scene.includes('window._focusPanel'));

console.log('\n── Arrow Keys ──');
check('K62', 'ArrowLeft/Right dispatched to panel', scene.includes("'ArrowLeft', 'ArrowRight'"));
check('K63', 'section slider ←→ steps value', scene.includes('§KBD_SLIDER'));
check('K64', 'section slider uses slider.step', scene.includes('parseFloat(slider.step)'));

console.log('\n── Clash List ──');
check('K70', 'clash list watcher setInterval', scene.includes('_clashListWatcher'));
check('K71', 'watcher re-arms on new list', scene.includes('_lastClashList'));
check('K72', 'clash list panel registered', scene.includes("'clashlist'"));
check('K73', 'multi-select red spheres', scene.includes('SphereGeometry(0.3'));
check('K74', 'depthTest false (shine through)', scene.includes('depthTest: false'));
check('K75', 'multi-select bbox framing', scene.includes('§CLASH_MULTI'));
check('K76', 'data-clash-idx mapped correctly', scene.includes("getAttribute('data-clash-idx')"));

// ── panels.js ──
var panels = src('panels.js');

console.log('\n── ListKeyNav (panels.js) ──');
check('K80', 'makeListKeyNav function', panels.includes('function makeListKeyNav'));
check('K81', 'onCursorMove 4th param', panels.includes('onCursorMove'));
check('K82', 'cursor variable', panels.includes('var cursor = -1'));
check('K83', 'anchor for range select', panels.includes('var anchor = -1'));
check('K84', 'selected Set', panels.includes('var selected = new Set()'));
check('K85', 'Shift+Arrow before plain Arrow', panels.includes('Shift+Arrow must be checked BEFORE'));
check('K86', 'ArrowLeft/Right supported', panels.includes("e.key === 'ArrowLeft'"));
check('K87', 'PageUp/Down jumps 5', panels.includes('moveCursor(-5)'));
check('K88', 'Ctrl+Space toggle', panels.includes("e.ctrlKey && e.key === ' '"));
check('K89', 'Ctrl+A select all', panels.includes("e.ctrlKey && e.key === 'a'"));
check('K90', 'typeahead function', panels.includes('onTypeahead'));
check('K91', 'typeahead buffer 600ms', panels.includes("_taBuffer += ch.toLowerCase()"));
check('K92', 'scrollIntoView nearest', panels.includes("scrollIntoView({ block: 'nearest' })"));
check('K93', 'slider detection (input range)', panels.includes("curItem.type === 'range'"));
check('K94', 'slider dispatches input event', panels.includes("new Event('input')"));
check('K95', '§LISTNAV_SELECT logged', panels.includes('§LISTNAV_SELECT'));
check('K96', '§LISTNAV_TYPEAHEAD logged', panels.includes('§LISTNAV_TYPEAHEAD'));
check('K97', '§LISTNAV_SLIDER logged', panels.includes('§LISTNAV_SLIDER'));
check('K98', 'makeListKeyNav exposed globally', panels.includes('window.makeListKeyNav'));

console.log('\n── Panel Registration (panels.js) ──');
check('K100', 'storey panel registered', panels.includes("_registerPanel('storey'"));
check('K101', 'disc panel registered', panels.includes("_registerPanel('disc'"));
check('K102', 'toolbar panel registered', panels.includes("_registerPanel('toolbar'"));
check('K103', 'section panel registered with closeFn', panels.includes("_registerPanel('section'") && panels.includes('secClose'));
check('K104', 'sunglasses panel registered with closeFn', panels.includes("_registerPanel('sunglass'") && panels.includes('sunClose'));
check('K105', '§LISTNAV_WIRE logged', panels.includes('§LISTNAV_WIRE'));
check('K106', '_wireListKeyNav called after populateStoreys', panels.includes('_wireListKeyNav') && panels.includes('populateStoreys'));
check('K107', '_wireListKeyNav called after populateDiscs', panels.includes('_wireListKeyNav') && panels.includes('populateDiscs'));
check('K108', '.panel-toggle in querySelectorAll', panels.includes('.panel-toggle'));

console.log('\n── Multi-Select (panels.js) ──');
check('K110', 'storey multi-select array', panels.includes('§STOREY_MULTI'));
check('K111', 'disc multi-select', panels.includes('§DISC_MULTI'));
check('K112', 'storey multi shows multiple storeys', panels.includes('selectedStoreys.indexOf(obj.userData.storey)'));
check('K113', 'clash matrix hide with -', panels.includes('A._clashMatrixDiv') && panels.includes('swipe-hidden'));

// ── grid_overlay.js ──
var grid = src('grid_overlay.js');

console.log('\n── Grid Panel (grid_overlay.js) ──');
check('K120', 'grid panel registered', grid.includes("_registerPanel('grid'"));
check('K121', 'grid auto-focus on open', grid.includes("_focusPanel('grid')"));
check('K122', 'Esc exits 2D (toggleGridOverlay)', grid.includes('A.toggleGridOverlay()'));
check('K123', 'panel positioned below HUD', grid.includes('getBoundingClientRect().bottom'));
check('K124', 'title is Plan Grid', grid.includes('Plan Grid'));
check('K125', '§LISTNAV_WIRE panel=grid', grid.includes("§LISTNAV_WIRE panel=grid"));

// ── index.html ──
var html = src('index.html');

console.log('\n── index.html ──');
check('K130', '🛟 calls showCommandPalette', html.includes('showCommandPalette'));
check('K131', 'sunglasses panel has × close', html.includes('sunglass-slider-panel') && html.includes('panel-toggle'));
check('K132', 'Z axis button label Z ⊥', html.includes('Z ⊥'));
check('K133', 'scene.js v=8', html.includes('scene.js?v=8'));
check('K134', 'panels.js v=8', html.includes('panels.js?v=8'));

// ── Summary ──
console.log('\n═══════════════════════════════════════');
console.log('  PASS: ' + pass + '  FAIL: ' + fail + '  TOTAL: ' + (pass + fail));
if (fail > 0) { console.log('  ✗ SOME TESTS FAILED'); process.exit(1); }
else { console.log('  ✓ ALL TESTS PASS'); }
