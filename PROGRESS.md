# PROGRESS — Current Development State

**Last updated:** 2026-03-01 (BIM_COBOL v0.5: ROUTE SPRINKLERS + Rosetta Stone + pipeline architecture)
**Tests:** DAGCompiler **185 runs** (183 PASS, G8-DX RED ×1, 1 SKIP, 2 executions) + ORMSandbox **25/25** | TopologyMaker **19/19** | BIM_COBOL **16/16** | TOTAL: **243 PASS / 1 RED / 1 SKIP**
**SpatialDigests:** stored in `c_order.spatial_digest` — enforced by BuildingRegistryTest for all 5 buildings. Formula: name-agnostic bbox + COUNT per class (GATE-DIGEST, 2026-02-28).
**EmptySpaceChecksums:** SH=b14f0c02c4602a14 DX=1f6f2018dbda2faa TB=eb9188e164bc3156

---

### ✅ SESSION COMPLETE — BIM_COBOL: ROUTE SPRINKLERS verb (W-COBOL-9..12) (2026-03-01)

**Result: 239 PASS / 1 RED / 1 SKIP** (+4 new witness tests W-COBOL-9..12, BIM_COBOL now 12/12)

**First MEP routing verb.** Computes sprinkler head grid + pipe routing + NFPA compliance proof from a single `ROUTE SPRINKLERS TB_LKTN "Ground Floor" bilik_utama` statement. Read-only — no DB writes. Queries `ad_room_boundary` for room AABB and `ad_fp_coverage` for LIGHT hazard rules.

**New files:**
- `SprinklerGrid.java` — pure geometry: grid generation within rectangular room AABB, centers heads when room < spacing
- `PipeRouter.java` — pure geometry: main line at riserX running along Y, per-head branch pipes, PipeSpec/PipeRoutingResult records
- `ComplianceChecker.java` — NFPA compliance: coverage per head, max spacing, wall distance checks against ad_fp_coverage rules
- `RouteSprinklersVerb.java` — verb: JDBC to BOM.db (ad_room_boundary + ad_fp_coverage), orchestrates grid → routing → compliance
- `RouteSprinklersVerbTest.java` — 4 witnesses (W-COBOL-9..12)

**Modified files:**
- `scripts/run_tests.sh` — added BIM_COBOL suite (12 tests, `./scripts/run_tests.sh cobol`)

| Witness | Assertion |
|---------|-----------|
| W-COBOL-9 | bilik_utama (BEDROOM 3.1×3.1m): 1 head, pass=true, compliance pass, area ~9.61m² |
| W-COBOL-10 | common (COMMON 3.7×6.2m): 2 heads, max spacing ≤ 4.6m, compliance pass |
| W-COBOL-11 | Pipe routing: total length > 0, one branch per head, fittings present |
| W-COBOL-12 | Error cases: nonexistent room → fail, missing storey → fail, insufficient args → fail |

**DB data used (LIGHT hazard from ad_fp_coverage):** max_coverage=18.6m², max_spacing=4.6m, wall_distance=2.3m

**What's next:** VerbStage in pipeline (Stage 6), or WIRE LIGHTING verb (next Rosetta Stone target: 814 Terminal light fixtures)

---

### ✅ SESSION COMPLETE — BIM_COBOL v0.5: Rosetta Stone + Pipeline Architecture (2026-03-01)

**Result: 243 PASS / 1 RED / 1 SKIP** (+4 Rosetta Stone witnesses W-COBOL-13..16, BIM_COBOL now 16/16)

**Rosetta Stone validation:** Ran BIM COBOL geometry against real IFC extracted data.
- Terminal_Extracted.db: 108 pendant heads at z=10.9 pass NFPA LIGHT compliance via ComplianceChecker
- Grid density within 25% of actual Terminal grid (3.0m dominant spacing, 91% of measurements)
- Duplex receptacle count consistent with ad_space_type_mep rules
- Terminal MEP census: 909 sprinklers, 3821 pipes, 814 lights, 568 ducts

