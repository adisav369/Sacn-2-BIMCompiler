# PROGRESS — Current Development State

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — **Session 44. 20 buildings onboarded. SH 10/10, FK 10/10, IN 9/10, DX 8/10, TE 9/10. New: BA 6/6, BH 6/6, BS 6/6, IP 6/6, SC 6/6, CA 6/6, CS 6/6, CH 6/6, CE 6/6, CP 6/6, ES 6/6, MO 4/6.**

| Gate | SH | FK | **IN** | DX | TE |
|------|----|----|--------|----|----|
| G1-COUNT | PASS (55) | PASS (82) | **PASS (699)** | PASS (1099) | PASS (48428) |
| G2-VOLUME | PASS (+0.00%) | PASS | **PASS** | -0.16% (MIRROR) | PASS |
| G3-DIGEST | PASS | PASS | **FAIL (120 window SHIFT)** | FAIL (G2 drift) | FAIL (FRAME) |
| G4-TAMPER | PASS | PASS | **PASS** | PASS | PASS |
| G5-PROVENANCE | PASS (0 GEO_) | PASS | **PASS** | PASS (7 checks) | PASS (0 GEO_) |
| G6-ISOLATION | PASS | PASS | **PASS** | PASS | PASS |
| C8-DIVERSITY | PASS | PASS | **PASS** | PASS | PASS |
| C9-AXIS | PASS | PASS | **PASS** | FAIL (87 mismatches) | PASS |
| W-TOT | PASS | PASS | **—** | PASS (centroid fix S43) | 48336/48428 |

**Pipeline:** 9 stages. 63 verbs. 1326 products. 4-DB architecture (21+20+6+output).
**Rosetta Stones:** 20 buildings — SH (55), FK (82), IN (699), BR (48), RD (53), RL (73), DX (1099), TE (48428), BA (11), BH (5), BS (16), IP (27), SC (3214), CA (2586), CS (1078), CH (3693), CE (2110), CP (6584), ES (1941), MO (3114).
**BIMBackOffice:** 5/5 GREEN (PrintConfigTest). New module: ERP back-office (print config, reports, portfolio).
**BonsaiBIMDesigner:** 249/249 GREEN (33 test classes). DemoHouseTest: skipped (DM_BOM.db empty).
**Tier 1 (S39):** 6D SustainabilityDAO, 7D FacilityMgmtDAO, Audit ChangelogDAO — 14 witnesses.
**4D/5D (S39b):** ScheduleDAO (CIDB sequence → Gantt), CostDAO (3-component: mat+lab+eq) — 11 witnesses.
**Portfolio (S39c):** PortfolioDAO — analysis table, Kanban board, balanced scorecard — 6 witnesses. 8 projects scanned.
**Scorecard: 31/36** (was 27). 4D/5D live DAOs + 6D=2, 7D=2, 3D=3, CR/Audit=2. Nearest competitor: 9.
**Wire actions:** 39 total (was 38). +1: clickToPlace.
**WF-BB §26:** 25 requirements, 17 witnesses. 8 CODE DONE (needs Blender test), 4 STUB, 13 SPEC ONLY.

## What's Next

**[DONE] G-8 Click-to-Place core wiring (session 45):**
  Interactive click-to-place: viewport click → room resolution → discipline-aware product placement.
  **Java (API + Server + DAO):**
  - `clickToPlace(ClickToPlaceRequest)` — resolves viewport (x,y,z) to containing room bbox,
    queries `ad_space_type_mep_bom` from disc_validation.db for MEP disciplines (FP/ELEC/SP/ACMV),
    falls back to keyword browse for ARC (furniture). Places seed item at click offset.
  - `placeItem` persistence — now writes `C_OrderLine` to `work_output.db` via `insertOrderLine()`,
    auto-creates sub-order if none exists. Returns real `orderLineId > 0`.
  - `MEPBOMQuery` DAO — queries `ad_space_type_mep_bom` by (space_type, discipline),
    maps discipline → MEP product IDs (FP=SPRINKLER/EMERGENCY_LIGHT, ELEC=LIGHT/OUTLET/SWITCH/etc,
    SP=TOILET/SINK/FLOOR_TRAP, ACMV=SUPPLY_DIFFUSER/EXHAUST_FAN/AIRCON_POINT).
  - `ClickToPlaceResponse` carries `mepRequirements` list with qty, placement_rule,
    host_surface, building_code provenance from disc_validation.db.
  **Python (Blender addon):**
  - `BIM_OT_designer_click_to_place` — modal operator: CROSSHAIR cursor, viewport ray-cast
    via `bpy_extras.view3d_utils`, ground-plane intersection → world mm → server call.
  - `click_discipline` EnumProperty (ARC/FP/ELEC/SP/ACMV) in panel with Place button.
  - `click_to_place()` client method wired to `"clickToPlace"` server action.
  **15 witnesses** (2 test classes): ClickToPlaceTest (9) + MEPBOMQueryTest (6).
  Multi-item placement: MEP disciplines place ALL required products per room (e.g., BATHROOM+ELEC
  = LIGHT + OUTLET_GFCI + SWITCH). Coverage tracking: qtyPlaced vs qtyNormal per requirement.
  `computePlacementOffset()` positions items by rule: CEILING_CENTER, WALL_ENTRY, WALL_SPACED,
  CEILING_GRID (grid distribution for qty>1), FLOOR_LOW, WALL_BACK, WALL_HIGH, etc.
  Panel shows per-product coverage (GREEN=met, AMBER=partial, RED=missing).
  249/249 GREEN. Rosetta Stones undisturbed.

