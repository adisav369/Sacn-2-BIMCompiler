# PRIME RULE

**EXTRACT, DON'T IMAGINE.**

Query the federated model DB. Copy patterns you find. Never invent.

---

**When lost:** `cat SESSION_STATE.md` then query DB.

**After token refresh:** Read this file → state phase → continue.

---

# CURRENT STATUS (2026-01-29)

| Phase | Status | Description |
|-------|--------|-------------|
| Phase 0: Extraction | ✓ Complete | 12 patterns in SESSION_STATE.md |
| Phase 1: Interfaces | ✓ Complete | 21 files |
| Phase 2: Validators | ✓ Calibrated | 7 validators |
| Phase 3: Builders | ✓ Complete | Wall + Pipe builders |
| Phase 4: Connections | ✓ Passed | 98.1% validation |
| Phase 5: IFC Export | ✓ Complete | 6/6 round-trip tests |
| Phase 6: DSL Compiler | ✓ Complete | DSL → IFC pipeline working |
| Phase 7: Code Validation | ✓ Complete | IRC 2021 room requirements |
| Phase 8: Library Placer | ✓ Complete | 8,087 components, factory pattern |
| Phase 9: DSL → Library | ✓ Complete | Sprinklers in DSL, math verified |
| Phase 10: Unified Export | ✓ Complete | Walls + spaces + sprinklers in single IFC |
| Phase 10b: Assembly (BOM) | ✓ Complete | IfcElementAssembly + IfcRelAggregates |
| Phase 11: Shed DSL | ✓ Complete | Foundation, walls, door, window, gable roof |
| Phase 11b: Vertex Verification | ✓ Complete | 5/5 tests pass, geometry mathematically verified |
| Phase 12: BOM Export | ✓ Complete | 4 CSV formats for iDempiere ERP |
| Phase 13: Multi-Storey | ✓ Complete | BUILDING DSL, stairs, iDempiere costed BOM |
| Phase 14A: Library Stairs | ✓ Complete | HybridFactory routes IfcStairFlight to LibraryFactory |
| Phase 14B: Terminal Mini | ✓ Complete | DEPARTURE_LOUNGE, GATE + MEP grids |
| Phase 15: Layer 3 Integration | ✓ Complete | Constraint solver wired into DSL pipeline |
| Phase 15B: Interior Walls | ✓ Complete | Auto-doors, auto-windows, 8/8 geometric proof |
| Phase 16: DSL Extensibility | ✓ Complete | `aligns:` constraint, docs, roadmap |
| Phase 17: Vertical Vocab | ✓ Complete | `above:`, `below:`, `stack:` constraints |
| Phase 18: Consolidation | ✓ Complete | 9/9 integration tests, all features working |
| Phase 19: Gap Closure | ✓ Complete | 10-room solver, stair/landing, IFC export verified |
| Phase 20: Intent Resolver | ✓ Complete | NL → DSL → IFC pipeline, 4/4 tests pass |
| Phase 21A: Validator Framework | ✓ Complete | HabitabilityValidator (IBC/IRC) |
| Phase 21B: Adjacency Fix | ✓ Complete | Door detection fix + room snapping |
| Phase 22: Fixtures | ✓ Complete | Toilet, sink, exhaust fan placement (IBC clearances) |
| Phase 23: Structural | ✓ Complete | Columns at corners/T-junctions, lintels over openings |
| Phase 24: HVAC | ✓ Complete | Diffusers per ASHRAE 62.1 (supply/return/exhaust) |
| Phase 25: Outlier Handling | ✓ Complete | Graceful degradation + developer guidance |
| **Phase 28: Validation Factory** | ✓ Complete | Dynamic validator composition + TB-LKTN proof |

**GitHub:** https://github.com/red1oon/BIMCompiler

---

# COMPLETE PIPELINE

**Multi-Storey (Phase 13):**
```
BUILDING DSL
    │
    ▼
BuildingParser → BuildingDefinition (storeys, stairs, rooms)
    │
    ▼
BuildingCompiler → BuildingSpec (IRC-compliant geometry)
    │
    ▼
BuildingWriter → SQLite DB (49 elements, assemblies)
    │
    ├──► BOMExporter → 4 CSV files
    ├──► IDempiereExporter → M_Product + costed BOM (MYR 17,504)
    │
    ▼
export_building_to_ifc.py → duplex_test.ifc (37.8 KB)
```

**Single-Storey (Phase 6):**
```
DSL Input → DSLParser → RoomCompiler → DSLExporter → export_dsl_to_ifc.py
```

