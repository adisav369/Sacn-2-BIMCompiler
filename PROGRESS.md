# PROGRESS — Current Development State

**Last updated:** 2026-02-28 (Phase NORM-3a — BOM Walker + Visitor Pattern, Phases A–E)
**Tests:** DAGCompiler **174/176** (G8-DX intentional RED ×1, 1 @Disabled) + ORMSandbox **25/25** | TopologyMaker **19/19** | TOTAL: **216 PASS / 1 RED / 1 SKIP**
**SpatialDigests:** SH=1f325a98 DX=d3c779b9 TB=dd4345f4 Terminal=301b42b1 (stable — SH+DX in scope)
**EmptySpaceChecksums:** SH=b14f0c02c4602a14 DX=1f6f2018dbda2faa TB=eb9188e164bc3156

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

**Deleted:**
- `bom/BOMAssemblerAD.java` — replaced by AssemblyStructureVisitor (Phase E)

**Modified:**
- `BuildingWriter.applyADBOMRecipes()` — Phase D: calls only `applyADBOMRecipesViaWalker()`; BOMAssemblerAD removed from pipeline
- `AssemblerFactory.java` — `bomAssembler()` method removed (dead after BOMAssemblerAD deletion)
- `PlacementAD`, `RelationalResolver` — made `public` (required for cross-package visitor access)
- `RelationalResolver` — `@Deprecated` (Phase D mark; still live for placement pipeline)

**Remaining debt (NORM-3a future):**
- `RelationalResolver` still used by `PlacementAD.loadRelational()` and `SpatialPlacementVisitor.compute()`
  and by `CompilerContractTest` via reflection for TB_LKTN geometric proofs.
  Full replacement requires `SpatialPlacementVisitor` to do independent coordinate resolution (Phase D full switch).
- `PlacementAD.consumed` registry still used by `StoreyCompiler.markConsumed()` + `BuildingWriter.isConsumed()`.
  Elimination deferred until spatial placement pipeline is fully on the walker.

**What's next:** Phase NORM-3b (CO_EmptySpace collapse assessment) or Phase ST-1 (template-driven compilation)

---

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

**Docs:** `bim_architecture_viz.html` ERD updated (NORM-2 column changes + M_Product ifc_class)
**Schema snapshot:** `library/schema_snapshot_bom.sql` regenerated (1176 lines)

**Key fix:** `BOMTreeLoader.BOMChild.productRef()` — Ifc-prefix check prevents structural BUY stubs from being treated as catalog product IDs, preserving namePattern-based dimension lookup for elements like IfcFurniture, IfcSlab.

**What's next:** Phase NORM-3 — BOM Visitor walker unification (BOMAssemblerAD + RelationalResolver → single walker with Visitor pattern)

---

### ✅ SESSION COMPLETE — Phase NORM-1: ad_product_dim → M_Product (2026-02-28)

**Result: 207 PASS / 1 RED / 1 SKIP** (gate unchanged)

iDempiere M_Product pattern: universal product master for both BUY leaves and MAKE assemblies.

**Schema changes (BOM.db):**
- `M_Product.component_id INTEGER` — logical FK → component_library.component_definitions(id).
  Populated for 16 BUY products by name-match migration (ATTACH).
- `M_Product.bom_id TEXT REFERENCES m_bom(bom_id)` — FK to BOM for assembly stubs.
- 31 M_Product stubs inserted (is_active=0) for MAKE-referenced BOMs. dims=0.001 sentinel.
- `ALTER TABLE ad_product_dim RENAME TO M_Product` — SQLite auto-updated m_bom_line FK.
- Total rows: 88 (57 BUY leaves + 31 assembly stubs)

**Java changes:**
- New: `X_MProduct.java` (base PO) + `MProduct.java` (model, +getAssembly() for stubs)
- Deprecated: `X_AdProductDim` → alias, `M_AdProductDim` → alias
- All 6 SQL strings + 4 class references updated across DAGCompiler + ORMSandbox

**Migration:** `migration/migration_NORM1_M_Product.sql`
**Fix:** `migration_topology_maker_bootstrap.sql` restored from archive (needed by test)

