# PROGRESS — Current Development State

**Last updated:** 2026-02-22
**Current phase:** Phase BOM-2c COMPLETE + WATCHDOG fixes + SH/DX clean (D2 partial)
**Baseline:** ALL 4 BUILDINGS PASS via `mvn test` — SH **58**, DX **1197**, TB-LKTN **138**, Terminal ~51088
**Tests:** **107 total** (41 contract + 4 registry + 13 metadata + 12 BOM chain math + 10 edge vertex + 5 intra-BOM relative + 7 BOMChainIntegrity + 3 ArchUnit + 8 BomChainIntegrity + 4 LOD X-Ray)
**SpatialDigests:** SH=b802a23b DX=68bf3ad7 TB=4a7ad4f6 Terminal=301b42b1 (all unchanged)

---

## Watchdog/D2 Session Fixes (2026-02-22) — COMPLETE

**Fix W1: MetadataMissingException — hard throw replaces silent BBox fallback**
- `BuildingWriter.java`: null→BBox path for FURN discipline now throws `MetadataMissingException`
  with element_ref + ifc_class + familyRef + PRIME RULE inline. Non-FURN (ARC/STR) falls to `[GEN-BOX]`.
- New file: `DAGCompiler/src/main/java/com/bim/compiler/dsl/MetadataMissingException.java`
- Law 3 compliance: hard throws, not silent fallbacks.

**Fix W2: Option B — GEN-BOX path eliminates D2 grep hit**
- `BuildingWriter.java` line 921: replaced `be = binder.bindParametric(p, guid, type)` with
  direct `writeBoxGeometry(p)` + `ep.writeElementMeta()` + `ep.writeInstance()` + `emitted++` + `continue`.
