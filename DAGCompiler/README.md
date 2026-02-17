# DAGCompiler

The compiler module of the BIM Compiler project. Contains all Java source code, reference data, and output databases.

## Layout

```
DAGCompiler/
├── src/main/java/com/bim/compiler/
│   ├── dsl/                         ← Core compiler pipeline
│   │   ├── BuildingParser.java          Parse .bim DSL text
│   │   ├── BuildingCompiler.java        Entry points, validation
│   │   ├── StoreyCompiler.java          Per-storey walls, openings, stairs
│   │   ├── MultiUnitCompiler.java       Multi-unit layout, party walls
│   │   ├── BuildingWriter.java          Write orchestrator (schema + output)
│   │   ├── ElementPersistence.java      Element write + material columns
│   │   ├── PlacementAD.java             Placement metadata reader (positions + materials)
│   │   ├── BuildingSpecs.java           26 record types (RoomSpec, WallSpec, SlabSpec, etc.)
│   │   ├── MEPWriter.java               MEP/fixture writer
│   │   ├── OpeningWriter.java           Door/window writer
│   │   ├── StructuralWriter.java        Column/beam writer
│   │   ├── StairWriter.java             Stair writer
│   │   └── *EndToEndTest.java           E2E tests for Rosetta Stones
│   ├── library/                     ← Component library resolvers
│   │   ├── ComponentLibrary.java        LOD400 component lookup
│   │   ├── FurnitureBOMResolver.java    Room furniture from ad_bom tree
│   │   └── FixturePlacer.java           Bathroom/kitchen fixtures (IPC clearances)
│   ├── geometry/                    ← Geometry primitives
│   │   ├── BoundingBox.java             (minX, maxX, minY, maxY, minZ, maxZ)
│   │   ├── Point3D.java, Vector3D.java
│   │   └── Mesh.java
│   ├── contract/                    ← 6-layer contract interfaces (L0-L5)
│   ├── bom/                         ← BOM assembly builders
│   ├── builder/                     ← WallBuilder, PipeBuilder
│   ├── validation/                  ← Spatial digest, clash detection
│   └── ...
├── lib/
│   ├── input/
│   │   ├── IFC/                            ← IFC source files (ground truth)
│   │   │   ├── Ifc4_SampleHouse.ifc             Stone 1 (2.2M)
│   │   │   ├── Ifc2x3_Duplex_Architecture.ifc   Stone 2 ARC (2.3M)
│   │   │   ├── Ifc2x3_Duplex_MEP.ifc           Stone 2 MEP (18M)
│   │   │   └── Ifc2x3_Duplex_Federated.ifc     Stone 2 merged ARC+MEP (49M)
│   │   ├── Ifc4_SampleHouse_extracted.db    Stone 1 reference (55 elements)
│   │   ├── Ifc2x3_Duplex_extracted.db      Stone 2 reference (1,085 elements)
│   │   └── Terminal_Extracted.db            Stone 3 reference (51,723 elements, from federation)
│   └── output/                      ← Compiled output DBs (generated, not committed)
│       ├── ifc4_sample_house.db
│       ├── ifc2x3_duplex.db
│       └── sjtii_terminal.db
├── python/                          ← Python scripts used by DAGCompiler
│   ├── extractIFCtoDB.py                Unified IFC → reference DB extractor
│   ├── reference_schema.sql             Canonical CREATE for reference DBs
│   ├── output_schema.sql                Canonical CREATE for output DBs
│   ├── spatial_checker.py               X-ray fidelity scoring (output vs reference)
│   └── placement_extractor.py           Extract positions → ad_element_placement
└── pom.xml                          ← Module POM (parent: project root)
```

## Build & Run

All commands run from the project root (`/home/red1/bim-compiler`):

```bash
# Compile everything
mvn compile -q

# Run Rosetta Stone E2E tests (the only 3 that matter)
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.SampleHouseEndToEndTest" -q
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNDuplexEndToEndTest" -q
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.TerminalEndToEndTest" -q
```

Note: `-pl DAGCompiler` is required since the source lives in the DAGCompiler module.

## The Pipeline: IFC → Extracted → Compiled

### Stage 1: Extract (IFC source → Reference DB)

