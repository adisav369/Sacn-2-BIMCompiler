# ⚠ DO NOT REMOVE — Read the log after every run

## S260d: Cinematic Drone Polish + Gantt Accuracy

### Context
S260c delivered the cinematic camera system, two-pass Gantt scheduling, IDB JSON cache,
PBR-lite materials (ACES tone mapping + env map), construction effects (outline, dust,
sparks, Web Audio sounds), and ground Y improvements. Deployed to ootb-dev sw v359.

### OPEN: White boxes still flashing during playback
- Recent-element highlight was removed (commit 6d59f4c5) but user still reports white boxes.
- Need to trace: which code path creates bright white/light elements during TM playback.
- Check: hero beat paint effect (high emissive), BatchedMesh visibility toggling, bbox placeholders.
- Whitebox: add §-log to every `applyHighlight`/`applyOutline`/`applyFlash` call with the
  element's GUID, color, opacity. Read log to find which elements flash white.

### OPEN: Movie script one-time processing may hang
- `computeStoryboard()` runs synchronously: 48K ops × `pickClearAngle()` raycasts per scene.
- On Terminal (48K elements, 507-823 scenes), each scene raycasts 8 angles against ALL visible
  meshes — this is O(scenes × 8 × meshCount). Can block the main thread for 5-15 seconds.
- User sees "Drone Movie In Progress.." then UI freezes until computation finishes.
- Fix options:
  1. **Chunked async**: process N scenes per `requestAnimationFrame`, yield between chunks.
     Show progress: "Processing scene 50/507..."
  2. **Skip raycasting on first pass**: use random angles, refine with raycasts only for
     the current scene when camera arrives (lazy angle computation).
  3. **Web Worker**: move `computeStoryboard` + `buildGuidPosMap` to a Worker thread.
     Requires serializing the scene graph positions (guidPosMap) to the worker.
- Recommendation: Option 2 (lazy angles) — simplest, no worker complexity. Compute the
  storyboard clusters in <100ms (no raycasts), cache it. When camera arrives at each scene,
  do ONE 8-angle raycast for just that scene (amortised cost, never blocks).
- Also: if IDB cache hit (`§MOVIE_CACHE_HIT`), skip all computation — instant. The hang
  only happens on first visit per building.

### OPEN: Camera not zooming into construction close enough
- Storyboard scenes advance (§CINE_BEAT logs confirm) but camera stays distant.
- FLYTHROUGH_DIST=5m, PANORAMIC_DIST=25m — check if these are being applied.
- The lerp convergence might be too slow (0.18 factor) for the 2min/tick slowdown.
- Try: on scene arrival, SNAP camera position (not lerp) to the computed angle/distance.

### OPEN: Sound effects not audible
- Web Audio API AudioContext may still be suspended. Browser requires a direct user gesture
  (click/touch) on the PAGE before AudioContext can play. The TM panel buttons use pointerup
  which should count as a gesture — but verify `§AUDIO_INIT state=` log appears.
- If state=suspended persists, add a one-time `_audioCtx.resume()` in the Eye button handler.

### OPEN: Drone PNG icon may not show on some browsers
- File exists at `sandbox/icons/drone_32.png` (verified 200 OK, 1386 bytes).
- Button has `background:#fff`. Check if SW caches the old button HTML.
- Fallback: embed the icon as a data URI (base64 inline) to avoid network dependency.

### OPEN: Gantt scheduling — underground piling order
- Two-pass fixed MEP-before-columns on bands with no structural.
- But user says "piling still not doing first" — may need to verify with actual piling
  buildings (Clinic has IfcFooting on TOF Footing band, Terminal has zero piling).
- Check: does the camera START at the lowest Z (underground) and work UP?
- `§CINE_START_POS` log should show lowestY = deep negative value.

### OPEN: Dashboard logs spam
- §COST_DEBUG + §DASH_PHASE logs fire on EVERY tick during playback — floods console.
- Throttle: only log every 20 ticks (same pattern as §SHADOW_FRONTIER and §CINE_DIRECTOR).

### Enhancement: Gantt chart visual accuracy
- Currently groups by storey|phase — no individual element bars.
- Consider: when camera is close-up on a scene, show a zoomed-in Gantt of just THAT scene's
  elements (10-30 bars) instead of the full 48K-element overview.

### Enhancement: Movie export
- Future: export the playback as a video file (MediaRecorder API on canvas).
- Or: export the storyboard JSON for external video editing tools.

### Files Modified (S260c)
- `deploy/dev/scene.js` — ACES tone mapping, procedural env map (vertex colors)
- `deploy/dev/streaming.js` — per-class shininess/reflectivity, _calcGroundY call, BatchedMesh consolidation
- `deploy/dev/time_machine.js` — two-pass Gantt, IDB cache, cinematic v2, outline/dust/sound/slowdown
- `deploy/dev/tools.js` — ground Y: 32 storey names, lowest-of-top5 fallback
- `deploy/dev/panels.js` — Shift+click storey accumulate (stopImmediatePropagation)
- `deploy/dev/diff.js` — deep §-logging
- `deploy/dev/main.js` — diff fetch §-logging
- `deploy/dev/import_db_builder.js` — post-export validation, 15K threshold
- `deploy/dev/icons/drone_32.png` — drone button image
- `deploy/dev/test-results/whitebox_s260c.sh` — SQL data whitebox (6 buildings)
- `deploy/dev/test-results/whitebox_js_logic.sh` — JS code path whitebox

### Whitebox test suite
Run before any deploy:
```bash
# SQL data checks (ground Y, storey bands, diff, integrity)
bash deploy/dev/test-results/whitebox_s260c.sh deploy/buildings/Clinic_extracted.db

# JS code path traces (ground Y pipeline, icons, camera, storey shift, scheduling, cache, versions)
bash deploy/dev/test-results/whitebox_js_logic.sh
```

### Deploy checklist
- Bump `?v=` for ALL changed files in index.html
- Bump `CACHE_VERSION` in sw.js AND `sw.js?v=` in index.html — MUST MATCH
- Current: sw v359, scene?v=19, streaming?v=30, tm?v=18, panels?v=10, tools?v=18, diff?v=3, main?v=27
- Upload ALL changed files to ootb-dev
- Verify with curl
- Clear IDB cache to test fresh Gantt/movie computation