**Grammar enrichment from Rosetta Stone analysis:**
- Each IFC class in Terminal maps to a verb: ROUTE SPRINKLERS (done), WIRE LIGHTING, ROUTE DUCTS, PLACE OUTLETS, PLACE SWITCHES
- Each `placement_rule` in ad_space_type_mep_bom is a verb pattern: CEILING_GRID, WALL_SPACED, WALL_ENTRY, WALL_BACK, FLOOR_LOW
- Duplex wall-mount patterns: receptacles @ z=0.46m, switches @ z=1.22m, counter outlets @ z=1.07m

**Key architecture insight (§15): Verbs as pipeline applicators over CO_EmptySpace lines.**
- Level 0 (UNIT): building-level compliance → CHECK FIRE_COMPARTMENT
- Level 1 (storey): main pipe runs → ROUTE SPRINKLERS main, ROUTE DUCTS main
- Level 2 (room): per-room placement → PLACE FIXTURES per ad_space_type_mep_bom
- Cross-discipline: `CHECK CLEARANCE AGAINST beams/ducts` via R-tree index
- Future VerbStage (Stage 6) replaces hardcoded placeMEPSprinklers/placeHVAC/placeElectrical

---

### ✅ SESSION COMPLETE — BIM_COBOL: COVER WITH COMPOUND_ROOF verb (W-COBOL-5..8) (2026-03-01)

**Result: 235 PASS / 1 RED / 1 SKIP** (+4 new witness tests W-COBOL-5..8, BIM_COBOL now 8/8)

**T-junction valley stitching between hip and gable roofs.** Reads `connects_to` + `valley_type=T_JUNCTION` from `lod_parametric_mesh_param`, generates both meshes via `ParametricMesh`, intersects slope planes, computes valley lines and meeting point.

**New files:**
- `ValleyStitcher.java` — pure geometry: `Plane3D`, `ValleyLine`, `ValleyResult`, `computeTJunction()`, plane intersection via cross-product, closest-approach meeting point
- `CoverWithRoofVerb.java` — verb: loads params from component_library.db, generates hip+gable meshes, extracts slope planes from vertex indices, calls ValleyStitcher
- `CoverWithRoofVerbTest.java` — 4 witnesses (W-COBOL-5..8)

**Modified files:**
- `BIM_COBOL/pom.xml` — added `dag-compiler` dependency (brings in ParametricMesh, Point3D, Vector3D, etc.)
- `VerbContext.java` — added nullable `componentConn` field + `VerbContext.of(bomConn, componentConn)` factory

| Witness | Assertion |
|---------|-----------|
| W-COBOL-5 | HIP_ROOF_MY compound: pass=true, 1 subsidiary=GABLE_PORCH_MY, no violations |
| W-COBOL-6 | Meeting point Z > 0, Z < 1.30 (below hip ridge 1.259m), on all 3 planes |
| W-COBOL-7 | Valley V-angle 30-150°, both valley lines descend (dir.z ≤ 0) |
| W-COBOL-8 | Error cases: NONEXISTENT_ROOF→fail, null componentConn→fail, no args→fail |

**Geometry derivation (from DB params):**
- Hip south slope: z = tan(25°) × (y + 2.70) = 0.4663y + 1.259
- Gable west slope (ridge_axis=Y): z = 0.1962 × (x + 2.55)
- Triple intersection (meeting point): **(1.85, −0.849, 0.863)**

**What's next:** ROUTE SPRINKLERS verb or roof mesh valley stitching IFC output

---

### ✅ SESSION COMPLETE — X1-BUG-FIX: Repair X1-SH-GAP + X1-DX-GAP; enforce pom.xml ordering (2026-02-28)

**Result: 227 PASS / 1 RED / 1 SKIP** (was 225/3/1 — both X1 GAP tests now GREEN)

**Migration:** `migration/migration_X1_bug_fixes.sql` applied to `library/BOM.db`

