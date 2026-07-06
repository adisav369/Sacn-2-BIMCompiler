# ⚠ DO NOT REMOVE — Scope: 4D Gantt → Viewer live sync. Read the log after every run.

# S240 — 4D Construction Playback: Gantt → Viewer Live Sync

## Concept
When the user plays the 4D Gantt timeline in `boq_charts.html`, the BIM viewer (`index.html`)
animates the construction sequence bar by bar — elements appear, highlight, and fade as their
task completes. Pause in Gantt = pause in viewer. Click a bar = jump viewer to that task state.

---

## §0 PRELIM CHECK — Gantt Quality Gate

**Before wiring sync, verify the Gantt chart is sharp and correct.**

The current Gantt (Chart 9 in `boq_charts.html`) shows the top 15 longest tasks as stacked
horizontal bars. Known issues to fix before sync:

### 0.1 Gantt Audit Checklist

Run against any loaded building. Log each check as `§4D_AUDIT_{name}`.

| # | Check | Pass Criteria | Log Tag |
|---|-------|---------------|---------|
| 1 | **Task count** | Every IFC class in the DB maps to a SEQUENCE_RULES entry (no DEFAULT fallback) | `§4D_AUDIT_COVERAGE unmapped=[list]` |
| 2 | **Phase order** | Tasks are sequenced Substructure → Superstructure → MEP Rough-in → Architecture → MEP Final → Finishes. No phase inversion. | `§4D_AUDIT_PHASE_ORDER ok=true` |
| 3 | **Storey order** | Within each phase, storeys run bottom-up (basement → ground → level 1 → ... → roof) | `§4D_AUDIT_STOREY_ORDER ok=true` |
| 4 | **Duration sanity** | No task > 120 days (suggests bad productivity rate). No task < 1 day. | `§4D_AUDIT_DURATION outliers=[list]` |
| 5 | **Overlap sanity** | Concurrent tasks on same storey are different trades only (no two MASON tasks on same floor) | `§4D_AUDIT_OVERLAP conflicts=[list]` |
| 6 | **Total duration** | Project total ≤ 3× naive serial sum (parallelism is working) | `§4D_AUDIT_TOTAL days=N serial=M ratio=R` |
| 7 | **Gantt bar labels** | Every bar label is readable (≤40 chars), no "[object Object]", no "undefined" | `§4D_AUDIT_LABELS ok=true` |
| 8 | **Task↔GUID resolution** | Every task resolves to ≥1 GUID in the DB. Zero-GUID tasks = orphaned. | `§4D_AUDIT_GUIDS orphaned=[list]` |

**Implementation:** Add `audit4DSchedule(scheduleData, db)` function in `boq_charts.html`.
Call it after `generateSchedule()`. Print all 8 log lines. If any check FAILS, show a
yellow warning banner above the Gantt chart: "⚠ Schedule has N issues — check console."

**Gate rule:** Do NOT proceed to BroadcastChannel sync until all 8 checks PASS for at
least one reference building (SampleHouse). Fix SEQUENCE_RULES or productivity rates first.

### 0.2 Reference Buildings for Audit

| Building | Elements | Expected Phases | Why |
|----------|----------|-----------------|-----|
| SampleHouse | ~60 | 3-4 (no MEP) | Simplest, fast audit cycle |
| Duplex | ~200 | 5-6 | Multi-storey, mixed trades |
| LTU AHouse | ~122K | All 6 | Stress test — must complete in <5s |

---

## §0.3 Schedule Template System

**The schedule is template-driven.** All construction knowledge lives in `rates.js` as
three editable lookup tables. Users can inspect and override these without touching code.

### Template Tables (in `rates.js`)

| Table | Purpose | Key → Value |
|-------|---------|-------------|
| `SEQUENCE_RULES` | IFC class → construction phase + sequence order | `IfcColumn → { phase:'Superstructure', sequence:2, resource:'STEEL_ERECTOR' }` |
| `LABOR_RATES` | Trade → daily rate, crew size, productivity per IFC class | `STEEL_ERECTOR → { rate_per_day:195, crew_size:4, productivity:{IfcBeam:8, IfcColumn:6} }` |
| `WORK_PACKAGES` | Named groups of IFC classes for BOQ/4D reporting | `PACKAGE 2 → { name:'SUPERSTRUCTURE', classes:['IfcColumn','IfcBeam',...] }` |

Supporting tables: `EQUIPMENT_RATES`, `EQUIPMENT_ALLOCATION`, `RATES` (material unit rates).

### How the Schedule is Generated

```
rates.js (templates)
    ↓
boq_charts.html → generateSchedule(activities, startDate)
    ↓
For each activity (IFC class + storey + discipline):
  1. Look up SEQUENCE_RULES[ifc_class] → get phase, sequence, resource
  2. Look up LABOR_RATES[resource].productivity[ifc_class] → get elements/day
  3. Duration = qty / productivity, capped at MAX_TASK_DAYS (parallel crews added)
  4. Scheduling: phases sequential, storeys bottom-up, same-phase trades concurrent
    ↓
scheduleData[] → array of tasks with WBS, dates, durations, phases
    ↓
Chart 9: Gantt (top 15 longest tasks)
Chart 7: Phase Duration bars
Chart 8: Milestone Timeline
```