---

# DSL SYNTAX (Complete)

**Single Storey / Shed:**
```
STOREY "Ground" height:2.8m {
    BEDROOM "master" at:A1 size:5x4m {
        DOOR south to:corridor
        WINDOW north
        SPRINKLERS grid:4.6m
    }
}
```

**Multi-Storey Building (Phase 13):**
```
BUILDING "duplex" {
    STOREY "Ground" level:0 height:2.8m {
        LIVING "main" at:A1 size:4x4m {
            DOOR south
            WINDOW east
        }
        STAIR "main" at:B1 width:1.0m to:"Upper"
    }
    STOREY "Upper" level:1 height:2.8m {
        BEDROOM "bed1" at:A1 size:4x4m {
            DOOR south to:landing
        }
        LANDING "landing" at:B1 size:2x2m from:"main"
    }
    ROOF pitch:15deg
}
```

---

# HYBRID ARCHITECTURE

```
MODE A: Parametric Builder (WallBuilder, PipeBuilder)
├── Dynamic dimensions
├── Creates geometry from spec
└── Use for: walls, slabs, ducts

MODE B: Library Placer (HybridFactory → LibraryFactory)
├── Fixed LOD500 geometry from component library
├── IfcMappedItem instancing (2.18 KB/sprinkler)
└── Use for: sprinklers, lights, fixtures
```

**Factory Pattern:**
```java
HybridFactory factory = new HybridFactory(library);

// Library placement (sprinklers, lights)
List<ISpatialElement> sprinklers = factory.createGrid(gridSpec);

// Parametric (walls, pipes) - TODO: integrate
ISpatialElement wall = factory.create(parametricSpec);
```

---

# COMPONENT LIBRARY (Phase 8)

**Database:** `library/component_library.db` (113 MB)
- 8,087 unique components across 13 IFC types
- Tables: component_types, component_definitions, component_geometries, placement_rules

**Component counts:**
| Category | Count |
|----------|-------|
| PIPE_FITTING | 4,198 |
| SPRINKLER | 891 |
| LIGHT | 801 |
| DUCT_FITTING | 683 |
| BEAM | 404 |
| Others | 1,110 |

---

# MATHEMATICAL VERIFICATION (Phase 9)

**GridPlacementMathTest.java** - 5/5 PASS

| Test | Formula/Invariant | Result |
|------|-------------------|--------|
| Grid count | `n = floor((dim - spacing/2) / spacing) + 1` | Verified |
| Position | First head at `(spacing/2, spacing/2)` | (2.30, 2.30) ✓ |
| Coverage | All corners ≤ spacing from nearest head | Verified |
| Edge cases | 6 room sizes tested | All match |
| Pendant invariant | `attachment_face = TOP` | Verified |

**Sprinkler Z Verification:**
- Ceiling height: 2.8m
- Sprinkler top Z: 2.771m
- Delta: 29mm (deflector drop, NOT 830mm plenum zone)

**ShedVertexVerification.java** - 5/5 PASS (Phase 11)

| Test | Verification | Result |
|------|-------------|--------|
| Corner Vertex Sharing | All 4 corners: distance = 0.0000m | ✓ PASS |
| Z-Continuity | Wall-Slab: 0mm, Wall-Roof: 0mm | ✓ PASS |
| Opening Void | IFC relationship-based | ✓ INFO |
| Manifold Check | 21 closed (Euler=2), 1 boundary | ✓ PASS |
| Junction Coordinates | All documented | ✓ PASS |

**Key Coordinates:**
- Corners: SW(0,0,0), SE(4,0,0), NW(0,3,0), NE(4,3,0)
- Wall height: Z ∈ [0, 2.4]m
- Roof ridge: Z=2.936m, 300mm overhang

---

# GENERATED MODEL PERSISTENCE

**GeneratedModelWriter.java** writes to Terminal DB schema:
- elements_meta, element_transforms, elements_rtree
- base_geometries, element_instances, spatial_structure
- global_offset

**Output:** `output/generated_model.db`

**Cross-verification query:**
```sql
SELECT ifc_class, COUNT(*), AVG(maxZ - minZ) as avg_height
FROM elements_meta JOIN elements_rtree USING(id)
GROUP BY ifc_class
```

---

# KEY FILES