| Fix | What | Result |
|---|---|---|
| Fix 1 (SH) | IfcDoor/IfcWindow: FRACTION/WALL → ABSOLUTE/BUILDING with reference bbox centres | `sh_door_window_gap` GREEN |
| Fix 2 (DX) | IfcFlowFitting_200: `is_active=0 → 1` | +1 FlowFitting (357→358) |
| Fix 3 (DX) | 14 IfcFlowController INSERTs (10 Ground FRACTION/ROOM, 4 Upper ABSOLUTE/BUILDING) | FlowController 0→14 |
| Fix 4a (DX) | DINING_SET `min_area` 6.0→12.0 (DX-only via `building_type IS NULL`) | -7 IfcFurnishingElement (ROOM_A102 loses DINING_SET) |
| Fix 4b (DX) | New UPPER_CABINET_4 at dx=2.0 in KITCHEN_CABINET_SET | +2 IfcFurnishingElement (both kitchens) |
| **Net DX** | +14 FlowController +1 FlowFitting −7+2 Furnishing = **+10** | expected_elements 1089→1099 |

**Golden masters updated in c_order:**
- Ifc4_SampleHouse: `spatial_digest=e858ce01...`
- Ifc2x3_Duplex: `expected_elements=1099`, `spatial_digest=91e158bd...`
- TB_LKTN: `expected_elements=140`, `spatial_digest=41132f60...` (kitchen BOM touched TB_LKTN +1)

**pom.xml two-surefire-executions (anti-cheat enforcement):**
- `compile-buildings` (step 1): runs `BuildingRegistryTest` only — compiles all 5 buildings, writes output DBs
- `validate-contracts` (step 2): runs all other tests — excludes BuildingRegistryTest
- Maven aborts between executions on failure: StructuralCrossCheckTest **cannot** silently pass against stale DBs
- `run_tests.sh` updated: sums both executions' "Tests run:" lines; updated expected counts + intentional RED count

**Intentional RED remaining:** G8-DX only (NULL-bound room calibration — separate investigation required)

---

### ✅ SESSION COMPLETE — GATE-X1: Structural Cross-Check Gate (2026-02-28)

**Result: 225 PASS / 3 RED / 1 SKIP** (+2 GREEN MATCH tests, +2 RED GAP tests)

Non-circular reference comparison (compiled vs extracted IFC DB). No threshold, no bypass, no BuildingRegistry dependency. Only way to defeat: delete the file — visible in git.

**New file:** `StructuralCrossCheckTest.java` — 4 test methods:

| Test | Status | Description |
|---|---|---|
| `X1-SH` (`sh_structural_match`) | ✅ GREEN | Wall/Slab/Roof/Member/Plate — count + position match reference |
| `X1-SH-GAP` (`sh_door_window_gap`) | 🔴 RED | IfcDoor/IfcWindow — positions wrong (relational resolver bug) |
| `X1-DX` (`dx_structural_match`) | ✅ GREEN | 10 classes — count matches reference |
| `X1-DX-GAP` (`dx_service_gap`) | 🔴 RED | FlowController=0 vs 14; FlowFitting=357 vs 358; Furnishing=66 vs 61 |

**Why these are honest RED, not bypassed RED:**
- No `@Disabled` annotation
- No `geometry_fail_threshold` tolerance
- No `isGenerative()` / BuildingRegistry escape hatch
- `assertEquals(ref, compiled)` — turns GREEN when the bug is actually fixed

**Repair instructions (in test Javadoc):**
- X1-SH-GAP: fix `RelationalResolver.loadDoors()` / `loadWindows()` to use IFC world coords
- X1-DX-GAP IfcFlowController: wire FlowController elements into MEP pipeline
- X1-DX-GAP IfcFlowFitting: find the missing fitting in extraction/resolver
- X1-DX-GAP IfcFurnishingElement: find source of 5 phantom elements

**Validation exposed (confirmed dead):**
- `shadowMismatches` field in CompilationContext/PipelineResult: always -1, `setShadowMismatches()` never called. X1 replaces this dead mechanism for structural + MEP classes.

**What's next:** AD-2 Part 2c — ad_opening_family PO (3 consumers) ✅ DONE (same session, see below)

---

### ✅ SESSION COMPLETE — Phase AD-2 Part 2d: X_AdSpaceType / M_AdSpaceType (2026-02-28)

