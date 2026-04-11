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

| Feature | Community API | Our code | What it does for us |
|---------|---------------|----------|---------------------|
| Geometry iterator | `geom.iterator()` (v0.8) | `extractIFCtoDB.py` S172 | Built-in C++ dedup + instancing — tessellate each unique shape once, returns mesh + transform. Replaced per-element `create_shape()` which had no caching. |
| Local coordinates | `USE_WORLD_COORDS=False` | `extractIFCtoDB.py` S168 | Canonical mesh at IFC tack point. Enables mesh dedup across instances — same door shape = same geometry_hash regardless of world position. |
| IFC merging | IfcPatch `MergeProjects` | `federation_preprocessor.py` | Merge discipline IFCs into one. **Caution:** can drop disciplines (Clinic lost entire STR). Prefer DB-level merge (`extract_merge_disciplines.py`). |
| Schema traversal | `by_type()`, `IsDefinedBy` | `extractIFCtoDB.py` | Extract GlobalId, ifc_class, storey, materials, colours, spatial containment, type definitions. |
| Spatial relationships | `ContainedInStructure`, `Decomposes` | `extractIFCtoDB.py` | `rel_contained_in_space` table — which element is in which room/space. Direct STEP pointer, no geometric intersection. |
| Boolean operations | `DISABLE_BOOLEAN_RESULT` setting | `extractIFCtoDB.py` tier 2 | 3-tier fallback: full booleans → skip booleans → bbox placement. Handles complex CSG chains that crash OpenCASCADE. |
| Placement matrix | `shape.transformation.matrix` | `extractIFCtoDB.py` | 4x4 column-major transform (v0.8). Decomposed to Euler XYZ rotation + world position for per-instance placement. |
| GlobalId stability | IFC standard guarantee | `elements_meta.guid` | Primary key across all tables. Same door = same GUID across re-extractions. Enables future delta-update pipeline. |
| Weld vertices | `WELD_VERTICES=True` | `extractIFCtoDB.py` | Merge coincident vertices during tessellation. Reduces mesh size, improves geometry_hash consistency. |

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

### What we built ON Bonsai (the federation module)

The `federation/` module is our contribution inside Bonsai's addon structure. It adds
capabilities that don't exist upstream. Bonsai's native IFC authoring is fully preserved —
our module activates only when the user opens the federation N-panel.

| Capability | What it does | Status (S174) |
|-----------|-------------|---------------|
| **R-Tree spatial index** | GPU bbox preview of entire building, instant load | LIVE — 49K elements in <1s |
| **Library-linked loading** | Pre-baked meshes from `library.blend`, no BLOB unpack | LIVE — 7.5K unique meshes appended in 5s |
| **Per-element objects** | Outliner shows each element by name (`IfcWall \| Ext Wall 200mm`) | LIVE — selectable, inspectable, colored |
| **3-path material chain** | Direct IFC rgba → surface_styles lookup → discipline fallback | LIVE — 41K colored elements (Terminal) |
| **Multi-discipline merge** | DB-level merge with geolocation alignment + grid_north rotation | LIVE — 8 disciplines, unit_scale + rotation fix |
| **Meshless extracted DBs** | Transforms + hashes only, BLOBs in shared library | LIVE — 30MB Hospital vs 232MB with BLOBs |
| **DLOD handler** | Distance LOD via GN instance swap, 500 swaps/frame budget | WIRED — 24ms for 1M elements (self-test) |
| **GN point cloud mode** | Per-discipline GN objects, DLOD-ready, small .blend saves | NEXT (S175) |
| **Thin .blend saves** | Meshes from library.blend, not embedded. Scene saves ~100KB | Via GN mode (DLOD makes it natural) |
| **Clash detection** | Discipline-vs-discipline bbox + geometry clash | LIVE |
| **4D/5D/6D** | Construction schedule, BOQ, cost from compiled BOM | LIVE |

**Key architectural points:**
- Bonsai's IFC authoring (`model`, `project`, `geometry` modules) is untouched
- Our `save_pre`/`load_post` handlers exit immediately on non-federation .blend files
- All operators use `bim.` prefix — no namespace collision
- `library.blend` is shared across all buildings (41K meshes, 118MB)
- Users can author in Bonsai AND view at scale in federation — same addon, same session

### Where we diverge (by design, not oversight)

| Area | Bonsai approach | Our approach | Why we differ |
|------|----------------|-------------|---------------|
| Data flow | Bidirectional (IFC ↔ edit ↔ save) | One-way compiler (IFC → DB → BOM) | We compile, not edit |
| 4D/5D | Manual entry via IFC schemas | Auto-generated from extracted DB + productivity rates | Speed + Malaysian market rates |
| Scale | Single IFC, ~50K elements max | 35 buildings federated, 1M architected | GN instances + DLOD (S165/S170/S174) |
| Mesh storage | HDF5 cache or in .blend | `library.blend` singleton (pre-baked, shared) | BOM separation (WHAT vs WHERE) |
| Mesh loading | `create_shape()` per element | Append from library.blend (<1s for 18K meshes) | 13s → <1s load time |
| Materials in SOLID mode | `diffuse_color` set natively | Must set both `diffuse_color` AND BSDF node | Trap documented in IFCAnalysis.md |
| Multi-discipline | Open one IFC at a time | DB-level merge + geolocation alignment | No data loss, handles grid_north rotation |
| Schedule | IfcWorkSchedule entities | Construction sequence rules + labour database | Auto vs manual |

### Rules

1. **Check IfcOpenShell API before implementing** — the iterator, not create_shape.
   The community has already solved most geometry performance problems in C++.
2. **Use IfcPatch for pre-processing** — don't write custom IFC fixers.
3. **Watch IfcOpenShell releases** — v0.8 taxonomy, hybrid kernel, BVH are all relevant.
4. **Our value-add is the compiler pipeline** (BOM, ERP, productivity, federation scale)
   — not reimplementing what the geometry kernel already does.
5. **Always set `diffuse_color`** — Blender SOLID mode ignores node trees. Every material
   creation path must set both `mat.diffuse_color` and `bsdf.inputs["Base Color"]`.
6. **Library.blend is the geometry truth** — baked once, shared across buildings. Meshes
   are appended (`link=False`) for per-element work or linked (`link=True`) for GN scale mode.
