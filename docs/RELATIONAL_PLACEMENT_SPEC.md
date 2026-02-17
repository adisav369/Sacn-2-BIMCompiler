# Relational Placement Specification
# From Hardcoded Coordinates to Computed Metadata

**Version:** 1.0
**Date:** 2026-02-17
**Status:** DESIGN — awaiting approval before implementation
**Validates against:** Rosetta Stones (SampleHouse, Duplex, Terminal) at 100% x-ray
**Prime Rule:** ALL DATA, NOTHING HARDCODED. Coordinates are computed from relationships, never stored as source of truth.

---

## 1. Problem Statement

### 1.1 The Violation

The `ad_element_placement` table holds 68,472 rows of absolute coordinates:

```sql
-- Current: hardcoded position (WHERE but not WHY)
building_type='Ifc4_SampleHouse', storey='Ground Floor', ifc_class='IfcDoor',
min_x=1510, max_x=2410, min_y=-570, max_y=310, min_z=0, max_z=2150
```

This is the prime rule violation: SQL hardcode instead of Java hardcode. The coordinates were extracted from reference IFC files and frozen. They are correct for reproduction but:

- **Not editable** — move a wall and the door doesn't follow
- **Not general** — cannot generate a new building (TB-LKTN) from intent
- **Not auditable** — no trace of WHY the door is at that position
- **Not the source of truth** — the relationship "door on south wall of bedroom" is the truth; the coordinates are a consequence

### 1.2 The Goal

Replace stored coordinates with **relational rules** that compute the same coordinates. Verify against existing flat placement rows (68,472 oracle values). Maintain 100% x-ray scores on all 3 Rosetta Stones. Enable new buildings (TB-LKTN) by adding metadata rows only — zero code change.

### 1.3 The Contract

```
CODE (invariant) + METADATA (per building) = EXACT output

Stones: metadata extracted from reference → validates code is correct
New buildings: metadata from intent → same code produces correct output
X-ray tests: regression gate — every code change must pass all 3 Stones
```

---

## 2. Architecture Overview

### 2.1 Current Pipeline (Flat)

```
ad_element_placement (68,472 absolute coordinate rows)
    → PlacementAD.java reads rows
        → ElementPersistence writes to output DB
            → X-ray scores: 100%
```

No relationships. No computation. Just copy.

### 2.2 Target Pipeline (Relational)

```
ad_building_grid          (building → grid lines)
ad_room_boundary          (room → grid cells → wall faces)
ad_element_rule           (element type → host → position rule)
    → RelationalResolver.java computes coordinates from rules
        → ad_element_placement becomes COMPUTED CACHE
            → PlacementAD.java reads cache (unchanged)
                → ElementPersistence writes to output DB (unchanged)
                    → X-ray scores: still 100%
```

Everything downstream of the cache is unchanged. The compiler doesn't know the coordinates were computed, not stored. The x-ray tests see identical output.

### 2.3 Migration Safety

The 68,472 existing flat placement rows become the **test oracle**:

```
For each building:
    computed_coords = RelationalResolver.compute(building_metadata)
    stored_coords   = ad_element_placement.get(building)
    ASSERT computed_coords == stored_coords within 0.01mm
```

If any coordinate differs, the relational rule is wrong, not the Stone. Fix the rule, not the test.

---

## 3. Relational Schema

### 3.1 Building Grid — `ad_building_grid`

Defines the structural grid for each building. Grid lines are the skeleton from which all room boundaries and wall positions derive.

