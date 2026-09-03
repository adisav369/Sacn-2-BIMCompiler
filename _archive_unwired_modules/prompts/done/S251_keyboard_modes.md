# ⚠ DO NOT REMOVE — S251 Keyboard Modes
# Scope: scene.js keyboard handler + panels.js (ListKeyNav utility) only
# Read the log after every run. Exit code is not evidence.
# Mobile devices are EXCLUDED — all shortcuts are desktop-only (guard: window._isMobile)

---

## S251 — Panel Focus + Key Sequence Shortcuts

### Problem
The viewer has one flat keydown handler in `scene.js` lines 211-232.
- No panel focus concept — arrow keys do nothing in lists
- No key sequences — single letters only, running out of room
- G inside the 2D overlay would close it (wrong)
- No typeahead in lists

We need:
1. **Key sequence shortcuts** — `SC`, `SU` etc. with debounce buffer (global level)
2. **Panel focus model** — Tab cycles panels, arrow keys navigate within focused panel
3. **`ListKeyNav` utility** — one reusable handler for every list panel
4. **Title fixes** — ambiguous panel labels corrected

---

## Spec §1 — Global Key Sequences

### §1.1 — Sequence engine

Replace the flat `keydown` handler with a sequence buffer:

```js
var _seq = '';
var _seqTimer = null;
var _SEQ_MS = 600; // wait up to 600ms for second key

var _shortcuts = {
  // Single-key
  'g':  function() { if (typeof window.open2DPlans==='function') window.open2DPlans(); },
  'x':  function() { var b=document.getElementById('section-btn'); if(b) b.click(); },
  '4':  function() { if (typeof A.export4D5D==='function') A.export4D5D(); },
  'f':  function() { if (typeof A.openFindPanel==='function') A.openFindPanel(''); },
  'c':  function() { if (A._loadClashRules) A._loadClashRules(function(r){ A._showClashMatrix(r,document.body); }); },
  'm':  function() { if (typeof A.toggleMeasure==='function') A.toggleMeasure(); },
  '?':  function() { showShortcutHelp(); },
  // Two-key sequences
  'sc': function() { if (typeof A.takeScreenshot==='function') A.takeScreenshot(); },
  'su': function() { A.toggleXray(); }   // Sunglasses = X-ray
};

function _dispatchSeq(seq) {
  if (_shortcuts[seq]) {
    _shortcuts[seq]();
    console.log('§KBD_SEQ seq=' + seq);
    return true;
  }
  return false;
}

function _isPrefix(seq) {
  return Object.keys(_shortcuts).some(function(k) {
    return k.length > seq.length && k.startsWith(seq);
  });
}
```

Single `keydown` listener:

```js
window.addEventListener('keydown', function(e) {
  if (window._isMobile) { console.log('§KBD_MOBILE skip'); return; }

  // Always-on modifier shortcuts
  if (e.altKey && e.key === 'z') { e.preventDefault(); A.toggleXray(); return; }
  if (e.key === 'F11') { e.preventDefault(); A.toggleFullscreen(); return; }

  var noMod = !e.ctrlKey && !e.altKey && !e.metaKey;
  var notInput = e.target.tagName !== 'INPUT' && e.target.tagName !== 'TEXTAREA';

  // Arrow / Space / Tab / Ctrl+Space — panel focus system (§2), any context
  if (e.key === 'Tab') { e.preventDefault(); _cyclePanel(e.shiftKey ? -1 : 1); return; }
  if (_focusedPanel) {
    if (['ArrowUp','ArrowDown','ArrowLeft','ArrowRight',' '].includes(e.key) ||
        (e.ctrlKey && e.key === ' ')) {
      e.preventDefault();
      _focusedPanel.nav.onKey(e);
      return;
    }
    if (e.key === 'Escape') { e.preventDefault(); _blurPanel(); return; }
    if (noMod && notInput && e.key.length === 1) {
      // Typeahead within focused panel
      _focusedPanel.nav.onTypeahead(e.key);
      return;
    }
  }

  if (!noMod || !notInput) return;

  // Esc with no focused panel — no-op
  if (e.key === 'Escape') return;

  // Key sequence engine
  clearTimeout(_seqTimer);
  _seq += e.key.toLowerCase();

  if (_dispatchSeq(_seq)) {
    _seq = '';
    _showSeqHint('');
    return;
  }
  if (_isPrefix(_seq)) {
    _showSeqHint(_seq);  // e.g. show "S▌" hint
    _seqTimer = setTimeout(function() {
      console.log('§KBD_SEQ_TIMEOUT seq=' + _seq);
      _seq = '';
      _showSeqHint('');
    }, _SEQ_MS);
    return;
  }
  // No match, no prefix
  _seq = '';
  _showSeqHint('');
});
```

