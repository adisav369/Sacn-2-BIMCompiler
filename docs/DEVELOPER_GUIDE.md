# Developer Guide

Expert-level onboarding. Assumes you know Java, SQL, and BIM concepts.

> **Architecture Reference**
> Governing architectural principles, spatial storage model (SpaceSize AABB / ERP mapping),
> and the Place / GPD / PhantomLayout spatial constructs live in:
> - `ARCHITECTURE.md` — founding principles, AD pattern, SpaceSize AABB model (§9)
> - `PREFAB_ARCHITECTURE.md` — BOM chain, Place descriptor, GPD, variance child, PhantomLayout (§8)
> - `BIMasBOMConcept.md` — BOM dimension model: Category (M_BomCategory) + Owner (C_BPartner) + SpaceSize (AABB). iDempiere ERD mapping, buffer space invariant, M_Product→M_BOM flattening rationale.
>
> This guide covers pipeline stages, key files, build commands, and developer how-to patterns.
> **Technical architecture content from this guide is being migrated en bloc to the above references.**

**Updated:** February 2026 (Post Phase E / 3-DB Split)

## The Machine

```
IFC source  →  Extract  →  Reference DB  →  placement_extractor  →  lod_element_placement
                                          →  material_extractor   →  (positions + materials)
                                                                           ↓
DSL text  →  Parser  →  Records  →  Compiler  →  Writer  →  SQLite DB (output)
              │                       │              │            │
              │                   reads from      writes to   includes:
              │                       │              │         material_name
              ├── BOM.db (working) ───┘              │         material_rgba
              │   (ad_* config + m_* BOM)            │
              └── component_library.db (LOD) ────────┘
                  (lod_* geometry + meshes + materials)
```

### 3-DB Architecture (Phase E)

See `ConstructionAsERP.md` for 3-DB architecture details (BOM.db, component_library.db, Output DBs).

Source code lives in `DAGCompiler/src/main/java/com/bim/compiler/`. Build with `mvn compile -q`. Run E2E tests with `mvn exec:java -pl DAGCompiler -Dexec.mainClass="..." -q`.

## DAG Pipeline

| Stage | Class | Input | Output |
|-------|-------|-------|--------|
| Parse | `BuildingParser` | `.bim` text | `BuildingDefinition` (records) |
| Validate | `BuildingCompiler` | Definition | Constraint-checked definition |
| Compile | `StoreyCompiler` | Per-storey specs | Room geometries, walls, openings |
| Multi-unit | `MultiUnitCompiler` | Unit blocks | Merged storey with party walls |
| Place (SH/DX) | `RelationalResolver` → `PlacementAD` + `StoreyCompiler.applyPlacementOverrides()` | C_OrderLine + `ad_room_boundary` + `ad_wall_face` | Computed element positions (per-storey consumed list + global emission) |
| Place (BOM, SH/DX) | `FurnitureWorker` → `BOMTierResolver` | Room bounds + BOM tree (m_bom/m_bom_line) | BOM-expanded furniture/fixture positions (three-way dispatch: fixture params / GPD / FLOAT) |
| Place (generative) | `StoreyCompiler` + `MEPWriter` | Room bounds | MEP/fixture positions for generative buildings (TB-LKTN only) |
| Write | `BuildingWriter` → sub-writers + `emitGlobalPlacementElements()` | All specs + consumed list | `DAGCompiler/lib/output/*.db` (SQLite) |
| Witness | `WitnessGenerator` | Output DB | `*_witness.json` proof claims |

Every stage reads metadata from `library/BOM.db` (working tables) and geometry from `library/component_library.db` (LOD) via JDBC. No stage invents values.

**Place stage split (SH/DX — EXTRACTED buildings):**

The Place stage has two sequential sub-stages for EXTRACTED buildings:
1. **Per-storey override** — `StoreyCompiler.applyPlacementOverrides()`: consumes IfcSlab, IfcFurnishingElement, IfcFurniture from PlacementAD and calls `markConsumed()`. Clears compiled walls/doors/windows without emitting them directly.
2. **Global emission** — `BuildingWriter.emitGlobalPlacementElements()`: emits everything NOT consumed (walls, doors, windows, MEP, structural, roofs). Uses MeshBinder for LOD geometry. Runs `PlacementProver` pre-write.

**Phase G-1 (2026-02-26):** `FixturePlacer` and `FurnitureTypeResolver` deleted.
`FurnitureWorker` calls `BOMTierResolver.resolveForRoom()` directly — no intermediary.
`MEPWriter` runs only for generative buildings (`isGenerative()=true`); SH/DX MEP
comes from `emitGlobalPlacementElements()` via the PlacementAD reference path.
See `docs/ConstructionAsERP.md` §3.7 for the ST mode roadmap.

## Key Files

```
DAGCompiler/src/main/java/com/bim/compiler/
├── dsl/
│   ├── BuildingSpecs.java        # 26 record types (RoomSpec, WallSpec, SlabSpec, etc.)
│   ├── BuildingCompiler.java     # Entry points, validation
│   ├── StoreyCompiler.java       # Walls, openings, stairs per storey + placement overrides
│   ├── MultiUnitCompiler.java    # Multi-unit layout, party walls
│   ├── PlacementAD.java          # Placement cache façade: loadRelational() (SH/DX via RelationalResolver) or loadLegacyFlat() (Terminal only, reads lod_element_placement from component_library.db)
│   ├── BuildingWriter.java       # Write orchestrator (schema + global emission)
│   ├── ElementPersistence.java   # Element write (10 columns incl. material_name, material_rgba)
│   ├── MEPWriter.java            # MEP/fixture writer (passes material to output)
│   ├── OpeningWriter.java        # Door/window writer
│   ├── StructuralWriter.java     # Column/beam writer
│   ├── StairWriter.java          # Stair writer
│   ├── WitnessGenerator.java     # Witness claim proofs
│   ├── CompilerConfig.java       # Config reader (placement_mode, etc.)
│   ├── RelationalResolver.java   # Relational placement engine (RM-2+): coordinate computation for SH/DX
│   ├── ExteriorRuleAD.java       # Exterior wall rule lookup
│   ├── BoundElement.java         # Proof-carrying type (mesh fits bbox)
│   ├── MeshBinder.java           # Bind library mesh to element (scale + validate)
│   └── *EndToEndTest.java        # E2E tests for 3 Rosetta Stones + TB-LKTN
├── library/
│   ├── ComponentLibrary.java     # LOD400 component lookup
│   ├── BOMTierResolver.java      # Unified BOM resolver — three-way dispatch: fixture params / GPD / FLOAT
│   ├── BOMTreeLoader.java        # Shared AD-layer BOM tree loader (m_bom_line + m_attribute → BOMNode/BOMChild)
│   ├── FurnitureWorker.java      # BundleWorker impl — dispatches to BOMTierResolver, maps PlacedFurniture → PlacedElement
│   ├── WorkerRegistry.java       # BundleWorker factory registry
│   ├── SlotRegistry.java         # Room→assembly slot dispatch (ad_room_slot)
│   └── ManifestResolver.java     # Assembly face clearances
├── validation/
│   ├── PlacementProver.java      # 14 proofs in 5 tiers (non-blocking audit)
│   ├── SpatialDigest.java        # Spatial fingerprint hash
│   └── GeometryIntegrityChecker.java  # Vertex-to-bbox + mesh topology validation
└── geometry/
    ├── Point3D.java, BoundingBox.java
    └── (geometry primitives)
```

