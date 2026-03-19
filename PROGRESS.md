# PROGRESS — Current Development State

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — **Session 37. SH 10/10, DX 7/10, TE 9/10.**

| Gate | SH | DX | TE |
|------|----|----|-----|
| G1-COUNT | PASS (55) | PASS (1099) | PASS (48428) |
| G2-VOLUME | PASS (+0.00%) | **-0.16%** (MIRROR allocated dims) | **PASS (-0.056%)** |
| G3-DIGEST | PASS | FAIL (G2 drift) | **FAIL** (s37: 1022 1mm boundary crossings, inherent to CLUSTER encoding) |
| G4-TAMPER | PASS (21 rules, T21 new) | PASS | PASS |
| G5-PROVENANCE | PASS (0 GEO_) | PASS (7 checks) | PASS (s34: Check 3 relaxed ≥4) |
| G6-ISOLATION | PASS | PASS | PASS |
| C9-AXIS | PASS | FAIL (85 mismatches) | **PASS** (s37: matching fix — 10mm bins + dim tiebreakers) |

**Pipeline:** 9 stages. 63 verbs. Seal v20 (74 files INTACT).
**BonsaiBIMDesigner:** 204/204 GREEN (25 test classes). DemoHouseTest: skipped (DM_BOM.db empty).

## What's Next

**[DONE] TE C9 axis dimension fix (session 37):**
  Root cause: NOT a CLUSTER expansion bug — output dimensions are correct. The 7 C9
  failures were element mis-pairing in the positional matching (ROW_NUMBER sort order).
  Two independent causes: (1) same-position slabs (Porcelain+CementRender at identical
  minX/Y/Z) with non-deterministic tie-breaking, (2) ~4μm position jitter from BOM
  coordinate accumulation causing 1mm rounding boundary crossings.
  **Fix:** C9 ROW_NUMBER + SpatialDiff.loadElements() → 10mm position bins (ROUND(x*100))
  + 1mm dimension tiebreakers (W,D,H) + 1mm max-bounds tiebreakers (maxX,Y,Z).
  CLUSTER encoding precision raised %.4f → %.8f (10nm). BomValidator precision updated.
  C9: 7→0. TE: 8/10→9/10. G3/TotalityContractTest remain FAIL (1022 1mm boundary
  crossings inherent to CLUSTER encoding, pairing unreliable at 48K scale).

**[DONE] LOD_Object → component_geometries rename (session 36):**
  Other session dropped LOD_Object from component_library.db + schema snapshot but didn't
  update Java code. Fixed: MetadataValidator, ProductGeometry, BuildingWriter,
  MetadataMissingException, ComponentLibrary. M_Product_Image repopulated (0→616 rows)
  from M_Product → I_Geometry_Map → component_definitions chain. SRS-canonical chain:
  M_Product → component_definitions → component_geometries (per DocAction_SRS §processIt).

**[DONE] disc_validation.db — Phase 2 (Java dual-read switch, session 36b):**
  CalibrationDAO.docEventQty() now reads ad_space_type_mep_bom from disc_validation.db
  (was component_library.db). CalibrationTest opens discConn with fallback to compConn.
  W-DV-DB-DUAL-READ witness: SPRINKLER 34=34 (identical), LIGHT disc=25 > comp=1
  (disc has DV004 per_area_normal=0.05 fix). CompilerConfig: DISC_VALIDATION_DB_PATH added.
  Phase 2b (DAGCompiler DAOs: MEPAD, MEPBOMResolver, ManifestResolver) deferred —
  ad_ref_list not in disc_validation.db, needs dual-connection approach.
  Phase 3 (drop tables from component_library.db) future.
  Product catalog gap: 8 residential MEP types need M_Product entries — not in Terminal.

**[DONE] disc_validation.db — Phase 1 (create + seed, session 33):**
  DV001 schema (19 tables) + DV002 seed (17 tables, 5613 rows). 9/9 witnesses pass.

**[DONE] Calibration seed data gaps (3 fixes, session 34b):**
  1. V007: Rule 803 ELEC spacing (typical=3000mm, max=5000mm) → validation.db
  2. DV004: LIGHT per_area_normal 0→0.05 (33 rows) → disc_validation.db + component_library.db
  3. CalibrationDAO: FP NN head-only filter (`%sprinkler head%`, excludes hose reels)
  Result: ELEC 0/6→4 CALIBRATED+1 DRIFT. ELEC pitch delta 3109→128mm. FP stays DRIFT (airport OH vs residential LH — expected).
  DiscValidationDBTest schema version check relaxed (DV001→DV* prefix). 136/136 GREEN.

