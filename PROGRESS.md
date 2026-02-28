# PROGRESS — Current Development State

**Last updated:** 2026-02-28 (Phase NORM-3b — CO_EmptySpace collapse assessment)
**Tests:** DAGCompiler **174/176** (G8-DX intentional RED ×1, 1 @Disabled) + ORMSandbox **25/25** | TopologyMaker **19/19** | TOTAL: **216 PASS / 1 RED / 1 SKIP**
**SpatialDigests:** SH=1f325a98 DX=d3c779b9 TB=dd4345f4 Terminal=301b42b1 (stable — SH+DX in scope)
**EmptySpaceChecksums:** SH=b14f0c02c4602a14 DX=1f6f2018dbda2faa TB=eb9188e164bc3156

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

### Phase ST-1c: Template-Driven Compilation Walker

**Goal:** Wire `BomTemplateComposer` into `CompilationPipeline` for the ST_SH dormant building.
Walk `M_BomCategoryLine` tree → create `CO_EmptySpaceLines` → select best-fit BOMs.

**POC gate:** `SpatialDigest(ST_SH) == SpatialDigest(SH)` — proves the engine reproduces
an owner-matched result from template constraints alone.

**Status:** NEXT. Schema foundation (ST-0 + ST-1b) complete. `BomTemplateComposer.compose()` POC proven.
ST_SH entry: `c_order` has `is_active=0`, `doc_status='DR'`. Activate when pipeline supports ST mode.

**Remaining gaps:**

| Gap | What | Status |
|-----|------|--------|
| Template stage in pipeline | Walk M_BomCategoryLine → CompilationPipeline stage | NEXT |
| CO_EmptySpaceLine L2–L3 | Room-level + item-level spatial records | ST-1c |
| BOMCopyStage | Verbatim copy M_BOM tree to C_OrderLine.BOM | Future |

**Key files:**
- `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java`
- `ORMSandbox/src/main/java/com/bim/ormsandbox/po/BomTemplateComposer.java`
- `ORMSandbox/src/main/java/com/bim/ormsandbox/po/MBomCategoryLine.java`
- `library/BOM.db` — M_BomCategoryLine template tree, ST_SH c_order entry
