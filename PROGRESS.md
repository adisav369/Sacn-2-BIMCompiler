# PROGRESS — Current Development State

## Current State

**Gate:** `./scripts/run_tests.sh` — **Phase 0 in progress: SH GREEN, DX next**

| Suite | Count |
|---|---|
| DAGCompiler | SH GREEN (55/55). DX/TB/TE/ST still failing. |
| ORMSandbox | 30 PASS / 1 RED (w_compose_dx pre-existing) |
| TopologyMaker | 19/19 |
| BIM_COBOL | 63 total, 60 PASS / 3 RED (CoverWithRoof pre-existing) |

**BuildingRegistryTest (2026-03-05):**
- SH: **55/55 GREEN** — geometry 55 OK, all critical proofs satisfied (244 proven)
- DX: expected 1099, got 755 (same cascade gap — needs DUPLEX→Ifc2x3_Duplex rename + activate)
- TB: expected 139, got 464 (DSL overproduces — GENERATIVE, separate issue)
- Terminal: CRASH (MetadataValidator: no ad_building_grid/ad_room_boundary/ad_wall_face)
- ST_SH: CRASH (same MetadataValidator)

**Root cause (diagnosed + fixed for SH, 2026-03-05):** c_orderline migration to C_DocType
introduced naming gap. `lod_element_placement.building_type` used old names (SAMPLE_HOUSE,
DUPLEX) but C_DocType.ProjectName uses new names (Ifc4_SampleHouse, Ifc2x3_Duplex).
PlacementAD couldn't match → StoreyCompiler fell to DSL invention → wrong counts.
Additionally, SH/DX entries had `is_active=0` (only Terminal was active in legacy flat path).
See Phase 0 cascade gap fix details below.

**Pipeline:** 9 stages — Metadata, Parse, Compile, Template, Write, Verb(SPI), Digest, Geometry, Prove
**BIM COBOL:** 12 verbs, 63 witnesses. VerbExecutor SPI wired. VerbNodePersister → PP_Order_Node.

**5 Active Buildings:**

| Building | Mode | expected_elements | EmptySpaceChecksum (L0) |
|---|---|---|---|
| Ifc4_SampleHouse (SH) | EXTRACTED | 55 | b14f0c02c4602a14 |
| Ifc2x3_Duplex (DX) | EXTRACTED | 1099 | 48c95914ede78646 |
| TB_LKTN | GENERATIVE | 139 | eb9188e164bc3156 |
| SJTII_Terminal | EXTRACTED | 51088 | — |
| ST_SH | GENERATIVE | 123 | — |

**SpatialDigests:** Computed post-compile, stored in output.db `c_order.SpatialDigest`.
Formula: name-agnostic bbox + material_rgba + geometry_hash + COUNT per class. Prefixes:
SH=28bbdff6 | DX=8860510e | TB=818ee300 | Terminal=5eaf2402 | ST_SH=dd41d4be
*(Updated 2026-03-04 after DX FRACTION→ABSOLUTE migration)*

## Model Design — COMPLETE (Q&A1 Rounds 1–7, §11.1–11.37)

All architectural ambiguities resolved. See `docs/ConstructionAsERP.md` §11 for full design decisions.

**Core principles confirmed:**
- **No invention:** every element traces to component_library.db (extracted from IFC)
- **3D exact AABB:** no tolerance, mismatches are data errors
- **BOM.db = read-only dictionary**, output.db = fresh each run
- **Digest = mathematical output=input proof** (elements_meta + RTREE + material_rgba + geometry_hash, not ESLines)
- **VerbStage before Write** (gradual COBOL-over-assembler takeover)
- **expected_elements:** fixed for EXTRACTED, auto-calculated for GENERATIVE
- **C_DocType = constant domain config (BOM.db).** Building type definition + DSL template + reference AABB. c_order DROPPED from BOM.db.
- **C_Order = transactional (output.db only).** Created fresh each compile from C_DocType. DocStatus lifecycle (DR→IP→CO).
- **c_orderline DROPPED from BOM.db (redundant).** Data derivable from M_BOM + M_Product + component_library. C_OrderLine generated at compile time in output.db.
- **Three-concern lock (§11.9):** C_OrderLine=WHAT, PP_Order_Node=HOW, co_empty_space_line=WHERE
- **C_DocType model:** DocBaseType (RE/CO/IN) drives template selection, DocSubType (SH/DX/TB) drives BOM scoping. Borrowed from iDempiere C_DocType.
- **Selection cascade:** AABB fit (primary) → largest volume → seq_no tiebreaker (lower preferred)

