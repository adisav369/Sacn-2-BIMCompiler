# PROGRESS — Current Development State

## Current State

**Gate:** `./scripts/run_RosettaStones.sh sh` — **SH Rosetta Stone 5/5 PASS. Contract tests 10/10 GREEN.**

| Suite | Count |
|---|---|
| DAGCompiler | 191 PASS / 1 RED (G8 calibration, intentional) |
| ORMSandbox | 34 PASS / 2 RED (pre-existing) |
| TopologyMaker | 19/19 |
| BIM_COBOL | 186 total, 182 PASS / 4 RED (CoverWithRoof ×3 + VerifyPlacement ×1, pre-existing) |

**RosettaStoneGateTest: 6 GATES GREEN for SH and DX.**

| Gate | SH | DX | Notes |
|------|----|----|-------|
| G1-COUNT | PASS (55) | PASS (1099) | |
| G2-VOLUME | PASS (+0.00%) | PASS (+0.00%) | |
| G3-DIGEST | PASS | PASS | SHA256=496022db / 4dd805b3 |
| G4-TAMPER | PASS | PASS | 0 violations / 16 rules |
| G5-PROVENANCE | PASS | PASS | 55/55 traced, 1099/1099 traced |
| G6-ISOLATION | PASS | PASS | |

**Pipeline:** 9 stages — Metadata, Parse, Compile, Template, Write, Verb(SPI), Digest, Geometry, Prove
**BIM COBOL:** 60 verbs, 187 witnesses (183 PASS / 4 RED pre-existing). F5 integration: 36 verbs exercised end-to-end, 42 verb lines (including PLACE BOM SH emit + CHECK PLACEMENT spatial proofs). VerbLogger (compact/detail/json). VerbExecutor SPI wired. PP_Order_Node = audit trail. EntityType enforcement (D=Dictionary read-only, U=User mutable, A=Application). Report verbs output XLSX via Apache POI. Phase H2+: 6 verb wrappers replace all raw SQL on protected + state tables + T16 tamper rule (expanded to wm_empty_storage_line).

**Rosetta Stone Buildings (manifest-registered):**

| Building | Mode | expected_elements | EmptySpaceChecksum (L0) |
|---|---|---|---|
| Ifc4_SampleHouse (SH) | EN-BLOC | 55 | b14f0c02c4602a14 |
| Ifc2x3_Duplex (DX) | EN-BLOC | 1099 | 48c95914ede78646 |
| SJTII_Terminal (TE) | EXTRACTED | 51088 | — (pending) |

TB-LKTN removed from manifest — generative case from clean slate once EXTRACTED pipeline stable.
ST_SH / ST_DX registered as template paths (no output DB).

**Dual Output (enbloc/walkthru):** SH enbloc=55 walkthru=55 delta=0. DX enbloc=1099 walkthru=1099 delta=0.

**Pre-existing failures (not bugs):**
- DAGCompiler: G8-DX calibration ×1 (intentional)
- BIM_COBOL: CoverWithRoof ×3 (HIP_ROOF_MY not in lod_parametric_mesh_param)
- BIM_COBOL: VerifyPlacement ×1 (DX placement gaps)
- ORMSandbox: ×2 (pre-existing)
- CO_TE G1-COUNT: -4 (Terminal, Phase B scope)
- TB overproduction (464 vs 139) — GENERATIVE path

## Model Design — COMPLETE (§11.1–11.37)

See `docs/ConstructionAsERP.md` §11 for full design decisions.

- **No invention:** every element traces to component_library.db (extracted from IFC)
- **BOM.db = read-only dictionary**, output.db = fresh each run
- **Three-concern lock (§11.9):** C_OrderLine=WHAT, PP_Order_Node=HOW, co_empty_space_line=WHERE
- **C_DocType model:** DocBaseType (RE/CO/IN) classifies. DocSubType (SH/DX/TB/TE/ST) drives BOM scoping. ST = template path. Prime Rule: `C_Order.(AABB + DocBaseType + DocSubType) == m_bom.(AABB + DocBaseType + DocSubType)` — three-key match, same level both sides.
- **Selection cascade:** AABB fit → largest volume → seq_no tiebreaker
- **Tack convention (§3.4):** Left-Front-Down corner. dx/dy/dz always >= 0. Guard: X_M_BOMLine.setDx()
- **BOM leaf→mesh:** m_bom_line.child_product_id → M_Product → M_Product_Image → LOD_Object

## Clean BOM.db — DONE for SH (2026-03-10)

BOM.db is now a pure dictionary — no extraction artifacts:
- **origin always (0,0,0)** — world position lives in CO_EmptySpace (output.db)
- **Floor-relative offsets** — element dx = centroid - floor_origin (parent-relative)
- **MAKE children carry floor-to-building offset** (always >= 0)
- **Instance columns NULL** — storey, element_ref, ordinal, orientation, material stay in I_Element_Extraction
- **Compiler reads world origin from I_Element_Extraction** at compile time (PlacementLoader)
- **MetadataValidator uses C_DocType** instead of ad_building (abstract model)

