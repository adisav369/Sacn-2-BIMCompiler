# BIM Compiler User Guide

## Table of Contents
1. [Quick Start](#quick-start)
2. [Building and Running](#building-and-running)
3. [DSL Syntax Reference](#dsl-syntax-reference)
4. [Available Room Types](#available-room-types)
5. [Profiles and Building Codes](#profiles-and-building-codes)
6. [Output Formats](#output-formats)
7. [Sanity Checker Tool](#sanity-checker-tool)
8. [Configuration and Extensibility](#configuration-and-extensibility)
9. [Complete Example](#complete-example)
10. [Troubleshooting](#troubleshooting)

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
- `output/tb_lktn_witness.json` - Proof of correctness claims

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

### Witness File (JSON)

Proof of correctness claims generated alongside geometry:

```json
{
  "version": "1.0",
  "building": "TB-LKTN",
  "claims": {
    "FOUNDATION_GROUNDED": {"status": "PROVEN", "witness": {...}},
    "ENTRY_EXISTS": {"status": "PROVEN", "witness": {...}},
    "ALL_ROOMS_REACHABLE": {"status": "PROVEN", "witness": {...}},
    "WINDOWS_ON_EXTERIOR": {"status": "PROVEN", "witness": {...}},
    "ROOF_COVERS_ALL": {"status": "PROVEN", "witness": {...}},
    "ROOMS_ENCLOSED": {"status": "PROVEN", "witness": {...}},
    "ROOMS_IN_ENVELOPE": {"status": "PROVEN", "witness": {...}}
  },
  "summary": {"total_claims": 7, "proven": 7, "unprovable": 0, "skipped": 0}
}
```

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

---

## Troubleshooting

### "Unknown space type"

Add to profile vocabulary or `config/spacetypes.yaml`.

### "Geometry impossible"

Check fixture definitions or room sizes in outlier log.

### "Constraint relaxation"

Room bounds may be incompatible. Expand bounds or adjust adjacencies.

### Compilation slow

Use `-DskipTests` flag: `mvn clean package -DskipTests`

---

## Additional Resources

- `docs/bim-compiler-dsl-architecture.md` - Design patterns
- `docs/bim-dsl-dictionary.md` - Vocabulary reference
- `claude.md` - Project status and phases