## The Library Databases

Everything the compiler knows lives in two databases (Phase E 3-DB split):

```
component_library.db  (LOD Geometry Store — ~12 tables)
│
├── LOD GEOMETRY (from Python extraction scripts)
│   ├── component_geometries   23,888 meshes (vertices/faces BLOBs, deduplicated by hash)
│   ├── component_definitions  23,888 defs (name, bounds, orientation, attachment)
│   ├── component_types        35 IFC class categories
│   ├── lod_geometry_map       65,336 element → geometry hash mappings
│   ├── lod_element_placement  67,332 compiled LOD element instances
│   ├── surface_styles         80 material RGBA colors
│   ├── material_layers        60 layer compositions
│   ├── lod_parametric_mesh    5 parametric mesh generators
│   ├── lod_parametric_mesh_param  41 mesh parameters
│   └── lod_roof_preset        4 roof presets
│   Question answered: "What does a toilet/door/sprinkler LOOK like?"
│
BOM.db  (Unified Working Database — ~73 tables)
│
├── BOM ASSEMBLY (m_* tables)
│   ├── m_bom                  Assembly headers (22 active)
│   ├── m_bom_line             Child placements (82 active) — dx/dy/dz, rotation_rule, SpaceSize
│   ├── m_attribute            Child parameters (214) — spatial offsets, z_rules, wall rules
│   └── M_BomCategory          Category codes (14)
│
├── CONFIG + RULES (ad_* tables)
│   ├── c_order   C_Order (Construction Order): 4 buildings
│   ├── c_orderline        C_OrderLine (Construction Order Details): placement rules per element
│   ├── ad_room_boundary       Room-to-grid mapping
│   ├── ad_building_grid       Structural grid lines
│   ├── ad_wall_face           Room boundary faces → wall type
│   ├── ad_space_type          Room type definitions (37)
│   ├── ad_wall_type           Wall thickness rules (13)
│   ├── ad_opening_family      Opening dimensions (295)
│   ├── ad_product_dim         Product catalog dimensions (in meters)
│   └── ... (~60 more ad_* tables: MEP, structural, code checks, spatial rules)
│   Source: migration/migration_108B.sql through migration_RM6*.sql (all idempotent)
│   Question answered: "How do things ASSEMBLE? Which wall goes WHERE?"
```

LOD geometry is extracted from real IFC files. Working tables are curated from standards and Rosetta Stone observations. The compiler reads both at runtime — LOD provides the mesh, working tables tell it where and how to place it.

The critical tables:

| Table | Database | What it does |
|-------|----------|-------------|
| `m_bom` | BOM.db | M_BOM headers — assembly ID, group_by, bom_category, c_bpartner |
| `m_bom_line` | BOM.db | M_BOM_Line children — role, name_pattern, dx/dy/dz, rotation_rule, space_*_mm |
| `m_attribute` | BOM.db | Child parameters — spatial offsets, z_rules, wall rules |
| `c_orderline` (C_OrderLine — Construction Order Details) | BOM.db | Element placement rules — host_ref, ifc_class, position_rule |
| `ad_room_boundary` | BOM.db | Room bounds mapped to grid cells |
| `ad_space_type` | BOM.db | Room type definitions (37) — category, wall rules |
| `ad_wall_type` | BOM.db | Wall thickness rules (13) — profile→thickness→material |
| `ad_opening_family` | BOM.db | Opening dimensions (295) — width, height, depth per family |
| `ad_product_dim` | BOM.db | Product catalog — dimensions in meters |
| `lod_geometry_map` | component_library.db | Element → geometry hash mapping (65K) |
| `component_definitions` | component_library.db | LOD400 geometry refs (23K) — bounds, orientation, hash |

## BOM Pattern (How Assemblies Work)

> **Dimension model:** see [BIMasBOMConcept.md](BIMasBOMConcept.md).
> M_BOM (`m_bom`) = product + assembly merged. M_BOM_Line (`m_bom_line`) = child reference + SpaceSize.
> Three dimensions: `bom_category` (WHAT), `c_bpartner` (WHO), SpaceSize (HOW MUCH).
> All BOM tables live in `library/BOM.db`.

A BOM recipe = parent assembly + ordered children. Each child has a name pattern (matches `component_definitions`) and spatial params.

```
BED_SET (m_bom — in BOM.db)
├── seq 1: BED       name_pattern="Bed_Queen"    back_to_wall=true
└── seq 2: SIDE_TABLE name_pattern="Side_Table"   dx=0.98
```

The resolver loads the tree, finds matching components by name pattern, applies offsets. No geometry invented — everything comes from the library.

### Adding a BOM Recipe (by hand)

**Example:** Add a STUDY_DESK_SET with desk + lamp. All SQL runs against `library/BOM.db`.

```sql
-- 1. Create the BOM header
INSERT INTO m_bom (bom_id, bom_type, group_by, is_active)
VALUES ('STUDY_DESK_SET', 'ASSEMBLY', 'ROOM', 1);

-- 2. Add children (sequence = placement order)
INSERT INTO m_bom_line (bom_id, role, child_name_pattern, sequence, is_active)
VALUES ('STUDY_DESK_SET', 'DESK', 'Desk%', 1, 1);

INSERT INTO m_bom_line (bom_id, role, child_name_pattern, sequence, is_active)
VALUES ('STUDY_DESK_SET', 'LAMP', 'Light_Desk%', 2, 1);

-- 3. Add spatial params (get bom_child_id from step 2)
-- Desk: against back wall
INSERT INTO m_attribute (bom_child_id, param_key, param_value)
VALUES (LAST_INSERT_ROWID(), 'back_to_wall', 'true');

-- Lamp: offset from desk center
INSERT INTO m_attribute (bom_child_id, param_key, param_value)
SELECT bom_child_id, 'dx', '0.4'
FROM m_bom_line WHERE bom_id='STUDY_DESK_SET' AND role='LAMP';
```

**Verify:** `SELECT * FROM m_bom_line WHERE bom_id='STUDY_DESK_SET';`

The `child_name_pattern` uses SQL LIKE wildcards (`%`). The resolver calls `findByName(pattern)` against `component_definitions`. If no match, the child is skipped with a warning.