```sql
CREATE TABLE ad_building_grid (
    id            INTEGER PRIMARY KEY,
    building_type TEXT NOT NULL,       -- 'Ifc4_SampleHouse', 'TB_LKTN'
    axis          TEXT NOT NULL,       -- 'X' or 'Y'
    grid_label    TEXT NOT NULL,       -- 'A', 'B', '1', '2' etc.
    position_mm   REAL NOT NULL,       -- absolute position in mm
    UNIQUE(building_type, axis, grid_label)
);

-- Example: TB-LKTN
-- X-axis: A=0, B=3100, C=6800, E=9900
-- Y-axis: 1=0, 2=2300, 3=5400, 4=7000, 5=8500
INSERT INTO ad_building_grid VALUES
    (NULL, 'TB_LKTN', 'X', 'A', 0),
    (NULL, 'TB_LKTN', 'X', 'B', 3100),
    (NULL, 'TB_LKTN', 'X', 'C', 6800),
    (NULL, 'TB_LKTN', 'X', 'E', 9900),
    (NULL, 'TB_LKTN', 'Y', '1', 0),
    (NULL, 'TB_LKTN', 'Y', '2', 2300),
    (NULL, 'TB_LKTN', 'Y', '3', 5400),
    (NULL, 'TB_LKTN', 'Y', '4', 7000),
    (NULL, 'TB_LKTN', 'Y', '5', 8500);

-- Example: SampleHouse (extracted from Stone)
INSERT INTO ad_building_grid VALUES
    (NULL, 'Ifc4_SampleHouse', 'X', '1', 0),
    (NULL, 'Ifc4_SampleHouse', 'X', '2', 3000),
    (NULL, 'Ifc4_SampleHouse', 'X', '3', 6000),
    (NULL, 'Ifc4_SampleHouse', 'Y', 'A', 0),
    (NULL, 'Ifc4_SampleHouse', 'Y', 'B', 3100),
    (NULL, 'Ifc4_SampleHouse', 'Y', 'C', 6100);
```

**Design note:** Grid positions are absolute from building origin (0,0). Multi-unit buildings (Duplex) have per-unit offsets applied by the resolver. Grid labels match the drawing sheet convention.

### 3.2 Room Boundary — `ad_room_boundary`

Maps each room to its grid cell boundaries and identifies which faces are exterior.

```sql
CREATE TABLE ad_room_boundary (
    id            INTEGER PRIMARY KEY,
    building_type TEXT NOT NULL,
    storey        TEXT NOT NULL,       -- 'Ground Floor'
    room_name     TEXT NOT NULL,       -- 'BILIK_2', 'master_bedroom'
    room_type     TEXT NOT NULL,       -- 'BEDROOM' → links to ad_space_type
    grid_min_x    TEXT NOT NULL,       -- grid label: 'D'
    grid_max_x    TEXT NOT NULL,       -- grid label: 'E'
    grid_min_y    TEXT NOT NULL,       -- grid label: '2'
    grid_max_y    TEXT NOT NULL,       -- grid label: '3'
    z_offset_mm   REAL DEFAULT 0,     -- floor level offset from storey
    UNIQUE(building_type, storey, room_name)
);

-- Resolver computes absolute coords:
--   min_x = ad_building_grid WHERE label='D' → 6800
--   max_x = ad_building_grid WHERE label='E' → 9900
--   min_y = ad_building_grid WHERE label='2' → 2300
--   max_y = ad_building_grid WHERE label='3' → 5400
```

**Design note:** Room boundaries reference grid labels, not coordinates. Moving a grid line moves all rooms that reference it. This is the cascade mechanism.

### 3.3 Wall Face — `ad_wall_face`

Each room boundary face that needs a wall. Derived from room adjacency — shared faces get interior walls, unshared faces touching the building envelope get exterior walls.

```sql
CREATE TABLE ad_wall_face (
    id            INTEGER PRIMARY KEY,
    building_type TEXT NOT NULL,
    storey        TEXT NOT NULL,
    room_name     TEXT NOT NULL,       -- owner room
    face          TEXT NOT NULL,       -- 'NORTH', 'SOUTH', 'EAST', 'WEST'
    wall_type     TEXT NOT NULL,       -- → ad_wall_type: 'EXTERIOR_BRICK', 'INTERIOR_BLOCK'
    is_exterior   INTEGER NOT NULL,    -- 1 = exterior envelope, 0 = interior partition
    adjacent_room TEXT,                -- NULL if exterior, else neighbour room name
    UNIQUE(building_type, storey, room_name, face)
);

-- Example: BILIK_2 in TB-LKTN
-- SOUTH face (y=2300): adjacent to ANJUNG → interior wall
-- NORTH face (y=5400): adjacent to BILIK_3 zone → interior wall
-- EAST face (x=9900): exterior envelope → exterior wall
-- WEST face (x=6800): adjacent to RUANG_TAMU → interior wall
```

**Design note:** The resolver computes wall coordinates from the room boundary + face direction + wall type thickness. No coordinates stored here — only topology.