```
src/main/java/com/bim/compiler/
├── dsl/
│   ├── DSLParser.java           # Parses single-storey DSL
│   ├── BuildingParser.java      # Parses BUILDING multi-storey DSL
│   ├── BuildingCompiler.java    # Compiles to BuildingSpec + validation
│   ├── BuildingDefinition.java  # Data model with profile/protocol/lod
│   ├── BuildingWriter.java      # Writes to DB + JSON export
│   ├── ProfileRegistry.java     # Profile definitions (Phase 28)
│   ├── ProtocolValidator.java   # Protocol definitions (Phase 28)
│   ├── WallGenerator.java       # WallRule-based generation (Phase 28)
│   ├── RoomType.java            # SpaceType + WallRule enum
│   ├── TBLKTNProofTest.java     # Mathematical proof (Phase 28)
│   └── ValidationIntegrationTest.java # 5/5 tests (Phase 28)
├── validation/building/
│   ├── ValidatorChain.java      # Orchestrates validators
│   ├── ValidatorFactory.java    # Dynamic composition (Phase 28)
│   ├── GeometryValidator.java   # Wall connectivity, OPEN_PLAN check
│   ├── HabitabilityValidator.java # Light, egress, dimensions
│   ├── ProfileValidatorRegistry.java  # 4 profiles (Phase 28)
│   ├── ProtocolValidatorRegistry.java # 4 protocols (Phase 28)
│   └── LODValidatorRegistry.java      # LOD 100-500 (Phase 28)
├── factory/
│   ├── HybridFactory.java       # Routes to Library or Parametric
│   ├── LibraryFactory.java      # Creates from component library
│   ├── GridPlacementSpec.java   # Grid-based placement spec
│   └── GridPlacementMathTest.java # Mathematical verification
├── library/
│   ├── ComponentLibrary.java    # Reads component_library.db
│   ├── FixturePlacer.java       # Bathroom/kitchen fixtures
│   ├── StructuralPlacer.java    # Columns, lintels
│   ├── HVACPlacer.java          # Supply/return/exhaust
│   └── SprinklerPlacer.java     # Grid + single placement
├── solver/
│   └── SpaceSolver.java         # Choco CSP solver
├── db/
│   ├── GeneratedModelWriter.java # Writes to Terminal DB schema
│   └── GeneratedModelTest.java   # Integration test
├── export/
│   ├── BOMExporter.java         # BOM → CSV (4 formats)
│   └── IDempiereExporter.java   # iDempiere M_Product + M_BOM CSV
└── util/
    ├── BIMLogger.java           # Debug logging
    └── OutlierLogger.java       # Outlier tracking (Phase 25)

library/
└── component_library.db         # 113MB, 8,087 components

scripts/
├── export_dsl_to_ifc.py         # Single-storey DSL → IFC
└── export_building_to_ifc.py    # Multi-storey DB → IFC (Phase 13)

output/
├── duplex_test.db               # Multi-storey test (49 elements)
├── duplex_test.ifc              # Exported IFC (37.8 KB)
├── shed_test.db                 # Shed test
└── generated_model.db           # Generic output
```

---

# DATABASES

```
Terminal DB: /home/red1/IfcOpenShell/WORK_DIR/databases/enhanced_federation_GI.db
├── 51,723 elements, 234MB
└── Reference for patterns

Component Library: /home/red1/bim-compiler/library/component_library.db
├── 8,087 unique components, 113MB
└── LOD500 geometry + placement rules

Generated Model: /home/red1/bim-compiler/output/generated_model.db
├── Same schema as Terminal DB
└── Ready for bake script → Blender
```

---

# ENVIRONMENT SETUP (2026-01-27)

## Blender + Bonsai Installation

| Component | Version | Location |
|-----------|---------|----------|
| Blender | 5.0.1 | `/snap/bin/blender` |
| Bonsai | 0.8.5-alpha | `~/.config/blender/5.0/extensions/user_default/bonsai/` |
| Federation module | Custom | Included in Bonsai wheel (3708 files) |
| IfcOpenShell (venv) | 0.8.4.post1 | `venv/lib/python3.12/site-packages/ifcopenshell/` |
| IfcOpenShell (source) | git clone | `/home/red1/IfcOpenShell/` |

## Launch Commands

```bash
# Launch Blender with Bonsai (shortcut)
bonsai

# Or directly
blender
```

## First-Time Bonsai Setup

1. Run `bonsai` command
2. Go to **Edit > Preferences > Extensions**
3. Enable **"Bonsai"** addon
4. Restart Blender

## Key Paths

```
~/bin/bonsai                    # Launch script
~/.config/blender/5.0/extensions/user_default/bonsai/  # Bonsai addon
~/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/  # Federation source
~/bim-compiler/venv/            # Python venv with IfcOpenShell 0.8.4
```

---

