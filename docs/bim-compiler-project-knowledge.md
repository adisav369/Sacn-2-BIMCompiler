# BIM COMPILER PROJECT KNOWLEDGE

## Executive Summary

Building a compiler that translates high-level building intent into valid IFC construction models. The approach uses pattern extraction from a professional Terminal building model (51,723 elements, LOD 400) rather than imagining rules.

**Current Status:** DSL → IFC pipeline working. Component library architecture designed. Sprinkler POC complete.

**Holy Grail Goal:** `"5 bedroom house"` → Valid IFC ready for permit

**Realistic Current Goal:** `TERMINAL gates:8` → Valid LOD 400 IFC

---

## Core Methodology

### PRIME RULE
```
EXTRACT, DON'T IMAGINE.
Query federated model DB. Copy patterns you find. Never invent.
```

### Why This Matters
Previous attempts failed because AI "drifts" — invents properties, relationships, rules that don't exist in real data. This methodology grounds every constant, pattern, and rule in extracted data with documented sources.

### Two Modes of Operation

| Mode | Use Case | Source |
|------|----------|--------|
| **Parametric** | Dynamic dimensions (walls, slabs, ducts) | Builds geometry from spec |
| **Library** | Fixed LOD 400 components (sprinklers, lights, columns) | Places existing geometry |

---

## Reference Model: Terminal Jetty Complex

### Database
- **File:** `enhanced_federation_GI.db` (234MB)
- **Location:** `~/IfcOpenShell/WORK_DIR/databases/`
- **Elements:** 51,723
- **Disciplines:** 9 (ARC, STR, ACMV, ELEC, FP, SP, CW, LPG, REB)
- **IFC Types:** 31

### LOD Level
**LOD 400** (fabrication-ready):
- Recognizable sprinkler heads
- Furniture (chairs visible)
- MEP fittings modeled
- Clash detection functional

### Key Database Tables
```
elements_meta      - Identity (guid, ifc_class, name, discipline)
elements_rtree     - Spatial (bounding boxes, R-tree index)
base_geometries    - Geometric (vertices, faces, geometry_hash)
element_instances  - Transforms (position in world)
spatial_structure  - Hierarchy (building → storey → space)
```

---

## 12 Extracted Generation Patterns

All patterns extracted from Terminal DB with SQL queries. Each has documented source.

| # | Pattern | Finding | Java Class |
|---|---------|---------|------------|
| G1 | Placement Anchor | ALL types = CENTER | Uniform convention |
| G2 | Orientation | Infer from bbox thin axis (82% door-wall align) | IOrientedElement, Vector3D |
| G3 | Routing Rules | Wall: 87-229mm, Ceiling: 440-727mm | RoutingConstraints |
| G4 | Connection Logic | Walls overlap 133mm, pipes insert 14-42mm | ConnectionPattern |
| G5 | Extrusion Direction | Columns/doors Z-up, plates X-axis | ExtrusionAxis |
| G6 | Profile Uniqueness | Plates 4.2x reuse, pipes 1.0x | InstancingStats |
| G7 | Opening-Wall Ratio | 55% width, 63.5% height typical | OpeningConstraints |
| G8 | Floor-to-Floor | 4m intervals, slabs overlap 175-250mm | StoreyConvention |
| G9 | MEP Zone | 830-916mm typical plenum | StoreyConvention |
| G10 | Termination | FP 1.18 fittings/pipe, SP 0.82 | TerminationPattern |
| G11 | Instancing | Plates max 17 instances, pipes unique | InstancingStats |
| G12 | Boundary | Only 21 elements at edge | Documented only |

### Key Construction Facts

| Fact | Value | Source |
|------|-------|--------|
| Coordinate precision | 5mm (TOLERANCE = 0.005) | DB analysis |
| Wall thicknesses | ONLY 150, 230, 250, 300mm | DB query (333 walls) |
| Units | METERS everywhere | Code pattern analysis |
| Transform convention | Bbox center, no rotation matrix | DB analysis |
| Sprinkler spacing | 4.6m max (NFPA 13) | Validator calibration |
| MEP-structure clearance | 50mm minimum | Industry standard |

---

## Architecture

