# ⚠ DO NOT REMOVE — Read the log after every run

## S254: Hourglass Panel Drawers — Gantt Mini + Dashboard

### Scope
Extend the time machine (hourglass) panel with two animated drawers:
- **Bottom drawer** (📊): mini Gantt bars tracking the playback cursor
- **Right drawer** (📋): dashboard overview — phase legend, resource crews, S-curve sparkline

Both drawers share the same `_ops[]` and `_cursor` — same JS context, zero sync.

### Why
The 4D experience lives in the viewer window. The separate `boq_charts.html` tab required BroadcastChannel sync and ghostglass.js — fragile, laggy, two competing animation systems. This spec consolidates 4D playback + visualization into a single panel. The `boq_charts.html` page remains as a standalone 5D analytics page (cost/BOQ charts only, no scene control).

### Current State (S253e done)
- Hourglass panel: ◀ ▶ ⏪ ⏩, slider, DAY/HR/MIN, ☀ sun cycle, 👁 camera follow
- `drawGanttMini()` exists: canvas bars grouped by storey|phase, hairline cursor, click-to-seek
- `_ganttVisible` toggle on 📊 button, gantt box `display:none/block`
- (-) panel toggle hides all UI chrome — hourglass panel stays = clean cinema mode

### Spec

#### §1 Bottom Drawer — Mini Gantt (📊)

**Trigger**: 📊 button (already wired as `tm-gantt`)

**Animate**: slide down from panel bottom edge, 200ms ease-out. Close = slide up 200ms ease-in. Use `max-height` transition (0 → 200px) with `overflow:hidden` — no layout jumps.

**Content** (already implemented, refinements below):
- Canvas with horizontal bars, one per `(storey, phase)` group from `_ops[]`
- Bars colored by phase (PHASE_COLORS map)
- Orange hairline tracks `_cursor` in real-time (redrawn in `renderAtTime`)
- Active bar(s) get orange stroke border
- Click bar → seek to task start timestamp
- Bar labels: 3-char phase abbreviation when bar > 40px

**Refinements to existing code:**
1. **Storey labels on left**: reserve 60px left margin. Print truncated storey name (max 8 chars) left of each bar row, 9px font, color #999. Only print if different from previous row (avoid repeats within same storey).
2. **Phase legend strip**: thin 14px row above canvas — colored squares + phase names, flexbox wrap. Saves vertical space vs labelling each bar.
3. **Hover tooltip**: `title` attribute on bar regions is impossible on canvas. Instead, on `pointermove` over canvas, find which bar is under cursor, show a small absolutely-positioned tooltip div: `"Aras 02 — Architecture (47 elements, Day 12–45)"`. Hide on `pointerleave`.
4. **Transition CSS**: add to panel style block:
   ```css
   #tm-gantt-box { transition: max-height 200ms ease-out; max-height: 0; overflow: hidden; }
   #tm-gantt-box.open { max-height: 200px; }
   ```
   Toggle adds/removes `.open` class instead of `display:none/block`.

**§-tags**: `§GANTT_MINI tasks=N` (existing), `§GANTT_MINI_SEEK ts=X bar="name"` on click

#### §2 Right Drawer — Dashboard (📋)

**Trigger**: new 📋 button after 📊 in panel header row. `id="tm-dash"`.

**Animate**: slide right from panel right edge, 200ms ease-out. Panel widens from 340px to 600px (or viewport-aware: `min(600px, 90vw)`). Close = shrink back to 340px. Use `width` transition on the panel itself.

**Content** — a compact vertical stack inside a new right-side column:

```
┌── existing 340px ──┬── 260px dashboard ──────────┐
│ [ 🔗 ☀ 👁 📊 📋 ] │  Phase Progress              │
│ DAY 47 | HR 14  [✕]│  ██████░░ Superstructure 68% │
│ Phase: Architecture │  ████████ MEP Rough-in  42%  │
│ [====slider======]  │  ███░░░░░ Architecture  31%  │
│ [⏪][◀][⏹][▶][⏩]  │                              │
│ ┌─ Gantt drawer ──┐ │  Crews Today                 │
│ │ bars + hairline  │ │  🏗️ Steel ×4  🧱 Mason ×3   │
│ └─────────────────┘ │  ⚡ Elec ×2   🔧 Plumb ×2   │
│                     │                              │
│                     │  ┌─ S-Curve sparkline ──┐    │
│                     │  │  ╱‾‾‾                │    │
│                     │  │ ╱     47% complete    │    │
│                     │  └──────────────────────┘    │
└─────────────────────┴──────────────────────────────┘
```

