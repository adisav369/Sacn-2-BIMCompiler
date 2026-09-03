# ⚠ DO NOT REMOVE — Scope: S282 Settings JSON + Reorderable List Pattern
# Read the log after every run.

## Design-First Principle

**Everything the user sees or configures is ONE JSON entry.**

One entry declares an icon, its shortcut, its Help description, its Settings
toggle, and its panel. The JSON is the single source of truth. The renderer
is generic — it walks sections → rows → fields and builds DOM. No per-feature
code in the renderer. Adding a new tool = adding one JSON object. The user
learns one interaction pattern (drag to reorder, toggle to show/hide, dropdown
to choose) and it works identically in pill config, Gantt schedule, rate
templates, locale picker, and Help panel.

This is the Excel model: rows are data, columns are typed fields, the grid
renderer doesn't know what it's showing — it just reads the schema. That's
what makes it reusable across every surface in the app.

## Goal

Extract the PillBuilder's JSON-driven reorder pattern into a general-purpose
`ListBuilder` that Settings, Gantt, ERP menus, and future apps can all reuse.
Settings panel becomes the first real consumer — editing pill icon visibility
and order, persisted to localStorage.

## Background

S281 created `pill_builder.js` — a declarative pill framework where one object
literal = icon + panel + highlight + shortcut. The `_actions` array in panels.js
is effectively a JSON schema with functions attached. The reorder logic
(`_getOrder` / `_bumpAction` / localStorage persistence) already works.

**What S281 proved:** declarative lists + user-overridable order + auto-wiring
is a reusable pattern. Gantt phases, ERP menus, locale packs, and rate templates
are all the same shape: ordered list of `{id, props}` + user override.

## Architecture

### 1. JSON Declaration (data) vs Handlers (code)

Each HTML page declares its pill as JSON:

```json
{
  "owner": "viewer",
  "actions": [
    { "id": "redpill", "img": "redpill.png", "platform": "desktop", "key": "," },
    { "id": "find",    "icon": "search",  "key": null },
    { "id": "measure", "icon": "ruler",   "key": "m", "hold": "clash" },
    { "id": "settings","icon": "gear",    "key": "=", "panel": { "title": "Settings" } },
    { "id": "home",    "icon": "home" }
  ],
  "order": ["settings","redpill","report","fly","measure","find","home"]
}
```

Handlers are registered in code by `id`:
```js
PillBuilder.registerHandler('measure', {
  fn: function() { A.toggleMeasure(); },
  isActive: function() { return !!A._measureOn; }
});
```

PillBuilder merges JSON (what to show) + handlers (what to do). New icons
added to JSON appear in pill + Help panel automatically.

### 2. `key` Property → Single-Place Shortcuts

Currently shortcuts live in TWO places: `_shortcuts` (scene.js) AND `_actions`
(panels.js). S282 unifies: each action declares `key: ','` and PillBuilder
auto-registers the shortcut. Scene.js shortcut map becomes derived, not authored.

### 3. Settings Panel Features (incremental)

**Phase 1 — Pill editor:**
- List of all pill icons with toggle (show/hide) + drag handle (reorder)
- Saves to localStorage key `bim_pill_config`
- Reset to defaults button
- §-tagged log: `§SETTINGS_SAVE items=N hidden=N`

**Phase 2 — Locale + Rates:**
- Locale picker (reads `locales/*.js` manifest)
- Rate template picker (reads `rates/*.json` manifest)
- Both already work via `_TRL_LOADER` and rate loader — Settings just surfaces them

**Phase 3 — Gantt reorder (TimeMachine):**
- Gantt phase list is the same pattern: `{id, name, duration, deps}`
- User drags to reorder → TM plays in that order
- Saved per-building to IndexedDB
- This is the high-value unlock: planner defines construction sequence,
  TM visualises it, BOQ costs it in that order

### 4. Settings UI — Excel-like Property Sheet

The Settings panel uses a **two-column property sheet** layout — the same pattern
Excel uses for structured data editing. Reusable by Gantt, ERP, any JSON editor.

```
┌──────────────────────────────────────────────┐
│  Settings                              [×]   │
├────────────────────┬─────────────────────────┤
│  ≡ Pill Icons      │ [show/hide toggles]     │  ← accordion section
│    ≡ Measure       │ ✓ visible   key: M      │  ← drag handle + row
│    ≡ X-Ray         │ ✓ visible   key: X      │
│    ≡ Find          │ ✓ visible   key: —      │
│    ≡ Settings      │ ✓ visible   key: =      │
├────────────────────┼─────────────────────────┤
│  ≡ Locale          │ [en_US ▾] dropdown      │  ← chooser
├────────────────────┼─────────────────────────┤
│  ≡ Rate Template   │ [Malaysia ▾] dropdown   │  ← chooser
├────────────────────┼─────────────────────────┤
│  ≡ Theme           │ [Dark ▾]                │
├────────────────────┼─────────────────────────┤
│  ≡ Gantt Phases    │ [reorder rows]          │  ← same drag pattern
│    ≡ Foundation    │ 30 days  STR            │
│    ≡ Columns       │ 15 days  STR            │
│    ≡ Slabs         │ 20 days  STR            │
│    ≡ Walls         │ 10 days  ARC            │
└────────────────────┴─────────────────────────┘
```

