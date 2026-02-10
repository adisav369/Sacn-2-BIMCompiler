# BIM Compiler

A DSL compiler that transforms declarative building descriptions into validated BIM models with LOD400 geometry, MEP systems, structural elements, and mathematical correctness proofs.

## What It Does

Write a `.bim` file describing a building. The compiler produces a SQLite database containing every element — walls, doors, windows, columns, beams, plumbing, electrical, sprinklers, furniture — with real LOD400 geometry extracted from reference IFC models. No parametric boxes. No invented dimensions.

```
DSL text  →  Parser  →  Compiler  →  SQLite DB  →  IFC / glTF / Blender
```

**6 building types compile today:** 18-storey condo, 2-storey school, single-storey house, 2-storey house, compact house, stacked duplex.

## Quick Start

```bash
# Prerequisites: Java 17+, Maven 3.8+
mvn compile -q

# Compile a condo (18 storeys, ~10,500 elements)
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.CondoMidEndToEndTest" -q

# Compile a school
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.SchoolEndToEndTest" -q

# Query the output
sqlite3 output/condo_mid.db "SELECT COUNT(*) FROM elements_meta;"
```

## DSL Example

```bim
BUILDING "Sekolah_Kebangsaan" {
    GRID {
        axes: A, B, C, D, E, F / 1, 2, 3, 4, 5, 6, 7
        spacing: 4, 8, 8, 8, 4 / 7, 3, 7, 4, 6, 6
    }
    SCHEDULE doors { D1: 900x2100 "Classroom door" }
    STOREY "Ground" level:0 height:3.2m {
        CLASSROOM "class_1" bounds:B1-C2 {
            exterior: north
            opens_to: koridor
            DOOR type:D1 wall:south
            WINDOW type:W1 wall:north
        }
        CORRIDOR "koridor" bounds:A2-F3 { exterior: west }
    }
    ROOF pitch:15deg overhang:900mm
}
```

## Architecture

**DSL selects. Metadata parameterises. Java resolves.**

The compiler reads construction knowledge from a metadata database (`library/component_library.db`) — not from hardcoded Java. Adding a new building variant means SQL INSERT, not code changes.

| Layer | What | Where |
|-------|------|-------|
| DSL | Building type + overrides | `examples/*.bim` |
| Metadata | 41 AD tables, 20 BOM recipes, 8,763 LOD400 components | `library/component_library.db` |
| Java | Parse → Resolve → Compile → Place → Write | `src/main/java/com/bim/compiler/` |
| Output | SQLite with geometry, MEP graphs, spatial structure | `output/*.db` |

## Witness System

Every build produces a `*_witness.json` with mathematical proofs:

- Foundation grounded at Z=0
- All rooms reachable from entry
- All windows on exterior walls
- Roof covers building footprint
- Sprinkler coverage per NFPA 13
- Structural grid completeness
- MEP system connectivity

24 claims, verified by the sanity checker (23/33 checks pass on condo).

## Current State (Phase 115B)

- **6 E2E tests** passing (condo, school, 4 house variants)
- **8,763 LOD400 components** extracted from real IFC models
- **20 active BOM recipes** with 82 children and 214 spatial parameters
- **41 AD tables** encoding construction standards (UBBL, IBC, NFPA 13, IPC)
- **MANIFEST face contracts** for assembly clearances (IPC 405.3.1)
- **World-space geometry** (Pattern B) — zero transforms, no coordinate confusion

## Documentation

| Document | Audience | Content |
|----------|----------|---------|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Architects & leads | Theory, AD/BOM patterns, correctness framework, conventions |
| [DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) | Developers | DAG pipeline, key files, BOM recipes, metadata schema, traps |
| [USER_GUIDE.md](docs/USER_GUIDE.md) | End users | DSL syntax, room types, build commands |
| [PREFAB_ARCHITECTURE.md](docs/PREFAB_ARCHITECTURE.md) | Assembly designers | Prefab hierarchy, MANIFEST contracts, room slots |
| [witness-system-specification.md](docs/witness-system-specification.md) | QA & verification | Witness claims, proof structures, verification protocol |

## License

MIT

## Related

- [IfcOpenShell](https://ifcopenshell.org/) — IFC geometry engine
- [IFC4 Schema](https://standards.buildingsmart.org/IFC/RELEASE/IFC4/ADD2_TC1/HTML/) — IFC4 reference