### §1.2 — Sequence hint

Small transient label, bottom-right of screen, appears while sequence is pending:

```js
function _showSeqHint(text) {
  var el = document.getElementById('kbd-seq-hint') || (function() {
    var d = document.createElement('div');
    d.id = 'kbd-seq-hint';
    d.style.cssText = 'position:fixed;bottom:48px;right:16px;z-index:200;' +
      'background:rgba(0,0,0,0.7);color:#4fc3f7;font-family:monospace;font-size:18px;' +
      'padding:4px 10px;border-radius:6px;pointer-events:none;transition:opacity 0.2s';
    document.body.appendChild(d);
    return d;
  })();
  el.textContent = text ? text.toUpperCase() + '▌' : '';
  el.style.opacity = text ? '1' : '0';
}
```

### §1.3 — Full shortcut map

| Sequence | Name | Action |
|---|---|---|
| `G` | 2D Grid | Toggle 2D grid overlay |
| `X` | Section Cut | Toggle section cut |
| `4` | 4D/5D | Analytics panel |
| `F` | Find | Find/Navigate panel |
| `C` | Clash Matrix | `_loadClashRules → _showClashMatrix(rules, document.body)` |
| `M` | Measure | Toggle measure mode |
| `SC` | Screenshot | `A.takeScreenshot()` |
| `SU` | Sunglasses | X-ray toggle |
| `?` | Help | Shortcut help overlay |
| `Alt+Z` | X-ray | (kept for backwards compat) |
| `F11` | Fullscreen | Toggle fullscreen |

`C` wiring:
```js
'c': function() {
  if (!A._loadClashRules) return;
  A._loadClashRules(function(rules) { A._showClashMatrix(rules, document.body); });
}
```
- `A._clashRules` cached after first load (measure.js line 55) — second press instant
- `_showClashMatrix` has own `_isMobile` guard (line 1084) and "already open → no-op" (line 1086)
- `document.body` as anchor centres matrix on screen

---

## Spec §2 — Panel Focus Model

**Concept:** Tab cycles keyboard focus between registered panels.
Arrow keys + Space navigate within the focused panel.
Mouse click on any panel item steals focus naturally — no blocking.

### §2.1 — Focus registry

```js
var _panels = [];       // [{ id, el, nav }] — registered panels
var _focusedPanel = null;

function _registerPanel(id, el, nav) {
  _panels.push({ id: id, el: el, nav: nav });
  el.addEventListener('pointerdown', function() { _focusPanel(id); });
}

function _focusPanel(id) {
  if (_focusedPanel) _blurPanel();
  _focusedPanel = _panels.find(function(p) { return p.id === id; }) || null;
  if (_focusedPanel) {
    _focusedPanel.el.style.boxShadow = 'inset 3px 0 0 #4fc3f7'; // left-edge glow
    console.log('§PANEL_FOCUS id=' + id);
  }
}

function _blurPanel() {
  if (!_focusedPanel) return;
  _focusedPanel.el.style.boxShadow = '';
  console.log('§PANEL_BLUR id=' + _focusedPanel.id);
  _focusedPanel = null;
}

function _cyclePanel(dir) {
  if (!_panels.length) return;
  var idx = _focusedPanel ? _panels.indexOf(_focusedPanel) : -1;
  var next = (idx + dir + _panels.length) % _panels.length;
  _focusPanel(_panels[next].id);
  console.log('§PANEL_TAB id=' + _panels[next].id);
}
```

### §2.2 — Visual indicator

Left-edge glow: `boxShadow: 'inset 3px 0 0 #4fc3f7'` — matches the blue accent colour already used throughout the UI. Removed on blur or mouse-to-another-panel.

### §2.3 — Tab order

Register panels in logical left→right, top→bottom visual order:
1. Storey panel
2. DISC panel
3. Section cut panel
4. Find panel
5. Clash matrix (if open)

Shift+Tab reverses.

---

## Spec §3 — ListKeyNav Utility (panels.js)

One generic function. Used by: Storey list, DISC list, Clash list, Find results.

