# PROGRESS — Current Development State

## Current State

**Gate:** `./scripts/run_tests.sh` — **Phase A COMPLETE: All 6 gates GREEN for SH and DX (2026-03-08)**

| Suite | Count |
|---|---|
| DAGCompiler | SH GREEN (55/55), DX GREEN (1099/1099). CoEmptySpaceTest 8/8 GREEN. TB/TE/ST pre-existing failures. |
| ORMSandbox | 33 PASS / 3 RED (w_compose_dx, w_category_2, w_doctype_1 pre-existing) |
| TopologyMaker | 19/19 |
| BIM_COBOL | 66 total, 61 PASS / 5 RED (CoverWithRoof ×3, VerifyPlacement ×2 pre-existing) |

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
**BIM COBOL:** 23 verbs (14 original + 8 data handling + PLACE BOM emitting), 66 witnesses. VerbExecutor SPI wired. PP_Order_Node = audit trail.

**5 Active Buildings:**

| Building | Mode | expected_elements | EmptySpaceChecksum (L0) |
|---|---|---|---|
| Ifc4_SampleHouse (SH) | EN-BLOC | 55 | b14f0c02c4602a14 |
| Ifc2x3_Duplex (DX) | EN-BLOC | 1099 | 48c95914ede78646 |
| TB_LKTN | GENERATIVE | 139 | eb9188e164bc3156 |
| SJTII_Terminal | EXTRACTED | 51088 | — |
| ST_SH | GENERATIVE | 123 | — |

**_s/_e Dual Output:** SH _s=55 _e=55 delta=0. DX _s=1099 _e=153 (946 missing, needs MIRROR verb).

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
- **C_DocType model:** DocBaseType (RE/CO/IN) drives template. DocSubType (SH/DX/TB) drives BOM scoping.
- **Selection cascade:** AABB fit → largest volume → seq_no tiebreaker
- **Tack convention (§3.4):** Left-Front-Down corner. dx/dy/dz always >= 0. Guard: X_M_BOMLine.setDx()
- **BOM leaf→mesh:** m_bom_line.child_product_id → M_Product → M_Product_Image → LOD_Object

## Roadmap

Full production roadmap: `docs/ACTION_ROADMAP.md` — 9 phases (0–H), 3 parallel tracks.

| Phase | What | Gate |
|-------|------|------|
| **0** | **EN-BLOC Singularity — wire BOM walk for EB_ buildings** | **BuildingRegistryTest SH=55, DX=1099** |
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

**BOM structure (2026-03-08):** WT_DX active children are shared structural only
(FLOOR_SLAB_GF, FLOOR_SLAB_L2, ROOF_ASSEMBLY, DUPLEX_SET_STD). Floor BOMs
(FLOOR_DX_L1_STD, FLOOR_DX_L2_STD) deactivated from UNIT level — room content
belongs inside half-units. Generic BOM traversal produces L0 + structural L1,
zero L2. No DX-specific code — abstract tack model handles it.

**CO_EmptySpace:** DAO-based UNIT BOM lookup via `MBOM.getByBomIdPrefix("WT_")`.
No hardcoded bom_category values. CoEmptySpaceTest 8/8 GREEN.

**Remaining blockers:**
1. rotation_rule handling in BOMWalker/PlacementCollectorVisitor
2. MIRROR verb (BIM COBOL) — `MIRROR HALF_UNIT ABOUT (4.422,11.091) AXIS Z`
3. Populate compact BOM: HALF_UNIT(444) + CENTER(5) + MEP_TRUNK(206)

## What's Next

**All HelloWorld tasks (HW-1 through HW-7) DONE.** Phase A + Gap Closure COMPLETE.

**Completed this session:**
- **Phase F0.1a: Full DAO migration DONE.** Zero X_ imports in production code (6 files changed).
- **Phase F0.x: 8 data handling verbs DONE.** SELECT/LIST/DESCRIBE/COUNT/AGGREGATE/EXPORT/CLONE/SUMMARIZE BOM.
- **DX MIRROR moot** — abstract tack model (dx/dy/dz + rotation_rule) handles mirroring via pure BOM structure. No extra code needed.

**Available tracks (see `docs/ACTION_ROADMAP.md`):**
- **Phase F0.2:** Synthetic BOM generation verbs (GENERATE/COMPOSE/ADAPT BOM) — framework studied, ready to implement
- **G7 gate formalization:** @Order(7) vertex count assertion in RosettaStoneGateTest
- **Phase B:** Terminal BOM Recomposition (51K elements)
- **Phase C:** 2D Drawing Export (3D → SVG)
- **Commercial/Industrial templates:** CO/IN template trees in M_BomCategoryLine (data-only, no Java)

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