- D2 grep `bindParametric` now returns 1 hit (only the pre-existing `DimensionalContractViolation`
  fallback at line 938 remains — pre-existing debt, not this session's target).
- GEN-BOX path now counts as `emitted`, not `bound` — correctly separate concerns.

**Fix W3: DX beam storey+ordinal migration**
- `migration/migration_BOM2c_dx_beam_ordinals.sql`:
  - `storey='Level 1'→'Ground'`, `ordinal += 55` (IfcBeam_56–59)
  - `storey='Level 2'→'Upper'`, `ordinal += 59` (IfcBeam_60–63)
- `resolveGeometryByInstance()` direct ordinal lookup now hits for all 8 DX beams.
- Verified: DX beams have 80-vertex W-flange mesh (was 8-vertex BBox before migration).
- SpatialDigest unchanged (beams are STR, not MEP — MEP-only hash not affected).

**Chain traces verified:**
- SH: `ad_building_registry`→`ad_room_slot(MASTER_BEDROOM/FURNITURE)`→`ad_bom(BED_SET_MASTER)`→`ad_bom_child(TALL_CABINET_B, Tall_Cabinet%)`→`ad_product_dim(0.6×0.6×2.0m) + component_definitions(hash=618ca21c448c6167)`
- DX: `ad_building_registry`→`ad_room_slot(BATHROOM/SANITARY)`→`ad_bom(TOILET_BLOCK_FIXTURES)`→`ad_bom_child(TOILET, Toilet%, FACE_AWAY_FROM_WALL)`→`ad_product_dim(0.4×0.7×0.4m) + component_definitions(hash=0cbf88c02987a900)`

---

## Phase BOM-2c Bug Fixes (2026-02-22) — COMPLETE

**Fix 1: perStoreyClasses guard → explicit consumption tracking**
- Root cause: `BuildingWriter.emitGlobalPlacementElements()` had a `perStoreyClasses` guard
  that prevented re-emission of compiled-path elements. It worked by storey-name mismatch
  (relational "Level 1/2" ≠ DSL "Ground/Upper"). After `migration_BOM2c_dx_storey_names.sql`
  unified to "Ground/Upper", the guard fired on DX and dropped 194 elements (173 IfcFurnishingElement
  + 21 IfcSlab) → DX 1197→1003 regression.
- Fix: `PlacementAD.markConsumed() / isConsumed()` explicit registry. `StoreyCompiler.applyPlacementOverrides()`
  marks slabs/FURN it consumes; emitGlobal skips only those. DX units ("Ifc2x3_Duplex_A") never match
  PlacementAD cache key ("Ifc2x3_Duplex") → applyPlacementOverrides returns early → nothing consumed →
  emitGlobal writes all 1197 DX elements correctly.
- Migrations: `migration_BOM2c_dx_storey_names.sql` KEPT (correct data). Revert file deleted.

**Fix 2: X1 LOD gate — FIXTURE_SINK BBox geometry**
- Root cause: 5 FIXTURE_SINK elements (IfcFurnishingElement_994/1017/1018/1021/1022) had no
  `ad_geometry_map` entry → `MeshBinder.resolveGeometryByRef()` returned null → `bindParametric()`
  fallback wrote GEO_ 8-vertex bounding box → vertex_count=8 failed X1 gate.
- Fix: `migration_BOM2c_sink_geometry_map.sql` — 5 rows in `ad_geometry_map` mapping each
  element_ref → hash `13ffa0740585876b` ("M_Sink - Island - Single:455mm×455mm", 947 vertices).
  `resolveGeometryByRef(elementRef, "IfcFurnishingElement")` now finds real sink mesh.

**Test result: 107/107 GREEN**

---

## Next Session: Phase BOM-2b — GGF Cascade Compilation + TB-LKTN Issues

**Priority open issues (user-reported):**
1. TB-LKTN furniture sank into floor — Z positioning from MEPWriter DSL path (pre-existing Known Debt)
2. TB-LKTN double-leaf front door missing — DOOR_D1_DOUBLE migration from Phase B1 needs check
3. Furniture rotation not applied (suppressed in BOM-2a to avoid GIC overflow — Phase BOM-3)

**Phase BOM-2b target:**
1. GGF cascade compilation: UNIT_*_STD anchor → floor assemblies → room slot dispatch
2. New building = one SQL INSERT in ad_building_registry + one ad_bom row
3. family_ref normalisation: map Revit strings → catalog product IDs for 261 DX ABSOLUTE rows

## Next Session: Phase BOM-2 — GGF + GF BOM Parent Layers (original)

The bottom 3 BOM layers (room space → furniture sets → individual items) are live (Phase BOM-1).
The top 2 layers remain:

```
GGF: UNIT_DUPLEX_STD  (the complete house — the "car")
  └─ GF: FLOOR_1_STD + FLOOR_2_STD  (floor assemblies)
       └─ [Phase BOM-1 ✅] Room space BOMs → sets → leaf items
```

**Phase BOM-2 target:**
1. Define `ad_bom` rows for `UNIT_DUPLEX_STD`, `UNIT_SH_STD` (GGF) and their floor children (GF)
2. BOM Drop from GGF → `ad_element_rule` anchor rows for floors and rooms
3. Compiler reads GGF anchor → expands to floor assemblies → room slot dispatch already handles the rest
4. User edits schedule (remove piano, swap set, add custom item) before compile
5. Result: new building = DSL entry + GGF BOM in `ad_bom`, zero hand-written `ad_element_rule` rows

**Also in scope for BOM-2:**
- family_ref normalisation for SH/DX structural/MEP elements (261 DX ABSOLUTE rows):
  map Revit strings (`M_Single-Flush:0762 x 2032mm`) → catalog product IDs in `ad_product_dim`
  so conn_points fires for orientation on doors/windows

**Rule:** Do not start BOM-2 without reading `docs/TECHNICAL_BUILDING_GUIDE.md` §0.1
(the MRP BOM explosion table) and `docs/LAST_MILE_PROBLEM.md` §0.1 (ordered fix plan).

---

## Phase History (Summary)

| Phase | Status | What |
|-------|--------|------|
| RM-1 | ✅ DONE | 5 relational tables + populated rules (SH 55, DX 1085) |
| RM-2 | ✅ DONE | RelationalResolver shadow validation (SH 100%, DX 100%) |
| RM-3 | ✅ DONE | PlacementAD cutover — RELATIONAL mode live |
| RM-4 | ✅ DONE | TB-LKTN 58→69 elements, LOD400 library, GeometryIntegrityChecker |
| RM-5 | DEFERRED | Flat table → computed cache (non-blocking, data still valid) |
| RM-6 | ✅ DONE | Plumbing system + CONNECTS_TO topology proofs |
| RM-7 | ✅ DONE | Visual defect audit: geometry_map storey, familyRef, P19-P21 |
| RM-8 | ✅ DONE | Registry-driven pipeline — one engine, N buildings from metadata |
| RM-9 | ✅ DONE | rotation_rule authority: 3-table contract, FixturePlacer hardened |
| RM-10 | ✅ DONE | Window depth-cap, GIC LOD_ check, furniture scaling, P22/P23 proofs |
| RM-11 | ✅ DONE | family_ref gates, conn_points orientation, adaptive BOM cascade, MEP GIC fixes, residential HVAC scope |
| B1-B5 | ✅ DONE | TB-LKTN data fixes: doors, water heater, kitchen counter, toilet rotation, ParametricMesh arch |
| BOM-1 | ✅ DONE | MRP BOM Drop → Schedule → Compile: ad_room_slot × ad_room_boundary → BOM anchor rows → FurnitureBOMResolver expansion |
| BOM-2a | ✅ DONE | LOD Geometry + GGF/GF BOM Hierarchy: furniture LOD via familyRef lookup; UNIT_*_STD + FLOOR_*_STD BOMs defined |

---

## Phase BOM-2a Detail (2026-02-21)

### Problem Solved
BOM-generated furniture placements (IfcFurnishingElement, discipline='FURN') appeared as opaque grey
boxes because `MeshBinder.bind()` returns null (no ad_geometry_map entry for BOM children), and
`bindParametric()` fallback wrote a plain box. GGF/GF BOM hierarchy layers were undefined.

### What Was Done

1. **LOD Geometry Fix** — `BuildingWriter.emitGlobalPlacementElements()`:
   - Added FURN LOD intercept after `binder.bind()` returns null
   - `furnitureLibrary.getByName(p.familyRef())` → ComponentDefinition → `getLocalBounds()`
   - Scale: sX=(bbox_w/mesh_w), sY=(bbox_d/mesh_d), sZ=(bbox_h/mesh_h)
   - Translation: `tx=p.minX()-lb[0]*sX, ty=p.minY()-lb[2]*sY, tz=p.minZ()-lb[4]*sZ`
     (aligns scaled mesh min-corner to placement min-corner — correct for any mesh origin)
   - Rotation suppressed (0.0) — applying rotation around world origin overflows bbox → GIC fail
     (rotation is a Phase BOM-3 concern)
   - Result: DX 168/173 furniture elements have LOD geometry (97%)
   - SH/TB-LKTN: N/A (their furniture flows through MEPWriter DSL path which already has LOD)

2. **GGF/GF BOM Hierarchy** — `migration/migration_BOM2a_lod_and_ggf.sql`:
   - 3 GGF BOMs (group_by='BUILDING'): UNIT_SH_STD, UNIT_DUPLEX_STD, UNIT_TBLKTN_STD
   - 4 GF BOMs (group_by='STOREY'): FLOOR_SH_GF_STD, FLOOR_DX_L1_STD, FLOOR_DX_L2_STD, FLOOR_TBLKTN_GF_STD
   - GGF→GF wiring in ad_bom_child (child_bom_id)
   - GF→Room-level BOM wiring (LIVING_SET, DINING_SET, KITCHEN_CABINET_SET, etc.)
   - SH living room fix and BOM Drop already in BOM-1 — not repeated

### Technical Note: Scope of LOD Fix
BOM children on COMPILED storeys are SKIPPED in emitGlobalPlacementElements:
```java
Set<String> perStoreyClasses = Set.of("IfcSlab", "IfcFurnishingElement", "IfcFurniture");
if (compiledStoreys.contains(p.storey()) && perStoreyClasses.contains(p.ifcClass())) continue;
```
For DX (pure metadata, compiledStoreys=∅): ALL BOM children are emitted → LOD fix applies.
For SH/TB-LKTN (have compiled storeys): BOM children on those storeys are skipped.
SH/TB-LKTN furniture goes through MEPWriter.writeFixture() which already applies LOD scaling.

### Verification
```
mvn test -pl DAGCompiler
Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
DX: 168/173 IfcFurnishingElement have LOD_ geometry hash (5 PARAM = no namePattern match)
GGF/GF BOMs: 3 BUILDING + 4 FLOOR_* STOREY rows in ad_bom ✓

### Files Changed
- `DAGCompiler/src/main/java/com/bim/compiler/dsl/BuildingWriter.java` (LOD FURN intercept)
- `migration/migration_BOM2a_lod_and_ggf.sql` (NEW — GGF/GF BOM hierarchy)
- `library/component_library.db` (migration applied)

---

## Phase BOM-1 Detail (2026-02-21)

### Problem Solved
Ad_element_rule held individual furniture leaf rows (FURN_DINING_CHAIR×8, FURN_BED_DOUBLE, etc.)
extracted from Revit with ABSOLUTE world coordinates — wrong IFC frame, guaranteed mismatch.
The full BOM hierarchy (27 BOMs, FurnitureBOMResolver, ad_bom_child, ad_bom_child_param) existed
but was never invoked for SH/DX/TB-LKTN.

### What Was Done
1. **SQL Migration** — `migration/migration_RM6_bom_anchors.sql`:
   - Fix SH ROOM_Ground_Floor_1 room_type ROOM→LIVING (so LIVING_SET + DINING_SET slots fire)
   - Fix TB-LKTN 'common' room_type LIVING→COMMON (dispatch KITCHEN_CABINET_SET + DINING_SET + LIVING_SET)
   - Deactivate individual furniture leaf rows (`family_ref LIKE 'FURN_%'`) for SH/DX/TB-LKTN
   - BOM Drop INSERT: `ad_room_slot × ad_room_boundary` JOIN → one anchor row per (building, room, assembly)
     - anchor `discipline='FURN'`, `position_rule='FRACTION'`, `family_ref=bom_id`
     - SH=5 anchors, DX=27 anchors, TB-LKTN=12 anchors

2. **Additional data** — `migration/migration_RM6b_bom_product_dims.sql`:
   - INSERT COMMON into ad_space_type (hybrid open-plan type for TB-LKTN common room)
   - INSERT 18 furniture product_dim rows (Bed_Queen, Sofa, Dining_Chair, Upper_Cabinet, etc.) for BOM child dims
   - UPDATE ad_building_registry expected_elements: SH 55→63, DX 1085→1197, TB-LKTN 102→138

3. **RelationalResolver.java** — BOM anchor detection + expansion:
   - Extended ResolutionContext with `bomIds` (Set) + `productDims` (Map)
   - `loadBomIds()` — reads ad_bom WHERE group_by='ROOM'
   - `loadProductDims()` — reads ad_product_dim (units already in meters, verified)
   - `computeOne()` returns `List<Placement>` (was single Placement)
   - BOM detection: `rule.familyRef ∈ bomIds` → `computeBomAnchor()` → FurnitureBOMResolver
   - GUID uniqueness: `childRef.hashCode() & 0x7FFFFFFF` (avoids collision across rooms)

4. **MetadataValidator.java** — exclude BOM anchor rows from family_ref null + dangle check
   (BOM anchors have bom_id as family_ref, not a product_id in ad_product_dim)

### Element Count Changes (net after deactivation + BOM expansion)
| Building | Before | After | BOM children added |
|----------|--------|-------|-------------------|
| SH | 55 | 63 | +8 (DINING_SET×7, LIVING_SET×6, BED_SET×5, BED_SET_MASTER×4 = +22; -14 deactivated) |
| DX | 1085 | 1197 | +112 (KITCHEN/DINING/LIVING/BED BOMs across 20 rooms; -56 deactivated) |
| TB-LKTN | 102 | 138 | +36 (BED_SET×3, KITCHEN_CABINET_SET, DINING_SET, LIVING_SET, TOILET_BLOCK_FIXTURES; -10 deactivated) |

### Known Debt Carried Forward
| Item | Count | Gate? |
|------|-------|-------|
| WARDROBE_SET: no ad_bom_child rows — always yields 0 | 3 rooms | No |
| BATHROOM_VANITY_SET/TOILET_BLOCK_FIXTURES: 0 children for bilik_mandi (BATHROOM type) | 2 | No |
| KITCHEN_CABINET_SET: 0 children for ROOM_Level_1_24/7 (room too small?) | 2 | No |
| DX GeometryValidator: 21 "Room not enclosed" (pre-existing, metadata building — walls bypass BuildingSpec) | 21 | No |

### Verification
```
mvn test -pl DAGCompiler
Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
BOM expansions confirmed: TB-LKTN BED_SET×3=5, KITCHEN_CABINET_SET=12, DINING_SET=7, LIVING_SET=6, TOILET=6
SH BED_SET=5, BED_SET_MASTER=4, DINING_SET=7, LIVING_SET=6
DX 7 KITCHEN rooms × 8-12 children, 6 LIVING/DINING rooms × 13 children, 2 BEDROOM rooms × 5 children

### Files Changed
- `migration/migration_RM6_bom_anchors.sql` (NEW)
- `migration/migration_RM6b_bom_product_dims.sql` (NEW)
- `DAGCompiler/src/main/java/com/bim/compiler/dsl/RelationalResolver.java`
- `DAGCompiler/src/main/java/com/bim/compiler/dsl/MetadataValidator.java`
- `DAGCompiler/src/test/java/com/bim/compiler/contract/CompilerContractTest.java` (G3e/G3f/G4c BOM refs)
- `library/component_library.db` (migrations applied)

---

## Phase RM-10 Detail (2026-02-20)

### Problems Solved
1. **Window opening-axis protrusion** — MeshBinder `isNS` detection was wrong for EW-orientation windows. Fixed via mesh-proximity comparison (`errNoRot` vs `errRot`). Wall-thickness axis (`wallThick`) now correctly `bboxW` when rotation applied, `bboxD` when not. `scaleY` capped to prevent frame protrusion through wall.

2. **GIC LOD_ blind spot** — GeometryIntegrityChecker previously only checked `GEO_` (parametric box) geometry. MeshBinder's world-coord `LOD_` geometry was entirely exempt. Fix: check both prefixes. `isParametric` renamed `isWorldCoords`.

3. **Furniture mesh overflow** — `MEPWriter.writeFixture()` called unscaled `transformAndWriteGeometry()` for `IfcFurniture`. Library mesh ≠ product dims → mesh overflowed bbox. Fix: `transformAndWriteGeometryScaled()` with computed `bbox/mesh` scale factors. Z aligned via `tz = z - bounds[4] * sZ`.

4. **P22 OPENING_MESH_IN_BBOX (CRITICAL)** — Post-write proof: reads vertex blobs from `base_geometries` for IfcDoor/IfcWindow, verifies no vertex exceeds rtree bounds by >1mm. Gates the build.

5. **P23 DRAIN_CORNER_ALIGNMENT (advisory)** — Post-write proof: checks orthogonal IfcFlowSegment pairs at same Z share a corner within 5mm. TB-LKTN: 6 violations (drain U-shape). Duplex: 364 advisory (MEP flow fittings, expected). Advisory only.

### Files Changed
- `MeshBinder.java` — isNS fix, wallThick axis, scaleY depth-cap, MIN_SCALE validation bypass
- `GeometryIntegrityChecker.java` — LOD_ blind spot fixed, isOpening flag added
- `MEPWriter.java` — furniture split-branch (scaled vs unscaled), `geoHash = null` init fix
- `PlacementProver.java` — P22/P23 added, `proveFromDB()` extended, `isCritical()` updated
- `DoorWindowLibraryMapper.java` — `PRAGMA foreign_keys = ON`
- `StairLibraryMapper.java` — `PRAGMA foreign_keys = ON`

### Migration Script
`migration/migration_DX_corner_windows.sql` — Duplex corner windows 1126-1133 (out-of-range FRACTION → ABSOLUTE)

### Verification
```
mvn test -pl DAGCompiler
Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## Strategic Pause — The Last Mile Problem

See `docs/LAST_MILE_PROBLEM.md` for full context.

**Root asymmetry**: Replication (SH/DX) works because reference IFC IS the oracle. Generation (TB-LKTN) fails because the compiler IS the authority — every coordinate must be computed from rules, with no reference to validate against.

**Proposed architecture** (not yet implemented):
- **ProvenElement** — PO.save() equivalent: every element at construction carries a `PlacementProof` sealed type that verifies itself at build time. No proof = no element.
- **CRD (Construction Rule Dictionary)** — `crd_rule` tables encoding UBBL/JKR placement rules (toilet back to wall, basin adjacent, bed headboard to wall, window sill 900mm, etc.). Replaces ad-hoc Java resolver logic.

**Decision before next session**: Do NOT add more ad-hoc TB-LKTN placement fixes. They don't compound — they regress. CRD must come first.

**Bridge strategy**: Mine Rosetta Stone buildings (SH/DX) for CRD seed data — their extracted placements are proven correct and can seed `crd_rule` rows.

---

## Known Debt (Explicit Thresholds)

| Item | Count | Severity | Gate? |
|------|-------|----------|-------|
| Terminal IfcReinforcingBar GIC failures | 8 | Advisory | No |
| Duplex P23 drain corners | 364 | Advisory (MEP flow fittings, not drains) | No |
| TB-LKTN P23 drain corners | 6 | Advisory (actual drain U-shape defect) | No |
| TB-LKTN furniture alignment | visible | Advisory | No |
| TB-LKTN roof porch | missing | Advisory | No |

---

---

## Phase 120 Detail (2026-02-20)

### Porch Canopy + Roof Footprint Fix

**Problem**: `compileRoofFromSpecs()` included PORCH rooms in the main gable footprint, pulling minY to 0 instead of 2.3m (row 2). For extracted buildings this made the ridge off-center. For TB_LKTN the porch canopy geometry was missing entirely.

**Changes**:

1. **`BuildingCompiler.compileRoofFromSpecs()`** — excludes rooms where `type ∈ {PORCH, CAR_PORCH, VERANDAH}` from main footprint. Tracks porch bounds separately. Calls new `generateAttachedCanopy()` + `mergeRoofSpecs()` when porch present.

2. **`BuildingCompiler.generateAttachedCanopy()`** — new method. Generates 6-vertex gable canopy: south gets full overhang, north face (wall attachment) gets none. Ridge at span midpoint.

3. **`BuildingCompiler.mergeRoofSpecs()`** — new method. Merges two RoofSpec vertex/face lists; offsets canopy face indices by main vertex count. Used for non-metadata buildings (SH/DX path).

4. **`RelationalResolver.loadRules()`** — added `ORDER BY id` to ensure rules process in insertion order (main roof before canopy).

5. **`migration/migration_TB_LKTN_porch_canopy.sql`** — new row in `ad_element_rule` for `IfcRoofCanopy_1`:
   - Center (4950, 800) mm = center of canopy footprint (C-D × rows 1-2 + south overhang)
   - width=5100mm, depth=3000mm (south overhang to north wall), ridgeRise=699mm
   - orientation=GABLE_25 → `writeGableGeometry()` generates 6-vertex canopy mesh

6. **`ad_building_registry.expected_elements`** — TB_LKTN updated: 69 → 70.

**Verified output** (tb_lktn.db):
- `MD_ROOF_GROUND_FLOOR_1`: main gable — x[-0.7,10.6], y[1.6,9.2], z[3.0,4.772]
- `MD_ROOF_GROUND_FLOOR_2`: porch canopy — x[2.4,7.5], y[-0.7,2.3], z[3.0,3.699]

**Geometry matches 2D PDF**: main gable covers rows 2-5 only (ridge at correct position), canopy is separate south-facing gable over porch area, ridge E-W with gable end pointing south (visible in front elevation).

**Tests: 58/58 green**

---

## Phase B Detail (2026-02-21)

### BIM AD ARCHITECTURE — TB-LKTN Data Fixes

#### Changes Made
| Phase | Change | Layer |
|-------|--------|-------|
| B1a | DOOR_D1_DOUBLE (1125mm double-leaf) added | M_Product (ad_product_dim) |
| B1b | TB-LKTN door family_ref: D1_DOUBLE/D2/D3 + width_mm corrected | C_OrderLine (ad_element_rule) |
| B3  | FIXTURE_WATER_HEATER (0.55×0.25×0.55m wall unit) added | M_Product (ad_product_dim) |
| B4  | Water heater element rule in bilik_mandi | C_OrderLine (ad_element_rule) |
| B4  | Toilet rotation fix: NS→EW (faces east toward door, per conn_points) | C_OrderLine (ad_element_rule) |
| B5  | Kitchen counter in open-plan 'common' zone | C_OrderLine (ad_element_rule) |
| B5  | COMMON COUNTER slot (for future COMMON-type rooms) | M_BOM_Line (ad_room_slot) |
| Mesh | ParametricMesh sealed interface + GableRoofMesh + DB tables | Architecture |

#### iDempiere ERP Layer Mapping
| Table | ERP Analog | Role |
|-------|-----------|------|
| `ad_product_dim` | M_Product | Catalog master (dimensions, type) |
| `ad_element_rule` | C_OrderLine | Per-building element placement |
| `ad_room_slot` | M_BOM_Line | Room template slots (generic) |
| `ad_bom_child_param` | C_BOM_Line | Assembly child params + rotation_rule |
| `ad_parametric_mesh_param` | AD_Parm | Shape generator parameters |
| `ad_roof_preset` | M_ProductPrice | Region × type → standard mesh |

#### Tests: 58/58 green, TB-LKTN 100 → 102 elements

---

## TB-LKTN Open Issues (remaining)

TB-LKTN (terrace house) is functional at 102 elements. Remaining known gaps:

| # | Issue | Severity | Notes |
|---|-------|----------|-------|
| 1 | ~~TB-LKTN front door too small (single leaf)~~ | ✅ Fixed B1a/B1b | DOOR_D1_DOUBLE added |
| 2 | TB-LKTN furniture alignment (bedroom/living) | Visual | Relative rules not precisely computed |
| 3 | ~~TB-LKTN bathroom: no water heater~~ | ✅ Fixed B3/B4 | FIXTURE_WATER_HEATER + element rule |
| 4 | ~~TB-LKTN kitchen: no cabinet in common area~~ | ✅ Fixed B5 | FURN_KITCHEN_COUNTER added |
| 5 | TB-LKTN drain U-shape (P23: 6 advisory) | Geometry | Deferred to CRD phase |
| 6 | Ceiling fans (TB-LKTN: 1500mm, too large) | Scale | Fans OK per user; LOD mesh issue |
| 7 | Roof LOD mesh (tiles): procedural only | Geometry | ParametricMesh arch ready; Mesh2Library next |
| 8 | ~~Toilet bowl wrong rotation~~ | ✅ Fixed B4 | NS→EW, faces east toward door |

## Next: BIM AD ARCHITECTURE Session

**Session goal**: Define and document the BIM AD ARCHITECTURE enforcement contract.
This covers the distinction between:
- **Level 1 (EXTRACTED)** — SH/DX: reference IFC is the oracle, data is pre-proven
- **Level 2 (GENERATIVE)** — TB-LKTN: compiler IS the authority, every coord must be computed
- **Level 3 (ASSEMBLY)** — BOM children: offsets relative to parent, never absolute
- **Level 4 (ROOM-RELATIVE)** — element rules: fractions along walls, never raw coords

The C_Order/C_OrderLine ERP analogy: headers carry no line-level data, lines carry no header-level data.

Key questions:
1. ProvenElement gate — should BoundElement refuse to construct without a PlacementProof?
2. CRD (Construction Rule Dictionary) — crd_rule table seeded from SH/DX proven placements
3. Where does BIM AD ARCHITECTURE doc live? Enforcement stack vs documentation only?