# BOM EXPORT (Phase 12)

**BOMExporter.java** exports 4 CSV formats:

| File | Purpose | Content |
|------|---------|---------|
| `*_bom_detailed.csv` | iDempiere product import | All components with dimensions |
| `*_bom_aggregated.csv` | Purchasing | Grouped by material, summed qty |
| `*_cut_list.csv` | Fabrication shop | Profiles + cut lengths + waste % |
| `*_bom_assemblies.csv` | Manufacturing | Hierarchical assembly → components |

**Sample output (shed 4x3m):**
- RHS 150x100: 16 pieces, 47.2m total length
- Metal Deck: 33.6 m² cladding
- Foundation: 12 m² slab

---

# MULTI-STOREY + iDEMPIERE (Phase 13) - COMPLETE

**BuildingParser + BuildingCompiler + IFC Export** - All tests pass

## DSL Constructs

| Construct | Syntax | IFC Output |
|-----------|--------|------------|
| BUILDING | `BUILDING "name" { ... }` | IfcBuilding |
| STOREY | `STOREY "name" level:N height:Xm { ... }` | IfcBuildingStorey |
| STAIR | `STAIR "name" at:B1 width:1m to:"Upper"` | IfcStairFlight |
| LANDING | `LANDING "name" at:B1 size:2x2m from:"stair"` | IfcSlab (LANDING) |
| DOOR | `DOOR south` | IfcDoor |
| WINDOW | `WINDOW east` | IfcWindow |
| ROOF | `ROOF pitch:15deg` | IfcRoof |

## Verification Summary

| Check | Result |
|-------|--------|
| IRC Stair Compliance | Riser 187mm ≤ 196mm ✓, Tread 267mm ≥ 254mm ✓ |
| Z-Continuity | Ground→Upper: 0.0000m gap ✓ |
| Slab Overlap | 200mm (Pattern G8) ✓ |
| IFC Export | 49 elements, 37.8 KB ✓ |
| Stair Z-range | [0.00 - 2.80] ✓ |
| Landing Z-range | [2.65 - 2.80] ✓ |

## IFC Element Counts (duplex_test.ifc)

```
IfcMember:        32 (steel frames)
IfcPlate:          8 (cladding)
IfcSlab:           3 (foundation + floor + landing)
IfcDoor:           2
IfcWindow:         2
IfcStairFlight:    1
IfcRoof:           1
───────────────────
Total:            49 elements
```

## iDempiere Costed BOM (MYR)

| Item | Qty | Cost |
|------|-----|------|
| Steel frames (RHS 150x100) | 113.6 LM | 5,112.00 |
| Metal cladding | 96.0 M² | 3,046.40 |
| Foundation slab | 3.56 M³ | 1,354.32 |
| Floor slab | 2.18 M³ | 828.40 |
| Stair flight | 1 EA | 2,800.00 |
| Doors | 2 EA | 1,700.00 |
| Windows | 2 EA | 900.00 |
| Landing | 4 M² | 720.00 |
| Roof | 21.2 M² | 804.08 |
| **GRAND TOTAL** | | **17,504.33** |

## Output Files

```
output/
├── duplex_test.db          # 49 elements, assemblies, spatial structure
├── duplex_test.ifc         # 37.8 KB, valid IFC4
├── duplex_test.json        # JSON intermediate
├── bom/
│   ├── duplex_bom_detailed.csv
│   ├── duplex_bom_aggregated.csv
│   ├── duplex_cut_list.csv
│   └── duplex_bom_assemblies.csv
└── idempiere/
    ├── idempiere_products.csv    # 9 M_Product SKUs
    ├── idempiere_bom.csv         # Assembly → Components
    └── idempiere_costed_bom.csv  # MYR 17,504.33
```

## Key Scripts

```
scripts/
├── export_dsl_to_ifc.py        # Single-storey export
└── export_building_to_ifc.py   # Multi-storey DB → IFC
```

---

# MEP DSL PATTERNS (Phase 14B)

## Syntax
```
ROOM "name" at:A1 size:WxDm {
    SPRINKLERS grid:4.6m        # NFPA 13 light hazard
    LIGHTS grid:3.0m            # Configurable spacing
    LIGHTS grid:2.5m type:panel # With fixture type
}
```

## Grid Math
```
Sprinkler count = ceil(width/spacing) × ceil(depth/spacing)
Light count = ceil(width/spacing) × ceil(depth/spacing)
Z position = storeyBaseZ + storeyHeight - 0.05m (50mm below ceiling)
```