**Data source** — all from `_ops[]` and `_cursor`, computed in a `drawDashboard()` function:

1. **Phase progress bars**: for each phase, count ops where `end_ts <= _cursor` vs total. Show colored bar + percentage. Phases in standard order.

2. **Crews today**: from `_ops` currently in frontier (`start_ts <= _cursor < end_ts`), extract `resource` from parameters. Count distinct resources, show icon + crew name + count. Icon map reuse from `boq_charts.html` RES_ICONS.

3. **S-Curve sparkline**: tiny 100×40px canvas. X = project timeline, Y = cumulative % complete. A dot marks current position. Computed once (on first open), redraw dot position each tick.

4. **Day counter**: "Day 47 / 1235 — 3.8% complete" below the sparkline.

**Refresh**: `drawDashboard()` called from `renderAtTime()` when `_dashVisible`, same pattern as `drawGanttMini()`. Phase/crew/S-curve calculations are lightweight — just counting ops vs cursor.

**§-tags**: `§DASH_OPEN phases=N crews=N`, `§DASH_PHASE name pct%`

#### §3 Drawer Interactions

- **Both open simultaneously**: panel goes 600px wide + Gantt drops below. The panel becomes an L-shape. On mobile (<600px viewport), only one drawer at a time — toggling one closes the other.
- **(-) panel toggle**: drawers respect it — if user hits (-) to hide all chrome, drawers close with the panel. On (+) restore, drawers return to their previous open/closed state.
- **Deactivate cleanup**: both `_ganttVisible` and `_dashVisible` reset to false, classes removed, widths restored.
- **Panel drag**: drag handle (the header row) works the same whether drawers are open or closed. The panel moves as a unit.

#### §4 Immersive Mode

When user activates hourglass AND hits (-) to hide all other UI:
- Only the hourglass panel remains on screen
- 3D scene fills the viewport — clean cinema
- With both drawers open: panel becomes an embedded control room at bottom-center
- ☀ sun cycle + 👁 camera follow + ▶ play = fully immersive 4D construction walkthrough
- This is the "presentation mode" experience — no code changes needed, emerges from existing (-) toggle + drawer design

#### §5 Rename 4D/5D → 5D and strip 4D playback from boq_charts.html

**Rename** — all 4D lives in the hourglass now. Three locations to update:
- `deploy/dev/index.html:337` — toolbar button title `"4D/5D Export"` → `"5D BOQ/Cost"`
- `deploy/dev/boq_charts.html:5` — `<title>BIM OOTB — 4D/5D Analytics</title>` → `5D Analytics`
- `deploy/dev/boq_charts.html:43` — `<h1>BIM OOTB — 4D/5D Analytics</h1>` → `5D Analytics`
- `deploy/dev/boq_charts.html:218` — TRL key `ui_tt_export: '4D/5D Export'` → `'5D BOQ/Cost'`
- `deploy/dev/mep_report.html:159` — reference text mentioning "4D/5D Analytics page"

**Strip 4D playback/sync code:**
- Remove: `4D_PLAY`, `4D_SEEK`, `4D_PAUSE`, `4D_RESET` message handling
- Remove: `startPlayTimer()`, `applyScrub()`, Play/Stop/Scrub controls, scrub line/handle/tooltip
- Remove: ghostglass dependency
- Keep: `4D_QTO_REQUEST`/`4D_SCHEDULE_REQUEST` for populating cost/BOQ charts
- Keep: all 9 chart panels (cost pie, S-curve, milestones, etc.) as read-only analytics
- Keep: `buildScheduleFromOps()` for chart data (not scene control)

