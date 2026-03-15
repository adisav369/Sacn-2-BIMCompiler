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

**[QA] Anti-drift hardening — SH 7/7 restored from scratch:**
- Root cause: M_Product_ID was NULL in I_Element_Extraction → NULL child_product_id
  on leaf BOM lines → BOMWalker silently produced 0 placements
- `migration_P02_SH_product_link.sql`: ported archived P01 mapping to correct table
- `ProductRegistrar.ensureProductImages()`: auto-creates M_Product_Image in
  component_library.db from I_Geometry_Map join (deterministic, no invention)
- BomValidator: NULL child_product_id → **FAIL** (was WARN) — blocks commit
- IFCtoBOMPipeline: QA now runs BEFORE commit — failures cause rollback
- ExtractionReader: warns about NULL M_Product_ID at read time
- ComponentLibrary.resolveByProduct(): graceful null if table missing (was: crash)
- ASSUMPTION remarks added to 7 IFCtoBOM files (decision-point documentation)
- Lessons-learned headers on IFCtoBOMPipeline, BomValidator, ProductRegistrar
- Verified: `rm SH_BOM.db` → `run_RosettaStones.sh classify_sh.yaml` → 7/7 PASS

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

1. **DX: Apply same product-link pattern as SH**
   SH is proven 7/7 from scratch. DX needs the same treatment:
   - Check if I_Element_Extraction has M_Product_ID populated for DX (building_type='Ifc2x3_Duplex')
   - If NULL: create `migration_P02_DX_product_link.sql` mapping element_ref → M_Product_ID
     (derive mappings from existing data, never invent — same pattern as SH P02 migration)
   - `ensureProductImages()` will auto-create M_Product_Image rows from I_Geometry_Map join
   - Run `rm DX_BOM.db && ./scripts/run_RosettaStones.sh classify_dx.yaml` — must be 7/7
   - **Read ASSUMPTION remarks in IFCtoBOM builders before modifying any code**
   - **Read lessons-learned headers in IFCtoBOMPipeline.java, BomValidator.java, ProductRegistrar.java**
   - **BomValidator now FAILs on NULL child_product_id — pipeline will abort if data is broken**
2. **Verify SH still 7/7** after DX changes (backward compatibility)
3. **TE-2**: ARC envelope decomposition (TILE verb BOM lines for 33K roof plates)
4. See [`TerminalAnalysis.md`](docs/TerminalAnalysis.md) §Verb Roadmap for full plan

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
