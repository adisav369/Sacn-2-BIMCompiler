# SPECIFICATION: Phase 0 House Sanity Checker

**Purpose:** Independent verification that generated building output is recognizably a valid house before fine-grained geometric analysis.

**Principle:** Non-intrusive probe that reads output .db file and reports findings without modifying anything.

---

## 1. Overview

### 1.1 Design Philosophy
```
BIM Compiler (Primary System)     House Sanity Checker (Probe)
─────────────────────────────     ────────────────────────────
Generates .db                  →  Reads .db (read-only)
                                  Applies logical checks
                                  Reports deviations
                                  NO fixes, NO modifications
```

### 1.2 Location
```
bim-compiler/
└── tools/
    └── sanity-checker/
        ├── HouseSanityChecker.java    # Main entry point
        ├── checks/
        │   ├── FoundationCheck.java
        │   ├── EntryDoorCheck.java
        │   ├── WindowPlacementCheck.java
        │   ├── RoomConnectivityCheck.java
        │   ├── RoomProportionCheck.java
        │   ├── RoofCoverageCheck.java
        │   └── EnvelopeContainmentCheck.java
        ├── model/
        │   └── SanityModel.java       # Lightweight read-only model
        ├── report/
        │   └── SanityReport.java      # Output formatting
        └── HouseSanityCheckerTest.java
```

**Note:** This is a separate tool, not integrated into the compiler. It shares no code with the compiler except reading the same .db schema.

---

## 2. Input/Output

### 2.1 Input
- Path to generated `.db` file (SQLite, TERMINAL-conformant schema)
- Optional: Path to original `.bim` DSL file (for constraint cross-reference)

### 2.2 Output
- Console report (human-readable)
- Optional: `sanity_report.json` (machine-readable)
- Exit code: 0 = PASS, 1 = FAIL

---

## 3. Check Specifications

### 3.1 Foundation Ground Level Check

**Question:** Is the building sitting on the ground?

**Method:**
```sql
SELECT MIN(minZ) FROM elements_rtree 
WHERE id IN (
    SELECT guid FROM elements_meta 
    WHERE ifc_class = 'IfcSlab' AND name LIKE '%foundation%'
)
```

**Pass criteria:**
- Foundation exists
- Foundation minZ is within ±50mm of Z=0

**Fail examples:**
- No foundation found
- Foundation at Z=2.8 (floating)
- Foundation at Z=-1.0 (buried)

**Output:**
```
✓ Foundation at ground level (Z=0.000m)
```
or
```
✗ FAIL: Foundation at Z=2.800m (should be at Z=0)
  → Building is floating 2.8m above ground
```

---

### 3.2 Entry Door Check

**Question:** Is there a main door that provides entry from outside?

**Method:**
1. Find all doors from elements_meta where ifc_class = 'IfcDoor'
2. Get building perimeter (convex hull of all exterior walls)
3. Check if at least one door bbox touches the perimeter

**Logic:**
```
For each door:
    door_bbox = get_bbox(door.guid)
    for each exterior_wall:
        wall_bbox = get_bbox(wall.guid)
        if wall_is_on_perimeter AND door_bbox intersects wall_bbox:
            mark door as "exterior door"
            
exterior_door_count >= 1 → PASS
```

**Pass criteria:**
- At least one door connects interior to exterior
- That door has minimum width ≥ 800mm (accessible entry)

**Fail examples:**
- No doors touch perimeter (all internal)
- Doors exist but all are between internal rooms

**Output:**
```
✓ Main entry door exists (D1: 900×2100mm on south perimeter)
```
or
```
✗ FAIL: No entry door found
  → Found 4 doors, but none touch building perimeter
  → Doors found: D2 (bedroom→corridor), D3 (bath→corridor), ...
```

---

### 3.3 Window Placement Check

**Question:** Are windows only on exterior walls?

**Method:**
1. Identify all windows from elements_meta
2. Identify all exterior walls (walls on building perimeter)
3. For each window, verify its parent wall is an exterior wall

**Logic:**
```
exterior_walls = walls where one face touches building perimeter
interior_walls = all other walls

For each window:
    parent_wall = get_wall_containing(window)
    if parent_wall in interior_walls:
        FAIL: "Window {id} on interior wall between {room_a} and {room_b}"
```

**Pass criteria:**
- All windows are on exterior walls
- Each window has at least one face toward outside

