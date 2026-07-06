# ⚠ DO NOT REMOVE — Scope: ghostglass.js — 4D construction animation via glass-to-solid transitions. Read the log after every run.

# S240b — Ghost Glass 4D Animation

## Concept

When the user presses Play on the Gantt chart (boq_charts.html), the BIM viewer
transforms the entire building into a transparent glass shell, then progressively
"fills in" each construction task — elements snap from glass to solid as their
Gantt bar activates. The effect: watching concrete pour into a glass mould.

**No new geometry.** The glass casing IS the existing building meshes at near-zero
opacity. The animation is pure material state manipulation.

---

## §1 Architecture

### New file: `deploy/dev/ghostglass.js`

Single module. Loaded by `index.html` with `<script>` tag before `main.js`.
Exposes `setupGhostGlass(APP)` called by `main.js`.

### Communication: BroadcastChannel (existing `bim_4d`)

Reuses the channel already wired in S240. New message types:

```js
// Gantt → Viewer
{ type: '4D_PLAY',  tasks: [...], speed: 1.0 }  // start animation
{ type: '4D_PAUSE' }                              // freeze at current task
{ type: '4D_RESUME', speed: 1.0 }                 // continue from pause
{ type: '4D_RESET' }                              // restore all materials (already exists)
{ type: '4D_SEEK',  taskIndex: N }                 // jump to specific task (drag-scrub)
```

The existing `4D_HIGHLIGHT` and `4D_HIGHLIGHT_ALL` messages continue to work
independently (click-to-highlight). Ghost glass is a separate mode triggered
only by `4D_PLAY`.

---

## §2 Material States

Three states per mesh during playback:

| State | Visual | Three.js properties |
|-------|--------|---------------------|
| GLASS | Transparent shell, faint blue tint | `transparent:true, opacity:0.06, color:#88aacc, depthWrite:false` |
| ACTIVE | Solid, emissive glow in phase colour | `transparent:false, opacity:1.0, emissive:PHASE_COLOR, emissiveIntensity:0.4` |
| BUILT | Solid, subtle phase tint | `transparent:false, opacity:0.85, emissive:PHASE_COLOR, emissiveIntensity:0.1` |

### Material cache (snapshot before first play)

```js
// On first 4D_PLAY, snapshot ALL mesh materials
APP._4dMaterialCache = new Map();  // mesh.id → { color, opacity, transparent, emissive, emissiveIntensity, depthWrite }
```

`4D_RESET` restores from this cache. Cache survives multiple play/reset cycles.
Only rebuilt on page reload.

---

## §3 Playback State Machine

```
             4D_PLAY
  IDLE ──────────────► PLAYING
   ▲                     │  │
   │    4D_RESET         │  │ 4D_PAUSE
   │◄────────────────────┘  │
   │                        ▼
   │    4D_RESET         PAUSED
   │◄────────────────────┘  │
   │                        │ 4D_RESUME
   │         ┌──────────────┘
   │         ▼
   └──── PLAYING ──► IDLE (when last task finishes)
```

### State: IDLE
- All meshes at original materials
- No timer running

### State: PLAYING
- Timer fires every `interval` ms (default 2000ms, adjusted by speed)
- Each tick: current task → ACTIVE, previous task → BUILT
- All other meshes → GLASS
- HUD shows: "▶ Task 5/46 — Superstructure: Columns (Level 2)"

### State: PAUSED
- Timer stopped
- Materials frozen at current state
- HUD shows: "⏸ Task 5/46 — Superstructure: Columns (Level 2)"

---

## §4 Glass Material Implementation

```js
function makeGlass(mesh) {
  // Clone material if shared (prevent cross-contamination)
  if (!mesh._4dOwnMaterial) {
    mesh.material = mesh.material.clone();
    mesh._4dOwnMaterial = true;
  }
  mesh.material.transparent = true;
  mesh.material.opacity = 0.06;
  mesh.material.color.setHex(0x88aacc);
  mesh.material.depthWrite = false;
  mesh.material.needsUpdate = true;
}

function makeActive(mesh, phaseColor) {
  mesh.material.transparent = false;
  mesh.material.opacity = 1.0;
  mesh.material.color.copy(mesh._4dOrigColor);  // restore original colour
  mesh.material.emissive.setHex(phaseColor);
  mesh.material.emissiveIntensity = 0.4;
  mesh.material.depthWrite = true;
  mesh.material.needsUpdate = true;
}

function makeBuilt(mesh, phaseColor) {
  mesh.material.transparent = false;
  mesh.material.opacity = 0.85;
  mesh.material.color.copy(mesh._4dOrigColor);
  mesh.material.emissive.setHex(phaseColor);
  mesh.material.emissiveIntensity = 0.1;
  mesh.material.depthWrite = true;
  mesh.material.needsUpdate = true;
}
```

