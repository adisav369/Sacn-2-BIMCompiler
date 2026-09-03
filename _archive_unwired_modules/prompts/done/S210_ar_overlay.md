# S210 — As-Built AR Overlay: Camera Feed + BIM Model

## Status: READY

## Goal
Hold phone up during walk mode → rear camera feed shows real building → BIM model overlays on top using GPS + compass + TrueNorth alignment. Architect sees where construction doesn't match design.

## What Already Works
- Device orientation controlling camera quaternion (S208)
- TrueNorth angle from DB (`project_metadata.true_north_angle`)
- GPS anchor point (walk.js walkAnchorGPS)
- Phone camera access (sitecam.js)
- Three.js scene with all elements rendered

## Spec

### Approach
- Use `getUserMedia({ video: { facingMode: 'environment' } })` for rear camera
- Render camera feed as background (CSS behind Three.js canvas)
- Make Three.js background transparent (`renderer.setClearColor(0x000000, 0)`)
- BIM elements render as semi-transparent wireframe overlay
- Device orientation aligns 3D view with real-world camera view

### Key Challenge
- Camera FOV must match Three.js FOV for alignment
- GPS accuracy (±3m) means rough alignment — good enough for "is the wall there?"
- No depth sensing — overlay is 2D projection, not true AR

### Files
- New `ar.js` — AR mode toggle, camera feed, transparency
- `walk.js` — AR mode flag, adjust rendering
- `scene.js` — transparent background when AR active
- `index2.html` — AR button in walk toolbar

## Anti-Drift
- Same orientation rules as S208 — no controls.update(), no extra listeners