## Completed Work (2026-03-02 to 2026-03-05)

**Phase 0 SH — EN-BLOC Singularity GREEN (2026-03-05):**
- **Root cause:** c_order→C_DocType migration changed building identifiers. `lod_element_placement.building_type`
  used old names (SAMPLE_HOUSE) but `C_DocType.ProjectName` uses new names (Ifc4_SampleHouse).
  PlacementAD lookup missed → StoreyCompiler fell to DSL invention → 122 elements instead of 55.
- **Fix 1:** `ad_element_placement.building_type`: `SAMPLE_HOUSE` → `Ifc4_SampleHouse` in component_library.db
- **Fix 2:** `ad_element_placement.is_active`: `0` → `1` for all 55 SH entries (was off — only Terminal active in legacy flat path)
- **Fix 3:** `ComponentLibrary.resolveByFamilyRank()`: disabled stale `bom.c_orderline` query (table dropped §11.9, method returns null early)
- **Result:** SH=55 elements. Geometry: 55 OK / 0 FAIL. Proofs: 244 proven, 11 advisory. All critical satisfied.
- **Pipeline path:** PlacementAD.loadLegacyFlat() → 55 placements cached → hasMetadata=true → applyPlacementOverrides
  (walls/slabs/doors/windows/furniture per storey) + emitGlobalPlacementElements (IfcMember/IfcPlate/IfcRoof globally)
- **Class name note:** Reference uses IfcFurnishingElement, compiled uses IfcFurniture (14 items). Count matches. Class drift = Phase A (G3-DIGEST).


**Output Template DB — Doc Artifact (2026-03-04):**
- `library/output_template.db` — blank output schema + `_schema_guide` documentation table (31 objects). Browsable with sqlite3/DB Browser.
- `OutputTemplateGenerator.java` — generates template from `BuildingWriter.initSchema()` (authoritative source).
- `scripts/generate_output_template.sh` — regenerate after schema changes.
- `OutputTemplateTest` — W-TEMPLATE-1 witness (template exists, >=24 tables, _schema_guide populated).
- Deleted 9 stale 0-byte files from `DAGCompiler/lib/output/`.
- Pipeline unchanged — `initSchema()` remains the sole schema authority at every call site.

**C_Order Model Cleanup — iDempiere Alignment (2026-03-04):**
- **Phase 1: c_order column renames** — All columns to iDempiere CamelCase. building_type DROPPED (redundant with C_DocType.DocBaseType).
- **Phase 2: c_orderline three-concern separation** — 14 placement+material columns DROPPED (§11.9). 9 WHAT columns RENAMED to CamelCase. View v_compilable_element_rule dropped.
- **Phase 3: c_order → C_DocType merge** — Domain config columns (DSLContent, OutputDbPath, ReferenceDbPath, AABB, ExpectedElements, Provenance, SeqNo, ProjectName) absorbed into C_DocType. c_order table DROPPED from BOM.db.
- **Phase 4: c_orderline DROPPED from BOM.db** — 1330 rows redundant with M_BOM + M_Product + component_library. C_OrderLine generated at compile time in output.db.
- **Pipeline updated:** BuildingRegistry reads C_DocType (not c_order). CompilationPipeline creates C_Order in output.db from C_DocType config (not copied from BOM.db). `.cbpartner()` → `.docSubType()`.
- **Java PO updated:** X_C_DocType extended with domain config columns/accessors. X_C_Order remains for output.db C_Order.
- **Disabled checks:** MetadataValidator, BuildingInspector, PlacementAD, RelationalResolver — updated for dropped c_orderline. Print SKIP messages with TODO notes.
- **Docs:** COLUMN_MIGRATION_MAP.md rewritten. Migration SQL in `migration/migration_c_order_to_c_doctype.sql`.
- **Compile:** All 5 modules (orm-core, ORMSandbox, DAGCompiler, BIM_COBOL, TopologyMaker) compile clean.
- **Gate:** Expected additional REDs from tests querying dropped tables. User directive: "take test out of the plan, get model right first."


