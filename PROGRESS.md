# PROGRESS — Current Development State

**Last updated:** 2026-02-27 (Phase F3 — BOM Buffer Space Fill)
**Tests:** DAGCompiler **163/165** (G8-DX intentional RED ×1, 1 @Disabled) + ORMSandbox **21/21** | TopologyMaker **18/18** | TOTAL: **202 PASS / 1 RED / 1 SKIP**
**SpatialDigests:** SH=1f325a98 DX=d3c779b9 TB=dd4345f4 Terminal=301b42b1 (stable — SH+DX in scope)

---

### ✅ SESSION COMPLETE — Phase F3: BOM Buffer Space Fill + Interstitial Fillers (2026-02-27)

**Result: 202 PASS / 1 RED / 1 SKIP** (+3 new tests)

Interstitial filler model: N fixed items → N−1 filler IFC elements between consecutive pairs. Fillers are real bounding-box elements that tile every gap in the strip — no strewn furniture, every arrangement confirmed as ground truth.

**Axis model fix:** Width=SUM (strip packing), Depth=MAX (clearance), Height=MAX (clearance). Previously all 3 axes used SUM — wrong for clearance axes.

**New file: `Filler.java`** (ORMSandbox DAO utility):
- `fill(conn, bomId, parentW, parentD, parentH)` — delete old trailing buffers, create N−1 interstitial fillers, renumber sequences
- `newFiller()` — factory for buffer MBOMLine
- `distanceBetween()` / `distanceBetweenRoles()` — measuring tape between any two items
- `isStripComplete()` — ground truth check: total strip width == parent width
- `computeRemainingWidth()`, `totalStripWidth()`, `clearanceEnvelope()`, `describeStrip()`, `partition()`

**TopologyWriter changes:**
- `fillBuffers(setBomId, parentW, parentD, parentH)` — raw JDBC interstitial filler creation (delete old → renumber fixed → INSERT N−1 fillers at seq 20, 40, 60, …)
- `fillFloorSetBuffers(parentBomId)` — traverse FLOOR→ROOM→SET to find and fill all buffer-enabled SETs

**TopologyBatchProcess:** Step 6b wired after `generatePrefabBoms()` — fills buffer space for room SET BOMs

**MBOM.java axis model fixes:**
- `isSpaceSizeValid()` → Width=SUM, Depth=MAX (≤), Height=MAX (≤)
- `fillSpaceBufferChildren()` → delegates to `Filler.fill()`
- `computeTotalChildSpace()` → Width=SUM, Depth=MAX, Height=MAX

**Tests:** T6-8 (WARDROBE_SET exact fit), T6-9 (BED_SET positive fill), T6-10 (LIVING_SET overflow clamp)

**Docs:** BIMasBOMConcept.md §3+§4 updated with interstitial model + axis semantics. DEVELOPER_GUIDE.md updated with Filler entity.

---

### ✅ SESSION COMPLETE — Phase F2: DX Duplex BOM Restructure (2026-02-27)

**Result: 199 PASS / 1 RED / 1 SKIP** (baseline maintained — compiler path unchanged)

Correct 2-in-1 duplex model: two mirrored half-width homes sharing a party wall, not L1+L2 floors. π rotation belongs to Unit B (mirrored half), not upper floor.

**New BOMs:**
- `DUPLEX_SET_STD` (SET, PR) — pair container: UNIT_A rotation=0, UNIT_B rotation=π
- `DUPLEX_SINGLE_UNIT_STD` (FLOOR, HU) — one half-unit, 9 rooms flat across both storeys
- `DUPLEX_MEP_TRUNK_STD` (SET, MP) — MEP placeholder (is_active=0; 904 IFC elements in Rosetta Stone, activate when MEP BOM decomposition populates children)

**BOM lines added:**
- 2 under UNIT_DUPLEX_STD: MEP_TRUNK (inactive), PAIR
- 2 under DUPLEX_SET_STD: UNIT_A (rotation=0), UNIT_B (rotation=π)
- 9 under DUPLEX_SINGLE_UNIT_STD: LIVING, DINING, KITCHEN_GF, BATHROOM_GF, BEDROOM, MASTER, WARDROBE, BATHROOM_UF, KITCHEN_UF — space dimensions copied from existing L1/L2 lines

**Descriptions updated:** UNIT_DUPLEX_STD, FLOOR_DX_L1_STD (→ "[Legacy compiler path]"), FLOOR_DX_L2_STD (→ "[Legacy compiler path]"), DUPLEX_BATHROOM_SET

**M_BomCategory:** PR (Pair), HU (Half-Unit), MP (MEP) added to lookup