**[DONE] G-7 Assembly Builder (session 35):**
  ASSEMBLY_BUILDER_SRS.md: CTFL reviewed (7 defects fixed before impl).
  ASM001_material_thermal.sql: 29 thermal properties seeded to component_library.db.
  UValueCalculator (BS EN ISO 6946), AssemblyDAO, AssemblyBuilderService.
  DesignerAPI: 4 new methods (listAssemblyTemplates, getAssemblyDetail,
  browseAssemblyLayers, swapLayer) + 8 records. 16 witnesses all GREEN.
  152/152 total GREEN. Rosetta Stones undisturbed.
  BIM_Designer.md §18.8: Click-to-Place spec (G-13). ACTION_ROADMAP updated.
  Phase 2 (wire + governance): BlenderBridge verbs, AD_Val_Rule U_VALUE, InferenceEngine.

**[DONE] Infrastructure rules — Bridge + Road + Rail (session 36):**
  DV006b: 13 bridge rules (901-913) + 29 params. BridgeRulesTest: 5 witnesses, 18 xval dims.
  DV007: 10 road rules (1001-1010) + 28 params. Layer stacking 490mm, Z-continuous.
  DV008: 7 rail rules (1101-1107) + 26 params. Sleeper spacing 606mm uniform, gauge 1500mm.
  InfraRulesTest: 8 witnesses (W-ROAD-*, W-RAIL-*, W-INFRA-SCOPE).
  Total: 30 infra rules, 83 params. 33→63 rules, 49→132 params in validation.db.
  W-INFRA-SCOPE: provenance + name prefix scoping verified, no cross-contamination.
  166/166 GREEN (20 test classes). Rosetta Stones undisturbed.
  Designer UI filtering by facility_type — deferred to future session.
  CORE_SRS.md v1.0: Scale research (§1), Report Engine 4D-7D (§2),
  Industry gap closure (§3), Compliance framework (§4), Schema extensions (§5).
  Phases R/RE/J extend ACTION_ROADMAP.md.

**[DONE] Infrastructure UI filtering in BIM Designer (session 37):**
  FacilityType enum (BUILDING/BRIDGE/ROAD/RAILWAY). PlacementValidatorImpl: dual-mode
  loadRules() (building excludes Infra_*, infra loads by provenance). PlacementValidator
  interface: activate(jurisdiction, facilityType, valConn) overload. DesignerAPI:
  BuildingTypeInfo +facilityType, FacilityTypeInfo record, listFacilityTypes(),
  snap/setJurisdiction with facilityType. 4 witnesses (W-INFRA-FILTER-1..4).
  170/170 GREEN. Rosetta Stones SH 10/10 undisturbed.
  INFRA_DESIGNER_SRS.md v1.0: terrain layer, infra element types, alignment model,
  component library, 5 implementation phases (I-1..I-5), 12 witnesses.

**[DONE] Phase I-1: Infra snap wiring (session 37):**
  extractActual() extended: width_mm, depth_mm, height_mm, thickness_mm, avg_* mapped.
  snap() extended: processes SEGMENT + LEAF bomTypes alongside ROOM.
  InfraUIFilterTest (7 witnesses, linter-generated): mode switching, listFacilityTypes.
  PlacementValidatorImplTest +4 witnesses (W-INFRA-SNAP-1..4): pier BLOCK, course BLOCK,
  rail BLOCK, building unchanged. 181/181 GREEN.

**[IN PROGRESS] Phase I-3: Infra Rosetta Stones (parallel session):**
  Component library already populated: 19 infra products in component_library.db
  (7 IfcCourse, 7 IfcEarthworksFill, 2 IfcFooting, 1 IfcRail, 1 IfcSurfaceFeature,
  1 IfcTrackElement). Tagged with building_type Infra_Road/Rail/Bridge.
  Parallel session delivered classify_rd.yaml + classify_rl.yaml + dsl files
  + V010/V011/V012 migrations + ReportDAO.java + fidelity tie-breaking fix.
  Awaiting pipeline pass completion. Specs to be updated by other session.

**[PLANNED] Phase I-2/I-4/I-5: Terrain + alignment + terrain integration:**
  Depends on Phase I-3 (Rosetta Stones prove verb patterns).

**[DEFERRED] DocValidation Rules — PlacementValidator Tier 2+3:**
  ClashDetector (DV-F-13..15) + VerticalContinuityChecker (DV-F-16..17).
  Entry: `docs/DocAction_SRS.md` §4-5.
  Blocked: AD_Clash_Rule (0 rows), AD_Val_Rule CONTINUITY (0 rows).