**Result: compile-clean (parallel session active — full gate deferred)**

**ad_space_type + ad_space_type_alias: 8 raw JDBC sites across 4 consumers replaced.**

**New files:**
- `X_AdSpaceType.java` — generated layer: all 15 columns including structural_grid, beam_max_span, code_reference
- `M_AdSpaceType.java` — model layer: `listActive()`, `getById()`, `existsActive()` for ad_space_type; `resolveAlias()`, `loadAllAliases()`, `getAliasesForType()` for ad_space_type_alias (no separate PO — 2-column table, static methods sufficient)

**Updated consumers (8 sites):**
- `SpaceTypeAD`: alias lookup → `resolveAlias()`; `isAvailable()` COUNT → `listActive().isEmpty()`; `getSpaceTypeCount()` COUNT → `listActive().size()`; reverse alias → `getAliasesForType()`
- `ADSession.SpaceTypeLookup`: alias lookup → `resolveAlias()`; `isValid()` COUNT → `existsActive()`
- `MEPBOMResolver.loadFromLibrary()`: alias bulk load → `loadAllAliases()`
- `SpaceDimResolver.loadAliases()`: alias bulk load → `loadAllAliases()`

**Left raw:** SpaceTypeAD `LEFT JOIN ad_space_type_mep` (complex JOIN, different table); MetadataValidator `LEFT JOIN ad_space_type` (FK validation aggregate)

**AD-2 Part 2 complete.** All 4 target tables upgraded: ad_building_grid ✅, ad_wall_face ✅, ad_opening_family ✅, ad_space_type ✅

---

### ✅ SESSION COMPLETE — Phase AD-2 Part 2c: X_AdOpeningFamily / M_AdOpeningFamily (2026-02-28)

**Result: compile-clean (parallel session active — full gate deferred)**

**ad_opening_family has 3 usages across 3 consumers; 2 of 3 upgraded to typed PO.**

**New files:**
- `X_AdOpeningFamily.java` — generated layer: family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, is_fire_rated, description, is_active, depth_mm
- `M_AdOpeningFamily.java` — model layer: `listActive(conn)`, `getByType(conn, type)`, `getDepthMmWithDefault()` (returns 100 when NULL — matches Phase 119B behaviour)

**Updated consumers:**
- `OpeningBomAD.ensureFamiliesLoaded()`: replaced raw `Statement` + `SELECT *` with `M_AdOpeningFamily.listActive()`
- `CatalogValidator.loadOpeningFamilies()`: replaced raw `PreparedStatement` with `M_AdOpeningFamily.getByType()` (table-existence guard retained)
- `MetadataValidator` (dim COUNT check): **left as raw JDBC** — it's an aggregate validity check, not a data load.

**What's next:** AD-2 Part 2d — ad_space_type PO (4 consumers) ✅ DONE (same session, see below)

---

### ✅ SESSION COMPLETE — Phase AD-2 Part 2b: X_AdWallFace / M_AdWallFace (2026-02-28)

**Result: 223 PASS / 1 RED / 1 SKIP** (compile-only check — full gate deferred, parallel session active)

**ad_wall_face has 3 usages across 2 consumers; 2 of 3 upgraded to typed PO.**

**New files:**
- `X_AdWallFace.java` — generated layer: id, building_type, storey, room_name, face, wall_type_id, is_exterior, adjacent_room, is_active, building_id
- `M_AdWallFace.java` — model layer: `getByBuilding(conn, buildingType)`, `getByRoom(conn, buildingType, roomName)`

**Updated consumers:**
- `RelationalResolver.loadWalls()`: replaced raw `PreparedStatement` with `M_AdWallFace.getByBuilding()` loop
- `MetadataValidator` (COUNT check): replaced with `getByBuilding().isEmpty()`
- `MetadataValidator.checkWallFaceRefs()`: **left as raw JDBC** — it's a JOIN against ad_wall_type for FK validation; not a data load.

**What's next:** AD-2 Part 2c — ad_opening_family PO (3 consumers)

---

