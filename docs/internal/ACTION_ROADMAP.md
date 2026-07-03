# BIM Compiler — Project Roadmap

<div class="bim-banner" markdown>
<center><b>Beta release on Oracle Cloud by July 2026</b></center>
</div>

---

## What Exists Today (2026-05-03)

| Asset | Measure |
|-------|---------|
| **Browser BIM Designer** | IFC + 6 mesh formats import, guided wizard, IFC export, BOQ charts, mobile site camera, cinematic tours, 18 locales, offline PWA. [BIM_Designer_Browser](BIM_Designer_Browser.md) |
| **Sunglasses (S244)** | Material contrast slider — 10 coloring strategies (warm/cool/earth pastels, storey, discipline, zebra, mono, random, HARD). Shadow study icon planned. [§11 Powerful Scenes](BIM_Designer_Browser.md#11-powerful-scenes) |
| **Offline PWA (S243)** | Service worker, precache, install prompt, offline/online toast. Works disconnected. |
| **Single-DB architecture (S242)** | One DB per building, IFC bbox placeholders, instanced IFC export. 33 buildings on OCI. |
| **Node.js IFC extractor (S241)** | `scripts/extractIFC2DB.js` — disc from filename, multi-disc merge, variance on "revised" only. [Prompt](../prompts/DropIFCMergeNoVarianceDISC.md) |
| **Deep refactor (S239)** | helpers.js, 18 traverse→0, 31 db.exec→dbQuery, 4 SQL injection fixes. `deploy/dev/` canonical. |
| **2D Plans viewer (S236)** | Browser DXF viewer. Canvas2D + dxf-parser. `deploy/dev/2d.html`. [Prompt](../prompts/2D_021_browser_dxf_viewer.md) |
| **Find & Navigate (S233b)** | Indoor wayfinding. 26/26 Playwright PASS. |
| **Mobile merge (S232)** | InstancedMesh batching — 95% draw call reduction on mobile. |
| **Drop Zone import (S228)** | IFC, OBJ, STL, DAE, GLB/GLTF, FBX, 3DS — auto-detect up-axis + scale. |
| **IFC export (S229)** | DB → .ifc download. Pure STEP text builder, 30-test round-trip suite. |
| **Guided wizard (S229a)** | 6-step classification flow for non-IFC meshes → BIM categories. |
| **Localisation (S225b)** | 18 locales, rates.js single source. |
| **Cinematic fly tour** | Spline flythrough, adaptive smoothing, x-ray integration. |
| **Bug reporter** | GitHub + email bug report, HELP FAB, DIY fixes. |
| **4D Ghost Glass (S240b)** | Gantt→Viewer live sync. ghostglass.js: glass-to-solid construction animation, orange scrub line, Play/Pause/Speed, rotating highlight colours, depthTest:false shine-through. 8-check CTFL audit gate. 212 Playwright tests. [S240 Prompt](../prompts/S240_4d_viewer_sync.md) |
| **BOQ charts (S210)** | ExcelJS, WP PACKAGE 1-6, USD, chart images. [4D5DAnalysis](4D5DAnalysis.md) |
| **Compilation pipeline** | 11 stages, 77 verbs, 7,403 products in ERP.db |
| **Rosetta Stone fleet** | 21 buildings, 116/157 gates PASS, 4 ALL GREEN. 9-gate system |
| **Blender federation** | City-scale R-tree streaming. 1M elements, 786 buildings. [RTree.md](RTree.md) |
| **2D Layout** | Python DXF generator — SH 6/6, DX 7/7. Java DxfWriter Phase 0-1. [2D spec](../2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md) |
| **Playwright E2E suite** | 212 tests, 22 specs. `deploy/dev/tests/` |
| **Documentation** | 61+ published specs, docs site live. [INDEX.md](INDEX.md) |
| **Academic paper** | [Deterministic Spatial Compilation](SPATIAL_COMPILATION_PAPER.md) — 0.002mm worst-case drift |

### Development Velocity

| Period | Commits | Lines | Rate |
|--------|---------|-------|------|
| Federation (Oct 2025, 5 days) | 11 | 80K Python | Proof of concept |
| BIMCompiler (Jan 25 – May 26, ~100 days) | 1000+ | 560K+ (187K Java + 31K Py + 59K SQL + 115K docs + 87K JS) | ~10 commits/day |

---

## The Architecture: Database Is the Model

```
                    ┌──────────────────────────────────────────────┐
                    │              SQLite (4-table schema)          │
                    │  elements_meta · element_instances            │
                    │  element_transforms · surface_styles          │
                    └───────┬──────────┬──────────┬────────────────┘
                            │          │          │
                    ┌───────▼──┐ ┌─────▼────┐ ┌──▼──────────┐
                    │ IFC/Mesh │ │ DAG      │ │ Browser     │
                    │ Import   │ │ Compiler │ │ Designer    │
                    └──────────┘ └──────────┘ └─────────────┘
                            │          │          │
                            ▼          ▼          ▼
                    ┌──────────────────────────────────────────────┐
                    │  BIM OOTB (browser) │ Federation (Blender)   │
                    │  sql.js + Three.js  │ R-tree + direct stream │
                    └──────────────────────────────────────────────┘
```

Import → compile → query. One database. The viewer, compiler, cost engine,
and designer share one source of truth.

---

## Next Up — Browser Intelligence

*Architect-facing features that make BIM OOTB a daily tool.*

### S245 — Room Area Calculator + Measure Integration

Extend the 📐 Ruler tool: click a floor polygon → auto-compute area, perimeter, volume.
Data already in the DB (IfcSpace, IfcSlab geometry). Every planning submission needs floor areas.

| Item | Detail |
|------|--------|
| **Mode** | New mode within Measure button — click enclosed floor → area overlay |
| **Data source** | IfcSpace properties if available, else compute from slab geometry |
| **Output** | Area (m²), perimeter (m), ceiling height, door count per space |
| **Spec** | [S211 Space Compliance](../prompts/S211_space_compliance.md) — area vs required, door widths, ceiling checks |

**Done (S245a):** Tap-same-dot area, DB-backed info card, adaptive status text.
- Single tap = blue dot, tap same dot = area (replaces double-click, works mobile+desktop)
- Right-click / long-press info card queries DB for per-class element counts (fixes merged mesh gap)
- Status adapts: desktop "Click/Right-click" vs mobile "Tap/Long-press"
- Area label appears at click point, not mesh center

### S246 — Shadow Study (sun position slider)

Animate directional light by hour and date for real-time shadow analysis.
Activated via icon on the Sunglasses slider panel. Zero perf cost — just update sun position + shadow map.

| Item | Detail |
|------|--------|
| **UI** | Sun icon (☀) on Sunglasses slider panel → opens time/date slider |
| **Range** | 06:00–18:00, month selector for seasonal variation |
| **Tech** | `A.sun.position` already exists. Add shadow camera frustum auto-sizing |
| **Use case** | Planning submissions, solar analysis, shade studies |

### S211 — NLP Query DSL ("Ask the Building")

Natural language queries against the DB. No LLM — pure keyword intent classification + SQL.

| Item | Detail |
|------|--------|
| **Intents** | COUNT, FIND, TOTAL, LIST, SEARCH, DISCIPLINE, MANUFACTURER, PROPERTY, STOREY |
| **Patterns** | 40+ across 8 categories |
| **Voice** | Web Speech API (Chrome/Safari native) — mic button with interim text |
| **Examples** | "Show me all fire exits on Level 2", "Total glass area", "Count doors" |
| **Spec** | [ROADMAP.md §S211](ROADMAP.md#s211--nlp-query-dsl-harden--browser-port--voice) |

### S211a — Clash Detection (R-tree bbox)

Template-driven clash detection with severity heatmap. No server.

| Item | Detail |
|------|--------|
| **Method** | R-tree bbox collision from element_transforms |
| **Severity** | Hard (red), soft (orange), near-miss (yellow) — configurable tolerance |
| **Output** | Clash list panel, sortable, export to Excel or BCF XML |
| **Spec** | [ROADMAP.md §S211a](ROADMAP.md#s211a--clash-detection-template-driven-r-tree-bbox) |
| **SHIPPED** | **[Clash Detection — full feature guide](CLASH_DETECTION.md)** (S245c/d, 2026-05-04) |

### S211b — Heatmap Costing ($ → colour by value)

Dollar icon colours elements by cost, area, volume, or custom field.

| Item | Detail |
|------|--------|
| **UI** | $ button in toolbar → colour gradient overlay |
| **Fields** | Cost (from rates.js), area, volume, weight, or any numeric property |
| **Spec** | [ROADMAP.md §S211b](ROADMAP.md#s211b--heatmap-costing--icon--colour-by-value) |

### S211d — Revision Diff (before/after visual)

Compare two versions: green (added), red ghost (removed), yellow (changed).

| Item | Detail |
|------|--------|
| **Method** | Loads diff only, not both full models |
| **Visual** | Ghost overlay — red 50% opacity for removed elements |
| **Spec** | [ROADMAP.md §S211d](ROADMAP.md#s211d--revision-diff-on-demand-delta-not-dual-load) |

### S221 — 4D Construction Animation

Timeline playback — slide through construction phases, watch building assemble.

| Item | Detail |
|------|--------|
| **UI** | Timeline slider, play/pause, speed control |
| **Data** | Storey + discipline ordering, or user-defined phases |
| **Spec** | [ROADMAP.md §S221](ROADMAP.md#s221--4d-construction-animation-timeline-playback) |

---

## Ongoing — iDempiere BIM OOTB Integration

OSGi plugin embedding the BIM viewer as a native tab in iDempiere's C_Project window.

| Item | Detail |
|------|--------|
| **Goal** | Drop IFC → 3D scene in iDempiere → save → C_Project.Name updated |
| **Architecture** | ZK iframe + BIM OOTB HTML, OSGi bundle, IWebActionHandler |
| **Status** | Testing — plugin structure built, tab renders, IFC drop wiring in progress |
| **Spec** | [iDempiereOOTB prompt](../prompts/iDempiereOOTB.md) |
| **Code** | `iDempiereOOTB/org.idempiere.bimootb/` |

---

## Phase 3 — 2D Layout + Dimensional Outputs

| Deliverable | State | Detail |
|-------------|-------|--------|
| **2D Layout** | Java Phase 0-1 done | Auto-detected page types, DrawingPipeline |
| **4D–8D** | Template engine LIVE | Phase/cost/carbon color-coding in browser |
| **PDF Terrain** | Specced | Survey PDF → elevation → site topology. [PDF_TERRAIN](PDF_TERRAIN.md) |

---

## Phase 4 — OCI Deployment + Beta (~June)

| Deliverable | Detail |
|-------------|--------|
| Oracle Cloud static hosting | 3 buckets live (dev, full, live). 33 buildings deployed |
| Zero-config onboarding | Share URL → open in browser → import IFC → view |
| Multi-language (S225b) | 18 locales live, flag picker in toolbar |
| Multi-jurisdiction rules | UK (AD B/K/M), Singapore (BCA) — data entry only |
| **Exit criterion** | External users import their own IFC files via URL |

---

## Phase 5 — Scale & Ecosystem (Q3–Q4 2026)

| Deliverable | Detail |
|-------------|--------|
| iDempiere integration | BIM tab in C_Project — ERP meets BIM. [Prompt](../prompts/iDempiereOOTB.md) |
| Plugin marketplace (S215) | ADUI metadata-driven plugins. [ROADMAP §S215](ROADMAP.md#s215--plugin-marketplace-adui-metadata--the-multiplier) |
| Multi-user collaboration | Two users on same DB, see each other's edits |
| Property editing (S216) | Viewer → tool: edit properties, save back to DB. [ROADMAP §S216](ROADMAP.md#s216--property-editing--save-db-viewer--tool) |
| Infrastructure designer | Terrain, alignment, cut-and-fill. [INFRA_DESIGNER_SRS](INFRA_DESIGNER_SRS.md) |
| Domain extension | Marine, tunnels, industrial plant. [ShipYard](ShipYard.md) |
| Oracle DB port | SQLite → Oracle Database for enterprise |

---

## Go-to-War

| Phase | Target | Key Actions |
|-------|--------|-------------|
| **Q2 2026** | Public beta | OCI-hosted browser viewer, share-by-URL onboarding |
| **Q3 2026** | Malaysia pilot | CIDB BIM lab, affordable housing, UBBL compliance |
| **Q3 2026** | Community | Bonsai/IfcOpenShell ecosystem, iDempiere community, osARCH |
| **Q4 2026** | Academic + industry | Automation in Construction journal, buildingSMART summit |
| **2027** | Scale | Enterprise multi-user, training programs, domain extensions |

---

## The Paradigm

The database is the model. The viewer reads SQL. The compiler writes SQL.
See [MANIFESTO.md](MANIFESTO.md) for the full architecture rationale, [RTree.md](RTree.md) for streaming design, and [SPATIAL_COMPILATION_PAPER.md](SPATIAL_COMPILATION_PAPER.md) for the academic treatment.

---

## Quick Links

| Resource | URL |
|----------|-----|
| **Docs site** | [red1oon.github.io/BIMCompiler](https://red1oon.github.io/BIMCompiler/) |
| **GitHub** | [github.com/red1oon/BIMCompiler](https://github.com/red1oon/BIMCompiler) |
| **Manifesto** | [MANIFESTO.md](MANIFESTO.md) |
| **Spec index** | [INDEX.md](INDEX.md) |
| **Full ROADMAP** | [ROADMAP.md](ROADMAP.md) — all S210–S231 scenarios with detailed specs |
| **Pipeline guide** | [WorkOrderGuide.md](WorkOrderGuide.md) |
| **Data model** | [DATA_MODEL.md](DATA_MODEL.md) |
| **Strategic positioning** | [StrategicIndustryPositioning.md](StrategicIndustryPositioning.md) |

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