**[DONE] GATE-FIX F1-F4 (session 34):**
  - F1: Seal already INTACT — changed files not in sealed set
  - F2: G5 Check 3 relaxed: vertex_count ≥ 4 (not 8). Ramp is valid triangular prism. Check 6 (no GEO_) is the real guard.
  - F3: VerbDetector CLUSTER sort extended (X,Y,Z) → (X,Y,Z,W,D,H) — 7 tie-breaking fixes
  - F4: DemoHouseTest Assumptions.assumeTrue() skip guard for empty DM_BOM.db

**[DONE] CTFL SRS gap analysis (session 34):**
  4 SRS docs updated (12 new spec sections):
  - DocAction_SRS v1.3: §0.1 routing matrix, §1.3a error handling, §1.3b rotation_rule
  - DISC_VALIDATE_SRS: §10.5 handler witness claims (12 witnesses W-H1..W-H6)
  - G4_SRS v1.2: §2.5 postconditions (27 acceptance criteria)
  - CALIBRATION_SRS v1.1: §3.4 verdict rules (blocking vs advisory)
  - VerbPatternArchitecture: SPRAY deprecated, CLUSTER in taxonomy

**[TACK-FIX] Tested session 25. Results:**
  - TILE: FIXED (0.0000m). DX G2: -0.16% MIRROR dims debt. TE G2: FIXED (session 32).
  - BIMLogger wired to CompilationPipeline.

**[VERB-GRAMMAR] FIXED (session 32):**
  CLUSTER now stores per-instance W/D/H alongside position offsets.
  G2-VOLUME: TE drift **13.71% → -0.056%** (PASS). Axis mismatch: 2072 → 7.

**[R4] ST-mode Rosetta Stone** — deferred.

## Session Log (recent → old)

| Session | Date | What | Tests |
|---------|------|------|-------|
| 37c | 2026-03-20 | Terrain-following placement: PlacementContext, AlignmentContext, TerrainSnap, contour-follow on 689-pt survey. Infra vocabulary: listSegments, deriveFacilityType. INFRA_DESIGNER_SRS v2.0. 4 specs updated | 204/204 |
| 37b | 2026-03-20 | TE C9 fix: 10mm bins + dim tiebreakers + CLUSTER %.8f encoding. C9 7→0. TE 8/10→9/10 | 166/166 |
| 37 | 2026-03-20 | Infra UI: FacilityType enum, dual-mode loadRules, snap SEGMENT/LEAF, extractActual infra params, INFRA_DESIGNER_SRS.md, Phase I-1 complete | 181/181 |
| 36b | 2026-03-19 | disc_validation.db Phase 2: CalibrationDAO dual-read switch + W-DV-DB-DUAL-READ witness. CompilerConfig DISC_VALIDATION_DB_PATH | 166/166 |
| 36 | 2026-03-19 | Infra rules: DV006b bridge (13) + DV007 road (10) + DV008 rail (7) = 30 rules, 83 params. BridgeRulesTest + InfraRulesTest: 13 witnesses | 166/166 |
| 35 | 2026-03-19 | G-7 Assembly Builder: SRS + CTFL + UValueCalculator + AssemblyDAO + 4 API methods + 16 witnesses. §18.8 Click-to-Place spec (G-13) | 152/152 |
| 34b | 2026-03-19 | Calibration 3 fixes: V007 Rule 803 + DV004 LIGHT per_area + FP NN head filter. ELEC 0→4 CALIBRATED | 136/136 |
| 34 | 2026-03-19 | CTFL review: F1-F4 fixes + 4 SRS gap fixes + SPRAY deprecated + seal v20 | 136/136 |
| 33 | 2026-03-19 | disc_validation.db Phase 1: DV001 schema + DV002 seed + DiscValidationDBTest (9 witnesses) | 139/139 |
| 32 | 2026-03-19 | check_method dispatch + SpatialPredicates + CalibrationTest + DISC_VALIDATION_DB_SRS | 130/130 |
| 32 | 2026-03-19 | CLUSTER per-instance dims → G2-VOLUME 13.71%→-0.056% PASS, axis mismatch 2072→7 | 103/103 |
| 31 | 2026-03-19 | CTFL spec review: 10 defects (D1-D10) across 7 docs, G4 tamper fix (HangVerb T14) | 103/103 |
| 30 | 2026-03-19 | DocAction_SRS + DISC_VALIDATE_SRS + 11 joining/surface verbs (74 total) | 103/103 |
| 29 | 2026-03-19 | C11/C12/C13 + Compile Bridge (SRS §22) + FindSimilar | 103/103 |
| 27 | 2026-03-19 | G-5: BOM Chooser + Place + Layout + Inference + Ambient | 87/87 |
| 26 | 2026-03-19 | G-4: work_output.db + Save/Recall + snap + status strip | 57/57 |
| 25 | 2026-03-19 | TACK-FIX testing + BIMLogger | 44/44 |
| 24 | 2026-03-19 | SRS: consistency sweep + spatial predicates + doc hygiene | 44/44 |
| 23 | 2026-03-19 | SRS: V004 mined rules + spawn detail + YAML v3 | 44/44 |
| 22 | 2026-03-19 | SRS: BBC.md deep walk + LAST_MILE cross-check + G4 master-detail | 44/44 |
| 21 | 2026-03-19 | SRS: G-4 pre-code specs + TACK-FIX code + TE mining | 21/21 |
| 20 | 2026-03-18 | SPECS: DocValidate deep + EN-BLOC clarification | 21/21 |
| 19 | 2026-03-18 | SPECS: BBC.md master spec + discipline-as-metadata | 21/21 |
| 18 | 2026-03-18 | SPECS: Tack convention + LBD rename | 21/21 |
| 17 | 2026-03-18 | G-3: Design Mode + bbox renderer + UI strategy | 44/44 |
| 16 | 2026-03-18 | G-2: DocValidate + DemoHouse + pattern rules + addon | 43/43 |
| 15 | 2026-03-18 | G-1: BonsaiBIMDesigner module + design specs | 14/14 |
| 14 | 2026-03-18 | Anti-drift: rebar removal + extraction leak + tamper rules | 21/21 |
| 13 | 2026-03-17 | Verb fidelity promotion (advisory → gating) | 21/21 |
| 12 | 2026-03-17 | Anti-drift: deep comb + BOM DB hygiene | 21/21 |
| 8-11 | 2026-03-17 | Phase B: Terminal BOM + verb factorization (37:1) | 21/21 |
| 1-7 | 2026-03-16-17 | Phase 0/A: SH+DX pipeline + anti-drift + docs | — |

