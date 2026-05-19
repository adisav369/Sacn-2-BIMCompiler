# UI Strategy — Architecture, Core-to-Surface, Desktop & Mobile

## Part I: The Organizing Question

Three candidates for the codebase's spine:

| Candidate | What it gives you | What it misses |
|-----------|-------------------|----------------|
| **Java abstract patterns** (M_Product, M_BOM, EntityType guards) | Clean interfaces, type safety, guard rails | JS has no compiler. Porting Java abstractions to untyped JS creates ceremony without enforcement. |
| **5 core tables** (containers, items, documents, document_lines, journal) | Universal data model — restaurant, warehouse, BIM, farm all fit | Tables describe *what's stored*, not *what's rendered*. UI doesn't map 1:1 to tables. |
| **nD dimensions** (1D→8D + IoT) | Maps to user-visible features and the product roadmap | Some dimensions share code (3D+4D both touch meshes). Dimensions are a roadmap, not a module boundary. |

**Answer: Use all three, at different layers.**

```
┌─────────────────────────────────────────────────────┐
│  SURFACE (UI)         Organized by interaction mode  │
│  What the user sees   5 icons + overflow + HUD       │
├─────────────────────────────────────────────────────┤
│  DIMENSION (nD)       Organized by domain concern    │
│  What the user does   3D view, 4D time, 5D cost,    │
│                       6D carbon, 7D ops, 8D comply   │
├─────────────────────────────────────────────────────┤
│  CORE (5 tables)      Organized by data invariant    │
│  What the system      containers → items → documents │
│  stores               → lines → journal              │
├─────────────────────────────────────────────────────┤
│  GUARD (Java pattern) Organized by contract          │
│  What the system      EntityType, BOM recursion,     │
│  enforces             validation rules, GodMode      │
└─────────────────────────────────────────────────────┘
```

The Java side already owns GUARD and CORE (compiler, BOM, validation). The browser JS owns SURFACE and DIMENSION. They meet at the `.db` file — that's the contract boundary. No need to duplicate Java's abstract patterns in JS. The `.db` schema IS the interface.

---

## Part II: Core-to-UI Relationship

### The 5 Tables as Data Gravity

Every UI component ultimately reads from or writes to one of the 5 tables. This mapping clarifies ownership:

```
containers ──→ Scene graph (Three.js groups)
              → Storey panel (spatial hierarchy)
              → HUD (building → floor → room)
              → 2D Grid (plan view of one container level)

items      ──→ Streaming pipeline (geometry BLOBs → GPU)
              → Picking (raycast → element → metadata)
              → Info panel (GUID, class, storey, discipline)
              → Search/Find (query items by name/class)

documents  ──→ Clash reports (type=clash)
              → Snag sheets (type=snag)
              → Share links (type=share, URL-encoded state)
              → Work orders (type=wo, ERP flow)
              → Variation orders (type=vo, cost delta)

doc_lines  ──→ BOQ lines (qty × rate)
              → Clash pairs (element_a + element_b)
              → Measurement results (point_a + point_b + distance)
              → 4D task elements (task_id + element_guid)

journal    ──→ kernel_ops (append-only log)
              → Undo/redo stack
              → 4D Time Machine (fossil record playback)
              → IoT sensor readings (same log, automated writer)
              → Audit trail (who changed what, when)
```

**This is NOT a refactoring plan.** It's a mental model. The code doesn't need to reorganize around tables — it already implicitly follows this gravity. What it needs is for developers to *know* which table they're touching when they modify a UI component.

### The nD Axis as Feature Grouping

Each dimension maps to a cluster of JS files. This IS the natural module boundary:

```
3D (Geometry)     scene.js, streaming.js, helpers.js, dlod.js,
                  picking.js, import.js, import_worker.js
                  → The render pipeline. Heaviest code. Most coupled.

4D (Time)         time_machine.js, ghostglass.js, kernel_ops.js,
                  export_4d.js
                  → Already well-isolated (IIFE, BroadcastChannel).

5D (Cost)         rates.js, export_5d.js, variation_order.js,
                  ad_charts.js
                  → Mostly standalone. Reads items + doc_lines.

6D (Carbon)       [future] — Will parallel 5D: SUM(qty × factor).
                  Same UI pattern as cost charts.

7D (Facility)     [future] — Asset register, maintenance schedules.
                  Extends documents (type=maintenance).

8D (Compliance)   clash_rules.json, measure.js (clash detection),
                  ad_val_rule in ERP.db
                  → Reads containers (spatial) + items (elements)
                  → Produces documents (clash reports).

IoT (Tandem)      kernel_ops.js (same log, automated writer),
                  [future] sensor_bridge.js
                  → Writes journal entries. Same format as human ops.
                  → Threshold breach → auto-creates document (alert).
```

### What Java Guards Give Us (Without Porting Java to JS)

The Java BOM compiler enforces:
- **EntityType** — a product is ITEM, SERVICE, or RESOURCE. No mixing.
- **BOM recursion** — parent → children, each with qty. Each child can be a BOM.
- **GodMode bypass** — test harness skips guards for rapid prototyping.
- **Validation rules** — AD_Val_Rule fires before save.

