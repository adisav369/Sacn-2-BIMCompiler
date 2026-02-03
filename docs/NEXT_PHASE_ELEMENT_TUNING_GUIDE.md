# Element Placement Tuning & DSL Elaboration - Implementation Guide

**Status:** Ready to Begin (Post-Schema Fix, Post-Discipline Tagging)
**Version:** 0.52.5
**Last Updated:** 2026-02-04

---

## Mission

Systematically verify and tune element placement accuracy through:
1. **Visual inspection** (Blender Full Load with multi-discipline view)
2. **Mathematical validation** (witness claims + sanity checks)
3. **DSL elaboration** (add intent controls where needed)
4. **2D drawing refinement** (architect-grade output)

---

## Current State Assessment

### ✅ What's Working

**Schema & Visualization:**
- ✓ Bonsai Full Load works (element_transforms table present)
- ✓ Multi-discipline visualization (ELEC|188, STR|178, ARC|166, SP|4)
- ✓ Bounding box preview (instant GPU wireframes)
- ✓ 3D viewer (glTF export working)

**Core Compilation:**
- ✓ 3 canonical buildings compile (TB-LKTN, TB-LKTN-2S, School)
- ✓ Witness system (25 claims, 18-21 PROVEN per building)
- ✓ Contract architecture (Layers 0-5 defined, Layer 0 implemented)
- ✓ Deterministic output (same DSL → same DB)

**2D Drawings (Concept Proof):**
- ✓ Floor plans export to SVG
- ✓ Sections (A-A, B-B) generated
- ✓ Schedules (doors, windows, rooms) output
- ⚠ Not architect-grade yet (see improvements needed below)

### ⚠ Known Gaps

**Element Placement (To Verify):**
- Columns: Grid alignment vs actual position
- Beams: Start/end connection to columns
- MEP elements: Room containment, clearances
- Doors/Windows: Wall alignment, opening creation
- Stairs: Landing connections, riser/tread accuracy

**DSL Intent Controls (Missing):**
- Explicit column placement (currently auto-grid only)
- Beam routing control (currently perimeter + grid only)
- MEP fixture positioning (currently auto-placed)
- Opening placement refinement (currently center-of-wall)

**2D Drawings (Improvements Needed):**
- Line weights (all lines same weight currently)
- Dimension annotations (missing)
- Section cut indicators (missing on floor plans)
- Door swing arcs (missing)
- Material hatching (missing)
- Title blocks (missing)
- Scale accuracy verification

---

## Phase 1: Element Placement Verification

**Objective:** Systematically verify each element type's placement accuracy.

### Methodology: Visual + Mathematical Validation

For each element type:
1. **Visual Inspection** (Blender Full Load)
2. **Query Validation** (SQL queries on DB)
3. **Witness Validation** (existing claims)
4. **Tuning Decision** (accept / adjust code / add DSL control)

### Element Checklist

#### 1. Structural Elements

**Columns** (`IfcColumn`, Discipline: STR)

Visual checks:
- [ ] Columns at grid intersections (±tolerance check)
- [ ] Column base Z at slab top (not embedded in slab)
- [ ] Column top Z at ceiling height
- [ ] Multi-storey columns continuous (single element, not stacked)

SQL validation:
```sql
-- Check column grid alignment
SELECT guid,
       ROUND(center_x, 2) as X,
       ROUND(center_y, 2) as Y,
       ROUND(center_z, 2) as Z
FROM element_transforms et
JOIN elements_meta em ON et.guid = em.guid
WHERE em.ifc_class = 'IfcColumn'
ORDER BY X, Y;

-- Compare with grid positions from DSL
-- Expected: X,Y at grid intersections (e.g., 3.0, 6.1, 9.8, ...)
```

Witness claims:
- ✓ STRUCTURAL_GRID_COMPLETE (Claim 24) - verifies column/beam existence
- ✓ COLUMN_SPANS_STOREYS (Claim 26) - verifies multi-storey continuity

**Beams** (`IfcBeam`, Discipline: STR)

Visual checks:
- [ ] Beams connect column-to-column (no floating beams)
- [ ] Beam centerline passes through column axes
- [ ] Beam depth/width appropriate for span
- [ ] No beam/column clashes