**Verification (SH):** 55 elements (27 GF + 2 Roof + 26 CW), delta=0, coordinate proof checked.
DX extraction deferred until SH proven clean end-to-end.

### Resolved (2026-03-10)

1. **5 contract test failures — FIXED (10/10 GREEN):**
   - m_attribute populated: wall_rule (Piano), opposite_wall (TALL_CABINET_A), stall params (TOILET)
   - IntraBOM R1/R2: exclude FLOOR/BUILDING bom_types from room-scale thresholds
   - Data added to RosettaStoneToBOM.py via abstract (bom_id, role) lookup

2. **Rule 8 — FIXED:** BUILDING_TBLKTN_STD AABB corrected to 9900×8500×4300 (was DX placeholder 4871×2450×2000)

3. **DX extraction — SCRIPT READY:** RosettaStoneToBOM.py calls extract_all(), CW/FN/MS bom_categories added.
   BOM.db not yet regenerated (script not yet sole source of truth — manual data still diverges).

4. **ad_building — DEPRECATED:** Table dropped from BOM.db. MetadataIntegrityTest M1/M2/M10 migrated to C_DocType.
   AD_Building_ID removed from X_C_OrderLine PO + BuildingWriter DDL. ad_building_* child tables kept (active).

## Dynamic Building Registration — DONE (2026-03-10)

`scripts/construction_manifest.yaml` = single source of truth for building identity.
Spec: `docs/BOMBasedCompilation.md` §10. Phases A–D complete, E deferred.

| Phase | Script | Change |
|-------|--------|--------|
| A | `construction_manifest.yaml` | Created (SH, DX, TE, ST_SH, ST_DX) |
| B | `RosettaStoneExtract.py` | `BUILDINGS` dict → `yaml.safe_load(manifest)` |
| C | `RosettaStoneToBOM.py` | `C_DOCTYPE` list → `_build_c_doctype()` from manifest |
| D | `run_RosettaStones.sh` | Hardcoded paths/case → BOM.db query + loop |

All phases gated GREEN: SH=55, DX=1099, 0 geometry divergences.

### Deep QA Audit — DONE (2026-03-11)

Full audit: `docs/TestArchitecture.md` (plan + tamper seal + 3-layer defense).

**Phase 1 fixes applied (this session):**
- C5: SQL injection fixed (PlaceBomVerb, EnBlocVerb, WalkThruVerb → PreparedStatement)
- C7: StackedDuplexWitnessTest moved from src/main/ to src/test/, converted to JUnit 5
- H5: Error suppression fixed (VerbNodePersister, ADSession, PlaceBomVerb → log warnings)
- C4: FurnitureGeometryTest `assumeTrue(false)` → `@Disabled` with ticket reference
- H3 partial: BomTemplateContract + BomTemplateComposer — hardcoded "RE" root → derived from docSubType
- H4: BuildingInspector.deriveBuildingPrefix — string matching → C_DocType query
- G4-TAMPER: Added T13 (assumeTrue(false)), T14 (catch ignored), T15 (assertNotNull sole check)
- Tamper seal: 68 files hashed, `verify_test_seal.sh` created

**Negative tack offsets (C6) — investigated, INTENTIONAL:**
- KITCHEN_CABINET_SET/BASE_CABINET_7: disabled item (component_type='0'), no risk
- KITCHEN_PREFAB_MY/ELEC_LIGHT: recessed ceiling light, valid geometry
- SH_LIVING_SET/SIDE_TABLE_B: wall-calibrated furniture, valid offset
- Decision: document as exception to §3.4 rather than correct

**Phase 2 — DONE (2026-03-11, verb wrappers for raw SQL):**
1. H2: 5 verb wrappers implemented + registered in VerbRegistry (52→57 verbs):
   - `COMPOSE PREFAB BOM` — TopologyWriter.writeBom() (INSERT m_bom + m_bom_line)
   - `CLEAR VARIANCE FROM BOM` — TopologyWriter.fillBuffers() step 2 (DELETE variance)
   - `FILL BUFFERS IN BOM` — TopologyWriter.fillBuffers() steps 1,3-6 (renumber+insert fillers)
   - `REGISTER BUILDING` — CompilationPipeline.copyCOrderToOutput() (INSERT c_order via SPI)
   - `COMPLETE BUILDING` — CompilationPipeline.updateCOrderComputedResults() (UPDATE c_order via SPI)
2. 15 witness tests (3 per verb): happy path, error case, data integrity cross-check
3. T16 tamper rule: blocks future raw SQL on m_bom/m_bom_line/c_order outside verb layer
4. T16 violations: **0** (all raw SQL replaced)
5. Rosetta Stone: SH=55, DX=1099, 0 geometry divergences (verb wrappers produce identical output)
6. Re-seal: PENDING (T13-T15 trigger 48 pre-existing violations — H5 scope, not H2)

