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

## Recently Completed (2026-03-16)

**[QA] YAML pipeline anti-drift hardening — 6 FAIL guards, persistent product catalog:**
- 6 new runtime FAIL guards (pipeline aborts on broken data, no silent loss):
  - NULL M_Product_ID → throw at ExtractionReader (was WARN)
  - Unmapped storey pre-flight in IFCtoBOMPipeline (was WARN in StructuralBomBuilder)
  - Geometry completeness pre-flight (products without geometry_hash)
  - Extraction reconciliation: LEAFs + paired != extraction count (accountant check)
  - Element ref provenance: extraction LEAF without element_ref
  - Schema version mismatch: YAML v2 declared but parser v1
- Persistent product catalog: M_Product in component_library.db (INSERT OR IGNORE = reuse)
  - Products persist across BOM rebuilds, reusable across buildings
  - BOM DB gets transitional copy until BOMWalker refactored to read from library
  - Architecture: library = product/geometry/orientation truth, BOM.db = spatial only
- YAMLGuide.md: synced Step 1 with extraction→library chain, pipeline table with
  pre-flight guards, Drift Prevention section (11 enforced guards + 5 honest gaps),
  new-building pre-flight checklist
- SH 7/7, DX 7/7 — no regression

## Prior (2026-03-15)

**[DX] Java ExtractionPopulator — fully deterministic pipeline, no Python/manual SQL:**
- New `ExtractionPopulator.java`: reads reference DB → populates `I_Element_Extraction` with
  `M_Product_ID = element_ref` (deterministic, no invention). Fills geometry gaps by importing
  missing meshes from reference DB into `component_geometries` + `I_Geometry_Map`.
- Replaces `placement_extractor.py` (Python) and `migration_P02_SH_product_link.sql` (manual SQL)
- Pipeline is now: YAML (only invention) → Java extract → link → BOM → validate → compile
- DX: 1099 elements → 78 products, 639 BOM lines (485 half-unit + 129 structural + 18 room)
- SH: 55 elements → 18 products, 65 BOM lines — backward compatible, 7/7 PASS
- Both SH + DX: `rm *_BOM.db → run_RosettaStones.sh` → 7/7 PASS from scratch
- Added YAML-only-invention comments to classify_sh.yaml, classify_dx.yaml
- Created `docs/YAMLGuide.md` — field dictionary, schema reference, invention boundary

**[DOCS] Anti-drift documentation sweep — `BOM.db` → `{PREFIX}_BOM.db` (29 files):**
- Eliminated all bare `BOM.db` references across entire docs tree (active + archive + HTML/ERD)
- DATA_MODEL.md: rewritten for IFCtoBOM Java pipeline, per-building `{PREFIX}_BOM.db` pattern
- SourceCodeGuide.md: Python migration status updated (SH/DX done, TE only), BomValidator added
- TestArchitecture.md: pipeline diagram, Layer 4 references updated
- DEVELOPER_GUIDE.md: verb counts 38→63/110→196, per-building DB pattern, compile DB naming
- BOMBasedCompilation.md §10: IFCtoBOM pipeline references
- bim_architecture_viz.html + terminal_erd.html: ERD labels updated
- ConstructionAsERP.md (91), BIM_COBOL.md (40), archive docs (77) — full sweep
- Corrected stale "transient library/BOM.db" → actual `library/_SH_compile.db` naming

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

1. **BOMWalker refactor**: resolve products from component_library.db (eliminate BOM DB copy)
   - `MProduct.get(bomConn, ...)` → read from compConn; BOM.db = spatial arrangement only
2. **TE-2**: ARC envelope decomposition (TILE verb BOM lines for 33K roof plates)
3. See [`TerminalAnalysis.md`](docs/TerminalAnalysis.md) §Verb Roadmap for full plan
4. Deprecate `tools/placement_extractor.py` and `migration/migration_P02_SH_product_link.sql`
   (replaced by `ExtractionPopulator.java` — both still exist as dead code)

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

- BOMWalker reads M_Product from BOM DB (transitional copy) — target: read from component_library.db
- DX MEP corners (364 fittings without connecting pipes)
- Terminal IfcReinforcingBar GIC(8) — deferred to IfcOpenShell Python
- ~~44 dev scripts~~ DONE — moved to `tests/archive/development/` (2026-03-15)
- Duplicate class name `BIMConstants` (root pkg vs `topology/` pkg in DAGCompiler)
- 5 BOM-targeting migrations in `migration/` top-level now redundant (`{PREFIX}_BOM.db` regenerated from IFCtoBOM pipeline); only `TE_001` is active

---
*Completed work archive: `docs/archive/PROGRESS_ARCHIVE_2026-03-08_completed_work.md`*
*Phase details: search git log for `[DX-1]`, `[QA]`, `[DISC-*]`, `[DOCS]`, `[TE-1]` tags*