### Key BOM Params

| param_key | Values | Effect |
|-----------|--------|--------|
| `dx`, `dy`, `dz` | meters | Offset from parent origin |
| `rotation` | radians | Rotation around Z |
| `back_to_wall` | true/false | Snap to back wall of room |
| `wall_rule` | back/door/side_interior | Which wall to place against |
| `placement_wall` | back/door/side_interior | Same (BOM-driven variant) |
| `z_rule` | floor/ceiling | Z reference point |
| `z_offset` | meters | Offset from z_rule reference |
| `spacing` | meters | For repeated elements (toilets along wall) |
| `qty_rule` | match_role:TOILET | Match count of another role |
| `name_pattern` | SQL LIKE | Override child_name_pattern |

### MANIFEST Faces (Phase 115B)

Each assembly declares clearance requirements per face:

```sql
-- What clearance does BED_SET need in front?
SELECT clearance_m FROM ad_assembly_manifest
WHERE assembly_id='BED_SET' AND face='FRONT';
-- → 0.6 (meters)
```

Interface types: `CLEARANCE` (free space), `WALL_BACK` (against wall), `JOINABLE` (can abut another assembly).

## Material Pipeline (Phase MAT)

Materials and colours flow from IFC sources through the full compilation pipeline.

### Data Flow

```
IFC source file (e.g., Ifc4_SampleHouse.ifc)
  ├── IfcRelAssociatesMaterial → IfcMaterial.Name → material_name
  └── Representation → IfcStyledItem → IfcSurfaceStyleRendering
      → IfcColourRgb (R,G,B) + Transparency → material_rgba
                    │
                    ↓
    material_extractor.py --ifc ... --ref ...
                    │
                    ↓
    Reference DB: elements_meta.material_name, elements_meta.material_rgba
                    │
                    ↓
    material_extractor.py --populate-placement --ref ... --library ...
                    │
                    ↓
    component_library.db: lod_element_placement.material_name, lod_element_placement.material_rgba
                    │
                    ↓
    PlacementAD.java (reads materialName, materialRgba per placement)
                    │
                    ↓
    StoreyCompiler / BuildingWriter (creates specs with material fields)
                    │
                    ↓
    ElementPersistence.writeElementMeta() (10-column INSERT)
                    │
                    ↓
    Output DB: elements_meta.material_name, elements_meta.material_rgba
```

### RGBA Format

`material_rgba` stores comma-separated RGBA values (0.0–1.0):
- Format: `"R,G,B,A"` — e.g., `"0.000,0.502,0.753,0.100"`
- Alpha = 1.0 - IFC_Transparency (IFC uses transparency, we store opacity)
- Glass: `Transparency: 0.9` → `alpha = 0.1` → 90% see-through
- Opaque wall: `Transparency: 0.0` → `alpha = 1.0` → fully solid

### Key Classes

| Class | Material Role |
|-------|--------------|
| `PlacementAD.Placement` | Record with `materialName()`, `materialRgba()` fields |
| `BuildingSpecs.SlabSpec` | `materialName`, `materialRgba` fields (with backwards-compat constructor) |
| `BuildingSpecs.FixtureSpec` | `materialName`, `materialRgba` fields (with backwards-compat constructor) |
| `ElementPersistence` | `writeElementMeta()` 10-param version: ...fireRatingHr, materialName, materialRgba |
| `BuildingWriter` | Schema includes material columns; global emission passes material |
| `StoreyCompiler` | `applyPlacementOverrides()` passes material from PlacementAD to specs |
| `MEPWriter` | `writeFixture()` passes material to ElementPersistence |

### Transparency Pipeline (Window Glass)

Transparent materials (glass, water, shower screens) require TWO things in the output DB:

1. `elements_meta.material_name` must match a `surface_styles.style_name` that has `transparency > 0`
2. The `surface_styles` table must be present (copied from `component_library.db` by `BuildingWriter.copySurfaceStyles()`)

The Bonsai Federation addon joins these:
```sql
LEFT JOIN surface_styles s ON m.material_name = s.style_name
```
If `s.transparency > 0.01`, the addon sets `blend_method = 'BLEND'` and `Alpha = 1.0 - transparency` on the Blender material. Without this join, elements appear opaque regardless of `material_rgba` alpha.

Key transparent styles in `surface_styles`:
| style_name | transparency | Use |
|-----------|-------------|-----|
| `Glass` | 0.9 | SH/DX windows, curtain wall panels |
| `Glass - Clear, Grey` | 0.64 | Tinted glass |
| `Window_W1` | 0.6 | TB-LKTN standard windows |
| `Window_W2` | 0.6 | TB-LKTN secondary windows |
| `Window_W3_Small` | 0.6 | TB-LKTN small windows (wet rooms) |
| `Shower` | 0.3 | Shower screens |
| `Interior Fill` | 0.85 | Interior transparent fills |

**Trap:** IFC exports assign `material_name = 'Window Frame'` to IfcWindow (the frame, not the glass pane). Migration RM6 fixes this to `'Glass'` in both `lod_element_placement` and C_OrderLine (Construction Order Details).

### Running the Extractor

```bash
# Step 1: Enrich reference DB from IFC source
python3 DAGCompiler/tools/material_extractor.py \
    --ifc DAGCompiler/lib/input/Ifc4_SampleHouse.ifc \
    --ref DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db

# Step 2: Copy materials from reference DB → lod_element_placement
python3 DAGCompiler/tools/material_extractor.py \
    --populate-placement \
    --ref DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db \
    --library library/component_library.db \
    --building-type Ifc4_SampleHouse

# Step 3: Compile — materials flow automatically via PlacementAD
mvn exec:java -pl DAGCompiler \
    -Dexec.mainClass="com.bim.compiler.dsl.SampleHouseEndToEndTest" -q

# Verify in output
sqlite3 DAGCompiler/lib/output/ifc4_sample_house.db \
    "SELECT material_name, material_rgba FROM elements_meta WHERE material_name IS NOT NULL"
```

## Extraction Tool (`tools/extract.py`)

Unified tool for both Layer 1 (LOD400) and Layer 3 (Rosetta reference) extraction:

```bash
# Layer 3: Extract Rosetta reference from IFC file
python3 tools/extract.py --to reference  source.ifc  -o reference/rosetta/out.db

# Layer 3: Extract Rosetta reference from pre-tessellated DB (e.g., federation)
python3 tools/extract.py --to reference  database/enhanced_federation_GI.db \
    -o reference/rosetta/terminal.db  --exclude IfcPlate,IfcOpeningElement

# Layer 1: Extract LOD400 geometry to component library
python3 tools/extract.py --to library  source.ifc  --classes IfcFurniture,IfcDoor

# Filter by discipline (DB sources only)
python3 tools/extract.py --to reference  federation.db  -o out.db  --discipline ARC,STR
```