In the browser, the `.db` arrives already compiled. The JS viewer doesn't need these guards — it reads the result. But the **ERP modules** (ad_ui.js, ad_parser.js) do need validation when users edit records in-browser. That's where Java patterns translate:

```
Java (compiler)              JS (browser ERP)
─────────────                ─────────────────
EntityType enum          →   category_registry.action_buttons
M_BOM.validate()         →   ad_val_rule evaluated client-side
X_M_BOMLine guards       →   document_lines INSERT trigger
GodMode                  →   role_band.js (RBAC, admin override)
```

The pattern isn't "port Java classes to JS." It's "the .db carries the rules; JS evaluates them at runtime."

---

## Part III: What Should Actually Be Refactored

### Not a Rewrite. Surgical Splits.

The codebase is 44K lines of working, deployed, scale-proven code. A rewrite would be reckless. But three specific tangles cause real pain:

#### 1. measure.js (2197 lines → 4 files)

```
measure.js (current: measurement + clash detection + clash UI + snag + reporting)
    ↓ split into
measure_core.js    (~200 lines)  — Two-point distance, area, volume
clash_engine.js    (~600 lines)  — R-tree queries, rule matching, overlap calc
clash_ui.js        (~800 lines)  — Matrix panel, fly-to, hover, list navigation
clash_snag.js      (exists, 298) — Annotation canvas, snag storage
clash_report.js    (exists, 502) — HTML/CSV export
```

**Why:** Changing measurement breaks clash. Changing clash UI breaks snag. They're distinct 8D (compliance) vs 3D (geometry) concerns forced into one file.

#### 2. Storey/Discipline Filter State (scattered → one owner)

Currently: panels.js sets filters, picking.js reads them, helpers.js provides filter functions, tools.js reacts to them. Four files touching one concept.

```
filter_state.js    (~100 lines)  — Single source of truth:
  A.activeStoreyFilter      (string or null)
  A.hiddenDiscs             (Set)
  A.setStoreyFilter(name)   (mutator + event)
  A.toggleDisc(disc)        (mutator + event)
  A.onFilterChange(cb)      (observer registration)
```

Panels, picking, and tools all subscribe via `A.onFilterChange()` instead of polling shared globals.

#### 3. APP Object Documentation (implicit → explicit)

The `A` object has 100+ properties set by 20+ files. No documentation of what's required vs optional. New contributors (or future-you) can't tell what's safe to remove.

```
// app_contract.js (documentation only, not runtime)
// Lists every A.* property, which file sets it, which files read it.
// Generated by: grep -rn 'A\.\w' deploy/dev/*.js | sort
```

This is a one-time audit, not a code change. It makes the implicit bus explicit.

### What NOT to Refactor

| Leave alone | Why |
|-------------|-----|
| Navigation subsystem (5 files) | Already well-isolated via shared `nav` object |
| Grid subsystem (10 files) | Clean window.GridDims/GridContours API |
| ERP subsystem (10 files) | Zero BIM coupling, independent IIFE |
| time_machine.js (2984 lines) | Large but self-contained IIFE, no tangling |
| Workers (3 files) | Isolated by Web Worker boundary |
| Global `A` object pattern | It works. ES6 modules would add a bundler dependency for zero user benefit. |

---

## Part IV: Technology Trajectory

### What's Advancing Under Us

| Technology | Current | Next | Impact on BIM OOTB |
|------------|---------|------|-------------------|
| **Three.js** | r160 ESM | r170+ (WebGPU renderer) | WebGPURenderer is opt-in. Our ESM import pattern handles the swap. BatchedMesh API stable. |
| **sql.js** | 1.14.1 + rtree-sql 1.7.0 | SQLite WASM via official sqlite.org build | Official build has OPFS (Origin Private File System) — persistent DB without IndexedDB serialization. Massive perf win for large buildings. |
| **WebGPU** | Not used | Chrome stable, Firefox/Safari shipping | GPU compute for clash detection (parallel bbox overlap), LOD culling, large-scale instancing. Phase 3 opportunity. |
| **WebXR** | Planned, not implemented | ARCore/ARKit mature, WebXR Device API stable | AR overlay of BIM model on physical site via phone camera. Extends sitecam.js. |
| **OPFS** | Not used | Available in Chrome/Edge/Firefox | Replace IndexedDB blob caching with file-system-like storage. Faster reads, no serialization overhead. |
| **Shared Workers** | Not used | Stable across browsers | Single SQL.js instance shared across tabs. Currently each tab loads its own WASM + DB. |
| **View Transitions API** | Not used | Chrome stable | Smooth transitions between 3D view and 2D plans, or between buildings in city view. |
| **Compression Streams** | Not used | Widely available | Compress .db files in-browser before share/upload. Currently uncompressed. |

### IoT Tandem — The Convergence Point

IoT doesn't require new architecture. It's already designed:

```
Human action     →  commitOp(db, 'MOVE_ITEM', {...})     →  kernel_ops row
IoT sensor       →  commitOp(db, 'SENSOR_READING', {...}) →  kernel_ops row
Threshold breach →  auto-creates document (alert)         →  documents row
Manager response →  commitOp(db, 'ACKNOWLEDGE', {...})    →  kernel_ops row
```