SQL validation:
```sql
-- Check beam span (start/end should align with column positions)
SELECT
    em.guid,
    r.minX, r.maxX,  -- Beam extents
    r.minY, r.maxY,
    r.minZ, r.maxZ
FROM elements_meta em
JOIN elements_rtree r ON em.id = r.id
WHERE em.ifc_class = 'IfcBeam';

-- Cross-reference with column positions
```

Witness claims:
- ✓ BEAM_SPAN_LIMIT (Claim 25) - verifies span ≤ 8m for MASONRY

**Members/Studs** (`IfcMember`, Discipline: STR)

Visual checks:
- [ ] Studs at wall corners (no duplicates)
- [ ] Studs at T-junctions (deduplicated)
- [ ] Stud spacing consistent (typically 400-600mm)
- [ ] Lintels above openings ≥ 600mm width

SQL validation:
```sql
-- Check stud deduplication (Phase 3 work)
SELECT storey, COUNT(*)
FROM elements_meta
WHERE ifc_class = 'IfcMember' AND element_type LIKE '%STUD%'
GROUP BY storey;

-- Compare with expected count (perimeter + interior walls)
```

Witness claims:
- ⚠ No specific claim yet (could add STUD_DEDUPLICATION witness)

#### 2. Architectural Elements

**Walls** (`IfcWall`, Discipline: ARC)

Visual checks:
- [ ] Wall centerlines on grid
- [ ] Wall thickness correct (150mm typical)
- [ ] Wall height correct (storey height)
- [ ] Corner mitring correct

SQL validation:
```sql
-- Wall dimensions
SELECT
    em.guid,
    em.element_name,
    ROUND(r.maxX - r.minX, 2) as length,
    ROUND(r.maxY - r.minY, 2) as width,
    ROUND(r.maxZ - r.minZ, 2) as height
FROM elements_meta em
JOIN elements_rtree r ON em.id = r.id
WHERE em.ifc_class = 'IfcWall';
```

Witness claims:
- ✓ ROOMS_ENCLOSED (Claim 3) - indirect validation

**Doors** (`IfcDoor`, Discipline: ARC)

Visual checks:
- [ ] Door in wall opening (not floating)
- [ ] Door at correct height (Z=0 for ground floor)
- [ ] Door swing direction correct
- [ ] Door width matches DSL (D1=900mm, D2=900mm, D3=700mm)

SQL validation:
```sql
-- Door dimensions
SELECT
    guid,
    element_name,
    element_type,
    ROUND(maxX - minX, 2) as width,
    ROUND(maxZ - minZ, 2) as height,
    ROUND(minZ, 2) as base_Z
FROM elements_meta em
JOIN elements_rtree r ON em.id = r.id
WHERE ifc_class = 'IfcDoor';
```

Witness claims:
- ✓ ENTRY (Claim 2) - verifies exterior door exists

**Windows** (`IfcWindow`, Discipline: ARC)

Visual checks:
- [ ] Window in wall opening
- [ ] Sill height correct (typically 900mm above floor)
- [ ] Window head height correct (sill + window height)
- [ ] Lintels above windows (structural requirement)

SQL validation:
```sql
-- Window sill heights
SELECT
    guid,
    ROUND(minZ, 2) as sill_Z,
    ROUND(maxZ - minZ, 2) as window_height,
    ROUND(maxZ, 2) as head_Z
FROM elements_meta em
JOIN elements_rtree r ON em.id = r.id
WHERE ifc_class = 'IfcWindow';
```

Witness claims:
- ✓ WINDOWS_ON_EXTERIOR (Claim 6) - verifies exterior placement
- ✓ CLASSROOM_DAYLIGHT (Claim 20) - verifies daylight ratio

**Slabs** (`IfcSlab`, Discipline: STR or ARC)

Visual checks:
- [ ] Slab at correct Z level (storey base)
- [ ] Slab thickness correct (150mm typical)
- [ ] Slab extent matches building footprint
- [ ] Openings for stairs

