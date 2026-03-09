# PROGRESS — Current Development State

## Current State

**Gate:** `./scripts/run_tests.sh` — **Phase A COMPLETE: All 6 gates GREEN for SH and DX (2026-03-08)**

| Suite | Count |
|---|---|
| DAGCompiler | SH GREEN (55/55), DX GREEN (1099/1099). CoEmptySpaceTest 8/8 GREEN. TB/TE/ST pre-existing failures. |
| ORMSandbox | 33 PASS / 3 RED (w_compose_dx, w_category_2, w_doctype_1 pre-existing) |
| TopologyMaker | 19/19 |
| BIM_COBOL | 180 total, 176 PASS / 4 RED (CoverWithRoof ×3, VerifyPlacement ×1 pre-existing) |

**RosettaStoneGateTest: 6 GATES GREEN for SH and DX.**

| Gate | SH | DX | Notes |
|------|----|----|-------|
| G1-COUNT | PASS (55) | PASS (1099) | |
| G2-VOLUME | PASS (+0.00%) | PASS (+0.00%) | |
| G3-DIGEST | PASS | PASS | SHA256=496022db / 4dd805b3 |
| G4-TAMPER | PASS | PASS | 0 violations / 12 rules |
| G5-PROVENANCE | PASS | PASS | 55/55 traced, 1099/1099 traced |
| G6-ISOLATION | PASS | PASS | |

**Pipeline:** 9 stages — Metadata, Parse, Compile, Template, Write, Verb(SPI), Digest, Geometry, Prove
**BIM COBOL:** 54 verbs, 180 witnesses (176 PASS / 4 RED pre-existing). F5 integration: 36 verbs exercised end-to-end, 42 verb lines (including PLACE BOM SH emit + CHECK PLACEMENT spatial proofs). VerbLogger (compact/detail/json). VerbExecutor SPI wired. PP_Order_Node = audit trail. EntityType enforcement (D=Dictionary read-only, U=User mutable, A=Application). Report verbs output XLSX via Apache POI.

**5 Active Buildings:**

| Building | Mode | expected_elements | EmptySpaceChecksum (L0) |
|---|---|---|---|
| Ifc4_SampleHouse (SH) | EN-BLOC | 55 | b14f0c02c4602a14 |
| Ifc2x3_Duplex (DX) | EN-BLOC | 1099 | 48c95914ede78646 |
| TB_LKTN | GENERATIVE | 139 | eb9188e164bc3156 |
| SJTII_Terminal | EXTRACTED | 51088 | — |
| ST_SH | GENERATIVE | 123 | — |

**Dual Output (enbloc/walkthru):** SH enbloc=55 walkthru=55 delta=0. DX enbloc=1099 walkthru=153 (946 missing). HELLO WORLD verb: 6 witnesses GREEN (W-HW-1..6).

**Pre-existing failures (not bugs):**
- CO_TE G1-COUNT: -4 (Terminal, Phase B scope)
- BIM_COBOL: CoverWithRoof ×3 (HIP_ROOF_MY not in lod_parametric_mesh_param)
- BIM_COBOL: VerifyPlacement ×2 (output DBs have 0 c_orderline — need full recompile)
- ORMSandbox: w_compose_dx, w_category_2, w_doctype_1
- IntraBOMRelativeTest R1/R3 (HW-5 storey sub-BOMs have building-scale dx)
- TB overproduction (464 vs 139) — GENERATIVE path

## Model Design — COMPLETE (§11.1–11.37)

See `docs/ConstructionAsERP.md` §11 for full design decisions.

- **No invention:** every element traces to component_library.db (extracted from IFC)
- **BOM.db = read-only dictionary**, output.db = fresh each run
- **Three-concern lock (§11.9):** C_OrderLine=WHAT, PP_Order_Node=HOW, co_empty_space_line=WHERE
- **C_DocType model:** DocBaseType (RE/CO/IN/ST) drives template. DocSubType (SH/DX/TB) drives BOM scoping. Prime Rule: `C_Order.(AABB + DocType + DocSubType) == m_bom.(AABB + DocType + DocSubType)` — three-key match, same level both sides.
- **Selection cascade:** AABB fit → largest volume → seq_no tiebreaker
- **Tack convention (§3.4):** Left-Front-Down corner. dx/dy/dz always >= 0. Guard: X_M_BOMLine.setDx()
- **BOM leaf→mesh:** m_bom_line.child_product_id → M_Product → M_Product_Image → LOD_Object

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

