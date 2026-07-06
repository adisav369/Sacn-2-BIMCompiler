# ⚠ DO NOT REMOVE — Scope: S282b ListBuilder + Universal Panel Navigation
# Read the log after every run.

## Background

S282 Phase 1 (done) created the unified action registry in `panels.js`:
- `_actions` = ONE list of 24 entries: `{id, name, key, icon, fn, children, pill, isActive, hold}`
- Icons reference `ICONS.xxx.svg` — zero duplication
- `_paletteEntries` deleted from scene.js — Help reads `_mainPillActions` only
- PillBuilder has `getConfig/setConfig/resetConfig` with `{order:[], hidden:[]}` persistence
- Settings panel: accordion property sheet with drag-reorder + toggle + key badge
- Alt+Z = X-Ray, x = Section restored

## Problem: Panel Navigation Is Per-Panel, Not Universal

Each panel hand-codes its own keyboard nav:
- **Find** (`navigate_find.js`): custom `_findNav.onKey()` with Left/Right cycle, Up/Down per accordion. BUG: ArrowDown from search input → "empty list, no-op" (falls to result list nav instead of first accordion header).
- **Clash** (`scene.js`): `makeListKeyNav` for matrix cells + separate watcher for clash list.
- **Storey/Disc** (`panels.js`): `makeListKeyNav` for storey list.
- **Settings** (`panels.js`): no keyboard nav yet.

The pattern is the same everywhere:
```
Panel has N focusable zones (input, header, list, header, list).
ArrowDown/ArrowUp = move between zones OR within a zone's items.
ArrowLeft/ArrowRight = cycle between zone headers.
Enter/Space = activate current item.
Escape = close panel.
```

But each panel implements this differently with custom `onKey` handlers, `_focusCycle()` arrays, and per-element `if` chains. Adding nav to a new panel = writing 60+ lines of boilerplate.

## Goal

Extract a `PanelNav(zones)` builder from the existing patterns. Each panel declares its zones as data. The builder auto-wires ArrowDown/Up/Left/Right/Enter/Escape. Find, Clash, Settings, and future panels all use the same builder.

Then: ListBuilder extracts the reorderable-list pattern from PillBuilder so Settings, Gantt, and ERP can all reuse drag-to-reorder + persistence.

## Phase 2a: PanelNav — Universal Panel Keyboard Navigation

### Zone Declaration

```js
PanelNav({
  panel: panelEl,
  zones: [
    { id: 'search', el: searchInput, type: 'input' },
    { id: 'storeys', header: storeyHdr, items: function() { return storeyBody.querySelectorAll('.find-acc-item'); }, onSelect: function(el) { el.click(); } },
    { id: 'types', header: typeHdr, items: function() { return typeBody.querySelectorAll('.find-acc-item'); }, onSelect: function(el) { el.click(); } },
    { id: 'results', items: function() { return resultEl.querySelectorAll('.find-result-item'); }, onSelect: function(el) { el.click(); } }
  ],
  onClose: closeFindPanel
});
```

### Behavior

- **ArrowDown from input zone** → focus first header zone (fixes Find bug)
- **ArrowDown/Up within a zone** → navigate items, highlight active, scroll into view
- **ArrowDown from last item in zone** → move to next zone's header
- **ArrowUp from first item in zone** → move to previous zone (or input)
- **ArrowLeft/Right** → cycle between zone headers (existing Find behavior)
- **Enter/Space on header** → expand/collapse accordion, or select active item
- **Enter on item** → call `onSelect(el)`
- **Escape** → close panel (existing behavior via `_registerPanel`)

### Registration

PanelNav auto-calls `_registerPanel(id, el, nav, close)` — panels don't need to wire this manually.

### File

`viewer/panel_nav.js` — loaded before panels that need it. ~80 lines.

### Migration

- **Find**: replace `_findNav` (60 lines) with `PanelNav({zones: [search, storeys, types, results]})`
- **Settings**: add `PanelNav({zones: [pillIcons]})` — arrows traverse rows
- **Clash**: defer (complex multi-select, watcher pattern — don't break it)

## Phase 2b: ListBuilder — Reorderable List Extraction

### Extract from PillBuilder

PillBuilder already has: order persistence, drag-to-reorder, pointer events, placeholder bar, DOM-to-order read. Extract into:

```js
ListBuilder({
  items: items,           // [{id, ...}]
  container: containerEl, // DOM element
  render: function(item) { return rowDOM; },  // returns DOM per item
  storageKey: 'key',      // localStorage key
  onReorder: function(newOrder) { ... }  // callback after reorder
});
```

### Consumers

| Consumer | Items | Phase |
|----------|-------|-------|
| Settings pill rows | `_actions` | 2b |
| Gantt phase rows | `{id, name, duration}` | 3 |
| ERP menu rows | `{id, icon, fn}` | 3 |
| Rate template rows | `{id, desc, rate}` | 3 |

### File

`viewer/list_builder.js` — ~100 lines. PillBuilder becomes a thin wrapper: `ListBuilder` + pill-specific rendering + highlight sync.

## Phase 2c: Settings Phase 2 — Locale + Rate Pickers

After ListBuilder exists:
- Add "Locale" section to Settings: `{ type: 'choice', options: locales }`
- Add "Rate Template" section: `{ type: 'choice', options: rates }`
- Both read from existing loaders (`_TRL_LOADER`, rate loader)
- Settings just surfaces them in the accordion — same renderer, different JSON

## Scope

- [ ] Phase 2a: `PanelNav` builder — universal zone-based keyboard nav
- [ ] Phase 2a: Migrate Find panel to PanelNav (fixes ArrowDown-from-input bug)
- [ ] Phase 2a: Add PanelNav to Settings panel
- [ ] Phase 2b: Extract `ListBuilder` from PillBuilder
- [ ] Phase 2b: Settings pill rows use ListBuilder
- [ ] Phase 2c: Locale + Rate picker sections in Settings

## Files

| File | Role |
|------|------|
| `viewer/panel_nav.js` | New: universal panel keyboard nav builder |
| `viewer/list_builder.js` | New: reorderable list extraction from PillBuilder |
| `viewer/navigate_find.js` | Migrate `_findNav` → PanelNav |
| `viewer/panels.js` | Settings uses PanelNav + ListBuilder |
| `viewer/pill_builder.js` | Becomes ListBuilder consumer |

## Constraints

- No external deps — vanilla JS, pointer events
- No perf impact — PanelNav is lazy (only wired when panel opens)
- Clash panel: DO NOT migrate (complex watcher + multi-select — defer to Phase 3)
- PanelNav zones are declared once per panel, not per-render

## Test

- `§PANEL_NAV` log on every zone focus transition
- `§LISTNAV_ZONE` log on zone entry/exit
- ArrowDown from Find search input → first storey header (the bug that triggered this)
- Settings: arrows traverse pill rows, Enter toggles visibility
- `§SHORTCUT_AUDIT` still clean after PanelNav migration