**[DONE] DB migration + doc consolidation + market report (session 41b):**
  Phase 2b: MEPAD, MEPBOMResolver, ManifestResolver → disc_validation.db (was bom.db).
  Phase 3: component_library.db 81→21 tables (dropped 60 tables).
  disc_validation.db 21→20 tables (dropped ad_space_type_furniture).
  Consolidated 4 ERDs → 1 (bim_architecture_viz.html), updated to 4-DB.
  Created database/DATABASE_SCHEMA.md — single source of truth for all table docs.
  Fixed 7 stale "3-DB" refs across 6 spec files. README.md complete rewrite.
  Blender/Bonsai integration guide expanded (SYSTEMS_INSTALLER_GUIDE §6).
  Market Impact Report added to docs + go-to-market timeline in ACTION_ROADMAP.
  Datasette (port 8001) documented as live DB browser service.
  13/13 DiscValidationDBTest PASS. 248/248 Designer. 19/19 BackOffice.

**[DONE] CP-3 scale-up: 12 fresh IFCs onboarded, 8→20 buildings (session 44):**
  Phase 1 — 4 PCERT IFC4x3 buildings (config-only, zero compiler changes):
  - BA (11), BH (5), BS (16), IP (27): all 6/6 PASS
  Phase 2 — 8 new IFC2x3 buildings via automated `onboard_ifc.sh`:
  - SC Schependomlaan (3214 elem, 135 prod, 9.3x): 6/6 PASS — Dutch residential reference
  - CA Clinic Architecture (2586 elem, 48 prod, 21.4x): 6/6 PASS — healthcare federated
  - CS Clinic Structural (1078 elem, 19 prod, 25.8x): 6/6 PASS
  - CH Clinic HVAC (3693 elem, 35 prod, 46.2x): 6/6 PASS
  - CE Clinic Electrical (2110 elem, 76 prod, 17.2x): 6/6 PASS
  - CP Clinic Plumbing (6584 elem, 23 prod, 87.8x): 6/6 PASS — highest factorization
  - ES Esplanades (1941 elem, 60 prod, 14.1x): 6/6 PASS — Estonian 8-storey
  - MO Molio (3114 elem, 107 prod, 12.2x): 4/6 (G1 +63, G2 27%) — bSDD classification URIs
  Code changes:
  - ComponentLibrary.java: cross-class geometry fallback (element_ref lookup ignores ifc_class mismatch)
  - RosettaStoneGateTest: +12 buildings to GATE_SCOPE, +9 IFC classes to G5 known list
  - BuildingRegistryTest: +12 buildings to GATE_SCOPE
  Scripts created: onboard_ifc.sh, ifc_recon.py, rosetta_report.sh, extract_validation_rules.sh, ifc_benefits.sh
  Catalog: 823→1326 products, 23K→35K geometries, 12→20 building types
  Stale file cleanup: removed 9 empty/duplicate extracted DBs, 5 stale output DBs

