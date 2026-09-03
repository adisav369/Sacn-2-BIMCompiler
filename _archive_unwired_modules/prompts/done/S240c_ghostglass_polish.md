# ⚠ DO NOT REMOVE — Scope: S240c Ghost Glass accuracy + resource visualization. Read the log after every run.

# S240c — Ghost Glass Accuracy + Resource Visualization

## Resume Checklist

1. Read `prompts/S240_4d_viewer_sync.md` §7 — Phase 0-2 DONE, Phase 3-5 OPEN
2. Read `prompts/S240b_ghostglass_4d.md` — full ghostglass spec
3. Read `deploy/dev/ghostglass.js` — current implementation
4. Read `deploy/dev/boq_charts.html` — scrubber + Play/Pause (search `§4D_`)
5. Read `deploy/dev/rates.js` — SEQUENCE_RULES, LABOR_RATES, PHASE_COLORS
6. Load Terminal in ootb-dev: open viewer + charts in 2 tabs, drag scrubber

## What Exists (2026-05-07)

| Component | File | Status |
|-----------|------|--------|
| 8-check CTFL audit | boq_charts.html `audit4DSchedule()` | DONE |
| BroadcastChannel sync | main.js + boq_charts.html | DONE |
| Click Gantt bar → highlight | boq_charts.html | DONE |
| ghostglass.js | deploy/dev/ghostglass.js | DONE |
| Orange scrub line on Gantt | boq_charts.html | DONE |
| Play/Pause/Speed (1-8×) | boq_charts.html | DONE |
| Rotating colours per task | ghostglass.js `ACTIVE_COLORS` | DONE — **replace with phase colours** |
| depthTest:false shine-through | ghostglass.js `makeActive()` | DONE — **keep, shows active work behind built structure** |
| InstancedMesh support | ghostglass.js `buildGuidMap()` | DONE |
| 22-4d-audit.spec.js | deploy/dev/tests/specs/ | DONE (8 tests) |

## Principle: Accuracy Before Animation

The Gantt chart must account for **100% of building elements**. Every element appears
in exactly one task. The highlight colour tells you the **construction phase**, not a
random index. Only after coverage and colouring are proven do we add visual polish.

No gimmicks that mask data gaps. No 70% leftover hack. No shine-through that pretends
walls aren't there. The animation IS the schedule — if the schedule is wrong, the
animation is a lie.

---

## P1 — Full Element Coverage (100% or bust)

### BUG-1: Storey mismatch → 0 GUIDs

33K IfcPlate on "Unknown" storey in Terminal don't appear because the storey string
in the task doesn't match `elements_meta.storey` exactly.

**Current** (boq_charts.html ~line 803):
```sql
SELECT guid FROM elements_meta WHERE ifc_class = ? AND storey = ?
```

**Fix:** If the storey-filtered query returns 0 rows, fall back without storey:
```sql
SELECT guid FROM elements_meta WHERE ifc_class = ? AND storey = ?
-- if 0 results:
SELECT guid FROM elements_meta WHERE ifc_class = ? AND building = ?
```

Log: `§4D_GUID_FALLBACK class=IfcPlate storey=Unknown fallbackCount=33000`

### BUG-2: Duplicate GUIDs across tasks

Same GUID can appear in multiple tasks (e.g. IfcPlate on shared storeys matches
both Superstructure and Architecture). The `assigned{}` dedup in `seekTo()` handles
render state but the underlying data has duplicates.

**Fix:** Deduplicate at GUID resolution time (boq_charts.html), not at render time.
First task in phase order wins. Track assigned GUIDs across all tasks:
```js
const assignedGuids = new Set();
for (const t of scheduleData) {
  t.guids = t.guids.filter(g => !assignedGuids.has(g));
  t.guids.forEach(g => assignedGuids.add(g));
}
```

Log: `§4D_GUID_DEDUP before=N after=N stripped=N`

### BUG-3: Viewer not ready on Day 1

First `4D_PLAY` message may arrive before streaming finishes.

**Fix:** `ghostglass.js play()` checks `APP.streamedCount > 0`. If not ready,
retry via `requestAnimationFrame` up to 60 frames, then warn.