**Left column:** drag handle (≡) + label. Dragging reorders rows within a section.
Accordion headers expand/collapse sections. Same interaction as HUD storey list.

**Right column:** value editor, type-driven:
- `toggle` → checkbox (show/hide pill icons)
- `choice` → dropdown (locale, rate template, theme)
- `text`   → input field (custom label, shortcut key)
- `number` → input + stepper (duration, tolerance)
- `color`  → swatch picker (discipline colors)
- `readonly` → display only (building name, element count)

**Data shape for each section:**
```json
{
  "section": "Pill Icons",
  "rows": [
    { "id": "measure", "label": "Measure", "fields": [
      { "key": "visible", "type": "toggle", "value": true },
      { "key": "shortcut", "type": "text", "value": "M", "readonly": true }
    ]},
    { "id": "xray", "label": "X-Ray", "fields": [
      { "key": "visible", "type": "toggle", "value": true },
      { "key": "shortcut", "type": "text", "value": "X", "readonly": true }
    ]}
  ],
  "reorderable": true
}
```

The renderer walks sections → rows → fields and builds the DOM.
Same renderer serves pill config, Gantt schedule, rate editor, locale picker.
No special code per section — just different JSON shapes.

**Gantt reuse:** same two-column layout, left = phase name (draggable),
right = duration + discipline. Drag reorders construction sequence.
TM reads the new order. BOQ costs in the same order.

### 6. ListBuilder (generalised from PillBuilder)

```js
// Same pattern, different consumer
var gantt = ListBuilder({
  items: phases,           // [{id, name, duration, deps}]
  container: ganttEl,      // DOM element
  render: function(item) { return ganttRowDOM(item); },
  storageKey: 'gantt_order_' + buildingId,
  onReorder: function(newOrder) { TM.setSequence(newOrder); }
});
```

PillBuilder becomes `ListBuilder` + pill-specific rendering. The reorder,
persistence, drag, and sync logic is shared.

### 7. Everything is JSON — One Template, Zero Learning Curve

The property sheet pattern applies to EVERY configurable surface in the app.
The user learns ONE interaction (drag row, toggle field, pick from dropdown)
and it works everywhere — pill, Help, Gantt, rates, locale. Even Help panel
content is declarative JSON describing what each tool does.

**Help panel becomes data-driven:**
```json
{
  "section": "Keyboard Shortcuts",
  "rows": [
    { "id": "measure", "label": "Measure", "fields": [
      { "key": "shortcut", "type": "text", "value": "M", "readonly": true },
      { "key": "desc", "type": "readonly", "value": "Measure distances between elements" },
      { "key": "children", "type": "readonly", "value": "Tolerance slider, Clear, CSV" }
    ]}
  ]
}
```

Adding a new tool = one JSON entry. No code change in Help renderer, Settings,
or PillBuilder. **One JSON template governs icon, shortcut, Help text, Settings
visibility, and panel creation.** User learning curve = learn one interaction,
use it everywhere.

### 8. Cross-App Reuse

| App | List | Items | Persist |
|-----|------|-------|---------|
| Viewer pill | Icons | `{id, icon, fn, panel}` | localStorage |
| ERP pill | ERP icons | `{id, icon, fn, panel}` | localStorage |
| Doc mode pill | Doc icons | `{id, icon, fn}` | localStorage |
| Gantt | Phases | `{id, name, duration}` | IndexedDB per building |
| Rate templates | BOQ lines | `{id, desc, unit, rate}` | rates/*.json |
| Locale | Translations | `{key, value}` | locales/*.js |

## Scope

- [ ] Phase 1: Add `key` to `_actions`, PillBuilder auto-registers shortcuts
- [ ] Phase 1: Settings panel shows pill icon list with show/hide toggles
- [ ] Phase 1: Drag-to-reorder pill icons, persist to localStorage
- [ ] Phase 2: Extract ListBuilder from PillBuilder
- [ ] Phase 2: Locale + rate picker in Settings
- [ ] Phase 3: Gantt phase reorder via ListBuilder → TM playback
- [ ] Phase 3: Per-building persistence to IndexedDB

## Files

| File | Role |
|------|------|
| `viewer/pill_builder.js` | Current pill framework — becomes ListBuilder consumer |
| `viewer/list_builder.js` | New: generalised reorderable list (Phase 2) |
| `viewer/panels.js` | Main pill `_actions` array — moves to JSON (Phase 1) |
| `viewer/scene.js` | `_shortcuts` map — derived from `key` props (Phase 1) |
| `viewer/erp.html` | ERP pill — declares own JSON (Phase 2) |
| `viewer/time_machine.js` | Gantt phase reorder consumer (Phase 3) |

## Constraints

- No perf impact on streaming/rendering — Settings is lazy-loaded
- No server — all persistence is localStorage/IndexedDB
- No external deps — vanilla JS, no drag library (pointer events)
- JSON files are human-editable — no minification
- Each HTML page owns its pill JSON — no shared common file
- Help panel reads from PillBuilder.actions dynamically (S281, done)

## Test

- `§SETTINGS_SAVE` log after every save
- `§PILL_SYNC` log confirms highlight after reorder
- `§SHORTCUT_AUDIT` confirms shortcuts match `key` props
- audit_input_registry.js verifies no dead shortcuts after refactor
