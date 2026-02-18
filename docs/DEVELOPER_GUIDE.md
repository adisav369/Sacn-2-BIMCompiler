# Developer Guide

Expert-level onboarding. Assumes you know Java, SQL, and BIM concepts.

**Updated:** February 2026 (Post Phase RM-4 / Unified Path)

## The Machine

```
IFC source  →  Extract  →  Reference DB  →  placement_extractor  →  ad_element_placement
                                          →  material_extractor   →  (positions + materials)
                                                                           ↓
DSL text  →  Parser  →  Records  →  Compiler  →  Writer  →  SQLite DB (output)
              │                       │              │            │
              │                   reads from      writes to   includes:
              │                       │              │         material_name
              └── component_library.db ──────────────┘         material_rgba
                  (metadata + geometry + placement)
```

**DSL** = catalog selector (building type + overrides). **Metadata** = design catalog (BOM, rooms, MEP, clearances, materials). **Java** = resolver (no changes for new variants).

Source code lives in `DAGCompiler/src/main/java/com/bim/compiler/`. Build with `mvn compile -q`. Run E2E tests with `mvn exec:java -pl DAGCompiler -Dexec.mainClass="..." -q`.

## DAG Pipeline

| Stage | Class | Input | Output |
|-------|-------|-------|--------|
| Parse | `BuildingParser` | `.bim` text | `BuildingDefinition` (records) |
| Validate | `BuildingCompiler` | Definition | Constraint-checked definition |
| Compile | `StoreyCompiler` | Per-storey specs | Room geometries, walls, openings |
| Multi-unit | `MultiUnitCompiler` | Unit blocks | Merged storey with party walls |
| Place | `PlacementAD` + `StoreyCompiler` | Placement metadata | Elements at exact reference positions + materials |
| Place (BOM) | `FixturePlacer`, `FurnitureBOMResolver`, `MEPWriter` | Room bounds | Fixture/furniture/MEP positions |
| Write | `BuildingWriter` → sub-writers | All specs | `DAGCompiler/lib/output/*.db` (SQLite) |
| Witness | `WitnessGenerator` | Output DB | `*_witness.json` proof claims |

Every stage reads metadata from `library/component_library.db` via JDBC. No stage invents values.

## Key Files

```
DAGCompiler/src/main/java/com/bim/compiler/
├── dsl/
│   ├── BuildingSpecs.java        # 26 record types (RoomSpec, WallSpec, SlabSpec, etc.)
│   ├── BuildingCompiler.java     # Entry points, validation
│   ├── StoreyCompiler.java       # Walls, openings, stairs per storey + placement overrides
│   ├── MultiUnitCompiler.java    # Multi-unit layout, party walls
│   ├── PlacementAD.java          # Reads ad_element_placement (positions + materials)
│   ├── BuildingWriter.java       # Write orchestrator (schema + global emission)
│   ├── ElementPersistence.java   # Element write (10 columns incl. material_name, material_rgba)
│   ├── MEPWriter.java            # MEP/fixture writer (passes material to output)
│   ├── OpeningWriter.java        # Door/window writer
│   ├── StructuralWriter.java     # Column/beam writer
│   ├── StairWriter.java          # Stair writer
│   ├── WitnessGenerator.java     # Witness claim proofs
│   ├── CompilerConfig.java       # Config reader (placement_mode, etc.)
│   ├── RelationalResolver.java   # Relational placement engine (RM-2+)
│   ├── ExteriorRuleAD.java       # Exterior wall rule lookup
│   ├── BoundElement.java         # Proof-carrying type (mesh fits bbox)
│   ├── MeshBinder.java           # Bind library mesh to element (scale + validate)
│   └── *EndToEndTest.java        # E2E tests for 3 Rosetta Stones + TB-LKTN
├── library/
│   ├── ComponentLibrary.java     # LOD400 component lookup
│   ├── FurnitureBOMResolver.java # Room furniture from ad_bom tree
│   ├── ManifestResolver.java     # Assembly face clearances
│   └── FixturePlacer.java        # Bathroom/kitchen fixtures (IPC clearances)
├── validation/
│   ├── PlacementProver.java      # 14 proofs in 5 tiers (non-blocking audit)
│   ├── SpatialDigest.java        # Spatial fingerprint hash
│   └── GeometryIntegrityChecker.java  # Vertex-to-bbox + mesh topology validation
└── geometry/
    ├── Point3D.java, BoundingBox.java
    └── (geometry primitives)
```

## The Component Library (component_library.db)

Everything the compiler knows about construction lives here. Two distinct layers:

```
component_library.db
│
├── LAYER 1: GEOMETRY (from Python extraction scripts)
│   ├── base_geometries        8,766 meshes (vertices/faces BLOBs, deduplicated by hash)
│   ├── component_definitions  8,766 defs (name, bounds, orientation, attachment)
│   └── component_types        21 IFC class categories
│   Source: extract_all_components.py, import_ifc_furniture.py, extract_duplex_components.py
│   Question answered: "What does a toilet/door/sprinkler LOOK like?"
│
└── LAYER 2: METADATA (from SQL migration scripts, hand-curated)
    ├── ad_bom + ad_bom_child + ad_bom_child_param   Assembly recipes + spatial offsets
    ├── ad_space_type + ad_room_slot                  Room type → assembly dispatch
    ├── ad_wall_type + ad_opening_family              Wall thickness, opening depth rules
    ├── ad_assembly_manifest                          Per-face clearances
    ├── ad_building_template + ad_unit_type_room      Building/unit type definitions
    ├── ad_building_grid + ad_room_boundary              Structural grid + room-to-grid mapping
    ├── ad_wall_face + ad_element_rule + ad_element_dependency  Relational placement (Phase RM)
    └── ... (55 ad_* tables total)
    Source: migration/migration_108B.sql through migration_RM6*.sql (all idempotent)
    Question answered: "How do toilet + vanity + grab bar ASSEMBLE?
                        Which wall type goes where? What clearance per face?"
```

Layer 1 is extracted from real IFC files (federation DB + residential IFCs). Layer 2 is curated from standards, reference patterns, and Rosetta Stone observations. The compiler reads both at runtime — Layer 1 provides the mesh, Layer 2 tells it where and how to place it.

The critical `ad_*` tables:

| Table | What it does | Rows |
|-------|-------------|------|
| `ad_bom` | BOM recipe headers (22 active) | Assembly ID, group_by, is_active |
| `ad_bom_child` | BOM children (82 active) | Role, name_pattern, sequence |
| `ad_bom_child_param` | Child parameters (214) | Spatial offsets, z_rules, wall rules |
| `ad_building_template` | Building types (9) | CONDO_MID, LANDED_1S, etc. |
| `ad_unit_type_room` | Room layouts per unit | Fractional coordinates |
| `ad_space_type` | Room type definitions (37) | Category, wall rules |
| `ad_assembly_manifest` | Face clearances (35) | CLEARANCE, WALL_BACK per face |
| `ad_room_slot` | Room→assembly mapping (25) | Slot priority, required flag |
| `ad_wall_type` | Wall thickness rules (13) | Profile→thickness→material |
| `ad_opening_family` | Opening dimensions (295) | Width, height, depth per family |
| `component_definitions` | LOD400 geometry refs (8,766) | Bounds, orientation, hash |

## BOM Pattern (How Assemblies Work)

A BOM recipe = parent assembly + ordered children. Each child has a name pattern (matches `component_definitions`) and spatial params.

```
BED_SET (ad_bom)
├── seq 1: BED       name_pattern="Bed_Queen"    back_to_wall=true
└── seq 2: SIDE_TABLE name_pattern="Side_Table"   dx=0.98
```

The resolver loads the tree, finds matching components by name pattern, applies offsets. No geometry invented — everything comes from the library.

### Adding a BOM Recipe (by hand)

**Example:** Add a STUDY_DESK_SET with desk + lamp.

```sql
-- 1. Create the BOM header
INSERT INTO ad_bom (bom_id, bom_type, group_by, is_active)
VALUES ('STUDY_DESK_SET', 'ASSEMBLY', 'ROOM', 1);

-- 2. Add children (sequence = placement order)
INSERT INTO ad_bom_child (bom_id, role, child_name_pattern, sequence, is_active)
VALUES ('STUDY_DESK_SET', 'DESK', 'Desk%', 1, 1);

INSERT INTO ad_bom_child (bom_id, role, child_name_pattern, sequence, is_active)
VALUES ('STUDY_DESK_SET', 'LAMP', 'Light_Desk%', 2, 1);

-- 3. Add spatial params (get bom_child_id from step 2)
-- Desk: against back wall
INSERT INTO ad_bom_child_param (bom_child_id, param_key, param_value)
VALUES (LAST_INSERT_ROWID(), 'back_to_wall', 'true');

-- Lamp: offset from desk center
INSERT INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bom_child_id, 'dx', '0.4'
FROM ad_bom_child WHERE bom_id='STUDY_DESK_SET' AND role='LAMP';
```

**Verify:** `SELECT * FROM ad_bom_child WHERE bom_id='STUDY_DESK_SET';`

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
    component_library.db: ad_element_placement.material_name, ad_element_placement.material_rgba
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

**Trap:** IFC exports assign `material_name = 'Window Frame'` to IfcWindow (the frame, not the glass pane). Migration RM6 fixes this to `'Glass'` in both `ad_element_placement` and `ad_element_rule`.

### Running the Extractor

```bash
# Step 1: Enrich reference DB from IFC source
python3 DAGCompiler/tools/material_extractor.py \
    --ifc DAGCompiler/lib/input/Ifc4_SampleHouse.ifc \
    --ref DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db

# Step 2: Copy materials from reference DB → ad_element_placement
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

```bash
# From project root — always /home/red1/bim-compiler
mvn compile -q                    # Compile all modules

