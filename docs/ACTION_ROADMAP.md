# BIM Compiler — Project Roadmap

<div class="bim-banner" markdown>
<center><b>Beta release on Oracle Cloud by July 2026</b></center>
</div>

---

## What Exists Today

| Asset | Measure |
|-------|---------|
| **Compilation pipeline** | 12 stages, 77 verbs, BOM-walk compiler |
| **Buildings proven** | 21 extracted, 4 ALL GREEN, 116/157 gate PASS |
| **Product library** | 7,403 products in ERP.db. 123,573 meshes in component_library.db |
| **Database architecture** | 4-DB split (ERP, BOM, output, component library) |
| **Geometry Forge** | Formula-driven construction pieces — rafter, stair, pipe bend, dome, vault, rebar. 6 engines, 11 witnesses. [GEOMETRY_FORGE_SRS](GEOMETRY_FORGE_SRS.md) |
| **Dimensional outputs** | 4D–8D live — template-driven nD engine. 37 buildings + 1M sandbox PASS. [4D5DAnalysis](4D5DAnalysis.md) |
| **Direct Stream viewer** | City-scale BIM streaming from SQLite. 1M elements, 786 buildings, cinematic autopilot, one-click sun. [RTree.md](RTree.md) |
| **2D Layout** | Python DXF generator — SH 6/6, DX 7/7. Java DxfWriter Phase 0-1 done. [2D spec](../2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md) |
| **Report generators** | BOQ, schedules, takeoffs, nD Excel — template-driven |
| **BIMEyes** | 28 proof classes, shape/compare/diff verification engine |
| **Test infrastructure** | 408 Bonsai tests, 20 BackOffice tests. 9-gate Rosetta system |
| **iDempiere conformance** | INTEGER PK on core tables. _ID/Name/Value triple |
| **Documentation** | 61+ published specs, docs site live |
| **Academic paper** | [Deterministic Spatial Compilation](SPATIAL_COMPILATION_PAPER.md) — 0.002mm worst-case drift |

### Development Velocity

| Period | Commits | Lines | Rate |
|--------|---------|-------|------|
| Federation (Oct 2025, 5 days) | 11 | 80K Python | Proof of concept |
| BIMCompiler (Jan 25 – Apr 19, ~84 days) | 950+ | 460K+ (187K Java + 31K Py + 59K SQL + 101K docs) | ~11 commits/day |
| **Combined** | **960+** | **540K+** | AI-assisted, specs-first |

---

## The Pivot: Direct Stream Changes Everything (S195–S198)

Prior to S195, viewing was a multi-step pipeline: extract → bake → save .blend → link.
Direct Stream eliminated all intermediate steps. **The database IS the viewer.**

This creates a **live feedback loop** between every stage of the compiler and the 3D viewport:

```
                    ┌──────────────────────────────────────────────┐
                    │              SQLite (4-table schema)          │
                    │  elements_meta · element_instances            │
                    │  element_transforms · elements_rtree          │
                    └───────┬──────────┬──────────┬────────────────┘
                            │          │          │
                    ┌───────▼──┐ ┌─────▼────┐ ┌──▼──────────┐
                    │ IFC      │ │ DAG      │ │ BIM         │
                    │ Extract  │ │ Compiler │ │ Designer    │
                    └──────────┘ └──────────┘ └─────────────┘
                            │          │          │
                            ▼          ▼          ▼
                    ┌──────────────────────────────────────────────┐
                    │           Direct Stream Viewer                │
                    │  from_pydata() → Blender viewport            │
                    │  Camera-driven · Envelope-first · Live HUD   │
                    └──────────────────────────────────────────────┘
```

**Any process that writes to the 4-table schema is instantly viewable.**
No export. No format conversion. No bake step. Write a row → see it in 3D.

### Real-Time Feedback Loops Enabled

| Loop | How it works | Impact |
|------|-------------|--------|
| **Compiler → 3D** | DAGCompiler writes output.db → Direct Stream reads it | Architect sees compilation result instantly, catches errors before construction |
| **Designer → 3D** | BIM Designer writes to project.db → next tick shows it | Interactive design at city scale — drag wall, see it appear |
| **Excel ↔ 3D** | nD engine writes phase/cost tags to DB → viewer color-codes | QS changes cost in Excel, sees heat map update in viewport. Click element → see its BOQ row |
| **2D ↔ 3D** | DXF generator reads same DB → floor plan and 3D from same source | Change in 3D reflects in 2D. Annotate 2D → XDATA links back to 3D element via GUID |
| **4D ↔ 3D** | Schedule phases tagged on elements → viewer filters by phase | Project manager drags a Gantt bar → building elements light up in construction sequence |
| **Audit → 3D** | Click BOQ line item → camera flies to element → see it in context | Quantity verification becomes visual. No more digging through spreadsheets |

