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

### Infrastructure (S225–S243, S260–S276)
- 18 locales, auto-detected from browser
- Split-DB streaming for large buildings (meta + geo)
- City mode — 786 buildings loaded simultaneously
- Share sheet — IFC/DB save, contribute, system share
- §S274 DLOD: r184 `perObjectFrustumCulled` for BatchedMesh (native, zero JS cost) + InstancedMesh zero-scale frustum culling (desktop). Mobile: render gate + DPR 0.75 orbit. See `docs/FeatureComparison.md` §Visibility Culling.
- §S276: Three.js r160→r184, WebGPURenderer with compat mode, `compileAsync` pipeline gate

### Red Pill / New From Reference (S266–S270)
- Doc Canvas — design document with live BOM
- BOM extraction from IFC to BOM tables (100% JavaScript)
- Verb expansion — BOM Walker ported from Java to browser
- Grid kinematics — drag grid line → cascade recompile building
- UBBL validator spec (trigger semantics, O(K) invariants)

## Next

### S276 DONE — Three.js r184 + WebGPU Upgrade
Upgraded from r160 (Dec 2023) to r184 (Apr 2025) — 24 releases. LTU 122K verified smooth on Chrome.

**What shipped:**
- **r184 split build:** `three.webgpu.min.js` (622KB) + `three.core.min.js` (375KB) = 998KB runtime
- **Native-only WebGPU detection:** `navigator.gpu.requestAdapter()` → if adapter found, use WebGPURenderer. If null, reload THREE from `three.module.min.js` and use WebGLRenderer. No mixed-build issues.
- **Async renderer init:** `setupScene`/`initViewer` async for `await renderer.init()`
- **Physically-correct lighting:** `useLegacyLights` removed (r165), intensities × π (ambient 0.785, sun 4.4, hemi 1.257)
- **BatchedMesh `addInstance()`:** required since r166, added at 3 call sites in streaming.js
- **BVH 0.7.8→0.8.0:** targets r170+
- **Render gates (WebGPU):** skip `render()` during streaming + `compileAsync()` after streaming with status message + deferred bbox clear
- **Dead files removed:** `three.min.js` (UMD 630KB), `OrbitControls.js` (IIFE 28KB), `.r160.bak` files
- **Streaming log spam reduced:** first + every 50K + final only
- **Whitebox tests:** `§WB_S276_LIBS` (998KB budget), `§WB_S276_WIRING` (8 checks), `§WB_S276_RESOURCES` (gates + precache audit)

**Verified working (2026-05-24):** LTU 122K smooth on Chrome (WebGLRenderer r184, adapter=null). Time Machine 122667 ops, DLOD 13611 IM, cinematic 1633 scenes, shadows, night mode, find panel — all functional.

**Key lessons learned:**
1. **Compat mode is slower.** WebGPURenderer compat (WebGL2 backend) transpiles TSL→GLSL per material. 9.2s for 44 materials on Intel iGPU. `compileAsync()` works (doesn't crash) but too slow for production. Direct WebGLRenderer skips TSL.
2. **Native WebGPU only.** Check `navigator.gpu.requestAdapter()` — if null, use WebGLRenderer from standard build. Don't mix builds (webgpu PMREMGenerator expects WebGPURenderer API → ENV_MAP_FAIL).
3. **Laptop GPU selection.** Chrome defaults to Intel iGPU on laptops → adapter=null. Need `chrome://flags/#force-high-performance-gpu` for NVIDIA dGPU.
4. **Render gates during streaming.** `render()` compiles pipelines synchronously — skip during streaming, use `compileAsync()` after.

**Open items (S276b):**
- ENV_MAP fix needs cache purge (scene.js v=47 deployed, browser may cache v=46)
- Chrome NVIDIA dGPU test pending (`chrome://flags/#force-high-performance-gpu`)
- X-ray toggle stutters on 133 materials — explore `scene.overrideMaterial`
- "Multiple instances of Three.js" warning — BVH CDN imports separate three.js
- PCFSoftShadowMap deprecated → update to PCFShadowMap

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
