# Developer Guide

Expert-level onboarding. Assumes you know Java, SQL, and BIM concepts.

## The Machine

```
DSL text  →  Parser  →  Records  →  Compiler  →  Writer  →  SQLite DB
              │                       │              │
              │                   reads from      writes to
              │                       │              │
              └── component_library.db ──────────────┘
                  (metadata + geometry)        (output model)
```

**DSL** = catalog selector (building type + overrides). **Metadata** = design catalog (BOM, rooms, MEP, clearances). **Java** = resolver (no changes for new variants).

## DAG Pipeline

| Stage | Class | Input | Output |
|-------|-------|-------|--------|
| Parse | `BuildingParser` | `.bim` text | `BuildingDefinition` (records) |
| Validate | `BuildingCompiler` | Definition | Constraint-checked definition |
| Compile | `StoreyCompiler` | Per-storey specs | Room geometries, walls, openings |
| Multi-unit | `MultiUnitCompiler` | Unit blocks | Merged storey with party walls |
| Place | `FixturePlacer`, `FurnitureBOMResolver`, `MEPWriter` | Room bounds | Fixture/furniture/MEP positions |
| Write | `BuildingWriter` → sub-writers | All specs | `output/*.db` (SQLite) |
| Witness | `WitnessGenerator` | Output DB | `*_witness.json` proof claims |

Every stage reads metadata from `library/component_library.db` via JDBC. No stage invents values.

## Key Files

```
src/main/java/com/bim/compiler/
├── dsl/
│   ├── BuildingSpecs.java        # 26 record types (RoomSpec, WallSpec, etc.)
│   ├── BuildingCompiler.java     # Entry points, validation
│   ├── StoreyCompiler.java       # Walls, openings, stairs per storey
│   ├── MultiUnitCompiler.java    # Multi-unit layout, party walls
│   ├── WitnessGenerator.java     # Witness claim proofs
│   ├── BuildingWriter.java       # Write orchestrator
│   └── (sub-writers)             # ElementPersistence, StructuralWriter, etc.
├── library/
│   ├── ComponentLibrary.java     # LOD400 component lookup
│   ├── FurnitureBOMResolver.java # Room furniture from ad_bom tree
│   ├── ManifestResolver.java     # Assembly face clearances
│   └── FixturePlacer.java        # Bathroom/kitchen fixtures (IPC clearances)
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
    └── ... (41 ad_* tables total)
    Source: migration/migration_108B.sql through migration_119D.sql (all idempotent)
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
mvn compile -q                    # Compile main
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.CondoMidEndToEndTest" -q  # Condo E2E

# All 7 E2E tests
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.SampleHouseEndToEndTest" -q
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.SchoolEndToEndTest" -q
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTN2SEndToEndTest" -q
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNCompactEndToEndTest" -q
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNDuplexEndToEndTest" -q

# Sanity checker (separate POM)
mvn -f tools/sanity-checker/pom.xml compile -q
mvn -f tools/sanity-checker/pom.xml exec:java \
  -Dexec.mainClass="com.bim.tools.sanity.HouseSanityChecker" \
  -Dexec.args="output/condo_mid.db" -q
```

## Output DB Schema

The compiler writes to SQLite. Key tables:

| Table | Content |
|-------|---------|
| `spatial_structure` | Project → Site → Building → Storey hierarchy |
| `elements_meta` | Every element: guid, ifc_class, name, storey, discipline |
| `elements_rtree` | Spatial index: id, minX, maxX, minY, maxY, minZ, maxZ |
| `base_geometries` | Vertices/faces BLOBs (float32/int32 arrays) + hash |
| `assembly_components` | BOM parent-child relationships |
| `mep_systems` / `system_nodes` / `system_edges` | MEP system graph |
| `simple_qto` | Quantity takeoff (area, volume, length) |

Query example:
```sql
-- All toilets on Ground floor
SELECT e.element_name, r.minX, r.maxX, r.minY, r.maxY
FROM elements_meta e
JOIN elements_rtree r ON e.id = r.id
WHERE e.ifc_class = 'IfcFlowTerminal'
  AND e.element_name LIKE 'Toilet%'
  AND e.storey = 'Ground';
```

## Traps

- `elements_rtree` columns: id, minX, **maxX**, minY, **maxY**, minZ, **maxZ** (NOT interleaved min/max pairs)
- Walls stored as `IfcPlate` (not IfcWall) — SQL must include IfcPlate
- `findByName("ChairDesk")` fails — must pass `%ChairDesk%` with wildcards
- `component_definitions.orientation` can be NULL → `valueOf(null)` throws NPE
- BOM role names must match writer constants: `BRANCH` not `FP_BRANCH`
- World-space geometry: all elements at zero transforms (Pattern B). No transform stacking.

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
  (Ifc2x3, US)  │      furniture (Phase 109)      │     │ 41 ad_* tables:  │ ← Layer 2 (curated)
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
  examples/*.bim → Parser → Compiler → Writer ──────→ output/*.db
                                                               │
                                                               ↓ (compares)
  SampleHouse IFC ──→ populate_sample_house_db.py ──→ reference/rosetta/*.db
  Duplex IFCs ──────→ populate_duplex_db.py ────────→ reference/rosetta/*.db
  Federation DB ────→ (proposed: unified extract.py) → reference/rosetta/*.db
                           │
                           ↓
                      spatial_checker.py ──→ X-ray fidelity scores
                      rosetta_dictionary.py → spatial skeleton text
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

### Rosetta Stone Pairs (Current)

| Stone | IFC Source | Layer 1 (geometry)? | Layer 3 (reference)? | Disciplines |
|-------|-----------|--------------------|--------------------|-------------|
| Federation (SJTII Terminal) | 8 IFC files, 51K elements | **Yes** — primary source | **Active** (15,104 elem) | ARC, STR, FP, ACMV, CW, SP, ELEC, LPG |
| SampleHouse | Ifc4_SampleHouse.ifc, 55 elem | **Yes** — furniture | **Active** (55 elem) | ARC only |
| Duplex | Ifc2x3_Duplex ARC+MEP, 1085 elem | **Yes** — furniture + MEP | **Active** (1,085 elem) | ARC + MEP |

## Where to Start

1. Read a `.bim` file in `examples/` to understand DSL syntax
2. Run an E2E test, then query the output DB to see what was produced
3. Read `BuildingSpecs.java` — the 26 record types are the compiler's vocabulary
4. Add a simple BOM recipe (SQL only) and see it appear in output
5. Read `ARCHITECTURE.md` for the full theory