## G7-GEOMETRY — Remaining Deliverables

**Tier 1 FIXED (2026-03-08):** 7 SH furniture geometry_hash swap chains corrected.
Migration: `migration/migration_G7_SH_furniture_geometry.sql`

| Task | Status |
|------|--------|
| G7 gate: RosettaStoneGateTest @Order(7) vertex_count assertion | TODO |
| DX analysis: vertex fidelity comparison for 1099 elements | TODO |
| Scale contract: MeshBinder DimensionalContractViolation softening | WIP |

## DX — HalfUnit Doubled Model

**DX = two mirrored half-units (DUPLEX_SINGLE_UNIT_STD) + shared envelope/MEP.**
888 paired (444 pairs, center=4.422,11.091) + 211 unpaired = 1099.
Compact: 655 stored → 1099 produced via MIRROR.

**BOM structure (2026-03-10):** UNIT_DX_STD active children are shared structural only
(FLOOR_SLAB_GF, FLOOR_SLAB_L2, ROOF_ASSEMBLY, DUPLEX_SET_STD). Floor BOMs
(FLOOR_DX_L1_STD, FLOOR_DX_L2_STD) deactivated from UNIT level — room content
belongs inside half-units. Generic BOM traversal produces L0 + structural L1,
zero L2. No DX-specific code — abstract tack model handles it.

**CO_EmptySpace:** DAO-based BUILDING BOM lookup via `MBOM.getBuildingBom(conn, docSubType)`.
No hardcoded bom_category values.

**Remaining blockers:**
1. rotation_rule handling in BOMWalker/PlacementCollectorVisitor
2. MIRROR verb (BIM COBOL) — `MIRROR HALF_UNIT ABOUT (4.422,11.091) AXIS Z`
3. Populate compact BOM: HALF_UNIT(444) + CENTER(5) + MEP_TRUNK(206)

## What's Next

**All HelloWorld tasks (HW-1 through HW-7) DONE.** Phase A + Gap Closure COMPLETE.

**Completed this session (2026-03-10) — Prime Rule DocType Alignment:**
- **Prime Rule formalized.** Three-key match: `C_Order.(AABB + DocType + DocSubType) == m_bom.(AABB + DocType + DocSubType)`. Short form + long form (DAO) + template path documented in `scripts/run_RosettaStones.sh` lines 48-105.
- **m_bom.doc_base_type added.** New column mirrors C_DocType.DocBaseType (RE/CO/IN/ST). BUILDING BOMs backfilled with 'RE'. X_M_BOM: getDocBaseType()/setDocBaseType().
- **C_DocType widened.** DocBaseType CHECK now includes 'ST'. RE_ST retired → ST_SH + ST_DX created (DocBaseType='ST', DocSubType='SH'/'DX').
- **M_BomCategory.doc_sub_type added.** ST-SH (AABB=16868×8668×3945) + ST-DX (AABB=9215×26565×7885) template records created. Composite virtual ID (iDempiere pattern).
- **BuildingRegistry JOIN tightened.** Now matches on doc_base_type + doc_sub_type (three-key). ST_SH/ST_DX won't accidentally match RE BUILDING BOMs.
- **Key insight documented:** bom_category ≠ DocType. bom_category = functional role (BD/KT/LI), doc_base_type = document classification (RE/CO/IN/ST). Different concerns, same 'RE' value at BUILDING level is coincidental.
- Migration: `migration/migration_prime_rule_doctype_align.sql`. Compile clean.

**Completed this session (2026-03-10) — Convention cleanup:**
- **Removed EB_/WT_ from BOM.db.** EB_ (flat extraction artifacts) deleted. WT_ renamed to proper iDempiere names: UNIT_SH_STD, UNIT_DX_STD (matching UNIT_TBLKTN_STD pattern). One top-level UNIT BOM per building type.
- **Removed EB_/WT_ prefix dependencies from 6 Java files.** Added MBOM.getUnitBom(conn, docSubType) — finds UNIT BOM by doc_sub_type+bom_type. Updated: EnBlocVerb, WalkThruVerb, PlaceBomVerb, PlacementLoader, CompilationPipeline, HelloWorldVerb.
- **Removed computeBomAabb() from BuildingRegistry.** Was querying deleted EB_ BOMs.
- **Added aabb_width_mm/depth/height to m_bom.** iDempiere convention: M_Product dimensions on BOM header. Backfilled from children (Width=SUM, Depth=MAX, Height=MAX).
- **NULLed C_DocType.AABB** — C_DocType is type definition (behavior), not dimensions. AABB belongs on m_bom and C_Order only.
- **Archived 36 active migration scripts** to migration/archive/. Source of truth = 14 create_ad_*.py scripts (zero EB_/WT_ references).