**Test fix:** R5b updated: SET BOMs now valid with nested BOM children (container SETs), not only leaf children

**Compiler safety:** DUPLEX_SET_STD (bom_level=SET) not loaded by loadBomChain() — compiler iterates legacy LEVEL_1/LEVEL_2 path unchanged. c_orderline orientation=π on L2 untouched.

**Migration:** `migration/migration_phase_F2_dx_duplex_model.sql`

**Next: Phase F3** — compiler refactor to walk PAIR→SINGLE_UNIT tree, deactivate legacy L1/L2, clear c_orderline orientation

---

### ✅ SESSION COMPLETE — Phase F: Cleanup & Documentation Consolidation (2026-02-26)

**Result: 199 PASS / 1 RED / 1 SKIP** (unchanged — no code changes)

Housekeeping pass after Phases G-1 + D + E. No functional changes — only file moves, archives, and doc headers.

**Deleted:**
- `--help` (spurious 0-byte file), `SanityCheckResults.txt` (regenerable)

**Archived to `docs/archive/`:**
- `AUDIT_REPORT_20260225.txt`, 11 stale docs (VIEW_CONTRACTS_OLD, BOMProcessNotes, CurrentState, DSL_Layer, space_solver_research, Mesh2Library, REFACTOR_METADATA_INTEGRITY, REFACTOR_SEALED_TYPES, concept-paper-compliance-gui, WHITEPAPER_UPDATE_NOTES, ADHistory)
- `boq_reports/`, `schedules/`, `EntrussVentures/` (historical deliverables)

**Archived to `scripts/archive/`:**
- 11 dead Python scripts (sprinkler ×3, IFC furniture import, component extraction ×2, DSL/IFC export ×2, removeElementsByType, migrate_tank, merge_duplex)

**Archived to `migration/archive/`:**
- ~100 applied one-time migrations (Phase 108–122, RM-*, BOM-2d, G8, etc.)
- Kept 8 reference migrations + README + gen_dx_grid_rooms.py

**Doc staleness headers added/updated:**
- `ARCHITECTURE.md` — redirect to METADATA_DRIVEN_ARCHITECTURE.md
- `DEVELOPER_GUIDE.md` — 3-DB section trimmed (→ ConstructionAsERP.md reference)
- `PREFAB_ARCHITECTURE.md` — deleted classes listed with replacements
- `TheLocatorBIMConcept.md` — marked as unimplemented concept
- `BIM_APPLICATION_DICTIONARY.md` — table counts may be stale
- `TECHNICAL_BUILDING_GUIDE.md` — Phase 85 era names, cross-ref to §4

**Canonical Doc Hierarchy:**
- **Tier 1 (Architecture):** ConstructionAsERP.md, PREFAB_ARCHITECTURE.md, METADATA_DRIVEN_ARCHITECTURE.md
- **Tier 2 (Specialized):** BIMasBOMConcept.md, VIEW_CONTRACTS.md, TheRosettaStoneStrategy.txt, WatchDogOnBIMasERP.md, RELATIONAL_PLACEMENT_SPEC.md, LAST_MILE_PROBLEM.md
- **Tier 3 (Narrow):** DEVELOPER_GUIDE.md, USER_GUIDE.md, HARDCODE_AUDIT.md, CODE_WATCHDOG.md

---

### ✅ SESSION COMPLETE — Phase E: 3-DB Split (2026-02-26)

**Result: 199 PASS / 1 RED / 1 SKIP** (improved from 197/1/3 — 2 previously disabled tests now pass)

Completed the ConstructionAsERP 3-DB separation:
- **component_library.db** → Pure LOD geometry store (~12 tables, `lod_*` prefix)
- **BOM.db** → Unified working database (~73 tables: `ad_*` config + `m_*` BOM)
- **Output DBs** → Compiled results (unchanged)

1. **Migration script:** `migration/migration_phase_E_lod_extract.sh` — moves 67 tables, renames 5 LOD tables (`ad_geometry_map`→`lod_geometry_map`, `ad_element_placement`→`lod_element_placement`, `ad_parametric_mesh`→`lod_parametric_mesh`, `ad_parametric_mesh_param`→`lod_parametric_mesh_param`, `ad_roof_preset`→`lod_roof_preset`), recreates views
2. **CompilerConfig.java:** `DB_PATH = "library/BOM.db"`, `LIBRARY_DB_PATH = "library/component_library.db"`, removed `BOM_DB_PATH`
3. **52+ Java files updated:** path constant swaps (component_library.db → BOM.db)
4. **5 dual-connection files simplified:** RelationalResolver, BOMTierResolver, TopologyAccessLayer, TopologyWriter, TopologyBatchProcess — all reduced from 2 connections to 1
5. **Cross-DB ATTACH pattern:** ComponentLibrary, BuildingInspector, PlacementProver, MetadataIntegrityTest use SQLite ATTACH for queries spanning both DBs
6. **All `ad_geometry_map` references → `lod_geometry_map`** in SQL strings, comments, javadoc, PO Table_Name
7. **Developer Guide updated** with 3-DB architecture, current table names, updated baselines

