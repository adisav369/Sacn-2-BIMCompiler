# BIM Intent Compiler

**Construction is manufacturing.** A building is a product. Its Bill of Materials
IS the building. This compiler reads BOM data and produces 3D coordinates — the
same thing an ERP system does when it explodes a manufacturing BOM into work orders.

## Killer Concepts

**1. BOM = Building.** Every wall, door, pipe, and cabinet is an M_BOM_Line with
dx/dy/dz position. The hierarchy UNIT → FLOOR → ROOM → SET → ITEM maps directly
to building → storey → room → furniture group → leaf product.

**2. iDempiere ERP data model.** M_Product, M_BOM, C_Order, C_DocType,
CO_EmptySpace, PP_Order_Node, C_Campaign — all iDempiere tables repurposed for
construction. Adding a new building type = adding BOM data, zero Java code.

**3. Verb-driven mutation.** The GUI emits [BIM COBOL](docs/BIM_COBOL.md) verbs
(`CREATE ROOM`, `FURNISH ROOM`, `RESIZE ROOM`), never direct SQL. 63 verbs in 5
tiers with layered composition. EntityType (D/U/A) guards prevent mutation of
dictionary data at the PO layer.

**4. Rosetta Stone verification.** Real IFC buildings are extracted, committed as
BOM data, and reproduced deterministically. 6 mathematical gates (count, volume,
SHA256 digest, tamper, provenance, isolation) prove the output without visual
inspection.

**5. Three BOM dimensions.** Category (WHAT: kitchen, bedroom, structural) ×
Owner (WHICH variant: SH, DX, TB) × SpaceSize (HOW MUCH: AABB in mm). Selection
cascade: AABB fit → largest volume → seq_no tiebreaker.

## Current State (2026-03-09)

| Metric | Value |
|--------|-------|
| **Rosetta Stone Gates** | 6/6 GREEN for SH (55 elements) and DX (1099 elements) |
| **BIM COBOL** | 63 verbs, 196 witnesses |
| **Pipeline** | 9 stages: Metadata → Parse → Compile → Template → Write → Verb → Digest → Geometry → Prove |
| **Buildings** | SH (55), DX (1099), TB-LKTN (139 generative), Terminal (51K extracted), ST_SH (123) |

## Project Structure

```
bim-compiler/
├── DAGCompiler/       ← Main compiler (parser, BOM walker, writers)
├── BIM_COBOL/         ← Verb language (63 verbs, VerbRegistry, VerbExecutor SPI)
├── ORMSandbox/        ← iDempiere PO layer + BuildingInspector
├── TopologyMaker/     ← Generative building pipeline
├── orm-core/          ← BasePO + ModelQuery framework
├── library/
│   ├── SH_BOM.db             ← Sample House BOM dictionary (M_BOM, M_Product, C_DocType)
│   ├── DX_BOM.db             ← Duplex BOM dictionary
│   ├── TE_BOM.db             ← Terminal BOM dictionary (planned)
│   └── component_library.db  ← Geometry: LOD meshes, product images (Git LFS)
├── migration/         ← SQL migration scripts
└── docs/              ← See Documentation below
```

## Build & Test

```bash
# Prerequisites: Java 17+, Maven 3.8+, SQLite3
mvn compile -q
./scripts/run_tests.sh              # Full gate
cd BIM_COBOL && mvn test            # BIM COBOL verbs only
cd BIM_COBOL && mvn test -Dtest=ConvenienceVerbTest  # L1 convenience verbs
```

## Documentation

| Document | What it covers |
|----------|---------------|
| [BOMBasedCompilation.md](docs/BOMBasedCompilation.md) | **Start here** — why BOM metadata solves construction |
| [ConstructionAsERP.md](docs/ConstructionAsERP.md) | 3-DB architecture, C_Order/M_BOM/CO model, §11 design decisions |
| [BIMasBOMConcept.md](docs/BIMasBOMConcept.md) | 3 BOM dimensions, buffer space, iDempiere ERD |
| [BIM_COBOL.md](docs/BIM_COBOL.md) | Language spec v0.13, 38 verbs, verb grammar, §18 synthetic BOM |
| [PREFAB_ARCHITECTURE.md](docs/PREFAB_ARCHITECTURE.md) | Assembly hierarchy, MRP BOM Drop |
| [DATA_MODEL.md](docs/DATA_MODEL.md) | 3-DB schema reference |
| [DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) | Pipeline, build, DAO pattern, verb-first discipline, EntityType |
| [ACTION_ROADMAP.md](docs/ACTION_ROADMAP.md) | 9 phases (0–H), 4 tracks, dependency graph |
| [PROGRESS.md](PROGRESS.md) | Current state, gates, what's next |

## Roadmap

**Phase 0–A COMPLETE.** EN-BLOC compilation + 6 gates GREEN. BOM walk proven.

| Phase | Goal | Status |
|-------|------|--------|
| 0–A | EN-BLOC singularity + gate convergence | **DONE** |
| F0.2 | Synthetic BOM verbs (P0 primitives + L1 convenience + EntityType) | **DONE** |
| H0 | ERP dimensions (C_Campaign, C_BPartner, AD_User) + ReportEngine POC | Next |
| B | Terminal BOM recomposition (51K elements) | Planned |
| C | 2D drawing export (3D → SVG) | Planned |
| D | Synthetic Rosetta Stone (3D → 2D → 3D round-trip proof) | Planned |
| F | BIM COBOL v1.0 — zero Java assembler | Planned |
| G | Bonsai GUI editor | Planned |
| H | iDempiere ERP integration | Planned |

Full roadmap with dependency graph: [`docs/ACTION_ROADMAP.md`](docs/ACTION_ROADMAP.md)

## License

MIT
