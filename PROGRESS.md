# PROGRESS — Current Development State

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — **Session 33. SH 10/10, DX 7/10, TE 9/10.**

| Gate | SH | DX | TE |
|------|----|----|-----|
| G1-COUNT | PASS (55) | PASS (1099) | PASS (48428) |
| G2-VOLUME | PASS (+0.00%) | **-0.16%** (MIRROR allocated dims) | **PASS (-0.056%)** |
| G3-DIGEST | PASS | FAIL (G2 drift) | FAIL (seal update needed) |
| G4-TAMPER | PASS (21 rules, T21 new) | PASS | PASS |
| G5-PROVENANCE | PASS (0 GEO_) | PASS (7 checks) | FAIL (1 degenerate geometry, pre-existing) |
| G6-ISOLATION | PASS | PASS | PASS |

**Pipeline:** 9 stages. 63 verbs. Seal v17 (74 files INTACT).
**BonsaiBIMDesigner:** 139/139 GREEN (16 test classes). DemoHouseTest: 6 pre-existing errors (OutputDbPath NOT NULL).

## What's Next

**[DONE] disc_validation.db — Phase 1 (create + seed):**
  DV001 schema (19 tables) + DV002 seed (17 tables, 5613 rows). 9/9 witnesses pass.
  Phase 2 (Java dual-read) and Phase 3 (drop from component_library.db) are future.
  Product catalog gap: 8 residential MEP types (OUTLET, SWITCH, PANEL, TOILET, SINK,
  FLOOR_DRAIN, SMOKE_DETECTOR, EXHAUST_FAN) need M_Product entries — not in Terminal.

**[NEXT] Calibration seed data gaps (3 fixes):**
  1. Rule 803 (ELEC spacing) → V007 INSERT into validation.db (zero impact)
  2. LIGHT per_area_normal=0.05 → DV004 UPDATE in disc_validation.db (zero impact)
  3. FP NN filter → CalibrationDAO WHERE clause (low impact — run before/after)
  Spec: `docs/CALIBRATION_SRS.md` §7. Impact analysis: §7.4. Test: `CalibrationTest.java`.

**[NEXT] DocValidation Rules — PlacementValidator Tier 2+3:**
  ClashDetector (DV-F-13..15) + VerticalContinuityChecker (DV-F-16..17).
  Entry: `docs/DocAction_SRS.md` §4-5.

**[GATE-FIX] Session 32 residual (quick wins for next session):**
  - F1: G3-DIGEST seal update — re-run `verify_test_seal.sh` after CLUSTER format change
  - F2: G5-PROVENANCE — IfcRampFlight LOD has 6 vertices (triangular prism), needs 8+ (box)
  - F3: C9 axis — 7 elements with tie-breaking instability, fix: sort by (X,Y,Z,W,D,H)
  - F4: DemoHouseTest — needs test skip guard when DM_BOM.db missing
  See `TerminalAnalysis.md` §CTFL Review Status for full table.

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