### Who Else Has Done This?

**Nobody.** The BIM industry treats viewing and data as separate concerns:

| Tool | Approach | Limitation |
|------|----------|------------|
| **Revit** | Parametric modelling, built-in viewer | Single building, slow viewport, no streaming |
| **Navisworks** | Federated model review | Pre-loaded .nwf files, no live DB connection |
| **BIM360/ACC** | Cloud viewer | Web-based, limited interaction, no live feedback |
| **IFC.js / That Open Company** | WebGL IFC viewer | Single file, no city scale, no compilation feedback |
| **Cesium/3D Tiles** | Geospatial streaming | Baked tiles, no per-element identity, no BIM semantics |
| **Unreal/Twinmotion** | Game engine visualization | Manual import, no DB link, no live compilation loop |

**What makes Direct Stream unique:**
1. **Database-native** — no file format, no export, no intermediate representation
2. **Per-element identity** — every pixel traces to a GUID, queryable at any time
3. **Compilation-aware** — the viewer knows about BOMs, phases, costs, not just geometry
4. **City-scale** — 1M elements, 786 buildings, interactive on a laptop
5. **Bidirectional** — the same DB that drives the viewer accepts writes from the designer

### Impact on the BIM World

The traditional BIM workflow is: **author → export → view → report → export → analyze**.
Each arrow is a format conversion that loses data.

Our workflow is: **compile → query**. One database. Every tool reads/writes the same tables.
The viewer, the compiler, the cost engine, the scheduler, and the designer share one source of truth.

This means:
- **Architects** see compilation errors before they become construction defects
- **Quantity surveyors** click a cost figure and see the element in 3D
- **Project managers** drag a schedule bar and see which elements light up
- **Facility managers** query the as-built DB and fly to any asset
- **Developers** fly through a 500-house township in real time for investor presentations

The DB is not a staging area. It is the model, the viewer, the report, and the design tool.

---

## Phase 1 — Direct Stream Integration (~8 days)

*Connect every pipeline stage to the viewer.*

| Deliverable | Est. | Detail |
|-------------|------|--------|
| DAGCompiler output → streamable | 2d | Add element_transforms + elements_rtree to output.db |
| nD phase tagging | 2d | Tag elements_meta with 4D phase, 5D cost band, 6D carbon tier |
| Color-by-dimension | 1d | Direct Stream colors elements by phase/cost/carbon from DB tags |
| Excel listener | 2d | File watcher on nD Excel → update DB tags → viewport refreshes |
| Click-to-BOQ | 1d | Click streamed element → show BOQ line item in N-panel |
| **Exit criterion** | | Compiler output viewable. Click element → see its cost + schedule |

Spec: [RTree.md](RTree.md) §How It Works | `prompts/S199_stream_integration.md` (TODO)

---

## Phase 2 — Fleet Convergence (~8 days)

*Get every building to GREEN.*

| Deliverable | Est. | Status |
|-------------|------|--------|
| Extraction reconciliation fix | 3d | FK/GH/IN/JS/TE — QA strict on element delta |
| Generative MEP geometry | 2d | Add sprinkler/light geometry to component_library |
| C8 IfcDoor mesh diversity | 1d | WL/WT fidelity FAIL |
| Re-run fleet | 2d | Target: 10+ ALL GREEN (from 4) |
| **Exit criterion** | | 21/21 buildings ≥ 8/9 gates |

---

## Phase 3 — BIM Designer MVP (~25 days → mid-May)

*Interactive building design inside Blender — write to DB, see it stream.*

| Deliverable | Est. | Detail |
|-------------|------|--------|
| Create New building | 3d | One-click with defaults → writes to project.db → streams immediately |
| Room editing | 5d | Slider-driven resize → DB update → viewport update on next tick |
| Save / Recall / Approve | 3d | Immutable versioning (W_Variant) |
| Ambient compliance | 3d | Live status strip — "spell-checker for buildings" |
| Assembly Builder | 5d | Wall/roof/floor composition, U-value. [ASSEMBLY_BUILDER_SRS](ASSEMBLY_BUILDER_SRS.md) |
| Forge UI | 3d | ForgePanel sidebar, ForgeMesh bridge. [FORGE_SUITE_SRS](FORGE_SUITE_SRS.md) |
| Finish Walls | 3d | Detect + complete missing ARC walls. [FINISH_WALLS_SRS](FINISH_WALLS_SRS.md) |
| **Exit criterion** | | Design a house → compile → stream → all from one DB, no file export |