Replaces: `extract_all_components.py`, `import_ifc_furniture.py`, `extract_duplex_components.py`, `populate_sample_house_db.py`, `populate_duplex_db.py`. Old scripts kept in `scripts/` for reference.

## Build & Test

### Canonical test-compile gate (run before every commit)

```bash
# From project root — always /home/red1/bim-compiler
./scripts/run_tests.sh            # all three suites
./scripts/run_tests.sh dag        # DAGCompiler only (118/2 baseline)
./scripts/run_tests.sh orm        # ORMSandbox only (6/0 baseline)
./scripts/run_tests.sh topology   # TopologyMaker only (15/0 baseline)
```

Expected baseline (2026-02-26): **199 PASS / 1 intentional RED / 1 SKIP** (G8-DX calibration).

### Individual module commands

```bash
mvn compile -q                    # Compile all modules

# DAGCompiler — 118 contract tests (DriftGuard + LOD + Rosetta + Placement)
mvn test -pl DAGCompiler

# ORMSandbox — 6 DAO smoke tests (BasePO lifecycle, ModelQuery, entity pairs)
mvn test -pl ORMSandbox

# TopologyMaker — 15 strategy + PO tests
mvn test -pl TopologyMaker
```

### Spatial fidelity check (SH / DX only — SpatialDigest gate)

```bash
python3 tools/spatial_checker.py \
  DAGCompiler/lib/output/ifc4_sample_house.db \
  DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db \
  --discipline ARC
```

Note: `-pl DAGCompiler` is required since source is in the DAGCompiler module.

### Schema Snapshots (after any migration)

```bash
sqlite3 library/BOM.db .schema > library/schema_snapshot_bom.sql
sqlite3 library/component_library.db .schema > library/schema_snapshot_component.sql
```

These are **local reference files** — gitignored under `library/`, never pushed.
Purpose: full DDL (column names, types, FKs, CHECK constraints) readable without querying the DB.
Regenerate after every migration script run.

## DAO Framework & Debug Tooling (orm-core)

The project has a second module stack — `orm-core`, `ORMSandbox`, and `TopologyMaker` — that
sits alongside `DAGCompiler` without depending on it. They share only the SQLite database file.

> Full specification: `orm-core/docs/BIMDAOTechnicalFramework.md`

### Symbiotic Architecture

```
Migrations ──────────────────┐
TopologyMaker (orm-core) ────┼──► BOM.db (working)  ◄── DAO layer reads / writes
                             │        │
                             │   DAGCompiler reads (raw batch SQL — no orm-core dependency)
                             │        │          also reads component_library.db (LOD)
                             │        ▼
                             │   output DBs (ifc4_sample_house.db, ifc2x3_duplex.db …)
                             │        │
                             └────────┴──► BuildingInspector reads BOM.db + component_library.db
```

`DAGCompiler` uses raw batch JDBC (`loadRooms()`, `loadRules()` — one query each) for
compilation speed. `orm-core` provides a typed iDempiere-style DAO layer for inspection,
seeding new domain data, and debugging. The two systems communicate through data, not code.
Most code now uses a single `BOM.db` connection; files needing LOD geometry open a second
connection to `component_library.db` (e.g., `ComponentLibrary`, `BuildingWriter`, `MeshBinder`).

### BuildingInspector — Primary Debug Tool

`BuildingInspector` navigates the full BIM construct via typed entity objects and prints
structured reports. Run it whenever a test fails and you need to understand the data state
before touching Java.

```bash
# From project root
mvn -pl ORMSandbox exec:java \
  -Dexec.mainClass="com.bim.ormsandbox.BuildingInspector" \
  -Dexec.args="library/BOM.db <command> [arg]"
```

| Command | Argument | What it shows |
|---------|----------|--------------|
| `buildings` | — | All registered buildings: id, type, doc_status, expected_elements |
| `rooms` | `<buildingType>` | Room boundaries: X/Y min/max, centroid, area, coordinate_frame |
| `bom` | `<bomId>` | Full recursive BOM tree with dx/dy/dz offsets, rotation_rule, product dims |
| `rules` | `<buildingType>` | Element rules: host_ref, ifc_class, discipline, position_rule, height |
| `slots` | `<roomType>` | BOM dispatch for room type: assembly_id, priority, required |
| `product` | `<productId>` | Product dimensions in meters (W × D × H) + clearances |

Can also point at an output DB to inspect compiled results:
```bash
mvn -pl ORMSandbox exec:java \
  -Dexec.mainClass="com.bim.ormsandbox.BuildingInspector" \
  -Dexec.args="DAGCompiler/lib/output/ifc4_sample_house.db rooms Ifc4_SampleHouse"
```

### Debug Workflow — Real Example (G8 Frame-of-Reference Bug)

**Symptom:** `RosettaPlacementTest` G8-SH RED — 16/17 furniture elements fail nearest-neighbour
check. Compiled X centroid ≈ +3900mm, reference X centroid ≈ −3000mm. Delta ≈ 6900mm.

**Step 1 — Check the DB data before reading any Java:**

```bash
mvn -pl ORMSandbox exec:java \
  -Dexec.args="library/BOM.db rooms Ifc4_SampleHouse"
```
```
=== ROOM BOUNDARIES for 'Ifc4_SampleHouse' (2) ===
  [42] ROOM_Ground_Floor_1    type=LIVING       frame=IFC_GLOBAL_MM
       X: [-7510, 1359]  centX=-3075
       Y: [-281,  4409]  centY=2064
       area=39.2 m²

  [43] ROOM_Ground_Floor_2    type=BEDROOM      frame=IFC_GLOBAL_MM
       X: [4113, 6120]   centX=5116
       Y: [946,  4367]   centY=2656
       area=8.3 m²
```

**Reading:** DB has correct calibrated values. LIVING centroid = −3075mm.
`coordinate_frame = IFC_GLOBAL_MM` — bounds are already in world space.
**Data is not the problem.** Eliminates the "migration didn't apply" hypothesis instantly.

**Step 2 — Check BOM dispatch:**

```bash
mvn -pl ORMSandbox exec:java \
  -Dexec.args="library/BOM.db slots LIVING"
```
```
=== ROOM SLOTS for 'LIVING' (2) ===
  [12] LIVING/FURNITURE    asm=LIVING_SET    priority=100  required=no
```

**Reading:** Correct BOM dispatched to correct room. Dispatch is not the problem.

**Step 3 — Inspect BOM children:**