IFC source files are processed by `extractIFCtoDB.py` into SQLite reference DBs containing `elements_meta` (guid, ifc_class, storey, material) and `elements_rtree` (spatial bounding boxes).

| Stone | IFC Source | Reference DB | Elements |
|-------|-----------|-------------|----------|
| SampleHouse | `lib/input/IFC/Ifc4_SampleHouse.ifc` | `lib/input/Ifc4_SampleHouse_extracted.db` | 55 |
| Duplex | `lib/input/IFC/Ifc2x3_Duplex_Federated.ifc` | `lib/input/Ifc2x3_Duplex_extracted.db` | 1,085 |
| Terminal | Federation of 7 IFCs | `lib/input/Terminal_Extracted.db` | 51,723 |

```bash
# Full extraction (geometry + materials in ONE pass)
python3 DAGCompiler/python/extractIFCtoDB.py \
    --ifc DAGCompiler/lib/input/IFC/Ifc4_SampleHouse.ifc \
    -o DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db \
    --exclude IfcOpeningElement,IfcCovering

# Enrich existing reference DB with materials from IFC
python3 DAGCompiler/python/extractIFCtoDB.py --enrich \
    --ifc DAGCompiler/lib/input/IFC/Ifc2x3_Duplex_Architecture.ifc \
    --ref DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db

# Extract placement positions from reference → ad_element_placement
python3 DAGCompiler/python/placement_extractor.py \
    --reference DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db \
    --building-type Ifc4_SampleHouse

# Populate ad_element_placement material columns
python3 DAGCompiler/python/extractIFCtoDB.py --populate-placement \
    --ref DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db \
    --library library/component_library.db \
    --building-type Ifc4_SampleHouse
```

### Stage 2: Metadata (Reference DB → component_library.db)

Extracted positions and materials are loaded into `ad_element_placement` in `library/component_library.db`. Each row represents one element to emit at exact reference coordinates with material attributes.

```sql
-- ad_element_placement columns (key fields)
building_type, storey, ifc_class, ordinal,
minX, maxX, minY, maxY, minZ, maxZ,       -- exact position from reference
orientation, discipline,
material_name, material_rgba               -- from IFC source via extractIFCtoDB
```

### Stage 3: Compile (DSL → Output DB)

The Java compiler reads DSL input, resolves metadata from `component_library.db`, and writes output DBs:

1. `PlacementAD.java` reads `ad_element_placement` — loads positions + materials per building type
2. `StoreyCompiler.applyPlacementOverrides()` — creates per-storey specs from placement data
3. `BuildingWriter.emitGlobalPlacementElements()` — emits non-storey elements (roof, curtain walls)
4. `ElementPersistence.writeElementMeta()` — writes element with all 10 columns including material

### Stage 4: Verify (spatial_checker.py)

```bash
# Positional check (PRIMARY METRIC)
python3 DAGCompiler/python/spatial_checker.py \
    DAGCompiler/lib/output/ifc4_sample_house.db \
    DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db \
    --positional --discipline ARC
```

### Material Colour Format

The `material_rgba` column stores comma-separated RGBA values (0.0-1.0):
- `R,G,B,A` where alpha = 1.0 - IFC_transparency
- Glass example: `0.000,0.502,0.753,0.100` → blue glass, 90% transparent
- Opaque concrete: `0.800,0.800,0.800,1.000` → grey, fully opaque

The extractor reads the IFC material chain:
```
IfcProduct → IfcRelAssociatesMaterial → IfcMaterial (name)
IfcProduct → Representation → IfcStyledItem → IfcSurfaceStyle
           → IfcSurfaceStyleRendering → IfcColourRgb + Transparency (RGBA)
```

## IFC Merge (Duplex)

Duplex has separate ARC and MEP IFC files. Merge with:
```bash
python3 scripts/merge_duplex_ifc.py   # IfcOpenShell append_asset → Federated.ifc
```

Advanced federation (multi-discipline):
```bash
python3 tools/federation_preprocessor.py   # IfcPatch MergeProjects recipe
```

## Current Scores

| Stone | Recall | Precision | F1 |
|-------|--------|-----------|------|
| SampleHouse | **100%** | **100%** | **100%** |
| Duplex | **100%** | **100%** | **100%** |
| Terminal | **~100%** | **100%** | **~100%** |

See [PROGRESS.md](PROGRESS.md) for detailed migration log.
