# ⚠ DO NOT REMOVE — Read the log after every run

## S254: Hourglass Panel Drawers — Gantt Mini + Dashboard

### Scope
Extend the time machine (hourglass) panel with two animated drawers:
- **Bottom drawer** (📊): mini Gantt bars tracking the playback cursor
- **Right drawer** (📋): dashboard overview — time/cost donuts, phase legend, resource crews, S-curve sparkline

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

**Animate**: slide out from the panel's right edge **outward** (not inward). The drawer hangs off the right edge of the panel as a sibling column. Close = retract back.

**BUG FIX (from S254d):** The current CSS uses `position:absolute; right:0; top:0; bottom:0` which places the drawer **inside** the panel's bounding box, eating the 340px space. Fix: change `.tm-drawer-right` to `left:100%; top:0` so it extends outward to the right. The panel itself stays 340px — it does NOT need to widen with `dash-open`. Remove the `#time-machine-panel.dash-open{width:min(600px,90vw)}` rule entirely. The drawer is visually attached but positionally outside. On mobile (<600px), the drawer switches to bottom position below the panel content (stacking, not overlapping).

**CSS fix:**
```css
/* BEFORE (broken — grows inward) */
.tm-drawer-right{max-width:0;overflow:hidden;transition:max-width 200ms ease-out;opacity:0;
  position:absolute;top:0;right:0;bottom:0;padding:0;pointer-events:none;...}
#time-machine-panel.dash-open{width:min(600px,90vw)}

/* AFTER (correct — grows outward) */
.tm-drawer-right{width:0;overflow:hidden;transition:width 200ms ease-out,opacity 150ms;opacity:0;
  position:absolute;left:100%;top:0;padding:0;pointer-events:none;
  background:rgba(20,20,40,0.92);backdrop-filter:blur(12px);
  border:1px solid rgba(79,195,247,0.3);border-left:none;border-radius:0 12px 12px 0;
  max-height:80vh;overflow-y:auto}
.tm-drawer-right.open{width:260px;opacity:1;padding:10px;pointer-events:auto}
/* No dash-open width change on the panel itself */
@media(max-width:600px){
  .tm-drawer-right{left:auto;top:100%;width:100%!important;max-width:none;
    border-radius:0 0 12px 12px;border-left:1px solid rgba(79,195,247,0.3);border-top:none}
  .tm-drawer-right.open{width:100%!important;max-height:200px}
}
```

Also update `toggleDashDOM()` — remove `_panel.classList.toggle('dash-open', on)` since the panel no longer changes width.

**Content** — a compact vertical stack inside the outward drawer column:

```
┌── existing 340px ──┐┌── 260px dashboard (outside) ─┐
│ [ 🔗 ☀ 👁 📊 📋 ] ││  ┌─ Time Pie ─┐┌─ Cost Pie ─┐│
│ DAY 47 | HR 14  [✕] ││  │  ◔ 47%    ││  ◔ 38%    ││
│ Phase: Architecture ││  │  elapsed   ││  spent     ││
│ [====slider======]  ││  └───────────┘└───────────┘│
│ [⏪][◀][⏹][▶][⏩]  ││  Phase Progress             │
│ ┌─ Gantt drawer ──┐ ││  ██████░░ Superstructure 68%│
│ │ bars + hairline  │ ││  ████████ MEP Rough-in  42% │
│ └─────────────────┘ ││  ███░░░░░ Architecture  31% │
│                     ││  Crews Today                │
│                     ││  🏗️ Steel ×4  🧱 Mason ×3   │
│                     ││  S-Curve sparkline + Day cnt│
└─────────────────────┘└─────────────────────────────┘
```

**Data source** — all from `_ops[]` and `_cursor`, computed in a `drawDashboard()` function:

1. **Time donut** (restored): 120×120 canvas `id="tm-dash-time-pie"`, two arcs — elapsed days (blue `#4fc3f7`) vs remaining (dark). Center text: "Day 47" and "47%". Redrawn each `renderAtTime()` tick. These donuts existed in the old BroadcastChannel-based GanttChart player and were lost when 4D playback moved to the hourglass. Restore them here — same cursor, same data, no sync needed.