SQL validation:
```sql
-- Slab Z levels by storey
SELECT
    storey,
    ROUND(AVG(minZ), 2) as slab_bottom_Z,
    ROUND(AVG(maxZ), 2) as slab_top_Z,
    ROUND(AVG(maxZ - minZ), 2) as thickness
FROM elements_meta em
JOIN elements_rtree r ON em.id = r.id
WHERE ifc_class = 'IfcSlab'
GROUP BY storey;
```

Witness claims:
- ✓ FOUNDATION (Claim 1) - verifies ground slab

**Roof** (`IfcRoof`, Discipline: ARC)

Visual checks:
- [ ] Roof covers all rooms (no gaps)
- [ ] Roof pitch correct (15° typical)
- [ ] Roof overhang correct (SLAB_OVERLAP + overhang)
- [ ] Roof vertices/faces valid (no twisted geometry)

SQL validation:
```sql
-- Roof geometry verification
SELECT
    em.guid,
    bg.vertex_count,
    bg.face_count,
    ROUND(r.maxZ - r.minZ, 2) as ridge_rise
FROM elements_meta em
JOIN elements_rtree r ON em.id = r.id
JOIN element_instances ei ON em.guid = ei.guid
JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
WHERE em.ifc_class = 'IfcRoof';
```

Witness claims:
- ✓ ROOF_COVERS_ALL (Claim 7) - verifies coverage

**Stairs** (`IfcStair`, `IfcStairFlight`, Discipline: ARC)

Visual checks:
- [ ] Stair at correct grid position
- [ ] Riser height ≤ 196mm (IRC)
- [ ] Tread depth ≥ 254mm (IRC)
- [ ] Landing connections correct
- [ ] Total rise = storey height

SQL validation:
```sql
-- Stair dimensions
SELECT
    guid,
    element_name,
    ROUND(maxX - minX, 2) as width,
    ROUND(maxY - minY, 2) as run,
    ROUND(maxZ - minZ, 2) as total_rise
FROM elements_meta em
JOIN elements_rtree r ON em.id = r.id
WHERE ifc_class IN ('IfcStair', 'IfcStairFlight');
```

Witness claims:
- ⚠ Riser/tread checks in test code, not witness yet

#### 3. MEP Elements

**Electrical** (`IfcLightFixture`, `IfcOutlet`, `IfcSwitchingDevice`, Discipline: ELEC)

Visual checks:
- [ ] Lights on ceiling (Z = ceiling height)
- [ ] Outlets on walls (Z = 300mm typical)
- [ ] Switches on walls near doors (Z = 1200mm typical)
- [ ] No MEP/structural clashes

SQL validation:
```sql
-- Check electrical element heights
SELECT
    em.ifc_class,
    ROUND(AVG(center_z), 2) as avg_Z,
    COUNT(*) as count
FROM elements_meta em
JOIN element_transforms et ON em.guid = et.guid
WHERE em.discipline = 'ELEC'
GROUP BY em.ifc_class;
```

Witness claims:
- ✓ ELECTRICAL_IN_SPACES (Claim 8) - verifies room containment
- ✓ ALL_OUTLETS_ON_CIRCUIT (Claim 13) - verifies circuit topology
- ✓ MEP_NO_STRUCTURAL_CLASH (Claim 9) - verifies no clashes

**Plumbing** (`IfcPipeSegment`, Discipline: SP)

Visual checks:
- [ ] Pipes connect fixtures to risers
- [ ] Pipe routing follows walls (not through rooms)
- [ ] Pipe Z levels appropriate (waste below floor, supply in walls)
- [ ] No pipe/structural clashes

SQL validation:
```sql
-- Pipe routing
SELECT
    em.guid,
    em.element_type,
    ROUND(center_x, 2) as X,
    ROUND(center_y, 2) as Y,
    ROUND(center_z, 2) as Z
FROM elements_meta em
JOIN element_transforms et ON em.guid = et.guid
WHERE em.discipline = 'SP'
ORDER BY Z, X, Y;
```

Witness claims:
- ✓ PLUMBING_PIPES_VALID (Claim 12) - verifies pipe extents
- ⚠ PLUMBING_*_COMPLETE (Claims 14-16) - SKIPPED (no system graph yet)

**HVAC** (Future - no elements generated yet)

---

## Phase 2: DSL Elaboration

