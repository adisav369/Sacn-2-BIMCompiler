# TACK-FIX Method Specification

**Version:** 1.0 (2026-03-18, session 21)
**Depends on:** [BOMBasedCompilation.md](BOMBasedCompilation.md) §4, [DATA_MODEL.md](DATA_MODEL.md) §1.2
**Status:** PRE-CODE — implement after review

> **Problem:** Three code paths compute BOM line dx/dy/dz using centroid-relative
> offsets instead of LBD-to-LBD (minCorner-to-minCorner) per BBC.md §4.
> This passes EN-BLOC because centroid offsets round-trip correctly when there
> is no stacking. It will fail WALK-THRU and BIM Designer placement.

---

## 1. Root Cause Analysis

### 1.1 The Specification (BBC.md §4.0)

**Schema-Not-Geometry classification: ERP-maths.** LBD (Left-Back-Down) is a
positional convention using `minX()/minY()/minZ()` from the element's AABB.
There is no IFC relationship for "left-back-down corner" — LBD is derived from
already-extracted element dimensions. The tack arithmetic (`child.minX -
parent.minX`) is the manufacturing offset convention, same category as M12 pipe
clearance: arithmetic on product positions IS the correct method. Not a schema gap.

```
dx = child.minX - parent.minX     (LBD X offset)
dy = child.minY - parent.minY     (LBD Y offset)
dz = child.minZ - parent.minZ     (LBD Z offset)
```

World coordinate reconstruction:
```
element_LBD = building_origin + tack[1] + tack[2] + ... + tack[N]
centroid    = element_LBD + (width/2, depth/2, height/2)
```

### 1.2 The Violation

| File | Line | Current (WRONG) | Correct (LBD) |
|------|------|-----------------|----------------|
| `ScopeBomBuilder.java` | 137-139 | `e.centroidX() - ox` | `e.minX() - setMinX` |
| `FloorRoomBomBuilder.java` | 105, 130 | `0.0, 0.0, 0.0` (hardcoded) | Measured room/floor LBD offset |
| `VerbDetector.java` | 385-395 | `e.centroidX() - gMinCentroidX` | `e.minX() - gMinX` |

### 1.3 Why It Hid

EN-BLOC compilation does not test tack evaluation — it restacks the entire BOM
hierarchy as-is (BBC.md §3.3). Centroid offsets round-trip correctly because:
```
centroid = origin + dx_centroid + width/2
         = origin + (centroid - parent_origin) + width/2    -- NO, this doubles
```
Actually, EN-BLOC skips tack evaluation entirely — it reads dx/dy/dz and adds
them to the parent origin regardless of whether they represent LBD or centroid
offsets. The PlacementCollectorVisitor then adds +halfW/D/H to recover centroid
for comparison with the reference. This recovery formula is correct for LBD
offsets but produces the wrong centroid for centroid offsets. The error was
masked by W-TACK-1 being advisory (WARN, not FAIL).

### 1.4 Impact

| Building | Affected Path | Lines Affected | Error Type |
|----------|--------------|----------------|------------|
| SH | ScopeBomBuilder (RE path) | ~48 furniture leaf lines | centroid-as-tack |
| DX | ScopeBomBuilder (RE path) | ~153 scope-assigned lines | centroid-as-tack |
| TE | VerbDetector.detectCluster() | CLUSTER verb offsets (46K+ instances) | centroid-as-group-origin |
| SH/DX | FloorRoomBomBuilder | FLOOR→ROOM + FLOOR→SPACE lines | zero offset (no parent context) |

---

## 2. Method Specifications

### 2.1 FIX-1: ScopeBomBuilder — Scope Box Leaf Offsets

**File:** `IFCtoBOM/src/main/java/com/bim/ifctobom/ScopeBomBuilder.java`
**Method:** `build()` lines 134-143

**Current code (WRONG):**
```java
// Line 137-139: centroid offset from scope box origin
double dx = e.centroidX() - ox;     // ox = scope box origin X
double dy = e.centroidY() - oy;
double dz = e.centroidZ() - oz;
```

**Corrected code:**
```java
// Line 137-139: LBD offset from SET BOM LBD (BBC.md §4)
double dx = e.minX() - setMinX;     // setMinX = SET BOM AABB minX
double dy = e.minY() - setMinY;
double dz = e.minZ() - setMinZ;
```