---

### ✅ SESSION COMPLETE — Phase D Cleanup: Zero-Risk Table Renames (2026-02-26)

**Result: 197 PASS / 1 RED / 3 SKIP** (baseline maintained, SpatialDigests unchanged)

iDempiere naming alignment + deprecated table cleanup:
1. **Dropped `ad_space_type_furniture`** (37 rows) — FurnitureTypeResolver.java deleted in G-1 Step 4. Removed from MetadataIntegrityTest M13 satellites.
2. **Renamed `ad_ref_value` → `ad_ref_list`** (26 rows) — matches iDempiere `AD_Ref_List`. Updated MEPAD.java + create_ad_mep_schema.py.
3. **Renamed `ad_compiler_config` → `ad_sysconfig`** (2 rows) — matches iDempiere `AD_SysConfig`. Updated CompilerConfig.java.
4. **Dropped 2 broken views** (`v_active_bom_assembly`, `v_component_leaf`) — referenced m_bom tables that moved to BOM.db in Phase 4. Also dropped `v_qualified_bom` (same issue).

**Parked for Phase G-1:** `ad_room_slot` drop — still has 6+ active consumers across 3 modules. Requires query rewrite to `m_bom WHERE bom_category=? AND bom_owner=?`.

**Completed in Phase E:** 3-DB split done. LOD tables renamed to `lod_*` prefix. Working tables moved to BOM.db. See Phase E session above.

Migration script: `migration/migration_phase_D_cleanup.sql`

---

## ⚡ IMMEDIATE — Do This First

### Phase AD-2: AD Infrastructure Upgrade

**Goal:** Harden the AD (Application Dictionary) layer — migrate raw JDBC hotpaths to DAO, create missing PO classes for multi-consumer tables, and clean up the remaining raw JDBC sites where PO classes already exist. This is **prerequisite groundwork** for Phase G-1 (type-blind compilation).

**Current AD coverage:**
- **16 tables have PO classes** (X_/M_ pairs in ORMSandbox + TopologyMaker)
- **44+ tables accessed via raw JDBC only** — many have 3+ consumers
- **3 BOM tables have PO classes but DAGCompiler still uses raw JDBC** (quick wins)

---

**Part 1: ✅ DONE (2026-02-26) — Wire existing POs into DAGCompiler hotpaths**

10 raw JDBC sites → ModelQuery/PO across 4 files:
- `BOMAssemblerAD.java` — 2 sites (X_M_BOM + X_M_BOMLine full-table loads)
- `FloorPlateBOMResolver.java` — 2 sites (X_M_BOMLine filtered + MAttribute.getByBomChild)
- `CompilationPipeline.java` — 2 sites (X_M_BOM.first() + X_M_BOMLine.list() walk)
- `ComponentLibrary.java` — 3 sites (X_AdGeometryMap.first().map(::getGeometryHash))

**Part 2: New PO classes for multi-consumer tables**

These tables are accessed from 3+ files and would benefit from typed access:

| Table | Consumers | New PO | Key methods |
|---|---|---|---|
| `ad_building_grid` | RelationalResolver, PlacementProver, MetadataValidator | `X_AdBuildingGrid / M_AdBuildingGrid` | `getByBuilding(conn, buildingType)`, `getPosition(axis, label)` |
| `ad_wall_face` | RelationalResolver, MetadataValidator (×2) | `X_AdWallFace / M_AdWallFace` | `getByBuilding()`, `getByRoom()` |
| `ad_opening_family` | OpeningBomAD, CatalogValidator, MetadataValidator | `X_AdOpeningFamily / M_AdOpeningFamily` | `getByType()`, `getByFamily()` |
| `ad_space_type` + `ad_space_type_alias` | SpaceTypeAD, ADSession, MEPBOMResolver, SpaceDimResolver | `X_AdSpaceType / M_AdSpaceType` | `getByName()`, `resolveAlias()` — sd_ domain root |
| ~~`ad_space_type_furniture`~~ | **DROPPED** (Phase D Cleanup) — FurnitureTypeResolver deleted in G-1, zero consumers | N/A | N/A |

**Part 3: Reassess DAO RULE carve-outs**

