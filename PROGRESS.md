# PROGRESS — Current Development State

## Current State

**Gate:** `./scripts/run_tests.sh` — **5 contract failures (pre-existing). SH compilation GREEN.**

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
| G4-TAMPER | PASS | PASS | 0 violations / 12 rules |
| G5-PROVENANCE | PASS | PASS | 55/55 traced, 1099/1099 traced |
| G6-ISOLATION | PASS | PASS | |

**Pipeline:** 9 stages — Metadata, Parse, Compile, Template, Write, Verb(SPI), Digest, Geometry, Prove
**BIM COBOL:** 54 verbs, 186 witnesses (182 PASS / 4 RED pre-existing). F5 integration: 36 verbs exercised end-to-end, 42 verb lines (including PLACE BOM SH emit + CHECK PLACEMENT spatial proofs). VerbLogger (compact/detail/json). VerbExecutor SPI wired. PP_Order_Node = audit trail. EntityType enforcement (D=Dictionary read-only, U=User mutable, A=Application). Report verbs output XLSX via Apache POI.

**5 Active Buildings:**

| Building | Mode | expected_elements | EmptySpaceChecksum (L0) |
|---|---|---|---|
| Ifc4_SampleHouse (SH) | EN-BLOC | 55 | b14f0c02c4602a14 |
| Ifc2x3_Duplex (DX) | EN-BLOC | 1099 | 48c95914ede78646 |
| TB_LKTN | GENERATIVE | 139 | eb9188e164bc3156 |
| SJTII_Terminal | EXTRACTED | 51088 | — |
| ST_SH | GENERATIVE | 123 | — |

**Dual Output (enbloc/walkthru):** SH enbloc=55 walkthru=55 delta=0. DX deferred (SH-first scope).

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

### Pending Issues (next session)

1. **5 contract test failures (pre-existing):**
   - `TranslationChainTest.tc1_shPiano` — expects old world X=0.674, gets -6.825 (needs test update for new coordinate chain)
   - `TranslationChainTest.tc3_oppositeWork` — DX furniture wall separation test
   - `IntraBOMRelativeTest.r1` — SH_GF_STR dx up to 15m flagged (house IS 17m wide — threshold too tight for floor BOMs)
   - `IntraBOMRelativeTest.r2` — DX ROOF_ASSEMBLY dz=6.0 exceeds 4.5m threshold
   - `StallDividerParamsTest` — expects spacing=1.3 from m_attribute, gets 0.0

2. **Rule 8 check: 1 FAIL** — one BOM line exceeds parent AABB (investigate which)

3. **DX extraction** — re-enable in RosettaStoneToBOM.py + RosettaStoneExtract.py after SH gate GREEN

4. **ad_building deprecation** — table empty, no create script. Replaced by C_DocType + m_bom. Remove references.

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