## Verification Rules
| Rule | Formula | Example |
|------|---------|---------|
| Coverage | count × spacing² ≥ room_area | 15 × 21.16 = 317 ≥ 240m² |
| Spacing | ≤ 4.6m (NFPA 13 Light Hazard) | 4.6m ✓ |
| Position | First at (spacing/2, spacing/2) | (2.3, 2.3) |

## Terminal Room Types
| Type | OmniClass | Min Dimension | Requirements |
|------|-----------|---------------|--------------|
| DEPARTURE_LOUNGE | 13-11 21 00 | 6m min, 100m² | Sprinklers required |
| GATE | 13-11 21 11 | 6×8m (48m²) | Window for airside |
| CONCOURSE | 13-81 11 11 | 3m width | High traffic |

## IFC Class Mapping
| DSL Element | IFC Class | Description |
|-------------|-----------|-------------|
| SPRINKLERS | IfcFlowTerminal | Fire protection |
| LIGHTS | IfcLightFixture | Electrical |

## Cost Mapping (iDempiere)
| Item | SKU | Unit Cost |
|------|-----|-----------|
| Fire Sprinkler | SPRINKLER-PENDANT | MYR 85 |
| Light Fixture | LIGHT-RECESSED-LED | MYR 180 |

---

# SPACE SOLVER + CONSTRAINTS (Phase 15-17)

## Status: Production Ready + Mathematically Verified

Constraint-based room placement with auto-generated walls, doors, windows.
8/8 geometric proof checks pass. Extended vertical vocabulary (Phase 17).

## DSL Syntax (Working)
```dsl
BEDROOM "master" size:4x4m {
    adjacent: ensuite        // Must share wall + auto-door
    exterior: north          // Must touch building edge + auto-window
    not_adjacent: kitchen    // Cannot share wall
    aligns: bath_lower       // [Phase 16] Same X,Y as room on other storey
    above: living_room       // [Phase 17] Must be directly above target
    below: bedroom_upper     // [Phase 17] Must be directly below target
    stack: plumbing_core     // [Phase 17] Named vertical group
}
```

## Constraint Types
| Constraint | Syntax | Description | Auto-generates |
|------------|--------|-------------|----------------|
| ADJACENT | `adjacent:room` | Must share wall | Interior wall + door |
| NOT_ADJACENT | `not_adjacent:room` | Cannot share wall | - |
| EXTERIOR | `exterior:dir` | On building edge | Window |
| ALIGNS | `aligns:room` | Vertical alignment | Cross-storey alignment |
| ABOVE | `above:room` | Above target room | Cross-storey (higher level) |
| BELOW | `below:room` | Below target room | Cross-storey (lower level) |
| STACK | `stack:name` | Named vertical group | All rooms share X,Y |

## Vertical Constraint Use Cases
| Constraint | Use Case |
|------------|----------|
| `aligns:` | Generic vertical alignment (any direction) |
| `above:` | Bedrooms above living spaces |
| `below:` | Ground floor aligned with upper explicit layout |
| `stack:` | MEP risers, plumbing cores, service shafts |

## Performance
| Rooms | Solve Time |
|-------|------------|
| 4 | 43ms |
| 6 | 67ms |

## Geometric Proof (8/8 PASS)
1. Wall Connectivity - corners meet within 5mm
2. Interior Walls - connect adjacent rooms
3. Door Placement - centered in shared walls
4. Window Placement - on exterior walls only
5. Room Enclosure - all rooms fully enclosed
6. No Overlap - 0.0 m² overlap between rooms
7. Adjacency Constraints - satisfied with walls + doors
8. IFC Relationships - valid

## Files
- `SpaceSolver.java` - Production CSP solver (Choco)
- `GeometricProofTest.java` - 8-check mathematical verification
- `TownhouseAlignsTest.java` - Vertical alignment test (aligns:)
- `VerticalConstraintTest.java` - Extended vertical test (above:/stack:)
- `BelowConstraintTest.java` - Below constraint test
- `IntegratedTownhouseTest.java` - Full integration test (Phase 18)
- `docs/DSL_EXTENSION_GUIDE.md` - How to add constraints
- `docs/VOCABULARY_ROADMAP.md` - Future vocabulary

## Test Suite Summary (Phase 18 Consolidation)

| Test | Scope | Result |
|------|-------|--------|
| GeometricProofTest | 8 geometry checks | 8/8 PASS |
| TownhouseAlignsTest | aligns: constraint | 3/3 PASS |
| VerticalConstraintTest | above: + stack: | 4/4 PASS |
| BelowConstraintTest | below: constraint | 3/3 PASS |
| IntegratedTownhouseTest | Full feature integration | 8/8 PASS |
| BuildingTest | Core compilation | 8/8 PASS |

