# BIM Compiler — Project Roadmap

<div class="bim-banner" markdown>
<center><b>Beta release on Oracle Cloud by July 2026</b></center>
</div>

---

## What Exists Today (2026-04-26)

| Asset | Measure |
|-------|---------|
| **Browser BIM Designer (primary)** | IFC + 6 mesh formats import, guided wizard, IFC export, BOQ charts, mobile site camera, cinematic tours, 15 locales. [BIM_Designer_Browser](BIM_Designer_Browser.md) |
| **Compilation pipeline** | 11 stages, 77 verbs, 7,403 products in ERP.db |
| **Rosetta Stone fleet** | 21 buildings, 116/157 gates PASS, 4 ALL GREEN (BR, MO, RL, WI). 9-gate system |
| **Multi-format import (S228)** | Drop Zone: OBJ, STL, DAE, GLB/GLTF, FBX, 3DS — auto-detect up-axis + scale |
| **IFC export (S229)** | DB → .ifc download. Pure STEP text builder, 30-test round-trip suite |
| **Guided wizard (S229a)** | 6-step classification flow for non-IFC meshes → BIM categories |
| **Database architecture** | 4-DB split (ERP, BOM, output, component library) |
| **Blender federation** | City-scale direct streaming from SQLite. 1M elements, 786 buildings. [RTree.md](RTree.md) |
| **Dimensional outputs** | 4D–8D live — template-driven nD engine. 37 buildings + 1M sandbox PASS. [4D5DAnalysis](4D5DAnalysis.md) |
| **2D Layout** | Python DXF generator — SH 6/6, DX 7/7. Java DxfWriter Phase 0-1 done. [2D spec](../2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md) |
| **Geometry Forge** | Formula-driven construction pieces — 6 engines, 11 witnesses. [GEOMETRY_FORGE_SRS](GEOMETRY_FORGE_SRS.md) |
| **Test infrastructure** | 72/72 Playwright (browser), 408/414 Bonsai, 20 BackOffice. 9-gate Rosetta system |
| **Documentation** | 61+ published specs, docs site live |
| **Academic paper** | [Deterministic Spatial Compilation](SPATIAL_COMPILATION_PAPER.md) — 0.002mm worst-case drift |

### Development Velocity

| Period | Commits | Lines | Rate |
|--------|---------|-------|------|
| Federation (Oct 2025, 5 days) | 11 | 80K Python | Proof of concept |
| BIMCompiler (Jan 25 – Apr 26, ~91 days) | 980+ | 480K+ (187K Java + 31K Py + 59K SQL + 115K docs + 87K JS) | ~11 commits/day |
| **Combined** | **990+** | **560K+** | AI-assisted, specs-first |

---

## The Architecture: Database Is the Model

The BIM OOTB browser and the Blender federation addon share the same SQLite
schema. Any process that writes to this schema is instantly viewable — no
export, no format conversion.

```
                    ┌──────────────────────────────────────────────┐
                    │              SQLite (4-table schema)          │
                    │  elements_meta · element_instances            │
                    │  element_transforms · elements_rtree          │
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

The traditional BIM workflow is: **author → export → view → report → export → analyze**.
Each arrow is a format conversion that loses data. Our workflow is:
**import → compile → query**. One database. The viewer, the compiler, the cost
engine, and the designer share one source of truth.

---

## Phase 1 — Browser Hardening (current)

*Make the browser BIM Designer production-ready.*

| Deliverable | Status | Detail |
|-------------|--------|--------|
| Multi-format Drop Zone | DONE (S228) | IFC, OBJ, STL, DAE, GLB/GLTF, FBX, 3DS |
| Guided classification wizard | DONE (S229a) | 6-step flow for non-IFC meshes |
| IFC export round-trip | DONE (S229b) | DB → .ifc, 30-test suite |
| Localisation | DONE (S225b) | 15 locales, rates.js single source |
| Playwright E2E suite | DONE | 72/72 desktop, 53/55 pure-function |
| Mesh tuning | IN PROGRESS | DAE material fidelity, scale edge cases |
| **Exit criterion** | | All import formats robust, test suite green |

---

## Phase 2 — Fleet Convergence

*Get every building to GREEN.*

| Deliverable | Detail |
|-------------|--------|
| Extraction reconciliation | DX, SH, TE — remaining gate failures |
| Generative MEP geometry | Add sprinkler/light geometry to component_library |
| Re-run fleet | Target: 10+ ALL GREEN (from 4) |
| **Exit criterion** | 21/21 buildings ≥ 8/9 gates |

---

## Phase 3 — 2D Layout + Dimensional Outputs

*Auto-generated drawings and complete nD feedback.*

| Deliverable | State | Detail |
|-------------|-------|--------|
| **2D Layout** | Java Phase 0-1 done | Auto-detected page types, DrawingPipeline |
| **4D–8D** | Template engine LIVE | Phase/cost/carbon color-coding in browser |
| **BOQ charts** | DONE (S210) | ExcelJS, WP PACKAGE 1-5, USD, chart images |
| **PDF Terrain** | Specced | Survey PDF → elevation → site topology |
| **Exit criterion** | | 2D drawings from DB, nD outputs in browser |

---

## Phase 4 — OCI Deployment + Beta (~June)

| Deliverable | Detail |
|-------------|--------|
| Oracle Cloud static hosting | Dev bucket live, prod cutover |
| Zero-config onboarding | Share URL → open in browser → import IFC → view |
| Multi-jurisdiction rules | UK (AD B/K/M), Singapore (BCA) — data entry only |
| Documentation polish | Video tutorials, onboarding guide |
| **Exit criterion** | External users import their own IFC files via URL |

---

## Phase 5 — Scale & Ecosystem (Q3–Q4 2026)

| Deliverable | Detail |
|-------------|--------|
| Multi-user collaboration | Two users on same DB, see each other's edits |
| Infrastructure designer | Terrain, alignment, cut-and-fill. [INFRA_DESIGNER_SRS](INFRA_DESIGNER_SRS.md) |
| Domain extension | Marine, tunnels, industrial plant — same engine, different products. [ShipYard](ShipYard.md) |
| Oracle DB port | SQLite → Oracle Database for enterprise |
| Community vocabulary addons | Third-party product catalogs and rule packs |

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
| **Pipeline guide** | [WorkOrderGuide.md](WorkOrderGuide.md) |
| **Data model** | [DATA_MODEL.md](DATA_MODEL.md) |
| **RTree viewer** | [RTree.md](RTree.md) — technology, architecture, streaming design |
| **Academic paper** | [SPATIAL_COMPILATION_PAPER.md](SPATIAL_COMPILATION_PAPER.md) |
| **Strategic positioning** | [StrategicIndustryPositioning.md](StrategicIndustryPositioning.md) |

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