### 3.4 Element Rule — `ad_element_rule`

The core relational table. Each row is a placement rule: "put element X on host Y at position Z."

```sql
CREATE TABLE ad_element_rule (
    id              INTEGER PRIMARY KEY,
    building_type   TEXT NOT NULL,
    storey          TEXT NOT NULL,
    element_ref     TEXT NOT NULL,       -- unique element name: 'DOOR_BILIK2_ENTRY'
    ifc_class       TEXT NOT NULL,       -- 'IfcDoor', 'IfcWindow', 'IfcLightFixture'
    discipline      TEXT NOT NULL,       -- 'ARC', 'ELEC', 'FP', etc.

    -- Host reference (what this element is attached to)
    host_type       TEXT NOT NULL,       -- 'WALL', 'ROOM', 'GRID', 'BUILDING', 'ELEMENT'
    host_ref        TEXT NOT NULL,       -- 'WALL_BILIK2_SOUTH', 'BILIK_2', 'B3', etc.

    -- Position on host
    position_rule   TEXT NOT NULL,       -- 'CENTER', 'FRACTION', 'OFFSET', 'GRID_INTERSECTION'
    position_value  REAL,                -- 0.5 for center, 0.35 for 35% along host, 200 for 200mm offset
    height_mm       REAL,                -- height above host floor (sill height, outlet height, etc.)

    -- Element sizing
    family_ref      TEXT,                -- → ad_opening_family or component_definitions
    width_mm        REAL,                -- override or from family
    height_extent_mm REAL,               -- override or from family
    depth_mm        REAL,                -- override or from family

    -- Orientation
    orientation     TEXT,                -- 'ALONG_HOST', 'PERPENDICULAR', 'NORTH', 'SOUTH', etc.

    -- Geometry
    geometry_hash   TEXT,                -- → component_geometries (NULL = bbox only)
    material_name   TEXT,
    material_rgba   TEXT,

    is_active       INTEGER DEFAULT 1,
    UNIQUE(building_type, storey, element_ref)
);
```

**Position rules explained:**

| `position_rule` | `position_value` | Meaning |
|-----------------|-------------------|---------|
| `CENTER` | NULL | Centered on host (wall midpoint, room center) |
| `FRACTION` | 0.0–1.0 | Fractional position along host (0.35 = 35% from start) |
| `OFFSET` | mm value | Absolute offset from host start point |
| `GRID_INTERSECTION` | NULL | At grid intersection (for columns) |
| `SPACING` | mm value | Repeated at interval (for MEP points, rebar) |
| `INSET` | mm value | Inset from room boundary (for furniture) |

**Examples across both buildings:**

```sql
-- SampleHouse: bedroom window (extracted from Stone)
INSERT INTO ad_element_rule VALUES (NULL,
    'Ifc4_SampleHouse', 'Ground Floor', 'WIN_BEDROOM_N',
    'IfcWindow', 'ARC',
    'WALL', 'WALL_BEDROOM_NORTH',
    'FRACTION', 0.47,  900,           -- 47% along north wall, 900mm sill
    'RES_CASEMENT', 1200, 1000, 150,  -- 1200×1000 casement
    'ALONG_HOST',
    NULL, 'Glass', '0.7,0.8,0.85,0.3',
    1);

-- TB-LKTN: bedroom 2 window (from drawing intent)
INSERT INTO ad_element_rule VALUES (NULL,
    'TB_LKTN', 'Ground Floor', 'WIN_BILIK2_E',
    'IfcWindow', 'ARC',
    'WALL', 'WALL_BILIK2_EAST',
    'CENTER', NULL,    900,            -- centered on east wall, 900mm sill
    'RES_CASEMENT', 1200, 1000, 150,   -- same family, same dims
    'ALONG_HOST',
    NULL, 'Glass', '0.7,0.8,0.85,0.3',
    1);
```

Same `ifc_class`, same `family_ref`, same `position_rule` pattern. Different `building_type`, different `host_ref`, different `position_value` (SH extracted as 0.47, TB-LKTN defaults to CENTER). Same code computes both.

### 3.5 Element Dependency — `ad_element_dependency`

Tracks parent-child relationships for cascade updates. Populated alongside `ad_element_rule`.