2. **Cost donut** (restored): 120×120 canvas `id="tm-dash-cost-pie"`, two arcs — cost of completed ops (green `#44cc44`) vs remaining budget (dark). Cost = sum of `getRate(cls)` for ops where `end_ts <= _cursor`. Center text: cost amount + "%". Uses `LABOR_RATES` from `rates.js` for consistent costing with boq_charts.html.

   Both donuts sit side-by-side in a flex row at the top of the dashboard drawer.

3. **Phase progress bars**: for each phase, count ops where `end_ts <= _cursor` vs total. Show colored bar + percentage. Phases in standard order.

4. **Crews today**: from `_ops` currently in frontier (`start_ts <= _cursor < end_ts`), extract `resource` from parameters. Count distinct resources, show icon + crew name + count. Icon map reuse from `boq_charts.html` RES_ICONS.

5. **S-Curve sparkline**: tiny 100×40px canvas. X = project timeline, Y = cumulative % complete. A dot marks current position. Computed once (on first open), redraw dot position each tick.

6. **Day counter**: "Day 47 / 1235 — 3.8% complete" below the sparkline.

**Donut drawing helper** (shared by both pies):
```javascript
function drawDonut(canvasId, pct, label, sublabel, color) {
  var canvas = document.getElementById(canvasId);
  if (!canvas) return;
  var ctx = canvas.getContext('2d');
  var w = canvas.width, h = canvas.height, cx = w/2, cy = h/2, r = Math.min(cx,cy) - 4;
  ctx.clearRect(0, 0, w, h);
  // Background ring
  ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.lineWidth = 10; ctx.strokeStyle = 'rgba(255,255,255,0.08)'; ctx.stroke();
  // Progress arc
  ctx.beginPath(); ctx.arc(cx, cy, r, -Math.PI/2, -Math.PI/2 + Math.PI*2*(pct/100));
  ctx.lineWidth = 10; ctx.strokeStyle = color; ctx.lineCap = 'round'; ctx.stroke();
  // Center text
  ctx.fillStyle = '#fff'; ctx.font = 'bold 14px sans-serif'; ctx.textAlign = 'center';
  ctx.fillText(label, cx, cy - 2);
  ctx.fillStyle = '#999'; ctx.font = '9px sans-serif';
  ctx.fillText(sublabel, cx, cy + 12);
}
```

**DOM additions** — add to the `tm-dash-col` HTML (before Phase Progress):
```html
<div style="display:flex;gap:8px;justify-content:center;margin-bottom:8px">
  <canvas id="tm-dash-time-pie" width="120" height="120" style="width:110px;height:110px"></canvas>
  <canvas id="tm-dash-cost-pie" width="120" height="120" style="width:110px;height:110px"></canvas>
</div>
```

**Refresh**: `drawDashboard()` called from `renderAtTime()` when `_dashVisible`, same pattern as `drawGanttMini()`. Phase/crew/S-curve/donut calculations are lightweight — just counting ops vs cursor.

**§-tags**: `§DASH_OPEN phases=N crews=N`, `§DASH_PHASE name pct%`, `§DASH_DONUTS time=N% cost=CUR+N`

#### §3 Drawer Interactions

- **Both open simultaneously**: Gantt drops below, dashboard extends to the right. No panel width change needed (dashboard is positionally outside). On mobile (<600px viewport), only one drawer at a time — toggling one closes the other.
- **(-) panel toggle**: drawers respect it — if user hits (-) to hide all chrome, drawers close with the panel. On (+) restore, drawers return to their previous open/closed state.
- **Deactivate cleanup**: both `_ganttVisible` and `_dashVisible` reset to false, classes removed, widths restored.
- **Panel drag**: drag handle (the header row) works the same whether drawers are open or closed. The panel moves as a unit (dashboard drawer follows via absolute positioning relative to panel).

#### §4 Immersive Mode

When user activates hourglass AND hits (-) to hide all other UI:
- Only the hourglass panel remains on screen
- 3D scene fills the viewport — clean cinema
- With both drawers open: panel becomes an embedded control room at bottom-center
- ☀ sun cycle + 👁 camera follow + ▶ play = fully immersive 4D construction walkthrough
- This is the "presentation mode" experience — no code changes needed, emerges from existing (-) toggle + drawer design