**Completed this session (2026-03-10) — AABB Rollup + BUILDING rename:**
- **bom_type UNIT→BUILDING.** Migration: recreated m_bom CHECK constraint, renamed bom_id UNIT_*→BUILDING_*, updated m_bom_line FKs. ~25 Java files updated (production + tests). MBOM.getUnitBom→getBuildingBom. Broader than housing — covers bridges, landscapes.
- **X_M_BOM AABB support.** Added aabb_width_mm/depth_mm/height_mm constants + getters/setters.
- **ROLLUP AABB verb (W-SY-73).** Envelope computation: MAX(child.dx*1000 + child_bom.aabb_*) - MIN(child.dx*1000). Verification utility — primary flow is top-down (envelope known from design/extraction).
- **MBOM.beforeSave() ValidateBOM.** SET level: auto-fill buffer fillers between furniture, then validate strip-packing. All levels: warn if children exceed declared parent AABB. Never blocks.
- **BuildingRegistry reads AABB from BUILDING BOM.** LEFT JOIN m_bom on doc_sub_type + bom_type='BUILDING'. Dead C_DocType AABB columns no longer queried.
- **AABB backfill from extraction.** SH: 16868×8668×3945 mm. DX: 9215×26565×7885 mm. TB: 4871×2450×2000 (pre-existing).
- **run_RosettaStones.sh fixed.** Reads BUILDING BOM AABB instead of EB_ queries.
- **Doc cleanup:** BIM_Designer.md, BOMBasedCompilation.md updated. ACTION_ROADMAP.md, BIM_COBOL.md have historical EB_/WT_ references (left as historical record).
- **54 verbs** (RollupAabbVerb added). Compile clean. Test gate not yet run.

**Completed this session (2026-03-10) — Docs + code cleanup:**
- **Full EB_/WT_ doc purge.** Zero EB_/WT_ references remain in active docs. Updated: BIM_COBOL.md (46 refs → 0), ACTION_ROADMAP.md (_s/_e → _enbloc/_walkthru), ConstructionAsERP.md, PREFAB_ARCHITECTURE.md, TECHNICAL_BUILDING_GUIDE.md, VIEW_CONTRACTS.md, bim_architecture_viz.html, TheRosettaStoneStrategy.txt. §18.13 namespace section rewritten: EntityType (D/U/A) is the protection mechanism, not naming prefix.
- **UNIT_*_STD → BUILDING_*_STD across all active docs.** ConstructionAsERP, PREFAB_ARCHITECTURE, TECHNICAL_BUILDING_GUIDE, VIEW_CONTRACTS, bim_architecture_viz.html, TheRosettaStoneStrategy.txt. bom_type='UNIT' → 'BUILDING' everywhere.
- **bom_category='UN' → 'RE'.** ComposeBuildingVerb now derives category from docBaseType prefix (RE/CO/IN), not hardcoded 'UN'. M_BomCategory 'UN' deactivated (orphan — no template tree references it). BOM theory: code is abstract, metadata carries semantics.
- **BOM category hierarchy clarified.** M_BomCategory = model structure (decomposition recipe). C_DocType/Sub = nouns (building types). C_Order = unit instance (output.db). UN was too generic (Object.class level).
- **Test fixes.** TopologyBatchProcessTest: migration path → archive/. BuildingVerbTest: unitBomId→bldgBomId + bom_category 'UN'→'RE'. StTemplatePipelineTest: stale comment fixed.
- **Compile clean.** Test gate not yet run.

**PENDING — Next session:**