```sql
CREATE TABLE ad_element_dependency (
    id            INTEGER PRIMARY KEY,
    building_type TEXT NOT NULL,
    element_ref   TEXT NOT NULL,       -- child: 'DOOR_BILIK2_ENTRY'
    parent_ref    TEXT NOT NULL,       -- parent: 'WALL_BILIK2_SOUTH'
    relation      TEXT NOT NULL,       -- 'HOSTED_ON', 'CONTAINED_IN', 'CONNECTS_TO', 'SUPPORTS'
    cascade_rule  TEXT DEFAULT 'MOVE', -- 'MOVE' = follows parent, 'RECOMPUTE' = re-resolve, 'NONE'
    UNIQUE(building_type, element_ref, parent_ref)
);

-- Cascade chain example:
-- GRID_LINE_D moves → ROOM BILIK_2 boundary changes
--   → WALL_BILIK2_WEST recomputes position
--     → DOOR_BILIK2_ENTRY recomputes position (HOSTED_ON, cascade=MOVE)
--     → OUTLET_BILIK2_01 recomputes position (HOSTED_ON, cascade=MOVE)
```

---

## 4. Computation Engine — `RelationalResolver.java`

### 4.1 Class Design

```java
/**
 * Computes element coordinates from relational metadata.
 * Reads: ad_building_grid, ad_room_boundary, ad_wall_face, ad_element_rule
 * Writes: ad_element_placement (computed cache)
 *
 * Pattern: stateless resolver. All state in metadata tables.
 * Singleton lazy-load, same as PlacementAD / CompilerConfig.
 */
class RelationalResolver {

    record GridSystem(Map<String, Double> xLines, Map<String, Double> yLines) {}
    record RoomExtent(double minX, double maxX, double minY, double maxY,
                      double minZ, double maxZ) {}
    record WallSegment(double x1, double y1, double x2, double y2,
                       String face, double thickness, boolean exterior) {}

    /**
     * Resolve all elements for a building into placement rows.
     * This is the ONLY public entry point.
     */
    List<PlacementAD.Placement> resolve(String buildingType) {
        GridSystem grid = loadGrid(buildingType);
        Map<String, RoomExtent> rooms = resolveRooms(buildingType, grid);
        Map<String, WallSegment> walls = resolveWalls(buildingType, rooms);
        List<PlacementAD.Placement> placements = resolveElements(buildingType, rooms, walls);
        return placements;
    }
}
```

### 4.2 Resolution Order

The computation is strictly ordered — each step depends on the previous:

```
Step 1: loadGrid()        — read ad_building_grid → GridSystem
Step 2: resolveRooms()    — read ad_room_boundary + grid → RoomExtent per room
Step 3: resolveWalls()    — read ad_wall_face + rooms → WallSegment per face
Step 4: resolveElements() — read ad_element_rule + rooms + walls → Placement per element
```

No circular dependencies. Each step is a pure function of its inputs.

### 4.3 Position Computation

For each `ad_element_rule` row, compute absolute coordinates:

```java
Placement computePlacement(ElementRule rule, Map<String, RoomExtent> rooms,
                           Map<String, WallSegment> walls) {
    switch (rule.hostType()) {
        case "WALL" -> {
            WallSegment wall = walls.get(rule.hostRef());
            double wallLength = wall.length();
            double posAlongWall = switch (rule.positionRule()) {
                case "CENTER"   -> 0.5;
                case "FRACTION" -> rule.positionValue();
                case "OFFSET"   -> rule.positionValue() / wallLength;
                default -> throw new IllegalArgumentException(rule.positionRule());
            };
            // Compute element center point on wall
            double cx = wall.x1() + (wall.x2() - wall.x1()) * posAlongWall;
            double cy = wall.y1() + (wall.y2() - wall.y1()) * posAlongWall;
            double cz = rule.heightMm() + rule.heightExtentMm() / 2.0;
            // Derive bbox from center + element dimensions + wall orientation
            return buildPlacement(rule, cx, cy, cz, wall.face());
        }
        case "ROOM" -> {
            RoomExtent room = rooms.get(rule.hostRef());
            // INSET, CENTER, etc. relative to room boundary
            return computeRoomHostedPlacement(rule, room);
        }
        case "GRID" -> {
            // Column at grid intersection, beam spanning grid lines
            return computeGridHostedPlacement(rule, grid);
        }
    }
}
```