**Objective:** Add intent controls where auto-placement is insufficient.

### Decision Framework

For each element type, decide:
1. **Auto-placement sufficient?** → Keep current behavior
2. **Needs tuning?** → Adjust placement logic
3. **Needs user control?** → Add DSL syntax

### Proposed DSL Enhancements

#### Explicit Column Placement

**Current:** Auto-placed at grid intersections only.

**Proposed DSL:**
```
STOREY "Ground" {
    COLUMN at:C3 size:200x200mm material:CONCRETE
    COLUMN at:D3 size:200x200mm material:CONCRETE

    // Or: use grid by default
    COLUMNS grid:PERIMETER size:200x200mm
}
```

**Implementation:** Extend `StoreyDef` to include optional `columns` list.

#### Beam Routing Control

**Current:** Perimeter + grid beams only.

**Proposed DSL:**
```
STOREY "Ground" {
    BEAM from:A1 to:E1 depth:300mm  // Explicit routing
    BEAM along:gridline_5           // Follow gridline

    // Or: use structural grid by default
    STRUCTURAL_GRID spacing:6m max_span:8m
}
```

**Implementation:** Extend `StructuralDef` with explicit beam routing.

#### MEP Fixture Positioning

**Current:** Auto-placed by room type (spacetypes.yaml).

**Proposed DSL:**
```
BEDROOM "master" bounds:A1-C3 {
    LIGHT at:center type:CEILING_FIXTURE
    OUTLET at:B1 wall:north height:300mm
    OUTLET at:B3 wall:south height:300mm
    SWITCH at:A1 wall:west height:1200mm
}
```

**Implementation:** Extend `RoomDef` to include optional `fixtures` list.

#### Opening Refinement

**Current:** Openings at wall center.

**Proposed DSL:**
```
BEDROOM "master" bounds:A1-C3 {
    DOOR at:A2 offset:600mm wall:west  // Offset from corner
    WINDOW at:B1 count:2 spacing:1200mm wall:north
}
```

**Implementation:** Add `offset` and `count` parameters to `OpeningDef`.

### DSL Evolution Strategy

1. **Maintain backward compatibility** - Default auto-placement still works
2. **Progressive enhancement** - Add explicit controls as optional
3. **Validation** - Witness claims verify both auto + explicit placement
4. **Documentation** - Update USER_GUIDE.md with new syntax

---

## Phase 3: 2D Drawing Refinement

**Objective:** Elevate 2D output from "concept proof" to "architect-grade".

### Current State (Concept Working)

✓ **What Works:**
- Floor plan SVG generation
- Section views (A-A, B-B)
- Door/window/room schedules
- Room labels with areas
- North arrow + scale bar

⚠ **What's Missing (Architect-Grade):**
- Line weights (walls heavy, dimensions light)
- Dimension strings
- Door swing arcs
- Section cut indicators on plans
- Material hatching (walls, concrete, etc.)
- Title blocks with project info
- Drawing scale verification
- Layer organization

### Improvement Roadmap

#### Priority 1: Line Weights + Hatching

**File:** `scripts/export_2d_drawings.py`

**Changes:**
```python
# Line weight hierarchy
LINEWEIGHTS = {
    'wall_cut': 0.7,      # Heavy (cut elements)
    'wall_beyond': 0.35,  # Medium (beyond cut)
    'dimension': 0.18,    # Light
    'grid': 0.18,         # Light
    'hatch': 0.13         # Very light
}

# Material hatching patterns
HATCH_PATTERNS = {
    'concrete': 'diagonal_lines',
    'masonry': 'brick_pattern',
    'timber': 'wood_grain',
    'insulation': 'stipple'
}
```

**Implementation:** Use SVG `stroke-width` and `<pattern>` elements.

#### Priority 2: Dimensions

**Strategy:** Auto-dimension walls, rooms, and overall building.

**Dimension Types:**
- Wall lengths (exterior walls)
- Room dimensions (internal)
- Opening widths (doors/windows)
- Overall building dimensions