**Docs:** `bim_architecture_viz.html` ERD + `ConstructionAsERP.md` §1.2/1.3 updated.

**What's next:** Phase NORM-2 — replace child_bom_id/product_ref/child_ifc_class with single child_product_id FK

---

### ✅ SESSION COMPLETE — Phase NORM-0b: c_orderline_id FK on co_empty_space_line (2026-02-28)

**Result: 207 PASS / 1 RED / 1 SKIP** (gate unchanged)

iDempiere fulfillment link: C_OrderLine = what was requested; CO_EmptySpaceLine = where
it was delivered. FK is logical (cross-DB: output.db → BOM.db), not enforced by SQLite.

**Schema change (output.db):**
- `co_empty_space_line.c_orderline_id INTEGER` (nullable) added to `BuildingWriter.java`
  CREATE TABLE DDL. All rows NULL — no BOM-assembly-level c_orderline entries exist yet
  (current c_orderline holds element-level refs: IfcDoor, IfcWall, etc.). Will be
  populated in NORM-2 when C_OrderLines for BOM assembly requests are created.

**Java changes:**
- `X_CO_EmptySpaceLine` — +COLUMNNAME_c_orderline_id, +getCOrderlineId(), +setCOrderlineId()

**Docs:**
- `ConstructionAsERP.md` §1.3 table + §3.4 — c_orderline_id fulfillment link documented

**What's next:** Phase NORM-1 — rename ad_product_dim → M_Product, add component_id + bom_id FKs

---

### ✅ SESSION COMPLETE — Phase NORM-0a: component_type Discriminator (2026-02-28)

**Result: 207 PASS / 1 RED / 1 SKIP** (gate unchanged)

Added explicit BUY/MAKE/PHANTOM discriminator to `m_bom_line`. Replaces implicit three-way
column sniff with a documented, queryable field following the Libero Manufacturing PP pattern.

**Schema change:**
- `m_bom_line.component_type TEXT NOT NULL DEFAULT 'MAKE'` — populated for all 214 rows
  - 131 BUY: 37 product_ref + 94 child_ifc_class structural leaves
  - 57 MAKE: child_bom_id on-site assemblies
  - 26 PHANTOM: buffer fillers + structural zone placeholders (BUFFER role, CORRIDOR etc.)

**Java changes:**
- `X_M_BOMLine` — +COLUMNNAME_component_type, +getComponentType(), +setComponentType()
- `BOMAssemblerAD.BOMChild` — +componentType field (8th arg), +isType(String),
  reimplemented isLeaf() = isType("BUY"), isNestedBom() = isType("MAKE")
- `MBOMLine` — isNestedBom/isLeaf dispatch via getComponentType(), +isPhantom()

**Migration:** `migration/migration_NORM0a_component_type.sql`

**What's next:** Phase NORM-0b — `CO_EmptySpaceLine.c_orderline_id` FK

---

### ✅ SESSION COMPLETE — Phase ES-1: EmptySpaceChecksum Verification Gate (2026-02-28)