**[DONE] AABB qualifier + PHANTOM spatial index + centroid diff (session 43):**
  Tack chain proof: SET BOM envelope offsets cancel algebraically — proven worldPosition = element.minX
  regardless of envelope computation. IN G3 drift is NOT a compilation bug.
  IN G3 actual root cause: SpatialDiff position-based matching cross-pairs windows across rooms
  (120/206 windows, 11695mm = building width). The 10-90mm gradient on 70 windows = extraction
  AABB vs library mesh dimensional mismatch (inner surface — asymmetric frame projection).
  `m_bom.aabb_qualifier` column: INNER/STRUCTURAL/OUTER/OPENING (GD&T tolerance zone mapping).
  ScopeBomBuilder: SET BOMs tagged OUTER, PHANTOM lines (66 across 82 IN SET BOMs).
  FloorRoomBomBuilder: FLOOR ROOM BOMs tagged INNER. StructuralBomBuilder: default OUTER.
  SpatialDiff: centroid comparison for IfcWindow/IfcDoor (invariant to asymmetric frame projection).
  BBC.md §4.2.1 (qualifier table), §4.2.2 (PHANTOM generation rule).
  DX improved 7→8/10 (centroid fix reclassified hosted element comparisons).
  SH 10/10, FK 10/10, IN 9/10 (unchanged — G3 is SpatialDiff mis-pairing debt, not compilation).

**[DONE] LAST_MILE checklist + C8 naming fix + coordinate root cause analysis (session 42):**
  Full 11-point checklist verification against all 5 Rosetta Stones.
  C8 SQL fix (R32): blank element_name normalization via `COALESCE(NULLIF(element_name,''),ifc_class)`.
  IN C8: FAIL→PASS (furnishing naming mismatch — ref blank, output ifc_class). DX C8: confirmed PASS (R31 effect).
  Checklist summary table added to LAST_MILE_PROBLEM.md with Remedy sections for each item.
  Gate table updated: IN 8→9/10, DX C8 FAIL→PASS.

  **IN G3 root cause analysis (deep investigation):**
  - The 11695mm "SHIFT" was a **SpatialDiff mis-pairing artifact** — opposite-wall windows
    cross-matched due to sorted-order pairing. NOT a real 11695mm placement error.
  - **Actual drift:** 70/206 windows have 10-90mm gradient X offset. 136/206 exact match.
  - **BOM coordinates verified correct:** full offset chain (origin + floor_dx + set_dx + leaf_dx)
    reconstructs to exact reference positions. The BOM builder is NOT the bug.
  - **Root cause:** SET BOM envelope computation. When a scope space contains mixed elements
    (walls + windows + furniture), the SET's LBD is computed from the union envelope of ALL
    elements. A wall that extends beyond the windows shifts the SET's minX/Y, which shifts
    all children's relative positions.
  - **TE avoids this:** no scope spaces, flat BUILDING→FLOOR→DISCIPLINE→LEAF hierarchy.
    Positions computed directly from elements. 48336/48428 exact (99.8%).
  - **Common fix principle:** "Only certain elements qualify as denominator" — the SET BOM
    envelope should be computed from qualifying elements (the room's bounding structure),
    not from all contained elements. Elements that protrude beyond the room (awnings,
    underground foundations, above-roof elements) should not shift the envelope.
    This is a set theory problem: which elements define the container vs which are contained.

**[DONE] FACTORIZE-v2 review + 6 fixes + C8 per-instance geometry (session 41):**
  Reviewed parallel session's FACTORIZE-v2 refactor (VerbFactorizer extraction,
  StructuralBomBuilder factorization, LOD_Object→component_geometries rename).
  Found and fixed 6 issues:
  (1) ProductGeometry SQL alias `lo`→`cg` (R30),
  (2) VerbFactorizer CP-1 regression: GUID element_ref + MA rows for unfactored CO lines (R29),
  (3) VerbFactorizer material uniformity guard: reject mixed material_rgba groups (R28),
  (4) SpatialDiff T8 tamper violations: Map.of()→Collections.emptyMap() (G4 fix),
  (5) BuildingWriter element_name: productId fallback instead of GUID (C8 grouping fix),
  (6) Per-instance geometry via GUID-keyed I_Geometry_Map + MeshBinder override (R31).
  IFC GUID format guard: `[0-9A-Za-z_$]{22}` in PlacementCollectorVisitor + MeshBinder.
  Spec hardened: LAST_MILE Gap 9 (§9.1-9.4), checklist #11, R28-R31.
  SH 9→10, FK 9→10, TE 8→9 (G5+C8 fixed), IN compiles (was 0-byte _compile.db).
  DX 7/10 stable (pre-existing MIRROR debt).

