# BIM Compiler - Project Structure

## Directory Layout

```
bim-compiler/
├── src/main/java/com/bim/compiler/
│   ├── cli/              # Command-line interfaces
│   │   └── BuildingCompilerCLI.java (generic DSL processor)
│   ├── contract/         # Layer 0-5 interface hierarchy (Phase 2-5)
│   ├── db/               # Database writers (federated schema)
│   ├── dsl/              # DSL parser & compiler (core engine)
│   ├── export/           # IFC/BOM exporters
│   ├── factory/          # Component factories (LOD400 library)
│   ├── geometry/         # Geometric primitives
│   ├── library/          # MEP/structural placers
│   ├── model/            # BIM element models
│   ├── topology/         # Spatial relationships
│   ├── validation/       # Validation rules
│   └── witness/          # Witness system (proof generation)
│
├── tests/
│   ├── canonical/        # Regression anchor tests (3 files)
│   └── archive/          # Historical development tests
│
├── examples/             # DSL example buildings (17 files)
│
├── config/               # Configuration YAML files
│   ├── spacetypes.yaml   # Space type definitions
│   └── profiles/         # Profile configurations
│
├── docs/                 # Documentation
│   ├── FOSS_DEVELOPER_GUIDE.md
│   ├── USER_GUIDE.md
│   ├── bim-dsl-dictionary.md
│   └── archive/          # Old documentation
│
├── scripts/              # Python utility scripts
│   ├── export_to_gltf.py        # DB → glTF converter
│   ├── export_2d_drawings.py    # DB → SVG floor plans
│   └── export_building_to_ifc.py
│
├── viewer/               # Web-based 3D viewer (model-viewer)
│
├── output/               # Build artifacts (*.db, *.glb, drawings/)
│
└── tools/                # Auxiliary tools
    └── sanity-checker/   # Post-compilation validators
```

## Key Entry Points

### For Users
- **Generic CLI**: `BuildingCompilerCLI` - compile any DSL file
- **Canonical Tests**: See `tests/canonical/` for examples

### For Developers
- **Core Engine**: `BuildingCompiler.java` - main compilation pipeline
- **Schema Writer**: `BuildingWriter.java` - federated DB output
- **Witness System**: `WitnessBuilder.java` - proof generation
- **Contract Hierarchy**: `src/main/java/com/bim/compiler/contract/` - Layer 0-5 interfaces

## Data Flow

```
DSL Input (.bim)
    ↓
BuildingParser → BuildingDefinition
    ↓
BuildingCompiler → BuildingSpec
    ↓
BuildingWriter → Federated DB (.db)
    ↓
    ├→ Bonsai (Blender) - Full 3D visualization
    ├→ export_to_gltf.py - Web viewer
    └→ export_2d_drawings.py - Floor plans/sections
```

## Witness System

25 claims prove correctness:
- Layer 0: Geometry validation (mesh topology)
- Layer 1-5: Contract compliance (future)
- Domain: Building codes (fire egress, daylight, etc.)

See `WitnessBuilder.java` for claim catalog.
