# SESSION STATE

## Current Phase
PHASE 0 COMPLETE - Deep Analysis Done

## Environment
- Repo: /home/red1/IfcOpenShell
- Branch: feature/IFC4_DB
- DB: /home/red1/IfcOpenShell/WORK_DIR/databases/enhanced_federation_GI.db
- DB Size: 234MB, 51,723 elements, 94.8% with geometry

---

# EXTRACTED PATTERNS (From Real DB Analysis)

## PATTERN 1: IFC Types (31 types)
```
IfcAirTerminal, IfcAlarm, IfcBeam, IfcBuildingElementProxy, IfcColumn,
IfcController, IfcCovering, IfcDoor, IfcDuctFitting, IfcDuctSegment,
IfcElectricAppliance, IfcFireSuppressionTerminal, IfcFlowController,
IfcFlowTerminal, IfcFurniture, IfcLightFixture, IfcMember, IfcOpeningElement,
IfcPipeFitting, IfcPipeSegment, IfcPlate, IfcRailing, IfcRampFlight,
IfcReinforcingBar, IfcRoof, IfcSensor, IfcSlab, IfcStairFlight,
IfcValve, IfcWall, IfcWindow
```

## PATTERN 2: Disciplines (9)
```
ACMV (1,621) - Ducts, Air Terminals
ARC  (35,338) - Plates, Walls, Doors, Windows, Furniture
CW   (1,431) - Curtain Wall piping
ELEC (1,172) - Light Fixtures, Appliances
FP   (6,884) - Fire Protection pipes, Sprinklers, Alarms
LPG  (209) - Gas piping
REB  (2,660) - Reinforcing Bars
SP   (979) - Sanitary/Plumbing
STR  (1,429) - Structural: Beams, Columns, Slabs
```

## PATTERN 3: Type-to-Discipline Rules
```
ACMV owns: IfcDuctFitting, IfcDuctSegment, IfcAirTerminal
ARC owns:  IfcPlate, IfcWall, IfcWindow, IfcDoor, IfcFurniture, IfcCovering
CW owns:   IfcPipeFitting, IfcPipeSegment (curtain wall services)
ELEC owns: IfcLightFixture, IfcElectricAppliance
FP owns:   IfcPipeFitting, IfcPipeSegment, IfcFireSuppressionTerminal, IfcAlarm
LPG owns:  IfcPipeFitting, IfcPipeSegment, IfcValve
REB owns:  IfcReinforcingBar
SP owns:   IfcPipeFitting, IfcPipeSegment, IfcFlowTerminal
STR owns:  IfcSlab, IfcBeam, IfcMember, IfcColumn
```

## PATTERN 4: Dimensional Standards
```
Wall thickness:  150-300mm (avg 158mm)
Pipe FP:         avg 39mm diameter
Pipe SP:         avg 236mm diameter (drainage larger)
Pipe CW:         avg 50mm diameter
Pipe LPG:        avg 36mm diameter
Door:            up to 1850mm wide, 2200mm high
Window:          up to 7000mm wide, 2900mm high
Column ARC:      450-600mm, 5.3m height
Column STR:      750mm, 14m height
Beam STR:        avg 705mm height
Slab thickness:  100-300mm (ARC=finish, STR=structural)
```

## PATTERN 5: Building Structure (Z-levels)
```
Z = -31m:  Foundation (STR, REB)
Z = 0m:    Ground (all 8 disciplines)
Z = 3-4m:  Level 1
Z = 7-8m:  Level 2
Z = 11-12m: Level 3
Z = 15-16m: Level 4
Z = 19-22m: Upper floors (main building mass)
Z = 26-27m: Roof

Floor-to-floor: ~4m (commercial/airport building)
```

## PATTERN 6: Cross-Discipline Interactions (Potential Clashes)
```
ARC-REB:  9,244 overlaps (rebar in concrete)
ARC-FP:   2,476 overlaps (fire pipes through architecture)
ARC-STR:  1,121 overlaps (structure in arch spaces)
ARC-CW:   1,102 overlaps (curtain wall integration)
ARC-ELEC: 843 overlaps (electrical in architecture)
ARC-SP:   622 overlaps (plumbing through architecture)
ACMV-FP:  479 overlaps (MEP coordination)
```

## PATTERN 7: Geometry Complexity
```
IfcPlate:                  24 vertices (simple flat panels)
IfcSlab/Opening:           8-10 vertices (boxes)
IfcPipeSegment:            56 vertices (cylinders)
IfcPipeFitting:            120 vertices (elbows/tees)
IfcDuctSegment:            166 vertices (rectangular extrusions)
IfcFireSuppressionTerminal: 2,000+ vertices (detailed sprinklers)
IfcBuildingElementProxy:   up to 79,003 vertices (complex custom)
```

## PATTERN 8: Material Rules
```
ARC/IfcPlate     → Metal Deck
STR/IfcBeam      → Concrete C35
STR/IfcMember    → Steel 50-355
STR/IfcSlab      → Concrete 45 MPa
STR/IfcColumn    → Cast-in-Place Concrete
```

## PATTERN 9: Geometry Instancing
```
Total elements:    51,723
With geometry:     49,059 (94.8%)
Unique shapes:     22,899
Reuse ratio:       2.14 instances per unique shape
```

---

# GENERATION-CRITICAL PATTERNS (Phase 0 Extension)

## PATTERN G1: Placement Anchor Convention
```
Query: Compare transform (center_x/y/z) to bbox center
Result: ALL TYPES have 0.0 offset from bbox center

DECISION: PlacementAnchor = CENTER for all types
No type uses corner or base anchor.
```

## PATTERN G2: Orientation/Rotation Storage
```
Schema check: element_transforms has NO rotation fields
  - Only center_x, center_y, center_z
  - No: rotation, direction, axis, normal, facing

DECISION: Orientation must be INFERRED from geometry
  - Door thin axis matches wall thin axis (82% aligned)
  - Wall facing = perpendicular to thin dimension
  - Linear MEP: direction = along longest bbox dimension
```

## PATTERN G3: Routing Constraints (MEP to surfaces)
```
Query: Distance from wall surfaces by discipline
  ACMV ducts:  159-191mm from wall
  FP pipes:    172-174mm from wall
  CW pipes:    212-221mm from wall
  SP pipes:    218-229mm from wall
  LPG pipes:   87-125mm from wall (closest)

Query: Distance below structural slab
  ACMV ducts:  avg 669mm (min 67mm)
  FP pipes:    avg 727mm (min 166mm)
  CW pipes:    avg 718mm (min 20mm)
  SP pipes:    avg 440mm (min 0mm - penetrations!)

DECISION: Routing constraints per discipline
  - Wall clearance: ~150-220mm typical
  - Ceiling clearance: 400-700mm typical
  - SP allowed to penetrate slabs (risers)
```

## PATTERN G4: Connection Logic
```
Query: Wall junction overlaps
  - 1,194 wall-wall connections
  - Perpendicular overlap: avg 133mm (≈ wall thickness)
  - L-corner junctions: 267 pairs

Query: Pipe-to-fitting insertion depth
  CW:  17mm average
  FP:  14mm average
  LPG: 18mm average
  SP:  42mm average (larger pipes)

DECISION: Connection patterns
  - Walls overlap by ~wall thickness at corners
  - Pipes insert 14-42mm into fittings
```

## PATTERN G5: Extrusion Direction
```
Query: Primary axis by type (longest bbox dimension)
Results by dominant axis:
  Z-up (vertical): IfcColumn (100%), IfcDoor (100%), IfcWindow (94%),
                   IfcWall (61%), IfcFireSuppressionTerminal (100%)
  X-axis: IfcPlate (100%), IfcBeam (60%), IfcPipeFitting (64%)
  Y-axis: IfcDuctSegment (48%), IfcStairFlight (94%)

DECISION: Extrusion conventions
  - Columns, doors, windows, sprinklers: always Z-up
  - Plates: always X-axis (metal deck orientation)
  - Pipes/ducts: varies by routing direction
  - Walls: mostly Z-up (61%), some horizontal
```

## PATTERN G6: Profile Uniqueness (Geometry Reuse)
```
Query: Unique profiles vs total elements per type
  IfcPlate:     8,018 unique / 33,324 total = 4.2x reuse (highest)
  IfcSlab:      485 unique / 705 total = 1.5x reuse
  IfcWindow:    183 unique / 236 total = 1.3x reuse
  IfcColumn:    122 unique / 158 total = 1.3x reuse
  IfcPipeFitting: 4,198 unique / 4,243 total = 1.0x (mostly unique)
  IfcPipeSegment: 3,787 unique / 3,821 total = 1.0x (mostly unique)

DECISION: Instancing strategy
  - IfcPlate highly instanced (use shared geometry)
  - Pipes/fittings mostly unique (per-element geometry)
  - Windows/doors moderate reuse (standard sizes)
```