**Preconditions:**
- `setMinX/Y/Z` computed from assigned elements (already computed at lines 116-121)
- Must use same assigned elements list that determined the SET AABB

**Postconditions:**
- `dx >= 0, dy >= 0, dz >= 0` (child LBD always within or at parent LBD)
- `dx + e.widthMm()/1000 <= setAabbW/1000` (child fits within parent, within tolerance)

**Invariant (W-TACK-1):**
```
For every LEAF line under SET BOM:
  dx + allocated_width_mm/1000 <= parent.aabb_width_mm/1000 * 1.01
  (same for Y/Z)
```

**Scope box origin clarification (BBC.md §4.1):**
The YAML `origin_m` (ox, oy, oz) is a **containment filter** — it determines
which elements belong to this scope space. It is NOT a spatial reference for
offsets. The tack_from on each leaf line comes from the element's measured LBD
relative to the SET BOM's measured LBD (setMinX/Y/Z), not from the scope box origin.

**Test:**
```
SH_LIVING_SET elements: dx = element.minX - livingSetMinX
All dx >= 0
All dx + width/1000 <= set_aabb_width/1000 * 1.01
W-TACK-1 reports 0 overshoots → PASS
```

### 2.2 FIX-2: FloorRoomBomBuilder — FLOOR→ROOM and FLOOR→SPACE Offsets

**File:** `IFCtoBOM/src/main/java/com/bim/ifctobom/FloorRoomBomBuilder.java`
**Methods:** `insertSpaceLine()` line 105, `insertBuildingChild()` line 130

**Current code (WRONG):**
```java
// insertSpaceLine line 105: hardcoded zero
// dx, dy, dz = 0.0, 0.0, 0.0

// insertBuildingChild line 130: hardcoded zero
// dx, dy, dz = 0.0, 0.0, 0.0
```

**Problem:** FloorRoomBomBuilder has no access to element positions — it works
purely from YAML configuration. The scope box origin and AABB are available
from `SpaceConfig`, but the actual element envelope (used by ScopeBomBuilder)
is computed later.

**Two-phase solution:**

**Phase A — BUILDING→FLOOR line:** The FLOOR BOM's LBD position within the
BUILDING is known from the storey's element envelope. This is already available
in `StructuralBomBuilder` (which computes floor AABB). FloorRoomBomBuilder
must receive the floor LBD offset from the caller.

```java
// Updated signature:
public static int build(Connection bomConn, BuildingConfig config,
                        Map<String, double[]> floorLbdOffsets)  // NEW: storey→[dx,dy,dz]

// insertBuildingChild: use floorLbdOffset
double[] offset = floorLbdOffsets.get(storeyName);
// dx = floorMinX - buildingMinX, dy = floorMinY - buildingMinY, dz = ...
```

**Phase B — FLOOR→SPACE line:** The SET BOM's LBD position within the FLOOR
requires the SET AABB, which is computed by ScopeBomBuilder. Two approaches:

1. **Post-hoc update (simpler):** ScopeBomBuilder computes setMinX/Y/Z and
   updates the FLOOR→SPACE line's dx/dy/dz after SET BOM creation.
2. **Pre-compute (cleaner):** Compute space element envelopes before
   FloorRoomBomBuilder runs.

**Recommended: Option 1 (post-hoc update).** ScopeBomBuilder already knows
setMinX/Y/Z (lines 116-121). After inserting leaf lines, it can UPDATE the
parent FLOOR BOM's line that references this SET BOM:

```java
// In ScopeBomBuilder, after SET BOM creation:
double setDxInFloor = setMinX - floorMinX;
double setDyInFloor = setMinY - floorMinY;
double setDzInFloor = setMinZ - floorMinZ;
updateFloorToSetOffset(bomConn, floorBomId, space.templateBom(),
                       setDxInFloor, setDyInFloor, setDzInFloor);
```

**Preconditions:**
- `floorMinX/Y/Z` available (from storeyElements envelope or passed parameter)
- `setMinX/Y/Z` available (from ScopeBomBuilder assigned element envelope)