**[DONE] Front-end review + 7 fixes (session 40):**
  Code review of BonsaiBIMDesigner/ Python addon against BACK_OFFICE_SRS + TIER1_SRS.
  7 fixes: (1) socket timeout 10s, (2) threading contract docstring, (3) public
  is_peek_active() API, (4) panel bbox cache eliminates json.loads per redraw,
  (5) md5 hash dirty detection in sync timer, (6) pre-built axis GPU batches —
  no per-frame allocation in draw callback, (7) browse pagination preserves search.
  Java: clean compile, no changes. All Python syntax-checked.

**[DONE] AC11 Institute Rosetta Stone (session 39c):**
  5th building compiled. IFC2x3, ArchiCAD 11, German institutional. 699 elements,
  82 spaces, 5 storeys. Extraction: 91 geometries, ARC=697/STR=2. BOM: 93 BOMs,
  791 lines, 4.5x factorization. 9/11 PASS (C8=furnishing library gap, G4=uncommitted).
  Files: classify_in.yaml (216 lines), dsl_in.bim, ASM003_ac11_materials.sql,
  construction_manifest.yaml (+IN entry), BuildingRegistryTest/RosettaStoneGateTest
  (+RE_IN to GATE_SCOPE). SourceCodeGuide §10 hardened with 8-step IFC onboarding recipe.
  Deferred: S7 mining (window TILE, furniture density, floor repetition).

**[DONE] BIMBackOffice module + FRAME LBD fix (session 39d):**
  New `BIMBackOffice` Maven module — ERP back-office layer (iDempiere pattern).
  PrintConfig: AD_PrintFormat + AD_PrintFormatItem, output table discovery + categorization.
  Migrated 7 DAOs from BonsaiBIMDesigner → BIMBackOffice (ReportDAO, CostDAO, ScheduleDAO,
  SustainabilityDAO, FacilityMgmtDAO, PortfolioDAO, ChangelogDAO). DesignBBox → backoffice.model.
  FRAME LBD fix: VerbDetector.detectFrame() now stores LBD offsets (centroid-halfW/halfD),
  Z-uniformity check rejects multi-Z groups. BomValidator fidelity check has pre-existing
  grouping mismatch — FRAME stays approximate/SKIP. Placement path (PlacementCollectorVisitor)
  is correct. 5 witnesses (PrintConfigTest), 248/248 BonsaiBIMDesigner GREEN.
  Docs: BACK_OFFICE_SRS.md, BackOfficeUserGuide.md.

**Remaining from CP-1:** 92 TE elements (85 shift + 7 drift) from FRAME verb expansion
coordinate mismatch (centroid-vs-LBD offset). These are Gap 6 scope — the FRAME expansion
in PlacementCollectorVisitor uses gridline intersections as positions but the reference DB
stores actual element positions. Fix: correct FRAME expandFrame() to use LBD-relative
positions matching the tack convention, or convert FRAME groups to CLUSTER (lossless).

**[S38] IFC model download + FZK-Haus analysis + pipeline self-containment fix:**
- Downloaded 12 new IFC files (PCERT infra IFC4X3 + FZK Haus + opensourceBIM)
- Created `DAGCompiler/lib/input/IFC/IFCAnalysis.md` — full inventory, quality ratings
- Created `docs/FZKHausAnalysis.md` — FK onboarding guide with full pipeline recipe
- Added `SourceCodeGuide.md §Step 4.1` — Geometry Resolution Chain (LOD → library)
- **Fix:** `IFCtoBOMPipeline.run()` now calls `ensureProductCatalog()` + `ensureProductImages()`
  internally (INSERT OR IGNORE). Pipeline is self-contained — no longer depends on separate
  `--populate` invocation. SH: 10/10 PASS confirmed.
- Updated `BOMBasedCompilation.md` — pipeline phases doc corrected.

**[S38b] FZK-Haus (FK) Rosetta Stone — COMPILED (4th building):**
- **S0:** IfcStair added to `ad_ifc_class_map`. `ASM002_fzk_materials.sql`: 2 materials
  (Leichtbeton λ=0.19, Holz λ=0.13), 2 DE masonry wall types, 3 timber beam types (ROOF).