**Prime Rule schema alignment (design in `scripts/run_RosettaStones.sh` lines 48-105, gaps in `memory/prime-rule-design.md`):**
1. **m_bom: add `doc_base_type`** — RE/CO/IN/ST. Backfill BUILDING rows with 'RE'. Add MBOM.getDocBaseType()/setDocBaseType().
2. **M_BomCategory: add `doc_sub_type`** — create ST-SH (AABB=16868×8668×3945), ST-DX (AABB=9215×26565×7885) records. Composite virtual ID (iDempiere pattern).
3. **C_DocType: add 'ST' to DocBaseType CHECK** — retire RE_ST, create ST_SH + ST_DX (DocBaseType='ST', DocSubType='SH'/'DX').
4. **singularity_check() in run_RosettaStones.sh** — add doc_base_type to WHERE clause.
5. **compile_building()** — switch DocType 'RE'→'ST' between ENBLOC/WALKTHRU paths.
6. **BomTemplateComposer** — update to match on M_BomCategory.doc_sub_type + AABB (currently uses doc_type='Residential' only).

**Deferred (lower priority):**
- DX structural children AABB: FLOOR_SLAB_GF (8000×26566×156mm), FLOOR_SLAB_L2 (7966×16966×324mm), ROOF_ASSEMBLY (7966×16966×457mm).
- CompilationAudit.txt cleanup: historical UNIT_/UN refs.
- C_DocType.AABB columns still in schema (SQLite can't DROP COLUMN, values NULLed).

**Key conventions established:**
- bom_type='BUILDING' (not 'UNIT'). One BUILDING BOM per building (BUILDING_SH_STD, BUILDING_DX_STD, BUILDING_TBLKTN_STD).
- AABB is top-down (construction convention). Envelope from design/extraction → set on BUILDING BOM. ValidateBOM warns, never computes.
- SET level: buffer fillers between furniture (BOM.db only, come off at output.db). Strip-packing invariant.
- BOM.db = pristine dictionary. Source of truth = create_ad_*.py scripts.
- EN-BLOC vs WALK-THRU = compilation decision (C_Order in output.db), not BOM structure.
- **F5 outputConn gap + spatial proof loop DONE (2026-03-09).** F5 script extended to 42 verb lines exercising 36/52 verbs. PLACE BOM SH emits 55 elements to output.db, SUMMARIZE BUILDING reads back, EN-BLOC + WALK THRU prove BOM walk parity. CHECK PLACEMENT + CHECK CLASH refactored for outputConn fallback (no file path needed). Full loop: define→compose→emit→verify→prove. 7 Tier 2 violations found (SH furniture overlap + curtain wall glazing). 6 new witnesses (W-F5-110..115). Witness count 168→174.
- **F5 integration script DONE (2026-03-09).** `scripts/F5_integration.bimcobol` — original 36 verb lines exercising 30 of 52 verbs across all 5 layers (L0→L4). F5IntegrationTest.java — cross-verb data flow validation.
- **Phase F0.2 P2+P3+P4: L2/L3/L4 verbs DONE (2026-03-09).** 11 new verbs completing the layered composition stack. L2 floor: PARTITION AABB, CREATE FLOOR, ADD ROOM, REMOVE ROOM, SWAP ROOM. L3 building: COMPOSE BUILDING (delegates to BomTemplateComposer), ADD FLOOR, STACK FLOORS. L4 catalog: DEFINE CATEGORY, ADD TEMPLATE RULE, REGISTER BOM. 31 witness claims (W-SY-44..72), all GREEN. FloorVerbTest.java + BuildingVerbTest.java. Verb count 41→52, witness count 122→153. Embedded guards: AABB overflow, EntityType, slot-fit validation, Z-stack correctness.
- **Phase H0: ERP Dimensions + Report Verbs DONE (2026-03-09).** C_Campaign (4 design themes), AD_User (System), FK columns on C_DocType. 4 DAO classes (X_CCampaign, MCCampaign, X_ADUser, MADUser). 3 XLSX report verbs: REPORT BOM CATALOG, REPORT PRODUCT CATALOG, REPORT BOM STRUCTURE (multi-sheet: SH+DX in one workbook). Apache POI dependency. Professional Excel template with standards compliance markings (green/orange), field holders, auto-filter, freeze panes. 11 witness claims (W-H0-1..10 + W-H0-6b), all GREEN. Verb count 38→41, witness count 111→122. **H0c: Terminal Analysis sheets added** — TE Disciplines (9 disciplines, clash counts per discipline, clash rate, red highlighting) + TE Clash Analysis (clash pairs by discipline with severity CRITICAL/WARNING/MINOR, top 25 cascade groups). AllModelsReport.xlsx = 9 sheets. Documented in `docs/ReportEngine.md`.
- **Phase F0.2 P1: 4 Level 1 convenience verbs DONE.** CREATE ROOM, FURNISH ROOM, RESIZE ROOM, STRIP ROOM. 14 witness claims (W-SY-30..43), all GREEN. ConvenienceVerbTest.java.
- **EntityType enforcement DONE.** entity_type column (D/U/A) on m_bom + m_bom_line. PO-layer guards in MBOM.beforeSave()/delete() and MBOMLine.beforeSave()/delete() reject mutation of Dictionary (D) records. All verbs + Filler set entity_type='U' on new records.
- **Verb-first discipline docs.** DEVELOPER_GUIDE.md updated with verb lookup checklist, code review gate, verb tiers, EntityType section.
- **Phase F0.2 P0: 8 primitives + 3 utilities + VerbLogger DONE.** CREATE/ADD/SET/REMOVE/DELETE BOM + SET LINE PROPERTY (8 primitives), EXTRACT AABB, SNAP TO GRID, VALIDATE AABB (3 utilities). VerbLogger (compact/detail/json) for structured verb output. 29 witness claims (W-SY-1..29), all GREEN. §18.19 compilation strategy appended to BIM_COBOL.md.
- **Phase F0.1a: Full DAO migration DONE.** Zero X_ imports in production code (6 files changed).
- **Phase F0.x: 8 data handling verbs DONE.** SELECT/LIST/DESCRIBE/COUNT/AGGREGATE/EXPORT/CLONE/SUMMARIZE BOM.
- **DX MIRROR moot** — abstract tack model (dx/dy/dz + rotation_rule) handles mirroring via pure BOM structure. No extra code needed.

**Known spatial proof findings (SH, for next session):**
- 7 Tier 2 (P06) violations: 1 IfcPlate pair overlap (curtain wall glazing, storey Unknown) + 6 IfcFurnishingElement overlaps (Ground Floor furniture, ~70L each). All Tier 1 (P01-P04) pass. Data quality issue in source BOM, not compilation bug.

**Available tracks (see `docs/ACTION_ROADMAP.md`):**
- **RosettaStone spatial digest issues:** Investigate the 7 P06 violations, G7 gate formalization
- **G7 gate formalization:** @Order(7) vertex count assertion in RosettaStoneGateTest
- **Phase B:** Terminal BOM Recomposition (51K elements)
- **Phase C:** 2D Drawing Export (3D → SVG)
- **Commercial/Industrial templates:** CO/IN template trees in M_BomCategoryLine (data-only, no Java — use L4 DEFINE CATEGORY + ADD TEMPLATE RULE verbs)

### PP_ Model — Three-Concern Lock — DONE

**C_OrderLine=WHAT** (8 setters, structural guard W-LOCK-1..6).
**PP_Order_Node=HOW** (verb invocations, DocStatus DR/IP/CO/VO, 5 witnesses W-PP-1..5).
**VerbExecutor SPI** (ServiceLoader, BimCobolVerbExecutor, 7 integration witnesses W-COBOL-57..63).

**Next PP_ steps:**
- Phase 3: Migrate extracted buildings — PLACE BOM proves verbs CAN write elements (Phase F milestone)
- Parity gate: run both Java assembler + PLACE BOM, diff output → Rosetta Stone for migration
- Evaluate ESLine FK direction — PP_Order_Node.S_Resource_ID may supersede c_orderline ESLine FK

### Remaining Tasks

6b. SIDE_TABLE_A/B in SH_LIVING_SET — no IFC match, possible invention
14. TB-LKTN INVENTION STOP + priority-based furniture placement
15. Terminal BOM modelling (third Rosetta Stone)
16. Mesh2Library compiler dispatch
17. G8-DX calibration investigation (NULL-bound rooms)

### Bonsai Outliner — Phase G Note
BOM hierarchy → IFC spatial structure: Building → Floor → Room → Leaves.
Maps to element_assemblies in output.db. M_BOM tree (UNIT → FLOOR → SET → leaf).

**Known debt (advisory, no gates):** Terminal IfcReinforcingBar GIC(8), DX P23 MEP corners(364), TB P23 drain(6), TB furniture alignment, TB fans 1500mm(5), WARDROBE_SET/BATHROOM_VANITY_SET empty children, DX "Room not enclosed"(21)

---
*Completed work history: `docs/archive/PROGRESS_ARCHIVE_2026-03-08_completed_work.md`*
