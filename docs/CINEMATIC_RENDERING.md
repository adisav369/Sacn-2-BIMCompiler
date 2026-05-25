# Cinematic Rendering — Three.js r184 Post-Processing Pipeline

> **Spec for S277: Production-quality rendering in browser BIM OOTB.**
> Built on Three.js r184 addons. All effects are post-process passes via EffectComposer — zero geometry changes, additive cost only.

## Current State (S277d DONE)

### Rendering Foundation
- **WebGL-only** renderer (WebGPU deferred — unsafe usage warnings, canvas poisoning)
- **Sky shader** (Preetham atmospheric scattering) — sunrise/sunset/dusk/dawn/night
- **Stars + moon** at night during Time Machine
- **PCF shadows** following sun position, chunked traverse (no 122K freeze)
- **ACES tone mapping** + procedural env map from sky
- **PBR materials** with per-IFC-class roughness/metalness (42 types)
- **Procedural normals** — onBeforeCompile noise perturbation: brushed metal on pipes, rough grain on concrete
- **Distance fog** — FogExp2 auto-scaled by building envelope, color follows sky/night/TM
- **Near clip 0.1m** (was 0.5m) — zoom within 10cm of any surface
- **Lensflare** — sun disc + halo sprites, strongest at sunrise/sunset, auto-hide at night

### EffectComposer Pipeline (S277c)
- **EffectComposer** — RenderPass → SSAOPass → OutlinePass → OutputPass
- **SSAO** — contact shadows in room corners, pipe junctions. Toggled with H (Shadow). Radius 0.5m, samples 16
- **OutlinePass** — mesh silhouette on pick (orange), clash (white), find (blue). Replaces bbox wireframe
- All passes toggle-able. Composer only active when SSAO or Outline enabled — zero cost when off
- 12 addon files in `lib/` using `window.THREE` pattern (parallel `Promise.all` import)

### Isolation Pick (S277d)
- **Pick**: dim all other meshes to 15% opacity, picked element at 70% (see internal structure). Orange OutlinePass silhouette. Bonsai-style — no bbox wireframe
- **Find**: same isolation effect with blue outline, tighter fly-to zoom (1.5x bbox)
- **Clash**: red element A + blue element B, white outline on both
- **Deselect**: click anywhere (empty space OR dimmed mesh) restores all materials

### Time Machine Enhancements (S277b)
- **TM lighting restore** — restoreSky() saves/restores full state (sun/ambient/hemi/exposure)
- **Mobile TM sky** — _sunCycleActive set early so Shadow↔TM releases properly
- **Bloom** — emissive boost on all materials during TM night via matCache (~150 entries, no scene traverse)
- **20x twilight detail** — TICK_MS 5x slower + tickMs 4x finer at horizon crossing. Cubic ramp, zone 30° above to 20° below
- **Auto-speed** — base tick scales by building element count (3.5K→200ms, 48K→150ms, 122K→220ms)

### Night Mode (S277d)
- **12 PointLights** follow orbit target (not camera — stable on orbit, updates on pan)
  - Intensity 1.5, range 30m, decay 1.5. Cost: ~6ms/frame
- **Ambient moonlight** 0.2 — walls/floors visible everywhere, zero cost
- **Fixture emissive glow** — zero GPU cost, visible at any distance
  - Matches by `IfcLightFixture` class OR element name containing 'light','lamp','led','luminaire'
  - Fallback: ALL `IfcFlowTerminal` when no named lights in building (LTU generic IFC)
  - Uses matCache keys — catches ALL material surfaces per fixture (face, rim, edge)
- **Night fog** — dark blue fog color (0.03, 0.03, 0.09)

### Movie Maker (S277d)
- **Record button** (video camera icon) in pill bar — desktop only, hidden on mobile
- Click to start (red pulsing), click to stop → browser downloads `.webm`
- `canvas.captureStream(30fps)` + `MediaRecorder` (VP9→VP8→webm fallback)
- Records whatever camera sees: orbit, fly, walk, TM playback
- All effects baked in (SSAO, outline, fog, bloom, sky transitions)

### Keyboard (S277b)
- Shortcuts (h/n/x/etc) checked before panel typeahead — no longer swallowed by toolbar focus

## Planned (Future Sessions)

### CSM (Cascaded Shadow Maps)
3 cascade levels replacing single 4096 shadow map. Near ~1cm/pixel, far ~10cm/pixel. ~1ms/frame. Toggled with H.

### Animation Export
Frame-by-frame TM recording to MP4 at fixed 30fps with full post-processing. Each frame rendered with all effects. Not real-time capture.

### City Benchmark
19 buildings × 4 strips = 1.2M elements. `city_bench.html`. Deployed but untested at scale.

## Dependencies

Addons in `lib/` (window.THREE pattern, parallel import):
- `EffectComposer.js`, `RenderPass.js`, `ShaderPass.js`, `OutputPass.js`
- `SSAOPass.js`, `SSAOShader.js`, `SimplexNoise.js`
- `OutlinePass.js`, `CopyShader.js`, `OutputShader.js`
- `Pass.js`, `MaskPass.js`
- `Sky.js` (Preetham atmospheric scattering)

## PointsMaterial Status
Tested in r184 (`tests/test_points_r184.html`). **White square artifacts confirmed** — sizeAttenuation + AdditiveBlending still produce squares at distance. Sparks/particles remain disabled.