#### §5 Rename 4D/5D → 5D and ensure Gantt Chart shows in boq_charts.html

**Rename** — all 4D playback lives in the hourglass now. The 5D page is read-only analytics:
- `deploy/dev/index.html:337` — toolbar button title `"4D/5D Export"` → `"5D BOQ/Cost"`
- `deploy/dev/boq_charts.html:5` — `<title>BIM OOTB — 4D/5D Analytics</title>` → `5D Analytics`
- `deploy/dev/boq_charts.html:43` — `<h1>BIM OOTB — 4D/5D Analytics</h1>` → `5D Analytics`
- `deploy/dev/boq_charts.html:218` — TRL key `ui_tt_export: '4D/5D Export'` → `'5D BOQ/Cost'`
- `deploy/dev/mep_report.html:159` — reference text mentioning "4D/5D Analytics page"

**Strip 4D playback/sync code** (lines 1474-1863):
- Remove: `4D_PLAY`, `4D_SEEK`, `4D_PAUSE`, `4D_RESET`, `4D_RESOURCES` BroadcastChannel messages
- Remove: `startPlayTimer()`, `applyScrub()`, `pixelToDay()`, `dayToPixel()`, `dayToTaskIndex()`
- Remove: Play button, Stop button, Speed selector, scrub line/handle/tooltip DOM elements
- Remove: `updateResPanel()`, `RES_ICONS`, `_scrubActive`, `_playing`, `_playTimer` state
- Remove: Canvas drag handlers for scrub (pointerdown/move/up on ganttCanvas)
- Remove: ghostglass dependency (all `4D_PLAY`/`4D_SEEK` → ghostglass path in main.js)
- Keep: `_bim4d` channel + `4D_QTO_REQUEST`/`4D_SCHEDULE_REQUEST` relay (feeds chart data)
- Keep: `4D_HIGHLIGHT` on Gantt bar click (lines 1488-1512) — click bar → highlight in viewer. Read-only, useful.
- Keep: `4D_PING`/`4D_PONG` connectivity check
- Keep: Chart 9 Gantt rendering (lines 1418-1472) — the Chart.js horizontal bar chart. This is the "paper value" Gantt.
- Keep: `buildScheduleFromOps()` for chart data (not scene control)
- Keep: all 9 chart panels (cost pie, S-curve, milestones, Gantt, etc.) as read-only analytics
- Keep: `ganttHeader` but remove the `controlBar` (Play/Stop/Speed/Day label) from it. The sync badge (Hourglass OK) stays.

**Gantt Chart data dependency** — the Chart 9 Gantt in boq_charts.html gets its data via:
1. **Primary**: `_tryScheduleRelay()` → BroadcastChannel `4D_SCHEDULE_REQUEST` → viewer responds with `kernel_ops` (ELEMENT_PLACE ops). Built by `buildScheduleFromOps()`. Sync badge shows "Hourglass OK" (green).
2. **Fallback**: `generateSchedule()` builds a schedule independently from QTO data + `SEQUENCE_RULES` in `rates.js`. Sync badge shows "Run Hourglass first" (orange).

Both paths produce `scheduleData[]` that feeds Chart 9. The Gantt will always show — it does NOT require the hourglass to have been run. The difference is data quality: kernel_ops path uses the actual construction schedule with storey-aware sequencing; fallback path uses simpler phase-only grouping.

**Note for the user**: The construction schedule is ultimately driven by `SEQUENCE_RULES` and `LABOR_RATES` in `rates.js` — these are editable JS objects that control phase ordering, resource assignment, and installation durations per IFC class. The hourglass `injectGantt()` writes ELEMENT_PLACE ops to the `kernel_ops` table in the SQLite DB. The user can:
- Edit `rates.js` to change sequencing rules → re-run hourglass to regenerate
- Directly edit kernel_ops in the DB (advanced) via the ERP panel
- The 5D page always reflects whatever schedule data is available

**Net result**: boq_charts.html becomes a clean 5D analytics page. Gantt chart is still there as a read-only visualization — you see the bars, you can click to highlight in the viewer, but playback lives in the hourglass panel only.