```js
function makeListKeyNav(getItems, onToggle, onActivate) {
  var cursor = -1;
  var anchor = -1;
  var selected = new Set();
  var _taBuffer = '';
  var _taTimer = null;

  function scrollTo(i) {
    var items = getItems();
    if (items[i]) items[i].scrollIntoView({ block: 'nearest' });
  }

  function moveCursor(delta) {
    var items = getItems();
    if (!items.length) return;
    cursor = Math.max(0, Math.min(items.length - 1, cursor + delta));
    scrollTo(cursor);
  }

  function extendRange(delta) {
    if (anchor < 0) anchor = cursor >= 0 ? cursor : 0;
    moveCursor(delta);
    var lo = Math.min(anchor, cursor), hi = Math.max(anchor, cursor);
    selected = new Set();
    for (var i = lo; i <= hi; i++) selected.add(i);
    _emit();
  }

  function _emit() {
    onToggle(Array.from(selected));
    console.log('§LISTNAV_SELECT count=' + selected.size);
  }

  return {
    onKey: function(e) {
      var items = getItems();
      if (e.key === 'ArrowUp')   { e.preventDefault(); anchor = -1; moveCursor(-1); }
      if (e.key === 'ArrowDown') { e.preventDefault(); anchor = -1; moveCursor(+1); }
      if (e.key === 'PageUp')    { e.preventDefault(); anchor = -1; moveCursor(-5); }
      if (e.key === 'PageDown')  { e.preventDefault(); anchor = -1; moveCursor(+5); }
      if (e.key === 'Home')      { e.preventDefault(); anchor = -1; cursor = 0; scrollTo(0); }
      if (e.key === 'End')       { e.preventDefault(); anchor = -1; cursor = items.length-1; scrollTo(cursor); }
      if (e.shiftKey && e.key === 'ArrowUp')   { extendRange(-1); return; }
      if (e.shiftKey && e.key === 'ArrowDown') { extendRange(+1); return; }
      if (e.ctrlKey && e.key === 'a') {
        selected = new Set(items.map(function(_,i){ return i; }));
        _emit(); return;
      }
      if (e.key === ' ' && !e.ctrlKey) {
        e.preventDefault();
        selected = new Set([cursor]); anchor = cursor;
        _emit();
        if (onActivate) onActivate(cursor);
      }
      if (e.ctrlKey && e.key === ' ') {
        e.preventDefault();
        if (selected.has(cursor)) selected.delete(cursor); else selected.add(cursor);
        anchor = cursor;
        _emit();
      }
      if (e.key === 'Enter' && onActivate) { e.preventDefault(); onActivate(cursor); }
    },

    // Typeahead: accumulate chars within 600ms, cycle matches on repeat letter
    onTypeahead: function(ch) {
      clearTimeout(_taTimer);
      _taBuffer += ch.toLowerCase();
      var items = getItems();
      var labels = items.map(function(el) {
        return (el.textContent || '').trim().toLowerCase();
      });
      // Find all matches for current buffer
      var matches = [];
      labels.forEach(function(l, i) { if (l.startsWith(_taBuffer)) matches.push(i); });
      if (matches.length) {
        // If single char typed and same as last char, cycle to next match
        var next = matches[0];
        if (_taBuffer.length === 1 && matches.indexOf(cursor) >= 0) {
          next = matches[(matches.indexOf(cursor) + 1) % matches.length];
        }
        cursor = next;
        scrollTo(cursor);
        console.log('§LISTNAV_TYPEAHEAD buf=' + _taBuffer + ' match=' + cursor);
      }
      _taTimer = setTimeout(function() { _taBuffer = ''; }, 600);
    },

    onClick: function(index, e) {
      // No stopPropagation — panel's own handlers still fire
      if (e.ctrlKey || e.metaKey) {
        if (selected.has(index)) selected.delete(index); else selected.add(index);
        anchor = index;
      } else if (e.shiftKey && anchor >= 0) {
        var lo = Math.min(anchor, index), hi = Math.max(anchor, index);
        selected = new Set();
        for (var i = lo; i <= hi; i++) selected.add(i);
      } else {
        selected = new Set([index]); anchor = index; cursor = index;
      }
      _emit();
    },

    getSelected: function() { return Array.from(selected); }
  };
}
```

**kernel_ops note:** `onToggle` fires the same function a mouse click calls.
If that commits `VIEW_FILTER` to kernel_ops, its `parameters` must accept `ids[]` array
(not a scalar) to support multi-select undo. Verify before wiring.

---

## Spec §4 — Panel Title Fixes