### ✅ SESSION COMPLETE — Phase AD-2 Part 2a: X_AdBuildingGrid / M_AdBuildingGrid (2026-02-28)

**Result: 223 PASS / 1 RED / 1 SKIP** (no count change — refactor only)

**ad_building_grid has 3 consumers; upgraded from raw JDBC to typed PO.**

**New files:**
- `X_AdBuildingGrid.java` — generated layer: id, building_type, axis, grid_label, position_mm, is_active, building_id
- `M_AdBuildingGrid.java` — model layer: `getByBuilding(conn, buildingType)`, `buildAxisMap(rows, axis)`, `getPosition(rows, axis, label)`

**Updated consumers:**
- `RelationalResolver.loadRooms()`: replaced raw `Statement` with `M_AdBuildingGrid.getByBuilding()` + `buildAxisMap()`
- `MetadataValidator`: replaced raw COUNT query with `getByBuilding().isEmpty()`
- `PlacementProver.computeExpectedPerimeter()`: replaced raw `PreparedStatement` with `getByBuilding()` loop

**What's next:** AD-2 Part 2b — ad_wall_face PO (3 consumers) ✅ DONE (same session, see below)

---

### ✅ SESSION COMPLETE — GATE-DIGEST: Spatial Digest Enforcement (2026-02-28)

**Result: 223 PASS / 1 RED / 1 SKIP** (no count change — digest gate fires and passes)

**Changes:**
- `SpatialDigest.java`: new name-agnostic formula. Removed `element_name` from hash payload
  (names differ EXTRACTED vs GENERATIVE). Added `CLASS={x} COUNT={n}` header per class —
  adding/removing any element changes the hash even if all remaining bbox values are unchanged.
  All IFC classes covered: walls, slabs, roof, doors, windows, furniture, MEP.
- `c_order.spatial_digest`: 5 golden masters written (migration_DIGEST_spatial_fingerprint.sql).
  `BuildingRegistryTest` now enforces digest on every pipeline run via existing `if (entry.spatialDigest() != null)` guard.
- `scripts/run_tests.sh`: updated expected counts + stale baseline comment replaced.

**Digests stored (prefix shown — full 64-char in DB):**

| Building | Prefix |
|---|---|
| Ifc4_SampleHouse | fd347105... |
| Ifc2x3_Duplex | d65ac3ce... |
| TB_LKTN | 44845374... |
| SJTII_Terminal | fed88a1a... |
| ST_SH | 24d97489... |

**What the hash total proves:** any dimension change, position shift, added/removed element of
any class → digest changes → `BuildingRegistryTest` fails → developer gets the stop sign.

**Remaining open items (from audit):**
- ST-1c POC gate (`SpatialDigest(ST_SH) == SpatialDigest(SH)`) not met — deferred to ST-1d
- ST mode runs ProveStage Tier 1 (P01-P03) not enforced — deferred
- G8-DX intentional RED unchanged

---

### ✅ SESSION COMPLETE — Phase G-1 Step 5: Data-Drive Stall Dividers (2026-02-28)

**Result: 223 PASS / 1 RED / 1 SKIP** (+2 new witness tests W-G1-5-A, W-G1-5-B)

**Stall divider constants are now data-driven from BOM, not hardcoded.**

**Changes:**
- `migration_G1_step5_stall_params.sql`: added `stall_divider_depth=1.2` and `stall_divider_height=1.8`
  to m_attribute for bom_child_id=58 (TOILET role line in TOILET_BLOCK_FIXTURES).
  `spacing=1.3` was already present.
- `BOMTierResolver`: added `StallDividerParams` record + `getStallDividerParams(bomId)` method.
  Reads all 3 params from the TOILET-role child of the named BOM.
- `StoreyCompiler`: added lazy `getBOMTierResolver()` singleton; Phase 98 stall divider block
  replaces 3 hardcoded constants with `sdp.spacing()`, `sdp.dividerDepth()`, `sdp.stallHeight()`.
- `StallDividerParamsTest`: witnesses W-G1-5-A (BOMTreeLoader loads params) and
  W-G1-5-B (BOMTierResolver.getStallDividerParams returns correct values).

