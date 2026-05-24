# Cinematic Rendering — Three.js r184 Post-Processing Pipeline

> **Spec for S277+: Production-quality rendering in browser BIM OOTB.**
> Built on Three.js r184 addons. All effects are post-process passes via EffectComposer — zero geometry changes, additive cost only.

## Current State (S277b DONE)

- **Sky shader** (Preetham atmospheric scattering) — sunrise/sunset/dusk/dawn/night
- **Stars + moon** at night during Time Machine
- **PCF shadows** following sun position
- **ACES tone mapping** + procedural env map from sky
- **PBR materials** with per-IFC-class roughness/metalness (42 types)
- **Cloud Layer** (S277d) — scrolling Perlin noise plane at Y=5000m, casts shadows via sun
- **Lensflare** (S277f) — sun disc + halo sprites, strongest at sunrise/sunset, auto-hide at night
- **Bloom** (S277b) — emissive boost on TM frontier meshes during night (construction floodlight glow)
- **Procedural normals** (S277) — onBeforeCompile noise perturbation: brushed metal on pipes, rough grain on concrete
- **TM lighting restore** (S277b fix) — restoreSky() now saves/restores full lighting state (sun/ambient/hemi/exposure)

## Planned Effects (priority order)

### 1. Bloom (Night Construction Glow)

**What**: Bright objects emit a soft glow halo. During TM night, construction frontier elements glow — simulating site flood lights on active work zones.

**Three.js addon**: `examples/jsm/postprocessing/UnrealBloomPass.js`

**Implementation**:
- EffectComposer pipeline: render → bloom → output
- Bloom only during TM night (first 2-3 nights for dramatic impression, then off)
- Threshold: 0.8 (only near-white materials glow — construction highlights, not everything)
- Strength: 0.6, radius: 0.4 (subtle, not neon)
- TM frontier elements get emissive boost (0.5) during bloom nights → they glow
- Toggle via TM internal state, not user-facing button

**Cost**: ~1ms/frame. Near-zero impact.

**Files**: `scene.js` (EffectComposer init), `time_machine.js` (bloom nights logic)

### 2. OutlineEffect (Pick + Clash Highlight)

**What**: GPU-rendered outline around selected/clashed elements — follows the actual mesh silhouette, not a bounding box.

**Three.js addon**: `examples/jsm/postprocessing/OutlinePass.js`

**Current method**: `EdgesGeometry` wireframe box (`LineSegments`) around element bbox. Works but shows a box, not the element's real shape. Clash pairs use material swap (red/orange on clipped mesh).

**Replacement**:
- OutlinePass renders selected objects with a 2-3px colored edge
- Pick: orange outline on actual mesh silhouette
- Clash pair: red outline on element A, orange on element B
- Find results: all matching elements get a subtle blue outline
- No more `_pickHighlight` box creation/disposal — just add/remove from OutlinePass.selectedObjects

**Why better than Bonsai**: Bonsai uses Blender's compositor outline (desktop GPU). We'd match the visual quality in browser at ~1ms/frame.

**Cost**: ~1-2ms/frame (depth-based edge detection).

**Files**: `scene.js` (OutlinePass init), `picking.js` (replace bbox with selectedObjects), `measure.js` (clash pair outlines)

### 3. SSAO (Ambient Occlusion)

**What**: Dark creases and contact shadows between adjacent surfaces. Makes rooms feel deep, MEP pipes cast soft shadows on walls, floors meet walls with visible darkening.

**Three.js addon**: `examples/jsm/postprocessing/SSAOPass.js` (or `N8AOPass` for better quality)

**Implementation**:
- Always on (subtle), or toggle via sunglasses panel
- Radius: 0.5m (architectural scale), samples: 16 (balance quality/perf)
- Intensity: 0.5 (subtle contact shadows, not heavy)
- Works with existing PBR materials — no changes needed

**Why it matters**: This is the single biggest visual upgrade. Every Enscape/Twinmotion render looks better than raw WebGL because of AO. Adding SSAO to our viewer closes 80% of the visual gap to desktop rendering apps.

**Cost**: ~2-3ms/frame. Toggle off on mobile.

**Files**: `scene.js` (SSAOPass in EffectComposer chain)

### 4. Cloud Layer (Scrolling Shadows)

**What**: A transparent plane high above the scene with scrolling Perlin noise texture. Clouds drift slowly, casting soft moving shadows on the ground.

**Implementation**:
- One `PlaneGeometry(50000, 50000)` at Y = 5000m
- Custom `ShaderMaterial` with 2-layer Perlin noise (fast + slow for parallax depth)
- UV offset scrolls with `deltaTime * 0.001` (gentle drift)
- Sun's shadow camera sees the cloud plane → cloud shadows fall naturally on ground + buildings
- Cloud opacity tied to turbidity (more clouds at dawn/dusk, fewer at noon)
- No Three.js addon needed — pure custom shader (~30 lines)

**Cost**: Near-zero (single textured quad). Shadow map already exists from sun.

**Files**: `scene.js` (cloud plane creation), `time_machine.js` (cloud opacity during day cycle)

### 5. Lensflare (Sun Glare)

**What**: Bright sun disc with radial glare during sunrise/sunset. Cinematic effect for drone shots and TM playback.

**Three.js addon**: `examples/jsm/objects/Lensflare.js`

**Implementation**:
- Attach to sun position (already tracked)
- Visible only when sun is within 30° of camera view direction
- 3 elements: main disc (white), inner ring (warm), outer halo (soft)
- Intensity scales with `dayFactor` — strongest at sunrise/sunset
- Auto-hide at night, during x-ray, during clash mode

**Cost**: Near-zero (billboard sprites).

