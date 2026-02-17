# BIM Compiler

A DSL compiler that transforms declarative building descriptions into validated BIM models with LOD400 geometry, MEP systems, structural elements, material/colour fidelity, and mathematical correctness proofs.

## What It Does

Write a `.bim` file describing a building. The compiler produces a SQLite database containing every element — walls, doors, windows, columns, beams, plumbing, electrical, sprinklers, furniture — with real LOD400 geometry extracted from reference IFC models. No parametric boxes. No invented dimensions. Materials and colours (including glass transparency) are carried from the original IFC source through to the output.

```
IFC source  →  Extract  →  Reference DB  →  Placement metadata
                                                    ↓
DSL text  →  Parser  →  Compiler  →  SQLite DB  →  IFC / glTF / Blender
                            ↑                            ↑
                     component_library.db          material_name +
                     (metadata + geometry)          material_rgba
```

## Project Structure

```
bim-compiler/
├── DAGCompiler/                    ← Compiler module (multi-module Maven)
│   ├── src/main/java/com/bim/compiler/
│   │   ├── dsl/                    ← Core compiler (parser, compiler, writers)
│   │   ├── library/                ← Component library resolvers
│   │   ├── geometry/               ← BoundingBox, Point3D, Mesh
│   │   ├── contract/               ← 6-layer contract interfaces
│   │   └── ...                     ← bom/, builder/, validation/, etc.
│   ├── lib/
│   │   ├── input/                  ← IFC sources + extracted reference DBs
│   │   └── output/                 ← Compiled output DBs (generated)
│   ├── tools/                      ← material_extractor.py
│   └── pom.xml                     ← DAGCompiler module POM
├── library/
│   └── component_library.db        ← Metadata + LOD400 geometry (127MB, Git LFS)
├── tools/                          ← spatial_checker.py, extract.py, etc.
├── docs/                           ← Architecture, guides, Rosetta strategy
├── examples/                       ← DSL .bim files
├── migration/                      ← SQL migration scripts
└── pom.xml                         ← Parent POM
```

## Quick Start

```bash
# Prerequisites: Java 17+, Maven 3.8+
mvn compile -q

# Compile SampleHouse (55 elements, ~100% fidelity)
mvn exec:java -pl DAGCompiler \
  -Dexec.mainClass="com.bim.compiler.dsl.SampleHouseEndToEndTest" -q

# Compile Duplex (1,085 elements, ~100% fidelity)
mvn exec:java -pl DAGCompiler \
  -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNDuplexEndToEndTest" -q

# Compile Terminal (51,719 elements, ~100% fidelity)
mvn exec:java -pl DAGCompiler \
  -Dexec.mainClass="com.bim.compiler.dsl.TerminalEndToEndTest" -q

# Verify spatial fidelity against reference
python3 tools/spatial_checker.py \
  DAGCompiler/lib/output/ifc4_sample_house.db \
  DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db \
  --discipline ARC

# Query the output
sqlite3 DAGCompiler/lib/output/ifc4_sample_house.db \
  "SELECT guid, ifc_class, material_name, material_rgba FROM elements_meta LIMIT 10;"
```

## The Pipeline: IFC → Extracted → Compiled

The BIM Compiler follows a three-stage pipeline from IFC source truth to compiled output:

```
STAGE 1: EXTRACT                    STAGE 2: METADATA                  STAGE 3: COMPILE
═══════════════                     ════════════════                    ═══════════════

IFC source files                    component_library.db               DSL .bim file
  ├── Ifc4_SampleHouse.ifc            ├── ad_element_placement            │
  ├── Ifc2x3_Duplex_*.ifc             │   (positions, materials,          ↓
  └── Federation DB (Terminal)         │    orientations)              Parser → Compiler
         │                             ├── ad_bom (assembly recipes)       │
         ↓                             ├── ad_wall_type, ad_space_type     │ reads
    tools/extract.py                   └── ... (41 AD tables)              │ metadata
    tools/material_extractor.py              ↑                             ↓
         │                             placement_extractor.py          BuildingWriter
         ↓                             material_extractor.py               │
    Reference DBs                            │                             ↓
    (DAGCompiler/lib/input/)           Extracted from reference     Output DB
      ├── elements_meta                DBs into ad_element_         (DAGCompiler/lib/output/)
      ├── elements_rtree               placement with exact           ├── elements_meta
      ├── material_name                positions + materials           │   (incl. material_name,
      └── material_rgba                                                │    material_rgba)
                                                                       ├── elements_rtree
                                                                       └── base_geometries
```