Same log. Same state machine. Same 5 tables. The viewer already plays back kernel_ops in Time Machine. An IoT sensor reading is just another fossil in the record.

**What's needed:**
1. `sensor_bridge.js` (~200 lines) — WebSocket or BLE listener that calls `commitOp()`
2. Heatmap overlay on 3D model — temperature/humidity/occupancy per container
3. Alert badge on icon pill — real-time threshold breach count
4. All three read from the same `.db` via the same `A.dbQuery()` path

### The 1D→8D+IoT Roadmap as Product Phases

```
SHIPPED (production)
  3D  Geometry viewer — 122K elements, 60fps, offline
  4D  Time Machine — construction phasing, kernel_ops playback
  5D  Cost — BOQ, variation orders, S-curve charts

NEXT (S265-S270)
  UI  TikTok-style icons, share refactor, HUD polish (this doc)
  8D  Clash compliance — rules-driven, occupancy classes

PLANNED (S270-S280)
  6D  Carbon — SUM(qty × emission_factor), same chart pattern as 5D
  7D  Facility — asset register, maintenance schedules, extends documents

HORIZON (S280+)
  IoT  Sensor bridge — WebSocket/BLE → kernel_ops → heatmap
  AR   WebXR overlay — extend sitecam.js with model registration
  GPU  WebGPU compute — parallel clash detection, large-scale culling
  OPFS Origin Private File System — replace IndexedDB for .db caching
```

---

## Part V: Desktop & Mobile Surface Design

### Governing Principle

BIM OOTB is a **viewer first**. Controls are invisible until needed. The model owns the screen. Every pixel of chrome must justify its existence.

Study TikTok (vertical icon column, right side), Instagram (bottom bar + overflow), and Google Maps (floating action buttons over a full-bleed map). These apps prove that complex tools can hide behind 5 icons.

---

### Current State (Problem)

| Issue | Detail |
|-------|--------|
| 12+ toolbar buttons | All visible, all competing for attention |
| Horizontal wrap layout | `flex-wrap` in `#search-box` — buttons reflow unpredictably on resize |
| Mobile clutter | 45vw panels left + 48vw toolbar right = no breathing room on 375px screen |
| No hierarchy | Measure (used 50x/session) and Screenshot (used 1x/session) have equal weight |
| No grouping | Navigation icons (Walk, Fly) sit next to analysis icons (Clash, Section) |
| 7 z-index layers | Panels fight for space — walk btn z:20, issues z:50, cam overlay z:2000 |

---

### Target Layout: Icon Pill — Right Edge, Vertical

A single floating pill (dark glass, `backdrop-filter: blur(8px)`) anchored to the right edge, vertically centered. Contains **5 primary icons** max. Everything else behind a three-dot overflow.

```
Desktop                          Mobile (portrait)
┌──────────────────────┐         ┌──────────────┐
│                      │         │              │
│                   [TM]│         │           [TM]│
│   3D MODEL        [📐]│         │  3D MODEL [📐]│
│   (full bleed)    [🔍]│         │           [🔍]│
│                   [📤]│         │           [📤]│
│                   [ ⋮]│         │           [ ⋮]│
│                      │         │              │
│ ┌─HUD─┐             │         │              │
│ │Bldg ▸│             │         │  ┌─status──┐ │
│ └──────┘             │         │  └─────────┘ │
└──────────────────────┘         └──────────────┘
```

### The 5 Primary Icons

| Slot | Icon | nD | Why it's primary |
|------|------|-----|-----------------|
| 1 | TM (Time Machine) | 4D | Most unique feature — nothing else in market does this |
| 2 | Measure | 3D | Highest daily use — distances, areas, quantities |
| 3 | Find | 3D | Indoor wayfinding, element search — core viewer task |
| 4 | Share | — | Frictionless link sharing — the growth loop |
| 5 | More (⋮) | — | Everything else — one tap away, not gone |

### Overflow Menu (three-dot opens)

Grouped by nD concern — the dimension model becomes the menu's information architecture:

```
── 8D Compliance ──
  ⚠  Clash Matrix
  ✂  Section Cut
  ☢  X-Ray

── 3D Navigation ──
  🚶 Walk Mode
  ✈  Fly Tour
  🏗  2D Plans

── 3D Display ──
  🕶  Sunglasses (color studio)
  🌙  Night Mode
  🖥  Fullscreen

── 5D Export ──
  📷  Screenshot
  📊  4D/5D Export

── Help ──
  🛟  Commands
```

Users don't see "8D Compliance" as a label — they see "Analysis." But the grouping follows the dimensional model internally, which means new 6D/7D features slot into the menu without redesign.

---

### Desktop vs Mobile