### 4.4 Orientation Logic

Orientation derives from host, not stored independently:

```java
String resolveOrientation(String face, String orientationRule) {
    if ("ALONG_HOST".equals(orientationRule)) {
        return switch (face) {
            case "NORTH", "SOUTH" -> "EW";  // element runs east-west along wall
            case "EAST", "WEST"   -> "NS";  // element runs north-south along wall
            default -> "EW";
        };
    }
    return orientationRule;  // explicit override
}
```

No hardcoded rotation angles. The wall face determines the element orientation.

---

## 5. Migration Strategy

### 5.1 Phase RM-1: Schema + Extraction (Non-Breaking)

**Goal:** Create relational tables. Extract relationships from Stones. Do NOT change compiler.

1. Create tables: `ad_building_grid`, `ad_room_boundary`, `ad_wall_face`, `ad_element_rule`, `ad_element_dependency`
2. Write Python extractor: `relational_extractor.py`
   - Input: reference DB + existing `ad_element_placement` rows
   - Output: relational metadata rows
   - Method: for each placement row, determine which room it's in, which wall it's on, what position along the wall
3. Run extractor on all 3 Stones
4. Verify: relational metadata populated, flat placement unchanged
5. **X-ray test: run all 3 Stones — must still pass 100%** (nothing changed in compiler)

### 5.2 Phase RM-2: Computation Engine (Shadow Mode)

**Goal:** Build resolver that computes coordinates from relational rules. Verify against flat placement.

1. Implement `RelationalResolver.java`
2. Add shadow-mode validation to E2E tests:
   ```java
   List<Placement> computed = resolver.resolve(buildingType);
   List<Placement> stored   = placementAD.getAll(buildingType);
   assertCoordsMatch(computed, stored, 0.01); // 0.01mm tolerance
   ```
3. Fix any relational rules where computed != stored
4. **X-ray test: run all 3 Stones — must still pass 100%** (compiler still reads flat placement)

### 5.3 Phase RM-3: Switch to Computed (The Cutover)

**Goal:** Compiler reads computed placement instead of flat stored placement.

1. Add mode flag to `CompilerConfig`:
   ```sql
   INSERT INTO ad_compiler_config VALUES
       (NULL, 'placement_mode', 'RELATIONAL', 'Use computed placement from relational rules', 1);
   ```
2. Modify `PlacementAD.load()`:
   ```java
   if ("RELATIONAL".equals(CompilerConfig.getInstance().getValues("placement_mode").get(0))) {
       // Compute from rules
       placements = RelationalResolver.getInstance().resolve(buildingType);
   } else {
       // Legacy: read flat table
       placements = loadFromFlatTable(buildingType);
   }
   ```
3. **X-ray test: run all 3 Stones — must still pass 100%**
4. If scores hold → relational mode is proven
5. If scores drop → revert to flat mode, debug relational rules

### 5.4 Phase RM-4: TB-LKTN from Intent

**Goal:** Add TB-LKTN metadata rows. Compile from intent. No IFC.

1. Populate `ad_building_grid` for TB-LKTN (from drawing dimensions)
2. Populate `ad_room_boundary` (9 rooms from floor plan)
3. Populate `ad_wall_face` (room adjacency from floor plan)
4. Populate `ad_element_rule` (openings from schedule, MEP from room rules)
5. Create minimal TB-LKTN DSL manifest (building type selector only)
6. Run compiler → output DB
7. Validate: room dimensions match drawing, element counts match schedule
8. Visual check in Bonsai

### 5.5 Phase RM-5: Deprecate Flat Placement

**Goal:** Flat `ad_element_placement` becomes read-only computed cache.

1. `RelationalResolver.resolve()` writes to `ad_element_placement` as cache
2. Remove manually-stored rows for Stones (replaced by computed)
3. Flat table kept for performance (avoid recomputing on every compile)
4. Cache invalidation: recompute when relational metadata changes

---

## 6. Element Type Coverage

### 6.1 By Host Type