MEMORY's DAO RULE says "raw JDBC only for ad_building_grid, ad_wall_face, ad_room_slot (single-consumer, no PO benefit)." But:
- `ad_building_grid` has **3 consumers** (not single)
- `ad_wall_face` has **3 consumers** (not single)
- `ad_room_slot` is **DEPRECATED** by bom_category+bom_owner — leave as-is

Decision: upgrade `ad_building_grid` and `ad_wall_face` carve-outs to PO.

---

**Execution order:** Part 1 first (zero-risk, existing POs), then Part 2 one table at a time, test gate after each.

**Key constraint:** 170 PASS / 1 RED / 3 SKIP. SpatialDigests unchanged. No geometry changes — this is pure infrastructure.

**Key files:**
- `ORMSandbox/src/main/java/com/bim/ormsandbox/po/` — existing POs + new POs here
- `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java` — m_bom raw JDBC
- `DAGCompiler/src/main/java/com/bim/compiler/dsl/BOMAssemblerAD.java` — m_bom raw JDBC
- `DAGCompiler/src/main/java/com/bim/compiler/library/FloorPlateBOMResolver.java` — m_attribute raw JDBC
- `DAGCompiler/src/main/java/com/bim/compiler/library/ComponentLibrary.java` — ad_geometry_map raw JDBC
- `DAGCompiler/src/main/java/com/bim/compiler/dsl/RelationalResolver.java` — ad_building_grid, ad_wall_face raw JDBC

**Reference:** `docs/METADATA_DRIVEN_ARCHITECTURE.md` §13–14, `CLAUDE.md` DAO RULE section

---

### Phase G-1: AD-Agnostic BOM Iteration (Type-Blind Compilation)

**Goal:** Make the BOM compilation loop treat rooms, storeys, and furniture as **abstract geometry blocks** — the compiler iterates metadata, not types.

**Step 1: ✅ DONE (2026-02-26)** — Unify FixtureWorker into FurnitureWorker
- Renamed `FurnitureBOMResolver` → `BOMTierResolver` (unified resolver for all BOM tiers)
- Renamed `BomTierResolver` → `QualifiedBomCascade` (VIEW_CONTRACTS cascade, freed name)
- Added fixture-param dispatch path in `BOMTierResolver.resolveForRoom()` — three-way: fixture params → GPD → FLOAT
- Ported fixture placement methods from `FixturePlacer` into `BOMTierResolver` (resolveFixtureWall, positionFixtureAgainstWall, resolveFixtureRotation, etc.)
- Enriched `DUPLEX_BATHROOM_SET` BOM data with fixture params + product_ref + SpaceSize
- Removed `FixtureWorker` registrations from `StoreyCompiler` — all BOMs now route through default `FurnitureWorker` factory
- Threaded `ceilingZ` through `FurnitureWorker` → `FurniturePlacer` → `BOMTierResolver`
- Migration: `migration/migration_G1_fixture_bom_enrich.sql`

**Step 2: ✅ DONE (2026-02-26)** — Unified Abstract BOM Compilation Core
- Extracted `BOMTreeLoader.java` — shared AD-layer utility for BOM tree loading (m_bom_line + m_attribute → canonical `BOMNode`/`BOMChild` records)
- iDempiere AD/Model pattern: common tree infrastructure in `BOMTreeLoader` (AD), resolution strategies stay in concrete resolvers (Model)
- `BOMChild` record: 14 fields (id, bomId, role, childBomId, namePattern, productRef, locatorRef, dx, dy, dz, sequence, isVariance, layoutStrategy, params) + `param()`/`paramInt()`/`paramDouble()`/`hasFixtureParams()` accessors
- 3-table authority enrichment in loader: param `name_pattern` overrides column `child_name_pattern`, params `dx`/`x_offset` override column `dx` (same for dy/dz)
- `BOMTierResolver` — removed BOMNode/BOMChild records, delegates to `BOMTreeLoader.load()`, resolver-specific fields (wallRule, backToWall, etc.) derived from params at usage time via `resolvedWallRule()` helper
- `FloorPlateBOMResolver` — removed FloorBOMNode/FloorBOMChild records, delegates to `BOMTreeLoader.load("TYPICAL_CONDO_FLOOR", "CORE_ASSEMBLY")`, param accessors identical API
- Bulk param loading (one query for all m_attribute rows) replaces FloorPlateBOMResolver's per-child `MAttribute.getByBomChild()` calls