| Aspect | Desktop | Mobile |
|--------|---------|--------|
| **Icon size** | 36px tap target, 20px icon | 44px tap target, 24px icon (WCAG 2.5.8) |
| **Icon pill** | `right: 16px`, vertically centered | `right: 8px`, vertically centered |
| **Overflow** | Dropdown below ⋮, max-width 280px | Bottom drawer, full-width, slides up |
| **Dismiss** | Click outside or Esc | Swipe down or tap scrim |
| **HUD** | Top-left, expanded by default | Collapsed by default (building name only), tap to expand |
| **HUD auto-hide** | Never | Collapse after 5s of no interaction |
| **Info panel** | Bottom-right, 320px, shows on hover | Bottom sheet, full-width, tap to show, swipe to dismiss |
| **Storey/Disc** | Left side, always available | Inside HUD expand — no separate panels |
| **Sliders** | Inline next to icon pill | Bottom sheet, full-width |
| **Search input** | Visible above icons | Hidden until Find tapped, then full-width top bar |
| **Tooltips** | Hover (200ms delay) | Long-press |
| **Keyboard** | Full set (Alt+Z, Ctrl+M, etc.) | N/A |
| **Site Camera** | Hidden (no rear camera) | Overflow menu item |

### Touch Interaction (Mobile)

| Gesture | Action |
|---------|--------|
| Tap icon | Toggle tool on/off |
| Tap ⋮ | Open overflow drawer |
| Swipe down on drawer | Dismiss |
| Tap scrim | Dismiss |
| Long-press icon | Tooltip label |
| Tap 3D canvas | Dismiss panels, pick element |
| Pinch/pan on canvas | Orbit/zoom |

Rules: 44px minimum targets. `pointerup` not `click`. No `stopPropagation` on draggable panels. Passive touch listeners.

---

### Panel Priority

```
P1 (always wins):   3D canvas — never obscured on desktop
P2 (modal):         Overflow menu, Share sheet, Walk anchor prompt
P3 (persistent):    Icon pill — always visible, never auto-hides
P4 (contextual):    Info panel, Sliders — shown by user action
P5 (ambient):       HUD, Status bar — background info, can hide
P6 (takeover):      Issues panel, Clash matrix — full panel mode
```

On mobile: only ONE P4 panel at a time. P5 auto-hides when P4+ is open.

---

### HUD: Desktop vs Mobile

**Desktop (expanded by default):**
```
┌─ LTU A-House ─────────────────┐
│ 122,437 elements · 8 storeys  │
│ ████████░░ 85% streamed       │
│ ARC:52K MECH:38K ELEC:22K    │
└───────────────────────────────┘
```

**Mobile (collapsed by default, storeys+disciplines inside on expand):**
```
┌─ LTU A-House ▸ ─┐

  tap to expand ↓

┌─ LTU A-House ─────────┐
│ 122K elements          │
│ ████████░░ 85%         │
│ [GF] [L1] [L2] [Roof] │
│ [●ARC] [●MECH] [●ELEC]│
└────────────────────────┘
```

---

### Share (S264 absorbed into S265 Phase 3)

```
User taps Share icon
  → buildShareUrl() captures camera + pick + storey + xray + tm + clash + tour
  → navigator.share({ title, url }) on mobile (system sheet)
  → navigator.clipboard.writeText(url) on desktop (toast)
```

Save-as-DB / Save-as-IFC / Contribute moves to Overflow → Export.

---

### Visual Language

| Token | Value | Use |
|-------|-------|-----|
| `--accent` | `#4fc3f7` | Active state, links, headers |
| `--bg-dark` | `#1a1a2e` | Canvas background |
| `--glass` | `rgba(0,0,0,0.3)` + `blur(8px)` | All panels |
| `--text` | `#e0e0e0` | Body text |
| `--text-dim` | `#888` | Labels, secondary |

Icons: white on transparent, active = accent background. Emoji or inline SVG (no icon fonts — offline-first). No text labels (tooltip on hover/long-press).

---

## Part VI: What This Means for the Codebase

### The Honest Assessment

| Question | Answer |
|----------|--------|
| Do we need a rewrite? | **No.** 44K lines of working, deployed, scale-proven code. |
| Do we need Java patterns in JS? | **No.** The `.db` file IS the interface. Java guards compile the data; JS reads the result. |
| Do we need ES6 modules? | **No.** Would require a bundler, adds complexity, zero user benefit. The current script-tag + global-A pattern works for 103 files. |
| What do we need? | **3 surgical splits** (measure.js → 4 files, filter state → 1 owner, APP object audit) + **the UI surface redesign** (S265). |
| Should we organize by tables or by nD? | **nD for file grouping** (what the user does), **tables for data flow understanding** (what the system stores), **Java patterns for the compiler only** (what the system enforces). |

### The Layered Truth

```
USER SEES:     5 icons. Tap. Model appears. Share link. Done.
                    ↑
SURFACE:       index.html + tools.js + panels.js (UI chrome)
                    ↑
DIMENSION:     3D(streaming) 4D(time_machine) 5D(rates) 8D(clash)
                    ↑
CORE:          containers → items → documents → lines → journal
                    ↑
GUARD:         Java BOM compiler, EntityType, AD_Val_Rule
                    ↑
STORAGE:       .db file (SQLite). The universal contract.
```