| Host Type | Element Types | Position Rule | Count (across Stones) |
|-----------|--------------|---------------|----------------------|
| WALL | IfcDoor, IfcWindow, IfcLightFixture, IfcOutlet, IfcSwitch, IfcAlarm | FRACTION, CENTER, OFFSET | ~2,500 |
| ROOM | IfcFurniture, IfcCovering (floor finish), IfcSlab (floor) | CENTER, INSET | ~1,200 |
| GRID | IfcColumn, IfcBeam | GRID_INTERSECTION, SPAN | ~600 |
| BUILDING | IfcRoof, IfcRailing, IfcStairFlight | ENVELOPE, EDGE | ~100 |
| PIPE_RUN | IfcPipeSegment, IfcPipeFitting, IfcDuctSegment | SPACING, ENDPOINT | ~10,000+ (Terminal) |
| ROOM_ZONE | IfcFireSuppressionTerminal, IfcSensor | SPACING, COVERAGE | ~1,000 |

### 6.2 Priority for Migration

```
TIER 1 (ARC core — proves the concept):
  Walls from room boundaries         ~500 elements
  Doors/windows on walls              ~400 elements
  Slabs under rooms                   ~700 elements
  → Covers SampleHouse fully (55 elements)

TIER 2 (ARC extended):
  Furniture in rooms                  ~250 elements
  Columns at grid intersections       ~160 elements
  Beams spanning grids                ~440 elements
  Roof over building envelope         ~33,300 elements (Terminal plates)
  Railing, stairs                     ~70 elements
  → Covers Duplex ARC fully

TIER 3 (MEP disciplines):
  Pipe segments between fittings      ~8,000 elements
  Pipe/duct fittings at junctions     ~5,300 elements
  MEP terminals in rooms              ~2,800 elements
  Fire suppression coverage           ~900 elements
  → Covers Terminal MEP

TIER 4 (Structural):
  Rebar in structural elements        ~2,660 elements
  → Terminal only
```

### 6.3 Extraction Complexity

| Relationship | Extraction Method | Difficulty |
|-------------|-------------------|------------|
| Element → Room | Spatial containment (bbox inside room bbox) | Low |
| Element → Wall | Spatial adjacency (element bbox touches wall plane) | Medium |
| Position on wall | Project element center onto wall line, compute fraction | Medium |
| Wall → Room face | Match wall position to room boundary edge | Low |
| Grid lines | Cluster wall/column X and Y positions | Medium |
| Pipe connectivity | Endpoint proximity between segments and fittings | High |
| Rebar → Host | Spatial containment in structural element | Medium |

---

## 7. Java Design Standards

### 7.1 Package Structure

```
com.bim.compiler.dsl/
    RelationalResolver.java    — stateless computation engine
    GridResolver.java          — grid line loading + room extent computation
    WallResolver.java          — wall face → wall segment computation
    ElementResolver.java       — element rule → placement computation
    RelationalExtractor.java   — (optional) extract rules from flat placement
```

### 7.2 Design Principles

1. **Stateless resolvers.** All state lives in metadata tables. Resolvers are pure functions: metadata in → placements out. No caches except the lazy-loaded singleton pattern already used throughout.

2. **Record types for data.** Follow `BuildingSpecs.java` pattern — immutable Java records for all intermediate data (GridSystem, RoomExtent, WallSegment). No mutable state.

3. **Single responsibility per class.** GridResolver handles grid → room coords. WallResolver handles room → wall segments. ElementResolver handles rules → element placements. RelationalResolver orchestrates the chain.

4. **Fail-fast on missing data.** If a rule references a wall that doesn't exist, throw immediately with a clear message. No silent fallback to hardcoded values.

5. **Tolerance constants in metadata.** Wall thickness lookups, sill heights, clearances — all from `ad_*` tables, never from Java constants.

### 7.3 Testing Contract

```java
/**
 * Every E2E test gains a relational validation step:
 *
 * 1. Compute placements from relational rules
 * 2. Compare against stored flat placement (oracle)
 * 3. Assert coordinate match within 0.01mm
 * 4. THEN run normal compilation + x-ray check
 *
 * The relational step is ADDITIVE — it validates the rules
 * without changing what the compiler emits. The compiler
 * still reads from ad_element_placement (flat cache) until
 * Phase RM-3 cutover.
 */
```

### 7.4 Configuration

