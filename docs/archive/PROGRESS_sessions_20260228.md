# Archived Session Logs — 2026-02-28 NORM + ST + F/E/D phases

> Archived from PROGRESS.md after NORM-3b closeout.
> All phases in this file are complete. Gate: 216 PASS / 1 RED / 1 SKIP.

---

## NORM Normalisation Backlog — All Phases Complete

Sequence: NORM-0a → NORM-0b → NORM-1 → NORM-2 → NORM-3a → NORM-3b ✅

Original backlog spec kept here for reference.

### Phase NORM-0 — Component type discriminator + CO_EmptySpaceLine anchor

#### NORM-0a — `component_type` on M_BOM_Line
Added `component_type TEXT NOT NULL DEFAULT 'MAKE'` with three values (BUY/MAKE/PHANTOM).
Populated all 214 rows. isLeaf()/isNestedBom() now dispatch via getComponentType().
Migration: `migration/migration_NORM0a_component_type.sql`

#### NORM-0b — `CO_EmptySpaceLine.c_orderline_id`
Added nullable `c_orderline_id INTEGER` to `co_empty_space_line` DDL in BuildingWriter.
Logical FK output.db → BOM.db c_orderline(id). NULL for all current rows.
`X_CO_EmptySpaceLine` +getCOrderlineId/setCOrderlineId.

### Phase NORM-1 — ad_product_dim → M_Product
`ALTER TABLE ad_product_dim RENAME TO M_Product`. Added `component_id` + `bom_id` FKs.
31 MAKE stubs inserted (is_active=0, dims=0.001 sentinel). 88 rows total.
New: `X_MProduct.java`, `MProduct.java`. Deprecated: `X_AdProductDim`, `M_AdProductDim`.
Migration: `migration/migration_NORM1_M_Product.sql`

### Phase NORM-2 — child_product_id unified FK
`child_product_id TEXT → M_Product` replaces `child_bom_id / product_ref / child_ifc_class`.
`allocated_*_mm` replaces `space_*_mm`. 34 new M_Product stubs (9 PHANTOM + 25 STRUCTURAL). 122 rows total.
`MBOMLine.getChildBomId()` backward-compat = getChildProductId() when MAKE.
Migration: `migration/migration_NORM2_child_product_id.sql`

### Phase NORM-3a — BOM Walker + Visitor Pattern
`BOMAssemblerAD` deleted → `BOMWalker` + `AssemblyStructureVisitor` (bom/walker/).
`SpatialPlacementVisitor` created (Phase C parity baseline, delegates to RelationalResolver).
RelationalResolver @Deprecated but still live (PlacementAD + CompilerContractTest).
PlacementAD.consumed registry still live (StoreyCompiler.markConsumed + BuildingWriter.isConsumed).

### Phase NORM-3b — CO_EmptySpace Collapse Assessment
Decision: KEEP. See PROGRESS.md for full reasoning.

---

## Session Logs

### ✅ SESSION COMPLETE — Phase NORM-2: child_product_id + space→allocated (2026-02-28)

**Result: 207 PASS / 1 RED / 1 SKIP** (gate unchanged)

iDempiere pattern: single `child_product_id → M_Product` FK replaces the three-way split
(`child_bom_id`, `product_ref`, `child_ifc_class`). `component_type` (BUY/MAKE/PHANTOM)
dispatches resolver path. `space_*_mm` renamed to `allocated_*_mm`.

**Schema changes (BOM.db):**
- `m_bom_line.child_product_id TEXT REFERENCES M_Product(product_id)` — unified FK
- `m_bom_line.component_type TEXT` — BUY/MAKE/PHANTOM discriminator
- `m_bom_line.allocated_width/depth/height_mm` — renamed from `space_*_mm`
- `M_Product.ifc_class TEXT` — IFC element type for STRUCTURAL stubs (IfcSlab, IfcWall…)
- Dropped from m_bom_line: `child_bom_id`, `product_ref`, `child_ifc_class`, `space_*_mm`
- New M_Product stubs: 9 PHANTOM + 25 STRUCTURAL = 34 new rows. Total: 122 rows.
- All 214 m_bom_line rows have `child_product_id` set (PHANTOM rows: role name as sentinel)

**Java changes:**
- `X_M_BOMLine` — removed old columns, added child_product_id + component_type + allocated_*_mm
- `X_MProduct` — added ifc_class getter/setter
- `MBOMLine` — +getChildBomId() compat (= getChildProductId() when MAKE); space→allocated rename
- `MBOM` — space→allocated in computeTotalChildSpace()
- `Filler` — space→allocated getters/setters (6 replace_all)
- `BOMTreeLoader.BOMChild` — childBomId/productRef fields → childProductId/componentType; backward-compat accessor methods added; Ifc-prefix heuristic in productRef()
- `BOMAssemblerAD` — loads M_Product ifc_class map; derives childIfcClass from map
- `RelationalResolver` — comment-only updates (uses MBOMLine.getChildBomId() compat)
- `CompilationPipeline` — getChildBomId() → getChildProductId()
- `TopologyWriter` — SQL updated: child_bom_id→child_product_id, space_*→allocated_*
- `BuildingInspector` — getProductRef()→getChildProductId(); preflightCheckA SQL updated

