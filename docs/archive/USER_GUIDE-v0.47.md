# BIM Compiler User Guide

**Version:** 0.47.0
**Updated:** February 2025

## Table of Contents
1. [Quick Start](#quick-start)
2. [Building and Running](#building-and-running)
3. [DSL Syntax Reference](#dsl-syntax-reference)
4. [Available Room Types](#available-room-types)
5. [Profiles and Building Codes](#profiles-and-building-codes)
6. [Output Formats](#output-formats)
7. [Witness System](#witness-system)
8. [MEP System Queries](#mep-system-queries)
9. [Sanity Checker Tool](#sanity-checker-tool)
10. [Configuration and Extensibility](#configuration-and-extensibility)
11. [Complete Example](#complete-example)
12. [Troubleshooting](#troubleshooting)

---

## Quick Start

### Minimal Example

Create a simple building DSL file (e.g., `my_house.dsl`):

```
BUILDING "My House" {

    STOREY "Ground" level:0 height:2.8m {

        BEDROOM "master" bounds:A1-B2 {
            exterior: west
            exterior: south
            DOOR type:D1 size:900x2100 wall:south
            WINDOW type:W1 size:1800x1000 wall:west
        }

        BATHROOM "bath" bounds:B1-C2 {
            DOOR type:D2 size:750x2100
            WINDOW type:W3 size:600x500
        }

        LIVING "lounge" bounds:A2-C3 {
            exterior: south
            exterior: east
            DOOR type:D1 size:900x2100 wall:south
            WINDOW type:W1 size:1800x1000 wall:east
        }
    }

    ROOF pitch:25deg overhang:600mm
}
```

### Compile to Database

```bash
mvn exec:java \
  -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q
```

This produces:
- `output/tb_lktn.db` - SQLite database with building geometry
- `output/tb_lktn_witness.json` - Proof of correctness (13 claims)
- `output/tb_lktn.ifc` - IFC4 exchange file
- `output/tb_lktn_bom.csv` - Bill of materials

---

## Building and Running

### Prerequisites

- Java 17+
- Maven 3.8+

### Build from Source

```bash
# Clean build
mvn clean package

# Quick build (skip tests)
mvn clean package -DskipTests
```

### Main Entry Points

| Command | Purpose |
|---------|---------|
| `TBLKTNEndToEndTest` | Compile TB-LKTN example to database |
| `TBLKTNCompleteTest` | Full test suite with outlier report |
| `HouseSanityChecker <db>` | Run sanity checks on compiled database |
| `SpaceTypeRegistry` | List registered space types |

### Run Examples

```bash
# TB-LKTN end-to-end compilation with witness generation
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q

# Complete test with outlier report
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNCompleteTest" -q

# List all 21 space types + aliases
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.SpaceTypeRegistry" -q
```

---

## DSL Syntax Reference

### Overall Structure

```
BUILDING "<name>" [profile:"<profile>"] [protocol:"<protocol>"] [lod:<number>] {
    GRID { ... }                    // Optional: Define structural grid
    STOREY "<name>" level:<z> height:<h> {
        SPACE declarations...
    }
    ROOF pitch:<degrees> [overhang:<mm>]
}
```

### GRID Definition

Used for grid-referenced room placement:

```
GRID {
    axes: A, B, C, D, E / 1, 2, 3, 4, 5
    spacing: 1.3, 3.1, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
}
```

- **axes**: Column letters and row numbers
- **spacing**: Spacing between columns (m) and rows (m)

### STOREY Declaration

```
STOREY "<name>" level:<z_meters> height:<h_meters> {
    ...spaces...
}
```

### Room/Space Declaration

#### Using Room Type Keywords

```
BEDROOM "master" bounds:A2-C3 { ... }
BATHROOM "bath" bounds:A4-B5 { ... }
LIVING "lounge" bounds:B2-D4 { ... }
```

#### Malaysian Terminology (with profile)

```
BILIK_UTAMA "master" size:4.4x3.1m { ... }
RUANG_TAMU "living" size:5.0x4.6m { ... }
TANDAS "toilet" size:1.5x1.6m { ... }
```

### Positioning Methods

**Grid Bounds (preferred)**
```
BEDROOM "master" bounds:A2-C3 { ... }
```

**Size Specification**
```
BEDROOM "master" size:4.4x3.1m { ... }
```

### Constraint Keywords

| Keyword | Effect | Example |
|---------|--------|---------|
| `exterior: <direction>` | Room has exterior wall | `exterior: south` |
| `adjacent: <room>` | Shares wall with room | `adjacent: living` |
| `opens_to: <room>` | Opens to open-plan area | `opens_to: common` |
| `stack: <name>` | Named plumbing stack | `stack: plumbing` |

### Openings: DOOR and WINDOW

```
// Simple form
DOOR south
WINDOW north

// With type and size
DOOR type:D1 size:900x2100 wall:south
WINDOW type:W1 size:1800x1000 wall:west
```

### OPEN_PLAN (Combined Living Spaces)

```
OPEN_PLAN "common" bounds:B2-D5 {
    zones: LIVING, DINING, KITCHEN
    exterior: south
    exterior: north
    DOOR type:D1 size:900x2100 wall:south
}
```

### PORCH (Semi-Exterior)

```
PORCH "anjung" bounds:C1-D2 {
    roof: ATTACHED
    exterior: south
}
```

### ROOF Declaration

```
ROOF pitch:25deg overhang:600mm
```

---

## Available Room Types

### Habitable Spaces (Require window and egress)

| Type | Aliases | Min Area | Min Dim |
|------|---------|----------|---------|
| BEDROOM | BED, BILIK_TIDUR | 6.5 m² | 2.134m |
| MASTER_BEDROOM | MASTER, BILIK_UTAMA | 9.3 m² | 2.5m |
| KITCHEN | DAPUR | 4.6 m² | 1.8m |
| LIVING | RUANG_TAMU, TAMU | 6.5 m² | 2.134m |
| DINING | RUANG_MAKAN, MAKAN | 6.5 m² | 2.134m |
| OFFICE | STUDY, PEJABAT | 6.5 m² | 2.134m |

### Service Spaces (Interior OK)

| Type | Aliases | Min Area | Min Dim |
|------|---------|----------|---------|
| BATHROOM | BILIK_MANDI, TANDAS | 2.5 m² | 1.2m |
| WET_KITCHEN | DAPUR_BASUH, LAUNDRY | 3.0 m² | 1.5m |
| STORAGE | STORE, STOR, PANTRY | 0 m² | 0m |
| GARAGE | GARAJ | 0 m² | 2.5m |

### Circulation Spaces

| Type | Aliases | Min Dim |
|------|---------|---------|
| CORRIDOR | HALL, HALLWAY | 0.914m |
| LOBBY | FOYER, ENTRY | 1.0m |

### Exterior/Semi-Exterior Spaces

| Type | Aliases | Notes |
|------|---------|-------|
| PORCH | ANJUNG | Covered entry (posts only) |
| CAR_PORCH | CARPORT, ANJUNG_KERETA | Vehicle cover |
| VERANDAH | VERANDA, SERAMBI | Covered outdoor |

---

## Profiles and Building Codes

### Using a Profile

```
BUILDING "Rumah Rakyat" profile:"Malaysian_Residential" {
    ...
}
```

### Profile Defaults

| Parameter | BASE (IRC) | Malaysian_Residential |
|-----------|------------|----------------------|
| Storey Height | 2.4m | 2.8m |
| Roof Pitch | 20° | 25° |
| Roof Overhang | 450mm | 600mm |
| Door Width | 813mm | 900mm |
| Door Height | 2032mm | 2100mm |
| Slab Thickness | 100mm | 150mm |

### Vocabulary Mapping (Malaysian)

| Input | Maps To |
|-------|---------|
| BILIK_UTAMA | MASTER_BEDROOM |
| BILIK_TIDUR | BEDROOM |
| DAPUR | KITCHEN |
| RUANG_TAMU | LIVING |
| BILIK_MANDI | BATHROOM |
| ANJUNG | PORCH |

---

## Output Formats

### Database Schema (SQLite)

| Table | Purpose |
|-------|---------|
| `spatial_structure` | Building hierarchy (Project → Site → Building → Storey) |
| `elements_meta` | Element metadata (guid, ifc_class, name, storey) |
| `elements_rtree` | Spatial index (bounding boxes) |
| `base_geometries` | Shared geometry definitions |
| `element_instances` | Transform instances |
| `element_assemblies` | Composite assemblies |
| `assembly_components` | Parts within assemblies |
| `mep_systems` | MEP system definitions (v0.35+) |
| `system_nodes` | Nodes in system graphs (v0.35+) |
| `system_edges` | Edges connecting nodes (v0.35+) |

### Output Files

| File | Description |
|------|-------------|
| `building.db` | SQLite database with all geometry and relationships |
| `building_witness.json` | Mathematical proofs of correctness (13 claims) |
| `building.ifc` | IFC4 exchange file for BIM software |
| `building_bom.csv` | Bill of materials for procurement |

---

## Witness System

The BIM Compiler generates mathematical proofs that the building is correct.

### Witness Claims (18 in v0.47.0)

| # | Claim | Proves |
|---|-------|--------|
| 1 | `FOUNDATION_GROUNDED` | Foundation at Z=0 |
| 2 | `ENTRY_EXISTS` | Door from exterior |
| 3 | `ALL_ROOMS_REACHABLE` | Every room accessible |
| 4 | `WINDOWS_ON_EXTERIOR` | No interior windows |
| 5 | `ROOF_COVERS_ALL` | Roof covers footprint |
| 6 | `ROOMS_ENCLOSED` | Walls form closure |
| 7 | `ROOMS_IN_ENVELOPE` | Rooms inside building |
| 8 | `ELECTRICAL_IN_SPACES` | Electrical in room bounds |
| 9 | `FIXTURES_ATTACHED_TO_HOSTS` | Lights on ceiling |
| 10 | `PLUMBING_PIPES_VALID` | Pipe dimensions correct |
| 11 | `PLUMBING_WASTE_COMPLETE` | All fixtures → MH |
| 12 | `PLUMBING_VENT_COMPLETE` | All traps → atmosphere |
| 13 | `PLUMBING_SUPPLY_COMPLETE` | Water meter → all fixtures |
| 14 | `STOREYS_VERTICALLY_CONSISTENT` | Storey Z-levels aligned |
| 15 | `ALL_OUTLETS_ON_CIRCUIT` | Electrical devices on circuits |
| 16 | `MEP_NO_STRUCTURAL_CLASH` | MEP doesn't penetrate structure |
| 17 | `ROOM_AREAS_CONSISTENT` | Room areas match spec |
| 18 | `PARTY_WALLS_VALID` | Party walls meet fire rating (multi-unit only) |

### Viewing Witness Report

```bash
# Summary
cat output/tb_lktn_witness.json | jq '.summary'

# All claims
cat output/tb_lktn_witness.json | jq '.claims | keys'

# Specific claim detail
cat output/tb_lktn_witness.json | jq '.claims.PLUMBING_WASTE_COMPLETE'
```

### Witness File Structure

```json
{
  "version": "1.0",
  "building": "TB-LKTN",
  "generated": "2025-01-30T15:30:00Z",
  "compiler_version": "0.37.0",
  "claims": {
    "FOUNDATION_GROUNDED": {"status": "PROVEN", "witness": {...}},
    "PLUMBING_WASTE_COMPLETE": {
      "status": "PROVEN",
      "witness": {
        "system_id": "waste_system_1",
        "source": "Manhole 1",
        "terminal_count": 2,
        "all_drain_to_source": true,
        "drainage_paths": {
          "toilet in bilik_mandi": ["toilet_bilik_mandi", "riser_bilik_mandi", "MH1"]
        }
      }
    }
  },
  "summary": {"total_claims": 18, "proven": 17, "unprovable": 0, "skipped": 1}
}
```

---

## MEP System Queries

The compiler builds connectivity graphs for MEP systems. Query them with SQL.

### List All MEP Systems

```bash
sqlite3 output/tb_lktn.db "SELECT * FROM mep_systems"
```

Output:
```
waste_system_1|PLUMBING_WASTE|building_guid|1|1|4|3
vent_system_1|PLUMBING_VENT|building_guid|1|1|4|3
supply_system_1|PLUMBING_SUPPLY|building_guid|1|1|4|3
```

### View System Nodes

```bash
sqlite3 output/tb_lktn.db "
  SELECT node_id, role, name 
  FROM system_nodes 
  WHERE system_id = 'waste_system_1'"
```

Output:
```
MH1|SOURCE|Manhole 1
riser_bilik_mandi|DISTRIBUTION|Waste Riser bilik_mandi
toilet_bilik_mandi|TERMINAL|toilet in bilik_mandi
sink_bilik_mandi|TERMINAL|sink in bilik_mandi
```

### View Drainage Paths

```bash
sqlite3 output/tb_lktn.db "
  SELECT n1.name AS from_element, e.edge_type, n2.name AS to_element
  FROM system_edges e
  JOIN system_nodes n1 ON e.from_node_id = n1.node_id
  JOIN system_nodes n2 ON e.to_node_id = n2.node_id
  WHERE e.edge_type = 'DRAINS_TO'"
```

Output:
```
toilet in bilik_mandi|DRAINS_TO|Waste Riser bilik_mandi
sink in bilik_mandi|DRAINS_TO|Waste Riser bilik_mandi
Waste Riser bilik_mandi|DRAINS_TO|Manhole 1
```

### View Supply Paths

```bash
sqlite3 output/tb_lktn.db "
  SELECT n1.name, e.edge_type, n2.name
  FROM system_edges e
  JOIN system_nodes n1 ON e.from_node_id = n1.node_id
  JOIN system_nodes n2 ON e.to_node_id = n2.node_id
  WHERE e.edge_type = 'SUPPLIES'"
```

### Check for Orphaned Terminals

```bash
sqlite3 output/tb_lktn.db "
  SELECT n.name, n.role
  FROM system_nodes n
  WHERE n.role = 'TERMINAL'
  AND n.node_id NOT IN (SELECT from_node_id FROM system_edges)"
```

If this returns any rows, there are fixtures not connected to their system.

---

## Sanity Checker Tool

Independent verification for compiled databases.

### Running

```bash
cd tools/sanity-checker
mvn clean package

java -jar target/sanity-checker-1.0-SNAPSHOT.jar output/my_house.db
```

### Exit Codes

| Code | Meaning |
|------|---------|
| 0 | All checks PASS |
| 1 | At least one FAIL |
| 2 | Input file not found |
| 3 | Internal error |

### Checks Performed

1. **Foundation Check** - Building sits on slab at Z=0
2. **Entry Door Check** - At least one door on perimeter
3. **Window Placement Check** - Windows on exterior walls
4. **Room Connectivity Check** - Rooms accessible via doors
5. **Room Proportions Check** - Rooms have sensible shapes
6. **Roof Coverage Check** - Roof covers building footprint
7. **Envelope Containment Check** - All elements within envelope

---

## Configuration and Extensibility

### Adding a New Space Type

Edit `config/spacetypes.yaml`:

```yaml
STUDY_NOOK:
  category: HABITABLE
  omniclass: "13-61 11 11"
  wall_rule: ENCLOSED
  validation:
    min_area: 4.0
    min_dimension: 1.8
    requires_window: true
  aliases:
    - NOOK
    - READING_CORNER
```

### Adding a New Profile

Create `config/profiles/<name>.yaml`:

```yaml
name: Commercial_IBC
extends: BASE
validation_code: IBC_2021

defaults:
  storey_height: 3.7
  door_width: 1000

vocabulary:
  CONFERENCE_ROOM: OFFICE
```

---

## Complete Example

TB-LKTN Malaysian affordable housing:

```
BUILDING "TB-LKTN"
    profile: "Malaysian_Residential"
    protocol: "Residential_Single_Storey"
    lod: 300
{
    GRID {
        axes: A, B, C, D, E / 1, 2, 3, 4, 5
        spacing: 1.3, 3.1, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
    }

    STOREY "Ground" level:0 height:2.8m {
        PORCH "anjung" bounds:C1-D2 {
            exterior: south
            roof: ATTACHED
        }

        OPEN_PLAN "common" bounds:B2-D5 {
            zones: LIVING, DINING, KITCHEN
            exterior: south
            exterior: north
            DOOR type:D1 size:900x2100 wall:south
            DOOR type:D1 size:900x2100 wall:north
        }

        BEDROOM "bilik_utama" bounds:A2-B4 {
            exterior: west
            opens_to: common
        }

        BATHROOM "bilik_mandi" bounds:A4-B5 {
            exterior: west
            opens_to: common
        }

        BEDROOM "bilik_2" bounds:D2-E4 {
            exterior: east
            opens_to: common
        }

        BEDROOM "bilik_3" bounds:D4-E5 {
            exterior: east
            opens_to: common
        }
    }

    ROOF pitch:25deg overhang:600mm
}
```

### Expected Output

```
=== COMPILATION COMPLETE ===
Building: TB-LKTN
Version: 0.37.0

Geometry:
  Spaces: 7
  Walls: 18
  Doors: 7
  Windows: 7
  Electrical: 29 (8 lights, 14 outlets, 7 switches)
  Plumbing: 4 pipes

MEP Systems:
  waste_system_1: 4 nodes, 3 edges, COMPLETE
  vent_system_1: 4 nodes, 3 edges, COMPLETE
  supply_system_1: 4 nodes, 3 edges, COMPLETE

Witness: 13/13 PROVEN

Output:
  → output/tb_lktn.db
  → output/tb_lktn_witness.json
  → output/tb_lktn.ifc
  → output/tb_lktn_bom.csv
```

---

## Troubleshooting

### "Unknown space type"

Add to profile vocabulary or `config/spacetypes.yaml`.

### "Geometry impossible"

Check fixture definitions or room sizes in outlier log.

### "Constraint relaxation"

Room bounds may be incompatible. Expand bounds or adjust adjacencies.

### "Orphaned MEP terminal"

Fixture not connected to system graph. Check placer logic.

### Witness claim FAILED

Check specific claim in witness.json for details:
```bash
cat output/tb_lktn_witness.json | jq '.claims.PLUMBING_WASTE_COMPLETE'
```

### Compilation slow

Use `-DskipTests` flag: `mvn clean package -DskipTests`

### MEP system not COMPLETE

Query orphaned terminals:
```bash
sqlite3 output/tb_lktn.db "
  SELECT * FROM system_nodes 
  WHERE role='TERMINAL' 
  AND node_id NOT IN (SELECT from_node_id FROM system_edges)"
```

---

## Quick Reference Commands

```bash
# Build
cd ~/bim-compiler && mvn compile

# Run TB-LKTN compilation
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q

# Run tests
mvn test

# View witness summary
cat output/tb_lktn_witness.json | jq '.summary'

# List MEP systems
sqlite3 output/tb_lktn.db "SELECT * FROM mep_systems"

# View drainage paths
sqlite3 output/tb_lktn.db "
  SELECT n.name, e.edge_type, n2.name 
  FROM system_edges e
  JOIN system_nodes n ON e.from_node_id = n.node_id
  JOIN system_nodes n2 ON e.to_node_id = n2.node_id
  WHERE e.edge_type = 'DRAINS_TO'"

# Check element count
sqlite3 output/tb_lktn.db "SELECT ifc_class, COUNT(*) FROM elements_meta GROUP BY ifc_class"
```

---

## Additional Resources

- `PROJECT_STATUS.md` - Current project state and phase history
- `bim-compiler-dsl-architecture.md` - Design patterns
- `bim-dsl-dictionary.md` - Complete vocabulary reference
- `witness-system-specification.md` - Witness system design
- `GLOSSARY.md` - Term definitions
- `UserGuideSupplement(MultiUnit).md` - Multi-unit buildings (duplexes, townhouses)

---

*User Guide v0.47.0 - February 2025*
*13 witness claims, MEP system graphs, plumbing 100% proven*
*See UserGuideSupplement(MultiUnit).md for multi-unit buildings*
