# Finish Walls SRS

**Version:** 1.0 (2026-04-03)
**Status:** SPEC — no code yet
**Parent spec:** [BIM_Designer_SRS.md §28.17](BIM_Designer_SRS.md) (Completion Tools)
**Type system:** [SPATIAL_VARIANCE_SRS.md §3](SPATIAL_VARIANCE_SRS.md) (Type Resolution Ladder)
**Depends on:** [BOMBasedCompilation.md](BOMBasedCompilation.md) §4 (ARC discipline),
[DISC_VALIDATION_DB_SRS.md §6.12.3](DISC_VALIDATION_DB_SRS.md) (MEP anchor tables / spatial context)

---

## 1. Problem Statement

Many IFC files in the wild are **discipline-only**: a mechanical engineer delivers an MEP
model with no architectural envelope, or a structural engineer delivers frame-only with no
partition walls. The BIM compiler extracts and compiles what exists, but the resulting
output.db has zero (or below-threshold) IfcWall elements for one or more storeys.

This is not an error — it is a known incompleteness. The compiler can detect it, and the
Type-Safe Designer can resolve it without the user leaving the BOM workflow.

**Opportunity:** A half-finished floor is the clearest case for the Type Resolution Ladder.
IfcWall is ARC discipline — fully typed, library-backed, spatially constrained by IfcSpace
AABB. "Finish Walls" is therefore a Level 2 (standard library product) or Level 4
(BOM-defined choice) resolution — not a variance. The compiler can place correct walls
without any Level 5 relaxation in the common case.

---

## 2. Discipline Qualification

Walls belong to ARC and STR disciplines. Both are fully typed in the existing system:

| Discipline | IFC class | Library products | Verbs available |
|-----------|-----------|-----------------|----------------|
| ARC | IfcWall, IfcWallStandardCase | component_library.db — all wall types by ProductCategory | TILE, CLUSTER |
| STR | IfcWall (structural), IfcMember | component_library.db — structural members | TILE |

Because these types exist in the library and have defined verbs, "Finish Walls" is a
**typed compilation operation**, not free-form geometry generation. The PRIME RULE holds:
walls are extracted from library products, not invented.

---

## 3. Detection — CompletionAuditService

At pipeline close, for each compiled building, run:

```
CompletionAuditService.audit(buildingId, "IfcWall")

For each storey in spatial_structure:
  actual   = COUNT(*) FROM elements_meta
             WHERE building_id=B AND storey=S AND ifc_class='IfcWall'
  expected = MIN_COUNT from ad_completion_rule
             WHERE product_category=B.product_category
             AND ifc_class='IfcWall'
  IF actual < expected.min_count:
    emit COMPLETION_OPPORTUNITY(building_id, storey, "IfcWall", deficit=expected-actual)
```

`COMPLETION_OPPORTUNITY` is surfaced in:
- Designer panel: storey card shows orange badge "N walls missing"
- Log output: `[COMPLETION] Building X, Storey Y: 0/4 IfcWall (deficit=4)`
- (future) BIMEyes advisory panel

**Schema prerequisite:** `ad_completion_rule` table — does not exist yet. Migration needed.

```sql
CREATE TABLE IF NOT EXISTS ad_completion_rule (
    rule_id           INTEGER PRIMARY KEY AUTOINCREMENT,
    product_category  TEXT NOT NULL,   -- RESIDENTIAL|COMMERCIAL|HOSPITAL|INDUSTRIAL
    ifc_class         TEXT NOT NULL,   -- IfcWall
    min_count         INTEGER NOT NULL,
    standard_ref      TEXT,            -- 'UBBL', 'HTM', 'local building code'
    created_at        TEXT DEFAULT (datetime('now'))
);

-- Seed from BIM_Designer_SRS.md §28.17.4
INSERT INTO ad_completion_rule VALUES
  (NULL, 'RESIDENTIAL', 'IfcWall', 4,  'local building code', datetime('now')),
  (NULL, 'COMMERCIAL',  'IfcWall', 4,  'UBBL',                datetime('now')),
  (NULL, 'HOSPITAL',    'IfcWall', 6,  'HTM',                 datetime('now')),
  (NULL, 'INDUSTRIAL',  'IfcWall', 2,  'factory code',        datetime('now'));
```

---

## 4. GUI Interactions — Two Entry Points

### 4.1 Click on Empty Edge (Viewport)

The IfcSpace bbox is visible as a WF-R1 wireframe in the Designer viewport (even when the
space contains no compiled walls — the spatial structure provides the bbox). The user clicks
on the edge of an empty space:

```
User clicks on IfcSpace boundary edge at storey=Ground Floor
  → system detects: ifc_class='IfcWall' missing at this face of this IfcSpace
  → Type Resolution Ladder:
      L1: grid snap — is the edge aligned with structural grid? YES for rectangular plans
      L2: library lookup — product_category=RESIDENTIAL → default wall BRICK_200
      → resolved: COMPLETE(IfcWall, storey=Ground_Floor, product=BRICK_200, face=NORTH)
  → W_BOM_Variance (resolution_level=1 or 2, gesture_type=ADD)
  → Compiler places wall along that IfcSpace face
  → Viewport: wall appears as WF-R1 (DR) overlay
```

**Single face vs. all faces:** Click on one edge → one wall face. Panel button "FINISH ALL
WALLS" → all deficit faces for that storey in one batch gesture (SERIES type).

### 4.2 Panel "FINISH WALLS" Button (YAML/Order Mode)

In the storey browser panel, each storey with deficit shows:

```
┌─────────────────────────────────┐
│ Ground Floor          [3 walls] │
│ ⚠ 4 IfcWall missing             │
│ Default: BRICK_200              │
│ [▼ Select wall type]  [FINISH WALLS] │
└─────────────────────────────────┘
```

Pressing FINISH WALLS:
```
product_id = selected wall type (default: ProductCategory default from ad_completion_rule)
  → for each missing face in this storey's IfcSpaces:
      COMPLETE(IfcWall, storey, product_id, face)
      → W_BOM_Variance batch (SERIES, resolution_level=2)
  → Compiler re-runs → all faces filled
  → Panel updates: "4/4 IfcWall — pending approval"
```

The panel mode is preferred for YAML/Order users (less spatial, more configuration-driven).
It enters the Type Resolution Ladder at Level 2 (standard product selection) by design.

---

## 5. COMPLETE Verb — Spatial Algorithm

**COMPLETE(ifc_class, storey, product_id, face?)**

Uses Stage P placement from unified_mathematical_formulation.txt:

```
T(wall) = T(p(space)) + R(θ) · Δ(face)
```

Where:
- `T(p(space))` = IfcSpace centroid from `element_transforms` (or AABB centre from `elements_rtree`)
- `R(θ)` = rotation for wall orientation: N=0°, E=90°, S=180°, W=270°
- `Δ(face)` = offset from space centre to face: (AABB.maxX - AABB.minX)/2 for E/W faces

**Wall dimensions** come from `M_Product` in component_library.db — no parametric generation.
The wall length = IfcSpace face length (from AABB). The wall thickness = product spec.

**Prerequisite:** IfcSpace AABB must be in `elements_rtree`. Currently missing (only IfcWall
has AABB — see §7 blockers). `placement_extractor.py` must be extended.

**Output:** New rows in `element_instances` and `element_transforms` in output.db, linked to
the `W_BOM_Variance` that triggered them. Also a new `C_OrderLine` row in erp.db (when promoted).

---

## 6. Type Resolution — Where Finish Walls Sits

Finish Walls is almost always resolved at **Level 1 or Level 2** of the Type Resolution Ladder:

| Case | Level | Resolution |
|------|-------|-----------|
| Rectangular plan, wall aligned to structural grid | L1: GRID SNAP | Wall placed exactly on grid line. No variance. |
| Standard residential wall type in library | L2: STANDARD SIZE | `BRICK_200` from library. PRODUCT_SWAP implicit. No variance. |
| Non-standard wall type requested | L4: BOM CHOICE | Panel dropdown shows available wall products for this ProductCategory |
| Non-rectangular space, angled wall | L5: VARIANCE | `W_BOM_Variance` created. User must confirm angled placement. |

The vast majority of buildings with missing walls have rectangular plans (grid-aligned spaces).
Finish Walls will resolve at L1-L2 for these — no variance card, no acknowledgment required,
immediate compilation.

---

## 7. Blockers and Prerequisites

| Blocker | Required by | Mitigation |
|---------|------------|-----------|
| `ad_completion_rule` table missing | Detection (§3) | New migration — 30 min |
| IfcSpace AABB not in `elements_rtree` | COMPLETE verb spatial algorithm (§5) | Extend `placement_extractor.py` |
| IfcSpace not in `element_transforms` | T(p(space)) in placement formula | Same extraction extension |
| `W_BOM_Variance` table missing | All Type-Safe Design operations | Migration from SPATIAL_VARIANCE_SRS.md §5.2 |

**Can build without IfcSpace AABB:** Detection (§3) and panel UI (§4.2) can be built using
only `spatial_structure` (which does have IfcSpace entries). The COMPLETE verb placement
algorithm requires AABB — defer that to after extraction is extended.