**Postconditions:**
- FLOOR→SPACE line dx/dy/dz reflects SET BOM LBD offset from FLOOR LBD
- BUILDING→FLOOR line dx/dy/dz reflects FLOOR LBD offset from BUILDING LBD
- All offsets >= 0

**Data flow requires pipeline coordination:**
```
IFCtoBOMPipeline.run():
  1. CompositionBomBuilder  → creates BUILDING BOM with world LBD origin
  2. FloorRoomBomBuilder    → creates FLOOR BOMs + FLOOR→SPACE lines (dx=0 initially)
  3. ScopeBomBuilder        → creates SET BOMs, fixes FLOOR→SPACE dx/dy/dz  ← UPDATE
  4. StructuralBomBuilder   → creates structural BOMs (already correct)

  Post-ScopeBomBuilder: UPDATE BUILDING→FLOOR line dx/dy/dz with floor LBD offsets
```

### 2.3 FIX-3: VerbDetector.detectCluster() — Group Origin

**File:** `IFCtoBOM/src/main/java/com/bim/ifctobom/VerbDetector.java`
**Method:** `detectCluster()` lines 380-433

**Current code (WRONG):**
```java
// Lines 385-387: group origin = minimum CENTROID
double gMinX = elements.stream().mapToDouble(ExtractionElement::centroidX).min().orElse(0);
double gMinY = elements.stream().mapToDouble(ExtractionElement::centroidY).min().orElse(0);
double gMinZ = elements.stream().mapToDouble(ExtractionElement::centroidZ).min().orElse(0);

// Lines 393-395: offsets relative to minimum CENTROID
offsets[i][0] = e.centroidX() - gMinX;
offsets[i][1] = e.centroidY() - gMinY;
offsets[i][2] = e.centroidZ() - gMinZ;
```

**Corrected code:**
```java
// Lines 385-387: group origin = minimum LBD (BBC.md §4)
double gMinX = elements.stream().mapToDouble(ExtractionElement::minX).min().orElse(0);
double gMinY = elements.stream().mapToDouble(ExtractionElement::minY).min().orElse(0);
double gMinZ = elements.stream().mapToDouble(ExtractionElement::minZ).min().orElse(0);

// Lines 393-395: offsets = LBD-to-LBD relative to group minimum
offsets[i][0] = e.minX() - gMinX;
offsets[i][1] = e.minY() - gMinY;
offsets[i][2] = e.minZ() - gMinZ;
```

**Preconditions:**
- All elements in the group have the same M_Product_ID (same dimensions)
- `minX/Y/Z` available on ExtractionElement

**Postconditions:**
- `offsets[0] = (0, 0, 0)` (first element at group origin, after sorting)
- All offsets >= 0
- Self-check round-trip passes (existing verification at lines 406-430)

**CLUSTER verb_ref format (unchanged):**
```
CLUSTER(ox1,oy1,oz1;ox2,oy2,oz2;...)
```
Each offset is now LBD-relative instead of centroid-relative. The verb
expander must place each instance at:
```
instance_LBD = parent_LBD + group_dx + offset[i]
centroid     = instance_LBD + (width/2, depth/2, height/2)
```

**Consistency with DisciplineBomBuilder:**
The factored recipe line's dx/dy/dz is already LBD-correct in DisciplineBomBuilder
(line 195: `gMinX - fMinX` using `minX`). The CLUSTER offsets are relative to that
group origin. After this fix, the chain is consistent:
```
element_LBD = buildingOrigin + floorDx + discDx(=0) + groupDx + clusterOffset[i]
            = buildingOrigin + (fMinX-bMinX) + 0 + (gMinX-fMinX) + (e.minX-gMinX)
            = buildingOrigin + e.minX - bMinX
            = e.minX  ✓  (since buildingOrigin = bMinX)
```

---

## 3. Affected Callers — Impact Analysis

| Caller | What changes | Risk |
|--------|-------------|------|
| `IFCtoBOMPipeline.run()` | Pass floor LBD offsets to FloorRoomBomBuilder | Low — data already computed |
| `ScopeBomBuilder.build()` | Use setMinX instead of ox; post-hoc UPDATE FLOOR→SPACE offset | Medium — must coordinate with FloorRoomBomBuilder |
| `VerbDetector.detectCluster()` | minX instead of centroidX | Low — self-contained, self-verifying |
| `PlacementCollectorVisitor` | Already uses +halfW/D/H recovery | None — correct with LBD offsets |
| `BomValidator W-TACK-1` | Promote WARN → FAIL after fix | Gate change — all 3 stones must pass |
| `BomValidator W-BUFFER-1` | No change (still advisory, T-3 pending) | None |