```bash
mvn -pl ORMSandbox exec:java \
  -Dexec.args="library/BOM.db bom LIVING_SET"
```
```
=== BOM CHAIN: LIVING_SET ===
  [BOM] LIVING_SET  type=SET  groupBy=ROOM
    [LEAF] role=SOFA        seq=10  pattern='Sofa%'          offset(dx=0.0,  dy=0.0, dz=0.0)
      [PRODUCT] Sofa  2.000m × 0.800m × 0.450m
    [LEAF] role=SOFA_B      seq=20  pattern='Sofa_Loveseat%' offset(dx=-1.5, dy=0.0, dz=0.0)
    [LEAF] role=COFFEE_TABLE seq=30 pattern='Coffee_Table%'  offset(dx=0.0,  dy=1.2, dz=0.0)
    [LEAF] role=SIDE_TABLE_A seq=40 pattern='Side_Table%'    offset(dx=1.2,  dy=0.0, dz=0.0)
```

**Reading:** Children are correct. Offsets are relative to the anchor.
If the anchor were at LIVING X(−7510…1359), furniture would land at X ≈ −3000mm.
But compiled output shows X ≈ +3900mm — the BEDROOM range.

**Hypothesis formed from three commands, under 5 minutes:**
The compiler is applying a LOCAL→GLOBAL offset transform on top of bounds that are
already in `IFC_GLOBAL_MM`. The `coordinate_frame` column is not being checked
before the transform is applied, causing a double-shift of ≈ +6900mm in X.

**Step 4 — Go directly to the fix location:**

The inspector eliminated data, dispatch, and BOM structure as causes. Only the anchor
computation remains. Open `BOMTierResolver.java` → `computeBomAnchorForRoom()` and
look for where the
building/storey offset is added without checking `room.coordinateFrame()`.

**Without BuildingInspector** the same investigation takes 90–120 minutes:
add debug prints → recompile → read 1,197 log lines → run `sqlite3` manually →
cross-reference by hand. With it: three commands, one hypothesis, straight to the fix.

### orm-core Entity Coverage

Typed X_/M_ entity pairs in orm-core. Each pair gives:
- `COLUMNNAME_*` constants — compile-time safety for column names
- Factory methods — `MBOM.get(conn, "BED_SET_MASTER")` vs raw ResultSet
- `beforeSave()` validation — catches NOT NULL violations before the DB does

All tables below live in `BOM.db` except `lod_geometry_map` (component_library.db).

| Entity pair | Table | Key factory methods |
|-------------|-------|-------------------|
| `MBOM` | `m_bom` | `get(bomId)` — assembly header |
| `MBOMLine` | `m_bom_line` | `getByBom(bomId)` — child placement + SpaceSize |
| `Filler` | `m_bom_line` | `fill(bomId)` — create interstitial fillers between items; `distanceBetween()` — measuring tape; `isStripComplete()` — ground truth check |
| `M_AdRoomBoundary` | `ad_room_boundary` | `getByBuilding(type)`, `get(type, roomName)` |
| `MOrderLine` | `c_orderline` | `getByBuilding(type)` |
| `M_AdProductDim` | `ad_product_dim` | `get(productId)` — **units in meters** |
| `MOrder` | `c_order` | `getAll()`, `get(buildingId)` |
| `M_AdRoomSlot` | `ad_room_slot` | `getByRoomType(roomType)` |
| `M_AdTypologyPattern` | `ad_typology_pattern` | `getActive()`, `getByStrategy(strategy)` |
| `M_AdGeometryMap` | `lod_geometry_map` (component_library.db) | `getByBuilding(type)`, `getOrphans(type)` |

### Guardrails

- **Never import orm-core in DAGCompiler** — compilation hotpath stays raw batch SQL
- **Never use ModelQuery against `v_*` views** — views are SQL contracts, not entity tables
- **BasePO never commits** — caller (TopologyBatchProcess, test) owns the transaction
- **`ad_product_dim` units are meters** — `getWidth()` returns 0.45 not 450 for a chair
- **Empty string ≠ null** — `get_ValueAsString()` returns `""` for empty DB values,
  not `null`; always check `== null || isEmpty()` in dispatch guards

Run ORMSandbox smoke tests:
```bash
mvn test -pl ORMSandbox
```

## Output DB Schema

The compiler writes to SQLite. Key tables:

| Table | Content |
|-------|---------|
| `spatial_structure` | Project → Site → Building → Storey hierarchy |
| `elements_meta` | Every element: guid, ifc_class, name, storey, discipline, material_name, material_rgba |
| `elements_rtree` | Spatial index: id, minX, maxX, minY, maxY, minZ, maxZ |
| `base_geometries` | Vertices/faces BLOBs (float32/int32 arrays) + hash |
| `assembly_components` | BOM parent-child relationships |
| `mep_systems` / `system_nodes` / `system_edges` | MEP system graph |
| `simple_qto` | Quantity takeoff (area, volume, length) |

Query examples:
```sql
-- All toilets on Ground floor
SELECT e.element_name, r.minX, r.maxX, r.minY, r.maxY
FROM elements_meta e
JOIN elements_rtree r ON e.id = r.id
WHERE e.ifc_class = 'IfcFlowTerminal'
  AND e.element_name LIKE 'Toilet%'
  AND e.storey = 'Ground';

-- Glass panels with transparency
SELECT e.guid, e.material_name, e.material_rgba
FROM elements_meta e
WHERE e.material_name = 'Glass';
-- 0.000,0.502,0.753,0.100 → blue glass, alpha=0.1 (90% transparent)
```

## Traps

- `elements_rtree` columns: id, minX, **maxX**, minY, **maxY**, minZ, **maxZ** (NOT interleaved min/max pairs)
- Walls stored as `IfcPlate` (not IfcWall) — SQL must include IfcPlate
- `findByName("ChairDesk")` fails — must pass `%ChairDesk%` with wildcards
- `component_definitions.orientation` can be NULL → `valueOf(null)` throws NPE
- BOM role names must match writer constants: `BRANCH` not `FP_BRANCH`
- World-space geometry: all elements at zero transforms (Pattern B). No transform stacking.
- Library geometry (non-GEO_ hash) uses canonical coords, NOT world coords — bounds check invalid
- `element_instances` column is `guid` (NOT `element_guid`) in the output DB schema
- `lod_geometry_map` ordinals: SH uses GLOBAL ordinals (renumbered), DX uses per-class-per-storey (rank-based lookup)
- `ComponentLibrary.resolveGeometryByInstance()` has TWO lookup strategies: direct ordinal, then rank-based fallback
- Shadow validator matches by placement_id = ordinal — renumber geometry_map to match, never element_rule
- R*Tree uses float32 rounding — use `struct.pack('f')` in Python, don't cast all to float in Java
- OpeningWriter distorts bbox — post-write fixup needed
- TB-LKTN compilation relies entirely on PlacementAD — StructuralWriter doesn't fire (0 compiled walls)
- TB-LKTN DSL completeness (generative building audit): Grid + rooms + adjacencies = complete. Windows/doors are in element_rules (family_refs now wired to WINDOW_W1/W2/W3 and DOOR_D*). WINDOW declarations missing from DSL text itself — all 11 windows are metadata-only (design gap: DSL should declare `WINDOW north` per room as SH does). Drain perimeter (8 segments) is ABSOLUTE GEN-BOX — pending compiler-agnostic refactor. Furniture BOMs all `is_active=0` (Last Mile deferred). Roof wired to HIP_ROOF_MY (main) + GABLE_PORCH_MY (porch) in metadata; compiler currently still uses GABLE_25 orientation string (pending Java dispatch refactor).
- DSL `.bim` files are opaque manifests — never read or analyze them directly