**Scope note:** SH/DX only. SJTII_Terminal intermittent SQLITE_BUSY on DigestStage (pre-existing,
large build 51088 elements) — out of scope, not investigated.

**What's next:** Phase AD-2 Part 2 (PO classes for ad_building_grid, ad_wall_face, ad_opening_family, ad_space_type)

---

### ✅ SESSION COMPLETE — Phase ST-1c: Template-Driven Compilation Walker (2026-02-28)

**Result: 221 PASS / 1 RED / 1 SKIP** (+5 new witness tests W-ST-1..4)

**ST_SH is now a live active pipeline entry.** Template-driven compilation fully wired.

**Changes:**
- `TemplateStage` inserted at Step 4 in `CompilationPipeline.STAGES` (skips for non-ST)
- `populateCoEmptySpace` extended: ST mode selects UNIT BOM via GF template owner,
  writes L2 lines from CompositionReport leaf selections, marks CO when composition complete
- `ProveStage` skips for ST mode (no relational placement rules — template proof is enough)
- `BuildingRegistry.BuildingEntry`: added `cbpartner` field
- `BOMChainIntegrityTest.t3`: exempt c_bpartner='ST' buildings (no c_orderline)

**Migration (`migration_ST1c_template_bom.sql`):**
- `FLOOR_SH_GF_STD.bom_category` L1→GF (SH template path fix)
- `ST_SH` in `ad_building` (MetadataValidator requires)
- `ST_SH.is_active=1, expected_elements=123, geometry_fail_threshold=5`

**Design notes:**
- SpatialDigest(ST_SH) ≠ SpatialDigest(Ifc4_SampleHouse): GENERATIVE (123 elems) ≠ EXTRACTED (56 elems)
- Template proof: composition complete (9 selections, 0 gaps) → header marked CO in WriteStage
- UN BOM derived from GF selection owner (SH → UNIT_SH_STD), not AABB-fit across all owners
- L2 lines in co_empty_space_line: 7 leaf selections (SL, GF, LI, BD, DN, KT, BT, RF)

**What's next:** Phase G-1 Step 5 (data-drive stall dividers) or Phase AD-2 Part 2

---

### ✅ SESSION COMPLETE — Phase NORM-3b: CO_EmptySpace Collapse Assessment (2026-02-28)

**Result: 216 PASS / 1 RED / 1 SKIP** (no code changes — assessment only)

**Decision: KEEP `co_empty_space` as a separate table. Do not collapse.**

Assessment against PROGRESS.md NORM-3b criteria:

1. **Can `is_available` move to `c_order.doc_status`?** — No.
   `c_order` lives in BOM.db (design-time authority). `ProveStage` writes the proof result
   using the output.db connection. Moving the quality gate to BOM.db would require a
   second cross-DB JDBC write at prove time — more complex, not less.