### Key Files
- `deploy/dev/time_machine.js` — all drawer code lives here (same IIFE)
- `deploy/dev/panels.js` — (-) toggle, `toggleAllPanels()` (no changes needed)
- `deploy/dev/rates.js` — `SEQUENCE_RULES`, `LABOR_RATES`, `SEQUENCE_DEFAULT` (user-editable schedule config)
- `deploy/dev/boq_charts.html` — 5D analytics, Chart 9 Gantt, `buildScheduleFromOps()`

### Implementation Order
1. §2 BUG FIX: right drawer CSS — `left:100%` outward positioning, remove `dash-open` width
2. §2 time donut + cost donut canvases, `drawDonut()` helper
3. §2 wire donuts into `drawDashboard()` refresh cycle
4. §1 bottom drawer animation (refine existing `drawGanttMini`)
5. §1 storey labels + legend + hover tooltip
6. §3 drawer interaction rules
7. Tests: §-tag logs prove drawer data matches kernel_ops

### Acceptance Criteria
- `§GANTT_MINI tasks=N` shows task count on first open
- `§GANTT_MINI_SEEK` confirms click-to-seek works
- `§DASH_OPEN` confirms dashboard data loaded
- `§DASH_DONUTS time=N% cost=CUR+N` confirms both pies render and sync with cursor
- Slider drag ↔ hairline movement is smooth (no flicker)
- Click Gantt bar → scene jumps to correct timestamp
- Dashboard phase percentages increase monotonically during forward play
- Crew count matches frontier ops at any cursor position
- Right drawer extends **outward** to the right (not inward consuming panel space)
- Mobile: only one drawer at a time, no overflow
- (-) toggle hides everything; (+) restores drawer state
- Deactivate cleans up all drawer state
- `?tm=play` share link still works with drawers closed (default)
- boq_charts.html Chart 9 Gantt renders with or without hourglass session

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

---

### §6 Future Rendering Upgrades (separate implementation — do NOT mix with §1-§5)

This section documents browser-native 3D rendering improvements that enhance the 4D cinema experience. **Implement independently** — each item is a standalone enhancement that does not depend on or modify the hourglass drawer code.

Research-verified 2026-05-15. Corrections from original study notes marked.

#### §6.1 WebGPU Renderer — VIABLE but NOT drop-in ⚠

`WebGPURenderer` is production-ready in Three.js r171+ (Sep 2025). Auto-fallback to WebGL2 is real.

**NOT a drop-in replacement.** Migration requires:
- Import from `three/webgpu` (not `three`). Never mix both imports.
- `await renderer.init()` or use `setAnimationLoop()` — init is async, blank canvas if you forget.
- All `ShaderMaterial` / `RawShaderMaterial` (GLSL) must be rewritten in TSL (Three Shading Language).
- `EffectComposer` is dead on WebGPU — must use node-based `RenderPipeline`.
- `onBeforeCompile()` hooks not supported.

**Browser support:** Chrome/Edge full. Firefox still flag-only (`dom.webgpu.enabled`). Safari 26+ (macOS Tahoe). Mobile Chrome Android 147+, Safari iOS 26+. All gaps covered by WebGL2 fallback.

**For this project:** Check if any custom ShaderMaterial exists in the viewer. If not, migration is feasible. The main value is enabling §6.5 BatchedMesh perf gains and §6.7 GPU particles. Do §6.2-§6.4 first (WebGL-compatible), then §6.1 as a gate to §6.7.

```javascript
import * as THREE from 'three/webgpu';
const renderer = new THREE.WebGPURenderer({ antialias: true });
await renderer.init(); // REQUIRED — async
renderer.setAnimationLoop(render);
```

#### §6.2 Procedural Sky + Environment Lighting — VIABLE, highest ROI ✓

**CORRECTED:** Original study cited `RGBELoader` — renamed to `HDRLoader` in recent Three.js. But for this project, skip the HDR file entirely.

