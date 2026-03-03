# PROGRESS — Current Development State

## Current State

**Gate:** `./scripts/run_tests.sh` — **290 PASS / 2 RED / 0 SKIP**

| Suite | Count |
|---|---|
| DAGCompiler | 191 PASS / 1 RED (G8-DX) — 2 surefire executions |
| ORMSandbox | 25 PASS / 1 RED (pre-existing) |
| TopologyMaker | 19/19 |
| BIM_COBOL | 56/56 (+8 new: TILE SURFACE W-49..52, ARRAY W-53..56) |

**Intentional REDs:** G8-DX (calibration) + ORMSandbox (pre-existing)
**Pre-existing REDs (outside gate):** X1-SH-GAP (StructuralCrossCheck), livingSetOverflowFillersZero (TopologyMaker)

**Pipeline:** 9 stages — Metadata, OrderLine, BOM, Template, Compile, Write, Verb, Prove, Digest
**BIM COBOL:** 12 verbs, 56 witnesses. VerbRegistry + ScriptRunner (PREP-1+2 done).

**5 Active Buildings:**

| Building | Mode | expected_elements | EmptySpaceChecksum (L0) |
|---|---|---|---|
| Ifc4_SampleHouse (SH) | EXTRACTED | 56 | b14f0c02c4602a14 |
| Ifc2x3_Duplex (DX) | EXTRACTED | 1099 | 1f6f2018dbda2faa |
| TB_LKTN | GENERATIVE | 139 | eb9188e164bc3156 |
| SJTII_Terminal | EXTRACTED | 51088 | — |
| ST_SH | GENERATIVE | 123 | — |

**SpatialDigests:** Stored in `c_order.spatial_digest`, enforced by BuildingRegistryTest.
Formula: name-agnostic bbox + material_rgba + geometry_hash + COUNT per class. DB is authoritative; prefixes for reference:
SH=28bbdff6 | DX=f4217aeb | TB=818ee300 | Terminal=5eaf2402 | ST_SH=dd41d4be
*(Updated 2026-03-03 after audit fix: material_rgba + geometry_hash added to digest formula)*

## Model Design — COMPLETE (Q&A1 Rounds 1–7, §11.1–11.37)

All architectural ambiguities resolved. See `docs/ConstructionAsERP.md` §11 for full design decisions.

**Core principles confirmed:**
- **No invention:** every element traces to component_library.db (extracted from IFC)
- **3D exact AABB:** no tolerance, mismatches are data errors
- **BOM.db = read-only dictionary**, output.db = fresh each run
- **Digest = mathematical output=input proof** (elements_meta + RTREE + material_rgba + geometry_hash, not ESLines)
- **VerbStage before Write** (gradual COBOL-over-assembler takeover)
- **expected_elements:** fixed for EXTRACTED, auto-calculated for GENERATIVE
- **output.db self-contained:** c_order + c_orderline copied from BOM.db at compile time
- **C_DocType model:** DocBaseType (RE/CO/IN) drives template selection, DocSubType (SH/DX/TB) drives BOM scoping. Replaces dual building_type + c_bpartner. Borrowed from iDempiere C_DocType.
- **Selection cascade:** AABB fit (primary) → largest volume → seq_no tiebreaker (lower preferred)

## Completed Work (2026-03-02 to 2026-03-03)

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
- SH: 41/55 (75%), DX: 372/1099 (34%) — honest scoreboard
- X1-DX-GAP promoted: Door/Furnishing positions (was count-only)
- X1-DX count gaps resolved: FlowController/FlowFitting/FurnishingElement now match
- X5b furniture bunching resolved: COUNTER_SINK data fix in BOM.db

**SpatialDigest Audit Fixes (2026-03-03):**
- Fix 1: material_rgba added to digest (COALESCE for NULL, deterministic)
- Fix 2: geometry_hash added via LEFT JOIN element_instances (prevents element substitution)
- Fix 3: P04_STOREY_Z_BAND promoted to critical (storey gating enforced)
- P04 band logic enhanced: foundation/FDN storey, IfcRailing extended Z, IfcSlab boundary tolerance, multi-storey element detection (>90% storey height)
- All 5 spatial_digest values updated in BOM.db c_order
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

## Next Work

### Pipeline work — C_OrderLine generation

The key mechanism (already half-implemented in TemplateStage/BomTemplateComposer):
- DocBaseType → look up template (RE for Residential) → walk M_BomCategoryLine tree
- System creates C_OrderLines per template slot with AABB from Z ratios × parent AABB
- Each C_OrderLine → best-fit M_BOM via selection cascade (AABB → volume → seq_no)
- GF recurses → room-level C_OrderLines → leaf BOMs → actual elements from component_library

BomTemplateComposer ALREADY does the template walk → NodeSelection records. Currently those only become CO_EmptySpaceLines. The missing step: NodeSelection → C_OrderLine in output.db.

7. **Template → C_OrderLine generation**: Write C_OrderLines from NodeSelection records to output.db. Each NodeSelection with a selectedBomId = one C_OrderLine.
8. **Element generation from C_OrderLines**: C_OrderLines replace the compiled DSL path (StoreyCompiler). Doors/furniture/MEP from BOM tree, not DSL heuristics.
9. EN-BLOC singularity: when DocSubType matches and exactly one BOM fits → single C_OrderLine, no walk

### PP_ Model Migration — C_OrderLine Separation (2026-03-04)

The verb storage model (c_order_verb_line + c_order_verb_param) implies a separation of
c_orderline into order topics (WHAT) vs production detail (HOW). See `ConstructionAsERP.md` §11.9.

18. **Phase 1: Create verb tables in BOM.db** — DDL in `BIM_COBOL.md` §15.6. Additive, no breakage.
19. **Phase 2: VerbStage fallback** — verb_lines present → VerbRegistry dispatch; absent → RelationalResolver fallback.
20. **Phase 3: Migrate extracted buildings** — Python extractor writes verb_lines. Drop placement columns from c_orderline. Migration SQL needed.
21. **Evaluate ESLine FK direction** — c_orderline_id on ESLine (NORM-0b, null) vs co_emptyspace_line_id on verb_line. The verb FK may supersede ESLine FK as primary production→space link.

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

**Known debt (advisory, no gates):** Terminal IfcReinforcingBar GIC(8), DX P23 MEP corners(364), TB P23 drain(6), TB furniture alignment, TB fans 1500mm(5), WARDROBE_SET/BATHROOM_VANITY_SET empty children, DX "Room not enclosed"(21)