**Result: 207 PASS / 1 RED / 1 SKIP** (gate unchanged — new gate #3 active)

Single level-0 CO_EmptySpaceLine checksum: when C_Order.c_bpartner == M_BOM.c_bpartner (owner-matched), the UNIT BOM is one complete intact construct. Hash that single acceptance line as a 16-char SHA256 prefix. If it changes → fault is in BOM.db data, not Java code.

**New files/methods:**
- `SpatialDigest.computeEmptySpaceChecksum(dbPath)` — hashes level-0 CO_EmptySpaceLine only

**Extended:**
- `CompilationContext` — +emptySpaceChecksum field, getter, setter, wired to PipelineResult
- `CompilationPipeline.PipelineResult` — +emptySpaceChecksum. DigestStage computes it after SpatialDigest
- `BuildingRegistry.BuildingEntry` — +emptySpaceChecksum loaded from c_order
- `BuildingRegistryTest` — Gate #3: assert emptySpaceChecksum match (7 gates total)
- `X_C_Order` — +empty_space_checksum column, getter, setter
- `c_order` schema — +empty_space_checksum TEXT column

**Framework foundation:** Both single-line (level-0, hash verified) and multi-line (level-1, structural audit trail) CO_EmptySpaceLines are produced. Single-line proves BOM construct correctness. Multi-line provides structural capacity decomposition.

**What's next:** Phase ST-1c — template-driven compilation walker. Also: review ConstructionAsERP.md Appendixes for issues.

---

## Backlog — Schema Normalisation (post ST-1c, do not start mid-pipeline)

Sequence: NORM-0 → NORM-1 → NORM-2 → NORM-3. Each phase ends at a full test gate. Do not
combine phases. The existing 207-pass suite is the regression guard throughout.

---

### Phase NORM-0 — Component type discriminator + CO_EmptySpaceLine anchor
**Scope:** Two targeted additions that cost nothing to add now and unblock later phases.
No column drops, no renames, no data migrations required.

#### NORM-0a — `component_type` on M_BOM_Line (iDempiere/Libero pattern)
Add `component_type TEXT NOT NULL DEFAULT 'MAKE'` to `m_bom_line`, with three values from
the Libero Manufacturing PP module:

| Value | iDempiere term | BIM meaning | Output |
|-------|---------------|-------------|--------|
| `BUY` | Component (purchased) | Leaf with geometry — Piano, Sofa, IfcDoor | Placement + geometry record |
| `MAKE` | Manufactured | On-site assembly — LIVING_SET, FLOOR_DX_L1 | Assembly record + recurse |
| `PHANTOM` | Phantom BOM | Space placeholder — Buffer_NW (ST category) | Inline expansion only, no output record |

Populate from existing data:
- `product_ref IS NOT NULL` → `BUY`
- `child_bom_id IS NOT NULL` → `MAKE`
- `NEITHER + bom_category = 'ST'` → `PHANTOM`
- `NEITHER + child_ifc_class IS NOT NULL` → `BUY` (structural leaf, pattern-matched)

This makes the current implicit three-way column sniff **explicit and documented**.
`BOMAssemblerAD.BOMChild.isLeaf()` and `isNestedBom()` become `isType(BUY)` / `isType(MAKE)`.
**Gate:** 207 PASS. `component_type` populated for all 214 rows (57+37+120).

#### NORM-0b — `CO_EmptySpaceLine.c_orderline_id` (iDempiere fulfillment link)
In iDempiere: C_OrderLine = what was requested; fulfillment record = what was actually delivered.
CO_EmptySpaceLine is the spatial fulfillment record for a C_OrderLine.

Add `c_orderline_id INTEGER REFERENCES c_orderline(id)` to `co_empty_space_line` (nullable).
Populate for all lines where the BOM matches a C_OrderLine entry.

This creates the direct C_OrderLine → CO_EmptySpaceLine link without removing CO_EmptySpace yet.
CO_EmptySpace.is_available remains the per-building quality gate (assess collapse in NORM-3).
**Gate:** 207 PASS. FK populated for all current co_empty_space_line rows.

---

### Phase NORM-1 — M_Product: rename + geometry link
**Scope:** `ad_product_dim` → `M_Product`. Add two columns. No row deletions. Requires NORM-0a complete
(so component_type already distinguishes BUY/MAKE/PHANTOM before we change the FK structure).

1. Add `component_id INTEGER REFERENCES component_definitions(id)` — populate by one-time
   name-match migration (deterministic, names already match for all BUY rows).
2. Add `bom_id TEXT REFERENCES m_bom(bom_id)` — populate for all products that are currently
   targeted by `m_bom_line.child_bom_id` (MAKE rows).
3. Rename table `ad_product_dim` → `M_Product` in schema, all SQL, Java POs
   (`X_M_BOMLine`, `RelationalResolver`, `BOMAssemblerAD`, `M_AdProductDim` class), and witnesses.
4. Update ERD viz + ConstructionAsERP.md §1.1 table list.
**Gate:** 207 PASS unchanged. ERD cross-DB dashed arrow becomes solid FK.

---

### Phase NORM-2 — M_BOM_Line: single child FK
**Scope:** Replace the three exclusive columns (`child_bom_id`, `product_ref`, `child_ifc_class`)
with one: `child_product_id → M_Product`. Requires NORM-1 complete.

1. Add `child_product_id TEXT REFERENCES M_Product(product_id)` (nullable initially).
2. Populate from `component_type` (set in NORM-0a — no sniffing needed):
   - `BUY` rows: `child_product_id = product_ref` (direct copy).
   - `MAKE` rows: `child_product_id = child_bom_id` (valid M_Product key after NORM-1).
   - `PHANTOM` rows: `child_product_id = role` key with thin M_Product stub (`ifc_class` set).
3. Make `child_product_id` NOT NULL. Run full test gate.
4. Drop `child_bom_id`, `product_ref`, `child_ifc_class` from schema and POs.
5. Rename `m_bom_line.space_*_mm` → `allocated_*_mm` (nullable);
   null = use `M_Product` intrinsic dims × 1000.
**Gate:** 207 PASS unchanged. Both `BOMAssemblerAD` and `RelationalResolver` dispatch on
`component_type` via `child_product_id`, no column sniffing.

---

### Phase NORM-3 — BOM Visitor unification + CO_EmptySpace assessment
**Scope:** Two concerns addressed together since both affect the compilation pipeline root.
Requires NORM-2 complete.

#### NORM-3a — Single BOM walker with Visitor pattern ✅ (assembly side done; spatial side deferred)
`BOMAssemblerAD` deleted; `AssemblyStructureVisitor + BOMWalker` are sole source for element_assemblies.
`SpatialPlacementVisitor` created (Phase C parity baseline); full spatial switch deferred.
`RelationalResolver` + `PlacementAD.consumed` still live. Next: independent SpatialPlacementVisitor
coordinate resolution to replace RelationalResolver entirely.

Original scope: `BOMAssemblerAD` (writes element_assemblies) and `RelationalResolver` (writes coordinates)
as two independent full BOM traversals. After NORM-2 the tree is homogeneous —
one `child_product_id`, one `component_type`. Replace with:

```
BOMWalker.walk(rootProduct, List<BOMVisitor> visitors)
  interface BOMVisitor {
      void onMake(MProduct p, MBOM bom, int level);     // assembly node
      void onBuy(MProduct p, int level);                // leaf with geometry
      void onPhantom(MProduct p, MBOM bom, int level);  // inline expand, no output
  }
```

Visitors: `AssemblyStructureVisitor` (replaces BOMAssemblerAD) and
`SpatialPlacementVisitor` (replaces RelationalResolver). `PlacementAD.consumed` registry
and the FLAT/RELATIONAL two-path split disappear — the walker is the single source of truth.

#### NORM-3b — CO_EmptySpace collapse assessment
With NORM-0b in place, every `co_empty_space_line` already has a `c_orderline_id` FK.
Assess whether `co_empty_space` header is still needed as a separate table:
- If `is_available` can move to `c_order.doc_status` (CO = compiled+verified): collapse.
- If per-building quality gate semantics are needed independently of order status: keep as view.
Decision gate: measure whether removing `co_empty_space` simplifies or complicates the
EmptySpaceChecksum witness (Gate #3). Only collapse if the witness complexity goes down.

**Gate:** 207 PASS. Single BOM walk covers both assembly structure and spatial placement.

---

### ✅ SESSION COMPLETE — Phase ST-1b: Aspect Columns + DX Composition Proof (2026-02-27)

**Result: 207 PASS / 1 RED / 1 SKIP** (+1 new witness: W-COMPOSE-DX)

Aspect injection columns on M_BomCategoryLine + DX template branch + composition proof engine.

**Schema changes:**
- 3 new columns on `M_BomCategoryLine`: `num_units` (0=universal, 1=SH, 2=DX), `storey_count`, `mirroring_rule` ('NONE' or 'PARTY_WALL_PI')
- DX template branch: RE→PR(seq=15, num_units=2)→2×HU→{L1,L2}→rooms (12 new lines, IDs 9–20)
- Existing SH lines tagged: GF num_units=1, SL/RF num_units=0

**New files:**
- `BomTemplateComposer.java` — composition walker: walks RE template with AABB + numUnits, selects best-fit BOMs from entire catalog (no c_bpartner filter)
- `migration/migration_phase_ST1b.sql` — aspect columns + DX branch seed

**Extended:**
- `X_MBomCategoryLine.java` — COLUMNNAME + getter/setter for num_units, storey_count, mirroring_rule
- `MBOM.java` — `findBestFitAnyOwner()`: like `findNextFitSpace` but no owner filter. Uses bom_type-aware fit model: SET BOMs use 1D strip (sumW), FLOOR/UNIT BOMs accept (2D room tiling)

**W-COMPOSE-DX witness:** AABB(12372×26730×7884) + numUnits=2 → composition selects DX BOMs at container level (PR→DUPLEX_SET_STD, HU→DUPLEX_SINGLE_UNIT_STD) and generic/mixed BOMs at room level. Proves the catalog cart mechanism: DX structure emerges from AABB + template constraints alone.

**Docs:** ConstructionAsERP.md §D.4 — Catalog Cart Model & Aspect Injection

**What's next:** Phase ST-1c — template-driven compilation walker. Walk M_BomCategoryLine tree → create CO_EmptySpaceLines → select best-fit BOMs. POC gate: SpatialDigest(ST_SH) == SpatialDigest(SH).

---

### ✅ SESSION COMPLETE — Phase ST-0: Standard Mode Foundation (2026-02-27)

**Result: 204 PASS / 1 RED / 1 SKIP** (+2 new witnesses)

Schema foundation for ST mode — template-driven compilation where no pre-built BOM tree exists.

**Column rename:** `bom_owner` → `c_bpartner` in m_bom and c_order. All X_/M_ PO classes, DAGCompiler SQL, and test witnesses updated.

**New tables:**
- `C_BPartner` — building pattern owner lookup (SH, DX, TB, MY, TE, ST)
- `M_BomCategoryLine` — recursive decomposition recipe: RE→{SL,GF,RF}, GF→{LI,BD,DN,KT,BT}

**M_BomCategory extended:** +Value (CamelCase search key), +C_BPartner_ID (owner scope for templates). New categories: GF (GroundFloor), RE (ResidentialTemplate).

**c_order extended:** +aabb_width_mm, +aabb_depth_mm, +aabb_height_mm. Backfilled from compiled output co_empty_space headers.

**New PO classes:** X_CBPartner/MCBPartner, X_MBomCategoryLine/MBomCategoryLine

**New c_order entry:** ST_SH (is_active=0, dormant) — POC with SH's exact AABB. Phase ST-1 will add the template walker.

**New witnesses:** W-CBPARTNER-1 (every c_bpartner exists in C_BPartner), W-CATEGORY-LINE-1 (every M_BomCategoryLine child exists in M_BomCategory)

**What's next:** Phase ST-1 — implement template-driven compilation in CompilationPipeline. Walk M_BomCategoryLine tree → create CO_EmptySpaceLines → select best-fit BOMs. POC gate: SpatialDigest(ST_SH) == SpatialDigest(SH).

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

**Parked for Phase G-1:** `ad_room_slot` drop — still has 6+ active consumers across 3 modules. Requires query rewrite to `m_bom WHERE bom_category=? AND c_bpartner=?`.

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
- `ad_room_slot` is **DEPRECATED** by bom_category+c_bpartner — leave as-is

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

### Phase ST: Standard Mode — c_bpartner='ST'

**Goal:** Owner-agnostic BOM compilation. The 1D Intent: two C_Order fields drive everything — `c_bpartner` (WHO) + `AABB` (HOW BIG).

**Phase ST-0 DONE** (2026-02-27): Schema foundation complete. See session above.

**Phase ST-1 NEXT:** Template-driven compilation — walk M_BomCategoryLine tree → create CO_EmptySpaceLines → select best-fit BOMs. POC gate: `SpatialDigest(ST_SH) == SpatialDigest(SH)`.

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