### Kill the 70% leftover hack

Current `ghostglass.js:210`:
```js
else if (!allTaskGuids[g] && n >= _tasks.length * 0.7) {
  makeBuilt(obj, 0x888888);  // ← arbitrary, hides coverage gap
}
```

**Replace with:** If any GUIDs remain unassigned after all tasks are resolved,
create a final synthetic task: `"Unassigned Elements"` at the end of the schedule.
This makes the gap visible in the Gantt (amber bar) instead of silently hiding it.

If coverage is truly 100%, this task has 0 GUIDs and doesn't render. That's the goal.

### Coverage witness

Add to boq_charts.html after GUID resolution:
```js
const totalElements = db.exec("SELECT COUNT(*) FROM elements_meta WHERE building = ?");
const assignedCount = assignedGuids.size;
const orphanCount = totalElements - assignedCount;
console.log('§4D_COVERAGE total=' + totalElements + ' assigned=' + assignedCount + ' orphan=' + orphanCount);
```

**PASS = orphan=0.** If orphan > 0, the schedule is incomplete.

---

## P2 — Phase-Accurate Highlighting

### Replace rotating colours with phase colours

Current `ACTIVE_COLORS = [0xff8c00, 0x44ff44, 0xff4444, 0xffff00, 0x00ccff]` cycles
per task index — meaningless. Two Superstructure tasks get different colours.

**Fix:** Active colour = `PHASE_HEX[task.phase]`. One phase, one colour, always.
The Gantt already uses `PHASE_COLORS` for bar colours — the 3D viewer must match.

In `ghostglass.js makeActive()`:
```js
function makeActive(mesh, phaseColor) {
  ensureOwnMaterial(mesh);
  var mat = mesh.material;
  mat.transparent = true;
  mat.opacity = 0.85;
  mat.color.setHex(phaseColor);         // ← phase colour, not rotating
  mat.depthTest = false;                 // ← shine through built structure
  mat.depthWrite = false;
  if (mat.emissive) { mat.emissive.setHex(phaseColor); mat.emissiveIntensity = 0.6; }
  mat.needsUpdate = true;
  mesh.renderOrder = 0;
}
```

### Keep depthTest:false on active elements

Shine-through shows where work is happening even behind completed structure.
When MEP Final is active, walls are already built — shine-through answers
"the electrician is behind that wall right now." That's what a PM needs to see.
Same technique as clash detection, different purpose: visibility of active work.

### Remove stagger ripple

The 2-5 second element-by-element ripple in `seekTo()` is decoration. A concrete pour
happens all at once, not one slab segment per 50ms. Remove the `setTimeout` stagger:
```js
// All active meshes snap immediately
for (var k = 0; k < activeMeshes.length; k++) {
  makeActive(activeMeshes[k], phaseColor);
}
```

The scrubber provides the animation. Elements don't need their own.

---

## P3 — Template Check Modal

Button next to Gantt title. Modal lists all IFC classes found in the building,
shows mapped phase/resource/productivity, highlights **unmapped in amber**.

This is the user's tool to fix coverage gaps — they see what's missing and can
add entries to `SEQUENCE_RULES` or `rates.js`.

Editable overrides stored in `localStorage` (persist across sessions, no server).

---

## P4 — Camera Fly-To (Storey Level)

On each `seekTo(N)`, compute centroid of active meshes and fly camera there.
Target the **storey centroid**, not individual element centroid — avoids jitter
when tasks have scattered elements.

Use existing `APP.flyTo(target, distance)` if available, or animate
`APP.controls.target` + `APP.camera.position` over 500ms with lerp.

Only activate when Play is running. Manual scrub = no fly (user controls camera).

---

## P5 — Resource Visualization (sprites + legend)

### Resource legend panel (charts page)

A panel in `boq_charts.html` showing resources deployed at the current scrub position.
Updates on every seek. Data already exists — filter tasks where
`startDay <= currentDay <= finishDay`, aggregate by resource:

```
┌─ Active Resources (Day 42) ───────────────────┐
│ 🏗 STEEL_ERECTOR   ×4 crew  + Mobile Crane     │
│ ▮  CONCRETE_GANG   ×6 crew  + Concrete Pump    │
│ ═  PLUMBER         ×2 crew                      │
│                                                 │
│ Total: 12 workers, 2 machines                   │
│ Cumulative: 840 / 3,200 labour-days (26%)       │
└─────────────────────────────────────────────────┘
```

Fields per row: trade icon, trade name, crew count, equipment (if allocated).
Footer: total workers, total machines, cumulative labour-days vs total (S-curve data).

### Construction sprites (3D viewer)

Stop-frame style. Sprite appears at active task centroid, stays static, disappears
when task moves on. No path animation, no particles. Reuse 9 canvas-drawn sprites.

**Sprites (THREE.Sprite with CanvasTexture — no external assets):**

| Resource | Icon | Colour | What it says |
|----------|------|--------|--------------|
| STEEL_ERECTOR | crane silhouette | #4472C4 | Steel going up |
| CONCRETE_GANG | mixer rectangle + figures | #A5A5A5 | Pouring concrete |
| MASON | brick pattern | #ED7D31 | Laying block |
| PLUMBER | pipe line | #70AD47 | Running pipes |
| HVAC_TECH | duct circle | #5B9BD5 | Installing ducts |
| ELECTRICIAN | lightning bolt | #FFC000 | Pulling cable |
| CARPENTER | frame grid | #C55A11 | Fitting doors/windows |
| ROOFER | roof triangle | #8E44AD | Roofing |
| FINISHER | panel square | #27AE60 | Finishing |

Canvas: 64×64, draw icon + tiny stick figures (count = crew size). Helmet shape
on each figure matches trade colour. Cheap, legible, reusable across tasks.

```js
// In ghostglass.js — on seekTo(), after applying states:
// 1. Remove previous sprites (scene.remove + dispose)
// 2. Compute centroid of active meshes
// 3. Look up task.resource → sprite canvas
// 4. Position at centroid, scale ~2m, billboard-facing
```

---

## Files to Edit

| File | Change | Priority |
|------|--------|----------|
| `deploy/dev/boq_charts.html` | GUID fallback query, dedup, coverage log, resource legend, Template Check modal | P1, P3, P5 |
| `deploy/dev/ghostglass.js` | BUG-3 fix, kill 70% hack, phase colours, remove depthTest:false, remove stagger, camera fly-to, sprites | P1, P2, P4, P5 |
| `deploy/dev/rates.js` | Only if new IFC classes found in test buildings | As needed |

## Testing

### §-log tags to verify (in priority order)

**P1 — Coverage:**
- `§4D_GUID_RESOLVE zeroGuid=0` — all tasks have GUIDs
- `§4D_GUID_FALLBACK` — should not appear if storey matching is clean
- `§4D_GUID_DEDUP stripped=0` — no duplicates (or low count)
- `§4D_COVERAGE orphan=0` — **every element in exactly one task**

**P2 — Highlighting:**
- `§4D_GLASS_SEEK active=N` — N > 0 for every task
- `§4D_GLASS_SEEK leftover=0` — no orphaned elements at end
- Visual: active colour matches Gantt bar colour for same task

**P5 — Resources:**
- `§4D_RESOURCE_LEGEND trades=N workers=N machines=N` — on every seek

### Test buildings (priority order)
1. **Terminal** (48K) — stress test, federated storeys, heavy MEP, BUG-1 repro
2. **HHS_Office** (7K) — clean building, good for visual verification
3. **SampleHouse** (65) — fast iteration, simple schedule
4. **Duplex** (1K) — multi-storey, mixed trades

### Deploy flow
Edit → syntax check → verify §-tags → upload to ootb-dev → smoke test URLs
```bash
# Upload
oci os object put --bucket-name bim-ootb-dev --file deploy/dev/ghostglass.js --name sandbox/ghostglass.js --content-type application/javascript --force
oci os object put --bucket-name bim-ootb-dev --file deploy/dev/boq_charts.html --name boq_charts.html --content-type text/html --force
# Bump ?v= in index.html if ghostglass.js changed, then upload index.html too
```