### Layer Model
```
LAYER 4: Intent       "5 bedroom house"           [NOT BUILT - research]
    ↓
LAYER 3: Program      {rooms, adjacencies}        [NOT BUILT - needs solver]
    ↓
LAYER 2: Spatial      Grid positions, polygons    [WORKING - DSL]
    ↓
LAYER 1: Construction WallSpec, OpeningSpec       [WORKING - builders]
    ↓
LAYER 0: Geometry     Vertices, faces             [WORKING - validated]
    ↓
IFC FILE
```

### Package Structure
```
bim-compiler/
├── src/main/java/com/bim/compiler/
│   ├── topology/           # Extracted patterns (G1-G12)
│   │   ├── BIMConstants.java
│   │   ├── BIMObjectType.java (31 IFC types)
│   │   ├── Discipline.java (9 disciplines)
│   │   ├── WallThickness.java (4 values only)
│   │   ├── PipeDiameterRange.java
│   │   ├── OpeningConstraints.java
│   │   ├── RoutingConstraints.java
│   │   ├── StoreyConvention.java
│   │   └── ConnectionPattern.java
│   │
│   ├── geometry/           # Primitives
│   │   ├── Point3D.java
│   │   └── BoundingBox.java
│   │
│   ├── model/              # Interfaces (3-level abstraction)
│   │   ├── IBIMElement.java      # Level 1: Identity
│   │   ├── ISpatialElement.java  # Level 2: Spatial
│   │   └── IGeometricElement.java # Level 3: Geometric
│   │
│   ├── validation/         # Terminal-calibrated validators
│   │   ├── IValidator.java
│   │   ├── ValidationResult.java
│   │   ├── WallThicknessValidator.java
│   │   ├── PipeDiameterValidator.java
│   │   ├── OpeningPlacementValidator.java
│   │   ├── MepStructureClearanceValidator.java
│   │   ├── SprinklerSpacingValidator.java
│   │   └── MultiStoryElementValidator.java
│   │
│   ├── builder/            # Parametric builders
│   │   ├── IBuilder.java
│   │   ├── WallSpec.java
│   │   ├── WallBuilder.java
│   │   ├── OpeningSpec.java
│   │   ├── PipeSpec.java
│   │   └── PipeBuilder.java
│   │
│   ├── db/                 # Database access
│   │   └── FederatedDBReader.java
│   │
│   ├── dsl/                # DSL compiler
│   │   ├── RoomType.java (OmniClass codes)
│   │   ├── Direction.java
│   │   ├── RoomDefinition.java
│   │   ├── StoreyDefinition.java
│   │   ├── DSLParser.java
│   │   ├── GridLayoutResolver.java
│   │   ├── RoomCompiler.java
│   │   ├── RoomRequirements.java (IRC 2021)
│   │   ├── ConstructionSpec.java
│   │   ├── DSLExporter.java
│   │   └── BIMCompiler.java (main)
│   │
│   ├── library/            # Component library [IN PROGRESS]
│   │   ├── ComponentLibrary.java
│   │   ├── ComponentDefinition.java
│   │   ├── SprinklerPlacer.java
│   │   └── LibraryInstance.java
│   │
│   └── factory/            # Factory pattern [PLANNED]
│       ├── IElementFactory.java
│       ├── ParametricFactory.java
│       ├── LibraryFactory.java
│       └── HybridFactory.java
│
├── scripts/
│   ├── export_to_ifc.py
│   ├── export_dsl_to_ifc.py
│   ├── export_sprinklers_to_ifc.py
│   └── create_sprinkler_library.py
│
└── examples/
    └── test-house.bim
```

---

## DSL Language

### Current Grammar
```
STOREY "<name>" height:<meters>m {
    <ROOM_TYPE> "<name>" at:<grid> size:<w>x<h>m {
        DOOR <direction> to:<room>
        WINDOW <direction>
    }
}
```

### Example
```
STOREY "Ground" height:2.8m {
    BEDROOM "master" at:A1 size:5x3m {
        DOOR south to:corridor
        WINDOW north
    }
    BATHROOM "bath1" at:B1 size:3x2m {
        DOOR west to:master
    }
    CORRIDOR "corridor" at:A2-B2 width:1.2m {
        DOOR north to:master
        DOOR north to:bath1
    }
}
```

### Grid System
- Cell = 1m × 1m base unit
- Position `at:A1` = origin (0, 0)
- Size `size:5x3m` = 5m width, 3m depth