**DX T3 Placement Fidelity — 100% (2026-03-04):**
- **Root cause:** 691 FRACTION/ROOM c_orderlines used fractions computed from old (deleted) room boundaries. The FRACTION→world-coordinate transform produced wrong positions.
- **Fix 1:** Restored 40 ROOM_Level_* boundaries from archived migration `migration_G8_DX_restore_grid_rooms.sql` (coordinates sourced from elements_rtree).
- **Fix 2:** Deactivated 3 BOM assembly anchors (UNIT_DX, FLOOR_DX_L1, FLOOR_DX_L2) — GENERATIVE mode lines, not needed for EXTRACTED.
- **Fix 3:** Added 30 ABSOLUTE c_orderlines for Ground Floor kitchen furniture (IfcFurnishingElement_1052..1081) extracted from reference DB.
- **Fix 4:** Converted ALL 691 FRACTION c_orderlines to ABSOLUTE, using world coordinates from reference DB. Matched via fuzzy (class, dims±3mm, Z±3mm) then set position_value=cx_mm, position_value_2=cy_mm.
- **Fix 5:** Fixed 28 remaining 1mm rounding mismatches by updating c_orderline width_mm/depth_mm/height_extent_mm to exactly match reference bbox dimensions.
- **Result:** DX T1 PASS, T2 PASS, T3 PASS — 1099/1099 (100%). From 34% to 100%.
- **Side effects:** BOMChainIntegrityTest (6 tests) now RED — expects furniture to go through BOM chain but EXTRACTED buildings use ABSOLUTE. EdgeVertexTest cabinet dims also shifted. These need repair in next session (update chain tests to exempt EXTRACTED buildings, or adopt the new PP_ verb model).
- **SH unchanged:** 41/55 (75%). Root cause: 14 IfcFurnishingElement generated as IfcFurniture via BOM explosion + 1 phantom. Fix deferred — needs family_ref→M_Product mapping.

**TILE SURFACE + ARRAY Verbs (2026-03-03):**
- **TileGrid.java** — pure geometry helper: single-panel `generate()` + multi-panel `generateMulti()`. All coords in meters.
- **LinearArray.java** — 1D linear array helper: `count = floor((hostLength - 2×cover) / spacing) + 1`. Direction enum (X/Y/Z).
- **TileSurfaceVerb.java** — keyword `TILE SURFACE`. Parses surface/product/origin/grid/step args, calls TileGrid, returns TilePayload. No DB.
- **ArrayVerb.java** — keyword `ARRAY`. Parses host/product/length/spacing/cover/direction args, calls LinearArray. BS 8110 cover (≥25mm) + EC2 spacing (≤300mm) compliance checks.
- **VerbRegistry.java** — registered both verbs (10→12). Updated VerbRegistryTest expected count.
- **8 new witnesses:** W-COBOL-49..52 (TileSurfaceVerbTest), W-COBOL-53..56 (ArrayVerbTest). All pure computation, no DB.
- Gate: 56/56 BIM_COBOL GREEN. run_tests.sh updated for new counts.

**ExtractedGeometryTruthTest (2026-03-03):**
- Standalone 3-tier truth test: T1 count → T2 volume → T3 placement match
- Class-agnostic, name-agnostic. Pure bbox geometry.
- T3: 1:1 AABB matching — if placement matches, visual is proven
- SH: 41/55 (75%), **DX: 1099/1099 (100%) — ALL THREE TIERS GREEN** *(was 372/1099, fixed 2026-03-04)*
- X1-DX-GAP promoted: Door/Furnishing positions (was count-only)
- X1-DX count gaps resolved: FlowController/FlowFitting/FurnishingElement now match
- X5b furniture bunching resolved: COUNTER_SINK data fix in BOM.db

