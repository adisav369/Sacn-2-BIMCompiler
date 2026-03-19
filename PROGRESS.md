# PROGRESS — Current Development State

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — **Session 32. SH 10/10, DX 7/10, TE 8/10.**

| Gate | SH | DX | TE |
|------|----|----|-----|
| G1-COUNT | PASS (55) | PASS (1099) | PASS (48428) |
| G2-VOLUME | PASS (+0.00%) | **-0.16%** (MIRROR allocated dims) | 13.71% (verb factorization) |
| G3-DIGEST | PASS | FAIL (G2 drift) | FAIL (G2 drift) |
| G4-TAMPER | PASS (21 rules, T21 new) | PASS | PASS |
| G5-PROVENANCE | PASS (0 GEO_) | PASS (7 checks) | PASS (7 checks) |
| G6-ISOLATION | PASS | PASS | PASS |

**Pipeline:** 9 stages. 63 verbs. Seal v17 (74 files INTACT).
**BonsaiBIMDesigner:** 130/130 GREEN (15 test classes).

## What's Next

**[NEXT] disc_validation.db — new database (Phase 1: create + seed):**
  Separate discipline metadata from component_library.db LODs.
  Spec: `docs/DISC_VALIDATION_DB_SRS.md`. Migration: DV001 schema + DV002 seed copy.

**[NEXT] Calibration seed data gaps (3 fixes):**
  1. Rule 803 (ELEC spacing) → INSERT into validation.db
  2. LIGHT per_area_normal=0.05 → UPDATE ad_space_type_mep_bom (in new disc_validation.db)
  3. FP NN filter → head-only NN in CalibrationDAO (IfcFireSuppressionTerminal)
  Spec: `docs/CALIBRATION_SRS.md` §3.3. Test: `CalibrationTest.java`.

**[NEXT] DocValidation Rules — PlacementValidator Tier 2+3:**
  ClashDetector (DV-F-13..15) + VerticalContinuityChecker (DV-F-16..17).
  Entry: `docs/DocAction_SRS.md` §4-5.

**[TACK-FIX] Tested session 25. Results:**
  - TILE: FIXED (0.0000m). DX G2: -0.16% MIRROR dims debt. TE G2: 13.71% verb factorization.
  - BIMLogger wired to CompilationPipeline.

**[VERB-GRAMMAR] Exact replication gap:**
  TILE=lossless, SPRAY/ROUTE=approximate. Next verb: CLUSTER.
  G2-VOLUME: factorized lines use first-element dims (13.66% drift).

**[R4] ST-mode Rosetta Stone** — deferred.

## Session Log (recent → old)

| Session | Date | What | Tests |
|---------|------|------|-------|
| 32 | 2026-03-19 | check_method dispatch + SpatialPredicates + CalibrationTest + DISC_VALIDATION_DB_SRS | 130/130 |
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
| G-7 | Assembly builder (MAKE path) | planned |
| G-8 | BlenderBridge pipe (Snap + incremental) | planned |
| G-9 | ORDER View + BOM Outliner | planned |
| G-10 | Promote to BOM (governance gate) | planned |
| G-11..12 | ParametricMesh UI, Text Mode | planned |
| C–H | Drawing Export, Synthetic Stone, BIM COBOL, ERP | planned |

## Pre-existing Failures (not bugs)

- DAGCompiler: G8-DX calibration ×1 (intentional)
- BIM_COBOL: CoverWithRoof ×3, VerifyPlacement ×1; schema-missing ×61
- ORMSandbox: ×18 (schema-missing)

## Known Debt

- Assembly stubs in *_BOM.db M_Product → should migrate to component_library.db
- DX MEP corners (364 fittings without connecting pipes)
- Duplicate class name `BIMConstants` (root pkg vs `topology/` pkg)
- R27: DONE — C_DocType now written by IFCtoBOM into `{PREFIX}_BOM.db`

---
*Archive: `docs/archive/PROGRESS_ARCHIVE_2026-03-08_completed_work.md`*
*Git tags: `[DX-1]`, `[QA]`, `[DISC-*]`, `[DOCS]`, `[TE-*]`, `[R8]`, `[ANTI-DRIFT]`*
