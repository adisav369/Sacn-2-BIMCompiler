# BIM Compiler User Guide
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [ConstructionAsERP](ConstructionAsERP.md) · [TestArchitecture](TestArchitecture.md)

**Version:** 1.1.0
**Updated:** March 2026

## Table of Contents
1. [Quick Start](#quick-start)
2. [Building and Running](#building-and-running)
3. [DSL Syntax Reference](#dsl-syntax-reference)
4. [Available Room Types](#available-room-types)
5. [BOM Resolution](#bom-resolution)
6. [LOD400 Library Integration](#lod400-library-integration)
7. [Profiles and Building Codes](#profiles-and-building-codes)
8. [Fire Protection](#fire-protection)
9. [Output Formats](#output-formats)
10. [Material and Colour Data](#material-and-colour-data)
11. [Validation and Proofs](#validation-and-proofs)
12. [Spatial Scoring (X-Ray)](#spatial-scoring-x-ray)
13. [MEP System Queries](#mep-system-queries)
14. [Troubleshooting](#troubleshooting)

---

## Quick Start

### Minimal Example

Create a building DSL file (e.g., `my_house.bim`):

```
BUILDING "My House" type:SINGLE_UNIT profile:"Malaysian_Residential" {

    GRID {
        axes: A, B, C / 1, 2, 3
        spacing: 4.5, 5.0 / 3.0, 3.5
    }

    STOREY "Ground" level:0 height:2.8m {

        BEDROOM "master" bounds:A1-B2 {
            exterior: west, south
            WINDOW wall:west
        }

        BATHROOM "bath" bounds:B1-C2 {
            WINDOW wall:east
        }

        LIVING "lounge" bounds:A2-C3 {
            exterior: south, east
            WINDOW wall:east
        }
    }

    ROOF pitch:25deg overhang:600mm
}
```

### Compile and Verify

```bash
# Build from project root
mvn compile -q

# Compile SampleHouse (UK residential, 55 elements)
mvn exec:java -pl DAGCompiler \
  -Dexec.mainClass="com.bim.compiler.dsl.SampleHouseEndToEndTest" -q

# Check spatial fidelity against reference
python3 DAGCompiler/python/spatial_checker.py \
  DAGCompiler/lib/output/ifc4_samplehouse.db \
  DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db \
  --discipline ARC
```

---

## Building and Running

### Prerequisites

- Java 17+
- Maven 3.8+
- Python 3.10+ (for spatial checking and extraction scripts)

### Build from Source

```bash
mvn compile -q          # Compile all modules
mvn clean package       # Full build
```

### The Four Buildings

The compiler validates against four buildings spanning three construction traditions:

| Building | Command Class | Elements | Tradition | F1 Score |
|----------|--------------|----------|-----------|----------|
| SampleHouse | `SampleHouseEndToEndTest` | 55 | UK residential | **100%** |
| Duplex | `TBLKTNDuplexEndToEndTest` | 1,085 | US residential | **100%** |
| Terminal | `TerminalEndToEndTest` | 51,088 | MY institutional | **~100%** |
| TB-LKTN | `TBLKTNEndToEndTest` | 58 | MY affordable | generative |

TB-LKTN is the first *generative* building — compiled purely from relational rules with no IFC reference. It proves the compiler can create, not just replicate.

### Run Commands

All commands run from project root. The `-pl DAGCompiler` flag is required.

```bash
# SampleHouse (single-storey UK house, 55 elements)
mvn exec:java -pl DAGCompiler \
  -Dexec.mainClass="com.bim.compiler.dsl.SampleHouseEndToEndTest" -q

# Duplex (2-storey US duplex, 1,085 elements)
mvn exec:java -pl DAGCompiler \
  -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNDuplexEndToEndTest" -q

# Terminal (4-storey airport terminal, 51,088 elements)
mvn exec:java -pl DAGCompiler \
  -Dexec.mainClass="com.bim.compiler.dsl.TerminalEndToEndTest" -q

# TB-LKTN (single-storey affordable house, 58 elements)
mvn exec:java -pl DAGCompiler \
  -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q
```

### Output

Each compilation produces:
- `DAGCompiler/lib/output/*.db` — SQLite database with geometry, materials, spatial index
- Console output with element counts, witness proofs, and placement diagnostics

---

## DSL Syntax Reference

### Overall Structure

```
BUILDING "<name>" [type:<template>] [profile:"<profile>"] [compliance:<mode>] {
    GRID { ... }
    STOREY "<name>" level:<z> height:<h> {
        <room declarations>
    }
    ROOF pitch:<degrees> [overhang:<mm>]
}
```

The DSL is a **catalog selector** — it declares *what* the user wants, not *how* to build it. Room sizes come from the grid, wall types from the profile, furniture from BOM recipes. No coordinates, no geometry.

### GRID Definition

```
GRID {
    axes: A, B, C, D, E / 1, 2, 3, 4, 5
    spacing: 1.3, 3.1, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
}
```

Grid axes define structural column lines. Spacings are in meters between consecutive axes. Room bounds reference grid intersections (e.g., `bounds:A2-C3` = rectangle from axis A/line 2 to axis C/line 3).

### Room/Space Declaration

```
BEDROOM "master" bounds:A2-C3 { ... }
BATHROOM "bath" bounds:B1-C2 { ... }
OPEN_PLAN "common" bounds:B2-D5 { zones: LIVING, DINING, KITCHEN }
```

### Constraint Keywords

| Keyword | Effect | Example |
|---------|--------|---------|
| `exterior: <direction>` | Room has exterior wall on this face | `exterior: south` |
| `adjacent: <room>` | Shares wall with named room (generates door) | `adjacent: living` |
| `opens_to: <room>` | Opens to open-plan area (generates door) | `opens_to: common` |
| `stack: <name>` | Named plumbing stack (groups wet areas) | `stack: plumbing` |
| `compliance: <mode>` | Fire protection compliance mode | `compliance: AUTO_FP` |
| `zones: <list>` | Functional zones within OPEN_PLAN | `zones: LIVING, DINING` |

### OPEN_PLAN (Combined Zones)

Combines multiple functional zones without internal walls:

```
OPEN_PLAN "common" bounds:B2-D5 {
    zones: LIVING, DINING, KITCHEN
    exterior: south
    exterior: north
}
```

Zones share the space. No walls between them. Perimeter walls only. Fixture placement uses zone types (kitchen gets counter, dining gets table).

### Door and Window Specifications

```
DOOR type:D1 size:900x2100 wall:south    // Main entry
DOOR type:D2 size:750x2100               // Bedroom door
WINDOW type:W1 size:1800x1000 wall:west  // Standard window
WINDOW type:W3 size:600x500 wall:east    // Ventilation window
```

When type codes are omitted, the compiler resolves appropriate families from the profile and room context.

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

| Type | Aliases | Min Area | Furniture |
|------|---------|----------|-----------|
| BEDROOM | BED, BILIK_TIDUR, BILIK_UTAMA | 6.5 m² | Bed, side table |
| KITCHEN | DAPUR | 4.6 m² | Counter, sink |
| LIVING | RUANG_TAMU | 6.5 m² | Sofa, table |
| DINING | RUANG_MAKAN | 6.5 m² | Table, chairs |
| OFFICE | PEJABAT | 9.0 m² | Desk, chair |
| CLASSROOM | BILIK_DARJAH | 46.5 m² | Desks, sprinklers |
| CANTEEN | KANTIN | 30.0 m² | Tables, sprinklers |

### Service Spaces

| Type | Aliases | Min Area | Fixtures |
|------|---------|----------|----------|
| BATHROOM | BILIK_MANDI | 2.5 m² | Toilet, sink, exhaust |
| TOILET_BLOCK | TANDAS | 2.5 m² | Toilet, sink |
| STORAGE | STOR | 0 m² | — |

### Circulation Spaces

| Type | Aliases | Min Width |
|------|---------|-----------|
| CORRIDOR | HALL | 1.8m (educational) |
| LOBBY | FOYER | 3.0m |
| WAITING | — | 3.0m |

### Combined Spaces

| Type | Description |
|------|-------------|
| OPEN_PLAN | Combined zones — no internal walls between zones |
| PORCH | Covered entry — partial walls (exterior open) |

---

## BOM Resolution

The compiler automatically calculates element quantities from room type, area, and building code rules.

### How It Works

```
ROOM "kantin" [CANTEEN] 84.0m²
    7x pendent         ceil(84.0m² / 12.1) = 7 (NFPA_13 8.6.2.1)
    2x Supply Diffuser ceil(CFM / 600) = 2 (ASHRAE_62_1)
    6x Downlight       ceil(84.0m² x 200 lux / 3000 lm) = 6 (MS_1525)
    9x Canteen Table   ceil(occupancy / 4) = 9 (IBC 1004.5)
```

### Calculation Rules

| Rule | Formula | Code Reference |
|------|---------|----------------|
| PER_AREA | `ceil(area / base)` | NFPA 13 (sprinklers) |
| PER_LUX | `ceil(area x lux / lumens)` | MS 1525 (lighting) |
| PER_CFM | `ceil(cfm / base)` | ASHRAE 62.1 (diffusers) |
| PER_OCCUPANT | `ceil(occupancy / base)` | IBC (furniture) |
| FIXED | `base` | IPC 403.1 (fixtures) |

### BOM Types

| Type | Description | Example |
|------|-------------|---------|
| MANDATORY | Always required | Toilet in BATHROOM |
| OPTIONAL | User-specified | Bidet (future) |
| VARIABLE | Calculated from room properties | Sprinklers, lights, tables |

### Adding a New Recipe

New BOM recipes require only SQL — no Java changes:

```sql
INSERT INTO m_bom (bom_id, bom_type, group_by, is_active)
VALUES ('STUDY_DESK_SET', 'ASSEMBLY', 'ROOM', 1);

INSERT INTO m_bom_line (bom_id, role, child_name_pattern, sequence, is_active)
VALUES ('STUDY_DESK_SET', 'DESK', 'Desk%', 1, 1);
```

---

## LOD400 Library Integration

The compiler uses LOD400 (fabrication-ready) tessellated geometry from the component library (`library/component_library.db`).

### How Geometry Binds to Elements

Every placed element goes through the **MeshBinder** which:
1. Looks up LOD400 geometry from `ComponentLibrary.resolveGeometryByInstance()`
2. Validates scale factors are within [0.3, 3.0] per axis
3. Creates a `BoundElement` — a proof-carrying type that certifies the mesh fits the bounding box
4. Tracks violations as degradations reported with `[WITNESS]` tags

All four buildings route through MeshBinder — there is a single unified geometry path.

### Geometry Provenance

Each geometry entry in `I_Geometry_Map` has a provenance:

| Provenance | Meaning |
|------------|---------|
| LIBRARY | Mesh from component library (LOD400 tessellation) |
| EXTRACTED | Mesh extracted from reference IFC |
| PARAMETRIC | Mesh generated from parameters (future) |

### Library Coverage

| Element | Library Items | Coverage |
|---------|--------------|----------|
| Doors | 46 families | 100% |
| Windows | 58 families | 88% |
| Stairs | 1 | 100% |
| Furniture | 11 types | 100% |
| Lights | 85 types | 100% |
| Sprinklers | 84 types | 100% |
| MEP Pipes | Generated | 100% |

### Library Statistics

```bash
sqlite3 library/component_library.db "
  SELECT COUNT(*) AS total_components FROM component_definitions;
  SELECT COUNT(*) AS total_geometries FROM base_geometries;
  SELECT COUNT(*) AS ad_tables FROM sqlite_master
    WHERE type='table' AND name LIKE 'ad_%';"
```

Current: **8,766 component definitions**, **55 ad_* tables**, **23,888 metadata rows**.

---

## Profiles and Building Codes

### Using a Profile

```
BUILDING "Rumah Rakyat" profile:"Malaysian_Residential" { ... }
```

Profiles select wall types, beam sizes, door families, and furniture sets from the metadata catalog. Adding a new profile requires only SQL INSERT statements — no Java changes.

### Available Profiles

| Profile | Tradition | Wall Thickness | Storey Height |
|---------|-----------|---------------|---------------|
| `UK_Residential` | UK brick-cavity | 290mm | 3.3m |
| `US_Residential` | US frame-with-siding | 417mm | 2.4m |
| `Malaysian_Residential` | MY brick-plaster | 150mm | 2.8m |
| `Malaysian_Institutional` | MY institutional | 150mm | 4.0m |

### Two-Pass Resolution

All metadata lookups are profile-aware:
1. **Pass 1:** Search for rules matching the specific profile
2. **Pass 2:** Fall back to generic rules (profile = NULL)

Profile-specific rules automatically override generic rules.

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

## Fire Protection

### Automatic Triggers

| Trigger | Threshold | Action |
|---------|-----------|--------|
| Building height | > 18m | Sprinklers required |
| Floor area | > 1000m² | Sprinklers required |
| Occupancy | Assembly, High-rise | Sprinklers required |

### Compliance Modes

| Mode | Behavior |
|------|----------|
| (none) | No automatic fire protection |
| AUTO_FP | Generate sprinklers when triggered |
| FULL_COMPLIANCE | All code requirements enforced |

### Suppression Piping

When sprinklers are generated, the compiler creates the pipe network:

```
RISER (100mm) -- MAIN (65mm) -- BRANCH (25mm) --> SPRINKLER_HEAD
```

Pipe sizing follows NFPA 13 (Light Hazard).

---

## Output Formats

### Database Schema (SQLite)

| Table | Purpose |
|-------|---------|
| `spatial_structure` | Building hierarchy (Project > Site > Building > Storey) |
| `elements_meta` | Element metadata: guid, ifc_class, name, storey, discipline, material_name, material_rgba |
| `elements_rtree` | Spatial index: id, minX, maxX, minY, maxY, minZ, maxZ |
| `base_geometries` | Shared geometry (vertices/faces BLOBs, float32/int32 arrays) |
| `element_geometry` | Per-element geometry hash link |
| `element_transforms` | Per-element world position (center_x/y/z) |
| `surface_styles` | Material rendering properties (transparency, specularity) |
| `assembly_components` | BOM parent-child relationships |
| `mep_systems` | MEP system definitions |
| `system_nodes` / `system_edges` | MEP system graph |
| `simple_qto` | Quantity takeoff (area, volume, length) |

### Output Location

All output goes to `DAGCompiler/lib/output/`:

| File | Description |
|------|-------------|
| `ifc4_samplehouse.db` | SampleHouse compiled output |
| `ifc2x3_duplex.db` | Duplex compiled output |
| `sjtii_terminal.db` | Terminal compiled output |
| `tb_lktn.db` | TB-LKTN compiled output |

### Viewing Output

The primary viewer is the **Bonsai Federation addon** in Blender, which reads output SQLite DBs directly — loading geometry, materials, and transparency from the database.

---

## Material and Colour Data

### What's in the Output

Each element in `elements_meta` can have:

| Column | Description | Example |
|--------|-------------|---------|
| `material_name` | IFC material name | `Glass`, `Concrete Block` |
| `material_rgba` | RGBA colour (0.0-1.0) | `0.000,0.502,0.753,0.100` |

### RGBA Format

`R,G,B,A` values, each 0.0-1.0. Alpha = opacity (1.0 = opaque, 0.0 = transparent).
Example: `0.000,0.502,0.753,0.100` = blue glass, 10% opaque.

### Transparency Pipeline

Transparent elements (glass, shower screens) require both:
1. `elements_meta.material_name` matching a `surface_styles.style_name`
2. The `surface_styles` table (copied from component library during compilation)

Key transparent styles:

| Style | Transparency | Use |
|-------|-------------|-----|
| `Glass` | 0.9 | Windows, curtain wall |
| `Window_W1` | 0.6 | TB-LKTN standard windows |
| `Shower` | 0.3 | Shower screens |

### Querying Materials

```sql
-- All materials in output
SELECT material_name, COUNT(*), material_rgba
FROM elements_meta WHERE material_name IS NOT NULL
GROUP BY material_name;

-- Glass elements with transparency
SELECT guid, material_name, material_rgba
FROM elements_meta WHERE material_name = 'Glass';
```

---

## Validation and Proofs

### PlacementProver (14 Proofs)

After compilation, `PlacementProver.java` runs 14 mathematical proofs in 5 tiers:

| Tier | Proofs | What It Checks |
|------|--------|----------------|
| 1 | P01-P04 | Per-element arithmetic (positive dimensions, valid coordinates) |
| 2 | P05-P06 | Pairwise relations (no identical overlaps, adjacency) |
| 3 | P07-P09 | Host-element containment (door within wall, fixture within room) |
| 4 | P10-P12 | Topological closure (walls form enclosure, rooms bounded) |
| 5 | P13-P14 | Conservation laws (grid coverage, slab continuity) |

The prover is **non-blocking** — it reports violations but never prevents emission. The spatial X-ray score remains the ultimate arbiter.

### BoundElement (Dimensional Contract)

Every element bound to library geometry passes through `BoundElement`, which enforces:
- Mesh fits within bounding box
- Scale factors within [0.3, 3.0] per axis
- Violations tracked as degradations and reported as `[WITNESS]` entries

### GeometryIntegrityChecker

Validates mesh quality:
- Vertex-to-bbox containment (all vertices within element bounds)
- Mesh topology (manifold, closed surfaces)
- Vertex/face count consistency

### SpatialDigest (Determinism)

Computes SHA-256 hash of all element bounding boxes at 1mm precision. Same input must always produce the same digest — this detects non-deterministic compilation.

### Dimension Range Validation (DV010)

When onboarding a new IFC file, the pipeline checks every extracted element's
dimensions (W, D, H) against typical ranges mined from 20 real buildings
(415 rules, 25 IFC classes, stored in `disc_validation.db`).

The check runs automatically as a pre-flight in the IFC-to-BOM pipeline:

```
IFC → extract.py → ExtractionPopulator → DimensionRangeValidator → BOM builders
```

**What it flags:** Elements whose dimensions deviate by more than 5× from
observed ranges across all onboarded buildings. For example:
- IfcWall typical width: 800–10,269mm across 20 buildings
- A 95mm partition wall → flagged (thin but legitimate)
- A 500,000mm wall → flagged (data error)

**Advisory only** — outliers are logged but never block the pipeline:

```
[DimRange] MyBuilding: 120/120 checked, 115 PASS, 5 outliers
[DimRange]   IfcWall: 2 outlier(s)
[DimRange]     Wall-Partn: W=95mm (range [800-10269]mm)
```

The same mined rules also feed the PlacementValidator in the BIM Designer,
providing dimension sanity checks during interactive design.

To apply new mined rules after onboarding additional buildings:

```bash
./scripts/apply_mined_rules.sh           # uncomment + apply all DV_*_rules.sql
./scripts/apply_mined_rules.sh --dry-run  # preview without changes
```

---

## Spatial Scoring (X-Ray)

### Current Scores

| Building | Recall | Precision | F1 | Elements |
|----------|--------|-----------|------|----------|
| SampleHouse | **100%** | **100%** | **100%** | 55 |
| Duplex | **100%** | **100%** | **100%** | 1,085 |
| Terminal | **~100%** | **100%** | **~100%** | 51,088 |
| TB-LKTN | N/A (generative) | N/A | N/A | 58 |

### How It Works

The spatial X-ray compares dimension signatures between compiled output and reference:

```
signature = (category, L_mm, W_mm, H_mm)
```

Dimensions are sorted descending and bucketed to 10mm. Two matching modes:
- **Exact match:** identical signature tuple
- **Near match:** 5mm tolerance on thin dimension, 10% tolerance on larger dimensions

### Running the Checker

```bash
python3 DAGCompiler/python/spatial_checker.py \
  DAGCompiler/lib/output/ifc4_samplehouse.db \
  DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db \
  --discipline ARC
```

The `--discipline ARC` flag filters to architectural elements only, preventing MEP elements from drowning the architectural signal.

---

## MEP System Queries

### List All MEP Systems

```bash
sqlite3 DAGCompiler/lib/output/tb_lktn.db "SELECT * FROM mep_systems"
```

### View Drainage Paths

```sql
SELECT n1.name AS from_element, e.edge_type, n2.name AS to_element
FROM system_edges e
JOIN system_nodes n1 ON e.from_node_id = n1.node_id
JOIN system_nodes n2 ON e.to_node_id = n2.node_id
WHERE e.edge_type = 'DRAINS_TO'
```

---

## Troubleshooting

### "Unknown space type"

The room type is not in the metadata. Check available types:
```bash
sqlite3 library/component_library.db "SELECT name FROM ad_space_type"
```

### "BOM exceeds capacity"

The outlier log shows when calculated quantities don't fit:
```
[OUTLIER] canteen_table | CANTEEN "kantin" (8.0m x 8.0m)
  --> BOM wants 10 tables but only 6 fit (grid 2x3)
```

Solution: increase room size or accept the reduced quantity.

### Placement mode toggle

If scores drop unexpectedly, check the placement mode:
```bash
sqlite3 library/component_library.db \
  "SELECT * FROM ad_sysconfig WHERE config_key='placement_mode'"
```

Toggle: `UPDATE ad_sysconfig SET config_value='FLAT' WHERE config_key='placement_mode'`

### Witness claim FAILED

Check the specific claim in console output. Witness failures are non-blocking — the building still compiles, but the proof didn't hold. Investigate the reported coordinates.

---

## Quick Reference

```bash
# Build
mvn compile -q

# Compile all 4 buildings
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.SampleHouseEndToEndTest" -q
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNDuplexEndToEndTest" -q
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.TerminalEndToEndTest" -q
mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q

# Spatial fidelity check
python3 DAGCompiler/python/spatial_checker.py \
  DAGCompiler/lib/output/ifc4_samplehouse.db \
  DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db \
  --discipline ARC

# Query element count
sqlite3 DAGCompiler/lib/output/ifc4_samplehouse.db \
  "SELECT ifc_class, COUNT(*) FROM elements_meta GROUP BY ifc_class"

# Query materials
sqlite3 DAGCompiler/lib/output/ifc4_samplehouse.db \
  "SELECT material_name, material_rgba FROM elements_meta WHERE material_name IS NOT NULL"
```

---

## Further Reading

| Document | Purpose |
|----------|---------|
| `SourceCodeGuide.md` | Developer onboarding, pipeline internals, data provenance |
| `ARCHITECTURE.md` | Governing architecture document (v3.0) |
| `TheRosettaStoneStrategy.md` | Validation methodology and scoring |
| `PREFAB_ARCHITECTURE.md` | Assembly hierarchy and MANIFEST contracts |
| `RELATIONAL_PLACEMENT_SPEC.md` | Relational placement migration spec |
| `HARDCODE_AUDIT.md` | Hardcoded values audit (33 findings) |
| `BUNDLE_WORKER_FRAMEWORK.md` | BundleWorker interface and slot dispatch |
| `witness-system-specification.md` | Witness proof system design |
| `BIM_Designer.md` | Compliance layer + Bonsai addon vision |
| `CurrentState.txt` | Known issues for expert review |
| `WHITEPAPER.pdf` | Academic paper (v1.0 — scores being updated) |

---

*User Guide v1.0.0 — February 2026*
*4 buildings: SampleHouse 100%, Duplex 100%, Terminal ~100%, TB-LKTN 58 generative*
