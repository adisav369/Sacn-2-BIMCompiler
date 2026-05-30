# ⚠ DO NOT REMOVE — Read the log after every run

## Time Machine Mobile Fix — Dedicated Session

### Problem
The `?tm=play` shared link does NOT work correctly on mobile (or fresh buildings on desktop):

1. **Building shows fully intact** — `<<` (jump to start) does not clear to empty site
2. **Streaming conflict** — Time machine `activate()` fires while streaming is still in progress (e.g., "Rendered 2000/16071"). BBOX placeholders remain visible. Elements arriving via streaming appear with default `visible=true`, bypassing time machine's visibility control.
3. **Mobile render stall** — `requestAnimationFrame` is throttled on mobile until user touches screen. A forced `renderer.render()` was added but doesn't solve it because the root issue is (2): elements stream in AFTER time machine has set visibility.

### Root Cause
`time_machine.js` `init()` uses `setInterval` waiting for `app.scene.children.length > 2` to trigger `activate()`. But:
- Streaming hasn't finished — only a few meshes exist
- `injectGantt()` reads ALL elements from `elements_meta` (correct) and writes kernel_ops for all of them
- `renderAtTime(_projectStart)` traverses the scene — but most meshes don't exist yet
- Streaming continues, adding meshes with `visible=true` (their default)
- Result: newly streamed meshes appear visible regardless of time machine cursor

### What Must Happen
1. **Wait for streaming to complete** before activating time machine
   - Check: `APP.streamedCount >= APP.totalElements` or `APP._streamingDone === true`
   - Look in `streaming.js` for the completion signal (search `§FLUSH`, `streamedCount`, `_streamingDone`)
2. **OR hook into streaming** so each newly-created mesh respects the current time machine state
   - After streaming adds a mesh, check if time machine is active → set `visible` based on cursor
3. **BBOX placeholders** (`§BBOX_PLACEHOLDERS`) must also be hidden when time machine is active
   - Search for `BBOX` creation in `streaming.js` — they have `userData.isBbox = true`

### Key Files
- `deploy/dev/time_machine.js` — lines 870-887: `?tm=play` handler with `setInterval` wait
- `deploy/dev/streaming.js` — streaming pipeline, BBOX creation, completion signal
- `deploy/dev/main.js` — `animate()` loop, `markDirty()`, `streamTick()`

### Architecture Context
- `renderAtTime(cursor)` traverses `app.scene` and sets `obj.visible` based on kernel_ops timestamps
- Single meshes: `obj.userData.guid` → check placed/frontier/future
- InstancedMesh: `app._instanceMeta[obj.id]` → per-instance zero-scale matrix for hidden instances
- `saveVisibility()` / `restoreVisibility()` saves/restores all mesh + InstancedMesh state on open/close
- Forced `renderer.render()` added after each `renderAtTime()` for mobile — but doesn't help if meshes don't exist yet

### Acceptance Criteria
- `?tm=play` on mobile: building starts from EMPTY site, constructs piece by piece
- `?tm=play` on desktop fresh building: same — empty to built
- `<<` always returns to truly empty (zero meshes visible, zero BBOX visible)
- Closing time machine (✕) restores scene to pre-activation state
- Normal viewer use (no `?tm`) is unaffected
- Streaming must complete before time machine takes control

### Testing
- Test on Duplex (215 elements, fast stream)
- Test on SampleCastle (fresh, no cached DB)
- Test on Terminal (48K elements, slow stream)
- Mobile: test shared link on phone browser
- Verify: `§TIME_MACHINE_GANTT injected=X dbElements=X sceneMeshGUIDs=X — ALL DB elements scheduled`
- Verify: `<<` shows 0 placed, 0 active in status bar