**Integration Test Features Verified:**
- Multi-storey building (2 floors)
- Horizontal constraints (adjacent, exterior)
- Vertical constraints (above, stack)
- MEP systems (2 sprinklers, 16 lights)
- Auto-generated walls (8+9 = 17 walls)
- Auto-generated openings (2 doors, 2 windows)
- Z-continuity (0.0000m gap)

---

# INTENT RESOLVER (Phase 20)

## Status: Production Ready

Natural language frontend for building generation. No LLM in critical path.

## Usage

```bash
# Full pipeline: NL → IFC
./scripts/compile_intent.sh "3 bedroom house with ensuite" output/my_house

# Step by step:
python scripts/intent_resolver.py "3 bedroom house" output/spec.json
java -cp target/classes com.bim.compiler.dsl.IntentCompiler output/spec.json output/
python scripts/export_building_to_ifc.py output/building.db output/building.ifc
```

## Supported Intents

| Pattern | Example |
|---------|---------|
| N bedroom | "3 bedroom house" |
| building type | apartment, house, townhouse, flat, studio |
| modifiers | large, compact, open plan |
| features | with ensuite, with garage, with study |

## Test Results

| Intent | Rooms | Storeys | Solver | IFC |
|--------|-------|---------|--------|-----|
| "2 bedroom apartment" | 5 | 1 | 21ms | ✓ |
| "3 bedroom house with ensuite" | 7 | 1 | 25ms | ✓ |
| "open plan 2 bedroom flat" | 5 | 1 | 4ms | ✓ |
| "large 4 bedroom townhouse" | 8 | 2 | 0ms | ✓ |

## Files

- `scripts/intent_resolver.py` - NL parser (spaCy)
- `IntentCompiler.java` - JSON → DSL converter
- `scripts/compile_intent.sh` - End-to-end pipeline

---

# PHASES 21-24: GOVERNANCE + AUTO-PLACEMENT

## Phase 21: Governance Layer
- **HabitabilityValidator.java** - IBC/IRC compliance checks
- Validates: natural light, egress, emergency egress, min dimensions
- Door detection fix: strip `_door` suffix from adjacency names
- Adjacency snapping: `snapAdjacentRooms()` closes solver grid gaps

## Phase 22: Fixture Placement
- **FixturePlacer.java** - Auto-places bathroom/kitchen fixtures
- IBC clearances: toilet 15" side/21" front, sink 21" front
- Clash detection: priority-based (toilet > sink > exhaust)
- Library components: Toilet, MRV sink, Exhaust air grill

## Phase 23: Structural Elements
- **StructuralPlacer.java** - Columns + lintels from library
- Corner columns: 4 at building perimeter
- T-junction columns: where interior walls meet exterior
- Lintels: over openings ≥ 900mm (doors, windows)

## Phase 24: HVAC Diffusers
- **HVACPlacer.java** - Supply/return/exhaust placement
- ASHRAE 62.1 ventilation rates by room type
- CFM calculation: `Volume × ACH / 60 × 35.31`
- Supply/Return balance: CFM-based matching
- Exhaust: auto-added for bathroom/kitchen

## StoreySpec Structure (Phase 24)
```java
public record StoreySpec(
    String name, int level, double baseZ, double height,
    SlabSpec slab, List<WallAssemblySpec> walls,
    List<RoomSpec> rooms, List<StairSpec> stairs,
    List<DoorSpec> doors, List<WindowSpec> windows,
    List<LandingSpec> landings,
    List<SprinklerSpec> sprinklers,  // Phase 14B
    List<LightSpec> lights,          // Phase 14B
    List<FixtureSpec> fixtures,      // Phase 22
    List<ColumnSpec> columns,        // Phase 23
    List<BeamSpec> beams,            // Phase 23
    List<DiffuserSpec> diffusers     // Phase 24
)
```

## Integration Test Results
```
Storey: Ground (level 0)
  Rooms: 4
  Fixtures: 3 (toilet, sink, exhaust fan)
  Columns: 5 (4 corner + 1 T-junction)
  Beams/Lintels: 5
  Diffusers: 10 (4 supply, 4 return, 2 exhaust)
  Total CFM: 2900
RESULT: PASS
```

