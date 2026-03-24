# BIM Intent Compiler

**Construction is manufacturing. A building IS its Bill of Materials.**

This is a metadata-driven, deterministic compiler that reads BOM data and produces 3D building coordinates — the same thing an ERP system does when it explodes a manufacturing BOM into work orders. Every output element traces to a library input. Nothing is invented.

## Why This Exists

The AEC industry has a productivity problem. CAD tools model geometry. BIM tools add metadata to geometry. This project takes a different path: **start from metadata, compile to geometry.** A building is a product. Its BOM is its complete definition.

The data model is [iDempiere](https://idempiere.org/) ERP: `M_Product`, `M_BOM`, `C_Order`, `C_DocType` — repurposed for construction. Adding a new building type means adding BOM data, not writing Java code.

## The Data Flywheel

The compiler gets smarter with every building it processes. Each onboarded IFC file contributes dimension patterns to a mined validation pool. Each new IFC is checked against that pool before compilation. This is an emergent property — not designed, but a natural consequence of three architectural decisions:

1. **"Extract or compile only"** — forces the pipeline to read real buildings, building a corpus of real-world data
2. **34 buildings onboarded** — creates enough statistical mass for meaningful dimensional ranges
3. **disc_validation.db** — gives the mined rules a clean home, separate from product geometry

The result: **415 validation rules mined from 20 real buildings** across 25 IFC classes. When a new IFC arrives, every element's dimensions are checked against observed ranges. A wall at 3,000mm passes silently. A wall at 500,000mm is flagged before the pipeline invests effort in BOM compilation. After compilation, the new building's patterns feed back into the pool — widening the ranges and reducing false positives for the next one.

No BIM tool in the industry does this. They validate against fixed, hand-authored rules. This system validates against empirical evidence from its own corpus — like a spell-checker built from real documents, not just grammar rules.

See [BOMBasedCompilation.md §9](docs/BOMBasedCompilation.md) for the full specification.

## We Got Eyes

A blind spot — pun intended — in BIM software is the inability to verify geometry without human eyes. Someone has to open a viewer, rotate the model, and eyeball whether that door is actually inside its wall. Scale that to 48,000 elements across eight disciplines and you have an industry running on hope.

This compiler can see. Every element's shape is reduced to three dimensionless ratios — planarity, elongation, squareness — that form a geometric fingerprint. A wall *must* be planar. A column *must* be elongated. Furniture *must* be compact. These are mathematical facts, not heuristics, and they hold regardless of scale, coordinate system, or what the BIM tool labelled the element.

The fingerprint engine (BIMEyes) powers 26 proofs across three tiers:

| Tier | What it proves | Example |
|------|---------------|---------|
| **Per-element** | Each element is geometrically sane | Positive extent, finite coordinates, non-degenerate dimensions |
| **Pairwise** | Elements relate correctly to each other | Doors contained in walls, furniture inside rooms, no same-class overlaps |
| **Aggregate** | The building makes sense as a whole | Rooms have walls + floor + ceiling + door, waste pipes slope downward, MEP systems are connected |

The result: **97% of 90,310 elements across 32 buildings pass both shape AND position proof.** 22 buildings score 100% — every element is the right shape at the right place, verified by pure arithmetic. The remaining 3% are known BOM relationship issues (mirror dimensions, MEP expansion), not test bugs.


## No AI inside
Not just freedom from slow human visual inspection, rigged with human errors. We are not using AI. No tolerances to tune. Just mathematics.

No call home to an alien AI API or embedded as a booby trap. Every proof is deterministic — same input, same result, every time. You can download it, disconnect from the Internet, point it at any IFC file or produce it via the BONSAI Designer from thin air, and it will tell you exactly what is right and what is wrong. No cloud. No model. No magic. No surprise. No backdoor. No trap. Pure arithmetic.

See [EYES_SRS.md](docs/EYES_SRS.md) for the full specification.

## Design in Blender BIM (BONSAI), Compile from BOM

The compiler is not a command-line-only tool. It powers a live GUI inside [Blender](https://www.blender.org/) via the [Bonsai](https://bonsaibim.org/) addon. A Java Design Server (TCP :9876) handles all BIM logic; Blender is just the viewport and interaction layer. Thin Python client, fat Java brain.

What you can do today: create a building from a single click ("Create New" with defaults → valid 3D building in under 200ms), edit room dimensions with sliders, switch jurisdictions (Malaysia UBBL, Singapore BCA), save/recall design versions, and promote a design to a construction-ready work order. The same 26 geometric proofs that validate compiled output run in real-time as you place elements — a door that lands outside its wall is flagged before you let go of the mouse.

42 wire protocol actions. 258 passing tests. The full BOM→compile→output pipeline runs behind the GUI — what the user sees IS what the compiler produces, deterministically.

See [BIM_Designer_SRS.md](docs/BIM_Designer_SRS.md) for UX requirements and [BIM_Designer_UserGuide.md](docs/BIM_Designer_UserGuide.md) for the walkthrough.

## Other Highlights

- **2D Architectural Drawings** — Floor plans, elevations, sections, and roof plans derived directly from the compiled 3D output. A horizontal section cut at 1.0m above each storey produces architect-grade floor plans with wall outlines, door swings, and window gaps — no manual drafting. SVG output, one drawing sheet per storey. *(Coming soon — [2D_ARCHITECTURAL_LAYOUT.md](2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md))*

- **Reporting Engine (4D–7D)** — The compiled BOM is not just geometry. It is a cost database (5D: elemental cost plans, material takeoffs from BOM lines × product costs), a schedule database (4D: verb execution sequence → Gantt chart, critical path from construction phasing), a sustainability database (6D: carbon footprint per product, embodied energy), and a facility management database (7D: asset registry, maintenance schedules). Six live DAOs, 31 witnesses. *(See [CORE_SRS.md](docs/CORE_SRS.md))*

- **ERP Integration** — The data model IS [iDempiere](https://idempiere.org/) ERP: `M_Product`, `M_BOM`, `C_Order`, `C_DocType`. A compiled building is a manufacturing work order. Promoting a design (`DocStatus DR→CO`) triggers the same document lifecycle that iDempiere uses for purchase orders and invoicing. Portfolio analysis, Kanban boards, and balanced scorecards come free from the ERP pattern — they are just views over `C_Order` data. *(See [ConstructionAsERP.md](docs/ConstructionAsERP.md))*

- **Back Office Server** — HTTP server (:9877) for portfolio management, session handling, and reporting. Browse all projects, view KPIs, run cost/schedule reports. 19 passing tests. *(See [BACK_OFFICE_SRS.md](docs/BACK_OFFICE_SRS.md))*

## Key Numbers

| Metric | Value |
|--------|-------|
| **Test suites** | 392 Designer + 5 BackOffice + AddDisciplineTest — all GREEN |
| **Buildings compiled** | 35 (34 extracted + 1 generative). 19 ALL GREEN |
| **BIM COBOL verbs** | 64 verbs, 205+ witnesses, 5 tiers (L0–L4) |
| **Compilation pipeline** | 9 stages, 6 mathematical gates (G1–G6) + composition gate (G7) |
| **Products** | 2,475 in catalog, 44K geometries, 25 IFC classes |
| **Mined validation rules** | 415 dimension rules from 20 buildings — IFC quality gate |
| **Largest building** | Terminal (48,428 elements, 505 products, 8 disciplines) |
| **Databases** | 4-DB architecture: component_library, disc_validation, per-building BOM, output |
| **Java source** | 740+ files across 10 Maven modules |
| **Specifications** | 53 docs, 4 tiers, governed by [SystemContract.md](docs/SystemContract.md) |

## Project Overview

Open [`project_overview.html`](project_overview.html) in any browser — a standalone, zero-dependency dashboard with all 34 buildings, the 9-stage pipeline, Rosetta Stone gate results, component library stats, and BOM output structure. Click **"Captions"** at the bottom-right for a guided walkthrough. Can be distributed and run anywhere without a server.

## 4-Database Architecture

| Database | Role | Mutability |
|----------|------|------------|
| `component_library.db` | LOD catalog: product geometry, meshes, materials (21 tables) | Read-only |
| `disc_validation.db` | Discipline metadata + 415 mined dimension rules (22 tables) | Read-only (migration-seeded) |
| `{PREFIX}_BOM.db` | Per-building BOM dictionary (one per building: SH, DX, TE, etc.) | Read-only at compile time |
| `output.db` | Compiled output: elements, spatial structure, IFC properties | Written fresh each compile |

## Project Structure

```
bim-compiler/
├── orm-core/              # Base ORM, BIMLogger, shared utilities
├── ORMSandbox/            # DAO smoke tests, BuildingInspector
├── DAGCompiler/           # 9-stage compilation pipeline (G1-G6 gates)
├── 2D_Layout/             # Floor plan generation
├── TopologyMaker/         # Grid strategy, production order lifecycle
├── BIM_COBOL/             # 63 domain verbs, witness engine
├── BIMEyes/               # Geometric comprehension: shape proofs, fingerprints, diff
├── IFCtoBOM/              # IFC extraction → BOM database pipeline
├── BIMBackOffice/         # ERP reporting, sessions, portfolio (HTTP :9877)
├── BonsaiBIMDesigner/     # GUI server, validation, assembly (TCP :9876)
├── library/               # SQLite databases (product catalog, BOMs)
├── database/              # Schema docs, interactive ERD viz
├── migration/             # SQL migration scripts (append-only)
├── scripts/               # Build, test, and audit shell scripts
└── docs/                  # Specifications and analysis documents
```

## Build & Run

```bash
# Prerequisites: Java 17+, Maven 3.8+, SQLite3
mvn compile -q                          # Compile all 9 modules
./scripts/run_tests.sh                  # Full test gate

# Servers
mvn exec:java -pl BonsaiBIMDesigner \
    -Dexec.mainClass="com.bim.designer.api.DesignerServer" \
    -Dexec.args="library 9876" -q       # Designer server (TCP, for Blender)

# Documentation site (auto-installs mkdocs on first run)
./scripts/serve_docs.sh                 # Browse: http://localhost:8000

# Database browser (optional)
pip install datasette
datasette library/*.db --port 8001      # Browse: http://localhost:8001
```

## Pipeline — How Buildings Flow Through the System

```
IFC file → extract → classify_XX.yaml → IFCtoBOM → {XX}_BOM.db → compile → output.db → gates
           (once)     (human intent)     (once)     (recipe)      (repeat)  (elements)   (proof)
```

**Three phases, two scripts:**

| Phase | What | Script | Run when |
|-------|------|--------|----------|
| **Onboard** | IFC → extraction → YAML → BOM | `./scripts/onboard_ifc.sh` | Once per new IFC file |
| **Compile** | BOM + library → output elements | `./scripts/run_RosettaStones.sh` | After any code change |
| **Validate** | G1-G6 gates + C8/C9 fidelity | (included in above) | Automatic |

```bash
# Onboard a new building (one-time)
./scripts/onboard_ifc.sh --prefix SC --type Schependomlaan \
    --name "Schependomlaan Residential" --base RE \
    --ifc DAGCompiler/lib/input/IFC/Schependomlaan_IFC2x3.ifc

# Run pipeline for one building (repeatable)
./scripts/run_RosettaStones.sh classify_sh.yaml

# Run pipeline for all buildings
./scripts/run_RosettaStones.sh
```

**Detailed walkthrough:** [WorkOrderGuide.md](docs/WorkOrderGuide.md) — step-by-step pipeline with code links.
**Onboarding recipe:** [IFC_ONBOARDING_RUNBOOK.md](docs/IFC_ONBOARDING_RUNBOOK.md) — 8-step self-service guide.

## Documentation

Browse the full documentation site locally:

```bash
./scripts/serve_docs.sh                 # http://localhost:8000
```

50 specs organized by audience: **Start Here** | **Architecture** | **Compiler** | **Designer** | **Disciplines** | **Enterprise** | **Roadmap** | **Buildings** | **Guides** — with full-text search, dark mode, and sidebar navigation.

Interactive database ERD: [`database/bim_architecture_viz.html`](database/bim_architecture_viz.html) — open in any browser.

## Blender + Bonsai Integration

The BIM Designer GUI runs inside [Blender](https://www.blender.org/) via the
[Bonsai](https://bonsaibim.org/) addon. The Federation module (IFC spatial database,
clash detection, MEP routing, 4D/5D) lives in a separate fork:

```bash
git clone -b feature/IFC4_DB git@github.com:red1oon/IfcOpenShell.git ~/IfcOpenShell
```

Blender connects to the Java Design Server (TCP :9876) via ndjson wire protocol —
thin Python client, all BIM logic stays in Java. The Federation module handles
multi-model coordination, spatial queries, and visualization entirely within Blender.

Full setup: [Systems Installer Guide §6](docs/SYSTEMS_INSTALLER_GUIDE.md).

## About the Creator

**Redhuan D. Oon** ([red1](mailto:red1org@gmail.com)) — Kuala Lumpur, Malaysia. Led [ADempiere](https://www.adempierebr.com/User:Red1) (2006), paved the way for [iDempiere](https://idempiere.org/) (2010), authored *[Open Source ERP](https://www.amazon.com/Open-Source-ERP-Redhuan-Oon/dp/9673490228)* (Pearson Malaysia, 2010). Two decades of ERP manufacturing BOM expertise applied to construction.

## License

- **Code:** GPL v2 (compatible with iDempiere/Bonsai FOSS ecosystem)
- **Documentation:** Creative Commons Attribution-ShareAlike 4.0 (CC BY-SA 4.0)

Copyright (c) 2026 Redhuan D. Oon. All rights reserved.
