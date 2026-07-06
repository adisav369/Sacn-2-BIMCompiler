# ⚠ DO NOT REMOVE — Read the log after every run

## S258: Three.js r128 → r156 + DLOD Fix

### Context
S254 session implemented BVH + DLOD + split DB loading. Results:
- **BVH v0.3.7 works** on r128: `bvh=86546` trees built, click-pick acceleration confirmed
- **Ray-blast DLOD crashed**: 400 rays × 86K meshes = 34M iterations → browser timeout. r128 has no scene-level BVH — each ray iterates all meshes.
- **Frustum+storey DLOD deployed** as fallback: hides behind-camera + distant storeys. But **meshes hide at wrong angle / delayed** — `matrixWorld` may not be current for meshes that haven't rendered yet, bounding spheres may be in local space without world transform.
- **Root cause of all three issues**: r128 is too old. BVH v0.3.7 is unmaintained. r156 unlocks BVH v0.7 (scene-level `raycastFirst`), BatchedMesh (86K→20 draw calls), and correct frustum culling.

### What's deployed (ootb-full bucket, v311)
- `loader.js` — BVH v0.3.7 dynamic import, self-test, debug logging
- `dlod.js` — frustum+storey DLOD (buggy angle, needs fix or replacement)
- `streaming.js` — split DB detection (fallback, no split DBs yet), DLOD enable/disable hooks
- `scene.js` — `computeBoundsTree()` on every geometry
- `picking.js` — `firstHitOnly` for click-pick
- `main.js` — `setupDLOD()` init, `dlodTick()` in animate loop
- `sw.js` v311, all `?v=` params current

### Scope — Three.js Upgrade

Read `prompts/S254_HOURGLASS_DRAWERS.md` §6.10 for the full upgrade plan. Summary:

1. **Update CDN URLs** in `loader.js` — r156 UMD build (global `THREE` preserved)
2. **Download UMD builds** to `lib/three.min.js` + `lib/OrbitControls.js`
3. **Upgrade three-mesh-bvh** to v0.7.8 (SAH heuristic, `raycastFirst` scene-level)
4. **Remove InstancedMesh raycast polyfill** (picking.js lines 8-38) — native in r156
5. **Audit `THREE.*` usage** across all 50+ JS files for deprecated APIs
6. **Fix DLOD**: with v0.7+ BVH, revert to ray-blast (400 rays on scene-level BVH = sub-ms)
7. **Test**: all 155 Playwright specs + LTU_AHouse 122K fly mode

### Key files
- `deploy/dev/loader.js` — CDN URLs, BVH import version
- `deploy/dev/lib/three.min.js` — local Three.js copy (download r156)
- `deploy/dev/lib/OrbitControls.js` — local copy (download r156)
- `deploy/dev/picking.js` — remove InstancedMesh polyfill (lines 8-38)
- `deploy/dev/dlod.js` — rewrite: ray-blast with scene BVH or fix frustum
- `deploy/dev/scene.js` — BVH build (may need API update for v0.7)
- `deploy/dev/streaming.js` — no changes expected
- All `deploy/dev/*.js` — audit for deprecated THREE APIs

### Deprecated API checklist (r128→r156)
- [ ] `THREE.Geometry` → `BufferGeometry` (already done)
- [ ] `THREE.Face3` → not used
- [ ] `THREE.Math` → `THREE.MathUtils`
- [ ] `THREE.VertexColors` → `material.vertexColors = true`
- [ ] `THREE.LinearEncoding` → `THREE.LinearSRGBColorSpace` (r152+)
- [ ] `THREE.sRGBEncoding` → `THREE.SRGBColorSpace` (r152+)
- [ ] InstancedMesh raycast polyfill → remove (native r132+)

### Version bump protocol (MANDATORY on every deploy)
1. Bump `?v=N` for every modified JS file in `index.html`
2. Bump `sw.js` CACHE_VERSION = 'vN+1'
3. Match `index.html` `sw.js?v=N+1`
4. Upload sw.js + index.html + all changed files
5. Run `node deploy/dev/tests/test_s253_gantt_sync.js` — T13 must pass

### §-tags to verify
- `§THREE_VERSION r=156` — confirm new Three.js
- `§BVH_INIT three-mesh-bvh v0.7.8` — confirm new BVH
- `§BVH_SELFTEST boundsTree=true` — BVH builds work
- `§DLOD_ENABLE count=122330` — DLOD activates
- `§DLOD_RAYBLAST rays=400 hits=N near=N ms=<2` — ray-blast sub-ms (if restored)
- `§DLOD_FRUSTUM vis=N hid=N ms=<3` — frustum culling (if kept)
- `§INSTANCED_RAYCAST native=true` — polyfill removed

### Test targets
- LTU_AHouse (122K elements, 25 storeys) — fly mode >45fps
- Terminal (48K elements) — must not regress
- SampleHouse (58 elements) — DLOD skips (too small)
- Click-pick at 122K: sub-ms (BVH acceleration)
- All 155 Playwright specs: `npx playwright test`

### Deploy target
- `bim-ootb-full` bucket first (staging)
- `bim-ootb-dev` after confirmed
- `bim-ootb-live` only after full Playwright pass
