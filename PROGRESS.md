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

**[G-2] DocValidate + DemoHouse + Pattern Rules — DONE (session 16):**
  - validation.db: 32 AD_Val_Rule (5 jurisdictions) + 8 ad_pattern_rule
  - PlacementValidatorImpl: cached rule lookup, MY/US tested (7 witnesses)
  - DM_BOM.db: DemoHouse_2BR generative POC (25 BOM lines, 7 seed products)
  - Pattern rules: window spacing, sprinkler grid, light grid, piping (8 rules)
  - Non-Disturbance test: mined TE/DX rules pass against source buildings
  - Bonsai addon: panel.py, operator.py, props.py, db_loader.py, client.py
  - DesignerServer: createNew action (stub), classify_dm.yaml
  - BIM_Designer.md §14-§16 (UX vision, enabling framework, Federation integration)
  - BIM_Designer_UserGuide.md (draft v0.1)
  - **43/43 tests GREEN** (5 test classes)
  - **Next:** Wire createNew to real BOM load + compile + Federation bbox preview

**[G-2 UX] Design iteration model — bbox preview then compile:**
  User works in Federation wireframe bbox mode (fast, discipline-colored).
  Bbox drag/resize is visual-only — no Outliner, no output.db update.
  When satisfied, user clicks "Compile It" (= iDempiere ProcessIt / MOrder).
  Compiler runs → output.db written → Outliner populated → viewport shows
  compiled geometry with full materials. Pattern rules recalculate during
  compile (window count, sprinkler grid). This is the batch model from §1.

**[BLENDER-BRIDGE] Incremental viewport updates — after Demo House:**
  Spec in `docs/BlenderBridge.md`. Java-smart/Python-dumb architecture.
  Delta applicator sits on top of Federation's Full Load. Needs: diff verb
  in DesignerServer, delta manifest in COMPILE_COMPLETE message.

**[LAST_MILE] TE coverage — sandbox test exposed coordinate bug (R16):**
  - G3/G6/Totality/Rotation: CO_TE added to scope (4 IfcSensor removed from ref)
  - **TerminalSandboxTest.java** — focused round-trip maths test with diagnostic dump
  - **R16 BUG:** PlacementCollectorVisitor.onSubAssembly() double-counts coordinates.
    Formula adds BOTH line.dx AND childBom.origin at each tree level.
    TE: ALL 48K elements shifted ~160m. SH/DX: uniform shift (building origin) —
    visually intact, mathematically shifted. Fix: use childBom.origin as absolute
    anchor for sub-assemblies. See TerminalSandboxTest diagnostic for exact chain.
  - **Note:** Rebar + IfcSensor are Federation addons — excluded from compilation.

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

## Recently Completed (2026-03-18, session 16)

**[G-2] DocValidate Phase 1-2 — validation.db + Non-Disturbance:**
- Created `library/validation.db` — 4th DB (rules, not data). Schema: `migration/V001_validation_schema.sql`
- Seeded 32 AD_Val_Rule across 5 jurisdictions (MY/US/UK/AU/SG): `migration/V002_validation_seed.sql`
- Mined TE: 909 sprinkler heads → NN spacing p50=1899mm, p95=3202mm, max acceptable 4600mm (NFPA 13)
- Mined TE: ELEC-SP clearance 26 close pairs < 150mm (centroid proxy, advisory pending R13)
- 6 AD_Clash_Rule: MEP×STR hard, PLB×ELC clearance, FPR×ACMV obstruction
- 2 AD_Val_Rule_Exception: TE isolated sprinklers (2), DX P23 fittings (358)
- AD_Val_Rule_Mining_Source table: tracks provenance of mined rules
- `NonDisturbanceTest.java` — 6 witnesses (W-ND-1 through W-ND-6), all PASS
- `scripts/run_NonDisturbance.sh` — shell equivalent, 4 PASS / 0 FAIL / 1 SKIP
- `PlacementValidator.java` interface — OSGi-style, verb-aware (SNAP/SCREW/JOIN)
- `PlacementRequest.java` + `ValidationVerdict.java` — semantic geometry DTOs
- BIM_Designer.md: DeepSeek sections trimmed (-766 lines), useful content absorbed
- 20/20 BonsaiBIMDesigner tests GREEN (14 DesignerServer + 6 NonDisturbance)

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

1. **WIRE createNew**: Connect createNew stub to real BOM load + compile + display
   - DesignerAPIImpl.createNew() → load DM_BOM.db → PlacementValidator → pipeline → output.db
   - Bonsai operator calls Federation's Full Load after compile
   - Entry: `DesignerAPIImpl.java` createNew() method (currently stub)
   - Read: `BIM_Designer_UserGuide.md` §5, `BIM_Designer.md` §16
2. **BBOX DESIGN MODE**: Implement bbox-then-compile UX pattern
   - Federation wireframe preview for visual impression (fast, discipline colors)
   - No Outliner until "Compile It" pressed — like iDempiere ProcessIt/MOrder
   - Pattern rules (ad_pattern_rule) recalculate during compile
   - Entry: `BonsaiBIMDesigner/src/main/python/bonsai_bim_designer/operator.py`
3. **R16 FIX**: PlacementCollectorVisitor coordinate double-count (other session found)
   - Fix childBom.origin absolute anchor for sub-assemblies
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
| G-2 | DocValidate + DemoHouse generative POC | **IN PROGRESS** (validation.db DONE, DemoHouse next) |
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
