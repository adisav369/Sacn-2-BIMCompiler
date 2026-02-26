# BIM Intent Compiler

A compiler that transforms declarative building descriptions into spatially validated BIM models. It maps the construction domain onto an ERP data model (iDempiere C_Order / M_BOM / CO warehouse allocation) and proves correctness via coordinate maths, not visual inspection.

## How It Works

Buildings are treated as manufacturing work orders. A building registry (C_Order) references BOM assemblies (M_BOM) which recursively expand into placed elements. The compiler resolves relational placement rules — wall fractions, room fractions, BOM child offsets — into world coordinates and writes a SQLite output database.

```
BOM.db                          component_library.db    Output DB
(config + rules + BOM)          (LOD geometry)          (compiled result)
──────────────────────          ────────────────────    ──────────────
ad_product_dim (57)             lod_geometry_map        elements_meta
ad_element_rule (1263)          lod_element_placement   elements_rtree
ad_room_boundary (60)           component_geometries    co_empty_space
ad_wall_face, ad_building_grid  lod_parametric_mesh     base_geometries
m_bom (50), m_bom_line (201)                            spatial_structure
m_attribute (425), M_BomCategory (14)
```

### Pipeline

```
DSL manifest
  → BuildingParser → BuildingCompiler → StoreyCompiler
                                            ↓
                         RelationalResolver (wall fractions, room fractions)
                         BOMTierResolver (recursive BOM tree expansion)
                                            ↓
                         BuildingWriter → ElementPersistence → Output DB
                                            ↓
                         PlacementProver (IsAvailable quality gate: IP→CO or IP→RE)
```

### Two Placement Paths

| Path | Buildings | Method | Status |
|------|-----------|--------|--------|
| **Relational** | SH (43 rules), DX (1026), TB (64) | Computes from C_OrderLine + room boundaries + wall faces | Active |
| **Legacy flat** | Terminal (51,088 rows) | Reads pre-extracted coordinates from ad_element_placement | Pending RM-5b migration |

The relational path is the target. SH and DX prove that a real IFC building can be decomposed into relational rules and recomposed to match the reference within 50mm.

### BOM Assembly (6 Levels)

```
UNIT (UN)  →  FLOOR (L1/L2)  →  ROOM (LI/BD/KT)  →  SET (FR)  →  ITEM  →  PORT
```

Each level is an `m_bom` record. Children carry dx/dy/dz offsets (metres), rotation rules, and SpaceSize (AABB in mm). 14 functional categories (LI/BD/KT/BT/DN/FR/ST/L1/L2/UN/WL/PH/RF/SL), 4 building owners (SH/DX/TB/TE).

## Current State (2026-02-26)

**Test gate: 199 PASS / 1 RED / 1 SKIP**

| Building | Elements | Positional Fidelity | LOD400 Mesh | Placement Path |
|----------|----------|--------------------:|------------:|----------------|
| SampleHouse | 56 | 100% <50mm | 98% (55/56) | Relational |
| Duplex | 1,089 | 100% <50mm | 6% (66/1089) | Relational |
| Terminal | 51,088 | tautological | — | Legacy flat copy |
| TB-LKTN | 139 (target) | — | — | Generative (pending) |

**What is mathematically proven:**
- SH/DX element centroids match IFC reference within 50mm (G8 test)
- DX L2 furniture at Z=3000mm from BOM storey offset (F1 test)
- BOM anchor dimensions match room boundaries within 1mm (C2/C3 tests)
- No hardcoded coordinates, no building-specific branching in pipeline code
- CO_EmptySpace pipeline: SH 4 lines, DX 6 lines, quality gate completes

**Known gaps:**
- DX: 94% of elements use generated box geometry (correct positions, no real mesh)
- Terminal: not yet on relational path (flat coordinate copy)
- TB-LKTN: generative placement from intent only — the real test — not yet done
- 40/51 DX rooms have NULL boundaries (G8-DX intentionally RED)

## Direction

The project is migrating from *extracted reference replay* to *generative intent compilation*:

1. **Done** — Decompose real IFC buildings into relational rules, prove recomposition (SH/DX)
2. **Done** — 3-DB split, BOM dimension model, CO_EmptySpace pipeline
3. **Next** — TB-LKTN generative path (Phase RM-4): place elements from UBBL rules + typology templates, no IFC reference
4. **Next** — LOD400 geometry dispatch for DX non-furniture (replace GEN-BOX with library mesh)
5. **Future** — Terminal relational migration (Phase RM-5b), parametric mesh compiler dispatch

## Project Structure

```
bim-compiler/
├── DAGCompiler/           ← Main compiler (parser, resolvers, writers, 138 tests)
├── ORMSandbox/            ← iDempiere PO layer + BuildingInspector (21 tests)
├── TopologyMaker/         ← Generative building pipeline (15 tests)
├── orm-core/              ← BasePO + ModelQuery framework
├── library/
│   ├── BOM.db                ← ~73 tables: ad_* config + m_* BOM (50 BOMs, 201 lines, 14 categories)
│   └── component_library.db  ← ~12 tables: lod_* geometry (8766 geometries, 127MB, Git LFS)
├── tools/                 ← extract.py, spatial_checker.py
├── migration/             ← SQL scripts (idempotent)
└── docs/                  ← See below
```

## Build & Test

```bash
# Prerequisites: Java 17+, Maven 3.8+, SQLite3
mvn compile -q
./scripts/run_tests.sh          # Full gate: compile + build SH/DX + 199 tests
./scripts/run_tests.sh dag      # DAGCompiler only
./scripts/run_tests.sh orm      # ORMSandbox only
./scripts/run_tests.sh topology # TopologyMaker only
```

## Documentation

| Document | Content |
|----------|---------|
| [ConstructionAsERP.md](docs/ConstructionAsERP.md) | 3-DB architecture, C_Order/M_BOM/CO model |
| [BIMasBOMConcept.md](docs/BIMasBOMConcept.md) | BOM 3 dimensions (Category + Owner + SpaceSize) |
| [PREFAB_ARCHITECTURE.md](docs/PREFAB_ARCHITECTURE.md) | 6-level assembly hierarchy, MRP BOM Drop |
| [TheRosettaStoneStrategy.txt](docs/TheRosettaStoneStrategy.txt) | 3-stone validation methodology |
| [RELATIONAL_PLACEMENT_SPEC.md](docs/RELATIONAL_PLACEMENT_SPEC.md) | Flat → relational migration spec |
| [DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) | Pipeline, key files, DAO pattern |
| [VIEW_CONTRACTS.md](docs/VIEW_CONTRACTS.md) | View layer contracts, coordinate frames |
| [PROGRESS.md](PROGRESS.md) | Session log, test scores, next steps |
| [AUDIT_REPORT_20260225.txt](docs/archive/AUDIT_REPORT_20260225.txt) | Systems audit with geometry proofs (archived) |

## License

MIT