The `.db` file is the API. Everything above it is presentation. Everything below it is compilation. That boundary is the architecture.

---

## Part VII: Share as Universal Context Bus

### The Pattern

Share is not a "share button." It's a **context serializer** — it reads whatever the user is currently doing and encodes it into a URL. The same icon, same position, same tap — but the output changes based on what's active.

This is exactly how iOS/Android system share works: the Share icon in Safari shares the current URL; in Photos it shares the current image; in Maps it shares the current pin. One gesture, context-aware payload.

### How It Works

```
User taps Share (always in slot 4 of icon pill)
        │
        ▼
  shareContext = collectShareState()     ← reads current app state
        │
        ▼
  shareContext resolves to ONE of:
        │
        ├─ DEFAULT    { camera, building }
        ├─ ELEMENT    { camera, building, pickedGuid }
        ├─ CLASH      { camera, building, clashPairGuids, overlap }
        ├─ SNAG       { camera, building, snagImage, severity }
        ├─ WALK       { camera, building, walkRoute, position }
        ├─ TOUR       { building, tour=play }
        ├─ MEASURE    { camera, building, pointA, pointB, distance }
        ├─ STOREY     { camera, building, storeyFilter }
        ├─ SECTION    { camera, building, sectionAxis, sectionDepth }
        ├─ TIMEMACHINE { building, tmCursor, phase }
        ├─ ERP_RECORD { erpWindow, erpRecordId }        ← future
        ├─ HEATMAP    { building, heatmapLayer, range }  ← future (6D/IoT)
        └─ ... any future screen context
        │
        ▼
  buildShareUrl(shareContext)    ← encodes into hash params
        │
        ▼
  navigator.share({ title, url })  OR  clipboard fallback
```

### The Context Collector (one function, extensible)

```js
function collectShareState() {
    var ctx = { type: 'default' };

    // Always: camera + building
    ctx.camera = { cx, cy, cz, tx, ty, tz };
    ctx.building = A.activeBuilding;

    // Priority cascade — most specific context wins
    if (A._tmActive && A._tmCursor)           ctx.type = 'timemachine';
    if (A.walkModeActive && A.walkPath)        ctx.type = 'walk';
    if (A._currentClashes && A._clashFocusIdx != null) ctx.type = 'clash';
    if (A.measureActive && A.measureFirstPoint) ctx.type = 'measure';
    if (A.pickedGuid)                          ctx.type = 'element';
    if (A.activeStoreyFilter)                  ctx.type = 'storey';
    if (A._sectionActive)                      ctx.type = 'section';

    // Attach context-specific data
    switch (ctx.type) {
        case 'element':     ctx.pick = A.pickedGuid; break;
        case 'clash':       ctx.clash = guidA + ',' + guidB; break;
        case 'walk':        ctx.walkPos = ...; break;
        case 'timemachine': ctx.tm = A._tmCursor; break;
        case 'measure':     ctx.mA = ...; ctx.mB = ...; break;
        case 'storey':      ctx.storey = A.activeStoreyFilter; break;
        case 'section':     ctx.secAxis = ...; ctx.secDepth = ...; break;
    }
    return ctx;
}
```

### Why This Scales to Any Future Screen

New features register their share context by setting a property on `A`. The collector reads it. No new Share code needed:

```
Future: 6D Carbon Heatmap
  → A._carbonLayer = 'embodied'
  → A._carbonRange = [0, 120]
  → collectShareState() sees it → ctx.type = 'heatmap'
  → URL: #bld=X&heatmap=embodied&range=0,120
  → Recipient opens → viewer restores heatmap state

Future: IoT Live Dashboard
  → A._iotSensor = 'temp_shed_3'
  → collectShareState() → ctx.type = 'iot'
  → URL: #bld=X&iot=temp_shed_3
  → Recipient opens → viewer loads sensor overlay

Future: ERP Record
  → A._erpWindow = 'M_Product'
  → A._erpRecordId = 1000023
  → collectShareState() → ctx.type = 'erp'
  → URL: #erp=M_Product&id=1000023
  → Recipient opens → ERP panel shows that record
```

The pattern: **set a property → collector reads it → URL encodes it → recipient restores it.** No new share UI, no new share.js code per feature. One `switch` case addition.

### URL Restore (Symmetric)

On page load, `restoreShareState(hash)` reverses the process:

```js
function restoreShareState(hash) {
    var p = new URLSearchParams(hash.substring(1));

    // Camera (always)
    if (p.has('cx')) setCameraFromParams(p);

    // Context-specific restore
    if (p.has('pick'))    highlightElement(p.get('pick'));
    if (p.has('clash'))   flyToClashPair(p.get('clash'));
    if (p.has('tour'))    autoStartTour();
    if (p.has('tm'))      seekTimeMachine(p.get('tm'));
    if (p.has('storey'))  filterStorey(p.get('storey'));
    if (p.has('section')) applySectionCut(p.get('secAxis'), p.get('secDepth'));
    if (p.has('heatmap')) loadHeatmap(p.get('heatmap'), p.get('range'));
    if (p.has('iot'))     openSensorOverlay(p.get('iot'));
    if (p.has('erp'))     openErpRecord(p.get('erp'), p.get('id'));
}
```