Details: `git log --oneline`, per-session commit tags, `docs/ACTION_ROADMAP.md`.

## Roadmap

Full roadmap: `docs/ACTION_ROADMAP.md` — Phases 0–H, G-1..G-12.

| Phase | What | Status |
|-------|------|--------|
| 0 | EN-BLOC Singularity (SH=55, DX=1099) | **DONE** |
| A | Rosetta Stone Gate Convergence (G1-G6) | **DONE** |
| B | Terminal BOM Recomposition (48K, 37:1) | **DONE** |
| G-1 | BonsaiBIMDesigner module | **DONE** (s15) |
| G-2 | DocValidate + DemoHouse | **DONE** (s16) |
| G-3 | Design Mode + bbox renderer | **DONE** (s17) |
| G-4 | work_output.db + Save/Recall | **DONE** (s26) |
| G-5 | BOM Chooser + Place + Layout + Inference + Ambient | **DONE** (s27) |
| G-6 | Compile bridge (real pipeline) | **DONE** (s29) |
| G-7 | Assembly builder (MAKE path) | **DONE** (s35) |
| G-8 | BlenderBridge pipe (Snap + incremental) | planned |
| G-9 | ORDER View + BOM Outliner | planned |
| G-10 | Promote to BOM (governance gate) | planned |
| G-11..12 | ParametricMesh UI, Text Mode | planned |
| G-13 | Click-to-Place (interactive discipline placement) | planned |
| C–H | Drawing Export, Synthetic Stone, BIM COBOL, ERP | planned |

## Pre-existing Failures (not bugs)

- DAGCompiler: G8-DX calibration ×1 (intentional)
- BIM_COBOL: CoverWithRoof ×3, VerifyPlacement ×1; schema-missing ×61
- ORMSandbox: ×18 (schema-missing)

## Known Debt

- Assembly stubs in *_BOM.db M_Product → should migrate to component_library.db
- DX MEP corners (364 fittings without connecting pipes)
- Duplicate class name `BIMConstants` (root pkg vs `topology/` pkg)
- ROUTE per-leg step-uniformity (VPA-002) — 533 instances with multi-metre fidelity errors
- CLUSTER expandCluster() missing entry validation (BBC-001)
- BomValidator verb fidelity not in compliance report (BBC-002)
- TE G3/TotalityContractTest: 1022 elements with 1mm CLUSTER encoding boundary crossings; positional matching unreliable at 48K scale. Fix: element_ref-based matching or tolerance widening

---
*Archive: `docs/archive/PROGRESS_ARCHIVE_2026-03-08_completed_work.md`*
*Git tags: `[DX-1]`, `[QA]`, `[DISC-*]`, `[DOCS]`, `[TE-*]`, `[R8]`, `[ANTI-DRIFT]`*
