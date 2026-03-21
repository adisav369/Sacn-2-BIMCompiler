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

No visual inspection. No AI. No tolerances to tune. Just mathematics.

See [EYES_SRS.md](docs/EYES_SRS.md) for the full specification.

## Key Numbers

| Metric | Value |
|--------|-------|
| **Test suites** | 258 Designer + 5 DimRange + 19 BackOffice — all green |
| **Buildings onboarded** | 34 (residential, clinics, castles, airport terminal, infrastructure) |
| **BIM COBOL verbs** | 63 verbs, 196 witnesses, 5 tiers (L0–L4) |
| **Compilation pipeline** | 9 stages, 6 mathematical gates (G1–G6) |
| **Products** | 2,459 in catalog, 44K geometries, 25 IFC classes |
| **Mined validation rules** | 415 dimension rules from 20 buildings — IFC quality gate |
| **Largest building** | Terminal (48,428 elements, 505 products, 8 disciplines) |
| **Databases** | 4-DB architecture: component_library (21 tables), disc_validation (22), per-building BOM (6), output |
| **Java source** | 728 files across 9 Maven modules |

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

# Database browser (optional)
pip install datasette
datasette library/*.db --port 8001      # Browse: http://localhost:8001
```

## Documentation

Start here, in order:

| Document | What |
|----------|------|
| [BOMBasedCompilation.md](docs/BOMBasedCompilation.md) | **Master spec** — tack convention, BOM walker, compilation gospel |
| [SourceCodeGuide.md](docs/SourceCodeGuide.md) | Code navigation, entry points, DAO patterns, module map |
| [BIM_COBOL.md](docs/BIM_COBOL.md) | Verb language: 63 verbs, 5 tiers, grammar, witness engine |
| [ConstructionAsERP.md](docs/ConstructionAsERP.md) | iDempiere mapping, C_Order model, 4-DB architecture |
| [DATABASE_SCHEMA.md](database/DATABASE_SCHEMA.md) | Full table inventory with purpose and Java access |
| [docs/INDEX.md](docs/INDEX.md) | Complete documentation index (39 active docs by tier) |

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

**Redhuan D. Oon** (red1) — Kuala Lumpur, Malaysia. Led [ADempiere](https://www.adempierebr.com/User:Red1) (2006), paved the way for [iDempiere](https://idempiere.org/) (2010), authored *[Open Source ERP](https://www.amazon.com/Open-Source-ERP-Redhuan-Oon/dp/9673490228)* (Pearson Malaysia, 2010). Two decades of ERP manufacturing BOM expertise applied to construction.

## License

- **Code:** GPL v2 (compatible with iDempiere/Bonsai FOSS ecosystem)
- **Documentation:** Creative Commons Attribution-ShareAlike 4.0 (CC BY-SA 4.0)

Copyright (c) 2026 Redhuan D. Oon. All rights reserved.