---

## 8. Round-Trip Significance

Finish Walls is the canonical case for the Compiler-Modeller round trip:

```
Discipline-only IFC (MEP only, no walls)
  → extract → compile → output.db (walls=0)
  → CompletionAuditService detects deficit
  → User clicks empty edge OR clicks "FINISH WALLS"
  → COMPLETE verb → new walls in output.db
  → Project to 2D → floor plan now shows walls
  → G1-G6 on new output → COUNT increases by deficit, VOLUME correct
```

This is also the primary use case for the **2D23D import path**: 2D23D produces walls from
DXF floor plan lines. If those walls are missing from the source IFC (discipline-only), Finish
Walls can fill the gap using the compiler's library — then the round trip closes.

---

## 9. Witnesses

| ID | Claim | Verify |
|----|-------|--------|
| W-FW-DETECT | CompletionAuditService returns deficit=4 for a known wall-less storey | Unit test against TE extracted.db (discipline-only building) |
| W-FW-PANEL | "FINISH WALLS" button creates W_BOM_Variance batch for all deficit faces | Check W_BOM_Variance rows count = deficit |
| W-FW-COMPILE | After COMPLETE verb, IfcWall count at storey increases by deficit | G1-COUNT delta check |
| W-FW-AABB | New wall AABB lies within IfcSpace AABB boundary | `elements_rtree` containment check |
| W-FW-2D | Floor plan SVG shows new walls after COMPLETE (solid lines, correct length) | Visual + SVG path length measurement |
| W-FW-ROUNDTRIP | TE discipline-only → Finish Walls → compile → 2D floor plan matches expected layout | Manual overlay against TE architectural drawings |

---

## 10. Implementation Roadmap

Maps to SPATIAL_VARIANCE_SRS.md §16 stages:

| Stage | Task | Prompt stub | Effort |
|-------|------|-------------|--------|
| FW-0 | `ad_completion_rule` migration + seed data | `Edit_002` | 30 min |
| FW-1 | `CompletionAuditService` — detection only, read-only | `Edit_002` | 1 session |
| FW-2 | Panel storey card: show deficit badge + "FINISH WALLS" button | `Edit_003` | 1 session |
| FW-3 | IfcSpace AABB extraction (`placement_extractor.py`) | `Edit_004` | 1 session |
| FW-4 | COMPLETE verb spatial algorithm + output.db write | `Edit_005` | 1-2 sessions |
| FW-5 | 2D floor plan update after COMPLETE | `2D_004` | Included in Java DrawingWriter port |

**FW-0 and FW-1 can be built today** — no IfcSpace AABB required, no gesture capture required.
They use only `elements_meta` (existing), `spatial_structure` (existing), and the new
`ad_completion_rule` table.

---

---

## 11. Reusability — Finish Walls as the Anchor Pattern

Finish Walls is the first instance of a general **COMPLETE(ifc_class, storey, product_id)**
pattern. The same infrastructure — detection, Type Resolution Ladder, W_BOM_Variance,
COMPLETE verb, 2D projection update — applies directly to any missing element class.

The prerequisites are identical for all cases:
- `ad_completion_rule` with `ifc_class` column (already generalised — not wall-specific)
- `CompletionAuditService.audit(buildingId, ifcClass)` — already parameterised on ifcClass
- `CompleteVerbService.complete(...)` — parameterised on ifc_class and product_id
- IfcSpace / IfcBuildingStorey AABB (once extracted, available for all operations)
- `W_BOM_Variance` — type-agnostic schema, works for any resolved_type

**Derived operations enabled by the same infrastructure:**

| Operation | ifc_class | Trigger | Notes |
|-----------|-----------|---------|-------|
| Finish Walls | IfcWall | Missing envelope on MEP-only floors | **This document — implement first** |
| Add Floor Slab | IfcSlab | Missing slab between storeys | Same TILE verb, XY plane instead of XZ |
| Add Columns | IfcColumn | Missing at grid intersections | L1 grid snap → trivial Level 1 resolution |
| Add Ceiling | IfcCovering | Missing ceiling finish | SPRAY verb along slab underside |
| Add Roof | IfcRoof | Missing roof on top storey | Geometry Forge roof engine (GEOMETRY_FORGE_SRS.md) |
| Add Stair | IfcStair | Missing vertical connection | Geometry Forge stair engine |
| Add MEP | IfcPipe/IfcDuct | Missing services after ARC complete | ROUTE WALKER (existing) |
| Add Railing | IfcRailing | Missing balustrade on open edge | TILE along open floor slab edge |

