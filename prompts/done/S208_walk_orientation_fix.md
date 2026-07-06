# S208 — Walk Mode Orientation Fix

## Status: DONE (2026-04-21)

## Goal
Walk mode: camera starts facing nearest ground-floor door, user turns phone to look around, step detection to walk forward.

## Root Cause
**`controls.update()` was overwriting `camera.quaternion` every frame.** The Three.js DeviceOrientationControls formula was always correct. OrbitControls and DeviceOrientation cannot coexist — `controls.update()` recomputes the quaternion from position→target, destroying whatever the device orientation listener set. This is a known problem solved by A-Frame, Panolens, and every production AR web framework since 2016.

### Why it was hard to find
- All 4 formula attempts (sin/cos, delta, custom quaternion, Three.js DOC) produced correct node tests
- Node tests can't reproduce `controls.update()` overwriting the quaternion
- The reversal looked like an alpha convention problem (CW vs CCW) but was actually a frame-ordering problem
- Two separate walk systems (`walkMode` for tour, `walkModeActive` for device orientation) with different variable names confused the guards

## Phone Diagnostic (Android Chrome, 2026-04-21)
```
STILL:  α=294.9°
RIGHT:  α=261.6° (Δ=-33.3)  ← alpha DECREASES on right turn
LEFT:   α=337.5° (Δ=+42.6)  ← alpha INCREASES on left turn
```
Confirms W3C convention on Android Chrome. Formula uses `e.alpha` directly — no `(360 - e.alpha)` inversion needed.

## The Fix (3 changes)

### 1. main.js — animate loop
```js
// BEFORE: controls.update() and flyTick/walkTick ran every frame regardless
if (!APP.walkModeActive) APP.controls.update();
APP.streamTick();
if (APP.walkMode) { APP.walkTick(); } else { APP.flyTick(); }

// AFTER: everything that calls controls.update() is blocked during walk
if (!APP.walkModeActive) {
  APP.controls.update();
  if (APP.walkMode) { APP.walkTick(); } else { APP.flyTick(); }
}
APP.streamTick();
// Device orientation LAST — nothing may overwrite the quaternion after this
if (APP.walkModeActive) APP.walkOrientTick();
APP.renderer.render(APP.scene, APP.camera);
```

### 2. walk.js — A-Frame pattern (cache event, compute in render loop)
```js
// Listener ONLY caches — no quaternion work here
A._walkOrientListener = function(e) {
  A._walkDeviceEvent = e;
};

// walkOrientTick() called from render loop LAST, right before renderer.render()
A.walkOrientTick = function() {
  if (!A.walkModeActive || !A._walkDeviceEvent) return;
  const e = A._walkDeviceEvent;
  // ... quaternion math here (same Three.js DOC formula) ...
};
```

### 3. walk.js — advanceWalkStep()
```js
// BEFORE: called controls.update() which overwrote device orientation
A.camera.position.add(dir);
A.controls.target.add(dir);
A.controls.update();  // ← THIS WAS THE BUG

// AFTER: only move position, device orientation drives quaternion
A.camera.position.add(dir);
// Do NOT call controls.update()
```

## Lessons Learned

### For future sessions
1. **OrbitControls and DeviceOrientation are mutually exclusive.** No amount of `enabled=false` helps if `controls.update()` is called from ANY code path during walk mode.
2. **Node tests cannot catch frame-ordering bugs.** The formula was always correct — the bug was in the render loop, which node tests don't reproduce.
3. **`controls.update()` overwrites quaternion even when `enabled=false`.** The `enabled` flag only controls input handling, not the position→target→quaternion computation.
4. **Search for existing solutions before debugging from first principles.** A-Frame solved this in 2016. The pattern: cache event in listener, apply quaternion LAST in render loop, block all OrbitControls during device orientation.
5. **Always put a visible version number in the UI.** Status bar text gets overwritten by streaming — use the page title or a panel header instead.
6. **Deploy what you test.** The modular sandbox files were being edited while the phone loaded the old monolith from a different OCI bucket. Wasted 4 sessions.

### DeviceOrientation facts (reference)
- W3C: `e.alpha` increases counter-clockwise from above. Turn right = alpha decreases.
- Android Chrome AND iOS Safari both follow W3C for `e.alpha`.
- iOS also provides `e.webkitCompassHeading` (increases clockwise = compass heading).
- Post-Chrome 50: alpha is RELATIVE (0 = page load direction) unless using `deviceorientationabsolute`.
- Three.js DeviceOrientationControls was removed in r134 — "reliable cross-device implementation not possible." A-Frame vendors a copy.
- The `_q1` quaternion (`-90° around X`) converts from phone-flat to phone-upright coordinate frame.
- `camera.rotation.reorder('YXZ')` must be set before device orientation work.

## Files
- `deploy/sandbox/walk.js` — walk mode (device orientation, step detection, wall X-ray)
- `deploy/sandbox/main.js` — render loop with walkOrientTick
- `deploy/sandbox/walk_math_test.js` — standalone formula test (12/12 pass)
- `deploy/sandbox/index2.html` — modular viewer (loads 14 JS files)
- `deploy/sandbox/landing2.html` — building picker landing page

## Architecture
- Monolithic `rtree_browser_demo.html` split into 15 modular files
- `walk.js`: `setupWalk(APP)` — adds walk functions to APP object
- `main.js`: `initViewer()` orchestrator, animation loop
- Live on OCI: `bim-ootb` (Duplex) and `bim-ootb-full` (25 buildings) buckets
- Landing page (`index.html`) → `sandbox/index2.html` → loads `walk.js` etc.