---

## 4. Test Specifications

### 4.1 W-TACK-1 Promotion (WARN → FAIL)

**Current:** W-TACK-1 counts LEAF lines where `dx + width/1000 > parent_width/1000 * 1.01`.
Reports WARN if overshoots > 0. Does not gate.

**After fix:** W-TACK-1 reports FAIL if overshoots > 0. Pipeline gates on FAIL.

**Acceptance criteria:**
```
SH: W-TACK-1 PASS (0 overshoots out of ~55 lines)
DX: W-TACK-1 PASS (0 overshoots out of ~1099 lines)
TE: W-TACK-1 PASS (0 overshoots out of ~1442 recipe lines)
```

### 4.2 Regression Tests

| Test | Assertion | Building |
|------|-----------|----------|
| G1-COUNT | Same element count as before | SH=55, DX=1099, TE=48428 |
| G2-VOLUME | Volume delta unchanged or improved | All 3 |
| G3-DIGEST | Spatial digest matches reference | All 3 |
| G5-PROVENANCE | All 7 provenance checks pass | All 3 |
| W-TACK-1 | 0 overshoots (promoted to FAIL) | All 3 |
| W-BUFFER-1 | Advisory unchanged (T-3 still pending) | All 3 |

### 4.3 Unit Test: Offset Arithmetic

```java
@Test void scopeBomBuilder_leafOffset_isLBD() {
    // Given: element at minX=10, centroidX=15, width=10
    //        SET BOM minX = 5
    // When:  ScopeBomBuilder computes dx
    // Then:  dx = 10 - 5 = 5 (not 15 - 5 = 10)
    assertEquals(5.0, computedDx, 0.001);
}

@Test void verbDetector_clusterOffset_isLBD() {
    // Given: elements with minX = [10, 20, 30]
    // When:  detectCluster computes offsets
    // Then:  offsets = [0, 10, 20] (not centroid-relative)
    assertEquals(0.0, offsets[0][0], 0.001);
    assertEquals(10.0, offsets[1][0], 0.001);
    assertEquals(20.0, offsets[2][0], 0.001);
}
```

### 4.4 Pipeline Coordination Test — FIX-2 Two-Phase Chain

FIX-2 introduces pipeline coupling: FloorRoomBomBuilder receives `floorLbdOffsets`,
ScopeBomBuilder does post-hoc UPDATE on FLOOR→SPACE lines. This chain must be
tested as an integration, not just leaf arithmetic.

```java
@Test void pipelineCoordination_buildingToLeaf_cumulativeOffset() {
    // Given: SH pipeline run with TACK-FIX applied
    // When:  read m_bom_line chain: BUILDING → FLOOR → SPACE(SET) → LEAF
    // Then:  cumulative dx/dy/dz from BUILDING to LEAF equals:
    //        leaf.minX - building.origin_x (world LBD)
    //
    // Verify each link in the chain:
    //   BUILDING→FLOOR line: dx = floorMinX - buildingMinX  (>= 0)
    //   FLOOR→SPACE line:    dx = setMinX - floorMinX       (>= 0, post-hoc UPDATE)
    //   SPACE→LEAF line:     dx = leaf.minX - setMinX       (>= 0, FIX-1)
    //
    // Sum: buildingOrigin + floorDx + spaceDx + leafDx = leaf.minX  ✓
    //
    // Test for SH (simple) and DX (multi-floor, multi-scope)
}

@Test void floorToSpaceOffset_notZero_afterScopeBomBuilder() {
    // Given: SH pipeline run
    // When:  read FLOOR→SPACE m_bom_line for SH_LIVING_SET
    // Then:  dx != 0 OR dy != 0 OR dz != 0
    //        (FIX-2 post-hoc UPDATE must have fired)
    //        dx = livingSetMinX - floorMinX
}
```

