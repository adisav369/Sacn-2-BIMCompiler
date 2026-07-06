# ⚠ DO NOT REMOVE — Read the log after every run

## S260e: Time Machine — Cinematic Fixes + Yellow Bbox + Forward Strategy

### Context
S260d session was long. Delivered PBR materials, progressive storyboard, opening shot, lazy
angles, particle/audio/emissive removal, §WB_MAT logging, WYSIWYG pick fix, whitebox regression
suite (sections 10-12).

**What BROKE during S260d and was reverted:**
- Predetermined camera arc system — replaced competing lerp/orbit with single arc interpolation.
  Looked clean in theory but produced: camera too close (inside geometry), no yellow bbox visible,
  jump cuts between scenes, loss of the natural "orbit while converging" feel.
- **REVERTED** to S260c working camera: target convergence (0.12 lerp) + distance spring +
  slow orbit (0.006 rad/tick). This works. Don't replace it again without testing on 3+ buildings.

**What WORKS from S260d (keep):**
- PBR materials (MeshStandardMaterial + roughness/metalness per IFC class)
- Lighting fix (neutral white ambient 0.35, warm hemisphere 0xb0c4de/0x8b7355/0.6, sun 1.4)
- Class-based color fallback for no-color buildings (LTU etc)
- Progressive storyboard (first 500 ops instant, rest in background rAF chunks)
- Lazy angle (random on storyboard build, raycast on scene arrival)
- Opening establishing shot (20 ticks = 1.6s, full building orbit, then transit to first scene)
- All particles/audio/emissive effects removed (caused white squares)
- InstancedMesh highlight removed (was white flash root cause)
- Frontier elements: just `applyOutline` — no emissive, no highlight
- DoubleSide on all materials (IFC normals inconsistent)
- §WB_MAT material diagnostic logging
- WYSIWYG pick (firstHitOnly=false, skip transparent/outline/bbox hits)
- BatchedMesh pick uses DB per-element bbox (not entire batch geometry bbox)
- Whitebox regression tests sections 10-12
- `_useDlodPath = false` (DLOD disabled until S261 promotion done)
- Storyboard cache invalidation via `_arcV:2` marker

**Current sw version:** v362 (other session may have bumped)
**Current file versions:** scene?v=21, streaming?v=33, tm?v=19, picking?v=21

### KNOWN ISSUES — Fix in this session

#### 1. Yellow bbox highlight not visible on click
**Symptom:** `§PICK` fires, guid resolves, `§PICK_BBOX` log shows position/size, but no yellow
wireframe box visible on screen.
**Possible causes:**
- Position at (0,0,0) instead of element position — check `§PICK_BBOX pos=` values
- Size too small (check `size=` values — should be > 0.3m for any real element)
- The `linewidth: 2` may not render on WebGL2 (linewidth > 1 is often ignored)
- ACESFilmic tone mapping may desaturate yellow — try `color: 0xffff00` without tone mapping

**Debug approach:** Click an element, read `§PICK_BBOX` log. Compare pos with actual element
position. If pos is correct but box invisible → try `linewidth: 1` or thicker box edges using
`BoxHelper` instead of `EdgesGeometry + LineSegments`.

**Alternative:** Replace `LineSegments` with a semi-transparent yellow `BoxHelper` or a thin
`MeshBasicMaterial` box with `opacity: 0.2, wireframe: false` — this renders reliably on all GPUs.

#### 2. `⚠EMISSIVE` false positive in §WB_MAT
MeshStandardMaterial default `emissiveIntensity=1.0` with emissive color `(0,0,0)` triggers the
flag. Fix: check `em.r + em.g + em.b > 0` before flagging, not just `emissiveIntensity > 0.3`.

#### 3. `§DASH_OPEN` spam
Fires every tick during playback when dashboard is visible. Throttle to every 20 ticks.