**Phase 2+ — DONE (2026-03-11, Verbs Only Rule expansion):**
1. H2+: VoidEmptySpaceVerb wraps M_WmEmptyStorageLine.voidForBuilding() (57→60 verbs)
2. T16 expanded: wm_empty_storage_line now protected (4 tables: m_bom, m_bom_line, c_order, wm_empty_storage_line)
3. PlaceBomVerb: try-with-resources for ComponentLibrary (H5 resource leak fix)
4. ComponentLibrary: implements AutoCloseable
5. VerbRegistryTest: verb count corrected (54→60, was stale after H2)
6. TestArchitecture.md: C5/C7/H5 marked DONE, Phase 1 closed
7. Verbs Only Rule (tiered) documented in MEMORY.md
8. Re-seal: 69 files INTACT

**Phase 3 (following session — golden values + content verification):**
4. C1: Golden digest verification — replace assertNotNull with exact values
5. C3: Live count queries — replace hardcoded 55/1099 with extraction DB COUNT(*)
6. C2: SpotCheckContract — pick 5 elements per building, assert exact coords
7. H6: Semantic witness — AABB comparison against extraction DB envelope

**Standing items:**
6. BOM.db full regen — backport all manual BOM data into RosettaStoneToBOM.py
7. DX compilation — 102 vs 1099 elements (blocked on #6)
8. run_tests.sh expected counts stale — update after #6 stabilizes

## Roadmap

Full production roadmap: `docs/ACTION_ROADMAP.md` — 9 phases (0–H), 3 parallel tracks.

| Phase | What | Gate |
|-------|------|------|
| **0** | **EN-BLOC Singularity — wire BOM walk for UNIT buildings** | **BuildingRegistryTest SH=55, DX=1099** |
| A | Rosetta Stone Gate Convergence (SH/DX 5 gates green) | RosettaStoneGateTest G1-G5 PASS |
| B | Terminal BOM Recomposition (51K elements) | G1-G5 PASS for CO_TE |
| C | 2D Drawing Export (3D → SVG) | SH professional drawing set |
| D | Synthetic Rosetta Stone (3D→2D→3D round-trip) | Digest match for synthetic stones |
| E | Generative from 2D Layout | TB-LKTN from 2D matches DSL |
| F | BIM COBOL v1.0 (verb-driven compilation) | Zero Java assembler code |
| G | Bonsai GUI Editor | Compile-edit-recompile cycle |
| H | iDempiere ERP Integration (CSV→REST→OSGI) | PO from compiled BOM |

**Tracks:** Core pipeline (0→A→B→F→G→H3) | 2D round-trip (0→A→C→D→E) | ERP (H1→H2)

## DX — HalfUnit Doubled Model

**DX = two mirrored half-units (DUPLEX_SINGLE_UNIT_STD) + shared envelope/MEP.**
888 paired (444 pairs, center=4.422,11.091) + 211 unpaired = 1099.
Compact: 655 stored → 1099 produced via MIRROR.

**BOM structure (2026-03-10):** BUILDING_DX_STD active children are shared structural only
(FLOOR_SLAB_GF, FLOOR_SLAB_L2, ROOF_ASSEMBLY, DUPLEX_SET_STD). Floor BOMs
(FLOOR_DX_L1_STD, FLOOR_DX_L2_STD) deactivated from BUILDING level — room content
belongs inside half-units. Generic BOM traversal produces L0 + structural L1,
zero L2. No DX-specific code — abstract tack model handles it.

**CO_EmptySpace:** DAO-based BUILDING BOM lookup via `MBOM.getBuildingBom(conn, docSubType)`.
No hardcoded bom_category values.

**Remaining blockers:**
1. rotation_rule handling in BOMWalker/PlacementCollectorVisitor
2. MIRROR verb (BIM COBOL) — `MIRROR HALF_UNIT ABOUT (4.422,11.091) AXIS Z`
3. Populate compact BOM: HALF_UNIT(444) + CENTER(5) + MEP_TRUNK(206)

### Remaining Tasks

6b. SIDE_TABLE_A/B in SH_LIVING_SET — no IFC match, possible invention
14. TB-LKTN INVENTION STOP + priority-based furniture placement
15. Terminal BOM modelling (third Rosetta Stone)
16. Mesh2Library compiler dispatch
17. G8-DX calibration investigation (NULL-bound rooms)

### Bonsai Outliner — Phase G Note
BOM hierarchy → IFC spatial structure: Building → Floor → Room → Leaves.
Maps to element_assemblies in output.db. M_BOM tree (BUILDING → FLOOR → SET → leaf).

**Known debt (advisory, no gates):** Terminal IfcReinforcingBar GIC(8), DX P23 MEP corners(364), TB P23 drain(6), TB furniture alignment, TB fans 1500mm(5), WARDROBE_SET/BATHROOM_VANITY_SET empty children, DX "Room not enclosed"(21)

---
*Completed work history: `docs/archive/PROGRESS_ARCHIVE_2026-03-08_completed_work.md`*
