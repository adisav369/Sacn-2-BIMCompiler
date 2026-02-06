# BIM Compiler

A domain-specific language (DSL) compiler that transforms human-readable building descriptions into valid BIM models with MEP systems, structural elements, and automated validation.

## Overview

BIM Compiler bridges the gap between architectural intent and BIM execution. Write declarative building descriptions and get fully-validated output with:

- **Architectural**: Walls, doors, windows, roofs with proper IFC classification
- **Structural**: Columns, beams, lintels placed per construction type
- **MEP Electrical**: Lights, outlets, switches on proper circuits
- **MEP Plumbing**: Fixture placement with vent stacks
- **Fire Protection**: Sprinkler heads with connecting piping network (NFPA 13)
- **Witness System**: 24 automated validation claims proving model correctness

## Example Buildings

### 1. TB-LKTN Residential (Malaysian single-storey house)

```bash
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q
```

### 2. Sekolah Kebangsaan (Malaysian primary school)

```bash
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.BuildingCompiler" \
  -Dexec.args="examples/Sekolah-Kebangsaan.bim output/sekolah_kebangsaan.db" -q
```

The school demonstrates:
- **Masonry construction**: Load-bearing walls carry roof loads; beams only where clear spans exceed wall capacity
- **Double-loaded corridor**: 6 classrooms + 2 toilet blocks + staffroom + office off central corridor
- **Assembly hall**: Column-beam framing for 20m × 12m clear span
- **School-specific witnesses**: Classroom daylight, toilet accessibility, corridor connectivity, fire travel distance

## DSL Syntax

```
BUILDING "Sekolah_Kebangsaan_Bukit_Cermin"
{
    GRID {
        axes: A, B, C, D, E, F / 1, 2, 3, 4, 5, 6, 7
        spacing: 4, 8, 8, 8, 4 / 7, 3, 7, 4, 6, 6
    }

    SCHEDULE doors {
        D1: 900x2100 "Timber panel classroom door"
        D2: 750x2100 "Flush door toilet"
    }

    STOREY "Ground" level:0 height:3.2m {
        CLASSROOM "class_1" bounds:B1-C2 {
            exterior: north
            opens_to: koridor
            DOOR type:D1 wall:south
            WINDOW type:W1 wall:north
            WINDOW type:W1 wall:north
            WINDOW type:W1 wall:north
        }

        CORRIDOR "koridor" bounds:A2-F3 {
            exterior: west
            exterior: east
            DOOR type:D1 wall:west
            DOOR type:D1 wall:east
        }

        ASSEMBLY_HALL "dewan" bounds:C5-F7 {
            exterior: south
            exterior: east
            exterior: north
            structural_grid: true
            beam_max_span: 8.0
        }
    }

    ROOF pitch:15deg overhang:900mm
}
```

### Space Types

| Type | Category | Description |
|------|----------|-------------|
| BEDROOM | Residential | Sleeping space |
| BATHROOM | Residential | Sanitary facilities |
| KITCHEN | Residential | Food preparation |
| LIVING_ROOM | Residential | Common area |
| CLASSROOM | Institutional | Teaching space (requires daylight) |
| ASSEMBLY_HALL | Institutional | Large clear-span gathering space |
| CORRIDOR | Circulation | Linear egress path |
| TOILET_BLOCK | Service | Clustered sanitary facilities |

## Witness System

Every compiled building produces a witness file (`*_witness.json`) with 24 validation claims:

| # | Claim | Validates |
|---|-------|-----------|
| 1 | ROOMS_ENCLOSED | All rooms have complete wall boundaries |
| 2 | ROOMS_IN_ENVELOPE | All rooms within building footprint |
| 3 | ROOF_COVERS_ALL | Roof projection covers all spaces |
| 4 | DOORS_REACHABLE | All doors accessible from room interior |
| 5 | WINDOWS_ON_EXTERIOR | Windows only on exterior walls |
| 6-8 | PLUMBING_* | Waste/vent/supply connectivity (institutional: SKIPPED) |
| 9 | ALL_OUTLETS_ON_CIRCUIT | Every outlet assigned to a circuit |
| 10 | MEP_NO_STRUCTURAL_CLASH | MEP elements clear of structural members |
| ... | ... | ... |
| 20 | CLASSROOM_DAYLIGHT | Classrooms have windows for natural light |
| 21 | TOILET_ACCESSIBLE | Toilet doors meet accessibility width (≥900mm) |
| 22 | CORRIDOR_CONNECTS_ALL | Corridor provides access to all teaching spaces |
| 23 | FIRE_TRAVEL_DISTANCE | Max travel to exit within code limits |
| 24 | STRUCTURAL_GRID_COMPLETE | Columns/beams exist with correct IFC classes |

```bash
# Check witness summary
python3 -c "import json; d=json.load(open('output/sekolah_kebangsaan_witness.json')); print(d['summary'])"
# Output: {'proven': 18, 'skipped': 6, 'unprovable': 0}
```

## Structural Model

The compiler supports two construction systems:

**FRAMED** (residential): Columns at corners and T-junctions, lintels over openings.

**MASONRY** (institutional): Load-bearing walls are the primary structure. Column-beam framing placed only where program demands clear spans exceeding wall capacity (e.g., assembly halls).

### Known Limitation: Beam Span Subdivision

Grid beams in large clear-span spaces currently span the full room dimension rather than subdividing at intermediate column positions. A 20m assembly hall produces a single 20m beam (structurally, this would require ~1.5m depth — a transfer girder, not a beam).

The `STRUCTURAL_GRID_COMPLETE` witness validates element existence and IFC classification. Beam-column connectivity and span limit checking (`BEAM_SPAN_LIMIT`) are roadmap items for Phase 51.

## Building from Source

### Prerequisites

- Java 17+
- Maven 3.8+
- Python 3.10+ with IfcOpenShell (`pip install ifcopenshell`)

### Build and Test

```bash
mvn clean package -q

# Run residential test
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q

# Run school compilation
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.BuildingCompiler" \
  -Dexec.args="examples/Sekolah-Kebangsaan.bim output/sekolah_kebangsaan.db" -q
```

### Output Files

| File | Contents |
|------|----------|
| `*.db` | SQLite database with geometry, MEP systems, spatial structure |
| `*_witness.json` | Validation claims with proof data |
| `*_bom.json` | Bill of materials by assembly/component |

## Project Status

| Phase | Status | Description |
|-------|--------|-------------|
| 0-7 | Complete | Core DSL, parser, IFC export |
| 8-22 | Complete | MEP placers (electrical, plumbing) |
| 23-26 | Complete | Structural placer, lintels, TB-LKTN house |
| 27-46 | Complete | Multi-storey, multi-unit, party walls |
| 47-49 | Complete | Stacked units, separating floors |
| 50 | Complete | School building typology |
| 57-62 | Complete | Authority Data, BOM Resolution, LOD400 Library |
| 77-79 | Complete | Sanity checks, witness verification |
| **80** | **Complete** | **Fire Suppression Piping (NFPA 13)** |

### Fire Suppression Piping (Phase 80)

When sprinklers are auto-generated, the compiler now creates connecting pipe networks:

```
RISER (100mm) → MAIN (65mm) → BRANCH (25mm) → SPRINKLER HEAD
```

**NFPA 13 Sizing:** Riser 4", Main 2.5", Branch 1" (Light Hazard)

### Roadmap

- Phase 81: FP system graph connectivity proofs
- Phase 82: HVAC ductwork generation

## Documentation

- [FOSS Developer Guide](docs/FOSS_DEVELOPER_GUIDE.md) — Architecture, data flow, contribution guide
- [DSL Dictionary](docs/bim-dsl-dictionary.md) — Complete DSL syntax reference
- [User Guide](docs/USER_GUIDE.md) — End-user documentation

## License

MIT

## Related

- [IfcOpenShell](https://ifcopenshell.org/) — IFC geometry and file handling
- [IFC4 Documentation](https://standards.buildingsmart.org/IFC/RELEASE/IFC4/ADD2_TC1/HTML/) — IFC4 schema reference