| Panel | Current | Problem | Fix |
|---|---|---|---|
| Grid overlay | **Grid Dimensions** | Sounds like building dimensions to new users; it's the 2D plan annotation layer | **"Plan Grid"** |
| Find panel | *(no heading)* | Invisible in help overlay; no label for focus indicator | Add **"Find"** heading |
| Section cut Z button | **Z ↗** | Diagonal arrow for a vertical-plane cut is confusing | **Z ⊥** |
| Storey panel | **Storeys** | Fine | Keep |
| Discipline panel | **Disciplines** | Fine | Keep |
| Section cut panel | **Section Cut** | Three unrelated things (axis + slider + bookmarks) with no grouping | Add `<small>Axis:</small>` sub-label above Y/X/Z row |

Changes go in:
- `grid_overlay.js` line 914 — `'Grid Dimensions'` → `'Plan Grid'`
- `navigate_find.js` — add `<b class="find-title">Find</b>` above search bar
- `index.html` line 417 — `Z ↗` → `Z ⊥`
- `index.html` line 413 — add `<small style="color:#888;font-size:10px">Axis:</small>` before axis buttons

---

## Spec §5 — Command Palette (`?`)

**Single discovery mechanism for all shortcuts.**
Newbies press `?`. Power users skip it once they know `SC`, `SU` etc.
No other help doc needed — the palette IS the reference.

```
? pressed →  ┌─────────────────────────────┐
             │ 🔍 Type a command...        │
             ├─────────────────────────────┤
             │ ▶ SC  Screenshot            │
             │   SU  Sunglasses (X-ray)    │
             │   G   2D Grid               │
             │   C   Clash Matrix          │
             │   X   Section Cut           │
             │   M   Measure               │
             │   F   Find / Navigate       │
             │   4   4D / 5D Analytics     │
             └─────────────────────────────┘
user types "s"  → filters to SC, SU
user types "sc" → only Screenshot, highlighted
Enter          → runs it, palette closes
                 user just learned SC passively
```

### §5.1 — Behaviour

- `?` opens palette; `?` again or `Esc` closes it
- Search input is focused immediately on open
- Typing filters by command name OR shortcut key (both columns searched)
- Arrow ↑↓ navigate results; Enter runs highlighted command
- Shortcut key shown in accent colour (`#4fc3f7`) — teaches the expert path passively
- Running a command from the palette executes the same function as typing the sequence directly (zero duplicate code — palette calls `_shortcuts[seq]()`)

### §5.2 — Implementation

```js
function showCommandPalette() {
  var existing = document.getElementById('cmd-palette');
  if (existing) { existing.remove(); console.log('§KBD_HELP close'); return; }
  console.log('§KBD_HELP open');

  var entries = [
    { seq:'SC', name:'Screenshot' },
    { seq:'SU', name:'Sunglasses (X-ray)' },
    { seq:'G',  name:'2D Grid' },
    { seq:'C',  name:'Clash Matrix' },
    { seq:'X',  name:'Section Cut' },
    { seq:'M',  name:'Measure' },
    { seq:'F',  name:'Find / Navigate' },
    { seq:'4',  name:'4D / 5D Analytics' }
  ];

  var pal = document.createElement('div');
  pal.id = 'cmd-palette';
  pal.style.cssText = 'position:fixed;top:20%;left:50%;transform:translateX(-50%);' +
    'z-index:300;background:#1a1a1a;border:1px solid #444;border-radius:10px;' +
    'width:320px;box-shadow:0 8px 32px rgba(0,0,0,0.6);overflow:hidden';

  // search input + filtered list — inline styles only, no external CSS
  // Arrow/Enter handled by local keydown listener on the palette
  // Esc / ? close it
  document.body.appendChild(pal);
  pal.querySelector('input').focus();
}
```

No external CSS. Inline styles only. Dismissed by `?`, `Esc`, or clicking outside.

---

## Spec §6 — Non-Blocking Guarantee (CRITICAL)

1. Panel focus only intercepts: `Tab`, `Arrow*`, `Space`, `Ctrl+Space`, `Escape`, single chars for typeahead.
2. All other keys fall through to sequence engine or browser defaults.
3. No `stopPropagation` anywhere — OrbitControls, IFC pick, drag are unaffected.
4. `_focusedPanel.nav.onKey` — must not call `stopPropagation`, only `preventDefault` on handled keys.
5. `ListKeyNav.onClick` — no `stopPropagation` (see feedback_clash_panels.md).
6. `_isMobile` early return is line 1 of the keydown listener.