Each `if` block is independent. Adding IoT restore doesn't touch clash restore. The URL is the universal deep-link contract.

### What the User Experiences

The Share icon never changes position, never changes appearance. But:

| User is doing... | Share produces... | Recipient sees... |
|-----------------|-------------------|-------------------|
| Orbiting | Camera angle link | Same viewpoint |
| Tapped an element | Link highlights that element | Orange highlight + info panel |
| Viewing clash | Link with clash pair | Fly-to + red/blue overlap |
| Walking on site | Link with walk route + position | Walk mode at same spot |
| Playing Time Machine | Link with timeline cursor | Same construction phase |
| Measuring | Link with measurement points | Measurement labels restored |
| 2D plan view | Link with storey + section | Same cut plane |
| ERP record (future) | Link to specific record | ERP panel opens |
| IoT heatmap (future) | Link with sensor layer | Heatmap overlay restored |

**One icon. Zero configuration. Always relevant.**

### Share Badge (Optional Enhancement)

When the share context is richer than "default," show a tiny dot badge on the Share icon:

```
[📤]    ← default (just camera) — no badge
[📤•]   ← element picked, or clash active, or TM playing — blue dot
```

This teaches the user: "Share knows what you're doing right now."

### Other UCB Candidates

Share is the first UCB, but the same `collectContext()` → context-aware output pattern applies to **every primary icon** and several overflow items:

#### Screenshot (overflow → Export)

Same camera, same context. What's in the screenshot changes.

```
User taps Screenshot
        │
        ▼
  ctx = collectContext()     ← same function as Share
        │
        ├─ DEFAULT       → capture viewport as PNG, filename: "BLD_camera.png"
        ├─ ELEMENT       → capture + draw orange highlight label on image
        ├─ CLASH         → capture + red/blue overlay + metadata watermark
        ├─ MEASURE       → capture + measurement labels baked into image
        ├─ SECTION       → capture + section plane visible in shot
        ├─ TIMEMACHINE   → capture + phase label + date watermark
        ├─ 2D_PLAN       → capture 2D grid view (not 3D)
        ├─ HEATMAP       → capture + legend bar + sensor values (future)
        └─ ERP_RECORD    → capture ERP panel as PDF-style image (future)
        │
        ▼
  downloadPNG(canvas, filename)    ← filename includes context type
```

Today screenshots are dumb viewport captures. With UCB, the screenshot **knows what you're looking at** and embeds the relevant metadata.

#### Find/Search (primary slot 3)

What you're searching changes based on what's active.

```
User taps Find
        │
        ▼
  ctx = collectContext()
        │
        ├─ DEFAULT       → search elements by name/class/GUID
        ├─ CLASH         → search within current clash list (filter clashes)
        ├─ STOREY        → search within filtered storey only
        ├─ WALK          → search destinations (wayfinding, "find room 3.02")
        ├─ TIMEMACHINE   → search by construction phase ("find MEP phase 3")
        ├─ ERP_RECORD    → search ERP records (future)
        └─ IOT           → search sensors by name/zone (future)
        │
        ▼
  openFindPanel({ scope: ctx.type, filter: ctx.data })
```

The search box placeholder text changes: "Search elements..." → "Search clashes..." → "Search rooms..." → "Search phases..."

#### Help (overflow → Help)

Context-sensitive help. Show relevant commands for what the user is doing.

```
User taps Help
        │
        ▼
  ctx = collectContext()
        │
        ├─ DEFAULT       → show full command palette
        ├─ MEASURE       → show: "Tap two points · Double-tap to clear · Alt+M to exit"
        ├─ CLASH         → show: "Click row to fly · Double-tap to mark resolved · Long-press to snag"
        ├─ WALK          → show: "WASD to move · Space for eye height · Tap floor to teleport"
        ├─ TIMEMACHINE   → show: "Drag slider · Space to play/pause · S to toggle S-curve"
        ├─ 2D_PLAN       → show: "Drag grid lines · Click storey · Pinch to zoom"
        └─ ERP_RECORD    → show: "Tab for next field · Enter to save · Swipe for next record"
```

Instead of a generic help page, the user gets **3 lines relevant to what they're doing right now.**

#### Export (overflow → Export)

What gets exported depends on context.

```
User taps 4D/5D Export
        │
        ▼
  ctx = collectContext()
        │
        ├─ DEFAULT       → full BOQ export (all elements × rates)
        ├─ STOREY        → BOQ for filtered storey only
        ├─ CLASH         → clash report (HTML/CSV) for current matrix
        ├─ TIMEMACHINE   → Gantt chart export (phases × dates)
        ├─ MEASURE       → measurement log export (all measurements this session)
        ├─ ERP_RECORD    → export current record/window as Excel (future)
        └─ IOT           → sensor data export for date range (future)
```

### The UCB Contract

Every UCB-capable action follows the same shape:

```js
// 1. Collect (shared — one function for all)
var ctx = collectContext();

// 2. Branch (per-action — each action reads ctx.type)
switch (ctx.type) {
    case 'clash':  doClashVariant(ctx); break;
    case 'walk':   doWalkVariant(ctx);  break;
    default:       doDefaultVariant(ctx);
}

// 3. Log (always — §-tagged for verification)
console.log('§UCB action=' + actionName + ' ctx=' + ctx.type + ' ...');
```

Rules:
- `collectContext()` is **one function** used by Share, Screenshot, Find, Help, Export — never duplicated
- Each action only adds `switch` cases for contexts it understands — unknown contexts fall to `default:`
- New features (6D, 7D, IoT, ERP) add their context type to `collectContext()` ONCE — all UCB actions see it automatically
- The §-tag always includes `action=` and `ctx=` so the log proves which path was taken

### UCB Summary Table

| Primary/Overflow | UCB? | What changes by context |
|-----------------|------|------------------------|
| Time Machine | No | Always does one thing — play/pause timeline |
| **Measure** | Partial | Measure is always measure, but **labels** change (show cost in 5D mode, show phase in 4D) |
| **Find** | **Yes** | Scope changes: elements → clashes → rooms → phases → sensors |
| **Share** | **Yes** | Payload changes: camera → element → clash → walk → TM → IoT |
| **More (⋮)** | No | Static menu — opens overflow |
| Clash Matrix | No | Always opens clash matrix |
| Section Cut | No | Always toggles section plane |
| X-Ray | No | Always toggles transparency |
| Walk Mode | No | Always enters walk |
| Fly Tour | No | Always starts cinematic |
| 2D Plans | No | Always toggles 2D |
| Sunglasses | No | Always opens color studio |
| Night Mode | No | Always toggles night |
| Fullscreen | No | Always toggles fullscreen |
| **Screenshot** | **Yes** | Output changes: plain capture → annotated → clash overlay → measurement labels |
| **Export** | **Yes** | Format changes: full BOQ → storey BOQ → clash report → Gantt → sensor log |
| **Help** | **Yes** | Content changes: full palette → 3-line contextual tips |

**5 UCB actions:** Share, Screenshot, Find, Export, Help
**12 static actions:** do one thing regardless of context

---

## Part VIII: Anti-Patterns

1. **Don't port Java class hierarchies to JS** — ceremony without enforcement
2. **Don't add a bundler** — complexity for zero user benefit when scripts work
3. **Don't reorganize files by table** — files should group by user-visible concern (nD)
4. **Don't refactor what's isolated** — navigation, grid, ERP, time_machine are clean
5. **Don't auto-open panels on load** — viewer boots to clean 3D
6. **Don't add more than 5 primary icons** — overflow exists for a reason
7. **Don't create separate mobile/desktop codepaths** — one HTML, CSS handles differences
8. **Don't add icon fonts** — breaks offline. Inline SVG only (see `icons/lucide/`).
9. **Don't duplicate collectContext()** — one function, all UCB actions share it
10. **Don't make static actions context-aware** — X-Ray is always X-Ray. Don't overthink it.

---

## Part IX: UI Whitebox Verification Protocol

**This section is the standard reference. Cite it every time a UI component is added, moved, or changed.**

### Who Uses What

| Role | Tool | Purpose |
|------|------|---------|
| **Claude (coder)** | §-tagged console logs | Prove every logic path was taken. Read the log BEFORE claiming "done." This is the coder's primary instrument — the ally that catches silent failures, wrong branches, missed states. |
| **User (reviewer)** | Visual inspection only | Initial check (does it look right?) and final sign-off (deploy-worthy?). The user does NOT read §-logs. They trust the coder verified. |
| **Playwright (CI)** | Wiring checks | Scripts load, buttons exist, panels toggle. Secondary. |

**The §-tags exist for the coder, not the user.** They are the coder's self-check — the machine-readable proof that replaces "it looked fine to me." The user's time is spent on visual/UX review, not log inspection. The coder reads the log so the user doesn't have to.

### Rule: Every UI Change Gets §-Tags Before Deploy

No UI change is verified by "it looks right." It's verified by §-tagged `console.log()` output proving the logic path was taken. The coder saves the log, reads it, confirms every §-tag shows expected values — then presents the result to the user for visual review. See `feedback_logs_only.md`, `feedback_whitebox_before_deploy.md`.

### §-Tag Registry (Canonical List)

Every UI §-tag follows the pattern: `§UI_{COMPONENT} {key=value pairs}`

#### Icon Pill & Overflow

```
§UI_PILL rendered=true icons=5 position=right
§UI_ICON tap=clock|ruler|search|share|more target=44px
§UI_OVERFLOW open|close trigger=more|escape|scrim
§UI_OVERFLOW_ITEM tap={item} ctx={context}
```

**What to prove:**
- Exactly 5 icons rendered (no more, no less)
- Tap target >= 44px on mobile, >= 36px on desktop
- Overflow opens on ⋮ tap, closes on scrim/Esc/swipe-down
- Each overflow item fires its handler

#### UCB (Context Bus)