**Tests updated:** BomChainIntegrityTest (R5a/R5b), BuildingInspectorTest (W-OWNER-1, W-SPACESIZE-1), TopologyBatchProcessTest (space→allocated)

**Migration:** `migration/migration_NORM2_child_product_id.sql`
**Bootstrap:** `migration/migration_topology_maker_bootstrap.sql` — full rewrite for NORM-2 columns
**Docs:** `bim_architecture_viz.html` ERD updated; `library/schema_snapshot_bom.sql` regenerated (1176 lines)

**Key fix:** `BOMTreeLoader.BOMChild.productRef()` — Ifc-prefix check prevents structural BUY stubs from being treated as catalog product IDs.

---

### ✅ SESSION COMPLETE — Phase NORM-1: ad_product_dim → M_Product (2026-02-28)

**Result: 207 PASS / 1 RED / 1 SKIP** (gate unchanged)

**Schema changes (BOM.db):**
- `M_Product.component_id INTEGER` — logical FK → component_library.component_definitions(id). Populated for 16 BUY products.
- `M_Product.bom_id TEXT REFERENCES m_bom(bom_id)` — FK for assembly stubs.
- 31 M_Product stubs inserted (is_active=0). dims=0.001 sentinel.
- `ALTER TABLE ad_product_dim RENAME TO M_Product`. Total rows: 88.

**Java changes:** New `X_MProduct.java` + `MProduct.java`. Deprecated `X_AdProductDim` / `M_AdProductDim` aliases. 6 SQL strings + 4 class references updated.

**Migration:** `migration/migration_NORM1_M_Product.sql`

---

### ✅ SESSION COMPLETE — Phase NORM-0b: c_orderline_id FK on co_empty_space_line (2026-02-28)

**Result: 207 PASS / 1 RED / 1 SKIP** (gate unchanged)

Added `co_empty_space_line.c_orderline_id INTEGER` (nullable) to BuildingWriter.java DDL.
`X_CO_EmptySpaceLine` +getCOrderlineId/setCOrderlineId.
Documented in ConstructionAsERP.md §1.3 + §3.4.

---

### ✅ SESSION COMPLETE — Phase NORM-0a: component_type Discriminator (2026-02-28)

**Result: 207 PASS / 1 RED / 1 SKIP** (gate unchanged)

Added `m_bom_line.component_type TEXT NOT NULL DEFAULT 'MAKE'`. Populated all 214 rows:
131 BUY (37 product_ref + 94 child_ifc_class structural) / 57 MAKE / 26 PHANTOM.

`X_M_BOMLine` +getComponentType/setComponentType. `BOMAssemblerAD.BOMChild` reimplemented isLeaf/isNestedBom via isType(). `MBOMLine` +isPhantom().

**Migration:** `migration/migration_NORM0a_component_type.sql`

---

### ✅ SESSION COMPLETE — Phase ES-1: EmptySpaceChecksum Verification Gate (2026-02-28)