### Template Check UI

Add a "📋 Check Template" button next to the Gantt chart title. On click:
1. Show a modal/panel listing all IFC classes found in the building
2. For each class, show: mapped phase, resource, productivity, material rate
3. Classes using DEFAULT_RULE highlighted in amber — user sees what's missing
4. Editable fields: user can override productivity or phase assignment
5. Changes stored in `localStorage` key `bim_4d_template_overrides`
6. On next `generateSchedule()`, merge overrides on top of `rates.js` defaults

```js
// Template override merge (in boq_charts.html, before generateSchedule)
const overrides = JSON.parse(localStorage.getItem('bim_4d_template_overrides') || '{}');
for (const [cls, patch] of Object.entries(overrides)) {
  if (SEQUENCE_RULES[cls]) Object.assign(SEQUENCE_RULES[cls], patch);
  else SEQUENCE_RULES[cls] = { ...SEQUENCE_DEFAULT, ...patch };
}
```

**Reset button** clears overrides and regenerates from `rates.js` defaults.

### Template Export/Import

- "Export Template" → downloads `schedule_template.json` with current SEQUENCE_RULES +
  LABOR_RATES + any overrides. Users can share templates between projects.
- "Import Template" → file picker, loads JSON, stores in localStorage, regenerates.

---

## §1 Product Vision

**"Watch your building get built."**

The Gantt drives a live construction simulation in the 3D viewer:
- Tasks play sequentially, bar by bar, matching the Gantt schedule
- Elements belonging to the active task: fully visible, highlighted (emissive glow)
- Elements already built (past tasks): visible but slightly transparent (0.4 opacity)
- Elements not yet built (future tasks): shown as thin wireframe bounding boxes only
- Pause/resume in Gantt instantly pauses/resumes the viewer animation
- Clicking any Gantt bar jumps the viewer to that exact construction state

---

## §2 Architecture

### Communication: BroadcastChannel
Both pages open in separate tabs, same session. No server needed.

```js
// Channel name
const BIM_CHANNEL = new BroadcastChannel('bim_4d');
```

### Message types (Gantt → Viewer)
```js
{ type: '4D_PLAY',   tasks: [...], speed: 1.0 }    // full task list + start playback
{ type: '4D_PAUSE',  taskIndex: N }                  // pause at current task
{ type: '4D_SEEK',   taskIndex: N }                  // jump to specific task (bar click)
{ type: '4D_RESET' }                                 // return all elements to normal
```

### Task list structure
`4D_PLAY` sends the **full resolved task list** (not just an index). Each task carries
its own GUIDs, resolved once on the Gantt side before playback starts:

```js
// Sent with 4D_PLAY
tasks: [
  {
    index: 0,
    name: 'Ground - Install Footing (STR)',
    phase: 'Substructure',
    storey: 'Ground',
    guids: ['abc-123', 'def-456', ...],   // resolved from DB
    startDay: 0,
    finishDay: 12,
    duration: 12,
  },
  ...
]
```

GUID resolution query (run once on Gantt side before broadcast):
```sql
SELECT guid FROM elements_meta
WHERE ifc_class = ? AND storey = ? AND building = ?
```

This avoids the viewer needing DB access for 4D — it just needs Three.js meshes.

---

## §3 Viewer-Side State Machine

Three element states during playback:

| State    | Visual                          | Three.js |
|----------|---------------------------------|----------|
| FUTURE   | Wireframe bbox, no fill         | `material.wireframe = true`, opacity 0.15 |
| ACTIVE   | Full solid + emissive highlight | `material.emissive = 0x00aaff`, opacity 1.0 |
| BUILT    | Solid, slightly transparent     | opacity 0.4, emissive off |

On `4D_SEEK(N)`:
1. Tasks 0…N-1 → mark BUILT (guids from task list)
2. Task N → mark ACTIVE
3. All remaining GUIDs → mark FUTURE
4. Camera fly-to centroid of ACTIVE elements

On `4D_PLAY`:
- Advance one task every `taskDuration` ms (configurable, default 1500ms)
- Fire `4D_SEEK(N)` internally for each step
- When last task done → `4D_RESET`

On `4D_RESET`:
- Restore all materials to original (cache originals before first playback)

---

## §4 Gantt-Side Controls (boq_charts.html)

Add playback toolbar above the Gantt chart:
```
[ ▶ Play ] [ ⏸ Pause ] [ ⏮ Reset ]   Speed: [1×] [2×] [4×]   [ 📋 Check Template ]
```

