# PROGRESS — Current Development State

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — **SH + DX + TE proven. 21/21 PASS.**

| Gate | SH | DX | TE |
|------|----|----|-----|
| G1-COUNT | PASS (55) | PASS (1099) | PASS (48428) |
| G2-VOLUME | PASS (+0.00%) | PASS (+0.00%) | PASS (+0.00%) |
| G3-DIGEST | PASS | PASS | SKIP (4 IfcSensor ref delta) |
| G4-TAMPER | PASS | PASS (0 violations / 20 rules) | PASS |
| G5-PROVENANCE | PASS (7 checks) | PASS (7 checks) | PASS (7 checks) |
| G6-ISOLATION | PASS | PASS | SKIP (CO mode) |

**Pipeline:** 9 stages. 63 verbs, 196 witnesses. Seal v11 (74 files INTACT).

**Rosetta Stone Buildings:**

| Building | Mode | Elements | Status |
|---|---|---|---|
| Ifc4_SampleHouse (SH) | EN-BLOC | 55 | GREEN (7/7) |
| Ifc2x3_Duplex (DX) | EN-BLOC | 1099 | GREEN (7/7) |
| SJTII_Terminal (TE) | EN-BLOC | 48,428 | GREEN (7/7) |

## What's Next

**[G-1] BonsaiBIMDesigner module DONE** — see session 15 below.

**[DOCVALIDATE] Validation module — NEW SESSION (dedicated):**
  Mine RosettaStone buildings for validation rules. These are real engineer-laid
  buildings — their spatial relationships ARE the ground truth for AD_Val_Rule.
  - **Step 1:** Run mining queries (DocValidate.md §7.4) against TE (48K, 9 disciplines)
    and DX (1,099, known P23 issues). Measure sprinkler spacing, conduit clearances,
    penetration patterns → encode as AD_Val_Rule rows in validation.db
  - **Step 2:** Non-Disturbance test — mined rules MUST pass against the buildings
    they were mined from. If TE fails its own derived rules, the rule is wrong
  - **Step 3:** Activate for generative path (DemoHouse_2BR, TB-LKTN)
  - **Docs:** `docs/DocValidate.md` (full spec), `docs/BIM_Designer.md` §4/§13
  - **Key rule:** Validation affects C_OrderLine + ASI instances only — NEVER
    component_library.db or m_bom templates (DocValidate.md §10.1)

**[DEMO-HOUSE] DemoHouse_2BR generative POC — after DocValidate:**
  Prove "Create New" end-to-end: dialog → BOM generation → DocValidate → compile
  → view in Bonsai. Spec in BIM_Designer.md §13. Needs: validation.db seed,
  M_Product catalog (7 seed products), PlacementValidator.java

**[BLENDER-BRIDGE] Incremental viewport updates — after Demo House:**
  Spec in `docs/BlenderBridge.md`. Java-smart/Python-dumb architecture.
  Delta applicator sits on top of Federation's Full Load. Needs: diff verb
  in DesignerServer, delta manifest in COMPILE_COMPLETE message.

**[LAST_MILE] TotalityContractTest for TE** — add CO_TE to GATE_SCOPE.
  TE rebar removed from input (2,660 IfcReinforcingBar deleted 2026-03-18).
  All classes now match exactly. Ready to assert paired elements EXACT.

**[LAST_MILE] R13: DONE** — ExtractionPopulator returns in-memory list,
  ExtractionReader DB methods removed, EXPECTED_ELEMENTS stored in ad_sysconfig.
  Gap 7 fully closed (R11-R15 all DONE). 21/21 PASS.

**[VERB-FIDELITY] Remaining approximate verbs (SKIP, not gating):**
- ROUTE (533 instances, avg 295m): inter-leg position not encoded
- SPRAY (46,712 instances, avg 23m): grid approximation by design

**[VERB-EXT] Extend verb detection coverage:**
- FRAME: 60 instances detected (column grids). Future: ARRAY, STACK, MIRROR
- CLUSTER: replace SPRAY + broken ROUTE with offset-table (no formula → no fidelity error)

**[R4] ST-mode Rosetta Stone** — deferred (synthetic building, dedicated session)