**Architectural principle:** Each operation is a different `ifc_class` value and a different
default product from `component_library.db`. The framework is identical. Build Finish Walls
correctly and the rest are configuration + minor spatial logic variations.

**ADD FLOOR (IfcSlab) specifics:**
- Detection: `ad_completion_rule` needs MIN_COUNT=1 for IfcSlab per storey
- Placement: AABB = IfcBuildingStorey AABB at Z=storey_FFL, full XY extent
- Product: floor slab from library (concrete slab, raised floor, etc. by ProductCategory)
- This enables a true "add storey" workflow: clone BOM + add floor slab + add ceiling above

**ADD ELEMENTS (general):**
The panel "Add Elements" button (future) is a generalised UI for `CompletionAuditService`
output. It lists all COMPLETION_OPPORTUNITY records for the building, groups by ifc_class,
and offers a "COMPLETE ALL" for each group. Each resolution goes through the same ladder.

**Spec extensions (write these when implementing each):**
- `ADD_FLOOR_SRS.md` — IfcSlab placement per storey
- `ADD_COLUMNS_SRS.md` — grid intersection detection + IfcColumn placement
- These extend this document's patterns, not replace them.

---

## 11. Reference Case: Hospital (HO_) — DECLARE ROOMS First

**See:** `docs/HospitalAnalysis.md §Engineering Reading` for full context.

Hospital has 1,468 IfcWall elements across 7 levels but **zero IfcSpace records**.
FINISH_WALLS cannot run: `§5 COMPLETE verb` needs IfcSpace AABB as spatial input.
This is the canonical case that exposes the **DECLARE ROOMS prerequisite**.

### 11.1 What DECLARE ROOMS Means

DECLARE ROOMS ≠ adding missing walls. It is a prior step: inferring the enclosed
air volumes from existing wall geometry and registering them as synthetic IfcSpace
records so the rest of the pipeline (FINISH_WALLS, room-level BOM, 2D floor plan
rooms) can proceed.

```
Hospital walls exist (1,468 IfcWall) — but no IfcSpace
  → DECLARE ROOMS: infer bounding zone from wall topology per storey
      For each storey:
        1. Collect IfcWall AABBs on this level (from elements_rtree)
        2. Run wall-segment graph → find enclosed polygons (rooms)
        3. For each polygon: compute AABB, assign name ("Room_L1_001")
        4. INSERT INTO spatial_structure (guid=synthetic, type='IfcSpace', ...)
        5. INSERT OR IGNORE INTO elements_rtree (id, minX..maxZ) for each space
  → Now FINISH_WALLS can run: IfcSpace AABB is available
  → CompletionAuditService detects wall deficits per declared room
  → COMPLETE verb places walls along room faces
```

### 11.2 Why Hospital Is the Right First Case

- Large, real clinical building — not a toy example
- 7 floors of rectangular ward plans → wall topology is tractable (grid-aligned walls,
  mostly orthogonal — Level 1-L2 resolution in the Type Resolution Ladder)
- IfcWall count (1,468) is sufficient to infer room boundaries without IfcSpace
- Once declared, Hospital becomes the test case for the full round-trip:
  `DECLARE ROOMS → FINISH_WALLS → 2D floor plan → room-level BOM → ERP procurement`

### 11.3 Session Scope

One bounded session:
1. Read `HospitalAnalysis.md` + this section
2. Implement `DeclareRoomsService.infer(buildingId, storeyId)` — wall topology → IfcSpace AABB
3. Write witness: W-FW-DECLARE (count of declared rooms per storey > 0, AABB non-zero)
4. Verify: FINISH_WALLS detection (§3) now finds deficit count > 0 for Hospital

**Do NOT implement the COMPLETE verb placement in the same session.**
Declare first, prove counts, then a separate session for wall placement.

---

*Cross-references:
[BIM_Designer_SRS.md §28.17](BIM_Designer_SRS.md) (parent spec — §28.17.1-28.17.7) |
[SPATIAL_VARIANCE_SRS.md §3](SPATIAL_VARIANCE_SRS.md) (Type Resolution Ladder) |
[SPATIAL_VARIANCE_SRS.md §6](SPATIAL_VARIANCE_SRS.md) (Variance Stack) |
[2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md §12](../2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md) (floor plan projection) |
[DISC_VALIDATION_DB_SRS.md §6.12.3](DISC_VALIDATION_DB_SRS.md) (discipline isolation) |
[TestArchitecture.md](TestArchitecture.md) (G1-G6 gates)*
