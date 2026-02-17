# BIM Compiler User Guide

**Version:** 0.90.0
**Updated:** February 2026

## Table of Contents
1. [Quick Start](#quick-start)
2. [Building and Running](#building-and-running)
3. [DSL Syntax Reference](#dsl-syntax-reference)
4. [Available Room Types](#available-room-types)
5. [BOM Resolution (Phase 60-62)](#bom-resolution-phase-60-62)
6. [LOD400 Library Integration (Phase 57-59)](#lod400-library-integration-phase-57-59)
7. [Fire Protection (Phase 57)](#fire-protection-phase-57)
8. [Profiles and Building Codes](#profiles-and-building-codes)
9. [Output Formats](#output-formats)
10. [Material and Colour Data](#material-and-colour-data)
11. [Witness System](#witness-system)
12. [MEP System Queries](#mep-system-queries)
13. [Troubleshooting](#troubleshooting)

---

## Quick Start

### Minimal Example

Create a simple building DSL file (e.g., `my_house.bim`):

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
# Compile SampleHouse (55 elements, with material/colour data)
mvn exec:java -pl DAGCompiler \
  -Dexec.mainClass="com.bim.compiler.dsl.SampleHouseEndToEndTest" -q
```

This produces:
- `DAGCompiler/lib/output/ifc4_sample_house.db` - SQLite database with building geometry + materials
- Witness JSON - Proof of correctness
- IFC4 exchange file
- BOM CSV - Bill of materials

---

## Building and Running

### Prerequisites

- Java 17+
- Maven 3.8+
- Python 3.10+ (for database scripts)

### Build from Source

```bash
# Compile all modules
mvn compile -q

# Clean build
mvn clean package

# Quick build (skip tests)
mvn clean package -DskipTests
```

### Main Entry Points (Rosetta Stone E2E)

All commands require `-pl DAGCompiler` since compiler source is in the DAGCompiler module.

| Command | Purpose | Output |
|---------|---------|--------|
| `SampleHouseEndToEndTest` | Compile SampleHouse (55 elements) | `DAGCompiler/lib/output/ifc4_sample_house.db` |
| `TBLKTNDuplexEndToEndTest` | Compile Duplex (1,085 elements) | `DAGCompiler/lib/output/ifc2x3_duplex.db` |
| `TerminalEndToEndTest` | Compile Terminal (51,719 elements) | `DAGCompiler/lib/output/sjtii_terminal.db` |

### Run Examples

```bash
# SampleHouse (single-storey UK house, 55 elements)
mvn exec:java -pl DAGCompiler \
  -Dexec.mainClass="com.bim.compiler.dsl.SampleHouseEndToEndTest" -q

# Duplex (2-storey US duplex, 1,085 elements)
mvn exec:java -pl DAGCompiler \
  -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNDuplexEndToEndTest" -q

# Terminal (multi-discipline airport terminal, 51,719 elements)
mvn exec:java -pl DAGCompiler \
  -Dexec.mainClass="com.bim.compiler.dsl.TerminalEndToEndTest" -q
```

---

## DSL Syntax Reference

### Overall Structure

```
BUILDING "<name>" [profile:"<profile>"] [compliance:<mode>] {
    GRID { ... }                    // Optional: Define structural grid
    STOREY "<name>" level:<z> height:<h> {
        SPACE declarations...
    }
    ROOF pitch:<degrees> [overhang:<mm>]
}
```

### GRID Definition

```
GRID {
    axes: A, B, C, D, E / 1, 2, 3, 4, 5
    spacing: 1.3, 3.1, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
}
```

### Room/Space Declaration

```
BEDROOM "master" bounds:A2-C3 { ... }
BATHROOM "bath" size:2.5x3m { ... }
CANTEEN "kantin" area:84m² { ... }     // NEW: area-based sizing
```

### Constraint Keywords

| Keyword | Effect | Example |
|---------|--------|---------|
| `exterior: <direction>` | Room has exterior wall | `exterior: south` |
| `adjacent: <room>` | Shares wall with room | `adjacent: living` |
| `opens_to: <room>` | Opens to open-plan area | `opens_to: common` |
| `stack: <name>` | Named plumbing stack | `stack: plumbing` |
| `compliance: <mode>` | Compliance mode | `compliance: AUTO_FP` |

### CORE Block (Vertical Circulation)

```
CORE "main_core" bounds:D3-E4 {
    STAIR width:1.2m
    LIFT capacity:8
    SHAFT type:MEP
}
```

---

## Available Room Types

### Habitable Spaces

| Type | Aliases | Min Area | BOM Elements |
|------|---------|----------|--------------|
| BEDROOM | BED, BILIK_TIDUR | 6.5 m² | Lights, Outlets |
| KITCHEN | DAPUR | 4.6 m² | Lights, Sink |
| LIVING | RUANG_TAMU | 6.5 m² | Lights, Outlets |
| DINING | RUANG_MAKAN | 6.5 m² | Lights |
| OFFICE | PEJABAT | 9.0 m² | Lights, Desk, Chair |
| CLASSROOM | BILIK_DARJAH | 46.5 m² | Lights, Sprinklers, Desks |
| CANTEEN | KANTIN | 30.0 m² | Lights, Sprinklers, Tables |

### Service Spaces

| Type | Aliases | Min Area | BOM Elements |
|------|---------|----------|--------------|
| BATHROOM | BILIK_MANDI | 2.5 m² | Toilet, Sink, Exhaust |
| KITCHEN | DAPUR | 4.6 m² | Sink |
| STORAGE | STOR | 0 m² | - |

### Circulation Spaces

| Type | Aliases | Min Width |
|------|---------|-----------|
| CORRIDOR | HALL | 1.8m (educational) |
| LOBBY | FOYER | 3.0m |
| WAITING | - | 3.0m |

---

## BOM Resolution (Phase 60-62)

The compiler automatically calculates quantities for building elements based on room type and area.

### How It Works

```
ROOM "kantin" [CANTEEN] 84.0m²
    7× pendent         ceil(84.0m² / 12.1) = 7 (NFPA_13 8.6.2.1)
    2× Supply Diffuser ceil(CFM / 600) = 2 (ASHRAE_62_1)
    6× Downlight       ceil(84.0m² × 200 lux / 3000 lm) = 6 (MS_1525)
    9× Canteen Table   ceil(occupancy / 4) = 9 (IBC 1004.5)
```

### BOM Types

| Type | Description | Example |
|------|-------------|---------|
| MANDATORY | Always required | Toilet in BATHROOM |
| OPTIONAL | User-specified in DSL | Bidet (future) |
| VARIABLE | Calculated from room properties | Sprinklers, Lights, Tables |

### Calculation Rules

| Rule | Formula | Code Reference |
|------|---------|----------------|
| PER_AREA | `ceil(area / base)` | NFPA 13 (sprinklers) |
| PER_LUX | `ceil(area × lux / lumens)` | MS 1525 (lighting) |
| PER_CFM | `ceil(cfm / base)` | ASHRAE 62.1 (diffusers) |
| PER_OCCUPANT | `ceil(occupancy / base)` | IBC (furniture) |
| FIXED | `base` | IPC 403.1 (fixtures) |

### Viewing BOM Resolution

```bash
# Standalone BOM test
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.BOMRuleAD" -q
```

Output:
```
=== BOM: CANTEEN "kantin" (84.0m²) ===
  7× pendent         [VARIABLE] ceil(area_m2 / 12.1) = 7 NFPA_13 8.6.2.1
  6× Downlight       [VARIABLE] ceil(area_m2 × 200 lux / 3000 lm) = 6 MS_1525
  9× Canteen Table   [VARIABLE] ceil((area_m2 / 2.5 m²/person) / 4 seats) = 9 IBC 1004.5
```

### Outlier Logging

When BOM quantities exceed room physical capacity:

```
[OUTLIER] canteen_table | CANTEEN "kantin" (12.0m x 12.0m)
  → BOM wants 15 tables but only 9 fit (grid 3x3)
```

---

## LOD400 Library Integration (Phase 57-59)

The compiler uses LOD400 (fabrication-ready) geometry from a component library.

### Library Coverage

| Element | Library | Parametric | Coverage |
|---------|---------|------------|----------|
| Doors | 46 | 0 | 100% |
| Windows | 58 | 8 | 88% |
| Stairs | 1 | 0 | 100% |
| Furniture | 11 | 0 | 100% |
| Lights | 85 | 0 | 100% |
| Sprinklers | 84 | 0 | 100% |
| FP Pipes | Generated | - | 100% |

### Furniture Placement

Room types automatically get appropriate furniture:

| Room Type | Furniture |
|-----------|-----------|
| CANTEEN | Canteen Table (1.5m × 1.25m) |
| OFFICE | Desk_with_return + Chair |
| LOBBY/WAITING | Waiting_Room_Seat (4-seat bench) |

### Library Statistics

```bash
# Query library contents
sqlite3 library/component_library.db "
  SELECT ct.category, COUNT(*)
  FROM component_definitions cd
  JOIN component_types ct ON cd.type_id = ct.id
  GROUP BY ct.category"
```

---

## Fire Protection (Phase 57)

Fire protection is driven by Authority Data triggers.

### Automatic Triggers

| Trigger | Threshold | Action |
|---------|-----------|--------|
| Building height | > 18m | Sprinklers required |
| Floor area | > 1000m² | Sprinklers required |
| Occupancy | Assembly, High-rise | Sprinklers required |

### Compliance Modes

```
BUILDING "School" compliance:AUTO_FP {
    // Sprinklers auto-generated when triggers fire
}
```

| Mode | Behavior |
|------|----------|
| (none) | No automatic fire protection |
| AUTO_FP | Generate sprinklers when triggered |
| FULL_COMPLIANCE | All code requirements enforced |

### Coverage Calculation

| Hazard Class | Coverage | Spacing |
|--------------|----------|---------|
| Light (office, classroom) | 18.6 m²/head | 4.31m |
| Ordinary Group 1 (canteen) | 12.1 m²/head | 3.48m |

### Fire Suppression Piping (Phase 80)

When sprinklers are generated, the compiler automatically creates the connecting pipe network:

```
RISER (100mm) ─┬─ MAIN (65mm) ─┬─ BRANCH (25mm) → SPRINKLER_HEAD
               │               ├─ BRANCH → SPRINKLER_HEAD
               │               └─ ...
               │
               └─ MAIN ─┬─ BRANCH → SPRINKLER_HEAD
                        └─ ...
```

**NFPA 13 Pipe Sizing (Light Hazard):**

| Pipe Type | Diameter | Purpose |
|-----------|----------|---------|
| RISER | 100mm (4") | Vertical from pump room |
| MAIN | 65mm (2.5") | Horizontal along ceiling |
| BRANCH | 25mm (1") | Connection to head |

**BOM Assembly Approach:**

Pipes are grouped into procurement assemblies:
- `FP_Ground_RISER` - Vertical riser segment per storey
- `FP_Ground_MAIN` - Horizontal distribution main
- `FP_Ground_LIVING_BRANCH` - Branch pipes per room

**Viewing FP Piping:**

```bash
# Count FP elements
sqlite3 output/condo_mid.db "
  SELECT element_type, COUNT(*)
  FROM elements_meta
  WHERE discipline='FP'
  GROUP BY element_type"

# Result:
# BRANCH|102
# MAIN|108
# PENDANT|84
# RISER|18
```

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

### Vocabulary Mapping (Malaysian)

| Input | Maps To |
|-------|---------|
| BILIK_UTAMA | MASTER_BEDROOM |
| BILIK_TIDUR | BEDROOM |
| DAPUR | KITCHEN |
| RUANG_TAMU | LIVING |
| BILIK_MANDI | BATHROOM |
| KANTIN | CANTEEN |

---

## Output Formats

### Database Schema (SQLite)

| Table | Purpose |
|-------|---------|
| `spatial_structure` | Building hierarchy (Project → Site → Building → Storey) |
| `elements_meta` | Element metadata: guid, ifc_class, name, storey, discipline, material_name, material_rgba |
| `elements_rtree` | Spatial index: id, minX, maxX, minY, maxY, minZ, maxZ |
| `base_geometries` | Shared geometry (vertices/faces BLOBs, float32/int32 arrays) |
| `assembly_components` | BOM parent-child relationships |
| `mep_systems` | MEP system definitions |
| `system_nodes` | Nodes in system graphs |
| `system_edges` | Edges connecting nodes |
| `simple_qto` | Quantity takeoff (area, volume, length) |

### Output Files

Output goes to `DAGCompiler/lib/output/`:

| File | Description |
|------|-------------|
| `*.db` | SQLite database with all geometry, materials, and spatial index |
| `*_witness.json` | Mathematical proofs of correctness |
| `*.ifc` | IFC4 exchange file |
| `*_bom.csv` | Bill of materials |

---

## Material and Colour Data

The compiler extracts material names and RGBA colours from IFC source files and carries them through to the output. This enables rendering with correct transparency (e.g., glass walls) and material colouring.

### What's in the Output

Each element in `elements_meta` can have:

| Column | Description | Example |
|--------|-------------|---------|
| `material_name` | IFC material name | `Glass`, `Concrete Block`, `Gypsum Board` |
| `material_rgba` | RGBA colour (0.0-1.0, comma-separated) | `0.000,0.502,0.753,0.100` |

### RGBA Format

The `material_rgba` column stores `R,G,B,A` values:
- Values range from 0.0 to 1.0
- Alpha = opacity (1.0 = fully opaque, 0.0 = fully transparent)
- Example: `0.000,0.502,0.753,0.100` = blue glass, 10% opaque (90% see-through)

### Querying Materials

```sql
-- List all materials in the output
SELECT material_name, COUNT(*), material_rgba
FROM elements_meta
WHERE material_name IS NOT NULL
GROUP BY material_name;

-- Find transparent elements (glass, etc.)
SELECT guid, ifc_class, material_name, material_rgba
FROM elements_meta
WHERE material_rgba IS NOT NULL
  AND CAST(SUBSTR(material_rgba, INSTR(material_rgba, ',')+1) AS REAL) < 1.0;

-- Glass panels with transparency
SELECT guid, material_name, material_rgba FROM elements_meta
WHERE material_name = 'Glass';
-- MD_PLATE_UNKNOWN_1 | Glass | 0.000,0.502,0.753,0.100
```

### Material Coverage

| Stone | Material Names | RGBA Colours |
|-------|---------------|-------------|
| SampleHouse | 55/55 (100%) | 51/55 (93%) |
| Duplex | 77/1085 (7%) | 124/1085 (11%) |
| Terminal | 41,148/51,719 (80%) | 41,613/51,719 (80%) |

Note: Duplex MEP elements (pipes, ducts) typically have no IFC material styling, hence lower coverage.

---

## Witness System

The BIM Compiler generates mathematical proofs that the building is correct.

### Witness Claims (21 in v0.62.0)

| # | Claim | Proves |
|---|-------|--------|
| 1 | `FOUNDATION_GROUNDED` | Foundation at Z=0 |
| 2 | `ENTRY_EXISTS` | Door from exterior |
| 3 | `ALL_ROOMS_REACHABLE` | Every room accessible |
| 4 | `WINDOWS_ON_EXTERIOR` | No interior windows |
| 5 | `ROOF_COVERS_ALL` | Roof covers footprint |
| 6 | `ROOMS_ENCLOSED` | Walls form closure |
| 7-13 | MEP connectivity | Plumbing, electrical connected |
| 14-21 | Additional claims | See witness-system-specification.md |

### Viewing Witness Report

```bash
# Summary
cat output/tb_lktn_witness.json | jq '.summary'

# All claims
cat output/tb_lktn_witness.json | jq '.claims | keys'

# Specific claim
cat output/tb_lktn_witness.json | jq '.claims.PLUMBING_WASTE_COMPLETE'
```

---

## MEP System Queries

### List All MEP Systems

```bash
sqlite3 output/tb_lktn.db "SELECT * FROM mep_systems"
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

---

## Troubleshooting

### "Unknown space type"

Add to profile vocabulary or Authority Data:
```bash
# Check available types
sqlite3 database/authority_data.db "SELECT name FROM ad_spacetype"
```

### "BOM exceeds capacity"

The outlier log shows when calculated quantities don't fit:
```
[OUTLIER] canteen_table | CANTEEN "kantin" (8.0m x 8.0m)
  → BOM wants 10 tables but only 6 fit (grid 2x3)
```

Solution: Increase room size or accept the reduced quantity.

### "Geometry impossible"

Fixture doesn't fit in room. Check room dimensions:
```bash
sqlite3 output/building.db "
  SELECT name, max_x-min_x AS width, max_y-min_y AS depth
  FROM elements_rtree r
  JOIN elements_meta m ON r.id = m.rowid
  WHERE m.ifc_class = 'IfcSpace'"
```

### Witness claim FAILED

Check specific claim:
```bash
cat output/tb_lktn_witness.json | jq '.claims.PLUMBING_WASTE_COMPLETE'
```

---

## Quick Reference Commands

```bash
# Build (from project root)
mvn compile -q

# Compile Rosetta Stones
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.SampleHouseEndToEndTest" -q
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNDuplexEndToEndTest" -q
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.TerminalEndToEndTest" -q

# Spatial fidelity check
python3 tools/spatial_checker.py \
  DAGCompiler/lib/output/ifc4_sample_house.db \
  DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db \
  --discipline ARC

# Query element count
sqlite3 DAGCompiler/lib/output/ifc4_sample_house.db \
  "SELECT ifc_class, COUNT(*) FROM elements_meta GROUP BY ifc_class"

# Query materials
sqlite3 DAGCompiler/lib/output/ifc4_sample_house.db \
  "SELECT material_name, material_rgba FROM elements_meta WHERE material_name IS NOT NULL"
```

---

## Additional Resources

- `PROGRESS.md` - Current development state
- `PROJECT_STATUS.md` - Project overview
- `GLOSSARY.md` - Term definitions
- `METADATA_DRIVEN_ARCHITECTURE.md` - Authority Data architecture
- `BUILDING_AS_BOM_CONCEPT.md` - BOM resolution concept
- `witness-system-specification.md` - Witness system design
- `UserGuideSupplement(MultiUnit).md` - Multi-unit buildings

---

*User Guide v0.90.0 - February 2026*
*3 Rosetta Stones at ~100% fidelity, material/colour extraction, LOD400 library, DAGCompiler module*