**SVG Implementation:**
```python
def draw_dimension(start, end, offset, text):
    """
    Draw dimension string with extension lines, arrows, and text.

    Args:
        start: (x, y) start point
        end: (x, y) end point
        offset: Distance to offset dimension line from object
        text: Dimension text (e.g., "3600")
    """
    # Extension lines
    # Dimension line
    # Arrows
    # Text (centered, above line)
```

#### Priority 3: Door Swings

**Implementation:** Add arc for door swing (90° or 180°).

```python
def draw_door_swing(door_pos, door_width, wall_direction, swing_angle=90):
    """
    Draw door swing arc.

    Args:
        door_pos: (x, y) hinge point
        door_width: Door width in mm
        wall_direction: 'north', 'south', 'east', 'west'
        swing_angle: 90 or 180 degrees
    """
    # SVG <path> with arc command
```

#### Priority 4: Section Cut Indicators

**Implementation:** Add cut line on floor plan showing section location.

```python
def draw_section_indicator(start, end, section_name, view_direction):
    """
    Draw section cut line with arrows showing view direction.

    Args:
        start: (x, y) section start
        end: (x, y) section end
        section_name: "A-A", "B-B", etc.
        view_direction: Arrow direction
    """
    # Heavy dashed line
    # Arrows at ends
    # Section bubble with text
```

#### Priority 5: Title Block

**Implementation:** Add title block template to each drawing.

```python
TITLE_BLOCK = {
    'project_name': 'From DSL',
    'drawing_title': 'Floor Plan - Ground',
    'scale': '1:100',
    'date': 'Auto',
    'drawn_by': 'BIM Compiler v0.52.5',
    'sheet_number': 'A-101'
}
```

### 2D Drawing Validation

**Quality Checklist:**
- [ ] Scale accurate (measure wall length in SVG vs DB)
- [ ] Dimensions match DB queries
- [ ] Door/window positions match DB
- [ ] Room areas match calculated areas
- [ ] Text readable at print scale
- [ ] Line weights distinguishable
- [ ] PDF export works (svglib/reportlab)

---

## Phase 4: Witness Hardening

**Objective:** Add witness claims for placement accuracy.

### New Witness Claims (Proposed)

**Claim 27: COLUMN_GRID_ALIGNED**
```json
{
  "claim_id": 27,
  "claim_name": "COLUMN_GRID_ALIGNED",
  "status": "PROVEN",
  "evidence": {
    "total_columns": 21,
    "grid_aligned": 21,
    "tolerance_mm": 5.0,
    "max_deviation_mm": 2.3
  }
}
```

**Claim 28: BEAM_COLUMN_CONNECTED**
```json
{
  "claim_id": 28,
  "claim_name": "BEAM_COLUMN_CONNECTED",
  "status": "PROVEN",
  "evidence": {
    "total_beams": 17,
    "beams_with_connections": 17,
    "orphan_beams": 0,
    "connection_tolerance_mm": 50.0
  }
}
```

**Claim 29: LINTEL_AT_HEAD_HEIGHT**
```json
{
  "claim_id": 29,
  "claim_name": "LINTEL_AT_HEAD_HEIGHT",
  "status": "PROVEN",
  "evidence": {
    "total_lintels": 25,
    "correctly_positioned": 25,
    "tolerance_mm": 10.0
  }
}
```

**Claim 30: MEP_FIXTURE_HEIGHTS**
```json
{
  "claim_id": 30,
  "claim_name": "MEP_FIXTURE_HEIGHTS",
  "status": "PROVEN",
  "evidence": {
    "lights_at_ceiling": 85,
    "outlets_at_300mm": 78,
    "switches_at_1200mm": 25,
    "tolerance_mm": 50.0
  }
}
```

### Implementation

**File:** `WitnessBuilder.java`

**Pattern:**
1. Add data collection fields to WitnessBuilder
2. Add `buildXXXClaim()` method
3. Call from `BuildingWriter.generateWitness()`
4. Update claim count (currently 25 → 30)

---

## Execution Plan

### Week 1: Structural Elements

**Day 1-2: Columns**
- [ ] Visual inspection (Blender)
- [ ] SQL validation queries
- [ ] Grid alignment check
- [ ] Add COLUMN_GRID_ALIGNED witness
- [ ] Tune placement if needed