**Fail examples:**
- Window between bedroom and corridor
- Window on wall that faces another room

**Output:**
```
✓ All 7 windows on exterior walls
```
or
```
✗ FAIL: Window W2 is on interior wall
  → W2 (1200×1000mm) is between "bilik_utama" and "corridor"
  → Windows should only face exterior
```

---

### 3.4 Room Connectivity Check

**Question:** Can a person walk from any room to any other room?

**Method:**
Build a graph and check connectivity:
```
Nodes = all rooms (SPACE elements)
Edges = doors connecting rooms

For each door:
    room_a = room on one side of door
    room_b = room on other side of door (or EXTERIOR)
    add_edge(room_a, room_b)

Run BFS/DFS from any room
If all rooms visited → PASS
Else → FAIL with list of unreachable rooms
```

**Special cases:**
- OPEN_PLAN zones count as one connected space
- EXTERIOR is a valid "room" for entry doors
- Corridors must connect to at least 2 other spaces

**Pass criteria:**
- Graph is connected
- Every room has at least one door
- No isolated subgraphs

**Fail examples:**
- Bathroom with no door (trapped)
- Bedroom cluster not connected to living areas
- Room only accessible through another bedroom (privacy violation - WARNING, not FAIL)

**Output:**
```
✓ All 6 rooms reachable from entry
  Path: EXTERIOR → living → corridor → bedroom1
                         → corridor → bedroom2
                         → corridor → bathroom
                         → kitchen
```
or
```
✗ FAIL: Room "ensuite" has no door - occupants trapped
✗ FAIL: Rooms "bedroom2", "bedroom3" not reachable from entry
  → Found 2 isolated room clusters
```

---

### 3.5 Room Proportion Check

**Question:** Do rooms have sensible shapes (not impossibly narrow)?

**Method:**
```sql
For each room:
    width = maxX - minX
    depth = maxY - minY
    aspect_ratio = min(width, depth) / max(width, depth)
    
    if aspect_ratio < 0.2:
        FAIL: "Room is too narrow"
    if min(width, depth) < 0.9:
        WARNING: "Room narrower than 900mm"
```

**Thresholds:**
| Metric | Threshold | Severity |
|--------|-----------|----------|
| Aspect ratio < 0.15 | FAIL | Corridor is acceptable, room is not |
| Aspect ratio < 0.25 | WARNING | Unusual but possible |
| Min dimension < 0.6m | FAIL | Person cannot fit |
| Min dimension < 0.9m | WARNING | Below code minimum |

**SpaceType-specific:**
| SpaceType | Min acceptable aspect ratio |
|-----------|----------------------------|
| CORRIDOR | 0.10 (narrow is expected) |
| BATHROOM | 0.30 |
| BEDROOM | 0.40 |
| LIVING | 0.40 |
| STORAGE | 0.20 |

**Output:**
```
✓ Room proportions reasonable
  bilik_utama: 4.0×3.5m (ratio 0.88)
  bathroom: 2.5×2.0m (ratio 0.80)
  corridor: 5.0×1.2m (ratio 0.24) [CORRIDOR - acceptable]
```
or
```
✗ FAIL: Room "bedroom2" has impossible proportions
  → 6.0m × 0.4m (aspect ratio 0.07)
  → This is a slot, not a room
```

---

### 3.6 Roof Coverage Check

**Question:** Does the roof cover the entire building?

**Method:**
1. Get building footprint (2D projection of all rooms at Z=0)
2. Get roof footprint (2D projection of roof at eave level)
3. Check that building footprint ⊆ roof footprint

**Logic:**
```
building_polygon = union of all room polygons (2D, XY plane)
roof_polygon = roof boundary projected to XY plane

coverage = area(intersection(building_polygon, roof_polygon)) / area(building_polygon)

if coverage >= 0.98 → PASS
if coverage >= 0.90 → WARNING
if coverage < 0.90 → FAIL
```

**Also check:**
- Roof overhangs beyond walls (expected for eaves)
- Roof is above top of walls, not below

**Output:**
```
✓ Roof covers building footprint (100% coverage)
  Roof overhang: 600mm on all sides
```
or
```
✗ FAIL: Roof covers only 73% of building
  → Rooms exposed: "bedroom3" (northeast corner)
  → Roof appears offset 2.1m to southwest
```

---

