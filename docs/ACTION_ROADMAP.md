# BIM Compiler — Project Roadmap

> **Construction is manufacturing with coordinates.** This compiler turns IFC
> geometry into ERP-structured Bills of Materials — same tables, same logic that
> manufacturing has used for 30 years, applied to construction.

<div class="bim-banner" markdown>
<b>One domain expert, AI-assisted. 913 commits in 62 days. ~948,000 lines landed. $550 in AI costs.</b>
</div>

---

## What Exists Today (March 2026)

| Asset | Measure |
|-------|---------|
| **Compilation pipeline** | 9 stages, 76 verbs, BOM-walk compiler |
| **Buildings proven** | 35 (24 ALL GREEN, 7 WARN, 3 FAIL, 2 pending) |
| **Product library** | 2,475 products in component library |
| **Database architecture** | 5-DB split (ERP, BOM, output, validation, component library) |
| **Geometry Forge** | Formula-driven construction pieces — rafter, stair, pipe bend, dome, vault, rebar. 6 engines, 11 witnesses. [GEOMETRY_FORGE_SRS](GEOMETRY_FORGE_SRS.md) |
| **Dimensional outputs** | 4D scheduling, 5D costing, 6D carbon (schema ready) |
| **Report generators** | 18 templates — BOQ, schedules, takeoffs, financial, compliance. BackOffice + Web UI |
| **Geometric comprehension** | BIMEyes — 28 proof classes, shape/compare/diff verification engine |
| **Test infrastructure** | 261 test classes, 45 witness/proof classes, 6 gates (G0–G6), tamper seal |
| **iDempiere conformance** | INTEGER PK on core tables (Tier 1–2 done). _ID/Name/Value triple. [Study](ID_NAME_VALUE_STUDY.md) |
| **Documentation** | 61 published specs, docs site live |
| **Federation addon** | Blender/Bonsai IFC viewer with NLP query, 80K lines Python |
| **Jurisdictions** | Malaysia (UBBL) — rules seeded |
| **Code** | 272K Java + 221K Python + 59K SQL |

---

## Phase 1 — Fleet Convergence (April 2026)

*Get every building to GREEN.*

| Deliverable | Detail |
|-------------|--------|
| Fix CA, CL, WA failures | Recently unblocked — extraction pipeline fixes |
| Resolve C9 axis warnings (7 buildings) | Library mesh orientation alignment, not compiler bug |
| RD, RL infrastructure walker | Non-standard IFC hierarchy — extend walker for rail/road elements |
| DM generative convergence | Generative path alignment to BOM-walk compiler |
| **Exit criterion** | 34/34 ALL GREEN (DM tracked separately as generative) |

---

## Phase 2 — Multi-Jurisdiction Rules (April–May 2026)

*The compiler validates against any jurisdiction's building code. Add three more.*

| Jurisdiction | Source | Copyright | Key Rules |
|-------------|--------|-----------|-----------|
| **Malaysia (UBBL)** | UBBL 1984 + amendments | Widely cited values | DONE — seeded in ad_val_rule |
| **UK (Approved Documents)** | gov.uk — free download | Crown Copyright (open) | AD B (fire), AD K (stairs), AD M (access) |
| **USA (ADA + IBC)** | ADA: ada.gov (public domain). IBC: published dimensional tables | ADA: zero risk. IBC: common-knowledge values | Room area, door width, stair geometry, egress |
| **Singapore (BCA)** | BCA Approved Document | Government publication | Accessibility, fire safety, structural |

The schema already supports multi-jurisdiction — `ad_val_rule.jurisdiction` partitions rules. This phase is **data entry**, not architecture.

---

## Phase 3 — BIM Designer MVP (May 2026)

*Interactive building design inside Blender — create, edit, save, recall, compile.*

| Deliverable | Detail |
|-------------|--------|
| Create New building | One-click with defaults, immediate 3D bbox feedback |
| Room editing | Slider-driven resize, add/remove rooms, storey management |
| Save / Recall / Approve | Immutable versioning (W_Variant), non-destructive recall |
| Ambient compliance | Live status strip — "spell-checker for buildings" |
| Promote to BOM | Approved design → BOM dictionary entry |
| Assembly Builder | Layer-by-layer wall/roof/floor composition, U-value calculation. [ASSEMBLY_BUILDER_SRS](ASSEMBLY_BUILDER_SRS.md) |
| Forge computation | Formula-driven pieces (rafters, stairs, rebar) integrated into compile. Forge UI panel + gizmo handles. [FORGE_SUITE_SRS](FORGE_SUITE_SRS.md) |
| Room-to-set matching | M_Product_Category drives room→furniture selection — bedroom gets bed set, not sofa set |
| **Exit criterion** | User can design a house from scratch and compile it without touching SQL |

SRS: [BIM_Designer_SRS.md](BIM_Designer_SRS.md) (2,665 lines, 50 functional requirements specified)

---

## Phase 4 — Dimensional Outputs (May–June 2026)

*Complete the 4D–8D promise.*