- **S1:** Extraction: 82 elements, 37 geometries, 2 storeys, 7 spaces.
- **S3:** `classify_fk.yaml` with 7 IfcSpace-derived scope spaces (AABBs from geometry).
- **S4:** `dsl_fk.bim` — 2 storeys, no ROOF/static_children (already extracted).
- **S5:** 9/10 PASS. Delta: 82=82, 0 geometry divergences, C8+C9 PASS.
- **Bug fix:** `BuildingWriter.java` bay slab `!hasMetadata` gate (G4 in BBC appendix).
- **Bug fix:** `RE_FK` added to `GATE_SCOPE` in `BuildingRegistryTest.java`.
- **Docs:** `SourceCodeGuide.md` §10 Extension Recipe: added step 4 (GATE_SCOPE).
  `BOMBasedCompilation.md` appendix: 5 onboarding gotchas (G1-G5).
- **Mined:** 42 rafters TILE pattern (700mm OC, 80×5500×3360mm). First timber structure.
- **Deferred:** `DV009_fzk_haus_rules.sql` (AD_Val_Rule table not yet created).

**DX pre-existing failures (for another session to investigate):**
- DX 7/10: G2 -0.16% MIRROR, G3 (follows G2), C9 axis (87 mismatches), W-TOT. C8 now PASS (R31+R32).
- Root cause: W↔D dimension swap in some walls (MIRROR dims debt since S25)
- These failures exist with AND without the S38 code change — not a regression

**[DONE] CP-1: TE per-element identity via m_bom_line_ma (session 38):**
  Gap 5 closure: IFC GUIDs threaded through BOM via Material Allocation table
  (iDempiere M_InOutLineMA pattern). Clean separation: verb_ref = geometry formula,
  m_bom_line_ma = per-instance identity. SpatialDiff hybrid matching: identity first
  (>25% overlap), position fallback for SH/DX. TE: 48336/48428 exact (99.8%),
  0 missing, 0 extra. SH: no regression (9/10, C8 pre-existing). DX: no regression (7/10).
  Files: ExtractionReader, ExtractionPopulator (guid field), DisciplineBomBuilder (MA writes),
  VerbDetector (computeExpansionOrder), PlacementCollectorVisitor (loadMaGuids),
  SpatialDiff (diffByIdentity + hybrid), IFCtoBOMPipeline (m_bom_line_ma DDL),
  LAST_MILE_PROBLEM.md (Gap 5 CP-1 status).

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

**[DONE] Phase I-4: Cut-and-fill + terrain-aware snap() (session 38b):**
  CutFillCalculator: flat design level + alignment profile modes. Computes cut/fill/net
  volumes from terrain vs design Z. Proven on 689-point real terrain (flat@40m: 254K m³ cut,
  mean@43.8m: 31K balanced). SnapOptions extended with terrainContext + terrainSnap fields.
  snap() loop adjusts bbox Z via TerrainSnap.computeZ() per element. Road course snaps to
  terrain Z, bridge deck at terrain+clearance, multiple bboxes get different Z from gradient.
  Backward compatible: building mode (null terrain) unchanged.
  GradingStrategy: CONTOUR (default, follow terrain) / STRAIGHT (fixed level, traditional
  cut-and-fill) / BLEND (slider 0→100%). designZ = terrainZ*(1-blend) + designLevel*blend.
  Proven: contour=0 m³, blend50=396 m³, straight=793 m³ (monotonic increase).
  Terrain JSON reference: `BonsaiBIMDesigner/src/test/resources/terrain/survey_689pt.json`.
  13 witnesses, 216/216 GREEN.

**[DONE] WF-BB §26 — Wireframe-First Interaction Protocol (session 39):**
  BIM_Designer_SRS.md §26: 25 requirements, 17 witnesses. Core UX principle:
  "BBox is the interaction mode. Full geometry is the settled state."
  **Spec:** §26.2-26.9 (Phase 1/2 core), §26.12 (chain highlight + ghost drag),
  §26.12.3 (cost-of-change live feedback), §26.13 (R_Request change requests),
  §26.14 (AD_ChangeLog audit trail + multi-user undo).
  **Code (Phase 2 core — WF-02..WF-10):**
  - `design_bbox.py`: Phase 2 engine — `enter_phase2()` sets all objects to
    `display_type='BOUNDS'`, `focus_phase2(obj)` promotes to SOLID + vivid bbox
    overlay + RGB orientation markers (Red=+X, Green=+Y, Blue=+Z).
    `enter_peek(metadata)` draws properties popup with blf text overlay.
    `has_full_geometry()` auto-detects Phase 1 vs Phase 2.
  - `operator.py`: `toggle_mode` dispatches Phase 1 (GPU overlay) vs Phase 2
    (per-object BOUNDS) automatically. `focus_section` bridges bomId→Blender
    object in Phase 2. New `BIM_OT_designer_peek_metadata` operator queries
    backend `getElementMetadata` verb with local fallback.
  - `panel.py`: Phase indicator label, "Peek Properties" button.
  - `client.py`: 4 new verbs: `get_element_metadata()`, `get_chain()`,
    `cost_of_change()`, `move_chain()`.
  **Backend stubs (Java):**
  - `DesignerAPI.java`: 4 new methods + 10 records (ElementMetadataResponse,
    ChainResponse, CostOfChangeResponse, MoveChainResponse, ChangeRequestInfo, etc.)
  - `DesignerAPIImpl.java`: `getElementMetadata` queries from saved bboxes via
    `getCurrentBboxes()`. `getChain`/`costOfChange`/`moveChain` are stubs.
  - `DesignerServer.java`: 4 new case handlers in dispatch switch.
  - `JsonProtocol.java`: `raw()` method for complex request forwarding.
  All compile clean. Rosetta Stones undisturbed.