## Data Provenance: How the Model is Stacked

Three IFC source families feed three layers:

```
  IFC SOURCE FILES          LAYER 1: GEOMETRY       LAYER 2: METADATA         LAYER 3: ROSETTA
  (fossil truth)            (Python extraction)     (SQL migrations)          (spatial validation)
  ════════════════          ═══════════════════     ════════════════          ══════════════════

  Federation DB ─────→ extract_all_components.py ─┐
  (SJTII Terminal,      8,400+ definitions        │
   9 disciplines,                                 │
   51K elements)     migrate_tank_geometry.py ─────┤
                       3 water tanks              │
                                                  ├─→ component_library.db (LOD)
  SampleHouse IFC ───→ import_ifc_furniture.py ───┤     ┌──────────────────┐
  (Ifc4, UK house)      furniture families        │     │ component_geom   │ ← LOD geometry
                                                  │     │ component_defs   │
  Duplex IFCs ──┬───→ import_ifc_furniture.py ────┤     │ lod_geometry_map │
  (Ifc2x3, US)  │      furniture (Phase 109)      │     │ surface_styles   │
                 └──→ extract_duplex_components.py ┘     └──────────────────┘
                        MEP fixtures (Phase 114)
                                                         BOM.db (Working)
                                                         ┌──────────────────┐
                                                         │ m_bom/m_bom_line │ ← BOM assembly
  Standards ─────────→ migration_108B..119D.sql ────────→│ c_orderline  │ ← rules + config
  Rosetta findings        (hand-curated,                 │ ad_space_type    │
  Building codes          idempotent)                    │ ad_product_dim   │
                                                         │ ... (~73 tables) │
                                                         └──────────────────┘
                                                               │ (reads)
                                                               ↓
  examples/*.bim → Parser → Compiler → Writer ──────→ DAGCompiler/lib/output/*.db
                                                               │
                                                               ↓ (compares)
  SampleHouse IFC ──→ extract.py ──────────────────→ DAGCompiler/lib/input/*.db
  Duplex IFCs ──────→ extract.py ──────────────────→ DAGCompiler/lib/input/*.db
  Federation DB ────→ extract.py ──────────────────→ DAGCompiler/lib/input/*.db
                           │
                           ↓
                    material_extractor.py ──→ enriches reference DBs with material_name/rgba
                    placement_extractor.py ─→ lod_element_placement (positions + materials)
                    spatial_checker.py ─────→ X-ray fidelity scores
```

### Layer 1: Geometry (Python extraction → component_library.db)

**Purpose:** "What does a toilet/door/sprinkler LOOK like?" — mesh vertices, faces, bounds.

| Script | Source | Phase | What it Extracts |
|--------|--------|-------|-----------------|
| `extract_all_components.py` | Federation DB | early | 8,400+ defs: pipes, ducts, beams, columns, sprinklers, doors, windows, furniture, etc. |
| `import_ifc_furniture.py` | Duplex ARC + SampleHouse + Revit ARC | 109 | Residential furniture families (beds, sofas, tables, chairs, cabinets) |
| `extract_duplex_components.py` | Duplex ARC + MEP | 114 | MEP fixtures (WC, lavatory, shower, pendant lights, appliances) |
| `migrate_tank_geometry.py` | Federation DB | 113 | 3 FRP water tank BLOBs (cross-DB copy) |

All scripts use `INSERT OR IGNORE` on `geometry_hash` — identical meshes are deduplicated. Result: **23,888 component definitions** with LOD400 geometry.

### Layer 2: Metadata (SQL migrations → BOM.db)

**Purpose:** "How do things ASSEMBLE? Which wall type goes WHERE?" — curated construction knowledge.

Source: `migration/migration_108B.sql` through `migration_119D.sql` (all idempotent). Written by hand from building codes, IPC standards, Rosetta Stone observations, and engineering judgement.

Examples of what Layer 2 encodes:
- `m_bom` / `m_bom_line` (BOM.db): BED_SET = bed + side_table, with dx=0.98m offset
- `ad_wall_type`: EXTERIOR + UK_Residential profile → 290mm brick
- `ad_opening_family`: D_EXT_DBL → 1860x2110mm, depth 200mm
- `ad_room_slot`: BATHROOM → BATHROOM_SET assembly at priority 1 (deprecated by bom_category)
- `ad_assembly_manifest`: BED_SET needs 0.6m CLEARANCE on FRONT face

**Layer 2 is NOT extractable** — it's the compiler's learned knowledge, curated over 30+ phases.

### Layer 3: Rosetta Stone (spatial validation)

**Purpose:** "Did we put things in the RIGHT PLACE?" — measure output against real IFC buildings.

| Script | Source | Phase | What it Produces |
|--------|--------|-------|-----------------|
| `populate_sample_house_db.py` | SampleHouse IFC | 118C | Reference DB: 55 elements with world-space bboxes |
| `populate_duplex_db.py` | Duplex ARC+MEP IFC | 114/119B | Reference DB: 1,085 elements with world-space bboxes |
| `rosetta_dictionary.py` | Any DB | 119D | Spatial skeleton: 11-section text dump of spatial facts |
| `spatial_checker.py` | Output DB vs Reference DB | 118C+ | X-ray fidelity score (dimension signature fingerprint) |

Layer 3 **reads but never writes** to `component_library.db`. It compares compiler output against reference DBs to measure spatial fidelity. Findings from Layer 3 feed back into Layer 2 as new migration SQL.

### The Feedback Loop

```
Layer 3 (Rosetta)  ──discovers──→  "Duplex walls are 417mm, not 150mm"
                                          │
                                          ↓
Layer 2 (Metadata)  ←──migration──  migration_119_wall_alignment.sql
                                          │
                                          ↓
Layer 1 (Geometry)                  (unchanged — same meshes, better placement)
```

### Rosetta Stone Pairs (Current — Phase RM-4)

