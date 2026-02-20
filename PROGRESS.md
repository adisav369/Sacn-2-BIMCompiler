# PROGRESS — Current Development State

**Last updated:** 2026-02-21
**Current phase:** Phase B COMPLETE — BIM AD ARCHITECTURE TB-LKTN data fixes + ParametricMesh
**Baseline:** ALL 4 BUILDINGS PASS via `mvn test` — SH 55, DX 1085, TB-LKTN **102**, Terminal ~51088
**Tests:** 58 total (41 contract + 4 registry + 13 metadata)

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