**[PLANNED] Phase I-5: BlenderBridge Terrain Viewport:**
  Wire terrain context to Blender viewport for interactive drag.

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
| 45 | 2026-03-21 | G-8 Click-to-Place: clickToPlace API + viewport ray-cast + discipline selector + placeItem persistence + MEPBOMQuery + multi-item placement + coverage tracking + computePlacementOffset (16 placement rules). 15 witnesses | 249/249 |
| 44 | 2026-03-21 | CP-3 scale-up: 12 IFCs onboarded (8→20 buildings). Scripts: onboard_ifc.sh, ifc_recon.py, rosetta_report.sh. Cross-class geometry fallback. 823→1326 products. Clinic federated (5 disciplines), Schependomlaan, Esplanades, Molio | — |
| 43 | 2026-03-21 | AABB qualifier (INNER/OUTER/STRUCTURAL/OPENING) on m_bom. PHANTOM spatial index (66 lines across 82 IN SET BOMs). SpatialDiff centroid for IfcWindow/IfcDoor. Tack chain algebra proven correct. DX 7→8/10. BBC.md §4.2.1-4.2.2 | — |
| 42 | 2026-03-21 | LAST_MILE checklist: C8 SQL blank element_name fix (R32). IN C8 FAIL→PASS, DX C8 FAIL→PASS. IN G3 diagnosed (120 window SHIFTs). Remedy sections added to checklist for newbies | — |
| 41 | 2026-03-20 | FACTORIZE-v2 review: 6 fixes (R28-R31), per-instance geometry (GUID I_Geometry_Map), IFC GUID format guard, Gap 9 spec. SH 10/10, FK 10/10, TE 9/10 | — |
| 39c | 2026-03-20 | AC11 Institute Rosetta Stone: 5th building (699 elements, 82 spaces, 5 storeys). Extraction + classify_in.yaml + dsl_in.bim + manifest + GATE_SCOPE. 9/11 PASS. SourceCodeGuide §10 hardened with complete IFC onboarding recipe | — |
| 38b | 2026-03-20 | Phase I-4: CutFillCalculator, GradingStrategy (contour/straight/blend), SnapOptions terrain wiring, terrain JSON reference, 13 witnesses | 216/216 |
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
| G-8 | Click-to-Place (viewport click → room → discipline placement) | **DONE** (s45) |
| G-9 | ORDER View + BOM Outliner | planned |
| G-10 | Promote to BOM (governance gate) | planned |
| G-11..12 | ParametricMesh UI, Text Mode | planned |
| G-13 | Auto-chain (seed → connectors → pipes) + live coverage | planned |
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
- IN G3: 120/206 windows cross-paired by SpatialDiff position-based matching (11695mm = building width). 70/206 have 10-90mm AABB dimensional mismatch (extraction AABB vs library mesh — inner surface issue). Tack chain proven algebraically correct (S43). Fix: identity-based matching (CP-1 MA rows for RE buildings) or R21 IfcRelVoidsElement extraction
- DX G2 -0.16% MIRROR: W↔D swap in output walls vs reference (pre-existing since S25). C9 87 axis mismatches = same root cause

---
*Archive: `docs/archive/PROGRESS_ARCHIVE_2026-03-08_completed_work.md`*
*Git tags: `[DX-1]`, `[QA]`, `[DISC-*]`, `[DOCS]`, `[TE-*]`, `[R8]`, `[ANTI-DRIFT]`*