| Dimension | Current State | Remaining Work |
|-----------|--------------|----------------|
| **3D Geometry** | LIVE — BOM-walk compiler, LOD400 | Native via Blender viewport |
| **4D Schedule** | LIVE — topological sort of BOM tree | — |
| **5D Cost** | LIVE — inherent in product data model | — |
| **6D Carbon** | Schema exists (V010) | Wire SustainabilityDAO, carbon rollup, UI panel |
| **7D Facility Mgmt** | Schema exists (V010) | Wire FacilityMgmtDAO, maintenance schedule, UI panel |
| **8D ERP Integration** | iDempiere table alignment DONE | Live sync to iDempiere instance |
| **2D Layout** | Specced | Floor plans, elevations, sections as SVG from compiled BOM. [2D_LAYOUT](2D_LAYOUT.md) |
| **PDF Terrain** | Specced | Survey PDF → elevation points → IFC site topology. [PDF_TERRAIN](PDF_TERRAIN.md) |
| **Audit trail** | Schema specced (V011) | ChangelogDAO, interceptor, history panel |

---

## Phase 5 — Packaging & Deployment (June 2026)

*From developer tool to installable product.*

| Deliverable | Detail |
|-------------|--------|
| Blender addon packaging | Single ZIP install for Federation + Designer |
| Java server packaging | Docker container or self-contained JAR |
| Zero-config onboarding | "3 minutes to first building" — no manual DB setup |
| PDF report export | 18 report templates already built — wire to PDF renderer |
| IFC export | output.db → IFC 4.3 file (round-trip) |
| Forge fabrication export | Cut lists, rebar schedules, work orders from ad_forge_fabrication |
| **Exit criterion** | Non-developer can install, create a building, and export results |

---

## Phase 6 — Beta Release (June–July 2026)

*Public beta — real users, real buildings.*

| Deliverable | Detail |
|-------------|--------|
| Cloud deployment | Multi-user server, auth, project storage |
| API documentation | OpenAPI/Swagger for BackOffice endpoints |
| Fleet hardening | Edge cases across all 35 building types |
| Localization | English, Malay, Mandarin (minimum for ASEAN) |
| **Exit criterion** | External users can run the full pipeline on their own IFC files |

---

## Phase 7 — Production & Scale (Q3–Q4 2026)

| Deliverable | Detail |
|-------------|--------|
| C_Project multi-building | Site grid generation, compile-once-copy-many, consolidated output. [ProjectOrderBlueprint](ProjectOrderBlueprint.md) |
| Click-to-place | Interactive 3D element placement via BlenderBridge |
| Freehand drawing → BOM | Viewport geometry becomes BOM mutation |
| MIRROR verb for duplex | Automated mirrored-unit compilation (DX: 85 axis mismatches) |
| Spatial predicate verbs | DISTANCE_BETWEEN, CLEARANCE_BETWEEN, NEAREST |
| Infrastructure designer | Terrain, alignment, cut-and-fill for road/rail/bridge. [INFRA_DESIGNER_SRS](INFRA_DESIGNER_SRS.md) |
| Self-orienting BOMs | Phantom spatial children (WALL_BACK, FACE_TOWARD, CLEARANCE) — fixture rotation from data, not heuristics |
| Domain extension | Marine hulls, tunnels, earthworks, industrial plant — same engine, different products. [ShipYard](ShipYard.md) |
| Dimensional tolerance fit | ±10% catalog match with scale transform — door/window fitting across buildings |
| Community vocabulary addons | Third-party product catalogs and rule packs |

---

## Go-to-Market

| Phase | Target | Key Actions |
|-------|--------|-------------|
| **Q2 2026** | GitHub public beta | 34+ Rosetta Stones GREEN, docs site, Bonsai demo video |
| **Q3 2026** | Malaysia pilot | CIDB BIM lab, affordable housing showcase, UBBL compliance demo |
| **Q3 2026** | Strategic partnerships | Blender/Bonsai ecosystem, iDempiere community, ConTech investors |
| **Q4 2026** | Academic + industry | Automation in Construction journal, buildingSMART summit |
| **2027** | Scale | Enterprise multi-user, infrastructure + marine domains, training programs |

---

## The Moat

1. **BOM-as-recipe is the insight.** A building is a manufactured product with coordinates. iDempiere's 30-year-old M_Product / M_BOM / C_Order pattern handles procurement, scheduling, cost, and quality. We add the *where*.

2. **No one else has this stack.** BIM tools don't speak ERP. ERP tools don't speak IFC. This compiler is the bridge — and it took 20 years of iDempiere internals to know where to put it.

3. **Proven at scale.** 48,428 elements compiled via BOM walk (Terminal building). 35 buildings across residential, commercial, infrastructure, and industrial. Not a prototype — a working compiler.

4. **AI-assisted development.** A domain expert with 20 years of ERP and construction knowledge drives every decision — AI handles the volume. 15 commits/day, one person, specs-first discipline. The codebase is AI-maintainable by design — witnesses, tamper seals, anti-drift gates — but the architecture comes from the subject matter expert.

---

## Quick Links

| Resource | URL |
|----------|-----|
| **Docs site** | [red1oon.github.io/BIMCompiler](https://red1oon.github.io/BIMCompiler/) |
| **GitHub** | [github.com/red1oon/BIMCompiler](https://github.com/red1oon/BIMCompiler) |
| **Manifesto** | [MANIFESTO.md](MANIFESTO.md) |
| **Spec index** | [INDEX.md](INDEX.md) |
| **Pipeline guide** | [WorkOrderGuide.md](WorkOrderGuide.md) |
| **Source code guide** | [SourceCodeGuide.md](SourceCodeGuide.md) |
| **Data model** | [DATA_MODEL.md](DATA_MODEL.md) |
