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

### S276 — Three.js r177 + WebGPU Upgrade (Priority 1)
Currently on r160 (Dec 2023). r177 (May 2026) brings WebGPU — the single biggest performance unlock.

**What it gives us:**
- **2-10x** draw-call performance (WebGPU driver overhead far lower than WebGL)
- **GPU compute shader frustum culling** — replaces JS tick entirely, zero CPU cost
- **Indirect draw buffer** populated by compute shader — GPU decides what to draw
- **TSL** (Three Shading Language) — shader nodes in JS, replaces GLSL chunks
- **Scale target:** 250K-500K elements viable (1M particles proven at 60fps, Expo 2025 Osaka)

**Migration scope (est. 2-3 sessions):**
- `import from "three/webgpu"` instead of `"three"`
- Async renderer init (`await renderer.init()`)
- Re-tune lighting (`useLegacyLights` removed in r165)
- TSL replaces custom shader code
- Test all 21 buildings

**Risk:** [BatchedMesh slower on Android WebGPU](https://github.com/mrdoob/three.js/issues/29580) — mobile WebGPU is newer, less optimised than desktop. WebGL fallback available.

**WebGPU browser support (Baseline Jan 2026):** Chrome, Edge, Firefox, Safari 26 — all ship stable.

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