**Files**: `scene.js` (Lensflare init), `time_machine.js` (intensity during sun cycle)

### 6. CSM (Cascaded Shadow Maps)

**What**: Multiple shadow quality zones — crisp 4K shadows on nearby geometry, softer 1K shadows far away. Replaces current single PCF shadow map.

**Three.js addon**: `examples/jsm/csm/CSM.js`

**Current**: One `DirectionalLight` with 4096×4096 shadow map covering entire building envelope. Large buildings (LTU 426m) spread 4K pixels across 426m = ~10cm/pixel. Close-up shadows are blurry.

**Replacement**:
- 3 cascade levels: near (50m, 4K), mid (200m, 2K), far (envelope, 1K)
- Near cascade gives ~1cm/pixel on close geometry — razor-sharp shadows
- Smooth blend between cascades (CSM handles this)

**Cost**: ~1ms/frame additional (3 shadow passes vs 1, but smaller maps per cascade).

**Files**: `scene.js` (CSM replaces DirectionalLight shadow), `tools.js` (toggleShadow update)

## EffectComposer Pipeline

```
Render scene → SSAOPass → OutlinePass → UnrealBloomPass → Output
                ↑              ↑              ↑
            always on     pick/clash      TM nights only
            (toggle)      (on demand)     (auto)
```

All passes share the same depth buffer. Total cost: ~5-7ms on desktop, ~3ms with SSAO off on mobile.

## Animation Export (Future)

**EffectComposer + CCapture.js** can record frames to WebM/MP4:
- Fly-through with all post-processing applied
- TM construction playback rendered to video
- Fixed frame rate (30fps) regardless of real-time performance
- User presses "Record" button → plays TM sequence → downloads video file

This is NOT real-time — it renders frame-by-frame to a buffer. A 60-second TM sequence at 30fps = 1800 frames, each rendered with full effects. On a mid-range GPU: ~10ms/frame × 1800 = ~18 seconds to render.

**Three.js addon**: No official. Use `canvas.toBlob()` per frame + `MediaRecorder` API (browser-native, no addon).

### 8. City Benchmark (1M Element Suburb Strip)

**What**: `city_bench.html` — 19 real buildings × 4 suburb strips = 1,196,164 IFC elements. Proves the architecture scales to 1M+ in a browser. Each building downloaded once, geometry cached, placed at 4 grid positions via InstancedMesh.

**Current state**: Deployed but not fully tested. Known issues:
- `faces` column name in component_geometries (fixed but needs verification on all 19 buildings)
- `normals` column may not exist on some DBs (probe added)
- Split-DB (meta+geo) vs single extracted — both paths implemented
- Large buildings (LTU 379MB, Hospital 229MB) may timeout on first download
- Memory pressure: 19 buildings × 4 copies → need to verify Chrome doesn't hit 4GB tab limit
- InstancedMesh reuse across strips — geometry shared, but each placement creates new InstancedMesh objects (draw call count may be higher than expected)

**Testing needed**:
1. Load individual buildings (small first: SampleHouse, Duplex, HospitalGarage)
2. Load a medium building (Clinic 16K, Terminal 48K)
3. Load a large building (Hospital 63K, LTU 122K)
4. Click "LOAD ALL" — measure peak memory, draw calls, FPS
5. Verify Sky shader + sun sliders + shadow work during city viewing
6. Verify click-on-bbox loads the correct building
7. Check if geometry cache (`cachedGeoData`) correctly shares across strips

**URL**: `https://red1oon.github.io/bim-ootb/viewer/city_bench.html`

**Files**: `deploy/dev/city_bench.html` (standalone, no viewer dependency)

**Success criteria**:
- All 19 unique buildings load without error
- 4 strips render (total ~1.2M elements)
- FPS >15 on desktop with all buildings loaded
- Memory <3.5GB (Chrome tab limit headroom)
- Console: `§CITY_BENCH` log for each building with draw calls + ms

## Implementation Order

| Phase | Effect | Files | Est. lines |
|-------|--------|-------|-----------|
| S277a | City benchmark 1M troubleshoot | city_bench.html | ~50 |
| S277b | EffectComposer + Bloom (TM nights) | scene.js, time_machine.js | ~60 |
| S277c | OutlinePass (pick + clash) | scene.js, picking.js, measure.js | ~80 |
| S277d | Cloud layer + shadows | scene.js, time_machine.js | ~50 |
| S277e | SSAO | scene.js | ~30 |
| S277f | Lensflare | scene.js, time_machine.js | ~30 |
| S277g | CSM | scene.js, tools.js | ~50 |
| S277h | Animation export | time_machine.js, new record.js | ~120 |

## Dependencies

All addons load from local `lib/` (same pattern as Sky.js):
- `lib/EffectComposer.js` — orchestrates passes
- `lib/RenderPass.js` — base scene render
- `lib/UnrealBloomPass.js` — bloom
- `lib/OutlinePass.js` — silhouette outlines
- `lib/SSAOPass.js` — ambient occlusion
- `lib/ShaderPass.js` — utility for custom passes
- `lib/Lensflare.js` — sun glare
- `lib/CSM.js` — cascaded shadows

All use `window.THREE` destructuring (same fix as Sky.js — avoid ES import from 'three' which breaks on WebGPU build).

## References

- [Three.js post-processing guide](https://threejs.org/docs/#manual/en/introduction/How-to-use-post-processing)
- [Three.js examples](https://threejs.org/examples/) — search "bloom", "outline", "ssao"
- [UnrealBloomPass source](https://github.com/mrdoob/three.js/blob/r184/examples/jsm/postprocessing/UnrealBloomPass.js)
- [Preetham sky model paper](https://www.researchgate.net/publication/220720443_A_Practical_Analytic_Model_for_Daylight)