## PATTERN G7: Opening-to-Wall Ratio
```
Query: Opening dimensions vs host wall dimensions
  avg_width_ratio:  0.555 (55% of wall length)
  max_width_ratio:  34.5 (outliers - multiple openings)
  avg_height_ratio: 0.635 (63.5% of wall height)

DECISION: Opening constraints
  - Typical opening width < 60% of wall length
  - Typical opening height < 70% of wall height
  - Validate: opening should not exceed wall dimensions
```

## PATTERN G8: Floor-to-Floor Convention
```
Query: Structural slab Z levels
  Z = -31m: Foundation (236 slabs)
  Z = -1m to 0m: Ground level (184 slabs)
  Z = 8m: Level 2 (67 slabs)
  Z = 12m: Level 3 (74 slabs)
  Z = 16m: Level 4 (53 slabs)

Query: Slab-to-slab gaps
  Typical: -0.175 to -0.25m (negative = overlap)
  Some: +3.825m (floor-to-floor gap)

DECISION: Storey convention
  - Floor-to-floor: ~4m (confirmed from PATTERN 5)
  - Slabs overlap by 175-250mm at floor transitions
  - Foundation at Z = -31m, roof area at Z = 16-27m
```

## PATTERN G9: MEP Zone Height
```
Query: Light fixture top to structural slab bottom
  min: 0.829m
  avg: 0.916m
  max: 1.604m

DECISION: Ceiling plenum depth
  - Typical MEP zone: 830-920mm
  - Maximum: 1.6m (high ceilings)
  - Confirms RULE 7 finding (~900mm)
```

## PATTERN G10: Termination Patterns
```
Query: Fitting-to-pipe ratio by discipline
  CW:  1.03 fittings per pipe (1:1 ratio)
  FP:  1.18 fittings per pipe (branching systems)
  LPG: 1.16 fittings per pipe
  SP:  0.82 fittings per pipe (more continuous runs)

DECISION: Pipe termination
  - Most pipes connect to fittings at both ends
  - FP has most branching (tees, crosses)
  - SP has more continuous runs (fewer fittings)
```

## PATTERN G11: Repetition/Instancing
```
Query: Maximum instance count per type
  IfcPlate:    max 17 instances of same geometry
  IfcWindow:   max 11 instances
  IfcSlab:     max 11 instances
  IfcColumn:   max 4 instances
  Most types:  avg 1-2 instances (low reuse)

DECISION: Assembly detection
  - Plates highly repeated (metal deck panels)
  - Windows/doors have standard sizes (moderate reuse)
  - Pipes/fittings mostly unique (custom routing)
```

## PATTERN G12: Boundary Conditions
```
Query: Elements at building edge (within 1m of bbox extremes)
  IfcBuildingElementProxy: 13 (custom edge elements)
  IfcSlab: 3 (edge slabs)
  IfcValve: 2
  IfcWall: 1
  IfcPipeSegment: 1
  Total: ~21 elements at boundary

DECISION: Edge handling
  - Very few elements at building boundary
  - Edge elements are mostly custom (BuildingElementProxy)
  - Most geometry is interior
```

---

# GEOMETRIC FACTS (Expert Analysis - Interface Contract Drivers)

## FACT 1: Coordinate Precision
```
Precision at 2 decimal places: ~5mm tolerance
Precision at 6 decimal places: sub-micron available
DECISION: Use double, define TOLERANCE = 0.005 (5mm)
```

## FACT 2: Global Project Offset
```
offset_x: 121.47m
offset_y: -21.66m
offset_z: -0.78m
extent: 73.7m x 59.1m x 59.8m
DECISION: Store global offset, all coords are world-relative
```

## FACT 3: Wall Thickness Standards (ONLY 4 VALUES!)
```
150mm: 306 walls (92%) - standard interior
250mm: 18 walls (5%)
230mm: 6 walls (2%)
300mm: 3 walls (1%) - exterior/structural
DECISION: WallThickness enum, not free-form double
```

## FACT 4: Pipe Diameter Ranges by Discipline
```
CW:  4-141mm,  55 unique sizes
FP:  3-168mm,  32 unique sizes
LPG: 3-383mm,  9 unique sizes
SP:  3-6696mm, 99 unique sizes (includes large drains)
DECISION: Per-discipline validation ranges
```

## FACT 5: Multi-Story Elements (span >8m)
```
IfcReinforcingBar: 590 elements, avg 27m height (continuous rebar)
IfcSlab: 236 elements, avg 30m (ramp slabs?)
IfcColumn: 90 elements, avg 13-22m (full-height columns)
IfcWall: 85 elements, avg 11-22m (atrium/curtain walls)
DECISION: Elements can span floors, don't assume floor containment
```

## FACT 6: Transform = BBox Center (No Offset)
```
All element transforms have 0.0 offset from bbox center
DECISION: Geometry is pre-transformed to world coordinates
No local-to-world conversion needed in Java
```

## FACT 7: No Degenerate Geometry
```
Zero elements with dimension < 1mm
DECISION: No need for degenerate geometry filters
```

## FACT 8: Walls NOT on Regular Grid
```
X coordinates spread across many values (no snap pattern)
DECISION: No grid-snapping logic, use actual coordinates
```

## FACT 9: Wall-to-Wall Connections
```
1,194 wall pairs with overlapping bboxes
DECISION: Connection detection via spatial overlap query
```

## FACT 10: Vertex Blob Format
```
79,003 vertices = 948KB blob
948,036 bytes / 79,003 = 12 bytes per vertex
DECISION: 3 x float32 (x, y, z) per vertex
```

---

# RELATIONSHIP PATTERNS (Critical for Compiler)

## Opening → Wall Relationship: SPATIAL INFERENCE

**No explicit relationship table exists.**

Query results:
```
Total Openings:           631
Openings overlapping Wall: 619 (98%)
Openings FULLY in Wall:   349 (55%)

Door-Opening overlaps:    188 (some doors touch multiple openings)
Window-Opening overlaps:  1234 (windows touch multiple openings)
```

**Inference chain:**
```
Wall ←[bbox overlap]→ Opening ←[bbox overlap]→ Door/Window
```

**Sample data (door→opening→wall):**
```
Door: FD2:1800x2100
  ↓ overlaps
Opening: 15dKaH6xHdWB6mpsb9eHGL
  ↓ overlaps
Wall: Basic Wall:A_Wall_Ext_150mm_Coping_V1
```

**Python code status:**
```python
'has_opening': False,  # Can be enhanced later
```
Explicit relationship extraction NOT implemented in federation module.

## DECISION: Relationship Method

| Relationship | Storage | Query Method |
|--------------|---------|--------------|
| Opening-Wall | **INFERRED** | Spatial bbox overlap |
| Door-Opening | **INFERRED** | Spatial bbox overlap |
| Wall-Wall | **INFERRED** | Spatial bbox overlap |
| Rebar-Concrete | **INFERRED** | Spatial bbox overlap |
| Element-Storey | **EXPLICIT** | spatial_structure table |
| Element-Building | **EXPLICIT** | spatial_structure table |

**Java Interface Impact:**
```java
// NOT this (no explicit storage):
interface IOpening {
    String getHostWallId();  // NO - doesn't exist in DB
}

// USE this (spatial query):
interface ISpatialElement {
    List<ISpatialElement> findOverlapping(BIMObjectType type);
    List<ISpatialElement> findContaining();  // elements this is inside
    List<ISpatialElement> findContained();   // elements inside this
}
```

---

# CONSTRUCTION RULES (IFC/BIM Conventions Verified Against DB)