**Key principle:** The compiler never invents geometry or materials. Everything is extracted from the IFC source truth, stored as metadata, and read back during compilation. The same Glass transparency (alpha=0.100) in the original IFC appears in the output DB.

## Architecture

**DSL selects. Metadata parameterises. Java resolves.**

The compiler reads construction knowledge from a metadata database (`library/component_library.db`) — not from hardcoded Java. Adding a new building variant means SQL INSERT, not code changes.

| Layer | What | Where |
|-------|------|-------|
| DSL | Building type + overrides | `examples/*.bim` |
| Metadata | 41 AD tables, 20 BOM recipes, 8,763 LOD400 components | `library/component_library.db` |
| Java | Parse → Resolve → Compile → Place → Write | `DAGCompiler/src/main/java/com/bim/compiler/` |
| Output | SQLite with geometry, materials, MEP graphs, spatial structure | `DAGCompiler/lib/output/*.db` |

## Rosetta Stone Fidelity (Phase DE-4 + MAT)

3 reference buildings compiled to ~100% positional fidelity across all disciplines, with material/colour data extracted from IFC sources.

| Stone | Recall | Precision | F1 | Elements | Material Coverage |
|-------|--------|-----------|------|----------|------------------|
| SampleHouse | **100%** (55/55) | **100%** | **100%** | 55 | 55/55 names, 51/55 RGBA |
| Duplex | **100%** (1085/1085) | **100%** | **100%** | 1,085 | 77/1085 names, 124/1085 RGBA |
| Terminal | **~100%** (51719/51723) | **100%** | **~100%** | 51,719 | 41K names, 41K RGBA |

### Material Fidelity

Materials and colours are extracted from IFC source files and carried through the full pipeline:

```sql
-- Glass panels in SampleHouse output: 90% transparent blue glass
SELECT guid, material_name, material_rgba FROM elements_meta
WHERE material_name = 'Glass';
-- MD_PLATE_UNKNOWN_1 | Glass | 0.000,0.502,0.753,0.100
-- (alpha = 0.100 = 90% transparent, matching IFC Transparency: 0.9)
```

## Witness System

Every build produces a `*_witness.json` with mathematical proofs:

- Foundation grounded at Z=0
- All rooms reachable from entry
- All windows on exterior walls
- Roof covers building footprint
- Sprinkler coverage per NFPA 13
- Structural grid completeness
- MEP system connectivity

## Output DB Schema

| Table | Content |
|-------|---------|
| `spatial_structure` | Project → Site → Building → Storey hierarchy |
| `elements_meta` | Every element: guid, ifc_class, name, storey, discipline, **material_name**, **material_rgba** |
| `elements_rtree` | Spatial index: id, minX, maxX, minY, maxY, minZ, maxZ |
| `base_geometries` | Vertices/faces BLOBs (float32/int32 arrays) + hash |
| `assembly_components` | BOM parent-child relationships |
| `mep_systems` / `system_nodes` / `system_edges` | MEP system graph |
| `simple_qto` | Quantity takeoff (area, volume, length) |

## Documentation

| Document | Audience | Content |
|----------|----------|---------|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Architects & leads | Theory, AD/BOM patterns, correctness framework |
| [DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) | Developers | DAG pipeline, key files, BOM recipes, material pipeline |
| [USER_GUIDE.md](docs/USER_GUIDE.md) | End users | DSL syntax, room types, build commands, output queries |
| [PREFAB_ARCHITECTURE.md](docs/PREFAB_ARCHITECTURE.md) | Assembly designers | Prefab hierarchy, MANIFEST contracts, room slots |
| [DAGCompiler/README.md](DAGCompiler/README.md) | Developers | Compiler module: layout, tools, build commands |
| [DAGCompiler/PROGRESS.md](DAGCompiler/PROGRESS.md) | Developers | Migration log, current scores, what's next |

## License

MIT

## Related

- [IfcOpenShell](https://ifcopenshell.org/) — IFC geometry engine
- [IFC4 Schema](https://standards.buildingsmart.org/IFC/RELEASE/IFC4/ADD2_TC1/HTML/) — IFC4 reference