### 3.7 Envelope Containment Check

**Question:** Are all rooms inside the building envelope?

**Method:**
```
building_bbox = bbox enclosing all exterior walls
room_tolerance = 50mm  # Allow for wall thickness

For each room:
    room_bbox = get_bbox(room)
    if not building_bbox.contains(room_bbox, tolerance=room_tolerance):
        FAIL: "Room extends outside building"
```

**Check in 3D:**
- XY: Room within wall perimeter
- Z: Room between foundation (Z=0) and roof

**Output:**
```
✓ All rooms inside building envelope
```
or
```
✗ FAIL: Room "porch" extends outside building envelope
  → Room bbox: (0, 0) to (3, 2)
  → Building bbox: (1, 0) to (10, 8)
  → Room extends 1.0m beyond west wall
```

---

## 4. Report Format

### 4.1 Console Output
```
╔══════════════════════════════════════════════════════════════╗
║              HOUSE SANITY CHECK - Phase 0 Probe              ║
╠══════════════════════════════════════════════════════════════╣
║ Input: output/tb_lktn.db                                     ║
║ Date:  2025-01-30 14:32:00                                   ║
╚══════════════════════════════════════════════════════════════╝

[1/7] Foundation Ground Level
      ✓ PASS: Foundation at Z=0.000m

[2/7] Entry Door
      ✓ PASS: Main entry D1 (900×2100mm) on south perimeter

[3/7] Window Placement  
      ✓ PASS: All 7 windows on exterior walls

[4/7] Room Connectivity
      ✓ PASS: All 6 rooms reachable from entry

[5/7] Room Proportions
      ✓ PASS: All rooms have sensible proportions
      
[6/7] Roof Coverage
      ✓ PASS: Roof covers 100% of building footprint

[7/7] Envelope Containment
      ✓ PASS: All rooms inside building envelope

══════════════════════════════════════════════════════════════
VERDICT: ✓ This is recognizably a house (7/7 checks passed)
══════════════════════════════════════════════════════════════
```

### 4.2 Failure Report
```
╔══════════════════════════════════════════════════════════════╗
║              HOUSE SANITY CHECK - Phase 0 Probe              ║
╠══════════════════════════════════════════════════════════════╣
║ Input: output/broken_house.db                                ║
║ Date:  2025-01-30 14:32:00                                   ║
╚══════════════════════════════════════════════════════════════╝

[1/7] Foundation Ground Level
      ✓ PASS: Foundation at Z=0.000m

[2/7] Entry Door
      ✗ FAIL: No entry door found
      
      Details:
      - Found 3 doors total
      - D2: between "bedroom" and "corridor" (internal)
      - D3: between "bathroom" and "corridor" (internal)  
      - D4: between "kitchen" and "corridor" (internal)
      - None touch building perimeter
      
      Guidance: Add door on exterior wall (south recommended for entry)

[3/7] Window Placement  
      ✗ FAIL: 1 window on interior wall
      
      Details:
      - W2 (1200×1000mm) between "bilik_utama" and "corridor"
      
      Guidance: Move W2 to exterior wall or remove

[4/7] Room Connectivity
      ✗ FAIL: 1 room unreachable
      
      Details:
      - "ensuite" has no doors
      - Occupants would be trapped
      
      Guidance: Add door connecting "ensuite" to "bilik_utama"

[5/7] Room Proportions
      ✓ PASS: All rooms have sensible proportions
      
[6/7] Roof Coverage
      ⚠ WARNING: Roof covers 94% of building footprint
      
      Details:
      - Small gap at northeast corner (0.3m × 0.4m)
      
      Guidance: Extend roof or check for modeling error

[7/7] Envelope Containment
      ✓ PASS: All rooms inside building envelope

══════════════════════════════════════════════════════════════
VERDICT: ✗ NOT a valid house

Summary:
- FAIL: 3 critical issues
- WARNING: 1 issue
- PASS: 3 checks

The building has fundamental problems that must be resolved
before fine-grained geometric verification.
══════════════════════════════════════════════════════════════
```