---

## §5 Gantt-Side Controls (boq_charts.html)

Add playback toolbar above the Gantt chart:

```
[ ▶ Play ] [ ⏸ Pause ] [ ⏮ Reset ]   Speed: [1×] [2×] [4×]
```

### Play button logic:
1. Resolve GUIDs for ALL tasks (already done by S240 GUID resolution)
2. Broadcast `4D_PLAY { tasks: scheduleData, speed: currentSpeed }`
3. Start internal ticker — highlight active Gantt bar with pulsing border
4. On each tick: advance bar, broadcast `4D_SEEK { taskIndex: N }`

### Gantt bar visual feedback during playback:
- Past bars: dimmed (opacity 0.3) with ✓ overlay
- Active bar: pulsing cyan border (`box-shadow` animation)
- Future bars: normal

### Drag-scrub (Phase 2 — after Play works):
- Mouse drag on Gantt canvas maps X position to day number
- All tasks with startDay ≤ cursorDay → BUILT in viewer
- Next task after cursorDay → ACTIVE
- Rest → GLASS
- Broadcast `4D_SEEK { taskIndex: N }` on mousemove

---

## §6 Viewer-Side (ghostglass.js)

### setupGhostGlass(APP)

```js
function setupGhostGlass(APP) {
  var _state = 'IDLE';    // IDLE | PLAYING | PAUSED
  var _tasks = [];
  var _taskIndex = -1;
  var _timer = null;
  var _speed = 1.0;
  var _guidMeshMap = null;  // guid → [mesh, mesh, ...] (built once)

  // Build GUID → mesh lookup (once, on first play)
  function buildGuidMap() {
    if (_guidMeshMap) return;
    _guidMeshMap = {};
    APP.collectMeshes(function(o) { return o.isMesh; }).forEach(function(obj) {
      var g = APP.guidMap[obj.id] || obj.userData.guid;
      if (g) {
        if (!_guidMeshMap[g]) _guidMeshMap[g] = [];
        _guidMeshMap[g].push(obj);
      }
    });
    console.log('§4D_GLASS_MAP guids=' + Object.keys(_guidMeshMap).length);
  }

  // Snapshot materials (once per session)
  function snapshotMaterials() {
    if (APP._4dMaterialCache) return;
    APP._4dMaterialCache = new Map();
    APP.collectMeshes(function(o) { return o.isMesh; }).forEach(function(obj) {
      APP._4dMaterialCache.set(obj.id, {
        color: obj.material.color.getHex(),
        opacity: obj.material.opacity,
        transparent: obj.material.transparent,
        emissive: obj.material.emissive ? obj.material.emissive.getHex() : 0,
        emissiveIntensity: obj.material.emissiveIntensity || 0,
        depthWrite: obj.material.depthWrite
      });
      obj._4dOrigColor = obj.material.color.clone();
    });
  }

  // Apply state to a set of GUIDs
  function applyState(guids, stateFn) {
    for (var i = 0; i < guids.length; i++) {
      var meshes = _guidMeshMap[guids[i]];
      if (meshes) meshes.forEach(stateFn);
    }
  }

  // Seek to task N
  function seekTo(n) {
    if (n < 0 || n >= _tasks.length) return;
    _taskIndex = n;

    // All GUIDs not yet built → GLASS
    var builtGuids = new Set();
    var activeGuids = new Set();
    for (var i = 0; i <= n; i++) {
      var guids = _tasks[i].guids || [];
      if (i < n) guids.forEach(function(g) { builtGuids.add(g); });
      else guids.forEach(function(g) { activeGuids.add(g); });
    }

    APP.collectMeshes(function(o) { return o.isMesh; }).forEach(function(obj) {
      var g = APP.guidMap[obj.id] || obj.userData.guid;
      if (!g) return;
      var phaseColor = 0x888888;
      // Find task for this guid to get phase colour
      // (pre-computed in _guidPhaseColor map)

      if (activeGuids.has(g)) makeActive(obj, _guidPhaseColor[g] || 0x4472C4);
      else if (builtGuids.has(g)) makeBuilt(obj, _guidPhaseColor[g] || 0x4472C4);
      else makeGlass(obj);
    });

    console.log('§4D_GLASS_SEEK task=' + n + '/' + _tasks.length +
      ' active=' + activeGuids.size + ' built=' + builtGuids.size +
      ' name="' + _tasks[n].name + '"');
    APP.markDirty();
  }

  // BroadcastChannel handler (extends existing bim_4d listener in main.js)
  // main.js should delegate 4D_PLAY/PAUSE/RESUME/SEEK to APP._ghostGlass.*
  APP._ghostGlass = {
    play: function(tasks, speed) { ... },
    pause: function() { ... },
    resume: function(speed) { ... },
    seek: function(taskIndex) { ... },
    reset: function() { ... }
  };
}
```