**SpatialDigest Audit Fixes (2026-03-03):**
- Fix 1: material_rgba added to digest (COALESCE for NULL, deterministic)
- Fix 2: geometry_hash added via LEFT JOIN element_instances (prevents element substitution)
- Fix 3: P04_STOREY_Z_BAND promoted to critical (storey gating enforced)
- P04 band logic enhanced: foundation/FDN storey, IfcRailing extended Z, IfcSlab boundary tolerance, multi-storey element detection (>90% storey height)
- All 5 spatial_digest values updated (now computed post-compile in output.db)
- Gate: 283 PASS / 1 RED / 1 SKIP (unchanged)

**Migration M1–M2 (2026-03-03):**
- m_bom.c_bpartner → doc_sub_type (column renamed in DB + all Java PO/queries)
- c_order.C_DocType_ID FK added + backfilled (RE_SH, RE_DX, RE_TB, CO_TE, RE_ST)
- WriteStage copies C_DocType_ID to output.db; BuildingWriter DDL updated
- Witnesses: W-OWNER-1/2 use doc_sub_type/C_DocType_ID, W-DOCTYPE-2 new
- c_order.c_bpartner kept (future: repurpose for real vendor/customer)
- Gate: 282 → 283 PASS (+1 W-DOCTYPE-2)

**Data fixes (2026-03-02):**
1. ~~Trace structural door drift~~ — TRACED: ST_SH legacy compiled path invents D2+D7 doors.
2. ~~Furniture AABB drift~~ — FIXED: all SH furniture allocated dims match IFC extraction.
3. ~~KITCHEN_CABINET_SET IfcFlowTerminal → Tall_Cabinet~~ — was already applied.
4. ~~TOILET_BLOCK_FIXTURES~~ — already correct.
5. ~~M_BomCategory.doc_type column~~ — ADDED (Residential/Commercial/Industrial).
6. ~~C_Order fresh-output.db pattern~~ — DONE: c_order + c_orderline in output.db initSchema.

**Model fixes (2026-03-03):**
6a. ~~Add c_orderline entries for ST_SH in BOM.db~~ — WRONG APPROACH (Q&A1 §11.2). Compiler generates C_OrderLines at compile time.
6c. ~~M_BOM.seq_no column~~ — ADDED. Selection cascade: AABB fit → volume → seq_no.
6d. ~~RE template decoupled from ST~~ — RE.C_BPartner_ID=NULL. Template lookup by doc_type. BomTemplateComposer takes docType parameter.
6e. ~~C_DocType table~~ — CREATED with 5 entries. X_C_DocType + MCDocType PO classes live. DocBaseType + DocSubType model documented in §11.36.

## Roadmap

Full production roadmap: `docs/ACTION_ROADMAP.md` — 9 phases (0–H), 3 parallel tracks.

| Phase | What | Gate |
|-------|------|------|
| **0** | **EN-BLOC Singularity — wire BOM walk for EXTRACTED buildings** | **BuildingRegistryTest SH=55, DX=1099** |
| A | Rosetta Stone Gate Convergence (SH/DX 5 gates green) | RosettaStoneGateTest G1-G5 PASS |
| B | Terminal BOM Recomposition (51K elements) | G1-G5 PASS for CO_TE |
| C | 2D Drawing Export (3D → SVG) | SH professional drawing set |
| D | Synthetic Rosetta Stone (3D→2D→3D round-trip) | Digest match for synthetic stones |
| E | Generative from 2D Layout | TB-LKTN from 2D matches DSL |
| F | BIM COBOL v1.0 (verb-driven compilation) | Zero Java assembler code |
| G | Bonsai GUI Editor | Compile-edit-recompile cycle |
| H | iDempiere ERP Integration (CSV→REST→OSGI) | PO from compiled BOM |

