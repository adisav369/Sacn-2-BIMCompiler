# BIM OOTB — Roadmap

## Principle
DB = model. Template = view. Browser = runtime. Three concerns, never merged.

**Live:** [red1oon.github.io/bim-ootb](https://red1oon.github.io/bim-ootb/)
**Repo:** [github.com/red1oon/bim-ootb](https://github.com/red1oon/bim-ootb)

## Shipped (S200–S271, April–May 2026)

Built in 33 days (April 20 – May 23). 552 commits. 92 JS modules. 30 test suites.

The BIM Intent Compiler project began October 2025 (concept) → January 2026 (Java compiler) → April 2026 (browser OOTB). The browser viewer outgrew the backend in one month.

### Core Viewer (S200–S209)
- Three.js r160 ESM, BatchedMesh, distance-based LOD
- Orbit, walk mode, cinematic drone, site camera
- Mobile-optimised: DPR=1, no antialias, frustum culling
- PWA offline support (service worker, IndexedDB cache)

### IFC Import/Export (S220–S229)
- Browser IFC import via web-ifc WASM (IFC2x3 + IFC4, 122K elements proven)
- Multi-format: OBJ, STL, DAE, GLB/GLTF, FBX, 3DS
- Guided classification wizard for non-IFC meshes
- IFC export — DB to .ifc download, STEP text builder

### Analysis (S210–S211, S245)
- BOQ charts, 17 country rate templates, forex
- NLP query DSL — keyword intent classification, no LLM
- Voice input via Web Speech API
- Clash detection — 12 discipline-pair rules, clash matrix, HTML report
- Variation Order Excel (FIDIC Clause 12)

### 4D/5D (S240, S253–S254)
- 4D time machine — construction sequence playback from BOM
- 5D cost estimation with Excel export
- Ghost glass visualisation
- Hourglass drawers — mini Gantt + dashboard

### Grid System (S248–S251, S268–S270)
- Grid overlay, drag, scissors, contours, dimension chains
- Door arcs, section cuts, elevations
- Grid kinematics engine — cascade model, ceiling grids
- Kernel ops — undo/redo for 2D operations

### ERP (S255–S259)
- iDempiere Application Dictionary: PostgreSQL → SQLite → browser
- AD renderer: tables, fields, tabs, charts
- FTS5 search across 23 AD tables
- Glass overlay, edge swipe, accordion drill

### Infrastructure (S225–S243, S260–S274)
- 18 locales, auto-detected from browser
- Split-DB streaming for large buildings (meta + geo)
- City mode — 786 buildings loaded simultaneously
- Share sheet — IFC/DB save, contribute, system share
- §S274 DLOD: r160 `perObjectFrustumCulled` for BatchedMesh (native, zero JS cost) + InstancedMesh zero-scale frustum culling (desktop). Mobile: render gate + DPR 0.75 orbit. See `docs/FeatureComparison.md` §Visibility Culling.

### Red Pill / New From Reference (S266–S270)
- Doc Canvas — design document with live BOM
- BOM extraction from IFC to BOM tables (100% JavaScript)
- Verb expansion — BOM Walker ported from Java to browser
- Grid kinematics — drag grid line → cascade recompile building
- UBBL validator spec (trigger semantics, O(K) invariants)

## Next

### S276 — Three.js r184 + WebGPU Upgrade (Priority 1)
Currently on r160 (Dec 2023). r184 (Apr 2025) is the latest stable — 24 releases ahead, WebGPU production-grade.

**Why r184, not r177:** r177 (May 2024) is already 12 months old. r160→r177 and r160→r184 cross the same breaking changes. r184 adds compatibility mode (WebGPURenderer with automatic WebGL2 fallback), 3x faster TSL compilation, truly non-blocking `compileAsync()`, and multiple BatchedMesh bug fixes (r182-r184). No reason to stop at r177.

**What it gives us:**
- **2-10x** draw-call performance (WebGPU driver overhead far lower than WebGL)
- **GPU compute shader frustum culling** — replaces JS tick entirely, zero CPU cost
- **Indirect draw buffer** populated by compute shader — GPU decides what to draw
- **TSL** (Three Shading Language) — shader nodes in JS, replaces GLSL chunks, 3x faster compilation (r184)
- **Compatibility mode** — `WebGPURenderer` auto-detects: WebGPU where available, WebGL2 fallback elsewhere. One renderer, one code path
- **Scale target:** 250K-500K elements viable (1M particles proven at 60fps, Expo 2025 Osaka)

**Expected performance gains (LTU 122K):**

| | Desktop (today 30-45fps) | Mobile (today 10-20fps) |
|---|---|---|
| **WebGPU path** | 50-60fps | 25-40fps |
| **Key unlock** | Draw-call overhead 5-10x lower | GPU frustum culling (currently disabled — JS too costly) |
| **Fallback** | WebGL2 (same as today) | WebGL2 + render gate + DPR tricks (same as today) |

**Breaking changes that hit us (r160→r184):**

| Version | Break | Our impact |
|---|---|---|
| r161 | `build/three.js` removed, ESM only | Already ESM — none |
| r163 | WebGL 1 dropped | Already WebGL 2 — none |
| r165 | `useLegacyLights` removed | `scene.js` — must re-tune lighting |
| r166 | BatchedMesh requires `addInstance()` | `streaming.js` — must update BM creation |
| r168 | Import paths: `three/webgpu`, `three/tsl` | All imports — search-replace |
| r184 | Deprecated instancing render paths removed | `dlod.js` IM handling — must verify |

**Migration scope (est. 2-3 sessions):**
- `import from "three/webgpu"` instead of `"three"` (r168 path change)
- `WebGPURenderer` with compatibility mode — replaces `WebGLRenderer`
- Async renderer init (`await renderer.init()`)
- Re-tune lighting (`useLegacyLights` removed in r165)
- BatchedMesh: add `addInstance()` calls after `addGeometry()` (r166)
- Verify InstancedMesh zero-scale DLOD against r184 instancing changes
- TSL replaces custom shader code
- Test all 21 buildings

**Risk:** [BatchedMesh slower on Android WebGPU](https://github.com/mrdoob/three.js/issues/29580) — mobile WebGPU is newer, less optimised than desktop. Compatibility mode provides automatic WebGL2 fallback.

**WebGPU browser support (Baseline 2025):** Chrome 148+, Edge, Safari 26+, Firefox (desktop). Mobile: Chrome Android stable, Safari iOS 26+ stable, Firefox Android not yet.

### S274 DONE — DLOD + Mobile Perf
- r160 `perObjectFrustumCulled` handles BM natively (zero JS cost)
- IM zero-scale frustum culling (desktop only, ~1.5ms tick)
- Mobile: DLOD off, on-demand render gate, DPR 0.75 orbit, tab pause
- Bench: `viewer/dlod_bench.html`

### Grid UX Polish
- Roof appearing in ground floor (storey filter)
- Arc segments in grid overlay
- Panel value display on drag
- Hover feedback on grid lines
- Save/restore grid state

### Red Pill Phase 2
- Drag grid → see cost impact in real-time (4D/5D cascade)
- UBBL validator — compliance check on grid change
- Multi-storey cascade (grid change propagates vertically)

### Spatial ERP
- Every record has a place — WMS, POS, MFG, logistics
- Five tables, one state machine, zero install
- See [SpatialERP_OOTB.md](SpatialERP_OOTB.md)

### Community
- Contributed buildings gallery
- Share via URL with embedded state
- Collaborative annotation (Post-it notes on 3D)

## Architecture

```
bim-ootb/                    (GitHub Pages — code only)
  index.html                 Landing page
  viewer/viewer.html         3D viewer
  viewer/*.js                80+ modules
  viewer/lib/                Three.js, sql-wasm, web-ifc
  viewer/locales/            18 languages
  viewer/rates/              17 country templates

OCI bim-ootb bucket          (building databases only)
  buildings/*_extracted.db   Single DB (small buildings)
  buildings/*_meta.db        Split DB (large buildings)
  buildings/*_geo.db         Split DB geometry
  buildings/*_BOM.db         BOM data (Red Pill)
  buildings/city_index.db    786 building bboxes
```

**Deploy:** `git push` → live. No OCI for code. No build step. No server.