| Stone | IFC Source | Reference DB | Elements | F1 Score |
|-------|-----------|-------------|----------|----------|
| SampleHouse | `Ifc4_SampleHouse.ifc` | `DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db` | 55 | **100%** |
| Duplex | `Ifc2x3_Duplex_*.ifc` | `DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db` | 1,085 | **100%** |
| Terminal | Federation of 7 IFCs | `DAGCompiler/lib/input/Terminal_Extracted.db` | 51,088 | **~100%** |
| TB-LKTN | *None (generative)* | *None* | 138 | N/A (generative — no reference IFC) |

IFC source files are stored in `DAGCompiler/lib/input/` for SampleHouse and Duplex (Terminal was merged from 7 IFCs into the federation DB). TB-LKTN is the first generative building — 58 elements from relational rules only, no IFC reference. It proves the compiler can generate buildings from pure intent without an existing IFC model.

## Viewing Output (Bonsai Federation Addon)

The primary viewing path is **NOT** GLTF export — it's the **Bonsai Federation addon** in Blender, which reads the output SQLite DB directly.

### Addon Location

```
/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/
├── stage2_tessellation_loader.py   # Material creation + geometry loading from DB
├── __init__.py                     # Addon registration
└── ...
```

### How "Full Load" Works

The Federation addon's "Full Load" feature:
1. Connects to the output SQLite DB (`DAGCompiler/lib/output/*.db`)
2. Queries `elements_meta` joined with `element_geometry` + `element_transforms` + `surface_styles`
3. Unpacks binary vertex/face BLOBs from `base_geometries`
4. Creates Blender meshes with materials derived from `material_rgba` + `surface_styles`
5. Positions elements using `element_transforms` (center_x/y/z)

### Material Creation Pipeline (in addon)

```
Output DB
  ├── elements_meta.material_name ──┐
  ├── elements_meta.material_rgba   │  LEFT JOIN on material_name = style_name
  └── surface_styles ───────────────┘
         │
         ↓
  stage2_tessellation_loader.py::get_or_create_db_material()
         │
         ├── Parse RGBA → base color (with gray amplification for subtle colors)
         ├── surface_styles.transparency > 0.01?
         │   YES → blend_method='BLEND', Alpha = 1.0 - transparency
         │   NO  → opaque material
         ├── surface_styles RGB overrides element RGBA when available
         ├── specular_exponent → Blender roughness (inverse mapping)
         └── reflectance_method hints (METAL → metallic=0.9, GLASS → roughness≤0.1)
```

### Key Query (with surface_styles)

```sql
SELECT m.guid, m.ifc_class, m.discipline,
       g.geometry_hash,
       t.center_x, t.center_y, t.center_z,
       m.material_name, m.material_rgba,
       s.transparency, s.specular_ratio, s.specular_exponent,
       s.specular_r, s.specular_g, s.specular_b,
       s.reflectance_method, s.surface_r, s.surface_g, s.surface_b
FROM elements_meta m
JOIN element_geometry g ON m.guid = g.guid
JOIN element_transforms t ON m.guid = t.guid
LEFT JOIN surface_styles s ON m.material_name = s.style_name
ORDER BY g.geometry_hash, m.discipline
```

### Viewing Workflow

```bash
# 1. Compile a building
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.SampleHouseEndToEndTest" -q

# 2. Open Blender with Bonsai addon
# 3. Federation panel → "Full Load" → select DAGCompiler/lib/output/ifc4_sample_house.db
# 4. Materials, transparency, and geometry load automatically from DB
```

## Mesh2Library — Parametric Mesh System

Fabricated mesh components (roofs, drain channels) are generated at compile time from
metadata parameters. No Python mesh scripts. No hardcoded vertex lists in Java.

### Sealed Interface

```java
public sealed interface ParametricMesh
    permits GableRoofMesh, HipRoofMesh, HalfRoundDrainMesh {
    MeshResult generate(MeshParameters params);
}
```

Adding a new mesh shape = new `permits` entry + new Java class + 2 SQL rows.
The CompilerContractTest blocks any Python mesh script that sneaks back in.

### Registered Mesh Types (as of Feb 2026)

| mesh_type | Generator | Building use | Params |
|---|---|---|---|
| `GABLE_ROOF_MY` | `GableRoofMesh` | Generic MY residential gable | pitch_deg, overhang_mm, ridge_axis |
| `HIP_ROOF_MY` | `HipRoofMesh` | TB-LKTN main block hip roof | pitch_deg, span_mm, depth_mm, ridge_length_mm, overhang_mm |
| `GABLE_PORCH_MY` | `GableRoofMesh` | TB-LKTN front porch gable | pitch_deg, span_mm, depth_mm, overhang_mm, ridge_axis=Y |
| `GABLE_CANOPY_MY` | `GableRoofMesh` | Generic porch canopy | pitch_deg, overhang_mm, canopy_type |
| `DRAIN_HALFROUND_MY` | `HalfRoundDrainMesh` | TB-LKTN perimeter drain G5 | diameter_mm=230, wall_thickness_mm=40, segment_length_mm=1000, segments_n=16 |

### Three-Table Authority for Fabricated Meshes

```
lod_parametric_mesh_param  → shape parameters (pitch, span, diameter)
m_attribute (BOM.db)        → where the mesh sits in the assembly (dx/dy/dz)
ad_product_dim            → resulting bounding box (generated bbox → catalog entry)
```

Fabricated mesh BOM leaves in `ad_product_dim`:
| product_id | W × D × H (m) | Description |
|---|---|---|
| `HIP_ROOF_MY` | 9.9 × 5.4 × 1.26 | TB-LKTN hip roof (rows 3-5) |
| `GABLE_PORCH_MY` | 5.1 × 3.6 × 0.86 | TB-LKTN porch gable |
| `DRAIN_HALFROUND_MY` | 0.23 × 1.0 × 0.115 | 1m drain segment |

### span_mm / depth_mm — Runtime vs Static

For **building-specific mesh types** (HIP_ROOF_MY, GABLE_PORCH_MY): `span_mm` and
`depth_mm` are extracted from 2D layout drawings and stored statically in
`lod_parametric_mesh_param`. The mesh reads them from the DB.

For **generic mesh types** (GABLE_ROOF_MY, GABLE_CANOPY_MY): `span_mm` and `depth_mm`
are **not** in the DB. They are injected at compile time from the ENVELOPE placement
bbox — which is itself computed from the building's room bounds (the 2D grid). So:

```
GRID axes/spacing  →  room bounds (minX, maxX, minY, maxY)
                    →  ENVELOPE placement bbox
                    →  MeshParameters.put("span_mm", (maxY-minY)*1000)
                    →  MeshParameters.put("depth_mm", (maxX-minX)*1000)
```

This is "infer span from 2D layout" — no hardcoded building dimensions in Java.

### Compiler Agnostic Direction (OPEN TODO)

Currently `BuildingWriter.resolveRoofGeometry()` checks
`orientation.startsWith("GABLE_")` and calls the hardcoded `writeGableGeometry()`.
This path bypasses the parametric mesh system. The `family_ref` in C_OrderLine
now records the intent (e.g., `HIP_ROOF_MY`) but the Java dispatch has not been refactored yet.

