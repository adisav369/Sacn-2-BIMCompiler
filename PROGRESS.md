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

**[INFRA] Eliminate monolithic BOM.db (96 files):**
- All Java code now reads `System.getProperty("bom.db")` — NO hardcoded paths
- Shell script passes `-Dbom.db=library/_SH_compile.db` per building
- Temp compile DBs: `_SH_compile.db`, `_DX_compile.db` (strongly typed, auto-cleaned)
- `placement_extractor.py`: schema now includes material_name, material_rgba, M_Product_ID
- `I_Element_Extraction` table created in component_library.db (55 SH rows from extractor)
- `ad_geometry_map` → `I_Geometry_Map` (aligned with PO class naming)
- Anti-drift docs added to CompilationPipeline, PlacementLoader, run_RosettaStones.sh

**Prior (2026-03-15): Codebase inspection + docs rewrite**
- StrategicIndustryPositioning.md rewrite, verb count fixes, codebase audit

**TE-1 (2026-03-14): Storey Normalisation + ERP Architecture Design**
- Z-centroid storey assignment, classify_te.yaml, ERP model architecture

## Next Session Priorities

1. **Fix IFCtoBOM StructuralBomBuilder to set child_product_id on leaf lines**
   Current state: IFCtoBOM creates BOM structure but leaves `child_product_id` empty
   on floor-level lines (e.g. SH_GF_STR). The OLD SH_BOM.db had product IDs
   (e.g. `BEAM_W410X60`). Without product IDs, BOMWalker produces 0 placements.
   Fix: `StructuralBomBuilder` must link leaf lines to M_Product records.
   **Read `docs/BOMBasedCompilation.md` before modifying.**
2. **Verify SH Rosetta 7/7 from pristine re-extract** after StructuralBomBuilder fix
3. **Then DX** — same fix, verify 7/7
4. **TE-2**: ARC envelope decomposition (TILE verb BOM lines for 33K roof plates)
5. See [`TerminalAnalysis.md`](docs/TerminalAnalysis.md) §Verb Roadmap for full plan

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