#### 4. `IfcPipeFitting/IfcPipeSegment/IfcDuctFitting rgb=0.70,0.70,0.70`
CLASS_COLOR_FALLBACK doesn't cover these pipe/duct fittings. They remain default grey.
Add entries: `IfcPipeFitting: '0.50,0.55,0.60'`, `IfcPipeSegment: '0.50,0.55,0.60'`,
`IfcDuctFitting: '0.60,0.62,0.58'`, `IfcDuctSegment: '0.60,0.62,0.58'`.

#### 5. Camera distance — still may be too close on some buildings
`_FLYTHROUGH_DIST=12`, `_HERO_DIST=8`. If camera goes inside geometry, increase.
Add min-distance guard: `camDist = Math.max(camDist, desiredDist * 0.5)`.

### FORWARD STRATEGY — Cinematic improvement without breaking what works

**Rule: Do NOT replace the S260c camera model.** It works. Improve incrementally.

#### Phase 1: Polish what works (this session)
1. Fix yellow bbox (try BoxHelper or MeshBasicMaterial wireframe box)
2. Fix §WB_MAT false positive
3. Throttle §DASH_OPEN
4. Add missing pipe/duct color fallbacks
5. Add min-distance guard on camera

#### Phase 2: Signature Scene scorer (next session)
After storyboard built, score all scenes. Pick ONE best per building.
Score by: chain linearity, IFC class appeal, count sweet spot (12-25), class purity.
Cache winner in IDB. Log: `§SIGNATURE_SCENE idx=N score=X cls=Y count=Z`.
The existing camera model (orbit + converge) plays this scene — no new camera system needed.
Just slow time to 30s/tick for the signature scene beat.

#### Phase 3: Incremental camera polish (future)
- Flythrough chain tracking: re-add ONLY for signature scene (not all scenes)
- The chain tracking from S260c (leadPt interpolation along spatial chain) was good —
  it just needs to be opt-in for high-quality scenes, not default for all 700 scenes
- Smooth scene-to-scene transition: ensure orbit angle carries over (already works with _camAngle)

### PRIORITY 6: Underground piling must build FIRST

The Gantt scheduler sorts by storey band (bottom-up by median Z) → sequence → center_z.
Clinic: TOF Footing (medianZ=-0.35, band 0, seq 1) should be FIRST ops. Terminal has no piling.

**Verify with §GANTT_OPS_FIRST20 log:**
- First entries MUST show lowest band (underground/footing storey)
- Hospital confirmed: `Level 1|band=0|seq=1|IfcFooting` ✓

### PRIORITY 7: HUD credentials on meta.db load + Building label fix

**A. Early HUD display:** When `meta.db` loads (BEFORE geo.db / geometry streaming), the HUD
should immediately show:
- Building name, element count, discipline breakdown with color chips, storey list
- Currently these populate only after streaming completes — move to fire after meta.db load

**B. Building label:** "Buildings" (plural, city mode) → singular "Building" + active name.

### Files to check
- `deploy/dev/time_machine.js` — TM, storyboard, camera, effects
- `deploy/dev/streaming.js` — `_useDlodPath = false`, `_batchMeta` populated, PBR materials
- `deploy/dev/scene.js` — PBR lighting
- `deploy/dev/picking.js` — yellow bbox, WYSIWYG pick

### DO NOT touch
- `deploy/dev/dlod.js` — DLOD session handles this
- `deploy/live/*` — production, never edit
- The S260c camera model (target convergence + distance spring + orbit) — it works, don't replace

### Whitebox
Run before any deploy:
```bash
bash deploy/dev/test-results/whitebox_js_logic.sh
bash deploy/dev/test-results/whitebox_s260c.sh deploy/buildings/Clinic_extracted.db
```
Sections 10-12 cover pick, PBR material, and TM material logging regression checks.

### Deploy checklist
- Bump `?v=` for changed files in index.html
- Bump `CACHE_VERSION` in sw.js AND `sw.js?v=` in index.html — MUST MATCH
- Upload ALL changed files to ootb-dev with `--content-type`
- Clear IDB cache to test fresh storyboard computation
