# Reference IFC Corpus — The Rosetta Stone

> **Note:** The primary Rosetta Stone reference DBs and IFC source files have been
> consolidated into `DAGCompiler/lib/input/`. See `DAGCompiler/README.md` for current layout.
> This directory retains the original IFC corpus for reference.

These IFC files are the **ground truth** for the BIM compiler's metadata grammar.
Every table, column, and row in `component_library.db` must trace back to a real
element in one of these files. The compiler proves itself by reproducing them
from metadata alone.

## Residential (Grammar Baseline)

| File | IFC Version | Elements | Role |
|------|-------------|----------|------|
| `Duplex_Architecture.ifc` | 2x3 | 218 | Walls, doors, windows, furniture, stairs, slabs |
| `Duplex_MEP.ifc` | 2x3 | ~900 | Plumbing, electrical, HVAC piping |
| `SampleHouse.ifc` | 4.0 | ~100 | Single-storey house — simplest grammar test |
| `Ifc4_WallElementedCase.ifc` | 4.0 | ~10 | Wall construction layers — stud/cladding detail |

**Source:** youshengCode IFC samples (open license)

### Duplex Architecture (218 entities)
- 4 storeys (T/FDN, Level 1, Level 2, Roof)
- 21 rooms (2 mirrored units × 10 rooms + 1 roof)
- 57 walls (8 construction types)
- 14 doors (4 size variants)
- 24 windows (6 type/size variants incl. skylight)
- 61 furnishing elements (12 distinct types)
- 2 stairs (each: flight + 2 stringers + 2 railings)
- 21 slabs (6 types incl. finish floors)
- 8 steel beams, 7 footings, 13 ceilings

### Extraction Status
- Stacked_Duplex.db: `database/Stacked_Duplex.db` (1085 elements, queryable)
- SampleHouse.db: Not yet extracted
- Duplex MEP.db: Not yet extracted as separate DB

## Infrastructure (IFC 4.3 Grammar Extension)

| File | Domain | Role |
|------|--------|------|
| `Building-Architecture.ifc` | Architectural | Multi-discipline building |
| `Building-Hvac.ifc` | HVAC | Ductwork, AHU, terminals |
| `Building-Landscaping.ifc` | Landscape | Site elements |
| `Building-Structural.ifc` | Structural | Frame, foundation |
| `Infra-Bridge.ifc` | Civil | Bridge geometry |
| `Infra-Landscaping.ifc` | Civil | Roads + landscape |
| `Infra-Plumbing.ifc` | Civil | Underground services |
| `Infra-Rail.ifc` | Rail | Railway alignment |
| `Infra-Road.ifc` | Road | Road geometry |

**Source:** PCERT IFC 4.3.2.0 (IFC4X3_ADD2) certification samples

## Grammar Extraction Process

1. **Read the IFC** — parse entity types, counts, spatial hierarchy
2. **Map to AD tables** — which metadata table describes each entity class?
3. **Identify gaps** — entity types with no metadata grammar = language deficiency
4. **Fill gaps** — add table/column/row to express what the IFC says
5. **Compile** — DSL → metadata → output DB
6. **Compare** — output geometry vs reference geometry (maths truth)
7. **Iterate** — fix discrepancies until exact match

The grammar is complete when ALL reference entities can be reproduced from
metadata catalog selection alone, with zero hardcoded geometry.

---

## IfcOpenShell / Bonsai Community Alignment

We are a **BIM Compiler** (IFC → DB → BOM → ERP). Bonsai is a **BIM Editor**
(IFC ↔ Blender ↔ IFC). Different goals, but we share the IfcOpenShell stack
and should use its optimizations rather than reinventing them.

### What we use from IfcOpenShell (keep current)

| Feature | Our usage | Community API |
|---------|-----------|---------------|
| Geometry tessellation | `extractIFCtoDB.py` | `geom.iterator()` (v0.8 built-in dedup + instancing) |
| IFC merging | `federation_preprocessor.py` | IfcPatch `MergeProjects` recipe |
| Coordinate modes | `USE_WORLD_COORDS=False` | Local coords for mesh dedup (community best practice) |
| Schema parsing | Element metadata extraction | `by_type()`, `IsDefinedBy`, `ContainedInStructure` |

### Community features to adopt (backlog)

| Feature | What it does | Our benefit | Reference |
|---------|-------------|-------------|-----------|
| **IfcPatch recipes** | Automated IFC pre-cleaning (fix spatial containers, normalize units) | Cleaner input before extraction | `ifcpatch` CLI |
| **IfcPropertyTemplate** | Validate incoming IFC has required properties | Pre-flight check in onboarding script | IFC4 schema |
| **IfcDiff (compare module)** | Detect changes between IFC revisions | Incremental DB update instead of full re-extract | `ifcopenshell.util.diff` |
| **HDF5 geometry cache** | Binary mesh cache for repeated loads | Evaluate vs our SQLite `component_library.db` | Bonsai issue #1785 |
| **Hybrid kernel** | `--kernel hybrid-cgal-simple-opencascade` | Faster tessellation for mesh-heavy IFCs | IfcOpenShell v0.8 |
| **BVH raycasting** | Faster element selection in viewport | Evaluate vs our R-tree | IfcOpenShell issue #4279 |
| **Taxonomy layer** | Detect geometric similarity beyond IfcMappedItem | Further mesh dedup in library | IfcOpenShell v0.8 |

### Where we diverge (by design, not oversight)

| Area | Bonsai approach | Our approach | Why we differ |
|------|----------------|-------------|---------------|
| Data flow | Bidirectional (IFC ↔ edit ↔ save) | One-way compiler (IFC → DB → BOM) | We compile, not edit |
| 4D/5D | Manual entry via IFC schemas | Auto-generated from extracted DB + productivity rates | Speed + Malaysian market rates |
| Scale | Single IFC, ~50K elements max | 28 buildings federated, 1M elements | GN instances + LOD (S165/S170) |
| Mesh storage | HDF5 cache or in .blend | `component_library.db` singleton | BOM separation (WHAT vs WHERE) |
| Schedule | IfcWorkSchedule entities | Construction sequence rules + labour database | Auto vs manual |

### Rules

1. **Check IfcOpenShell API before implementing** — the iterator, not create_shape.
   The community has already solved most geometry performance problems in C++.
2. **Use IfcPatch for pre-processing** — don't write custom IFC fixers.
3. **Watch IfcOpenShell releases** — v0.8 taxonomy, hybrid kernel, BVH are all relevant.
4. **Our value-add is the compiler pipeline** (BOM, ERP, productivity, federation scale)
   — not reimplementing what the geometry kernel already does.