**Best approach: built-in `Sky` addon** — zero file download, procedural, pairs perfectly with ☀ sun cycle.
```javascript
import { Sky } from 'three/addons/objects/Sky.js';
const sky = new Sky();
sky.scale.setScalar(450000);
scene.add(sky);
// Feed to PMREMGenerator for PBR environment lighting
const envMap = pmremGenerator.fromScene(sky).texture;
scene.environment = envMap;
```
Advantages over HDR file:
- No download (critical for no-server single-HTML architecture)
- Sun position adjustable in real-time → syncs with hourglass ☀ cycle
- Turbidity, Rayleigh, Mie scattering all configurable
- PMREMGenerator converts it to cubemap for PBR reflections

If HDR file is ever needed (e.g. interior scenes), use `HDRLoader` (not `RGBELoader`):
```javascript
import { HDRLoader } from 'three/addons/loaders/HDRLoader.js';
```
Free HDR skies: Poly Haven (CC0, 1K = 1-2MB download, sufficient for PMREMGenerator).

#### §6.3 Atmosphere — REPLACED by §6.2 ✗

**CORRECTED:** `@takram/three-atmosphere` is real (npm v0.16.0) but **wrong for this project**:
- Requires React Three Fiber as peer dependency (we use vanilla Three.js)
- Designed for planetary/GIS scale, not building-scale BIM
- Forces Lambertian BRDF (conflicts with PBR materials)

**The built-in `Sky` addon in §6.2 covers this use case.** It provides Preetham atmospheric scattering model with sun position, turbidity, and Rayleigh controls — exactly what the hourglass ☀ cycle needs. No separate library required.

#### §6.4 Post-Processing — VIABLE on current WebGLRenderer ✓

**CORRECTED:** `RenderPipeline` (r183+) requires WebGPURenderer. On our current WebGLRenderer, use `EffectComposer`:

**Ambient Occlusion** (biggest visual win after sky):
- Built-in: `HBAOPass` (Horizon-Based AO) — best quality/perf ratio, official example `webgl_postprocessing_hbao`
- Third-party: `N8AOPass` (`n8ao` npm) — drop-in, quality presets, community recommended
- Avoid: `SSAOPass` (too slow for 48K elements)
- Note: hardware MSAA incompatible with screen-space AO — use SMAAPass alongside

**Bloom**:
- `UnrealBloomPass` — still the standard. Mip-chain approach, good quality.
- Reduce bloom resolution to 0.5x for perf on large scenes.

**Performance estimate at 48K elements:**
- AO requires depth/normal pre-pass = 1 extra scene render (the expensive part)
- AO computation: ~2-4ms at 1080p
- Bloom: ~1-3ms at 1080p
- Total: ~1 extra render + 3-7ms screen-space work per frame
- **Do §6.5 instancing BEFORE AO** — the pre-pass benefits from reduced draw calls

```javascript
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
// HBAOPass or N8AOPass for AO
```

#### §6.5 100K+ Element Performance — VIABLE, critical for Terminal ✓

**CORRECTED:** InstancedMesh has NO `setVisibleAt()`. BatchedMesh does.

**BatchedMesh** (r156+, stable) — best fit for BIM + 4D playback:
- Many different geometries, one material, one draw call via WebGL multi-draw
- **Native `setVisibleAt(index, bool)`** — perfect for 4D show/hide during construction
- Group by material: one BatchedMesh per material type (concrete, glass, steel, etc.)
- 48K elements → ~5-20 draw calls (vs 48,000 naive). **300-5000x reduction.**

**InstancedMesh** — for truly repeated elements only (500 identical light fixtures):
- No `setVisibleAt()` — workaround: swap matrix with last active instance + decrement `count`
- Need index mapping table for 4D playback

**three-mesh-bvh** (npm `three-mesh-bvh`, github `gkjohnson/three-mesh-bvh`) — standard BVH:
- 3-line monkey-patch integration
- 80K polygon model at 60fps raycasting (vs unusable without)
- `raycaster.firstHitOnly = true` for additional speedup
- BVH build is one-time cost (can offload to Web Worker)

**Implementation order within §6.5:**
1. BatchedMesh grouping by material (biggest draw call reduction)
2. three-mesh-bvh for picking (sub-ms raycasting)
3. InstancedMesh for repeated elements (secondary optimization)

#### §6.6 Positional Audio (immersive mode) — VIABLE ✓