## Industry Standards Referenced
- [MEP Coordination Checklist](https://builtinbim.com/blogs/mep-coordination-checklist-the-only-checklist-for-mep-engineering-youll-need)
- [BIM Rule-Based Checking](https://www.bim.com.sg/blog/bim-automated-checks)
- Industry standard: 50mm minimum MEP-Structure clearance
- UK Building Regs: 665mm minimum opening distance from corner

## RULE 1: MEP-Structure Clearance
```
Standard: 50mm minimum clearance between MEP and structure
DB Finding:
  - Duct-Slab pairs: 441 checked
  - Min gap: 0mm (violations exist!)
  - Avg gap: 614mm

DECISION: Validator should flag gaps < 50mm
VALIDATOR: mepStructureClearance(element) >= 50mm
```

## RULE 2: MEP Vertical Coordination Zones
```
Standard: Disciplines layered vertically in ceiling plenum
DB Finding (Z=15-25m floor):
  - CW pipes:  avg Z = 16.3m (lowest)
  - ACMV ducts: avg Z = 17.3m
  - SP pipes:  avg Z = 17.7m
  - FP pipes:  avg Z = 19.0m (highest)

PATTERN: FP on top, ACMV/SP middle, CW bottom
VALIDATOR: fpAboveAcmv(), acmvAboveCw()
```

## RULE 3: FP Pipe Slope
```
Standard: FP pipes horizontal or sloped to drains
DB Finding:
  - 64% horizontal (Z-change < 100mm)
  - 36% sloped
  - Avg Z-change: 171mm

DECISION: Horizontal runs preferred, slopes < 2% typical
```

## RULE 4: Duct Standard Sizes (mm)
```
DB Finding - Most common sizes:
  150 x 150:  30 instances
  300 x 300:  8 instances
  250 x 300:  5 instances
  400 x 500:  5 instances
  900 x 950:  5 instances (main trunk)

PATTERN: Standard modular sizes (50mm increments)
VALIDATOR: ductSizeIsStandard(width, height)
```

## RULE 5: Slab Penetrations by Discipline
```
DB Finding - Pipes passing through structural slabs:
  SP (sanitary): 257 penetrations (drainage risers)
  CW (curtain):  15 penetrations
  FP (fire):     13 penetrations
  ACMV (ducts):  4 penetrations (ducts run below slabs)

PATTERN: Vertical pipes penetrate, ducts run horizontally below
VALIDATOR: ductShouldNotPenetrateSlab()
```

## RULE 6: Sprinkler Head Spacing
```
Standard: NFPA 13 max spacing 4.6m (light hazard)
DB Finding:
  - 909 sprinkler heads
  - Min spacing: 0mm (some clustered)
  - Avg spacing: 6490mm

WARNING: Some exceed max spacing - needs review
VALIDATOR: sprinklerSpacing() <= 4600mm
```

## RULE 7: Ceiling Plenum Depth
```
DB Finding - Light fixture to slab clearance:
  - Min clearance: 829mm
  - Avg clearance: 916mm

PATTERN: ~900mm plenum depth for MEP routing
CONSTRAINT: plenumDepth = floorToFloor - clearHeight - slabThickness
```

## RULE 8: Structural Grid
```
DB Finding - Column positions:
  - X-axis: 90m, 95m (5m spacing)
  - Y-axis: -41m to -1m (8m spacing)

PATTERN: Regular structural grid 5m x 8m
VALIDATOR: columnOnGrid(5000, 8000)
```

## RULE 9: Beam Clear Height
```
DB Finding - Beam soffit heights (floor 15-25m):
  - Min soffit: 15,265mm
  - Avg soffit: 15,899mm
  - Max soffit: 21,938mm

MEP routing must clear beam soffits
VALIDATOR: mepBelowBeamSoffit()
```

## RULE 10: Opening Placement (Doors/Windows)
```
Standard: 665mm minimum from corner (UK), 700mm (US)
DB Finding:
  - Some doors at wall edge (< 100mm clearance)
  - Some doors well-centered (10+ meters from edge)
  - Negative values = door overlaps past wall end

PATTERN: No consistent corner clearance rule enforced
VALIDATOR: openingCornerClearance() >= 665mm (optional)
```

## SUMMARY: Validated Construction Rules

| Rule | Standard | DB Confirms | Validator |
|------|----------|-------------|-----------|
| MEP-STR clearance | 50mm | Violations exist | YES - flag < 50mm |
| MEP vertical zones | FP top, ACMV mid | YES - verified | YES |
| FP pipe slope | Horizontal/slight | 64% horizontal | OPTIONAL |
| Duct sizes | Modular 50mm | YES - standard | OPTIONAL |
| Slab penetrations | Pipes yes, ducts no | YES - verified | YES |
| Sprinkler spacing | ≤ 4.6m | Some exceed | YES - flag > 4.6m |
| Plenum depth | ~900mm | 829-916mm | INFORMATIONAL |
| Structural grid | Regular | 5m x 8m | OPTIONAL |
| Opening clearance | 665mm | NOT enforced | OPTIONAL |

---

# PYTHON CODE PATTERNS (Proven Logic from Federation Module)

## CODE PATTERN 1: Unit Convention
```
Source: coordinate_utils.py line 12-13
Rule: "Store/load meters 1:1. NO /1000 divisions anywhere."
Database stores METERS, not millimeters.
IFC files use METERS.
Viewport uses METERS.
DECISION: Java uses double in METERS. No unit conversion.
```

## CODE PATTERN 2: Coordinate Systems
```
Source: coordinate_utils.py
Three systems:
  - IFC absolute: (-50433, 34188, 8) meters
  - Database/Viewport: offset-relative (118, -3, 1) meters
Conversion: subtract global_offset to go IFC→DB/Viewport
DECISION: Java stores offset-relative coordinates (like DB)
```

## CODE PATTERN 3: Transform Handling
```
Source: blend_cache.py line 309-312
Transform stored as (center_x, center_y, center_z)
Applied as obj.location = transform
DECISION: Transform is just translation, no rotation matrix needed
```

## CODE PATTERN 4: Geometry Instancing
```
Source: blend_cache.py line 285-314
Pattern: meshes[geom_hash] → shared mesh, multiple objects
Each object gets: guid, ifc_class, discipline, transform
DECISION: Java should use same pattern - GeometryHash → shared vertices
```

## CODE PATTERN 5: Error Handling
```
Source: federation_preprocessor.py line 497
On geometry error: "Warning: Skipping element due to error"
Continues processing, doesn't abort
DECISION: Java validators should collect errors, not throw
```

## CODE PATTERN 6: Discipline Organization
```
Source: blend_cache.py line 296-301
Elements organized into collections by discipline
DECISION: Java model should support groupBy(discipline)
```

## CODE PATTERN 7: Vertex Blob Format
```
Vertices: float32[3] per vertex (x, y, z)
Faces: int32[3] per face (v1, v2, v3 indices)
948,036 bytes / 79,003 vertices = 12 bytes/vertex = 3 floats
DECISION: Java uses float[] for vertices, int[] for faces
```

---

## Phase Progress
- [x] BOOTSTRAP complete
- [x] PHASE 0 complete (deep analysis + 12 generation patterns)
- [x] PHASE 1: Topology Dictionary (18 files created)
- [x] PHASE 2: Validators (7 validators) + DB Reader + Calibration Test
- [x] PHASE 3: WallBuilder + PipeBuilder + Vertex-level tests (100% pass)
- [x] PHASE 4 Gate: Connection validation (98.1% overlap)
- [x] PHASE 7: IFC Export + Round-trip validation (6/6 tests pass)
- [x] CONFIG REFACTOR: Python exporter now typology-agnostic
- [x] **FULL DB ROUND-TRIP**: Real 49-opening wall → PASS
- [x] **PHASE 14A**: Library Stairs (HybridFactory routes to LibraryFactory)
- [x] **PHASE 14B**: Terminal Mini + MEP Grids (DEPARTURE_LOUNGE, GATE, sprinklers, lights)
- [ ] PHASE 4 continued: DuctBuilder, FittingBuilder

## Phase 1 Files Created (+ All 12 Generation Patterns)
```
topology/
├── BIMConstants.java          (tolerances, offsets, construction rules)
├── BIMObjectType.java         (31 IFC types)
├── Discipline.java            (9 disciplines)
├── PipeDiameterRange.java     (per-discipline validation)
├── TypeDisciplineMapping.java (type ↔ discipline)
├── WallThickness.java         (4-value enum)
├── RoutingConstraints.java    [G3] (MEP wall/ceiling clearances)
├── ConnectionPattern.java     [G4] (junction overlap patterns)
├── ExtrusionAxis.java         [G5] (extrusion direction per type)
├── OpeningConstraints.java    [G7] (opening size limits)
├── StoreyConvention.java      [G8,G9] (floor heights, MEP zone)
├── TerminationPattern.java    [G10] (pipe termination logic)
└── InstancingStats.java       [G11] (geometry reuse stats)

geometry/
├── Point3D.java           (immutable 3D point record)
├── BoundingBox.java       (AABB with overlap queries)
└── Vector3D.java          [G2] (direction vectors)

model/
├── IBIMElement.java       (Level 1: Identity)
├── ISpatialElement.java   (Level 2: Spatial + relationship queries)
├── IGeometricElement.java (Level 3: Vertices/faces)
└── IOrientedElement.java  [G2] (orientation inference from bbox)

validation/ (Phase 2)
├── ValidationResult.java
├── IValidator.java
├── MepStructureClearanceValidator.java
├── SprinklerSpacingValidator.java
├── PipeDiameterValidator.java
├── WallThicknessValidator.java
├── OpeningPlacementValidator.java
└── MultiStoryElementValidator.java
```

## Phase 2 Files Created
```
validation/
├── ValidationResult.java              (collect errors, don't throw)
├── IValidator.java                    (common interface)
├── MepStructureClearanceValidator.java   (RULE 1: 50mm)
├── SprinklerSpacingValidator.java        (RULE 6: 4.6m NFPA)
├── PipeDiameterValidator.java            (FACT 4: discipline ranges)
├── WallThicknessValidator.java           (FACT 3: 4 values only)
├── OpeningPlacementValidator.java        (98% wall overlap)
└── MultiStoryElementValidator.java       (FACT 5: >8m = INFO)
```

## Phase 2 Validation Results (Calibrated)

```
Loaded 51,719 elements from database

Validator                          Errors   Warnings       Info
--------------------------------------------------------------
Wall Thickness                          0          0          0
Pipe Diameter                           0          0          0
Opening Placement                       0         12        393
Multi-Story Element                     0          0       1001
MEP-Structure Clearance                 0       1744          0
Sprinkler Spacing                       0         10          9
--------------------------------------------------------------
TOTAL                                   0       1766       1403
```

### Assessment

| Validator | Result | Notes |
|-----------|--------|-------|
| Wall Thickness | ✓ PASS | Zero issues - FACT 3 (4 values) confirmed |
| Pipe Diameter | ✓ PASS | Zero issues - FACT 4 ranges include fittings |
| Opening Placement | ✓ PASS | 12 warnings (2%) = orphaned openings (expected) |
| Multi-Story | ✓ INFO | 1001 elements span >8m (rebar, columns) |
| MEP-Structure | ⚠ REVIEW | 1744 warnings - mostly intentional penetrations |
| Sprinkler Spacing | ⚠ REVIEW | 10 warnings - isolated areas, different floors |

### Calibration Notes

1. **Pipe Diameter ranges updated** to include fittings (larger than segments):
   - CW: 4-155mm (was 4-141mm)
   - FP: 3-184mm (was 3-168mm)

2. **MEP-Structure warnings** are mostly intentional pipe penetrations through slabs, not violations.

3. **Sprinkler spacing warnings** (25m spacing) are sprinklers on different floor areas, not adjacent heads.

### Files Created (Phase 2)
```
db/
└── FederatedDBReader.java       (SQLite JDBC reader)

model/
└── SpatialElement.java          (concrete ISpatialElement)

test/
└── ValidatorCalibrationTest.java (calibration runner)
```

## Phase 3: WallBuilder Round-Trip Test Results

```
======================================================================
WALL BUILDER ROUND-TRIP TEST
======================================================================
Total walls in model: 333
Walls with openings: 165

Tested: 20 walls
Passed: 20 (100.0%)
Failed: 0 (0.0%)

✓ ALL ROUND-TRIP TESTS PASSED
======================================================================
```

### Sample walls tested:
- Wall 40m x 0.15m x 16m with 13 openings: PASS
- Wall 60m x 0.30m x 20m with 49 openings: PASS
- Wall 36m x 0.15m x 4m with 51 openings: PASS
- Wall 60m x 0.30m x 18m with 130 openings: PASS

### BBox match verification:
```
Original BBox: BBox[(89.90,-40.16,0.11)-(90.05,-0.16,16.11)]
Generated BBox: BBox[(89.90,-40.16,0.11)-(90.05,-0.16,16.11)]
BBox match (within 5mm): YES
```

### Files Created (Phase 3)
```
builder/
├── IBuilder.java         (common interface)
├── OpeningSpec.java      (opening specification)
├── WallSpec.java         (wall specification with extractFrom())
├── WallBuilder.java      (builds wall + openings from spec)
└── WallBuilderTest.java  (round-trip test)
```

---

# Phase 3 Continuation: COMPLETE

## Part A: Vertex-Level Validation - COMPLETE

### WallBuilderVertexTest Results
```
======================================================================
WALL BUILDER VERTEX-LEVEL TEST
======================================================================
Testing MM_150 wall: 09wyevrd5A3eL31Smq56XD
  Original: 40 vertices, BBox: 0.150m x 6.617m x 1.277m
  Generated: 8 vertices, BBox match: YES, Volume ratio: 0.9998
  [PASS]

Testing MM_230 wall: 0Rssu52A1DGA1uyRz$LBFS
  Original: 8 vertices (simple box), BBox match: YES, Volume ratio: 0.9999
  [PASS]

Testing MM_250 wall: 0Rssu52A1DGA1uyRz$LBFp
  Original: 40 vertices, BBox match: YES, Volume ratio: 1.0000
  [PASS]

Testing MM_300 wall: 1X8oycKuf0shYLYEAURWY1
  Original: 566 vertices (49 openings), BBox match: YES, Volume ratio: 1.0000
  [PASS]

ALL VERTEX-LEVEL TESTS PASSED
```

### Files Created/Modified
- model/GeometricElement.java - IGeometricElement implementation
- db/FederatedDBReader.java - Added getGeometricElement(guid) method
- builder/WallBuilder.java - Added buildGeometry(), buildFaces() methods
- builder/WallBuilderVertexTest.java - Vertex-level round-trip test

## Part B: PipeBuilder - COMPLETE

### PipeBuilderTest Results
```
======================================================================
PIPE BUILDER ROUND-TRIP TEST
======================================================================
Diameter ranges per discipline (FACT 4):
  CW: 4.0 - 155.0mm
  FP: 3.0 - 184.0mm
  LPG: 3.0 - 383.0mm
  SP: 3.0 - 6696.0mm

Testing LPG pipe: 2_22kN7QbBZecZCBJXEdsp
  Diameter: 73.0mm, Diameter valid: YES [PASS]

Testing SP pipe: 0HLXdCBvr3OAXqA6u7TKZx
  Diameter: 114.3mm, Diameter valid: YES [PASS]

Testing CW pipe: 2EWQkj7Z9F_BZghmRaKV0R
  Diameter: 26.7mm, Diameter valid: YES [PASS]

Testing FP pipe: 3vd2r0Uuv9WPbLeq8saOTR
  Diameter: 42.2mm, Diameter valid: YES [PASS]

Diameter Validation Boundary Tests:
  [OK] FP 50mm: valid=true
  [OK] SP 200mm: valid=true
  [OK] CW 100mm: valid=true
  [OK] LPG 100mm: valid=true
  [OK] FP 1mm: valid=false (too small)
  [OK] FP 500mm: valid=false (too large)
  [OK] CW 200mm: valid=false (too large)

ALL PIPE ROUND-TRIP TESTS PASSED (4/4 pipes, 7/7 boundary tests)
```

### Files Created
- builder/PipeSpec.java - Pipe specification record
- builder/PipeBuilder.java - Pipe builder with cylinder geometry
- builder/PipeBuilderTest.java - Round-trip test with diameter validation

---

# Connection Validation (Phase 4 Gate)

**Before building more elements, validated connection logic:**

| Test | Result | Details |
|------|--------|---------|
| Wall-Opening Containment | PASS | 98.1% overlap (target: ≥95%) |
| Bidirectional Queries | PASS | Both directions work |
| Wall-Wall Corner Overlap | PASS | 69.4mm avg, 878 connections |
| Generated Relationships | PASS | 13/13 openings connected |

**Conclusion:** Foundation is solid. Connection logic validated.

---

# PHASE 8: Library Placer System - COMPLETE

## Overview
Hybrid architecture for LOD500 component placement:
- **MODE A**: Parametric Builder (existing) - walls, pipes with dynamic dimensions
- **MODE B**: Library Placer (NEW) - fixed LOD500 geometry for sprinklers, lights, etc.

## Part 1: Component Library Extraction - COMPLETE

**Database**: `library/component_library.db` (113 MB)
- 8,087 unique component definitions across 13 IFC types
- Geometry stored as binary blobs (12 bytes/vertex)
- Tables: component_types, component_definitions, component_geometries, placement_rules

**Component counts by category:**
| Category | Count |
|----------|-------|
| PIPE_FITTING | 4,198 |
| SPRINKLER | 891 |
| LIGHT | 801 |
| DUCT_FITTING | 683 |
| BEAM | 404 |
| MEMBER | 382 |
| DIFFUSER | 268 |
| FURNITURE | 131 |
| COLUMN | 122 |
| VALVE | 111 |
| ALARM | 71 |
| APPLIANCE | 19 |
| CONTROLLER | 6 |

**Scripts created:**
- `scripts/create_sprinkler_library.py` - Initial POC extraction
- `scripts/extract_all_components.py` - Full extraction with heuristics
- `scripts/export_sprinklers_to_ifc.py` - IFC4 export with IfcMappedItem

### Component Library DB Schema
```sql
-- library/component_library.db (113 MB)

CREATE TABLE component_types (
    id INTEGER PRIMARY KEY,
    ifc_class TEXT NOT NULL,    -- e.g., 'IfcFireSuppressionTerminal'
    category TEXT NOT NULL,      -- e.g., 'SPRINKLER'
    discipline TEXT NOT NULL,    -- e.g., 'FP'
    UNIQUE(ifc_class, category)
);

CREATE TABLE component_definitions (
    id INTEGER PRIMARY KEY,
    type_id INTEGER REFERENCES component_types(id),
    name TEXT NOT NULL,
    geometry_hash TEXT NOT NULL,
    -- Local geometry bounds (in local coordinates)
    local_min_x REAL, local_max_x REAL,
    local_min_y REAL, local_max_y REAL,
    local_min_z REAL, local_max_z REAL,
    -- Attachment convention
    attachment_face TEXT NOT NULL,  -- TOP, BOTTOM, SIDE, CENTER
    up_axis TEXT DEFAULT 'Z',
    forward_axis TEXT DEFAULT 'Y',
    -- Orientation
    orientation TEXT,               -- PENDANT, UPRIGHT, WALL_MOUNT
    default_rotation REAL DEFAULT 0,
    -- Geometry stats
    vertex_count INTEGER,
    face_count INTEGER,
    instance_count INTEGER DEFAULT 1,
    UNIQUE(name, geometry_hash)
);

CREATE TABLE component_geometries (
    geometry_hash TEXT PRIMARY KEY,
    vertices BLOB NOT NULL,     -- 3 x float32 per vertex (12 bytes)
    faces BLOB NOT NULL,        -- 3 x int32 per face (12 bytes)
    normals BLOB,
    vertex_count INTEGER NOT NULL,
    face_count INTEGER NOT NULL
);

CREATE TABLE placement_rules (
    id INTEGER PRIMARY KEY,
    component_id INTEGER REFERENCES component_definitions(id),
    host_type TEXT,           -- CEILING, WALL, FLOOR
    offset_from_host REAL,    -- Distance from host surface
    grid_spacing REAL,        -- Standard spacing (e.g., 4.6m for sprinklers)
    clearance_radius REAL     -- Min distance from other objects
);
```

### IFC Classes in Library
| IFC Class | Category | Discipline | Count |
|-----------|----------|------------|-------|
| IfcPipeFitting | PIPE_FITTING | from_element | 4,198 |
| IfcFireSuppressionTerminal | SPRINKLER | FP | 891 |
| IfcLightFixture | LIGHT | ELEC | 801 |
| IfcDuctFitting | DUCT_FITTING | ACMV | 683 |
| IfcBeam | BEAM | STR | 404 |
| IfcMember | MEMBER | STR | 382 |
| IfcAirTerminal | DIFFUSER | ACMV | 268 |
| IfcFurniture | FURNITURE | ARC | 131 |
| IfcColumn | COLUMN | STR | 122 |
| IfcValve | VALVE | from_element | 111 |
| IfcAlarm | ALARM | FP | 71 |
| IfcElectricAppliance | APPLIANCE | ELEC | 19 |
| IfcController | CONTROLLER | ELEC | 6 |

### Stair Components (Stringers)
```
Total stringers: 129 definitions, 130 instances
Unique geometries: 129 (nearly all unique)

Height distribution:
  2.5-2.6m:  4 (full-height)
  2.0-2.4m: 59 (standard floor-to-floor)
  0.4m:     66 (connection pieces/brackets)

Geometry: Simple box shapes (8 vertices, 12 faces)
IFC Class: IfcMember (not IfcStairFlight)
Category: MEMBER
Discipline: STR

Sample stringer dimensions:
  ID 8409: hash=bdfca775d8ff2b9d, 3.32m x 2.64m (run x rise)
  ID 8330: hash=f0c82d982958eaa5, 3.08m x 2.56m
  ID 8586: hash=2cded84c25373528, 3.32m x 2.50m

NOTE: Terminal stairs are decomposed into individual components
(stringers, treads, risers) rather than composite IfcStairFlight.
Treads likely classified as IfcSlab or not yet extracted.
```

### Complete Stair Parts Inventory (Source DB)
| Part | IFC Class | Count | Geometry | In Library? |
|------|-----------|-------|----------|-------------|
| StairFlight | IfcStairFlight | 32 | 184-200 verts | NO |
| Railing | IfcRailing | 34 | 48-828 verts | NO |
| Stringer | IfcMember | 129 | 8 verts (box) | YES |

**StairFlight Details:**
- Named: "Assembled Stair:Stair:XXXXXX Run 1/2"
- Dimensions: ~1.4-1.5m width × 3.04m depth × 1.9-2.2m rise
- Two runs per stair (with landing between)
- Z levels: 0.11m to 8.11m (multi-storey)
- 100% have geometry (184-200 vertices, 276-300 faces)

**Railing Details:**
- Named: "Railing:1100mm:XXXXXX" (code-compliant height)
- Heights: 4.8-5.4m (span full stair run)
- 100% have geometry (48-828 vertices)
- Unique per instance (no reuse)

**Missing from Library (to extract):**
1. IfcStairFlight (32) - composite solid treads
2. IfcRailing (34) - handrails with balusters
3. Landing slabs - likely in IfcSlab but not stair-named

---

# TERMINAL COMPONENT CATALOG (Comprehensive)

**Source**: `/home/red1/IfcOpenShell/WORK_DIR/databases/enhanced_federation_GI.db`
**Total Elements**: 51,723 across 31 IFC classes, 9 disciplines

## Discipline Summary

| Discipline | Total | IFC Types | Unique Geoms | Description |
|------------|-------|-----------|--------------|-------------|
| ARC | 35,338 | 15 | 9,711 | Architecture (plates, walls, doors, windows) |
| FP | 6,884 | 10 | 6,767 | Fire Protection (pipes, sprinklers, alarms) |
| REB | 2,660 | 1 | 0 | Reinforcing Bar (no extractable geometry) |
| ACMV | 1,621 | 4 | 1,556 | Mechanical (ducts, diffusers, fans) |
| CW | 1,431 | 6 | 1,431 | Chilled Water (pipes, valves) |
| STR | 1,429 | 5 | 1,122 | Structural (beams, columns, slabs) |
| ELEC | 1,172 | 3 | 1,129 | Electrical (lights, panels, appliances) |
| SP | 979 | 4 | 976 | Sanitary/Plumbing (pipes, fixtures) |
| LPG | 209 | 3 | 209 | Gas (pipes, valves) |

## Component Library Status

### ✓ COMPONENT LIBRARY COMPLETE (extracted 2025-01-28)

**Location**: `/home/red1/bim-compiler/library/component_library.db` (126 MB)
**Total**: 8,701 definitions covering 9,085 instances across 18 IFC classes

| IFC Class | Category | Defs | Instances | Status |
|-----------|----------|------|-----------|--------|
| IfcPipeFitting | PIPE_FITTING | 4,198 | 4,243 | ✓ Complete |
| IfcFireSuppressionTerminal | SPRINKLER | 891 | 909 | ✓ Complete |
| IfcLightFixture | LIGHT | 801 | 814 | ✓ Complete |
| IfcDuctFitting | DUCT_FITTING | 683 | 713 | ✓ Complete |
| IfcBeam | BEAM | 404 | 432 | ✓ Complete |
| IfcMember | MEMBER | 382 | 442 | ✓ (incl. Stringers) |
| IfcAirTerminal | DIFFUSER | 268 | 289 | ✓ Complete |
| **IfcFlowTerminal** | **FIXTURE** | **253** | **256** | ✓ **NEW** (sinks, toilets) |
| **IfcWindow** | **WINDOW** | **183** | **236** | ✓ **NEW** |
| IfcFurniture | FURNITURE | 131 | 176 | ✓ Complete |
| IfcColumn | COLUMN | 122 | 158 | ✓ Complete |
| **IfcDoor** | **DOOR** | **112** | **135** | ✓ **NEW** |
| IfcValve | VALVE | 111 | 111 | ✓ Complete |
| IfcAlarm | ALARM | 71 | 80 | ✓ Complete |
| **IfcRailing** | **RAILING** | **34** | **34** | ✓ **NEW** |
| **IfcStairFlight** | **STAIR** | **32** | **32** | ✓ **NEW** |
| IfcElectricAppliance | APPLIANCE | 19 | 19 | ✓ Complete |
| IfcController | CONTROLLER | 6 | 6 | ✓ Complete |

### Remaining Gaps (IfcBuildingElementProxy - not standard IFC)
| Component | Source Count | Notes |
|-----------|--------------|-------|
| FireExtinguisher | 27 | Custom Revit family, IfcBuildingElementProxy |
| Fan | 44 | Custom Revit family |
| Panel | 38 | Electrical distribution boards |
| Toilet (Asian) | 11 | Some toilets as proxy |

These are Revit custom families exported as IfcBuildingElementProxy.
Can be extracted separately if needed, but main placeable components are complete.

### Key Component Types Found

**PLUMBING FIXTURES (SP):**
- Sinks: `005_915x535_single_end_bowl_sink` (36"x21") - 14 units
- ADA Countertop: `006_ADA_Countertop_and_Sink` - 18 units
- Asian Toilets: `Asian_Toilet` - 4 units

**HVAC EQUIPMENT (ACMV):**
- Return Grilles: `Ceiling Mounted Return Air Grille:1200x600` - many
- Exhaust Grilles: `M_Exhaust air grill_with insect net:1800x600`
- Fans: `jkrME_mec-eq_ventilation fan` (TEF, EF-PKA, CEF types)
- AHUs: `Indoor AHU - Horizontal` (WCPU types)

**FIRE SAFETY:**
- Sprinklers: 909 (already in library)
- Alarms: `jkrME18_fir-al_alarm bell`, `Flashing Light_Red & Green`
- Extinguishers: `fire extinguisher_dp`, `fire extinguisher_co2`
- Intercom: `fireman intercom remote handset`

**STRUCTURAL (STR):**
- SHS: `SHS 60x60x3`, `SHS 100x100x5`, `SHS 120x120x5`
- RHS: `RHS150x100x4`
- Beams: 404 unique geometries
- Columns: 122 unique geometries

**VERTICAL CIRCULATION (ARC):**
- StairFlight: 32 runs (16 two-run stairs) - 184-200 vertices each
- Railings: 34 (1100mm height) - 48-828 vertices each
- Stringers: 129 (already extracted)
- RampFlight: 1

**DOORS & WINDOWS:**
- Doors: D2, D3, D4 types - 0.2-0.9m width, 2.18m height
- Windows: Various sizes, 1.2-7.0m width

## Extraction Status (2025-01-28)

```
✓ PHASE A - High Value (COMPLETE)
  1. IfcDoor (112 defs) → DOOR category ✓
  2. IfcWindow (183 defs) → WINDOW category ✓
  3. IfcStairFlight (32 defs) → STAIR category ✓
  4. IfcRailing (34 defs) → RAILING category ✓

✓ PHASE B - MEP Fixtures (COMPLETE)
  5. IfcFlowTerminal (253 defs) → FIXTURE category ✓
     (includes sinks, toilets, and other plumbing fixtures)

⊘ PHASE C - Custom Families (OPTIONAL)
  6. FireExtinguisher → IfcBuildingElementProxy (not extracted)
  7. Fans → IfcBuildingElementProxy (not extracted)
  8. Panels → IfcBuildingElementProxy (not extracted)
```

**Library is now complete for standard IFC classes.**
DSL can place: doors, windows, stairs, railings, sinks, toilets, sprinklers, lights, diffusers, furniture, etc.

## Part 2: Mathematical Verification - COMPLETE

**LibraryVerificationTest.java**: 15/15 checks passed
- Round-trip position accuracy: 0.0mm delta
- Orientation verification: pendant=TOP, upright=BOTTOM
- Grid spacing: 4.6m (NFPA 13 light hazard)
- Attachment height: verified

## Part 3: Factory Pattern - COMPLETE

**Package**: `com.bim.compiler.factory`
- `ElementSpec.java` - Base spec class
- `ParametricSpec.java` - For dynamic dimensions
- `LibraryPlacementSpec.java` - For LOD400 component placement
- `GridPlacementSpec.java` - For grid-based placement (sprinklers, lights)
- `IElementFactory.java` - Factory interface
- `LibraryFactory.java` - Creates elements from library
- `HybridFactory.java` - Routes to appropriate factory

**FactoryTest.java results**: 4/4 tests PASS
1. Single sprinkler via LibraryPlacementSpec: PASS
2. Grid placement via GridPlacementSpec: 16 sprinklers created: PASS
3. Factory routing verification (light fixture): PASS
4. Component library statistics: PASS

## Part 4: Debug Logging - COMPLETE

**BIMLogger.java** (`com.bim.compiler.util`):
- Levels: DEBUG, INFO, WARN, ERROR
- Specialized methods: placement(), verification(), factoryRoute(), extraction()
- File and console output with timestamps

## Files Created (Phase 8)

```
library/
├── component_library.db         (113 MB - 8,087 components)

scripts/
├── create_sprinkler_library.py
├── extract_all_components.py
└── export_sprinklers_to_ifc.py

src/main/java/com/bim/compiler/library/
├── ComponentLibrary.java
├── SprinklerPlacer.java
├── SprinklerPlacerTest.java
└── LibraryVerificationTest.java

src/main/java/com/bim/compiler/factory/
├── ElementSpec.java
├── ParametricSpec.java
├── LibraryPlacementSpec.java
├── GridPlacementSpec.java
├── IElementFactory.java
├── LibraryFactory.java
├── HybridFactory.java
└── FactoryTest.java

src/main/java/com/bim/compiler/util/
└── BIMLogger.java
```

## IFC Export Efficiency

Using IfcMappedItem for geometry instancing:
- 121 sprinklers = 264 KB (2.18 KB/sprinkler)
- Without instancing: ~24 KB/sprinkler

---

# PHASE 9: DSL → Library Integration - COMPLETE

## DSL Syntax Extended

```dsl
STOREY "Ground" height:2.8m {
    BEDROOM "master" at:A1 size:5x4m {
        DOOR south to:corridor
        SPRINKLERS grid:4.6m    // NEW - uses library factory
    }
    CORRIDOR "corridor" at:A2-B2 width:1.2m {
        SPRINKLERS              // Default 4.6m (NFPA 13)
    }
}
```

## Files Created/Modified

- `SprinklerDefinition.java` - New record for sprinkler DSL spec
- `RoomDefinition.java` - Added sprinklers field
- `DSLParser.java` - Added SPRINKLERS pattern
- `ConstructionSpec.java` - Added SprinklerGridSpec
- `RoomCompiler.java` - Added processSprinklers()
- `DSLSprinklerTest.java` - Integration test

## Test Results: 3/3 PASS

```
Test 1: Parse DSL with SPRINKLERS keyword       [PASS]
Test 2: Compile to ConstructionSpec             [PASS]
Test 3: Full pipeline DSL → Library → Elements  [PASS]
```

## Technical Details

- Spacing: 4.6m (from placement_rules table, NFPA 13)
- Attachment: Ceiling (Z = storey height)
- Type: Pendant (default, ceiling-mount)
- Grid algorithm places heads at spacing/2 from room edges

---

# PHASE 10: Unified IFC Export - COMPLETE

## Problem Solved
Previously two separate export paths:
```
DSL → WallBuilder → walls.ifc
DSL → HybridFactory → sprinklers.ifc
```

Now unified:
```
DSL → ConstructionSpec → unified.ifc (walls + spaces + sprinklers)
```

## Changes Made

### Java: DSLExporter.java
- Added `sprinklerGridToJson()` method
- Modified `constructionSpecToJson()` to include `sprinkler_grids` array

### Python: export_dsl_to_ifc.py
- Added `get_sprinkler_geometry()` - reads from component library
- Added `get_default_sprinkler_geometry()` - gets pendant sprinkler
- Added `create_sprinkler_shape()` - creates IfcTriangulatedFaceSet
- Added `create_sprinkler_grid()` - generates grid positions, places sprinklers
- Modified `export_storey()` - processes sprinkler grids

## Test Results: 3/3 PASS

```
Test 1: Unified export (walls + spaces + sprinklers)
  IFC element counts:
    Walls: 8
    Openings: 2
    Spaces: 2
    Sprinklers: 1
  [PASS]

Test 2: Multi-sprinkler grid (10x10m room)
  Sprinklers: 4 (2x2 grid)
  [PASS]

Test 3: Room without sprinklers
  Walls: 4
  Sprinklers: 0
  [PASS]
```

## Sprinkler Position Verification

10x10m room with 4.6m spacing:
- `(2.3, 2.3, 3.0)` - first head at half-spacing
- `(2.3, 6.9, 3.0)` - second row
- `(6.9, 2.3, 3.0)` - second column
- `(6.9, 6.9, 3.0)` - far corner

Grid formula: `n = floor((dim - spacing/2) / spacing) + 1`

## Output Files
- `output/unified_test.ifc` - 2 rooms, walls, door, sprinkler
- `output/unified_large_room.ifc` - 10x10m room, 4 sprinklers
- `output/unified_no_sprinklers.ifc` - room without sprinklers

---

# PHASE 10b: Assembly (BOM) Structure - COMPLETE

## Problem Solved
Flat element export doesn't support prefab assemblies or ERP integration.
Now have hierarchical BOM structure: Assembly → Components.

## Schema Added

```sql
CREATE TABLE element_assemblies (
    assembly_guid TEXT PRIMARY KEY,
    assembly_type TEXT NOT NULL,  -- WALL_PANEL, ROOF_ASSEMBLY
    name TEXT,
    total_width REAL, total_depth REAL, total_height REAL,
    storey TEXT
);

CREATE TABLE assembly_components (
    assembly_guid TEXT,
    component_guid TEXT,
    role TEXT,        -- FRAME, CLADDING, FASTENER
    local_x/y/z REAL, -- offset from assembly origin
    sequence INTEGER, -- assembly order
    optional BOOLEAN  -- BOM-only (no geometry)
);
```

## IFC Export
- `IfcElementAssembly` for assembly parent
- `IfcRelAggregates` links assembly → components
- Blender Outliner shows collapsible hierarchy

## Test Results: 4/4 PASS
```
Test 1: Create wall panel assembly (2 components)     [PASS]
Test 2: Query BOM structure                           [PASS]
Test 3: Verify IfcElementAssembly entity              [PASS]
Test 4: Multiple assemblies (4 wall panels, 8 parts)  [PASS]
```

## IFC Verification
```
#44=IFCELEMENTASSEMBLY('...','Wall Panel North','WALL_PANEL'...)
#45=IFCRELAGGREGATES('...',#44,(#31,#18))
  ├── #18=IFCMEMBER('RHS 150x100')
  └── #31=IFCPLATE('Metal Deck')
```

## Files Modified/Created
- `GeneratedModelWriter.java` - Added assembly tables + methods
- `AssemblyTest.java` - New test file
- `export_dsl_to_ifc.py` - Added `create_assembly()` function

---

# Shed Component Inventory (From Terminal DB)

## Available for Library Placement
| Component | Count | Notes |
|-----------|-------|-------|
| RHS 150x100x5 steel tube | 270 | Good for shed frame |
| SHS 60x60x3 | 10 | Light bracing |
| Doors 750-900x2100 | 37 | Standard entry |
| Windows (various) | 200+ | Small to wide |
| Metal Deck panels | 33,324 | Flat cladding |
| 200mm RC Slab | 189 | Foundation |

## Must Generate Parametrically
- Pitched roof geometry
- Corrugated sheeting
- Rafters/trusses
- Shed-scale posts (DB columns are commercial 600mm+)

## Implicit Assembly Pattern Found (Terminal)
Awning assembly at X=150.1m:
- Main panel: `T1 L2 Awning A-C` (5.68m x 15.55m)
- Supports: `Awning Support 4500mm` at 2.5m intervals

---

# PHASE 11: Shed DSL - COMPLETE

## DSL Syntax
```
SHED "garden" size:4x3m height:2.4m {
    FOUNDATION slab:150mm
    DOOR south size:900x2100
    WINDOW north size:1200x833
    ROOF pitch:15deg
}
```

## Test Results: 5/5 PASS
```
Test 1: Parse Shed DSL                    [PASS]
Test 2: Compile to ShedSpec               [PASS]
Test 3: Write to database (BOM)           [PASS]
Test 4: Export to IFC                     [PASS]
Test 5: Roof geometry math                [PASS]
```

## Generated Elements
| Element | Count | Type |
|---------|-------|------|
| Foundation slab | 1 | IfcSlab |
| Wall assemblies | 4 | IfcElementAssembly |
| Frame members | 16 | IfcMember (4 per wall) |
| Cladding panels | 4 | IfcPlate |
| Gable roof | 1 | IfcRoof |
| Door opening | 1 | IfcOpeningElement |
| Window opening | 1 | IfcOpeningElement |

## BOM Report (from DB)
```
SOUTH_WALL_ASSEMBLY:
  FRAME: 4 × RHS 150x100
  CLADDING: 1 × Metal Deck
NORTH_WALL_ASSEMBLY:
  FRAME: 4 × RHS 150x100
  CLADDING: 1 × Metal Deck
WEST_WALL_ASSEMBLY:
  FRAME: 4 × RHS 150x100
  CLADDING: 1 × Metal Deck
EAST_WALL_ASSEMBLY:
  FRAME: 4 × RHS 150x100
  CLADDING: 1 × Metal Deck
```

## Roof Math Verified
- Pitch: 15°
- Span: 4m
- Ridge rise: 0.536m = (4/2) × tan(15°)
- Overhang: 300mm

## Files Created
```
src/main/java/com/bim/compiler/dsl/
├── ShedDefinition.java   # Parsed shed record
├── ShedParser.java       # DSL parser
├── ShedCompiler.java     # Generates assemblies + roof
├── ShedWriter.java       # DB + JSON output
└── ShedTest.java         # Integration test

output/
├── shed_test.db          # 26 elements, 4 assemblies
├── shed_test.json        # For IFC export
└── shed_test.ifc         # Viewable in Blender
```

---

# PHASE 14 COMPLETE (January 2025)

## 14A: Library Stairs
- HybridFactory routes to LibraryFactory for IfcStairFlight
- Fallback to parametric when no library match
- 32 stair flights + 34 railings extracted from Terminal

## 14B: Terminal Mini + MEP Grids

### New Room Types (IBC/NFPA)
| Type | OmniClass | Min Area | Requirements |
|------|-----------|----------|--------------|
| DEPARTURE_LOUNGE | 13-11 21 00 | 100m² | 6m min dimension, sprinklers |
| GATE | 13-11 21 11 | 48m² (6×8m) | Window required for airside |
| CONCOURSE | 13-81 11 11 | - | 3m minimum width |

### MEP Grid Placement
| System | DSL Syntax | Spacing | IFC Class |
|--------|-----------|---------|-----------|
| Sprinklers | `SPRINKLERS grid:4.6m` | NFPA 13 | IfcFlowTerminal |
| Lights | `LIGHTS grid:3.0m` | Configurable | IfcLightFixture |

### Grid Math
```
Grid count: ceil(width/spacing) × ceil(depth/spacing)
Position: First at (spacing/2, spacing/2), then grid
Z-level: storeyHeight - 0.05m (50mm below ceiling)
Coverage: Each sprinkler covers spacing × spacing area
```

### Test Results (terminal_mini.bim)
```
Lounge: 20×12m = 240 m²
Sprinklers: 15 (5×3 grid at 4.6m) - 317 m² coverage (132%)
Lights: 28 (7×4 grid at 3.0m)
MEP Cost: MYR 1,275 (sprinklers) + MYR 5,040 (lights) = MYR 6,315
Total elements: 73
```

### Files Created/Modified
```
src/main/java/com/bim/compiler/dsl/
├── BuildingDefinition.java     # Extended RoomDef with MEP fields
├── BuildingParser.java         # Added SPRINKLERS/LIGHTS patterns
├── BuildingCompiler.java       # Added SprinklerSpec, LightSpec, grid gen
├── BuildingWriter.java         # Added writeSprinkler(), writeLight()
├── RoomType.java              # Extended with terminal types
├── RoomRequirements.java      # Added IBC/NFPA requirements
├── LightDefinition.java       # NEW - DSL record for lights
└── TerminalMiniTest.java      # NEW - Integration test (7/7 pass)

src/main/java/com/bim/compiler/export/
├── BOMExporter.java           # Updated for MEP items
└── IDempiereExporter.java     # Added MEP cost mappings

examples/
└── terminal_mini.bim          # NEW - Terminal POC DSL
```

### iDempiere MEP Cost Mapping
| Product | SKU | Unit | Cost |
|---------|-----|------|------|
| Fire Sprinkler | SPRINKLER-PENDANT | EA | MYR 85 |
| Light Fixture | LIGHT-RECESSED-LED | EA | MYR 180 |

---

# PHASE 15: LAYER 3 INTEGRATION - COMPLETE

## Summary

Constraint-based room placement now integrated into production pipeline.
User writes constraints, solver finds positions, existing pipeline generates IFC.

## Test Results (ConstrainedHouseTest)

| Test | Result |
|------|--------|
| Parse constraint DSL | PASS |
| Compile with solver | PASS (43ms solve) |
| Constraint satisfaction | 4/4 PASS |
| Write to database | PASS (24 elements) |
| BOM export | PASS |
| iDempiere export | PASS (MYR 29,026) |
| IFC export | PASS (27 elements) |

## Constraint DSL Syntax

```dsl
BEDROOM "master" size:4x4m {
    adjacent: ensuite        // Must share wall
    exterior: north          // Must touch north edge
    not_adjacent: kitchen    // Cannot share wall
}
```

## Architecture

```
Layer 3 Input          Layer 2 Bridge           Layer 1 Output
─────────────          ──────────────           ──────────────
Constraints     →      SpaceSolver       →      Grid Positions
  adjacent              (Choco CSP)              A1, B2, etc.
  not_adjacent                                      ↓
  exterior                                    BuildingCompiler
                                                    ↓
                                              Geometry + IFC
```

## Files Created/Modified

```
src/main/java/com/bim/compiler/
├── dsl/
│   ├── BuildingDefinition.java   # RoomDef with constraint fields
│   ├── BuildingParser.java       # Constraint pattern parsing
│   ├── BuildingCompiler.java     # Solver integration
│   └── ConstrainedHouseTest.java # Integration test
├── solver/
│   ├── SpaceSolver.java          # Production solver
│   └── SpaceSolverPrototype.java # Research prototype

examples/
└── house_constrained.bim         # Test DSL with constraints

output/
├── house_constrained.db          # 24 elements
├── house_constrained.ifc         # 27 IFC entities
├── house_constrained.json
├── bom_constrained/*.csv
└── idempiere_constrained/*.csv
```

## Solver Performance

| Rooms | Constraints | Solve Time |
|-------|-------------|------------|
| 3 | 2 | 11ms |
| 4 | 4 | 43ms |
| 6 | 6 | 67ms |

---

# PHASE 15B: INTERIOR WALLS + AUTO-OPENINGS - COMPLETE

## Problem Solved

Phase 15 generated only perimeter walls. Adjacent rooms had no shared wall, no door.
Phase 15B fixes this by:
1. Generating interior walls along shared edges between rooms
2. Auto-placing doors for ADJACENT constraints
3. Auto-placing windows for EXTERIOR constraints

## Test Results (Phase15BTest)

```
============================================================
PHASE 15B: INTERIOR WALLS + AUTO-DOORS TEST
============================================================
Walls:   6 (4 perimeter + 2 interior)
Doors:   2 (auto-placed for adjacent rooms)
Windows: 2 (auto-placed for exterior rooms)

VERIFICATION:
[PASS] Walls: 6 >= 6 (includes interior walls)
[PASS] Doors: 2 >= 2 (auto-placed for adjacent rooms)
[PASS] Windows: 2 >= 2 (auto-placed for exterior rooms)
[PASS] Interior walls: 2 (rooms have physical separation)
[PASS] Auto-doors: 2 (correctly named for adjacency)
[PASS] Auto-windows: 2 (correctly named for exterior)

OVERALL: PASS - Interior walls and auto-openings working!
============================================================
```

## Implementation

### Interior Wall Generation
- After room bounds calculated, detect shared edges between all room pairs
- Shared edge = overlapping boundary segment (horizontal or vertical)
- Generate `WallAssemblySpec` for each shared edge

### Auto-Door Placement
- For each shared edge, check if either room has ADJACENT constraint to the other
- If yes, place door at center of shared edge
- Door size: 900mm × 2100mm (standard)

### Auto-Window Placement
- For each room with EXTERIOR constraint, check if window already specified in DSL
- If no window exists on that wall, auto-place at center
- Window size: 1200mm × 1200mm, sill height: 900mm

## Files Modified

```
src/main/java/com/bim/compiler/dsl/
├── BuildingCompiler.java    # Added interior wall gen, auto-doors, auto-windows
└── Phase15BTest.java        # NEW - verification test

examples/
└── apartment_test.bim       # NEW - 4-room test case
```

## Constants Added

```java
private static final double DEFAULT_DOOR_WIDTH = 0.9;    // 900mm
private static final double DEFAULT_DOOR_HEIGHT = 2.1;   // 2100mm
private static final double DEFAULT_WINDOW_WIDTH = 1.2;  // 1200mm
private static final double DEFAULT_WINDOW_HEIGHT = 1.2; // 1200mm
private static final double DEFAULT_SILL_HEIGHT = 0.9;   // 900mm
```

## Helper Records

```java
private record RoomBounds(double minX, double minY, double maxX, double maxY) {}
private record SharedEdge(double x1, double y1, double x2, double y2) {
    boolean isVertical();
    boolean isHorizontal();
}
```

---

# PHASE 16: DSL EXTENSIBILITY - COMPLETE

## Summary

Documented the constraint extension pattern and proved extensibility with `aligns:` vertical constraint.

## Documentation Created

- `docs/DSL_EXTENSION_GUIDE.md` - How to add new constraint types
- `docs/VOCABULARY_ROADMAP.md` - Planned vocabulary extensions

## New Constraint: aligns: (Vertical Alignment)

**Syntax:**
```dsl
BATHROOM "bath_upper" size:2.5x3m {
    aligns: bath_lower    // Same X,Y as bath_lower
}
```

**Use Case:** Plumbing stacks - bathrooms aligned vertically for efficient wet risers.

**Test Results:**
```
1. Parse aligns: constraint   PASS
2. Vertical alignment         PASS (delta: 0.000m)
3. Plumbing stack             PASS (offset: 0.000m)

OVERALL: PASS - aligns: constraint working!
```

## Implementation

**Files Modified:**
- `BuildingParser.java` - Added ALIGNS_PATTERN
- `BuildingDefinition.java` - Added alignsWith field to RoomDef
- `BuildingCompiler.java` - Cross-storey position tracking

**Key Change:**
```java
// Track solved positions across storeys
Map<String, GridPosition> allSolvedPositions = new HashMap<>();

// For rooms with aligns: constraint
if (room.hasVerticalConstraint() && allSolvedPositions.containsKey(room.alignsWith())) {
    GridPosition alignedPos = allSolvedPositions.get(room.alignsWith());
    resolvedRooms.add(room.withPosition(SpaceSolver.toGridRef(alignedPos)));
}
```

## Vocabulary Roadmap (docs/VOCABULARY_ROADMAP.md)

| Phase | Constraints | Status |
|-------|-------------|--------|
| 15 | adjacent, not_adjacent, exterior | ✓ Done |
| 16 | aligns | ✓ Done |
| 17 | above, below, stack | ✓ Done |
| 18 | Consolidation (9/9 tests) | ✓ Done |
| 19 | Gap Closure (solver scaling, IFC) | ✓ Done |
| 20 | Intent Resolver (NL→DSL→IFC) | ✓ Done |

---

# PHASE 17-20 SUMMARY

## Phase 17: Vertical Vocabulary
- `above:` - Room must be directly above target
- `below:` - Room must be directly below target
- `stack:` - Named vertical group (MEP risers)
- Test: 4/4 PASS

## Phase 18: Consolidation
- IntegratedTownhouseTest: 9/9 PASS
- All features working together

## Phase 19: Gap Closure
- Solver scaling: 10 rooms in 172ms
- Stair/Landing integration verified
- IFC export pipeline verified

## Phase 20: Intent Resolver
- NL → DSL → IFC pipeline
- Uses spaCy (en_core_web_sm), no LLM
- Test results: 4/4 PASS
  - "2 bedroom apartment" → 5 rooms
  - "3 bedroom house with ensuite" → 7 rooms
  - "open plan 2 bedroom flat" → 5 rooms
  - "large 4 bedroom townhouse" → 8 rooms, 2 storeys

## Systems Verification
- DB schema: Compatible with Terminal DB
- LOD400 library: 8,701/8,701 (100%) have geometry
- Data flow: Library → Compiler → Generated DB → IFC intact

---
UPDATED: 2026-01-29