**Tracks:** Core pipeline (0→A→B→F→G→H3) | 2D round-trip (0→A→C→D→E) | ERP (H1→H2)

## Phase 0.1 — Product Catalog Normalisation

### P0.1-DEDUP — DONE (2026-03-05)

Deduped 1099 DX instance rows → 79 unique M_Product entries (14:1 ratio).

**Changes:**
- **BOM.db:** M_AttributeSet table (5 rows: BIM_Pipe/Conduit/Wall/Slab/Component). M_Product: +3 columns (M_AttributeSet_ID, Name, Description), +65 new rows, 14 existing rows updated. Total: 187 M_Product.
- **component_library.db:** ad_element_placement.M_Product_ID column added, 1099 DX rows backfilled.
- **Migrations:** `migration_P01_product_catalog.sql` (BOM.db), `migration_P01_placement_product_link.sql` (component_library.db).
- **Schema snapshots regenerated.**

**Product breakdown:**
| Category | Unique Products | Instances | Dedup Rule |
|----------|-----------------|-----------|------------|
| PIPE | 8 | 407 | Subtype + cross-section diameter |
| FITTING | 6 | 358 | One per element_ref family |
| COMPONENT (MEP/fixture/appliance) | 17 | 139 | One per element_ref family |
| WALL | 8 | 57 | One per wall type (thickness product) |
| SLAB | 6 | 21 | One per slab type (thickness product) |
| FURNISHING | 6+14 overlap | 69 | One per element_ref family |
| DOOR | 2+2 overlap | 14 | One per element_ref family |
| WINDOW | 3+3 overlap | 24 | One per element_ref family |
| BEAM | 2 | 8 | One per steel section |
| CONDUIT/DUCT | 3 | 20 | One per type + cross-section |
| RAILING | 2 | 4 | One per railing type |
| STAIR | 2 | 6 | Stringer vs flight |

**Verification (all PASS):**
- V1: 0 NULL M_Product_ID (1099/1099 mapped)
- V2: 0 orphan cross-DB FKs
- V3: 187 M_Product rows
- V4: No new test failures (BIM_COBOL 60/63, DAGCompiler pre-existing REDs unchanged)
- V5: 79 products → 1099 instances

**No Java/pipeline changes.** X_MProduct.java untouched. New columns unused by pipeline.

**Next P0.1 steps:**
- P0.1-BOM: M_AttributeSetInstance for per-instance attributes (pipe length, wall height)
- P0.1-RENAME: ad_element_placement → M_StorageOnHand (iDempiere WMS pattern)

## Next Work

### Phase 0: EN-BLOC Singularity — SH DONE, DX NEXT

**SH Phase 0 GREEN (2026-03-05):**
Root cause was a cascade gap from the c_order→C_DocType migration. Three fixes:
1. `ad_element_placement.building_type` in component_library.db: `SAMPLE_HOUSE` → `Ifc4_SampleHouse`
2. `ad_element_placement.is_active`: `0` → `1` for SH entries
3. `ComponentLibrary.resolveByFamilyRank()`: disabled stale `bom.c_orderline` query (table dropped §11.9)

Result: PlacementAD finds 55 metadata placements → StoreyCompiler takes hasMetadata=true
path → applyPlacementOverrides (walls/slabs/doors/windows/furniture) + emitGlobalPlacementElements
(IfcMember/IfcPlate/IfcRoof) → 55 elements, all geometry OK, all critical proofs satisfied.

**DX Phase 0 — NEXT (same cascade gap recipe + 14 IfcFlowController):**
1. `ad_element_placement.building_type`: `DUPLEX` → `Ifc2x3_Duplex` + `is_active` → `1`
2. lod_element_placement has 1085 rows — missing 14 IfcFlowController (valves/dampers)
3. Extract 14 IfcFlowController from reference DB (Ifc2x3_Duplex_extracted.db) → INSERT into ad_element_placement
4. Gate: BuildingRegistryTest DX=1099