### Planned Extensions
```
# Library component placement
SPRINKLERS type:"K11.2" grid:4.6m attach:CEILING
LIGHTS type:"2x4_LED" grid:3m attach:CEILING
COLUMN type:"M_Rectangular_400x400" at:(0,0)

# Multi-storey
BUILDING "Terminal" {
    STOREY "Ground" level:0 height:4.0m { ... }
    STOREY "Departure" level:1 height:4.0m { ... }
}
```

---

## Validation Results

### Validator Calibration (Against Terminal)
| Validator | Errors | Warnings | Status |
|-----------|--------|----------|--------|
| Wall Thickness | 0 | 0 | ✓ Data-derived |
| Pipe Diameter | 0 | 0 | ✓ Calibrated |
| Opening Placement | 0 | 12 (2%) | ✓ Expected orphans |
| Multi-Story | 0 | 0 | ✓ 1001 INFO messages |
| MEP-Structure | 0 | 1744 | ✓ Intentional penetrations |
| Sprinkler Spacing | 0 | 10 | ✓ Isolated areas |

### Round-Trip Tests
| Test | Result |
|------|--------|
| Wall bbox match | 100% within 5mm |
| Wall vertex-level | Volume ratio 0.9998-1.0000 |
| Pipe discipline validation | 4/4 pass |
| Connection preservation | 13/13 relationships |
| IFC export/reimport | Lossless |

### Code Compliance (IRC 2021)
| Room Type | Min Area | Min Dimension | Window | Egress |
|-----------|----------|---------------|--------|--------|
| BEDROOM | 6.5 m² | 2.134 m | Yes | Yes |
| BATHROOM | - | - | No* | No |
| KITCHEN | - | - | No* | No |
| LIVING | 6.5 m² | 2.134 m | Yes | No |
| CORRIDOR | - | 0.914 m | No | No |

---

## Component Library (LOD 400)

### Schema
```sql
CREATE TABLE component_definitions (
    id INTEGER PRIMARY KEY,
    ifc_class TEXT NOT NULL,
    name TEXT NOT NULL,
    geometry_hash TEXT NOT NULL,
    
    -- Origin & Attachment
    origin_x REAL, origin_y REAL, origin_z REAL,
    attachment_face TEXT,  -- TOP, BOTTOM, SIDE, CENTER
    
    -- Orientation
    forward_axis TEXT DEFAULT 'Y',
    up_axis TEXT DEFAULT 'Z',
    default_rotation REAL DEFAULT 0,
    
    -- Dimensions
    bbox_width REAL, bbox_depth REAL, bbox_height REAL,
    
    -- Metadata
    instance_count INTEGER
);

CREATE TABLE connection_points (
    id INTEGER PRIMARY KEY,
    component_id INTEGER REFERENCES component_definitions(id),
    name TEXT,           -- "inlet", "outlet"
    point_x REAL, point_y REAL, point_z REAL,
    direction_x REAL, direction_y REAL, direction_z REAL,
    connection_type TEXT -- PIPE, DUCT, ELECTRICAL
);
```

### Sprinkler POC Results
| Metric | Value |
|--------|-------|
| Pendant instances | 683 |
| Upright instances | 198 |
| IFC size per sprinkler | 2.18 KB (with instancing) |
| Position accuracy | < 5mm |

---

## IFC Export

### Config-Driven Architecture
```
Java (owns patterns) → Config JSON → Python (generic exporter)
```

### IFC Relationships Exported
- `IfcRelContainedInSpatialStructure` — Element in Space/Storey
- `IfcRelSpaceBoundary` — Wall bounds Space
- `IfcRelVoidsElement` — Opening cuts Wall
- `IfcRelFillsElement` — Door fills Opening
- `IfcRelAggregates` — Building → Storeys
- `IfcMappedItem` — Geometry instancing

### OmniClass Codes (Table 13)
- BEDROOM: 13-21 11 00
- BATHROOM: 13-21 13 00
- CORRIDOR: 13-81 11 00
- DEPARTURE_LOUNGE: 13-11 21 00

---

## Gaps & Roadmap