Verify: with each panel focused, confirm mouse orbit, IFC pick, drag-pan, section slider all still work.

---

## Spec §7 — §-log Witnesses

| Tag | When |
|---|---|
| `§KBD_SEQ seq=X` | Shortcut sequence fired |
| `§KBD_SEQ_TIMEOUT seq=X` | Sequence timed out, reset |
| `§KBD_MOBILE skip` | Early return — mobile |
| `§KBD_HELP open/close` | Help overlay |
| `§PANEL_FOCUS id=X` | Panel gained keyboard focus |
| `§PANEL_BLUR id=X` | Panel lost keyboard focus |
| `§PANEL_TAB id=X` | Tab key cycled to panel |
| `§LISTNAV_SELECT count=N` | List selection changed |
| `§LISTNAV_TYPEAHEAD buf=X match=N` | Typeahead jump |

---

## Spec §8 — Files to Change

| File | Change |
|---|---|
| `deploy/dev/scene.js` | Replace lines 208-233: sequence engine + panel focus dispatcher |
| `deploy/dev/panels.js` | Add `makeListKeyNav` + `_registerPanel` calls for storey + DISC lists |
| `deploy/dev/grid_overlay.js` | Line 914: `'Grid Dimensions'` → `'Plan Grid'` |
| `deploy/dev/navigate_find.js` | Add `<b>Find</b>` heading; call `_registerPanel` |
| `deploy/dev/index.html` | Z button label; Section Cut axis sub-label |

Do NOT touch `navigate_controls.js`.

---

## Spec §9 — Implementation Steps

1. Read `scene.js` lines 205-235 — confirm exact current handler.
2. Implement §1 sequence engine + hint in `scene.js`.
3. Implement §2 panel focus registry in `scene.js`.
4. Implement §3 `makeListKeyNav` in `panels.js`; wire storey + DISC panels.
5. Apply §4 title fixes.
6. Register find-panel and clash-matrix with panel focus registry.
7. Verify `VIEW_FILTER` kernel_ops op accepts `ids[]` array.
8. Add all `§`-log lines per §7.
9. Run `node deploy/dev/tests/audit_specs.js` — must exit 0.
10. Save test log, read it, confirm all `§` witnesses present.

---

## Acceptance Criteria

- [ ] `SC` (typed within 600ms) → Screenshot; `S▌` hint shows after first key
- [ ] `SU` (typed within 600ms) → X-ray/Sunglasses
- [ ] `G`, `X`, `4`, `F`, `C`, `M`, `?` all work as single-key shortcuts
- [ ] `C` opens Clash Matrix centred on screen, no-op on mobile
- [ ] Tab cycles focus: Storey → DISC → Section → Find → (Clash if open) → wrap
- [ ] Shift+Tab cycles backwards
- [ ] Focused panel shows left-edge blue glow (`inset 3px 0 0 #4fc3f7`)
- [ ] Click any panel item → that panel steals keyboard focus
- [ ] Arrow ↑↓ navigates list in focused panel; item scrolls into view
- [ ] Page Up/Down jumps 5 items
- [ ] Home/End jumps to first/last item
- [ ] Space selects item at cursor, clears others
- [ ] Ctrl+Space toggles item at cursor (non-contiguous multi-select)
- [ ] Shift+Arrow extends range selection from anchor
- [ ] Ctrl+A selects all items in focused panel
- [ ] Typeahead: type "G" → jump to first item starting with G; type "G" again → cycle to next G item
- [ ] Typeahead: type "SC" quickly → jump to item starting with "sc"
- [ ] `?` opens command palette with search input focused; typing filters by name or key; Enter runs command; `?`/Esc closes
- [ ] Panel title "Grid Dimensions" → "Plan Grid"
- [ ] Find panel has visible "Find" heading
- [ ] Z axis button: `Z ↗` → `Z ⊥`
- [ ] Section cut panel has "Axis:" sub-label
- [ ] Global G/X/4/F/C/M/SC/SU all still work when NO panel is focused
- [ ] Mouse orbit, IFC pick, drag-pan, section slider unaffected with any panel focused
- [ ] Everything above: NO-OP on mobile
- [ ] `§`-log witnesses present for every branch (from log file, not terminal)
- [ ] `audit_specs.js` exits 0
- [ ] `VIEW_FILTER` kernel_ops op accepts `ids[]` array — undo still works
