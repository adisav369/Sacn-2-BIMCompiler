# BIM Compiler — Project Roadmap

<div class="bim-banner" markdown>
<center><b>Beta release on Oracle Cloud by July 2026</b></center>
</div>

---

## What Exists Today (March 2026)

| Asset | Measure |
|-------|---------|
| **Compilation pipeline** | 9 stages, 76 verbs, BOM-walk compiler |
| **Buildings proven** | 35 (20 ALL GREEN, 9 WARN, 3 FAIL, 2 stall, 1 generative) |
| **Product library** | 2,475 products in component library |
| **Database architecture** | 4-DB split (ERP, BOM, output, component library) |
| **Geometry Forge** | Formula-driven construction pieces — rafter, stair, pipe bend, dome, vault, rebar. 6 engines, 11 witnesses. [GEOMETRY_FORGE_SRS](GEOMETRY_FORGE_SRS.md) |
| **Dimensional outputs** | 4D scheduling, 5D costing, 6D carbon (schema ready) |
| **Report generators** | 18 templates — BOQ, schedules, takeoffs, financial, compliance. BackOffice + Web UI |
| **Geometric comprehension** | BIMEyes — 28 proof classes, shape/compare/diff verification engine |
| **Test infrastructure** | 261 test classes, 45 witness/proof classes, 6 gates (G0–G6), tamper seal |
| **iDempiere conformance** | INTEGER PK on core tables (Tier 1–2 done). _ID/Name/Value triple. [Study](https://github.com/red1oon/BIMCompiler/blob/master/internal/ID_NAME_VALUE_STUDY.md) |
| **Documentation** | 61 published specs, docs site live |
| **Federation addon** | Blender/Bonsai IFC viewer with NLP query, 80K lines Python |
| **Jurisdictions** | Malaysia (UBBL) — rules seeded |

### Development Velocity

| Period | Commits | Lines | Rate |
|--------|---------|-------|------|
| Federation (Oct 2025, 5 days) | 11 | 80K Python | Proof of concept |
| BIMCompiler (Jan 25 – Mar 28, 62 days) | 920 | 378K (187K Java + 31K Py + 59K SQL + 101K docs) | 14.8 commits/day |
| **Combined** | **931** | **458K** | AI-assisted, specs-first |

---

## Phase 1 — Fleet Convergence (~10 days → early April)

*Get every building to GREEN.*

| Deliverable | Est. | Detail |
|-------------|------|--------|
| Fix CA, CL, WA failures | 3d | Recently unblocked — extraction pipeline fixes |
| Resolve C9 axis warnings (7 buildings) | 2d | Library mesh orientation alignment, not compiler bug |
| RD, RL infrastructure walker | 3d | Non-standard IFC hierarchy — extend walker for rail/road elements |
| DM generative convergence | 2d | Generative path alignment to BOM-walk compiler |
| **Exit criterion** | | 34/34 ALL GREEN (DM tracked separately as generative) |

---

## Phase 2 — Multi-Jurisdiction Rules (~8 days → mid-April)

*The compiler validates against any jurisdiction's building code. Add three more.*

| Jurisdiction | Est. | Source | Key Rules |
|-------------|------|--------|-----------|
| **Malaysia (UBBL)** | — | DONE — seeded in ad_val_rule | UBBL 1984 + amendments |
| **UK (Approved Documents)** | 3d | gov.uk — Crown Copyright (open) | AD B (fire), AD K (stairs), AD M (access) |
| **USA (ADA + IBC)** | 3d | ADA: public domain. IBC: published tables | Room area, door width, stair geometry, egress |
| **Singapore (BCA)** | 2d | BCA Approved Document | Accessibility, fire safety, structural |

The schema already supports multi-jurisdiction — `ad_val_rule.jurisdiction` partitions rules. This phase is **data entry**, not architecture.

---

## Phase 3 — BIM Designer MVP (~31 days → mid-May)

*Interactive building design inside Blender — create, edit, save, recall, compile.*

| Deliverable | Est. | Detail |
|-------------|------|--------|
| Create New building | 3d | One-click with defaults, immediate 3D bbox feedback |
| Room editing | 5d | Slider-driven resize, add/remove rooms, storey management |
| Save / Recall / Approve | 4d | Immutable versioning (W_Variant), non-destructive recall |
| Ambient compliance | 3d | Live status strip — "spell-checker for buildings" (ComplianceStage exists) |
| Assembly Builder | 5d | Layer-by-layer wall/roof/floor composition, U-value calculation. [ASSEMBLY_BUILDER_SRS](ASSEMBLY_BUILDER_SRS.md) |
| Forge UI (p60–p63) | 8d | ForgePanel sidebar, ForgeMesh bridge, ForgeGizmo handles, LOD promotion. [FORGE_SUITE_SRS](FORGE_SUITE_SRS.md) |
| Room-to-set matching | 3d | M_Product_Category drives room→furniture selection — bedroom gets bed set, not sofa set |
| **Exit criterion** | | User can design a house from scratch and compile it without touching SQL |

SRS: [BIM_Designer_SRS.md](BIM_Designer_SRS.md) (2,665 lines, 50 functional requirements specified)

---

## Phase 4 — Dimensional Outputs (~17 days → early June)

*Complete the 4D–8D promise.*

| Dimension | Est. | Current State | Remaining Work |
|-----------|------|--------------|----------------|
| **3D Geometry** | — | LIVE — BOM-walk compiler, LOD400 | Native via Blender viewport |
| **4D Schedule** | — | LIVE — topological sort of BOM tree | — |
| **5D Cost** | — | LIVE — inherent in product data model | — |
| **6D Carbon** | 2d | Schema exists (V010) | Wire SustainabilityDAO, carbon rollup, UI panel |
| **7D Facility Mgmt** | 2d | Schema exists (V010) | Wire FacilityMgmtDAO, maintenance schedule, UI panel |
| **8D ERP Integration** | — | iDempiere table alignment DONE | Live sync to iDempiere instance |
| **2D Layout** | 5d | Specced | Floor plans, elevations, sections as SVG from compiled BOM. [2D_LAYOUT](2D_LAYOUT.md) |
| **PDF Terrain** | 5d | Specced | Survey PDF → elevation points → IFC site topology. [PDF_TERRAIN](PDF_TERRAIN.md) |
| **Audit trail** | 3d | Schema specced (V011) | ChangelogDAO, interceptor, history panel |

---

## Phase 5 — Packaging & Deployment (~16 days → late June)

*From developer tool to installable product.*

| Deliverable | Est. | Detail |
|-------------|------|--------|
| Blender addon packaging | 3d | Single ZIP install for Federation + Designer |
| Java server packaging | 2d | Docker container or self-contained JAR |
| Zero-config onboarding | 3d | "3 minutes to first building" — no manual DB setup |
| PDF report export | 3d | 18 report templates already built — wire to PDF renderer |
| IFC export | 5d | output.db → IFC 4.3 file (round-trip) |
| **Exit criterion** | | Non-developer can install, create a building, and export results |

---

## Phase 6 — Beta Release (~16 days → early July)

*Public beta — real users, real buildings.*

| Deliverable | Est. | Detail |
|-------------|------|--------|
| Cloud deployment | 5d | Oracle Cloud Free Tier — ARM A1 + SQLite, revolving workspace, user saves Order set. [INSTALLER_SPEC.md §10](https://github.com/red1oon/BIMCompiler/blob/master/internal/INSTALLER_SPEC.md#10-oracle-cloud-deployment) |
| User identity | 2d | OCI Identity Domains — self-service signup, OAuth2, social login, MFA. [INSTALLER_SPEC.md §10.5](https://github.com/red1oon/BIMCompiler/blob/master/internal/INSTALLER_SPEC.md#105-user-identity-and-access) |
| Custom domain | 1d | `bomtree.io` → OCI Load Balancer + DNS Zone + Let's Encrypt SSL |
| OCI Marketplace listing | 1d | First BIM-to-BOM tool on Oracle Cloud. [INSTALLER_SPEC.md §10.4](https://github.com/red1oon/BIMCompiler/blob/master/internal/INSTALLER_SPEC.md#104-market-position) |
| Context-sensitive help | 1d | F1 key → github.io docs, deep-linked per panel. [INSTALLER_SPEC.md §11](https://github.com/red1oon/BIMCompiler/blob/master/internal/INSTALLER_SPEC.md#11-context-sensitive-help) |
| API documentation | 3d | OpenAPI/Swagger for BackOffice endpoints |
| Fleet hardening | 5d | Edge cases across all 35 building types |
| Localization | 3d | English, Malay, Mandarin (minimum for ASEAN) |
| **Exit criterion** | | External users can run the full pipeline on their own IFC files |

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
| Oracle DB port | SQLite → Oracle Database for enterprise deployment. iDempiere-native table conventions already aligned |
| Community vocabulary addons | Third-party product catalogs and rule packs |

---

## Go-to-Market

| Phase | Target | Key Actions |
|-------|--------|-------------|
| **Q2 2026** | GitHub public beta | 34+ Rosetta Stones GREEN, docs site, Bonsai demo video |
| **Q3 2026** | Malaysia pilot | CIDB BIM lab, affordable housing showcase, UBBL compliance demo, Oracle Cloud hosted |
| **Q3 2026** | Strategic partnerships | Blender/Bonsai ecosystem, iDempiere community, ConTech investors |
| **Q4 2026** | Academic + industry | Automation in Construction journal, buildingSMART summit |
| **2027** | Scale | Enterprise multi-user, infrastructure + marine domains, training programs |

---

## Project Environment

| Metric | Count |
|--------|-------|
| **Java** | 187K lines — compilation pipeline, 76 verbs, ORM, Forge engines |
| **Python** | 31K lines — Blender/Bonsai Federation addon, IFC extraction |
| **SQL** | 59K lines — 4 databases, migrations, seed data, validation rules |
| **Specs** | 101K lines across 61 published docs |
| **Databases** | 4-DB architecture: ERP (incl. compliance rules), BOM (per-building), output, component library |
| **Commits** | 931 across 2 repos (AI-assisted, specs-first discipline) |
| **Interactive ERD** | [bim_architecture_viz.html](bim_architecture_viz.html) — clickable tables, pipeline, BOM tree |

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