## Recently Completed (2026-03-18, session 15)

**[G-1] BonsaiBIMDesigner module + design specs:**
- New Maven module `BonsaiBIMDesigner/` — Java server (ndjson/TCP port 9876) + Python addon
- Three-layer architecture: DesignerAPI (facade) → DesignerDAO (SQL) → CompileScopeDetector
- StubDataSeeder provides POC data — 14/14 tests GREEN (DAO, API, TCP protocol)
- `docs/BIM_Designer.md` §11 (module spec), §12 (versatility — compiler + Blender compound),
  §13 (DemoHouse_2BR generative POC), Item 2 "Create New" generative entry point,
  building codes as component choosers (jurisdiction drives slider bounds)
- `docs/DocValidate.md` — renamed from VALIDATION_RULE_DESIGN.md, iDempiere DocValidate
  framing, AD_Validation_Result + AD_Val_Rule_Exception schemas, UBBL residential seed,
  world construction standards (MY/US/UK/AU/SG) with AD_Val_Rule SQL, §10.1 ASI/OrderLine
  rule (validation never touches library or templates), OSGi activation analogy
- `docs/BlenderBridge.md` — thin pipe spec. Java-smart/Python-dumb. Delta applicator
  for incremental viewport updates (don't reload 48K when 3 changed). Rides on
  Federation's existing Full Load. Material cache + mesh instancing.
- DeepSeek analysis triaged: useful parts absorbed into DocValidate.md, wrong/redundant
  parts written off (R-tree already exists, DX count wrong, JS doesn't match our arch)

## Recently Completed (2026-03-18, session 14)

**[ANTI-DRIFT] Rebar removal + extraction leak fix + proactive tamper rules:**
- Removed 2,660 IfcReinforcingBar from Terminal_Extracted.db + component_library.db
- Discovered Gap 7: 49K extraction rows leaked into component_library.db (product catalog)
- PlacementLoader was reading world origin from extraction — circular dependency
- R11: m_bom.origin_x/y/z now stores measured LFD corner (was hardcoded 0.0)
- R12: PlacementLoader reads BOM origin, loadWorldOrigin() deleted
- R14: PlacementProver hardcoded building name dispatch removed
- R15: ComponentLibrary deprecated I_Element_Extraction subquery removed
- T18/T19/T20: 3 new proactive tamper rules catch extraction leaks, hardcoded building
  names, and hardcoded zero origins at source scan time (0 violations after fix)
- 21/21 PASS. G4-TAMPER now 20 rules (was 17)

## Prior Session (2026-03-17, session 13)

**[LAST_MILE] Verb fidelity promotion — advisory → gating:**
- `checkVerbExpansionFidelity()` now returns int; pipeline gates on it (step 9b)
- EXACT verbs (TILE, FRAME): gate at ≤5mm — pipeline FAILs if exceeded
- APPROXIMATE verbs (ROUTE, SPRAY): reported as SKIP with `[approximate — CLUSTER pending]`
- TE verified: TILE 0.0mm PASS, FRAME 0.1mm PASS, ROUTE/SPRAY SKIP. 21/21 PASS
- TotalityContractTest for TE: researched, not yet implemented (next session)

## Prior Sessions (2026-03-17, session 12)

**[ANTI-DRIFT] Deep comb + BOM DB hygiene + doc consolidation:**
- Removed ensureProducts() call — dead since R7 (BOMWalker reads compConn)
- M_Product stays in BOM DB for assembly stubs only (BUILDING/FLOOR/SET placeholders)
- Deleted stale: library/BOM.db, TE_BOM (Copy).db, null, 15+ output artifacts
- Canonical naming: ifc4_sample_house → ifc4_samplehouse (27 files, matches YAML)
- Fixed walkthru residue: run_RosettaStones.sh rm -f bare .db after copy
- LAST_MILE_PROBLEM.md: "How to See It Working" debug-message table, mantra R1-R9 DONE
- Doc consolidation: -160 lines duplication → cross-refs to canonical sources
- 21/21 PASS after full regeneration. Seal v10 (74 files INTACT)

## Prior Sessions (2026-03-17, sessions 8-11)

**Phase B: Terminal BOM Recomposition + Verb Factorization:**
- Sessions 8-9: BOM factorization (qty, verb_ref, VerbDetector, DisciplineBomBuilder)
  TE: 48,428 → 1,297 recipe lines (37:1 compression). 4 verbs: TILE/ROUTE/FRAME/SPRAY
- Session 10: TE GUID collision fix, verb fidelity check (BomValidator step 9b)
- Session 11: R8 step-uniformity (ROUTE 34K→533), R9 grouping key fix, populate separation

## Prior Sessions (2026-03-16-17, sessions 1-7)

- Sessions 1-2: Anti-drift hardening, persistent product catalog, docs audit
- Session 3: LAST_MILE R1/R3/R5/R6/R7 — SpatialDiff, TotalityContract, BOMWalker compConn
- Sessions 4-7: TE pipeline (CO mode, DisciplineBomBuilder, gate scope, IfcSlab fix)

## Prior (2026-03-15)

- Java ExtractionPopulator (replaces Python), monolithic BOM.db elimination (96 files)
- Anti-drift doc sweep (29 files), YAMLGuide.md, SH 7/7 from scratch

*Full archive: `docs/archive/PROGRESS_ARCHIVE_2026-03-08_completed_work.md`*
*Phase details: git log tags `[DX-1]`, `[QA]`, `[DISC-*]`, `[DOCS]`, `[TE-*]`, `[R8]`, `[ANTI-DRIFT]`*

## Next Session Priorities

1. **DOCVALIDATE**: Mine RosettaStones for validation rules → validation.db → Non-Disturbance test
   - Read: `docs/DocValidate.md` §7 (mining), §9 (UBBL seed), §11 (world standards)
   - Entry: TE extracted DB (48K elements), DX extracted DB (1,099 elements)
   - Output: validation.db with AD_Val_Rule + AD_Val_Rule_Param seeded from real buildings
2. **DEMO-HOUSE**: DemoHouse_2BR generative POC (BIM_Designer.md §13)
   - Depends on: validation.db, 7 seed products in component_library.db
3. **LAST_MILE R13**: Remove I_Element_Extraction from component_library.db
4. **LAST_MILE**: TotalityContractTest for TE (W-TOT-4)
5. **CLUSTER**: Replace SPRAY + broken ROUTE with offset-table approach

## Roadmap

Full roadmap: `docs/ACTION_ROADMAP.md` — 9 phases (0–H), 3 parallel tracks.

| Phase | What | Status |
|-------|------|--------|
| **0** | EN-BLOC Singularity (SH=55, DX=1099) | **DONE** |
| **A** | Rosetta Stone Gate Convergence (G1-G6 GREEN) | **DONE** |
| **B** | Terminal BOM Recomposition (51K elements) | **DONE** (48,428 elements, 21/21 PASS) |
| **G-1** | BonsaiBIMDesigner module (server + addon scaffold) | **DONE** (14/14 GREEN) |
| C | 2D Drawing Export (3D → SVG) | planned |
| G-2 | DocValidate + DemoHouse generative POC | **next** |
| G-3 | BlenderBridge incremental viewport | planned |
| D-H | Synthetic Stone, BIM COBOL v1, ERP | planned |

## Pre-existing Failures (not bugs)

- DAGCompiler: G8-DX calibration ×1 (intentional)
- BIM_COBOL: CoverWithRoof ×3, VerifyPlacement ×1; schema-missing ×61
- ORMSandbox: ×18 (schema-missing)
- CO_TE G3-DIGEST: SKIP (4 IfcSensor metadata-only)
- CO_TE G6-ISOLATION: SKIP (CO mode)

## Known Debt (advisory)

- Assembly stubs in *_BOM.db M_Product — should migrate to component_library.db
- DX MEP corners (364 fittings without connecting pipes)
- ~~Terminal IfcReinforcingBar~~ — REMOVED from input (Bonsai addon script)
- Duplicate class name `BIMConstants` (root pkg vs `topology/` pkg)
- schema_snapshot_bom.sql still has full M_Product DDL (harmless — compile DB only)

---
*Completed work archive: `docs/archive/PROGRESS_ARCHIVE_2026-03-08_completed_work.md`*
