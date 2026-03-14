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

**Pipeline:** 9 stages. 63 verbs, 196 witnesses. Seal v9 (74 files INTACT).

**Rosetta Stone Buildings:**

| Building | Mode | Elements | Status |
|---|---|---|---|
| Ifc4_SampleHouse (SH) | EN-BLOC | 55 | GREEN (7/7) |
| Ifc2x3_Duplex (DX) | EN-BLOC | 1099 | GREEN (7/7) |
| SJTII_Terminal (TE) | EXTRACTED | 48,428 | Phase B — TE-1 done |

## Recently Completed (2026-03-15)

**Codebase inspection + docs rewrite:**
- Rewrote `docs/StrategicIndustryPositioning.md` for stakeholder readability (~355→210 lines)
  - Added layman BIM/IFC intro (dimensions, market context)
  - Updated scorecard: Federation addon as proven PoC, ERP framework as production foundation
  - Referenced CONCEPTUAL BLUEPRINT + BeyondVerbs roadmap
- Fixed stale verb counts: VerbRegistry Javadoc 14→63, BIM_COBOL.md 57→63
- Full codebase health audit (see below)

**TE-1 — Storey Normalisation + ERP Architecture Design (2026-03-14):**
- Z-centroid storey assignment (7 storeys), REBAR excluded (is_active=0)
- `classify_te.yaml` + ExtractionReader `is_active` filter
- ERP model architecture: discipline hierarchy, verb→AttributeSet mapping,
  ROUTE-as-BOM-tree, Val_Rule via AD tables
  → [`TerminalAnalysis.md`](docs/TerminalAnalysis.md) §ERP Model Architecture
  → [`terminal_erd.html`](docs/terminal_erd.html) (interactive ERD)
- Cross-linked: ConstructionAsERP §11.8, BOMBasedCompilation §2.1.5
- M_BomCategory doc_type scoping: RE=rooms, CO=disciplines, NULL=shared

## Next Session Priorities

1. **TE-2**: ARC envelope decomposition (TILE verb BOM lines for 33K roof plates)
2. **TE-3/4**: STR frame + MEP ROUTE variants (DUCTS, PIPES)
3. See [`TerminalAnalysis.md`](docs/TerminalAnalysis.md) §Verb Roadmap for full plan

## Roadmap

Full roadmap: `docs/ACTION_ROADMAP.md` — 9 phases (0–H), 3 parallel tracks.

| Phase | What | Status |
|-------|------|--------|
| **0** | EN-BLOC Singularity (SH=55, DX=1099) | **DONE** |
| **A** | Rosetta Stone Gate Convergence (G1-G6 GREEN) | **DONE** |
| **B** | Terminal BOM Recomposition (48K elements) | **TE-1 DONE** |
| C | 2D Drawing Export (3D → SVG) | planned |
| D-H | Synthetic Stone, BIM COBOL v1, GUI, ERP | planned |

## Pre-existing Failures (not bugs)

- DAGCompiler: G8-DX calibration ×1 (intentional)
- BIM_COBOL: CoverWithRoof ×3, VerifyPlacement ×1; schema-missing ×61 (pre-existing)
- ORMSandbox: ×18 (schema-missing, pre-existing)
- CO_TE G1-COUNT: -4 (4 IfcSensor metadata-only)

## Known Debt (advisory)

- DX MEP corners (364 fittings without connecting pipes)
- Terminal IfcReinforcingBar GIC(8) — deferred to IfcOpenShell Python
- ~~44 dev scripts~~ DONE — moved to `tests/archive/development/` (2026-03-15)
- Duplicate class name `BIMConstants` (root pkg vs `topology/` pkg in DAGCompiler)
- 5 BOM-targeting migrations in `migration/` top-level now redundant (`{PREFIX}_BOM.db` regenerated from IFCtoBOM pipeline); only `TE_001` is active

---
*Completed work archive: `docs/archive/PROGRESS_ARCHIVE_2026-03-08_completed_work.md`*
*Phase details: search git log for `[DX-1]`, `[QA]`, `[DISC-*]`, `[DOCS]`, `[TE-1]` tags*
