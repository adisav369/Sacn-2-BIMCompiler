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

## The Metadata (component_library.db)

Everything the compiler knows about construction lives here. 41 `ad_*` tables. The critical ones:

| Table | What it does | Rows |
|-------|-------------|------|
| `ad_bom` | BOM recipe headers (20 active) | Assembly ID, group_by, is_active |
| `ad_bom_child` | BOM children (82 active) | Role, name_pattern, sequence |
| `ad_bom_child_param` | Child parameters (214) | Spatial offsets, z_rules, wall rules |
| `ad_building_template` | Building types (9) | CONDO_MID, LANDED_1S, etc. |
| `ad_unit_type_room` | Room layouts per unit | Fractional coordinates |
| `ad_space_type` | Room type definitions | Category, wall rules |
| `ad_assembly_manifest` | Face clearances (35) | CLEARANCE, WALL_BACK per face |
| `ad_room_slot` | Room→assembly mapping (21) | Slot priority, required flag |
| `component_definitions` | LOD400 geometry refs (8,763) | Bounds, orientation, hash |

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

## Build & Test

```bash
# From project root — always /home/red1/bim-compiler
mvn compile -q                    # Compile main
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.CondoMidEndToEndTest" -q  # Condo E2E

# All 6 E2E tests
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

## Where to Start

1. Read a `.bim` file in `examples/` to understand DSL syntax
2. Run an E2E test, then query the output DB to see what was produced
3. Read `BuildingSpecs.java` — the 26 record types are the compiler's vocabulary
4. Add a simple BOM recipe (SQL only) and see it appear in output
5. Read `ARCHITECTURE.md` for the full theory