**Required Java change:** replace `writeGableGeometry()` with:
1. Read `family_ref` from placement → look up `lod_parametric_mesh.generator_class`
2. Load `lod_parametric_mesh_param` → build `MeshParameters`
3. Inject runtime dims: `span_mm`, `depth_mm` from placement bbox (for generic types)
4. Dispatch: `new GableRoofMesh()` / `new HipRoofMesh()` per `generator_class`
5. Write `MeshResult` to output DB

Same refactor unlocks `HalfRoundDrainMesh` for the perimeter drain (currently GEN-BOX).

### Drain Perimeter (OPEN TODO — blocked on Java refactor above)

`IfcSlab_drain_1` through `_8` are currently `ABSOLUTE + GEN-BOX (8v/12f)`.
Target: `BOUNDARY/PERIMETER + HalfRoundDrainMesh (68v/132f, LOD400 N=16)`.
`DRAIN_HALFROUND_MY` is registered in DB — only the Java dispatch path is missing.
Perimeter offset: 700mm from outer wall face (aligns with roof eave drip line).

## Relational Placement (Phase RM)

The compiler uses relational rules instead of flat coordinates for element placement.

### Placement Mode

Controlled by `ad_sysconfig.placement_mode`:
- `FLAT` — reads coordinates from `lod_element_placement` (legacy)
- `RELATIONAL` — computes coordinates from C_OrderLine + grid/room/wall metadata (current)

Toggle without code change: `UPDATE ad_sysconfig SET config_value='FLAT' WHERE config_key='placement_mode'`

### Relational Tables

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `ad_building_grid` | Structural grid lines per building | axis, line_ref, offset_mm |
| `ad_room_boundary` | Rooms mapped to grid cells | room_ref, grid_min_*, grid_max_* |
| `ad_wall_face` | Room boundary faces → wall type + adjacency | room_ref, face_direction, wall_type |
| `c_orderline` (C_OrderLine — Construction Order Details) | Element placement rules (host + position + family) | host_type, host_ref, position_rule, material_name |
| `ad_element_dependency` | Parent-child cascade chain | parent_ref, child_ref, dependency_type |

### Resolution Flow

```
c_orderline (what + where)
  → host_ref → ad_wall_face (which wall face)
    → room_ref → ad_room_boundary (which room)
      → grid cells → ad_building_grid (grid offsets)
        → COMPUTED coordinates (minX, maxX, minY, maxY, minZ, maxZ)
```

`RelationalResolver.java` implements this chain. Shadow validation confirms computed coords match flat oracle within 0.001mm.

## Validation (PlacementProver)

`PlacementProver.java` runs 14 mathematical proofs in 5 tiers after compilation:

| Tier | Proofs | Scope | Requires |
|------|--------|-------|----------|
| 1 | P01-P04 | Per-element arithmetic | Coordinates only |
| 2 | P05-P06 | Pairwise relations | Coordinates only |
| 3 | P07-P09 | Host-element containment | Relational metadata |
| 4 | P10-P12 | Topological closure | Wall face + room data |
| 5 | P13-P14 | Conservation laws | Grid + slab data |

The prover is **non-blocking** — it reports violations but never prevents emission. Score remains the arbiter.

Architectural boundary: `BoundElement` constructor = THE GATE (enforces mesh-fits-bbox). `PlacementProver` = THE AUDIT (reports anomalies).

## DAO Pattern (orm-core)

**Rule:** Use DAO for all new resolver code. Raw JDBC is legacy — permitted in production paths already committed, not in new code.

### Three modules, three roles

| Module | Package | When to use |
|--------|---------|-------------|
| `orm-core` | `com.bim.orm` | Shared PO base + query builder. Zero business logic. |
| `ORMSandbox` | `com.bim.ormsandbox` | Building inspector, preflight checks, standalone tools. |
| `TopologyMaker` | `com.bim.topology` | Typology/UBBL domain — its own PO layer. |

`DAGCompiler` may import `orm-core` (Phase 4c added this dep). It does NOT import ORMSandbox PO classes.

### How to use ModelQuery

```java
// Load all m_bom_line rows for a given BOM (conn = BOM.db)
List<X_M_BOMLine> children = new ModelQuery<>(conn, X_M_BOMLine::new, X_M_BOMLine.Table_Name)
    .where("bom_id = ?", bomId)
    .orderBy("sequence ASC")
    .list();

// Load a product_dim by product_id (conn = BOM.db)
X_AdProductDim dim = new ModelQuery<>(conn, X_AdProductDim::new, X_AdProductDim.Table_Name)
    .where("product_id = ?", productId)
    .first();  // returns null if not found
```

### PO naming convention

- `X_` prefix — plain PO (column getters/setters, no business logic)
- `M_` prefix — domain model (adds factory methods, lifecycle, validation)
- Table_Name constant: `X_M_BOMLine.Table_Name = "m_bom_line"` (must match actual table)
- PK field: TEXT PK must be set explicitly before `save()` — `BasePO.isNewRecord` flag determines INSERT vs UPDATE

### BasePO trap

`isNewRecord` is an explicit flag — not derived from PK presence. TEXT PKs are non-blank before `save()` but the row may not exist yet. Always set `isNewRecord = true` for new objects:

```java
X_M_BOMLine child = new X_M_BOMLine(conn);
child.setBomId("SOFA_AREA");
child.setSequence(1);
child.markAsNew();   // sets isNewRecord = true
child.save();        // → INSERT
```

### What BOMCascadeResolver needs

```java
// BOMTreeLoader — DAO-only, no JDBC
BOMNode loadTree(Connection conn, String rootBomId) {
    List<X_M_BOMLine> rows = new ModelQuery<>(conn, X_M_BOMLine::new, X_M_BOMLine.Table_Name)
        .where("bom_id = ?", rootBomId)
        .orderBy("sequence ASC").list();
    // recursively load child_bom_id subtrees
    ...
}
```

See `BOMTreeLoader.load()` (Phase G-1 Step 2) as the canonical working example — shared AD-layer tree loader used by both `BOMTierResolver` and `FloorPlateBOMResolver`.

---

## Where to Start

1. Read `USER_GUIDE.md` for DSL syntax and the four buildings
2. Run an E2E test (`SampleHouseEndToEndTest`), then query the output DB
3. Read `BuildingSpecs.java` — the 26 record types are the compiler's vocabulary
4. Read the relational tables: C_OrderLine (Construction Order Details), `ad_wall_face`, `ad_building_grid`
5. Add a simple BOM recipe (SQL only) and see it appear in output
6. Read `ARCHITECTURE.md` for the full theory
7. Read `CurrentState.txt` for known issues and architectural trade-offs