**Day 3-4: Beams**
- [ ] Visual inspection
- [ ] SQL validation
- [ ] Column connection check
- [ ] Add BEAM_COLUMN_CONNECTED witness
- [ ] Tune placement if needed

**Day 5: Studs/Members**
- [ ] Deduplication verification
- [ ] Corner/T-junction check
- [ ] Document findings

### Week 2: Architectural Elements

**Day 1: Walls**
- [ ] Visual inspection
- [ ] Dimension validation
- [ ] Corner mitring check

**Day 2: Doors**
- [ ] Opening verification
- [ ] Dimension check
- [ ] Swing direction

**Day 3: Windows**
- [ ] Sill height validation
- [ ] Lintel verification
- [ ] Add LINTEL_AT_HEAD_HEIGHT witness

**Day 4: Slabs/Roof**
- [ ] Z-level validation
- [ ] Coverage check
- [ ] Geometry validation

**Day 5: Stairs**
- [ ] IRC compliance check
- [ ] Landing connections
- [ ] Add witness if needed

### Week 3: MEP Elements

**Day 1-2: Electrical**
- [ ] Height validation
- [ ] Room containment
- [ ] Add MEP_FIXTURE_HEIGHTS witness

**Day 3: Plumbing**
- [ ] Pipe routing validation
- [ ] Fixture connections

**Day 4-5: DSL Elaboration**
- [ ] Prioritize DSL enhancements
- [ ] Implement top 3 controls
- [ ] Update USER_GUIDE.md

### Week 4: 2D Drawing Refinement

**Day 1-2: Line Weights + Hatching**
- [ ] Implement line weight hierarchy
- [ ] Add material hatching patterns

**Day 3: Dimensions**
- [ ] Implement dimension strings
- [ ] Validate accuracy

**Day 4: Door Swings + Section Indicators**
- [ ] Implement door swing arcs
- [ ] Add section cut indicators

**Day 5: Title Blocks + Validation**
- [ ] Implement title block template
- [ ] Full quality validation

---

## Success Criteria

### Element Placement
- [ ] All structural elements within 5mm tolerance of intended position
- [ ] All MEP elements within 50mm tolerance
- [ ] Zero structural/MEP clashes
- [ ] All witness claims PROVEN (target: 28/30)

### DSL Elaboration
- [ ] Top 3 user controls implemented
- [ ] Backward compatibility maintained
- [ ] USER_GUIDE.md updated
- [ ] Examples demonstrate new syntax

### 2D Drawings
- [ ] Architect reviews and approves
- [ ] Scale accuracy verified
- [ ] All dimensions match DB
- [ ] Title blocks complete
- [ ] PDF export working

### Code Quality
- [ ] All canonical tests pass
- [ ] No regressions
- [ ] PROGRESS.md updated
- [ ] Code review complete

---

## Risk Mitigation

**Risk:** Visual inspection time-consuming
**Mitigation:** Automate with SQL queries + witness claims first, visual spot-check

**Risk:** Breaking changes to placement logic
**Mitigation:** Run canonical tests after every change, witness count must not decrease

**Risk:** DSL changes break existing files
**Mitigation:** Maintain backward compatibility, new syntax optional

**Risk:** 2D drawing complexity
**Mitigation:** Incremental improvements, validate each feature independently

---

## References

**Code Files:**
- `BuildingCompiler.java` - Placement logic
- `StructuralPlacer.java` - Column/beam placement
- `FixturePlacer.java` - MEP fixture placement
- `BuildingWriter.java` - DB writing + witness generation
- `WitnessBuilder.java` - Witness claim catalog
- `export_2d_drawings.py` - 2D drawing generation

**Documentation:**
- `USER_GUIDE.md` - DSL syntax reference
- `bim-dsl-dictionary.md` - DSL vocabulary
- `FOSS_DEVELOPER_GUIDE.md` - Developer reference
- `PROJECT_STRUCTURE.md` - Codebase layout

**Standards:**
- IRC (International Residential Code) - Stairs, egress
- IBC (International Building Code) - Fire safety
- MS 1184 (Malaysia) - Accessibility
- Eurocode 2 / MS 1195 - Structural design

---

**END OF GUIDE**
