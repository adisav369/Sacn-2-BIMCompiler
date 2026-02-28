# PROGRESS — Current Development State

**Last updated:** 2026-02-28 (Phase ST-1c — Template-driven compilation walker)
**Tests:** DAGCompiler **179/181** (G8-DX intentional RED ×1, 1 @Disabled) + ORMSandbox **25/25** | TopologyMaker **19/19** | TOTAL: **221 PASS / 1 RED / 1 SKIP**
**SpatialDigests:** SH=1f325a98 DX=d3c779b9 TB=dd4345f4 Terminal=301b42b1 (stable — SH+DX in scope)
**EmptySpaceChecksums:** SH=b14f0c02c4602a14 DX=1f6f2018dbda2faa TB=eb9188e164bc3156

---

### ✅ SESSION COMPLETE — Phase ST-1c: Template-Driven Compilation Walker (2026-02-28)

**Result: 221 PASS / 1 RED / 1 SKIP** (+5 new witness tests W-ST-1..4)

**ST_SH is now a live active pipeline entry.** Template-driven compilation fully wired.

**Changes:**
- `TemplateStage` inserted at Step 4 in `CompilationPipeline.STAGES` (skips for non-ST)
- `populateCoEmptySpace` extended: ST mode selects UNIT BOM via GF template owner,
  writes L2 lines from CompositionReport leaf selections, marks CO when composition complete
- `ProveStage` skips for ST mode (no relational placement rules — template proof is enough)
- `BuildingRegistry.BuildingEntry`: added `cbpartner` field
- `BOMChainIntegrityTest.t3`: exempt c_bpartner='ST' buildings (no c_orderline)

**Migration (`migration_ST1c_template_bom.sql`):**
- `FLOOR_SH_GF_STD.bom_category` L1→GF (SH template path fix)
- `ST_SH` in `ad_building` (MetadataValidator requires)
- `ST_SH.is_active=1, expected_elements=123, geometry_fail_threshold=5`

**Design notes:**
- SpatialDigest(ST_SH) ≠ SpatialDigest(Ifc4_SampleHouse): GENERATIVE (123 elems) ≠ EXTRACTED (56 elems)
- Template proof: composition complete (9 selections, 0 gaps) → header marked CO in WriteStage
- UN BOM derived from GF selection owner (SH → UNIT_SH_STD), not AABB-fit across all owners
- L2 lines in co_empty_space_line: 7 leaf selections (SL, GF, LI, BD, DN, KT, BT, RF)

**What's next:** Phase G-1 Step 5 (data-drive stall dividers) or Phase AD-2 Part 2

---

### ✅ SESSION COMPLETE — Phase NORM-3b: CO_EmptySpace Collapse Assessment (2026-02-28)

**Result: 216 PASS / 1 RED / 1 SKIP** (no code changes — assessment only)

**Decision: KEEP `co_empty_space` as a separate table. Do not collapse.**

Assessment against PROGRESS.md NORM-3b criteria:

1. **Can `is_available` move to `c_order.doc_status`?** — No.
   `c_order` lives in BOM.db (design-time authority). `ProveStage` writes the proof result
   using the output.db connection. Moving the quality gate to BOM.db would require a
   second cross-DB JDBC write at prove time — more complex, not less.