`THREE.PositionalAudio` uses HRTF by default (`panner.panningModel = 'HRTF'`). Web Audio API universally supported.

**Critical gotcha: autoplay policy.** All browsers require user gesture before AudioContext plays.
- Context starts `'suspended'` — must call `context.resume()` inside click/tap handler
- Mobile Safari strictest
- Pattern: check `audioContext.state === 'suspended'`, resume on first user gesture
- Natural fit: user taps ▶ play on hourglass = gesture that resumes audio

HRTF is more CPU-expensive than equalpower — fine for a handful of sources, avoid 50+ simultaneous.

#### §6.7 GPU Particle Effects — VIABLE (two-tier) ✓

**Tier 1 (WebGL, now):** `THREE.Points` + `BufferGeometry`
- CPU-updated: ~100K-500K particles before frame drops
- Shader-animated (velocity as attribute, animate in vertex shader): millions, no CPU upload
- Good enough for construction dust/sparks at small scale

**Tier 2 (WebGPU, after §6.1):** TSL compute shaders
- Official example `webgpu_tsl_compute_attractors_particles`: 262K particles GPU-computed
- Real-world: 1M particles at 4K demonstrated (Expo 2025 Osaka)
- ~150x faster than CPU particle update
- No separate library needed — built into Three.js via TSL

#### §6.8 Ray-Blast DLOD (Dynamic Level of Detail) — PRACTICAL, highest perf ROI ✓

**The problem:** LTU_AHouse has 125,698 elements. At 1 mesh per element (desktop), that's 125K draw calls and a 125K-node `scene.traverse` per frame. Fly mode (`flyTick`) orbits the camera — the camera logic costs nothing, but rendering 125K meshes drops to ~10fps.

**Why storey-based DLOD alone is insufficient:** Looking head-on at one storey of LTU, that storey has ~5,000 elements stretching 100m deep. The front facade wall occludes ~80% of them. Storey distance doesn't know what's behind a wall. You'd still render 5,000 meshes when only ~300 are visible.

**Solution: ray-blast occlusion culling.** Cast a grid of rays from the camera into the scene. Elements that rays hit are VISIBLE (NEAR tier). Everything else demotes to cheap representations.

**The existing infrastructure:**

| Layer | What exists today | Draw calls (125K scene) | Picking |
|-------|-------------------|------------------------|---------|
| Individual mesh (desktop) | `streaming.js` default | ~125,000 | Direct guid |
| InstancedMesh | 2+ identical geometries | ~200-500 | `_instanceMeta[meshId][instanceId]` |
| Merged mesh (mobile only) | `storey\|disc\|rgba` buckets | ~200 | DB nearest-neighbor query |

**Three LOD tiers — driven by ray visibility, not storey distance:**

| Tier | Criteria | Representation | Picking |
|------|----------|---------------|---------|
| **NEAR** | Ray-hit + 1-ring spatial neighbours | Individual mesh (full detail) | Direct guid |
| **MID** | Same storey as NEAR but not ray-hit | InstancedMesh (same geometry, instanced) | `_instanceMeta` |
| **FAR** | All other storeys | Merged mesh (storey\|disc\|rgba blob) | DB fallback |

**Ray-blast algorithm (runs every N frames, throttled):**

```javascript
// §6.8 — Ray-blast DLOD evaluator
// Runs every 6 frames (~100ms at 60fps) or on controls.change
var DLOD_RAYS_X = 20, DLOD_RAYS_Y = 20; // 400 rays = sub-ms with BVH
var _nearSet = {};  // guid → true (ray-hit + neighbours)

function rayBlastDLOD() {
  var raycaster = new THREE.Raycaster();
  raycaster.firstHitOnly = true;  // BVH early termination — crucial
  var hitGuids = {};
  var cam = APP.camera;

  // Cast grid of rays through viewport
  for (var rx = 0; rx < DLOD_RAYS_X; rx++) {
    for (var ry = 0; ry < DLOD_RAYS_Y; ry++) {
      var nx = (rx / (DLOD_RAYS_X - 1)) * 2 - 1;  // -1 to +1
      var ny = (ry / (DLOD_RAYS_Y - 1)) * 2 - 1;
      raycaster.setFromCamera({ x: nx, y: ny }, cam);
      var hits = raycaster.intersectObjects(APP.scene.children, true);
      if (hits.length && hits[0].object.userData.guid) {
        hitGuids[hits[0].object.userData.guid] = true;
      }
    }
  }

  // Expand hit set with spatial neighbours (within 5m radius)
  // Uses element_transforms bbox proximity from DB — one query
  _nearSet = expandNeighbours(hitGuids, 5.0);

  // Promote/demote meshes
  APP.scene.traverse(function(obj) {
    if (!obj.isMesh || !obj.userData.guid) return;
    var isNear = _nearSet[obj.userData.guid];
    // NEAR: full individual mesh, visible, shadow-capable
    // MID/FAR: hide individual mesh (InstancedMesh/merged blob covers it)
    obj.visible = !!isNear;
  });
}
```