**Rationale:** TACK_FIX §2.2 Option 1 (post-hoc UPDATE) means correctness
depends on ScopeBomBuilder running AFTER FloorRoomBomBuilder AND having access
to floorMinX/Y/Z. If pipeline order changes or params aren't passed, this chain
silently breaks with zero offsets — the pre-fix state.

### 4.5 Full Pipeline Test

```bash
# Regenerate all 3 stones from scratch
rm library/SH_BOM.db library/DX_BOM.db library/TE_BOM.db
./scripts/run_RosettaStones.sh classify_sh.yaml
./scripts/run_RosettaStones.sh classify_dx.yaml
./scripts/run_RosettaStones.sh classify_te.yaml

# All gates must be GREEN
# W-TACK-1 must report PASS (not WARN)
```

---

## 5. Sequence Diagram — TACK-FIX Pipeline Coordination

```
IFCtoBOMPipeline.run()
│
├── 1. ExtractionPopulator.populate()
│       → I_Element_Extraction in component_library.db
│
├── 2. ProductRegistrar.ensureProducts()
│       → M_Product in BOM DB
│
├── 3. CompositionBomBuilder.build()
│       → BUILDING BOM header (world LBD origin = allMinX/Y/Z)
│       → Computes: buildingMinX/Y/Z, per-storey floorMinX/Y/Z
│       → Returns: floorLbdOffsets map { storeyName → [dx,dy,dz] }
│
├── 4. FloorRoomBomBuilder.build(bomConn, config, floorLbdOffsets)  ← NEW PARAM
│       → FLOOR BOM headers
│       → FLOOR→SPACE lines (dx=0 initially — placeholder)
│       → BUILDING→FLOOR lines (dx = floorMinX - buildingMinX)  ← FIXED
│
├── 5. ScopeBomBuilder.build(bomConn, config, storeyElements, floorLbdMap)  ← NEW PARAM
│       │
│       ├── For each space with scope box:
│       │   ├── Assign elements by centroid containment (unchanged)
│       │   ├── Compute SET AABB: setMinX/Y/Z from assigned elements
│       │   ├── Create SET BOM header
│       │   ├── Insert LEAF lines: dx = e.minX() - setMinX  ← FIXED
│       │   └── UPDATE parent FLOOR→SPACE line:
│       │       dx = setMinX - floorMinX  ← NEW POST-HOC FIX
│       │
│       └── Returns: ScopeResult (unchanged interface)
│
├── 6. StructuralBomBuilder.build()  (already correct — minX-fMinX)
│
├── 7. DisciplineBomBuilder.build()  (already correct — minX-fMinX)
│       └── VerbDetector.detectCluster(): minX-gMinX  ← FIXED
│
├── 8. BomValidator.validate()
│       ├── W-TACK-1: 0 overshoots → PASS (promoted from WARN)  ← GATE
│       └── W-BUFFER-1: advisory (unchanged)
│
└── 9. IntegrityHash.compute()
```

---

## 6. State Machine — W-TACK-1 Witness Lifecycle

```
                    ┌───────────────┐
                    │  NOT CHECKED  │
                    └───────┬───────┘
                            │ BomValidator runs
                            ▼
            ┌───────────────────────────────┐
            │  COUNT overshoots             │
            │  (dx + width > parent * 1.01) │
            └───────────────┬───────────────┘
                            │
                   ┌────────┴────────┐
                   │                 │
            overshoots == 0    overshoots > 0
                   │                 │
                   ▼                 ▼
              ┌────────┐      ┌────────────┐
              │  PASS  │      │  FAIL      │  ← promoted from WARN
              └────────┘      │ (pipeline  │
                              │  aborts)   │
                              └────────────┘

Before TACK-FIX:  overshoots > 0 → WARN (advisory, non-gating)
After  TACK-FIX:  overshoots > 0 → FAIL (pipeline aborts, no BOM committed)
```

---

*References:
[BOMBasedCompilation.md](BOMBasedCompilation.md) §4 (tack convention) |
[DATA_MODEL.md](DATA_MODEL.md) §1.2 (tack convention) |
[TestArchitecture.md](TestArchitecture.md) (gate structure) |
[ACTION_ROADMAP.md](ACTION_ROADMAP.md) Pre-Code Specs T-1..T-4*