---

## Phase 4 — Dimensional Outputs (~12 days → early June)

*Complete the nD promise with visual feedback.*

| Dimension | Est. | State | Remaining |
|-----------|------|-------|-----------|
| **3D** | — | LIVE | Direct Stream viewer |
| **4D Schedule** | 1d | LIVE — template engine | Phase color-coding in viewport |
| **5D Cost** | 1d | LIVE — template engine | Cost heat map in viewport |
| **6D Carbon** | 1d | Schema + templates done | Carbon tier visualization |
| **7D Facility** | 1d | Schema + templates done | Asset fly-to from maintenance schedule |
| **8D ERP** | — | iDempiere alignment done | Live sync to iDempiere instance |
| **2D Layout** | 5d | Python prototype DONE, Java Phase 0-1 | TB-KLTN Phase A hardening |
| **PDF Terrain** | 3d | Specced | Survey PDF → elevation → site topology |

---

## Phase 5 — Packaging & Deployment (~12 days → late June)

| Deliverable | Est. | Detail |
|-------------|------|--------|
| Blender addon packaging | 3d | Single ZIP install — Federation + Designer + Direct Stream |
| Distribution: 2 SQLite files | 1d | component_library.db + project.db = complete portable project |
| Zero-config onboarding | 2d | "3 minutes to first building" — no DB setup |
| IFC export | 3d | output.db → IFC 4.3 round-trip |
| Cloud deployment | 3d | Oracle Cloud Free Tier — SQLite + static file serving |
| **Exit criterion** | | Non-developer installs, streams a city, exports IFC |

---

## Phase 6 — Beta Release (~10 days → early July)

| Deliverable | Est. | Detail |
|-------------|------|--------|
| Oracle Cloud hosting | 3d | ARM A1, SQLite, revolving workspace |
| Custom domain | 1d | `bomtree.io` |
| User identity | 2d | OCI Identity Domains — OAuth2, social login |
| Multi-jurisdiction rules | 2d | UK (AD B/K/M), Singapore (BCA) — data entry only |
| Documentation polish | 2d | Video tutorials, onboarding guide |
| **Exit criterion** | | External users run the pipeline on their own IFC files |

---

## Phase 7 — Scale & Ecosystem (Q3–Q4 2026)

| Deliverable | Detail |
|-------------|--------|
| WebGPU port | Direct Stream in browser — `from_pydata` → WebGPU mesh. Same DB, no Blender required |
| Multi-user collaboration | Two users stream same DB, see each other's edits in real time |
| Infrastructure designer | Terrain, alignment, cut-and-fill. [INFRA_DESIGNER_SRS](INFRA_DESIGNER_SRS.md) |
| Domain extension | Marine, tunnels, industrial plant — same engine, different products. [ShipYard](ShipYard.md) |
| Oracle DB port | SQLite → Oracle Database for enterprise |
| Community vocabulary addons | Third-party product catalogs and rule packs |

---

## Go-to-Market

| Phase | Target | Key Actions |
|-------|--------|-------------|
| **Q2 2026** | GitHub public beta | City-scale demo video, Bonsai community, osARCH post |
| **Q3 2026** | Malaysia pilot | CIDB BIM lab, affordable housing, UBBL compliance, Oracle Cloud |
| **Q3 2026** | Strategic partnerships | Blender/Bonsai ecosystem, iDempiere community, ConTech investors |
| **Q4 2026** | Academic + industry | Automation in Construction journal, buildingSMART summit |
| **2027** | Scale | Enterprise multi-user, WebGPU browser viewer, training programs |

---

## The Paradigm

Direct Stream proves that a **compiled database** is a better viewer than a **modelled file**.
The geometry, the semantics, the cost, and the schedule are the same data structure.
The viewer doesn't interpret the data — it streams it.

This started from a simple observation in [RTree.md](RTree.md):

> *The industry loads the model, then queries it. We query the index. The model loads only what you ask for.*

From that, everything follows: envelope-first streaming, camera-driven phases,
compilation-to-viewport feedback, the death of the export step.

The database is the model. The viewer reads SQL. The compiler writes SQL.
The whole suite shares one truth.

See: [MANIFESTO.md](MANIFESTO.md) | [RTree.md](RTree.md) | [SPATIAL_COMPILATION_PAPER.md](SPATIAL_COMPILATION_PAPER.md)

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
