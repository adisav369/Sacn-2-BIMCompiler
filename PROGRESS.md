# PROGRESS — Current Development State

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — **SH + DX proven. YAML-driven pipeline.**

| Gate | SH | DX |
|------|----|----|
| G1-COUNT | PASS (55) | PASS (1099) |
| G2-VOLUME | PASS (+0.00%) | PASS (+0.00%) |
| G3-DIGEST | PASS | PASS |
| G4-TAMPER | PASS | PASS (0 violations / 17 rules) |
| G5-PROVENANCE | PASS (7 checks) | PASS (7 checks) |
| G6-ISOLATION | PASS | PASS |

**Dual Output:** SH enbloc=55 walkthru=55 delta=0. DX enbloc=1099 walkthru=1099 delta=0.

**Pipeline:** 9 stages. 63 verbs, 196 witnesses. Seal v9 (74 files INTACT).

**Rosetta Stone Buildings:**

| Building | Mode | Elements | Status |
|---|---|---|---|
| Ifc4_SampleHouse (SH) | EN-BLOC | 55 | GREEN (7/7) |
| Ifc2x3_Duplex (DX) | EN-BLOC | 1099 | GREEN (7/7) |
| SJTII_Terminal (TE) | EXTRACTED | 51088 | Phase B — next |

## Recently Completed (2026-03-14)

**DX-1 — Half-Unit Composition + 6-Element Fix:**
- CompositionBomBuilder: 3-tier mirror partition (485 paired/side + 129 shared = 1099)
- B-side excess bug fixed (excluded all B, should only exclude paired B)
- DuplexAnalysis.md updated, SourceCodeGuide.md +CompositionBomBuilder/DXPipelineTest

**QA — Quality Audit + Abstract G5 Checks:**
- G5-PROVENANCE expanded 5→7 abstract checks (LOD mesh prevalence + surface_styles)
- Rosetta Stone sameness principle documented (coordinate match = geometry guarantee)
- SourceCodeGuide Appendix A: Quality Verification Guide (§A.1-A.7)

**TerminalAnalysis.md — Forensic Analysis:**
- 9 disciplines, element inventory, TILE verb (33K roof from ~20 formulas)
- New verbs needed: TILE, ROUTE, ARRAY, WIRE LIGHTING, FRAME
- qty expansion mechanism, discipline BOM layer, YAML user intent design
- 8 implementation phases (TE-1 through TE-8)

## Next Session Priorities

1. **Terminal BOM (Phase B)** — `classify_te.yaml`, start with TE-1 (storey normalisation).
   See `docs/TerminalAnalysis.md` for full forensics and phase plan.
2. **VerbRegistry refactor** — migrate Python `create_ad_*.py` to Java DAO.
3. **DX scope space origins** — `floor_rooms` empty SETs (no `origin_m`). Data issue.
4. **DX C2-SpotCheck** — 5 per-element cross-DB coordinate proofs.

## Roadmap

Full roadmap: `docs/ACTION_ROADMAP.md` — 9 phases (0–H), 3 parallel tracks.

| Phase | What | Status |
|-------|------|--------|
| **0** | EN-BLOC Singularity (SH=55, DX=1099) | **DONE** |
| **A** | Rosetta Stone Gate Convergence (G1-G6 GREEN) | **DONE** |
| **B** | Terminal BOM Recomposition (51K elements) | **NEXT** |
| C | 2D Drawing Export (3D → SVG) | planned |
| D-H | Synthetic Stone, BIM COBOL v1, GUI, ERP | planned |

## Pre-existing Failures (not bugs)

- DAGCompiler: G8-DX calibration ×1 (intentional)
- BIM_COBOL: CoverWithRoof ×3, VerifyPlacement ×1
- ORMSandbox: ×2
- CO_TE G1-COUNT: -4 (4 IfcSensor metadata-only)

## Known Debt (advisory)

- DX MEP corners (364 fittings without connecting pipes)
- TB-LKTN overproduction (generative path, removed from manifest)
- Terminal IfcReinforcingBar GIC(8)

---
*Completed work archive: `docs/archive/PROGRESS_ARCHIVE_2026-03-08_completed_work.md`*
*Phase details: search git log for `[DX-1]`, `[QA]`, `[DISC-*]`, `[DOCS]` tags*