### What's Built
- [x] Phase 0: Model Archaeology (12 patterns)
- [x] Phase 1: Topology Dictionary (21 files)
- [x] Phase 2: Validators (7, calibrated)
- [x] Phase 3: Builders (Wall, Pipe)
- [x] Phase 4: Connection validation (98.1%)
- [x] Phase 7: IFC Export (config-driven)
- [x] Phase 8A: DSL Language
- [x] Phase 8B: IRC 2021 validation
- [x] Phase 8C: Sprinkler Library POC

### In Progress
- [ ] Component Library (full extraction)
- [ ] Factory Pattern refactor
- [ ] Mathematical verification tests

### Planned
- [ ] Multi-storey DSL
- [ ] MEP grid placement
- [ ] All LOD 400 component placers
- [ ] Malaysian codes (MS 1064)

### Research (Future)
- [ ] Space Solver (constraint satisfaction)
- [ ] Intent Parser (NLP)

---

## Key Design Decisions

### 1. Parametric vs Library
**Decision:** Hybrid factory — parametric for dynamic dimensions, library for LOD 400 components.

**Rationale:** Terminal DB already has professional-grade geometry. Don't recreate as inferior parametric; place the real thing.

### 2. Grid-Based DSL
**Decision:** Human specifies grid positions (`at:A1`), compiler places walls.

**Rationale:** Avoids unsolved Space Solver problem. Crude but logical.

### 3. IFC-Native Vocabulary
**Decision:** Use IFC4 terms (IfcWallType, IfcSpaceType) and OmniClass codes, not invented taxonomy.

**Rationale:** Industry standard, no translation needed.

### 4. Validation Before Generation
**Decision:** Calibrate validators against Terminal before building generators.

**Rationale:** Ensures generated output matches professional quality.

### 5. Config-Driven Export
**Decision:** Java passes configuration JSON to Python exporter.

**Rationale:** Supports multiple typologies (Terminal, Residential) without code changes.

---

## Verification Audit (Passed)

All values traced to documented sources:

| Item | Source | Verified |
|------|--------|----------|
| TOLERANCE = 0.005 | Terminal FACT 1 | ✓ BIMConstants.java:21 |
| Wall 150/230/250/300mm | Terminal FACT 3 | ✓ WallThickness.java |
| Interior = 150mm | Terminal convention | ✓ RoomCompiler.java:25 |
| Exterior = 250mm | Terminal convention | ✓ RoomCompiler.java:26 |
| Bedroom min 6.5m² | IRC R304.1 | ✓ RoomRequirements.java:46 |
| Opening ratio 55%/65% | Terminal G7 | ✓ OpeningConstraints.java |

---

## Repository

- **URL:** https://github.com/red1oon/IfcOpenShell
- **Branch:** feature/IFC4_DB
- **Compiler Path:** ~/bim-compiler/

---

## Session Continuity

### When Starting New Chat

1. Reference this document for context
2. Check SESSION_STATE.md in repo for current phase
3. Use workdiary.txt for detailed history
4. Run verification tests before making changes

### Key Files for Context
```
~/bim-compiler/
├── claude.md              # Prime rule reminder
├── MASTER_CONTROL.md      # Phase gates
├── SESSION_STATE.md       # Current status
├── workdiary.txt          # Detailed log
└── docs/
    └── ARCHIVE_intent_compiler_method.md  # Design rationale
```

### Commands
```bash
# Build
cd ~/bim-compiler && mvn compile

# Run DSL compiler
java -cp target/classes com.bim.compiler.dsl.BIMCompiler examples/test-house.bim output.ifc

# Run tests
mvn test

# View verification report
cat verification_report.txt
```

---

## Adversarial Testing Notes

Claude Code tends to complete patterns, not challenge them. When reviewing Code's work:

1. **Ask it to break tests:** "Find a case where this passes but is wrong"
2. **Spot check actual code:** Don't trust summaries, view line numbers
3. **Mathematical verification before visual:** Numbers don't lie
4. **Check for circular tests:** Is it comparing object to itself?

---

## Contact & Credits

- **Developer:** Redhuan Oon (red1)
- **Organization:** Cruffee / Terminal 1/2 Jetty Complex project
- **Location:** Perlis, Malaysia
- **AI Assistance:** Claude (Anthropic) with Claude Code

---

*Last Updated: January 2025*
*Document Version: 1.0*