**Result: 207 PASS / 1 RED / 1 SKIP** (new gate #3 active)

Single level-0 CO_EmptySpaceLine checksum: owner-matched builds hash the top-level acceptance line as a 16-char SHA256 prefix.

**New:** `SpatialDigest.computeEmptySpaceChecksum(dbPath)` — hashes bom_level=0 line only.
**Extended:** `CompilationContext` + `PipelineResult` + `BuildingRegistry.BuildingEntry` + `X_C_Order` (empty_space_checksum column). `BuildingRegistryTest` Gate #3 added.
**Foundation:** level-0 (hash verified) + level-1 (structural audit trail) lines both produced.

---

### ✅ SESSION COMPLETE — Phase ST-1b: Aspect Columns + DX Composition Proof (2026-02-27)

**Result: 207 PASS / 1 RED / 1 SKIP** (+1 new witness: W-COMPOSE-DX)

3 new columns on `M_BomCategoryLine`: `num_units`, `storey_count`, `mirroring_rule`.
DX template branch: RE→PR(seq=15, num_units=2)→2×HU→{L1,L2}→rooms (12 new lines).
New: `BomTemplateComposer.java` — composition walker with catalog-wide best-fit selection.
Extended: `MBOM.findBestFitAnyOwner()`. W-COMPOSE-DX proves DX structure from AABB+template alone.
Migration: `migration/migration_phase_ST1b.sql`
Docs: ConstructionAsERP.md §D.4.

---

### ✅ SESSION COMPLETE — Phase ST-0: Standard Mode Foundation (2026-02-27)

**Result: 204 PASS / 1 RED / 1 SKIP** (+2 new witnesses)

`bom_owner → c_bpartner` rename in m_bom + c_order. New: `C_BPartner` table, `M_BomCategoryLine` table.
`c_order` +aabb_width/depth/height_mm (backfilled). `M_BomCategory` +Value +C_BPartner_ID.
New POs: `X_CBPartner/MCBPartner`, `X_MBomCategoryLine/MBomCategoryLine`.
ST_SH dormant c_order entry added (is_active=0, doc_status='DR').
Witnesses: W-CBPARTNER-1, W-CATEGORY-LINE-1.

---

### ✅ SESSION COMPLETE — Phase F3: BOM Buffer Space Fill + Interstitial Fillers (2026-02-27)

**Result: 202 PASS / 1 RED / 1 SKIP** (+3 new tests)

Interstitial filler model: N fixed items → N−1 filler IFC elements between consecutive pairs.
Axis model fix: Width=SUM (strip packing), Depth=MAX (clearance), Height=MAX (clearance).
New: `Filler.java` (ORMSandbox DAO utility): fill/distanceBetween/isStripComplete/partition etc.
`TopologyWriter`: fillBuffers() + fillFloorSetBuffers(). `TopologyBatchProcess` Step 6b wired.
Tests: T6-8 (WARDROBE_SET), T6-9 (BED_SET), T6-10 (LIVING_SET overflow clamp).
Docs: BIMasBOMConcept.md §3+§4.

---

### ✅ SESSION COMPLETE — Phase F2: DX Duplex BOM Restructure (2026-02-27)

**Result: 199 PASS / 1 RED / 1 SKIP** (baseline maintained)

New BOMs: `DUPLEX_SET_STD` (PR), `DUPLEX_SINGLE_UNIT_STD` (HU), `DUPLEX_MEP_TRUNK_STD` (MP, inactive).
π rotation belongs to Unit B (mirrored half), not upper floor.
M_BomCategory: PR/HU/MP added. Test fix: R5b updated for container SETs.
Migration: `migration/migration_phase_F2_dx_duplex_model.sql`

---

### ✅ SESSION COMPLETE — Phase F: Cleanup & Documentation Consolidation (2026-02-26)

**Result: 199 PASS / 1 RED / 1 SKIP** (unchanged — no code changes)

Archived: 11 stale docs + `boq_reports/` + `schedules/` + `EntrussVentures/` to `docs/archive/`.
Archived: 11 dead Python scripts to `scripts/archive/`.
Archived: ~100 applied one-time migrations to `migration/archive/`.
Doc staleness headers added to ARCHITECTURE.md, DEVELOPER_GUIDE.md, PREFAB_ARCHITECTURE.md, etc.

**Canonical Doc Hierarchy:**
- **Tier 1:** ConstructionAsERP.md, PREFAB_ARCHITECTURE.md, METADATA_DRIVEN_ARCHITECTURE.md
- **Tier 2:** BIMasBOMConcept.md, VIEW_CONTRACTS.md, TheRosettaStoneStrategy.txt, WatchDogOnBIMasERP.md, RELATIONAL_PLACEMENT_SPEC.md, LAST_MILE_PROBLEM.md
- **Tier 3:** DEVELOPER_GUIDE.md, USER_GUIDE.md, HARDCODE_AUDIT.md, CODE_WATCHDOG.md

---

### ✅ SESSION COMPLETE — Phase E: 3-DB Split (2026-02-26)

**Result: 199 PASS / 1 RED / 1 SKIP** (from 197/1/3 — 2 tests re-enabled)

- **component_library.db** → Pure LOD geometry store (~12 tables, `lod_*` prefix)
- **BOM.db** → Unified working database (~73 tables: `ad_*` config + `m_*` BOM)
- **Output DBs** → Compiled results (unchanged)

Migration: `migration/migration_phase_E_lod_extract.sh`. 52+ Java files updated. 5 dual-connection files simplified to 1. Cross-DB ATTACH pattern for spanning queries.

---

### ✅ SESSION COMPLETE — Phase D Cleanup: Zero-Risk Table Renames (2026-02-26)

**Result: 197 PASS / 1 RED / 3 SKIP** (baseline maintained)

Dropped: `ad_space_type_furniture`. Renamed: `ad_ref_value→ad_ref_list`, `ad_compiler_config→ad_sysconfig`. Dropped 3 broken views.
Migration: `migration/migration_phase_D_cleanup.sql`

---

### ✅ DONE — Phase AD-2 Part 1 + Phase G-1 Steps 1-4 (2026-02-26)

**AD-2 Part 1:** 10 raw JDBC sites → ModelQuery/PO in BOMAssemblerAD, FloorPlateBOMResolver, CompilationPipeline, ComponentLibrary.

**G-1 Step 1:** FurnitureBOMResolver → BOMTierResolver. FixtureWorker eliminated. ceilingZ threaded.
**G-1 Step 2:** BOMTreeLoader.java extracted (shared AD-layer tree loader). 3-table authority enrichment.
**G-1 Step 3:** FurniturePlacer intermediary deleted. FurnitureWorker.execute() → direct BOMTierResolver.
**G-1 Step 4:** FurniturePlacer.java + FurnitureTypeResolver.java deleted. StoreyCompiler fallback removed.
Migration for G-1: `migration/migration_G1_fixture_bom_enrich.sql`

---

*Older sessions (Phase 108–122, Phases A–C, SH/DX Gap Resolution, Phase 4 BOM.db Extraction): see git log.*