### 4.3 JSON Output (Optional)
```json
{
  "input": "output/tb_lktn.db",
  "timestamp": "2025-01-30T14:32:00Z",
  "verdict": "PASS",
  "checks": [
    {
      "id": "foundation_ground_level",
      "status": "PASS",
      "value": 0.0,
      "threshold": 0.05,
      "unit": "m"
    },
    {
      "id": "entry_door",
      "status": "PASS", 
      "door_id": "D1",
      "dimensions": [0.9, 2.1],
      "wall": "south_perimeter"
    }
  ],
  "summary": {
    "total": 7,
    "pass": 7,
    "warning": 0,
    "fail": 0
  }
}
```

---

## 5. Implementation Notes

### 5.1 Database Queries

The checker needs to read from these tables:
```sql
-- Elements and their types
SELECT guid, ifc_class, name, discipline FROM elements_meta;

-- Bounding boxes for spatial checks
SELECT id, minX, minY, minZ, maxX, maxY, maxZ FROM elements_rtree;

-- Spatial hierarchy (room containment)
SELECT * FROM spatial_structure;

-- Assembly relationships (door in wall)
SELECT * FROM assembly_components;
```

### 5.2 No Compiler Dependencies

The sanity checker must NOT import:
- Any class from `com.bim.compiler.*`
- Any validation logic from the primary system
- Any constants from BIMConstants.java

It should define its own thresholds based on building code / human factors, not the compiler's tolerance values.

### 5.3 Geometry Helpers

Simple 2D geometry needed (implement from scratch, do not reuse compiler code):
- Point-in-polygon
- Polygon intersection area
- Bounding box containment
- Graph connectivity (BFS/DFS)

---

## 6. Test Cases

### 6.1 Test with Known Good Building
```java
@Test
void tbLktnShouldPassAllChecks() {
    SanityReport report = HouseSanityChecker.check("output/tb_lktn.db");
    assertEquals(Verdict.PASS, report.getVerdict());
    assertEquals(0, report.getFailCount());
}
```

### 6.2 Test with Intentionally Broken Buildings

Create test fixtures:
```
test/fixtures/
├── no_entry_door.db      # All doors internal
├── trapped_room.db       # Room with no door
├── floating_building.db  # Foundation at Z=3
├── interior_window.db    # Window between rooms
├── narrow_room.db        # 5m × 0.3m "bedroom"
├── partial_roof.db       # Roof covers 70%
└── room_outside.db       # Room extends past walls
```

Each test should:
1. Run the checker
2. Verify it catches the specific issue
3. Verify the failure message is helpful

### 6.3 Edge Cases
```java
@Test
void openPlanShouldCountAsConnected() {
    // OPEN_PLAN with zones should not report "zones not connected"
}

@Test  
void porchCanExtendBeyondMainEnvelope() {
    // PORCH is allowed to be outside main building bbox
}

@Test
void corridorCanBeNarrow() {
    // 1.2m wide corridor should not fail proportion check
}
```

---

## 7. Execution

### 7.1 Command Line
```bash
# Basic usage
java -jar sanity-checker.jar output/tb_lktn.db

# With JSON output
java -jar sanity-checker.jar output/tb_lktn.db --json sanity_report.json

# Verbose mode (show all details even for passing checks)
java -jar sanity-checker.jar output/tb_lktn.db --verbose
```

### 7.2 Exit Codes
```
0 = All checks PASS
1 = At least one FAIL
2 = Input file not found or invalid
3 = Checker internal error
```

### 7.3 Integration with Build
```bash
# Run after compilation, fail build if sanity check fails
mvn compile
java -cp target/classes com.bim.compiler.dsl.BIMCompiler examples/tb-lktn.bim output/
java -jar tools/sanity-checker.jar output/tb_lktn.db || exit 1
```

---

## 8. Definition of Done

- [ ] HouseSanityChecker.java reads .db independently (no compiler imports)
- [ ] All 7 checks implemented
- [ ] Console report formatted as specified
- [ ] JSON output optional
- [ ] Test with tb_lktn.db passes all 7 checks
- [ ] Test fixtures for each failure mode
- [ ] Each failure mode caught with helpful message
- [ ] Exit codes work correctly
- [ ] Can run standalone from command line

---

## 9. Future Extensions (Not in Phase 0)

For later phases, not now:
- Multi-storey vertical connectivity (stairs reach all floors)
- Accessibility checks (wheelchair turning radius)
- Fire egress (two exits, travel distance)
- MEP sanity (plumbing stack vertical, electrical panels accessible)

---

*Specification v1.0 - Phase 0 House Sanity Checker*
*Follows probe methodology: independent, non-intrusive, reports only*