Play button:
1. Resolve GUIDs for ALL tasks (one-time DB query batch)
2. Broadcast `4D_PLAY { tasks: [...], speed }` with full resolved list
3. Start internal ticker — highlight active bar, advance on interval

Clicking a Gantt bar → broadcasts `4D_SEEK { taskIndex: N }`.
Pause → broadcasts `4D_PAUSE`.

Visual feedback on Gantt: active bar gets pulsing cyan border. Past bars get ✓ overlay.
Future bars dimmed (opacity 0.3).

---

## §5 Bounding Box Wireframes (FUTURE state)

For elements not yet built, show lightweight axis-aligned bbox wireframe:
```js
// On viewer init for 4D mode — pre-compute bboxes from mesh userData
const box = new THREE.Box3().setFromObject(mesh);
const helper = new THREE.Box3Helper(box, 0x334455);
helper.userData.bboxFor = guid;
scene.add(helper);
```
Bboxes hidden by default, shown only during 4D playback for FUTURE elements.

---

## §6 Material Cache

Before first playback, snapshot all original materials:
```js
APP._4dOriginalMaterials = new Map(); // guid → { opacity, emissive, wireframe }
```
`4D_RESET` restores from this cache. Prevents drift if user plays multiple times.

---

## §7 Implementation Plan

**Phase 0 — Prelim: Audit + Template Check** ✓ DONE (2026-05-07)
- `audit4DSchedule()` 8 checks — CTFL-triaged, all PASS for SampleHouse/Duplex/HHS
- rates.js: 3 new trades (CARPENTER/ROOFER/FINISHER), 12 new IFC classes, WORK_PACKAGES aligned
- 22-4d-audit.spec.js: 8 Playwright tests (212 total suite)

**Phase 1 — BroadcastChannel + click highlight** ✓ DONE (2026-05-07)
- BroadcastChannel('bim_4d') sender in boq_charts.html, listener in main.js
- Click Gantt bar → resolve GUIDs → highlight in viewer with phase colour
- Background click resets. Hover preserves 4D colours (tools.js _4dColor)

**Phase 2 — Ghost Glass animation (S240b)** ✓ DONE (2026-05-07)
- ghostglass.js: pure renderer (no internal timer), Gantt controls pacing
- Three states: GLASS (0.03 opacity) → ACTIVE (depthTest:false, shines through) → BUILT (solid)
- Orange scrub line overlaid on Gantt chart, drag to scrub timeline
- Play/Pause + speed selector (1×/2×/4×/8×), Day 0 = normal building
- Rotating highlight colours: orange→green→red→yellow→cyan per task
- 2-5s ripple stagger for large groups, InstancedMesh support
- Tested on Terminal (48K elements)

**Phase 3 — Camera fly-to** OPEN
- Camera flies to active task centroid on each seek

**Phase 4 — Construction sprites (S240c)** OPEN
- Truck/crane/worker sprite billboards at active task centroids
- Resource-type → icon mapping from SEQUENCE_RULES

**Phase 5 — Template export/import** OPEN
- Export/import JSON for schedule templates
- localStorage overrides merge

---

## §8 Files

| File | Status | What |
|------|--------|------|
| `deploy/dev/rates.js` | DONE | 12 new IFC classes, 3 trades, WORK_PACKAGES aligned |
| `deploy/dev/boq_charts.html` | DONE | audit, GUID resolution, BroadcastChannel, scrub slider, Play/Pause |
| `deploy/dev/ghostglass.js` | DONE | Glass-to-solid animation engine |
| `deploy/dev/main.js` | DONE | BroadcastChannel listener, ghostglass delegation |
| `deploy/dev/tools.js` | DONE | Hover preserves 4D colours |
| `deploy/dev/tests/specs/22-4d-audit.spec.js` | DONE | 8 Playwright tests for audit gate |

---

## §9 Witnesses

- W-4D_AUDIT: ✓ all 8 audit checks PASS for SampleHouse/HHS_Office (§4D_AUDIT_SUMMARY pass=8)
- W-4D_CHANNEL: ✓ Gantt sends → viewer receives (§4D_CHANNEL_READY on both sides)
- W-4D_GUID_RESOLVE: ✓ Terminal 48K elements, totalGuids=6871+ (§4D_GUID_RESOLVE)
- W-4D_GLASS_PLAY: ✓ Ghost glass plays on Terminal, all elements transition
- W-4D_GLASS_SEEK: ✓ Scrubber maps day→task, active elements shine through built
- W-4D_RESET: ✓ Day 0 restores all materials
- W-4D_TEMPLATE: OPEN — Check Template modal not yet built
- W-4D_SYNC: OPEN — frame-accurate pause (currently relies on Gantt timer)

---

## §10 Out of Scope (this sprint)

- Reverse playback (demolition sequence)
- Multi-building sync
- Mobile support
- Export playback as video
- IFC-embedded schedule (IfcWorkPlan) — see `prompts/4D_handling.md` for that spec
