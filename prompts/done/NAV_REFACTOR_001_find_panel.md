# ⚠ DO NOT REMOVE — Read this block before any action. Read the log after every run.

## Scope: Extract SECTION A (Find Panel) from navigate.js → navigate_find.js
## Activity category: pipeline/debug (refactoring)
## Constraint: ONE section per session. Do NOT touch Sections B–E.
## Do NOT touch: grid_overlay.js, print_sheet.js (2D session), routewalker.js (RouteWalker session)

---

## Context

`deploy/dev/navigate.js` is 1961 lines split into 5 sections already marked with `// ══ SECTION X`:

| Section | Lines | Content |
|---------|------:|---------|
| A | 107–650 | **Find panel** — search UI, results, highlight, main entrance |
| B/B3/B4 | 651–1215 | Occupancy grid, A*, vertical transport, route templates |
| C | 1216–1406 | Multi-storey path builder |
| D | 1407–1799 | Turn-by-turn navigation engine |
| E | 1800–1961 | Walk button, keyboard, pointer lock, voice, preprocessing |

**This session: extract Section A only → `deploy/dev/navigate_find.js`.**

---

## Spec — What goes in navigate_find.js (Section A boundary)

Lines 107–650 of navigate.js. Specifically:

**State owned by find module:**
- `nav.results`, `nav.activeIdx` — search results state
- `_highlight`, `_highlightPulse` — highlight mesh + pulse timer
- `panel` DOM element + `navHud` DOM element + all `el*` refs (elType, elStorey, elName, elResults, elCount, elNavBtn, elClose, elCue, elBar)
- CSS (lines 14–89) for find panel — move to navigate_find.js

**Functions to move:**
- `_t(k, fb)` — translation helper (local, only find panel uses it)
- `openFindPanel(searchTerm)` — exposed as `A.openFindPanel`
- `closeFindPanel()`
- `populateDropdowns()`
- `runSearch()`
- `findSuggestions(bld, name)`
- `renderSuggestions(suggestions, originalTerm)`
- `renderResults()`
- `escHtml(s)`
- `friendlyName(elementName, ifcClass)`
- `friendlyClass(ifcClass)`
- `classIcon(ifcClass)`
- `selectResult(idx)`
- `highlightElement(guid, worldPos)`
- `clearHighlight()`
- `findMainEntrance()`
- `debounce(fn, ms)` (line 646, used only by find panel filter listeners)

**What stays in navigate.js:**
- `nav` object declaration (shared state — waypoints, active, voiceMode, grid, gridCache)
- `A.navActive`, `A.navCurrentStep` (exposed for tests)
- Sections B, B3, B4, C, D, E entirely untouched

**Interface between navigate_find.js and navigate.js:**
- `navigate_find.js` receives `(A, nav)` — the APP object and the shared nav state
- `navigate_find.js` reads: `A.db`, `A.activeBuilding`, `A.scene`, `A.inputWasVoice`, `A.walkModeActive`, `A.status`
- `navigate_find.js` calls: `startNavigation(target)` — passed in as a callback OR accessed via `A._startNavigation`
- `navigate_find.js` calls: `stopNavigation()` — same pattern
- `navigate_find.js` exposes: `A.openFindPanel`, `A.closeAndClearFind`, `A.clearHighlight`

---

## §-log requirements (prove the wiring works)

Add these `console.log` lines — they are the primary verification mechanism.

In `navigate_find.js`:
```javascript
// In init():
console.log('[S233] §NAV_FIND_MODULE_LOADED panel=' + !!document.getElementById('find-panel'));

// In openFindPanel():
console.log('[S233] §NAV_FIND_OPEN term="' + (searchTerm||'') + '" voice=' + nav.voiceMode);

// In runSearch():
console.log('[S233] §NAV_FIND_SEARCH query="' + query + '" results=' + nav.results.length);

// In selectResult():
console.log('[S233] §NAV_FIND_SELECT idx=' + idx + ' guid=' + (nav.results[idx]||{}).guid);

// In highlightElement():
console.log('[S233] §NAV_FIND_HIGHLIGHT guid=' + guid);

// In clearHighlight():
// (already exists — keep it or add §NAV_FIND_CLEAR_HIGHLIGHT)

// In findMainEntrance():
console.log('[S233] §NAV_FIND_ENTRANCE guid=' + (result ? result.guid : 'none'));
```

In `navigate.js` (after extraction):
```javascript
// At module load, confirm find module wired:
console.log('[S233] §NAV_FIND_WIRED openFindPanel=' + (typeof A.openFindPanel));
```

---

## Extraction procedure

1. **Read** navigate.js lines 14–650 fully before writing anything
2. **Create** `deploy/dev/navigate_find.js` as an IIFE `(function(){ 'use strict'; ... })()`
   - Takes `(A, nav, getStartNavigation)` as init params
   - Moves all Section A code inside
   - Exposes: `window.NavigateFind = { init: init }`
3. **Edit** navigate.js:
   - Remove lines 14–89 (CSS — now in navigate_find.js)
   - Remove lines 107–650 (Section A)
   - Add after `nav` state declaration:
     ```javascript
     if (typeof NavigateFind !== 'undefined') {
       NavigateFind.init(A, nav, function() { return startNavigation; });
     }
     ```
   - Keep `// SECTION A: FIND PANEL` comment as a tombstone with pointer to navigate_find.js
4. **Add** navigate_find.js to `deploy/dev/index.html` — load it BEFORE navigate.js
5. **Syntax check**: `node --check deploy/dev/navigate_find.js && node --check deploy/dev/navigate.js`
6. **Run tests**: `cd deploy/dev/tests && npx playwright test --project=desktop specs/17-find-navigate.spec.js --grep "@fast" --reporter=list`
   - Baseline: 13 pass. After extraction: still 13 pass.
7. **Read the §-log output** — confirm `§NAV_FIND_MODULE_LOADED`, `§NAV_FIND_WIRED`, `§NAV_FIND_SEARCH` all appear
8. **Upload to ootb-dev**: bump sw.js CACHE_VERSION (currently v274 → v275), upload navigate_find.js + navigate.js + sw.js with `--content-type "application/javascript"`

---

## What NOT to do
- Do NOT touch Sections B, B3, B4, C, D, E
- Do NOT touch grid_overlay.js, print_sheet.js, routewalker.js
- Do NOT extract more than Section A in this session
- Do NOT invent new functions — extract only what exists

---

## Pre-flight state
- navigate.js: 1961 lines, all 27 tests in specs/17-find-navigate.spec.js exist
- @fast baseline: 13/13 pass (run `npx playwright test --project=desktop specs/17-find-navigate.spec.js --grep "@fast"` to confirm before starting)
- sw.js CACHE_VERSION: v274
- Branch: `full`