### Key Files
- `deploy/dev/time_machine.js` — all drawer code lives here (same IIFE)
- `deploy/dev/panels.js` — (-) toggle, `toggleAllPanels()` (no changes needed)
- `deploy/dev/boq_charts.html` — future strip of 4D controls (separate session)

### Implementation Order
1. §1 bottom drawer animation (refine existing `drawGanttMini`)
2. §1 storey labels + legend + hover tooltip
3. §2 right drawer — panel width transition + dashboard div
4. §2 phase progress + crews + sparkline
5. §3 drawer interaction rules
6. Tests: §-tag logs prove drawer data matches kernel_ops

### Acceptance Criteria
- `§GANTT_MINI tasks=N` shows task count on first open
- `§GANTT_MINI_SEEK` confirms click-to-seek works
- `§DASH_OPEN` confirms dashboard data loaded
- Slider drag ↔ hairline movement is smooth (no flicker)
- Click Gantt bar → scene jumps to correct timestamp
- Dashboard phase percentages increase monotonically during forward play
- Crew count matches frontier ops at any cursor position
- Mobile: only one drawer at a time, no overflow
- (-) toggle hides everything; (+) restores drawer state
- Deactivate cleans up all drawer state
- `?tm=play` share link still works with drawers closed (default)

### Testing
- Verify with Terminal (48k elements, 23 storey-bands, 61 tasks)
- Verify with SampleHouse (58 elements, tiny — drawer should still render)
- Mobile viewport: drawers fit within 92vw panel width
- Performance: `drawGanttMini` + `drawDashboard` combined < 2ms per frame (measure with `performance.now()`)

### Lessons Learned (from S253d/S253e — carry into this work)

#### L1: Z-gap banding was wrong — use the data you have
The original `injectGantt()` inferred storeys from Z-coordinate gaps (1.5m threshold). Terminal building has 48k elements with MEP filling inter-floor gaps — result: only 2 Z-bands for 9+ storeys. Phase dependencies broke, MEP Final started before Architecture. **Fix**: use `elements_meta.storey` directly, ranked by min `center_z`. The IFC file already knows its storeys — don't re-infer what's already extracted. **Lesson: prefer explicit metadata over inferred heuristics. The extraction pipeline already solved this problem.**

#### L2: Two-tab sync is the wrong architecture for real-time 4D
BroadcastChannel between boq_charts.html and the viewer required ghostglass.js (competing animation), task-index-based seeking (lossy vs timestamp), and had no access to sun/eye/sparks effects. Every bug was a sync bug. **Lesson: if two systems need the same cursor, put them in the same JS context. A BroadcastChannel is a network boundary — treat it as one. Same-window function calls are free and instant.**

#### L3: Log mandate is non-negotiable
In S253e, test results were read from inline Bash output instead of saved log files. This violates the standing rule: "save output to a log file, read the log before conclusions — exit code is not evidence." Inline terminal output can be truncated, reordered, or stale. **Always: `node test.js > /tmp/test.log 2>&1`, then `Read /tmp/test.log`.** The log is the witness.

#### L4: Test mirrors production code exactly
`test_s253_real_db.js` duplicates the scheduling algorithm from `time_machine.js`. When the Z-band fix changed production code, the test had to change identically. **Lesson: when test simulates production logic, keep them in lockstep. Change one → change the other in the same commit. Better yet, extract shared logic into a function both can call (future refactor).**

#### L5: Don't split what belongs together
The Gantt chart was in a separate HTML page, the scene control in the viewer, the schedule in kernel_ops, the animation in ghostglass. Four files, three communication channels, two animation systems. The user's insight: "why even call up the GanttChart in 4D5D to play since it is that difficult in synching?" **Lesson: features that share a cursor and a scene belong in one module. Split by concern (rendering vs data vs UI), not by page.**

#### L6: Worktree for safety on structural changes
The mini Gantt drawer was built in a git worktree (`dev/scene-timeline` branch). If it broke, `full` branch was untouched. **Lesson: use worktrees for any change that touches panel HTML structure or adds new rendering functions. The fallback cost is zero.**