```
§UCB collect ctx={type} building={name} camera={cx,cy,cz}
§UCB action=share ctx={type} method=native|clipboard url={url}
§UCB action=screenshot ctx={type} filename={name} size={WxH}
§UCB action=find ctx={type} scope={elements|clashes|rooms|phases}
§UCB action=export ctx={type} format={boq|clash|gantt|excel}
§UCB action=help ctx={type} tips={count}
```

**What to prove:**
- `collectContext()` returns correct `type` for current app state
- Share URL includes all relevant hash params for that context
- Screenshot filename reflects context (not just "screenshot.png")
- Find scope changes when context changes
- Export format matches context

#### Share (S264/S265 Phase 3)

```
§SHARE_URL state={type} params={hash} url={full_url}
§SHARE_METHOD native|clipboard
§SHARE_RESTORE param={key} value={val}
§SHARE_BADGE visible=true|false ctx={type}
```

**What to prove:**
- URL contains camera params (cx/cy/cz/tx/ty/tz) when camera moved
- URL contains `pick=GUID` when element selected
- URL contains `clash=GUID1,GUID2` when clash focused
- URL contains `tour=play` when cinematic active
- `navigator.share()` called on mobile (not WhatsApp hardcode)
- Clipboard fallback fires on desktop
- Restore function parses all params on page load

#### HUD

```
§UI_HUD state=collapsed|expanded trigger=tap|auto|load
§UI_HUD_STOREY filter={name}|clear count={visible}/{total}
§UI_HUD_DISC toggle={name} hidden={list}
§UI_HUD_AUTOHIDE after=5000ms
```

**What to prove:**
- Mobile: HUD collapsed on load, expanded on tap, auto-collapses after 5s
- Desktop: HUD expanded on load, collapses on click
- Storey filter updates element count
- Discipline toggle hides/shows correct meshes

#### Panels

```
§UI_PANEL name={id} action=show|hide|auto-hide trigger=tap|context|priority
§UI_PANEL_CONFLICT winner={id} loser={id} rule=P{N}>P{N}
§UI_PANEL_MOBILE singleton=true closed={previous} opened={current}
```

**What to prove:**
- Only one P4 panel open at a time on mobile
- P2 (modal) dims everything below (scrim)
- P6 (takeover) hides P3-P5
- Priority conflicts resolved correctly

#### Touch & Interaction

```
§UI_TAP target={id} size={px}x{px} event=pointerup
§UI_SWIPE direction=down|left target={id} action=dismiss
§UI_TOOLTIP target={id} trigger=hover|longpress delay={ms}
§UI_KEYBOARD key={combo} action={fn}
```

**What to prove:**
- All tap targets >= 44px on mobile (log actual size)
- `pointerup` used (not `click`) for all button handlers
- Tooltips show on hover (desktop) / long-press (mobile)
- Keyboard shortcuts fire correct action

#### Theme & Visual

```
§UI_THEME glass=rgba(0,0,0,0.3) blur=8px border=rgba(255,255,255,0.08)
§UI_ICON_STYLE stroke=currentColor active=#4fc3f7 size={px}
§UI_TRANSITION target={id} duration={ms} easing={fn}
```

**What to prove:**
- Glass effect applied to all panels (no outliers)
- Active icon gets accent color background
- Transitions are 200ms ease (not janky, not missing)

### Verification Checklist (Copy This Per UI Change)

```markdown
## §-Verification: {S-number} {change description}

### Tags Added
- [ ] §UI_{COMPONENT} tags in code
- [ ] Tags fire on: load / tap / toggle / dismiss

### Logs Captured
- [ ] Run viewer with devtools open
- [ ] Save console output to `deploy/dev/test-results/s{NNN}_ui.log`
- [ ] Every §UI_ tag appears in log
- [ ] No §UI_ tag shows unexpected values

### UCB (if applicable)
- [ ] collectContext() returns correct type
- [ ] Share URL encodes current state
- [ ] Restore function parses URL correctly
- [ ] Screenshot filename reflects context

### Mobile
- [ ] Tap targets >= 44px (§UI_TAP size= proves it)
- [ ] pointerup used (§UI_TAP event=pointerup proves it)
- [ ] One P4 panel at a time (§UI_PANEL_MOBILE proves it)
- [ ] HUD auto-collapses (§UI_HUD_AUTOHIDE proves it)

### Desktop
- [ ] Hover tooltips fire (§UI_TOOLTIP trigger=hover)
- [ ] Keyboard shortcuts fire (§UI_KEYBOARD proves it)
- [ ] Overflow = dropdown (not drawer)

### Regression
- [ ] Run `whitebox_regression.js` — all existing §-tags still pass
- [ ] No §-tag removed without documented reason
```

### How to Use This Protocol

1. **Before coding:** read Part IX, identify which §-tag groups apply
2. **While coding:** add `console.log('§UI_...')` lines as you write handlers
3. **After coding:** open devtools, exercise the change, save log
4. **Before deploy:** run whitebox suite, confirm all §-tags present
5. **In PR/commit:** cite "Verified per UIStrategy.md Part IX" with log file path

This protocol is not optional. It is the standing law for UI changes. See `feedback_logs_only.md` — "NEVER guess from source. Only §-tagged runtime logs prove truth."
