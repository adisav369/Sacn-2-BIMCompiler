# BIM Intent Compiler — FOSS Developer's Guide

> **Version**: Phase 50+
> **License**: Open Source (FOSS)
> **Language**: Java 17 + Python 3.10 (IFC export)
> **Audit**: Watchdog-reviewed 2026-02-02

### PRIME RULE

**EXTRACT, DON'T IMAGINE.** Every constant in this guide carries a provenance tag:
- `[EXTRACTED: ...]` — Value measured from TERMINAL database or real drawings
- `[RESEARCHED: ...]` — Value from building codes or standards (cited)
- `[PENDING: ...]` — Estimated value awaiting verification

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [The DAG Compiler Engine](#2-the-dag-compiler-engine)
3. [Pipeline Stages](#3-pipeline-stages)
4. [Data Flow Diagram](#4-data-flow-diagram)
5. [Adding a New Building (DSL)](#5-adding-a-new-building-dsl)
6. [Configuration Files](#6-configuration-files)
7. [The Witness System](#7-the-witness-system)
8. [Output Formats](#8-output-formats)
9. [Determinism Guarantee](#9-determinism-guarantee)
10. [Key Classes Reference](#10-key-classes-reference)
11. [Developer Workflow](#11-developer-workflow)

**Appendices**
- [Appendix A: DSL Syntax Reference](#appendix-a-dsl-syntax-reference)
- [Appendix B: Example Buildings](#appendix-b-example-buildings)
- [Appendix C: Addon Framework](#appendix-c-addon-framework-recommended-for-vocabulary-additions)
- [Appendix D: Constant Reconciliation Notes](#appendix-d-constant-reconciliation-notes)
- [Appendix E: AD-Style Architecture Roadmap](#appendix-e-ad-style-architecture-roadmap)

---

## 1. Architecture Overview

The BIM Intent Compiler transforms **high-level building intent** (expressed in a domain-specific language) into **construction-ready BIM models**. The compiler follows a strict DAG (Directed Acyclic Graph) pipeline with no backtracking.

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   DSL       │───▶│   Parse     │───▶│  Compile    │───▶│   Output    │
│   Input     │    │   + Solve   │    │  + Place    │    │  DB + IFC   │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
     .bim              AST              Geometry          .db + .ifc
                   + Constraints        + MEP              + witness
```

### Core Principles

1. **Intent over Geometry**: Designers express *what* they want, not *how* to build it
2. **Constraint-Driven Layout**: Room positions derived from adjacency rules via CSP solver
3. **Witness-Verified Output**: Every compilation produces proof claims (witnesses)
4. **Configuration over Code**: Space types, MEP rules, and validation in YAML—no recompile needed

---

## 2. The DAG Compiler Engine

The compilation is a **5-stage DAG** where each stage's output feeds the next with no cycles or backtracking:

```
Stage 1: PARSE          DSL text → BuildingDefinition
         ↓
Stage 2: RESOLVE        BuildingDefinition → Solved GridPositions
         ↓
Stage 3: COMPILE        StoreyDef → StoreySpec (geometry)
         ↓
Stage 4: PLACE MEP      StoreySpec → MEP elements positioned
         ↓
Stage 5: WRITE          BuildingSpec → SQLite DB + Witness JSON + IFC
```

### DAG Properties

| Property | Implementation |
|----------|----------------|
| **Acyclic** | Each stage only reads from previous stages |
| **Deterministic** | Same input always produces same output |
| **Parallelizable** | Storeys compile independently (except vertical constraints) |
| **Incremental** | Geometry cached by hash in `base_geometries` table |

---

## 3. Pipeline Stages

### Stage 1: Parse (DSL → AST)

**Class**: `BuildingParser.java`
**Input**: `.bim` file (DSL text)
**Output**: `BuildingDefinition` record

The parser tokenizes DSL syntax and builds a typed AST:

```java
record BuildingDefinition(
    String name,
    BuildingType buildingType,      // SINGLE_UNIT or MULTI_UNIT
    List<StoreyDef> storeys,
    RoofDef roof,
    GridDef grid,                   // Structural grid (optional)
    ScheduleDef doorSchedule,       // Door types: D1, D2, etc.
    ScheduleDef windowSchedule,     // Window types: W1, W2, etc.
    String profile,                 // e.g., "Malaysian_Residential"
    String protocol,                // e.g., "Residential_Single_Storey"
    int lod,                        // Level of Detail: 100-500
    ConstructionSystem construction // FRAMED or MASONRY
)
```

### Stage 2: Resolve Constraints (CSP Solver)

**Class**: `SpaceSolver.java`
**Input**: `BuildingDefinition` with constraint annotations
**Output**: `Map<String, GridPosition>` (room name → x,y coordinates)

The solver uses the **Choco CSP library** to place rooms on an integer grid:

```
Constraints supported:
  adjacent: roomA roomB      → Rooms share an edge
  not_adjacent: roomA roomB  → Rooms don't touch
  exterior: north|south|east|west → Room touches building envelope
  aligns: roomA roomB        → Rooms share a wall line (multi-storey)
  above: roomA               → Room is directly above (vertical stacking)
```

**Relaxation Priority** (if unsolvable):
1. `NOT_ADJACENT` dropped first
2. `ALIGNS` dropped second
3. `EXTERIOR` dropped third
4. `ADJACENT` dropped last (most important)

### Stage 3: Compile Building (Geometry Generation)

**Class**: `BuildingCompiler.java`
**Input**: `BuildingDefinition` with solved positions
**Output**: `BuildingSpec` with full geometry

For each storey:
1. Convert grid positions to metric bounds (minX, maxX, minY, maxY)
2. Generate walls from room adjacency:
   - Interior walls: 150mm thickness `[EXTRACTED: TERMINAL wall panels]`
   - Exterior walls: 250mm thickness `[EXTRACTED: TERMINAL envelope walls]`
   - *Note: Malaysian_Residential profile may override to 100mm/150mm per UBBL*
3. Place doors at room connections per SCHEDULE and adjacency constraints
4. Place windows on exterior walls per SCHEDULE and exterior constraints
5. Generate slab: 150mm thickness `[RESEARCHED: UBBL 1984 minimum slab depth]`

```java
record StoreySpec(
    String name,
    double level,              // Z elevation (meters)
    double height,             // Floor-to-ceiling height
    List<WallSpec> walls,
    List<RoomSpec> rooms,
    List<DoorSpec> doors,
    List<WindowSpec> windows,
    List<FixtureSpec> fixtures,
    List<SprinklerSpec> sprinklers,
    List<LightSpec> lights,
    SlabSpec slab
)
```

### Stage 4: Place MEP Elements

**Classes**: `ElectricalPlacer`, `FixturePlacer`, `PlumbingPlacer`, `StructuralPlacer`

Each placer reads space type configuration from `spacetypes.yaml`:

| Placer | Places | Rules |
|--------|--------|-------|
| `ElectricalPlacer` | Lights, outlets, switches | Outlets: 300mm AFF, switches: 1200mm AFF `[RESEARCHED: MS IEC 60364 / IBC — values identical]` |
| `FixturePlacer` | Toilet, sink, exhaust | Clearances per fixture type, attached to walls `[RESEARCHED: MS 1184 accessibility]` |
| `PlumbingPlacer` | Waste stack, vent, supply | Vertical routing, diameter: 100mm waste, 50mm vent `[RESEARCHED: UBBL plumbing]` |
| `StructuralPlacer` | Columns, beams | Corners, T-junctions, spans > 8m `[EXTRACTED: TERMINAL G8 grid pattern]` |
| `SprinklerPlacer` | Sprinkler heads | Grid at 4.6m spacing `[RESEARCHED: NFPA 13 — internationally adopted]` |

### Stage 5: Write Output

**Class**: `BuildingWriter.java`
**Input**: `BuildingSpec`
**Output**: SQLite `.db` + `_witness.json`

Database schema (TERMINAL-compatible):

| Table | Purpose |
|-------|---------|
| `elements_meta` | Element definitions (guid, ifc_class, name, storey) |
| `elements_rtree` | Spatial index (bounding boxes) |
| `base_geometries` | Geometry cache (vertices, faces as BLOB) |
| `element_instances` | Instance → geometry hash mapping |
| `spatial_structure` | Building → Storey → Space hierarchy |
| `mep_nodes` | MEP system equipment nodes |
| `mep_edges` | MEP system connectivity edges |

---

## 4. Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        BIM INTENT COMPILER                              │
│                       DAG COMPILATION ENGINE                            │
└─────────────────────────────────────────────────────────────────────────┘

Input: *.bim (DSL file)
  │
  ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ STAGE 1: PARSE                                                          │
│ BuildingParser.parse(dslText)                                           │
│ Output: BuildingDefinition { name, storeys[], roof, grid, profile }     │
└──────────────────────────────────────────────────────────────────────────┘
  │
  ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ STAGE 2: RESOLVE CONSTRAINTS                                            │
│ SpaceSolver.solve(buildingDef)                                          │
│ - CSP model from adjacency/separation/exterior constraints              │
│ - Choco solver finds valid room positions                               │
│ - Relaxation if needed (drop constraints by priority)                   │
│ Output: Map<roomName, GridPosition(x, y)>                               │
└──────────────────────────────────────────────────────────────────────────┘
  │
  ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ STAGE 3: COMPILE GEOMETRY                                               │
│ BuildingCompiler.compile(buildingDef)                                   │
│ For each storey:                                                        │
│   - Grid → metric bounds                                                │
│   - Adjacency → walls (thickness per profile constants)                 │
│   - Place doors per SCHEDULE + adjacency constraints                    │
│   - Place windows per SCHEDULE + exterior constraints                   │
│   - Generate slab (thickness per profile)                               │
│ Output: StoreySpec[] with walls, rooms, doors, windows, slab            │
└──────────────────────────────────────────────────────────────────────────┘
  │
  ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ STAGE 4: PLACE MEP                                                      │
│ ElectricalPlacer, FixturePlacer, PlumbingPlacer, StructuralPlacer       │
│ - Read spacetypes.yaml for room-specific rules                          │
│ - Position per MS IEC 60364 / NFPA (see provenance in Section 3)        │
│ Output: Enhanced StoreySpec with fixtures, lights, pipes, structure     │
└──────────────────────────────────────────────────────────────────────────┘
  │
  ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ STAGE 5A: BUILD MEP SYSTEMS                                             │
│ BuildingCompiler.buildMEPSystems(storeys)                               │
│ - Aggregate MEP elements into system graphs                             │
│ Output: List<MEPSystem> { nodes[], edges[] }                            │
└──────────────────────────────────────────────────────────────────────────┘
  │
  ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ STAGE 5B: VALIDATE                                                      │
│ ValidatorChain.validate(buildingSpec)                                   │
│ - Profile rules (Malaysian_Residential, etc.)                           │
│ - Protocol rules (Residential_Single_Storey, etc.)                      │
│ Output: ValidationReport (critical failures block compilation)          │
└──────────────────────────────────────────────────────────────────────────┘
  │
  ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ STAGE 5C: WRITE DATABASE                                                │
│ BuildingWriter.write(buildingSpec, outputPath)                          │
│ - Create SQLite schema                                                  │
│ - Write elements with geometry hashes                                   │
│ - Build spatial hierarchy                                               │
│ Output: *.db (SQLite)                                                   │
└──────────────────────────────────────────────────────────────────────────┘
  │
  ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ STAGE 5D: WITNESS CLAIMS                                                │
│ WitnessBuilder.collect(buildingSpec)                                    │
│ - 24 proof claims (foundation, entry, windows, MEP, structure)          │
│ Output: *_witness.json                                                  │
└──────────────────────────────────────────────────────────────────────────┘
  │
  ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ STAGE 6: IFC EXPORT (offline, Python)                                   │
│ python export_building_to_ifc.py input.db output.ifc                    │
│ - Reads SQLite DB                                                       │
│ - Writes IFC4 file via IfcOpenShell                                     │
│ Output: *.ifc                                                           │
└──────────────────────────────────────────────────────────────────────────┘

Final Outputs:
  • *.db          (SQLite database, TERMINAL-compatible)
  • *_witness.json (24 proof claims)
  • *.ifc          (IFC4 BIM model)
```

---

## 5. Adding a New Building (DSL)

### Step 0: Declare Provenance

**Every DSL file must declare where its values come from.** This is the provenance gate.

```
BUILDING "MyBuilding"
    provenance: "TB-LKTN drawing set, Sheet A-101"  # Real building
    # OR
    provenance: "EXAMPLE — not from a real building"  # Tutorial
    # OR
    provenance: "RESEARCHED: JKR standard school plan, ref KKR.PK(S)-07"  # Standard
```

Without provenance, invented values propagate unchecked. The witnesses validate *geometric* correctness but not *provenance* correctness.

### Step 1: Create the DSL File

Create `examples/MyBuilding.bim`:

```
# ══════════════════════════════════════════════════════════════
# EXAMPLE VALUES — not from a real building plan
# For production use, extract dimensions from architectural drawings
# ══════════════════════════════════════════════════════════════
BUILDING "MyHouse"
    provenance: "EXAMPLE — tutorial only"
    profile: "Malaysian_Residential"
    protocol: "Residential_Single_Storey"
    lod: 300
{
    GRID {
        axes: A, B, C / 1, 2, 3
        spacing: 3.0, 3.5 / 2.5, 2.8   # EXAMPLE ONLY — extract from drawings
    }

    STOREY "Ground" level:0 height:2.8m {
        BEDROOM "master" bounds:A1-B2 {
            exterior: west
            adjacent: common
        }

        BATHROOM "bath" bounds:B1-B2 {
            adjacent: common
        }

        OPEN_PLAN "common" bounds:B2-C3 {
            exterior: south
            DOOR type:D1 wall:south
        }
    }

    ROOF pitch:25deg overhang:600mm
}
```

### Step 2: Verify Space Types Exist

Check `config/spacetypes.yaml` for required space types:

```yaml
BEDROOM:
  category: HABITABLE
  wall_rule: ENCLOSED
  validation:
    min_area: 9.0
    min_dimension: 2.4
    requires_window: true
  mep:
    electrical:
      light_points: 2
      power_points: 2
```

If adding a new space type (e.g., CLASSROOM), add it to the YAML—no Java recompile needed.

### Step 3: Compile

```bash
mvn exec:java \
  -Dexec.mainClass="com.bim.compiler.dsl.BuildingCompiler" \
  -Dexec.args="examples/MyBuilding.bim output/mybuilding.db"
```

### Step 4: Verify Witnesses

Check `output/mybuilding_witness.json`:

```json
{
  "building": "MyHouse",
  "summary": {
    "total_claims": 24,
    "proven": 17,
    "unprovable": 2
  },
  "claims": {
    "BEDROOM_WINDOW": { "status": "PROVEN" },
    "ENTRY_ACCESSIBLE": { "status": "PROVEN" }
  }
}
```

### Step 5: Export to IFC

```bash
python scripts/export_building_to_ifc.py \
  output/mybuilding.db \
  output/mybuilding.ifc
```

### Required Artifacts Checklist

| Artifact | Location | Config Only? | Notes |
|----------|----------|--------------|-------|
| DSL file | `examples/*.bim` | Yes | Building definition with provenance |
| Space types | `config/spacetypes.yaml` | Yes | Add YAML entries (no recompile) |
| Door schedule | In DSL | Yes | `SCHEDULE doors { D1: 900x2100 }` |
| Window schedule | In DSL | Yes | `SCHEDULE windows { W1: 1200x1200 }` |
| Library geometry | `library/component_library.db` | Yes | Pre-built 3D components |
| End-to-end test | `src/.../dsl/*EndToEndTest.java` | No (Code) | Regression test |
| New fixture type | `config/spacetypes.yaml` + `FixturePlacer.java` | **Config + Code** | YAML defines properties, Placer handles positioning |
| New MEP system | `config/` + new `*Placer.java` | **Config + Code** | Requires new Placer class |
| New witness claim | `WitnessBuilder.java` | No (Code) | Requires Java implementation |

**Configuration vs Code boundary**: Space type *values* (min_area, light_points) are configuration. The *placers* that interpret them are code. Adding a fundamentally new element type (not just a new space type) requires Placer support.

---

## 6. Configuration Files

### spacetypes.yaml

**Path**: `config/spacetypes.yaml`

Defines room types and their rules:

```yaml
CLASSROOM:
  category: HABITABLE
  omniclass: "13-31 11 11"            # [RESEARCHED: OmniClass Table 13]
  wall_rule: ENCLOSED
  validation:
    min_area: 48.0                     # [RESEARCHED: JKR school guidelines, 1.2m²/student × 40]
    min_dimension: 6.0                 # [RESEARCHED: JKR minimum classroom width]
    requires_window: true              # [RESEARCHED: UBBL natural daylighting requirement]
    requires_egress: true              # [RESEARCHED: UBBL escape route requirements]
  structural:
    structural_grid: true              # Enable beam/column placement
    beam_max_span: 8.0                 # [EXTRACTED: TERMINAL G8 floor-to-floor pattern]
  mep:
    electrical:
      light_points: 4                  # [RESEARCHED: MS IEC 60364 — 300 lux educational]
      power_points: 4                  # [RESEARCHED: MS IEC 60364 — corners + teacher wall]
      switch_points: 1                 # [RESEARCHED: single entry control]
    hvac:
      allows_aircon: true              # [RESEARCHED: MOE guidelines — optional for standard]
      ventilation_type: natural        # [RESEARCHED: JKR — cross-ventilation preferred]
```

**Provenance requirement**: Every numeric value in spacetypes.yaml must carry a comment with `[EXTRACTED: ...]`, `[RESEARCHED: ...]`, or `[PENDING: ...]`. Values without provenance tags fail code review.

### Categories

| Category | Examples | Characteristics |
|----------|----------|-----------------|
| HABITABLE | BEDROOM, LIVING, CLASSROOM | Requires windows, MEP |
| WET | BATHROOM, KITCHEN, TOILET | Plumbing fixtures |
| CIRCULATION | CORRIDOR, LOBBY, STAIR | Path connectivity |
| EXTERIOR | PORCH, CARPORT, BALCONY | Open air, no roof validation |

---

## 7. The Witness System

**Class**: `WitnessBuilder.java`

Witnesses are **proof claims** that verify design intent was achieved.

### Current Claims (25 total)

| # | Claim | Verifies |
|---|-------|----------|
| 1 | `FOUNDATION_LEVEL` | Foundation elevation correct |
| 2 | `ENTRY_ACCESSIBLE` | Entry door width meets accessibility |
| 3 | `ROOM_PATH` | All rooms reachable from entry |
| 4 | `BEDROOM_WINDOW` | All bedrooms have windows |
| 5 | `WINDOW_EXTERIOR` | Windows only on exterior walls |
| 6 | `ROOF_ENVELOPE` | Roof covers all rooms |
| 7 | `ROOM_CORNERS_CORRECT` | Room geometry matches bounds |
| 8 | `EXTERIOR_WALL_CONTAINMENT` | Exterior walls at envelope |
| 9 | `ELECTRICAL_CONTAINED` | Electrical within walls |
| 10 | `FIXTURE_ATTACHED` | Fixtures against walls |
| 11 | `FIXTURE_NOT_FLOATING` | No floating fixtures |
| 12 | `FIXTURE_NOT_CLASHING` | No fixture-structure clashes |
| 13 | `PLUMBING_PIPE_VALID` | Pipes exist for wet rooms |
| 14 | `WASTE_STACK_CONNECTED` | Waste system connected |
| 15 | `VENT_PIPE_EXTENDED` | Vent reaches roof |
| 16 | `WATER_SUPPLY_ACCESSIBLE` | Supply reaches fixtures |
| 17 | `ELECTRICAL_CIRCUITS` | Circuits per space type |
| 18 | `CIRCUIT_BREAKER_CAPACITY` | Breaker sizing correct |
| 19 | `ALIGNS_CONSTRAINT_SATISFIED` | Vertical alignment met |
| 20 | `CLASSROOM_DAYLIGHT` | Daylight ratio >= 5% |
| 21 | `TOILET_ACCESSIBLE` | Toilet door width |
| 22 | `CORRIDOR_CONNECTS_ALL` | Corridor reachability |
| 23 | `FIRE_TRAVEL_DISTANCE` | Exit distance <= 30m |
| 24 | `STRUCTURAL_GRID_COMPLETE` | Grid beams/columns present |
| 25 | `BEAM_SPAN_LIMIT` | No beam exceeds max span for construction type |

### Witness Output Format

```json
{
  "building": "TB-LKTN",
  "timestamp": "2025-02-02T10:30:00Z",
  "summary": {
    "total_claims": 24,
    "proven": 17,
    "unprovable": 2,
    "skipped": 5
  },
  "claims": {
    "BEDROOM_WINDOW": {
      "status": "PROVEN",
      "bedrooms_checked": ["master", "bed2", "bed3"],
      "all_have_windows": true
    }
  }
}
```

### Adding a New Witness Claim

1. Define the claim in `WitnessBuilder.java` **with provenance for the threshold**:
```java
// THRESHOLD must come from one of:
//   [EXTRACTED: TERMINAL measurement] — cite query
//   [RESEARCHED: Building code]       — cite clause
//   [PENDING: Estimated]              — mark for verification
//
// Example: Daylight factor threshold
// [RESEARCHED: UBBL educational daylighting ≥ 5%]
private static final double DAYLIGHT_THRESHOLD = 0.05;

public void claimMyFeature(String roomName, double measuredValue) {
    claims.put("MY_FEATURE", Map.of(
        "status", measuredValue >= DAYLIGHT_THRESHOLD ? "PROVEN" : "UNPROVABLE",
        "room", roomName,
        "value", measuredValue,
        "threshold", DAYLIGHT_THRESHOLD,
        "threshold_source", "UBBL educational daylighting requirement"  // REQUIRED
    ));
}
```

2. Call from compiler at appropriate stage
3. Document in witness specification
4. **Threshold without provenance fails code review**

---

## 8. Output Formats

### SQLite Database (`.db`)

TERMINAL-compatible schema for BIM viewers:

```sql
-- Element definitions
CREATE TABLE elements_meta (
    guid TEXT PRIMARY KEY,
    ifc_class TEXT,           -- e.g., 'IfcWallStandardCase'
    name TEXT,
    type TEXT,
    storey TEXT,
    discipline TEXT           -- ARCH, STRUCT, MEP
);

-- Spatial index
CREATE VIRTUAL TABLE elements_rtree USING rtree(
    guid,
    minX, maxX,
    minY, maxY,
    minZ, maxZ
);

-- Geometry cache
CREATE TABLE base_geometries (
    geometry_hash TEXT PRIMARY KEY,
    vertices BLOB,            -- Float array
    faces BLOB                -- Int array (triangle indices)
);

-- Instance-to-geometry mapping
CREATE TABLE element_instances (
    guid TEXT PRIMARY KEY,
    geometry_hash TEXT,
    FOREIGN KEY (geometry_hash) REFERENCES base_geometries
);

-- MEP system graph
CREATE TABLE mep_nodes (
    node_id TEXT PRIMARY KEY,
    system_type TEXT,         -- PLUMBING_WASTE, ELECTRICAL, etc.
    element_guid TEXT
);

CREATE TABLE mep_edges (
    from_node TEXT,
    to_node TEXT,
    system_type TEXT
);
```

### IFC File (`.ifc`)

IFC4 export via Python script:

```bash
python scripts/export_building_to_ifc.py input.db output.ifc
```

Entities exported:
- `IfcSite`, `IfcBuilding`, `IfcBuildingStorey`
- `IfcWallStandardCase`, `IfcDoor`, `IfcWindow`
- `IfcSpace` (rooms)
- `IfcStair`, `IfcRamp`
- `IfcFurniture` (fixtures)
- `IfcFlowTerminal` (plumbing, electrical)

### Witness JSON (`_witness.json`)

Machine-readable proof claims (see Section 7).

---

## 9. Determinism Guarantee

**The compiler is fully deterministic.** Same DSL input always produces identical output.

### Verified Deterministic Components

| Component | Implementation | Why Deterministic |
|-----------|----------------|-------------------|
| **GUIDs** | Name-based: `WALL_bedroom_Ground` | Derived from element names, not random |
| **Element ordering** | ArrayList throughout pipeline | Preserves insertion order |
| **HashMap iteration** | Sorted keys before iteration | Canonical ordering regardless of JVM |
| **CSP Solver (Choco)** | Pinned `inputOrderLBSearch` strategy | Version-independent search order |
| **Geometry generation** | Pure functions of coordinates | No randomness in calculations |

### Determinism Fixes Applied (Watchdog ruling 2026-02-02)

1. **ElectricalPlacer.java** — Circuit types sorted alphabetically before iteration
2. **SpaceSolver.java** — Search strategy explicitly pinned to `inputOrderLBSearch`
3. **pom.xml** — Choco version pinned with determinism warning comment

### Determinism Level

**What we guarantee:**
- **Functional determinism**: Geometry, topology, element IDs, relationships — identical every run
- **Database reproducibility**: `.db` files are byte-identical across runs (same machine)

**What varies:**
- **Witness timestamp**: `_witness.json` includes `generated` field with compilation time

| Item | Location | Impact |
|------|----------|--------|
| `Instant.now()` | `WitnessBuilder.java:851` | Timestamp in witness JSON — metadata only, geometry unaffected |
| `UUID.randomUUID()` | `IBuilder.java:26` | **NOT USED** in production pipeline (legacy/test only) |

**Reproducibility test**: To verify determinism, compile twice and diff:
```bash
# Compile twice
mvn exec:java -Dexec.mainClass="..." -Dexec.args="input.bim output1.db"
mvn exec:java -Dexec.mainClass="..." -Dexec.args="input.bim output2.db"

# Compare (excluding witness timestamp)
sqlite3 output1.db ".dump" | grep -v "^--" > dump1.sql
sqlite3 output2.db ".dump" | grep -v "^--" > dump2.sql
diff dump1.sql dump2.sql  # Should be empty
```

### GUID Generation Pattern

BuildingWriter uses deterministic name-based GUIDs:
```java
"WALL_" + wall.assemblyName() + "_" + storeyName
"SPACE_" + storey.name() + "_" + room.name()
"SPRINKLER_" + storey.name() + "_" + sprinkler.id()
```

This ensures:
- Diffing outputs between runs shows only real changes
- Version control of databases is meaningful
- Witness claims reference stable identifiers

### Under-Constrained Layouts

If DSL constraints don't fully specify room positions, the CSP solver picks the **first valid solution** in its search order. This is:
- **Reproducible**: Same constraints → same solution every time
- **But possibly unexpected**: Multiple valid arrangements exist; solver picks one deterministically

To ensure expected layouts, fully constrain room positions with `bounds:` or sufficient `adjacent:`/`exterior:` rules.

---

## 10. Key Classes Reference

### Parsing

| Class | File | Purpose |
|-------|------|---------|
| `BuildingParser` | `dsl/BuildingParser.java` | DSL tokenizer and parser |
| `BuildingDefinition` | `dsl/BuildingDefinition.java` | Parsed AST record |
| `StoreyDef` | `dsl/BuildingDefinition.java` | Storey definition |
| `RoomDef` | `dsl/BuildingDefinition.java` | Room definition with constraints |

### Solving

| Class | File | Purpose |
|-------|------|---------|
| `SpaceSolver` | `solver/SpaceSolver.java` | CSP constraint solver |
| `GridPosition` | `solver/SpaceSolver.java` | Solution: room x,y coordinates |
| `SolvedLayout` | `solver/SpaceSolver.java` | Full solution with metadata |

### Compiling

| Class | File | Purpose |
|-------|------|---------|
| `BuildingCompiler` | `dsl/BuildingCompiler.java` | Main compiler orchestrator |
| `BuildingSpec` | `dsl/BuildingCompiler.java` | Compiled building |
| `StoreySpec` | `dsl/BuildingCompiler.java` | Compiled storey geometry |
| `RoomCompiler` | `dsl/RoomCompiler.java` | Room geometry generator |

### MEP Placement

| Class | File | Purpose |
|-------|------|---------|
| `ElectricalPlacer` | `library/ElectricalPlacer.java` | Lights, outlets, switches |
| `FixturePlacer` | `library/FixturePlacer.java` | Bathroom/kitchen fixtures |
| `PlumbingPlacer` | `library/PlumbingPlacer.java` | Pipes and stacks |
| `StructuralPlacer` | `library/StructuralPlacer.java` | Columns and beams |

### Output

| Class | File | Purpose |
|-------|------|---------|
| `BuildingWriter` | `dsl/BuildingWriter.java` | SQLite database writer |
| `WitnessBuilder` | `witness/WitnessBuilder.java` | Proof claim collector |
| `DSLExporter` | `dsl/DSLExporter.java` | DSL round-trip export |

### Configuration

| Class | File | Purpose |
|-------|------|---------|
| `SpaceTypeRegistry` | `dsl/SpaceTypeRegistry.java` | Loads spacetypes.yaml |
| `BIMConstants` | `BIMConstants.java` | Threshold constants |

---

## 11. Developer Workflow

### Session Startup

1. Read `CLAUDE.md` (prime rules)
2. Read `PROGRESS.md` (current state)
3. Read relevant Java interfaces
4. Run witnesses to verify current state

### Making Changes

1. **Configuration change** (new space type):
   - Edit `config/spacetypes.yaml`
   - No recompile needed
   - Run tests to verify

2. **New feature**:
   - Write witness claim FIRST
   - Implement feature
   - Verify witness passes

3. **Bug fix**:
   - Write failing test
   - Fix bug
   - Verify test passes

### Running Tests

```bash
# Full test suite
mvn test

# Specific end-to-end test
mvn test -Dtest=TBLKTNEndToEndTest

# Sanity checker
mvn exec:java -pl tools/sanity-checker \
  -Dexec.mainClass="com.bim.tools.sanity.HouseSanityChecker" \
  -Dexec.args="output/tb_lktn.db"
```

### Session Closeout

Update `PROGRESS.md` with:
- What was done
- What's next
- Witness count if claims changed

---

## Appendix A: DSL Syntax Reference

```
BUILDING "<name>"
    profile: "<profile_name>"
    protocol: "<protocol_name>"
    lod: <100-500>
{
    GRID {
        axes: A, B, C / 1, 2, 3
        spacing: <x1>, <x2> / <y1>, <y2>
    }

    SCHEDULE doors {
        D1: <width>x<height> "<description>"
    }

    SCHEDULE windows {
        W1: <width>x<height> "<description>"
    }

    STOREY "<name>" level:<z> height:<h>m {
        <ROOM_TYPE> "<name>" bounds:<cell1>-<cell2> {
            exterior: <north|south|east|west>
            adjacent: <room_name>
            not_adjacent: <room_name>
            aligns: <room_name>
            above: <room_name>
            DOOR type:<type> wall:<direction>
            WINDOW type:<type> wall:<direction>
        }

        STAIR "<name>" bounds:<cell1>-<cell2> {
            connects: <lower_storey>, <upper_storey>
        }
    }

    ROOF pitch:<deg>deg overhang:<mm>mm
}
```

### Room Types (from spacetypes.yaml)

**Residential**: BEDROOM, BATHROOM, KITCHEN, LIVING, DINING, OFFICE, LAUNDRY, STORE
**Circulation**: CORRIDOR, LOBBY, STAIR, ELEVATOR
**Institutional**: CLASSROOM, ASSEMBLY_HALL, CANTEEN, STAFFROOM, TOILET_BLOCK
**Exterior**: PORCH, CARPORT, VERANDAH, BALCONY

---

## Appendix B: Example Buildings

### TB-LKTN (Single-storey house)

```
BUILDING "TB-LKTN"
    profile: "Malaysian_Residential"
    protocol: "Residential_Single_Storey"
    lod: 300
{
    STOREY "Ground" level:0 height:2.8m {
        BEDROOM "master" ... { exterior: west; adjacent: common }
        BATHROOM "bath" ... { adjacent: common }
        OPEN_PLAN "common" ... { exterior: south, north }
        BEDROOM "bed2" ... { adjacent: common }
        BEDROOM "bed3" ... { adjacent: common }
    }
    ROOF pitch:25deg overhang:600mm
}
```

### Sekolah-Kebangsaan (School)

```
BUILDING "Sekolah-Kebangsaan"
    profile: "Malaysian_Educational"
    protocol: "School_Primary"
    lod: 300
{
    GRID { axes: A-G / 1-5; spacing: 8.0 / 8.0 }

    STOREY "Ground" ... {
        CLASSROOM "class_1" ... { exterior: north }
        CLASSROOM "class_2" ... { adjacent: class_1 }
        CORRIDOR "main_corridor" ... { connects: class_1, class_2, ... }
        ASSEMBLY_HALL "hall" ... { exterior: south }
        TOILET_BLOCK "toilets" ... { adjacent: corridor }
    }
}
```

---

## Appendix C: Addon Framework (Recommended for Vocabulary Additions)

For non-trivial vocabulary additions (new space types, new building typologies), use the **Addon Framework** instead of directly editing spacetypes.yaml.

An addon is a YAML manifest that declares a vocabulary extension across all six enrichment layers:

1. **Vocabulary** — SpaceType definition with provenance
2. **Component Activation** — TERMINAL library mapping
3. **MEP Rules** — Electrical, plumbing, HVAC configuration
4. **Witness Claims** — Required proofs for this space type
5. **Profile Overrides** — Jurisdiction-specific rules
6. **Spatial Patterns** — Typical adjacencies and groupings

**Benefits**:
- Structurally enforces provenance (validation rejects values without tags)
- Ensures vocabulary completeness across all layers
- Declares attendant elements (CLASSROOM requires CORRIDOR)
- Supports gap declaration (PENDING items tracked, not hidden)

See `docs/foss-guide-audit-addon-framework.md` for the full specification.

**Example addon structure**:
```
addons/
  educational/
    classroom.addon.yaml
    assembly_hall.addon.yaml
  residential/
    bedroom.addon.yaml
    bathroom.addon.yaml
  _index.yaml              # Registry with activation status
```

The addon framework sits above the config layer — it generates/validates spacetypes.yaml entries without touching the engine.

---

## Appendix D: Constant Reconciliation Notes

Some constants in this guide may differ from `docs/GLOSSARY.md` due to profile-dependency:

| Constant | FOSS Guide | Glossary | Resolution |
|----------|------------|----------|------------|
| Interior wall | 150mm | 100mm | Profile-dependent: TERMINAL uses 150mm (concrete), Malaysian residential uses 100mm (brick partition) |
| Exterior wall | 250mm | 150mm | Profile-dependent: TERMINAL uses 250mm (marine terminal), Malaysian residential uses 150mm (standard) |

**Rule**: The `profile:` declaration in the DSL determines which constants apply. When in doubt, check `BIMConstants.java` for the profile-specific override chain.

---

## Appendix E: AD-Style Architecture Roadmap

The BIM Compiler is evolving toward an **Application Dictionary (AD) style** architecture similar to iDempiere's metadata-driven design. This section documents the current state and planned evolution.

### Current State

```
config/
├── spacetypes.yaml    ← AD_SpaceType equivalent (RICH - 670+ lines)
└── [nothing else]

src/ (constants scattered)
├── BIMConstants.java          ~30 constants (tolerance, offsets, grid)
├── StoreyConvention.java      ~10 constants (floor heights, MEP zones)
├── OpeningConstraints.java    ~6 constants (door/window ratios)
├── StructuralPlacer.java      ~6 constants (lintel, column, beam limits)
├── FixturePlacer.java         ~5 constants (clearances)
└── ... more scattered
```

**What works well**: `spacetypes.yaml` is effectively the "AD_SpaceType" table — 670+ lines of room type definitions with validation rules, MEP requirements, and provenance tags. No Java recompile needed to add room types.

**What needs externalization**: Construction-specific constants (beam span limits, lintel thresholds) are still hardcoded in Java Placer classes.

### Target State (AD-Style)

```
config/
├── spacetypes.yaml                ← EXISTS (mature)
├── construction_profiles/         ← NEW
│   ├── masonry.yaml              (MAX_BEAM_SPAN: 8.0, lintel rules)
│   └── framed.yaml               (MAX_BEAM_SPAN: 10.0)
├── structural_rules.yaml         ← NEW
├── electrical_rules.yaml         ← NEW
├── plumbing_rules.yaml           ← NEW
├── witness_claims.yaml           ← NEW (claim #1-25 definitions)
└── building_codes/               ← NEW
    ├── ubbl_2012.yaml
    ├── ms_1195.yaml
    └── irc_2021.yaml
```

### iDempiere AD Parallel

| iDempiere AD | BIM Compiler Equivalent | Status |
|--------------|-------------------------|--------|
| `AD_Window`, `AD_Tab`, `AD_Field` | N/A (no UI) | Not applicable |
| `AD_Val_Rule` | `spacetypes.yaml` validation block | ✓ Complete |
| `AD_Reference` | OmniClass codes in spacetypes | ✓ Complete |
| `AD_Process` | Witness claims | Hardcoded |
| `AD_ModelValidator` | Placer constraint constants | Hardcoded |

### Timing Recommendation

**Wait until Phase 55+ completion before major AD refactor.**

| Factor | Assessment |
|--------|------------|
| Constants scattered | Yes — getting messy |
| spacetypes.yaml mature | Yes — 670 lines, solid schema |
| Core features complete | No — still adding (Phase 51 just added beam spans) |
| Pattern visibility | Not yet — only 3 example buildings |
| Addon framework spec | Exists — ready when needed |

**Recommended sequence**:
1. Complete Phase 55 (stair grid position bug fix)
2. Add 1-2 more building examples (duplex, shophouse)
3. Patterns become clearer
4. THEN refactor constants to YAML

**Why wait**: The risk of premature externalization is designing a YAML schema, then realizing the next phase needs a different structure. Better to let dust settle from Phase 50-55's rapid feature additions.

### The Vision: Hybrid AI-Assisted Development

Once AD-style configuration is complete, the system enables rapid AI-assisted development:

```
User: "Add beam span limit check for FRAMED construction at 10m"
         ↓
AI edits config/construction_profiles/framed.yaml (~50 tokens)
         ↓
Compiler uses new limit (no recompile)
         ↓
Witness verifies: BEAM_SPAN_LIMIT PROVEN
         ↓
Package + Deploy
```

This mirrors the iDempiere modernization vision: **YAML as the hot-swappable intent layer**, deterministic compilation downstream, witnesses prove correctness. The AI handles the YAML editing; no complex rules engine runtime needed.

**Key insight**: For BIM (greenfield), we're BUILDING the rules into configuration. For iDempiere (brownfield), we'd EXTRACT existing rules to configuration. Both converge on the same architecture.

---

*Document generated from BIM Intent Compiler codebase analysis.*
*Watchdog-audited 2026-02-02 — provenance gates added per findings 1-8.*
