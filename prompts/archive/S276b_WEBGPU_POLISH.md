# ⚠ DO NOT REMOVE — read the log after every run

# S276b — WebGPU Polish + X-ray Smoothness

## Context
S276 shipped: Three.js r160→r184, WebGPU native-only detection, WebGLRenderer fallback.
LTU 122K verified smooth on Chrome (WebGLRenderer r184, adapter=null on Intel iGPU).
See `docs/ROADMAP.md` §S276 DONE and `memory/project_s276_webgpu.md` for full state.

## Tasks (priority order)

### 1. ENV_MAP fix verification
- `scene.js` v=47 reloads standard THREE when adapter=null — fixes PMREMGenerator `hasInitialized` crash
- Verify: `§ENV_MAP vertex-color gradient sky` appears (not `§ENV_MAP_FAIL`)
- If still failing after cache purge: the `for (var _k of Object.keys(_std)) THREE[_k] = _std[_k]` line may not overwrite PMREMGenerator. Debug by logging `THREE.PMREMGenerator` source before/after overwrite.

### 2. Chrome NVIDIA dGPU test
- Enable `chrome://flags/#force-high-performance-gpu`, restart Chrome
- Load LTU 122K, look for:
  - `§S276_RENDERER WebGPURenderer native adapter=NVIDIA...`
  - `§S276_COMPILE_ASYNC done ms=???` — measure pipeline compilation time
- If adapter found: WebGPU path active, measure fps vs WebGL baseline
- If still adapter=null: check `chrome://gpu` for WebGPU status

### 3. X-ray smoothness
- Current: toggles `transparent`/`opacity`/`depthWrite` on 133 materials synchronously → stutter
- File: `deploy/dev/tools.js` — search for `§XRAY`
- Options:
  - A: `scene.overrideMaterial` with a single transparent material (one-liner, no iteration)
  - B: Pre-cache xray material variants at streaming time, swap pointers on toggle
  - C: Batch material changes across 2-3 frames with requestAnimationFrame
- Constraint: must preserve per-element IFC colors in xray mode (opacity < 1, not flat white)

### 4. "Multiple instances of Three.js" warning
- BVH CDN `https://cdn.jsdelivr.net/npm/three-mesh-bvh@0.8.0/+esm` imports its own `three`
- Fix: download BVH locally to `lib/`, update loader.js import path
- Or: suppress the warning (cosmetic, doesn't affect function)

### 5. PCFSoftShadowMap deprecated
- `tools.js` shadow setup uses `THREE.PCFSoftShadowMap` — removed in r184
- Replace with `THREE.PCFShadowMap`
- File: search for `PCFSoft` in tools.js

### 6. Mobile LTU freeze at bboxes
- LTU 122K on mobile: viewer freezes at bbox stage, operations continue in background
- Likely cause: 122K bbox InstancedMesh overwhelms mobile GPU, or streaming geo fetch blocks render
- Investigate: is render gate (`_isWebGPU && APP.streaming`) incorrectly activated on mobile? Mobile has no WebGPU adapter → _isWebGPU=false, so gate shouldn't fire
- Check: mobile DPR, antialias, render gate, memory pressure from 379MB geo.db
- May need: progressive bbox (show first 10K, add more as streaming progresses) or skip bbox on mobile for buildings >50K

## Testing
- Whitebox: `node deploy/dev/tests/whitebox_regression.js` — must pass 36+/39
- Browser: load LTU 122K, toggle xray (Alt+Z), night mode (n), shadows (h)
- Log lines to verify: `§S276_RENDERER`, `§ENV_MAP`, `§XRAY ON/OFF`, `§SHADOW`

## Files
- `deploy/dev/scene.js` — renderer init, env map, lighting
- `deploy/dev/tools.js` — xray toggle, shadow setup
- `deploy/dev/loader.js` — THREE loading, BVH import
- `deploy/dev/main.js` — render loop, compileAsync gate
- `docs/ROADMAP.md` §S276 — status doc