# Rosetta Stone E2E tests (the 3 primary validation targets)
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.SampleHouseEndToEndTest" -q
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNDuplexEndToEndTest" -q
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.TerminalEndToEndTest" -q

# Spatial fidelity check (output vs reference)
python3 tools/spatial_checker.py \
  DAGCompiler/lib/output/ifc4_sample_house.db \
  DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db \
  --discipline ARC

# Positional check (stricter, per-element position matching)
python3 tools/spatial_checker.py \
  DAGCompiler/lib/output/ifc4_sample_house.db \
  DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db \
  --discipline ARC --positional
```

Note: `-pl DAGCompiler` is required since source is in the DAGCompiler module.

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
- `ad_geometry_map` ordinals: SH uses GLOBAL ordinals (renumbered), DX uses per-class-per-storey (rank-based lookup)
- `ComponentLibrary.resolveGeometryByInstance()` has TWO lookup strategies: direct ordinal, then rank-based fallback
- Shadow validator matches by placement_id = ordinal — renumber geometry_map to match, never element_rule
- R*Tree uses float32 rounding — use `struct.pack('f')` in Python, don't cast all to float in Java
- OpeningWriter distorts bbox — post-write fixup needed
- TB-LKTN compilation relies entirely on PlacementAD — StructuralWriter doesn't fire (0 compiled walls)
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
                                                  ├─→ component_library.db
  SampleHouse IFC ───→ import_ifc_furniture.py ───┤     ┌─────────────────┐
  (Ifc4, UK house)      furniture families        │     │ base_geometries  │ ← Layer 1 (extracted)
                                                  │     │ component_defs   │
  Duplex IFCs ──┬───→ import_ifc_furniture.py ────┤     │─────────────────│
  (Ifc2x3, US)  │      furniture (Phase 109)      │     │ 55 ad_* tables:  │ ← Layer 2 (curated)
                 └──→ extract_duplex_components.py ┘     │  ad_bom          │
                        MEP fixtures (Phase 114)         │  ad_space_type   │
                                                         │  ad_wall_type    │
                                                         │  ad_opening_fam  │
  Standards ─────────→ migration_108B..119D.sql ────────→│  ad_room_slot    │
  Rosetta findings        (hand-curated,                 │  ad_unit_type    │
  Building codes          idempotent)                    │  ...             │
                                                         └─────────────────┘
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
                    placement_extractor.py ─→ ad_element_placement (positions + materials)
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

All scripts use `INSERT OR IGNORE` on `geometry_hash` — identical meshes are deduplicated. Result: **8,766 component definitions** with LOD400 geometry.

### Layer 2: Metadata (SQL migrations → component_library.db)

**Purpose:** "How do things ASSEMBLE? Which wall type goes WHERE?" — curated construction knowledge.

Source: `migration/migration_108B.sql` through `migration_119D.sql` (all idempotent). Written by hand from building codes, IPC standards, Rosetta Stone observations, and engineering judgement.

Examples of what Layer 2 encodes:
- `ad_bom`: BED_SET = bed + side_table, with dx=0.98m offset
- `ad_wall_type`: EXTERIOR + UK_Residential profile → 290mm brick
- `ad_opening_family`: D_EXT_DBL → 1860x2110mm, depth 200mm
- `ad_room_slot`: BATHROOM → BATHROOM_SET assembly at priority 1
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
| TB-LKTN | *None (generative)* | *None* | 58 | N/A (generative) |

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

## Relational Placement (Phase RM)

The compiler uses relational rules instead of flat coordinates for element placement.

### Placement Mode

Controlled by `ad_compiler_config.placement_mode`:
- `FLAT` — reads coordinates from `ad_element_placement` (legacy)
- `RELATIONAL` — computes coordinates from `ad_element_rule` + grid/room/wall metadata (current)

Toggle without code change: `UPDATE ad_compiler_config SET config_value='FLAT' WHERE config_key='placement_mode'`

### Relational Tables

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `ad_building_grid` | Structural grid lines per building | axis, line_ref, offset_mm |
| `ad_room_boundary` | Rooms mapped to grid cells | room_ref, grid_min_*, grid_max_* |
| `ad_wall_face` | Room boundary faces → wall type + adjacency | room_ref, face_direction, wall_type |
| `ad_element_rule` | Element placement rules (host + position + family) | host_type, host_ref, position_rule, material_name |
| `ad_element_dependency` | Parent-child cascade chain | parent_ref, child_ref, dependency_type |

### Resolution Flow

```
ad_element_rule (what + where)
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

## Where to Start

1. Read `USER_GUIDE.md` for DSL syntax and the four buildings
2. Run an E2E test (`SampleHouseEndToEndTest`), then query the output DB
3. Read `BuildingSpecs.java` — the 26 record types are the compiler's vocabulary
4. Read the relational tables: `ad_element_rule`, `ad_wall_face`, `ad_building_grid`
5. Add a simple BOM recipe (SQL only) and see it appear in output
6. Read `ARCHITECTURE.md` for the full theory
7. Read `CurrentState.txt` for known issues and architectural trade-offs