## Test Commands
```bash
# Individual placer tests
mvn exec:java -Dexec.mainClass="com.bim.compiler.library.FixturePlacerTest" -q
mvn exec:java -Dexec.mainClass="com.bim.compiler.library.StructuralPlacerTest" -q
mvn exec:java -Dexec.mainClass="com.bim.compiler.library.HVACPlacerTest" -q

# Full integration
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.StructuralIntegrationTest" -q
```

---

# MEP TRIFECTA COMPLETE

| System | Phase | Placement Logic | Test |
|--------|-------|-----------------|------|
| Fire Protection | 14B | NFPA 13 grid (4.6m) | SprinklerPlacerTest |
| Electrical | 14B | Configurable grid | - |
| HVAC | 24 | ASHRAE 62.1 CFM | HVACPlacerTest |

---

# PHASE 25: OUTLIER HANDLING FRAMEWORK

## Status: Complete

Graceful degradation for unknown inputs with actionable developer guidance.

## OutlierLogger Utility

**File:** `util/OutlierLogger.java`

**Categories:**
| Category | Detection Point | Action |
|----------|-----------------|--------|
| UNKNOWN_SPACETYPE | RoomType.fromKeyword() | Fallback cascade to GENERIC |
| MISSING_COMPONENT | ComponentLibrary | Fuzzy match or skip |
| UNSATISFIABLE | SpaceSolver | Constraint relaxation |
| GEOMETRY_IMPOSSIBLE | FixturePlacer | Skip with dimensions |
| VALIDATION_UNKNOWN | HabitabilityValidator | GENERIC rules |
| VOCABULARY_GAP | Intent resolver | Log for developer |

**Log Format:**
```
[OUTLIER:CATEGORY] component | context | action
  → GUIDANCE: developer action
```

## SpaceType Fallback Cascade

```
1. Exact match (BEDROOM → BEDROOM)
2. Partial match (MASTER_BEDROOM → BEDROOM)
3. Category guess (WC → BATHROOM, SUNROOM → LIVING)
4. GENERIC fallback
```

## Solver Constraint Relaxation Order

When constraints are unsatisfiable, drop in priority order:
```
1. NOT_ADJACENT (drop first)
2. ALIGNS
3. EXTERIOR
4. ADJACENT (drop last - connectivity important)
```

## Compilation Summary

- `OutlierLogger.reset()` at compilation start
- Track total elements for rate calculation
- `OutlierLogger.summarize()` writes `outliers.log`
- Target: <5% outlier rate, <10% fallback rate

## Test Results (9/9 PASS)

```bash
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.OutlierHandlingTest" -q
```

---

# TEST COVERAGE AUDIT

| Package | Coverage | Notes |
|---------|----------|-------|
| Library (placers) | 80% | ComponentLibrary lacks direct test |
| Validation | 8% | Only framework-level test |
| Builder | 29% | Wall + Pipe have tests |
| Export | 25% | IFC has round-trip test |
| Solver | 0% | Indirectly tested via DSL |
| Factory | 0% | Only math tests |

**Strong DSL integration tests (21 files) provide pipeline-level coverage.**

---

# PHASE 28: VALIDATION FACTORY + TB-LKTN GRID BOUNDS

## Status: Complete (21/21 tests pass)

Dynamic validator composition based on building metadata. TB-LKTN Malaysian house validated.

## Validation Pipeline

```
DSL → BuildingParser.parse() → BuildingDefinition
                                    │
                                    ▼
              BuildingCompiler.compile() → BuildingSpec
                                    │
                                    ▼
              ValidatorFactory.create(def) → ValidatorChain
                    │
                    ├── GeometryValidator (required - always)
                    ├── HabitabilityValidator (required - always)
                    ├── ProfileValidator (from profile:)
                    ├── ProtocolValidator (from protocol:)
                    └── LODValidator (from lod:)
                                    │
                                    ▼
              Critical failures → RuntimeException
              Warnings → Print report, continue
```

## New DSL Syntax (Phase 28)

```dsl
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
        OPEN_PLAN "common" bounds:B2-D5 {
            zones: LIVING, DINING, KITCHEN
            exterior: south
            exterior: north
        }

        BEDROOM "bilik_utama" bounds:A2-B4 {
            exterior: west
            opens_to: common
        }

        PORCH "anjung" bounds:C1-D2 {
            exterior: south
            roof: ATTACHED
        }
    }

    ROOF pitch:25deg overhang:600mm
}
```

## Grid Bounds Compilation

Room coordinates computed from cumulative grid spacing:

| Room | Bounds | Computed Coordinates | Area |
|------|--------|---------------------|------|
| anjung | C1-D2 | X[4.40-8.10] Y[0.00-2.30] | 8.5m² |
| common | B2-D5 | X[1.30-8.10] Y[2.30-8.50] | 42.2m² |
| bilik_utama | A2-B4 | X[0.00-1.30] Y[2.30-6.90] | 6.0m² |

## Validator Registries

| Registry | Maps | Examples |
|----------|------|----------|
| ProfileValidatorRegistry | Profile → Validator | Malaysian_Residential, US_IRC, UK, Commercial |
| ProtocolValidatorRegistry | Protocol → Validator | SingleStorey, MultiStorey, Apartment, Commercial |
| LODValidatorRegistry | LOD → Validator | LOD 100-500 |

## Key Features

| Feature | Implementation |
|---------|----------------|
| Grid bounds | `bounds:A2-B4` → coordinates from GridDef |
| Opens to | `opens_to: common` → auto-door generation |
| Exterior list | `exterior: south, north` → multiple windows |
| OPEN_PLAN validation | No interior walls allowed inside OPEN_PLAN |
| PORCH exemption | Porches exempt from door requirement |
| Profile/Protocol | Regional code + building type validation |

## New Files (Phase 28)

```
src/main/java/com/bim/compiler/
├── dsl/
│   ├── ProfileRegistry.java           # Profile definitions
│   ├── ProtocolValidator.java         # Protocol definitions
│   ├── WallGenerator.java             # WallRule-based generation
│   ├── ValidationIntegrationTest.java # 5/5 tests
│   ├── ProfileProtocolTest.java       # 10/10 tests
│   ├── RoofGenerationTest.java        # 6/6 tests
│   └── TBLKTNProofTest.java          # Mathematical proof
├── validation/building/
│   ├── ValidatorFactory.java          # Dynamic composition
│   ├── ProfileValidatorRegistry.java  # 4 profiles
│   ├── ProtocolValidatorRegistry.java # 4 protocols
│   └── LODValidatorRegistry.java      # LOD 100-500
└── util/
    └── OutlierLogger.java             # Enhanced logging
```

## Test Commands

```bash
# Run TB-LKTN mathematical proof
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNProofTest" -q

# Run validation integration test
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.ValidationIntegrationTest" -q

# Run all Phase 28 tests
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.ProfileProtocolTest" -q
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.RoofGenerationTest" -q
```

## TB-LKTN Validation Report

```
| Validator                      | Required | Critical | Status |
|--------------------------------|----------|----------|--------|
| GeometryValidator              | YES      | 0        | PASS   |
| HabitabilityValidator          | YES      | 0        | PASS   |
| MalaysianResidentialValidator  | no       | 0        | PASS   |
| ResidentialSingleStoreyValidator| no      | 0        | PASS   |
| LOD300Validator                | no       | 0        | PASS   |

[SUCCESS] TB-LKTN PASSES all validation checks
```

---

# NEXT STEPS

1. **Dictionary-driven validation** - Move rules from Java to DSL dictionary
2. **Roof variants** - Flat, hip (currently only pitched)
3. **IFC export for new elements** - Diffusers, columns, beams
4. **Multi-storey structural** - Column stacking, shafts

**Phase 28 Complete: Validation is now extensible via factory pattern.**

## Architecture Complete Through Layer 4 + Governance + Validation Factory

```
Layer 4: Intent       "5 bedroom house"              [COMPLETE - spaCy NLP]
         ↓            intent_resolver.py
Layer 3: Program      {rooms, constraints}           [COMPLETE - Choco]
         ↓            SpaceSolver.java + relaxation
Layer 2: Spatial      Grid positions                 [COMPLETE - DSL]
         ↓            BuildingParser/Compiler
Layer 1: Construction Specs + Components             [COMPLETE - HybridFactory]
         ↓            Library (8,701) + Parametric
Layer 0: Geometry     Vertices, faces                [COMPLETE - Builders]
         ↓
VALIDATION FACTORY                                   [COMPLETE - Phase 28]
         ↓            ValidatorFactory.create(def)
         ├── GeometryValidator (required)
         ├── HabitabilityValidator (required)
         ├── ProfileValidator (from profile:)
         ├── ProtocolValidator (from protocol:)
         └── LODValidator (from lod:)
         ↓
IFC FILE + BOM/ERP                                   [COMPLETE - Export]

CROSS-CUTTING:
├── OutlierLogger    Graceful degradation            [COMPLETE - Phase 25]
├── ValidatorFactory Dynamic validator composition   [COMPLETE - Phase 28]
└── Auto-placers     MEP + Structural + Fixtures     [COMPLETE - Phase 22-24]
```