**Step 3: ✅ DONE (2026-02-26)** — Kill FurniturePlacer Intermediary
- Deleted `FixtureWorker.java`, `FixturePlacer.java`, `FixturePlacerTest.java` — all dead code since Step 1
- Rewrote `FurnitureWorker.execute()` — direct `BOMTierResolver.resolveForRoom()` → `PlacedElement` (no `FurniturePlacer` intermediary)
- Added `normalizeRole()` — String→String switch with `default → bomRole` (pass-through) replacing `default → GENERIC_SEATING`
- Bug fix: fixture-param roles (TOILET, SINK, EXHAUST_FAN, etc.) now pass through correctly instead of collapsing to "generic_seating"
- Trimmed `FurniturePlacer.java` — removed `bomResolver` field, `toFurnitureInstance()`, and 3 `placeUniversalFurniture` overloads (8/9/10-arg)
- SpatialDigests unchanged — pass-through roles lowercased same as enum names

**Step 4: ✅ DONE (2026-02-26)** — Eliminate fallback code paths
- Deleted `FurniturePlacer.java` — no callers remain (was only called from StoreyCompiler fallback)
- Deleted `FurnitureTypeResolver.java` — no callers remain (was only called from StoreyCompiler fallback)
- Removed StoreyCompiler `!dispatched` fallback block (CANTEEN/SEATING/WORKSTATION dispatch)
- Removed `BOMResolver` + `roomBOMs` map (only consumed by fallback block)
- Removed `addFurnitureToCtx()` method (FurnitureInstance→FixtureSpec converter, only used by fallback)
- Removed `dispatched` variable (only used to gate the fallback)
- Zero digest impact — fallback was unreachable for all 4 current buildings (all room types have ad_room_slot entries)
- Future room types (CANTEEN, CLASSROOM etc.) will need ad_room_slot + BOM data — correct forward path (data-driven)

**Step 5: QUEUED** — Data-drive stall dividers (hardcoded toilet stall logic → BOM data)

**Key constraint:** SpatialDigests stable. 170 PASS / 1 RED / 3 SKIP gate holds.

**Key files:**
- `DAGCompiler/src/main/java/com/bim/compiler/library/BOMTreeLoader.java` (shared AD-layer tree loader)
- `DAGCompiler/src/main/java/com/bim/compiler/library/BOMTierResolver.java` (furniture/fixture Model resolver)
- `DAGCompiler/src/main/java/com/bim/compiler/dsl/FloorPlateBOMResolver.java` (floor plate Model resolver)
- `DAGCompiler/src/main/java/com/bim/compiler/library/FurnitureWorker.java` (direct BOMTierResolver adapter)
- `DAGCompiler/src/main/java/com/bim/compiler/dsl/StoreyCompiler.java` (dispatch hub — no fallback paths)
- `library/BOM.db` (m_bom, m_bom_line, m_attribute)

---

### Phase ST: Standard Mode — bom_owner='ST' (DESIGN)

**Goal:** Owner-agnostic BOM compilation. The 1D Intent: two C_Order fields drive everything — `bom_owner` (WHO) + `AABB` (HOW BIG).

**AABB on C_Order — CONFIRMED** as the governing building definition. Three new columns: `aabb_width_mm`, `aabb_depth_mm`, `aabb_height_mm`. NULL-safe for existing builds.

**Impact:** 1 migration + 4 PO files + 2 Java files + 1 inspector check + 2 tests. Full inventory: `docs/ConstructionAsERP.md` §3.7.2.

**Prerequisites:** Phase G-1 complete (Steps 1-4 done, Step 5 queued).

**7 TODOs:** See `docs/ConstructionAsERP.md` §3.7.1 (ST-1 through ST-7).

---

## Archived Sessions (2026-02-25 → 2026-02-26)

Older detailed session logs trimmed. Key milestones:
- **Phase G-1 Steps 1-4** (2026-02-26): Unified BOMTierResolver, BOMTreeLoader, killed FurniturePlacer+FixtureWorker, eliminated fallback paths
- **Phase AD-2 Part 1** (2026-02-26): 10 raw JDBC sites → ModelQuery/PO in DAGCompiler
- **Phase D Cleanup** (2026-02-26): iDempiere table renames, dropped ad_space_type_furniture + broken views
- **Phase E: 3-DB Split** (2026-02-26): component_library.db (LOD) + BOM.db (working) + output DBs
- **Phase F: Cleanup & Doc Consolidation** (2026-02-26): Archive stale files/scripts/migrations, doc hierarchy
- **SH/DX Gap Resolution A+B+C** (2026-02-25): Room boundaries, DX LOD400 6%→99%, product materials
- **Phase 4: BOM.db Extraction** (2026-02-25): Physical extraction, IsAvailable gate, per-storey CO lines

For full details, see git log or individual migration files in `migration/`.