All modes controlled via `ad_compiler_config`:

```sql
-- Phase RM-2: shadow mode (compute + validate, don't use)
INSERT INTO ad_compiler_config VALUES
    (NULL, 'placement_mode', 'FLAT', 'Read stored ad_element_placement', 1);

-- Phase RM-3: relational mode (compute + use)
UPDATE ad_compiler_config SET config_value = 'RELATIONAL'
    WHERE config_key = 'placement_mode';

-- Rollback: switch back to flat if scores drop
UPDATE ad_compiler_config SET config_value = 'FLAT'
    WHERE config_key = 'placement_mode';
```

Toggle without code change. Toggle without recompilation.

---

## 8. Risk Analysis

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Extracted relationships don't match flat placement exactly | HIGH for first pass | LOW — iterative fix | Shadow mode validates before cutover |
| SampleHouse window not perfectly centered (offset by 20mm) | MEDIUM | LOW | Store extracted FRACTION value, not CENTER |
| Terminal MEP pipe connectivity too complex to express relationally | HIGH | MEDIUM | Keep flat placement for Tier 3-4 initially |
| Performance: computing 51K+ placements from rules on every compile | LOW | MEDIUM | Cache in ad_element_placement, invalidate on metadata change |
| Grid extraction ambiguous (multiple possible grid interpretations) | MEDIUM | MEDIUM | Manual verification against Stone reference drawings |

### 8.1 Fallback Plan

At every phase boundary, `ad_compiler_config.placement_mode` can be toggled:
- `FLAT` = current behavior, proven at 100%
- `RELATIONAL` = new behavior, validated against flat oracle

If Phase RM-3 causes any score drop, toggle back to FLAT immediately. Debug relational rules offline. No data loss. No score regression.

---

## 9. Verification Checklist

Each phase must satisfy ALL checks before proceeding:

### Phase RM-1 Exit Criteria
- [ ] All 5 relational tables created in component_library.db
- [ ] Extractor populates tables for all 3 Stones
- [ ] Zero compiler changes
- [ ] X-ray: SampleHouse 100%, Duplex 100%, Terminal ~100%

### Phase RM-2 Exit Criteria
- [ ] RelationalResolver computes placements for all 3 Stones
- [ ] Shadow validation: computed coords match stored coords within 0.01mm
- [ ] Coverage: ≥95% of elements resolved relationally (Tier 1-2)
- [ ] X-ray: SampleHouse 100%, Duplex 100%, Terminal ~100%

### Phase RM-3 Exit Criteria
- [ ] Compiler reads RELATIONAL mode placements
- [ ] X-ray: SampleHouse 100%, Duplex 100%, Terminal ~100%
- [ ] No coordinate differs by more than 0.01mm from flat oracle
- [ ] Rollback to FLAT mode verified working

### Phase RM-4 Exit Criteria
- [ ] TB-LKTN metadata populated (~35 rows + element rules)
- [ ] Compiler produces TB-LKTN output DB from intent only
- [ ] Room dimensions match drawing within 1mm
- [ ] Element counts match drawing schedule
- [ ] Visual check passes in Bonsai

### Phase RM-5 Exit Criteria
- [ ] Flat placement table populated by resolver (cache, not source)
- [ ] All 3 Stones + TB-LKTN compile from relational rules
- [ ] Flat table regenerable: delete → recompute → scores unchanged
- [ ] X-ray: all buildings pass

---

## 10. Summary

The flat `ad_element_placement` table was a correct but architecturally wrong shortcut. It achieved 100% scores by copying positions from Stones. The relational model achieves the same 100% scores by computing positions from rules — and enables new buildings from intent.

The migration is safe because:
1. **The Stones remain the test oracle** — flat placement rows validate relational computation
2. **Shadow mode validates before cutover** — no score risk
3. **Config toggle enables instant rollback** — one SQL UPDATE
4. **Each phase is independently verifiable** — clear exit criteria
5. **The compiler's output path is unchanged** — only the input source switches

The prime rule is restored: coordinates are computed from relationships, never stored as source of truth. The same code + different metadata = different building. The Stones prove the code. The metadata defines the building.

---

*Relational Placement Specification v1.0*
*BIM Intent Compiler — Phase RM (Relational Migration)*
*February 2026*
