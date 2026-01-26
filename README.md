# BIM Compiler

A domain-specific language (DSL) compiler that transforms human-readable building descriptions into valid IFC4 files.

## Overview

BIM Compiler bridges the gap between architectural intent and BIM execution. Write simple, declarative building descriptions and get fully-compliant IFC4 output with:

- **IfcWall** entities with proper geometry
- **IfcSpace** entities with OmniClass Table 13 classification
- **IfcOpeningElement** for doors and windows
- Proper IFC spatial hierarchy (Project → Site → Building → Storey)

## Quick Start

```bash
# Compile a DSL file to IFC
java -cp target/bim-compiler-1.0.jar com.bim.compiler.dsl.BIMCompiler \
    examples/test-house.bim \
    output/test-house.ifc
```

## DSL Syntax

```
STOREY "Ground" height:2.8m {
    BEDROOM "master" at:A1 size:5x4m {
        DOOR south to:corridor
        WINDOW north
        WINDOW east
    }

    BATHROOM "bath1" at:B1 size:3x4m {
        DOOR south to:corridor
        WINDOW north
    }

    CORRIDOR "corridor" at:A2-B2 size:8x1.5m {
        DOOR north to:master
        DOOR north to:bath1
    }
}
```

### Room Types

| Type | OmniClass Code | Description |
|------|----------------|-------------|
| BEDROOM | 13-21 11 00 | Residential sleeping space |
| BATHROOM | 13-21 13 00 | Sanitary facilities |
| KITCHEN | 13-21 15 00 | Food preparation area |
| LIVING_ROOM | 13-21 17 00 | Living/common area |
| CORRIDOR | 13-81 11 00 | Circulation space |
| OFFICE | 13-31 11 00 | Work space |
| STORAGE | 13-55 00 00 | Storage space |

### Grid Positioning

Rooms are placed on a grid using column-row notation:
- `at:A1` - Single cell at column A, row 1
- `at:A2-B2` - Span from A2 to B2 (multi-cell room)

### Openings

- `DOOR <direction> to:<room>` - Door connecting to another room
- `WINDOW <direction>` - Window on exterior wall

Directions: `north`, `south`, `east`, `west`

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    BIM Compiler                          │
├─────────────────────────────────────────────────────────┤
│  DSL Input (.bim)                                       │
│       │                                                 │
│       ▼                                                 │
│  DSLParser → StoreyDefinition + RoomDefinitions         │
│       │                                                 │
│       ▼                                                 │
│  GridLayoutResolver → RoomPolygons (coordinates)        │
│       │                                                 │
│       ▼                                                 │
│  RoomCompiler → WallSpecs + OpeningSpecs                │
│       │                                                 │
│       ▼                                                 │
│  DSLExporter → JSON → Python → IFC4                     │
└─────────────────────────────────────────────────────────┘
```

### Components

| Component | Purpose |
|-----------|---------|
| `DSLParser` | Regex-based parser for BIM DSL syntax |
| `GridLayoutResolver` | Converts grid positions (A1, B2) to metric coordinates |
| `RoomCompiler` | Generates walls with proper thickness and opening placement |
| `DSLExporter` | Java-to-Python bridge for IFC generation |
| `export_dsl_to_ifc.py` | IfcOpenShell-based IFC4 writer |

## Building from Source

### Prerequisites

- Java 17+
- Maven 3.8+
- Python 3.10+
- IfcOpenShell (`pip install ifcopenshell`)

### Build

```bash
mvn clean package
```

### Python Environment

```bash
python3 -m venv venv
source venv/bin/activate
pip install ifcopenshell
```

## IFC Export Features

The compiler produces IFC4 files with:

- **Proper spatial hierarchy**: IfcProject → IfcSite → IfcBuilding → IfcBuildingStorey
- **IfcSpace entities**: Rooms with OmniClass classification in Description
- **IfcWall entities**: Extruded solid geometry with correct placement
- **IfcOpeningElement**: Door/window voids properly linked via IfcRelVoidsElement
- **IfcRelAggregates**: Spaces aggregated to storey (IFC4 compliant)

## Project Status

| Phase | Status | Description |
|-------|--------|-------------|
| Phase 0 | Complete | Core value objects (Point3D, Dimensions, etc.) |
| Phase 1 | Complete | Database population from federated IFC sources |
| Phase 2 | Complete | Pattern extraction from real building data |
| Phase 3 | Complete | WallBuilder with opening placement |
| Phase 4 | Complete | PipeBuilder with connection validation |
| Phase 5 | Complete | IFC Export with round-trip validation |
| Phase 6 | Complete | DSL Parser and Compiler |
| Phase 7 | Complete | Config-driven export architecture |

## Example Output

Compiling `examples/test-house.bim`:

```
Compiled: Ground
  Walls: 12
  Spaces: 3
  Openings: 4
SUCCESS: Exported to output/test-house.ifc
```

Generated IFC contains:
- 3 IfcSpace entities (master, bath1, corridor)
- 12 IfcWall entities forming room boundaries
- 4 IfcOpeningElement for doors
- Proper IfcRelAggregates linking spaces to storey

## License

MIT

## Related

- [IfcOpenShell](https://ifcopenshell.org/) - IFC geometry and file handling
- [IFC4 Documentation](https://standards.buildingsmart.org/IFC/RELEASE/IFC4/ADD2_TC1/HTML/) - IFC4 schema reference
- [OmniClass Table 13](https://www.omniclass.org/) - Space classification codes