**Neighbour expansion — why 1-ring matters:**
Rays hit surfaces facing the camera. But an element 1m behind a hit wall (e.g. a pipe inside a wall cavity, furniture against a wall) is contextually relevant — the user expects to see it if they click nearby. The 1-ring expansion uses the DB `element_transforms` bbox to find elements within 5m of any ray-hit element. One SQL query:
```sql
SELECT a.guid FROM element_transforms a
WHERE EXISTS (
  SELECT 1 FROM element_transforms b
  WHERE b.guid IN (?) -- hit guids
  AND ABS(a.center_x - b.center_x) < 5
  AND ABS(a.center_y - b.center_y) < 5
  AND ABS(a.center_z - b.center_z) < 5
)
```

**For LTU_AHouse head-on at Level 5 (the hard case):**
- 400 rays cast → ~250 unique element hits (front facade, visible furniture, columns)
- 1-ring expansion → ~500 NEAR meshes (includes elements just behind hit surfaces)
- Same storey not hit → ~4,500 elements → MID (one InstancedMesh group, ~50 draw calls)
- Other 24 storeys → ~120K elements → FAR (~100 merged blobs)
- **Total draw calls: ~650** (vs 125,000)
- **scene.traverse: ~650 visible nodes** (vs 125,000)

**Performance budget:**

| Component | Cost | When |
|-----------|------|------|
| 400 raycasts with BVH (`firstHitOnly`) | ~0.5ms | Every 6 frames |
| Neighbour SQL query | ~2ms | Every 6 frames |
| Promote/demote traverse | ~1ms | Every 6 frames |
| Normal frame render (650 draw calls) | ~3ms | Every frame |
| **Total per frame** | **~3.5ms average** | **60fps** |

**Prerequisites:**
- **§6.5 BVH (three-mesh-bvh)** — without BVH, 400 raycasts against 125K meshes = 2000ms. With BVH = 0.5ms. BVH is mandatory.
- Storey grouping metadata (already exists in `elements_meta.storey`)
- Merged mesh path (already exists in `streaming.js` S232)

**Implementation phases:**

1. **Phase A: BVH setup** — install three-mesh-bvh, compute bounds trees on streamed geometries. 3-line monkey-patch. Test: raycaster pick at 125K must be <1ms.

2. **Phase B: Ray-blast evaluator** — 20×20 grid, throttled to every 6 frames. Outputs `_nearSet` guid map. Test: §-tag logs show ~300-500 NEAR elements for head-on LTU view.

3. **Phase C: Tier management** — `promoteStorey(storeyIdx, newTier)` reuses existing merge/instance paths from `streaming.js`:
   - **NEAR→MID**: Dispose individual meshes, rebuild as InstancedMesh (`_flushInstanced` with `forceInstance=true`)
   - **MID→FAR**: Dispose InstancedMesh, rebuild as merged mesh (mobile merge path, `streaming.js` line 387-459)
   - **FAR→MID→NEAR**: Reverse — re-stream from `meshCache[hash]` (geometry cached, no DB re-fetch)
   - Transition budget: max 1 storey promotion per frame (spread cost over ~200ms)

4. **Phase D: Time machine integration** — frontier storey auto-promotes to NEAR. `renderAtTime` traverse only visits NEAR meshes (the ~500 visible ones, not 125K). Hourglass eye mode ray-blasts along the camera direction to keep the construction zone in NEAR.