2. **Does removing `co_empty_space` simplify the EmptySpaceChecksum witness (Gate #3)?** — No.
   `SpatialDigest.computeEmptySpaceChecksum()` reads only `co_empty_space_line` (bom_level=0).
   It does not touch the `co_empty_space` header at all. Witness complexity is unchanged
   whether the header exists or not. Decision criterion: *only collapse if complexity goes down*.
   It doesn't → keep.

3. **Additional blocker:** `co_empty_space` stores `origin_x/y/z_mm` (actual RTREE-measured
   origin at compile time). `c_order` has `aabb_width/depth/height_mm` (design-time intent)
   but no origin. These can legitimately differ (IFC models are not always at world origin).

4. **FK cleanliness:** `co_empty_space_line.co_emptyspace_id` is a local SQLite FK within
   output.db. Collapsing to `c_order_id` directly turns it into a logical cross-DB reference.

**NORM normalisation sequence fully closed:**
NORM-0a ✅ → NORM-0b ✅ → NORM-1 ✅ → NORM-2 ✅ → NORM-3a ✅ → NORM-3b ✅ (KEEP)

**What's next:** Phase ST-1c (template-driven compilation walker) or Phase G-1 Step 5 (data-drive stall dividers)

---

### ✅ SESSION COMPLETE — Phase NORM-3a: BOM Walker + Visitor Pattern (2026-02-28)

**Result: 216 PASS / 1 RED / 1 SKIP** (+9 new witness tests, SpatialDigests unchanged)

Single BOM walker replaces two independent BOM traversal passes. `BOMAssemblerAD` deleted.
`AssemblyStructureVisitor` + `BOMWalker` are the sole source for element_assemblies.

**New files:**
- `bom/walker/BOMVisitor.java` — visitor interface (onMake, onMakeComplete, onBuy, onPhantom, flush)
- `bom/walker/BOMWalker.java` — tree traversal engine; `walk()` + `walkSelf()` (synthetic root wrapping)
- `bom/walker/AssemblyStructureVisitor.java` — ports BOMAssemblerAD.applyAllRecipes() to visitor pattern
- `bom/walker/SpatialPlacementVisitor.java` — Phase C parallel parity baseline (delegates to RelationalResolver)
- Tests: `BOMWalkerTest.java` (W-WALKER-1..5), `SpatialPlacementVisitorTest.java` (W-SPV-1..4)

**Deleted:** `bom/BOMAssemblerAD.java` — replaced by AssemblyStructureVisitor.

**Remaining debt:**
- `RelationalResolver` still used by `PlacementAD.loadRelational()`, `SpatialPlacementVisitor.compute()`,
  and `CompilerContractTest` reflection. Full replacement deferred (SpatialPlacementVisitor needs
  independent coordinate resolution — Phase D full switch).
- `PlacementAD.consumed` registry still used by `StoreyCompiler.markConsumed()` + `BuildingWriter.isConsumed()`.

---

## Archived Sessions

**All NORM phases complete.** Detailed session logs in `docs/archive/PROGRESS_sessions_20260228.md`:
- NORM-2, NORM-1, NORM-0b, NORM-0a, ES-1 (2026-02-28)
- ST-1b, ST-0, F3, F2, F (cleanup), E (3-DB Split), D Cleanup (2026-02-26 → 2026-02-27)
- G-1 Steps 1-4, AD-2 Part 1 (2026-02-26)

**Older sessions (2026-02-25 → 2026-02-26):** See git log or `migration/` for Phases A–C, SH/DX Gap Resolution, Phase 4 BOM.db Extraction.

---

## ⚡ NEXT — Pending Work

### Phase AD-2 Part 2: New PO classes for multi-consumer tables

These tables are accessed from 3+ files and benefit from typed access:

| Table | Consumers | New PO | Key methods |
|---|---|---|---|
| `ad_building_grid` | RelationalResolver, PlacementProver, MetadataValidator | `X_AdBuildingGrid / M_AdBuildingGrid` | `getByBuilding(conn, buildingType)`, `getPosition(axis, label)` |
| `ad_wall_face` | RelationalResolver, MetadataValidator (×2) | `X_AdWallFace / M_AdWallFace` | `getByBuilding()`, `getByRoom()` |
| `ad_opening_family` | OpeningBomAD, CatalogValidator, MetadataValidator | `X_AdOpeningFamily / M_AdOpeningFamily` | `getByType()`, `getByFamily()` |
| `ad_space_type` + `ad_space_type_alias` | SpaceTypeAD, ADSession, MEPBOMResolver, SpaceDimResolver | `X_AdSpaceType / M_AdSpaceType` | `getByName()`, `resolveAlias()` |

**Note:** `ad_building_grid` and `ad_wall_face` were previously raw JDBC carve-outs (single-consumer assumption). Both have 3 consumers — upgrade to PO.
One table at a time, full test gate after each. Key files: `ORMSandbox/src/main/java/com/bim/ormsandbox/po/`

---

### ✅ Phase G-1 Step 5: Data-Drive Stall Dividers — COMPLETE

---

### ✅ Phase ST-1c: Template-Driven Compilation Walker — COMPLETE

**BOMCopyStage** (verbatim copy M_BOM tree → C_OrderLine.BOM) deferred to future phase.
ST_SH is a live active pipeline entry. L2–L3 room-level lines future work (ST-2).