**Known Phase A issues (not Phase 0):**
- SH class name drift: IfcFurnishingElement (reference) vs IfcFurniture (compiled) — G3-DIGEST
- TB overproduction (464 vs 139) — GENERATIVE path, separate from EXTRACTED cascade gap

### iDempiere naming convention note
C_Order.Name (human-readable) + Value (Search Key, concatenated form of Name).
Use iDempiere convention for all naming — ProjectName on C_DocType follows this pattern.

### PP_ Model — Three-Concern Lock (2026-03-04) — DONE

**X_C_OrderLine = WHAT only.** Structural guard via reflection tests (W-LOCK-1..6).
Zero placement columns, zero material columns. 8 setters exactly.

**PP_Order_Node = HOW.** PO classes (X_, M_) + DDL in BuildingWriter. 5 witness tests (W-PP-1..5).
Each verb invocation → one row. DocStatus tracks lifecycle (DR/IP/CO/VO).

**VerbExecutor SPI.** DAGCompiler defines interface, BIM_COBOL implements via BimCobolVerbExecutor.
ServiceLoader discovers at runtime. VerbNodePersister converts VerbResult → PP_Order_Node rows.
7 integration witnesses (W-COBOL-57..63).

**BasePO.save() fix.** Returns false on INSERT OR IGNORE constraint violations (was silently dropping).

18. ~~Phase 1: Create verb tables~~ — DONE. PP_Order_Node + PP_Order_NodeProduct in output.db.
19. ~~Phase 2: VerbStage SPI~~ — DONE. VerbExecutor interface, BimCobolVerbExecutor, ServiceLoader wiring.
20. **Phase 3: Migrate extracted buildings** — Python extractor writes verb_lines. Drop placement columns from c_orderline. Migration SQL needed.
21. **Evaluate ESLine FK direction** — PP_Order_Node.S_Resource_ID links to ESLine. May supersede c_orderline ESLine FK.

### Remaining tasks

6b. SIDE_TABLE_A/B in SH_LIVING_SET (610×610×610) — no IFC match, possible invention. Investigate.
10. Rosetta Stone digest: sorted BBox vertex hash per element class, structural union BBox
11. VerbStage execution: SPI interface, move before Write, gradual MEP verb takeover
12. Populate m_bom_line dx/dy from reference IFC centroids
13. RelationalResolver deletion sprint (aligns with Phase 2/3 of PP_ migration — §11.9)
14. TB-LKTN INVENTION STOP + priority-based furniture placement
15. Terminal BOM modelling (third Rosetta Stone)
16. Mesh2Library compiler dispatch
17. G8-DX calibration investigation (NULL-bound rooms)
22. **DX chain test repair**: BOMChainIntegrityTest×3 + BomChainIntegrityTest×3 + EdgeVertexTest — update to exempt EXTRACTED buildings from BOM chain assumptions (or adopt PP_ verb model)
23. **SH T3 furniture fix**: Deactivate 2 IfcElementAssembly BOM anchors, add 14 ABSOLUTE IfcFurnishingElement c_orderlines with family_ref→M_Product mapping

### Bonsai Outliner — BOM-like IFC Tree Structure (Phase G note)
The Bonsai Outliner should display the BOM hierarchy as an IFC spatial structure:
Building → Floor → Room → Leaves (furniture, fixtures, MEP). This maps naturally to
element_assemblies in output.db. When forming BOM-like families during compilation,
the Outliner tree reflects the same structure. Reference: IFC spatial containment
(IfcBuilding → IfcBuildingStorey → IfcSpace → elements). Good alignment with the
M_BOM tree (UNIT → FLOOR → SET → leaf products).

**Known debt (advisory, no gates):** Terminal IfcReinforcingBar GIC(8), DX P23 MEP corners(364), TB P23 drain(6), TB furniture alignment, TB fans 1500mm(5), WARDROBE_SET/BATHROOM_VANITY_SET empty children, DX "Room not enclosed"(21)