**Why this works without new Three.js features:**
- `THREE.Raycaster` already exists + three-mesh-bvh accelerates it
- Merged mesh path already exists in `streaming.js` (S232)
- InstancedMesh path already exists in `streaming.js`
- `meshCache[hash]` persists geometry — re-streaming costs zero DB access
- Picking fallback (DB nearest-neighbor) already exists in `picking.js`
- The only new code: ray-blast evaluator + tier orchestrator

**Fly mode at LTU (125K):**
- Camera orbits → every 6 frames, 400 rays re-evaluate visibility
- Approaching a facade: elements "pop in" as rays start hitting them (~100ms ahead of camera)
- Orbiting around: rear elements demote as front elements promote — smooth ~650 draw calls throughout
- **Result: 60fps on mid-range GPU** (vs 10fps currently)

**Time machine at LTU:**
- Frontier storey = NEAR (full guid tracking for show/hide/highlight)
- Adjacent storeys = MID (visible backdrop, instanced)
- Distant = FAR (merged blob backdrop)
- `renderAtTime` traverse: ~500 NEAR meshes → ~0.2ms (vs 50ms for 125K)

**§-tags:**
- `§DLOD_RAYBLAST rays=400 hits=N near=N mid=N far=N ms=T` — every evaluation
- `§DLOD_PROMOTE storey=N from=FAR to=NEAR meshes=M` — tier transitions
- `§DLOD_STATE near=N mid=M far=F drawCalls=D traverse_ms=T` — frame stats

**Test targets:**
- LTU_AHouse (125K elements, 25 storeys) — the proving ground
- Terminal (48K elements, 9 storeys) — must not regress
- SampleHouse (58 elements) — trivial, all NEAR always
- Fly mode: full 360° orbit at LTU must stay >45fps
- Head-on view: NEAR set must be <1000 elements (not full storey)
- Time machine: frontier storey NEAR, traverse <2ms
- BVH: 400 raycasts < 1ms

**Key files to modify:**
- `deploy/dev/streaming.js` — extract merge/instance logic into `buildStoreyTier(storey, tier)`, add BVH computation on flush
- `deploy/dev/scene.js` or new `dlod.js` — ray-blast evaluator, tier orchestrator
- `deploy/dev/tour.js` — trigger DLOD evaluation from `flyTick()` and `walkTick()`
- `deploy/dev/time_machine.js` — auto-promote frontier storeys to NEAR
- `deploy/dev/picking.js` — already handles all three tiers (no changes needed)

**Recommended implementation priority (updated):**

| Priority | Item | Why | Depends on |
|----------|------|-----|------------|
| **P1a** | §6.5 BVH (three-mesh-bvh) | Prerequisite for ray-blast. 3-line patch. | Nothing |
| **P1b** | §6.8 Ray-Blast DLOD | Biggest perf ROI, 125K→650 draw calls | §6.5 BVH |
| **P2** | §6.2 Sky + PBR env | Highest visual ROI, zero download, pairs with ☀ | Nothing |
| **P3** | §6.5 BatchedMesh | Further perf for NEAR tier (replaces individual meshes) | §6.8 |
| **P4** | §6.4 AO (HBAOPass) | Depth/realism after sky | §6.8 (NEAR-only pre-pass) |
| **P5** | §6.4 Bloom | Polish, orange glow on active elements | §6.4 AO (same pipeline) |
| **P6** | §6.6 Audio | Immersive mode enhancement | Nothing |
| **P7** | §6.7 Tier 1 particles | CPU particles for construction effects | Nothing |
| **P8** | §6.1 WebGPU migration | Gate to Tier 2 particles + RenderPipeline | All GLSL audit |
| **P9** | §6.7 Tier 2 particles | GPU 100K+ particles | §6.1 |

**Implementation rule**: Each §6.x is a separate session/PR. Test against LTU_AHouse (125K elements) AND Terminal (48K elements) for performance. Never regress the existing hourglass playback or drawer functionality. Each upgrade should be behind a feature toggle (`?dlod=on`, `?fx=sky`, `?fx=ao`, `?fx=bloom`, `?fx=audio`) until proven stable.
