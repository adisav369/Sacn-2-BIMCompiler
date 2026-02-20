# PROGRESS — Current Development State

**Last updated:** 2026-02-21
**Current phase:** Phase BOM-1 COMPLETE — MRP BOM Drop → Schedule → Compile flow
**Baseline:** ALL 4 BUILDINGS PASS via `mvn test` — SH **63**, DX **1197**, TB-LKTN **138**, Terminal ~51088
**Tests:** 58 total (41 contract + 4 registry + 13 metadata)

---

## Next Session: Phase BOM-2 — GGF + GF BOM Parent Layers

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