2. **Does removing `co_empty_space` simplify the EmptySpaceChecksum witness (Gate #3)?** — No.
   `SpatialDigest.computeEmptySpaceChecksum()` reads only `co_empty_space_line` (bom_level=0).
   It does not touch the `co_empty_space` header at all. Witness complexity is unchanged
   whether the header exists or not. Decision criterion: *only collapse if complexity goes down*.
   It doesn't → keep.

3. **Additional blocker:** `co_empty_space` stores `origin_x/y/z_mm` (actual RTREE-measured
   origin at compile time). `c_order` has `aabb_width/depth/height_mm` (design-time intent)
   but no origin. These can legitimately differ (IFC models are not always at world origin).

4. **FK cleanliness:** `co_empty_space_line.co_emptyspace_id` is a local SQLite FK within
   output.db. Collapsing to `c_order_id` directly turns it into a logical cross-DB reference.

**NORM normalisation sequence fully closed:**
NORM-0a ✅ → NORM-0b ✅ → NORM-1 ✅ → NORM-2 ✅ → NORM-3a ✅ → NORM-3b ✅ (KEEP)

**What's next:** Phase ST-1c (template-driven compilation walker) or Phase G-1 Step 5 (data-drive stall dividers)

---

### ✅ SESSION COMPLETE — Phase NORM-3a: BOM Walker + Visitor Pattern (2026-02-28)

**Result: 216 PASS / 1 RED / 1 SKIP** (+9 new witness tests, SpatialDigests unchanged)

Single BOM walker replaces two independent BOM traversal passes. `BOMAssemblerAD` deleted.
`AssemblyStructureVisitor` + `BOMWalker` are the sole source for element_assemblies.

**New files:**
- `bom/walker/BOMVisitor.java` — visitor interface (onMake, onMakeComplete, onBuy, onPhantom, flush)
- `bom/walker/BOMWalker.java` — tree traversal engine; `walk()` + `walkSelf()` (synthetic root wrapping)
- `bom/walker/AssemblyStructureVisitor.java` — ports BOMAssemblerAD.applyAllRecipes() to visitor pattern
- `bom/walker/SpatialPlacementVisitor.java` — Phase C parallel parity baseline (delegates to RelationalResolver)
- Tests: `BOMWalkerTest.java` (W-WALKER-1..5), `SpatialPlacementVisitorTest.java` (W-SPV-1..4)

**Deleted:** `bom/BOMAssemblerAD.java` — replaced by AssemblyStructureVisitor.

**Remaining debt:**
- `RelationalResolver` still used by `PlacementAD.loadRelational()`, `SpatialPlacementVisitor.compute()`,
  and `CompilerContractTest` reflection. Full replacement deferred (SpatialPlacementVisitor needs
  independent coordinate resolution — Phase D full switch).
- `PlacementAD.consumed` registry still used by `StoreyCompiler.markConsumed()` + `BuildingWriter.isConsumed()`.

---

## Archived Sessions

**All NORM phases complete.** Detailed session logs in `docs/archive/PROGRESS_sessions_20260228.md`:
- NORM-2, NORM-1, NORM-0b, NORM-0a, ES-1 (2026-02-28)
- ST-1b, ST-0, F3, F2, F (cleanup), E (3-DB Split), D Cleanup (2026-02-26 → 2026-02-27)
- G-1 Steps 1-4, AD-2 Part 1 (2026-02-26)

**Older sessions (2026-02-25 → 2026-02-26):** See git log or `migration/` for Phases A–C, SH/DX Gap Resolution, Phase 4 BOM.db Extraction.

---

## ⚡ NEXT — Pending Work

### Phase AD-2 Part 2: New PO classes for multi-consumer tables

These tables are accessed from 3+ files and benefit from typed access:

| Table | Consumers | New PO | Key methods |
|---|---|---|---|
| `ad_building_grid` | RelationalResolver, PlacementProver, MetadataValidator | `X_AdBuildingGrid / M_AdBuildingGrid` | `getByBuilding(conn, buildingType)`, `getPosition(axis, label)` |
| `ad_wall_face` | RelationalResolver, MetadataValidator (×2) | `X_AdWallFace / M_AdWallFace` | `getByBuilding()`, `getByRoom()` |
| `ad_opening_family` | OpeningBomAD, CatalogValidator, MetadataValidator | `X_AdOpeningFamily / M_AdOpeningFamily` | `getByType()`, `getByFamily()` |
| `ad_space_type` + `ad_space_type_alias` | SpaceTypeAD, ADSession, MEPBOMResolver, SpaceDimResolver | `X_AdSpaceType / M_AdSpaceType` | `getByName()`, `resolveAlias()` |

**Note:** `ad_building_grid` and `ad_wall_face` were previously raw JDBC carve-outs (single-consumer assumption). Both have 3 consumers — upgrade to PO.
One table at a time, full test gate after each. Key files: `ORMSandbox/src/main/java/com/bim/ormsandbox/po/`

---

### Phase G-1 Step 5: Data-Drive Stall Dividers

**Goal:** Move hardcoded toilet stall logic in `BOMTierResolver.java` + `StoreyCompiler.java` into BOM data.
**Status:** QUEUED (G-1 Steps 1–4 complete — see archive).
**Approach:** Add stall-count param to m_attribute for TOILET_BLOCK BOMs. BOMTierResolver reads param, no hardcode.
**Key files:** `DAGCompiler/src/main/java/com/bim/compiler/library/BOMTierResolver.java`, `StoreyCompiler.java`

---

### ✅ Phase ST-1c: Template-Driven Compilation Walker — COMPLETE

**BOMCopyStage** (verbatim copy M_BOM tree → C_OrderLine.BOM) deferred to future phase.
ST_SH is a live active pipeline entry. L2–L3 room-level lines future work (ST-2).
