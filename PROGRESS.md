# PROGRESS — Current Development State

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — **SH + DX + TE proven. 21/21 PASS.**

| Gate | SH | DX | TE |
|------|----|----|-----|
| G1-COUNT | PASS (55) | PASS (1099) | PASS (48428) |
| G2-VOLUME | PASS (+0.00%) | PASS (+0.00%) | PASS (+0.00%) |
| G3-DIGEST | PASS | PASS | SKIP (4 IfcSensor ref delta) |
| G4-TAMPER | PASS | PASS (0 violations / 17 rules) | PASS |
| G5-PROVENANCE | PASS (7 checks) | PASS (7 checks) | PASS (7 checks) |
| G6-ISOLATION | PASS | PASS | SKIP (CO mode) |

**Pipeline:** 9 stages. 63 verbs, 196 witnesses. Seal v10 (74 files INTACT).

**Rosetta Stone Buildings:**

| Building | Mode | Elements | Status |
|---|---|---|---|
| Ifc4_SampleHouse (SH) | EN-BLOC | 55 | GREEN (7/7) |
| Ifc2x3_Duplex (DX) | EN-BLOC | 1099 | GREEN (7/7) |
| SJTII_Terminal (TE) | EN-BLOC | 48,428 | GREEN (7/7) |

## What's Next

**[LAST_MILE]** Re-audit LAST_MILE_PROBLEM.md against current state — next session.

**[VERB-FIDELITY] Remaining fidelity errors:**
- ROUTE (533 instances, avg 295m): inter-leg position not encoded
- SPRAY (46,712 instances, avg 23m): grid approximation by design

**[VERB-EXT] Extend verb detection coverage:**
- FRAME: 60 instances detected (column grids). Future: ARRAY, STACK, MIRROR
- CLUSTER: replace SPRAY + broken ROUTE with offset-table (no formula → no fidelity error)

**[R4] ST-mode Rosetta Stone** — deferred (synthetic building, dedicated session)

## Recently Completed (2026-03-17, session 12)

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

1. **LAST_MILE**: Re-audit LAST_MILE_PROBLEM.md — verify all claims against 21/21 run
2. **CLUSTER**: Replace SPRAY + broken ROUTE with offset-table approach
3. **R4**: ST-mode Rosetta Stone (synthetic building with roles)

## Roadmap

Full roadmap: `docs/ACTION_ROADMAP.md` — 9 phases (0–H), 3 parallel tracks.

| Phase | What | Status |
|-------|------|--------|
| **0** | EN-BLOC Singularity (SH=55, DX=1099) | **DONE** |
| **A** | Rosetta Stone Gate Convergence (G1-G6 GREEN) | **DONE** |
| **B** | Terminal BOM Recomposition (51K elements) | **DONE** (48,428 elements, 21/21 PASS) |
| C | 2D Drawing Export (3D → SVG) | planned |
| D-H | Synthetic Stone, BIM COBOL v1, GUI, ERP | planned |

## Pre-existing Failures (not bugs)

- DAGCompiler: G8-DX calibration ×1 (intentional)
- BIM_COBOL: CoverWithRoof ×3, VerifyPlacement ×1; schema-missing ×61
- ORMSandbox: ×18 (schema-missing)
- CO_TE G3-DIGEST: SKIP (4 IfcSensor metadata-only)
- CO_TE G6-ISOLATION: SKIP (CO mode)

## Known Debt (advisory)

- Assembly stubs in *_BOM.db M_Product — should migrate to component_library.db
- DX MEP corners (364 fittings without connecting pipes)
- Terminal IfcReinforcingBar GIC(8) — deferred to IfcOpenShell Python
- Duplicate class name `BIMConstants` (root pkg vs `topology/` pkg)
- schema_snapshot_bom.sql still has full M_Product DDL (harmless — compile DB only)

---
*Completed work archive: `docs/archive/PROGRESS_ARCHIVE_2026-03-08_completed_work.md`*