---

## §7 §-Log Tags

| Tag | Module | What to check |
|-----|--------|---------------|
| `§4D_GLASS_MAP guids=N` | ghostglass.js | GUID→mesh map built, count reasonable |
| `§4D_GLASS_SNAPSHOT meshes=N` | ghostglass.js | Material cache size matches mesh count |
| `§4D_GLASS_PLAY tasks=N speed=X` | ghostglass.js | Playback started with correct task count |
| `§4D_GLASS_SEEK task=N/M active=A built=B` | ghostglass.js | State counts match task GUID counts |
| `§4D_GLASS_PAUSE task=N` | ghostglass.js | Timer stopped at correct task |
| `§4D_GLASS_RESET restored=N` | ghostglass.js | All materials restored from cache |
| `§4D_GLASS_TICK task=N elapsed=Xms` | ghostglass.js | Timer advancing at correct rate |

---

## §8 Files to Edit

| File | Change |
|------|--------|
| `deploy/dev/ghostglass.js` | **NEW** — full module |
| `deploy/dev/index.html` | Add `<script src="ghostglass.js?v=1">` before main.js |
| `deploy/dev/main.js` | Delegate 4D_PLAY/PAUSE/SEEK to `APP._ghostGlass` if loaded |
| `deploy/dev/boq_charts.html` | Play/Pause/Reset toolbar + ticker, broadcasts 4D_PLAY |

---

## §9 Implementation Order

1. **ghostglass.js** — material cache, glass/active/built functions, seekTo()
2. **main.js** — delegate new message types to APP._ghostGlass
3. **boq_charts.html** — Play/Pause/Reset buttons + internal ticker
4. **Test** — load Terminal (48K elements), play, verify §4D_GLASS_* logs
5. **Drag-scrub** — mousemove on Gantt maps to seekTo()

---

## §10 Terminal-Specific Notes

Terminal (T0_Terminal) is the reference building for this feature:
- 48,428 elements, 6 disciplines, 20+ storeys
- 33,324 IfcPlate in "Unknown" storey — steel cladding, appears as one mega-task
- Federated: Malay storeys (Aras Tanah/01/02/03) + English (GROUND/FIRST/SECOND)
- Heavy MEP: 4,243 pipe fittings, 3,821 pipe segments, 909 fire sprinklers
- The glass effect will be dramatic on this building — full terminal ghost,
  then foundations appear, steel rises, MEP threads through, cladding wraps

### Storey mapping for realistic schedule:
| DB Storey | Construction Order | Notes |
|-----------|-------------------|-------|
| 00 Aras Asas / -1 fundering | 1st (foundations) | Substructure |
| Aras Tanah / GROUND FLOOR LEVEL / Ground Lev | 2nd | Ground slab + columns |
| Aras Kedai | 3rd | Retail level |
| Aras 01 / Ceiling Level 01 | 4th | First floor |
| Aras 02 / Ceiling Level 02 / 02 FIRST FLOOR LEVEL | 5th | Second floor |
| Aras 03 / Ceiling Level 03 / 03 SECOND FLOOR LEVEL | 6th | Third floor |
| Aras 04 / Ceiling Level 04 / 04 THIRD FLOOR LEVEL | 7th | Fourth floor |
| 05 FOURTH FLOOR LEVEL (OBSERVATORY DECK) | 8th | Observatory |
| 06 ROOF LEVEL / 07 BEAM LEVEL / Aras Bumbung | 9th | Roof |
| Unknown | Parallel with Superstructure | Steel cladding runs concurrent |

---

## §11 Witnesses

- W-4D_GLASS_PLAY: Terminal plays from glass → solid, all 48K elements transition
- W-4D_GLASS_SPEED: 1×/2×/4× speed controls work, timer interval matches
- W-4D_GLASS_PAUSE: Pause freezes exact state, resume continues from same task
- W-4D_GLASS_SEEK: Drag-scrub maps X→day→taskIndex correctly
- W-4D_GLASS_RESET: All materials restored, no visual drift after 3 play/reset cycles
- W-4D_GLASS_PERF: Terminal (48K) seekTo() completes in <100ms

---

## §12 Out of Scope

- Video export (MediaRecorder)
- Camera fly-to per task (Phase 3 of S240)
- Bbox wireframes (replaced by glass casing)
- Template editor modal
- Multi-building sync
